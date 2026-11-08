package com.nova.client.config;

import com.nova.client.module.Module;
import com.nova.client.module.ModuleManager;
import com.nova.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Saves module on/off state and settings to config/novaclient.properties. */
public final class ConfigManager {
	private ConfigManager() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("novaclient.properties");
	}

	public static void load(ModuleManager manager) {
		Path path = file();
		if (!Files.exists(path)) return;
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(path)) {
			props.load(reader);
		} catch (IOException e) {
			return;
		}
		for (Module m : manager.getModules()) {
			String enabled = props.getProperty(m.getName() + ".enabled");
			if (enabled != null) m.setEnabled(Boolean.parseBoolean(enabled));
			for (Setting s : m.getSettings()) {
				String value = props.getProperty(m.getName() + "." + s.name);
				if (value != null) s.deserialize(value);
			}
		}
	}

	public static void save(ModuleManager manager) {
		Properties props = new Properties();
		for (Module m : manager.getModules()) {
			props.setProperty(m.getName() + ".enabled", Boolean.toString(m.isEnabled()));
			for (Setting s : m.getSettings()) {
				props.setProperty(m.getName() + "." + s.name, s.serialize());
			}
		}
		try (Writer writer = Files.newBufferedWriter(file())) {
			props.store(writer, "Nova Client");
		} catch (IOException ignored) {
		}
	}
}
