package dev.zprestige.prestige.gui;

import dev.zprestige.prestige.Prestige;
import dev.zprestige.prestige.config.ConfigManager;
import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.BoolSetting;
import dev.zprestige.prestige.setting.ModeSetting;
import dev.zprestige.prestige.setting.NumberSetting;
import dev.zprestige.prestige.setting.Setting;
import dev.zprestige.prestige.util.SoundUtil;
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
 * Prestige Client ClickGUI: Sleek dark theme, category sidebar, search filter,
 * animated toggle pills, expandable settings accordion, and authentic sound effects.
 */
public class ClickGuiScreen extends Screen {
	private static final int SIDEBAR_W = 120;
	private static final int MOD_H = 32;
	private static final int SET_H = 22;
	private static final int GAP = 4;

	// Prestige color palette (Vault theme)
	private static final int C_DIM = 0xAA000000;
	private static final int C_BG = 0xF5060818;
	private static final int C_SIDEBAR = 0xF8080A1C;
	private static final int C_EDGE = 0xFF2B2B5E;
	private static final int C_ROW = 0xFF141830;
	private static final int C_ROW_HOVER = 0xFF202249;
	private static final int C_SETTING = 0xFF0E1224;
	private static final int C_ACCENT = 0xFF7C6CFF;       // Prestige Violet
	private static final int C_ACCENT_HOVER = 0xFFA696FF;
	private static final int C_SELECTED = 0xFF1C2042;
	private static final int C_WHITE = 0xFFF0F2FF;
	private static final int C_MUTED = 0xFF9CA0BF;
	private static final int C_HEADER_GRAY = 0xFF5A5F85;
	private static final int C_TRACK_OFF = 0xFF1B2238;
	private static final int C_GREEN = 0xFF4ADE80;
	private static final int C_RED = 0xFFF87171;

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
		super(Component.literal("Prestige Client"));
	}

	@Override
	protected void init() {
		SoundUtil.play("intro.wav");
		dynamic.clear();
		placed.clear();

		pw = Math.min(this.width - 24, 600);
		ph = Math.min(this.height - 24, 380);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		listX = px + SIDEBAR_W + 14;
		listW = px + pw - 12 - listX;
		listY = py + 68;
		listH = py + ph - 12 - listY;

		search = new EditBox(this.font, px + pw - 12 - 175, py + 14, 175, 18, Component.literal("Search"));
		search.setHint(Component.literal("Search modules..."));
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
			}).bounds(px + 6, y, SIDEBAR_W - 12, 26).build();
			b.setAlpha(0f);
			addRenderableWidget(b);
			y += 30;
		}
		dirty = true;
	}

	private int sidebarTop() { return py + 68; }

	private List<Module> visibleModules() {
		String q = searchText.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return Prestige.modules.getModules(selected);
		return Prestige.modules.getModules().stream()
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
			int x = isSetting ? listX + 12 : listX;
			int w = isSetting ? listW - 12 : listW;
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
				SoundUtil.play("dropdown.wav");
			} else {
				m.toggle();
				SoundUtil.play(m.isEnabled() ? "pop.wav" : "pop2.wav");
			}
			dirty = true;
		}).bounds(x, y, w - 50, MOD_H).build();

		Button toggle = Button.builder(Component.empty(), b -> {
			m.toggle();
			SoundUtil.play(m.isEnabled() ? "pop.wav" : "pop2.wav");
		}).bounds(x + w - 46, y, 46, MOD_H).build();

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
			g.text(this.font, "No modules found matching search", listX + 4, listY + 8, C_MUTED, false);
		}

		super.extractRenderState(g, mx, my, partialTick);
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
		// Prestige Header
		g.text(this.font, "PRESTIGE", px + 12, py + 14, C_ACCENT, true);
		g.text(this.font, "Client", px + 12 + this.font.width("PRESTIGE "), py + 14, C_WHITE, true);
		g.text(this.font, "RELEASE 4.4.0 · FABRIC 26.1", px + 12, py + 26, C_MUTED, false);

		// Category label
		g.text(this.font, "MODULES", px + 12, py + 52, C_HEADER_GRAY, false);

		int y = sidebarTop();
		boolean searching = !searchText.trim().isEmpty();
		for (Module.Category c : Module.Category.values()) {
			int x = px + 6;
			int w = SIDEBAR_W - 12;
			boolean isSelected = c == selected && !searching;
			boolean hover = inside(mx, my, x, y, w, 26);

			if (isSelected) {
				g.fill(x, y, x + w, y + 26, C_SELECTED);
				g.fill(x, y, x + 3, y + 26, C_ACCENT);
			} else if (hover) {
				g.fill(x, y, x + w, y + 26, C_ROW);
			}

			g.text(this.font, c.label, x + 10, y + 9, isSelected ? C_WHITE : C_MUTED, false);
			String count = Integer.toString(Prestige.modules.getModules(c).size());
			g.text(this.font, count, x + w - 8 - this.font.width(count), y + 9, isSelected ? C_ACCENT : C_HEADER_GRAY, false);
			y += 30;
		}
	}

	private void drawHeader(GuiGraphicsExtractor g) {
		boolean searching = !searchText.trim().isEmpty();
		List<Module> shown = visibleModules();
		long on = shown.stream().filter(Module::isEnabled).count();

		if (searching) {
			g.text(this.font, "Search Results", listX, py + 16, C_WHITE, true);
		} else {
			g.text(this.font, selected.label, listX, py + 16, C_ACCENT, true);
			g.text(this.font, " Modules", listX + this.font.width(selected.label), py + 16, C_WHITE, true);
		}
		g.text(this.font, shown.size() + " modules · " + on + " enabled", listX, py + 30, C_MUTED, false);
		g.text(this.font, "Click module name to expand settings · Esc to close", listX, py + 48, C_HEADER_GRAY, false);
	}

	private void drawModule(GuiGraphicsExtractor g, Placed p, int mx, int my) {
		Module m = p.entry().module();
		int x = p.x(), y = p.y(), w = p.w();
		boolean hover = inside(mx, my, x, y, w, MOD_H);

		// Card Background
		g.fill(x, y, x + w, y + MOD_H, hover ? C_ROW_HOVER : C_ROW);
		g.outline(x, y, w, MOD_H, C_EDGE);
		if (m.isEnabled()) g.fill(x, y, x + 3, y + MOD_H, C_ACCENT);

		// Module Name & Description
		g.text(this.font, m.getName(), x + 10, y + 6, C_WHITE, false);
		g.text(this.font, m.getDescription(), x + 10, y + 19, C_MUTED, false);

		// Expand chevron
		if (!m.getSettings().isEmpty()) {
			String chev = expanded.contains(m) ? "▼" : "▶";
			g.text(this.font, chev, x + w - 58, y + 12, expanded.contains(m) ? C_ACCENT : C_MUTED, false);
		}

		// Prestige Toggle Switch Pill
		int tx = x + w - 38;
		int ty = y + 10;
		g.fill(tx, ty, tx + 28, ty + 12, m.isEnabled() ? C_ACCENT : C_TRACK_OFF);
		int knobX = m.isEnabled() ? tx + 28 - 11 : tx + 1;
		g.fill(knobX, ty + 1, knobX + 10, ty + 11, C_WHITE);
	}

	private void drawSetting(GuiGraphicsExtractor g, Placed p, int mx, int my) {
		Setting s = p.entry().setting();
		int x = p.x(), y = p.y(), w = p.w();
		boolean hover = inside(mx, my, x, y, w, SET_H);

		g.fill(x, y, x + w, y + SET_H, hover ? C_ROW_HOVER : C_SETTING);
		g.outline(x, y, w, SET_H, 0xFF1F243E);
		g.text(this.font, s.name, x + 10, y + 7, C_MUTED, false);

		if (s instanceof NumberSetting ns) {
			drawStepper(g, x + w - 74, y + 2, "-");
			drawStepper(g, x + w - 24, y + 2, "+");
			String v = ns.display();
			int center = x + w - 74 + 20 + (30 / 2);
			g.text(this.font, v, center - this.font.width(v) / 2, y + 7, C_WHITE, false);
		} else if (s instanceof BoolSetting bs) {
			String v = bs.get() ? "ON" : "OFF";
			g.text(this.font, v, x + w - 10 - this.font.width(v), y + 7, bs.get() ? C_GREEN : C_RED, true);
		} else {
			String v = s.display();
			g.text(this.font, v, x + w - 10 - this.font.width(v), y + 7, C_ACCENT, false);
		}
	}

	private void drawStepper(GuiGraphicsExtractor g, int x, int y, String label) {
		g.fill(x, y, x + 20, y + SET_H - 4, C_ROW);
		g.outline(x, y, 20, SET_H - 4, C_EDGE);
		g.text(this.font, label, x + 10 - this.font.width(label) / 2, y + 5, C_WHITE, false);
	}

	@Override
	public void removed() {
		ConfigManager.save(Prestige.modules);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
