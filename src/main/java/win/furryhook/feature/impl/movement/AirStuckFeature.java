package win.furryhook.feature.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.PlayerTickEvent;
import win.furryhook.event.events.packet.PacketSendEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;

public final class AirStuckFeature extends Feature {
   public AirStuckFeature() {
      super("AirStuck", "Останавливает вас в воздухе как на фрикаме", FeatureCategory.MOVEMENT, -1);
   }

   public static AirStuckFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(AirStuckFeature.class);
   }

   public void pauseForAttack() {
   }

   public boolean isExtendedKillAuraRange() {
      return false;
   }

   public double getExtendedKillAuraRange() {
      return 0.0;
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      LocalPlayer player = event.getPlayer();
      if (player != null) {
         player.setDeltaMovement(Vec3.ZERO);
      }
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.PRE) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player == null) {
            this.setEnabled(false);
         } else {
            player.setDeltaMovement(Vec3.ZERO);
            Packet<?> packet = event.getPacket();
            if (packet instanceof ServerboundUseItemOnPacket && !(player.getMainHandItem().getItem() instanceof BlockItem)) {
               event.cancel();
            } else if (packet instanceof ServerboundMovePlayerPacket move) {
               if (move.hasPosition() && move.hasRotation()) {
                  event.setPacket(
                     new PosRot(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision)
                  );
               } else if (move.hasPosition()) {
                  event.setPacket(new Pos(player.getX(), player.getY(), player.getZ(), player.onGround(), player.horizontalCollision));
               } else if (move.hasRotation()) {
                  event.setPacket(new Rot(player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision));
               }
            }
         }
      }
   }
}
