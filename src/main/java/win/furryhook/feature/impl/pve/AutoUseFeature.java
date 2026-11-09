package win.furryhook.feature.impl.pve;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.impl.misc.DonItems;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.utils.inventory.DropAllInventoryController;
import win.furryhook.utils.inventory.InventorySwap;

public final class AutoUseFeature extends PveFeature {
   public static final String AUTO_EAT = "Auto Eat";
   public static final String AUTO_INVISIBILITY = "Auto Invisibility";
   private static final long INVISIBILITY_RETRY_NANOS = 4000000000L;
   public final MultiSelectSetting features = this.register(new MultiSelectSetting("Features", Set.of("Auto Eat"), "Auto Eat", "Auto Invisibility"));
   public final BooleanSetting ignoreGoldenApples = this.register(
      new BooleanSetting("Ignore Golden Apples", false).visibleWhen(() -> this.features.isSelected("Auto Eat"))
   );
   public final BooleanSetting ignoreEnchantedGoldenApples = this.register(
      new BooleanSetting("Ignore Enchanted Golden Apples", false).visibleWhen(() -> this.features.isSelected("Auto Eat"))
   );
   private final ConsumableUseController useController = new ConsumableUseController();
   private AutoUseFeature.Action activeAction;
   private boolean rotationApplied;
   private long invisibilityRetryAt;

   public AutoUseFeature() {
      super("AutoUse", "Automatically eats food and maintains invisibility", FeatureCategory.PLAYER, -1, AutomationPriority.BACKGROUND);
   }

