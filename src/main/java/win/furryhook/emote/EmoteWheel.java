package win.furryhook.emote;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.TextAlign;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class EmoteWheel {
   private static final List<EmoteDefinition> ENTRIES = EmoteRegistry.ALL;
   private static final float SENSITIVITY = 3.4F;
   private static final float MAX_OFFSET = 66.0F;
   private static final float DEADZONE = 15.0F;
   private static final float RADIUS_FRACTION = 0.155F;
   private static final float ICON_BUTTON_SIZE = 40.0F;
   private static final float ICON_SIZE = 20.0F;
   private static final float HUB_RADIUS = 30.0F;
   private static final float LABEL_SIZE = 11.0F;
   private static final float DIM_ALPHA = 0.32F;
   private static final float OPEN_SMOOTH_TIME = 0.085F;
   private static final float CLOSE_SMOOTH_TIME = 0.14F;
   private static final float HOVER_SMOOTH_TIME = 0.07F;
   private static boolean open;
   private static boolean cancelled;
   private static float cursorX;
   private static float cursorY;
   private static int hoveredIndex = -1;
   private static final AnimatedValue openAnim = new AnimatedValue();
   private static final AnimatedValue[] hoverAnim = new AnimatedValue[ENTRIES.size()];
   private static final AnimatedValue hubGlow = new AnimatedValue();

   private EmoteWheel() {
   }

   public static boolean isOpen() {
      return open;
   }

   public static void open() {
      open = true;
      cursorX = 0.0F;
      cursorY = 0.0F;
      hoveredIndex = -1;
   }

   public static void close(Minecraft mc) {
      if (open) {
         open = false;
         boolean apply = !cancelled;
         cancelled = false;
         if (apply && hoveredIndex >= 0 && hoveredIndex < ENTRIES.size() && mc.player != null) {
            EmoteEngine.INSTANCE.play(mc.player.getUUID(), ENTRIES.get(hoveredIndex));
         }
      }
   }

   public static void requestCancel() {
      cancelled = true;
   }

   public static boolean onMouseTurn(double yawDelta, double pitchDelta) {
      if (!open) {
         return false;
      }

      cursorX += (float)yawDelta * 3.4F;
      cursorY += (float)pitchDelta * 3.4F;
      float length = (float)Math.sqrt(cursorX * cursorX + cursorY * cursorY);
      if (length > 66.0F) {
         float scale = 66.0F / length;
         cursorX *= scale;
         cursorY *= scale;
      }

      return true;
   }

   public static void render(Minecraft minecraft, GuiGraphicsExtractor extractor, int screenWidth, int screenHeight) {
      long now = System.nanoTime();
      float openT = openAnim.update(open ? 1.0F : 0.0F, open ? 0.085F : 0.14F, now);
      if (!(openT <= 0.002F)) {
         int entryCount = ENTRIES.size();
         float length = (float)Math.sqrt(cursorX * cursorX + cursorY * cursorY);
         hoveredIndex = -1;
         if (open && length > 15.0F) {
            float angle = (float)Math.atan2(cursorY, cursorX);
            float step = (float)((Math.PI * 2) / entryCount);
            float best = Float.MAX_VALUE;

            for (int i = 0; i < entryCount; i++) {
               float slice = sliceAngle(i, entryCount);
               float diff = Math.abs(angleDiff(angle, slice));
               if (diff < best) {
                  best = diff;
                  hoveredIndex = i;
               }
            }
         }

         float hubT = hubGlow.update(hoveredIndex >= 0 ? 1.0F : 0.0F, 0.08F, now);
         float centerX = screenWidth / 2.0F;
         float centerY = screenHeight / 2.0F;
         float radius = Math.min(screenWidth, screenHeight) * 0.155F;
         Render2DUtil.rect(0.0F, 0.0F, screenWidth, screenHeight).color(ColorUtil.withAlpha(-16777216, 0.32F * openT)).draw();
         Matrix3x2fStack pose = extractor.pose();
         pose.pushMatrix();
         pose.translate(centerX, centerY);
         float bloom = 0.55F + 0.45F * easeOutBack(openT);
         pose.scale(bloom, bloom);
         pose.translate(-centerX, -centerY);
         if (length > 2.0F) {
            float tetherLength = Math.min(length, 66.0F) * (radius / 66.0F);
            float tetherAngle = (float)Math.atan2(cursorY, cursorX);
            pose.pushMatrix();
            pose.translate(centerX, centerY);
            pose.rotate(tetherAngle);
            Render2DUtil.rect(0.0F, -0.75F, tetherLength, 1.5F).color(ColorUtil.withAlpha(Theme.getAccent(), 0.5F * openT)).draw();
            pose.popMatrix();
         }

         for (int i = 0; i < entryCount; i++) {
            float slice = sliceAngle(i, entryCount);
            float iconX = centerX + (float)Math.cos(slice) * radius;
            float iconY = centerY + (float)Math.sin(slice) * radius;
            boolean hovered = i == hoveredIndex;
            float hoverT = hoverAnim[i].update(hovered ? 1.0F : 0.0F, 0.07F, now);
            drawEntry(ENTRIES.get(i), iconX, iconY, hoverT, openT);
         }

         drawHub(centerX, centerY, hubT, openT);
         pose.popMatrix();
      }
   }

   private static void drawEntry(EmoteDefinition entry, float centerX, float centerY, float hoverT, float openT) {
      float size = 40.0F * (1.0F + 0.22F * hoverT);
      float half = size / 2.0F;
      int background = ColorUtil.multiplyAlpha(mixColor(Theme.Colors.CARD, Theme.Colors.CARD_HOVER, hoverT), openT);
      int border = ColorUtil.multiplyAlpha(mixColor(Theme.Colors.OUTLINES_MEDIUM, Theme.getAccent(), hoverT), openT);
      Render2DUtil.rect(centerX - half, centerY - half, size, size)
         .color(background)
         .radius(half)
         .border(Math.max(1.0F, 1.5F * (1.0F + hoverT)), border)
         .draw();
      float iconSize = 20.0F * (1.0F + 0.1F * hoverT);
      Render2DUtil.texture(centerX - iconSize / 2.0F, centerY - iconSize / 2.0F, iconSize, iconSize, entry.icon()).color(ColorUtil.withAlpha(-1, openT)).draw();
      if (hoverT > 0.05F) {
         MsdfFont font = UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight());
         float labelSize = 11.0F;
         float labelY = centerY + half + 6.0F;
         Render2DUtil.text(centerX, font.centeredTextY(labelY + labelSize / 2.0F, labelSize), labelSize, entry.displayName())
            .font(font)
            .align(TextAlign.CENTER)
            .color(ColorUtil.withAlpha(Theme.Colors.TEXT_TITLE, openT * hoverT))
            .draw();
      }
   }

   private static void drawHub(float centerX, float centerY, float hubT, float openT) {
      float radius = 30.0F * (1.0F + 0.08F * hubT);
      int background = ColorUtil.multiplyAlpha(Theme.Colors.PANEL, openT * 0.92F);
      int border = ColorUtil.multiplyAlpha(mixColor(Theme.Colors.OUTLINES_MEDIUM, Theme.getAccent(), hubT), openT);
      Render2DUtil.rect(centerX - radius, centerY - radius, radius * 2.0F, radius * 2.0F).color(background).radius(radius).border(1.5F, border).draw();
      String label = hoveredIndex >= 0 ? ENTRIES.get(hoveredIndex).displayName() : "Emotions";
      MsdfFont font = UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight());
      float labelSize = 9.5F;
      Render2DUtil.text(centerX, font.centeredTextY(centerY, labelSize), labelSize, label)
         .font(font)
         .align(TextAlign.CENTER)
         .color(ColorUtil.withAlpha(hubT > 0.05F ? -1 : Theme.Colors.SECONDARY, openT))
         .draw();
   }

   private static float sliceAngle(int index, int count) {
      return (float)((-Math.PI / 2) + index * ((Math.PI * 2) / count));
   }

   private static float angleDiff(float a, float b) {
      float diff = (a - b) % (float) (Math.PI * 2);
      if (diff > Math.PI) {
         diff -= (float) (Math.PI * 2);
      } else if (diff < -Math.PI) {
         diff += (float) (Math.PI * 2);
      }

      return diff;
   }

   private static float easeOutBack(float x) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float t = x - 1.0F;
      return 1.0F + c3 * t * t * t + c1 * t * t;
   }

   private static int mixColor(int from, int to, float t) {
      float clamped = MathUtil.clamp01(t);
      return ColorUtil.lerp(from, to, clamped);
   }

   static {
      for (int i = 0; i < hoverAnim.length; i++) {
         hoverAnim[i] = new AnimatedValue();
      }
   }
}
