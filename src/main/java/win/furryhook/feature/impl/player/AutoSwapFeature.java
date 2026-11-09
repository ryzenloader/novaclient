package win.furryhook.feature.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.hud.NotificationsElement;
import win.furryhook.utils.InputGuard;

public final class AutoSwapFeature extends Feature {
   private static final int GLFW_RELEASE = 0;
   private static final int OFFHAND_BUTTON = 40;
   private static final String[] ITEM_NAMES = new String[]{"Totem of Undying", "Player Head", "Golden Apple", "Shield"};
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Legit", "Instant", "Legit", "FunTime", "Grim", "Custom"));
   public final InputBindSetting swapKey = this.register(new InputBindSetting("Key", -1));
   public final ModeSetting first = this.register(new ModeSetting("First Item", "Totem of Undying", ITEM_NAMES));
   public final ModeSetting second = this.register(new ModeSetting("Second Item", "Player Head", ITEM_NAMES));
   public final BooleanSetting stopMovement = this.register(new BooleanSetting("Stop Movement", true).visibleWhen(this::custom));
   public final BooleanSetting stopSprint = this.register(new BooleanSetting("Stop Sprint", true).visibleWhen(this::custom));
   public final BooleanSetting closeInventory = this.register(new BooleanSetting("Close Inventory", true).visibleWhen(this::custom));
   public final NumberSetting preSwapMin = this.register(new NumberSetting("Pre-Swap Min", 20.0, 0.0, 300.0, 1.0, " ms").visibleWhen(this::custom));
   public final NumberSetting preSwapMax = this.register(new NumberSetting("Pre-Swap Max", 100.0, 0.0, 300.0, 1.0, " ms").visibleWhen(this::custom));
   public final NumberSetting stopTicksBefore = this.register(new NumberSetting("Stop Before", 4.0, 0.0, 20.0, 1.0, " ticks").visibleWhen(this::funTime));
   public final NumberSetting stopTicksAfter = this.register(
      new NumberSetting("Stop After", 3.0, 0.0, 20.0, 1.0, " ticks").visibleWhen(() -> this.funTime() || this.grim())
   );
   public final BooleanSetting stopInAir = this.register(new BooleanSetting("Stop In Air", true).visibleWhen(this::funTime));
   public final NumberSetting airStopSpeed = this.register(
      new NumberSetting("Air Stop Speed", 0.2, 0.01, 0.35, 0.01, " b/t").visibleWhen(() -> this.funTime() && this.stopInAir.getValue())
   );
   public final NumberSetting maxAirStopTicks = this.register(
      new NumberSetting("Max Air Stop", 6.0, 1.0, 30.0, 1.0, " ticks").visibleWhen(() -> this.funTime() && this.stopInAir.getValue())
   );
   public final NumberSetting stopSpeed = this.register(new NumberSetting("Stop Speed", 0.06, 0.01, 0.3, 0.01, " b/t").visibleWhen(this::grim));
   public final NumberSetting maxStopTicks = this.register(new NumberSetting("Max Stop", 30.0, 5.0, 60.0, 1.0, " ticks").visibleWhen(this::grim));
   private static volatile boolean holdingStill;
   private boolean wasHeld;
   private long pendingAt;
   private int holdTicks;
   private boolean clicked;
   private int airTicks;
   private static volatile boolean grimHold;
   private int grimTicks;

   public static boolean shouldStopMovement() {
      return holdingStill || grimHold;
   }

