package win.furryhook.context;

import lombok.Generated;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class RotationContext implements MinecraftContext {
   private static final float MOUSE_TURN_SCALE = 0.15F;
   private static float freeYaw;
   private static float freePitch;
   private static boolean active;
   private static boolean hasLastAngle;
   private static float serverYaw;
   private static float serverPitch;
   private static float lastYaw;
   private static float lastPitch;
   private static float lastHeadYaw;
   private static float lastBodyYaw;
   private static Object owner;
   private static int ownerPriority;
   private static long lastContestMs;
   private static final long OWNER_TIMEOUT_MS = 250L;
   public static final int PRIORITY_AIM = 10;
   public static final int PRIORITY_PLACE = 5;

   public static boolean isOwner(Object o) {
      return owner == o;
   }

   public static boolean claim(Object claimant, int priority) {
      if (owner == claimant) {
         ownerPriority = priority;
         return true;
      } else if (owner != null && priority <= ownerPriority && System.currentTimeMillis() - lastContestMs <= 250L) {
         lastContestMs = System.currentTimeMillis();
         return false;
      } else {
         owner = claimant;
         ownerPriority = priority;
         lastContestMs = System.currentTimeMillis();
         return true;
      }
   }

   public static void release(Object claimant) {
      if (owner == claimant) {
         owner = null;
         ownerPriority = 0;
      }
   }

   public static void clearIfOwner(Object claimant) {
      if (owner == null || owner == claimant) {
         owner = null;
         ownerPriority = 0;
         clear();
      }
   }

   public static void disengageIfOwner(Object claimant) {
      if (owner == null || owner == claimant) {
         disengage();
      }
   }

   private RotationContext() {
   }

   public static void apply(float yawDelta, float pitchDelta) {
      LocalPlayer player = mc.player;
      if (player != null) {
         if (!active) {
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
            serverYaw = player.getYRot();
            serverPitch = player.getXRot();
            active = true;
         }

         rememberRenderAngles(player);
         serverYaw += yawDelta;
         serverPitch = Mth.clamp(serverPitch + pitchDelta, -90.0F, 90.0F);
         player.setYRot(serverYaw);
         player.setXRot(serverPitch);
         player.yHeadRot = serverYaw;
         player.yBodyRot = serverYaw;
      }
   }

   public static void setRotation(float yaw, float pitch) {
      LocalPlayer player = mc.player;
      if (player != null) {
         if (!active) {
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
            serverYaw = player.getYRot();
            serverPitch = player.getXRot();
            active = true;
         }

         float targetPitch = Mth.clamp(pitch, -90.0F, 90.0F);
         float deltaYaw = Mth.wrapDegrees(yaw - serverYaw);
         float deltaPitch = targetPitch - serverPitch;
         double gcdStep = gcdStep();
         if (gcdStep >= 1.0E-4) {
            deltaYaw = (float)(Math.round(deltaYaw / gcdStep) * gcdStep);
            deltaPitch = (float)(Math.round(deltaPitch / gcdStep) * gcdStep);
         }

         apply(deltaYaw, deltaPitch);
      }
   }

   public static double gcdStep() {
      double sensitivity = (Double)mc.options.sensitivity().get();
      double f = sensitivity * 0.6 + 0.2;
      return f * f * f * 1.2;
   }

   public static void rotateTo(LocalPlayer player, Entity target) {
      Vec3 delta = target.getEyePosition().subtract(player.getEyePosition());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      setRotation(yaw, pitch);
   }

   public static void rotateToPosition(LocalPlayer player, Vec3 pos) {
      Vec3 delta = pos.subtract(player.getEyePosition());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      setRotation(yaw, pitch);
   }

   public static float getFovToEntity(LocalPlayer player, Entity entity) {
      double diffX = entity.getX() - player.getX();
      double diffZ = entity.getZ() - player.getZ();
      float yaw = (float)(Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
      float deltaYaw = Mth.wrapDegrees(yaw - (active ? serverYaw : player.getYRot()));
      return Math.abs(deltaYaw);
   }

   public static void syncFreeLook(float yaw, float pitch) {
      if (!active) {
         freeYaw = yaw;
         freePitch = pitch;
      }
   }

   public static boolean onMouseTurn(double yawInput, double pitchInput) {
      if (!active) {
         return false;
      }

      freeYaw += (float)yawInput * 0.15F;
      freePitch = Mth.clamp(freePitch + (float)pitchInput * 0.15F, -90.0F, 90.0F);
      return true;
   }

   public static void applyRenderInterpolation() {
      if (active && hasLastAngle) {
         LocalPlayer player = mc.player;
         if (player == null) {
            hasLastAngle = false;
         } else {
            player.yRotO = lastYaw;
            player.xRotO = lastPitch;
            player.yHeadRotO = lastHeadYaw;
            player.yBodyRotO = lastBodyYaw;
         }
      }
   }

   public static void disengage() {
      if (active) {
         LocalPlayer player = mc.player;
         if (player == null) {
            clear();
         } else {
            float deltaYaw = Mth.wrapDegrees(player.getYRot() - serverYaw);
            float deltaPitch = player.getXRot() - serverPitch;
            if (Math.abs(deltaYaw) < 2.0F && Math.abs(deltaPitch) < 2.0F) {
               clear();
            } else {
               rememberRenderAngles(player);
               serverYaw += deltaYaw * 0.4F;
               serverPitch = Mth.clamp(serverPitch + deltaPitch * 0.4F, -90.0F, 90.0F);
               player.setYRot(serverYaw);
               player.setXRot(serverPitch);
               player.yHeadRot = serverYaw;
               player.yBodyRot = serverYaw;
            }
         }
      }
   }

   public static void clear() {
      LocalPlayer player = mc.player;
      if (active && player != null) {
         player.setYRot(freeYaw);
         player.setXRot(freePitch);
         player.yHeadRot = freeYaw;
         player.yBodyRot = freeYaw;
         player.yRotO = freeYaw;
         player.xRotO = freePitch;
         player.yHeadRotO = freeYaw;
         player.yBodyRotO = freeYaw;
      }

      active = false;
      hasLastAngle = false;
      owner = null;
      ownerPriority = 0;
      if (player != null) {
         serverYaw = player.getYRot();
         serverPitch = player.getXRot();
         freeYaw = player.getYRot();
         freePitch = player.getXRot();
      }
   }

   private static void rememberRenderAngles(LocalPlayer player) {
      lastYaw = player.getYRot();
      lastPitch = player.getXRot();
      lastHeadYaw = player.yHeadRot;
      lastBodyYaw = player.yBodyRot;
      hasLastAngle = true;
   }

   @Generated
   public static float getFreeYaw() {
      return freeYaw;
   }

   @Generated
   public static float getFreePitch() {
      return freePitch;
   }

   @Generated
   public static boolean isActive() {
      return active;
   }

   @Generated
   public static float getServerYaw() {
      return serverYaw;
   }

   @Generated
   public static float getServerPitch() {
      return serverPitch;
   }
}
