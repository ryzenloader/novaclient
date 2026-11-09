package win.furryhook.feature.impl.pve;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.AttackEvent;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.PlayerInputEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.packet.PacketSendEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.feature.setting.TextSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.mining.MineTimer;
import win.furryhook.pve.mining.MiningInventory;
import win.furryhook.pve.mining.MiningParsers;
import win.furryhook.pve.mining.MiningServerAdapter;
import win.furryhook.pve.mining.MiningServerAdapters;
import win.furryhook.pve.mining.MiningToolProfile;
import win.furryhook.pve.navigation.BaritoneNavigator;
import win.furryhook.utils.inventory.DropAllInventoryController;
import win.furryhook.utils.inventory.InventorySwap;
import win.furryhook.utils.inventory.InventoryUtil;

public final class MineHelperFeature extends PveFeature {
   private static final String NEXT_MINE = "Next Mine";
   private static final String CLEAN_INVENTORY = "Clean Inventory";
   private static final String SAVE_PICKAXE = "Save Pickaxe";
   private static final int TIMER_SCAN_INTERVAL_TICKS = 60;
   public final MultiSelectSetting helpers = this.register(
      new MultiSelectSetting("Helpers", Set.of("Next Mine", "Clean Inventory", "Save Pickaxe"), "Next Mine", "Clean Inventory", "Save Pickaxe")
   );
   public final NumberSetting cleanupAtFreeSlots = this.register(new NumberSetting("Cleanup At Free Slots", 2.0, 1.0, 12.0, 1.0, ""));
   public final NumberSetting dropInterval = this.register(
      new NumberSetting("Drop Interval", 1.0, 1.0, 60.0, 1.0, " s").visibleWhen(() -> this.helpers.isSelected("Clean Inventory"))
   );
   public final TextSetting trashItems = this.register(
      new TextSetting("Trash Items", "cobblestone,cobbled_deepslate,dirt,gravel,andesite,diorite,granite,tuff,netherrack,deepslate,stone", 512)
   );
   private MineTimer currentTimer;
   private long tick;
   private long lastTrashActionTick;
   private boolean pickaxeProtected;
   private BlockPos miningInProgress;
   private final Deque<Integer> trashDropSlots = new ArrayDeque<>();
   private Set<String> activeTrashItems = Set.of();
   private MineHelperFeature.TrashDropState trashDropState = MineHelperFeature.TrashDropState.IDLE;

   public MineHelperFeature() {
      super("MineHelper", "Shows mine timers, removes configured trash and protects pickaxes", FeatureCategory.MISC, -1, AutomationPriority.BACKGROUND);
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntime();
   }

   @Override
   protected void onPveDisable() {
      this.cancelTrashDrop();
      this.resetRuntime();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cancelTrashDrop();
      this.resetRuntime();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      this.tick++;
      if (player != null && client.level != null && client.gameMode != null) {
         if (this.trashDropState != MineHelperFeature.TrashDropState.IDLE) {
            this.tickTrashDrop(client, player);
         }

         if (this.helpers.isSelected("Next Mine") && (this.currentTimer == null || this.tick % 60L == 0L)) {
            this.updateMineTimer(client);
         }

         if (this.currentTimer != null && this.currentTimer.isExpired(System.currentTimeMillis())) {
            this.currentTimer = null;
         }

         boolean wasProtected = this.pickaxeProtected;
         this.pickaxeProtected = this.helpers.isSelected("Save Pickaxe") && this.shouldProtect(player.getMainHandItem());
         if (this.pickaxeProtected && !wasProtected) {
            this.abortMiningInProgress(player);
         }

         if (this.pickaxeProtected) {
            client.gameMode.stopDestroyBlock();
         }

         if (this.trashDropState == MineHelperFeature.TrashDropState.IDLE
            && this.helpers.isSelected("Clean Inventory")
            && MiningInventory.freeSlots(player) <= this.cleanupAtFreeSlots.getValue().intValue()
            && this.tick - this.lastTrashActionTick >= this.dropIntervalTicks()) {
            this.tryClearTrash(client, player);
         }
      } else {
         this.cancelTrashDrop();
         this.pickaxeProtected = false;
      }
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      if (this.trashDropState != MineHelperFeature.TrashDropState.IDLE) {
         event.clearMovement(false, true);
      }
   }

   @EventTarget
   public void onAttack(AttackEvent event) {
      LocalPlayer player = event.getClient().player;
      if (this.helpers.isSelected("Save Pickaxe") && player != null && this.shouldProtect(player.getMainHandItem())) {
         this.pickaxeProtected = true;
         event.cancel();
         if (event.getClient().gameMode != null) {
            event.getClient().gameMode.stopDestroyBlock();
         }
      }
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.PRE && event.getPacket() instanceof ServerboundPlayerActionPacket packet) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            Action action = packet.getAction();
            if (action == Action.START_DESTROY_BLOCK) {
               this.miningInProgress = packet.getPos().immutable();
            } else if ((action == Action.STOP_DESTROY_BLOCK || action == Action.ABORT_DESTROY_BLOCK)
               && this.miningInProgress != null
               && this.miningInProgress.equals(packet.getPos())) {
               this.miningInProgress = null;
            }

