package win.furryhook.hud;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix3x2fStack;
import win.furryhook.context.RenderContext;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.misc.ServerHelperFeature;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class QuickUseElement extends HudElement {
   private static final float LOCAL_SCALE = 0.85F;
   private static final int MAX_COLUMNS = 6;
   private static final float PADDING = 6.0F;
   private static final float CELL_SIZE = 31.0F;
   private static final float CELL_GAP = 2.5F;
   private static final float ITEM_SIZE = 20.0F;
   private static final float PANEL_RADIUS = 5.0F;
   private static final float CELL_RADIUS = 4.0F;
   private static final float KEY_TEXT_SIZE = 7.5F;
   private static final float COUNT_TEXT_SIZE = 8.0F;
   private List<Object> entries = List.of();
   private int columns;

   public QuickUseElement() {
      super("quick_use", "QuickUse");
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      unit *= 0.85F;
      ServerHelperFeature helper = FeatureManager.INSTANCE.getEnabled(ServerHelperFeature.class);
      LocalPlayer player = mc.player;
      this.entries = List.of();
      if (this.entries.isEmpty()) {
         this.columns = 0;
         this.width = 0.0F;
         this.height = 0.0F;
      } else {
         this.columns = Math.min(6, this.entries.size());
         int rows = (this.entries.size() + this.columns - 1) / this.columns;
         this.width = (12.0F + this.columns * 31.0F + Math.max(0, this.columns - 1) * 2.5F) * unit;
         this.height = (12.0F + rows * 31.0F + Math.max(0, rows - 1) * 2.5F) * unit;
      }
   }

   @Override
   protected float defaultY(float unit) {
      return 220.0F * unit;
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      unit *= 0.85F;
      if (!this.entries.isEmpty() && this.columns > 0 && mc.player != null) {
         float alpha = this.appearAlpha();
         this.drawWatermarkPanel(this.x, this.y, this.width, this.height, 5.0F * unit, unit, alpha);
         float cellSize = 31.0F * unit;
         float cellGap = 2.5F * unit;
         float cellsX = this.x + 6.0F * unit;
         float cellsY = this.y + 6.0F * unit;

         for (int index = 0; index < this.entries.size(); index++) {
            int column = index % this.columns;
            int row = index / this.columns;
            float cellX = cellsX + column * (cellSize + cellGap);
            float cellY = cellsY + row * (cellSize + cellGap);
            Render2DUtil.rect(cellX, cellY, cellSize, cellSize)
               .color(ColorUtil.multiplyAlpha(ColorUtil.withAlpha(HudFeature.hudAccent(), 22), alpha))
               .radius(4.0F * unit)
               .draw();
         }

         if (!(alpha < 0.75F)) {
            GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
            if (extractor != null) {
               Render2DUtil.flush();
               Matrix3x2fStack pose = extractor.pose();
               float guiScale = mc.getWindow().getGuiScale();
               float itemSize = 20.0F * unit;
               float itemScale = itemSize / 16.0F;
               MsdfFont keyFont = UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight());
               MsdfFont countFont = UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight());
               float keySize = 7.5F * unit;
               float countSize = 8.0F * unit;
               float keySpacing = keySize * UiFontStyle.SEMIBOLD.letterSpacingEm();
               int index = 0;

               while (index < this.entries.size()) {
                  index++;
               }
            }
         }
      }
   }

   private static String compactBind(String label) {
      return switch (label) {
         case "Mouse Left" -> "M1";
         case "Mouse Right" -> "M2";
         case "Mouse Middle" -> "M3";
         default -> label != null && label.startsWith("Mouse ") ? "M" + label.substring("Mouse ".length()) : label;
      };
   }
}
