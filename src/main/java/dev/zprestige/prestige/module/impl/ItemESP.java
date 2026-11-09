package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.HashSet;
import java.util.Set;

/** Prestige ItemESP: Highlights dropped items with glowing outline. */
public class ItemESP extends Module {
	private final Set<ItemEntity> glowing = new HashSet<>();

	public ItemESP() {
		super("Item ESP", "Highlights dropped items on the ground", Category.RENDER);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) return;
		var box = mc.player.getBoundingBox().inflate(32);
		glowing.removeIf(e -> {
			if (!e.isAlive() || !e.getBoundingBox().intersects(box)) {
				e.setGlowingTag(false);
				return true;
			}
			return false;
		});
		for (var entity : mc.level.getEntitiesOfClass(ItemEntity.class, box)) {
			if (glowing.add(entity)) entity.setGlowingTag(true);
		}
	}

	@Override
	protected void onDisable() {
		glowing.forEach(e -> e.setGlowingTag(false));
		glowing.clear();
	}
}
