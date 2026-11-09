package win.furryhook.mixin.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.furryhook.context.RotationContext;
import win.furryhook.emote.EmoteWheel;
import win.furryhook.event.EventManager;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.feature.impl.movement.FreecamFeature;
import win.furryhook.menu.core.MenuOverlay;
import win.furryhook.utils.combat.neuro.NeuroMouse;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
   @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
   private void onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
      if (EventManager.hasListeners(MouseInputEvent.class)) {
         MouseInputEvent event = EventManager.call(new MouseInputEvent(window, buttonInfo.button(), action, buttonInfo.modifiers()));
         if (event.isCancelled()) {
            ci.cancel();
         }
      }
   }

   @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
   private void blockWorldScrollWhileMenuOpen(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (MenuOverlay.blocksInput()) {
         MenuOverlay.handleScroll(vertical);
         ci.cancel();
      }
   }

   @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
   private void onTurnPlayer(LocalPlayer player, double yawDelta, double pitchDelta, Operation<Void> original) {
      NeuroMouse.onMouseTurn(yawDelta, pitchDelta);
      if (!EmoteWheel.onMouseTurn(yawDelta, pitchDelta)) {
         if (!FreecamFeature.onMouseTurn(yawDelta, pitchDelta)) {
            if (!RotationContext.onMouseTurn(yawDelta, pitchDelta)) {
               original.call(player, yawDelta, pitchDelta);
            }
         }
      }
   }
}
