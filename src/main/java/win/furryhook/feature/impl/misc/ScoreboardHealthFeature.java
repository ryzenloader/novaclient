package win.furryhook.feature.impl.misc;

import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;

public final class ScoreboardHealthFeature extends Feature {
   public ScoreboardHealthFeature() {
      super("ScoreboardHealth", "Fixes a target's health when the server shows a fake value", FeatureCategory.MISC, -1);
   }

   public static boolean isActive() {
      return FeatureManager.INSTANCE.getEnabled(ScoreboardHealthFeature.class) != null;
   }
}
