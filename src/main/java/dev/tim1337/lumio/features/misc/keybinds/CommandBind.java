package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import com.mojang.blaze3d.platform.InputConstants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * One custom keybind: a key (with Ctrl, Shift or Alt) that sends one command, as if the player typed it. Where it works: everywhere, only in
 * SkyBlock, or only on some islands; by default only while no screen is open.
 */
public final class CommandBind {
	/** Where a keybind works. */
	public enum Where {
		// SKYBLOCK means the world of the add-on that knows where the player is; the name stays for the saved keybinds
		EVERYWHERE("Everywhere"), SKYBLOCK("SkyBlock"), ISLANDS("Only on islands");

		public final String label;

		Where(String label) {
			this.label = label;
		}
	}

	/** The modifier keys a keybind can have; Caps Lock, Num Lock and the system key are ignored. */
	public static final int MODIFIERS = ScreenInput.SHIFT | ScreenInput.CONTROL | ScreenInput.ALT;
	/** The longest command kept, as the chat allows. */
	public static final int MOST_COMMAND = 256;
	/** No key yet. */
	public static final int NO_KEY = -1;
	/** Above every key number of the game, on every version. */
	public static final int KEY_LIMIT = 512;

	public int key = NO_KEY;
	public int modifiers;
	public String command = "/";
	public Where where = Where.EVERYWHERE;
	/** The most places a keybind keeps, and the longest key of one. */
	public static final int MOST_PLACES = 64;
	private static final int LONGEST_PLACE_KEY = 40;

	/** The places of the add-on it works on, by key ({@link KeybindPlaces}); kept also for places no add-on knows now. */
	public final Set<String> islands = new LinkedHashSet<>();
	/** The player's own islands it works on, by name (see {@link CustomIslands}). */
	public final Set<String> customIslands = new LinkedHashSet<>();
	public boolean enabled = true;
	public boolean onlyWithoutScreen = true;

	/** @return whether a number from a config or an event is a key a keybind can have */
	public static boolean validKey(int key) {
		return key > 0 && key < KEY_LIMIT;
	}

	/** @return whether a key is set */
	public boolean hasKey() {
		return key != NO_KEY;
	}

	/** @return whether the command is one the keybind can send: a single command starting with "/", with something after it */
	public boolean validCommand() {
		return CommandBinds.validCommand(command);
	}

	/** @return whether no island is picked, built-in or the player's own */
	public boolean noIslands() {
		return islands.isEmpty() && customIslands.isEmpty();
	}

	/** @return whether it works on an island of the player's own, by its name in the compared form ({@link CustomIslands#key}) */
	public boolean onCustomIsland(String areaKey) {
		if (areaKey == null || areaKey.isEmpty()) {
			return false;
		}
		for (String island : customIslands) {
			if (areaKey.equals(CustomIslands.key(island))) {
				return true;
			}
		}
		return false;
	}

	/** @return a copy, for Duplicate */
	public CommandBind copy() {
		CommandBind copy = new CommandBind();
		copy.key = key;
		copy.modifiers = modifiers;
		copy.command = command;
		copy.where = where;
		copy.islands.addAll(islands);
		copy.customIslands.addAll(customIslands);
		copy.enabled = enabled;
		copy.onlyWithoutScreen = onlyWithoutScreen;
		return copy;
	}

	public JsonObject save() {
		JsonObject json = new JsonObject();
		json.addProperty("key", key);
		// the numbers of the keys differ between Minecraft versions: the name is what another version reads
		if (key != NO_KEY) {
			json.addProperty("key_name", InputConstants.Type.KEYSYM.getOrCreate(key).getName());
		}
		json.addProperty("modifiers", modifiers);
		json.addProperty("command", command);
		json.addProperty("where", where.name());
		JsonArray list = new JsonArray();
		for (String island : islands) {
			list.add(island);
		}
		json.add("islands", list);
		JsonArray custom = new JsonArray();
		for (String island : customIslands) {
			custom.add(island);
		}
		json.add("custom_islands", custom);
		json.addProperty("enabled", enabled);
		json.addProperty("only_without_screen", onlyWithoutScreen);
		return json;
	}

	/** @return a place key from a config: lower case letters, digits and "_" only, and not too long; empty when nothing is left */
	static String placeKey(String raw) {
		String lower = raw.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < lower.length() && out.length() < LONGEST_PLACE_KEY; i++) {
			char c = lower.charAt(i);
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
				out.append(c);
			}
		}
		return out.toString();
	}

	/** @return the keybind in a config, or null when it is unreadable; values out of range are left at their defaults */
	public static CommandBind load(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		try {
			JsonObject json = element.getAsJsonObject();
			CommandBind bind = new CommandBind();
			if (json.has("key")) {
				// without a name it comes from a version with the numbers of 26.2
				int key = GlfwKeys.toGame(json.get("key").getAsInt());
				bind.key = validKey(key) ? key : NO_KEY;
			}
			if (json.has("key_name")) {
				int named = InputConstants.getKey(json.get("key_name").getAsString()).getValue();
				bind.key = validKey(named) ? named : NO_KEY;
			}
			if (json.has("modifiers")) {
				bind.modifiers = json.get("modifiers").getAsInt() & MODIFIERS;
			}
			if (json.has("command")) {
				String command = json.get("command").getAsString();
				bind.command = command.length() > MOST_COMMAND ? command.substring(0, MOST_COMMAND) : command;
			}
			if (json.has("where")) {
				for (Where where : Where.values()) {
					if (where.name().equals(json.get("where").getAsString())) {
						bind.where = where;
					}
				}
			}
			if (json.has("islands") && json.get("islands").isJsonArray()) {
				for (JsonElement island : json.getAsJsonArray("islands")) {
					String key = placeKey(island.getAsString());
					if (!key.isEmpty() && bind.islands.size() < MOST_PLACES) {
						bind.islands.add(key);
					}
				}
			}
			if (json.has("custom_islands") && json.get("custom_islands").isJsonArray()) {
				for (JsonElement island : json.getAsJsonArray("custom_islands")) {
					String name = CustomIslands.clean(island.getAsString());
					if (!CustomIslands.key(name).isEmpty() && bind.customIslands.size() < CustomIslands.MOST) {
						bind.customIslands.add(name);
					}
				}
			}
			if (json.has("enabled")) {
				bind.enabled = json.get("enabled").getAsBoolean();
			}
			if (json.has("only_without_screen")) {
				bind.onlyWithoutScreen = json.get("only_without_screen").getAsBoolean();
			}
			return bind;
		} catch (RuntimeException error) {
			return null;
		}
	}
}
