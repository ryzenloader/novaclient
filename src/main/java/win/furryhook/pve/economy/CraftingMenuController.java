package win.furryhook.pve.economy;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CraftingMenuController {
   private static final int RESULT_SLOT = 0;
   private static final int GRID_FIRST = 1;
   private static final int GRID_LAST = 9;
   private static final int PLAYER_FIRST = 10;
   private int sourceSlot = -1;
   private int actionCount;
   private int resultWaitTicks;

   public CraftingMenuController.Result tick(Minecraft client, CraftingMenu menu, CraftingMenuController.Recipe recipe) {
      if (EconomyMenus.isCurrent(client, menu) && menu.slots.size() > 9 && this.actionCount++ <= 120) {
         int invalidGrid = invalidGridSlot(menu, recipe);
         if (invalidGrid < 0) {
            int missingGrid = missingGridSlot(menu, recipe);
            if (missingGrid >= 0) {
               Item expected = recipe.ingredient(missingGrid - 1);
               ItemStack carried = menu.getCarried();
               if (!carried.isEmpty() && !carried.is(expected)) {
                  return this.returnCarried(client, menu) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
               }

               if (carried.isEmpty()) {
                  int ingredient = findIngredient(menu, expected);
                  if (ingredient < 0) {
                     return CraftingMenuController.Result.FAILED;
                  }

                  this.sourceSlot = ingredient;
                  return EconomyMenus.click(client, menu, ingredient, 0, ContainerInput.PICKUP)
                     ? CraftingMenuController.Result.IN_PROGRESS
                     : CraftingMenuController.Result.FAILED;
               } else {
                  return EconomyMenus.click(client, menu, missingGrid, 1, ContainerInput.PICKUP)
                     ? CraftingMenuController.Result.IN_PROGRESS
                     : CraftingMenuController.Result.FAILED;
               }
            } else if (!menu.getCarried().isEmpty()) {
               return this.returnCarried(client, menu) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
            } else {
               ItemStack result = menu.getSlot(0).getItem();
               if (result.is(recipe.output())) {
                  this.resultWaitTicks = 0;
                  return EconomyMenus.quickMove(client, menu, 0) ? CraftingMenuController.Result.CRAFTED : CraftingMenuController.Result.FAILED;
               } else {
                  return ++this.resultWaitTicks <= 20 ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
               }
            }
         } else if (!menu.getCarried().isEmpty()) {
            return this.returnCarried(client, menu) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
         } else {
            return EconomyMenus.quickMove(client, menu, invalidGrid) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
         }
      } else {
         return CraftingMenuController.Result.FAILED;
      }
   }

   public boolean cleanup(Minecraft client, CraftingMenu menu) {
      return menu != null && !menu.getCarried().isEmpty() ? this.returnCarried(client, menu) : true;
   }

   public void reset() {
      this.sourceSlot = -1;
      this.actionCount = 0;
      this.resultWaitTicks = 0;
   }

   private boolean returnCarried(Minecraft client, CraftingMenu menu) {
      ItemStack carried = menu.getCarried();
      if (carried.isEmpty()) {
         this.sourceSlot = -1;
         return true;
      }

      if (canAccept(menu, this.sourceSlot, carried)) {
         boolean clicked = EconomyMenus.click(client, menu, this.sourceSlot, 0, ContainerInput.PICKUP);
         if (clicked) {
            this.sourceSlot = -1;
         }

         return clicked;
      } else {
         for (int slotId = 10; slotId < menu.slots.size(); slotId++) {
            if (canAccept(menu, slotId, carried)) {
               boolean clicked = EconomyMenus.click(client, menu, slotId, 0, ContainerInput.PICKUP);
               if (clicked) {
                  this.sourceSlot = -1;
               }

               return clicked;
            }
         }

         return false;
      }
   }

   private static boolean canAccept(CraftingMenu menu, int slotId, ItemStack carried) {
      if (menu.isValidSlotIndex(slotId) && slotId >= 10) {
         Slot slot = menu.getSlot(slotId);
         ItemStack existing = slot.getItem();
         return slot.mayPlace(carried)
            && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, carried) && existing.getCount() < existing.getMaxStackSize());
      } else {
         return false;
      }
   }

   private static int invalidGridSlot(CraftingMenu menu, CraftingMenuController.Recipe recipe) {
      for (int slotId = 1; slotId <= 9; slotId++) {
         ItemStack stack = menu.getSlot(slotId).getItem();
         Item expected = recipe.ingredient(slotId - 1);
         if (!stack.isEmpty() && (!stack.is(expected) || stack.getCount() != 1)) {
            return slotId;
         }
      }

      return -1;
   }

   private static int missingGridSlot(CraftingMenu menu, CraftingMenuController.Recipe recipe) {
      for (int slotId = 1; slotId <= 9; slotId++) {
         if (!menu.getSlot(slotId).getItem().is(recipe.ingredient(slotId - 1))) {
            return slotId;
         }
      }

      return -1;
   }

   private static int findIngredient(CraftingMenu menu, Item item) {
      for (int slotId = 10; slotId < menu.slots.size(); slotId++) {
         if (menu.getSlot(slotId).getItem().is(item)) {
            return slotId;
         }
      }

      return -1;
   }

   public enum Recipe {
      ENCHANTED_GOLDEN_APPLE(
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.APPLE,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK
      ),
      GOLD_BLOCK(
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT
      );

      private final Item[] ingredients;

      Recipe(Item... ingredients) {
         this.ingredients = ingredients;
      }

      Item ingredient(int index) {
         return this.ingredients[index];
      }

      Item output() {
         return this == ENCHANTED_GOLDEN_APPLE ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLD_BLOCK;
      }
   }

   public enum Result {
      IN_PROGRESS,
      CRAFTED,
      FAILED;
   }
}
