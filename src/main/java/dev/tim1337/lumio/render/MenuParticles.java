package dev.tim1337.lumio.render;

import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.util.SeasonDates;

import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The game's particles in the main menu's world, simulated the way the game simulates them, tick by tick at 20 ticks per second of real time and
 * drawn between two ticks: falling cherry leaves in spring, fireflies at dusk and at night in summer, falling leaves in autumn (winter has the
 * game's snow weather, drawn by the shader). A small, fixed number of each lives in the part of the fields the camera sees; when one ends (its
 * life is over, or it reached the crops, where the game's would touch the ground) a new one starts. Their positions go to the shader through the
 * world's parameter texture.
 */
final class MenuParticles {
	/** The most particles at once, and the texels the shader reads them from: three per particle, after the world's own. */
	static final int MAX = 24;
	static final int TEXELS_EACH = 3;
	private static final int CHERRY_COUNT = 20;
	private static final int LEAF_COUNT = 20;
	private static final int FIREFLY_COUNT = 14;
	private static final float TICKS_PER_SECOND = 20f;
	// kinds, as the shader reads them; 0 is none
	private static final int NONE = 0;
	private static final int CHERRY = 1;
	private static final int LEAF = 2;
	private static final int FIREFLY = 3;
	/** The height of the scene's level ground, the shader's {@code G}, and where the camera rests, as in the shader. */
	private static final float GROUND = 64f;
	private static final float CAMERA_X = -3.3f;
	private static final float CAMERA_Y = GROUND + 3.35f;
	private static final float CAMERA_Z = -1f;
	private static final float CAMERA_YAW = 0.30f;
	/** The particles keep to what the camera sees: this far ahead and this many radians to each side. */
	private static final float NEAR = 3f;
	private static final float FAR = 18f;
	private static final float SIDE = 0.7f;
	/** Fireflies come out from this hour of the evening until this hour of the morning. */
	private static final float FIREFLY_EVENING = 19.5f;
	private static final float FIREFLY_MORNING = 5.5f;
	/** How far the crops reach above their farmland, where a falling leaf ends like the game's on the ground. */
	private static final float CROP_TOP = 1.8f;
	private static final double ENCODE_OFFSET = 128.0;
	private static final double ENCODE_RANGE = 256.0;

	private static final AnimClock CLOCK = new AnimClock();
	private static final Random RANDOM = new Random();
	private static final Particle[] PARTICLES = new Particle[MAX];
	private static float tickTime;
	private static int visible;
	private static int lastWanted = -1;

	static {
		for (int i = 0; i < MAX; i++) {
			PARTICLES[i] = new Particle();
		}
	}

	private MenuParticles() {
	}

	/** One particle, with the fields of the game's that matter here: position before and after the tick, speed, spin and life. */
	private static final class Particle {
		int kind;
		double x, y, z, xo, yo, zo;
		double xd, yd, zd;
		float roll, oRoll, rotSpeed, spinAcceleration;
		float size;
		int sprite;
		int age;
		int lifetime;
		// the falling leaves' wind: how strong, whether it swirls or drifts away, and the drift's direction and the swirl's period
		float gravity, windBig;
		boolean swirl, flowAway;
		double xaFlowScale, zaFlowScale, swirlPeriod;
	}

	/**
	 * Advances the particles on real time, once per frame the world is drawn.
	 *
	 * @param season the season shown
	 * @param hour the hour of the day shown
	 * @param enabled whether particles are shown at all
	 */
	static void update(int season, float hour, boolean enabled) {
		float step = CLOCK.tick();
		int wanted = !enabled ? NONE : season == SeasonDates.SPRING ? CHERRY : season == SeasonDates.AUTUMN ? LEAF
			: season == SeasonDates.SUMMER && (hour >= FIREFLY_EVENING || hour < FIREFLY_MORNING) ? FIREFLY : NONE;
		int count = wanted == CHERRY ? CHERRY_COUNT : wanted == LEAF ? LEAF_COUNT : wanted == FIREFLY ? FIREFLY_COUNT : 0;
		if (wanted != lastWanted) {
			// a new kind (the first frame, or the season or the hour changed): spread over their way already, so they do not all start at the top
			lastWanted = wanted;
			for (int i = 0; i < count; i++) {
				if (PARTICLES[i].kind == NONE) {
					spawn(PARTICLES[i], wanted, true);
				}
			}
		}
		tickTime += step * TICKS_PER_SECOND;
		int ticks = Math.min(4, (int) tickTime);
		tickTime -= (int) tickTime;
		for (int t = 0; t < ticks; t++) {
			for (int i = 0; i < MAX; i++) {
				Particle p = PARTICLES[i];
				if (p.kind != NONE) {
					tick(p);
				}
				// a particle that ended starts again, as the kind of the season, while there should be one in its place
				if (p.kind == NONE && i < count) {
					spawn(p, wanted, false);
				}
			}
		}
		visible = 0;
		for (Particle p : PARTICLES) {
			if (p.kind != NONE) {
				visible++;
			}
		}
	}

	/** @return whether particles are on screen, so the world is drawn often enough for them to move smoothly */
	static boolean active() {
		return visible > 0;
	}

	private static float groundAt(double x, double z) {
		// the shader's low terraces, one block at a time across the fields
		double u = 0.30 * Math.floor(z) - 0.21 * Math.floor(x) + 1.0;
		return GROUND + (float) Mth.clamp(Math.floor(u / 9.0), 0.0, 3.0);
	}

	private static void spawn(Particle p, int kind, boolean spread) {
		float angle = CAMERA_YAW + (RANDOM.nextFloat() * 2f - 1f) * SIDE;
		float distance = NEAR + RANDOM.nextFloat() * (FAR - NEAR);
		p.x = CAMERA_X + Mth.sin(angle) * distance;
		p.z = CAMERA_Z + Mth.cos(angle) * distance;
		float ground = groundAt(p.x, p.z);
		p.kind = kind;
		p.age = 0;
		p.xd = p.yd = p.zd = 0.0;
		p.roll = p.oRoll = 0f;
		if (kind == FIREFLY) {
			// the game's fireflies: small, slow, with a life of 200 to 300 ticks
			p.y = ground + 1.3 + RANDOM.nextFloat() * 1.8;
			p.size = 0.1f * (RANDOM.nextFloat() * 0.5f + 0.5f) * 2f * 0.75f;
			p.lifetime = 200 + RANDOM.nextInt(101);
			p.sprite = MenuGameTextures.FIREFLY;
			if (spread) {
				p.age = RANDOM.nextInt(p.lifetime);
			}
		} else {
			// the game's falling leaves: cherry leaves fall faster and drift away, other leaves fall slowly and swirl
			boolean cherry = kind == CHERRY;
			float gravityScale = cherry ? 0.25f : 0.07f;
			p.windBig = cherry ? 2f : 10f;
			p.swirl = !cherry;
			p.flowAway = cherry;
			float sizeScale = cherry ? 1f : 2f;
			p.yd = cherry ? 0.0 : -0.021;
			p.gravity = gravityScale * 1.2f * 0.0025f;
			p.size = sizeScale * (RANDOM.nextBoolean() ? 0.05f : 0.075f);
			p.lifetime = 300;
			p.rotSpeed = (float) Math.toRadians(RANDOM.nextBoolean() ? -30.0 : 30.0);
			p.spinAcceleration = (float) Math.toRadians(RANDOM.nextBoolean() ? -5.0 : 5.0);
			float r = RANDOM.nextFloat();
			p.xaFlowScale = Math.cos(Math.toRadians(r * 60f)) * p.windBig;
			p.zaFlowScale = Math.sin(Math.toRadians(r * 60f)) * p.windBig;
			p.swirlPeriod = Math.toRadians(1000f + r * 3000f);
			p.sprite = (cherry ? MenuGameTextures.CHERRY : MenuGameTextures.LEAF) + RANDOM.nextInt(MenuGameTextures.LEAF_SPRITES);
			// from above the view, so they fall into it; spread out, somewhere on their way down
			float top = (float) (CAMERA_Y + 1.0 + distance * 0.45);
			p.y = spread ? ground + CROP_TOP + RANDOM.nextFloat() * (top - ground - CROP_TOP) : top;
		}
		p.xo = p.x;
		p.yo = p.y;
		p.zo = p.z;
	}

	private static void tick(Particle p) {
		p.xo = p.x;
		p.yo = p.y;
		p.zo = p.z;
		p.oRoll = p.roll;
		if (++p.age >= p.lifetime) {
			p.kind = NONE;
			return;
		}
		if (p.kind == FIREFLY) {
			// now and then a new slow random speed, which friction calms down again
			if (RANDOM.nextFloat() > 0.95f || p.age == 1) {
				p.xd = -0.05 + 0.1 * RANDOM.nextFloat();
				p.yd = -0.05 + 0.1 * RANDOM.nextFloat();
				p.zd = -0.05 + 0.1 * RANDOM.nextFloat();
			}
			p.x += p.xd;
			p.y += p.yd;
			p.z += p.zd;
			p.xd *= 0.96;
			p.yd *= 0.96;
			p.zd *= 0.96;
			float ground = groundAt(p.x, p.z);
			if (p.y < ground + 1.1) {
				p.y = ground + 1.1;
				p.yd = Math.abs(p.yd);
			}
			return;
		}
		float progress = Math.min(p.age / 300f, 1f);
		double xa = 0.0;
		double za = 0.0;
		if (p.flowAway) {
			xa += p.xaFlowScale * Math.pow(progress, 1.25);
			za += p.zaFlowScale * Math.pow(progress, 1.25);
		}
		if (p.swirl) {
			xa += progress * Math.cos(progress * p.swirlPeriod) * p.windBig;
			za += progress * Math.sin(progress * p.swirlPeriod) * p.windBig;
		}
		p.xd += xa * 0.0025;
		p.zd += za * 0.0025;
		p.yd -= p.gravity;
		p.rotSpeed += p.spinAcceleration / 20f;
		p.roll += p.rotSpeed / 20f;
		p.x += p.xd;
		p.y += p.yd;
		p.z += p.zd;
		if (p.y < groundAt(p.x, p.z) + CROP_TOP) {
			p.kind = NONE;
		}
	}

	/**
	 * Writes the particles for the shader, three texels each from a texel on: position (16 bits each), roll and texture, size, how visible, how
	 * much it glows and its kind.
	 *
	 * @return how many were written
	 */
	static int write(ParameterTexture parameters, int first) {
		float partial = tickTime;
		int written = 0;
		for (Particle p : PARTICLES) {
			if (p.kind == NONE) {
				continue;
			}
			int texel = first + written * TEXELS_EACH;
			int x = encode(Mth.lerp(partial, p.xo, p.x));
			int y = encode(Mth.lerp(partial, p.yo, p.y));
			int z = encode(Mth.lerp(partial, p.zo, p.z));
			float roll = Mth.lerp(partial, p.oRoll, p.roll);
			int rollByte = Math.floorMod(Math.round(roll / Mth.TWO_PI * 256f), 256);
			float alpha = 1f;
			float glow = 0f;
			if (p.kind == FIREFLY) {
				// the game's firefly fades in and out, and its light a little ahead of that
				float life = Mth.clamp((p.age + partial) / p.lifetime, 0f, 1f);
				alpha = fade(life, 0.3f, 0.5f);
				glow = fade(life, 0.1f, 0.3f);
			}
			parameters.set(texel, x & 0xFF, x >> 8, y & 0xFF, y >> 8);
			parameters.set(texel + 1, z & 0xFF, z >> 8, rollByte, p.sprite);
			parameters.set(texel + 2, Math.min(255, Math.round(p.size * 1000f)), Math.round(alpha * 255f), Math.round(glow * 255f), p.kind);
			written++;
		}
		return written;
	}

	/** The game's fade of a firefly over its life: in over the first part, out over the last. */
	private static float fade(float life, float in, float out) {
		if (life >= 1f - out) {
			return (1f - life) / out;
		}
		return life <= in ? life / in : 1f;
	}

	private static int encode(double value) {
		return (int) Math.round(Mth.clamp((value + ENCODE_OFFSET) / ENCODE_RANGE, 0.0, 1.0) * 65535.0);
	}
}
