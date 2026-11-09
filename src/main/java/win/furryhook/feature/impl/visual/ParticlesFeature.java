package win.furryhook.feature.impl.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.EntityDeathEvent;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.particles.ProceduralParticleRenderer;
import win.furryhook.utils.render.particles.ProceduralParticleState;

public final class ParticlesFeature extends Feature implements MinecraftContext {
   private static final double SPAWN_PI = 3.1415928936382223;
   private static final int WORLD_BATCH_SIZE = 10;
   private static final double WALK_SPEED_THRESHOLD = 0.004;
   private static final int MAX_TRACKED_ITEMS = 32;
   private static final double PHYSICS_SCALE = 0.01;
   private static final int HIT_BURST = 15;
   private static final int WALK_BURST = 1;
   private static final int JUMP_BURST = 6;
   private static final int LAND_BURST = 10;
   private static final String MODE_HIT = "Hit";
   private static final String MODE_WORLD = "World";
   private static final String MODE_WALKING = "Walking";
   private static final String MODE_FALLING_ITEMS = "Falling Items";
   private static final String MODE_CRYSTAL = "Crystal";
   private static final String MODE_DEATH = "Death";
   private static final String ITEM_ENDER_PEARL = "Ender Pearl";
   private static final String ITEM_TRIDENT = "Trident";
   private static final String ITEM_SNOWBALL = "Snowball";
   private static final String ITEM_EGG = "Egg";
   private static final String ITEM_POTIONS = "Potions";
   private static final String ITEM_ARROWS = "Arrows";
   private static final String ITEM_FISHING_BOBBER = "Fishing Bobber";
   private static final String ITEM_DROPPED = "Dropped Items";
   public final MultiSelectSetting workFrom = this.register(
      new MultiSelectSetting("Work From", Set.of("Hit"), "Hit", "World", "Walking", "Falling Items", "Crystal", "Death").configKey("render.particles.workfrom")
   );
   public final ModeSetting shape = this.register(
      new ModeSetting("Shape", ProceduralParticleRenderer.Shape.RANDOM.displayName(), ProceduralParticleRenderer.Shape.displayNames())
         .configKey("render.particles.texture")
   );
   public final ModeSetting colorMode = this.register(ColorMode.setting().configKey("render.particles.colormode"));
   public final ColorSetting color = this.register(
      new ColorSetting("Color", -1).configKey("render.particles.color").visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );
   public final BooleanSetting advanced = this.register(new BooleanSetting("Advanced", false).asExpander().configKey("render.particles.advanced"));
   public final NumberSetting lifetime = this.register(
      new NumberSetting("Lifetime", 60.0, 10.0, 200.0, 1.0, " ticks").configKey("render.particles.lifetime").advancedOnly()
   );
   public final NumberSetting size = this.register(
      new NumberSetting("Size", 0.15, 0.02, 1.0, 0.01, " blocks").configKey("render.particles.size").advancedOnly()
   );
   public final NumberSetting sizeVariation = this.register(
      new NumberSetting("Size Variation", 0.35, 0.0, 1.0, 0.05, "").configKey("render.particles.sizevariation").advancedOnly()
   );
   public final NumberSetting glow = this.register(new NumberSetting("Glow", 0.5, 0.0, 1.5, 0.05, "").configKey("render.particles.glow").advancedOnly());
   public final BooleanSetting bloom = this.register(new BooleanSetting("Bloom", true).configKey("render.particles.bloom").advancedOnly());
   public final NumberSetting bloomIntensity = this.register(
      new NumberSetting("Bloom Intensity", 1.0, 0.0, 3.0, 0.05, "")
         .configKey("render.particles.bloomintensity")
         .visibleWhen(this.bloom::getValue)
         .advancedOnly()
   );
   public final NumberSetting bloomRadius = this.register(
      new NumberSetting("Bloom Radius", 3.0, 1.0, 4.0, 1.0, "").configKey("render.particles.bloomradius").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting hdrBoost = this.register(
      new NumberSetting("HDR Boost", 2.0, 0.5, 6.0, 0.1, "").configKey("render.particles.hdrboost").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting anamorphic = this.register(
      new NumberSetting("Anamorphic", 0.35, 0.0, 1.0, 0.05, "").configKey("render.particles.anamorphic").visibleWhen(this.bloom::getValue).advancedOnly()
   );
   public final NumberSetting chromatic = this.register(
      new NumberSetting("Chromatic", 0.5, 0.0, 1.0, 0.05, "").configKey("render.particles.chromatic").advancedOnly()
   );
   public final NumberSetting birthFlash = this.register(
      new NumberSetting("Birth Flash", 1.0, 0.0, 2.0, 0.05, "").configKey("render.particles.birthflash").advancedOnly()
   );
   public final NumberSetting gravity = this.register(
      new NumberSetting("Gravity", -0.3, -2.0, 2.0, 0.1, "").configKey("render.particles.gravity").advancedOnly()
   );
   public final NumberSetting bounciness = this.register(
      new NumberSetting("Bounciness", 0.55, 0.0, 1.0, 0.05, "").configKey("render.particles.bounciness").advancedOnly()
   );
   public final NumberSetting drag = this.register(new NumberSetting("Drag", 0.96, 0.85, 1.0, 0.01, "").configKey("render.particles.drag").advancedOnly());
   public final NumberSetting floorFriction = this.register(
      new NumberSetting("Floor Friction", 0.75, 0.0, 1.0, 0.05, "").configKey("render.particles.floorfriction").advancedOnly()
   );
   public final NumberSetting turbulence = this.register(
      new NumberSetting("Turbulence", 0.2, 0.0, 2.0, 0.1, "").configKey("render.particles.turbulence").advancedOnly()
   );
   public final NumberSetting spawnRate = this.register(
      new NumberSetting("Spawn Rate", 1.0, 1.0, 5.0, 1.0, " ticks")
         .configKey("render.particles.spawnrate")
         .visibleWhen(() -> this.workFrom.isSelected("World"))
         .advancedOnly()
   );
   public final NumberSetting worldRadius = this.register(
      new NumberSetting("Spawn Radius", 40.0, 10.0, 40.0, 1.0, " blocks")
         .configKey("render.particles.radius")
         .visibleWhen(() -> this.workFrom.isSelected("World"))
         .advancedOnly()
   );
   public final NumberSetting walkRate = this.register(
      new NumberSetting("Emit Rate", 8.0, 1.0, 30.0, 1.0, "/s")
         .configKey("render.particles.walkrate")
         .visibleWhen(() -> this.workFrom.isSelected("Walking"))
         .advancedOnly()
   );
   public final MultiSelectSetting itemTypes = this.register(
      new MultiSelectSetting(
            "Item Types", Set.of("Ender Pearl", "Trident"), "Ender Pearl", "Trident", "Snowball", "Egg", "Potions", "Arrows", "Fishing Bobber", "Dropped Items"
         )
         .configKey("render.particles.itemtypes")
         .visibleWhen(() -> this.workFrom.isSelected("Falling Items"))
   );
   public final NumberSetting itemRate = this.register(
      new NumberSetting("Emit Rate", 12.0, 1.0, 30.0, 1.0, "/s")
         .configKey("render.particles.itemrate")
         .visibleWhen(() -> this.workFrom.isSelected("Falling Items"))
         .advancedOnly()
   );
   private final List<ProceduralParticleState> particles = new ArrayList<>();
   private final List<ProceduralParticleState> itemDropParticles = new ArrayList<>();
   private final List<ProceduralParticleRenderer.Sprite> sprites = new ArrayList<>();
   private final ProceduralParticleRenderer renderer = new ProceduralParticleRenderer();
   private final Random random = new Random();
   private final Map<Entity, Double> itemBudgets = new HashMap<>();
   private double walkBudget;
   private boolean wasOnGround = true;

   public ParticlesFeature() {
      super("Particles", "Spawns procedural particles from hits, the world, movement or thrown items", FeatureCategory.VISUAL, -1);
   }

   public static ParticlesFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(ParticlesFeature.class);
   }

   public void onAttack(Entity target) {
      if (this.workFrom.isSelected("Hit") && target instanceof LivingEntity living) {
         this.spawnHit(living);
      }
   }

   public void spawnCrystalExplosion(Vec3 center) {
      if (this.workFrom.isSelected("Crystal")) {
         int configuredLifetime = this.lifetime.getValue().intValue();

         for (int i = 0; i < 30; i++) {
            double angle = this.random.nextDouble() * 3.1415928936382223 * 2.0;
            double speed = 0.06 + this.random.nextDouble() * 0.22;
            Vec3 position = center.add((this.random.nextDouble() - 0.5) * 0.6, (this.random.nextDouble() - 0.5) * 0.4, (this.random.nextDouble() - 0.5) * 0.6);
            Vec3 velocity = new Vec3(Math.cos(angle) * speed, 0.12 + this.random.nextDouble() * 0.3, Math.sin(angle) * speed);
            this.spawnAt(position, velocity, configuredLifetime, ProceduralParticleState.Source.CRYSTAL);
         }
      }
   }

   public void spawnDeath(Vec3 position) {
      if (this.workFrom.isSelected("Death")) {
         int configuredLifetime = this.lifetime.getValue().intValue();

         for (int i = 0; i < 25; i++) {
            Vec3 offset = new Vec3((this.random.nextDouble() - 0.5) * 0.8, this.random.nextDouble() * 1.6, (this.random.nextDouble() - 0.5) * 0.8);
            Vec3 velocity = new Vec3((this.random.nextDouble() - 0.5) * 0.12, 0.05 + this.random.nextDouble() * 0.18, (this.random.nextDouble() - 0.5) * 0.12);
            int color = this.random.nextBoolean() ? -50373 : -1;
            this.itemDropParticles
               .add(
                  new ProceduralParticleState(
                     position.x + offset.x,
                     position.y + offset.y,
                     position.z + offset.z,
                     velocity.x,
                     velocity.y,
                     velocity.z,
                     configuredLifetime,
                     this.selectedShape(),
                     ProceduralParticleState.Source.DEATH,
                     this.random,
                     color
                  )
               );
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (mc.level != null && mc.player != null) {
         if (this.workFrom.isSelected("World")) {
            this.spawnWorldBatch();
         }

         if (this.workFrom.isSelected("Walking")) {
            this.tickWalking();
         }

         if (this.workFrom.isSelected("Falling Items")) {
            this.tickFallingItems();
         }

         if (!this.particles.isEmpty()) {
            float configuredSize = this.size.getValue().floatValue();
            float variation = this.sizeVariation.getValue().floatValue();
            ProceduralParticleState.Physics physics = this.currentPhysics();
            Iterator<ProceduralParticleState> iterator = this.particles.iterator();

            while (iterator.hasNext()) {
               if (!iterator.next().update(mc.level, configuredSize, variation, 1.0F, this.random, physics)) {
                  iterator.remove();
               }
            }
         }

         if (!this.itemDropParticles.isEmpty()) {
            float configuredSize = this.size.getValue().floatValue();
            float variation = this.sizeVariation.getValue().floatValue();
            ProceduralParticleState.Physics physics = this.currentPhysics();
            Iterator<ProceduralParticleState> iterator = this.itemDropParticles.iterator();

            while (iterator.hasNext()) {
               if (!iterator.next().update(mc.level, configuredSize, variation, 1.0F, this.random, physics)) {
                  iterator.remove();
               }
            }
         }
      } else {
         this.clear();
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.clear();
   }

   @EventTarget
   public void onEntityDeath(EntityDeathEvent event) {
      if (this.workFrom.isSelected("Death")) {
         this.spawnDeath(event.getEntity().position());
      }
   }

   @Override
   protected void onDisable() {
      this.clear();
      this.renderer.release();
   }

   private void clear() {
      this.particles.clear();
      this.itemDropParticles.clear();
      this.sprites.clear();
      this.walkBudget = 0.0;
      this.wasOnGround = true;
      this.itemBudgets.clear();
   }

   public void renderWorld(float tickDelta) {
      if (mc.player != null && mc.level != null) {
         if (!this.particles.isEmpty() || !this.itemDropParticles.isEmpty()) {
            int baseColor = ColorMode.resolve(this.colorMode, this.color);
            float configuredSize = this.size.getValue().floatValue();
            float variation = this.sizeVariation.getValue().floatValue();
            this.sprites.clear();

            for (ProceduralParticleState particle : this.particles) {
               this.sprites
                  .add(
                     new ProceduralParticleRenderer.Sprite(
                        particle.interpolatedPosition(tickDelta),
                        particle.renderSize(configuredSize, variation, tickDelta),
                        ColorUtil.multiplyAlpha(baseColor, particle.opacityEnvelope()),
                        particle.shape(),
                        particle.renderRotation(tickDelta),
                        particle.normalizedLife(),
                        particle.seed(),
                        particle.phase()
                     )
                  );
            }

            for (ProceduralParticleState particle : this.itemDropParticles) {
               this.sprites
                  .add(
                     new ProceduralParticleRenderer.Sprite(
                        particle.interpolatedPosition(tickDelta),
                        particle.renderSize(configuredSize, variation, tickDelta),
                        ColorUtil.multiplyAlpha(particle.tint(), particle.opacityEnvelope()),
                        particle.shape(),
                        particle.renderRotation(tickDelta),
                        particle.normalizedLife(),
                        particle.seed(),
                        particle.phase()
                     )
                  );
            }

            this.renderer.render(this.sprites, this.glow.getValue().floatValue(), false, this.currentBloom());
         }
      }
   }

   private ProceduralParticleRenderer.Bloom currentBloom() {
      float flash = this.birthFlash.getValue().floatValue();
      float chroma = this.chromatic.getValue().floatValue();
      return !this.bloom.getValue()
         ? new ProceduralParticleRenderer.Bloom(false, 0.0F, 1, 1.0F, 0.0F, flash, chroma)
         : new ProceduralParticleRenderer.Bloom(
            true,
            this.bloomIntensity.getValue().floatValue(),
            this.bloomRadius.getValue().intValue(),
            this.hdrBoost.getValue().floatValue(),
            this.anamorphic.getValue().floatValue(),
            flash,
            chroma
         );
   }

   private ProceduralParticleState.Physics currentPhysics() {
      return new ProceduralParticleState.Physics(
         this.gravity.getValue() * 0.01, this.bounciness.getValue(), this.drag.getValue(), this.floorFriction.getValue(), this.turbulence.getValue() * 0.01
      );
   }

   private void spawnHit(LivingEntity entity) {
      Vec3 base = entity.position();
      int configuredLifetime = this.lifetime.getValue().intValue();

      for (int index = 0; index < 15; index++) {
         Vec3 position = base.add(
            (this.random.nextDouble() - 0.5) * entity.getBbWidth(),
            this.random.nextDouble() * entity.getBbHeight(),
            (this.random.nextDouble() - 0.5) * entity.getBbWidth()
         );
         this.spawnAt(position, ProceduralParticleState.randomVelocity(this.random), configuredLifetime, ProceduralParticleState.Source.HIT);
      }
   }

   private void spawnWorldBatch() {
      Vec3 center = mc.player.position();
      double configuredRadius = this.worldRadius.getValue();
      int configuredLifetime = this.lifetime.getValue().intValue();

      for (int index = 0; index < 10; index++) {
         double angle = this.random.nextDouble() * 3.1415928936382223 * 2.0;
         double distance = this.random.nextDouble() * configuredRadius;
         Vec3 position = center.add(Math.cos(angle) * distance, (this.random.nextDouble() - 0.5) * distance, Math.sin(angle) * distance);
         if (!ProceduralParticleState.isPositionBlocked(mc.level, position)) {
            this.spawnAt(position, ProceduralParticleState.randomVelocity(this.random), configuredLifetime, ProceduralParticleState.Source.WORLD);
         }
      }
   }

   private void tickWalking() {
      boolean onGround = mc.player.onGround();
      Vec3 movement = mc.player.getDeltaMovement();
      boolean leftGround = this.wasOnGround && !onGround;
      boolean landed = !this.wasOnGround && onGround;
      this.wasOnGround = onGround;
      if (leftGround && movement.y > 0.0) {
         this.spawnFootParticles(6, 1.4, 0.6);
      }

      if (landed) {
         this.spawnFootParticles(10, 1.6, 0.15);
      }

      boolean moving = movement.horizontalDistance() > 0.004 || Math.abs(movement.y) > 0.004;
      if (!moving) {
         this.walkBudget = 0.0;
      } else {
         this.walkBudget = this.walkBudget + this.walkRate.getValue() / 20.0;
         int bursts = (int)this.walkBudget;
         this.walkBudget -= bursts;
         this.spawnFootParticles(bursts * 1, 1.0, 0.5);
      }
   }

   private void spawnFootParticles(int count, double spread, double velocityScale) {
      if (count > 0) {
         Vec3 feet = mc.player.position();
         int configuredLifetime = this.lifetime.getValue().intValue();
         double width = mc.player.getBbWidth() * 0.6 * spread;

         for (int index = 0; index < count; index++) {
            Vec3 position = feet.add((this.random.nextDouble() - 0.5) * width, 0.05, (this.random.nextDouble() - 0.5) * width);
            if (!ProceduralParticleState.isPositionBlocked(mc.level, position)) {
               Vec3 velocity = ProceduralParticleState.randomVelocity(this.random).scale(velocityScale);
               this.spawnAt(position, velocity, configuredLifetime, ProceduralParticleState.Source.WALK);
            }
         }
      }
   }

   private void tickFallingItems() {
      Set<Entity> tracked = this.collectTrackedItems();
      this.itemBudgets.keySet().retainAll(tracked);
      if (!tracked.isEmpty()) {
         int configuredLifetime = this.lifetime.getValue().intValue();
         double perTick = this.itemRate.getValue() / 20.0;

         for (Entity entity : tracked) {
            if (!(entity.getDeltaMovement().lengthSqr() < 1.0E-5)) {
               boolean firstSeen = !this.itemBudgets.containsKey(entity);
               double budget = this.itemBudgets.getOrDefault(entity, 0.0) + perTick + (firstSeen ? 1.0 : 0.0);
               int toSpawn = (int)budget;
               this.itemBudgets.put(entity, budget - toSpawn);

               for (int index = 0; index < toSpawn; index++) {
                  Vec3 position = entity.position()
                     .add((this.random.nextDouble() - 0.5) * 0.1, (this.random.nextDouble() - 0.5) * 0.1, (this.random.nextDouble() - 0.5) * 0.1);
                  this.spawnAt(position, ProceduralParticleState.randomVelocity(this.random), configuredLifetime, ProceduralParticleState.Source.ITEM);
               }
            }
         }
      }
   }

   private Set<Entity> collectTrackedItems() {
      Set<Entity> tracked = new LinkedHashSet<>();

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (tracked.size() >= 32) {
            break;
         }

         if (this.matchesTrackedItem(entity)) {
            tracked.add(entity);
         }
      }

      return tracked;
   }

   private boolean matchesTrackedItem(Entity entity) {
      if (entity instanceof ThrownTrident) {
         return this.itemTypes.isSelected("Trident");
      } else if (entity instanceof ThrownEnderpearl) {
         return this.itemTypes.isSelected("Ender Pearl");
      } else if (entity instanceof Snowball) {
         return this.itemTypes.isSelected("Snowball");
      } else if (entity instanceof ThrownEgg) {
         return this.itemTypes.isSelected("Egg");
      } else if (entity instanceof AbstractThrownPotion) {
         return this.itemTypes.isSelected("Potions");
      } else if (entity instanceof AbstractArrow) {
         return this.itemTypes.isSelected("Arrows");
      } else if (entity instanceof FishingHook) {
         return this.itemTypes.isSelected("Fishing Bobber");
      } else {
         return entity instanceof ItemEntity ? this.itemTypes.isSelected("Dropped Items") : false;
      }
   }

   private void spawnAt(Vec3 position, Vec3 velocity, int lifetime, ProceduralParticleState.Source source) {
      this.particles
         .add(
            new ProceduralParticleState(
               position.x, position.y, position.z, velocity.x, velocity.y, velocity.z, lifetime, this.selectedShape(), source, this.random
            )
         );
   }

   private ProceduralParticleRenderer.Shape selectedShape() {
      return ProceduralParticleRenderer.Shape.fromDisplayName(this.shape.getValue()).resolve(this.random);
   }
}
