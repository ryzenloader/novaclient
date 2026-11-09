package win.furryhook.utils.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ElytraPhysics {
   public static final double HORIZONTAL_DRAG = 0.99F;
   public static final double VERTICAL_DRAG = 0.98F;
   public static final double TURN_RATE = 0.1;
   public static final double DEFAULT_GRAVITY = 0.08;

   private ElytraPhysics() {
   }

   public static Vec3 step(Vec3 motion, float yaw, float pitch, double gravity) {
      Vec3 look = Vec3.directionFromRotation(pitch, yaw);
      double pitchRad = pitch * (float) (Math.PI / 180.0);
      double horizLook = Math.sqrt(look.x * look.x + look.z * look.z);
      double horizSpeed = motion.horizontalDistance();
      double cosSq = Mth.square(Math.cos(pitchRad));
      Vec3 result = motion.add(0.0, gravity * (-1.0 + cosSq * 0.75), 0.0);
      if (result.y < 0.0 && horizLook > 0.0) {
         double gain = result.y * -0.1 * cosSq;
         result = result.add(look.x * gain / horizLook, gain, look.z * gain / horizLook);
      }

      if (pitchRad < 0.0 && horizLook > 0.0) {
         double lift = horizSpeed * -Math.sin(pitchRad) * 0.04;
         result = result.add(-look.x * lift / horizLook, lift * 3.2, -look.z * lift / horizLook);
      }

      if (horizLook > 0.0) {
         result = result.add((look.x / horizLook * horizSpeed - result.x) * 0.1, 0.0, (look.z / horizLook * horizSpeed - result.z) * 0.1);
      }

      return result.multiply(0.99F, 0.98F, 0.99F);
   }

   public static ElytraPhysics.Flight simulate(
      Vec3 startPos,
      Vec3 startMotion,
      double eyeHeight,
      double gravity,
      LivingEntity target,
      Vec3 targetMotion,
      double reach,
      float pitchOffset,
      double leadTicks,
      int ticks
   ) {
      Vec3 pos = startPos;
      Vec3 motion = startMotion;
      Vec3 targetPos = target.position();
      double targetHeight = target.getBbHeight();
      double targetWidth = target.getBbWidth();
      double aimHeight = targetHeight * 0.75;
      double minDistance = Double.MAX_VALUE;
      int minTick = 0;
      int ticksInReach = 0;
      int firstReachTick = -1;

      for (int tick = 1; tick <= ticks; tick++) {
         Vec3 eye = pos.add(0.0, eyeHeight, 0.0);
         Vec3 aim = targetPos.add(targetMotion.scale(leadTicks)).add(0.0, aimHeight, 0.0).subtract(eye);
         if (aim.lengthSqr() < 1.0E-8) {
            break;
         }

         float yaw = (float)(Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90.0);
         float pitch = (float)(-Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))));
         pitch = Mth.clamp(pitch + pitchOffset, -89.0F, 89.0F);
         motion = step(motion, yaw, pitch, gravity);
         pos = pos.add(motion);
         targetPos = targetPos.add(targetMotion);
         AABB box = new AABB(
            targetPos.x - targetWidth * 0.5,
            targetPos.y,
            targetPos.z - targetWidth * 0.5,
            targetPos.x + targetWidth * 0.5,
            targetPos.y + targetHeight,
            targetPos.z + targetWidth * 0.5
         );
         double distance = distanceToBox(box, pos.add(0.0, eyeHeight, 0.0));
         if (distance < minDistance) {
            minDistance = distance;
            minTick = tick;
         }

         if (distance <= reach) {
            ticksInReach++;
            if (firstReachTick < 0) {
               firstReachTick = tick;
            }
         }
      }

      return new ElytraPhysics.Flight(
         minDistance == Double.MAX_VALUE ? Double.MAX_VALUE : minDistance, minTick, ticksInReach, firstReachTick, motion.horizontalDistance()
      );
   }

   public static double distanceToBox(AABB box, Vec3 point) {
      double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
      double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
      double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   public record Flight(double minDistance, int minDistanceTick, int ticksInReach, int firstReachTick, double endSpeed) {
      public boolean reaches() {
         return this.ticksInReach > 0;
      }
   }
}
