package win.furryhook.feature.impl.pve.autowarden;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.DisconnectEvent;
import win.furryhook.event.events.lifecycle.WorldJoinEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.event.events.render.Render2DEvent;
import win.furryhook.event.events.render.Render3DEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureEnableRejectedException;
import win.furryhook.feature.impl.pve.PveManagerFeature;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ButtonSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.feature.setting.TextSetting;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;
import win.furryhook.pve.PvpStateTracker;
import win.furryhook.pve.economy.AuctionPriceScanner;
import win.furryhook.pve.economy.EconomyItemText;
import win.furryhook.pve.economy.ServerUiText;
import win.furryhook.pve.navigation.BaritoneNavigator;
import win.furryhook.pve.navigation.NavigationOptions;
import win.furryhook.pve.server.ServerAdapter;
import win.furryhook.pve.server.ServerAdapters;
import win.furryhook.utils.FriendManager;
import win.furryhook.utils.inventory.ContainerLootService;
import win.furryhook.utils.inventory.InventoryUtil;
import win.furryhook.utils.render.Theme;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;
import win.furryhook.utils.text.ChatUtil;

public final class AutoWardenFeature extends PveFeature implements MinecraftContext {
   private static final int COMMAND_SETTLE_TICKS = 80;
   private static final int ACTION_INTERVAL_TICKS = 4;
   private static final int OPEN_RETRY_TICKS = 30;
   private static final int INVENTORY_FULL_FREE_SLOTS = 2;
   private static final int ZONE_COLOR = -871038977;
   private static final float ZONE_LINE_WIDTH = 1.4F;
   public final TextSetting homeAnarchy = this.register(new TextSetting("Home Anarchy", "101", 3));
   public final TextSetting lootAnarchies = this.register(new TextSetting("Loot Anarchies", "102, 103, 104", 64));
   public final NumberSetting maxChestWait = this.register(new NumberSetting("Max Chest Wait", 900.0, 0.0, 7200.0, 10.0, " s"));
   public final NumberSetting minLootPerCycle = this.register(new NumberSetting("Min Loot", 1.0, 1.0, 36.0, 1.0, " items"));
   public final NumberSetting homeWaitThreshold = this.register(new NumberSetting("Home Wait Threshold", 180.0, 0.0, 3600.0, 10.0, " s"));
   public final NumberSetting returnBuffer = this.register(new NumberSetting("Return Buffer", 12.0, 1.0, 120.0, 1.0, " s"));
   public final NumberSetting preOpenSeconds = this.register(new NumberSetting("Before Opening", 2.0, 0.0, 20.0, 1.0, " s"));
   public final NumberSetting chestGrace = this.register(new NumberSetting("Chest Open Delay", 1.0, 0.0, 10.0, 0.5, " s"));
   public final NumberSetting teleportCooldown = this.register(new NumberSetting("Teleport Cooldown", 8.0, 1.0, 30.0, 1.0, " s"));
   public final BooleanSetting needInvisibility = this.register(new BooleanSetting("Require Invisibility", true));
   public final BooleanSetting needFood = this.register(new BooleanSetting("Require Food", true));
   public final BooleanSetting needSpeed = this.register(new BooleanSetting("Require Speed", false));
   public final NumberSetting minInvisibility = this.register(
      new NumberSetting("Min Invisibility", 2.0, 0.0, 64.0, 1.0, "").visibleWhen(this.needInvisibility::getValue)
   );
   public final NumberSetting minFood = this.register(new NumberSetting("Min Food", 32.0, 0.0, 256.0, 1.0, "").visibleWhen(this.needFood::getValue));
   public final NumberSetting minSpeed = this.register(new NumberSetting("Min Speed", 1.0, 0.0, 64.0, 1.0, "").visibleWhen(this.needSpeed::getValue));
   public final NumberSetting homeScanRadius = this.register(new NumberSetting("Home Scan Radius", 24.0, 4.0, 48.0, 1.0, " blocks"));
   public final TextSetting restockSign = this.register(new TextSetting("Restock Sign", "", 64));
   public final BooleanSetting avoidCrowded = this.register(new BooleanSetting("Avoid Crowded Chests", true));
   public final NumberSetting crowdRadius = this.register(new NumberSetting("Crowd Radius", 12.0, 0.0, 64.0, 1.0, " blocks"));
   public final NumberSetting crowdPenalty = this.register(new NumberSetting("Crowd Penalty", 20.0, 0.0, 500.0, 1.0, ""));
   public final BooleanSetting fleeWarden = this.register(new BooleanSetting("Flee From Warden", true));
   public final BooleanSetting emptyHand = this.register(new BooleanSetting("Keep Hand Empty", false));
   public final BooleanSetting autoReconnect = this.register(new BooleanSetting("Auto Reconnect", true));
   public final NumberSetting autoReconnectDelay = this.register(
      new NumberSetting("Reconnect Delay", 5.0, 1.0, 120.0, 1.0, " s").visibleWhen(this.autoReconnect::getValue)
   );
   public final BooleanSetting autoReporter = this.register(new BooleanSetting("Auto Reporter", false));
   public final BooleanSetting scoutCities = this.register(new BooleanSetting("Scout Warden Cities", true));
   public final ButtonSetting scoutNow = this.register(
      new ButtonSetting("Scout Now", "Scout", this::printScoutRecommendation).visibleWhen(this.scoutCities::getValue)
   );
   public final BooleanSetting telegramNotifications = this.register(new BooleanSetting("Telegram Notifications", false));
   public final ButtonSetting telegramConnect = this.register(
      new ButtonSetting("Connect Telegram", "Connect", this::connectTelegram).visibleWhen(this.telegramNotifications::getValue)
   );
   public final BooleanSetting autoSell = this.register(new BooleanSetting("Auto Sell", false));
   public final TextSetting sellSign = this.register(new TextSetting("Sell Sign", "продажа", 64).visibleWhen(this.autoSell::getValue));
   public final NumberSetting sellMarkup = this.register(new NumberSetting("Sell Markup", 0.0, -50.0, 200.0, 1.0, "%").visibleWhen(this.autoSell::getValue));
   public final NumberSetting fallbackSellPrice = this.register(
      new NumberSetting("Fallback Sell Price", 1000.0, 1.0, 1.0E8, 100.0, "").visibleWhen(this.autoSell::getValue)
   );
   public final NumberSetting investPercent = this.register(
      new NumberSetting("Invest Into Clan", 100.0, 0.0, 100.0, 1.0, "%").visibleWhen(this.autoSell::getValue)
   );
   public final NumberSetting sellMaxPages = this.register(new NumberSetting("Pages To Parse", 3.0, 1.0, 10.0, 1.0, "").visibleWhen(this.autoSell::getValue));
   public final BooleanSetting showZone = this.register(new BooleanSetting("Show Warden Zone", true));
   public final BooleanSetting showStats = this.register(new BooleanSetting("Show Statistics", true));
   private final AutoWardenWorkflow workflow = new AutoWardenWorkflow();
   private final AnarchyRotation rotation = new AnarchyRotation();
   private final AutoWardenStats stats = new AutoWardenStats();
   private final WardenChestScanner scanner = new WardenChestScanner();
   private final BaritoneNavigator navigator = BaritoneNavigator.INSTANCE;
   private final Set<String> reportedAttackers = new HashSet<>();
   private FunTimeWardenContract contract;
   private TelegramNotifier telegram;
   private List<Integer> configuredLootAnarchies = List.of();
   private int configuredHomeAnarchy = -1;
   private int currentAnarchy = -1;
   private int targetAnarchy = -1;
   private long tick;
   private long phaseActionTick;
   private long reconnectAtMillis;
   private long combatHoldUntilMillis;
   private long lastContainerOpenTick;
   private long lastScoutTick;
   private long storageRetryUntil;
   private boolean phaseActionStarted;
   private int phaseSubstep;
   private boolean navigationActive;
   private boolean reconnectAttempted;
   private boolean resourcesHeld;
   private boolean wasDead;
   private String lastAttacker;
   private ServerData reconnectServer;
   private BlockPos cityCenter;
   private BlockPos targetChest;
   private WardenChestScanner.ChestObservation targetObservation;
   private BlockPos storageCenter;
   private BlockPos activeStorageChest;
   private int patrolIndex;
   private int cycleLoot;
   private int depositedThisCycle;
   private int sellStep;
   private int sellSourceSlot = -1;
   private ItemStack sellStack = ItemStack.EMPTY;
   private long sellPrice;
   private long bestAuctionPrice;
   private int sellPagesScanned;

