package dev.tim1337.lumio.ui;

/**
 * The look the game's own controls get on its screens (Options, the pause menu, the lists), where the mod restyles them: boxes with the menu's
 * small corners, never pills; a slider with its name on the left and its value on the right, a soft tint up to its value, a thin bright line
 * along its bottom edge and a round knob on that line; a button that switches a value with the name on the left and the value on the right. Sizes
 * are in GUI pixels. Plain logic, tested without the game.
 */
public final class VanillaLook {
	/** A box's corner radius at most, in GUI pixels: 8 screen pixels at GUI scale 2, as the boxes of the menu. */
	public static final float BOX_RADIUS = 4f;
	/** A corner is never more than this share of the control's height: a button 20 high gets 3. */
	public static final float MOST_OF_HEIGHT = 0.15f;

	/** What parts a control's name from its value in the game's texts ("Master Volume: 100%", "Subtitles: OFF"). */
	static final String VALUE_MARK = ": ";
	/** The game's word for a slider at nothing, and how it is shown. */
	static final String GAME_OFF = "OFF";
	static final String OFF = "Off";
	/** The room at each end of a control's box before its texts, and the least between its name and its value. */
	public static final int SLIDER_SIDE = 8;
	public static final int SLIDER_GAP = 5;
	/** The line along a slider's bottom edge: this far in from the box's sides, this thick. */
	public static final int LINE_INSET = 3;
	public static final float LINE_HEIGHT = 1.5f;
	/** The knob on the line: its radius at rest, and under the pointer and while it is dragged. */
	public static final float KNOB_REST = 2.5f;
	public static final float KNOB_LIT = 3.5f;
	/** The tint ends in a soft edge this wide. */
	public static final int FILL_EDGE = 5;
	/** A checkbox's mark is this share of its box. */
	public static final float CHECK_SHARE = 0.7f;

	private VanillaLook() {
	}

	/** @return the corner radius of a restyled control this high: small, and never more than {@link #MOST_OF_HEIGHT} of its height */
	public static float radius(float height) {
		return Math.max(0f, Math.min(BOX_RADIUS, height * MOST_OF_HEIGHT));
	}

	/** @return a control's name: its text before the value, or all of it where it holds none */
	public static String sliderName(String message) {
		int mark = message.indexOf(VALUE_MARK);
		return mark < 0 ? message : message.substring(0, mark);
	}

	/** @return a control's value ("100%", "OFF", "Fancy"), or an empty text where its text holds none */
	public static String sliderValue(String message) {
		int mark = message.indexOf(VALUE_MARK);
		return mark < 0 ? "" : message.substring(mark + VALUE_MARK.length());
	}

	/** @return a slider's value as it is shown: the game's "OFF" as "Off", any other as it is */
	public static String shownValue(String value) {
		return GAME_OFF.equals(value) ? OFF : value;
	}

	/** @return the room a control's name has: what its value, drawn in full, and the sides leave; the name is the one that shortens */
	public static int nameRoom(int width, int valueWidth) {
		return Math.max(0, width - 2 * SLIDER_SIDE - (valueWidth > 0 ? valueWidth + SLIDER_GAP : 0));
	}

	/** @return the middle of the knob at a share of the slider's way, from 0 to 1: on the line, inside the box at both ends */
	public static float knobX(int x, int width, float amount) {
		int travel = Math.max(0, width - 2 * LINE_INSET);
		return x + LINE_INSET + Math.clamp(amount, 0f, 1f) * travel;
	}

	/** @return how wide the tinted part is: up to the knob's middle, and nothing at all at 0 ("Off") */
	public static int tinted(int x, int width, float amount) {
		return amount <= 0f ? 0 : Math.clamp(Math.round(knobX(x, width, amount)) - x, 0, width);
	}

	/** @return the knob's radius by how far the pointer is on the slider or it is dragged, from 0 to 1: it grows slightly */
	public static float knobRadius(float lit) {
		return KNOB_REST + (KNOB_LIT - KNOB_REST) * Math.clamp(lit, 0f, 1f);
	}
}
