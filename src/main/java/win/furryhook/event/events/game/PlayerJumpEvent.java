package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.Event;

public final class PlayerJumpEvent extends Event {
   private LocalPlayer player;
   private Vec3 position;

   public PlayerJumpEvent set(LocalPlayer player, Vec3 position) {
      this.player = player;
      this.position = position;
      return this;
   }

   @Generated
   public LocalPlayer getPlayer() {
      return this.player;
   }

   @Generated
   public Vec3 getPosition() {
      return this.position;
   }
}
