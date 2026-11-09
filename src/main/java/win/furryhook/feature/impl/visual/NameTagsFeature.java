package win.furryhook.feature.impl.visual;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Matrix3x2fStack;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.render.Render2DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.FriendManager;
import win.furryhook.utils.combat.HealthUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.TextAlign;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;
import win.furryhook.utils.render.gui.VanillaTextMetrics;

public final class NameTagsFeature extends Feature {
   private static final float SCALE = 0.8F;
   private static final float NAME_TEXT_SIZE = 11.0F;
   private static final float ENCHANT_TEXT_SIZE = 8.0F;
   private static final float EFFECT_TEXT_SIZE = 9.0F;
   private static final float ITEM_ICON_SIZE = 14.0F;
   private static final float NAME_PART_GAP = 6.0F;
   private static final float HP_GAP = 4.0F;
   private static final float TAG_PAD_X = 4.0F;
   private static final float TAG_PAD_Y = 2.0F;
   private static final float HEAD_TO_TAG_GAP = 3.0F;
   private static final float ARMOR_ROW_GAP = 5.0F;
   private static final float ENCHANT_ROW_GAP = 2.0F;
   private static final float ITEM_ICON_GAP = 2.0F;
   private static final float FEET_GAP = 5.0F;
   private static final float EFFECTS_SIDE_GAP = 6.0F;
   private static final float EFFECT_LINE_GAP = 2.0F;
   private static final double MAX_DISTANCE = 100.0;
   private static final int SHORT_EFFECT_TICKS = 20;
   private static final int TAG_BG = ColorUtil.rgba(0, 0, 0, 120);
   private static final int FRIEND_TAG_BG = ColorUtil.rgba(20, 130, 55, 130);
   private static final int HEALTH_COLOR = ColorUtil.rgb(255, 85, 85);
   private static final int FRIEND_BRACKET_COLOR = ColorUtil.rgb(170, 170, 170);
   private static final int FRIEND_LETTER_COLOR = ColorUtil.rgb(85, 255, 85);
   private static final int ENCHANT_COLOR = ColorUtil.rgb(255, 255, 255);
   private static final UiFontStyle TAG_STYLE = UiFontStyle.MEDIUM;
   private static final int OUTLINE_COLOR = ColorUtil.rgba(0, 0, 0, 190);
   private static final float OUTLINE_THICKNESS = 0.35F;
   private static final String FONT_UI = "Google Sans";
   private static final String FONT_PRODUCT_SANS = "Product Sans";
   private static final String FONT_VANILLA = "Vanilla";
   private static final EquipmentSlot[] EQUIPMENT_ORDER = new EquipmentSlot[]{
      EquipmentSlot.OFFHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.MAINHAND
   };
   private static final Pattern LEGACY_COLOR_CODE = Pattern.compile("§[xX](§[0-9a-fA-F]){6}|§[0-9a-fA-Fk-oK-OrR]");
   public final NumberSetting scale = this.register(new NumberSetting("Scale", 1.0, 0.95, 1.2, 0.05, "x"));
   public final ModeSetting font = this.register(new ModeSetting("Font", "Google Sans", "Google Sans", "Product Sans", "Vanilla"));
   public final BooleanSetting outline = this.register(new BooleanSetting("Text Outline", true).visibleWhen(() -> !this.font.is("Vanilla")));
   public final MultiSelectSetting targets = this.register(
      new MultiSelectSetting("Targets", Set.of("Players", "Items"), "Players", "Hostile", "Passive", "Villagers", "Friends", "Naked", "Self", "Items")
   );
   public final BooleanSetting privilege = this.register(new BooleanSetting("Privilege", true));
   public final BooleanSetting health = this.register(new BooleanSetting("Health", true));
   public final BooleanSetting viewArmor = this.register(new BooleanSetting("Armor", true));
   public final BooleanSetting viewEnchants = this.register(new BooleanSetting("Enchants", false).visibleWhen(() -> this.viewArmor.getValue()));
   public final BooleanSetting viewEffects = this.register(new BooleanSetting("Effects", false));
   private boolean frameSelf;
   private boolean frameFriends;
   private boolean frameNaked;
   private boolean framePlayers;
   private boolean frameVillagers;
   private boolean framePassive;
   private boolean frameHostile;
   private boolean frameItems;
   private boolean frameFirstPerson;
   private boolean frameAnyTarget;
   private static final String[] ROMAN = new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

