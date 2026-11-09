package win.furryhook.feature.impl.pve;

import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.DisconnectEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.PveStateMachine;
import win.furryhook.pve.economy.AuctionPriceScanner;
import win.furryhook.pve.economy.CommandCooldown;
import win.furryhook.pve.economy.CraftingMenuController;
import win.furryhook.pve.economy.EconomyAutomationPolicy;
import win.furryhook.pve.economy.EconomyChat;
import win.furryhook.pve.economy.EconomyCommands;
import win.furryhook.pve.economy.EconomyInventory;
import win.furryhook.pve.economy.EconomyMenus;
import win.furryhook.pve.economy.EconomyNavigator;
import win.furryhook.pve.economy.EconomyTextParser;
import win.furryhook.pve.economy.NearbyEconomyBlocks;
import win.furryhook.pve.navigation.BaritoneNavigator;
import win.furryhook.pve.server.ServerAdapter;
import win.furryhook.pve.server.ServerAdapters;
import win.furryhook.pve.server.ServerProfile;
import win.furryhook.utils.inventory.InventoryUtil;

public final class AutoCrafterFeature extends PveFeature {
   public static final String RECIPE_ENCHANTED_GOLDEN_APPLE = "Enchanted Golden Apple";
   private static final int SEARCH_RADIUS = 16;
   private static final long MOVE_TIMEOUT_TICKS = 240L;
   private static final long OPEN_TIMEOUT_TICKS = 100L;
   private static final long AUCTION_RETRY_TICKS = 1200L;
   public final ModeSetting craft = this.register(new ModeSetting("Craft", "Enchanted Golden Apple", "Enchanted Golden Apple"));
   public final BooleanSetting autoSell = this.register(new BooleanSetting("Auto Sell", true));
   public final BooleanSetting takeCrafterChest = this.register(
      new BooleanSetting("Take from Crafter Chest", false).visibleWhen(() -> !SynchronizationFeature.isActive())
   );
   private final PveStateMachine<AutoCrafterFeature.State> machine = new PveStateMachine<>(AutoCrafterFeature.State.WAIT);
   private final EconomyNavigator navigator = new EconomyNavigator(BaritoneNavigator.INSTANCE);
   private final CraftingMenuController crafting = new CraftingMenuController();
   private final CommandCooldown actionCooldown = new CommandCooldown();
   private final CommandCooldown commandCooldown = new CommandCooldown();
   private long lastTick;
   private long nextWorkTick;
   private long auctionRetryTick;
   private BlockPos targetBlock;
   private AutoCrafterFeature.ChestKind chestKind;
   private CraftingMenuController.Recipe pendingRecipe;
   private int ownedContainerId = -1;
   private int lootActions;
   private boolean resourcesClaimed;
   private int saleCount;
   private long salePrice;
   private int originalSelectedSlot = -1;
   private int saleSwapMenuSlot = -1;
   private int saleHotbarSlot = -1;
   private boolean saleConfirmed;
   private boolean saleRejected;

