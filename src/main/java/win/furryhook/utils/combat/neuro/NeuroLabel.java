package win.furryhook.utils.combat.neuro;

import com.google.gson.JsonObject;
import java.util.Locale;

public record NeuroLabel(String outcome, boolean duel, int opponents, float smoothness, float avgDistance, int ping, String weapon) {
   public static final String OUTCOME_HIT = "hit";
   public static final String OUTCOME_TRACK = "track";
   public static final String OUTCOME_LOST = "lost";
   public static final double DUEL_RADIUS = 16.0;
   public static final NeuroLabel UNKNOWN = new NeuroLabel("track", false, 0, 0.0F, 0.0F, 0, "");

   public boolean isHit() {
      return "hit".equals(this.outcome);
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("outcome", this.outcome);
      json.addProperty("duel", this.duel);
      json.addProperty("opponents", this.opponents);
      json.addProperty("smoothness", this.smoothness);
      json.addProperty("distance", this.avgDistance);
      json.addProperty("ping", this.ping);
      json.addProperty("weapon", this.weapon);
      return json;
   }

   public static NeuroLabel fromJson(JsonObject json) {
      if (json == null) {
         return UNKNOWN;
      }

      try {
         return new NeuroLabel(
            json.has("outcome") ? json.get("outcome").getAsString() : "track",
            json.has("duel") && json.get("duel").getAsBoolean(),
            json.has("opponents") ? json.get("opponents").getAsInt() : 0,
            json.has("smoothness") ? json.get("smoothness").getAsFloat() : 0.0F,
            json.has("distance") ? json.get("distance").getAsFloat() : 0.0F,
            json.has("ping") ? json.get("ping").getAsInt() : 0,
            json.has("weapon") ? json.get("weapon").getAsString() : ""
         );
      } catch (RuntimeException malformed) {
         return UNKNOWN;
      }
   }

   @Override
   public String toString() {
      return this.outcome + (this.duel ? " 1v1" : " " + this.opponents + "v") + String.format(Locale.ROOT, " sm%.2f d%.1f", this.smoothness, this.avgDistance);
   }
}
