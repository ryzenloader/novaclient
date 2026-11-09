package baritone.api;
import baritone.api.selection.ISelectionManager;
public interface IBaritone {
    ICustomGoalProcess getCustomGoalProcess();
    IMineProcess getMineProcess();
    IPathingBehavior getPathingBehavior();
    ISelectionManager getSelectionManager();
}