   public AutoCrafterFeature() {
      super("AutoCrafter", "Crafts enchanted golden apples from signed resource chests", FeatureCategory.MISC, -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null) {
         long tick = client.level.getGameTime();
         this.lastTick = tick;
         if (ServerAdapters.current().profile() == ServerProfile.FUNTIME) {
            switch ((AutoCrafterFeature.State)this.machine.state()) {
               case WAIT:
                  if (tick >= this.nextWorkTick) {
                     this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
                  }
                  break;
               case SELECT:
                  this.selectWork(client, player, tick);
                  break;
               case FIND_CHEST:
                  this.findChest(client, player, tick);
                  break;
               case MOVE_CHEST:
                  this.moveToTarget(player, AutoCrafterFeature.State.OPEN_CHEST, tick);
                  break;
               case OPEN_CHEST:
                  this.openChest(client, player, tick);
                  break;
               case LOOT_CHEST:
                  this.lootChest(client, player, tick);
                  break;
               case FIND_TABLE:
                  this.findTable(client, player, tick);
                  break;
               case MOVE_TABLE:
                  this.moveToTarget(player, AutoCrafterFeature.State.OPEN_TABLE, tick);
                  break;
               case OPEN_TABLE:
                  this.openTable(client, player, tick);
                  break;
               case CRAFT:
                  this.craft(client, tick);
                  break;
               case OPEN_AUCTION_SEARCH:
                  this.openAuctionSearch(client, player, tick);
                  break;
               case WAIT_AUCTION_SEARCH:
                  this.waitAuctionSearch(client, tick);
                  break;
               case LIST_AUCTION:
                  this.listAuction(client, player, tick);
                  break;
               case WAIT_SALE_CONFIRMATION:
                  this.waitSaleConfirmation(client, tick);
            }
         } else {
            if (this.resourcesClaimed || !this.machine.is(AutoCrafterFeature.State.WAIT) || this.ownedContainerId >= 0) {
               this.finishCycle(client, tick, 20L);
            }
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         String text = EconomyChat.incomingText(event.getPacket());
         if (text != null) {
            String normalized = EconomyTextParser.normalize(text);
            Minecraft.getInstance().execute(() -> {
               if (this.isEnabled()) {
                  if (EconomyTextParser.containsAny(normalized, "не удалось выставить", "хранилищ", "слот", "ah rent", "auction slots are full")) {
                     this.saleRejected = true;
                     this.auctionRetryTick = this.lastTick + 1200L;
                  } else if (EconomyTextParser.containsAny(normalized, "выставлен на продажу", "listed for sale")) {
                     this.saleConfirmed = true;
                     this.auctionRetryTick = 0L;
                  }
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

   private void selectWork(Minecraft client, LocalPlayer player, long tick) {
      if (client.gui.screen() != null || player.containerMenu != player.inventoryMenu) {
         this.finishCycle(client, tick, 20L);
      } else if (this.ensureCycleResources(tick)) {
         int output = EconomyInventory.count(player, Items.ENCHANTED_GOLDEN_APPLE);
         int apples = EconomyInventory.count(player, Items.APPLE);
         int blocks = EconomyInventory.count(player, Items.GOLD_BLOCK);
         int ingots = EconomyInventory.count(player, Items.GOLD_INGOT);
         boolean canTake = this.canTakeResources();
         switch (EconomyAutomationPolicy.crafterAction(output, apples, blocks, ingots, this.autoSell.getValue(), canTake, tick >= this.auctionRetryTick)) {
            case SELL:
               this.machine.transition(AutoCrafterFeature.State.OPEN_AUCTION_SEARCH, tick);
               break;
            case CRAFT_APPLE:
               this.pendingRecipe = CraftingMenuController.Recipe.ENCHANTED_GOLDEN_APPLE;
               this.machine.transition(AutoCrafterFeature.State.FIND_TABLE, tick);
               break;
            case CRAFT_GOLD_BLOCK:
               this.pendingRecipe = CraftingMenuController.Recipe.GOLD_BLOCK;
               this.machine.transition(AutoCrafterFeature.State.FIND_TABLE, tick);
               break;
            case TAKE_APPLES:
               this.chestKind = SynchronizationFeature.isActive() ? AutoCrafterFeature.ChestKind.APPLE : AutoCrafterFeature.ChestKind.CRAFTER;
               this.machine.transition(AutoCrafterFeature.State.FIND_CHEST, tick);
               break;
            case TAKE_GOLD:
               this.chestKind = SynchronizationFeature.isActive() ? AutoCrafterFeature.ChestKind.GOLD : AutoCrafterFeature.ChestKind.CRAFTER;
               this.machine.transition(AutoCrafterFeature.State.FIND_CHEST, tick);
               break;
            case WAIT:
               this.finishCycle(client, tick, 40L);
         }
      }
   }

   private void findChest(Minecraft client, LocalPlayer player, long tick) {
      this.targetBlock = this.findSignedChest(client, player, this.chestKind);
      if (this.targetBlock == null) {
         if (this.autoSell.getValue() && tick >= this.auctionRetryTick && EconomyInventory.count(player, Items.ENCHANTED_GOLDEN_APPLE) > 0) {
            this.machine.transition(AutoCrafterFeature.State.OPEN_AUCTION_SEARCH, tick);
         } else {
            this.finishCycle(client, tick, 100L);
         }
      } else {
         this.machine.transition(AutoCrafterFeature.State.MOVE_CHEST, tick);
      }
   }

   private void findTable(Minecraft client, LocalPlayer player, long tick) {
      this.targetBlock = NearbyEconomyBlocks.nearestCraftingTable(client.level, player, 16);
      if (this.targetBlock == null) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.machine.transition(AutoCrafterFeature.State.MOVE_TABLE, tick);
      }
   }

   private void moveToTarget(LocalPlayer player, AutoCrafterFeature.State next, long tick) {
      if (this.targetBlock == null) {
         this.finishCycle(Minecraft.getInstance(), tick, 100L);
      } else if (EconomyNavigator.arrived(player, this.targetBlock, 3.7)) {
         this.navigator.cancel();
         this.machine.transition(next, tick);
      } else {
         this.navigator.moveTo(player, this.targetBlock, 3);
         if (this.machine.ticksInState(tick) > 240L) {
            this.finishCycle(Minecraft.getInstance(), tick, 100L);
         }
      }
   }

   private void openChest(Minecraft client, LocalPlayer player, long tick) {
      if (player.containerMenu instanceof ChestMenu menu && player.containerMenu != player.inventoryMenu) {
         this.ownedContainerId = menu.containerId;
         this.lootActions = 0;
         this.machine.transition(AutoCrafterFeature.State.LOOT_CHEST, tick);
      } else if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.interactTarget(client, player, tick);
      }
   }

   private void openTable(Minecraft client, LocalPlayer player, long tick) {
      if (player.containerMenu instanceof CraftingMenu menu) {
         this.ownedContainerId = menu.containerId;
         this.crafting.reset();
         this.machine.transition(AutoCrafterFeature.State.CRAFT, tick);
      } else if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.interactTarget(client, player, tick);
      }
   }

   private void interactTarget(Minecraft client, LocalPlayer player, long tick) {
      if (this.targetBlock != null && this.actionCooldown.tryAcquire(tick, 10L)) {
         client.gameMode
            .useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(this.targetBlock), Direction.UP, this.targetBlock, false));
         player.swing(InteractionHand.MAIN_HAND);
      }
   }

   private void lootChest(Minecraft client, LocalPlayer player, long tick) {
      if (player.containerMenu instanceof ChestMenu menu && menu.containerId == this.ownedContainerId) {
         int apples = EconomyInventory.count(player, Items.APPLE);
         int blocks = EconomyInventory.count(player, Items.GOLD_BLOCK);
         int ingots = EconomyInventory.count(player, Items.GOLD_INGOT);
         if (apples >= 1 && (blocks >= 8 || ingots >= 9)) {
            this.closeOwned(client);
            this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
         } else if (this.actionCooldown.ready(tick)) {
            int source = EconomyMenus.findContainerSlot(menu, stack -> this.shouldLoot(stack, apples, blocks, ingots));
            if (source >= 0 && this.lootActions++ < 20) {
               if (EconomyMenus.quickMove(client, menu, source)) {
                  this.actionCooldown.tryAcquire(tick, 3L);
               }
            } else {
               this.closeOwned(client);
               this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
            }
         }
      } else {
         this.finishCycle(client, tick, 60L);
      }
   }

   private boolean shouldLoot(ItemStack stack, int apples, int blocks, int ingots) {
      return switch (this.chestKind) {
         case APPLE -> apples < 1 && stack.is(Items.APPLE);
         case GOLD -> blocks < 8 && (stack.is(Items.GOLD_BLOCK) || ingots < 72 && stack.is(Items.GOLD_INGOT));
         case CRAFTER -> apples < 1 && stack.is(Items.APPLE) || blocks < 8 && (stack.is(Items.GOLD_BLOCK) || ingots < 72 && stack.is(Items.GOLD_INGOT));
      };
   }

   private void craft(Minecraft client, long tick) {
      if (!(client.player.containerMenu instanceof CraftingMenu menu && menu.containerId == this.ownedContainerId && this.pendingRecipe != null)) {
         this.finishCycle(client, tick, 60L);
      } else if (this.actionCooldown.ready(tick)) {
         CraftingMenuController.Result result = this.crafting.tick(client, menu, this.pendingRecipe);
         this.actionCooldown.tryAcquire(tick, 2L);
         if (result == CraftingMenuController.Result.CRAFTED) {
            this.closeOwned(client);
            this.pendingRecipe = null;
            this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
         } else if (result == CraftingMenuController.Result.FAILED) {
            this.crafting.cleanup(client, menu);
            this.finishCycle(client, tick, 100L);
         }
      }
   }

   private void openAuctionSearch(Minecraft client, LocalPlayer player, long tick) {
      if (this.commandCooldown.ready(tick)) {
         ServerAdapter adapter = ServerAdapters.current();
         String query = "зачарованное золотое яблоко";
         adapter.auctionCommand().flatMap(root -> EconomyCommands.auctionSearch(root, query)).ifPresentOrElse(command -> {
            this.saleCount = largestStackCount(player, Items.ENCHANTED_GOLDEN_APPLE);
            if (this.saleCount <= 0) {
               this.finishCycle(client, tick, 40L);
            } else {
               adapter.sendCommand(player, command);
               this.commandCooldown.tryAcquire(tick, 40L);
               this.machine.transition(AutoCrafterFeature.State.WAIT_AUCTION_SEARCH, tick);
            }
         }, () -> this.finishCycle(client, tick, 100L));
      }
   }

   private void waitAuctionSearch(Minecraft client, long tick) {
      if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else if (EconomyMenus.titleContains(client, "аукцион", "auction")) {
         AbstractContainerMenu menu = client.player.containerMenu;
         this.ownedContainerId = menu.containerId;
         OptionalLong price = AuctionPriceScanner.competitivePrice(menu, Items.ENCHANTED_GOLDEN_APPLE, "зачарованное золотое яблоко", this.saleCount);
         if (!price.isEmpty()) {
            this.salePrice = price.getAsLong();
            this.closeOwned(client);
            this.machine.transition(AutoCrafterFeature.State.LIST_AUCTION, tick);
         }
      }
   }

   private void listAuction(Minecraft client, LocalPlayer player, long tick) {
      if (client.gui.screen() == null && this.commandCooldown.ready(tick) && this.prepareSaleStack(player)) {
         ServerAdapter adapter = ServerAdapters.current();
         adapter.auctionCommand().flatMap(root -> EconomyCommands.auctionSell(root, this.salePrice)).ifPresentOrElse(command -> {
            adapter.sendCommand(player, command);
            this.commandCooldown.tryAcquire(tick, 100L);
            this.saleConfirmed = false;
            this.saleRejected = false;
            this.machine.transition(AutoCrafterFeature.State.WAIT_SALE_CONFIRMATION, tick);
         }, () -> this.finishCycle(client, tick, 100L));
      } else {
         if (this.machine.ticksInState(tick) > 100L) {
            this.finishCycle(client, tick, 100L);
         }
      }
   }

   private void waitSaleConfirmation(Minecraft client, long tick) {
      if (this.saleConfirmed || this.saleRejected || this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, this.saleRejected ? 1200L : 40L);
      }
   }

   private boolean prepareSaleStack(LocalPlayer player) {
      if (this.saleCount <= 0) {
         return false;
      }

      ItemStack mainHand = player.getMainHandItem();
      if (mainHand.is(Items.ENCHANTED_GOLDEN_APPLE) && mainHand.getCount() == this.saleCount) {
         return true;
      }

      int menuSlot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE) && stack.getCount() == this.saleCount);
      if (menuSlot >= 0 && player.containerMenu == player.inventoryMenu) {
         if (this.originalSelectedSlot < 0) {
            this.originalSelectedSlot = player.getInventory().getSelectedSlot();
         }

         if (menuSlot >= 36 && menuSlot <= 44) {
            this.saleHotbarSlot = menuSlot - 36;
            return EconomyInventory.selectHotbar(player, this.saleHotbarSlot);
         } else {
            this.saleHotbarSlot = player.getInventory().getSelectedSlot();
            this.saleSwapMenuSlot = menuSlot;
            return InventoryUtil.swapWithHotbar(menuSlot, this.saleHotbarSlot);
         }
      } else {
         return false;
      }
   }

   private void restoreSaleStack(LocalPlayer player) {
      if (player != null && player.containerMenu == player.inventoryMenu) {
         if (this.saleSwapMenuSlot >= 0 && this.saleHotbarSlot >= 0) {
            InventoryUtil.swapWithHotbar(this.saleSwapMenuSlot, this.saleHotbarSlot);
         }

         if (this.originalSelectedSlot >= 0) {
            EconomyInventory.selectHotbar(player, this.originalSelectedSlot);
         }
      }

      this.originalSelectedSlot = -1;
      this.saleSwapMenuSlot = -1;
      this.saleHotbarSlot = -1;
   }

   private BlockPos findSignedChest(Minecraft client, LocalPlayer player, AutoCrafterFeature.ChestKind kind) {
      return switch (kind) {
         case APPLE -> this.firstSignedChest(client, player, "яблок", "apple");
         case GOLD -> this.firstSignedChest(client, player, "золот", "gold");
         case CRAFTER -> this.firstSignedChest(client, player, "крафтер", "crafter");
      };
   }

   private BlockPos firstSignedChest(Minecraft client, LocalPlayer player, String... labels) {
      for (String label : labels) {
         BlockPos found = NearbyEconomyBlocks.nearestSignedChest(client.level, player, 16, label);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private boolean canTakeResources() {
      return SynchronizationFeature.isActive() || this.takeCrafterChest.getValue();
   }

   private boolean ensureCycleResources(long tick) {
      if (this.resourcesClaimed) {
         return true;
      } else if (!this.claim(
         AutomationResource.MOVEMENT, AutomationResource.NAVIGATION, AutomationResource.INVENTORY, AutomationResource.SCREEN, AutomationResource.CHAT
      )) {
         this.nextWorkTick = tick + 5L;
         return false;
      } else {
         this.resourcesClaimed = true;
         return true;
      }
   }

   private void closeOwned(Minecraft client) {
      if (client.player == null
         || !(client.player.containerMenu instanceof CraftingMenu menu && menu.containerId == this.ownedContainerId && !this.crafting.cleanup(client, menu))) {
         EconomyMenus.closeOwned(client, this.ownedContainerId);
         this.ownedContainerId = -1;
      }
   }

   private void finishCycle(Minecraft client, long tick, long delayTicks) {
      this.closeOwned(client);
      this.navigator.close();
      this.restoreSaleStack(client.player);
      this.targetBlock = null;
      this.chestKind = null;
      this.pendingRecipe = null;
      this.saleCount = 0;
      this.salePrice = 0L;
      this.saleConfirmed = false;
      this.saleRejected = false;
      this.lootActions = 0;
      this.crafting.reset();
      this.machine.transition(AutoCrafterFeature.State.WAIT, tick);
      this.nextWorkTick = tick + Math.max(1L, delayTicks);
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private void resetRuntime(boolean closeScreen) {
      Minecraft client = Minecraft.getInstance();
      if (closeScreen) {
         this.closeOwned(client);
         this.restoreSaleStack(client.player);
      }

      this.navigator.close();
      this.machine.reset(0L);
      this.actionCooldown.reset();
      this.commandCooldown.reset();
      this.crafting.reset();
      this.nextWorkTick = 0L;
      this.auctionRetryTick = 0L;
      this.targetBlock = null;
      this.chestKind = null;
      this.pendingRecipe = null;
      this.ownedContainerId = -1;
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }

      this.saleCount = 0;
      this.salePrice = 0L;
      this.originalSelectedSlot = -1;
      this.saleSwapMenuSlot = -1;
      this.saleHotbarSlot = -1;
      this.saleConfirmed = false;
      this.saleRejected = false;
      this.lootActions = 0;
      this.lastTick = 0L;
   }

   private static int largestStackCount(LocalPlayer player, Item item) {
      int largest = 0;

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (stack.is(item)) {
            largest = Math.max(largest, stack.getCount());
         }
      }

      return largest;
   }

   enum ChestKind {
      APPLE,
      GOLD,
      CRAFTER;
   }

   enum State {
      WAIT,
      SELECT,
      FIND_CHEST,
      MOVE_CHEST,
      OPEN_CHEST,
      LOOT_CHEST,
      FIND_TABLE,
      MOVE_TABLE,
      OPEN_TABLE,
      CRAFT,
      OPEN_AUCTION_SEARCH,
      WAIT_AUCTION_SEARCH,
      LIST_AUCTION,
      WAIT_SALE_CONFIRMATION;
   }
}
