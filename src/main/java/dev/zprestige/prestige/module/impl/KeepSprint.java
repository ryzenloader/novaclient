package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige KeepSprint: Keeps sprint active during attacks. */
public class KeepSprint extends Module {
	public KeepSprint() {
		super("Keep Sprint", "Maintains sprint momentum on attack", Category.COMBAT);
	}

	@Override
	public void onTick() {
		if (mc.player != null && mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.horizontalCollision) {
			mc.player.setSprinting(true);
		}
	}
}
