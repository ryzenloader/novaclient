package win.furryhook.menu.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import win.furryhook.event.events.screen.ScreenMouseButtonEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BindSetting;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ButtonSetting;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.feature.setting.Setting;
import win.furryhook.feature.setting.TextSetting;
import win.furryhook.menu.i18n.UiLanguage;
import win.furryhook.menu.screen.Motion;
import win.furryhook.utils.ClientSounds;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.DiscordPresence;
import win.furryhook.utils.math.MathUtil;
import win.furryhook.utils.render.CategoryIconGlyphs;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.TextAlign;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;

public final class MenuOverlay {
   private static final float PANEL_WIDTH = 1052.0F;
   private static final float PANEL_HEIGHT = 663.0F;
   private static final float REFERENCE_SCREEN_WIDTH = 2560.0F;
   private static final float REFERENCE_SCREEN_HEIGHT = 1440.0F;
   private static final float PANEL_RADIUS = 10.0F;
   private static final float PANEL_ALPHA = 0.95F;
   private static final float PANEL_BLUR_ALPHA = 0.98F;
   private static final float PANEL_VIGNETTE_ALPHA = 0.3F;
   private static final float BLUR_RADIUS = 34.0F;
   private static final int MENU_PANEL = ColorUtil.rgb(8, 9, 11);
   private static final float SIDEBAR_WIDTH = 195.0F;
   private static final float DIVIDER_THICKNESS = 1.0F;
   private static final float CATEGORY_CHIP_HEIGHT = 38.0F;
   private static final float CATEGORY_CHIP_GAP = 8.0F;
   private static final float CATEGORY_CHIP_PADDING_X = 14.0F;
   private static final float CATEGORY_CHIP_ICON = 15.0F;
   private static final float CATEGORY_CHIP_ICON_TEXT_GAP = 10.0F;
   private static final float CATEGORY_CHIP_LABEL_SIZE = 15.0F;
   private static final float CATEGORY_CHIP_RADIUS = 10.0F;
   private static final float CATEGORY_DIVIDER_GAP_TOP = 10.0F;
   private static final float CATEGORY_DIVIDER_GAP_BOTTOM = 12.0F;
   private static final float SEARCH_WIDTH = 240.0F;
   private static final float SEARCH_HEIGHT = 36.0F;
   private static final float SEARCH_RADIUS = 10.0F;
   private static final float SEARCH_PADDING_X = 12.0F;
   private static final float SEARCH_ICON_SIZE = 14.0F;
   private static final float SEARCH_LABEL_SIZE = 14.0F;
   private static final float SEARCH_BORDER_THICKNESS = 1.0F;
   private static final float SEARCH_BORDER_ALPHA = 0.5F;
   private static final float SEARCH_SUGGEST_GAP = 6.0F;
   private static final float SEARCH_SUGGEST_ROW_HEIGHT = 30.0F;
   private static final float SEARCH_SUGGEST_RADIUS = 10.0F;
   private static final int SEARCH_SUGGEST_MAX = 6;
   private static final long HIGHLIGHT_DURATION_NANOS = 3000000000L;
   private static final float HIGHLIGHT_FADE_SECONDS = 0.6F;
   private static final float CONTENT_PADDING = 20.0F;
   private static final int MODULE_COLUMNS = 2;
   private static final float MODULE_CARD_HEIGHT = 42.0F;
   private static final float MODULE_CARD_GAP_X = 14.0F;
   private static final float MODULE_CARD_GAP_Y = 14.0F;
   private static final float MODULE_CARD_RADIUS = 11.0F;
   private static final float MODULE_CARD_PADDING_X = 14.0F;
   private static final float MODULE_LABEL_SIZE = 14.0F;
   private static final float MODULE_CARD_BORDER_THICKNESS = 0.5F;
   private static final float MODULE_CARD_BORDER_ALPHA = 0.32F;
   private static final float SCROLL_STEP = 46.0F;
   private static final float SCROLLBAR_WIDTH = 4.0F;
   private static final float SCROLL_OVERSHOOT = 64.0F;
   private static final float TOGGLE_WIDTH = 40.0F;
   private static final float TOGGLE_HEIGHT = 22.0F;
   private static final float TOGGLE_KNOB_PADDING = 2.0F;
   private static final int TOGGLE_OFF_COLOR = -12303028;
   private static final float SETTINGS_GAP = 18.0F;
   private static final float SETTINGS_BLOCK_PADDING = 12.0F;
   private static final float SETTINGS_PADDING = 16.0F;
   private static final float SETTINGS_RADIUS = 12.0F;
   private static final float SETTINGS_TITLE_SIZE = 18.0F;
   private static final float SETTINGS_SUBTITLE_SIZE = 12.0F;
   private static final float SETTINGS_TITLE_GAP = 4.0F;
   private static final float SETTINGS_DIVIDER_GAP_TOP = 11.0F;
   private static final float SETTINGS_DIVIDER_GAP_BOTTOM = 10.0F;
   private static final float SETTINGS_ROW_HEIGHT = 27.0F;
   private static final float SETTINGS_SLIDER_ROW_HEIGHT = 33.0F;
   private static final float SETTINGS_ROW_GAP = 7.0F;
   private static final float SETTINGS_LABEL_SIZE = 14.0F;
   private static final float SETTINGS_VALUE_SIZE = 12.0F;
   private static final float SETTINGS_PILL_HEIGHT = 23.0F;
   private static final float SETTINGS_PILL_MIN_WIDTH = 82.0F;
   private static final float SETTINGS_PILL_PADDING_X = 10.0F;
   private static final float SETTINGS_PILL_RADIUS = 7.0F;
   private static final float SETTINGS_PILL_GAP = 10.0F;
   private static final float SETTINGS_WRAP_EXTRA = 25.0F;
   private static final float SETTINGS_CHIP_HEIGHT = 24.0F;
   private static final float SETTINGS_CHIP_PADDING_X = 12.0F;
   private static final float SETTINGS_CHIP_GAP_X = 6.0F;
   private static final float SETTINGS_CHIP_GAP_Y = 8.0F;
   private static final float SETTINGS_CHIP_LABEL_HEIGHT = 18.0F;
   private static final float SETTINGS_TRACK_HEIGHT = 4.0F;
   private static final float SETTINGS_KNOB_DIAMETER = 11.0F;
   private static final float SETTINGS_SWATCH_WIDTH = 34.0F;
   private static final float SETTINGS_SWATCH_HEIGHT = 20.0F;
   private static final float SETTINGS_OPTION_ROW_HEIGHT = 24.0F;
   private static final float SETTINGS_BUTTON_PADDING_X = 12.0F;
   private static final float SETTINGS_SLIDE_IN = 26.0F;
   private static final float ACCOUNT_MARGIN = 12.0F;
   private static final float ACCOUNT_HEIGHT = 46.0F;
   private static final float ACCOUNT_AVATAR = 34.0F;
   private static final float ACCOUNT_GEAR = 17.0F;
   private static final float ACCOUNT_GAP = 10.0F;
   private static final float ACCOUNT_LABEL_SIZE = 10.0F;
   private static final float ACCOUNT_HANDLE_SIZE = 13.0F;
   private static final String ACCOUNT_NAME = "Your Account";
   private static final String ACCOUNT_HANDLE = "t.me/i284e";
   private static final float CLIENT_WIDTH = 300.0F;
   private static final float CLIENT_SUBTITLE_WIDTH = 260.0F;
   private static final float COLOR_PICKER_PADDING = 8.0F;
   private static final float COLOR_PICKER_SV_SIZE = 150.0F;
   private static final float COLOR_PICKER_HUE_HEIGHT = 12.0F;
   private static final float COLOR_PICKER_GAP = 10.0F;
   private static final float COLOR_PICKER_HANDLE = 5.0F;
   private static final int[] HUE_STOPS = new int[]{
      ColorUtil.rgb(255, 0, 0),
      ColorUtil.rgb(255, 255, 0),
      ColorUtil.rgb(0, 255, 0),
      ColorUtil.rgb(0, 255, 255),
      ColorUtil.rgb(0, 0, 255),
      ColorUtil.rgb(255, 0, 255),
      ColorUtil.rgb(255, 0, 0)
   };
   private static final int CATEGORY_BASE = -12829629;
   private static final float CATEGORY_UNSELECTED_ALPHA = 0.6F;
   private static final float CATEGORY_SELECTED_BG_ALPHA = 0.3F;
   private static final float HOVER_FILL_ALPHA = 0.16F;
   private static final float BACKGROUND_DIM_ALPHA = 0.68F;
   private static final int TEXT_PRIMARY = Theme.Colors.TEXT_TITLE;
   private static final int TEXT_ROW = Theme.Colors.TEXT_TEXT;
   private static final int TEXT_MUTED = Theme.Colors.SECONDARY;
   private static final int HAIRLINE = ColorUtil.withAlpha(-1, 0.09F);
   private static final int SEPARATOR = ColorUtil.withAlpha(-1, 0.055F);
   private static final float[] SPEED_TOGGLE = Motion.TOGGLE;
   private static final float[] SPEED_HOVER = Motion.HOVER;
   private static final float[] SPEED_COLOR = Motion.PILL;
   private static final float[] SPEED_APPEAR = Motion.PANEL;
   private static final float[] SPEED_FLYOUT = Motion.PILL;
   private static final float[] SPEED_SCROLL = Motion.SCROLL;
   private static final float[] SLIDER_GRAB = new float[]{900.0F, 60.0F};
   private static final float[] SPEED_OPEN = Motion.PANEL;
   private static final float[] SPEED_CLOSE = new float[]{420.0F, 38.0F};
   private static final float SCROLL_WHEEL_KICK = 9.0F;
   private static final float SCROLL_FRICTION = 9.0F;
   private static final float SCROLL_EDGE_BRAKE = 30.0F;
   private static final float SCROLL_VELOCITY_EPSILON = 4.0F;
   private static final float STAGGER_DELAY_S = 0.03F;
   private static final float STAGGER_DURATION_S = 0.42F;
   private static final float DEAL_RISE = 22.0F;
   private static final float ROW_STAGGER_DELAY_S = 0.022F;
   private static final float ROW_STAGGER_DURATION_S = 0.3F;
   private static final float PRESS_SCALE = 0.97F;
   private static final float[] SPEED_PRESS = Motion.PRESS;
   private static final float RIPPLE_SECONDS = 0.5F;
   private static final float RIPPLE_MAX = 14.0F;
   private static final FeatureCategory[][] SECTION_CATEGORIES = new FeatureCategory[][]{
      {FeatureCategory.COMBAT, FeatureCategory.MOVEMENT},
      {FeatureCategory.VISUAL, FeatureCategory.PLAYER, FeatureCategory.MISC},
      {FeatureCategory.COSMETICS, FeatureCategory.SCRIPTS}
   };
   private static final String SUBCAT_ALL = "All";
   private static final float SUBCAT_ROW_HEIGHT = 30.0F;
   private static final float SUBCAT_ROW_GAP = 4.0F;
   private static final float SUBCAT_ROW_PADDING_X = 14.0F;
   private static final float SUBCAT_ROW_LABEL_SIZE = 14.0F;
   private static final float SUBCAT_ROW_RADIUS = 8.0F;
   private static final float SUBCAT_TOP_GAP = 16.0F;
   private static String selectedSubcategory = "All";
   private static boolean open;
   private static FeatureCategory selectedCategory = FeatureCategory.COMBAT;
   private static float lastPanelX;
   private static float lastPanelY;
   private static float lastScale = 1.0F;
   private static float moduleScrollOffset;
   private static float moduleScrollShown;
   private static float moduleScrollVelocity;
   private static Setting<?> openDropdown;
   private static Feature capturingBindFeature;
   private static InputBindSetting capturingBindInput;
   private static TextSetting focusedTextSetting;
   private static NumberSetting draggingSlider;
   private static float draggingSliderTrackX;
   private static float draggingSliderTrackWidth;
   private static ColorSetting draggingColor;
   private static int colorDragMode;
   private static float lastSvX;
   private static float lastSvY;
   private static float lastSvSize;
   private static float lastHueX;
   private static float lastHueY;
   private static float lastHueWidth;
   private static float lastHueHeight;
   private static boolean clientPanelOpen;
   private static final List<MenuOverlay.SettingRow> lastClientRows = new ArrayList<>();
   private static List<MenuOverlay.SettingRow> rowSink;
   private static float lastGearX;
   private static float lastGearY;
   private static float lastGearSize;
   private static float lastClientPanelX;
   private static float lastClientPanelY;
   private static float lastClientPanelWidth;
   private static float lastClientPanelHeight;
   private static final ModeSetting languageSetting = new ModeSetting(
      "Language", UiLanguage.current() == UiLanguage.RUSSIAN ? "Russian" : "English", "English", "Russian"
   );
   private static final NumberSetting guiScaleSetting = new NumberSetting("Gui Scale", 1.0, 0.75, 1.35, 0.01, "x");
   private static final ColorSetting themeColorSetting = new ColorSetting("Custom Color", Theme.getAccent());
   private static final ModeSetting accentModeSetting = new ModeSetting(
      "Color Mode", Theme.AccentMode.STATIC.label(), Theme.AccentMode.STATIC.label(), Theme.AccentMode.RAINBOW.label(), Theme.AccentMode.ASTOLFO.label()
   );
   private static final BooleanSetting backgroundLightSetting = new BooleanSetting("Background Light", true);
   private static final BooleanSetting backgroundEmbersSetting = new BooleanSetting("Background Embers", true);
   private static final BooleanSetting glowSetting = new BooleanSetting("Glow", false);
   private static final StringBuilder searchQuery = new StringBuilder();
   private static boolean searchFocused;
   private static float lastSearchX;
   private static float lastSearchY;
   private static float lastSearchWidth;
   private static float lastSearchHeight;
   private static final List<Feature> lastSuggestionFeatures = new ArrayList<>();
   private static float lastSuggestionX;
   private static float lastSuggestionY;
   private static float lastSuggestionWidth;
   private static float lastSuggestionRowHeight;
   private static Feature highlightedFeature;
   private static long highlightUntilNanos;
   private static final List<MenuOverlay.SettingRow> lastSettingsRows = new ArrayList<>();
   private static boolean draggingMain;
   private static float dragLastMouseX;
   private static float dragLastMouseY;
   private static float mainDragOffsetX;
   private static float mainDragOffsetY;
   private static final Motion MOTION = new Motion();
   private static float frameDelta = 0.016666668F;
   private static int hoverMouseX;
   private static int hoverMouseY;
   private static long dealStartNanos = System.nanoTime();
   private static Feature pressedCard;
   private static FeatureCategory pressedCategory;
   private static long cardPressNanos;
   private static long categoryPressNanos;
   private static long lastSliderSoundNanos;

