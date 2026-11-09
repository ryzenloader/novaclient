package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.config.ConfigManager;
import com.nova.client.module.Module;
import com.nova.client.setting.BoolSetting;
import com.nova.client.setting.ModeSetting;
import com.nova.client.setting.NumberSetting;
import com.nova.client.setting.Setting;
import com.nova.client.util.SoundUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sidebar GUI: category list on the left, search box on top, one row per module with a toggle switch.
 * Click a module's name to expand its settings. Mouse wheel scrolls the list.
 *
 * All visuals are drawn here. The clickable areas are invisible vanilla buttons laid over the rows,
 * so no raw mouse-event handling is needed.
 */
public class ClickGuiScreen extends Screen {
	private static final int SIDEBAR_W = 108;
	private static final int MOD_H = 30;
	private static final int SET_H = 22;
	private static final int GAP = 4;

	private static final int C_DIM = 0xA0000000;
	private static final int C_BG = 0xF00B1020;
	private static final int C_SIDEBAR = 0xF0070A16;
	private static final int C_EDGE = 0xFF2B3A66;
	private static final int C_ROW = 0xFF111830;
	private static final int C_ROW_HOVER = 0xFF19234A;
	private static final int C_SETTING = 0xFF0D1426;
	private static final int C_ACCENT = 0xFF7C5CFF;
	private static final int C_ACCENT_TEXT = 0xFFB89BFF;
	private static final int C_SELECTED = 0xFF1B2450;
	private static final int C_WHITE = 0xFFFFFFFF;
	private static final int C_MUTED = 0xFF7E8AA6;
	private static final int C_TRACK_OFF = 0xFF2A3350;
	private static final int C_GREEN = 0xFF6EE79A;
	private static final int C_RED = 0xFFFF7A7A;

	private record Entry(Module module, Setting setting, int height) {}
	private record Placed(Entry entry, int x, int y, int w) {}

	private static Module.Category selected = Module.Category.COMBAT;
	private static String searchText = "";

	private final Set<Module> expanded = new HashSet<>();
	private final List<AbstractWidget> dynamic = new ArrayList<>();
	private final List<Placed> placed = new ArrayList<>();
	private EditBox search;
	private int scroll;
	private boolean dirty = true;

	private int px, py, pw, ph;
	private int listX, listY, listW, listH;

	public ClickGuiScreen() {
		super(Component.literal("Nova Client"));
	}

	@Override
	protected void init() {
		SoundUtil.play("intro.wav");
		dynamic.clear();
		placed.clear();

		pw = Math.min(this.width - 24, 560);
		ph = Math.min(this.height - 24, 340);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		listX = px + SIDEBAR_W + 14;
		listW = px + pw - 12 - listX;
		listY = py + 70;
		listH = py + ph - 12 - listY;

		search = new EditBox(this.font, px + pw - 12 - 170, py + 14, 170, 18, Component.literal("Search"));
		search.setHint(Component.literal("Search modules"));
		search.setValue(searchText);
		search.setResponder(text -> {
			searchText = text;
			scroll = 0;
			dirty = true;
		});
		addRenderableWidget(search);

		int y = sidebarTop();
		for (Module.Category category : Module.Category.values()) {
			Button b = Button.builder(Component.empty(), btn -> {
				selected = category;
				SoundUtil.play("dropdown.wav");
				searchText = "";
				search.setValue("");
				scroll = 0;
				dirty = true;
			}).bounds(px + 6, y, SIDEBAR_W - 12, 24).build();
			b.setAlpha(0f);
			addRenderableWidget(b);
			y += 28;
		}
		dirty = true;
	}

	private int sidebarTop() { return py + 62; }

	private List<Module> visibleModules() {
		String q = searchText.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return NovaClient.modules.getModules(selected);
		return NovaClient.modules.getModules().stream()
			.filter(m -> m.getName().toLowerCase(Locale.ROOT).contains(q)
				|| m.getDescription().toLowerCase(Locale.ROOT).contains(q))
			.toList();
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		for (Module m : visibleModules()) {
			list.add(new Entry(m, null, MOD_H));
			if (expanded.contains(m)) {
				for (Setting s : m.getSettings()) list.add(new Entry(m, s, SET_H));
			}
		}
		return list;
	}

