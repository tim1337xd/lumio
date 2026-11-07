package dev.tim1337.lumio.ui.title;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Filtering and sorting of the main menu's lists: a typed text matches the name or the address of a server, or the name of a world, without
 * caring about case. Plain logic without the game, so it can be tested on its own.
 */
public final class ListFilter {
	/** A server address: a host name or an IP address, optionally with a port, and no spaces. */
	private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9.\\-\\[\\]:]+(:\\d{1,5})?");

	private ListFilter() {
	}

	/**
	 * @param names the shown name of every entry
	 * @param others a second text of every entry that also matches (a server's address), or null
	 * @return the positions of the entries that match a query, in their order; all of them for an empty query
	 */
	public static List<Integer> matching(List<String> names, List<String> others, String query) {
		String wanted = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		List<Integer> result = new ArrayList<>(names.size());
		for (int i = 0; i < names.size(); i++) {
			if (wanted.isEmpty() || contains(names.get(i), wanted) || (others != null && contains(others.get(i), wanted))) {
				result.add(i);
			}
		}
		return result;
	}

	private static boolean contains(String text, String wanted) {
		return text != null && text.toLowerCase(Locale.ROOT).contains(wanted);
	}

	/** @return whether a typed text can be joined as a server address: it has a dot or a port, or is localhost, and no spaces */
	public static boolean looksLikeAddress(String text) {
		String typed = text == null ? "" : text.strip();
		if (typed.isEmpty() || !ADDRESS.matcher(typed).matches()) {
			return false;
		}
		return typed.contains(".") || typed.contains(":") || typed.equalsIgnoreCase("localhost");
	}

	/** @return the positions of entries sorted by when they were last played, the latest first; equal times keep their order */
	public static List<Integer> byLastPlayed(List<Long> lastPlayed) {
		List<Integer> order = new ArrayList<>(lastPlayed.size());
		for (int i = 0; i < lastPlayed.size(); i++) {
			order.add(i);
		}
		order.sort((a, b) -> Long.compare(lastPlayed.get(b), lastPlayed.get(a)));
		return order;
	}

	/** @return how long ago a time was, in words: "just now", "5 minutes ago", "2 hours ago", "yesterday", "5 days ago", "3 weeks ago" */
	public static String ago(long thenMillis, long nowMillis) {
		long minutes = Math.max(0L, (nowMillis - thenMillis) / 60_000L);
		if (minutes < 1L) {
			return "just now";
		}
		if (minutes < 60L) {
			return minutes == 1L ? "a minute ago" : minutes + " minutes ago";
		}
		long hours = minutes / 60L;
		if (hours < 24L) {
			return hours == 1L ? "an hour ago" : hours + " hours ago";
		}
		long days = hours / 24L;
		if (days < 2L) {
			return "yesterday";
		}
		if (days < 14L) {
			return days + " days ago";
		}
		if (days < 60L) {
			return days / 7L + " weeks ago";
		}
		return days < 730L ? days / 30L + " months ago" : days / 365L + " years ago";
	}
}
