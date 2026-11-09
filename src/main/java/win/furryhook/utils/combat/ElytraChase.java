package win.furryhook.utils.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class ElytraChase {
   private static final double STEER_GAIN = 5.0;
   private static final double SPEED_MARGIN = 0.45;
   private static final double MAX_LEAD_TICKS = 40.0;
   private static final double MIN_CHASE_SPEED = 1.0;
   private static final float MAX_STEER_OFFSET = 78.0F;
   private static final double PITCH_PER_SPEED = 65.0;
   private static final float MAX_DIVE = 55.0F;
   private static final float MAX_CLIMB = 32.0F;
   private static final double BRAKE_DISTANCE = 25.0;
   private static final double HANDOVER_START = 22.0;
   public static final double HANDOVER_END = 6.0;
   private static final double THRUST_MARGIN = 0.08;
   private static final double GROUND_PROBE = 32.0;
   private static final double SAFE_CLEARANCE = 18.0;
   private static final double FLOOR_CLEARANCE = 5.0;

   private ElytraChase() {
   }

   public static ElytraChase.Solution solve(LocalPlayer player, LivingEntity target) {
      Vec3 eye = player.getEyePosition();
      Vec3 velocity = player.getDeltaMovement();
      double speed = velocity.length();
      Vec3 targetMotion = motionOf(target);
      double targetSpeed = targetMotion.length();
      double wantSpeed = Math.max(1.0, targetSpeed * 1.45 + 0.1);
      double solveSpeed = Math.max(speed, wantSpeed);
      Vec3 base = target.position().add(0.0, target.getBbHeight() * 0.75, 0.0);
      Vec3 relative = base.subtract(eye);
      double lead = leadTicks(relative, targetMotion, solveSpeed);
      Vec3 intercept = base.add(targetMotion.scale(lead));
      Vec3 toIntercept = intercept.subtract(eye);
      double distance = toIntercept.length();
      if (distance < 1.0E-4) {
         return direct(player, eye, intercept, speed, wantSpeed, lead);
      }

      Vec3 desired = toIntercept.scale(1.0 / distance);
      Vec3 heading = speed > 1.0E-4 ? velocity.scale(1.0 / speed) : Vec3.directionFromRotation(player.getXRot(), player.getYRot());
      float headingYaw = yawOf(heading);
      float desiredYaw = yawOf(desired);
      float desiredPitch = pitchOf(desired);
      float yawError = Mth.wrapDegrees(desiredYaw - headingYaw);
      float steeredYaw = headingYaw + Mth.clamp((float)(yawError * 5.0), -78.0F, 78.0F);
      double speedError = wantSpeed - speed;
      float throttle = (float)Mth.clamp(speedError * 65.0, -32.0, 55.0);
      if (throttle > 0.0F) {
         throttle *= (float)diveAllowance(player);
      } else if (distance > 25.0) {
         throttle = 0.0F;
      }

      float steeredPitch = Mth.clamp(desiredPitch + throttle, -80.0F, 80.0F);
      float blend = (float)Mth.clamp((distance - 6.0) / 16.0, 0.0, 1.0);
      float finalYaw = desiredYaw + Mth.wrapDegrees(steeredYaw - desiredYaw) * blend;
      float finalPitch = Mth.clamp(desiredPitch + (steeredPitch - desiredPitch) * blend, -90.0F, 90.0F);
      Vec3 look = Vec3.directionFromRotation(finalPitch, finalYaw);
      Vec3 aimPoint = eye.add(look.scale(Math.max(distance, 4.0)));
      return new ElytraChase.Solution(aimPoint, look, desired, intercept, distance, speed, wantSpeed, lead, yawError);
   }

   private static ElytraChase.Solution direct(LocalPlayer player, Vec3 eye, Vec3 intercept, double speed, double wantSpeed, double lead) {
      Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
      return new ElytraChase.Solution(eye.add(look.scale(4.0)), look, look, intercept, 0.0, speed, wantSpeed, lead, 0.0F);
   }

   private static double leadTicks(Vec3 relative, Vec3 targetMotion, double speed) {
      double a = targetMotion.lengthSqr() - speed * speed;
      double b = 2.0 * relative.dot(targetMotion);
      double c = relative.lengthSqr();
      double fallback = speed > 1.0E-4 ? Math.sqrt(c) / speed : 0.0;
      double ticks;
      if (Math.abs(a) < 1.0E-6) {
         ticks = Math.abs(b) < 1.0E-6 ? fallback : -c / b;
      } else {
         double discriminant = b * b - 4.0 * a * c;
         if (discriminant < 0.0) {
            ticks = fallback;
         } else {
            double root = Math.sqrt(discriminant);
            ticks = smallestPositive((-b - root) / (2.0 * a), (-b + root) / (2.0 * a), fallback);
         }
      }

      if (!Double.isFinite(ticks) || ticks < 0.0) {
         ticks = fallback;
      }

      return Mth.clamp(ticks, 0.0, 40.0);
   }

   private static double smallestPositive(double first, double second, double fallback) {
      double low = Math.min(first, second);
      double high = Math.max(first, second);
      if (low > 1.0E-4) {
         return low;
      } else {
         return high > 1.0E-4 ? high : fallback;
      }
   }

   private static double diveAllowance(LocalPlayer player) {
      double clearance = groundClearance(player);
      return Mth.clamp((clearance - 5.0) / 13.0, 0.0, 1.0);
   }

   private static double groundClearance(LocalPlayer player) {
      Vec3 from = player.position();
      Vec3 to = from.subtract(0.0, 32.0, 0.0);
      HitResult hit = player.level().clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));
      return hit.getType() == Type.MISS ? 32.0 : Math.max(0.0, from.y - hit.getLocation().y);
   }

   public static Vec3 motionOf(LivingEntity target) {
      Vec3 delta = target.position().subtract(new Vec3(target.xOld, target.yOld, target.zOld));
      return delta.lengthSqr() > 1.0E-8 ? delta : target.getDeltaMovement();
   }

   public static float yawOf(Vec3 direction) {
      return (float)(Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
   }

   public static float pitchOf(Vec3 direction) {
      return (float)(-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
   }

   public record Solution(
      Vec3 aimPoint, Vec3 look, Vec3 desired, Vec3 intercept, double distance, double speed, double wantSpeed, double leadTicks, float headingError
   ) {
      public boolean wantsThrust() {
         return this.speed + 0.08 < this.wantSpeed;
      }
   }
}
