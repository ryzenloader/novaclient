package win.furryhook.pve;

public interface AutomationOwner {
   default String automationId() {
      return this.getClass().getSimpleName();
   }

   default void onAutomationRevoked(PveAutomationCoordinator.RevocationReason reason) {
   }
}
