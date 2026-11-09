package win.furryhook.feature.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.math.TickSimulator;

public final class BowAimbotFeature extends Feature {
   private static final int MAX_FLIGHT_TICKS = 120;
   private static final double MAX_HIT_ERROR_SQR = 12.25;
   private boolean isAiming = false;
   public final NumberSetting range = this.register(new NumberSetting("Range", 50.0, 5.0, 100.0, 5.0, " blocks"));

   public BowAimbotFeature() {
      super("BowAimbot", "Predicts target movement and arrow ballistics", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      RotationContext.clear();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      ClientLevel level = mc.level;
      if (player != null && level != null && player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem) {
         LivingEntity target = this.findClosestTarget(player, level);
         if (target == null) {
            if (this.isAiming) {
               RotationContext.clear();
               this.isAiming = false;
            }
         } else {
            float force = player.getTicksUsingItem(0.0F) / 20.0F;
            force = Math.min(1.0F, (force * force + force * 2.0F) / 3.0F);
            if (force < 0.1F) {
               if (this.isAiming) {
                  RotationContext.clear();
                  this.isAiming = false;
               }
            } else {
               Vec3 predictedPos = TickSimulator.getPredictedState(target, 15, level).pos;
               double targetY = predictedPos.y + target.getBbHeight() * 0.5;
               double diffX = predictedPos.x - player.getX();
               double diffZ = predictedPos.z - player.getZ();
               double distanceXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
               float yaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
               double basePitch = -Math.toDegrees(Math.atan2(targetY - player.getEyeY(), distanceXZ));
               double velocity = force * 3.0;
               float bestPitch = player.getXRot();
               double bestScore = Double.MAX_VALUE;
               double bestApexX = player.getX();
               double bestApexY = player.getEyeY() - 0.1;
               double bestApexZ = player.getZ();
               double bestEndX = bestApexX;
               double bestEndY = bestApexY;
               double bestEndZ = bestApexZ;

               for (float pitch = (float)basePitch + 5.0F; pitch > -85.0F; pitch -= 0.5F) {
                  double pitchRad = Math.toRadians(pitch);
                  double yawRad = Math.toRadians(yaw);
                  double motionX = -Math.sin(yawRad) * Math.cos(pitchRad) * velocity;
                  double motionY = -Math.sin(pitchRad) * velocity;
                  double motionZ = Math.cos(yawRad) * Math.cos(pitchRad) * velocity;
                  double arrowX = player.getX();
                  double arrowY = player.getEyeY() - 0.1;
                  double arrowZ = player.getZ();
                  double apexX = arrowX;
                  double apexY = arrowY;
                  double apexZ = arrowZ;
                  double closestX = arrowX;
                  double closestY = arrowY;
                  double closestZ = arrowZ;
                  double score = Double.MAX_VALUE;

                  for (int step = 0; step < 120; step++) {
                     arrowX += motionX;
                     arrowY += motionY;
                     arrowZ += motionZ;
                     if (arrowY > apexY) {
                        apexX = arrowX;
                        apexY = arrowY;
                        apexZ = arrowZ;
                     }

                     double dx = arrowX - predictedPos.x;
                     double dy = arrowY - targetY;
                     double dz = arrowZ - predictedPos.z;
                     double distanceSqr = dx * dx + dy * dy + dz * dz;
                     if (distanceSqr < score) {
                        score = distanceSqr;
                        closestX = arrowX;
                        closestY = arrowY;
                        closestZ = arrowZ;
                     }

                     motionX *= 0.99;
                     motionY = motionY * 0.99 - 0.05;
                     motionZ *= 0.99;
                     if (motionY < 0.0 && arrowY < predictedPos.y - 1.0) {
                        break;
                     }
                  }

                  if (score < bestScore) {
                     bestScore = score;
                     bestPitch = pitch;
                     bestApexX = apexX;
                     bestApexY = apexY;
                     bestApexZ = apexZ;
                     bestEndX = closestX;
                     bestEndY = closestY;
                     bestEndZ = closestZ;
                  }
               }

               if (bestScore < 12.25 && this.isPathClear(level, player, bestApexX, bestApexY, bestApexZ, bestEndX, bestEndY, bestEndZ)) {
                  RotationContext.setRotation(yaw, bestPitch);
                  this.isAiming = true;
               } else if (this.isAiming) {
                  RotationContext.clear();
                  this.isAiming = false;
               }
            }
         }
      } else {
         if (this.isAiming) {
            RotationContext.clear();
            this.isAiming = false;
         }
      }
   }

   private boolean isPathClear(ClientLevel level, LocalPlayer player, double apexX, double apexY, double apexZ, double endX, double endY, double endZ) {
      Vec3 start = new Vec3(player.getX(), player.getEyeY() - 0.1, player.getZ());
      Vec3 apex = new Vec3(apexX, apexY, apexZ);
      Vec3 end = new Vec3(endX, endY, endZ);
      return start.distanceToSqr(apex) > 0.01 && this.isBlocked(level, player, start, apex)
         ? false
         : apex.distanceToSqr(end) <= 0.01 || !this.isBlocked(level, player, apex, end);
   }

   private boolean isBlocked(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 to) {
      return level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player)).getType() == Type.BLOCK;
   }

   private LivingEntity findClosestTarget(LocalPlayer player, ClientLevel level) {
      double maxRange = this.range.getValue();
      double closestDistance = maxRange * maxRange;
      LivingEntity closest = null;

      for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(maxRange), this::isTarget)) {
         double distance = player.distanceToSqr(entity);
         if (distance < closestDistance && entity instanceof LivingEntity living) {
            closestDistance = distance;
            closest = living;
         }
      }

      return closest;
   }

   private boolean isTarget(Entity entity) {
      return entity instanceof LivingEntity && entity.isAlive() && entity.isAttackable() && !entity.isSpectator();
   }
}