   public AutoWardenFeature() {
      super(
         "AutoWarden",
         "Automates Warden-city chest routes, storage, supplies and selling",
         FeatureCategory.MISC,
         -1,
         AutomationPriority.BOT,
         AutomationResource.MOVEMENT,
         AutomationResource.ROTATION,
         AutomationResource.INVENTORY,
         AutomationResource.SCREEN,
         AutomationResource.CHAT,
         AutomationResource.NAVIGATION
      );
   }

   @Override
   protected void validatePveEnable() {
      ServerAdapter adapter = ServerAdapters.current();
      this.contract = new FunTimeWardenContract(adapter);
      if (!this.contract.supported()) {
         throw new FeatureEnableRejectedException("AutoWarden requires a FunTime server profile");
      }

      this.configuredHomeAnarchy = AutoWardenParsers.parseHomeAnarchy(this.homeAnarchy.getValue()).orElse(-1);
      this.configuredLootAnarchies = AutoWardenParsers.parseLootAnarchies(this.lootAnarchies.getValue(), this.configuredHomeAnarchy);
      if (this.configuredHomeAnarchy < 0 || this.configuredLootAnarchies.isEmpty()) {
         throw new FeatureEnableRejectedException("configure one home anarchy and at least one loot anarchy");
      }

      if (!this.navigator.isAvailable()) {
         throw new FeatureEnableRejectedException("Baritone is unavailable");
      }
   }

   @Override
   protected void onPveEnable() {
      Minecraft client = Minecraft.getInstance();
      this.resetRuntime();
      this.resourcesHeld = true;
      this.navigator.begin(PveManagerFeature.INSTANCE.configureNavigation(NavigationOptions.walking()));
      this.navigationActive = true;
      this.stats.start(System.currentTimeMillis());
      this.rememberServer(client);
      if (this.telegramNotifications.getValue()) {
         this.ensureTelegram().reloadAndVerify();
      }
   }

   @Override
   protected void onPveDisable() {
      this.cleanupRuntime(true);
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resourcesHeld = false;
      this.endNavigation();
      this.closeOwnedContainer(mc.player);
      if ((reason == PveAutomationCoordinator.RevocationReason.DISCONNECT || reason == PveAutomationCoordinator.RevocationReason.WORLD_CHANGE)
         && this.autoReconnect.getValue()
         && this.reconnectServer != null) {
         this.scheduleReconnect();
      }
   }

