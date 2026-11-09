package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.BoolSetting;
import dev.zprestige.prestige.setting.ModeSetting;
import dev.zprestige.prestige.setting.NumberSetting;
import dev.zprestige.prestige.util.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Smoothly pulls your crosshair toward the closest target inside your field of view. */
public class AimAssist extends Module {
	private final NumberSetting range = number("Range", 5.0, 2.0, 8.0, 0.5);
	private final NumberSetting speed = number("Speed", 4.0, 1.0, 20.0, 0.5);
	private final NumberSetting fov = number("FOV", 90, 10, 360, 10);
	private final ModeSetting targets = mode("Targets", "Players", "Players", "Mobs", "All");
	private final BoolSetting onlyClicking = bool("Only when clicking", true);

	public AimAssist() {
		super("Aim Assist", "Aims at targets", Category.COMBAT);
	}

	@Override
	public void onTick() {
		var p = mc.player;
		if (mc.screen != null) return;
		if (onlyClicking.get() && !mc.options.keyAttack.isDown()) return;

		Vec3 eye = p.getEyePosition();
		Entity best = null;
		double bestAngle = Double.MAX_VALUE;
		float bestYaw = 0, bestPitch = 0;

		for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(range.get() + 1.0))) {
			if (!Util.validTarget(p, e, targets.get())) continue;
			if (Util.distanceToBox(p, e) > range.get()) continue;

			Vec3 aim = e.position().add(0, e.getBbHeight() * 0.75, 0);
			float yaw = Util.yawTo(eye, aim);
			float pitch = Util.pitchTo(eye, aim);
			float dYaw = Mth.wrapDegrees(yaw - p.getYRot());
			float dPitch = pitch - p.getXRot();
			double angle = Math.hypot(dYaw, dPitch);

			if (Math.abs(dYaw) > fov.get() / 2.0) continue;
			if (angle < bestAngle) {
				best = e;
				bestAngle = angle;
				bestYaw = dYaw;
				bestPitch = dPitch;
			}
		}
		if (best == null) return;

		float max = speed.getFloat();
		p.setYRot(p.getYRot() + Mth.clamp(bestYaw, -max, max));
		p.setXRot(Mth.clamp(p.getXRot() + Mth.clamp(bestPitch, -max, max), -90f, 90f));
	}
}
