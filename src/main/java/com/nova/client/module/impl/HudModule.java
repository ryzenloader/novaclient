package com.nova.client.module.impl;

import com.nova.client.module.Module;
import com.nova.client.setting.BoolSetting;

/** Controls the on-screen watermark and module list. Enabled by default. */
public class HudModule extends Module {
	public static BoolSetting watermark;
	public static BoolSetting arrayList;

	public HudModule() {
		super("HUD", "Watermark and enabled-module list", Category.RENDER);
		watermark = bool("Watermark", true);
		arrayList = bool("Module list", true);
		setEnabled(true);
	}
}
