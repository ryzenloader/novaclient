package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.NumberSetting;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ContainerInput;

/** Quick-moves every item out of an open chest. */
public class ChestStealer extends Module {
	private final NumberSetting delay = number("Delay (ticks)", 2, 0, 10, 1);
	private int cooldown;

	public ChestStealer() {
		super("Chest Stealer", "Takes everything from open chests", Category.PLAYER);
	}

	@Override
	public void onTick() {
		if (!(mc.screen instanceof ContainerScreen screen)) return;
		if (cooldown-- > 0) return;

		var menu = screen.getMenu();
		int containerSlots = menu.slots.size() - 36;
		for (int i = 0; i < containerSlots; i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) {
				mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
				cooldown = delay.getInt();
				return;
			}
		}
	}
}
