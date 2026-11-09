package win.furryhook.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fc;
import win.furryhook.context.MinecraftContext;

public final class ColorGridRenderState extends UiElementRenderState {
   private final ColorGridRenderState.ColorGridTexture texture;
   private final float x;
   private final float y;
   private final float cellSize;
   private final int columns;
   private final int rows;
   private final float radius;
   private final int color;
   private final int selectedIndex;
   private final float selectedThickness;
   private final int hoveredIndex;
   private final float hoveredThickness;

   public ColorGridRenderState(
      Matrix3x2fc pose,
      float x,
      float y,
      float cellSize,
      int columns,
      float radius,
      int color,
      int selectedIndex,
      float selectedThickness,
      int hoveredIndex,
      float hoveredThickness,
      int[] colors,
      ScreenRectangle scissor
   ) {
      super(pose, scissor, x, y, Math.max(1.0F, cellSize) * Math.max(1, columns), Math.max(1.0F, cellSize) * Math.max(1, colors.length / Math.max(1, columns)));
      this.texture = ColorGridRenderState.ColorGridTexture.load(columns, colors);
      this.x = x;
      this.y = y;
      this.cellSize = Math.max(1.0F, cellSize);
      this.columns = Math.max(1, columns);
      this.rows = Math.max(1, colors.length / this.columns);
      this.radius = Math.max(0.0F, radius);
      this.color = color;
      this.selectedIndex = selectedIndex;
      this.selectedThickness = Math.max(0.0F, selectedThickness);
      this.hoveredIndex = hoveredIndex;
      this.hoveredThickness = Math.max(0.0F, hoveredThickness);
   }

   public void buildVertices(VertexConsumer vertexConsumer) {
      float width = this.cellSize * this.columns;
      float height = this.cellSize * this.rows;
      float packedIndices = UiVertexPacking.packDual12Raw(this.selectedIndex + 1, this.hoveredIndex + 1);
      int packedThickness = UiVertexPacking.packU8Pair(Math.round(this.selectedThickness * 8.0F), Math.round(this.hoveredThickness * 8.0F));
      int packedGrid = UiVertexPacking.packU8Pair(this.columns, this.rows);
      int packedSizeX = UiVertexPacking.packSize(width);
      int packedSizeY = UiVertexPacking.packSize(height);
      this.addVertex(vertexConsumer, this.x, this.y, 0.0F, 0.0F, packedIndices, packedThickness, packedGrid, packedSizeX, packedSizeY);
      this.addVertex(vertexConsumer, this.x, this.y + height, 0.0F, height, packedIndices, packedThickness, packedGrid, packedSizeX, packedSizeY);
      this.addVertex(vertexConsumer, this.x + width, this.y + height, width, height, packedIndices, packedThickness, packedGrid, packedSizeX, packedSizeY);
      this.addVertex(vertexConsumer, this.x + width, this.y, width, 0.0F, packedIndices, packedThickness, packedGrid, packedSizeX, packedSizeY);
   }

   private void addVertex(
      VertexConsumer vertexConsumer,
      float x,
      float y,
      float localX,
      float localY,
      float packedIndices,
      int packedThickness,
      int packedGrid,
      int packedSizeX,
      int packedSizeY
   ) {
      vertexConsumer.addVertex(this.transformX(x, y), this.transformY(x, y), packedIndices)
         .setColor(this.color)
         .setUv(localX, localY)
         .setUv1(packedSizeX, packedSizeY)
         .setUv2(packedThickness, packedGrid)
         .setNormal(0.0F, 0.0F, 1.0F)
         .setLineWidth(this.radius);
   }

   public RenderPipeline pipeline() {
      return GuiPipelines.COLOR_GRID;
   }

   public TextureSetup textureSetup() {
      return this.texture.textureSetup();
   }

   private static final class ColorGridTexture {
      private static final Map<ColorGridRenderState.ColorGridTexture.Key, ColorGridRenderState.ColorGridTexture> CACHE = new ConcurrentHashMap<>();
      private final ColorGridRenderState.ColorGridTexture.Key key;
      private TextureSetup textureSetup;

      private ColorGridTexture(ColorGridRenderState.ColorGridTexture.Key key) {
         this.key = key;
      }

      private static ColorGridRenderState.ColorGridTexture load(int columns, int[] colors) {
         return CACHE.computeIfAbsent(new ColorGridRenderState.ColorGridTexture.Key(columns, colors), ColorGridRenderState.ColorGridTexture::new);
      }

      private TextureSetup textureSetup() {
         if (this.textureSetup == null) {
            Identifier textureId = Identifier.parse("furryhook:generated/color_grid/" + Integer.toHexString(this.key.hashCode()));
            DynamicTexture texture = new DynamicTexture(() -> textureId.toString(), createImage(this.key.columns, this.key.colors));
            MinecraftContext.mc.getTextureManager().register(textureId, texture);
            this.textureSetup = TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, false));
         }

         return this.textureSetup;
      }

      private static NativeImage createImage(int columns, int[] colors) {
         int safeColumns = Math.max(1, columns);
         int rows = Math.max(1, colors.length / safeColumns);
         NativeImage image = new NativeImage(Format.RGBA, safeColumns, rows, false);

         for (int index = 0; index < colors.length; index++) {
            image.setPixel(index % safeColumns, index / safeColumns, colors[index]);
         }

         return image;
      }

      private record Key(int columns, int[] colors) {
         private Key {
            columns = Math.max(1, columns);
            colors = Arrays.copyOf(colors, colors.length);
         }

         @Override
         public boolean equals(Object object) {
            return object instanceof ColorGridRenderState.ColorGridTexture.Key other
               && this.columns == other.columns
               && Arrays.equals(this.colors, other.colors);
         }

         @Override
         public int hashCode() {
            return 31 * this.columns + Arrays.hashCode(this.colors);
         }
      }
   }
}
