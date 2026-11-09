package win.furryhook.feature.impl.visual;

import win.furryhook.context.MinecraftContext;
import win.furryhook.emote.EmoteWheel;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.utils.InputGuard;

public final class EmotionsFeature extends Feature implements MinecraftContext {
   public final InputBindSetting wheelBind = this.register(new InputBindSetting("Wheel Bind", -1));

   public EmotionsFeature() {
      super("Emotions", "Radial emote wheel", FeatureCategory.VISUAL, -1);
   }

   @Override
   protected void onDisable() {
      if (EmoteWheel.isOpen()) {
         EmoteWheel.requestCancel();
         EmoteWheel.close(mc);
      }
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (this.wheelBind.matches(event.getKey()) && !InputGuard.isBindingBlocked()) {
         this.handleBindAction(event.getAction());
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (this.wheelBind.matchesMouse(event.getButton()) && !InputGuard.isBindingBlocked()) {
         this.handleBindAction(event.getAction());
      }
   }

   private void handleBindAction(int action) {
      if (action == 1) {
         EmoteWheel.open();
      } else if (action == 0) {
         EmoteWheel.close(mc);
      }
   }
}
