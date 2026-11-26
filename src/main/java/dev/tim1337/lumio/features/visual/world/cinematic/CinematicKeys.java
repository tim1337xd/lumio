package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.misc.keybinds.KeyCombos;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The cinematic camera's keys, all unbound at first: one starts and stops it, one leaves it, and while it runs others take the next shot, hold the
 * shot and show or hide the lyrics. Those three do nothing while it is not running; none of these keys counts as the player coming back.
 */
public final class CinematicKeys {
	/** "Lumio: Camera" in the game's key list, shared with the Camera module's keys. */
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Lumio.id("cinematic"));
	// every key unbound at first: the player sets them under Keybinds or in the game's Controls
	static final KeyMapping TOGGLE = key("cinematic_toggle", InputConstants.UNKNOWN.getValue());
	static final KeyMapping LEAVE = key("cinematic_leave", InputConstants.UNKNOWN.getValue());
	static final KeyMapping NEXT = key("cinematic_next", InputConstants.UNKNOWN.getValue());
	static final KeyMapping HOLD = key("cinematic_hold", InputConstants.UNKNOWN.getValue());
	static final KeyMapping LYRICS = key("cinematic_lyrics", InputConstants.UNKNOWN.getValue());
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

	/** Fills in the names of the keys to stop, take the next shot, hold it and show the lyrics; empty for an unbound key. */
	static void names(String[] into) {
		KeyMapping[] keys = {TOGGLE, NEXT, HOLD, LYRICS};
		for (int i = 0; i < into.length && i < keys.length; i++) {
			into[i] = keys[i].isUnbound() ? "" : KeyCombos.shown(keys[i]);
		}
	}
}