	private void rebuild() {
		for (AbstractWidget w : dynamic) removeWidget(w);
		dynamic.clear();
		placed.clear();

		List<Entry> all = entries();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - 1)));

		int y = listY;
		for (int i = scroll; i < all.size(); i++) {
			Entry e = all.get(i);
			if (y + e.height() > listY + listH) break;

			boolean isSetting = e.setting() != null;
			int x = isSetting ? listX + 14 : listX;
			int w = isSetting ? listW - 14 : listW;
			placed.add(new Placed(e, x, y, w));

			if (!isSetting) addModuleButtons(e.module(), x, y, w);
			else addSettingButtons(e.setting(), x, y, w, e.height());

			y += e.height() + GAP;
		}
	}

	private void addModuleButtons(Module m, int x, int y, int w) {
		boolean hasSettings = !m.getSettings().isEmpty();
		Button main = Button.builder(Component.empty(), b -> {
			if (hasSettings) {
				if (!expanded.remove(m)) expanded.add(m);
			} else {
				m.toggle();
			}
			dirty = true;
		}).bounds(x, y, w - 54, MOD_H).build();
		Button toggle = Button.builder(Component.empty(), b -> {
			m.toggle();
			SoundUtil.play(m.isEnabled() ? "pop.wav" : "pop2.wav");
		}).bounds(x + w - 50, y, 50, MOD_H).build();
		track(main);
		track(toggle);
	}

	private void addSettingButtons(Setting s, int x, int y, int w, int h) {
		if (s instanceof BoolSetting bs) {
			track(Button.builder(Component.empty(), b -> {
				bs.toggle();
				SoundUtil.play("pop3.wav");
			}).bounds(x, y, w, h).build());
		} else if (s instanceof ModeSetting ms) {
			track(Button.builder(Component.empty(), b -> {
				ms.cycle();
				SoundUtil.play("hover.wav");
			}).bounds(x, y, w, h).build());
		} else if (s instanceof NumberSetting ns) {
			track(Button.builder(Component.empty(), b -> {
				ns.decrease();
				SoundUtil.play("hover.wav");
			}).bounds(x + w - 74, y + 2, 20, h - 4).build());
			track(Button.builder(Component.empty(), b -> {
				ns.increase();
				SoundUtil.play("hover.wav");
			}).bounds(x + w - 24, y + 2, 20, h - 4).build());
		}
	}

	private void track(Button b) {
		b.setAlpha(0f);
		addRenderableWidget(b);
		dynamic.add(b);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
			scroll -= (int) Math.signum(scrollY);
			dirty = true;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
		if (dirty) {
			rebuild();
			dirty = false;
		}

		g.fill(0, 0, this.width, this.height, C_DIM);
		g.fill(px, py, px + pw, py + ph, C_BG);
		g.fill(px, py, px + SIDEBAR_W, py + ph, C_SIDEBAR);
		g.outline(px, py, pw, ph, C_EDGE);
		g.fill(px + SIDEBAR_W, py + 1, px + SIDEBAR_W + 1, py + ph - 1, C_EDGE);

		drawSidebar(g, mx, my);
		drawHeader(g);
		for (Placed p : placed) {
			if (p.entry().setting() == null) drawModule(g, p, mx, my);
			else drawSetting(g, p, mx, my);
		}
		if (placed.isEmpty()) {
			g.text(this.font, "No modules found", listX + 4, listY + 6, C_MUTED, false);
		}

		super.extractRenderState(g, mx, my, partialTick);
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
		g.text(this.font, "Nova", px + 12, py + 12, C_ACCENT_TEXT, true);
		g.text(this.font, "Client", px + 12 + this.font.width("Nova "), py + 12, C_WHITE, true);
		g.text(this.font, "RELEASE 1.0", px + 12, py + 24, C_MUTED, false);
		g.text(this.font, "MODULES", px + 12, py + 48, C_MUTED, false);

		int y = sidebarTop();
		boolean searching = !searchText.trim().isEmpty();
		for (Module.Category c : Module.Category.values()) {
			int x = px + 6;
			int w = SIDEBAR_W - 12;
			boolean isSelected = c == selected && !searching;
			boolean hover = inside(mx, my, x, y, w, 24);
			if (isSelected) {
				g.fill(x, y, x + w, y + 24, C_SELECTED);
				g.fill(x, y, x + 2, y + 24, C_ACCENT);
			} else if (hover) {
				g.fill(x, y, x + w, y + 24, C_ROW);
			}
			g.text(this.font, c.label, x + 10, y + 8, isSelected ? C_WHITE : C_MUTED, false);
			String count = Integer.toString(NovaClient.modules.getModules(c).size());
			g.text(this.font, count, x + w - 8 - this.font.width(count), y + 8, C_MUTED, false);
			y += 28;
		}
	}

	private void drawHeader(GuiGraphicsExtractor g) {
		boolean searching = !searchText.trim().isEmpty();
		List<Module> shown = visibleModules();
		long on = shown.stream().filter(Module::isEnabled).count();

		if (searching) {
			g.text(this.font, "Search results", listX, py + 16, C_WHITE, true);
		} else {
			g.text(this.font, selected.label, listX, py + 16, C_ACCENT_TEXT, true);
			g.text(this.font, " Modules", listX + this.font.width(selected.label), py + 16, C_WHITE, true);
		}
		g.text(this.font, shown.size() + " modules · " + on + " enabled", listX, py + 30, C_MUTED, false);
		g.text(this.font, "Click a name for settings · scroll for more · Esc to close", listX, py + 48, C_MUTED, false);
	}

	private void drawModule(GuiGraphicsExtractor g, Placed p, int mx, int my) {
		Module m = p.entry().module();
		int x = p.x(), y = p.y(), w = p.w();
		boolean hover = inside(mx, my, x, y, w, MOD_H);

		g.fill(x, y, x + w, y + MOD_H, hover ? C_ROW_HOVER : C_ROW);
		g.outline(x, y, w, MOD_H, C_EDGE);
		if (m.isEnabled()) g.fill(x, y, x + 2, y + MOD_H, C_ACCENT);

		g.text(this.font, m.getName(), x + 10, y + 6, C_WHITE, false);
		g.text(this.font, m.getDescription(), x + 10, y + 18, C_MUTED, false);

		if (!m.getSettings().isEmpty()) {
			g.text(this.font, expanded.contains(m) ? "v" : ">", x + w - 66, y + 11, C_MUTED, false);
		}

		int tx = x + w - 40;
		int ty = y + 9;
		g.fill(tx, ty, tx + 28, ty + 12, m.isEnabled() ? C_ACCENT : C_TRACK_OFF);
		int knobX = m.isEnabled() ? tx + 28 - 11 : tx + 1;
		g.fill(knobX, ty + 1, knobX + 10, ty + 11, C_WHITE);
	}

	private void drawSetting(GuiGraphicsExtractor g, Placed p, int mx, int my) {
		Setting s = p.entry().setting();
		int x = p.x(), y = p.y(), w = p.w();
		boolean hover = inside(mx, my, x, y, w, SET_H);

		g.fill(x, y, x + w, y + SET_H, hover ? C_ROW : C_SETTING);
		g.text(this.font, s.name, x + 10, y + 7, C_MUTED, false);

		if (s instanceof NumberSetting ns) {
			drawStepper(g, x + w - 74, y + 2, "-");
			drawStepper(g, x + w - 24, y + 2, "+");
			String v = ns.display();
			int center = x + w - 74 + 20 + (30 / 2);
			g.text(this.font, v, center - this.font.width(v) / 2, y + 7, C_WHITE, false);
		} else if (s instanceof BoolSetting bs) {
			String v = bs.display();
			g.text(this.font, v, x + w - 10 - this.font.width(v), y + 7, bs.get() ? C_GREEN : C_RED, false);
		} else {
			String v = s.display();
			g.text(this.font, v, x + w - 10 - this.font.width(v), y + 7, C_ACCENT_TEXT, false);
		}
	}

	private void drawStepper(GuiGraphicsExtractor g, int x, int y, String label) {
		g.fill(x, y, x + 20, y + SET_H - 4, C_ROW_HOVER);
		g.outline(x, y, 20, SET_H - 4, C_EDGE);
		g.text(this.font, label, x + 10 - this.font.width(label) / 2, y + 5, C_WHITE, false);
	}

	@Override
	public void removed() {
		ConfigManager.save(NovaClient.modules);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
