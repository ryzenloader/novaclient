package win.furryhook.feature.impl.misc;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.packet.PacketSendEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;

public final class XCarryFeature extends Feature {
   private static final int PLAYER_INVENTORY_CONTAINER_ID = 0;

   public XCarryFeature() {
      super("XCarry", "Store items in the crafting grid", FeatureCategory.MISC, -1);
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.PRE) {
         if (event.getPacket() instanceof ServerboundContainerClosePacket packet && packet.getContainerId() == 0) {
            event.cancel();
         }
      }
   }
}
