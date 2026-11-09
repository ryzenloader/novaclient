package win.furryhook.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;

public final class WatermarkElement extends HudElement {
   public static final String ITEM_PLAYER = "Player";
   public static final String ITEM_FPS = "FPS";
   public static final String ITEM_PING = "Ping";
   public static final String ITEM_SERVER = "Server";
   public static final String[] CONTENT_ITEMS = new String[]{"Player", "FPS", "Ping", "Server"};
   private static final String LABEL_SINGLEPLAYER = "singleplayer";
   private static final String LABEL_REALMS = "realms";
   private static final String DEFAULT_PORT = ":25565";
   private static final String BRAND = "furryhook";
   private static final int ADDRESS_MAX_CHARS = 24;
   private static final float PILL_HEIGHT = 20.0F;
   private static final float PILL_RADIUS = 6.0F;
   private static final float PAD_X = 6.0F;
   private static final float TEXT_SIZE = 7.5F;
   private static final float LOGO_SIZE = 9.0F;
   private static final float ICON_SIZE = 7.0F;
   private static final float ICON_GAP = 2.5F;
   private static final float MAX_PLAYER_WIDTH = 96.0F;
   private static final float DSF_CROP_V0 = 0.0943F;
   private static final float DSF_CROP_V1 = 0.9057F;
   private static final UiFontStyle TEXT_STYLE = UiFontStyle.SEMIBOLD;
   private final AnimatedValue fpsAnim = new AnimatedValue();
   private final AnimatedValue pingAnim = new AnimatedValue();
   private final List<WatermarkElement.StatPill> pills = new ArrayList<>(2);
   private String serverText = "";
   private String playerName = "";
   private float brandWidth;
   private float statsWidth;

   public WatermarkElement() {
      super("watermark", "Watermark");
   }

   @Override
   protected float defaultX(float unit) {
      return 6.0F * unit;
   }

   @Override
   protected float defaultY(float unit) {
      return 6.0F * unit;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      float fps = this.fpsAnim.update(Math.max(0, mc.getFps()), 0.05F, now);
      float ping = this.pingAnim.update(latency(mc), 0.05F, now);
      float size = 7.5F * unit;
      String name = HudFeature.watermarkShows("Player") && mc.player != null ? mc.player.getName().getString() : "";
      this.playerName = glassEllipsize(name, size, TEXT_STYLE, 96.0F * unit);
      this.pills.clear();
      if (HudFeature.watermarkShows("FPS")) {
         this.pills.add(new WatermarkElement.StatPill(Textures.Hud.ICON_COMPUTER, Math.round(fps) + " fps"));
      }

      if (HudFeature.watermarkShows("Ping")) {
         this.pills.add(new WatermarkElement.StatPill(Textures.Hud.ICON_WIFI, Math.round(ping) + " ms"));
      }

      this.serverText = HudFeature.watermarkShows("Server") ? serverAddress(mc) : "";
      this.brandWidth = 21.0F * unit + separatorWidth(unit) + textWidth("furryhook", unit);
      if (!this.playerName.isEmpty()) {
         this.brandWidth = this.brandWidth + (separatorWidth(unit) + textWidth(this.playerName, unit));
      }

      float statsContent = 0.0F;
      int segments = 0;

      for (WatermarkElement.StatPill pill : this.pills) {
         statsContent += 9.5F * unit + textWidth(pill.text(), unit);
         segments++;
      }

      if (!this.serverText.isEmpty()) {
         statsContent += textWidth(this.serverText, unit);
         segments++;
      }

      this.statsWidth = segments == 0 ? 0.0F : 12.0F * unit + statsContent + (segments - 1) * separatorWidth(unit);
      this.width = this.brandWidth + this.statsWidth;
      if (this.statsWidth > 0.0F) {
         this.width = this.width + (separatorWidth(unit) - 12.0F * unit);
      }

      this.height = 20.0F * unit;
   }

   @Override
   protected boolean preservePositionOnContentResize() {
      return true;
   }

