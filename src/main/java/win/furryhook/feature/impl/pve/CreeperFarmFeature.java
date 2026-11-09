package win.furryhook.feature.impl.pve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.feature.setting.TextSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.PveStateMachine;
import win.furryhook.pve.navigation.BaritoneNavigator;
import win.furryhook.pve.navigation.NavigationOptions;
import win.furryhook.pve.server.ServerAdapters;
import win.furryhook.utils.inventory.ContainerLootService;
import win.furryhook.utils.inventory.InventoryUtil;

public final class CreeperFarmFeature extends PveFeature implements MinecraftContext {
   private static final Pattern MONEY_NUMBER = Pattern.compile(
      "(?iu)(?:\\$|₽)\\s*([0-9][0-9\\s.,]*)([kкmм]?)|([0-9][0-9\\s.,]*)([kкmм]?)\\s*(?:\\$|₽|coins?|монет(?:а|ы|у)?|валют(?:а|ы)?)"
   );
   private static final int ACTION_INTERVAL_TICKS = 4;
   private static final int ATTACK_TIMEOUT_TICKS = 1200;
   private static final int UNLOAD_RETRY_TICKS = 1200;
   public final BooleanSetting unloadGunpowder = this.register(new BooleanSetting("Unload Gunpowder", false));
   public final ModeSetting unloadTarget = this.register(new ModeSetting("Unload Target", "Chest", "Clan", "Ender Chest", "Chest", "None"));
   public final TextSetting farmPosition = this.register(new TextSetting("Farm Position", "auto"));
   public final TextSetting regionMin = this.register(new TextSetting("Region Min", "auto"));
   public final TextSetting regionMax = this.register(new TextSetting("Region Max", "auto"));
   public final TextSetting storagePosition = this.register(new TextSetting("Storage Position", "auto"));
   public final TextSetting clanUnloadCommand = this.register(new TextSetting("Clan Unload Command", ""));
   public final NumberSetting regionRadius = this.register(new NumberSetting("Region Radius", 32.0, 8.0, 128.0, 4.0, " blocks"));
   public final NumberSetting regionHeight = this.register(new NumberSetting("Region Height", 16.0, 4.0, 64.0, 2.0, " blocks"));
   public final NumberSetting chunkStep = this.register(new NumberSetting("Chunk Step", 1.0, 1.0, 4.0, 1.0, " chunks"));
   public final NumberSetting chunkLoadTimeout = this.register(new NumberSetting("Chunk Load Timeout", 60.0, 10.0, 180.0, 5.0, "s"));
   public final NumberSetting unloadAtStacks = this.register(new NumberSetting("Unload At", 16.0, 1.0, 36.0, 1.0, " stacks"));
   public final NumberSetting attackRange = this.register(new NumberSetting("Attack Range", 3.0, 2.0, 4.0, 0.1, " blocks"));
   public final NumberSetting moneyPerGunpowder = this.register(new NumberSetting("Money Per Gunpowder", 0.0, 0.0, 10000.0, 0.1, ""));
   private final PveStateMachine<CreeperFarmFeature.Phase> state = new PveStateMachine<>(CreeperFarmFeature.Phase.APPROACH);
   private final BaritoneNavigator navigator = BaritoneNavigator.INSTANCE;
   private final List<BlockPos> chunkWaypoints = new ArrayList<>();
   private final List<BlockPos> patrolWaypoints = new ArrayList<>();
   private long tick;
   private long lastActionTick;
   private long lastNavigationTick;
   private long lastProgressTick;
   private long unloadBackoffUntil;
   private BlockPos farmCenter;
   private BlockPos farmMin;
   private BlockPos farmMax;
   private BlockPos storage;
   private AABB farmBounds;
   private int chunkWaypointIndex;
   private int patrolWaypointIndex;
   private int previousGunpowder;
   private int gunpowderCollected;
   private int gunpowderUnloaded;
   private int kills;
   private int recoveries;
   private double moneyEarned;
   private Creeper target;
   private boolean targetAttacked;
   private boolean targetWasSwelling;
   private int targetAttacks;
   private boolean navigationActive;
   private boolean openedContainer;
   private boolean storageOpenRequested;
   private boolean unloadCommandSent;
   private CreeperFarmFeature.UnloadStep unloadStep = CreeperFarmFeature.UnloadStep.FIND;
   private float savedYaw;
   private float savedPitch;
   private boolean rotationSaved;

