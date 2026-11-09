package win.furryhook.utils.render.target;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.HurtUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.Textures;

public final class AuraMarkerRenderer {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
   private static final BindGroupLayout MARKER_LAYOUT = BindGroupLayout.builder()
      .withSampler("texSampler")
      .withUniform("params", UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/world/aura_marker"))
      .withVertexShader(Identifier.parse("furryhook:core/aura_marker"))
      .withFragmentShader(Identifier.parse("furryhook:core/aura_marker"))
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(MARKER_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
      .withCull(false)
      .build();
   private GpuBuffer paramsBuffer;
   private LivingEntity lastTarget;
   private float alpha;
   private long lastFrameTime;

   public static RenderPipeline[] pipelines() {
      return new RenderPipeline[]{PIPELINE};
   }

   public void render(LivingEntity activeTarget, float tickDelta, int baseColor) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.player != null && mc.level != null && mc.gameRenderer != null) {
         this.updateAnimation(activeTarget);
         LivingEntity target = this.lastTarget;
         if (target != null) {
            if (this.alpha <= 0.01F) {
               this.reset();
            } else {
               AuraMarkerRenderer.MarkerGeometry geometry = this.markerGeometry(mc, target, tickDelta);
               if (geometry != null) {
                  this.renderMarker(mc, geometry, HurtUtil.blend(baseColor, target, this.alpha));
               }
            }
         }
      } else {
         this.reset();
      }
   }

   public void reset() {
      this.lastTarget = null;
      this.alpha = 0.0F;
      this.lastFrameTime = 0L;
   }

   private void updateAnimation(LivingEntity activeTarget) {
      long now = System.currentTimeMillis();
      float delta = this.lastFrameTime == 0L ? 0.016F : (float)Math.min(100L, now - this.lastFrameTime) / 1000.0F;
      this.lastFrameTime = now;
      if (valid(activeTarget)) {
         this.lastTarget = activeTarget;
      }

      float targetAlpha = valid(activeTarget) ? 1.0F : 0.0F;
      float step = Mth.clamp(delta * 2.5F, 0.0F, 1.0F);
      this.alpha = this.alpha + (targetAlpha - this.alpha) * step;
   }

   private AuraMarkerRenderer.MarkerGeometry markerGeometry(Minecraft mc, LivingEntity target, float tickDelta) {
      Camera camera = mc.gameRenderer.mainCamera();
      if (camera != null && camera.isInitialized()) {
         Vec3 position = Render3DUtil.interpolatedPosition(target, tickDelta);
         float widthScale;
         if (target.getBbWidth() < 1.0F) {
            widthScale = 0.95F;
         } else if (target.getBbWidth() > 2.0F) {
            widthScale = 1.45F;
         } else {
            widthScale = 1.0F;
         }

         float halfSize = 0.5F * widthScale * this.alpha * HurtUtil.scale(target, 0.12F);
         if (halfSize <= 0.001F) {
            return null;
         }

         Matrix4f pose = Render3DUtil.buildBillboardPose(
            camera, position, target.getBbHeight() / 2.0, (float)(Math.sin(System.currentTimeMillis() / 1000.0) * 360.0)
         );
         return new AuraMarkerRenderer.MarkerGeometry(pose, halfSize);
      } else {
         return null;
      }
   }

   private void renderMarker(Minecraft mc, AuraMarkerRenderer.MarkerGeometry geometry, int color) {
      RenderTarget target = mc.gameRenderer.mainRenderTarget();
      GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
      if (colorView != null) {
         AbstractTexture texture = mc.getTextureManager().getTexture(Textures.TARGET);
         GpuTextureView textureView = texture != null ? texture.getTextureView() : null;
         if (textureView != null) {
            this.ensureParamsBuffer();
            this.writeParams(color);
            MeshData mesh = this.buildMesh(geometry);
            GpuBuffer vertexBuffer = null;

            try {
               vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Blade Aura Marker Vertices", 32, mesh.vertexBuffer());
               GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
               RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blade Aura Marker Pass", colorView, Optional.empty());

               try {
                  pass.setPipeline(PIPELINE);
                  pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                  pass.setUniform("params", this.paramsBuffer);
                  pass.bindTexture("texSampler", textureView, sampler);
                  pass.setVertexBuffer(0, vertexBuffer.slice());
                  pass.draw(4, 1, 0, 0);
               } catch (Throwable var19) {
                  if (pass != null) {
                     try {
                        pass.close();
                     } catch (Throwable var18) {
                        var19.addSuppressed(var18);
                     }
                  }

                  throw var19;
               }

               if (pass != null) {
                  pass.close();
               }
            } finally {
               if (vertexBuffer != null) {
                  vertexBuffer.close();
               }

               mesh.close();
            }
         }
      }
   }

   private MeshData buildMesh(AuraMarkerRenderer.MarkerGeometry geometry) {
      float halfSize = geometry.halfSize;
      BufferBuilder builder = new BufferBuilder(
         ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize()),
         PrimitiveTopology.TRIANGLE_STRIP,
         DefaultVertexFormat.POSITION_TEX
      );
      builder.addVertex(geometry.pose, -halfSize, -halfSize, 0.0F).setUv(0.0F, 1.0F);
      builder.addVertex(geometry.pose, -halfSize, halfSize, 0.0F).setUv(0.0F, 0.0F);
      builder.addVertex(geometry.pose, halfSize, -halfSize, 0.0F).setUv(1.0F, 1.0F);
      builder.addVertex(geometry.pose, halfSize, halfSize, 0.0F).setUv(1.0F, 0.0F);
      return builder.buildOrThrow();
   }

   private void ensureParamsBuffer() {
      if (this.paramsBuffer == null) {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "Blade Aura Marker UBO", 136, UNIFORM_SIZE);
      }
   }

   private void writeParams(int color) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putVec4(ColorUtil.red(color) / 255.0F, ColorUtil.green(color) / 255.0F, ColorUtil.blue(color) / 255.0F, ColorUtil.alpha(color) / 255.0F)
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.paramsBuffer.slice(), data);
      } catch (Throwable var6) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private static boolean valid(LivingEntity entity) {
      return entity != null && entity.isAlive() && !entity.isRemoved();
   }

   public void release() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   private record MarkerGeometry(Matrix4f pose, float halfSize) {
   }
}
