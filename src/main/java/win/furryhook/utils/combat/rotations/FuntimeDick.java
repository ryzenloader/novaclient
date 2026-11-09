package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.combat.AuraRaycast;

public final class FuntimeDick implements AuraRotation {
   private static final float FULL_SPEED_ERROR = 55.0F;
   private static final float CAP_YAW = 46.0F;
   private static final float CAP_PITCH = 34.0F;
   private static final float PITCH_LAG = 0.82F;
   private static final float SPEED_JITTER = 0.09F;
   private static final int POINT_DRAWS = 6;
   private static final int POINT_BISECTIONS = 4;
   private static final int POINT_HOLD_TICKS = 3;
   private double reach = 3.0;
   private boolean throughWalls;
   private float minClosure = 0.3F;
   private float maxClosure = 0.86F;
   private float spread = 0.55F;
   private int holdTicks = 20;
   private int targetId = Integer.MIN_VALUE;
   private Vec3 point;
   private int pointTicks;
   private int holdLeft;
   private float heldYaw;
   private float heldPitch;
   private boolean hasHeld;

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public void setMinClosure(float minClosure) {
      this.minClosure = Mth.clamp(minClosure, 0.05F, 1.0F);
   }

   public void setMaxClosure(float maxClosure) {
      this.maxClosure = Mth.clamp(maxClosure, 0.05F, 1.0F);
   }

   public void setSpread(float spread) {
      this.spread = Mth.clamp(spread, 0.0F, 1.0F);
   }

   public void setHoldTicks(int holdTicks) {
      this.holdTicks = Math.max(0, holdTicks);
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.pointTicks = 0;
            this.point = null;
         }

         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         Vec3 eye = player.getEyePosition(1.0F);
         Vec3 aimPoint = this.aimPoint(player, target, targetEyePos, eye);
         float[] aim = rotationTo(eye, aimPoint);
         float deltaYaw = Mth.wrapDegrees(aim[0] - currentYaw);
         float deltaPitch = Mth.clamp(aim[1], -89.0F, 89.0F) - currentPitch;
         float error = (float)Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
         float closure = attackLikely ? 1.0F : this.closure(error);
         float stepYaw = Mth.clamp(deltaYaw * closure, -46.0F, 46.0F);
         float stepPitch = Mth.clamp(deltaPitch * closure * 0.82F, -34.0F, 34.0F);
         double gcd = RotationContext.gcdStep();
         stepYaw = quantize(stepYaw, deltaYaw, gcd);
         stepPitch = quantize(stepPitch, deltaPitch, gcd);
         float yaw = currentYaw + stepYaw;
         float pitch = Mth.clamp(currentPitch + stepPitch, -89.0F, 89.0F);
         RotationContext.setRotation(yaw, pitch);
         this.heldYaw = yaw;
         this.heldPitch = pitch;
         this.hasHeld = true;
         this.holdLeft = this.holdTicks;
         this.pointTicks++;
      } else {
         this.hold();
      }
   }

   public boolean isHolding() {
      return this.hasHeld && this.holdLeft > 0;
   }

   @Override
   public void onAttack() {
      this.pointTicks = 3;
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.point = null;
      this.pointTicks = 0;
      this.holdLeft = 0;
      this.hasHeld = false;
   }

   private void hold() {
      this.targetId = Integer.MIN_VALUE;
      this.point = null;
      this.pointTicks = 0;
      if (this.holdLeft > 0 && this.hasHeld) {
         this.holdLeft--;
         RotationContext.setRotation(this.heldYaw, this.heldPitch);
         if (this.holdLeft <= 0) {
            this.hasHeld = false;
         }
      } else {
         this.hasHeld = false;
      }
   }

   private float closure(float error) {
      float t = Mth.clamp(error / 55.0F, 0.0F, 1.0F);
      float eased = t * t * (3.0F - 2.0F * t);
      float base = Mth.lerp(eased, this.minClosure, this.maxClosure);
      float jitter = 1.0F + (float)ThreadLocalRandom.current().nextDouble(-0.09F, 0.09F);
      return Mth.clamp(base * jitter, 0.05F, 1.0F);
   }

   private Vec3 aimPoint(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, Vec3 eye) {
      if (this.point != null && this.pointTicks < 3 && this.hitsPoint(player, target, eye, this.point)) {
         return this.point;
      }

      AABB box = target.getBoundingBox().deflate(0.05);
      Vec3 centre = new Vec3(
         (box.minX + box.maxX) * 0.5, Mth.clamp(box.maxY - 0.28, box.minY + 0.1, Math.max(box.minY + 0.1, box.maxY - 0.05)), (box.minZ + box.maxZ) * 0.5
      );

      for (int draw = 0; draw < 6; draw++) {
         Vec3 candidate = this.draw(box, centre);
         if (this.hitsPoint(player, target, eye, candidate)) {
            this.point = candidate;
            this.pointTicks = 0;
            return candidate;
         }

         Vec3 pulled = candidate;

         for (int step = 0; step < 4; step++) {
            pulled = centre.add(pulled.subtract(centre).scale(0.5));
            if (this.hitsPoint(player, target, eye, pulled)) {
               this.point = pulled;
               this.pointTicks = 0;
               return pulled;
            }
         }
      }

      Vec3 fallback = AuraRaycast.findAimPoint(player, target, target.position(), this.reach, this.throughWalls, Vec3.ZERO);
      Vec3 resolved = fallback != null ? fallback : targetEyePos;
      this.point = resolved;
      this.pointTicks = 0;
      return resolved;
   }

   private Vec3 draw(AABB box, Vec3 centre) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      double halfX = Math.max(1.0E-4, box.getXsize() * 0.5 * this.spread);
      double halfZ = Math.max(1.0E-4, box.getZsize() * 0.5 * this.spread);
      double bandUp = Math.max(1.0E-4, 0.1 * this.spread);
      double bandDown = Math.max(2.0E-4, 0.55 * this.spread);
      return new Vec3(
         Mth.clamp(centre.x + random.nextDouble(-halfX, halfX), box.minX, box.maxX),
         Mth.clamp(centre.y + random.nextDouble(-bandDown, bandUp), box.minY, box.maxY),
         Mth.clamp(centre.z + random.nextDouble(-halfZ, halfZ), box.minZ, box.maxZ)
      );
   }

   private boolean hitsPoint(LocalPlayer player, LivingEntity target, Vec3 eye, Vec3 point) {
      float[] rotation = rotationTo(eye, point);
      return AuraRaycast.rotationIntersectsTarget(player, target, rotation[0], Mth.clamp(rotation[1], -89.0F, 89.0F), this.reach, 0.0, 0.0, this.throughWalls);
   }

   private static float quantize(float step, float delta, double gcd) {
      if (gcd < 1.0E-4) {
         return step;
      }

      if (Math.abs(step) > Math.abs(delta)) {
         step = delta;
      }

      float snapped = (float)(Math.round(step / gcd) * gcd);
      if (snapped == 0.0F && Math.abs(delta) >= gcd) {
         snapped = (float)(Math.signum(delta) * gcd);
      }

      return snapped;
   }

   private static float[] rotationTo(Vec3 eye, Vec3 point) {
      Vec3 diff = point.subtract(eye);
      double horizontal = Math.hypot(diff.x, diff.z);
      float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      float pitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, horizontal))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }
}
