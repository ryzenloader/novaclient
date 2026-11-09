package win.furryhook.feature.impl.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.render.Render2DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.ScaleUtil;
import win.furryhook.utils.render.gui.TextAlign;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;
import win.furryhook.utils.render.world.WorldMeshRenderer;

public final class TrajectoriesFeature extends Feature implements MinecraftContext {
   private static final String TARGET_SELF = "Self";
   private static final String TARGET_PLAYERS = "Players";
   private final MultiSelectSetting predictTrajectory = this.register(new MultiSelectSetting("Predict Trajectory", Set.of("Self"), "Self", "Players"));
   private final BooleanSetting showOwner = this.register(new BooleanSetting("Show Owner", true));
   private final List<TrajectoriesFeature.ScreenLine> pendingScreenLines = new ArrayList<>();
   private final List<TrajectoriesFeature.ImpactPoint> points = new ArrayList<>();
   private int tpSkipTicks;
   private static final int MAX_SIMULATION_TICKS = 300;
   private static final float DESIGN_SCALE = 1.6F;
   private static final float SCREEN_CONNECTOR_THICKNESS = 1.35F;
   private static final float TAG_WIDTH = 36.0F;
   private static final float TAG_CARD = 36.0F;
   private static final float TAG_HEIGHT = 48.0F;
   private static final float TAG_GAP = 4.0F;
   private static final float TAG_RADIUS = 10.0F;
   private static final float TAG_BORDER = 1.2F;
   private static final float TAG_PADDING = 6.0F;
   private static final float TAG_ICON = 20.0F;
   private static final float TAG_TEXT_SIZE = 8.0F;
   private static final float TAG_TEXT_ROW = 8.0F;
   private static final int IMPACT_RING_SEGMENTS = 180;
   private static final double IMPACT_RING_HALF_WIDTH = 0.004;
   private static final double IMPACT_CROSS_RADIUS_FACTOR = 0.72;
   private static final double IMPACT_CROSS_HALF_WIDTH = 0.003;
   private static final int POTION_AREA_RING_SEGMENTS = 220;
   private static final double POTION_AREA_RING_HALF_WIDTH = 0.018;
   private static final double POTION_AREA_Y_OFFSET = 0.012;
   private static final double POTION_GROUND_SEARCH_UP = 0.35;
   private static final double POTION_GROUND_SEARCH_DOWN = 2.5;
   private static final double SPLASH_POTION_RADIUS = 4.0;
   private static final double LINGERING_POTION_RADIUS = 3.0;
   private static final double SPLASH_ENTITY_VERTICAL_RANGE = 2.0;
   private static final double SPLASH_MIN_DISTANCE = 0.001;
   private static final double SPLASH_LINE_MIN_ALPHA = 0.38;
   private static final double SPLASH_LINE_MAX_ALPHA = 0.92;
   private static final float POTION_AREA_ALPHA = 0.55F;
   private static final float POTION_AREA_CONNECTOR_ALPHA = 0.67F;
   private static final double ENTITY_HIT_BOX_EXPAND = 0.028;
   private static final float ENTITY_HIT_BOX_ALPHA = 0.94F;
   private static final float ENTITY_HIT_BOX_FILL_ALPHA = 0.09F;
   private static final int SPLASH_LINE_START_COLOR = -12386427;
   private static final int SPLASH_LINE_END_COLOR = -42920;
   private static final double AIR_INERTIA = 0.99;
   private static final double THROWABLE_WATER_INERTIA = 0.8;
   private static final double ARROW_WATER_INERTIA = 0.6;
   private static final double TRIDENT_WATER_INERTIA = 0.99;
   private static final double THROWABLE_GRAVITY = 0.03;
   private static final double POTION_GRAVITY = 0.05;
   private static final double EXPERIENCE_BOTTLE_GRAVITY = 0.07;
   private static final double ARROW_GRAVITY = 0.05;

   public TrajectoriesFeature() {
      super("Trajectories", "Predicts projectile paths and impact points", FeatureCategory.VISUAL, -1);
   }

