package dev.tim1337.lumio.api.experimental.waypoint;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Vector3fc;

/**
 * How a waypoint looks, shared by every kind of waypoint so they all look alike: a soft beam of light standing on the block, a filled box with a
 * crisp outline around the block, a ring on the ground, a dotted line to the next point, and a label pill with its number, name and distance. The world parts are quads in one color with their own
 * opacity, for the game's translucent quads (or, through walls, its see-through quads); the label is drawn with the HUD at the place's screen
 * position.
 */
public final class WaypointLook {
	/** How the label of a point is drawn. */
	public enum State {
		/** The next point of a route: bright, with a ring in its color. */
		NEXT,
		/** A point that shows. */
		SHOWN,
		/** A point of a route that comes later: dim. */
		LATER,
		/** A point of a route already reached: dim, with "done" for its distance. */
		DONE
	}

	/** How high the beam reaches above the block, in blocks. */
	public static final float BEAM_HEIGHT = 80f;
	/** The beam's bright core and its soft halo: half their widths, in blocks. */
	private static final float CORE_HALF_WIDTH = 0.06f;
	private static final float HALO_HALF_WIDTH = 0.32f;
	private static final float CORE_OPACITY = 0.8f;
	private static final float HALO_OPACITY = 0.28f;
	/** How far the box stands out of the block, in blocks. */
	private static final float BOX_GROW = 0.004f;
	/** The share of the way to the camera the box and its lines are pulled by: the same place on the screen, in front of the block. */
	private static final float TOWARDS_CAMERA = 0.004f;
	/** The ground ring: how wide its band is, how many pieces its circle has and how far above the ground it lies, in blocks. */
	private static final float RING_WIDTH = 0.07f;
	private static final int RING_PIECES = 40;
	private static final float RING_LIFT = 0.02f;
	private static final float[] RING_COS = new float[RING_PIECES + 1];
	private static final float[] RING_SIN = new float[RING_PIECES + 1];

	static {
		for (int i = 0; i <= RING_PIECES; i++) {
			RING_COS[i] = (float) Math.cos(Math.PI * 2 * i / RING_PIECES);
			RING_SIN[i] = (float) Math.sin(Math.PI * 2 * i / RING_PIECES);
		}
	}
	/** The dotted line: the space between dots and half a dot's size, in blocks. */
	private static final float DOT_STEP = 0.7f;
	private static final float DOT_HALF = 0.05f;
	private static final int MOST_DOTS = 400;
	/** The game's full light, so the quads are as bright where the vertex format has light as where it has none. */
	private static final int FULL_LIGHT = 0xF000F0;
	/** Labels shrink from this distance on, in blocks, down to their least size at the far one. */
	private static final double FULL_SIZE_BLOCKS = 8.0;
	private static final double LEAST_SIZE_BLOCKS = 128.0;
	private static final float LEAST_SIZE = 0.65f;
	/** The label's sizes, in its GUI pixels. */
	private static final int LABEL_HEIGHT = 14;
	private static final int LABEL_PAD = 4;
	private static final int NUMBER = 10;
	private static final float DIM = 0.5f;
	/** The plain label: its height and padding, the gap before the distance, and its soft shadow (a dark glow and a drop under the text). */
	private static final int PLAIN_HEIGHT = 14;
	private static final int PLAIN_PAD = 3;
	private static final int PLAIN_GAP = 5;
	private static final float PLAIN_SHADOW_SPREAD = 5f;
	private static final int PLAIN_SHADOW = 0x55000000;
	private static final float PLAIN_DROP = 0.75f;
	private static final int PLAIN_DROP_COLOR = 0x8C000000;
	/** The icon label's tile: its least side, in GUI pixels, and how far the icon stands in from its edge, as a share of the side. */
	private static final int ICON_LEAST = 8;
	private static final float ICON_INSET = 0.18f;

	private WaypointLook() {
	}

