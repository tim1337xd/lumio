package dev.tim1337.lumio.features.music;

/** The presets of the lyrics in the world: how a line is shown. */
enum WorldLyricPreset {
	/** The look of before: the line in its font with the chosen glow. */
	CLASSIC("Classic"),
	/** The words one at a time, stacked like paper cut-outs. */
	CUTOUT("Cut-out");

	private final String label;

	WorldLyricPreset(String label) {
		this.label = label;
	}

	String label() {
		return label;
	}
}
