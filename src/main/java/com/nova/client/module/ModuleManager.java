package com.nova.client.module;

import com.nova.client.module.impl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager() {
		// Combat
		register(new KillAura());
		register(new AimAssist());
		register(new TriggerBot());
		register(new AutoTotem());
		register(new AntiBot());
		register(new Velocity());
		register(new WTap());
		register(new Hitboxes());
		register(new KeepSprint());
		register(new AutoShield());

		// Movement
		register(new Sprint());
		register(new Fly());
		register(new Scaffold());
		register(new FastBridge());
		register(new Step());
		register(new NoJumpDelay());
		register(new Parkour());

		// Player
		register(new AutoTool());
		register(new ChestStealer());
		register(new AutoRespawn());
		register(new FastPlace());
		register(new NoFall());

		// Visual / Render
		register(new Fullbright());
		register(new ESP());
		register(new ItemESP());
		register(new HudModule());
	}

	private void register(Module module) { modules.add(module); }

	public List<Module> getModules() { return Collections.unmodifiableList(modules); }

	public List<Module> getModules(Module.Category category) {
		return modules.stream().filter(m -> m.getCategory() == category).toList();
	}

	public Optional<Module> get(String name) {
		return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst();
	}

	/** Runs enable/disable hooks when a module's state changed, then ticks enabled modules. */
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

	/** Called when joining a world so enabled modules run onEnable again with the new player. */
	public void reset() {
		for (Module m : modules) m.started = false;
	}
}
