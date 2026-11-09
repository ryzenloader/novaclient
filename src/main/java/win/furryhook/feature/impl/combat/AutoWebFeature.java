package win.furryhook.feature.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.combat.PredictUtils;
import win.furryhook.utils.combat.TargetFilter;
import win.furryhook.utils.combat.TargetUtil;
import win.furryhook.utils.inventory.InventorySwap;
import win.furryhook.utils.inventory.InventoryUtil;

public final class AutoWebFeature extends Feature {
   private final BooleanSetting rotate = this.register(new BooleanSetting("Ротация", true));
   private final BooleanSetting doubleWeb = this.register(new BooleanSetting("Двойная паутина", true));
   private final BooleanSetting throughWalls = this.register(new BooleanSetting("Сквозь стены", true));
   private final BooleanSetting ignoreSelf = this.register(new BooleanSetting("Игнорировать себя", true));
   private final NumberSetting range = this.register(new NumberSetting("Радиус", 4.5, 1.0, 6.0, 0.5, ""));
   private final NumberSetting predictTicks = this.register(new NumberSetting("Прогноз", 2.0, 0.0, 6.0, 1.0, " ticks"));
   private static final double PLACE_REACH = 5.0;
   private static final int HOTBAR_SIZE = 9;
   private int slotToRestore = -1;
   private int ticksToReturnSlot = -1;
   private BlockPos pendingUpperPos = null;
   private BlockHitResult pendingUseHit = null;
   private static final int RESTORE_TICKS = 2;

