package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public final class ReallyworldRotation implements AuraRotation {
   private static final float LERP_SPEED = 0.45F;
   private static final float NOISE_LERP_SPEED = 0.15F;
   private static final float NOISE_YAW_RANGE = 0.6F;
   private static final float NOISE_PITCH_RANGE = 0.4F;
   private float smoothedYaw;
   private float smoothedPitch;
   private float noiseYaw;
   private float noisePitch;
   private boolean initialized;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         Vec3 eye = player.getEyePosition();
         Vec3 delta = targetEyePos.subtract(eye);
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         float idealYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
         float idealPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(delta.y, horizontal))), -90.0F, 90.0F);
         if (!this.initialized) {
            this.smoothedYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
            this.smoothedPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
            this.initialized = true;
         }

         float deltaYaw = Mth.wrapDegrees(idealYaw - this.smoothedYaw);
         float deltaPitch = idealPitch - this.smoothedPitch;
         this.smoothedYaw = Mth.wrapDegrees(this.smoothedYaw + deltaYaw * 0.45F);
         this.smoothedPitch = Mth.clamp(this.smoothedPitch + deltaPitch * 0.45F, -90.0F, 90.0F);
         ThreadLocalRandom random = ThreadLocalRandom.current();
         float targetNoiseYaw = (float)random.nextDouble(-0.6F, 0.6F);
         float targetNoisePitch = (float)random.nextDouble(-0.4F, 0.4F);
         this.noiseYaw = this.noiseYaw + (targetNoiseYaw - this.noiseYaw) * 0.15F;
         this.noisePitch = this.noisePitch + (targetNoisePitch - this.noisePitch) * 0.15F;
         float finalYaw = this.smoothedYaw + this.noiseYaw;
         float finalPitch = Mth.clamp(this.smoothedPitch + this.noisePitch, -90.0F, 90.0F);
         RotationContext.setRotation(finalYaw, finalPitch);
      } else {
         this.reset();
         RotationContext.clear();
      }
   }

   @Override
   public void reset() {
      this.initialized = false;
      this.noiseYaw = 0.0F;
      this.noisePitch = 0.0F;
   }
}
