package win.furryhook.feature.impl.pve;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.DisconnectEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.PveStateMachine;
import win.furryhook.pve.economy.CommandCooldown;
import win.furryhook.pve.economy.EconomyInventory;
import win.furryhook.pve.economy.ServerUiText;
import win.furryhook.pve.server.ServerAdapters;
import win.furryhook.pve.server.ServerProfile;

public final class ClanUpgradeFeature extends PveFeature {
   private final PveStateMachine<ClanUpgradeFeature.State> machine = new PveStateMachine<>(ClanUpgradeFeature.State.FIND_ITEM);
   private final CommandCooldown actionCooldown = new CommandCooldown();
   private long lastTick;
   private int upgradeSlot = -1;
   private int restoreSlot = -1;
   private Item upgradeItem;
   private BlockPos supportBlock;
   private boolean resourcesClaimed;
   private boolean rotationSaved;
   private float savedYaw;
   private float savedPitch;

   public ClanUpgradeFeature() {
      super("ClanUpgrade", "Uses the held clan-upgrade items on the block below", FeatureCategory.MISC, -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null) {
         long tick = client.level.getGameTime();
         this.lastTick = tick;
         if (ServerAdapters.current().profile() != ServerProfile.FUNTIME
            || ServerUiText.tabHeader(client).contains("lobby")
            || ServerUiText.tabHeader(client).contains("лобби")) {
            this.cancelAction(player, tick);
         } else if (client.gui.screen() == null && player.containerMenu == player.inventoryMenu && !player.isUsingItem()) {
            switch ((ClanUpgradeFeature.State)this.machine.state()) {
               case FIND_ITEM:
                  this.findItem(player, tick);
                  break;
               case SELECT_ITEM:
                  this.selectItem(player, tick);
                  break;
               case PLACE:
                  this.placeItem(client, player, tick);
                  break;
               case BREAK:
                  this.breakPlacedItem(client, player, tick);
                  break;
               case COOLDOWN:
                  if (this.machine.ticksInState(tick) >= 5L) {
                     this.finishAction(player, tick);
                  }
            }
         } else {
            this.cancelAction(player, tick);
         }
      }
   }

   @EventTarget
   public void onDisconnect(DisconnectEvent event) {
      this.cancelAction(Minecraft.getInstance().player, this.lastTick);
      this.resetRuntime();
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntime();
   }

   @Override
   protected void onPveDisable() {
      this.cancelAction(Minecraft.getInstance().player, this.lastTick);
      this.resetRuntime();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelAction(Minecraft.getInstance().player, this.lastTick);
      this.resetRuntime();
   }

   private void findItem(LocalPlayer player, long tick) {
      if (this.actionCooldown.ready(tick)) {
         int slot = EconomyInventory.findHotbar(player, stack -> stack.is(Items.TORCH) || stack.is(Items.REDSTONE));
         if (slot < 0) {
            this.actionCooldown.defer(tick, 100L);
         } else {
            BlockPos below = player.blockPosition().below();
            if (!player.level().getBlockState(below).isFaceSturdy(player.level(), below, Direction.UP)) {
               this.actionCooldown.defer(tick, 20L);
            } else {
               boolean claimed = PveManagerFeature.INSTANCE.rotate.getValue()
                  ? this.claim(AutomationResource.INVENTORY, AutomationResource.ROTATION, AutomationResource.COMBAT)
                  : this.claim(AutomationResource.INVENTORY, AutomationResource.COMBAT);
               if (!claimed) {
                  this.actionCooldown.defer(tick, 5L);
               } else {
                  this.resourcesClaimed = true;
                  this.restoreSlot = player.getInventory().getSelectedSlot();
                  this.upgradeSlot = slot;
                  this.upgradeItem = player.getInventory().getItem(slot).getItem();
                  this.supportBlock = below.immutable();
                  this.machine.transition(ClanUpgradeFeature.State.SELECT_ITEM, tick);
               }
            }
         }
      }
   }

   private void selectItem(LocalPlayer player, long tick) {
      if (!EconomyInventory.selectHotbar(player, this.upgradeSlot)) {
         this.cancelAction(player, tick);
      } else {
         this.machine.transition(ClanUpgradeFeature.State.PLACE, tick);
      }
   }

