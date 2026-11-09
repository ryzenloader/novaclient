package win.furryhook.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
   private InputBindSetting inputBind;
   private boolean expander = false;

   public BooleanSetting(String name, boolean defaultValue) {
      super(name, defaultValue);
   }

   public BooleanSetting bindWith(InputBindSetting inputBind) {
      if (inputBind == null) {
         throw new IllegalArgumentException("Boolean setting bind cannot be null");
      }

      this.inputBind = inputBind;
      return this;
   }

   public boolean isBindable() {
      return this.inputBind != null;
   }

   public InputBindSetting getInputBind() {
      return this.inputBind;
   }

   public BooleanSetting asExpander() {
      this.expander = true;
      return this;
   }

   public boolean isExpander() {
      return this.expander;
   }

   protected JsonElement writeValue(Boolean value) {
      return new JsonPrimitive(value);
   }

   protected Boolean readValue(JsonElement element) {
      return element.getAsBoolean();
   }
}
