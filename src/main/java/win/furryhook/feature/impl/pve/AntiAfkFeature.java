package win.furryhook.feature.impl.pve;

import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldJoinEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.feature.setting.TextSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;

public final class AntiAfkFeature extends PveFeature {
   private static final float TURN_DEGREES = 3.0F;
   private static final float ROTATION_EPSILON = 0.01F;
   public final NumberSetting actionInterval = this.register(new NumberSetting("Action Interval", 20.0, 1.0, 300.0, 1.0, " s"));
   public final BooleanSetting turnHead = this.register(new BooleanSetting("Turn Head", true));
   public final BooleanSetting jump = this.register(new BooleanSetting("Jump", true));
   public final BooleanSetting sendChatMessage = this.register(new BooleanSetting("Send Chat Message", false));
   public final TextSetting chatText = this.register(new TextSetting("Chat Text", "Still here", 256).visibleWhen(this.sendChatMessage::getValue));
   private final AntiAfkActionTimer timer = new AntiAfkActionTimer();
   private ClientLevel observedLevel;
   private boolean orientationKnown;
   private float lastYaw;
   private float lastPitch;
   private float turnDirection = 1.0F;

   public AntiAfkFeature() {
      super("AntiAFK", "Performs small idle actions at a configurable interval", FeatureCategory.PLAYER, -1, AutomationPriority.BACKGROUND);
   }

   @Override
   protected void onPveEnable() {
      this.reset(null);
   }

   @Override
   protected void onPveDisable() {
      this.reset(null);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player == null || level == null) {
         this.reset(null);
      } else if (level != this.observedLevel) {
         this.reset(level);
         this.rememberOrientation(player);
      } else if (client.gui.screen() != null) {
         this.timer.reset();
         this.rememberOrientation(player);
      } else {
         this.timer.advance();
         if (this.hasManualActivity(client, player)) {
            this.timer.reset();
         } else if (!this.hasConfiguredAction()) {
            this.timer.reset();
         } else {
            long intervalTicks = AntiAfkActionTimer.secondsToTicks(this.actionInterval.getValue());
            if (this.timer.isDue(intervalTicks) && !this.automationIsBusy()) {
               boolean shouldTurn = this.turnHead.getValue();
               boolean shouldJump = this.jump.getValue() && this.canJumpSafely(player);
               String message = this.chatText.getValue().trim();
               boolean shouldChat = this.sendChatMessage.getValue() && !message.isEmpty();
               EnumSet<AutomationResource> resources = EnumSet.noneOf(AutomationResource.class);
               if (shouldTurn) {
                  resources.add(AutomationResource.ROTATION);
               }

               if (shouldJump) {
                  resources.add(AutomationResource.MOVEMENT);
               }

               if (shouldChat) {
                  resources.add(AutomationResource.CHAT);
               }

               if (!resources.isEmpty() && PveAutomationCoordinator.INSTANCE.acquire(this, AutomationPriority.BACKGROUND, resources)) {
                  try {
                     if (shouldTurn) {
                        this.turnSlightly(player);
                     }

                     if (shouldJump) {
                        player.jumpFromGround();
                     }

                     if (shouldChat) {
                        player.connection.sendChat(message);
                     }

                     this.timer.actionPerformed();
                  } finally {
                     PveAutomationCoordinator.INSTANCE.release(this);
                  }
               }
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reset(null);
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      this.reset(event.getLevel());
   }

   private boolean hasManualActivity(Minecraft client, LocalPlayer player) {
      boolean keyActivity = client.options.keyUp.isDown()
         || client.options.keyDown.isDown()
         || client.options.keyLeft.isDown()
         || client.options.keyRight.isDown()
         || client.options.keyJump.isDown()
         || client.options.keyShift.isDown()
         || client.options.keySprint.isDown()
         || client.options.keyAttack.isDown()
         || client.options.keyUse.isDown();
      if (!this.orientationKnown) {
         this.rememberOrientation(player);
         return keyActivity;
      } else {
         boolean rotationActivity = Math.abs(Mth.wrapDegrees(player.getYRot() - this.lastYaw)) > 0.01F || Math.abs(player.getXRot() - this.lastPitch) > 0.01F;
         this.rememberOrientation(player);
         return keyActivity || rotationActivity;
      }
   }

   private boolean automationIsBusy() {
      PveAutomationCoordinator coordinator = PveAutomationCoordinator.INSTANCE;
      return coordinator.isClaimedByOther(this, AutomationResource.MOVEMENT)
         || coordinator.isClaimedByOther(this, AutomationResource.ROTATION)
         || coordinator.isClaimedByOther(this, AutomationResource.CHAT);
   }

   private boolean hasConfiguredAction() {
      return this.turnHead.getValue() || this.jump.getValue() || this.sendChatMessage.getValue() && !this.chatText.getValue().isBlank();
   }

   private boolean canJumpSafely(LocalPlayer player) {
      return player.isAlive()
         && player.onGround()
         && !player.isPassenger()
         && !player.isCrouching()
         && !player.isSpectator()
         && !player.getAbilities().flying
         && !player.isFallFlying()
         && !player.isInWater()
         && !player.isInLava()
         && !player.onClimbable();
   }

   private void turnSlightly(LocalPlayer player) {
      float yaw = Mth.wrapDegrees(player.getYRot() + 3.0F * this.turnDirection);
      this.turnDirection = -this.turnDirection;
      player.setYRot(yaw);
      player.setYHeadRot(yaw);
      this.rememberOrientation(player);
   }

   private void rememberOrientation(LocalPlayer player) {
      this.lastYaw = player.getYRot();
      this.lastPitch = player.getXRot();
      this.orientationKnown = true;
   }

   private void reset(ClientLevel level) {
      this.observedLevel = level;
      this.timer.reset();
      this.orientationKnown = false;
      this.lastYaw = 0.0F;
      this.lastPitch = 0.0F;
      this.turnDirection = 1.0F;
   }
}
