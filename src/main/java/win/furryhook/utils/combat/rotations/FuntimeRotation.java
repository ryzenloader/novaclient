package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.combat.AuraRaycast;

public final class FuntimeRotation implements AuraRotation {
   private static final float SNAP_THRESHOLD = 18.0F;
   private static final float SNAP_CAP_YAW = 60.0F;
   private static final float SNAP_CAP_PITCH = 40.0F;
   private static final float TRACK_CAP_YAW = 20.0F;
   private static final float TRACK_CAP_PITCH = 14.0F;
   private static final float OFFSET_PHASE_STEP = 0.16F;
   private static final int OFFSET_STEPS = 5;
   private static final float OFFSET_SAFETY = 0.85F;
   private static final int POINT_HOLD_TICKS = 14;
   private double reach = 3.0;
   private boolean throughWalls;
   private int snapTicks = 3;
   private float smoothness = 0.55F;
   private float offsetDegrees = 1.2F;
   private int multipoint = 3;
   private int targetId = Integer.MIN_VALUE;
   private int snapLeft;
   private int snapLength;
   private float snapProgress;
   private int pointIndex;
   private int pointTicks;
   private int pointSide = 1;
   private float offsetPhase;
   private float offsetSign = 1.0F;

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public void setSnapTicks(int snapTicks) {
      this.snapTicks = Math.max(2, snapTicks);
   }

   public void setSmoothness(float smoothness) {
      this.smoothness = Mth.clamp(smoothness, 0.0F, 1.0F);
   }

   public void setOffsetDegrees(float offsetDegrees) {
      this.offsetDegrees = Math.max(0.0F, offsetDegrees);
   }

   public void setMultipoint(int multipoint) {
      this.multipoint = Mth.clamp(multipoint, 1, 5);
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.pointTicks = 0;
            this.pointIndex = ThreadLocalRandom.current().nextInt(Math.max(1, this.multipoint));
            this.offsetPhase = (float)(ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0);
            this.beginSnap();
         }

         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         Vec3 eye = player.getEyePosition(1.0F);
         Vec3 aimPoint = this.pickAimPoint(player, target, targetEyePos, eye);
         float[] aim = this.applyOffset(player, target, rotationTo(eye, aimPoint));
         float deltaYaw = Mth.wrapDegrees(aim[0] - currentYaw);
         float deltaPitch = aim[1] - currentPitch;
         float error = (float)Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
         if (this.snapLeft <= 0 && error > 18.0F) {
            this.beginSnap();
         }

