package win.furryhook.feature.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.utils.inventory.HotbarSyncGuard;
import win.furryhook.utils.inventory.InventorySwap;

public final class ClickPearlFeature extends Feature {
   private static final Minecraft MC = Minecraft.getInstance();
   private final InputBindSetting key = this.register(new InputBindSetting("Key", -1));
   private final ModeSetting mode = this.register(new ModeSetting("Mode", "Funtime", "Funtime", "Vanilla", "Holyworld"));
   private final BooleanSetting silent = this.register(new BooleanSetting("Silent Throw", true));
   private static final int MAX_CYCLE_TICKS = 40;
   private boolean queued;
   private ClickPearlFeature.Phase phase = ClickPearlFeature.Phase.IDLE;
   private int phaseWait;
   private int cycleTicks;
   private int pearlSlot = -1;
   private boolean doSwap;
   private boolean thrown;

   public ClickPearlFeature() {
      super("ClickPearl", "Quickly throws an ender pearl", FeatureCategory.PLAYER, -1);
   }

   @Override
   protected void onDisable() {
      this.queued = false;
      this.cancelCycle();
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 1 && this.key.matches(event.getKey())) {
         this.queued = true;
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 1 && this.key.matchesMouse(event.getButton())) {
         this.queued = true;
         event.cancel();
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.phase != ClickPearlFeature.Phase.IDLE) {
         this.tickCycle();
      } else if (this.queued) {
         LocalPlayer player = MC.player;
         if (player != null && MC.level != null && player.containerMenu == player.inventoryMenu && MC.gameMode != null && !InventorySwap.isBusy()) {
            int slot = this.findPearlSlot();
            if (slot < 0) {
               this.queued = false;
            } else if (player.getCooldowns().isOnCooldown(player.inventoryMenu.getSlot(slot).getItem())) {
               this.queued = false;
            } else {
               this.queued = false;
               if (!this.silent.getValue() || !this.throwSilent(player, slot)) {
                  this.startCycle(player, slot);
               }
            }
         }
      }
   }

   private boolean throwSilent(LocalPlayer player, int slot) {
      if (player.connection != null && !player.isUsingItem()) {
         if (player.getMainHandItem().is(Items.ENDER_PEARL)) {
            this.sendUse(player, InteractionHand.MAIN_HAND);
            return true;
         }

         if (player.getOffhandItem().is(Items.ENDER_PEARL)) {
            this.sendUse(player, InteractionHand.OFF_HAND);
            return true;
         }

         for (int hotbar = 0; hotbar < 9; hotbar++) {
            if (player.getInventory().getItem(hotbar).is(Items.ENDER_PEARL)) {
               HotbarSyncGuard.begin();
               int previous = player.getInventory().getSelectedSlot();
               player.connection.send(new ServerboundSetCarriedItemPacket(hotbar));
               this.sendUse(player, InteractionHand.MAIN_HAND);
               player.connection.send(new ServerboundSetCarriedItemPacket(previous));
               HotbarSyncGuard.end();
               return true;
            }
         }

         HotbarSyncGuard.begin();
         MC.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, 40, ContainerInput.SWAP, player);
         this.sendUse(player, InteractionHand.OFF_HAND);
         MC.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, 40, ContainerInput.SWAP, player);
         HotbarSyncGuard.end();
         return true;
      } else {
         return false;
      }
   }

   private void sendUse(LocalPlayer player, InteractionHand hand) {
      player.connection.send(new ServerboundUseItemPacket(hand, 0, player.getYRot(), player.getXRot()));
   }

   private void startCycle(LocalPlayer player, int slot) {
      this.pearlSlot = slot;
      int selected = player.getInventory().getSelectedSlot();
      this.doSwap = slot != selected + 36;
      HotbarSyncGuard.begin();
      int preStop = this.profileStop();
      if (preStop > 0) {
         this.stopKeys();
         this.enter(ClickPearlFeature.Phase.STOP, preStop);
      } else {
         this.swapIn();
         this.enter(ClickPearlFeature.Phase.WAIT_USE, this.profileUseDelay() + 1);
      }
   }

   private void tickCycle() {
      LocalPlayer player = MC.player;
      if (player != null && MC.level != null && player.containerMenu == player.inventoryMenu) {
         if (++this.cycleTicks > 40) {
            this.swapOut();
            this.finish();
         } else if (--this.phaseWait <= 0) {
            switch (this.phase) {
               case STOP:
                  this.swapIn();
                  this.enter(ClickPearlFeature.Phase.WAIT_USE, this.profileUseDelay() + 1);
                  break;
               case WAIT_USE:
                  this.throwFromMainHand();
                  this.thrown = true;
                  if (this.profileBackDelay() <= 0) {
                     this.swapOut();
                     this.finish();
                  } else {
                     this.enter(ClickPearlFeature.Phase.WAIT_BACK, this.profileBackDelay());
                  }
                  break;
               case WAIT_BACK:
                  this.swapOut();
                  this.finish();
                  break;
               default:
                  this.cancelCycle();
            }
         }
      } else {
         this.cancelCycle();
      }
   }

   private void swapIn() {
      if (this.doSwap) {
         this.clickPearl();
      }

      this.restoreKeys();
   }

   private void swapOut() {
      if (this.doSwap) {
         this.clickPearl();
      }
   }

   private void finish() {
      HotbarSyncGuard.end();
      this.reset();
   }

   private void enter(ClickPearlFeature.Phase next, int wait) {
      this.phase = next;
      this.phaseWait = wait;
   }

   private void clickPearl() {
      LocalPlayer player = MC.player;
      if (player != null) {
         MC.gameMode
            .handleContainerInput(player.inventoryMenu.containerId, this.pearlSlot, player.getInventory().getSelectedSlot(), ContainerInput.SWAP, player);
      }
   }

   private void throwFromMainHand() {
      LocalPlayer player = MC.player;
      if (player != null && player.connection != null) {
         player.connection.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, player.getYRot(), player.getXRot()));
      }
   }

   private void stopKeys() {
      MC.options.keyUp.setDown(false);
      MC.options.keyDown.setDown(false);
      MC.options.keyLeft.setDown(false);
      MC.options.keyRight.setDown(false);
      MC.options.keyJump.setDown(false);
      LocalPlayer player = MC.player;
      if (player != null) {
         player.setSprinting(false);
      }
   }

   private void restoreKeys() {
      long window = MC.getWindow().handle();
      MC.options.keyUp.setDown(GLFW.glfwGetKey(window, 87) == 1);
      MC.options.keyDown.setDown(GLFW.glfwGetKey(window, 83) == 1);
      MC.options.keyLeft.setDown(GLFW.glfwGetKey(window, 65) == 1);
      MC.options.keyRight.setDown(GLFW.glfwGetKey(window, 68) == 1);
      MC.options.keyJump.setDown(GLFW.glfwGetKey(window, 32) == 1);
   }

   private int profileStop() {
      return this.mode.is("Holyworld") ? 4 : (this.mode.is("Funtime") ? 2 : 0);
   }

   private int profileUseDelay() {
      return this.mode.is("Holyworld") ? 2 : 0;
   }

   private int profileBackDelay() {
      return !this.mode.is("Funtime") && !this.mode.is("Holyworld") ? 0 : 1;
   }

   private int findPearlSlot() {
      LocalPlayer player = MC.player;
      if (player == null) {
         return -1;
      }

      for (int slot = 36; slot < 45; slot++) {
         if (player.inventoryMenu.getSlot(slot).getItem().is(Items.ENDER_PEARL)) {
            return slot;
         }
      }

      for (int slot = 9; slot < 36; slot++) {
         if (player.inventoryMenu.getSlot(slot).getItem().is(Items.ENDER_PEARL)) {
            return slot;
         }
      }

      return player.inventoryMenu.getSlot(45).getItem().is(Items.ENDER_PEARL) ? 45 : -1;
   }

   private void cancelCycle() {
      if (this.phase != ClickPearlFeature.Phase.IDLE) {
         if (this.thrown) {
            LocalPlayer player = MC.player;
            if (player != null && player.containerMenu == player.inventoryMenu) {
               this.swapOut();
            }
         }

         HotbarSyncGuard.end();
         this.restoreKeys();
         this.reset();
      }
   }

   private void reset() {
      this.phase = ClickPearlFeature.Phase.IDLE;
      this.phaseWait = 0;
      this.cycleTicks = 0;
      this.pearlSlot = -1;
      this.doSwap = false;
      this.thrown = false;
   }

   private enum Phase {
      IDLE,
      STOP,
      WAIT_USE,
      WAIT_BACK;
   }
}
