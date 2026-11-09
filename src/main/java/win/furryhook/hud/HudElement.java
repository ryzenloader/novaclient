package win.furryhook.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import win.furryhook.context.RenderContext;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.menu.core.MenuConfigStore;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public abstract class HudElement {
   private static final long APPEAR_MS = 200L;
   private final String id;
   private final String displayName;
   private final String configKeyX;
   private final String configKeyY;
   private final String configKeyAlwaysShow;
   private final String configKeySize;
   private float sizePreview = Float.NaN;
   protected float x;
   protected float y;
   protected float width;
   protected float height;
   private boolean dragging;
   private float dragOffsetX;
   private float dragOffsetY;
   private boolean wasHidden = true;
   private long appearStart;
   private boolean positionInitialized;
   private int lastScreenWidth = -1;
   private int lastScreenHeight = -1;
   private static final int SOLID_BACKGROUND = ColorUtil.rgba(19, 20, 22, 255);
   protected static final float GLASS_HEADER_HEIGHT = 16.0F;
   protected static final float GLASS_RADIUS = 5.0F;
   protected static final float GLASS_PAD = 6.0F;
   protected static final float GLASS_TITLE_SIZE = 7.5F;
   protected static final float GLASS_TEXT_SIZE = 7.0F;
   protected static final float GLASS_ROW_HEIGHT = 11.0F;
   protected static final float GLASS_ROW_GAP = 2.0F;
   protected static final float GLASS_BODY_TOP = 3.0F;
   protected static final float GLASS_BODY_BOTTOM = 5.0F;
   protected static final int GLASS_TEXT_COLOR = ColorUtil.rgb(232, 239, 251);
   private static final long SHINE_PERIOD_MS = 3200L;
   private static final float SHINE_WIDTH_UNITS = 26.0F;
   private static final float SHINE_ANGLE_RADIANS = (float)Math.toRadians(20.0);
   private static final int SHINE_COLOR = ColorUtil.rgba(255, 255, 255, 26);
   private static final int SHINE_TRANSPARENT = ColorUtil.rgba(255, 255, 255, 0);
   protected static final int DIVIDER_COLOR = ColorUtil.rgba(255, 255, 255, 25);

   protected HudElement(String id, String displayName) {
      this.id = id;
      this.displayName = displayName;
      this.configKeyX = "hud." + id + ".x";
      this.configKeyY = "hud." + id + ".y";
      this.configKeyAlwaysShow = "hud." + id + ".alwaysShow";
      this.configKeySize = "hud." + id + ".size";
   }

   public final String id() {
      return this.id;
   }

   public final String displayName() {
      return this.displayName;
   }

   protected final boolean alwaysShow() {
      return MenuConfigStore.getBoolean(this.configKeyAlwaysShow, false);
   }

   public final void setAlwaysShow(boolean value) {
      MenuConfigStore.save(data -> data.addProperty(this.configKeyAlwaysShow, value));
   }

   public final float sizePercent() {
      if (Float.isFinite(this.sizePreview)) {
         return this.sizePreview;
      }

      try {
         return normalizeSize(MenuConfigStore.getFloat(this.configKeySize, 100.0F));
      } catch (RuntimeException invalidConfig) {
         return 100.0F;
      }
   }

   private static float normalizeSize(float value) {
      return Float.isFinite(value) ? Math.clamp(Math.round(value), 50, 200) : 100.0F;
   }

   public final void previewSize(float value) {
      this.sizePreview = normalizeSize(value);
   }

   public final void commitSize() {
      if (Float.isFinite(this.sizePreview)) {
         float value = this.sizePreview;
         MenuConfigStore.save(data -> data.addProperty(this.configKeySize, value));
         this.sizePreview = Float.NaN;
      }
   }

   public final void setSizePercent(float value) {
      this.previewSize(value);
      this.commitSize();
   }

   public final boolean contains(float mouseX, float mouseY) {
      return this.width > 0.5F && this.height > 0.5F && mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
   }

   public final void render(Minecraft mc, float unit) {
      unit *= this.sizePercent() / 100.0F;
      this.layout(mc, unit);
      if (!(this.width <= 0.5F) && !(this.height <= 0.5F)) {
         if (this.wasHidden) {
            this.wasHidden = false;
            this.appearStart = System.currentTimeMillis();
         }

         if (this.customPosition()) {
            this.applyCustomPosition(mc, unit);
            this.draw(mc, unit);
         } else {
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            float freeWidth = Math.max(0.0F, screenWidth - this.width);
            float freeHeight = Math.max(0.0F, screenHeight - this.height);
            boolean contentResizeOnly = this.preservePositionOnContentResize()
               && this.positionInitialized
               && screenWidth == this.lastScreenWidth
               && screenHeight == this.lastScreenHeight;
            if (!this.dragging && !contentResizeOnly) {
               this.x = MenuConfigStore.getFloat(this.configKeyX, this.defaultX(unit) / Math.max(1.0F, freeWidth)) * freeWidth;
               this.y = MenuConfigStore.getFloat(this.configKeyY, this.defaultY(unit) / Math.max(1.0F, freeHeight)) * freeHeight;
            }

            this.positionInitialized = true;
            this.lastScreenWidth = screenWidth;
            this.lastScreenHeight = screenHeight;
            this.x = MathUtil.clamp(this.x, 0.0F, freeWidth);
            this.y = MathUtil.clamp(this.y, 0.0F, freeHeight);
            if (this.centerHorizontally()) {
               this.x = freeWidth / 2.0F;
            }

            this.draw(mc, unit);
         }
      } else {
         this.wasHidden = true;
      }
   }

   protected boolean customPosition() {
      return false;
   }

   protected void applyCustomPosition(Minecraft mc, float unit) {
   }

   protected boolean centerHorizontally() {
      return false;
   }

   protected boolean preservePositionOnContentResize() {
      return false;
   }

   protected final void drawPanelBacking(float unit, float radius, float alpha) {
      this.drawPanelBacking(this.x, this.y, this.width, this.height, radius, alpha);
   }

   protected final void drawPanelBacking(float panelX, float panelY, float panelWidth, float panelHeight, float radius, float alpha) {
      this.drawPanelBacking(panelX, panelY, panelWidth, panelHeight, radius, alpha, HudFeature.backgroundOpacity());
   }

   protected final void drawPanelBacking(float panelX, float panelY, float panelWidth, float panelHeight, float radius, float alpha, float opacity) {
      if (!(opacity <= 0.001F) && !(panelWidth <= 0.0F) && !(panelHeight <= 0.0F)) {
         Render2DUtil.rect(panelX, panelY, panelWidth, panelHeight).color(ColorUtil.multiplyAlpha(SOLID_BACKGROUND, alpha * opacity)).radius(radius).draw();
      }
   }

   protected final void drawGlassPanel(float panelX, float panelY, float panelWidth, float panelHeight, float radius, float unit, float alpha) {
      this.drawWatermarkPanel(panelX, panelY, panelWidth, panelHeight, radius, unit, alpha);
   }

   protected final void drawGlassPanel(float unit, float radius, float alpha) {
      this.drawGlassPanel(this.x, this.y, this.width, this.height, radius, unit, alpha);
   }

   protected static int glassMutedColor() {
      return ColorUtil.lerp(GLASS_TEXT_COLOR, ColorUtil.withAlpha(HudFeature.hudAccent(), 255), 0.4F);
   }

   protected final void drawWatermarkPanel(float panelX, float panelY, float panelWidth, float panelHeight, float radius, float unit, float alpha) {
      if (!(panelWidth <= 0.0F) && !(panelHeight <= 0.0F)) {
         int tint = ColorUtil.withAlpha(HudFeature.panelColor(), HudFeature.panelOpacity());
         int edge = ColorUtil.withAlpha(glassMutedColor(), 27);
         Render2DUtil.RectBuilder plate = Render2DUtil.rect(panelX, panelY, panelWidth, panelHeight)
            .color(ColorUtil.multiplyAlpha(tint, alpha))
            .radius(radius)
            .border(0.4F * unit, ColorUtil.multiplyAlpha(edge, alpha));
         if (HudFeature.glowEnabled() && HudFeature.glowRadius() > 0.0F) {
            plate.shadow(ColorUtil.withAlpha(HudFeature.hudAccent(), alpha * HudFeature.glowOpacity() * 0.25F), HudFeature.glowRadius() * unit);
         }

         if (HudFeature.blurEnabled() && HudFeature.blurRadius() > 0.0F) {
            plate.blur(HudFeature.blurRadius() * unit, alpha);
         }

         plate.draw();
      }
   }

   protected static MsdfFont glassFont(String text, UiFontStyle style) {
      MsdfFont font = UiFonts.sfPro(style.weight());
      return font.canRender(text) ? font : UiFonts.getFontForText(text, style.weight());
   }

   protected static float glassTextWidth(String text, float size, UiFontStyle style) {
      return glassFont(text, style).measureWidth(text, size, size * style.letterSpacingEm());
   }

   protected static void drawGlassText(String text, float x, float centerY, float size, UiFontStyle style, int color, float alpha) {
      MsdfFont font = glassFont(text, style);
      Render2DUtil.text(x, font.centeredTextY(centerY, size), size, text).font(font).style(style).color(ColorUtil.multiplyAlpha(color, alpha)).draw();
   }

   protected static String glassEllipsize(String text, float size, UiFontStyle style, float budget) {
      return budget <= glassTextWidth("...", size, style) ? "" : glassFont(text, style).ellipsize(text, size, size * style.letterSpacingEm(), budget);
   }

   protected static float glassBadgeWidth(String text, float size, float unit) {
      return glassTextWidth(text, size, UiFontStyle.MEDIUM) + 6.0F * unit;
   }

   protected final float drawGlassBadge(String text, float right, float centerY, float size, float unit, int color, float alpha) {
      float width = glassBadgeWidth(text, size, unit);
      float height = 10.0F * unit;
      Render2DUtil.rect(right - width, centerY - height * 0.5F, width, height)
         .color(ColorUtil.multiplyAlpha(ColorUtil.withAlpha(color, 22), alpha))
         .radius(3.0F * unit)
         .draw();
      drawGlassText(text, right - width + 3.0F * unit, centerY, size, UiFontStyle.MEDIUM, color, alpha);
      return right - width;
   }

   protected final void drawGlassListHeader(String title, Identifier icon, int count, float unit, float alpha) {
      this.drawWatermarkPanel(this.x, this.y, this.width, this.height, 5.0F * unit, unit, alpha);
      float centerY = this.y + 16.0F * unit * 0.5F;
      float iconSize = 8.0F * unit;
      float cursor = this.x + 6.0F * unit;
      int accent = HudFeature.hudAccent();
      Render2DUtil.texture(cursor, centerY - iconSize * 0.5F, iconSize, iconSize, icon).color(ColorUtil.multiplyAlpha(accent, alpha)).draw();
      cursor += iconSize;
      cursor = drawGlassChevron(cursor, centerY, unit, alpha);
      drawGlassText(title, cursor, centerY, 7.5F * unit, UiFontStyle.SEMIBOLD, accent, alpha);
      String label = Integer.toString(count);
      float countSize = 6.0F * unit;
      drawGlassText(
         label,
         this.x + this.width - 6.0F * unit - glassTextWidth(label, countSize, UiFontStyle.MEDIUM),
         centerY,
         countSize,
         UiFontStyle.MEDIUM,
         glassMutedColor(),
         alpha
      );
   }

   protected final boolean glassRowFits(float centerY, float rowHeight, float unit) {
      return centerY + rowHeight * 0.5F <= this.y + this.height - 2.0F * unit;
   }

   protected static float glassChevronWidth(float unit) {
      return 7.4F * unit;
   }

   protected static float drawGlassChevron(float cursor, float centerY, float unit, float alpha) {
      int color = ColorUtil.multiplyAlpha(glassMutedColor(), alpha);
      float x = cursor + 2.5F * unit;

      for (int i = 0; i < 2; i++) {
         float left = x + (i * 1.3F + 0.2F) * unit;
         float right = left + 0.7F * unit;
         glassStroke(left, centerY - 0.85F * unit, right, centerY, 0.4F * unit, color);
         glassStroke(right, centerY, left, centerY + 0.85F * unit, 0.4F * unit, color);
      }

      return cursor + glassChevronWidth(unit);
   }

   private static void glassStroke(float x1, float y1, float x2, float y2, float thickness, int color) {
      GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
      if (extractor != null) {
         float dx = x2 - x1;
         float dy = y2 - y1;
         Matrix3x2fStack pose = extractor.pose();
         pose.pushMatrix();
         pose.translate(x1, y1);
         pose.rotate((float)Math.atan2(dy, dx));
         float scale = Math.min(1.0F, thickness * 0.5F);
         pose.scale(scale);
         Render2DUtil.rect(-thickness * 0.5F / scale, -thickness * 0.5F / scale, ((float)Math.hypot(dx, dy) + thickness) / scale, thickness / scale)
            .color(color)
            .radius(thickness * 0.5F / scale)
            .draw();
         pose.popMatrix();
      }
   }

   protected final void drawShine(float unit) {
      this.drawShine(this.x, this.y, this.width, this.height, unit);
   }

   protected final void drawShine(float panelX, float panelY, float panelWidth, float panelHeight, float unit) {
      if (!(panelWidth <= 1.0F) && !(panelHeight <= 1.0F) && HudFeature.glareEnabled()) {
         GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
         if (extractor != null) {
            long now = System.currentTimeMillis();
            float progress = (float)(now % 3200L) / 3200.0F;
            float barWidth = 26.0F * unit;
            float half = barWidth / 2.0F;
            float travel = panelWidth + barWidth * 2.0F;
            float barX = panelX - barWidth + progress * travel;
            float centerX = barX + half;
            float centerY = panelY + panelHeight / 2.0F;
            float barHeight = panelHeight * 1.8F;
            float barTop = centerY - barHeight / 2.0F;
            Render2DUtil.flush();
            Render2DUtil.pushScissor(panelX, panelY, panelWidth, panelHeight);
            Matrix3x2fStack pose = extractor.pose();
            pose.pushMatrix();
            pose.translate(centerX, centerY);
            pose.rotate(SHINE_ANGLE_RADIANS);
            pose.translate(-centerX, -centerY);
            Render2DUtil.rect(barX, barTop, half, barHeight).color(SHINE_TRANSPARENT, SHINE_COLOR, SHINE_COLOR, SHINE_TRANSPARENT).draw();
            Render2DUtil.rect(barX + half, barTop, half, barHeight).color(SHINE_COLOR, SHINE_TRANSPARENT, SHINE_TRANSPARENT, SHINE_COLOR).draw();
            Render2DUtil.flush();
            pose.popMatrix();
            Render2DUtil.popScissor();
         }
      }
   }

   protected final float appearAlpha() {
      float progress = MathUtil.clamp01((float)(System.currentTimeMillis() - this.appearStart) / 200.0F);
      return progress * progress * (3.0F - 2.0F * progress);
   }

   protected final float divider(float cursor, float centerY, float heightUnits, float gapUnits, float unit, float alpha) {
      cursor += gapUnits * unit;
      Render2DUtil.rect(cursor, centerY - heightUnits * unit / 2.0F, 1.0F * unit, heightUnits * unit)
         .color(ColorUtil.multiplyAlpha(DIVIDER_COLOR, alpha))
         .draw();
      return cursor + (1.0F + gapUnits) * unit;
   }

   protected abstract void layout(Minecraft var1, float var2);

   protected float defaultX(float unit) {
      return 20.0F * unit;
   }

   protected float defaultY(float unit) {
      return 20.0F * unit;
   }

   protected abstract void draw(Minecraft var1, float var2);

   protected static boolean showcase(Minecraft mc) {
      return mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof InventoryScreen;
   }

   public final boolean startDrag(float mouseX, float mouseY) {
      if (this.customPosition()) {
         return false;
      } else if (!(mouseX < this.x) && !(mouseX > this.x + this.width) && !(mouseY < this.y) && !(mouseY > this.y + this.height)) {
         this.dragging = true;
         this.dragOffsetX = mouseX - this.x;
         this.dragOffsetY = mouseY - this.y;
         return true;
      } else {
         return false;
      }
   }

   public final void dragTo(float mouseX, float mouseY) {
      if (!this.centerHorizontally()) {
         this.x = mouseX - this.dragOffsetX;
      }

      this.y = mouseY - this.dragOffsetY;
   }

   public final boolean isDragging() {
      return this.dragging;
   }

   public final float x() {
      return this.x;
   }

   public final float y() {
      return this.y;
   }

   public final float width() {
      return this.width;
   }

   public final float height() {
      return this.height;
   }

   public final boolean isHorizontallyCentered() {
      return this.centerHorizontally();
   }

   public final void snapTo(float x, float y) {
      if (this.dragging) {
         if (!this.centerHorizontally()) {
            this.x = x;
         }

         this.y = y;
      }
   }

   public final boolean overlaps(float x, float y, float width, float height) {
      return this.width > 0.5F && this.height > 0.5F && x < this.x + this.width && x + width > this.x && y < this.y + this.height && y + height > this.y;
   }

   public final void resetPosition() {
      this.dragging = false;
      this.positionInitialized = false;
      this.lastScreenWidth = -1;
      this.lastScreenHeight = -1;
   }

   public final void stopDrag(Minecraft mc) {
      if (this.dragging) {
         this.dragging = false;
         float freeWidth = Math.max(1.0F, mc.getWindow().getGuiScaledWidth() - this.width);
         float freeHeight = Math.max(1.0F, mc.getWindow().getGuiScaledHeight() - this.height);
         float fractionX = MathUtil.clamp01(this.x / freeWidth);
         float fractionY = MathUtil.clamp01(this.y / freeHeight);
         MenuConfigStore.save(data -> {
            data.addProperty(this.configKeyX, fractionX);
            data.addProperty(this.configKeyY, fractionY);
         });
      }
   }
}
