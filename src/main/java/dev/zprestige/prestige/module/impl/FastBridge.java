package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.BoolSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;

/**
 * Automatically sneaks at the edge of blocks so you can fast-bridge (ninja-bridge)
 * without falling off, and releases sneak when a solid block is under you.
 */
public class FastBridge extends Module {
	private final BoolSetting blocksOnly = bool("Blocks only", true);
	private boolean sneaking;

	public FastBridge() {
		super("FastBridge", "Auto-sneaks at block edges for fast bridging", Category.MOVEMENT);
	}

	@Override
	protected void onDisable() {
		if (sneaking && mc.options != null) {
			mc.options.keyShift.setDown(false);
			sneaking = false;
		}
	}

	@Override
	public void onTick() {
		var p = mc.player;
		if (p == null || mc.level == null || mc.screen != null) return;

		if (blocksOnly.get()) {
			boolean holdingBlock = p.getMainHandItem().getItem() instanceof BlockItem
				|| p.getOffhandItem().getItem() instanceof BlockItem;
			if (!holdingBlock) {
				if (sneaking) {
					mc.options.keyShift.setDown(false);
					sneaking = false;
				}
				return;
			}
		}

		if (p.onGround()) {
			BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
			boolean airBelow = mc.level.getBlockState(below).isAir();
			if (airBelow) {
				mc.options.keyShift.setDown(true);
				sneaking = true;
			} else if (sneaking) {
				mc.options.keyShift.setDown(false);
				sneaking = false;
			}
		}
	}
}
