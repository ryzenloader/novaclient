package win.furryhook.feature.impl.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.entity.player.Player;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.EntityAttackEvent;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class TrashTalkFeature extends Feature implements MinecraftContext {
   private static final String[] MESSAGES = new String[]{
      "на парашу сынок",
      "это гташка",
      "гг сучка",
      "у тебя так смешно разлетелись зубы",
      "че деду в зеркальную ракету",
      "че деду в нос ебанный паравоз",
      "причина тряски?",
      "гетни фурри клиент",
      "я ты",
      "еба ты долбоебис",
      "го секс",
      "кто поймал тот и съел",
      "кто успел тот и съел",
      "пиздорез",
      "армо бог,ты лох",
      "въебал в мозгу",
      "ок да?",
      "го ты валетиком ляжешь в гроб ебанутый",
      "мур мур",
      "cry more сынок",
      "ты кто по масте?",
      "привет чика",
      "привет котик",
      "паста твоя не бустит гетни фурри длс",
      "уебу суку"
   };
   private static final long TRACK_TIMEOUT_MS = 30000L;
   public final BooleanSetting mentionName = this.register(new BooleanSetting("Mention Name", true));
   public final NumberSetting delay = this.register(new NumberSetting("Delay", 0.5, 0.0, 5.0, 0.1, "s"));
   public final NumberSetting cooldown = this.register(new NumberSetting("Cooldown", 2.0, 0.5, 10.0, 0.5, "s"));
   private final Map<Integer, TrashTalkFeature.TrackedTarget> tracked = new LinkedHashMap<>();
   private final Map<Integer, TrashTalkFeature.PendingTaunt> pending = new LinkedHashMap<>();
   private final ThreadLocalRandom random = ThreadLocalRandom.current();
   private long lastMessageAt;

   public TrashTalkFeature() {
      super("TrashTalk", "Отправляет сообщение в чат после убийства цели", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.tracked.clear();
      this.pending.clear();
      this.lastMessageAt = 0L;
   }

   @EventTarget
   public void onAttack(EntityAttackEvent event) {
      if (event.getTarget() instanceof Player player && player != event.getPlayer()) {
         this.tracked.put(player.getId(), new TrashTalkFeature.TrackedTarget(player, System.currentTimeMillis() + 30000L));
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.player() != null) {
         long now = System.currentTimeMillis();
         this.tracked.values().removeIf(tracked -> now >= tracked.expiresAt() && !tracked.player().isDeadOrDying());
         this.tracked.entrySet().removeIf(entry -> {
            if (!entry.getValue().player().isDeadOrDying()) {
               return false;
            }

            long sendAt = now + Math.round(this.delay.getValue() * 1000.0);
            this.pending.put(entry.getKey(), new TrashTalkFeature.PendingTaunt(entry.getValue().player().getName().getString(), sendAt));
            return true;
         });
         this.pending.entrySet().removeIf(entry -> {
            if (now < entry.getValue().sendAt()) {
               return false;
            }

            this.trySend(entry.getValue().name(), now);
            return true;
         });
      }
   }

   private void trySend(String name, long now) {
      if (name != null && !name.isBlank() && mc.player != null) {
         if (now - this.lastMessageAt >= Math.round(this.cooldown.getValue() * 1000.0)) {
            String taunt = MESSAGES[this.random.nextInt(MESSAGES.length)];
            String message = this.mentionName.getValue() ? name + " " + taunt : taunt;
            mc.player.connection.sendChat(message);
            this.lastMessageAt = now;
         }
      }
   }

   private record PendingTaunt(String name, long sendAt) {
   }

   private record TrackedTarget(Player player, long expiresAt) {
   }
}
