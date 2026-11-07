package dev.tim1337.lumio;

import dev.tim1337.lumio.config.PageOutlines;
import dev.tim1337.lumio.api.experimental.util.HudCover;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.addon.AddonLoader;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.command.ModCommands;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.features.music.MusicKeys;
import dev.tim1337.lumio.features.visual.hud.HudStack;
import dev.tim1337.lumio.features.visual.world.AmbienceParticles;
import dev.tim1337.lumio.features.visual.world.SwingTrace;
import dev.tim1337.lumio.features.visual.world.cinematic.CinematicKeys;
import dev.tim1337.lumio.render.AmbienceDraw;
import dev.tim1337.lumio.render.SkyMeteorDraw;
import dev.tim1337.lumio.render.MenuGameTextures;
import dev.tim1337.lumio.render.ModPipelines;
import dev.tim1337.lumio.ui.ScreenTitles;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MenuGear;
import dev.tim1337.lumio.ui.MissingGlyphs;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.UiSounds;
import dev.tim1337.lumio.ui.setup.SetupScreen;
import dev.tim1337.lumio.util.ModVersion;
import dev.tim1337.lumio.util.Profiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import static dev.tim1337.lumio.ModFeatures.*;

/**
 * Client entrypoint of Lumio. Creates the features (see {@link ModFeatures}), loads their settings and registers the event hooks,
 * HUD elements, commands and key bindings in the order the features rely on.
 */
public class Lumio implements ClientModInitializer {
	public static final String MOD_ID = "lumio";

	/** Shared logger of the mod, named after the mod id. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	static {
		// Create all features as soon as this class is loaded. That can happen before onInitializeClient (an early mixin calling id() loads
		// this class), and code running that early may already look features up.
		ModFeatures.createAll();
	}

	/** @return whether a screen of the mod is open, all with text fields of their own: they ask for typed text while they are open; see ScreenInput */
	private static boolean takesTyping(net.minecraft.client.gui.screens.Screen screen) {
		return screen instanceof dev.tim1337.lumio.ui.MacScreen || screen instanceof dev.tim1337.lumio.ui.MacOptionsScreen || screen instanceof dev.tim1337.lumio.ui.MacTitleScreen
			|| screen instanceof dev.tim1337.lumio.ui.HudEditScreen || screen instanceof dev.tim1337.lumio.ui.setup.SetupScreen || screen instanceof dev.tim1337.lumio.features.music.SourceSetupScreen;
	}

