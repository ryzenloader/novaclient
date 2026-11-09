package win.furryhook.feature.impl.visual;

import java.util.List;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class ShaderHandsFeature extends Feature {
   public static final String FILL = "Fill";
   public static final String OUTLINE = "Outline";
   public static final String GLOW = "Glow";
   public static final String GLASS = "Glass";
   public static final String SOLID = "Solid";
   public static final String PLASMA = "Plasma";
   public static final String NEBULA = "Nebula";
   public static final String PRISM = "Prism";
   public static final String EMBERS = "Embers";
   public static final String FIRE = "Fire";
   public static final String ELECTRIC = "Electric";
   public final MultiSelectSetting effects = this.register(
      new MultiSelectSetting("Effects", List.of("Fill", "Glow"), "Fill", "Outline", "Glow").configKey("render.armtweaks.fill.effects")
   );
   public final ModeSetting colorMode = this.register(ColorMode.setting().configKey("render.armtweaks.fill.colorMode"));
   public final ColorSetting color = this.register(
      new ColorSetting("Color", 7964363).configKey("render.armtweaks.fill.color").visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );
   public final ModeSetting fillType = this.register(
      new ModeSetting("Fill Type", "Glass", "Glass", "Solid", "Plasma", "Nebula", "Prism", "Embers")
         .configKey("render.armtweaks.fill.type")
         .visibleWhen(this::hasFill)
   );
   public final BooleanSetting bothHands = this.register(new BooleanSetting("Both Hands", true).configKey("render.armtweaks.fill.bothHands"));
   public final BooleanSetting advanced = this.register(new BooleanSetting("Advanced", false).asExpander().configKey("render.armtweaks.advanced"));
   public final NumberSetting fillOpacity = this.register(
      new NumberSetting("Fill Opacity", 0.19, 0.0, 1.0, 0.01, "").configKey("render.armtweaks.fill.opacity").visibleWhen(() -> this.hasFill()).advancedOnly()
   );
   public final NumberSetting plasmaSpeed = this.register(
      new NumberSetting("Fill Speed", 1.0, 0.1, 5.0, 0.1, "x")
         .configKey("render.armtweaks.fill.plasma.speed")
         .visibleWhen(() -> this.hasAnimatedFill())
         .advancedOnly()
   );
   public final NumberSetting glassBlur = this.register(
      new NumberSetting("Glass Blur", 19.0, 0.0, 60.0, 1.0, "px")
         .configKey("render.armtweaks.fill.glass.blur")
         .visibleWhen(() -> this.hasGlass())
         .advancedOnly()
   );
   public final BooleanSetting mirror = this.register(
      new BooleanSetting("Mirror", true).configKey("render.armtweaks.fill.glass.mirror").visibleWhen(() -> this.hasGlass()).advancedOnly()
   );
   public final NumberSetting glowRadius = this.register(
      new NumberSetting("Glow Radius", 18.0, 1.0, 50.0, 1.0, "px")
         .configKey("render.armtweaks.fill.glow.radius")
         .visibleWhen(() -> this.hasGlow())
         .advancedOnly()
   );
   public final NumberSetting glowStrength = this.register(
      new NumberSetting("Glow Strength", 1.0, 0.0, 1.0, 0.01, "")
         .configKey("render.armtweaks.fill.glow.strength")
         .visibleWhen(() -> this.hasGlow())
         .advancedOnly()
   );
   public final BooleanSetting flame = this.register(
      new BooleanSetting("Flame", true).configKey("render.armtweaks.fill.glow.flame").visibleWhen(() -> this.hasGlow()).advancedOnly()
   );
   public final ModeSetting flameStyle = this.register(
      new ModeSetting("Flame Style", "Fire", "Fire", "Electric")
         .configKey("render.armtweaks.fill.glow.flameStyle")
         .visibleWhen(() -> this.hasFlame())
         .advancedOnly()
   );
   public final NumberSetting flameSpeed = this.register(
      new NumberSetting("Flame Speed", 1.1, 0.1, 5.0, 0.1, "x")
         .configKey("render.armtweaks.fill.glow.flameSpeed")
         .visibleWhen(() -> this.hasFlame())
         .advancedOnly()
   );
   public final NumberSetting flameTrail = this.register(
      new NumberSetting("Flame Trail", 1.0, 0.0, 1.0, 0.01, "")
         .configKey("render.armtweaks.fill.glow.flameTrail")
         .visibleWhen(() -> this.hasFlame())
         .advancedOnly()
   );
   public final NumberSetting flameTurbulence = this.register(
      new NumberSetting("Flame Turbulence", 1.0, 0.0, 3.0, 0.05, "x")
         .configKey("render.armtweaks.fill.glow.flameTurbulence")
         .visibleWhen(() -> this.hasFlame())
         .advancedOnly()
   );
   public final NumberSetting flameChaos = this.register(
      new NumberSetting("Flame Chaos", 1.0, 0.0, 2.0, 0.05, "x")
         .configKey("render.armtweaks.fill.glow.flameChaos")
         .visibleWhen(() -> this.hasFlame())
         .advancedOnly()
   );
   public final NumberSetting flameFlicker = this.register(
      new NumberSetting("Flame Flicker", 1.0, 0.0, 2.0, 0.05, "x")
         .configKey("render.armtweaks.fill.glow.flameFlicker")
         .visibleWhen(() -> this.hasFlame())
         .advancedOnly()
   );
   public final NumberSetting flameBrightness = this.register(
      new NumberSetting("Flame Brightness", 1.0, 0.0, 3.0, 0.05, "x")
         .configKey("render.armtweaks.fill.glow.flameBrightness")
         .visibleWhen(() -> this.hasFlame())
         .advancedOnly()
   );
   public final NumberSetting outlineThickness = this.register(
      new NumberSetting("Outline Thickness", 1.5, 0.5, 5.0, 0.1, "px")
         .configKey("render.armtweaks.fill.outline.thickness")
         .visibleWhen(() -> this.hasOutline())
         .advancedOnly()
   );

   public ShaderHandsFeature() {
      super("ShaderHands", "Renders a colored silhouette over your first-person hands", FeatureCategory.VISUAL, -1);
   }

   public int resolvedColor() {
      return ColorMode.resolve(this.colorMode, this.color);
   }

   public boolean hasFill() {
      return this.effects.isSelected("Fill");
   }

   public boolean hasGlass() {
      return this.hasFill() && this.fillType.is("Glass");
   }

   public boolean hasPlasma() {
      return this.hasFill() && this.fillType.is("Plasma");
   }

   public boolean hasAnimatedFill() {
      return this.hasFill() && !this.fillType.is("Solid") && !this.fillType.is("Glass");
   }

   public boolean hasOutline() {
      return this.effects.isSelected("Outline");
   }

   public boolean hasGlow() {
      return this.effects.isSelected("Glow");
   }

   public boolean hasFlame() {
      return this.hasGlow() && this.flame.getValue();
   }

   public boolean isElectric() {
      return this.hasFlame() && this.flameStyle.is("Electric");
   }
}
