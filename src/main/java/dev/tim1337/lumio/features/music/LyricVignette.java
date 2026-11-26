package dev.tim1337.lumio.features.music;

/**
 * Whether lyrics stand in the world outside the cinematic camera. While they do, the game's vignette is drawn around them, so they stay fully lit
 * (see {@link dev.tim1337.lumio.render.LyricOverlay}); it keeps its strength everywhere else. In the cinematic camera the game's vignette is off.
 */
public final class LyricVignette {
	private static boolean shown;

	private LyricVignette() {
	}

	/** Called each frame the lyrics are looked after: whether any line can be seen now, and whether the cinematic camera films them. */
	static void update(boolean visible, boolean filmed) {
		shown = visible && !filmed;
	}

	/** No lyrics in the world at all. */
	static void off() {
		shown = false;
	}

	/** @return whether the vignette has to leave lyrics out */
	public static boolean lyricsShown() {
		return shown;
	}
}