   public CreeperFarmFeature() {
      super(
         "CreeperFarm",
         "Loads a creeper farm, collects drops and unloads gunpowder",
         FeatureCategory.MISC,
         -1,
         AutomationPriority.BOT,
         AutomationResource.MOVEMENT,
         AutomationResource.ROTATION,
         AutomationResource.INVENTORY,
         AutomationResource.SCREEN,
         AutomationResource.CHAT,
         AutomationResource.NAVIGATION,
         AutomationResource.COMBAT
      );
   }

   @Override
   protected void onPveEnable() {
      this.tick = 0L;
      this.lastActionTick = -4611686018427387904L;
      this.lastNavigationTick = -4611686018427387904L;
      this.lastProgressTick = 0L;
      this.unloadBackoffUntil = 0L;
      this.farmCenter = null;
      this.farmMin = null;
      this.farmMax = null;
      this.storage = null;
      this.farmBounds = null;
      this.chunkWaypointIndex = 0;
      this.patrolWaypointIndex = 0;
      this.previousGunpowder = mc.player == null ? 0 : countGunpowder(mc.player);
      this.gunpowderCollected = 0;
      this.gunpowderUnloaded = 0;
      this.kills = 0;
      this.recoveries = 0;
      this.moneyEarned = 0.0;
      this.target = null;
      this.targetAttacked = false;
      this.targetWasSwelling = false;
      this.targetAttacks = 0;
      this.navigationActive = false;
      this.openedContainer = false;
      this.storageOpenRequested = false;
      this.unloadCommandSent = false;
      this.unloadStep = CreeperFarmFeature.UnloadStep.FIND;
      this.rotationSaved = false;
      this.chunkWaypoints.clear();
      this.patrolWaypoints.clear();
      this.state.reset(0L);
      this.beginNavigation();
   }

