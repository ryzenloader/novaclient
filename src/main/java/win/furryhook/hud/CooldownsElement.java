package win.furryhook.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import win.furryhook.context.RenderContext;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.mixin.accessor.ItemCooldownInstanceAccessor;
import win.furryhook.mixin.accessor.ItemCooldownsAccessor;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class CooldownsElement extends HudElement {
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
   private static final Identifier CHAT_PREVIEW_ID = Identifier.parse("furryhook:cooldown_preview");
   private final AnimatedValue visibilityAnim = new AnimatedValue();
   private final AnimatedValue widthAnim = new AnimatedValue();
   private final AnimatedValue heightAnim = new AnimatedValue();
   private final Map<Identifier, CooldownsElement.RowState> rowStates = new LinkedHashMap<>();
   private final Map<Identifier, Item> resolvedItems = new HashMap<>();
   private float visible;
   private int activeCount;
   private List<CooldownsElement.RowVisual> entries = List.of();

   public CooldownsElement() {
      super("cooldowns", "Cooldowns");
   }

   @Override
   protected float defaultX(float unit) {
      return 12.0F * unit;
   }

   @Override
   protected float defaultY(float unit) {
      return 94.0F * unit;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
      List<CooldownsElement.RowVisual> entries = this.collectEntries(mc, now, chatOpen);
      this.activeCount = (int)this.rowStates.values().stream().filter(state -> state.seen).count();
      float headerWidth = 20.0F
         + glassChevronWidth(1.0F)
         + glassTextWidth("Cooldowns", 7.5F, UiFontStyle.SEMIBOLD)
         + 8.0F
         + glassTextWidth(Integer.toString(this.activeCount), 6.0F, UiFontStyle.MEDIUM);
      float targetWidth = Math.max(96.0F, headerWidth);

      for (CooldownsElement.RowState state : this.rowStates.values()) {
         CooldownsElement.CooldownEntry entry = state.entry;
         targetWidth = Math.max(targetWidth, 25.0F + glassTextWidth(entry.name(), 7.0F, UiFontStyle.MEDIUM) + 8.0F + glassBadgeWidth(entry.time(), 6.5F, 1.0F));
      }

      float rowStride = 13.0F;
      float animatedRowsHeight = 0.0F;

      for (CooldownsElement.RowVisual visual : entries) {
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
         GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
         if (extractor != null) {
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
            MsdfFont font = UiFonts.sfPro(400);
            MsdfFont titleFont = UiFonts.sfPro(600);
            float titleSize = 7.5F * unit;
            float titleCenterY = y + headerHeight * 0.5F;
            Render2DUtil.text(x + 6.0F * unit, titleFont.centeredTextY(titleCenterY, titleSize), titleSize, "Cooldowns")
               .font(titleFont)
               .style(UiFontStyle.SEMIBOLD)
               .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, this.visible))
               .draw();
            float headerIconSize = 8.0F * unit;
            Render2DUtil.texture(
                  x + width - 6.0F * unit - headerIconSize, titleCenterY - headerIconSize / 2.0F, headerIconSize, headerIconSize, Textures.Hud.ICON_TIME
               )
               .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), this.visible))
               .draw();
            if (hasBody) {
               float textSize = 7.0F * unit;
               float iconSize = 9.0F * unit;
               float cursorY = y + headerHeight + 3.0F * unit;
               Render2DUtil.flush();

               for (CooldownsElement.RowVisual rowVisual : this.entries) {
                  CooldownsElement.CooldownEntry entry = rowVisual.entry();
                  float progress = MathUtil.clamp01(rowVisual.progress());
                  float rowAlpha = this.visible * progress;
                  float rowCenterY = cursorY + 5.5F * unit;
                  drawItem(mc, extractor, entry.stack(), x + 6.0F * unit, rowCenterY - iconSize / 2.0F, iconSize);
                  Render2DUtil.text(x + 6.0F * unit + iconSize + 4.0F * unit, font.centeredTextY(rowCenterY, textSize), textSize, entry.name())
                     .font(font)
                     .style(UiFontStyle.REGULAR)
                     .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, rowAlpha))
                     .draw();
                  float timeWidth = font.measureWidth(entry.time(), textSize, 0.0F);
                  Render2DUtil.text(x + width - 6.0F * unit - timeWidth, font.centeredTextY(rowCenterY, textSize), textSize, entry.time())
                     .font(font)
                     .style(UiFontStyle.REGULAR)
                     .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), rowAlpha))
                     .draw();
                  cursorY += 13.0F * unit * progress;
               }
            }
         }
      }
   }

   private static void drawItem(Minecraft mc, GuiGraphicsExtractor extractor, ItemStack stack, float x, float y, float size) {
      if (!stack.isEmpty()) {
         float guiScale = mc.getWindow().getGuiScale();
         float itemX = Math.round(x * guiScale) / guiScale;
         float itemY = Math.round(y * guiScale) / guiScale;
         Matrix3x2fStack pose = extractor.pose();
         pose.pushMatrix();
         pose.translate(itemX, itemY);
         pose.scale(size / 16.0F);
         extractor.item(mc.player, stack, 0, 0, 0);
         pose.popMatrix();
      }
   }

   private List<CooldownsElement.RowVisual> collectEntries(Minecraft mc, long now, boolean chatOpen) {
      Set<Identifier> liveIds = new HashSet<>();
      List<CooldownsElement.RowState> liveStates = new ArrayList<>();
      if (mc.player != null) {
         ItemCooldowns manager = mc.player.getCooldowns();
         ItemCooldownsAccessor accessor = (ItemCooldownsAccessor)manager;
         int currentTick = accessor.furryhook$getTickCount();

         for (Entry<Identifier, Object> raw : accessor.furryhook$getCooldowns().entrySet()) {
            if (raw.getValue() instanceof ItemCooldownInstanceAccessor cooldown) {
               float remainingTicks = cooldown.furryhook$getEndTime() - currentTick;
               if (!(remainingTicks <= 0.0F)) {
                  Identifier id = raw.getKey();
                  Item item = this.resolveItem(id, manager, mc);
                  ItemStack stack = new ItemStack(item);
                  float seconds = remainingTicks / 20.0F;
                  int durationTicks = Math.max(1, cooldown.furryhook$getEndTime() - cooldown.furryhook$getStartTime());
                  float progress = MathUtil.clamp(remainingTicks / durationTicks, 0.0F, 1.0F);
                  String name = item == Items.AIR ? id.getPath().replace('_', ' ') : stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                  CooldownsElement.RowState state = this.rowStates.computeIfAbsent(id, CooldownsElement.RowState::new);
                  state.entry = new CooldownsElement.CooldownEntry(stack, name, formatSeconds(seconds), seconds, progress);
                  state.seen = true;
                  liveIds.add(id);
                  liveStates.add(state);
               }
            }
         }
      }

      liveStates.sort(
         Comparator.<CooldownsElement.RowState>comparingDouble(statex -> statex.entry.remaining()).reversed().thenComparing(statex -> statex.id.toString())
      );
      int i = 0;

      while (i < liveStates.size()) {
         liveStates.get(i).targetIndex = i++;
      }

      if (liveStates.isEmpty() && chatOpen && showcase(mc)) {
         CooldownsElement.RowState preview = this.rowStates.computeIfAbsent(CHAT_PREVIEW_ID, CooldownsElement.RowState::new);
         preview.entry = new CooldownsElement.CooldownEntry(new ItemStack(Items.ENDER_PEARL), "ender pearl", "3.2s", 3.2F, 0.64F);
         preview.seen = true;
         preview.targetIndex = 0;
         liveIds.add(CHAT_PREVIEW_ID);
      }

      List<CooldownsElement.RowVisual> visuals = new ArrayList<>();
      List<Identifier> remove = new ArrayList<>();

      for (Entry<Identifier, CooldownsElement.RowState> mapEntry : this.rowStates.entrySet()) {
         CooldownsElement.RowState state = mapEntry.getValue();
         boolean seen = liveIds.contains(mapEntry.getKey());
         state.seen = seen;
         float animation = MathUtil.clamp01(state.animation.update(seen, now));
         if (!seen && state.animation.isHidden()) {
            remove.add(mapEntry.getKey());
         } else if (state.entry != null && animation > 0.001F) {
            visuals.add(new CooldownsElement.RowVisual(state.entry, state.targetIndex, animation, state.id));
         }
      }

      remove.forEach(this.rowStates::remove);
      visuals.sort(Comparator.comparingInt(CooldownsElement.RowVisual::targetIndex).thenComparing(visual -> visual.id().toString()));
      return visuals;
   }

   private Item resolveItem(Identifier group, ItemCooldowns manager, Minecraft mc) {
      Item cached = this.resolvedItems.get(group);
      if (cached != null) {
         return cached;
      }

      if (BuiltInRegistries.ITEM.containsKey(group)) {
         Item direct = (Item)BuiltInRegistries.ITEM.getValue(group);
         if (direct != Items.AIR) {
            this.resolvedItems.put(group, direct);
            return direct;
         }
      }

      for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
         ItemStack stack = mc.player.getInventory().getItem(slot);
         if (!stack.isEmpty() && group.equals(manager.getCooldownGroup(stack))) {
            this.resolvedItems.put(group, stack.getItem());
            return stack.getItem();
         }
      }

      for (Item item : BuiltInRegistries.ITEM) {
         if (item != Items.AIR && group.equals(manager.getCooldownGroup(new ItemStack(item)))) {
            this.resolvedItems.put(group, item);
            return item;
         }
      }

      this.resolvedItems.put(group, Items.AIR);
      return Items.AIR;
   }

   private static String formatSeconds(float seconds) {
      return String.format(Locale.ROOT, "%.1fs", Math.max(0.0F, seconds));
   }

   private record CooldownEntry(ItemStack stack, String name, String time, float remaining, float cooldownProgress) {
   }

   private static final class RowState {
      private final Identifier id;
      private final HudRowAnimation animation = new HudRowAnimation();
      private CooldownsElement.CooldownEntry entry;
      private int targetIndex;
      private boolean seen;

      private RowState(Identifier id) {
         this.id = id;
      }
   }

   private record RowVisual(CooldownsElement.CooldownEntry entry, int targetIndex, float progress, Identifier id) {
   }
}
