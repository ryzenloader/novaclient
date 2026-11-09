package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.BoolSetting;
import dev.zprestige.prestige.setting.ModeSetting;
import dev.zprestige.prestige.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;

/** Attacks automatically when your crosshair is on a valid target and your weapon is charged. */
public class TriggerBot extends Module {
	private final ModeSetting targets = mode("Targets", "Players", "Players", "Mobs", "All");
	private final BoolSetting swing = bool("Swing", true);

	public TriggerBot() {
		super("Trigger Bot", "Attacks when your crosshair is on a target", Category.COMBAT);
	}

	@Override
	public void onTick() {
		var p = mc.player;
		if (mc.screen != null || p.getAttackStrengthScale(0f) < 0.95f) return;
		if (!(mc.hitResult instanceof EntityHitResult hit)) return;
		if (!Util.validTarget(p, hit.getEntity(), targets.get())) return;

		mc.gameMode.attack(p, hit.getEntity());
		if (swing.get()) p.swing(InteractionHand.MAIN_HAND);
	}
}
