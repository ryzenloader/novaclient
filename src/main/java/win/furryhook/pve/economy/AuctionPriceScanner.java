package win.furryhook.pve.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.OptionalLong;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import win.furryhook.utils.inventory.ContainerLootService;

public final class AuctionPriceScanner {
   private AuctionPriceScanner() {
   }

   public static OptionalLong competitivePrice(AbstractContainerMenu menu, Item target, String query, int saleCount) {
      if (menu != null && target != null && saleCount > 0) {
         ArrayList<Long> unitPrices = new ArrayList<>();
         int containerSlots = ContainerLootService.containerSlotCount(menu);

         for (int slotId = 0; slotId < containerSlots; slotId++) {
            if (menu.isValidSlotIndex(slotId)) {
               ItemStack stack = menu.getSlot(slotId).getItem();
               if (!stack.isEmpty() && (stack.is(target) || EconomyItemText.containsAny(stack, query))) {
                  EconomyItemText.listingUnitPrice(stack).ifPresent(unitPrices::add);
               }
            }
         }

         if (unitPrices.isEmpty()) {
            return OptionalLong.empty();
         }

         Collections.sort(unitPrices);
         long unitMedian = unitPrices.get(unitPrices.size() / 2);

         long total;
         try {
            total = Math.multiplyExact(unitMedian, saleCount);
         } catch (ArithmeticException ignored) {
            total = 2147483647L;
         }

         long competitive = Math.max(1L, Math.min(2147483647L, total - 1L));
         return OptionalLong.of(competitive);
      } else {
         return OptionalLong.empty();
      }
   }
}
