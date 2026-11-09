package win.furryhook.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.furryhook.feature.impl.visual.AmbienceFeature;
import win.furryhook.feature.impl.visual.WorldTweaksFeature;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   @Inject(method = "setupFog", at = @At("RETURN"))
   private void onSetupFog(
      Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir
   ) {
      if (camera.getFluidInCamera() == FogType.NONE) {
         FogData fog = (FogData)cir.getReturnValue();
         if (fog != null) {
            AmbienceFeature ambience = AmbienceFeature.getEnabled();
            if (ambience != null && ambience.usesFog()) {
               ambience.applyFog(fog);
            } else {
               WorldTweaksFeature worldTweaks = WorldTweaksFeature.getEnabled();
               if (worldTweaks != null && worldTweaks.changeFog.getValue()) {
                  worldTweaks.applyFog(fog);
               }
            }
         }
      }
   }
}
