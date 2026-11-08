package com.nova.client.module.impl;

import com.nova.client.module.Module;
import net.minecraft.world.inventory.ClickType;
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
				mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 40, ClickType.SWAP, p);
				return;
			}
		}
	}
}