   private void placeItem(Minecraft client, LocalPlayer player, long tick) {
      if (this.supportBlock != null
         && this.upgradeItem != null
         && player.getMainHandItem().is(this.upgradeItem)
         && player.level().getBlockState(this.supportBlock).isFaceSturdy(player.level(), this.supportBlock, Direction.UP)) {
         this.rotateToward(player, Vec3.atCenterOf(this.supportBlock));
         Vec3 hitPosition = Vec3.atCenterOf(this.supportBlock).add(0.0, 0.5, 0.0);
         client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hitPosition, Direction.UP, this.supportBlock, false));
         player.swing(InteractionHand.MAIN_HAND);
         this.machine.transition(ClanUpgradeFeature.State.BREAK, tick);
      } else {
         this.cancelAction(player, tick);
      }
   }

   private void breakPlacedItem(Minecraft client, LocalPlayer player, long tick) {
      if (this.supportBlock == null) {
         this.cancelAction(player, tick);
      } else {
         BlockPos placedPos = this.supportBlock.above();
         BlockState placedState = player.level().getBlockState(placedPos);
         boolean expectedBlock = this.upgradeItem == Items.TORCH
            ? placedState.is(Blocks.TORCH) || placedState.is(Blocks.WALL_TORCH)
            : this.upgradeItem == Items.REDSTONE && placedState.is(Blocks.REDSTONE_WIRE);
         if (!expectedBlock) {
            if (this.machine.ticksInState(tick) >= 10L) {
               this.actionCooldown.defer(tick, 20L);
               this.cancelAction(player, tick);
            }
         } else {
            this.rotateToward(player, Vec3.atCenterOf(placedPos));
            client.gameMode.startDestroyBlock(placedPos, Direction.UP);
            this.actionCooldown.tryAcquire(tick, 5L);
            this.machine.transition(ClanUpgradeFeature.State.COOLDOWN, tick);
         }
      }
   }

   private void finishAction(LocalPlayer player, long tick) {
      this.restoreRotation(player);
      this.restoreSlot(player);
      this.upgradeSlot = -1;
      this.upgradeItem = null;
      this.supportBlock = null;
      this.machine.transition(ClanUpgradeFeature.State.FIND_ITEM, tick);
      this.releaseResources();
   }

   private void cancelAction(LocalPlayer player, long tick) {
      this.restoreRotation(player);
      this.restoreSlot(player);
      this.upgradeSlot = -1;
      this.upgradeItem = null;
      this.supportBlock = null;
      this.machine.transition(ClanUpgradeFeature.State.FIND_ITEM, tick);
      this.releaseResources();
   }

   private void restoreSlot(LocalPlayer player) {
      if (this.restoreSlot >= 0 && this.restoreSlot < 9) {
         EconomyInventory.selectHotbar(player, this.restoreSlot);
      }

      this.restoreSlot = -1;
   }

   private void releaseResources() {
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private void rotateToward(LocalPlayer player, Vec3 target) {
      if (PveManagerFeature.INSTANCE.rotate.getValue()) {
         if (!this.rotationSaved) {
            this.savedYaw = player.getYRot();
            this.savedPitch = player.getXRot();
            this.rotationSaved = true;
         }

         Vec3 delta = target.subtract(player.getEyePosition());
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         player.setYRot((float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F);
         player.setXRot((float)(-Math.toDegrees(Math.atan2(delta.y, horizontal))));
      }
   }

   private void restoreRotation(LocalPlayer player) {
      if (this.rotationSaved) {
         if (player != null) {
            player.setYRot(this.savedYaw);
            player.setXRot(this.savedPitch);
         }

         this.rotationSaved = false;
      }
   }

   private void resetRuntime() {
      this.machine.reset(0L);
      this.actionCooldown.reset();
      this.upgradeSlot = -1;
      this.restoreSlot = -1;
      this.upgradeItem = null;
      this.supportBlock = null;
      this.rotationSaved = false;
      this.releaseResources();
      this.lastTick = 0L;
   }

   enum State {
      FIND_ITEM,
      SELECT_ITEM,
      PLACE,
      BREAK,
      COOLDOWN;
   }
}
