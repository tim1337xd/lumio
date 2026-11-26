package dev.tim1337.lumio.api.experimental.debug;

/** How much a probe's line matters, for the filters of a log viewer. */
public enum DebugLevel {
	/** Every step, many lines per second (input, frames). */
	TRACE,
	/** What a reader understood or decided. */
	DEBUG,
	/** Something worth seeing once (a fetch failed, a run ended). */
	INFO,
	/** Something unexpected the code got around. */
	WARN
}
