package dev.tim1337.lumio.features.misc.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A key combination: up to three keys or buttons, the holders first and the last one the trigger ("Tab + R", "G + 1", "Mouse 4 + E"). Any key can be
 * a holder, not only Ctrl, Shift or Alt; mouse buttons and the wheel count as keys (the wheel only as the trigger). Keys are kept by the game's names
 * ("key.keyboard.tab", "key.mouse.4"), which are the same on every Minecraft version; the left and right Ctrl, Shift and Alt are one key each.
 * Plain data, so it is unit tested.
 */
public record KeyCombo(List<String> holders, String trigger) {
	/** The most keys of a combination. */
	public static final int MOST_KEYS = 3;
	/** The wheel, as keys: only ever the trigger. */
	public static final String WHEEL_UP = "lumio.wheel.up";
	public static final String WHEEL_DOWN = "lumio.wheel.down";
	/** The one name for the left and the right key of each modifier. */
	public static final String CONTROL = "lumio.key.control";
	public static final String SHIFT = "lumio.key.shift";
	public static final String ALT = "lumio.key.alt";
	/** No key. */
	public static final KeyCombo NONE = new KeyCombo(List.of(), "");
	/** The longest name of a key that is read from a config. */
	private static final int LONGEST_NAME = 48;

	public KeyCombo {
		holders = List.copyOf(holders);
	}

	/** @return a combination of a single key */
	public static KeyCombo of(String trigger) {
		return new KeyCombo(List.of(), normal(trigger));
	}

	/** @return a combination from its keys in the order pressed: the last is the trigger; null keys and repeats are left out */
	public static KeyCombo pressed(List<String> keys) {
		List<String> clean = new ArrayList<>();
		for (String key : keys) {
			String name = normal(key);
			if (!name.isEmpty() && !clean.contains(name) && clean.size() < MOST_KEYS) {
				clean.add(name);
			}
		}
		if (clean.isEmpty()) {
			return NONE;
		}
		String trigger = clean.removeLast();
		// the wheel is never held: as a holder it is left out
		clean.removeIf(KeyCombo::wheel);
		return new KeyCombo(clean, trigger);
	}

	public boolean bound() {
		return !trigger.isEmpty();
	}

	/** @return the number of keys: 1 for a single key */
	public int size() {
		return bound() ? holders.size() + 1 : 0;
	}

	/** @return the name every key is compared by: the left and right modifiers as one */
	public static String normal(String key) {
		if (key == null) {
			return "";
		}
		return switch (key) {
			case "key.keyboard.left.control", "key.keyboard.right.control" -> CONTROL;
			case "key.keyboard.left.shift", "key.keyboard.right.shift" -> SHIFT;
			case "key.keyboard.left.alt", "key.keyboard.right.alt" -> ALT;
			case "key.keyboard.unknown" -> "";
			default -> key;
		};
	}

	public static boolean wheel(String key) {
		return WHEEL_UP.equals(key) || WHEEL_DOWN.equals(key);
	}

	/** @return whether the combination fires as its trigger goes down while these keys are held */
	public boolean firesWith(String pressed, Set<String> held) {
		if (!bound() || !trigger.equals(normal(pressed))) {
			return false;
		}
		for (String holder : holders) {
			if (!held.contains(holder)) {
				return false;
			}
		}
		return true;
	}

	/** @return whether two combinations are the same keys (the holders in any order): a conflict where both work */
	public boolean sameAs(KeyCombo other) {
		return bound() && trigger.equals(other.trigger) && holders.size() == other.holders.size() && holders.containsAll(other.holders);
	}

	/**
	 * The most specific bind wins: of all binds whose trigger just went down and whose holders are held, only those with the most keys fire. So with
	 * "Tab + R" matching, the plain "R" does not fire on that press.
	 *
	 * @return the number of keys the binds that fire have; 0 when none fires
	 */
	public static int winningSize(Collection<KeyCombo> combos, String pressed, Set<String> held) {
		int best = 0;
		for (KeyCombo combo : combos) {
			if (combo.firesWith(pressed, held)) {
				best = Math.max(best, combo.size());
			}
		}
		return best;
	}

	/** @return whether this combination fires on a press where the binds with so many keys win */
	public boolean wins(String pressed, Set<String> held, int winningSize) {
		return firesWith(pressed, held) && size() == winningSize;
	}

	/** @return the combination as shown: "Tab + R", with each key's shown name */
	public String shown(java.util.function.Function<String, String> names) {
		if (!bound()) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		for (String holder : holders) {
			text.append(names.apply(holder)).append(" + ");
		}
		return text.append(names.apply(trigger)).toString();
	}

	/** @return whether a search finds the combination: every word of it in the shown names ("tab r" finds "Tab + R") */
	public static boolean found(String shown, String search) {
		String text = shown.toLowerCase(Locale.ROOT);
		for (String word : search.toLowerCase(Locale.ROOT).strip().split("[\\s+]+")) {
			if (!word.isEmpty() && !text.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/** @return the keys in order, the trigger last */
	public JsonArray toJson() {
		JsonArray json = new JsonArray();
		holders.forEach(json::add);
		if (bound()) {
			json.add(trigger);
		}
		return json;
	}

	/** @return the combination in a config; NONE for anything unreadable */
	public static KeyCombo fromJson(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return NONE;
		}
		List<String> keys = new ArrayList<>();
		for (JsonElement key : json.getAsJsonArray()) {
			if (key.isJsonPrimitive() && key.getAsString().length() <= LONGEST_NAME && keys.size() < MOST_KEYS) {
				keys.add(key.getAsString());
			}
		}
		return pressed(keys);
	}
}
