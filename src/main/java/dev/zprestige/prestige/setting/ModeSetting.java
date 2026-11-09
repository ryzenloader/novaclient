package dev.zprestige.prestige.setting;

import java.util.List;

public class ModeSetting extends Setting {
	private final List<String> modes;
	private int index;

	public ModeSetting(String name, String def, String... modes) {
		super(name);
		this.modes = List.of(modes);
		int i = this.modes.indexOf(def);
		this.index = Math.max(i, 0);
	}

	public String get() { return modes.get(index); }
	public boolean is(String mode) { return get().equalsIgnoreCase(mode); }
	public void cycle() { index = (index + 1) % modes.size(); }

	@Override public String display() { return get(); }
	@Override public String serialize() { return get(); }

	@Override
	public void deserialize(String text) {
		int i = modes.indexOf(text);
		if (i >= 0) index = i;
	}
}
