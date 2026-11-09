package win.furryhook.feature.impl.combat;

import java.security.SecureRandom;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.game.PlayerTickEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.movement.AirStuckFeature;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.FriendManager;
import win.furryhook.utils.combat.AuraRaycast;
import win.furryhook.utils.combat.SprintManager;

public final class TriggerBotFeature extends Feature implements MinecraftContext {
   private static final SecureRandom SECURE_RANDOM = new SecureRandom();
   public final MultiSelectSetting targets = this.register(
      new MultiSelectSetting("Targets", List.of("Players", "Naked"), "Players", "Friends", "Naked", "Animals", "Mobs")
   );
   public final MultiSelectSetting checks = this.register(
      new MultiSelectSetting("Checks", List.of("Disable on death"), "Disable on death", "Don't eat & hit", "Weapon only", "TPSSync")
   );
   public final NumberSetting attackRange = this.register(new NumberSetting("Attack Range", 3.0, 2.5, 6.0, 0.1, " blocks"));
   public final NumberSetting minAttackDelay = this.register(new NumberSetting("Min Delay", 0.0, 0.0, 500.0, 1.0, " ms"));
   public final NumberSetting maxAttackDelay = this.register(new NumberSetting("Max Delay", 0.0, 0.0, 500.0, 1.0, " ms"));
   public final BooleanSetting onlycrit = this.register(new BooleanSetting("Only Criticals", false));
   public final BooleanSetting onlySpaceCritical = this.register(new BooleanSetting("Smart Criticals", false));
   public final BooleanSetting throughWalls = this.register(new BooleanSetting("Through Walls", true));
   private LivingEntity currentTarget;
   private long attackDelayTime = 0L;
   private boolean wasEating = false;
   private long lastEatingEndTime = -1L;
   private long currentEatingBoostDuration = 1500L;

   public TriggerBotFeature() {
      super("TriggerBot", "Attacks the entity you are aiming at", FeatureCategory.COMBAT, -1);
   }

