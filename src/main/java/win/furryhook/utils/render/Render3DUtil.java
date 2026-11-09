package win.furryhook.utils.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class Render3DUtil {
   private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();
   private static boolean levelProjectionCaptured;

   private Render3DUtil() {
   }

   public static Vec3 interpolatedPosition(Entity entity, float tickDelta) {
      return new Vec3(
         Mth.lerp(tickDelta, entity.xOld, entity.getX()), Mth.lerp(tickDelta, entity.yOld, entity.getY()), Mth.lerp(tickDelta, entity.zOld, entity.getZ())
      );
   }

   public static Matrix4f buildBillboardPose(Camera camera, Vec3 worldPos, double offsetY, float zRotationDegrees) {
      Vec3 cameraPos = camera.position();
      PoseStack poseStack = new PoseStack();
      poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
      poseStack.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0F));
      poseStack.translate(worldPos.x - cameraPos.x, worldPos.y - cameraPos.y + offsetY, worldPos.z - cameraPos.z);
      poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
      poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
      if (zRotationDegrees != 0.0F) {
         poseStack.mulPose(Axis.ZP.rotationDegrees(zRotationDegrees));
      }

      return new Matrix4f(poseStack.last().pose());
   }

   public static Matrix4f cameraViewPose(Camera camera) {
      return camera.getViewRotationMatrix(new Matrix4f());
   }

   public static Vector4f toViewSpace(Vec3 point, Vec3 cameraPos, Matrix4f pose) {
      return new Vector4f((float)(point.x - cameraPos.x), (float)(point.y - cameraPos.y), (float)(point.z - cameraPos.z), 1.0F).mul(pose);
   }

   public static Vector4f toViewDirection(Vec3 direction, Matrix4f pose) {
      return new Vector4f((float)direction.x, (float)direction.y, (float)direction.z, 0.0F).mul(pose);
   }

   public static void captureLevelProjection(Matrix4fc projection) {
      LEVEL_PROJECTION.set(projection);
      levelProjectionCaptured = true;
   }

   public static Matrix4f levelProjectionCopy() {
      return levelProjectionCaptured ? new Matrix4f(LEVEL_PROJECTION) : null;
   }

   public static Render3DUtil.ScreenPoint projectToScreen(Minecraft mc, Vec3 pos) {
      if (pos != null && mc.getWindow() != null && mc.gameRenderer != null && levelProjectionCaptured) {
         CameraRenderState cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
         if (!cameraState.initialized) {
            return null;
         }

         int guiWidth = mc.getWindow().getGuiScaledWidth();
         int guiHeight = mc.getWindow().getGuiScaledHeight();
         if (guiWidth > 0 && guiHeight > 0) {
            Vector4f clip = new Vector4f((float)(pos.x - cameraState.pos.x()), (float)(pos.y - cameraState.pos.y()), (float)(pos.z - cameraState.pos.z()), 1.0F);
            cameraState.viewRotationMatrix.transform(clip);
            LEVEL_PROJECTION.transform(clip);
            if (clip.w <= 1.0E-4F) {
               return null;
            } else {
               float ndcX = clip.x / clip.w;
               float ndcY = clip.y / clip.w;
               if (!(Math.abs(ndcX) > 2.0F) && !(Math.abs(ndcY) > 2.0F)) {
                  float screenX = (ndcX + 1.0F) * 0.5F * guiWidth;
                  float screenY = (1.0F - ndcY) * 0.5F * guiHeight;
                  return new Render3DUtil.ScreenPoint(screenX, screenY);
               } else {
                  return null;
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static Render3DUtil.ScreenBounds projectBoxBounds(Minecraft mc, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      if (levelProjectionCaptured && mc.gameRenderer != null) {
         Window window = mc.getWindow();
         if (window == null) {
            return null;
         }

         CameraRenderState cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
         if (!cameraState.initialized) {
            return null;
         }

         int guiWidth = window.getGuiScaledWidth();
         int guiHeight = window.getGuiScaledHeight();
         if (guiWidth > 0 && guiHeight > 0) {
            double cameraX = cameraState.pos.x();
            double cameraY = cameraState.pos.y();
            double cameraZ = cameraState.pos.z();
            Matrix4f viewRotation = cameraState.viewRotationMatrix;
            float halfWidth = guiWidth * 0.5F;
            float halfHeight = guiHeight * 0.5F;
            Vector4f corner = new Vector4f();
            float boundsMinX = Float.MAX_VALUE;
            float boundsMinY = Float.MAX_VALUE;
            float boundsMaxX = -Float.MAX_VALUE;
            float boundsMaxY = -Float.MAX_VALUE;
            boolean projected = false;

            for (int index = 0; index < 8; index++) {
               double x = (index & 1) == 0 ? minX : maxX;
               double y = (index & 2) == 0 ? minY : maxY;
               double z = (index & 4) == 0 ? minZ : maxZ;
               corner.set((float)(x - cameraX), (float)(y - cameraY), (float)(z - cameraZ), 1.0F);
               viewRotation.transform(corner);
               LEVEL_PROJECTION.transform(corner);
               if (!(corner.w <= 1.0E-4F)) {
                  float ndcX = corner.x / corner.w;
                  float ndcY = corner.y / corner.w;
                  if (!(Math.abs(ndcX) > 2.0F) && !(Math.abs(ndcY) > 2.0F)) {
                     float screenX = (ndcX + 1.0F) * halfWidth;
                     float screenY = (1.0F - ndcY) * halfHeight;
                     if (screenX < boundsMinX) {
                        boundsMinX = screenX;
                     }

                     if (screenY < boundsMinY) {
                        boundsMinY = screenY;
                     }

                     if (screenX > boundsMaxX) {
                        boundsMaxX = screenX;
                     }

                     if (screenY > boundsMaxY) {
                        boundsMaxY = screenY;
                     }

                     projected = true;
                  }
               }
            }

            return projected ? new Render3DUtil.ScreenBounds(boundsMinX, boundsMinY, boundsMaxX, boundsMaxY) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static double lerpX(Entity entity, float tickDelta) {
      return Mth.lerp(tickDelta, entity.xOld, entity.getX());
   }

   public static double lerpY(Entity entity, float tickDelta) {
      return Mth.lerp(tickDelta, entity.yOld, entity.getY());
   }

   public static double lerpZ(Entity entity, float tickDelta) {
      return Mth.lerp(tickDelta, entity.zOld, entity.getZ());
   }

   public record ScreenBounds(float minX, float minY, float maxX, float maxY) {
      public float width() {
         return this.maxX - this.minX;
      }

      public float height() {
         return this.maxY - this.minY;
      }
   }

   public record ScreenPoint(float x, float y) {
   }
}
