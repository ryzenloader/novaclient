package win.furryhook.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;

public final class HudElementSettingsPopup {
   private static final float WIDTH = 150.0F;
   private static final float PAD = 6.0F;
   private static final float ROW = 17.0F;
   private static final float GROUP = 30.0F;
   private static final float TEXT = 8.5F;
   private static final int PANEL = ColorUtil.rgba(14, 17, 29, 242);
   private static final int FOREGROUND = ColorUtil.rgb(225, 229, 240);
   private static final int MUTED = ColorUtil.rgb(172, 182, 205);
   private static final int TRACK = ColorUtil.rgb(51, 61, 88);
   private static final int ACCENT = ColorUtil.rgb(60, 109, 255);
   private final AnimatedValue visibility = new AnimatedValue();
   private HudElement target;
   private String title = "";
   private List<HudElementSettingsPopup.Toggle> toggles = List.of();
   private boolean open;
   private boolean sizeDragging;
   private float x;
   private float y;
   private float screenWidth;
   private float screenHeight;
   private float alpha;

   public void open(HudElement element, String title, float anchorX, float anchorY, float screenWidth, float screenHeight, float unit) {
      this.handleRelease();
      this.target = element;
      this.title = title;
      this.toggles = buildToggles(element);
      this.screenWidth = screenWidth;
      this.screenHeight = screenHeight;
      this.x = anchorX;
      this.y = anchorY;
      this.open = true;
      this.clampToScreen(unit);
   }

   private static List<HudElementSettingsPopup.Toggle> buildToggles(HudElement element) {
      List<HudElementSettingsPopup.Toggle> result = new ArrayList<>();
      if (element instanceof TargetElement target) {
         result.add(
            new HudElementSettingsPopup.Toggle("Здоровье поглощения", target::absorptionVisible, () -> target.setAbsorptionVisible(!target.absorptionVisible()))
         );
         result.add(
            new HudElementSettingsPopup.Toggle("Использование предмета", target::itemUseVisible, () -> target.setItemUseVisible(!target.itemUseVisible()))
         );
         result.add(new HudElementSettingsPopup.Toggle("Партиклы", HudFeature::targetHeadParticles, HudFeature::toggleTargetHeadParticles));
         result.add(new HudElementSettingsPopup.Toggle("Партиклы здоровья", HudFeature::targetHealthParticles, HudFeature::toggleTargetHealthParticles));
         result.add(new HudElementSettingsPopup.Toggle("Отображать броню", target::armorVisible, () -> target.setArmorVisible(!target.armorVisible())));
      } else if (element instanceof WatermarkElement) {
         for (String item : WatermarkElement.CONTENT_ITEMS) {
            String label = switch (item) {
               case "Player" -> "Игрок";
               case "FPS" -> "FPS";
               case "Ping" -> "Пинг";
               case "Server" -> "Сервер";
               default -> item;
            };
            result.add(new HudElementSettingsPopup.Toggle(label, () -> HudFeature.watermarkShows(item), () -> HudFeature.toggleWatermarkContent(item)));
         }
      } else if (element instanceof BottomStatsElement) {
         result.add(
            new HudElementSettingsPopup.Toggle(
               "Координаты", () -> HudFeature.informationShows("Coordinates"), () -> HudFeature.toggleInformationContent("Coordinates")
            )
         );
         result.add(new HudElementSettingsPopup.Toggle("Скорость", () -> HudFeature.informationShows("BPS"), () -> HudFeature.toggleInformationContent("BPS")));
      } else if (element instanceof TpsElement) {
         result.add(new HudElementSettingsPopup.Toggle("TPS", () -> HudFeature.informationShows("TPS"), () -> HudFeature.toggleInformationContent("TPS")));
      }

      if (element instanceof TargetElement
         || element instanceof KeybindsElement
         || element instanceof PotionsElement
         || element instanceof CooldownsElement
         || element instanceof StaffListElement) {
         result.add(new HudElementSettingsPopup.Toggle("Всегда показывать", element::alwaysShow, () -> element.setAlwaysShow(!element.alwaysShow())));
      }

      return List.copyOf(result);
   }

   public void close() {
      this.handleRelease();
      this.open = false;
   }

   public boolean isOpen() {
      return this.open;
   }

