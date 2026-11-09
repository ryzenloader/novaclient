package dev.zprestige.prestige.util;

import dev.zprestige.prestige.Prestige;
import dev.zprestige.prestige.module.impl.HudModule;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;

/** Asynchronous sound effect player using Prestige audio assets. */
public final class SoundUtil {
	private SoundUtil() {}

	public static void play(String soundName) {
		if (HudModule.soundEffects != null && !HudModule.soundEffects.get()) return;

		Thread.ofVirtual().start(() -> {
			try {
				InputStream is = SoundUtil.class.getResourceAsStream("/assets/prestige/sounds/" + soundName);
				if (is == null) is = SoundUtil.class.getResourceAsStream("/assets/novaclient/sounds/" + soundName);
				if (is == null) return;
				try (BufferedInputStream bis = new BufferedInputStream(is);
					 AudioInputStream ais = AudioSystem.getAudioInputStream(bis)) {
					Clip clip = AudioSystem.getClip();
					clip.open(ais);
					clip.start();
				}
			} catch (Exception ignored) {}
		});
	}
}
