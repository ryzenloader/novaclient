package win.furryhook.feature.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class GrimElytraFeature extends Feature {
   public final BooleanSetting velocityStart = this.register(new BooleanSetting("Velocity Start", true));
   public final NumberSetting airborneTicks = this.register(new NumberSetting("Airborne Ticks", 3.0, 0.0, 12.0, 1.0, " ticks"));
   private int airTicks;
   private boolean velocityReceived;
   private boolean started;

   public GrimElytraFeature() {
      super("GrimElytra", "Retries fall-flying after airborne transitions", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && packet.id() == player.getId()) {
            this.velocityReceived = true;
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      if (player != null && !player.isFallFlying() && !player.onGround()) {
         this.airTicks++;
         if (!this.started && (this.velocityStart.getValue() && this.velocityReceived || this.airTicks >= this.airborneTicks.getValue().intValue())) {
            this.started = true;
         }

         if (this.started) {
            player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
         }
      } else {
         this.reset();
      }
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.airTicks = 0;
      this.velocityReceived = false;
      this.started = false;
   }
}
