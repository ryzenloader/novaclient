package win.furryhook.context;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

public interface InventoryContext extends ScreenContext {
   default AbstractContainerMenu menu() {
      AbstractContainerScreen<?> containerScreen = this.containerScreen();
      return containerScreen != null ? containerScreen.getMenu() : null;
   }

   default boolean hasMenu() {
      return this.menu() != null;
   }
}
