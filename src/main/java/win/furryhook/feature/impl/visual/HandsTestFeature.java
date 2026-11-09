package win.furryhook.feature.impl.visual;

import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class HandsTestFeature extends Feature {
   public final ModeSetting colorMode = this.register(ColorMode.setting().configKey("render.handstest.colorMode"));
   public final ColorSetting color = this.register(
      new ColorSetting("Color", 6697983).configKey("render.handstest.color").visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );
   public final NumberSetting intensity = this.register(new NumberSetting("Intensity", 0.85, 0.1, 1.5, 0.01, "").configKey("render.handstest.intensity"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 1.15, 0.2, 3.0, 0.05, "").configKey("render.handstest.speed"));
   public final NumberSetting length = this.register(new NumberSetting("Length", 0.72, 0.1, 1.0, 0.05, "").configKey("render.handstest.length"));
   public final NumberSetting trailFade = this.register(new NumberSetting("Trail Fade", 0.91, 0.55, 0.98, 0.01, "").configKey("render.handstest.trailFade"));
   public final NumberSetting trailSoftness = this.register(
      new NumberSetting("Trail Softness", 1.35, 0.45, 2.5, 0.05, "").configKey("render.handstest.trailSoftness")
   );
   public final NumberSetting trailBlur = this.register(new NumberSetting("Trail Blur", 1.55, 0.2, 3.0, 0.05, "").configKey("render.handstest.trailBlur"));
   public final NumberSetting handSoftness = this.register(
      new NumberSetting("Hand Softness", 1.3, 0.45, 2.5, 0.05, "").configKey("render.handstest.handSoftness")
   );
   public final NumberSetting handBlur = this.register(new NumberSetting("Hand Blur", 1.45, 0.2, 3.0, 0.05, "").configKey("render.handstest.handBlur"));
   public final NumberSetting smoke = this.register(new NumberSetting("Smoke", 0.55, 0.0, 0.8, 0.05, "").configKey("render.handstest.smoke"));

   public HandsTestFeature() {
      super("HandsTest", "Experimental fire/smoke hand trail (test module)", FeatureCategory.VISUAL, -1);
   }

   public int resolvedColor() {
      return ColorMode.resolve(this.colorMode, this.color);
   }
}
