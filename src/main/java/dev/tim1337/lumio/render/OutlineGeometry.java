package dev.tim1337.lumio.render;

import net.minecraft.client.renderer.texture.SpriteContents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the shell around a sprite from its pixels: a closed volume that follows the opaque part of the
 * texture exactly, grown by a distance. Drawing its back faces around the item results in an outline that looks
 * right from any direction.
 * <p>
 * The geometry is in sprite space, as fractions of the sprite size, and may reach beyond the sprite.
 * Results are cached, as they only depend on the sprite and the reach.
 */
public final class OutlineGeometry {
	/**
	 * The shell.
	 *
	 * @param radius how far the shell extends beyond the item, in pixels of a 16 pixel wide item
	 * @param caps   rectangles (u0, v0, u1, v1) of the two large faces, parallel to the sprite
	 * @param walls  boundary segments (u0, v0, u1, v1) standing perpendicular on the sprite, grouped by facing direction:
	 *               {@link #TOP}, {@link #BOTTOM}, {@link #LEFT} and {@link #RIGHT}
	 */
	public record Hull(float radius, float[] caps, float[][] walls) {
	}

	/** The directions walls face in sprite space. Top is towards smaller v, left towards smaller u. */
	public static final int TOP = 0;
	public static final int BOTTOM = 1;
	public static final int LEFT = 2;
	public static final int RIGHT = 3;

	private record Key(SpriteContents sprite, int reach) {
	}

	/** An item is 16 pixels wide regardless of its texture resolution. */
	public static final float ITEM_SIZE = 16f;

	private static final int MAX_CACHE_SIZE = 64;
	private static final float INFINITY = 1e20f;