   private static String[] subcategoriesFor(FeatureCategory category) {
      return switch (category) {
         case COMBAT -> new String[]{"All", "Rage", "Legit", "Elytra", "Utility"};
         case MOVEMENT -> new String[]{"All", "Speed", "Fly", "Assist"};
         case VISUAL -> new String[]{"All", "Enemy", "World", "Display"};
         case PLAYER -> new String[]{"All", "Auto", "Assist", "Visual"};
         case MISC -> new String[]{"All", "Economy", "Farm", "Utility"};
         default -> null;
      };
   }

   private static boolean hasSubcategories() {
      return subcategoriesFor(selectedCategory) != null;
   }

   private static String subcategoryOf(FeatureCategory category, String featureName) {
      return switch (category) {
         case COMBAT -> {
            switch (featureName) {
               case "Aura":
               case "CrystalAura":
               case "AutoAnchor":
               case "AutoExplosion":
               case "CrystalTap":
                  yield "Rage";
               case "BowAimbot":
               case "TriggerBot":
               case "AutoClicker":
               case "HitBoxes":
                  yield "Legit";
               case "ElytraTarget":
               case "ElytraResolver":
                  yield "Elytra";
               default:
                  yield "Utility";
            }
         }
         case MOVEMENT -> {
            switch (featureName) {
               case "Speed":
               case "Strafe":
               case "Sprint":
               case "NoSlow":
               case "WaterSpeed":
                  yield "Speed";
               case "GrimFly":
               case "ElytraBoost":
               case "ElytraMotion":
               case "AirStuck":
               case "Freecam":
                  yield "Fly";
               default:
                  yield "Assist";
            }
         }
         case VISUAL -> {
            switch (featureName) {
               case "EntityESP":
               case "NameTags":
               case "Chams":
               case "Arrows":
               case "Trajectories":
               case "JumpCircles":
               case "Crosshair":
               case "CrystalOptimizer":
                  yield "Enemy";
               case "WorldTweaks":
               case "Ambience":
               case "Atmosphere":
               case "RTX":
               case "Particles":
               case "Trails":
               case "LineGlyphes":
               case "Removals":
                  yield "World";
               default:
                  yield "Display";
            }
         }
         case PLAYER -> {
            switch (featureName) {
               case "AutoArmor":
               case "AutoGapple":
               case "AutoPotion":
               case "AutoFish":
               case "AutoUse":
               case "AntiAfk":
                  yield "Auto";
               case "FullBright":
               case "FakePlayer":
                  yield "Visual";
               default:
                  yield "Assist";
            }
         }
         case MISC -> {
            switch (featureName) {
               case "AuctionHelper":
               case "AuctionRelist":
               case "AppleFarmer":
               case "AutoTrade":
               case "ClanUpgrade":
               case "ClanInvest":
               case "ServerHelper":
                  yield "Economy";
               case "AutoMine":
               case "MineHelper":
               case "CreeperFarm":
               case "Nuker":
               case "AutoCrafter":
               case "AutoWarden":
               case "PveManager":
               case "Synchronization":
               case "AutoTpLoot":
               case "BaseFinder":
               case "GriefJoiner":
                  yield "Farm";
               default:
                  yield "Utility";
            }
         }
         default -> "All";
      };
   }

   private static Identifier categoryIcon(FeatureCategory category) {
      return switch (category) {
         case VISUAL -> Textures.Icons.EYE;
         case PLAYER -> Textures.Icons.USER_ROUND;
         case MISC -> Textures.Icons.BOXES;
         case COSMETICS -> Textures.Icons.GIFT;
         case SCRIPTS -> Textures.Icons.COMMAND;
         default -> Textures.Icons.BOXES;
      };
   }

   private static List<FeatureCategory> stripCategories() {
      List<FeatureCategory> categories = new ArrayList<>();

      for (FeatureCategory[] group : SECTION_CATEGORIES) {
         categories.addAll(List.of(group));
      }

      return categories;
   }

   private static float categoryChipWidth(FeatureCategory category, float reveal, float scale) {
      float icon = 15.0F * scale;
      float width = 28.0F * scale + icon;
      if (reveal > 0.001F) {
         float label = UiFonts.sfProDisplay().measureWidth(category.getDisplayName(), 15.0F * scale, 0.0F);
         width += reveal * (10.0F * scale + label);
      }

      return width;
   }

   private static List<MenuOverlay.CategoryChip> categoryChips(float contentX, float scale) {
      List<MenuOverlay.CategoryChip> chips = new ArrayList<>();
      float cursor = contentX;

      for (FeatureCategory category : stripCategories()) {
         float reveal = MOTION.spring(subKey(category, "chip"), category == selectedCategory ? 1.0F : 0.0F, SPEED_COLOR);
         float width = categoryChipWidth(category, reveal, scale);
         chips.add(new MenuOverlay.CategoryChip(category, cursor, width, reveal));
         cursor += width + 8.0F * scale;
      }

      return chips;
   }

   private static float moduleGridTop(float panelY, float scale) {
      return panelY + 81.0F * scale;
   }

   public static void loadSavedTheme() {
      int color = MenuConfigStore.getInt("theme.accentColor", 0);
      if (color != 0) {
         themeColorSetting.setValue(color);
      }

      accentModeSetting.setValue(Theme.AccentMode.byLabel(MenuConfigStore.getString("theme.accentMode", Theme.AccentMode.STATIC.label())).label());
      backgroundLightSetting.setValue(MenuConfigStore.getBoolean("background.light", true));
      backgroundEmbersSetting.setValue(MenuConfigStore.getBoolean("background.embers", true));
      glowSetting.setValue(MenuConfigStore.getBoolean("menu.glow", false));
   }

   private static void redeal() {
      dealStartNanos = System.nanoTime();
   }

   private MenuOverlay() {
   }

   public static boolean toggle(Minecraft minecraft) {
      if (open) {
         close(minecraft);
         return true;
      } else {
         open(minecraft);
         return true;
      }
   }

   public static void close(Minecraft minecraft) {
      close(minecraft, true);
   }

   private static void close(Minecraft minecraft, boolean sound) {
      if (open) {
         if (sound) {
            ClientSounds.playMenuClose();
         }

         open = false;
         openDropdown = null;
         capturingBindFeature = null;
         capturingBindInput = null;
         focusedTextSetting = null;
         draggingSlider = null;
         draggingMain = false;
         searchFocused = false;
         highlightedFeature = null;
         if (shouldReturnMouseToGame(minecraft)) {
            minecraft.mouseHandler.grabMouse();
         }
      }
   }

   public static boolean suspendForReload(Minecraft minecraft) {
      if (!open) {
         return false;
      }

      close(minecraft, false);
      return true;
   }

   public static void resumeAfterReload(Minecraft minecraft) {
   }

   public static boolean isOpen() {
      return open;
   }

   public static boolean isVisible() {
      return open;
   }

   public static boolean blocksInput() {
      return open;
   }

