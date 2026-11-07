package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.render.EchoFeedback;
import dev.tim1337.lumio.render.EchoPatches;
import dev.tim1337.lumio.render.EchoShape;
import dev.tim1337.lumio.render.OutlineItem;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.Random;

/**
 * Radiance's Echo between two frames: when the item moved on the screen, by a swing, a turn of the camera, a step, the hand's sway or the change of
 * items, it leaves thin copies of its outline at the places it passed, one about every {@link #COPY_SPACING_PIXELS} of the tip's travel, placed
 * between the two frames so a fast swing leaves no gaps; each copy with a brightness of its own. The points of the edge that move the most also
 * draw a streak from where they were to where they are, which makes the swept shape's outer edge a little brighter than the lines inside it. The
 * after-glow ({@link EchoFeedback}) keeps the lines and lets them fade. The places are kept as the hand is drawn on the screen, only while the look
 * is shown; a hand that stands still leaves nothing.
 */
final class RadianceEcho {
	/** A point slower than this, in pixels of a 1080 line screen a second, leaves no streak: the hand stands still. */
	private static final float LEAST_STREAK_SPEED = 15f;
	/** A point this fast, in pixels of a 1080 line screen a second, leaves a streak as bright as the rim; a slower one a fainter one. */
	private static final float FULL_STREAK_SPEED = 120f;
	/** A frame longer than this has no trail: the item may have been anywhere. */
	private static final long LONGEST_GAP_NANOS = 100_000_000L;
	/** One copy of the outline per this much travel of the tip, in pixels of a 1080 line screen. */
	static final float COPY_SPACING_PIXELS = 14f;
	/** The brightness of a copy is chosen in this range, so the lines are not all alike. */
	private static final float DIMMEST_COPY = 0.6f;
	/** The copies inside the swept shape are this much dimmer than its outer edge, the streaks, so the edge reads brightest. */
	private static final float INNER_LIGHT = 0.8f;
	/** Only the points that move at least this share of the farthest, by {@link #share}, draw a streak: the outer edge of the swept shape. */
	private static final float OUTER_EDGE_SHARE = 0.85f;
	/** A point that jumped farther than this in one frame (pixels of a 1080 line screen) draws no streak: a straight line that long would cut across the curve it took. */
	private static final float LONGEST_STREAK_PIXELS = 80f;
	/** The most copies one frame leaves, so a jump of the hand does not fill the line budget at once. */
	private static final int MOST_COPIES = 24;
	/** Two ends closer than this (squared, in blocks of camera space) are one corner of the outline. */
	private static final float SAME_CORNER_SQUARED = 1e-8f;
	/** Poses closer than this in every entry are the same: the hand stands still. */
	private static final float STILL = 1e-6f;
	/** A hand standing still works out its item's bounds again this often, in frames. */
	private static final int STILL_BOUNDS_FRAMES = 20;
	/** How far a model may reach out of its block, in blocks, for the box of an item without a flat outline. */
	private static final float MODEL_SPILL = 0.5f;

	private final PoseHistory right = new PoseHistory();
	private final PoseHistory left = new PoseHistory();
	private final long[] lastFrame = new long[2];
	/** Per side: how far the tip has travelled since the last copy, in pixels of a 1080 line screen. */
	private final float[] carried = new float[2];
	private final Random random = new Random();
	private final EchoShape shapeNow = new EchoShape();
	private final EchoShape shapeThen = new EchoShape();
	private final EchoShape shapeAt = new EchoShape();
	private final PoseStack between = new PoseStack();
	private final float[] tipAt = new float[3];
	private final float[] lineAt = new float[6];
	private final float[] patchPoints = new float[EchoPatches.MOST_POINTS * 2];
	/** Per hand: the item's screen bounds (left, bottom, right, top from -1 to 1), whether they are known, and the frames it stood still. */
	private final float[][] bounds = new float[2][4];
	private final boolean[] boundsKnown = new boolean[2];
	private final int[] stillFrames = new int[2];
	private final org.joml.Vector3f corner3 = new org.joml.Vector3f();
	/** A copy's segments, both ends each, and whether each meets another; grown when an item has more lines. */
	private float[] corners = new float[64 * 6];
	private boolean[] met = new boolean[64];
	/** The pairs of segments that meet, found at the first copy of a frame; -1 when not found yet this frame. */
	private int[] pairs = new int[256];
	private int pairCount = -1;
	private int pairsOf;
	private final PoseStack copy = new PoseStack();
	private final Matrix4f then = new Matrix4f();
	private final float[] tipNow = new float[3];
	private final float[] tipThen = new float[3];
	private final float[] screenA = new float[2];
	private final float[] screenB = new float[2];
	private final float[] lineNow = new float[6];
	private final float[] lineThen = new float[6];
	private final float[] places = new float[MOST_COPIES];
	/** Per line of the shape: how far its first point moved this frame, and both ends then and now on the screen; grown when an item has more lines. */
	private float[] travel = new float[64];
	private float[] ends = new float[64 * 8];