   @Override
   protected void onPveDisable() {
      LocalPlayer player = mc.player;
      this.closeFeatureContainer(player);
      this.restoreRotation(player);
      this.endNavigation();
      this.clearTarget();
      this.chunkWaypoints.clear();
      this.patrolWaypoints.clear();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelNavigation();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = event.getClient().player;
      ClientLevel level = event.getClient().level;
      if (player != null && level != null && event.getClient().gameMode != null && player.isAlive()) {
         this.tick++;
         this.updateInventoryStats(player);
         this.updateTargetStats();
         switch ((CreeperFarmFeature.Phase)this.state.state()) {
            case APPROACH:
               this.tickApproach(player);
               break;
            case LOADING_CHUNKS:
               this.tickLoadingChunks(player);
               break;
            case LOOTING:
               this.tickLooting(player, level);
               break;
            case UNLOADING:
               this.tickUnloading(player, level);
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && event.getPacket() instanceof ClientboundSystemChatPacket packet) {
         OptionalDouble delta = parseMoneyDelta(packet.content().getString());
         if (delta.isPresent()) {
            this.moneyEarned = this.moneyEarned + delta.getAsDouble();
         }
      }
   }

   public CreeperFarmFeature.Phase getPhase() {
      return this.state.state();
   }

   public BlockPos getRegionMin() {
      return this.farmMin;
   }

   public BlockPos getRegionMax() {
      return this.farmMax;
   }

   public CreeperFarmFeature.FarmStats getStats() {
      double hours = this.tick / 72000.0;
      double incomePerHour = hours <= 0.0 ? 0.0 : this.moneyEarned / hours;
      return new CreeperFarmFeature.FarmStats(
         this.tick, this.moneyEarned, this.gunpowderCollected, this.kills, incomePerHour, this.state.state(), this.recoveries
      );
   }

   public int getGunpowderUnloaded() {
      return this.gunpowderUnloaded;
   }

   private void tickApproach(LocalPlayer player) {
      this.resolveRegion(player);
      if (this.farmBounds != null && this.farmCenter != null) {
         boolean inside = this.farmBounds.contains(player.position());
         boolean timedOut = this.tick - this.lastProgressTick > 1200L;
         CreeperFarmFeature.Phase next = nextPhase(CreeperFarmFeature.Phase.APPROACH, new CreeperFarmFeature.FarmSignals(inside, false, false, false, timedOut));
         if (next == CreeperFarmFeature.Phase.LOADING_CHUNKS) {
            this.transition(next);
         } else if (timedOut) {
            this.recoverFromStall(player);
         } else {
            this.navigateTo(this.farmCenter, 3);
         }
      } else {
         this.recoverFromStall(player);
      }
   }

   private void tickLoadingChunks(LocalPlayer player) {
      if (this.chunkWaypoints.isEmpty()) {
         this.buildWaypoints();
      }

      long timeout = Math.max(1L, Math.round(this.chunkLoadTimeout.getValue() * 20.0));
      boolean timedOut = this.state.ticksInState(this.tick) >= timeout;
      boolean complete = this.chunkWaypointIndex >= this.chunkWaypoints.size();
      CreeperFarmFeature.Phase next = nextPhase(
         CreeperFarmFeature.Phase.LOADING_CHUNKS, new CreeperFarmFeature.FarmSignals(true, complete, false, false, timedOut)
      );
      if (next != CreeperFarmFeature.Phase.LOADING_CHUNKS) {
         this.transition(next);
      } else {
         BlockPos waypoint = this.chunkWaypoints.get(this.chunkWaypointIndex);
         if (at(player, waypoint, 4.0)) {
            this.chunkWaypointIndex++;
            this.markProgress();
            this.cancelNavigation();
         } else {
            this.navigateTo(waypoint, 3);
         }
      }
   }

   private void tickLooting(LocalPlayer player, ClientLevel level) {
      if (this.shouldUnload(player)) {
         this.transition(CreeperFarmFeature.Phase.UNLOADING);
      } else if (this.tick - this.lastProgressTick > 1200L) {
         this.recoverFromStall(player);
      } else {
         ItemEntity powder = this.nearestGunpowderEntity(level, player);
         if (powder != null) {
            this.clearTarget();
            if (powder.distanceToSqr(player) > 2.25) {
               this.navigateTo(powder.blockPosition(), 1);
            } else {
               this.cancelNavigation();
            }
         } else {
            if (!this.validTarget(this.target)) {
               this.target = this.nearestCreeper(level, player);
               this.targetAttacked = false;
               this.targetWasSwelling = false;
               this.targetAttacks = 0;
            }

            if (this.target != null) {
               this.lootCreeper(player);
            } else {
               this.patrol(player);
            }
         }
      }
   }

   private void lootCreeper(LocalPlayer player) {
      if (this.target != null) {
         double distanceSquared = this.target.distanceToSqr(player);
         if (!this.target.isIgnited() && this.target.getSwellDir() <= 0) {
            double range = this.attackRange.getValue();
            if (distanceSquared > range * range) {
               this.navigateTo(this.target.blockPosition(), Math.max(1, (int)Math.floor(range - 1.0)));
            } else {
               this.cancelNavigation();
               if (player.hasLineOfSight(this.target) && !(player.getAttackStrengthScale(0.0F) < 0.95F) && this.actionReady()) {
                  this.lookAt(player, this.target.getEyePosition());
                  mc.gameMode.attack(player, this.target);
                  player.swing(InteractionHand.MAIN_HAND);
                  this.targetAttacked = true;
                  this.targetAttacks++;
                  this.lastActionTick = this.tick;
                  this.markProgress();
                  if (this.targetAttacks >= 20) {
                     this.clearTarget();
                  }
               }
            }
         } else {
            this.targetWasSwelling = true;
            BlockPos retreat = this.farthestPatrolPoint(player.position());
            if (retreat != null) {
               this.navigateTo(retreat, 2);
            }
         }
      }
   }

   private void patrol(LocalPlayer player) {
      if (this.patrolWaypoints.isEmpty()) {
         this.buildWaypoints();
      }

      if (this.patrolWaypoints.isEmpty()) {
         this.recoverFromStall(player);
      } else {
         this.patrolWaypointIndex = this.patrolWaypointIndex % this.patrolWaypoints.size();
         BlockPos waypoint = this.patrolWaypoints.get(this.patrolWaypointIndex);
         if (at(player, waypoint, 4.0)) {
            this.patrolWaypointIndex = (this.patrolWaypointIndex + 1) % this.patrolWaypoints.size();
            this.markProgress();
            this.cancelNavigation();
         } else {
            this.navigateTo(waypoint, 3);
         }
      }
   }

   private void tickUnloading(LocalPlayer player, ClientLevel level) {
      int powder = countGunpowder(player);
      if (powder <= 0) {
         this.closeFeatureContainer(player);
         this.transition(CreeperFarmFeature.Phase.LOOTING);
      } else if (!this.unloadTarget.is("None") && this.unloadGunpowder.getValue()) {
         if (this.tick - this.lastProgressTick > 800L) {
            this.unloadBackoffUntil = this.tick + 1200L;
            this.recoverFromStall(player);
         } else if (this.unloadTarget.is("Clan")) {
            this.tickClanUnload(player);
         } else {
            this.tickBlockStorageUnload(player, level);
         }
      } else {
         this.unloadBackoffUntil = this.tick + 1200L;
         this.transition(CreeperFarmFeature.Phase.LOOTING);
      }
   }

   private void tickClanUnload(LocalPlayer player) {
      String command = this.clanUnloadCommand.getValue().trim();
      if (command.isEmpty()) {
         this.unloadBackoffUntil = this.tick + 1200L;
         this.transition(CreeperFarmFeature.Phase.LOOTING);
      } else {
         if (!this.unloadCommandSent) {
            ServerAdapters.current().sendCommand(player, command);
            this.unloadCommandSent = true;
            this.storageOpenRequested = true;
            this.lastActionTick = this.tick;
         }

         AbstractContainerMenu menu = this.currentStorageMenu(player);
         if (menu != null) {
            this.depositGunpowder(player, menu);
         } else {
            if (this.state.ticksInState(this.tick) > 240L) {
               this.unloadBackoffUntil = this.tick + 1200L;
               this.closeFeatureContainer(player);
               this.transition(CreeperFarmFeature.Phase.LOOTING);
            }
         }
      }
   }

   private void tickBlockStorageUnload(LocalPlayer player, ClientLevel level) {
      switch (this.unloadStep) {
         case FIND:
            this.storage = this.resolveStorage(player, level);
            if (this.storage != null) {
               this.unloadStep = CreeperFarmFeature.UnloadStep.MOVE;
               this.markProgress();
            } else if (this.state.ticksInState(this.tick) > 200L) {
               this.unloadBackoffUntil = this.tick + 1200L;
               this.transition(CreeperFarmFeature.Phase.LOOTING);
            }
            break;
         case MOVE:
            if (this.storage == null) {
               this.unloadStep = CreeperFarmFeature.UnloadStep.FIND;
               return;
            }

            if (at(player, this.storage, 4.0)) {
               this.cancelNavigation();
               this.unloadStep = CreeperFarmFeature.UnloadStep.OPEN;
               this.markProgress();
            } else {
               this.navigateTo(this.storage, 2);
            }
            break;
         case OPEN:
            AbstractContainerMenu menu = this.currentStorageMenu(player);
            if (menu != null) {
               this.depositGunpowder(player, menu);
               return;
            }

            if (this.storage == null || !at(player, this.storage, 4.0)) {
               this.unloadStep = CreeperFarmFeature.UnloadStep.MOVE;
               return;
            }

            if (this.actionReady()) {
               this.openStorage(player, this.storage);
            }
      }
   }

   private void depositGunpowder(LocalPlayer player, AbstractContainerMenu menu) {
      int slot = findPlayerContainerSlot(menu, stack -> stack.is(Items.GUNPOWDER));
      if (slot >= 0 && this.actionReady()) {
         InventoryUtil.quickMoveSlot(slot);
         this.lastActionTick = this.tick;
      } else if (slot < 0) {
         this.closeFeatureContainer(player);
         this.transition(CreeperFarmFeature.Phase.LOOTING);
      }
   }

   private void openStorage(LocalPlayer player, BlockPos position) {
      this.lookAt(player, Vec3.atCenterOf(position));
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
      mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
      player.swing(InteractionHand.MAIN_HAND);
      this.storageOpenRequested = true;
      this.lastActionTick = this.tick;
   }

   private AbstractContainerMenu currentStorageMenu(LocalPlayer player) {
      if ((this.storageOpenRequested || this.openedContainer) && InventoryUtil.isContainerScreenOpen()) {
         AbstractContainerMenu menu = InventoryUtil.getOpenMenu();
         if (menu != null && menu != player.inventoryMenu) {
            this.openedContainer = true;
            return menu;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private BlockPos resolveStorage(LocalPlayer player, ClientLevel level) {
      Optional<BlockPos> configured = PveCoordinateParser.parse(this.storagePosition.getValue());
      if (configured.isPresent()) {
         return configured.get();
      }

      BlockPos origin = this.farmCenter == null ? player.blockPosition() : this.farmCenter;
      boolean ender = this.unloadTarget.is("Ender Chest");
      BlockPos signed = this.findSignedStorage(level, origin, ender);
      return signed != null ? signed : this.findNearestStorage(level, player, origin, ender);
   }

   private BlockPos findSignedStorage(ClientLevel level, BlockPos origin, boolean ender) {
      int radius = Math.min(20, intValue(this.regionRadius));
      int yRadius = Math.min(8, intValue(this.regionHeight));

      for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-radius, -yRadius, -radius), origin.offset(radius, yRadius, radius))) {
         if (level.getBlockEntity(cursor) instanceof SignBlockEntity sign) {
            List<String> lines = new ArrayList<>(8);

            for (int line = 0; line < 4; line++) {
               lines.add(sign.getFrontText().getMessage(line, false).getString());
               lines.add(sign.getBackText().getMessage(line, false).getString());
            }

            if (matchesGunpowderLabel(lines)) {
               BlockPos storagePos = storageNear(level, cursor, 2, ender);
               if (storagePos != null) {
                  return storagePos;
               }
            }
         }
      }

      return null;
   }

   private BlockPos findNearestStorage(ClientLevel level, LocalPlayer player, BlockPos origin, boolean ender) {
      int radius = Math.min(20, intValue(this.regionRadius));
      int yRadius = Math.min(8, intValue(this.regionHeight));
      BlockPos best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-radius, -yRadius, -radius), origin.offset(radius, yRadius, radius))) {
         if (isStorage(level.getBlockState(cursor), ender)) {
            double distance = player.position().distanceToSqr(Vec3.atCenterOf(cursor));
            if (distance < bestDistance) {
               bestDistance = distance;
               best = cursor.immutable();
            }
         }
      }

      return best;
   }

   private static BlockPos storageNear(ClientLevel level, BlockPos sign, int radius, boolean ender) {
      BlockPos best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (BlockPos cursor : BlockPos.betweenClosed(sign.offset(-radius, -radius, -radius), sign.offset(radius, radius, radius))) {
         if (isStorage(level.getBlockState(cursor), ender)) {
            double distance = cursor.distSqr(sign);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = cursor.immutable();
            }
         }
      }

      return best;
   }

   static boolean matchesGunpowderLabel(Iterable<String> lines) {
      for (String line : lines) {
         String normalized = line == null ? "" : line.replaceAll("§.", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
         if (normalized.contains("gunpowder") || normalized.contains("порох")) {
            return true;
         }
      }

      return false;
   }

   private void resolveRegion(LocalPlayer player) {
      BlockPos configuredCenter = PveCoordinateParser.parse(this.farmPosition.getValue())
         .orElseGet(() -> this.farmCenter == null ? player.blockPosition().immutable() : this.farmCenter);
      Optional<BlockPos> configuredMin = PveCoordinateParser.parse(this.regionMin.getValue());
      Optional<BlockPos> configuredMax = PveCoordinateParser.parse(this.regionMax.getValue());
      this.farmCenter = configuredCenter;
      if (configuredMin.isPresent() && configuredMax.isPresent()) {
         BlockPos first = configuredMin.get();
         BlockPos second = configuredMax.get();
         this.farmMin = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
         this.farmMax = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
         this.farmCenter = new BlockPos(
            (this.farmMin.getX() + this.farmMax.getX()) / 2, (this.farmMin.getY() + this.farmMax.getY()) / 2, (this.farmMin.getZ() + this.farmMax.getZ()) / 2
         );
      } else {
         int radius = intValue(this.regionRadius);
         int height = intValue(this.regionHeight);
         this.farmMin = this.farmCenter.offset(-radius, -height, -radius);
         this.farmMax = this.farmCenter.offset(radius, height, radius);
      }

      this.farmBounds = new AABB(
         this.farmMin.getX(), this.farmMin.getY(), this.farmMin.getZ(), this.farmMax.getX() + 1.0, this.farmMax.getY() + 1.0, this.farmMax.getZ() + 1.0
      );
      if (this.chunkWaypoints.isEmpty()) {
         this.buildWaypoints();
      }
   }

   private void buildWaypoints() {
      this.chunkWaypoints.clear();
      this.patrolWaypoints.clear();
      if (this.farmMin != null && this.farmMax != null && this.farmCenter != null) {
         int step = Math.max(16, intValue(this.chunkStep) * 16);
         int y = this.farmCenter.getY();

         for (int x = this.farmMin.getX(); x <= this.farmMax.getX(); x += step) {
            for (int z = this.farmMin.getZ(); z <= this.farmMax.getZ(); z += step) {
               this.chunkWaypoints.add(new BlockPos(x, y, z));
               if (this.chunkWaypoints.size() >= 81) {
                  break;
               }
            }

            if (this.chunkWaypoints.size() >= 81) {
               break;
            }
         }

         if (this.chunkWaypoints.stream().noneMatch(this.farmCenter::equals)) {
            this.chunkWaypoints.add(this.farmCenter);
         }

         this.patrolWaypoints.add(new BlockPos(this.farmMin.getX(), y, this.farmMin.getZ()));
         this.patrolWaypoints.add(new BlockPos(this.farmMax.getX(), y, this.farmMin.getZ()));
         this.patrolWaypoints.add(new BlockPos(this.farmMax.getX(), y, this.farmMax.getZ()));
         this.patrolWaypoints.add(new BlockPos(this.farmMin.getX(), y, this.farmMax.getZ()));
         this.patrolWaypoints.add(this.farmCenter);
      }
   }

   private ItemEntity nearestGunpowderEntity(ClientLevel level, LocalPlayer player) {
      if (this.farmBounds == null) {
         return null;
      }

      ItemEntity best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof ItemEntity item && item.isAlive() && item.getItem().is(Items.GUNPOWDER) && this.farmBounds.contains(item.position())) {
            double distance = item.distanceToSqr(player);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = item;
            }
         }
      }

      return best;
   }

   private Creeper nearestCreeper(ClientLevel level, LocalPlayer player) {
      return this.creepersSortedByDistance(level, player).stream().findFirst().orElse(null);
   }

   public List<Creeper> creepersSortedByDistance(ClientLevel level, LocalPlayer player) {
      if (level != null && player != null && this.farmBounds != null) {
         List<Creeper> creepers = new ArrayList<>();

         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Creeper creeper && this.validTarget(creeper) && this.farmBounds.contains(creeper.position())) {
               creepers.add(creeper);
            }
         }

         creepers.sort(Comparator.comparingDouble(creeperx -> creeperx.distanceToSqr(player)));
         return List.copyOf(creepers);
      } else {
         return List.of();
      }
   }

   private boolean validTarget(Creeper creeper) {
      return creeper != null && creeper.isAlive() && !creeper.isRemoved() && this.farmBounds != null && this.farmBounds.contains(creeper.position());
   }

   private void updateTargetStats() {
      if (this.target != null && (!this.target.isAlive() || this.target.isRemoved())) {
         if (this.targetAttacked && !this.targetWasSwelling) {
            this.kills++;
         }

         this.clearTarget();
      }
   }

   private void updateInventoryStats(LocalPlayer player) {
      int current = countGunpowder(player);
      int gained = Math.max(0, current - this.previousGunpowder);
      int unloaded = Math.max(0, this.previousGunpowder - current);
      if (gained > 0 && this.state.state() != CreeperFarmFeature.Phase.UNLOADING) {
         this.gunpowderCollected += gained;
         this.moneyEarned = this.moneyEarned + gained * this.moneyPerGunpowder.getValue();
         this.markProgress();
      }

      if (unloaded > 0 && this.state.state() == CreeperFarmFeature.Phase.UNLOADING) {
         this.gunpowderUnloaded += unloaded;
         this.markProgress();
      }

      this.previousGunpowder = current;
   }

   private boolean shouldUnload(LocalPlayer player) {
      if (this.unloadGunpowder.getValue() && !this.unloadTarget.is("None") && this.tick >= this.unloadBackoffUntil) {
         int powder = countGunpowder(player);
         int threshold = intValue(this.unloadAtStacks) * 64;
         return powder >= threshold || powder > 0 && freeSlots(player) <= 1;
      } else {
         return false;
      }
   }

   private void recoverFromStall(LocalPlayer player) {
      this.cancelNavigation();
      this.closeFeatureContainer(player);
      this.clearTarget();
      this.recoveries++;
      this.lastProgressTick = this.tick;
      this.chunkWaypointIndex = 0;
      this.patrolWaypointIndex = 0;
      this.unloadStep = CreeperFarmFeature.UnloadStep.FIND;
      this.unloadCommandSent = false;
      this.storage = null;
      if (this.state.state() == CreeperFarmFeature.Phase.APPROACH) {
         this.state.reset(this.tick);
      } else {
         this.transition(CreeperFarmFeature.Phase.APPROACH);
      }
   }

   private void transition(CreeperFarmFeature.Phase next) {
      if (this.state.transition(next, this.tick)) {
         this.cancelNavigation();
         this.markProgress();
         if (next == CreeperFarmFeature.Phase.LOADING_CHUNKS) {
            this.chunkWaypointIndex = 0;
         }

         if (next == CreeperFarmFeature.Phase.UNLOADING) {
            this.unloadStep = CreeperFarmFeature.UnloadStep.FIND;
            this.unloadCommandSent = false;
            this.storage = null;
         }

         if (next != CreeperFarmFeature.Phase.UNLOADING) {
            this.closeFeatureContainer(mc.player);
         }

         if (next != CreeperFarmFeature.Phase.LOOTING) {
            this.clearTarget();
         }
      }
   }

   static CreeperFarmFeature.Phase nextPhase(CreeperFarmFeature.Phase phase, CreeperFarmFeature.FarmSignals signals) {
      return switch (phase) {
         case APPROACH -> signals.insideRegion() ? CreeperFarmFeature.Phase.LOADING_CHUNKS : CreeperFarmFeature.Phase.APPROACH;
         case LOADING_CHUNKS -> !signals.chunksLoaded() && !signals.timedOut() ? CreeperFarmFeature.Phase.LOADING_CHUNKS : CreeperFarmFeature.Phase.LOOTING;
         case LOOTING -> signals.shouldUnload() ? CreeperFarmFeature.Phase.UNLOADING : CreeperFarmFeature.Phase.LOOTING;
         case UNLOADING -> signals.unloadComplete()
            ? CreeperFarmFeature.Phase.LOOTING
            : (signals.timedOut() ? CreeperFarmFeature.Phase.APPROACH : CreeperFarmFeature.Phase.UNLOADING);
      };
   }

   static OptionalDouble parseMoneyDelta(String message) {
      if (message == null) {
         return OptionalDouble.empty();
      }

      String normalized = message.replaceAll("§.", "").trim().toLowerCase(Locale.ROOT);
      boolean positiveContext = normalized.contains("+")
         || normalized.contains("earned")
         || normalized.contains("received")
         || normalized.contains("sold")
         || normalized.contains("заработ")
         || normalized.contains("получ")
         || normalized.contains("продан");
      boolean moneyContext = normalized.contains("$")
         || normalized.contains("₽")
         || normalized.contains("coin")
         || normalized.contains("монет")
         || normalized.contains("валют");
      if (positiveContext && moneyContext) {
         Matcher matcher = MONEY_NUMBER.matcher(normalized);
         double best = -1.0;

         while (matcher.find()) {
            String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String suffix = matcher.group(1) != null ? matcher.group(2) : matcher.group(4);
            OptionalDouble parsed = parseLocalizedNumber(token, suffix);
            if (parsed.isPresent()) {
               best = Math.max(best, parsed.getAsDouble());
            }
         }

         return best > 0.0 ? OptionalDouble.of(best) : OptionalDouble.empty();
      } else {
         return OptionalDouble.empty();
      }
   }

   private static OptionalDouble parseLocalizedNumber(String token, String suffix) {
      String compact = token.replace(" ", "");
      if (compact.isEmpty()) {
         return OptionalDouble.empty();
      }

      int comma = compact.lastIndexOf(44);
      int dot = compact.lastIndexOf(46);
      int separator = Math.max(comma, dot);
      String normalized;
      if (separator >= 0 && compact.length() - separator - 1 <= 2) {
         String integer = compact.substring(0, separator).replace(",", "").replace(".", "");
         String fraction = compact.substring(separator + 1);
         normalized = integer + "." + fraction;
      } else {
         normalized = compact.replace(",", "").replace(".", "");
      }

      try {
         double value = Double.parseDouble(normalized);
         if (suffix == null || !suffix.equalsIgnoreCase("k") && !suffix.equalsIgnoreCase("к")) {
            if (suffix != null && (suffix.equalsIgnoreCase("m") || suffix.equalsIgnoreCase("м"))) {
               value *= 1000000.0;
            }
         } else {
            value *= 1000.0;
         }

         return value > 0.0 ? OptionalDouble.of(value) : OptionalDouble.empty();
      } catch (NumberFormatException ignored) {
         return OptionalDouble.empty();
      }
   }

   private boolean beginNavigation() {
      if (!this.navigator.isAvailable()) {
         return false;
      }

      try {
         this.navigator.begin(PveManagerFeature.INSTANCE.configureNavigation(NavigationOptions.walking()));
         this.navigationActive = true;
         return true;
      } catch (RuntimeException | LinkageError ignored) {
         this.navigationActive = false;
         return false;
      }
   }

   private void navigateTo(BlockPos target, int radius) {
      if (target != null && this.beginNavigation()) {
         Optional<BlockPos> goal = this.navigator.currentGoal();
         boolean sameGoal = goal.isPresent() && goal.get().equals(target);
         if (!sameGoal || !this.navigator.isPathing() && this.tick - this.lastNavigationTick >= 40L) {
            try {
               this.navigator.pathTo(target, radius);
               this.lastNavigationTick = this.tick;
            } catch (RuntimeException | LinkageError ignored) {
               this.lastNavigationTick = this.tick;
            }
         }
      }
   }

   private void cancelNavigation() {
      if (this.navigationActive) {
         try {
            this.navigator.cancel();
         } catch (RuntimeException | LinkageError ignored) {
            this.navigationActive = false;
         }
      }
   }

   private void endNavigation() {
      if (this.navigationActive) {
         try {
            this.navigator.end();
         } catch (RuntimeException | LinkageError ignored) {
            this.navigator.cancel();
         } finally {
            this.navigationActive = false;
         }
      }
   }

   private void closeFeatureContainer(LocalPlayer player) {
      if (this.openedContainer && player != null && player.containerMenu != player.inventoryMenu) {
         player.closeContainer();
      }

      this.openedContainer = false;
      this.storageOpenRequested = false;
   }

   private void clearTarget() {
      this.target = null;
      this.targetAttacked = false;
      this.targetWasSwelling = false;
      this.targetAttacks = 0;
   }

   private BlockPos farthestPatrolPoint(Vec3 position) {
      BlockPos farthest = null;
      double distance = Double.NEGATIVE_INFINITY;

      for (BlockPos waypoint : this.patrolWaypoints) {
         double candidate = position.distanceToSqr(Vec3.atCenterOf(waypoint));
         if (candidate > distance) {
            distance = candidate;
            farthest = waypoint;
         }
      }

      return farthest;
   }

   private boolean actionReady() {
      return this.tick - this.lastActionTick >= 4L;
   }

   private void markProgress() {
      this.lastProgressTick = this.tick;
   }

   private void lookAt(LocalPlayer player, Vec3 targetPosition) {
      if (PveManagerFeature.INSTANCE.rotate.getValue()) {
         this.saveRotation(player);
         Vec3 delta = targetPosition.subtract(player.getEyePosition());
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         player.setYRot((float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F);
         player.setXRot((float)(-Math.toDegrees(Math.atan2(delta.y, horizontal))));
      }
   }

   private void saveRotation(LocalPlayer player) {
      if (!this.rotationSaved && player != null) {
         this.savedYaw = player.getYRot();
         this.savedPitch = player.getXRot();
         this.rotationSaved = true;
      }
   }

   private void restoreRotation(LocalPlayer player) {
      if (this.rotationSaved && player != null) {
         player.setYRot(this.savedYaw);
         player.setXRot(this.savedPitch);
         this.rotationSaved = false;
      }
   }

   private static boolean isStorage(BlockState state, boolean ender) {
      return ender ? state.is(Blocks.ENDER_CHEST) : state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST);
   }

   private static int findPlayerContainerSlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
      int start = ContainerLootService.containerSlotCount(menu);

      for (int slot = start; slot < menu.slots.size(); slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty() && predicate.test(stack)) {
            return slot;
         }
      }

      return -1;
   }

   private static int countGunpowder(LocalPlayer player) {
      int count = 0;

      for (int slot = 9; slot < 45; slot++) {
         ItemStack stack = player.inventoryMenu.getSlot(slot).getItem();
         if (stack.is(Items.GUNPOWDER)) {
            count += stack.getCount();
         }
      }

      if (player.getOffhandItem().is(Items.GUNPOWDER)) {
         count += player.getOffhandItem().getCount();
      }

      return count;
   }

   private static int freeSlots(LocalPlayer player) {
      int free = 0;

      for (int slot = 9; slot < 45; slot++) {
         if (player.inventoryMenu.getSlot(slot).getItem().isEmpty()) {
            free++;
         }
      }

      return free;
   }

   private static boolean at(LocalPlayer player, BlockPos position, double radius) {
      return position != null && player.position().distanceToSqr(Vec3.atCenterOf(position)) <= radius * radius;
   }

   private static int intValue(NumberSetting setting) {
      return (int)Math.round(setting.getValue());
   }

   record FarmSignals(boolean insideRegion, boolean chunksLoaded, boolean shouldUnload, boolean unloadComplete, boolean timedOut) {
   }

   public record FarmStats(long uptimeTicks, double money, int gunpowder, int kills, double incomePerHour, CreeperFarmFeature.Phase phase, int recoveries) {
   }

   public enum Phase {
      APPROACH,
      LOADING_CHUNKS,
      LOOTING,
      UNLOADING;
   }

   enum UnloadStep {
      FIND,
      MOVE,
      OPEN;
   }
}
