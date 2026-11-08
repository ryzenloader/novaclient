package com.nova.client.setting;

public abstract class Setting {
	public final String name;

	protected Setting(String name) {
		this.name = name;
	}

	/** Text shown in the GUI for the current value. */
	public abstract String display();

	public abstract String serialize();

	public abstract void deserialize(String text);
}