   public static boolean handleKey(int key) {
      if (!open) {
         return false;
      }

      if (capturingBindFeature != null) {
         if (isBindClearKey(key)) {
            capturingBindFeature.getBind().clear();
         } else {
            capturingBindFeature.getBind().setSingle(key);
         }

         capturingBindFeature = null;
         return true;
      } else if (capturingBindInput != null) {
         if (isBindClearKey(key)) {
            capturingBindInput.clear();
         } else {
            capturingBindInput.setKey(key);
         }

         capturingBindInput = null;
         return true;
      } else if (searchFocused) {
         if (key == 259) {
            if (searchQuery.length() > 0) {
               searchQuery.deleteCharAt(searchQuery.length() - 1);
            }

            return true;
         } else if (key == 257) {
            List<Feature> matches = searchMatches();
            if (!matches.isEmpty()) {
               selectFromSearch(matches.get(0));
            } else {
               searchFocused = false;
            }

            return true;
         } else if (key == 256) {
            searchFocused = false;
            return true;
         } else {
            return true;
         }
      } else if (focusedTextSetting != null) {
         if (key == 259) {
            String value = focusedTextSetting.getValue();
            if (!value.isEmpty()) {
               focusedTextSetting.setValue(value.substring(0, value.length() - 1));
            }

            return true;
         } else {
            if (key != 257 && key != 256) {
               return true;
            }

            focusedTextSetting = null;
            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean isBindClearKey(int key) {
      return key == 256 || key == 261 || key == 259;
   }

   public static boolean handleCharacter(int codePoint) {
      if (searchFocused) {
         searchQuery.append(new String(Character.toChars(codePoint)));
         return true;
      }

      if (focusedTextSetting == null) {
         return false;
      }

      if (focusedTextSetting.getValue().length() >= focusedTextSetting.getMaxLength()) {
         return true;
      }

      focusedTextSetting.setValue(focusedTextSetting.getValue() + new String(Character.toChars(codePoint)));
      return true;
   }

   public static void handleScroll(double vertical) {
      if (open) {
         float step = (float)vertical * 46.0F * lastScale;
         moduleScrollVelocity -= step * 9.0F;
      }
   }

   private static void integrateScroll() {
      if (Math.abs(moduleScrollVelocity) < 4.0F) {
         moduleScrollVelocity = 0.0F;
      } else {
         moduleScrollOffset = moduleScrollOffset + moduleScrollVelocity * frameDelta;
         moduleScrollVelocity = moduleScrollVelocity * (float)Math.exp(-frameDelta * 9.0F);
      }
   }

   public static boolean handleMouseButton(Minecraft minecraft, int button, int action) {
      if (!open) {
         return false;
      }

      if (action == 0) {
         draggingMain = false;
         draggingSlider = null;
         draggingColor = null;
         colorDragMode = 0;
         pressedCard = null;
         pressedCategory = null;
         return true;
      }

      if (action != 1) {
         return true;
      }

      int mouseX = mouseX(minecraft);
      int mouseY = mouseY(minecraft);
      if (capturingBindFeature != null) {
         capturingBindFeature.getBind().setSingle(BindSetting.mouse(button));
         capturingBindFeature = null;
         return true;
      }

      if (capturingBindInput != null) {
         capturingBindInput.setMouse(button);
         capturingBindInput = null;
         return true;
      }

      if (button == 2) {
         Feature feature = moduleCardAt(mouseX, mouseY);
         if (feature != null) {
            capturingBindFeature = feature;
         }

         return true;
      } else {
         if (button == 0) {
            if (insideRect(mouseX, mouseY, lastSearchX, lastSearchY, lastSearchWidth, lastSearchHeight)) {
               searchFocused = !searchFocused;
               if (searchFocused) {
                  MOTION.play("searchSuggest");
               }

               focusedTextSetting = null;
               return true;
            }

            if (handleSuggestionClick(mouseX, mouseY)) {
               return true;
            }

            searchFocused = false;
            if (lastGearSize > 0.0F && insideRect(mouseX, mouseY, lastGearX, lastGearY, lastGearSize, lastGearSize)) {
               clientPanelOpen = !clientPanelOpen;
               openDropdown = null;
               return true;
            }

            if (handleClientClick(mouseX, mouseY)) {
               return true;
            }

            if (handleRowClick(lastSettingsRows, mouseX, mouseY)) {
               return true;
            }

            FeatureCategory clicked = categoryStripAt(lastPanelX, lastPanelY, lastScale, mouseX, mouseY);
            if (clicked != null) {
               if (clicked != selectedCategory) {
                  redeal();
               }

               categoryPressNanos = System.nanoTime();
               selectedCategory = clicked;
               selectedSubcategory = "All";
               moduleScrollOffset = 0.0F;
               moduleScrollVelocity = 0.0F;
               pressedCategory = clicked;
               ClientSounds.playUiOn();
               return true;
            }

            String subClicked = subcategoryStripAt(lastPanelX, lastPanelY, lastScale, mouseX, mouseY);
            if (subClicked != null) {
               if (!subClicked.equals(selectedSubcategory)) {
                  redeal();
               }

               selectedSubcategory = subClicked;
               moduleScrollOffset = 0.0F;
               moduleScrollVelocity = 0.0F;
               ClientSounds.playUiOn();
               return true;
            }

            Feature feature = moduleCardAt(mouseX, mouseY);
            if (feature != null) {
               pressedCard = feature;
               cardPressNanos = System.nanoTime();
               if (feature.isToggleable()) {
                  feature.setEnabled(!feature.isEnabled());
                  ClientSounds.playModuleToggle(feature.isEnabled());
               }

               return true;
            }

            openDropdown = null;
            focusedTextSetting = null;
            if (clientPanelOpen && insideRect(mouseX, mouseY, lastClientPanelX, lastClientPanelY, lastClientPanelWidth, lastClientPanelHeight)) {
               return true;
            }

            if (insideRect(mouseX, mouseY, lastPanelX, lastPanelY, 1052.0F * lastScale, 663.0F * lastScale)) {
               draggingMain = true;
               dragLastMouseX = mouseX;
               dragLastMouseY = mouseY;
               return true;
            }
         }

         return true;
      }
   }

   private static boolean handleClientClick(int mouseX, int mouseY) {
      return clientPanelOpen && handleRowClick(lastClientRows, mouseX, mouseY);
   }

   private static boolean handleSuggestionClick(int mouseX, int mouseY) {
      if (lastSuggestionFeatures.isEmpty()) {
         return false;
      } else {
         float totalHeight = lastSuggestionRowHeight * lastSuggestionFeatures.size();
         if (!insideRect(mouseX, mouseY, lastSuggestionX, lastSuggestionY, lastSuggestionWidth, totalHeight)) {
            return false;
         } else {
            int index = (int)((mouseY - lastSuggestionY) / lastSuggestionRowHeight);
            if (index >= 0 && index < lastSuggestionFeatures.size()) {
               selectFromSearch(lastSuggestionFeatures.get(index));
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private static boolean handleRowClick(List<MenuOverlay.SettingRow> rows, int mouseX, int mouseY) {
      for (int i = rows.size() - 1; i >= 0; i--) {
         MenuOverlay.SettingRow row = rows.get(i);
         if (insideRect(mouseX, mouseY, row.x(), row.y(), row.width(), row.height())) {
            applyRow(row, mouseX, mouseY);
            return true;
         }
      }

      return false;
   }

   private static void applyRow(MenuOverlay.SettingRow row, int mouseX, int mouseY) {
      switch (row.kind()) {
         case CHECKBOX: {
            BooleanSetting setting = (BooleanSetting)row.setting();
            setting.setValue(!setting.getValue());
            break;
         }
         case MODE_TOGGLE:
         case COLOR_TOGGLE:
            boolean opening = openDropdown != row.setting();
            openDropdown = opening ? row.setting() : null;
            if (opening) {
               ClientSounds.playUiOn();
            } else {
               ClientSounds.playUiOff();
            }
            break;
         case MODE_OPTION:
            ((ModeSetting)row.setting()).setValue(row.optionValue());
            break;
         case SLIDER: {
            NumberSetting setting = (NumberSetting)row.setting();
            draggingSlider = setting;
            draggingSliderTrackX = row.x();
            draggingSliderTrackWidth = row.width();
            applySliderValue(setting, mouseX, draggingSliderTrackX, draggingSliderTrackWidth);
            break;
         }
         case COLOR_SV:
            draggingColor = (ColorSetting)row.setting();
            colorDragMode = 1;
            applyColorSv(draggingColor, mouseX, mouseY);
            break;
         case COLOR_HUE:
            draggingColor = (ColorSetting)row.setting();
            colorDragMode = 2;
            applyColorHue(draggingColor, mouseX);
            break;
         case MULTI_OPTION:
            ((MultiSelectSetting)row.setting()).toggle(row.optionValue());
            break;
         case TEXT_FIELD:
            focusedTextSetting = (TextSetting)row.setting();
            break;
         case BUTTON:
            ((ButtonSetting)row.setting()).press();
            break;
         case BIND_FIELD:
            capturingBindInput = (InputBindSetting)row.setting();
      }
   }

   private static void applySliderValue(NumberSetting setting, float mouseX, float trackX, float trackWidth) {
      double progress = MathUtil.clamp01((mouseX - trackX) / trackWidth);
      setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * progress);
      playSliderTick();
   }

   private static void playSliderTick() {
      long now = System.nanoTime();
      if (now - lastSliderSoundNanos >= 90000000L) {
         lastSliderSoundNanos = now;
         ClientSounds.playSlider();
      }
   }

   private static void applyColorSv(ColorSetting setting, float mouseX, float mouseY) {
      if (!(lastSvSize <= 0.0F)) {
         float sat = MathUtil.clamp01((mouseX - lastSvX) / lastSvSize);
         float val = 1.0F - MathUtil.clamp01((mouseY - lastSvY) / lastSvSize);
         float hue = ColorUtil.hsv(setting.getValue())[0];
         setting.setValue(ColorUtil.fromHsv(hue, sat, val, 255));
      }
   }

   private static void applyColorHue(ColorSetting setting, float mouseX) {
      if (!(lastHueWidth <= 0.0F)) {
         float hue = MathUtil.clamp01((mouseX - lastHueX) / lastHueWidth);
         float[] hsv = ColorUtil.hsv(setting.getValue());
         float sat = Math.max(0.05F, hsv[1]);
         float val = Math.max(0.15F, hsv[2]);
         setting.setValue(ColorUtil.fromHsv(hue, sat, val, 255));
      }
   }

   private static boolean insideRect(float pointX, float pointY, float x, float y, float width, float height) {
      return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
   }

   public static boolean handleScreenMouseButton(Minecraft minecraft, int button, ScreenMouseButtonEvent.Action action) {
      return action == ScreenMouseButtonEvent.Action.DRAG
         ? open
         : handleMouseButton(minecraft, button, action == ScreenMouseButtonEvent.Action.RELEASE ? 0 : 1);
   }

   public static void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor, int screenWidth, int screenHeight) {
      MOTION.tick();
      frameDelta = MOTION.delta();
      float openRaw = MOTION.spring("openAnim", open ? 1.0F : 0.0F, open ? SPEED_OPEN : SPEED_CLOSE);
      if (!open && openRaw < 0.01F) {
         Render2DUtil.setBackdropBlurScale(1.0F);
      } else {
         float openT = MathUtil.clamp01(openRaw);
         Render2DUtil.setBackdropBlurScale(0.35F + 0.65F * openT);
         hoverMouseX = mouseX(minecraft);
         hoverMouseY = mouseY(minecraft);
         float menuScale = MOTION.spring("menuScale", guiScaleSetting.getValue().floatValue(), SPEED_HOVER);
         float width = screenWidth * 0.4109375F * menuScale;
         float height = screenHeight * 0.46041667F * menuScale;
         float scale = width / 1052.0F;
         if (draggingMain) {
            mainDragOffsetX = mainDragOffsetX + (hoverMouseX - dragLastMouseX);
            mainDragOffsetY = mainDragOffsetY + (hoverMouseY - dragLastMouseY);
            dragLastMouseX = hoverMouseX;
            dragLastMouseY = hoverMouseY;
         }

         if (draggingSlider != null) {
            updateSliderDrag(hoverMouseX);
         }

         if (draggingColor != null) {
            if (colorDragMode == 0) {
               draggingColor = null;
            } else if (colorDragMode == 1) {
               applyColorSv(draggingColor, hoverMouseX, hoverMouseY);
            } else {
               applyColorHue(draggingColor, hoverMouseX);
            }
         }

         float x = MathUtil.clamp((screenWidth - width) / 2.0F + mainDragOffsetX, 0.0F, Math.max(0.0F, screenWidth - width));
         float y = MathUtil.clamp((screenHeight - height) / 2.0F + mainDragOffsetY, 0.0F, Math.max(0.0F, screenHeight - height));
         lastPanelX = x;
         lastPanelY = y;
         lastScale = scale;
         integrateScroll();
         moduleScrollShown = MOTION.spring("moduleScroll", moduleScrollOffset, SPEED_SCROLL);
         float overshoot = openRaw - openT;
         float zoom = 0.94F + 0.06F * openT + overshoot * 0.03F;
         float tilt = (float)Math.toRadians((1.0F - openT) * -1.1F);
         float centerX = x + width / 2.0F;
         float centerY = y + height / 2.0F;
         Matrix3x2fStack pose = guiGraphicsExtractor.pose();
         pose.pushMatrix();
         pose.translate(centerX, centerY);
         if (tilt != 0.0F) {
            pose.rotate(tilt);
         }

         pose.scale(zoom, zoom);
         pose.translate(-centerX, -centerY);
         Render2DUtil.rect(x, y, width, height)
            .color(ColorUtil.withAlpha(MENU_PANEL, 0.98F))
            .radius(10.0F * scale)
            .border(Math.max(0.5F, 1.0F * scale), Theme.Colors.PANEL_BORDER)
            .blur(34.0F * scale, 1.0F)
            .draw();
         Render2DUtil.rect(x, y, width, height).color(ColorUtil.withAlpha(MENU_PANEL, 0.3F)).radius(10.0F * scale).draw();
         if (glowSetting.getValue()) {
            MenuBorderGlow.render(x, y, width, height, 10.0F * scale, scale, openT, frameDelta);
         }

         renderSidebar(guiGraphicsExtractor, x, y, scale, height);
         renderCategoryStrip(x, y, scale);
         renderSubcategoryStrip(x, y, scale);
         renderSearchBar(x, y, scale, height);
         renderModules(guiGraphicsExtractor, x, y, scale, height, openT);
         renderSearchSuggestions(x, y, scale, height);
         renderClientPanel(guiGraphicsExtractor, x, y, height, scale);
         pose.popMatrix();
      }
   }

   private static void updateSliderDrag(float mouseX) {
      if (draggingSliderTrackWidth > 0.0F) {
         applySliderValue(draggingSlider, mouseX, draggingSliderTrackX, draggingSliderTrackWidth);
      }
   }

   private static void renderSidebar(GuiGraphicsExtractor extractor, float panelX, float panelY, float scale, float panelHeight) {
      renderAccountCard(extractor, panelX, panelY, 195.0F * scale, scale);
   }

   private static void renderCategoryStrip(float panelX, float panelY, float scale) {
      float contentX = panelX + 195.0F * scale + 20.0F * scale;
      float chipY = panelY + 20.0F * scale;
      float chipHeight = 38.0F * scale;
      int accent = Theme.getAccent();
      int dim = ColorUtil.withAlpha(-12829629, 0.6F);
      List<MenuOverlay.CategoryChip> chips = categoryChips(contentX, scale);
      float pillX = -1.0F;
      float pillWidth = 0.0F;

      for (MenuOverlay.CategoryChip chip : chips) {
         if (chip.category() == selectedCategory) {
            pillX = MOTION.spring("stripPillX", chip.x(), SPEED_COLOR);
            pillWidth = MOTION.spring("stripPillW", chip.width(), SPEED_COLOR);
            Render2DUtil.rect(pillX, chipY, pillWidth, chipHeight).color(ColorUtil.withAlpha(-12829629, 0.3F)).radius(10.0F * scale).draw();
         }
      }

      for (MenuOverlay.CategoryChip chip : chips) {
         FeatureCategory category = chip.category();
         boolean active = category == selectedCategory;
         float reveal = chip.reveal();
         boolean hovered = !active && insideRect(hoverMouseX, hoverMouseY, chip.x(), chipY, chip.width(), chipHeight);
         float activeT = MOTION.spring(category, active ? 1.0F : 0.0F, SPEED_COLOR);
         float pressT = MOTION.spring(subKey(category, "dots"), category == pressedCategory ? 1.0F : 0.0F, SPEED_PRESS);
         int textColor = mixColor(dim, -1, hovered ? Math.max(activeT, 0.4F) : activeT);
         int iconColor = mixColor(dim, accent, activeT);
         if (pressT > 0.0F) {
            textColor = mixColor(textColor, accent, pressT * 0.45F);
            iconColor = mixColor(iconColor, accent, pressT * 0.45F);
         }

         if (reveal <= 0.001F && hovered) {
            Render2DUtil.rect(chip.x(), chipY, chip.width(), chipHeight).color(ColorUtil.withAlpha(-1, 0.08F)).radius(10.0F * scale).draw();
         }

         float iconSize = 15.0F * scale;
         float iconX = chip.x() + 14.0F * scale;
         float centerY = chipY + chipHeight / 2.0F;
         if (CategoryIconGlyphs.isGlyph(category)) {
            MsdfFont glyphFont = UiFonts.divineNotificationIcons();
            Render2DUtil.text(iconX, glyphFont.centeredTextY(centerY, iconSize), iconSize, CategoryIconGlyphs.glyph(category))
               .font(glyphFont)
               .color(iconColor)
               .draw();
         } else {
            Render2DUtil.texture(iconX, chipY + (chipHeight - iconSize) / 2.0F, iconSize, iconSize, categoryIcon(category)).color(iconColor).draw();
         }

         if (reveal > 0.001F) {
            float labelSize = 15.0F * scale;
            float textX = iconX + iconSize + 10.0F * scale;
            Render2DUtil.pushScissor(chip.x(), chipY, chip.width(), chipHeight);
            Render2DUtil.text(textX, UiFonts.sfProDisplay().centeredTextY(centerY, labelSize), labelSize, category.getDisplayName())
               .style(UiFontStyle.MEDIUM)
               .color(ColorUtil.withAlpha(textColor, reveal))
               .draw();
            Render2DUtil.popScissor();
         }
      }

      float rippleAge = (float)(System.nanoTime() - categoryPressNanos) / 1.0E9F;
      if (pillX >= 0.0F && rippleAge < 0.5F) {
         float t = rippleAge / 0.5F;
         float grow = Motion.ease(Motion.Ease.OUT_QUINT, t) * 14.0F * scale;
         Render2DUtil.rect(pillX - grow, chipY - grow, pillWidth + grow * 2.0F, chipHeight + grow * 2.0F)
            .color(0)
            .radius(10.0F * scale + grow)
            .border(Math.max(0.5F, 1.5F * scale * (1.0F - t)), ColorUtil.withAlpha(accent, 0.55F * (1.0F - t) * (1.0F - t)))
            .draw();
      }

      float dividerY = panelY + 68.0F * scale;
      Render2DUtil.rect(panelX, dividerY, 1052.0F * scale, Math.max(0.5F, 1.0F * scale)).color(SEPARATOR).draw();
   }

   private static void renderSubcategoryStrip(float panelX, float panelY, float scale) {
      String[] subcategories = subcategoriesFor(selectedCategory);
      if (subcategories != null) {
         float margin = 12.0F * scale;
         float rowX = panelX + margin;
         float rowWidth = 195.0F * scale - margin * 2.0F;
         float rowHeight = 30.0F * scale;
         float labelSize = 14.0F * scale;
         float cursorY = panelY + 12.0F * scale + 46.0F * scale + 16.0F * scale;
         int dim = ColorUtil.withAlpha(-12829629, 0.6F);
         float activeY = -1.0F;
         float probeY = cursorY;

         for (String subcategory : subcategories) {
            if (subcategory.equals(selectedSubcategory)) {
               activeY = probeY;
            }

            probeY += rowHeight + 4.0F * scale;
         }

         if (activeY >= 0.0F) {
            float pillY = MOTION.spring("subcatPillY", activeY, SPEED_COLOR);
            Render2DUtil.rect(rowX, pillY, rowWidth, rowHeight).color(ColorUtil.withAlpha(-12829629, 0.3F)).radius(8.0F * scale).draw();
         }

         for (String subcategory : subcategories) {
            boolean active = subcategory.equals(selectedSubcategory);
            boolean hovered = !active && insideRect(hoverMouseX, hoverMouseY, rowX, cursorY, rowWidth, rowHeight);
            float activeT = MOTION.spring(subKey(subcategory, "subcat"), active ? 1.0F : 0.0F, SPEED_COLOR);
            if (activeT <= 0.001F && hovered) {
               Render2DUtil.rect(rowX, cursorY, rowWidth, rowHeight).color(ColorUtil.withAlpha(-1, 0.08F)).radius(8.0F * scale).draw();
            }

            int textColor = mixColor(dim, -1, hovered ? Math.max(activeT, 0.4F) : activeT);
            Render2DUtil.text(rowX + 14.0F * scale, UiFonts.sfProDisplay().centeredTextY(cursorY + rowHeight / 2.0F, labelSize), labelSize, subcategory)
               .style(UiFontStyle.MEDIUM)
               .color(textColor)
               .draw();
            cursorY += rowHeight + 4.0F * scale;
         }
      }
   }

   private static void renderAccountCard(GuiGraphicsExtractor extractor, float panelX, float panelY, float sidebarWidth, float scale) {
      float margin = 12.0F * scale;
      float cardX = panelX + margin;
      float cardY = panelY + margin;
      float cardWidth = sidebarWidth - margin * 2.0F;
      float cardHeight = 46.0F * scale;
      float avatarSize = 34.0F * scale;
      float avatarX = cardX;
      float avatarY = cardY + (cardHeight - avatarSize) / 2.0F;
      Render2DUtil.texture(avatarX, avatarY, avatarSize, avatarSize, Textures.Header.FURRY_AVATAR).color(-1).radius(avatarSize / 2.0F).draw();
      float gearSize = 17.0F * scale;
      float gearX = cardX + cardWidth - gearSize;
      float gearY = cardY + (cardHeight - gearSize) / 2.0F;
      lastGearX = gearX;
      lastGearY = gearY;
      lastGearSize = gearSize;
      boolean gearHover = insideRect(hoverMouseX, hoverMouseY, gearX, gearY, gearSize, gearSize);
      float gearT = MOTION.spring("gear", !clientPanelOpen && !gearHover ? 0.0F : 1.0F, SPEED_COLOR);
      float gearSpin = (float)Math.toRadians(gearT * 45.0F);
      Matrix3x2fStack gearPose = extractor.pose();
      gearPose.pushMatrix();
      gearPose.translate(gearX + gearSize / 2.0F, gearY + gearSize / 2.0F);
      gearPose.rotate(gearSpin);
      gearPose.translate(-(gearX + gearSize / 2.0F), -(gearY + gearSize / 2.0F));
      Render2DUtil.texture(gearX, gearY, gearSize, gearSize, Textures.Header.SETTINGS).color(mixColor(TEXT_MUTED, Theme.getAccent(), gearT)).draw();
      gearPose.popMatrix();
      float textX = avatarX + avatarSize + 10.0F * scale;
      float labelSize = 10.0F * scale;
      float handleSize = 13.0F * scale;
      float personSize = labelSize;
      float labelCenterY = cardY + cardHeight * 0.34F;
      float iconY = labelCenterY - personSize / 2.0F;
      Render2DUtil.texture(textX, iconY, personSize, personSize, Textures.Icons.USER_ROUND).color(TEXT_MUTED).draw();
      Render2DUtil.text(textX + personSize + 5.0F * scale, UiFonts.sfProDisplay().centeredTextY(labelCenterY, labelSize), labelSize, "Your Account")
         .style(UiFontStyle.MEDIUM)
         .color(TEXT_MUTED)
         .draw();
      String username = DiscordPresence.INSTANCE.username();
      String handle = username != null && !username.isBlank() ? "@" + username : "Not connected";
      Render2DUtil.text(textX, UiFonts.sfProDisplay().centeredTextY(cardY + cardHeight * 0.66F, handleSize), handleSize, handle)
         .style(UiFontStyle.SEMIBOLD)
         .color(TEXT_PRIMARY)
         .draw();
   }

   private static void renderClientPanel(GuiGraphicsExtractor guiGraphicsExtractor, float mainPanelX, float mainPanelY, float mainPanelHeight, float scale) {
      lastClientRows.clear();
      float appear = MOTION.spring("clientAppear", clientPanelOpen ? 1.0F : 0.0F, clientPanelOpen ? SPEED_APPEAR : SPEED_CLOSE);
      if (appear < 0.01F) {
         lastClientPanelWidth = 0.0F;
         lastClientPanelHeight = 0.0F;
      } else {
         rowSink = lastClientRows;
         float padding = 16.0F * scale;
         float panelWidth = 300.0F * scale;
         float rowWidth = panelWidth - padding * 2.0F;
         float slideIn = (1.0F - MathUtil.clamp01(appear)) * 26.0F * scale;
         float panelX = mainPanelX - 18.0F * scale - panelWidth - slideIn;
         float panelY = mainPanelY;
         float headerHeight = 18.0F * scale + 4.0F * scale + 12.0F * scale * 2.0F + 11.0F * scale + Math.max(0.5F, 1.0F * scale) + 10.0F * scale;
         float rowH = 27.0F * scale;
         float sliderH = 33.0F * scale;
         float gap = 7.0F * scale;
         float dividerThickness = Math.max(0.5F, 1.0F * scale);
         float rowsHeight = rowH + gap + sliderH + gap + rowH + gap + rowH;
         float panelHeight = padding * 2.0F + headerHeight + rowsHeight;
         lastClientPanelX = panelX;
         lastClientPanelY = panelY;
         lastClientPanelWidth = panelWidth;
         lastClientPanelHeight = panelHeight;
         float zoom = 0.9F + 0.1F * MathUtil.clamp01(appear) + Math.max(0.0F, appear - 1.0F) * 0.35F;
         Matrix3x2fStack pose = guiGraphicsExtractor.pose();
         pose.pushMatrix();
         pose.translate(panelX, panelY);
         pose.scale(zoom, zoom);
         pose.translate(-panelX, -panelY);
         Render2DUtil.rect(panelX, panelY, panelWidth, panelHeight)
            .color(ColorUtil.withAlpha(MENU_PANEL, 0.98F))
            .radius(12.0F * scale)
            .border(Math.max(0.5F, 1.0F * scale), Theme.Colors.PANEL_BORDER)
            .blur(34.0F * scale, 1.0F)
            .draw();
         float cursorY = panelY + padding;
         float gearSize = 17.0F * scale;
         Render2DUtil.text(panelX + padding, cursorY, 18.0F * scale, "Settings Panel").style(UiFontStyle.SEMIBOLD).color(TEXT_PRIMARY).draw();
         Render2DUtil.texture(panelX + panelWidth - padding - gearSize, cursorY, gearSize, gearSize, Textures.Header.SETTINGS).color(TEXT_MUTED).draw();
         cursorY += 18.0F * scale + 4.0F * scale;
         Render2DUtil.text(panelX + padding, cursorY, 12.0F * scale, "Customize the GUI appearance, layout")
            .style(UiFontStyle.MEDIUM)
            .color(Theme.Colors.SECTION)
            .draw();
         cursorY += 12.0F * scale;
         Render2DUtil.text(panelX + padding, cursorY, 12.0F * scale, "and interface preferences.").style(UiFontStyle.MEDIUM).color(Theme.Colors.SECTION).draw();
         cursorY += 12.0F * scale + 11.0F * scale;
         Render2DUtil.rect(panelX + padding, cursorY, rowWidth, dividerThickness).color(HAIRLINE).draw();
         cursorY += dividerThickness + 10.0F * scale;
         float rowX = panelX + padding;
         renderSettingsLabel(rowX, cursorY, rowH, "Language", TEXT_ROW, scale, 1.0F);
         MenuOverlay.SettingRow langRow = renderDropdownPill(
            rowX, cursorY, rowWidth, rowH, false, languageSetting, languageSetting.getValue(), MenuOverlay.SettingRowKind.MODE_TOGGLE, scale, 1.0F
         );
         lastClientRows.add(langRow);
         float langY = cursorY;
         cursorY += rowH + gap;
         renderClientSlider(rowX, cursorY, rowWidth, sliderH, guiScaleSetting, scale);
         cursorY += sliderH + gap;
         renderSettingsLabel(rowX, cursorY, rowH, "Themes", TEXT_ROW, scale, 1.0F);
         float swatchW = 34.0F * scale;
         float swatchH = 20.0F * scale;
         float swatchX = rowX + rowWidth - swatchW;
         float swatchY = cursorY + (rowH - swatchH) / 2.0F;
         Render2DUtil.rect(swatchX, swatchY, swatchW, swatchH)
            .color(Theme.isAccentAnimated() ? Theme.getAccent() : themeColorSetting.getValue())
            .radius(7.0F * scale)
            .border(Math.max(0.5F, 1.0F * scale), HAIRLINE)
            .draw();
         MenuOverlay.SettingRow themeRow = new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.COLOR_TOGGLE, themeColorSetting, rowX, cursorY, rowWidth, rowH);
         lastClientRows.add(themeRow);
         cursorY += rowH + gap;
         renderSettingsLabel(rowX, cursorY, rowH, accentModeSetting.getName(), TEXT_ROW, scale, 1.0F);
         MenuOverlay.SettingRow accentModeRow = renderDropdownPill(
            rowX, cursorY, rowWidth, rowH, false, accentModeSetting, accentModeSetting.getValue(), MenuOverlay.SettingRowKind.MODE_TOGGLE, scale, 1.0F
         );
         lastClientRows.add(accentModeRow);
         if (openDropdown == languageSetting) {
            renderModeFlyout(
               languageSetting, new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.MODE_TOGGLE, languageSetting, rowX, langY, rowWidth, rowH), scale
            );
         } else if (openDropdown == themeColorSetting) {
            renderColorFlyout(themeColorSetting, themeRow, scale);
         } else if (openDropdown == accentModeSetting) {
            renderModeFlyout(accentModeSetting, accentModeRow, scale);
         }

         pose.popMatrix();
      }
   }

   private static void renderClientSlider(float rowX, float rowY, float rowWidth, float rowHeight, NumberSetting numberSetting, float scale) {
      float labelY = UiFonts.sfProDisplay().centeredTextY(rowY + 12.0F * scale / 2.0F, 14.0F * scale);
      Render2DUtil.text(rowX, labelY, 14.0F * scale, numberSetting.getName()).style(UiFontStyle.MEDIUM).color(TEXT_ROW).draw();
      Render2DUtil.text(rowX + rowWidth, labelY, 12.0F * scale, NumberSetting.format(numberSetting.getValue()) + "x")
         .style(UiFontStyle.MEDIUM)
         .color(TEXT_MUTED)
         .align(TextAlign.RIGHT)
         .draw();
      float trackHeight = 4.0F * scale;
      float knobSize = 11.0F * scale;
      float trackY = rowY + rowHeight - knobSize / 2.0F - trackHeight / 2.0F;
      float trackCenterY = trackY + trackHeight / 2.0F;
      boolean sliderHot = draggingSlider == numberSetting;
      Render2DUtil.rect(rowX, trackY, rowWidth, trackHeight).color(ColorUtil.withAlpha(-12303028, 0.9F)).radius(trackHeight / 2.0F).draw();
      float progress = MOTION.spring(numberSetting, (float)numberSetting.getProgress(), sliderHot ? SLIDER_GRAB : SPEED_TOGGLE);
      if (progress > 0.001F) {
         Render2DUtil.rect(rowX, trackY, Math.max(trackHeight, rowWidth * progress), trackHeight).color(Theme.getAccent()).radius(trackHeight / 2.0F).draw();
      }

      float knobX = MathUtil.clamp(rowX + rowWidth * progress - knobSize / 2.0F, rowX - knobSize * 0.15F, rowX + rowWidth - knobSize * 0.85F);
      float knobVelocity = MOTION.velocity(numberSetting);
      float stretch = MathUtil.clamp(Math.abs(knobVelocity) * 0.06F, 0.0F, 0.35F);
      float knobW = knobSize * (1.0F + stretch);
      float knobH = knobSize * (1.0F - stretch * 0.5F);
      Render2DUtil.rect(knobX + (knobSize - knobW) / 2.0F, trackCenterY - knobH / 2.0F, knobW, knobH).color(-1).radius(knobH / 2.0F).draw();
      float trackHitPadding = knobSize / 2.0F + 3.0F * scale;
      lastClientRows.add(
         new MenuOverlay.SettingRow(
            MenuOverlay.SettingRowKind.SLIDER, numberSetting, null, -1, rowX, trackCenterY - trackHitPadding, rowWidth, trackHitPadding * 2.0F
         )
      );
   }

   private static void renderSearchBar(float panelX, float panelY, float scale, float panelHeight) {
      float barWidth = 240.0F * scale;
      float barHeight = 36.0F * scale;
      float barX = panelX + 1052.0F * scale - 20.0F * scale - barWidth;
      float barY = panelY + 20.0F * scale + (38.0F * scale - barHeight) / 2.0F;
      int dimColor = ColorUtil.withAlpha(-12829629, 0.6F);
      float focusT = MOTION.spring("searchFocus", searchFocused ? 1.0F : 0.0F, SPEED_COLOR);
      int borderColor = mixColor(ColorUtil.withAlpha(-12829629, 0.5F), Theme.getAccent(), focusT);
      float grow = focusT * 2.0F * scale;
      barX -= grow;
      barY -= grow * 0.5F;
      barWidth += grow * 2.0F;
      barHeight += grow;
      lastSearchX = barX;
      lastSearchY = barY;
      lastSearchWidth = barWidth;
      lastSearchHeight = barHeight;
      Render2DUtil.rect(barX, barY, barWidth, barHeight).color(0).radius(10.0F * scale + grow * 0.5F).border(Math.max(0.5F, 1.0F * scale), borderColor).draw();
      float paddingX = 12.0F * scale;
      float labelSize = 14.0F * scale;
      float textY = UiFonts.sfProDisplay().centeredTextY(barY + barHeight / 2.0F, labelSize);
      boolean empty = searchQuery.length() == 0;
      String shown = empty ? "search" : searchQuery.toString();
      if (searchFocused && caretVisible()) {
         shown = empty ? "|" : shown + "|";
      }

      Render2DUtil.text(barX + paddingX, textY, labelSize, shown).style(UiFontStyle.MEDIUM).color(empty && !searchFocused ? dimColor : -1).draw();
      float iconSize = 14.0F * scale;
      float iconX = barX + barWidth - paddingX - iconSize;
      float iconY = barY + (barHeight - iconSize) / 2.0F;
      Render2DUtil.texture(iconX, iconY, iconSize, iconSize, Textures.Header.SEARCH).color(mixColor(dimColor, -1, focusT)).draw();
   }

   private static void renderSearchSuggestions(float panelX, float panelY, float scale, float panelHeight) {
      lastSuggestionFeatures.clear();
      if (searchFocused) {
         List<Feature> matches = searchMatches();
         if (!matches.isEmpty()) {
            float rowH = 30.0F * scale;
            float width = lastSearchWidth;
            float x = lastSearchX;
            float height = matches.size() * rowH;
            float y = lastSearchY + lastSearchHeight + 6.0F * scale;
            float radius = 10.0F * scale;
            float appear = Motion.ease(Motion.Ease.OUT_EXPO, MOTION.progress("searchSuggest", 0.0F, 0.22F));
            Render2DUtil.pushScissor(x, y, width, height * appear + 1.0F);
            Render2DUtil.rect(x, y, width, height)
               .color(ColorUtil.withAlpha(MENU_PANEL, 0.95F * appear))
               .radius(radius)
               .border(Math.max(0.5F, 1.0F * scale), Theme.Colors.PANEL_BORDER)
               .draw();
            lastSuggestionX = x;
            lastSuggestionY = y;
            lastSuggestionWidth = width;
            lastSuggestionRowHeight = rowH;
            float paddingX = 12.0F * scale;
            float labelSize = 14.0F * scale;
            float catSize = labelSize * 0.82F;
            int accent = Theme.getAccent();

            for (int i = 0; i < matches.size(); i++) {
               Feature feature = matches.get(i);
               float rowY = y + i * rowH;
               float rowT = Motion.ease(Motion.Ease.OUT_QUINT, MOTION.progress("searchSuggest", 0.05F + i * 0.03F, 0.22F));
               float rowSlide = (1.0F - rowT) * 8.0F * scale;
               boolean hovered = insideRect(hoverMouseX, hoverMouseY, x, rowY, width, rowH);
               float hoverT = MOTION.spring(subKey(feature, "sugg"), hovered ? 1.0F : 0.0F, SPEED_HOVER);
               if (hoverT > 0.001F) {
                  Render2DUtil.rect(x + 2.0F * scale, rowY + 2.0F * scale, width - 4.0F * scale, rowH - 4.0F * scale)
                     .color(ColorUtil.withAlpha(accent, 0.15F * hoverT))
                     .radius(radius - 3.0F * scale)
                     .draw();
               }

               float textY = UiFonts.sfProDisplay().centeredTextY(rowY + rowH / 2.0F, labelSize);
               Render2DUtil.text(x + paddingX + rowSlide, textY, labelSize, feature.getName())
                  .style(UiFontStyle.MEDIUM)
                  .color(ColorUtil.multiplyAlpha(hovered ? -1 : TEXT_ROW, rowT))
                  .draw();
               Render2DUtil.text(
                     x + width - paddingX + rowSlide,
                     UiFonts.sfProDisplay().centeredTextY(rowY + rowH / 2.0F, catSize),
                     catSize,
                     feature.getCategory().getDisplayName()
                  )
                  .style(UiFontStyle.MEDIUM)
                  .align(TextAlign.RIGHT)
                  .color(ColorUtil.multiplyAlpha(TEXT_MUTED, rowT))
                  .draw();
               lastSuggestionFeatures.add(feature);
            }

            Render2DUtil.popScissor();
         }
      }
   }

   private static void selectFromSearch(Feature feature) {
      selectedCategory = feature.getCategory();
      selectedSubcategory = hasSubcategories() ? subcategoryOf(feature.getCategory(), feature.getName()) : "All";
      highlightedFeature = feature;
      highlightUntilNanos = System.nanoTime() + 3000000000L;
      searchQuery.setLength(0);
      searchFocused = false;
      scrollToFeature(feature);
   }

   private static void scrollToFeature(Feature feature) {
      float scale = lastScale;
      float panelHeight = 663.0F * scale;
      float contentWidth = 817.0F * scale;
      MenuOverlay.ModuleSlot target = null;

      for (MenuOverlay.ModuleSlot slot : layoutModules(0.0F, 0.0F, contentWidth, scale)) {
         if (slot.feature() == feature) {
            target = slot;
            break;
         }
      }

      if (target == null) {
         moduleScrollOffset = 0.0F;
      } else {
         float available = panelHeight - moduleGridTop(0.0F, scale) - 20.0F * scale;
         float scroll = target.y() - (available - target.cardHeight()) / 2.0F;
         moduleScrollOffset = MathUtil.clamp(scroll, 0.0F, moduleScrollMax(scale, panelHeight));
         moduleScrollVelocity = 0.0F;
      }
   }

   private static float highlightIntensity() {
      if (highlightedFeature == null) {
         return 0.0F;
      } else {
         long now = System.nanoTime();
         if (now < highlightUntilNanos && highlightedFeature.getCategory() == selectedCategory) {
            float remaining = (float)(highlightUntilNanos - now) / 1.0E9F;
            return MathUtil.clamp(remaining / 0.6F, 0.0F, 1.0F);
         } else {
            highlightedFeature = null;
            return 0.0F;
         }
      }
   }

   private static int scaleAlpha(int color, float factor) {
      float alpha = ColorUtil.alpha(color) / 255.0F * factor;
      return ColorUtil.withAlpha(color, MathUtil.clamp01(alpha));
   }

   private static List<Feature> visibleFeatures() {
      List<Feature> visible = new ArrayList<>();

      for (Feature feature : FeatureManager.INSTANCE.getFeatures(selectedCategory)) {
         if (feature.isVisible()
            && (!hasSubcategories() || "All".equals(selectedSubcategory) || selectedSubcategory.equals(subcategoryOf(feature.getCategory(), feature.getName())))
            )
          {
            visible.add(feature);
         }
      }

      return visible;
   }

   private static List<Feature> searchMatches() {
      List<Feature> matches = new ArrayList<>();
      String query = searchQuery.toString().trim().toLowerCase(Locale.ROOT);
      if (query.isEmpty()) {
         return matches;
      }

      for (Feature feature : FeatureManager.INSTANCE.getFeatures()) {
         if (feature.isVisible() && feature.getName().toLowerCase(Locale.ROOT).contains(query)) {
            matches.add(feature);
         }
      }

      matches.sort((a, b) -> {
         boolean pa = a.getName().toLowerCase(Locale.ROOT).startsWith(query);
         boolean pb = b.getName().toLowerCase(Locale.ROOT).startsWith(query);
         if (pa != pb) {
            return pa ? -1 : 1;
         } else {
            return a.getName().compareToIgnoreCase(b.getName());
         }
      });
      return matches.size() > 6 ? new ArrayList<>(matches.subList(0, 6)) : matches;
   }

   private static float settingsBlockHeight(Feature feature, float blockWidth, float scale) {
      List<Setting<?>> settings = visibleSettings(feature);
      if (settings.isEmpty()) {
         return 0.0F;
      }

      float rowWidth = blockWidth - 28.0F * scale;
      float rowsHeight = 0.0F;

      for (Setting<?> setting : settings) {
         rowsHeight += settingRowHeight(setting, rowWidth, scale) + 7.0F * scale;
      }

      rowsHeight = Math.max(0.0F, rowsHeight - 7.0F * scale);
      return Math.max(0.5F, 1.0F * scale) + 24.0F * scale + rowsHeight;
   }

   private static List<Setting<?>> visibleSettings(Feature feature) {
      List<Setting<?>> settings = new ArrayList<>();

      for (Setting<?> setting : feature.getSettings()) {
         if (setting.isVisible() && isSupportedSetting(setting)) {
            settings.add(setting);
         }
      }

      return settings;
   }

   private static List<MenuOverlay.ModuleSlot> layoutModules(float contentX, float contentY, float contentWidth, float scale) {
      float gapX = 14.0F * scale;
      float gapY = 14.0F * scale;
      float cardWidth = (contentWidth - gapX * 1.0F) / 2.0F;
      float cardHeight = 42.0F * scale;
      List<MenuOverlay.ModuleSlot> slots = new ArrayList<>();
      float[] columnY = new float[2];
      Arrays.fill(columnY, contentY);

      for (Feature feature : visibleFeatures()) {
         int column = 0;

         for (int c = 1; c < 2; c++) {
            if (columnY[c] < columnY[column]) {
               column = c;
            }
         }

         float slotX = contentX + column * (cardWidth + gapX);
         float slotHeight = cardHeight + settingsBlockHeight(feature, cardWidth, scale);
         slots.add(new MenuOverlay.ModuleSlot(feature, slotX, columnY[column], cardWidth, slotHeight, cardHeight));
         columnY[column] += slotHeight + gapY;
      }

      return slots;
   }

   private static float moduleContentHeight(float scale) {
      float contentWidth = 817.0F * scale;
      List<MenuOverlay.ModuleSlot> slots = layoutModules(0.0F, 0.0F, contentWidth, scale);
      if (slots.isEmpty()) {
         return 0.0F;
      }

      MenuOverlay.ModuleSlot last = slots.get(slots.size() - 1);
      return last.y() + last.height();
   }

   private static float moduleScrollMax(float scale, float panelHeight) {
      float available = panelHeight - moduleGridTop(0.0F, scale) - 20.0F * scale;
      return Math.max(0.0F, moduleContentHeight(scale) - available + 64.0F * scale);
   }

   private static void renderModules(GuiGraphicsExtractor guiGraphicsExtractor, float panelX, float panelY, float scale, float panelHeight, float openT) {
      float contentAreaX = panelX + 195.0F * scale;
      float contentAreaWidth = 1052.0F * scale - 195.0F * scale;
      float contentX = contentAreaX + 20.0F * scale;
      float contentY = moduleGridTop(panelY, scale) - moduleScrollShown;
      float contentWidth = contentAreaWidth - 20.0F * scale * 2.0F;
      float gridTop = moduleGridTop(panelY, scale);
      Render2DUtil.pushScissor(contentAreaX, gridTop, contentAreaWidth, panelY + panelHeight - gridTop);
      float highlight = highlightIntensity();
      float staggerElapsed = (float)(System.nanoTime() - dealStartNanos) / 1.0E9F;
      boolean staggering = staggerElapsed < 1.6199999F;
      rowSink = lastSettingsRows;
      lastSettingsRows.clear();
      List<MenuOverlay.SettingRow> flyoutRows = new ArrayList<>();
      List<MenuOverlay.ModuleSlot> slots = layoutModules(contentX, contentY, contentWidth, scale);

      for (int index = 0; index < slots.size(); index++) {
         MenuOverlay.ModuleSlot slot = slots.get(index);
         Feature feature = slot.feature();
         float cardX = slot.x();
         float cardY = slot.y();
         float slotWidth = slot.width();
         boolean capturing = feature == capturingBindFeature;
         boolean hovered = insideRect(hoverMouseX, hoverMouseY, cardX, cardY, slotWidth, slot.cardHeight())
            && hoverMouseY >= panelY
            && hoverMouseY <= panelY + panelHeight;
         float hoverT = MOTION.spring(hoverKey(feature), hovered ? 1.0F : 0.0F, SPEED_HOVER);
         float pressT = MOTION.spring(subKey(feature, "press"), feature == pressedCard ? 1.0F : 0.0F, SPEED_PRESS);
         float appearT = 1.0F;
         if (staggering) {
            appearT = Motion.ease(Motion.Ease.OUT_EXPO, MathUtil.clamp01((staggerElapsed - index * 0.03F) / 0.42F));
         }

         float cardScale = (1.0F - 0.029999971F * pressT) * (0.94F + 0.06F * appearT);
         float rise = (1.0F - appearT) * 22.0F * scale;
         float magnetX = hovered ? (hoverMouseX - (cardX + slotWidth / 2.0F)) / slotWidth * 3.0F * scale * hoverT : 0.0F;
         float magnetY = hovered ? (hoverMouseY - (cardY + slot.cardHeight() / 2.0F)) / slot.cardHeight() * 2.0F * scale * hoverT : 0.0F;
         float cardAlpha = appearT * openT;
         Matrix3x2fStack cardPose = guiGraphicsExtractor.pose();
         cardPose.pushMatrix();
         float cardMidX = cardX + slotWidth / 2.0F;
         float cardMidY = cardY + slot.cardHeight() / 2.0F;
         cardPose.translate(cardMidX + magnetX, cardMidY + magnetY);
         cardPose.scale(cardScale, cardScale);
         cardPose.translate(-cardMidX, -cardMidY + rise);
         boolean isHighlighted = highlight > 0.0F && feature == highlightedFeature;
         float dimFactor = highlight > 0.0F && !isHighlighted ? 1.0F - 0.72F * highlight : 1.0F;
         dimFactor *= appearT;
         int borderColor = !capturing && !isHighlighted
            ? mixColor(ColorUtil.withAlpha(-12829629, 0.32F), Theme.getAccent(), hoverT * 0.85F)
            : scaleAlpha(Theme.getAccent(), dimFactor);
         if (dimFactor < 1.0F) {
            borderColor = scaleAlpha(borderColor, dimFactor);
         }

         Render2DUtil.rect(cardX, cardY, slotWidth, slot.height())
            .color(isHighlighted ? ColorUtil.withAlpha(Theme.getAccent(), 0.1F * highlight) : 0)
            .radius(11.0F * scale)
            .border(Math.max(0.5F, 0.5F * scale), borderColor)
            .draw();
         float paddingX = 14.0F * scale;
         float labelSize = 14.0F * scale;
         float textY = UiFonts.sfProDisplay().centeredTextY(cardY + slot.cardHeight() / 2.0F, labelSize);
         int labelColor = dimFactor < 1.0F ? scaleAlpha(TEXT_PRIMARY, dimFactor) : TEXT_PRIMARY;
         Render2DUtil.text(cardX + paddingX, textY, labelSize, feature.getName())
            .style(UiFontStyle.SEMIBOLD)
            .color(ColorUtil.multiplyAlpha(labelColor, cardAlpha))
            .draw();
         float toggleWidth = 40.0F * scale;
         float toggleHeight = 22.0F * scale;
         float toggleX = cardX + slotWidth - paddingX - toggleWidth;
         float toggleY = cardY + (slot.cardHeight() - toggleHeight) / 2.0F;
         float toggleT = MOTION.spring(subKey(feature, "toggle"), feature.isEnabled() ? 1.0F : 0.0F, SPEED_TOGGLE);
         renderToggle(toggleX, toggleY, toggleWidth, toggleHeight, toggleT, MOTION.velocity(subKey(feature, "toggle")), scale, cardAlpha);
         if (feature == pressedCard) {
            float age = (float)(System.nanoTime() - cardPressNanos) / 1.0E9F;
            if (age < 0.5F) {
               float t = age / 0.5F;
               float grow = Motion.ease(Motion.Ease.OUT_QUINT, t) * 14.0F * scale;
               Render2DUtil.rect(cardX - grow, cardY - grow, slotWidth + grow * 2.0F, slot.height() + grow * 2.0F)
                  .color(0)
                  .radius(11.0F * scale + grow)
                  .border(Math.max(0.5F, 1.5F * scale * (1.0F - t)), ColorUtil.withAlpha(Theme.getAccent(), 0.45F * (1.0F - t) * (1.0F - t)))
                  .draw();
            }
         }

         cardPose.popMatrix();
         if (slot.height() > slot.cardHeight()) {
            float padX = 14.0F * scale;
            float dividerThickness = Math.max(0.5F, 1.0F * scale);
            float dividerY = cardY + slot.cardHeight();
            Render2DUtil.rect(cardX + padX, dividerY, slotWidth - padX * 2.0F, dividerThickness).color(ColorUtil.multiplyAlpha(SEPARATOR, cardAlpha)).draw();
            float rowWidth = slotWidth - padX * 2.0F;
            float rowStaggerBase = staggerElapsed - index * 0.03F - 0.23099999F;
            MenuOverlay.SettingRow flyoutRow = renderSettingRows(
               visibleSettings(feature), cardX + padX, dividerY + dividerThickness + 12.0F * scale, rowWidth, 1.0F, true, scale, cardAlpha, rowStaggerBase
            );
            if (flyoutRow != null) {
               flyoutRows.add(flyoutRow);
            }
         }
      }

      for (MenuOverlay.SettingRow flyoutRow : flyoutRows) {
         if (flyoutRow.setting() instanceof ModeSetting modeSetting) {
            renderModeFlyout(modeSetting, flyoutRow, scale);
         } else if (flyoutRow.setting() instanceof ColorSetting colorSetting) {
            renderColorFlyout(colorSetting, flyoutRow, scale);
         }
      }

      float bandTop = gridTop;
      float bandBottom = panelY + panelHeight;
      lastSettingsRows.removeIf(
         r -> r.kind() != MenuOverlay.SettingRowKind.MODE_OPTION
            && r.kind() != MenuOverlay.SettingRowKind.COLOR_SV
            && r.kind() != MenuOverlay.SettingRowKind.COLOR_HUE
            && (r.y() + r.height() / 2.0F < bandTop || r.y() + r.height() / 2.0F > bandBottom)
      );
      if (slots.isEmpty()) {
         boolean soon = selectedCategory == FeatureCategory.COSMETICS || selectedCategory == FeatureCategory.SCRIPTS;
         String message = soon ? "SOON" : "Nothing here yet";
         float messageSize = (soon ? 40.0F : 15.0F) * scale;
         float centerX = contentAreaX + contentAreaWidth / 2.0F;
         float centerY = UiFonts.sfProDisplay().centeredTextY(panelY + panelHeight / 2.0F, messageSize);
         Render2DUtil.text(centerX, centerY, messageSize, message)
            .style(soon ? UiFontStyle.SEMIBOLD : UiFontStyle.MEDIUM)
            .align(TextAlign.CENTER)
            .color(soon ? mixColor(TEXT_MUTED, Theme.getAccent(), 0.35F) : TEXT_MUTED)
            .draw();
      }

      Render2DUtil.popScissor();
      float max = moduleScrollMax(scale, panelHeight);
      if (max > 0.5F) {
         float trackTop = moduleGridTop(panelY, scale);
         float trackHeight = panelY + panelHeight - 20.0F * scale - trackTop;
         float viewport = trackHeight;
         float total = moduleContentHeight(scale);
         float thumbHeight = Math.max(24.0F * scale, viewport * (viewport / total));
         float progress = max > 0.0F ? moduleScrollShown / max : 0.0F;
         float thumbY = trackTop + (trackHeight - thumbHeight) * MathUtil.clamp(progress, 0.0F, 1.0F);
         float barW = 4.0F * scale;
         float barX = panelX + 1052.0F * scale - barW - 5.0F * scale;
         Render2DUtil.rect(barX, thumbY, barW, thumbHeight).color(ColorUtil.withAlpha(-12829629, 0.7F)).radius(barW / 2.0F).draw();
      }
   }

   private static boolean hasSettings(Feature feature) {
      for (Setting<?> setting : feature.getSettings()) {
         if (isSupportedSetting(setting)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isSupportedSetting(Setting<?> setting) {
      return setting instanceof BooleanSetting
         || setting instanceof ModeSetting
         || setting instanceof NumberSetting
         || setting instanceof ColorSetting
         || setting instanceof MultiSelectSetting
         || setting instanceof TextSetting
         || setting instanceof ButtonSetting
         || setting instanceof InputBindSetting;
   }

   private static boolean rowWraps(String label, float rowWidth, float scale) {
      float labelWidth = UiFonts.sfProDisplay().measureWidth(label, 14.0F * scale);
      float available = rowWidth - labelWidth - 10.0F * scale;
      return available < 82.0F * scale;
   }

   private static float settingRowHeight(Setting<?> setting, float rowWidth, float scale) {
      if (setting instanceof NumberSetting) {
         return 33.0F * scale;
      } else if (setting instanceof MultiSelectSetting multiSetting) {
         return multiSelectRowHeight(multiSetting, rowWidth, scale);
      } else if (setting instanceof ModeSetting) {
         boolean wraps = rowWraps(setting.getName(), rowWidth, scale);
         return wraps ? 27.0F * scale + 25.0F * scale : 27.0F * scale;
      } else {
         return 27.0F * scale;
      }
   }

   private static List<List<String>> wrapChipRows(List<String> options, float rowWidth, float scale) {
      List<List<String>> rows = new ArrayList<>();
      List<String> current = new ArrayList<>();
      float lineWidth = 0.0F;

      for (String option : options) {
         float chipWidth = chipWidth(option, scale);
         float needed = current.isEmpty() ? chipWidth : lineWidth + 6.0F * scale + chipWidth;
         if (!current.isEmpty() && needed > rowWidth) {
            rows.add(current);
            current = new ArrayList<>();
            lineWidth = chipWidth;
         } else {
            lineWidth = needed;
         }

         current.add(option);
      }

      if (!current.isEmpty()) {
         rows.add(current);
      }

      return rows;
   }

   private static float chipWidth(String option, float scale) {
      return UiFonts.sfProDisplay().measureWidth(option, 12.0F * scale) + 12.0F * scale * 2.0F;
   }

   private static float multiSelectRowHeight(MultiSelectSetting multiSetting, float rowWidth, float scale) {
      List<List<String>> rows = wrapChipRows(multiSetting.getOptions(), rowWidth, scale);
      float chipHeight = 24.0F * scale;
      float chipsHeight = rows.size() * chipHeight + Math.max(0, rows.size() - 1) * 8.0F * scale;
      return 18.0F * scale + 8.0F * scale + chipsHeight;
   }

   private static void renderMultiChips(
      float rowX, float rowY, float rowWidth, MultiSelectSetting multiSetting, boolean interactive, float scale, float appearT
   ) {
      float labelHeight = 18.0F * scale;
      float labelY = UiFonts.sfProDisplay().centeredTextY(rowY + labelHeight / 2.0F, 14.0F * scale);
      Render2DUtil.text(rowX, labelY, 14.0F * scale, multiSetting.getName()).style(UiFontStyle.MEDIUM).color(ColorUtil.multiplyAlpha(TEXT_ROW, appearT)).draw();
      List<String> options = multiSetting.getOptions();
      String counter = multiSetting.getValue().size() + "/" + options.size();
      float counterSize = 12.0F * scale;
      float counterY = UiFonts.sfProDisplay().centeredTextY(rowY + labelHeight / 2.0F, counterSize);
      Render2DUtil.text(rowX + rowWidth, counterY, counterSize, counter)
         .style(UiFontStyle.SEMIBOLD)
         .color(ColorUtil.multiplyAlpha(Theme.getAccent(), appearT))
         .align(TextAlign.RIGHT)
         .draw();
      float chipHeight = 24.0F * scale;
      float cursorY = rowY + labelHeight + 8.0F * scale;

      for (List<String> line : wrapChipRows(options, rowWidth, scale)) {
         float cursorX = rowX;

         for (String option : line) {
            float width = chipWidth(option, scale);
            boolean selected = multiSetting.isSelected(option);
            boolean hovered = insideRect(hoverMouseX, hoverMouseY, cursorX, cursorY, width, chipHeight);
            int bg = selected ? Theme.getAccent() : ColorUtil.withAlpha(-12303028, hovered ? 1.0F : 0.85F);
            int textColor = selected ? -1 : (hovered ? TEXT_ROW : TEXT_MUTED);
            Render2DUtil.rect(cursorX, cursorY, width, chipHeight).color(ColorUtil.multiplyAlpha(bg, appearT)).radius(chipHeight / 2.0F).draw();
            float textY = UiFonts.sfProDisplay().centeredTextY(cursorY + chipHeight / 2.0F, 12.0F * scale);
            Render2DUtil.text(cursorX + width / 2.0F, textY, 12.0F * scale, option)
               .style(UiFontStyle.MEDIUM)
               .color(ColorUtil.multiplyAlpha(textColor, appearT))
               .align(TextAlign.CENTER)
               .draw();
            if (interactive) {
               rowSink.add(new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.MULTI_OPTION, multiSetting, option, -1, cursorX, cursorY, width, chipHeight));
            }

            cursorX += width + 6.0F * scale;
         }

         cursorY += chipHeight + 8.0F * scale;
      }
   }

   private static MenuOverlay.SettingRow renderSettingRows(
      List<Setting<?>> settings,
      float rowX,
      float startY,
      float rowWidth,
      float advancedOpen,
      boolean interactive,
      float scale,
      float baseAppear,
      float rowStaggerBase
   ) {
      MenuOverlay.SettingRow openFlyoutRow = null;
      float cursorY = startY;

      for (int s = 0; s < settings.size(); s++) {
         Setting<?> setting = settings.get(s);
         float rowHeight = settingRowHeight(setting, rowWidth, scale);
         boolean advancedRow = setting.isAdvancedOnly();
         float openFactor = advancedRow ? advancedOpen : 1.0F;
         float effectiveHeight = rowHeight * openFactor;
         float rowY = cursorY;
         boolean rowInteractive = interactive && (!advancedRow || advancedOpen > 0.5F);
         boolean rowCollapsed = advancedRow && effectiveHeight < 0.5F * scale;
         float appearT = baseAppear * Motion.ease(Motion.Ease.OUT_EXPO, MathUtil.clamp01((rowStaggerBase - s * 0.022F) / 0.3F));
         if (!rowCollapsed) {
            if (s > 0) {
               Render2DUtil.rect(rowX, rowY - 7.0F * scale / 2.0F, rowWidth, Math.max(0.5F, 1.0F * scale))
                  .color(ColorUtil.multiplyAlpha(SEPARATOR, appearT))
                  .draw();
            }

            if (advancedRow) {
               float slack = 6.0F * scale;
               Render2DUtil.pushScissor(rowX - slack, rowY, rowWidth + slack * 2.0F, Math.max(0.0F, effectiveHeight));
            }

            if (setting instanceof BooleanSetting boolSetting) {
               renderSettingsLabel(rowX, rowY, rowHeight, setting.getName(), TEXT_ROW, scale, appearT);
               if (boolSetting.isExpander()) {
                  renderExpanderDots(rowX, rowY, rowWidth, rowHeight, advancedOpen, scale, appearT);
               } else {
                  float toggleWidth = 40.0F * scale;
                  float toggleHeight = 22.0F * scale;
                  float toggleX = rowX + rowWidth - toggleWidth;
                  float toggleY = rowY + (rowHeight - toggleHeight) / 2.0F;
                  Object toggleKey = subKey(setting, "toggle");
                  float toggleT = MOTION.spring(toggleKey, boolSetting.getValue() ? 1.0F : 0.0F, SPEED_TOGGLE);
                  renderToggle(toggleX, toggleY, toggleWidth, toggleHeight, toggleT, MOTION.velocity(toggleKey), scale, appearT);
               }

               if (rowInteractive) {
                  lastSettingsRows.add(new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.CHECKBOX, setting, rowX, rowY, rowWidth, rowHeight));
               }
            } else if (setting instanceof ModeSetting modeSetting) {
               boolean wraps = rowWraps(setting.getName(), rowWidth, scale);
               renderSettingsLabel(rowX, rowY, wraps ? 27.0F * scale : rowHeight, setting.getName(), TEXT_ROW, scale, appearT);
               MenuOverlay.SettingRow row = renderDropdownPill(
                  rowX, rowY, rowWidth, rowHeight, wraps, modeSetting, modeSetting.getValue(), MenuOverlay.SettingRowKind.MODE_TOGGLE, scale, appearT
               );
               if (rowInteractive) {
                  lastSettingsRows.add(row);
               }

               if (openDropdown == modeSetting) {
                  openFlyoutRow = row;
               }
            } else if (setting instanceof MultiSelectSetting multiSetting) {
               renderMultiChips(rowX, rowY, rowWidth, multiSetting, rowInteractive, scale, appearT);
            } else if (setting instanceof NumberSetting numberSetting) {
               float labelY = UiFonts.sfProDisplay().centeredTextY(rowY + 12.0F * scale / 2.0F, 14.0F * scale);
               Render2DUtil.text(rowX, labelY, 14.0F * scale, setting.getName())
                  .style(UiFontStyle.MEDIUM)
                  .color(ColorUtil.multiplyAlpha(TEXT_ROW, appearT))
                  .draw();
               String valueText = NumberSetting.format(numberSetting.getValue()) + " / " + NumberSetting.format(numberSetting.getMax());
               Render2DUtil.text(rowX + rowWidth, labelY, 12.0F * scale, valueText)
                  .style(UiFontStyle.MEDIUM)
                  .color(ColorUtil.multiplyAlpha(TEXT_MUTED, appearT))
                  .align(TextAlign.RIGHT)
                  .draw();
               float trackHeight = 4.0F * scale;
               float knobSize = 11.0F * scale;
               float trackY = rowY + rowHeight - knobSize / 2.0F - trackHeight / 2.0F;
               float trackCenterY = trackY + trackHeight / 2.0F;
               boolean sliderHot = draggingSlider == setting;
               Render2DUtil.rect(rowX, trackY, rowWidth, trackHeight)
                  .color(ColorUtil.multiplyAlpha(ColorUtil.withAlpha(-12303028, 0.9F), appearT))
                  .radius(trackHeight / 2.0F)
                  .draw();
               float progress = MOTION.spring(subKey(setting, "slider"), (float)numberSetting.getProgress(), sliderHot ? SLIDER_GRAB : SPEED_TOGGLE);
               if (progress > 0.001F) {
                  Render2DUtil.rect(rowX, trackY, Math.max(trackHeight, rowWidth * progress), trackHeight)
                     .color(ColorUtil.multiplyAlpha(Theme.getAccent(), appearT))
                     .radius(trackHeight / 2.0F)
                     .draw();
               }

               float knobX = MathUtil.clamp(rowX + rowWidth * progress - knobSize / 2.0F, rowX - knobSize * 0.15F, rowX + rowWidth - knobSize * 0.85F);
               float knobY = trackCenterY - knobSize / 2.0F;
               float knobScale = sliderHot ? 1.12F : 1.0F;
               float grown = knobSize * MOTION.spring(subKey(setting, "pill"), knobScale, SPEED_HOVER);
               Render2DUtil.rect(knobX - (grown - knobSize) / 2.0F, knobY - (grown - knobSize) / 2.0F, grown, grown)
                  .color(ColorUtil.multiplyAlpha(-1, appearT))
                  .radius(grown / 2.0F)
                  .draw();
               if (rowInteractive) {
                  float trackHitPadding = knobSize / 2.0F + 3.0F * scale;
                  lastSettingsRows.add(
                     new MenuOverlay.SettingRow(
                        MenuOverlay.SettingRowKind.SLIDER, setting, null, -1, rowX, trackCenterY - trackHitPadding, rowWidth, trackHitPadding * 2.0F
                     )
                  );
               }
            } else if (setting instanceof ColorSetting colorSetting) {
               renderSettingsLabel(rowX, rowY, rowHeight, setting.getName(), TEXT_ROW, scale, appearT);
               float swatchWidth = 34.0F * scale;
               float swatchHeight = 20.0F * scale;
               float swatchX = rowX + rowWidth - swatchWidth;
               float swatchY = rowY + (rowHeight - swatchHeight) / 2.0F;
               Render2DUtil.rect(swatchX, swatchY, swatchWidth, swatchHeight)
                  .color(ColorUtil.multiplyAlpha(colorSetting.getValue(), appearT))
                  .radius(7.0F * scale)
                  .draw();
               MenuOverlay.SettingRow row = new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.COLOR_TOGGLE, setting, rowX, rowY, rowWidth, rowHeight);
               if (rowInteractive) {
                  lastSettingsRows.add(row);
               }

               if (openDropdown == colorSetting) {
                  openFlyoutRow = row;
               }
            } else if (setting instanceof TextSetting textSetting) {
               renderSettingsLabel(rowX, rowY, rowHeight, setting.getName(), TEXT_ROW, scale, appearT);
               float fieldWidth = rowWidth * 0.5F;
               float fieldHeight = 23.0F * scale;
               float fieldX = rowX + rowWidth - fieldWidth;
               float fieldY = rowY + (rowHeight - fieldHeight) / 2.0F;
               boolean focused = focusedTextSetting == textSetting;
               float focusT = MOTION.spring(subKey(textSetting, "focus"), focused ? 1.0F : 0.0F, SPEED_COLOR);
               Render2DUtil.rect(fieldX, fieldY, fieldWidth, fieldHeight)
                  .color(0)
                  .radius(7.0F * scale)
                  .border(
                     Math.max(0.5F, 1.0F * scale), ColorUtil.multiplyAlpha(mixColor(ColorUtil.withAlpha(-12829629, 0.7F), Theme.getAccent(), focusT), appearT)
                  )
                  .draw();
               String value = textSetting.isSecret() ? "*".repeat(textSetting.getValue().length()) : textSetting.getValue();
               if (focused && caretVisible()) {
                  value = value + "|";
               }

               float fieldTextY = UiFonts.sfProDisplay().centeredTextY(fieldY + fieldHeight / 2.0F, 12.0F * scale);
               Render2DUtil.text(fieldX + 10.0F * scale, fieldTextY, 12.0F * scale, value)
                  .style(UiFontStyle.MEDIUM)
                  .color(ColorUtil.multiplyAlpha(TEXT_PRIMARY, appearT))
                  .draw();
               if (rowInteractive) {
                  lastSettingsRows.add(new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.TEXT_FIELD, setting, fieldX, fieldY, fieldWidth, fieldHeight));
               }
            } else if (setting instanceof ButtonSetting buttonSetting) {
               float buttonHeight = 23.0F * scale;
               float buttonWidth = Math.max(
                  82.0F * scale, UiFonts.sfProDisplay().measureWidth(buttonSetting.getButtonLabel(), 12.0F * scale) + 12.0F * scale * 2.0F
               );
               float buttonX = rowX + rowWidth - buttonWidth;
               float buttonY = rowY + (rowHeight - buttonHeight) / 2.0F;
               boolean hovered = insideRect(hoverMouseX, hoverMouseY, buttonX, buttonY, buttonWidth, buttonHeight);
               float hoverT = MOTION.spring(hoverKey(buttonSetting), hovered ? 1.0F : 0.0F, SPEED_HOVER);
               Render2DUtil.rect(buttonX, buttonY, buttonWidth, buttonHeight)
                  .color(ColorUtil.multiplyAlpha(mixColor(Theme.getAccent(), -1, hoverT * 0.18F), appearT))
                  .radius(7.0F * scale)
                  .draw();
               float buttonTextY = UiFonts.sfProDisplay().centeredTextY(buttonY + buttonHeight / 2.0F, 12.0F * scale);
               Render2DUtil.text(buttonX + buttonWidth / 2.0F, buttonTextY, 12.0F * scale, buttonSetting.getButtonLabel())
                  .style(UiFontStyle.MEDIUM)
                  .align(TextAlign.CENTER)
                  .color(ColorUtil.multiplyAlpha(-1, appearT))
                  .draw();
               if (rowInteractive) {
                  lastSettingsRows.add(new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.BUTTON, setting, buttonX, buttonY, buttonWidth, buttonHeight));
               }
            } else if (setting instanceof InputBindSetting bindSetting) {
               renderSettingsLabel(rowX, rowY, rowHeight, setting.getName(), TEXT_ROW, scale, appearT);
               boolean capturing = capturingBindInput == bindSetting;
               String bindText = capturing ? "..." : bindSetting.getDisplayValue().toUpperCase(Locale.ROOT);
               float bindHeight = 23.0F * scale;
               float bindWidth = Math.max(82.0F * scale * 0.7F, UiFonts.sfProDisplay().measureWidth(bindText, 12.0F * scale) + 10.0F * scale * 2.0F);
               float bindX = rowX + rowWidth - bindWidth;
               float bindY = rowY + (rowHeight - bindHeight) / 2.0F;
               Render2DUtil.rect(bindX, bindY, bindWidth, bindHeight)
                  .color(0)
                  .radius(7.0F * scale)
                  .border(Math.max(0.5F, 1.0F * scale), ColorUtil.multiplyAlpha(capturing ? Theme.getAccent() : ColorUtil.withAlpha(-12829629, 0.85F), appearT))
                  .draw();
               float bindTextY = UiFonts.sfProDisplay().centeredTextY(bindY + bindHeight / 2.0F, 12.0F * scale);
               Render2DUtil.text(bindX + bindWidth / 2.0F, bindTextY, 12.0F * scale, bindText)
                  .style(UiFontStyle.MEDIUM)
                  .align(TextAlign.CENTER)
                  .color(ColorUtil.multiplyAlpha(capturing ? Theme.getAccent() : TEXT_PRIMARY, appearT))
                  .draw();
               if (rowInteractive) {
                  lastSettingsRows.add(new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.BIND_FIELD, setting, bindX, bindY, bindWidth, bindHeight));
               }
            }

            if (advancedRow) {
               Render2DUtil.popScissor();
            }
         }

         cursorY += (rowHeight + 7.0F * scale) * openFactor;
      }

      return openFlyoutRow;
   }

