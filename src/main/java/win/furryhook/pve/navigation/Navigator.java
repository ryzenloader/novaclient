package win.furryhook.pve.navigation;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public interface Navigator {
   boolean isAvailable();

   void begin(NavigationOptions var1);

   void pathTo(BlockPos var1, int var2);

   void mine(int var1, Block... var2);

   void setMineBounds(BlockPos var1, BlockPos var2);

   default void setMineRenderColor(int argb) {
   }

   default void setMineAoeLevel(int level) {
   }

   boolean isPathing();

   boolean isMining();

   List<BlockPos> miningTargets();

   Optional<Double> estimatedTicksToGoal();

   Optional<BlockPos> currentGoal();

   String diagnostics();

   void cancel();

   void end();
}
