package win.furryhook.feature.impl.pve;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.utils.inventory.DropAllInventoryController;
import win.furryhook.utils.inventory.InventorySwap;

public final class AutoArmorFeature extends PveFeature implements MinecraftContext {
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   public final NumberSetting swapDelay = this.register(new NumberSetting("Swap Delay", 100.0, 0.0, 1000.0, 25.0, "ms"));
   private long lastSwapNanos;

   public AutoArmorFeature() {
      super("AutoArmor", "Equips the strongest armor in your inventory", FeatureCategory.PLAYER, -1, AutomationPriority.FEATURE);
   }

   @Override
   protected void onPveEnable() {
      this.lastSwapNanos = 0L;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      if (this.canManageInventory(player) && !InventorySwap.isBusy() && this.delayElapsed(System.nanoTime())) {
         List<AutoArmorFeature.Upgrade> upgrades = this.findUpgrades(player);
         if (!upgrades.isEmpty() && this.claim(AutomationResource.INVENTORY)) {
            boolean swapped = false;

            try {
               if (this.canManageInventory(player) && !InventorySwap.isBusy()) {
                  for (AutoArmorFeature.Upgrade upgrade : upgrades) {
                     swapped |= this.equipUpgrade(player, upgrade);
                  }
               }
            } finally {
               PveAutomationCoordinator.INSTANCE.release(this);
            }

            if (swapped) {
               this.lastSwapNanos = System.nanoTime();
            }
         }
      }
   }

   private boolean canManageInventory(LocalPlayer player) {
      return player != null
         && player.isAlive()
         && mc.gameMode != null
         && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.getCarried().isEmpty()
         && !DropAllInventoryController.blocksInventoryOperations()
         && (mc.gui.screen() == null || mc.gui.screen() instanceof InventoryScreen);
   }

   private boolean delayElapsed(long nowNanos) {
      long delayNanos = (long)(this.swapDelay.getValue() * 1000000.0);
      return this.lastSwapNanos == 0L || nowNanos - this.lastSwapNanos >= delayNanos;
   }

   private List<AutoArmorFeature.Upgrade> findUpgrades(LocalPlayer player) {
      List<AutoArmorFeature.Upgrade> upgrades = new ArrayList<>(ARMOR_SLOTS.length);

      for (EquipmentSlot equipmentSlot : ARMOR_SLOTS) {
         int armorSlot = armorMenuSlot(equipmentSlot);
         ItemStack equipped = player.inventoryMenu.getSlot(armorSlot).getItem();
         if (!hasBindingCurse(equipped)) {
            double equippedScore = equipped.isEmpty() ? Double.NEGATIVE_INFINITY : armorScore(equipped);
            List<AutoArmorFeature.ScoredSlot> candidates = new ArrayList<>();

            for (int slot = 9; slot < 45; slot++) {
               ItemStack candidate = player.inventoryMenu.getSlot(slot).getItem();
               if (isArmorFor(candidate, equipmentSlot) && !hasBindingCurse(candidate)) {
                  candidates.add(new AutoArmorFeature.ScoredSlot(slot, armorScore(candidate)));
               }
            }

            OptionalInt best = selectBestUpgrade(equippedScore, candidates);
            if (best.isPresent()) {
               upgrades.add(new AutoArmorFeature.Upgrade(best.getAsInt(), armorSlot, equipmentSlot));
            }
         }
      }

      return upgrades;
   }

   private boolean equipUpgrade(LocalPlayer player, AutoArmorFeature.Upgrade upgrade) {
      if (player.inventoryMenu.isValidSlotIndex(upgrade.sourceSlot())
         && player.inventoryMenu.isValidSlotIndex(upgrade.armorSlot())
         && player.inventoryMenu.getCarried().isEmpty()) {
         ItemStack candidate = player.inventoryMenu.getSlot(upgrade.sourceSlot()).getItem();
         ItemStack equipped = player.inventoryMenu.getSlot(upgrade.armorSlot()).getItem();
         if (isArmorFor(candidate, upgrade.equipmentSlot())
            && !hasBindingCurse(candidate)
            && !hasBindingCurse(equipped)
            && (equipped.isEmpty() || !(armorScore(candidate) <= armorScore(equipped)))) {
            this.click(player, upgrade.sourceSlot());
            if (player.inventoryMenu.getCarried().isEmpty()) {
               return false;
            }

            this.click(player, upgrade.armorSlot());
            if (!player.inventoryMenu.getCarried().isEmpty()) {
               this.click(player, upgrade.sourceSlot());
            }

            return player.inventoryMenu.getCarried().isEmpty();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void click(LocalPlayer player, int slot) {
      mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, player);
   }

   private static int armorMenuSlot(EquipmentSlot equipmentSlot) {
      return switch (equipmentSlot) {
         case HEAD -> 5;
         case CHEST -> 6;
         case LEGS -> 7;
         case FEET -> 8;
         default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + equipmentSlot);
      };
   }

   private static boolean isArmorFor(ItemStack stack, EquipmentSlot equipmentSlot) {
      if (stack.isEmpty()) {
         return false;
      }

      Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
      return equippable != null && equippable.slot() == equipmentSlot;
   }

   private static boolean hasBindingCurse(ItemStack stack) {
      return enchantmentLevel(stack, "binding_curse") > 0;
   }

   private static double armorScore(ItemStack stack) {
      double armor = 0.0;
      double toughness = 0.0;
      ItemAttributeModifiers modifiers = (ItemAttributeModifiers)stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
      if (modifiers != null) {
         for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(Attributes.ARMOR)) {
               armor += entry.modifier().amount();
            } else if (entry.attribute().equals(Attributes.ARMOR_TOUGHNESS)) {
               toughness += entry.modifier().amount();
            }
         }
      }

      return score(armor, toughness, enchantmentLevel(stack, "protection"), enchantmentLevel(stack, "unbreaking"), enchantmentLevel(stack, "mending"));
   }

   private static int enchantmentLevel(ItemStack stack, String path) {
      if (stack.isEmpty()) {
         return 0;
      }

      for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
         Optional<ResourceKey<Enchantment>> key = ((Holder)entry.getKey()).unwrapKey();
         if (key.isPresent() && key.get().identifier().getPath().equals(path)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   static double score(double armor, double toughness, int protection, int unbreaking, int mending) {
      return armor + toughness + protection + unbreaking * 0.1 + mending * 0.2;
   }

   static OptionalInt selectBestUpgrade(double equippedScore, List<AutoArmorFeature.ScoredSlot> candidates) {
      int bestSlot = -1;
      double bestScore = equippedScore;

      for (AutoArmorFeature.ScoredSlot candidate : candidates) {
         if (candidate.score() > bestScore) {
            bestScore = candidate.score();
            bestSlot = candidate.slot();
         }
      }

      return bestSlot < 0 ? OptionalInt.empty() : OptionalInt.of(bestSlot);
   }

   record ScoredSlot(int slot, double score) {
   }

   private record Upgrade(int sourceSlot, int armorSlot, EquipmentSlot equipmentSlot) {
   }
}
