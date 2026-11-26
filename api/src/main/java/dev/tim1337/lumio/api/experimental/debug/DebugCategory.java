package dev.tim1337.lumio.api.experimental.debug;

/** The categories of Lumio's own probes. An add-on uses its own names for its own things ("dungeon", "mining") and these for shared ones. */
public final class DebugCategory {
	/** What fits nowhere else. */
	public static final String GENERAL = "general";
	/** Render passes, shaders, pipelines, the menu world. */
	public static final String RENDER = "render";
	/** The attack and use keys, the mouse, the window's focus. */
	public static final String INPUT = "input";
	/** Chat lines and what the server sends above the hotbar. */
	public static final String CHAT = "chat";
	/** HUD panels and what they read (scoreboard, tab list). */
	public static final String HUD = "hud";
	/** The song, its lyrics and the players they come from. */
	public static final String MUSIC = "music";
	/** The update check. */
	public static final String UPDATE = "update";
	/** The window: where it is put, borderless, the GUI scale. */
	public static final String WINDOW = "window";
	/** Server resource packs. */
	public static final String PACKS = "packs";
	/** The cinematic camera. */
	public static final String CAMERA = "camera";
	/** The menu's look (Colored, glass); the Colored theme's colors once a second when asked for ({@link DebugHooks#wants}). */
	public static final String MENU = "menu";
	/** The first person swing's values, every frame, when asked for ({@link DebugHooks#wants}). */
	public static final String SWING = "swing";
	/** The Colored theme's colors once a second, when asked for ({@link DebugHooks#wants}). */
	public static final String COLORED = "colored";

	private DebugCategory() {
	}
}
