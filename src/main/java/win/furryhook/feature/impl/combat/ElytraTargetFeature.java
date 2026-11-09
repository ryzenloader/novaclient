package win.furryhook.feature.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.render.Render2DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.Render2DUtil;

public final class ElytraTargetFeature extends Feature {
   private static final double MIN_TARGET_DISTANCE = 5.0;
   private static final double MIN_OWN_VEL_SQ = 0.0064;
   private static final double TARGET_HEIGHT_FRACTION = 0.5;
   private static final double MIN_DIFF_SQ = 1.0E-6;
   private static final double BEHIND_DOT = 0.0;
   private static final double PREDICT_MIN_MOVE_SQ = 1.0;
   private static final double FLYBOX_MIN_MOTION_SQ = 1.0E-4;
   private static final double FLYBOX_IDLE_GROW = 0.2;
   private static final double FLYBOX_GROW_X = 0.4;
   private static final double FLYBOX_GROW_Y = 0.3;
   private static final double FLYBOX_GROW_Z = 0.4;
   private static final double REVERSE_COS = -0.5;
   private static final double BLEND = 0.5;
   private static final double MIN_HORIZ_LEN = 0.03;
   private static final double EPS = 1.0E-4;
   public static final String PRIORITY_ELYTRAS_FIRST = "Сперва элитры";
   private static final String PRIORITY_NORMAL = "Обычно";
   public final ModeSetting priority = this.register(new ModeSetting("Приоритет цели", "Обычно", "Обычно", "Сперва элитры"));
   public final BooleanSetting overtake = this.register(new BooleanSetting("Перегон", true));
   public final BooleanSetting bravoFFA = this.register(new BooleanSetting("BravoFFA", false).visibleWhen(() -> this.overtake.getValue()));
   public final NumberSetting predictPower = this.register(new NumberSetting("Сила предикта", 3.0, 1.0, 5.0, 0.1, ""));
   public final NumberSetting targetRange = this.register(new NumberSetting("Дистанция цели", 64.0, 5.0, 100.0, 1.0, " blocks"));
   public final BooleanSetting hitboxResolver = this.register(new BooleanSetting("Резолвер хитбокса", true));
   public final BooleanSetting renderPredict = this.register(new BooleanSetting("Показывать предикт", true));
   private Vec3 lastImpulse = Vec3.ZERO;

   public ElytraTargetFeature() {
      super("ElytraTarget", "Топовый перегон на элитре", FeatureCategory.COMBAT, -1);
   }

