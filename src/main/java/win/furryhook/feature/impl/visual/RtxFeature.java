package win.furryhook.feature.impl.visual;

import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class RtxFeature extends Feature {
   public static final String SOURCE_AUTO = "Auto";
   public static final String SOURCE_MANUAL = "Manual";
   public final BooleanSetting rays = this.register(new BooleanSetting("Rays", true).configKey("render.rtx.rays"));
   public final BooleanSetting reflections = this.register(new BooleanSetting("Wet Floor", true).configKey("render.rtx.reflections"));
   public final BooleanSetting advanced = this.register(new BooleanSetting("Advanced", false).asExpander().configKey("render.rtx.advanced"));
   public final BooleanSetting moonRays = this.register(
      new BooleanSetting("Moon Rays", true).configKey("render.rtx.rays.moon").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final ColorSetting rayColor = this.register(
      new ColorSetting("Ray Color", -3892).configKey("render.rtx.rays.color").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final NumberSetting rayTint = this.register(
      new NumberSetting("Ray Tint", 0.35, 0.0, 1.0, 0.05, "").configKey("render.rtx.rays.tint").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final NumberSetting rayStrength = this.register(
      new NumberSetting("Ray Strength", 1.0, 0.0, 3.0, 0.05, "x").configKey("render.rtx.rays.strength").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final NumberSetting rayDensity = this.register(
      new NumberSetting("Ray Length", 0.85, 0.2, 1.6, 0.05, "").configKey("render.rtx.rays.density").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final NumberSetting rayDecay = this.register(
      new NumberSetting("Ray Decay", 0.96, 0.85, 0.995, 0.005, "").configKey("render.rtx.rays.decay").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final NumberSetting raySpread = this.register(
      new NumberSetting("Ray Spread", 0.6, 0.2, 2.0, 0.05, "").configKey("render.rtx.rays.spread").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final NumberSetting raySamples = this.register(
      new NumberSetting("Ray Samples", 40.0, 12.0, 64.0, 4.0, "").configKey("render.rtx.rays.samples").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final NumberSetting rayEmissive = this.register(
      new NumberSetting("Emissive Rays", 0.3, 0.0, 1.0, 0.05, "").configKey("render.rtx.rays.emissive").visibleWhen(() -> this.rays.getValue()).advancedOnly()
   );
   public final ModeSetting wetSource = this.register(
      new ModeSetting("Wet Source", "Auto", "Auto", "Manual")
         .configKey("render.rtx.reflections.source")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting wetness = this.register(
      new NumberSetting("Wetness", 0.75, 0.0, 1.0, 0.05, "")
         .configKey("render.rtx.reflections.wetness")
         .visibleWhen(() -> this.reflections.getValue() && "Manual".equals(this.wetSource.getValue()))
         .advancedOnly()
   );
   public final NumberSetting puddles = this.register(
      new NumberSetting("Puddles", 0.55, 0.0, 1.0, 0.05, "")
         .configKey("render.rtx.reflections.puddles")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting puddleScale = this.register(
      new NumberSetting("Puddle Size", 3.5, 1.0, 12.0, 0.5, " blocks")
         .configKey("render.rtx.reflections.puddles.size")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting reflectStrength = this.register(
      new NumberSetting("Reflect Strength", 1.0, 0.0, 2.0, 0.05, "x")
         .configKey("render.rtx.reflections.strength")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting fresnel = this.register(
      new NumberSetting("Fresnel", 1.0, 0.0, 1.0, 0.05, "")
         .configKey("render.rtx.reflections.fresnel")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting sharpness = this.register(
      new NumberSetting("Sharpness", 0.7, 0.0, 1.0, 0.05, "")
         .configKey("render.rtx.reflections.sharpness")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting darkening = this.register(
      new NumberSetting("Darkening", 0.35, 0.0, 0.8, 0.05, "")
         .configKey("render.rtx.reflections.darkening")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting surfaceAngle = this.register(
      new NumberSetting("Surface Angle", 0.86, 0.5, 0.98, 0.01, "")
         .configKey("render.rtx.reflections.angle")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final BooleanSetting reflectRipples = this.register(
      new BooleanSetting("Ripples", true).configKey("render.rtx.reflections.ripples").visibleWhen(() -> this.reflections.getValue()).advancedOnly()
   );
   public final NumberSetting rippleAmount = this.register(
      new NumberSetting("Ripple Amount", 0.6, 0.0, 2.0, 0.05, "x")
         .configKey("render.rtx.reflections.ripples.amount")
         .visibleWhen(() -> this.reflections.getValue() && this.reflectRipples.getValue())
         .advancedOnly()
   );
   public final NumberSetting waveAmount = this.register(
      new NumberSetting("Wave Amount", 0.35, 0.0, 2.0, 0.05, "x")
         .configKey("render.rtx.reflections.waves")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting reflectDistance = this.register(
      new NumberSetting("Reflect Distance", 28.0, 6.0, 96.0, 2.0, " blocks")
         .configKey("render.rtx.reflections.distance")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting reflectSteps = this.register(
      new NumberSetting("Reflect Steps", 32.0, 8.0, 64.0, 4.0, "")
         .configKey("render.rtx.reflections.steps")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );
   public final NumberSetting reflectThickness = this.register(
      new NumberSetting("Thickness", 0.7, 0.1, 4.0, 0.1, " blocks")
         .configKey("render.rtx.reflections.thickness")
         .visibleWhen(() -> this.reflections.getValue())
         .advancedOnly()
   );

   public RtxFeature() {
      super("RTX", "Volumetric light shafts and wet mirror floors on one screen-space pass", FeatureCategory.VISUAL, -1);
   }

   public static RtxFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(RtxFeature.class);
   }

   public boolean usesRays() {
      return this.rays.getValue();
   }

   public boolean usesReflections() {
      return this.reflections.getValue();
   }

   public boolean usesManualWetness() {
      return "Manual".equals(this.wetSource.getValue());
   }
}
