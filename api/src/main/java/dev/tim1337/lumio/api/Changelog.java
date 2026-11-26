package dev.tim1337.lumio.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The changelog of a mod, read from one plain text file in its jar: {@code assets/<mod id>/changelog.txt} ({@link #path}). Lumio shows the
 * changelogs of itself and of every add-on that ships one in its "What is new" window, one tab per mod; an add-on needs no code for it, only the
 * file. The same file is the source of a release's notes, so they are written once.
 *
 * <p>The format, line by line (blank lines and lines starting with {@code #} but not {@code ##} are left out):
 * <pre>
 * ## In development                      the changes since the last release (shown only in development builds)
 * ## 1.0.0-beta.2 | 2026-11-24           a released version and its date (YYYY-MM-DD), newest first
 * &gt; Second beta: dungeons and waypoints.  a short line for the release notes only, not shown in the game
 * + Waypoints: mark places @waypoints     Added; after " @" the id of a feature that "Open ›" goes to (optional)
 * * Menu with a top bar                   Changed
 * ! Loading screen stuck with quick play  Fixed
 * </pre>
 * An entry is one line of text, plain words, no formatting codes. Plain Java, no game classes, so an add-on can check its file in a unit test
 * with {@link #parse}.
 */
public final class Changelog {
	/** The section of the changes since the last release. */
	public static final String IN_DEVELOPMENT = "In development";
	/** Before an entry's link target. */
	public static final String LINK_MARK = " @";
	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
	private static final Pattern TARGET = Pattern.compile("[a-z0-9_.:-]+");
	private static final int MOST_ENTRY_CHARS = 200;

	private Changelog() {
	}

	/** The groups of a release, in the order they are shown. */
	public enum Kind {
		ADDED('+'), CHANGED('*'), FIXED('!');

		private final char symbol;

		Kind(char symbol) {
			this.symbol = symbol;
		}

		/** @return the character a line of this group starts with */
		public char symbol() {
			return symbol;
		}

		static Kind of(char symbol) {
			for (Kind kind : values()) {
				if (kind.symbol == symbol) {
					return kind;
				}
			}
			return null;
		}
	}

	/**
	 * One line of a release.
	 *
	 * @param kind its group
	 * @param text what changed, in plain words
	 * @param link the id of the feature "Open ›" goes to, or null
	 */
	public record Entry(Kind kind, String text, String link) {
	}

	/**
	 * One section of the file.
	 *
	 * @param version  the version ("1.0.0-beta.2"), or {@link #IN_DEVELOPMENT}
	 * @param date     its date (YYYY-MM-DD), or empty in development
	 * @param summary  the release notes' short lines, not shown in the game
	 * @param entries  its lines, in the order written
	 */
	public record Release(String version, String date, List<String> summary, List<Entry> entries) {
		/** @return whether this is the section of the changes since the last release */
		public boolean inDevelopment() {
			return IN_DEVELOPMENT.equalsIgnoreCase(version);
		}

		/** @return the entries of one group, in the order written */
		public List<Entry> of(Kind kind) {
			return entries.stream().filter(entry -> entry.kind() == kind).toList();
		}
	}

	/** @return where a mod's changelog lies in its jar */
	public static String path(String modId) {
		return "assets/" + modId + "/changelog.txt";
	}

	/**
	 * @return the sections of a changelog, in the order of the file
	 * @throws IllegalArgumentException for a file that is not a changelog: a released section without a date (YYYY-MM-DD), an entry before the
	 *                                  first section, an empty or too long entry, or a link that is no id; Lumio then leaves that mod's tab out
	 */
	public static List<Release> parse(String text) {
		List<Release> releases = new ArrayList<>();
		String version = null;
		String date = "";
		List<String> summary = new ArrayList<>();
		List<Entry> entries = new ArrayList<>();
		for (String raw : text.split("\n", -1)) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#") && !line.startsWith("##")) {
				continue;
			}
			if (line.startsWith("##")) {
				if (version != null) {
					releases.add(new Release(version, date, List.copyOf(summary), List.copyOf(entries)));
				}
				String[] parts = line.substring(2).split("\\|", 2);
				version = parts[0].strip();
				date = parts.length > 1 ? parts[1].strip() : "";
				if (version.isEmpty()) {
					throw new IllegalArgumentException("A section without a version: " + line);
				}
				if (!IN_DEVELOPMENT.equalsIgnoreCase(version) && !DATE.matcher(date).matches()) {
					throw new IllegalArgumentException("A released version without its date (YYYY-MM-DD): " + line);
				}
				summary = new ArrayList<>();
				entries = new ArrayList<>();
				continue;
			}
			if (version == null) {
				throw new IllegalArgumentException("A line before the first section: " + line);
			}
			if (line.startsWith(">")) {
				summary.add(line.substring(1).strip());
				continue;
			}
			Kind kind = Kind.of(line.charAt(0));
			if (kind == null) {
				throw new IllegalArgumentException("Not an entry (+, * or !): " + line);
			}
			entries.add(entry(kind, line.substring(1).strip(), line));
		}
		if (version != null) {
			releases.add(new Release(version, date, List.copyOf(summary), List.copyOf(entries)));
		}
		return releases;
	}

	private static Entry entry(Kind kind, String rest, String line) {
		String text = rest;
		String link = null;
		int at = rest.lastIndexOf(LINK_MARK);
		if (at >= 0) {
			Matcher target = TARGET.matcher(rest.substring(at + LINK_MARK.length()).strip());
			if (target.matches()) {
				link = target.group();
				text = rest.substring(0, at).strip();
			}
		}
		if (text.isEmpty() || text.length() > MOST_ENTRY_CHARS) {
			throw new IllegalArgumentException("An entry empty or longer than " + MOST_ENTRY_CHARS + " characters: " + line);
		}
		return new Entry(kind, text, link);
	}
}
