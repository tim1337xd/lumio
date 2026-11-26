package dev.tim1337.lumio.features.misc.keybinds;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.function.Predicate;

/**
 * When a custom keybind sends its command, without anything of the game, so it can be tested on its own. Of the keybinds on a press, the one with the
 * most keys whose other keys are held sends (with Tab held, "Tab + R" rather than "R"). One press sends one command: holding the
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

	/** Nothing held besides the modifiers. */
	private static final Predicate<String> NOTHING_HELD = key -> false;

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
		return key(binds, key, modifiers, action, now, place, NOTHING_HELD, 0);
	}

	/**
	 * A key event, with the keys held now and how many keys the most specific of the game's or Lumio's other binds on this key has, to give way to.
	 *
	 * @param heldNow   whether a key (by name) is held now
	 * @param giveWayTo the keys of a more specific bind of the game's or Lumio's on this press; a keybind with fewer keys does not take it
	 */
	public Outcome key(List<CommandBind> binds, int key, int modifiers, int action, long now, Spot place, Predicate<String> heldNow, int giveWayTo) {
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
		CommandBind bind = match(binds, InputConstants.Type.KEYSYM.getOrCreate(key).getName(), modifiers, heldNow, giveWayTo, place);
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

	/** @return the first keybind in the list for this key and these modifiers that works here, with no other key held; or null */
	public static CommandBind match(List<CommandBind> binds, int key, int modifiers, Spot place) {
		return match(binds, InputConstants.Type.KEYSYM.getOrCreate(key).getName(), modifiers, NOTHING_HELD, 0, place);
	}

	/**
	 * @param trigger   the name of the key, button or wheel that went down
	 * @param held      whether a key (by name) is held now
	 * @param giveWayTo the keys of a more specific bind of the game's or Lumio's on this press: a keybind with fewer keys gives way
	 * @return of the keybinds for this trigger, these modifiers and held keys that work here, the first with the most keys; or null
	 */
	public static CommandBind match(List<CommandBind> binds, String trigger, int modifiers, Predicate<String> held, int giveWayTo, Spot place) {
		int mods = modifiers & CommandBind.MODIFIERS;
		String pressed = KeyCombo.normal(trigger);
		CommandBind best = null;
		int bestSize = 0;
		for (CommandBind bind : binds) {
			if (!bind.enabled || bind.modifiers != mods || !pressed.equals(KeyCombo.normal(bind.triggerName())) || !allHeld(bind, held) || !bind.validCommand()
				|| !applies(bind, place)) {
				continue;
			}
			int size = bind.combo().size();
			if (size > bestSize) {
				best = bind;
				bestSize = size;
			}
		}
		return bestSize >= giveWayTo ? best : null;
	}

	private static boolean allHeld(CommandBind bind, Predicate<String> held) {
		for (String holder : bind.holders) {
			if (!held.test(holder)) {
				return false;
			}
		}
		return true;
	}

	/** A press of a mouse button or a turn of the wheel: one command per press, after the same cooldown as keys. */
	public Outcome other(List<CommandBind> binds, String trigger, int modifiers, long now, Spot place, Predicate<String> held) {
		CommandBind bind = match(binds, trigger, modifiers, held, 0, place);
		if (bind == null) {
			return Outcome.NONE;
		}
		if (now - lastSent < COOLDOWN_MILLIS) {
			return Outcome.TAKEN;
		}
		lastSent = now;
		return new Outcome(true, bind);
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

	/** @return whether two keybinds would both work for the same press: the same keys (a plain key and a combination with it are not), at a place where both work */
	public static boolean overlap(CommandBind first, CommandBind second) {
		if (!first.hasKey() || !first.combo().sameAs(second.combo())) {
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
