package dev.tim1337.lumio.menu;

import net.minecraft.util.Mth;

/**
 * How the Transparency slider moves the blur along with the tint: the clearer the glass, the less is blurred. The blur follows a curve through the
 * defaults, so that they stay exactly as they are, and a blur set by itself keeps its distance from that curve. Plain numbers, so it is unit tested.
 */
final class GlassMix {
	/** Where the curve passes the defaults: the tint's opacity of 0.48 is this transparency, at this blur. */
	private static final float DEFAULT_TRANSPARENCY = 0.52f;
	private static final float DEFAULT_BLUR = 0.55f;

	private GlassMix() {
	}

	/** @return the blur that goes with a transparency from 0 to 1: full at none, none at full */
	static float blurFor(float transparency) {
		if (transparency <= DEFAULT_TRANSPARENCY) {
			return Mth.lerp(transparency / DEFAULT_TRANSPARENCY, 1f, DEFAULT_BLUR);
		}
		return Mth.lerp((transparency - DEFAULT_TRANSPARENCY) / (1f - DEFAULT_TRANSPARENCY), DEFAULT_BLUR, 0f);
	}

	/** @return the blur after the slider moved from one transparency to another, from a blur now set to any value */
	static float blurAfter(float blur, float from, float to) {
		return Mth.clamp(blurFor(to) + blur - blurFor(from), 0f, 1f);
	}
}
