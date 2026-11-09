package win.furryhook.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import win.furryhook.feature.impl.visual.RtxFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.post.PostFx;
import win.furryhook.utils.render.post.PostPipelines;
import win.furryhook.utils.render.post.PostTarget;

public final class RtxRayRenderer {
   private static final float LIGHT_DISTANCE = 512.0F;
   private static final float NEAR_PLANE_GUARD = 0.5F;
   private static final float OFFSCREEN_REACH = 0.35F;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
   private final GpuBuffer uniforms = PostFx.createUniforms("Blade RTX Ray UBO", UNIFORM_SIZE);
   private final PostTarget occlusion = new PostTarget("furryhook-rtx-ray-occlusion", PostPipelines.RTX_RAY_FORMAT, false);
   private final Vector4f lightClip = new Vector4f();

   public void render(RtxFeature feature, CameraRenderState cameraState, SkyRenderState skyState) {
      Minecraft minecraft = Minecraft.getInstance();
      RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
      if (valid(mainTarget) && cameraState != null && cameraState.initialized) {
         Matrix4f levelProjection = Render3DUtil.levelProjectionCopy();
         if (levelProjection != null) {
            Matrix4f viewProjection = levelProjection.mul(cameraState.viewRotationMatrix);
            float sunAngle = skyState != null ? skyState.sunAngle : 0.0F;
            float sunUp = (float)Math.cos(sunAngle);
            float sunEast = -((float)Math.sin(sunAngle));
            boolean daylight = sunUp >= 0.0F;
            if (daylight || feature.moonRays.getValue()) {
               float directionX = daylight ? sunEast : -sunEast;
               float directionY = daylight ? sunUp : -sunUp;
               float elevation = Math.abs(sunUp);
               float horizonFade = Math.clamp(elevation * 5.0F, 0.0F, 1.0F);
               float elevationShape = 0.4F + 0.6F * (1.0F - elevation);
               float sourceStrength = horizonFade * elevationShape * (daylight ? 1.0F : 0.32F);
               if (!(sourceStrength <= 0.001F)) {
                  this.lightClip.set(directionX * 512.0F, directionY * 512.0F, 0.0F, 1.0F);
                  viewProjection.transform(this.lightClip);
                  if (!(this.lightClip.w <= 0.5F)) {
                     float lightU = this.lightClip.x / this.lightClip.w * 0.5F + 0.5F;
                     float lightV = this.lightClip.y / this.lightClip.w * 0.5F + 0.5F;
                     float screenFade = offscreenFade(lightU, lightV);
                     if (!(screenFade <= 0.001F)) {
                        int halfWidth = Math.max(1, mainTarget.width / 2);
                        int halfHeight = Math.max(1, mainTarget.height / 2);
                        TextureTarget occlusionTarget = this.occlusion.ensure(halfWidth, halfHeight);
                        if (occlusionTarget != null && occlusionTarget.getColorTextureView() != null) {
                           float aspect = mainTarget.height > 0 ? (float)mainTarget.width / mainTarget.height : 1.0F;
                           this.writeUniforms(feature, lightU, lightV, aspect, screenFade * sourceStrength);
                           PostFx.pass("Blade RTX Ray Occlusion", PostPipelines.RTX_RAY_OCCLUSION, occlusionTarget.getColorTextureView(), pass -> {
                              pass.setUniform("RtxRayUniforms", this.uniforms);
                              pass.bindTexture("SceneSampler", mainTarget.getColorTextureView(), PostFx.linearSampler());
                              pass.bindTexture("DepthSampler", mainTarget.getDepthTextureView(), PostFx.nearestSampler());
                           });
                           PostFx.pass("Blade RTX Rays", PostPipelines.RTX_RAYS, mainTarget, pass -> {
                              pass.setUniform("RtxRayUniforms", this.uniforms);
                              pass.bindTexture("OcclusionSampler", occlusionTarget.getColorTextureView(), PostFx.linearSampler());
                           });
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void writeUniforms(RtxFeature feature, float lightU, float lightV, float aspect, float strengthScale) {
      int tint = feature.rayColor.getValue();
      float samples = feature.raySamples.getValue().floatValue();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putVec4(lightU, lightV, aspect, strengthScale)
            .putVec4(ColorUtil.red(tint) / 255.0F, ColorUtil.green(tint) / 255.0F, ColorUtil.blue(tint) / 255.0F, feature.rayTint.getValue().floatValue())
            .putVec4(
               samples,
               feature.rayDensity.getValue().floatValue(),
               feature.rayDecay.getValue().floatValue(),
               1.0F / Math.max(feature.raySpread.getValue().floatValue() * feature.raySpread.getValue().floatValue(), 0.0025F)
            )
            .putVec4(0.72F, feature.rayEmissive.getValue().floatValue(), feature.rayStrength.getValue().floatValue() * 2.0F, PostFx.shaderTime())
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
      } catch (Throwable var12) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var11) {
               var12.addSuppressed(var11);
            }
         }

         throw var12;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private static float offscreenFade(float u, float v) {
      float outsideU = Math.max(0.0F, Math.abs(u - 0.5F) - 0.5F);
      float outsideV = Math.max(0.0F, Math.abs(v - 0.5F) - 0.5F);
      float outside = (float)Math.sqrt(outsideU * outsideU + outsideV * outsideV);
      return 1.0F - Math.clamp(outside / 0.35F, 0.0F, 1.0F);
   }

   private static boolean valid(RenderTarget target) {
      return target != null
         && target.width > 0
         && target.height > 0
         && target.getColorTexture() != null
         && target.getColorTextureView() != null
         && target.getDepthTexture() != null
         && target.getDepthTextureView() != null;
   }

   public void release() {
      this.occlusion.release();
      this.uniforms.close();
   }
}