	private static final Map<Key, Hull> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Hull> eldest) {
			return size() > MAX_CACHE_SIZE;
		}
	};

	private OutlineGeometry() {
	}

	/** @param reach how far the shell extends beyond the sprite, in pixels of a 16 pixel wide item */
	public static Hull get(SpriteContents sprite, float reach) {
		Key key = new Key(sprite, Math.round(reach * 20f));
		Hull hull = CACHE.get(key);
		if (hull == null) {
			hull = compute(sprite, key.reach / 20f);
			CACHE.put(key, hull);
		}
		return hull;
	}

	private static Hull compute(SpriteContents sprite, float reach) {
		int width = sprite.width();
		int height = sprite.height();
		float spritePixelsPerItemPixel = Math.max(width, height) / ITEM_SIZE;
		float reachInSpritePixels = reach * spritePixelsPerItemPixel;

		// split every sprite pixel into subdivision x subdivision cells for a smoother shell
		int subdivision = Math.clamp(128 / Math.max(width, height), 1, 8);
		int padding = (int) Math.ceil(reachInSpritePixels) + 1;
		int cellsX = (width + 2 * padding) * subdivision;
		int cellsY = (height + 2 * padding) * subdivision;

		boolean[] opaque = new boolean[cellsX * cellsY];
		for (int y = 0; y < cellsY; y++) {
			int spriteY = y / subdivision - padding;
			for (int x = 0; x < cellsX; x++) {
				int spriteX = x / subdivision - padding;
				opaque[y * cellsX + x] = spriteX >= 0 && spriteY >= 0 && spriteX < width && spriteY < height
					&& !sprite.isTransparent(0, spriteX, spriteY);
			}
		}

		float[] squaredDistance = squaredDistanceTransform(opaque, cellsX, cellsY);
		float reachInCells = reachInSpritePixels * subdivision;
		boolean[] inside = new boolean[opaque.length];
		for (int i = 0; i < inside.length; i++) {
			// distances are measured between cell centers, so the edge of a cell next to the sprite is half a cell away
			inside[i] = squaredDistance[i] == 0f || (float) Math.sqrt(squaredDistance[i]) - 0.5f <= reachInCells;
		}
		smooth(inside, squaredDistance, cellsX, cellsY, Math.round(reachInCells * 0.35f));
		return buildHull(inside, reach, new Grid(cellsX, cellsY, subdivision, padding, width, height));
	}

	/**
	 * Smooths the shell: the shape is blurred and thresholded again at the midpoint, which rounds off corners and fills small dents, so a wide
	 * outline is smooth instead of stair-stepped. Cells on the item itself are kept.
	 */
	private static void smooth(boolean[] inside, float[] squaredDistance, int cellsX, int cellsY, int radius) {
		if (radius < 1) {
			return;
		}
		float[] values = new float[inside.length];
		float[] scratch = new float[inside.length];
		for (int i = 0; i < values.length; i++) {
			values[i] = inside[i] ? 1f : 0f;
		}
		for (int pass = 0; pass < 2; pass++) {
			boxBlur(values, scratch, cellsX, cellsY, radius, true);
			boxBlur(scratch, values, cellsX, cellsY, radius, false);
		}
		for (int i = 0; i < inside.length; i++) {
			inside[i] = squaredDistance[i] == 0f || values[i] >= 0.5f;
		}
	}

	private static void boxBlur(float[] from, float[] to, int cellsX, int cellsY, int radius, boolean horizontal) {
		int length = horizontal ? cellsX : cellsY;
		int lines = horizontal ? cellsY : cellsX;
		float[] sums = new float[length + 1];
		for (int line = 0; line < lines; line++) {
			for (int i = 0; i < length; i++) {
				int index = horizontal ? line * cellsX + i : i * cellsX + line;
				sums[i + 1] = sums[i] + from[index];
			}
			for (int i = 0; i < length; i++) {
				int a = Math.max(0, i - radius);
				int b = Math.min(length, i + radius + 1);
				int index = horizontal ? line * cellsX + i : i * cellsX + line;
				to[index] = (sums[b] - sums[a]) / (2 * radius + 1);
			}
		}
	}

	private record Grid(int cellsX, int cellsY, int subdivision, int padding, int width, int height) {
		float u(int x) {
			return ((float) x / subdivision - padding) / width;
		}

		float v(int y) {
			return ((float) y / subdivision - padding) / height;
		}
	}

	/** Builds the shell around all inside cells. */
	private static Hull buildHull(boolean[] inside, float radius, Grid grid) {
		int cellsX = grid.cellsX();
		int cellsY = grid.cellsY();

		// large faces: horizontal runs of cells, with consecutive rows of identical runs merged into one rectangle
		List<int[]> rects = new ArrayList<>();
		Map<Long, int[]> openRects = new HashMap<>();
		FloatList[] walls = {new FloatList(), new FloatList(), new FloatList(), new FloatList()};
		for (int y = 0; y < cellsY; y++) {
			for (int x = 0; x < cellsX; x++) {
				if (!isInside(inside, cellsX, cellsY, x, y)) {
					continue;
				}
				int start = x;
				while (x + 1 < cellsX && isInside(inside, cellsX, cellsY, x + 1, y)) {
					x++;
				}
				long key = ((long) start << 32) | (x + 1);
				int[] rect = openRects.get(key);
				if (rect != null && rect[3] == y) {
					rect[3] = y + 1;
				} else {
					rect = new int[]{start, y, x + 1, y + 1};
					rects.add(rect);
					openRects.put(key, rect);
				}
			}
			// top and bottom walls: horizontal runs of cells without a neighbor on that side
			addHorizontalWalls(walls[TOP], inside, grid, y, -1, y);
			addHorizontalWalls(walls[BOTTOM], inside, grid, y, 1, y + 1);
		}
		for (int x = 0; x < cellsX; x++) {
			// left and right walls: vertical runs of cells without a neighbor on that side
			addVerticalWalls(walls[LEFT], inside, grid, x, -1, x);
			addVerticalWalls(walls[RIGHT], inside, grid, x, 1, x + 1);
		}

		FloatList caps = new FloatList();
		for (int[] rect : rects) {
			caps.add(grid.u(rect[0]), grid.v(rect[1]), grid.u(rect[2]), grid.v(rect[3]));
		}
		return new Hull(radius, caps.toArray(), new float[][]{
			walls[TOP].toArray(), walls[BOTTOM].toArray(), walls[LEFT].toArray(), walls[RIGHT].toArray()
		});
	}

	private static void addHorizontalWalls(FloatList walls, boolean[] inside, Grid grid, int y, int neighborOffset, int boundary) {
		int cellsX = grid.cellsX();
		int cellsY = grid.cellsY();
		for (int x = 0; x < cellsX; x++) {
			if (!isEdge(inside, cellsX, cellsY, x, y, 0, neighborOffset)) {
				continue;
			}
			int start = x;
			while (x + 1 < cellsX && isEdge(inside, cellsX, cellsY, x + 1, y, 0, neighborOffset)) {
				x++;
			}
			walls.add(grid.u(start), grid.v(boundary), grid.u(x + 1), grid.v(boundary));
		}
	}

	private static void addVerticalWalls(FloatList walls, boolean[] inside, Grid grid, int x, int neighborOffset, int boundary) {
		int cellsX = grid.cellsX();
		int cellsY = grid.cellsY();
		for (int y = 0; y < cellsY; y++) {
			if (!isEdge(inside, cellsX, cellsY, x, y, neighborOffset, 0)) {
				continue;
			}
			int start = y;
			while (y + 1 < cellsY && isEdge(inside, cellsX, cellsY, x, y + 1, neighborOffset, 0)) {
				y++;
			}
			walls.add(grid.u(boundary), grid.v(start), grid.u(boundary), grid.v(y + 1));
		}
	}

	private static boolean isInside(boolean[] inside, int cellsX, int cellsY, int x, int y) {
		return x >= 0 && y >= 0 && x < cellsX && y < cellsY && inside[y * cellsX + x];
	}

	/** @return whether the cell is inside and its neighbor at the given offset is not */
	private static boolean isEdge(boolean[] inside, int cellsX, int cellsY, int x, int y, int offsetX, int offsetY) {
		return isInside(inside, cellsX, cellsY, x, y) && !isInside(inside, cellsX, cellsY, x + offsetX, y + offsetY);
	}

	private static final class FloatList {
		private float[] values = new float[64];
		private int size;

		void add(float a, float b, float c, float d) {
			if (size + 4 > values.length) {
				values = Arrays.copyOf(values, values.length * 2);
			}
			values[size++] = a;
			values[size++] = b;
			values[size++] = c;
			values[size++] = d;
		}

		float[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}

	/**
	 * Felzenszwalb and Huttenlocher's exact euclidean distance transform.
	 *
	 * @return for every cell, the squared distance to the closest opaque cell, in cells
	 */
	static float[] squaredDistanceTransform(boolean[] opaque, int width, int height) {
		return squaredDistanceTransform(opaque, width, height, new float[width * height]);
	}

	private static float[] scratchInput = new float[0];
	private static float[] scratchOutput = new float[0];
	private static int[] scratchParabolas = new int[0];
	private static float[] scratchBoundaries = new float[0];

	/** Same, writing into the given reused array, since this runs every frame; render thread only. */
	static float[] squaredDistanceTransform(boolean[] opaque, int width, int height, float[] distances) {
		int size = Math.max(width, height);
		if (scratchInput.length < size) {
			scratchInput = new float[size];
			scratchOutput = new float[size];
			scratchParabolas = new int[size];
			scratchBoundaries = new float[size + 1];
		}
		float[] input = scratchInput;
		float[] output = scratchOutput;
		int[] parabolas = scratchParabolas;
		float[] boundaries = scratchBoundaries;

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				input[y] = opaque[y * width + x] ? 0f : INFINITY;
			}
			transform1d(input, output, parabolas, boundaries, height);
			for (int y = 0; y < height; y++) {
				distances[y * width + x] = output[y];
			}
		}
		for (int y = 0; y < height; y++) {
			System.arraycopy(distances, y * width, input, 0, width);
			transform1d(input, output, parabolas, boundaries, width);
			System.arraycopy(output, 0, distances, y * width, width);
		}
		return distances;
	}

	private static void transform1d(float[] input, float[] output, int[] parabolas, float[] boundaries, int n) {
		int k = 0;
		parabolas[0] = 0;
		boundaries[0] = -INFINITY;
		boundaries[1] = INFINITY;
		for (int q = 1; q < n; q++) {
			float intersection;
			while (true) {
				int p = parabolas[k];
				intersection = ((input[q] + (float) q * q) - (input[p] + (float) p * p)) / (2f * q - 2f * p);
				if (intersection > boundaries[k]) {
					break;
				}
				k--;
			}
			k++;
			parabolas[k] = q;
			boundaries[k] = intersection;
			boundaries[k + 1] = INFINITY;
		}
		k = 0;
		for (int q = 0; q < n; q++) {
			while (boundaries[k + 1] < q) {
				k++;
			}
			int p = parabolas[k];
			output[q] = (float) (q - p) * (q - p) + input[p];
		}
	}
}
