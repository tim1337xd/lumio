package dev.tim1337.lumio.api.experimental.debug;

/** The render passes whose time a debug tool can measure, as passed to {@link DebugHooks#passBegin}; their names in {@link #NAMES}. */
public final class DebugPass {
	public static final int FRAME = 0;
	public static final int GUI = 1;
	public static final int SKY = 2;
	public static final int CLOUDS = 3;
	public static final int CLOUDS_OVER_SKY = 4;
	public static final int ECHO = 5;
	public static final int GLASS = 6;
	public static final int WET_GROUND = 7;
	/** The march of the clouds, inside the sky's time. */
	public static final int CLOUD_MARCH = 8;
	/** Lumio's outlines of highlighted entities. */
	public static final int HIGHLIGHTS = 9;

	/** The shown name of every pass, by its number. */
	private static final String[] NAMES = {"frame (all)", "gui (all)", "sky", "clouds", "clouds over sky", "echo", "glass backdrop", "wet ground",
		"cloud march", "highlights"};

	private DebugPass() {
	}

	/** @return how many passes there are */
	public static int count() {
		return NAMES.length;
	}

	/** @return the shown name of a pass */
	public static String name(int pass) {
		return NAMES[pass];
	}
}
