package win.furryhook.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import win.furryhook.mixin.accessor.LightEngineAccessor;
import win.furryhook.utils.render.world.DynamicLightManager;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
   @ModifyExpressionValue(method = "getEmission", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"))
   private int virtualDynamicLight(int original, long packedPos, BlockState state) {
      return !(((LightEngineAccessor)this).getChunkSource() instanceof ClientChunkCache)
         ? original
         : Math.max(original, DynamicLightManager.virtualLuminance(packedPos));
   }
}
