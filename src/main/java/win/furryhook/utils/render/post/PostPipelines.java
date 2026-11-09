package win.furryhook.utils.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public final class PostPipelines {
   private static final BindGroupLayout KAWASE_DOWN_LAYOUT = BindGroupLayout.builder()
      .withSampler("CurrentInput")
      .withUniform("KawaseDownUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout KAWASE_UP_LAYOUT = BindGroupLayout.builder()
      .withSampler("CurrentInput")
      .withUniform("KawaseUpUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_MASK_LAYOUT = BindGroupLayout.builder()
      .withSampler("BeforeTexture")
      .withSampler("AfterTexture")
      .withSampler("BeforeDepth")
      .withSampler("AfterDepth")
      .withUniform("HandMaskUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout SHADER_HANDS_LAYOUT = BindGroupLayout.builder()
      .withSampler("BlurredSampler")
      .withSampler("MaskSampler")
      .withUniform("HandCompositeUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_FILL_LAYOUT = BindGroupLayout.builder()
      .withSampler("MaskSampler")
      .withUniform("HandFillUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_GLASS_LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withSampler("MaskSampler")
      .withUniform("HandGlassUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_OUTLINE_LAYOUT = BindGroupLayout.builder()
      .withSampler("MaskSampler")
      .withUniform("HandOutlineUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_HALO_LAYOUT = BindGroupLayout.builder()
      .withSampler("BlurredSampler")
      .withSampler("MaskSampler")
      .withUniform("HandHaloUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_ELECTRIC_LAYOUT = BindGroupLayout.builder()
      .withSampler("BlurredSampler")
      .withSampler("MaskSampler")
      .withUniform("HandElectricUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_TRAIL_LAYOUT = BindGroupLayout.builder()
      .withSampler("PrevSampler")
      .withSampler("InjectSampler")
      .withUniform("HandTrailUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_BLUR_LAYOUT = BindGroupLayout.builder()
      .withSampler("CurrentInput")
      .withUniform("HandBlurUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HANDS_TEST_TRAIL_LAYOUT = BindGroupLayout.builder()
      .withSampler("PrevSampler")
      .withSampler("SceneSampler")
      .withSampler("MaskSampler")
      .withUniform("HandsTestTrailUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HANDS_TEST_FIRE_LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withSampler("SmokeSampler")
      .withSampler("MaskSampler")
      .withUniform("HandsTestFireUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout POINT_LIGHT_LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withSampler("DepthSampler")
      .withUniform("PointLightUniforms", UniformType.UNIFORM_BUFFER)
      .withUniform("PointLights", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout WORLD_SKY_CLOUDS_LAYOUT = BindGroupLayout.builder()
      .withSampler("DepthSampler")
      .withUniform("WorldSkyUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout WORLD_SKY_LAYOUT = BindGroupLayout.builder()
      .withSampler("DepthSampler")
      .withSampler("CloudSampler")
      .withUniform("WorldSkyUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout HAND_MASK_SMOOTH_LAYOUT = BindGroupLayout.builder()
      .withSampler("RawMask")
      .withUniform("HandMaskUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout SATURATION_LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withUniform("SaturationUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout CHAMS_MASK_STYLE_LAYOUT = BindGroupLayout.builder()
      .withSampler("MaskSampler")
      .withUniform("ChamsStyle", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout CHAMS_GLASS_LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withSampler("MaskSampler")
      .withUniform("ChamsStyle", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout CHAMS_BLURRED_LAYOUT = BindGroupLayout.builder()
      .withSampler("BlurredSampler")
      .withSampler("MaskSampler")
      .withUniform("ChamsStyle", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout ATMOSPHERE_LAYOUT = BindGroupLayout.builder()
      .withSampler("DepthSampler")
      .withSampler("SceneSampler")
      .withUniform("AtmosphereUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout RTX_REFLECTION_LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withSampler("BlurredSampler")
      .withSampler("DepthSampler")
      .withUniform("RtxReflectionUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout RTX_RAY_OCCLUSION_LAYOUT = BindGroupLayout.builder()
      .withSampler("SceneSampler")
      .withSampler("DepthSampler")
      .withUniform("RtxRayUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   private static final BindGroupLayout RTX_RAY_LAYOUT = BindGroupLayout.builder()
      .withSampler("OcclusionSampler")
      .withUniform("RtxRayUniforms", UniformType.UNIFORM_BUFFER)
      .build();
   public static final RenderPipeline ESP_KAWASE_DOWN = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/blurs/kawase_down"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/blurs/esp_kawase_down"))
      .withBindGroupLayout(KAWASE_DOWN_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline ESP_KAWASE_UP = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/blurs/kawase_up"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/blurs/esp_kawase_up"))
      .withBindGroupLayout(KAWASE_UP_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final GpuFormat MASK_RAW_FORMAT = GpuFormat.RGBA16_FLOAT;
   public static final RenderPipeline HDR_KAWASE_DOWN = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/blurs/hdr_kawase_down"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/blurs/esp_kawase_down"))
      .withBindGroupLayout(KAWASE_DOWN_LAYOUT)
      .withColorTargetState(new ColorTargetState(Optional.empty(), MASK_RAW_FORMAT, 15))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HDR_KAWASE_UP = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/blurs/hdr_kawase_up"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/blurs/esp_kawase_up"))
      .withBindGroupLayout(KAWASE_UP_LAYOUT)
      .withColorTargetState(new ColorTargetState(Optional.empty(), MASK_RAW_FORMAT, 15))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_MASK = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_mask"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_mask"))
      .withBindGroupLayout(HAND_MASK_LAYOUT)
      .withColorTargetState(new ColorTargetState(Optional.empty(), MASK_RAW_FORMAT, 15))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_MASK_SMOOTH = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_mask_smooth"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_mask_smooth"))
      .withBindGroupLayout(HAND_MASK_SMOOTH_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline SHADER_HANDS = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/shader_hands"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/shader_hands"))
      .withBindGroupLayout(SHADER_HANDS_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_FILL = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_fill"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_fill"))
      .withBindGroupLayout(HAND_FILL_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_PLASMA = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_plasma"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_plasma"))
      .withBindGroupLayout(HAND_FILL_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_NEBULA = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_nebula"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_nebula"))
      .withBindGroupLayout(HAND_FILL_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_PRISM = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_prism"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_prism"))
      .withBindGroupLayout(HAND_FILL_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_EMBERS = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_embers"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_embers"))
      .withBindGroupLayout(HAND_FILL_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_GLASS = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_glass"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_glass"))
      .withBindGroupLayout(HAND_GLASS_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_OUTLINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_outline"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_outline"))
      .withBindGroupLayout(HAND_OUTLINE_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_HALO = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_halo"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_halo"))
      .withBindGroupLayout(HAND_HALO_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_ELECTRIC = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_electric"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_electric"))
      .withBindGroupLayout(HAND_ELECTRIC_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_BLUR_DOWN = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_blur_down"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_blur_down"))
      .withBindGroupLayout(HAND_BLUR_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_BLUR_UP = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_blur_up"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_blur_up"))
      .withBindGroupLayout(HAND_BLUR_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HAND_TRAIL = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hand_trail"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hand_trail"))
      .withBindGroupLayout(HAND_TRAIL_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HANDS_TEST_TRAIL = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hands_test_trail"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hands_test_trail"))
      .withBindGroupLayout(HANDS_TEST_TRAIL_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline HANDS_TEST_FIRE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/hands_test_fire"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/hands_test_fire"))
      .withBindGroupLayout(HANDS_TEST_FIRE_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline POINT_LIGHTS = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/post/point_lights"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:post/point_lights"))
      .withBindGroupLayout(POINT_LIGHT_LAYOUT)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final GpuFormat SKY_CLOUDS_FORMAT = GpuFormat.RGBA16_FLOAT;
   public static final RenderPipeline WORLD_SKY_CLOUDS_DEEP_SPACE = skyCloudsPipeline("sky_clouds_deep_space", "furryhook:world/sky/clouds_deep_space");
   public static final RenderPipeline WORLD_SKY_CLOUDS_NEBULA = skyCloudsPipeline("sky_clouds_nebula", "furryhook:world/sky/clouds_nebula");
   public static final RenderPipeline WORLD_SKY_CLOUDS_PLASMA = skyCloudsPipeline("sky_clouds_plasma", "furryhook:world/sky/clouds_plasma");
   public static final RenderPipeline WORLD_SKY_DEEP_SPACE = worldPostPipeline("sky_deep_space", "furryhook:world/sky/deep_space", WORLD_SKY_LAYOUT);
   public static final RenderPipeline WORLD_SKY_NEBULA = worldPostPipeline("sky_nebula", "furryhook:world/sky/nebula", WORLD_SKY_LAYOUT);
   public static final RenderPipeline WORLD_SKY_PLASMA = worldPostPipeline("sky_plasma", "furryhook:world/sky/plasma", WORLD_SKY_LAYOUT);
   public static final RenderPipeline WORLD_SATURATION = worldPostPipeline("saturation", "furryhook:post/world_saturation", SATURATION_LAYOUT);
   public static final RenderPipeline CHAMS_SOLID = chamsPipeline("solid", "furryhook:post/chams_solid", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT);
   public static final RenderPipeline CHAMS_PLASMA = chamsPipeline("plasma", "furryhook:post/chams_plasma", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT);
   public static final RenderPipeline CHAMS_NEBULA = chamsPipeline("nebula", "furryhook:post/chams_nebula", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT);
   public static final RenderPipeline CHAMS_GLASS = chamsPipeline("glass", "furryhook:post/chams_glass", CHAMS_GLASS_LAYOUT, BlendFunction.TRANSLUCENT);
   public static final RenderPipeline CHAMS_OUTLINE = chamsPipeline(
      "outline", "furryhook:post/chams_outline", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT
   );
   public static final RenderPipeline CHAMS_INTERNAL = chamsPipeline("internal", "furryhook:post/chams_internal", CHAMS_BLURRED_LAYOUT, BlendFunction.LIGHTNING);
   public static final RenderPipeline CHAMS_GLOW = chamsPipeline("glow", "furryhook:post/chams_glow", CHAMS_BLURRED_LAYOUT, BlendFunction.TRANSLUCENT);
   public static final RenderPipeline CHAMS_GLOW_ADDITIVE = chamsPipeline(
      "glow_additive", "furryhook:post/chams_glow", CHAMS_BLURRED_LAYOUT, BlendFunction.LIGHTNING
   );
   public static final GpuFormat EFFECT_FORMAT = GpuFormat.RGBA8_UNORM;
   public static final RenderPipeline ATMOSPHERE_FOG = worldPostPipeline("atmosphere_fog", "furryhook:world/atmosphere_fog", ATMOSPHERE_LAYOUT);
   public static final GpuFormat RTX_RAY_FORMAT = GpuFormat.RGBA16_FLOAT;
   public static final RenderPipeline RTX_REFLECTIONS = worldPostPipeline("rtx_reflections", "furryhook:world/rtx_reflections", RTX_REFLECTION_LAYOUT);
   public static final RenderPipeline RTX_RAY_OCCLUSION = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/world/rtx_ray_occlusion"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:world/rtx_ray_occlusion"))
      .withBindGroupLayout(RTX_RAY_OCCLUSION_LAYOUT)
      .withColorTargetState(new ColorTargetState(Optional.empty(), RTX_RAY_FORMAT, 15))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();
   public static final RenderPipeline RTX_RAYS = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.parse("furryhook:pipeline/world/rtx_rays"))
      .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
      .withFragmentShader(Identifier.parse("furryhook:world/rtx_rays"))
      .withBindGroupLayout(RTX_RAY_LAYOUT)
      .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
      .withDepthStencilState(Optional.empty())
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withCull(false)
      .build();

   private static RenderPipeline worldPostPipeline(String name, String fragment, BindGroupLayout layout) {
      return RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.parse("furryhook:pipeline/world/" + name))
         .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
         .withFragmentShader(Identifier.parse(fragment))
         .withBindGroupLayout(layout)
         .withColorTargetState(ColorTargetState.DEFAULT)
         .withDepthStencilState(Optional.empty())
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
         .withCull(false)
         .build();
   }

   private static RenderPipeline skyCloudsPipeline(String name, String fragment) {
      return RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.parse("furryhook:pipeline/world/" + name))
         .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
         .withFragmentShader(Identifier.parse(fragment))
         .withBindGroupLayout(WORLD_SKY_CLOUDS_LAYOUT)
         .withColorTargetState(new ColorTargetState(Optional.empty(), SKY_CLOUDS_FORMAT, 15))
         .withDepthStencilState(Optional.empty())
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
         .withCull(false)
         .build();
   }

   private static RenderPipeline chamsPipeline(String name, String fragment, BindGroupLayout layout, BlendFunction blend) {
      return RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.parse("furryhook:pipeline/chams/" + name))
         .withVertexShader(Identifier.parse("furryhook:post/blurs/kawase_common"))
         .withFragmentShader(Identifier.parse(fragment))
         .withBindGroupLayout(layout)
         .withColorTargetState(new ColorTargetState(blend))
         .withDepthStencilState(Optional.empty())
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
         .withCull(false)
         .build();
   }

   private PostPipelines() {
   }
}
