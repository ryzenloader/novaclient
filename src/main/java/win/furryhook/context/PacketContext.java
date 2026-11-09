package win.furryhook.context;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import win.furryhook.event.PacketEventManager;

public interface PacketContext extends MinecraftContext {
   default ClientPacketListener packetListener() {
      return this.client().getConnection();
   }

   default Connection connection() {
      ClientPacketListener packetListener = this.packetListener();
      return packetListener != null ? packetListener.getConnection() : null;
   }

   default boolean hasConnection() {
      return this.connection() != null;
   }

   default boolean isConnected() {
      Connection connection = this.connection();
      return connection != null && connection.isConnected();
   }

   default void sendPacket(Packet<?> packet) {
      Connection connection = this.connection();
      if (connection != null && packet != null) {
         connection.send(packet);
      }
   }

   default boolean hasPacketSendListeners() {
      return PacketEventManager.hasSendListeners();
   }

   default boolean hasPacketReceiveListeners() {
      return PacketEventManager.hasReceiveListeners();
   }
}
