package win.furryhook.utils.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class JavelinPredict {
   private static final float BASE_TICKS = 3.0F;

   private JavelinPredict() {
   }

   public static Vec3 predictElytra(LivingEntity entity, float minusTicks, JavelinPredict.Type type) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer local = mc.player;
      if (entity instanceof Player player && local != null) {
         Vec3 basePos = player.getPosition(1.0F);
         Vec3 velocity = new Vec3(player.getX() - player.xOld, player.getY() - player.yOld, player.getZ() - player.zOld);
         float ticks = (float)(local.getEyePosition().distanceTo(entity.getBoundingBox().getCenter()) * minusTicks);
         ticks += 3.0F;
         if (type == JavelinPredict.Type.LIMIT) {
            ticks = Mth.clamp(ticks, 0.0F, 2.7F);
         }

         float deltaYaw = Mth.wrapDegrees(player.getYRot() - player.yRotO);
         float deltaPitch = Mth.wrapDegrees(player.getXRot() - player.xRotO);
         Vec3 predictedPos = basePos;
         int intTicks = (int)ticks;
         float frac = ticks - intTicks;

         for (int i = 0; i < intTicks; i++) {
            velocity = turn(velocity, deltaYaw, deltaPitch);
            predictedPos = predictedPos.add(velocity);
         }

         if (frac > 0.0F) {
            velocity = turn(velocity, deltaYaw * frac, deltaPitch * frac);
            predictedPos = predictedPos.add(velocity.scale(frac));
         }

         return predictedPos;
      } else {
         return entity.position();
      }
   }

   private static Vec3 turn(Vec3 velocity, float deltaYaw, float deltaPitch) {
      double speed = velocity.length();
      if (speed <= 0.001) {
         return velocity;
      }

      double velYaw = Math.atan2(-velocity.x, velocity.z) + Math.toRadians(deltaYaw);
      double velPitch = -Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z)) + Math.toRadians(deltaPitch);
      velPitch = Mth.clamp(velPitch, -Math.PI / 2, Math.PI / 2);
      return new Vec3(-Math.sin(velYaw) * Math.cos(velPitch) * speed, -Math.sin(velPitch) * speed, Math.cos(velYaw) * Math.cos(velPitch) * speed);
   }

   public static Vec3 realPredict(LivingEntity entity, JavelinPredict.Type type) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer local = mc.player;
      if (local == null) {
         return entity.position();
      }

      Vec3 predict = predictElytra(entity, 0.0F, type);
      float minusTicks = Mth.clamp((float)local.getEyePosition().distanceTo(predict) / 100.0F, 0.0F, 12.0F);
      minusTicks = Math.max(minusTicks, 0.0F);
      return predictElytra(entity, minusTicks, type);
   }

   public enum Type {
      DEFAULT,
      LIMIT;
   }
}
