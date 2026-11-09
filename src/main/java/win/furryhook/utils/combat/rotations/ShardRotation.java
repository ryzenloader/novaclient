package win.furryhook.utils.combat.rotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.combat.AuraRaycast;

public final class ShardRotation implements AuraRotation {
   private static final float YAW_STEP_SIGMA = 3.0F;
   private static final float PITCH_STEP_SIGMA = 2.5F;
   private static final float EASE_YAW_SPAN = 55.0F;
   private static final float EASE_PITCH_SPAN = 40.0F;
   private static final float EASE_FLOOR = 0.3F;
   private static final int POINT_HOLD_MIN = 2;
   private static final int POINT_HOLD_MAX = 4;
   private static final int DRIFT_PERIOD_MIN = 14;
   private static final int DRIFT_PERIOD_MAX = 26;
   private static final int DRIFT_DOWN_TICKS = 3;
   private static final int DRIFT_HOLD_TICKS = 2;
   private static final int DRIFT_UP_TICKS = 4;
   private static final float DRIFT_PITCH_NEAR = 5.0F;
   private static final float DRIFT_PITCH_FAR = 2.0F;
   private static final double DRIFT_FAR_DISTANCE = 3.0;
   private static final float STRIKE_TOLERANCE = 5.0F;
   private static final float[][] POINTS = new float[][]{
      {0.5F, 0.87F, 0.5F},
      {0.5F, 0.62F, 0.5F},
      {0.36F, 0.5F, 0.5F},
      {0.64F, 0.5F, 0.5F},
      {0.5F, 0.38F, 0.5F},
      {0.5F, 0.18F, 0.5F},
      {0.5F, 0.72F, 0.36F},
      {0.5F, 0.72F, 0.64F}
   };
   private float maxYawStep = 26.0F;
   private float maxPitchStep = 18.0F;
   private double reach = 3.0;
   private boolean throughWalls;
   private int targetId = Integer.MIN_VALUE;
   private int pointIndex;
   private int pointHoldTicks;
   private final List<Integer> pointOrder = new ArrayList<>();
   private int driftCountdown = 14;
   private ShardRotation.Drift drift = ShardRotation.Drift.TRACKING;
   private int driftTicks;
   private float aimYaw;
   private float aimPitch;
   private boolean hasAim;

   public void setMaxYawStep(float maxYawStep) {
      this.maxYawStep = Math.max(1.0F, maxYawStep);
   }

   public void setMaxPitchStep(float maxPitchStep) {
      this.maxPitchStep = Math.max(1.0F, maxPitchStep);
   }

   public void setReach(double reach) {
      this.reach = Math.max(1.0, reach);
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.pointIndex = 0;
            this.pointHoldTicks = 0;
            this.pointOrder.clear();
            this.drift = ShardRotation.Drift.TRACKING;
            this.driftCountdown = rollDriftPeriod();
            this.driftTicks = 0;
         }

         Vec3 point = this.resolveAimPoint(player, target, targetEyePos);
         float[] ideal = yawPitchTo(player, point);
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         this.stepDriftMachine(player, target, currentYaw, currentPitch, ideal);
         float deltaYaw = Mth.wrapDegrees(ideal[0] - currentYaw);
         float deltaPitch = ideal[1] - currentPitch;
         ThreadLocalRandom random = ThreadLocalRandom.current();
         float maxYaw = Math.max(1.0F, this.maxYawStep + (float)random.nextGaussian() * 3.0F);
         float maxPitch = Math.max(1.0F, this.maxPitchStep + (float)random.nextGaussian() * 2.5F);
         if (this.drift == ShardRotation.Drift.DOWN || this.drift == ShardRotation.Drift.UP) {
            maxYaw *= 0.4F;
            maxPitch *= 0.45F;
         }

