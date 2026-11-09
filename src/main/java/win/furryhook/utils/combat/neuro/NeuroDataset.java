package win.furryhook.utils.combat.neuro;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.furryhook.utils.ConfigIO;

public final class NeuroDataset {
   public static final NeuroDataset INSTANCE = new NeuroDataset();
   public static final String DEFAULT_NAME = "default";
   public static final int MAX_NEIGHBOURS = 16;
   private static final Logger LOGGER = LoggerFactory.getLogger(NeuroDataset.class);
   private static final String DIRECTORY = "neuro";
   private static final String EXTENSION = ".json";
   private static final int FORMAT = 2;
   private static final String NAME_PATTERN = "[A-Za-z0-9_-]{1,32}";
   private static final int MAX_SAMPLES = 20000;
   private static final int TRIM_BATCH = 512;
   private static final int CELL_CAPACITY = 64;
   private static final float CELL_YAW = 6.0F;
   private static final float CELL_PITCH = 5.0F;
   private static final int FRAME_BITS = 8;
   private static final int FRAME_MASK = 255;
   private static final float WEIGHT_EPSILON = 0.05F;
   private final List<NeuroSample> samples = new ArrayList<>();
   private final Map<Long, NeuroDataset.Cell> grid = new HashMap<>();
   private int frameCount;
   private String activeName = "default";
   private boolean dirty;
   private List<String> cachedNames;

   private NeuroDataset() {
   }

   public int sampleCount() {
      return this.samples.size();
   }

   public int frameCount() {
      return this.frameCount;
   }

   public String activeName() {
      return this.activeName;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public boolean isEmpty() {
      return this.samples.isEmpty();
   }

   public void add(NeuroSample sample) {
      if (sample != null) {
         this.samples.add(sample);
         this.indexSample(this.samples.size() - 1, sample);
         this.frameCount = this.frameCount + sample.frames();
         this.dirty = true;
         this.trim();
      }
   }

   public void clear() {
      this.samples.clear();
      this.grid.clear();
      this.frameCount = 0;
      this.dirty = false;
   }

   public float[] predict(NeuroQuery query, int neighbours, float variation, float duelBias) {
      if (this.samples.isEmpty()) {
         return null;
      }

      int wanted = Math.clamp(neighbours, 1, 16);
      float[] bestDistance = new float[wanted];
      int[] bestEntry = new int[wanted];
      Arrays.fill(bestDistance, Float.MAX_VALUE);
      Arrays.fill(bestEntry, -1);
      int found = this.collect(query, 1, bestDistance, bestEntry, duelBias);
      if (found == 0) {
         found = this.collect(query, 3, bestDistance, bestEntry, duelBias);
      }

      if (found == 0) {
         return null;
      }

      float totalWeight = 0.0F;
      float yawSum = 0.0F;
      float pitchSum = 0.0F;

      for (int index = 0; index < found; index++) {
         float weight = 1.0F / (bestDistance[index] + 0.05F);
         totalWeight += weight;
         yawSum += weight * this.value(bestEntry[index], 8);
         pitchSum += weight * this.value(bestEntry[index], 9);
      }

      if (totalWeight <= 0.0F) {
         return null;
      }

      float yaw = yawSum / totalWeight;
      float pitch = pitchSum / totalWeight;
      float lean = Mth.clamp(variation, 0.0F, 1.0F);
      if (lean > 0.0F && found > 1) {
         int picked = pick(bestDistance, found, totalWeight);
         yaw = Mth.lerp(lean, yaw, this.value(bestEntry[picked], 8));
         pitch = Mth.lerp(lean, pitch, this.value(bestEntry[picked], 9));
      }

      return new float[]{yaw, pitch};
   }

   public NeuroSample nearestSample(NeuroQuery query, float duelBias, boolean hitsOnly) {
      NeuroSample best = null;
      float bestDistance = Float.MAX_VALUE;

      for (NeuroSample sample : this.samples) {
         if (!hitsOnly || sample.label().isHit()) {
            float distance = query.distanceSqr(sample.data(), 0, duelBias);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = sample;
            }
         }
      }

      return best == null && hitsOnly ? this.nearestSample(query, duelBias, false) : best;
   }

