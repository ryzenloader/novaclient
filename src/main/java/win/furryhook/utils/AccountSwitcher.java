package win.furryhook.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.furryhook.account.MicrosoftAuth;
import win.furryhook.menu.core.MenuConfigStore;
import win.furryhook.mixin.accessor.MinecraftAccessor;
import win.furryhook.mixin.accessor.MinecraftServerAccessor;

public final class AccountSwitcher {
   private static final Logger LOGGER = LoggerFactory.getLogger(AccountSwitcher.class);
   public static final String DEFAULT_NAME = "Furry";
   private static boolean startupApplied;

   private AccountSwitcher() {
   }

   public static void applyStartupAccount() {
      if (!startupApplied) {
         startupApplied = true;
         String name = MenuConfigStore.getString("selectedAccount", "");
         if (name.isEmpty() || !name.matches("^[a-zA-Z0-9_]{3,16}$")) {
            name = "Furry";
         }

         switchTo(name);
      }
   }

   public static void switchTo(String name) {
      Minecraft mc = Minecraft.getInstance();
      UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
      User user = new User(name, uuid, "0", Optional.empty(), Optional.empty());
      MinecraftAccessor accessor = (MinecraftAccessor)mc;
      accessor.setUser(user);
      accessor.setProfileFuture(CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid, name))));
      accessor.setUserApiService(UserApiService.OFFLINE);
      accessor.setProfileKeyPairManager(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
      mc.updateTitle();
   }

   public static void switchToMicrosoft(MicrosoftAuth.MsAccount account) {
      Minecraft mc = Minecraft.getInstance();
      UUID uuid = parseUuid(account.uuid());
      User user = new User(account.username(), uuid, account.accessToken(), Optional.empty(), Optional.empty());
      MinecraftAccessor accessor = (MinecraftAccessor)mc;
      accessor.setUser(user);
      accessor.setProfileFuture(CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid, account.username()))));
      mc.updateTitle();
      CompletableFuture.runAsync(() -> {
         try {
            YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(mc.getProxy());
            UserApiService apiService = authService.createUserApiService(account.accessToken());
            Services services = Services.create(authService, mc.gameDirectory.toPath().toFile());
            ProfileKeyPairManager keyPairManager = ProfileKeyPairManager.create(apiService, user, mc.gameDirectory.toPath());
            mc.execute(() -> {
               accessor.setUserApiService(apiService);
               accessor.setServices(services);
               accessor.setProfileKeyPairManager(keyPairManager);
               mc.updateTitle();
            });
         } catch (Throwable throwable) {
            LOGGER.error("Failed to rebuild auth services for {}", account.username(), throwable);
         }
      });
   }

   public static void relogin(String name) {
      Minecraft mc = Minecraft.getInstance();
      ServerData server = mc.getCurrentServer();
      String levelId = null;
      IntegratedServer integrated = mc.getSingleplayerServer();
      if (mc.isLocalServer() && integrated != null) {
         levelId = ((MinecraftServerAccessor)integrated).getStorageSource().getLevelId();
      }

      switchTo(name);
      if (mc.level != null) {
         mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
         if (levelId != null) {
            mc.createWorldOpenFlows().openWorld(levelId, () -> mc.setScreenAndShow(new TitleScreen()));
         } else if (server != null && !server.isLan() && !server.isRealm()) {
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(server.ip), server, false, null);
         }
      }
   }

   private static UUID parseUuid(String raw) {
      String trimmed = raw.trim();
      if (trimmed.length() == 32) {
         trimmed = trimmed.substring(0, 8)
            + "-"
            + trimmed.substring(8, 12)
            + "-"
            + trimmed.substring(12, 16)
            + "-"
            + trimmed.substring(16, 20)
            + "-"
            + trimmed.substring(20);
      }

      return UUID.fromString(trimmed);
   }
}
