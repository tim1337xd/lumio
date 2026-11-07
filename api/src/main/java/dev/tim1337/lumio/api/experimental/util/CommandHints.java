package dev.tim1337.lumio.api.experimental.util;

import com.mojang.brigadier.Message;

import java.util.function.Function;

/**
 * Quiet text in the game's command suggestions: beside an entry of its list (give that entry a {@link Quiet} as its tooltip), and in the chat field after the cursor while there is nothing to
 * suggest, such as "&lt;player&gt;" after "/party kick ". Only shown, never sent.
 */
public final class CommandHints {
	/** The quiet text beside a suggestion. */
	public record Quiet(String text) implements Message {
		@Override
		public String getString() {
			return text;
		}
	}

	/** The room between an entry and its quiet text. */
	public static final int GAP = 6;

	private static volatile Function<String, String> usage = input -> null;

	private CommandHints() {
	}

	/** @param source gives what the input before the cursor takes next, or null when it does not know */
	public static void setUsage(Function<String, String> source) {
		usage = source;
	}

	/** @return what the input before the cursor takes next, or null */
	public static String usage(String beforeCursor) {
		return usage.apply(beforeCursor);
	}
}
