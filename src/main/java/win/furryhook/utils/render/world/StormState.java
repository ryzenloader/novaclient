package win.furryhook.utils.render.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import win.furryhook.feature.impl.visual.AtmosphereFeature;

public final class StormState {
   public static final StormState INSTANCE = new StormState();
   private static final float BOLT_WINDOW = 0.45F;
   private static final float FLASH_WINDOW = 0.95F;
   private static final float MAX_FRAME_DELTA = 0.1F;
   private static final int MAX_TRACKED = 4;
   private static final float RAIN_WRAP = 4096.0F;
   private static final float DRIFT_WRAP = 65536.0F;
   private static final float RIPPLE_WRAP = 1000.0F;
   private static final float RAIN_FALL = 24.0F;
   private static final float SNOW_FALL = 2.5F;
   private final Random random = new Random();
   private final List<StormState.Strike> strikes = new ArrayList<>();
   private long lastNanos;
   private ClientLevel lastLevel;
   private float intensity;
   private float windX = 1.0F;
   private float windZ;
   private float windStrength;
   private float windPhase;
   private float strikeCooldown = 0.8F;
   private float rainPhase;
   private float fogDrift;
   private float ripplePhase;
   private float skyVisibility = 1.0F;
   private float groundHeight = 64.0F;
   private boolean groundHeightKnown;

   private StormState() {
   }