            if (this.helpers.isSelected("Save Pickaxe") && this.shouldProtect(player.getMainHandItem())) {
               if (action == Action.START_DESTROY_BLOCK || action == Action.STOP_DESTROY_BLOCK) {
                  if (action == Action.START_DESTROY_BLOCK) {
                     this.abortMiningInProgress(player);
                  }

                  this.miningInProgress = null;
                  this.pickaxeProtected = true;
                  event.cancel();
               }
            }
         }
      }
   }

   private void abortMiningInProgress(LocalPlayer player) {
      if (this.miningInProgress != null && player.connection != null) {
         player.connection.send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, this.miningInProgress, Direction.UP));
         this.miningInProgress = null;
      }
   }

   public Optional<MineTimer> getCurrentTimer() {
      return Optional.ofNullable(this.currentTimer);
   }

   public boolean isMineTimerSelected() {
      return this.helpers.isSelected("Next Mine");
   }

   public boolean isPickaxeProtected() {
      return this.pickaxeProtected;
   }

   public boolean tryClearTrash() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      return player != null && this.tryClearTrash(client, player);
   }

   private void updateMineTimer(Minecraft client) {
      MiningServerAdapter adapter = MiningServerAdapters.forProfile(PveManagerFeature.INSTANCE.resolveServerProfile(client));
      List<String> lines = this.hologramLines(client);
      adapter.parseMineTimer(lines, System.currentTimeMillis()).ifPresent(timer -> this.currentTimer = timer);
   }

   private List<String> hologramLines(Minecraft client) {
      if (client.level == null) {
         return List.of();
      }

      List<ArmorStand> stands = new ArrayList<>();

      for (Entity entity : client.level.entitiesForRendering()) {
         if (entity instanceof ArmorStand stand && stand.hasCustomName() && stand.getCustomName() != null) {
            stands.add(stand);
         }
      }

      stands.sort(Comparator.<ArmorStand>comparingDouble(standx -> standx.getY()).reversed());
      return stands.stream().map(standx -> standx.getCustomName().getString()).toList();
   }

   private boolean tryClearTrash(Minecraft client, LocalPlayer player) {
      if (player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.getCarried().isEmpty()
         && client.gui.screen() == null
         && this.trashDropState == MineHelperFeature.TrashDropState.IDLE
         && !InventorySwap.isBusy()
         && !DropAllInventoryController.blocksInventoryOperations()
         && this.claim(AutomationResource.INVENTORY)) {
         this.activeTrashItems = MiningParsers.identifiers(this.trashItems.getValue());
         this.trashDropSlots.clear();

         for (int slot = 9; slot < 45; slot++) {
            if (MiningInventory.isTrash(player.inventoryMenu.getSlot(slot).getItem(), this.activeTrashItems)) {
               this.trashDropSlots.addLast(slot);
            }
         }

         if (MiningInventory.isTrash(player.inventoryMenu.getSlot(45).getItem(), this.activeTrashItems)) {
            this.trashDropSlots.addLast(45);
         }

         if (this.trashDropSlots.isEmpty()) {
            this.lastTrashActionTick = this.tick;
            this.release(AutomationResource.INVENTORY);
            return false;
         } else {
            BaritoneNavigator.INSTANCE.cancel();
            client.gameMode.stopDestroyBlock();
            this.trashDropState = MineHelperFeature.TrashDropState.PREPARING;
            return true;
         }
      } else {
         return false;
      }
   }

   private void tickTrashDrop(Minecraft client, LocalPlayer player) {
      if (player.containerMenu == player.inventoryMenu && player.inventoryMenu.getCarried().isEmpty() && client.gui.screen() == null) {
         client.gameMode.stopDestroyBlock();
         switch (this.trashDropState) {
            case PREPARING:
               this.trashDropState = MineHelperFeature.TrashDropState.DROPPING;
               break;
            case DROPPING:
               while (!this.trashDropSlots.isEmpty()) {
                  int slot = this.trashDropSlots.removeFirst();
                  if (player.inventoryMenu.isValidSlotIndex(slot)
                     && MiningInventory.isTrash(player.inventoryMenu.getSlot(slot).getItem(), this.activeTrashItems)) {
                     InventoryUtil.dropPlayerStack(player, slot);
                     return;
                  }
               }

               this.trashDropState = MineHelperFeature.TrashDropState.SETTLING;
               break;
            case SETTLING:
               this.finishTrashDrop();
         }
      } else {
         this.cancelTrashDrop();
      }
   }

   private void finishTrashDrop() {
      if (this.trashDropState != MineHelperFeature.TrashDropState.IDLE) {
         this.trashDropSlots.clear();
         this.activeTrashItems = Set.of();
         this.trashDropState = MineHelperFeature.TrashDropState.IDLE;
         this.lastTrashActionTick = this.tick;
         this.release(AutomationResource.INVENTORY);
      }
   }

   private void cancelTrashDrop() {
      this.finishTrashDrop();
   }

   private boolean shouldProtect(ItemStack stack) {
      MiningToolProfile profile = MiningToolProfile.detect(PveManagerFeature.INSTANCE.resolveServerProfile(Minecraft.getInstance()), stack);
      return MiningInventory.isPickaxe(stack)
         && stack.isDamageableItem()
         && MiningInventory.remainingDurability(stack) <= profile.durabilityReserve(stack, PveManagerFeature.INSTANCE.minimumToolDurability.getValue());
   }

   private long dropIntervalTicks() {
      return Math.max(20L, Math.round(this.dropInterval.getValue() * 20.0));
   }

   private void resetRuntime() {
      this.currentTimer = null;
      this.tick = 0L;
      this.lastTrashActionTick = -4611686018427387904L;
      this.pickaxeProtected = false;
      this.miningInProgress = null;
      this.trashDropSlots.clear();
      this.activeTrashItems = Set.of();
      this.trashDropState = MineHelperFeature.TrashDropState.IDLE;
   }

   private enum TrashDropState {
      IDLE,
      PREPARING,
      DROPPING,
      SETTLING;
   }
}
