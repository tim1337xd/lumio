package dev.tim1337.lumio.api.experimental.debug;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The probes of Lumio and its add-ons, and the sinks that listen to them. With no sink, {@link #ACTIVE} is false and a probe costs one read of
 * it; so every probe that builds text is written guarded:
 *
 * <pre>{@code if (DebugHooks.ACTIVE) {
 *     DebugHooks.log(DebugCategory.CHAT, "Read {} from {}", value, line);
 * }}</pre>
 *
 * Sinks are the Debug log switch (it writes {@code logs/lumio-debug.log}) and a debug tool. The few switches a debug tool may turn are here too:
 * the update check's test server, read-only views of internal state. Everything here is safe to call from any thread.
 */
public final class DebugHooks {
	/**
	 * Whether a sink listens. Read it before a probe builds any text, makes a lambda or sends a GPU query. Written only here, when a sink is
	 * added or removed.
	 */
	public static volatile boolean ACTIVE;

	private static final Object LOCK = new Object();
	/** The sinks, copied on every change so probes read them without a lock. */
	private static volatile DebugSink[] sinks = new DebugSink[0];
	private static final List<DebugSink> ADDED = new ArrayList<>();
	/** Who wants the log file written (the Debug log switch, a debug tool); while anyone does, the file's sink listens too. */
	private static final Set<String> FILE_WANTED_BY = new LinkedHashSet<>();
	private static DebugSink fileSink;
	private static final Map<String, Supplier<List<String>>> VIEWS = Collections.synchronizedMap(new LinkedHashMap<>());
	private static volatile String updateTestServer;
	private static volatile String updateTestKey;

	private DebugHooks() {
	}

	/** Starts sending the probes to a sink. */
	public static void addSink(DebugSink sink) {
		synchronized (LOCK) {
			if (!ADDED.contains(sink)) {
				ADDED.add(sink);
				publish();
			}
		}
	}

	/** Stops sending the probes to a sink. */
	public static void removeSink(DebugSink sink) {
		synchronized (LOCK) {
			if (ADDED.remove(sink)) {
				publish();
			}
		}
	}

	/** Lumio only: the sink that writes {@code logs/lumio-debug.log}, listening while anyone wants the file ({@link #wantLogFile}). */
	public static void installLogFile(DebugSink sink) {
		synchronized (LOCK) {
			fileSink = sink;
			publish();
		}
	}

	/** Asks for the log file to be written (owner: who asks, such as "switch" or a debug tool's id), or stops asking. */
	public static void wantLogFile(String owner, boolean wanted) {
		synchronized (LOCK) {
			if (wanted ? FILE_WANTED_BY.add(owner) : FILE_WANTED_BY.remove(owner)) {
				publish();
			}
		}
	}

	/** @return whether the log file is being written */
	public static boolean logFileWritten() {
		synchronized (LOCK) {
			return fileSink != null && !FILE_WANTED_BY.isEmpty();
		}
	}

	private static void publish() {
		List<DebugSink> now = new ArrayList<>(ADDED);
		if (fileSink != null && !FILE_WANTED_BY.isEmpty() && !now.contains(fileSink)) {
			now.add(fileSink);
		}
		sinks = now.toArray(new DebugSink[0]);
		ACTIVE = !now.isEmpty();
	}

	/** A log line of a category at the DEBUG level. Guard the call with {@link #ACTIVE}. */
	public static void log(String category, String text) {
		emit(category, DebugLevel.DEBUG, text, null);
	}

	/**
	 * A log line with {@code {}} places filled from the arguments, as the game's log does; a last argument that is an exception is passed on as
	 * the line's exception. Guard the call with {@link #ACTIVE}: the arguments are an array made for the call.
	 */
	public static void log(String category, String format, Object... arguments) {
		log(category, DebugLevel.DEBUG, format, arguments);
	}

	/** As {@link #log(String, String, Object...)}, at a level. */
	public static void log(String category, DebugLevel level, String format, Object... arguments) {
		if (!ACTIVE) {
			return;
		}
		FormattingTuple made = MessageFormatter.arrayFormat(format, arguments);
		emit(category, level, made.getMessage(), made.getThrowable());
	}

	/** A log line as it is, with an exception or null. */
	public static void emit(String category, DebugLevel level, String text, Throwable error) {
		for (DebugSink sink : sinks) {
			try {
				sink.log(category, level, text, error);
			} catch (RuntimeException e) {
				removeSink(sink);
			}
		}
	}

	/**
	 * @return whether a sink wants the costly probes of a category ({@link DebugSink#wants}); false at once while nothing listens. Guard the
	 *         per-frame probes of the swing, the Colored theme and the like with it.
	 */
	public static boolean wants(String category) {
		if (!ACTIVE) {
			return false;
		}
		for (DebugSink sink : sinks) {
			if (sink.wants(category)) {
				return true;
			}
		}
		return false;
	}

	/** A moment of a category, such as a screen opening. Guard the call with {@link #ACTIVE} when the detail is built for it. */
	public static void event(String category, String what, String detail) {
		for (DebugSink sink : sinks) {
			try {
				sink.event(category, what, detail);
			} catch (RuntimeException e) {
				removeSink(sink);
			}
		}
	}

	/** A pass of rendering ({@link DebugPass}) begins on the render thread. Free while nothing listens: no guard needed. */
	public static void passBegin(int pass) {
		if (!ACTIVE) {
			return;
		}
		for (DebugSink sink : sinks) {
			try {
				sink.passBegin(pass);
			} catch (RuntimeException e) {
				removeSink(sink);
			}
		}
	}

	/** The pass ends. Free while nothing listens. */
	public static void passEnd(int pass) {
		if (!ACTIVE) {
			return;
		}
		for (DebugSink sink : sinks) {
			try {
				sink.passEnd(pass);
			} catch (RuntimeException e) {
				removeSink(sink);
			}
		}
	}

	/** A frame begins on the render thread. Free while nothing listens. */
	public static void frameBegin() {
		if (!ACTIVE) {
			return;
		}
		for (DebugSink sink : sinks) {
			try {
				sink.frameBegin();
			} catch (RuntimeException e) {
				removeSink(sink);
			}
		}
	}

	/** A step of a timed animation, its text a fixed phase name or null. Free while nothing listens, unless the text is built for it. */
	public static void frameMark(String animation, FrameMark mark, String text) {
		if (!ACTIVE) {
			return;
		}
		for (DebugSink sink : sinks) {
			try {
				sink.frameMark(animation, mark, text);
			} catch (RuntimeException e) {
				removeSink(sink);
			}
		}
	}

	/**
	 * Offers a read-only view of internal state under a name ("skyblock.pets"), as lines; registered once at start, read only by a debug tool.
	 * The supplier must not change anything and must be safe on the render thread.
	 */
	public static void view(String name, Supplier<List<String>> lines) {
		VIEWS.put(name, lines);
	}

	/** @return the registered views by name, in the order they came */
	public static Map<String, Supplier<List<String>>> views() {
		synchronized (VIEWS) {
			return Collections.unmodifiableMap(new LinkedHashMap<>(VIEWS));
		}
	}

	/**
	 * Points the update check at a local test server (such as {@code http://127.0.0.1:8123}) or back at GitHub (null). Lumio honors it only in a
	 * jar that is no public release.
	 */
	public static void setUpdateTestServer(String address) {
		updateTestServer = address;
	}

	/** @return the update check's test server, or null */
	public static String updateTestServer() {
		return updateTestServer;
	}

	/**
	 * The public key (Ed25519, X.509, base64) the test server signs its files with, or null. Lumio takes it only in a jar that is no public release
	 * and only while a test server is set; a public release checks every download against its own release key alone.
	 */
	public static void setUpdateTestKey(String publicKey) {
		updateTestKey = publicKey;
	}

	/** @return the test server's signing key, or null */
	public static String updateTestKey() {
		return updateTestKey;
	}
}
