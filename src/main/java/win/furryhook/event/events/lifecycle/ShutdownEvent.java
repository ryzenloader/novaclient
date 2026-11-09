package win.furryhook.event.events.lifecycle;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import win.furryhook.event.Event;

public final class ShutdownEvent extends Event {
   private Minecraft client;

   public ShutdownEvent set(Minecraft client) {
      this.client = client;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }
}
