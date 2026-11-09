package win.furryhook.utils.render.target;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.Animation;
import win.furryhook.utils.render.HurtUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.particles.WorldParticleRenderer;

public final class SoulTargetRenderer {
   private static final float FIXED_STEP = 0.008333334F;
   private static final float MAX_FRAME = 0.1F;
   private static final float BASE_RADIUS = 0.13F;
   private static final float ASCEND_SECONDS = 0.9F;
   private static final float TAIL_THICKNESS = 0.42F;
   private static final float MAX_TAIL_LENGTH = 3.5F;
   private final List<SoulSpirit> souls = new ArrayList<>();
   private final List<WorldParticleRenderer.Sprite> core = new ArrayList<>(512);
   private final List<WorldParticleRenderer.Sprite> body = new ArrayList<>(512);
   private final List<WorldParticleRenderer.Sprite> halo = new ArrayList<>(512);
   private final WorldParticleRenderer renderer = new WorldParticleRenderer();
   private final Animation appear = new Animation(600L, Animation.Easing.EASE_OUT_EXPO);
   private final float[] point = new float[3];
   private LivingEntity lastTarget;
   private int lastTargetId = Integer.MIN_VALUE;
   private boolean animationTarget;
   private long lastFrameNanos;
   private float clock;
   private float accumulator;
   private float ascend;
   private Vec3 ascendOrigin;

   public void render(AuraFeature feature, LivingEntity activeTarget, float tickDelta, int color) {
      float delta = this.frameDelta();
      if (feature == null) {
         this.reset();
      } else {
         boolean present = valid(activeTarget);
         if (present) {
            this.lastTarget = activeTarget;
         } else if (dead(this.lastTarget) && this.ascend <= 0.0F && this.lastTargetId != Integer.MIN_VALUE) {
            this.beginAscend(tickDelta);
         }

         boolean ascending = this.ascend > 0.0F;
         this.updateAnimations(present || ascending);
         float appearValue = this.appear.getValue();
         if (!present && !ascending && appearValue <= 0.01F) {
            this.softReset();
         } else if (this.lastTarget != null || ascending) {
            int count = Mth.clamp(feature.soulCount(), 1, 8);
            float sizeScale = feature.soulSize();
            float speedScale = feature.soulSpeed();
            float trailScale = feature.soulTrail();
            boolean throughWalls = feature.soulsThroughWalls();
            Vec3 center = centerOf(present ? activeTarget : this.lastTarget, tickDelta);
            this.syncSouls(count, center);
            this.handleTargetSwitch(present ? activeTarget : null, center);
            this.handleImpact(present ? activeTarget : null);
            if (ascending) {
               this.ascend = Math.max(0.0F, this.ascend - delta);
            }

            this.advance(delta, center, speedScale, ascending);
            float fade = ascending ? this.ascend / 0.9F : 1.0F;
            int tinted = present ? HurtUtil.blend(color, activeTarget, 1.0F) : color;
            this.buildSprites(tinted, appearValue * fade, sizeScale, trailScale);
            if (!this.halo.isEmpty()) {
               this.renderer.render(this.halo, 1.1F, throughWalls, true);
            }

            if (!this.body.isEmpty()) {
               this.renderer.render(this.body, 1.3F, throughWalls, false);
            }

            if (!this.core.isEmpty()) {
               this.renderer.render(this.core, 1.9F, throughWalls, true);
            }

            if (ascending && this.ascend <= 0.0F) {
               this.softReset();
            }
         }
      }
   }

   public void reset() {
      this.softReset();
      this.lastFrameNanos = 0L;
      this.clock = 0.0F;
   }

   private void advance(float delta, Vec3 center, float speedScale, boolean ascending) {
      this.accumulator = Math.min(0.25F, this.accumulator + delta);

      while (this.accumulator >= 0.008333334F) {
         this.accumulator -= 0.008333334F;
         this.clock += 0.008333334F;

         for (SoulSpirit soul : this.souls) {
            Vec3 anchor = ascending ? this.ascendAnchor(soul) : this.orbitAnchor(soul, center, speedScale);
            soul.step(0.008333334F, anchor, this.clock, ascending ? 0.75F : speedScale);
         }
      }
   }

