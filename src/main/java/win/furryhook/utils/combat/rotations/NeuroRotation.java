package win.furryhook.utils.combat.rotations;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.FriendManager;
import win.furryhook.utils.combat.AuraRaycast;
import win.furryhook.utils.combat.neuro.NeuroDataset;
import win.furryhook.utils.combat.neuro.NeuroLabel;
import win.furryhook.utils.combat.neuro.NeuroMouse;
import win.furryhook.utils.combat.neuro.NeuroQuery;
import win.furryhook.utils.combat.neuro.NeuroSample;

public final class NeuroRotation implements AuraRotation {
   private static final int MIN_FRAMES = 3;
   private static final float SINCE_HIT_CAP = 40.0F;
   private static final float SANE_STEP = 120.0F;
   private static final float MAX_STEP = 90.0F;
   private static final float REPLAY_FOLLOW = 0.35F;
   private static final float FALLBACK_SPEED = 0.5F;
   private static final int ASSIST_STEPS = 6;
   private static final int TRACK_FLUSH_FRAMES = 60;
   private boolean learning;
   private boolean replay;
   private int neighbours = 6;
   private float variation = 0.35F;
   private boolean assist = true;
   private float assistLimit = 1.0F;
   private double reach = 3.0;
   private boolean throughWalls;
   private float duelBias = 0.5F;
   private boolean duelOnly;
   private boolean replayHitsOnly = true;
   private float swingDistanceSum;
   private float swingSmoothSum;
   private int swingDuelFrames;
   private int swingOpponents;
   private int targetId = Integer.MIN_VALUE;
   private final float[] pending = new float[16];
   private boolean hasPending;
   private final float[] swing = new float[4080];
   private int swingFrames;
   private boolean hasHistory;
   private float previousYaw;
   private float previousPitch;
   private float previousNeedYaw;
   private float previousNeedPitch;
   private float lastDeltaYaw;
   private float lastDeltaPitch;
   private int ticksSinceHit;
   private NeuroSample replaySample;
   private int replayFrame;
   private int lastTick = Integer.MIN_VALUE;

   public void setLearning(boolean learning) {
      if (this.learning != learning) {
         this.learning = learning;
         if (learning) {
            this.discardSwing();
            this.clearHistory();
         } else {
            this.flushSwing("track");
            NeuroDataset.INSTANCE.saveIfDirty();
         }
      }
   }

   public boolean isLearning() {
      return this.learning;
   }

   public int swingFrames() {
      return this.swingFrames;
   }

   public void setReplay(boolean replay) {
      this.replay = replay;
   }

   public void setNeighbours(int neighbours) {
      this.neighbours = Math.clamp(neighbours, 1, 16);
   }

   public void setVariation(float variation) {
      this.variation = Mth.clamp(variation, 0.0F, 1.0F);
   }

   public void setAssist(boolean assist) {
      this.assist = assist;
   }

   public void setAssistLimit(float assistLimit) {
      this.assistLimit = Mth.clamp(assistLimit, 0.0F, 1.0F);
   }

