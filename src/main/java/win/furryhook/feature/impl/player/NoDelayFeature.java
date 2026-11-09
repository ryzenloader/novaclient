package win.furryhook.feature.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.mixin.accessor.LivingEntityAccessor;
import win.furryhook.mixin.accessor.MinecraftAccessor;

public final class NoDelayFeature extends Feature {
   public final BooleanSetting jump = this.register(new BooleanSetting("Jump", true));
   public final BooleanSetting experienceBottle = this.register(new BooleanSetting("Experience Bottle", true));
   public final BooleanSetting crystals = this.register(new BooleanSetting("Crystals", true));
   public final BooleanSetting place = this.register(new BooleanSetting("Place", false));

   public NoDelayFeature() {
      super("NoDelay", "Removes delays for selected items and jumping", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      if (client.player != null) {
         if (this.jump.getValue()) {
            ((LivingEntityAccessor)client.player).setNoJumpDelay(0);
         }

         Item item = client.player.getMainHandItem().getItem();
         if (this.removesUseDelay(item)) {
            ((MinecraftAccessor)client).setRightClickDelay(0);
         }
      }
   }

   private boolean removesUseDelay(Item item) {
      return item instanceof BlockItem && this.place.getValue()
         || item == Items.END_CRYSTAL && this.crystals.getValue()
         || item == Items.EXPERIENCE_BOTTLE && this.experienceBottle.getValue();
   }
}
