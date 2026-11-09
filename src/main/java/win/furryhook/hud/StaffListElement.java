package win.furryhook.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.PlayerTeam;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.PlayerHead;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class StaffListElement extends HudElement {
   private static final float MIN_WIDTH = 108.0F;
   private static final float HEADER_HEIGHT = 16.0F;
   private static final float HEADER_RADIUS = 5.0F;
   private static final float BODY_RADIUS = 5.0F;
   private static final float HEADER_ICON_SIZE = 8.0F;
   private static final float BODY_PAD_TOP = 3.0F;
   private static final float BODY_PAD_BOTTOM = 5.0F;
   private static final float ROW_HEIGHT = 11.0F;
   private static final float ROW_GAP = 2.0F;
   private static final float STATUS_SIZE = 6.0F;
   private static final float NAME_SIZE = 7.0F;
   private static final float TITLE_SIZE = 7.5F;
   private static final float HEAD_SIZE = 7.5F;
   private static final float HEAD_GAP = 4.0F;
   private static final float PAD_X = 6.0F;
   private static final float VALUE_GAP = 8.0F;
   private static final long REFRESH_INTERVAL_MS = 400L;
   private static final int STATUS_ONLINE = ColorUtil.rgba(92, 211, 128, 255);
   private static final int STATUS_VANISH = ColorUtil.rgba(238, 171, 76, 255);
   private static final Set<String> EXCLUDED_NAMES = Set.of("d.helper");
   private final AnimatedValue visibilityAnim = new AnimatedValue();
   private final AnimatedValue widthAnim = new AnimatedValue();
   private final AnimatedValue heightAnim = new AnimatedValue();
   private final Map<String, StaffListElement.RowState> rowStates = new HashMap<>();
   private List<StaffListElement.StaffEntry> cachedStaff = List.of();
   private Object cachedWorld;
   private long nextRefreshAtMs;
   private List<StaffListElement.RowVisual> visuals = List.of();
   private float visible;
   private int activeCount;

   public StaffListElement() {
      super("staff_list", "StaffList");
   }

   @Override
   protected float defaultX(float unit) {
      return 104.0F * unit;
   }

   @Override
   protected float defaultY(float unit) {
      return 42.0F * unit;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
      List<StaffListElement.StaffEntry> staff = this.getCachedStaff(mc, System.currentTimeMillis());

      for (StaffListElement.RowState state : this.rowStates.values()) {
         state.seen = false;
      }

      int index = 0;

      while (index < staff.size()) {
         StaffListElement.StaffEntry entry = staff.get(index);
         String key = entry.name().toLowerCase(Locale.ROOT);
         StaffListElement.RowState state = this.rowStates.get(key);
         if (state == null) {
            state = new StaffListElement.RowState(entry);
            this.rowStates.put(key, state);
         } else {
            state.entry = entry;
         }

         state.seen = true;
         state.targetIndex = index++;
      }

      List<StaffListElement.RowVisual> visuals = new ArrayList<>();
      List<String> toRemove = new ArrayList<>();

      for (Entry<String, StaffListElement.RowState> stateEntry : this.rowStates.entrySet()) {
         StaffListElement.RowState state = stateEntry.getValue();
         float progress = MathUtil.clamp01(state.animation.update(state.seen, now));
         if (!state.seen && state.animation.isHidden()) {
            toRemove.add(stateEntry.getKey());
         } else if (progress > 0.001F) {
            visuals.add(new StaffListElement.RowVisual(state.entry, state.targetIndex, progress));
         }
      }

      toRemove.forEach(this.rowStates::remove);
      visuals.sort(
         Comparator.comparingInt(StaffListElement.RowVisual::targetIndex).thenComparing(visual -> visual.entry().name(), String.CASE_INSENSITIVE_ORDER)
      );
      this.visuals = visuals;
      this.activeCount = (int)this.rowStates.values().stream().filter(state -> state.seen).count();
      float headerWidth = 20.0F
         + glassChevronWidth(1.0F)
         + glassTextWidth("Staff list", 7.5F, UiFontStyle.SEMIBOLD)
         + 8.0F
         + glassTextWidth(Integer.toString(this.activeCount), 6.0F, UiFontStyle.MEDIUM);
      float targetWidth = Math.max(108.0F, headerWidth);

      for (StaffListElement.RowState state : this.rowStates.values()) {
         StaffListElement.StaffEntry entry = state.entry;
         float rowWidth = 23.5F + glassTextWidth(entry.name(), 7.0F, UiFontStyle.MEDIUM) + 8.0F + glassBadgeWidth(entry.status(), 6.0F, 1.0F);
         targetWidth = Math.max(targetWidth, rowWidth);
      }

      float rowStride = 13.0F;
      float animatedRowsHeight = 0.0F;

      for (StaffListElement.RowVisual visual : visuals) {
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
         MsdfFont titleFont = UiFonts.sfPro(600);
         float titleSize = 7.5F * unit;
         float titleCenterY = y + headerHeight * 0.5F;
         Render2DUtil.text(x + 6.0F * unit, titleFont.centeredTextY(titleCenterY, titleSize), titleSize, "Staff list")
            .font(titleFont)
            .style(UiFontStyle.SEMIBOLD)
            .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, this.visible))
            .draw();
         float headerIconSize = 8.0F * unit;
         Render2DUtil.texture(
               x + width - 6.0F * unit - headerIconSize, titleCenterY - headerIconSize / 2.0F, headerIconSize, headerIconSize, Textures.Hud.ICON_PLAYER
            )
            .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), this.visible))
            .draw();
         if (hasBody) {
            MsdfFont font = UiFonts.sfPro(400);
            float statusSize = 6.0F * unit;
            float nameSize = 7.0F * unit;
            float headSize = 7.5F * unit;
            float headGap = 4.0F * unit;
            float cursorY = y + headerHeight + 3.0F * unit;

            for (StaffListElement.RowVisual visual : this.visuals) {
               StaffListElement.StaffEntry entry = visual.entry();
               float progress = visual.progress();
               float rowAlpha = this.visible * progress;
               float rowCenterY = cursorY + 11.0F * unit / 2.0F;
               boolean online = "Online".equals(entry.status());
               int statusColor = online ? STATUS_ONLINE : STATUS_VANISH;
               String statusText = entry.status();
               Identifier headSkin = entry.skin();
               if (headSkin != null) {
                  PlayerHead.draw(x + 6.0F * unit, rowCenterY - headSize / 2.0F, headSize, headSkin, headSize / 2.0F, ColorUtil.multiplyAlpha(-1, rowAlpha));
               } else {
                  Render2DUtil.texture(x + 6.0F * unit, rowCenterY - headSize / 2.0F, headSize, headSize, Textures.Hud.ICON_PLAYER)
                     .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), rowAlpha))
                     .draw();
               }

               float statusW = font.measureWidth(statusText, statusSize, 0.0F);
               float nameX = x + 6.0F * unit + headSize + headGap;
               String name = font.ellipsize(entry.name(), nameSize, 0.0F, Math.max(12.0F * unit, x + width - 6.0F * unit - statusW - 4.0F * unit - nameX));
               Render2DUtil.text(nameX, font.centeredTextY(rowCenterY, nameSize), nameSize, name)
                  .font(font)
                  .style(UiFontStyle.REGULAR)
                  .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, rowAlpha))
                  .draw();
               Render2DUtil.text(x + width - 6.0F * unit - statusW, font.centeredTextY(rowCenterY, statusSize), statusSize, statusText)
                  .font(font)
                  .style(UiFontStyle.REGULAR)
                  .color(ColorUtil.multiplyAlpha(statusColor, rowAlpha))
                  .draw();
               cursorY += 13.0F * unit * progress;
            }
         }
      }
   }

   private List<StaffListElement.StaffEntry> getCachedStaff(Minecraft mc, long nowMs) {
      if (mc.level != null && mc.getConnection() != null) {
         if (this.cachedWorld != mc.level || nowMs >= this.nextRefreshAtMs) {
            this.cachedWorld = mc.level;
            this.cachedStaff = this.collectStaff(mc);
            this.nextRefreshAtMs = nowMs + 400L;
         }

         return this.cachedStaff;
      } else {
         this.cachedStaff = List.of();
         this.cachedWorld = null;
         this.nextRefreshAtMs = 0L;
         return this.cachedStaff;
      }
   }

   private List<StaffListElement.StaffEntry> collectStaff(Minecraft mc) {
      if (mc.level != null && mc.getConnection() != null) {
         Map<String, PlayerInfo> onlineInfo = new HashMap<>();
         mc.getConnection().getOnlinePlayers().forEach(infox -> onlineInfo.put(infox.getProfile().name().toLowerCase(Locale.ROOT), infox));
         Map<String, StaffListElement.StaffEntry> result = new HashMap<>();

         for (PlayerTeam team : mc.level.getScoreboard().getPlayerTeams()) {
            String role = team.getPlayerPrefix() == null ? "" : team.getPlayerPrefix().getString().trim();
            if (this.isStaffRole(role)) {
               for (String name : team.getPlayers()) {
                  String key = name.toLowerCase(Locale.ROOT);
                  if (!EXCLUDED_NAMES.contains(key)) {
                     PlayerInfo info = onlineInfo.get(key);
                     Identifier skin = info != null ? info.getSkin().body().texturePath() : null;
                     result.put(key, new StaffListElement.StaffEntry(name, skin, info != null ? "Online" : "Vanish"));
                  }
               }
            }
         }

         return result.values()
            .stream()
            .sorted(
               Comparator.comparing(StaffListElement.StaffEntry::status)
                  .reversed()
                  .thenComparing(StaffListElement.StaffEntry::name, String.CASE_INSENSITIVE_ORDER)
            )
            .toList();
      } else {
         return List.of();
      }
   }

   private boolean isStaffRole(String role) {
      String value = role.toLowerCase(Locale.ROOT);
      return value.contains("admin")
         || value.contains("moder")
         || value.contains("helper")
         || value.contains("staff")
         || value.contains("owner")
         || value.contains("curator")
         || value.contains("админ")
         || value.contains("модер")
         || value.contains("хелпер");
   }

   private static final class RowState {
      private StaffListElement.StaffEntry entry;
      private final HudRowAnimation animation = new HudRowAnimation();
      private boolean seen;
      private int targetIndex;

      private RowState(StaffListElement.StaffEntry entry) {
         this.entry = entry;
      }
   }

   private record RowVisual(StaffListElement.StaffEntry entry, int targetIndex, float progress) {
   }

   private record StaffEntry(String name, Identifier skin, String status) {
   }
}
