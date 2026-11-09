package win.furryhook.utils.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class AuraRaycast {
   private static final float PARTIAL_TICK = 1.0F;
   private static final double EPSILON = 1.0E-7;

   private AuraRaycast() {
   }

   public static boolean rotationIntersectsTarget(
      LocalPlayer player, LivingEntity target, float yaw, float pitch, double maxReach, double minReach, double margin, boolean throughWalls
   ) {
      Vec3 eye = player.getEyePosition(1.0F);
      Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
      double castDistance = maxReach + margin;
      Vec3 end = eye.add(direction.scale(castDistance));
      AABB box = target.getBoundingBox().inflate(target.getPickRadius());
      if (box.distanceToSqr(eye) <= 1.0E-7) {
         return minReach - margin <= 1.0E-7;
      }

      Optional<Vec3> clip = box.clip(eye, end);
      if (clip.isEmpty()) {
         return false;
      }

      Vec3 hit = clip.get();
      double distance = hit.distanceTo(eye);
      return !(distance > castDistance + 1.0E-7) && !(distance < minReach - margin - 1.0E-7) ? throughWalls || isBlockPathClear(player, eye, hit) : false;
   }

   public static boolean canSeeTarget(LocalPlayer player, LivingEntity target, double range) {
      return findAimPoint(player, target, target.position(), range, false, Vec3.ZERO) != null;
   }

   public static Vec3 findAimPoint(LocalPlayer player, LivingEntity target, Vec3 predictedPosition, double range, boolean throughWalls, Vec3 jitter) {
      Vec3 eye = player.getEyePosition(1.0F);
      Vec3 movement = predictedPosition.subtract(target.position());
      AABB box = target.getBoundingBox().move(movement).inflate(target.getPickRadius());
      double rangeSqr = range * range;
      if (box.distanceToSqr(eye) <= 1.0E-7) {
         Vec3 center = box.getCenter();
         if (center.distanceToSqr(eye) <= 1.0E-7) {
            center = eye.add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(0.01));
         }

         return center;
      } else {
         double targetEyeY = predictedPosition.y + target.getEyeHeight();
         double torsoY = Mth.clamp(targetEyeY - 0.15, box.minY + 0.1, box.maxY - 0.1);
         Vec3 centerAim = new Vec3((box.minX + box.maxX) * 0.5, torsoY, (box.minZ + box.maxZ) * 0.5);
         Vec3 primaryCandidate = new Vec3(
            Mth.clamp(centerAim.x + jitter.x * 0.25, box.minX, box.maxX),
            Mth.clamp(centerAim.y + jitter.y * 0.25, box.minY, box.maxY),
            Mth.clamp(centerAim.z + jitter.z * 0.25, box.minZ, box.maxZ)
         );
         if (!(primaryCandidate.distanceToSqr(eye) <= rangeSqr + 1.0E-7) || !throughWalls && !isBlockPathClear(player, eye, primaryCandidate)) {
            List<Vec3> candidates = aimCandidates(box, eye, targetEyeY);
            candidates.sort(Comparator.comparingDouble(primaryCandidate::distanceToSqr));

            for (Vec3 candidate : candidates) {
               if (!(candidate.distanceToSqr(eye) > rangeSqr + 1.0E-7) && (throughWalls || isBlockPathClear(player, eye, candidate))) {
                  return candidate;
               }
            }

            return null;
         } else {
            return primaryCandidate;
         }
      }
   }

   public static double predictedHitboxDistanceSqr(LocalPlayer player, LivingEntity target, Vec3 predictedPosition) {
      Vec3 movement = predictedPosition.subtract(target.position());
      AABB box = target.getBoundingBox().move(movement).inflate(target.getPickRadius());
      return box.distanceToSqr(player.getEyePosition(1.0F));
   }

   private static boolean isBlockPathClear(LocalPlayer player, Vec3 from, Vec3 to) {
      BlockHitResult blockHit = player.level().clip(new ClipContext(from, to, Block.OUTLINE, Fluid.NONE, player));
      return blockHit.getType() == Type.MISS || blockHit.getLocation().distanceToSqr(from) + 1.0E-7 >= to.distanceToSqr(from);
   }

   private static List<Vec3> aimCandidates(AABB box, Vec3 eye, double predictedEyeY) {
      List<Vec3> points = new ArrayList<>(7);
      points.add(box.getCenter());
      points.add(new Vec3((box.minX + box.maxX) * 0.5, Mth.clamp(predictedEyeY, box.minY, box.maxY), (box.minZ + box.maxZ) * 0.5));
      points.add(new Vec3(eye.x, box.minY + (box.maxY - box.minY) * 0.75, eye.z));
      points.add(new Vec3(box.minX, (box.minY + box.maxY) * 0.5, box.minZ));
      points.add(new Vec3(box.maxX, (box.minY + box.maxY) * 0.5, box.maxZ));
      points.add(new Vec3(box.minX, (box.minY + box.maxY) * 0.5, box.maxZ));
      points.add(new Vec3(box.maxX, (box.minY + box.maxY) * 0.5, box.minZ));
      return points;
   }
}
