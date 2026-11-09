package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige WTap: Resets sprint on attack to maximize knockback dealt. */
public class WTap extends Module {
	private int ticks = 0;
	private boolean active = false;

	public WTap() {
		super("W-Tap", "Resets sprint on hit for extra knockback", Category.COMBAT);
	}

	public void onAttack() {
		if (!isEnabled() || mc.player == null) return;
		active = true;
		ticks = 0;
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		if (active) {
			ticks++;
			if (ticks == 1) {
				mc.player.setSprinting(false);
			} else if (ticks >= 2) {
				mc.player.setSprinting(true);
				active = false;
				ticks = 0;
			}
		}
	}
}
