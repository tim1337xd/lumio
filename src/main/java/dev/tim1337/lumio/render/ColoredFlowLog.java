package dev.tim1337.lumio.render;

import dev.tim1337.lumio.api.experimental.util.Logs;

import java.util.Locale;

/**
 * Once per second, behind a switch of the Debug log: how far the Colored theme's field moved and what moved it. It adds to its counts per frame and writes only with the switch on, so without it nothing happens here.
 */
final class ColoredFlowLog {
	private static final long SECOND_NANOS = 1_000_000_000L;
	private static final double NANOS = 1e9;

	private long since;
	private float flowAtStart;
	private int frames;
	private int drawn;
	private int hudPanes;
	private int screenPanes;

	/** Counts a pane that reads the colors in this frame: of the HUD, or of a screen over it. */
	void pane(boolean hud) {
		if (hud) {
			hudPanes++;
		} else {
			screenPanes++;
		}
	}

	/** Counts a frame, and writes what the last second was. */
	void frame(boolean draws, long now, float flowSeconds, float rate, String screen, boolean followsSong, int[] songColors, int[] shown) {
		frames++;
		if (draws) {
			drawn++;
		}
		if (since == 0L) {
			since = now;
			flowAtStart = flowSeconds;
			return;
		}
		if (now - since < SECOND_NANOS) {
			return;
		}
		double seconds = (now - since) / NANOS;
		Logs.info(line(seconds, flowSeconds, flowSeconds - flowAtStart, rate, frames, drawn, hudPanes, screenPanes, screen, followsSong, songColors, shown));
		since = now;
		flowAtStart = flowSeconds;
		frames = 0;
		drawn = 0;
		hudPanes = 0;
		screenPanes = 0;
	}

	/** @return the line of the log: the field must move rate × seconds, whatever is open */
	static String line(double seconds, float flow, float moved, float rate, int frames, int drawn, int hudPanes, int screenPanes, String screen, boolean followsSong, int[] songColors, int[] shown) {
		String path = hudPanes > 0 && screenPanes > 0 ? "HUD and screen" : hudPanes > 0 ? "HUD" : screenPanes > 0 ? "screen" : "nothing";
		return String.format(Locale.ROOT, "Colored field: time %.2f, moved %.3f in %.2f s (speed %.3f, wanted %.3f), %d frames, %d drew it, read by %s (%d HUD panes, %d screen panes), screen %s, follows song %b, song %s, shown %s",
			flow, moved, seconds, seconds > 0 ? moved / seconds : 0, rate, frames, drawn, path, hudPanes, screenPanes, screen, followsSong, hex(songColors), hex(shown));
	}

	private static String hex(int[] colors) {
		if (colors == null) {
			return "none";
		}
		StringBuilder text = new StringBuilder();
		for (int color : colors) {
			text.append(String.format(Locale.ROOT, "%06X ", color & 0xFFFFFF));
		}
		return text.toString().strip();
	}
}
