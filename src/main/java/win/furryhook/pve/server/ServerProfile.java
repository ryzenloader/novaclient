package win.furryhook.pve.server;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public enum ServerProfile {
   GENERIC,
   FUNTIME,
   HOLYWORLD,
   REALLYWORLD;

   public static ServerProfile detect(Minecraft client) {
      ServerData server = client.getCurrentServer();
      return server == null ? GENERIC : detect(server.name, server.ip);
   }

   public static ServerProfile detect(String name, String address) {
      String identity = ((name == null ? "" : name) + " " + (address == null ? "" : address)).toLowerCase(Locale.ROOT);
      if (identity.contains("funtime")) {
         return FUNTIME;
      } else if (identity.contains("holyworld") || identity.contains("holy-world")) {
         return HOLYWORLD;
      } else {
         return !identity.contains("reallyworld") && !identity.contains("spookytime") ? GENERIC : REALLYWORLD;
      }
   }
}
