package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.api.Place;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The islands the player added for keybinds beyond the built-in ones, such as a new island the mod does not know yet. They are kept once for all
 * keybinds; a keybind picks some of them by name. Names match the player list's area name loosely: case, color codes, extra spaces and symbols
 * do not count, so "Crimson Isle", "crimson  isle" and "§cCrimson Isle!" are the same island. A name of a built-in island is never added again.
 */
public final class CustomIslands {
	/** The most islands kept, and the longest name. */
	public static final int MOST = 24;
	public static final int LONGEST_NAME = 32;
	/** Separates the names in the saved setting; names never contain it. */
	public static final String SEPARATOR = "\n";
	private static final char SECTION = '§';

	/** The known places, which are never added as the player's own. */
	private final Supplier<List<Place>> known;
	private final List<String> names = new ArrayList<>();
	private final List<String> view = Collections.unmodifiableList(names);

	/** The player's own islands, next to the places the add-on that knows the world has. */
	public CustomIslands() {
		this(KeybindPlaces::places);
	}

	/** @param known the known places, which are never added as the player's own */
	public CustomIslands(Supplier<List<Place>> known) {
		this.known = known;
	}

	/** @return the islands, in the order they were added */
	public List<String> names() {
		return view;
	}

	/**
	 * Cleans a name as typed or as the player list shows it: without color codes and control characters, with single spaces, cut to the longest
	 * name.
	 *
	 * @return the cleaned name, empty when nothing is left
	 */
	public static String clean(String name) {
		if (name == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(Math.min(name.length(), LONGEST_NAME));
		boolean space = false;
		for (int i = 0; i < name.length() && out.length() < LONGEST_NAME; i++) {
			char c = name.charAt(i);
			if (c == SECTION) {
				i++; // the code after the sign
				continue;
			}
			if (Character.isWhitespace(c) || Character.isISOControl(c)) {
				space = !out.isEmpty();
				continue;
			}
			if (space) {
				out.append(' ');
				space = false;
			}
			out.append(c);
		}
		return out.toString();
	}

	/** @return the form two names are compared in: lower case letters and digits only, words joined by single spaces */
	public static String key(String name) {
		String cleaned = clean(name);
		StringBuilder out = new StringBuilder(cleaned.length());
		boolean space = false;
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				if (space && !out.isEmpty()) {
					out.append(' ');
				}
				space = false;
				out.append(Character.toLowerCase(c));
			} else if (c == ' ') {
				space = true;
			}
			// other symbols (an apostrophe, a star) are left out without breaking the word
		}
		return out.toString();
	}

	/** @return whether two names are the same island */
	public static boolean same(String first, String second) {
		String key = key(first);
		return !key.isEmpty() && key.equals(key(second));
	}

	/** @return the known place of a name (its name or a name the server shows for it), from the add-on that knows the world, or null */
	public static Place builtIn(String name) {
		return builtIn(name, KeybindPlaces.places());
	}

	/** @return the place of a name among these places (its name or one of its aliases), or null */
	public static Place builtIn(String name, List<Place> places) {
		String key = key(name);
		if (key.isEmpty()) {
			return null;
		}
		for (Place known : places) {
			if (key.equals(key(known.name()))) {
				return known;
			}
			for (String area : known.aliases()) {
				if (key.equals(key(area))) {
					return known;
				}
			}
		}
		return null;
	}

	/** @return the island of this name in the list, as it was added, or null */
	public String find(String name) {
		for (String known : names) {
			if (same(known, name)) {
				return known;
			}
		}
		return null;
	}

	/**
	 * Adds an island, unless it is a built-in one, already there, empty or the list is full.
	 *
	 * @return the island's name in the list (the one there before for a repeat), or null when it was not added
	 */
	public String add(String name) {
		String cleaned = clean(name);
		if (key(cleaned).isEmpty() || builtIn(cleaned, known.get()) != null) {
			return null;
		}
		String known = find(cleaned);
		if (known != null) {
			return known;
		}
		if (names.size() >= MOST) {
			return null;
		}
		names.add(cleaned);
		return cleaned;
	}

	/** Puts an island back at a place in the list, for Undo. */
	public void insert(int index, String name) {
		if (find(name) == null && names.size() < MOST) {
			names.add(Math.max(0, Math.min(names.size(), index)), name);
		}
	}

	/** Removes an island from the list. @return its place in the list, or -1 */
	public int remove(String name) {
		for (int i = 0; i < names.size(); i++) {
			if (same(names.get(i), name)) {
				names.remove(i);
				return i;
			}
		}
		return -1;
	}

	/** @return the list as saved */
	public String save() {
		return String.join(SEPARATOR, names);
	}

	/** Reads the list as saved, cleaning every name and leaving out built-in islands, repeats and anything beyond the most. */
	public void load(String saved) {
		names.clear();
		if (saved == null || saved.isEmpty()) {
			return;
		}
		for (String name : saved.split(SEPARATOR, MOST * 2)) {
			add(name);
		}
	}
}
