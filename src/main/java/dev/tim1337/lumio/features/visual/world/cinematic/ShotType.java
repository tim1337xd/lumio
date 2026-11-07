package dev.tim1337.lumio.features.visual.world.cinematic;

/** The kinds of shots of the cinematic camera, each a place relative to the player with a slow move and a field of view. Their ids are persisted. */
public enum ShotType {
	WIDE_CRANE("wide_crane", "Wide crane", true),
	SLOW_ORBIT("slow_orbit", "Slow orbit", false),
	OVER_SHOULDER("over_shoulder", "Over the shoulder", false),
	LOW_HERO("low_hero", "Low hero", false),
	CLOSE_PUSH_IN("close_push_in", "Close push-in", false),
	SIDE_TRACK("side_track", "Side track", false),
	TOP_DOWN("top_down", "Top-down drift", true);

	private static final ShotType[] ALL = values();

	final String id;
	final String title;
	/** Whether the shot is wide, with more of the world in it: preferred in choruses. */
	final boolean wide;

	ShotType(String id, String title, boolean wide) {
		this.id = id;
		this.title = title;
		this.wide = wide;
	}

	public String title() {
		return title;
	}

	static ShotType of(int ordinal) {
		return ALL[ordinal];
	}

	static int count() {
		return ALL.length;
	}
}
