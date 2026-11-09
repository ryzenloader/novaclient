package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/** Keeps a Totem of Undying in your offhand. */
public class AutoTotem extends Module {
	public AutoTotem() {
		super("Auto Totem", "Keeps a totem in your offhand", Category.COMBAT);
	}

	@Override
	public void onTick() {
		var p = mc.player;
		if (mc.screen != null) return;
		if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;

		var menu = p.inventoryMenu;
		for (int slot = 9; slot <= 44; slot++) {
			if (menu.getSlot(slot).getItem().is(Items.TOTEM_OF_UNDYING)) {
				mc.gameMode.handleContainerInput(menu.containerId, slot, 40, ContainerInput.SWAP, p);
				return;
			}
		}
	}
}
