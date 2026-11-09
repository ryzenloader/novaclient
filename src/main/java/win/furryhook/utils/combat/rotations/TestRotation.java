package win.furryhook.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import win.furryhook.context.RotationContext;

public final class TestRotation implements AuraRotation {
   private static final float MAX_YAW_STEP = 35.0F;
   private static final float MAX_PITCH_STEP = 20.0F;
   private static final float EASE_SACCADE = 0.68F;
   private static final float EASE_TRACK = 0.32F;
   private static final float EASE_HOLD = 0.18F;
   private static final float SACCADE_THRESHOLD = 6.0F;
   private static final float HOLD_THRESHOLD = 1.4F;
   private static final float TREMOR_YAW_HOLD = 0.14F;
   private static final float TREMOR_YAW_TRACK = 0.06F;
   private static final float TREMOR_PITCH_HOLD = 0.1F;
   private static final float TREMOR_PITCH_TRACK = 0.04F;
   private static final float IDLE_JITTER = 0.03F;
   private static final float BLOCKED_PENALTY = 25.0F;
   private static final double RAY_DISTANCE = 6.0;
   private static final double LEAD_TICKS = 1.0;
   private float smoothedYaw;
   private float smoothedPitch;
   private boolean initialized;
   private int acquireDelay;
   private LivingEntity lastTarget;
   private float lockedYaw;
   private float lockedPitch;
   private boolean hasLock;
   private float tremorPhase;
   private float idlePhase;
   private int overshootCooldown;
   private float overshootBias;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         if (target != this.lastTarget) {
            this.lastTarget = target;
            this.acquireDelay = 2 + random.nextInt(3);
            this.overshootCooldown = 0;
            this.overshootBias = 0.0F;
            this.hasLock = false;
         }

         if (!this.initialized) {
            this.smoothedYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
            this.smoothedPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
            this.initialized = true;
         }

         this.selectBestPoint(player, target, attackLikely);
         if (this.acquireDelay > 0) {
            this.acquireDelay--;
            RotationContext.setRotation(this.smoothedYaw, clampPitch(this.smoothedPitch));
         } else {
            float deltaYaw = Mth.wrapDegrees(this.lockedYaw - this.smoothedYaw);
            float deltaPitch = this.lockedPitch - this.smoothedPitch;
            float absDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch) * 0.5F;
            float ease = absDelta > 6.0F ? 0.68F : (absDelta > 1.4F ? 0.32F : 0.18F);
            float stepYaw = Mth.clamp(deltaYaw * ease, -35.0F, 35.0F);
            float stepPitch = Mth.clamp(deltaPitch * ease, -20.0F, 20.0F);
            if (!attackLikely && this.overshootCooldown <= 0 && Math.abs(deltaYaw) > 12.0F && random.nextFloat() < 0.15F) {
               this.overshootBias = Math.signum(deltaYaw) * (0.6F + random.nextFloat() * 1.0F);
               this.overshootCooldown = 12 + random.nextInt(20);
            } else if (this.overshootCooldown > 0) {
               this.overshootCooldown--;
               this.overshootBias *= 0.7F;
            } else {
               this.overshootBias = 0.0F;
            }

            this.smoothedYaw = Mth.wrapDegrees(this.smoothedYaw + stepYaw + this.overshootBias);
            this.smoothedPitch = clampPitch(this.smoothedPitch + stepPitch);
            float finalYaw = this.smoothedYaw;
            float finalPitch = this.smoothedPitch;
            if (!attackLikely) {
               this.tremorPhase += 0.37F;
               this.idlePhase += 0.11F;
               float tremorScale = absDelta > 6.0F ? 0.0F : (absDelta > 1.4F ? 1.0F : 1.3F);
               float tremorYawAmp = (absDelta > 1.4F ? 0.06F : 0.14F) * tremorScale;
               float tremorPitchAmp = (absDelta > 1.4F ? 0.04F : 0.1F) * tremorScale;
               float tremorY = (float)(Math.sin(this.tremorPhase) * 0.6 + Math.sin(this.tremorPhase * 1.7 + 0.9) * 0.4) * tremorYawAmp;
               float tremorP = (float)(Math.sin(this.tremorPhase * 1.3 + 0.4) * 0.6 + Math.sin(this.tremorPhase * 0.8) * 0.4) * tremorPitchAmp;
               float idleY = absDelta < 1.4F ? (float)Math.sin(this.idlePhase) * 0.03F : 0.0F;
               finalYaw += tremorY + idleY;
               finalPitch += tremorP;
            }

