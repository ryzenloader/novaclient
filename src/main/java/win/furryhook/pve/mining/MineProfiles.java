package win.furryhook.pve.mining;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.pve.server.ServerProfile;

public final class MineProfiles {
   public static final String MODE_AUTO = "Auto";
   public static final String MODE_FUNTIME = "FunTime";
   public static final String MODE_HOLYWORLD = "HolyWorld";
   public static final String MODE_NONE = "None";
   public static final MineProfiles.Profile FUNTIME_MAIN = profile("funtime-main", ServerProfile.FUNTIME, -86, 72, -5, -66, 81, 15);
   public static final MineProfiles.Profile HOLYWORLD_FIRST = profile("holyworld-first", ServerProfile.HOLYWORLD, 43, 73, 38, 61, 82, 56);
   public static final MineProfiles.Profile HOLYWORLD_SECOND = profile("holyworld-second", ServerProfile.HOLYWORLD, 43, 70, 74, 61, 82, 92);
   private static final List<MineProfiles.Profile> HOLYWORLD_MINES = List.of(HOLYWORLD_FIRST, HOLYWORLD_SECOND);

   private MineProfiles() {
   }

   public static Optional<MineProfiles.Profile> select(String mode, ServerProfile detected, BlockPos playerPosition) {
      String normalized = mode == null ? "Auto".toLowerCase(Locale.ROOT) : mode.trim().toLowerCase(Locale.ROOT);

      return switch (normalized) {
         case "funtime" -> Optional.of(FUNTIME_MAIN);
         case "holyworld" -> nearestHolyWorld(playerPosition);
         case "none", "generic" -> Optional.empty();
         default -> switch (detected) {
            case FUNTIME -> Optional.of(FUNTIME_MAIN);
            case HOLYWORLD -> nearestHolyWorld(playerPosition);
            default -> Optional.empty();
         }
      };
   }

   public static List<MineProfiles.Profile> holyWorldMines() {
      return HOLYWORLD_MINES;
   }

   private static Optional<MineProfiles.Profile> nearestHolyWorld(BlockPos position) {
      return position == null
         ? Optional.of(HOLYWORLD_FIRST)
         : HOLYWORLD_MINES.stream().min(Comparator.comparingDouble(profile -> profile.distanceToSqr(position)));
   }

   private static MineProfiles.Profile profile(String id, ServerProfile server, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      return new MineProfiles.Profile(
         id,
         server,
         new BlockPos(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ)),
         new BlockPos(Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ))
      );
   }

   public record Profile(String id, ServerProfile server, BlockPos min, BlockPos max) {
      public AABB box() {
         return new AABB(this.min.getX(), this.min.getY(), this.min.getZ(), this.max.getX() + 1.0, this.max.getY() + 1.0, this.max.getZ() + 1.0);
      }

      public double distanceToSqr(BlockPos position) {
         return this.box().distanceToSqr(Vec3.atCenterOf(position));
      }

      public int topY() {
         return this.max.getY();
      }

      public int bottomY() {
         return this.min.getY();
      }
   }
}
