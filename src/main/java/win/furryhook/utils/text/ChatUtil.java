package win.furryhook.utils.text;

import lombok.Generated;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import win.furryhook.context.MinecraftContext;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Theme;

public final class ChatUtil {
   private static final String PREFIX = "furryhook: ";

   public static void print(String message) {
      info(message);
   }

   public static void header(String message) {
      send(message);
   }

   public static void info(String message) {
      send(message);
   }

   public static void success(String message) {
      send(message);
   }

   public static void error(String message) {
      send(message);
   }

   public static void usage(String message) {
      send(message);
   }

   public static void entry(String title, String detail) {
      MutableComponent component = gradient("furryhook: " + title);
      if (detail != null && !detail.isBlank()) {
         component.append(Component.literal("  •  " + detail).withStyle(Style.EMPTY.withColor(16777215)));
      }

      sendComponent(component);
   }

   public static void send(String message) {
      sendComponent(gradient("furryhook: " + message));
   }

   private static void sendComponent(MutableComponent component) {
      Minecraft mc = MinecraftContext.mc;
      if (mc.gui != null) {
         mc.gui.hud.getChat().addClientSystemMessage(component);
      }
   }

   private static MutableComponent gradient(String text) {
      MutableComponent result = Component.empty();
      int startColor = Theme.getAccent();
      int endColor = ColorUtil.lerp(startColor, -1, 0.62F);
      int cursor = 0;

      while (cursor < text.length()) {
         int codePoint = text.codePointAt(cursor);
         appendColored(result, new String(Character.toChars(codePoint)), gradientColor(startColor, endColor, cursor, text.length()));
         cursor += Character.charCount(codePoint);
      }

      return result;
   }

   private static int gradientColor(int start, int end, int index, int length) {
      return ColorUtil.lerp(start, end, length <= 1 ? 0.0F : (float)index / (length - 1));
   }

   private static void appendColored(MutableComponent target, String text, int color) {
      target.append(Component.literal(text).withStyle(Style.EMPTY.withColor(color & 16777215)));
   }

   @Generated
   private ChatUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
