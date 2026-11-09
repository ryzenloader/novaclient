package dev.zprestige.prestige.module;

import dev.zprestige.prestige.module.impl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager() {
		register(new AimAssist());
		register(new AnchorExploder());
		register(new AnchorMacro());
		register(new AnchorPearlCatch());
		register(new AnchorPlacer());
		register(new AntiAction());
		register(new AntiBot());
		register(new AntiWeb());
		register(new AutoAnchor());
		register(new AutoCart());
		register(new AutoCrystal());
		register(new AutoDrain());
		register(new AutoElytra());
		register(new AutoHitCrystal());
		register(new AutoObbyCrystal());
		register(new AutoSafeCart());
		register(new AutoShield());
		register(new AutoShieldBreaker());
		register(new AutoTotem());
		register(new Backtrack());
		register(new CartAura());
		register(new CartPearlCatch());
		register(new CobwebPlacer());
		register(new DoubleAnchor());
		register(new GhostPlace());
		register(new Hitboxes());
		register(new HitFix());
		register(new HitFlick());
		register(new InstantCrossbow());
		register(new InstantShield());
		register(new KeepSprint());
		register(new KillAura());
		register(new NoMissDelay());
		register(new PearlFollow());
		register(new PredictDoubleHand());
		register(new SafeAnchor());
		register(new SilentAim());
		register(new SwapHit());
		register(new TotemHit());
		register(new Triggerbot());
		register(new Velocity());
		register(new WTap());
		register(new XbowCart());
		register(new AutoMace());
		register(new AutoPearlCatch());
		register(new AutoReMace());
		register(new BreachSwap());
		register(new DiagonalPearlCatch());
		register(new ElytraWindCharge());
		register(new FastPearlCatch());
		register(new KeyWindCharge());
		register(new MacePot());
		register(new MacePrediction());
		register(new PearlWindCharge());
		register(new ShieldDrain());
		register(new ShortWindCharge());
		register(new WindChargeAura());
		register(new AutoJumpReset());
		register(new AutoShortPearl());
		register(new FakeLag());
		register(new FastBridge());
		register(new Fly());
		register(new Freecam());
		register(new NoJumpDelay());
		register(new Parkour());
		register(new Scaffold());
		register(new ShortPearl());
		register(new Sprint());
		register(new Step());
		register(new AntiTrap());
		register(new AutoArmor());
		register(new AutoClicker());
		register(new AutoLoot());
		register(new AutoPickaxe());
		register(new AutoPot());
		register(new AutoRespawn());
		register(new AutoTool());
		register(new ChestStealer());
		register(new Criticals());
		register(new CrystalOptimizer());
		register(new EnemyFaker());
		register(new FastEXP());
		register(new FastPlace());
		register(new NameHider());
		register(new NoFall());
		register(new Reach());
		register(new RightClicker());
		register(new StreamerMode());
		register(new Teams());
		register(new ExtendedReach());
		register(new InstantLunge());
		register(new SpearDamageIndicator());
		register(new SpearMaceDoubleTap());
		register(new Ambience());
		register(new Animations());
		register(new AntiResourcePack());
		register(new ESP());
		register(new FreeLook());
		register(new HealthIndicators());
		register(new HitEffect());
		register(new HudModule());
		register(new ItemESP());
		register(new JumpCircles());
		register(new NameTags());
		register(new NoBounce());
		register(new OreSimulation());
		register(new PearlTrajectory());
		register(new RenderOptimizer());
		register(new ShieldRenderer());
		register(new StorageESP());
		register(new TargetRenderer());
		register(new TotemAnimation());
		register(new Trails());
		register(new Trajectories());
		register(new TwoDESP());
		register(new Weather());
		register(new XRay());
	}

	private void register(Module module) { modules.add(module); }

	public List<Module> getModules() { return Collections.unmodifiableList(modules); }

	public List<Module> getModules(Module.Category category) {
		return modules.stream().filter(m -> m.getCategory() == category).toList();
	}

	public Optional<Module> get(String name) {
		return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst();
	}

	public void tick() {
		for (Module m : modules) {
			if (m.isEnabled() && !m.started) {
				m.started = true;
				m.onEnable();
			} else if (!m.isEnabled() && m.started) {
				m.started = false;
				m.onDisable();
			}
			if (m.isEnabled()) m.onTick();
		}
	}

	public void reset() {
		for (Module m : modules) m.started = false;
	}
}
