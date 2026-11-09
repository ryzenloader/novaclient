package win.furryhook.feature.impl.visual;

import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class AnimationsFeature extends Feature {
   public final BooleanSetting containerAnimation = this.register(
      new BooleanSetting("Container Animation", true).configKey("render.animations.containerAnimation")
   );
   public final BooleanSetting chatAnimation = this.register(new BooleanSetting("Chat Animation", true).configKey("render.animations.chatAnimation"));
   public final NumberSetting chatAnimationSpeed = this.register(
      new NumberSetting("Chat Animation Speed", 1.0, 0.3, 3.0, 0.1, "x")
         .configKey("render.animations.chatAnimationSpeed")
         .visibleWhen(() -> this.chatAnimation.getValue())
   );

   public AnimationsFeature() {
      super("Animations", "Animated container and chat reveal transitions", FeatureCategory.VISUAL, -1);
   }

   public static AnimationsFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(AnimationsFeature.class);
   }

   public static boolean usesContainerAnimation() {
      AnimationsFeature feature = getEnabled();
      return feature != null && feature.containerAnimation.getValue();
   }

   public static boolean usesChatAnimation() {
      AnimationsFeature feature = getEnabled();
      return feature != null && feature.chatAnimation.getValue();
   }

   public static float chatAnimationDurationNanos() {
      AnimationsFeature feature = getEnabled();
      float speed = feature != null ? feature.chatAnimationSpeed.getValue().floatValue() : 1.0F;
      return 2.2E8F / Math.max(0.05F, speed);
   }
}
