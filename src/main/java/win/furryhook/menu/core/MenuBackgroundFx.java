package win.furryhook.menu.core;

import java.util.Random;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.Render2DUtil;

public final class MenuBackgroundFx {
   private static final float TAU = (float) (Math.PI * 2);
   private static final int GLOW_BANDS = 16;
   private static final float GLOW_FALLOFF = 2.3F;
   private static final float BASE_HEIGHT = 0.3F;
   private static final float BASE_ALPHA = 0.26F;
   private static final float DOME_HALF_WIDTH = 0.36F;
   private static final float DOME_HEIGHT = 0.52F;
   private static final float DOME_ALPHA = 0.2F;
   private static final float DOME_EDGE_FADE = 0.55F;
   private static final float POOL_HALF_WIDTH = 0.17F;
   private static final float POOL_HEIGHT = 0.28F;
   private static final float POOL_ALPHA = 0.13F;
   private static final float POOL_EDGE_FADE = 0.7F;
   private static final float BREATH_BASE_HZ = 0.11F;
   private static final float BREATH_DOME_HZ = 0.17F;
   private static final float BREATH_POOL_HZ = 0.13F;
   private static final float BREATH_DEPTH = 0.22F;
   private static final int MAX_EMBERS = 72;
   private static final float SPAWN_RATE = 13.0F;
   private static final int EMBER_WARM = -23204;
   private static final float RISE_MIN = 26.0F;
   private static final float RISE_MAX = 78.0F;
   private static final float LIFE_MIN = 3.4F;
   private static final float LIFE_MAX = 8.5F;
   private static final float SIZE_MIN = 1.1F;
   private static final float SIZE_MAX = 3.4F;
   private static final float SWAY_MIN = 5.0F;
   private static final float SWAY_MAX = 26.0F;
   private static final Random RANDOM = new Random();
   private static final MenuBackgroundFx.Ember[] EMBERS = new MenuBackgroundFx.Ember[72];
   private static float time;
   private static float spawnAccumulator;

   private MenuBackgroundFx() {
   }

   public static void render(float screenWidth, float screenHeight, float delta, float openT, float scale, boolean lightEnabled, boolean embersEnabled) {
      if (!(openT <= 0.01F) && !(screenWidth <= 0.0F) && !(screenHeight <= 0.0F)) {
         time += delta;
         int accent = Theme.getAccent();
         if (lightEnabled) {
            renderLight(screenWidth, screenHeight, openT, accent);
         }

         if (embersEnabled) {
            updateEmbers(screenWidth, screenHeight, delta);
            renderEmbers(openT, scale, accent);
         } else {
            releaseEmbers();
         }
      }
   }

   public static void releaseEmbers() {
      for (MenuBackgroundFx.Ember ember : EMBERS) {
         ember.alive = false;
      }

      spawnAccumulator = 0.0F;
   }

   private static void renderLight(float screenWidth, float screenHeight, float openT, int accent) {
      float bottom = screenHeight;
      int rgb = accent & 16777215;
      glow(screenWidth * 0.5F, bottom, screenWidth * 0.5F, screenHeight * 0.3F, rgb, 0.26F * openT * breath(0.11F, 0.0F), 0.0F, 1.0F);
      glow(screenWidth * 0.5F, bottom, screenWidth * 0.36F, screenHeight * 0.52F, rgb, 0.2F * openT * breath(0.17F, 1.7F), 0.55F, 1.35F);
      float poolHalfWidth = screenWidth * 0.17F;
      float poolHeight = screenHeight * 0.28F;
      glow(screenWidth * 0.17F, bottom, poolHalfWidth, poolHeight, rgb, 0.13F * openT * breath(0.13F, 0.0F), 0.7F, 1.2F);
      glow(screenWidth * 0.83F, bottom, poolHalfWidth, poolHeight, rgb, 0.13F * openT * breath(0.13F, 3.1F), 0.7F, 1.2F);
   }

   private static float breath(float hz, float phase) {
      return 0.89F + 0.11F * (float)Math.sin(time * hz * (float) (Math.PI * 2) + phase);
   }

   private static void glow(float centerX, float bottomY, float halfWidth, float height, int rgb, float peakAlpha, float edgeFade, float spread) {
      if (!(peakAlpha <= 0.002F) && !(halfWidth <= 0.0F) && !(height <= 0.0F)) {
         for (int band = 0; band < 16; band++) {
            float t0 = band / 16.0F;
            float t1 = (band + 1) / 16.0F;
            float alphaBottom = falloff(t0) * peakAlpha;
            float alphaTop = falloff(t1) * peakAlpha;
            if (!(alphaBottom <= 0.002F) || !(alphaTop <= 0.002F)) {
               float bandTop = bottomY - height * t1;
               float bandHeight = height * (t1 - t0);
               float bandHalf = halfWidth * (1.0F + (spread - 1.0F) * (t0 + t1) * 0.5F);
               if (edgeFade <= 0.001F) {
                  Render2DUtil.rect(centerX - bandHalf, bandTop, bandHalf * 2.0F, bandHeight)
                     .color(
                        ColorUtil.withAlpha(rgb, alphaTop),
                        ColorUtil.withAlpha(rgb, alphaTop),
                        ColorUtil.withAlpha(rgb, alphaBottom),
                        ColorUtil.withAlpha(rgb, alphaBottom)
                     )
                     .draw();
               } else {
                  float fadeWidth = bandHalf * MathUtil.clamp(edgeFade, 0.0F, 1.0F);
                  float coreHalf = bandHalf - fadeWidth;
                  int topSolid = ColorUtil.withAlpha(rgb, alphaTop);
                  int bottomSolid = ColorUtil.withAlpha(rgb, alphaBottom);
                  int clear = rgb;
                  if (coreHalf > 0.0F) {
                     Render2DUtil.rect(centerX - coreHalf, bandTop, coreHalf * 2.0F, bandHeight).color(topSolid, topSolid, bottomSolid, bottomSolid).draw();
                  }

                  Render2DUtil.rect(centerX - bandHalf, bandTop, fadeWidth, bandHeight).color(clear, topSolid, bottomSolid, clear).draw();
                  Render2DUtil.rect(centerX + coreHalf, bandTop, fadeWidth, bandHeight).color(topSolid, clear, clear, bottomSolid).draw();
               }
            }
         }
      }
   }