   public AutoSwapFeature() {
      super("AutoSwap", "Configurable offhand swap on key press", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 0 || !InputGuard.isBindingBlocked()) {
         if (this.swapKey.matches(event.getKey())) {
            this.updateHeld(event.getAction());
         }
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 0 || !InputGuard.isBindingBlocked()) {
         if (this.swapKey.matchesMouse(event.getButton())) {
            this.updateHeld(event.getAction());
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && mc.gameMode != null) {
         if (holdingStill) {
            this.tickFunTimeHold(mc, player);
         } else if (grimHold) {
            this.tickGrimHold(mc, player);
         } else {
            if (this.pendingAt != 0L && System.currentTimeMillis() >= this.pendingAt) {
               this.pendingAt = 0L;
               if (this.funTime()) {
                  this.beginFunTimeHold(player);
               } else if (this.grim()) {
                  this.beginGrimHold(player);
               } else {
                  this.performSwap(mc, player);
               }
            }
         }
      }
   }

   private void beginFunTimeHold(LocalPlayer player) {
      if (player.onGround() || this.stopInAir.getValue() && !player.isFallFlying()) {
         holdingStill = true;
         this.clicked = false;
         player.setSprinting(false);
         if (player.onGround()) {
            this.holdTicks = Math.max(0, this.stopTicksBefore.getValue().intValue());
            player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
         } else {
            this.holdTicks = 0;
            this.airTicks = 0;
         }
      } else {
         this.performSwapNow(player);
      }
   }

   private void tickFunTimeHold(Minecraft mc, LocalPlayer player) {
      if (player.isFallFlying() || !player.onGround() && !this.stopInAir.getValue()) {
         holdingStill = false;
         if (!this.clicked) {
            this.clicked = true;
            this.performSwap(mc, player);
         }
      } else {
         player.setSprinting(false);
         if (this.clicked) {
            if (player.onGround()) {
               player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            }

            if (--this.holdTicks <= 0) {
               holdingStill = false;
               this.clicked = false;
            }
         } else {
            if (player.onGround()) {
               player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
               if (this.holdTicks > 0) {
                  this.holdTicks--;
                  return;
               }
            } else {
               this.airTicks++;
               Vec3 delta = player.getDeltaMovement();
               boolean decayed = Math.hypot(delta.x, delta.z) <= this.airStopSpeed.getValue();
               boolean timedOut = this.airTicks >= this.maxAirStopTicks.getValue().intValue();
               if (!decayed && !timedOut) {
                  return;
               }
            }

            this.clicked = true;
            this.performSwap(mc, player);
            this.holdTicks = Math.max(0, this.stopTicksAfter.getValue().intValue());
         }
      }
   }

   private void performSwapNow(LocalPlayer player) {
      Minecraft mc = Minecraft.getInstance();
      if (player.isFallFlying()) {
         player.setSprinting(false);
      }

      this.performSwap(mc, player);
   }

   private void beginGrimHold(LocalPlayer player) {
      if (!player.onGround()) {
         this.performSwapNow(player);
      } else {
         grimHold = true;
         this.clicked = false;
         this.grimTicks = 0;
         player.setSprinting(false);
      }
   }

   private void tickGrimHold(Minecraft mc, LocalPlayer player) {
      if (!player.onGround()) {
         grimHold = false;
         if (!this.clicked) {
            this.clicked = true;
            this.performSwap(mc, player);
         }
      } else {
         player.setSprinting(false);
         this.grimTicks++;
         if (!this.clicked) {
            Vec3 delta = player.getDeltaMovement();
            double horizontalSpeed = Math.hypot(delta.x, delta.z);
            boolean stopped = horizontalSpeed <= this.stopSpeed.getValue();
            boolean timedOut = this.grimTicks >= this.maxStopTicks.getValue().intValue();
            if (stopped || timedOut) {
               this.clicked = true;
               this.performSwap(mc, player);
               this.holdTicks = Math.max(0, this.stopTicksAfter.getValue().intValue());
            }
         } else {
            if (--this.holdTicks <= 0) {
               grimHold = false;
               this.clicked = false;
               this.grimTicks = 0;
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      this.wasHeld = false;
      this.pendingAt = 0L;
      holdingStill = false;
      grimHold = false;
      this.holdTicks = 0;
      this.clicked = false;
      this.airTicks = 0;
      this.grimTicks = 0;
   }

   private void updateHeld(int action) {
      boolean pressed = action != 0;
      if (pressed && !this.wasHeld && this.pendingAt == 0L && !holdingStill && !grimHold) {
         this.pendingAt = System.currentTimeMillis() + this.delayBeforeSwap();
      }

      this.wasHeld = pressed;
   }

   private void performSwap(Minecraft mc, LocalPlayer player) {
      Item target = player.getOffhandItem().is(this.resolve(this.first.getValue()))
         ? this.resolve(this.second.getValue())
         : this.resolve(this.first.getValue());
      int slot = this.findSlot(player, target);
      if (slot >= 0) {
         boolean custom = this.custom();
         if (custom && this.stopSprint.getValue()) {
            player.setSprinting(false);
         }

         if (custom && this.stopMovement.getValue()) {
            player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
         }

         int inventorySlot = slot >= 36 ? slot - 36 : slot;
         ItemStack swapped = player.getInventory().getItem(inventorySlot);
         Component itemName = swapped.isEmpty() ? target.getName(target.getDefaultInstance()) : swapped.getHoverName();
         mc.gameMode.handleContainerInput(player.containerMenu.containerId, slot, 40, ContainerInput.SWAP, player);
         NotificationsElement.notify(NotificationsElement.NotificationType.SUCCESS, "Swapped to ", itemName);
         if (custom && this.closeInventory.getValue() && mc.gui.screen() != null) {
            mc.gui.setScreen(null);
         }
      }
   }

   private long delayBeforeSwap() {
      return switch ((String)this.mode.getValue()) {
         case "Instant" -> 0L;
         case "Legit" -> 75L;
         case "FunTime", "Grim" -> 0L;
         default -> Math.round((this.preSwapMin.getValue() + this.preSwapMax.getValue()) * 0.5);
      };
   }

   private boolean custom() {
      return this.mode.is("Custom");
   }

   private boolean funTime() {
      return this.mode.is("FunTime");
   }

   private boolean grim() {
      return this.mode.is("Grim");
   }

   private int findSlot(LocalPlayer player, Item item) {
      for (int i = 0; i < 36; i++) {
         if (player.getInventory().getItem(i).is(item)) {
            return i < 9 ? i + 36 : i;
         }
      }

      return -1;
   }

   private Item resolve(String name) {
      return switch (name) {
         case "Player Head" -> Items.PLAYER_HEAD;
         case "Golden Apple" -> Items.GOLDEN_APPLE;
         case "Shield" -> Items.SHIELD;
         default -> Items.TOTEM_OF_UNDYING;
      };
   }
}