   private Vec3 orbitAnchor(SoulSpirit soul, Vec3 center, float speedScale) {
      float t = this.clock * speedScale;
      double x = Math.cos(t * soul.orbitFrequency * 2.4F + soul.phase) * soul.orbitRadius;
      double y = Math.sin(t * soul.bobFrequency * 1.7F + soul.phase * 1.7F) * soul.orbitHeight;
      double z = Math.sin(t * soul.orbitFrequency * 2.71F + soul.phase) * soul.orbitRadius;
      return center.add(x, y, z);
   }

   private Vec3 ascendAnchor(SoulSpirit soul) {
      Vec3 origin = this.ascendOrigin != null ? this.ascendOrigin : new Vec3(soul.x, soul.y, soul.z);
      float progress = 1.0F - this.ascend / 0.9F;
      double drift = 0.35 * Math.sin(soul.phase + progress * 2.0F);
      return origin.add(drift, 0.6 + progress * 3.2, drift * 0.7);
   }

   private void syncSouls(int count, Vec3 center) {
      if (this.souls.size() != count) {
         while (this.souls.size() > count) {
            this.souls.remove(this.souls.size() - 1);
         }

         while (this.souls.size() < count) {
            SoulSpirit soul = new SoulSpirit(this.souls.size(), count);
            soul.placeAt(center);
            this.souls.add(soul);
         }
      }
   }

   private void handleTargetSwitch(LivingEntity target, Vec3 center) {
      int id = target != null ? target.getId() : Integer.MIN_VALUE;
      if (id != this.lastTargetId) {
         boolean firstTarget = this.lastTargetId == Integer.MIN_VALUE;
         this.lastTargetId = id;
         if (target != null) {
            if (!firstTarget) {
               for (SoulSpirit soul : this.souls) {
                  Vec3 from = new Vec3(soul.x, soul.y, soul.z);
                  Vec3 direction = center.subtract(from);
                  if (!(direction.lengthSqr() < 1.0E-6)) {
                     Vec3 lateral = direction.normalize().cross(new Vec3(0.0, 1.0, 0.0));
                     if (lateral.lengthSqr() < 1.0E-6) {
                        lateral = new Vec3(1.0, 0.0, 0.0);
                     }

                     soul.launchTo(center, lateral.normalize());
                  }
               }
            } else {
               for (SoulSpirit soul : this.souls) {
                  soul.placeAt(center);
               }
            }
         }
      }
   }