   public AutoWebFeature() {
      super("AutoWeb", "Ставит паутину под ноги цели", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.pendingUpperPos = null;
      this.pendingUseHit = null;
      this.ticksToReturnSlot = -1;
      this.restoreSlot();
      RotationContext.release(this);
      RotationContext.disengageIfOwner(this);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      ClientLevel level = mc.level;
      if (player != null && level != null && mc.gameMode != null) {
         if (this.ticksToReturnSlot > 0) {
            this.ticksToReturnSlot--;
            if (this.ticksToReturnSlot == 0) {
               this.restoreSlot();
            }
         }

         if (this.pendingUseHit != null) {
            BlockHitResult hit = this.pendingUseHit;
            this.pendingUseHit = null;
            this.performWebUse(mc, player, hit);
            if (this.pendingUpperPos != null) {
               BlockPos upper = this.pendingUpperPos;
               this.pendingUpperPos = null;
               this.pendingUseHit = this.findPlaceHit(player, level, upper);
               if (this.pendingUseHit == null) {
                  this.ticksToReturnSlot = 2;
               }
            } else {
               this.ticksToReturnSlot = 2;
            }
         } else if (!InventorySwap.isBusy()) {
            LivingEntity target = this.findTarget(player, level);
            if (target == null) {
               RotationContext.release(this);
               if (this.rotate.getValue()) {
                  RotationContext.disengageIfOwner(this);
               }
            } else {
               int webSlot = this.getWebSlot(player);
               if (webSlot == -1) {
                  int containerSlot = InventoryUtil.findInventorySlot(player, stack -> stack.getItem() == Items.COBWEB);
                  if (containerSlot != -1) {
                     InventorySwap.moveToHotbar(containerSlot, this.findSwapTargetSlot(player));
                  } else {
                     RotationContext.release(this);
                     if (this.rotate.getValue()) {
                        RotationContext.disengageIfOwner(this);
                     }
                  }
               } else {
                  Vec3 predicted = PredictUtils.predict(target, target.position(), this.predictTicks.getValue().floatValue());
                  BlockPos feet = new BlockPos((int)Math.floor(predicted.x), (int)Math.floor(predicted.y), (int)Math.floor(predicted.z));
                  BlockHitResult lowerHit = this.findPlaceHit(player, level, feet);
                  if (lowerHit == null) {
                     RotationContext.release(this);
                     if (this.rotate.getValue()) {
                        RotationContext.disengageIfOwner(this);
                     }
                  } else {
                     if (this.doubleWeb.getValue()) {
                        this.pendingUpperPos = feet.above();
                     }

                     if (this.slotToRestore == -1) {
                        this.slotToRestore = player.getInventory().getSelectedSlot();
                     }

                     if (player.getInventory().getSelectedSlot() != webSlot) {
                        player.getInventory().setSelectedSlot(webSlot);
                        if (player.connection != null) {
                           player.connection.send(new ServerboundSetCarriedItemPacket(webSlot));
                        }
                     }

                     this.pendingUseHit = lowerHit;
                  }
               }
            }
         }
      }
   }

   private void restoreSlot() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && this.slotToRestore >= 0 && this.slotToRestore <= 8) {
         player.getInventory().setSelectedSlot(this.slotToRestore);
         if (player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(this.slotToRestore));
         }
      }

      this.slotToRestore = -1;
   }

   private LivingEntity findTarget(LocalPlayer player, ClientLevel level) {
      TargetFilter filter = TargetFilter.builder()
         .targetsPlayers(true)
         .targetsFriends(false)
         .targetsNakedPlayers(true)
         .targetsInvisibles(false)
         .targetsVillagers(false)
         .targetsMonsters(false)
         .targetsAnimals(false)
         .targetsTeams(true)
         .distanceWeight(1.0)
         .healthWeight(0.0)
         .armorWeight(0.0)
         .fovWeight(0.0)
         .build();
      return TargetUtil.getBestTarget(player, level, this.range.getValue(), filter, entity -> true);
   }

   private BlockHitResult findPlaceHit(LocalPlayer player, ClientLevel level, BlockPos placePos) {
      if (!this.canPlaceAt(player, level, placePos)) {
         return null;
      }

      AutoWebFeature.PlaceData placeData = this.findPlaceData(player, level, placePos);
      if (placeData == null) {
         return null;
      }

      float[] rot = this.getRotations(player, placeData.hitVec());
      if (this.rotate.getValue() && RotationContext.claim(this, 5)) {
         RotationContext.setRotation(rot[0], rot[1]);
      }

      return !this.throughWalls.getValue() && !this.blockHit(player, level, rot, placeData.neighbourPos())
         ? null
         : new BlockHitResult(placeData.hitVec(), placeData.side(), placeData.neighbourPos(), false);
   }

   private void performWebUse(Minecraft mc, LocalPlayer player, BlockHitResult hit) {
      if (player.connection != null) {
         player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0));
         BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
         if (mc.level != null) {
            mc.level.setBlock(placed, Blocks.COBWEB.defaultBlockState(), 3);
         }
      }
   }

   private boolean canPlaceAt(LocalPlayer player, ClientLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return !state.isAir() && !state.canBeReplaced() ? false : !player.getBoundingBox().inflate(0.05).contains(Vec3.atCenterOf(pos));
   }

   private AutoWebFeature.PlaceData findPlaceData(LocalPlayer player, ClientLevel level, BlockPos placePos) {
      for (Direction side : Direction.values()) {
         BlockPos neighbour = placePos.relative(side);
         Direction hitSide = side.getOpposite();
         BlockState neighbourState = level.getBlockState(neighbour);
         if (this.isValidSupport(level, neighbour, neighbourState, hitSide)) {
            Vec3 faceCenter = new Vec3(
               neighbour.getX() + 0.5 + hitSide.getStepX() * 0.5,
               neighbour.getY() + 0.5 + hitSide.getStepY() * 0.5,
               neighbour.getZ() + 0.5 + hitSide.getStepZ() * 0.5
            );
            return new AutoWebFeature.PlaceData(neighbour, hitSide, faceCenter);
         }
      }

      return null;
   }

   private boolean isValidSupport(ClientLevel level, BlockPos pos, BlockState state, Direction face) {
      if (state.isAir()) {
         return false;
      } else if (state.getFluidState() != null && !state.getFluidState().isEmpty()) {
         return false;
      } else {
         Block block = state.getBlock();
         if (block == Blocks.COBWEB) {
            return true;
         } else if (!state.isFaceSturdy(level, pos, face)) {
            return false;
         } else if (block instanceof ButtonBlock) {
            return false;
         } else if (block instanceof LeverBlock) {
            return false;
         } else if (block instanceof TorchBlock) {
            return false;
         } else if (block instanceof WallTorchBlock) {
            return false;
         } else if (block instanceof RedstoneTorchBlock) {
            return false;
         } else if (block instanceof FenceBlock) {
            return false;
         } else if (block instanceof FenceGateBlock) {
            return false;
         } else if (block instanceof DoorBlock) {
            return false;
         } else if (block instanceof TrapDoorBlock) {
            return false;
         } else if (block instanceof CarpetBlock) {
            return false;
         } else {
            return block instanceof VineBlock ? false : !(block instanceof LadderBlock);
         }
      }
   }

   private boolean blockHit(LocalPlayer player, ClientLevel level, float[] rot, BlockPos pos) {
      Vec3 eye = player.getEyePosition();
      Vec3 dir = Vec3.directionFromRotation(rot[1], rot[0]);
      Vec3 end = eye.add(dir.scale(5.0));
      HitResult hit = level.clip(new ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE, Fluid.NONE, player));
      return hit.getType() == Type.BLOCK && pos.equals(((BlockHitResult)hit).getBlockPos());
   }

   private float[] getRotations(LocalPlayer player, Vec3 point) {
      Vec3 eyes = player.getEyePosition();
      Vec3 delta = point.subtract(eyes);
      double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
      float pitch = (float)Math.toDegrees(Math.atan2(-delta.y, distXZ));
      if (pitch > 90.0F) {
         pitch = 90.0F;
      }

      if (pitch < -90.0F) {
         pitch = -90.0F;
      }

      return new float[]{yaw, pitch};
   }

   private int getWebSlot(LocalPlayer player) {
      for (int i = 0; i < 9; i++) {
         if (player.getInventory().getItem(i).getItem() == Items.COBWEB) {
            return i;
         }
      }

      return -1;
   }

   private int findSwapTargetSlot(LocalPlayer player) {
      int selected = player.getInventory().getSelectedSlot();

      for (int i = 0; i < 9; i++) {
         if (i != selected && player.getInventory().getItem(i).isEmpty()) {
            return i;
         }
      }

      for (int i = 0; i < 9; i++) {
         if (i != selected && player.getInventory().getItem(i).getItem() != Items.COBWEB) {
            return i;
         }
      }

      return selected;
   }

   private record PlaceData(BlockPos neighbourPos, Direction side, Vec3 hitVec) {
   }
}
