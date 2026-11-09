package win.furryhook.utils.combat.rotations;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
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

public final class FunfickRotation implements AuraRotation {
   private static final int TICKS_UNTIL_RESET = 20;
   private static final int PREDICT_TICKS = 5;
   private static final int[] COOLDOWN_PATTERN = new int[]{10, 10, 10, 10, 12};
   private static final double CANDIDATE_DIVISOR = 10.0;
   private static final float RELEASE_ANGLE = 2.0F;
   private final SecureRandom secureRandom = new SecureRandom();
   private double reach = 3.0;
   private boolean throughWalls;
   private boolean onlyCriticalHit;
   private boolean critsWithSpace;
   private int targetId = Integer.MIN_VALUE;
   private boolean engaged;
   private boolean settled;
   private boolean owning;
   private int tickCounter;
   private long lastAttackMillis = -2305843009213693952L;
   private int hitCounter;
   private int attackCooldown;
   private final boolean[] collisionHistory = new boolean[8];
   private int collisionCursor;
   private int collisionSamples;
   private float targetPitchOffset = 0.5F;

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

   public boolean isEngaged() {
      return this.engaged;
   }

   public boolean isOnTarget() {
      return this.engaged && this.settled;
   }

   public boolean isReturning() {
      return this.owning && RotationContext.isActive();
   }

   public int hitCounter() {
      return this.hitCounter;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
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
               float[] bodyPoints = new float[]{-1.0F, -0.5F, 0.0F, 0.5F, 1.0F};
               this.targetPitchOffset = bodyPoints[this.secureRandom.nextInt(bodyPoints.length)];
            }

            Vec3 eye = player.getEyePosition();
            AABB box = this.aimBox(target, targetEyePos);
            float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
            float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
            Vec3 aim = this.computeVector(player, box, eye, currentYaw, currentPitch);
            if (aim == null) {
               this.returnPhase(critNow);
            } else if (!this.canAttack(player, 5, box, eye)) {
               this.returnPhase(critNow);
            } else {
               Vec3 diff = aim.subtract(eye);
               double diffXZ = Math.hypot(diff.x, diff.z);
               if (diffXZ < 1.0E-5) {
                  this.returnPhase(critNow);
               } else {
                  float targetYaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
                  float targetPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, diffXZ))), -90.0F, 90.0F);
                  if (target != null) {
                     double entityHeight = target.getBbHeight();
                     double entityBottom = target.getY();
                     double heightPercent = (this.targetPitchOffset + 1.0) * 0.5;
                     double targetY = entityBottom + entityHeight * heightPercent;
                     Vec3 multipointAim = new Vec3(aim.x, targetY, aim.z);
                     Vec3 multipointDiff = multipointAim.subtract(eye);
                     double multipointDiffXZ = Math.sqrt(multipointDiff.x * multipointDiff.x + multipointDiff.z * multipointDiff.z);
                     targetPitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(multipointDiff.y, multipointDiffXZ))), -90.0F, 90.0F);
                  }

                  this.engaged = true;
                  float[] next = this.funTimeRotation(currentYaw, currentPitch, targetYaw, targetPitch, critNow);
                  this.settled = this.angleTo(next[0], next[1], targetYaw, targetPitch) < 2.0F;
                  RotationContext.setRotation(next[0], Mth.clamp(next[1], -90.0F, 90.0F));
                  this.owning = true;
                  this.tickCounter++;
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
      if (!RotationContext.isActive()) {
         this.tickCounter = 0;
         this.owning = false;
      } else {
         float currentYaw = RotationContext.getServerYaw();
         float currentPitch = RotationContext.getServerPitch();
         float freeYaw = RotationContext.getFreeYaw();
         float freePitch = RotationContext.getFreePitch();
         float[] next = this.funTimeRotation(currentYaw, currentPitch, freeYaw, freePitch, critNow);
         if (this.tickCounter >= 20 && this.angleTo(next[0], next[1], freeYaw, freePitch) < 2.0F) {
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
      if (!(hypot < 0.001F) && !Float.isNaN(hypot) && !Float.isInfinite(hypot)) {
         float maxStepYaw = Math.abs(yawDiff) / hypot * 130.0F;
         float maxStepPitch = Math.abs(pitchDiff) / hypot * 130.0F;
         float yawStep = Mth.clamp(yawDiff, -maxStepYaw, maxStepYaw);
         float pitchStep = Mth.clamp(pitchDiff, -maxStepPitch, maxStepPitch);
         boolean shouldFlick = critNow && this.hitCounter > 0 && this.hitCounter % 4 == 0;
         long sinceAttack = System.currentTimeMillis() - this.lastAttackMillis;
         boolean isFlickWindow = sinceAttack < 250L;
         if (shouldFlick && isFlickWindow) {
            float var28 = -89.42F + this.secureRandom.nextFloat() * 179.33F;
            float var27 = curYaw + yawStep;
            return new float[]{var27, Mth.clamp(var28, -89.0F, 90.0F)};
         }

         float nextYaw;
         float nextPitch;
         if (critNow) {
            nextYaw = curYaw + yawStep;
            nextPitch = curPitch + pitchStep;
         } else if (sinceAttack >= 535L) {
            long nowMs = System.currentTimeMillis();
            float shakeYaw = (18.0F + (float)Math.random() * 10.0F) * (float)Math.sin(nowMs / 60.0);
            float shakePitch = (6.0F + (float)Math.random() * 10.0F) * (float)Math.cos(nowMs / 60.0);
            nextYaw = Mth.clamp(curYaw + shakeYaw, curYaw - 45.0F, curYaw + 45.0F);
            nextPitch = Mth.clamp(curPitch + shakePitch, curPitch - 45.0F, curPitch + 45.0F);
         } else {
            float base = this.secureRandom.nextBoolean() ? 0.4F : 0.2F;
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
            nextYaw = curYaw + yawStep + yawWobble;
            nextPitch = curPitch + pitchStep + pitchWobble;
         }

         return this.applyGCDGrid(nextYaw, nextPitch);
      } else {
         return new float[]{curYaw, curPitch};
      }
   }

   private float randomLerp(float from, float to) {
      return Mth.lerp(this.secureRandom.nextFloat(), from, to);
   }

   private float[] applyGCDGrid(float yaw, float pitch) {
      float gcdStep = 0.15F;
      float quantizedYaw = Math.round(yaw / gcdStep) * gcdStep;
      float quantizedPitch = Math.round(pitch / gcdStep) * gcdStep;
      return new float[]{quantizedYaw, quantizedPitch};
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
      if (this.hitCounter % 2 == 0) {
         float[] bodyPoints = new float[]{-1.0F, -0.5F, 0.0F, 0.5F, 1.0F};
         this.targetPitchOffset = bodyPoints[this.secureRandom.nextInt(bodyPoints.length)];
      }

      this.attackCooldown = COOLDOWN_PATTERN[Math.floorMod(this.hitCounter, COOLDOWN_PATTERN.length)];
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      this.engaged = false;
      this.settled = false;
      this.owning = false;
      this.tickCounter = 0;
      this.targetPitchOffset = 0.0F;
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
}
