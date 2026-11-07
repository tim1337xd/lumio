package dev.tim1337.lumio.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * The edge of a flat item at a place, as lines in the space the camera is the origin of, on the face that is its outline on screen, with its handle
 * and its tip: what Radiance's Echo draws between two frames when the item moved far. Filled anew into buffers that are kept.
 */
public final class EchoShape {
	/** Per segment its two ends and the direction out of the item, or nothing (zero) for a line that faces the camera on both sides. */
	private static final int FLOATS_PER_SEGMENT = 9;
	/** A short step out of the item, to see which way out is on screen. */
	private static final float SMALL = 0.01f;

	private float[] segments = new float[FLOATS_PER_SEGMENT * 1024];
	private int segmentCount;
	private int[] first = new int[64];
	private int[] end = new int[64];
	private float[] light = new float[64];
	private float[] thickness = new float[64];
	/** Per copy: the handle's and the tip's place, and whether there is a blade at all. */
	private float[] blades = new float[6 * 64];
	private boolean[] bladed = new boolean[64];
	private int copies;
	private boolean open;
	private final Vector3f point = new Vector3f();
	private final Vector3f outward = new Vector3f();
	private final Vector3f depth = new Vector3f();
	/** The handle and the tip of the copy being filled, as the item pixel with the least and the most u - v. */
	private float leastDiagonal;
	private float mostDiagonal;

	/** Empties it for a new frame. */
	public void clear() {
		segmentCount = 0;
		copies = 0;
		open = false;
	}

	/** @return how many copies it holds */
	public int copies() {
		return copies;
	}

	/** Starts a copy with its light (0 to 1) and how thick its lines are (1 is the full width). */
	public void begin(float copyLight, float copyThickness) {
		if (copies == first.length) {
			int size = copies * 2;
			first = Arrays.copyOf(first, size);
			end = Arrays.copyOf(end, size);
			light = Arrays.copyOf(light, size);
			thickness = Arrays.copyOf(thickness, size);
			blades = Arrays.copyOf(blades, size * 6);
			bladed = Arrays.copyOf(bladed, size);
		}
		first[copies] = segmentCount;
		light[copies] = copyLight;
		thickness[copies] = copyThickness;
		bladed[copies] = false;
		leastDiagonal = Float.POSITIVE_INFINITY;
		mostDiagonal = Float.NEGATIVE_INFINITY;
		open = true;
	}

	/** Ends the copy begun last; one without lines is dropped. */
	public void end() {
		if (!open) {
			return;
		}
		open = false;
		if (segmentCount == first[copies]) {
			return;
		}
		end[copies] = segmentCount;
		copies++;
	}

	/** Drops the copy begun last, with what was added to it. */
	public void drop() {
		if (open) {
			segmentCount = first[copies];
			open = false;
		}
	}

	/** @return the tip of the copy begun last, x, y and z, or null if it has no blade yet */
	public float[] tipOfOpen(float[] out) {
		if (!open || !bladed[copies]) {
			return null;
		}
		System.arraycopy(blades, copies * 6 + 3, out, 0, 3);
		return out;
	}

	/**
	 * Adds the edge of a flat item's pixels to the copy begun last, with its handle and tip.
	 *
	 * @param pose    the pose of the item's quads
	 * @param mapping where the sprite lies
	 * @param hull    the walls along the edge of the sprite's pixels
	 */
	public void addItem(Matrix4f pose, FlatItemMapping mapping, OutlineGeometry.Hull hull) {
		for (int side = 0; side < hull.walls().length; side++) {
			float[] walls = hull.walls()[side];
			float outU = side == OutlineGeometry.LEFT ? -1f : side == OutlineGeometry.RIGHT ? 1f : 0f;
			float outV = side == OutlineGeometry.TOP ? -1f : side == OutlineGeometry.BOTTOM ? 1f : 0f;
			Vector3f uAxis = mapping.uAxis();
			Vector3f vAxis = mapping.vAxis();
			pose.transformDirection(uAxis.x * outU + vAxis.x * outV, uAxis.y * outU + vAxis.y * outV, uAxis.z * outU + vAxis.z * outV, outward);
			outward.normalize();
			pose.transformDirection(0f, 0f, mapping.halfThickness(), depth);
			int sideFirst = segmentCount;
			for (int i = 0; i + 3 < walls.length; i += 4) {
				ensure(segmentCount + 1);
				int at = segmentCount * FLOATS_PER_SEGMENT;
				place(pose, mapping, walls[i], walls[i + 1], at);
				place(pose, mapping, walls[i + 2], walls[i + 3], at + 3);
				segments[at + 6] = outward.x;
				segments[at + 7] = outward.y;
				segments[at + 8] = outward.z;
				segmentCount++;
				blade(pose, mapping, walls[i], walls[i + 1]);
				blade(pose, mapping, walls[i + 2], walls[i + 3]);
			}
			if (segmentCount > sideFirst) {
				ontoSilhouette(sideFirst, segmentCount);
			}
		}
	}

