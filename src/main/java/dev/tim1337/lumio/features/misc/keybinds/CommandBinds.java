package dev.tim1337.lumio.features.misc.keybinds;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/**
 * When a custom keybind sends its command, without anything of the game, so it can be tested on its own. One press sends one command: holding the
 * key does not repeat it, and a short cooldown after every command stops a double press from sending twice. While a keybind works, it takes its
 * key: the press, its repeats while held, and presses within the cooldown do not reach the game.
 */
public final class CommandBinds {
	/** The shortest time between two commands sent by keybinds. */
	public static final long COOLDOWN_MILLIS = 500L;

	/** What a key event means for the keybinds: nothing, taken (the game does not get it) with or without a command to send. */
	public record Outcome(boolean taken, CommandBind send) {
		public static final Outcome NONE = new Outcome(false, null);
		public static final Outcome TAKEN = new Outcome(true, null);
	}

	/**
	 * Where the player is: in the add-on's world (SkyBlock) or not, the key of the known place there (or null), the area's name in the
	 * compared form of {@link CustomIslands#key} (for the player's own islands), and whether a screen is open.
	 */
	public record Spot(boolean inWorld, String placeKey, String areaKey, boolean screenOpen) {
		public Spot(boolean inWorld, String placeKey, boolean screenOpen) {
			this(inWorld, placeKey, "", screenOpen);
		}
	}

	/** The keys pressed by a keybind and not yet released. */
	private final boolean[] held = new boolean[CommandBind.KEY_LIMIT];
	private long lastSent = Long.MIN_VALUE / 2;

	/**
	 * A key event.
	 *
	 * @param action GLFW's press, repeat or release
	 * @param now    the time, in milliseconds
	 */
	public Outcome key(List<CommandBind> binds, int key, int modifiers, int action, long now, Spot place) {
		if (key < 0 || key >= held.length) {
			return Outcome.NONE;
		}
		if (action == InputConstants.RELEASE) {
			held[key] = false;
			return Outcome.NONE;
		}
		if (action == InputConstants.REPEAT) {
			// held down: the keybind keeps its key, and sends nothing more
			return held[key] ? Outcome.TAKEN : Outcome.NONE;
		}
		CommandBind bind = match(binds, key, modifiers, place);
		if (bind == null) {
			return Outcome.NONE;
		}
		// a press while the key counts as held means its release was lost (the window lost focus): it is a new press, since holding a key
		// only repeats
		if (now - lastSent < COOLDOWN_MILLIS) {
			held[key] = true;
			return Outcome.TAKEN;
		}
		held[key] = true;
		lastSent = now;
		return new Outcome(true, bind);
	}

	/** @return the first keybind in the list for this key and these modifiers that works here, or null */
	public static CommandBind match(List<CommandBind> binds, int key, int modifiers, Spot place) {
		int mods = modifiers & CommandBind.MODIFIERS;
		for (CommandBind bind : binds) {
			if (bind.enabled && bind.key == key && bind.modifiers == mods && bind.validCommand() && applies(bind, place)) {
				return bind;
			}
		}
		return null;
	}

	/** @return whether a keybind works here: outside SkyBlock only those for everywhere, on islands only on the chosen ones */
	public static boolean applies(CommandBind bind, Spot place) {
		if (bind.onlyWithoutScreen && place.screenOpen()) {
			return false;
		}
		return switch (bind.where) {
			case EVERYWHERE -> true;
			case SKYBLOCK -> place.inWorld();
			case ISLANDS -> place.inWorld() && (place.placeKey() != null && bind.islands.contains(place.placeKey()) || bind.onCustomIsland(place.areaKey()));
		};
	}

	/** @return whether two keybinds would both work for the same press: the same key and modifiers, at a place where both work */
	public static boolean overlap(CommandBind first, CommandBind second) {
		if (!first.hasKey() || first.key != second.key || first.modifiers != second.modifiers) {
			return false;
		}
		if (first.where == CommandBind.Where.ISLANDS && second.where == CommandBind.Where.ISLANDS) {
			for (String island : first.islands) {
				if (second.islands.contains(island)) {
					return true;
				}
			}
			for (String island : first.customIslands) {
				if (second.onCustomIsland(CustomIslands.key(island))) {
					return true;
				}
			}
			return false;
		}
		// everywhere covers SkyBlock and every island; SkyBlock covers every island. A keybind for islands without any island works nowhere.
		return !(first.where == CommandBind.Where.ISLANDS && first.noIslands() || second.where == CommandBind.Where.ISLANDS && second.noIslands());
	}

	/** @return the first other switched-on keybind that would work for the same press as this one, or null */
	public static CommandBind conflictOf(List<CommandBind> binds, CommandBind bind) {
		for (CommandBind other : binds) {
			if (other != bind && other.enabled && overlap(bind, other)) {
				return other;
			}
		}
		return null;
	}

	/** @return whether a command can be sent: one line starting with "/", with a command name after it, at most as long as the chat allows */
	public static boolean validCommand(String command) {
		if (command == null || command.length() < 2 || command.length() > CommandBind.MOST_COMMAND || command.charAt(0) != '/'
			|| command.charAt(1) == ' ' || command.charAt(1) == '/') {
			return false;
		}
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			// one command only: no line breaks or other control characters, and no section signs, which the server rejects
			if (c < ' ' || c == 0x7F || c == '§') {
				return false;
			}
		}
		return true;
	}
}
