package win.furryhook.utils.math;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.mixin.accessor.LivingEntityAccessor;

public final class TickSimulator {
   private static final double COLLISION_EPSILON = 1.0E-7;
   private static final float DEFAULT_FRICTION = 0.6F;
   private static final double BELOW_OFFSET = 0.5000001;

   private TickSimulator() {
   }

   public static void simulateNextTick(TickSimulator.SimState state, Level level) {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(level, "level");
      if (state.noJumpDelay > 0) {
         state.noJumpDelay--;
      }

      updateFluidState(state, level);
      clampSmallMovement(state);
      tickJump(state, level);
      if (hasEffect(state, MobEffects.SLOW_FALLING) || hasEffect(state, MobEffects.LEVITATION)) {
         state.fallDistance = 0.0F;
      }

      boolean useFluidTravel = (state.inWater || state.inLava) && !state.flying && !canStandOnFluid(state);
      if (useFluidTravel) {
         if (state.inWater) {
            travelInWater(state, level);
         } else {
            travelInLava(state, level);
         }
      } else {
         travelInAir(state, level);
      }
   }

   public static TickSimulator.SimState getPredictedState(LivingEntity entity, int ticks, Level level) {
      if (ticks < 0) {
         throw new IllegalArgumentException("Prediction tick count cannot be negative");
      }

      Objects.requireNonNull(level, "level");
      TickSimulator.SimState state = new TickSimulator.SimState(entity);

      for (int i = 0; i < ticks; i++) {
         simulateNextTick(state, level);
      }

      return state;
   }

   public static TickSimulator.SimState simulateLocalPlayer(LocalPlayer player, int ticks, Level level) {
      if (ticks < 0) {
         throw new IllegalArgumentException("Prediction tick count cannot be negative");
      }

      Objects.requireNonNull(level, "level");
      TickSimulator.SimState state = new TickSimulator.SimState(player).withLocalInput(player);

      for (int i = 0; i < ticks; i++) {
         simulateNextTick(state, level);
      }

      return state;
   }

