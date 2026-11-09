package win.furryhook.utils.combat.rotations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public class CustomRotation implements AuraRotation {
   private double reach = 3.0;
   private boolean throughWalls = false;
   private String preset = "Balanced";
   private float smoothness = 0.5F;
   private float rotationSpeed = 1.0F;
   private float prediction = 2.0F;
   private float aimHeight = 0.6F;
   private float lastYaw;
   private float lastPitch;
   private boolean initialized = false;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (player != null && target != null) {
         Vec3 aimPoint = targetEyePos.subtract(0.0, target.getBbHeight() * (1.0 - this.aimHeight), 0.0);
         Vec3 motion = target.getDeltaMovement();
         aimPoint = aimPoint.add(motion.x * this.prediction, motion.y * this.prediction, motion.z * this.prediction);
         float[] ideal = calcAngle(player.getEyePosition(), aimPoint);
         if (!this.initialized) {
            this.lastYaw = player.getYRot();
            this.lastPitch = player.getXRot();
            this.initialized = true;
         }

         float deltaYaw = wrapDegrees(ideal[0] - this.lastYaw);
         float deltaPitch = ideal[1] - this.lastPitch;
         deltaYaw *= this.rotationSpeed;
         deltaPitch *= this.rotationSpeed;
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

   public void loadPreset(String presetName) {
      this.preset = presetName;
      switch (presetName.toLowerCase()) {
         case "legit":
            this.smoothness = 0.35F;
            this.rotationSpeed = 0.8F;
            this.prediction = 1.5F;
            this.aimHeight = 0.6F;
            break;
         case "balanced":
            this.smoothness = 0.5F;
            this.rotationSpeed = 1.0F;
            this.prediction = 2.0F;
            this.aimHeight = 0.6F;
            break;
         case "aggressive":
            this.smoothness = 0.7F;
            this.rotationSpeed = 1.5F;
            this.prediction = 3.0F;
            this.aimHeight = 0.7F;
            break;
         case "smooth":
            this.smoothness = 0.25F;
            this.rotationSpeed = 0.6F;
            this.prediction = 1.0F;
            this.aimHeight = 0.6F;
      }
   }

   public void setSmoothingFactor(float factor) {
      this.smoothness = Math.max(0.1F, Math.min(1.0F, factor));
   }

   public void setRotationSpeed(float speed) {
      this.rotationSpeed = Math.max(0.1F, Math.min(3.0F, speed));
   }

   public void setPredictionAmount(float amount) {
      this.prediction = Math.max(0.0F, Math.min(5.0F, amount));
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
