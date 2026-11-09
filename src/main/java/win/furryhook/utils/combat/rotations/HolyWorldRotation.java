package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public final class HolyWorldRotation implements AuraRotation {
   private static final float HUMAN_LERP = 0.85F;
   private static final float JITTER_YAW = 0.09F;
   private static final float JITTER_PITCH = 0.05F;
   private int targetId = Integer.MIN_VALUE;
   private float velYaw;
   private float velPitch;
   private float aimHeight;
   private float aimBiasX;
   private float aimBiasZ;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.velYaw = 0.0F;
            this.velPitch = 0.0F;
            this.aimHeight = 0.42F + ThreadLocalRandom.current().nextFloat() * 0.3F;
            float lean = ThreadLocalRandom.current().nextFloat() * 0.05F;
            this.aimBiasX = (ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F) * lean;
            this.aimBiasZ = (ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F) * (0.03F + ThreadLocalRandom.current().nextFloat() * 0.02F);
         }

         Vec3 eye = player.getEyePosition();
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         float aimY = attackLikely ? target.getEyeHeight() * 0.92F : target.getBbHeight() * this.aimHeight;
         Vec3 aim = target.position().add(this.aimBiasX, aimY, this.aimBiasZ);
         if (attackLikely) {
            aim = aim.add(target.getDeltaMovement());
         }

         double dx = aim.x - eye.x;
         double dy = aim.y - eye.y;
         double dz = aim.z - eye.z;
         double horizontal = Math.sqrt(dx * dx + dz * dz);
         float targetYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
         float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
         float yawError = Mth.wrapDegrees(targetYaw - currentYaw);
         float pitchError = targetPitch - currentPitch;
         float angleDist = (float)Math.sqrt(yawError * yawError + pitchError * pitchError);
         float speedMul = 1.32F;
         float pendulumMul = 0.6666667F;
         float pitchPendulumMul = 0.5F;
         float catchUp = Mth.clamp(angleDist / 13.33F, 1.0F, 2.56F);
         float spring = 0.125F * catchUp * speedMul * pendulumMul;
         float damping = 0.92F;
         float maxStepYaw = 13.125F * speedMul;
         float maxStepPitch = 8.125F * speedMul * pitchPendulumMul;
         if (attackLikely) {
            float attackSpeedMul = 1.45F;
            spring = 0.144F * catchUp * attackSpeedMul * speedMul;
            damping = 0.94F;
            maxStepYaw = 14.25F * attackSpeedMul * speedMul;
            maxStepPitch = 8.75F * attackSpeedMul * speedMul * pitchPendulumMul;
         }

         this.velYaw += yawError * spring;
         this.velPitch += pitchError * spring * 0.72F * pitchPendulumMul;
         if (this.velYaw * yawError < 0.0F) {
            this.velYaw *= 0.55F;
         }

         if (this.velPitch * pitchError < 0.0F) {
            this.velPitch *= 0.55F;
         }

         this.velYaw *= damping;
         this.velPitch *= damping;
         this.velYaw = Mth.clamp(this.velYaw, -maxStepYaw, maxStepYaw);
         this.velPitch = Mth.clamp(this.velPitch, -maxStepPitch, maxStepPitch);
         float yaw = currentYaw + this.velYaw;
         float pitch = Mth.clamp(currentPitch + this.velPitch, -89.9F, 89.9F);
         float close = Mth.clamp((18.0F - angleDist) / 18.0F, 0.0F, 1.0F);
         if (close > 0.0F) {
            float pull = 0.85F * (0.35F + 0.65F * close) * (attackLikely ? 1.0F : 0.6F);
            yaw = Mth.lerp(pull, yaw, targetYaw);
            pitch = Mth.lerp(pull, pitch, targetPitch);
         }

         float jitterScale = attackLikely ? 0.35F : 1.0F;
         yaw = Mth.wrapDegrees(yaw + (ThreadLocalRandom.current().nextFloat() * 2.0F - 1.0F) * 0.09F * jitterScale);
         pitch = Mth.clamp(pitch + (ThreadLocalRandom.current().nextFloat() * 2.0F - 1.0F) * 0.05F * jitterScale, -89.9F, 89.9F);
         RotationContext.setRotation(yaw, pitch);
      } else {
         this.reset();
      }
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.velYaw = 0.0F;
      this.velPitch = 0.0F;
   }
}
