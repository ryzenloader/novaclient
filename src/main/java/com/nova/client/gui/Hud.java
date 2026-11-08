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

/** Watermark (top-left) and the enabled-module list (top-right). */
public final class Hud {
	public static final String NAME = "Nova";
	public static final String VERSION = "1.0";

	private Hud() {}

	/** Accent color cycling by index (purple to pink to blue), as a full ARGB int. */
	public static int accent(int index) {
		float hue = ((280 + index * 18) % 360) / 360f;
		return 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 1.0f) & 0xFFFFFF);
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		if (!NovaClient.modules.get("HUD").map(Module::isEnabled).orElse(false)) return;
		Font font = mc.font;

		if (HudModule.watermark.get()) {
			g.text(font, NAME, 6, 6, accent(0), true);
			g.text(font, VERSION, 6 + font.width(NAME) + 4, 6, 0xFFFFFFFF, true);
		}

		if (!HudModule.arrayList.get()) return;
		List<Module> enabled = NovaClient.modules.getModules().stream()
			.filter(Module::isEnabled)
			.filter(m -> !(m instanceof HudModule))
			.sorted(Comparator.comparingInt((Module m) -> font.width(m.getName())).reversed())
			.toList();

		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int rowHeight = font.lineHeight + 2;
		int y = 2;
		for (int i = 0; i < enabled.size(); i++) {
			String name = enabled.get(i).getName();
			int x = screenWidth - font.width(name) - 6;
			int color = accent(i);

			g.fill(x - 3, y, screenWidth, y + rowHeight, 0x90000000);
			g.fill(screenWidth - 2, y, screenWidth, y + rowHeight, color);
			g.text(font, name, x, y + 2, color, true);
			y += rowHeight;
		}
	}
}
