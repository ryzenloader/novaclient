package com.nova.client.module.impl;

import com.nova.client.module.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;

/** Prestige AutoShield: Automatically blocks when opponents swing. */
public class AutoShield extends Module {
	public AutoShield() {
		super("Auto Shield", "Blocks automatically when facing enemies", Category.COMBAT);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) return;
		boolean hasShield = mc.player.getOffhandItem().is(Items.SHIELD) || mc.player.getMainHandItem().is(Items.SHIELD);
		if (!hasShield) return;

		boolean threat = mc.level.players().stream()
			.filter(p -> p != mc.player && p.isAlive())
			.anyMatch(p -> p.distanceTo(mc.player) <= 3.8 && p.swinging);

		if (threat) {
			InteractionHand hand = mc.player.getOffhandItem().is(Items.SHIELD) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			mc.gameMode.useItem(mc.player, hand);
		}
	}
}
