package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.BoolSetting;
import com.nova.client.setting.ModeSetting;
import com.nova.client.setting.NumberSetting;
import com.nova.client.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;

public class KillAura extends Module {
	private final NumberSetting range = number("Range", 3.0, 2.0, 6.0, 0.1);
	private final ModeSetting targets = mode("Targets", "Players", "Players", "Mobs", "All");
	private final ModeSetting sort = mode("Sort", "Distance", "Distance", "Health");
	private final BoolSetting swing = bool("Swing", true);

	public KillAura() {
		super("Kill Aura", "Attacks nearby targets for you", Category.COMBAT);
	}

	@Override
	public void onTick() {
		var player = mc.player;
		if (mc.screen != null || player.getAttackStrengthScale(0f) < 0.95f) return;

		var box = player.getBoundingBox().inflate(range.get() + 1.0);
		Comparator<Entity> order = sort.is("Health")
			? Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth())
			: Comparator.comparingDouble(e -> Util.distanceToBox(player, e));

		Entity target = mc.level.getEntities(player, box).stream()
			.filter(e -> Util.validTarget(player, e, targets.get()))
			.filter(e -> Util.distanceToBox(player, e) <= range.get())
			.min(order)
			.orElse(null);

		if (target == null) return;
		mc.gameMode.attack(player, target);
		if (swing.get()) player.swing(InteractionHand.MAIN_HAND);
	}
}
