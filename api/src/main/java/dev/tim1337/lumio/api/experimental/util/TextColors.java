package dev.tim1337.lumio.api.experimental.util;

/** Text helpers for the game's legacy color codes ("§a"), which servers still send in names, lines and messages. */
public final class TextColors {
	private TextColors() {
	}

	/**
	 * @return the text without color codes ("§a"); implemented as a loop since it runs for many lines every tick and a regex was recompiled each
	 *         time
	 */
	public static String noColors(String text) {
		int at = text.indexOf('§');
		if (at < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		out.append(text, 0, at);
		for (int i = at; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§') {
				i++;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
}
