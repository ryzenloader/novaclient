package win.furryhook.utils.render.world;

import com.mojang.blaze3d.GpuFormat;
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
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.post.EmissiveBloom;
import win.furryhook.utils.render.post.PostFx;

public final class GlowStripRenderer {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().get();
   private static final float HALO_PAD = 3.0F;
   private static final float MIN_WIDTH = 0.002F;
   private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
      .withSampler("DepthSampler")
      .withUniform("GlowStripUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline SCENE_PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/world/glow_strip"))
      .withVertexShader(Identifier.parse("furryhook:core/glow_strip"))
      .withFragmentShader(Identifier.parse("furryhook:core/glow_strip"))
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(LAYOUT)
      .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   private static final RenderPipeline EMISSIVE_PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/world/glow_strip_emissive"))
      .withVertexShader(Identifier.parse("furryhook:core/glow_strip"))
      .withFragmentShader(Identifier.parse("furryhook:core/glow_strip"))
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(LAYOUT)
      .withColorTargetState(new ColorTargetState(Optional.of(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)), EmissiveBloom.FORMAT, 15))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   private final EmissiveBloom bloom;
   private TextureTarget depthCopy;

   public GlowStripRenderer(String name) {
      this.bloom = new EmissiveBloom(name);
   }

   public static RenderPipeline[] pipelines() {
      return new RenderPipeline[]{SCENE_PIPELINE, EMISSIVE_PIPELINE};
   }

   public static List<Vec3> smooth(List<Vec3> spine, int subdivisions) {
      int count = spine.size();
      if (count >= 3 && subdivisions >= 2) {
         List<Vec3> smoothed = new ArrayList<>((count - 1) * subdivisions + 1);

         for (int index = 0; index < count - 1; index++) {
            Vec3 previous = spine.get(Math.max(0, index - 1));
            Vec3 start = spine.get(index);
            Vec3 end = spine.get(index + 1);
            Vec3 next = spine.get(Math.min(count - 1, index + 2));

            for (int step = 0; step < subdivisions; step++) {
               smoothed.add(catmullRom(previous, start, end, next, (double)step / subdivisions));
            }
         }

         smoothed.add(spine.getLast());
         return smoothed;
      } else {
         return spine;
      }
   }

   private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
      double t2 = t * t;
      double t3 = t2 * t;
      double w0 = -0.5 * t3 + t2 - 0.5 * t;
      double w1 = 1.5 * t3 - 2.5 * t2 + 1.0;
      double w2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
      double w3 = 0.5 * t3 - 0.5 * t2;
      return new Vec3(
         p0.x * w0 + p1.x * w1 + p2.x * w2 + p3.x * w3, p0.y * w0 + p1.y * w1 + p2.y * w2 + p3.y * w3, p0.z * w0 + p1.z * w1 + p2.z * w2 + p3.z * w3
      );
   }

   public void render(List<GlowStripRenderer.Strip> strips, GlowStripRenderer.Style style, EmissiveBloom.Settings bloomSettings) {
      Minecraft minecraft = Minecraft.getInstance();
      if (!strips.isEmpty() && minecraft.level != null) {
         RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
         if (target != null
            && target.getColorTextureView() != null
            && target.getDepthTexture() != null
            && target.getDepthTextureView() != null
            && target.width > 0
            && target.height > 0) {
            this.ensureDepthCopy(target.width, target.height);
            if (this.depthCopy != null && this.depthCopy.getDepthTextureView() != null) {
               RenderSystem.getDevice()
                  .createCommandEncoder()
                  .copyTextureToTexture(target.getDepthTexture(), this.depthCopy.getDepthTexture(), 0, 0, 0, 0, 0, target.width, target.height);
               MeshData mesh = this.buildMesh(minecraft, strips);
               if (mesh != null) {
                  int vertexCount = mesh.drawState().vertexCount();
                  GpuDevice device = RenderSystem.getDevice();
                  GpuBuffer vertexBuffer = null;
                  GpuBuffer sceneUniforms = null;
                  GpuBuffer emissiveUniforms = null;

                  try {
                     vertexBuffer = device.createBuffer(() -> "Blade Glow Strip Vertices", 40, mesh.vertexBuffer());
                     sceneUniforms = this.uploadUniforms(target.width, target.height, style, false);
                     GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                     RenderPass pass = device.createCommandEncoder()
                        .createRenderPass(
                           () -> "Blade Glow Strips", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
                        );

                     try {
                        this.draw(pass, SCENE_PIPELINE, sceneUniforms, depthSampler, vertexBuffer, vertexCount);
                     } catch (Throwable var26) {
                        if (pass != null) {
                           try {
                              pass.close();
                           } catch (Throwable var24) {
                              var26.addSuppressed(var24);
                           }
                        }

                        throw var26;
                     }

                     if (pass != null) {
                        pass.close();
                     }

                     if (!bloomSettings.active()) {
                        return;
                     }

                     TextureTarget emissive = this.bloom.begin(target.width, target.height);
                     if (emissive != null) {
                        emissiveUniforms = this.uploadUniforms(emissive.width, emissive.height, style, true);
                        RenderPass passx = device.createCommandEncoder()
                           .createRenderPass(() -> "Blade Glow Strip Emissive", emissive.getColorTextureView(), Optional.empty());

                        try {
                           this.draw(passx, EMISSIVE_PIPELINE, emissiveUniforms, depthSampler, vertexBuffer, vertexCount);
                        } catch (Throwable var25) {
                           if (passx != null) {
                              try {
                                 passx.close();
                              } catch (Throwable var23) {
                                 var25.addSuppressed(var23);
                              }
                           }

                           throw var25;
                        }

                        if (passx != null) {
                           passx.close();
                        }

                        this.bloom.composite(target.getColorTextureView(), bloomSettings);
                        return;
                     }
                  } finally {
                     if (sceneUniforms != null) {
                        sceneUniforms.close();
                     }

                     if (emissiveUniforms != null) {
                        emissiveUniforms.close();
                     }

                     if (vertexBuffer != null) {
                        vertexBuffer.close();
                     }

                     mesh.close();
                  }
               }
            }
         }
      }
   }

   private void draw(RenderPass pass, RenderPipeline pipeline, GpuBuffer uniforms, GpuSampler depthSampler, GpuBuffer vertexBuffer, int vertexCount) {
      pass.setPipeline(pipeline);
      pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
      pass.setUniform("GlowStripUniforms", uniforms);
      pass.bindTexture("DepthSampler", this.depthCopy.getDepthTextureView(), depthSampler);
      pass.setVertexBuffer(0, vertexBuffer.slice());
      pass.draw(vertexCount, 1, 0, 0);
   }

   private MeshData buildMesh(Minecraft minecraft, List<GlowStripRenderer.Strip> strips) {
      int segments = 0;

      for (GlowStripRenderer.Strip strip : strips) {
         segments += Math.max(0, strip.points().size() - 1);
      }

      if (segments == 0) {
         return null;
      }

      Camera camera = minecraft.gameRenderer.mainCamera();
      Vec3 cameraPosition = camera.position();
      Matrix4f viewPose = Render3DUtil.cameraViewPose(camera);
      BufferBuilder builder = new BufferBuilder(
         ByteBufferBuilder.exactlySized(segments * 6 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()),
         PrimitiveTopology.TRIANGLES,
         DefaultVertexFormat.POSITION_TEX_COLOR
      );
      boolean any = false;

      for (GlowStripRenderer.Strip strip : strips) {
         any |= this.appendStrip(builder, strip, cameraPosition, viewPose);
      }

      return any ? builder.build() : discard(builder);
   }

   private static MeshData discard(BufferBuilder builder) {
      MeshData data = builder.build();
      if (data != null) {
         data.close();
      }

      return null;
   }

   private boolean appendStrip(BufferBuilder builder, GlowStripRenderer.Strip strip, Vec3 cameraPosition, Matrix4f viewPose) {
      List<GlowStripRenderer.Point> points = strip.points();
      int count = points.size();
      if (count < 2) {
         return false;
      }

      Vector4f[] view = new Vector4f[count];
      float[] along = new float[count];
      float total = 0.0F;

      for (int index = 0; index < count; index++) {
         view[index] = Render3DUtil.toViewSpace(points.get(index).position(), cameraPosition, viewPose);
         if (index > 0) {
            float dx = view[index].x - view[index - 1].x;
            float dy = view[index].y - view[index - 1].y;
            float dz = view[index].z - view[index - 1].z;
            total += (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         }

         along[index] = total;
      }

      if (total <= 1.0E-5F) {
         return false;
      }

      float[] sideX = new float[count];
      float[] sideY = new float[count];

      for (int index = 0; index < count; index++) {
         Vector4f previous = view[Math.max(0, index - 1)];
         Vector4f next = view[Math.min(count - 1, index + 1)];
         float dx = next.x - previous.x;
         float dy = next.y - previous.y;
         float length = (float)Math.sqrt(dx * dx + dy * dy);
         float halfWidth = Math.max(0.002F, points.get(index).width()) * 0.5F * 3.0F;
         if (length > 1.0E-4F) {
            sideX[index] = -dy / length * halfWidth;
            sideY[index] = dx / length * halfWidth;
         } else {
            sideX[index] = halfWidth;
            sideY[index] = 0.0F;
         }
      }

      for (int index = 0; index < count - 1; index++) {
         Vector4f start = view[index];
         Vector4f end = view[index + 1];
         int startColor = points.get(index).color();
         int endColor = points.get(index + 1).color();
         float startU = along[index] / total;
         float endU = along[index + 1] / total;
         addVertex(builder, start, sideX[index], sideY[index], startU, 3.0F, startColor);
         addVertex(builder, start, -sideX[index], -sideY[index], startU, -3.0F, startColor);
         addVertex(builder, end, -sideX[index + 1], -sideY[index + 1], endU, -3.0F, endColor);
         addVertex(builder, start, sideX[index], sideY[index], startU, 3.0F, startColor);
         addVertex(builder, end, -sideX[index + 1], -sideY[index + 1], endU, -3.0F, endColor);
         addVertex(builder, end, sideX[index + 1], sideY[index + 1], endU, 3.0F, endColor);
      }

      return true;
   }

   private static void addVertex(BufferBuilder builder, Vector4f base, float offsetX, float offsetY, float u, float v, int color) {
      builder.addVertex(base.x + offsetX, base.y + offsetY, base.z).setUv(u, v).setColor(color);
   }

   private GpuBuffer uploadUniforms(int width, int height, GlowStripRenderer.Style style, boolean emissivePass) {
      GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Blade Glow Strip UBO", 136, UNIFORM_SIZE);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putVec4(PostFx.shaderTime(), Math.max(0.0F, style.glow()), Math.max(5.0E-4F, style.softness()), Math.clamp(style.coreWidth(), 0.02F, 1.0F))
            .putVec4(width, height, 1.0F, 3.0F)
            .putVec4(emissivePass ? 1.0F : 0.0F, Math.max(0.0F, style.hdrBoost()), Math.clamp(style.chromatic(), 0.0F, 1.0F), style.flowSpeed())
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
      } catch (Throwable var10) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (stack != null) {
         stack.close();
      }

      return buffer;
   }

   private void ensureDepthCopy(int width, int height) {
      if (this.depthCopy == null || this.depthCopy.width != width || this.depthCopy.height != height) {
         if (this.depthCopy != null) {
            this.depthCopy.destroyBuffers();
         }

         this.depthCopy = new TextureTarget("furryhook-glow-strip-depth", width, height, true, GpuFormat.RGBA8_UNORM);
      }
   }

   public void release() {
      if (this.depthCopy != null) {
         this.depthCopy.destroyBuffers();
         this.depthCopy = null;
      }

      this.bloom.release();
   }

   public record Point(Vec3 position, float width, int color) {
   }

   public record Strip(List<GlowStripRenderer.Point> points) {
   }

   public record Style(float glow, float coreWidth, float chromatic, float flowSpeed, float softness, float hdrBoost) {
   }
}