	/**
	 * @return how bright the streak of a point at so many pixels of a 1080 line screen a second is: none below the least speed, full from the full
	 * speed on; by the speed, not the length, so the trail is as bright at every frame rate
	 */
	static float streakLight(float pixelsPerSecond) {
		if (pixelsPerSecond < LEAST_STREAK_SPEED) {
			return 0f;
		}
		return Math.min(1f, (pixelsPerSecond - LEAST_STREAK_SPEED) / (FULL_STREAK_SPEED - LEAST_STREAK_SPEED));
	}

	/**
	 * @return how many times longer the trail lasts than at the Echo length setting 50 %, by the setting (0 to 1): half as long at 0, twice as long
	 * at 100 %
	 */
	static float lengthFactor(float echoLength) {
		return (float) Math.pow(2.0, 2.0 * Math.clamp(echoLength, 0f, 1f) - 1.0);
	}

	/**
	 * Where along this frame's travel the copies of the outline go: one each time the travel since the last copy reaches the spacing.
	 *
	 * @param carried how far the tip travelled since the last copy, before this frame
	 * @param travel  how far it travels in this frame
	 * @param out     the places, as shares of this frame's travel from 0 (where it was) to 1 (where it is)
	 * @return how many copies, at most as many as {@code out} holds
	 */
	static int copyPlaces(float carried, float travel, float spacing, float[] out) {
		int count = 0;
		float next = spacing - carried;
		while (next <= travel && count < out.length && travel > 0f) {
			out[count++] = next / travel;
			next += spacing;
		}
		return count;
	}

	/** @return how far the tip has travelled since the last copy after a frame that left {@link #copyPlaces} copies */
	static float carriedAfter(float carried, float travel, float spacing) {
		float total = carried + travel;
		return total - (float) Math.floor(total / spacing) * spacing;
	}

