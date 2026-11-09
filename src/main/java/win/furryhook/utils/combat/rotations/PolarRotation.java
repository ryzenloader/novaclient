package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public final class PolarRotation implements AuraRotation {
   private static final float YAW_STEP_SIGMA = 4.0F;
   private static final float PITCH_STEP_SIGMA = 3.0F;
   private static final float EASE_YAW_SPAN = 60.0F;
   private static final float EASE_PITCH_SPAN = 45.0F;
   private static final float EASE_FLOOR = 0.35F;
   private static final float OVERSHOOT_WINDOW = 6.0F;
   private static final float OVERSHOOT_MIN_STEP = 2.0F;
   private static final float OVERSHOOT_SIGMA = 0.6F;
   private static final float AIM_HEIGHT_SIGMA = 0.05F;
   private float maxYawStep = 35.0F;
   private float maxPitchStep = 22.0F;
   private float tolerance = 4.0F;
   private float aimHeight = 0.72F;
   private int targetId = Integer.MIN_VALUE;
   private float lastStepYaw;
   private float lastStepPitch;
   private float aimYaw;
   private float aimPitch;
   private boolean hasAim;

   public void setMaxYawStep(float maxYawStep) {
      this.maxYawStep = Math.max(1.0F, maxYawStep);
   }

   public void setMaxPitchStep(float maxPitchStep) {
      this.maxPitchStep = Math.max(1.0F, maxPitchStep);
   }

   public void setTolerance(float tolerance) {
      this.tolerance = Math.max(0.1F, tolerance);
   }

   public void setAimHeight(float aimHeight) {
      this.aimHeight = Mth.clamp(aimHeight, 0.1F, 0.95F);
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.lastStepYaw = 0.0F;
            this.lastStepPitch = 0.0F;
         }

         float[] aim = this.aimAt(player, target, targetEyePos);
         this.aimYaw = aim[0];
         this.aimPitch = aim[1];
         this.hasAim = true;
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         float deltaYaw = Mth.wrapDegrees(aim[0] - currentYaw);
         float deltaPitch = aim[1] - currentPitch;
         ThreadLocalRandom random = ThreadLocalRandom.current();
         float maxYaw = Math.max(1.0F, this.maxYawStep + (float)random.nextGaussian() * 4.0F);
         float maxPitch = Math.max(1.0F, this.maxPitchStep + (float)random.nextGaussian() * 3.0F);
         float easeYaw = Math.min(1.0F, Math.abs(deltaYaw) / 60.0F);
         float easePitch = Math.min(1.0F, Math.abs(deltaPitch) / 45.0F);
         float stepYaw = Math.copySign(Math.min(Math.abs(deltaYaw), maxYaw * (0.35F + 0.65F * easeYaw)), deltaYaw);
         float stepPitch = Math.copySign(Math.min(Math.abs(deltaPitch), maxPitch * (0.35F + 0.65F * easePitch)), deltaPitch);
         if (Math.abs(deltaYaw) < 6.0F && Math.abs(this.lastStepYaw) > 2.0F) {
            stepYaw += (float)random.nextGaussian() * 0.6F;
         }

         double gcd = RotationContext.gcdStep();
         stepYaw = quantize(stepYaw, deltaYaw, gcd);
         stepPitch = quantize(stepPitch, deltaPitch, gcd);
         RotationContext.setRotation(currentYaw + stepYaw, Mth.clamp(currentPitch + stepPitch, -90.0F, 90.0F));
         this.lastStepYaw = stepYaw;
         this.lastStepPitch = stepPitch;
      } else {
         this.reset();
      }
   }

   public boolean isOnTarget(LocalPlayer player) {
      if (this.hasAim && player != null) {
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         return Math.abs(Mth.wrapDegrees(this.aimYaw - currentYaw)) <= this.tolerance && Math.abs(this.aimPitch - currentPitch) <= this.tolerance;
      } else {
         return false;
      }
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.lastStepYaw = 0.0F;
      this.lastStepPitch = 0.0F;
      this.aimYaw = 0.0F;
      this.aimPitch = 0.0F;
      this.hasAim = false;
   }

   private float[] aimAt(LocalPlayer player, LivingEntity target, Vec3 targetEyePos) {
      Vec3 eye = player.getEyePosition(1.0F);
      double height = target.getBbHeight();
      double fraction = Mth.clamp(this.aimHeight + ThreadLocalRandom.current().nextGaussian() * 0.05F, 0.05, 0.98);
      double baseY = targetEyePos.y - target.getEyeHeight();
      double dx = targetEyePos.x - eye.x;
      double dy = baseY + height * fraction - eye.y;
      double dz = targetEyePos.z - eye.z;
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
}