   public static int ticksUntilCriticalWindow(LivingEntity entity, int maxTicks, Level level) {
      TickSimulator.SimState state = entity instanceof LocalPlayer player
         ? new TickSimulator.SimState(player).withLocalInput(player)
         : new TickSimulator.SimState(entity);
      if (!state.mobilityRestricted && !state.passenger) {
         for (int tick = 0; tick <= maxTicks; tick++) {
            if (state.isFallingCriticalWindow()) {
               return tick;
            }

            if (tick < maxTicks) {
               simulateNextTick(state, level);
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private static void clampSmallMovement(TickSimulator.SimState state) {
      double dx = state.motion.x;
      double dy = state.motion.y;
      double dz = state.motion.z;
      if (state.source instanceof Player) {
         if (state.motion.horizontalDistanceSqr() < 9.0E-6) {
            dx = 0.0;
            dz = 0.0;
         }
      } else {
         if (Math.abs(dx) < 0.003) {
            dx = 0.0;
         }

         if (Math.abs(dz) < 0.003) {
            dz = 0.0;
         }
      }

      if (Math.abs(dy) < 0.003) {
         dy = 0.0;
      }

      state.motion = new Vec3(dx, dy, dz);
   }

   private static void tickJump(TickSimulator.SimState state, Level level) {
      if (!state.jumpHeld) {
         state.noJumpDelay = 0;
      } else {
         double fluidHeight = state.inLava ? state.lavaHeight : state.waterHeight;
         boolean inWaterWithHeight = state.inWater && fluidHeight > 0.0;
         double jumpThreshold = fluidJumpThreshold(state);
         if (!inWaterWithHeight || state.onGround && !(fluidHeight > jumpThreshold)) {
            if (!state.inLava || state.onGround && !(fluidHeight > jumpThreshold)) {
               if ((state.onGround || inWaterWithHeight && fluidHeight <= jumpThreshold) && state.noJumpDelay == 0) {
                  jumpFromGround(state, level);
                  state.noJumpDelay = 10;
               }
            } else {
               state.motion = state.motion.add(0.0, 0.04, 0.0);
            }
         } else {
            state.motion = state.motion.add(0.0, 0.04, 0.0);
         }
      }
   }

   private static void jumpFromGround(TickSimulator.SimState state, Level level) {
      float jumpPower = (float)attribute(state, Attributes.JUMP_STRENGTH) * blockJumpFactor(state, level) + jumpBoostPower(state);
      if (!(jumpPower <= 1.0E-5F)) {
         state.motion = new Vec3(state.motion.x, Math.max(jumpPower, state.motion.y), state.motion.z);
         if (state.isSprinting) {
            float angle = state.yaw * (float) (Math.PI / 180.0);
            state.motion = state.motion.add(-Mth.sin(angle) * 0.2, 0.0, Mth.cos(angle) * 0.2);
         }

         state.onGround = false;
      }
   }

   private static void travelInAir(TickSimulator.SimState state, Level level) {
      BlockPos posBelow = blockPosBelow(state);
      float blockFriction = state.onGround
         ? modifiedFriction(level.getBlockState(posBelow).getBlock().getFriction(), (float)attribute(state, Attributes.FRICTION_MODIFIER))
         : 1.0F;
      moveRelative(state, frictionInfluencedSpeed(state, blockFriction));
      state.motion = handleOnClimbable(state, level);
      move(state, level);
      Vec3 movement = state.motion;
      if ((state.horizontalCollision || state.jumpHeld) && state.climbing) {
         movement = new Vec3(movement.x, 0.2, movement.z);
      }

      double movementY = movement.y;
      MobEffectInstance levitation = effect(state, MobEffects.LEVITATION);
      if (levitation != null) {
         movementY += (0.05 * (levitation.getAmplifier() + 1) - movement.y) * 0.2;
         state.fallDistance = 0.0F;
      } else {
         movementY -= effectiveGravity(state, movement.y);
      }

      float airDragModifier = (float)attribute(state, Attributes.AIR_DRAG_MODIFIER);
      float airDrag = modifiedFriction(0.91F, airDragModifier);
      float friction = blockFriction * airDrag;
      float verticalDrag = modifiedFriction(0.98F, airDragModifier);
      state.motion = new Vec3(movement.x * friction, movementY * verticalDrag, movement.z * friction);
   }

   private static void travelInWater(TickSimulator.SimState state, Level level) {
      boolean isFalling = state.motion.y <= 0.0;
      double oldY = state.pos.y;
      double gravity = effectiveGravity(state, state.motion.y);
      float slowDown = state.isSprinting ? 0.9F : 0.8F;
      float speed = 0.02F;
      float waterEfficiency = (float)attribute(state, Attributes.WATER_MOVEMENT_EFFICIENCY);
      if (!state.onGround) {
         waterEfficiency *= 0.5F;
      }

      if (waterEfficiency > 0.0F) {
         slowDown += (0.54600006F - slowDown) * waterEfficiency;
         speed += (speedAttribute(state) - speed) * waterEfficiency;
      }

      if (hasEffect(state, MobEffects.DOLPHINS_GRACE)) {
         slowDown = 0.96F;
      }

      moveRelative(state, speed);
      move(state, level);
      Vec3 movement = state.motion;
      if (state.horizontalCollision && state.climbing) {
         movement = new Vec3(movement.x, 0.2, movement.z);
      }

      movement = movement.multiply(slowDown, 0.8, slowDown);
      state.motion = fluidFallingAdjustedMovement(state, gravity, isFalling, movement);
      jumpOutOfFluid(state, level, oldY);
   }

   private static void travelInLava(TickSimulator.SimState state, Level level) {
      boolean isFalling = state.motion.y <= 0.0;
      double oldY = state.pos.y;
      double gravity = effectiveGravity(state, state.motion.y);
      moveRelative(state, 0.02F);
      move(state, level);
      if (state.lavaHeight <= fluidJumpThreshold(state)) {
         state.motion = state.motion.multiply(0.5, 0.8, 0.5);
         state.motion = fluidFallingAdjustedMovement(state, gravity, isFalling, state.motion);
      } else {
         state.motion = state.motion.scale(0.5);
      }

      if (gravity != 0.0) {
         state.motion = state.motion.add(0.0, -gravity / 4.0, 0.0);
      }

      jumpOutOfFluid(state, level, oldY);
   }

   private static void move(TickSimulator.SimState state, Level level) {
      Vec3 delta = state.motion;
      if (state.stuckSpeedMultiplier.lengthSqr() > 1.0E-7) {
         delta = delta.multiply(state.stuckSpeedMultiplier);
         state.stuckSpeedMultiplier = Vec3.ZERO;
         state.motion = Vec3.ZERO;
      }

      Vec3 resolved = Entity.collideBoundingBox(state.source, delta, state.boundingBox, level, List.of());
      boolean collidedX = differs(delta.x, resolved.x);
      boolean collidedY = differs(delta.y, resolved.y);
      boolean collidedZ = differs(delta.z, resolved.z);
      state.horizontalCollision = collidedX || collidedZ;
      state.verticalCollision = collidedY;
      state.verticalCollisionBelow = collidedY && delta.y < 0.0;
      state.onGround = state.verticalCollisionBelow;
      state.boundingBox = state.boundingBox.move(resolved);
      state.pos = state.pos.add(resolved);
      if (!state.inWater && resolved.y < 0.0) {
         state.fallDistance = state.fallDistance + (float)(-resolved.y);
      }

      if (state.onGround) {
         state.fallDistance = 0.0F;
      }

      state.motion = new Vec3(collidedX ? 0.0 : delta.x, collidedY ? 0.0 : delta.y, collidedZ ? 0.0 : delta.z);
      float speedFactor = blockSpeedFactor(state, level);
      state.motion = state.motion.multiply(speedFactor, 1.0, speedFactor);
      applyStuckBlocks(state, level);
      state.climbing = isClimbing(state, level);
   }

   private static void applyStuckBlocks(TickSimulator.SimState state, Level level) {
      state.inCobweb = false;
      AABB box = state.boundingBox.deflate(1.0E-7);
      int minX = Mth.floor(box.minX);
      int maxX = Mth.floor(box.maxX);
      int minY = Mth.floor(box.minY);
      int maxY = Mth.floor(box.maxY);
      int minZ = Mth.floor(box.minZ);
      int maxZ = Mth.floor(box.maxZ);
      MutableBlockPos cursor = new MutableBlockPos();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cursor.set(x, y, z);
               BlockState blockState = level.getBlockState(cursor);
               if (blockState.is(Blocks.COBWEB)) {
                  state.inCobweb = true;
                  state.stuckSpeedMultiplier = new Vec3(0.25, 0.05, 0.25);
                  state.fallDistance = 0.0F;
               } else if (blockState.is(Blocks.POWDER_SNOW)) {
                  state.stuckSpeedMultiplier = new Vec3(0.9, 1.5, 0.9);
                  state.fallDistance = 0.0F;
               } else if (blockState.is(Blocks.SWEET_BERRY_BUSH)) {
                  state.stuckSpeedMultiplier = new Vec3(0.8, 0.75, 0.8);
                  state.fallDistance = 0.0F;
               }
            }
         }
      }
   }

   private static void updateFluidState(TickSimulator.SimState state, Level level) {
      AABB box = state.boundingBox.deflate(0.001);
      state.waterHeight = fluidHeight(level, box, FluidTags.WATER);
      state.lavaHeight = fluidHeight(level, box, FluidTags.LAVA);
      state.inWater = state.waterHeight > 0.0;
      state.inLava = state.lavaHeight > 0.0;
      double eyeY = state.pos.y + state.source.getEyeHeight();
      state.submergedInWater = state.inWater && box.minY + state.waterHeight > eyeY;
      if (state.inWater) {
         state.fallDistance = 0.0F;
      }
   }

   private static double fluidHeight(Level level, AABB box, TagKey<Fluid> tag) {
      int minX = Mth.floor(box.minX);
      int maxX = Mth.ceil(box.maxX);
      int minY = Mth.floor(box.minY);
      int maxY = Mth.ceil(box.maxY);
      int minZ = Mth.floor(box.minZ);
      int maxZ = Mth.ceil(box.maxZ);
      double highest = 0.0;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int x = minX; x < maxX; x++) {
         for (int y = minY; y < maxY; y++) {
            for (int z = minZ; z < maxZ; z++) {
               cursor.set(x, y, z);
               FluidState fluid = level.getFluidState(cursor);
               if (fluid.is(tag)) {
                  double surface = y + fluid.getHeight(level, cursor);
                  if (surface >= box.minY) {
                     highest = Math.max(highest, surface - box.minY);
                  }
               }
            }
         }
      }

      return highest;
   }

