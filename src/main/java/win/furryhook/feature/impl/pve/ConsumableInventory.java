package win.furryhook.feature.impl.pve;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

final class ConsumableInventory {
   private ConsumableInventory() {
   }

   static List<ConsumableSelector.Candidate<ItemStack>> collect(LocalPlayer player, Function<ItemStack, ConsumableInventory.Profile> classifier) {
      List<ConsumableSelector.Candidate<ItemStack>> candidates = new ArrayList<>();
      int selectedHotbar = player.getInventory().getSelectedSlot();
      int selectedContainer = 36 + selectedHotbar;
      add(candidates, player.getOffhandItem(), ConsumableSelector.Location.OFF_HAND, 45, classifier);
      add(candidates, player.getMainHandItem(), ConsumableSelector.Location.MAIN_HAND, selectedContainer, classifier);

      for (int slot = 36; slot < 45; slot++) {
         if (slot != selectedContainer) {
            add(candidates, player.inventoryMenu.getSlot(slot).getItem(), ConsumableSelector.Location.HOTBAR, slot, classifier);
         }
      }

      for (int slot = 9; slot < 36; slot++) {
         add(candidates, player.inventoryMenu.getSlot(slot).getItem(), ConsumableSelector.Location.INVENTORY, slot, classifier);
      }

      return List.copyOf(candidates);
   }

   private static void add(
      List<ConsumableSelector.Candidate<ItemStack>> candidates,
      ItemStack stack,
      ConsumableSelector.Location location,
      int containerSlot,
      Function<ItemStack, ConsumableInventory.Profile> classifier
   ) {
      if (!stack.isEmpty()) {
         ConsumableInventory.Profile profile = classifier.apply(stack);
         if (profile != null) {
            candidates.add(
               new ConsumableSelector.Candidate<>(
                  stack.copy(), profile.kind(), location, containerSlot, profile.nutrition(), profile.saturation(), profile.safe()
               )
            );
         }
      }
   }

   record Profile(ConsumableSelector.Kind kind, int nutrition, float saturation, boolean safe) {
   }
}
