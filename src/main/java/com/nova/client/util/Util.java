package com.nova.client.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Util {
	private Util() {}

	/** Distance from the viewer's eyes to the closest point of the target's hitbox. */
	public static double distanceToBox(Entity from, Entity to) {
		Vec3 eye = from.getEyePosition();
		AABB box = to.getBoundingBox();
		double dx = Mth.clamp(eye.x, box.minX, box.maxX) - eye.x;
		double dy = Mth.clamp(eye.y, box.minY, box.maxY) - eye.y;
		double dz = Mth.clamp(eye.z, box.minZ, box.maxZ) - eye.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** mode is one of "Players", "Mobs", "All". */
	public static boolean validTarget(Entity self, Entity e, String mode) {
		if (e == self || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
		boolean player = e instanceof Player p && !p.isSpectator();
		boolean mob = e instanceof Mob;
		return switch (mode) {
			case "Players" -> player;
			case "Mobs" -> mob;
			default -> player || mob;
		};
	}

	public static float yawTo(Vec3 from, Vec3 to) {
		return Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x))) - 90.0f);
	}

	public static float pitchTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		return (float) -Math.toDegrees(Math.atan2(to.y - from.y, horizontal));
	}
}
