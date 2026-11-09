package win.furryhook.feature.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class TimerFeature extends Feature {
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Balance", "Classic", "Balance"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 1.05, 1.0, 1.2, 0.01, "x"));
   public final NumberSetting slowSpeed = this.register(new NumberSetting("Slow Speed", 0.6, 0.1, 0.99, 0.01, "x"));
   public final NumberSetting boostSpeed = this.register(new NumberSetting("Boost Speed", 1.2, 1.0, 1.2, 0.01, "x"));
   public final NumberSetting balanceCap = this.register(new NumberSetting("Balance Cap", 12.0, 1.0, 60.0, 1.0, " ticks"));
   private double rateAccumulator;
   private double slowAccumulator;
   private int balanceTicks;

   public TimerFeature() {
      super("Timer", "Changes the local tick rate", FeatureCategory.MOVEMENT, -1);
   }

   public static boolean shouldSkipBaseTick() {
      TimerFeature timer = FeatureManager.INSTANCE.getEnabled(TimerFeature.class);
      LocalPlayer player = Minecraft.getInstance().player;
      if (timer != null && player != null && timer.mode.is("Balance") && !hasMovementInput(player)) {
         timer.slowAccumulator = timer.slowAccumulator + timer.slowSpeed.getValue();
         if (timer.slowAccumulator >= 1.0) {
            timer.slowAccumulator--;
            return false;
         } else {
            timer.balanceTicks = Math.min(timer.balanceTicks + 1, timer.balanceCap.getValue().intValue());
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean consumeExtraTick() {
      TimerFeature timer = FeatureManager.INSTANCE.getEnabled(TimerFeature.class);
      if (timer == null) {
         return false;
      }

      double rate = timer.mode.is("Balance") ? timer.boostSpeed.getValue() : timer.speed.getValue();
      if (!timer.mode.is("Balance") || timer.balanceTicks != 0 && hasMovementInput(Minecraft.getInstance().player)) {
         timer.rateAccumulator += rate - 1.0;
         if (timer.rateAccumulator < 1.0) {
            return false;
         }

         timer.rateAccumulator--;
         if (timer.mode.is("Balance")) {
            timer.balanceTicks--;
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   protected void onDisable() {
      this.rateAccumulator = 0.0;
      this.slowAccumulator = 0.0;
      this.balanceTicks = 0;
   }

   private static boolean hasMovementInput(LocalPlayer player) {
      return player != null && player.input.getMoveVector().lengthSquared() > 0.0F;
   }
}