   public NameTagsFeature() {
      super("NameTags", "Draws styled nametags above players and other entities", FeatureCategory.VISUAL, -1);
   }

   public static boolean shouldHideVanillaTag() {
      return FeatureManager.INSTANCE.getEnabled(NameTagsFeature.class) != null;
   }

   private boolean vanillaFont() {
      return this.font.is("Vanilla");
   }

   private MsdfFont referenceFont() {
      return this.font.is("Product Sans") ? UiFonts.divineProductSans(TAG_STYLE.weight()) : UiFonts.googleSans(TAG_STYLE.weight());
   }

   private MsdfFont fontFor(String text) {
      if (this.font.is("Product Sans")) {
         MsdfFont productSans = UiFonts.divineProductSans(TAG_STYLE.weight());
         if (productSans.canRender(text)) {
            return productSans;
         }
      }

      return UiFonts.getFontForText(text, TAG_STYLE.weight());
   }

   private float msdfSize(float size) {
      float lineHeightEm = this.referenceFont().lineHeight(1.0F);
      return lineHeightEm <= 0.0F ? size : VanillaTextMetrics.lineHeight(size) / lineHeightEm;
   }

   private MsdfFont rankFontFor(String glyph) {
      MsdfFont preferred = this.referenceFont();
      if (preferred.canRender(glyph)) {
         return preferred;
      }

      MsdfFont googleSans = UiFonts.googleSans(TAG_STYLE.weight());
      if (googleSans.canRender(glyph)) {
         return googleSans;
      }

      MsdfFont googleSansFlex = UiFonts.googleSansFlex(TAG_STYLE.weight());
      if (googleSansFlex.canRender(glyph)) {
         return googleSansFlex;
      }

      MsdfFont sfPro = UiFonts.sfPro(TAG_STYLE.weight());
      if (sfPro.canRender(glyph)) {
         return sfPro;
      }

      MsdfFont productSans = UiFonts.divineProductSans(TAG_STYLE.weight());
      return productSans.canRender(glyph) ? productSans : null;
   }

   private List<NameTagsFeature.RankSpan> rankSpans(String text) {
      List<NameTagsFeature.RankSpan> spans = new ArrayList<>();
      int start = 0;
      MsdfFont currentFont = null;
      int offset = 0;

      while (offset < text.length()) {
         int end = offset + Character.charCount(text.codePointAt(offset));
         MsdfFont resolved = this.rankFontFor(text.substring(offset, end));
         if (offset > start && (resolved != currentFont || resolved == null)) {
            spans.add(new NameTagsFeature.RankSpan(text.substring(start, offset), currentFont));
            start = offset;
         }

         currentFont = resolved;
         offset = end;
      }

      if (start < text.length()) {
         spans.add(new NameTagsFeature.RankSpan(text.substring(start), currentFont));
      }

      return spans;
   }

   private float rankSpanWidth(NameTagsFeature.RankSpan span, float size) {
      if (span.font() == null) {
         return VanillaTextMetrics.width(span.text(), size);
      }

      float drawSize = this.msdfSize(size);
      return span.font().measureWidth(span.text(), drawSize, drawSize * TAG_STYLE.letterSpacingEm());
   }

   private float drawRankRun(NameTagsFeature.Run run, float x, float y, float size) {
      float drawSize = this.msdfSize(size);
      Minecraft mc = Minecraft.getInstance();
      float vanillaAscender = 7.0F * VanillaTextMetrics.lineHeight(size) / 9.0F;
      float baseline = y + this.referenceFont().ascender(drawSize);
      if (this.vanillaFont()) {
         float guiScale = mc.getWindow().getGuiScale();
         baseline = Math.round(y * guiScale) / guiScale + vanillaAscender;
      }

      float cursorX = x;

      for (NameTagsFeature.RankSpan span : this.rankSpans(run.text())) {
         if (span.font() == null) {
            Render2DUtil.text(cursorX, baseline - vanillaAscender, size, span.text()).vanilla().color(run.color()).draw();
         } else {
            Render2DUtil.TextBuilder builder = Render2DUtil.text(cursorX, baseline - span.font().ascender(drawSize), drawSize, span.text())
               .font(span.font())
               .style(TAG_STYLE)
               .color(run.color());
            if (this.outline.getValue()) {
               builder.outline(OUTLINE_COLOR, 0.35F);
            }

            builder.draw();
         }

         cursorX += this.rankSpanWidth(span, size);
      }

      return cursorX;
   }

