package win.furryhook.feature.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix3x2fStack;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.PlayerInputEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.render.Render2DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.movement.AirStuckFeature;
import win.furryhook.feature.impl.movement.SprintFeature;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.mixin.accessor.MultiPlayerGameModeAccessor;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.FriendManager;
import win.furryhook.utils.combat.AuraUtil;
import win.furryhook.utils.combat.MultipointUtil;
import win.furryhook.utils.combat.PredictUtils;
import win.furryhook.utils.combat.SprintManager;
import win.furryhook.utils.combat.neuro.NeuroDataset;
import win.furryhook.utils.combat.rotations.AuraRotation;
import win.furryhook.utils.combat.rotations.BMWRotation;
import win.furryhook.utils.combat.rotations.CustomRotation;
import win.furryhook.utils.combat.rotations.FTNewRotation;
import win.furryhook.utils.combat.rotations.FunTestRotation;
import win.furryhook.utils.combat.rotations.FunfickRotation;
import win.furryhook.utils.combat.rotations.FuntimeDick;
import win.furryhook.utils.combat.rotations.FuntimeRotation;
import win.furryhook.utils.combat.rotations.FurryRotation;
import win.furryhook.utils.combat.rotations.GrimRotation;
import win.furryhook.utils.combat.rotations.HolyWorldRotation;
import win.furryhook.utils.combat.rotations.LegitSnapRotation;
import win.furryhook.utils.combat.rotations.NeuroRotation;
import win.furryhook.utils.combat.rotations.PolarRotation;
import win.furryhook.utils.combat.rotations.ReallyworldRotation;
import win.furryhook.utils.combat.rotations.ShardRotation;
import win.furryhook.utils.combat.rotations.SlothRotation;
import win.furryhook.utils.combat.rotations.SnapRotation;
import win.furryhook.utils.combat.rotations.SpaceTimeRotation;
import win.furryhook.utils.combat.rotations.SpookyRotation;
import win.furryhook.utils.combat.rotations.TestRotation;
import win.furryhook.utils.inventory.InventoryUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.MsdfFont;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.ScaleUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.render.gui.UiFonts;
import win.furryhook.utils.render.target.SoulImpulse;
import win.furryhook.utils.render.world.StormState;
import win.furryhook.utils.text.ChatUtil;

