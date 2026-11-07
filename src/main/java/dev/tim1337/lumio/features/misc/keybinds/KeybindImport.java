package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.config.Feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Keybinds in a config someone else shared: a key that sends a command is not loaded with the rest of the config. The player sees every key and
 * command it would add and chooses "Also import keybinds" first; then they are added to the player's own keybinds, never replacing them.
 */
public final class KeybindImport {
	/** The id of the keybinds feature and the key of its list in a config. */
	public static final String FEATURE_ID = "command_keybinds";
	public static final String LIST_KEY = "binds";

	private KeybindImport() {
	}

	/** @return a filter for importing a config: every feature but the keybinds */
	public static Predicate<Feature> withoutKeybinds() {
		return feature -> !FEATURE_ID.equals(feature.id());
	}

	/** @return the keybinds a decoded config brings, in order; empty when it has none or they cannot be read */
	public static List<CommandBind> inConfig(JsonObject root) {
		List<CommandBind> found = new ArrayList<>();
		if (root == null || !(root.get(FEATURE_ID) instanceof JsonObject feature) || !(feature.get(LIST_KEY) instanceof JsonElement list)
			|| !list.isJsonArray()) {
			return found;
		}
		for (JsonElement element : list.getAsJsonArray()) {
			CommandBind bind = CommandBind.load(element);
			if (bind != null) {
				found.add(bind);
			}
		}
		return found;
	}

	/**
	 * @return the config's keybinds that would be added: those with a key and a command that can be sent, not already among the player's (the same
	 *         key, modifiers and command) or twice in the config, and only as many as fit into the list
	 */
	public static List<CommandBind> toAdd(List<CommandBind> current, List<CommandBind> incoming, int most) {
		List<CommandBind> added = new ArrayList<>();
		int room = Math.max(0, most - current.size());
		for (CommandBind bind : incoming) {
			if (added.size() >= room) {
				break;
			}
			if (bind.hasKey() && bind.validCommand() && !contains(current, bind) && !contains(added, bind)) {
				added.add(bind);
			}
		}
		return added;
	}

	private static boolean contains(List<CommandBind> binds, CommandBind wanted) {
		for (CommandBind bind : binds) {
			if (bind.key == wanted.key && bind.modifiers == wanted.modifiers && bind.command.equals(wanted.command)) {
				return true;
			}
		}
		return false;
	}
}
