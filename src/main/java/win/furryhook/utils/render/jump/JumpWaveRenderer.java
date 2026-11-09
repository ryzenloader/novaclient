package win.furryhook.utils.render.jump;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
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
import java.util.OptionalDouble;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Render3DUtil;

public final class JumpWaveRenderer {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
   private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withUniform("JumpWaveUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/world/jump_wave"))
      .withVertexShader(Identifier.parse("furryhook:core/jump_wave"))
      .withFragmentShader(Identifier.parse("furryhook:core/jump_wave"))
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   private final SceneSnapshot scene = new SceneSnapshot("furryhook-jump-wave-scene");

   public static RenderPipeline[] pipelines() {
      return new RenderPipeline[]{PIPELINE};
   }

   public void render(Vec3 center, float maxRadius, float ringProgress, float strength, float waveFade, int crestColor) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null && !(maxRadius <= 0.001F) && !(strength <= 0.001F) && !(waveFade <= 0.003F)) {
         RenderTarget target = mc.gameRenderer.mainRenderTarget();
         GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
         if (colorView != null) {
            TextureTarget sceneCopy = this.scene.capture();
            if (sceneCopy != null) {
               GpuDevice device = RenderSystem.getDevice();
               MeshData meshData = this.buildMesh(mc, center, maxRadius);
               GpuBuffer vertexBuffer = null;
               GpuBuffer uniformBuffer = null;

               try {
                  vertexBuffer = device.createBuffer(() -> "Blade Jump Wave Vertices", 40, meshData.vertexBuffer());
                  uniformBuffer = this.uploadUniform(ringProgress, strength, waveFade, crestColor);
                  GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                  GpuTextureView depthView = target.getDepthTextureView();
                  RenderPass pass = depthView != null
                     ? device.createCommandEncoder()
                        .createRenderPass(() -> "Blade Jump Wave Pass", colorView, Optional.empty(), depthView, OptionalDouble.empty())
                     : device.createCommandEncoder().createRenderPass(() -> "Blade Jump Wave Pass", colorView, Optional.empty());

                  try {
                     pass.setPipeline(PIPELINE);
                     pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                     pass.setUniform("JumpWaveUniforms", uniformBuffer);
                     pass.bindTexture("SceneSampler", sceneCopy.getColorTextureView(), sampler);
                     pass.setVertexBuffer(0, vertexBuffer.slice());
                     pass.draw(6, 1, 0, 0);
                  } finally {
                     pass.close();
                  }
               } finally {
                  if (uniformBuffer != null) {
                     uniformBuffer.close();
                  }

                  if (vertexBuffer != null) {
                     vertexBuffer.close();
                  }

                  meshData.close();
               }
            }
         }
      }
   }

   private MeshData buildMesh(Minecraft mc, Vec3 center, float radius) {
      Camera camera = mc.gameRenderer.mainCamera();
      Vec3 cameraPos = camera.position();
      Matrix4f pose = Render3DUtil.cameraViewPose(camera);
      Vec3 xAxis = new Vec3(radius, 0.0, 0.0);
      Vec3 zAxis = new Vec3(0.0, 0.0, radius);
      Vec3 lifted = center.add(0.0, 0.03, 0.0);
      Vector4f p1 = Render3DUtil.toViewSpace(lifted.add(xAxis).add(zAxis), cameraPos, pose);
      Vector4f p2 = Render3DUtil.toViewSpace(lifted.add(xAxis).subtract(zAxis), cameraPos, pose);
      Vector4f p3 = Render3DUtil.toViewSpace(lifted.subtract(xAxis).subtract(zAxis), cameraPos, pose);
      Vector4f p4 = Render3DUtil.toViewSpace(lifted.subtract(xAxis).add(zAxis), cameraPos, pose);
      BufferBuilder builder = new BufferBuilder(
         ByteBufferBuilder.exactlySized(6 * DefaultVertexFormat.POSITION_TEX.getVertexSize()), PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX
      );
      this.addVertex(builder, p1, 1.0F, 1.0F);
      this.addVertex(builder, p2, 1.0F, 0.0F);
      this.addVertex(builder, p3, 0.0F, 0.0F);
      this.addVertex(builder, p1, 1.0F, 1.0F);
      this.addVertex(builder, p3, 0.0F, 0.0F);
      this.addVertex(builder, p4, 0.0F, 1.0F);
      return builder.buildOrThrow();
   }

   private void addVertex(BufferBuilder builder, Vector4f point, float u, float v) {
      builder.addVertex(point.x, point.y, point.z).setUv(u, v);
   }

   private GpuBuffer uploadUniform(float ringProgress, float strength, float fade, int crestColor) {
      GpuDevice device = RenderSystem.getDevice();
      GpuBuffer buffer = device.createBuffer(() -> "Blade Jump Wave UBO", 136, UNIFORM_SIZE);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putVec4(ringProgress, strength, fade, 0.0F)
            .putVec4(
               ColorUtil.red(crestColor) / 255.0F,
               ColorUtil.green(crestColor) / 255.0F,
               ColorUtil.blue(crestColor) / 255.0F,
               ColorUtil.alpha(crestColor) / 255.0F
            )
            .get();
         device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
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

      return buffer;
   }

   public void release() {
      this.scene.release();
   }
}
