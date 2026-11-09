package win.furryhook.utils.combat.rotations;

import java.util.Random;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public class FunTestRotation implements AuraRotation {
   private final Random random = new Random();
   private double reach = 3.0;
   private boolean throughWalls = false;
   private float jitterStrength = 0.8F;
   private float smoothing = 0.4F;
   private long lastJitterTime = 0L;
   private float jitterFrequency = 150.0F;
   private float jitterYaw = 0.0F;
   private float jitterPitch = 0.0F;
   private int jitterPhase = 0;
   private int attackCount = 0;
   private float lastYaw;
   private float lastPitch;
   private boolean initialized = false;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (player != null && target != null) {
         float[] ideal = calcAngle(player.getEyePosition(), targetEyePos);
         long now = System.currentTimeMillis();
         if ((float)(now - this.lastJitterTime) >= this.jitterFrequency) {
            this.updateJitter(player, target);
            this.lastJitterTime = now;
         }

         float targetYaw = ideal[0] + this.jitterYaw;
         float targetPitch = ideal[1] + this.jitterPitch;
         if (!this.initialized) {
            this.lastYaw = player.getYRot();
            this.lastPitch = player.getXRot();
            this.initialized = true;
         }

         float deltaYaw = wrapDegrees(targetYaw - this.lastYaw);
         float deltaPitch = targetPitch - this.lastPitch;
         float smoothedYaw = this.lastYaw + deltaYaw * this.smoothing;
         float smoothedPitch = this.lastPitch + deltaPitch * this.smoothing;
         smoothedPitch = Math.max(-90.0F, Math.min(90.0F, smoothedPitch));
         RotationContext.setRotation(smoothedYaw, smoothedPitch);
         this.lastYaw = smoothedYaw;
         this.lastPitch = smoothedPitch;
      }
   }

   private void updateJitter(LocalPlayer player, LivingEntity target) {
      this.jitterPhase = (this.jitterPhase + 1) % 4;
      float distance = player.distanceTo(target);
      float adaptiveFactor = Math.min(1.0F, distance / 4.0F) * 0.5F + 0.5F;
      switch (this.jitterPhase) {
         case 0:
         case 2:
            this.jitterYaw = this.random.nextFloat() * this.jitterStrength * adaptiveFactor;
            break;
         case 1:
         case 3:
            this.jitterYaw = -this.random.nextFloat() * this.jitterStrength * adaptiveFactor;
      }

      if (this.attackCount % 2 == 0) {
         if (this.jitterPhase < 2) {
            this.jitterPitch = -this.random.nextFloat() * this.jitterStrength * 0.5F * adaptiveFactor;
         } else {
            this.jitterPitch = this.random.nextFloat() * this.jitterStrength * 0.5F * adaptiveFactor;
         }
      }
   }

   @Override
   public void reset() {
      this.initialized = false;
      this.jitterYaw = 0.0F;
      this.jitterPitch = 0.0F;
      this.jitterPhase = 0;
      this.attackCount = 0;
   }

   @Override
   public void onAttack() {
      this.attackCount++;
   }

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public void setJitterStrength(float strength) {
      this.jitterStrength = Math.max(0.0F, Math.min(3.0F, strength));
   }

   public void setJitterFrequency(float frequency) {
      this.jitterFrequency = frequency;
   }

   public void setSmoothing(float smoothing) {
      this.smoothing = Math.max(0.1F, Math.min(1.0F, smoothing));
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
