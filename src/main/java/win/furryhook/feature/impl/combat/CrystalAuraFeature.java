package win.furryhook.feature.impl.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Generated;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.render.Render3DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.player.AutoToolFeature;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.mixin.accessor.MultiPlayerGameModeAccessor;
import win.furryhook.utils.FriendManager;
import win.furryhook.utils.combat.MultipointUtil;
import win.furryhook.utils.render.world.WorldMeshRenderer;

public final class CrystalAuraFeature extends Feature implements MinecraftContext {
   private final ModeSetting rotate = this.register(new ModeSetting("Ротация", "Vanilla", "Vanilla", "Без ротации"));
   private final BooleanSetting autoKrot = this.register(new BooleanSetting("Делать подкоп", true));
   private final BooleanSetting safeYourSelf = this.register(new BooleanSetting("Не взрывать себя", false));
   private final BooleanSetting fastPlacement = this.register(new BooleanSetting("Fast placement", true));
   private int ticksToBack;
   private int lastSlot = -1;
   private int managedSlot = -1;
   private ClientLevel activeWorld;
   private LocalPlayer activePlayer;
   private BlockHitResult pendingPlaceHit;
   private BlockPos pendingVisualPos;
   private Item pendingPlaceItem;
   private int pendingPlaceSlot = -1;
   private Player pendingPlaceTarget;
   private CrystalAuraFeature.PlaceData lastData;
   private final ConcurrentLinkedQueue<CrystalAuraFeature.VisualData> visualDatas = new ConcurrentLinkedQueue<>();
   private float[] rotation;
   private static final double DIAMETER = 12.0;

