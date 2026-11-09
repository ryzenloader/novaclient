package win.furryhook.feature.impl.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;

public final class ParkourFeature extends Feature {
   private static final double EDGE_PROBE_DEPTH = 0.001;

   public ParkourFeature() {
      super("Parkour", "Automatically jumps at block edges", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      if (player != null && event.getClient().level != null) {
         if (player.onGround() && !player.isShiftKeyDown() && !player.getAbilities().flying) {
            if (this.isAtEdge(event, player)) {
               player.jumpFromGround();
            }
         }
      }
   }

   private boolean isAtEdge(GameTickEvent event, LocalPlayer player) {
      AABB probe = player.getBoundingBox().move(0.0, -0.001, 0.0);
      return !event.getClient().level.getBlockCollisions(player, probe).iterator().hasNext();
   }
}
