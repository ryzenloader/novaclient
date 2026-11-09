package baritone.api;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
public interface IMineProcess {
    void mine(int quantity, Block... blocks);
    default boolean isActive() { return false; }
    default List<BlockPos> knownTargets() { return Collections.emptyList(); }
}
