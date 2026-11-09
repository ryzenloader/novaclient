package win.furryhook.feature;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.event.events.lifecycle.ShutdownEvent;
import win.furryhook.feature.impl.combat.AntiBotFeature;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.impl.combat.AutoClickerFeature;
import win.furryhook.feature.impl.combat.AutoExplosionFeature;
import win.furryhook.feature.impl.combat.AutoTotemFeature;
import win.furryhook.feature.impl.combat.AutoWebFeature;
import win.furryhook.feature.impl.combat.BowAimbotFeature;
import win.furryhook.feature.impl.combat.CrystalAuraFeature;
import win.furryhook.feature.impl.combat.CrystalTapFeature;
import win.furryhook.feature.impl.combat.ElytraResolverFeature;
import win.furryhook.feature.impl.combat.HitBoxesFeature;
import win.furryhook.feature.impl.combat.HitSoundFeature;
import win.furryhook.feature.impl.combat.TrashTalkFeature;
import win.furryhook.feature.impl.combat.TriggerBotFeature;
import win.furryhook.feature.impl.combat.VelocityFeature;
import win.furryhook.feature.impl.misc.AuctionHelperFeature;
import win.furryhook.feature.impl.misc.AutoTpaAcceptFeature;
import win.furryhook.feature.impl.misc.ChestStealerFeature;
import win.furryhook.feature.impl.misc.DeathCoordsFeature;
import win.furryhook.feature.impl.misc.NameProtectFeature;
import win.furryhook.feature.impl.misc.ScoreboardHealthFeature;
import win.furryhook.feature.impl.misc.ServerHelperFeature;
import win.furryhook.feature.impl.misc.XCarryFeature;
import win.furryhook.feature.impl.movement.AirStuckFeature;
import win.furryhook.feature.impl.movement.AutoJumpFeature;
import win.furryhook.feature.impl.movement.ElytraBoostFeature;
import win.furryhook.feature.impl.movement.ElytraMotionFeature;
import win.furryhook.feature.impl.movement.FreecamFeature;
import win.furryhook.feature.impl.movement.GrimCollideFeature;
import win.furryhook.feature.impl.movement.GrimElytraFeature;
import win.furryhook.feature.impl.movement.GrimFlyFeature;
import win.furryhook.feature.impl.movement.InventoryMoveFeature;
import win.furryhook.feature.impl.movement.NoPushFeature;
import win.furryhook.feature.impl.movement.NoSlowFeature;
import win.furryhook.feature.impl.movement.ParkourFeature;
import win.furryhook.feature.impl.movement.SafeWalkFeature;
import win.furryhook.feature.impl.movement.SpeedFeature;
import win.furryhook.feature.impl.movement.SprintFeature;
import win.furryhook.feature.impl.movement.StrafeFeature;
import win.furryhook.feature.impl.movement.TimerFeature;
import win.furryhook.feature.impl.movement.WallClimbFeature;
import win.furryhook.feature.impl.movement.WaterSpeedFeature;
import win.furryhook.feature.impl.player.AutoRespawnFeature;
import win.furryhook.feature.impl.player.AutoSwapFeature;
import win.furryhook.feature.impl.player.AutoToolFeature;
import win.furryhook.feature.impl.player.ClickPearlFeature;
import win.furryhook.feature.impl.player.ElytraHelperFeature;
import win.furryhook.feature.impl.player.FakePlayerFeature;
import win.furryhook.feature.impl.player.FullBrightFeature;
import win.furryhook.feature.impl.player.MultiActionFeature;
import win.furryhook.feature.impl.player.NoDelayFeature;
import win.furryhook.feature.impl.player.NoJumpBoostFeature;
import win.furryhook.feature.impl.player.WindHopFeature;
import win.furryhook.feature.impl.pve.AntiAfkFeature;
import win.furryhook.feature.impl.pve.AppleFarmerFeature;
import win.furryhook.feature.impl.pve.AuctionRelistFeature;
import win.furryhook.feature.impl.pve.AutoArmorFeature;
import win.furryhook.feature.impl.pve.AutoAuthFeature;
import win.furryhook.feature.impl.pve.AutoCrafterFeature;
import win.furryhook.feature.impl.pve.AutoFishFeature;
import win.furryhook.feature.impl.pve.AutoGappleFeature;
import win.furryhook.feature.impl.pve.AutoLeaveFeature;
import win.furryhook.feature.impl.pve.AutoMineFeature;
import win.furryhook.feature.impl.pve.AutoPotionFeature;
import win.furryhook.feature.impl.pve.AutoTpLootFeature;
import win.furryhook.feature.impl.pve.AutoTradeFeature;
import win.furryhook.feature.impl.pve.AutoUseFeature;
import win.furryhook.feature.impl.pve.BaseFinderFeature;
import win.furryhook.feature.impl.pve.ClanInvestFeature;
import win.furryhook.feature.impl.pve.ClanUpgradeFeature;
import win.furryhook.feature.impl.pve.CreeperFarmFeature;
import win.furryhook.feature.impl.pve.GriefJoinerFeature;
import win.furryhook.feature.impl.pve.MineHelperFeature;
import win.furryhook.feature.impl.pve.NukerFeature;
import win.furryhook.feature.impl.pve.PveManagerFeature;
import win.furryhook.feature.impl.pve.SynchronizationFeature;
import win.furryhook.feature.impl.pve.autowarden.AutoWardenFeature;
import win.furryhook.feature.impl.visual.AmbienceFeature;
import win.furryhook.feature.impl.visual.AnimationsFeature;
import win.furryhook.feature.impl.visual.ArrowsFeature;
import win.furryhook.feature.impl.visual.AtmosphereFeature;
import win.furryhook.feature.impl.visual.CapeFeature;
import win.furryhook.feature.impl.visual.ChamsFeature;
import win.furryhook.feature.impl.visual.CrosshairFeature;
import win.furryhook.feature.impl.visual.CrystalOptimizerFeature;
import win.furryhook.feature.impl.visual.EmotionsFeature;
import win.furryhook.feature.impl.visual.EntityEspFeature;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.feature.impl.visual.ItemPhysicsFeature;
import win.furryhook.feature.impl.visual.ItemReplacerFeature;
import win.furryhook.feature.impl.visual.JumpCirclesFeature;
import win.furryhook.feature.impl.visual.LineGlyphesFeature;
import win.furryhook.feature.impl.visual.NameTagsFeature;
import win.furryhook.feature.impl.visual.ParticlesFeature;
import win.furryhook.feature.impl.visual.RemovalsFeature;
import win.furryhook.feature.impl.visual.RtxFeature;
import win.furryhook.feature.impl.visual.ShaderHandsFeature;
import win.furryhook.feature.impl.visual.SwingAnimationFeature;
import win.furryhook.feature.impl.visual.TrailsFeature;
import win.furryhook.feature.impl.visual.TrajectoriesFeature;
import win.furryhook.feature.impl.visual.ViewModelFeature;
import win.furryhook.feature.impl.visual.WorldTweaksFeature;
import win.furryhook.feature.setting.BindSetting;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.feature.setting.Setting;
import win.furryhook.utils.InputGuard;

