package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.render.GraphicsQuality;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the air around the camera filled: it reads the scene now and then, spawns what is wanted on valid spots in a radius around the camera, and lets everything else fade out. The rules
 * are in {@link AmbienceLook}, the particles in {@link AmbiencePool}, the drawing in {@link dev.tim1337.lumio.render.AmbienceDraw}. A few block checks per tick, nothing per frame.
 */
public final class AmbienceField {
	private static final int SCENE_EVERY_TICKS = 40;
	private static final float RADIUS = 64f;
	private static final float RAIN_REACH = 30f;
	private static final float TICK_SECONDS = 0.05f;
	private static final float WET_FADE_SECONDS = 4f;
	private static final int EMBER_RGB = 0xFF8A2A;
	private static final int TRIES_PER_KIND = 24;
	private static final int TRIES_WHEN_HURRYING = 120;
	private static final int HURRY_TICKS = 200;
	private static final double TELEPORT_BLOCKS_SQUARED = 64.0;
	private static final int CHECKS_PER_TICK = 80;
	private static final float OUT_OF_RANGE = 1.15f;
	private static final float NEAR = 5f;
	/** How many of a kind at normal amount. */
	private static final int[] NORMAL = new int[AmbiencePool.KINDS.length];
	private static final int ASH_RGB = 0x8C8680;
	private static final int DUST_RGB = 0xFFEFD0;
	private static final int WEB_RGB = 0xE6E6E6;
	private static final int MIST_RGB = 0xD8DEE6;
	private static final int RAIN_RGB = 0xA9BDD6;
	private static final int RIPPLE_RGB = 0xCFDDEE;
	private static final int GLINT_RGB = 0xFFF8E0;
	private static final int SHAFT_RGB = 0xFFE9BC;
	private static final int[] AUTUMN_RGB = {0xC96A1E, 0xD98E2B, 0xB5481B, 0xE0A83A};
	private static final float SMALLEST_LIGHT = 0.12f;

	static {
		set(AmbiencePool.Kind.FIREFLY, 300);
		set(AmbiencePool.Kind.RAIN, 700);
		set(AmbiencePool.Kind.RIPPLE, 90);
		set(AmbiencePool.Kind.GLINT, 110);
		set(AmbiencePool.Kind.DUST, 160);
		set(AmbiencePool.Kind.LEAF, 70);
		set(AmbiencePool.Kind.MIST, 22);
		set(AmbiencePool.Kind.SHAFT, 8);
		set(AmbiencePool.Kind.ASH, 180);
		set(AmbiencePool.Kind.EMBER, 110);
		set(AmbiencePool.Kind.WEB, 14);
	}

	private static void set(AmbiencePool.Kind kind, int normal) {
		NORMAL[kind.ordinal()] = normal;
	}

	private final AmbiencePool pool = new AmbiencePool();
	private final AmbienceLook.Scene scene = new AmbienceLook.Scene();
	private final AmbienceLook.Choice choice = new AmbienceLook.Choice();
	private final BlockPos.MutableBlockPos spot = new BlockPos.MutableBlockPos();
	/** The share of the spots tried for fireflies that were open air, followed slowly: how much room there is for them here. */
	private float fireflyOpen = 1f;
	private static final float OPEN_FOLLOW = 0.02f;
	/** Ground this far above the camera is a roof: a cave, a tunnel, a building. */
	private static final double UNDER_ROOF_BLOCKS = 6.0;
	private final SkyLook.Slew wet = new SkyLook.Slew(WET_FADE_SECONDS);
	private final float[] wanted = new float[AmbiencePool.KINDS.length];
	private ClientLevel lastLevel;
	private int sceneIn;
	private int hurry;
	private int checkAt;
	private double lastX = Double.NaN;
	private double lastZ;
	private float sunX;
	private float sunY;
	private boolean active;

	/** @return how strongly the ground reflects now, which fades in and out over some seconds */
	public float wet() {
		return wet.value();
	}

	public AmbiencePool pool() {
		return pool;
	}

	/** @return where the sun stands, sideways, as a share of a unit vector towards it */
	public float sunX() {
		return sunX;
	}

	public float sunY() {
		return sunY;
	}

