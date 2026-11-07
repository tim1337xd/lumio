package dev.tim1337.lumio.features.visual.world;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** The last places of something that moves, in a fixed ring of matrices, so that recording them each frame allocates nothing. */
final class PoseHistory {
	private static final int CAPACITY = 256;
	private static final long LEAST_GAP_NANOS = 5_000_000L;
	/** A sample this much older than the time asked for is from before a pause, and says nothing about that time. */
	private static final long STALE_NANOS = 200_000_000L;
	private static final float NEAR = 0.03f;

	private Matrix4f[] poses;
	private final long[] times = new long[CAPACITY];
	private int newest = -1;
	private int size;

	/** Records a place, unless the last one is only just recorded. */
	void record(long nanos, Matrix4fc pose) {
		if (size > 0 && nanos - times[newest] < LEAST_GAP_NANOS) {
			return;
		}
		if (poses == null) {
			poses = new Matrix4f[CAPACITY];
			for (int i = 0; i < CAPACITY; i++) {
				poses[i] = new Matrix4f();
			}
		}
		newest = (newest + 1) % CAPACITY;
		poses[newest].set(pose);
		times[newest] = nanos;
		size = Math.min(size + 1, CAPACITY);
	}

	/** @return the place at a time, as the latest sample not after it; null without one. It stays owned by this history. */
	Matrix4f at(long nanos) {
		for (int i = 0; i < size; i++) {
			int index = Math.floorMod(newest - i, CAPACITY);
			if (times[index] <= nanos) {
				return nanos - times[index] > STALE_NANOS ? null : poses[index];
			}
		}
		return null;
	}

	/** Works out the place at a time between its samples, so that a swing's copies lie close together at any frame rate. @return whether there were samples for it */
	boolean between(long nanos, Matrix4f into) {
		for (int i = 0; i < size; i++) {
			int index = Math.floorMod(newest - i, CAPACITY);
			if (times[index] <= nanos) {
				if (nanos - times[index] > STALE_NANOS) {
					return false;
				}
				if (i == 0) {
					into.set(poses[index]);
					return true;
				}
				int after = Math.floorMod(index + 1, CAPACITY);
				float share = (nanos - times[index]) / (float) Math.max(1L, times[after] - times[index]);
				poses[index].lerp(poses[after], share, into);
				return true;
			}
		}
		return false;
	}

	/** @return whether two places are so close that a copy at one would hide behind the item at the other */
	static boolean near(Matrix4f a, Matrix4f b) {
		float sum = 0f;
		for (int c = 0; c < 4; c++) {
			for (int r = 0; r < 4; r++) {
				sum += Math.abs(a.get(c, r) - b.get(c, r));
			}
		}
		return sum < NEAR;
	}
}
