package com.nova.client.module.impl;

import com.nova.client.module.Module;
import net.minecraft.client.gui.screens.DeathScreen;

public class AutoRespawn extends Module {
	public AutoRespawn() {
		super("Auto Respawn", "Respawns instantly when you die", Category.PLAYER);
	}

	@Override
	public void onTick() {
		if (mc.screen instanceof DeathScreen) mc.player.respawn();
	}
}
