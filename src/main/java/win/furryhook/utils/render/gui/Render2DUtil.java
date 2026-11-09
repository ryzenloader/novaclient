package win.furryhook.utils.render.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import win.furryhook.context.MinecraftContext;
import win.furryhook.context.RenderContext;
import win.furryhook.mixin.accessor.GuiGraphicsExtractorAccessor;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Theme;

public final class Render2DUtil {
   private static final Render2DUtil.FrameState FRAME_STATE = new Render2DUtil.FrameState();
   private static final Deque<ScreenRectangle> SCISSORS = new ArrayDeque<>();
   private static float backdropBlurScale = 1.0F;

   private Render2DUtil() {
   }

   public static void setBackdropBlurScale(float scale) {
      backdropBlurScale = Math.max(0.0F, Math.min(1.0F, scale));
   }

   public static Render2DUtil.RectBuilder rect(float x, float y, float width, float height) {
      return new Render2DUtil.RectBuilder(x, y, width, height);
   }

   public static Render2DUtil.TextBuilder text(float x, float y, float size, String text) {
      return new Render2DUtil.TextBuilder(x, y, size, text);
   }

   public static Render2DUtil.TextureBuilder texture(float x, float y, float width, float height, Identifier textureId) {
      return new Render2DUtil.TextureBuilder(x, y, width, height, textureId);
   }

   public static Render2DUtil.ColorGridBuilder colorGrid(float x, float y, float cellSize, int columns, int[] colors) {
      return new Render2DUtil.ColorGridBuilder(x, y, cellSize, columns, colors);
   }

   public static void beginFrame() {
      Render2DUtil.FrameState frameState = FRAME_STATE;
      frameState.queued.clear();
      frameState.pendingCount = 0;
      SCISSORS.clear();
   }

   public static void pushScissor(float x, float y, float width, float height) {
      int scissorX = Math.round(x);
      int scissorY = Math.round(y);
      int scissorWidth = Math.max(0, Math.round(x + width) - scissorX);
      int scissorHeight = Math.max(0, Math.round(y + height) - scissorY);
      ScreenRectangle next = new ScreenRectangle(scissorX, scissorY, scissorWidth, scissorHeight);
      if (!SCISSORS.isEmpty()) {
         next = SCISSORS.peek().intersection(next);
         if (next == null) {
            next = ScreenRectangle.empty();
         }
      }

      if (next.width() > 0 && next.height() > 0) {
         int left = Math.max(0, next.left());
         int top = Math.max(0, next.top());
         int clampedWidth = next.right() - left;
         int clampedHeight = next.bottom() - top;
         next = clampedWidth > 0 && clampedHeight > 0 ? new ScreenRectangle(left, top, clampedWidth, clampedHeight) : ScreenRectangle.empty();
      }

      if (next.width() > 0 && next.height() > 0) {
         Window window = MinecraftContext.mc.getWindow();
         ScreenRectangle screen = new ScreenRectangle(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
         if (screen.intersection(next) == null) {
            next = ScreenRectangle.empty();
         }
      }

      SCISSORS.push(next);
   }

   public static void popScissor() {
      if (SCISSORS.isEmpty()) {
         throw new IllegalStateException("No active render scissor");
      }

      SCISSORS.pop();
   }

   public static boolean isPointScissored(float x, float y) {
      if (SCISSORS.isEmpty()) {
         return false;
      }

      ScreenRectangle scissor = SCISSORS.peek();
      return x < scissor.left() || x >= scissor.right() || y < scissor.top() || y >= scissor.bottom();
   }

   private static ScreenRectangle currentScissor() {
      return SCISSORS.peek();
   }

   private static boolean hasEmptyScissor() {
      ScreenRectangle scissor = currentScissor();
      return scissor != null && (scissor.width() <= 0 || scissor.height() <= 0);
   }

   public static int flush() {
      Render2DUtil.FrameState frameState = FRAME_STATE;
      GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
      if (extractor == null) {
         frameState.queued.clear();
         frameState.lastFlushedCount = 0;
         frameState.pendingCount = 0;
         return 0;
      }

      GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor)extractor;

      for (GuiElementRenderState renderState : frameState.queued) {
         accessor.getGuiRenderState().addGuiElement(renderState);
      }

      int flushed = frameState.queued.size();
      frameState.queued.clear();
      frameState.pendingCount = 0;
      frameState.lastFlushedCount = flushed;
      return flushed;
   }

