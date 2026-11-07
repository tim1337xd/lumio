package dev.tim1337.lumio.api.experimental.util;

/**
 * Checks values from outside the player's typing (the item repository, saved files) before they become part of a command the mod sends for a
 * click: only one plain word, so such a value can neither add arguments nor carry characters the server rejects (it disconnects for them).
 */
public final class CommandText {
	/** The longest argument accepted; SkyBlock item ids and page numbers are far shorter. */
	public static final int LONGEST_ARGUMENT = 64;

	private CommandText() {
	}

	/** @return whether the text is one word of letters, digits and {@code _ - : ; .}, between 1 and {@link #LONGEST_ARGUMENT} characters */
	public static boolean safeArgument(String text) {
		if (text == null || text.isEmpty() || text.length() > LONGEST_ARGUMENT) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			boolean letterOrDigit = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
			if (!letterOrDigit && c != '_' && c != '-' && c != ':' && c != ';' && c != '.') {
				return false;
			}
		}
		return true;
	}
}
