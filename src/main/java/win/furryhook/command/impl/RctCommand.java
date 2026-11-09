package win.furryhook.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import win.furryhook.command.ClientCommand;
import win.furryhook.context.MinecraftContext;
import win.furryhook.mixin.accessor.PlayerTabOverlayAccessor;
import win.furryhook.utils.text.ChatUtil;

public final class RctCommand extends ClientCommand implements MinecraftContext {
   private static final Pattern ANARCHY_PATTERN = Pattern.compile("анархия-(\\d+)");
   private static final Pattern GRIEF_PATTERN = Pattern.compile("гриф-(\\d+)");
   private static final long REJOIN_DELAY_MS = 1500L;

   public RctCommand() {
      super("rct", "Rejoins the current anarchy (FunTime/SpookyTime)");
   }

   @Override
   public void build(LiteralArgumentBuilder<Object> builder) {
      builder.executes(context -> this.execute());
   }

   private int execute() {
      if (this.player() == null) {
         return 0;
      } else if (!this.isOnSupportedServer()) {
         ChatUtil.error("Rct only works on FunTime and SpookyTime");
         return 0;
      } else {
         String header = this.tabHeader();
         String rejoinCommand = this.resolveRejoinCommand(header);
         if (rejoinCommand == null) {
            ChatUtil.error("Failed to detect the anarchy number from the tab list");
            return 0;
         } else {
            this.player().connection.sendCommand("hub");
            CompletableFuture.delayedExecutor(1500L, TimeUnit.MILLISECONDS).execute(() -> mc.execute(() -> {
               if (this.player() != null) {
                  this.player().connection.sendCommand(rejoinCommand);
               }
            }));
            ChatUtil.info("Reconnecting via /" + rejoinCommand + "...");
            return 1;
         }
      }
   }

   private boolean isOnSupportedServer() {
      ServerData server = mc.getCurrentServer();
      if (server != null && server.ip != null) {
         String ip = server.ip.toLowerCase(Locale.ROOT);
         return ip.contains("funtime") || ip.contains("spookytime");
      } else {
         return false;
      }
   }

   private String tabHeader() {
      Component header = ((PlayerTabOverlayAccessor)mc.gui.hud.getTabList()).getHeader();
      return header == null ? "" : header.getString().toLowerCase(Locale.ROOT).replaceAll("§.", "");
   }

   private String resolveRejoinCommand(String header) {
      Matcher anarchy = ANARCHY_PATTERN.matcher(header);
      if (anarchy.find()) {
         return "an" + anarchy.group(1);
      }

      Matcher grief = GRIEF_PATTERN.matcher(header);
      return grief.find() ? "grief" + grief.group(1) : null;
   }
}