         boolean snapping = this.snapLeft > 0;
         float fraction = snapping ? this.snapFraction() : this.trackFraction();
         float capYaw = snapping ? 60.0F : 20.0F;
         float capPitch = snapping ? 40.0F : 14.0F;
         float stepYaw = Mth.clamp(deltaYaw * fraction, -capYaw, capYaw);
         float stepPitch = Mth.clamp(deltaPitch * fraction, -capPitch, capPitch);
         double gcd = RotationContext.gcdStep();
         stepYaw = quantize(stepYaw, deltaYaw, gcd);
         stepPitch = quantize(stepPitch, deltaPitch, gcd);
         RotationContext.setRotation(currentYaw + stepYaw, Mth.clamp(currentPitch + stepPitch, -89.0F, 89.0F));
         this.pointTicks++;
      } else {
         this.reset();
      }
   }

   @Override
   public void onAttack() {
      this.advancePoint();
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.snapLeft = 0;
      this.snapLength = 0;
      this.snapProgress = 0.0F;
      this.pointIndex = 0;
      this.pointTicks = 0;
      this.pointSide = 1;
      this.offsetPhase = 0.0F;
      this.offsetSign = 1.0F;
   }

   private void beginSnap() {
      this.snapLength = Math.max(2, this.snapTicks + (ThreadLocalRandom.current().nextBoolean() ? 0 : 1));
      this.snapLeft = this.snapLength;
      this.snapProgress = 0.0F;
   }

   private float snapFraction() {
      int index = this.snapLength - this.snapLeft + 1;
      float done = (float)KinematicsUtil.getMinimumJerk(0.0, 1.0, (double)index / this.snapLength);
      float fraction = this.snapProgress >= 1.0F ? 1.0F : Mth.clamp((done - this.snapProgress) / (1.0F - this.snapProgress), 0.0F, 1.0F);
      this.snapProgress = done;
      this.snapLeft--;
      if (this.snapLeft <= 0) {
         this.snapProgress = 0.0F;
      }

      return fraction;
   }

   private float trackFraction() {
      return Mth.lerp(this.smoothness, 0.62F, 0.2F);
   }

   private Vec3 pickAimPoint(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, Vec3 eye) {
      if (this.pointTicks >= 14) {
         this.advancePoint();
      }

      int points = Math.max(1, this.multipoint);

      for (int offset = 0; offset < points; offset++) {
         int index = (this.pointIndex + offset) % points;
         Vec3 candidate = this.candidate(target, eye, index, points);
         float[] rotation = rotationTo(eye, candidate);
         if (this.hits(player, target, rotation[0], rotation[1])) {
            if (offset != 0) {
               this.pointIndex = index;
               this.pointTicks = 0;
            }

            return candidate;
         }
      }

      Vec3 fallback = AuraRaycast.findAimPoint(player, target, target.position(), this.reach, this.throughWalls, Vec3.ZERO);
      return fallback != null ? fallback : targetEyePos;
   }

   private Vec3 candidate(LivingEntity target, Vec3 eye, int index, int points) {
      AABB box = target.getBoundingBox().deflate(0.05);
      double top = box.maxY - 0.18;
      double bottom = Mth.clamp(box.maxY - 0.75, box.minY + 0.1, top);
      double y = points == 1 ? top : Mth.lerp((double)index / (points - 1), top, bottom);
      double centerX = (box.minX + box.maxX) * 0.5;
      double centerZ = (box.minZ + box.maxZ) * 0.5;
      double dirX = centerX - eye.x;
      double dirZ = centerZ - eye.z;
      double length = Math.hypot(dirX, dirZ);
      if (length < 1.0E-6) {
         return new Vec3(centerX, Mth.clamp(y, box.minY, box.maxY), centerZ);
      }

      double perpX = -dirZ / length;
      double perpZ = dirX / length;
      double spread = Math.min(box.getXsize(), box.getZsize()) * 0.22 * this.pointSide;
      return new Vec3(
         Mth.clamp(centerX + perpX * spread, box.minX, box.maxX), Mth.clamp(y, box.minY, box.maxY), Mth.clamp(centerZ + perpZ * spread, box.minZ, box.maxZ)
      );
   }

   private void advancePoint() {
      int points = Math.max(1, this.multipoint);
      this.pointIndex = (this.pointIndex + 1) % points;
      this.pointTicks = 0;
      this.pointSide = -this.pointSide;
      this.offsetSign = -this.offsetSign;
   }

   private float[] applyOffset(LocalPlayer player, LivingEntity target, float[] ideal) {
      if (this.offsetDegrees <= 0.0F) {
         return ideal;
      }

      this.offsetPhase += 0.16F;
      float fast = (float)Math.sin(this.offsetPhase);
      float slow = (float)Math.sin(this.offsetPhase * 0.37F + 1.3F);
      float yawOffset = this.offsetDegrees * this.offsetSign * (fast * 0.75F + slow * 0.25F);
      float pitchOffset = this.offsetDegrees * 0.45F * (float)Math.sin(this.offsetPhase * 0.61F);
      float scale = this.largestHittingScale(player, target, ideal, yawOffset, pitchOffset) * 0.85F;
      return new float[]{ideal[0] + yawOffset * scale, Mth.clamp(ideal[1] + pitchOffset * scale, -89.0F, 89.0F)};
   }

   private float largestHittingScale(LocalPlayer player, LivingEntity target, float[] ideal, float yawOffset, float pitchOffset) {
      if (this.offsetHits(player, target, ideal, yawOffset, pitchOffset, 1.0F)) {
         return 1.0F;
      }

      float low = 0.0F;
      float high = 1.0F;

      for (int step = 0; step < 5; step++) {
         float mid = (low + high) * 0.5F;
         if (this.offsetHits(player, target, ideal, yawOffset, pitchOffset, mid)) {
            low = mid;
         } else {
            high = mid;
         }
      }

      return low;
   }

   private boolean offsetHits(LocalPlayer player, LivingEntity target, float[] ideal, float yawOffset, float pitchOffset, float scale) {
      return this.hits(player, target, ideal[0] + yawOffset * scale, Mth.clamp(ideal[1] + pitchOffset * scale, -89.0F, 89.0F));
   }

   private boolean hits(LocalPlayer player, LivingEntity target, float yaw, float pitch) {
      return AuraRaycast.rotationIntersectsTarget(player, target, yaw, pitch, this.reach, 0.0, 0.0, this.throughWalls);
   }

   private static float quantize(float step, float delta, double gcd) {
      if (gcd < 1.0E-4) {
         return step;
      }

      if (Math.abs(step) > Math.abs(delta)) {
         step = delta;
      }

      float snapped = (float)(Math.round(step / gcd) * gcd);
      if (snapped == 0.0F && Math.abs(delta) >= gcd) {
         snapped = (float)(Math.signum(delta) * gcd);
      }

      return snapped;
   }

   private static float[] rotationTo(Vec3 eye, Vec3 point) {
      Vec3 diff = point.subtract(eye);
      double horizontal = Math.hypot(diff.x, diff.z);
      float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      float pitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, horizontal))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }
}
