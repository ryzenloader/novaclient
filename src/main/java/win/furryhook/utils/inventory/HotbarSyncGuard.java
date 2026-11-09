package win.furryhook.utils.inventory;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.mixin.accessor.MultiPlayerGameModeAccessor;

public final class HotbarSyncGuard implements MinecraftContext {
   public static final HotbarSyncGuard INSTANCE = new HotbarSyncGuard();
   private static final int MAX_WINDOW_TICKS = 40;
   private int activeWindows;
   private int windowAgeTicks;

   private HotbarSyncGuard() {
   }

   public static void begin() {
      if (INSTANCE.activeWindows++ == 0) {
         INSTANCE.windowAgeTicks = 0;
      }
   }

   public static void end() {
      if (--INSTANCE.activeWindows <= 0) {
         INSTANCE.activeWindows = 0;
         INSTANCE.windowAgeTicks = 0;
         INSTANCE.reassert();
      }
   }

   public static boolean isActive() {
      return INSTANCE.activeWindows > 0;
   }

   public static void reset() {
      INSTANCE.activeWindows = 0;
      INSTANCE.windowAgeTicks = 0;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.activeWindows > 0) {
         if (++this.windowAgeTicks > 40) {
            this.activeWindows = 0;
            this.windowAgeTicks = 0;
            this.reassert();
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (this.activeWindows > 0 && event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         if (event.getPacket() instanceof ClientboundSetHeldSlotPacket) {
            event.cancel();
         }
      }
   }

   private void reassert() {
      LocalPlayer player = this.player();
      if (player != null && mc.gameMode != null && mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
         accessor.invokerEnsureHasSentCarriedItem();
      }
   }
}
