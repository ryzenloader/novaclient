package win.furryhook.pve.economy;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import win.furryhook.mixin.accessor.PlayerTabOverlayAccessor;

public final class ServerUiText {
   private ServerUiText() {
   }

   public static String tabHeader(Minecraft client) {
      if (client != null && client.gui != null && client.gui.hud.getTabList() != null) {
         Component header = ((PlayerTabOverlayAccessor)client.gui.hud.getTabList()).getHeader();
         return header == null ? "" : EconomyTextParser.normalize(header.getString());
      } else {
         return "";
      }
   }

   public static String serverHost(Minecraft client) {
      ServerData server = client == null ? null : client.getCurrentServer();
      if (server != null && server.ip != null) {
         String host = server.ip.trim().toLowerCase(Locale.ROOT);
         if (host.startsWith("[")) {
            int closing = host.indexOf(93);
            return closing > 0 ? host.substring(1, closing) : host;
         } else {
            int colon = host.indexOf(58);
            return colon < 0 ? host : host.substring(0, colon);
         }
      } else {
         return "";
      }
   }
}