   private static Vec3 handleOnClimbable(TickSimulator.SimState state, Level level) {
      Vec3 delta = state.motion;
      if (!state.climbing) {
         return delta;
      }

      state.fallDistance = 0.0F;
      double xd = Mth.clamp(delta.x, -0.15, 0.15);
      double zd = Mth.clamp(delta.z, -0.15, 0.15);
      double yd = Math.max(delta.y, -0.15);
      if (yd < 0.0
         && state.source instanceof Player player
         && player.isSuppressingSlidingDownLadder()
         && !level.getBlockState(BlockPos.containing(state.pos)).is(Blocks.SCAFFOLDING)) {
         yd = 0.0;
      }

      return new Vec3(xd, yd, zd);
   }

   private static void jumpOutOfFluid(TickSimulator.SimState state, Level level, double oldY) {
      if (state.horizontalCollision) {
         Vec3 movement = state.motion;
         Vec3 climbStep = new Vec3(movement.x, movement.y + 0.6 - state.pos.y + oldY, movement.z);
         Vec3 resolved = Entity.collideBoundingBox(state.source, climbStep, state.boundingBox, level, List.of());
         if (resolved.equals(climbStep)) {
            state.motion = new Vec3(movement.x, 0.3, movement.z);
         }
      }
   }

   private static Vec3 fluidFallingAdjustedMovement(TickSimulator.SimState state, double gravity, boolean isFalling, Vec3 movement) {
      if (gravity != 0.0 && !state.isSprinting) {
         double yd;
         if (isFalling && Math.abs(movement.y - 0.005) >= 0.003 && Math.abs(movement.y - gravity / 16.0) < 0.003) {
            yd = -0.003;
         } else {
            yd = movement.y - gravity / 16.0;
         }

         return new Vec3(movement.x, yd, movement.z);
      } else {
         return movement;
      }
   }

