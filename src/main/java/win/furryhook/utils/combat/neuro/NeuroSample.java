package win.furryhook.utils.combat.neuro;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

public final class NeuroSample {
   public static final int FIELDS = 16;
   public static final int LEGACY_FIELDS = 10;
   public static final int ERR_YAW = 0;
   public static final int ERR_PITCH = 1;
   public static final int PREV_DELTA_YAW = 2;
   public static final int PREV_DELTA_PITCH = 3;
   public static final int TARGET_YAW_SPEED = 4;
   public static final int TARGET_PITCH_SPEED = 5;
   public static final int DISTANCE = 6;
   public static final int SINCE_HIT = 7;
   public static final int DELTA_YAW = 8;
   public static final int DELTA_PITCH = 9;
   public static final int TARGET_SPEED = 10;
   public static final int MOUSE_STEPS = 11;
   public static final int MOUSE_PEAK = 12;
   public static final int SMOOTHNESS = 13;
   public static final int JERK = 14;
   public static final int DUEL = 15;
   public static final int MAX_FRAMES = 255;
   private final float[] data;
   private final int frames;
   private final long recordedAt;
   private final NeuroLabel label;

   public NeuroSample(float[] data, int frames, long recordedAt, NeuroLabel label) {
      if (frames >= 1 && frames <= 255) {
         if (data.length < frames * 16) {
            throw new IllegalArgumentException("Sample holds " + data.length + " values, needs " + frames * 16);
         }

         this.data = data;
         this.frames = frames;
         this.recordedAt = recordedAt;
         this.label = label == null ? NeuroLabel.UNKNOWN : label;
      } else {
         throw new IllegalArgumentException("Frame count out of range: " + frames);
      }
   }

   public int frames() {
      return this.frames;
   }

   public long recordedAt() {
      return this.recordedAt;
   }

   public NeuroLabel label() {
      return this.label;
   }

   public float get(int frame, int field) {
      return this.data[frame * 16 + field];
   }

   float[] data() {
      return this.data;
   }

   public String encode() {
      ByteBuffer buffer = ByteBuffer.allocate(this.frames * 16 * 4).order(ByteOrder.LITTLE_ENDIAN);
      buffer.asFloatBuffer().put(this.data, 0, this.frames * 16);
      return Base64.getEncoder().encodeToString(buffer.array());
   }

   public static NeuroSample decode(String encoded, int frames, int storedFields, long recordedAt, NeuroLabel label) {
      if (encoded != null && frames >= 1 && frames <= 255) {
         int width = storedFields <= 0 ? 10 : storedFields;
         if (width > 16) {
            width = 16;
         }

         byte[] bytes;
         try {
            bytes = Base64.getDecoder().decode(encoded);
         } catch (IllegalArgumentException malformed) {
            return null;
         }

         int storedWidth = storedFields <= 0 ? 10 : storedFields;
         int storedValues = frames * storedWidth;
         if (bytes.length < storedValues * 4) {
            return null;
         }

         float[] stored = new float[storedValues];
         ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(stored);

         for (float value : stored) {
            if (!Float.isFinite(value)) {
               return null;
            }
         }

         if (storedWidth == 16) {
            return new NeuroSample(stored, frames, recordedAt, label);
         }

         float[] data = new float[frames * 16];

         for (int frame = 0; frame < frames; frame++) {
            System.arraycopy(stored, frame * storedWidth, data, frame * 16, width);
         }

         return new NeuroSample(data, frames, recordedAt, label);
      } else {
         return null;
      }
   }
}