         float easeYaw = Math.min(1.0F, Math.abs(deltaYaw) / 55.0F);
         float easePitch = Math.min(1.0F, Math.abs(deltaPitch) / 40.0F);
         float stepYaw = Math.copySign(Math.min(Math.abs(deltaYaw), maxYaw * (0.3F + 0.7F * easeYaw)), deltaYaw);
         float stepPitch = Math.copySign(Math.min(Math.abs(deltaPitch), maxPitch * (0.3F + 0.7F * easePitch)), deltaPitch);
         double gcd = RotationContext.gcdStep();
         stepYaw = quantize(stepYaw, deltaYaw, gcd);
         stepPitch = quantize(stepPitch, deltaPitch, gcd);
         float nextYaw = currentYaw + stepYaw;
         float nextPitch = Mth.clamp(currentPitch + stepPitch, -90.0F, 90.0F);
         RotationContext.setRotation(nextYaw, nextPitch);
         this.aimYaw = ideal[0];
         this.aimPitch = ideal[1];
         this.hasAim = true;
      } else {
         this.reset();
      }
   }

   public boolean shouldStrike(LocalPlayer player, LivingEntity target) {
      if (!this.hasAim || player == null || target == null) {
         return false;
      } else if (this.drift != ShardRotation.Drift.DOWN && this.drift != ShardRotation.Drift.HOLD) {
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         return !(Math.abs(Mth.wrapDegrees(this.aimYaw - currentYaw)) > 5.0F) && !(Math.abs(this.aimPitch - currentPitch) > 5.0F)
            ? AuraRaycast.rotationIntersectsTarget(player, target, currentYaw, currentPitch, this.reach, 0.0, 0.153, this.throughWalls)
            : false;
      } else {
         return false;
      }
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.pointIndex = 0;
      this.pointHoldTicks = 0;
      this.pointOrder.clear();
      this.drift = ShardRotation.Drift.TRACKING;
      this.driftCountdown = 14;
      this.driftTicks = 0;
      this.aimYaw = 0.0F;
      this.aimPitch = 0.0F;
      this.hasAim = false;
   }

   private Vec3 resolveAimPoint(LocalPlayer player, LivingEntity target, Vec3 targetEyePos) {
      if (this.pointOrder.isEmpty()) {
         this.refillPointOrder();
      }

      if (--this.pointHoldTicks <= 0) {
         this.pointIndex = (this.pointIndex + 1) % this.pointOrder.size();
         this.pointHoldTicks = ThreadLocalRandom.current().nextInt(2, 5);
      }

      float[] p = POINTS[this.pointOrder.get(this.pointIndex)];
      Vec3 movement = targetEyePos.subtract(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
      AABB box = target.getBoundingBox().move(movement);
      double x = box.minX + (box.maxX - box.minX) * p[0];
      double y = box.minY + (box.maxY - box.minY) * p[1];
      double z = box.minZ + (box.maxZ - box.minZ) * p[2];
      return new Vec3(x, y, z);
   }

   private void refillPointOrder() {
      this.pointOrder.clear();
      List<Integer> deck = new ArrayList<>();

      for (int i = 0; i < POINTS.length; i++) {
         deck.add(i);
      }

      Collections.shuffle(deck);
      this.pointOrder.addAll(deck);
   }

   private void stepDriftMachine(LocalPlayer player, LivingEntity target, float currentYaw, float currentPitch, float[] ideal) {
      switch (this.drift) {
         case TRACKING:
            boolean converged = Math.abs(Mth.wrapDegrees(ideal[0] - currentYaw)) < 4.0F && Math.abs(ideal[1] - currentPitch) < 4.0F;
            if (converged && --this.driftCountdown <= 0) {
               this.drift = ShardRotation.Drift.DOWN;
               this.driftTicks = 3;
            }
            break;
         case DOWN:
            double distance = player.distanceTo(target);
            float depth = (float)Mth.clamp(5.0 - 3.0 * (distance / 3.0), 2.0, 5.0);
            ideal[1] = Mth.clamp(ideal[1] + depth, -90.0F, 90.0F);
            ideal[0] += (float)ThreadLocalRandom.current().nextGaussian() * 0.8F;
            if (--this.driftTicks <= 0) {
               this.drift = ShardRotation.Drift.HOLD;
               this.driftTicks = 2;
            }
            break;
         case HOLD:
            if (--this.driftTicks <= 0) {
               this.drift = ShardRotation.Drift.UP;
               this.driftTicks = 4;
            }
            break;
         case UP:
            if (--this.driftTicks <= 0) {
               this.drift = ShardRotation.Drift.TRACKING;
               this.driftCountdown = rollDriftPeriod();
            }
      }
   }

   private static int rollDriftPeriod() {
      return ThreadLocalRandom.current().nextInt(14, 27);
   }

   private static float[] yawPitchTo(LocalPlayer player, Vec3 point) {
      Vec3 eye = player.getEyePosition(1.0F);
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(dy, horizontal))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }

   private static float quantize(float step, float delta, double gcd) {
      if (gcd < 1.0E-4) {
         return step;
      }

      float snapped = (float)(Math.round(step / gcd) * gcd);
      if (snapped == 0.0F && Math.abs(delta) >= gcd) {
         snapped = (float)(Math.signum(delta) * gcd);
      }

      return snapped;
   }

   private enum Drift {
      TRACKING,
      DOWN,
      HOLD,
      UP;
   }
}
