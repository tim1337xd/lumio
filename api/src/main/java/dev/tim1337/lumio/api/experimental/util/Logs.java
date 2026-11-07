package dev.tim1337.lumio.api.experimental.util;

import dev.tim1337.lumio.api.LumioApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * The mod's log, for every message it writes. An exception logged with a message has the user's home folder in its message replaced with "~"
 * (also in its causes): Java names the file in its messages ("C:\Users\name\...\config.json"), and logs get shared when asking for help.
 */
public final class Logs {
	private static final Logger LOGGER = LoggerFactory.getLogger(LumioApi.MOD_ID);
	private static final String HOME = System.getProperty("user.home", "");
	/** How many causes of an exception are cleaned and kept; real chains are a few long. */
	private static final int MOST_CAUSES = 8;

	private Logs() {
	}

	public static void info(String message, Object... arguments) {
		LOGGER.info(message, cleaned(arguments));
	}

	public static void warn(String message, Object... arguments) {
		LOGGER.warn(message, cleaned(arguments));
	}

	public static void error(String message, Object... arguments) {
		LOGGER.error(message, cleaned(arguments));
	}

	/** @return the arguments, with an exception among them replaced by a copy whose messages do not name the home folder */
	private static Object[] cleaned(Object[] arguments) {
		for (int i = 0; i < arguments.length; i++) {
			if (arguments[i] instanceof Throwable error) {
				arguments[i] = withoutHome(error, HOME, MOST_CAUSES);
			}
		}
		return arguments;
	}

	/** @return a copy of the exception and its causes with the home folder replaced by "~" in their messages; the same stack traces */
	static Throwable withoutHome(Throwable error, String home, int causes) {
		Throwable cause = error.getCause() != null && error.getCause() != error && causes > 0 ? withoutHome(error.getCause(), home, causes - 1) : null;
		Throwable copy = new Cleaned(error.getClass().getName(), withoutHome(error.getMessage(), home), cause);
		copy.setStackTrace(error.getStackTrace());
		return copy;
	}

	/** @return the text with the home folder replaced by "~", written with either slash and in any case; null stays null */
	static String withoutHome(String text, String home) {
		if (text == null || home == null || home.length() < 2) {
			return text;
		}
		String result = replaceIgnoringCase(text, home);
		return replaceIgnoringCase(result, home.replace('\\', '/'));
	}

	private static String replaceIgnoringCase(String text, String home) {
		String lower = text.toLowerCase(Locale.ROOT);
		String wanted = home.toLowerCase(Locale.ROOT);
		if (lower.length() != text.length() || wanted.length() != home.length()) {
			// a few letters change their length in lower case: then only the exact spelling is replaced
			return text.replace(home, "~");
		}
		int at = lower.indexOf(wanted);
		if (at < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		int from = 0;
		while (at >= 0) {
			out.append(text, from, at).append('~');
			from = at + wanted.length();
			at = lower.indexOf(wanted, from);
		}
		return out.append(text, from, text.length()).toString();
	}

	/** An exception as logged: printed with its original class name and the cleaned message. */
	private static final class Cleaned extends Throwable {
		private final String type;

		Cleaned(String type, String message, Throwable cause) {
			super(message, cause, false, true);
			this.type = type;
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			// the original's stack trace is set instead
			return this;
		}

		@Override
		public String toString() {
			return getMessage() == null ? type : type + ": " + getMessage();
		}
	}
}
