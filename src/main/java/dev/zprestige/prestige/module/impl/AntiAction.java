package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige AntiAction: Prevents unsafe actions during combat */
public class AntiAction extends Module {
	public AntiAction() {
		super("AntiAction", "Prevents unsafe actions during combat", Category.COMBAT);
	}
}
