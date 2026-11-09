package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import net.minecraft.world.phys.AABB;

/** Prestige Parkour: Jumps automatically at block edges. */
public class Parkour extends Module {
	public Parkour() {
		super("Parkour", "Jumps automatically at block edges", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) return;
		if (mc.player.onGround() && !mc.player.isShiftKeyDown() && !mc.options.keyJump.isDown()) {
			AABB box = mc.player.getBoundingBox().move(0, -0.5, 0).deflate(0.001, 0, 0.001);
			if (mc.level.noCollision(mc.player, box)) {
				mc.player.jumpFromGround();
			}
		}
	}
}
