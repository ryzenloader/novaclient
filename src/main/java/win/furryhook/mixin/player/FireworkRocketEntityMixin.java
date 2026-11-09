package win.furryhook.mixin.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventManager;
import win.furryhook.event.Events;
import win.furryhook.event.events.game.FireworkEvent;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.impl.movement.ElytraBoostFeature;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
   @WrapOperation(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
   )
   private void redirectBoostVelocity(LivingEntity shooter, Vec3 originalVelocity, Operation<Void> original) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && (Object)shooter == player) {
         ElytraBoostFeature.markBoostApplied();
         if (EventManager.hasListeners(FireworkEvent.class)) {
            EventManager.call(Events.FIREWORK.set(player, 1.5F));
         }

         ElytraBoostFeature boost = ElytraBoostFeature.getEnabled();
         if (boost == null) {
            original.call(shooter, originalVelocity);
         } else {
            AuraFeature aura = AuraFeature.getEnabled();
            boolean auraControlling = aura != null && aura.getTarget() != null || RotationContext.isActive();
            float yaw = auraControlling ? RotationContext.getServerYaw() : player.getYRot();
            float pitch = auraControlling ? RotationContext.getServerPitch() : player.getXRot();
            Vec3 motion = shooter.getDeltaMovement();
            Vec3 rotation = Vec3.directionFromRotation(pitch, yaw);
            Vec3 boostVec = boost.computeBoost(yaw, pitch);
            original.call(shooter, new Vec3(
               motion.x + rotation.x * 0.1 + (rotation.x * boostVec.x - motion.x) * 0.5,
               motion.y + rotation.y * 0.1 + (rotation.y * boostVec.y - motion.y) * 0.5,
               motion.z + rotation.z * 0.1 + (rotation.z * boostVec.z - motion.z) * 0.5
            ));
         }
      } else {
         original.call(shooter, originalVelocity);
      }
   }
}