	/** Moves everything on by a tick. Without the feature on and with nothing left to fade, this is one check. */
	public void tick(Minecraft client, AmbienceFeature ambience) {
		boolean on = ambience.isEnabled();
		if (!on && !active) {
			return;
		}
		ClientLevel level = client.level;
		if (level != lastLevel) {
			lastLevel = level;
			pool.clear();
			hurry = HURRY_TICKS;
			sceneIn = 0;
			lastX = Double.NaN;
		}
		if (level == null || client.player == null) {
			active = on;
			return;
		}
		if (client.isPaused()) {
			return;
		}
		if (on && !active) {
			hurry = HURRY_TICKS;
			sceneIn = 0;
		}
		if (--sceneIn <= 0) {
			sceneIn = SCENE_EVERY_TICKS;
			if (on) {
				ambience.fill(choice);
				readScene(level, client, ambience);
			}
			updateWanted(on, ambience);
		}
		Vec3 camera = client.gameRenderer.mainCamera().position();
		if (!Double.isNaN(lastX) && Mth.lengthSquared(camera.x - lastX, camera.z - lastZ) > TELEPORT_BLOCKS_SQUARED) {
			hurry = HURRY_TICKS;
		}
		lastX = camera.x;
		lastZ = camera.z;
		pool.retireOutside(camera.x, camera.z, RADIUS * OUT_OF_RANGE);
		check(level);
		pool.tick((float) AmbienceParticles.windX(), (float) AmbienceParticles.windZ());
		if (on) {
			spawnAll(level, ambience, camera);
			if (hurry > 0) {
				hurry--;
			}
		}
		wet.update(ambience.wetWanted(), TICK_SECONDS);
		active = on || pool.size() > 0 || wet.value() > 0f;
	}


