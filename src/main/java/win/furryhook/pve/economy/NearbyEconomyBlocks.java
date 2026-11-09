package win.furryhook.pve.economy;

import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class NearbyEconomyBlocks {
   private NearbyEconomyBlocks() {
   }

   public static BlockPos nearestCraftingTable(ClientLevel level, LocalPlayer player, int radius) {
      return nearest(level, player, radius, Math.min(16, radius), state -> state.is(Blocks.CRAFTING_TABLE));
   }

   public static BlockPos nearestSignedChest(ClientLevel level, LocalPlayer player, int radius, String label) {
      String target = EconomyTextParser.normalize(label);
      return target.isEmpty()
         ? null
         : nearest(
            level, player, radius, Math.min(16, radius), state -> state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL), target
         );
   }

   public static boolean adjacentSignContains(ClientLevel level, BlockPos block, String label) {
      if (level != null && block != null && !EconomyTextParser.normalize(label).isEmpty()) {
         for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(block.relative(direction)) instanceof SignBlockEntity sign
               && (contains(sign.getFrontText(), label) || contains(sign.getBackText(), label))) {
               return true;
            }
         }

         BlockState state = level.getBlockState(block);
         if (state.getBlock() instanceof ChestBlock
            && state.hasProperty(BlockStateProperties.CHEST_TYPE)
            && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            ChestType type = (ChestType)state.getValue(BlockStateProperties.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
               Direction facing = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
               Direction partnerDirection = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
               BlockPos partner = block.relative(partnerDirection);

               for (Direction direction : Direction.values()) {
                  if (level.getBlockEntity(partner.relative(direction)) instanceof SignBlockEntity sign
                     && (contains(sign.getFrontText(), label) || contains(sign.getBackText(), label))) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static BlockPos nearest(ClientLevel level, LocalPlayer player, int horizontalRadius, int verticalRadius, Predicate<BlockState> predicate) {
      return nearest(level, player, horizontalRadius, verticalRadius, predicate, null);
   }

   private static BlockPos nearest(
      ClientLevel level, LocalPlayer player, int horizontalRadius, int verticalRadius, Predicate<BlockState> predicate, String requiredSign
   ) {
      if (level != null && player != null) {
         int horizontal = Math.max(1, Math.min(32, horizontalRadius));
         int vertical = Math.max(1, Math.min(16, verticalRadius));
         BlockPos origin = player.blockPosition();
         MutableBlockPos cursor = new MutableBlockPos();
         BlockPos nearest = null;
         double nearestDistance = Double.POSITIVE_INFINITY;

         for (int y = -vertical; y <= vertical; y++) {
            for (int x = -horizontal; x <= horizontal; x++) {
               for (int z = -horizontal; z <= horizontal; z++) {
                  if (x * x + z * z <= horizontal * horizontal) {
                     cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     if (level.hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) {
                        BlockState state = level.getBlockState(cursor);
                        if (predicate.test(state) && (requiredSign == null || adjacentSignContains(level, cursor, requiredSign))) {
                           double distance = cursor.distToCenterSqr(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
                           if (distance < nearestDistance) {
                              nearestDistance = distance;
                              nearest = cursor.immutable();
                           }
                        }
                     }
                  }
               }
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   private static boolean contains(SignText text, String label) {
      if (text == null) {
         return false;
      }

      for (Component line : text.getMessages(false)) {
         if (line != null && EconomyTextParser.containsAny(line.getString(), label)) {
            return true;
         }
      }

      return false;
   }
}
