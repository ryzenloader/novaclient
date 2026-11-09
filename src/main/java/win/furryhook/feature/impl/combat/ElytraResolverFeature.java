package win.furryhook.feature.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.PlayerTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class ElytraResolverFeature extends Feature {
   private static final long RETREAT_TIMEOUT_MS = 2000L;
   private static final double PREDICT_TICKS = 3.0;
   private static final double FACING_MAX_DISTANCE = 4.0;
   private static final double FACING_HARD_DISTANCE = 3.5;
   private static final double FACING_DOT = 0.5;
   private static final double MIN_AWAY_LENGTH = 0.1;
   private static final String MODE_SIDE = "В сторону";
   private static final String MODE_UP = "Вверх";
   private static final String MODE_DIAGONAL = "Диагональ";
   private static final String GRIM_ROTATION = "Grim";
   public final NumberSetting attackDistance = this.register(new NumberSetting("Дистанция атаки", 3.0, 2.0, 4.5, 0.1, " blocks"));
   public final NumberSetting retreatDistance = this.register(new NumberSetting("Дистанция отлёта", 8.0, 5.0, 12.0, 0.5, " blocks"));
   public final NumberSetting retreatAngle = this.register(new NumberSetting("Угол отлёта", 50.0, 20.0, 90.0, 5.0, "°"));
   public final ModeSetting retreatMode = this.register(new ModeSetting("Режим отлёта", "В сторону", "В сторону", "Вверх", "Диагональ"));
   public final BooleanSetting alternateSides = this.register(new BooleanSetting("Чередовать стороны", true));
   private ElytraResolverFeature.Phase phase = ElytraResolverFeature.Phase.APPROACH;
   private LivingEntity target;
   private Vec3 aimPoint;
   private Vec3 retreatDirection;
   private boolean flipSide;
   private long phaseStart;
   private String savedRotationMode;

   public ElytraResolverFeature() {
      super("ElytraResolver", "Подлёт и отлёт от цели на элитре", FeatureCategory.COMBAT, -1);
   }

   public static ElytraResolverFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(ElytraResolverFeature.class);
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.phaseStart = 0L;
      AuraFeature aura = AuraFeature.getMarkerFeature();
      if (aura != null) {
         this.savedRotationMode = aura.componentMode.getValue();
         aura.componentMode.setValue("Grim");
      }
   }

   @Override
   protected void onDisable() {
      this.reset();
      if (this.savedRotationMode != null) {
         AuraFeature aura = AuraFeature.getMarkerFeature();
         if (aura != null && aura.componentMode.containsMode(this.savedRotationMode)) {
            aura.componentMode.setValue(this.savedRotationMode);
         }

         this.savedRotationMode = null;
      }
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         LocalPlayer player = event.getPlayer();
         AuraFeature aura = AuraFeature.getEnabled();
         LivingEntity auraTarget = aura == null ? null : aura.getTarget();
         if (player != null && auraTarget != null && player.isFallFlying()) {
            double distance = player.getEyePosition().distanceTo(auraTarget.position());
            this.update(player, auraTarget, distance);
         } else {
            this.reset();
         }
      }
   }

   private void update(LocalPlayer player, LivingEntity target, double distance) {
      this.target = target;
      if (this.isTargetFacingUs(player, target, distance) && this.phase == ElytraResolverFeature.Phase.APPROACH) {
         this.enterRetreat();
      }

      switch (this.phase) {
         case APPROACH:
            if (distance <= this.attackDistance.getValue()) {
               this.enterRetreat();
            }
            break;
         case RETREAT:
            if (distance >= this.retreatDistance.getValue()) {
               this.phase = ElytraResolverFeature.Phase.APPROACH;
               this.retreatDirection = null;
               this.phaseStart = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() - this.phaseStart > 2000L) {
               this.phase = ElytraResolverFeature.Phase.APPROACH;
               this.retreatDirection = null;
            }
      }

      this.updateAimPoint(player, target);
   }

   private boolean isTargetFacingUs(LocalPlayer player, LivingEntity target, double distance) {
      if (distance > 4.0) {
         return false;
      }

      Vec3 toPlayer = player.position().subtract(target.position());
      if (toPlayer.lengthSqr() < 1.0E-6) {
         return false;
      }

      double dot = target.getLookAngle().dot(toPlayer.normalize());
      return dot > 0.5 && distance < 3.5;
   }

   private void updateAimPoint(LocalPlayer player, LivingEntity target) {
      Vec3 predicted = predictedPosition(target);
      if (this.phase == ElytraResolverFeature.Phase.APPROACH) {
         this.aimPoint = predicted.add(0.0, target.getBbHeight() * 0.5, 0.0);
      } else {
         if (this.retreatDirection == null) {
            this.retreatDirection = this.buildRetreatDirection(player, predicted);
         }
         double height = switch ((String)this.retreatMode.getValue()) {
            case "Вверх" -> 6.0;
            case "Диагональ" -> 3.0;
            default -> 1.5;
         };
         this.aimPoint = predicted.add(this.retreatDirection.scale(this.retreatDistance.getValue())).add(0.0, height, 0.0);
      }
   }

   private static Vec3 predictedPosition(LivingEntity target) {
      Vec3 position = target.position();
      Vec3 delta = position.subtract(new Vec3(target.xOld, target.yOld, target.zOld));
      return position.add(delta.scale(3.0));
   }

   private Vec3 buildRetreatDirection(LocalPlayer player, Vec3 targetPos) {
      Vec3 away = player.position().subtract(targetPos);
      away = away.length() < 0.1 ? player.getLookAngle().scale(-1.0) : away.normalize();
      double angle = this.retreatAngle.getValue();
      if (this.alternateSides.getValue()) {
         angle = this.flipSide ? angle : -angle;
         this.flipSide = !this.flipSide;
      }

      double rad = Math.toRadians(angle);
      double cos = Math.cos(rad);
      double sin = Math.sin(rad);
      double newX = away.x * cos - away.z * sin;
      double newZ = away.x * sin + away.z * cos;

      double y = switch ((String)this.retreatMode.getValue()) {
         case "Вверх" -> 0.6;
         case "Диагональ" -> 0.3;
         default -> 0.1;
      };
      return new Vec3(newX, y, newZ).normalize();
   }

   private void enterRetreat() {
      this.phase = ElytraResolverFeature.Phase.RETREAT;
      this.retreatDirection = null;
      this.phaseStart = System.currentTimeMillis();
   }

   public void onHit(LivingEntity hitTarget) {
      if (hitTarget != null && hitTarget == this.target && this.phase == ElytraResolverFeature.Phase.APPROACH) {
         this.enterRetreat();
      }
   }

   private void reset() {
      this.phase = ElytraResolverFeature.Phase.APPROACH;
      this.target = null;
      this.aimPoint = null;
      this.retreatDirection = null;
   }

   public boolean isApproaching() {
      return this.phase == ElytraResolverFeature.Phase.APPROACH;
   }

   public boolean isRetreating() {
      return this.phase == ElytraResolverFeature.Phase.RETREAT;
   }

   public LivingEntity getResolvedTarget() {
      return this.target;
   }

   public Vec3 getAimPoint() {
      return this.aimPoint;
   }

   public boolean isDriving(LivingEntity target) {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && player.isFallFlying() && target != null && target == this.target && this.aimPoint != null;
   }

   private enum Phase {
      APPROACH,
      RETREAT;
   }
}
