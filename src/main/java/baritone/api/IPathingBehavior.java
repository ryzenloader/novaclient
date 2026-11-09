package baritone.api;
import baritone.api.pathing.goals.Goal;
import java.util.Optional;
public interface IPathingBehavior {
    default boolean isPathing() { return false; }
    default boolean hasPath() { return false; }
    default Optional<?> getInProgress() { return Optional.empty(); }
    default Goal getGoal() { return null; }
    default Optional<Double> estimatedTicksToGoal() { return Optional.empty(); }
    default void cancelEverything() {}
}