   public static int pendingDrawCount() {
      return FRAME_STATE.pendingCount;
   }

   public static int lastFlushedDrawCount() {
      return FRAME_STATE.lastFlushedCount;
   }

   public static final class ColorGridBuilder {
      private final float x;
      private final float y;
      private final float cellSize;
      private final int columns;
      private final int[] colors;
      private int color = -1;
      private float radius;
      private int selectedIndex = -1;
      private float selectedThickness;
      private int hoveredIndex = -1;
      private float hoveredThickness;

      private ColorGridBuilder(float x, float y, float cellSize, int columns, int[] colors) {
         this.x = x;
         this.y = y;
         this.cellSize = cellSize;
         this.columns = columns;
         this.colors = Objects.requireNonNull(colors, "colors");
      }

      public Render2DUtil.ColorGridBuilder radius(float radius) {
         this.radius = Math.max(0.0F, radius);
         return this;
      }

      public Render2DUtil.ColorGridBuilder color(int color) {
         this.color = color;
         return this;
      }

      public Render2DUtil.ColorGridBuilder selected(int index, float thickness) {
         this.selectedIndex = index;
         this.selectedThickness = Math.max(0.0F, thickness);
         return this;
      }

      public Render2DUtil.ColorGridBuilder hovered(int index, float thickness) {
         this.hoveredIndex = index;
         this.hoveredThickness = Math.max(0.0F, thickness);
         return this;
      }

      public void draw() {
         if (!(this.cellSize <= 0.0F) && this.columns > 0 && this.colors.length != 0 && !Render2DUtil.hasEmptyScissor()) {
            if (!RenderContext.isIn2D()) {
               throw new IllegalStateException("Render2DUtil.colorGrid().draw() can only be used inside a 2D render context");
            }

            GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
            if (extractor == null) {
               throw new IllegalStateException("Render2DUtil.colorGrid().draw() requires an active GuiGraphicsExtractor");
            }

            Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
            frameState.queued
               .add(
                  new ColorGridRenderState(
                     extractor.pose(),
                     this.x,
                     this.y,
                     this.cellSize,
                     this.columns,
                     this.radius,
                     this.color,
                     this.selectedIndex,
                     this.selectedThickness,
                     this.hoveredIndex,
                     this.hoveredThickness,
                     this.colors,
                     Render2DUtil.currentScissor()
                  )
               );
            frameState.pendingCount = frameState.queued.size();
         }
      }
   }

   private static final class FrameState {
      private final List<GuiElementRenderState> queued = new ArrayList<>(128);
      private int pendingCount;
      private int lastFlushedCount;
   }

   public static final class RectBuilder {
      private static final float BLUR_TINT_SCALE = 0.8F;
      private final float x;
      private final float y;
      private final float width;
      private final float height;
      private int topLeftColor = -1;
      private int topRightColor = -1;
      private int bottomRightColor = -1;
      private int bottomLeftColor = -1;
      private float topLeftRadius;
      private float topRightRadius;
      private float bottomRightRadius;
      private float bottomLeftRadius;
      private float borderThickness;
      private int borderColor = 0;
      private int shadowColor = 0;
      private float shadowBlur;
      private float backdropBlurRadius;
      private float backdropBlurOpacity = 1.0F;
      private float liquidAmount;
      private float liquidBevel = -1.0F;

      private RectBuilder(float x, float y, float width, float height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }

      public Render2DUtil.RectBuilder color(int color) {
         return this.color(color, color, color, color);
      }

      public Render2DUtil.RectBuilder color(int topLeftColor, int topRightColor, int bottomRightColor, int bottomLeftColor) {
         this.topLeftColor = topLeftColor;
         this.topRightColor = topRightColor;
         this.bottomRightColor = bottomRightColor;
         this.bottomLeftColor = bottomLeftColor;
         return this;
      }

