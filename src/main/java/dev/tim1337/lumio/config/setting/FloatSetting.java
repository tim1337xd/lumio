package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.SliderControl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/** A numeric setting within a range, shown as a slider with a given number of decimals. */
public class FloatSetting extends Setting<Float> implements Slider {
	private final float min;
	private final float max;
	private final int decimals;

	public FloatSetting(String key, Component label, float defaultValue, float min, float max, int decimals) {
		super(key, label, defaultValue);
		this.min = min;
		this.max = max;
		this.decimals = decimals;
	}

	/** @return the lowest value: the fixed minimum, or a higher limit that depends on the screen */
	public float min() {
		return lowerLimit == null ? min : Mth.clamp((float) lowerLimit.getAsDouble(), min, max);
	}

	private DoubleSupplier lowerLimit;
	private float step;

	/** Raises the minimum while a condition (such as the screen size) requires it; the slider shows the raised limit. */
	public FloatSetting lowerLimit(DoubleSupplier limit) {
		lowerLimit = limit;
		return this;
	}

	private DoubleSupplier upperLimit;
	private boolean doubleClickResets;

	/** Lowers the maximum while a condition (such as the screen size) requires it; the slider shows the lowered limit. */
	public FloatSetting upperLimit(DoubleSupplier limit) {
		upperLimit = limit;
		return this;
	}

	/** Lets a double click on the slider put the value back to its default. */
	public FloatSetting doubleClickResets() {
		doubleClickResets = true;
		return this;
	}

	/** @return whether a double click on the slider resets it */
	public boolean resetsOnDoubleClick() {
		return doubleClickResets;
	}

	private DoubleSupplier atMost;
	private DoubleSupplier atLeast;

	/** Keeps the value at or below another setting's, such as a warning threshold below the next one; the slider's range stays the same. */
	public FloatSetting atMost(DoubleSupplier limit) {
		atMost = limit;
		return this;
	}

	/** Keeps the value at or above another setting's; the slider's range stays the same. */
	public FloatSetting atLeast(DoubleSupplier limit) {
		atLeast = limit;
		return this;
	}

	/** Lets the value move in steps, such as 5 % steps. */
	public FloatSetting step(float size) {
		step = size;
		return this;
	}

	private boolean logarithmic;
	private float[] snaps = new float[0];
	private boolean coins;
	/** How close the knob has to come to a snap value, as a share of the track, to rest on it. */
	private static final float SNAP_REACH = 0.02f;
	/** The significant digits an amount of coins keeps: fewer while dragged (1.5M, 230k), more when typed (1.25M). */
	private static final int DRAGGED_DIGITS = 2;
	private static final int TYPED_DIGITS = 3;

	/** Moves the slider on a logarithmic scale; has no effect while the lowest value is not above zero. */
	public FloatSetting logarithmic() {
		logarithmic = true;
		return this;
	}

	/** Lets the knob rest on these values when it is dragged close to one. */
	public FloatSetting snapTo(float... values) {
		snaps = values.clone();
		return this;
	}

	/** Shows the value as an amount of coins ("2.5M") and reads one typed like that. */
	public FloatSetting coins() {
		coins = true;
		return this;
	}

	/** @return whether the value is an amount of coins, which may be typed with k, M or B */
	public boolean isCoins() {
		return coins;
	}

	private boolean logScale() {
		return logarithmic && min() > 0f;
	}

	/** @return where a value sits on the slider's track, from 0 to 1 */
	public float fraction(float value) {
		float low = min();
		float high = max();
		if (high <= low) {
			return 0f;
		}
		if (logScale()) {
			return Mth.clamp((float) (Math.log(value / low) / Math.log(high / low)), 0f, 1f);
		}
		return Mth.clamp((value - low) / (high - low), 0f, 1f);
	}

	/** @return the value at a place on the track: on the slider's scale, resting on a snap value close to it, as it is stored */
	public float valueAt(float fraction) {
		for (float snap : snaps) {
			if (Math.abs(fraction - fraction(snap)) <= SNAP_REACH) {
				return rounded(snap);
			}
		}
		float low = min();
		float high = max();
		float value = logScale() ? (float) (low * Math.pow(high / low, fraction)) : low + fraction * (high - low);
		return rounded(coins ? significant(value, DRAGGED_DIGITS) : value);
	}

	/** @return the value rounded to a count of significant digits: 1,234,567 to two is 1,200,000 */
	static float significant(float value, int digits) {
		if (value <= 0f) {
			return value;
		}
		double unit = Math.pow(10, Math.floor(Math.log10(value)) - digits + 1);
		return (float) (Math.round(value / unit) * unit);
	}

	/** @return the value typed into the slider's field, as shown (75 for 75 %, "2.5M" for coins), or NaN when it is no number */
	public float parseTyped(String text) {
		if (coins) {
			return (float) CoinAmount.parse(text);
		}
		try {
			float value = Float.parseFloat(text.replace(",", ".").replace("%", "").trim());
			return percent ? value / 100f : value;
		} catch (NumberFormatException e) {
			return Float.NaN;
		}
	}

	/** @return the value as it is stored: within the limits and the order it keeps with other settings, on a step or rounded to the displayed
	 *         precision */
	public float rounded(float value) {
		float low = min();
		float high = max();
		if (atLeast != null) {
			low = Mth.clamp((float) atLeast.getAsDouble(), low, high);
		}
		if (atMost != null) {
			high = Mth.clamp((float) atMost.getAsDouble(), low, high);
		}
		if (coins) {
			return Mth.clamp(significant(value, TYPED_DIGITS), low, high);
		}
		if (step > 0f) {
			return Mth.clamp(low + Math.round((value - low) / step) * step, low, high);
		}
		float factor = (float) Math.pow(10, percent ? decimals + 2 : decimals);
		return Mth.clamp(Math.round(value * factor) / factor, low, high);
	}

	/** @return the highest value: the fixed maximum, or a lower limit that depends on the screen */
	public float max() {
		return upperLimit == null ? max : Mth.clamp((float) upperLimit.getAsDouble(), min(), max);
	}

	private boolean percent;
	private boolean offAtZero;

	/** Displays the value as a percentage (0.5 as 50%), for amounts such as volume. */
	public FloatSetting percent() {
		percent = true;
		return this;
	}

	/** Shows the lowest value, 0, as "Off", for an amount where nothing at all is the same as off. */
	public FloatSetting offAtZero() {
		offAtZero = true;
		return this;
	}

	/** @return the value as displayed next to the slider */
	public String format(float value) {
		if (offAtZero && value <= 0f) {
			return "Off";
		}
		if (percent) {
			return Math.round(value * 100f) + "%";
		}
		if (coins) {
			return CoinAmount.format(value);
		}
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}

	@Override
	public String valueText() {
		return format(get());
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(get());
	}

	@Override
	public void load(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(Mth.clamp(json.getAsFloat(), min, max));
		}
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new SliderControl(this, onChanged);
	}
}
