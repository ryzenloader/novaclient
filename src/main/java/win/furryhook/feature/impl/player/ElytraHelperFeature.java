package win.furryhook.feature.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.event.events.input.PlayerInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.InputGuard;
import win.furryhook.utils.inventory.HotbarSyncGuard;
import win.furryhook.utils.inventory.InventorySwap;

public final class ElytraHelperFeature extends Feature {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int GLFW_PRESS = 1;
   private static final int GLFW_RELEASE = 0;
   private static final int CHEST_SLOT = 6;
   private static final Item[] CHESTPLATE_PRIORITY = new Item[]{
      Items.NETHERITE_CHESTPLATE,
      Items.DIAMOND_CHESTPLATE,
      Items.IRON_CHESTPLATE,
      Items.GOLDEN_CHESTPLATE,
      Items.CHAINMAIL_CHESTPLATE,
      Items.LEATHER_CHESTPLATE
   };
   public final InputBindSetting swapChestKey = this.register(new InputBindSetting("Swap Key", -1));
   public final InputBindSetting fireworkKey = this.register(new InputBindSetting("Firework Key", -1));
   public final BooleanSetting autoFly = this.register(new BooleanSetting("Auto Fly", true));
   public final BooleanSetting autoFirework = this.register(new BooleanSetting("Auto Firework", false));
   public final BooleanSetting autoFireworkOnStart = this.register(new BooleanSetting("Only On Takeoff", false).visibleWhen(this.autoFirework::getValue));
   public final NumberSetting fireworkDelay = this.register(
      new NumberSetting("Firework Delay", 570.0, 100.0, 3000.0, 10.0, " ms")
         .visibleWhen(() -> this.autoFirework.getValue() && !this.autoFireworkOnStart.getValue())
   );
   public final ModeSetting swapMode = this.register(new ModeSetting("Swap Mode", "Funtime", "Funtime", "Holyworld", "Vanilla"));
   public final NumberSetting swapDelay = this.register(new NumberSetting("Swap Delay", 0.0, 0.0, 20.0, 1.0, " ticks"));
   public final BooleanSetting removeOnLand = this.register(new BooleanSetting("Remove On Land", false));
   public final BooleanSetting startSetting = this.register(new BooleanSetting("Fast Start", false));
   public final BooleanSetting equipInCT = this.register(new BooleanSetting("Equip In CT", false));
   private boolean swapQueued;
   private int swapCooldown;
   private boolean wasOnGround = true;
   private boolean flowActive;
   private int flowRemaining;
   private boolean flowClickDone;
   private boolean flowEnablePending;
   private int chestSwapSlot = -1;
   private boolean fastStartArmed;
   private ElytraHelperFeature.FastStart fastStart = ElytraHelperFeature.FastStart.NONE;
   private int fastStartTicks;
   private boolean ctSwapPending;
   private int ctSwapTicks;
   private boolean fireworkHeld;
   private boolean fireworkPending;
   private boolean hasFiredOnStart;
   private boolean queueJump;
   private long lastFireworkUse = Long.MIN_VALUE;

   public ElytraHelperFeature() {
      super("ElytraHelper", "Elytra swap, firework and auto fly", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 0 || !InputGuard.isBindingBlocked()) {
         if (this.swapChestKey.matches(event.getKey()) && event.getAction() == 1) {
            this.queueSwap();
         }

         if (this.fireworkKey.matches(event.getKey())) {
            if (event.getAction() == 1) {
               this.fireworkHeld = true;
            } else if (event.getAction() == 0) {
               this.fireworkHeld = false;
            }
         }
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 0 || !InputGuard.isBindingBlocked()) {
         if (this.swapChestKey.matchesMouse(event.getButton()) && event.getAction() == 1) {
            this.queueSwap();
         }

         if (this.fireworkKey.matchesMouse(event.getButton())) {
            if (event.getAction() == 1) {
               this.fireworkHeld = true;
            } else if (event.getAction() == 0) {
               this.fireworkHeld = false;
            }
         }
      }
   }

