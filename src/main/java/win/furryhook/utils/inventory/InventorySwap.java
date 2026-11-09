package win.furryhook.utils.inventory;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;

public final class InventorySwap implements MinecraftContext {
   public static final InventorySwap INSTANCE = new InventorySwap();
   private static final int OFFHAND_BUTTON = 40;
   private InventorySwap.State state = InventorySwap.State.IDLE;
   private int sourceSlot = -1;
   private int hotbarButton = 40;
   private boolean useAndReturn;
   private boolean waitForUseCompletion;
   private boolean immediateReturn;
   private boolean directUse;
   private ContainerInput clickInput = ContainerInput.SWAP;
   private boolean syntheticUseHeld;
   private int useWaitTicks;
   private static final int MAX_USE_WAIT_TICKS = 200;
   private int holdBeforeTicks = 0;
   private int holdAfterTicks = 0;
   private int currentHoldTicks = 0;

   private InventorySwap() {
   }

   public static void equip(int containerSlot) {
      LocalPlayer player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.isValidSlotIndex(containerSlot)
         && !player.inventoryMenu.getSlot(containerSlot).getItem().isEmpty()) {
         mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, containerSlot, 40, ContainerInput.SWAP, player);
      }
   }

   public static void moveToHotbar(int containerSlot, int hotbarSlot) {
      if (INSTANCE.player() != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && hotbarSlot >= 0
         && hotbarSlot <= 8) {
         INSTANCE.begin(containerSlot, hotbarSlot, false, false, false, false, ContainerInput.SWAP);
      }
   }

   public static void useFromSlot(int containerSlot) {
      useFromSlot(containerSlot, false);
   }

   public static void useFromSlot(int containerSlot, boolean waitForUseCompletion) {
      useFromSlot(containerSlot, waitForUseCompletion, false);
   }

   public static void useFromSlot(int containerSlot, boolean waitForUseCompletion, boolean immediateReturn) {
      useFromSlot(containerSlot, waitForUseCompletion, immediateReturn, 0, 0);
   }

   public static void useFromSlot(int containerSlot, boolean waitForUseCompletion, boolean immediateReturn, int holdBeforeTicks, int holdAfterTicks) {
      LocalPlayer player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.containerMenu == player.inventoryMenu) {
         INSTANCE.beginWithDelay(
            containerSlot,
            player.getInventory().getSelectedSlot(),
            true,
            waitForUseCompletion,
            immediateReturn,
            false,
            ContainerInput.SWAP,
            holdBeforeTicks,
            holdAfterTicks
         );
      }
   }

   public static void useSelected(boolean waitForUseCompletion) {
      LocalPlayer player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.containerMenu == player.inventoryMenu) {
         INSTANCE.begin(-1, player.getInventory().getSelectedSlot(), true, waitForUseCompletion, false, true, ContainerInput.SWAP);
      }
   }

   public static boolean dropStack(int containerSlot) {
      LocalPlayer player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.isValidSlotIndex(containerSlot)
         && !player.inventoryMenu.getSlot(containerSlot).getItem().isEmpty()) {
         INSTANCE.begin(containerSlot, 1, false, false, false, false, ContainerInput.THROW);
         return true;
      } else {
         return false;
      }
   }

   public static boolean isBusy() {
      return INSTANCE.state != InventorySwap.State.IDLE;
   }

   public static boolean shouldStopMovement() {
      return INSTANCE.state != InventorySwap.State.IDLE;
   }

   public static void abort() {
      INSTANCE.finish();
   }

   private void begin(
      int containerSlot,
      int hotbarButton,
      boolean useAndReturn,
      boolean waitForUseCompletion,
      boolean immediateReturn,
      boolean directUse,
      ContainerInput clickInput
   ) {
      this.beginWithDelay(containerSlot, hotbarButton, useAndReturn, waitForUseCompletion, immediateReturn, directUse, clickInput, 0, 0);
   }

   private void beginWithDelay(
      int containerSlot,
      int hotbarButton,
      boolean useAndReturn,
      boolean waitForUseCompletion,
      boolean immediateReturn,
      boolean directUse,
      ContainerInput clickInput,
      int holdBeforeTicks,
      int holdAfterTicks
   ) {
      this.sourceSlot = containerSlot;
      this.hotbarButton = hotbarButton;
      this.useAndReturn = useAndReturn;
      this.waitForUseCompletion = waitForUseCompletion;
      this.immediateReturn = immediateReturn;
      this.directUse = directUse;
      this.clickInput = clickInput == null ? ContainerInput.SWAP : clickInput;
      this.syntheticUseHeld = false;
      this.useWaitTicks = 0;
      this.holdBeforeTicks = Math.max(0, holdBeforeTicks);
      this.holdAfterTicks = Math.max(0, holdAfterTicks);
      this.currentHoldTicks = 0;
      HotbarSyncGuard.begin();
      this.state = InventorySwap.State.MOVING;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.state != InventorySwap.State.IDLE) {
         LocalPlayer player = this.player();
         if (player != null && (this.directUse || player.inventoryMenu.isValidSlotIndex(this.sourceSlot)) && player.containerMenu == player.inventoryMenu) {
            switch (this.state) {
               case MOVING:
                  if (!this.directUse) {
                     if (player.inventoryMenu.getSlot(this.sourceSlot).getItem().isEmpty()) {
                        this.finish();
                        return;
                     }

                     this.click(player);
                  }

                  if (this.holdBeforeTicks > 0 && this.useAndReturn) {
                     this.currentHoldTicks = this.holdBeforeTicks;
                     this.state = InventorySwap.State.HOLDING_BEFORE_USE;
                  } else {
                     this.state = this.useAndReturn ? InventorySwap.State.USING : InventorySwap.State.SETTLING;
                  }
                  break;
               case HOLDING_BEFORE_USE:
                  this.currentHoldTicks--;
                  if (this.currentHoldTicks <= 0) {
                     this.state = InventorySwap.State.USING;
                  }
                  break;
               case USING:
                  int selected = player.getInventory().getSelectedSlot();
                  if (selected != this.hotbarButton) {
                     player.getInventory().setSelectedSlot(this.hotbarButton);
                  }

                  mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                  player.swing(InteractionHand.MAIN_HAND);
                  if (selected != this.hotbarButton) {
                     player.getInventory().setSelectedSlot(selected);
                  }

                  if (this.waitForUseCompletion && player.isUsingItem()) {
                     mc.options.keyUse.setDown(true);
                     this.syntheticUseHeld = true;
                     this.state = InventorySwap.State.WAITING_FOR_USE;
                  } else if (this.immediateReturn && !this.directUse) {
                     this.click(player);
                     this.state = InventorySwap.State.SETTLING;
                  } else if (this.holdAfterTicks > 0) {
                     this.currentHoldTicks = this.holdAfterTicks;
                     this.state = InventorySwap.State.HOLDING_AFTER_USE;
                  } else {
                     this.state = InventorySwap.State.RETURNING;
                  }
                  break;
               case WAITING_FOR_USE:
                  this.useWaitTicks++;
                  if (!player.isUsingItem() || this.useWaitTicks >= 200) {
                     this.releaseSyntheticUse();
                     if (this.holdAfterTicks > 0) {
                        this.currentHoldTicks = this.holdAfterTicks;
                        this.state = InventorySwap.State.HOLDING_AFTER_USE;
                     } else {
                        this.state = InventorySwap.State.RETURNING;
                     }
                  }
                  break;
               case HOLDING_AFTER_USE:
                  this.currentHoldTicks--;
                  if (this.currentHoldTicks <= 0) {
                     this.state = InventorySwap.State.RETURNING;
                  }
                  break;
               case RETURNING:
                  if (!this.directUse) {
                     this.click(player);
                  }

                  this.state = InventorySwap.State.SETTLING;
                  break;
               case SETTLING:
                  this.finish();
                  break;
               default:
                  this.finish();
            }
         } else {
            this.finish();
         }
      }
   }

   private void click(LocalPlayer player) {
      mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, this.sourceSlot, this.hotbarButton, this.clickInput, player);
   }

   private void finish() {
      HotbarSyncGuard.end();
      this.releaseSyntheticUse();
      this.state = InventorySwap.State.IDLE;
      this.sourceSlot = -1;
      this.hotbarButton = 40;
      this.useAndReturn = false;
      this.waitForUseCompletion = false;
      this.immediateReturn = false;
      this.directUse = false;
      this.clickInput = ContainerInput.SWAP;
      this.useWaitTicks = 0;
      this.holdBeforeTicks = 0;
      this.holdAfterTicks = 0;
      this.currentHoldTicks = 0;
   }

   private void releaseSyntheticUse() {
      if (this.syntheticUseHeld) {
         mc.options.keyUse.setDown(false);
         this.syntheticUseHeld = false;
      }
   }

   private enum State {
      IDLE,
      MOVING,
      HOLDING_BEFORE_USE,
      USING,
      WAITING_FOR_USE,
      HOLDING_AFTER_USE,
      RETURNING,
      SETTLING;
   }
}
