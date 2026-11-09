package win.furryhook.context;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

public interface PlayerContext extends WorldContext {
   double MOVEMENT_EPSILON = 1.0E-4;

   default LocalPlayer localPlayer() {
      return this.player();
   }

   default Inventory inventory() {
      LocalPlayer player = this.localPlayer();
      return player != null ? player.getInventory() : null;
   }

   default boolean hasPlayer() {
      return this.localPlayer() != null;
   }

   default boolean isMoving() {
      LocalPlayer player = this.localPlayer();
      if (player == null) {
         return false;
      }

      Vec3 movement = player.getDeltaMovement();
      return movement.horizontalDistanceSqr() > 1.0E-4;
   }

   default boolean hasMovementInput() {
      LocalPlayer player = this.localPlayer();
      return player != null
         && (player.input.keyPresses.forward() || player.input.keyPresses.backward() || player.input.keyPresses.left() || player.input.keyPresses.right());
   }
}
