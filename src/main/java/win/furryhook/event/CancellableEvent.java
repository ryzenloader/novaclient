package win.furryhook.event;

public abstract class CancellableEvent extends Event {
   private boolean cancelled;

   @Override
   public final boolean isCancelled() {
      return this.cancelled;
   }

   @Override
   protected void reset() {
      super.reset();
      this.cancelled = false;
   }

   public final void cancel() {
      if (this.isCompleted()) {
         throw new IllegalStateException("Cannot cancel an event that has already been completed.");
      }

      this.cancelled = true;
   }
}