            RotationContext.setRotation(finalYaw, clampPitch(finalPitch));
         }
      } else {
         this.reset();
         RotationContext.clear();
      }
   }

   private void selectBestPoint(LocalPlayer player, LivingEntity target, boolean attackLikely) {
      Vec3 eye = player.getEyePosition();
      Vec3 vel = target.getDeltaMovement();
      double leadX = vel.x * 1.0;
      double leadY = vel.y * 1.0;
      double leadZ = vel.z * 1.0;
      AABB box = target.getBoundingBox().move(leadX, leadY, leadZ);
      double w = box.getXsize();
      double h = box.getYsize();
      double d = box.getZsize();
      double cx = (box.minX + box.maxX) * 0.5;
      double cz = (box.minZ + box.maxZ) * 0.5;
      double eyeY = box.minY + target.getEyeHeight();
      double chestY = box.minY + h * 0.72;
      double sternumY = box.minY + h * 0.6;
      double waistY = box.minY + h * 0.45;
      double sx = w * 0.28;
      double sz = d * 0.28;
      Vec3[] candidates = new Vec3[]{
         new Vec3(cx, sternumY, cz),
         new Vec3(cx, chestY, cz),
         new Vec3(cx, waistY, cz),
         new Vec3(cx, eyeY - 0.08, cz),
         new Vec3(cx + sx, chestY, cz + sz),
         new Vec3(cx - sx, chestY, cz + sz),
         new Vec3(cx + sx, chestY, cz - sz),
         new Vec3(cx - sx, chestY, cz - sz),
         new Vec3(cx + sx, waistY, cz),
         new Vec3(cx - sx, waistY, cz),
         new Vec3(cx, waistY, cz + sz),
         new Vec3(cx, waistY, cz - sz),
         closestPointOnBox(eye, box)
      };
      float bestScore = Float.POSITIVE_INFINITY;
      float bestYaw = this.lockedYaw;
      float bestPitch = this.lockedPitch;
      boolean bestHitsBox = false;
      boolean any = false;

      for (Vec3 point : candidates) {
         double dx = point.x - eye.x;
         double dy = point.y - eye.y;
         double dz = point.z - eye.z;
         double horiz = Math.sqrt(dx * dx + dz * dz);
         float pointYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
         float pointPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(dy, horiz))), -89.5F, 89.5F);
         float yawDelta = Math.abs(Mth.wrapDegrees(pointYaw - this.smoothedYaw));
         float pitchDelta = Math.abs(pointPitch - this.smoothedPitch);
         boolean hitsBox = rayHitsBox(eye, pointYaw, pointPitch, box);
         boolean lineClear = isVisible(player, eye, point);
         float score = yawDelta + pitchDelta * 1.2F;
         if (!lineClear) {
            score += 25.0F;
         }

         if (attackLikely && !hitsBox) {
            score += 50.0F;
         }

         boolean better = attackLikely ? hitsBox && !bestHitsBox || hitsBox == bestHitsBox && score < bestScore : score < bestScore;
         if (better) {
            bestScore = score;
            bestYaw = pointYaw;
            bestPitch = pointPitch;
            bestHitsBox = hitsBox;
            any = true;
         }
      }

      if (any) {
         this.lockedYaw = bestYaw;
         this.lockedPitch = bestPitch;
         this.hasLock = true;
      }
   }

   private static boolean rayHitsBox(Vec3 eye, float yaw, float pitch, AABB box) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      double cosPitch = Math.cos(pitchRad);
      double dx = -Math.sin(yawRad) * cosPitch;
      double dy = -Math.sin(pitchRad);
      double dz = Math.cos(yawRad) * cosPitch;
      Vec3 end = eye.add(dx * 6.0, dy * 6.0, dz * 6.0);
      return box.clip(eye, end).isPresent();
   }

   private static Vec3 closestPointOnBox(Vec3 eye, AABB box) {
      double x = Mth.clamp(eye.x, box.minX, box.maxX);
      double y = Mth.clamp(eye.y, box.minY, box.maxY);
      double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
      return new Vec3(x, y, z);
   }

   private static boolean isVisible(LocalPlayer player, Vec3 from, Vec3 to) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return true;
      }

      HitResult r = mc.level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));
      return r.getType() == Type.MISS;
   }

   private static float clampPitch(float p) {
      return Mth.clamp(p, -89.5F, 89.5F);
   }

   @Override
   public void reset() {
      this.initialized = false;
      this.acquireDelay = 0;
      this.lastTarget = null;
      this.hasLock = false;
      this.lockedYaw = 0.0F;
      this.lockedPitch = 0.0F;
      this.tremorPhase = 0.0F;
      this.idlePhase = 0.0F;
      this.overshootCooldown = 0;
      this.overshootBias = 0.0F;
   }
}