   @Override
   protected boolean disableAfterRevocation(PveAutomationCoordinator.RevocationReason reason) {
      boolean reconnectTransition = reason == PveAutomationCoordinator.RevocationReason.DISCONNECT
         || reason == PveAutomationCoordinator.RevocationReason.WORLD_CHANGE;
      return !reconnectTransition || !this.autoReconnect.getValue() || this.reconnectServer == null;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft client = event.getClient();
      this.rememberServer(client);
      if (client.player == null || client.level == null || client.gameMode == null) {
         this.tickReconnect(client);
      } else if (this.ensureResources()) {
         LocalPlayer player = client.player;
         this.tick++;
         this.updateCurrentAnarchy();
         this.updateSupplies(player);
         if (this.scoutCities.getValue() && this.tick - this.lastScoutTick >= 6000L) {
            this.printScoutRecommendation();
         }

         this.handleDeath(player);
         if (!player.isDeadOrDying()) {
            if (this.emptyHand.getValue()) {
               this.keepSafeHand(player);
            }

            if (this.dangerNearby(player, client.level)) {
               this.workflow.dangerDetected(this.tick);
            } else if (this.workflow.phase() == AutoWardenWorkflow.Phase.PVP_HIDE
               && System.currentTimeMillis() >= this.combatHoldUntilMillis
               && !PvpStateTracker.INSTANCE.isActive()) {
               this.workflow.dangerCleared(AutoWardenInventory.carryingValuables(player), this.tick);
               this.phaseChanged();
            }

            long timeoutOverride = this.workflow.phase() == AutoWardenWorkflow.Phase.HOME_WAIT
               ? Math.round((this.maxChestWait.getValue() + this.teleportCooldown.getValue() * 3.0) * 20.0)
               : -1L;
            if (this.workflow.timedOut(this.tick, timeoutOverride)) {
               this.failCurrentPhase("Phase timed out");
            }

            this.dispatchPhase(player, client.level);
            this.updateStatsPhase();
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && event.getPacket() instanceof ClientboundSystemChatPacket packet) {
         String text = packet.content().getString();
         AutoWardenParsers.parseAnarchy(text).ifPresent(value -> this.currentAnarchy = value);
         AutoWardenParsers.parseCombatHoldMillis(text).ifPresent(value -> {
            this.combatHoldUntilMillis = Math.max(this.combatHoldUntilMillis, System.currentTimeMillis() + value);
            PvpStateTracker.INSTANCE.markCombatFor(value);
         });
         AutoWardenParsers.parseAttacker(text).ifPresent(attacker -> this.lastAttacker = attacker);
         if (AutoWardenParsers.indicatesFullAnarchy(text) && this.targetAnarchy >= 0) {
            this.rotation.avoidFor(this.targetAnarchy, 120000L, System.currentTimeMillis());
            this.workflow.rotateAnarchy(this.tick);
            this.phaseChanged();
         }
      }
   }

   @EventTarget
   public void onDisconnect(DisconnectEvent event) {
      this.rememberServer(event.getClient());
      if (this.autoReconnect.getValue() && this.reconnectServer != null) {
         this.scheduleReconnect();
      }
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      this.reconnectAtMillis = 0L;
      this.reconnectAttempted = false;
      if (this.isEnabled()) {
         if (this.ensureResources()) {
            this.navigator.begin(PveManagerFeature.INSTANCE.configureNavigation(NavigationOptions.walking()));
            this.navigationActive = true;
            this.workflow.move(AutoWardenWorkflow.Phase.ENSURE_ANARCHY, "Connection restored", this.tick);
            this.phaseChanged();
         }
      }
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      if (this.showStats.getValue()) {
         AutoWardenStats.Snapshot snapshot = this.stats.snapshot(System.currentTimeMillis());
         float x = 12.0F;
         float y = 92.0F;

         for (Entry<String, String> entry : snapshot.widgetData().entrySet()) {
            Render2DUtil.text(x, y, 10.0F, entry.getKey() + ": " + entry.getValue())
               .style(UiFontStyle.MEDIUM)
               .color(Theme.Colors.TEXT_TEXT)
               .outline(-1342177280, 0.7F)
               .draw();
            y += 12.0F;
         }
      }
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (this.showZone.getValue() && mc.level != null && mc.levelRenderer != null) {
         SimpleGizmoCollector collector = new SimpleGizmoCollector();
         TemporaryCollection ignored = Gizmos.withCollector(collector);

         try {
            for (Warden warden : mc.level
               .getEntitiesOfClass(Warden.class, mc.player == null ? new AABB(BlockPos.ZERO) : mc.player.getBoundingBox().inflate(96.0))) {
               Gizmos.addGizmo(new AutoWardenFeature.ZoneGizmo(warden.getBoundingBox().inflate(16.0, 4.0, 16.0))).setAlwaysOnTop();
            }
         } catch (Throwable var7) {
            if (ignored != null) {
               try {
                  ignored.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (ignored != null) {
            ignored.close();
         }

         event.getClient().levelRenderer.addMainThreadGizmos(collector.drainGizmos());
      }
   }

   public AutoWardenWorkflow.Phase getPhase() {
      return this.workflow.phase();
   }

   public AutoWardenStats.Snapshot getStats() {
      return this.stats.snapshot(System.currentTimeMillis());
   }

   public int activeLootAnarchy() {
      return this.targetAnarchy;
   }

   public boolean isOnLootAnarchy() {
      return this.configuredLootAnarchies.contains(this.currentAnarchy);
   }

   public boolean managesHotbar() {
      return this.workflow.phase() != AutoWardenWorkflow.Phase.IDLE;
   }

   public boolean stocksSpeed() {
      return this.needSpeed.getValue();
   }

   private void dispatchPhase(LocalPlayer player, ClientLevel level) {
      switch (this.workflow.phase()) {
         case IDLE:
            this.tickIdle(player);
            break;
         case ENSURE_ANARCHY:
            this.tickEnsureAnarchy(player);
            break;
         case HOME_TO_WARDEN_CITY:
            this.tickHomeToCity(player);
            break;
         case SEARCH_CHEST:
            this.tickSearchChest(player, level);
            break;
         case PATROL_CITY:
            this.tickPatrol(player, level);
            break;
         case MOVE_TO_CHEST:
            this.tickMoveToChest(player);
            break;
         case HOME_WAIT:
            this.tickHomeWait(player);
            break;
         case RETURN_TO_CITY:
            this.tickReturnToCity(player);
            break;
         case OPEN_AND_LOOT:
            this.tickOpenAndLoot(player);
            break;
         case PVP_HIDE:
            this.tickHide(player, level);
            break;
         case GO_TO_STORAGE:
            this.tickGoStorage(player);
            break;
         case DEPOSIT_LOOT:
            this.tickDeposit(player, level);
            break;
         case RESTOCK:
            this.tickRestock(player, level);
            break;
         case SELL_ITEMS:
            this.tickSell(player, level);
      }
   }

   private void tickIdle(LocalPlayer player) {
      if (this.tick >= this.storageRetryUntil) {
         boolean needsStorage = AutoWardenInventory.carryingValuables(player) || this.suppliesMissing(player);
         if (this.workflow.start(true, needsStorage, this.tick)) {
            this.phaseChanged();
         }
      }
   }

   private void tickEnsureAnarchy(LocalPlayer player) {
      if (!this.phaseActionStarted) {
         this.targetAnarchy = this.rotation.pickNext(this.configuredLootAnarchies, this.crowdPenalty.getValue(), System.currentTimeMillis());
         if (this.targetAnarchy < 0) {
            this.failCurrentPhase("No loot anarchy is available");
         } else {
            this.stats.activeAnarchy(this.targetAnarchy);
            if (this.currentAnarchy != this.targetAnarchy) {
               this.contract.switchAnarchy(this.targetAnarchy).ifPresent(command -> this.contractAdapter().sendCommand(player, command));
            }

            this.startPhaseAction();
         }
      } else {
         if (this.currentAnarchy == this.targetAnarchy || this.phaseActionElapsed(this.commandSettleTicks())) {
            this.currentAnarchy = this.targetAnarchy;
            this.workflow.anarchyReady(this.tick);
            this.phaseChanged();
         }
      }
   }

   private void tickHomeToCity(LocalPlayer player) {
      if (this.suppliesMissing(player)) {
         this.workflow.requestStorage("Supplies are below the configured minimum", this.tick);
         this.phaseChanged();
      } else if (!this.phaseActionStarted) {
         this.configuredHomeCommand().ifPresent(command -> this.contractAdapter().sendCommand(player, command));
         this.startPhaseAction();
      } else {
         if (this.phaseActionElapsed(this.commandSettleTicks())) {
            this.cityCenter = player.blockPosition();
            this.workflow.cityReady(this.tick);
            this.phaseChanged();
         }
      }
   }

   private void tickSearchChest(LocalPlayer player, ClientLevel level) {
      long now = System.currentTimeMillis();
      List<WardenChestScanner.ChestObservation> candidates = this.scanner.scanLootChests(level, player, 64, this.crowdRadius.getValue().intValue(), now);
      Optional<WardenChestScanner.ChestObservation> selected = this.scanner
         .selectBest(candidates, player.position(), this.maxChestWait.getValue().intValue(), this.avoidCrowded.getValue(), this.crowdPenalty.getValue(), now);
      if (selected.isEmpty()) {
         this.workflow.noChestFound(this.tick);
         this.phaseChanged();
      } else {
         this.selectChest(selected.get());
         int remaining = selected.get().remainingSeconds(now);
         this.workflow.chestSelected(remaining, this.homeWaitThreshold.getValue().intValue(), this.tick);
         this.phaseChanged();
      }
   }

   private void tickPatrol(LocalPlayer player, ClientLevel level) {
      long now = System.currentTimeMillis();
      Optional<WardenChestScanner.ChestObservation> selected = this.scanner
         .selectBest(
            this.scanner.scanLootChests(level, player, 64, this.crowdRadius.getValue().intValue(), now),
            player.position(),
            this.maxChestWait.getValue().intValue(),
            this.avoidCrowded.getValue(),
            this.crowdPenalty.getValue(),
            now
         );
      if (selected.isPresent()) {
         this.selectChest(selected.get());
         this.workflow.patrolFoundChest(this.tick);
         this.phaseChanged();
      } else {
         if (!this.navigator.isPathing() || this.phaseActionElapsed(200L)) {
            BlockPos center = this.cityCenter == null ? player.blockPosition() : this.cityCenter;
            int[][] offsets = new int[][]{{24, 0}, {0, 24}, {-24, 0}, {0, -24}, {16, 16}, {-16, 16}, {-16, -16}, {16, -16}};
            int[] offset = offsets[Math.floorMod(this.patrolIndex++, offsets.length)];
            this.pathTo(center.offset(offset[0], 0, offset[1]), 3);
            this.startPhaseAction();
         }
      }
   }

   private void tickMoveToChest(LocalPlayer player) {
      if (this.targetChest == null) {
         this.workflow.continueSearching(this.tick);
         this.phaseChanged();
      } else if (player.distanceToSqr(Vec3.atCenterOf(this.targetChest)) <= 16.0) {
         this.cancelNavigation();
         this.workflow.chestReached(this.tick);
         this.phaseChanged();
      } else {
         if (!this.navigator.isPathing()) {
            this.pathTo(this.targetChest, 2);
         }
      }
   }

   private void tickHomeWait(LocalPlayer player) {
      if (!this.phaseActionStarted) {
         if (this.currentAnarchy != this.configuredHomeAnarchy) {
            this.contract.switchAnarchy(this.configuredHomeAnarchy).ifPresent(command -> this.contractAdapter().sendCommand(player, command));
            this.phaseSubstep = 0;
         } else {
            this.configuredHomeCommand().ifPresent(command -> this.contractAdapter().sendCommand(player, command));
            this.phaseSubstep = 1;
         }

         this.startPhaseAction();
      } else if (this.phaseSubstep == 0 && this.phaseActionElapsed(this.commandSettleTicks())) {
         this.currentAnarchy = this.configuredHomeAnarchy;
         this.configuredHomeCommand().ifPresent(command -> this.contractAdapter().sendCommand(player, command));
         this.phaseSubstep = 1;
         this.phaseActionTick = this.tick;
      } else if (this.phaseSubstep != 1 || this.phaseActionElapsed(this.commandSettleTicks())) {
         int remaining = this.targetObservation == null ? 0 : this.targetObservation.remainingSeconds(System.currentTimeMillis());
         int returnAt = this.returnBuffer.getValue().intValue() + this.preOpenSeconds.getValue().intValue();
         this.stats.homeWaitSeconds(remaining);
         if (remaining <= returnAt) {
            this.workflow.returnWindowReached(this.tick);
            this.phaseChanged();
         }
      }
   }

   private void tickReturnToCity(LocalPlayer player) {
      if (!this.phaseActionStarted) {
         this.contract.switchAnarchy(this.targetAnarchy).ifPresent(command -> this.contractAdapter().sendCommand(player, command));
         this.startPhaseAction();
      } else if (this.phaseSubstep == 0 && this.phaseActionElapsed(this.commandSettleTicks())) {
         this.configuredHomeCommand().ifPresent(command -> this.contractAdapter().sendCommand(player, command));
         this.currentAnarchy = this.targetAnarchy;
         this.phaseSubstep = 1;
         this.phaseActionTick = this.tick;
      } else {
         if (this.phaseSubstep == 1 && this.phaseActionElapsed(this.commandSettleTicks())) {
            this.workflow.move(AutoWardenWorkflow.Phase.MOVE_TO_CHEST, "Returned for chest opening", this.tick);
            this.phaseChanged();
         }
      }
   }

   private void tickOpenAndLoot(LocalPlayer player) {
      AbstractContainerMenu menu = this.currentForeignMenu(player);
      if (menu == null) {
         if (this.phaseActionElapsed(Math.max(30, Math.round(this.chestGrace.getValue().floatValue() * 20.0F)))) {
            if (this.targetChest != null && this.openContainer(player, this.targetChest)) {
               this.startPhaseAction();
            } else {
               this.scanner.markFailed(this.targetChest, System.currentTimeMillis());
               this.workflow.continueSearching(this.tick);
               this.phaseChanged();
            }
         }
      } else if (this.actionReady() && AutoWardenInventory.quickMoveFirstContainerItem(menu, AutoWardenInventory::isValuable)) {
         this.cycleLoot++;
         this.phaseActionTick = this.tick;
      } else {
         this.closeOwnedContainer(player);
         this.scanner.markLooted(this.targetChest);
         this.stats.chestLooted(this.cycleLoot);
         this.rotation.onChestLooted(this.targetAnarchy);
         this.notifyTelegram("Looted chest on anarchy " + this.targetAnarchy + ": " + this.cycleLoot + " items");
         boolean shouldStore = this.cycleLoot >= this.minLootPerCycle.getValue().intValue()
            || AutoWardenInventory.freeSlots(player) <= 2
            || this.suppliesMissing(player);
         if (shouldStore) {
            this.workflow.requestStorage("Loot or supplies require storage", this.tick);
         } else {
            this.workflow.continueSearching(this.tick);
         }

         this.phaseChanged();
      }
   }

   private void tickHide(LocalPlayer player, ClientLevel level) {
      Optional<Vec3> danger = this.nearestDanger(player, level);
      if (!danger.isEmpty()) {
         Vec3 away = player.position().subtract(danger.get()).normalize();
         if (away.lengthSqr() < 1.0E-6) {
            away = new Vec3(1.0, 0.0, 0.0);
         }

         BlockPos escape = BlockPos.containing(player.position().add(away.scale(24.0)));
         if (!this.navigator.isPathing() || this.phaseActionElapsed(100L)) {
            this.pathTo(escape, 3);
            this.startPhaseAction();
         }
      }
   }

   private void tickGoStorage(LocalPlayer player) {
      if (!this.phaseActionStarted) {
         if (this.currentAnarchy != this.configuredHomeAnarchy) {
            this.contract.switchAnarchy(this.configuredHomeAnarchy).ifPresent(command -> this.contractAdapter().sendCommand(player, command));
            this.phaseSubstep = 0;
         } else {
            this.configuredHomeCommand().ifPresent(command -> this.contractAdapter().sendCommand(player, command));
            this.phaseSubstep = 1;
         }

         this.startPhaseAction();
      } else if (this.phaseSubstep == 0 && this.phaseActionElapsed(this.commandSettleTicks())) {
         this.currentAnarchy = this.configuredHomeAnarchy;
         this.configuredHomeCommand().ifPresent(command -> this.contractAdapter().sendCommand(player, command));
         this.phaseSubstep = 1;
         this.phaseActionTick = this.tick;
      } else {
         if (this.phaseSubstep == 1 && this.phaseActionElapsed(this.commandSettleTicks())) {
            this.storageCenter = player.blockPosition();
            this.workflow.storageReady(this.tick);
            this.phaseChanged();
         }
      }
   }

   private void tickDeposit(LocalPlayer player, ClientLevel level) {
      List<WardenChestScanner.StorageChest> storage = this.scanStorage(level);
      WardenChestScanner.StorageChest deposit = storage.stream()
         .filter(chest -> chest.kind() == WardenChestScanner.StorageKind.DEPOSIT)
         .findFirst()
         .orElse(null);
      if (deposit == null) {
         this.delayStorageRetry("No unsigned deposit chest was found");
      } else if (this.ensureStorageOpen(player, deposit.position())) {
         AbstractContainerMenu menu = this.currentForeignMenu(player);
         if (menu != null) {
            if (this.actionReady() && AutoWardenInventory.quickMoveFirstPlayerItem(menu, AutoWardenInventory::isValuable)) {
               this.depositedThisCycle++;
               this.phaseActionTick = this.tick;
            } else {
               this.closeOwnedContainer(player);
               this.stats.stored(this.depositedThisCycle);
               this.rotation.onDeposited(this.targetAnarchy, this.depositedThisCycle);
               boolean needsRestock = this.suppliesMissing(player);
               this.workflow.depositFinished(needsRestock, this.autoSell.getValue(), this.tick);
               if (!needsRestock && !this.autoSell.getValue()) {
                  this.recordCycleComplete();
               }

               this.phaseChanged();
            }
         }
      }
   }

   private void tickRestock(LocalPlayer player, ClientLevel level) {
      if (!this.suppliesMissing(player)) {
         this.closeOwnedContainer(player);
         this.workflow.restockFinished(this.autoSell.getValue(), this.tick);
         if (!this.autoSell.getValue()) {
            this.recordCycleComplete();
         }

         this.phaseChanged();
      } else {
         WardenChestScanner.StorageChest chest = this.scanStorage(level)
            .stream()
            .filter(value -> value.kind() == WardenChestScanner.StorageKind.RESTOCK)
            .findFirst()
            .orElse(null);
         if (chest == null) {
            this.delayStorageRetry("No signed supply chest was found");
         } else if (this.ensureStorageOpen(player, chest.position())) {
            AbstractContainerMenu menu = this.currentForeignMenu(player);
            if (menu != null && this.actionReady()) {
               if (this.needInvisibility.getValue()
                  && AutoWardenInventory.countInvisibility(player) < this.minInvisibility.getValue().intValue()
                  && AutoWardenInventory.quickMoveFirstContainerItem(menu, AutoWardenInventory::isInvisibilityPotion)) {
                  this.phaseActionTick = this.tick;
               } else if (this.needFood.getValue()
                  && AutoWardenInventory.countFood(player) < this.minFood.getValue().intValue()
                  && AutoWardenInventory.quickMoveFirstContainerItem(menu, AutoWardenInventory::isRestockableFood)) {
                  this.phaseActionTick = this.tick;
               } else if (this.needSpeed.getValue()
                  && AutoWardenInventory.countSpeed(player) < this.minSpeed.getValue().intValue()
                  && AutoWardenInventory.quickMoveFirstContainerItem(menu, AutoWardenInventory::isSpeedPotion)) {
                  this.phaseActionTick = this.tick;
               } else {
                  this.delayStorageRetry("Supply chest does not contain the required stock");
               }
            }
         }
      }
   }

   private void tickSell(LocalPlayer player, ClientLevel level) {
      WardenChestScanner.StorageChest chest = this.scanStorage(level)
         .stream()
         .filter(value -> value.kind() == WardenChestScanner.StorageKind.SELL)
         .findFirst()
         .orElse(null);
      if (chest == null) {
         this.completeCycle();
      } else if (this.sellStep == 0) {
         if (this.ensureStorageOpen(player, chest.position())) {
            AbstractContainerMenu menu = this.currentForeignMenu(player);
            if (menu != null && this.actionReady()) {
               int slot = ContainerLootService.findFirst(menu, AutoWardenInventory::isValuable);
               if (slot < 0) {
                  this.closeOwnedContainer(player);
                  this.completeCycle();
               } else {
                  this.sellStack = menu.getSlot(slot).getItem().copy();
                  ContainerLootService.quickMoveFirst(menu, stack -> ItemStack.isSameItemSameComponents(stack, this.sellStack));
                  this.phaseActionTick = this.tick;
                  this.sellStep = 1;
               }
            }
         }
      } else if (this.sellStep == 1) {
         this.closeOwnedContainer(player);
         this.sellSourceSlot = InventoryUtil.findPlayerMenuSlot(player, stack -> ItemStack.isSameItemSameComponents(stack, this.sellStack));
         if (this.sellSourceSlot < 0) {
            this.resetSellOperation();
         } else {
            this.contract.auction().ifPresent(command -> this.contractAdapter().sendCommand(player, command + " " + itemQuery(this.sellStack)));
            this.phaseActionTick = this.tick;
            this.sellStep = 2;
         }
      } else if (this.sellStep == 2) {
         AbstractContainerMenu menu = this.currentForeignMenu(player);
         if (menu != null && this.actionReady()) {
            AuctionPriceScanner.competitivePrice(menu, this.sellStack.getItem(), this.sellStack.getHoverName().getString(), this.sellStack.getCount())
               .ifPresent(value -> this.bestAuctionPrice = this.bestAuctionPrice == 0L ? value : Math.min(this.bestAuctionPrice, value));
            this.sellPagesScanned++;
            if (this.sellPagesScanned < this.sellMaxPages.getValue().intValue()) {
               int nextPage = ContainerLootService.findFirst(
                  menu, stack -> EconomyItemText.containsAny(stack, "next page", "следующая страница", "вперед", "далее")
               );
               if (nextPage >= 0) {
                  InventoryUtil.clickSlot(nextPage, 0, ContainerInput.PICKUP);
                  this.phaseActionTick = this.tick;
                  return;
               }
            }

            long base = this.bestAuctionPrice > 0L ? this.bestAuctionPrice : this.fallbackSellPrice.getValue().longValue();
            this.sellPrice = Math.max(1L, Math.round(base * (1.0 + this.sellMarkup.getValue() / 100.0)));
            this.closeOwnedContainer(player);
            this.selectSellStack(player);
            this.contract.sell(this.sellPrice).ifPresent(command -> this.contractAdapter().sendCommand(player, command));
            long invest = Math.round(this.sellPrice * this.investPercent.getValue() / 100.0);
            this.contract.invest(invest).ifPresent(command -> this.contractAdapter().sendCommand(player, command));
            this.phaseActionTick = this.tick;
            this.sellStep = 3;
         }
      } else {
         if (this.phaseActionElapsed(40L)) {
            this.resetSellOperation();
         }
      }
   }

   private void completeCycle() {
      this.workflow.sellFinished(this.tick);
      this.recordCycleComplete();
      this.phaseChanged();
   }

   private void recordCycleComplete() {
      this.stats.cycleComplete();
      this.notifyTelegram("AutoWarden cycle complete: " + this.stats.snapshot(System.currentTimeMillis()).widgetData());
      this.scanner.clearLootedCycle();
      this.cycleLoot = 0;
      this.depositedThisCycle = 0;
   }

   private void selectChest(WardenChestScanner.ChestObservation observation) {
      this.targetObservation = observation;
      this.targetChest = observation.position();
      this.stats.target(observation.position().getX() + ", " + observation.position().getY() + ", " + observation.position().getZ());
   }

   private List<WardenChestScanner.StorageChest> scanStorage(ClientLevel level) {
      BlockPos center = this.storageCenter == null ? mc.player.blockPosition() : this.storageCenter;
      return this.scanner.scanStorage(level, center, this.homeScanRadius.getValue().intValue(), this.restockSign.getValue(), this.sellSign.getValue());
   }

   private boolean ensureStorageOpen(LocalPlayer player, BlockPos position) {
      AbstractContainerMenu menu = this.currentForeignMenu(player);
      if (menu != null && position.equals(this.activeStorageChest)) {
         return true;
      }

      if (player.distanceToSqr(Vec3.atCenterOf(position)) > 16.0) {
         if (!this.navigator.isPathing()) {
            this.pathTo(position, 2);
         }

         return false;
      } else {
         this.cancelNavigation();
         if (this.tick - this.lastContainerOpenTick < 30L) {
            return false;
         }

         this.activeStorageChest = position;
         this.lastContainerOpenTick = this.tick;
         return this.openContainer(player, position);
      }
   }

   private boolean openContainer(LocalPlayer player, BlockPos position) {
      if (mc.gameMode != null && position != null) {
         Vec3 directionVector = player.getEyePosition().subtract(Vec3.atCenterOf(position));
         Direction direction = Direction.getApproximateNearest((float)directionVector.x, (float)directionVector.y, (float)directionVector.z);
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), direction, position, false);
         mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
         player.swing(InteractionHand.MAIN_HAND);
         return true;
      } else {
         return false;
      }
   }

   private AbstractContainerMenu currentForeignMenu(LocalPlayer player) {
      return player != null && player.containerMenu != null && player.containerMenu != player.inventoryMenu ? player.containerMenu : null;
   }

   private void closeOwnedContainer(LocalPlayer player) {
      if (player != null && this.currentForeignMenu(player) != null) {
         player.closeContainer();
      }

      this.activeStorageChest = null;
   }

   private boolean dangerNearby(LocalPlayer player, ClientLevel level) {
      Optional<Vec3> danger = this.nearestDanger(player, level);
      if (danger.isPresent()) {
         this.combatHoldUntilMillis = Math.max(this.combatHoldUntilMillis, System.currentTimeMillis() + 10000L);
         return true;
      } else {
         return PvpStateTracker.INSTANCE.isActive() || System.currentTimeMillis() < this.combatHoldUntilMillis;
      }
   }

   private Optional<Vec3> nearestDanger(LocalPlayer player, ClientLevel level) {
      List<Vec3> positions = new ArrayList<>();
      if (this.fleeWarden.getValue()) {
         for (Warden warden : level.getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(32.0))) {
            positions.add(warden.position());
         }
      }

      for (Player other : level.players()) {
         if (other != player
            && other.isAlive()
            && !other.isCreative()
            && !other.isSpectator()
            && !FriendManager.INSTANCE.isFriend(other.getGameProfile().name())
            && player.distanceToSqr(other) <= 256.0) {
            positions.add(other.position());
         }
      }

      return positions.stream().min(Comparator.comparingDouble(player.position()::distanceToSqr));
   }

   private boolean suppliesMissing(LocalPlayer player) {
      return this.needInvisibility.getValue() && AutoWardenInventory.countInvisibility(player) < this.minInvisibility.getValue().intValue()
         || this.needFood.getValue() && AutoWardenInventory.countFood(player) < this.minFood.getValue().intValue()
         || this.needSpeed.getValue() && AutoWardenInventory.countSpeed(player) < this.minSpeed.getValue().intValue();
   }

   private void updateSupplies(LocalPlayer player) {
      this.stats.supplies(AutoWardenInventory.countInvisibility(player), AutoWardenInventory.countFood(player), AutoWardenInventory.countSpeed(player));
   }

   private void handleDeath(LocalPlayer player) {
      boolean dead = player.isDeadOrDying();
      if (dead && !this.wasDead) {
         this.wasDead = true;
         this.stats.died();
         this.rotation.onDeath(this.targetAnarchy);
         this.notifyTelegram("AutoWarden died on anarchy " + this.targetAnarchy);
         if (this.autoReporter.getValue() && this.lastAttacker != null && this.reportedAttackers.add(this.lastAttacker)) {
            this.contract.report(this.lastAttacker).ifPresent(command -> this.contractAdapter().sendCommand(player, command));
         }
      } else if (!dead) {
         this.wasDead = false;
      }
   }

   private void keepSafeHand(LocalPlayer player) {
      if (!player.isUsingItem() && this.currentForeignMenu(player) == null) {
         int selected = player.getInventory().getSelectedSlot();
         if (!player.getMainHandItem().isEmpty()) {
            int safe = AutoWardenInventory.findEmptyHotbarSlot(player, selected);
            if (safe >= 0) {
               player.getInventory().setSelectedSlot(safe);
            }
         }
      }
   }

   private void selectSellStack(LocalPlayer player) {
      if (this.sellSourceSlot >= 0) {
         int selected = player.getInventory().getSelectedSlot();
         int selectedMenu = 36 + selected;
         if (this.sellSourceSlot >= 36 && this.sellSourceSlot <= 44) {
            player.getInventory().setSelectedSlot(this.sellSourceSlot - 36);
         } else if (this.sellSourceSlot != selectedMenu) {
            InventoryUtil.swapWithHotbar(this.sellSourceSlot, selected);
         }
      }
   }

   private void resetSellOperation() {
      this.sellStep = 0;
      this.sellSourceSlot = -1;
      this.sellStack = ItemStack.EMPTY;
      this.sellPrice = 0L;
      this.bestAuctionPrice = 0L;
      this.sellPagesScanned = 0;
   }

   private static String itemQuery(ItemStack stack) {
      return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
   }

   private void failCurrentPhase(String reason) {
      this.rotation.onFailedCycle(this.targetAnarchy);
      this.scanner.markFailed(this.targetChest, System.currentTimeMillis());
      this.cancelNavigation();
      this.closeOwnedContainer(mc.player);
      this.resetSellOperation();
      this.workflow.rotateAnarchy(this.tick);
      if (this.workflow.phase() != AutoWardenWorkflow.Phase.ENSURE_ANARCHY) {
         this.workflow.failSafe(reason, this.tick);
      }

      this.phaseChanged();
   }

   private void delayStorageRetry(String reason) {
      this.closeOwnedContainer(mc.player);
      this.storageRetryUntil = this.tick + 1200L;
      this.workflow.failSafe(reason, this.tick);
      ChatUtil.error(reason);
      this.notifyTelegram(reason);
      this.phaseChanged();
   }

   private void pathTo(BlockPos position, int radius) {
      if (!this.navigationActive) {
         this.navigator.begin(PveManagerFeature.INSTANCE.configureNavigation(NavigationOptions.walking()));
         this.navigationActive = true;
      }

      this.navigator.pathTo(position, radius);
   }

   private void cancelNavigation() {
      if (this.navigationActive) {
         this.navigator.cancel();
      }
   }

   private void endNavigation() {
      if (this.navigationActive) {
         this.navigator.end();
         this.navigationActive = false;
      }
   }

   private void startPhaseAction() {
      this.phaseActionStarted = true;
      this.phaseActionTick = this.tick;
   }

   private boolean phaseActionElapsed(long ticks) {
      return this.phaseActionStarted && this.tick - this.phaseActionTick >= Math.max(0L, ticks);
   }

   private boolean actionReady() {
      return this.tick - this.phaseActionTick >= 4L;
   }

   private long commandSettleTicks() {
      return Math.max(80, Math.round(this.teleportCooldown.getValue().floatValue() * 20.0F));
   }

   private void phaseChanged() {
      this.phaseActionStarted = false;
      this.phaseSubstep = 0;
      this.phaseActionTick = this.tick;
      this.lastContainerOpenTick = -4611686018427387904L;
      this.activeStorageChest = null;
      this.stats.phase(this.workflow.phase(), this.workflow.lastReason());
   }

   private void updateStatsPhase() {
      this.stats.phase(this.workflow.phase(), this.workflow.lastReason());
   }

   private void updateCurrentAnarchy() {
      OptionalInt parsed = AutoWardenParsers.parseAnarchy(ServerUiText.tabHeader(mc));
      parsed.ifPresent(value -> this.currentAnarchy = value);
   }

   private void rememberServer(Minecraft client) {
      ServerData server = client == null ? null : client.getCurrentServer();
      if (server != null && !server.isLan() && !server.isRealm()) {
         this.reconnectServer = server;
      }
   }

   private void scheduleReconnect() {
      this.reconnectAtMillis = System.currentTimeMillis() + Math.round(this.autoReconnectDelay.getValue() * 1000.0);
      this.reconnectAttempted = false;
   }

   private void tickReconnect(Minecraft client) {
      if (this.isEnabled()
         && this.autoReconnect.getValue()
         && this.reconnectServer != null
         && this.reconnectAtMillis != 0L
         && !this.reconnectAttempted
         && System.currentTimeMillis() >= this.reconnectAtMillis) {
         this.reconnectAttempted = true;
         ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(this.reconnectServer.ip), this.reconnectServer, false, null);
      }
   }

