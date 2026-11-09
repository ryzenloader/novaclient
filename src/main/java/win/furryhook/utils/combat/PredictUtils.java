package win.furryhook.utils.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class PredictUtils {
   private PredictUtils() {
   }

   public static Vec3 predict(LivingEntity entity, Vec3 pos, float ticks) {
      if (Math.hypot(entity.getX() - entity.xOld, entity.getZ() - entity.zOld) * 20.0 <= 5.0 && (entity.getY() - entity.yOld) * 20.0 <= 5.0) {
         return pos;
      }

      float prevPitchRad = (entity.getXRot() + (entity.xRotO - entity.getXRot())) * (float) (Math.PI / 180.0);
      float prevYawRad = -(entity.getYRot() + (entity.yHeadRotO - entity.getYRot())) * (float) (Math.PI / 180.0);
      float cosYaw = Mth.cos(prevYawRad);
      float sinYaw = Mth.sin(prevYawRad);
      float cosPitch = Mth.cos(prevPitchRad);
      float sinPitch = Mth.sin(prevPitchRad);
      Vec3 motion = entity.getDeltaMovement();
      Vec3 look = new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
      float pitchRad = (float)(entity.getXRot() * 0.017453293005625408);
      double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
      double motionHorizontal = motion.horizontalDistance();
      boolean falling = motion.y <= 0.0;
      double gravity = falling && entity.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(entity.getGravity(), 0.01) : entity.getGravity();
      double pitchFactor = Mth.square(Math.cos(pitchRad));
      motion = motion.add(0.0, gravity * (-1.0 + pitchFactor * 0.75), 0.0);
      if (motion.y < 0.0 && lookHorizontal > 0.0) {
         double delta = motion.y * -0.1 * pitchFactor;
         motion = motion.add(look.x * delta / lookHorizontal, delta, look.z * delta / lookHorizontal);
      }

      if (pitchRad < 0.0F && lookHorizontal > 0.0) {
         double delta = motionHorizontal * -Mth.sin(pitchRad) * 0.04;
         motion = motion.add(-look.x * delta / lookHorizontal, delta * 3.2, -look.z * delta / lookHorizontal);
      }

      if (lookHorizontal > 0.0) {
         motion = motion.add((look.x / lookHorizontal * motionHorizontal - motion.x) * 0.1, 0.0, (look.z / lookHorizontal * motionHorizontal - motion.z) * 0.1);
      }

      Vec3 step = motion.multiply(0.99F, 0.98F, 0.99F);
      return pos.add(step.scale(ticks)).add(0.0, 0.35F, 0.0);
   }
}
