package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.BoolSetting;
import com.nova.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Places blocks under you while you move.
 * It looks a few ticks ahead along your motion so the block is there before you reach the edge,
 * can place several blocks per tick, extends further ahead (Expand), keeps your height (Keep Y),
 * and always picks the biggest valid block stack in your hotbar.
 * Holding jump builds a tower straight up.
 */
public class Scaffold extends Module {
	private static final Direction[] SUPPORT_ORDER = {
		Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
	};
	private static final double REACH = 4.5;

	private final NumberSetting expand = number("Expand", 0, 0, 5, 1);
	private final NumberSetting perTick = number("Blocks per tick", 1, 1, 3, 1);
	private final BoolSetting keepY = bool("Keep Y", false);
	private final BoolSetting swing = bool("Swing", true);
	private final BoolSetting switchBack = bool("Switch back", true);

	private int previousSlot = -1;
	private int lockedY;

	public Scaffold() {
		super("Scaffold", "Places blocks under you as you walk", Category.MOVEMENT);
	}

	@Override
	protected void onEnable() {
		previousSlot = mc.player.getInventory().getSelectedSlot();
		lockedY = mc.player.blockPosition().getY();
	}

	@Override
	protected void onDisable() {
		if (mc.player != null && previousSlot >= 0 && switchBack.get()) {
			mc.player.getInventory().setSelectedSlot(previousSlot);
		}
		previousSlot = -1;
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (mc.screen != null) return;

		if (p.onGround() && !mc.options.keyJump.isDown()) {
			lockedY = p.blockPosition().getY();
		}

		int slot = bestBlockSlot();
		if (slot < 0) return;

		int placed = 0;
		boolean switched = false;
		for (BlockPos target : collectTargets(p)) {
			if (placed >= perTick.getInt()) break;
			if (!mc.level.getBlockState(target).canBeReplaced()) continue;

			BlockHitResult hit = findSupport(p, target);
			if (hit == null) continue;

			if (!switched) {
				p.getInventory().setSelectedSlot(slot);
				switched = true;
			}
			InteractionResult result = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
			if (result.consumesAction()) {
				placed++;
				if (swing.get()) p.swing(InteractionHand.MAIN_HAND);
			}
		}
	}

	/** Block positions to fill, most urgent first: under your feet, predicted position, then Expand steps. */
	private Set<BlockPos> collectTargets(Player p) {
		Vec3 pos = p.position();
		Vec3 vel = p.getDeltaMovement();
		double speed = Math.hypot(vel.x, vel.z);
		int y = keepY.get() ? lockedY - 1 : Mth.floor(p.getY() - 1.0);

		Set<BlockPos> targets = new LinkedHashSet<>();
		targets.add(new BlockPos(Mth.floor(pos.x), y, Mth.floor(pos.z)));

		if (speed > 0.03) {
			double nx = vel.x / speed;
			double nz = vel.z / speed;
			targets.add(new BlockPos(Mth.floor(pos.x + vel.x * 3), y, Mth.floor(pos.z + vel.z * 3)));
			for (int i = 1; i <= expand.getInt(); i++) {
				targets.add(new BlockPos(Mth.floor(pos.x + nx * i), y, Mth.floor(pos.z + nz * i)));
			}
		}
		return targets;
	}

	/** Finds a solid neighbor of target to place against and builds the click for it. */
	private BlockHitResult findSupport(Player p, BlockPos target) {
		Vec3 eye = p.getEyePosition();
		for (Direction dir : SUPPORT_ORDER) {
			BlockPos neighbor = target.relative(dir);
			var state = mc.level.getBlockState(neighbor);
			if (state.isAir() || state.canBeReplaced()) continue;

			Direction face = dir.getOpposite();
			Vec3 hitPos = Vec3.atCenterOf(neighbor).add(
				face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			if (eye.distanceToSqr(hitPos) > REACH * REACH) continue;

			return new BlockHitResult(hitPos, face, neighbor, false);
		}
		return null;
	}

	private int bestBlockSlot() {
		var inv = mc.player.getInventory();
		int best = -1;
		int bestCount = 0;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inv.getItem(i);
			if (isValidBlock(stack) && stack.getCount() > bestCount) {
				best = i;
				bestCount = stack.getCount();
			}
		}
		return best;
	}

	private boolean isValidBlock(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
		Block block = blockItem.getBlock();
		if (block instanceof FallingBlock || block instanceof EntityBlock) return false;
		return block.defaultBlockState().isCollisionShapeFullBlock(mc.level, BlockPos.ZERO);
	}
}
