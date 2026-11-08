package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Lets you walk up taller blocks without jumping. */
public class Step extends Module {
	private static final double DEFAULT_HEIGHT = 0.6;
	private final NumberSetting height = number("Height", 1.0, 0.6, 2.5, 0.1);

	public Step() {
		super("Step", "Step up taller blocks", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		var attribute = mc.player.getAttribute(Attributes.STEP_HEIGHT);
		if (attribute != null) attribute.setBaseValue(height.get());
	}

	@Override
	protected void onDisable() {
		if (mc.player == null) return;
		var attribute = mc.player.getAttribute(Attributes.STEP_HEIGHT);
		if (attribute != null) attribute.setBaseValue(DEFAULT_HEIGHT);
	}
}
