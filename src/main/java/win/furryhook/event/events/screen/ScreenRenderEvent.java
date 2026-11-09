package win.furryhook.event.events.screen;

import lombok.Generated;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import win.furryhook.event.CancellableEvent;

public final class ScreenRenderEvent extends CancellableEvent {
   private Screen screen;
   private GuiGraphicsExtractor guiGraphicsExtractor;
   private int mouseX;
   private int mouseY;
   private float partialTick;

   public ScreenRenderEvent set(Screen screen, GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
      this.screen = screen;
      this.guiGraphicsExtractor = guiGraphicsExtractor;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.partialTick = partialTick;
      return this;
   }

   @Generated
   public Screen getScreen() {
      return this.screen;
   }

   @Generated
   public GuiGraphicsExtractor getGuiGraphicsExtractor() {
      return this.guiGraphicsExtractor;
   }

   @Generated
   public int getMouseX() {
      return this.mouseX;
   }

   @Generated
   public int getMouseY() {
      return this.mouseY;
   }

   @Generated
   public float getPartialTick() {
      return this.partialTick;
   }
}
