package dev.tim1337.lumio.api.experimental.debug;

/**
 * What listens to the probes: the Debug log switch's file in Lumio, or a debug tool. Called on any thread (the render thread, a download's thread),
 * so an implementation is thread-safe and quick, and never throws (an exception is caught and the sink dropped).
 */
public interface DebugSink {
	/** A log line of a category ({@link DebugCategory} or an add-on's own), with an exception or null. */
	void log(String category, DebugLevel level, String text, Throwable error);

	/**
	 * @return whether the sink wants the probes of a category that cost something to make each frame (the swing's values, the Colored theme's
	 *         colors): those only run while a sink asks for them, not for every sink (the Debug log's file does not)
	 */
	default boolean wants(String category) {
		return false;
	}

	/** A moment of a category, such as a screen opening: what happened, and a short detail (may be empty). */
	default void event(String category, String what, String detail) {
	}

	/** A pass of rendering ({@link DebugPass}) begins on the render thread; nested calls of the same pass come once. */
	default void passBegin(int pass) {
	}

	/** The pass ends. */
	default void passEnd(int pass) {
	}

	/** A frame begins on the render thread, before its passes. */
	default void frameBegin() {
	}

	/** A step of a timed animation (the startup transition, a loading screen): see {@link FrameMark}. */
	default void frameMark(String animation, FrameMark mark, String text) {
	}
}
