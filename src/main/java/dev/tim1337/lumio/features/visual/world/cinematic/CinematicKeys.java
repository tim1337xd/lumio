package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;


import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The cinematic camera's keys: C starts and stops it, Enter leaves it, and while it runs the arrows take the next shot, hold the shot and show or
 * hide the lyrics. The arrows do nothing while it is not running, so they cannot clash with anything; none of these keys counts as the player
 * coming back. All can be rebound.
 */
public final class CinematicKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Lumio.id("cinematic"));
	static final KeyMapping TOGGLE = key("cinematic_toggle", InputConstants.KEY_C);
	static final KeyMapping LEAVE = key("cinematic_leave", InputConstants.KEY_RETURN);
	static final KeyMapping NEXT = key("cinematic_next", InputConstants.KEY_RIGHT);
	static final KeyMapping HOLD = key("cinematic_hold", InputConstants.KEY_DOWN);
	static final KeyMapping LYRICS = key("cinematic_lyrics", InputConstants.KEY_UP);
	private static final List<KeyMapping> ALL = List.of(TOGGLE, LEAVE, NEXT, HOLD, LYRICS);

	private CinematicKeys() {
	}

	private static KeyMapping key(String name, int glfwKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + Lumio.MOD_ID + "." + name, InputConstants.Type.KEYSYM, glfwKey, CATEGORY));
	}

	/**
	 * Registers the key bindings. Must happen during mod initialization, since no bindings can be added after the options are created.
	 *
	 * @return the keys, for the Keys group of the feature's settings
	 */
	public static List<KeyMapping> register() {
		// the bindings are created when this class initializes, which calling this method triggers
		return ALL;
	}

	/** Called every tick: lets the camera observe the player's input, and handles its keys. */
	public static void tick(Minecraft client) {
		boolean toggle = pressed(TOGGLE);
		boolean leave = pressed(LEAVE);
		boolean next = pressed(NEXT);
		boolean hold = pressed(HOLD);
		boolean lyrics = pressed(LYRICS);
		CinematicCameraFeature cinematic = ModFeatures.CINEMATIC;
		if (leave && client.gui.screen() == null) {
			cinematic.leave();
		}
		cinematic.tick(client, ALL);
		if (toggle) {
			cinematic.toggle(client);
		}
		if (next) {
			cinematic.nextShot();
		}
		if (hold) {
			cinematic.holdShot();
		}
		if (lyrics) {
			cinematic.toggleLyrics();
		}
	}

	private static boolean pressed(KeyMapping key) {
		boolean pressed = false;
		while (key.consumeClick()) {
			pressed = true;
		}
		return pressed;
	}

	/** Fills in the names of the keys to stop, take the next shot, hold it and show the lyrics, short for the arrows. */
	static void names(String[] into) {
		KeyMapping[] keys = {TOGGLE, NEXT, HOLD, LYRICS};
		for (int i = 0; i < into.length && i < keys.length; i++) {
			into[i] = shortName(keys[i]);
		}
	}

	private static String shortName(KeyMapping key) {
		if (key.isUnbound()) {
			return "";
		}
		if (key.same(NEXT) && NEXT.isDefault()) {
			return "→";
		}
		if (key.same(HOLD) && HOLD.isDefault()) {
			return "↓";
		}
		if (key.same(LYRICS) && LYRICS.isDefault()) {
			return "↑";
		}
		return key.getTranslatedKeyMessage().getString();
	}
}
