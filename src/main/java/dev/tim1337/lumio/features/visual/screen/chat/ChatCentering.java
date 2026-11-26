package dev.tim1337.lumio.features.visual.screen.chat;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.List;

/**
 * A server's centered lines. Servers center a line by padding it with spaces at its left, counted for the game's own chat width; in a chat of
 * another width that padding puts the text off the middle and makes a short line as wide as the widest. Such a line is told by where its text's
 * middle lies, its padding is dropped, and its rows are centered in the width the chat has ({@link #left}). Drawing and clicks both go by that
 * one place.
 */
public final class ChatCentering {
	/** The middle a server pads its lines for: its text's middle lies here, give or take the width of the spaces it pads with, in the game's chat pixels. */
	static final float GAME_MIDDLE = 156f;
	static final float MIDDLE_ROOM = 10f;
	/** Fewer spaces than this before a line are no centering. */
	static final int LEAST_SPACES = 3;

	private ChatCentering() {
	}

	/**
	 * @param padWidth  the width of the spaces before the text
	 * @param textWidth the width of the text without the spaces around it
	 * @return whether a line padded so was centered for the game's chat
	 */
	static boolean padded(float padWidth, float textWidth) {
		return textWidth > 0f && Math.abs(padWidth + textWidth / 2f - GAME_MIDDLE) <= MIDDLE_ROOM;
	}

	/**
	 * @param room  the width rows may take
	 * @param width a centered row's width
	 * @return how far the row starts from the left of the room; a row wider than the room starts at its left
	 */
	public static int left(int room, int width) {
		return Math.max(0, (room - width) / 2);
	}

	/** @return a centered message's text without its padding, or null for a message that is not centered */
	static FormattedText unpadded(FormattedText message, StringSplitter splitter) {
		List<Style> styles = new ArrayList<>();
		StringBuilder letters = new StringBuilder();
		StringDecomposer.iterateFormatted(message, Style.EMPTY, (position, style, codePoint) -> {
			int before = letters.length();
			letters.appendCodePoint(codePoint);
			for (int i = before; i < letters.length(); i++) {
				styles.add(style);
			}
			return true;
		});
		int from = 0;
		while (from < letters.length() && letters.charAt(from) == ' ') {
			from++;
		}
		int to = letters.length();
		while (to > from && letters.charAt(to - 1) == ' ') {
			to--;
		}
		if (from < LEAST_SPACES || from == to || letters.indexOf("\n") >= 0) {
			return null;
		}
		FormattedText text = part(letters, styles, from, to);
		return padded(splitter.stringWidth(part(letters, styles, 0, from)), splitter.stringWidth(text)) ? text : null;
	}

	/** @return the letters from one place to another, each run of one style a part of its own */
	private static FormattedText part(StringBuilder letters, List<Style> styles, int from, int to) {
		List<FormattedText> parts = new ArrayList<>();
		int start = from;
		for (int i = from + 1; i <= to; i++) {
			if (i == to || !styles.get(i).equals(styles.get(start))) {
				parts.add(FormattedText.of(letters.substring(start, i), styles.get(start)));
				start = i;
			}
		}
		return FormattedText.composite(parts);
	}
}
