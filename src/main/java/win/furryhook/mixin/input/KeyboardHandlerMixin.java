package win.furryhook.mixin.input;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.furryhook.event.EventManager;
import win.furryhook.event.events.input.CharacterInputEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
   @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
   private void onCharacterTyped(long window, CharacterEvent characterEvent, CallbackInfo ci) {
      if (characterEvent.isAllowedChatCharacter() && EventManager.hasListeners(CharacterInputEvent.class)) {
         CharacterInputEvent event = EventManager.call(new CharacterInputEvent(window, characterEvent.codepoint()));
         if (event.isCancelled()) {
            ci.cancel();
         }
      }
   }

   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
      if (EventManager.hasListeners(KeyboardInputEvent.class)) {
         KeyboardInputEvent event = EventManager.call(new KeyboardInputEvent(window, keyEvent.key(), keyEvent.scancode(), action, keyEvent.modifiers()));
         if (event.isCancelled()) {
            ci.cancel();
         }
      }
   }
}
