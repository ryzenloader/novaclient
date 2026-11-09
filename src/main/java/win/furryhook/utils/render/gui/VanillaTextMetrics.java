package win.furryhook.utils.render.gui;

import net.minecraft.client.gui.Font;
import win.furryhook.context.MinecraftContext;

public final class VanillaTextMetrics {
   private VanillaTextMetrics() {
   }

   public static float width(String text, float size) {
      Font font = MinecraftContext.mc.font;
      float rawScale = size / 9.0F;
      float scale = Math.max(1.0F, Math.round(rawScale * 4.0F) / 4.0F);
      return font.width(text) * scale;
   }

   public static float lineHeight(float size) {
      Font font = MinecraftContext.mc.font;
      float rawScale = size / 9.0F;
      float scale = Math.max(1.0F, Math.round(rawScale * 4.0F) / 4.0F);
      return 9.0F * scale;
   }

   public static float centeredTextY(float centerY, float size) {
      return centerY - lineHeight(size) * 0.5F;
   }
}
