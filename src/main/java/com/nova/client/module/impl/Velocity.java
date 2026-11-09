package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;

/** Prestige Velocity: Reduces or negates knockback. */
public class Velocity extends Module {
	public static NumberSetting horizontal;
	public static NumberSetting vertical;

	public Velocity() {
		super("Velocity", "Reduces incoming knockback", Category.COMBAT);
		horizontal = number("Horizontal %", 0, 0, 100, 5);
		vertical = number("Vertical %", 0, 0, 100, 5);
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		if (mc.player.hurtTime == 9 || mc.player.hurtTime == 8) {
			double h = horizontal.get() / 100.0;
			double v = vertical.get() / 100.0;
			Vec3 motion = mc.player.getDeltaMovement();
			mc.player.setDeltaMovement(motion.x * h, motion.y * v, motion.z * h);
		}
	}
}