	private void readScene(ClientLevel level, Minecraft client, AmbienceFeature ambience) {
		Vec3 camera = client.gameRenderer.mainCamera().position();
		spot.set(camera.x, camera.y, camera.z);
		int darken = level.getSkyDarken();
		// with a gap, so that the moment around dusk can't flip to and fro
		scene.night = scene.night ? darken > 3 : darken >= 6;
		scene.raining = level.getRainLevel(1f) > 0.2f;
		float sun = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, camera) * Mth.DEG_TO_RAD;
		sunX = -Mth.sin(sun);
		sunY = Mth.cos(sun);
		scene.sunUp = sunY > 0.08f && darken < 6;
		scene.noSky = !level.dimensionType().hasSkyLight();
		LocationProvider place = Addons.location();
		scene.knownWorld = place != null && place.inWorld();
		scene.underGround = scene.knownWorld ? place.has("mines") : level.getBrightness(LightLayer.SKY, spot) < 4 && level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot.getX(), spot.getZ()) > spot.getY() + 6;
		boolean known = scene.knownWorld && AmbienceLook.EXTRAS;
		scene.hintNoLeaves = known && place.has("ambience:no_leaves");
		scene.hintMist = known && place.has("ambience:mist");
		autumn = known && place.has("ambience:autumn");
		scene.moodAsh = known && place.has("mood:ash");
		scene.moodSparks = known && place.has("mood:sparks");
		scene.moodDust = known && place.has("mood:dust");
		scene.moodWebs = known && place.has("mood:webs");
	}

	private boolean autumn;

	private void updateWanted(boolean on, AmbienceFeature ambience) {
		float quality = GraphicsQuality.share();
		float area = 1f;
		for (AmbiencePool.Kind kind : AmbiencePool.KINDS) {
			float share = on ? AmbienceLook.share(kind, choice, scene) : 0f;
			if (share <= 0f) {
				if (wanted[kind.ordinal()] > 0f || pool.count(kind) > 0) {
					pool.retireAll(kind);
				}
				wanted[kind.ordinal()] = 0f;
				continue;
			}
			float amount = switch (kind) {
				case FIREFLY -> ambience.fireflyAmount();
				default -> ambience.intensity();
			};
			float spread = kind == AmbiencePool.Kind.SHAFT || kind == AmbiencePool.Kind.MIST || kind == AmbiencePool.Kind.DUST ? 1f : area;
			wanted[kind.ordinal()] = Math.min(kind.cap(), NORMAL[kind.ordinal()] * share * amount * quality * spread);
			if (kind == AmbiencePool.Kind.FIREFLY) {
				// by how much open air there is: a tunnel has room for fewer than a meadow, spread through all of it
				wanted[kind.ordinal()] = AmbienceLook.fireflyDensity(wanted[kind.ordinal()], fireflyOpen);
			}
		}
	}

	/** A few each tick, in turn: what has drifted into a block fades out, and each one's light follows the place it is at. */
	private void check(ClientLevel level) {
		int size = pool.size();
		if (size == 0) {
			return;
		}
		int darken = level.getSkyDarken();
		for (int n = 0; n < CHECKS_PER_TICK && n < size; n++) {
			checkAt = (checkAt + 1) % size;
			int i = checkAt;
			spot.set(pool.x[i], pool.y[i], pool.z[i]);
			AmbiencePool.Kind kind = AmbiencePool.KINDS[pool.kind[i]];
			if (kind == AmbiencePool.Kind.SHAFT || kind == AmbiencePool.Kind.MIST || kind == AmbiencePool.Kind.RIPPLE || kind == AmbiencePool.Kind.GLINT) {
				continue;
			}
			if (level.getBlockState(spot).isSolidRender()) {
				pool.retire(i);
				continue;
			}
			pool.light[i] = lightAt(level, darken);
		}
	}

	private float lightAt(ClientLevel level, int darken) {
		int sky = level.getBrightness(LightLayer.SKY, spot) - darken;
		int block = level.getBrightness(LightLayer.BLOCK, spot);
		return Math.max(SMALLEST_LIGHT, Math.max(sky, block) / 15f);
	}

	private void spawnAll(ClientLevel level, AmbienceFeature ambience, Vec3 camera) {
		RandomSource random = level.getRandom();
		int tries = hurry > 0 ? TRIES_WHEN_HURRYING : TRIES_PER_KIND;
		for (AmbiencePool.Kind kind : AmbiencePool.KINDS) {
			int missing = Mth.ceil(wanted[kind.ordinal()]) - pool.count(kind);
			if (missing <= 0) {
				continue;
			}
			// in a cave most tries hit rock, and those are cheap
			int attempts = Math.min(tries, missing) * (scene.underGround ? 4 : 1);
			for (int n = 0, made = 0; n < attempts && made < missing; n++) {
				if (trySpawn(kind, level, ambience, camera, random)) {
					made++;
				}
			}
		}
	}

	private boolean trySpawn(AmbiencePool.Kind kind, ClientLevel level, AmbienceFeature ambience, Vec3 camera, RandomSource random) {
		float radius = RADIUS;
		float reach = switch (kind) {
			case DUST, RIPPLE, LEAF -> Math.min(radius, 26f);
			case RAIN -> RAIN_REACH;
			case GLINT, WEB -> Math.min(radius, 36f);
			case MIST -> Math.min(radius, 42f);
			case SHAFT -> Math.min(radius, 38f);
			default -> scene.underGround ? Math.min(radius, 22f) : radius;
		};
		double distance = NEAR + random.nextDouble() * (reach - NEAR);
		double angle = random.nextDouble() * Mth.TWO_PI;
		double x = camera.x + Math.cos(angle) * distance;
		double z = camera.z + Math.sin(angle) * distance;
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		if (!level.hasChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ))) {
			return false;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
		// no ground over the void: nothing spawns below an island
		if (top <= level.getMinY() + 1) {
			return false;
		}
		float size = ambience.particleSize();
		// far ones have to read as large as near ones would
		float far = 1f + (float) distance / 40f;
		return switch (kind) {
			case FIREFLY -> firefly(level, ambience, x, z, top, camera, size * far, random);
			case RAIN -> rain(level, x, z, top, camera, random);
			case RIPPLE -> ripple(level, x, z, top, random);
			case GLINT -> glint(level, x, z, top, random);
			case LEAF -> leaf(level, x, z, top, size, random);
			case MIST -> mist(level, x, z, camera, random);
			case SHAFT -> shaft(level, x, z, top, random);
			case DUST -> airParticle(level, kind, x, z, camera, top, 0.028f * size, 0.8f, DUST_RGB, 90 + random.nextInt(120), 30, random, 0.003f);
			case ASH -> airParticle(level, kind, x, z, camera, top, 0.035f * size * far, 0.75f, ASH_RGB, 160 + random.nextInt(200), 30, random, -0.012f);
			case EMBER -> ember(level, x, z, top, camera, size * far, random);
			case WEB -> airParticle(level, kind, x, z, camera, top, 0.5f + random.nextFloat() * 0.7f, 0.26f, WEB_RGB, 240 + random.nextInt(240), 50, random, 0.001f);
		};
	}

	private boolean air(ClientLevel level, int x, int y, int z) {
		spot.set(x, y, z);
		return !level.getBlockState(spot).isSolidRender() && level.getFluidState(spot).isEmpty();
	}

	/** @return whether the particle's place is open: not in a block, and the light comes along */
	private float lit(ClientLevel level) {
		return lightAt(level, level.getSkyDarken());
	}

	/** Spawns in the air near the camera's height or the ground, on nothing that would hide it. */
	private boolean airParticle(ClientLevel level, AmbiencePool.Kind kind, double x, double z, Vec3 camera, int top, float size, float alpha, int rgb, int life, int fade, RandomSource random, float rise) {
		double y = spawnHeight(camera, top, random);
		if (!air(level, Mth.floor(x), Mth.floor(y), Mth.floor(z))) {
			return false;
		}
		float light = lit(level);
		int i = pool.spawn(kind, x, y, z, size, alpha, rgb, life, fade);
		if (i >= 0) {
			pool.vy[i] = rise;
			pool.vx[i] = (random.nextFloat() - 0.5f) * 0.006f;
			pool.vz[i] = (random.nextFloat() - 0.5f) * 0.006f;
			pool.phase[i] = random.nextFloat() * Mth.TWO_PI;
			pool.light[i] = light;
		}
		return i >= 0;
	}

	private double spawnHeight(Vec3 camera, int top, RandomSource random) {
		if (scene.underGround || top > camera.y + 6.0) {
			// under a roof or in a cave: around the camera, where the air is
			return camera.y - 3.0 + random.nextDouble() * 8.0;
		}
		return top + 0.5 + random.nextDouble() * 7.0;
	}

	/**
	 * A firefly in open air, spread out: under a roof or in a tunnel anywhere in the open air around the camera's height, outdoors over the ground, most
	 * over grass, leaves, flowers or crops. Never inside a block or right against one, never close to another one, and never a place used before: a spot
	 * that does not fit is simply not used.
	 */
	private boolean firefly(ClientLevel level, AmbienceFeature ambience, double x, double z, int top, Vec3 camera, float size, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
		boolean under = scene.underGround || ground > camera.y + UNDER_ROOF_BLOCKS;
		double y;
		if (under) {
			// any open air of the cave or tunnel counts, round the camera's height
			y = camera.y - 3.0 + random.nextDouble() * 9.0;
		} else {
			// over the void and far above or below the camera nothing is seen of them
			if (Math.abs(ground - camera.y) > 28.0) {
				return false;
			}
			spot.set(blockX, ground - 1, blockZ);
			BlockState below = level.getBlockState(spot);
			boolean grass = below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MOSS_BLOCK) || below.is(BlockTags.LEAVES) || below.is(BlockTags.FLOWERS)
				|| below.is(BlockTags.CROPS);
			if (!AmbienceLook.fireflySpot(grass, naturalGround(below), random.nextFloat() < 0.15f, choice.firefliesAnywhere)) {
				return false;
			}
			y = ground + 1.0 + random.nextDouble() * (random.nextFloat() < 0.8f ? 3.0 : 7.0);
		}
		boolean roomy = airAround(level, blockX, Mth.floor(y), blockZ);
		fireflyOpen += ((roomy ? 1f : 0f) - fireflyOpen) * OPEN_FOLLOW;
		if (!roomy || crowded(x, y, z)) {
			return false;
		}
		int i = pool.spawn(AmbiencePool.Kind.FIREFLY,
 x, y, z, 0.13f * size * (0.7f + random.nextFloat() * 0.6f), ambience.fireflyBrightness(), mixWhite(ambience.fireflyRgb(), random.nextFloat() < 0.25f ? 0.1f : 0.6f),
			700 + random.nextInt(900), 100);
		if (i >= 0) {
			pool.phase[i] = random.nextFloat() * Mth.TWO_PI;
		}
		return i >= 0;
	}

	/** @return whether a place is open air with room round it: not in a block, and not right against one on any side */
	private boolean airAround(ClientLevel level, int x, int y, int z) {
		return air(level, x, y, z) && air(level, x + 1, y, z) && air(level, x - 1, y, z) && air(level, x, y + 1, z) && air(level, x, y - 1, z)
			&& air(level, x, y, z + 1) && air(level, x, y, z - 1);
	}

	/** @return whether a firefly there would be too near one that is there already */
	private boolean crowded(double x, double y, double z) {
		for (int i = 0; i < pool.size(); i++) {
			if (!pool.retired(i) && pool.kindOf(i) == AmbiencePool.Kind.FIREFLY && AmbienceLook.fireflyCrowded(x, y, z, pool.x(i), pool.y(i), pool.z(i))) {
				return true;
			}
		}
		return false;
	}

	/** @return whether the block is natural ground (grass, dirt, sand, stone, moss, snow) rather than a built roof or wall */
	public static boolean naturalGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SNOW) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.SHORT_GRASS) || state.is(Blocks.GRAVEL);
	}

	private static int mixWhite(int rgb, float share) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return Math.round(r + (255 - r) * share) << 16 | Math.round(g + (255 - g) * share) << 8 | Math.round(b + (255 - b) * share);
	}

	private boolean rain(ClientLevel level, double x, double z, int top, Vec3 camera, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		spot.set(blockX, top + 1, blockZ);
		// only where the sky is open: it does not ask the server's weather
		if (!level.canSeeSky(spot)) {
			return false;
		}
		double from = Math.max(top, camera.y) + 3.0 + random.nextDouble() * 14.0;
		float fall = 0.55f + random.nextFloat() * 0.25f;
		int life = (int) ((from - top) / fall);
		if (life < 6) {
			return false;
		}
		float light = lit(level);
		int i = pool.spawn(AmbiencePool.Kind.RAIN, x, from, z, 0.018f, 0.72f, RAIN_RGB, life, 3);
		if (i >= 0) {
			pool.vy[i] = -fall;
			pool.light[i] = light;
		}
		return i >= 0;
	}

	private boolean water(ClientLevel level, int x, int y, int z) {
		spot.set(x, y, z);
		return level.getFluidState(spot).is(FluidTags.WATER);
	}

	private boolean ripple(ClientLevel level, double x, double z, int top, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		boolean water = water(level, blockX, top - 1, blockZ);
		spot.set(blockX, top, blockZ);
		if (!AmbienceLook.ringSpot(water, level.getBlockState(spot).isAir(), level.canSeeSky(spot))) {
			return false;
		}
		float light = lit(level);
		int i = pool.spawn(AmbiencePool.Kind.RIPPLE, x, top - 1 + WATER_TOP, z, 0.45f + random.nextFloat() * 0.5f, 0.6f, RIPPLE_RGB, 18 + random.nextInt(14), 5);
		if (i >= 0) {
			pool.light[i] = light;
		}
		return i >= 0;
	}

	/** A full water block's surface, a little under its top, where the game draws it, and a hair over it. */
	private static final float WATER_TOP = 0.91f;

	private boolean glint(ClientLevel level, double x, double z, int top, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		boolean water = water(level, blockX, top - 1, blockZ);
		spot.set(blockX, top, blockZ);
		if (!AmbienceLook.glintSpot(water, level.getBlockState(spot).isAir(), level.canSeeSky(spot))) {
			return false;
		}
		// the light on the water is the sun's or the moon's: fainter by night
		float strength = scene.night ? 0.35f : 0.9f;
		int i = pool.spawn(AmbiencePool.Kind.GLINT, x, top - 1 + WATER_TOP, z, 0.05f + random.nextFloat() * 0.07f, strength, GLINT_RGB, 14 + random.nextInt(26), 6);
		if (i >= 0) {
			pool.phase[i] = random.nextFloat() * Mth.TWO_PI;
		}
		return i >= 0;
	}

	private boolean leaf(ClientLevel level, double x, double z, int top, float size, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		spot.set(blockX, top - 1, blockZ);
		// a leaf only falls from where there are leaves
		if (!level.getBlockState(spot).is(BlockTags.LEAVES)) {
			return false;
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
		double from = top - 1 - random.nextDouble() * Math.min(4.0, Math.max(0, top - ground - 1));
		if (ground >= from - 2.0 || !air(level, blockX, Mth.floor(from) - 1, blockZ)) {
			return false;
		}
		float fall = 0.018f + random.nextFloat() * 0.018f;
		int life = Math.min(600, (int) ((from - ground) / fall));
		spot.set(blockX, Mth.floor(from) - 1, blockZ);
		int rgb = autumn ? AUTUMN_RGB[random.nextInt(AUTUMN_RGB.length)] : BiomeColors.getAverageFoliageColor(level, spot) & 0xFFFFFF;
		float light = lit(level);
		int i = pool.spawn(AmbiencePool.Kind.LEAF, x, from - 1.0, z, 0.09f * size * (0.75f + random.nextFloat() * 0.5f), 0.95f, rgb, life, 16);
		if (i >= 0) {
			pool.vy[i] = -fall;
			pool.phase[i] = random.nextFloat() * Mth.TWO_PI;
			pool.light[i] = light;
		}
		return i >= 0;
	}

	private boolean mist(ClientLevel level, double x, double z, Vec3 camera, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
		if (Math.abs(ground - camera.y) > 24.0) {
			return false;
		}
		spot.set(blockX, ground - 1, blockZ);
		BlockState below = level.getBlockState(spot);
		boolean over = below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MOSS_BLOCK) || naturalGround(below) || !below.getFluidState().isEmpty();
		spot.set(blockX, ground, blockZ);
		if (!over || !level.canSeeSky(spot)) {
			return false;
		}
		float light = lit(level);
		int i = pool.spawn(AmbiencePool.Kind.MIST, x, ground + 1.7 + random.nextDouble() * 0.8, z, 3.5f + random.nextFloat() * 4f, 0.2f, MIST_RGB, 360 + random.nextInt(360), 90);
		if (i >= 0) {
			pool.phase[i] = random.nextFloat() * Mth.TWO_PI;
			pool.light[i] = light;
		}
		return i >= 0;
	}

	private boolean shaft(ClientLevel level, double x, double z, int top, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		spot.set(blockX, top, blockZ);
		if (!level.canSeeSky(spot)) {
			return false;
		}
		// strongest while the sun is low; at noon they would stand straight and show nothing
		float low = Mth.clamp(1.1f - sunY, 0.25f, 1f);
		int i = pool.spawn(AmbiencePool.Kind.SHAFT, x, top, z, 1.4f + random.nextFloat() * 3f, 0.13f * low, SHAFT_RGB, 240 + random.nextInt(240), 70);
		if (i >= 0) {
			pool.phase[i] = random.nextFloat() * Mth.TWO_PI;
		}
		return i >= 0;
	}

	private boolean ember(ClientLevel level, double x, double z, int top, Vec3 camera, float size, RandomSource random) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		boolean covered = top > camera.y + 6.0;
		double y = covered ? camera.y - 3.0 + random.nextDouble() * 6.0 : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) + 0.3 + random.nextDouble() * 2.5;
		if (!air(level, blockX, Mth.floor(y), blockZ)) {
			return false;
		}
		int rgb = EMBER_RGB;
		float heat = random.nextFloat();
		int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) + 38f * heat));
		int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * (0.7f + 0.5f * heat)));
		int b = Math.min(255, Math.round((rgb & 0xFF) * (0.5f + 0.5f * heat)));
		int i = pool.spawn(AmbiencePool.Kind.EMBER, x, y, z, 0.04f * size * (0.6f + random.nextFloat() * 0.7f), 0.75f + random.nextFloat() * 0.25f, r << 16 | g << 8 | b,
			60 + random.nextInt(90), 12);
		if (i >= 0) {
			pool.vy[i] = 0.012f + random.nextFloat() * 0.022f;
			pool.phase[i] = random.nextFloat() * Mth.TWO_PI;
		}
		return i >= 0;
	}
}
