package win.furryhook.menu.screen;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import win.furryhook.menu.core.MenuBackgroundFx;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class MainMenuScreen {
   private static final int BACKGROUND = ColorUtil.rgb(21, 21, 22);
   private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath("furryhook", "fonts/background.png");
   private static final Identifier BUTTON_TEXTURE = Identifier.fromNamespaceAndPath("furryhook", "fonts/button.png");
   private static final String[] LABELS = new String[]{"Singleplayer", "Multiplayer", "Alt Manager", "Settings"};
   private static final float CLOSE_SIZE = 14.0F;
   private static final float CLOSE_MARGIN = 14.0F;
   private static final float BLOCK_WIDTH = 220.0F;
   private static final float BLOCK_PADDING = 14.0F;
   private static final float TITLE_SIZE = 18.0F;
   private static final float HEADER_GAP = 15.0F;
   private static final float ROW_HEIGHT = 26.0F;
   private static final float ROW_GAP = 4.0F;
   private static final float ROW_RADIUS = 8.0F;
   private static final float ROW_LABEL_SIZE = 11.0F;
   private static final float SOCIAL_ICON = 15.0F;
   private static final float SOCIAL_GAP = 12.0F;
   private static final float SOCIAL_MARGIN = 16.0F;
   private static final float META_MARGIN = 14.0F;
   private static final float META_SIZE = 8.0F;
   private static final float APPEAR_SECONDS = 0.26F;
   private static final float APPEAR_RISE = 8.0F;
   private static final float SPEED_HOVER = 16.0F;
   private static final String TITLE = "Furry Client";
   private static final String TELEGRAM_URL = "https://t.me/furryclient";
   private static final String DISCORD_URL = "https://discord.gg/8rsMKurMRE";
   private static final UiAnimator ANIM = new UiAnimator();
   private static long openedAtNanos;

   private MainMenuScreen() {
   }

   public static void onOpen() {
      openedAtNanos = System.nanoTime();
      ANIM.reset();
   }

   public static void render(Minecraft minecraft, int width, int height, int mouseX, int mouseY) {
      ANIM.tick();
      float appear = openedAtNanos == 0L ? 1.0F : MathUtil.clamp((float)(System.nanoTime() - openedAtNanos) / 1.0E9F / 0.26F, 0.0F, 1.0F);
      float eased = UiAnimator.easeOutCubic(appear);
      float rise = (1.0F - eased) * 8.0F;
      Render2DUtil.rect(0.0F, 0.0F, width, height).color(BACKGROUND).draw();
      Render2DUtil.texture(0.0F, 0.0F, width, height, BACKGROUND_TEXTURE).color(ColorUtil.multiplyAlpha(-1, eased)).draw();
      MenuBackgroundFx.render(width, height, ANIM.delta(), eased, 1.0F, true, true);
      float blockX = blockX(width);
      float blockY = blockY(height) + rise;
      int accent = Theme.getAccent();
      float titleCenterY = blockY + 14.0F + 9.0F;
      float titleWidth = widthOf("Furry Client", 18.0F, UiFontStyle.SEMIBOLD);
      Render2DUtil.text(
            blockX + (220.0F - titleWidth) * 0.5F, UiFonts.sfPro(UiFontStyle.SEMIBOLD.weight()).centeredTextY(titleCenterY, 18.0F), 18.0F, "Furry Client"
         )
         .style(UiFontStyle.SEMIBOLD)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.PRIMARY_BRIGHT, eased))
         .draw();
      float[] rect = new float[4];

      for (int i = 0; i < LABELS.length; i++) {
         rowRect(i, width, height, rect);
         rect[1] += rise;
         boolean hovered = appear > 0.6F && insideRect(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
         renderRow(i, rect, hovered, eased, accent);
      }

      renderSocials(width, mouseX, mouseY, eased, accent, rise);
      renderMeta(minecraft, height, eased, rise);
      renderClose(mouseX, mouseY, eased);
   }

   private static void renderRow(int index, float[] rect, boolean hovered, float eased, int accent) {
      float hover = ANIM.value("row" + index, hovered ? 1.0F : 0.0F, 16.0F);
      boolean quit = index == LABELS.length - 1;
      int markerColor = quit ? Theme.Colors.SYSTEM_RED : accent;
      float x = rect[0];
      float y = rect[1];
      float width = rect[2];
      float height = rect[3];
      Render2DUtil.texture(x, y, width, height, BUTTON_TEXTURE).color(ColorUtil.multiplyAlpha(-1, eased)).draw();
      int border = ColorUtil.lerp(Theme.Colors.OUTLINES_SMALL, ColorUtil.withAlpha(markerColor, 110), hover);
      Render2DUtil.rect(x, y, width, height).color(0).radius(8.0F).border(1.0F, ColorUtil.multiplyAlpha(border, eased)).draw();
      String label = LABELS[index];
      float labelWidth = widthOf(label, 11.0F, UiFontStyle.MEDIUM);
      int idleColor = quit ? Theme.Colors.SECONDARY_DARK : Theme.Colors.SECONDARY;
      int labelColor = ColorUtil.lerp(idleColor, quit ? Theme.Colors.SYSTEM_RED : Theme.Colors.PRIMARY_BRIGHT, hover);
      Render2DUtil.text(x + (width - labelWidth) * 0.5F, UiFonts.sfPro(UiFontStyle.MEDIUM.weight()).centeredTextY(y + height * 0.5F, 11.0F), 11.0F, label)
         .style(UiFontStyle.MEDIUM)
         .color(ColorUtil.multiplyAlpha(labelColor, eased))
         .draw();
   }

   private static void renderSocials(int width, int mouseX, int mouseY, float eased, int accent, float rise) {
      float y = 16.0F - rise;

      for (int i = 0; i < 2; i++) {
         float x = socialX(width, i);
         boolean hovered = insideRect(mouseX, mouseY, x, y, 15.0F, 15.0F);
         float hover = ANIM.value("social" + i, hovered ? 1.0F : 0.0F, 16.0F);
         Identifier icon = i == 0 ? Textures.Icons.TELEGRAM : Textures.Icons.DISCORD;
         Render2DUtil.texture(x, y, 15.0F, 15.0F, icon).color(ColorUtil.multiplyAlpha(ColorUtil.lerp(Theme.Colors.ICON, accent, hover), eased)).draw();
      }
   }

   private static void renderClose(int mouseX, int mouseY, float eased) {
      boolean hovered = insideRect(mouseX, mouseY, 14.0F, 14.0F, 14.0F, 14.0F);
      float hover = ANIM.value("close", hovered ? 1.0F : 0.0F, 16.0F);
      int color = ColorUtil.lerp(Theme.Colors.SECONDARY, Theme.Colors.SYSTEM_RED, hover);
      Render2DUtil.texture(14.0F, 14.0F, 14.0F, 14.0F, Textures.Icons.X).color(ColorUtil.multiplyAlpha(color, eased)).draw();
   }

   private static void renderMeta(Minecraft minecraft, int height, float eased, float rise) {
      String meta = "furryhook.win   " + minecraft.getLaunchedVersion();
      Render2DUtil.text(14.0F, UiFonts.sfPro(UiFontStyle.REGULAR_TRACKED.weight()).centeredTextY(height - 14.0F + rise, 8.0F), 8.0F, meta)
         .style(UiFontStyle.REGULAR_TRACKED)
         .color(ColorUtil.multiplyAlpha(Theme.Colors.SECONDARY_DARK, eased))
         .draw();
   }

   public static boolean mouseClicked(Screen parent, Minecraft minecraft, double mouseX, double mouseY, int button) {
      if (button != 0) {
         return false;
      }

      int width = parent.width;
      int height = parent.height;
      float[] rect = new float[4];

      for (int i = 0; i < LABELS.length; i++) {
         rowRect(i, width, height, rect);
         if (insideRect((float)mouseX, (float)mouseY, rect[0], rect[1], rect[2], rect[3])) {
            playClick(minecraft);
            activate(parent, minecraft, i);
            return true;
         }
      }

      for (int i = 0; i < 2; i++) {
         if (insideRect((float)mouseX, (float)mouseY, socialX(width, i), 16.0F, 15.0F, 15.0F)) {
            playClick(minecraft);
            openLink(i == 0 ? "https://t.me/furryclient" : "https://discord.gg/8rsMKurMRE");
            return true;
         }
      }

      if (insideRect((float)mouseX, (float)mouseY, 14.0F, 14.0F, 14.0F, 14.0F)) {
         playClick(minecraft);
         minecraft.stop();
         return true;
      } else {
         return false;
      }
   }

   private static void activate(Screen parent, Minecraft minecraft, int index) {
      switch (index) {
         case 0:
            minecraft.setScreenAndShow(new SelectWorldScreen(parent));
            break;
         case 1:
            minecraft.setScreenAndShow(new JoinMultiplayerScreen(parent));
            break;
         case 2:
            minecraft.setScreenAndShow(new AltsScreen(parent));
            break;
         default:
            minecraft.setScreenAndShow(new OptionsScreen(parent, minecraft.options, false));
      }
   }

   static void playClick(Minecraft minecraft) {
      minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   private static void openLink(String url) {
      try {
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
         }
      } catch (Exception var2) {
      }
   }

   private static void rowRect(int index, int screenWidth, int screenHeight, float[] out) {
      float blockX = blockX(screenWidth);
      float blockY = blockY(screenHeight);
      float inner = 192.0F;
      float rowsTop = blockY + 14.0F + 18.0F + 15.0F;
      out[0] = blockX + 14.0F;
      out[1] = rowsTop + index * 30.0F;
      out[2] = inner;
      out[3] = 26.0F;
   }

   private static float blockHeight() {
      float rows = LABELS.length * 30.0F - 4.0F;
      return 61.0F + rows;
   }

   private static float blockX(int screenWidth) {
      return (screenWidth - 220.0F) * 0.5F;
   }

   private static float blockY(int screenHeight) {
      return Math.max(43.0F, (screenHeight - blockHeight()) * 0.5F - screenHeight * 0.02F);
   }

   private static float socialX(int width, int index) {
      float rightmost = width - 16.0F - 15.0F;
      return rightmost - (1 - index) * 27.0F;
   }

   static float widthOf(String text, float size, UiFontStyle style) {
      return UiFonts.sfPro(style.weight()).measureWidth(text, size, size * style.letterSpacingEm());
   }

   static boolean insideRect(float pointX, float pointY, float x, float y, float width, float height) {
      return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
   }
}
