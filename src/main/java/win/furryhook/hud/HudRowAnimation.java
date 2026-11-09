package win.furryhook.hud;

public final class HudRowAnimation {
   private static final long DURATION_NANOS = 300000000L;
   private float value;
   private float startValue;
   private float targetValue;
   private long startedAtNanos;
   private boolean done = true;

   public float update(boolean shown, long nowNanos) {
      float nextTarget = shown ? 1.0F : 0.0F;
      if (Float.compare(nextTarget, this.targetValue) != 0) {
         this.sample(nowNanos);
         this.startValue = this.value;
         this.targetValue = nextTarget;
         this.startedAtNanos = nowNanos;
         this.done = false;
      }

      return this.sample(nowNanos);
   }

   public boolean isHidden() {
      return this.done && this.targetValue == 0.0F && this.value == 0.0F;
   }

   private float sample(long nowNanos) {
      if (this.done) {
         return this.value;
      } else {
         long elapsed = Math.max(0L, nowNanos - this.startedAtNanos);
         if (elapsed >= 300000000L) {
            this.value = this.targetValue;
            this.done = true;
            return this.value;
         } else {
            float time = (float)elapsed / 3.0E8F;
            float eased = this.targetValue > this.startValue ? cubicBezier(time, 0.45F, 1.45F, 0.49F, 1.15F) : cubicBezier(time, 0.62F, -0.16F, 0.8F, 0.37F);
            this.value = this.startValue + (this.targetValue - this.startValue) * eased;
            return this.value;
         }
      }
   }

   private static float cubicBezier(float progress, float x1, float y1, float x2, float y2) {
      if (progress <= 0.0F) {
         return 0.0F;
      }

      if (progress >= 1.0F) {
         return 1.0F;
      }

      float t = progress;

      for (int i = 0; i < 8; i++) {
         float x = bezier(t, x1, x2) - progress;
         float derivative = bezierDerivative(t, x1, x2);
         if (Math.abs(x) < 1.0E-5F || Math.abs(derivative) < 1.0E-6F) {
            break;
         }

         t = Math.max(0.0F, Math.min(1.0F, t - x / derivative));
      }

      return bezier(t, y1, y2);
   }

   private static float bezier(float t, float p1, float p2) {
      float inv = 1.0F - t;
      return 3.0F * inv * inv * t * p1 + 3.0F * inv * t * t * p2 + t * t * t;
   }

   private static float bezierDerivative(float t, float p1, float p2) {
      float inv = 1.0F - t;
      return 3.0F * inv * inv * p1 + 6.0F * inv * t * (p2 - p1) + 3.0F * t * t * (1.0F - p2);
   }
}
