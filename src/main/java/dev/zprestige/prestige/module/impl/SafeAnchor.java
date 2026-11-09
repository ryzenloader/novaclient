package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige SafeAnchor: Cancels anchor explosion if player is in blast radius */
public class SafeAnchor extends Module {
	public SafeAnchor() {
		super("SafeAnchor", "Cancels anchor explosion if player is in blast radius", Category.COMBAT);
	}
}
