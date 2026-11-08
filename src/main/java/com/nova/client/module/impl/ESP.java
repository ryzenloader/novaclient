package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.BoolSetting;
import com.nova.client.setting.NumberSetting;
import com.nova.client.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;

/** Highlights entities through walls using the vanilla glow outline (client-side only). */
public class ESP extends Module {
	private final BoolSetting players = bool("Players", true);
	private final BoolSetting mobs = bool("Mobs", false);
	private final NumberSetting range = number("Range", 64, 16, 128, 8);
	private final Set<Entity> glowing = new HashSet<>();

	public ESP() {
		super("ESP", "Highlights entities through walls", Category.RENDER);
	}

	@Override
	public void onTick() {
		var me = mc.player;
		var box = me.getBoundingBox().inflate(range.get());

		glowing.removeIf(e -> {
			if (!e.isAlive() || !e.getBoundingBox().intersects(box) || !wanted(e)) {
				e.setGlowingTag(false);
				return true;
			}
			return false;
		});
		for (Entity e : mc.level.getEntities(me, box)) {
			if (wanted(e) && glowing.add(e)) e.setGlowingTag(true);
		}
	}

	private boolean wanted(Entity e) {
		if (!(e instanceof LivingEntity) || e == mc.player) return false;
		return (players.get() && e instanceof Player) || (mobs.get() && e instanceof Mob);
	}

	@Override
	protected void onDisable() {
		glowing.forEach(e -> e.setGlowingTag(false));
		glowing.clear();
	}
}
