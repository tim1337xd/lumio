package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.motion.Spring;

import java.util.Locale;

/**
 * One status bar between frames: what it shows now (values and their texts, built only when a value changes), where it is drawn (springs that
 * glide to its place in the stack), how visible it is, and its effects: the white trail of lost value after damage and the gain of experience.
 */
final class StatusBar {
	/** A thousand, for the numbers in short. */
	private static final int THOUSAND = 1000;
	/** The kinds of bars, with their saved id, the name shown in the editor and their icon. */
	enum Kind {
		HEALTH("health", "Health", "bar_heart"),
		MANA("mana", "Mana", "bar_mana"),
		FOOD("food", "Hunger", "bar_food"),
		AIR("air", "Air", "bubble"),
		VITALITY("vitality", "Vitality", "bar_vitality"),
		FUEL("fuel", "Drill fuel", "bar_fuel"),
		ARROWS("arrows", "Arrows", "bar_arrow"),
		SPEED("speed", "Speed", "bar_speed"),
		XP("xp", "Experience", "sparkle");

		final String id;
		final String title;
		final String icon;

		Kind(String id, String title, String icon) {
			this.id = id;
			this.title = title;
			this.icon = icon;
		}

		/** All kinds, once: {@code values()} makes a new array at every call, and the layout looks kinds up every frame. */
		private static final Kind[] ALL = values();

		static Kind byId(String id) {
			for (Kind kind : ALL) {
				if (kind.id.equals(id)) {
					return kind;
				}
			}
			return null;
		}
	}

	/** How long the lost part of a bar stays white before it shrinks, and how long its flash takes to settle. */
	private static final long TRAIL_HOLD_NANOS = 400_000_000L;
	private static final float FLASH_SECONDS = 0.25f;
	/** How long the experience gain floats and the level glows. */
	static final float GAIN_SECONDS = 1.4f;

	final Kind kind;
	/** Whether the bar is shown now (wanted and has a value), and how visible it is drawn, following that. */
	boolean present;
	final Spring shown = Spring.smooth(Spring.DEFAULT);
	/** Whether it was drawn before: a bar that appears starts at its place instead of gliding there from the corner. */
	boolean placed;

	// the values, and the fill: the share of the bar that is full, an extra share (absorption, overflow mana), and the defense of the health bar
	int current = Integer.MIN_VALUE;
	int maximum = Integer.MIN_VALUE;
	int extra = Integer.MIN_VALUE;
	int defense = Integer.MIN_VALUE;
	float fraction;
	float extraFraction;
	String valueText = "";
	String maxText = "";
	String defenseText = "";
	/** The value and what follows it, shortened ("12.4k", "/ 12.5k"), for a bar too narrow for them in full. */
	String valueShort = "";
	String maxShort = "";
	/** The number's own color as 0xRRGGBB, or 0 for the usual. */
	int valueRgb;
	/** Whether the text after the value is the absorption or overflow ("+375") rather than the maximum. */
	boolean extraInText;
	/** The share of the health that is real health, without absorption: low health is judged by it. */
	float realFraction = 1f;
	/** The value is a percentage (drill fuel by choice), shown without a maximum. */
	boolean percent;

	// where the bar is drawn now, in GUI pixels, and at which size
	final Spring x = Spring.smooth(Spring.DEFAULT);
	final Spring y = Spring.smooth(Spring.DEFAULT);
	final Spring width = Spring.smooth(Spring.DEFAULT);
	final Spring height = Spring.smooth(Spring.DEFAULT);
	float drawnScale = 1f;
	final int[] bounds = {0, 0, 0, 0};
	// its place this frame: the target of the springs
	float targetX;
	float targetY;
	float targetWidth;
	float targetHeight;
	float targetScale = 1f;
	/** Whether it sits in the stack this frame (otherwise it floats on its own). */
	boolean stacked;

	// the fill and the trail of lost value
	final Spring fill = Spring.smooth(Spring.DEFAULT);
	final Spring trail = Spring.smooth(Spring.DEFAULT);
	private float lastFraction = -1f;
	private long trailHeldUntil;
	private long hitAt;

	// experience: the last level and total, and the gain shown
	int level = Integer.MIN_VALUE;
	int total = Integer.MIN_VALUE;
	String levelText = "";
	String gainText = "";
	long gainAt;

	StatusBar(Kind kind) {
		this.kind = kind;
	}

