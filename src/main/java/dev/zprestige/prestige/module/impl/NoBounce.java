package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige NoBounce: Removes camera bounce on damage */
public class NoBounce extends Module {
	public NoBounce() {
		super("NoBounce", "Removes camera bounce on damage", Category.RENDER);
	}
}
