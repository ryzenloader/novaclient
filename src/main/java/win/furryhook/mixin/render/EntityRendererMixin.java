package win.furryhook.mixin.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.furryhook.feature.impl.visual.NameTagsFeature;
import win.furryhook.utils.text.NameProtectUtil;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
   @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
   private void protectNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
      if (entity instanceof LivingEntity && NameTagsFeature.shouldHideVanillaTag()) {
         cir.setReturnValue(null);
      } else {
         cir.setReturnValue(NameProtectUtil.protect((Component)cir.getReturnValue()));
      }
   }
}
