package win.furryhook.utils.text;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.misc.NameProtectFeature;

public final class NameProtectUtil {
   private static final String FEATURE_NAME = "NameProtect";

   private NameProtectUtil() {
   }

   public static String protect(String text) {
      if (text != null && !text.isEmpty()) {
         String realName = realName();
         String fakeName = fakeName();
         return !realName.isEmpty() && !fakeName.isEmpty() && !realName.equals(fakeName) && text.contains(realName) ? text.replace(realName, fakeName) : text;
      } else {
         return text;
      }
   }

   public static Component protect(Component component) {
      if (component == null) {
         return null;
      }

      String protectedText = protect(component.getString());
      return (Component)(protectedText.equals(component.getString()) ? component : Component.literal(protectedText).withStyle(component.getStyle()));
   }

   public static FormattedText protect(FormattedText text) {
      if (text == null) {
         return null;
      } else if (text instanceof Component component) {
         return protect(component);
      } else {
         String protectedText = protect(text.getString());
         return protectedText.equals(text.getString()) ? text : FormattedText.of(protectedText);
      }
   }

   public static FormattedCharSequence protect(FormattedCharSequence sequence) {
      if (sequence == null) {
         return null;
      }

      StringBuilder text = new StringBuilder();
      sequence.accept((index, style, codePoint) -> {
         text.appendCodePoint(codePoint);
         return true;
      });
      String original = text.toString();
      String protectedText = protect(original);
      return protectedText.equals(original) ? sequence : FormattedCharSequence.forward(protectedText, Style.EMPTY);
   }

   private static String realName() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft == null) {
         return "";
      } else {
         return minecraft.getUser() != null && minecraft.getUser().getName() != null ? minecraft.getUser().getName() : "";
      }
   }

   private static String fakeName() {
      if (FeatureManager.INSTANCE.getFeature("NameProtect") instanceof NameProtectFeature nameProtect && nameProtect.isEnabled()) {
         String value = nameProtect.name.getValue();
         return value == null ? "" : value.trim();
      } else {
         return "";
      }
   }
}