public final class FeatureManager {
   public static final FeatureManager INSTANCE = new FeatureManager();
   private final Map<String, Feature> features = new LinkedHashMap<>();
   private final Map<Class<? extends Feature>, Feature> featuresByType = new ConcurrentHashMap<>();
   private final FeatureConfigStore configStore = new FeatureConfigStore();
   private final ScheduledExecutorService configWriter = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "Blade Config Writer");
      thread.setDaemon(true);
      return thread;
   });
   private final Object saveLock = new Object();
   private final Map<Feature, Set<Integer>> activeHoldBinds = new IdentityHashMap<>();
   private ScheduledFuture<?> pendingSave;
   private boolean initialized;

   private FeatureManager() {
   }

   public void initialize() {
      if (!this.initialized) {
         List.of(
               new SprintFeature(),
               new AutoJumpFeature(),
               new GrimFlyFeature(),
               new InventoryMoveFeature(),
               new NoSlowFeature(),
               new NoPushFeature(),
               new SafeWalkFeature(),
               new WallClimbFeature(),
               new WaterSpeedFeature(),
               new ParkourFeature(),
               new TimerFeature(),
               new SpeedFeature(),
               new StrafeFeature(),
               new FreecamFeature(),
               new GrimCollideFeature(),
               new GrimElytraFeature(),
               new AirStuckFeature(),
               new ElytraBoostFeature(),
               new ElytraHelperFeature(),
               new ElytraMotionFeature(),
               new AuraFeature(),
               new AntiBotFeature(),
               new ElytraResolverFeature(),
               new TriggerBotFeature(),
               new AutoWebFeature(),
               new CrystalAuraFeature(),
               new CrystalTapFeature(),
               new AutoTotemFeature(),
               new AutoExplosionFeature(),
               new AutoClickerFeature(),
               new HitBoxesFeature(),
               new HitSoundFeature(),
               new TrashTalkFeature(),
               new VelocityFeature(),
               new BowAimbotFeature(),
               new HudFeature(),
               new ArrowsFeature(),
               new EntityEspFeature(),
               new NameTagsFeature(),
               new ParticlesFeature(),
               new TrailsFeature(),
               new LineGlyphesFeature(),
               new JumpCirclesFeature(),
               new TrajectoriesFeature(),
               new RemovalsFeature(),
               new CrosshairFeature(),
               new CrystalOptimizerFeature(),
               new SwingAnimationFeature(),
               new ViewModelFeature(),
               new ShaderHandsFeature(),
               new CapeFeature(),
               new EmotionsFeature(),
               new ChamsFeature(),
               new ItemPhysicsFeature(),
               new ItemReplacerFeature(),
               new WorldTweaksFeature(),
               new AmbienceFeature(),
               new AtmosphereFeature(),
               new RtxFeature(),
               new AnimationsFeature(),
               new NameProtectFeature(),
               new DeathCoordsFeature(),
               new ScoreboardHealthFeature(),
               new XCarryFeature(),
               new AuctionHelperFeature(),
               new ServerHelperFeature(),
               new AutoTpaAcceptFeature(),
               new ChestStealerFeature(),
               new AutoSwapFeature(),
               new AutoRespawnFeature(),
               new WindHopFeature(),
               new FakePlayerFeature(),
               new FullBrightFeature(),
               new NoJumpBoostFeature(),
               new AutoToolFeature(),
               new ClickPearlFeature(),
               new MultiActionFeature(),
               new NoDelayFeature(),
               PveManagerFeature.INSTANCE,
               new AutoFishFeature(),
               new AutoArmorFeature(),
               new AutoPotionFeature(),
               new AutoUseFeature(),
               new AutoGappleFeature(),
               new AntiAfkFeature(),
               new AutoLeaveFeature(),
               new AutoAuthFeature(),
               new NukerFeature(),
               new MineHelperFeature(),
               new AutoTpLootFeature(),
               new AutoMineFeature(),
               new BaseFinderFeature(),
               new AppleFarmerFeature(),
               new CreeperFarmFeature(),
               new AutoCrafterFeature(),
               new AutoTradeFeature(),
               new AuctionRelistFeature(),
               new ClanInvestFeature(),
               new ClanUpgradeFeature(),
               new GriefJoinerFeature(),
               new SynchronizationFeature(),
               new AutoWardenFeature()
            )
            .forEach(this::register);
         this.configStore.load(this);
         this.initialized = true;
         Runtime.getRuntime().addShutdownHook(new Thread(this::flushPendingSave, "Blade Config Flush"));
      }
   }

   public void save() {
      if (this.initialized) {
         JsonObject snapshot = this.configStore.snapshot(this);
         synchronized (this.saveLock) {
            if (this.pendingSave != null) {
               this.pendingSave.cancel(false);
            }

            this.pendingSave = this.configWriter.schedule(() -> this.configStore.save(snapshot), 2L, TimeUnit.SECONDS);
         }
      }
   }

   private void flushPendingSave() {
      synchronized (this.saveLock) {
         if (this.pendingSave == null) {
            return;
         }

         this.pendingSave.cancel(false);
         this.pendingSave = null;
      }

      this.configStore.save(this.configStore.snapshot(this));
   }

   public boolean saveConfigAs(String name) {
      return this.initialized && this.configStore.saveNamed(name, this.configStore.snapshot(this));
   }

   public boolean loadConfig(String name) {
      if (this.initialized && this.configStore.loadNamed(this, name)) {
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean deleteConfig(String name) {
      return this.configStore.deleteNamed(name);
   }

   public List<String> configNames() {
      return this.configStore.listNamed();
   }

   public void register(Feature feature) {
      String key = this.normalize(feature.getName());
      if (this.features.containsKey(key)) {
         throw new IllegalArgumentException("Duplicate feature name: " + feature.getName());
      }

      feature.setStateListener(this::save);
      this.features.put(key, feature);
      this.featuresByType.put((Class<? extends Feature>)feature.getClass(), feature);
   }

   public Collection<Feature> getFeatures() {
      return Collections.unmodifiableCollection(this.features.values());
   }

   public List<Feature> getFeatures(FeatureCategory category) {
      List<Feature> result = new ArrayList<>();

      for (Feature feature : this.features.values()) {
         if (feature.getCategory() == category) {
            result.add(feature);
         }
      }

      return result;
   }

   public Feature getFeature(String name) {
      return this.features.get(this.normalize(name));
   }

   public <T extends Feature> T getFeature(Class<T> type) {
      return type.cast(this.featuresByType.get(type));
   }

   public <T extends Feature> T getEnabled(Class<T> type) {
      T feature = this.getFeature(type);
      return feature != null && feature.isEnabled() ? feature : null;
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() != 1 || !InputGuard.isBindingBlocked()) {
         this.dispatchBind(BindSetting.key(event.getKey()), event.getAction());
         this.dispatchBooleanSettingBind(BindSetting.key(event.getKey()), event.getAction());
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() != 1 || !InputGuard.isBindingBlocked()) {
         this.dispatchBind(BindSetting.mouse(event.getButton()), event.getAction());
         this.dispatchBooleanSettingBind(BindSetting.mouse(event.getButton()), event.getAction());
      }
   }

   @EventTarget
   public void onShutdown(ShutdownEvent event) {
      synchronized (this.saveLock) {
         if (this.pendingSave != null) {
            this.pendingSave.cancel(false);
            this.pendingSave = null;
         }
      }

      this.configStore.save(this.configStore.snapshot(this));
      this.configWriter.shutdown();
      this.initialized = false;
   }

   private String normalize(String value) {
      return value.toLowerCase(Locale.ROOT);
   }

   private void dispatchBind(int bindCode, int action) {
      if (action == 1 || action == 0) {
         for (Feature feature : this.features.values()) {
            if (feature.supportsBinds() && feature.isToggleable() && feature.getBind().matchesCode(bindCode)) {
               this.handleBindInput(feature, bindCode, action);
            }
         }
      }
   }

   private void dispatchBooleanSettingBind(int bindCode, int action) {
      if (action == 1) {
         for (Feature feature : this.features.values()) {
            for (Setting<?> setting : feature.getSettings()) {
               if (setting instanceof BooleanSetting booleanSetting && booleanSetting.isBindable()) {
                  InputBindSetting inputBind = booleanSetting.getInputBind();
                  if (inputBind.isBound() && inputBind.getValue() == bindCode) {
                     booleanSetting.setValue(!booleanSetting.getValue());
                  }
               }
            }
         }
      }
   }

   private void handleBindInput(Feature feature, int bindCode, int action) {
      if (feature.getBindModeForCode(bindCode) == BindMode.HOLD) {
         Set<Integer> activeCodes = this.activeHoldBinds.computeIfAbsent(feature, ignored -> new LinkedHashSet<>());
         if (action == 1) {
            if (activeCodes.add(bindCode)) {
               feature.setEnabled(true);
            }
         } else {
            activeCodes.remove(bindCode);
            if (activeCodes.isEmpty()) {
               this.activeHoldBinds.remove(feature);
               feature.setEnabled(false);
            }
         }
      } else {
         if (action == 1) {
            feature.toggle();
         }
      }
   }
}