	/**
	 * Adds a beam standing on a block: a bright core and a soft halo, turned to the camera, fading towards the top.
	 *
	 * @param x       the middle of the block, relative to the camera
	 * @param y       the bottom of the block, relative to the camera
	 * @param z       the middle of the block, relative to the camera
	 * @param opacity the beam's opacity, from 0 to 1
	 */
	public static void beam(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, int rgb, float opacity) {
		beam(pose, consumer, x, y, z, rgb, opacity, BEAM_HEIGHT);
	}

	/** Adds a beam as {@link #beam(PoseStack.Pose, VertexConsumer, float, float, float, int, float)}, reaching {@code height} blocks above the block. */
	public static void beam(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, int rgb, float opacity, float height) {
		// across the beam: level, at a right angle to the way from the camera
		float length = (float) Math.sqrt(x * x + z * z);
		float sideX = length < 1e-4f ? 1f : -z / length;
		float sideZ = length < 1e-4f ? 0f : x / length;
		float top = y + height;
		int core = argb(rgb, CORE_OPACITY * opacity);
		int halo = argb(rgb, HALO_OPACITY * opacity);
		int clear = argb(rgb, 0f);
		ribbon(pose, consumer, x, y, z, top, sideX, sideZ, HALO_HALF_WIDTH, halo, clear);
		ribbon(pose, consumer, x, y, z, top, sideX, sideZ, CORE_HALF_WIDTH, core, argb(rgb, CORE_OPACITY * opacity * 0.3f));
	}

