package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.world.entity.Entity;
import win.furryhook.event.Event;

public final class FireworkEvent extends Event {
   private Entity boostedEntity;
   private float speed;

   public FireworkEvent set(Entity boostedEntity, float speed) {
      this.boostedEntity = boostedEntity;
      this.speed = speed;
      return this;
   }

   @Generated
   public Entity getBoostedEntity() {
      return this.boostedEntity;
   }

   @Generated
   public float getSpeed() {
      return this.speed;
   }
}
