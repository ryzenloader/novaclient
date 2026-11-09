package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.combat.AuraRaycast;

public final class SpaceTimeRotation implements AuraRotation {
   private float lastYaw = 0.0F;
   private float lastPitch = 0.0F;
   private boolean initialized = false;
   private float acceleration = 0.0F;
   private boolean isBack = false;
   private float aimFatigue = 0.0F;
   private long lastAimStartTime = 0L;
   private float bodyYawOffset = 0.0F;
   private float bodyPitchOffset = 0.0F;
   private int bodyZoneChangeTicks = 0;
   private int aimPauseTicks = 0;
   private int reactionDelay = 0;
   private int microCorrectionTimer = 0;
   private int driftRetargetTicks = 0;
   private float aimDriftYaw = 0.0F;
   private float aimDriftPitch = 0.0F;
   private float driftTargetYaw = 0.0F;
   private float driftTargetPitch = 0.0F;
   private float yawAcceleration = 0.0F;
   private float pitchAcceleration = 0.0F;
   private float prevTargetYaw = 0.0F;
   private float prevTargetPitch = 0.0F;
   private float velocityYaw = 0.0F;
   private float velocityPitch = 0.0F;
   private final ThreadLocalRandom random = ThreadLocalRandom.current();
   private double reach = 3.0;
   private boolean throughWalls;
   private final float[][] patterns = new float[][]{
      {0.0F, 0.0F, 70.0F, 1.8F, 0.0F, 0.0F, 0.75F, 2.0F},
      {0.0F, 0.0F, 44.0F, 3.0F, 0.0F, 0.0F, 0.65F, 3.0F},
      {0.0F, 0.0F, 28.0F, 4.0F, 0.0F, 0.0F, 0.5F, 4.0F},
      {0.0F, 0.0F, 90.0F, 1.2F, 0.0F, 0.0F, 0.85F, 2.0F},
      {0.0F, 0.0F, 20.0F, 5.5F, 0.0F, 0.0F, 0.4F, 5.0F}
   };

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive() && player != null) {
         if (!this.initialized) {
            this.lastYaw = player.getYRot();
            this.lastPitch = player.getXRot();
            Vec3 eye = player.getEyePosition(1.0F);
            Vec3 diff = targetEyePos.subtract(eye);
            double horizontal = Math.hypot(diff.x, diff.z);
            this.prevTargetYaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
            this.prevTargetPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, horizontal))), -90.0F, 90.0F);
            this.initialized = true;
            this.lastAimStartTime = System.currentTimeMillis();
         }

         float dist = player.distanceTo(target);
         long now = System.currentTimeMillis();
         if (this.lastAimStartTime == 0L) {
            this.lastAimStartTime = now;
         }

         float timeFighting = (float)(now - this.lastAimStartTime) / 1000.0F;
         this.aimFatigue = Mth.clamp(timeFighting / 300.0F, 0.0F, 0.42F);
         boolean playerMoving = player.getDeltaMovement().horizontalDistanceSqr() > 0.003;
         boolean targetMoving = target.getDeltaMovement().horizontalDistanceSqr() > 0.003;
         boolean bothMoving = playerMoving && targetMoving;
         boolean anyMoving = playerMoving || targetMoving;
         boolean inHitRange = dist <= 3.2F;
         if (--this.bodyZoneChangeTicks <= 0) {
            if (!bothMoving && !inHitRange) {
               this.bodyYawOffset = (this.random.nextFloat() - 0.5F) * 4.5F;
               float[] pitches = new float[]{-3.0F, -1.0F, 0.0F, 1.5F, 3.5F};
               this.bodyPitchOffset = pitches[this.random.nextInt(pitches.length)];
               this.bodyPitchOffset = this.bodyPitchOffset + (this.random.nextFloat() - 0.5F) * 0.8F;
               this.bodyZoneChangeTicks = 18 + this.random.nextInt(28);
            } else {
               this.bodyYawOffset = (this.random.nextFloat() - 0.5F) * 1.6F;
               this.bodyPitchOffset = (this.random.nextFloat() - 0.5F) * 1.4F;
               this.bodyZoneChangeTicks = 16 + this.random.nextInt(22);
            }
         }

         int i1 = 0;
         int i2 = 0;
         float d1 = Float.MAX_VALUE;
         float d2 = Float.MAX_VALUE;

         for (int i = 0; i < this.patterns.length; i++) {
            float diff = Math.abs(this.patterns[i][3] - dist);
            if (diff < d1) {
               d2 = d1;
               i2 = i1;
               d1 = diff;
               i1 = i;
            } else if (diff < d2) {
               d2 = diff;
               i2 = i;
            }
         }

         float blend = d1 + d2 == 0.0F ? 0.5F : d2 / (d1 + d2);
         float speed = this.patterns[i1][2] * (1.0F - blend) + this.patterns[i2][2] * blend;
         float jitterMult = this.patterns[i1][6] * (1.0F - blend) + this.patterns[i2][6] * blend;
         speed *= 1.0F - this.aimFatigue * 0.4F;
         if (bothMoving) {
            speed *= 0.62F;
         } else if (anyMoving) {
            speed *= 0.8F;
         }

         if (timeFighting > 90.0F) {
            speed *= 0.84F;
         }

         if (timeFighting > 150.0F) {
            speed *= 0.8F;
         }

         Vec3 eye = player.getEyePosition(1.0F);
         Vec3 targetVec = targetEyePos != null ? targetEyePos : target.getEyePosition(1.0F);
         Vec3 diff = targetVec.subtract(eye);
         double horizontal = Math.hypot(diff.x, diff.z);
         float targetYaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
         float targetPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, horizontal))), -90.0F, 90.0F);
         if (this.aimPauseTicks > 0) {
            if (inHitRange && this.aimPauseTicks > 1) {
               this.aimPauseTicks = 1;
            }

            this.aimPauseTicks--;
            if (--this.driftRetargetTicks <= 0) {
               this.driftTargetYaw = (float)(this.random.nextGaussian() * (bothMoving ? 0.12F : 0.22F));
               this.driftTargetPitch = (float)(this.random.nextGaussian() * (bothMoving ? 0.08F : 0.14F));
               this.driftRetargetTicks = 8 + this.random.nextInt(12);
            }

            this.aimDriftYaw = this.aimDriftYaw + (this.driftTargetYaw - this.aimDriftYaw) * 0.05F;
            this.aimDriftPitch = this.aimDriftPitch + (this.driftTargetPitch - this.aimDriftPitch) * 0.05F;
            float pauseYaw = this.lastYaw + this.aimDriftYaw * 0.1F;
            float pausePitch = this.lastPitch + this.aimDriftPitch * 0.1F;
            double gcd = RotationContext.gcdStep();
            pauseYaw = this.quantizeGCD(pauseYaw, this.lastYaw, gcd);
            pausePitch = this.quantizeGCD(pausePitch, this.lastPitch, gcd);
            pausePitch = Mth.clamp(pausePitch, -90.0F, 90.0F);
            RotationContext.setRotation(pauseYaw, pausePitch);
            this.lastYaw = pauseYaw;
            this.lastPitch = pausePitch;
         } else {
            float pauseChance = bothMoving ? 0.02F : 0.035F;
            if (inHitRange) {
               pauseChance = 0.01F;
            }

            if (this.random.nextFloat() < pauseChance && Math.abs(Mth.wrapDegrees(targetYaw - this.lastYaw)) < 12.0F) {
               this.aimPauseTicks = 1 + (bothMoving ? 0 : this.random.nextInt(2));
            }

            if (!bothMoving && !inHitRange && this.random.nextFloat() < 0.006F && Math.abs(Mth.wrapDegrees(targetYaw - this.lastYaw)) < 16.0F) {
               this.aimPauseTicks = 6 + this.random.nextInt(10);
            }

            if (this.isBack) {
               float angleDiff = Math.abs(Mth.wrapDegrees(targetYaw - this.lastYaw));
               this.acceleration -= angleDiff > 70.0F ? 0.11F : speed / 1400.0F;
               if (this.acceleration <= 0.0F) {
                  this.acceleration = 0.0F;
                  this.isBack = false;
               }
            } else {
               this.acceleration = this.acceleration + speed / 1800.0F * (0.88F + this.random.nextFloat() * 0.12F);
               float maxAcc = bothMoving ? 0.12F : 0.17F;
               maxAcc = Math.min(maxAcc, speed / 2500.0F + 0.03F);
               boolean onTarget = this.hits(player, target, this.lastYaw, this.lastPitch);
               if (this.acceleration >= maxAcc || onTarget) {
                  this.isBack = true;
               }
            }

            float currentAcc = Mth.clamp(this.acceleration, 0.0F, 1.0F);
            if (player.hurtTime > 0) {
               currentAcc *= 0.32F;
            }

            if (this.reactionDelay > 0) {
               this.reactionDelay--;
               currentAcc *= 0.28F;
            }

            if (bothMoving) {
               currentAcc *= 0.58F + this.random.nextFloat() * 0.08F;
            } else if (anyMoving) {
               currentAcc *= 0.78F;
            }

            if (timeFighting > 90.0F) {
               currentAcc *= 0.84F;
            }

            if (timeFighting > 150.0F) {
               currentAcc *= 0.8F;
            }

            if (--this.driftRetargetTicks <= 0) {
               float j = bothMoving ? 0.35F : 0.75F;
               if (inHitRange) {
                  j *= 0.5F;
               }

               this.driftTargetYaw = (float)(this.random.nextGaussian() * j * jitterMult);
               this.driftTargetPitch = (float)(this.random.nextGaussian() * j * 0.6F * jitterMult);
               this.driftRetargetTicks = bothMoving ? 7 + this.random.nextInt(12) : 9 + this.random.nextInt(16);
            }

            this.aimDriftYaw = this.aimDriftYaw + (this.driftTargetYaw - this.aimDriftYaw) * 0.08F;
            this.aimDriftPitch = this.aimDriftPitch + (this.driftTargetPitch - this.aimDriftPitch) * 0.08F;
            boolean micro = false;
            if (--this.microCorrectionTimer <= 0 && Math.abs(Mth.wrapDegrees(targetYaw - this.lastYaw)) < 8.0F) {
               micro = true;
               this.microCorrectionTimer = bothMoving ? 8 + this.random.nextInt(14) : 12 + this.random.nextInt(18);
            }

            float useBody = !bothMoving && !inHitRange ? 1.0F : 0.35F;
            float useDrift = micro ? 1.1F : 1.0F;
            if (bothMoving || inHitRange) {
               useDrift *= 0.3F;
            }

            float aimYaw = targetYaw + this.bodyYawOffset * useBody + this.aimDriftYaw * useDrift;
            float aimPitch = targetPitch + this.bodyPitchOffset * useBody + this.aimDriftPitch * useDrift;
            float deltaYaw = Mth.wrapDegrees(aimYaw - this.lastYaw);
            float deltaPitch = aimPitch - this.lastPitch;
            if (micro) {
               deltaYaw *= 0.35F;
               deltaPitch *= 0.35F;
            }

            float absYaw = Math.abs(deltaYaw);
            float absPitch = Math.abs(deltaPitch);
            if (absYaw > 28.0F || absPitch > 18.0F) {
               currentAcc = Math.min(1.0F, currentAcc + 0.08F);
            }

            boolean hasTrace = this.hits(player, target, this.lastYaw, this.lastPitch);
            if (hasTrace && absYaw < 5.0F && absPitch < 4.0F) {
               deltaYaw *= 0.4F;
               deltaPitch *= 0.4F;
               currentAcc *= 0.65F;
            }

            if (this.reactionDelay <= 0) {
               float velocityYaw = Mth.wrapDegrees(targetYaw - this.prevTargetYaw);
               float velocityPitch = targetPitch - this.prevTargetPitch;
               float lead = bothMoving ? 0.16F : Mth.clamp(dist / 5.5F, 0.1F, 0.22F);
               float clamp = bothMoving ? 4.0F : 3.0F;
               deltaYaw += Mth.clamp(velocityYaw * lead, -clamp, clamp);
               deltaPitch += Mth.clamp(velocityPitch * lead * 0.55F, -2.0F, 2.0F);
            }

            this.prevTargetYaw = targetYaw;
            this.prevTargetPitch = targetPitch;
            float breathTime = (float)(now % 100000L) / 1000.0F;
            float breathMul = bothMoving ? 0.7F : 1.15F;
            deltaYaw += Mth.sin(breathTime * 0.55F) * 0.09F * jitterMult * breathMul;
            deltaPitch += Mth.cos(breathTime * 0.42F) * 0.06F * jitterMult * breathMul;
            float smooth = Mth.clamp(currentAcc, 0.009F, 1.0F) * (1.0F - this.aimFatigue * 0.28F);
            float targetVelYaw = deltaYaw * smooth * (0.76F + this.random.nextFloat() * 0.1F);
            float targetVelPitch = deltaPitch * smooth * (0.74F + this.random.nextFloat() * 0.1F);
            if (micro) {
               targetVelYaw *= 0.55F;
               targetVelPitch *= 0.55F;
            }

            float inertia = bothMoving ? 0.68F : 0.58F;
            this.velocityYaw = this.velocityYaw * inertia + targetVelYaw * (1.0F - inertia);
            this.velocityPitch = this.velocityPitch * inertia + targetVelPitch * (1.0F - inertia);
            float noiseScale = bothMoving ? 0.018F : 0.028F;
            this.yawAcceleration = this.yawAcceleration * 0.86F + (float)(this.random.nextGaussian() * noiseScale * jitterMult);
            this.pitchAcceleration = this.pitchAcceleration * 0.86F + (float)(this.random.nextGaussian() * noiseScale * 0.75 * jitterMult);
            float newYaw = this.lastYaw + this.velocityYaw + this.yawAcceleration;
            float newPitch = this.lastPitch + this.velocityPitch + this.pitchAcceleration;
            float maxStep = bothMoving ? 5.0F + 12.0F * currentAcc : 7.0F + 19.0F * currentAcc;
            maxStep = Mth.clamp(maxStep, 2.5F, bothMoving ? 18.0F : 26.0F);
            if (absYaw > 30.0F) {
               maxStep *= 1.08F;
            }

            if (timeFighting > 90.0F) {
               maxStep *= 0.9F;
            }

            if (timeFighting > 150.0F) {
               maxStep *= 0.86F;
            }

            newYaw = this.lastYaw + Mth.clamp(newYaw - this.lastYaw, -maxStep, maxStep);
            newPitch = this.lastPitch + Mth.clamp(newPitch - this.lastPitch, -maxStep * 0.6F, maxStep * 0.6F);
            double gcd = RotationContext.gcdStep();
            newYaw = this.quantizeGCD(newYaw, this.lastYaw, gcd);
            newPitch = this.quantizeGCD(newPitch, this.lastPitch, gcd);
            newPitch = Mth.clamp(newPitch, -90.0F, 90.0F);
            RotationContext.setRotation(newYaw, newPitch);
            this.lastYaw = newYaw;
            this.lastPitch = newPitch;
         }
      }
   }

   @Override
   public void onAttack() {
      this.bodyYawOffset *= 0.25F;
      this.bodyPitchOffset *= 0.25F;
      this.bodyZoneChangeTicks = 0;
      this.reactionDelay = 2 + this.random.nextInt(3);
      this.isBack = true;
      this.acceleration *= 0.42F;
      this.velocityYaw *= 0.55F;
      this.velocityPitch *= 0.55F;
   }

   @Override
   public void reset() {
      this.initialized = false;
      this.lastAimStartTime = 0L;
      this.aimFatigue = 0.0F;
      this.velocityYaw = 0.0F;
      this.velocityPitch = 0.0F;
      this.aimPauseTicks = 0;
      this.reactionDelay = 0;
      this.acceleration = 0.0F;
      this.isBack = false;
   }

   private boolean hits(LocalPlayer player, LivingEntity target, float yaw, float pitch) {
      return AuraRaycast.rotationIntersectsTarget(player, target, yaw, pitch, this.reach, 0.0, 0.0, this.throughWalls);
   }

   private float quantizeGCD(float newValue, float oldValue, double gcd) {
      if (gcd < 1.0E-4) {
         return newValue;
      }

      float delta = newValue - oldValue;
      return oldValue + (float)(Math.round(delta / gcd) * gcd);
   }

   public boolean isReturning() {
      return false;
   }
}
