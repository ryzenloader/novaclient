package win.furryhook.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import win.furryhook.feature.impl.visual.WorldTweaksFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.post.FullscreenQuad;
import win.furryhook.utils.render.post.PostFx;
import win.furryhook.utils.render.post.PostPipelines;

public final class WorldTweaksRenderer {
   private static final int SKY_UNIFORM_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().get();
   private static final int SATURATION_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
   private final GpuBuffer skyUniforms = uniformBuffer("Blade World Sky UBO", SKY_UNIFORM_SIZE);
   private final GpuBuffer saturationUniforms = uniformBuffer("Blade World Saturation UBO", SATURATION_UNIFORM_SIZE);
   private TextureTarget sceneCopy;
   private TextureTarget skyClouds;

   public void renderSky(WorldTweaksFeature feature, CameraRenderState cameraState) {
      Minecraft minecraft = Minecraft.getInstance();
      RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
      if (valid(target) && cameraState != null && cameraState.initialized) {
         Matrix4f projection = Render3DUtil.levelProjectionCopy();
         if (projection != null) {
            Matrix4f inverseViewProjection = projection.mul(cameraState.viewRotationMatrix).invert();
            this.writeSkyUniforms(feature, inverseViewProjection, target.width, target.height);
            this.ensureSkyClouds(target.width, target.height);
            RenderPipeline cloudsPipeline;
            RenderPipeline compositePipeline;
            switch ((String)feature.skyEffect.getValue()) {
               case "Nebula":
                  cloudsPipeline = PostPipelines.WORLD_SKY_CLOUDS_NEBULA;
                  compositePipeline = PostPipelines.WORLD_SKY_NEBULA;
                  break;
               case "Plasma":
                  cloudsPipeline = PostPipelines.WORLD_SKY_CLOUDS_PLASMA;
                  compositePipeline = PostPipelines.WORLD_SKY_PLASMA;
                  break;
               default:
                  cloudsPipeline = PostPipelines.WORLD_SKY_CLOUDS_DEEP_SPACE;
                  compositePipeline = PostPipelines.WORLD_SKY_DEEP_SPACE;
            }

            GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            GpuSampler cloudSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            RenderPass pass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> "Blade WorldTweaks sky clouds", this.skyClouds.getColorTextureView(), Optional.empty());

            try {
               pass.setPipeline(cloudsPipeline);
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("WorldSkyUniforms", this.skyUniforms);
               pass.bindTexture("DepthSampler", target.getDepthTextureView(), depthSampler);
               drawFullscreen(pass);
            } catch (Throwable var17) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var15) {
                     var17.addSuppressed(var15);
                  }
               }

               throw var17;
            }

            if (pass != null) {
               pass.close();
            }

            pass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> "Blade WorldTweaks sky", target.getColorTextureView(), Optional.empty());

            try {
               pass.setPipeline(compositePipeline);
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("WorldSkyUniforms", this.skyUniforms);
               pass.bindTexture("DepthSampler", target.getDepthTextureView(), depthSampler);
               pass.bindTexture("CloudSampler", this.skyClouds.getColorTextureView(), cloudSampler);
               drawFullscreen(pass);
            } catch (Throwable var16) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var14) {
                     var16.addSuppressed(var14);
                  }
               }

               throw var16;
            }

            if (pass != null) {
               pass.close();
            }
         }
      }
   }

   public void renderSaturation(WorldTweaksFeature feature) {
      Minecraft minecraft = Minecraft.getInstance();
      RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
      if (valid(target)) {
         this.ensureSceneCopy(target.width, target.height);
         RenderSystem.getDevice()
            .createCommandEncoder()
            .copyTextureToTexture(target.getColorTexture(), this.sceneCopy.getColorTexture(), 0, 0, 0, 0, 0, target.width, target.height);
         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer data = Std140Builder.onStack(stack, SATURATION_UNIFORM_SIZE)
               .putVec4(Math.clamp(1.0F + feature.saturationAmount.getValue().floatValue(), 0.0F, 3.0F), 0.0F, 0.0F, 0.0F)
               .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.saturationUniforms.slice(), data);
         } catch (Throwable var10) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var8) {
                  var10.addSuppressed(var8);
               }
            }

            throw var10;
         }

         if (stack != null) {
            stack.close();
         }

         RenderPass pass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Blade WorldTweaks saturation", target.getColorTextureView(), Optional.empty());

         try {
            pass.setPipeline(PostPipelines.WORLD_SATURATION);
            RenderSystem.bindDefaultUniforms(pass);
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            pass.setUniform("SaturationUniforms", this.saturationUniforms);
            pass.bindTexture("SceneSampler", this.sceneCopy.getColorTextureView(), sampler);
            drawFullscreen(pass);
         } catch (Throwable var9) {
            if (pass != null) {
               try {
                  pass.close();
               } catch (Throwable var7) {
                  var9.addSuppressed(var7);
               }
            }

            throw var9;
         }

         if (pass != null) {
            pass.close();
         }
      }
   }

   private void writeSkyUniforms(WorldTweaksFeature feature, Matrix4f inverseViewProjection, int width, int height) {
      int primary = feature.skyColor1.getValue();
      int secondary = feature.skyColor2.getValue();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, SKY_UNIFORM_SIZE)
            .putMat4f(inverseViewProjection)
            .putVec4(ColorUtil.red(primary) / 255.0F, ColorUtil.green(primary) / 255.0F, ColorUtil.blue(primary) / 255.0F, 1.0F)
            .putVec4(ColorUtil.red(secondary) / 255.0F, ColorUtil.green(secondary) / 255.0F, ColorUtil.blue(secondary) / 255.0F, 1.0F)
            .putVec4(
               PostFx.shaderTime(),
               feature.skyIntensity.getValue().floatValue(),
               feature.skySpeed.getValue().floatValue(),
               RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1.0F : 0.0F
            )
            .putVec4(1.0F / width, 1.0F / height, 0.0F, 0.0F)
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.skyUniforms.slice(), data);
      } catch (Throwable var11) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }
         }

         throw var11;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private static void drawFullscreen(RenderPass pass) {
      pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
      pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
   }

   private void ensureSceneCopy(int width, int height) {
      if (this.sceneCopy == null || this.sceneCopy.width != width || this.sceneCopy.height != height) {
         if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
         }

         this.sceneCopy = new TextureTarget("furryhook-world-tweaks-scene", width, height, false, PostPipelines.EFFECT_FORMAT);
      }
   }

   private void ensureSkyClouds(int width, int height) {
      int halfWidth = Math.max(1, width / 2);
      int halfHeight = Math.max(1, height / 2);
      if (this.skyClouds == null || this.skyClouds.width != halfWidth || this.skyClouds.height != halfHeight) {
         if (this.skyClouds != null) {
            this.skyClouds.destroyBuffers();
         }

         this.skyClouds = new TextureTarget("furryhook-world-tweaks-sky-clouds", halfWidth, halfHeight, false, PostPipelines.SKY_CLOUDS_FORMAT);
      }
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

   private static GpuBuffer uniformBuffer(String label, int size) {
      return RenderSystem.getDevice().createBuffer(() -> label, 136, size);
   }

   public void release() {
      if (this.sceneCopy != null) {
         this.sceneCopy.destroyBuffers();
         this.sceneCopy = null;
      }

      if (this.skyClouds != null) {
         this.skyClouds.destroyBuffers();
         this.skyClouds = null;
      }

      this.skyUniforms.close();
      this.saturationUniforms.close();
   }
}
