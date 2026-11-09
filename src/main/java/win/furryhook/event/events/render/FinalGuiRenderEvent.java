package win.furryhook.event.events.render;

import lombok.Generated;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import win.furryhook.event.Event;

public final class FinalGuiRenderEvent extends Event {
   private Minecraft client;
   private Gui gui;
   private GuiGraphicsExtractor guiGraphicsExtractor;
   private DeltaTracker deltaTracker;
   private boolean renderHud;
   private boolean renderScreen;
   private int mouseX;
   private int mouseY;

   public FinalGuiRenderEvent set(
      Minecraft client,
      Gui gui,
      GuiGraphicsExtractor guiGraphicsExtractor,
      DeltaTracker deltaTracker,
      boolean renderHud,
      boolean renderScreen,
      int mouseX,
      int mouseY
   ) {
      this.client = client;
      this.gui = gui;
      this.guiGraphicsExtractor = guiGraphicsExtractor;
      this.deltaTracker = deltaTracker;
      this.renderHud = renderHud;
      this.renderScreen = renderScreen;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }

   @Generated
   public Gui getGui() {
      return this.gui;
   }

   @Generated
   public GuiGraphicsExtractor getGuiGraphicsExtractor() {
      return this.guiGraphicsExtractor;
   }

   @Generated
   public DeltaTracker getDeltaTracker() {
      return this.deltaTracker;
   }

   @Generated
   public boolean isRenderHud() {
      return this.renderHud;
   }

   @Generated
   public boolean isRenderScreen() {
      return this.renderScreen;
   }

   @Generated
   public int getMouseX() {
      return this.mouseX;
   }

   @Generated
   public int getMouseY() {
      return this.mouseY;
   }
}