   private static void moveRelative(TickSimulator.SimState state, float speed) {
      Vec3 input = new Vec3(state.impulseX, 0.0, state.impulseZ);
      double lengthSqr = input.lengthSqr();
      if (!(lengthSqr < 1.0E-7)) {
         Vec3 scaled = (lengthSqr > 1.0 ? input.normalize() : input).scale(speed);
         float sin = Mth.sin(state.yaw * (float) (Math.PI / 180.0));
         float cos = Mth.cos(state.yaw * (float) (Math.PI / 180.0));
         state.motion = state.motion.add(scaled.x * cos - scaled.z * sin, scaled.y, scaled.z * cos + scaled.x * sin);
      }
   }

   private static float frictionInfluencedSpeed(TickSimulator.SimState state, float blockFriction) {
      if (state.onGround) {
         float speed = speedAttribute(state);
         return blockFriction > 0.6F ? speed * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : speed;
      } else {
         return state.isSprinting ? 0.025999999F : 0.02F;
      }
   }

   private static float speedAttribute(TickSimulator.SimState state) {
      return (float)attribute(state, Attributes.MOVEMENT_SPEED);
   }

   private static double effectiveGravity(TickSimulator.SimState state, double motionY) {
      double gravity = attribute(state, Attributes.GRAVITY);
      boolean isFalling = motionY <= 0.0;
      if (isFalling && hasEffect(state, MobEffects.SLOW_FALLING)) {
         state.fallDistance = 0.0F;
         return Math.min(gravity, 0.01);
      } else {
         return gravity;
      }
   }

   private static float blockSpeedFactor(TickSimulator.SimState state, Level level) {
      if (state.flying) {
         return 1.0F;
      }

      BlockState inState = level.getBlockState(BlockPos.containing(state.pos));
      float factor = inState.getBlock().getSpeedFactor();
      if (!inState.is(Blocks.WATER) && !inState.is(Blocks.BUBBLE_COLUMN) && factor == 1.0F) {
         factor = level.getBlockState(blockPosBelow(state)).getBlock().getSpeedFactor();
      }

      float efficiency = (float)attribute(state, Attributes.MOVEMENT_EFFICIENCY);
      return Mth.lerp(efficiency, factor, 1.0F);
   }

   private static float blockJumpFactor(TickSimulator.SimState state, Level level) {
      float inFactor = level.getBlockState(BlockPos.containing(state.pos)).getBlock().getJumpFactor();
      return inFactor == 1.0F ? level.getBlockState(blockPosBelow(state)).getBlock().getJumpFactor() : inFactor;
   }

   private static float jumpBoostPower(TickSimulator.SimState state) {
      MobEffectInstance jumpBoost = effect(state, MobEffects.JUMP_BOOST);
      return jumpBoost == null ? 0.0F : 0.1F * (jumpBoost.getAmplifier() + 1.0F);
   }

   private static boolean isClimbing(TickSimulator.SimState state, Level level) {
      BlockState inState = level.getBlockState(BlockPos.containing(state.pos));
      return inState.is(BlockTags.CLIMBABLE);
   }

   private static boolean canStandOnFluid(TickSimulator.SimState state) {
      return false;
   }

   private static double fluidJumpThreshold(TickSimulator.SimState state) {
      return state.source.getEyeHeight() < 0.4 ? 0.0 : 0.4;
   }

   private static BlockPos blockPosBelow(TickSimulator.SimState state) {
      return BlockPos.containing(state.pos.x, state.boundingBox.minY - 0.5000001, state.pos.z);
   }

   private static float modifiedFriction(float friction, float modifier) {
      return Mth.clamp(1.0F - (1.0F - friction) * modifier, 0.0F, 1.0F);
   }

