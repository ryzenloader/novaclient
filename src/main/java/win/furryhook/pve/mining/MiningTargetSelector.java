package win.furryhook.pve.mining;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MiningTargetSelector {
   private MiningTargetSelector() {
   }

   public static <T> Optional<MiningTargetSelector.Candidate<T>> select(
      List<MiningTargetSelector.Candidate<T>> candidates, MiningTargetSelector.DiggingMode mode, boolean throughWalls
   ) {
      if (candidates == null || candidates.isEmpty()) {
         return Optional.empty();
      }

      MiningTargetSelector.DiggingMode resolvedMode = mode == null ? MiningTargetSelector.DiggingMode.EVERYONE : mode;
      return candidates.stream()
         .filter(MiningTargetSelector.Candidate::safe)
         .filter(MiningTargetSelector.Candidate::inWorkArea)
         .filter(candidate -> throughWalls || candidate.visible())
         .filter(candidate -> resolvedMode != MiningTargetSelector.DiggingMode.ONLY_ORE || candidate.ore())
         .min(comparator(resolvedMode));
   }

   public static <T> Optional<MiningTargetSelector.Candidate<T>> easierNeighbor(
      MiningTargetSelector.Candidate<T> primary, List<MiningTargetSelector.Candidate<T>> neighbors, boolean throughWalls
   ) {
      if (primary == null || neighbors == null || neighbors.isEmpty()) {
         return Optional.empty();
      }

      return neighbors.stream()
         .filter(MiningTargetSelector.Candidate::safe)
         .filter(MiningTargetSelector.Candidate::inWorkArea)
         .filter(candidate -> throughWalls || candidate.visible())
         .filter(candidate -> candidate.hardness() >= 0.0F)
         .filter(candidate -> candidate.hardness() < primary.hardness())
         .min(Comparator.<MiningTargetSelector.Candidate<T>>comparingDouble(MiningTargetSelector.Candidate::hardness).thenComparingDouble(MiningTargetSelector.Candidate::distanceSquared));
   }

   private static <T> Comparator<MiningTargetSelector.Candidate<T>> comparator(MiningTargetSelector.DiggingMode mode) {
      return Comparator.<MiningTargetSelector.Candidate<T>>comparingInt(
            candidate -> mode == MiningTargetSelector.DiggingMode.ORE_PRIORITY && candidate.ore() ? 0 : 1
         )
         .thenComparingInt(candidate -> verticalRank(candidate.verticalOffset()))
         .thenComparingDouble(MiningTargetSelector.Candidate::distanceSquared);
   }

   private static int verticalRank(int offset) {
      if (offset == 0) {
         return 0;
      } else {
         return offset > 0 ? 1 : 2;
      }
   }

   public record Candidate<T>(
      T value, boolean safe, boolean ore, boolean visible, boolean inWorkArea, int verticalOffset, double distanceSquared, float hardness
   ) {
   }

   public enum DiggingMode {
      EVERYONE,
      ORE_PRIORITY,
      ONLY_ORE;
   }
}
