package win.furryhook.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

public final class Alt {
   public static final String NAME_PATTERN = "^[a-zA-Z0-9_]{3,16}$";
   private final String name;
   private final UUID uuid;
   private final long addedAt;
   private long lastUsedAt;
   private Identifier skin;

   public Alt(String name, long addedAt, long lastUsedAt) {
      this.name = name;
      this.uuid = offlineUuid(name);
      this.addedAt = addedAt;
      this.lastUsedAt = lastUsedAt;
   }

   public static UUID offlineUuid(String name) {
      return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
   }

   public static boolean isValidName(String name) {
      return name != null && name.matches("^[a-zA-Z0-9_]{3,16}$");
   }

   public String name() {
      return this.name;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public long addedAt() {
      return this.addedAt;
   }

   public long lastUsedAt() {
      return this.lastUsedAt;
   }

   public void markUsed() {
      this.lastUsedAt = System.currentTimeMillis();
   }

   public Identifier skin() {
      Identifier cached = this.skin;
      if (cached == null) {
         cached = DefaultPlayerSkin.get(this.uuid).body().texturePath();
         this.skin = cached;
      }

      return cached;
   }

   public String lastUsedLabel() {
      if (this.lastUsedAt <= 0L) {
         return "never used";
      } else {
         long seconds = Math.max(0L, (System.currentTimeMillis() - this.lastUsedAt) / 1000L);
         if (seconds < 60L) {
            return "used just now";
         } else if (seconds < 3600L) {
            return "used " + seconds / 60L + "m ago";
         } else {
            return seconds < 86400L ? "used " + seconds / 3600L + "h ago" : "used " + seconds / 86400L + "d ago";
         }
      }
   }
}
