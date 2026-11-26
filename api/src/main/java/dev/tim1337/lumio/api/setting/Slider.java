package dev.tim1337.lumio.api.setting;

/** A number between a lowest and a highest value, set with a slider and a value field. */
public interface Slider extends AddonSetting<Float> {
	float min();

	float max();

	/** Snaps the value to steps of the given size. @return this */
	Slider step(float size);

	/** Shows the value as a percentage. @return this */
	Slider percent();

	/**
	 * Moves the slider on a logarithmic scale, for a range over several orders of size such as 100k to 100M coins: each step of the track is
	 * the same factor, not the same amount. Has no effect when the lowest value is not above zero. @return this
	 */
	Slider logarithmic();

	/** Lets the knob rest on these values when it is dragged close to one; typed values are kept as typed. @return this */
	Slider snapTo(float... values);

	/** Shows the value as an amount of coins ("750k", "2.5M") and reads one typed like that. @return this */
	Slider coins();

	/**
	 * Shows a unit after the value, so the unit stands in the value and not in the label: " s" for seconds, " ms", " blocks", " px". Only the
	 * display; the stored value stays the number. @return this
	 * @since 1.27.0
	 */
	default Slider unit(String suffix) {
		return this;
	}

	/** Shows the lowest value, 0, as "Off", for an amount where none at all is the same as off. @return this @since 1.27.0 */
	default Slider offAtZero() {
		return offAtZero("Off");
	}

	/**
	 * Shows the lowest value, 0, as a word: "Auto" where 0 leaves it to the game or the data, "Any" where 0 sets no limit. @return this
	 * @since 1.27.0
	 */
	default Slider offAtZero(String shown) {
		return this;
	}

	/** @return a value as the slider shows it */
	String format(float value);
}
