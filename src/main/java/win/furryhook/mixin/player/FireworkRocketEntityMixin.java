package win.furryhook.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventManager;
import win.furryhook.event.Events;
import win.furryhook.event.events.game.FireworkEvent;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.impl.movement.ElytraBoostFeature;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
   private static final double INERTIA = 0.1;
   private static final double ACCEL = 0.5;
   private static final Vec3 VANILLA_BOOST = new Vec3(1.5, 1.5, 1.5);

   @Redirect(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
   )
   private void redirectBoostVelocity(LivingEntity shooter, Vec3 originalVelocity) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && shooter == player) {
         ElytraBoostFeature.markBoostApplied();
         if (EventManager.hasListeners(FireworkEvent.class)) {
            EventManager.call(Events.FIREWORK.set(player, 1.5F));
         }

         ElytraBoostFeature boost = ElytraBoostFeature.getEnabled();
         if (boost == null) {
            shooter.setDeltaMovement(originalVelocity);
         } else {
            AuraFeature aura = AuraFeature.getEnabled();
            boolean auraControlling = aura != null && aura.getTarget() != null || RotationContext.isActive();
            float yaw = auraControlling ? RotationContext.getServerYaw() : player.getYRot();
            float pitch = auraControlling ? RotationContext.getServerPitch() : player.getXRot();
            Vec3 motion = shooter.getDeltaMovement();
            Vec3 rotation = Vec3.directionFromRotation(pitch, yaw);
            Vec3 boostVec = boost.computeBoost(yaw, pitch);
            shooter.setDeltaMovement(
               motion.x + rotation.x * 0.1 + (rotation.x * boostVec.x - motion.x) * 0.5,
               motion.y + rotation.y * 0.1 + (rotation.y * boostVec.y - motion.y) * 0.5,
               motion.z + rotation.z * 0.1 + (rotation.z * boostVec.z - motion.z) * 0.5
            );
         }
      } else {
         shooter.setDeltaMovement(originalVelocity);
      }
   }
}
