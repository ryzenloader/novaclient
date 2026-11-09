package win.furryhook.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class CrystalOptimizerFeature extends Feature {
   public final NumberSetting renderDistance = this.register(new NumberSetting("Дистанция", 24.0, 4.0, 128.0, 1.0, " бл"));
   public final BooleanSetting hideAll = this.register(new BooleanSetting("Скрыть все", false));

   public CrystalOptimizerFeature() {
      super("CrystalOptimizer", "Режет отрисовку эндер-кристаллов", FeatureCategory.VISUAL, -1);
   }

   public static CrystalOptimizerFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(CrystalOptimizerFeature.class);
   }

   public static boolean shouldRender(EndCrystal crystal) {
      CrystalOptimizerFeature feature = getEnabled();
      if (feature == null || crystal == null) {
         return true;
      }

      if (feature.hideAll.getValue()) {
         return false;
      }

      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return true;
      }

      double limit = feature.renderDistance.getValue();
      return player.distanceToSqr(crystal) <= limit * limit;
   }
}
