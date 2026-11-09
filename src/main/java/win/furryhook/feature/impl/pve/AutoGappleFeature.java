package win.furryhook.feature.impl.pve;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.utils.inventory.DropAllInventoryController;
import win.furryhook.utils.inventory.InventorySwap;

public final class AutoGappleFeature extends PveFeature {
   private static final int RETRY_GUARD_TICKS = 10;
   public final NumberSetting health = this.register(new NumberSetting("Health", 15.0, 4.0, 20.0, 0.05, " HP"));
   public final BooleanSetting goldenApples = this.register(new BooleanSetting("Golden Apples", true));
   public final BooleanSetting enchantedGoldenApples = this.register(new BooleanSetting("Enchanted Golden Apples", true));
   private final ConsumableUseController useController = new ConsumableUseController();
   private int retryAfterTick;

   public AutoGappleFeature() {
      super("AutoGapple", "Eats the strongest allowed golden apple at low health", FeatureCategory.PLAYER, -1, AutomationPriority.EMERGENCY);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      if (this.useController.isActive()) {
         if (!isWorldUsable(client, player)) {
            this.cancelActive(client, player);
         } else if (!this.useController.tick(client, player)) {
            this.finishTransaction();
            this.retryAfterTick = player.tickCount + 10;
         }
      } else if (isWorldUsable(client, player)
         && player.tickCount >= this.retryAfterTick
         && client.gui.screen() == null
         && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.getCarried().isEmpty()
         && !player.isUsingItem()
         && !client.options.keyUse.isDown()
         && !DropAllInventoryController.blocksInventoryOperations()
         && !InventorySwap.isBusy()
         && !(effectiveHealth(player) > this.health.getValue())) {
         List<ConsumableSelector.Candidate<ItemStack>> candidates = ConsumableInventory.collect(player, AutoGappleFeature::appleProfile)
            .stream()
            .filter(candidate -> !player.getCooldowns().isOnCooldown(candidate.value()))
            .toList();
         Optional<ConsumableSelector.Candidate<ItemStack>> selected = ConsumableSelector.selectApple(
            candidates, this.goldenApples.getValue(), this.enchantedGoldenApples.getValue()
         );
         selected.ifPresent(candidate -> this.startUse(client, player, (ConsumableSelector.Candidate<ItemStack>)candidate));
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cancelActive(Minecraft.getInstance(), Minecraft.getInstance().player);
      this.retryAfterTick = 0;
   }

   @Override
   protected void onPveDisable() {
      this.cancelActive(Minecraft.getInstance(), Minecraft.getInstance().player);
      this.retryAfterTick = 0;
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelActive(Minecraft.getInstance(), Minecraft.getInstance().player);
   }

   private void startUse(Minecraft client, LocalPlayer player, ConsumableSelector.Candidate<ItemStack> selected) {
      boolean claimed = selected.location() == ConsumableSelector.Location.OFF_HAND
         ? this.claim(AutomationResource.INVENTORY)
         : this.claim(AutomationResource.INVENTORY, AutomationResource.MOVEMENT, AutomationResource.SCREEN);
      if (claimed) {
         if (!this.useController.start(client, player, selected, true, 0)) {
            this.finishTransaction();
         }
      }
   }

   private void cancelActive(Minecraft client, LocalPlayer player) {
      this.useController.cancel(client, player);
      this.finishTransaction();
   }

   private void finishTransaction() {
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private static ConsumableInventory.Profile appleProfile(ItemStack stack) {
      if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
         return new ConsumableInventory.Profile(ConsumableSelector.Kind.ENCHANTED_GOLDEN_APPLE, 0, 0.0F, true);
      } else {
         return stack.is(Items.GOLDEN_APPLE) ? new ConsumableInventory.Profile(ConsumableSelector.Kind.GOLDEN_APPLE, 0, 0.0F, true) : null;
      }
   }

   private static double effectiveHealth(LocalPlayer player) {
      return player.getHealth() + player.getAbsorptionAmount();
   }

   private static boolean isWorldUsable(Minecraft client, LocalPlayer player) {
      return player != null && client.level != null && client.gameMode != null && player.isAlive() && !player.isSpectator();
   }
}