	/**
	 * Notes where the item is in this frame (the glow's patches are anchored to it); when it moved since the frame before, it leaves copies of its
	 * outline and streaks from there to here, unless the trail is off.
	 *
	 * @param trail whether it leaves a trail: off at the Echo length Off, where only the glow around the item stays
	 */
	void submit(OutlineItem item, ItemDisplayContext context, PoseStack pose, boolean trail) {
		int side = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 0 : 1;
		PoseHistory history = side == 0 ? right : left;
		long now = System.nanoTime();
		// places as the hand is drawn: a swing, the sway after a turn of the camera and the view bobbing all move it on the screen
		history.record(now, pose.last().pose());
		Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
		Matrix4f current = pose.last().pose();
		if (EchoFeedback.onScreen(modelView, current.m30(), current.m31(), current.m32(), screenA)) {
			EchoFeedback.anchor(screenA[0], screenA[1]);
		}
		long before = lastFrame[side];
		lastFrame[side] = now;
		if (before == 0L || now - before > LONGEST_GAP_NANOS || !history.between(before, then)) {
			carried[side] = 0f;
			shape(item, pose, shapeNow, tipNow);
			boundsFrom(shapeNow, current, modelView, side);
			reportBounds(side);
			return;
		}
		if (then.equals(pose.last().pose(), STILL)) {
			// the hand has not moved since the frame before: no trail, and the shapes need not be made (but once, for the glow's patches)
			carried[side] = 0f;
			// the bounds again now and then, for another item that came into the hand without moving it
			if (!EchoPatches.hasOutline() || ++stillFrames[side] % STILL_BOUNDS_FRAMES == 0) {
				shape(item, pose, shapeNow, tipNow);
				notePatchOutline(shapeNow, modelView);
				boundsFrom(shapeNow, current, modelView, side);
			}
			reportBounds(side);
			return;
		}
		pairCount = -1;
		copy.last().pose().set(then);
		float seconds = Math.max(1e-4f, (now - before) / 1e9f);
		boolean tipNowFound = shape(item, pose, shapeNow, tipNow);
		boolean tipThenFound = shape(item, copy, shapeThen, tipThen);
		notePatchOutline(shapeNow, modelView);
		boundsFrom(shapeNow, current, modelView, side);
		reportBounds(side);
		int count = Math.min(shapeNow.lineCount(), shapeThen.lineCount());
		if (count == 0) {
			return;
		}
		float tipTravel = 0f;
		if (tipNowFound && tipThenFound && EchoFeedback.onScreen(modelView, tipNow[0], tipNow[1], tipNow[2], screenA)
			&& EchoFeedback.onScreen(modelView, tipThen[0], tipThen[1], tipThen[2], screenB)) {
			// the tip's motion: the sheet is smeared along it, and the copies are spaced by it
			EchoFeedback.motionBetween(screenB, screenA);
			tipTravel = EchoFeedback.pixelsBetween(screenA, screenB);
		}
		if (travel.length < count) {
			travel = new float[count * 2];
			ends = new float[count * 2 * 8];
		}
		// both ends of every line, then and now, on the screen; and how far each line's first point moved, and the farthest
		float farthest = 0f;
		for (int i = 0; i < count; i++) {
			shapeNow.line(i, lineNow);
			shapeThen.line(i, lineThen);
			travel[i] = -1f;
			int at = i * 8;
			if (EchoFeedback.onScreen(modelView, lineThen[0], lineThen[1], lineThen[2], screenB) && EchoFeedback.onScreen(modelView, lineNow[0], lineNow[1], lineNow[2], screenA)) {
				travel[i] = EchoFeedback.pixelsBetween(screenA, screenB);
				farthest = Math.max(farthest, travel[i]);
				ends[at] = screenB[0];
				ends[at + 1] = screenB[1];
				ends[at + 2] = screenA[0];
				ends[at + 3] = screenA[1];
				if (EchoFeedback.onScreen(modelView, lineThen[3], lineThen[4], lineThen[5], screenB) && EchoFeedback.onScreen(modelView, lineNow[3], lineNow[4], lineNow[5], screenA)) {
					ends[at + 4] = screenB[0];
					ends[at + 5] = screenB[1];
					ends[at + 6] = screenA[0];
					ends[at + 7] = screenA[1];
				} else {
					travel[i] = -1f;
				}
			}
		}
		if (tipTravel <= 0f) {
			// an item without a blade (a block, food): its farthest point stands for the tip
			tipTravel = farthest;
		}
		boolean moving = trail && streakLight(tipTravel / seconds) > 0f;
		EchoFeedback.moving(moving);
		if (!moving) {
			carried[side] = 0f;
			return;
		}
		// the copies of the outline, spaced along the tip's travel, as many as the lines allow
		int copies = Math.min(copyPlaces(carried[side], tipTravel, COPY_SPACING_PIXELS, places), Math.max(1, EchoFeedback.MOST_LINES / (2 * count)));
		carried[side] = carriedAfter(carried[side], tipTravel, COPY_SPACING_PIXELS);
		EchoFeedback.copies(copies);
		// each copy is the outline at the item's place at its own moment, the pose taken between the two frames, so the copies follow the swing's arc
		// instead of sliding along straight lines
		for (int c = 0; c < copies; c++) {
			float light = INNER_LIGHT * (DIMMEST_COPY + (1f - DIMMEST_COPY) * random.nextFloat());
			then.lerp(pose.last().pose(), places[c], between.last().pose());
			shape(item, between, shapeAt, tipAt);
			addSmoothed(shapeAt, modelView, light);
		}
		// the outer edge of the swept shape: streaks of the points that move the most
		for (int i = 0; i < count; i++) {
			float share = travel[i] < 0f ? 0f : share(travel[i], farthest);
			if (share >= OUTER_EDGE_SHARE && travel[i] <= LONGEST_STREAK_PIXELS) {
				int at = i * 8;
				EchoFeedback.addLine(ends[at], ends[at + 1], ends[at + 2], ends[at + 3], streakLight(travel[i] / seconds));
			}
		}
	}

