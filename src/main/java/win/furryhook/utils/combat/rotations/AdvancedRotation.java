package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public final class AdvancedRotation implements AuraRotation {
   private static final float EASE_NEAR = 0.8F;
   private static final float EASE_FAR = 0.96F;
   private static final float NEAR_DISTANCE = 4.0F;
   private static final float FAR_DISTANCE = 40.0F;
   private static final float MAX_STEP_YAW = 42.0F;
   private static final float MAX_STEP_PITCH = 32.0F;
   private static final double LEAD_SECONDS = 0.12;
   private int targetId = Integer.MIN_VALUE;
   private float velYaw = 0.0F;
   private float velPitch = 0.0F;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.velYaw = 0.0F;
            this.velPitch = 0.0F;
         }

         Vec3 lead = target.getDeltaMovement().scale(2.4);
         Vec3 aimPoint = targetEyePos.add(lead);
         Vec3 diff = aimPoint.subtract(player.getEyePosition(1.0F));
         double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
         float rawYaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
         float rawPitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(diff.y, horizontal)), -90.0, 90.0);
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         float deltaYaw = Mth.wrapDegrees(rawYaw - currentYaw);
         float deltaPitch = rawPitch - currentPitch;
         float dist = (float)Math.hypot(deltaYaw, deltaPitch);
         float t = Mth.clamp((dist - 4.0F) / 36.0F, 0.0F, 1.0F);
         float ease = Mth.lerp(t, 0.8F, 0.96F);
         float stepYaw = deltaYaw * ease;
         float stepPitch = deltaPitch * ease;
         this.velYaw = this.velYaw * 0.12F + stepYaw * 0.88F;
         this.velPitch = this.velPitch * 0.12F + stepPitch * 0.88F;
         stepYaw = this.velYaw;
         stepPitch = this.velPitch;
         if (!attackLikely && dist > 1.0F) {
            stepYaw += randomRange(-0.35F, 0.35F);
            stepPitch += randomRange(-0.18F, 0.18F);
         }

         stepYaw = Mth.clamp(stepYaw, -42.0F, 42.0F);
         stepPitch = Mth.clamp(stepPitch, -32.0F, 32.0F);
         RotationContext.setRotation(currentYaw + stepYaw, Mth.clamp(currentPitch + stepPitch, -90.0F, 90.0F));
      } else {
         this.reset();
      }
   }

   @Override
   public void onAttack() {
      this.velYaw *= 0.3F;
      this.velPitch *= 0.3F;
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.velYaw = 0.0F;
      this.velPitch = 0.0F;
   }

   private static float randomRange(float min, float max) {
      return min + ThreadLocalRandom.current().nextFloat() * (max - min);
   }
}
