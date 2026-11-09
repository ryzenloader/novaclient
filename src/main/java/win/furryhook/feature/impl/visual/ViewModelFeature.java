package win.furryhook.feature.impl.visual;

import net.minecraft.world.InteractionHand;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.ButtonSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class ViewModelFeature extends Feature {
   public final NumberSetting mainHandX = this.register(new NumberSetting("Main Hand X", 0.0, -1.0, 1.0, 0.01, ""));
   public final NumberSetting mainHandY = this.register(new NumberSetting("Main Hand Y", 0.0, -1.0, 1.0, 0.01, ""));
   public final NumberSetting mainHandZ = this.register(new NumberSetting("Main Hand Z", 0.0, -2.5, 2.5, 0.01, ""));
   public final NumberSetting offHandX = this.register(new NumberSetting("Off Hand X", 0.0, -1.0, 1.0, 0.01, ""));
   public final NumberSetting offHandY = this.register(new NumberSetting("Off Hand Y", 0.0, -1.0, 1.0, 0.01, ""));
   public final NumberSetting offHandZ = this.register(new NumberSetting("Off Hand Z", 0.0, -2.5, 2.5, 0.01, ""));
   public final ButtonSetting resetPosition = this.register(new ButtonSetting("Reset Position", "Reset", this::resetPosition));

   public ViewModelFeature() {
      super("ViewModel", "Adjusts first person hand positions", FeatureCategory.VISUAL, -1);
   }

   public float offsetX(InteractionHand hand) {
      return value(hand == InteractionHand.MAIN_HAND ? this.mainHandX : this.offHandX);
   }

   public float offsetY(InteractionHand hand) {
      return value(hand == InteractionHand.MAIN_HAND ? this.mainHandY : this.offHandY);
   }

   public float offsetZ(InteractionHand hand) {
      return value(hand == InteractionHand.MAIN_HAND ? this.mainHandZ : this.offHandZ);
   }

   private static float value(NumberSetting setting) {
      return setting.getValue().floatValue();
   }

   private void resetPosition() {
      this.mainHandX.reset();
      this.mainHandY.reset();
      this.mainHandZ.reset();
      this.offHandX.reset();
      this.offHandY.reset();
      this.offHandZ.reset();
   }
}
