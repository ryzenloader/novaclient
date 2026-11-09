package win.furryhook.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventManager;
import win.furryhook.event.Events;
import win.furryhook.event.events.game.PlayerTickEvent;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.impl.movement.NoPushFeature;
import win.furryhook.feature.impl.movement.NoSlowFeature;
import win.furryhook.utils.combat.LocalPlayerHistory;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(method = "tick", at = @At("HEAD"))
   private void onTickPre(CallbackInfo ci) {
      if (EventManager.hasListeners(PlayerTickEvent.class)) {
         EventManager.call(Events.PLAYER_TICK.set((LocalPlayer)this, PlayerTickEvent.Phase.PRE));
      }
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void onTickPost(CallbackInfo ci) {
      LocalPlayer player = (LocalPlayer)this;
      LocalPlayerHistory.record(player);
      if (EventManager.hasListeners(PlayerTickEvent.class)) {
         EventManager.call(Events.PLAYER_TICK.set(player, PlayerTickEvent.Phase.POST));
      }
   }

   @Inject(method = "moveTowardsClosestSpace(DD)V", at = @At("HEAD"), cancellable = true)
   private void cancelClosestSpacePush(double x, double z, CallbackInfo ci) {
      if (NoPushFeature.shouldCancelClosestSpacePush((LocalPlayer)this)) {
         ci.cancel();
      }
   }

   @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
   private void removeItemUseSlowdown(CallbackInfoReturnable<Float> cir) {
      if (NoSlowFeature.shouldBypassSlowdown((LocalPlayer)this)) {
         cir.setReturnValue(1.0F);
      }
   }

   @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
   private void keepSprintingWhileUsingItem(CallbackInfoReturnable<Boolean> cir) {
      if (NoSlowFeature.shouldBypassSlowdown((LocalPlayer)this)) {
         cir.setReturnValue(false);
      }
   }

   @Redirect(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
   private float useCameraPitchForHandBob(LocalPlayer player) {
      return this.minecraft.options.getCameraType().isFirstPerson() ? this.minecraft.gameRenderer.mainCamera().xRot() : player.getXRot();
   }

   @Redirect(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
   private float useCameraYawForHandBob(LocalPlayer player) {
      return this.minecraft.options.getCameraType().isFirstPerson() ? this.minecraft.gameRenderer.mainCamera().yRot() : player.getYRot();
   }

   @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
   private void onGetViewYRot(float partialTick, CallbackInfoReturnable<Float> cir) {
      if (RotationContext.isActive()) {
         AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
         if (aura != null && aura.clientLook.getValue() && aura.getTarget() != null) {
            Entity target = aura.getTarget();
            LocalPlayer player = (LocalPlayer)this;
            Vec3 visualEye = player.getEyePosition(partialTick);
            Vec3 visualTarget = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
            float visualYaw = (float)Math.toDegrees(Math.atan2(-(visualTarget.x - visualEye.x), visualTarget.z - visualEye.z));
            if (aura.componentMode.is("Плавный")) {
               cir.setReturnValue(visualYaw);
               return;
            }

            Vec3 tickEye = player.getEyePosition(1.0F);
            Vec3 tickTarget = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            float tickYaw = (float)Math.toDegrees(Math.atan2(-(tickTarget.x - tickEye.x), tickTarget.z - tickEye.z));
            Vec3 prevTickEye = new Vec3(player.xo, player.yo + player.getEyeHeight(), player.zo);
            Vec3 prevTickTarget = new Vec3(target.xo, target.yo + target.getBbHeight() * 0.5, target.zo);
            float prevTickYaw = (float)Math.toDegrees(Math.atan2(-(prevTickTarget.x - prevTickEye.x), prevTickTarget.z - prevTickEye.z));
            float errorOld = Mth.wrapDegrees(player.yRotO - prevTickYaw);
            float errorNew = Mth.wrapDegrees(player.getYRot() - tickYaw);
            float interpolatedError = Mth.lerp(partialTick, errorOld, errorNew);
            cir.setReturnValue(visualYaw + interpolatedError);
            return;
         }

         cir.setReturnValue(RotationContext.getFreeYaw());
      }
   }

   @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
   private void onGetViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
      if (RotationContext.isActive()) {
         AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
         if (aura != null && aura.clientLook.getValue() && aura.getTarget() != null) {
            Entity target = aura.getTarget();
            LocalPlayer player = (LocalPlayer)this;
            Vec3 visualEye = player.getEyePosition(partialTick);
            Vec3 visualTarget = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
            double visualDist = Math.hypot(visualTarget.x - visualEye.x, visualTarget.z - visualEye.z);
            float visualPitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(visualTarget.y - visualEye.y, visualDist)), -90.0, 90.0);
            if (aura.componentMode.is("Плавный")) {
               cir.setReturnValue(visualPitch);
               return;
            }

            Vec3 tickEye = player.getEyePosition(1.0F);
            Vec3 tickTarget = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            double tickDist = Math.hypot(tickTarget.x - tickEye.x, tickTarget.z - tickEye.z);
            float tickPitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(tickTarget.y - tickEye.y, tickDist)), -90.0, 90.0);
            Vec3 prevTickEye = new Vec3(player.xo, player.yo + player.getEyeHeight(), player.zo);
            Vec3 prevTickTarget = new Vec3(target.xo, target.yo + target.getBbHeight() * 0.5, target.zo);
            double prevTickDist = Math.hypot(prevTickTarget.x - prevTickEye.x, prevTickTarget.z - prevTickEye.z);
            float prevTickPitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(prevTickTarget.y - prevTickEye.y, prevTickDist)), -90.0, 90.0);
            float errorOld = player.xRotO - prevTickPitch;
            float errorNew = player.getXRot() - tickPitch;
            float interpolatedError = Mth.lerp(partialTick, errorOld, errorNew);
            cir.setReturnValue(Mth.clamp(visualPitch + interpolatedError, -90.0F, 90.0F));
            return;
         }

         cir.setReturnValue(RotationContext.getFreePitch());
      }
   }
}
