package dev.tim1337.lumio.api.experimental.util;

import dev.tim1337.lumio.api.spi.Backend;

import net.minecraft.client.KeyMapping;

/**
 * The keys of a key mapping as the player set them, for hints: one key ("R") or a combination of up to three keys and buttons ("Tab + R", "Mouse 4 +
 * E"), which players set on Lumio's Keybinds page for any mapping, also an add-on's. The mapping itself stays the game's: register it as usual, unbound,
 * and check it with {@code matches} or {@code consumeClick}; Lumio lets it act only when its whole combination is pressed.
 */
public final class KeyNames {
	private KeyNames() {
	}

	/** @return the mapping's keys as shown, such as "Tab + R"; empty while it is unbound */
	public static String of(KeyMapping mapping) {
		return Backend.get().keyText(mapping);
	}
}
