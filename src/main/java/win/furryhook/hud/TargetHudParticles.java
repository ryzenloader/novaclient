package win.furryhook.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.gui.Render2DUtil;

public final class TargetHudParticles {
   private static final int MAX_PARTICLES = 220;
   private static final float FULL_HIT = 8.0F;
   private final List<TargetHudParticles.Particle> particles = new ArrayList<>();
   private final Random random = new Random();
   private long lastUpdate;

   public void update() {
      long now = System.currentTimeMillis();
      float delta = this.lastUpdate == 0L ? 0.016F : (float)Math.min(80L, now - this.lastUpdate) / 1000.0F;
      this.lastUpdate = now;
      if (!this.particles.isEmpty()) {
         for (int i = this.particles.size() - 1; i >= 0; i--) {
            TargetHudParticles.Particle particle = this.particles.get(i);
            particle.life -= delta;
            if (particle.life <= 0.0F) {
               this.particles.remove(i);
            } else {
               particle.velocityY = particle.velocityY + particle.gravity * delta;
               float damping = Math.max(0.0F, 1.0F - particle.drag * delta);
               particle.velocityX *= damping;
               particle.velocityY *= damping;
               particle.x = particle.x + particle.velocityX * delta;
               particle.y = particle.y + particle.velocityY * delta;
            }
         }
      }
   }

   public void clear() {
      this.particles.clear();
   }

   public boolean isEmpty() {
      return this.particles.isEmpty();
   }

   public void emitHealthVertical(float barX, float barWidth, float edgeY, float unit, int color, int count) {
      for (int i = 0; i < count; i++) {
         TargetHudParticles.Particle particle = this.spawn(color, unit);
         if (particle == null) {
            return;
         }

         particle.x = barX + this.random.nextFloat() * barWidth;
         particle.y = edgeY + (this.random.nextFloat() - 0.5F) * 1.5F * unit;
         particle.velocityX = (this.random.nextFloat() - 0.5F) * 26.0F * unit;
         particle.velocityY = (2.0F + this.random.nextFloat() * 16.0F) * unit;
         particle.gravity = 52.0F * unit;
         particle.drag = 1.6F;
         particle.size = (0.5F + this.random.nextFloat() * 0.9F) * unit;
         particle.maxLife = 0.32F + this.random.nextFloat() * 0.38F;
         particle.life = particle.maxLife;
      }
   }

   public void emitHealthHorizontal(float edgeX, float barY, float barHeight, float unit, int color, int count) {
      for (int i = 0; i < count; i++) {
         TargetHudParticles.Particle particle = this.spawn(color, unit);
         if (particle == null) {
            return;
         }

         particle.x = edgeX + (this.random.nextFloat() - 0.5F) * 1.5F * unit;
         particle.y = barY + this.random.nextFloat() * barHeight;
         particle.velocityX = (3.0F + this.random.nextFloat() * 20.0F) * unit;
         particle.velocityY = (this.random.nextFloat() - 0.6F) * 20.0F * unit;
         particle.gravity = 58.0F * unit;
         particle.drag = 1.8F;
         particle.size = (0.5F + this.random.nextFloat() * 0.9F) * unit;
         particle.maxLife = 0.3F + this.random.nextFloat() * 0.36F;
         particle.life = particle.maxLife;
      }
   }

   public void emitHeadBurst(float headX, float headY, float headSize, float unit, int color, int count) {
      float centerX = headX + headSize * 0.5F;
      float centerY = headY + headSize * 0.5F;
      float ring = headSize * 0.34F;

      for (int i = 0; i < count; i++) {
         TargetHudParticles.Particle particle = this.spawn(color, unit);
         if (particle == null) {
            return;
         }

         double angle = this.random.nextDouble() * Math.PI * 2.0;
         float radius = ring * (0.6F + this.random.nextFloat() * 0.6F);
         float dirX = (float)Math.cos(angle);
         float dirY = (float)Math.sin(angle);
         float speed = (26.0F + this.random.nextFloat() * 40.0F) * unit;
         particle.x = centerX + dirX * radius;
         particle.y = centerY + dirY * radius;
         particle.velocityX = dirX * speed;
         particle.velocityY = dirY * speed - 8.0F * unit;
         particle.gravity = 74.0F * unit;
         particle.drag = 3.4F;
         particle.size = (0.6F + this.random.nextFloat() * 1.1F) * unit;
         particle.maxLife = 0.34F + this.random.nextFloat() * 0.42F;
         particle.life = particle.maxLife;
      }
   }

   public void draw(float alpha) {
      if (!this.particles.isEmpty() && !(alpha <= 0.01F)) {
         for (TargetHudParticles.Particle particle : this.particles) {
            float progress = Math.clamp(particle.life / particle.maxLife, 0.0F, 1.0F);
            float fade = progress * progress * (3.0F - 2.0F * progress);
            float size = particle.size * (0.45F + 0.55F * progress);
            if (!(size <= 0.05F)) {
               float halo = size * 2.6F;
               Render2DUtil.rect(particle.x - halo * 0.5F, particle.y - halo * 0.5F, halo, halo)
                  .color(ColorUtil.multiplyAlpha(particle.color, alpha * fade * 0.22F))
                  .radius(halo * 0.5F)
                  .draw();
               Render2DUtil.rect(particle.x - size * 0.5F, particle.y - size * 0.5F, size, size)
                  .color(ColorUtil.multiplyAlpha(particle.color, alpha * fade))
                  .radius(size * 0.5F)
                  .draw();
            }
         }
      }
   }

   public static int countFor(float damage, int min, int max) {
      float scaled = Math.clamp(damage / 8.0F, 0.0F, 1.0F);
      return Math.round(min + (max - min) * scaled);
   }

   private TargetHudParticles.Particle spawn(int color, float unit) {
      if (this.particles.size() < 220 && !(unit <= 0.0F)) {
         TargetHudParticles.Particle particle = new TargetHudParticles.Particle();
         particle.color = this.random.nextFloat() < 0.28F ? ColorUtil.lerp(color, -1, 0.55F) : color;
         this.particles.add(particle);
         return particle;
      } else {
         return null;
      }
   }

   private static final class Particle {
      float x;
      float y;
      float velocityX;
      float velocityY;
      float size;
      float gravity;
      float drag;
      float life;
      float maxLife;
      int color;
   }
}
