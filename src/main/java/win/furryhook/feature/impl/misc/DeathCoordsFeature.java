package win.furryhook.feature.impl.misc;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.utils.text.ChatUtil;

public final class DeathCoordsFeature extends Feature {
   private boolean reported;

   public DeathCoordsFeature() {
      super("DeathCoords", "Shows your coordinates on death", FeatureCategory.MISC, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      if (player == null) {
         this.reported = false;
      } else if (!player.isDeadOrDying()) {
         this.reported = false;
      } else if (!this.reported) {
         BlockPos pos = player.getOnPos();
         ChatUtil.print("Death at x: " + pos.getX() + " y: " + pos.getY() + " z: " + pos.getZ());
         this.reported = true;
      }
   }
}
