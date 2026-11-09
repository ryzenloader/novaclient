package win.furryhook.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.furryhook.context.RotationContext;
import win.furryhook.feature.impl.movement.FreecamFeature;
import win.furryhook.feature.impl.visual.RemovalsFeature;

@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
   private void onFreecamAlign(float partialTick, CallbackInfo ci) {
      if (FreecamFeature.isActive()) {
         this.setPosition(FreecamFeature.interpolatedPosition(partialTick));
         this.setRotation(FreecamFeature.yaw(), FreecamFeature.pitch());
         ci.cancel();
      }
   }

   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void onExtractRenderState(CameraRenderState cameraState, float cameraEntityPartialTick, CallbackInfo ci) {
      if (RemovalsFeature.shouldRemoveBadEffectsVisuals()) {
         cameraState.entityRenderState.doesMobEffectBlockSky = false;
      }
   }

   @Inject(method = "alignWithEntity", at = @At("HEAD"))
   private void onAlignWithEntity(float partialTick, CallbackInfo ci) {
      Camera camera = (Camera)(Object)this;
      Entity entity = camera.entity();
      if (entity != null) {
         RotationContext.applyRenderInterpolation();
         RotationContext.syncFreeLook(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
      }
   }
}
