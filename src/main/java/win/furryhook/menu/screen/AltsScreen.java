package win.furryhook.menu.screen;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import win.furryhook.account.Alt;
import win.furryhook.account.AltStore;
import win.furryhook.account.MicrosoftAuth;
import win.furryhook.account.MsAccountStore;
import win.furryhook.menu.core.MenuBackgroundFx;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.RandomNick;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.PlayerHead;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class AltsScreen extends Screen {
   private static final int BACKGROUND = ColorUtil.rgb(6, 9, 16);
   private static final int PANEL = ColorUtil.rgba(13, 18, 29, 242);
   private static final int PANEL_BORDER = ColorUtil.rgba(96, 140, 255, 18);
   private static final int GRID_LINE = ColorUtil.rgba(120, 160, 255, 8);
   private static final int FIELD = ColorUtil.rgba(10, 14, 24, 220);
   private static final int CARD = ColorUtil.rgba(16, 22, 36, 235);
   private static final int CARD_HOVER = ColorUtil.rgba(22, 30, 48, 240);
   private static final int CHECK_BLUE = ColorUtil.rgb(74, 144, 255);
   private static final float PANEL_RADIUS = 12.0F;
   private static final float PANEL_WIDTH = 472.0F;
   private static final float PANEL_HEIGHT = 260.0F;
   private static final float PADDING = 14.0F;
   private static final float SIDE_WIDTH = 158.0F;
   private static final float PANE_GAP = 10.0F;
   private static final float SECTION_RADIUS = 8.0F;
   private static final float HEADER_SIZE = 18.0F;
   private static final float HEADER_BOTTOM = 50.0F;
   private static final float SECTION_TITLE_SIZE = 10.0F;
   private static final float LABEL_SIZE = 10.0F;
   private static final float META_SIZE = 8.0F;
   private static final float HINT_SIZE = 8.0F;
   private static final float HINT_GAP = 16.0F;
   private static final float INPUT_HEIGHT = 22.0F;
   private static final float BUTTON_HEIGHT = 20.0F;
   private static final float CONTROL_RADIUS = 6.0F;
   private static final float CONTROL_GAP = 6.0F;
   private static final float SESSION_HEAD = 26.0F;
   private static final float CARD_HEIGHT = 44.0F;
   private static final float CARD_GAP = 6.0F;
   private static final float CARD_HEAD = 26.0F;
   private static final float CARD_PAD = 8.0F;
   private static final float CARD_ICON = 10.0F;
   private static final float CHECK_SIZE = 9.0F;
   private static final float GRID_TOP_GAP = 8.0F;
   private static final float SCROLL_STEP = 33.0F;
   private static final float SCROLLBAR_WIDTH = 3.0F;
   private static final float SPEED_HOVER = 16.0F;
   private static final float SPEED_SLIDE = 5.0F;
   private static final float CLEAR_HOLD_SECONDS = 1.1F;
   private static final long REMOVE_CONFIRM_NANOS = 3000000000L;
   private static final long FEEDBACK_NANOS = 1500000000L;
   private static final int NAME_MAX_LENGTH = 16;
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
   private static final String TITLE = "ACCOUNTS";
   private final UiAnimator anim = new UiAnimator();
   private final StringBuilder input = new StringBuilder();
   private final StringBuilder search = new StringBuilder();
   private final Screen parent;
   private AltsScreen.FocusField focus = AltsScreen.FocusField.INPUT;
   private float scroll;
   private float scrollMax;
   private Alt pendingRemove;
   private long pendingRemoveUntilNanos;
   private boolean clearPressed;
   private float clearStartX;
   private float clearProgress;
   private long generateFlashNanos;
   private long undoFlashNanos;
   private Alt copiedAlt;
   private long copiedNanos;
   private Alt lengthConfirm;
   private long lengthFlashNanos;
   private final List<Alt> filtered = new ArrayList<>();
   private final MicrosoftAuth microsoftAuth = new MicrosoftAuth();
   private volatile boolean msInProgress;
   private volatile String msCode;
   private volatile String msStatus;
   private int lastMouseX;
   private int lastMouseY;

   public AltsScreen(Screen parent) {
      super(Component.literal("Accounts"));
      this.parent = parent;
   }

   protected void init() {
      this.anim.reset();
   }

   public void onClose() {
      this.microsoftAuth.cancel();
      this.microsoftAuth.shutdown();
      this.minecraft.setScreenAndShow(this.parent);
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
      UiFrame.draw(guiGraphicsExtractor, () -> this.renderScreen(mouseX, mouseY));
   }

   private void renderScreen(int mouseX, int mouseY) {
      this.anim.tick();
      this.lastMouseX = mouseX;
      this.lastMouseY = mouseY;
      Render2DUtil.rect(0.0F, 0.0F, this.width, this.height).color(BACKGROUND).draw();
      this.renderGrid();
      MenuBackgroundFx.render(this.width, this.height, this.anim.delta(), 0.55F, 0.8F, true, true);
      float panelX = this.panelX();
      float panelY = this.panelY();
      this.renderHeader(panelY);
      Render2DUtil.rect(panelX, panelY, 472.0F, 260.0F).color(PANEL).radius(12.0F).border(1.0F, PANEL_BORDER).draw();
      float sideX = panelX + 14.0F;
      float sideW = 158.0F;
      float mainX = sideX + sideW + 10.0F;
      float mainW = panelX + 472.0F - 14.0F - mainX;
      float contentY = panelY + 14.0F;
      float contentH = 232.0F;
      this.renderAccountPanel(sideX, contentY, sideW, contentH, mouseX, mouseY);
      this.renderMainArea(mainX, contentY, mainW, contentH, mouseX, mouseY);
      this.renderHints(mouseX, mouseY);
   }

   private void renderGrid() {
      for (float x = 12.0F; x < this.width; x += 24.0F) {
         for (float y = 12.0F; y < this.height; y += 24.0F) {
            Render2DUtil.rect(x, y, 1.0F, 1.0F).color(GRID_LINE).draw();
         }
      }
   }

   private void renderHeader(float panelY) {
      float centerY = panelY - 50.0F + 24.0F;
      float titleWidth = MainMenuScreen.widthOf("ACCOUNTS", 18.0F, UiFontStyle.SEMIBOLD);
      Render2DUtil.text((this.width - titleWidth) * 0.5F, UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight()).centeredTextY(centerY, 18.0F), 18.0F, "ACCOUNTS")
         .style(UiFontStyle.SEMIBOLD)
         .color(Theme.Colors.PRIMARY_BRIGHT)
         .draw();
      float closeX = this.width - 18.0F - 10.0F;
      float closeY = centerY - 5.0F;
      boolean hovered = MainMenuScreen.insideRect(this.lastMouseX, this.lastMouseY, closeX - 4.0F, closeY - 4.0F, 18.0F, 18.0F);
      float hover = this.anim.value("close", hovered ? 1.0F : 0.0F, 16.0F);
      Render2DUtil.texture(closeX, closeY, 10.0F, 10.0F, Textures.Icons.X)
         .color(ColorUtil.lerp(Theme.Colors.ICON_GHOST, Theme.Colors.PRIMARY_BRIGHT, hover))
         .draw();
   }

   private void renderAccountPanel(float x, float y, float w, float h, int mouseX, int mouseY) {
      float sectionH = 96.0F;
      float sessionY = y + sectionH + 10.0F;
      float sessionH = y + h - sessionY;
      Render2DUtil.rect(x, y, w, sectionH).color(ColorUtil.multiplyAlpha(CARD, 0.6F)).radius(8.0F).border(1.0F, Theme.Colors.DIVIDER_SUBTLE).draw();
      Render2DUtil.text(x + 10.0F, UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight()).centeredTextY(y + 13.0F, 10.0F), 10.0F, "Account Panel")
         .style(UiFontStyle.SEMIBOLD)
         .color(Theme.Colors.SECONDARY)
         .draw();
      float innerX = x + 10.0F;
      float innerW = w - 20.0F;
      float inputY = y + 26.0F;
      this.renderInput(innerX, inputY, innerW, mouseX, mouseY);
      float genY = inputY + 22.0F + 6.0F;
      this.renderGenerate(innerX, genY, innerW, mouseX, mouseY);
      float clearY = genY + 20.0F + 6.0F;
      this.renderClearAll(innerX, clearY, innerW, mouseX, mouseY);
      this.renderSession(x, sessionY, w, sessionH, mouseX, mouseY);
   }

   private void renderInput(float x, float y, float w, int mouseX, int mouseY) {
      float plusW = 22.0F;
      float fieldW = w - plusW - 6.0F;
      boolean active = this.focus == AltsScreen.FocusField.INPUT;
      Render2DUtil.rect(x, y, fieldW, 22.0F)
         .color(FIELD)
         .radius(6.0F)
         .border(1.0F, active ? ColorUtil.withAlpha(CHECK_BLUE, 110) : Theme.Colors.DIVIDER_SUBTLE)
         .draw();
      boolean empty = this.input.length() == 0;
      String shown = empty ? "Enter nickname" : this.input.toString();
      if (!empty && active && caretVisible()) {
         shown = shown + "|";
      }

      Render2DUtil.text(x + 8.0F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(y + 11.0F, 10.0F), 10.0F, shown)
         .style(UiFontStyle.MEDIUM)
         .color(empty ? Theme.Colors.SECONDARY_DARK : Theme.Colors.TEXT)
         .draw();
      float plusX = x + fieldW + 6.0F;
      boolean valid = this.canAdd();
      boolean hovered = MainMenuScreen.insideRect(mouseX, mouseY, plusX, y, plusW, 22.0F);
      float hover = this.anim.value("plus", hovered ? 1.0F : 0.0F, 16.0F);
      int fill = valid ? ColorUtil.lerp(ColorUtil.withAlpha(CHECK_BLUE, 210), CHECK_BLUE, hover) : FIELD;
      Render2DUtil.rect(plusX, y, plusW, 22.0F).color(fill).radius(6.0F).border(1.0F, valid ? 0 : Theme.Colors.DIVIDER_SUBTLE).draw();
      Render2DUtil.texture(plusX + (plusW - 11.0F) * 0.5F, y + 5.5F, 11.0F, 11.0F, Textures.Icons.PLUS)
         .color(valid ? Theme.Colors.PRIMARY_BRIGHT : Theme.Colors.SECONDARY_DARK)
         .draw();
   }

   private void renderGenerate(float x, float y, float w, int mouseX, int mouseY) {
      boolean hovered = MainMenuScreen.insideRect(mouseX, mouseY, x, y, w, 20.0F);
      float hover = this.anim.value("generate", hovered ? 1.0F : 0.0F, 16.0F);
      float flash = this.generateFlashNanos == 0L ? 0.0F : MathUtil.clamp(1.0F - (float)(System.nanoTime() - this.generateFlashNanos) / 4.0E8F, 0.0F, 1.0F);
      Render2DUtil.rect(x, y, w, 20.0F)
         .color(ColorUtil.lerp(FIELD, CARD_HOVER, hover))
         .radius(6.0F)
         .border(1.0F, ColorUtil.withAlpha(CHECK_BLUE, 40 + (int)(flash * 120.0F)))
         .draw();
      float iconSize = 12.0F;
      String label = "Generate";
      float labelWidth = MainMenuScreen.widthOf(label, 10.0F, UiFontStyle.MEDIUM);
      float total = iconSize + 6.0F + labelWidth;
      float iconX = x + (w - total) * 0.5F;
      Render2DUtil.texture(iconX, y + (20.0F - iconSize) * 0.5F, iconSize, iconSize, Textures.Icons.SLIDERS)
         .color(ColorUtil.lerp(Theme.Colors.ICON, CHECK_BLUE, Math.max(hover, flash)))
         .draw();
      Render2DUtil.text(iconX + iconSize + 6.0F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(y + 10.0F, 10.0F), 10.0F, label)
         .style(UiFontStyle.MEDIUM)
         .color(Theme.Colors.TEXT)
         .draw();
   }

   private void renderClearAll(float x, float y, float w, int mouseX, int mouseY) {
      boolean empty = AltStore.size() == 0;
      boolean hovered = !empty && MainMenuScreen.insideRect(mouseX, mouseY, x, y, w, 20.0F);
      if (!this.clearPressed) {
         float target = hovered ? 0.06F : 0.0F;
         this.clearProgress = Mth.lerp(this.anim.delta() * 5.0F, this.clearProgress, target);
      } else if (this.clearProgress < 1.0F) {
         float span = Math.max(1.0F, w - 28.0F);
         this.clearProgress = MathUtil.clamp((mouseX - this.clearStartX) / span, 0.0F, 1.0F);
      }

      Render2DUtil.rect(x, y, w, 20.0F)
         .color(FIELD)
         .radius(6.0F)
         .border(1.0F, this.clearProgress > 0.5F ? ColorUtil.withAlpha(Theme.Colors.SYSTEM_RED, 120) : Theme.Colors.DIVIDER_SUBTLE)
         .draw();
      if (this.clearProgress > 0.01F) {
         Render2DUtil.pushScissor(x, y, w, 20.0F);
         Render2DUtil.rect(x, y, w * this.clearProgress, 20.0F).color(ColorUtil.withAlpha(Theme.Colors.SYSTEM_RED, 70)).radius(6.0F).draw();
         Render2DUtil.popScissor();
      }

      float iconSize = 11.0F;
      String label = "Slide to clear all";
      float labelWidth = MainMenuScreen.widthOf(label, 9.0F, UiFontStyle.MEDIUM);
      float total = iconSize + 6.0F + labelWidth;
      float iconX = x + (w - total) * 0.5F;
      int color = empty ? Theme.Colors.SECONDARY_DARK : ColorUtil.lerp(Theme.Colors.SECONDARY, Theme.Colors.SYSTEM_RED, this.clearProgress);
      Render2DUtil.texture(iconX, y + (20.0F - iconSize) * 0.5F, iconSize, iconSize, Textures.Icons.DELETE).color(color).draw();
      Render2DUtil.text(iconX + iconSize + 6.0F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(y + 10.0F, 9.0F), 9.0F, label)
         .style(UiFontStyle.MEDIUM)
         .color(color)
         .draw();
   }

   private void renderSession(float x, float y, float w, float h, int mouseX, int mouseY) {
      Render2DUtil.rect(x, y, w, h).color(ColorUtil.multiplyAlpha(CARD, 0.6F)).radius(8.0F).border(1.0F, Theme.Colors.DIVIDER_SUBTLE).draw();
      Render2DUtil.text(x + 10.0F, UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight()).centeredTextY(y + 13.0F, 10.0F), 10.0F, "Active session")
         .style(UiFontStyle.SEMIBOLD)
         .color(Theme.Colors.SECONDARY)
         .draw();
      String currentName = this.minecraft.getUser().getName();
      Alt match = null;

      for (Alt alt : AltStore.all()) {
         if (alt.name().equalsIgnoreCase(currentName)) {
            match = alt;
            break;
         }
      }

      float rowY = y + 24.0F;
      float headX = x + 10.0F;
      PlayerHead.draw(headX, rowY, 26.0F, this.sessionSkin(match), 5.0F, -1);
      float textX = headX + 26.0F + 8.0F;
      float nameY = rowY + 5.0F;
      Render2DUtil.text(textX, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(nameY + 4.0F, 10.0F), 10.0F, currentName)
         .style(UiFontStyle.MEDIUM)
         .color(Theme.Colors.PRIMARY_BRIGHT)
         .draw();
      this.drawCheck(textX + MainMenuScreen.widthOf(currentName, 10.0F, UiFontStyle.MEDIUM) + 4.0F, nameY + 4.0F - 4.5F, 1.0F);
      String stamp = match != null && match.lastUsedAt() > 0L ? "Last active " + formatStamp(match.lastUsedAt()) : "Current login session";
      Render2DUtil.text(textX, UiFonts.sfPro(UiFontStyle.REGULAR_TRACKED.weight()).centeredTextY(rowY + 26.0F - 4.0F, 8.0F), 8.0F, stamp)
         .style(UiFontStyle.REGULAR_TRACKED)
         .color(Theme.Colors.SECONDARY_DARK)
         .draw();
   }

   private void renderMainArea(float x, float y, float w, float h, int mouseX, int mouseY) {
      float searchW = w - 52.0F - 6.0F;
      this.renderSearch(x, y, searchW, mouseX, mouseY);
      this.renderLengthButton(x + searchW + 6.0F, y, 52.0F, mouseX, mouseY);
      float msButtonH = 26.0F;
      float msSectionH = msButtonH + 6.0F + MsAccountStore.size() * 26.0F;
      float gridY = y + 22.0F + 8.0F;
      float gridBottom = y + h - msSectionH - 6.0F;
      float gridH = gridBottom - gridY;
      this.renderGrid2(x, gridY, w, gridH, mouseX, mouseY);
      this.renderMsSection(x, y + h - msSectionH, w, msSectionH, mouseX, mouseY);
   }

   private void renderSearch(float x, float y, float w, int mouseX, int mouseY) {
      boolean active = this.focus == AltsScreen.FocusField.SEARCH;
      Render2DUtil.rect(x, y, w, 22.0F)
         .color(FIELD)
         .radius(6.0F)
         .border(1.0F, active ? ColorUtil.withAlpha(CHECK_BLUE, 110) : Theme.Colors.DIVIDER_SUBTLE)
         .draw();
      float iconSize = 11.0F;
      Render2DUtil.texture(x + 8.0F, y + (22.0F - iconSize) * 0.5F, iconSize, iconSize, Textures.Icons.SEARCH)
         .color(active ? CHECK_BLUE : Theme.Colors.SECONDARY_DARK)
         .draw();
      boolean empty = this.search.length() == 0;
      String shown = empty ? "Search nickname" : this.search.toString();
      if (!empty && active && caretVisible()) {
         shown = shown + "|";
      }

      Render2DUtil.text(x + 24.0F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(y + 11.0F, 10.0F), 10.0F, shown)
         .style(UiFontStyle.MEDIUM)
         .color(empty ? Theme.Colors.SECONDARY_DARK : Theme.Colors.TEXT)
         .draw();
   }

   private void renderLengthButton(float x, float y, float w, int mouseX, int mouseY) {
      boolean hovered = MainMenuScreen.insideRect(mouseX, mouseY, x, y, w, 22.0F);
      float hover = this.anim.value("length", hovered ? 1.0F : 0.0F, 16.0F);
      float flash = this.lengthFlashNanos == 0L ? 0.0F : MathUtil.clamp(1.0F - (float)(System.nanoTime() - this.lengthFlashNanos) / 6.0E8F, 0.0F, 1.0F);
      Render2DUtil.rect(x, y, w, 22.0F)
         .color(ColorUtil.lerp(FIELD, CARD_HOVER, hover))
         .radius(6.0F)
         .border(1.0F, flash > 0.0F ? ColorUtil.withAlpha(Theme.Colors.SYSTEM_RED, (int)(40.0F + flash * 120.0F)) : Theme.Colors.DIVIDER_SUBTLE)
         .draw();
      float iconSize = 10.0F;
      String label = "Length";
      float labelWidth = MainMenuScreen.widthOf(label, 9.0F, UiFontStyle.MEDIUM);
      float total = iconSize + 4.0F + labelWidth;
      float iconX = x + (w - total) * 0.5F;
      int color = ColorUtil.lerp(Theme.Colors.SECONDARY, Theme.Colors.SYSTEM_RED, Math.max(hover * 0.5F, flash));
      Render2DUtil.texture(iconX, y + (22.0F - iconSize) * 0.5F, iconSize, iconSize, Textures.Icons.DELETE).color(color).draw();
      Render2DUtil.text(iconX + iconSize + 4.0F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(y + 11.0F, 9.0F), 9.0F, label)
         .style(UiFontStyle.MEDIUM)
         .color(color)
         .draw();
   }

   private void renderGrid2(float x, float y, float w, float h, int mouseX, int mouseY) {
      List<Alt> visible = this.filteredAlts();
      int columns = 2;
      float cardW = (w - 6.0F) / columns;
      int rows = (visible.size() + columns - 1) / columns;
      float content = rows * 50.0F - 6.0F;
      this.scrollMax = Math.max(0.0F, content - h);
      this.scroll = MathUtil.clamp(this.scroll, 0.0F, this.scrollMax);
      Alt current = AltStore.current();
      String currentName = this.minecraft.getUser().getName();
      Render2DUtil.pushScissor(x, y, w, h);

      for (int i = 0; i < visible.size(); i++) {
         Alt alt = visible.get(i);
         int column = i % columns;
         int row = i / columns;
         float cardX = x + column * (cardW + 6.0F);
         float cardY = y - this.scroll + row * 50.0F;
         if (!(cardY + 44.0F < y) && !(cardY > y + h)) {
            boolean active = alt == current || alt.name().equalsIgnoreCase(currentName);
            this.renderCard(alt, i, cardX, cardY, cardW, active, mouseX, mouseY, y, h);
         }
      }

      Render2DUtil.popScissor();
      if (this.scrollMax > 0.5F) {
         float trackHeight = h - 8.0F;
         float thumbHeight = Math.max(18.0F, trackHeight * (h / content));
         float progress = this.scroll / this.scrollMax;
         float thumbY = y + 4.0F + (trackHeight - thumbHeight) * progress;
         Render2DUtil.rect(x + w - 3.0F - 2.0F, thumbY, 3.0F, thumbHeight).color(Theme.Colors.OUTLINES_LARGE).radius(1.5F).draw();
      }
   }

   private void renderCard(Alt alt, int index, float x, float y, float w, boolean active, int mouseX, int mouseY, float gridTop, float gridH) {
      boolean inGrid = mouseY >= gridTop && mouseY <= gridTop + gridH;
      boolean hovered = inGrid && MainMenuScreen.insideRect(mouseX, mouseY, x, y, w, 44.0F);
      boolean confirming = alt == this.pendingRemove && System.nanoTime() < this.pendingRemoveUntilNanos;
      if (alt == this.pendingRemove && !confirming) {
         this.pendingRemove = null;
      }

      float hover = this.anim.value("card" + alt.name().toLowerCase(Locale.ROOT), hovered ? 1.0F : 0.0F, 16.0F);
      int fill = confirming ? ColorUtil.withAlpha(Theme.Colors.SYSTEM_RED, 0.14F) : ColorUtil.lerp(CARD, CARD_HOVER, hover);
      Render2DUtil.rect(x, y, w, 44.0F)
         .color(fill)
         .radius(8.0F)
         .border(1.0F, active ? ColorUtil.withAlpha(CHECK_BLUE, 190) : Theme.Colors.DIVIDER_SUBTLE)
         .draw();
      float headX = x + 8.0F;
      float headY = y + 9.0F;
      PlayerHead.draw(headX, headY, 26.0F, alt.skin(), 4.0F, -1);
      float textX = headX + 26.0F + 7.0F;
      float nameCenterY = y + 15.0F;
      Render2DUtil.text(textX, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(nameCenterY, 10.0F), 10.0F, alt.name())
         .style(UiFontStyle.MEDIUM)
         .color(active ? Theme.Colors.PRIMARY_BRIGHT : ColorUtil.lerp(Theme.Colors.TEXT, Theme.Colors.PRIMARY_BRIGHT, hover))
         .draw();
      if (active) {
         this.drawCheck(textX + MainMenuScreen.widthOf(alt.name(), 10.0F, UiFontStyle.MEDIUM) + 4.0F, nameCenterY - 4.5F, 1.0F);
      }

      String meta = confirming ? "click trash again to remove" : formatStamp(alt.lastUsedAt() > 0L ? alt.lastUsedAt() : alt.addedAt());
      Render2DUtil.text(textX, UiFonts.sfPro(UiFontStyle.REGULAR_TRACKED.weight()).centeredTextY(y + 44.0F - 12.0F, 8.0F), 8.0F, meta)
         .style(UiFontStyle.REGULAR_TRACKED)
         .color(confirming ? Theme.Colors.SYSTEM_RED : Theme.Colors.SECONDARY_DARK)
         .draw();
      float iconX = x + w - 8.0F - 10.0F;
      boolean copyHovered = inGrid && MainMenuScreen.insideRect(mouseX, mouseY, iconX - 4.0F, y + 44.0F - 8.0F - 10.0F - 4.0F, 18.0F, 18.0F);
      float copyHover = this.anim.value("copy" + alt.name().toLowerCase(Locale.ROOT), copyHovered ? 1.0F : 0.0F, 16.0F);
      boolean justCopied = alt == this.copiedAlt && System.nanoTime() < this.copiedNanos;
      Render2DUtil.texture(iconX, y + 44.0F - 8.0F - 10.0F, 10.0F, 10.0F, justCopied ? Textures.Icons.USER_ROUND_CHECK : Textures.Icons.COPY)
         .color(justCopied ? CHECK_BLUE : ColorUtil.lerp(Theme.Colors.ICON_GHOST, CHECK_BLUE, copyHover))
         .draw();
      boolean loginHovered = inGrid && MainMenuScreen.insideRect(mouseX, mouseY, iconX - 4.0F, y + 8.0F - 4.0F, 18.0F, 18.0F);
      float loginHover = this.anim.value("login" + alt.name().toLowerCase(Locale.ROOT), loginHovered ? 1.0F : 0.0F, 16.0F);
      Render2DUtil.texture(iconX, y + 8.0F, 10.0F, 10.0F, Textures.Icons.ARROW_UP)
         .color(active ? CHECK_BLUE : ColorUtil.lerp(Theme.Colors.ICON_GHOST, Theme.Colors.PRIMARY_BRIGHT, loginHover))
         .draw();
      if (hover > 0.02F || confirming) {
         boolean trashHovered = inGrid && MainMenuScreen.insideRect(mouseX, mouseY, iconX - 10.0F - 8.0F, y + 8.0F - 4.0F, 18.0F, 18.0F);
         float trashHover = this.anim.value("trash" + alt.name().toLowerCase(Locale.ROOT), trashHovered ? 1.0F : 0.0F, 16.0F);
         int trashColor = ColorUtil.lerp(Theme.Colors.ICON_GHOST, Theme.Colors.SYSTEM_RED, Math.max(trashHover, confirming ? 1.0F : 0.0F));
         Render2DUtil.texture(iconX - 10.0F - 4.0F, y + 8.0F, 10.0F, 10.0F, Textures.Icons.DELETE)
            .color(ColorUtil.multiplyAlpha(trashColor, Math.max(hover, confirming ? 1.0F : 0.0F)))
            .draw();
      }
   }

   private void renderMsSection(float x, float y, float w, float h, int mouseX, int mouseY) {
      List<MicrosoftAuth.MsAccount> accounts = MsAccountStore.all();
      float buttonY = y + h - 26.0F;
      String currentName = this.minecraft.getUser().getName();

      for (int i = 0; i < accounts.size(); i++) {
         MicrosoftAuth.MsAccount account = accounts.get(i);
         float rowY = y + i * 26.0F;
         boolean current = account.username().equalsIgnoreCase(currentName);
         boolean hovered = MainMenuScreen.insideRect(mouseX, mouseY, x, rowY, w, 24.0F);
         float hover = this.anim.value("msRow" + i, hovered ? 1.0F : 0.0F, 16.0F);
         Render2DUtil.rect(x, rowY, w, 24.0F)
            .color(ColorUtil.lerp(CARD, CARD_HOVER, hover))
            .radius(6.0F)
            .border(1.0F, current ? ColorUtil.withAlpha(CHECK_BLUE, 190) : Theme.Colors.DIVIDER_SUBTLE)
            .draw();
         Render2DUtil.text(x + 9.0F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(rowY + 12.0F, 10.0F), 10.0F, account.username())
            .style(UiFontStyle.MEDIUM)
            .color(current ? Theme.Colors.PRIMARY_BRIGHT : Theme.Colors.TEXT)
            .draw();
         if (current) {
            this.drawCheck(x + 9.0F + MainMenuScreen.widthOf(account.username(), 10.0F, UiFontStyle.MEDIUM) + 4.0F, rowY + 12.0F - 4.5F, 1.0F);
         }

         float metaWidth = MainMenuScreen.widthOf("Licensed", 8.0F, UiFontStyle.REGULAR_TRACKED);
         Render2DUtil.text(x + w - 9.0F - metaWidth, UiFonts.sfPro(UiFontStyle.REGULAR_TRACKED.weight()).centeredTextY(rowY + 12.0F, 8.0F), 8.0F, "Licensed")
            .style(UiFontStyle.REGULAR_TRACKED)
            .color(Theme.Colors.SECONDARY_DARK)
            .draw();
      }

      boolean hovered = !this.msInProgress && MainMenuScreen.insideRect(mouseX, mouseY, x, buttonY, w, 24.0F);
      float hover = this.anim.value("msButton", hovered ? 1.0F : 0.0F, 16.0F);
      Render2DUtil.rect(x, buttonY, w, 24.0F)
         .color(ColorUtil.lerp(FIELD, CARD_HOVER, hover))
         .radius(6.0F)
         .border(1.0F, this.msInProgress ? Theme.Colors.DIVIDER_SUBTLE : ColorUtil.withAlpha(CHECK_BLUE, 70))
         .draw();
      String label;
      if (this.msInProgress && this.msCode != null) {
         label = this.msCode + "  |  microsoft.com/link";
      } else if (this.msInProgress) {
         label = this.msStatus == null ? "Microsoft..." : this.msStatus;
      } else {
         label = this.msStatus == null ? "Add Microsoft account" : this.msStatus;
      }

      float labelWidth = MainMenuScreen.widthOf(label, 9.0F, UiFontStyle.MEDIUM);
      Render2DUtil.text(x + (w - labelWidth) * 0.5F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(buttonY + 12.0F, 9.0F), 9.0F, label)
         .style(UiFontStyle.MEDIUM)
         .color(this.msInProgress ? CHECK_BLUE : Theme.Colors.TEXT)
         .draw();
   }

   private void renderHints(int mouseX, int mouseY) {
      float y = this.panelY() + 260.0F + 16.0F + 4.0F;
      boolean undoReady = AltStore.canUndo();
      float undoFlash = this.undoFlashNanos == 0L
         ? 0.0F
         : MathUtil.clamp(1.0F - (float)((System.nanoTime() - this.undoFlashNanos) / 1500000000L) * 1.0F, 0.0F, 1.0F);
      String[] hints = new String[]{"CTRL + V  -  create account from clipboard", "CTRL + Z  -  restore removed accounts"};
      float totalWidth = 0.0F;
      float[] widths = new float[hints.length];

      for (int i = 0; i < hints.length; i++) {
         widths[i] = MainMenuScreen.widthOf(hints[i], 8.0F, UiFontStyle.REGULAR_TRACKED);
         totalWidth += widths[i];
      }

      totalWidth += 24.0F;
      float x = (this.width - totalWidth) * 0.5F;

      for (int i = 0; i < hints.length; i++) {
         int color = Theme.Colors.SECONDARY_DARK;
         if (i == 1 && undoReady) {
            color = ColorUtil.lerp(Theme.Colors.SECONDARY_DARK, CHECK_BLUE, 0.4F + undoFlash * 0.6F);
         }

         Render2DUtil.text(x, UiFonts.sfPro(UiFontStyle.REGULAR_TRACKED.weight()).centeredTextY(y, 8.0F), 8.0F, hints[i])
            .style(UiFontStyle.REGULAR_TRACKED)
            .color(color)
            .draw();
         x += widths[i] + 24.0F;
      }
   }

   private void drawCheck(float x, float y, float alpha) {
      Render2DUtil.rect(x, y, 9.0F, 9.0F).color(ColorUtil.multiplyAlpha(CHECK_BLUE, alpha)).radius(4.5F).draw();
      float cx = x + 4.5F;
      float cy = y + 4.5F;
      int white = ColorUtil.multiplyAlpha(Theme.Colors.PRIMARY_BRIGHT, alpha);
      Render2DUtil.rect(cx - 2.2F, cy - 0.2F, 1.6F, 1.6F).color(white).draw();
      Render2DUtil.rect(cx - 1.2F, cy + 0.8F, 1.6F, 1.6F).color(white).draw();
      Render2DUtil.rect(cx - 0.2F, cy - 0.2F, 1.6F, 1.6F).color(white).draw();
      Render2DUtil.rect(cx + 0.8F, cy - 1.2F, 1.6F, 1.6F).color(white).draw();
      Render2DUtil.rect(cx + 1.8F, cy - 2.2F, 1.6F, 1.6F).color(white).draw();
   }

   public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
      float mouseX = (float)mouseButtonEvent.x();
      float mouseY = (float)mouseButtonEvent.y();
      return mouseButtonEvent.button() == 0 ? this.leftClick(mouseX, mouseY) : super.mouseClicked(mouseButtonEvent, doubleClick);
   }

   private boolean leftClick(float mouseX, float mouseY) {
      float panelX = this.panelX();
      float panelY = this.panelY();
      float closeX = this.width - 18.0F - 10.0F;
      float closeY = panelY - 50.0F + 24.0F - 5.0F;
      if (MainMenuScreen.insideRect(mouseX, mouseY, closeX - 4.0F, closeY - 4.0F, 18.0F, 18.0F)) {
         MainMenuScreen.playClick(this.minecraft);
         this.onClose();
         return true;
      }

      float sideX = panelX + 14.0F;
      float sideW = 158.0F;
      float mainX = sideX + sideW + 10.0F;
      float mainW = panelX + 472.0F - 14.0F - mainX;
      float contentY = panelY + 14.0F;
      float contentH = 232.0F;
      float innerX = sideX + 10.0F;
      float innerW = sideW - 20.0F;
      float inputY = contentY + 26.0F;
      float plusW = 22.0F;
      float fieldW = innerW - plusW - 6.0F;
      if (MainMenuScreen.insideRect(mouseX, mouseY, innerX, inputY, fieldW, 22.0F)) {
         this.focus = AltsScreen.FocusField.INPUT;
         return true;
      }

      if (MainMenuScreen.insideRect(mouseX, mouseY, innerX + fieldW + 6.0F, inputY, plusW, 22.0F)) {
         this.addTyped();
         return true;
      }

      float genY = inputY + 22.0F + 6.0F;
      if (MainMenuScreen.insideRect(mouseX, mouseY, innerX, genY, innerW, 20.0F)) {
         this.rollNickname();
         return true;
      }

      float clearY = genY + 20.0F + 6.0F;
      if (AltStore.size() > 0 && MainMenuScreen.insideRect(mouseX, mouseY, innerX, clearY, innerW, 20.0F)) {
         this.clearPressed = true;
         this.clearStartX = mouseX;
         return true;
      }

      float searchW = mainW - 52.0F - 6.0F;
      if (MainMenuScreen.insideRect(mouseX, mouseY, mainX, contentY, searchW, 22.0F)) {
         this.focus = AltsScreen.FocusField.SEARCH;
         return true;
      }

      if (MainMenuScreen.insideRect(mouseX, mouseY, mainX + searchW + 6.0F, contentY, 52.0F, 22.0F)) {
         this.purgeByLength();
         return true;
      }

      float msSectionH = 32.0F + MsAccountStore.size() * 26.0F;
      float gridY = contentY + 22.0F + 8.0F;
      float gridH = contentY + contentH - msSectionH - 6.0F - gridY;
      if (mouseY >= gridY && mouseY <= gridY + gridH && mouseX >= mainX && mouseX <= mainX + mainW) {
         return this.gridClick(mouseX, mouseY, mainX, gridY, mainW);
      }

      float msTop = contentY + contentH - msSectionH;
      List<MicrosoftAuth.MsAccount> accounts = MsAccountStore.all();
      if (mouseY >= msTop && mouseY <= msTop + accounts.size() * 26.0F && mouseX >= mainX && mouseX <= mainX + mainW) {
         int index = (int)Math.floor((mouseY - msTop) / 26.0F);
         if (index >= 0 && index < accounts.size()) {
            this.loginMs(accounts.get(index));
         }

         return true;
      } else {
         float msButtonY = contentY + contentH - 26.0F;
         if (!this.msInProgress && MainMenuScreen.insideRect(mouseX, mouseY, mainX, msButtonY, mainW, 24.0F)) {
            this.startMicrosoftLogin();
            return true;
         } else {
            this.focus = AltsScreen.FocusField.NONE;
            return true;
         }
      }
   }

   private boolean gridClick(float mouseX, float mouseY, float x, float y, float w) {
      List<Alt> visible = this.filteredAlts();
      int columns = 2;
      float cardW = (w - 6.0F) / columns;
      int column = (int)((mouseX - x) / (cardW + 6.0F));
      int row = (int)((mouseY - y + this.scroll) / 50.0F);
      if (column >= 0 && column < columns && row >= 0) {
         float localX = mouseX - x - column * (cardW + 6.0F);
         if (localX > cardW) {
            return true;
         } else {
            int index = row * columns + column;
            if (index >= visible.size()) {
               return true;
            } else {
               Alt alt = visible.get(index);
               float cardX = x + column * (cardW + 6.0F);
               float cardY = y - this.scroll + row * 50.0F;
               float iconX = cardX + cardW - 8.0F - 10.0F;
               if (MainMenuScreen.insideRect(mouseX, mouseY, iconX - 10.0F - 8.0F, cardY + 8.0F - 4.0F, 18.0F, 18.0F)) {
                  this.requestRemove(alt);
                  return true;
               } else if (MainMenuScreen.insideRect(mouseX, mouseY, iconX - 4.0F, cardY + 44.0F - 8.0F - 10.0F - 4.0F, 18.0F, 18.0F)) {
                  this.minecraft.keyboardHandler.setClipboard(alt.name());
                  this.copiedAlt = alt;
                  this.copiedNanos = System.nanoTime() + 1500000000L;
                  MainMenuScreen.playClick(this.minecraft);
                  return true;
               } else if (MainMenuScreen.insideRect(mouseX, mouseY, iconX - 4.0F, cardY + 8.0F - 4.0F, 18.0F, 18.0F)) {
                  this.login(alt);
                  return true;
               } else {
                  return true;
               }
            }
         }
      } else {
         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
      if (mouseButtonEvent.button() == 0 && this.clearPressed) {
         this.clearPressed = false;
         if (this.clearProgress >= 1.0F && AltStore.size() > 0) {
            AltStore.clearAll();
            this.pendingRemove = null;
            this.scroll = 0.0F;
            MainMenuScreen.playClick(this.minecraft);
         }

         this.clearProgress = 0.0F;
         return true;
      } else {
         return super.mouseReleased(mouseButtonEvent);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      this.scroll = MathUtil.clamp(this.scroll - (float)scrollY * 33.0F, 0.0F, this.scrollMax);
      return true;
   }

   public boolean keyPressed(KeyEvent keyEvent) {
      switch (keyEvent.key()) {
         case 256:
            this.onClose();
            return true;
         case 257:
         case 335:
            if (this.focus == AltsScreen.FocusField.INPUT && this.input.length() > 0) {
               this.addTyped();
            }

            return true;
         case 258:
            this.focus = this.focus == AltsScreen.FocusField.INPUT ? AltsScreen.FocusField.SEARCH : AltsScreen.FocusField.INPUT;
            return true;
         case 259:
            StringBuilder target = this.focusedField();
            if (target != null && target.length() > 0) {
               target.deleteCharAt(target.length() - 1);
            }

            return true;
         default:
            if (keyEvent.hasControlDown()) {
               if (keyEvent.key() == 86) {
                  this.pasteClipboard();
                  return true;
               }

               if (keyEvent.key() == 90) {
                  this.undoRemove();
                  return true;
               }

               if (keyEvent.key() == 65) {
                  return true;
               }
            }

            return super.keyPressed(keyEvent);
      }
   }

   public boolean charTyped(CharacterEvent characterEvent) {
      int codePoint = characterEvent.codepoint();
      StringBuilder target = this.focusedField();
      if (target != null && target.length() < 16 && isNameChar(codePoint)) {
         target.append((char)codePoint);
         if (target == this.search) {
            this.scroll = 0.0F;
         }

         return true;
      } else {
         return true;
      }
   }

   private void rollNickname() {
      this.focus = AltsScreen.FocusField.INPUT;
      this.input.setLength(0);
      this.input.append(RandomNick.next(AltStore.names()));
      this.generateFlashNanos = System.nanoTime();
      MainMenuScreen.playClick(this.minecraft);
   }

   private void addTyped() {
      if (this.canAdd()) {
         Alt added = AltStore.add(this.input.toString());
         if (added != null) {
            this.input.setLength(0);
            this.scroll = 0.0F;
            MainMenuScreen.playClick(this.minecraft);
         }
      }
   }

   private void pasteClipboard() {
      String clip = this.minecraft.keyboardHandler.getClipboard();
      if (clip != null) {
         StringBuilder cleaned = new StringBuilder();

         for (int i = 0; i < clip.length() && cleaned.length() < 16; i++) {
            char c = clip.charAt(i);
            if (isNameChar(c)) {
               cleaned.append(c);
            } else if (cleaned.length() > 0) {
               break;
            }
         }

         if (cleaned.length() != 0) {
            this.focus = AltsScreen.FocusField.INPUT;
            this.input.setLength(0);
            this.input.append(cleaned);
            if (this.canAdd()) {
               this.addTyped();
            }
         }
      }
   }

   private void undoRemove() {
      if (AltStore.canUndo()) {
         AltStore.undoRemove();
         this.undoFlashNanos = System.nanoTime();
         MainMenuScreen.playClick(this.minecraft);
      }
   }

   private void purgeByLength() {
      int cutoff = this.lengthConfirm != null ? this.lengthConfirm.name().length() : 12;
      this.lengthConfirm = null;
      List<Alt> doomed = new ArrayList<>();

      for (Alt alt : AltStore.all()) {
         if (alt.name().length() > cutoff) {
            doomed.add(alt);
         }
      }

      if (doomed.isEmpty()) {
         this.lengthFlashNanos = System.nanoTime();
      } else {
         for (Alt alt : doomed) {
            AltStore.remove(alt);
         }

         this.lengthFlashNanos = System.nanoTime();
         this.scroll = 0.0F;
         MainMenuScreen.playClick(this.minecraft);
      }
   }

   private void login(Alt alt) {
      AltStore.login(alt);
      MainMenuScreen.playClick(this.minecraft);
   }

   private void requestRemove(Alt alt) {
      if (this.pendingRemove == alt && System.nanoTime() < this.pendingRemoveUntilNanos) {
         AltStore.remove(alt);
         this.pendingRemove = null;
         MainMenuScreen.playClick(this.minecraft);
      } else {
         this.pendingRemove = alt;
         this.pendingRemoveUntilNanos = System.nanoTime() + 3000000000L;
      }
   }

   private void startMicrosoftLogin() {
      this.msInProgress = true;
      this.msCode = null;
      this.msStatus = "Requesting code...";
      MainMenuScreen.playClick(this.minecraft);
      this.microsoftAuth
         .requestDeviceCode(
            code -> this.minecraft
               .execute(
                  () -> {
                     this.msCode = code.userCode();
                     this.msStatus = null;
                     this.microsoftAuth
                        .login(
                           code.deviceCode(),
                           account -> this.minecraft.execute(() -> this.completeMicrosoft(account)),
                           error -> this.minecraft.execute(() -> this.failMicrosoft(error))
                        );
                  }
               ),
            error -> this.minecraft.execute(() -> this.failMicrosoft(error))
         );
   }

   private void loginMs(MicrosoftAuth.MsAccount account) {
      if (!this.msInProgress) {
         if (account.refreshToken() != null) {
            this.msInProgress = true;
            this.msCode = null;
            this.msStatus = "Refreshing...";
            this.microsoftAuth
               .refresh(
                  account.refreshToken(), refreshed -> this.minecraft.execute(() -> this.completeMicrosoft(refreshed)), error -> this.minecraft.execute(() -> {
                     this.msInProgress = false;
                     this.msStatus = "Refresh failed - logging in with stored token";
                     MsAccountStore.login(account);
                  })
               );
         } else {
            MsAccountStore.login(account);
         }

         MainMenuScreen.playClick(this.minecraft);
      }
   }

   private void completeMicrosoft(MicrosoftAuth.MsAccount account) {
      this.msInProgress = false;
      this.msCode = null;
      this.msStatus = null;
      MsAccountStore.login(account);
      MainMenuScreen.playClick(this.minecraft);
   }

   private void failMicrosoft(String error) {
      this.msInProgress = false;
      this.msCode = null;
      this.msStatus = error;
   }

   private boolean canAdd() {
      String typed = this.input.toString();
      return Alt.isValidName(typed) && !AltStore.contains(typed);
   }

   private List<Alt> filteredAlts() {
      this.filtered.clear();
      String query = this.search.toString().toLowerCase(Locale.ROOT);

      for (Alt alt : AltStore.all()) {
         if (query.isEmpty() || alt.name().toLowerCase(Locale.ROOT).contains(query)) {
            this.filtered.add(alt);
         }
      }

      return this.filtered;
   }

   private StringBuilder focusedField() {
      return switch (this.focus) {
         case INPUT -> this.input;
         case SEARCH -> this.search;
         default -> null;
      };
   }

   private Identifier sessionSkin(Alt match) {
      return match != null ? match.skin() : DefaultPlayerSkin.get(Alt.offlineUuid(this.minecraft.getUser().getName())).body().texturePath();
   }

   private static String formatStamp(long millis) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(STAMP);
   }

   private static boolean isNameChar(int codePoint) {
      return codePoint >= 97 && codePoint <= 122 || codePoint >= 65 && codePoint <= 90 || codePoint >= 48 && codePoint <= 57 || codePoint == 95;
   }

   private static boolean caretVisible() {
      return System.currentTimeMillis() / 500L % 2L == 0L;
   }

   private float panelX() {
      return (this.width - 472.0F) * 0.5F;
   }

   private float panelY() {
      return Math.max(54.0F, (this.height - 260.0F) * 0.5F + 6.0F);
   }

   private enum FocusField {
      NONE,
      INPUT,
      SEARCH;
   }
}