public final class AuraFeature extends Feature {
   public static LivingEntity target = null;
   public static double bpsTarget = 0.0;
   private static final String TARGET_ESP_MARKER = "Marker";
   private static final String TARGET_ESP_GHOSTS = "Ghosts";
   private static final String TARGET_ESP_CIRCLE = "Circle";
   private static final String TARGET_ESP_LIGHTNING = "Молнии";
   private static final String TARGET_ESP_SOULS = "Души";
   public final ModeSetting targetEsp = this.register(new ModeSetting("Target ESP", "Marker", "Marker", "Ghosts", "Circle", "Молнии", "Души"));
   public final NumberSetting soulsCount = this.register(new NumberSetting("Souls Count", 4.0, 1.0, 8.0, 1.0, "").visibleWhen(this::usesSoulsTargetEsp));
   public final NumberSetting soulsSize = this.register(new NumberSetting("Souls Size", 1.0, 0.4, 2.5, 0.1, "x").visibleWhen(this::usesSoulsTargetEsp));
   public final NumberSetting soulsSpeed = this.register(new NumberSetting("Souls Speed", 1.0, 0.4, 2.5, 0.1, "x").visibleWhen(this::usesSoulsTargetEsp));
   public final NumberSetting soulsTrail = this.register(new NumberSetting("Souls Trail", 1.0, 0.3, 2.5, 0.1, "x").visibleWhen(this::usesSoulsTargetEsp));
   public final BooleanSetting soulsThroughWalls = this.register(new BooleanSetting("Souls Through Walls", true).visibleWhen(this::usesSoulsTargetEsp));
   public final ModeSetting targetEspColorMode = this.register(ColorMode.setting());
   public final ColorSetting targetEspColor = this.register(
      new ColorSetting("Target ESP Color", -16711800).visibleWhen(() -> ColorMode.isCustom(this.targetEspColorMode))
   );
   public final ModeSetting sortMode = this.register(new ModeSetting("Sort Mode", "По здоровью", "По здоровью", "По дистанции", "По броне", "По FOV"));
   public final ModeSetting componentMode = this.register(
      new ModeSetting(
            "Rotation Mode",
            "ReallyWorld",
            "ReallyWorld",
            "Grim",
            "Snap",
            "Funtime",
            "FuntimeDick",
            "Funfick",
            "Polar",
            "Spooky",
            "HolyWorld",
            "BMW",
            "Legit Snap",
            "Sloth",
            "Furry",
            "Neuro",
            "Test",
            "SpaceTime",
            "FT-New",
            "FunTest",
            "Custom",
            "Shard"
         )
         .hideModes("Furry")
   );
   public final NumberSetting legitSnapFov = this.register(
      new NumberSetting("FOV", 25.0, 5.0, 90.0, 1.0, "").visibleWhen(() -> this.componentMode.is("Legit Snap"))
   );
   public final ModeSetting legitSnapProcessor = this.register(
      new ModeSetting("Snap Processor", "Funtime", "Funtime", "Smooth", "Grim").visibleWhen(() -> this.componentMode.is("Legit Snap"))
   );
   public final BooleanSetting legitSnapCritsWithSpace = this.register(
      new BooleanSetting("Snap Crits With Space", false)
         .visibleWhen(() -> (this.componentMode.is("Legit Snap") || this.componentMode.is("Funfick")) && this.combatSettings.isSelected("Only Criticals"))
   );
   public final NumberSetting funtimeSnapTicks = this.register(
      new NumberSetting("Snap Ticks", 3.0, 2.0, 8.0, 1.0, " ticks").visibleWhen(() -> this.componentMode.is("Funtime"))
   );
   public final NumberSetting funtimeSmoothness = this.register(
      new NumberSetting("Smoothness", 55.0, 0.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("Funtime"))
   );
   public final NumberSetting funtimeOffset = this.register(
      new NumberSetting("Aim Offset", 1.2, 0.0, 5.0, 0.1, "°").visibleWhen(() -> this.componentMode.is("Funtime"))
   );
   public final NumberSetting funtimeMultipoint = this.register(
      new NumberSetting("Multipoint", 3.0, 1.0, 5.0, 1.0, " points").visibleWhen(() -> this.componentMode.is("Funtime"))
   );
   public final NumberSetting dickMinClosure = this.register(
      new NumberSetting("Min Closure", 30.0, 5.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("FuntimeDick"))
   );
   public final NumberSetting dickMaxClosure = this.register(
      new NumberSetting("Max Closure", 86.0, 5.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("FuntimeDick"))
   );
   public final NumberSetting dickSpread = this.register(
      new NumberSetting("Aim Spread", 55.0, 0.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("FuntimeDick"))
   );
   public final NumberSetting dickHoldTicks = this.register(
      new NumberSetting("Hold Ticks", 20.0, 0.0, 40.0, 1.0, " ticks").visibleWhen(() -> this.componentMode.is("FuntimeDick"))
   );
   public final NumberSetting polarTolerance = this.register(
      new NumberSetting("On-Target Tolerance", 4.0, 1.0, 15.0, 0.5, "°").visibleWhen(() -> this.componentMode.is("Polar"))
   );
   public final NumberSetting polarYawStep = this.register(
      new NumberSetting("Max Yaw Step", 35.0, 5.0, 90.0, 1.0, "°").visibleWhen(() -> this.componentMode.is("Polar"))
   );
   public final NumberSetting polarPitchStep = this.register(
      new NumberSetting("Max Pitch Step", 22.0, 5.0, 90.0, 1.0, "°").visibleWhen(() -> this.componentMode.is("Polar"))
   );
   public final NumberSetting polarAimHeight = this.register(
      new NumberSetting("Aim Height", 72.0, 30.0, 95.0, 1.0, "%").visibleWhen(() -> this.componentMode.is("Polar"))
   );
   public final NumberSetting shardYawStep = this.register(
      new NumberSetting("Max Yaw Step", 26.0, 5.0, 60.0, 1.0, "°").visibleWhen(() -> this.componentMode.is("Shard"))
   );
   public final NumberSetting shardPitchStep = this.register(
      new NumberSetting("Max Pitch Step", 18.0, 5.0, 45.0, 1.0, "°").visibleWhen(() -> this.componentMode.is("Shard"))
   );
   public final ModeSetting furryMode = this.register(
      new ModeSetting("Furry Mode", "Spin + Jitter", "Spin + Jitter", "Spin 360", "Jitter").visibleWhen(() -> this.componentMode.is("Furry"))
   );
   public final NumberSetting furrySpinTicks = this.register(
      new NumberSetting("Spin Ticks", 6.0, 3.0, 12.0, 1.0, " ticks").visibleWhen(() -> this.componentMode.is("Furry") && !this.furryMode.is("Jitter"))
   );
   public final NumberSetting furryJitter = this.register(
      new NumberSetting("Jitter", 100.0, 0.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("Furry") && !this.furryMode.is("Spin 360"))
   );
   public final BooleanSetting displayFov = this.register(new BooleanSetting("Display FOV", true).visibleWhen(() -> this.componentMode.is("Legit Snap")));
   public final BooleanSetting neuroLearning = this.register(new BooleanSetting("Learning Mode", false).visibleWhen(() -> this.componentMode.is("Neuro")));
   public final ModeSetting neuroPlayback = this.register(
      new ModeSetting("Neuro Playback", "Blend", "Blend", "Replay").visibleWhen(() -> this.componentMode.is("Neuro"))
   );
   public final NumberSetting neuroNeighbours = this.register(
      new NumberSetting("Blended Samples", 6.0, 1.0, 16.0, 1.0, "").visibleWhen(() -> this.componentMode.is("Neuro") && this.neuroPlayback.is("Blend"))
   );
   public final NumberSetting neuroVariation = this.register(
      new NumberSetting("Variation", 35.0, 0.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("Neuro") && this.neuroPlayback.is("Blend"))
   );
   public final BooleanSetting neuroAssist = this.register(new BooleanSetting("Hit Assist", true).visibleWhen(() -> this.componentMode.is("Neuro")));
   public final NumberSetting neuroAssistLimit = this.register(
      new NumberSetting("Assist Limit", 100.0, 0.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("Neuro") && this.neuroAssist.getValue())
   );
   public final BooleanSetting neuroDuelOnly = this.register(
      new BooleanSetting("Learn 1v1 Only", false).visibleWhen(() -> this.componentMode.is("Neuro") && this.neuroLearning.getValue())
   );
   public final NumberSetting neuroDuelBias = this.register(
      new NumberSetting("1v1 Priority", 50.0, 0.0, 100.0, 5.0, "%").visibleWhen(() -> this.componentMode.is("Neuro"))
   );
   public final BooleanSetting neuroReplayHits = this.register(
      new BooleanSetting("Replay Landed Only", true).visibleWhen(() -> this.componentMode.is("Neuro") && this.neuroPlayback.is("Replay"))
   );
   public final ModeSetting neuroDataset = this.register(
      new ModeSetting("Neuro Dataset", "default", "default").visibleWhen(() -> this.componentMode.is("Neuro"))
   );
   public final BooleanSetting neuroOverlay = this.register(new BooleanSetting("Show Samples", true).visibleWhen(() -> this.componentMode.is("Neuro")));
   public final MultiSelectSetting targets = this.register(
      new MultiSelectSetting(
         "Targets", List.of("Игроки", "Голые", "Невидимки", "Голые невидимки"), "Игроки", "Голые", "Мобы", "Друзья", "Невидимки", "Голые невидимки"
      )
   );
   public final ModeSetting correctionType = this.register(
      new ModeSetting("Movement Correction", "Сфокусированная", "Свободная", "Преследование", "Сфокусированная", "Таргет")
   );
   public final NumberSetting attackRange = this.register(new NumberSetting("Attack Range", 3.0, 2.5, 6.0, 0.1, " blocks"));
   public final NumberSetting rotateDistance = this.register(new NumberSetting("Rotate Distance", 0.0, 0.0, 3.0, 0.1, " blocks"));
   public final NumberSetting attackDelay = this.register(new NumberSetting("Attack Delay", 380.0, 250.0, 600.0, 10.0, "ms"));
   public final NumberSetting noPetRange = this.register(new NumberSetting("No-Pet Range", 5.0, 3.0, 6.0, 0.1, " blocks"));
   public final BooleanSetting packetCrits = this.register(new BooleanSetting("Пакетные криты", true));
   public final ModeSetting sprintMode = this.register(new ModeSetting("Sprint Mode", "Легитный", "Обычный", "Легитный"));
   public final MultiSelectSetting combatSettings = this.register(
      new MultiSelectSetting(
         "Combat Settings",
         List.of("Only Criticals", "Turbo Criticals", "Unblock Shield", "Break Shield", "Smart Criticals", "Don't Hit While Eating"),
         "Only Criticals",
         "Turbo Criticals",
         "Unblock Shield",
         "Break Shield",
         "Smart Criticals",
         "Through Walls",
         "Don't Hit While Eating"
      )
   );
   public final BooleanSetting bypassWalls = this.register(
      new BooleanSetting("Bypass Walls RW", false).visibleWhen(() -> this.combatSettings.isSelected("Through Walls"))
   );
   public final BooleanSetting maceSwap = this.register(new BooleanSetting("Mace Swap", false));
   public final NumberSetting maceHeight = this.register(new NumberSetting("Mace Height", 3.0, 1.0, 10.0, 0.5, " blocks").visibleWhen(this.maceSwap::getValue));
   public final BooleanSetting clientLook = this.register(new BooleanSetting("Client Look", false));
   public final BooleanSetting predictElytra = this.register(new BooleanSetting("Предикт на элитрах", true));
   public final ModeSetting predictMode = this.register(new ModeSetting("Предикт", "Обычный", "Обычный", "Легитный").visibleWhen(this.predictElytra::getValue));
   private static final double CRIT_HOP = 0.0625;
   private static final double STICKY_RANGE_BONUS = 0.75;
   private static final double RANGE_MARGIN = 0.153;
   private Vec3 predictedElytraPos;
   private LivingEntity elytraTarget;
   private long cpsLimit = 0L;
   private final ReallyworldRotation reallyworldRotation = new ReallyworldRotation();
   private final GrimRotation grimRotation = new GrimRotation();
   private final SnapRotation snapRotation = new SnapRotation();
   private final FuntimeRotation funtimeRotation = new FuntimeRotation();
   private final FuntimeDick funtimeDick = new FuntimeDick();
   private final PolarRotation polarRotation = new PolarRotation();
   private final SpookyRotation spookyRotation = new SpookyRotation();
   private final HolyWorldRotation holyWorldRotation = new HolyWorldRotation();
   private final BMWRotation bmwRotation = new BMWRotation();
   private final LegitSnapRotation legitSnapRotation = new LegitSnapRotation();
   private final FunfickRotation funfickRotation = new FunfickRotation();
   private final SpaceTimeRotation spaceTimeRotation = new SpaceTimeRotation();
   private final SlothRotation slothRotation = new SlothRotation();
   private final FurryRotation furryRotation = new FurryRotation();
   private final NeuroRotation neuroRotation = new NeuroRotation();
   private final TestRotation testRotation = new TestRotation();
   private final ShardRotation shardRotation = new ShardRotation();
   private final FTNewRotation ftNewRotation = new FTNewRotation();
   private final FunTestRotation funTestRotation = new FunTestRotation();
   private final CustomRotation customRotation = new CustomRotation();
   private String requestedNeuroDataset;

   public AuraFeature() {
      super("Aura", "Automatically attacks selected entities", FeatureCategory.COMBAT, -1);
   }

