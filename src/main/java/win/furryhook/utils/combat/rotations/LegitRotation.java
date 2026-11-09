package win.furryhook.utils.combat.rotations;

import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.RotationContext;
import win.furryhook.utils.math.NoiseUtil;

public final class LegitRotation implements AuraRotation {
   private final LinkedList<Vec3> targetPositionHistory = new LinkedList<>();
   private float remainderYaw = 0.0F;
   private float remainderPitch = 0.0F;

   @Override
   public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
      if (target != null && target.isAlive()) {
         long now = System.currentTimeMillis();
         this.targetPositionHistory.addLast(target.getDeltaMovement());

         while (this.targetPositionHistory.size() > 7) {
            this.targetPositionHistory.removeFirst();
         }

         Vec3 targetPos = target.position();
         if (!this.targetPositionHistory.isEmpty()) {
            int delayIndex = Math.min(2, this.targetPositionHistory.size() - 1);
            Vec3 historicVel = this.targetPositionHistory.get(delayIndex);
            targetPos = target.position().subtract(historicVel.scale(0.35));
         }

         double noiseTime = now / 450.0;
         float wanderY = NoiseUtil.perlin1D(noiseTime) * 0.1F;
         float wanderX = NoiseUtil.perlin2D(noiseTime, 10.0) * 0.06F;
         float wanderZ = NoiseUtil.perlin2D(noiseTime, 20.0) * 0.06F;
         double targetY = targetPos.y + target.getBbHeight() * (0.5F + wanderY);
         Vec3 aimVec = new Vec3(targetPos.x + wanderX, targetY, targetPos.z + wanderZ).subtract(player.getEyePosition(1.0F));
         double horizDist = Math.hypot(aimVec.x, aimVec.z);
         float rawYaw = (float)Math.toDegrees(Math.atan2(-aimVec.x, aimVec.z));
         float rawPitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(aimVec.y, horizDist)), -90.0, 90.0);
         float currentYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float currentPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         float deltaYaw = Mth.wrapDegrees(rawYaw - currentYaw);
         float deltaPitch = rawPitch - currentPitch;
         float totalDist = (float)Math.hypot(deltaYaw, deltaPitch);
         float maxStepYaw = 35.0F;
         if (totalDist < 5.0F) {
            maxStepYaw = randomLerp(12.0F, 18.0F);
         } else if (totalDist > 25.0F) {
            maxStepYaw = randomLerp(38.0F, 48.0F);
         }

         float stepYaw = Mth.clamp(deltaYaw * randomLerp(0.45F, 0.65F), -maxStepYaw, maxStepYaw);
         double arcNoiseTime = now / 180.0;
         float baseArcStrength = 0.35F + NoiseUtil.perlin1D(arcNoiseTime) * 0.35F;
         float arcJitter = randomLerp(-0.12F, 0.12F) + NoiseUtil.perlin2D(now / 75.0, 5.0) * 0.2F;
         float arcMultiplier = baseArcStrength + arcJitter;
         float arcBias = (float)Math.sin(Math.toRadians(deltaYaw)) * (stepYaw * arcMultiplier);
         float pitchSyncRatio = randomLerp(0.1F, 0.2F);
         float stepPitch = deltaPitch * pitchSyncRatio + arcBias * (0.15F + arcJitter * 0.5F);
         double tremorTime = now / 110.0;
         float jitterX = randomLerp(-0.15F, 0.15F);
         float jitterY = randomLerp(-0.08F, 0.08F);
         float tremorYaw = NoiseUtil.perlin2D(tremorTime, 1.0) * 0.4F + jitterX;
         float tremorPitch = NoiseUtil.perlin2D(tremorTime, 2.0) * 0.15F + jitterY;
         stepYaw += tremorYaw;
         stepPitch += tremorPitch;
         float gcd = getGCDValue();
         float targetStepYaw = stepYaw + this.remainderYaw;
         float targetStepPitch = stepPitch + this.remainderPitch;
         int mX = Math.round(targetStepYaw / gcd);
         int mY = Math.round(targetStepPitch / gcd);
         float finalDeltaYaw = mX * gcd;
         float finalDeltaPitch = mY * gcd;
         this.remainderYaw = targetStepYaw - finalDeltaYaw;
         this.remainderPitch = targetStepPitch - finalDeltaPitch;
         RotationContext.setRotation(currentYaw + finalDeltaYaw, Mth.clamp(currentPitch + finalDeltaPitch, -90.0F, 90.0F));
      } else {
         this.reset();
      }
   }

   @Override
   public void reset() {
      this.targetPositionHistory.clear();
      this.remainderYaw = 0.0F;
      this.remainderPitch = 0.0F;
   }

   private static float getGCDValue() {
      float sensitivity = ((Double)Minecraft.getInstance().options.sensitivity().get()).floatValue() * 0.6F + 0.2F;
      return sensitivity * sensitivity * sensitivity * 8.0F * 0.15F;
   }

   private static float randomLerp(float min, float max) {
      return min + ThreadLocalRandom.current().nextFloat() * (max - min);
   }
}
