package dev.tim1337.lumio.features.visual.world;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Radiance's choices and the numbers of how they move. Plain, so it is unit tested. */
public final class RadianceLook {
	/** How the light around the item looks. */
	public enum Style {
		OFF("Off"), GLOW("Glow"), ECHO("Echo");

		private final Component name;

		Style(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** Which items get the light. */
	public enum ShowsOn {
		ALL("All items"), WEAPONS("Weapons"), CHOSEN("Chosen");

		private final Component name;

		ShowsOn(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	private static final long SHORTEST_ECHO_NANOS = 90_000_000L;
	private static final long LONGEST_ECHO_NANOS = 220_000_000L;

	/** What is left of a swing's fan is made from the first part of the swing only, so it is gone before the arm is back. */
	private static final float FAN_SHARE = 0.6f;

	private RadianceLook() {
	}

	/** @return how far back an echo of a length (0 to 1) reaches */
	public static long echoSpanNanos(float length) {
		return SHORTEST_ECHO_NANOS + (long) ((LONGEST_ECHO_NANOS - SHORTEST_ECHO_NANOS) * Mth.clamp(length, 0f, 1f));
	}

	/** @return for how long after a swing starts it leaves copies; each of them is then gone within the span */
	public static long fanNanos(float length) {
		return (long) (echoSpanNanos(length) * FAN_SHARE);
	}

	/** Changes of style: the old one fades out, then the new one fades in. */
	public static final class Blend {
		private static final float FADE_NANOS = 250_000_000f;

		private Style shown = Style.OFF;
		private float level;
		private long last;

		/** Moves on to the time given, towards the wanted style. */
		public void update(Style wanted, long nanos) {
			float step = last == 0L ? 1f : Math.min(1f, (nanos - last) / FADE_NANOS);
			last = nanos;
			if (shown != wanted) {
				level -= step;
				if (level <= 0f) {
					level = 0f;
					shown = wanted;
				}
			} else if (shown != Style.OFF) {
				level = Math.min(1f, level + step);
			}
		}

		public Style shown() {
			return shown;
		}

		/** @return how much of the shown style there is, from 0 to 1 */
		public float level() {
			return level;
		}

		/** @return whether nothing is shown and nothing is left to fade */
		public boolean idle() {
			return shown == Style.OFF && level <= 0f;
		}
	}
}