	/** Adds a line from one point to another, already in camera space. */
	public void addSegment(float ax, float ay, float az, float bx, float by, float bz) {
		ensure(segmentCount + 1);
		int at = segmentCount * FLOATS_PER_SEGMENT;
		segments[at] = ax;
		segments[at + 1] = ay;
		segments[at + 2] = az;
		segments[at + 3] = bx;
		segments[at + 4] = by;
		segments[at + 5] = bz;
		segments[at + 6] = 0f;
		segments[at + 7] = 0f;
		segments[at + 8] = 0f;
		segmentCount++;
	}

	/**
	 * Moves the edges of one side of the item from its middle onto its front or its back, whichever lies further out on screen, all of them alike so
	 * that they stay joined: the item is a slab, and on
	 * the edges where its thin sides show, they are the outline, and would cover a line in the middle. The camera is at the origin, looking along -z.
	 */
	private void ontoSilhouette(int from, int to) {
		int at = (from + (to - from) / 2) * FLOATS_PER_SEGMENT;
		float mx = (segments[at] + segments[at + 3]) * 0.5f;
		float my = (segments[at + 1] + segments[at + 4]) * 0.5f;
		float mz = (segments[at + 2] + segments[at + 5]) * 0.5f;
		float outX = screenX(mx + outward.x * SMALL, mz + outward.z * SMALL) - screenX(mx, mz);
		float outY = screenY(my + outward.y * SMALL, mz + outward.z * SMALL) - screenY(my, mz);
		float front = (screenX(mx + depth.x, mz + depth.z) - screenX(mx, mz)) * outX + (screenY(my + depth.y, mz + depth.z) - screenY(my, mz)) * outY;
		float back = (screenX(mx - depth.x, mz - depth.z) - screenX(mx, mz)) * outX + (screenY(my - depth.y, mz - depth.z) - screenY(my, mz)) * outY;
		float sign = front >= back ? 1f : -1f;
		for (int segment = from; segment < to; segment++) {
			int place = segment * FLOATS_PER_SEGMENT;
			for (int end = 0; end < 6; end += 3) {
				segments[place + end] += depth.x * sign;
				segments[place + end + 1] += depth.y * sign;
				segments[place + end + 2] += depth.z * sign;
			}
		}
	}

	private static float screenX(float x, float z) {
		return x / Math.max(1.0e-4f, -z);
	}

	private static float screenY(float y, float z) {
		return y / Math.max(1.0e-4f, -z);
	}

	private void place(Matrix4f pose, FlatItemMapping mapping, float u, float v, int at) {
		Vector3f origin = mapping.origin();
		Vector3f uAxis = mapping.uAxis();
		Vector3f vAxis = mapping.vAxis();
		pose.transformPosition(origin.x + u * uAxis.x + v * vAxis.x, origin.y + u * uAxis.y + v * vAxis.y, origin.z + u * uAxis.z + v * vAxis.z, point);
		segments[at] = point.x;
		segments[at + 1] = point.y;
		segments[at + 2] = point.z;
	}

	/** Keeps the pixel corner that is most the handle (bottom left) and most the tip (top right) of a held tool. */
	private void blade(Matrix4f pose, FlatItemMapping mapping, float u, float v) {
		float diagonal = u - v;
		int at = copies * 6;
		if (diagonal < leastDiagonal) {
			leastDiagonal = diagonal;
			placeInto(pose, mapping, u, v, blades, at);
		}
		if (diagonal > mostDiagonal) {
			mostDiagonal = diagonal;
			placeInto(pose, mapping, u, v, blades, at + 3);
		}
		bladed[copies] = true;
	}

	private void placeInto(Matrix4f pose, FlatItemMapping mapping, float u, float v, float[] target, int at) {
		Vector3f origin = mapping.origin();
		Vector3f uAxis = mapping.uAxis();
		Vector3f vAxis = mapping.vAxis();
		pose.transformPosition(origin.x + u * uAxis.x + v * vAxis.x, origin.y + u * uAxis.y + v * vAxis.y, origin.z + u * uAxis.z + v * vAxis.z, point);
		target[at] = point.x;
		target[at + 1] = point.y;
		target[at + 2] = point.z;
	}

	private void ensure(int count) {
		if (count * FLOATS_PER_SEGMENT > segments.length) {
			segments = Arrays.copyOf(segments, Math.max(segments.length * 2, count * FLOATS_PER_SEGMENT));
		}
	}

	/** @return how many lines it holds, of all copies */
	public int lineCount() {
		return segmentCount;
	}

	/** @return a line's two ends, x, y and z each, into {@code out} */
	public float[] line(int index, float[] out) {
		System.arraycopy(segments, index * FLOATS_PER_SEGMENT, out, 0, 6);
		return out;
	}
}
