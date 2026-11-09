package win.furryhook.feature.impl.pve;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.DisconnectEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.PveStateMachine;
import win.furryhook.pve.economy.CommandCooldown;
import win.furryhook.pve.economy.EconomyChat;
import win.furryhook.pve.economy.EconomyCommands;
import win.furryhook.pve.economy.EconomyItemText;
import win.furryhook.pve.economy.EconomyMenus;
import win.furryhook.pve.economy.EconomyTextParser;
import win.furryhook.pve.server.ServerAdapter;
import win.furryhook.pve.server.ServerAdapters;
import win.furryhook.pve.server.ServerProfile;

public final class AuctionRelistFeature extends PveFeature {
   private static final long CYCLE_TICKS = 1200L;
   private static final long MENU_TIMEOUT_TICKS = 80L;
   private final PveStateMachine<AuctionRelistFeature.State> machine = new PveStateMachine<>(AuctionRelistFeature.State.WAIT);
   private final CommandCooldown commandCooldown = new CommandCooldown();
   private long lastTick;
   private long nextCycleTick;
   private int ownedContainerId = -1;
   private boolean resourcesClaimed;

   public AuctionRelistFeature() {
      super("AuctionRelist", "Periodically relists expired auction items", FeatureCategory.MISC, -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      if (client.player != null && client.level != null) {
         long tick = client.level.getGameTime();
         this.lastTick = tick;
         if (ServerAdapters.current().profile() == ServerProfile.FUNTIME) {
            switch ((AuctionRelistFeature.State)this.machine.state()) {
               case WAIT:
                  if (tick >= this.nextCycleTick && !isPlayerMoving(client)) {
                     this.beginCycle(tick);
                  }
                  break;
               case OPEN_AUCTION:
                  this.openAuction(client, tick);
                  break;
               case WAIT_AUCTION_MENU:
                  this.waitForAuctionMenu(client, tick);
                  break;
               case WAIT_STORAGE_MENU:
                  this.waitForStorageMenu(client, tick);
                  break;
               case CLOSING:
                  if (this.machine.ticksInState(tick) >= 6L) {
                     this.finishCycle(client, tick);
                  }
            }
         } else {
            if (this.resourcesClaimed || !this.machine.is(AuctionRelistFeature.State.WAIT)) {
               this.finishCycle(client, tick);
            }
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         String text = EconomyChat.incomingText(event.getPacket());
         if (text != null && EconomyTextParser.containsAny(text, "аукцион недоступен", "auction is unavailable", "не удалось открыть аукцион", "слишком часто")
            )
          {
            Minecraft.getInstance().execute(() -> {
               if (this.isEnabled()) {
                  this.finishCycle(Minecraft.getInstance(), this.lastTick);
               }
            });
         }
      }
   }

   @EventTarget
   public void onDisconnect(DisconnectEvent event) {
      this.resetRuntime(false);
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntime(false);
      this.nextCycleTick = this.lastTick + 1200L;
   }

   @Override
   protected void onPveDisable() {
      this.resetRuntime(true);
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resetRuntime(true);
   }

   private void beginCycle(long tick) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.gui.screen() == null && client.player.containerMenu == client.player.inventoryMenu) {
         if (!this.claim(AutomationResource.INVENTORY, AutomationResource.SCREEN, AutomationResource.CHAT)) {
            this.nextCycleTick = tick + 10L;
         } else {
            this.resourcesClaimed = true;
            this.machine.transition(AuctionRelistFeature.State.OPEN_AUCTION, tick);
         }
      } else {
         this.nextCycleTick = tick + 20L;
      }
   }

   private void openAuction(Minecraft client, long tick) {
      if (this.commandCooldown.ready(tick)) {
         ServerAdapter adapter = ServerAdapters.current();
         adapter.auctionCommand().flatMap(EconomyCommands::auctionRoot).ifPresentOrElse(command -> {
            adapter.sendCommand(client.player, command);
            this.commandCooldown.tryAcquire(tick, 40L);
            this.machine.transition(AuctionRelistFeature.State.WAIT_AUCTION_MENU, tick);
         }, () -> this.finishCycle(client, tick));
      }
   }

   private void waitForAuctionMenu(Minecraft client, long tick) {
      if (this.machine.ticksInState(tick) > 80L) {
         this.finishCycle(client, tick);
      } else if (EconomyMenus.titleContains(client, "аукцион", "auction")) {
         AbstractContainerMenu menu = client.player.containerMenu;
         int storage = EconomyMenus.findContainerSlot(
            menu, stack -> EconomyItemText.containsAny(stack, "хранилище", "storage", "истекшие", "expired", "снятые товары")
         );
         if (storage >= 0) {
            this.ownedContainerId = menu.containerId;
            if (EconomyMenus.click(client, menu, storage, 0, ContainerInput.PICKUP)) {
               this.machine.transition(AuctionRelistFeature.State.WAIT_STORAGE_MENU, tick);
            }
         }
      }
   }

   private void waitForStorageMenu(Minecraft client, long tick) {
      if (this.machine.ticksInState(tick) > 80L) {
         this.finishCycle(client, tick);
      } else if (EconomyMenus.titleContains(client, "хранилище", "storage")) {
         AbstractContainerMenu menu = client.player.containerMenu;
         int relist = EconomyMenus.findContainerSlot(
            menu, stack -> EconomyItemText.containsAny(stack, "перевыставить", "перевыстав", "выставить снова", "relist")
         );
         if (relist < 0) {
            if (this.machine.ticksInState(tick) >= 10L) {
               this.ownedContainerId = menu.containerId;
               this.machine.transition(AuctionRelistFeature.State.CLOSING, tick);
            }
         } else {
            this.ownedContainerId = menu.containerId;
            if (EconomyMenus.click(client, menu, relist, 0, ContainerInput.PICKUP)) {
               this.machine.transition(AuctionRelistFeature.State.CLOSING, tick);
            }
         }
      }
   }

   private void finishCycle(Minecraft client, long tick) {
      if (this.isRecognizedMenu(client)) {
         EconomyMenus.closeOwned(client, this.ownedContainerId);
      }

      this.ownedContainerId = -1;
      this.nextCycleTick = tick + 1200L;
      this.machine.transition(AuctionRelistFeature.State.WAIT, tick);
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private boolean isRecognizedMenu(Minecraft client) {
      return EconomyMenus.currentContainerId(client) == this.ownedContainerId
         && EconomyMenus.titleContains(client, "аукцион", "auction", "хранилище", "storage");
   }

   private static boolean isPlayerMoving(Minecraft client) {
      return client.player != null && client.player.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
   }

   private void resetRuntime(boolean closeScreen) {
      Minecraft client = Minecraft.getInstance();
      if (closeScreen && this.isRecognizedMenu(client)) {
         EconomyMenus.closeOwned(client, this.ownedContainerId);
      }

      this.machine.reset(0L);
      this.commandCooldown.reset();
      this.ownedContainerId = -1;
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }

      this.nextCycleTick = 1200L;
      this.lastTick = 0L;
   }

   enum State {
      WAIT,
      OPEN_AUCTION,
      WAIT_AUCTION_MENU,
      WAIT_STORAGE_MENU,
      CLOSING;
   }
}
