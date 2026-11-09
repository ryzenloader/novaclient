package win.furryhook.event.events.render;

import lombok.Generated;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import win.furryhook.event.Event;

public final class Render2DEvent extends Event {
   private Minecraft client;
   private Gui gui;
   private GuiGraphicsExtractor guiGraphicsExtractor;
   private DeltaTracker deltaTracker;

   public Render2DEvent set(Minecraft client, Gui gui, GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker) {
      this.client = client;
      this.gui = gui;
      this.guiGraphicsExtractor = guiGraphicsExtractor;
      this.deltaTracker = deltaTracker;
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
}
