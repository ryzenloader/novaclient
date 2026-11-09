package dev.zprestige.prestige.module.impl;

import dev.zprestige.prestige.module.Module;

/** Prestige StreamerMode: Hides server IP and sensitive info */
public class StreamerMode extends Module {
	public StreamerMode() {
		super("StreamerMode", "Hides server IP and sensitive info", Category.PLAYER);
	}
}