   public int hitCount() {
      int count = 0;

      for (NeuroSample sample : this.samples) {
         if (sample.label().isHit()) {
            count++;
         }
      }

      return count;
   }

   public int duelCount() {
      int count = 0;

      for (NeuroSample sample : this.samples) {
         if (sample.label().duel()) {
            count++;
         }
      }

      return count;
   }

   public float averageSmoothness() {
      if (this.samples.isEmpty()) {
         return 0.0F;
      }

      float total = 0.0F;

      for (NeuroSample sample : this.samples) {
         total += sample.label().smoothness();
      }

      return total / this.samples.size();
   }

   public boolean save(String name) {
      Path path = this.pathFor(name);
      if (path == null) {
         return false;
      }

      JsonObject root = new JsonObject();
      root.addProperty("version", 2);
      root.addProperty("saved", System.currentTimeMillis());
      root.addProperty("frames", this.frameCount);
      JsonArray array = new JsonArray();

      for (NeuroSample sample : this.samples) {
         JsonObject entry = new JsonObject();
         entry.addProperty("frames", sample.frames());
         entry.addProperty("fields", 16);
         entry.addProperty("recorded", sample.recordedAt());
         entry.add("label", sample.label().toJson());
         entry.addProperty("data", sample.encode());
         array.add(entry);
      }

      root.add("samples", array);
      if (!ConfigIO.write(path, root)) {
         return false;
      }

      this.activeName = name;
      this.dirty = false;
      this.cachedNames = null;
      return true;
   }

   public boolean load(String name) {
      Path path = this.pathFor(name);
      if (path != null && Files.exists(path)) {
         JsonObject root = ConfigIO.read(path);
         if (root == null) {
            return false;
         }

         List<NeuroSample> loaded = new ArrayList<>();
         JsonArray array = root.getAsJsonArray("samples");
         if (array != null) {
            for (JsonElement element : array) {
               if (element.isJsonObject()) {
                  JsonObject entry = element.getAsJsonObject();
                  int fields = entry.has("fields") ? integer(entry, "fields") : 10;
                  NeuroSample sample = NeuroSample.decode(
                     string(entry, "data"),
                     integer(entry, "frames"),
                     fields,
                     longValue(entry, "recorded"),
                     entry.has("label") && entry.get("label").isJsonObject() ? NeuroLabel.fromJson(entry.getAsJsonObject("label")) : NeuroLabel.UNKNOWN
                  );
                  if (sample != null) {
                     loaded.add(sample);
                  }
               }
            }
         }

         this.samples.clear();
         this.samples.addAll(loaded);
         this.reindex();
         this.activeName = name;
         this.dirty = false;
         return true;
      } else {
         return false;
      }
   }

   public void loadDefault() {
      if (!this.load("default")) {
         this.activeName = "default";
      }
   }

   public void saveIfDirty() {
      if (this.dirty) {
         this.save(this.activeName);
      }
   }

   public boolean delete(String name) {
      Path path = this.pathFor(name);

      try {
         boolean deleted = path != null && Files.deleteIfExists(path);
         if (deleted) {
            this.cachedNames = null;
         }

         return deleted;
      } catch (IOException exception) {
         LOGGER.error("Failed to delete neuro dataset {}", path, exception);
         return false;
      }
   }

   public List<String> names() {
      List<String> cached = this.cachedNames;
      if (cached != null) {
         return cached;
      }

      List<String> found = new ArrayList<>();
      Path directory = this.directory();
      if (Files.isDirectory(directory)) {
         try (Stream<Path> files = Files.list(directory)) {
            files.<String>map(file -> file.getFileName().toString())
               .filter(fileName -> fileName.endsWith(".json"))
               .map(fileName -> fileName.substring(0, fileName.length() - ".json".length()))
               .sorted()
               .forEach(found::add);
         } catch (IOException exception) {
            LOGGER.error("Failed to list neuro datasets in {}", directory, exception);
         }
      }

      if (!found.contains("default")) {
         found.add(0, "default");
      }

      this.cachedNames = List.copyOf(found);
      return this.cachedNames;
   }

