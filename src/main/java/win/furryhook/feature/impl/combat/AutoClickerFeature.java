package win.furryhook.feature.impl.combat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.mixin.accessor.KeyMappingAccessor;

public final class AutoClickerFeature extends Feature {
   public final BooleanSetting leftMouse = this.register(new BooleanSetting("Left Mouse", true));
   public final BooleanSetting rightMouse = this.register(new BooleanSetting("Right Mouse", false));
   public final ModeSetting clickMode = this.register(new ModeSetting("Mode", "Normal", "Normal", "Jitter", "Butterfly"));
   public final MultiSelectSetting targets = this.register(
      new MultiSelectSetting("Targets", List.of("Players", "Mobs"), "Players", "Mobs", "Animals", "Invisible")
   );
   public final NumberSetting cps = this.register(new NumberSetting("CPS", 12.0, 1.0, 20.0, 1.0, ""));
   private long nextLeftClickAt;
   private long nextRightClickAt;
   private boolean butterflyFast;

   public AutoClickerFeature() {
      super("AutoClicker", "Automatic mouse clicking", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.nextLeftClickAt = 0L;
      this.nextRightClickAt = 0L;
      this.butterflyFast = false;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      if (client.player != null && client.level != null && client.gui.screen() == null && !client.player.isHandsBusy()) {
         long now = System.nanoTime();
         if (this.leftMouse.getValue() && now >= this.nextLeftClickAt && this.validCrosshairTarget(client.crosshairPickEntity)) {
            click(client.options.keyAttack);
            this.nextLeftClickAt = now + this.nextDelayNanos();
         }

         if (this.rightMouse.getValue() && now >= this.nextRightClickAt) {
            click(client.options.keyUse);
            this.nextRightClickAt = now + this.nextDelayNanos();
         }
      }
   }

   private boolean validCrosshairTarget(Entity entity) {
      if (!(entity instanceof LivingEntity living && living.isAlive())) {
         return false;
      } else if (living.isInvisible() && !this.targets.isSelected("Invisible")) {
         return false;
      } else if (living instanceof Player) {
         return this.targets.isSelected("Players");
      } else {
         return living instanceof AgeableMob ? this.targets.isSelected("Animals") : living instanceof Mob && this.targets.isSelected("Mobs");
      }
   }

   private long nextDelayNanos() {
      double baseMillis = 1000.0 / this.cps.getValue();

      double multiplier = switch ((String)this.clickMode.getValue()) {
         case "Jitter" -> ThreadLocalRandom.current().nextDouble(0.85, 1.16);
         case "Butterfly" -> {
            this.butterflyFast = !this.butterflyFast;
            yield this.butterflyFast ? 0.65 : 1.35;
         }
         default -> 1.0;
      };
      return Math.max(1L, Math.round(baseMillis * multiplier * 1000000.0));
   }

   private static void click(KeyMapping mapping) {
      KeyMapping.click(((KeyMappingAccessor)mapping).getKey());
   }
}
