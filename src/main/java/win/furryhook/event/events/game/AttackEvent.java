package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import win.furryhook.event.CancellableEvent;

public final class AttackEvent extends CancellableEvent {
   private Minecraft client;

   public AttackEvent set(Minecraft client) {
      this.client = client;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }
}
