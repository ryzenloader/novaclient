package win.furryhook.event.events.game;

import lombok.Generated;
import net.minecraft.client.player.LocalPlayer;
import win.furryhook.event.Event;

public final class PlayerTickEvent extends Event {
   private LocalPlayer player;
   private PlayerTickEvent.Phase phase;

   public PlayerTickEvent set(LocalPlayer player, PlayerTickEvent.Phase phase) {
      this.player = player;
      this.phase = phase;
      return this;
   }

   public boolean isPre() {
      return this.phase == PlayerTickEvent.Phase.PRE;
   }

   public boolean isPost() {
      return this.phase == PlayerTickEvent.Phase.POST;
   }

   @Generated
   public LocalPlayer getPlayer() {
      return this.player;
   }

   @Generated
   public PlayerTickEvent.Phase getPhase() {
      return this.phase;
   }

   public enum Phase {
      PRE,
      POST;
   }
}
