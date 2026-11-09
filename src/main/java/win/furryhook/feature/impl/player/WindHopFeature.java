package win.furryhook.feature.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.InputGuard;
import win.furryhook.utils.inventory.InventorySwap;
import win.furryhook.utils.inventory.InventoryUtil;

public final class WindHopFeature extends Feature {
   private static final int GLFW_RELEASE = 0;
   private static final int GLFW_PRESS = 1;
   public final InputBindSetting key = this.register(new InputBindSetting("Key", -1));
   public final BooleanSetting auto = this.register(new BooleanSetting("Auto", false));
   public final NumberSetting delay = this.register(new NumberSetting("Delay", 4.0, 1.0, 20.0, 1.0, " ticks"));
   private boolean held;
   private boolean pending;
   private int cooldown;

   public WindHopFeature() {
      super("WindHop", "Uses a wind charge for height on key press", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 0 || !InputGuard.isBindingBlocked()) {
         if (this.key.matches(event.getKey())) {
            this.updateHeld(event.getAction());
         }
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 0 || !InputGuard.isBindingBlocked()) {
         if (this.key.matchesMouse(event.getButton())) {
            this.updateHeld(event.getAction());
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.cooldown > 0) {
         this.cooldown--;
      }

      boolean trigger = this.auto.getValue() ? this.held : this.pending;
      if (trigger) {
         this.pending = false;
         this.useWindCharge();
      }
   }

   @Override
   protected void onDisable() {
      this.held = false;
      this.pending = false;
      this.cooldown = 0;
   }

   private void updateHeld(int action) {
      if (action == 1) {
         this.held = true;
         this.pending = true;
      } else if (action == 0) {
         this.held = false;
      }
   }

   private void useWindCharge() {
      if (this.cooldown <= 0 && !InventorySwap.isBusy()) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            int slot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.is(Items.WIND_CHARGE));
            if (slot >= 0) {
               InventorySwap.useFromSlot(slot);
               this.cooldown = Math.max(1, Math.round(this.delay.getValue().floatValue()));
            }
         }
      }
   }
}
