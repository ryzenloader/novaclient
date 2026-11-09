package win.furryhook.menu.screen;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import win.furryhook.utils.math.MathUtil;

public final class Motion {
   public static final float[] HOVER = new float[]{420.0F, 40.0F};
   public static final float[] PRESS = new float[]{700.0F, 42.0F};
   public static final float[] TOGGLE = new float[]{520.0F, 25.0F};
   public static final float[] PILL = new float[]{340.0F, 27.0F};
   public static final float[] PANEL = new float[]{230.0F, 21.0F};
   public static final float[] SCROLL = new float[]{260.0F, 30.0F};
   public static final float[] SWEEP = new float[]{160.0F, 26.0F};
   private static final float BACK_C1 = 1.70158F;
   private static final float BACK_C3 = 2.70158F;
   private final Map<Object, Motion.Spring> springs = new HashMap<>();
   private final Map<Object, Long> stamps = new HashMap<>();
   private long lastNanos;
   private float delta = 0.016666668F;
   private final Map<Object, Object[]> siblings = new HashMap<>();

   public void tick() {
      long now = System.nanoTime();
      this.delta = this.lastNanos == 0L ? 0.016666668F : MathUtil.clamp((float)(now - this.lastNanos) / 1.0E9F, 0.0F, 0.1F);
      this.lastNanos = now;
   }

   public float delta() {
      return this.delta;
   }

   public float spring(Object key, float target, float[] tuning) {
      Motion.Spring spring = this.springs.get(key);
      if (spring == null) {
         spring = new Motion.Spring(target);
         this.springs.put(key, spring);
      }

      float stiffness = tuning[0];
      float damping = tuning[1];
      float h = this.delta * 0.5F;

      for (int step = 0; step < 2; step++) {
         float accel = (target - spring.value) * stiffness - spring.velocity * damping;
         spring.velocity += accel * h;
         spring.value = spring.value + spring.velocity * h;
      }

      spring.idle = Math.abs(target - spring.value) < 0.0012F && Math.abs(spring.velocity) < 0.02F;
      if (spring.idle) {
         spring.value = target;
         spring.velocity = 0.0F;
      }

      return spring.value;
   }

   public float velocity(Object key) {
      Motion.Spring spring = this.springs.get(key);
      return spring == null ? 0.0F : spring.velocity;
   }

   public void seed(Object key, float value) {
      Motion.Spring spring = this.springs.get(key);
      if (spring == null) {
         spring = new Motion.Spring(value);
         this.springs.put(key, spring);
      } else {
         spring.value = value;
         spring.velocity = 0.0F;
      }
   }

   public void play(Object key) {
      this.stamps.put(key, System.nanoTime());
   }

   public float progress(Object key, float delay, float duration) {
      Long stamp = this.stamps.get(key);
      if (stamp == null) {
         stamp = System.nanoTime();
         this.stamps.put(key, stamp);
      }

      float t = (float)(System.nanoTime() - stamp) / 1.0E9F;
      if (duration <= 0.0F) {
         return t >= delay ? 1.0F : 0.0F;
      } else {
         return MathUtil.clamp01((t - delay) / duration);
      }
   }

   public static float ease(Motion.Ease ease, float t) {
      t = MathUtil.clamp01(t);
      switch (ease) {
         case OUT_EXPO:
            return t >= 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0, -10.0F * t);
         case OUT_QUINT: {
            float inv = 1.0F - t;
            return 1.0F - inv * inv * inv * inv * inv;
         }
         case OUT_QUAD:
            return 1.0F - (1.0F - t) * (1.0F - t);
         case OUT_BACK: {
            float inv = t - 1.0F;
            return 1.0F + 2.70158F * inv * inv * inv + 1.70158F * inv * inv;
         }
         case OUT_ELASTIC:
            if (t <= 0.0F) {
               return 0.0F;
            } else {
               if (t >= 1.0F) {
                  return 1.0F;
               }

               float c = (float) (Math.PI * 2.0 / 3.0);
               return (float)Math.pow(2.0, -10.0F * t) * (float)Math.sin((t * 10.0F - 0.75F) * c) + 1.0F;
            }
         case IN_QUAD:
            return t * t;
         case IN_QUINT:
            return t * t * t * t * t;
         default:
            return t;
      }
   }

   public void reset() {
      this.springs.clear();
      this.stamps.clear();
      this.lastNanos = 0L;
   }

   public void sweep() {
      Iterator<Entry<Object, Motion.Spring>> it = this.springs.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Object, Motion.Spring> entry = it.next();
         if (entry.getValue().idle && !this.stamps.containsKey(entry.getKey())) {
            it.remove();
         }
      }
   }

   public Object sub(Object base, String tag) {
      Object[] slots = this.siblings.get(base);
      if (slots == null) {
         slots = new Object[16];
         this.siblings.put(base, slots);
      }

      int index = slotIndex(tag);
      Object key = slots[index];
      if (key == null) {
         key = new Object();
         slots[index] = key;
      }

      return key;
   }

   private static int slotIndex(String tag) {
      return switch (tag) {
         case "hover" -> 0;
         case "press" -> 1;
         case "sheen" -> 2;
         case "toggle" -> 3;
         case "focus" -> 4;
         case "spin" -> 5;
         case "chip" -> 7;
         case "dots" -> 8;
         case "subcat" -> 9;
         case "slider" -> 10;
         case "pill" -> 11;
         case "flyout" -> 12;
         case "sugg" -> 13;
         default -> 6;
      };
   }

   public enum Ease {
      LINEAR,
      OUT_EXPO,
      OUT_QUINT,
      OUT_QUAD,
      OUT_BACK,
      OUT_ELASTIC,
      IN_QUAD,
      IN_QUINT;
   }

   private static final class Spring {
      private float value;
      private float velocity;
      private boolean idle = true;

      Spring(float value) {
         this.value = value;
      }
   }
}
