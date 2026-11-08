package com.nova.client.module.impl;

import com.nova.client.module.Module;
import net.minecraft.world.phys.BlockHitResult;

/** Switches to the fastest tool in your hotbar for the block you're mining. */
public class AutoTool extends Module {
	public AutoTool() {
		super("Auto Tool", "Picks the best tool for the block you mine", Category.PLAYER);
	}

	@Override
	public void onTick() {
		if (mc.screen != null || !mc.options.keyAttack.isDown()) return;
		if (!(mc.hitResult instanceof BlockHitResult hit)) return;

		var state = mc.level.getBlockState(hit.getBlockPos());
		if (state.isAir()) return;

		var inv = mc.player.getInventory();
		int best = inv.getSelectedSlot();
		float bestSpeed = inv.getItem(best).getDestroySpeed(state);
		for (int i = 0; i < 9; i++) {
			float speed = inv.getItem(i).getDestroySpeed(state);
			if (speed > bestSpeed) {
				best = i;
				bestSpeed = speed;
			}
		}
		if (best != inv.getSelectedSlot()) inv.setSelectedSlot(best);
	}
}