   private static float falloff(float t) {
      return (float)Math.pow(1.0F - MathUtil.clamp(t, 0.0F, 1.0F), 2.3F);
   }

   private static void updateEmbers(float screenWidth, float screenHeight, float delta) {
      for (MenuBackgroundFx.Ember ember : EMBERS) {
         if (ember.alive) {
            ember.age += delta;
            ember.y = ember.y - ember.rise * delta;
            ember.rise = Math.max(ember.rise * (1.0F - 0.22F * delta), 10.400001F);
            if (ember.age >= ember.life || ember.y < -ember.size * 4.0F) {
               ember.alive = false;
            }
         }
      }

      spawnAccumulator += delta * 13.0F * (screenWidth / 1920.0F);
      int spawns = (int)spawnAccumulator;
      spawnAccumulator -= spawns;

      for (int i = 0; i < spawns; i++) {
         spawn(screenWidth, screenHeight);
      }
   }

   private static void spawn(float screenWidth, float screenHeight) {
      for (MenuBackgroundFx.Ember ember : EMBERS) {
         if (!ember.alive) {
            ember.alive = true;
            ember.age = 0.0F;
            ember.life = range(3.4F, 8.5F);
            float centered = (RANDOM.nextFloat() + RANDOM.nextFloat()) * 0.5F;
            ember.originX = screenWidth * MathUtil.clamp(centered + range(-0.22F, 0.22F), -0.05F, 1.05F);
            ember.y = screenHeight + range(0.0F, screenHeight * 0.12F);
            ember.rise = range(26.0F, 78.0F);
            ember.size = range(1.1F, 3.4F);
            ember.swayAmplitude = range(5.0F, 26.0F);
            ember.swayFrequency = range(0.15F, 0.55F);
            ember.swayPhase = range(0.0F, (float) (Math.PI * 2));
            ember.flickerFrequency = range(1.6F, 5.4F);
            ember.flickerPhase = range(0.0F, (float) (Math.PI * 2));
            ember.warmth = RANDOM.nextFloat() * RANDOM.nextFloat();
            ember.accentTinted = RANDOM.nextInt(3) == 0;
            return;
         }
      }
   }

   private static void renderEmbers(float openT, float scale, int accent) {
      for (MenuBackgroundFx.Ember ember : EMBERS) {
         if (ember.alive) {
            float t = ember.age / ember.life;
            float fadeIn = MathUtil.clamp(t / 0.12F, 0.0F, 1.0F);
            float fadeOut = MathUtil.clamp((1.0F - t) / 0.4F, 0.0F, 1.0F);
            float flicker = 0.62F + 0.38F * (float)Math.sin(time * ember.flickerFrequency + ember.flickerPhase);
            float alpha = fadeIn * fadeOut * flicker * openT;
            if (!(alpha <= 0.004F)) {
               float x = ember.originX + (float)Math.sin(time * ember.swayFrequency * (float) (Math.PI * 2) + ember.swayPhase) * ember.swayAmplitude * scale;
               float y = ember.y;
               float size = ember.size * scale;
               int warmthBase = ember.accentTinted ? ColorUtil.lerp(accent, ColorUtil.rgb(255, 255, 255), 0.25F) : ColorUtil.lerp(accent, -23204, ember.warmth);
               int base = warmthBase & 16777215;
               int core = ColorUtil.lerp(base | 0xFF000000, -1, 0.55F) & 16777215;
               float halo = size * 4.2F;
               Render2DUtil.rect(x - halo * 0.5F, y - halo * 0.5F, halo, halo).color(ColorUtil.withAlpha(base, 0.1F * alpha)).radius(halo * 0.5F).draw();
               float mid = size * 2.1F;
               Render2DUtil.rect(x - mid * 0.5F, y - mid * 0.5F, mid, mid).color(ColorUtil.withAlpha(base, 0.28F * alpha)).radius(mid * 0.5F).draw();
               Render2DUtil.rect(x - size * 0.5F, y - size * 0.5F, size, size).color(ColorUtil.withAlpha(core, 0.92F * alpha)).radius(size * 0.5F).draw();
            }
         }
      }
   }

   private static float range(float min, float max) {
      return min + RANDOM.nextFloat() * (max - min);
   }

   static {
      for (int i = 0; i < EMBERS.length; i++) {
         EMBERS[i] = new MenuBackgroundFx.Ember();
      }
   }

   private static final class Ember {
      private boolean alive;
      private float originX;
      private float y;
      private float rise;
      private float age;
      private float life;
      private float size;
      private float swayAmplitude;
      private float swayFrequency;
      private float swayPhase;
      private float flickerFrequency;
      private float flickerPhase;
      private float warmth;
      private boolean accentTinted;
   }
}
