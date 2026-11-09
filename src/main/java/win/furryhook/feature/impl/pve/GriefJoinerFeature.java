package win.furryhook.feature.impl.pve;

import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.DisconnectEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.TextSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.PveStateMachine;
import win.furryhook.pve.economy.CommandCooldown;
import win.furryhook.pve.economy.EconomyChat;
import win.furryhook.pve.economy.EconomyInventory;
import win.furryhook.pve.economy.EconomyItemText;
import win.furryhook.pve.economy.EconomyMenus;
import win.furryhook.pve.economy.EconomyTextParser;
import win.furryhook.pve.economy.ServerUiText;
import win.furryhook.pve.server.ServerAdapters;
import win.furryhook.pve.server.ServerProfile;
import win.furryhook.utils.inventory.InventoryUtil;

public final class GriefJoinerFeature extends PveFeature {
   public static final String MODE_REALLYWORLD = "ReallyWorld";
   public static final String MODE_SPOOKYTIME = "SpookyTime";
   private static final long RETRY_TICKS = 400L;
   private static final long MENU_TIMEOUT_TICKS = 100L;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "ReallyWorld", "ReallyWorld", "SpookyTime"));
   public final BooleanSetting mega = this.register(new BooleanSetting("Mega", false).visibleWhen(() -> this.mode.is("ReallyWorld")));
   public final TextSetting griefNumber = this.register(
      new TextSetting("Grief Number", "1", 3).visibleWhen(() -> this.mode.is("ReallyWorld") && !this.mega.getValue())
   );
   private final PveStateMachine<GriefJoinerFeature.State> machine = new PveStateMachine<>(GriefJoinerFeature.State.OPEN_SELECTOR);
   private final CommandCooldown actionCooldown = new CommandCooldown();
   private long lastTick;
   private long retryAtTick;
   private int ownedContainerId = -1;
   private boolean resourcesClaimed;

   public GriefJoinerFeature() {
      super("GriefJoiner", "Retries the selected grief server through its validated selector menus", FeatureCategory.MISC, -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null) {
         long tick = client.level.getGameTime();
         this.lastTick = tick;
         if (this.isSupported(client)) {
            if (this.hasJoined(client)) {
               this.setEnabled(false);
            } else if (this.ensureResources(client, tick)) {
               if (tick >= this.retryAtTick) {
                  switch ((GriefJoinerFeature.State)this.machine.state()) {
                     case OPEN_SELECTOR:
                        this.openSelector(client, player, tick);
                        break;
                     case SELECT_CATEGORY:
                     case SELECT_SERVER:
                        this.selectMenuEntry(client, tick);
                        break;
                     case WAIT_JOIN:
                        if (this.machine.ticksInState(tick) > 100L) {
                           this.closeOwned(client);
                           this.machine.transition(GriefJoinerFeature.State.OPEN_SELECTOR, tick);
                        }
                        break;
                     case RETRY_DELAY:
                        if (tick >= this.retryAtTick) {
                           this.machine.transition(GriefJoinerFeature.State.OPEN_SELECTOR, tick);
                        }
                  }
               }
            }
         } else {
            if (this.resourcesClaimed || this.ownedContainerId >= 0 || !this.machine.is(GriefJoinerFeature.State.OPEN_SELECTOR)) {
               this.resetRuntime(true);
            }
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         String text = EconomyChat.incomingText(event.getPacket());
         if (text != null
            && EconomyTextParser.containsAny(
               text,
               "к сожалению сервер переполнен",
               "подождите 20 секунд",
               "большой поток игроков",
               "imperator",
               "подождите несколько секунд",
               "server is full",
               "too many players"
            )) {
            Minecraft.getInstance().execute(() -> {
               if (this.isEnabled()) {
                  this.closeOwned(Minecraft.getInstance());
                  this.retryAtTick = this.lastTick + 400L;
                  this.machine.transition(GriefJoinerFeature.State.RETRY_DELAY, this.lastTick);
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
   }

   @Override
   protected void onPveDisable() {
      this.resetRuntime(true);
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resetRuntime(true);
   }

   private void openSelector(Minecraft client, LocalPlayer player, long tick) {
      if (client.gui.screen() == null && this.actionCooldown.ready(tick)) {
         int previous = player.getInventory().getSelectedSlot();
         int compassMenuSlot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.is(Items.COMPASS));
         boolean swapped = false;
         int compass;
         if (compassMenuSlot >= 36 && compassMenuSlot <= 44) {
            compass = compassMenuSlot - 36;
         } else if (compassMenuSlot >= 9
            && compassMenuSlot < 36
            && player.containerMenu == player.inventoryMenu
            && InventoryUtil.swapWithHotbar(compassMenuSlot, previous)) {
            compass = previous;
            swapped = true;
         } else {
            compass = -1;
         }

         if (compass >= 0 && EconomyInventory.selectHotbar(player, compass)) {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
            if (swapped) {
               InventoryUtil.swapWithHotbar(compassMenuSlot, previous);
            }

            EconomyInventory.selectHotbar(player, previous);
            this.actionCooldown.tryAcquire(tick, 40L);
            this.machine.transition(GriefJoinerFeature.State.SELECT_CATEGORY, tick);
         } else {
            this.actionCooldown.defer(tick, 40L);
         }
      }
   }

   private void selectMenuEntry(Minecraft client, long tick) {
      if (client.player.containerMenu == client.player.inventoryMenu) {
         if (this.machine.ticksInState(tick) > 100L) {
            this.machine.transition(GriefJoinerFeature.State.OPEN_SELECTOR, tick);
         }
      } else if (this.actionCooldown.ready(tick)) {
         AbstractContainerMenu menu = client.player.containerMenu;
         int specific = EconomyMenus.findContainerSlot(menu, stack -> this.isSpecificTarget(stack));
         if (specific >= 0) {
            this.ownedContainerId = menu.containerId;
            if (EconomyMenus.click(client, menu, specific, 0, ContainerInput.PICKUP)) {
               this.actionCooldown.tryAcquire(tick, 10L);
               this.machine.transition(GriefJoinerFeature.State.WAIT_JOIN, tick);
            }
         } else {
            if (this.machine.is(GriefJoinerFeature.State.SELECT_CATEGORY)) {
               int category = EconomyMenus.findContainerSlot(
                  menu, stack -> EconomyItemText.containsAny(stack, "гриферское выживание", "grief survival", "spookytime", "reallyworld")
               );
               if (category >= 0) {
                  this.ownedContainerId = menu.containerId;
                  if (EconomyMenus.click(client, menu, category, 0, ContainerInput.PICKUP)) {
                     this.actionCooldown.tryAcquire(tick, 10L);
                     this.machine.transition(GriefJoinerFeature.State.SELECT_SERVER, tick);
                  }

                  return;
               }
            }

            if (this.machine.ticksInState(tick) > 100L) {
               this.closeOwned(client);
               this.machine.transition(GriefJoinerFeature.State.OPEN_SELECTOR, tick);
            }
         }
      }
   }

   private boolean isSpecificTarget(ItemStack stack) {
      String text = EconomyTextParser.normalize(EconomyItemText.combined(stack));
      if (this.mode.is("SpookyTime")) {
         return EconomyTextParser.containsAny(text, "гриф", "grief") && !EconomyTextParser.containsAny(text, "хаб", "hub", "лобби", "lobby");
      }

      if (this.mega.getValue()) {
         return EconomyTextParser.containsAny(text, "мега", "mega") && EconomyTextParser.containsAny(text, "гриф", "grief", "выживание", "survival");
      }

      int number = EconomyTextParser.positiveInt(this.griefNumber.getValue(), 1, 999);
      Pattern exactNumber = Pattern.compile("(?<!\\d)" + number + "(?!\\d)");
      return exactNumber.matcher(text).find() && EconomyTextParser.containsAny(text, "гриф", "grief", "сервер", "server");
   }

   private boolean hasJoined(Minecraft client) {
      String header = ServerUiText.tabHeader(client);
      return !this.mode.is("SpookyTime")
         ? EconomyTextParser.containsAny(header, "гриферское выживание", "grief survival")
         : header.contains("spookytime") && !EconomyTextParser.containsAny(header, "хаб", "hub", "лобби", "lobby");
   }

   private boolean isSupported(Minecraft client) {
      if (this.mode.is("ReallyWorld")) {
         return ServerAdapters.current().profile() == ServerProfile.REALLYWORLD;
      }

      String host = ServerUiText.serverHost(client);
      return host.equals("spookytime.net") || host.endsWith(".spookytime.net");
   }

   private boolean ensureResources(Minecraft client, long tick) {
      if (this.resourcesClaimed) {
         return true;
      }

      if (client.gui.screen() == null && client.player != null && client.player.containerMenu == client.player.inventoryMenu) {
         if (!this.claim(AutomationResource.INVENTORY, AutomationResource.SCREEN)) {
            this.actionCooldown.defer(tick, 5L);
            return false;
         } else {
            this.resourcesClaimed = true;
            return true;
         }
      } else {
         this.actionCooldown.defer(tick, 5L);
         return false;
      }
   }

   private void closeOwned(Minecraft client) {
      if (EconomyMenus.currentContainerId(client) == this.ownedContainerId) {
         EconomyMenus.closeOwned(client, this.ownedContainerId);
      }

      this.ownedContainerId = -1;
   }

   private void resetRuntime(boolean closeScreen) {
      if (closeScreen) {
         this.closeOwned(Minecraft.getInstance());
      }

      this.machine.reset(0L);
      this.actionCooldown.reset();
      this.retryAtTick = 0L;
      this.ownedContainerId = -1;
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }

      this.lastTick = 0L;
   }

   enum State {
      OPEN_SELECTOR,
      SELECT_CATEGORY,
      SELECT_SERVER,
      WAIT_JOIN,
      RETRY_DELAY;
   }
}
