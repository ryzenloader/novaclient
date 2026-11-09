package com.nova.client.module;

import com.nova.client.setting.BoolSetting;
import com.nova.client.setting.ModeSetting;
import com.nova.client.setting.NumberSetting;
import com.nova.client.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	protected static final Minecraft mc = Minecraft.getInstance();

	public enum Category {
		COMBAT("Combat"), MOVEMENT("Movement"), PLAYER("Player"), RENDER("Visual");

		public final String label;
		Category(String label) { this.label = label; }
	}

	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting> settings = new ArrayList<>();
	private boolean enabled;

	/** Managed by ModuleManager: true once onEnable has run for the current world. */
	boolean started;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	public String getName() { return name; }
	public String getDescription() { return description; }
	public Category getCategory() { return category; }
	public List<Setting> getSettings() { return Collections.unmodifiableList(settings); }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean value) {
		if (enabled != value) {
			enabled = value;
			com.nova.client.gui.Hud.toast(name, enabled);
		}
	}
	public void toggle() {
		setEnabled(!enabled);
	}

	protected BoolSetting bool(String name, boolean def) {
		BoolSetting s = new BoolSetting(name, def);
		settings.add(s);
		return s;
	}

	protected NumberSetting number(String name, double def, double min, double max, double step) {
		NumberSetting s = new NumberSetting(name, def, min, max, step);
		settings.add(s);
		return s;
	}

	protected ModeSetting mode(String name, String def, String... modes) {
		ModeSetting s = new ModeSetting(name, def, modes);
		settings.add(s);
		return s;
	}

	protected void onEnable() {}
	protected void onDisable() {}

	/** Called every client tick while enabled and a world is loaded. */
	public void onTick() {}
}
