package win.furryhook.feature.impl.visual;

import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;

public final class ItemPhysicsFeature extends Feature {
   public ItemPhysicsFeature() {
      super("ItemPhysics", "Dropped items lie on the ground", FeatureCategory.VISUAL, -1);
   }
}
