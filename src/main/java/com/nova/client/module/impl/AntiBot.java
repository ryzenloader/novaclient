package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.BoolSetting;
import com.nova.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;

/**
 * Filters out fake server bots spawned by anti-cheats (Watchdog, Vulcan, Verus, GrimAC, etc.)
 * so combat modules never target them.
 */
public class AntiBot extends Module {
	public static BoolSetting tabList;
	public static BoolSetting ticksExisted;
	public static NumberSetting minTicks;
	public static BoolSetting invisible;

	public AntiBot() {
		super("Anti Bot", "Ignores fake anti-cheat bots", Category.COMBAT);
		tabList = bool("Tablist check", true);
		ticksExisted = bool("Ticks alive", true);
		minTicks = number("Min ticks", 15, 5, 100, 5);
		invisible = bool("Invisible check", true);
		setEnabled(true);
	}

	public static boolean isBot(Entity entity) {
		if (!(entity instanceof Player player)) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return false;

		// 1. Tablist check: Real players appear in the server tab list
		if (tabList != null && tabList.get()) {
			Collection<PlayerInfo> players = mc.getConnection().getOnlinePlayers();
			boolean inTab = players.stream().anyMatch(info -> info.getProfile().getId().equals(player.getUUID()));
			if (!inTab) return true;
		}

		// 2. Ticks existed check: Anti-cheat bots are newly spawned during combat
		if (ticksExisted != null && ticksExisted.get() && minTicks != null) {
			if (player.tickCount < minTicks.getInt()) {
				return true;
			}
		}

		// 3. Invisible check: Aura watchdog bots often fly invisibly around the player
		if (invisible != null && invisible.get() && player.isInvisible()) {
			return true;
		}

		return false;
	}
}
