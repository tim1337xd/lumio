package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.Threads;
import dev.tim1337.lumio.mixin.accessor.KeyMappingAccessor;
import dev.tim1337.lumio.util.HeldButtons;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Key combinations for the game's key mappings (Lumio's, its add-ons' and, through the same page, any other): kept by the mapping's name in a file of
 * Lumio's own (`key-combos.json`), while the mapping's own key (the trigger) stays where the game keeps it, so an old single-key bind loads unchanged and
 * a config shared with others never changes anyone's keys. When a key goes down, of the mappings on it only those with the most keys whose holders are
 * held act: with "Tab + R" matching, the plain "R" (Lumio's or the game's) does nothing on that press, while Tab itself works as always. What is held is
 * read from the keyboard and the mouse at that moment, so a lost release can never leave a key stuck.
 */
public final class KeyCombos {
	private static final String FILE = "key-combos.json";
	/** Bounds the file: more binds than the game has. */
	private static final int MOST = 512;
	private static final Map<String, KeyCombo> BY_NAME = new HashMap<>();
	/** Every holder key of every combination, to read only those when a key goes down. */
	private static final Set<String> HOLDERS = new HashSet<>();
	private static final Set<String> HELD = new HashSet<>();
	private static final List<KeyCombo> CANDIDATES = new ArrayList<>();
	private static final ExecutorService WRITER = Threads.daemonExecutor("Lumio key combinations");
	private static int version;

	private KeyCombos() {
	}

	/** @return whether any mapping has a combination: without one the game's keys go their own way, untouched */
	public static boolean any() {
		return !BY_NAME.isEmpty();
	}

	/** @return a number that changes with every combination set */
	public static int version() {
		return version;
	}

	/** @return a mapping's combination: its own key alone when it has none */
	public static KeyCombo of(KeyMapping mapping) {
		KeyCombo combo = BY_NAME.get(mapping.getName());
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		String own = InputConstants.UNKNOWN.equals(key) ? "" : KeyCombo.normal(key.getName());
		// a key set again in the game's own controls (without Lumio) goes alone: the combination it had is for another key
		if (combo != null && (combo.trigger().equals(own) || KeyCombo.wheel(combo.trigger()) && own.isEmpty())) {
			return combo;
		}
		return own.isEmpty() ? KeyCombo.NONE : KeyCombo.of(own);
	}

	/** @return a mapping's combination as shown: "Tab + R", or the key's own name */
	public static String shown(KeyMapping mapping) {
		KeyCombo combo = of(mapping);
		return combo.size() > 1 || KeyCombo.wheel(combo.trigger()) ? combo.shown(KeyCombos::keyName)
			: mapping.isUnbound() ? "" : mapping.getTranslatedKeyMessage().getString();
	}

	/** @return a key's shown name: "Tab", "Mouse 4", "Wheel up" */
	public static String keyName(String name) {
		return switch (name) {
			case KeyCombo.WHEEL_UP -> "Wheel up";
			case KeyCombo.WHEEL_DOWN -> "Wheel down";
			case KeyCombo.CONTROL -> "Ctrl";
			case KeyCombo.SHIFT -> "Shift";
			case KeyCombo.ALT -> "Alt";
			default -> InputConstants.getKey(name).getDisplayName().getString();
		};
	}

	/**
	 * Binds a mapping to a combination: its last key becomes the mapping's own key (none for the wheel, which the mapping cannot have), the others are
	 * kept here. A single key keeps nothing here.
	 */
	public static void bind(KeyMapping mapping, KeyCombo combo) {
		mapping.setKey(InputConstants.getKey(ownKey(combo)));
		remember(mapping.getName(), combo);
		KeyMapping.resetMapping();
		changed();
	}

	/**
	 * @return the key the game keeps for a mapping bound to a combination (in options.txt): its trigger, or the game's "unknown" key for none (a removed
	 *         key, which the game saves and loads as removed, never as its default) and for the wheel
	 */
	static String ownKey(KeyCombo combo) {
		String trigger = combo.trigger();
		return !combo.bound() || KeyCombo.wheel(trigger) ? InputConstants.UNKNOWN.getName() : original(trigger);
	}

	/** Keeps a mapping's combination in Lumio's file, or forgets it for a single key or none. */
	static void remember(String name, KeyCombo combo) {
		if (combo.size() > 1 || KeyCombo.wheel(combo.trigger())) {
			BY_NAME.put(name, combo);
		} else {
			BY_NAME.remove(name);
		}
	}

	/** @return the combination kept for a mapping, or null when it has none here (a single key, or none) */
	static KeyCombo kept(String name) {
		return BY_NAME.get(name);
	}

	/** @return a key's name the game knows: the left one of a modifier */
	private static String original(String normal) {
		return switch (normal) {
			case KeyCombo.CONTROL -> "key.keyboard.left.control";
			case KeyCombo.SHIFT -> "key.keyboard.left.shift";
			case KeyCombo.ALT -> "key.keyboard.left.alt";
			default -> normal;
		};
	}

	private static void changed() {
		version++;
		HOLDERS.clear();
		for (KeyCombo combo : BY_NAME.values()) {
			HOLDERS.addAll(combo.holders());
		}
		save();
	}

	// ------------------------------------------------------------------------------------------------ what is held

	/** @return the holder keys held now, read from the keyboard and the mouse; only those of combinations are read */
	public static Set<String> held() {
		HELD.clear();
		for (String holder : HOLDERS) {
			if (down(holder)) {
				HELD.add(holder);
			}
		}
		return HELD;
	}

