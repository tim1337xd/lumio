package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.util.Daylight;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Auto placement of the world lyrics: for every new line it looks at a few spots in the view and picks a good one, like a cheap version of the
 * cinematic camera's scout. First it decides between the sky and around the player by the surroundings (open sky ahead, or a cave, a building,
 * trees, a tight space), then it judges the two spots to the left and the right of the view (the band around the crosshair is never
 * used): inside blocks or hidden behind terrain, or on a line that is still visible drops a spot; a calm background with good contrast to the
 * text, not straight ahead where the player walks, and the other side than the last line win.
 * <p>
 * The work is spread over the frames before the line starts (its time is known), a few rays per frame, so there is no frame time spike. When the
 * player turned or moved meanwhile, or the line comes without warning (a seek), it is done at once. Nothing is allocated per frame.
 */
final class WorldLyricScout {
	/** The scouting starts this long before the line, and judges this many spots (or sky rays) per frame. */
	static final long LEAD_MS = 700L;
	private static final int PER_FRAME = 2;
	/** The sky counts as open when the eye sees the sky and enough of these rays, this high and this long, reach their end. */
	private static final float[] SKY_YAWS = {-20f, 0f, 20f};
	private static final float SKY_RAY_ELEVATION = 27f;
	private static final double SKY_RAY_LENGTH = 45.0;
	private static final int SKY_RAYS_NEEDED = 2;
	/** A scouted result is used only while the view stays this close to where it was scouted, in degrees and blocks. */
	private static final float SAME_VIEW_DEGREES = 20f;
	private static final double SAME_PLACE_BLOCKS = 2.0;
	/** The spread of the lines around the player in Auto. */
	static final float SPREAD = 0.45f;
	/** Weights of the judgement: contrast with the background, the walking direction, the same kind of spot again, and less room than wanted. */
	private static final float CONTRAST_WEIGHT = 1.2f;
	private static final float WALK_WEIGHT = 0.8f;
	private static final float REPEAT_WEIGHT = 0.6f;
	private static final float ROOM_WEIGHT = 0.5f;
	/** The player walks when moving faster than this per tick; the spots within this angle of where they go are in the way. */
	private static final double WALKING = 0.04;
	private static final float WALK_DEGREES = 30f;
	/** The background is looked at this far behind the line. */
	private static final double BEHIND_BLOCKS = 6.0;
	private static final float FULL_LIGHT = 15f;
	/** The visible lines a spot must keep clear of: their direction and half size in degrees. */
	static final int MOST_AVOIDED = 4;

	/** The same view of the blocks as the placement, so a spot judged open is one the placer can use. */
	private final WorldLyricSpace space = new WorldLyricSpace(WorldLyricBlocks.client());
	private final float[] view = new float[2];
	private final float[] angles = new float[2];
	private final float[] scores = new float[WorldLyricPlacer.KINDS];
	private final float[] avoidYaw = new float[MOST_AVOIDED];
	private final float[] avoidElevation = new float[MOST_AVOIDED];
	private final float[] avoidHalfWidth = new float[MOST_AVOIDED];
	private final float[] avoidHalfHeight = new float[MOST_AVOIDED];
	private int avoided;
	/** What is scouted: the line (by its lyrics, index and start), from where, and how far it got. */
	private List<LyricLine> forLines;
	private int forIndex = -1;
	private long forStart;
	private Vec3 eye = Vec3.ZERO;
	private float yaw;
	private float elevation;
	private float halfWidth;
	private float halfHeight;
	private int lastKind;
	private int textColor;
	private int stage;
	private int skyRaysOpen;
	private boolean seesSky;

	/** Starts scouting for a line from the view now; {@link #avoid} the visible lines first. */
	void begin(List<LyricLine> lines, int index, long start, Minecraft client, Vec3 eye, float viewYaw, float viewPitch, float halfWidth, float halfHeight,
			   int lastKind, int textColor) {
		forLines = lines;
		forIndex = index;
		forStart = start;
		this.eye = eye;
		yaw = viewYaw;
		elevation = -viewPitch;
		this.halfWidth = halfWidth;
		this.halfHeight = halfHeight;
		this.lastKind = lastKind;
		this.textColor = textColor;
		stage = 0;
		space.resetBudget();
		skyRaysOpen = 0;
		seesSky = client.level.canSeeSky(BlockPos.containing(eye));
		WorldLyricPlacer.halfView(client, view);
		for (int i = 0; i < scores.length; i++) {
			scores[i] = Float.NEGATIVE_INFINITY;
		}
	}

	/** Forgets the lines to keep clear of, before {@link #avoid} adds the visible ones. */
	void clearAvoided() {
		avoided = 0;
	}

	/** A visible line a spot must keep clear of, as seen from the eye. */
	void avoid(float lineYaw, float lineElevation, float lineHalfWidth, float lineHalfHeight) {
		if (avoided < MOST_AVOIDED) {
			avoidYaw[avoided] = lineYaw;
			avoidElevation[avoided] = lineElevation;
			avoidHalfWidth[avoided] = lineHalfWidth;
			avoidHalfHeight[avoided] = lineHalfHeight;
			avoided++;
		}
	}

	/** @return whether scouting is under way or done for this line */
	boolean scouting(List<LyricLine> lines, int index, long start) {
		return forLines == lines && forIndex == index && forStart == start;
	}

