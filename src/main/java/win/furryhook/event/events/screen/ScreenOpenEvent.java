package win.furryhook.event.events.screen;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import win.furryhook.event.CancellableEvent;

public final class ScreenOpenEvent extends CancellableEvent {
   private Minecraft client;
   private Screen screen;

   public ScreenOpenEvent set(Minecraft client, Screen screen) {
      this.client = client;
      this.screen = screen;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }

   @Generated
   public Screen getScreen() {
      return this.screen;
   }
}