      public Render2DUtil.RectBuilder radius(float radius) {
         return this.radius(radius, radius, radius, radius);
      }

      public Render2DUtil.RectBuilder radius(float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {
         this.topLeftRadius = Math.max(0.0F, topLeftRadius);
         this.topRightRadius = Math.max(0.0F, topRightRadius);
         this.bottomRightRadius = Math.max(0.0F, bottomRightRadius);
         this.bottomLeftRadius = Math.max(0.0F, bottomLeftRadius);
         return this;
      }

      public Render2DUtil.RectBuilder border(float thickness, int color) {
         this.borderThickness = Math.max(0.0F, thickness);
         this.borderColor = color;
         return this;
      }

      public Render2DUtil.RectBuilder shadow(int color, float blur) {
         this.shadowColor = color;
         this.shadowBlur = Math.max(0.0F, blur);
         return this;
      }

      public Render2DUtil.RectBuilder glass(float alpha, float borderThickness, float blurRadius) {
         return this.color(ColorUtil.multiplyAlpha(Theme.Colors.BACKGROUND_PRIMARY_50, alpha))
            .border(Math.max(0.5F, borderThickness), ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha))
            .blur(blurRadius, alpha);
      }

      public Render2DUtil.RectBuilder flatPanel(float alpha, float borderThickness) {
         return this.color(ColorUtil.multiplyAlpha(Theme.Colors.BACKGROUND_PRIMARY_50, alpha))
            .border(Math.max(0.5F, borderThickness), ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha));
      }

      public Render2DUtil.RectBuilder liquidGlass(float alpha, float borderThickness, float blurRadius) {
         return this.glass(alpha, borderThickness, blurRadius).liquid(alpha);
      }

      public Render2DUtil.RectBuilder liquid() {
         return this.liquid(1.0F);
      }

      public Render2DUtil.RectBuilder liquid(float amount) {
         this.liquidAmount = Math.max(0.0F, Math.min(1.0F, amount));
         return this;
      }

      public Render2DUtil.RectBuilder liquidBevel(float bevelPx) {
         this.liquidBevel = Math.max(0.0F, bevelPx);
         return this;
      }

      public Render2DUtil.RectBuilder blur(float radius) {
         return this.blur(radius, 1.0F);
      }

      public Render2DUtil.RectBuilder blur(float radius, float opacity) {
         this.backdropBlurRadius = Math.max(0.0F, radius);
         this.backdropBlurOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         return this;
      }

      public void draw() {
         if (!(this.width <= 0.0F) && !(this.height <= 0.0F) && !Render2DUtil.hasEmptyScissor()) {
            if (!RenderContext.isIn2D()) {
               throw new IllegalStateException("Render2DUtil.draw() can only be used inside a 2D render context");
            }

            GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
            if (extractor == null) {
               throw new IllegalStateException("Render2DUtil.draw() requires an active GuiGraphicsExtractor");
            }

            Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
            this.queueShadow(frameState, extractor);
            if (!this.queueBackdropBlur(frameState, extractor)) {
               RectRenderState renderState = new RectRenderState(
                  extractor.pose(),
                  this.x,
                  this.y,
                  this.x + this.width,
                  this.y + this.height,
                  this.topLeftColor,
                  this.bottomLeftColor,
                  this.bottomRightColor,
                  this.topRightColor,
                  this.topLeftRadius,
                  this.topRightRadius,
                  this.bottomRightRadius,
                  this.bottomLeftRadius,
                  this.borderThickness,
                  this.borderColor,
                  Render2DUtil.currentScissor()
               );
               frameState.queued.add(renderState);
               frameState.pendingCount = frameState.queued.size();
            }
         }
      }

