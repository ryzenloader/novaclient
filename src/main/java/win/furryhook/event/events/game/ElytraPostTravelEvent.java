package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.Event;

public final class ElytraPostTravelEvent extends Event {
   private Vec3 oldVelocity;

   public ElytraPostTravelEvent set(Vec3 oldVelocity) {
      this.oldVelocity = oldVelocity;
      return this;
   }

   public void setOldVelocity(Vec3 oldVelocity) {
      this.oldVelocity = oldVelocity;
   }

   @Generated
   public Vec3 getOldVelocity() {
      return this.oldVelocity;
   }
}