	/** Two quads from the middle out to each side: bright in the middle and at the bottom, clear at the edges and the top. */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float top, float sideX, float sideZ, float half,
		int middle, int edge) {
		int clearTop = middle & 0x00FFFFFF;
		for (int side = -1; side <= 1; side += 2) {
			float ex = x + sideX * half * side;
			float ez = z + sideZ * half * side;
			vertex(pose, consumer, x, y, z, middle);
			vertex(pose, consumer, ex, y, ez, edge & 0x00FFFFFF);
			vertex(pose, consumer, ex, top, ez, edge & 0x00FFFFFF);
			vertex(pose, consumer, x, top, z, clearTop);
		}
	}

	/**
	 * Adds a filled box around a block: its faces that look at the camera, so no face lies over another and the box is as light from every side.
	 * Coordinates as for {@link #beam}.
	 *
	 * @param opacity the faces' opacity, from 0 to 1
	 */
	public static void box(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, int rgb, float opacity) {
		boxAt(pose, consumer, x - 0.5f, y, z - 0.5f, x + 0.5f, y + 1f, z + 0.5f, rgb, opacity);
	}

	/**
	 * Adds a filled box between two corners, relative to the camera, such as one part of a block's shape (a slab, a step of stairs); see {@link #box}.
	 */
	public static void boxAt(PoseStack.Pose pose, VertexConsumer consumer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int rgb,
		float opacity) {
		int color = argb(rgb, opacity);
		float x0 = minX - BOX_GROW;
		float x1 = maxX + BOX_GROW;
		float y0 = minY - BOX_GROW;
		float y1 = maxY + BOX_GROW;
		float z0 = minZ - BOX_GROW;
		float z1 = maxZ + BOX_GROW;
		// the camera is at 0, 0, 0: a face looks at it when the camera lies on its outer side
		if (x0 > 0) {
			face(pose, consumer, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, color);
		}
		if (x1 < 0) {
			face(pose, consumer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color);
		}
		if (y0 > 0) {
			face(pose, consumer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color);
		}
		if (y1 < 0) {
			face(pose, consumer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, color);
		}
		if (z0 > 0) {
			face(pose, consumer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, color);
		}
		if (z1 < 0) {
			face(pose, consumer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color);
		}
	}

	/**
	 * Adds the outline of a block's box: the edges of its faces that look at the camera, as lines of the same width on the screen at any distance.
	 * Coordinates as for {@link #beam}.
	 *
	 * @param pixelAngle the size of a screen pixel at a distance of one block
	 * @param widthPixels how wide the lines are, in screen pixels
	 */
	public static void outline(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, int rgb, float opacity, float pixelAngle,
		float widthPixels) {
		outlineAt(pose, consumer, x - 0.5f, y, z - 0.5f, x + 0.5f, y + 1f, z + 0.5f, rgb, opacity, pixelAngle, widthPixels);
	}

	/** Adds the outline of a box between two corners, relative to the camera; see {@link #outline}. */
	public static void outlineAt(PoseStack.Pose pose, VertexConsumer consumer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
		int rgb, float opacity, float pixelAngle, float widthPixels) {
		int color = argb(rgb, opacity);
		float half = pixelAngle * widthPixels / 2f;
		float x0 = minX - BOX_GROW;
		float x1 = maxX + BOX_GROW;
		float y0 = minY - BOX_GROW;
		float y1 = maxY + BOX_GROW;
		float z0 = minZ - BOX_GROW;
		float z1 = maxZ + BOX_GROW;
		boolean west = x0 > 0;
		boolean east = x1 < 0;
		boolean down = y0 > 0;
		boolean up = y1 < 0;
		boolean north = z0 > 0;
		boolean south = z1 < 0;
		// each edge where one of its two faces looks at the camera; through walls the hidden ones would draw a wire frame
		for (int a = 0; a < 2; a++) {
			float ya = a == 0 ? y0 : y1;
			boolean faceY = a == 0 ? down : up;
			for (int b = 0; b < 2; b++) {
				float zb = b == 0 ? z0 : z1;
				float xb = b == 0 ? x0 : x1;
				if (faceY || (b == 0 ? north : south)) {
					edge(pose, consumer, x0, ya, zb, x1, ya, zb, half, color);
				}
				if (faceY || (b == 0 ? west : east)) {
					edge(pose, consumer, xb, ya, z0, xb, ya, z1, half, color);
				}
				float xa = a == 0 ? x0 : x1;
				if ((a == 0 ? west : east) || (b == 0 ? north : south)) {
					edge(pose, consumer, xa, y0, zb, xa, y1, zb, half, color);
				}
			}
		}
	}

	/**
	 * Adds a thin flat ring lying on the ground around a block, for a ring that grows and fades. Coordinates as for {@link #beam}, but
	 * with y the height of the ground it lies on.
	 *
	 * @param radius the ring's radius from the block's middle, in blocks
	 */
	public static void groundRing(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float radius, int rgb, float opacity) {
		float lift = y + RING_LIFT;
		float inner = Math.max(0f, radius - RING_WIDTH / 2f);
		float outer = radius + RING_WIDTH / 2f;
		int color = argb(rgb, opacity);
		for (int i = 0; i < RING_PIECES; i++) {
			float fromCos = RING_COS[i];
			float fromSin = RING_SIN[i];
			float toCos = RING_COS[i + 1];
			float toSin = RING_SIN[i + 1];
			face(pose, consumer, x + fromCos * inner, lift, z + fromSin * inner, x + fromCos * outer, lift, z + fromSin * outer, x + toCos * outer, lift,
				z + toSin * outer, x + toCos * inner, lift, z + toSin * inner, color);
		}
	}

	/**
	 * Adds a line between two places relative to the camera, as wide on the screen at any distance: an edge of a block's shape.
	 *
	 * @param pixelAngle the size of a screen pixel at a distance of one block
	 */
	public static void line(PoseStack.Pose pose, VertexConsumer consumer, float ax, float ay, float az, float bx, float by, float bz, int rgb,
		float opacity, float pixelAngle, float widthPixels) {
		edge(pose, consumer, ax, ay, az, bx, by, bz, pixelAngle * widthPixels / 2f, argb(rgb, opacity));
	}

	/** One line along an edge, turned to the camera, as wide on the screen at both ends; a half width longer each way so corners close. */
	private static void edge(PoseStack.Pose pose, VertexConsumer consumer, float ax, float ay, float az, float bx, float by, float bz, float half,
		int color) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float ux = dx / length;
		float uy = dy / length;
		float uz = dz / length;
		float halfA = half * (float) Math.sqrt(ax * ax + ay * ay + az * az);
		float halfB = half * (float) Math.sqrt(bx * bx + by * by + bz * bz);
		ax -= ux * halfA;
		ay -= uy * halfA;
		az -= uz * halfA;
		bx += ux * halfB;
		by += uy * halfB;
		bz += uz * halfB;
		// across the line and across the way to the camera: the line's side as the camera sees it
		float sx = uy * az - uz * ay;
		float sy = uz * ax - ux * az;
		float sz = ux * ay - uy * ax;
		float side = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (side < 1e-6f) {
			return;
		}
		float sa = halfA / side;
		float sb = halfB / side;
		face(pose, consumer, ax - sx * sa, ay - sy * sa, az - sz * sa, ax + sx * sa, ay + sy * sa, az + sz * sa, bx + sx * sb, by + sy * sb, bz + sz * sb,
			bx - sx * sb, by - sy * sb, bz - sz * sb, color);
	}

	/**
	 * One quad, its corners in turn around it, wound to face the camera (some of the game's quads drop their back), and pulled a little towards
	 * the camera: the same place on the screen, but in front of the block it lies on, so it never flickers with it.
	 */
	private static void face(PoseStack.Pose pose, VertexConsumer consumer, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
		float cz, float dx, float dy, float dz, int color) {
		float nx = (by - ay) * (cz - az) - (bz - az) * (cy - ay);
		float ny = (bz - az) * (cx - ax) - (bx - ax) * (cz - az);
		float nz = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
		float pull = 1f - TOWARDS_CAMERA;
		if (nx * ax + ny * ay + nz * az <= 0) {
			pulled(pose, consumer, ax, ay, az, pull, color);
			pulled(pose, consumer, bx, by, bz, pull, color);
			pulled(pose, consumer, cx, cy, cz, pull, color);
			pulled(pose, consumer, dx, dy, dz, pull, color);
		} else {
			pulled(pose, consumer, dx, dy, dz, pull, color);
			pulled(pose, consumer, cx, cy, cz, pull, color);
			pulled(pose, consumer, bx, by, bz, pull, color);
			pulled(pose, consumer, ax, ay, az, pull, color);
		}
	}

	private static void pulled(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float pull, int color) {
		vertex(pose, consumer, x * pull, y * pull, z * pull, color);
	}

	/**
	 * Adds a dotted line between two places, relative to the camera, of small squares turned to the camera.
	 *
	 * @param left the camera's left vector, and up its up vector
	 */
	public static void dottedLine(PoseStack.Pose pose, VertexConsumer consumer, float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
		Vector3fc left, Vector3fc up, int rgb, float opacity) {
		float dx = toX - fromX;
		float dy = toY - fromY;
		float dz = toZ - fromZ;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		int dots = Math.min(MOST_DOTS, (int) (length / DOT_STEP));
		int color = argb(rgb, opacity);
		float lx = left.x() * DOT_HALF;
		float ly = left.y() * DOT_HALF;
		float lz = left.z() * DOT_HALF;
		float ux = up.x() * DOT_HALF;
		float uy = up.y() * DOT_HALF;
		float uz = up.z() * DOT_HALF;
		for (int i = 1; i < dots; i++) {
			float t = i / (float) dots;
			float cx = fromX + dx * t;
			float cy = fromY + dy * t;
			float cz = fromZ + dz * t;
			vertex(pose, consumer, cx - lx - ux, cy - ly - uy, cz - lz - uz, color);
			vertex(pose, consumer, cx + lx - ux, cy + ly - uy, cz + lz - uz, color);
			vertex(pose, consumer, cx + lx + ux, cy + ly + uy, cz + lz + uz, color);
			vertex(pose, consumer, cx - lx + ux, cy - ly + uy, cz - lz + uz, color);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, int argb) {
		// the texture coordinate is for the see-through quads, which take a white texture; formats without one leave it out
		consumer.addVertex(pose, x, y, z).setColor(argb).setUv(0.5f, 0.5f).setLight(FULL_LIGHT);
	}

	private static int argb(int rgb, float opacity) {
		int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
		return alpha << 24 | rgb & 0xFFFFFF;
	}

	/** @return how large a waypoint label is at a distance, in GUI pixels per label pixel: full near, down to its least size far away */
	public static float labelSize(double distance) {
		double share = Math.clamp((distance - FULL_SIZE_BLOCKS) / (LEAST_SIZE_BLOCKS - FULL_SIZE_BLOCKS), 0.0, 1.0);
		return (float) (1.0 - share * (1.0 - LEAST_SIZE));
	}

	/** @return the label's width, in its GUI pixels, to center it on its place */
	public static float labelWidth(Canvas canvas, String name, String distance) {
		return LABEL_PAD + NUMBER + 5 + canvas.boldWidthScaled(name, TextScale.SMALL) + (distance.isEmpty() ? 0 : 4 + canvas.widthScaled(distance,
			TextScale.SMALL)) + LABEL_PAD + 2;
	}

	/** @return the label's height, in its GUI pixels */
	public static int labelHeight() {
		return LABEL_HEIGHT;
	}

	/**
	 * Draws a label pill with its top left at 0, 0: the point's number on a disc in its color, its name, and its distance ("6 m", or "done").
	 * The caller places and scales it (see {@code WorldLabel.draw}).
	 */
	public static void label(Canvas canvas, int number, String name, String distance, int rgb, State state) {
		float width = labelWidth(canvas, name, distance);
		boolean dim = state == State.LATER || state == State.DONE;
		int color = 0xFF000000 | rgb;
		canvas.roundedSmooth(0, 0, width, LABEL_HEIGHT, Radius.LG, dim ? 0x99101418 : 0xD9101418);
		if (state == State.NEXT) {
			canvas.ring(0, 0, Math.round(width), LABEL_HEIGHT, Radius.LG, 1, Draw.withOpacity(color, 0.85f));
		}
		float numberX = LABEL_PAD + NUMBER / 2f;
		int disc = state == State.DONE ? Draw.mix(color, 0xFF30D158, 0.6f) : color;
		canvas.discAt(numberX, LABEL_HEIGHT / 2f, NUMBER, dim ? Draw.withOpacity(disc, DIM) : disc);
		String digits = String.valueOf(number);
		float digitsWidth = canvas.boldWidthScaled(digits, TextScale.SMALL * 0.85f);
		canvas.textAtScaledBold(digits, numberX - digitsWidth / 2f, LABEL_HEIGHT / 2f - 3f, TextScale.SMALL * 0.85f,
			Draw.luminance(disc) > 0.6f ? 0xFF1C1C1E : 0xFFFFFFFF);
		float textX = LABEL_PAD + NUMBER + 5;
		canvas.textAtScaledBold(name, textX, LABEL_HEIGHT / 2f - 3.5f, TextScale.SMALL, dim ? 0x99FFFFFF : 0xFFFFFFFF);
		if (!distance.isEmpty()) {
			canvas.textAtScaled(distance, textX + canvas.boldWidthScaled(name, TextScale.SMALL) + 4, LABEL_HEIGHT / 2f - 3.5f, TextScale.SMALL,
				dim ? 0x66FFFFFF : 0xB3FFFFFF);
		}
	}

	/** @return the width of a plain label, the name and the distance after it, in its GUI pixels */
	public static float plainLabelWidth(Canvas canvas, String name, String distance) {
		return canvas.boldWidthScaled(name, TextScale.BODY) + (distance.isEmpty() ? 0 : PLAIN_GAP + canvas.widthScaled(distance, TextScale.SMALL)) + 2 * PLAIN_PAD;
	}

	/** @return a plain label's height, in its GUI pixels */
	public static int plainLabelHeight() {
		return PLAIN_HEIGHT;
	}

	/**
	 * Draws a plain label with its top left at 0, 0: the name in white with a soft shadow, floating without a box, and the distance after it,
	 * smaller. Later points of a route are a little softer.
	 */
	public static void plainLabel(Canvas canvas, String name, String distance, State state) {
		plainLabel(canvas, name, distance, state, true);
	}

	/** Draws a plain label as {@link #plainLabel(Canvas, String, String, State)}, with or without its shadow. */
	public static void plainLabel(Canvas canvas, String name, String distance, State state, boolean shadow) {
		float width = plainLabelWidth(canvas, name, distance);
		if (shadow) {
			canvas.softLight(width / 2f, PLAIN_HEIGHT / 2f, width / 2f + PLAIN_SHADOW_SPREAD, PLAIN_HEIGHT / 2f + PLAIN_SHADOW_SPREAD, PLAIN_SHADOW);
		}
		plainText(canvas, 0f, 0f, name, distance, state, shadow);
	}

	/** The name and the distance of a plain label, in a label's box with its top left at {@code x}, {@code top}. */
	private static void plainText(Canvas canvas, float x, float top, String name, String distance, State state, boolean shadow) {
		boolean soft = state == State.LATER || state == State.DONE;
		float textY = top + PLAIN_HEIGHT / 2f - 4f;
		if (shadow) {
			canvas.textAtScaledBold(name, x + PLAIN_PAD + PLAIN_DROP, textY + PLAIN_DROP, TextScale.BODY, PLAIN_DROP_COLOR);
		}
		canvas.textAtScaledBold(name, x + PLAIN_PAD, textY, TextScale.BODY, soft ? 0xC8FFFFFF : 0xFFFFFFFF);
		if (!distance.isEmpty()) {
			float distanceX = x + PLAIN_PAD + canvas.boldWidthScaled(name, TextScale.BODY) + (name.isEmpty() ? 0 : PLAIN_GAP);
			if (shadow) {
				canvas.textAtScaled(distance, distanceX + PLAIN_DROP, textY + 1f + PLAIN_DROP, TextScale.SMALL, PLAIN_DROP_COLOR);
			}
			canvas.textAtScaled(distance, distanceX, textY + 1f, TextScale.SMALL, soft ? 0x99FFFFFF : 0xCCFFFFFF);
		}
	}

	/** @return the side of an icon label's tile, in its GUI pixels */
	public static int iconTile(float iconSize) {
		return Math.max(ICON_LEAST, Math.round(PLAIN_HEIGHT * iconSize));
	}

	/** @return the width of an icon label: the tile, and the text after it where there is one */
	public static float iconLabelWidth(Canvas canvas, String name, String distance, float iconSize) {
		boolean text = !name.isEmpty() || !distance.isEmpty();
		return iconTile(iconSize) + (text ? plainLabelWidth(canvas, name, distance) : 0);
	}

	/** @return an icon label's height: its tile's, or the text's where that is higher */
	public static int iconLabelHeight(float iconSize) {
		return Math.max(PLAIN_HEIGHT, iconTile(iconSize));
	}

	/**
	 * Draws a label that marks a point by itself, with its top left at 0, 0: the point's icon on a small box in its color, and after it the name
	 * and the distance as a plain label has them.
	 *
	 * @param icon the name of the icon, as {@code Canvas.icon} takes it
	 */
	public static void iconLabel(Canvas canvas, String icon, String name, String distance, int rgb, State state, boolean shadow, float iconSize) {
		int tile = iconTile(iconSize);
		int height = iconLabelHeight(iconSize);
		boolean soft = state == State.LATER || state == State.DONE;
		int color = 0xFF000000 | rgb;
		if (shadow) {
			float width = iconLabelWidth(canvas, name, distance, iconSize);
			canvas.softLight(width / 2f, height / 2f, width / 2f + PLAIN_SHADOW_SPREAD, height / 2f + PLAIN_SHADOW_SPREAD, PLAIN_SHADOW);
		}
		canvas.roundedSmooth(0, (height - tile) / 2f, tile, tile, Radius.SM, soft ? Draw.withOpacity(color, DIM) : color);
		int inset = Math.max(1, Math.round(tile * ICON_INSET));
		canvas.icon(icon, inset, (height - tile) / 2 + inset, tile - 2 * inset, Draw.luminance(color) > 0.6f ? 0xFF1C1C1E : 0xFFFFFFFF);
		if (!name.isEmpty() || !distance.isEmpty()) {
			plainText(canvas, tile, (height - PLAIN_HEIGHT) / 2f, name, distance, state, shadow);
		}
	}

	/** @return a distance as a label shows it: "6 m", "120 m", "1.2 km" */
	public static String distance(double blocks) {
		if (blocks >= 1000) {
			return Math.round(blocks / 100.0) / 10.0 + " km";
		}
		return Math.round(blocks) + " m";
	}
}
