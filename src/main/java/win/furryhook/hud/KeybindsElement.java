package win.furryhook.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import win.furryhook.event.EventManager;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.lifecycle.FeatureToggleEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.feature.setting.BindSetting;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class KeybindsElement extends HudElement {
   private static final float MIN_WIDTH = 96.0F;
   private static final float HEADER_HEIGHT = 16.0F;
   private static final float HEADER_RADIUS = 5.0F;
   private static final float BODY_RADIUS = 5.0F;
   private static final float ICON_SIZE = 8.0F;
   private static final float BODY_PAD_TOP = 3.0F;
   private static final float BODY_PAD_BOTTOM = 5.0F;
   private static final float ROW_HEIGHT = 11.0F;
   private static final float ROW_GAP = 2.0F;
   private static final float TEXT_SIZE = 7.0F;
   private static final float TITLE_SIZE = 7.5F;
   private static final float PAD_X = 6.0F;
   private static final float BADGE_SIZE = 6.5F;
   private static final float VALUE_GAP = 8.0F;
   private final Map<String, KeybindsElement.RowState> rowStates = new HashMap<>();
   private final AnimatedValue visibilityAnim = new AnimatedValue();
   private final AnimatedValue widthAnim = new AnimatedValue();
   private final AnimatedValue heightAnim = new AnimatedValue();
   private List<KeybindsElement.RowVisual> visuals = List.of();
   private float visible;
   private int activeCount;
   private boolean rowsDirty = true;
   private int lastFingerprint;

   public KeybindsElement() {
      super("keybinds", "Keybinds");
      EventManager.subscribe(this);
   }

   @EventTarget
   public void onFeatureToggle(FeatureToggleEvent event) {
      this.rowsDirty = true;
   }

   @Override
   protected float defaultY(float unit) {
      return 42.0F * unit;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      int fingerprint = this.fingerprint(mc);
      if (this.rowsDirty || fingerprint != this.lastFingerprint) {
         this.rowsDirty = false;
         this.lastFingerprint = fingerprint;
      }

      List<KeybindsElement.BoundEntry> entries = this.collectEntries(mc);

      for (KeybindsElement.RowState state : this.rowStates.values()) {
         state.seen = false;
      }

      int i = 0;

      while (i < entries.size()) {
         KeybindsElement.BoundEntry entry = entries.get(i);
         KeybindsElement.RowState state = this.rowStates.get(entry.name());
         if (state == null) {
            state = new KeybindsElement.RowState(entry);
            this.rowStates.put(entry.name(), state);
         } else {
            state.entry = entry;
         }

         state.seen = true;
         state.targetIndex = i++;
      }

      this.activeCount = (int)this.rowStates.values().stream().filter(state -> state.seen).count();
      float headerWidth = 20.0F
         + glassChevronWidth(1.0F)
         + glassTextWidth("Keybinds", 7.5F, UiFontStyle.SEMIBOLD)
         + 8.0F
         + glassTextWidth(Integer.toString(this.activeCount), 6.0F, UiFontStyle.MEDIUM);
      float targetWidth = Math.max(96.0F, headerWidth);

      for (KeybindsElement.RowState state : this.rowStates.values()) {
         float rowWidth = 12.0F + glassTextWidth(state.entry.name(), 7.0F, UiFontStyle.MEDIUM) + 8.0F + glassBadgeWidth(state.entry.bind(), 6.5F, 1.0F);
         targetWidth = Math.max(targetWidth, rowWidth);
      }

      List<KeybindsElement.RowVisual> visuals = new ArrayList<>();
      List<String> toRemove = new ArrayList<>();

      for (Entry<String, KeybindsElement.RowState> stateEntry : this.rowStates.entrySet()) {
         KeybindsElement.RowState state = stateEntry.getValue();
         float progress = MathUtil.clamp01(state.animation.update(state.seen, now));
         if (!state.seen && state.animation.isHidden()) {
            toRemove.add(stateEntry.getKey());
         } else if (progress > 0.001F) {
            visuals.add(new KeybindsElement.RowVisual(state.entry, state.targetIndex, progress));
         }
      }

      toRemove.forEach(this.rowStates::remove);
      visuals.sort(
         Comparator.comparingInt(KeybindsElement.RowVisual::targetIndex).thenComparing(visual -> visual.entry().name(), String.CASE_INSENSITIVE_ORDER)
      );
      this.visuals = visuals;
      float rowStride = 13.0F;
      float animatedRowsHeight = 0.0F;

      for (KeybindsElement.RowVisual visual : visuals) {
         animatedRowsHeight += rowStride * MathUtil.clamp01(visual.progress());
      }

      float listPresence = MathUtil.clamp01(animatedRowsHeight / Math.max(0.001F, rowStride));
      float bodyRowsHeight = Math.max(0.0F, animatedRowsHeight - 2.0F * listPresence);
      float bodyHeight = 8.0F * listPresence + bodyRowsHeight;
      float targetHeight = 16.0F + (bodyHeight > 0.5F ? bodyHeight : 0.0F);
      boolean shouldShow = this.alwaysShow() || !this.rowStates.isEmpty() || showcase(mc);
      this.visible = this.visibilityAnim.update(shouldShow ? 1.0F : 0.0F, 0.08F, now);
      float width = this.widthAnim.update(targetWidth, 0.09F, now);
      float height = this.heightAnim.update(targetHeight, 0.09F, now);
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
         float headerHeight = 16.0F * unit;
         boolean hasBody = this.height - headerHeight > 0.5F;
         if (hasBody) {
            this.drawGlassPanel(x, y, width, this.height, 5.0F * unit, unit, panelAlpha);
         }

         Render2DUtil.rect(x, y, width, headerHeight).color(ColorUtil.multiplyAlpha(HudFeature.headerColor(), panelAlpha)).radius(5.0F * unit).draw();
         float barWidth = 1.3F * unit;
         float barHeight = headerHeight * 0.5F;
         Render2DUtil.rect(x, y + (headerHeight - barHeight) * 0.5F, barWidth, barHeight)
            .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), this.visible))
            .radius(0.0F, barWidth * 0.5F, barWidth * 0.5F, 0.0F)
            .draw();
         MsdfFont boldFont = UiFonts.sfPro(600);
         MsdfFont font = UiFonts.sfPro(400);
         float titleSize = 7.5F * unit;
         float titleCenterY = y + headerHeight * 0.5F;
         Render2DUtil.text(x + 6.0F * unit, boldFont.centeredTextY(titleCenterY, titleSize), titleSize, "Keybinds")
            .font(boldFont)
            .style(UiFontStyle.SEMIBOLD)
            .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, this.visible))
            .draw();
         float headerIconSize = 8.0F * unit;
         MsdfFont iconFont = UiFonts.divineNotificationIcons();
         Render2DUtil.text(x + width - 6.0F * unit - headerIconSize, iconFont.centeredTextY(titleCenterY, headerIconSize), headerIconSize, "J")
            .font(iconFont)
            .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), this.visible))
            .draw();
         if (hasBody) {
            float textSize = 7.0F * unit;
            float cursorY = y + headerHeight + 3.0F * unit;

            for (KeybindsElement.RowVisual visual : this.visuals) {
               KeybindsElement.BoundEntry entry = visual.entry();
               float progress = MathUtil.clamp01(visual.progress());
               float rowAlpha = this.visible * progress;
               float rowCenterY = cursorY + 5.5F * unit;
               Render2DUtil.text(x + 6.0F * unit, font.centeredTextY(rowCenterY, textSize), textSize, entry.name())
                  .font(font)
                  .style(UiFontStyle.REGULAR)
                  .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, rowAlpha))
                  .draw();
               String bindText = entry.bind();
               float bindWidth = font.measureWidth(bindText, textSize, 0.0F);
               Render2DUtil.text(x + width - 6.0F * unit - bindWidth, font.centeredTextY(rowCenterY, textSize), textSize, bindText)
                  .font(font)
                  .style(UiFontStyle.REGULAR)
                  .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), rowAlpha))
                  .draw();
               cursorY += 13.0F * unit * progress;
            }
         }
      }
   }

   private int fingerprint(Minecraft mc) {
      int fp = showcase(mc) ? 1 : 0;

      for (Feature feature : FeatureManager.INSTANCE.getFeatures()) {
         fp = fp * 31 + feature.getName().hashCode();
         List<Integer> bindCodes = feature.getBinds();
         fp = fp * 31 + bindCodes.hashCode();

         for (int i = 0; i < bindCodes.size(); i++) {
            fp = fp * 31 + (feature.isBindVisibleAt(i) ? 1 : 0);
         }

         fp = fp * 31 + (feature.isEnabled() ? 1 : 0);
      }

      return fp;
   }

   private List<KeybindsElement.BoundEntry> collectEntries(Minecraft mc) {
      List<KeybindsElement.BoundEntry> entries = new ArrayList<>();

      for (Feature feature : FeatureManager.INSTANCE.getFeatures()) {
         StringJoiner binds = new StringJoiner(" + ");
         List<Integer> bindCodes = feature.getBinds();

         for (int i = 0; i < bindCodes.size(); i++) {
            int bindCode = bindCodes.get(i);
            if (bindCode != -1 && feature.isBindVisibleAt(i)) {
               binds.add(BindSetting.describe(bindCode));
            }
         }

         if (binds.length() > 0 && feature.isEnabled()) {
            entries.add(new KeybindsElement.BoundEntry(displayName(feature.getName()), binds.toString(), feature.getCategory()));
         }
      }

      return entries;
   }

   private static String displayName(String name) {
      return name.length() <= 14 ? name : name.substring(0, 13) + "…";
   }

   private record BoundEntry(String name, String bind, FeatureCategory category) {
   }

   private static final class RowState {
      private KeybindsElement.BoundEntry entry;
      private final HudRowAnimation animation = new HudRowAnimation();
      private boolean seen;
      private int targetIndex;

      private RowState(KeybindsElement.BoundEntry entry) {
         this.entry = entry;
      }
   }

   private record RowVisual(KeybindsElement.BoundEntry entry, int targetIndex, float progress) {
   }
}
