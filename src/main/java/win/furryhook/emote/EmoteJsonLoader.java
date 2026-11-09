package win.furryhook.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.LoggerFactory;

final class EmoteJsonLoader {
   private EmoteJsonLoader() {
   }

   static EmoteAnimation load(String id, String displayName, String resourcePath) {
      try (InputStream stream = EmoteJsonLoader.class.getResourceAsStream(resourcePath)) {
         if (stream == null) {
            throw new IOException("Missing bundled resource: " + resourcePath);
         }

         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);

         JsonObject root;
         try {
            root = JsonParser.parseReader(reader).getAsJsonObject();
         } catch (Throwable var29) {
            try {
               reader.close();
            } catch (Throwable var28) {
               var29.addSuppressed(var28);
            }

            throw var29;
         }

         reader.close();
         JsonObject emote = root.getAsJsonObject("emote");
         boolean degrees = getBoolean(emote, "degrees", true);
         boolean loop = getBoolean(emote, "isLoop", false);
         float beginTick = getFloat(emote, "beginTick", 0.0F);
         float endTick = getFloat(emote, "endTick", beginTick);
         float stopTick = getFloat(emote, "stopTick", endTick);
         EmoteAnimation.Builder builder = new EmoteAnimation.Builder();
         JsonElement movesElement = emote.get("moves");
         if (movesElement != null && movesElement.isJsonArray()) {
            for (JsonElement moveElement : movesElement.getAsJsonArray()) {
               if (moveElement.isJsonObject()) {
                  JsonObject move = moveElement.getAsJsonObject();
                  float tick = getFloat(move, "tick", 0.0F);
                  Easing easing = Easing.parse(getString(move, "easing", "linear"));

                  for (Entry<String, JsonElement> entry : move.entrySet()) {
                     if (!entry.getKey().equals("tick") && !entry.getKey().equals("easing")) {
                        EmotePart part = EmotePart.fromJsonKey(entry.getKey());
                        if (part != null && entry.getValue().isJsonObject()) {
                           JsonObject transform = entry.getValue().getAsJsonObject();

                           for (Entry<String, JsonElement> axisEntry : transform.entrySet()) {
                              Axis axis = Axis.fromJsonKey(axisEntry.getKey());
                              if (axis != null && axisEntry.getValue().isJsonPrimitive()) {
                                 float raw = axisEntry.getValue().getAsFloat();
                                 float value = axis.isRotation() && degrees ? (float)Math.toRadians(raw) : raw;
                                 builder.put(part, axis, tick, value, easing);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return new EmoteAnimation(id, displayName, beginTick, endTick, stopTick, loop, builder.build());
      } catch (Exception exception) {
         LoggerFactory.getLogger(EmoteJsonLoader.class).error("Failed to load bundled emote '{}' from {}", new Object[]{id, resourcePath, exception});
         return new EmoteAnimation(id, displayName, 0.0F, 1.0F, 1.0F, false, Map.of());
      }
   }

   private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
      return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
   }

   private static float getFloat(JsonObject object, String key, float fallback) {
      return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsFloat() : fallback;
   }

   private static String getString(JsonObject object, String key, String fallback) {
      return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
   }
}
