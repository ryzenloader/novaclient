package win.furryhook.utils.math;

public final class AnimatedValue {
   private float value;
   private boolean initialized;
   private long lastUpdateNanos = -1L;

   public float update(float target, float smoothTime, long nowNanos) {
      if (!this.initialized) {
         this.value = target;
         this.initialized = true;
         this.lastUpdateNanos = nowNanos;
         return this.value;
      }

      if (smoothTime <= 0.0F) {
         this.value = target;
         this.lastUpdateNanos = nowNanos;
         return this.value;
      }

      if (this.lastUpdateNanos < 0L) {
         this.lastUpdateNanos = nowNanos;
      }

      float deltaTime = (float)(nowNanos - this.lastUpdateNanos) / 1.0E9F;
      this.lastUpdateNanos = nowNanos;
      float safeDeltaTime = MathUtil.clamp(deltaTime, 0.0F, 0.1F);
      float safeSmoothTime = Math.max(1.0E-4F, smoothTime);
      float factor = 1.0F - (float)Math.exp(-safeDeltaTime / safeSmoothTime);
      this.value = this.value + (target - this.value) * factor;
      if (Math.abs(target - this.value) <= 5.0E-4F) {
         this.value = target;
      }

      return this.value;
   }

   public float getValue() {
      return this.value;
   }

   public void setValue(float value) {
      this.value = value;
      this.initialized = true;
      this.lastUpdateNanos = -1L;
   }

   public void reset() {
      this.value = 0.0F;
      this.initialized = false;
      this.lastUpdateNanos = -1L;
   }
}