   private boolean ensureResources() {
      if (this.resourcesHeld) {
         return true;
      }

      this.resourcesHeld = PveAutomationCoordinator.INSTANCE
         .acquire(
            this,
            AutomationPriority.BOT,
            Set.of(
               AutomationResource.MOVEMENT,
               AutomationResource.ROTATION,
               AutomationResource.INVENTORY,
               AutomationResource.SCREEN,
               AutomationResource.CHAT,
               AutomationResource.NAVIGATION
            )
         );
      return this.resourcesHeld;
   }

   private void connectTelegram() {
      this.ensureTelegram()
         .reloadAndVerify()
         .thenAccept(status -> Minecraft.getInstance().execute(() -> ChatUtil.info("Telegram: " + status.name().toLowerCase(Locale.ROOT))));
   }

   private void notifyTelegram(String message) {
      if (this.telegramNotifications.getValue()) {
         this.ensureTelegram().send(message);
      }
   }

   private TelegramNotifier ensureTelegram() {
      if (this.telegram == null) {
         this.telegram = new TelegramNotifier(Minecraft.getInstance().gameDirectory.toPath());
      }

      return this.telegram;
   }

   private void printScoutRecommendation() {
      if (this.configuredLootAnarchies.isEmpty()) {
         ChatUtil.error("Configure loot anarchies first");
      } else {
         long now = System.currentTimeMillis();
         int best = this.configuredLootAnarchies
            .stream()
            .max(Comparator.comparingDouble(value -> this.rotation.score(value, this.crowdPenalty.getValue(), now)))
            .orElse(-1);
         ChatUtil.info(best < 0 ? "No Warden city is currently available" : "Recommended Warden city: anarchy " + best);
         this.lastScoutTick = this.tick;
      }
   }

