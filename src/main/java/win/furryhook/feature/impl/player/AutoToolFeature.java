package win.furryhook.feature.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.combat.CrystalAuraFeature;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.mixin.accessor.MultiPlayerGameModeAccessor;
import win.furryhook.utils.inventory.InventorySwap;

public final class AutoToolFeature extends Feature {
   private static final int HOTBAR_SIZE = 9;
   private static final int INVENTORY_SIZE = 36;
   private static final int ST_IDLE = 0;
   private static final int ST_SWAPPING_IN = 1;
   private static final int ST_READY = 2;
   private static final int ST_SWAPPING_OUT = 3;
   public final BooleanSetting useInventory = this.register(new BooleanSetting("Use Inventory", true));
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Silent", "Silent", "Normal"));
   private int originalHotbarSlot = -1;
   private int swappedInventorySlot = -1;
   private int silentServerSlot = -1;
   private int swapState = 0;

   public AutoToolFeature() {
      super("AutoTool", "Picks the best tool for the targeted block", FeatureCategory.PLAYER, -1);
   }

   @Override
   protected void onDisable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (this.swappedInventorySlot != -1 && this.originalHotbarSlot != -1 && !InventorySwap.isBusy()) {
            InventorySwap.moveToHotbar(this.swappedInventorySlot, this.originalHotbarSlot);
         }

         this.restoreSilentServerSlot(player);
         if (this.originalHotbarSlot != -1) {
            player.getInventory().setSelectedSlot(this.originalHotbarSlot);
         }
      }

      this.originalHotbarSlot = -1;
      this.swappedInventorySlot = -1;
      this.silentServerSlot = -1;
      this.swapState = 0;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && !player.isCreative() && client.gui.screen() == null) {
         boolean breaking = this.crystalAuraBreakState(client) != null || this.isBreakingBlock(client);
         if (InventorySwap.isBusy()) {
            if (breaking) {
               this.gateDestroy();
            }
         } else if (this.swapState == 3) {
            this.swappedInventorySlot = -1;
            this.swapState = 0;
         } else if (!breaking) {
            this.restoreNow(player);
         } else {
            BlockState blockState = this.crystalAuraBreakState(client) != null
               ? this.crystalAuraBreakState(client)
               : client.level.getBlockState(((BlockHitResult)client.hitResult).getBlockPos());
            switch (this.swapState) {
               case 0: {
                  int bestSlot = this.findBestToolSlot(player, blockState);
                  if (bestSlot == -1) {
                     return;
                  }

                  if (this.originalHotbarSlot == -1) {
                     this.originalHotbarSlot = player.getInventory().getSelectedSlot();
                  }

                  if (bestSlot < 9) {
                     this.selectHotbarTool(player, bestSlot);
                     return;
                  }

                  if (!this.useInventory.getValue()) {
                     return;
                  }

                  InventorySwap.moveToHotbar(bestSlot, this.originalHotbarSlot);
                  this.swappedInventorySlot = bestSlot;
                  this.swapState = 1;
                  this.gateDestroy();
                  break;
               }
               case 1:
                  if (InventorySwap.isBusy()) {
                     this.gateDestroy();
                     return;
                  }

                  player.getInventory().setSelectedSlot(this.originalHotbarSlot);
                  this.swapState = 2;
                  break;
               case 2:
                  int bestSlot = this.findBestToolSlot(player, blockState);
                  if (bestSlot == this.swappedInventorySlot || bestSlot == this.originalHotbarSlot) {
                     return;
                  }

                  if (bestSlot == -1 || bestSlot >= 9 && !this.useInventory.getValue()) {
                     return;
                  }

                  InventorySwap.moveToHotbar(this.swappedInventorySlot, this.originalHotbarSlot);
                  this.swapState = 3;
            }
         }
      } else {
         this.restoreNow(player);
      }
   }

   public boolean swapForBlock(BlockState state) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (this.isEnabled() && player != null && state != null) {
         int bestSlot = this.findBestToolSlot(player, state);
         if (bestSlot < 0) {
            return false;
         }

         if (this.originalHotbarSlot == -1) {
            this.originalHotbarSlot = player.getInventory().getSelectedSlot();
         }

         if (bestSlot < 9) {
            this.selectHotbarTool(player, bestSlot);
            return true;
         }

         if (!this.useInventory.getValue()) {
            return false;
         }

         if (this.swapState == 2 && this.swappedInventorySlot == bestSlot) {
            return true;
         }

         if (!InventorySwap.isBusy()) {
            InventorySwap.moveToHotbar(bestSlot, this.originalHotbarSlot);
            this.swappedInventorySlot = bestSlot;
            this.swapState = 1;
         }

         this.gateDestroy();
         return this.swapState == 2;
      } else {
         return false;
      }
   }

   private boolean isBreakingBlock(Minecraft client) {
      return client.options.keyAttack.isDown() && client.hitResult != null && client.hitResult.getType() == Type.BLOCK;
   }

   private BlockState crystalAuraBreakState(Minecraft client) {
      CrystalAuraFeature aura = FeatureManager.INSTANCE.getFeature(CrystalAuraFeature.class);
      if (aura != null && aura.isEnabled() && aura.getAutoKrot().getValue() && client.level != null) {
         CrystalAuraFeature.PlaceData data = aura.getLastData();
         return data != null && data.blockPos() != null && data.placeResult() == CrystalAuraFeature.PlaceResult.BREAK
            ? client.level.getBlockState(data.blockPos())
            : null;
      } else {
         return null;
      }
   }

   private int findBestToolSlot(LocalPlayer player, BlockState state) {
      int limit = this.useInventory.getValue() ? 36 : 9;
      int bestSlot = -1;
      float bestSpeed = 1.0F;

      for (int slot = 0; slot < limit; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (!stack.isEmpty()) {
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
               bestSpeed = speed;
               bestSlot = slot;
            }
         }
      }

      return bestSlot;
   }

   private void selectHotbarTool(LocalPlayer player, int slot) {
      Minecraft client = Minecraft.getInstance();
      if (this.mode.is("Normal")) {
         this.restoreSilentServerSlot(player);
         player.getInventory().setSelectedSlot(slot);
         if (client.gameMode instanceof MultiPlayerGameModeAccessor acc) {
            acc.invokerEnsureHasSentCarriedItem();
         }
      } else if (slot == player.getInventory().getSelectedSlot()) {
         this.restoreSilentServerSlot(player);
      } else {
         if (this.silentServerSlot != slot) {
            player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            this.silentServerSlot = slot;
         }
      }
   }

   private void restoreNow(LocalPlayer player) {
      boolean hasSwap = this.swappedInventorySlot != -1 && this.originalHotbarSlot != -1;
      if (hasSwap) {
         if (!InventorySwap.isBusy()) {
            if (this.swapState != 3) {
               InventorySwap.moveToHotbar(this.swappedInventorySlot, this.originalHotbarSlot);
               this.swapState = 3;
            }
         }
      } else {
         if (player != null) {
            this.restoreSilentServerSlot(player);
            if (this.originalHotbarSlot != -1) {
               player.getInventory().setSelectedSlot(this.originalHotbarSlot);
            }
         }

         this.originalHotbarSlot = -1;
         this.swapState = 0;
      }
   }

   private void gateDestroy() {
      Minecraft client = Minecraft.getInstance();
      if (client.gameMode instanceof MultiPlayerGameModeAccessor acc) {
         acc.setDestroyDelay(2);
      }
   }

   private void restoreSilentServerSlot(LocalPlayer player) {
      if (this.silentServerSlot != -1) {
         player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
         this.silentServerSlot = -1;
      }
   }
}