	/** Sets the values, rebuilding the texts only when one of them changed. */
	void values(int current, int maximum, int extra, boolean grouped, boolean showMax, boolean extraShown) {
		if (current == this.current && maximum == this.maximum && extra == this.extra && !percent) {
			return;
		}
		percent = false;
		this.current = current;
		this.maximum = maximum;
		this.extra = extra;
		float most = Math.max(1, maximum);
		fraction = Math.max(0f, Math.min(1f, current / most));
		extraFraction = extra > 0 ? Math.max(0f, Math.min(1f, extra / most)) : 0f;
		valueText = format(current, grouped);
		// with absorption or overflow the extra follows the value, in its soft color: "7,605 +375"; otherwise the maximum, dim: "7,605 / 8,200"
		extraInText = extra > 0 && extraShown;
		maxText = extraInText ? "+" + format(extra, grouped) : showMax ? "/ " + format(maximum, grouped) : "";
		valueShort = shortNumber(current);
		maxShort = extraInText ? "+" + shortNumber(extra) : showMax ? "/ " + shortNumber(maximum) : "";
		realFraction = fraction;
	}

	/** Sets a number with what it is of after it, in place of the maximum: "2,864 Flint Arrow". */
	void named(int current, int maximum, String name, int numberRgb, boolean grouped) {
		values(current, maximum, 0, grouped, false, false);
		maxText = name;
		maxShort = name;
		valueRgb = numberRgb;
	}

	/** Sets a value shown as a percentage, such as the drill fuel. */
	void percentValue(int current, int maximum) {
		int share = Math.round(100f * current / Math.max(1, maximum));
		if (percent && share == this.current) {
			return;
		}
		percent = true;
		this.current = share;
		this.maximum = 100;
		this.extra = 0;
		fraction = Math.max(0f, Math.min(1f, share / 100f));
		extraFraction = 0f;
		valueText = share + "%";
		valueShort = valueText;
		maxShort = "";
		maxText = "";
		extraInText = false;
		realFraction = fraction;
	}

	void defense(int value, boolean grouped) {
		if (value != defense) {
			defense = value;
			defenseText = value > 0 ? format(value, grouped) : "";
		}
	}

	/** Experience: the level, its progress, and the total, whose growth shows as a gain floating up. */
	void experience(int level, float progress, int total, boolean live) {
		fraction = Math.max(0f, Math.min(1f, progress));
		extraFraction = 0f;
		if (level != this.level) {
			if (live && this.level != Integer.MIN_VALUE && level > this.level) {
				int gained = level - this.level;
				gainText = gained == 1 ? "+1 level" : "+" + gained + " levels";
				gainAt = System.nanoTime();
			}
			this.level = level;
			levelText = String.valueOf(level);
		} else if (live && this.total != Integer.MIN_VALUE && total > this.total) {
			gainText = "+" + (total - this.total);
			gainAt = System.nanoTime();
		}
		this.total = total;
	}

	/** @return seconds since the last experience gain, or a large number when there was none */
	float sinceGain() {
		return gainAt == 0L ? Float.MAX_VALUE : (System.nanoTime() - gainAt) / 1_000_000_000f;
	}

	/**
	 * Follows the fill to the current share: it drops at once on damage, leaving the lost part as a white trail that shrinks after a moment, and
	 * grows smoothly on healing. Without animations both jump.
	 */
	void advanceFill(boolean animated) {
		long now = System.nanoTime();
		if (!animated || lastFraction < 0f) {
			fill.snap(fraction);
			trail.snap(fraction);
			lastFraction = fraction;
			return;
		}
		if (fraction < lastFraction - 0.002f) {
			// keep the higher of the old trail and the value before the hit, so several quick hits add up
			trail.snap(Math.max(trail.value(), lastFraction));
			trailHeldUntil = now + TRAIL_HOLD_NANOS;
			hitAt = now;
			fill.snap(fraction);
		}
		lastFraction = fraction;
		// a drop snapped above, so this only ever grows towards the value
		fill.follow(fraction);
		if (now >= trailHeldUntil) {
			trail.follow(fraction);
		}
		if (trail.value() < fraction) {
			trail.snap(fraction);
		}
	}

	/** @return how white the trail is: bright right after the hit, settling to a softer white */
	float trailAlpha() {
		float since = (System.nanoTime() - hitAt) / 1_000_000_000f;
		float flash = Math.max(0f, 1f - since / FLASH_SECONDS);
		return 0.62f + 0.3f * flash;
	}

	/** @return a number in short: as it is below a thousand, else in thousands or millions with one decimal at most, as "12.4k" or "1.2M" */
	static String shortNumber(int value) {
		int size = Math.abs(value);
		if (size < THOUSAND) {
			return Integer.toString(value);
		}
		boolean millions = size >= THOUSAND * THOUSAND;
		long tenths = Math.round(value / (millions ? THOUSAND * THOUSAND / 10.0 : THOUSAND / 10.0));
		String number = tenths % 10 == 0 ? Long.toString(tenths / 10) : tenths / 10 + "." + Math.abs(tenths % 10);
		return number + (millions ? "M" : "k");
	}

	static String format(int value, boolean grouped) {
		if (!grouped || Math.abs(value) < 1000) {
			return Integer.toString(value);
		}
		return String.format(Locale.ROOT, "%,d", value);
	}
}