   private Optional<String> configuredHomeCommand() {
      return this.contract.home(PveManagerFeature.INSTANCE.resolvedHomeName());
   }

   private ServerAdapter contractAdapter() {
      return ServerAdapters.current();
   }

   private void resetRuntime() {
      this.tick = 0L;
      this.phaseActionTick = 0L;
      this.reconnectAtMillis = 0L;
      this.combatHoldUntilMillis = 0L;
      this.lastContainerOpenTick = -4611686018427387904L;
      this.lastScoutTick = 0L;
      this.storageRetryUntil = 0L;
      this.phaseActionStarted = false;
      this.phaseSubstep = 0;
      this.reconnectAttempted = false;
      this.currentAnarchy = -1;
      this.targetAnarchy = -1;
      this.lastAttacker = null;
      this.cityCenter = null;
      this.targetChest = null;
      this.targetObservation = null;
      this.storageCenter = null;
      this.activeStorageChest = null;
      this.patrolIndex = 0;
      this.cycleLoot = 0;
      this.depositedThisCycle = 0;
      this.wasDead = false;
      this.reportedAttackers.clear();
      this.scanner.clearWorld();
      this.rotation.reset();
      this.workflow.reset(0L);
      this.resetSellOperation();
   }

   private void cleanupRuntime(boolean closeTelegram) {
      this.endNavigation();
      this.closeOwnedContainer(mc.player);
      this.resourcesHeld = false;
      this.workflow.reset(this.tick);
      this.scanner.clearWorld();
      this.resetSellOperation();
      if (closeTelegram && this.telegram != null) {
         this.telegram.close();
         this.telegram = null;
      }
   }

