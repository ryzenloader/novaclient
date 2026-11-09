package win.furryhook.menu.screen;

import java.util.HashMap;
import java.util.Map;
import win.furryhook.utils.math.MathUtil;

public final class UiAnimator {
   private final Map<String, Float> values = new HashMap<>();
   private long lastNanos;
   private float delta = 0.016666668F;

   public void tick() {
      long now = System.nanoTime();
      this.delta = this.lastNanos == 0L ? 0.016666668F : MathUtil.clamp((float)(now - this.lastNanos) / 1.0E9F, 0.0F, 0.1F);
      this.lastNanos = now;
   }

   public float delta() {
      return this.delta;
   }

   public float value(String key, float target, float speed) {
      float current = this.values.getOrDefault(key, target);
      float step = 1.0F - (float)Math.exp(-this.delta * speed);
      current += (target - current) * step;
      if (Math.abs(target - current) < 0.0015F) {
         current = target;
      }

      this.values.put(key, current);
      return current;
   }

   public void reset() {
      this.values.clear();
      this.lastNanos = 0L;
   }

   public static float easeOutCubic(float x) {
      float inv = 1.0F - x;
      return 1.0F - inv * inv * inv;
   }
}
