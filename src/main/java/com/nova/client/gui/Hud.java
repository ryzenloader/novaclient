package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.module.Module;
import com.nova.client.module.impl.HudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

/** Prestige-inspired modern HUD: Watermark, status stats (FPS, Ping, XYZ), and animated Module List. */
public final class Hud {
	public static final String NAME = "Nova";
	public static final String BADGE = "Prestige";

	private Hud() {}

	/** Accent color cycling by index (purple to pink to blue), as a full ARGB int. */
	public static int accent(int index) {
		float hue = ((280 + index * 16) % 360) / 360f;
		return 0xFF000000 | (Color.HSBtoRGB(hue, 0.65f, 1.0f) & 0xFFFFFF);
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		if (!NovaClient.modules.get("HUD").map(Module::isEnabled).orElse(false)) return;
		Font font = mc.font;

		// 1. Watermark
		if (HudModule.watermark != null && HudModule.watermark.get()) {
			int nameWidth = font.width(NAME);
			int badgeWidth = font.width(BADGE);
			int totalW = nameWidth + badgeWidth + 12;
			g.fill(4, 4, 4 + totalW, 18, 0xAA080B14);
			g.fill(4, 4, 6, 18, accent(0));
			g.text(font, NAME, 8, 7, accent(0), true);
			g.text(font, BADGE, 8 + nameWidth + 4, 7, 0xFFAAAAAA, true);
		}

		// 2. Info Stats (FPS, Ping, Coordinates)
		if (HudModule.infoHud != null && HudModule.infoHud.get()) {
			int fps = mc.getFps();
			int ping = 0;
			if (mc.getConnection() != null) {
				var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (info != null) ping = info.getLatency();
			}
			String coords = String.format("XYZ: %.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
			String stats = String.format("FPS: %d  |  Ping: %dms  |  %s", fps, ping, coords);

			int screenHeight = mc.getWindow().getGuiScaledHeight();
			int statsY = screenHeight - font.lineHeight - 4;
			int statsW = font.width(stats) + 8;
			g.fill(4, statsY - 2, 4 + statsW, statsY + font.lineHeight + 1, 0x88080B14);
			g.text(font, stats, 8, statsY, 0xFFEEEEEE, true);
		}

		// 3. Module List (Right side)
		if (HudModule.arrayList == null || !HudModule.arrayList.get()) return;
		List<Module> enabled = NovaClient.modules.getModules().stream()
			.filter(Module::isEnabled)
			.filter(m -> !(m instanceof HudModule))
			.sorted(Comparator.comparingInt((Module m) -> font.width(m.getName())).reversed())
			.toList();

		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int rowHeight = font.lineHeight + 3;
		int y = 4;
		for (int i = 0; i < enabled.size(); i++) {
			String name = enabled.get(i).getName();
			int textWidth = font.width(name);
			int x = screenWidth - textWidth - 8;
			int color = accent(i);

			g.fill(x - 4, y, screenWidth, y + rowHeight, 0x990A0E1A);
			g.fill(screenWidth - 3, y, screenWidth, y + rowHeight, color);
			g.text(font, name, x, y + 2, color, true);
			y += rowHeight;
		}
	}
}
