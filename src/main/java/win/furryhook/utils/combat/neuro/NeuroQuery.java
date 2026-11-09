package win.furryhook.utils.combat.neuro;

import net.minecraft.util.Mth;

public record NeuroQuery(
   float errYaw,
   float errPitch,
   float prevDeltaYaw,
   float prevDeltaPitch,
   float targetYawSpeed,
   float targetPitchSpeed,
   float distance,
   float sinceHit,
   float targetSpeed,
   boolean duel
) {
   private static final float ERR_YAW_SCALE = 8.0F;
   private static final float ERR_PITCH_SCALE = 6.0F;
   private static final float DELTA_SCALE = 4.0F;
   private static final float SPEED_SCALE = 5.0F;
   private static final float DISTANCE_SCALE = 1.5F;
   private static final float SINCE_HIT_SCALE = 6.0F;
   private static final float TARGET_SPEED_SCALE = 0.25F;
   private static final float ERR_WEIGHT = 1.0F;
   private static final float DELTA_WEIGHT = 0.6F;
   private static final float SPEED_WEIGHT = 0.5F;
   private static final float DISTANCE_WEIGHT = 0.35F;
   private static final float SINCE_HIT_WEIGHT = 0.3F;
   private static final float TARGET_SPEED_WEIGHT = 0.3F;
   private static final float DUEL_MISMATCH = 0.35F;

   public float distanceSqr(float[] data, int offset, float duelBias) {
      float yawGap = Mth.wrapDegrees(this.errYaw - data[offset + 0]);
      float total = term(yawGap, 8.0F, 1.0F)
         + term(this.errPitch - data[offset + 1], 6.0F, 1.0F)
         + term(this.prevDeltaYaw - data[offset + 2], 4.0F, 0.6F)
         + term(this.prevDeltaPitch - data[offset + 3], 4.0F, 0.6F)
         + term(this.targetYawSpeed - data[offset + 4], 5.0F, 0.5F)
         + term(this.targetPitchSpeed - data[offset + 5], 5.0F, 0.5F)
         + term(this.distance - data[offset + 6], 1.5F, 0.35F)
         + term(this.sinceHit - data[offset + 7], 6.0F, 0.3F)
         + term(this.targetSpeed - data[offset + 10], 0.25F, 0.3F);
      if (duelBias > 0.0F) {
         boolean frameDuel = data[offset + 15] > 0.5F;
         if (frameDuel != this.duel) {
            total += 0.35F * duelBias;
         }
      }

      return total;
   }

   private static float term(float difference, float scale, float weight) {
      float normalized = difference / scale;
      return normalized * normalized * weight;
   }
}
