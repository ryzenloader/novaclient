package win.furryhook.feature.impl.visual;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
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
import win.furryhook.utils.render.post.EmissiveBloom;
import win.furryhook.utils.render.world.GlowStripRenderer;

public final class LineGlyphesFeature extends Feature implements MinecraftContext {
   private static final int SUBDIVISIONS = 3;
   private static final int SPAWN_ATTEMPTS = 4;
   private static final float TAIL_FADE = 0.3F;
   private static final float FADE_OUT_START = 0.75F;
   private static final double TWO_PI = Math.PI * 2;
   public final NumberSetting count = this.register(new NumberSetting("Count", 8.0, 1.0, 32.0, 1.0, "").configKey("render.lineglyphes.count"));
   public final NumberSetting radius = this.register(new NumberSetting("Radius", 16.0, 3.0, 48.0, 1.0, " blocks").configKey("render.lineglyphes.radius"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 0.25, 0.05, 1.0, 0.05, "").configKey("render.lineglyphes.speed"));
   public final NumberSetting climb = this.register(new NumberSetting("Climb", 0.35, 0.0, 1.5, 0.05, "").configKey("render.lineglyphes.climb"));
   public final NumberSetting length = this.register(new NumberSetting("Length", 40.0, 6.0, 120.0, 1.0, " points").configKey("render.lineglyphes.length"));
   public final ModeSetting colorMode = this.register(ColorMode.setting().configKey("render.lineglyphes.colormode"));
   public final ColorSetting color = this.register(
      new ColorSetting("Color", -5210881).configKey("render.lineglyphes.color").visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );
   public final BooleanSetting advanced = this.register(new BooleanSetting("Advanced", false).asExpander().configKey("render.lineglyphes.advanced"));
   public final NumberSetting wiggle = this.register(new NumberSetting("Wiggle", 0.5, 0.0, 2.0, 0.05, "").configKey("render.lineglyphes.wiggle").advancedOnly());
   public final NumberSetting wiggleSpeed = this.register(
      new NumberSetting("Wiggle Speed", 0.35, 0.05, 1.5, 0.05, "").configKey("render.lineglyphes.wigglespeed").advancedOnly()
   );
   public final NumberSetting lifetime = this.register(
      new NumberSetting("Lifetime", 6000.0, 800.0, 20000.0, 100.0, " ms").configKey("render.lineglyphes.lifetime").advancedOnly()
   );
   public final NumberSetting width = this.register(
      new NumberSetting("Line Width", 0.06, 0.01, 0.4, 0.01, " blocks").configKey("render.lineglyphes.width").advancedOnly()
   );
   public final NumberSetting glow = this.register(new NumberSetting("Glow", 1.0, 0.0, 2.5, 0.05, "").configKey("render.lineglyphes.glow").advancedOnly());
   public final NumberSetting coreWidth = this.register(new NumberSetting("Core", 0.3, 0.05, 1.0, 0.05, "").configKey("render.lineglyphes.core").advancedOnly());
   public final NumberSetting flowSpeed = this.register(
      new NumberSetting("Flow Speed", 0.8, 0.0, 5.0, 0.1, "").configKey("render.lineglyphes.flowspeed").advancedOnly()
   );
   public final NumberSetting chromatic = this.register(
      new NumberSetting("Chromatic", 0.5, 0.0, 1.0, 0.05, "").configKey("render.lineglyphes.chromatic").advancedOnly()
   );
   public final BooleanSetting bloom = this.register(new BooleanSetting("Bloom", true).configKey("render.lineglyphes.bloom").advancedOnly());
   public final NumberSetting bloomIntensity = this.register(
      new NumberSetting("Bloom Intensity", 1.2, 0.0, 3.0, 0.05, "")
         .configKey("render.lineglyphes.bloomintensity")
         .visibleWhen(this.bloom::getValue)
         .advancedOnly()
   );
   public final NumberSetting bloomRadius = this.register(
      new NumberSetting("Bloom Radius", 3.0, 1.0, 4.0, 1.0, "").configKey("render.lineglyphes.bloomradius").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting hdrBoost = this.register(
      new NumberSetting("HDR Boost", 2.5, 0.5, 6.0, 0.1, "").configKey("render.lineglyphes.hdrboost").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting anamorphic = this.register(
      new NumberSetting("Anamorphic", 0.4, 0.0, 1.0, 0.05, "").configKey("render.lineglyphes.anamorphic").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   private final List<LineGlyphesFeature.Snake> snakes = new ArrayList<>();
   private final List<GlowStripRenderer.Strip> strips = new ArrayList<>();
   private final GlowStripRenderer renderer = new GlowStripRenderer("furryhook-lineglyphes");
   private final Random random = new Random();

   public LineGlyphesFeature() {
      super("LineGlyphes", "Glowing lines running through the world, weaving and climbing", FeatureCategory.VISUAL, -1);
   }

   public static LineGlyphesFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(LineGlyphesFeature.class);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (mc.level != null && mc.player != null) {
         long now = System.currentTimeMillis();
         double stepSize = this.speed.getValue();
         double climbRate = this.climb.getValue();
         double turnRate = this.wiggle.getValue() * 0.35;
         double wigglePhaseStep = this.wiggleSpeed.getValue();
         int maxPoints = this.length.getValue().intValue();
         double keepDistanceSqr = Math.pow(this.radius.getValue() * 2.0, 2.0);
         Vec3 eye = mc.player.getEyePosition();
         Iterator<LineGlyphesFeature.Snake> iterator = this.snakes.iterator();

         while (iterator.hasNext()) {
            LineGlyphesFeature.Snake snake = iterator.next();
            if (!snake.finished(now) && !(snake.head().distanceToSqr(eye) > keepDistanceSqr)) {
               snake.advance(stepSize, climbRate, turnRate, wigglePhaseStep, maxPoints);
            } else {
               iterator.remove();
            }
         }

         int wanted = this.count.getValue().intValue();

         for (int attempt = 0; attempt < 4 && this.snakes.size() < wanted; attempt++) {
            this.snakes.add(this.createSnake(now));
         }
      } else {
         this.snakes.clear();
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.snakes.clear();
   }

   @Override
   protected void onDisable() {
      this.snakes.clear();
      this.strips.clear();
      this.renderer.release();
   }

   public void renderWorld() {
      if (mc.player != null && mc.level != null && !this.snakes.isEmpty()) {
         long now = System.currentTimeMillis();
         int baseColor = ColorMode.resolve(this.colorMode, this.color);
         float lineWidth = this.width.getValue().floatValue();
         this.strips.clear();

         for (LineGlyphesFeature.Snake snake : this.snakes) {
            if (snake.points.size() >= 2) {
               float progress = snake.progress(now);
               float alpha = progress <= 0.75F ? 1.0F : 1.0F - smoothstep((progress - 0.75F) / 0.25F);
               if (!(alpha <= 0.01F)) {
                  List<Vec3> path = GlowStripRenderer.smooth(snake.points, 3);
                  List<GlowStripRenderer.Point> points = new ArrayList<>(path.size());

                  for (int index = 0; index < path.size(); index++) {
                     float position = (float)index / (path.size() - 1);
                     points.add(
                        new GlowStripRenderer.Point(
                           path.get(index), lineWidth * (0.3F + 0.7F * position), ColorUtil.multiplyAlpha(baseColor, alpha * tailFade(position))
                        )
                     );
                  }

                  this.strips.add(new GlowStripRenderer.Strip(points));
               }
            }
         }

         if (!this.strips.isEmpty()) {
            this.renderer
               .render(
                  this.strips,
                  new GlowStripRenderer.Style(
                     this.glow.getValue().floatValue(),
                     this.coreWidth.getValue().floatValue(),
                     this.chromatic.getValue().floatValue(),
                     this.flowSpeed.getValue().floatValue(),
                     0.004F,
                     this.hdrBoost.getValue().floatValue()
                  ),
                  this.currentBloom()
               );
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

   private LineGlyphesFeature.Snake createSnake(long now) {
      double spawnRadius = this.radius.getValue();
      double angle = this.random.nextDouble() * (Math.PI * 2);
      double distance = spawnRadius * (0.3 + 0.7 * this.random.nextDouble());
      Vec3 start = mc.player.position().add(Math.cos(angle) * distance, -1.0 + this.random.nextDouble() * 2.0, Math.sin(angle) * distance);
      return new LineGlyphesFeature.Snake(
         start,
         this.random.nextDouble() * (Math.PI * 2),
         this.random.nextDouble() * (Math.PI * 2),
         this.random.nextBoolean() ? 1.0 : -1.0,
         now,
         this.lifetime.getValue().floatValue()
      );
   }

   private static float tailFade(float position) {
      return Math.clamp(position * 2.2F, 0.0F, 1.0F);
   }

   private static float smoothstep(float value) {
      float clamped = Math.clamp(value, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   private static final class Snake {
      private final List<Vec3> points = new ArrayList<>();
      private final long createdAt;
      private final float lifetimeMs;
      private final double turnDirection;
      private double heading;
      private double phase;

      private Snake(Vec3 start, double heading, double phase, double turnDirection, long createdAt, float lifetimeMs) {
         this.points.add(start);
         this.heading = heading;
         this.phase = phase;
         this.turnDirection = turnDirection;
         this.createdAt = createdAt;
         this.lifetimeMs = lifetimeMs;
      }

      private Vec3 head() {
         return this.points.getLast();
      }

      private void advance(double stepSize, double climbRate, double turnRate, double phaseStep, int maxPoints) {
         this.phase += phaseStep;
         this.heading = this.heading + Math.sin(this.phase) * turnRate * this.turnDirection;
         double rise = climbRate * stepSize * (0.55 + 0.45 * Math.sin(this.phase * 0.5));
         Vec3 head = this.head();
         this.points.add(new Vec3(head.x + Math.cos(this.heading) * stepSize, head.y + rise, head.z + Math.sin(this.heading) * stepSize));

         while (this.points.size() > Math.max(2, maxPoints)) {
            this.points.removeFirst();
         }
      }

      private float progress(long now) {
         return Math.clamp((float)(now - this.createdAt) / this.lifetimeMs, 0.0F, 1.0F);
      }

      private boolean finished(long now) {
         return (float)(now - this.createdAt) >= this.lifetimeMs;
      }
   }
}