   private static double attribute(TickSimulator.SimState state, Holder<Attribute> attribute) {
      return state.source.getAttributeValue(attribute);
   }

   private static MobEffectInstance effect(TickSimulator.SimState state, Holder<MobEffect> effect) {
      return state.source.getEffect(effect);
   }

   private static boolean hasEffect(TickSimulator.SimState state, Holder<MobEffect> effect) {
      return state.source.hasEffect(effect);
   }

   private static float itemUseSpeedMultiplier(LocalPlayer player) {
      UseEffects useEffects = (UseEffects)player.getUseItem().get(DataComponents.USE_EFFECTS);
      return useEffects == null ? 1.0F : useEffects.speedMultiplier();
   }

   private static boolean differs(double requested, double resolved) {
      return Math.abs(requested - resolved) > 1.0E-7;
   }

   public static final class SimState {
      public final LivingEntity source;
      public Vec3 pos;
      public Vec3 motion;
      public AABB boundingBox;
      public boolean onGround;
      public boolean horizontalCollision;
      public boolean verticalCollision;
      public boolean verticalCollisionBelow;
      public float fallDistance;
      public boolean isSprinting;
      public boolean inWater;
      public boolean submergedInWater;
      public boolean swimming;
      public boolean inLava;
      public boolean climbing;
      public boolean inCobweb;
      public boolean mobilityRestricted;
      public boolean passenger;
      public boolean flying;
      public int noJumpDelay;
      public Vec3 stuckSpeedMultiplier = Vec3.ZERO;
      public double waterHeight;
      public double lavaHeight;
      public float impulseX;
      public float impulseZ;
      public boolean jumpHeld;
      public float yaw;

      public SimState(LivingEntity entity) {
         this.source = Objects.requireNonNull(entity, "entity");
         this.pos = entity.position();
         this.motion = entity.getDeltaMovement();
         this.boundingBox = entity.getBoundingBox();
         this.onGround = entity.onGround();
         this.horizontalCollision = entity.horizontalCollision;
         this.verticalCollision = entity.verticalCollision;
         this.verticalCollisionBelow = entity.verticalCollisionBelow;
         this.fallDistance = (float)entity.fallDistance;
         this.isSprinting = entity.isSprinting();
         this.inWater = entity.isInWater();
         this.submergedInWater = entity.isUnderWater();
         this.swimming = entity.isSwimming();
         this.inLava = entity.isInLava();
         this.climbing = entity.onClimbable();
         this.mobilityRestricted = entity instanceof Player player && player.isMobilityRestricted();
         this.passenger = entity.isPassenger();
         this.flying = entity instanceof Player player && player.getAbilities().flying;
         this.yaw = entity.getYRot();
         this.waterHeight = entity.getFluidHeight(FluidTags.WATER);
         this.lavaHeight = entity.getFluidHeight(FluidTags.LAVA);
         this.noJumpDelay = ((LivingEntityAccessor)entity).getNoJumpDelay();
         if (entity instanceof LocalPlayer player && player.input != null) {
            this.jumpHeld = player.input.keyPresses.jump();
         }

         TickSimulator.applyStuckBlocks(this, entity.level());
      }

      public TickSimulator.SimState withLocalInput(LocalPlayer player) {
         if (player.input != null) {
            float scale = 0.98F;
            if (player.isUsingItem() && !player.isPassenger()) {
               scale *= TickSimulator.itemUseSpeedMultiplier(player);
            }

            if (player.isMovingSlowly()) {
               scale *= (float)player.getAttributeValue(Attributes.SNEAKING_SPEED);
            }

            float inputX = player.input.getMoveVector().x * scale;
            float inputZ = player.input.getMoveVector().y * scale;
            float length = (float)Math.sqrt(inputX * inputX + inputZ * inputZ);
            if (length > 0.0F) {
               float absX = Math.abs(inputX / length);
               float absZ = Math.abs(inputZ / length);
               float tangent = absZ > absX ? absX / absZ : absZ / absX;
               float toUnitSquare = (float)Math.sqrt(1.0F + tangent * tangent);
               float modified = Math.min(length * toUnitSquare, 1.0F) / length;
               inputX *= modified;
               inputZ *= modified;
            }

            this.impulseX = inputX;
            this.impulseZ = inputZ;
            this.jumpHeld = player.input.keyPresses.jump();
         }

         return this;
      }

      public boolean isFallingCriticalWindow() {
         return this.fallDistance > 0.0F && !this.onGround && !this.inWater && !this.climbing && !this.mobilityRestricted && !this.passenger;
      }
   }
}
