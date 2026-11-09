package win.furryhook.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.lifecycle.FeatureToggleEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.CategoryIconGlyphs;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class NotificationsElement extends HudElement {
   private static final long LIFETIME_MS = 4000L;
   private static final long APPEAR_MS = 200L;
   private static final long FADE_MS = 300L;
   private static final int MAX_NOTES = 5;
   private static final float STACK_GAP = 2.5F;
   private static final float TEXT_SIZE = 7.5F;
   private static final float TEXT_SEGMENT_GAP = 2.0F;
   private static final float SLIDE_PX = 6.0F;
   private static final Identifier[] CATEGORY_ICONS = new Identifier[]{
      Textures.Icons.SWORDS, Textures.Icons.PERSON_STANDING, Textures.Icons.EYE, Textures.Icons.USER_ROUND, Textures.Icons.BOXES, Textures.Icons.BRAIN
   };
   private static final NotificationsElement.Style MINI = new NotificationsElement.Style(16.0F, 5.0F, 6.0F, 7.0F);
   private static final float DEACTIVATION_OPACITY_DELTA = 0.15F;
   private static final Deque<NotificationsElement.Note> NOTES = new ArrayDeque<>();
   private final Map<Identifier, NotificationsElement.EffectState> trackedEffects = new HashMap<>();
   private final List<NotificationsElement.Note> visible = new ArrayList<>();
   private final NotificationsElement.Style style = MINI;

   public NotificationsElement() {
      super("notifications", "Notifications");
   }

   public static void notify(NotificationsElement.NotificationType type, String message) {
      push(
         new NotificationsElement.Note(
            typeIcon(type), typeColor(type), false, List.of(new NotificationsElement.Segment(message, Theme.Colors.TEXT_TEXT)), System.currentTimeMillis()
         )
      );
   }

   public static void notify(NotificationsElement.NotificationType type, String prefix, Component itemName) {
      List<NotificationsElement.Segment> segments = new ArrayList<>();
      segments.add(new NotificationsElement.Segment(prefix, Theme.Colors.TEXT_TEXT));
      boolean[] first = new boolean[]{true};
      itemName.visit((style, text) -> {
         if (!text.isEmpty()) {
            TextColor textColor = style.getColor();
            int color = textColor != null ? 0xFF000000 | textColor.getValue() : Theme.Colors.TEXT_TITLE;
            segments.add(new NotificationsElement.Segment(text, color, false, !first[0]));
            first[0] = false;
         }

         return Optional.empty();
      }, net.minecraft.network.chat.Style.EMPTY);
      push(new NotificationsElement.Note(typeIcon(type), typeColor(type), false, segments, System.currentTimeMillis()));
   }

   private static Identifier typeIcon(NotificationsElement.NotificationType type) {
      return switch (type) {
         case SUCCESS -> Textures.Hud.ICON_SUCCESS;
         case WARNING -> Textures.Hud.ICON_WARNING;
         case INFO -> Textures.Hud.ICON_INFO;
         case MODULE -> Textures.Hud.ICON_FRAME;
      };
   }

   private static int typeColor(NotificationsElement.NotificationType type) {
      return switch (type) {
         case SUCCESS -> Theme.Colors.TRAFFIC_MAXIMIZE;
         case WARNING -> Theme.Colors.SYSTEM_RED;
         case INFO -> Theme.Colors.TEXT_TEXT;
         case MODULE -> Theme.getAccent();
      };
   }

   @Override
   protected boolean centerHorizontally() {
      return true;
   }

   @Override
   protected float defaultY(float unit) {
      return 420.0F * unit;
   }

   private static void featureToggled(Feature feature, boolean enabled) {
      Minecraft mc = MinecraftContext.mc;
      if (mc != null && mc.level != null) {
         FeatureCategory category = feature.getCategory();
         push(
            new NotificationsElement.Note(
               categoryIcon(category),
               enabled ? Theme.getAccent() : Theme.Colors.TEXT_GHOST,
               false,
               List.of(
                  new NotificationsElement.Segment("The", Theme.Colors.TEXT_TEXT),
                  new NotificationsElement.Segment(feature.getName(), Theme.Colors.TEXT_TITLE, enabled),
                  new NotificationsElement.Segment(enabled ? "feature has been enabled" : "feature has been disabled", Theme.Colors.TEXT_TEXT)
               ),
               System.currentTimeMillis(),
               enabled ? 0.0F : 0.15F,
               CategoryIconGlyphs.isGlyph(category) ? CategoryIconGlyphs.glyph(category) : null
            )
         );
      }
   }

   private static void push(NotificationsElement.Note note) {
      NOTES.addLast(note);

      while (NOTES.size() > 5) {
         NOTES.removeFirst();
      }
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      this.trackEffects(mc);
      long now = System.currentTimeMillis();
      NOTES.removeIf(notex -> now - notex.createdAt() >= 4000L);
      this.visible.clear();
      this.visible.addAll(NOTES);
      if (this.visible.isEmpty() && showcase(mc)) {
         this.visible.add(this.exampleNote(now));
      }

      if (this.visible.isEmpty()) {
         this.width = 0.0F;
         this.height = 0.0F;
      } else {
         float maxWidth = 0.0F;
         float stackHeight = 0.0F;

         for (NotificationsElement.Note note : this.visible) {
            maxWidth = Math.max(maxWidth, this.pillWidth(note, unit));
            stackHeight += (this.style.height() + 2.5F) * unit * envelope(note, now);
         }

         this.width = maxWidth;
         this.height = Math.max(1.0F, stackHeight - 2.5F * unit);
      }
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      long now = System.currentTimeMillis();
      float guiWidth = mc.getWindow().getGuiScaledWidth();
      float pillY = this.y;

      for (NotificationsElement.Note note : this.visible) {
         float envelope = envelope(note, now);
         if (envelope > 0.01F) {
            this.drawPill(note, guiWidth, pillY, unit, now, envelope);
         }

         pillY += (this.style.height() + 2.5F) * unit * envelope;
      }
   }

   private static float envelope(NotificationsElement.Note note, long now) {
      long age = now - note.createdAt();
      float appear = Math.clamp((float)age / 200.0F, 0.0F, 1.0F);
      float disappear = Math.clamp((float)(4000L - age) / 300.0F, 0.0F, 1.0F);
      float value = Math.min(appear, disappear);
      return value * value * (3.0F - 2.0F * value);
   }

   private void drawPill(NotificationsElement.Note note, float guiWidth, float pillY, float unit, long now, float alpha) {
      float pillHeight = this.style.height() * unit;
      float pillWidth = this.pillWidth(note, unit);
      float pillX = (guiWidth - pillWidth) / 2.0F;
      long age = now - note.createdAt();
      float appear = Math.clamp((float)age / 200.0F, 0.0F, 1.0F);
      pillY -= (1.0F - appear * appear * (3.0F - 2.0F * appear)) * 6.0F * unit;
      float centerY = pillY + pillHeight / 2.0F;
      float textSize = 7.5F * unit;
      boolean muted = note.opacityDelta() > 0.001F;
      this.drawWatermarkPanel(pillX, pillY, pillWidth, pillHeight, this.style.radius() * unit, unit, alpha);
      float iconSize = this.style.iconSize() * unit;
      float cursor = pillX + this.style.padding() * unit;
      boolean semanticIcon = Textures.Hud.ICON_SUCCESS.equals(note.icon()) || Textures.Hud.ICON_WARNING.equals(note.icon());
      int iconTint = semanticIcon ? note.iconTint() : HudFeature.hudAccent();
      if (note.iconGlyph() != null) {
         MsdfFont iconFont = UiFonts.divineNotificationIcons();
         Render2DUtil.text(cursor, iconFont.centeredTextY(centerY, iconSize), iconSize, note.iconGlyph())
            .font(iconFont)
            .color(ColorUtil.multiplyAlpha(iconTint, alpha))
            .draw();
      } else {
         Render2DUtil.TextureBuilder icon = Render2DUtil.texture(cursor, centerY - iconSize / 2.0F, iconSize, iconSize, note.icon());
         if (note.managedIcon()) {
            icon.managed();
            icon.color(ColorUtil.multiplyAlpha(-1, alpha));
         } else {
            icon.color(ColorUtil.multiplyAlpha(iconTint, alpha));
         }

         icon.draw();
      }

      cursor = drawGlassChevron(cursor + iconSize, centerY, unit, alpha);

      for (int index = 0; index < note.segments().size(); index++) {
         NotificationsElement.Segment segment = note.segments().get(index);
         if (index > 0 && !segment.attach()) {
            cursor += 2.0F * unit;
         }

         int segmentColor = segment.color();
         if (muted) {
            segmentColor = glassMutedColor();
         } else if (segment.gradient()) {
            segmentColor = HudFeature.hudAccent();
         } else if (segmentColor == Theme.Colors.TEXT_TEXT || segmentColor == Theme.Colors.TEXT_TITLE) {
            segmentColor = GLASS_TEXT_COLOR;
         }

         drawGlassText(segment.text(), cursor, centerY, textSize, UiFontStyle.MEDIUM, segmentColor, alpha);
         cursor += glassTextWidth(segment.text(), textSize, UiFontStyle.MEDIUM);
      }
   }

   private float pillWidth(NotificationsElement.Note note, float unit) {
      float textSize = 7.5F * unit;
      float textWidth = 0.0F;

      for (int index = 0; index < note.segments().size(); index++) {
         NotificationsElement.Segment segment = note.segments().get(index);
         textWidth += glassTextWidth(segment.text(), textSize, UiFontStyle.MEDIUM);
         if (index > 0 && !segment.attach()) {
            textWidth += 2.0F * unit;
         }
      }

      return (this.style.padding() * 2.0F + this.style.iconSize()) * unit + glassChevronWidth(unit) + textWidth;
   }

   private void trackEffects(Minecraft mc) {
      if (mc.player == null) {
         this.trackedEffects.clear();
      } else {
         Map<Identifier, NotificationsElement.EffectState> current = new HashMap<>();

         for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            Identifier effectId = ((ResourceKey)effect.getEffect().unwrapKey().orElseThrow()).identifier();
            int duration = effect.getDuration();
            int amplifier = effect.getAmplifier();
            NotificationsElement.EffectState previous = this.trackedEffects.get(effectId);
            boolean expiryNotified = previous != null && previous.expiryNotified();
            current.put(effectId, new NotificationsElement.EffectState(duration, amplifier, expiryNotified));
         }

         this.trackedEffects.clear();
         this.trackedEffects.putAll(current);
      }
   }

   private NotificationsElement.Note exampleNote(long now) {
      return new NotificationsElement.Note(
         categoryIcon(FeatureCategory.VISUAL),
         Theme.getAccent(),
         false,
         List.of(
            new NotificationsElement.Segment("The", Theme.Colors.TEXT_TEXT),
            new NotificationsElement.Segment("ExampleFeature", Theme.Colors.TEXT_TITLE),
            new NotificationsElement.Segment("feature has been enabled", Theme.Colors.TEXT_TEXT)
         ),
         now - 2000L
      );
   }

   private static Identifier categoryIcon(FeatureCategory category) {
      int index = category.ordinal();
      return CATEGORY_ICONS[index >= 0 && index < CATEGORY_ICONS.length ? index : 0];
   }

   private record EffectState(int duration, int amplifier, boolean expiryNotified) {
   }

   private record Note(
      Identifier icon, int iconTint, boolean managedIcon, List<NotificationsElement.Segment> segments, long createdAt, float opacityDelta, String iconGlyph
   ) {
      private Note(Identifier icon, int iconTint, boolean managedIcon, List<NotificationsElement.Segment> segments, long createdAt) {
         this(icon, iconTint, managedIcon, segments, createdAt, 0.0F, null);
      }

      private Note(Identifier icon, int iconTint, boolean managedIcon, List<NotificationsElement.Segment> segments, long createdAt, float opacityDelta) {
         this(icon, iconTint, managedIcon, segments, createdAt, opacityDelta, null);
      }
   }

   public enum NotificationType {
      SUCCESS,
      WARNING,
      INFO,
      MODULE;
   }

   private record Segment(String text, int color, boolean gradient, boolean attach) {
      private Segment(String text, int color) {
         this(text, color, false, false);
      }

      private Segment(String text, int color, boolean gradient) {
         this(text, color, gradient, false);
      }
   }

   private record Style(float height, float radius, float padding, float iconSize) {
   }

   public static final class ToggleListener {
      @EventTarget
      public void onFeatureToggle(FeatureToggleEvent event) {
         NotificationsElement.featureToggled(event.getFeature(), event.isEnabled());
      }
   }
}
