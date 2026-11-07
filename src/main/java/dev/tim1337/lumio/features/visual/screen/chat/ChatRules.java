package dev.tim1337.lumio.features.visual.screen.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the chat reads out of a message's plain text, to add things around it: who wrote it, whether it mentions the player, the time, repeats, the filter of an own tab and links. The message itself
 * is never changed. No game in here, so it is unit tested.
 */
public final class ChatRules {
	/** "[MVP+] Steve123: hi", "Party > [VIP] Bob: hi", "Guild > [MVP] Eve [Officer]: hi", "From [MVP+] Cara: hi". A line of the game has no name and a colon at this place, or none the player list knows. */
	private static final Pattern SENDER = Pattern.compile("^(?:(?:Party|P|Guild|G|Officer|O)\\s*[>»]\\s*|(?:From|To)\\s+)?(?:\\[[^\\]]{1,16}]\\s*)*([A-Za-z0-9_]{1,16})(?:\\s*\\[[^\\]]{1,16}])*\\s*:\\s");
	private static final Pattern URL = Pattern.compile("(?:https?://|www\\.)[^\\s]+|\\b[a-z0-9-]+\\.(?:net|com|org|gg|io)/[^\\s]*", Pattern.CASE_INSENSITIVE);
	private static final int NOON = 12;

	private ChatRules() {
	}

	/** @return the name of who wrote a line, or null for a line of the game */
	public static String sender(String plain) {
		Matcher matcher = SENDER.matcher(plain);
		return matcher.find() ? matcher.group(1) : null;
	}

	/** @return whether a text holds one of the words as a word of its own, in any case: "Bob" is in "bob, hi" and not in "Bobby" */
	public static boolean mentions(String text, List<String> words) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String word : words) {
			String wanted = word.strip().toLowerCase(Locale.ROOT);
			if (!wanted.isEmpty() && holdsWord(lower, wanted)) {
				return true;
			}
		}
		return false;
	}

	private static boolean holdsWord(String text, String word) {
		int at = text.indexOf(word);
		while (at >= 0) {
			int end = at + word.length();
			boolean startsClean = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1)) && text.charAt(at - 1) != '_';
			boolean endsClean = end == text.length() || !Character.isLetterOrDigit(text.charAt(end)) && text.charAt(end) != '_';
			if (startsClean && endsClean) {
				return true;
			}
			at = text.indexOf(word, at + 1);
		}
		return false;
	}

	/** @return a time as "14:30" or, on a 12 hour clock, "2:30 PM"; with seconds if asked */
	public static String time(int hour, int minute, int second, boolean twelveHours, boolean seconds) {
		StringBuilder out = new StringBuilder(11);
		if (twelveHours) {
			int shown = hour % NOON;
			out.append(shown == 0 ? NOON : shown);
		} else {
			two(out, hour);
		}
		out.append(':');
		two(out, minute);
		if (seconds) {
			out.append(':');
			two(out, second);
		}
		if (twelveHours) {
			out.append(hour < NOON ? " AM" : " PM");
		}
		return out.toString();
	}

	private static void two(StringBuilder out, int value) {
		if (value < 10) {
			out.append('0');
		}
		out.append(value);
	}

	/** What an own tab looks for. */
	public enum FilterMode {
		CONTAINS,
		STARTS_WITH,
		FROM_PLAYER
	}

	/** @return whether a message belongs in an own tab; an empty filter finds nothing */
	public static boolean matchesFilter(String plain, FilterMode mode, String filter) {
		String wanted = filter.strip().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return false;
		}
		return switch (mode) {
			case CONTAINS -> plain.toLowerCase(Locale.ROOT).contains(wanted);
			case STARTS_WITH -> plain.stripLeading().toLowerCase(Locale.ROOT).startsWith(wanted);
			case FROM_PLAYER -> wanted.equalsIgnoreCase(sender(plain));
		};
	}

	/** @return where the first link of a text is, as its start and end, or null */
	public static int[] link(String text) {
		Matcher matcher = URL.matcher(text);
		return matcher.find() ? new int[]{matcher.start(), matcher.end()} : null;
	}

	/** @return a link as it can be opened: with its scheme; only web links */
	public static String openable(String link) {
		String lower = link.toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://") ? link : "https://" + link;
	}

	/** @return what stands after a repeated message: " (x2)" */
	public static String countText(int count) {
		return " (x" + count + ")";
	}

	/** Counts the same message within a span of time; it remembers a bounded number of them. */
	public static final class Repeats<T> {
		private record Seen<T>(int count, long at, T shown) {
		}

		private final long spanMillis;
		private final int most;
		private final Map<String, Seen<T>> seen = new HashMap<>();

		public Repeats(long spanMillis, int most) {
			this.spanMillis = spanMillis;
			this.most = most;
		}

		/** @return how many times this is the same message, this time included */
		public int count(String key, long now) {
			Seen<T> before = seen.get(key);
			return before != null && now - before.at() < spanMillis ? before.count() + 1 : 1;
		}

		/** @return what was shown for it the last time and is to be replaced, or null */
		public T replaced(String key, long now) {
			Seen<T> before = seen.get(key);
			return before != null && now - before.at() < spanMillis ? before.shown() : null;
		}

		/** Remembers a message, as what is shown for it now. */
		public void note(String key, int count, long now, T shown) {
			if (seen.size() > most) {
				seen.values().removeIf(entry -> now - entry.at() >= spanMillis);
				if (seen.size() > most) {
					seen.clear();
				}
			}
			seen.put(key, new Seen<>(count, now, shown));
		}
	}
}
