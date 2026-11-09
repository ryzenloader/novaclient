package dev.zprestige.prestige.gui;

import dev.zprestige.prestige.Prestige;
import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.module.impl.HudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Complete Prestige Client HUD: Watermark, Dynamic Gradient ArrayList, Info Stats, and In-Game Toasts. */
public final class Hud {
	public static final String TITLE = "PRESTIGE";
	public static final String SUB = "Client";
	public static final String VERSION = "v4.4.0";

	private record Toast(String name, boolean enabled, long timestamp) {}
	private static final List<Toast> toasts = new ArrayList<>();

	private Hud() {}

	/** Dispatches an on-screen Prestige notification toast. */
	public static synchronized void toast(String name, boolean enabled) {
		toasts.removeIf(t -> t.name.equalsIgnoreCase(name));
		toasts.add(new Toast(name, enabled, System.currentTimeMillis()));
	}

	/** Prestige multi-step gradient cycling. */
	public static int prestigeGradient(int index) {
		float step = ((System.currentTimeMillis() / 40L + index * 18L) % 360) / 360f;
		// Hue cycle between violet (260) and cyan/emerald (160)
		float hue = 0.72f - (float) Math.sin(step * Math.PI * 2) * 0.18f;
		return 0xFF000000 | (Color.HSBtoRGB(hue, 0.65f, 1.0f) & 0xFFFFFF);
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		if (!Prestige.modules.get("HUD").map(Module::isEnabled).orElse(false)) return;
		Font font = mc.font;
		long now = System.currentTimeMillis();

		// 1. Prestige Watermark
		if (HudModule.watermark != null && HudModule.watermark.get()) {
			int titleW = font.width(TITLE);
			int subW = font.width(SUB);
			int verW = font.width(VERSION);
			int totalW = titleW + subW + verW + 16;

			// Prestige midnight background with left violet bar
			g.fill(4, 4, 4 + totalW, 20, 0xDD060818);
			g.fill(4, 4, 6, 20, 0xFF7C6CFF);
			g.text(font, TITLE, 9, 8, 0xFF7C6CFF, true);
			g.text(font, SUB, 9 + titleW + 3, 8, 0xFFFFFFFF, true);
			g.text(font, VERSION, 9 + titleW + 3 + subW + 4, 8, 0xFF9CA0BF, false);
		}

		// 2. Info Stats (FPS, Ping, Coordinates)
		if (HudModule.infoHud != null && HudModule.infoHud.get()) {
			int fps = mc.getFps();
			int ping = 0;
			if (mc.getConnection() != null) {
				var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (info != null) ping = info.getLatency();
			}
			String coords = String.format("X: %.1f  Y: %.1f  Z: %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());

			int screenHeight = mc.getWindow().getGuiScaledHeight();
			int statsY = screenHeight - font.lineHeight - 6;

			String line = String.format("FPS: %d   Ping: %dms   %s", fps, ping, coords);
			int boxW = font.width(line) + 12;

			g.fill(4, statsY - 3, 4 + boxW, statsY + font.lineHeight + 2, 0xCC060818);
			g.fill(4, statsY - 3, 6, statsY + font.lineHeight + 2, 0xFF7C6CFF);

			// Render formatted text
			int curX = 9;
			g.text(font, "FPS: ", curX, statsY, 0xFF7C6CFF, true);
			curX += font.width("FPS: ");
			String fpsStr = Integer.toString(fps);
			g.text(font, fpsStr + "   ", curX, statsY, 0xFF4ADE80, true);
			curX += font.width(fpsStr + "   ");

			g.text(font, "Ping: ", curX, statsY, 0xFF7C6CFF, true);
			curX += font.width("Ping: ");
			String pingStr = ping + "ms   ";
			g.text(font, pingStr, curX, statsY, 0xFF22D3EE, true);
			curX += font.width(pingStr);

			g.text(font, coords, curX, statsY, 0xFFF0F2FF, false);
		}

		// 3. Prestige Dynamic Gradient ArrayList (Top Right)
		if (HudModule.arrayList != null && HudModule.arrayList.get()) {
			List<Module> enabled = Prestige.modules.getModules().stream()
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
				int color = prestigeGradient(i);

				// Midnight box with gradient right pill
				g.fill(x - 5, y, screenWidth, y + rowHeight, 0xD9060818);
				g.fill(screenWidth - 3, y, screenWidth, y + rowHeight, color);
				g.text(font, name, x - 1, y + 2, color, true);
				y += rowHeight;
			}
		}

		// 4. In-Game Prestige Notification Toasts (Bottom Right)
		synchronized (toasts) {
			toasts.removeIf(t -> now - t.timestamp > 2600);
			int toastY = mc.getWindow().getGuiScaledHeight() - 28;
			int screenW = mc.getWindow().getGuiScaledWidth();

			for (Toast t : toasts) {
				String status = t.enabled ? "Enabled" : "Disabled";
				int statusCol = t.enabled ? 0xFF4ADE80 : 0xFFF87171;
				String text = t.name + " » " + status;
				int tw = font.width(text);
				int bx = screenW - tw - 18;

				g.fill(bx, toastY, screenW - 4, toastY + 18, 0xEE080A1C);
				g.outline(bx, toastY, screenW - 4 - bx, 18, 0xFF2B2B5E);
				g.fill(bx, toastY, bx + 3, toastY + 18, statusCol);
				g.text(font, text, bx + 7, toastY + 5, 0xFFF0F2FF, true);

				toastY -= 22;
			}
		}
	}
}