   public Path directory() {
      return ConfigIO.resolve("neuro");
   }

   public static boolean isValidName(String name) {
      return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
   }

   private Path pathFor(String name) {
      return isValidName(name) ? this.directory().resolve(name + ".json") : null;
   }

   private float value(int entry, int field) {
      NeuroSample sample = this.samples.get(entry >>> 8);
      return sample.get(entry & 0xFF, field);
   }

   private int collect(NeuroQuery query, int radius, float[] bestDistance, int[] bestEntry, float duelBias) {
      int baseYaw = cellIndex(query.errYaw(), 6.0F);
      int basePitch = cellIndex(query.errPitch(), 5.0F);
      int found = 0;

      for (int yawOffset = -radius; yawOffset <= radius; yawOffset++) {
         for (int pitchOffset = -radius; pitchOffset <= radius; pitchOffset++) {
            NeuroDataset.Cell cell = this.grid.get(key(baseYaw + yawOffset, basePitch + pitchOffset));
            if (cell != null) {
               for (int index = 0; index < cell.size; index++) {
                  int entry = cell.entries[index];
                  NeuroSample sample = this.samples.get(entry >>> 8);
                  float distance = query.distanceSqr(sample.data(), (entry & 0xFF) * 16, duelBias);
                  found = insert(bestDistance, bestEntry, distance, entry, found);
               }
            }
         }
      }

      return found;
   }

   private static int insert(float[] bestDistance, int[] bestEntry, float distance, int entry, int found) {
      int last = bestDistance.length - 1;
      if (distance >= bestDistance[last]) {
         return found;
      }

      int position;
      for (position = last; position > 0 && bestDistance[position - 1] > distance; position--) {
         bestDistance[position] = bestDistance[position - 1];
         bestEntry[position] = bestEntry[position - 1];
      }

      bestDistance[position] = distance;
      bestEntry[position] = entry;
      return Math.min(found + 1, bestDistance.length);
   }

   private static int pick(float[] bestDistance, int found, float totalWeight) {
      float roll = ThreadLocalRandom.current().nextFloat() * totalWeight;

      for (int index = 0; index < found; index++) {
         roll -= 1.0F / (bestDistance[index] + 0.05F);
         if (roll <= 0.0F) {
            return index;
         }
      }

      return found - 1;
   }

   private void indexSample(int sampleIndex, NeuroSample sample) {
      float[] data = sample.data();

      for (int frame = 0; frame < sample.frames(); frame++) {
         int offset = frame * 16;
         long key = key(cellIndex(data[offset + 0], 6.0F), cellIndex(data[offset + 1], 5.0F));
         this.grid.computeIfAbsent(key, ignored -> new NeuroDataset.Cell()).add(sampleIndex << 8 | frame);
      }
   }

   private void reindex() {
      this.grid.clear();
      this.frameCount = 0;

      for (int index = 0; index < this.samples.size(); index++) {
         NeuroSample sample = this.samples.get(index);
         this.indexSample(index, sample);
         this.frameCount = this.frameCount + sample.frames();
      }
   }

   private void trim() {
      if (this.samples.size() > 20000) {
         int excess = Math.min(this.samples.size() - 20000 + 512, this.samples.size());
         this.samples.subList(0, excess).clear();
         this.reindex();
      }
   }

   private static int cellIndex(float value, float size) {
      return (int)Math.floor(value / size);
   }

   private static long key(int yawCell, int pitchCell) {
      return (long)yawCell << 32 ^ pitchCell & 4294967295L;
   }

   private static String string(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   private static int integer(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
   }

   private static long longValue(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsLong() : 0L;
   }

   private static final class Cell {
      private int[] entries = new int[16];
      private int size;
      private int seen;

      private void add(int entry) {
         this.seen++;
         if (this.size < 64) {
            if (this.size == this.entries.length) {
               this.entries = Arrays.copyOf(this.entries, Math.min(this.entries.length * 2, 64));
            }

            this.entries[this.size++] = entry;
         } else {
            int slot = ThreadLocalRandom.current().nextInt(this.seen);
            if (slot < 64) {
               this.entries[slot] = entry;
            }
         }
      }
   }
}
