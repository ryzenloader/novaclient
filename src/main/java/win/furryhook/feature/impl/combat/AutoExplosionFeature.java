package win.furryhook.feature.impl.combat;

import java.util.Iterator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.packet.PacketSendEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.utils.inventory.InventoryUtil;

public final class AutoExplosionFeature extends Feature implements MinecraftContext {
   public final BooleanSetting test = this.register(new BooleanSetting("Test", true));
   public final ModeSetting rotate = this.register(new ModeSetting("Ротация", "Vanilla", "Vanilla", "Без ротации"));
   private BlockPos targetPos;
   private int targetSlot = -1;
   private boolean blocked;
   private AABB crystalArea;
   private int crystalWaitTicks;
   private Entity pendingAttack;
   private float pendingYaw;
   private float pendingPitch;
   private static final int CRYSTAL_WAIT_MAX = 10;

   public AutoExplosionFeature() {
      super("AutoExplosion", "Автоматически ставит и взрывает кристалл", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.targetPos = null;
      this.targetSlot = -1;
      this.blocked = false;
      this.crystalArea = null;
      this.crystalWaitTicks = 0;
      this.pendingAttack = null;
      RotationContext.release(this);
      RotationContext.clearIfOwner(this);
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
         LocalPlayer player = this.player();
         if (player != null && this.level() != null) {
            BlockPos clickedPos = packet.getHitResult().getBlockPos();
            Block clickedBlock = this.level().getBlockState(clickedPos).getBlock();
            if (event.getPhase() == PacketSendEvent.Phase.PRE) {
               if (clickedBlock == Blocks.OBSIDIAN || clickedBlock == Blocks.BEDROCK) {
                  this.crystalArea = new AABB(clickedPos.above()).inflate(0.5);
                  this.crystalWaitTicks = 10;
               }

               if (this.test.getValue()
                  && clickedBlock == Blocks.OBSIDIAN
                  && this.level().getBlockState(clickedPos.above()).isAir()
                  && player.getMainHandItem().getItem() != Items.END_CRYSTAL
                  && player.getOffhandItem().getItem() != Items.END_CRYSTAL) {
                  int crystalSlot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.getItem() == Items.END_CRYSTAL);
                  if (crystalSlot != -1) {
                     BlockHitResult hit = packet.getHitResult();
                     mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, crystalSlot, 40, ContainerInput.SWAP, player);
                     mc.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hit);
                     player.swing(InteractionHand.OFF_HAND);
                     mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, crystalSlot, 40, ContainerInput.SWAP, player);
                     event.cancel();
                  }
               }
            } else {
               if ((player.getMainHandItem().getItem() == Items.END_CRYSTAL || player.getOffhandItem().getItem() == Items.END_CRYSTAL)
                  && (clickedBlock == Blocks.OBSIDIAN || clickedBlock == Blocks.BEDROCK)) {
                  this.crystalArea = new AABB(clickedPos.above()).inflate(0.5);
                  this.crystalWaitTicks = 10;
               }

               if (player.getMainHandItem().getItem() == Items.OBSIDIAN || player.getOffhandItem().getItem() == Items.OBSIDIAN) {
                  int crystalSlot = this.findHotbarItem(player, Items.END_CRYSTAL);
                  if (crystalSlot != -1) {
                     this.targetPos = clickedPos.relative(packet.getHitResult().getDirection());
                     this.targetSlot = crystalSlot;
                     this.blocked = true;
                  }
               }
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = this.player();
      if (player != null && this.level() != null) {
         if (this.targetPos != null) {
            if (this.level().getBlockState(this.targetPos).isAir()) {
               this.targetPos = null;
            } else if (this.blocked) {
               this.blocked = false;
            } else {
               this.placeCrystal(player, this.targetPos, this.targetSlot);
               this.crystalArea = new AABB(this.targetPos.above()).inflate(0.5);
               this.crystalWaitTicks = 10;
               this.targetPos = null;
            }
         }

         if (this.pendingAttack != null) {
            if (this.pendingAttack.isAlive() && !this.pendingAttack.isRemoved()) {
               if (!this.rotate.is("Без ротации")) {
                  RotationContext.claim(this, 5);
                  RotationContext.setRotation(this.pendingYaw, this.pendingPitch);
               }

               mc.gameMode.attack(player, this.pendingAttack);
               player.swing(InteractionHand.MAIN_HAND);
            }

            this.pendingAttack = null;
            this.crystalArea = null;
            this.crystalWaitTicks = 0;
            RotationContext.release(this);
            RotationContext.clearIfOwner(this);
         }

         if (this.crystalArea != null) {
            this.tryExplodeArea(player, this.crystalArea);
         }

         if (this.crystalArea == null && this.targetPos == null && this.pendingAttack == null && RotationContext.isOwner(this)) {
            RotationContext.release(this);
            RotationContext.clearIfOwner(this);
         }
      }
   }

   private void tryExplodeArea(LocalPlayer player, AABB area) {
      EndCrystal found = null;
      Iterator center = this.level().getEntitiesOfClass(EndCrystal.class, area).iterator();
      if (center.hasNext()) {
         EndCrystal crystal = (EndCrystal)center.next();
         found = crystal;
      }

      if (found == null) {
         if (--this.crystalWaitTicks <= 0) {
            this.crystalArea = null;
         }
      } else if (this.rotate.is("Без ротации")) {
         mc.gameMode.attack(player, found);
         player.swing(InteractionHand.MAIN_HAND);
         this.crystalArea = null;
         this.crystalWaitTicks = 0;
      } else {
         Vec3 centerx = found.getBoundingBox().getCenter();
         Vec3 delta = centerx.subtract(player.getEyePosition());
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         this.pendingYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
         this.pendingPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
         this.pendingAttack = found;
         RotationContext.claim(this, 5);
         RotationContext.setRotation(this.pendingYaw, this.pendingPitch);
      }
   }

   private void placeCrystal(LocalPlayer player, BlockPos obsidianPos, int crystalSlot) {
      int prevSlot = player.getInventory().getSelectedSlot();
      player.getInventory().setSelectedSlot(crystalSlot);
      Vec3 hitVec = Vec3.atCenterOf(obsidianPos);
      if (!this.rotate.is("Без ротации")) {
         this.rotateTo(player, hitVec);
      }

      BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, obsidianPos, false);
      mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
      player.swing(InteractionHand.MAIN_HAND);
      player.getInventory().setSelectedSlot(prevSlot);
   }

   private void rotateTo(LocalPlayer player, Vec3 pos) {
      Vec3 delta = pos.subtract(player.getEyePosition());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      if (RotationContext.claim(this, 5)) {
         RotationContext.setRotation(yaw, pitch);
      }
   }

   private int findHotbarItem(LocalPlayer player, Item item) {
      for (int i = 0; i < 9; i++) {
         if (player.getInventory().getItem(i).getItem() == item) {
            return i;
         }
      }

      return -1;
   }
}