	/** @return whether a key or mouse button is down now */
	public static boolean down(String name) {
		return switch (name) {
			case KeyCombo.CONTROL -> keyDown(InputConstants.KEY_LCONTROL) || keyDown(InputConstants.KEY_RCONTROL);
			case KeyCombo.SHIFT -> keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT);
			case KeyCombo.ALT -> keyDown(InputConstants.KEY_LALT) || keyDown(InputConstants.KEY_RALT);
			default -> {
				if (KeyCombo.wheel(name)) {
					yield false;
				}
				InputConstants.Key key = InputConstants.getKey(name);
				yield key.getType() == InputConstants.Type.MOUSE ? HeldButtons.down(key.getValue())
					: key.getType() == InputConstants.Type.KEYSYM && keyDown(key.getValue());
			}
		};
	}

	private static boolean keyDown(int key) {
		//#if 26.3
		//$ return InputConstants.isKeyDown(key);
		//#else
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
		//#endif
	}

	// ------------------------------------------------------------------------------------------------ which mappings act

	/**
	 * Of the mappings on a key that just went down, the ones that act: those with the most keys whose holders are held.
	 *
	 * @param into filled with them
	 */
	public static void winners(InputConstants.Key pressed, List<KeyMapping> onKey, List<KeyMapping> into) {
		into.clear();
		String name = KeyCombo.normal(pressed.getName());
		Set<String> held = held();
		CANDIDATES.clear();
		for (KeyMapping mapping : onKey) {
			CANDIDATES.add(of(mapping));
		}
		int best = KeyCombo.winningSize(CANDIDATES, name, held);
		for (int i = 0; i < onKey.size(); i++) {
			if (CANDIDATES.get(i).wins(name, held, best)) {
				into.add(onKey.get(i));
			}
		}
	}

	/** @return whether a mapping that matches a key a screen got may act on it: it is among the most specific on that key */
	public static boolean actsInScreen(KeyMapping mapping, InputConstants.Key pressed) {
		if (!any()) {
			return true;
		}
		List<KeyMapping> onKey = KeyMappingAccessor.lumio$map().get(pressed);
		if (onKey == null) {
			return of(mapping).firesWith(pressed.getName(), held());
		}
		List<KeyMapping> winners = new ArrayList<>(onKey.size());
		winners(pressed, onKey, winners);
		return winners.contains(mapping);
	}

	/** @return how many keys the most specific mapping on a key has right now, for the command keybinds to give way to it; 0 for none */
	public static int mostSpecific(InputConstants.Key pressed) {
		List<KeyMapping> onKey = KeyMappingAccessor.lumio$map().get(pressed);
		if (onKey == null || !any()) {
			return 0;
		}
		CANDIDATES.clear();
		for (KeyMapping mapping : onKey) {
			CANDIDATES.add(of(mapping));
		}
		return KeyCombo.winningSize(CANDIDATES, KeyCombo.normal(pressed.getName()), held());
	}

	/** A turn of the wheel: the mappings on it act once each, the most specific ones. @return whether one did */
	public static boolean wheel(boolean up) {
		if (!any()) {
			return false;
		}
		String name = up ? KeyCombo.WHEEL_UP : KeyCombo.WHEEL_DOWN;
		Set<String> held = held();
		List<KeyCombo> combos = new ArrayList<>(BY_NAME.values());
		int best = KeyCombo.winningSize(combos, name, held);
		if (best == 0) {
			return false;
		}
		boolean acted = false;
		for (KeyMapping mapping : KeyMappingAccessor.lumio$all().values()) {
			KeyCombo combo = BY_NAME.containsKey(mapping.getName()) ? of(mapping) : null;
			if (combo != null && combo.wins(name, held, best)) {
				((KeyMappingAccessor) mapping).lumio$setClickCount(((KeyMappingAccessor) mapping).lumio$clickCount() + 1);
				acted = true;
			}
		}
		return acted;
	}

	// ------------------------------------------------------------------------------------------------ the file

	/** Reads the file once while the game starts (it is small); a broken one is left as it is and nothing is combined. */
	public static void load() {
		Path file = ModFiles.file(FILE);
		try {
			if (Files.isRegularFile(file)) {
				read(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
			}
		} catch (IOException | RuntimeException e) {
			Lumio.LOGGER.warn("Could not read the key combinations", e);
		}
	}

	/** Takes the combinations of a file in; broken ones are left out. */
	static void read(JsonObject json) {
		BY_NAME.clear();
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			KeyCombo combo = KeyCombo.fromJson(entry.getValue());
			if (combo.bound() && BY_NAME.size() < MOST) {
				BY_NAME.put(entry.getKey(), combo);
			}
		}
		version++;
		HOLDERS.clear();
		for (KeyCombo combo : BY_NAME.values()) {
			HOLDERS.addAll(combo.holders());
		}
	}

	static JsonObject write() {
		JsonObject json = new JsonObject();
		BY_NAME.forEach((name, combo) -> json.add(name, combo.toJson()));
		return json;
	}

	private static void save() {
		String text = new Gson().toJson(write());
		Path file = ModFiles.file(FILE);
		WRITER.execute(() -> {
			try {
				ModFiles.writeAtomically(file, text);
			} catch (IOException e) {
				Lumio.LOGGER.warn("Could not keep the key combinations", e);
			}
		});
	}
}