   private static MenuOverlay.SettingRow renderDropdownPill(
      float rowX,
      float rowY,
      float rowWidth,
      float rowHeight,
      boolean wraps,
      Setting<?> setting,
      String valueText,
      MenuOverlay.SettingRowKind kind,
      float scale,
      float appearT
   ) {
      float pillHeight = 23.0F * scale;
      float measured = UiFonts.sfProDisplay().measureWidth(valueText, 12.0F * scale) + 10.0F * scale * 2.0F;
      float minWidth = 82.0F * scale;
      float maxWidth = wraps ? rowWidth : rowWidth * 0.55F;
      float pillWidth = MathUtil.clamp(Math.max(measured, minWidth), minWidth, Math.max(minWidth, maxWidth));
      float openT = MOTION.spring(subKey(setting, "pill"), openDropdown == setting ? 1.0F : 0.0F, SPEED_COLOR);
      float pillX = rowX + rowWidth - pillWidth;
      float pillY = wraps ? rowY + 27.0F * scale : rowY + (rowHeight - pillHeight) / 2.0F;
      Render2DUtil.rect(pillX, pillY, pillWidth, pillHeight)
         .color(0)
         .radius(7.0F * scale)
         .border(Math.max(0.5F, 1.0F * scale), ColorUtil.multiplyAlpha(mixColor(ColorUtil.withAlpha(-12829629, 0.85F), Theme.getAccent(), openT), appearT))
         .draw();
      float pillTextY = UiFonts.sfProDisplay().centeredTextY(pillY + pillHeight / 2.0F, 12.0F * scale);
      Render2DUtil.text(pillX + 10.0F * scale, pillTextY, 12.0F * scale, valueText)
         .style(UiFontStyle.MEDIUM)
         .color(ColorUtil.multiplyAlpha(TEXT_PRIMARY, appearT))
         .draw();
      return new MenuOverlay.SettingRow(kind, setting, rowX, rowY, rowWidth, rowHeight);
   }

