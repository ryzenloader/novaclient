package win.furryhook.utils.render;

import win.furryhook.feature.FeatureCategory;

public final class CategoryIconGlyphs {
   private static final String COMBAT_GLYPH = "D";
   private static final String MOVEMENT_GLYPH = "E";

   private CategoryIconGlyphs() {
   }

   public static boolean isGlyph(FeatureCategory category) {
      return category == FeatureCategory.COMBAT || category == FeatureCategory.MOVEMENT;
   }

   public static String glyph(FeatureCategory category) {
      return category == FeatureCategory.COMBAT ? "D" : "E";
   }
}
