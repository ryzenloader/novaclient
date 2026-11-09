package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige Backtrack: Delays enemy hitboxes to attack previous positions */
public class Backtrack extends Module {
	public Backtrack() {
		super("Backtrack", "Delays enemy hitboxes to attack previous positions", Category.COMBAT);
	}
}
