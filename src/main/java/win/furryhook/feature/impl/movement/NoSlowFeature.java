package win.furryhook.feature.impl.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.PlayerTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.ModeSetting;

public final class NoSlowFeature extends Feature {
   public static final String MODE_GRIM = "Grim";
   public static final String MODE_REALLY_WORLD = "ReallyWorld";
   public static final String MODE_LONY_GRIEF = "LonyGrief";
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Grim", "Grim", "ReallyWorld", "LonyGrief"));
   private int useTicks;
   private boolean reallyWorldBypass;
   private int lastLonySpoofTick = Integer.MIN_VALUE;

   public NoSlowFeature() {
      super("NoSlow", "Prevents item use from slowing movement", FeatureCategory.MOVEMENT, -1);
   }

   public static NoSlowFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(NoSlowFeature.class);
   }

   public static boolean shouldBypassSlowdown(LocalPlayer player) {
      NoSlowFeature feature = getEnabled();
      if (feature == null || player == null || !player.isUsingItem()) {
         return false;
      }

      if (feature.mode.is("ReallyWorld")) {
         return feature.reallyWorldBypass;
      }

      if (feature.mode.is("LonyGrief")) {
         feature.sendLonySpoof(player);
      }

      return true;
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre() && this.mode.is("ReallyWorld")) {
         LocalPlayer player = event.getPlayer();
         if (!player.isFallFlying()) {
            if (player.isUsingItem()) {
               this.useTicks++;
            } else {
               this.useTicks = 0;
            }
         }

         this.reallyWorldBypass = this.useTicks == 1;
         if (this.useTicks >= 2) {
            this.useTicks = 0;
            this.reallyWorldBypass = false;
         }
      }
   }

   private void sendLonySpoof(LocalPlayer player) {
      if (player.connection != null && this.lastLonySpoofTick != player.tickCount) {
         InteractionHand active = player.getUsedItemHand();
         InteractionHand opposite = active == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         player.connection.send(new ServerboundUseItemPacket(opposite, 0, player.getYRot(), player.getXRot()));
         this.lastLonySpoofTick = player.tickCount;
      }
   }

   @Override
   protected void onDisable() {
      this.useTicks = 0;
      this.reallyWorldBypass = false;
      this.lastLonySpoofTick = Integer.MIN_VALUE;
   }
}
