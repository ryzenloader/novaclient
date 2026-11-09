package win.furryhook.pve.economy;

import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import win.furryhook.utils.inventory.InventoryUtil;

public final class EconomyInventory {
   private EconomyInventory() {
   }

   public static int count(LocalPlayer player, Item item) {
      if (player != null && item != null) {
         int count = 0;

         for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
               count += stack.getCount();
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   public static int findHotbar(LocalPlayer player, Predicate<ItemStack> predicate) {
      if (player == null) {
         return -1;
      }

      for (int slot = 0; slot < 9; slot++) {
         if (predicate.test(player.getInventory().getItem(slot))) {
            return slot;
         }
      }

      return -1;
   }

   public static boolean selectHotbar(LocalPlayer player, int slot) {
      if (player != null && slot >= 0 && slot <= 8) {
         if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            player.connection.send(new ServerboundSetCarriedItemPacket(slot));
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean moveFirstToSelectedHotbar(LocalPlayer player, Predicate<ItemStack> predicate) {
      if (player != null && player.containerMenu == player.inventoryMenu) {
         int menuSlot = InventoryUtil.findPlayerMenuSlot(player, predicate);
         if (menuSlot < 0) {
            return false;
         }

         int selected = player.getInventory().getSelectedSlot();
         return menuSlot >= 36 && menuSlot <= 44 ? selectHotbar(player, menuSlot - 36) : InventoryUtil.swapWithHotbar(menuSlot, selected);
      } else {
         return false;
      }
   }

   public static boolean hasFreeSlot(LocalPlayer player) {
      if (player == null) {
         return false;
      }

      for (int slot = 0; slot < 36; slot++) {
         if (player.getInventory().getItem(slot).isEmpty()) {
            return true;
         }
      }

      return false;
   }
}
