package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.combat.AuraRaycast;

public final class SlothRotation implements AuraRotation {
   private static final float JITTER_MIN = -0.2F;
   private static final float JITTER_MAX = 0.2F;
   private static final float JITTER_STEP = 0.02F;
   private static final float HEAD_MOVEMENT_MULT = 3.0F;
   private static final float RANDOM_OFFSET = 2.0F;
   private static final double REACH_MARGIN = 0.15;
   private double reach = 3.0;
   private boolean throughWalls;
   private boolean waterSprintActive;
   private float jitterOffset = 0.0F;
   private boolean jitterIncreasing = false;
   private static final float GCD_STEP = 0.15F;
   private int lastTargetId = Integer.MIN_VALUE;

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public void setWaterSprintActive(boolean active) {
      this.waterSprintActive = active;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (player != null && target != null && target.isAlive()) {
         if (this.lastTargetId != target.getId()) {
            this.lastTargetId = target.getId();
            this.jitterOffset = 0.0F;
            this.jitterIncreasing = false;
         }

         this.updateJitterOscillator();
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         float headMovement = Mth.wrapDegrees(player.getYRot() - player.yRotO);
         Vec3 aimPoint = this.getAimPoint(target);
         float[] ideal = rotationTo(player.getEyePosition(1.0F), aimPoint);
         float adaptiveJitter = this.jitterOffset * Math.abs(headMovement) * 3.0F;
         ideal[0] += adaptiveJitter;
         ThreadLocalRandom random = ThreadLocalRandom.current();
         ideal[0] += random.nextFloat(-2.0F, 2.0F);
         ideal[1] += random.nextFloat(-2.0F, 2.0F);
         float deltaYaw = Mth.wrapDegrees(ideal[0] - currentYaw);
         float deltaPitch = ideal[1] - currentPitch;
         float smooth = Math.clamp(Math.abs(this.jitterOffset), 0.0F, 1.0F);
         float absYaw = Math.abs(deltaYaw);
         float absPitch = Math.abs(deltaPitch);
         boolean verticalFreeze = currentPitch >= 89.9F || currentPitch <= -89.9F;
         boolean collisionFreeze = player.getBoundingBox().intersects(target.getBoundingBox()) && player.horizontalCollision;
         boolean shouldFreeze = verticalFreeze || collisionFreeze;
         float speedYaw = shouldFreeze ? 0.0F : Mth.clamp(absYaw * smooth, 0.0F, 999.0F);
         float speedPitch = Mth.clamp(absPitch * (this.waterSprintActive ? 0.0F : smooth / 2.0F), 0.0F, 999.0F);
         float yawStep = Mth.clamp(deltaYaw, -speedYaw, speedYaw);
         float pitchStep = Mth.clamp(deltaPitch, -speedPitch, speedPitch);
         float newYaw = currentYaw + yawStep;
         float newPitch = currentPitch + pitchStep;
         newYaw = this.applyGCD(newYaw, currentYaw);
         newPitch = this.applyGCD(newPitch, currentPitch);
         RotationContext.setRotation(newYaw, Mth.clamp(newPitch, -90.0F, 90.0F));
      } else {
         this.reset();
      }
   }

   @Override
   public void reset() {
      this.lastTargetId = Integer.MIN_VALUE;
      this.jitterOffset = 0.0F;
      this.jitterIncreasing = false;
      this.waterSprintActive = false;
   }

   private void updateJitterOscillator() {
      if (!this.jitterIncreasing) {
         this.jitterOffset += 0.02F;
         if (this.jitterOffset >= 0.2F) {
            this.jitterOffset = 0.2F;
            this.jitterIncreasing = true;
         }
      } else {
         this.jitterOffset -= 0.02F;
         if (this.jitterOffset <= -0.2F) {
            this.jitterOffset = -0.2F;
            this.jitterIncreasing = false;
         }
      }
   }

   private Vec3 getAimPoint(LivingEntity target) {
      AABB box = target.getBoundingBox();
      return box.getCenter().add(0.0, 0.5, 0.0);
   }

   private static float[] rotationTo(Vec3 eye, Vec3 point) {
      Vec3 diff = point.subtract(eye);
      double horizontal = Math.hypot(diff.x, diff.z);
      float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      float pitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, horizontal))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }

   private float applyGCD(float newAngle, float currentAngle) {
      float delta = newAngle - currentAngle;
      float quantized = Math.round(delta / 0.15F) * 0.15F;
      return currentAngle + quantized;
   }

   private boolean hitsTarget(LocalPlayer player, LivingEntity target, float yaw, float pitch) {
      return AuraRaycast.rotationIntersectsTarget(player, target, yaw, pitch, this.reach, 0.0, 0.15, this.throughWalls);
   }
}
