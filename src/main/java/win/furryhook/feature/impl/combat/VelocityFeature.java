package win.furryhook.feature.impl.combat;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;

public final class VelocityFeature extends Feature implements MinecraftContext {
   public VelocityFeature() {
      super("Velocity", "Removes knockback", FeatureCategory.COMBAT, -1);
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && this.player() != null) {
         if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == this.player().getId()) {
            event.cancel();
         }
      }
   }
}
