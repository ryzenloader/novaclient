package win.furryhook.event.events.packet;

import lombok.Generated;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import win.furryhook.event.CancellableEvent;

public final class PacketReceiveEvent extends CancellableEvent {
   private final Connection connection;
   private final Packet<?> packet;
   private final PacketReceiveEvent.Phase phase;

   public PacketReceiveEvent(Connection connection, Packet<?> packet, PacketReceiveEvent.Phase phase) {
      this.connection = connection;
      this.packet = packet;
      this.phase = phase;
   }

   @Generated
   public Connection getConnection() {
      return this.connection;
   }

   @Generated
   public Packet<?> getPacket() {
      return this.packet;
   }

   @Generated
   public PacketReceiveEvent.Phase getPhase() {
      return this.phase;
   }

   public enum Phase {
      PRE,
      POST;
   }
}
