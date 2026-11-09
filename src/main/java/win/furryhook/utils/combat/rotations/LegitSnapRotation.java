package win.furryhook.utils.combat.rotations;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import win.furryhook.context.RotationContext;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.movement.AutoJumpFeature;
import win.furryhook.utils.math.TickSimulator;

public final class LegitSnapRotation implements AuraRotation {
   private static final double CANDIDATE_DIVISOR = 10.0;
   private static final float RELEASE_ANGLE = 2.0F;
   private static final float DEFAULT_FOV_ANGLE = 25.0F;
   private static final float FOV_DISABLED_AT = 90.0F;
   private static final int ATTACK_HOLD_TICKS = 4;
   private final SecureRandom secureRandom = new SecureRandom();
   private LegitSnapRotation.Mode mode = LegitSnapRotation.Mode.FUNTIME;
   private float fovAngle = 25.0F;
   private boolean fovDisabled;
   private double reach = 3.0;
   private boolean throughWalls;
   private int targetId = Integer.MIN_VALUE;
   private boolean engaged;
   private boolean settled;
   private boolean targetInFov;
   private int attackHold;
   private boolean owning;
   private int tickCounter;
   private long lastAttackMillis = -2305843009213693952L;
   private int hitCounter;
   private boolean onlyCriticalHit;
   private boolean critsWithSpace;
   private int attackCooldown;
   private final boolean[] collisionHistory = new boolean[8];
   private int collisionCursor;
   private int collisionSamples;

   public void setFovAngle(float fovAngle) {
      this.fovAngle = Math.max(1.0F, fovAngle);
      this.fovDisabled = fovAngle >= 90.0F;
   }

   public void setMode(LegitSnapRotation.Mode mode) {
      if (mode != null && mode != this.mode) {
         this.mode = mode;
         this.tickCounter = 0;
      }
   }

   public void setMode(String mode) {
      this.setMode(LegitSnapRotation.Mode.of(mode));
   }

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public void setOnlyCriticalHit(boolean onlyCriticalHit) {
      this.onlyCriticalHit = onlyCriticalHit;
   }

   public void setCritsWithSpace(boolean critsWithSpace) {
      this.critsWithSpace = critsWithSpace;
   }

   public void setHitCounter(int hitCounter) {
      this.hitCounter = hitCounter;
   }

   public boolean isEngaged() {
      return this.engaged;
   }

   public boolean isTargetInFov() {
      return this.targetInFov;
   }

   public boolean isOnTarget() {
      return this.engaged && this.settled;
   }

   public boolean isReturning() {
      return this.owning && RotationContext.isActive();
   }

   public LegitSnapRotation.Mode mode() {
      return this.mode;
   }

