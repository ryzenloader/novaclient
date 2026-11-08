package com.nova.client.setting;

public class BoolSetting extends Setting {
	private boolean value;

	public BoolSetting(String name, boolean def) {
		super(name);
		this.value = def;
	}

	public boolean get() { return value; }
	public void set(boolean v) { value = v; }
	public void toggle() { value = !value; }

	@Override public String display() { return value ? "ON" : "OFF"; }
	@Override public String serialize() { return Boolean.toString(value); }
	@Override public void deserialize(String text) { value = Boolean.parseBoolean(text); }
}