   private static void renderExpanderDots(float rowX, float rowY, float rowWidth, float rowHeight, float openT, float scale, float appearT) {
      float size = 22.0F * scale;
      float pillX = rowX + rowWidth - size;
      float pillY = rowY + (rowHeight - size) / 2.0F;
      Render2DUtil.rect(pillX, pillY, size, size)
         .color(ColorUtil.multiplyAlpha(mixColor(-12303028, Theme.getAccent(), openT), appearT))
         .radius(size / 2.0F)
         .draw();
      float centerX = pillX + size / 2.0F;
      float centerY = pillY + size / 2.0F;
      float spacing = size * 0.22F;
      float dotSize = Math.max(1.6F * scale, size * 0.12F);

      for (int i = -1; i <= 1; i++) {
         float dx = centerX + spacing * i * (1.0F - openT);
         float dy = centerY + spacing * i * openT;
         Render2DUtil.rect(dx - dotSize / 2.0F, dy - dotSize / 2.0F, dotSize, dotSize)
            .color(ColorUtil.multiplyAlpha(-1, appearT))
            .radius(dotSize / 2.0F)
            .draw();
      }
   }

   private static void renderSettingsLabel(float rowX, float rowY, float rowHeight, String name, int color, float scale, float appearT) {
      float labelY = UiFonts.sfProDisplay().centeredTextY(rowY + rowHeight / 2.0F, 14.0F * scale);
      Render2DUtil.text(rowX, labelY, 14.0F * scale, name).style(UiFontStyle.MEDIUM).color(ColorUtil.multiplyAlpha(color, appearT)).draw();
   }

