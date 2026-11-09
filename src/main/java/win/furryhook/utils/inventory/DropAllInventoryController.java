package win.furryhook.utils.inventory;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.screen.ScreenCloseEvent;
import win.furryhook.feature.impl.movement.InventoryMoveFeature;

public final class DropAllInventoryController implements MinecraftContext {
   public static final DropAllInventoryController INSTANCE = new DropAllInventoryController();
   private static final Component DROP_ALL = Component.literal("Выбросить всё");
   private static final Component STOP = Component.literal("Остановить");
   private final Deque<Integer> slots = new ArrayDeque<>();
   private InventoryScreen screen;
   private InventoryMenu menu;
   private Button button;
   private boolean running;

   private DropAllInventoryController() {
   }

   public static void bindButton(Button button) {
      INSTANCE.button = button;
      INSTANCE.updateButton();
   }

   public static void toggle(InventoryScreen screen) {
      if (INSTANCE.running) {
         INSTANCE.cancel();
      } else {
         INSTANCE.start(screen);
      }
   }

   public static boolean blocksInventoryOperations() {
      return INSTANCE.running || InventoryMoveFeature.isClickPipelineBusy();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.running) {
         Minecraft client = event.getClient();
         if (client != null
            && client.player != null
            && client.gui.screen() == this.screen
            && client.player.containerMenu == this.menu
            && !InventoryUtil.hasCarriedItem()) {
            if (!InventorySwap.isBusy()) {
               while (!this.slots.isEmpty()) {
                  int slotId = this.slots.removeFirst();
                  if (this.menu.isValidSlotIndex(slotId) && this.menu.getSlot(slotId).hasItem()) {
                     InventoryUtil.dropStack(slotId);
                     return;
                  }
               }

               this.finish();
            }
         } else {
            this.cancel();
         }
      }
   }

   @EventTarget
   public void onScreenClose(ScreenCloseEvent event) {
      if (event.getScreen() == this.screen) {
         this.cancel();
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cancel();
   }

   private void start(InventoryScreen screen) {
      Minecraft client = Minecraft.getInstance();
      if (client != null
         && client.player != null
         && screen != null
         && client.player.containerMenu == screen.getMenu()
         && !InventoryUtil.hasCarriedItem()
         && !InventorySwap.isBusy()) {
         this.screen = screen;
         this.menu = (InventoryMenu)screen.getMenu();
         this.slots.clear();

         for (int slotId = 0; slotId < this.menu.slots.size(); slotId++) {
            if (this.menu.getSlot(slotId).hasItem()) {
               this.slots.addLast(slotId);
            }
         }

         this.running = !this.slots.isEmpty();
         this.updateButton();
      }
   }

   private void finish() {
      this.running = false;
      this.slots.clear();
      this.updateButton();
   }

   private void cancel() {
      this.finish();
      this.screen = null;
      this.menu = null;
      this.button = null;
   }

   private void updateButton() {
      if (this.button != null) {
         this.button.setMessage(this.running ? STOP : DROP_ALL);
      }
   }
}
