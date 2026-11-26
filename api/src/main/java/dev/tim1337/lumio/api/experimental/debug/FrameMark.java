package dev.tim1337.lumio.api.experimental.debug;

/** The steps a timed animation reports, so a debug tool can record its frame times. */
public enum FrameMark {
	/** One frame of the animation, its text the phase it is in (or null). */
	FRAME,
	/** Work done in the current frame, so a slow frame says what it did. */
	NOTE,
	/** The moment the listed frame times count from (the end of loading). */
	ZERO,
	/** The animation ended. */
	FINISH
}
