package win.furryhook.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.furryhook.feature.impl.visual.ItemReplacerFeature;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin {
   @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
   private void furryhook$renderItemReplacer(
      PoseStack poseStack, SubmitNodeCollector collector, int combinedLight, int combinedOverlay, int seed, CallbackInfo ci
   ) {
      if (ItemReplacerFeature.renderPending(poseStack, collector, combinedLight, combinedOverlay)) {
         ci.cancel();
      }
   }
}