   public boolean isEating() {
      return this.activeAction == AutoUseFeature.Action.EATING && this.useController.isActive();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      if (this.useController.isActive()) {
         if (isWorldUsable(client, player) && !this.combatActive()) {
            this.keepThrowableRotation(player);
            if (!this.useController.tick(client, player)) {
               if (this.activeAction == AutoUseFeature.Action.INVISIBILITY) {
                  this.invisibilityRetryAt = System.nanoTime() + 4000000000L;
               }

               this.finishTransaction();
            }
         } else {
            this.cancelActive(client, player);
         }
      } else if (isWorldUsable(client, player)
         && !this.combatActive()
         && client.gui.screen() == null
         && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.getCarried().isEmpty()
         && !player.isUsingItem()
         && !client.options.keyUse.isDown()
         && !DropAllInventoryController.blocksInventoryOperations()
         && !InventorySwap.isBusy()) {
         if (!this.features.isSelected("Auto Eat") || !player.getFoodData().needsFood() || !this.tryAutoEat(client, player)) {
            if (this.features.isSelected("Auto Invisibility")) {
               this.tryAutoInvisibility(client, player);
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cancelActive(Minecraft.getInstance(), Minecraft.getInstance().player);
      this.invisibilityRetryAt = 0L;
   }

   @Override
   protected void onPveDisable() {
      this.cancelActive(Minecraft.getInstance(), Minecraft.getInstance().player);
      this.invisibilityRetryAt = 0L;
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelActive(Minecraft.getInstance(), Minecraft.getInstance().player);
   }

   private boolean tryAutoEat(Minecraft client, LocalPlayer player) {
      List<ConsumableSelector.Candidate<ItemStack>> candidates = ConsumableInventory.collect(player, AutoUseFeature::foodProfile)
         .stream()
         .filter(candidate -> !player.getCooldowns().isOnCooldown(candidate.value()))
         .toList();
      Optional<ConsumableSelector.Candidate<ItemStack>> selected = ConsumableSelector.selectFood(
         candidates, this.ignoreGoldenApples.getValue(), this.ignoreEnchantedGoldenApples.getValue()
      );
      return selected.isPresent() && this.startUse(client, player, selected.get(), AutoUseFeature.Action.EATING);
   }

   private boolean tryAutoInvisibility(Minecraft client, LocalPlayer player) {
      long now = System.nanoTime();
      if (now >= this.invisibilityRetryAt
         && player.onGround()
         && player.tickCount > 100
         && !player.hasEffect(MobEffects.INVISIBILITY)
         && !player.hasEffect(MobEffects.GLOWING)) {
         Optional<ConsumableSelector.Candidate<ItemStack>> selected = ConsumableInventory.collect(player, AutoUseFeature::invisibilityProfile)
            .stream()
            .filter(candidate -> !player.getCooldowns().isOnCooldown(candidate.value()))
            .min(
               Comparator.<ConsumableSelector.Candidate<ItemStack>>comparingInt(candidate -> locationRank(candidate.location()))
                  .thenComparingInt(ConsumableSelector.Candidate::containerSlot)
            );
         if (selected.isEmpty()) {
            return false;
         }

         boolean started = this.startUse(client, player, selected.get(), AutoUseFeature.Action.INVISIBILITY);
         if (started) {
            this.invisibilityRetryAt = now + 4000000000L;
         }

         return started;
      } else {
         return false;
      }
   }

   private boolean startUse(Minecraft client, LocalPlayer player, ConsumableSelector.Candidate<ItemStack> selected, AutoUseFeature.Action action) {
      boolean throwable = action == AutoUseFeature.Action.INVISIBILITY && isThrowablePotion(selected.value());
      boolean rotateThrowable = throwable && PveManagerFeature.INSTANCE.rotate.getValue();
      if (rotateThrowable && RotationContext.isActive()) {
         return false;
      }

      boolean swappedUse = selected.location() != ConsumableSelector.Location.OFF_HAND;
      boolean claimed;
      if (rotateThrowable && swappedUse) {
         claimed = this.claim(AutomationResource.INVENTORY, AutomationResource.ROTATION, AutomationResource.MOVEMENT, AutomationResource.SCREEN);
      } else if (rotateThrowable) {
         claimed = this.claim(AutomationResource.INVENTORY, AutomationResource.ROTATION);
      } else if (swappedUse) {
         claimed = this.claim(AutomationResource.INVENTORY, AutomationResource.MOVEMENT, AutomationResource.SCREEN);
      } else {
         claimed = this.claim(AutomationResource.INVENTORY);
      }

      if (!claimed) {
         return false;
      }

      if (rotateThrowable) {
         RotationContext.setRotation(player.getYRot(), 90.0F);
         this.rotationApplied = true;
      }

      boolean heldUse = action == AutoUseFeature.Action.EATING || selected.value().is(Items.POTION);
      int directDelay = throwable && selected.location() == ConsumableSelector.Location.OFF_HAND ? 1 : 0;
      if (!this.useController.start(client, player, selected, heldUse, directDelay)) {
         this.finishTransaction();
         return false;
      } else {
         this.activeAction = action;
         return true;
      }
   }

   private void keepThrowableRotation(LocalPlayer player) {
      if (this.rotationApplied && player != null) {
         RotationContext.setRotation(player.getYRot(), 90.0F);
      }
   }

   private void cancelActive(Minecraft client, LocalPlayer player) {
      this.useController.cancel(client, player);
      this.finishTransaction();
   }

   private void finishTransaction() {
      if (this.rotationApplied) {
         if (this.owns(AutomationResource.ROTATION) || !PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.ROTATION)) {
            RotationContext.clear();
         }

         this.rotationApplied = false;
      }

      this.activeAction = null;
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private boolean combatActive() {
      AuraFeature aura = AuraFeature.getMarkerFeature();
      return aura != null && aura.getCurrentTarget() != null
         ? true
         : PveAutomationCoordinator.INSTANCE.isClaimedByOther(this, AutomationResource.COMBAT)
            || PveAutomationCoordinator.INSTANCE.isClaimedByOther(this, AutomationResource.ROTATION);
   }

   private static boolean isWorldUsable(Minecraft client, LocalPlayer player) {
      return player != null && client.level != null && client.gameMode != null && player.isAlive() && !player.isSpectator();
   }

   private static ConsumableInventory.Profile foodProfile(ItemStack stack) {
      FoodProperties food = (FoodProperties)stack.get(DataComponents.FOOD);
      if (food != null && !stack.is(Items.DRIED_KELP)) {
         ConsumableSelector.Kind kind;
         if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            kind = ConsumableSelector.Kind.ENCHANTED_GOLDEN_APPLE;
         } else if (stack.is(Items.GOLDEN_APPLE)) {
            kind = ConsumableSelector.Kind.GOLDEN_APPLE;
         } else {
            kind = ConsumableSelector.Kind.FOOD;
         }

         return new ConsumableInventory.Profile(kind, food.nutrition(), food.saturation(), hasNoHarmfulFoodEffect(stack));
      } else {
         return null;
      }
   }

   private static ConsumableInventory.Profile invisibilityProfile(ItemStack stack) {
      if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) {
         return null;
      }

      boolean cataloguedInvisibility = DonItems.FunTime.ENHANCED_INVISIBILITY_POTION.matches(stack);
      PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
      int effectCount = 0;
      boolean invisibility = false;
      if (contents != null) {
         for (MobEffectInstance effect : contents.getAllEffects()) {
            effectCount++;
            invisibility |= effect.is(MobEffects.INVISIBILITY);
         }
      }

      return cataloguedInvisibility || invisibility && effectCount == 1
         ? new ConsumableInventory.Profile(ConsumableSelector.Kind.INVISIBILITY_POTION, 0, 0.0F, true)
         : null;
   }

   private static boolean hasNoHarmfulFoodEffect(ItemStack stack) {
      Consumable consumable = (Consumable)stack.get(DataComponents.CONSUMABLE);
      if (consumable == null) {
         return true;
      }

      for (ConsumeEffect effect : consumable.onConsumeEffects()) {
         if (effect instanceof ApplyStatusEffectsConsumeEffect statusEffects) {
            for (MobEffectInstance statusEffect : statusEffects.effects()) {
               if (!((MobEffect)statusEffect.getEffect().value()).isBeneficial()) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private static boolean isThrowablePotion(ItemStack stack) {
      return stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
   }

   private static int locationRank(ConsumableSelector.Location location) {
      return switch (location) {
         case OFF_HAND -> 0;
         case MAIN_HAND -> 1;
         case HOTBAR -> 2;
         case INVENTORY -> 3;
      };
   }

   private enum Action {
      EATING,
      INVISIBILITY;
   }
}
