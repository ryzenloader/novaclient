package win.furryhook.feature.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.event.events.packet.PacketSendEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class SpeedFeature extends Feature {
   private static final String MODE_VISUAL = "Visual";
   private static final String MODE_LEGIT = "Legit";
   private static final String MODE_MICRO = "Micro";
   private static final double MIN_INPUT_SQ = 1.0E-4;
   private static final double HORIZ_CAP = 0.62;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Visual", "Visual", "Legit", "Micro"));
   public final NumberSetting bps = this.register(new NumberSetting("BPS", 40.0, 5.0, 100.0, 1.0, "").visibleWhen(() -> this.mode.is("Visual")));
   public final BooleanSetting syncOnDrop = this.register(new BooleanSetting("Sync On Drop", true).visibleWhen(() -> this.mode.is("Visual")));
   public final BooleanSetting syncOnDisable = this.register(new BooleanSetting("Sync On Disable", true).visibleWhen(() -> this.mode.is("Visual")));
   public final BooleanSetting killMomentumY = this.register(new BooleanSetting("No Fall Damage", true).visibleWhen(() -> this.mode.is("Visual")));
   public final NumberSetting microBoost = this.register(
      new NumberSetting("Micro Boost", 0.01, 0.005, 0.02, 0.005, "").visibleWhen(() -> this.mode.is("Micro"))
   );
   public final BooleanSetting onlyWhenMoving = this.register(new BooleanSetting("Only With Input", true));
   public final BooleanSetting autoSprint = this.register(new BooleanSetting("Auto Sprint", true));
   private Vec3 anchorPos;
   private float anchorYRot;
   private float anchorXRot;
   private boolean flushing;

   public SpeedFeature() {
      super("Speed", "Визуальный спид до 100 bps + drop-sync", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onEnable() {
      this.anchorPos = null;
   }

   @Override
   protected void onDisable() {
      if (this.mode.is("Visual") && this.syncOnDisable.getValue()) {
         this.flushToVisual();
      }

      this.anchorPos = null;
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (this.isEnabled() && this.mode.is("Visual")) {
         if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            this.anchorPos = null;
         }
      }
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (this.isEnabled() && this.mode.is("Visual")) {
         if (!this.flushing) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof ServerboundPlayerActionPacket action && this.syncOnDrop.getValue()) {
               Action a = action.getAction();
               if (a == Action.DROP_ITEM || a == Action.DROP_ALL_ITEMS) {
                  this.flushToVisual();
               }
            } else if (packet instanceof ServerboundMovePlayerPacket move) {
               LocalPlayer player = Minecraft.getInstance().player;
               if (player != null) {
                  if (this.anchorPos == null) {
                     this.anchorPos = player.position();
                     this.anchorYRot = player.getYRot();
                     this.anchorXRot = player.getXRot();
                  }

                  float yaw = move.getYRot(this.anchorYRot);
                  float pitch = move.getXRot(this.anchorXRot);
                  this.anchorYRot = yaw;
                  this.anchorXRot = pitch;
                  boolean onGround = move.isOnGround();
                  boolean hc = move.horizontalCollision();
                  event.setPacket(new PosRot(this.anchorPos.x, this.anchorPos.y, this.anchorPos.z, yaw, pitch, onGround, hc));
               }
            }
         }
      }
   }

   private void flushToVisual() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.connection != null) {
         if (this.anchorPos != null) {
            Vec3 visual = player.position();
            if (!(visual.distanceToSqr(this.anchorPos) < 1.0E-4)) {
               this.flushing = true;

               try {
                  player.connection
                     .send(new PosRot(visual.x, visual.y, visual.z, player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision));
               } finally {
                  this.flushing = false;
               }

               this.anchorPos = visual;
               this.anchorYRot = player.getYRot();
               this.anchorXRot = player.getXRot();
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      if (player != null) {
         if (this.mode.is("Visual")) {
            this.visualTick(player);
         } else if (!player.getAbilities().flying
            && !player.isFallFlying()
            && !player.isPassenger()
            && !player.isSpectator()
            && !player.isInWater()
            && !player.isInLava()
            && !player.onClimbable()) {
            Vec2 input = player.input.getMoveVector();
            boolean hasInput = input.lengthSquared() >= 1.0E-4;
            if (!this.onlyWhenMoving.getValue() || hasInput) {
               if (this.autoSprint.getValue() && hasInput && input.y > 0.0F && !player.isSprinting()) {
                  player.setSprinting(true);
               }

               if (this.mode.is("Micro")) {
                  Vec3 motion = player.getDeltaMovement();
                  Vec3 forward = forwardVector(player, input);
                  double push = this.microBoost.getValue();
                  double newX = clamp(motion.x + forward.x * push, 0.62);
                  double newZ = clamp(motion.z + forward.z * push, 0.62);
                  player.setDeltaMovement(newX, motion.y, newZ);
               }
            }
         }
      }
   }

   private void visualTick(LocalPlayer player) {
      if (!player.getAbilities().flying && !player.isFallFlying() && !player.isPassenger() && !player.isSpectator()) {
         Vec2 input = player.input.getMoveVector();
         boolean hasInput = input.lengthSquared() >= 1.0E-4;
         if (this.autoSprint.getValue() && hasInput && input.y > 0.0F && !player.isSprinting()) {
            player.setSprinting(true);
         }

         if (!this.onlyWhenMoving.getValue() || hasInput) {
            double perTick = this.bps.getValue() / 20.0;
            Vec3 forward = forwardVector(player, input);
            Vec3 motion = player.getDeltaMovement();
            double vy = motion.y;
            if (this.killMomentumY.getValue()) {
               player.fallDistance = 0.0;
               if (vy < -0.08) {
                  vy = -0.08;
               }
            }

            player.setDeltaMovement(forward.x * perTick, vy, forward.z * perTick);
         }
      }
   }

   private static Vec3 forwardVector(LocalPlayer player, Vec2 input) {
      double yawRad = Math.toRadians(player.getYRot());
      double sin = -Math.sin(yawRad);
      double cos = Math.cos(yawRad);
      double sx = input.x;
      double sy = input.y;
      double len = Math.sqrt(sx * sx + sy * sy);
      if (len < 1.0E-4) {
         return new Vec3(sin, 0.0, cos);
      }

      sx /= len;
      sy /= len;
      double wx = sx * cos + sy * sin;
      double wz = sy * cos - sx * sin;
      double wl = Math.sqrt(wx * wx + wz * wz);
      return wl < 1.0E-6 ? new Vec3(sin, 0.0, cos) : new Vec3(wx / wl, 0.0, wz / wl);
   }

   private static double clamp(double value, double cap) {
      return Mth.clamp(value, -cap, cap);
   }
}
