package win.furryhook.feature.setting;

import win.furryhook.utils.render.Theme;

public final class ColorMode {
   public static final String SYNC = "Sync";
   public static final String CUSTOM = "Custom";

   private ColorMode() {
   }

   public static ModeSetting setting() {
      return new ModeSetting("Color Mode", "Sync", "Sync", "Custom");
   }

   public static boolean isCustom(ModeSetting mode) {
      return mode != null && mode.is("Custom");
   }

   public static int resolve(ModeSetting mode, ColorSetting customColor) {
      return isCustom(mode) ? customColor.getValue() : Theme.getAccent();
   }
}