   public static TrajectoriesFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(TrajectoriesFeature.class);
   }

   @EventTarget
   public void on2DRender(Render2DEvent event) {
      if (!this.pendingScreenLines.isEmpty() || !this.points.isEmpty()) {
         float unit = ScaleUtil.toGuiPixels(1.6F, mc.getWindow().getGuiScale());
         float centerX = event.getGuiGraphicsExtractor().guiWidth() * 0.5F;
         float centerY = event.getGuiGraphicsExtractor().guiHeight() * 0.5F;
         float connectorThickness = Math.max(0.5F, 1.35F * unit);

         for (TrajectoriesFeature.ScreenLine line : this.pendingScreenLines) {
            this.drawScreenLine(event, centerX, centerY, line.x(), line.y(), connectorThickness, line.color());
         }

         for (TrajectoriesFeature.ImpactPoint point : this.points) {
            TrajectoriesFeature.ScreenPoint screen = this.projectToScreen(point.pos());
            if (screen != null) {
               this.renderImpactTag(event, point, screen.x(), screen.y(), unit);
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.clear();
   }

   public void renderWorld() {
      this.pendingScreenLines.clear();
      this.points.clear();
      if (mc.level != null && mc.player != null) {
         Vec3 oldPlayerPos = mc.player.oldPosition();
         double dx = mc.player.getX() - oldPlayerPos.x;
         double dy = mc.player.getY() - oldPlayerPos.y;
         double dz = mc.player.getZ() - oldPlayerPos.z;
         if (dx * dx + dy * dy + dz * dz > 36.0) {
            this.tpSkipTicks = 2;
         }

         if (this.tpSkipTicks > 0) {
            this.tpSkipTicks--;
            this.pendingScreenLines.clear();
         } else {
            List<WorldMeshRenderer.Line> lines = new ArrayList<>();
            List<WorldMeshRenderer.Ring> rings = new ArrayList<>();
            List<WorldMeshRenderer.PlaneRect> planeRects = new ArrayList<>();
            if (this.predictTrajectory.isSelected("Self")) {
               this.drawPredictionInHand(lines, rings, planeRects);
            }

            if (this.predictTrajectory.isSelected("Players")) {
               this.drawOtherPlayersPrediction(lines, rings, planeRects);
            }

            for (Entity entity : mc.level.entitiesForRendering()) {
               if ((entity instanceof Projectile || entity instanceof ItemEntity) && !this.isStationary(entity)) {
                  Vec3 motion = entity.getDeltaMovement();
                  float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                  double startX = Mth.lerp(partialTicks, entity.xo, entity.getX());
                  double startY = Mth.lerp(partialTicks, entity.yo, entity.getY());
                  double startZ = Mth.lerp(partialTicks, entity.zo, entity.getZ());
                  Vec3 pos = new Vec3(startX, startY, startZ);

                  for (int tick = 0; tick < 300; tick++) {
                     Vec3 prevPos = pos;
                     TrajectoriesFeature.TrajectoryStep step;
                     if (entity instanceof Projectile projectile) {
                        step = this.simulateProjectileStep(projectile, pos, motion);
                     } else {
                        Vec3 nextPos = pos.add(motion);
                        Vec3 nextMotion = this.calculateMotion(entity, prevPos, motion);
                        HitResult hit = this.raycastBlock(prevPos, nextPos, entity);
                        step = new TrajectoriesFeature.TrajectoryStep(
                           hit.getType() != Type.MISS ? hit.getLocation() : nextPos, nextMotion, hit.getType() != Type.MISS ? hit : null
                        );
                     }

                     Vec3 renderEnd = step.hitResult() != null ? step.hitResult().getLocation() : step.nextPos();
                     float alpha = Mth.clamp(tick / 7.0F, 0.0F, 1.0F);
                     lines.add(new WorldMeshRenderer.Line(prevPos, renderEnd, ColorUtil.applyAlpha(this.animatedAccentColor(tick), alpha)));
                     if (step.hitResult() != null || step.nextPos().y < -128.0) {
                        this.registerImpact(entity, renderEnd, tick);
                        break;
                     }

                     pos = step.nextPos();
                     motion = step.nextMotion();
                  }
               }
            }

            WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, rings, planeRects));
         }
      } else {
         this.clear();
      }
   }

   @Override
   protected void onDisable() {
      this.clear();
   }

   private void drawPredictionInHand(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.Ring> rings, List<WorldMeshRenderer.PlaneRect> planeRects) {
      if (mc.level != null && mc.player != null) {
         this.drawPredictionForPlayer(mc.player, true, lines, rings, planeRects);
      }
   }

   private void drawOtherPlayersPrediction(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.Ring> rings, List<WorldMeshRenderer.PlaneRect> planeRects) {
      if (mc.level != null && mc.player != null) {
         for (Player player : mc.level.players()) {
            if (player != mc.player && player.isAlive() && !player.isRemoved()) {
               this.drawPredictionForPlayer(player, false, lines, rings, planeRects);
            }
         }
      }
   }

   private void drawPredictionForPlayer(
      Player player,
      boolean screenAnchored,
      List<WorldMeshRenderer.Line> lines,
      List<WorldMeshRenderer.Ring> rings,
      List<WorldMeshRenderer.PlaneRect> planeRects
   ) {
      if (mc.level != null && player != null) {
         ItemStack activeStack = player.getUseItem();
         ItemStack[] stacks = new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()};

         for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
               float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
               float pitch = Mth.lerp(partialTicks, player.xRotO, player.getXRot());
               float yaw = Mth.lerp(partialTicks, player.yRotO, player.getYRot());
               List<TrajectoriesFeature.Prediction> predictions = new ArrayList<>();
               Item item = stack.getItem();
               if (item instanceof ExperienceBottleItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new ThrownExperienceBottle(mc.level, player, stack), 0.7, pitch, yaw, -20.0F, screenAnchored)
                  );
               } else if (item instanceof SplashPotionItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new ThrownSplashPotion(mc.level, player, stack), 0.5, pitch, yaw, -20.0F, screenAnchored)
                  );
               } else if (item instanceof LingeringPotionItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new ThrownLingeringPotion(mc.level, player, stack), 0.5, pitch, yaw, -20.0F, screenAnchored)
                  );
               } else if (item instanceof TridentItem && !activeStack.isEmpty() && activeStack.getItem() == item && player.getTicksUsingItem() >= 10) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new ThrownTrident(mc.level, player, stack), 2.5, pitch, yaw, 0.0F, screenAnchored)
                  );
               } else if (item instanceof SnowballItem) {
                  this.addPrediction(predictions, this.checkTrajectory(player, new Snowball(mc.level, player, stack), 1.5, pitch, yaw, 0.0F, screenAnchored));
               } else if (item instanceof EggItem) {
                  this.addPrediction(predictions, this.checkTrajectory(player, new ThrownEgg(mc.level, player, stack), 1.5, pitch, yaw, 0.0F, screenAnchored));
               } else if (item instanceof EnderpearlItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new ThrownEnderpearl(mc.level, player, stack), 1.5, pitch, yaw, 0.0F, screenAnchored)
                  );
               } else if (item instanceof BowItem && !activeStack.isEmpty() && activeStack.getItem() == item && player.isUsingItem()) {
                  float power = BowItem.getPowerForTime(player.getTicksUsingItem()) * 3.0F;
                  this.addPrediction(predictions, this.checkTrajectory(player, this.newArrow(player, stack), power, pitch, yaw, 0.0F, screenAnchored));
               } else if (item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
                  ChargedProjectiles charged = (ChargedProjectiles)stack.get(DataComponents.CHARGED_PROJECTILES);
                  if (charged != null && !charged.isEmpty()) {
                     double velocity = charged.contains(Items.FIREWORK_ROCKET) ? 1.6 : 3.15;
                     this.addPrediction(
                        predictions,
                        this.checkTrajectory(
                           player, this.buildCrossbowDirection(player, partialTicks, 0.0F), this.newArrow(player, stack), velocity, screenAnchored
                        )
                     );
                     if (charged.itemCopies().size() > 2) {
                        this.addPrediction(
                           predictions,
                           this.checkTrajectory(
                              player, this.buildCrossbowDirection(player, partialTicks, -10.0F), this.newArrow(player, stack), velocity, screenAnchored
                           )
                        );
                        this.addPrediction(
                           predictions,
                           this.checkTrajectory(
                              player, this.buildCrossbowDirection(player, partialTicks, 10.0F), this.newArrow(player, stack), velocity, screenAnchored
                           )
                        );
                     }
                  }
               }

               for (TrajectoriesFeature.Prediction prediction : predictions) {
                  if (screenAnchored) {
                     TrajectoriesFeature.ScreenPoint connectorEnd = this.projectToScreen(prediction.screenConnectorEnd());
                     if (connectorEnd != null) {
                        this.pendingScreenLines.add(new TrajectoriesFeature.ScreenLine(connectorEnd.x(), connectorEnd.y(), this.animatedAccentColor(0)));
                     }
                  }

                  lines.addAll(prediction.lines());
                  if (prediction.result() != null) {
                     this.addImpactMarker(lines, rings, planeRects, prediction.projectile(), prediction.result());
                  }
               }
            }
         }
      }
   }

   private Arrow newArrow(LivingEntity shooter, ItemStack weapon) {
      return new Arrow(mc.level, shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ(), new ItemStack(Items.ARROW), weapon);
   }

   private TrajectoriesFeature.Prediction checkTrajectory(
      LivingEntity shooter, Projectile entity, double velocity, float pitch, float yaw, float angleOffset, boolean screenAnchored
   ) {
      return this.checkTrajectory(shooter, this.buildShootFromRotationDirection(pitch, yaw, angleOffset), entity, velocity, screenAnchored);
   }

   private TrajectoriesFeature.Prediction checkTrajectory(LivingEntity shooter, Vec3 lookVec, Projectile entity, double velocity, boolean screenAnchored) {
      if (shooter == null) {
         return new TrajectoriesFeature.Prediction(entity, null, List.of(), null);
      }

      float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      double startX = Mth.lerp(partialTicks, shooter.xo, shooter.getX());
      double startY = Mth.lerp(partialTicks, shooter.yo, shooter.getY()) + shooter.getEyeHeight();
      double startZ = Mth.lerp(partialTicks, shooter.zo, shooter.getZ());
      Vec3 startPos = new Vec3(startX, startY - 0.1, startZ);
      entity.setPos(startPos.x, startPos.y, startPos.z);
      Vec3 motion;
      if (entity instanceof AbstractArrow arrow && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() == Items.CROSSBOW) {
         motion = lookVec.normalize().scale(velocity);
      } else {
         motion = this.buildShootFromRotationMotion(shooter, lookVec, velocity, true);
      }

      return this.traceTrajectory(startPos, motion, entity, screenAnchored);
   }

   private TrajectoriesFeature.Prediction traceTrajectory(Vec3 start, Vec3 startMotion, Projectile entity, boolean screenAnchored) {
      List<WorldMeshRenderer.Line> lines = new ArrayList<>();
      Vec3 screenConnectorEnd = null;
      Vec3 pos = start;
      Vec3 motion = startMotion;

      for (int tick = 0; tick < 300; tick++) {
         TrajectoriesFeature.TrajectoryStep step = this.simulateProjectileStep(entity, pos, motion);
         Vec3 renderEnd = step.hitResult() != null ? step.hitResult().getLocation() : step.nextPos();
         float alpha = Mth.clamp(tick / 7.0F, 0.0F, 1.0F);
         if (tick == 0 && screenAnchored) {
            screenConnectorEnd = renderEnd;
         } else {
            lines.add(new WorldMeshRenderer.Line(pos, renderEnd, ColorUtil.applyAlpha(this.animatedAccentColor(tick), alpha)));
         }

         if (step.hitResult() != null) {
            return new TrajectoriesFeature.Prediction(entity, step.hitResult(), lines, screenConnectorEnd);
         }

         if (step.nextPos().y < -128.0) {
            break;
         }

         pos = step.nextPos();
         motion = step.nextMotion();
      }

      return new TrajectoriesFeature.Prediction(entity, null, lines, screenConnectorEnd);
   }

   private TrajectoriesFeature.TrajectoryStep simulateProjectileStep(Projectile projectile, Vec3 pos, Vec3 motion) {
      return projectile instanceof AbstractArrow arrow ? this.simulateArrowStep(arrow, pos, motion) : this.simulateThrowableStep(projectile, pos, motion);
   }

   private TrajectoriesFeature.TrajectoryStep simulateThrowableStep(Projectile projectile, Vec3 pos, Vec3 motion) {
      Vec3 nextMotion = motion.add(0.0, -this.projectileGravity(projectile), 0.0).scale(this.isInWater(pos) ? 0.8 : 0.99);
      HitResult hit = this.raycastProjectile(pos, nextMotion, projectile);
      return new TrajectoriesFeature.TrajectoryStep(hit != null ? hit.getLocation() : pos.add(nextMotion), nextMotion, hit);
   }

   private TrajectoriesFeature.TrajectoryStep simulateArrowStep(AbstractArrow arrow, Vec3 pos, Vec3 motion) {
      boolean inWater = this.isInWater(pos);
      Vec3 moveDelta = inWater ? motion.scale(arrow instanceof ThrownTrident ? 0.99 : 0.6) : motion;
      HitResult hit = this.raycastProjectile(pos, moveDelta, arrow);
      Vec3 nextPos = hit != null ? hit.getLocation() : pos.add(moveDelta);
      Vec3 nextMotion = inWater ? moveDelta.add(0.0, -0.05, 0.0) : moveDelta.scale(0.99).add(0.0, -0.05, 0.0);
      return new TrajectoriesFeature.TrajectoryStep(nextPos, nextMotion, hit);
   }

   private HitResult raycastProjectile(Vec3 start, Vec3 motion, Projectile projectile) {
      if (mc.level == null) {
         return null;
      }

      Vec3 end = start.add(motion);
      HitResult hit = mc.level.clipIncludingBorder(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, projectile));
      if (hit.getType() != Type.MISS) {
         end = hit.getLocation();
      }

      AABB boxAtStart = projectile.getBoundingBox().move(start.subtract(projectile.position()));
      EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
         mc.level, projectile, start, end, boxAtStart.expandTowards(motion).inflate(1.0), candidate -> this.canHitProjectileTarget(projectile, candidate)
      );
      if (entityHit != null) {
         hit = entityHit;
      }

      return hit.getType() != Type.MISS ? hit : null;
   }

   private boolean canHitProjectileTarget(Projectile projectile, Entity candidate) {
      if (!candidate.canBeHitByProjectile()) {
         return false;
      } else {
         Entity owner = projectile.getOwner();
         if (owner == null) {
            return true;
         } else {
            return candidate != owner && !owner.isPassengerOfSameVehicle(candidate)
               ? !(projectile instanceof AbstractArrow && owner instanceof Player ownerPlayer)
                  || !(candidate instanceof Player targetPlayer && !ownerPlayer.canHarmPlayer(targetPlayer))
               : false;
         }
      }
   }

   private double projectileGravity(Projectile projectile) {
      if (projectile instanceof AbstractArrow) {
         return 0.05;
      } else if (projectile instanceof ThrownExperienceBottle) {
         return 0.07;
      } else if (projectile instanceof AbstractThrownPotion) {
         return 0.05;
      } else {
         return projectile instanceof ThrowableItemProjectile ? 0.03 : 0.03;
      }
   }

   private boolean isInWater(Vec3 pos) {
      return mc.level != null && mc.level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
   }

   private Vec3 buildShootFromRotationMotion(LivingEntity shooter, Vec3 direction, double velocity, boolean addShooterMovement) {
      Vec3 motion = direction.normalize().scale(velocity);
      if (addShooterMovement) {
         Vec3 knownMovement = shooter.getKnownMovement();
         motion = motion.add(knownMovement.x, shooter.onGround() ? 0.0 : knownMovement.y, knownMovement.z);
      }

      return motion;
   }

   private Vec3 buildShootFromRotationDirection(float pitch, float yaw, float angleOffset) {
      double pitchRad = pitch * (Math.PI / 180.0);
      double yawRad = yaw * (Math.PI / 180.0);
      double x = -Math.sin(yawRad) * Math.cos(pitchRad);
      double y = -Math.sin((pitch + angleOffset) * (Math.PI / 180.0));
      double z = Math.cos(yawRad) * Math.cos(pitchRad);
      return new Vec3(x, y, z);
   }

   private Vec3 buildCrossbowDirection(LivingEntity shooter, float partialTicks, float angle) {
      if (angle == 0.0F) {
         return shooter.getViewVector(partialTicks);
      }

      Vec3 up = shooter.getUpVector(partialTicks);
      Quaternionf rotation = new Quaternionf().setAngleAxis(angle * (Math.PI / 180.0), up.x, up.y, up.z);
      Vector3f rotated = shooter.getViewVector(partialTicks).toVector3f().rotate(rotation);
      return new Vec3(rotated.x, rotated.y, rotated.z);
   }

   private Vec3 calculateMotion(Entity entity, Vec3 prevPos, Vec3 motion) {
      boolean water = mc.level != null && mc.level.getFluidState(BlockPos.containing(prevPos)).is(FluidTags.WATER);
      double inertia;
      double gravity;
      if (entity instanceof ThrownTrident) {
         inertia = 0.99;
         gravity = 0.05;
      } else if (entity instanceof AbstractArrow) {
         inertia = water ? 0.6 : 0.99;
         gravity = 0.05;
      } else if (entity instanceof ThrownExperienceBottle) {
         inertia = water ? 0.8 : 0.99;
         gravity = 0.07;
      } else if (entity instanceof AbstractThrownPotion) {
         inertia = water ? 0.8 : 0.99;
         gravity = 0.05;
      } else if (entity instanceof ThrowableItemProjectile) {
         inertia = water ? 0.8 : 0.99;
         gravity = 0.03;
      } else if (entity instanceof ItemEntity) {
         inertia = water ? 0.8 : 0.98;
         gravity = 0.04;
      } else {
         inertia = 0.99;
         gravity = 0.03;
      }

      return motion.scale(inertia).add(0.0, -gravity, 0.0);
   }

   private void addImpactMarker(
      List<WorldMeshRenderer.Line> lines,
      List<WorldMeshRenderer.Ring> rings,
      List<WorldMeshRenderer.PlaneRect> planeRects,
      Projectile projectile,
      HitResult result
   ) {
      Direction direction = this.getDirection(result);
      int color = result.getType() == Type.ENTITY ? -48060 : this.animatedAccentColor(0);
      Vec3 center = result.getLocation().add(direction.getStepX() * 0.01, direction.getStepY() * 0.01, direction.getStepZ() * 0.01);
      double width = 0.12;
      Vec3 u;
      Vec3 v;
      switch (direction.getAxis()) {
         case X:
            u = new Vec3(0.0, 1.0, 0.0);
            v = new Vec3(0.0, 0.0, 1.0);
            break;
         case Y:
            u = new Vec3(1.0, 0.0, 0.0);
            v = new Vec3(0.0, 0.0, 1.0);
            break;
         case Z:
            u = new Vec3(1.0, 0.0, 0.0);
            v = new Vec3(0.0, 1.0, 0.0);
            break;
         default:
            u = new Vec3(1.0, 0.0, 0.0);
            v = new Vec3(0.0, 0.0, 1.0);
      }

      rings.add(new WorldMeshRenderer.Ring(center, u, v, width, 0.004, color, 180));
      double crossRadius = width * 0.72;
      planeRects.add(new WorldMeshRenderer.PlaneRect(center, u, v, crossRadius, 0.003, color));
      planeRects.add(new WorldMeshRenderer.PlaneRect(center, v, u, crossRadius, 0.003, color));
      if (result instanceof EntityHitResult entityHit) {
         this.addEntityHitBox(lines, planeRects, entityHit.getEntity());
      }

      Double areaRadius = this.potionAreaRadius(projectile);
      if (areaRadius != null) {
         Vec3 areaCenter = this.resolvePotionAreaCenter(projectile, result);
         int areaColor = this.animatedAccentColor(0);
         rings.add(
            new WorldMeshRenderer.Ring(
               areaCenter, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), areaRadius, 0.018, ColorUtil.applyAlpha(areaColor, 0.55F), 220
            )
         );
         if (areaCenter.distanceToSqr(result.getLocation()) > 1.0E-4) {
            lines.add(new WorldMeshRenderer.Line(result.getLocation(), areaCenter, ColorUtil.applyAlpha(areaColor, 0.67F)));
         }

         if (projectile instanceof ThrownSplashPotion splashPotion) {
            this.addSplashExposureLines(lines, splashPotion, result, areaCenter);
         }
      }
   }

   private void registerImpact(Entity entity, Vec3 pos, int ticks) {
      ItemStack stack = ItemStack.EMPTY;
      String ownerName = null;
      if (entity instanceof ItemEntity itemEntity) {
         stack = itemEntity.getItem();
      } else if (entity instanceof ThrowableItemProjectile throwable) {
         stack = throwable.getItem();
         ownerName = this.ownerName(throwable.getOwner());
      } else if (entity instanceof ThrownTrident trident) {
         stack = new ItemStack(Items.TRIDENT);
         ownerName = this.ownerName(trident.getOwner());
      } else if (entity instanceof AbstractArrow arrow) {
         stack = arrow.getPickupItemStackOrigin();
         ownerName = this.ownerName(arrow.getOwner());
      }

      List<MobEffectInstance> effects = new ArrayList<>();
      PotionContents potionContents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
      if (potionContents != null) {
         potionContents.getAllEffects().forEach(effects::add);
      }

      this.points.add(new TrajectoriesFeature.ImpactPoint(stack.copy(), pos, ticks, entity.tickCount, ownerName, List.copyOf(effects)));
   }

   private String ownerName(Entity owner) {
      return owner instanceof LivingEntity living ? living.getName().getString() : null;
   }

   private void renderImpactTag(Render2DEvent event, TrajectoriesFeature.ImpactPoint point, float screenX, float screenY, float unit) {
      float width = 36.0F * unit;
      float card = 36.0F * unit;
      float height = 48.0F * unit;
      float gap = 4.0F * unit;
      float radius = 10.0F * unit;
      float border = Math.max(1.0F, 1.2F * unit);
      float icon = 20.0F * unit;
      float textSize = 8.0F * unit;
      float textRow = 8.0F * unit;
      float x = screenX - width * 0.5F;
      float y = screenY - height * 0.5F;
      int totalTicks = point.ticks() + point.entityAge();
      float progress = totalTicks > 0 ? Mth.clamp((float)point.ticks() / totalTicks, 0.0F, 1.0F) : 0.0F;
      int accent = Theme.getAccent();
      Render2DUtil.rect(x, y, card, card).color(Theme.Colors.BACKGROUND_PRIMARY_50).radius(radius).blur(8.0F * unit).draw();
      this.drawRoundedProgressBorder(event, x, y, card, radius, border, progress, accent);
      float iconOffset = (card - icon) * 0.5F;
      this.drawScaledItem(event, point.stack(), x + iconOffset, y + iconOffset, icon);
      String time = this.formatSeconds(point.ticks());
      float textCenterX = x + width * 0.5F;
      float textCenterY = y + card + gap + textRow * 0.5F;
      Render2DUtil.text(textCenterX, UiFonts.sfProDisplay().centeredTextY(textCenterY, textSize), textSize, time)
         .style(UiFontStyle.MEDIUM)
         .align(TextAlign.CENTER)
         .color(Theme.Colors.TEXT_TITLE)
         .draw();
   }

   private void drawScaledItem(Render2DEvent event, ItemStack stack, float x, float y, float size) {
      if (!stack.isEmpty()) {
         Render2DUtil.flush();
         double guiScale = mc.getWindow().getGuiScale();
         float scale = size / 16.0F;
         float itemX = (float)(Math.round(x * guiScale) / guiScale);
         float itemY = (float)(Math.round(y * guiScale) / guiScale);
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().pose();
         pose.pushMatrix();
         pose.translate(itemX, itemY);
         pose.scale(scale);
         event.getGuiGraphicsExtractor().item(stack, 0, 0);
         pose.popMatrix();
      }
   }

   private void drawRoundedProgressBorder(Render2DEvent event, float x, float y, float size, float radius, float thickness, float progress, int color) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      if (!(progress <= 0.001F) && !(thickness <= 0.0F)) {
         float straight = Math.max(0.0F, size - radius * 2.0F);
         float arc = (float)((Math.PI / 2) * radius);
         float perimeter = straight * 4.0F + arc * 4.0F;
         float target = perimeter * progress;
         float half = thickness * 0.5F;
         int samples = Math.max(48, Math.round(perimeter / 1.5F));
         float traveled = 0.0F;
         float prevX = x + size * 0.5F;
         float prevY = y;

         for (int i = 1; i <= samples; i++) {
            float distance = perimeter * ((float)i / samples);
            float[] point = pointOnRoundedRect(x, y, size, radius, straight, arc, distance);
            float seg = distance - traveled;
            if (traveled < target) {
               float drawLen = Math.min(seg, target - traveled);
               float t = drawLen / seg;
               float endX = prevX + (point[0] - prevX) * t;
               float endY = prevY + (point[1] - prevY) * t;
               this.strokeSegment(event, prevX, prevY, endX, endY, half, color);
            }

            traveled = distance;
            prevX = point[0];
            prevY = point[1];
            if (traveled >= target) {
               break;
            }
         }
      }
   }

   private static float[] pointOnRoundedRect(float x, float y, float size, float radius, float straight, float arc, float distance) {
      float cursor = distance;
      float halfTop = straight * 0.5F;
      if (cursor <= halfTop) {
         return new float[]{x + size * 0.5F + cursor, y};
      } else {
         cursor -= halfTop;
         if (cursor <= arc) {
            float angle = (float)((-Math.PI / 2) + cursor / radius);
            return new float[]{x + size - radius + (float)Math.cos(angle) * radius, y + radius + (float)Math.sin(angle) * radius};
         } else {
            cursor -= arc;
            if (cursor <= straight) {
               return new float[]{x + size, y + radius + cursor};
            } else {
               cursor -= straight;
               if (cursor <= arc) {
                  float angle = cursor / radius;
                  return new float[]{x + size - radius + (float)Math.cos(angle) * radius, y + size - radius + (float)Math.sin(angle) * radius};
               } else {
                  cursor -= arc;
                  if (cursor <= straight) {
                     return new float[]{x + size - radius - cursor, y + size};
                  } else {
                     cursor -= straight;
                     if (cursor <= arc) {
                        float angle = (float)((Math.PI / 2) + cursor / radius);
                        return new float[]{x + radius + (float)Math.cos(angle) * radius, y + size - radius + (float)Math.sin(angle) * radius};
                     } else {
                        cursor -= arc;
                        if (cursor <= straight) {
                           return new float[]{x, y + size - radius - cursor};
                        } else {
                           cursor -= straight;
                           if (cursor <= arc) {
                              float angle = (float)(Math.PI + cursor / radius);
                              return new float[]{x + radius + (float)Math.cos(angle) * radius, y + radius + (float)Math.sin(angle) * radius};
                           } else {
                              cursor -= arc;
                              return new float[]{x + radius + Math.min(cursor, halfTop), y};
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void strokeSegment(Render2DEvent event, float x1, float y1, float x2, float y2, float halfThickness, int color) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float length = (float)Math.sqrt(dx * dx + dy * dy);
      if (length < 0.001F) {
         Render2DUtil.rect(x1 - halfThickness, y1 - halfThickness, halfThickness * 2.0F, halfThickness * 2.0F).color(color).draw();
      } else {
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().pose();
         pose.pushMatrix();
         pose.translate(x1, y1);
         pose.rotate((float)Math.atan2(dy, dx));
         float overlap = halfThickness * 0.75F;
         Render2DUtil.rect(-overlap, -halfThickness, length + overlap * 2.0F, halfThickness * 2.0F).color(color).draw();
         pose.popMatrix();
      }
   }

   private void addEntityHitBox(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.PlaneRect> planeRects, Entity entity) {
      AABB box = entity.getBoundingBox().inflate(0.028);
      Vec3 center = new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
      double hx = box.getXsize() * 0.5;
      double hy = box.getYsize() * 0.5;
      double hz = box.getZsize() * 0.5;
      int fillColor = ColorUtil.applyAlpha(this.animatedAccentColor(22), 0.09F);
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3(box.minX, center.y, center.z), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0), hy, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3(box.maxX, center.y, center.z), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0), hy, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3(center.x, box.minY, center.z), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), hx, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3(center.x, box.maxY, center.z), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), hx, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3(center.x, center.y, box.minZ), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), hx, hy, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3(center.x, center.y, box.maxZ), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), hx, hy, fillColor)
      );
      Vec3[] corners = new Vec3[]{
         new Vec3(box.minX, box.minY, box.minZ),
         new Vec3(box.minX, box.minY, box.maxZ),
         new Vec3(box.minX, box.maxY, box.minZ),
         new Vec3(box.minX, box.maxY, box.maxZ),
         new Vec3(box.maxX, box.minY, box.minZ),
         new Vec3(box.maxX, box.minY, box.maxZ),
         new Vec3(box.maxX, box.maxY, box.minZ),
         new Vec3(box.maxX, box.maxY, box.maxZ)
      };
      int[][] edges = new int[][]{{0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3}, {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}};

      for (int i = 0; i < edges.length; i++) {
         int edgeColor = ColorUtil.applyAlpha(this.animatedAccentColor(i * 11), 0.94F);
         lines.add(new WorldMeshRenderer.Line(corners[edges[i][0]], corners[edges[i][1]], edgeColor));
      }
   }

   private void addSplashExposureLines(List<WorldMeshRenderer.Line> lines, ThrownSplashPotion projectile, HitResult result, Vec3 areaCenter) {
      if (mc.level != null) {
         Entity hitEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
         AABB affectedBox = new AABB(areaCenter.x - 4.0, areaCenter.y - 2.0, areaCenter.z - 4.0, areaCenter.x + 4.0, areaCenter.y + 2.0, areaCenter.z + 4.0);

         for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, affectedBox, candidate -> candidate.isAlive() && !candidate.isRemoved())) {
            if (entity != mc.player) {
               Vec3 targetPoint = new Vec3(entity.getX(), areaCenter.y, entity.getZ());
               Vec3 offset = targetPoint.subtract(areaCenter);
               Vec3 horizontal = new Vec3(offset.x, 0.0, offset.z);
               double distance = Math.sqrt(horizontal.x * horizontal.x + horizontal.z * horizontal.z);
               if (!(distance > 4.0) && !(distance < 0.001)) {
                  double exposure = entity == hitEntity ? 1.0 : Mth.clamp(1.0 - distance / 4.0, 0.0, 1.0);
                  float alpha = (float)(0.38 + 0.54 * exposure);
                  lines.add(new WorldMeshRenderer.Line(areaCenter, targetPoint, ColorUtil.applyAlpha(-12386427, alpha), ColorUtil.applyAlpha(-42920, alpha)));
               }
            }
         }
      }
   }

   private Double potionAreaRadius(Projectile projectile) {
      if (projectile instanceof ThrownSplashPotion) {
         return 4.0;
      } else {
         return projectile instanceof ThrownLingeringPotion ? 3.0 : null;
      }
   }

   private Vec3 resolvePotionAreaCenter(Projectile projectile, HitResult result) {
      if (mc.level == null) {
         return result.getLocation();
      }

      Vec3 start = result.getLocation().add(0.0, 0.35, 0.0);
      Vec3 end = result.getLocation().add(0.0, -2.5, 0.0);
      HitResult groundHit = mc.level.clipIncludingBorder(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, projectile));
      return groundHit.getType() != Type.MISS ? groundHit.getLocation().add(0.0, 0.012, 0.0) : result.getLocation();
   }

   private Direction getDirection(HitResult result) {
      if (result instanceof BlockHitResult blockHit) {
         return blockHit.getDirection();
      } else {
         if (mc.player == null) {
            return Direction.UP;
         }

         Vec3 vec = result.getLocation().subtract(mc.player.getEyePosition()).normalize();
         return Direction.getApproximateNearest((float)vec.x, (float)vec.y, (float)vec.z);
      }
   }

   private boolean isStationary(Entity entity) {
      boolean posChange = entity.position().equals(entity.oldPosition());
      boolean itemEntityCheck = entity instanceof ItemEntity
         && (entity.onGround() || mc.level != null && mc.level.getFluidState(entity.blockPosition()).is(FluidTags.WATER));
      return posChange || itemEntityCheck;
   }

   private HitResult raycastBlock(Vec3 start, Vec3 end, Entity entity) {
      return mc.level == null
         ? BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end))
         : mc.level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, entity));
   }

   private void drawScreenLine(Render2DEvent event, float x1, float y1, float x2, float y2, float thickness, int color) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float length = (float)Math.sqrt(dx * dx + dy * dy);
      if (!(length < 0.001F)) {
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().pose();
         pose.pushMatrix();
         pose.translate(x1, y1);
         pose.rotate((float)Math.atan2(dy, dx));
         Render2DUtil.rect(0.0F, -thickness * 0.5F, length, thickness).color(color).draw();
         pose.popMatrix();
      }
   }

   private String formatSeconds(int ticks) {
      int seconds = Math.max(0, Math.round(ticks / 20.0F));
      return seconds + "s";
   }

   private TrajectoriesFeature.ScreenPoint projectToScreen(Vec3 pos) {
      Render3DUtil.ScreenPoint point = Render3DUtil.projectToScreen(mc, pos);
      return point == null ? null : new TrajectoriesFeature.ScreenPoint(point.x(), point.y());
   }

   private void addPrediction(List<TrajectoriesFeature.Prediction> predictions, TrajectoriesFeature.Prediction prediction) {
      if (prediction != null) {
         predictions.add(prediction);
      }
   }

   private void clear() {
      this.tpSkipTicks = 0;
      this.pendingScreenLines.clear();
      this.points.clear();
   }

   private int animatedAccentColor(int index) {
      return ColorUtil.fade(8, index * 11, Theme.getAccent(), ColorUtil.rgb(101, 228, 255));
   }

   private record ImpactPoint(ItemStack stack, Vec3 pos, int ticks, int entityAge, String ownerName, List<MobEffectInstance> effects) {
   }

   private record Prediction(Projectile projectile, HitResult result, List<WorldMeshRenderer.Line> lines, Vec3 screenConnectorEnd) {
   }

   private record ScreenLine(float x, float y, int color) {
   }

   private record ScreenPoint(float x, float y) {
   }

   private record TrajectoryStep(Vec3 nextPos, Vec3 nextMotion, HitResult hitResult) {
   }
}