   private static float textWidth(String text, float unit) {
      float size = 7.5F * unit;
      return glassTextWidth(text, size, TEXT_STYLE);
   }

   private static float separatorWidth(float unit) {
      return glassChevronWidth(unit);
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      if (!(this.width <= 0.0F)) {
         this.x = Math.clamp(this.x, 0.0F, Math.max(0.0F, mc.getWindow().getGuiScaledWidth() - this.width));
         float alpha = this.appearAlpha();
         float centerY = this.y + this.height * 0.5F;
         this.drawPlate(this.x, this.width, unit, alpha);
         float cursor = this.x + 6.0F * unit;
         drawLogo(cursor, centerY, unit, alpha);
         cursor += 9.0F * unit;
         cursor = separator(cursor, centerY, unit, alpha);
         cursor = text("furryhook", cursor, centerY, unit, HudFeature.hudAccent(), alpha);
         if (!this.playerName.isEmpty()) {
            cursor = separator(cursor, centerY, unit, alpha);
            cursor = text(this.playerName, cursor, centerY, unit, GLASS_TEXT_COLOR, alpha);
         }

         if (!(this.statsWidth <= 0.0F)) {
            cursor = separator(cursor, centerY, unit, alpha);
            boolean previous = false;

            for (WatermarkElement.StatPill pill : this.pills) {
               if (previous) {
                  cursor = separator(cursor, centerY, unit, alpha);
               }

               float iconSize = 7.0F * unit;
               Render2DUtil.texture(cursor, centerY - iconSize * 0.5F, iconSize, iconSize, pill.icon())
                  .color(ColorUtil.multiplyAlpha(HudFeature.hudAccent(), alpha))
                  .draw();
               cursor += 9.5F * unit;
               cursor = text(pill.text(), cursor, centerY, unit, GLASS_TEXT_COLOR, alpha);
               previous = true;
            }

            if (!this.serverText.isEmpty()) {
               if (previous) {
                  cursor = separator(cursor, centerY, unit, alpha);
               }

               text(this.serverText, cursor, centerY, unit, GLASS_TEXT_COLOR, alpha);
            }
         }
      }
   }

   private void drawPlate(float x, float width, float unit, float alpha) {
      this.drawWatermarkPanel(x, this.y, width, this.height, 6.0F * unit, unit, alpha);
   }

   private static float text(String value, float x, float centerY, float unit, int color, float alpha) {
      float size = 7.5F * unit;
      drawGlassText(value, x, centerY, size, TEXT_STYLE, color, alpha);
      return x + textWidth(value, unit);
   }

   private static void drawLogo(float x, float centerY, float unit, float alpha) {
      float size = 9.0F * unit;
      Render2DUtil.texture(x, centerY - size * 0.5F, size, size, Textures.Hud.ICON_DSF)
         .uv(0.0F, 0.0943F, 1.0F, 0.9057F)
         .radius(2.0F * unit)
         .color(ColorUtil.multiplyAlpha(-1, alpha))
         .draw();
   }

   private static float separator(float cursor, float centerY, float unit, float alpha) {
      return drawGlassChevron(cursor, centerY, unit, alpha);
   }

   private static String serverAddress(Minecraft mc) {
      ServerData server = mc.getCurrentServer();
      if (server == null) {
         return "singleplayer";
      }

      if (server.isRealm()) {
         return "realms";
      }

      String address = server.ip;
      if (address != null && !address.isBlank()) {
         address = address.trim().toLowerCase(Locale.ROOT);
         if (address.endsWith(":25565")) {
            address = address.substring(0, address.length() - ":25565".length());
         }

         if (address.length() > 24) {
            address = address.substring(0, 21) + "...";
         }

         return address;
      } else {
         return "singleplayer";
      }
   }

   private static int latency(Minecraft mc) {
      if (mc.getConnection() != null && mc.player != null) {
         PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
         return info != null ? info.getLatency() : 0;
      } else {
         return 0;
      }
   }

   private record StatPill(Identifier icon, String text) {
   }
}
