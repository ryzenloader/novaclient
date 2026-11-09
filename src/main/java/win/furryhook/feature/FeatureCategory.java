package win.furryhook.feature;

import lombok.Generated;

public enum FeatureCategory {
   COMBAT("Combat"),
   MOVEMENT("Movement"),
   VISUAL("Visuals"),
   PLAYER("Player"),
   MISC("Misc"),
   COSMETICS("Cosmetics"),
   SCRIPTS("Scripts");

   private final String displayName;

   FeatureCategory(String displayName) {
      this.displayName = displayName;
   }

   @Generated
   public String getDisplayName() {
      return this.displayName;
   }
}
