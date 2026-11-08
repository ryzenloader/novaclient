package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.NumberSetting;

/** Creative-style flight. Works in singleplayer and creative; most servers reset it. */
public class Fly extends Module {
	private static final float DEFAULT_SPEED = 0.05f;
	private final NumberSetting speed = number("Speed", 1.0, 0.5, 5.0, 0.5);

	public Fly() {
		super("Fly", "Creative-style flight", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		var abilities = mc.player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
		abilities.setFlyingSpeed(DEFAULT_SPEED * speed.getFloat());
	}

	@Override
	protected void onDisable() {
		if (mc.player == null) return;
		var p = mc.player;
		p.getAbilities().setFlyingSpeed(DEFAULT_SPEED);
		if (!p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
	}
}
