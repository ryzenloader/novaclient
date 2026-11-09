package win.furryhook.event.events.packet;

import java.util.Objects;
import lombok.Generated;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import win.furryhook.event.CancellableEvent;

public final class PacketSendEvent extends CancellableEvent {
   private final Connection connection;
   private Packet<?> packet;
   private final PacketSendEvent.Phase phase;

   public PacketSendEvent(Connection connection, Packet<?> packet, PacketSendEvent.Phase phase) {
      this.connection = connection;
      this.packet = packet;
      this.phase = phase;
   }

   public void setPacket(Packet<?> packet) {
      if (this.isCompleted()) {
         throw new IllegalStateException("Cannot replace a packet after event dispatch.");
      }

      this.packet = Objects.requireNonNull(packet, "packet");
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
   public PacketSendEvent.Phase getPhase() {
      return this.phase;
   }

   public enum Phase {
      PRE,
      POST;
   }
}
