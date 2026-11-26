package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.ui.Ui;

/** Text of the main menu that lies right on the scene: its soft shadow, in the text's own font. */
public final class MenuText {
	/** The weight of the text a shadow is for. */
	public static final int REGULAR_FONT = 0;
	public static final int BOLD_FONT = 1;
	public static final int TITLE_FONT = 2;
	private static final int STEPS = 8;
	private static final float STEP_OPACITY = 0.11f;

	private MenuText() {
	}

	/**
	 * A soft shadow under text, in the text's own font so it lies exactly under it: the text drawn faintly a few times in a small circle. The
	 * blurred text of the toolkit is in the regular font, which peeked out beside bold text.
	 */
	public static void softShadow(Ui ui, String text, float x, float y, float textScale, int color, float radius, int font) {
		int faint = Ui.withOpacity(color, ((color >>> 24) / 255f) * STEP_OPACITY);
		for (int i = 0; i < STEPS; i++) {
			double angle = 2.0 * Math.PI / STEPS * i;
			float sx = x + (float) Math.cos(angle) * radius;
			float sy = y + (float) Math.sin(angle) * radius;
			switch (font) {
				case BOLD_FONT -> ui.textAtScaledBold(text, sx, sy, textScale, faint);
				case TITLE_FONT -> ui.textAtScaledTitle(text, sx, sy, textScale, faint);
				default -> ui.textAtScaled(text, sx, sy, textScale, faint);
			}
		}
	}
}
