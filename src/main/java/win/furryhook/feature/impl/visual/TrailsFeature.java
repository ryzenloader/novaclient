package win.furryhook.feature.impl.visual;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.post.EmissiveBloom;
import win.furryhook.utils.render.post.PostFx;
import win.furryhook.utils.render.world.GlowStripRenderer;

public final class TrailsFeature extends Feature implements MinecraftContext {
   private static final int RIBBONS = 3;
   private static final int SUBDIVISIONS = 4;
   private static final double MIN_STEP_SQR = 4.0E-4;
   private static final double TELEPORT_DISTANCE_SQR = 64.0;
   private static final float TAIL_WIDTH = 0.18F;
   private static final double TWO_PI = Math.PI * 2;
   public final NumberSetting length = this.register(new NumberSetting("Length", 30.0, 6.0, 90.0, 1.0, " points").configKey("render.trails.length"));
   public final NumberSetting width = this.register(new NumberSetting("Width", 0.12, 0.02, 0.6, 0.01, " blocks").configKey("render.trails.width"));
   public final NumberSetting spread = this.register(new NumberSetting("Spread", 0.25, 0.0, 1.5, 0.05, " blocks").configKey("render.trails.spread"));
   public final NumberSetting height = this.register(new NumberSetting("Height", 1.0, 0.0, 2.5, 0.05, " blocks").configKey("render.trails.height"));
   public final BooleanSetting thirdPersonOnly = this.register(new BooleanSetting("Third Person Only", true).configKey("render.trails.thirdperson"));
   public final ModeSetting colorMode = this.register(ColorMode.setting().configKey("render.trails.colormode"));
   public final ColorSetting color = this.register(
      new ColorSetting("Color", -9707009).configKey("render.trails.color").visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );
   public final BooleanSetting advanced = this.register(new BooleanSetting("Advanced", false).asExpander().configKey("render.trails.advanced"));
   public final NumberSetting wave = this.register(new NumberSetting("Wave", 0.18, 0.0, 1.0, 0.01, " blocks").configKey("render.trails.wave").advancedOnly());
   public final NumberSetting waveSpeed = this.register(
      new NumberSetting("Wave Speed", 1.5, 0.0, 6.0, 0.1, "").configKey("render.trails.wavespeed").advancedOnly()
   );
   public final NumberSetting waveDensity = this.register(
      new NumberSetting("Wave Density", 0.35, 0.05, 1.5, 0.05, "").configKey("render.trails.wavedensity").advancedOnly()
   );
   public final NumberSetting glow = this.register(new NumberSetting("Glow", 1.0, 0.0, 2.5, 0.05, "").configKey("render.trails.glow").advancedOnly());
   public final NumberSetting coreWidth = this.register(new NumberSetting("Core", 0.35, 0.05, 1.0, 0.05, "").configKey("render.trails.core").advancedOnly());
   public final NumberSetting flowSpeed = this.register(
      new NumberSetting("Flow Speed", 1.2, 0.0, 5.0, 0.1, "").configKey("render.trails.flowspeed").advancedOnly()
   );
   public final NumberSetting chromatic = this.register(
      new NumberSetting("Chromatic", 0.4, 0.0, 1.0, 0.05, "").configKey("render.trails.chromatic").advancedOnly()
   );
   public final BooleanSetting bloom = this.register(new BooleanSetting("Bloom", true).configKey("render.trails.bloom").advancedOnly());
   public final NumberSetting bloomIntensity = this.register(
      new NumberSetting("Bloom Intensity", 1.0, 0.0, 3.0, 0.05, "").configKey("render.trails.bloomintensity").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting bloomRadius = this.register(
      new NumberSetting("Bloom Radius", 3.0, 1.0, 4.0, 1.0, "").configKey("render.trails.bloomradius").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting hdrBoost = this.register(
      new NumberSetting("HDR Boost", 2.0, 0.5, 6.0, 0.1, "").configKey("render.trails.hdrboost").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting anamorphic = this.register(
      new NumberSetting("Anamorphic", 0.35, 0.0, 1.0, 0.05, "").configKey("render.trails.anamorphic").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   private final List<Vec3> history = new ArrayList<>();
   private final List<GlowStripRenderer.Strip> strips = new ArrayList<>(3);
   private final GlowStripRenderer renderer = new GlowStripRenderer("furryhook-trails");

   public TrailsFeature() {
      super("Trails", "Three glowing ribbons trailing behind the player", FeatureCategory.VISUAL, -1);
   }

   public static TrailsFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(TrailsFeature.class);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (mc.level != null && mc.player != null) {
         Vec3 head = mc.player.position().add(0.0, this.height.getValue(), 0.0);
         if (!this.history.isEmpty()) {
            Vec3 last = this.history.getLast();
            if (last.distanceToSqr(head) > 64.0) {
               this.history.clear();
            }
         }

         if (this.history.isEmpty() || this.history.getLast().distanceToSqr(head) > 4.0E-4) {
            this.history.add(head);
         } else if (!this.history.isEmpty()) {
            this.history.removeFirst();
         }

         int maximum = this.length.getValue().intValue();

         while (this.history.size() > maximum) {
            this.history.removeFirst();
         }
      } else {
         this.history.clear();
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.history.clear();
   }

   @Override
   protected void onDisable() {
      this.history.clear();
      this.strips.clear();
      this.renderer.release();
   }

   public void renderWorld(float tickDelta) {
      if (mc.player != null && mc.level != null && this.history.size() >= 2) {
         if (!this.thirdPersonOnly.getValue() || !mc.options.getCameraType().isFirstPerson()) {
            List<Vec3> path = GlowStripRenderer.smooth(this.buildSpine(tickDelta), 4);
            if (path.size() >= 2) {
               int baseColor = ColorMode.resolve(this.colorMode, this.color);
               float time = PostFx.shaderTime();
               float baseWidth = this.width.getValue().floatValue();
               double spreadDistance = this.spread.getValue();
               double waveAmplitude = this.wave.getValue();
               double waveFrequency = this.waveDensity.getValue();
               double wavePhase = time * this.waveSpeed.getValue();
               this.strips.clear();

               for (int ribbon = 0; ribbon < 3; ribbon++) {
                  double offset = (ribbon - 1) * spreadDistance;
                  double ribbonPhase = ribbon * (Math.PI * 2) / 3.0;
                  List<GlowStripRenderer.Point> points = new ArrayList<>(path.size());

                  for (int index = 0; index < path.size(); index++) {
                     double progress = (double)index / (path.size() - 1);
                     Vec3 side = sideVector(path, index);
                     Vec3 position = path.get(index);
                     double phase = index * waveFrequency + wavePhase + ribbonPhase;
                     double lateral = offset + Math.sin(phase) * waveAmplitude;
                     double vertical = Math.cos(phase) * waveAmplitude * 0.6;
                     points.add(
                        new GlowStripRenderer.Point(
                           position.add(side.scale(lateral)).add(0.0, vertical, 0.0),
                           baseWidth * taper(progress),
                           ColorUtil.multiplyAlpha(baseColor, fade(progress))
                        )
                     );
                  }

                  this.strips.add(new GlowStripRenderer.Strip(points));
               }

               this.renderer
                  .render(
                     this.strips,
                     new GlowStripRenderer.Style(
                        this.glow.getValue().floatValue(),
                        this.coreWidth.getValue().floatValue(),
                        this.chromatic.getValue().floatValue(),
                        this.flowSpeed.getValue().floatValue(),
                        0.0035F,
                        this.hdrBoost.getValue().floatValue()
                     ),
                     this.currentBloom()
                  );
            }
         }
      }
   }

   private EmissiveBloom.Settings currentBloom() {
      return !this.bloom.getValue()
         ? EmissiveBloom.Settings.OFF
         : new EmissiveBloom.Settings(
            true,
            this.bloomIntensity.getValue().floatValue(),
            this.bloomRadius.getValue().intValue(),
            this.hdrBoost.getValue().floatValue(),
            this.anamorphic.getValue().floatValue()
         );
   }

   private List<Vec3> buildSpine(float tickDelta) {
      List<Vec3> spine = new ArrayList<>(this.history.size() + 1);
      spine.addAll(this.history);
      Vec3 head = Render3DUtil.interpolatedPosition(mc.player, tickDelta).add(0.0, this.height.getValue(), 0.0);
      if (spine.getLast().distanceToSqr(head) > 1.0E-6) {
         spine.add(head);
      }

      return spine;
   }

   private static Vec3 sideVector(List<Vec3> path, int index) {
      Vec3 previous = path.get(Math.max(0, index - 1));
      Vec3 next = path.get(Math.min(path.size() - 1, index + 1));
      double dx = next.x - previous.x;
      double dz = next.z - previous.z;
      double length = Math.sqrt(dx * dx + dz * dz);
      return length < 1.0E-5 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-dz / length, 0.0, dx / length);
   }

   private static float taper(double progress) {
      return (float)(0.18F + 0.82F * progress);
   }

   private static float fade(double progress) {
      float tail = (float)Math.clamp(progress * 1.6, 0.0, 1.0);
      float head = (float)Math.clamp((1.0 - progress) * 12.0, 0.0, 1.0);
      return tail * head;
   }
}
