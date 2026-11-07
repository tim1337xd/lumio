package dev.tim1337.lumio.command;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.menu.HomeFeature;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.MacScreen;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * The client command {@code /lumio}. Without arguments it opens the menu; {@code menu}, {@code edithud}, {@code list} and
 * {@code toggle <feature>} do what their names say, and the add-ons add words that print what they read, for troubleshooting.
 */
public final class ModCommands {
	/**
	 * Screen to open on the next tick. Commands run while the chat screen is still open, and closing the chat would close any screen opened
	 * during the command, so opening is deferred by one tick.
	 */
	private static Supplier<Screen> pendingScreen;

	private ModCommands() {
	}

	/** @return whether a command is one of the mod's: /lumio with or without more words */
	public static boolean isOurs(String command) {
		return command.equals(Lumio.MOD_ID) || command.startsWith(Lumio.MOD_ID + " ");
	}

	/**
	 * Runs one of the mod's commands right here, so what another mod does with commands on their way out can't change it and it never goes to the
	 * server. A wrong word gets the game's error in the chat.
	 */
	public static void run(String command) {
		var connection = Minecraft.getInstance().getConnection();
		if (connection == null || !(connection.getSuggestionsProvider() instanceof FabricClientCommandSource source)) {
			return;
		}
		try {
			ClientCommands.getActiveDispatcher().execute(command, source);
		} catch (CommandSyntaxException error) {
			source.sendError(Component.literal(error.getMessage()));
		} catch (RuntimeException error) {
			Logs.warn("The command /{} failed", command, error);
			source.sendError(Component.literal("That did not work. The log says why."));
		}
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(ClientCommands.literal(Lumio.MOD_ID)
				.executes(context -> openLater(MacScreen::new))
				.then(ClientCommands.literal("menu").executes(context -> openLater(MacScreen::new)))
				.then(ClientCommands.literal("edithud").executes(context -> openLater(HudEditScreen::new)))
				.then(ClientCommands.literal("list").executes(context -> {
					for (Feature feature : FeatureManager.features()) {
						if (!feature.isChild() && !(feature instanceof MenuFeature) && !(feature instanceof HomeFeature)) {
							context.getSource().sendFeedback(Component.literal(feature.id() + (feature.isSwitchable() ? (isOn(feature) ? "  on" : "  off") : "")));
						}
					}
					return 1;
				}))
				.then(ClientCommands.literal("toggle").then(ClientCommands.argument("feature", StringArgumentType.word())
					.suggests((context, builder) -> {
						for (Feature feature : FeatureManager.features()) {
							if (feature.isSwitchable()) {
								builder.suggest(feature.id());
							}
						}
						return builder.buildFuture();
					})
					.executes(context -> {
						String id = StringArgumentType.getString(context, "feature");
						for (Feature feature : FeatureManager.features()) {
							if (feature.id().equalsIgnoreCase(id) && feature.isSwitchable()) {
								boolean on = feature.toggle();
								FeatureManager.save();
								context.getSource().sendFeedback(Component.literal(feature.name().getString() + (on ? " is on" : " is off")));
								return 1;
							}
						}
						context.getSource().sendError(Component.literal("There is no feature called " + id + ". /" + Lumio.MOD_ID + " list shows them."));
						return 0;
					}))));
			// what the add-ons describe, as more words of the same command
			for (var diagnostic : Addons.diagnostics().entrySet()) {
				dispatcher.register(ClientCommands.literal(Lumio.MOD_ID).then(ClientCommands.literal(diagnostic.getKey()).executes(context -> {
					for (String line : diagnostic.getValue().get()) {
						context.getSource().sendFeedback(Component.literal(line));
					}
					return 1;
				})));
			}
		});
	}

	/** Opens the screen requested by a command, one tick later. Registered as a client tick handler. */
	public static void openPendingScreen(Minecraft client) {
		if (pendingScreen != null) {
			Supplier<Screen> screen = pendingScreen;
			pendingScreen = null;
			Minecraft.getInstance().setScreenAndShow(screen.get());
		}
	}

	private static int openLater(Supplier<Screen> screen) {
		pendingScreen = screen;
		return 1;
	}

	/** @return whether a feature is enabled; a feature made of parts counts as enabled while any part is */
	private static boolean isOn(Feature feature) {
		return feature.children().isEmpty() ? feature.isEnabled() : feature.children().stream().anyMatch(Feature::isEnabled);
	}
}
