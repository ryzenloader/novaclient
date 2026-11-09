package win.furryhook.emote;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EmoteEngine {
   public static final EmoteEngine INSTANCE = new EmoteEngine();
   private static final float TICKS_PER_SECOND = 20.0F;
   private final Map<UUID, EmoteEngine.Playback> active = new ConcurrentHashMap<>();

   private EmoteEngine() {
   }

   public void play(UUID entity, EmoteDefinition definition) {
      if (entity != null && definition != null) {
         this.active.put(entity, new EmoteEngine.Playback(definition.animation(), System.nanoTime()));
      }
   }

   public void stop(UUID entity) {
      if (entity != null) {
         this.active.remove(entity);
      }
   }

   public boolean isPlaying(UUID entity) {
      return entity != null && this.active.containsKey(entity);
   }

   public EnumMap<EmotePart, float[]> poseFor(UUID entity) {
      if (entity == null) {
         return null;
      }

      EmoteEngine.Playback playback = this.active.get(entity);
      if (playback == null) {
         return null;
      }

      EmoteAnimation animation = playback.animation();
      float elapsedTicks = (float)(System.nanoTime() - playback.startNanos()) / 1.0E9F * 20.0F;
      float tick = animation.beginTick() + elapsedTicks;
      if (tick >= animation.stopTick()) {
         if (animation.loop() && animation.endTick() > animation.beginTick()) {
            float span = animation.endTick() - animation.beginTick();
            float looped = animation.beginTick() + (tick - animation.beginTick()) % span;
            return animation.sample(looped);
         } else {
            this.active.remove(entity);
            return null;
         }
      } else {
         return animation.sample(tick);
      }
   }

   private record Playback(EmoteAnimation animation, long startNanos) {
   }
}
