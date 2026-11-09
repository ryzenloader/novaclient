package win.furryhook.utils.render.particles;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class ProceduralParticleState {
   private static final double PI = 3.141593430080217;
   private static final double Y_SLEEP_THRESHOLD = 0.010000003725784143;
   private static final double OTHER_AXIS_DAMPING = 0.8999997820741558;
   private static final double COLLISION_EPSILON_POSITIVE = 9.99999563509513E-5;
   private static final double COLLISION_EPSILON_NEGATIVE = -9.999997135847346E-5;
   private static final double VELOCITY_EPSILON = 1.0000000000249859E-5;
   private static final float ROTATION_DAMPING = 0.985F;
   private static final float COLLISION_SPIN = 0.3F;
   private static final float FADE_IN_END = 0.2F;
   private static final float FADE_OUT_START = 0.8F;
   private static final double SPAWN_VELOCITY = 0.2000000000678856;
   private static final float GROW_END = 0.06F;
   private static final float SHRINK_START = 0.7F;
   private static final float SHRINK_DEPTH = 0.85F;
   private static final float COLLISION_SCALE = 0.45F;
   private double previousX;
   private double previousY;
   private double previousZ;
   private double x;
   private double y;
   private double z;
   private double velocityX;
   private double velocityY;
   private double velocityZ;
   private final int lifetime;
   private final ProceduralParticleRenderer.Shape shape;
   private final ProceduralParticleState.Source source;
   private float age;
   private float rotation;
   private float rotationVelocity;
   private final float seed;
   private final float phase;
   private final float sizeJitter;
   private final int tint;

   public ProceduralParticleState(
      double x,
      double y,
      double z,
      double velocityX,
      double velocityY,
      double velocityZ,
      int lifetime,
      ProceduralParticleRenderer.Shape shape,
      ProceduralParticleState.Source source,
      Random random
   ) {
      this(x, y, z, velocityX, velocityY, velocityZ, lifetime, shape, source, random, -1);
   }

   public ProceduralParticleState(
      double x,
      double y,
      double z,
      double velocityX,
      double velocityY,
      double velocityZ,
      int lifetime,
      ProceduralParticleRenderer.Shape shape,
      ProceduralParticleState.Source source,
      Random random,
      int tint
   ) {
      this.previousX = this.x = x;
      this.previousY = this.y = y;
      this.previousZ = this.z = z;
      this.velocityX = velocityX;
      this.velocityY = velocityY;
      this.velocityZ = velocityZ;
      this.lifetime = lifetime;
      this.shape = shape;
      this.source = source;
      this.rotation = (float)(random.nextDouble() * 3.141593430080217 * 2.0);
      this.rotationVelocity = (float)(random.nextDouble() - 0.5) * 0.2F;
      this.seed = random.nextFloat();
      this.phase = random.nextFloat() * 100.0F;
      this.sizeJitter = random.nextFloat() * 2.0F - 1.0F;
      this.tint = tint;
   }

   public int tint() {
      return this.tint;
   }

   public static Vec3 randomVelocity(Random random) {
      return new Vec3(
         (random.nextDouble() - 0.5) * 0.2000000000678856, (random.nextDouble() - 0.5) * 0.2000000000678856, (random.nextDouble() - 0.5) * 0.2000000000678856
      );
   }

   public static boolean isPositionBlocked(Level level, Vec3 position) {
      BlockPos blockPos = BlockPos.containing(position);
      BlockState state = level.getBlockState(blockPos);
      return !state.isAir() && !state.getCollisionShape(level, blockPos).isEmpty();
   }

   public boolean update(Level level, float configuredSize, float sizeVariation, float delta, Random random, ProceduralParticleState.Physics physics) {
      this.age += delta;
      if (this.age >= this.lifetime) {
         return false;
      }

      float collisionSize = Math.max(0.005F, this.baseSize(configuredSize, sizeVariation) * 0.45F);
      this.previousX = this.x;
      this.previousY = this.y;
      this.previousZ = this.z;
      this.rotation = this.rotation + this.rotationVelocity * delta;
      this.rotationVelocity *= 0.985F;
      double gravity = physics.gravity();
      double turbulence = physics.turbulence();
      if (this.source == ProceduralParticleState.Source.CRYSTAL) {
         gravity = 0.08;
         turbulence = 0.0;
      }

      this.velocityX = this.velocityX + (random.nextDouble() - 0.5) * turbulence * delta;
      this.velocityZ = this.velocityZ + (random.nextDouble() - 0.5) * turbulence * delta;
      double drag = Math.pow(physics.drag(), delta);
      this.velocityX *= drag;
      this.velocityY = this.velocityY * drag - gravity * delta;
      this.velocityZ *= drag;
      if (Math.abs(this.velocityY) < 0.010000003725784143) {
         this.velocityY = 0.0;
      }

      double requestedX = this.velocityX * delta;
      double requestedY = this.velocityY * delta;
      double requestedZ = this.velocityZ * delta;
      AABB box = new AABB(
         this.x - collisionSize, this.y - collisionSize, this.z - collisionSize, this.x + collisionSize, this.y + collisionSize, this.z + collisionSize
      );
      double clippedY = clipAxis(level, box, requestedY, Axis.Y);
      if (clippedY != requestedY) {
         this.velocityY = -this.velocityY * physics.bounciness();
         this.rotationVelocity = (float)(random.nextDouble() - 0.5) * 0.3F;
         if (requestedY < 0.0) {
            this.velocityX = this.velocityX * physics.floorFriction();
            this.velocityZ = this.velocityZ * physics.floorFriction();
         }

         clippedY = signedCollisionEpsilon(clippedY, requestedY);
      }

      this.y += clippedY;
      box = box.move(0.0, clippedY, 0.0);
      double clippedX = clipAxis(level, box, requestedX, Axis.X);
      if (clippedX != requestedX) {
         this.velocityX = -this.velocityX * physics.bounciness();
         this.velocityY *= 0.8999997820741558;
         this.velocityZ *= 0.8999997820741558;
         clippedX = signedCollisionEpsilon(clippedX, requestedX);
      }

      this.x += clippedX;
      box = box.move(clippedX, 0.0, 0.0);
      double clippedZ = clipAxis(level, box, requestedZ, Axis.Z);
      if (clippedZ != requestedZ) {
         this.velocityZ = -this.velocityZ * physics.bounciness();
         this.velocityY *= 0.8999997820741558;
         this.velocityX *= 0.8999997820741558;
         clippedZ = signedCollisionEpsilon(clippedZ, requestedZ);
      }

      this.z += clippedZ;
      if (Math.abs(this.velocityX) < 1.0000000000249859E-5) {
         this.velocityX = 0.0;
      }

      if (Math.abs(this.velocityY) < 1.0000000000249859E-5) {
         this.velocityY = 0.0;
      }

      if (Math.abs(this.velocityZ) < 1.0000000000249859E-5) {
         this.velocityZ = 0.0;
      }

      return true;
   }

   public Vec3 interpolatedPosition(float tickDelta) {
      return new Vec3(
         this.previousX + (this.x - this.previousX) * tickDelta,
         this.previousY + (this.y - this.previousY) * tickDelta,
         this.previousZ + (this.z - this.previousZ) * tickDelta
      );
   }

   public float renderRotation(float tickDelta) {
      return this.rotation + this.rotationVelocity * tickDelta;
   }

   public float normalizedLife() {
      return Math.clamp(this.age / this.lifetime, 0.0F, 1.0F);
   }

   public float normalizedLife(float tickDelta) {
      return Math.clamp((this.age + tickDelta) / this.lifetime, 0.0F, 1.0F);
   }

   public float baseSize(float configuredSize, float sizeVariation) {
      float variation = Math.clamp(sizeVariation, 0.0F, 1.0F);
      return Math.max(0.001F, configuredSize * (1.0F + this.sizeJitter * variation));
   }

   public float renderSize(float configuredSize, float sizeVariation, float tickDelta) {
      float progress = this.normalizedLife(tickDelta);
      float growth = progress >= 0.06F ? 1.0F : smoothstep(progress / 0.06F);
      float shrink = progress <= 0.7F ? 1.0F : 1.0F - 0.85F * smoothstep((progress - 0.7F) / 0.3F);
      return this.baseSize(configuredSize, sizeVariation) * growth * shrink;
   }

   private static float smoothstep(float value) {
      float clamped = Math.clamp(value, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   public float opacityEnvelope() {
      float progress = this.normalizedLife();
      if (progress < 0.2F) {
         return progress / 0.2F;
      } else {
         return progress > 0.8F ? (1.0F - progress) / 0.2F : 1.0F;
      }
   }

   public ProceduralParticleRenderer.Shape shape() {
      return this.shape;
   }

   public ProceduralParticleState.Source source() {
      return this.source;
   }

   public float seed() {
      return this.seed;
   }

   public float phase() {
      return this.phase;
   }

   private static double clipAxis(Level level, AABB box, double movement, Axis axis) {
      if (movement == 0.0) {
         return 0.0;
      }

      AABB expanded = switch (axis) {
         case X -> box.expandTowards(movement, 0.0, 0.0);
         case Y -> box.expandTowards(0.0, movement, 0.0);
         case Z -> box.expandTowards(0.0, 0.0, movement);
         default -> throw new MatchException(null, null);
      };
      return Shapes.collide(axis, box, level.getBlockCollisions(null, expanded), movement);
   }

   private static double signedCollisionEpsilon(double clipped, double requested) {
      if (Math.abs(clipped) >= 9.99999563509513E-5) {
         return clipped;
      } else {
         return requested < 0.0 ? 9.99999563509513E-5 : -9.999997135847346E-5;
      }
   }

   public record Physics(double gravity, double bounciness, double drag, double floorFriction, double turbulence) {
   }

   public enum Source {
      HIT,
      WORLD,
      WALK,
      ITEM,
      CRYSTAL,
      DEATH;
   }
}
