package com.nova.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.nova.client.config.ConfigManager;
import com.nova.client.gui.ClickGuiScreen;
import com.nova.client.gui.Hud;
import com.nova.client.module.Module;
import com.nova.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class NovaClient implements ClientModInitializer {
	public static ModuleManager modules;
	private static KeyMapping openGui;
	private static final Map<Module, KeyMapping> moduleKeys = new LinkedHashMap<>();

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		ConfigManager.load(modules);

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath("novaclient", "hud"),
			Hud::extract);

		// Right Shift opens the click GUI (rebindable in Options > Controls)
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("novaclient", "main"));
		openGui = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.novaclient.open_gui", GLFW.GLFW_KEY_RIGHT_SHIFT, category));

		// One unbound key per module: set them in Options > Controls > Nova Client
		for (Module m : modules.getModules()) {
			String id = "key.novaclient.module." + m.getName().toLowerCase(Locale.ROOT).replace(' ', '_');
			moduleKeys.put(m, KeyMappingHelper.registerKeyMapping(
				new KeyMapping(id, GLFW.GLFW_KEY_UNKNOWN, category)));
		}

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> modules.reset());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save(modules));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openGui.consumeClick()) {
				if (mc.screen == null) mc.setScreen(new ClickGuiScreen());
			}
			moduleKeys.forEach((module, key) -> {
				while (key.consumeClick()) {
					module.toggle();
					ConfigManager.save(modules);
					if (mc.player != null) {
						mc.player.sendOverlayMessage(Component.literal(
							module.getName() + (module.isEnabled() ? " enabled" : " disabled")));
					}
				}
			});
			if (mc.player != null && mc.level != null) modules.tick();
		});

		// /nova and /prestige commands:
		//   list            -> show all modules and their state
		//   toggle <name>   -> toggle a module
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String cmdName : new String[]{"nova", "prestige"}) {
				dispatcher.register(ClientCommands.literal(cmdName)
					.then(ClientCommands.literal("list").executes(ctx -> {
						for (Module m : modules.getModules()) {
							ctx.getSource().sendFeedback(Component.literal(
								(m.isEnabled() ? "§a[ON]  §f" : "§c[OFF] §7") + m.getName() + "  §8(" + m.getCategory().label + ")"));
						}
						return 1;
					}))
					.then(ClientCommands.literal("toggle")
						.then(ClientCommands.argument("module", StringArgumentType.greedyString())
							.suggests((c, b) -> {
								modules.getModules().forEach(m -> b.suggest(m.getName()));
								return b.buildFuture();
							})
							.executes(ctx -> {
								String name = StringArgumentType.getString(ctx, "module");
								return modules.get(name).map(m -> {
									m.toggle();
									ConfigManager.save(modules);
									ctx.getSource().sendFeedback(Component.literal(
										"§7[Prestige] §f" + m.getName() + (m.isEnabled() ? " §aenabled" : " §cdisabled")));
									return 1;
								}).orElseGet(() -> {
									ctx.getSource().sendError(Component.literal("Unknown module: " + name));
									return 0;
								});
							}))));
			}
		});
	}
}
