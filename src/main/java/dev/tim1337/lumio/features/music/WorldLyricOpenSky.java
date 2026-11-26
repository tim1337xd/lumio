package dev.tim1337.lumio.features.music;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Where the view is open: a coarse grid of rays across the screen, each looking whether anything stands in its direction within the reach (the
 * render distance, at most {@link #MOST_REACH_BLOCKS}). A cell with no block behind it is open, also below the horizon and looking down: the
 * void beside or under an island, the open sky, the sea far off. The grid is looked at a few times per second: the sections the rays pass are copied
 * on the render thread, a few per frame ({@link WorldLyricSnapshot}), and the rays follow those copies on a background thread, never the live world;
 * the lyrics are placed by what it finds ({@link LyricSpots}), and the cinematic camera takes the largest open area near the middle of the view
 * ({@link #largest}). Each ray also notes how bright
 * what it sees is, for the contrast behind the lyrics ({@link #backdrop}). The finding is plain logic, tested
 * without the game.
 */
public final class WorldLyricOpenSky {
	/** The grid across and up the screen. */
	public static final int COLUMNS = 16;
	public static final int ROWS = 9;
	/** A new look this often: about four a second. */
	private static final float LOOK_SECONDS = 0.25f;
	/** How far a ray looks, at most, in blocks; a look this old is too old to place by. */
	static final double MOST_REACH_BLOCKS = 128.0;
	private static final float STALE_SECONDS = 1.5f;
	/** The most sections copied in a frame, and for how many looks a copy serves before it is made anew (about a second). */
	private static final int COPIES_PER_FRAME = 32;
	private static final int KEEP_LOOKS = 4;
	/** A cell this far to the side of a region's middle counts this much less for being off the middle of the view. */
	private static final float MIDDLE_WEIGHT = 0.6f;

	/** The open cells of the last whole look, and those being looked at. */
	private final boolean[] open = new boolean[COLUMNS * ROWS];
	private final boolean[] looking = new boolean[COLUMNS * ROWS];
	/** Per cell of the last whole look and of the one being made: how far the ray went (the reach where it met nothing), and how bright what it
	 * met is, from 0 to 1 (the sky where it met nothing). */
	private final float[] distance = new float[COLUMNS * ROWS];
	private final float[] lookingDistance = new float[COLUMNS * ROWS];
	private final float[] light = new float[COLUMNS * ROWS];
	private final float[] lookingLight = new float[COLUMNS * ROWS];
	private int skyColor;
	private int fogColor;
	/** How many whole looks have been made: a new one is a new chance to check what lies behind the lyrics. */
	private int looks;
	private float sinceLook = LOOK_SECONDS;
	private float age = Float.MAX_VALUE;
	/** How long ago what was seen was forgotten, in seconds: the first look after that is waited for, for a moment. */
	private float sinceReset;
	/** The view the last whole look was made from, and the one being made. */
	private float yaw;
	private float pitch;
	private double halfFovY;
	private double aspect;
	private float lookYaw;
	private float lookPitch;
	private double lookHalfFovY;
	private double lookAspect;
	private double lookReach;
	private final double[] direction = new double[3];
	/** Per cell of the look being made: the block its ray met and the face, which the render thread reads the light of. */
	private final long[] lookingBlock = new long[COLUMNS * ROWS];
	private final Direction[] lookingFace = new Direction[COLUMNS * ROWS];
	private final BlockPos.MutableBlockPos litPos = new BlockPos.MutableBlockPos();
	private final BlockPos.MutableBlockPos litBeside = new BlockPos.MutableBlockPos();
	/** What a look is doing: its rays name the sections they pass on a background thread, those are copied here, and its rays follow the copies there. */
	private enum Phase {
		IDLE, PLANNING, COPYING, TRACING
	}

	private Phase phase = Phase.IDLE;
	/** Whether the work on the background thread has ended, and failed: {@link #workDone} hands what it wrote over to the render thread. */
	private volatile boolean workDone;
	private volatile boolean workFailed;
	/** Raised by {@link #reset}, so a look still under way then is not taken. */
	private int generation;
	private int lookGeneration;
	/** The world and eye of the look under way, the sections its rays pass, and those copied so far. */
	private ClientLevel lookLevel;
	private Vec3 lookEye = Vec3.ZERO;
	private long[] planned = new long[0];
	private int nextCopy;
	private Long2ObjectOpenHashMap<WorldLyricSnapshot.Section> copied = new Long2ObjectOpenHashMap<>();
	private Long2ObjectOpenHashMap<WorldLyricSnapshot.Section> copying = new Long2ObjectOpenHashMap<>();
	private int lookNumber;

	/** Forgets what was seen, as when the lyrics are off. A look still on its thread keeps its own copies, and is not taken. */
	void reset() {
		generation++;
		sinceReset = 0f;
		sinceLook = LOOK_SECONDS;
		age = Float.MAX_VALUE;
		if (phase == Phase.COPYING) {
			phase = Phase.IDLE;
		}
		lookLevel = null;
		copied = new Long2ObjectOpenHashMap<>();
		copying = new Long2ObjectOpenHashMap<>();
	}

	/** Takes what the background thread finished and works on the look, a new one every {@link #LOOK_SECONDS}; only a few sections are copied per frame. */
	void step(Minecraft client, Vec3 eye, float viewYaw, float viewPitch, double viewHalfFovY, double viewAspect, float dt) {
		age += dt;
		sinceLook += dt;
		sinceReset += dt;
		ClientLevel level = client.level;
		if (phase == Phase.PLANNING || phase == Phase.TRACING) {
			if (!workDone) {
				return;
			}
			boolean good = !workFailed && lookGeneration == generation && level == lookLevel && level != null;
			if (phase == Phase.TRACING) {
				phase = Phase.IDLE;
				if (good) {
					takeLook(level);
				}
			} else {
				phase = good ? Phase.COPYING : Phase.IDLE;
				nextCopy = 0;
				copying = new Long2ObjectOpenHashMap<>(planned.length);
			}
		}
		if (level == null || client.player == null || phase == Phase.COPYING && level != lookLevel) {
			phase = Phase.IDLE;
			return;
		}
		if (phase == Phase.IDLE) {
			if (sinceLook >= LOOK_SECONDS) {
				sinceLook = 0f;
				planLook(client, level, eye, viewYaw, viewPitch, viewHalfFovY, viewAspect);
			}
			return;
		}
		copySections(level);
		if (nextCopy == planned.length) {
			startLook(level);
		}
	}

	/** Fixes the view of the next look; its rays are walked on a background thread. */
	private void planLook(Minecraft client, ClientLevel level, Vec3 eye, float viewYaw, float viewPitch, double viewHalfFovY, double viewAspect) {
		lookLevel = level;
		lookEye = eye;
		lookYaw = viewYaw;
		lookPitch = viewPitch;
		lookHalfFovY = viewHalfFovY;
		lookAspect = viewAspect;
		lookReach = Math.min(MOST_REACH_BLOCKS, client.options.getEffectiveRenderDistance() * 16.0);
		// the sky's colors now (as the frame drew them: straight up and at the horizon), for the cells where nothing stands
		skyColor = dev.tim1337.lumio.render.FogState.skyColor();
		fogColor = dev.tim1337.lumio.render.FogState.color();
		lookNumber++;
		lookGeneration = generation;
		work(Phase.PLANNING, () -> plan(eye));
	}

	/** Starts work on the background thread; where it takes none now, this look is left out. */
	private void work(Phase next, Runnable job) {
		workDone = false;
		workFailed = false;
		phase = next;
		try {
			Util.nonCriticalIoPool().execute(job);
		} catch (RejectedExecutionException e) {
			phase = Phase.IDLE;
		}
	}

	/** On a background thread: which sections the rays of the look pass. Plain arithmetic, nothing of the world. */
	private void plan(Vec3 eye) {
		try {
			LongOpenHashSet wanted = new LongOpenHashSet();
			for (int row = 0; row < ROWS; row++) {
				for (int column = 0; column < COLUMNS; column++) {
					walk(eye, screenX(column), screenY(row), wanted);
				}
			}
			planned = wanted.toLongArray();
		} catch (RuntimeException e) {
			workFailed = true;
		}
		workDone = true;
	}

	private void walk(Vec3 eye, double x, double y, LongOpenHashSet into) {
		WorldLyricView.direction(x, y, lookYaw, lookPitch, lookHalfFovY, lookAspect, direction);
		WorldLyricSnapshot.sectionsAlong(eye.x, eye.y, eye.z, direction[0], direction[1], direction[2], lookReach, into::add);
	}

	/** Copies the next few sections; one copied a moment ago is kept, so a block changed there shows within {@link #KEEP_LOOKS} looks. */
	private void copySections(ClientLevel level) {
		int budget = COPIES_PER_FRAME;
		while (nextCopy < planned.length && budget > 0) {
			long key = planned[nextCopy++];
			WorldLyricSnapshot.Section known = copied.get(key);
			if (known != null && lookNumber - known.look() < KEEP_LOOKS) {
				copying.put(key, known);
				continue;
			}
			copying.put(key, copy(level, key));
			budget--;
		}
	}

	private WorldLyricSnapshot.Section copy(ClientLevel level, long key) {
		int sectionY = WorldLyricSnapshot.keyY(key);
		int index = level.getSectionIndexFromSectionY(sectionY);
		LevelChunk chunk = index < 0 || index >= level.getSectionsCount() ? null
			: level.getChunkSource().getChunk(WorldLyricSnapshot.keyX(key), WorldLyricSnapshot.keyZ(key), ChunkStatus.FULL, false);
		LevelChunkSection section = chunk == null ? null : chunk.getSection(index);
		return new WorldLyricSnapshot.Section(section == null || section.hasOnlyAir() ? null : section.getStates().copy(), lookNumber);
	}

	/** Hands the copies to a background thread, which from now on is the only one to read them until the look has ended. */
	private void startLook(ClientLevel level) {
		copied = copying;
		WorldLyricSnapshot snapshot = new WorldLyricSnapshot(copied, level.getMinY(), level.getHeight());
		Vec3 eye = lookEye;
		work(Phase.TRACING, () -> look(snapshot, eye));
	}

	/** On the render thread: what the look found becomes the last whole look, with the light on the blocks it met, which only this thread may read. */
	private void takeLook(ClientLevel level) {
		for (int cell = 0; cell < looking.length; cell++) {
			if (!looking[cell]) {
				lookingLight[cell] = surfaceLight(level, lookingBlock[cell], lookingFace[cell]);
			}
		}
		System.arraycopy(looking, 0, open, 0, open.length);
		System.arraycopy(lookingDistance, 0, distance, 0, distance.length);
		System.arraycopy(lookingLight, 0, light, 0, light.length);
		looks++;
		yaw = lookYaw;
		pitch = lookPitch;
		halfFovY = lookHalfFovY;
		aspect = lookAspect;
		age = 0f;
	}

	/**
	 * On a background thread: follows every ray through the copied sections, and touches neither the live world nor the player. What it writes the
	 * render thread reads only after {@link #workDone}. Should anything still go wrong, this one look is dropped: the next is a quarter of a second away.
	 */
	private void look(WorldLyricSnapshot snapshot, Vec3 eye) {
		try {
			for (int row = 0; row < ROWS; row++) {
				for (int column = 0; column < COLUMNS; column++) {
					BlockHitResult hit = trace(snapshot, eye, screenX(column), screenY(row));
					int cell = row * COLUMNS + column;
					boolean miss = hit == null;
					looking[cell] = miss;
					lookingDistance[cell] = miss ? (float) lookReach : (float) hit.getLocation().distanceTo(eye);
					if (miss) {
						lookingLight[cell] = skyLight(skyColor, fogColor, direction[1]);
					} else {
						lookingBlock[cell] = hit.getBlockPos().asLong();
						lookingFace[cell] = hit.getDirection();
					}
				}
			}
		} catch (RuntimeException e) {
			workFailed = true;
		}
		workDone = true;
	}

	/** @return what the ray through this place of the screen meets, or null; its direction stays in {@link #direction} */
	private BlockHitResult trace(WorldLyricSnapshot snapshot, Vec3 eye, double x, double y) {
		WorldLyricView.direction(x, y, lookYaw, lookPitch, lookHalfFovY, lookAspect, direction);
		Vec3 end = eye.add(direction[0] * lookReach, direction[1] * lookReach, direction[2] * lookReach);
		return BlockGetter.traverseBlocks(eye, end, snapshot, (blocks, pos) -> hitIn(blocks, pos, eye, end), blocks -> null);
	}

	/**
	 * @return where a ray meets what fills a block cell, or null: the block's visual shape, or a fluid up to its own height. Water counts: the lyrics never
	 *         stand in the sea, only above it or where it lies far beyond them. A fluid's own shape is not asked for, since the game keeps those in a map
	 *         that only its main thread may touch.
	 */
	private static BlockHitResult hitIn(WorldLyricSnapshot blocks, BlockPos pos, Vec3 eye, Vec3 end) {
		BlockState state = blocks.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}
		BlockHitResult block = state.getVisualShape(blocks, pos, CollisionContext.empty()).clip(eye, end, pos);
		FluidState fluid = state.getFluidState();
		if (fluid.isEmpty()) {
			return block;
		}
		BlockHitResult surface = AABB.clip(List.of(new AABB(0.0, 0.0, 0.0, 1.0, fluid.getOwnHeight(), 1.0)), eye, end, pos);
		if (block == null || surface == null) {
			return block == null ? surface : block;
		}
		return eye.distanceToSqr(block.getLocation()) <= eye.distanceToSqr(surface.getLocation()) ? block : surface;
	}

	/** @return how bright a block face the ray met looks, from 0 to 1 (see {@link #surfaceLight(int, float, float)}); on the render thread */
	private float surfaceLight(ClientLevel level, long block, Direction face) {
		litPos.set(block);
		int color = level.getBlockState(litPos).getMapColor(level, litPos).col;
		float lit = level.getMaxLocalRawBrightness(litBeside.setWithOffset(litPos, face)) / 15f;
		float shade = switch (face) {
			case UP -> 1f;
			case DOWN -> DOWN_SHADE;
			case NORTH, SOUTH -> NORTH_SOUTH_SHADE;
			default -> EAST_WEST_SHADE;
		};
		return surfaceLight(color, shade, lit);
	}

	/** The game shades the faces of blocks by the way they face: the top full, the bottom, north and south, and east and west this much. */
	private static final float DOWN_SHADE = 0.5f;
	private static final float NORTH_SOUTH_SHADE = 0.8f;
	private static final float EAST_WEST_SHADE = 0.6f;
	/**
	 * A block's textures are on the whole this much darker than its map color (grass and leaves are tinted, their pixels mostly darker). Taken
	 * on the low side: a surface thought darker than it is gets light words with a shadow, which still read; one thought brighter would get
	 * deep words that do not.
	 */
	private static final float TEXTURE_SHADE = 0.75f;
	/** A face in no light at all keeps this much of its color (the night sky still shows it a little); the light falls off as its square. */
	private static final float LEAST_LIT = 0.08f;

	/**
	 * @param mapColor the block's map color
	 * @param shade    how much the game shades the face for the way it faces
	 * @param lit      the light on the face, from 0 to 1 (sky light with the time of day, or a torch)
	 * @return how bright the face looks, as relative luminance from 0 to 1: its map color, darkened for its texture, its facing and the light on it
	 */
	static float surfaceLight(int mapColor, float shade, float lit) {
		float factor = TEXTURE_SHADE * shade * (LEAST_LIT + (1f - LEAST_LIT) * lit * lit);
		int r = Math.round((mapColor >> 16 & 0xFF) * factor);
		int g = Math.round((mapColor >> 8 & 0xFF) * factor);
		int b = Math.round((mapColor & 0xFF) * factor);
		return lightness(r << 16 | g << 8 | b);
	}

	/** The sky turns from the fog's color at the horizon to its own color this many degrees above it. */
	private static final double SKY_BLEND_DEGREES = 35.0;

	/**
	 * @param sky the sky's color straight up
	 * @param fog the fog's color, which the sky has at the horizon
	 * @param up  how far up the direction looks: the sine of its height above the horizon
	 * @return how bright the sky is in that direction, as relative luminance: the game's sky is the fog's color low down, its own color higher up
	 */
	static float skyLight(int sky, int fog, double up) {
		double t = Math.clamp(Math.toDegrees(Math.asin(Math.clamp(up, -1.0, 1.0))) / SKY_BLEND_DEGREES, 0.0, 1.0);
		float blend = (float) (t * t * (3.0 - 2.0 * t));
		return lightness(LyricTones.mix(fog, sky, blend));
	}

	/** @return the relative luminance of a color, from 0 to 1 */
	static float lightness(int rgb) {
		return LyricTones.luminance(rgb);
	}

	/**
	 * How bright and how busy what lies behind a place of the screen is, from the last whole look: every cell the place touches, even partly.
	 *
	 * @param out receives the mean brightness (0 to 1), how much world surfaces there vary (their standard deviation), and the darkest and the
	 *            brightest cell
	 * @return whether any cell of the last look lies there
	 */
	boolean backdrop(double left, double right, double top, double bottom, float[] out) {
		return backdrop(open, light, left, right, top, bottom, out);
	}

	/**
	 * The same over given cells. The brightness is the mean of all of them, the sky's among them; how busy it is comes from the world's
	 * surfaces alone: the sky is one calm tone here, whatever lines, stars or clouds a sky style draws on it.
	 */
	static boolean backdrop(boolean[] open, float[] light, double left, double right, double top, double bottom, float[] out) {
		double halfCellX = 1.0 / COLUMNS;
		double halfCellY = 1.0 / ROWS;
		float sum = 0f;
		int count = 0;
		float darkest = 1f;
		float brightest = 0f;
		float surfaces = 0f;
		float squares = 0f;
		int solid = 0;
		for (int r = 0; r < ROWS; r++) {
			double y = screenY(r);
			if (y - halfCellY > top || y + halfCellY < bottom) {
				continue;
			}
			for (int c = 0; c < COLUMNS; c++) {
				double x = screenX(c);
				if (x + halfCellX < left || x - halfCellX > right) {
					continue;
				}
				int cell = r * COLUMNS + c;
				float each = light[cell];
				sum += each;
				count++;
				darkest = Math.min(darkest, each);
				brightest = Math.max(brightest, each);
				if (!open[cell]) {
					surfaces += each;
					squares += each * each;
					solid++;
				}
			}
		}
		if (count == 0) {
			return false;
		}
		out[0] = sum / count;
		float mean = solid == 0 ? 0f : surfaces / solid;
		out[1] = solid < 2 ? 0f : (float) Math.sqrt(Math.max(0f, squares / solid - mean * mean));
		out[2] = darkest;
		out[3] = brightest;
		return true;
	}

	/** @return how many whole looks have been made */
	int looks() {
		return looks;
	}

	/** @return how bright what each ray of the last look met is, from 0 to 1 (the sky where it met nothing) */
	float[] light() {
		return light;
	}

	/** @return how far each ray of the last look went, in blocks */
	float[] distance() {
		return distance;
	}

	/** A first look is waited for at most this long, in seconds; it takes a fraction of that. */
	private static final float FIRST_LOOK_SECONDS = 1f;

	/** @return whether the first look since the lyrics came on is still under way: a line then waits for it rather than be placed without it */
	boolean firstLookPending() {
		return age == Float.MAX_VALUE && sinceReset < FIRST_LOOK_SECONDS;
	}

	/** @return whether a whole look is recent enough to place by */
	boolean fresh() {
		return age <= STALE_SECONDS;
	}

	/** @return the open cells of the last look; the array is shared and may be marked taken by the caller on a copy only */
	boolean[] open() {
		return open;
	}

	float yaw() {
		return yaw;
	}

	float pitch() {
		return pitch;
	}

	double halfFovY() {
		return halfFovY;
	}

	double aspect() {
		return aspect;
	}

	/** @return the middle of a column of the grid, across the screen from -1 to 1 */
	public static double screenX(int column) {
		return -1.0 + (column + 0.5) * 2.0 / COLUMNS;
	}

	/** @return the middle of a row of the grid, up the screen from -1 (bottom) to 1; row 0 is the top */
	public static double screenY(int row) {
		return 1.0 - (row + 0.5) * 2.0 / ROWS;
	}

	/** @return the column of a place across the screen, or -1 off it */
	static int column(double x) {
		int column = (int) Math.floor((x + 1.0) / 2.0 * COLUMNS);
		return column >= 0 && column < COLUMNS ? column : -1;
	}

	/** @return the row of a place up the screen, or -1 off it */
	static int row(double y) {
		int row = (int) Math.floor((1.0 - y) / 2.0 * ROWS);
		return row >= 0 && row < ROWS ? row : -1;
	}

	/**
	 * Finds the largest open rectangle of cells, preferring one near the middle of the view: its area counts less the farther its middle lies
	 * from the screen's middle.
	 *
	 * @param open    the open cells, row by row from the top
	 * @param columns the grid's width
	 * @param rows    its height
	 * @param out     the rectangle found, as first column, first row, last column, last row (all inclusive)
	 * @return whether there is an open cell at all
	 */
	public static boolean largest(boolean[] open, int columns, int rows, int[] out) {
		int[] heights = new int[columns];
		float best = 0f;
		for (int row = 0; row < rows; row++) {
			for (int c = 0; c < columns; c++) {
				heights[c] = open[row * columns + c] ? heights[c] + 1 : 0;
			}
			// every rectangle whose bottom is this row: for each column, the widest run at least as tall as a height
			for (int c = 0; c < columns; c++) {
				int tall = heights[c];
				for (int height = 1; height <= tall; height++) {
					int left = c;
					while (left > 0 && heights[left - 1] >= height) {
						left--;
					}
					int right = c;
					while (right + 1 < columns && heights[right + 1] >= height) {
						right++;
					}
					// counted once, from its leftmost column
					if (left != c) {
						continue;
					}
					int width = right - left + 1;
					float middleX = (left + right + 1) / (float) columns - 1f;
					float middleY = 1f - (2 * row + 1 - height + 1) / (float) rows;
					float off = (float) Math.sqrt(middleX * middleX + middleY * middleY);
					float score = width * height * (1f - MIDDLE_WEIGHT * Math.min(1f, off / 1.4142f));
					if (score > best) {
						best = score;
						out[0] = left;
						out[1] = row - height + 1;
						out[2] = right;
						out[3] = row;
					}
				}
			}
		}
		return best > 0f;
	}
}
