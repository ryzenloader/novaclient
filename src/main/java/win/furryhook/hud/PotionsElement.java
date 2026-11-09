package win.furryhook.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class PotionsElement extends HudElement {
   private static final float MIN_WIDTH = 96.0F;
   private static final float HEADER_HEIGHT = 16.0F;
   private static final float HEADER_RADIUS = 5.0F;
   private static final float BODY_RADIUS = 5.0F;
   private static final float HEADER_ICON_SIZE = 8.0F;
   private static final float BODY_PAD_TOP = 3.0F;
   private static final float BODY_PAD_BOTTOM = 5.0F;
   private static final float ROW_HEIGHT = 11.0F;
   private static final float ROW_GAP = 2.0F;
   private static final float TEXT_SIZE = 7.0F;
   private static final float TITLE_SIZE = 7.5F;
   private static final float ICON_SIZE = 9.0F;
   private static final float ICON_GAP = 4.0F;
   private static final float PAD_X = 6.0F;
   private static final float BADGE_SIZE = 6.5F;
   private static final float VALUE_GAP = 8.0F;
   private static final Identifier CHAT_PREVIEW_ID = Identifier.parse("furryhook:potions_preview");
   private static final String[] ROMAN = new String[]{"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
   private final AnimatedValue visibilityAnim = new AnimatedValue();
   private final AnimatedValue widthAnim = new AnimatedValue();
   private final AnimatedValue heightAnim = new AnimatedValue();
   private final Map<Identifier, PotionsElement.RowState> rowStates = new LinkedHashMap<>();
   private List<PotionsElement.RowVisual> entries = List.of();
   private float visible;
   private int activeCount;
   private static final int INFINITE_THRESHOLD_TICKS = 1728000;

   public PotionsElement() {
      super("potions", "Potions");
   }

   @Override
   protected float defaultY(float unit) {
      return 260.0F * unit;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
      List<PotionsElement.RowVisual> entries = this.collectEntries(mc, now, chatOpen);
      this.activeCount = (int)this.rowStates.values().stream().filter(state -> state.seen).count();
      float headerWidth = 20.0F
         + glassChevronWidth(1.0F)
         + glassTextWidth("Potions", 7.5F, UiFontStyle.SEMIBOLD)
         + 8.0F
         + glassTextWidth(Integer.toString(this.activeCount), 6.0F, UiFontStyle.MEDIUM);
      float targetWidth = Math.max(96.0F, headerWidth);

      for (PotionsElement.RowState state : this.rowStates.values()) {
         PotionsElement.PotionEntry entry = state.entry;
         targetWidth = Math.max(targetWidth, 25.0F + glassTextWidth(entry.name(), 7.0F, UiFontStyle.MEDIUM) + 8.0F + glassBadgeWidth(entry.time(), 6.5F, 1.0F));
      }

      float rowStride = 13.0F;
      float animatedRowsHeight = 0.0F;

      for (PotionsElement.RowVisual visual : entries) {
         animatedRowsHeight += rowStride * MathUtil.clamp01(visual.progress());
      }

      float listPresence = MathUtil.clamp01(animatedRowsHeight / Math.max(0.001F, rowStride));
      float bodyRowsHeight = Math.max(0.0F, animatedRowsHeight - 2.0F * listPresence);
      float bodyHeight = 8.0F * listPresence + bodyRowsHeight;
      float targetHeight = 16.0F + (bodyHeight > 0.5F ? bodyHeight : 0.0F);
      boolean shouldShow = this.alwaysShow() || !this.rowStates.isEmpty() || chatOpen && showcase(mc);
      this.visible = this.visibilityAnim.update(shouldShow ? 1.0F : 0.0F, 0.08F, now);
      float width = this.widthAnim.update(targetWidth, 0.09F, now);
      float height = this.heightAnim.update(targetHeight, 0.09F, now);
      this.entries = entries;
      if (this.visible <= 0.01F) {
         this.width = 0.0F;
         this.height = 0.0F;
      } else {
         this.width = Math.max(targetWidth, width) * unit;
         this.height = Math.max(targetHeight, height) * unit;
      }
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      if (!(this.visible <= 0.01F)) {
         float x = this.x;
         float y = this.y;
         float width = this.width;
         float panelAlpha = Math.min(1.0F, this.visible / 0.34F);
         this.drawPlates(x, y, width, unit, panelAlpha);
         MsdfFont font = UiFonts.sfPro(400);
         MsdfFont titleFont = UiFonts.sfPro(600);
         float headerHeight = 16.0F * unit;
         float titleSize = 7.5F * unit;
         float titleCenterY = y + headerHeight * 0.5F;
         Render2DUtil.text(x + 6.0F * unit, titleFont.centeredTextY(titleCenterY, titleSize), titleSize, "Potions")
            .font(titleFont)
            .style(UiFontStyle.SEMIBOLD)
            .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, this.visible))
            .draw();
         float headerIconSize = 8.0F * unit;
         MsdfFont iconFont = UiFonts.divineNotificationIcons();
         Render2DUtil.text(x + width - 6.0F * unit - headerIconSize, iconFont.centeredTextY(titleCenterY, headerIconSize), headerIconSize, "I")
            .font(iconFont)
            .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), this.visible))
            .draw();
         if (!(this.height - headerHeight <= 0.5F)) {
            float textSize = 7.0F * unit;
            float iconSize = 9.0F * unit;
            float cursorY = y + headerHeight + 3.0F * unit;

            for (PotionsElement.RowVisual rowVisual : this.entries) {
               PotionsElement.PotionEntry entry = rowVisual.entry();
               float progress = MathUtil.clamp01(rowVisual.progress());
               float rowAlpha = this.visible * progress;
               float rowCenterY = cursorY + 5.5F * unit;
               Render2DUtil.texture(x + 6.0F * unit, rowCenterY - iconSize / 2.0F, iconSize, iconSize, entry.icon())
                  .managed()
                  .color(ColorUtil.multiplyAlpha(-1, rowAlpha))
                  .draw();
               Render2DUtil.text(x + 6.0F * unit + iconSize + 4.0F * unit, font.centeredTextY(rowCenterY, textSize), textSize, entry.name())
                  .font(font)
                  .style(UiFontStyle.REGULAR)
                  .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, rowAlpha))
                  .draw();
               float timeWidth = font.measureWidth(entry.time(), textSize, 0.0F);
               Render2DUtil.text(x + width - 6.0F * unit - timeWidth, font.centeredTextY(rowCenterY, textSize), textSize, entry.time())
                  .font(font)
                  .style(UiFontStyle.REGULAR)
                  .color(ColorUtil.multiplyAlpha(entry.harmful() ? Theme.Colors.SYSTEM_RED : Theme.getAccent(), rowAlpha))
                  .draw();
               cursorY += 13.0F * unit * progress;
            }
         }
      }
   }

   private void drawPlates(float x, float y, float width, float unit, float panelAlpha) {
      float headerHeight = 16.0F * unit;
      if (this.height - headerHeight > 0.5F) {
         this.drawGlassPanel(x, y, width, this.height, 5.0F * unit, unit, panelAlpha);
      }

      Render2DUtil.rect(x, y, width, headerHeight).color(ColorUtil.multiplyAlpha(HudFeature.headerColor(), panelAlpha)).radius(5.0F * unit).draw();
      float barWidth = 1.3F * unit;
      float barHeight = headerHeight * 0.5F;
      Render2DUtil.rect(x, y + (headerHeight - barHeight) * 0.5F, barWidth, barHeight)
         .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), this.visible))
         .radius(0.0F, barWidth * 0.5F, barWidth * 0.5F, 0.0F)
         .draw();
   }

   private List<PotionsElement.RowVisual> collectEntries(Minecraft mc, long now, boolean chatOpen) {
      Set<Identifier> liveIds = new HashSet<>();
      List<PotionsElement.RowState> liveStates = new ArrayList<>();
      if (mc.player != null) {
         for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            Identifier effectId = ((ResourceKey)effect.getEffect().unwrapKey().orElseThrow()).identifier();
            Identifier icon = effectId.withPath(path -> "textures/mob_effect/" + path + ".png");
            boolean harmful = ((MobEffect)effect.getEffect().value()).getCategory() == MobEffectCategory.HARMFUL;
            String name = ((MobEffect)effect.getEffect().value()).getDisplayName().getString().toLowerCase(Locale.ROOT) + roman(effect.getAmplifier());
            String time = isEffectivelyInfinite(effect) ? "MAX" : formatTicks(effect.getDuration());
            PotionsElement.RowState state = this.rowStates.computeIfAbsent(effectId, PotionsElement.RowState::new);
            state.entry = new PotionsElement.PotionEntry(icon, name, time, harmful);
            state.seen = true;
            liveIds.add(effectId);
            liveStates.add(state);
         }
      }

      liveStates.sort(Comparator.comparing(statex -> statex.entry.name(), String.CASE_INSENSITIVE_ORDER));
      int i = 0;

      while (i < liveStates.size()) {
         liveStates.get(i).targetIndex = i++;
      }

      if (liveStates.isEmpty() && chatOpen && showcase(mc)) {
         PotionsElement.RowState preview = this.rowStates.computeIfAbsent(CHAT_PREVIEW_ID, PotionsElement.RowState::new);
         preview.entry = new PotionsElement.PotionEntry(effectIcon("fire_resistance"), "fire resistance", "1:52", false);
         preview.seen = true;
         preview.targetIndex = 0;
         liveIds.add(CHAT_PREVIEW_ID);
      }

      List<PotionsElement.RowVisual> visuals = new ArrayList<>();
      List<Identifier> remove = new ArrayList<>();

      for (Entry<Identifier, PotionsElement.RowState> mapEntry : this.rowStates.entrySet()) {
         PotionsElement.RowState state = mapEntry.getValue();
         boolean seen = liveIds.contains(mapEntry.getKey());
         state.seen = seen;
         float animation = MathUtil.clamp01(state.animation.update(seen, now));
         if (!seen && state.animation.isHidden()) {
            remove.add(mapEntry.getKey());
         } else if (state.entry != null && animation > 0.001F) {
            visuals.add(new PotionsElement.RowVisual(state.entry, state.targetIndex, animation, state.id));
         }
      }

      remove.forEach(this.rowStates::remove);
      visuals.sort(Comparator.comparingInt(PotionsElement.RowVisual::targetIndex).thenComparing(visual -> visual.id().toString()));
      return visuals;
   }

   private static String roman(int amplifier) {
      return amplifier > 0 && amplifier < ROMAN.length ? ROMAN[amplifier] : (amplifier >= ROMAN.length ? " " + (amplifier + 1) : "");
   }

   private static boolean isEffectivelyInfinite(MobEffectInstance effect) {
      return effect.isInfiniteDuration() || effect.getDuration() > 1728000;
   }

   private static String formatTicks(int ticks) {
      int seconds = Math.max(0, ticks) / 20;
      return seconds / 60 + ":" + String.format("%02d", seconds % 60);
   }

   private static Identifier effectIcon(String name) {
      return Identifier.withDefaultNamespace("textures/mob_effect/" + name + ".png");
   }

   private record PotionEntry(Identifier icon, String name, String time, boolean harmful) {
   }

   private static final class RowState {
      private final Identifier id;
      private final HudRowAnimation animation = new HudRowAnimation();
      private PotionsElement.PotionEntry entry;
      private int targetIndex;
      private boolean seen;

      private RowState(Identifier id) {
         this.id = id;
      }
   }

   private record RowVisual(PotionsElement.PotionEntry entry, int targetIndex, float progress, Identifier id) {
   }
}