   private float textWidth(String text, float size) {
      if (this.vanillaFont()) {
         return VanillaTextMetrics.width(text, size);
      }

      MsdfFont resolved = this.fontFor(text);
      if (!resolved.canRender(text)) {
         return VanillaTextMetrics.width(text, size);
      }

      float drawSize = this.msdfSize(size);
      return resolved.measureWidth(text, drawSize, drawSize * TAG_STYLE.letterSpacingEm());
   }

   private float lineHeight(float size) {
      return VanillaTextMetrics.lineHeight(size);
   }

   private float centeredTextY(float centerY, float size) {
      return this.vanillaFont() ? VanillaTextMetrics.centeredTextY(centerY, size) : this.referenceFont().centeredTextY(centerY, this.msdfSize(size));
   }

   private void drawText(float x, float y, float size, String text, int color, TextAlign align) {
      if (this.vanillaFont()) {
         Render2DUtil.text(x, y, size, text).vanilla().align(align).color(color).draw();
      } else {
         Render2DUtil.TextBuilder builder = Render2DUtil.text(x, y, this.msdfSize(size), text)
            .font(this.fontFor(text))
            .style(TAG_STYLE)
            .align(align)
            .color(color);
         if (this.outline.getValue()) {
            builder.outline(OUTLINE_COLOR, 0.35F);
         }

         builder.draw();
      }
   }

   private void drawText(float x, float y, float size, String text, int color) {
      this.drawText(x, y, size, text, color, TextAlign.LEFT);
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      Minecraft mc = event.getClient();
      if (mc != null && mc.level != null && mc.player != null) {
         float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         float unit = (float)(0.8F * this.scale.getValue() / mc.getWindow().getGuiScale());
         double maxDistanceSqr = 10000.0;
         this.captureFrameTargets(mc);
         if (this.frameAnyTarget) {
            for (Entity entity : mc.level.entitiesForRendering()) {
               if (!entity.isRemoved() && !(mc.player.distanceToSqr(entity) > maxDistanceSqr)) {
                  if (entity instanceof ItemEntity itemEntity) {
                     if (this.frameItems) {
                        Render3DUtil.ScreenBounds bounds = this.computeBounds(mc, itemEntity, tickDelta);
                        if (bounds != null) {
                           this.drawItemTag(event, itemEntity, bounds, unit);
                        }
                     }
                  } else if (entity instanceof LivingEntity living && living.isAlive() && this.isTarget(mc, living)) {
                     Render3DUtil.ScreenBounds bounds = this.computeBounds(mc, living, tickDelta);
                     if (bounds != null) {
                        this.drawEntityTag(event, living, bounds, unit);
                     }
                  }
               }
            }
         }
      }
   }

   private void captureFrameTargets(Minecraft mc) {
      this.frameSelf = this.targets.isSelected("Self");
      this.frameFriends = this.targets.isSelected("Friends");
      this.frameNaked = this.targets.isSelected("Naked");
      this.framePlayers = this.targets.isSelected("Players");
      this.frameVillagers = this.targets.isSelected("Villagers");
      this.framePassive = this.targets.isSelected("Passive");
      this.frameHostile = this.targets.isSelected("Hostile");
      this.frameItems = this.targets.isSelected("Items");
      this.frameFirstPerson = mc.options.getCameraType().isFirstPerson();
      this.frameAnyTarget = this.frameSelf
         || this.frameFriends
         || this.frameNaked
         || this.framePlayers
         || this.frameVillagers
         || this.framePassive
         || this.frameHostile
         || this.frameItems;
   }

