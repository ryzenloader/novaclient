package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

/** Prestige FastPlace: Removes the right-click delay for fast placing blocks/items. */
public class FastPlace extends Module {
	public static NumberSetting delay;
	private static Field delayField;

	public FastPlace() {
		super("Fast Place", "Removes right-click placement delay", Category.PLAYER);
		delay = number("Delay Ticks", 0, 0, 4, 1);
	}

	@Override
	public void onTick() {
		try {
			if (delayField == null) {
				for (Field f : Minecraft.class.getDeclaredFields()) {
					if (f.getType() == int.class && (f.getName().equals("rightClickDelay") || f.getName().contains("rightClickDelay"))) {
						delayField = f;
						delayField.setAccessible(true);
						break;
					}
				}
			}
			if (delayField != null) {
				int target = (int) delay.get();
				if (delayField.getInt(mc) > target) {
					delayField.setInt(mc, target);
				}
			}
		} catch (Exception ignored) {}
	}
}