   public static AuraFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
   }

   public static AuraFeature getMarkerFeature() {
      return FeatureManager.INSTANCE.getFeature(AuraFeature.class);
   }

   public LivingEntity getTarget() {
      return this.isEnabled() ? target : null;
   }

   public LivingEntity getCurrentTarget() {
      return this.getTarget();
   }

   public boolean usesGhostTargetEsp() {
      return "Ghosts".equals(this.targetEsp.getValue());
   }

   public boolean usesCircleTargetEsp() {
      return "Circle".equals(this.targetEsp.getValue());
   }

   public boolean usesRhombusTargetEsp() {
      return false;
   }

   public boolean usesLightningTargetEsp() {
      return "Молнии".equals(this.targetEsp.getValue());
   }

   public boolean usesSoulsTargetEsp() {
      return "Души".equals(this.targetEsp.getValue());
   }

   public int soulCount() {
      return (int)Math.round(this.soulsCount.getValue());
   }

   public float soulSize() {
      return this.soulsSize.getValue().floatValue();
   }

   public float soulSpeed() {
      return this.soulsSpeed.getValue().floatValue();
   }

   public float soulTrail() {
      return this.soulsTrail.getValue().floatValue();
   }

   public boolean soulsThroughWalls() {
      return this.soulsThroughWalls.getValue();
   }

   public int getMarkerColor() {
      return ColorMode.resolve(this.targetEspColorMode, this.targetEspColor);
   }

   public boolean shouldAutoJump(LocalPlayer player) {
      if (!this.isEnabled() || target == null) {
         return false;
      }

      if (!this.combatSettings.isSelected("Only Criticals") && !this.combatSettings.isSelected("Turbo Criticals")) {
         return false;
      }

      if (this.checkReturn(player)) {
         return false;
      }

      if (!player.isFallFlying() && !player.getAbilities().flying) {
         if (this.getStrictDistance(target) >= this.attackDistance()) {
            return false;
         } else {
            float swing = player.getAttackStrengthScale(0.0F);
            float minSwing = this.combatSettings.isSelected("Turbo Criticals") ? 0.82F : 0.9F;
            if (swing < minSwing) {
               return false;
            } else if (player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.BLINDNESS)) {
               return false;
            } else {
               return player.isEyeInFluid(FluidTags.WATER) || player.isEyeInFluid(FluidTags.LAVA) ? false : !player.onClimbable() && !player.isPassenger();
            }
         }
      } else {
         return false;
      }
   }

   @Override
   protected void onEnable() {
      this.cpsLimit = System.currentTimeMillis();
      this.resetRotationState();
   }

   @Override
   protected void onDisable() {
      target = null;
      this.neuroRotation.setLearning(false);
      this.resetRotationState();
      RotationContext.release(this);
      RotationContext.clearIfOwner(this);
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      target = null;
      NeuroDataset.INSTANCE.saveIfDirty();
      RotationContext.release(this);
      RotationContext.clearIfOwner(this);
   }

   @EventTarget
   public void onInput(PlayerInputEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         if (target == null || !this.isValidTarget(target)) {
            target = this.findTarget();
         }

         if (target != null) {
            boolean rotateActive = RotationContext.isActive();
            boolean controlling = rotateActive || target != null;
            if (this.correctionType.is("Свободная")) {
               if (controlling && !this.clientLook.getValue()) {
                  this.fixMovement(event, rotateActive ? RotationContext.getFreeYaw() : player.getYRot());
               }
            } else if (this.correctionType.is("Сфокусированная")) {
               if (controlling && !this.clientLook.getValue()) {
                  this.fixMovement(event, rotateActive ? RotationContext.getServerYaw() : player.getYRot());
               }
            } else if (this.correctionType.is("Преследование")) {
               if (!this.clientLook.getValue()) {
                  double dx = target.getX() - player.getX();
                  double dz = target.getZ() - player.getZ();
                  float chaseYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
                  this.fixMovement(event, chaseYaw);
               }
            } else if (this.correctionType.is("Таргет")) {
               if (!rotateActive) {
                  this.updateRotation();
               }

               Vec3 followPos = player.isFallFlying() && target.isFallFlying() && this.predictElytra.getValue() ? this.realPredict(target) : target.position();
               this.moveToPosition(event, followPos, RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot());
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && !player.isDeadOrDying()) {
         if (this.componentMode.is("Neuro")) {
            this.configureNeuroRotation();
         }

         if (target != null) {
            Vec3 delta = target.position().subtract(new Vec3(target.xo, target.yo, target.zo));
            bpsTarget = delta.length() * 20.0;
            Player fakePlayer = null;
            if ((fakePlayer != null ? fakePlayer : player).isFallFlying() && target.isFallFlying()) {
               Vec3 point = this.realPredict(target);
               this.predictedElytraPos = point;
               this.elytraTarget = target;
            } else {
               this.predictedElytraPos = null;
               this.elytraTarget = null;
            }
         }

         if (target == null || !this.isValidTarget(target)) {
            target = this.findTarget();
         }

         if (target != null) {
            boolean rotateFirst = player.isFallFlying()
               || this.componentMode.is("Legit Snap")
               || this.componentMode.is("Funfick")
               || this.componentMode.is("SpaceTime")
               || this.componentMode.is("Spooky")
               || this.componentMode.is("Shard");
            if (rotateFirst) {
               this.updateRotation();
            }

            if (this.canAttack() && this.legitSnapAllowsHit(player)) {
               this.updateAttack();
            }

            if (!rotateFirst) {
               this.updateRotation();
            }
         } else if (this.componentMode.is("FuntimeDick") && this.funtimeDick.isHolding()) {
            this.funtimeDick.tick(player, null, player.getEyePosition(), false);
         } else if (this.componentMode.is("Legit Snap") && this.legitSnapRotation.isReturning()) {
            this.legitSnapRotation.tick(player, null, player.getEyePosition(), false);
         } else if (this.componentMode.is("Funfick") && this.funfickRotation.isReturning()) {
            this.funfickRotation.tick(player, null, player.getEyePosition(), false);
         } else if (this.componentMode.is("SpaceTime") && this.spaceTimeRotation.isReturning()) {
            this.spaceTimeRotation.tick(player, null, player.getEyePosition(), false);
         } else {
            this.reset();
         }
      } else {
         this.reset();
      }
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      this.drawLegitSnapFov(event);
      this.drawNeuroCounter(event);
   }

   private void drawNeuroCounter(Render2DEvent event) {
      if (this.neuroOverlay.getValue() && this.componentMode.is("Neuro")) {
         Minecraft mc = event.getClient();
         if (mc.player != null && mc.options.getCameraType() == CameraType.FIRST_PERSON && !mc.getDebugOverlay().showDebugScreen()) {
            boolean learning = this.neuroRotation.isLearning();
            if (learning || target != null) {
               String label = learning
                  ? "REC  "
                     + NeuroDataset.INSTANCE.sampleCount()
                     + "  •  "
                     + this.neuroRotation.swingFrames()
                     + "  •  1v1 "
                     + NeuroDataset.INSTANCE.duelCount()
                  : "NEURO  " + NeuroDataset.INSTANCE.sampleCount() + "  •  hit " + NeuroDataset.INSTANCE.hitCount();
               float centerX = event.getGuiGraphicsExtractor().guiWidth() / 2.0F;
               float centerY = event.getGuiGraphicsExtractor().guiHeight() / 2.0F;
               float unit = ScaleUtil.toGuiPixels(1.0F, mc.getWindow().getGuiScale());
               MsdfFont font = UiFonts.sfProDisplay();
               float textSize = 8.0F * unit;
               float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
               float textWidth = font.measureWidth(label, textSize, letterSpacing);
               float dotSize = learning ? 4.0F * unit : 0.0F;
               float dotGap = learning ? 4.0F * unit : 0.0F;
               float paddingX = 5.0F * unit;
               float paddingY = 3.0F * unit;
               float width = textWidth + dotSize + dotGap + paddingX * 2.0F;
               float height = textSize + paddingY * 2.0F;
               float left = centerX - width * 0.5F;
               float top = centerY - 26.0F * unit - height;
               Render2DUtil.rect(left, top, width, height).color(ColorUtil.rgba(0, 0, 0, 140)).radius(height * 0.35F).draw();
               float contentLeft = left + paddingX;
               if (learning) {
                  float pulse = 0.45F + 0.55F * (float)Math.abs(Math.sin(System.currentTimeMillis() / 400.0));
                  Render2DUtil.rect(contentLeft, top + (height - dotSize) * 0.5F, dotSize, dotSize)
                     .color(ColorUtil.multiplyAlpha(-49088, pulse))
                     .radius(dotSize * 0.5F)
                     .draw();
                  contentLeft += dotSize + dotGap;
               }

               Render2DUtil.text(contentLeft, font.centeredTextY(top + height * 0.5F, textSize), textSize, label)
                  .style(UiFontStyle.MEDIUM)
                  .color(learning ? -1 : 0xFF000000 | Theme.getAccent())
                  .draw();
            }
         }
      }
   }

   private void drawLegitSnapFov(Render2DEvent event) {
      if (this.displayFov.getValue() && this.componentMode.is("Legit Snap")) {
         Minecraft mc = event.getClient();
         if (mc.player != null && mc.level != null && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            if (!mc.getDebugOverlay().showDebugScreen()) {
               float centerX = event.getGuiGraphicsExtractor().guiWidth() / 2.0F;
               float centerY = event.getGuiGraphicsExtractor().guiHeight() / 2.0F;
               float halfHeight = event.getGuiGraphicsExtractor().guiHeight() / 2.0F;
               double coneDegrees = Math.min(this.legitSnapFov.getValue(), 89.0);
               double halfFovDegrees = Math.max(1.0, ((Integer)mc.options.fov().get()).intValue() * 0.5);
               float radius = (float)(halfHeight * (Math.tan(Math.toRadians(coneDegrees)) / Math.tan(Math.toRadians(halfFovDegrees))));
               float maxRadius = (float)Math.hypot(event.getGuiGraphicsExtractor().guiWidth(), event.getGuiGraphicsExtractor().guiHeight()) * 0.5F;
               if (Float.isFinite(radius) && !(radius < 1.0F)) {
                  radius = Math.min(radius, maxRadius);
                  int color;
                  float thickness;
                  if (this.legitSnapRotation.isEngaged()) {
                     color = 0xFF000000 | Theme.getAccent();
                     thickness = 1.0F;
                  } else if (target != null && this.legitSnapRotation.isTargetInFov()) {
                     color = ColorUtil.rgba(255, 255, 255, 220);
                     thickness = 0.8F;
                  } else {
                     color = ColorUtil.rgba(255, 255, 255, 90);
                     thickness = 0.6F;
                  }

                  this.drawFovRing(event, centerX, centerY, radius, thickness, color);
               }
            }
         }
      }
   }

   private void drawFovRing(Render2DEvent event, float centerX, float centerY, float radius, float thickness, int color) {
      int segments = Mth.clamp((int)Math.ceil(radius * 0.6F), 48, 192);

      for (int index = 0; index < segments; index++) {
         double firstAngle = (Math.PI * 2) * index / segments;
         double secondAngle = (Math.PI * 2) * (index + 1) / segments;
         float x1 = centerX + (float)Math.cos(firstAngle) * radius;
         float y1 = centerY + (float)Math.sin(firstAngle) * radius;
         float x2 = centerX + (float)Math.cos(secondAngle) * radius;
         float y2 = centerY + (float)Math.sin(secondAngle) * radius;
         float dx = x2 - x1;
         float dy = y2 - y1;
         float segmentLength = (float)Math.sqrt(dx * dx + dy * dy);
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().pose();
         pose.pushMatrix();
         pose.translate(x1, y1);
         pose.rotate((float)Math.atan2(dy, dx));
         Render2DUtil.rect(0.0F, -thickness * 0.5F, segmentLength, thickness).color(color).draw();
         pose.popMatrix();
      }
   }

   private void resetRotationState() {
      this.reallyworldRotation.reset();
      this.grimRotation.reset();
      this.snapRotation.reset();
      this.funtimeRotation.reset();
      this.funtimeDick.reset();
      this.polarRotation.reset();
      this.spookyRotation.reset();
      this.holyWorldRotation.reset();
      this.bmwRotation.reset();
      this.legitSnapRotation.reset();
      this.funfickRotation.reset();
      this.spaceTimeRotation.reset();
      this.furryRotation.reset();
      this.neuroRotation.reset();
      this.testRotation.reset();
      this.shardRotation.reset();
      this.ftNewRotation.reset();
      this.funTestRotation.reset();
      this.customRotation.reset();
   }

   private AuraRotation currentRotation() {
      return switch ((String)this.componentMode.getValue()) {
         case "Grim" -> this.grimRotation;
         case "Snap" -> this.snapRotation;
         case "Funtime" -> this.funtimeRotation;
         case "FuntimeDick" -> this.funtimeDick;
         case "Polar" -> this.polarRotation;
         case "Spooky" -> this.spookyRotation;
         case "HolyWorld" -> this.holyWorldRotation;
         case "BMW" -> this.bmwRotation;
         case "Legit Snap" -> this.legitSnapRotation;
         case "Funfick" -> this.funfickRotation;
         case "SpaceTime" -> this.spaceTimeRotation;
         case "Sloth" -> this.slothRotation;
         case "Furry" -> this.furryRotation;
         case "Neuro" -> this.neuroRotation;
         case "Test" -> this.testRotation;
         case "FT-New" -> this.ftNewRotation;
         case "FunTest" -> this.funTestRotation;
         case "Custom" -> this.customRotation;
         case "Shard" -> this.shardRotation;
         default -> this.reallyworldRotation;
      };
   }

   private void updateRotation() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && target != null) {
         boolean attackLikely = this.canAttack();
         Vec3 targetEyePos = this.getEffectiveTargetEyePos(player, target, attackLikely);
         AuraRotation rotation = this.currentRotation();
         boolean ownsRotation = RotationContext.claim(this, 10);
         if (rotation == this.legitSnapRotation) {
            this.configureLegitSnapRotation();
         } else if (rotation == this.funfickRotation) {
            this.configureFunfickRotation();
         } else if (rotation == this.spaceTimeRotation) {
            this.configureSpaceTimeRotation();
         } else if (rotation == this.funtimeRotation) {
            this.configureFuntimeRotation();
         } else if (rotation == this.funtimeDick) {
            this.configureFuntimeDick();
         } else if (rotation == this.polarRotation) {
            this.configurePolarRotation();
         } else if (rotation == this.slothRotation) {
            this.configureSlothRotation();
         } else if (rotation == this.furryRotation) {
            this.configureFurryRotation();
         } else if (rotation == this.neuroRotation) {
            this.configureNeuroRotation();
         } else if (rotation == this.ftNewRotation) {
            this.configureFTNewRotation();
         } else if (rotation == this.funTestRotation) {
            this.configureFunTestRotation();
         } else if (rotation == this.customRotation) {
            this.configureCustomRotation();
         } else if (rotation == this.spookyRotation) {
            this.configureSpookyRotation();
         } else if (rotation == this.shardRotation) {
            this.configureShardRotation();
         }

         if (ownsRotation) {
            rotation.tick(player, target, targetEyePos, attackLikely);
         }
      }
   }

   private void configureLegitSnapRotation() {
      this.legitSnapRotation.setFovAngle(this.legitSnapFov.getValue().floatValue());
      this.legitSnapRotation.setMode(this.legitSnapProcessor.getValue());
      this.legitSnapRotation.setReach(this.attackDistance());
      this.legitSnapRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
      this.legitSnapRotation.setOnlyCriticalHit(this.combatSettings.isSelected("Only Criticals"));
      this.legitSnapRotation.setCritsWithSpace(this.legitSnapCritsWithSpace.getValue());
   }

   private void configureFunfickRotation() {
      this.funfickRotation.setReach(this.attackDistance());
      this.funfickRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
      this.funfickRotation.setOnlyCriticalHit(this.combatSettings.isSelected("Only Criticals"));
      this.funfickRotation.setCritsWithSpace(this.legitSnapCritsWithSpace.getValue());
   }

   private void configureSpaceTimeRotation() {
      this.spaceTimeRotation.setReach(this.attackDistance());
      this.spaceTimeRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configureFuntimeRotation() {
      this.funtimeRotation.setSnapTicks(this.funtimeSnapTicks.getValue().intValue());
      this.funtimeRotation.setSmoothness((float)(this.funtimeSmoothness.getValue() / 100.0));
      this.funtimeRotation.setOffsetDegrees(this.funtimeOffset.getValue().floatValue());
      this.funtimeRotation.setMultipoint(this.funtimeMultipoint.getValue().intValue());
      this.funtimeRotation.setReach(this.attackDistance());
      this.funtimeRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configureFuntimeDick() {
      double min = this.dickMinClosure.getValue();
      double max = Math.max(min, this.dickMaxClosure.getValue());
      this.funtimeDick.setMinClosure((float)(min / 100.0));
      this.funtimeDick.setMaxClosure((float)(max / 100.0));
      this.funtimeDick.setSpread((float)(this.dickSpread.getValue() / 100.0));
      this.funtimeDick.setHoldTicks(this.dickHoldTicks.getValue().intValue());
      this.funtimeDick.setReach(this.attackDistance());
      this.funtimeDick.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configurePolarRotation() {
      this.polarRotation.setTolerance(this.polarTolerance.getValue().floatValue());
      this.polarRotation.setMaxYawStep(this.polarYawStep.getValue().floatValue());
      this.polarRotation.setMaxPitchStep(this.polarPitchStep.getValue().floatValue());
      this.polarRotation.setAimHeight((float)(this.polarAimHeight.getValue() / 100.0));
   }

   private void configureFurryRotation() {
      this.furryRotation.setSpin(!this.furryMode.is("Jitter"));
      this.furryRotation.setJitter(!this.furryMode.is("Spin 360"));
      this.furryRotation.setSpinTicks(this.furrySpinTicks.getValue().intValue());
      this.furryRotation.setJitterStrength((float)(this.furryJitter.getValue() / 100.0));
      this.furryRotation.setReach(this.attackDistance());
      this.furryRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configureSpookyRotation() {
      this.spookyRotation.setReach(this.attackDistance());
      this.spookyRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configureShardRotation() {
      this.shardRotation.setMaxYawStep(this.shardYawStep.getValue().floatValue());
      this.shardRotation.setMaxPitchStep(this.shardPitchStep.getValue().floatValue());
      this.shardRotation.setReach(this.attackDistance());
      this.shardRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configureNeuroRotation() {
      this.neuroDataset.updateModes(NeuroDataset.INSTANCE.names());
      this.applyNeuroDataset();
      this.neuroRotation.setLearning(this.neuroLearning.getValue());
      this.neuroRotation.setReplay(this.neuroPlayback.is("Replay"));
      this.neuroRotation.setNeighbours(this.neuroNeighbours.getValue().intValue());
      this.neuroRotation.setVariation((float)(this.neuroVariation.getValue() / 100.0));
      this.neuroRotation.setAssist(this.neuroAssist.getValue());
      this.neuroRotation.setAssistLimit((float)(this.neuroAssistLimit.getValue() / 100.0));
      this.neuroRotation.setReach(this.attackDistance());
      this.neuroRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
      this.neuroRotation.setDuelOnly(this.neuroDuelOnly.getValue());
      this.neuroRotation.setDuelBias((float)(this.neuroDuelBias.getValue() / 100.0));
      this.neuroRotation.setReplayHitsOnly(this.neuroReplayHits.getValue());
   }

   private void configureFTNewRotation() {
      this.ftNewRotation.setReach(this.attackDistance());
      this.ftNewRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configureFunTestRotation() {
      this.funTestRotation.setReach(this.attackDistance());
      this.funTestRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
   }

   private void configureCustomRotation() {
      this.customRotation.setReach(this.attackDistance());
      this.customRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
      this.customRotation.loadPreset("Balanced");
   }

   private void configureSlothRotation() {
      this.slothRotation.setReach(this.attackDistance());
      this.slothRotation.setThroughWalls(this.combatSettings.isSelected("Through Walls"));
      Minecraft mc = Minecraft.getInstance();
      boolean waterSprint = mc.player != null && mc.player.isSprinting() && mc.player.isInWater();
      this.slothRotation.setWaterSprintActive(waterSprint);
   }

   private void applyNeuroDataset() {
      String wanted = this.neuroDataset.getValue();
      if (!wanted.equals(NeuroDataset.INSTANCE.activeName()) && !wanted.equals(this.requestedNeuroDataset)) {
         this.requestedNeuroDataset = wanted;
         NeuroDataset.INSTANCE.saveIfDirty();
         if (!NeuroDataset.INSTANCE.load(wanted)) {
            ChatUtil.error("Neuro dataset not found  •  " + wanted);
         }
      }
   }

   public void selectNeuroDataset(String name) {
      this.neuroDataset.updateModes(NeuroDataset.INSTANCE.names());
      if (this.neuroDataset.containsMode(name)) {
         this.neuroDataset.setValue(name);
      }

      this.requestedNeuroDataset = name;
   }

   public NeuroRotation neuroRotation() {
      return this.neuroRotation;
   }

   private Vec3 realPredict(LivingEntity target) {
      if (!this.predictElytra.getValue()) {
         return target.getEyePosition();
      }

      if (Math.hypot(target.getX() - target.xOld, target.getZ() - target.zOld) * 20.0 == 0.0 && (target.getY() - target.yOld) * 20.0 == 0.0) {
         return target.getEyePosition();
      }

      float ticks = this.predictMode.is("Легитный") ? 2.0F : 3.0F;
      return PredictUtils.predict(target, target.position(), ticks);
   }

   private Vec3 getEffectiveTargetEyePos(LocalPlayer player, LivingEntity target, boolean attackLikely) {
      ElytraResolverFeature resolver = ElytraResolverFeature.getEnabled();
      if (resolver != null && resolver.isDriving(target)) {
         return resolver.getAimPoint();
      } else {
         return player.isFallFlying() && target.isFallFlying() && this.predictElytra.getValue() ? this.realPredict(target) : target.getEyePosition();
      }
   }

   private boolean legitSnapAllowsHit(LocalPlayer player) {
      if (!this.componentMode.is("Legit Snap")) {
         return true;
      } else {
         return target != null && player.isFallFlying() && target.isFallFlying() ? true : this.legitSnapRotation.isEngaged() && this.rayTrace();
      }
   }

   private void updateAttack() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && target != null && mc.gameMode != null) {
         if (!this.checkReturn(player)) {
            boolean canStartSprint = player.input.keyPresses.forward()
               && !player.hasEffect(MobEffects.BLINDNESS)
               && !player.isFallFlying()
               && !player.isUsingItem()
               && !player.horizontalCollision
               && player.getFoodData().getFoodLevel() > 6
               && !player.isShiftKeyDown();
            boolean sprintStop = false;
            if (this.combatSettings.isSelected("Unblock Shield") && player.isBlocking()) {
               mc.gameMode.releaseUsingItem(player);
            }

            if (player.isFallFlying()) {
               if (player.isSprinting()) {
                  player.setSprinting(false);
               }
            } else if (this.sprintMode.is("Легитный")) {
               if (player.isSprinting()) {
                  player.setSprinting(false);
                  SprintManager.markAttackImminent();
                  return;
               }
            } else if (this.sprintMode.is("Обычный") && player.isSprinting()) {
               if (player.connection != null) {
                  player.connection.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
               }

               player.setSprinting(false);
               sprintStop = true;
            }

            if (this.bypassWalls.getValue() && this.combatSettings.isSelected("Through Walls") && !this.canAttackThroughWalls(target)) {
               this.punchThroughWalls(player);
            }

            this.cpsLimit = System.currentTimeMillis() + (long)this.attackDelay.getValue().doubleValue();
            if (target instanceof Player targetPlayer && this.combatSettings.isSelected("Break Shield")) {
               this.breakShieldPlayer(targetPlayer);
            }

            if (!this.smashWithMace(target)) {
               this.attackEntity(target);
            }

            this.notifyRotationAttack();
            SprintManager.reset();
            SprintFeature sprintFeature = SprintFeature.getEnabled();
            if (sprintFeature != null && sprintFeature.keepSprint.getValue()) {
               player.setSprinting(true);
            }

            if (this.sprintMode.is("Обычный") && sprintStop && canStartSprint) {
               if (player.connection != null) {
                  player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
               }

               player.setSprinting(true);
            }
         }
      }
   }

   private void punchThroughWalls(LocalPlayer player) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && target != null) {
         Vec3 startVec = player.getEyePosition();
         Vec3 targetPos = target.getEyePosition();
         Vec3 direction = targetPos.subtract(startVec);
         double distance = direction.length();
         if (!(distance < 0.001)) {
            Vec3 normalizedDir = direction.normalize();

            for (double i = 0.0; i < distance; i += 0.5) {
               Vec3 point = startVec.add(normalizedDir.scale(i));
               BlockPos pos = BlockPos.containing(point);
               if (!mc.level.getBlockState(pos).isAir() && player.connection != null) {
                  player.connection
                     .send(
                        new ServerboundPlayerActionPacket(
                           net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP
                        )
                     );
                  player.connection
                     .send(
                        new ServerboundPlayerActionPacket(
                           net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP
                        )
                     );
               }
            }
         }
      }
   }

   private boolean breakShieldPlayer(Player entity) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.gameMode == null || player.connection == null) {
         return false;
      }

      if (!isRaisingShield(entity)) {
         return false;
      }

      int selectedSlot = player.getInventory().getSelectedSlot();
      if (isAxe(player.getInventory().getItem(selectedSlot))) {
         return false;
      }

      int axeHotbarSlot = this.findHotbarAxe(player);
      int swappedMenuSlot = -1;
      if (axeHotbarSlot == -1) {
         int menuSlot = InventoryUtil.findInventorySlot(player, AuraFeature::isAxe);
         if (menuSlot == -1 || !InventoryUtil.swapWithHotbar(menuSlot, selectedSlot)) {
            return false;
         }

         swappedMenuSlot = menuSlot;
         axeHotbarSlot = selectedSlot;
      }

      this.selectHotbarSlot(mc, player, axeHotbarSlot);
      this.attackEntity(entity);
      this.selectHotbarSlot(mc, player, selectedSlot);
      if (swappedMenuSlot != -1) {
         InventoryUtil.swapWithHotbar(swappedMenuSlot, selectedSlot);
      }

      return true;
   }

   private boolean smashWithMace(Entity entity) {
      if (!this.maceSwap.getValue()) {
         return false;
      }

      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.gameMode == null || player.connection == null) {
         return false;
      }

      if (!MaceItem.canSmashAttack(player)) {
         return false;
      }

      if (player.getY() - entity.getY() < this.maceHeight.getValue()) {
         return false;
      }

      int selectedSlot = player.getInventory().getSelectedSlot();
      if (isMace(player.getInventory().getItem(selectedSlot))) {
         this.attackEntity(entity);
         return true;
      }

      int maceHotbarSlot = this.findHotbarMace(player);
      int swappedMenuSlot = -1;
      if (maceHotbarSlot == -1) {
         int menuSlot = InventoryUtil.findInventorySlot(player, AuraFeature::isMace);
         if (menuSlot == -1 || !InventoryUtil.swapWithHotbar(menuSlot, selectedSlot)) {
            return false;
         }

         swappedMenuSlot = menuSlot;
         maceHotbarSlot = selectedSlot;
      }

      this.selectHotbarSlot(mc, player, maceHotbarSlot);
      this.attackEntity(entity);
      this.selectHotbarSlot(mc, player, selectedSlot);
      if (swappedMenuSlot != -1) {
         InventoryUtil.swapWithHotbar(swappedMenuSlot, selectedSlot);
      }

      return true;
   }

   private int findHotbarMace(LocalPlayer player) {
      for (int slot = 0; slot < 9; slot++) {
         if (isMace(player.getInventory().getItem(slot))) {
            return slot;
         }
      }

      return -1;
   }

   private static boolean isMace(ItemStack stack) {
      return !stack.isEmpty() && stack.is(Items.MACE);
   }

   private void selectHotbarSlot(Minecraft mc, LocalPlayer player, int slot) {
      player.getInventory().setSelectedSlot(slot);
      player.connection.send(new ServerboundSetCarriedItemPacket(slot));
      if (mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
         accessor.setCarriedIndex(slot);
      }
   }

   private static boolean isRaisingShield(Player entity) {
      return entity.isBlocking() || entity.isUsingItem() && entity.getUseItem().is(Items.SHIELD);
   }

   private int findHotbarAxe(LocalPlayer player) {
      for (int slot = 0; slot < 9; slot++) {
         if (isAxe(player.getInventory().getItem(slot))) {
            return slot;
         }
      }

      return -1;
   }

   private static boolean isAxe(ItemStack stack) {
      return !stack.isEmpty() && stack.is(ItemTags.AXES);
   }

   private boolean checkReturn(LocalPlayer player) {
      return this.combatSettings.isSelected("Don't Hit While Eating") && player.isUsingItem() && !player.isBlocking();
   }

   private void notifyRotationAttack() {
      AuraRotation rotation = this.currentRotation();
      if (rotation == this.furryRotation) {
         this.configureFurryRotation();
      } else if (rotation == this.neuroRotation) {
         this.configureNeuroRotation();
      }

      rotation.onAttack();
   }

   private void attackEntity(Entity entity) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.gameMode != null) {
         AirStuckFeature airStuck = FeatureManager.INSTANCE.getEnabled(AirStuckFeature.class);
         if (airStuck != null) {
            airStuck.pauseForAttack();
         }

         this.ensureCriticalWhileGliding(player);
         mc.gameMode.attack(player, entity);
         player.swing(InteractionHand.MAIN_HAND);
         ElytraResolverFeature resolver = ElytraResolverFeature.getEnabled();
         if (resolver != null && player.isFallFlying() && entity instanceof LivingEntity living) {
            resolver.onHit(living);
         }

         this.strikeLightningTargetEsp(player, entity);
         if (this.usesSoulsTargetEsp()) {
            SoulImpulse.hit(entity);
         }
      }
   }

   private void ensureCriticalWhileGliding(LocalPlayer player) {
      if (this.packetCrits.getValue()) {
         if (player.isFallFlying() && player.connection != null) {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            player.connection.send(new Pos(x, y + 0.0625, z, false, false));
            player.connection.send(new Pos(x, y, z, false, false));
         }
      }
   }

   private void strikeLightningTargetEsp(LocalPlayer player, Entity entity) {
      if (this.usesLightningTargetEsp()) {
         double bbHeight = entity instanceof LivingEntity living ? living.getBbHeight() : 1.8;
         Vec3 impact = entity.position().add(0.0, bbHeight * 0.5, 0.0);
         StormState.INSTANCE.strikeAt(impact, player.distanceTo(entity));
      }
   }

   public boolean canAttack() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && target != null && !this.checkReturn(player)) {
         double distance = this.getStrictDistance(target);
         boolean elytraBoth = player.isFallFlying() && target.isFallFlying();
         ElytraTargetFeature elytraFeature = ElytraTargetFeature.getEnabled();
         boolean flyboxPassed = elytraFeature != null && elytraFeature.allowsAttack(player, target);
         boolean hitboxOverlap = elytraBoth && player.getBoundingBox().inflate(0.153).intersects(target.getBoundingBox().inflate(0.306));
         if (elytraBoth && this.predictElytra.getValue()) {
            double hitboxDist = player.getEyePosition().distanceTo(MultipointUtil.getNearestPoint(target, 0.0));
            double elytraReach = Math.max(this.attackDistance(), 3.5) + 0.306;
            if (hitboxDist > elytraReach && !flyboxPassed && !hitboxOverlap) {
               return false;
            }
         } else if (player.isFallFlying()) {
            double hitboxDist = player.getEyePosition().distanceTo(MultipointUtil.getNearestPoint(target, 0.0));
            if (hitboxDist > 3.0) {
               return false;
            }
         } else if (distance >= this.attackDistance()) {
            return false;
         }

         boolean elytraOvertake = elytraBoth;
         boolean rayOptional = flyboxPassed || hitboxOverlap;
         if (!elytraOvertake && this.componentMode.is("BMW") && !this.isTargetInView(player, target)) {
            return false;
         }

         if (this.componentMode.is("Furry")) {
            if (!elytraOvertake && this.furryRotation.isSpinning()) {
               return false;
            }

            if (!rayOptional && !this.rayTrace()) {
               return false;
            }
         }

         if (this.componentMode.is("Polar")) {
            if (!elytraOvertake && !this.polarRotation.isOnTarget(player)) {
               return false;
            }

            if (!rayOptional && !this.rayTrace()) {
               return false;
            }
         }

         if (this.componentMode.is("Spooky")) {
            if (!elytraOvertake && !this.spookyRotation.shouldStrike(player, target)) {
               return false;
            }

            if (!rayOptional && !this.rayTrace()) {
               return false;
            }
         }

         if (this.componentMode.is("Shard")) {
            if (!elytraOvertake && !this.shardRotation.shouldStrike(player, target)) {
               return false;
            }

            if (!rayOptional && !this.rayTrace()) {
               return false;
            }
         }

         if (this.componentMode.is("Funtime") && !rayOptional && !this.rayTrace()) {
            return false;
         }

         if (this.componentMode.is("Funfick") && !rayOptional && !this.rayTrace()) {
            return false;
         }

         if (this.componentMode.is("Neuro") && !rayOptional && !this.rayTrace()) {
            return false;
         }

         if (this.cpsLimit > System.currentTimeMillis()) {
            return false;
         }

         float swing = player.getAttackStrengthScale(0.5F);
         boolean longReach = !elytraOvertake && distance >= this.noPetRange.getValue();
         boolean turbo = this.combatSettings.isSelected("Turbo Criticals");
         float minSwing = longReach ? 0.99F : (player.isFallFlying() ? 0.902F : (turbo ? 0.905F : 0.92F));
         boolean swingReady = swing >= minSwing;
         if (mc.level != null && AuraUtil.isJumpBlockedByCeiling(player, mc.level)) {
            return swingReady;
         }

         if (!swingReady) {
            return false;
         }

         boolean onlyCrits = this.combatSettings.isSelected("Only Criticals") || this.combatSettings.isSelected("Turbo Criticals");
         if (!onlyCrits) {
            return true;
         }

         if (player.isFallFlying()) {
            return true;
         }

         if (mc.level == null || !AuraUtil.isValidAttackCondition(player, mc.level)) {
            return true;
         }

         if (this.combatSettings.isSelected("Turbo Criticals")) {
            if (player.fallDistance > 0.0 && !player.onGround()) {
               return true;
            } else {
               return !longReach && this.combatSettings.isSelected("Smart Criticals") && !mc.options.keyJump.isDown() && player.onGround()
                  ? true
                  : player.isFallFlying();
            }
         } else if (this.combatSettings.isSelected("Smart Criticals") && player.fallDistance <= 0.0 && !mc.options.keyJump.isDown() && player.onGround()) {
            return !longReach;
         } else {
            return player.isFallFlying() ? true : AuraUtil.canCritical(player);
         }
      } else {
         return false;
      }
   }

   private boolean isTargetInView(LocalPlayer player, LivingEntity target) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 look = player.getViewVector(1.0F).normalize();
      Vec3 toTarget = target.getEyePosition().subtract(player.getEyePosition());
      if (toTarget.lengthSqr() < 1.0E-6) {
         return true;
      }

      toTarget = toTarget.normalize();
      double dot = Math.max(-1.0, Math.min(1.0, look.dot(toTarget)));
      double angle = Math.toDegrees(Math.acos(dot));
      double verticalFov = ((Integer)mc.options.fov().get()).intValue();
      int width = mc.getWindow().getWidth();
      int height = mc.getWindow().getHeight();
      double aspect = height > 0 ? (double)width / height : 1.7777777777777777;
      double horizontalFov = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(verticalFov) * 0.5) * aspect));
      return angle <= horizontalFov * 0.5;
   }

   private double fovAngleTo(LocalPlayer player, LivingEntity entity) {
      float yaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
      float pitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
      Vec3 look = Vec3.directionFromRotation(pitch, yaw).normalize();
      Vec3 toTarget = entity.getEyePosition().subtract(player.getEyePosition());
      if (toTarget.lengthSqr() < 1.0E-6) {
         return 0.0;
      }

      double dot = Math.max(-1.0, Math.min(1.0, look.dot(toTarget.normalize())));
      return Math.toDegrees(Math.acos(dot));
   }

   private LivingEntity findTarget() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         List<LivingEntity> potentialTargets = new ArrayList<>();

         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && this.isValidTarget(living)) {
               potentialTargets.add(living);
            }
         }

         if (potentialTargets.isEmpty()) {
            return null;
         }

         if (potentialTargets.size() > 1) {
            switch ((String)this.sortMode.getValue()) {
               case "По здоровью":
                  potentialTargets.sort(Comparator.comparingDouble(LivingEntity::getHealth));
                  break;
               case "По дистанции":
                  potentialTargets.sort(Comparator.comparingDouble(player::distanceTo));
                  break;
               case "По броне":
                  potentialTargets.sort(Comparator.comparingDouble(entityx -> entityx instanceof Player p ? this.getEntityArmor(p) : entityx.getArmorValue()));
                  break;
               case "По FOV":
                  potentialTargets.sort(Comparator.comparingDouble(entityx -> this.fovAngleTo(player, entityx)));
            }

            if (player.isFallFlying()) {
               potentialTargets.sort(Comparator.comparing(entityx -> {
                  ItemStack chest = entityx.getItemBySlot(EquipmentSlot.CHEST);
                  return !chest.is(Items.ELYTRA);
               }));
            }
         }

         return potentialTargets.get(0);
      } else {
         return null;
      }
   }

   private boolean isValidTarget(LivingEntity entity) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null || entity == player) {
         return false;
      }

      if (entity.tickCount < 3) {
         return false;
      }

      double stickyBonus = entity == target ? 0.75 : 0.0;
      if (this.getStrictDistance(entity) >= this.getMaxAimRange() + stickyBonus) {
         return false;
      }

      if (AntiBotFeature.checkBot(entity)) {
         return false;
      }

      if (entity instanceof ArmorStand) {
         return false;
      }

      if (entity.isInvulnerable() || !entity.isAlive()) {
         return false;
      }

      if (entity instanceof Player playerEntity) {
         if (!this.targets.isSelected("Игроки")) {
            return false;
         } else if (playerEntity.getGameProfile().name().equalsIgnoreCase(player.getGameProfile().name())) {
            return false;
         } else if (!this.targets.isSelected("Друзья") && FriendManager.INSTANCE.isFriend(playerEntity.getGameProfile().name())) {
            return false;
         } else {
            boolean naked = playerEntity.getArmorValue() == 0;
            boolean invisible = playerEntity.isInvisible();
            if (invisible && naked) {
               return this.targets.isSelected("Голые невидимки");
            } else if (invisible) {
               return this.targets.isSelected("Невидимки");
            } else {
               return naked ? this.targets.isSelected("Голые") : true;
            }
         }
      } else {
         return !(entity instanceof Monster)
               && !(entity instanceof Phantom)
               && !(entity instanceof Bat)
               && !(entity instanceof Shulker)
               && !(entity instanceof Villager)
               && !(entity instanceof Animal)
               && !(entity instanceof AmbientCreature)
            ? true
            : this.targets.isSelected("Мобы");
      }
   }

   public double getEntityArmor(Player targetPlayer) {
      return targetPlayer.getArmorValue();
   }

   private boolean canAttackThroughWalls(LivingEntity entity) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && entity != null) {
         Vec3 targetVec = entity.position().add(0.0, entity.getEyeHeight(), 0.0);
         Vec3 playerVec = player.getEyePosition();
         BlockHitResult result = mc.level.clip(new ClipContext(playerVec, targetVec, Block.COLLIDER, Fluid.NONE, player));
         return result.getType() == Type.MISS;
      } else {
         return false;
      }
   }

   public boolean rayTrace() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && target != null) {
         Vec3 eye = player.getEyePosition();
         float serverYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
         float serverPitch = RotationContext.isActive() ? RotationContext.getServerPitch() : player.getXRot();
         Vec3 look = Vec3.directionFromRotation(serverPitch, serverYaw);
         Vec3 end = eye.add(look.scale(this.attackDistance()));
         boolean elytraRay = player.isFallFlying() && target.isFallFlying();
         double rayMargin = elytraRay ? 0.45899999999999996 : 0.153;
         AABB box = target.getBoundingBox().inflate(rayMargin);
         if (!box.contains(eye) && !box.clip(eye, end).isPresent()) {
            AABB sweep = null;
            if (elytraRay && this.predictElytra.getValue()) {
               AABB current = target.getBoundingBox();
               Vec3 predictPos = this.realPredict(target);
               AABB predicted = current.move(predictPos.subtract(target.position()));
               Vec3 motion = target.getDeltaMovement();
               if (motion.lengthSqr() > 1.0E-4) {
                  double lead = 3.0;
                  ElytraTargetFeature etf = ElytraTargetFeature.getEnabled();
                  if (etf != null) {
                     lead = etf.predictPower.getValue();
                  }

                  AABB trailing = current.move(motion.normalize().scale(-Math.max(1.0, lead)));
                  AABB combined = current.minmax(predicted).minmax(trailing);
                  sweep = combined;
               } else {
                  sweep = current.minmax(predicted);
               }
            }

            if (sweep != null) {
               AABB swept = sweep.inflate(rayMargin);
               double sweepReach = Math.sqrt(swept.distanceToSqr(eye)) + swept.getSize() + this.attackDistance();
               Vec3 sweptEnd = eye.add(look.scale(sweepReach));
               if (swept.contains(eye) || swept.clip(eye, sweptEnd).isPresent()) {
                  return true;
               }

               if (elytraRay) {
                  AABB inflated = target.getBoundingBox().inflate(rayMargin * 2.0);
                  Optional<Vec3> clip = inflated.clip(eye, eye.add(look.scale(this.attackDistance() + rayMargin * 2.0)));
                  if (clip.isPresent()) {
                     return true;
                  }

                  if (inflated.contains(eye)) {
                     return true;
                  }
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public double getMaxAimRange() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && player.isFallFlying() && this.predictElytra.getValue()
         ? Math.max(this.attackDistance() + this.rotateDistance.getValue(), 60.0)
         : this.attackDistance() + this.rotateDistance.getValue();
   }

   public double attackDistance() {
      double base = this.attackRange.getValue();
      AirStuckFeature airStuck = FeatureManager.INSTANCE.getEnabled(AirStuckFeature.class);
      return airStuck != null && airStuck.isExtendedKillAuraRange() ? Math.max(base, airStuck.getExtendedKillAuraRange()) : base;
   }

   public double getStrictDistance(Entity entity) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return 0.0;
      } else {
         return entity instanceof LivingEntity living ? AuraUtil.getDistance(player, living) : player.distanceTo(entity);
      }
   }

   private void reset() {
      target = null;
      this.predictedElytraPos = null;
      this.elytraTarget = null;
      this.resetRotationState();
      RotationContext.release(this);
      RotationContext.disengageIfOwner(this);
      this.cpsLimit = System.currentTimeMillis();
   }

   private static float wrapDegrees(float angle) {
      float wrapped = angle % 360.0F;
      if (wrapped >= 180.0F) {
         wrapped -= 360.0F;
      }

      if (wrapped < -180.0F) {
         wrapped += 360.0F;
      }

      return wrapped;
   }

   public static double direction(float rotationYaw, double moveForward, double moveStrafing) {
      if (moveForward < 0.0) {
         rotationYaw += 180.0F;
      }

      float forward = 1.0F;
      if (moveForward < 0.0) {
         forward = -0.5F;
      } else if (moveForward > 0.0) {
         forward = 0.5F;
      }

      if (moveStrafing > 0.0) {
         rotationYaw -= 90.0F * forward;
      }

      if (moveStrafing < 0.0) {
         rotationYaw += 90.0F * forward;
      }

      return Math.toRadians(rotationYaw);
   }

   private void fixMovement(PlayerInputEvent event, float intentYaw) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Vec2 moveVec = event.getMoveVector();
         float forward = moveVec.y;
         float strafe = moveVec.x;
         if (forward != 0.0F || strafe != 0.0F) {
            double angle = wrapDegrees((float)Math.toDegrees(direction(intentYaw, forward, strafe)));
            float serverYaw = RotationContext.isActive() ? RotationContext.getServerYaw() : player.getYRot();
            float closestForward = 0.0F;
            float closestStrafe = 0.0F;
            double closestDifference = Double.MAX_VALUE;

            for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward++) {
               for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe++) {
                  if (predictedStrafe != 0.0F || predictedForward != 0.0F) {
                     double predictedAngle = wrapDegrees((float)Math.toDegrees(direction(serverYaw, predictedForward, predictedStrafe)));
                     double difference = Math.abs(wrapDegrees((float)(angle - predictedAngle)));
                     if (difference < closestDifference) {
                        closestDifference = difference;
                        closestForward = predictedForward;
                        closestStrafe = predictedStrafe;
                     }
                  }
               }
            }

            event.setMoveVector(new Vec2(closestStrafe, closestForward));
            boolean fwd = closestForward > 0.0F;
            boolean bwd = closestForward < 0.0F;
            boolean left = closestStrafe > 0.0F;
            boolean right = closestStrafe < 0.0F;
            Input in = event.getKeyPresses();
            event.setKeyPresses(new Input(fwd, bwd, left, right, in.jump(), in.shift(), in.sprint()));
         }
      }
   }

   private void moveToPosition(PlayerInputEvent event, Vec3 position, float currentYaw) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Vec2 input = event.getMoveVector();
         if (!(input.lengthSquared() <= 1.0E-6F)) {
            double deltaX = position.x - player.getX();
            double deltaZ = position.z - player.getZ();
            double angleToTarget = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;
            angleToTarget = wrapDegrees((float)angleToTarget);
            float bestForward = 0.0F;
            float bestStrafe = 0.0F;
            double minDifference = Double.MAX_VALUE;

            for (float forward = -1.0F; forward <= 1.0F; forward++) {
               for (float strafe = -1.0F; strafe <= 1.0F; strafe++) {
                  if (forward != 0.0F || strafe != 0.0F) {
                     double moveAngle = Math.toDegrees(direction(currentYaw, forward, strafe));
                     double difference = Math.abs(wrapDegrees((float)(angleToTarget - moveAngle)));
                     if (difference < minDifference) {
                        minDifference = difference;
                        bestForward = forward;
                        bestStrafe = strafe;
                     }
                  }
               }
            }

            event.setMoveVector(new Vec2(bestStrafe, bestForward));
            boolean fwd = bestForward > 0.0F;
            boolean bwd = bestForward < 0.0F;
            boolean left = bestStrafe > 0.0F;
            boolean right = bestStrafe < 0.0F;
            Input in = event.getKeyPresses();
            event.setKeyPresses(new Input(fwd, bwd, left, right, in.jump(), in.shift(), in.sprint()));
         }
      }
   }
}