   private boolean isTarget(Minecraft mc, LivingEntity entity) {
      if (entity != mc.player) {
         if (entity instanceof Player player) {
            if (FriendManager.INSTANCE.isFriend(player.getGameProfile().name())) {
               return this.frameFriends;
            } else {
               return player.getArmorValue() <= 0.0F ? this.frameNaked : this.framePlayers;
            }
         } else if (entity instanceof Villager) {
            return this.frameVillagers;
         } else if (entity instanceof Animal) {
            return this.framePassive;
         } else {
            return entity instanceof Mob ? this.frameHostile : false;
         }
      } else {
         return this.frameSelf && !this.frameFirstPerson;
      }
   }

   private Render3DUtil.ScreenBounds computeBounds(Minecraft mc, Entity entity, float tickDelta) {
      double x = Render3DUtil.lerpX(entity, tickDelta);
      double y = Render3DUtil.lerpY(entity, tickDelta);
      double z = Render3DUtil.lerpZ(entity, tickDelta);
      double halfWidth = entity.getBbWidth() / 2.0;
      double height = entity.getBbHeight();
      return Render3DUtil.projectBoxBounds(mc, x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
   }

   private void drawItemTag(Render2DEvent event, ItemEntity itemEntity, Render3DUtil.ScreenBounds bounds, float unit) {
      ItemStack stack = itemEntity.getItem();
      float nameSize = 11.0F * unit;
      Integer rarityColor = rarityColor(stack.getRarity());
      int fallbackColor = rarityColor != null ? rarityColor : Theme.Colors.TEXT_TITLE;
      List<NameTagsFeature.Run> runs = extractRuns(stack.getHoverName(), fallbackColor);
      int count = stack.getCount();
      if (count > 1) {
         runs.add(new NameTagsFeature.Run(" x" + count, fallbackColor));
      }

      float textWidth = this.runsWidth(runs, nameSize);
      float fontHeight = this.lineHeight(nameSize);
      float centerX = (bounds.minX() + bounds.maxX()) / 2.0F;
      float tagY = bounds.minY() - fontHeight - 3.0F * unit;
      float tagRectY = tagY - 2.0F * unit;
      float tagRectHeight = fontHeight + 4.0F * unit;
      float tagWidth = textWidth + 8.0F * unit;
      float tagRectX = centerX - tagWidth / 2.0F;
      Render2DUtil.rect(tagRectX, tagRectY, tagWidth, tagRectHeight).color(TAG_BG).draw();
      this.drawRuns(runs, centerX - textWidth / 2.0F, this.centeredTextY(tagRectY + tagRectHeight / 2.0F, nameSize), nameSize);
   }

   private void drawEntityTag(Render2DEvent event, LivingEntity entity, Render3DUtil.ScreenBounds bounds, float unit) {
      Minecraft mc = event.getClient();
      float nameSize = 11.0F * unit;
      float namePartGap = 6.0F * unit;
      boolean friend = entity instanceof Player player && FriendManager.INSTANCE.isFriend(player.getGameProfile().name());
      List<List<NameTagsFeature.Run>> gappedGroups = new ArrayList<>(3);
      if (this.privilege.getValue()) {
         PlayerTeam team = entity.getTeam();
         addNamePart(gappedGroups, team == null ? null : team.getPlayerPrefix());
      }

      String rawName = entity instanceof Player player ? player.getGameProfile().name() : entity.getName().getString();
      gappedGroups.add(List.of(new NameTagsFeature.Run(rawName, Theme.Colors.TEXT_TITLE)));
      if (this.privilege.getValue()) {
         PlayerTeam team = entity.getTeam();
         addNamePart(gappedGroups, team == null ? null : team.getPlayerSuffix());
      }

      List<NameTagsFeature.Run> tightRuns = new ArrayList<>(3);
      if (this.health.getValue()) {
         int hp = Math.max(0, Math.round(HealthUtil.getHealth(entity)));
         tightRuns.add(new NameTagsFeature.Run(hp + "HP", HEALTH_COLOR));
      }

      if (friend) {
         tightRuns.add(new NameTagsFeature.Run(" [", FRIEND_BRACKET_COLOR));
         tightRuns.add(new NameTagsFeature.Run("F", FRIEND_LETTER_COLOR));
         tightRuns.add(new NameTagsFeature.Run("]", FRIEND_BRACKET_COLOR));
      }

      float nameWidth = 0.0F;

      for (int i = 0; i < gappedGroups.size(); i++) {
         if (i > 0) {
            nameWidth += namePartGap;
         }

         for (NameTagsFeature.Run run : gappedGroups.get(i)) {
            nameWidth += this.textWidth(run.text(), nameSize);
         }
      }

      if (!tightRuns.isEmpty()) {
         nameWidth += 4.0F * unit;
      }

      for (NameTagsFeature.Run run : tightRuns) {
         nameWidth += this.textWidth(run.text(), nameSize);
      }

      float fontHeight = this.lineHeight(nameSize);
      float centerX = (bounds.minX() + bounds.maxX()) / 2.0F;
      float tagY = bounds.minY() - fontHeight - 3.0F * unit;
      float tagRectY = tagY - 2.0F * unit;
      float tagRectHeight = fontHeight + 4.0F * unit;
      float tagWidth = nameWidth + 8.0F * unit;
      float tagRectX = centerX - tagWidth / 2.0F;
      float nameY = this.centeredTextY(tagRectY + tagRectHeight / 2.0F, nameSize);
      Render2DUtil.rect(tagRectX, tagRectY, tagWidth, tagRectHeight).color(friend ? FRIEND_TAG_BG : TAG_BG).draw();
      float cursorX = centerX - nameWidth / 2.0F;

      for (int i = 0; i < gappedGroups.size(); i++) {
         if (i > 0) {
            cursorX += namePartGap;
         }

         for (NameTagsFeature.Run run : gappedGroups.get(i)) {
            this.drawText(cursorX, nameY, nameSize, run.text(), run.color());
            cursorX += this.textWidth(run.text(), nameSize);
         }
      }

      if (!tightRuns.isEmpty()) {
         cursorX += 4.0F * unit;
      }

      for (NameTagsFeature.Run run : tightRuns) {
         this.drawText(cursorX, nameY, nameSize, run.text(), run.color());
         cursorX += this.textWidth(run.text(), nameSize);
      }

      if (this.viewArmor.getValue()) {
         this.drawArmorRow(event, entity, centerX, tagRectY, unit);
      }

      if (this.viewEffects.getValue()) {
         this.drawEffects(entity, bounds, unit);
      }
   }

   private void drawArmorRow(Render2DEvent event, LivingEntity entity, float centerX, float tagRectY, float unit) {
      List<NameTagsFeature.EquipmentEntry> equipment = new ArrayList<>(EQUIPMENT_ORDER.length);

      for (EquipmentSlot slot : EQUIPMENT_ORDER) {
         ItemStack stack = entity.getItemBySlot(slot);
         if (!stack.isEmpty()) {
            equipment.add(new NameTagsFeature.EquipmentEntry(stack, this.viewEnchants.getValue() ? enchantLabels(stack) : List.of()));
         }
      }

      if (!equipment.isEmpty()) {
         float itemSize = 14.0F * unit;
         float itemGap = 2.0F * unit;
         float enchantTextSize = 8.0F * unit;
         float enchantLineHeight = this.lineHeight(enchantTextSize);
         float iconTop = tagRectY - 5.0F * unit - itemSize;
         float enchantBlockBottom = iconTop - 2.0F * unit;
         float[] columnWidths = new float[equipment.size()];
         float rowWidth = 0.0F;

         for (int i = 0; i < equipment.size(); i++) {
            float labelWidth = 0.0F;

            for (String label : equipment.get(i).labels()) {
               labelWidth = Math.max(labelWidth, this.textWidth(label, enchantTextSize));
            }

            columnWidths[i] = Math.max(itemSize, labelWidth);
            rowWidth += columnWidths[i];
         }

         rowWidth += (equipment.size() - 1) * itemGap;
         float columnX = centerX - rowWidth / 2.0F;
         Minecraft mc = event.getClient();
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().pose();
         float guiScale = mc.getWindow().getGuiScale();
         float itemScale = itemSize / 16.0F;

         for (int i = 0; i < equipment.size(); i++) {
            NameTagsFeature.EquipmentEntry entry = equipment.get(i);
            float columnWidth = columnWidths[i];
            float columnCenterX = columnX + columnWidth / 2.0F;
            List<String> labels = entry.labels();
            float labelY = enchantBlockBottom - labels.size() * enchantLineHeight;

            for (String label : labels) {
               this.drawText(columnCenterX, labelY, enchantTextSize, label, ENCHANT_COLOR, TextAlign.CENTER);
               labelY += enchantLineHeight;
            }

            Render2DUtil.flush();
            float itemX = Math.round((columnCenterX - itemSize / 2.0F) * guiScale) / guiScale;
            float itemY = Math.round(iconTop * guiScale) / guiScale;
            pose.pushMatrix();
            pose.translate(itemX, itemY);
            pose.scale(itemScale);
            event.getGuiGraphicsExtractor().item(entry.stack(), 0, 0);
            pose.popMatrix();
            columnX += columnWidth + itemGap;
         }
      }
   }

   private void drawEffects(LivingEntity entity, Render3DUtil.ScreenBounds bounds, float unit) {
      float textSize = 9.0F * unit;
      List<NameTagsFeature.EffectLine> lines = new ArrayList<>();

      for (MobEffectInstance effectInstance : entity.getActiveEffects()) {
         int durationTicks = effectInstance.getDuration();
         if (effectInstance.isInfiniteDuration() || durationTicks > 20) {
            int packed = ((MobEffect)effectInstance.getEffect().value()).getColor();
            int color = 0xFF000000 | packed & 16777215;
            String name = ((MobEffect)effectInstance.getEffect().value()).getDisplayName().getString();
            int amplifier = effectInstance.getAmplifier() + 1;
            StringBuilder text = new StringBuilder(name);
            if (amplifier > 1) {
               text.append(' ').append(roman(amplifier));
            }

            text.append(' ').append(effectInstance.isInfiniteDuration() ? "∞" : formatDuration(durationTicks));
            float width = this.textWidth(text.toString(), textSize);
            lines.add(new NameTagsFeature.EffectLine(text.toString(), width, color));
         }
      }

      if (!lines.isEmpty()) {
         lines.sort(Comparator.comparingDouble(NameTagsFeature.EffectLine::width));
         float lineStep = this.lineHeight(textSize) + 2.0F * unit;
         float x = bounds.maxX() + 6.0F * unit;
         float totalHeight = lineStep * lines.size() - 2.0F * unit;
         float y = (bounds.minY() + bounds.maxY()) / 2.0F - totalHeight / 2.0F;

         for (NameTagsFeature.EffectLine line : lines) {
            this.drawText(x, y, textSize, line.text(), line.color());
            y += lineStep;
         }
      }
   }

   private static void addNamePart(List<List<NameTagsFeature.Run>> groups, Component prefixOrSuffix) {
      if (prefixOrSuffix != null) {
         List<NameTagsFeature.Run> runs = extractRuns(prefixOrSuffix, Theme.getAccent());
         List<NameTagsFeature.Run> cleaned = new ArrayList<>(runs.size());

         for (int i = 0; i < runs.size(); i++) {
            NameTagsFeature.Run run = runs.get(i);
            String trimmed = run.text();
            if (i == 0) {
               trimmed = trimmed.stripLeading();
            }

            if (i == runs.size() - 1) {
               trimmed = trimmed.stripTrailing();
            }

            if (!trimmed.isEmpty()) {
               cleaned.add(new NameTagsFeature.Run(trimmed, run.color()));
            }
         }

         if (!cleaned.isEmpty()) {
            groups.add(cleaned);
         }
      }
   }

   private static List<NameTagsFeature.Run> extractRuns(Component component, int fallbackColor) {
      List<NameTagsFeature.Run> runs = new ArrayList<>();
      component.visit((style, text) -> {
         TextColor styleColor = style.getColor();
         int baseColor = styleColor != null ? 0xFF000000 | styleColor.getValue() : fallbackColor;
         splitLegacyRuns(text, baseColor, runs);
         return Optional.empty();
      }, Style.EMPTY);
      return runs;
   }

   private float runsWidth(List<NameTagsFeature.Run> runs, float size) {
      float width = 0.0F;

      for (NameTagsFeature.Run run : runs) {
         width += this.textWidth(run.text(), size);
      }

      return width;
   }

   private float drawRuns(List<NameTagsFeature.Run> runs, float x, float y, float size) {
      float cursorX = x;

      for (NameTagsFeature.Run run : runs) {
         this.drawText(cursorX, y, size, run.text(), run.color());
         cursorX += this.textWidth(run.text(), size);
      }

      return cursorX;
   }

   private static void splitLegacyRuns(String text, int initialColor, List<NameTagsFeature.Run> out) {
      if (!text.isEmpty()) {
         if (text.indexOf(167) < 0) {
            out.add(new NameTagsFeature.Run(text, initialColor));
         } else {
            Matcher matcher = LEGACY_COLOR_CODE.matcher(text);
            int last = 0;
            int currentColor = initialColor;

            while (matcher.find()) {
               if (matcher.start() > last) {
                  out.add(new NameTagsFeature.Run(text.substring(last, matcher.start()), currentColor));
               }

               Integer parsed = parseColorCode(matcher.group());
               if (parsed != null) {
                  currentColor = parsed;
               }

               last = matcher.end();
            }

            if (last < text.length()) {
               out.add(new NameTagsFeature.Run(text.substring(last), currentColor));
            }
         }
      }
   }

   private static Integer parseColorCode(String code) {
      if (!code.startsWith("§x") && !code.startsWith("§X")) {
         ChatFormatting formatting = ChatFormatting.getByCode(code.charAt(1));
         if (formatting == null) {
            return null;
         }

         TextColor color = TextColor.fromLegacyFormat(formatting);
         return color != null ? 0xFF000000 | color.getValue() : null;
      } else {
         StringBuilder hex = new StringBuilder(6);

         for (int i = 2; i < code.length(); i += 2) {
            hex.append(code.charAt(i + 1));
         }

         try {
            return 0xFF000000 | Integer.parseInt(hex.toString(), 16);
         } catch (NumberFormatException exception) {
            return null;
         }
      }
   }

   private static List<String> enchantLabels(ItemStack stack) {
      List<String> labels = new ArrayList<>();

      for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
         String description = ((Enchantment)((Holder)entry.getKey()).value()).description().getString();
         if (!description.isBlank()) {
            String firstWord = description.trim().split("\\s+")[0];
            String abbreviation = abbreviate(firstWord);
            if (!abbreviation.isEmpty()) {
               int level = entry.getIntValue();
               labels.add(level > 1 ? abbreviation + level : abbreviation);
            }
         }
      }

      return labels;
   }

