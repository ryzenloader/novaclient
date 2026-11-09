package win.furryhook.utils.combat.rotations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;

public final class SnapRotation implements AuraRotation {
   private static final int SNAP_TICKS = 3;
   private int targetId = Integer.MIN_VALUE;
   private int ticks = 0;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            this.ticks = 0;
         }

         if (this.ticks < 3) {
            Vec3 eye = player.getEyePosition();
            double dx = targetEyePos.x - eye.x;
            double dy = targetEyePos.y + target.getBbHeight() / 2.0 - (eye.y + player.getBbHeight() / 2.0);
            double dz = targetEyePos.z - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float idealYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float idealPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(dy, horizontal))), -90.0F, 90.0F);
            float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
            float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
            float deltaYaw = Mth.wrapDegrees(idealYaw - currentYaw);
            float deltaPitch = idealPitch - currentPitch;
            float step = 0.4F;
            float yaw = currentYaw + Mth.clamp(deltaYaw * step, -180.0F, 180.0F);
            float pitch = Mth.clamp(currentPitch + deltaPitch * step, -90.0F, 90.0F);
            RotationContext.setRotation(yaw, pitch);
            this.ticks++;
         } else {
            RotationContext.setRotation(player.getYRot(), player.getXRot());
         }
      } else {
         this.reset();
      }
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.ticks = 0;
   }
}