      private boolean queueBackdropBlur(Render2DUtil.FrameState frameState, GuiGraphicsExtractor extractor) {
         float effectiveBlurRadius = this.backdropBlurRadius * Render2DUtil.backdropBlurScale;
         if (!(effectiveBlurRadius <= 0.05F) && !(this.backdropBlurOpacity <= 0.01F)) {
            GpuTextureView backdropView = GuiBackdrop.acquireView();
            if (backdropView == null) {
               return false;
            }

            int guiScale = Math.max(1, MinecraftContext.mc.getWindow().getGuiScale());
            int tintAlpha = Math.round((this.topLeftColor >>> 24 & 0xFF) * 0.8F);
            float liquid = LiquidGlass.enabled() ? this.liquidAmount * LiquidGlass.intensity() : 0.0F;
            float bevel = 0.0F;
            float lightX = 0.0F;
            float lightY = 0.0F;
            if (liquid > 0.004F) {
               bevel = this.liquidBevel >= 0.0F ? this.liquidBevel : LiquidGlass.bevelFor(this.topLeftRadius);
               bevel = Math.min(bevel, Math.min(this.width, this.height) * 0.5F) * liquid;
               float centerX = this.x + this.width * 0.5F;
               float centerY = this.y + this.height * 0.5F;
               lightX = LiquidGlass.lightX(centerX, centerY);
               lightY = LiquidGlass.lightY(centerX, centerY);
            }

            frameState.queued
               .add(
                  new BlurRectRenderState(
                     extractor.pose(),
                     this.x,
                     this.y,
                     this.x + this.width,
                     this.y + this.height,
                     this.topLeftColor & 16777215 | tintAlpha << 24,
                     this.topLeftRadius,
                     effectiveBlurRadius * guiScale,
                     this.backdropBlurOpacity,
                     bevel,
                     liquid * LiquidGlass.specular(),
                     liquid * LiquidGlass.dispersion(),
                     liquid * LiquidGlass.frost(),
                     lightX,
                     lightY,
                     backdropView,
                     Render2DUtil.currentScissor()
                  )
               );
            if (this.borderThickness > 0.0F && this.borderColor >>> 24 != 0) {
               frameState.queued
                  .add(
                     new RectRenderState(
                        extractor.pose(),
                        this.x,
                        this.y,
                        this.x + this.width,
                        this.y + this.height,
                        0,
                        this.topLeftRadius,
                        this.borderThickness,
                        this.borderColor,
                        Render2DUtil.currentScissor()
                     )
                  );
            }

            frameState.pendingCount = frameState.queued.size();
            return true;
         } else {
            return false;
         }
      }