   @EventTarget
   public void onInput(PlayerInputEvent event) {
      if (this.queueJump) {
         event.setJump(true);
         this.queueJump = false;
      }
   }

   private void queueSwap() {
      LocalPlayer player = MC.player;
      if (player != null) {
         if (!this.flowActive && !this.ctSwapPending && this.fastStart == ElytraHelperFeature.FastStart.NONE && this.swapCooldown <= 0) {
            boolean hasElytra = this.findContainerSlot(player, Items.ELYTRA) >= 0;
            int target = this.chestPlateSlot(player);
            if (hasElytra || target >= 0) {
               if (this.equipInCT.getValue()) {
                  this.beginCtSwap(player);
               } else if (target >= 0) {
                  this.swapQueued = true;
               }
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && mc.gameMode != null) {
         ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
         if (this.swapCooldown > 0) {
            this.swapCooldown--;
         }

         if (this.flowActive) {
            this.tickFlow(mc, player);
         }

         if (this.ctSwapPending) {
            this.tickCtSwap(mc, player);
         }

         this.tickFastStart(mc, player);
         if (this.removeOnLand.getValue()
            && !this.wasOnGround
            && player.onGround()
            && chest.is(Items.ELYTRA)
            && this.chestPlateSlot(player) >= 0
            && this.swapCooldown == 0
            && !this.flowActive) {
            this.startSwapFlow(player);
         }

         this.wasOnGround = player.onGround();
         if (this.swapQueued && this.swapCooldown == 0 && !this.flowActive && !this.ctSwapPending) {
            this.swapQueued = false;
            int slot = this.chestPlateSlot(player);
            if (slot < 0) {
               return;
            }

            boolean puttingElytra = player.inventoryMenu.getSlot(slot).getItem().is(Items.ELYTRA);
            this.fastStartArmed = this.startSetting.getValue() && puttingElytra && this.findContainerSlot(player, Items.FIREWORK_ROCKET) >= 0;
            this.startSwapFlow(player);
         }

         if (this.fireworkHeld && player.isFallFlying()) {
            this.fireworkPending = true;
            this.fireworkHeld = false;
         }

         boolean wearingElytra = chest.is(Items.ELYTRA);
         if (this.autoFly.getValue() && wearingElytra && !player.getAbilities().mayfly) {
            if (player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()) {
               this.queueJump = true;
            }

            if (!player.onGround() && !player.isFallFlying() && !player.isInWater() && !player.isInLava() && !player.onClimbable()) {
               boolean started = player.tryToStartFallFlying();
               if (mc.getConnection() != null) {
                  mc.getConnection().send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
               }

               if (started && this.autoFirework.getValue() && this.autoFireworkOnStart.getValue() && !this.hasFiredOnStart) {
                  this.fireworkPending = true;
                  this.hasFiredOnStart = true;
               }
            }
         }

         if (player.onGround() || player.isInWater() || player.isInLava()) {
            this.hasFiredOnStart = false;
         }

         if (player.isFallFlying()
            && this.autoFirework.getValue()
            && !this.autoFireworkOnStart.getValue()
            && System.currentTimeMillis() - this.lastFireworkUse >= Math.round(this.fireworkDelay.getValue())) {
            this.fireworkPending = true;
         }

         if (this.fireworkPending) {
            if (!player.isFallFlying()) {
               this.fireworkPending = false;
            } else if (!InventorySwap.isBusy() && this.useFirework(mc, player)) {
               this.fireworkPending = false;
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      this.swapQueued = false;
      this.swapCooldown = 0;
      this.wasOnGround = true;
      this.fireworkHeld = false;
      this.fireworkPending = false;
      this.hasFiredOnStart = false;
      this.queueJump = false;
      this.flowActive = false;
      this.flowRemaining = 0;
      this.flowClickDone = false;
      this.flowEnablePending = false;
      this.chestSwapSlot = -1;
      this.fastStartArmed = false;
      this.fastStart = ElytraHelperFeature.FastStart.NONE;
      this.fastStartTicks = 0;
      this.ctSwapPending = false;
      this.ctSwapTicks = 0;
   }

   private void startSwapFlow(LocalPlayer player) {
      this.chestSwapSlot = this.chestPlateSlot(player);
      if (this.chestSwapSlot >= 0) {
         if (this.swapMode.is("Vanilla")) {
            this.performSwapClicks(player, this.chestSwapSlot);
            this.swapCooldown = this.swapDelay.getValue().intValue();
            this.armFastStart();
         } else {
            this.disableMoveKeys();
            this.flowActive = true;
            this.flowClickDone = false;
            this.flowEnablePending = false;
            this.flowRemaining = this.swapMode.is("Holyworld") ? 4 : 2;
         }
      }
   }

   private void tickFlow(Minecraft mc, LocalPlayer player) {
      if (--this.flowRemaining <= 0) {
         if (!this.flowClickDone) {
            this.flowClickDone = true;
            this.performSwapClicks(player, this.chestSwapSlot);
            this.swapCooldown = this.swapDelay.getValue().intValue();
            this.armFastStart();
            if (this.swapMode.is("Holyworld")) {
               this.flowEnablePending = true;
               this.flowRemaining = 1;
            } else {
               this.enableMoveKeys(mc, player);
               this.flowActive = false;
            }
         } else {
            if (this.flowEnablePending) {
               this.flowEnablePending = false;
               this.enableMoveKeys(mc, player);
               this.flowActive = false;
            }
         }
      }
   }

   private void armFastStart() {
      if (this.fastStartArmed) {
         this.fastStartArmed = false;
         this.fastStart = ElytraHelperFeature.FastStart.JUMP;
         this.fastStartTicks = 4;
      }
   }

   private void tickFastStart(Minecraft mc, LocalPlayer player) {
      if (this.fastStart != ElytraHelperFeature.FastStart.NONE) {
         if (--this.fastStartTicks <= 0) {
            if (this.fastStart == ElytraHelperFeature.FastStart.JUMP) {
               if (player.onGround()) {
                  this.queueJump = true;
               }

               this.fastStart = ElytraHelperFeature.FastStart.FIREWORK;
               this.fastStartTicks = 3;
            } else {
               this.fastStart = ElytraHelperFeature.FastStart.NONE;
               if (!player.isFallFlying()) {
                  player.tryToStartFallFlying();
                  if (mc.getConnection() != null) {
                     mc.getConnection().send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
                  }
               }

               if (!player.getCooldowns().isOnCooldown(player.inventoryMenu.getSlot(this.findContainerSlot(player, Items.FIREWORK_ROCKET)).getItem())
                  && this.useFirework(mc, player)) {
                  this.lastFireworkUse = System.currentTimeMillis();
               }
            }
         }
      }
   }

   private void performSwapClicks(LocalPlayer player, int containerSlot) {
      Minecraft mc = Minecraft.getInstance();
      if (player.inventoryMenu != null && mc.gameMode != null) {
         int containerId = player.inventoryMenu.containerId;
         if (containerSlot >= 36) {
            mc.gameMode.handleContainerInput(containerId, 6, containerSlot - 36, ContainerInput.SWAP, player);
         } else {
            mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, player);
            mc.gameMode.handleContainerInput(containerId, 6, 0, ContainerInput.PICKUP, player);
            mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, player);
         }
      }
   }

   private void disableMoveKeys() {
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

   private void enableMoveKeys(Minecraft mc, LocalPlayer player) {
      if (mc.getConnection() != null) {
         mc.getConnection().send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
      }

      long window = mc.getWindow().handle();
      mc.options.keyUp.setDown(GLFW.glfwGetKey(window, 87) == 1);
      mc.options.keyDown.setDown(GLFW.glfwGetKey(window, 83) == 1);
      mc.options.keyLeft.setDown(GLFW.glfwGetKey(window, 65) == 1);
      mc.options.keyRight.setDown(GLFW.glfwGetKey(window, 68) == 1);
      mc.options.keyJump.setDown(GLFW.glfwGetKey(window, 32) == 1);
   }

   private void beginCtSwap(LocalPlayer player) {
      if (MC.gui.screen() == null) {
         MC.setScreenAndShow(new InventoryScreen(player));
         this.ctSwapPending = true;
         this.ctSwapTicks = 0;
      }
   }

   private void tickCtSwap(Minecraft mc, LocalPlayer player) {
      this.ctSwapTicks++;
      if (this.ctSwapTicks >= 2) {
         int slot = this.chestPlateSlot(player);
         if (slot >= 0) {
            this.performSwapClicks(player, slot);
         }

         if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
         }

         MC.gui.setScreen(null);
         this.ctSwapPending = false;
         this.ctSwapTicks = 0;
         this.swapCooldown = this.swapDelay.getValue().intValue();
      }
   }

   private int chestPlateSlot(LocalPlayer player) {
      if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
         for (Item item : CHESTPLATE_PRIORITY) {
            int slot = this.findContainerSlot(player, item);
            if (slot >= 0) {
               return slot;
            }
         }

         return -1;
      } else {
         return this.findContainerSlot(player, Items.ELYTRA);
      }
   }

   private int findContainerSlot(LocalPlayer player, Item item) {
      for (int slot = 9; slot < 45; slot++) {
         if (player.inventoryMenu.getSlot(slot).getItem().is(item)) {
            return slot;
         }
      }

      return player.inventoryMenu.getSlot(45).getItem().is(item) ? 45 : -1;
   }

   private boolean useFirework(Minecraft mc, LocalPlayer player) {
      if (mc.gameMode == null || player.connection == null || player.containerMenu != player.inventoryMenu || InventorySwap.isBusy()) {
         return false;
      }

      if (player.isUsingItem()) {
         return false;
      }

      if (player.getMainHandItem().is(Items.FIREWORK_ROCKET)) {
         this.sendUsePacket(player, InteractionHand.MAIN_HAND);
         this.lastFireworkUse = System.currentTimeMillis();
         return true;
      }

      if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
         this.sendUsePacket(player, InteractionHand.OFF_HAND);
         this.lastFireworkUse = System.currentTimeMillis();
         return true;
      }

      int hotbarSlot = this.findHotbarSlot(player, Items.FIREWORK_ROCKET);
      if (hotbarSlot >= 0) {
         HotbarSyncGuard.begin();
         this.useCarriedFromHotbar(player, hotbarSlot);
         HotbarSyncGuard.end();
         this.lastFireworkUse = System.currentTimeMillis();
         return true;
      }

      int slot = this.findInventorySlot(player, Items.FIREWORK_ROCKET);
      if (slot < 0) {
         return false;
      }

      int screenSlot = slot < 9 ? slot + 36 : slot;
      HotbarSyncGuard.begin();
      mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, screenSlot, 40, ContainerInput.SWAP, player);
      this.sendUsePacket(player, InteractionHand.OFF_HAND);
      mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, screenSlot, 40, ContainerInput.SWAP, player);
      HotbarSyncGuard.end();
      this.lastFireworkUse = System.currentTimeMillis();
      return true;
   }

   private void sendUsePacket(LocalPlayer player, InteractionHand hand) {
      player.connection.send(new ServerboundUseItemPacket(hand, 0, player.getYRot(), player.getXRot()));
   }

   private void useCarriedFromHotbar(LocalPlayer player, int rocketSlot) {
      int previous = player.getInventory().getSelectedSlot();
      player.connection.send(new ServerboundSetCarriedItemPacket(rocketSlot));
      this.sendUsePacket(player, InteractionHand.MAIN_HAND);
      player.connection.send(new ServerboundSetCarriedItemPacket(previous));
   }

   private int findHotbarSlot(LocalPlayer player, Item item) {
      for (int slot = 0; slot < 9; slot++) {
         if (player.getInventory().getItem(slot).is(item)) {
            return slot;
         }
      }

      return -1;
   }

   private int findInventorySlot(LocalPlayer player, Item item) {
      for (int i = 0; i < 36; i++) {
         if (player.getInventory().getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }

   private enum FastStart {
      NONE,
      JUMP,
      FIREWORK;
   }
}
