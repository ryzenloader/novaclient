package win.furryhook.feature.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class StrafeFeature extends Feature {
   private static final String MODE_AIR = "В воздухе";
   private static final String MODE_ALWAYS = "Всегда";
   private static final String MODE_ELYTRA = "Элитра";
   private static final double INPUT_EPSILON = 1.0E-4;
   public final ModeSetting mode = this.register(new ModeSetting("Режим", "В воздухе", "В воздухе", "Всегда", "Элитра"));
   public final NumberSetting speed = this.register(new NumberSetting("Скорость", 5.6, 0.5, 30.0, 0.1, " бл/с"));
   public final NumberSetting elytraSpeed = this.register(
      new NumberSetting("Боковая", 0.08, 0.01, 0.6, 0.01, " бл/т").visibleWhen(() -> this.mode.is("Элитра"))
   );
   public final NumberSetting accel = this.register(new NumberSetting("Разгон", 100.0, 5.0, 100.0, 5.0, "%").visibleWhen(() -> !this.mode.is("Элитра")));
   public final BooleanSetting keepMomentum = this.register(new BooleanSetting("Не тормозить", false).visibleWhen(() -> !this.mode.is("Элитра")));
   public final BooleanSetting onlyWithInput = this.register(new BooleanSetting("Только с вводом", true));
   public final BooleanSetting holdSprint = this.register(new BooleanSetting("Держать спринт", true).visibleWhen(() -> !this.mode.is("Элитра")));

   public StrafeFeature() {
      super("Strafe", "Стрейф с задаваемой скоростью", FeatureCategory.MOVEMENT, -1);
   }

   public static StrafeFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(StrafeFeature.class);
   }

   public double speedPerTick() {
      return this.speed.getValue() / 20.0;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && !player.isSpectator() && !player.getAbilities().flying) {
         if (!player.isPassenger() && !player.isInWater() && !player.isInLava() && !player.onClimbable()) {
            Vec2 input = player.input.getMoveVector();
            double forward = input.y;
            double strafe = input.x;
            boolean moving = Math.abs(forward) > 1.0E-4 || Math.abs(strafe) > 1.0E-4;
            if (moving || !this.onlyWithInput.getValue()) {
               if (player.isFallFlying()) {
                  if (this.mode.is("Элитра") && moving) {
                     this.applyElytra(player, strafe);
                  }
               } else if (!this.mode.is("Элитра")) {
                  if (!this.mode.is("В воздухе") || !player.onGround()) {
                     this.applyGround(player, forward, strafe, moving);
                  }
               }
            }
         }
      }
   }

   private void applyGround(LocalPlayer player, double forward, double strafe, boolean moving) {
      Vec3 motion = player.getDeltaMovement();
      double wanted = this.speedPerTick();
      if (this.keepMomentum.getValue()) {
         wanted = Math.max(wanted, Math.hypot(motion.x, motion.z));
      }

      double current = Math.hypot(motion.x, motion.z);
      double blend = Mth.clamp(this.accel.getValue() / 100.0, 0.05, 1.0);
      double magnitude = moving ? current + (wanted - current) * blend : current * (1.0 - blend);
      double direction = moveDirection(player.getYRot(), forward, strafe);
      double x = -Math.sin(direction) * magnitude;
      double z = Math.cos(direction) * magnitude;
      player.setDeltaMovement(x, motion.y, z);
      if (this.holdSprint.getValue() && moving && forward > 0.0 && player.getFoodData().getFoodLevel() > 6) {
         player.setSprinting(true);
      }
   }

   private void applyElytra(LocalPlayer player, double strafe) {
      if (!(Math.abs(strafe) <= 1.0E-4)) {
         double yaw = Math.toRadians(player.getYRot());
         double x = Math.cos(yaw);
         double z = Math.sin(yaw);
         double step = this.elytraSpeed.getValue() * Math.signum(strafe);
         Vec3 motion = player.getDeltaMovement();
         player.setDeltaMovement(motion.x + x * step, motion.y, motion.z + z * step);
      }
   }

   private static double moveDirection(float yaw, double forward, double strafe) {
      float heading = yaw;
      if (forward < 0.0) {
         heading += 180.0F;
      }

      float sign = 1.0F;
      if (forward < 0.0) {
         sign = -0.5F;
      } else if (forward > 0.0) {
         sign = 0.5F;
      }

      if (strafe > 0.0) {
         heading -= 90.0F * sign;
      }

      if (strafe < 0.0) {
         heading += 90.0F * sign;
      }

      return Math.toRadians(heading);
   }
}