      private void queueShadow(Render2DUtil.FrameState frameState, GuiGraphicsExtractor extractor) {
         int alpha = this.shadowColor >>> 24 & 0xFF;
         if (alpha != 0 && !(this.shadowBlur <= 0.0F)) {
            boolean glass = this.backdropBlurRadius * Render2DUtil.backdropBlurScale > 0.05F && this.backdropBlurOpacity > 0.01F;
            RectRenderState shadowState = glass
               ? RectRenderState.glassShadow(
                  extractor.pose(),
                  this.x,
                  this.y,
                  this.x + this.width,
                  this.y + this.height,
                  this.topLeftRadius,
                  this.topRightRadius,
                  this.bottomRightRadius,
                  this.bottomLeftRadius,
                  this.shadowBlur,
                  this.shadowColor,
                  Render2DUtil.currentScissor()
               )
               : RectRenderState.shadow(
                  extractor.pose(),
                  this.x,
                  this.y,
                  this.x + this.width,
                  this.y + this.height,
                  this.topLeftRadius,
                  this.topRightRadius,
                  this.bottomRightRadius,
                  this.bottomLeftRadius,
                  this.shadowBlur,
                  this.shadowColor,
                  Render2DUtil.currentScissor()
               );
            frameState.queued.add(shadowState);
         }
      }
   }

   public static final class TextBuilder {
      private final float x;
      private final float y;
      private final float size;
      private final String text;
      private MsdfFont font;
      private MsdfFontFamily family;
      private int weight = -1;
      private UiFontStyle fontStyle = UiFontStyle.REGULAR;
      private int color = -1;
      private int outlineColor = 0;
      private float outlineThickness;
      private TextAlign align = TextAlign.LEFT;
      private float offsetX;
      private float offsetY;
      private float scale = 1.0F;
      private boolean forceVanilla;

      private TextBuilder(float x, float y, float size, String text) {
         this.x = x;
         this.y = y;
         this.size = size;
         this.text = Objects.requireNonNull(text, "text");
      }

      public Render2DUtil.TextBuilder font(Identifier fontId) {
         this.font = MsdfFont.load(Objects.requireNonNull(fontId, "fontId"));
         return this;
      }

      public Render2DUtil.TextBuilder font(MsdfFont font) {
         this.font = Objects.requireNonNull(font, "font");
         return this;
      }

      public Render2DUtil.TextBuilder family(MsdfFontFamily family) {
         this.family = Objects.requireNonNull(family, "family");
         return this;
      }

      public Render2DUtil.TextBuilder weight(int weight) {
         if (weight >= 1 && weight <= 1000) {
            this.weight = weight;
            return this;
         } else {
            throw new IllegalArgumentException("Font weight out of range: " + weight);
         }
      }

      public Render2DUtil.TextBuilder style(UiFontStyle fontStyle) {
         this.fontStyle = Objects.requireNonNull(fontStyle, "fontStyle");
         return this;
      }

      public Render2DUtil.TextBuilder color(int color) {
         this.color = color;
         return this;
      }

      public Render2DUtil.TextBuilder outline(int color, float thickness) {
         this.outlineColor = color;
         this.outlineThickness = thickness;
         return this;
      }

      public Render2DUtil.TextBuilder align(TextAlign align) {
         this.align = Objects.requireNonNull(align, "align");
         return this;
      }

      public Render2DUtil.TextBuilder offset(float offsetX, float offsetY) {
         this.offsetX = offsetX;
         this.offsetY = offsetY;
         return this;
      }

      public Render2DUtil.TextBuilder scale(float scale) {
         this.scale = Math.max(1.0E-4F, scale);
         return this;
      }

      public Render2DUtil.TextBuilder vanilla() {
         this.forceVanilla = true;
         return this;
      }

      public void draw() {
         if (!this.text.isEmpty() && !(this.size <= 0.0F) && !(this.scale <= 0.001F) && !Render2DUtil.hasEmptyScissor()) {
            if (!RenderContext.isIn2D()) {
               throw new IllegalStateException("Render2DUtil.text().draw() can only be used inside a 2D render context");
            }

            GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
            if (extractor == null) {
               throw new IllegalStateException("Render2DUtil.text().draw() requires an active GuiGraphicsExtractor");
            }

            if (this.forceVanilla) {
               this.drawVanilla(extractor);
            } else {
               MsdfFont font = this.resolveFont();
               float effectiveSize = this.size * this.scale;
               MsdfFont.Paragraph paragraph = font.shape(this.text);
               if (paragraph.missingGlyphs()) {
                  this.drawVanillaFallback(extractor);
               } else {
                  float letterSpacing = effectiveSize * this.fontStyle.letterSpacingEm();
                  float sizeDiff = (effectiveSize - this.size) * 0.5F;
                  float animOffsetY = this.offsetY - sizeDiff;
                  TextRenderState renderState = new TextRenderState(
                     extractor.pose(),
                     font,
                     this.x,
                     this.y,
                     effectiveSize,
                     paragraph,
                     this.color,
                     this.outlineColor,
                     this.outlineThickness * this.scale,
                     0.0F,
                     letterSpacing,
                     this.align,
                     this.offsetX,
                     animOffsetY,
                     Render2DUtil.currentScissor()
                  );
                  Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
                  frameState.queued.add(renderState);
                  frameState.pendingCount = frameState.queued.size();
               }
            }
         }
      }

      private MsdfFont resolveFont() {
         if (this.font != null) {
            return this.font;
         }

         int resolvedWeight = this.weight > 0 ? this.weight : this.fontStyle.weight();
         return this.family != null ? this.family.resolve(resolvedWeight) : UiFonts.getFontForText(this.text, resolvedWeight);
      }

      private void drawVanilla(GuiGraphicsExtractor extractor) {
         Render2DUtil.flush();
         Font vanillaFont = MinecraftContext.mc.font;
         float effectiveSize = this.size * this.scale;
         float rawScale = effectiveSize / 9.0F;
         float scale = Math.max(1.0F, Math.round(rawScale * 4.0F) / 4.0F);
         int textWidth = vanillaFont.width(this.text);

         int localX = switch (this.align) {
            case LEFT -> 0;
            case CENTER -> -textWidth / 2;
            case RIGHT -> -textWidth;
         };
         float guiScale = MinecraftContext.mc.getWindow().getGuiScale();
         float drawX = Math.round((this.x + this.offsetX) * guiScale) / guiScale;
         float drawY = Math.round((this.y + this.offsetY) * guiScale) / guiScale;
         extractor.pose().pushMatrix();

         try {
            extractor.pose().translate(drawX, drawY);
            extractor.pose().scale(scale, scale);
            extractor.text(vanillaFont, this.text, localX, 0, this.color, false);
         } finally {
            extractor.pose().popMatrix();
         }
      }

      private void drawVanillaFallback(GuiGraphicsExtractor extractor) {
         Render2DUtil.flush();
         Font vanillaFont = MinecraftContext.mc.font;
         float rawScale = this.size / 9.0F;
         float scale = Math.max(1.0F, Math.round(rawScale * 4.0F) / 4.0F);
         int textWidth = vanillaFont.width(this.text);

         int localX = switch (this.align) {
            case LEFT -> 0;
            case CENTER -> -textWidth / 2;
            case RIGHT -> -textWidth;
         };
         MsdfFont metricsFont = this.resolveFont();
         float baselineShift = metricsFont.ascender(this.size) - 7.0F * scale;
         float guiScale = MinecraftContext.mc.getWindow().getGuiScale();
         float drawX = Math.round((this.x + this.offsetX) * guiScale) / guiScale;
         float drawY = Math.round((this.y + this.offsetY + baselineShift) * guiScale) / guiScale;
         extractor.pose().pushMatrix();

         try {
            extractor.pose().translate(drawX, drawY);
            extractor.pose().scale(scale, scale);
            extractor.text(vanillaFont, this.text, localX, 0, this.color, false);
         } finally {
            extractor.pose().popMatrix();
         }
      }
   }

   public static final class TextureBuilder {
      private final float x;
      private final float y;
      private final float width;
      private final float height;
      private final Identifier textureId;
      private int color = -1;
      private boolean managed;
      private float u0;
      private float v0;
      private float u1 = 1.0F;
      private float v1 = 1.0F;
      private float radius;

      private TextureBuilder(float x, float y, float width, float height, Identifier textureId) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.textureId = Objects.requireNonNull(textureId, "textureId");
      }

      public Render2DUtil.TextureBuilder color(int color) {
         this.color = color;
         return this;
      }

      public Render2DUtil.TextureBuilder managed() {
         this.managed = true;
         return this;
      }

      public Render2DUtil.TextureBuilder uv(float u0, float v0, float u1, float v1) {
         this.u0 = u0;
         this.v0 = v0;
         this.u1 = u1;
         this.v1 = v1;
         return this;
      }

      public Render2DUtil.TextureBuilder radius(float radius) {
         this.radius = radius;
         return this;
      }

      public void draw() {
         if (!(this.width <= 0.0F) && !(this.height <= 0.0F) && !Render2DUtil.hasEmptyScissor()) {
            if (!GuiTexture.svgIconsDisabled() || !this.textureId.getPath().endsWith(".svg")) {
               if (!RenderContext.isIn2D()) {
                  throw new IllegalStateException("Render2DUtil.texture().draw() can only be used inside a 2D render context");
               }

               GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
               if (extractor == null) {
                  throw new IllegalStateException("Render2DUtil.texture().draw() requires an active GuiGraphicsExtractor");
               }

               TextureRenderState renderState = new TextureRenderState(
                  extractor.pose(),
                  this.textureId,
                  this.managed,
                  this.x,
                  this.y,
                  this.width,
                  this.height,
                  this.u0,
                  this.v0,
                  this.u1,
                  this.v1,
                  this.color,
                  this.radius,
                  Render2DUtil.currentScissor()
               );
               Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
               frameState.queued.add(renderState);
               frameState.pendingCount = frameState.queued.size();
            }
         }
      }
   }
}
