package win.furryhook.event.events.screen;

import lombok.Generated;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import win.furryhook.event.CancellableEvent;

public final class ScreenMouseButtonEvent extends CancellableEvent {
   private Screen screen;
   private MouseButtonEvent mouseButtonEvent;
   private ScreenMouseButtonEvent.Action action;
   private double dragX;
   private double dragY;

   public ScreenMouseButtonEvent set(Screen screen, MouseButtonEvent mouseButtonEvent, ScreenMouseButtonEvent.Action action, double dragX, double dragY) {
      this.screen = screen;
      this.mouseButtonEvent = mouseButtonEvent;
      this.action = action;
      this.dragX = dragX;
      this.dragY = dragY;
      return this;
   }

   @Generated
   public Screen getScreen() {
      return this.screen;
   }

   @Generated
   public MouseButtonEvent getMouseButtonEvent() {
      return this.mouseButtonEvent;
   }

   @Generated
   public ScreenMouseButtonEvent.Action getAction() {
      return this.action;
   }

   @Generated
   public double getDragX() {
      return this.dragX;
   }

   @Generated
   public double getDragY() {
      return this.dragY;
   }

   public enum Action {
      CLICK,
      RELEASE,
      DRAG;
   }
}
