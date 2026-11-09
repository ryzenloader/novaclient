package win.furryhook.feature.impl.pve;

import java.util.Arrays;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import win.furryhook.context.MinecraftContext;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.PlayerInputEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.PvpStateTracker;
import win.furryhook.utils.inventory.DropAllInventoryController;
import win.furryhook.utils.inventory.InventorySwap;
import win.furryhook.utils.inventory.InventoryUtil;

public final class AutoPotionFeature extends PveFeature implements MinecraftContext {
   private static final String FIRE_RESISTANCE = "Fire Resistance";
   private static final String STRENGTH = "Strength";
   private static final String SPEED = "Speed";
   private static final long THROW_THROTTLE_NANOS = 600000000L;
   private static final int MIN_WORLD_AGE_TICKS = 100;
   private static final int INVENTORY_SWAP_RETURN_TICKS = 4;
   private static final double GROUND_PROBE_DEPTH = 0.5;
   public final MultiSelectSetting potions = this.register(new MultiSelectSetting("Potions", Set.of(), "Fire Resistance", "Strength", "Speed"));
   public final BooleanSetting onlyPvp = this.register(new BooleanSetting("Only PvP", false));
   private boolean throwing;
   private int operationTicks;
   private int potionContainerSlot = -1;
   private int originalHotbarSlot = -1;
   private ItemStack originalHotbarStack = ItemStack.EMPTY;
   private float throwYaw;
   private boolean rotationApplied;
   private long lastThrowNanos;

   public AutoPotionFeature() {
      super("AutoPotion", "Throws selected splash potions when their effects are missing", FeatureCategory.PLAYER, -1, AutomationPriority.FEATURE);
   }

   @Override
   protected void onPveEnable() {
      this.resetOperationState();
      this.lastThrowNanos = 0L;
   }

   @Override
   protected void onPveDisable() {
      this.cancelOperation();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelOperation();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      if (this.throwing) {
         this.tickOperation(player);
      } else {
         ClientLevel level = event.getClient().level;
         long nowNanos = System.nanoTime();
         if (this.canStart(player, level) && !InventorySwap.isBusy() && this.throttleElapsed(nowNanos) && (!this.onlyPvp.getValue() || hasActivePlayerTarget())
            )
          {
            AutoPotionFeature.PotionChoice choice = this.findPotion(player);
            if (choice != null && this.claimOperationResources()) {
               this.beginThrow(player, choice, nowNanos);
            }
         }
      }
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      if (this.throwing) {
         event.clearMovement(false, true);
      }
   }

   private boolean canStart(LocalPlayer player, ClientLevel level) {
      return player != null
         && level != null
         && player.isAlive()
         && player.tickCount > 100
         && !player.isUsingItem()
         && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.getCarried().isEmpty()
         && mc.gameMode != null
         && !DropAllInventoryController.blocksInventoryOperations()
         && isOnOrNearGround(player, level);
   }

   private boolean throttleElapsed(long nowNanos) {
      return this.lastThrowNanos == 0L || nowNanos - this.lastThrowNanos >= 600000000L;
   }

   private AutoPotionFeature.PotionChoice findPotion(LocalPlayer player) {
      AutoPotionFeature.PotionKind[] kinds = AutoPotionFeature.PotionKind.values();
      boolean[] selected = new boolean[kinds.length];
      boolean[] active = new boolean[kinds.length];
      int[] slots = new int[kinds.length];
      Arrays.fill(slots, -1);

      for (int index = 0; index < kinds.length; index++) {
         AutoPotionFeature.PotionKind kind = kinds[index];
         selected[index] = this.potions.isSelected(kind.settingName());
         active[index] = player.hasEffect(kind.effect());
         if (selected[index] && !active[index]) {
            slots[index] = InventoryUtil.findPlayerMenuSlot(player, stack -> containsEffect(stack, kind.effect()));
         }
      }

      int selectedIndex = selectPotionIndex(selected, active, slots);
      return selectedIndex < 0 ? null : new AutoPotionFeature.PotionChoice(kinds[selectedIndex], slots[selectedIndex]);
   }

   private void beginThrow(LocalPlayer player, AutoPotionFeature.PotionChoice choice, long nowNanos) {
      this.potionContainerSlot = choice.containerSlot();
      this.originalHotbarSlot = player.getInventory().getSelectedSlot();
      this.originalHotbarStack = player.getInventory().getItem(this.originalHotbarSlot).copy();
      this.throwYaw = player.getYRot();
      this.operationTicks = 0;
      if (PveManagerFeature.INSTANCE.rotate.getValue()) {
         RotationContext.setRotation(this.throwYaw, 90.0F);
         this.rotationApplied = true;
      }

      InventorySwap.useFromSlot(this.potionContainerSlot);
      if (!InventorySwap.isBusy()) {
         this.clearAppliedRotation();
         PveAutomationCoordinator.INSTANCE.release(this);
         this.resetOperationState();
      } else {
         this.throwing = true;
         this.lastThrowNanos = nowNanos;
      }
   }

