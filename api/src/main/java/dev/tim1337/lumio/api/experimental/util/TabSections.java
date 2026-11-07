package dev.tim1337.lumio.api.experimental.util;

import java.util.regex.Pattern;

/** The sections of a server's player list that list names as information: "Guests (1)", "Coop (3)". Their lines look like players, but they belong to their section. Plain text, so it is unit tested. */
public final class TabSections {
	private static final Pattern COUNTED = Pattern.compile("^[A-Za-z][A-Za-z -]{1,24}?\\s*\\(\\d{1,4}(?:/\\d{1,4})?\\):?$");
	private static final Pattern NAMED = Pattern.compile("^(?:Guests?|Co-?op|Party|Members|Island Members?|Visitors?):?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PLAYERS = Pattern.compile("^Players\\s*\\(.*$", Pattern.CASE_INSENSITIVE);

	private TabSections() {
	}

	/** @return whether a line without colors is the heading of a section that lists names; the players' own column is not one */
	public static boolean isNameSection(String plain) {
		String line = plain.strip();
		return !PLAYERS.matcher(line).matches() && (COUNTED.matcher(line).matches() || NAMED.matcher(line).matches());
	}
}
