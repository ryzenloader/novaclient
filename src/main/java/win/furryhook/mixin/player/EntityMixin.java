package win.furryhook.mixin.player;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.furryhook.feature.impl.combat.HitBoxesFeature;
import win.furryhook.feature.impl.movement.NoPushFeature;

@Mixin(Entity.class)
public abstract class EntityMixin {
   @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void cancelEntityPush(Entity entity, CallbackInfo ci) {
      if (NoPushFeature.shouldCancelEntityPush((Entity)this, entity)) {
         ci.cancel();
      }
   }

   @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
   private void expandPickRadius(CallbackInfoReturnable<Float> cir) {
      HitBoxesFeature hitBoxes = HitBoxesFeature.getEnabled();
      if (hitBoxes != null && hitBoxes.appliesTo((Entity)this)) {
         cir.setReturnValue((Float)cir.getReturnValue() + (float)hitBoxes.getHorizontalExpansion());
      }
   }
}
