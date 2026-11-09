package win.furryhook.feature.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class CrystalTapFeature extends Feature {
   private final BooleanSetting autoAddCrystal = this.register(new BooleanSetting("Автоматически ставить кристалл", true));
   private final NumberSetting addCooldown = this.register(new NumberSetting("Задержка на ставку", 1.0, 0.0, 5.0, 1.0, " ticks"));
   private final NumberSetting attackCooldown = this.register(new NumberSetting("Задержка на атаку", 1.0, 0.0, 5.0, 1.0, " ticks"));
   private int addCrystalCooldown;
   private int attackCrystalCooldown;

   public CrystalTapFeature() {
      super("CrystalTap", "Ставит и бьёт кристалл под курсором", FeatureCategory.COMBAT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && mc.gameMode != null) {
         if (this.autoAddCrystal.getValue()) {
            this.addCrystal(mc, player);
         }

         if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystal) {
            if (this.attackCrystalCooldown > 0) {
               this.attackCrystalCooldown--;
               return;
            }

            mc.gameMode.attack(player, entityHit.getEntity());
            player.swing(InteractionHand.MAIN_HAND);
            this.attackCrystalCooldown = this.attackCooldown.getValue().intValue();
         }
      }
   }

   private void addCrystal(Minecraft mc, LocalPlayer player) {
      if (mc.hitResult instanceof BlockHitResult blockHit) {
         BlockPos obsidian = blockHit.getBlockPos();
         BlockState state = mc.level.getBlockState(obsidian);
         if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
            if (!this.crystalOn(mc, obsidian)) {
               if (player.getMainHandItem().is(Items.END_CRYSTAL)) {
                  if (this.addCrystalCooldown > 0) {
                     this.addCrystalCooldown--;
                  } else {
                     BlockHitResult placeHit = new BlockHitResult(blockHit.getLocation(), blockHit.getDirection(), obsidian, true);
                     mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, placeHit);
                     this.addCrystalCooldown = this.addCooldown.getValue().intValue();
                  }
               }
            }
         }
      }
   }

   private boolean crystalOn(Minecraft mc, BlockPos obsidian) {
      BlockPos crystalPos = obsidian.above();
      AABB box = new AABB(obsidian).inflate(1.0);

      for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, box)) {
         if (crystal.blockPosition().equals(crystalPos)) {
            return true;
         }
      }

      return false;
   }

   @Override
   protected void onDisable() {
      this.addCrystalCooldown = 0;
      this.attackCrystalCooldown = 0;
   }
}
