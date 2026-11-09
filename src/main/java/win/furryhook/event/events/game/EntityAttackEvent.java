package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import win.furryhook.event.Event;

public final class EntityAttackEvent extends Event {
   private Player player;
   private Entity target;

   public EntityAttackEvent set(Player player, Entity target) {
      this.player = player;
      this.target = target;
      return this;
   }

   @Generated
   public Player getPlayer() {
      return this.player;
   }

   @Generated
   public Entity getTarget() {
      return this.target;
   }
}
