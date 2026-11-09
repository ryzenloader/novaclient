package win.furryhook.feature.impl.pve;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseRemainder;
import win.furryhook.utils.inventory.DropAllInventoryController;
import win.furryhook.utils.inventory.InventorySwap;

final class ConsumableUseController {
   private static final int MAX_USE_TICKS = 200;
   private ConsumableUseController.Mode mode = ConsumableUseController.Mode.IDLE;
   private InteractionHand directHand = InteractionHand.MAIN_HAND;
   private ItemStack expectedStack = ItemStack.EMPTY;
   private ItemStack expectedRemainder = ItemStack.EMPTY;
   private boolean waitForCompletion;
   private boolean useIssued;
   private boolean sawExpectedUse;
   private boolean syntheticUseHeld;
   private int directDelayTicks;
   private int activeTicks;
   private int sourceSlot = -1;
   private int destinationSlot = -1;
   private int destinationHotbarSlot = -1;
   private ItemStack sourceBefore = ItemStack.EMPTY;
   private ItemStack destinationBefore = ItemStack.EMPTY;

   boolean start(Minecraft client, LocalPlayer player, ConsumableSelector.Candidate<ItemStack> candidate, boolean waitForCompletion, int directDelayTicks) {
      if (!this.isActive()
         && client.gameMode != null
         && player != null
         && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.getCarried().isEmpty()
         && !DropAllInventoryController.blocksInventoryOperations()
         && !InventorySwap.isBusy()) {
         this.expectedStack = candidate.value().copy();
         UseRemainder useRemainder = (UseRemainder)this.expectedStack.get(DataComponents.USE_REMAINDER);
         this.expectedRemainder = useRemainder == null ? ItemStack.EMPTY : useRemainder.convertInto().create();
         this.waitForCompletion = waitForCompletion;
         this.directDelayTicks = Math.max(0, directDelayTicks);
         this.activeTicks = 0;
         this.useIssued = false;
         this.sawExpectedUse = false;
         if (candidate.location() == ConsumableSelector.Location.OFF_HAND) {
            this.mode = ConsumableUseController.Mode.DIRECT_HAND;
            this.directHand = InteractionHand.OFF_HAND;
            if (this.directDelayTicks == 0) {
               this.issueDirectUse(client, player);
            }

            return true;
         } else {
            this.mode = ConsumableUseController.Mode.INVENTORY_SWAP;
            this.destinationHotbarSlot = player.getInventory().getSelectedSlot();
            this.destinationSlot = 36 + this.destinationHotbarSlot;
            if (candidate.location() == ConsumableSelector.Location.MAIN_HAND) {
               this.sourceSlot = -1;
               InventorySwap.useSelected(waitForCompletion);
            } else {
               this.sourceSlot = candidate.containerSlot();
               if (!player.inventoryMenu.isValidSlotIndex(this.sourceSlot) || this.sourceSlot == this.destinationSlot) {
                  this.clear();
                  return false;
               }

               this.sourceBefore = player.inventoryMenu.getSlot(this.sourceSlot).getItem().copy();
               this.destinationBefore = player.inventoryMenu.getSlot(this.destinationSlot).getItem().copy();
               InventorySwap.useFromSlot(this.sourceSlot, waitForCompletion);
            }

            if (!InventorySwap.isBusy()) {
               this.clear();
               return false;
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   boolean tick(Minecraft client, LocalPlayer player) {
      if (!this.isActive()) {
         return false;
      }

      if (player != null && client.gameMode != null) {
         this.activeTicks++;
         if (this.activeTicks >= 200) {
            this.cancel(client, player);
            return false;
         }

         if (this.mode == ConsumableUseController.Mode.DIRECT_HAND) {
            return this.tickDirect(client, player);
         }

         if (this.isExpectedUse(player)) {
            this.sawExpectedUse = true;
         }

         if (!InventorySwap.isBusy()) {
            if (this.restoreIfNeeded(client, player)) {
               this.clear();
               return false;
            } else {
               return true;
            }
         } else {
            if (this.isExpectedUse(player) || !this.positionsRestored(player) || !this.sawExpectedUse && this.activeTicks < 5) {
               return true;
            }

            this.clear();
            return false;
         }
      } else {
         this.cancel(client, player);
         return false;
      }
   }

   boolean isActive() {
      return this.mode != ConsumableUseController.Mode.IDLE;
   }

   void cancel(Minecraft client, LocalPlayer player) {
      if (this.isActive()) {
         if (this.mode == ConsumableUseController.Mode.DIRECT_HAND) {
            this.releaseExpectedUse(client, player);
            this.releaseSyntheticUse(client);
            this.clear();
         } else {
            boolean alreadyReturned = player != null
               && !this.isExpectedUse(player)
               && this.positionsRestored(player)
               && (this.sawExpectedUse || this.activeTicks >= 5);
            if (!alreadyReturned && InventorySwap.isBusy()) {
               InventorySwap.abort();
            }

            this.releaseExpectedUse(client, player);
            this.restoreIfNeeded(client, player);
            this.clear();
         }
      }
   }

   private boolean tickDirect(Minecraft client, LocalPlayer player) {
      if (!this.useIssued) {
         if (this.directDelayTicks > 0) {
            this.directDelayTicks--;
         }

         if (this.directDelayTicks == 0) {
            this.issueDirectUse(client, player);
         }
      }

      if (!this.useIssued) {
         return true;
      } else if (this.waitForCompletion && this.isExpectedUse(player)) {
         this.sawExpectedUse = true;
         client.options.keyUse.setDown(true);
         this.syntheticUseHeld = true;
         return true;
      } else {
         this.releaseSyntheticUse(client);
         this.clear();
         return false;
      }
   }

   private void issueDirectUse(Minecraft client, LocalPlayer player) {
      ItemStack held = player.getItemInHand(this.directHand);
      if (!sameItemAndComponents(held, this.expectedStack)) {
         this.useIssued = true;
      } else {
         client.gameMode.useItem(player, this.directHand);
         player.swing(this.directHand);
         this.useIssued = true;
         if (this.waitForCompletion && this.isExpectedUse(player)) {
            this.sawExpectedUse = true;
            client.options.keyUse.setDown(true);
            this.syntheticUseHeld = true;
         }
      }
   }

   private void releaseExpectedUse(Minecraft client, LocalPlayer player) {
      if (client.gameMode != null && player != null && this.isExpectedUse(player)) {
         client.gameMode.releaseUsingItem(player);
      }
   }

   private void releaseSyntheticUse(Minecraft client) {
      if (this.syntheticUseHeld) {
         client.options.keyUse.setDown(false);
         this.syntheticUseHeld = false;
      }
   }

   private boolean restoreIfNeeded(Minecraft client, LocalPlayer player) {
      if (this.sourceSlot < 0) {
         return true;
      } else if (player == null
         || client.gameMode == null
         || player.containerMenu != player.inventoryMenu
         || !player.inventoryMenu.isValidSlotIndex(this.sourceSlot)
         || !player.inventoryMenu.isValidSlotIndex(this.destinationSlot)) {
         return false;
      } else if (this.positionsRestored(player)) {
         return true;
      } else {
         ItemStack sourceNow = player.inventoryMenu.getSlot(this.sourceSlot).getItem();
         ItemStack destinationNow = player.inventoryMenu.getSlot(this.destinationSlot).getItem();
         if (ItemStack.matches(sourceNow, this.destinationBefore) && this.matchesConsumedRemainder(destinationNow, this.sourceBefore)) {
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, this.sourceSlot, this.destinationHotbarSlot, ContainerInput.SWAP, player);
            return this.positionsRestored(player);
         } else {
            return false;
         }
      }
   }

   private boolean positionsRestored(LocalPlayer player) {
      if (this.sourceSlot < 0) {
         return true;
      } else if (player != null && player.inventoryMenu.isValidSlotIndex(this.sourceSlot) && player.inventoryMenu.isValidSlotIndex(this.destinationSlot)) {
         ItemStack sourceNow = player.inventoryMenu.getSlot(this.sourceSlot).getItem();
         ItemStack destinationNow = player.inventoryMenu.getSlot(this.destinationSlot).getItem();
         return this.matchesConsumedRemainder(sourceNow, this.sourceBefore) && ItemStack.matches(destinationNow, this.destinationBefore);
      } else {
         return false;
      }
   }

   private boolean isExpectedUse(LocalPlayer player) {
      return player != null && player.isUsingItem() && sameItemAndComponents(player.getUseItem(), this.expectedStack);
   }

   private boolean matchesConsumedRemainder(ItemStack current, ItemStack original) {
      if (current.isEmpty()) {
         return original.getCount() == 1 && this.expectedRemainder.isEmpty();
      } else {
         return sameItemAndComponents(current, original)
            ? current.getCount() > 0 && current.getCount() <= original.getCount()
            : original.getCount() == 1 && sameItemAndComponents(current, this.expectedRemainder);
      }
   }

   private static boolean sameItemAndComponents(ItemStack first, ItemStack second) {
      return !first.isEmpty() && !second.isEmpty() && ItemStack.isSameItemSameComponents(first, second);
   }

   private void clear() {
      this.mode = ConsumableUseController.Mode.IDLE;
      this.directHand = InteractionHand.MAIN_HAND;
      this.expectedStack = ItemStack.EMPTY;
      this.expectedRemainder = ItemStack.EMPTY;
      this.waitForCompletion = false;
      this.useIssued = false;
      this.sawExpectedUse = false;
      this.syntheticUseHeld = false;
      this.directDelayTicks = 0;
      this.activeTicks = 0;
      this.sourceSlot = -1;
      this.destinationSlot = -1;
      this.destinationHotbarSlot = -1;
      this.sourceBefore = ItemStack.EMPTY;
      this.destinationBefore = ItemStack.EMPTY;
   }

   private enum Mode {
      IDLE,
      DIRECT_HAND,
      INVENTORY_SWAP;
   }
}
