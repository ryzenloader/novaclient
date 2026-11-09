package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.BoolSetting;

public class Sprint extends Module {
	private final BoolSetting whileSneaking = bool("Allow while using items", false);

	public Sprint() {
		super("Sprint", "Sprints automatically while moving forward", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		var p = mc.player;
		if (p.zza <= 0 || p.horizontalCollision) return;
		if (p.isUsingItem() && !whileSneaking.get()) return;
		p.setSprinting(true);
	}
}