	/** @return whether the result is for this line and still fits the view: the player did not turn or move much since */
	boolean fits(List<LyricLine> lines, int index, long start, Vec3 eyeNow, float viewYaw) {
		return scouting(lines, index, start) && Math.abs(Mth.wrapDegrees(viewYaw - yaw)) < SAME_VIEW_DEGREES
			&& eyeNow.distanceToSqr(eye) < SAME_PLACE_BLOCKS * SAME_PLACE_BLOCKS;
	}

	/** Does a frame's share of the work. */
	void step(Minecraft client) {
		for (int i = 0; i < PER_FRAME && !done(); i++) {
			next(client);
		}
	}

	/** Does all that is left at once, for a line that came without warning. */
	void finish(Minecraft client) {
		while (!done()) {
			next(client);
		}
	}

	private boolean done() {
		return stage >= SKY_YAWS.length + WorldLyricPlacer.KINDS || stage >= SKY_YAWS.length && sky();
	}

	private void next(Minecraft client) {
		if (stage < SKY_YAWS.length) {
			// without the sky above the eye (a cave, a building, under trees) the line goes around the player, no need to look further up
			if (seesSky && space.ray(eye.x, eye.y, eye.z, yaw + SKY_YAWS[stage], SKY_RAY_ELEVATION, SKY_RAY_LENGTH, WorldLyricPlacer.obstacleGap()) >= SKY_RAY_LENGTH) {
				skyRaysOpen++;
			}
		} else {
			int kind = stage - SKY_YAWS.length;
			scores[kind] = judge(client, kind);
		}
		stage++;
	}

	/** @return whether the line goes into the sky: the sky ahead is open */
	boolean sky() {
		return seesSky && skyRaysOpen >= SKY_RAYS_NEEDED;
	}

	/** @return the best kind of spot around the player, or -1 when none is good */
	int bestKind() {
		int best = -1;
		for (int kind = 0; kind < scores.length; kind++) {
			if (scores[kind] > Float.NEGATIVE_INFINITY && (best < 0 || scores[kind] > scores[best])) {
				best = kind;
			}
		}
		return best;
	}

	/** @return how good a kind of spot around the player is, higher is better; negative infinity when it is ruled out */
	private float judge(Minecraft client, int kind) {
		if (!WorldLyricPlacer.nearAngles(kind, yaw, elevation, view[0], view[1], halfWidth, halfHeight, SPREAD, angles)) {
			return Float.NEGATIVE_INFINITY;
		}
		float spotYaw = angles[0];
		float spotElevation = angles[1];
		for (int i = 0; i < avoided; i++) {
			if (Math.abs(Mth.wrapDegrees(spotYaw - avoidYaw[i])) < halfWidth + avoidHalfWidth[i]
				&& Math.abs(spotElevation - avoidElevation[i]) < halfHeight + avoidHalfHeight[i]) {
				return Float.NEGATIVE_INFINITY;
			}
		}
		double wanted = WorldLyricPlacer.nearDistance(1f);
		double room = space.openDistance(eye.x, eye.y, eye.z, spotYaw, spotElevation, halfWidth, halfHeight, wanted, WorldLyricPlacer.obstacleGap());
		if (room < WorldLyricPlacer.nearClosest()) {
			// inside blocks or hidden behind them
			return Float.NEGATIVE_INFINITY;
		}
		float score = -ROOM_WEIGHT * (float) (1.0 - Math.min(1.0, room / wanted));
		score -= CONTRAST_WEIGHT * (1f - contrast(client, spotYaw, spotElevation, room));
		var motion = client.player.getDeltaMovement();
		if (motion.horizontalDistanceSqr() > WALKING * WALKING) {
			float walkYaw = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));
			if (Math.abs(Mth.wrapDegrees(spotYaw - walkYaw)) < WALK_DEGREES + halfWidth) {
				score -= WALK_WEIGHT;
			}
		}
		if (kind == lastKind) {
			score -= REPEAT_WEIGHT;
		}
		return score;
	}

	/**
	 * @return how well the text stands out from what is behind the spot, from 0 to 1: the brightness of the block a little further on (its color,
	 *         lit by the sky and torches) or of the sky, against the text's
	 */
	private float contrast(Minecraft client, float spotYaw, float spotElevation, double room) {
		Vec3 direction = WorldLyricPlacer.direction(spotYaw, spotElevation);
		double length = room + BEHIND_BLOCKS;
		Vec3 end = eye.add(direction.x * length, direction.y * length, direction.z * length);
		BlockHitResult hit = client.level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, client.player));
		float day = Daylight.of(client.level);
		float background;
		if (hit.getType() == HitResult.Type.MISS) {
			background = day;
		} else {
			BlockPos pos = hit.getBlockPos();
			int color = client.level.getBlockState(pos).getMapColor(client.level, pos).col | 0xFF000000;
			BlockPos lit = pos.relative(hit.getDirection());
			float light = Math.max(client.level.getBrightness(LightLayer.SKY, lit) / FULL_LIGHT * day, client.level.getBrightness(LightLayer.BLOCK, lit) / FULL_LIGHT);
			background = Ui.luminance(color) * light;
		}
		return Math.min(1f, Math.abs(Ui.luminance(textColor) - background) * 1.5f);
	}
}
