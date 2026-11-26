package dev.tim1337.lumio.util;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.debug.FrameMark;

/**
 * The frames of one animation (the startup transition, a loading screen) as probes: a debug tool that listens records their times and lists the
 * slow ones with what happened in them. While nothing listens, each call is one read of {@link DebugHooks#ACTIVE}.
 */
public final class FrameTimes {
	private final String name;

	public FrameTimes(String name) {
		this.name = name;
	}

	/** @return whether a debug tool listens, so work worth noting may be described */
	public static boolean enabled() {
		return DebugHooks.ACTIVE;
	}

	/** One frame of the animation. */
	public void frame() {
		DebugHooks.frameMark(name, FrameMark.FRAME, null);
	}

	/** One frame of the animation in a phase (a fixed text). */
	public void frame(String phase) {
		DebugHooks.frameMark(name, FrameMark.FRAME, phase);
	}

	/** Work done in the current frame, so a slow frame says what it did; built by the caller only while {@link #enabled()}. */
	public void note(String work) {
		DebugHooks.frameMark(name, FrameMark.NOTE, work);
	}

	/** The moment the listed frame times count from (the end of loading). */
	public void markZero() {
		DebugHooks.frameMark(name, FrameMark.ZERO, null);
	}

	/** The animation ended. */
	public void finish() {
		DebugHooks.frameMark(name, FrameMark.FINISH, null);
	}
}
