package dev.tim1337.lumio.features.visual.hud;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tells a player from the text columns a server writes into the player list: "[502] Steve123", or a bare name under the heading "Players (21)". Plain text, so it is unit tested. */
final class TabPlayerRows {
	private static final Pattern WITH_LEVEL = Pattern.compile("^\\[\\d{1,4}\\]\\s+(?:\\S{1,2}\\s+)?([A-Za-z0-9_]{1,16})(?:\\s.*)?$");
	private static final Pattern BARE_NAME = Pattern.compile("^([A-Za-z0-9_]{3,16})(?:\\s.{0,4})?$");
	private static final Pattern PLAYERS_HEADING = Pattern.compile("^Players\\s*\\(\\d{1,4}\\)$", Pattern.CASE_INSENSITIVE);
	/** The headings of the other columns, after which bare names are no players any more. */
	private static final Pattern OTHER_HEADING = Pattern.compile("^(?:Info|Account Info|Server Info|Player Info)$", Pattern.CASE_INSENSITIVE);

	private TabPlayerRows() {
	}

	/** @return whether a line is the heading of the players' column */
	static boolean isPlayersHeading(String plain) {
		return PLAYERS_HEADING.matcher(plain).matches();
	}

	/** @return whether a line starts a column that holds no players */
	static boolean isOtherHeading(String plain) {
		return OTHER_HEADING.matcher(plain).matches();
	}

	/**
	 * @param plain        a line of the list, without its colors
	 * @param inPlayers    whether it is under the heading of the players' column
	 * @return the name of the player it stands for, or null when it is no player
	 */
	static String nameIn(String plain, boolean inPlayers) {
		Matcher level = WITH_LEVEL.matcher(plain);
		if (level.matches()) {
			return level.group(1);
		}
		if (!inPlayers) {
			return null;
		}
		Matcher bare = BARE_NAME.matcher(plain);
		return bare.matches() ? bare.group(1) : null;
	}

	/** @return a name in the form players are compared by */
	static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
