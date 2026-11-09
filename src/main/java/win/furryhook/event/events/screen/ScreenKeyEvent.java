package win.furryhook.event.events.screen;

import lombok.Generated;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import win.furryhook.event.CancellableEvent;

public final class ScreenKeyEvent extends CancellableEvent {
   private Screen screen;
   private KeyEvent keyEvent;
   private ScreenKeyEvent.Action action;

   public ScreenKeyEvent set(Screen screen, KeyEvent keyEvent, ScreenKeyEvent.Action action) {
      this.screen = screen;
      this.keyEvent = keyEvent;
      this.action = action;
      return this;
   }

   @Generated
   public Screen getScreen() {
      return this.screen;
   }

   @Generated
   public KeyEvent getKeyEvent() {
      return this.keyEvent;
   }

   @Generated
   public ScreenKeyEvent.Action getAction() {
      return this.action;
   }

   public enum Action {
      PRESS,
      RELEASE;
   }
}
