package win.furryhook.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import win.furryhook.event.PacketEventManager;
import win.furryhook.event.events.packet.PacketSendEvent;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
   @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
   private void wrapSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
      if (!PacketEventManager.hasSendListeners()) {
         original.call(new Object[]{packet, listener, flush});
      } else {
         PacketSendEvent event = PacketEventManager.callSendPre((Connection)(Object)this, packet);
         if (!event.isCancelled()) {
            Packet<?> dispatchedPacket = event.getPacket();
            original.call(new Object[]{dispatchedPacket, listener, flush});
            PacketEventManager.callSendPost((Connection)(Object)this, dispatchedPacket);
         }
      }
   }

   @WrapMethod(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V")
   private void wrapReceive(ChannelHandlerContext context, Packet<?> packet, Operation<Void> original) {
      if (!PacketEventManager.hasReceiveListeners()) {
         original.call(new Object[]{context, packet});
      } else if (!PacketEventManager.callReceivePre((Connection)(Object)this, packet)) {
         original.call(new Object[]{context, packet});
         PacketEventManager.callReceivePost((Connection)(Object)this, packet);
      }
   }
}