   public CrystalAuraFeature() {
      super("CrystalAura", "Автоматически ставит обсидиан, кристаллы и подкапывает", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      RotationContext.disengage();
      this.clearPendingPlacement();
      if (mc.gameMode != null) {
         try {
            mc.gameMode.stopDestroyBlock();
         } catch (Throwable var2) {
         }
      }

      if (this.lastSlot != -1
         && mc.player != null
         && mc.player == this.activePlayer
         && mc.level == this.activeWorld
         && mc.player.isAlive()
         && !mc.player.isUsingItem()
         && mc.player.getInventory().getSelectedSlot() == this.managedSlot) {
         mc.player.getInventory().setSelectedSlot(this.lastSlot);
      }

      this.clearPlacementState();
      this.activeWorld = null;
      this.activePlayer = null;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer self = mc.player;
      ClientLevel world = mc.level;
      if (self != this.activePlayer || world != this.activeWorld || self == null || world == null || mc.gameMode == null || !self.isAlive()) {
         this.clearPlacementState();
         this.activePlayer = self;
         this.activeWorld = world;
         if (self == null || world == null || mc.gameMode == null || !self.isAlive()) {
            return;
         }
      }

      if (self.isUsingItem() || this.managedSlot != -1 && self.getInventory().getSelectedSlot() != this.managedSlot) {
         this.clearPlacementState();
      } else if (this.pendingPlaceHit != null) {
         BlockHitResult hit = this.validatePendingPlacement(self, world);
         BlockPos vis = this.pendingVisualPos;
         this.clearPendingPlacement();
         if (hit != null) {
            this.placeOnce(self, hit, vis);
            if (this.lastSlot != -1) {
               this.ticksToBack = 3;
            }
         } else {
            this.lastData = null;
            this.releaseRotation();
         }
      } else {
         boolean slotRestored = false;
         if (this.ticksToBack > 0) {
            this.ticksToBack--;
         }

         if (this.ticksToBack == 0 && this.lastSlot != -1) {
            self.getInventory().setSelectedSlot(this.lastSlot);
            this.lastSlot = -1;
            this.managedSlot = -1;
            slotRestored = true;
         }

         Player target = this.findTarget(self, world);
         if (target == null) {
            this.lastData = null;
            this.releaseRotation();
         } else {
            int obsSlot = this.findHotbar(self, Items.OBSIDIAN);
            int crysSlot = this.findHotbar(self, Items.END_CRYSTAL);
            if (target != null) {
               List<Entity> entities = world.getEntitiesOfClass(Entity.class, self.getBoundingBox().inflate(16.0), e -> e.isAlive() && !e.isSpectator());
               CrystalAuraFeature.PlaceData obsidianData = this.findBestObsidian(target, world, self, entities, obsSlot != -1 && crysSlot != -1);
               CrystalAuraFeature.PlaceData crystalData = crysSlot != -1 ? this.findBestCrystal(target, world, self, entities) : null;
               CrystalAuraFeature.PlaceData breakData = this.autoKrot.getValue() && crysSlot != -1 ? this.findBestBreak(target, world, self, entities) : null;
               CrystalAuraFeature.PlaceData finalData = obsidianData;
               if (crystalData != null && crystalData.damage() > finalData.damage()) {
                  finalData = crystalData;
               }

               if (breakData != null && breakData.damage() > finalData.damage()) {
                  finalData = breakData;
               }

               if (obsidianData.placeResult() == CrystalAuraFeature.PlaceResult.AWAITING) {
                  finalData = obsidianData;
               }

               if (this.lastData == null || this.lastData.placeResult() != CrystalAuraFeature.PlaceResult.EXPLOSION) {
                  this.lastData = finalData;
               }
            }

            if (this.lastData == null || this.lastData.placeResult() == CrystalAuraFeature.PlaceResult.AWAITING) {
               this.releaseRotation();
            } else if (this.lastData.damage() == 0.0) {
               this.lastData = null;
               this.releaseRotation();
            } else if (this.lastData.placeResult() == CrystalAuraFeature.PlaceResult.EXPLOSION) {
               BlockPos blockPos = this.lastData.blockPos();
               Direction direction = this.lastData.face();
               Entity crystal = this.lastData.entity();
               if (blockPos != null && direction != null && crystal instanceof EndCrystal) {
                  float[] rot = this.calculate(this.getFacePoint(blockPos, direction), self);
                  if (this.raycastBox(rot, blockPos, 5.5, self) != null && this.rayTrace(rot, 3.0, crystal.getBoundingBox(), self)) {
                     if (this.rotate.is("Vanilla")) {
                        RotationContext.setRotation(rot[0], rot[1]);
                     }

                     mc.gameMode.attack(self, crystal);
                     self.swing(InteractionHand.MAIN_HAND);
                     this.lastData = null;
                     this.releaseRotation();
                     return;
                  }
               }

               if (crystal instanceof EndCrystal) {
                  Vec3 nearest = MultipointUtil.getNearestPoint(crystal, 0.0, 0.05);
                  float[] rotCrystal = this.calculate(nearest, self);
                  if (this.isLookingAtEntity(rotCrystal, crystal, 3.0, self)) {
                     this.rotation = rotCrystal;
                     if (this.rotate.is("Без ротации") || this.isLookingAtEntity(crystal, 3.0, self)) {
                        mc.gameMode.attack(self, crystal);
                        self.swing(InteractionHand.MAIN_HAND);
                     }

                     if (this.rotate.is("Vanilla")) {
                        RotationContext.setRotation(rotCrystal[0], rotCrystal[1]);
                     }
                  }
               }

               this.lastData = null;
               this.releaseRotation();
            } else {
               BlockPos blockPos = this.lastData.blockPos();
               if (blockPos == null) {
                  this.lastData = null;
                  this.releaseRotation();
               } else {
                  Direction direction = this.lastData.face();
                  if (direction == null) {
                     this.lastData = null;
                     this.releaseRotation();
                  } else {
                     float[] rot = this.calculate(this.getFacePoint(blockPos, direction), self);
                     BlockHitResult aimedHit = this.raycastBox(rot, blockPos, 5.5, self);
                     if (aimedHit != null) {
                        BlockHitResult lookHit = this.raycastBoxLook(blockPos, 5.5, self);
                        if (this.rotate.is("Без ротации") || lookHit != null) {
                           if (this.lastData.placeResult() == CrystalAuraFeature.PlaceResult.BREAK) {
                              if (this.findBestTool(self, world.getBlockState(blockPos)) == -1) {
                                 this.lastData = null;
                                 this.releaseRotation();
                                 return;
                              }

                              AutoToolFeature autoTool = FeatureManager.INSTANCE.getFeature(AutoToolFeature.class);
                              if (autoTool != null) {
                                 autoTool.swapForBlock(world.getBlockState(blockPos));
                              }

                              if (!world.getBlockState(blockPos).isAir()) {
                                 if (mc.gameMode instanceof MultiPlayerGameModeAccessor acc && acc.getDestroyDelay() <= 1) {
                                    mc.gameMode.startDestroyBlock(blockPos, direction);
                                    self.swing(InteractionHand.MAIN_HAND);
                                 }

                                 if (!world.getBlockState(blockPos).isAir() && mc.gameMode.continueDestroyBlock(blockPos, direction)) {
                                    self.swing(InteractionHand.MAIN_HAND);
                                 }
                              } else {
                                 mc.gameMode.stopDestroyBlock();
                              }

                              if (!this.hasVisual(blockPos)) {
                                 this.visualDatas.add(new CrystalAuraFeature.VisualData(blockPos, System.currentTimeMillis(), 0.8F, 0.1F, 0.1F));
                              }
                           } else {
                              Item item = this.lastData.placeResult() == CrystalAuraFeature.PlaceResult.PLACE_CRYSTAL ? Items.END_CRYSTAL : Items.OBSIDIAN;
                              int slot = this.findHotbar(self, item);
                              BlockHitResult hit = this.rotate.is("Vanilla") ? lookHit : aimedHit;
                              if (slot != -1 && hit.getDirection() == direction) {
                                 BlockPos vis = item == Items.END_CRYSTAL ? blockPos.above() : blockPos.relative(direction);
                                 boolean alreadySelected = self.getInventory().getSelectedSlot() == slot;
                                 if (this.fastPlacement.getValue() && alreadySelected && !slotRestored) {
                                    this.placeOnce(self, hit, vis);
                                    if (this.lastSlot != -1) {
                                       this.ticksToBack = 3;
                                    }
                                 } else {
                                    if (!alreadySelected) {
                                       if (this.lastSlot == -1) {
                                          this.lastSlot = self.getInventory().getSelectedSlot();
                                       }

                                       this.managedSlot = slot;
                                       self.getInventory().setSelectedSlot(slot);
                                    }

                                    if (mc.gameMode instanceof MultiPlayerGameModeAccessor acc) {
                                       acc.invokerEnsureHasSentCarriedItem();
                                    }

                                    this.ticksToBack = 3;
                                    this.pendingPlaceHit = hit;
                                    this.pendingVisualPos = vis;
                                    this.pendingPlaceItem = item;
                                    this.pendingPlaceSlot = slot;
                                    this.pendingPlaceTarget = target;
                                 }

                                 this.lastData = null;
                              }
                           }
                        }

                        this.rotation = rot;
                     } else {
                        this.releaseRotation();
                        this.lastData = null;
                     }

                     if (this.rotation != null && this.rotate.is("Vanilla")) {
                        RotationContext.setRotation(this.rotation[0], this.rotation[1]);
                     }
                  }
               }
            }
         }
      }
   }

   private void releaseRotation() {
      this.rotation = null;
      RotationContext.clear();
   }

   private void clearPendingPlacement() {
      this.pendingPlaceHit = null;
      this.pendingVisualPos = null;
      this.pendingPlaceItem = null;
      this.pendingPlaceSlot = -1;
      this.pendingPlaceTarget = null;
   }

   private void clearPlacementState() {
      this.clearPendingPlacement();
      this.lastData = null;
      this.ticksToBack = 0;
      this.lastSlot = -1;
      this.managedSlot = -1;
      this.releaseRotation();
   }

   private void placeOnce(LocalPlayer self, BlockHitResult hit, BlockPos visualPos) {
      mc.gameMode.useItemOn(self, InteractionHand.MAIN_HAND, hit);
      self.swing(InteractionHand.MAIN_HAND);
      if (visualPos != null && !this.hasVisual(visualPos)) {
         this.visualDatas.add(new CrystalAuraFeature.VisualData(visualPos, System.currentTimeMillis(), 0.2F, 0.8F, 1.0F));
      }
   }

   private BlockHitResult validatePendingPlacement(LocalPlayer self, ClientLevel world) {
      ItemStack held = self.getMainHandItem();
      if (self.getInventory().getSelectedSlot() == this.pendingPlaceSlot && !held.isEmpty() && held.getItem() == this.pendingPlaceItem) {
         Player target = this.pendingPlaceTarget;
         if (target != null
            && target != self
            && world.players().contains(target)
            && target.isAlive()
            && !target.isRemoved()
            && !target.isSpectator()
            && !target.isCreative()
            && !FriendManager.INSTANCE.isFriend(target.getGameProfile().name())
            && !(self.distanceToSqr(target) >= 64.0)) {
            BlockPos base = this.pendingPlaceHit.getBlockPos();
            Direction face = this.pendingPlaceHit.getDirection();
            boolean crystal = this.pendingPlaceItem == Items.END_CRYSTAL;
            BlockPos placePos = crystal ? base.above() : base.relative(face);
            BlockState state = world.getBlockState(base);
            if (crystal ? state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.BEDROCK : state.getBlock().defaultBlockState().isSolid()) {
               if (world.getBlockState(placePos).isAir() && (crystal ? base : placePos).getY() < (int)target.getY()) {
                  BlockHitResult hit = this.rotate.is("Vanilla")
                     ? this.raycastBoxLook(base, 5.5, self)
                     : this.raycastBox(this.calculate(this.getFacePoint(base, face), self), base, 5.5, self);
                  if (hit != null && hit.getDirection() == face) {
                     if (!this.canReachBox(
                        new AABB(
                           placePos.getX() - 0.5, placePos.getY(), placePos.getZ() - 0.5, placePos.getX() + 2, placePos.getY() + 1.5, placePos.getZ() + 1.5
                        ),
                        3.0,
                        self
                     )) {
                        return null;
                     }

                     List<Entity> entities = world.getEntitiesOfClass(Entity.class, self.getBoundingBox().inflate(16.0), e -> e.isAlive() && !e.isSpectator());
                     if (crystal) {
                        Vec3 source = Vec3.atCenterOf(placePos);
                        if (this.blockedCrystal(entities, placePos)
                           || this.explosionDamage(source, target, world) + 19.0 < 31.0
                           || !this.selfDamageAllowed(this.explosionDamage(source, self, world))) {
                           return null;
                        }
                     } else {
                        BlockPos crystalPos = placePos.above();
                        Vec3 source = Vec3.atCenterOf(crystalPos);
                        if (this.blockedObsidian(entities, placePos) == CrystalAuraFeature.ReturnResult.PLAYER
                           || this.blockedObsidian(entities, crystalPos) != CrystalAuraFeature.ReturnResult.NONE
                           || this.ghostDamage(source, target, placePos, world) + 19.0 - 6.0 < 25.0
                           || !this.selfDamageAllowed(this.ghostDamage(source, self, placePos, world))) {
                           return null;
                        }
                     }

                     return hit;
                  } else {
                     return null;
                  }
               } else {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private Player findTarget(LocalPlayer self, ClientLevel world) {
      Player best = null;
      double bestDist = 64.0;

      for (Player p : world.players()) {
         if (p != self && p.isAlive() && !p.isRemoved() && !p.isSpectator() && !p.isCreative() && !FriendManager.INSTANCE.isFriend(p.getGameProfile().name())) {
            double d = self.distanceToSqr(p);
            if (!(d > 64.0) && d < bestDist) {
               bestDist = d;
               best = p;
            }
         }
      }

      return best;
   }

   private CrystalAuraFeature.PlaceData findBestObsidian(Player entity, ClientLevel world, LocalPlayer self, List<Entity> entities, boolean canPlaceObsidian) {
      BlockPos bestPanPos = null;
      double bestPanDamage = 0.0;
      Direction bestFace = null;
      BlockPos basePos = self.blockPosition();

      for (int x = -5; canPlaceObsidian && x <= 5; x++) {
         for (int y = -5; y <= 5; y++) {
            for (int z = -5; z <= 5; z++) {
               BlockPos checkPos = basePos.offset(x, y, z);
               BlockState state = world.getBlockState(checkPos);
               if (state.getBlock().defaultBlockState().isSolid()) {
                  Direction face = this.getBestVisibleFaceToObsidian(checkPos, self, world);
                  if (face != null) {
                     float[] rot = this.calculate(this.getFacePoint(checkPos, face), self);
                     BlockHitResult raycast = this.raycastBox(rot, checkPos, 5.5, self);
                     if (raycast != null && raycast.getDirection() == face) {
                        BlockPos placePos = checkPos.relative(face);
                        if (placePos.getY() < (int)entity.getY()) {
                           BlockPos crystalPos = placePos.above();
                           if (world.getBlockState(placePos).isAir()
                              && this.blockedObsidian(entities, placePos) != CrystalAuraFeature.ReturnResult.PLAYER
                              && this.blockedObsidian(entities, crystalPos) != CrystalAuraFeature.ReturnResult.PLAYER
                              && this.canReachBox(
                                 new AABB(
                                    placePos.getX() - 0.5,
                                    placePos.getY(),
                                    placePos.getZ() - 0.5,
                                    placePos.getX() + 2,
                                    placePos.getY() + 1.5,
                                    placePos.getZ() + 1.5
                                 ),
                                 3.0,
                                 self
                              )) {
                              double damage = this.ghostDamage(Vec3.atCenterOf(crystalPos), entity, placePos, world) + 19.0 - 6.0;
                              if (!(damage <= bestPanDamage) && !(damage < 25.0)) {
                                 double selfDamage = this.ghostDamage(Vec3.atCenterOf(crystalPos), self, placePos, world);
                                 if (this.selfDamageAllowed(selfDamage)) {
                                    if (this.blockedObsidian(entities, crystalPos) == CrystalAuraFeature.ReturnResult.ITEM) {
                                       return new CrystalAuraFeature.PlaceData(null, null, 0.0, CrystalAuraFeature.PlaceResult.AWAITING, null);
                                    }

                                    bestPanDamage = damage;
                                    bestPanPos = checkPos;
                                    bestFace = face;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      BlockPos bestCrystalPos = null;
      Direction bestCrystalFace = null;
      EndCrystal bestCrystal = null;
      double bestCrystalDamage = 0.0;

      for (Entity entity1 : entities) {
         if (entity1 instanceof EndCrystal crystal) {
            double damage = this.explosionDamage(crystal.position().add(0.0, 0.5, 0.0), entity, world) + 30.0;
            if (!(damage <= bestCrystalDamage)) {
               double selfDamage = this.explosionDamage(crystal.position().add(0.0, 0.5, 0.0), self, world);
               if (this.selfDamageAllowed(selfDamage)) {
                  BlockPos pos = crystal.blockPosition().below();
                  Direction face = Direction.UP;
                  Vec3 rotatePoint = this.getFacePoint(pos, face);
                  float[] rotBlock = this.calculate(rotatePoint, self);
                  BlockHitResult raycast = this.raycastBox(rotBlock, pos, 5.5, self);
                  if (raycast != null && raycast.getDirection() == face && this.rayTrace(rotBlock, 3.0, crystal.getBoundingBox(), self)) {
                     bestCrystalPos = pos;
                     bestCrystalFace = face;
                  }

                  float[] rot = this.calculate(MultipointUtil.getNearestPoint(crystal, 0.0, 0.05), self);
                  if (this.isLookingAtEntity(rot, crystal, 3.0, self) || bestCrystalPos != null) {
                     bestCrystalDamage = damage;
                     bestCrystal = crystal;
                  }
               }
            }
         }
      }

      return bestCrystalDamage > bestPanDamage
         ? new CrystalAuraFeature.PlaceData(bestCrystalPos, bestCrystalFace, bestCrystalDamage, CrystalAuraFeature.PlaceResult.EXPLOSION, bestCrystal)
         : new CrystalAuraFeature.PlaceData(bestPanPos, bestFace, bestPanDamage, CrystalAuraFeature.PlaceResult.PLACE_OBSIDIAN, null);
   }

   private CrystalAuraFeature.PlaceData findBestCrystal(Player entity, ClientLevel world, LocalPlayer self, List<Entity> entities) {
      BlockPos bestPanPos = null;
      double bestPanDamage = 0.0;
      Direction bestFace = null;
      BlockPos basePos = self.blockPosition();

      for (int x = -5; x <= 5; x++) {
         for (int y = -5; y <= 5; y++) {
            for (int z = -5; z <= 5; z++) {
               BlockPos checkPos = basePos.offset(x, y, z);
               if (checkPos.getY() < (int)entity.getY()) {
                  BlockState state = world.getBlockState(checkPos);
                  if (state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.BEDROCK) {
                     Direction face = this.getBestVisibleFace(checkPos, self);
                     if (face != null) {
                        BlockPos placePos = checkPos.above();
                        if (world.getBlockState(placePos).isAir()) {
                           float[] rot = this.calculate(this.getFacePoint(checkPos, face), self);
                           BlockHitResult raycast = this.raycastBox(rot, checkPos, 5.5, self);
                           if (raycast != null && raycast.getDirection() == face) {
                              float[] rot2 = this.calculate(this.getFacePoint(checkPos, Direction.UP), self);
                              BlockHitResult raycast2 = this.raycastBox(rot2, checkPos, 5.5, self);
                              if (raycast2 != null && raycast2.getDirection() == Direction.UP) {
                                 face = Direction.UP;
                              }

                              if (!this.blockedCrystal(entities, placePos)
                                 && this.canReachBox(
                                    new AABB(
                                       placePos.getX() - 0.5,
                                       placePos.getY(),
                                       placePos.getZ() - 0.5,
                                       placePos.getX() + 2,
                                       placePos.getY() + 1.5,
                                       placePos.getZ() + 1.5
                                    ),
                                    3.0,
                                    self
                                 )) {
                                 double damage = this.explosionDamage(Vec3.atCenterOf(placePos), entity, world) + 19.0;
                                 if (!(damage <= bestPanDamage) && !(damage < 31.0)) {
                                    double selfDamage = this.explosionDamage(Vec3.atCenterOf(placePos), self, world);
                                    if (this.selfDamageAllowed(selfDamage)) {
                                       bestPanDamage = damage;
                                       bestPanPos = checkPos;
                                       bestFace = face;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return new CrystalAuraFeature.PlaceData(bestPanPos, bestFace, bestPanDamage, CrystalAuraFeature.PlaceResult.PLACE_CRYSTAL, null);
   }

   private CrystalAuraFeature.PlaceData findBestBreak(Player entity, ClientLevel world, LocalPlayer self, List<Entity> entities) {
      BlockPos bestPanPos = null;
      double bestPanDamage = 0.0;
      Direction bestFace = null;
      BlockPos bestPanPosBreak = null;
      double bestPanDamageBreak = 0.0;
      Direction bestFaceBreak = null;
      BlockPos basePos = self.blockPosition();

      for (int x = -5; x <= 5; x++) {
         for (int y = -5; y <= 5; y++) {
            for (int z = -5; z <= 5; z++) {
               BlockPos checkPos = basePos.offset(x, y, z);
               if (checkPos.getY() < (int)entity.getY()) {
                  BlockState cs = world.getBlockState(checkPos);
                  if (cs.getBlock().defaultBlockState().isSolid()) {
                     Direction face = this.getBestVisibleFace(checkPos, self);
                     if (face != null) {
                        float[] rot = this.calculate(this.getFacePoint(checkPos, face), self);
                        BlockHitResult raycast = this.raycastBox(rot, checkPos, 5.5, self);
                        if (raycast != null && raycast.getDirection() == face) {
                           BlockPos placePos = checkPos.above();
                           if (!this.blockedBreak(entities, placePos)
                              && this.canReachBox(
                                 new AABB(
                                    placePos.getX() - 0.5,
                                    placePos.getY(),
                                    placePos.getZ() - 0.5,
                                    placePos.getX() + 2,
                                    placePos.getY() + 1.5,
                                    placePos.getZ() + 1.5
                                 ),
                                 3.0,
                                 self
                              )) {
                              if (!world.getBlockState(placePos).isAir()) {
                                 int bestSlot = -1;
                                 int bestSlot2 = -1;
                                 BlockState state = world.getBlockState(placePos);
                                 float bestSpeed = 1.0F;

                                 for (int slot = 0; slot < 36; slot++) {
                                    ItemStack stack = self.getInventory().getItem(slot);
                                    if (!stack.isEmpty()) {
                                       float speed = stack.getDestroySpeed(state);
                                       if (speed > bestSpeed) {
                                          bestSpeed = speed;
                                          bestSlot = slot;
                                       }
                                    }
                                 }

                                 state = world.getBlockState(checkPos);
                                 bestSpeed = 1.0F;

                                 for (int slot = 0; slot < 36; slot++) {
                                    ItemStack stack = self.getInventory().getItem(slot);
                                    if (!stack.isEmpty()) {
                                       float speed = stack.getDestroySpeed(state);
                                       if (speed > bestSpeed) {
                                          bestSpeed = speed;
                                          bestSlot2 = slot;
                                       }
                                    }
                                 }

                                 if (bestSlot != -1 && (world.getBlockState(checkPos).getBlock() == Blocks.OBSIDIAN || bestSlot2 != -1)) {
                                    double damage = this.ignoreDamage(Vec3.atCenterOf(placePos), entity, placePos, world) + 19.0 - 12.0;
                                    if (!(damage <= bestPanDamageBreak) && !(damage < 19.0)) {
                                       double selfDamage = this.ignoreDamage(Vec3.atCenterOf(placePos), self, placePos, world);
                                       if (this.selfDamageAllowed(selfDamage)) {
                                          bestPanDamageBreak = damage;
                                          bestPanPosBreak = placePos;
                                          bestFaceBreak = face;
                                       }
                                    }
                                 }
                              } else {
                                 BlockState state = world.getBlockState(checkPos);
                                 int bestSlot = -1;
                                 float bestSpeed = 1.0F;

                                 for (int slot = 0; slot < 36; slot++) {
                                    ItemStack stack = self.getInventory().getItem(slot);
                                    if (!stack.isEmpty()) {
                                       float speed = stack.getDestroySpeed(state);
                                       if (speed > bestSpeed) {
                                          bestSpeed = speed;
                                          bestSlot = slot;
                                       }
                                    }
                                 }

                                 if (bestSlot != -1) {
                                    double damage = this.explosionDamage(Vec3.atCenterOf(placePos), entity, world) + 19.0 - 12.0;
                                    if (!(damage <= bestPanDamage) && !(damage < 19.0)) {
                                       double selfDamage = this.explosionDamage(Vec3.atCenterOf(placePos), self, world);
                                       if (this.selfDamageAllowed(selfDamage)) {
                                          bestPanDamage = damage;
                                          bestPanPos = checkPos;
                                          bestFace = face;
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return bestPanDamageBreak > bestPanDamage
         ? new CrystalAuraFeature.PlaceData(bestPanPosBreak, bestFaceBreak, bestPanDamageBreak, CrystalAuraFeature.PlaceResult.BREAK, null)
         : new CrystalAuraFeature.PlaceData(bestPanPos, bestFace, bestPanDamage, CrystalAuraFeature.PlaceResult.BREAK, null);
   }

   private boolean selfDamageAllowed(double selfDamage) {
      return this.safeYourSelf.getValue() ? selfDamage <= 0.5 : selfDamage <= 30.0;
   }

   private CrystalAuraFeature.ReturnResult blockedObsidian(List<Entity> entities, BlockPos crystalPos) {
      for (Entity entity : entities) {
         AABB blockBox = new AABB(
            crystalPos.getX(), crystalPos.getY() - 0.2F, crystalPos.getZ(), crystalPos.getX() + 1.0, crystalPos.getY() + 2.2, crystalPos.getZ() + 1.0
         );
         if (entity instanceof ItemEntity && !entity.onGround() && entity.getBoundingBox().intersects(blockBox)) {
            return CrystalAuraFeature.ReturnResult.ITEM;
         }

         if ((entity instanceof Player || entity instanceof EndCrystal) && this.intersectsBox(crystalPos, entity)) {
            return CrystalAuraFeature.ReturnResult.PLAYER;
         }
      }

      return CrystalAuraFeature.ReturnResult.NONE;
   }

   private boolean blockedCrystal(List<Entity> entities, BlockPos placePos) {
      for (Entity entity : entities) {
         if (this.intersectsBox(placePos, entity)) {
            return true;
         }
      }

      return false;
   }

   private boolean blockedBreak(List<Entity> entities, BlockPos placePos) {
      for (Entity entity : entities) {
         if (this.intersectsBox(placePos, entity) && !(entity instanceof ItemEntity)) {
            return true;
         }
      }

      return false;
   }

   private boolean intersectsBox(BlockPos pos, Entity entity) {
      AABB crystalBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
      return crystalBox.intersects(entity.getBoundingBox());
   }

   private boolean hasVisual(BlockPos blockPos) {
      for (CrystalAuraFeature.VisualData visualData : this.visualDatas) {
         if (visualData.blockPos.equals(blockPos)) {
            return true;
         }
      }

      return false;
   }

   private float[] calculate(Vec3 target, LocalPlayer self) {
      Vec3 eye = self.getEyePosition();
      Vec3 delta = target.subtract(eye);
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      return new float[]{yaw, pitch};
   }

   private Vec3 getFacePoint(BlockPos pos, Direction face) {
      return new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5001, pos.getY() + 0.5 + face.getStepY() * 0.5001, pos.getZ() + 0.5 + face.getStepZ() * 0.5001);
   }

   private Vec3 lookVec(LocalPlayer self) {
      if (this.rotate.is("Vanilla") && RotationContext.isActive()) {
         return Vec3.directionFromRotation(RotationContext.getServerPitch(), RotationContext.getServerYaw());
      }

      Camera cam = mc.gameRenderer.mainCamera();
      return Vec3.directionFromRotation(cam.xRot(), cam.yRot());
   }

   private BlockHitResult raycastBox(float[] rot, BlockPos targetPos, double maxDistance, LocalPlayer self) {
      Vec3 eye = self.getEyePosition();
      Vec3 dir = Vec3.directionFromRotation(rot[1], rot[0]);
      Vec3 end = eye.add(dir.scale(maxDistance));
      AABB blockBox = new AABB(targetPos);
      Optional<Vec3> hitOpt = blockBox.clip(eye, end);
      if (hitOpt.isEmpty()) {
         return null;
      }

      Vec3 hitVec = hitOpt.get();
      Direction side = this.getHitSide(hitVec, targetPos);
      return new BlockHitResult(hitVec, side, targetPos, false);
   }

   private BlockHitResult raycastBoxLook(BlockPos targetPos, double maxDistance, LocalPlayer self) {
      Vec3 eye = self.getEyePosition();
      Vec3 dir = this.lookVec(self);
      Vec3 end = eye.add(dir.scale(maxDistance));
      AABB blockBox = new AABB(targetPos);
      Optional<Vec3> hitOpt = blockBox.clip(eye, end);
      if (hitOpt.isEmpty()) {
         return null;
      }

      Vec3 hitVec = hitOpt.get();
      Direction side = this.getHitSide(hitVec, targetPos);
      return new BlockHitResult(hitVec, side, targetPos, false);
   }

   private Direction getHitSide(Vec3 hitVec, BlockPos pos) {
      double dx = hitVec.x - (pos.getX() + 0.5);
      double dy = hitVec.y - (pos.getY() + 0.5);
      double dz = hitVec.z - (pos.getZ() + 0.5);
      double absDx = Math.abs(dx);
      double absDy = Math.abs(dy);
      double absDz = Math.abs(dz);
      if (absDx > absDy && absDx > absDz) {
         return dx > 0.0 ? Direction.EAST : Direction.WEST;
      } else if (absDy > absDz) {
         return dy > 0.0 ? Direction.UP : Direction.DOWN;
      } else {
         return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private Direction getBestVisibleFace(BlockPos pos, LocalPlayer self) {
      Vec3 eye = self.getEyePosition();
      Direction best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction face : Direction.values()) {
         float[] rot = this.calculate(this.getFacePoint(pos, face), self);
         if (this.raycastBox(rot, pos, 4.5, self) != null) {
            double dist = eye.distanceToSqr(this.getFacePoint(pos, face));
            if (dist < bestDist) {
               bestDist = dist;
               best = face;
            }
         }
      }

      return best;
   }

   private Direction getBestVisibleFaceToObsidian(BlockPos pos, LocalPlayer self, ClientLevel world) {
      Direction best = null;
      double bestDist = 0.0;

      for (Direction face : Direction.values()) {
         BlockPos sidePos = pos.relative(face);
         if (world.getBlockState(sidePos).isAir()) {
            float[] rot = this.calculate(this.getFacePoint(pos, face), self);
            if (this.raycastBox(rot, pos, 4.5, self) != null) {
               double damage = this.ghostDamage(Vec3.atCenterOf(sidePos.above()), self, sidePos, world);
               if (damage > bestDist) {
                  bestDist = damage;
                  best = face;
               }
            }
         }
      }

      return best;
   }

   private boolean rayTrace(float[] rot, double reach, AABB box, LocalPlayer self) {
      Vec3 eye = self.getEyePosition();
      Vec3 dir = Vec3.directionFromRotation(rot[1], rot[0]);
      Vec3 end = eye.add(dir.scale(reach));
      return box.contains(eye) || box.clip(eye, end).isPresent();
   }

   private boolean isLookingAtEntity(float[] rot, Entity entity, double reach, LocalPlayer self) {
      return this.rayTrace(rot, reach, entity.getBoundingBox(), self);
   }

   private boolean isLookingAtEntity(Entity entity, double reach, LocalPlayer self) {
      Vec3 eye = self.getEyePosition();
      Vec3 dir = this.lookVec(self);
      Vec3 end = eye.add(dir.scale(reach));
      AABB box = entity.getBoundingBox();
      return box.contains(eye) || box.clip(eye, end).isPresent();
   }

   private boolean canReachBox(AABB box, double reach, LocalPlayer self) {
      return box.distanceToSqr(self.getEyePosition()) <= reach * reach;
   }

   private Direction getFaceDirection(float[] rot, LocalPlayer self, BlockPos targetPos) {
      Vec3 eyePos = self.getEyePosition();
      Vec3 lookVec = Vec3.directionFromRotation(rot[1], rot[0]);
      double cx = targetPos.getX() + 0.5;
      double cy = targetPos.getY() + 0.5;
      double cz = targetPos.getZ() + 0.5;
      double minX = targetPos.getX();
      double minY = targetPos.getY();
      double minZ = targetPos.getZ();
      double maxX = targetPos.getX() + 1.0;
      double maxY = targetPos.getY() + 1.0;
      double maxZ = targetPos.getZ() + 1.0;
      Direction closestFace = null;
      double closestT = Double.MAX_VALUE;
      double t = tryHitPlaneY(eyePos, lookVec, maxY, minX, maxX, minZ, maxZ);
      if (t > 0.0 && t < closestT) {
         closestT = t;
         closestFace = Direction.UP;
      }

      t = tryHitPlaneY(eyePos, lookVec, minY, minX, maxX, minZ, maxZ);
      if (t > 0.0 && t < closestT) {
         closestT = t;
         closestFace = Direction.DOWN;
      }

      t = tryHitPlaneZ(eyePos, lookVec, minZ, minX, maxX, minY, maxY);
      if (t > 0.0 && t < closestT) {
         closestT = t;
         closestFace = Direction.NORTH;
      }

      t = tryHitPlaneZ(eyePos, lookVec, maxZ, minX, maxX, minY, maxY);
      if (t > 0.0 && t < closestT) {
         closestT = t;
         closestFace = Direction.SOUTH;
      }

      t = tryHitPlaneX(eyePos, lookVec, minX, minY, maxY, minZ, maxZ);
      if (t > 0.0 && t < closestT) {
         closestT = t;
         closestFace = Direction.WEST;
      }

      t = tryHitPlaneX(eyePos, lookVec, maxX, minY, maxY, minZ, maxZ);
      if (t > 0.0 && t < closestT) {
         closestFace = Direction.EAST;
      }

      if (closestFace == null) {
         closestFace = fallbackDirection(eyePos, cx, cy, cz);
      }

      return closestFace;
   }

   private static double tryHitPlaneY(Vec3 eye, Vec3 dir, double yVal, double minX, double maxX, double minZ, double maxZ) {
      if (dir.y == 0.0) {
         return -1.0;
      }

      double t = (yVal - eye.y) / dir.y;
      if (t <= 0.0) {
         return -1.0;
      }

      double hitX = eye.x + t * dir.x;
      double hitZ = eye.z + t * dir.z;
      return hitX >= minX && hitX <= maxX && hitZ >= minZ && hitZ <= maxZ ? t : -1.0;
   }

   private static double tryHitPlaneZ(Vec3 eye, Vec3 dir, double zVal, double minX, double maxX, double minY, double maxY) {
      if (dir.z == 0.0) {
         return -1.0;
      }

      double t = (zVal - eye.z) / dir.z;
      if (t <= 0.0) {
         return -1.0;
      }

      double hitX = eye.x + t * dir.x;
      double hitY = eye.y + t * dir.y;
      return hitX >= minX && hitX <= maxX && hitY >= minY && hitY <= maxY ? t : -1.0;
   }

   private static double tryHitPlaneX(Vec3 eye, Vec3 dir, double xVal, double minY, double maxY, double minZ, double maxZ) {
      if (dir.x == 0.0) {
         return -1.0;
      }

      double t = (xVal - eye.x) / dir.x;
      if (t <= 0.0) {
         return -1.0;
      }

      double hitY = eye.y + t * dir.y;
      double hitZ = eye.z + t * dir.z;
      return hitY >= minY && hitY <= maxY && hitZ >= minZ && hitZ <= maxZ ? t : -1.0;
   }

   private static Direction fallbackDirection(Vec3 eye, double cx, double cy, double cz) {
      double dx = eye.x - cx;
      double dy = eye.y - cy;
      double dz = eye.z - cz;
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      if (ay >= ax && ay >= az) {
         return dy > 0.0 ? Direction.UP : Direction.DOWN;
      } else if (ax >= az) {
         return dx > 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private int findHotbar(LocalPlayer self, Item item) {
      int selected = self.getInventory().getSelectedSlot();
      ItemStack held = self.getInventory().getItem(selected);
      if (!held.isEmpty() && held.getItem() == item) {
         return selected;
      }

      for (int i = 0; i < 9; i++) {
         ItemStack stack = self.getInventory().getItem(i);
         if (!stack.isEmpty() && stack.getItem() == item) {
            return i;
         }
      }

      return -1;
   }

   private int findBestTool(LocalPlayer self, BlockState state) {
      int bestSlot = -1;
      float bestSpeed = 1.0F;

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = self.getInventory().getItem(slot);
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

   private double explosionDamage(Vec3 source, LivingEntity target, ClientLevel world) {
      if (world.getDifficulty() != Difficulty.PEACEFUL && target != null) {
         if (!new AABB(
               Mth.floor(source.x - 11.0),
               Mth.floor(source.y - 11.0),
               Mth.floor(source.z - 11.0),
               Mth.floor(source.x + 13.0),
               Mth.floor(source.y + 13.0),
               Mth.floor(source.z + 13.0)
            )
            .intersects(target.getBoundingBox())) {
            return 0.0;
         }

         if (target.isInvulnerable()) {
            return 0.0;
         }

         double distExposure = target.position().distanceToSqr(source) / 144.0;
         if (distExposure > 1.0) {
            return 0.0;
         }

         double finalExposure = (1.0 - distExposure) * this.exposure(source, target.getBoundingBox(), world);
         float toDamage = (float)Math.floor((finalExposure * finalExposure + finalExposure) / 2.0 * 7.0 * 12.0 + 1.0);
         return this.reductions(toDamage, target, world);
      } else {
         return 0.0;
      }
   }

   private double ghostDamage(Vec3 source, LivingEntity target, BlockPos ghost, ClientLevel world) {
      if (world.getDifficulty() == Difficulty.PEACEFUL) {
         return 0.0;
      }

      if (!new AABB(
            Mth.floor(source.x - 12.0 - 1.0),
            Mth.floor(source.y - 12.0 - 1.0),
            Mth.floor(source.z - 12.0 - 1.0),
            Mth.floor(source.x + 12.0 + 1.0),
            Mth.floor(source.y + 12.0 + 1.0),
            Mth.floor(source.z + 12.0 + 1.0)
         )
         .intersects(target.getBoundingBox())) {
         return 0.0;
      }

      if (target.isInvulnerable()) {
         return 0.0;
      }

      double distExposure = target.position().distanceToSqr(source) / 144.0;
      if (distExposure > 1.0) {
         return 0.0;
      }

      double finalExposure = (1.0 - distExposure) * this.exposureOverride(source, target.getBoundingBox(), world, ghost, null);
      float toDamage = (float)Math.floor((finalExposure * finalExposure + finalExposure) / 2.0 * 7.0 * 12.0 + 1.0);
      return this.reductions(toDamage, target, world);
   }

   private double ignoreDamage(Vec3 source, LivingEntity target, BlockPos ignore, ClientLevel world) {
      if (world.getDifficulty() != Difficulty.PEACEFUL && target != null && ignore != null) {
         if (!new AABB(
               Mth.floor(source.x - 12.0 - 1.0),
               Mth.floor(source.y - 12.0 - 1.0),
               Mth.floor(source.z - 12.0 - 1.0),
               Mth.floor(source.x + 12.0 + 1.0),
               Mth.floor(source.y + 12.0 + 1.0),
               Mth.floor(source.z + 12.0 + 1.0)
            )
            .intersects(target.getBoundingBox())) {
            return 0.0;
         }

         if (target.isInvulnerable()) {
            return 0.0;
         }

         double distExposure = target.position().distanceToSqr(source) / 144.0;
         if (distExposure > 1.0) {
            return 0.0;
         }

         double finalExposure = (1.0 - distExposure) * this.exposureOverride(source, target.getBoundingBox(), world, null, ignore);
         float toDamage = (float)Math.floor((finalExposure * finalExposure + finalExposure) / 2.0 * 7.0 * 12.0 + 1.0);
         return this.reductions(toDamage, target, world);
      } else {
         return 0.0;
      }
   }

   private float reductions(float toDamage, LivingEntity target, ClientLevel world) {
      if (world.getDifficulty() == Difficulty.EASY) {
         toDamage = Math.min(toDamage / 2.0F + 1.0F, toDamage);
      } else if (world.getDifficulty() == Difficulty.HARD) {
         toDamage = toDamage * 3.0F / 2.0F;
      }

      float armor = (float)Math.floor(target.getArmorValue());
      float toughness = 0.0F;
      AttributeInstance attr = target.getAttribute(Attributes.ARMOR_TOUGHNESS);
      if (attr != null) {
         toughness = (float)attr.getValue();
      }

      toDamage = getDamageAfterArmor(toDamage, armor, toughness);
      MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
      if (resistance != null) {
         float r = 25.0F - (resistance.getAmplifier() + 1) * 5.0F;
         toDamage = Math.max(toDamage * r / 25.0F, 0.0F);
      }

      if (toDamage <= 0.0F) {
         return 0.0F;
      }

      int protAmount = this.getProtectionTotal(target, world);
      if (protAmount > 0) {
         toDamage *= 1.0F - Mth.clamp(protAmount, 0.0F, 20.0F) / 25.0F;
      }

      return toDamage;
   }

   private static float getDamageAfterArmor(float damage, float armor, float toughness) {
      float i = 2.0F + toughness / 4.0F;
      float j = Mth.clamp(armor - damage / i, armor * 0.2F, 20.0F);
      return damage * (1.0F - j / 25.0F);
   }

   private int getProtectionTotal(LivingEntity target, ClientLevel world) {
      int amount = 0;
      amount += this.protectionFor(target.getItemBySlot(EquipmentSlot.HEAD), world);
      amount += this.protectionFor(target.getItemBySlot(EquipmentSlot.CHEST), world);
      amount += this.protectionFor(target.getItemBySlot(EquipmentSlot.LEGS), world);
      return amount + this.protectionFor(target.getItemBySlot(EquipmentSlot.FEET), world);
   }

   private int protectionFor(ItemStack stack, ClientLevel world) {
      return this.enchantLevel(Enchantments.BLAST_PROTECTION, stack, world) * 2 + this.enchantLevel(Enchantments.PROTECTION, stack, world);
   }

   private int enchantLevel(ResourceKey<Enchantment> key, ItemStack stack, ClientLevel world) {
      if (stack != null && !stack.isEmpty()) {
         try {
            return EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), stack);
         } catch (Throwable ignored) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private double exposure(Vec3 source, AABB box, ClientLevel world) {
      double d = 0.4545454446934474;
      double e = 0.21739130885479366;
      double f = 0.4545454446934474;
      int i = 0;
      int j = 0;

      for (double k = 0.0; k <= 1.0; k += d) {
         for (double l = 0.0; l <= 1.0; l += e) {
            for (double m = 0.0; m <= 1.0; m += f) {
               double n = Mth.lerp(k, box.minX, box.maxX);
               double o = Mth.lerp(l, box.minY, box.maxY);
               double p = Mth.lerp(m, box.minZ, box.maxZ);
               if (this.isVisible(new Vec3(n + 0.045454555306552624, o, p + 0.045454555306552624), source, world, null, null)) {
                  i++;
               }

               j++;
            }
         }
      }

      return (double)i / j;
   }

   private double exposureOverride(Vec3 source, AABB box, ClientLevel world, BlockPos ghost, BlockPos ignore) {
      double d = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double e = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double f = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      if (!(d < 0.0) && !(e < 0.0) && !(f < 0.0)) {
         double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
         double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;
         int i = 0;
         int j = 0;

         for (double k = 0.0; k <= 1.0; k += d) {
            for (double l = 0.0; l <= 1.0; l += e) {
               for (double m = 0.0; m <= 1.0; m += f) {
                  double n = Mth.lerp(k, box.minX, box.maxX);
                  double o = Mth.lerp(l, box.minY, box.maxY);
                  double p = Mth.lerp(m, box.minZ, box.maxZ);
                  if (this.isVisible(new Vec3(n + g, o, p + h), source, world, ghost, ignore)) {
                     i++;
                  }

                  j++;
               }
            }
         }

         return (double)i / j;
      } else {
         return 0.0;
      }
   }

   private boolean isVisible(Vec3 start, Vec3 end, ClientLevel world, BlockPos ghost, BlockPos ignore) {
      return this.raycastShapes(start, end, world, ghost, ignore) == null;
   }

   private BlockHitResult raycastShapes(Vec3 start, Vec3 end, ClientLevel world, BlockPos ghost, BlockPos ignore) {
      return (BlockHitResult)BlockGetter.traverseBlocks(start, end, world, (ctx, pos) -> {
         BlockState state;
         if (ghost != null && pos.equals(ghost)) {
            state = Blocks.OBSIDIAN.defaultBlockState();
         } else if (ignore != null && pos.equals(ignore)) {
            state = Blocks.AIR.defaultBlockState();
         } else {
            state = world.getBlockState(pos);
         }

         return state.getCollisionShape(world, pos).clip(start, end, pos);
      }, ctx -> null);
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (!this.visualDatas.isEmpty() && mc.level != null) {
         long now = System.currentTimeMillis();
         this.visualDatas.removeIf(vx -> now - vx.createdAt > 350L);
         List<WorldMeshRenderer.Line> lines = new ArrayList<>();
         List<WorldMeshRenderer.Ring> rings = new ArrayList<>();
         List<WorldMeshRenderer.PlaneRect> rects = new ArrayList<>();

         for (CrystalAuraFeature.VisualData v : this.visualDatas) {
            float age = (float)(now - v.createdAt) / 300.0F;
            if (!(age >= 1.0F)) {
               float alpha = 1.0F - age;
               int color = toArgb(v.r, v.g, v.b, alpha);
               int edge = toArgb(v.r, v.g, v.b, Math.min(1.0F, alpha + 0.3F));
               box(lines, rects, new AABB(v.blockPos), color, edge);
            }
         }

         if (!lines.isEmpty() || !rects.isEmpty()) {
            WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, rings, rects), true);
         }
      }
   }

   private static int toArgb(float r, float g, float b, float a) {
      int ai = (int)(Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
      int ri = (int)(Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
      int gi = (int)(Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
      int bi = (int)(Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
      return ai << 24 | ri << 16 | gi << 8 | bi;
   }

   private static void box(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.PlaneRect> rects, AABB box, int fill, int edge) {
      Vec3 c = box.getCenter();
      double hx = (box.maxX - box.minX) / 2.0;
      double hy = (box.maxY - box.minY) / 2.0;
      double hz = (box.maxZ - box.minZ) / 2.0;
      rects.add(new WorldMeshRenderer.PlaneRect(new Vec3(box.minX, c.y, c.z), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0), hy, hz, fill));
      rects.add(new WorldMeshRenderer.PlaneRect(new Vec3(box.maxX, c.y, c.z), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0), hy, hz, fill));
      rects.add(new WorldMeshRenderer.PlaneRect(new Vec3(c.x, box.minY, c.z), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), hx, hz, fill));
      rects.add(new WorldMeshRenderer.PlaneRect(new Vec3(c.x, box.maxY, c.z), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), hx, hz, fill));
      rects.add(new WorldMeshRenderer.PlaneRect(new Vec3(c.x, c.y, box.minZ), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), hx, hy, fill));
      rects.add(new WorldMeshRenderer.PlaneRect(new Vec3(c.x, c.y, box.maxZ), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), hx, hy, fill));
      Vec3 p000 = new Vec3(box.minX, box.minY, box.minZ);
      Vec3 p001 = new Vec3(box.minX, box.minY, box.maxZ);
      Vec3 p010 = new Vec3(box.minX, box.maxY, box.minZ);
      Vec3 p011 = new Vec3(box.minX, box.maxY, box.maxZ);
      Vec3 p100 = new Vec3(box.maxX, box.minY, box.minZ);
      Vec3 p101 = new Vec3(box.maxX, box.minY, box.maxZ);
      Vec3 p110 = new Vec3(box.maxX, box.maxY, box.minZ);
      Vec3 p111 = new Vec3(box.maxX, box.maxY, box.maxZ);
      lines.add(new WorldMeshRenderer.Line(p000, p001, edge));
      lines.add(new WorldMeshRenderer.Line(p000, p010, edge));
      lines.add(new WorldMeshRenderer.Line(p000, p100, edge));
      lines.add(new WorldMeshRenderer.Line(p001, p011, edge));
      lines.add(new WorldMeshRenderer.Line(p001, p101, edge));
      lines.add(new WorldMeshRenderer.Line(p010, p011, edge));
      lines.add(new WorldMeshRenderer.Line(p010, p110, edge));
      lines.add(new WorldMeshRenderer.Line(p100, p101, edge));
      lines.add(new WorldMeshRenderer.Line(p100, p110, edge));
      lines.add(new WorldMeshRenderer.Line(p101, p111, edge));
      lines.add(new WorldMeshRenderer.Line(p011, p111, edge));
      lines.add(new WorldMeshRenderer.Line(p110, p111, edge));
   }

   @Generated
   public BooleanSetting getAutoKrot() {
      return this.autoKrot;
   }

   @Generated
   public CrystalAuraFeature.PlaceData getLastData() {
      return this.lastData;
   }

   @Generated
   public float[] getRotation() {
      return this.rotation;
   }

   public record PlaceData(BlockPos blockPos, Direction face, double damage, CrystalAuraFeature.PlaceResult placeResult, Entity entity) {
   }

   public enum PlaceResult {
      PLACE_OBSIDIAN,
      PLACE_CRYSTAL,
      EXPLOSION,
      BREAK,
      AWAITING;
   }

   public enum ReturnResult {
      ITEM,
      PLAYER,
      NONE;
   }

   public static class VisualData {
      private final BlockPos blockPos;
      private final long createdAt;
      private float r;
      private float g;
      private float b;

      @Generated
      public BlockPos getBlockPos() {
         return this.blockPos;
      }

      @Generated
      public long getCreatedAt() {
         return this.createdAt;
      }

      @Generated
      public float getR() {
         return this.r;
      }

      @Generated
      public float getG() {
         return this.g;
      }

      @Generated
      public float getB() {
         return this.b;
      }

      @Generated
      public VisualData(BlockPos blockPos, long createdAt, float r, float g, float b) {
         this.blockPos = blockPos;
         this.createdAt = createdAt;
         this.r = r;
         this.g = g;
         this.b = b;
      }
   }
}