	/**
	 * Works out where the item is on the screen: the ends of its outline for a flat item, else a box around its model, generous enough for models that
	 * reach out of their block. Kept per hand, as left, bottom, right, top from -1 to 1.
	 */
	private void boundsFrom(EchoShape shape, Matrix4f pose, Matrix4f modelView, int side) {
		float[] b = bounds[side];
		b[0] = Float.MAX_VALUE;
		b[1] = Float.MAX_VALUE;
		b[2] = -Float.MAX_VALUE;
		b[3] = -Float.MAX_VALUE;
		int count = shape.lineCount();
		for (int i = 0; i < count; i++) {
			shape.line(i, lineAt);
			include(b, modelView, lineAt[0], lineAt[1], lineAt[2]);
			include(b, modelView, lineAt[3], lineAt[4], lineAt[5]);
		}
		if (count == 0) {
			for (int corner = 0; corner < 8; corner++) {
				float x = (corner & 1) == 0 ? -MODEL_SPILL : 1f + MODEL_SPILL;
				float y = (corner & 2) == 0 ? -MODEL_SPILL : 1f + MODEL_SPILL;
				float z = (corner & 4) == 0 ? -MODEL_SPILL : 1f + MODEL_SPILL;
				pose.transformPosition(x, y, z, corner3);
				include(b, modelView, corner3.x, corner3.y, corner3.z);
			}
		}
		boundsKnown[side] = b[0] <= b[2];
	}

	private void include(float[] b, Matrix4f modelView, float x, float y, float z) {
		if (!EchoFeedback.onScreen(modelView, x, y, z, screenB)) {
			// behind the camera: no bounds can be told
			b[0] = -1f;
			b[1] = -1f;
			b[2] = 1f;
			b[3] = 1f;
			return;
		}
		b[0] = Math.min(b[0], screenB[0]);
		b[1] = Math.min(b[1], screenB[1]);
		b[2] = Math.max(b[2], screenB[0]);
		b[3] = Math.max(b[3], screenB[1]);
	}

	/** Tells the after-glow where this hand's item is, or that it is not known (then the glow is made over the whole screen). */
	private void reportBounds(int side) {
		float[] b = bounds[side];
		if (boundsKnown[side]) {
			EchoFeedback.itemBounds(b[0], b[1], b[2], b[3]);
		} else {
			EchoFeedback.itemBounds(-1f, -1f, 1f, 1f);
		}
	}

	/** Gives the glow's patches points along the item's outline now, relative to its anchor, to keep about a third of it glowing. */
	private void notePatchOutline(EchoShape shape, Matrix4f modelView) {
		int count = shape.lineCount();
		int stride = Math.max(1, (count + EchoPatches.MOST_POINTS - 1) / EchoPatches.MOST_POINTS);
		int noted = 0;
		for (int i = 0; i < count && noted < EchoPatches.MOST_POINTS; i += stride) {
			shape.line(i, lineAt);
			// only what is seen: the part of a large sword beyond the screen's edge does not count
			if (EchoFeedback.onScreen(modelView, (lineAt[0] + lineAt[3]) * 0.5f, (lineAt[1] + lineAt[4]) * 0.5f, (lineAt[2] + lineAt[5]) * 0.5f, screenB)
				&& Math.abs(screenB[0]) <= 1f && Math.abs(screenB[1]) <= 1f) {
				EchoFeedback.relativeToAnchor(screenB[0], screenB[1], patchPoints, 2 * noted++);
			}
		}
		if (noted > 0) {
			EchoPatches.outline(patchPoints, noted);
		}
	}

