package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.combat.AuraRaycast;

public final class FurryRotation implements AuraRotation {
   private static final float SPIN_DEGREES = 360.0F;
   private static final int RETURN_TICKS = 2;
   private static final float ACQUIRE_STEP = 70.0F;
   private static final float JITTER_YAW = 90.0F;
   private static final float JITTER_PITCH = 60.0F;
   private static final int JITTER_STEPS = 6;
   private static final float JITTER_SAFETY = 0.9F;
   private boolean spin = true;
   private boolean jitter = true;
   private int spinTicks = 6;
   private float jitterStrength = 1.0F;
   private double reach = 3.0;
   private boolean throughWalls;
   private int targetId = Integer.MIN_VALUE;
   private float spinLeft;
   private float spinStep;
   private int spinSign = 1;
   private int returnTicks;

   public void setSpin(boolean spin) {
      this.spin = spin;
      if (!spin) {
         this.cancelSpin();
      }
   }

   public void setJitter(boolean jitter) {
      this.jitter = jitter;
   }

   public void setSpinTicks(int spinTicks) {
      this.spinTicks = Math.max(3, spinTicks);
   }

   public void setJitterStrength(float jitterStrength) {
      this.jitterStrength = Mth.clamp(jitterStrength, 0.0F, 1.0F);
   }

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public boolean isSpinning() {
      return this.spinLeft > 0.0F;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.cancelSpin();
         }

         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         if (this.spinLeft > 0.0F) {
            this.spinStep(player, target, targetEyePos, currentYaw, currentPitch);
         } else {
            float[] aim = this.jitter ? this.jitteredAim(player, target, targetEyePos) : rotationTo(player.getEyePosition(1.0F), targetEyePos);
            float deltaYaw = Mth.wrapDegrees(aim[0] - currentYaw);
            float deltaPitch = aim[1] - currentPitch;
            if (this.returnTicks > 0) {
               this.returnTicks--;
               float ease = this.returnTicks == 0 ? 1.0F : 0.6F;
               deltaYaw *= ease;
               deltaPitch *= ease;
            } else {
               deltaYaw = Mth.clamp(deltaYaw, -70.0F, 70.0F);
               deltaPitch = Mth.clamp(deltaPitch, -70.0F, 70.0F);
            }

            RotationContext.setRotation(currentYaw + deltaYaw, Mth.clamp(currentPitch + deltaPitch, -90.0F, 90.0F));
         }
      } else {
         this.reset();
      }
   }

   @Override
   public void onAttack() {
      if (this.spin) {
         this.spinLeft = 360.0F;
         this.spinStep = 360.0F / this.spinTicks;
         this.spinSign = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
         this.returnTicks = 0;
      }
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.cancelSpin();
   }

   private void cancelSpin() {
      this.spinLeft = 0.0F;
      this.spinStep = 0.0F;
      this.returnTicks = 0;
   }

   private void spinStep(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, float currentYaw, float currentPitch) {
      float step = Math.min(this.spinLeft, this.spinStep);
      this.spinLeft -= step;
      float[] aim = rotationTo(player.getEyePosition(1.0F), targetEyePos);
      float pitch = Mth.lerp(0.4F, currentPitch, aim[1]);
      RotationContext.setRotation(currentYaw + step * this.spinSign, Mth.clamp(pitch, -90.0F, 90.0F));
      if (this.spinLeft <= 0.0F) {
         this.spinLeft = 0.0F;
         this.returnTicks = 2;
      }
   }

   private float[] jitteredAim(LocalPlayer player, LivingEntity target, Vec3 targetEyePos) {
      Vec3 eye = player.getEyePosition(1.0F);
      ThreadLocalRandom random = ThreadLocalRandom.current();
      AABB box = target.getBoundingBox();
      Vec3 bias = new Vec3(
         random.nextDouble(-2.0, 2.0) * box.getXsize(), random.nextDouble(-2.0, 2.0) * box.getYsize(), random.nextDouble(-2.0, 2.0) * box.getZsize()
      );
      Vec3 point = AuraRaycast.findAimPoint(player, target, target.position(), this.reach, this.throughWalls, bias);
      if (point == null) {
         return rotationTo(eye, targetEyePos);
      }

      float[] aim = rotationTo(eye, point);
      if (this.jitterStrength <= 0.0F) {
         return aim;
      }

      float offsetYaw = (float)random.nextDouble(-90.0, 90.0) * this.jitterStrength;
      float offsetPitch = (float)random.nextDouble(-60.0, 60.0) * this.jitterStrength;
      float scale = this.largestHittingScale(player, target, aim, offsetYaw, offsetPitch) * 0.9F;
      return new float[]{aim[0] + offsetYaw * scale, Mth.clamp(aim[1] + offsetPitch * scale, -90.0F, 90.0F)};
   }

   private float largestHittingScale(LocalPlayer player, LivingEntity target, float[] aim, float offsetYaw, float offsetPitch) {
      if (this.hits(player, target, aim, offsetYaw, offsetPitch, 1.0F)) {
         return 1.0F;
      }

      float low = 0.0F;
      float high = 1.0F;

      for (int step = 0; step < 6; step++) {
         float mid = (low + high) * 0.5F;
         if (this.hits(player, target, aim, offsetYaw, offsetPitch, mid)) {
            low = mid;
         } else {
            high = mid;
         }
      }

      return low;
   }

   private boolean hits(LocalPlayer player, LivingEntity target, float[] aim, float offsetYaw, float offsetPitch, float scale) {
      return AuraRaycast.rotationIntersectsTarget(
         player, target, aim[0] + offsetYaw * scale, Mth.clamp(aim[1] + offsetPitch * scale, -90.0F, 90.0F), this.reach, 0.0, 0.0, this.throughWalls
      );
   }

   private static float[] rotationTo(Vec3 eye, Vec3 point) {
      Vec3 diff = point.subtract(eye);
      double horizontal = Math.hypot(diff.x, diff.z);
      float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      float pitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, horizontal))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }
}
