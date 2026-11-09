package win.furryhook.event.events.render;

import lombok.Generated;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import win.furryhook.event.Event;

public final class Render3DEvent extends Event {
   private Minecraft client;
   private GameRenderer gameRenderer;
   private DeltaTracker deltaTracker;

   public Render3DEvent set(Minecraft client, GameRenderer gameRenderer, DeltaTracker deltaTracker) {
      this.client = client;
      this.gameRenderer = gameRenderer;
      this.deltaTracker = deltaTracker;
      return this;
   }

   @Generated
   public Minecraft getClient() {
      return this.client;
   }

   @Generated
   public GameRenderer getGameRenderer() {
      return this.gameRenderer;
   }

   @Generated
   public DeltaTracker getDeltaTracker() {
      return this.deltaTracker;
   }
}
