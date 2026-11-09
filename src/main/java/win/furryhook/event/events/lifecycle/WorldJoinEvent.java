package win.furryhook.event.events.lifecycle;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import win.furryhook.event.Event;

public final class WorldJoinEvent extends Event {
   private Minecraft client;
   private ClientLevel level;

   public WorldJoinEvent set(Minecraft client, ClientLevel level) {
      this.client = client;
      this.level = level;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }

   @Generated
   public ClientLevel getLevel() {
      return this.level;
   }
}
