package dev.tim1337.lumio.features.visual.world;

/**
 * All ambience particles, in fixed arrays: nothing is allocated when one is born or dies, and each kind has a hard cap. Plain, so that its rules are unit tested.
 * A particle fades in, fades out at the end of its life, and fades out early when it is retired: nothing pops in or out.
 */
public final class AmbiencePool {
	/** What a particle is, with its hard cap and how it moves. */
	public enum Kind {
		FIREFLY(600, true, 0f), RAIN(800, false, 0.4f), RIPPLE(120, false, 0f), GLINT(150, true, 0f),
		DUST(220, true, 0.2f), LEAF(120, false, 1.3f), MIST(32, false, 0.25f), SHAFT(16, true, 0f), ASH(260, false, 1.6f), EMBER(220, true, 1.1f), WEB(24, false, 0.8f);

		final int cap;
		final boolean additive;
		final float wind;

		Kind(int cap, boolean additive, float wind) {
			this.cap = cap;
			this.additive = additive;
			this.wind = wind;
		}

		public int cap() {
			return cap;
		}

		public boolean additive() {
			return additive;
		}
	}

	static final Kind[] KINDS = Kind.values();
	private static final int CAPACITY;

	static {
		int sum = 0;
		for (Kind kind : KINDS) {
			sum += kind.cap;
		}
		CAPACITY = sum;
	}

	/** Fading out early takes this long. */
	static final int RETIRE_TICKS = 20;
	private static final int NOT_RETIRED = -1;

	final double[] x = new double[CAPACITY];
	final double[] y = new double[CAPACITY];
	final double[] z = new double[CAPACITY];
	final float[] vx = new float[CAPACITY];
	final float[] vy = new float[CAPACITY];
	final float[] vz = new float[CAPACITY];
	final float[] size = new float[CAPACITY];
	final float[] peak = new float[CAPACITY];
	final float[] phase = new float[CAPACITY];
	final float[] light = new float[CAPACITY];
	final int[] rgb = new int[CAPACITY];
	final int[] age = new int[CAPACITY];
	final int[] life = new int[CAPACITY];
	final int[] fade = new int[CAPACITY];
	final int[] retiring = new int[CAPACITY];
	final byte[] kind = new byte[CAPACITY];
	private final int[] counts = new int[KINDS.length];
	private int alive;

	public double x(int i) {
		return x[i];
	}

	public double y(int i) {
		return y[i];
	}

	public double z(int i) {
		return z[i];
	}

	public float vx(int i) {
		return vx[i];
	}

	public float vy(int i) {
		return vy[i];
	}

	public float vz(int i) {
		return vz[i];
	}

	public float size(int i) {
		return size[i];
	}

	public float phase(int i) {
		return phase[i];
	}

	public float light(int i) {
		return light[i];
	}

	public int rgb(int i) {
		return rgb[i];
	}

	public Kind kindOf(int i) {
		return KINDS[kind[i]];
	}

	/** @return how much of its life a particle has left, from 1 to 0 */
	public float lifeLeft(int i) {
		return 1f - age[i] / (float) life[i];
	}

	/** @return how many particles live */
	public int size() {
		return alive;
	}

	/** @return how many of a kind live */
	public int count(Kind of) {
		return counts[of.ordinal()];
	}

	/** Takes a particle out of the pool, at rest and fully lit; the caller sets the rest. @return its index, or -1 when its kind is at its cap */
	public int spawn(Kind of, double px, double py, double pz, float particleSize, float alpha, int rgbColor, int lifeTicks, int fadeTicks) {
		if (counts[of.ordinal()] >= of.cap) {
			return -1;
		}
		int i = alive++;
		counts[of.ordinal()]++;
		x[i] = px;
		y[i] = py;
		z[i] = pz;
		vx[i] = 0f;
		vy[i] = 0f;
		vz[i] = 0f;
		size[i] = particleSize;
		peak[i] = alpha;
		phase[i] = 0f;
		light[i] = 1f;
		rgb[i] = rgbColor;
		age[i] = 0;
		life[i] = lifeTicks;
		fade[i] = Math.max(1, Math.min(fadeTicks, lifeTicks / 2));
		retiring[i] = NOT_RETIRED;
		kind[i] = (byte) of.ordinal();
		return i;
	}