   private static void renderToggle(float x, float y, float width, float height, float t, float velocity, float scale, float appearT) {
      Render2DUtil.rect(x, y, width, height)
         .color(ColorUtil.multiplyAlpha(mixColor(-12303028, Theme.getAccent(), MathUtil.clamp01(t)), appearT))
         .radius(height / 2.0F)
         .draw();
      float knobPadding = 2.0F * scale;
      float knobSize = height - knobPadding * 2.0F;
      float offX = x + knobPadding;
      float onX = x + width - knobSize - knobPadding;
      float knobX = offX + (onX - offX) * t;
      float knobY = y + knobPadding;
      float stretch = MathUtil.clamp(Math.abs(velocity) * 0.1F, 0.0F, 0.45F);
      float knobW = knobSize * (1.0F + stretch);
      float knobH = knobSize * (1.0F - stretch * 0.55F);
      Render2DUtil.rect(knobX + (knobSize - knobW) / 2.0F, knobY + (knobSize - knobH) / 2.0F, knobW, knobH)
         .color(ColorUtil.multiplyAlpha(-1, appearT))
         .radius(knobH / 2.0F)
         .draw();
   }

   private static void renderModeFlyout(ModeSetting modeSetting, MenuOverlay.SettingRow anchorRow, float scale) {
      List<String> options = modeSetting.getVisibleModes();
      float optionHeight = 24.0F * scale;
      float fullHeight = optionHeight * options.size();
      float openT = MOTION.spring(subKey(modeSetting, "flyout"), 1.0F, SPEED_FLYOUT);
      float flyoutY = anchorRow.y() + anchorRow.height() + 4.0F * scale;
      float flyoutX = anchorRow.x();
      float flyoutWidth = anchorRow.width();
      Render2DUtil.pushScissor(flyoutX, flyoutY, flyoutWidth, fullHeight * openT + 1.0F);
      Render2DUtil.rect(flyoutX, flyoutY, flyoutWidth, fullHeight)
         .color(ColorUtil.withAlpha(MENU_PANEL, 0.95F))
         .radius(7.0F * scale)
         .border(Math.max(0.5F, 1.0F * scale), HAIRLINE)
         .draw();

      for (int index = 0; index < options.size(); index++) {
         String option = options.get(index);
         float optionY = flyoutY + optionHeight * index;
         boolean selected = modeSetting.is(option);
         boolean hovered = insideRect(hoverMouseX, hoverMouseY, flyoutX, optionY, flyoutWidth, optionHeight);
         int textColor = selected ? TEXT_PRIMARY : mixColor(TEXT_MUTED, TEXT_ROW, hovered ? 1.0F : 0.0F);
         float textY = UiFonts.sfProDisplay().centeredTextY(optionY + optionHeight / 2.0F, 12.0F * scale);
         Render2DUtil.text(flyoutX + 10.0F * scale, textY, 12.0F * scale, option).style(UiFontStyle.MEDIUM).color(textColor).draw();
         rowSink.add(new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.MODE_OPTION, modeSetting, option, -1, flyoutX, optionY, flyoutWidth, optionHeight));
      }

