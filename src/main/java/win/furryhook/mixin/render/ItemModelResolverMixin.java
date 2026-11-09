package win.furryhook.mixin.render;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.furryhook.feature.impl.visual.ItemReplacerFeature;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
   @Inject(method = "updateForTopItem", at = @At("RETURN"))
   private void furryhook$captureItemReplacer(
      ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed, CallbackInfo ci
   ) {
      ItemReplacerFeature.captureForRender(stack, displayContext, owner);
   }
}
