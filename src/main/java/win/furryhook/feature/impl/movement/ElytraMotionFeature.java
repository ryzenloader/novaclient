package win.furryhook.feature.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.PlayerTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.impl.combat.AuraFeature;

public final class ElytraMotionFeature extends Feature {
   private boolean waitTarget;

   public ElytraMotionFeature() {
      super("ElytraMotion", "Остановка перед целью на элитрах", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         LocalPlayer player = event.getPlayer();
         if (player != null) {
            AuraFeature aura = AuraFeature.getEnabled();
            LivingEntity target = aura != null ? aura.getTarget() : null;
            if (target == null) {
               if (!this.waitTarget) {
                  player.setNoGravity(false);
                  this.waitTarget = true;
               }
            } else {
               this.waitTarget = false;
               if (player.isFallFlying()) {
                  Vec3 targetCenter = target.getBoundingBox().getCenter().add(0.0, (target.getY() - target.yOld) * 2.0, 0.0);
                  double distance = player.getEyePosition(1.0F).distanceTo(targetCenter);
                  if (distance < 2.4F && !target.isFallFlying()) {
                     player.setDeltaMovement(0.0, 0.0, 0.0);
                     player.setNoGravity(true);
                  } else {
                     player.setNoGravity(false);
                  }
               } else {
                  player.setNoGravity(false);
               }
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         this.waitTarget = false;
         mc.player.setNoGravity(false);
      }
   }
}
