package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.NumberSetting;

/** Prestige Hitboxes: Expands enemy hitboxes for easier attacks. */
public class Hitboxes extends Module {
	public static NumberSetting expand;

	public Hitboxes() {
		super("Hitboxes", "Expands enemy hitboxes", Category.COMBAT);
		expand = number("Expand", 0.3, 0.1, 1.5, 0.1);
	}

	public static double getExpansion() {
		return expand != null ? expand.get() : 0.3;
	}
}
