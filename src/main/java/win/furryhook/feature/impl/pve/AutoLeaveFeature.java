package win.furryhook.feature.impl.pve;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.feature.setting.TextSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.utils.FriendManager;

public final class AutoLeaveFeature extends PveFeature {
   public static final String ACTION_DISCONNECT = "Disconnect";
   public static final String ACTION_SERVER_COMMAND = "Server Command";
   public static final String CONDITION_LOW_HEALTH = "Low Health";
   public static final String CONDITION_MODERATOR = "Moderator";
   public static final String CONDITION_NEARBY_PLAYER = "Nearby Player";
   private static final long CLEAR_DEBOUNCE_MILLIS = 500L;
   public final ModeSetting action = this.register(new ModeSetting("Action", "Server Command", "Disconnect", "Server Command"));
   public final TextSetting command = this.register(this.commandSetting());
   public final MultiSelectSetting conditions = this.register(
      new MultiSelectSetting("Conditions", List.of("Low Health", "Moderator", "Nearby Player"), "Low Health", "Moderator", "Nearby Player")
   );
   public final NumberSetting minimumHealth = this.register(
      new NumberSetting("Minimum Health", 5.0, 1.0, 20.0, 0.5, " HP").visibleWhen(() -> this.conditions.isSelected("Low Health"))
   );
   public final NumberSetting leaveDistance = this.register(
      new NumberSetting("Trigger Distance", 10.0, 1.0, 100.0, 1.0, " blocks").visibleWhen(() -> this.conditions.isSelected("Nearby Player"))
   );
   public final NumberSetting cooldown = this.register(new NumberSetting("Cooldown", 5.0, 1.0, 30.0, 1.0, " s"));
   private boolean dangerLatched;
   private long clearStartedAt;
   private long nextActionAt;
   private ClientPacketListener connection;

   public AutoLeaveFeature() {
      super("AutoLeave", "Leaves when health, nearby players, or moderator presence becomes unsafe", FeatureCategory.MISC, -1, AutomationPriority.EMERGENCY);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      this.syncConnection(client);
      LocalPlayer self = client.player;
      ClientLevel level = client.level;
      if (self != null && level != null && client.getConnection() != null && client.getCurrentServer() != null) {
         long now = System.currentTimeMillis();
         if (!self.isCreative() && !self.isSpectator()) {
            boolean danger = this.hasDanger(client, self, level);
            if (!danger) {
               this.clearLatchAfterDebounce(now);
            } else {
               this.clearStartedAt = 0L;
               if (!this.dangerLatched && now >= this.nextActionAt) {
                  if (this.performAction(client, self)) {
                     this.dangerLatched = true;
                     this.nextActionAt = now + Math.round(this.cooldown.getValue() * 1000.0);
                  }
               }
            }
         } else {
            this.clearLatchAfterDebounce(now);
         }
      }
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntimeState();
   }

   @Override
   protected void onPveDisable() {
      this.resetRuntimeState();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resetRuntimeState();
   }

   private boolean hasDanger(Minecraft client, LocalPlayer self, ClientLevel level) {
      if (this.conditions.isSelected("Low Health") && self.getHealth() <= this.minimumHealth.getValue().floatValue()) {
         return true;
      } else {
         return this.conditions.isSelected("Moderator") && ModeratorDetector.find(client, self).isPresent()
            ? true
            : this.conditions.isSelected("Nearby Player") && nearbyNonFriend(self, level, this.leaveDistance.getValue());
      }
   }

   private boolean performAction(Minecraft client, LocalPlayer self) {
      if (this.action.is("Disconnect")) {
         client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
         return true;
      }

      Optional<String> safeCommand = SafeServerCommand.normalize(this.command.getValue());
      if (!safeCommand.isEmpty() && this.claim(AutomationResource.CHAT)) {
         try {
            self.connection.sendCommand(safeCommand.get());
            return true;
         } catch (RuntimeException ignored) {
            return false;
         } finally {
            PveAutomationCoordinator.INSTANCE.release(this);
         }
      } else {
         return false;
      }
   }

   private void clearLatchAfterDebounce(long now) {
      if (!this.dangerLatched) {
         this.clearStartedAt = 0L;
      } else if (this.clearStartedAt == 0L) {
         this.clearStartedAt = now;
      } else {
         if (now - this.clearStartedAt >= 500L) {
            this.dangerLatched = false;
            this.clearStartedAt = 0L;
         }
      }
   }

   private void resetRuntimeState() {
      this.connection = null;
      this.clearDebounceState();
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private void syncConnection(Minecraft client) {
      ClientPacketListener current = client.getConnection();
      if (current != this.connection) {
         this.connection = current;
         this.clearDebounceState();
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private void clearDebounceState() {
      this.dangerLatched = false;
      this.clearStartedAt = 0L;
      this.nextActionAt = 0L;
   }

   static boolean nearbyNonFriend(LocalPlayer self, ClientLevel level, double distance) {
      double maxDistanceSquared = distance * distance;

      for (Player candidate : level.players()) {
         if (candidate != self
            && !candidate.getUUID().equals(self.getUUID())
            && candidate.isAlive()
            && !candidate.isCreative()
            && !candidate.isSpectator()
            && !FriendManager.INSTANCE.isFriend(candidate.getGameProfile().name())
            && self.distanceToSqr(candidate) <= maxDistanceSquared) {
            return true;
         }
      }

      return false;
   }

   private TextSetting commandSetting() {
      return new TextSetting("Command", "/hub", 64).visibleWhen(() -> this.action.is("Server Command"));
   }
}
