package baritone.api;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;

public class Settings {
    public static class Setting<T> {
        public T value;
        public Setting(T v) { this.value = v; }
    }
    public final Setting<Boolean> allowBreak = new Setting<>(true);
    public final Setting<List<Block>> allowBreakAnyway = new Setting<>(new ArrayList<>());
    public final Setting<Boolean> allowPlace = new Setting<>(true);
    public final Setting<Boolean> allowSprint = new Setting<>(true);
    public final Setting<Boolean> freeLook = new Setting<>(false);
    public final Setting<Boolean> blockFreeLook = new Setting<>(false);
    public final Setting<Boolean> mineScanDroppedItems = new Setting<>(true);
    public final Setting<Integer> mineSearchRadius = new Setting<>(32);
    public final Setting<Integer> mineAoeLevel = new Setting<>(0);
    public final Setting<Integer> mineRegionMinX = new Setting<>(Integer.MIN_VALUE);
    public final Setting<Integer> mineRegionMinY = new Setting<>(Integer.MIN_VALUE);
    public final Setting<Integer> mineRegionMinZ = new Setting<>(Integer.MIN_VALUE);
    public final Setting<Integer> mineRegionMaxX = new Setting<>(Integer.MAX_VALUE);
    public final Setting<Integer> mineRegionMaxY = new Setting<>(Integer.MAX_VALUE);
    public final Setting<Integer> mineRegionMaxZ = new Setting<>(Integer.MAX_VALUE);
    public final Setting<Boolean> allowRightClick = new Setting<>(true);
    public final Setting<Integer> mineGoalUpdateInterval = new Setting<>(5);
    public final Setting<Integer> mineMaxOreLocationsCount = new Setting<>(6);
    public final Setting<Long> mineDropLoiterDurationMSThanksLouca = new Setting<>(0L);
    public final Setting<Boolean> exploreForBlocks = new Setting<>(false);
    public final Setting<List<Block>> blocksToDisallowBreaking = new Setting<>(new ArrayList<>());
    public final Setting<Boolean> chatControl = new Setting<>(false);
    public final Setting<Boolean> chatControlAnyway = new Setting<>(false);
    public final Setting<Boolean> renderPath = new Setting<>(true);
    public final Setting<Boolean> renderGoal = new Setting<>(true);
    public final Setting<Boolean> renderGoalXZBeacon = new Setting<>(true);
    public final Setting<Boolean> renderSelectionBoxes = new Setting<>(true);
    public final Setting<Boolean> renderSelection = new Setting<>(false);
    public final Setting<Color> colorSelection = new Setting<>(Color.CYAN);
    public final Setting<Color> colorSelectionPos1 = new Setting<>(Color.RED);
    public final Setting<Color> colorSelectionPos2 = new Setting<>(Color.BLUE);
    public final Setting<Boolean> chunkCaching = new Setting<>(false);
    public final Setting<Boolean> renderCachedChunks = new Setting<>(false);
}
