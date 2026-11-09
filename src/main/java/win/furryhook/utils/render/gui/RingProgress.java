package win.furryhook.utils.render.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import win.furryhook.context.RenderContext;
import win.furryhook.utils.math.MathUtil;

public final class RingProgress {
   private static final int MAX_SEGMENTS = 128;

   private RingProgress() {
   }

   public static void draw(float centerX, float centerY, float diameter, float progress, float thicknessFraction, int color) {
      if (Float.isFinite(diameter) && Float.isFinite(progress) && Float.isFinite(thicknessFraction) && !(diameter <= 0.0F) && (color >>> 24 & 0xFF) != 0) {
         float fill = MathUtil.clamp01(progress);
         GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
         if (!(fill <= 0.0F) && extractor != null) {
            float guiScale = Math.max(1.0F, Minecraft.getInstance().getWindow().getGuiScale());
            float thickness = Math.min(diameter * 0.5F, Math.max(0.75F / guiScale, thicknessFraction * diameter));
            float outerRadius = diameter * 0.5F;
            if (fill >= 0.9999F) {
               Render2DUtil.rect(centerX - outerRadius, centerY - outerRadius, diameter, diameter).color(0).radius(outerRadius).border(thickness, color).draw();
            } else {
               float pathRadius = outerRadius - thickness * 0.5F;
               double sweep = (Math.PI * 2) * fill;
               int segments = Math.clamp((int)Math.ceil(sweep * pathRadius * guiScale / 2.0), 1, 128);
               double step = sweep / segments;
               float chordLength = 2.0F * pathRadius * (float)Math.sin(step * 0.5);
               float overlap = Math.min(thickness * 0.25F, 0.35F / guiScale);
               float length = Math.max(thickness, chordLength + overlap);
               Matrix3x2fStack pose = extractor.pose();

               for (int i = 0; i < segments; i++) {
                  float angle = (float)(step * (i + 0.5));
                  pose.pushMatrix();
                  pose.translate(centerX, centerY);
                  pose.rotate(angle);
                  float localScale = Math.min(1.0F, thickness * 0.5F);
                  pose.scale(localScale);
                  Render2DUtil.rect(-length * 0.5F / localScale, -outerRadius / localScale, length / localScale, thickness / localScale)
                     .color(color)
                     .radius(thickness * 0.5F / localScale)
                     .draw();
                  pose.popMatrix();
               }
            }
         }
      }
   }
}