      Render2DUtil.popScissor();
   }

   private static void renderColorFlyout(ColorSetting colorSetting, MenuOverlay.SettingRow anchorRow, float scale) {
      float pad = 8.0F * scale;
      float svSize = 150.0F * scale;
      float hueHeight = 12.0F * scale;
      float gap = 10.0F * scale;
      float boxWidth = svSize + pad * 2.0F;
      float boxHeight = pad * 2.0F + svSize + gap + hueHeight;
      float boxX = anchorRow.x() + anchorRow.width() - boxWidth;
      float boxY = anchorRow.y() + anchorRow.height() + 4.0F * scale;
      float openT = MOTION.spring(subKey(colorSetting, "flyout"), 1.0F, SPEED_FLYOUT);
      float[] hsv = ColorUtil.hsv(colorSetting.getValue());
      int hueColor = ColorUtil.fromHsv(hsv[0], 1.0F, 1.0F, 255);
      float svX = boxX + pad;
      float svY = boxY + pad;
      float hueX = svX;
      float hueY = svY + svSize + gap;
      lastSvX = svX;
      lastSvY = svY;
      lastSvSize = svSize;
      lastHueX = hueX;
      lastHueY = hueY;
      lastHueWidth = svSize;
      lastHueHeight = hueHeight;
      Render2DUtil.pushScissor(boxX, boxY, boxWidth, boxHeight * openT + 1.0F);
      Render2DUtil.rect(boxX, boxY, boxWidth, boxHeight)
         .color(ColorUtil.withAlpha(MENU_PANEL, 0.95F))
         .radius(7.0F * scale)
         .border(Math.max(0.5F, 1.0F * scale), HAIRLINE)
         .draw();
      float svRadius = 5.0F * scale;
      Render2DUtil.rect(svX, svY, svSize, svSize).color(-1, hueColor, hueColor, -1).radius(svRadius).draw();
      int blackClear = ColorUtil.withAlpha(-16777216, 0);
      Render2DUtil.rect(svX, svY, svSize, svSize).color(blackClear, blackClear, -16777216, -16777216).radius(svRadius).draw();
      float handleR = 5.0F * scale;
      float handleX = svX + hsv[1] * svSize;
      float handleY = svY + (1.0F - hsv[2]) * svSize;
      Render2DUtil.rect(handleX - handleR, handleY - handleR, handleR * 2.0F, handleR * 2.0F)
         .color(0)
         .radius(handleR)
         .border(Math.max(1.0F, 2.0F * scale), -1)
         .draw();
      int segments = HUE_STOPS.length - 1;
      float segWidth = svSize / segments;

      for (int i = 0; i < segments; i++) {
         int c0 = HUE_STOPS[i];
         int c1 = HUE_STOPS[i + 1];
         Render2DUtil.rect(hueX + i * segWidth, hueY, segWidth + 0.5F, hueHeight).color(c0, c1, c1, c0).draw();
      }

      float hueHandleX = hueX + hsv[0] * svSize;
      float hueHandleW = 4.0F * scale;
      Render2DUtil.rect(hueHandleX - hueHandleW / 2.0F, hueY - 2.0F * scale, hueHandleW, hueHeight + 4.0F * scale)
         .color(0)
         .radius(hueHandleW / 2.0F)
         .border(Math.max(1.0F, 2.0F * scale), -1)
         .draw();
      Render2DUtil.popScissor();
      rowSink.add(new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.COLOR_SV, colorSetting, null, -1, svX, svY, svSize, svSize));
      rowSink.add(
         new MenuOverlay.SettingRow(MenuOverlay.SettingRowKind.COLOR_HUE, colorSetting, null, -1, hueX, hueY - 3.0F * scale, svSize, hueHeight + 6.0F * scale)
      );
   }

   private static Feature moduleCardAt(int mouseX, int mouseY) {
      float panelHeight = 663.0F * lastScale;
      if (!(mouseY < lastPanelY) && !(mouseY > lastPanelY + panelHeight)) {
         float contentX = lastPanelX + 195.0F * lastScale + 20.0F * lastScale;
         float contentY = moduleGridTop(lastPanelY, lastScale) - moduleScrollShown;
         float contentWidth = 1052.0F * lastScale - 195.0F * lastScale - 20.0F * lastScale * 2.0F;

         for (MenuOverlay.ModuleSlot slot : layoutModules(contentX, contentY, contentWidth, lastScale)) {
            if (insideRect(mouseX, mouseY, slot.x(), slot.y(), slot.width(), slot.cardHeight())) {
               return slot.feature();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String subcategoryStripAt(float panelX, float panelY, float scale, int mouseX, int mouseY) {
      String[] subcategories = subcategoriesFor(selectedCategory);
      if (subcategories == null) {
         return null;
      }

      float margin = 12.0F * scale;
      float rowX = panelX + margin;
      float rowWidth = 195.0F * scale - margin * 2.0F;
      float rowHeight = 30.0F * scale;
      if (!(mouseX < rowX) && !(mouseX > rowX + rowWidth)) {
         float top = panelY + 12.0F * scale + 46.0F * scale + 16.0F * scale;
         float local = mouseY - top;
         if (local < 0.0F) {
            return null;
         } else {
            int index = (int)(local / (rowHeight + 4.0F * scale));
            if (index < 0 || index >= subcategories.length) {
               return null;
            } else {
               return local - index * (rowHeight + 4.0F * scale) > rowHeight ? null : subcategories[index];
            }
         }
      } else {
         return null;
      }
   }

   private static FeatureCategory categoryStripAt(float panelX, float panelY, float scale, int mouseX, int mouseY) {
      float chipY = panelY + 20.0F * scale;
      float chipHeight = 38.0F * scale;
      if (!(mouseY < chipY) && !(mouseY > chipY + chipHeight)) {
         float contentX = panelX + 195.0F * scale + 20.0F * scale;

         for (MenuOverlay.CategoryChip chip : categoryChips(contentX, scale)) {
            if (mouseX >= chip.x() && mouseX <= chip.x() + chip.width()) {
               return chip.category();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Object hoverKey(Object base) {
      return subKey(base, "hover");
   }

   private static Object subKey(Object base, String tag) {
      return MOTION.sub(base, tag);
   }

   private static int mixColor(int a, int b, float t) {
      t = MathUtil.clamp01(t);
      int ar = ColorUtil.alpha(a);
      int rr = ColorUtil.red(a);
      int gr = ColorUtil.green(a);
      int br = ColorUtil.blue(a);
      int ab = ColorUtil.alpha(b);
      int rb = ColorUtil.red(b);
      int gb = ColorUtil.green(b);
      int bb = ColorUtil.blue(b);
      int alpha = Math.round(ar + (ab - ar) * t);
      int red = Math.round(rr + (rb - rr) * t);
      int green = Math.round(gr + (gb - gr) * t);
      int blue = Math.round(br + (bb - br) * t);
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   private static boolean caretVisible() {
      return System.currentTimeMillis() / 500L % 2L == 0L;
   }

   private static void open(Minecraft minecraft) {
      boolean grabbedMouseBeforeOpen = minecraft.mouseHandler.isMouseGrabbed();
      open = true;
      MOTION.seed("openAnim", 0.0F);
      moduleScrollVelocity = 0.0F;
      ClientSounds.playMenuOpen();
      redeal();
      KeyMapping.releaseAll();
      if (grabbedMouseBeforeOpen) {
         minecraft.mouseHandler.releaseMouse();
      }
   }

   private static boolean shouldReturnMouseToGame(Minecraft minecraft) {
      return minecraft.level != null && minecraft.player != null && minecraft.gui.screen() == null;
   }

   private static int mouseX(Minecraft minecraft) {
      return (int)Math.round(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()));
   }

   private static int mouseY(Minecraft minecraft) {
      return (int)Math.round(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
   }

   static {
      languageSetting.setChangeListener(() -> UiLanguage.set(languageSetting.is("Russian") ? UiLanguage.RUSSIAN : UiLanguage.ENGLISH));
      themeColorSetting.setChangeListener(() -> {
         int color = themeColorSetting.getValue();
         Theme.saveAccentPreset(Theme.CUSTOM_ACCENT_INDEX, color);
         MenuConfigStore.save(data -> data.addProperty("theme.accentColor", color));
      });
      accentModeSetting.setChangeListener(() -> {
         Theme.AccentMode mode = Theme.AccentMode.byLabel(accentModeSetting.getValue());
         Theme.setAccentMode(mode);
         MenuConfigStore.save(data -> data.addProperty("theme.accentMode", mode.label()));
      });
      backgroundLightSetting.setChangeListener(() -> MenuConfigStore.save(data -> data.addProperty("background.light", backgroundLightSetting.getValue())));
      backgroundEmbersSetting.setChangeListener(() -> {
         MenuConfigStore.save(data -> data.addProperty("background.embers", backgroundEmbersSetting.getValue()));
         if (!backgroundEmbersSetting.getValue()) {
            MenuBackgroundFx.releaseEmbers();
         }
      });
      glowSetting.setChangeListener(() -> MenuConfigStore.save(data -> data.addProperty("menu.glow", glowSetting.getValue())));
   }

   private record CategoryChip(FeatureCategory category, float x, float width, float reveal) {
   }

   private record ModuleSlot(Feature feature, float x, float y, float width, float height, float cardHeight) {
   }

   private record SettingRow(
      MenuOverlay.SettingRowKind kind, Setting<?> setting, String optionValue, int optionIndex, float x, float y, float width, float height
   ) {
      SettingRow(MenuOverlay.SettingRowKind kind, Setting<?> setting, float x, float y, float width, float height) {
         this(kind, setting, null, -1, x, y, width, height);
      }
   }

   private enum SettingRowKind {
      CHECKBOX,
      MODE_TOGGLE,
      MODE_OPTION,
      SLIDER,
      COLOR_TOGGLE,
      COLOR_SV,
      COLOR_HUE,
      MULTI_OPTION,
      TEXT_FIELD,
      BUTTON,
      BIND_FIELD;
   }
}
