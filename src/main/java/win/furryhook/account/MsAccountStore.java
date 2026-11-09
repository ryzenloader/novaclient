package win.furryhook.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import win.furryhook.utils.AccountSwitcher;
import win.furryhook.utils.ConfigIO;

public final class MsAccountStore {
   private static final Path PATH = ConfigIO.resolve("ms-alts.json");
   private static final List<MicrosoftAuth.MsAccount> ACCOUNTS = new ArrayList<>();
   private static boolean loaded;

   private MsAccountStore() {
   }

   public static List<MicrosoftAuth.MsAccount> all() {
      load();
      return ACCOUNTS;
   }

   public static int size() {
      return all().size();
   }

   public static MicrosoftAuth.MsAccount store(MicrosoftAuth.MsAccount account) {
      load();
      ACCOUNTS.removeIf(a -> a.username().equalsIgnoreCase(account.username()));
      ACCOUNTS.add(0, account);
      save();
      return account;
   }

   public static void remove(MicrosoftAuth.MsAccount account) {
      load();
      if (ACCOUNTS.removeIf(a -> a.username().equalsIgnoreCase(account.username()))) {
         save();
      }
   }

   public static void login(MicrosoftAuth.MsAccount account) {
      store(account);
      AccountSwitcher.switchToMicrosoft(account);
   }

   private static void save() {
      JsonObject root = new JsonObject();
      JsonArray array = new JsonArray();

      for (MicrosoftAuth.MsAccount account : ACCOUNTS) {
         JsonObject entry = new JsonObject();
         entry.addProperty("name", account.username());
         entry.addProperty("uuid", account.uuid());
         entry.addProperty("accessToken", account.accessToken());
         entry.addProperty("refreshToken", account.refreshToken());
         entry.addProperty("expiresAt", account.expiresAt() == null ? 0L : account.expiresAt().getEpochSecond());
         array.add(entry);
      }

      root.add("accounts", array);
      ConfigIO.write(PATH, root);
   }

   private static void load() {
      if (!loaded) {
         loaded = true;
         JsonObject root = ConfigIO.read(PATH);
         if (root != null && root.has("accounts") && root.get("accounts").isJsonArray()) {
            for (JsonElement raw : root.getAsJsonArray("accounts")) {
               if (raw.isJsonObject()) {
                  JsonObject entry = raw.getAsJsonObject();
                  String name = entry.has("name") ? entry.get("name").getAsString() : "";
                  String uuid = entry.has("uuid") ? entry.get("uuid").getAsString() : "";
                  String access = entry.has("accessToken") ? entry.get("accessToken").getAsString() : "";
                  String refresh = entry.has("refreshToken") ? entry.get("refreshToken").getAsString() : null;
                  long expires = entry.has("expiresAt") ? entry.get("expiresAt").getAsLong() : 0L;
                  if (!name.isEmpty() && !uuid.isEmpty() && !access.isEmpty()) {
                     ACCOUNTS.add(new MicrosoftAuth.MsAccount(name, uuid, access, refresh, expires <= 0L ? null : Instant.ofEpochSecond(expires)));
                  }
               }
            }
         }
      }
   }
}
