package win.furryhook.feature.impl.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.PlayerContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.NumberSetting;

public final class WaterSpeedFeature extends Feature implements PlayerContext {
   public final NumberSetting boost = this.register(new NumberSetting("Boost", 1.05, 1.01, 1.2, 0.01, "x"));

   public WaterSpeedFeature() {
      super("WaterSpeed", "Swim faster", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      if (player != null && player.isSwimming() && this.isMoving()) {
         Vec3 movement = player.getDeltaMovement();
         player.setDeltaMovement(movement.x * this.boost.getValue(), movement.y, movement.z * this.boost.getValue());
      }
   }
}
