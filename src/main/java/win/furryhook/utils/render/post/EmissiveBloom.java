package win.furryhook.utils.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

public final class EmissiveBloom {
   public static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
   public static final int MAX_LEVELS = 4;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
   private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
   private static final BindGroupLayout COMPOSITE_LAYOUT = BindGroupLayout.builder()
      .withSampler("BloomSampler")
      .withUniform("ParticleBloomUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   public static final RenderPipeline COMPOSITE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/emissive_bloom"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/particle_bloom"))
      .withBindGroupLayout(COMPOSITE_LAYOUT)
      .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   private final PostTarget target;
   private KawaseBlur blur;
   private GpuBuffer uniforms;

   public EmissiveBloom(String name) {
      this.target = new PostTarget(name + "-emissive", FORMAT, false);
   }

   public TextureTarget begin(int width, int height) {
      TextureTarget emissive = this.target.ensure(Math.max(1, width / 2), Math.max(1, height / 2));
      if (emissive.getColorTexture() != null && emissive.getColorTextureView() != null) {
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(emissive.getColorTexture(), CLEAR);
         return emissive;
      } else {
         return null;
      }
   }

   public void composite(GpuTextureView output, EmissiveBloom.Settings settings) {
      TextureTarget emissive = this.target.get();
      if (output != null && emissive != null && emissive.getColorTextureView() != null) {
         int levels = Math.clamp(settings.radius(), 1, 4);
         float offset = 1.0F + 0.35F * levels;
         GpuSampler sampler = PostFx.linearSampler();
         GpuTextureView blurred = this.blur().run(emissive.getColorTextureView(), emissive.width, emissive.height, levels, offset, sampler);
         if (blurred != null) {
            if (this.uniforms == null) {
               this.uniforms = PostFx.createUniforms("Blade Emissive Bloom UBO", UNIFORM_SIZE);
            }

            float texelX = 1.0F / Math.max(1, emissive.width / 2);
            PostFx.writeUniforms(
               this.uniforms, UNIFORM_SIZE, builder -> builder.putVec4(settings.intensity(), settings.anamorphic(), texelX, PostFx.shaderTime())
            );
            PostFx.pass("Blade Emissive Bloom Composite", COMPOSITE, output, pass -> {
               pass.setUniform("ParticleBloomUniforms", this.uniforms);
               pass.bindTexture("BloomSampler", blurred, sampler);
            });
         }
      }
   }

   private KawaseBlur blur() {
      if (this.blur == null) {
         this.blur = KawaseBlur.hdr("furryhook-emissive-bloom");
      }

      return this.blur;
   }

   public void release() {
      this.target.release();
      if (this.blur != null) {
         this.blur.release();
         this.blur = null;
      }

      if (this.uniforms != null) {
         this.uniforms.close();
         this.uniforms = null;
      }
   }

   public record Settings(boolean enabled, float intensity, int radius, float boost, float anamorphic) {
      public static final EmissiveBloom.Settings OFF = new EmissiveBloom.Settings(false, 0.0F, 1, 1.0F, 0.0F);

      public boolean active() {
         return this.enabled && this.intensity > 0.001F;
      }
   }
}
