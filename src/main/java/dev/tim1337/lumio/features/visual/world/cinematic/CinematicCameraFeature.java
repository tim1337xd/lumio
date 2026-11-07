package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.config.setting.NoteSetting;
import dev.tim1337.lumio.features.music.LyricCutFade;
import dev.tim1337.lumio.features.music.MusicService;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.util.Daylight;


import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Your own music video: a camera that shows the player from shots it finds around them, cuts with the song (on line starts, on the beat), and
 * places the lyrics in the world in each shot. It is client only: the player's position and rotation are never changed, nothing is sent and
 * nothing is automated; the camera only shows what is around the player, never from inside blocks or through walls. On servers it only runs
 * while the player stands still and stays close; in singleplayer it may go further and keep running while they walk.
 */
public class CinematicCameraFeature extends Feature {
	public enum Mode {
		MUSIC_VIDEO("Music video"),
		CALM_DRIFT("Calm drift");

		private final Component displayName;

		Mode(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** How many lines a shot lasts, from the least to the most; a chorus shot one line less. */
	public enum Pace {
		RELAXED("Relaxed", 6, 8),
		BALANCED("Balanced", 4, 6),
		ENERGETIC("Energetic", 3, 4);

		private final Component displayName;
		final int leastLines;
		final int mostLines;

		Pace(String displayName, int leastLines, int mostLines) {
			this.displayName = Component.literal(displayName);
			this.leastLines = leastLines;
			this.mostLines = mostLines;
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** When the film starts by itself: after this long without input while music plays. */
	public enum StartAfter {
		OFF("Off", 0),
		SECONDS_30("30 s", 30),
		SECONDS_60("60 s", 60),
		MINUTES_2("2 min", 120);

		private final Component displayName;
		final int seconds;

		StartAfter(String displayName, int seconds) {
			this.displayName = Component.literal(displayName);
			this.seconds = seconds;
		}

		public Component displayName() {
			return displayName;
		}
	}

	public enum Letterbox {
		OFF("Off", 0f),
		WIDE("1.85", 1.85f),
		SCOPE("2.39", 2.39f);

		private final Component displayName;
		final float ratio;

		Letterbox(String displayName, float ratio) {
			this.displayName = Component.literal(displayName);
			this.ratio = ratio;
		}

		public Component displayName() {
			return displayName;
		}
	}


	/** The song card: at the song start only, or then also as a calm card that stays. */
	public enum SongInfo {
		AT_START("At song start"),
		ALWAYS("Always");

		private final Component displayName;

		SongInfo(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** One row for the song card and how long it stays, which are saved as a switch and a choice. */
	public enum SongInfoRow {
		OFF("Off"),
		CARD("Card"),
		AT_START("At start");

		private final Component displayName;

		SongInfoRow(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** On servers the camera stays this close to the player's eyes and at most this far above them; in singleplayer further. */
	private static final double SERVER_DISTANCE = 6.0;
	private static final double SERVER_HEIGHT = 4.0;
	private static final double SINGLEPLAYER_DISTANCE = 16.0;
	/** A player moving more than this per tick is walking, in blocks. */
	private static final double MOVED = 0.02;
	private static final int TICKS_PER_SECOND = 20;

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", Component.literal("Mode"), Mode.MUSIC_VIDEO, Mode::displayName)
		.withOptionCards(choice -> choice == Mode.MUSIC_VIDEO
			? new EnumSetting.OptionCard("camera", SystemColors.PURPLE_RGB, "Picks a new shot on line starts and lands cuts on the beat. Lyrics in the world.", null)
			: new EnumSetting.OptionCard("wind", SystemColors.TEAL_RGB, "One slow shot after another with soft blends. Good without music.", null)), "Film");
	private final EnumSetting<Pace> pace = add(new EnumSetting<>("pace", Component.literal("Pace"), Pace.BALANCED, Pace::displayName, EnumSetting.Style.SEGMENTED),
		"Film");
	private final BooleanSetting[] shotSwitches = new BooleanSetting[ShotType.count()];
	private final MultiSelectSetting shots;
	private final BooleanSetting cutOnBeat = add(new BooleanSetting("cut_on_beat", Component.literal("Cut on the beat"), true), "Cuts and starting",
		"Moves a cut up to 150 ms to the nearest beat");
	private final BooleanSetting smoothMoves = add(new BooleanSetting("smooth_instrumental", Component.literal("Smooth moves in long instrumental parts"), true),
		"Cuts and starting");
	private final EnumSetting<StartAfter> startAfter = add(new EnumSetting<>("start_after", Component.literal("Start by itself"), StartAfter.SECONDS_60,
		StartAfter::displayName, EnumSetting.Style.SEGMENTED), "Cuts and starting", "After this long idle while music plays");
	private final BooleanSetting stopOnMove = add(new BooleanSetting("stop_on_move", Component.literal("Stop when I move or press a key"), true),
		"Cuts and starting");
	private final EnumSetting<Letterbox> letterbox = add(new EnumSetting<>("letterbox", Component.literal("Letterbox"), Letterbox.SCOPE, Letterbox::displayName,
		EnumSetting.Style.SEGMENTED), "Look");
	private final BooleanSetting titleCard = add(new BooleanSetting("title_card", Component.literal("Song card"), true), "Look");
	private final EnumSetting<SongInfo> songInfo = add(new EnumSetting<>("song_info", Component.literal("Song info"), SongInfo.AT_START, SongInfo::displayName,
		EnumSetting.Style.SEGMENTED), "Look");
	private final BooleanSetting vignette = add(new BooleanSetting("vignette", Component.literal("Vignette"), true), "Look");
	private final BooleanSetting worldLyrics = add(new BooleanSetting("world_lyrics", Component.literal("Lyrics in the world"), true), "Look",
		"Same style as Music, Lyrics in the world");
	private final BooleanSetting hideHud = add(new BooleanSetting("hide_hud", Component.literal("Hide the HUD"), true), "Look");
	private final BooleanSetting handheld = add(new BooleanSetting("handheld", Component.literal("Handheld camera"), false), "Look",
		"A slight, natural sway, like a camera held by hand");

	{
		add(new NoteSetting("server_note", "On servers it only runs while you stand still, stays close and always keeps you in view."), "Look");
	}

	private final CineCamera camera = new CineCamera(this);
	private final CineOverlay overlay = new CineOverlay(this, camera);
	/** Keys of the film itself, which never count as the player coming back. */
	private List<KeyMapping> ownKeys = List.of();
	private int idleTicks;
	private double lastX;
	private double lastY;
	private double lastZ;
	private float lastHealth = -1f;
	private double[] fingerprint = new double[0];
	/** The lyrics hidden for this film with their key. */
	private boolean lyricsHidden;

	public CinematicCameraFeature() {
		super("cinematic_camera", "Cinematic camera", "Your own music video: the camera finds its shots and cuts with the song.", false);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("camera");
		summary("Your own music video while you are away");
		List<BooleanSetting> switches = new ArrayList<>();
		for (int i = 0; i < ShotType.count(); i++) {
			ShotType type = ShotType.of(i);
			shotSwitches[i] = new BooleanSetting("shot_" + type.id, Component.literal(type.title), true, BooleanSetting.Style.CHECKBOX);
			switches.add(shotSwitches[i]);
		}
		shots = add(new MultiSelectSetting("shots", Component.literal("Shots"), switches).asChips(), "Film");
		pace.setDescription("A new shot every few lines, a bit faster in the chorus");
		pace.visibleWhen(() -> mode.get() == Mode.MUSIC_VIDEO);
		cutOnBeat.visibleWhen(() -> mode.get() == Mode.MUSIC_VIDEO);
		smoothMoves.visibleWhen(() -> mode.get() == Mode.MUSIC_VIDEO);
		add(new DerivedEnumSetting<>("song_info_row", Component.literal("Song info"), SongInfoRow.AT_START, SongInfoRow::displayName, EnumSetting.Style.SEGMENTED,
			() -> !titleCard.get() ? SongInfoRow.OFF : songInfo.get() == SongInfo.ALWAYS ? SongInfoRow.CARD : SongInfoRow.AT_START,
			value -> {
				titleCard.set(value != SongInfoRow.OFF);
				if (value != SongInfoRow.OFF) {
					songInfo.set(value == SongInfoRow.CARD ? SongInfo.ALWAYS : SongInfo.AT_START);
				}
			}), "Look");
		titleCard.visibleWhen(() -> false);
		songInfo.visibleWhen(() -> false);
		addResetButton();
	}

	// ------------------------------------------------------------------------------------------------------ for the parts

	boolean calmMode() {
		return mode.get() == Mode.CALM_DRIFT;
	}

	Pace pace() {
		return pace.get();
	}

	boolean cutOnBeat() {
		return cutOnBeat.get();
	}

	boolean smoothMoves() {
		return smoothMoves.get();
	}

	boolean handheld() {
		return handheld.get();
	}

	boolean titleCard() {
		return titleCard.get();
	}

	/** @return whether the song card stays as a calm card after the song start */
	boolean songInfoAlways() {
		return songInfo.get() == SongInfo.ALWAYS;
	}

	boolean vignette() {
		return vignette.get();
	}

	float letterboxRatio() {
		return letterbox.get().ratio;
	}

	/** @return whether the game runs on a server, where the camera only runs while the player stands still and stays close */
	static boolean onServer() {
		return !Minecraft.getInstance().hasSingleplayerServer();
	}

	double maxDistance() {
		return onServer() ? SERVER_DISTANCE : SINGLEPLAYER_DISTANCE;
	}

	double maxHeight() {
		return onServer() ? SERVER_HEIGHT : SINGLEPLAYER_DISTANCE;
	}

	/** @return whether the film keeps running while the player walks: only in singleplayer, with stopping on movement off */
	boolean allowsWalking() {
		return !onServer() && !stopOnMove.get();
	}

	void allowedShots(boolean[] into) {
		boolean any = false;
		for (int i = 0; i < into.length; i++) {
			into[i] = shotSwitches[i].get();
			any |= into[i];
		}
		if (!any) {
			// with every type switched off, the film still needs somewhere to film from
			into[ShotType.SLOW_ORBIT.ordinal()] = true;
		}
	}

	float daylight(Minecraft client) {
		return Daylight.of(client.level);
	}

	// ------------------------------------------------------------------------------------------------------ for other features

	/** @return whether the film runs */
	public boolean active() {
		return camera.running();
	}

	/** @return whether the HUD, the chat and notices are hidden because the view is a film; they come back when it stops */
	public boolean hidesHud() {
		return isEnabled() && hideHud.get() && camera.running();
	}

	/** @return whether the film places the lyrics in its shots */
	public boolean showsWorldLyrics() {
		return camera.running() && worldLyrics.get() && !lyricsHidden;
	}

	/** @return how visible the lyrics and the song card are now: they fade around a cut, and never stand in a new shot as they stood in the old one */
	public float lyricFade() {
		return camera.running() && ModFeatures.MENU.animations() ? camera.lyricFade() : 1f;
	}

	/** @return how long ago the last cut was, or {@link LyricCutFade#NONE} */
	public long sinceCutMs() {
		return camera.running() ? camera.sinceCutMs() : LyricCutFade.NONE;
	}

	/** @return whether the shot has a place for its lyric; it is read with {@link #lyricX} and the others */
	public boolean lyricPlaced() {
		return showsWorldLyrics() && camera.anchored();
	}

	public double lyricX() {
		return camera.anchorX();
	}

	public double lyricY() {
		return camera.anchorY();
	}

	public double lyricZ() {
		return camera.anchorZ();
	}

	/** @return how many shots took their lyric spot so far, so a log can tell them apart */
	public int shotNumber() {
		return camera.shots();
	}

	/** @return the lyric's size in blocks per text unit, about 40 % of the frame width for a typical line */
	public float lyricUnit() {
		return camera.anchorUnit();
	}

	/**
	 * Measures a lyric line's box (standing upright, facing along its right direction) along the shot's planned path, from now over the time it
	 * shows.
	 *
	 * @return its least margin to the safe part of the picture over that time, as a share of it; negative when it leaves it, lands on the song
	 *         card or the character, or a block hides it
	 */
	public float lyricMargin(double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight, float seconds) {
		Minecraft client = Minecraft.getInstance();
		float aspect = CineCamera.aspect(client);
		return camera.lyricMargin(client.level, x, y, z, rightX, rightZ, halfWidth, halfHeight, seconds, aspect, picture(aspect));
	}

	/** Projects a line standing in the world through the camera now, in picture units. @return false when part of it is behind the camera */
	public boolean lyricBoxNow(double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight, float[] box) {
		float aspect = CineCamera.aspect(Minecraft.getInstance());
		return camera.boxNow(x, y, z, rightX, rightZ, halfWidth, halfHeight, aspect, picture(aspect), box);
	}

	/** @return whether two lines in the world stay apart on the screen along the shot's path over some seconds, by a share of the first one's height */
	public boolean lyricLinesApart(double ax, double ay, double az, double aRightX, double aRightZ, double aHalfWidth, double aHalfHeight, double bx,
								   double by, double bz, double bRightX, double bRightZ, double bHalfWidth, double bHalfHeight, float seconds, float gapShare) {
		float aspect = CineCamera.aspect(Minecraft.getInstance());
		return camera.linesApart(ax, ay, az, aRightX, aRightZ, aHalfWidth, aHalfHeight, bx, by, bz, bRightX, bRightZ, bHalfWidth, bHalfHeight, seconds,
			gapShare, aspect, picture(aspect));
	}

	/**
	 * Finds the world spot behind a place in the picture now, about as far away as the player.
	 *
	 * @param spot receives x, y, z and the camera's level right direction (x, z)
	 */
	public void pictureSpot(float across, float up, double depthFactor, double[] spot) {
		float aspect = CineCamera.aspect(Minecraft.getInstance());
		camera.pictureSpot(across, up, depthFactor, aspect, picture(aspect), spot);
	}

	/** @return how much deeper than the player a line must stand for half its width to take only a share of the picture's half width; at least 1 */
	public double fitDepthFactor(double halfWidth, double share) {
		return camera.fitDepthFactor(halfWidth, share, CineCamera.aspect(Minecraft.getInstance()));
	}

	/** @return whether the shot running is a push-in that still moves in, where lines near the edges would leave the narrowing picture */
	public boolean pushingIn() {
		return camera.pushingIn();
	}

	/**
	 * Finds a spot for a line during a push-in that keeps it fully in the picture while it shows: close to the player, a little smaller if need be,
	 * or with the rest of the push-in shortened.
	 *
	 * @param halfWidth  half the line's width at its own size, in blocks
	 * @param halfHeight half its height at its own size, in blocks
	 * @param out        receives x, y, z (the line's middle), its right direction (x, z), its size (1 its own) and the share of the push-in kept
	 */
	public boolean pushInSpot(double halfWidth, double halfHeight, float seconds, double[] out) {
		Minecraft client = Minecraft.getInstance();
		float aspect = CineCamera.aspect(client);
		return camera.pushInSpot(client.level, halfWidth, halfHeight, seconds, aspect, picture(aspect), out);
	}

	/** @return the point the camera looks at during the shot (the player, at their feet): x, y and z */
	public double focusX() {
		return camera.focusX();
	}

	public double focusY() {
		return camera.focusY();
	}

	public double focusZ() {
		return camera.focusZ();
	}

	/** @return why the last lyric spot measured failed, for the debug log; empty when it passed */
	public String lyricMiss() {
		return camera.lyricMiss;
	}

	public String shotName() {
		return camera.shot.type.name();
	}

	/** @return the share of the screen's height the picture takes between the letterbox bars */
	private float picture(float aspect) {
		float ratio = letterboxRatio();
		return ratio <= 0f ? 1f : Math.min(1f, aspect / ratio);
	}

	// ------------------------------------------------------------------------------------------------------ camera hook

	/** Computes the camera for the frame. @return false when the film is not running (or just ended), and the game's camera is used */
	public boolean frame(float partial) {
		Minecraft client = Minecraft.getInstance();
		if (!camera.running() || client.level == null || client.player == null) {
			return false;
		}
		return camera.frame(client, partial);
	}

	public double cameraX() {
		return camera.out.x;
	}

	public double cameraY() {
		return camera.out.y;
	}

	public double cameraZ() {
		return camera.out.z;
	}

	public float cameraYaw() {
		return camera.out.yaw;
	}

	public float cameraPitch() {
		return camera.out.pitch;
	}

	/** @return the shot's field of view instead of the game's */
	public float fovOr(float original) {
		camera.gameFov = original;
		return camera.running() ? camera.out.fov : original;
	}

	// ------------------------------------------------------------------------------------------------------ start and stop

	public void toggle(Minecraft client) {
		if (!isEnabled() || client.level == null || client.player == null) {
			return;
		}
		if (camera.running()) {
			camera.stop();
		} else {
			start(client);
		}
	}

	private void start(Minecraft client) {
		idleTicks = 0;
		lyricsHidden = false;
		fingerprint = fingerprint(client);
		lastHealth = client.player.getHealth();
		camera.start(client, 1f);
	}

	/** Ends the film if it runs. */
	public void leave() {
		camera.stop();
	}

	void nextShot() {
		if (camera.running()) {
			camera.nextRequested = true;
		}
	}

	void holdShot() {
		if (camera.running()) {
			camera.holding = !camera.holding;
		}
	}

	void toggleLyrics() {
		if (camera.running()) {
			lyricsHidden = !lyricsHidden;
		}
	}

	/** @return whether a screen lets the film run behind it: the chat, the pause menu and the mod's menu; other screens end it */
	private static boolean filmsBehind(Screen screen) {
		return screen == null || screen instanceof ChatScreen || screen instanceof PauseScreen || screen instanceof MacScreen;
	}

	/**
	 * Called every game tick: ends the film when the player is back (a key, a look, a step on servers or with stopping on movement, damage, a game
	 * screen), and starts it by itself after the idle time while music plays.
	 */
	public void tick(Minecraft client, List<KeyMapping> own) {
		ownKeys = own;
		if (!isEnabled() || client.level == null || client.player == null) {
			if (camera.running()) {
				camera.phase = CineCamera.Phase.OFF;
			}
			return;
		}
		Player player = client.player;
		Screen screen = client.gui.screen();
		double[] now = fingerprint(client);
		boolean changed = !Arrays.equals(now, fingerprint);
		fingerprint = now;
		boolean moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ) + Math.abs(player.getY() - lastY) > MOVED;
		lastX = player.getX();
		lastY = player.getY();
		lastZ = player.getZ();
		boolean hurt = lastHealth >= 0f && player.getHealth() < lastHealth;
		lastHealth = player.getHealth();
		boolean present = changed || moved || now[3] > 0 || (client.gameMode != null && client.gameMode.isDestroying());
		if (camera.running()) {
			boolean strict = onServer() || stopOnMove.get();
			boolean back = hurt || !filmsBehind(screen) || screen == null && strict && (changed || moved);
			if (back) {
				camera.stop();
				idleTicks = 0;
			}
			return;
		}
		if (screen != null || present) {
			idleTicks = 0;
			return;
		}
		idleTicks++;
		StartAfter after = startAfter.get();
		if (!camera.running() && after != StartAfter.OFF && idleTicks >= after.seconds * TICKS_PER_SECOND && MusicService.isPlaying() && !client.isPaused()
			&& player.isAlive()) {
			start(client);
		}
	}

	/** @return the player's input: look direction, selected slot and the number of held keys (the film's own keys and the chat keys left out) */
	private double[] fingerprint(Minecraft client) {
		Player player = client.player;
		int keys = 0;
		for (KeyMapping key : client.options.keyMappings) {
			if (!own(key) && key != client.options.keyChat && key != client.options.keyCommand && !(key instanceof ToggleKeyMapping) && key.isDown()) {
				keys++;
			}
		}
		return new double[]{player.getYRot(), player.getXRot(), player.getInventory().getSelectedSlot(), keys};
	}

	/** @return whether a key is one of the film's, or bound to the same key as one of them (another binding on it is held by the same press) */
	private boolean own(KeyMapping key) {
		for (KeyMapping mine : ownKeys) {
			if (key == mine || key.same(mine)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------------ overlay and settings

	/** Draws the letterbox, the vignette, the title card and the keys hint. Called instead of the HUD while it is hidden, otherwise after it. */
	public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (isEnabled()) {
			overlay.render(graphics);
		}
	}

	/** Registers the cinematic camera keys and shows them in the Keys group of the settings. Called during mod initialization. */
	public void registerKeys() {
		for (KeyMapping key : CinematicKeys.register()) {
			addKey(key, KeySetting.Where.WORLD);
		}
	}

	/** @return the film's camera, for the test log */
	CineCamera camera() {
		return camera;
	}
}