   private static String abbreviate(String word) {
      StringBuilder letters = new StringBuilder(2);

      for (int i = 0; i < word.length() && letters.length() < 2; i++) {
         char character = word.charAt(i);
         if (Character.isLetter(character)) {
            letters.append(character);
         }
      }

      if (letters.isEmpty()) {
         return "";
      }

      String result = letters.toString();
      return result.length() > 1
         ? "" + Character.toUpperCase(result.charAt(0)) + Character.toLowerCase(result.charAt(1))
         : String.valueOf(Character.toUpperCase(result.charAt(0)));
   }

   private static String roman(int amplifier) {
      return amplifier > 0 && amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier);
   }

   private static String formatDuration(int ticks) {
      int seconds = Math.max(0, ticks) / 20;
      return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
   }

   private static Integer rarityColor(Rarity rarity) {
      TextColor color = TextColor.fromLegacyFormat(rarity.color());
      return color != null ? 0xFF000000 | color.getValue() : null;
   }

   private record EffectLine(String text, float width, int color) {
   }

   private record EquipmentEntry(ItemStack stack, List<String> labels) {
   }

   private record RankSpan(String text, MsdfFont font) {
   }

   private record Run(String text, int color, boolean rank) {
      private Run(String text, int color) {
         this(text, color, false);
      }
   }
}
