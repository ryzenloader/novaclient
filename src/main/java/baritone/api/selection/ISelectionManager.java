package baritone.api.selection;
import baritone.api.utils.BetterBlockPos;
public interface ISelectionManager {
    ISelection addSelection(BetterBlockPos p1, BetterBlockPos p2);
    void removeSelection(ISelection sel);
}
