package win.furryhook.utils.render.gui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class UiVertexFormats {
   public static final VertexFormat UI = VertexFormat.builder(0)
      .addAttribute("Position", GpuFormat.RGB32_FLOAT)
      .addAttribute("Color", GpuFormat.RGBA8_UNORM)
      .addAttribute("UV0", GpuFormat.RG32_FLOAT)
      .addAttribute("UV1", GpuFormat.RG16_SINT)
      .addAttribute("UV2", GpuFormat.RG16_SINT)
      .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
      .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
      .build();

   private UiVertexFormats() {
   }
}