	@Override
	public void onInitializeClient() {
		Logs.info("Lumio {} for Minecraft {}", ModVersion.forLog(), SharedConstants.getCurrentVersion().name());
		HudCover.add(ScreenTitles::hidesHud);
		HudCover.add(dev.tim1337.lumio.ui.ColoredCustomizeScreen::hidesHud);
		// Prepare the mod's shaders now; ModPipelines compiles them one by one right after the game has loaded instead of on first use, which
		// used to stall the menu fade-in and the first frames of a world for a good part of a second. They are not registered as required
		// pipelines, so a shader the GPU driver rejects cannot block the game from loading.
		for (String name : new String[]{"dev.tim1337.lumio.render.CustomSky", "dev.tim1337.lumio.render.MenuWorld", "dev.tim1337.lumio.render.GlassPanes", "dev.tim1337.lumio.render.OutlineRenderTypes", "dev.tim1337.lumio.render.RoundedShapes", "dev.tim1337.lumio.render.Arcs"}) {
			try {
				Class.forName(name, true, Lumio.class.getClassLoader());
			} catch (ClassNotFoundException | LinkageError e) {
				Logs.warn("Could not prepare the shaders of {}", name, e);
			}
		}
		// the add-ons register their features and providers now: after the features of the main mod exist, before the settings are read
		PageOutlines.apply(MOD_ID);
		AddonLoader.load();
		dev.tim1337.lumio.ModFeatures.CHAT.addonsLoaded();
		FeatureManager.load();
		// the game's crop and particle textures for the main menu's world, read while the game loads
		MenuGameTextures.register();
		UiSounds.warmUp();
		// Remember the joined server for the main menu's "rejoin" button
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			var server = client.getCurrentServer();
			if (server != null && server.ip != null) {
				MENUS.rememberServer(server.name, server.ip);
			}
		});
		// The HUD may use its own theme: it is applied from the first HUD element to the last, menus keep the menu theme
		HudElementRegistry.addFirst(id("hud_theme_begin"), (graphics, tracker) -> {
			HudEditScreen.dimWorld(graphics);
			HudStyle.beginHud();
			HudStack.newFrame();
		});
		HudElementRegistry.addLast(id("hud_theme_end"), (graphics, tracker) -> HudStyle.endHud());
		// what stands at a place in the world lies under all of the HUD: before everything that is attached behind the game's first element
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, id("world_labels"), dev.tim1337.lumio.api.experimental.util.WorldLabels::draw);
		// Error notifications, drawn over the game and over every screen
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("toasts"), Toasts::renderHud);
		// the first person swing's values, while the Debug log shows them
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("swing_trace"), (graphics, tracker) -> SwingTrace.draw(graphics));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
			ScreenEvents.afterExtract(screen).register((shown, graphics, mouseX, mouseY, delta) -> {
				Toasts.render(graphics);
				if (takesTyping(shown)) {
					ScreenInput.wantText();
				}
				ScreenInput.applyText();
			}));
		// without a screen no frame above says it any more
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.gui.screen() == null) {
				ScreenInput.applyText();
			}
		});
		BLOCK_OVERLAY.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("action_bar_hud"), ACTION_BAR::render);
		MUSIC.registerKeys();
		CINEMATIC.registerKeys();
		MenuGear.register();
		Toasts.register();
		// which symbols the menu font lacks, read from the font file in the background
		MissingGlyphs.load();

		ModCommands.register();

		ClientTickEvents.END_CLIENT_TICK.register(ModPipelines::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> AmbienceParticles.tick(client, AMBIENCE));
		ClientTickEvents.END_CLIENT_TICK.register(SetupScreen::tick);
		ClientTickEvents.END_CLIENT_TICK.register(BORDERLESS::tick);
		ClientTickEvents.END_CLIENT_TICK.register(CHAT::tick);
		// The music players' controls take clicks on screens while the mouse is free, where the screen has nothing of its own
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> !MUSIC.playerClick(shown, event.x(), event.y(), ScreenInput.button(event)));
			ScreenMouseEvents.allowMouseRelease(screen).register((shown, event) -> !MUSIC.playerRelease(ScreenInput.button(event)));
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(AmbienceDraw::collect);
		LevelRenderEvents.COLLECT_SUBMITS.register(SkyMeteorDraw::collect);
		ClientTickEvents.END_CLIENT_TICK.register(WALK::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ARROW_TRAILS::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(ARROW_TRAILS::collect);
		ClientTickEvents.END_CLIENT_TICK.register(client -> FeatureManager.flushIfDue());
		// JFR profiling while the debug setting for it is enabled
		ClientTickEvents.END_CLIENT_TICK.register(client -> Profiler.tick(DEBUG != null && DEBUG.recordsProfile()));
		// Flush pending setting changes when the game closes
		Runtime.getRuntime().addShutdownHook(new Thread(FeatureManager::saveNow, "Lumio settings"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> MUSIC.warmUp());
		ClientTickEvents.END_CLIENT_TICK.register(MusicKeys::tick);
		ClientTickEvents.END_CLIENT_TICK.register(CinematicKeys::tick);
		// While the HUD is hidden, the letterbox bars and subtitles are drawn by a mixin; otherwise they are drawn here, above the HUD
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("cinematic_overlay"), (graphics, tracker) -> {
			if (!CINEMATIC.hidesHud()) {
				CINEMATIC.renderOverlay(graphics, tracker);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(ModCommands::openPendingScreen);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("info_bar"), INFO_BAR::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("music_window"), MUSIC::renderWindow);
		LevelRenderEvents.COLLECT_SUBMITS.register(MUSIC::collectWorld);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("armor_hud"), ARMOR::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("inventory_hud"), INVENTORY::render);
		// Replace the vanilla hotbar and armor bar while the corresponding features are enabled
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, original -> (graphics, tracker) -> {
			if (HOTBAR.isEnabled()) {
				graphics.nextStratum(); // own layer, like every panel
				HOTBAR.render(graphics, tracker);
			} else {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id("item_name"), HOTBAR::renderName);
		// Replace the vanilla boss bars while the feature is enabled
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> (graphics, tracker) -> {
			if (BOSS_BARS.replacesVanilla()) {
				BOSS_BARS.render(graphics, tracker);
			} else {
				original.extractRenderState(graphics, tracker);
			}
		});
		// The vanilla item name and action bar move up above the stacked bars, so they never cover them
		HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, original -> (graphics, tracker) -> {
			if (!HOTBAR.hidesVanillaName()) {
				boolean lifted = HudStack.beginLift(graphics, HudStack.vanillaNameBottom(graphics.guiHeight()), true);
				original.extractRenderState(graphics, tracker);
				HudStack.endLift(graphics, lifted);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, original -> (graphics, tracker) -> {
			boolean lifted = HudStack.beginLift(graphics, HudStack.vanillaActionBarBottom(graphics.guiHeight()), false);
			original.extractRenderState(graphics, tracker);
			HudStack.endLift(graphics, lifted);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, original -> (graphics, tracker) -> {
			if (!STATUS.hidesVanillaAir()) {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("scoreboard_hud"), SCOREBOARD::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.PLAYER_LIST, id("tab_list_hud"), TAB_LIST::render);
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, tracker) -> {
			if (!SCOREBOARD.hidesVanilla()) {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, original -> (graphics, tracker) -> {
			if (!TAB_LIST.hidesVanilla()) {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, original -> (graphics, tracker) -> {
			if (!ARMOR.hidesVanillaBar() && !STATUS.hidesVanillaArmor()) {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("status_bars"), STATUS::render);
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, original -> (graphics, tracker) -> {
			if (!STATUS.hidesVanillaHealthFood()) {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original -> (graphics, tracker) -> {
			if (!STATUS.hidesVanillaHealthFood()) {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, original -> (graphics, tracker) -> {
			if (!STATUS.hidesVanillaXp()) {
				original.extractRenderState(graphics, tracker);
			}
		});
		// The info bar slot below the hotbar also holds the horse jump bar, which is left untouched
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, original -> (graphics, tracker) -> {
			Minecraft client = Minecraft.getInstance();
			if (!STATUS.hidesVanillaXp() || client.player == null || client.player.jumpableVehicle() != null) {
				original.extractRenderState(graphics, tracker);
			}
		});

	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
