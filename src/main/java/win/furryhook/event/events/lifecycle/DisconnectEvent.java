package win.furryhook.event.events.lifecycle;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import win.furryhook.event.Event;

public final class DisconnectEvent extends Event {
   private Minecraft client;
   private Screen screen;
   private boolean transferring;
   private boolean resetting;

   public DisconnectEvent set(Minecraft client, Screen screen, boolean transferring, boolean resetting) {
      this.client = client;
      this.screen = screen;
      this.transferring = transferring;
      this.resetting = resetting;
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

   @Generated
   public boolean isTransferring() {
      return this.transferring;
   }

   @Generated
   public boolean isResetting() {
      return this.resetting;
   }
}
