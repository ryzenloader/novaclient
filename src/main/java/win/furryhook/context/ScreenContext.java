package win.furryhook.context;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public interface ScreenContext extends MinecraftContext {
   default Screen currentScreen() {
      return this.screen();
   }

   default boolean hasScreen() {
      return this.currentScreen() != null;
   }

   default boolean hasContainerScreen() {
      return this.currentScreen() instanceof AbstractContainerScreen;
   }

   default AbstractContainerScreen<?> containerScreen() {
      return this.currentScreen() instanceof AbstractContainerScreen<?> containerScreen ? containerScreen : null;
   }
}