   public static TriggerBotFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(TriggerBotFeature.class);
   }

   public LivingEntity getCurrentTarget() {
      return this.isEnabled() ? this.currentTarget : null;
   }

   @Override
   protected void onDisable() {
      this.currentTarget = null;
      this.attackDelayTime = 0L;
      SprintManager.reset();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.currentTarget = null;
      this.attackDelayTime = 0L;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         boolean isEatingNow = player.isUsingItem();
         if (isEatingNow && !this.wasEating) {
            this.wasEating = true;
         } else if (!isEatingNow && this.wasEating) {
            this.wasEating = false;
            this.lastEatingEndTime = System.currentTimeMillis();
            this.currentEatingBoostDuration = 1000L + (long)(Math.random() * 1500.0);
         }

         this.currentTarget = this.crosshairTarget(mc, player);
      }
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         LocalPlayer player = event.getPlayer();
         LivingEntity target = this.currentTarget;
         if (player != null && target != null && target.isAlive()) {
            long now = System.currentTimeMillis();
            if (this.attackDelayTime == 0L) {
               long minD = this.minAttackDelay.getValue().longValue();
               long maxD = this.maxAttackDelay.getValue().longValue();
               long delay = minD;
               if (maxD > minD) {
                  delay = minD + (long)(SECURE_RANDOM.nextDouble() * (maxD - minD));
               }

               this.attackDelayTime = now + delay;
            }

            if (now >= this.attackDelayTime) {
               boolean readyToStrike;
               if (!this.onlycrit.getValue() && !this.onlySpaceCritical.getValue()) {
                  readyToStrike = this.checkCooldownAndConditions(player);
               } else {
                  readyToStrike = this.canAttack(player);
               }

               if (readyToStrike) {
                  SprintManager.markAttackImminent();
                  if (mc.gameMode != null) {
                     AirStuckFeature airStuck = FeatureManager.INSTANCE.getEnabled(AirStuckFeature.class);
                     if (airStuck != null) {
                        airStuck.pauseForAttack();
                     }

                     mc.gameMode.attack(player, target);
                     player.swing(InteractionHand.MAIN_HAND);
                  }

                  this.attackDelayTime = 0L;
               }
            }
         }
      }
   }

   private boolean checkCooldownAndConditions(LocalPlayer player) {
      return this.checkReturn(player) ? false : player.getAttackStrengthScale(this.checks.isSelected("TPSSync") ? 1.0F : 0.0F) >= 0.85F;
   }

   private boolean canAttack(LocalPlayer player) {
      if (!this.checkCooldownAndConditions(player)) {
         return false;
      }

      boolean canCritEnvironment = !player.onClimbable() && !player.isInWater() && !player.isInLava() && !player.isPassenger();
      if (canCritEnvironment) {
         boolean isJumpPressed = player.input != null && player.input.keyPresses.jump() || mc.options.keyJump.isDown();
         boolean isRecrit = !player.onGround() && player.hurtTime > 0;
         boolean wantCrit = this.onlycrit.getValue() && !this.onlySpaceCritical.getValue() || this.onlySpaceCritical.getValue() && (isJumpPressed || isRecrit);
         if (wantCrit) {
            if (player.onGround()) {
               return false;
            }

            return player.fallDistance > 0.0 && player.getDeltaMovement().y < 0.0;
         }
      }

      return true;
   }

   private boolean checkReturn(LocalPlayer player) {
      if (player.isDeadOrDying() && this.checks.isSelected("Disable on death")) {
         this.setEnabled(false);
         return true;
      }

      if (this.checks.isSelected("Weapon only") && !player.getMainHandItem().is(ItemTags.AXES) && !player.getMainHandItem().is(ItemTags.SWORDS)) {
         return true;
      }

      if (this.checks.isSelected("Don't eat & hit")) {
         boolean isEatingNow = player.isUsingItem();
         if (isEatingNow) {
            return true;
         }
      }

      return false;
   }

   private LivingEntity crosshairTarget(Minecraft mc, LocalPlayer player) {
      if (mc.level == null) {
         return null;
      } else {
         double reach = this.attackRange.getValue();
         if (mc.crosshairPickEntity instanceof LivingEntity living && this.isValidTarget(living, player, reach)) {
            return living;
         } else {
            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity instanceof LivingEntity living
                  && this.isValidTarget(living, player, reach)
                  && AuraRaycast.rotationIntersectsTarget(player, living, player.getYRot(), player.getXRot(), reach, 0.0, 0.2, this.throughWalls.getValue())) {
                  return living;
               }
            }

            return null;
         }
      }
   }

   private boolean isValidTarget(LivingEntity entity, LocalPlayer player, double reach) {
      if (entity == player) {
         return false;
      }

      if (entity.tickCount < 3) {
         return false;
      }

      if (player.distanceTo(entity) > reach) {
         return false;
      }

      if (entity instanceof Player playerEntity) {
         if (!this.targets.isSelected("Friends") && FriendManager.INSTANCE.isFriend(playerEntity.getGameProfile().name())) {
            return false;
         }

         if (playerEntity.getGameProfile().name().equalsIgnoreCase(player.getGameProfile().name())) {
            return false;
         }

         if (playerEntity.getArmorValue() == 0 && !this.targets.isSelected("Naked")) {
            return false;
         }

         if (!this.targets.isSelected("Players")) {
            return false;
         }
      }

      if ((entity instanceof Monster || entity instanceof Phantom || entity instanceof Bat || entity instanceof Shulker || entity instanceof Villager)
         && !this.targets.isSelected("Mobs")) {
         return false;
      } else {
         return (entity instanceof Animal || entity instanceof AmbientCreature) && !this.targets.isSelected("Animals")
            ? false
            : !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStand);
      }
   }
}
