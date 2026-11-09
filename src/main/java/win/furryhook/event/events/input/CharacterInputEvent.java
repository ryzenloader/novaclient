package win.furryhook.event.events.input;

import lombok.Generated;
import win.furryhook.event.CancellableEvent;

public final class CharacterInputEvent extends CancellableEvent {
   private final long window;
   private final int codePoint;

   public CharacterInputEvent(long window, int codePoint) {
      this.window = window;
      this.codePoint = codePoint;
   }

   @Generated
   public long getWindow() {
      return this.window;
   }

   @Generated
   public int getCodePoint() {
      return this.codePoint;
   }
}
