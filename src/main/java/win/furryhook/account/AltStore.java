package win.furryhook.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import win.furryhook.menu.core.MenuConfigStore;
import win.furryhook.utils.AccountSwitcher;
import win.furryhook.utils.ConfigIO;

public final class AltStore {
   private static final Path PATH = ConfigIO.resolve("alts.json");
   private static final String KEY_ALTS = "alts";
   private static final List<Alt> ALTS = new ArrayList<>();
   private static final Deque<AltStore.Removed> UNDO = new ArrayDeque<>();
   private static final int UNDO_LIMIT = 16;
   private static boolean loaded;

   private AltStore() {
   }

   public static List<Alt> all() {
      load();
      return ALTS;
   }

   public static int size() {
      return all().size();
   }

   public static Alt current() {
      String selected = MenuConfigStore.getString("selectedAccount", "Furry");

      for (Alt alt : all()) {
         if (alt.name().equalsIgnoreCase(selected)) {
            return alt;
         }
      }

      return null;
   }

   public static boolean contains(String name) {
      for (Alt alt : all()) {
         if (alt.name().equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   public static List<String> names() {
      List<String> names = new ArrayList<>(size());

      for (Alt alt : all()) {
         names.add(alt.name());
      }

      return names;
   }

   public static Alt add(String name) {
      String trimmed = name == null ? "" : name.trim();
      if (Alt.isValidName(trimmed) && !contains(trimmed)) {
         Alt alt = new Alt(trimmed, System.currentTimeMillis(), 0L);
         ALTS.add(0, alt);
         save();
         return alt;
      } else {
         return null;
      }
   }

   public static void remove(Alt alt) {
      if (alt != null) {
         load();
         int index = ALTS.indexOf(alt);
         if (index >= 0) {
            ALTS.remove(index);
            pushUndo(new AltStore.Removed(List.of(alt), List.of(index)));
            save();
         }
      }
   }

   public static void clearAll() {
      load();
      if (!ALTS.isEmpty()) {
         List<Alt> removed = new ArrayList<>(ALTS);
         List<Integer> indices = new ArrayList<>(removed.size());

         for (int i = 0; i < removed.size(); i++) {
            indices.add(i);
         }

         ALTS.clear();
         pushUndo(new AltStore.Removed(removed, indices));
         save();
      }
   }

   public static List<Alt> undoRemove() {
      load();
      AltStore.Removed removed = UNDO.pollLast();
      if (removed == null) {
         return List.of();
      }

      List<Alt> restored = new ArrayList<>(removed.alts().size());

      for (int i = 0; i < removed.alts().size(); i++) {
         Alt alt = removed.alts().get(i);
         if (!containsLoaded(alt.name())) {
            int index = Math.min(removed.indices().get(i), ALTS.size());
            ALTS.add(index, alt);
            restored.add(alt);
         }
      }

      if (!restored.isEmpty()) {
         save();
      }

      return restored;
   }

   public static boolean canUndo() {
      return !UNDO.isEmpty();
   }

   private static void pushUndo(AltStore.Removed removed) {
      UNDO.addLast(removed);

      while (UNDO.size() > 16) {
         UNDO.pollFirst();
      }
   }

   public static void login(Alt alt) {
      if (alt != null) {
         AccountSwitcher.switchTo(alt.name());
         MenuConfigStore.save(data -> data.addProperty("selectedAccount", alt.name()));
         alt.markUsed();
         save();
      }
   }

   public static void save() {
      JsonObject root = new JsonObject();
      JsonArray array = new JsonArray();

      for (Alt alt : ALTS) {
         JsonObject entry = new JsonObject();
         entry.addProperty("name", alt.name());
         entry.addProperty("added", alt.addedAt());
         entry.addProperty("used", alt.lastUsedAt());
         array.add(entry);
      }

      root.add("alts", array);
      ConfigIO.write(PATH, root);
   }

   private static void load() {
      if (!loaded) {
         loaded = true;
         JsonObject root = ConfigIO.read(PATH);
         if (root != null) {
            JsonElement element = root.get("alts");
            if (element != null && element.isJsonArray()) {
               for (JsonElement raw : element.getAsJsonArray()) {
                  if (raw.isJsonObject()) {
                     JsonObject entry = raw.getAsJsonObject();
                     String name = entry.has("name") ? entry.get("name").getAsString() : "";
                     if (Alt.isValidName(name) && !containsLoaded(name)) {
                        long added = entry.has("added") ? entry.get("added").getAsLong() : 0L;
                        long used = entry.has("used") ? entry.get("used").getAsLong() : 0L;
                        ALTS.add(new Alt(name, added, used));
                     }
                  }
               }
            }
         }

         String selected = MenuConfigStore.getString("selectedAccount", "Furry");
         if (!Alt.isValidName(selected)) {
            selected = "Furry";
         }

         if (!containsLoaded(selected)) {
            ALTS.add(0, new Alt(selected, System.currentTimeMillis(), System.currentTimeMillis()));
            save();
         }
      }
   }

   private static boolean containsLoaded(String name) {
      String lower = name.toLowerCase(Locale.ROOT);

      for (Alt alt : ALTS) {
         if (alt.name().toLowerCase(Locale.ROOT).equals(lower)) {
            return true;
         }
      }

      return false;
   }

   private record Removed(List<Alt> alts, List<Integer> indices) {
   }
}
