package win.furryhook.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.player.Player;
import win.furryhook.event.EventManager;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.lifecycle.DisconnectEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class TotemCounterElement extends StatRowElement {
   private static final double COUNT_RADIUS_SQR = 4096.0;
   private static final int MAX_ROWS = 3;
   private final Map<String, Integer> pops = new LinkedHashMap<>();
   private final AnimatedValue widthAnim = new AnimatedValue();
   private final List<StatRowElement.Segment> row = new ArrayList<>(3);

   public TotemCounterElement() {
      super("totemcounter", "Totem Counter");
      EventManager.subscribe(this);
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && event.getPacket() instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 35) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null) {
            if (packet.getEntity(mc.level) instanceof Player popped) {
               if (popped == mc.player || !(popped.distanceToSqr(mc.player) > 4096.0)) {
                  String name = popped.getGameProfile().name();
                  this.pops.merge(name, 1, Integer::sum);
               }
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.pops.clear();
   }

   @EventTarget
   public void onDisconnect(DisconnectEvent event) {
      this.pops.clear();
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      this.row.clear();
      if (!this.pops.isEmpty()) {
         List<Entry<String, Integer>> sorted = new ArrayList<>(this.pops.entrySet());
         sorted.sort(Entry.comparingByValue(Comparator.reverseOrder()));
         int limit = Math.min(sorted.size(), 3);

         for (int i = 0; i < limit; i++) {
            Entry<String, Integer> entry = sorted.get(i);
            this.row
               .add(
                  new StatRowElement.Segment(
                     Textures.Hud.ICON_TARGET,
                     false,
                     List.of(new StatRowElement.TextPart(entry.getKey(), false), new StatRowElement.TextPart(" ×" + entry.getValue(), true))
                  )
               );
         }
      }

      MsdfFont font = UiFonts.sfPro(UiFontStyle.MEDIUM.weight());
      float textSize = 7.5F * unit;
      float target = this.row.isEmpty() ? 0.0F : measureRow(font, this.row, textSize, unit);
      float width = this.widthAnim.update(target, 0.13F, now);
      this.width = width;
      this.height = width > 0.5F ? 16.0F * unit : 0.0F;
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      if (!(this.width <= 0.5F)) {
         float alpha = this.appearAlpha();
         float rowHeight = 16.0F * unit;
         this.drawRow(this.row, this.x, this.y, this.width, rowHeight, unit, alpha);
         this.drawShine(this.x, this.y, this.width, rowHeight, unit);
      }
   }
}
