package win.furryhook.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.furryhook.context.RenderContext;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {
   private static final long MAX_DEFER_MILLIS = 3000L;

   @Inject(method = "runAllTasks", at = @At("HEAD"), cancellable = true)
   private void onRunAllTasks(CallbackInfo ci) {
      if (RenderContext.deferQueuedTasks) {
         long overlayStart = RenderContext.overlayStartTime;
         if (overlayStart >= 0L) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
               if ((Object)this == mc) {
                  if (mc.level == null && mc.getConnection() == null) {
                     if (Util.getMillis() - overlayStart < 3000L) {
                        ci.cancel();
                     }
                  }
               }
            }
         }
      }
   }
}