	/**
	 * Adds a copy's outline as lines between the middles of segments that meet at a corner: the edge of a pixel art item is a staircase of unit
	 * steps, and a copy of it would show as a zigzag; through the middles a staircase becomes its straight slope and a corner is only rounded a little.
	 * A segment that meets none (an edge on the other face) is drawn as it is.
	 */
	private void addSmoothed(EchoShape shape, Matrix4f modelView, float light) {
		int count = shape.lineCount();
		if (corners.length < count * 6) {
			corners = new float[count * 12];
			met = new boolean[count * 2];
		}
		for (int i = 0; i < count; i++) {
			shape.line(i, lineAt);
			System.arraycopy(lineAt, 0, corners, i * 6, 6);
		}
		// which segments meet is the same for every copy of a frame (the same item, its edge in the same order): found once, at the first copy
		if (pairCount < 0 || pairsOf != count) {
			findPairs(count);
		}
		for (int p = 0; p < pairCount; p++) {
			int i = pairs[2 * p];
			int j = pairs[2 * p + 1];
			addBetween(modelView, middle(i, 0), middle(i, 1), middle(i, 2), middle(j, 0), middle(j, 1), middle(j, 2), light);
		}
		for (int i = 0; i < count; i++) {
			if (!met[i]) {
				int at = i * 6;
				addBetween(modelView, corners[at], corners[at + 1], corners[at + 2], corners[at + 3], corners[at + 4], corners[at + 5], light);
			}
		}
	}

	/** Finds the segments of the copy in {@link #corners} that meet at a corner, and marks those that meet none. */
	private void findPairs(int count) {
		pairCount = 0;
		pairsOf = count;
		for (int i = 0; i < count; i++) {
			met[i] = false;
		}
		for (int i = 0; i < count; i++) {
			for (int j = i + 1; j < count; j++) {
				if (meet(i, j)) {
					met[i] = true;
					met[j] = true;
					if (2 * pairCount + 1 >= pairs.length) {
						pairs = java.util.Arrays.copyOf(pairs, pairs.length * 2);
					}
					pairs[2 * pairCount] = i;
					pairs[2 * pairCount + 1] = j;
					pairCount++;
				}
			}
		}
	}

	/** @return whether two segments share an end */
	private boolean meet(int i, int j) {
		int a = i * 6;
		int b = j * 6;
		return same(a, b) || same(a, b + 3) || same(a + 3, b) || same(a + 3, b + 3);
	}

	private boolean same(int a, int b) {
		float dx = corners[a] - corners[b];
		float dy = corners[a + 1] - corners[b + 1];
		float dz = corners[a + 2] - corners[b + 2];
		return dx * dx + dy * dy + dz * dz < SAME_CORNER_SQUARED;
	}

	private float middle(int segment, int axis) {
		int at = segment * 6 + axis;
		return (corners[at] + corners[at + 3]) * 0.5f;
	}

	private void addBetween(Matrix4f modelView, float ax, float ay, float az, float bx, float by, float bz, float light) {
		if (EchoFeedback.onScreen(modelView, ax, ay, az, screenA) && EchoFeedback.onScreen(modelView, bx, by, bz, screenB)) {
			EchoFeedback.addLine(screenA[0], screenA[1], screenB[0], screenB[1], light);
		}
	}

	private static float lerp(float from, float to, float share) {
		return from + (to - from) * share;
	}

	/** @return how much of its light a streak keeps, by how far its point moved against the farthest: the outer ones keep it, the inner ones little */
	static float share(float pixels, float farthest) {
		if (farthest <= 0f) {
			return 0f;
		}
		float part = pixels / farthest;
		return part * part * part;
	}

	/** Puts the item's edge at the pose into the shape. @return whether it is flat and has a tip, which is put into {@code tip} */
	private static boolean shape(OutlineItem item, PoseStack pose, EchoShape shape, float[] tip) {
		shape.clear();
		shape.begin(1f, 1f);
		boolean flat = false;
		for (int layer = 0; layer < item.lumio$layerCount(); layer++) {
			flat |= item.lumio$layer(layer).lumio$echoShape(pose, shape);
		}
		boolean found = flat && shape.tipOfOpen(tip) != null;
		shape.end();
		return found;
	}
}
