package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.combat.AuraRaycast;

public final class SpookyRotation implements AuraRotation {
   private static final float SPEED_MIN = 55.0F;
   private static final float SPEED_MAX = 120.0F;
   private static final float WIGGLE = 0.18F;
   private static final double POINT_NOISE = 0.07;
   private static final float STRIKE_YAW_ERROR = 14.0F;
   private static final int POINT_HOLD_MIN = 2;
   private static final int POINT_HOLD_MAX = 4;
   private static final double LATERAL_SPREAD = 0.38;
   private static double reach = 3.0;
   private static boolean throughWalls = false;
   private int targetId = Integer.MIN_VALUE;
   private int pointIndex;
   private int pointTicks;
   private int pointHold;
   private int pointSide = 1;
   private float speedYaw;
   private float pointYaw;
   private boolean hasPoint;

   public void setReach(double reach) {
      SpookyRotation.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      SpookyRotation.throughWalls = throughWalls;
   }

   public boolean isOnTarget(LocalPlayer player, LivingEntity target) {
      if (target != null && target.isAlive()) {
         float serverYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float serverPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         return AuraRaycast.rotationIntersectsTarget(player, target, serverYaw, serverPitch, reach, 0.0, 0.0, throughWalls);
      } else {
         return false;
      }
   }

   public boolean shouldStrike(LocalPlayer player, LivingEntity target) {
      if (!this.isOnTarget(player, target)) {
         return false;
      }

      if (!this.hasPoint) {
         return true;
      }

      float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
      float yawError = Math.abs(Mth.wrapDegrees(this.pointYaw - currentYaw));
      return yawError <= 14.0F;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.pointIndex = this.rollZone(random);
            this.pointTicks = 0;
            this.pointHold = 2 + random.nextInt(3);
            this.speedYaw = 55.0F + random.nextFloat() * 65.0F;
            this.hasPoint = false;
         }

         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         Vec3 eye = player.getEyePosition(1.0F);
         int zone = this.pointIndex % 7;
         Vec3 lateral = this.lateralPoint(target, eye, zone);
         float yawToPoint = yawTo(eye, lateral);
         if (this.pointTicks == 0 || !this.hasPoint) {
            this.pointYaw = yawToPoint;
            this.hasPoint = true;
         }

         float yawDelta = Mth.wrapDegrees(yawToPoint - currentYaw);
         float drift = Mth.lerp(random.nextFloat() * 0.9F + 0.05F, this.speedYaw, 55.0F + random.nextFloat() * 65.0F);
         this.speedYaw = Mth.clamp(drift, 38.5F, 132.0F);
         double gcd = RotationContext.gcdStep();
         float step = Mth.clamp(yawDelta, -this.speedYaw, this.speedYaw);
         float microJitter = quantize((float)((random.nextDouble() - 0.5) * 2.0 * 0.18F), gcd);
         float queuedYaw = quantize(step + microJitter, gcd);
         RotationContext.setRotation(currentYaw + queuedYaw, currentPitch);
         this.pointTicks++;
         if (this.pointTicks >= this.pointHold) {
            this.advancePoint(random);
         }
      } else {
         this.reset();
      }
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.pointIndex = 0;
      this.pointTicks = 0;
      this.pointHold = 0;
      this.pointSide = 1;
      this.speedYaw = 55.0F;
      this.hasPoint = false;
      this.pointYaw = 0.0F;
   }

   private int rollZone(ThreadLocalRandom random) {
      int r = random.nextInt(100);
      if (r < 40) {
         return 1;
      } else if (r < 70) {
         return 0;
      } else if (r < 86) {
         return 3 + random.nextInt(2);
      } else {
         return r < 96 ? 2 : 5 + random.nextInt(2);
      }
   }

   private Vec3 lateralPoint(LivingEntity target, Vec3 eye, int zone) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      AABB box = target.getBoundingBox().deflate(0.05);
      double centerX = (box.minX + box.maxX) * 0.5;
      double centerZ = (box.minZ + box.maxZ) * 0.5;
      double side = zone != 3 && zone != 5 ? -this.pointSide : this.pointSide;
      double dirX = centerX - eye.x;
      double dirZ = centerZ - eye.z;
      double length = Math.hypot(dirX, dirZ);
      if (length < 1.0E-6) {
         return new Vec3(centerX + noise(random), this.zoneY(target, zone), centerZ + noise(random));
      }

      double perpX = -dirZ / length;
      double perpZ = dirX / length;
      double spread = Math.min(box.getXsize(), box.getZsize()) * 0.38 * side;
      return new Vec3(
         Mth.clamp(centerX + perpX * spread + noise(random), box.minX, box.maxX),
         this.zoneY(target, zone),
         Mth.clamp(centerZ + perpZ * spread + noise(random), box.minZ, box.maxZ)
      );
   }

   private static double noise(ThreadLocalRandom random) {
      return (random.nextDouble() - 0.5) * 2.0 * 0.07;
   }

   private double zoneY(LivingEntity target, int zone) {
      AABB box = target.getBoundingBox();
      double minY = box.minY;
      double maxY = box.maxY;
      double h = maxY - minY;

      return switch (zone) {
         case 0 -> maxY - h * 0.07;
         default -> maxY - h * 0.4;
         case 2 -> minY + h * 0.32;
         case 3, 4 -> maxY - h * 0.24;
         case 5, 6 -> minY + h * 0.44;
      };
   }

   private void advancePoint(ThreadLocalRandom random) {
      this.pointIndex = (this.pointIndex + 1) % 7;
      this.pointTicks = 0;
      this.pointSide = -this.pointSide;
      this.pointHold = 2 + random.nextInt(3);
   }

   private static float quantize(float step, double gcd) {
      if (gcd < 1.0E-4) {
         return step;
      } else if (Math.abs(step) >= gcd) {
         return (float)(Math.round(step / gcd) * gcd);
      } else {
         return step == 0.0F ? 0.0F : (float)(Math.signum(step) * gcd);
      }
   }

   private static float yawTo(Vec3 eye, Vec3 point) {
      Vec3 diff = point.subtract(eye);
      return (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
   }
}
