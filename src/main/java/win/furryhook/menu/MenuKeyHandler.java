package win.furryhook.menu;

import win.furryhook.context.MinecraftContext;
import win.furryhook.emote.EmoteWheel;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.input.CharacterInputEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.event.events.screen.ScreenKeyEvent;
import win.furryhook.event.events.screen.ScreenMouseButtonEvent;
import win.furryhook.menu.core.MenuOverlay;

public final class MenuKeyHandler implements MinecraftContext {
   @EventTarget(priority = 1000)
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getKey() != 300) {
         if (event.getAction() == 1 && event.getKey() == 344) {
            if (MenuOverlay.toggle(mc)) {
               event.cancel();
            }
         } else if (event.getAction() == 1 && event.getKey() == 256 && EmoteWheel.isOpen()) {
            EmoteWheel.requestCancel();
            EmoteWheel.close(mc);
            event.cancel();
         } else if (MenuOverlay.isOpen()) {
            boolean pressOrRepeat = event.getAction() == 1 || event.getAction() == 2;
            if (pressOrRepeat && MenuOverlay.handleKey(event.getKey())) {
               event.cancel();
            } else if (event.getAction() == 1 && event.getKey() == 256) {
               MenuOverlay.close(mc);
               event.cancel();
            } else {
               event.cancel();
            }
         }
      }
   }

   @EventTarget(priority = 1000)
   public void onCharacterInput(CharacterInputEvent event) {
      if (MenuOverlay.handleCharacter(event.getCodePoint())) {
         event.cancel();
      }
   }

   @EventTarget(priority = 1000)
   public void onMouseInput(MouseInputEvent event) {
      if (MenuOverlay.handleMouseButton(mc, event.getButton(), event.getAction())) {
         event.cancel();
      }
   }

   @EventTarget(priority = 1000)
   public void onScreenKey(ScreenKeyEvent event) {
      if (event.getKeyEvent() == null || event.getKeyEvent().key() != 300) {
         if (MenuOverlay.blocksInput()) {
            event.cancel();
         }
      }
   }

   @EventTarget(priority = 1000)
   public void onScreenMouseButton(ScreenMouseButtonEvent event) {
      if (MenuOverlay.handleScreenMouseButton(mc, event.getMouseButtonEvent().button(), event.getAction())) {
         event.cancel();
      }
   }
}