   public void setReach(double reach) {
      this.reach = reach;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public void setDuelBias(float duelBias) {
      this.duelBias = Mth.clamp(duelBias, 0.0F, 1.0F);
   }

   public void setDuelOnly(boolean duelOnly) {
      this.duelOnly = duelOnly;
   }

   public void setReplayHitsOnly(boolean replayHitsOnly) {
      this.replayHitsOnly = replayHitsOnly;
   }

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target == null || !target.isAlive()) {
         this.reset();
      } else if (player.tickCount != this.lastTick) {
         this.lastTick = player.tickCount;
         if (this.targetId != target.getId()) {
            this.targetId = target.getId();
            if (this.learning) {
               this.flushSwing("lost");
            } else {
               this.discardSwing();
            }

            this.clearHistory();
         }

         Vec3 eye = player.getEyePosition(1.0F);
         float[] need = rotationTo(eye, targetEyePos);
         float distance = (float)eye.distanceTo(targetEyePos);
         if (this.learning) {
            this.record(player, target, need, distance);
         } else {
            this.play(player, target, need, distance);
         }
      }
   }

   @Override
   public void onAttack() {
      if (this.learning) {
         this.flushSwing("hit");
         this.hasPending = false;
      } else {
         this.replaySample = null;
         this.replayFrame = 0;
      }

      this.ticksSinceHit = 0;
   }

   @Override
   public void reset() {
      this.targetId = Integer.MIN_VALUE;
      if (this.learning) {
         this.flushSwing("lost");
         this.hasPending = false;
      } else {
         this.discardSwing();
      }

      this.clearHistory();
   }

   private void record(LocalPlayer player, LivingEntity target, float[] need, float distance) {
      if (RotationContext.isActive()) {
         RotationContext.disengage();
         this.hasPending = false;
         NeuroMouse.consume();
         this.clearHistory();
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui != null && mc.gui.screen() != null) {
            this.hasPending = false;
            NeuroMouse.consume();
            this.clearHistory();
         } else {
            int mouseSteps = NeuroMouse.steps();
            float mousePeak = NeuroMouse.peak();
            float evenness = NeuroMouse.evenness();
            NeuroMouse.consume();
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            float movedYaw = 0.0F;
            float movedPitch = 0.0F;
            if (this.hasHistory) {
               movedYaw = Mth.wrapDegrees(yaw - this.previousYaw);
               movedPitch = pitch - this.previousPitch;
            }

            boolean sane = Math.abs(movedYaw) <= 120.0F && Math.abs(movedPitch) <= 120.0F;
            if (this.hasPending && this.hasHistory && sane) {
               this.pending[8] = movedYaw;
               this.pending[9] = movedPitch;
               this.appendFrame();
            }

            this.pending[0] = Mth.wrapDegrees(need[0] - yaw);
            this.pending[1] = need[1] - pitch;
            this.pending[2] = sane ? movedYaw : 0.0F;
            this.pending[3] = sane ? movedPitch : 0.0F;
            this.pending[4] = this.hasHistory ? Mth.wrapDegrees(need[0] - this.previousNeedYaw) : 0.0F;
            this.pending[5] = this.hasHistory ? need[1] - this.previousNeedPitch : 0.0F;
            this.pending[6] = distance;
            this.pending[7] = Math.min(this.ticksSinceHit, 40.0F);
            int opponents = countOpponents(player);
            boolean duel = opponents == 1;
            this.pending[10] = (float)targetSpeed(target);
            this.pending[11] = mouseSteps;
            this.pending[12] = mousePeak;
            this.pending[13] = evenness;
            this.pending[14] = sane ? (float)Math.hypot(movedYaw - this.lastDeltaYaw, movedPitch - this.lastDeltaPitch) : 0.0F;
            this.pending[15] = duel ? 1.0F : 0.0F;
            this.hasPending = true;
            this.lastDeltaYaw = sane ? movedYaw : 0.0F;
            this.lastDeltaPitch = sane ? movedPitch : 0.0F;
            this.swingDistanceSum += distance;
            this.swingSmoothSum += evenness;
            if (duel) {
               this.swingDuelFrames++;
            }

            this.swingOpponents = Math.max(this.swingOpponents, opponents);
            if (this.swingFrames >= 60) {
               this.flushSwing("track");
            }

            this.rememberState(yaw, pitch, need);
         }
      }
   }

   private static double targetSpeed(LivingEntity target) {
      return target == null ? 0.0 : target.position().distanceTo(new Vec3(target.xOld, target.yOld, target.zOld));
   }

   private static int countOpponents(LocalPlayer player) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return 0;
      }

      int count = 0;

      for (Player other : mc.level.players()) {
         if (other != player
            && other.isAlive()
            && !other.isSpectator()
            && !(other.distanceToSqr(player) > 256.0)
            && !FriendManager.INSTANCE.isFriend(other.getGameProfile().name())) {
            count++;
         }
      }

      return count;
   }

   private void play(LocalPlayer player, LivingEntity target, float[] need, float distance) {
      NeuroMouse.consume();
      float yaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
      float pitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
      float errYaw = Mth.wrapDegrees(need[0] - yaw);
      float errPitch = need[1] - pitch;
      NeuroQuery query = new NeuroQuery(
         errYaw,
         errPitch,
         this.lastDeltaYaw,
         this.lastDeltaPitch,
         this.hasHistory ? Mth.wrapDegrees(need[0] - this.previousNeedYaw) : 0.0F,
         this.hasHistory ? need[1] - this.previousNeedPitch : 0.0F,
         distance,
         Math.min(this.ticksSinceHit, 40.0F),
         (float)targetSpeed(target),
         countOpponents(player) == 1
      );
      float[] step = this.replay ? this.replayStep(query) : NeuroDataset.INSTANCE.predict(query, this.neighbours, this.variation, this.duelBias);
      if (step == null) {
         step = new float[]{errYaw * 0.5F, errPitch * 0.5F};
      }

      float newYaw = yaw + Mth.clamp(step[0], -90.0F, 90.0F);
      float newPitch = Mth.clamp(pitch + Mth.clamp(step[1], -90.0F, 90.0F), -90.0F, 90.0F);
      if (this.assist && distance <= this.reach) {
         float[] corrected = this.correct(player, target, newYaw, newPitch);
         newYaw = corrected[0];
         newPitch = corrected[1];
      }

      RotationContext.setRotation(newYaw, newPitch);
      float appliedYaw = RotationContext.getServerYaw();
      float appliedPitch = RotationContext.getServerPitch();
      this.lastDeltaYaw = Mth.wrapDegrees(appliedYaw - yaw);
      this.lastDeltaPitch = appliedPitch - pitch;
      this.rememberState(appliedYaw, appliedPitch, need);
   }

   private float[] replayStep(NeuroQuery query) {
      if (this.replaySample == null || this.replayFrame >= this.replaySample.frames()) {
         this.replaySample = NeuroDataset.INSTANCE.nearestSample(query, this.duelBias, this.replayHitsOnly);
         this.replayFrame = 0;
         if (this.replaySample == null) {
            return null;
         }
      }

      float recordedErrYaw = this.replaySample.get(this.replayFrame, 0);
      float recordedErrPitch = this.replaySample.get(this.replayFrame, 1);
      float yaw = this.replaySample.get(this.replayFrame, 8) + Mth.wrapDegrees(query.errYaw() - recordedErrYaw) * 0.35F;
      float pitch = this.replaySample.get(this.replayFrame, 9) + (query.errPitch() - recordedErrPitch) * 0.35F;
      this.replayFrame++;
      return new float[]{yaw, pitch};
   }

   private float[] correct(LocalPlayer player, LivingEntity target, float yaw, float pitch) {
      float[] unchanged = new float[]{yaw, pitch};
      if (this.hits(player, target, yaw, pitch)) {
         return unchanged;
      }

      Vec3 point = AuraRaycast.findAimPoint(player, target, target.position(), this.reach, this.throughWalls, Vec3.ZERO);
      if (point == null) {
         return unchanged;
      }

      float[] ideal = rotationTo(player.getEyePosition(1.0F), point);
      float gapYaw = Mth.wrapDegrees(ideal[0] - yaw);
      float gapPitch = ideal[1] - pitch;
      if (!this.hits(player, target, yaw + gapYaw, Mth.clamp(pitch + gapPitch, -90.0F, 90.0F))) {
         return unchanged;
      }

      float miss = 0.0F;
      float hit = 1.0F;

      for (int step = 0; step < 6; step++) {
         float middle = (miss + hit) * 0.5F;
         if (this.hits(player, target, yaw + gapYaw * middle, Mth.clamp(pitch + gapPitch * middle, -90.0F, 90.0F))) {
            hit = middle;
         } else {
            miss = middle;
         }
      }

      float blend = Math.min(hit, this.assistLimit);
      return new float[]{yaw + gapYaw * blend, Mth.clamp(pitch + gapPitch * blend, -90.0F, 90.0F)};
   }

   private boolean hits(LocalPlayer player, LivingEntity target, float yaw, float pitch) {
      return AuraRaycast.rotationIntersectsTarget(player, target, yaw, pitch, this.reach, 0.0, 0.0, this.throughWalls);
   }

   private void appendFrame() {
      if (this.swingFrames >= 255) {
         System.arraycopy(this.swing, 16, this.swing, 0, 4064);
         this.swingFrames = 254;
      }

      System.arraycopy(this.pending, 0, this.swing, this.swingFrames * 16, 16);
      this.swingFrames++;
   }

   private void flushSwing(String outcome) {
      if (this.swingFrames >= 3 && (!this.duelOnly || this.swingDuelFrames * 2 >= this.swingFrames)) {
         int values = this.swingFrames * 16;
         NeuroDataset.INSTANCE.add(new NeuroSample(Arrays.copyOf(this.swing, values), this.swingFrames, System.currentTimeMillis(), this.buildLabel(outcome)));
      }

      this.clearSwingStats();
   }

   private NeuroLabel buildLabel(String outcome) {
      int frames = Math.max(1, this.swingFrames);
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      int ping = 0;
      if (mc.getConnection() != null && player != null) {
         PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
         if (info != null) {
            ping = info.getLatency();
         }
      }

      String weapon = player == null ? "" : BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
      return new NeuroLabel(
         outcome, this.swingDuelFrames * 2 >= this.swingFrames, this.swingOpponents, this.swingSmoothSum / frames, this.swingDistanceSum / frames, ping, weapon
      );
   }

   private void discardSwing() {
      this.clearSwingStats();
      this.hasPending = false;
   }

   private void clearSwingStats() {
      this.swingFrames = 0;
      this.swingDistanceSum = 0.0F;
      this.swingSmoothSum = 0.0F;
      this.swingDuelFrames = 0;
      this.swingOpponents = 0;
   }

   private void clearHistory() {
      this.hasHistory = false;
      this.lastDeltaYaw = 0.0F;
      this.lastDeltaPitch = 0.0F;
      this.ticksSinceHit = 0;
      this.replaySample = null;
      this.replayFrame = 0;
   }

   private void rememberState(float yaw, float pitch, float[] need) {
      this.previousYaw = yaw;
      this.previousPitch = pitch;
      this.previousNeedYaw = need[0];
      this.previousNeedPitch = need[1];
      this.hasHistory = true;
      this.ticksSinceHit++;
   }

   private static float[] rotationTo(Vec3 eye, Vec3 point) {
      Vec3 diff = point.subtract(eye);
      double horizontal = Math.hypot(diff.x, diff.z);
      float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      float pitch = Mth.clamp((float)(-Math.toDegrees(Math.atan2(diff.y, horizontal))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }
}
