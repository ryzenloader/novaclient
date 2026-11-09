package win.furryhook.utils.combat.rotations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public class FTNewRotation implements AuraRotation {
   private double reach = 3.0;
   private boolean throughWalls = false;
   private float smoothness = 0.35F;
   private float prediction = 2.0F;
   private float lastYaw;
   private float lastPitch;
   private boolean initialized = false;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (player != null && target != null) {
         Vec3 motion = target.getDeltaMovement();
         Vec3 predictedPos = targetEyePos.add(motion.x * this.prediction, motion.y * this.prediction, motion.z * this.prediction);
         float[] ideal = calcAngle(player.getEyePosition(), predictedPos);
         if (!this.initialized) {
            this.lastYaw = player.getYRot();
            this.lastPitch = player.getXRot();
            this.initialized = true;
         }

         float deltaYaw = wrapDegrees(ideal[0] - this.lastYaw);
         float deltaPitch = ideal[1] - this.lastPitch;
         float targetYaw = this.lastYaw + deltaYaw * this.smoothness;
         float targetPitch = this.lastPitch + deltaPitch * this.smoothness;
         RotationContext.setRotation(targetYaw, targetPitch);
         this.lastYaw = targetYaw;
         this.lastPitch = targetPitch;
      }
   }

   @Override
   public void reset() {
      this.initialized = false;
   }

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public void setSmoothness(float smoothness) {
      this.smoothness = Math.max(0.1F, Math.min(1.0F, smoothness));
   }

   public void setPrediction(float prediction) {
      this.prediction = Math.max(0.0F, Math.min(5.0F, prediction));
   }

   private static float wrapDegrees(float angle) {
      angle %= 360.0F;
      if (angle >= 180.0F) {
         angle -= 360.0F;
      }

      if (angle < -180.0F) {
         angle += 360.0F;
      }

      return angle;
   }

   private static float[] calcAngle(Vec3 from, Vec3 to) {
      double diffX = to.x - from.x;
      double diffY = to.y - from.y;
      double diffZ = to.z - from.z;
      double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
      float yaw = (float)(Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
      float pitch = (float)(-(Math.atan2(diffY, dist) * 180.0 / Math.PI));
      return new float[]{yaw, pitch};
   }
}
