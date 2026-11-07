package dev.tim1337.lumio.features.music;

/**
 * Whether texts in the world stand over each other, as they are seen: each a box around its direction from the eye, its real size after scaling turned into degrees. Plain numbers, so it is unit tested. A box is four values: its yaw and elevation
 * and half its width and height, all in degrees.
 */
final class AdlibBoxes {
	/** Values per box. */
	static final int VALUES = 4;

	private AdlibBoxes() {
	}

	/** @return half of a text's size, in degrees, as seen from a distance; both in blocks */
	static float halfAngle(float halfBlocks, double distance) {
		return (float) Math.toDegrees(Math.atan2(halfBlocks, Math.max(distance, 0.01)));
	}

	private static float wrapped(float degrees) {
		float wrapped = degrees % 360f;
		if (wrapped >= 180f) {
			wrapped -= 360f;
		}
		return wrapped < -180f ? wrapped + 360f : wrapped;
	}

	/** @return whether two boxes overlap, or come closer than the gap, in degrees */
	static boolean overlap(float yawA, float elevationA, float halfWidthA, float halfHeightA, float yawB, float elevationB, float halfWidthB, float halfHeightB, float gap) {
		// the same yaw apart is less far apart the steeper one looks up or down
		float across = Math.abs(wrapped(yawA - yawB)) * (float) Math.cos(Math.toRadians((elevationA + elevationB) / 2f));
		return across < halfWidthA + halfWidthB + gap && Math.abs(elevationA - elevationB) < halfHeightA + halfHeightB + gap;
	}

	/** @return whether a box overlaps any of the others */
	static boolean overlapsAny(float[] box, int at, float[] others, int count, float gap) {
		for (int i = 0; i < count; i++) {
			int other = i * VALUES;
			if (overlap(box[at], box[at + 1], box[at + 2], box[at + 3], others[other], others[other + 1], others[other + 2], others[other + 3], gap)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds the first place that is free.
	 *
	 * @param candidates the places to try, in order, as boxes
	 * @param others     the boxes that are standing
	 * @return which candidate is free, or -1 where none is
	 */
	static int firstFree(float[] candidates, int candidateCount, float[] others, int otherCount, float gap) {
		for (int i = 0; i < candidateCount; i++) {
			if (!overlapsAny(candidates, i * VALUES, others, otherCount, gap)) {
				return i;
			}
		}
		return -1;
	}
}
