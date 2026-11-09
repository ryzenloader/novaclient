package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige NoMissDelay: Removes cooldown on missed attacks */
public class NoMissDelay extends Module {
	public NoMissDelay() {
		super("NoMissDelay", "Removes cooldown on missed attacks", Category.COMBAT);
	}
}
