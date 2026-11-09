package win.furryhook.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.client.model.geom.ModelPart.Vertex;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.joml.Matrix3x2fStack;
import win.furryhook.context.RenderContext;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.impl.combat.TriggerBotFeature;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.menu.core.MenuConfigStore;
import win.furryhook.mixin.accessor.ModelPartAccessor;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.combat.HealthUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.PlayerHead;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.RingProgress;
import win.furryhook.utils.render.gui.TextAlign;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class TargetElement extends HudElement {
   public static final String STYLE_DEFAULT = "Default";
   public static final String STYLE_GSMAR = "Gsmar";
   public static final String STYLE_POWERZ = "Powerz";
   private static final int BAR_TRACK_COLOR = Theme.Colors.OUTLINES_MEDIUM;
   private static final int ABSORPTION_DARK_COLOR = ColorUtil.rgb(184, 125, 18);
   private static final int ABSORPTION_LIGHT_COLOR = ColorUtil.rgb(255, 226, 92);
   private static final float ITEM_SIZE = 12.0F;
   private static final float ITEM_GAP = 2.0F;
   private static final float TEXT_SIZE = 8.5F;
   private static final float BAR_HEIGHT = 4.0F;
   private static final float BAR_INSET = 1.0F;
   private static final EquipmentSlot[] EQUIPMENT_ORDER = new EquipmentSlot[]{
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
   };
   private static final TargetElement.Style MINI = new TargetElement.Style(100.0F, 40.0F, 6.0F, 5.0F, 5.0F, 22.0F, false);
   private static final TargetElement.Style POWERZ_STYLE = new TargetElement.Style(0.0F, 20.0F, 5.0F, 4.0F, 8.0F, 11.0F, true);
   private static final float BAR_GAP = 4.0F;
   private static final TargetElement.Style GSMAR = new TargetElement.Style(0.0F, 40.0F, 5.0F, 4.0F, 5.5F, 22.0F, false);
   private static final String GSMAR_WIDTH_TEMPLATE = "Distance: 000.0m";
   private static final float GSMAR_TEXT_TRAIL = 6.0F;
   private static final float GSMAR_MIN_WIDTH = 82.0F;
   private static final float GSMAR_MAX_WIDTH = 136.0F;
   private static final float GSMAR_SCALE = 0.95F;
   private static final int GSMAR_HEALTH_DARK = ColorUtil.rgb(16, 94, 34);
   private static final int GSMAR_HEALTH_LIGHT = ColorUtil.rgb(138, 255, 146);
   private static final int GSMAR_ARMOR_DARK = ColorUtil.rgb(12, 62, 154);
   private static final int GSMAR_ARMOR_LIGHT = ColorUtil.rgb(124, 214, 255);
   private static final float GSMAR_NAME_SIZE = 8.5F;
   private static final float GSMAR_LINE_SIZE = 7.0F;
   private static final float GSMAR_NAME_CENTER = 4.5F;
   private static final float GSMAR_HEALTH_CENTER = 12.5F;
   private static final float GSMAR_DISTANCE_CENTER = 20.0F;
   private static final float GSMAR_BAR_HEIGHT = 2.5F;
   private static final float GSMAR_BAR_TOP_GAP = 3.0F;
   private static final float GSMAR_BAR_GAP = 2.0F;
   private static final float GSMAR_MAX_ARMOR = 20.0F;
   private static final float PZ_PAD = 4.0F;
   private static final float PZ_HEAD = 11.0F;
   private static final float PZ_GAP = 8.0F;
   private static final float PZ_NAME_SIZE = 5.5F;
   private static final float PZ_NAME_CENTER = 6.5F;
   private static final float PZ_ITEM_SIZE = 5.5F;
   private static final float PZ_ITEM_GAP = 1.0F;
   private static final float PZ_ITEM_TOP = 11.0F;
   private static final float PZ_COUNT_SIZE = 4.5F;
   private static final float PZ_COUNT_GAP = 1.0F;
   private static final float PZ_RING_SIZE = 13.0F;
   private static final float PZ_RING_GAP = 6.0F;
   private static final float PZ_RING_STROKE = 0.07F;
   private static final float PZ_RADIUS = 5.0F;
   private static final float PZ_HEIGHT = 20.0F;
   private static final float PZ_MIN_WIDTH = 84.0F;
   private static final float PZ_MAX_NAME_WIDTH = 70.0F;
   private static final int PZ_RING_BG = ColorUtil.rgba(232, 239, 251, 32);
   private List<ItemStack> powerzEquipment = List.of();
   private static final float D_PAD = 4.0F;
   private static final float D_HEAD = 22.0F;
   private static final float D_GAP = 6.5F;
   private static final float D_NAME_SIZE = 8.0F;
   private static final float D_LINE_SIZE = 6.0F;
   private static final float D_NAME_CENTER = 5.0F;
   private static final float D_HP_CENTER = 12.0F;
   private static final float D_DISTANCE_CENTER = 18.0F;
   private static final float D_HPBAR_W = 4.5F;
   private static final float D_HPBAR_GAP = 5.0F;
   private static final float D_RADIUS = 6.0F;
   private static final float D_ITEM = 11.0F;
   private static final float D_ITEM_GAP = 2.5F;
   private static final float D_BODY_GAP = 3.0F;
   private static final float D_BODY_OVERLAP = 6.0F;
   private static final float D_MIN_WIDTH = 96.0F;
   private static final String D_LINE_TEMPLATE = "Distance: 000.0";
   private static final int D_HEADER_BG = ColorUtil.rgb(30, 30, 30);
   private static final int D_BODY_BG = ColorUtil.rgba(19, 20, 22, 179);
   private int defaultItemCount;
   private TargetElement.Style style = MINI;
   private LivingEntity target;
   private LivingEntity smoothedFor;
   private float smoothedHealth;
   private float smoothedAbsorption;
   private float smoothedArmor;
   private long lastFrameTime;
   private LivingEntity fadeEntity;
   private float fadeAlpha;
   private long lastFadeTime;
   private final TargetHudParticles particles = new TargetHudParticles();
   private LivingEntity damageFor;
   private float lastTotalHealth;
   private float frameDamage;
   private float dripCarry;

   public TargetElement() {
      super("target", "Target");
   }

   public boolean healthBasedColor() {
      return MenuConfigStore.getBoolean("hud.target.healthBasedColor", false);
   }

   public void setHealthBasedColor(boolean value) {
      MenuConfigStore.save(data -> data.addProperty("hud.target.healthBasedColor", value));
   }

   public boolean absorptionVisible() {
      return MenuConfigStore.getBoolean("hud.target.absorptionVisible", true);
   }

   public void setAbsorptionVisible(boolean value) {
      MenuConfigStore.save(data -> data.addProperty("hud.target.absorptionVisible", value));
   }

   public boolean itemUseVisible() {
      return MenuConfigStore.getBoolean("hud.target.itemUseVisible", true);
   }

   public void setItemUseVisible(boolean value) {
      MenuConfigStore.save(data -> data.addProperty("hud.target.itemUseVisible", value));
   }

   public boolean armorVisible() {
      return MenuConfigStore.getBoolean("hud.target.armorVisible", true);
   }

   public void setArmorVisible(boolean value) {
      MenuConfigStore.save(data -> data.addProperty("hud.target.armorVisible", value));
   }

   @Override
   protected float defaultY(float unit) {
      return 340.0F * unit;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      this.style = this.resolveStyle();
      unit *= this.localScale();
      this.target = this.resolveTarget(mc);
      if (this.target != null) {
         this.fadeEntity = this.target;
      }

      this.updateFade();
      if (this.fadeEntity != null && (this.target != null || !(this.fadeAlpha <= 0.02F))) {
         LivingEntity shown = this.target != null ? this.target : this.fadeEntity;
         if (this.style == GSMAR) {
            this.width = this.gsmarWidth(shown, unit);
            this.height = this.style.height() * unit;
         } else if (this.style == POWERZ_STYLE) {
            this.powerzEquipment = this.equipmentStacks(shown);
            this.width = this.powerzWidth(shown, unit);
            this.height = 20.0F * unit;
         } else {
            this.defaultItemCount = this.countEquipment(shown);
            this.width = this.defaultWidth(shown, unit);
            this.height = this.defaultHeight(unit);
         }
      } else {
         this.width = 0.0F;
         this.height = 0.0F;
      }
   }

   private TargetElement.Style resolveStyle() {
      if (HudFeature.powerzTarget()) {
         return POWERZ_STYLE;
      } else {
         return HudFeature.gsmarTarget() ? GSMAR : MINI;
      }
   }

   private boolean equipmentVisible(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD, CHEST, LEGS, FEET -> this.armorVisible();
         default -> true;
      };
   }

   private int countEquipment(LivingEntity entity) {
      if (entity == null) {
         return 0;
      }

      int count = 0;

      for (EquipmentSlot slot : EQUIPMENT_ORDER) {
         if (this.equipmentVisible(slot) && !entity.getItemBySlot(slot).isEmpty()) {
            count++;
         }
      }

      return count;
   }

   private List<ItemStack> equipmentStacks(LivingEntity entity) {
      List<ItemStack> stacks = new ArrayList<>(EQUIPMENT_ORDER.length);

      for (EquipmentSlot slot : EQUIPMENT_ORDER) {
         if (this.equipmentVisible(slot)) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
               stacks.add(stack);
            }
         }
      }

      return stacks;
   }

   private float defaultHeight(float unit) {
      float total = 30.0F + (this.defaultItemCount > 0 ? 18.0F : 0.0F);
      return total * unit;
   }

   private float defaultWidth(LivingEntity entity, float unit) {
      MsdfFont semi = UiFonts.sfPro(600);
      MsdfFont reg = UiFonts.sfPro(400);
      float nameSize = 8.0F * unit;
      float lineSize = 6.0F * unit;
      String name = entity != null ? entity.getName().getString() : "";
      float textWidth = Math.max(
         semi.measureWidth(name, nameSize, nameSize * UiFontStyle.SEMIBOLD.letterSpacingEm()), reg.measureWidth("Distance: 000.0", lineSize, 0.0F)
      );
      float headerWidth = 32.5F * unit + textWidth + 13.5F * unit;
      float bodyWidth = this.defaultItemCount > 0 ? (8.0F + this.defaultItemCount * 11.0F + (this.defaultItemCount - 1) * 2.5F) * unit : 0.0F;
      return Math.max(Math.max(96.0F * unit, headerWidth), bodyWidth);
   }

   @Override
   protected boolean preservePositionOnContentResize() {
      return true;
   }

   private float gsmarWidth(LivingEntity entity, float unit) {
      MsdfFont font = UiFonts.sfProDisplay();
      float nameSize = 8.5F * unit;
      float lineSize = 7.0F * unit;
      float textWidth = Math.max(
         font.measureWidth(entity.getName().getString(), nameSize, nameSize * UiFontStyle.SEMIBOLD.letterSpacingEm()),
         font.measureWidth("Distance: 000.0m", lineSize, lineSize * UiFontStyle.MEDIUM.letterSpacingEm())
      );
      float content = (this.style.padding() * 2.0F + this.style.headSize() + this.style.gap() + 6.0F) * unit + textWidth;
      return Math.clamp(content, 82.0F * unit, 136.0F * unit);
   }

   private float localScale() {
      return this.style == POWERZ_STYLE ? 1.3299999F : 0.95F;
   }

   private float powerzWidth(LivingEntity entity, float unit) {
      MsdfFont font = UiFonts.sfPro(600);
      float nameSize = 5.5F * unit;
      float textWidth = font.measureWidth(entity.getName().getString(), nameSize, nameSize * UiFontStyle.SEMIBOLD.letterSpacingEm());
      textWidth = Math.min(textWidth, 70.0F * unit);
      float itemsWidth = 0.0F;

      for (ItemStack stack : this.powerzEquipment) {
         if (itemsWidth > 0.0F) {
            itemsWidth += 1.0F * unit;
         }

         itemsWidth += this.powerzStackWidth(stack, unit);
      }

      float fixedWidth = 46.0F * unit;
      return Math.max(84.0F * unit, fixedWidth + Math.max(textWidth, itemsWidth));
   }

   private float powerzStackWidth(ItemStack stack, float unit) {
      float width = 5.5F * unit;
      if (stack.getCount() > 1) {
         width += 1.0F * unit
            + UiFonts.sfPro(500).measureWidth(Integer.toString(stack.getCount()), 4.5F * unit, 4.5F * unit * UiFontStyle.MEDIUM.letterSpacingEm());
      }

      return width;
   }

   private void updateFade() {
      long now = System.currentTimeMillis();
      float delta = this.lastFadeTime == 0L ? 0.016F : (float)Math.min(100L, now - this.lastFadeTime) / 1000.0F;
      this.lastFadeTime = now;
      float goal = this.target != null ? 1.0F : 0.0F;
      float step = Math.clamp(delta * 12.0F, 0.0F, 1.0F);
      this.fadeAlpha = this.fadeAlpha + (goal - this.fadeAlpha) * step;
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      LivingEntity entity = this.target != null ? this.target : this.fadeEntity;
      if (entity != null) {
         float alpha = this.fadeAlpha;
         unit *= this.localScale();
         this.frameDamage = this.trackDamage(entity);
         this.particles.update();
         if (this.style == GSMAR) {
            this.drawGlassPanel(unit, this.style.radius() * unit, alpha);
            this.drawGsmar(mc, entity, unit, alpha);
         } else if (this.style == POWERZ_STYLE) {
            this.drawPowerz(mc, entity, unit, alpha);
         } else {
            this.drawDefault(mc, entity, unit, alpha);
         }
      }
   }

   private void drawDefault(Minecraft mc, LivingEntity entity, float unit, float alpha) {
      float x = this.x;
      float y = this.y;
      float width = this.width;
      float headerHeight = 30.0F * unit;
      List<ItemStack> stacks = this.equipmentStacks(entity);
      boolean hasBody = !stacks.isEmpty() && this.height - headerHeight > 0.5F;
      this.drawWatermarkPanel(x, y, width, this.height, 5.0F * unit, unit, alpha);
      float headTop = y + 4.0F * unit;
      float headSize = 22.0F * unit;
      this.drawHead(entity, x + 4.0F * unit, headTop, headSize, headSize * 0.18F, alpha);
      this.drawEatingOverlay(mc, entity, x + 4.0F * unit, headTop, headSize, headSize * 0.18F, alpha);
      float hpBarWidth = 4.5F * unit;
      float hpBarX = x + width - 4.0F * unit - hpBarWidth;
      float hpBarTop = y + 4.0F * unit;
      float hpBarHeight = headerHeight - 8.0F * unit;
      float health = HealthUtil.getHealth(entity);
      float absorption = Math.max(0.0F, entity.getAbsorptionAmount());
      float fraction = this.healthFraction(entity, health, absorption, entity.getArmorValue());
      Render2DUtil.rect(hpBarX, hpBarTop, hpBarWidth, hpBarHeight).color(ColorUtil.multiplyAlpha(BAR_TRACK_COLOR, alpha)).radius(hpBarWidth / 2.0F).draw();
      float fillTop = hpBarTop + hpBarHeight;
      if (fraction > 0.0F) {
         float fillHeight = Math.max(hpBarWidth, hpBarHeight * fraction);
         fillTop = hpBarTop + hpBarHeight - fillHeight;
         Render2DUtil.rect(hpBarX, fillTop, hpBarWidth, fillHeight)
            .color(ColorUtil.multiplyAlpha(this.healthColor(fraction), alpha))
            .radius(hpBarWidth / 2.0F)
            .draw();
      }

      if (this.target != null) {
         if (HudFeature.targetHealthParticles()) {
            if (this.frameDamage > 0.01F) {
               this.particles
                  .emitHealthVertical(hpBarX, hpBarWidth, fillTop, unit, HudFeature.hudAccent(), TargetHudParticles.countFor(this.frameDamage, 4, 14));
            }

            int drip = this.drainDrip(health);
            if (drip > 0) {
               this.particles.emitHealthVertical(hpBarX, hpBarWidth, fillTop, unit, HudFeature.hudAccent(), drip);
            }
         }

         if (this.frameDamage > 0.01F && HudFeature.targetHeadParticles()) {
            this.particles
               .emitHeadBurst(x + 4.0F * unit, headTop, headSize, unit, HudFeature.hudAccent(), TargetHudParticles.countFor(this.frameDamage, 6, 18));
         }
      }

      MsdfFont semi = UiFonts.sfPro(600);
      MsdfFont reg = UiFonts.sfPro(400);
      float nameSize = 8.0F * unit;
      float lineSize = 6.0F * unit;
      float infoX = x + 4.0F * unit + headSize + 6.5F * unit;
      float textRight = hpBarX - 5.0F * unit;
      float textLimit = Math.max(12.0F * unit, textRight - infoX);
      float distance = mc.player != null ? mc.player.distanceTo(entity) : 0.0F;
      String name = semi.ellipsize(entity.getName().getString(), nameSize, nameSize * UiFontStyle.SEMIBOLD.letterSpacingEm(), textLimit);
      String hpText = reg.ellipsize(String.format(Locale.ROOT, "HP: %.1f", this.displayedHealth(health, absorption)), lineSize, 0.0F, textLimit);
      String distanceText = reg.ellipsize(String.format(Locale.ROOT, "Distance: %.1f", distance), lineSize, 0.0F, textLimit);
      Render2DUtil.text(infoX, semi.centeredTextY(headTop + 5.0F * unit, nameSize), nameSize, name)
         .font(semi)
         .style(UiFontStyle.SEMIBOLD)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, alpha))
         .draw();
      Render2DUtil.text(infoX, reg.centeredTextY(headTop + 12.0F * unit, lineSize), lineSize, hpText)
         .font(reg)
         .style(UiFontStyle.REGULAR)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha))
         .draw();
      Render2DUtil.text(infoX, reg.centeredTextY(headTop + 18.0F * unit, lineSize), lineSize, distanceText)
         .font(reg)
         .style(UiFontStyle.REGULAR)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha))
         .draw();
      if (hasBody && this.target != null && alpha >= 0.75F) {
         float itemY = y + headerHeight + 3.0F * unit;
         this.drawItemRow(mc, stacks, x + 4.0F * unit, itemY, 11.0F * unit, 2.5F * unit);
      }

      this.particles.draw(alpha);
   }

   private void drawItemRow(Minecraft mc, List<ItemStack> stacks, float startX, float y, float itemSize, float gap) {
      GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
      if (extractor != null) {
         Render2DUtil.flush();
         Matrix3x2fStack pose = extractor.pose();
         float guiScale = mc.getWindow().getGuiScale();
         float scale = itemSize / 16.0F;
         float itemY = Math.round(y * guiScale) / guiScale;
         float cursor = startX;

         for (ItemStack stack : stacks) {
            float itemX = Math.round(cursor * guiScale) / guiScale;
            pose.pushMatrix();
            pose.translate(itemX, itemY);
            pose.scale(scale);
            extractor.item(stack, 0, 0);
            pose.popMatrix();
            cursor += itemSize + gap;
         }
      }
   }

   private void drawGsmar(Minecraft mc, LivingEntity entity, float unit, float alpha) {
      MsdfFont font = UiFonts.sfProDisplay();
      float padding = this.style.padding() * unit;
      float contentX = this.x + padding;
      float contentRight = this.x + this.width - padding;
      float headTop = this.y + padding;
      float headSize = this.style.headSize() * unit;
      this.drawHead(entity, contentX, headTop, headSize, headSize * 0.12F, alpha);
      this.drawEatingOverlay(mc, entity, contentX, headTop, headSize, headSize * 0.12F, alpha);
      float infoX = contentX + headSize + this.style.gap() * unit;
      float infoWidth = contentRight - infoX - 6.0F * unit;
      float nameSize = 8.5F * unit;
      float lineSize = 7.0F * unit;
      float nameSpacing = nameSize * UiFontStyle.SEMIBOLD.letterSpacingEm();
      float lineSpacing = lineSize * UiFontStyle.MEDIUM.letterSpacingEm();
      float health = HealthUtil.getHealth(entity);
      float absorption = Math.max(0.0F, entity.getAbsorptionAmount());
      float armor = Math.min(entity.getArmorValue(), 20.0F);
      float distance = mc.player != null ? mc.player.distanceTo(entity) : 0.0F;
      String name = font.ellipsize(entity.getName().getString(), nameSize, nameSpacing, infoWidth);
      String healthText = font.ellipsize(String.format(Locale.ROOT, "Health: %.1f", this.displayedHealth(health, absorption)), lineSize, lineSpacing, infoWidth);
      String distanceText = font.ellipsize(String.format(Locale.ROOT, "Distance: %.1fm", distance), lineSize, lineSpacing, infoWidth);
      Render2DUtil.text(infoX, font.centeredTextY(headTop + 4.5F * unit, nameSize), nameSize, name)
         .style(UiFontStyle.SEMIBOLD)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, alpha))
         .draw();
      Render2DUtil.text(infoX, font.centeredTextY(headTop + 12.5F * unit, lineSize), lineSize, healthText)
         .style(UiFontStyle.MEDIUM)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha))
         .draw();
      Render2DUtil.text(infoX, font.centeredTextY(headTop + 20.0F * unit, lineSize), lineSize, distanceText)
         .style(UiFontStyle.MEDIUM)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha))
         .draw();
      float fraction = this.healthFraction(entity, health, absorption, armor);
      int color = this.healthColor(fraction);
      float barWidth = contentRight - contentX;
      float barHeight = 2.5F * unit;
      float healthBarY = headTop + headSize + 3.0F * unit;
      float armorBarY = healthBarY + barHeight + 2.0F * unit;
      drawGsmarBar(contentX, healthBarY, barWidth, barHeight, fraction, ColorUtil.multiplyRgb(color, 0.7F), color, alpha);
      if (this.armorVisible()) {
         drawGsmarBar(
            contentX, armorBarY, barWidth, barHeight, this.smoothedArmor / 20.0F, ColorUtil.multiplyRgb(glassMutedColor(), 0.7F), glassMutedColor(), alpha
         );
      }

      if (this.target != null && this.frameDamage > 0.01F && HudFeature.targetHeadParticles()) {
         this.particles.emitHeadBurst(contentX, headTop, headSize, unit, HudFeature.hudAccent(), TargetHudParticles.countFor(this.frameDamage, 6, 18));
      }

      this.particles.draw(alpha);
   }

   private void drawPowerz(Minecraft mc, LivingEntity entity, float unit, float alpha) {
      float height = 20.0F * unit;
      this.drawWatermarkPanel(this.x, this.y, this.width, height, 5.0F * unit, unit, alpha);
      float headX = this.x + 4.0F * unit;
      float headSize = 11.0F * unit;
      float headTop = this.y + (height - headSize) * 0.5F;
      this.drawHead(entity, headX, headTop, headSize, headSize * 0.12F, alpha);
      this.drawEatingOverlay(mc, entity, headX, headTop, headSize, headSize * 0.12F, alpha);
      float health = HealthUtil.getHealth(entity);
      float absorption = Math.max(0.0F, entity.getAbsorptionAmount());
      float fraction = this.healthFraction(entity, health, absorption, entity.getArmorValue());
      float ringSize = 13.0F * unit;
      float ringCx = this.x + this.width - 4.0F * unit - ringSize * 0.5F;
      float ringCy = this.y + height * 0.5F;
      RingProgress.draw(ringCx, ringCy, ringSize, 1.0F, 0.07F, ColorUtil.multiplyAlpha(PZ_RING_BG, alpha));
      RingProgress.draw(ringCx, ringCy, ringSize, fraction, 0.07F, ColorUtil.multiplyAlpha(this.healthColor(fraction), alpha));
      MsdfFont font = UiFonts.sfPro(600);
      String hpText = String.format(Locale.ROOT, "%.1f", this.displayedHealth(health, absorption));
      if (hpText.endsWith(".0")) {
         hpText = hpText.substring(0, hpText.length() - 2);
      }

      float hpSize = 6.0F * unit;
      float hpWidth = font.measureWidth(hpText, hpSize, hpSize * UiFontStyle.SEMIBOLD.letterSpacingEm());
      hpSize *= Math.min(1.0F, (ringSize - 4.0F * unit) / Math.max(0.01F, hpWidth));
      Render2DUtil.text(ringCx, font.centeredTextY(ringCy, hpSize), hpSize, hpText)
         .font(font)
         .style(UiFontStyle.SEMIBOLD)
         .align(TextAlign.CENTER)
         .color(ColorUtil.multiplyAlpha(-1, alpha))
         .draw();
      float infoX = headX + headSize + 8.0F * unit;
      float textLimit = ringCx - ringSize * 0.5F - 6.0F * unit - infoX;
      float nameSize = 5.5F * unit;
      String name = font.ellipsize(entity.getName().getString(), nameSize, nameSize * UiFontStyle.SEMIBOLD.letterSpacingEm(), Math.max(0.0F, textLimit));
      Render2DUtil.text(infoX, font.centeredTextY(this.y + 6.5F * unit, nameSize), nameSize, name)
         .font(font)
         .style(UiFontStyle.SEMIBOLD)
         .color(ColorUtil.multiplyAlpha(-1, alpha))
         .draw();
      if (this.target != null && alpha >= 0.75F) {
         this.drawPowerzEquipment(mc, infoX, this.y + 11.0F * unit, unit, alpha);
      }

      if (this.target != null && this.frameDamage > 0.01F && HudFeature.targetHeadParticles()) {
         this.particles.emitHeadBurst(headX, headTop, headSize, unit, HudFeature.hudAccent(), TargetHudParticles.countFor(this.frameDamage, 6, 18));
      }

      this.particles.draw(alpha);
   }

   private void drawPowerzEquipment(Minecraft mc, float startX, float itemY, float unit, float alpha) {
      float itemSize = 5.5F * unit;
      float cursor = startX;

      for (ItemStack stack : this.powerzEquipment) {
         this.drawItemRow(mc, List.of(stack), cursor, itemY, itemSize, 0.0F);
         cursor += this.powerzStackWidth(stack, unit) + 1.0F * unit;
      }

      MsdfFont font = UiFonts.sfPro(500);
      float size = 4.5F * unit;
      cursor = startX;

      for (ItemStack stack : this.powerzEquipment) {
         if (stack.getCount() > 1) {
            Render2DUtil.text(cursor + itemSize + 1.0F * unit, font.centeredTextY(itemY + itemSize * 0.5F, size), size, Integer.toString(stack.getCount()))
               .font(font)
               .style(UiFontStyle.MEDIUM)
               .color(ColorUtil.multiplyAlpha(-1, alpha))
               .draw();
         }

         cursor += this.powerzStackWidth(stack, unit) + 1.0F * unit;
      }
   }

   private static void drawGsmarBar(float x, float y, float width, float height, float fraction, int darkColor, int lightColor, float alpha) {
      float radius = height / 2.0F;
      Render2DUtil.rect(x, y, width, height).color(ColorUtil.multiplyAlpha(BAR_TRACK_COLOR, alpha)).radius(radius).draw();
      float fillWidth = width * Math.clamp(fraction, 0.0F, 1.0F);
      if (!(fillWidth < height)) {
         Render2DUtil.rect(x, y, fillWidth, height)
            .color(
               ColorUtil.multiplyAlpha(darkColor, alpha),
               ColorUtil.multiplyAlpha(lightColor, alpha),
               ColorUtil.multiplyAlpha(lightColor, alpha),
               ColorUtil.multiplyAlpha(darkColor, alpha)
            )
            .radius(radius)
            .draw();
      }
   }

   private void drawEatingOverlay(Minecraft mc, LivingEntity entity, float headX, float headTop, float headSize, float headRadius, float alpha) {
      if (this.itemUseVisible() && this.target != null && entity.isUsingItem()) {
         ItemStack use = entity.getUseItem();
         if (!use.isEmpty()) {
            ItemUseAnimation animation = use.getUseAnimation();
            if (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK) {
               Render2DUtil.rect(headX, headTop, headSize, headSize)
                  .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(0, 0, 0, 165), alpha))
                  .radius(headRadius)
                  .draw();
               float itemSize = headSize * 0.6F;
               float itemX = headX + (headSize - itemSize) / 2.0F;
               float itemY = headTop + (headSize - itemSize) / 2.0F - headSize * 0.08F;
               if (alpha >= 0.75F) {
                  this.drawItemRow(mc, List.of(use), itemX, itemY, itemSize, 0.0F);
               }

               int remainingTicks = Math.max(0, entity.getUseItemRemainingTicks());
               String timer = String.format(Locale.ROOT, "%.1f", remainingTicks / 20.0F);
               MsdfFont font = UiFonts.sfPro(600);
               float timerSize = headSize * 0.26F;
               float timerWidth = font.measureWidth(timer, timerSize, 0.0F);
               Render2DUtil.text(headX + (headSize - timerWidth) / 2.0F, font.centeredTextY(headTop + headSize - headSize * 0.17F, timerSize), timerSize, timer)
                  .font(font)
                  .style(UiFontStyle.SEMIBOLD)
                  .outline(ColorUtil.multiplyAlpha(ColorUtil.rgba(0, 0, 0, 200), alpha), 0.35F)
                  .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, alpha))
                  .draw();
            }
         }
      }
   }

   private void drawHead(LivingEntity entity, float x, float y, float size, float radius, float alpha) {
      int tint = ColorUtil.multiplyAlpha(-1, alpha);
      if (entity instanceof AbstractClientPlayer player) {
         PlayerHead.draw(x, y, size, player.getSkin().body().texturePath(), radius, tint);
      } else {
         Render2DUtil.rect(x, y, size, size).color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha)).radius(radius).draw();
         TargetElement.FaceIcon icon = this.resolveFaceIcon(entity);
         if (icon != null) {
            Render2DUtil.texture(x, y, size, size, icon.texture()).managed().uv(icon.u0(), icon.v0(), icon.u1(), icon.v1()).radius(radius).color(tint).draw();
         }
      }
   }

   private TargetElement.FaceIcon resolveFaceIcon(LivingEntity entity) {
      EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
      EntityRenderState state = renderer.createRenderState(entity, 1.0F);
      if (renderer instanceof LivingEntityRenderer livingRenderer && state instanceof LivingEntityRenderState livingState) {
         Identifier texture = livingRenderer.getTextureLocation(livingState);
         Model<?> model = livingRenderer.getModel();
         ModelPart root = model.root();
         Function<String, ModelPart> lookup = root.createPartLookup();
         ModelPart iconPart = lookup.apply("head");
         if (iconPart == null) {
            iconPart = lookup.apply("body");
         }

         if (iconPart == null) {
            iconPart = root;
         }

         TargetElement.FaceIcon icon = findLargestFrontFace(texture, iconPart);
         return icon == null && iconPart != root ? findLargestFrontFace(texture, root) : icon;
      } else {
         return null;
      }
   }

   private static TargetElement.FaceIcon findLargestFrontFace(Identifier texture, ModelPart part) {
      TargetElement.FaceIcon result = null;
      float largestArea = 0.0F;

      for (ModelPart candidate : part.getAllParts()) {
         for (Cube cube : ((ModelPartAccessor)candidate).furryhook$getCubes()) {
            for (Polygon polygon : cube.polygons) {
               if (!(polygon.normal().z() > -0.9F)) {
                  float minU = Float.POSITIVE_INFINITY;
                  float minV = Float.POSITIVE_INFINITY;
                  float maxU = Float.NEGATIVE_INFINITY;
                  float maxV = Float.NEGATIVE_INFINITY;

                  for (Vertex vertex : polygon.vertices()) {
                     minU = Math.min(minU, vertex.u());
                     minV = Math.min(minV, vertex.v());
                     maxU = Math.max(maxU, vertex.u());
                     maxV = Math.max(maxV, vertex.v());
                  }

                  float area = (maxU - minU) * (maxV - minV);
                  if (area > largestArea) {
                     largestArea = area;
                     result = new TargetElement.FaceIcon(texture, minU, minV, maxU, maxV);
                  }
               }
            }
         }
      }

      return result;
   }

   private void drawItems(Minecraft mc, LivingEntity entity, float x, float y, float unit) {
      List<ItemStack> stacks = this.equipmentStacks(entity);
      if (!stacks.isEmpty()) {
         GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
         if (extractor != null) {
            Render2DUtil.flush();
            Matrix3x2fStack pose = extractor.pose();
            float guiScale = mc.getWindow().getGuiScale();
            float itemSize = 12.0F * unit;
            float scale = itemSize / 16.0F;
            float itemY = Math.round(y * guiScale) / guiScale;
            float cursor = x;

            for (ItemStack stack : stacks) {
               float itemX = Math.round(cursor * guiScale) / guiScale;
               pose.pushMatrix();
               pose.translate(itemX, itemY);
               pose.scale(scale);
               extractor.item(stack, 0, 0);
               pose.popMatrix();
               cursor += itemSize + 2.0F * unit;
            }
         }
      }
   }

   private void drawHealthBar(LivingEntity entity, float health, float x, float y, float width, float unit, float alpha) {
      float absorption = Math.max(0.0F, entity.getAbsorptionAmount());
      float fraction = this.healthFraction(entity, health, absorption, entity.getArmorValue());
      float visibleAbsorption = this.absorptionVisible() ? this.smoothedAbsorption : 0.0F;
      float maxHealth = Math.max(1.0F, entity.getMaxHealth() + visibleAbsorption);
      float barHeight = 4.0F * unit;
      Render2DUtil.rect(x, y, width, barHeight).color(ColorUtil.multiplyAlpha(BAR_TRACK_COLOR, alpha)).radius(barHeight / 2.0F).draw();
      float inset = 1.0F * unit;
      float fillHeight = barHeight - inset * 2.0F;
      float innerWidth = width - inset * 2.0F;
      float fillWidth = innerWidth * fraction;
      if (fillWidth >= fillHeight) {
         float absorptionWidth = Math.min(fillWidth, innerWidth * Math.clamp(visibleAbsorption / maxHealth, 0.0F, 1.0F));
         float healthWidth = fillWidth - absorptionWidth;
         float radius = fillHeight / 2.0F;
         boolean hasHealth = healthWidth > 0.01F;
         boolean hasAbsorption = absorptionWidth > 0.01F;
         if (hasHealth) {
            float rightRadius = hasAbsorption ? 0.0F : radius;
            Render2DUtil.rect(x + inset, y + inset, healthWidth, fillHeight)
               .color(ColorUtil.multiplyAlpha(this.healthColor(fraction), alpha))
               .radius(radius, rightRadius, rightRadius, radius)
               .draw();
         }

         if (hasAbsorption) {
            float leftRadius = hasHealth ? 0.0F : radius;
            Render2DUtil.rect(x + inset + healthWidth, y + inset, absorptionWidth, fillHeight)
               .color(
                  ColorUtil.multiplyAlpha(ABSORPTION_DARK_COLOR, alpha),
                  ColorUtil.multiplyAlpha(ABSORPTION_LIGHT_COLOR, alpha),
                  ColorUtil.multiplyAlpha(ABSORPTION_LIGHT_COLOR, alpha),
                  ColorUtil.multiplyAlpha(ABSORPTION_DARK_COLOR, alpha)
               )
               .radius(leftRadius, radius, radius, leftRadius)
               .draw();
         }
      }
   }

   private float trackDamage(LivingEntity entity) {
      float total = HealthUtil.getHealth(entity);
      if (this.damageFor != entity) {
         this.damageFor = entity;
         this.lastTotalHealth = total;
         this.dripCarry = 0.0F;
         this.particles.clear();
         return 0.0F;
      } else {
         float damage = Math.max(0.0F, this.lastTotalHealth - total);
         this.lastTotalHealth = total;
         return damage;
      }
   }

   private int drainDrip(float health) {
      float lag = this.smoothedHealth - health;
      if (lag <= 0.1F) {
         this.dripCarry = 0.0F;
         return 0;
      } else {
         this.dripCarry = this.dripCarry + Math.min(1.2F, lag * 0.35F);
         int count = (int)this.dripCarry;
         this.dripCarry -= count;
         return count;
      }
   }

   private float displayedHealth(float totalHealth, float absorption) {
      return this.absorptionVisible() ? totalHealth : Math.max(0.0F, totalHealth - absorption);
   }

   private float healthFraction(LivingEntity entity, float health, float absorption, float armor) {
      float value = this.smoothHealth(entity, health, absorption, armor);
      float visibleAbsorption = this.absorptionVisible() ? this.smoothedAbsorption : 0.0F;
      float maxHealth = Math.max(1.0F, entity.getMaxHealth() + visibleAbsorption);
      return Math.clamp(this.displayedHealth(value, this.smoothedAbsorption) / maxHealth, 0.0F, 1.0F);
   }

   private int healthColor(float fraction) {
      return this.healthBasedColor() ? ColorUtil.lerp(-65536, -16711936, fraction) : HudFeature.hudAccent();
   }

   private float smoothHealth(LivingEntity entity, float health, float absorption, float armor) {
      long now = System.currentTimeMillis();
      float delta = this.lastFrameTime == 0L ? 0.016F : (float)Math.min(100L, now - this.lastFrameTime) / 1000.0F;
      this.lastFrameTime = now;
      if (this.smoothedFor != entity) {
         this.smoothedFor = entity;
         this.smoothedHealth = health;
         this.smoothedAbsorption = absorption;
         this.smoothedArmor = armor;
      }

      float step = Math.clamp(delta * 10.0F, 0.0F, 1.0F);
      this.smoothedHealth = this.smoothedHealth + (health - this.smoothedHealth) * step;
      this.smoothedAbsorption = this.smoothedAbsorption + (absorption - this.smoothedAbsorption) * step;
      this.smoothedArmor = this.smoothedArmor + (armor - this.smoothedArmor) * step;
      return this.smoothedHealth;
   }

   private LivingEntity resolveTarget(Minecraft mc) {
      AuraFeature aura = AuraFeature.getMarkerFeature();
      LivingEntity current = aura != null ? aura.getCurrentTarget() : null;
      if (current == null || !current.isAlive()) {
         TriggerBotFeature triggerBot = TriggerBotFeature.getEnabled();
         current = triggerBot != null ? triggerBot.getCurrentTarget() : null;
      }

      if ((current == null || !current.isAlive()) && (showcase(mc) || this.alwaysShow())) {
         current = mc.player;
      }

      return current != null && current.isAlive() ? current : null;
   }

   private record FaceIcon(Identifier texture, float u0, float v0, float u1, float v1) {
   }

   private record Style(float width, float height, float radius, float padding, float gap, float headSize, boolean showItems) {
   }
}
