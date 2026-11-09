package win.furryhook.event.events.lifecycle;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import win.furryhook.event.Event;

public final class ClientStartEvent extends Event {
   private Minecraft client;

   public ClientStartEvent set(Minecraft client) {
      this.client = client;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }
}
