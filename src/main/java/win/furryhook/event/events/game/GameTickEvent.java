package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import win.furryhook.event.Event;

public final class GameTickEvent extends Event {
   private Minecraft client;
   private TickContext context;

   public GameTickEvent set(Minecraft client, TickContext context) {
      this.client = client;
      this.context = context;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }

   @Generated
   public TickContext getContext() {
      return this.context;
   }
}
