package win.furryhook.feature.impl.visual;

import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class AtmosphereFeature extends Feature {
   public static final String STORM_MANUAL = "Manual";
   public static final String STORM_WEATHER = "Weather";
   public static final String RAIN = "Rain";
   public static final String SNOW = "Snow";
   public final BooleanSetting fog = this.register(new BooleanSetting("Fog", true).configKey("render.atmosphere.fog"));
   public final BooleanSetting nightHaze = this.register(new BooleanSetting("Night Haze", false).configKey("render.atmosphere.haze"));
   public final BooleanSetting rain = this.register(new BooleanSetting("Rain", true).configKey("render.atmosphere.rain"));
   public final ModeSetting rainType = this.register(
      new ModeSetting("Precipitation", "Rain", "Rain", "Snow").configKey("render.atmosphere.rain.type").visibleWhen(() -> this.rain.getValue())
   );
   public final BooleanSetting lightning = this.register(new BooleanSetting("Lightning", true).configKey("render.atmosphere.lightning"));
   public final BooleanSetting advanced = this.register(new BooleanSetting("Advanced", false).asExpander().configKey("render.atmosphere.advanced"));
   public final ModeSetting fogColorMode = this.register(
      ColorMode.setting().configKey("render.atmosphere.fog.colorMode").visibleWhen(() -> this.fog.getValue()).advancedOnly()
   );
   public final ColorSetting fogColor = this.register(
      new ColorSetting("Fog Color", -6642232)
         .configKey("render.atmosphere.fog.color")
         .visibleWhen(() -> this.fog.getValue() && ColorMode.isCustom(this.fogColorMode))
         .advancedOnly()
   );
   public final NumberSetting fogDensity = this.register(
      new NumberSetting("Density", 0.55, 0.05, 2.0, 0.01, "").configKey("render.atmosphere.fog.density").visibleWhen(() -> this.fog.getValue()).advancedOnly()
   );
   public final NumberSetting fogHeight = this.register(
      new NumberSetting("Height", 48.0, 8.0, 160.0, 1.0, " blocks")
         .configKey("render.atmosphere.fog.height")
         .visibleWhen(() -> this.fog.getValue())
         .advancedOnly()
   );
   public final NumberSetting ambientStrength = this.register(
      new NumberSetting("Ambient", 0.5, 0.0, 2.0, 0.05, "").configKey("render.atmosphere.fog.ambient").visibleWhen(() -> this.fog.getValue()).advancedOnly()
   );
   public final BooleanSetting syncWithSky = this.register(
      new BooleanSetting("Sync With Sky", true).configKey("render.atmosphere.fog.sync").visibleWhen(() -> this.fog.getValue()).advancedOnly()
   );
   public final NumberSetting skySync = this.register(
      new NumberSetting("Sync Amount", 0.65, 0.0, 1.0, 0.05, "")
         .configKey("render.atmosphere.fog.sync.amount")
         .visibleWhen(() -> this.fog.getValue() && this.syncWithSky.getValue())
         .advancedOnly()
   );
   public final NumberSetting sunsetBoost = this.register(
      new NumberSetting("Sunset Boost", 1.4, 0.0, 3.0, 0.05, "x")
         .configKey("render.atmosphere.fog.sync.sunset")
         .visibleWhen(() -> this.fog.getValue() && this.syncWithSky.getValue())
         .advancedOnly()
   );
   public final NumberSetting turbulence = this.register(
      new NumberSetting("Turbulence", 0.35, 0.0, 1.0, 0.05, "")
         .configKey("render.atmosphere.fog.turbulence")
         .visibleWhen(() -> this.fog.getValue())
         .advancedOnly()
   );
   public final NumberSetting sunScatter = this.register(
      new NumberSetting("Sun Scatter", 0.8, 0.0, 2.0, 0.05, "x")
         .configKey("render.atmosphere.fog.sunscatter")
         .visibleWhen(() -> this.fog.getValue())
         .advancedOnly()
   );
   public final BooleanSetting stormFog = this.register(
      new BooleanSetting("Storm Fog", false).configKey("render.atmosphere.fog.storm").visibleWhen(() -> this.fog.getValue()).advancedOnly()
   );
   public final ModeSetting hazeColorMode = this.register(
      ColorMode.setting().configKey("render.atmosphere.haze.colorMode").visibleWhen(() -> this.nightHaze.getValue()).advancedOnly()
   );
   public final ColorSetting hazeColor = this.register(
      new ColorSetting("Haze Color", -4930336)
         .configKey("render.atmosphere.haze.color")
         .visibleWhen(() -> this.nightHaze.getValue() && ColorMode.isCustom(this.hazeColorMode))
         .advancedOnly()
   );
   public final NumberSetting hazeAmount = this.register(
      new NumberSetting("Haze Amount", 0.6, 0.05, 2.0, 0.05, "")
         .configKey("render.atmosphere.haze.amount")
         .visibleWhen(() -> this.nightHaze.getValue())
         .advancedOnly()
   );
   public final NumberSetting hazeThickness = this.register(
      new NumberSetting("Haze Thickness", 5.0, 1.0, 40.0, 0.5, " blocks")
         .configKey("render.atmosphere.haze.thickness")
         .visibleWhen(() -> this.nightHaze.getValue())
         .advancedOnly()
   );
   public final BooleanSetting hazeFollowGround = this.register(
      new BooleanSetting("Follow Ground", true).configKey("render.atmosphere.haze.follow").visibleWhen(() -> this.nightHaze.getValue()).advancedOnly()
   );
   public final NumberSetting hazeOffset = this.register(
      new NumberSetting("Haze Offset", 1.5, -8.0, 24.0, 0.5, " blocks")
         .configKey("render.atmosphere.haze.offset")
         .visibleWhen(() -> this.nightHaze.getValue() && this.hazeFollowGround.getValue())
         .advancedOnly()
   );
   public final NumberSetting hazeLevel = this.register(
      new NumberSetting("Haze Level", 64.0, -64.0, 320.0, 1.0, " Y")
         .configKey("render.atmosphere.haze.level")
         .visibleWhen(() -> this.nightHaze.getValue() && !this.hazeFollowGround.getValue())
         .advancedOnly()
   );
   public final NumberSetting hazeBanks = this.register(
      new NumberSetting("Haze Banks", 0.6, 0.0, 1.0, 0.05, "")
         .configKey("render.atmosphere.haze.banks")
         .visibleWhen(() -> this.nightHaze.getValue())
         .advancedOnly()
   );
   public final NumberSetting hazeGlow = this.register(
      new NumberSetting("Haze Glow", 0.85, 0.0, 2.0, 0.05, "")
         .configKey("render.atmosphere.haze.glow")
         .visibleWhen(() -> this.nightHaze.getValue())
         .advancedOnly()
   );
   public final NumberSetting hazeMoonScatter = this.register(
      new NumberSetting("Moon Scatter", 1.0, 0.0, 3.0, 0.05, "x")
         .configKey("render.atmosphere.haze.moon")
         .visibleWhen(() -> this.nightHaze.getValue())
         .advancedOnly()
   );
   public final BooleanSetting hazeNightOnly = this.register(
      new BooleanSetting("Night Only", true).configKey("render.atmosphere.haze.nightonly").visibleWhen(() -> this.nightHaze.getValue()).advancedOnly()
   );
   public final NumberSetting rainDensity = this.register(
      new NumberSetting("Rain Density", 1.0, 0.1, 2.0, 0.05, "x")
         .configKey("render.atmosphere.rain.density")
         .visibleWhen(() -> this.rain.getValue())
         .advancedOnly()
   );
   public final NumberSetting rainSpeed = this.register(
      new NumberSetting("Rain Speed", 1.0, 0.25, 3.0, 0.05, "x")
         .configKey("render.atmosphere.rain.speed")
         .visibleWhen(() -> this.rain.getValue())
         .advancedOnly()
   );
   public final ColorSetting rainColor = this.register(
      new ColorSetting("Rain Color", -4009240).configKey("render.atmosphere.rain.color").visibleWhen(() -> this.rain.getValue()).advancedOnly()
   );
   public final NumberSetting rainOpacity = this.register(
      new NumberSetting("Rain Opacity", 0.55, 0.1, 1.0, 0.05, "")
         .configKey("render.atmosphere.rain.opacity")
         .visibleWhen(() -> this.rain.getValue())
         .advancedOnly()
   );
   public final BooleanSetting ripples = this.register(
      new BooleanSetting("Ripples", true)
         .configKey("render.atmosphere.rain.ripples")
         .visibleWhen(() -> this.rain.getValue() && !this.usesSnow())
         .advancedOnly()
   );
   public final NumberSetting rippleStrength = this.register(
      new NumberSetting("Ripple Strength", 0.5, 0.1, 1.5, 0.05, "x")
         .configKey("render.atmosphere.rain.ripples.strength")
         .visibleWhen(() -> this.rain.getValue() && !this.usesSnow() && this.ripples.getValue())
         .advancedOnly()
   );
   public final ModeSetting stormSource = this.register(
      new ModeSetting("Storm Source", "Manual", "Manual", "Weather")
         .configKey("render.atmosphere.storm.source")
         .visibleWhen(() -> this.lightning.getValue() || this.fog.getValue() && this.stormFog.getValue())
         .advancedOnly()
   );
   public final NumberSetting stormPower = this.register(
      new NumberSetting("Storm Power", 0.5, 0.0, 1.0, 0.05, "")
         .configKey("render.atmosphere.storm.power")
         .visibleWhen(() -> "Manual".equals(this.stormSource.getValue()) && (this.lightning.getValue() || this.fog.getValue() && this.stormFog.getValue()))
         .advancedOnly()
   );
   public final NumberSetting strikeRate = this.register(
      new NumberSetting("Strike Rate", 1.0, 0.25, 4.0, 0.05, "x")
         .configKey("render.atmosphere.lightning.rate")
         .visibleWhen(() -> this.lightning.getValue())
         .advancedOnly()
   );
   public final NumberSetting strikeDistance = this.register(
      new NumberSetting("Strike Distance", 220.0, 60.0, 500.0, 10.0, " blocks")
         .configKey("render.atmosphere.lightning.distance")
         .visibleWhen(() -> this.lightning.getValue())
         .advancedOnly()
   );
   public final ColorSetting boltColor = this.register(
      new ColorSetting("Bolt Color", -3416833).configKey("render.atmosphere.lightning.color").visibleWhen(() -> this.lightning.getValue()).advancedOnly()
   );
   public final NumberSetting boltWidth = this.register(
      new NumberSetting("Bolt Width", 1.6, 0.5, 5.0, 0.1, " px")
         .configKey("render.atmosphere.lightning.width")
         .visibleWhen(() -> this.lightning.getValue())
         .advancedOnly()
   );
   public final NumberSetting boltBranches = this.register(
      new NumberSetting("Branches", 3.0, 0.0, 4.0, 1.0, "")
         .configKey("render.atmosphere.lightning.branches")
         .visibleWhen(() -> this.lightning.getValue())
         .advancedOnly()
   );
   public final NumberSetting flashStrength = this.register(
      new NumberSetting("Flash", 1.0, 0.0, 2.0, 0.05, "x")
         .configKey("render.atmosphere.lightning.flash")
         .visibleWhen(() -> this.lightning.getValue())
         .advancedOnly()
   );

   public AtmosphereFeature() {
      super("Atmosphere", "Volumetric fog, rain and shader lightning on one storm driver", FeatureCategory.VISUAL, -1);
   }

   public static AtmosphereFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(AtmosphereFeature.class);
   }

   public boolean usesFog() {
      return this.fog.getValue();
   }

   public boolean usesLightning() {
      return this.lightning.getValue();
   }

   public boolean usesRain() {
      return this.rain.getValue();
   }

   public boolean usesSnow() {
      return "Snow".equals(this.rainType.getValue());
   }

   public boolean usesNightHaze() {
      return this.nightHaze.getValue();
   }

   public boolean usesAtmosphere() {
      return this.usesFog() || this.usesLightning() || this.usesRain() || this.usesNightHaze();
   }
}
