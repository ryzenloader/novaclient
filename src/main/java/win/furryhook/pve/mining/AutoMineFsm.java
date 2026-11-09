package win.furryhook.pve.mining;

public final class AutoMineFsm {
   private AutoMineFsm() {
   }

   public static AutoMineFsm.State next(AutoMineFsm.State state, AutoMineFsm.Signal signal, boolean hasRoute, boolean hasDeposit, boolean usesServerWarp) {
      if (signal == AutoMineFsm.Signal.STOP) {
         return AutoMineFsm.State.IDLE;
      }

      if (signal == AutoMineFsm.Signal.FAIL) {
         return AutoMineFsm.State.ERROR;
      }

      if (signal == AutoMineFsm.Signal.UNSAFE && state != AutoMineFsm.State.IDLE && state != AutoMineFsm.State.ERROR) {
         return AutoMineFsm.State.PAUSED;
      }

      return switch (state) {
         case IDLE -> signal == AutoMineFsm.Signal.START ? startState(hasRoute, usesServerWarp) : state;
         case WARPING -> signal == AutoMineFsm.Signal.TELEPORT_DONE ? (hasRoute ? AutoMineFsm.State.ROUTING : AutoMineFsm.State.MINING) : state;
         case ROUTING -> signal == AutoMineFsm.Signal.ARRIVED ? AutoMineFsm.State.MINING : state;
         case MINING -> signal == AutoMineFsm.Signal.INVENTORY_FULL ? (hasDeposit ? AutoMineFsm.State.TO_DEPOSIT : AutoMineFsm.State.MINING) : state;
         case TO_DEPOSIT -> signal == AutoMineFsm.Signal.ARRIVED ? AutoMineFsm.State.OPENING_DEPOSIT : state;
         case OPENING_DEPOSIT -> signal == AutoMineFsm.Signal.CONTAINER_OPEN ? AutoMineFsm.State.DEPOSITING : state;
         case DEPOSITING -> signal == AutoMineFsm.Signal.DEPOSIT_DONE ? restartState(hasRoute, usesServerWarp) : state;
         case RETURNING -> signal == AutoMineFsm.Signal.ARRIVED ? AutoMineFsm.State.MINING : state;
         case PAUSED, ERROR -> state;
      };
   }

   public static AutoMineFsm.State resume(AutoMineFsm.State desiredState, boolean hasRoute, boolean usesServerWarp) {
      return desiredState != null
            && desiredState != AutoMineFsm.State.PAUSED
            && desiredState != AutoMineFsm.State.ERROR
            && desiredState != AutoMineFsm.State.IDLE
         ? desiredState
         : startState(hasRoute, usesServerWarp);
   }

   private static AutoMineFsm.State startState(boolean hasRoute, boolean usesServerWarp) {
      if (usesServerWarp) {
         return AutoMineFsm.State.WARPING;
      } else {
         return hasRoute ? AutoMineFsm.State.ROUTING : AutoMineFsm.State.MINING;
      }
   }

   private static AutoMineFsm.State restartState(boolean hasRoute, boolean usesServerWarp) {
      if (usesServerWarp) {
         return AutoMineFsm.State.WARPING;
      } else {
         return hasRoute ? AutoMineFsm.State.RETURNING : AutoMineFsm.State.MINING;
      }
   }

   public enum Signal {
      START,
      TELEPORT_DONE,
      ARRIVED,
      INVENTORY_FULL,
      CONTAINER_OPEN,
      DEPOSIT_DONE,
      UNSAFE,
      FAIL,
      STOP;
   }

   public enum State {
      IDLE,
      WARPING,
      ROUTING,
      MINING,
      TO_DEPOSIT,
      OPENING_DEPOSIT,
      DEPOSITING,
      RETURNING,
      PAUSED,
      ERROR;
   }
}
