package baritone.api.utils;
import net.minecraft.core.BlockPos;
public class BetterBlockPos extends BlockPos {
    public BetterBlockPos(int x, int y, int z) { super(x, y, z); }
    public static BetterBlockPos from(BlockPos p) { return new BetterBlockPos(p.getX(), p.getY(), p.getZ()); }
}
