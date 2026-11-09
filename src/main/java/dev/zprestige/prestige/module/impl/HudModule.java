package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;
import dev.zprestige.prestige.setting.BoolSetting;

/** Controls the on-screen watermark, module list, and info stats (FPS, Ping, XYZ). */
public class HudModule extends Module {
	public static BoolSetting watermark;
	public static BoolSetting arrayList;
	public static BoolSetting infoHud;
	public static BoolSetting soundEffects;

	public HudModule() {
		super("HUD", "Watermark, module list, and status HUD", Category.RENDER);
		watermark = bool("Watermark", true);
		arrayList = bool("Module list", true);
		infoHud = bool("Info (FPS/Ping/XYZ)", true);
		soundEffects = bool("Prestige Sounds", true);
		setEnabled(true);
	}
}
