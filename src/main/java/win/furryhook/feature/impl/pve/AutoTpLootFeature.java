package win.furryhook.feature.impl.pve;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.PlayerTickEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.mining.MiningSessionSnapshot;

public final class AutoTpLootFeature extends PveFeature {
   public final NumberSetting range = this.register(new NumberSetting("Range", 128.0, 4.0, 256.0, 4.0, " blocks"));
   public final NumberSetting maxSpeed = this.register(new NumberSetting("Max Speed", 35.0, 0.5, 35.0, 0.5, " blocks/tick"));
   public final NumberSetting acceleration = this.register(new NumberSetting("Acceleration", 0.5, 0.05, 1.0, 0.05, "x"));
   public final NumberSetting returnRadius = this.register(new NumberSetting("Return Radius", 0.5, 0.1, 2.0, 0.1, " blocks"));
   public final NumberSetting targetTimeout = this.register(new NumberSetting("Target Timeout", 15.0, 2.0, 60.0, 1.0, "s"));
   private final MiningSessionSnapshot snapshot = new MiningSessionSnapshot();
   private ItemEntity target;
   private Vec3 origin;
   private AutoTpLootFeature.State state = AutoTpLootFeature.State.IDLE;
   private long stateTick;
   private long tick;
   private boolean controlledVelocity;

   public AutoTpLootFeature() {
      super(
         "AutoTpLoot",
         "Accelerates flight to the nearest grounded item and returns",
         FeatureCategory.MISC,
         -1,
         AutomationPriority.FEATURE,
         AutomationResource.MOVEMENT
      );
   }

   @Override
   protected void onPveEnable() {
      this.reset(false);
      this.snapshot.capture(Minecraft.getInstance().player);
   }

   @Override
   protected void onPveDisable() {
      this.cleanup();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cleanup();
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         LocalPlayer player = event.getPlayer();
         Minecraft client = Minecraft.getInstance();
         this.tick++;
         if (player != null && client.level != null && player.isAlive() && player.getAbilities().flying) {
            this.snapshot.capture(player);
            switch (this.state) {
               case IDLE:
                  this.acquireTarget(client, player);
                  break;
               case SEEKING:
                  this.seek(player);
                  break;
               case RETURNING:
                  this.returnToOrigin(player);
            }
         } else {
            this.reset(true);
         }
      }
   }

   public AutoTpLootFeature.State getState() {
      return this.state;
   }

   public ItemEntity getTarget() {
      return this.target;
   }

   private void acquireTarget(Minecraft client, LocalPlayer player) {
      double rangeSquared = this.range.getValue() * this.range.getValue();
      double nearestDistance = rangeSquared;
      this.target = null;

      for (Entity entity : client.level.entitiesForRendering()) {
         if (entity instanceof ItemEntity item && item.isAlive() && item.onGround()) {
            double distance = item.distanceToSqr(player);
            if (distance <= nearestDistance) {
               nearestDistance = distance;
               this.target = item;
            }
         }
      }

      if (this.target != null) {
         this.origin = player.position();
         this.transition(AutoTpLootFeature.State.SEEKING);
      }
   }

   private void seek(LocalPlayer player) {
      long timeoutTicks = Math.max(1L, this.targetTimeout.getValue().longValue() * 20L);
      if (this.target != null && this.target.isAlive() && this.tick - this.stateTick < timeoutTicks) {
         this.accelerate(player, this.target.position());
      } else {
         this.transition(AutoTpLootFeature.State.RETURNING);
      }
   }

   private void returnToOrigin(LocalPlayer player) {
      if (this.origin == null) {
         this.reset(true);
      } else {
         double distance = player.position().distanceTo(this.origin);
         if (distance <= this.returnRadius.getValue()) {
            this.reset(true);
         } else {
            this.accelerate(player, this.origin);
         }
      }
   }

   private void accelerate(LocalPlayer player, Vec3 destination) {
      Vec3 delta = destination.subtract(player.position());
      double distance = delta.length();
      if (distance <= 1.0E-6) {
         player.setDeltaMovement(Vec3.ZERO);
         this.controlledVelocity = true;
      } else {
         double speed = Math.min(this.maxSpeed.getValue(), distance * this.acceleration.getValue());
         player.setDeltaMovement(delta.normalize().scale(speed));
         this.controlledVelocity = true;
      }
   }

   private void transition(AutoTpLootFeature.State next) {
      this.state = next;
      this.stateTick = this.tick;
      if (next == AutoTpLootFeature.State.RETURNING) {
         this.target = null;
      }
   }

   private void reset(boolean stopMovement) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (stopMovement && this.controlledVelocity && player != null) {
         player.setDeltaMovement(Vec3.ZERO);
      }

      this.target = null;
      this.origin = null;
      this.state = AutoTpLootFeature.State.IDLE;
      this.stateTick = this.tick;
      this.controlledVelocity = false;
   }

   private void cleanup() {
      Minecraft client = Minecraft.getInstance();
      this.reset(true);
      this.snapshot.restore(client);
   }

   public enum State {
      IDLE,
      SEEKING,
      RETURNING;
   }
}
