package win.furryhook.utils.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

public final class Textures {
   private static final List<Identifier> ALL = new ArrayList<>();
   public static final Identifier TARGET = texture("target.png");

   private Textures() {
   }

   public static List<Identifier> all() {
      touch(Textures.Logos.BOOT, Textures.Hud.ARROW_OUTLINE, Textures.Shader.BLOOM, Textures.Header.SEARCH, Textures.Icons.BOXES);
      synchronized (ALL) {
         return List.copyOf(ALL);
      }
   }

   private static void touch(Identifier... constants) {
   }

   private static Identifier svg(String menuPath) {
      return texture("menu/" + menuPath + ".svg");
   }

   private static Identifier texture(String path) {
      return register(Identifier.parse("furryhook:textures/" + path));
   }

   private static Identifier register(Identifier id) {
      synchronized (ALL) {
         ALL.add(id);
         return id;
      }
   }

   public static final class Emotes {
      public static final Identifier WAVING = Textures.texture("emotes/waving.png");
      public static final Identifier CLAP = Textures.texture("emotes/clap.png");
      public static final Identifier POINT = Textures.texture("emotes/point.png");
      public static final Identifier HERE = Textures.texture("emotes/here.png");
      public static final Identifier PALM = Textures.texture("emotes/palm.png");
      public static final Identifier CRYING = Textures.texture("emotes/crying.png");
      public static final Identifier BACKFLIP = Textures.texture("emotes/backflip.png");
      public static final Identifier TWERK = Textures.texture("emotes/twerk.png");
      public static final Identifier KAZOTSKY_KICK = Textures.texture("emotes/kazotsky_kick.png");
      public static final Identifier CLUB_PENGUIN_DANCE = Textures.texture("emotes/club_penguin_dance.png");
      public static final Identifier ROBLOX_POTION_DANCE = Textures.texture("emotes/roblox_potion_dance.png");

      private Emotes() {
      }
   }

   public static final class Header {
      public static final Identifier SEARCH = Textures.svg("header/search");
      public static final Identifier CHEVRON_LEFT = Textures.svg("header/chevron_left");
      public static final Identifier CHEVRON_RIGHT = Textures.svg("header/chevron_right");
      public static final Identifier SETTINGS = Textures.svg("header/settings");
      public static final Identifier FRIENDS = Textures.svg("header/friends");
      public static final Identifier PROFILE_ADD = Textures.svg("header/profile_add");
      public static final Identifier DOCUMENT = Textures.svg("header/document");
      public static final Identifier DEV_AVATAR = Textures.texture("menu/header/dev.png");
      public static final Identifier FURRY_AVATAR = Textures.texture("menu/header/furry.png");

      private Header() {
      }
   }

   public static final class Hud {
      public static final Identifier ARROW_OUTLINE = Textures.texture("hud/arrow.png");
      public static final Identifier ARROW_FILLED = Textures.texture("hud/filled_arrow.png");
      public static final Identifier ICON_TIME = Textures.texture("hud/icons/time.png");
      public static final Identifier ICON_PLAYER = Textures.texture("hud/icons/player.png");
      public static final Identifier ICON_VISUALS = Textures.texture("hud/icons/visuals.png");
      public static final Identifier ICON_MISC = Textures.texture("hud/icons/misc.png");
      public static final Identifier ICON_CLOUD = Textures.texture("hud/icons/cloud.png");
      public static final Identifier ICON_COMPUTER = Textures.texture("hud/icons/computer.png");
      public static final Identifier ICON_WIFI = Textures.texture("hud/icons/wifi.png");
      public static final Identifier ICON_MOVEMENT = Textures.texture("hud/icons/movement.png");
      public static final Identifier ICON_TARGET = Textures.texture("hud/icons/target.png");
      public static final Identifier ICON_SUCCESS = Textures.texture("hud/icons/success.png");
      public static final Identifier ICON_WARNING = Textures.texture("hud/icons/warning.png");
      public static final Identifier ICON_FRAME = Textures.texture("hud/icons/frame.png");
      public static final Identifier ICON_DSF = Textures.texture("hud/icons/dsf.png");
      public static final Identifier ICON_INFO = Textures.texture("hud/icons/info.png");

      private Hud() {
      }
   }

   public static final class Icons {
      public static final Identifier BOXES = Textures.svg("icons/boxes");
      public static final Identifier BRAIN = Textures.svg("icons/brain");
      public static final Identifier CHEVRON_DOWN = Textures.svg("icons/chevron_down");
      public static final Identifier CHEVRONS_LEFT_RIGHT = Textures.svg("icons/chevrons_left_right");
      public static final Identifier CHEVRONS_LEFT_RIGHT_ELLIPSIS = Textures.svg("icons/chevrons_left_right_ellipsis");
      public static final Identifier CIRCLE_PLUS = Textures.svg("icons/circle_plus");
      public static final Identifier COMMAND = Textures.svg("icons/command");
      public static final Identifier DELETE = Textures.svg("icons/delete");
      public static final Identifier DELETE_LEFT = Textures.svg("icons/delete_left");
      public static final Identifier DICES = Textures.svg("icons/dices");
      public static final Identifier DISCORD = Textures.svg("icons/discord");
      public static final Identifier EYE = Textures.svg("icons/eye");
      public static final Identifier GAMEPAD = Textures.svg("icons/gamepad_2");
      public static final Identifier GIFT = Textures.svg("icons/gift");
      public static final Identifier HARD_DRIVE = Textures.svg("icons/hard_drive");
      public static final Identifier KEYBOARD = Textures.svg("icons/keyboard");
      public static final Identifier MOVE_3D = Textures.svg("icons/move_3d");
      public static final Identifier OPTION = Textures.svg("icons/option");
      public static final Identifier PERSON_STANDING = Textures.svg("icons/person_standing");
      public static final Identifier PIN = Textures.svg("icons/pin");
      public static final Identifier PLUS = Textures.svg("icons/plus");
      public static final Identifier REFRESH_CCW = Textures.svg("icons/refresh_ccw");
      public static final Identifier SCAN_HEART = Textures.svg("icons/scan_heart");
      public static final Identifier SPARKLES = Textures.svg("icons/sparkles");
      public static final Identifier SWORDS = Textures.svg("icons/swords");
      public static final Identifier TELEGRAM = Textures.svg("icons/telegram");
      public static final Identifier TRIANGLE_ALERT = Textures.svg("icons/triangle_alert");
      public static final Identifier USER_ROUND = Textures.svg("icons/user_round");
      public static final Identifier USER_ROUND_CHECK = Textures.svg("icons/user_round_check");
      public static final Identifier USER_ROUND_PLUS = Textures.svg("icons/user_round_plus");
      public static final Identifier SLIDERS = Textures.svg("icons/sliders_horizontal");
      public static final Identifier ROTATE_CCW = Textures.svg("icons/rotate_ccw");
      public static final Identifier X = Textures.svg("icons/x");
      public static final Identifier SEARCH = Textures.svg("header/search");
      public static final Identifier ARROW_UP = Textures.svg("icons/arrow_up");
      public static final Identifier COPY = Textures.svg("icons/copy");

      private Icons() {
      }
   }

   public static final class Logos {
      public static final Identifier BOOT = Textures.texture("menu/logo_boot.svg");
      public static final Identifier BOLT = Textures.texture("hud/logo_bolt.svg");

      private Logos() {
      }
   }

   public static final class Shader {
      public static final Identifier BLOOM = Textures.texture("shader/bloom.png");
      public static final Identifier JUMP_FREQUENCY = Textures.texture("shader/jump_frequency.png");

      private Shader() {
      }
   }
}
