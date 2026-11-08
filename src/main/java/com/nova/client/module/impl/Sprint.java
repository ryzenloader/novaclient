package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.BoolSetting;

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
