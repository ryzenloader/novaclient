package win.furryhook.utils.combat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AuraUtil {
   private AuraUtil() {
   }

   public static double getDistance(LocalPlayer player, LivingEntity target) {
      return getVector(player, target).length();
   }

   public static Vec3 getVector(LocalPlayer player, LivingEntity target) {
      Vec3 basePos = target.position();
      double wHalf = target.getBbWidth() / 2.0;
      double yMin = basePos.y;
      double yMax = basePos.y + target.getBbHeight();
      double playerEyeY = player.getEyeY();
      int steps = 10;
      Vec3 bestVector = null;
      double bestDistance = Double.MAX_VALUE;

      for (int i = 0; i <= steps; i++) {
         double y = yMin + (yMax - yMin) * ((double)i / steps);
         double xOffset = Mth.clamp(player.getX() - basePos.x, -wHalf, wHalf);
         double zOffset = Mth.clamp(player.getZ() - basePos.z, -wHalf, wHalf);
         Vec3 currentVector = new Vec3(basePos.x - player.getX() + xOffset, y - playerEyeY, basePos.z - player.getZ() + zOffset);
         double distance = currentVector.length();
         if (distance < bestDistance) {
            bestDistance = distance;
            bestVector = currentVector;
         }
      }

      if (bestVector == null) {
         bestVector = new Vec3(basePos.x - player.getX(), (yMin + yMax) / 2.0 - playerEyeY, basePos.z - player.getZ());
      }

      return bestVector;
   }

   public static boolean canCritical(LocalPlayer player) {
      return player.fallDistance > 0.0
         && !player.onGround()
         && !player.onClimbable()
         && !player.isInWater()
         && !player.isMobilityRestricted()
         && !player.isPassenger()
         && !player.isSprinting();
   }

   public static boolean isValidAttackCondition(LocalPlayer player, ClientLevel level) {
      return !player.hasEffect(MobEffects.LEVITATION)
         && !player.hasEffect(MobEffects.BLINDNESS)
         && !player.isEyeInFluid(FluidTags.WATER)
         && !player.isEyeInFluid(FluidTags.LAVA)
         && !isInWeb(player, level)
         && !player.getAbilities().flying
         && !player.onClimbable()
         && !player.isPassenger();
   }

   public static boolean isInWeb(LocalPlayer player, ClientLevel level) {
      AABB box = player.getBoundingBox();

      for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
         for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (level.getBlockState(pos).is(Blocks.COBWEB) && new AABB(pos).intersects(box)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static boolean isJumpBlockedByCeiling(LocalPlayer player, ClientLevel level) {
      return !player.isInWater() && !player.onClimbable() && !player.getAbilities().flying
         ? player.onGround() && isBlockAboveHead(player, level) && !canMoveUp(player, level)
         : false;
   }

   private static boolean isBlockAboveHead(LocalPlayer player, ClientLevel level) {
      double upHeight = !player.onGround() ? 1.5 : 2.5;
      return !level.noCollision(player, createBoundingBox(player, upHeight));
   }

   private static boolean canMoveUp(LocalPlayer player, ClientLevel level) {
      EntityDimensions dimensions = player.getDimensions(player.getPose());
      boolean crouchingAndJumping = player.getPose() == Pose.CROUCHING && player.input != null && player.input.keyPresses.jump();
      double upHeight = dimensions.height() + (crouchingAndJumping ? 0.05 : 0.2);
      return level.noCollision(player, createBoundingBox(player, upHeight));
   }

   private static AABB createBoundingBox(LocalPlayer player, double heightOffset) {
      EntityDimensions dimensions = player.getDimensions(player.getPose());
      return new AABB(
         player.getX() - 0.3, player.getY() + dimensions.height(), player.getZ() - 0.3, player.getX() + 0.3, player.getY() + heightOffset, player.getZ() + 0.3
      );
   }
}
