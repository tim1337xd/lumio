package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;


import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Key bindings for pause, next and previous in the song's player. Unbound by default; the player assigns them in the game's controls. */
public final class MusicKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Lumio.id("music"));

	private static final KeyMapping PLAY_PAUSE = key("play_pause");
	private static final KeyMapping NEXT = key("next");
	private static final KeyMapping PREVIOUS = key("previous");

	private MusicKeys() {
	}

	/**
	 * Registers the key bindings. Must happen during mod initialization: no bindings can be added after the game options are created.
	 *
	 * @return the keys, for the Keys group of the feature's settings
	 */
	public static List<KeyMapping> register() {
		// the bindings are created when this class initializes, which calling this method triggers
		return List.of(PLAY_PAUSE, NEXT, PREVIOUS);
	}

	private static KeyMapping key(String name) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + Lumio.MOD_ID + ".music_" + name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
	}

	/** Called every tick: handles pressed keys. This only sends requests to the local player; nothing goes to a server. */
	public static void tick(Minecraft client) {
		while (PLAY_PAUSE.consumeClick()) {
			control(client, MusicService.Action.PLAY_PAUSE);
		}
		while (NEXT.consumeClick()) {
			control(client, MusicService.Action.NEXT);
		}
		while (PREVIOUS.consumeClick()) {
			control(client, MusicService.Action.PREVIOUS);
		}
	}

	private static void control(Minecraft client, MusicService.Action action) {
		if (ModFeatures.MUSIC.isEnabled() && client.level != null) {
			// the player's button for it animates as if it was clicked
			ModFeatures.MUSIC.controls.pressed(action);
			MusicService.control(action);
		}
	}
}