   public static ElytraTargetFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(ElytraTargetFeature.class);
   }

   @Override
   protected void onDisable() {
      this.lastImpulse = Vec3.ZERO;
   }

   public boolean isOvertakeActive() {
      return this.isEnabled() && this.overtake.getValue();
   }

   public boolean isBravoActive() {
      return this.isEnabled() && this.bravoFFA.getValue() && this.overtake.getValue();
   }

   public boolean isOvertakeTarget(LivingEntity target) {
      LocalPlayer player = Minecraft.getInstance().player;
      return this.isOvertakeActive() && player != null && target != null ? target.isFallFlying() && player.distanceTo(target) > 5.0 : false;
   }

   public boolean isTargetBehind(LivingEntity target) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && target != null) {
         Vec3 motion = player.getDeltaMovement();
         Vec3 horizMotion = new Vec3(motion.x, 0.0, motion.z);
         if (horizMotion.lengthSqr() < 0.0064) {
            return false;
         }

         horizMotion = horizMotion.normalize();
         Vec3 toTarget = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(player.getEyePosition());
         Vec3 horizTo = new Vec3(toTarget.x, 0.0, toTarget.z);
         return horizTo.lengthSqr() < 1.0E-6 ? false : horizMotion.dot(horizTo.normalize()) < 0.0;
      } else {
         return false;
      }
   }

   public static boolean isPredicting(LocalPlayer player, LivingEntity target) {
      ElytraTargetFeature feature = getEnabled();
      return feature != null && player != null && target != null ? feature.overtake.getValue() && player.isFallFlying() && target.isFallFlying() : false;
   }

   public Vec3 aimPoint(LocalPlayer player, LivingEntity target) {
      if (player == null || target == null) {
         return Vec3.ZERO;
      }

      if (!isPredicting(player, target)) {
         return target.getEyePosition();
      }

      double dx = target.getX() - target.xOld;
      double dy = target.getY() - target.yOld;
      double dz = target.getZ() - target.zOld;
      if (dx * dx + dy * dy + dz * dz < 1.0) {
         return target.position();
      }

      double forward = this.predictPower.getValue();
      double half = target.getBbHeight() * 0.5;
      Vec3 centre = target.position().add(0.0, half, 0.0);
      Vec3 velocity = target.getDeltaMovement();
      Vec3 velDir = velocity.lengthSqr() > 1.0E-4 ? velocity.normalize() : Vec3.ZERO;
      Vec3 combined = target.getLookAngle().add(velDir).normalize();
      Vec3 predicted = centre.add(combined.scale(forward));
      return new Vec3(predicted.x, predicted.y - half, predicted.z);
   }

   public Vec3 predictPoint(LivingEntity target, float ignoredTicks) {
      return this.aimPoint(Minecraft.getInstance().player, target);
   }

   public float predictTicks() {
      return this.predictPower.getValue().floatValue();
   }

   public double aimRange() {
      return this.targetRange.getValue();
   }

   public static boolean prefersGlidingTargets(LocalPlayer player) {
      ElytraTargetFeature feature = getEnabled();
      return feature != null && player != null && player.isFallFlying() && feature.priority.is("Сперва элитры");
   }

   public static boolean overridesReach(LocalPlayer player, LivingEntity target) {
      ElytraTargetFeature feature = getEnabled();
      if (feature == null || player == null || target == null) {
         return false;
      } else {
         return ElytraResolverFeature.getEnabled() != null
            ? false
            : feature.overtake.getValue() && feature.hitboxResolver.getValue() && player.isFallFlying() && target.isFallFlying();
      }
   }

   public boolean allowsAttack(LocalPlayer player, LivingEntity target) {
      if (player != null && target != null) {
         AABB playerBox = player.getBoundingBox();
         AABB flyBox = this.flyBox(target);
         if (flyBox.intersects(playerBox) || flyBox.contains(player.getEyePosition())) {
            return true;
         } else {
            return !this.isTargetBehind(target) ? false : flyBox.intersects(playerBox) || flyBox.contains(player.getEyePosition());
         }
      } else {
         return false;
      }
   }

   private AABB flyBox(LivingEntity target) {
      AABB current = target.getBoundingBox();
      Vec3 motion = targetMotion(target);
      if (motion.lengthSqr() < 1.0E-4) {
         return current.inflate(0.2);
      }

      double lead = Math.max(1.5, this.predictPower.getValue());
      return current.move(motion.normalize().scale(lead)).inflate(0.4, 0.3, 0.4);
   }

   private static Vec3 targetMotion(LivingEntity target) {
      Vec3 motion = target.getDeltaMovement();
      return motion.lengthSqr() > 1.0E-4 ? motion : target.position().subtract(new Vec3(target.xOld, target.yOld, target.zOld));
   }

   public static Vec3 redirectFireworkImpulse(LocalPlayer player, Vec3 impulse) {
      ElytraTargetFeature feature = getEnabled();
      return feature != null && player != null ? feature.redirect(player, impulse) : impulse;
   }

   private Vec3 redirect(LocalPlayer player, Vec3 impulse) {
      AuraFeature aura = AuraFeature.getEnabled();
      LivingEntity target = aura == null ? null : aura.getTarget();
      if (target == null || !player.isFallFlying() || !this.isOvertakeTarget(target)) {
         this.lastImpulse = impulse;
         return impulse;
      }

      if (this.isBravoActive() && this.isTargetBehind(target)) {
         Vec3 forward = lockToCourse(player, impulse);
         this.lastImpulse = forward == null ? impulse : forward;
         return this.lastImpulse;
      }

      Vec3 toTarget = this.aimPoint(player, target).subtract(player.getEyePosition());
      Vec3 horizDir = new Vec3(toTarget.x, 0.0, toTarget.z);
      if (!(toTarget.lengthSqr() < 1.0E-4) && !(horizDir.lengthSqr() < 1.0E-4)) {
         horizDir = horizDir.normalize();
         Vec3 lastHoriz = new Vec3(this.lastImpulse.x, 0.0, this.lastImpulse.z);
         double lastLen = Math.sqrt(lastHoriz.x * lastHoriz.x + lastHoriz.z * lastHoriz.z);
         double currentLen = Math.sqrt(impulse.x * impulse.x + impulse.z * impulse.z);
         if (lastLen < 1.0E-4 && currentLen < 1.0E-4) {
            this.lastImpulse = impulse;
            return impulse;
         } else if (lastLen > 1.0E-4 && lastHoriz.normalize().dot(horizDir) < -0.5) {
            double limited = Math.min(currentLen, lastLen);
            Vec3 snapped = horizDir.scale(limited);
            this.lastImpulse = new Vec3(snapped.x, impulse.y, snapped.z);
            return this.lastImpulse;
         } else {
            Vec3 desired = horizDir.scale(currentLen);
            Vec3 blended = lastLen > 1.0E-4 ? new Vec3(Mth.lerp(0.5, lastHoriz.x, desired.x), 0.0, Mth.lerp(0.5, lastHoriz.z, desired.z)) : desired;
            this.lastImpulse = new Vec3(blended.x, impulse.y, blended.z);
            return this.lastImpulse;
         }
      } else {
         this.lastImpulse = impulse;
         return impulse;
      }
   }

   private static Vec3 lockToCourse(LocalPlayer player, Vec3 impulse) {
      Vec3 motion = player.getDeltaMovement();
      Vec3 horiz = new Vec3(motion.x, 0.0, motion.z);
      double len = horiz.length();
      if (len < 0.03) {
         horiz = new Vec3(player.getX() - player.xOld, 0.0, player.getZ() - player.zOld);
         len = horiz.length();
      }

      if (len < 1.0E-4) {
         return null;
      }

      horiz = horiz.normalize();
      double speed = Math.sqrt(impulse.x * impulse.x + impulse.z * impulse.z);
      Vec3 out = horiz.scale(speed);
      return new Vec3(out.x, impulse.y, out.z);
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      if (this.renderPredict.getValue()) {
         Minecraft mc = event.getClient();
         LocalPlayer player = mc.player;
         if (player != null) {
            AuraFeature aura = AuraFeature.getEnabled();
            LivingEntity target = aura == null ? null : aura.getTarget();
            if (target != null && isPredicting(player, target)) {
               Render3DUtil.ScreenPoint point = Render3DUtil.projectToScreen(mc, this.aimPoint(player, target));
               if (point != null) {
                  float size = 8.0F;
                  float half = size / 2.0F;
                  int accent = 0xFF000000 | Theme.getAccent();
                  Render2DUtil.rect(point.x() - half - 1.0F, point.y() - half - 1.0F, size + 2.0F, size + 2.0F).color(-1442840576).draw();
                  Render2DUtil.rect(point.x() - half, point.y() - half, size, size).color(accent).draw();
               }
            }
         }
      }
   }
}
