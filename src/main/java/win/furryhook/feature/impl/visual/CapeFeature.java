package win.furryhook.feature.impl.visual;

import java.util.Locale;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.ModeSetting;

public final class CapeFeature extends Feature {
   public final ModeSetting mode = this.register(
      new ModeSetting("Style", "PMCBoykisser", "AppIcon", "ASCII", "Barcode", "Glass", "MinimalPattern", "Motto", "Vandal", "PMCBoykisser")
   );

   public CapeFeature() {
      super("Capes", "Custom cosmetic capes visible on you", FeatureCategory.VISUAL, -1);
      this.setVisible(false);
   }

   @Override
   public boolean isMenuHidden() {
      return true;
   }

   public CapeFeature.CapeStyle currentStyle() {
      try {
         return CapeFeature.CapeStyle.valueOf(this.mode.getValue().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return CapeFeature.CapeStyle.PMCBOYKISSER;
      }
   }

   public enum CapeStyle {
      APPICON("appicon.png"),
      ASCII("ascii.png"),
      BARCODE("barcode.png"),
      GLASS("glass.png"),
      MINIMALPATTERN("minimalpatern.png"),
      MOTTO("motto.png"),
      VANDAL("vandal.png"),
      PMCBOYKISSER("pmcboykisser.png");

      private final String fileName;

      CapeStyle(String fileName) {
         this.fileName = fileName;
      }

      public String getFileName() {
         return this.fileName;
      }
   }
}