   public int hitCounter() {
      return this.hitCounter;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (this.attackHold > 0) {
         this.attackHold--;
      }

      if (this.attackCooldown > 0) {
         this.attackCooldown--;
      }

      if (player != null) {
         this.recordCollision(player);
      }

      if (player == null) {
         this.reset();
      } else {
         boolean critNow = target != null && target.isAlive() && this.canCrit(player, 0);
         if (target != null && target.isAlive()) {
            if (this.targetId != target.getId()) {
               this.targetId = target.getId();
               this.engaged = false;
               this.settled = false;
               this.tickCounter = 0;
            }

            Vec3 eye = player.getEyePosition();
            AABB box = this.aimBox(target, targetEyePos);
            float viewYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYRot();
            float viewPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getXRot();
            this.targetInFov = this.fovDisabled || this.angleBetween(eye, box.getCenter(), viewYaw, viewPitch) <= this.fovAngle;
            if (!this.targetInFov && this.attackHold <= 0) {
               this.returnPhase(critNow);
            } else {
               float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
               float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
               Vec3 aim = this.computeVector(player, box, eye, currentYaw, currentPitch);
               if (aim == null) {
                  this.returnPhase(critNow);
               } else if (this.mode.predictTicks > 0 && !this.canAttack(player, this.mode.predictTicks, box, eye)) {
                  this.returnPhase(critNow);
               } else {
                  Vec3 diff = aim.subtract(eye);
                  double diffXZ = Math.hypot(diff.x, diff.z);
                  if (diffXZ < 1.0E-5) {
                     this.returnPhase(critNow);
                  } else {
                     float targetYaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
                     float targetPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, diffXZ))), -90.0F, 90.0F);
                     this.engaged = true;

                     float[] next = switch (this.mode) {
                        case FUNTIME -> this.funTimeRotation(currentYaw, currentPitch, targetYaw, targetPitch, critNow);
                        case SMOOTH -> this.funTimeRotation2(currentYaw, currentPitch, targetYaw, targetPitch, critNow);
                        case GRIM -> this.snapRotation(currentYaw, currentPitch, targetYaw, targetPitch, critNow || this.canCrit(player, 1));
                     };
                     this.settled = this.angleTo(next[0], next[1], targetYaw, targetPitch) < 2.0F;
                     RotationContext.setRotation(next[0], Mth.clamp(next[1], -90.0F, 90.0F));
                     this.owning = true;
                     this.tickCounter++;
                  }
               }
            }
         } else {
            this.returnPhase(critNow);
         }
      }
   }

   private void returnPhase(boolean critNow) {
      this.engaged = false;
      this.settled = false;
      this.targetInFov = false;
      if (!RotationContext.isActive()) {
         this.tickCounter = 0;
         this.owning = false;
      } else {
         float currentYaw = RotationContext.getServerYaw();
         float currentPitch = RotationContext.getServerPitch();
         float freeYaw = RotationContext.getFreeYaw();
         float freePitch = RotationContext.getFreePitch();

         float[] next = switch (this.mode) {
            case FUNTIME -> this.funTimeRotation(currentYaw, currentPitch, freeYaw, freePitch, critNow);
            case SMOOTH -> this.funTimeRotation2(currentYaw, currentPitch, freeYaw, freePitch, critNow);
            case GRIM -> this.snapRotation(currentYaw, currentPitch, freeYaw, freePitch, false);
         };
         if (this.tickCounter >= this.mode.ticksUntilReset && this.angleTo(next[0], next[1], freeYaw, freePitch) < 2.0F) {
            RotationContext.clear();
            this.tickCounter = 0;
            this.owning = false;
         } else {
            RotationContext.setRotation(next[0], Mth.clamp(next[1], -90.0F, 90.0F));
            this.owning = true;
            this.tickCounter++;
         }
      }
   }

   private float[] funTimeRotation(float curYaw, float curPitch, float targetYaw, float targetPitch, boolean critNow) {
      float yawDiff = Mth.wrapDegrees(targetYaw - curYaw);
      float pitchDiff = targetPitch - curPitch;
      float hypot = (float)Math.hypot(Math.abs(yawDiff), Math.abs(pitchDiff));
      if (hypot < 1.0E-5F) {
         return new float[]{curYaw, curPitch};
      }

      float yawLimit = Math.abs(yawDiff / hypot) * 180.0F;
      float pitchLimit = Math.abs(pitchDiff / hypot) * 180.0F;
      float yawStep = Mth.clamp(yawDiff, -yawLimit, yawLimit);
      float pitchStep = Mth.clamp(pitchDiff, -pitchLimit, pitchLimit);
      if (critNow) {
         return new float[]{curYaw + yawStep, curPitch + pitchStep};
      }

      float base = this.secureRandom.nextBoolean() ? 0.4F : 0.2F;
      long sinceAttack = System.currentTimeMillis() - this.lastAttackMillis;
      float low = sinceAttack < 500L ? -0.2F : base;
      float phaseA = (float)(System.currentTimeMillis() % 2000L) / 75.0F;
      float phaseB = (float)(System.currentTimeMillis() % 1000L) / 75.0F;
      float waveYaw;
      float wavePitch;
      switch (Math.floorMod(this.hitCounter, 4)) {
         case 0:
            waveYaw = (float)Math.cos(phaseA);
            wavePitch = (float)Math.sin(phaseB);
            break;
         case 1:
            waveYaw = (float)Math.sin(phaseA);
            wavePitch = (float)Math.cos(phaseB);
            break;
         case 2:
            waveYaw = (float)Math.sin(phaseA);
            wavePitch = (float)(-Math.cos(phaseB));
            break;
         default:
            waveYaw = (float)(-Math.cos(phaseA));
            wavePitch = (float)Math.sin(phaseB);
      }

      float yawWobble = sinceAttack < 1000L ? this.randomLerp(6.0F, 12.0F) * waveYaw : 0.0F;
      float pitchWobble = sinceAttack < 1000L ? this.randomLerp(2.0F, 4.0F) * wavePitch : 0.0F;
      float yaw = Mth.lerp(Mth.clamp(this.randomLerp(low, low + 0.2F), 0.0F, 1.0F), curYaw, curYaw + yawStep) + yawWobble;
      float pitch = Mth.lerp(Mth.clamp(this.randomLerp(low, low + 0.2F), 0.0F, 1.0F), curPitch, curPitch + pitchStep) + pitchWobble;
      return new float[]{yaw, pitch};
   }

   private float[] funTimeRotation2(float curYaw, float curPitch, float targetYaw, float targetPitch, boolean critNow) {
      float yawDiff = Mth.wrapDegrees(targetYaw - curYaw);
      float pitchDiff = targetPitch - curPitch;
      float hypot = (float)Math.hypot(Math.abs(yawDiff), Math.abs(pitchDiff));
      if (hypot < 1.0E-5F) {
         return new float[]{curYaw, curPitch};
      }

      float yawLimit = Math.abs(yawDiff / hypot) * 60.0F;
      float pitchLimit = Math.abs(pitchDiff / hypot) * 40.0F;
      float oscillation = critNow ? 0.0F : (float)Math.ceil(this.randomLerp(2.0F, 5.0F) * Math.sin(System.currentTimeMillis() / 40.0));
      float yawStep = Mth.clamp(yawDiff, -yawLimit, yawLimit) + oscillation;
      float pitchStep = Mth.clamp(pitchDiff, -pitchLimit, pitchLimit);
      float yawFactor = 0.6F + ThreadLocalRandom.current().nextFloat() * 0.6F;
      float pitchFactor = 0.5F + ThreadLocalRandom.current().nextFloat() * 0.5F;
      return new float[]{Mth.lerp(yawFactor, curYaw, curYaw + yawStep), Mth.lerp(pitchFactor, curPitch, curPitch + pitchStep)};
   }

   private float[] snapRotation(float curYaw, float curPitch, float targetYaw, float targetPitch, boolean snapNow) {
      float yawDiff = Mth.wrapDegrees(targetYaw - curYaw);
      float pitchDiff = targetPitch - curPitch;
      float hypot = (float)Math.hypot(Math.abs(yawDiff), Math.abs(pitchDiff));
      if (hypot < 1.0E-5F) {
         return new float[]{curYaw, curPitch};
      }

      float yawLimit = Math.abs(yawDiff / hypot) * 180.0F;
      float pitchLimit = Math.abs(pitchDiff / hypot) * 180.0F;
      boolean settledEnough = System.currentTimeMillis() - this.lastAttackMillis >= 50L;
      float yawFactor = snapNow ? 1.0F : (settledEnough ? this.getRandom(0.4F, 0.7F) : -0.2F);
      float pitchFactor = snapNow ? 1.0F : (settledEnough ? this.getRandom(0.4F, 0.7F) : -0.2F);
      return new float[]{
         Mth.lerp(yawFactor, curYaw, curYaw + Mth.clamp(yawDiff, -yawLimit, yawLimit)),
         Mth.lerp(pitchFactor, curPitch, curPitch + Mth.clamp(pitchDiff, -pitchLimit, pitchLimit))
      };
   }

   private float getRandom(float from, float to) {
      return from + ThreadLocalRandom.current().nextFloat() * (to - from);
   }

   private float randomLerp(float from, float to) {
      return Mth.lerp(this.secureRandom.nextFloat(), from, to);
   }

   private Vec3 computeVector(LocalPlayer player, AABB box, Vec3 eye, float curYaw, float curPitch) {
      List<Vec3> candidates = this.generateCandidatePoints(player, box, eye);
      Vec3 best = null;
      double bestCost = Double.MAX_VALUE;

      for (Vec3 candidate : candidates) {
         double cost = this.rotationCost(eye, candidate, curYaw, curPitch);
         if (cost < bestCost) {
            bestCost = cost;
            best = candidate;
         }
      }

      return best;
   }

   private List<Vec3> generateCandidatePoints(LocalPlayer player, AABB box, Vec3 eye) {
      double step = box.getYsize() / 10.0;
      Vec3 center = box.getCenter();
      List<Vec3> points = new ArrayList<>();
      if (step > 1.0E-6) {
         for (double y = box.minY; y <= box.maxY; y += step) {
            Vec3 candidate = new Vec3(center.x, y, center.z);
            if (this.isReachable(player, eye, candidate)) {
               points.add(candidate);
            }
         }
      }

      if (points.isEmpty()) {
         Vec3 closest = this.getClosestVec(eye, box);
         if (this.isReachable(player, eye, closest)) {
            points.add(closest);
         }
      }

      return points;
   }

   public boolean hasValidPoint(LocalPlayer player, LivingEntity target, Vec3 targetEyePos) {
      return player != null && target != null
         ? !this.generateCandidatePoints(player, this.aimBox(target, targetEyePos), player.getEyePosition()).isEmpty()
         : false;
   }

   private Vec3 getClosestVec(Vec3 from, AABB box) {
      return new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY), Mth.clamp(from.z, box.minZ, box.maxZ));
   }

   private boolean isReachable(LocalPlayer player, Vec3 eye, Vec3 point) {
      if (eye.distanceTo(point) > this.reach) {
         return false;
      }

      if (this.throughWalls) {
         return true;
      }

      BlockHitResult hit = player.level().clip(new ClipContext(eye, point, Block.COLLIDER, Fluid.NONE, player));
      return hit.getType() != Type.BLOCK;
   }

   private double rotationCost(Vec3 eye, Vec3 point, float curYaw, float curPitch) {
      Vec3 diff = point.subtract(eye);
      double diffXZ = Math.hypot(diff.x, diff.z);
      if (diffXZ < 1.0E-5) {
         return Double.MAX_VALUE;
      }

      float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      float pitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, diffXZ))), -90.0F, 90.0F);
      return Math.hypot(Mth.wrapDegrees(yaw - curYaw), pitch - curPitch);
   }

   private boolean canAttack(LocalPlayer player, int ticks, AABB box, Vec3 eye) {
      if (box.distanceToSqr(eye) > this.reach * this.reach) {
         return false;
      }

      for (int i = 0; i <= ticks; i++) {
         if (this.canCrit(player, i)) {
            return true;
         }
      }

      return false;
   }

   public boolean canCrit(LocalPlayer player, int ticks) {
      boolean ready = player.getAttackStrengthScale(ticks + 0.5F) > 0.9F && this.attackCooldown - ticks <= 0;
      if (!ready) {
         return false;
      }

      if (!this.onlyCriticalHit) {
         return true;
      }

      Level level = player.level();
      TickSimulator.SimState state = TickSimulator.simulateLocalPlayer(player, ticks, level);
      boolean jumping = state.jumpHeld || FeatureManager.INSTANCE.getEnabled(AutoJumpFeature.class) != null;
      return this.critsWithSpace && state.onGround && !jumping ? true : this.isCriticalWindow(player, ticks, level, state);
   }

   private boolean isCriticalWindow(LocalPlayer player, int ticks, Level level, TickSimulator.SimState state) {
      if (!this.hasEffect(player, MobEffects.LEVITATION, ticks)
         && !this.hasEffect(player, MobEffects.BLINDNESS, ticks)
         && !this.hasEffect(player, MobEffects.SLOW_FALLING, ticks)) {
         if (state.inCobweb || state.submergedInWater && state.inWater || state.inLava || state.climbing || player.getAbilities().flying) {
            return true;
         }

         if (player.getMainHandItem().is(Items.MACE)) {
            return MaceItem.canSmashAttack(player);
         }

         boolean groundNext = TickSimulator.simulateLocalPlayer(player, ticks + 1, level).onGround;
         boolean groundAfter = TickSimulator.simulateLocalPlayer(player, ticks + 2, level).onGround;
         boolean landingHard = state.fallDistance > 1.0F && groundNext;
         boolean landingExact = state.fallDistance == (float)state.motion.y && groundNext;
         boolean steppingDown = state.motion.y < -0.3
            && groundAfter
            && (
               this.verticalCollisionSnapshot(player, 4)
                  || this.verticalCollisionSnapshot(player, 5)
                  || this.verticalCollisionSnapshot(player, 6)
                  || this.verticalCollisionSnapshot(player, 7)
            );
         return !state.onGround && state.fallDistance > 0.0F && !steppingDown && !landingExact && !landingHard;
      } else {
         return true;
      }
   }

   private boolean hasEffect(LocalPlayer player, Holder<MobEffect> effect, int ticks) {
      MobEffectInstance instance = player.getEffect(effect);
      return instance != null && instance.getDuration() >= ticks;
   }

   private boolean verticalCollisionSnapshot(LocalPlayer player, int ticksAgo) {
      if (ticksAgo > 0 && ticksAgo <= this.collisionHistory.length && ticksAgo <= this.collisionSamples) {
         int index = Math.floorMod(this.collisionCursor - ticksAgo, this.collisionHistory.length);
         return this.collisionHistory[index];
      } else {
         return player.verticalCollision;
      }
   }

   private void recordCollision(LocalPlayer player) {
      this.collisionHistory[this.collisionCursor] = player.verticalCollision;
      this.collisionCursor = (this.collisionCursor + 1) % this.collisionHistory.length;
      if (this.collisionSamples < this.collisionHistory.length) {
         this.collisionSamples++;
      }
   }

   @Override
   public void onAttack() {
      this.lastAttackMillis = System.currentTimeMillis();
      this.hitCounter++;
      this.attackHold = 4;
      this.attackCooldown = this.mode.cooldownFor(this.hitCounter);
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.engaged = false;
      this.settled = false;
      this.targetInFov = false;
      this.attackHold = 0;
      this.tickCounter = 0;
      this.owning = false;
   }

   private AABB aimBox(LivingEntity target, Vec3 targetEyePos) {
      AABB box = target.getBoundingBox();
      if (targetEyePos != null) {
         Vec3 offset = targetEyePos.subtract(target.getEyePosition());
         if (offset.lengthSqr() > 1.0E-6) {
            box = box.move(offset);
         }
      }

      return box;
   }

   private float angleTo(float yawA, float pitchA, float yawB, float pitchB) {
      return (float)Math.hypot(Mth.wrapDegrees(yawA - yawB), pitchA - pitchB);
   }

   private double angleBetween(Vec3 eye, Vec3 point, float yaw, float pitch) {
      Vec3 toPoint = point.subtract(eye);
      double length = toPoint.length();
      if (length < 1.0E-6) {
         return 0.0;
      }

      Vec3 look = Vec3.directionFromRotation(pitch, yaw);
      double dot = Mth.clamp(look.dot(toPoint) / length, -1.0, 1.0);
      return Math.toDegrees(Math.acos(dot));
   }

   public enum Mode {
      FUNTIME(20, 5, new int[]{10, 10, 10, 10, 12}),
      SMOOTH(5, 0, new int[]{10, 10, 10, 13}),
      GRIM(2, 4, new int[]{10});

      final int ticksUntilReset;
      final int predictTicks;
      final int[] cooldownPattern;

      Mode(int ticksUntilReset, int predictTicks, int[] cooldownPattern) {
         this.ticksUntilReset = ticksUntilReset;
         this.predictTicks = predictTicks;
         this.cooldownPattern = cooldownPattern;
      }

      int cooldownFor(int hitCounter) {
         return this.cooldownPattern[Math.floorMod(hitCounter, this.cooldownPattern.length)];
      }

      public static LegitSnapRotation.Mode of(String name) {
         if (name == null) {
            return FUNTIME;
         }

         return switch (name) {
            case "Smooth" -> SMOOTH;
            case "Grim" -> GRIM;
            default -> FUNTIME;
         };
      }
   }
}
