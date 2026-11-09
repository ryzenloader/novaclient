package win.furryhook.feature.impl.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.NumberSetting;

public final class FreecamFeature extends Feature implements MinecraftContext {
   private static volatile boolean active;
   private static volatile Vec3 camPos = Vec3.ZERO;
   private static volatile Vec3 prevCamPos = Vec3.ZERO;
   private static volatile float camYaw;
   private static volatile float camPitch;
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1, ""));

   public FreecamFeature() {
      super("Freecam", "Свободная камера, игрок остаётся на месте", FeatureCategory.MOVEMENT, -1);
   }

   public static boolean isActive() {
      return active;
   }

   public static Vec3 interpolatedPosition(float partialTick) {
      return prevCamPos.lerp(camPos, partialTick);
   }

   public static float yaw() {
      return camYaw;
   }

   public static float pitch() {
      return camPitch;
   }

   public static boolean onMouseTurn(double yawDelta, double pitchDelta) {
      if (!active) {
         return false;
      }

      camYaw += (float)yawDelta * 0.15F;
      camPitch = Mth.clamp(camPitch + (float)pitchDelta * 0.15F, -90.0F, 90.0F);
      return true;
   }

   @Override
   protected void onEnable() {
      LocalPlayer player = this.player();
      if (player == null) {
         active = false;
      } else {
         camPos = player.getEyePosition();
         prevCamPos = camPos;
         camYaw = player.getYRot();
         camPitch = player.getXRot();
         active = true;
      }
   }

   @Override
   protected void onDisable() {
      active = false;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (active) {
         prevCamPos = camPos;
         double step = this.speed.getValue();
         double yawRad = Math.toRadians(camYaw);
         double sin = -Math.sin(yawRad);
         double cos = Math.cos(yawRad);
         double dx = 0.0;
         double dy = 0.0;
         double dz = 0.0;
         if (mc.options.keyUp.isDown()) {
            dx += sin;
            dz += cos;
         }

         if (mc.options.keyDown.isDown()) {
            dx -= sin;
            dz -= cos;
         }

         if (mc.options.keyLeft.isDown()) {
            dx -= cos;
            dz += sin;
         }

         if (mc.options.keyRight.isDown()) {
            dx += cos;
            dz -= sin;
         }

         if (mc.options.keyJump.isDown()) {
            dy++;
         }

         if (mc.options.keyShift.isDown()) {
            dy--;
         }

         double length = Math.sqrt(dx * dx + dz * dz);
         if (length > 1.0E-4) {
            dx = dx / length * step;
            dz = dz / length * step;
         }

         dy *= step;
         if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            camPos = camPos.add(dx, dy, dz);
         }
      }
   }
}