	/** Lets a particle fade out soon. It stays where it is in the pool and in its count until it has. */
	public void retire(int i) {
		if (retiring[i] == NOT_RETIRED) {
			retiring[i] = RETIRE_TICKS;
		}
	}

	/** Lets all of a kind fade out. */
	public void retireAll(Kind of) {
		for (int i = 0; i < alive; i++) {
			if (kind[i] == of.ordinal()) {
				retire(i);
			}
		}
	}

	/** Lets the particles out of range fade out, by the distance sideways from a point. */
	public void retireOutside(double centerX, double centerZ, double radius) {
		double most = radius * radius;
		for (int i = 0; i < alive; i++) {
			double dx = x[i] - centerX;
			double dz = z[i] - centerZ;
			if (dx * dx + dz * dz > most) {
				retire(i);
			}
		}
	}

	/** @return whether a particle is fading out early */
	public boolean retired(int i) {
		return retiring[i] != NOT_RETIRED;
	}

	/** @return how much of a particle shows now, from 0 to its peak */
	public float alpha(int i) {
		float share = Math.min(1f, Math.min(age[i], life[i] - age[i]) / (float) fade[i]);
		if (retiring[i] != NOT_RETIRED) {
			share = Math.min(share, retiring[i] / (float) RETIRE_TICKS);
		}
		share = Math.max(0f, share);
		return peak[i] * share * share * (3f - 2f * share);
	}

	/** Moves all on by one tick, with the wind in blocks per tick, and takes out those that have ended. */
	public void tick(float windX, float windZ) {
		for (int i = alive - 1; i >= 0; i--) {
			age[i]++;
			if (retiring[i] != NOT_RETIRED) {
				retiring[i]--;
			}
			if (age[i] >= life[i] || retiring[i] == 0) {
				remove(i);
				continue;
			}
			Kind of = KINDS[kind[i]];
			if (of == Kind.FIREFLY) {
				wander(i);
			}
			x[i] += vx[i] + windX * of.wind;
			y[i] += vy[i];
			z[i] += vz[i] + windZ * of.wind;
		}
	}

	/** Each axis of a firefly is a slow wave of its own, with different lengths, so the movement does not repeat. */
	private void wander(int i) {
		float time = age[i] + phase[i] * 10f;
		float own = phase[i];
		vx[i] = 0.010f * (float) Math.sin(time * 0.040f + own) + 0.006f * (float) Math.sin(time * 0.097f + own * 2.3f);
		vy[i] = 0.008f * (float) Math.sin(time * 0.033f + own * 1.7f) + 0.005f * (float) Math.cos(time * 0.081f + own);
		vz[i] = 0.010f * (float) Math.cos(time * 0.046f + own * 0.7f) + 0.006f * (float) Math.sin(time * 0.113f + own * 1.3f);
	}

	/** Empties the pool at once: for a new world, where the old one's particles mean nothing. */
	public void clear() {
		alive = 0;
		java.util.Arrays.fill(counts, 0);
	}

	private void remove(int i) {
		counts[kind[i]]--;
		int last = --alive;
		if (i == last) {
			return;
		}
		x[i] = x[last];
		y[i] = y[last];
		z[i] = z[last];
		vx[i] = vx[last];
		vy[i] = vy[last];
		vz[i] = vz[last];
		size[i] = size[last];
		peak[i] = peak[last];
		phase[i] = phase[last];
		light[i] = light[last];
		rgb[i] = rgb[last];
		age[i] = age[last];
		life[i] = life[last];
		fade[i] = fade[last];
		retiring[i] = retiring[last];
		kind[i] = kind[last];
	}
}
