package dev.tim1337.lumio.api.experimental.waypoint;

/** The colors a waypoint can have, as the color dots of the Waypoints page and the place card show them, in their order. */
public final class WaypointColors {
	public static final int BLUE = 0x5AC8FA;
	public static final int PURPLE = 0xBF5AF2;
	public static final int GREEN = 0x30D158;
	public static final int YELLOW = 0xFFD60A;
	public static final int ORANGE = 0xFF9F0A;
	public static final int RED = 0xFF453A;
	/** The color of a new group. */
	public static final int DEFAULT = BLUE;
	private static final int[] ALL = {BLUE, PURPLE, GREEN, YELLOW, ORANGE, RED};

	private WaypointColors() {
	}

	/** @return how many colors there are */
	public static int count() {
		return ALL.length;
	}

	/** @return the color at an index, as 0xRRGGBB */
	public static int get(int index) {
		return ALL[Math.floorMod(index, ALL.length)];
	}

	/** @return the index of the color closest to an RGB color */
	public static int nearest(int rgb) {
		int best = 0;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < ALL.length; i++) {
			int dr = (ALL[i] >> 16 & 0xFF) - (rgb >> 16 & 0xFF);
			int dg = (ALL[i] >> 8 & 0xFF) - (rgb >> 8 & 0xFF);
			int db = (ALL[i] & 0xFF) - (rgb & 0xFF);
			int distance = dr * dr + dg * dg + db * db;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}
}
