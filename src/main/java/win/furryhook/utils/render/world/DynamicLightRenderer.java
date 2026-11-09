package win.furryhook.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import win.furryhook.feature.impl.player.FullBrightFeature;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.post.PostFx;
import win.furryhook.utils.render.post.PostPipelines;
import win.furryhook.utils.render.post.PostTarget;

public final class DynamicLightRenderer {
   private static final int MAX_LIGHTS = 16;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putMat4f().putVec4().get();
   private static final int LIGHTS_SIZE = lightsSize();
   private final GpuBuffer uniforms = PostFx.createUniforms("Blade Point Light UBO", UNIFORM_SIZE);
   private final GpuBuffer lights = PostFx.createUniforms("Blade Point Light Array UBO", LIGHTS_SIZE);
   private final PostTarget sceneCopy = new PostTarget("furryhook-dynamic-light-scene", PostPipelines.EFFECT_FORMAT, false);

   public void render(FullBrightFeature feature, CameraRenderState cameraState, float partialTick) {
      Minecraft minecraft = Minecraft.getInstance();
      RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
      if (valid(mainTarget) && cameraState != null && cameraState.initialized) {
         Vec3 cameraPosition = new Vec3(cameraState.pos.x(), cameraState.pos.y(), cameraState.pos.z());
         List<DynamicLightManager.RenderLight> renderLights = DynamicLightManager.INSTANCE.shaderLights(feature, partialTick, cameraPosition);
         if (!renderLights.isEmpty()) {
            TextureTarget scene = this.sceneCopy.ensure(mainTarget.width, mainTarget.height);
            if (scene != null && scene.getColorTexture() != null) {
               RenderSystem.getDevice()
                  .createCommandEncoder()
                  .copyTextureToTexture(mainTarget.getColorTexture(), scene.getColorTexture(), 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
               Matrix4f levelProjection = Render3DUtil.levelProjectionCopy();
               if (levelProjection != null) {
                  Matrix4f inverseViewProjection = levelProjection.mul(cameraState.viewRotationMatrix).invert();
                  this.writeUniforms(
                     inverseViewProjection,
                     feature.lightIntensity.getValue().floatValue(),
                     renderLights.size(),
                     RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
                  );
                  this.writeLights(renderLights, cameraPosition);
                  PostFx.pass("Blade Dynamic Point Lights", PostPipelines.POINT_LIGHTS, mainTarget, pass -> {
                     pass.setUniform("PointLightUniforms", this.uniforms);
                     pass.setUniform("PointLights", this.lights);
                     pass.bindTexture("SceneSampler", scene.getColorTextureView(), PostFx.linearSampler());
                     pass.bindTexture("DepthSampler", mainTarget.getDepthTextureView(), PostFx.nearestSampler());
                  });
               }
            }
         }
      }
   }

   private void writeUniforms(Matrix4fc inverseViewProjection, float intensity, int lightCount, boolean depthZeroToOne) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putMat4f(inverseViewProjection)
            .putVec4(PostFx.shaderTime(), intensity, lightCount, depthZeroToOne ? 1.0F : 0.0F)
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeLights(List<DynamicLightManager.RenderLight> renderLights, Vec3 cameraPosition) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         Std140Builder builder = Std140Builder.onStack(stack, LIGHTS_SIZE);

         for (int index = 0; index < 16; index++) {
            if (index < renderLights.size()) {
               DynamicLightManager.RenderLight light = renderLights.get(index);
               Vec3 relative = light.position().subtract(cameraPosition);
               builder.putVec4((float)relative.x, (float)relative.y, (float)relative.z, light.radius());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for (int index = 0; index < 16; index++) {
            if (index < renderLights.size()) {
               DynamicLightManager.RenderLight light = renderLights.get(index);
               builder.putVec4((light.rgb() >> 16 & 0xFF) / 255.0F, (light.rgb() >> 8 & 0xFF) / 255.0F, (light.rgb() & 0xFF) / 255.0F, light.flicker());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for (int index = 0; index < 16; index++) {
            builder.putVec4(index < renderLights.size() ? renderLights.get(index).phase() : 0.0F, 0.0F, 0.0F, 0.0F);
         }

         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.lights.slice(), builder.get());
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
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

   private static int lightsSize() {
      Std140SizeCalculator calculator = new Std140SizeCalculator();

      for (int index = 0; index < 48; index++) {
         calculator.putVec4();
      }

      return calculator.get();
   }

   public void release() {
      this.sceneCopy.release();
      this.uniforms.close();
      this.lights.close();
   }
}
