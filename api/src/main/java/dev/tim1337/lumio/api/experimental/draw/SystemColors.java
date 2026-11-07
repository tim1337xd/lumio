package dev.tim1337.lumio.api.experimental.draw;

/**
 * The macOS system colors the mod uses, in one place: accent choices, feature icon colors and status colors (success, error, warning). Every
 * color is available as ARGB for drawing and as RGB (the _RGB constants) for settings and icons, which store colors without alpha.
 */
public final class SystemColors {
	private SystemColors() {
	}

	public static final int BLUE_RGB = 0x0A84FF;
	public static final int GREEN_RGB = 0x30D158;
	public static final int RED_RGB = 0xFF453A;
	public static final int ORANGE_RGB = 0xFF9F0A;
	public static final int YELLOW_RGB = 0xFFD60A;
	public static final int PURPLE_RGB = 0xBF5AF2;
	public static final int PINK_RGB = 0xFF375F;
	public static final int INDIGO_RGB = 0x5E5CE6;
	public static final int TEAL_RGB = 0x64D2FF;
	public static final int GRAY_RGB = 0x8E8E93;
	/** A lighter red than {@link #RED_RGB} for text: the fill red is hard to read as text on a dark panel. */
	public static final int RED_TEXT_RGB = 0xFF6B5E;

	public static final int BLUE = 0xFF000000 | BLUE_RGB;
	public static final int GREEN = 0xFF000000 | GREEN_RGB;
	public static final int RED = 0xFF000000 | RED_RGB;
	public static final int ORANGE = 0xFF000000 | ORANGE_RGB;
	public static final int YELLOW = 0xFF000000 | YELLOW_RGB;
	public static final int PURPLE = 0xFF000000 | PURPLE_RGB;
	public static final int PINK = 0xFF000000 | PINK_RGB;
	public static final int INDIGO = 0xFF000000 | INDIGO_RGB;
	public static final int TEAL = 0xFF000000 | TEAL_RGB;
	public static final int GRAY = 0xFF000000 | GRAY_RGB;
	public static final int RED_TEXT = 0xFF000000 | RED_TEXT_RGB;

	/** Success or completion. */
	public static final int SUCCESS = GREEN;
	/** Errors and irreversible actions, such as a delete button. */
	public static final int DANGER = RED;
	/** Same, for text. */
	public static final int DANGER_TEXT = RED_TEXT;
	/** Something that needs attention soon. */
	public static final int WARNING = ORANGE;
}
