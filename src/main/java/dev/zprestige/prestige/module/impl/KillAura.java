package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.Prestige;
import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.BoolSetting;
import dev.zprestige.prestige.setting.ModeSetting;
import dev.zprestige.prestige.setting.NumberSetting;
import dev.zprestige.prestige.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;

/** Prestige KillAura with AntiBot, Hitboxes, and WTap integration. */
public class KillAura extends Module {
	private final NumberSetting range = number("Range", 3.2, 2.0, 6.0, 0.1);
	private final ModeSetting targets = mode("Targets", "Players", "Players", "Mobs", "All");
	private final ModeSetting sort = mode("Sort", "Distance", "Distance", "Health");
	private final BoolSetting requireVisible = bool("Require line of sight", true);
	private final BoolSetting swing = bool("Swing", true);

	public KillAura() {
		super("Kill Aura", "Attacks nearby targets for you", Category.COMBAT);
	}

	@Override
	public void onTick() {
		var player = mc.player;
		if (mc.screen != null || player == null || mc.level == null) return;
		if (player.getAttackStrengthScale(0f) < 0.95f) return;

		double maxReach = range.get();
		if (Prestige.modules != null && Prestige.modules.get("Hitboxes").map(Module::isEnabled).orElse(false)) {
			maxReach += Hitboxes.getExpansion();
		}

		var box = player.getBoundingBox().inflate(maxReach + 1.0);
		final double finalReach = maxReach;
		Comparator<Entity> order = sort.is("Health")
			? Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth())
			: Comparator.comparingDouble(e -> Util.distanceToBox(player, e));

		Entity target = mc.level.getEntities(player, box).stream()
			.filter(e -> Util.validTarget(player, e, targets.get()))
			.filter(e -> !AntiBot.isBot(e))
			.filter(e -> Util.distanceToBox(player, e) <= finalReach)
			.filter(e -> !requireVisible.get() || player.hasLineOfSight(e))
			.min(order)
			.orElse(null);

		if (target == null) return;
		mc.gameMode.attack(player, target);
		if (swing.get()) player.swing(InteractionHand.MAIN_HAND);

		// Prestige WTap sync
		if (Prestige.modules != null) {
			Prestige.modules.get("W-Tap").ifPresent(m -> {
				if (m.isEnabled() && m instanceof WTap wtap) wtap.onAttack();
			});
		}
	}
}
