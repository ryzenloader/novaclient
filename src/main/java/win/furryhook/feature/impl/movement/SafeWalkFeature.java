package win.furryhook.feature.impl.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.input.PlayerInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;

public final class SafeWalkFeature extends Feature implements MinecraftContext {
   public SafeWalkFeature() {
      super("SafeWalk", "Prevents walking off block edges", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onInput(PlayerInputEvent event) {
      LocalPlayer player = this.player();
      if (player != null && this.level() != null && player.onGround()) {
         BlockPos below = player.blockPosition().below();
         if (this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty()) {
            event.setShift(true);
         }
      }
   }
}
