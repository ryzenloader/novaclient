package com.nova.client.module.impl;

import com.nova.client.module.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Prestige NoFall: Cancels fall damage packets. */
public class NoFall extends Module {
	public NoFall() {
		super("No Fall", "Cancels fall damage", Category.PLAYER);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.player.fallDistance <= 2.5f) return;
		if (mc.getConnection() != null) {
			try {
				mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
				mc.player.resetFallDistance();
			} catch (Throwable ignored) {}
		}
	}
}