   public void update(AtmosphereFeature feature, Vec3 cameraPos) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != this.lastLevel) {
         this.lastLevel = level;
         this.reset();
      }

      long now = System.nanoTime();
      float delta = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1.0E9F;
      this.lastNanos = now;
      delta = Mth.clamp(delta, 0.0F, 0.1F);
      this.advanceIntensity(feature, level, delta);
      this.advanceWind(delta);
      this.advanceScroll(feature, delta);
      this.advanceSkyVisibility(level, cameraPos, delta);
      this.advanceGroundHeight(level, cameraPos, delta);
      this.advanceStrikes(feature, cameraPos, delta);
   }

   public float rainPhase() {
      return this.rainPhase;
   }

   public float fogDrift() {
      return this.fogDrift;
   }

   public float ripplePhase() {
      return this.ripplePhase;
   }

   public float skyVisibility() {
      return this.skyVisibility;
   }

   public float groundHeight() {
      return this.groundHeight;
   }

   public float intensity() {
      return this.intensity;
   }

   public float windX() {
      return this.windX;
   }

   public float windZ() {
      return this.windZ;
   }

   public float windStrength() {
      return this.windStrength;
   }

   public StormState.Strike activeStrike() {
      StormState.Strike best = null;

      for (StormState.Strike strike : this.strikes) {
         if (strike.age <= 0.45F && (best == null || strike.age < best.age)) {
            best = strike;
         }
      }

      return best;
   }

   public float flashStrength() {
      float strongest = 0.0F;

      for (StormState.Strike strike : this.strikes) {
         if (!strike.manual) {
            strongest = Math.max(strongest, flashEnvelope(strike));
         }
      }

      return strongest;
   }

   public boolean hasVisibleStrike() {
      for (StormState.Strike strike : this.strikes) {
         if (strike.age <= 0.95F) {
            return true;
         }
      }

      return false;
   }

   public void strikeAt(Vec3 impactPos, double distanceFromCamera) {
      if (impactPos != null) {
         if (this.strikes.size() >= 4) {
            this.strikes.remove(0);
         }

         StormState.Strike strike = new StormState.Strike();
         strike.seed = this.random.nextFloat() * 900.0F;
         strike.topX = impactPos.x();
         strike.topY = impactPos.y() + 9.0 + this.random.nextDouble() * 5.0;
         strike.topZ = impactPos.z();
         strike.bottomX = impactPos.x();
         strike.bottomY = impactPos.y();
         strike.bottomZ = impactPos.z();
         strike.distance = distanceFromCamera;
         strike.falloff = (float)Mth.clamp(1.0 - distanceFromCamera / 600.0, 0.35, 1.0);
         strike.manual = true;
         this.strikes.add(strike);
      }
   }

   public void reset() {
      this.strikes.clear();
      this.lastNanos = 0L;
      this.strikeCooldown = 0.8F;
      this.intensity = 0.0F;
      this.rainPhase = 0.0F;
      this.fogDrift = 0.0F;
      this.ripplePhase = 0.0F;
      this.skyVisibility = 1.0F;
      this.groundHeightKnown = false;
   }

   private void advanceScroll(AtmosphereFeature feature, float delta) {
      float fall = (feature.usesSnow() ? 2.5F : 24.0F) * feature.rainSpeed.getValue().floatValue();
      this.rainPhase = (this.rainPhase + delta * fall) % 4096.0F;
      this.fogDrift = (this.fogDrift + delta * (1.5F + 4.0F * this.windStrength)) % 65536.0F;
      this.ripplePhase = (this.ripplePhase + delta) % 1000.0F;
   }

   private void advanceSkyVisibility(ClientLevel level, Vec3 cameraPos, float delta) {
      LocalPlayer player = Minecraft.getInstance().player;
      boolean open = level != null
         && cameraPos != null
         && level.dimensionType().hasSkyLight()
         && level.canSeeSky(BlockPos.containing(cameraPos))
         && (player == null || !player.isUnderWater());
      float target = open ? 1.0F : 0.0F;
      float step = delta * 2.5F;
      this.skyVisibility = Math.abs(target - this.skyVisibility) <= step ? target : this.skyVisibility + Math.signum(target - this.skyVisibility) * step;
   }

   private void advanceGroundHeight(ClientLevel level, Vec3 cameraPos, float delta) {
      if (level != null && cameraPos != null) {
         BlockPos pos = BlockPos.containing(cameraPos);
         if (level.hasChunkAt(pos)) {
            float target = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            if (!this.groundHeightKnown) {
               this.groundHeight = target;
               this.groundHeightKnown = true;
            } else {
               float difference = target - this.groundHeight;
               float step = delta * (2.5F + Math.abs(difference) * 1.8F);
               this.groundHeight = Math.abs(difference) <= step ? target : this.groundHeight + Math.signum(difference) * step;
            }
         }
      }
   }

   private void advanceIntensity(AtmosphereFeature feature, ClientLevel level, float delta) {
      float target;
      if (!feature.isEnabled()) {
         target = 0.0F;
      } else if ("Manual".equals(feature.stormSource.getValue())) {
         target = feature.stormPower.getValue().floatValue();
      } else if (level != null) {
         target = Mth.clamp(level.getRainLevel(1.0F) * 0.45F + level.getThunderLevel(1.0F) * 0.85F, 0.0F, 1.0F);
      } else {
         target = 0.0F;
      }

      float step = delta * 0.5F;
      this.intensity = Math.abs(target - this.intensity) <= step ? target : this.intensity + Math.signum(target - this.intensity) * step;
   }

   private void advanceWind(float delta) {
      this.windPhase += delta * 0.06F;
      float angle = this.windPhase + Mth.sin(this.windPhase * 0.73F) * 1.6F;
      this.windX = Mth.cos(angle);
      this.windZ = Mth.sin(angle);
      this.windStrength = 0.3F + 0.7F * this.intensity;
   }

   private void advanceStrikes(AtmosphereFeature feature, Vec3 cameraPos, float delta) {
      for (int i = this.strikes.size() - 1; i >= 0; i--) {
         StormState.Strike strike = this.strikes.get(i);
         strike.age += delta;
         if (strike.age > 0.95F) {
            this.strikes.remove(i);
         }
      }

      if (feature.isEnabled() && feature.lightning.getValue() && !(this.intensity <= 0.02F) && cameraPos != null) {
         this.strikeCooldown -= delta;
         if (!(this.strikeCooldown > 0.0F)) {
            this.strikeCooldown = this.nextInterval(feature);
            if (this.strikes.size() >= 4) {
               this.strikes.remove(0);
            }

            this.strikes.add(this.spawn(cameraPos, this.azimuth(), feature.strikeDistance.getValue() * (0.5 + this.random.nextDouble() * 0.5)));
         }
      }
   }

   private float nextInterval(AtmosphereFeature feature) {
      float rate = Math.max(feature.strikeRate.getValue().floatValue(), 0.05F);
      float base = Mth.lerp(this.intensity, 11.0F, 1.2F) / rate;
      return base * (0.55F + this.random.nextFloat() * 0.9F);
   }

   private StormState.Strike spawn(Vec3 cameraPos, double azimuth, double distance) {
      double baseX = cameraPos.x() + Math.cos(azimuth) * distance;
      double baseZ = cameraPos.z() + Math.sin(azimuth) * distance;
      double lean = (6.0 + this.random.nextDouble() * 16.0) * this.windStrength;
      StormState.Strike strike = new StormState.Strike();
      strike.seed = this.random.nextFloat() * 900.0F;
      strike.topX = baseX + this.windX * lean;
      strike.topY = cameraPos.y() + 60.0 + this.random.nextDouble() * 70.0;
      strike.topZ = baseZ + this.windZ * lean;
      strike.bottomX = baseX;
      strike.bottomY = cameraPos.y() - 20.0 - this.random.nextDouble() * 30.0;
      strike.bottomZ = baseZ;
      strike.distance = distance;
      strike.falloff = (float)Mth.clamp(1.0 - distance / 600.0, 0.35, 1.0);
      return strike;
   }

   private double azimuth() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && !(this.random.nextFloat() > 0.66F)) {
         double yaw = Math.toRadians(player.getYRot());
         double facing = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
         return facing + (this.random.nextDouble() - 0.5) * 1.9;
      } else {
         return this.random.nextDouble() * Math.PI * 2.0;
      }
   }

   public static float boltEnvelope(StormState.Strike strike) {
      float age = strike.age;
      if (!(age < 0.0F) && !(age > 0.45F)) {
         float attack = Math.min(1.0F, age / 0.012F);
         float decay = (float)Math.exp(-age * 6.5F);
         return attack * decay * (0.42F + 0.58F * Math.abs(Mth.sin(age * 33.0F + strike.seed)));
      } else {
         return 0.0F;
      }
   }

   private static float flashEnvelope(StormState.Strike strike) {
      float age = strike.age;
      if (!(age < 0.0F) && !(age > 0.95F)) {
         float attack = Math.min(1.0F, age / 0.02F);
         float decay = (float)Math.exp(-age * 4.2F);
         return attack * decay * (0.6F + 0.4F * Mth.sin(age * 27.0F + strike.seed)) * strike.falloff;
      } else {
         return 0.0F;
      }
   }

   public static final class Strike {
      public double topX;
      public double topY;
      public double topZ;
      public double bottomX;
      public double bottomY;
      public double bottomZ;
      public double distance;
      public float seed;
      float falloff = 1.0F;
      boolean manual;
      float age;
   }
}