   private record ZoneGizmo(AABB box) implements Gizmo {
      public void emit(GizmoPrimitives primitives, float alpha) {
         Vec3 a = new Vec3(this.box.minX, this.box.minY, this.box.minZ);
         Vec3 b = new Vec3(this.box.minX, this.box.minY, this.box.maxZ);
         Vec3 c = new Vec3(this.box.minX, this.box.maxY, this.box.minZ);
         Vec3 d = new Vec3(this.box.minX, this.box.maxY, this.box.maxZ);
         Vec3 e = new Vec3(this.box.maxX, this.box.minY, this.box.minZ);
         Vec3 f = new Vec3(this.box.maxX, this.box.minY, this.box.maxZ);
         Vec3 g = new Vec3(this.box.maxX, this.box.maxY, this.box.minZ);
         Vec3 h = new Vec3(this.box.maxX, this.box.maxY, this.box.maxZ);
         primitives.addLine(a, b, -871038977, 1.4F);
         primitives.addLine(a, c, -871038977, 1.4F);
         primitives.addLine(a, e, -871038977, 1.4F);
         primitives.addLine(b, d, -871038977, 1.4F);
         primitives.addLine(b, f, -871038977, 1.4F);
         primitives.addLine(c, d, -871038977, 1.4F);
         primitives.addLine(c, g, -871038977, 1.4F);
         primitives.addLine(e, f, -871038977, 1.4F);
         primitives.addLine(e, g, -871038977, 1.4F);
         primitives.addLine(f, h, -871038977, 1.4F);
         primitives.addLine(d, h, -871038977, 1.4F);
         primitives.addLine(g, h, -871038977, 1.4F);
      }
   }
}
