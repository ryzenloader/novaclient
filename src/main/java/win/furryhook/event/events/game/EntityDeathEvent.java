package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.world.entity.LivingEntity;
import win.furryhook.event.Event;

public final class EntityDeathEvent extends Event {
   private LivingEntity entity;

   public EntityDeathEvent set(LivingEntity entity) {
      this.entity = entity;
      return this;
   }

   @Generated
   public LivingEntity getEntity() {
      return this.entity;
   }
}
