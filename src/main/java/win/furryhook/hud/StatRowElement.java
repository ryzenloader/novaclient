package win.furryhook.hud;

import java.util.List;
import net.minecraft.resources.Identifier;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;

public abstract class StatRowElement extends HudElement {
   static final float ROW_HEIGHT = 16.0F;
   static final float PAD_X = 6.0F;
   static final float TEXT_SIZE = 7.5F;
   static final float HEAD_SIZE = 9.0F;
   static final float RADIUS = 5.0F;
   static final float ICON_SIZE = 6.5F;
   static final float ICON_GAP = 2.2F;
   static final float DSF_CROP_V0 = 0.0943F;
   static final float DSF_CROP_V1 = 0.9057F;

   protected StatRowElement(String id, String displayName) {
      super(id, displayName);
   }

   protected boolean accentBarOnRight() {
      return false;
   }

   static StatRowElement.Segment unit(Identifier icon, String value, String suffix) {
      return new StatRowElement.Segment(icon, false, List.of(new StatRowElement.TextPart(value, false), new StatRowElement.TextPart(suffix, true)));
   }

   static StatRowElement.Segment coordinates(int x, int y, int z) {
      return new StatRowElement.Segment(
         Textures.Hud.ICON_TARGET,
         false,
         List.of(
            new StatRowElement.TextPart("x", true),
            new StatRowElement.TextPart(Integer.toString(x), false),
            new StatRowElement.TextPart(" y", true),
            new StatRowElement.TextPart(Integer.toString(y), false),
            new StatRowElement.TextPart(" z", true),
            new StatRowElement.TextPart(Integer.toString(z), false)
         )
      );
   }

   static float measureRow(MsdfFont font, List<StatRowElement.Segment> segments, float textSize, float unit) {
      float width = 12.0F * unit;

      for (int i = 0; i < segments.size(); i++) {
         StatRowElement.Segment segment = segments.get(i);
         if (i > 0) {
            width += glassChevronWidth(unit);
         }

         if (segment.isPlayerHead()) {
            width += 11.2F * unit;
         } else if (segment.icon() != null) {
            width += 8.7F * unit;
         }

         for (StatRowElement.TextPart part : segment.parts()) {
            width += glassTextWidth(part.text(), textSize, UiFontStyle.MEDIUM);
         }
      }

      return width;
   }

   final void drawRow(List<StatRowElement.Segment> segments, float x, float y, float width, float rowHeight, float unit, float alpha) {
      this.drawWatermarkPanel(x, y, width, rowHeight, 5.0F * unit, unit, alpha);
      float textSize = 7.5F * unit;
      float centerY = y + rowHeight / 2.0F;
      float cursor = x + 6.0F * unit;

      for (int i = 0; i < segments.size(); i++) {
         StatRowElement.Segment segment = segments.get(i);
         if (i > 0) {
            cursor = drawGlassChevron(cursor, centerY, unit, alpha);
         }

         if (segment.isPlayerHead()) {
            float headSize = 9.0F * unit;
            Render2DUtil.texture(cursor, centerY - headSize / 2.0F, headSize, headSize, Textures.Hud.ICON_DSF)
               .uv(0.0F, 0.0943F, 1.0F, 0.9057F)
               .radius(headSize / 2.0F)
               .color(ColorUtil.multiplyAlpha(-1, alpha))
               .draw();
            cursor += headSize + 2.2F * unit;
         } else if (segment.icon() != null) {
            float iconSize = 6.5F * unit;
            Render2DUtil.texture(cursor, centerY - iconSize / 2.0F, iconSize, iconSize, segment.icon())
               .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), alpha))
               .draw();
            cursor += iconSize + 2.2F * unit;
         }

         for (StatRowElement.TextPart part : segment.parts()) {
            drawGlassText(part.text(), cursor, centerY, textSize, UiFontStyle.MEDIUM, part.secondary() ? glassMutedColor() : GLASS_TEXT_COLOR, alpha);
            cursor += glassTextWidth(part.text(), textSize, UiFontStyle.MEDIUM);
         }
      }
   }

   record Segment(Identifier icon, boolean isPlayerHead, List<StatRowElement.TextPart> parts) {
   }

   record TextPart(String text, boolean secondary) {
   }
}
