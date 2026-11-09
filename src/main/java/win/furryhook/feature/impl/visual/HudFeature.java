package win.furryhook.feature.impl.visual;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.event.events.render.Render2DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.hud.BottomStatsElement;
import win.furryhook.hud.CooldownsElement;
import win.furryhook.hud.HudElement;
import win.furryhook.hud.HudElementSettingsPopup;
import win.furryhook.hud.KeybindsElement;
import win.furryhook.hud.MediaPlayerElement;
import win.furryhook.hud.NotificationsElement;
import win.furryhook.hud.PotionsElement;
import win.furryhook.hud.PveStatusElement;
import win.furryhook.hud.QuickUseElement;
import win.furryhook.hud.StaffListElement;
import win.furryhook.hud.TargetElement;
import win.furryhook.hud.TotemCounterElement;
import win.furryhook.hud.TpsElement;
import win.furryhook.hud.WatermarkElement;
import win.furryhook.menu.core.MenuConfigStore;
import win.furryhook.menu.i18n.MenuText;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class HudFeature extends Feature {
   private static final float HUD_SCALE = 2.142F;
   private static final String SIZE_MIGRATION_KEY = "hudScale100Migrated";
   private static final double LEGACY_DEFAULT_SIZE = 70.0;
   private static final String SIZE_REBASE_KEY = "hudScale153Rebased";
   private static final double PRE_REBASE_SIZE = 153.0;
   private static final Identifier RESET_ICON = Identifier.parse("furryhook:textures/menu/icons/refresh_ccw.svg");
   private static final int RESET_DIVIDER_COLOR = ColorUtil.rgba(255, 255, 255, 25);
   private static final String RESET_LABEL = "Сбросить расположение";
   private static final float SNAP_MARGIN = 12.0F;
   private static final float SNAP_GAP = 6.0F;
   private static final float SNAP_THRESHOLD = 8.0F;
   private static final String WATERMARK = "Watermark";
   private static final String KEYBINDS = "Keybinds";
   private static final String POTIONS = "Potions";
   private static final String TARGET = "Target";
   private static final String NOTIFICATIONS = "Notifications";
   private static final String INFORMATION = "Information";
   private static final String QUICK_USE = "QuickUse";
   private static final String COOLDOWNS = "Cooldowns";
   private static final String STAFF_LIST = "StaffList";
   private static final String TOTEM_COUNTER = "TotemCounter";
   private static final String PVE_STATE = "PveState";
   private static final String MEDIA_PLAYER = "MediaPlayer";
   public final MultiSelectSetting elements = this.register(
      new MultiSelectSetting(
         "Elements",
         List.of(
            "Watermark", "Keybinds", "Potions", "Target", "Notifications", "Information", "QuickUse", "Cooldowns", "StaffList", "TotemCounter", "MediaPlayer"
         ),
         "Watermark",
         "Keybinds",
         "Potions",
         "Target",
         "Notifications",
         "Information",
         "QuickUse",
         "Cooldowns",
         "StaffList",
         "TotemCounter",
         "PveState",
         "MediaPlayer"
      )
   );
   public final ModeSetting targetStyle = this.register(new ModeSetting("Target Style", "Default", "Default", "Gsmar", "Powerz").visibleWhen(() -> false));
   public final BooleanSetting targetHeadParticles = this.register(new BooleanSetting("Head Particles", true).visibleWhen(() -> false));
   public final BooleanSetting targetHealthParticles = this.register(new BooleanSetting("Health Particles", true).visibleWhen(() -> false));
   public final NumberSetting size = this.register(new NumberSetting("Size", 100.0, 50.0, 200.0, 1.0, "%").visibleWhen(() -> false));
   public final MultiSelectSetting watermarkContent = this.register(
         new MultiSelectSetting("Watermark Content", List.of("Player"), WatermarkElement.CONTENT_ITEMS)
      )
      .visibleWhen(() -> false);
   private static final List<String> INFORMATION_CONTENT_ITEMS = List.of("Coordinates", "BPS", "TPS");
   public final MultiSelectSetting informationContent = this.register(
      new MultiSelectSetting("Information Content", INFORMATION_CONTENT_ITEMS, INFORMATION_CONTENT_ITEMS.toArray(new String[0]))
         .visibleWhen(() -> this.elements.isSelected("Information"))
   );
   private static HudFeature instance;
   private final WatermarkElement watermarkElement = new WatermarkElement();
   private final KeybindsElement keybindsElement = new KeybindsElement();
   private final PotionsElement potionsElement = new PotionsElement();
   private final TargetElement targetElement = new TargetElement();
   private final NotificationsElement notificationsElement = new NotificationsElement();
   private final BottomStatsElement bottomStatsElement = new BottomStatsElement();
   private final TpsElement tpsElement = new TpsElement();
   private final QuickUseElement quickUseElement = new QuickUseElement();
   private final PveStatusElement pveStatusElement = new PveStatusElement();
   private final CooldownsElement cooldownsElement = new CooldownsElement();
   private final StaffListElement staffListElement = new StaffListElement();
   private final TotemCounterElement totemCounterElement = new TotemCounterElement();
   private final MediaPlayerElement mediaPlayerElement = new MediaPlayerElement();
   private final HudElementSettingsPopup settingsPopup = new HudElementSettingsPopup();
   private final List<HudElement> allElements = List.of(
      this.watermarkElement,
      this.keybindsElement,
      this.potionsElement,
      this.targetElement,
      this.notificationsElement,
      this.bottomStatsElement,
      this.tpsElement,
      this.quickUseElement,
      this.pveStatusElement,
      this.cooldownsElement,
      this.staffListElement,
      this.totemCounterElement,
      this.mediaPlayerElement
   );
   private HudElement draggedElement;
   private boolean sizeMigrationChecked;
   private boolean sizeRebaseChecked;
   private boolean elementSizeMigrationChecked;
   private float resetButtonX;
   private float resetButtonY;
   private float resetButtonWidth;
   private float resetButtonHeight;
   private float activeGuideX = Float.NaN;
   private float activeGuideY = Float.NaN;

   public HudFeature() {
      super("HUD", "HUD customization settings", FeatureCategory.VISUAL, -1);
      instance = this;
   }

   public static float backgroundOpacity() {
      return 1.0F;
   }

   public static boolean blurEnabled() {
      return true;
   }

   public static float blurRadius() {
      return 8.0F;
   }

   public static int panelColor() {
      return ColorUtil.rgb(12, 14, 23);
   }

   public static float panelOpacity() {
      return 0.72F;
   }

   public static int headerColor() {
      return ColorUtil.rgb(18, 20, 30);
   }

   public static boolean glowEnabled() {
      return false;
   }

   public static float glowRadius() {
      return 0.0F;
   }

   public static float glowOpacity() {
      return 0.0F;
   }

   public static int hudAccent() {
      return Theme.getAccent();
   }

   public static boolean gsmarTarget() {
      return instance != null && instance.targetStyle.is("Gsmar");
   }

   public static boolean powerzTarget() {
      return instance != null && instance.targetStyle.is("Powerz");
   }

   public static boolean targetHeadParticles() {
      return instance != null && instance.targetHeadParticles.getValue();
   }

   public static boolean targetHealthParticles() {
      return instance != null && instance.targetHealthParticles.getValue();
   }

   public static String targetStyleName() {
      return instance == null ? "Default" : instance.targetStyle.getValue();
   }

   public static void setTargetStyle(String style) {
      if (instance != null) {
         instance.targetStyle.setValue(style);
      }
   }

   public static void toggleTargetHeadParticles() {
      if (instance != null) {
         instance.targetHeadParticles.setValue(!instance.targetHeadParticles.getValue());
      }
   }

   public static void toggleTargetHealthParticles() {
      if (instance != null) {
         instance.targetHealthParticles.setValue(!instance.targetHealthParticles.getValue());
      }
   }

   public static boolean glareEnabled() {
      return false;
   }

   public static boolean watermarkShows(String item) {
      return instance != null && instance.watermarkContent.isSelected(item);
   }

   public static boolean informationShows(String item) {
      return instance != null && instance.informationContent.isSelected(item);
   }

   public static float bossBarYOffset() {
      if (instance != null && instance.isEnabled() && instance.elements.isSelected("Watermark")) {
         Minecraft mc = MinecraftContext.mc;
         if (mc == null) {
            return 0.0F;
         }

         float height = instance.watermarkElement.height();
         if (height <= 0.5F) {
            return 0.0F;
         }

         float screenCenterX = mc.getWindow().getGuiScaledWidth() / 2.0F;
         float watermarkCenterX = instance.watermarkElement.x() + instance.watermarkElement.width() / 2.0F;
         boolean nearTop = instance.watermarkElement.y() < 60.0F;
         boolean nearCenterX = Math.abs(watermarkCenterX - screenCenterX) < instance.watermarkElement.width() / 2.0F + 40.0F;
         return nearTop && nearCenterX ? height + 16.0F : 0.0F;
      } else {
         return 0.0F;
      }
   }

   public static void toggleWatermarkContent(String item) {
      if (instance != null) {
         instance.watermarkContent.toggle(item);
      }
   }

   public static void toggleInformationContent(String item) {
      if (instance != null) {
         instance.informationContent.toggle(item);
      }
   }

   @EventTarget(priority = -100)
   public void onRender2D(Render2DEvent event) {
      Minecraft mc = event.getClient();
      if (mc != null && mc.player != null) {
         this.migrateLegacySize();
         this.rebaseSizeTo153Baseline();
         this.migrateElementSizes();
         float unit = this.unit(mc);
         float popupUnit = this.popupUnit(mc);
         List<HudElement> visible = this.visibleElements();
         boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
         boolean snapMode = chatOpen && controlDown(mc);
         if (this.draggedElement != null) {
            if (chatOpen) {
               this.draggedElement.dragTo(mouseX(mc), mouseY(mc));
               if (snapMode) {
                  this.applySnap(mc, visible);
               } else {
                  this.clearSnapGuides();
               }
            } else {
               this.stopDrag(mc);
            }
         } else {
            this.clearSnapGuides();
         }

         if (this.settingsPopup.isOpen() && !chatOpen) {
            this.settingsPopup.close();
         }

         this.settingsPopup.updatePointer(mouseX(mc), mouseY(mc), popupUnit);
         if (snapMode) {
            this.drawSnapGrid(mc);
         }

         for (HudElement element : visible) {
            element.render(mc, unit);
         }

         if (snapMode) {
            this.drawResetButton(mc, unit, visible);
         } else {
            this.resetButtonWidth = 0.0F;
            this.resetButtonHeight = 0.0F;
         }

         this.settingsPopup.render(mc, popupUnit);
      }
   }

   private float unit(Minecraft mc) {
      return 2.142F / mc.getWindow().getGuiScale();
   }

   private float popupUnit(Minecraft mc) {
      return 1.5F / mc.getWindow().getGuiScale();
   }

   private String popupTitle(HudElement element) {
      if (element == this.cooldownsElement) {
         return MenuText.ui("Cooldowns");
      } else if (element == this.staffListElement) {
         return MenuText.ui("Staff list");
      } else if (element == this.keybindsElement) {
         return MenuText.ui("Keybinds");
      } else {
         return element == this.potionsElement ? MenuText.ui("Potions") : element.displayName();
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      Minecraft mc = MinecraftContext.mc;
      if (mc != null) {
         if (event.getButton() == 0 && event.getAction() == 0) {
            boolean handled = this.settingsPopup.handleRelease();
            if (this.draggedElement != null) {
               this.stopDrag(mc);
               handled = true;
            }

            if (handled) {
               event.cancel();
            }
         } else if (event.getAction() == 1 && mc.gui.screen() instanceof ChatScreen) {
            float mouseX = mouseX(mc);
            float mouseY = mouseY(mc);
            float popupUnit = this.popupUnit(mc);
            if (this.settingsPopup.isOpen()) {
               if (this.settingsPopup.contains(mouseX, mouseY, popupUnit)) {
                  if (event.getButton() == 0) {
                     this.settingsPopup.handleClick(mouseX, mouseY, popupUnit);
                  }

                  event.cancel();
                  return;
               }

               this.settingsPopup.close();
            }

            if (event.getButton() == 1) {
               List<HudElement> visible = this.visibleElements();

               for (int i = visible.size() - 1; i >= 0; i--) {
                  HudElement element = visible.get(i);
                  if (element.contains(mouseX, mouseY)) {
                     if (this.draggedElement != null) {
                        this.stopDrag(mc);
                     }

                     this.settingsPopup
                        .open(
                           element,
                           this.popupTitle(element),
                           element.x(),
                           element.y() + element.height() + 4.0F * popupUnit,
                           mc.getWindow().getGuiScaledWidth(),
                           mc.getWindow().getGuiScaledHeight(),
                           popupUnit
                        );
                     event.cancel();
                     return;
                  }
               }
            } else if (event.getButton() == 0) {
               if (controlDown(mc) && inside(mouseX, mouseY, this.resetButtonX, this.resetButtonY, this.resetButtonWidth, this.resetButtonHeight)) {
                  this.resetPositions();
                  event.cancel();
               } else if (!inside(mouseX, mouseY, mc.getWindow().getGuiScaledWidth() - 150.0F, mc.getWindow().getGuiScaledHeight() - 40.0F, 146.0F, 20.0F)) {
                  List<HudElement> visible = this.visibleElements();

                  for (int i = visible.size() - 1; i >= 0; i--) {
                     HudElement element = visible.get(i);
                     if (element.startDrag(mouseX, mouseY)) {
                        this.draggedElement = element;
                        event.cancel();
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      this.settingsPopup.close();
      if (this.draggedElement != null && MinecraftContext.mc != null) {
         this.stopDrag(MinecraftContext.mc);
      }
   }

   public void resetPositions() {
      this.draggedElement = null;
      this.clearSnapGuides();
      MenuConfigStore.resetHudPositions();
      this.allElements.forEach(HudElement::resetPosition);
   }

   private void applySnap(Minecraft mc, List<HudElement> visible) {
      HudElement dragged = this.draggedElement;
      if (dragged == null) {
         this.clearSnapGuides();
      } else {
         float screenWidth = mc.getWindow().getGuiScaledWidth();
         float screenHeight = mc.getWindow().getGuiScaledHeight();
         float maxX = Math.max(0.0F, screenWidth - dragged.width());
         float maxY = Math.max(0.0F, screenHeight - dragged.height());
         List<HudFeature.SnapCandidate> xCandidates = new ArrayList<>(20);
         List<HudFeature.SnapCandidate> yCandidates = new ArrayList<>(20);
         addCandidate(xCandidates, Math.min(12.0F, maxX), 12.0F, maxX);
         addCandidate(xCandidates, maxX / 2.0F, screenWidth / 2.0F, maxX);
         addCandidate(xCandidates, Math.max(0.0F, maxX - 12.0F), screenWidth - 12.0F, maxX);
         addCandidate(yCandidates, Math.min(12.0F, maxY), 12.0F, maxY);
         addCandidate(yCandidates, maxY / 2.0F, screenHeight / 2.0F, maxY);
         addCandidate(yCandidates, Math.max(0.0F, maxY - 12.0F), screenHeight - 12.0F, maxY);

         for (HudElement other : visible) {
            if (other != dragged && !(other.width() <= 0.5F) && !(other.height() <= 0.5F)) {
               float otherRight = other.x() + other.width();
               float otherBottom = other.y() + other.height();
               float otherCenterX = other.x() + other.width() / 2.0F;
               float otherCenterY = other.y() + other.height() / 2.0F;
               addCandidate(xCandidates, other.x(), other.x(), maxX);
               addCandidate(xCandidates, otherRight - dragged.width(), otherRight, maxX);
               addCandidate(xCandidates, otherCenterX - dragged.width() / 2.0F, otherCenterX, maxX);
               addCandidate(xCandidates, other.x() - dragged.width() - 6.0F, other.x() - 3.0F, maxX);
               addCandidate(xCandidates, otherRight + 6.0F, otherRight + 3.0F, maxX);
               addCandidate(yCandidates, other.y(), other.y(), maxY);
               addCandidate(yCandidates, otherBottom - dragged.height(), otherBottom, maxY);
               addCandidate(yCandidates, otherCenterY - dragged.height() / 2.0F, otherCenterY, maxY);
               addCandidate(yCandidates, other.y() - dragged.height() - 6.0F, other.y() - 3.0F, maxY);
               addCandidate(yCandidates, otherBottom + 6.0F, otherBottom + 3.0F, maxY);
            }
         }

         float x = Math.clamp(dragged.x(), 0.0F, maxX);
         float y = Math.clamp(dragged.y(), 0.0F, maxY);
         this.activeGuideX = Float.NaN;
         this.activeGuideY = Float.NaN;
         if (!dragged.isHorizontallyCentered()) {
            HudFeature.SnapCandidate snappedX = nearestCandidate(x, xCandidates);
            if (snappedX != null) {
               x = snappedX.position();
               this.activeGuideX = snappedX.guide();
            }
         }

         HudFeature.SnapCandidate snappedY = nearestCandidate(y, yCandidates);
         if (snappedY != null) {
            y = snappedY.position();
            this.activeGuideY = snappedY.guide();
         }

         dragged.snapTo(x, y);
      }
   }

   private static void addCandidate(List<HudFeature.SnapCandidate> candidates, float position, float guide, float maximum) {
      if (Float.isFinite(position) && position >= 0.0F && position <= maximum) {
         candidates.add(new HudFeature.SnapCandidate(position, guide));
      }
   }

   private static HudFeature.SnapCandidate nearestCandidate(float position, List<HudFeature.SnapCandidate> candidates) {
      HudFeature.SnapCandidate nearest = null;
      float nearestDistance = 8.0F;

      for (HudFeature.SnapCandidate candidate : candidates) {
         float distance = Math.abs(position - candidate.position());
         if (distance <= nearestDistance) {
            nearest = candidate;
            nearestDistance = distance;
         }
      }

      return nearest;
   }

   private void drawSnapGrid(Minecraft mc) {
      float width = mc.getWindow().getGuiScaledWidth();
      float height = mc.getWindow().getGuiScaledHeight();
      float line = (float)Math.max(0.5, 1.0 / Math.max(1.0, mc.getWindow().getGuiScale()));
      int thirdsColor = ColorUtil.rgba(190, 195, 205, 36);
      int centerColor = ColorUtil.rgba(205, 209, 216, 58);
      int cornerColor = ColorUtil.rgba(210, 214, 220, 78);
      float left = 12.0F;
      float top = 12.0F;
      float right = width - 12.0F;
      float bottom = height - 12.0F;
      float cornerLength = 10.0F;
      drawDashedVertical(width / 3.0F, top, bottom, 1.5F, 6.0F, line, thirdsColor);
      drawDashedVertical(width * 2.0F / 3.0F, top, bottom, 1.5F, 6.0F, line, thirdsColor);
      drawDashedHorizontal(height / 3.0F, left, right, 1.5F, 6.0F, line, thirdsColor);
      drawDashedHorizontal(height * 2.0F / 3.0F, left, right, 1.5F, 6.0F, line, thirdsColor);
      drawDashedVertical(width / 2.0F, top, bottom, 4.0F, 6.0F, line, centerColor);
      drawDashedHorizontal(height / 2.0F, left, right, 4.0F, 6.0F, line, centerColor);
      Render2DUtil.rect(left, top, cornerLength, line).color(cornerColor).draw();
      Render2DUtil.rect(left, top, line, cornerLength).color(cornerColor).draw();
      Render2DUtil.rect(right - cornerLength, top, cornerLength, line).color(cornerColor).draw();
      Render2DUtil.rect(right - line, top, line, cornerLength).color(cornerColor).draw();
      Render2DUtil.rect(left, bottom - line, cornerLength, line).color(cornerColor).draw();
      Render2DUtil.rect(left, bottom - cornerLength, line, cornerLength).color(cornerColor).draw();
      Render2DUtil.rect(right - cornerLength, bottom - line, cornerLength, line).color(cornerColor).draw();
      Render2DUtil.rect(right - line, bottom - cornerLength, line, cornerLength).color(cornerColor).draw();
      if (Float.isFinite(this.activeGuideX)) {
         Render2DUtil.rect(this.activeGuideX - line * 2.0F, 0.0F, line * 4.0F, height).color(ColorUtil.rgba(205, 210, 218, 30)).draw();
         Render2DUtil.rect(this.activeGuideX - line / 2.0F, 0.0F, line, height).color(ColorUtil.rgba(220, 224, 230, 175)).draw();
      }

      if (Float.isFinite(this.activeGuideY)) {
         Render2DUtil.rect(0.0F, this.activeGuideY - line * 2.0F, width, line * 4.0F).color(ColorUtil.rgba(205, 210, 218, 30)).draw();
         Render2DUtil.rect(0.0F, this.activeGuideY - line / 2.0F, width, line).color(ColorUtil.rgba(220, 224, 230, 175)).draw();
      }
   }

   private static void drawDashedVertical(float x, float top, float bottom, float dash, float gap, float width, int color) {
      float y = top;

      while (y < bottom) {
         Render2DUtil.rect(x - width / 2.0F, y, width, Math.min(dash, bottom - y)).color(color).draw();
         y += dash + gap;
      }
   }

   private static void drawDashedHorizontal(float y, float left, float right, float dash, float gap, float height, int color) {
      float x = left;

      while (x < right) {
         Render2DUtil.rect(x, y - height / 2.0F, Math.min(dash, right - x), height).color(color).draw();
         x += dash + gap;
      }
   }

   private void clearSnapGuides() {
      this.activeGuideX = Float.NaN;
      this.activeGuideY = Float.NaN;
   }

   private void drawResetButton(Minecraft mc, float unit, List<HudElement> visible) {
      float height = 28.0F * unit;
      float radius = 8.0F * unit;
      float padding = 8.0F * unit;
      float gap = 4.0F * unit;
      float iconSize = 12.0F * unit;
      float dividerWidth = Math.max(0.5F, 0.5F * unit);
      float textSize = 10.0F * unit;
      MsdfFont font = UiFonts.sfProDisplay();
      float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
      float textWidth = font.measureWidth("Сбросить расположение", textSize, letterSpacing);
      this.resetButtonWidth = padding * 2.0F + iconSize + gap * 2.0F + dividerWidth + textWidth;
      this.resetButtonHeight = height;
      this.resetButtonX = (mc.getWindow().getGuiScaledWidth() - this.resetButtonWidth) / 2.0F;
      this.resetButtonY = this.resolveResetButtonY(mc, visible);
      float mouseX = mouseX(mc);
      float mouseY = mouseY(mc);
      boolean hovered = inside(mouseX, mouseY, this.resetButtonX, this.resetButtonY, this.resetButtonWidth, this.resetButtonHeight);
      int foreground = hovered ? Theme.Colors.PRIMARY_BRIGHT : Theme.Colors.TEXT_TEXT;
      int iconColor = Theme.getAccent();
      float centerY = this.resetButtonY + height / 2.0F;
      Render2DUtil.rect(this.resetButtonX, this.resetButtonY, this.resetButtonWidth, height)
         .color(Theme.Colors.BACKGROUND_PRIMARY_50)
         .radius(radius)
         .border(Math.max(0.5F, 0.5F * unit), Theme.Colors.OUTLINES_MEDIUM)
         .blur(8.0F * unit)
         .draw();
      float cursor = this.resetButtonX + padding;
      Render2DUtil.texture(cursor, centerY - iconSize / 2.0F, iconSize, iconSize, RESET_ICON).color(iconColor).draw();
      cursor += iconSize + gap;
      Render2DUtil.rect(cursor, centerY - iconSize / 2.0F, dividerWidth, iconSize).color(RESET_DIVIDER_COLOR).draw();
      cursor += dividerWidth + gap;
      Render2DUtil.text(cursor, font.centeredTextY(centerY, textSize), textSize, "Сбросить расположение").style(UiFontStyle.MEDIUM).color(foreground).draw();
   }

   private float resolveResetButtonY(Minecraft mc, List<HudElement> visible) {
      float preferredY = 18.0F;
      float minY = 4.0F;
      float maxY = Math.max(minY, mc.getWindow().getGuiScaledHeight() - this.resetButtonHeight - minY);

      for (float offset = 0.0F; offset <= maxY + preferredY; offset += 2.0F) {
         float above = preferredY - offset;
         if (above >= minY && this.resetButtonFits(visible, above)) {
            return above;
         }

         float below = preferredY + offset;
         if (offset > 0.0F && below <= maxY && this.resetButtonFits(visible, below)) {
            return below;
         }
      }

      return Math.min(preferredY, maxY);
   }

   private boolean resetButtonFits(List<HudElement> visible, float y) {
      float gap = 4.0F;

      for (HudElement element : visible) {
         if (element.overlaps(this.resetButtonX - gap, y - gap, this.resetButtonWidth + gap * 2.0F, this.resetButtonHeight + gap * 2.0F)) {
            return false;
         }
      }

      return true;
   }

   private List<HudElement> visibleElements() {
      List<HudElement> visible = new ArrayList<>(4);
      if (this.elements.isSelected("Watermark")) {
         visible.add(this.watermarkElement);
      }

      if (this.elements.isSelected("Keybinds")) {
         visible.add(this.keybindsElement);
      }

      if (this.elements.isSelected("Potions")) {
         visible.add(this.potionsElement);
      }

      if (this.elements.isSelected("Target")) {
         visible.add(this.targetElement);
      }

      if (this.elements.isSelected("Notifications")) {
         visible.add(this.notificationsElement);
      }

      if (this.elements.isSelected("Information")) {
         visible.add(this.bottomStatsElement);
         visible.add(this.tpsElement);
      }

      if (this.elements.isSelected("QuickUse")) {
         visible.add(this.quickUseElement);
      }

      if (this.elements.isSelected("Cooldowns")) {
         visible.add(this.cooldownsElement);
      }

      if (this.elements.isSelected("StaffList")) {
         visible.add(this.staffListElement);
      }

      if (this.elements.isSelected("TotemCounter")) {
         visible.add(this.totemCounterElement);
      }

      if (this.elements.isSelected("PveState")) {
         visible.add(this.pveStatusElement);
      }

      if (this.elements.isSelected("MediaPlayer")) {
         visible.add(this.mediaPlayerElement);
      }

      return visible;
   }

   private void stopDrag(Minecraft mc) {
      this.draggedElement.stopDrag(mc);
      this.draggedElement = null;
      this.clearSnapGuides();
   }

   private static boolean controlDown(Minecraft mc) {
      long window = mc.getWindow().handle();
      return GLFW.glfwGetKey(window, 341) == 1 || GLFW.glfwGetKey(window, 345) == 1;
   }

   private static float mouseX(Minecraft mc) {
      return (float)mc.mouseHandler.getScaledXPos(mc.getWindow());
   }

   private static float mouseY(Minecraft mc) {
      return (float)mc.mouseHandler.getScaledYPos(mc.getWindow());
   }

   private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
      return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
   }

   private void migrateLegacySize() {
      if (!this.sizeMigrationChecked) {
         this.sizeMigrationChecked = true;
         if (!MenuConfigStore.getBoolean("hudScale100Migrated", false)) {
            if (this.size.getValue() == 70.0) {
               this.size.setValue(100.0);
            }

            MenuConfigStore.save(data -> data.addProperty("hudScale100Migrated", true));
         }
      }
   }

   private void rebaseSizeTo153Baseline() {
      if (!this.sizeRebaseChecked) {
         this.sizeRebaseChecked = true;
         if (!MenuConfigStore.getBoolean("hudScale153Rebased", false)) {
            if (this.size.getValue() == 153.0) {
               this.size.setValue(100.0);
            }

            MenuConfigStore.save(data -> data.addProperty("hudScale153Rebased", true));
         }
      }
   }

   private void migrateElementSizes() {
      if (!this.elementSizeMigrationChecked) {
         this.elementSizeMigrationChecked = true;
         double legacySize = this.size.getValue();
         double initialSize = Double.isFinite(legacySize) ? Math.clamp(legacySize, 50.0, 200.0) : 100.0;
         MenuConfigStore.save(data -> {
            for (HudElement element : this.allElements) {
               String key = "hud." + element.id() + ".size";
               if (!data.has(key)) {
                  data.addProperty(key, initialSize);
               }
            }
         });
      }
   }

   private record SnapCandidate(float position, float guide) {
   }
}
