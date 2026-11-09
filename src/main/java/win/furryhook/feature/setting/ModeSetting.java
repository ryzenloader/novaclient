package win.furryhook.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Generated;

public final class ModeSetting extends Setting<String> {
   private List<String> modes;
   private Set<String> hiddenModes = Set.of();

   public ModeSetting(String name, String defaultValue, String... modes) {
      super(name, defaultValue);
      if (modes != null && modes.length != 0) {
         this.modes = List.copyOf(new ArrayList<>(List.of(modes)));
         if (!this.containsMode(defaultValue)) {
            throw new IllegalArgumentException("Default mode must exist in modes: " + defaultValue);
         }
      } else {
         throw new IllegalArgumentException("Mode setting requires at least one mode");
      }
   }

   public void updateModes(List<String> newModes) {
      if (newModes != null && !newModes.isEmpty()) {
         List<String> copy = List.copyOf(newModes);
         if (!copy.equals(this.modes)) {
            this.modes = copy;
            if (!this.containsMode(this.getValue())) {
               this.setValue(copy.get(0));
            }
         }
      }
   }

   public ModeSetting hideModes(String... hidden) {
      Set<String> resolved = new LinkedHashSet<>();

      for (String mode : hidden) {
         int index = this.indexOf(mode);
         if (index == -1) {
            throw new IllegalArgumentException("Unknown mode '" + mode + "' for setting " + this.getName());
         }

         resolved.add(this.modes.get(index));
      }

      this.hiddenModes = Set.copyOf(resolved);
      return this;
   }

   public boolean isHidden(String mode) {
      int index = this.indexOf(mode);
      return index != -1 && this.hiddenModes.contains(this.modes.get(index));
   }

   public List<String> getVisibleModes() {
      if (this.hiddenModes.isEmpty()) {
         return this.modes;
      }

      List<String> visible = new ArrayList<>(this.modes.size());

      for (String mode : this.modes) {
         if (!this.hiddenModes.contains(mode)) {
            visible.add(mode);
         }
      }

      return List.copyOf(visible);
   }

   public boolean is(String mode) {
      return this.getValue().equalsIgnoreCase(mode);
   }

   public void cycle() {
      int current = this.indexOf(this.getValue());

      for (int offset = 1; offset <= this.modes.size(); offset++) {
         String candidate = this.modes.get((current + offset) % this.modes.size());
         if (!this.hiddenModes.contains(candidate)) {
            this.setValue(candidate);
            return;
         }
      }
   }

   public int indexOf(String mode) {
      for (int i = 0; i < this.modes.size(); i++) {
         if (this.modes.get(i).equalsIgnoreCase(mode)) {
            return i;
         }
      }

      return -1;
   }

   public boolean containsMode(String mode) {
      return this.indexOf(mode) != -1;
   }

   protected String normalize(String value) {
      Objects.requireNonNull(value, "mode");
      int index = this.indexOf(value);
      if (index == -1) {
         throw new IllegalArgumentException("Unknown mode '" + value + "' for setting " + this.getName());
      } else {
         return this.modes.get(index);
      }
   }

   @Override
   protected String normalizeWarningOption(String option) {
      Objects.requireNonNull(option, "mode");
      int index = this.indexOf(option);
      if (index == -1) {
         throw new IllegalArgumentException("Unknown mode '" + option + "' for setting " + this.getName());
      } else {
         return this.modes.get(index);
      }
   }

   protected JsonElement writeValue(String value) {
      return new JsonPrimitive(value);
   }

   protected String readValue(JsonElement element) {
      String value = element.getAsString();
      int index = this.indexOf(value);
      return index == -1 ? this.getDefaultValue() : this.modes.get(index);
   }

   @Generated
   public List<String> getModes() {
      return this.modes;
   }

   @Generated
   public Set<String> getHiddenModes() {
      return this.hiddenModes;
   }
}
