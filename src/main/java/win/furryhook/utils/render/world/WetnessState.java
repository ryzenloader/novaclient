package win.furryhook.utils.render.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.visual.AtmosphereFeature;
import win.furryhook.feature.impl.visual.RtxFeature;

public final class WetnessState {
   public static final WetnessState INSTANCE = new WetnessState();
   private static final float MAX_FRAME_DELTA = 0.1F;
   private static final float WET_RATE = 0.45F;
   private static final float DRY_RATE = 0.035F;
   private static final float PHASE_WRAP = 1000.0F;
   private static final float DRIFT_WRAP = 65536.0F;
   private long lastNanos;
   private ClientLevel lastLevel;
   private float wetness;
   private float rainAmount;
   private float ripplePhase;
   private float drift;

   private WetnessState() {
   }

   public void update(RtxFeature feature) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != this.lastLevel) {
         this.lastLevel = level;
         this.reset();
      }

      long now = System.nanoTime();
      float delta = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1.0E9F;
      this.lastNanos = now;
      delta = Mth.clamp(delta, 0.0F, 0.1F);
      this.ripplePhase = (this.ripplePhase + delta) % 1000.0F;
      this.drift = (this.drift + delta * 0.35F) % 65536.0F;
      float target;
      if (feature.usesManualWetness()) {
         target = feature.wetness.getValue().floatValue();
         this.rainAmount = target;
      } else {
         float weather = weatherIntensity(level);
         this.rainAmount = weather;
         target = weather;
      }

      float rate = target > this.wetness ? 0.45F : 0.035F;
      float step = delta * rate;
      this.wetness = Math.abs(target - this.wetness) <= step ? target : this.wetness + Math.signum(target - this.wetness) * step;
   }

   public float wetness() {
      return this.wetness;
   }

   public float rainAmount() {
      return this.rainAmount;
   }

   public float ripplePhase() {
      return this.ripplePhase;
   }

   public float drift() {
      return this.drift;
   }

   public void reset() {
      this.lastNanos = 0L;
      this.wetness = 0.0F;
      this.rainAmount = 0.0F;
      this.ripplePhase = 0.0F;
      this.drift = 0.0F;
   }

   private static float weatherIntensity(ClientLevel level) {
      AtmosphereFeature atmosphere = FeatureManager.INSTANCE.getFeature(AtmosphereFeature.class);
      if (atmosphere != null && atmosphere.isEnabled() && atmosphere.usesRain()) {
         return Mth.clamp(StormState.INSTANCE.intensity(), 0.0F, 1.0F);
      } else {
         return level == null ? 0.0F : Mth.clamp(level.getRainLevel(1.0F), 0.0F, 1.0F);
      }
   }
}