   public boolean contains(float mouseX, float mouseY, float unit) {
      return this.open && inside(mouseX, mouseY, this.x, this.y, 150.0F * unit, this.height() * unit);
   }

   public boolean handleClick(float mouseX, float mouseY, float unit) {
      if (this.contains(mouseX, mouseY, unit) && this.target != null) {
         float px = (mouseX - this.x) / unit;
         float py = (mouseY - this.y) / unit;
         if (this.target instanceof TargetElement target) {
            if (py >= 18.0F && py <= 31.0F) {
               String[] styles = new String[]{"Default", "Gsmar", "Powerz"};

               for (int i = 0; i < styles.length; i++) {
                  if (px >= 6.0F + i * 38.0F && px <= 6.0F + i * 38.0F + 35.0F) {
                     HudFeature.setTargetStyle(styles[i]);
                     return true;
                  }
               }
            }

            if (py >= 48.0F && py <= 61.0F) {
               if (px >= 6.0F && px <= 78.0F) {
                  target.setHealthBasedColor(false);
               }

               if (px >= 81.0F && px <= 134.0F) {
                  target.setHealthBasedColor(true);
               }

               return true;
            }
         }

         float start = this.togglesTop();
         int index = (int)Math.floor((py - start) / 17.0F);
         if (py >= start && index >= 0 && index < this.toggles.size()) {
            this.toggles.get(index).action().run();
            return true;
         }

         float sliderY = this.sliderTop();
         if (py >= sliderY + 12.0F && py <= sliderY + 27.0F && px >= 6.0F && px <= 144.0F) {
            this.sizeDragging = true;
            this.updatePointer(mouseX, mouseY, unit);
         } else if (py >= sliderY && py <= sliderY + 11.0F && px >= 106.0F) {
            this.target.setSizePercent(100.0F);
         }

         return true;
      } else {
         return false;
      }
   }

   public void updatePointer(float mouseX, float mouseY, float unit) {
      if (this.open && this.sizeDragging && this.target != null && !(unit <= 0.0F)) {
         float left = this.x + 9.0F * unit;
         float width = 132.0F * unit;
         float fraction = Math.clamp((mouseX - left) / width, 0.0F, 1.0F);
         this.target.previewSize(50.0F + fraction * 150.0F);
      }
   }

   public boolean handleRelease() {
      if (!this.sizeDragging) {
         return false;
      }

      this.sizeDragging = false;
      if (this.target != null) {
         this.target.commitSize();
      }

      return true;
   }

