package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige FakeLag: Chokes movement packets to create fake lag */
public class FakeLag extends Module {
	public FakeLag() {
		super("FakeLag", "Chokes movement packets to create fake lag", Category.MOVEMENT);
	}
}
