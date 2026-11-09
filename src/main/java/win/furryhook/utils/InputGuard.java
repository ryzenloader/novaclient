package win.furryhook.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class InputGuard {
   private InputGuard() {
   }

   public static boolean isBindingBlocked() {
      Screen screen = Minecraft.getInstance().gui.screen();
      if (screen == null) {
         return false;
      } else {
         return !(screen instanceof AbstractContainerScreen) ? true : screen.getFocused() instanceof EditBox editBox && editBox.isFocused();
      }
   }
}