   private void tickOperation(LocalPlayer player) {
      if (player == null) {
         this.cancelOperation();
      } else {
         if (this.rotationApplied) {
            RotationContext.setRotation(this.throwYaw, 90.0F);
         }

         this.operationTicks++;
         if (!InventorySwap.isBusy() || this.operationTicks >= 4) {
            this.finishOperation(player);
         }
      }
   }

   private void finishOperation(LocalPlayer player) {
      InventorySwap.abort();
      this.restoreOriginalSlot(player);
      this.clearAppliedRotation();
      PveAutomationCoordinator.INSTANCE.release(this);
      this.resetOperationState();
   }

   private void cancelOperation() {
      if (this.throwing) {
         LocalPlayer player = this.player();
         InventorySwap.abort();
         if (player != null) {
            this.restoreOriginalSlot(player);
         }

         this.clearAppliedRotation();
         PveAutomationCoordinator.INSTANCE.release(this);
         this.resetOperationState();
      }
   }

   private void restoreOriginalSlot(LocalPlayer player) {
      if (this.originalHotbarSlot >= 0
         && this.originalHotbarSlot <= 8
         && this.potionContainerSlot >= 0
         && player.containerMenu == player.inventoryMenu
         && mc.gameMode != null) {
         int selectedContainerSlot = 36 + this.originalHotbarSlot;
         if (this.potionContainerSlot != selectedContainerSlot && player.inventoryMenu.isValidSlotIndex(this.potionContainerSlot)) {
            ItemStack source = player.inventoryMenu.getSlot(this.potionContainerSlot).getItem();
            ItemStack selected = player.inventoryMenu.getSlot(selectedContainerSlot).getItem();
            if (ItemStack.matches(source, this.originalHotbarStack) && !ItemStack.matches(selected, this.originalHotbarStack)) {
               mc.gameMode
                  .handleContainerInput(player.inventoryMenu.containerId, this.potionContainerSlot, this.originalHotbarSlot, ContainerInput.SWAP, player);
            }
         }

         if (player.getInventory().getSelectedSlot() != this.originalHotbarSlot) {
            player.getInventory().setSelectedSlot(this.originalHotbarSlot);
         }
      }
   }

   private boolean claimOperationResources() {
      return PveManagerFeature.INSTANCE.rotate.getValue()
         ? this.claim(AutomationResource.INVENTORY, AutomationResource.ROTATION, AutomationResource.MOVEMENT, AutomationResource.SCREEN)
         : this.claim(AutomationResource.INVENTORY, AutomationResource.MOVEMENT, AutomationResource.SCREEN);
   }

   private void clearAppliedRotation() {
      if (this.rotationApplied) {
         if (this.owns(AutomationResource.ROTATION) || !PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.ROTATION)) {
            RotationContext.clear();
         }

         this.rotationApplied = false;
      }
   }

   private void resetOperationState() {
      this.throwing = false;
      this.operationTicks = 0;
      this.potionContainerSlot = -1;
      this.originalHotbarSlot = -1;
      this.originalHotbarStack = ItemStack.EMPTY;
      this.throwYaw = 0.0F;
      this.rotationApplied = false;
   }

   private static boolean isOnOrNearGround(LocalPlayer player, ClientLevel level) {
      return player.onGround() ? true : level.getBlockCollisions(player, player.getBoundingBox().expandTowards(0.0, -0.5, 0.0)).iterator().hasNext();
   }

   private static boolean containsEffect(ItemStack stack, Holder<MobEffect> expected) {
      if (!stack.is(Items.SPLASH_POTION)) {
         return false;
      }

      PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
      if (contents == null) {
         return false;
      }

      for (MobEffectInstance effect : contents.getAllEffects()) {
         if (effect.getEffect().equals(expected)) {
            return true;
         }
      }

      return false;
   }

   private static boolean hasActivePlayerTarget() {
      if (PvpStateTracker.INSTANCE.isActive()) {
         return true;
      }

      AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
      return aura != null && aura.getCurrentTarget() instanceof Player target && target.isAlive();
   }

   static int selectPotionIndex(boolean[] selected, boolean[] active, int[] slots) {
      if (selected.length == active.length && selected.length == slots.length) {
         for (int index = 0; index < selected.length; index++) {
            if (selected[index] && !active[index] && slots[index] >= 0) {
               return index;
            }
         }

         return -1;
      } else {
         throw new IllegalArgumentException("Potion state arrays must have equal lengths");
      }
   }

   private record PotionChoice(AutoPotionFeature.PotionKind kind, int containerSlot) {
   }

   private enum PotionKind {
      FIRE_RESISTANCE("Fire Resistance", MobEffects.FIRE_RESISTANCE),
      STRENGTH("Strength", MobEffects.STRENGTH),
      SPEED("Speed", MobEffects.SPEED);

      private final String settingName;
      private final Holder<MobEffect> effect;

      PotionKind(String settingName, Holder<MobEffect> effect) {
         this.settingName = settingName;
         this.effect = effect;
      }

      String settingName() {
         return this.settingName;
      }

      Holder<MobEffect> effect() {
         return this.effect;
      }
   }
}
