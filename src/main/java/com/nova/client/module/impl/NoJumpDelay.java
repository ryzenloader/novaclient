package com.nova.client.module.impl;

import com.nova.client.module.Module;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;

/** Prestige NoJumpDelay: Removes jump cooldown between bunnyhops. */
public class NoJumpDelay extends Module {
	private static Field jumpDelayField;

	public NoJumpDelay() {
		super("No Jump Delay", "Removes jump cooldown between bunnyhops", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		try {
			if (jumpDelayField == null) {
				for (Field f : LivingEntity.class.getDeclaredFields()) {
					if (f.getType() == int.class && (f.getName().equals("noJumpDelay") || f.getName().contains("jumpDelay"))) {
						jumpDelayField = f;
						jumpDelayField.setAccessible(true);
						break;
					}
				}
			}
			if (jumpDelayField != null) {
				jumpDelayField.setInt(mc.player, 0);
			}
		} catch (Exception ignored) {}
	}
}