   private void handleImpact(LivingEntity target) {
      if (target != null && SoulImpulse.consume(target.getId()) && !this.souls.isEmpty()) {
         SoulSpirit closest = null;
         double best = Double.MAX_VALUE;
         Vec3 hit = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);

         for (SoulSpirit soul : this.souls) {
            double distance = hit.distanceToSqr(soul.x, soul.y, soul.z);
            if (distance < best) {
               best = distance;
               closest = soul;
            }
         }

         if (closest != null) {
            closest.impact();
            double dx = hit.x - closest.x;
            double dy = hit.y - closest.y;
            double dz = hit.z - closest.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 1.0E-4) {
               double push = 6.5 / length;
               closest.vx += dx * push;
               closest.vy += dy * push;
               closest.vz += dz * push;
            }
         }
      }
   }

   private void buildSprites(int color, float alphaScale, float sizeScale, float trailScale) {
      this.core.clear();
      this.body.clear();
      this.halo.clear();
      if (!(alphaScale <= 0.004F)) {
         for (SoulSpirit soul : this.souls) {
            int history = soul.historyCount();
            if (history >= 2) {
               float speed = soul.speed();
               float pulse = 1.0F + 0.12F * (float)Math.sin(this.clock * 4.0F + soul.pulsePhase);
               float headRadius = 0.13F * sizeScale * pulse * (1.0F + soul.flash * 0.9F);
               float tailSamples = Mth.clamp(speed * 8.0F * trailScale, 10.0F, 90.0F);
               tailSamples = Math.min(tailSamples, history - 1.0F);
               float perSample = Math.max(1.0E-4F, speed * 0.008333334F);
               tailSamples = Math.min(tailSamples, 3.5F * trailScale / perSample);
               float tailLength = speed * tailSamples * 0.008333334F;
               float thinnest = headRadius * 0.42F;
               int samples = Mth.clamp(Math.round(tailLength / Math.max(0.004F, thinnest * 0.4F)) + 12, 20, 170);
               float density = Mth.clamp(52.0F / samples, 0.3F, 1.25F);

               for (int i = 0; i < samples; i++) {
                  float u = (float)i / (samples - 1);
                  soul.sample(u * tailSamples, this.point);
                  float shape = 0.42F + 0.58000004F * (float)Math.pow(1.0F - u, 0.7);
                  float radius = headRadius * shape;
                  float head = (float)Math.pow(1.0F - u, 3.0);
                  float taper = (float)Math.pow(1.0F - u, 2.0);
                  int hot = ColorUtil.lerp(color, -1, Mth.clamp(0.28F + head * 0.5F + soul.flash * 0.4F, 0.0F, 1.0F));
                  Vec3 position = new Vec3(this.point[0], this.point[1], this.point[2]);
                  float coreAlpha = taper * alphaScale * density * (0.5F + soul.flash * 0.9F);
                  if (coreAlpha > 0.004F) {
                     this.core.add(new WorldParticleRenderer.Sprite(position, radius * 0.75F, ColorUtil.multiplyAlpha(hot, Math.min(1.0F, coreAlpha))));
                  }

                  float bodyAlpha = 0.3F * (1.0F - u) * alphaScale * density;
                  if (bodyAlpha > 0.004F) {
                     this.body.add(new WorldParticleRenderer.Sprite(position, radius * 1.25F, ColorUtil.multiplyAlpha(color, bodyAlpha)));
                  }

                  float haloAlpha = 0.03F * taper * alphaScale * density;
                  if (haloAlpha > 0.002F) {
                     this.halo.add(new WorldParticleRenderer.Sprite(position, radius * 2.9F, ColorUtil.multiplyAlpha(color, haloAlpha)));
                  }
               }
            }
         }
      }
   }

   private float frameDelta() {
      long now = System.nanoTime();
      float delta = this.lastFrameNanos == 0L ? 0.008333334F : Math.min(0.1F, (float)(now - this.lastFrameNanos) / 1.0E9F);
      this.lastFrameNanos = now;
      return delta;
   }

   private void beginAscend(float tickDelta) {
      this.ascend = 0.9F;
      this.ascendOrigin = centerOf(this.lastTarget, tickDelta);
      this.lastTargetId = Integer.MIN_VALUE;
   }

   private void updateAnimations(boolean present) {
      if (present != this.animationTarget) {
         this.animationTarget = present;
         this.appear
            .animate(
               this.appear.getValue(), present ? 1.0F : 0.0F, present ? 600L : 420L, present ? Animation.Easing.EASE_OUT_EXPO : Animation.Easing.EASE_OUT_QUAD
            );
      }
   }

   private void softReset() {
      this.souls.clear();
      this.core.clear();
      this.body.clear();
      this.halo.clear();
      this.lastTarget = null;
      this.lastTargetId = Integer.MIN_VALUE;
      this.accumulator = 0.0F;
      this.ascend = 0.0F;
      this.ascendOrigin = null;
      this.animationTarget = false;
      this.appear.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_EXPO);
      SoulImpulse.clear();
   }

   private static Vec3 centerOf(LivingEntity entity, float tickDelta) {
      return entity == null ? Vec3.ZERO : Render3DUtil.interpolatedPosition(entity, tickDelta).add(0.0, entity.getBbHeight() * 0.55, 0.0);
   }

   private static boolean valid(LivingEntity entity) {
      return entity != null && entity.isAlive() && !entity.isRemoved();
   }

   private static boolean dead(LivingEntity entity) {
      return entity != null && (entity.isDeadOrDying() || entity.deathTime > 0 || entity.getHealth() <= 0.0F);
   }
}
