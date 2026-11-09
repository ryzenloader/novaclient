package win.furryhook.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class BottomStatsElement extends StatRowElement {
   public static final String ITEM_COORDINATES = "Coordinates";
   public static final String ITEM_BPS = "BPS";
   private static final float MARGIN = 12.0F;
   private static final float CHAT_LIFT = 64.0F;
   private final AnimatedValue widthAnim = new AnimatedValue();
   private final AnimatedValue bpsAnim = new AnimatedValue();
   private final AnimatedValue liftAnim = new AnimatedValue();
   private final List<StatRowElement.Segment> row = new ArrayList<>(2);

   public BottomStatsElement() {
      super("bottom_stats", "Coordinates");
   }

   @Override
   protected boolean customPosition() {
      return true;
   }

   @Override
   protected void applyCustomPosition(Minecraft mc, float unit) {
      boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
      float lift = this.liftAnim.update(chatOpen ? 64.0F * unit : 0.0F, 0.18F, System.nanoTime());
      this.x = 12.0F * unit;
      this.y = mc.getWindow().getGuiScaledHeight() - this.height - 12.0F * unit - lift;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      double rawBps = mc.player != null ? mc.player.getDeltaMovement().horizontalDistance() * 20.0 : 0.0;
      float bps = this.bpsAnim.update((float)rawBps, 0.08F, now);
      this.row.clear();
      if (HudFeature.informationShows("Coordinates") && mc.player != null) {
         this.row.add(coordinates((int)Math.floor(mc.player.getX()), (int)Math.floor(mc.player.getY()), (int)Math.floor(mc.player.getZ())));
      }

      if (HudFeature.informationShows("BPS")) {
         this.row.add(unit(Textures.Hud.ICON_MOVEMENT, String.format(Locale.ROOT, "%.1f", bps), " bps"));
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