   public void render(Minecraft mc, float unit) {
      this.alpha = this.visibility.update(this.open ? 1.0F : 0.0F, 0.12F, System.nanoTime());
      if (!(this.alpha <= 0.01F) && this.target != null && !(unit <= 0.0F)) {
         this.screenWidth = mc.getWindow().getGuiScaledWidth();
         this.screenHeight = mc.getWindow().getGuiScaledHeight();
         this.clampToScreen(unit);
         Render2DUtil.rect(this.x, this.y, 150.0F * unit, this.height() * unit)
            .color(this.color(PANEL))
            .radius(6.0F * unit)
            .border(0.45F * unit, this.color(ColorUtil.rgba(92, 114, 170, 38)))
            .draw();
         if (this.target instanceof TargetElement target) {
            this.label("Интерфейс", 6.0F, 11.0F, 8.5F, FOREGROUND, unit);
            String[] styles = new String[]{"Default", "Gsmar", "Powerz"};

            for (int i = 0; i < styles.length; i++) {
               this.chip(i + 1 + " Вид", 6.0F + i * 38.0F, 18.0F, 35.0F, styles[i].equals(HudFeature.targetStyleName()), unit);
            }

            this.label("Цвет отображения хп", 6.0F, 41.0F, 8.5F, FOREGROUND, unit);
            this.chip("Клиентский", 6.0F, 48.0F, 72.0F, !target.healthBasedColor(), unit);
            this.chip("Здоровье", 81.0F, 48.0F, 53.0F, target.healthBasedColor(), unit);
         } else {
            String heading = HudElement.glassEllipsize(this.title, 8.5F * unit, UiFontStyle.SEMIBOLD, 138.0F * unit);
            this.label(heading, 6.0F, 12.0F, 8.5F, FOREGROUND, unit);
         }

         float top = this.togglesTop();

         for (int i = 0; i < this.toggles.size(); i++) {
            HudElementSettingsPopup.Toggle toggle = this.toggles.get(i);
            float center = top + i * 17.0F + 8.5F;
            String text = HudElement.glassEllipsize(toggle.label(), 8.5F * unit, UiFontStyle.MEDIUM, 115.0F * unit);
            this.label(text, 6.0F, center, 8.5F, FOREGROUND, unit);
            boolean enabled = toggle.enabled().getAsBoolean();
            float trackX = this.x + 126.0F * unit;
            float trackY = this.y + (center - 4.5F) * unit;
            Render2DUtil.rect(trackX, trackY, 18.0F * unit, 9.0F * unit).color(this.color(enabled ? ACCENT : TRACK)).radius(4.5F * unit).draw();
            Render2DUtil.rect(trackX + (enabled ? 10.0F : 1.0F) * unit, trackY + unit, 7.0F * unit, 7.0F * unit)
               .color(this.color(-1))
               .radius(3.5F * unit)
               .draw();
         }

         float slider = this.sliderTop();
         this.label("Размер", 6.0F, slider + 5.0F, 8.5F, FOREGROUND, unit);
         String percent = Math.round(this.target.sizePercent()) + "%";
         float textWidth = HudElement.glassTextWidth(percent, 8.5F * unit, UiFontStyle.MEDIUM) / unit;
         this.label(percent, 144.0F - textWidth, slider + 5.0F, 8.5F, MUTED, unit);
         float trackX = this.x + 9.0F * unit;
         float trackY = this.y + (slider + 19.0F) * unit;
         float trackWidth = 132.0F * unit;
         float fill = Math.clamp((this.target.sizePercent() - 50.0F) / 150.0F, 0.0F, 1.0F) * trackWidth;
         Render2DUtil.rect(trackX, trackY, trackWidth, 3.0F * unit).color(this.color(TRACK)).radius(1.5F * unit).draw();
         if (fill > 0.0F) {
            Render2DUtil.rect(trackX, trackY, fill, 3.0F * unit).color(this.color(ACCENT)).radius(1.5F * unit).draw();
         }

         Render2DUtil.rect(trackX + fill - 3.5F * unit, trackY - 2.0F * unit, 7.0F * unit, 7.0F * unit).color(this.color(-1)).radius(3.5F * unit).draw();
      }
   }

   private void chip(String text, float x, float y, float width, boolean selected, float unit) {
      Render2DUtil.rect(this.x + x * unit, this.y + y * unit, width * unit, 13.0F * unit)
         .color(this.color(selected ? ACCENT : TRACK))
         .radius(2.5F * unit)
         .draw();
      float textWidth = HudElement.glassTextWidth(text, 8.5F * unit, UiFontStyle.MEDIUM) / unit;
      this.label(text, x + (width - textWidth) * 0.5F, y + 6.5F, 8.5F, selected ? -1 : MUTED, unit);
   }

   private void label(String text, float x, float centerY, float size, int color, float unit) {
      HudElement.drawGlassText(text, this.x + x * unit, this.y + centerY * unit, size * unit, UiFontStyle.MEDIUM, color, this.alpha);
   }

   private int color(int color) {
      return ColorUtil.multiplyAlpha(color, this.alpha);
   }

   private float togglesTop() {
      return this.target instanceof TargetElement ? 66.0F : 24.0F;
   }

   private float sliderTop() {
      return this.togglesTop() + this.toggles.size() * 17.0F + 3.0F;
   }

   private float height() {
      return this.sliderTop() + 30.0F + 6.0F;
   }

   private void clampToScreen(float unit) {
      this.x = Math.clamp(this.x, 2.0F, Math.max(2.0F, this.screenWidth - 150.0F * unit - 2.0F));
      this.y = Math.clamp(this.y, 2.0F, Math.max(2.0F, this.screenHeight - this.height() * unit - 2.0F));
   }

   private static boolean inside(float px, float py, float x, float y, float width, float height) {
      return px >= x && px <= x + width && py >= y && py <= y + height;
   }

   private record Toggle(String label, BooleanSupplier enabled, Runnable action) {
   }
}
