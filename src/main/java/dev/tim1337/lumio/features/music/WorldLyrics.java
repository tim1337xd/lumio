package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.features.music.MusicFeature.WorldEffect;
import dev.tim1337.lumio.features.music.MusicFeature.WorldFont;
import dev.tim1337.lumio.features.music.MusicFeature.WorldPlace;
import dev.tim1337.lumio.features.music.MusicFeature.WorldPlacement;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.visual.world.cinematic.CinematicCameraFeature;
import dev.tim1337.lumio.render.FogState;
import dev.tim1337.lumio.render.LyricSprites;
import dev.tim1337.lumio.render.OutlineRenderTypes;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.util.Daylight;

import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * The lyrics in the world (the "Lyrics in the world" settings of {@link MusicFeature}): the line being sung, standing at a fixed spot in the world,
 * around the player near eye height or large in the sky. Each line gets its spot once, when it starts ({@link WorldLyricPlacer}): in the direction
 * the player looks, in the open, never over the crosshair, facing the player once; it stays there, also when the player turns away. When the next
 * line starts, the old one stays a moment, slowly dimming, then fades out; at most two older lines stand at once, and the last line before a long
 * instrumental part stays a few seconds after its last word.
 * <p>
 * Two effects, both with the chosen colors: the soft glow (sung words light up with a glow in the glow color) and the classic glow of before (a
 * breathing glow around the whole line and a light following the sung part). Adlibs float smaller next to their line with the same effect, a bit
 * softer. The lyrics fade with the fog, are a little
 * dimmer by day and glow at night. In the cinematic camera every shot has its own spot for the line; the look is the same.
 */
final class WorldLyrics {
	/** The widest a row of a line gets, in text units. */
	static final int WRAP_WIDTH = 300;
	/** A line fades in and out over this time. */
	private static final float FADE_SECONDS = 0.25f;
	/**
	 * In the cinematic camera a new line comes in where the one before stands: the one leaving drifts up this many rows as it fades, and the new one rises
	 * in from as far below, so the two part instead of lying over each other.
	 */
	private static final float FILM_DRIFT_ROWS = 0.6f;
	/** Size of a text unit per block of distance, so a line is equally large on screen at any distance: around the player, and in the sky. */
	private static final float NEAR_UNIT = 0.0022f;
	private static final float SKY_UNIT = 0.0029f;
	/** The chorus glows this much more. */
	private static final float CHORUS_GLOW = 1.3f;
	/** A line sung again brightens once, softly, and its glow more. */
	private static final float REPEAT_PULSE_SECONDS = 0.45f;
	private static final float REPEAT_GLOW = 0.8f;
	private static final float REPEAT_LIGHT = 0.25f;
	private static final float GLOW_ALPHA = 0.75f;
	/** How much softer adlibs glow; their size, gap and place come from {@link AdlibLayout}. */
	private static final float ADLIB_GLOW = 0.5f;
	/** The most adlibs of a line planned, and the least width they may wrap in, in text units. */
	private static final int MOST_PLANNED = 8;
	private static final float ADLIB_LEAST_ROOM = 160f;
	/** The most adlibs of a line the room below it is kept for; more stand beside it. */
	private static final int ADLIBS_BELOW = 3;
	/** A second singer's line stands this far to the right. */
	private static final float SINGER_TWO_SIDE = 40f;
	private static final float ROW = 38f;
	/** Words not sung yet, as a share of the sung ones' brightness. */
	private static final float REST_ALPHA = 0.4f;
	/** By day the lyrics are a little dimmer and glow less; at night they are bright. */
	private static final float DAY_ALPHA = 0.85f;
	private static final float DAY_GLOW = 0.55f;
	/** The dark shadow below the text: always under handwriting, by day under the clean style. */
	private static final int SHADOW = 0xFF000014;
	private static final float HAND_SHADOW = 0.42f;
	private static final float DAY_SHADOW = 0.5f;
	/** Even in thick fog the line stays faintly readable. */
	private static final float LEAST_CLEARNESS = 0.15f;
	private static final float FOG_TINT = 0.6f;
	/** Lines in the sky stand far away but must stay readable: they take only this share of the fog. */
	private static final float SKY_FOG = 0.3f;
	/** The classic glow: how strong the glow is around clean and handwritten text, how it breathes, and its light following the sung part. */
	private static final float CLASSIC_GLOW = 0.62f;
	private static final float CLASSIC_HAND_GLOW = 0.4f;
	private static final float CLASSIC_HAND_WHITE = 0.35f;
	private static final float CLASSIC_DIM = 0.55f;
	private static final float CLASSIC_HAND_DIM = 0.72f;
	private static final float CLASSIC_HOT_WHITE = 0.6f;
	private static final float CLASSIC_HOT = 0.75f;
	private static final float BREATH = 0.15f;
	private static final float BREATH_SPEED = 2.1f;
	/** Adlibs lean like italics. */
	private static final Matrix4f SLANT = new Matrix4f(1f, 0f, 0f, 0f, -0.2f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
	private static final int SPRITES = 6;
	/** A line whose sprite could not be made (no free texture) is tried again after this many frames. */
	private static final int RETRY_FRAMES = 10;

	/** An older line dims to this share of its brightness while it stays, then fades out over the time after; one pushed out goes quicker. */
	private static final float OLDER_DIM = 0.55f;
	private static final float OUT_SECONDS = 0.6f;
	/** Below this nothing is seen. */
	private static final float VISIBLE_ALPHA = 0.004f;
	/** The places an adlib tries: the one planned, two further below, and one on each side. */
	private static final int ADLIB_SPOTS = 5;
	private static final int SPOT_RIGHT = 3;
	private static final int SPOT_LEFT = 4;
	private static final float PUSHED_OUT_SECONDS = 0.25f;
	/** In the cinematic camera: how long the line before takes to fade once the next begins, and how far ahead a new line must stay clear of it,
	 * along the camera's path, with a gap of half a line's height. */
	private static final float FILM_LEAVE_SECONDS = 1.2f;
	private static final float FILM_CLEAR_SECONDS = 1.5f;
	private static final float FILM_GAP_ROWS = 0.5f;
	/** A line waiting for the one in its place comes in once that one is this dim. */
	private static final float MADE_WAY = 0.2f;
	/** At most this many older lines stand beside the sung one; in the cinematic camera one, so never more than two lines show. */
	private static final int MOST_OLDER = 2;
	private static final int MOST_OLDER_FILMED = 1;
	/** Room for the sung line, the older ones and one pushed out while it fades. */
	private static final int SLOTS = MOST_OLDER + 2;
	/**
	 * A line followed by a long instrumental part (or the last of the song) stays this long after its last word; in the cinematic camera every line
	 * stays this long after its last word, also when the next one has started.
	 */
	private static final long LONG_GAP_MS = 6000L;
	private static final long HOLD_BEFORE_GAP_MS = 3000L;
	private static final long FILM_HOLD_MS = 2000L;
	/** In the cinematic camera the lyrics are this much smaller than the shot's room for them; the new line keeps clear of an older one. */
	private static final float FILM_SCALE = 0.75f;
	private static final float FILM_RISE_GAP = 6f;
	/** The offsets a film line tries from the shot's spot. Reused. */
	private final float[] rises = new float[3];
	/** A line too wide even in balanced rows is made at most this much smaller; rows are never narrower than the least wrap width. */
	private static final float LEAST_SHRINK = 0.8f;
	private static final int LEAST_WRAP = 120;
	/** The rows a line prefers at most, the steps it is made smaller in, and the smallest a very long line gets so none of its words is lost. */
	private static final int PREFERRED_ROWS = 2;
	private static final float SHRINK_STEP = 0.05f;
	private static final float LEAST_TEXT_SCALE = 0.25f;

	/** A line's sprite, kept while the line is on screen; the same text in two lines (a repeated line) shares it. */
	private static final class Entry {
		String text;
		boolean hand;
		boolean classic;
		/** The width its rows were broken at, and the size of its text in the sprite. */
		int wrap;
		float textScale;
		LyricSprites.Sprite sprite;
		long frame;
		long triedAt;
	}

	/**
	 * A line standing in the world: which line (by its index and start, never by its text, so a repeated line is a new one), where, how visible,
	 * how far it is sung, and where it is in its life: sung, older (dimming), or leaving.
	 */
	private static final class Shown {
		int index = -1;
		long startMs;
		long endMs;
		String text;
		double x;
		double y;
		double z;
		float facing;
		float unit;
		/** Half the width and height it covers as seen from where it was placed, in degrees, for the next lines to keep clear of it. */
		float halfWidth;
		float halfHeight;
		/** Whether it stands in the sky, where the fog touches it only a little. */
		boolean sky;
		/** The width its rows are broken at, in text units: narrower than the most where the view has less room. */
		int wrap = WRAP_WIDTH;
		/** How large its text is set, a share of the normal size: below 1 only for a line too long for its rows at full size. */
		float textScale = 1f;
		/** How far it has faded in, how much it is dimmed as an older line, and how much is left of its fade out. */
		float appear;
		float dim = 1f;
		float out = 1f;
		boolean older;
		float olderFor;
		boolean leaving;
		boolean pushed;
		/** A line that takes the place of one still fading there waits for it to dim before it comes in, so the two never lie over each other. */
		Shown waitFor;
		/** In the cinematic camera a line leaves with an ease-out over {@link #FILM_LEAVE_SECONDS}: its light falls quickly at first, then softly. */
		boolean easeOut;
		/** Whether it is the last line of the song or followed by a long instrumental part. */
		boolean beforeGap;
		float sung;
		float lit;
		float age;
		/** The soft brightening of a line sung again, from 1 down to 0. */
		float repeatPulse;
			/** In the cinematic camera: how far the line stands from the shot's spot, in text units, fixed when it starts so no line ever moves. */
		float rise;
		/** The sung glyphs of its rows, its own, since lines are submitted now and drawn later in the frame. */
		final float[] rowsSung = new float[LyricSprites.MAX_ROWS];
		/**
		 * Where its adlibs stand, by their order among the line's adlibs: their middles from the line's middle, their sizes and the widths their
		 * rows break at, in text units; planned once, when its first adlib shows, so no adlib ever moves.
		 */
		boolean planned;
		int planCount;
		final float[] adlibX = new float[MOST_PLANNED];
		final float[] adlibY = new float[MOST_PLANNED];
		final float[] adlibScale = new float[MOST_PLANNED];
		final int[] adlibWrap = new int[MOST_PLANNED];
		final float[] adlibWidth = new float[MOST_PLANNED];
		final float[] adlibHeight = new float[MOST_PLANNED];
		/** The film's shot it was placed in, which it never outlives. */
		int shot;
		// the line's box in the film, to keep the next line clear of it: its middle's height, half sizes and its right, all in blocks
		float boxShift;
		double boxY;
		double boxHalfWidth;
		double boxHalfHeight;
		double boxRightX;
		double boxRightZ;

		void clear() {
			index = -1;
			text = null;
			appear = 0f;
			dim = 1f;
			out = 1f;
			older = false;
			olderFor = 0f;
			leaving = false;
			pushed = false;
			easeOut = false;
			waitFor = null;
			sung = 0f;
			lit = 0f;
			age = 0f;
			rise = 0f;
			sky = false;
			wrap = WRAP_WIDTH;
			textScale = 1f;
			planned = false;
			boxHalfWidth = 0.0;
			boxShift = 0f;
			boxHalfHeight = 0.0;
			planCount = 0;
		}

		boolean on() {
			return text != null;
		}

		float alpha() {
			return appear * dim * (easeOut ? out * out : out);
		}
	}

	private final MusicFeature music;
	private final LyricMotion motion;
	private final AnimClock clock = new AnimClock();
	private final WorldLyricPlacer placer = new WorldLyricPlacer();
	private final WorldLyricPlacer.Spot spot = new WorldLyricPlacer.Spot();
	private final WorldLyricScout scout = new WorldLyricScout();
	private final float[] halfAngles = new float[2];
	/** The rows' width and the text size {@link #fitRows} chose. */
	private int fitWrap;
	private float fitScale;
	/** A line's direction from the eye, worked out when needed. */
	private final float[] direction = new float[2];
	private final Entry[] entries = new Entry[SPRITES];
	/** The lines standing, the sung one among them (or null), and the sung glyphs of each adlib. */
	private final Shown[] lines = new Shown[SLOTS];
	private Shown current;
	private final float[][] adlibSung = new float[LyricMotion.MAX_ADLIBS][LyricSprites.MAX_ROWS];
	/** Scratch space for planning a line's adlibs: their widths, heights and places. */
	private final float[] planWidths = new float[MOST_PLANNED];
	private final float[] planHeights = new float[MOST_PLANNED];
	private final float[] planSpots = new float[2 * MOST_PLANNED];
	/** The line spawned last, by its lyrics, index and start, so it is spawned once even after it left. */
	private List<LyricLine> spawnedIn;
	private int spawnedIndex = -1;
	private long spawnedStart;
	/** A spot for adlibs sung while no line stands, such as at the start of a song. */
	private final Shown alone = new Shown();
	/**
	 * Where each adlib slot stands when its own line is not standing (an adlib in a gap): the line that was visible when it started, by that line's
	 * index and start, or the spot of its own; kept until the adlib ends, so it never moves.
	 */
	private final Shown[] adlibHome = new Shown[LyricMotion.MAX_ADLIBS];
	/**
	 * Where each adlib slot stands once it shows: copied from its line's place, and from then on its own, so that it lives and fades by its own time however long its line stays, and never moves. Its place in the line's plane, its size in text
	 * units, and which adlib it is for.
	 */
	private final Shown[] adlibPlace = new Shown[LyricMotion.MAX_ADLIBS];
	private final MusicService.Background[] adlibPlaceFor = new MusicService.Background[LyricMotion.MAX_ADLIBS];
	private final float[] adlibPlaceX = new float[LyricMotion.MAX_ADLIBS];
	private final float[] adlibPlaceY = new float[LyricMotion.MAX_ADLIBS];
	private final float[] adlibPlaceScale = new float[LyricMotion.MAX_ADLIBS];
	private final float[] adlibPlaceWidth = new float[LyricMotion.MAX_ADLIBS];
	private final float[] adlibPlaceHeight = new float[LyricMotion.MAX_ADLIBS];
	private final int[] adlibPlaceWrap = new int[LyricMotion.MAX_ADLIBS];
	/** The boxes standing and the places tried for a new adlib, as {@link AdlibBoxes} takes them. */
	private final float[] standingBoxes = new float[(LyricMotion.MAX_ADLIBS + SLOTS) * AdlibBoxes.VALUES];
	private final float[] spotBoxes = new float[ADLIB_SPOTS * AdlibBoxes.VALUES];
	private final float[] oneBox = new float[AdlibBoxes.VALUES];
	private final float[] adlibFilmBox = new float[4];
	/** How visible everything is around a cut of the film, this frame. */
	private float filmFade = 1f;
	private boolean filmedNow;
	private final MusicService.Background[] adlibHomeFor = new MusicService.Background[LyricMotion.MAX_ADLIBS];
	private final int[] adlibHomeIndex = new int[LyricMotion.MAX_ADLIBS];
	private final long[] adlibHomeStart = new long[LyricMotion.MAX_ADLIBS];
	/** How much it is day now, from 0 (night) to 1, for the brightness and the shadow. */
	private float day;
	private float time;
	private long frame;
	private float dt;
	private boolean animated;
	private Vec3 eye = Vec3.ZERO;
	/** Whether the lines are in the classic glow's own face this frame: lower case in the old lyrics font, as before. */
	private boolean classicFace;

	WorldLyrics(MusicFeature music, LyricMotion motion) {
		this.music = music;
		this.motion = motion;
		for (int i = 0; i < SPRITES; i++) {
			entries[i] = new Entry();
		}
		for (int i = 0; i < SLOTS; i++) {
			lines[i] = new Shown();
		}
		for (int i = 0; i < adlibPlace.length; i++) {
			adlibPlace[i] = new Shown();
		}
	}

	/** Submits the lines with the rest of the world rendering, so whatever is in front of them occludes them. */
	public void collectWorld(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!music.isEnabled() || !music.worldLyricsShown() || !music.lyrics.get() || client.level == null || client.player == null) {
			releaseAll();
			clearLines();
			alone.clear();
			placer.reset();
			return;
		}
		motion.update();
		frame++;
		dt = clock.tick();
		time += dt;
		animated = ModFeatures.MENU.animations();
		var camera = client.gameRenderer.mainCamera();
		eye = camera.position();
		boolean hand = music.worldFont.get() == WorldFont.HANDWRITTEN;
		classicFace = !hand && music.worldEffect.get() == WorldEffect.CLASSIC;
		float size = music.worldSize.get();
		CinematicCameraFeature film = ModFeatures.CINEMATIC;
		boolean filmed = film.active();
		filmFade = filmed ? film.lyricFade() : 1f;
		filmedNow = filmed;

		if (!filmed && music.worldPlacement.get() == WorldPlacement.AUTO) {
			scoutAhead(client, camera.yRot(), camera.xRot(), size);
		}
		LyricLine line = motion.visible > 0.001f ? motion.line(motion.current) : null;
		// right after a cut only the line sung by the clock starts in the new shot, not one the motion has yet to move on from
		if (filmed && line != null && LyricCutFade.inCutFade(film.sinceCutMs()) && LyricTimes.mainIndexAt(motion.lines, music.lyricClockMs()) != motion.current) {
			line = null;
		}
		// a line is new by its index and start, so the same text sung again is a line of its own
		if (line != null && !line.text().isBlank() && (motion.lines != spawnedIn || motion.current != spawnedIndex || line.timeMs() != spawnedStart)) {
			if (!repeatsCurrent(line, motion.current, filmed) && (filmed || !keepPlace(line, motion.current))) {
				spawn(client, line, motion.current, camera.yRot(), camera.xRot(), size, filmed);
			}
		}
		if (filmed) {
			if (!film.lyricPlaced()) {
				// the film's shot found no open place for its lyric
				releaseUnused();
				return;
			}
		}
		long clockMs = music.lyricClockMs();
		float linger = music.worldLinger.get().seconds;
		for (Shown shown : lines) {
			advance(shown, shown == current && line != null && motion.current == shown.index, clockMs, linger, filmed);
		}
		if (current != null && !current.on()) {
			current = null;
		}

		day = Daylight.of(client.level);
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		// the older lines first, the sung one last
		for (Shown shown : lines) {
			if (shown != current) {
				drawShown(collector, pose, shown, hand);
			}
		}
		if (current != null && drawShown(collector, pose, current, hand) && filmed && Debug.on() && film.shotNumber() != loggedShot) {
			loggedShot = film.shotNumber();
			Debug.log("Cinematic shot {}: line drawn", loggedShot);
		}
		drawAdlibs(client, collector, pose, hand, size, camera.yRot(), camera.xRot());
		releaseUnused();
	}

	/**
	 * The same line sung again right after it (a chorus line several times in a row): the line on show stays where it is, as the line sung now, and only
	 * pulses once and starts its fill again. A cut of the film has cleared it, so there it starts at the new place.
	 *
	 * @return whether the line on show is the one sung again
	 */
	private boolean repeatsCurrent(LyricLine line, int index, boolean filmed) {
		if (current == null || !current.on() || current.leaving || filmed && current.shot != ModFeatures.CINEMATIC.shotNumber()
			|| !LyricChorus.repeats(current.text, current.endMs, line.text(), line.timeMs())) {
			return false;
		}
		spawnedIn = motion.lines;
		spawnedIndex = index;
		spawnedStart = line.timeMs();
		current.index = index;
		current.startMs = line.timeMs();
		current.endMs = index < motion.lines.size() ? LyricTimes.lineEnd(motion.lines, index) : line.timeMs();
		current.beforeGap = beforeLongGap(motion.lines, index, current.endMs);
		current.sung = 0f;
		current.lit = 0f;
		current.repeatPulse = 1f;
		repeats++;
		return true;
	}

	/**
	 * A line that is showing never moves. The song's clock can step back a little (a player reports its time late), and lyrics can come again as a new list
	 * or with their times adjusted: that made the line before a new one "new" again, so it got a new place, and the next line then took the one it left.
	 * When the line asked for is already standing, it stays where it is and is the sung line again.
	 *
	 * @return whether the line was standing
	 */
	private boolean keepPlace(LyricLine line, int index) {
		for (Shown shown : lines) {
			if (!shown.on() || shown.pushed || shown.leaving || !LyricTimes.sameLine(shown.text, shown.startMs, line.text(), line.timeMs())) {
				continue;
			}
			spawnedIn = motion.lines;
			spawnedIndex = index;
			spawnedStart = line.timeMs();
			shown.index = index;
			shown.startMs = line.timeMs();
			shown.endMs = index < motion.lines.size() ? LyricTimes.lineEnd(motion.lines, index) : line.timeMs();
			if (shown != current) {
				if (current != null) {
					current.older = true;
					current.olderFor = 0f;
					current.sung = Float.MAX_VALUE;
					current.lit = 1f;
				}
				shown.older = false;
				shown.olderFor = 0f;
				shown.leaving = false;
				current = shown;
			}
			return true;
		}
		return false;
	}

	/**
	 * The cinematic camera cut to a new shot: the lines on screen belong to the shot before and are gone at once, so that no frame of the new shot shows them where they stood; the line being sung appears in the new shot, at a spot of its own there.
	 */
	void cut() {
		if (current != null) {
			cutLines++;
		}
		for (Shown shown : lines) {
			shown.clear();
		}
		current = null;
		// what stood in the old shot is gone with it, adlibs too: those still sung come back at a place in the new one
		alone.clear();
		for (int i = 0; i < adlibPlaceFor.length; i++) {
			adlibPlaceFor[i] = null;
			adlibHomeFor[i] = null;
			adlibHome[i] = null;
		}
		// the sung line starts again in the new shot
		spawnedIn = null;
	}

	/** The last shot whose line was logged as drawn. */
	private int loggedShot = -1;

	/** Lines carried over cuts, for the test log. */
	int cutLines;

	/** Lines sung again that stayed, for the test log. */
	int repeats;

	private void clearLines() {
		for (Shown shown : lines) {
			shown.clear();
		}
		current = null;
		spawnedIn = null;
	}

	/**
	 * A new line starts: the sung one becomes an older line that stays a moment, dimming, before it fades (the oldest goes first when there are
	 * too many), and the new one gets its spot in the world. In the cinematic camera the new line gets a spot of its own in the shot (see
	 * {@link #placeInShot}) and the one before fades out.
	 */
	private void spawn(Minecraft client, LyricLine line, int index, float viewYaw, float viewPitch, float size, boolean filmed) {
		List<LyricLine> all = motion.lines;
		// the lines of another song (or of other lyrics) make way for the first line of this one
		if (all != spawnedIn) {
			for (Shown shown : lines) {
				if (shown.on() && shown != current) {
					shown.pushed = true;
					shown.leaving = true;
				}
			}
			if (current != null) {
				current.pushed = true;
				current.leaving = true;
				current = null;
			}
		}
		spawnedIn = all;
		spawnedIndex = index;
		spawnedStart = line.timeMs();
		if (current != null) {
			current.older = true;
			current.olderFor = 0f;
			current.sung = Float.MAX_VALUE;
			current.lit = 1f;
			if (filmed) {
				// in the cinematic camera at most two lines show: the new one and the one before, which fades out where it stands as the new one
				// begins; anything older goes at once
				for (Shown shown : lines) {
					if (shown.on() && shown != current) {
						shown.leaving = true;
						shown.pushed = true;
					}
				}
				if (!current.leaving) {
					current.leaving = true;
					current.easeOut = true;
				}
			}
			// the dots of a break are no line of the song: they leave as the next line comes, and never stay among the lines before
			current.leaving |= LyricBreaks.MARK.equals(current.text);
		}
		pushOut(filmed ? MOST_OLDER_FILMED : MOST_OLDER);
		Shown fresh = freeSlot();
		fresh.clear();
		fresh.index = index;
		fresh.startMs = line.timeMs();
		fresh.endMs = index < all.size() ? LyricTimes.lineEnd(all, index) : line.timeMs();
		fresh.beforeGap = beforeLongGap(all, index, fresh.endMs);
		fresh.text = line.text();
		// right after a cut the cut's own fade brings it in, with nothing on top of it
		fresh.appear = !animated || filmed && LyricCutFade.inCutFade(ModFeatures.CINEMATIC.sinceCutMs()) ? 1f : 0f;
		fresh.shot = filmed ? ModFeatures.CINEMATIC.shotNumber() : 0;
		current = fresh;
		if (filmed) {
			fitRows(client, fresh.text, WRAP_WIDTH);
			fresh.wrap = fitWrap;
			fresh.textScale = fitScale;
			placeInShot(client, ModFeatures.CINEMATIC, fresh, size, music.lyricClockMs());
			makeAdlibsWay(fresh);
		} else {
			place(client, fresh, line.text(), 1, viewYaw, viewPitch, size);
			if (!fresh.on()) {
				current = null;
			} else {
				makeWay(fresh);
			}
		}
	}

	/**
	 * In the cinematic camera a new line gets a spot of its own, fixed for its whole life: the shot's spot when it is free, else below or above the
	 * line still fading out. A spot counts only when the line's whole box (its rows, adlibs and glow) stays inside the safe part of the picture
	 * along the shot's planned camera path for all of its time, clear of the song card and the character and not behind blocks (see
	 * {@link CinematicCameraFeature#lyricMargin}). Without such a spot it stands beside the player the camera looks at (see
	 * {@link #besideFocus}); it is never left out, and never moves.
	 */
	private void placeInShot(Minecraft client, CinematicCameraFeature film, Shown fresh, float size, long clockMs) {
		// the line before: the latest one still standing; a copy left from the shot before a cut (same line, same time) is already making way
		Shown older = null;
		for (Shown shown : lines) {
			if (shown.on() && shown != fresh && !shown.pushed && (older == null || shown.startMs > older.startMs)) {
				older = shown;
			}
		}
		float unit = film.lyricUnit() * size * FILM_SCALE;
		int rows = rowsOf(client, fresh.text, fresh.wrap);
		float top = LyricSprites.boundsTop(rows);
		float bottom = LyricSprites.boundsBottom(rows) + adlibRoom(fresh.index) * ROW;
		float total = (bottom - top) / ROW;
		// the box's middle below the line's, in text units: the adlibs' room is below it
		float shift = (top + bottom) / 2f;
		double halfWidth = LyricSprites.boundsWidth(lineWidth(client, fresh)) / 2.0 * unit;
		double halfHeight = total * ROW / 2.0 * unit;
		float seconds = Math.max(0f, (fresh.endMs - clockMs) / 1000f) + FILM_HOLD_MS / 1000f + OUT_SECONDS;
		double anchorX = film.lyricX();
		double anchorY = film.lyricY();
		double anchorZ = film.lyricZ();
		double dx = anchorX - eye.x;
		double dz = anchorZ - eye.z;
		double flat = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
		double rightX = -dz / flat;
		double rightZ = dx / flat;
		// the candidates: the shot's spot when the line before leaves it free (else below that line, else above it), and a little above the
		// spot, clear of the ground or a block right below it
		int count = 0;
		if (older == null) {
			rises[count++] = 0f;
			rises[count++] = -(total * ROW / 2f + FILM_RISE_GAP);
		} else {
			// the boxes' middles apart by both half heights and a gap; each box sits its own shift below its spot (its adlibs' room is below)
			float apart = (boxRows(client, older) + total) * ROW / 2f + FILM_RISE_GAP;
			float olderMiddle = older.rise + older.boxShift;
			if (Math.abs(shift - olderMiddle) >= apart) {
				rises[count++] = 0f;
			}
			rises[count++] = olderMiddle + apart - shift;
			rises[count++] = olderMiddle - apart - shift;
		}
		String misses = "";
		// the line before may stand elsewhere than the shot's spot (beside the player, in a push-in): its box in the picture is what must stay clear
		boolean olderVisible = older != null && film.lyricBoxNow(older.x, older.boxY, older.z, older.boxRightX, older.boxRightZ, older.boxHalfWidth,
			older.boxHalfHeight, olderBox);
		for (int k = 0; k < count; k++) {
			double centerY = anchorY - (rises[k] + shift) * unit;
			float margin = film.lyricMargin(anchorX, centerY, anchorZ, rightX, rightZ, halfWidth, halfHeight, seconds);
			if (margin >= 0f && onVisibleAdlib(film, anchorX, centerY, anchorZ, rightX, rightZ, halfWidth, halfHeight)) {
				if (Debug.on()) {
					misses += (misses.isEmpty() ? "" : "; ") + "on an adlib";
				}
				continue;
			}
			if (margin >= 0f && olderVisible && !clearOf(film, older, anchorX, centerY, anchorZ, rightX, rightZ, halfWidth, halfHeight, total)) {
				if (Debug.on()) {
					misses += (misses.isEmpty() ? "" : "; ") + "on the line before";
				}
				continue;
			}
			if (margin >= 0f) {
				standAt(fresh, anchorX, anchorY, anchorZ, unit, shift, rightX, rightZ, halfWidth, halfHeight);
				fresh.rise = rises[k];
				fresh.boxY = centerY;
				Debug.log("Cinematic line {}: {} shot, in the world {} rows {} the shot's spot, least margin to the frame edge {}", fresh.index,
					film.shotName(), Math.round(Math.abs(rises[k]) / ROW * 10f) / 10f, rises[k] >= 0f ? "below" : "above", Math.round(margin * 100f) / 100f);
				return;
			}
			if (Debug.on()) {
				misses += (misses.isEmpty() ? "" : "; ") + film.lyricMiss();
			}
		}
		// no spot by the shot's lyric place stays in the picture: one beside the player the camera looks at, which stays in the frame, made a
		// little smaller when it spawns if that is what it takes (never afterwards)
		besideFocus(film, fresh, older, unit, shift, total, halfWidth / unit, seconds, misses);
	}

	/**
	 * Spots beside the shot's focus, in blocks, tried in this order: the room between the player's side and the line's near edge (so the line is
	 * never on the player), the height of the line's spot above the player's feet, and how far behind the player (seen from the camera) it
	 * stands; each to the right and the left.
	 */
	private static final double[] FOCUS_CLEARS = {0.8, 1.8};
	private static final double[] FOCUS_HEIGHTS = {1.8, 1.0, 2.6};
	private static final double[] FOCUS_DEPTHS = {0.0, 2.5};
	private static final double[] SIDES = {1.0, -1.0};
	/** The sizes tried for a line by the focus: its own, then a little smaller (only when it spawns, never afterwards). */
	private static final float[] FOCUS_SIZES = {1f, 0.85f, 0.7f, 0.55f};
	/** The sizes tried after those, when a line fits nowhere else: only what it takes. */
	private static final float[] LAST_SIZES = {0.7f, 0.55f, 0.45f, 0.35f, 0.25f};
	/** The margin a spot counts with when it would stand on the line before: no room at all. */
	private static final float ON_THE_LINE_BEFORE = -1f;
	/** The least room between two lines on screen, in picture units. */
	private static final float LINE_GAP = 0.04f;
	private final float[] olderBox = new float[4];
	private final float[] freshBox = new float[4];

	/**
	 * Places a line in the world beside the shot's focus, where the camera looks the whole shot: the first spot (and size) that stays in the
	 * picture along the path and clear of the line before. If none does, spots taken from the picture itself (in front of the camera, as far as
	 * the player), which also work when the camera looks down at the player. If none fully stays inside, the one with the most room. It never
	 * moves afterwards.
	 *
	 * @param halfWidthUnits half the line's width, in text units
	 */
	private void besideFocus(CinematicCameraFeature film, Shown fresh, Shown older, float unit, float shift, float total, double halfWidthUnits,
							 float seconds, String misses) {
		double focusX = film.focusX();
		double focusY = film.focusY();
		double focusZ = film.focusZ();
		double dx = focusX - eye.x;
		double dz = focusZ - eye.z;
		double flat = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
		double rightX = -dz / flat;
		double rightZ = dx / flat;
		double forwardX = dx / flat;
		double forwardZ = dz / flat;
		olderShown = older != null && film.lyricBoxNow(older.x, older.boxY, older.z, older.boxRightX, older.boxRightZ, older.boxHalfWidth,
			older.boxHalfHeight, olderBox);
		olderLine = older;
		best = -Float.MAX_VALUE;
		bestMiss = "";
		// a push-in narrows the picture toward the player: spots near them first, a little smaller or with a shorter push-in if need be
		if (film.pushingIn() && pushIn(film, fresh, older, unit, shift, total, halfWidthUnits, seconds, misses)) {
			return;
		}
		bestSpot[0] = focusX + rightX * (halfWidthUnits * unit + FOCUS_CLEARS[0]);
		bestSpot[1] = focusY + FOCUS_HEIGHTS[0];
		bestSpot[2] = focusZ + rightZ * (halfWidthUnits * unit + FOCUS_CLEARS[0]);
		bestSpot[3] = rightX;
		bestSpot[4] = rightZ;
		bestUnit = unit;
		if (searchSpots(film, fresh, unit, shift, total, halfWidthUnits, seconds, misses, focusX, focusY, focusZ, rightX, rightZ, forwardX, forwardZ)) {
			return;
		}
		if (olderShown) {
			// nothing fits clear of the line before: it leaves quickly to make way, and the spots are tried again without it
			olderShown = false;
			if (searchSpots(film, fresh, unit, shift, total, halfWidthUnits, seconds, misses, focusX, focusY, focusZ, rightX, rightZ, forwardX, forwardZ)) {
				makeWayFor(fresh, older);
				return;
			}
		}
		if (fitAtAnySize(film, fresh, unit, shift, total, halfWidthUnits, seconds)) {
			if (older != null) {
				makeWayFor(fresh, older);
			}
			return;
		}
		standAt(fresh, bestSpot[0], bestSpot[1], bestSpot[2], bestUnit, shift, bestSpot[3], bestSpot[4], halfWidthUnits * bestUnit,
			total * ROW / 2.0 * bestUnit);
		// it may stand near the line before, which leaves quickly to make way
		if (older != null) {
			makeWayFor(fresh, older);
		}
		Debug.log("Cinematic line {}: {} shot, in the world beside the player with the most room ({}, {}), no spot stays fully inside: {}",
			fresh.index, film.shotName(), Math.round(best * 100f) / 100f, bestMiss, misses);
	}

	/**
	 * The last way to keep a whole line in the picture: free in front of the camera, away from everything, as much smaller as it takes and no more. A cut off
	 * line is the one thing that must not happen, so this comes before the spot with the most room.
	 *
	 * @return whether the line stands fully inside the picture
	 */
	private boolean fitAtAnySize(CinematicCameraFeature film, Shown fresh, float unit, float shift, float total, double halfWidthUnits, float seconds) {
		olderShown = false;
		for (float size : LAST_SIZES) {
			float sized = unit * size;
			double fit = film.fitDepthFactor(halfWidthUnits * sized, PICTURE_FIT_SHARE);
			for (int i = 0; i < PICTURE_SPOTS.length; i += 2) {
				film.pictureSpot(PICTURE_SPOTS[i], PICTURE_SPOTS[i + 1], fit, pictureSpot);
				double anchorY = pictureSpot[1] + shift * sized;
				if (tried(film, fresh, pictureSpot[0], anchorY, pictureSpot[2], pictureSpot[3], pictureSpot[4], sized, shift, total, halfWidthUnits, seconds)) {
					Debug.log("Cinematic line {}: {} shot, in front of the camera at size {}, the only way to keep it whole", fresh.index, film.shotName(), size);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Tries the spots beside the shot's focus, then the spots taken from the picture,
 each a little smaller if need be: the first that stays fully
	 * in the picture (and clear of the line before, while that counts) is taken.
	 *
	 * @return whether the line stands at one
	 */
	private boolean searchSpots(CinematicCameraFeature film, Shown fresh, float unit, float shift, float total, double halfWidthUnits, float seconds,
								String misses, double focusX, double focusY, double focusZ, double rightX, double rightZ, double forwardX, double forwardZ) {
		for (float size : FOCUS_SIZES) {
			float sized = unit * size;
			double halfWidth = halfWidthUnits * sized;
			for (double depth : FOCUS_DEPTHS) {
				for (double clear : FOCUS_CLEARS) {
					for (double height : FOCUS_HEIGHTS) {
						for (double sign : SIDES) {
							double side = sign * (halfWidth + clear);
							double x = focusX + rightX * side + forwardX * depth;
							double z = focusZ + rightZ * side + forwardZ * depth;
							if (tried(film, fresh, x, focusY + height, z, rightX, rightZ, sized, shift, total, halfWidthUnits, seconds)) {
								Debug.log("Cinematic line {}: {} shot, in the world beside the player at {} blocks, size {}, least margin to the frame edge "
									+ "{} (the shot's spot did not stay inside: {})", fresh.index, film.shotName(), Math.round(side * 10) / 10.0, size,
									Math.round(best * 100f) / 100f, misses);
								return true;
							}
						}
					}
				}
			}
		}
		for (float size : FOCUS_SIZES) {
			float sized = unit * size;
			// at least so deep that the line takes only part of the picture: in a close shot it is wider than the whole picture at the player
			double fit = film.fitDepthFactor(halfWidthUnits * sized, PICTURE_FIT_SHARE);
			for (double depthFactor : PICTURE_DEPTHS) {
				for (int i = 0; i < PICTURE_SPOTS.length; i += 2) {
					film.pictureSpot(PICTURE_SPOTS[i], PICTURE_SPOTS[i + 1], Math.max(depthFactor, fit), pictureSpot);
					// the spot is the line's middle; it stands with its anchor above that
					double anchorY = pictureSpot[1] + shift * sized;
					if (tried(film, fresh, pictureSpot[0], anchorY, pictureSpot[2], pictureSpot[3], pictureSpot[4], sized, shift, total, halfWidthUnits, seconds)) {
						Debug.log("Cinematic line {}: {} shot, in the world in front of the camera at {} {} of the picture, size {}, least margin to the "
							+ "frame edge {} (the shot's spot did not stay inside: {})", fresh.index, film.shotName(), PICTURE_SPOTS[i], PICTURE_SPOTS[i + 1],
							size, Math.round(best * 100f) / 100f, misses);
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The spot found for a line in a push-in: its middle, its right direction, its size and the share of the push-in kept. */
	private final double[] pushSpot = new double[7];

	/**
	 * Places a line during a push-in where it stays fully in the picture while it shows (see {@link CinematicCameraFeature#pushInSpot}).
	 *
	 * @return whether such a spot was found
	 */
	private boolean pushIn(CinematicCameraFeature film, Shown fresh, Shown older, float unit, float shift, float total, double halfWidthUnits,
						   float seconds, String misses) {
		if (!film.pushInSpot(halfWidthUnits * unit, total * ROW / 2.0 * unit, seconds, pushSpot)) {
			return false;
		}
		float sized = unit * (float) pushSpot[5];
		// the spot is the line's middle; it stands with its anchor above that
		double anchorY = pushSpot[1] + shift * sized;
		if (!tried(film, fresh, pushSpot[0], anchorY, pushSpot[2], pushSpot[3], pushSpot[4], sized, shift, total, halfWidthUnits, seconds)) {
			// in the picture, but on the line before, which leaves quickly to make way
			standAt(fresh, pushSpot[0], anchorY, pushSpot[2], sized, shift, pushSpot[3], pushSpot[4], halfWidthUnits * sized, total * ROW / 2.0 * sized);
			if (older != null) {
				makeWayFor(fresh, older);
			}
		}
		Debug.log("Cinematic line {}: push-in, in the world by the player, size {}, push-in kept {} (the shot's spot did not stay inside: {})",
			fresh.index, Math.round(pushSpot[5] * 100.0) / 100.0, Math.round(pushSpot[6] * 100.0) / 100.0, misses);
		return true;
	}

	/**
	 * Places across and up in the picture (-1 to 1) for a line in front of the camera: upper right, upper left, right, top middle, then nearer the
	 * middle, which stays in the picture while the camera pushes in.
	 */
	private static final float[] PICTURE_SPOTS = {0.4f, 0.4f, -0.4f, 0.4f, 0.45f, -0.05f, 0f, 0.5f, 0.25f, 0.3f, -0.25f, 0.3f, 0.3f, 0.05f};
	/** How far in front of the camera those spots are, as a share of the player's distance: farther ones move less with the camera. */
	private static final double[] PICTURE_DEPTHS = {1.0, 1.4, 2.0};
	/** The share of the picture's half width half a line may take at a picture spot. */
	private static final double PICTURE_FIT_SHARE = 0.35;
	/** The best spot measured so far by {@link #tried}: its margin, x, y (the anchor), z, its right direction, and size. Reused. */
	private float best;
	private final double[] bestSpot = new double[5];
	private float bestUnit;
	/** Why the best spot still missed, for the debug log. */
	private String bestMiss = "";
	private final double[] pictureSpot = new double[5];
	private boolean olderShown;
	/** The line before, which a spot beside the player must stay clear of while {@link #olderShown}. */
	private Shown olderLine;

	/**
	 * @return whether a new line's box stays clear of the line before on the screen along the camera's path over the next moments, by half a line's
	 *         height: the one before is still fading there
	 */
	/** No spot is clear of the line before: it leaves quickly, and the new line comes in where it stood once it has dimmed (the new line wins). */
	private static void makeWayFor(Shown fresh, Shown older) {
		older.leaving = true;
		older.pushed = true;
		fresh.waitFor = older;
	}

	private static boolean clearOf(CinematicCameraFeature film, Shown before, double x, double y, double z, double rightX, double rightZ,
								   double halfWidth, double halfHeight, float rows) {
		return film.lyricLinesApart(x, y, z, rightX, rightZ, halfWidth, halfHeight, before.x, before.boxY, before.z, before.boxRightX, before.boxRightZ,
			before.boxHalfWidth, before.boxHalfHeight, FILM_CLEAR_SECONDS, FILM_GAP_ROWS / Math.max(1f, rows));
	}

	/**
	 * Measures a spot for a line along the shot's path, counting no room at all where it would stand on the line before, and keeps it if it has the
	 * most room so far.
	 *
	 * @return whether it stays fully inside the picture: then the line stands there
	 */
	private boolean tried(CinematicCameraFeature film, Shown fresh, double x, double anchorY, double z, double rightX, double rightZ, float sized,
						  float shift, float total, double halfWidthUnits, float seconds) {
		double halfWidth = halfWidthUnits * sized;
		double halfHeight = total * ROW / 2.0 * sized;
		double centerY = anchorY - shift * sized;
		float margin = film.lyricMargin(x, centerY, z, rightX, rightZ, halfWidth, halfHeight, seconds);
		if (olderShown && olderLine != null && !clearOf(film, olderLine, x, centerY, z, rightX, rightZ, halfWidth, halfHeight, total)) {
			margin = Math.min(margin, ON_THE_LINE_BEFORE);
		}
		if (margin >= 0f && onVisibleAdlib(film, x, centerY, z, rightX, rightZ, halfWidth, halfHeight)) {
			margin = Math.min(margin, ON_THE_LINE_BEFORE);
		}
		if (margin >= 0f) {
			best = margin;
			standAt(fresh, x, anchorY, z, sized, shift, rightX, rightZ, halfWidth, halfHeight);
			return true;
		}
		if (margin > best) {
			best = margin;
			bestMiss = film.lyricMiss();
			bestSpot[0] = x;
			bestSpot[1] = anchorY;
			bestSpot[2] = z;
			bestSpot[3] = rightX;
			bestSpot[4] = rightZ;
			bestUnit = sized;
		}
		return false;
	}

	/** Places a line in the world at a spot and size, with its box for the next line to keep clear of, facing the eye. */
	private void standAt(Shown shown, double x, double anchorY, double z, float unit, float shift, double rightX, double rightZ, double halfWidth,
						 double halfHeight) {
		shown.rise = 0f;
		shown.x = x;
		shown.y = anchorY;
		shown.z = z;
		shown.unit = unit;
		shown.boxY = anchorY - shift * unit;
		shown.boxShift = shift;
		shown.boxHalfWidth = halfWidth;
		shown.boxHalfHeight = halfHeight;
		shown.boxRightX = rightX;
		shown.boxRightZ = rightZ;
		face(shown);
	}

	/** @return whether two boxes in picture units (left, bottom, right, top) come closer than a gap */
	private static boolean closer(float[] a, float[] b, float gap) {
		return a[0] < b[2] + gap && a[2] > b[0] - gap && a[1] < b[3] + gap && a[3] > b[1] - gap;
	}

	/** @return the height of a line with the room for its adlibs, in rows */
	private float boxRows(Minecraft client, Shown shown) {
		int rows = rowsOf(client, shown.text, shown.wrap);
		return (LyricSprites.boundsBottom(rows) - LyricSprites.boundsTop(rows)) / ROW + adlibRoom(shown.index);
	}

	/** Turns a line sideways to face the eye, once when it spawns; it stands upright and level. */
	private void face(Shown shown) {
		double dx = shown.x - eye.x;
		double dz = shown.z - eye.z;
		shown.facing = (float) Math.atan2(-dx, -dz);
		shown.sky = false;
	}

	/**
	 * @return the room a line keeps below it for its adlibs, in rows of the line: the gap and its first few adlibs at their usual size, one row
	 *         each, or two for one too wide for a row (as {@link #plan} breaks it); none for a line without adlibs. Kept from when the line is
	 *         laid out, so an adlib never moves it, and measured once per line.
	 */
	private float adlibRoom(int index) {
		LyricLine line = motion.line(index);
		if (line == null || line.backgrounds().isEmpty() || !music.adlibsShown()) {
			return 0f;
		}
		if (line == roomLine) {
			return roomRows;
		}
		int count = Math.min(line.backgrounds().size(), ADLIBS_BELOW);
		float rowHeight = LyricSprites.boundsBottom(1) - LyricSprites.boundsTop(1);
		var font = Minecraft.getInstance().font;
		for (int k = 0; k < count; k++) {
			float usualWidth = font.width(Ui.lyricSequence(line.backgrounds().get(k).text())) * AdlibLayout.SCALE;
			int rows = usualWidth > ADLIB_LEAST_ROOM ? AdlibLayout.MOST_ROWS : 1;
			roomHeights[k] = (LyricSprites.boundsBottom(rows) - LyricSprites.boundsTop(rows)) * AdlibLayout.SCALE;
		}
		roomLine = line;
		roomRows = AdlibLayout.room(rowHeight, AdlibLayout.WORLD_GAP_SHARE, roomHeights, count) / ROW;
		return roomRows;
	}

	// the room kept below the line measured last, for its adlibs
	private LyricLine roomLine;
	private float roomRows;
	private final float[] roomHeights = new float[ADLIBS_BELOW];

	/** Lets the oldest lines go, quickly, until at most {@code most} older lines stay. */
	private void pushOut(int most) {
		while (true) {
			int staying = 0;
			Shown oldest = null;
			for (Shown shown : lines) {
				if (shown.on() && shown.older && !shown.pushed) {
					staying++;
					if (oldest == null || shown.startMs < oldest.startMs || shown.startMs == oldest.startMs && shown.index < oldest.index) {
						oldest = shown;
					}
				}
			}
			if (staying <= most || oldest == null) {
				return;
			}
			oldest.pushed = true;
			oldest.leaving = true;
		}
	}

	/** @return a free place for a new line, or the faintest older line's, which then goes at once */
	private Shown freeSlot() {
		Shown faintest = null;
		for (Shown shown : lines) {
			if (!shown.on()) {
				return shown;
			}
			if (shown != current && (faintest == null || shown.alpha() < faintest.alpha())) {
				faintest = shown;
			}
		}
		return faintest != null ? faintest : lines[0];
	}

	/** @return whether the line is the last of the song or followed by a long instrumental part, after which it stays a moment longer */
	private static boolean beforeLongGap(List<LyricLine> all, int index, long endMs) {
		for (int i = index + 1; i < all.size(); i++) {
			LyricLine next = all.get(i);
			if (LyricTimes.main(next) && !next.adlibOnly()) {
				return next.timeMs() - endMs >= LONG_GAP_MS;
			}
		}
		return true;
	}

	/** @return how many rows a line is broken into at a width */
	private static int rowsOf(Minecraft client, String text, int wrapWidth) {
		return Math.max(1, Math.min(LyricSprites.MAX_ROWS, rowsFor(client, text, wrapWidth).size()));
	}

	/**
	 * Finds the spot of a line from its size: the angles it covers follow from its width and rows at the placement's size. In Auto the scout's
	 * pick decides between the sky and around the player and which spot there; Manual takes the place, spread and distance set.
	 */
	private void place(Minecraft client, Shown shown, String text, float scale, float viewYaw, float viewPitch, float size) {
		boolean auto = music.worldPlacement.get() == WorldPlacement.AUTO;
		boolean sky;
		float spread;
		float reach;
		if (auto) {
			if (shown.index >= 0 && !scout.fits(motion.lines, shown.index, shown.startMs, eye, viewYaw)) {
				// the line came without warning (a seek), or the player turned or moved since: scouted now, at once
				beginScout(client, text, shown.index, shown.startMs, viewYaw, viewPitch, size);
			}
			scout.finish(client);
			sky = scout.sky();
			int kind = scout.bestKind();
			if (!sky && kind >= 0) {
				placer.preferNext(kind);
			}
			spread = WorldLyricScout.SPREAD;
			reach = 1f;
		} else {
			sky = music.worldPlace.get() == WorldPlace.SKY;
			spread = music.worldReach.get();
			reach = music.worldDistance.get();
		}
		float perBlock = fittedSize(client, shown, text, (sky ? SKY_UNIT : NEAR_UNIT) * size * scale, shown == alone ? 0f : adlibRoom(shown.index));
		float halfWidth = halfAngles[0];
		float halfHeight = halfAngles[1];
		avoidVisible(shown);
		float glow = (float) Math.toDegrees(Math.atan(LyricSprites.GLOW_ROOM * perBlock));
		placer.place(client, eye, viewYaw, viewPitch, sky, !auto, halfWidth, halfHeight, glow, spread, reach, spot);
		if (Debug.on()) {
			// relative to the eye, so no world position is logged
			Debug.log("World lyric {}: {} at {} blocks, size {}, offset {} {} {}", shown.index, spot.open ? (sky ? "sky" : "near") : "skipped",
				spot.open ? Math.round(spot.distance * 10.0) / 10.0 : 0.0, spot.shrink, Math.round((spot.x - eye.x) * 10.0) / 10.0,
				Math.round((spot.y - eye.y) * 10.0) / 10.0, Math.round((spot.z - eye.z) * 10.0) / 10.0);
		}
		shown.halfWidth = halfWidth * spot.shrink;
		shown.halfHeight = halfHeight * spot.shrink;
		if (!spot.open) {
			// no free spot (a cave, a small room, a dense forest): this line is left out of the world
			shown.text = null;
			return;
		}
		shown.x = spot.x;
		shown.y = spot.y;
		shown.z = spot.z;
		shown.facing = spot.facing;
		shown.unit = perBlock * (float) spot.distance * spot.shrink;
		shown.sky = sky;
	}

	/**
	 * @return the size of a line in blocks per text unit; its rows and text size are chosen so it fits on one side of the view outside the band
	 *         around the crosshair (see {@link #fitRows}), and go to the line, its angles into {@link #halfAngles}
	 * @param shown     the line, which keeps the width its rows break at, or null when only measured
	 * @param adlibRows the room kept below the line for its adlibs, in rows
	 */
	private float fittedSize(Minecraft client, Shown shown, String text, float perBlock, float adlibRows) {
		float most = WorldLyricPlacer.mostHalfWidth(client);
		float allowed = (float) (2.0 * Math.tan(Math.toRadians(most)) / perBlock);
		fitRows(client, text, Math.max(LEAST_WRAP, allowed));
		if (shown != null) {
			shown.wrap = fitWrap;
			shown.textScale = fitScale;
		}
		angularSize(client, text, perBlock, fitWrap, fitScale, adlibRows);
		return perBlock;
	}

	/**
	 * Chooses how a line is broken and how large its text is, so it fits a width (in text units at full size) and the sprite: two balanced rows
	 * at full size; else two rows a little smaller, down to {@link #LEAST_SHRINK}; else three rows, smaller as needed. A line is never cut: a very
	 * long one only gets smaller. The choice goes into {@link #fitWrap} and {@link #fitScale}.
	 */
	private void fitRows(Minecraft client, String text, float allowed) {
		float limit = Math.min(WRAP_WIDTH, allowed);
		for (int most = PREFERRED_ROWS; most <= LyricSprites.MAX_ROWS; most++) {
			float least = most == LyricSprites.MAX_ROWS ? LEAST_TEXT_SCALE : LEAST_SHRINK;
			for (float scale = 1f; scale >= least - 1e-4f; scale -= SHRINK_STEP) {
				int wrapWidth = Math.round(limit / scale);
				if (rowsFor(client, text, wrapWidth).size() <= most) {
					fitWrap = wrapWidth;
					fitScale = scale;
					return;
				}
			}
		}
		fitWrap = Math.round(limit / LEAST_TEXT_SCALE);
		fitScale = LEAST_TEXT_SCALE;
	}

	/** Writes half the width and height a text covers, in degrees, at a size in blocks per text unit, into {@link #halfAngles}. */
	private void angularSize(Minecraft client, String text, float perBlock, int wrapWidth, float textScale, float adlibRows) {
		// the rows the line will have, and the widest of them, measured with the game's font, as the sprite places its glyphs
		List<String> wrapped = rowsFor(client, text, wrapWidth);
		float rows = Math.max(1, Math.min(LyricSprites.MAX_ROWS, wrapped.size())) + adlibRows;
		float width = 0f;
		for (String row : wrapped) {
			width = Math.max(width, client.font.width(Ui.lyricSequence(row)));
		}
		halfAngles[0] = (float) Math.toDegrees(Math.atan(width * textScale / 2f * perBlock));
		halfAngles[1] = (float) Math.toDegrees(Math.atan(rows * ROW / 2f * perBlock));
	}

	/**
	 * Scouts ahead in Auto: from shortly before the next line starts, a few spots per frame are judged, so the line has its spot without a frame
	 * time spike when it starts.
	 */
	private void scoutAhead(Minecraft client, float viewYaw, float viewPitch, float size) {
		List<LyricLine> all = motion.lines;
		long clockMs = music.lyricClockMs();
		int next = LyricTimes.nextMainIndex(all, clockMs);
		if (next < 0 || all.get(next).timeMs() - clockMs > WorldLyricScout.LEAD_MS) {
			return;
		}
		LyricLine line = all.get(next);
		if (!scout.scouting(all, next, line.timeMs())) {
			beginScout(client, line.text(), next, line.timeMs(), viewYaw, viewPitch, size);
		}
		scout.step(client);
	}

	/** Starts scouting a line from the view now, keeping clear of the lines still visible. */
	private void beginScout(Minecraft client, String text, int index, long startMs, float viewYaw, float viewPitch, float size) {
		fittedSize(client, null, text, NEAR_UNIT * size, adlibRoom(index));
		scout.begin(motion.lines, index, startMs, client, eye, viewYaw, viewPitch, halfAngles[0], halfAngles[1], placer.lastKind(),
			music.worldTextArgb());
		scout.clearAvoided();
		for (Shown shown : lines) {
			if (shown.on() && !shown.leaving) {
				directionOf(shown, direction);
				scout.avoid(direction[0], direction[1], shown.halfWidth, shown.halfHeight);
			}
		}
	}

	/** Tells the placer the lines still visible, the newest marked, so a new line keeps clear of them and goes below it or across from it. */
	private void avoidVisible(Shown placing) {
		placer.clearAvoided();
		Shown newest = null;
		for (Shown shown : lines) {
			if (shown != placing && shown.on() && !shown.leaving && (newest == null || shown.startMs > newest.startMs)) {
				newest = shown;
			}
		}
		for (Shown shown : lines) {
			if (shown != placing && shown.on() && !shown.leaving) {
				directionOf(shown, direction);
				placer.avoid(direction[0], direction[1], shown.halfWidth, shown.halfHeight, shown == newest);
			}
		}
		// an adlib outlives its line: where it stands stays taken, also after the line has gone
		for (int i = 0; i < adlibPlace.length; i++) {
			if (placedAdlibVisible(i)) {
				adlibBox(i, oneBox);
				placer.avoid(oneBox[0], oneBox[1], oneBox[2], oneBox[3], false);
			}
		}
	}

	/** @return whether a slot's adlib has its place and is still visible there */
	private boolean placedAdlibVisible(int slot) {
		LyricMotion.Adlib adlib = motion.adlibs[slot];
		return adlibPlaceFor[slot] != null && adlib.part == adlibPlaceFor[slot] && adlib.alpha > VISIBLE_ALPHA;
	}

	/** Writes a placed adlib's box as seen from the eye, its real size after scaling. */
	private void adlibBox(int slot, float[] out) {
		adlibBox(slot, out, 0);
	}

	/** Writes the box of a text in a line's plane, by its middle and size in text units, as seen from the eye. */
	private void textBox(Shown place, float x, float y, float width, float height, float[] out, int at) {
		double side = x * place.unit;
		double px = place.x + side * Math.cos(place.facing);
		double py = place.y - (y + place.rise) * place.unit;
		double pz = place.z - side * Math.sin(place.facing);
		writeBox(px, py, pz, width * place.unit / 2f, height * place.unit / 2f, out, at);
	}

	/** Writes the box of something standing in the world, its half sizes in blocks, as seen from the eye. */
	private void writeBox(double x, double y, double z, float halfWidth, float halfHeight, float[] out, int at) {
		double dx = x - eye.x;
		double dy = y - eye.y;
		double dz = z - eye.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		double distance = Math.sqrt(flat * flat + dy * dy);
		out[at] = (float) Math.toDegrees(Math.atan2(-dx, dz));
		out[at + 1] = (float) Math.toDegrees(Math.atan2(dy, flat));
		out[at + 2] = AdlibBoxes.halfAngle(halfWidth, distance);
		out[at + 3] = AdlibBoxes.halfAngle(halfHeight, distance);
	}

	/** Writes the box of a line's own text as seen from the eye: its real size, without the room kept for adlibs, which is only taken once one stands there. */
	private void lineBox(Shown shown, float[] out, int at) {
		Minecraft client = Minecraft.getInstance();
		int rows = rowsOf(client, shown.text, shown.wrap);
		float top = LyricSprites.boundsTop(rows);
		float bottom = LyricSprites.boundsBottom(rows);
		LyricLine line = motion.line(shown.index);
		float x = line != null && line.singer() == MusicService.SINGER_TWO ? SINGER_TWO_SIDE : 0f;
		textBox(shown, x, (top + bottom) / 2f, LyricSprites.boundsWidth(lineWidth(client, shown)), bottom - top, out, at);
	}

	/** @return whether a place for a line in the film's picture would stand on an adlib that is still visible, measured in the picture */
	private boolean onVisibleAdlib(CinematicCameraFeature film, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight) {
		if (!film.lyricBoxNow(x, y, z, rightX, rightZ, halfWidth, halfHeight, freshBox)) {
			return false;
		}
		for (int i = 0; i < adlibPlace.length; i++) {
			if (placedAdlibVisible(i)) {
				Shown place = adlibPlace[i];
				double side = adlibPlaceX[i] * place.unit;
				double right = Math.cos(place.facing);
				double forward = -Math.sin(place.facing);
				if (film.lyricBoxNow(place.x + side * right, place.y - (adlibPlaceY[i] + place.rise) * place.unit, place.z + side * forward, right, forward, adlibPlaceWidth[i] * place.unit / 2f,
					adlibPlaceHeight[i] * place.unit / 2f, adlibFilmBox) && closer(freshBox, adlibFilmBox, LINE_GAP)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Only where a new line found no free spot: an adlib it would cover goes a little faster, and the line's fade in waits for it, so the two never stand over each other. */
	private void makeAdlibsWay(Shown fresh) {
		if (!fresh.on()) {
			return;
		}
		lineBox(fresh, standingBoxes, 0);
		for (int i = 0; i < adlibPlace.length; i++) {
			if (placedAdlibVisible(i)) {
				adlibBox(i, oneBox);
				if (AdlibBoxes.overlapsAny(oneBox, 0, standingBoxes, 1, 0f)) {
					motion.adlibs[i].forcedOut = true;
				}
			}
		}
	}

	/** @return whether an adlib is going faster to make room */
	private boolean adlibMakingWay() {
		for (int i = 0; i < adlibPlace.length; i++) {
			if (placedAdlibVisible(i) && motion.adlibs[i].forcedOut) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Only when no free spot was found at all (boxed in): an older line that the new one would cover goes at once, so two lines never stand over
	 * each other. Otherwise the placer keeps the new line clear of every visible line.
	 */
	private void makeWay(Shown fresh) {
		directionOf(fresh, direction);
		float freshYaw = direction[0];
		float freshElevation = direction[1];
		for (Shown shown : lines) {
			if (shown == fresh || !shown.on() || shown.leaving) {
				continue;
			}
			directionOf(shown, direction);
			if (Math.abs(Mth.wrapDegrees(direction[0] - freshYaw)) < shown.halfWidth + fresh.halfWidth
				&& Math.abs(direction[1] - freshElevation) < shown.halfHeight + fresh.halfHeight) {
				shown.pushed = true;
				shown.leaving = true;
			}
		}
		makeAdlibsWay(fresh);
	}

	/** Writes the direction from the eye to a line into {@code out}: its yaw and its height above the horizon, in degrees. */
	private void directionOf(Shown shown, float[] out) {
		double dx = shown.x - eye.x;
		double dy = shown.y - eye.y;
		double dz = shown.z - eye.z;
		out[0] = (float) Math.toDegrees(Math.atan2(-dx, dz));
		out[1] = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	/**
	 * Moves a line through its life: it fades in and is sung; an older line dims while it stays, then fades out (one pushed out by a newer line
	 * quickly); the last line before a long instrumental part leaves a moment after its last word, and in the cinematic camera every line does.
	 */
	private void advance(Shown shown, boolean sung, long clockMs, float linger, boolean filmed) {
		if (!shown.on()) {
			return;
		}
		shown.age += dt;
		// a leaving line only fades: it never grows brighter again, even when it had not fully come in yet
		if (!shown.leaving && !(shown == current && shown.appear <= 0f && adlibMakingWay())) {
			if (shown.waitFor != null && (!shown.waitFor.on() || shown.waitFor.alpha() < MADE_WAY)) {
				shown.waitFor = null;
			}
			if (shown.waitFor == null) {
				shown.appear = animated ? Math.min(1f, shown.appear + dt / FADE_SECONDS) : 1f;
			}
		}
		shown.repeatPulse = Math.max(0f, shown.repeatPulse - dt / REPEAT_PULSE_SECONDS);
		if (sung) {
			shown.sung = motion.sung;
			shown.lit = motion.lit;
		}
		if (shown.older && !filmed) {
			shown.olderFor += dt;
			float dimming = Math.min(1f, shown.olderFor / linger);
			float eased = animated ? dimming * dimming * (3f - 2f * dimming) : 1f;
			shown.dim = Mth.lerp(eased, 1f, OLDER_DIM);
		}
		if (!shown.leaving) {
			if (filmed) {
				// a line of the film stays a moment after its last word, then fades out where it stands
				shown.leaving = clockMs >= shown.endMs + FILM_HOLD_MS;
				shown.easeOut = shown.leaving;
			} else if (shown.older) {
				shown.leaving = shown.olderFor >= linger;
			} else {
				shown.leaving = shown.beforeGap && clockMs >= shown.endMs + HOLD_BEFORE_GAP_MS;
			}
		}
		if (shown.leaving) {
			shown.out = animated ? Math.max(0f, shown.out - dt / (shown.pushed ? PUSHED_OUT_SECONDS : shown.easeOut ? FILM_LEAVE_SECONDS : OUT_SECONDS)) : 0f;
		}
		if (shown.out <= 0f) {
			shown.clear();
		}
	}

	/** @return whether the line was drawn this frame */
	private boolean drawShown(SubmitNodeCollector collector, PoseStack pose, Shown shown, boolean hand) {
		if (!shown.on()) {
			return false;
		}
		float alpha = shown.alpha() * (shown.older || shown.leaving ? 1f : motion.visible) * filmFade;
		Entry entry = sprite(shown.text, hand, shown.wrap, shown.textScale);
		if (alpha <= 0.004f || entry.sprite == null || entry.sprite.state() != LyricSprites.State.READY) {
			return false;
		}
		Look look = look(shown);
		pose.pushPose();
		at(pose, shown);
		pose.translate(0f, filmDrift(shown), 0f);
		LyricLine line = motion.line(shown.index);
		float x = line != null && line.singer() == MusicService.SINGER_TWO ? SINGER_TWO_SIDE : 0f;
		if (LyricBreaks.is(line)) {
			// the dots of a break breathe as a group, and shrink and fade out just before the next line
			long now = music.lyricClockMs();
			float leaving = LyricDots.leaving(line, now);
			float size = LyricDots.breath(line, now) * leaving;
			pose.scale(size, size, size);
			alpha *= leaving;
		}
		float pulse = shown.repeatPulse * shown.repeatPulse;
		float glow = look.glow * (LyricChorus.at(motion.lines, shown.index) ? CHORUS_GLOW : 1f) * (1f + REPEAT_GLOW * pulse);
		drawLine(collector, pose, entry, shown.rowsSung, x, 0f, 1f, Math.min(1f, alpha * look.light * (1f + REPEAT_LIGHT * pulse)), shown.sung, shown.lit, glow, look, false);
		pose.popPose();
		return true;
	}

	/** The brightness, glow and colors of a line at its distance: fading into the fog, a little dimmer by day. One, reused for every line. */
	private static final class Look {
		float light;
		float glow;
		int text;
		int color;
	}

	private final Look shared = new Look();

	private Look look(Shown shown) {
		double away = Math.sqrt((shown.x - eye.x) * (shown.x - eye.x) + (shown.y - eye.y) * (shown.y - eye.y) + (shown.z - eye.z) * (shown.z - eye.z));
		float clearness = FogState.clearness(away);
		if (shown.sky) {
			clearness = 1f - (1f - clearness) * SKY_FOG;
		}
		float light = Mth.lerp(day, 1f, DAY_ALPHA) * Math.max(LEAST_CLEARNESS, clearness);
		float glow = Mth.lerp(day, 1f, DAY_GLOW) * clearness;
		shared.light = light;
		shared.glow = glow;
		shared.text = Ui.mix(music.worldTextArgb(), FogState.color(), (1f - clearness) * FOG_TINT);
		shared.color = music.worldGlowArgb();
		return shared;
	}

	/** Moves the pose to a line's spot, turned sideways the way it faces (upright and level: no lean, no roll), in text units. */
	private void at(PoseStack pose, Shown shown) {
		pose.translate(shown.x - eye.x, shown.y - eye.y, shown.z - eye.z);
		pose.mulPose(Axis.YP.rotation(shown.facing));
		pose.scale(shown.unit, -shown.unit, shown.unit);
		pose.translate(0f, shown.rise, 0f);
	}

	/** @return how far a line of the film is moved off its spot as it comes in or leaves, in text units (down is positive) */
	private float filmDrift(Shown shown) {
		if (!filmedNow || !animated) {
			return 0f;
		}
		if (shown.leaving) {
			float gone = 1f - shown.out;
			return -FILM_DRIFT_ROWS * ROW * gone * (2f - gone);
		}
		float ahead = 1f - shown.appear;
		return FILM_DRIFT_ROWS * ROW * ahead * ahead;
	}

	/** @return the newest line still visible, or null */
	private Shown newestVisible() {
		Shown newest = null;
		for (Shown shown : lines) {
			if (shown.on() && !shown.leaving && shown.alpha() > 0f && (newest == null || shown.startMs > newest.startMs)) {
				newest = shown;
			}
		}
		return newest;
	}

	/** @return the standing line with this index of the shown lyrics, or null */
	private Shown standing(int index) {
		if (index < 0 || motion.lines != spawnedIn) {
			return null;
		}
		for (Shown shown : lines) {
			if (shown.on() && shown.index == index) {
				return shown;
			}
		}
		return null;
	}

	/**
	 * Draws every adlib: when it begins it takes the place planned for it below the line it is sung with (or beside it), a clear gap from the line and its glow, or the next free one where something else still stands there; an adlib whose line is not
	 * standing (sung in a gap) goes by the line that was visible when it began, or gets a small spot of its own. From then on that place is its own, and it lives by its own time: it never moves, and neither stays for its line nor goes with it.
	 */
	private void drawAdlibs(Minecraft client, SubmitNodeCollector collector, PoseStack pose, boolean hand, float size, float viewYaw, float viewPitch) {
		boolean any = false;
		for (LyricMotion.Adlib adlib : motion.adlibs) {
			any |= adlib.part != null && adlib.alpha > 0.004f;
		}
		if (!any) {
			alone.clear();
			return;
		}
		for (int i = 0; i < motion.adlibs.length; i++) {
			LyricMotion.Adlib adlib = motion.adlibs[i];
			if (adlib.part == null || adlib.alpha <= 0.004f) {
				continue;
			}
			if (adlibPlaceFor[i] != adlib.part && !placeAdlib(client, i, adlib, hand, size, viewYaw, viewPitch)) {
				// it comes in when it has its place
				adlib.alpha = 0f;
				continue;
			}
			Entry entry = sprite(adlib.part.text(), hand, adlibPlaceWrap[i], 1f);
			if (entry.sprite == null || entry.sprite.state() != LyricSprites.State.READY) {
				continue;
			}
			Shown place = adlibPlace[i];
			Look look = look(place);
			pose.pushPose();
			at(pose, place);
			drawLine(collector, pose, entry, adlibSung[i], adlibPlaceX[i], adlibPlaceY[i], adlibPlaceScale[i], adlib.alpha * AdlibLayout.ALPHA * filmFade
				* look.light, adlib.sung, 1f, look.glow * ADLIB_GLOW, look, true);
			pose.popPose();
		}
	}

	/**
	 * Gives an adlib its place when it begins. Where another adlib or a line still stands on the place planned for it, it takes the next free one: further below, then beside. With no free place at all an adlib on the planned place goes a little
	 * faster, and this one waits for it: never over each other.
	 *
	 * @return whether it has its place
	 */
	private boolean placeAdlib(Minecraft client, int slot, LyricMotion.Adlib adlib, boolean hand, float size, float viewYaw, float viewPitch) {
		Shown owner = standing(adlib.line);
		if (owner == null) {
			owner = home(client, slot, adlib, size, viewYaw, viewPitch);
			if (owner == null) {
				return false;
			}
		}
		LyricLine line = motion.line(adlib.line);
		if (line == null) {
			return false;
		}
		plan(client, owner, line, hand);
		int order = Math.min(adlib.order, owner.planCount - 1);
		if (order < 0) {
			return false;
		}
		float x = owner.adlibX[order] + (adlib.singer == MusicService.SINGER_TWO && owner != alone ? SINGER_TWO_SIDE : 0f);
		float y = owner.adlibY[order];
		float width = owner.adlibWidth[order];
		float height = owner.adlibHeight[order];
		float stepDown = height + AdlibLayout.between(height);
		float stepAside = width + AdlibLayout.between(height);
		for (int spot = 0; spot < ADLIB_SPOTS; spot++) {
			textBox(owner, spotX(spot, x, stepAside), spotY(spot, y, stepDown), width, height, spotBoxes, spot * AdlibBoxes.VALUES);
		}
		int standingCount = 0;
		for (int other = 0; other < adlibPlace.length; other++) {
			if (other != slot && placedAdlibVisible(other)) {
				adlibBox(other, standingBoxes, standingCount++);
			}
		}
		for (Shown shown : lines) {
			if (shown != owner && shown.on() && shown.alpha() > VISIBLE_ALPHA) {
				lineBox(shown, standingBoxes, standingCount++ * AdlibBoxes.VALUES);
			}
		}
		int free = AdlibBoxes.firstFree(spotBoxes, ADLIB_SPOTS, standingBoxes, standingCount, 0f);
		if (free > 0 && owner != alone) {
			// the places further away are not planned around its own line
			lineBox(owner, standingBoxes, standingCount++ * AdlibBoxes.VALUES);
			free = AdlibBoxes.firstFree(spotBoxes, ADLIB_SPOTS, standingBoxes, standingCount, 0f);
		}
		if (free < 0) {
			for (int other = 0; other < adlibPlace.length; other++) {
				if (other != slot && placedAdlibVisible(other)) {
					adlibBox(other, oneBox);
					if (AdlibBoxes.overlapsAny(spotBoxes, 0, oneBox, 1, 0f)) {
						motion.adlibs[other].forcedOut = true;
					}
				}
			}
			return false;
		}
		Shown place = adlibPlace[slot];
		place.x = owner.x;
		place.y = owner.y;
		place.z = owner.z;
		place.facing = owner.facing;
		place.unit = owner.unit;
		place.rise = owner.rise;
		place.sky = owner.sky;
		place.shot = owner == alone && ModFeatures.CINEMATIC.active() ? ModFeatures.CINEMATIC.shotNumber() : owner.shot;
		adlibPlaceX[slot] = spotX(free, x, stepAside);
		adlibPlaceY[slot] = spotY(free, y, stepDown);
		adlibPlaceScale[slot] = owner.adlibScale[order];
		adlibPlaceWrap[slot] = owner.adlibWrap[order];
		adlibPlaceWidth[slot] = width;
		adlibPlaceHeight[slot] = height;
		adlibPlaceFor[slot] = adlib.part;
		return true;
	}

	private static float spotX(int spot, float x, float stepAside) {
		return spot == SPOT_RIGHT ? x + stepAside : spot == SPOT_LEFT ? x - stepAside : x;
	}

	private static float spotY(int spot, float y, float stepDown) {
		return spot < SPOT_RIGHT ? y + spot * stepDown : y;
	}

	/** Writes a placed adlib's box as the given one of an array of boxes. */
	private void adlibBox(int slot, float[] out, int index) {
		textBox(adlibPlace[slot], adlibPlaceX[slot], adlibPlaceY[slot], adlibPlaceWidth[slot], adlibPlaceHeight[slot], out, index * AdlibBoxes.VALUES);
	}

	/**
	 * Plans where a line's adlibs stand (see {@link AdlibLayout}), once: each broken into at most two balanced rows and sized, then stacked below
	 * the line's real bounds (its glow included) with a clear gap, their ends at the line's end, in the room the line keeps for them, the rest
	 * beside it. Adlibs on their own (in a gap) stack below each other around their spot.
	 */
	private void plan(Minecraft client, Shown owner, LyricLine line, boolean hand) {
		if (owner.planned) {
			return;
		}
		owner.planned = true;
		List<MusicService.Background> parts = line.backgrounds();
		int count = Math.min(parts.size(), MOST_PLANNED);
		owner.planCount = count;
		boolean own = owner != alone;
		int lineRows = own ? rowsOf(client, owner.text, owner.wrap) : 0;
		Entry lineEntry = own ? find(owner.text, hand, owner.wrap, owner.textScale) : null;
		boolean ready = lineEntry != null && lineEntry.sprite != null && lineEntry.sprite.state() == LyricSprites.State.READY;
		float lineWidth = !own ? 0f : ready ? lineEntry.sprite.width() : lineWidth(client, owner);
		float room = Math.max(lineWidth, ADLIB_LEAST_ROOM);
		for (int k = 0; k < count; k++) {
			String text = parts.get(k).text();
			int wrap = (int) Math.ceil(AdlibLayout.wrapWidth(text, room / AdlibLayout.SCALE, row -> client.font.width(Ui.lyricSequence(row))));
			List<String> rows = rowsFor(client, text, wrap);
			float widest = 0f;
			for (String row : rows) {
				widest = Math.max(widest, client.font.width(Ui.lyricSequence(row)));
			}
			float scale = AdlibLayout.fitScale(widest * AdlibLayout.SCALE, room);
			int adlibRows = Math.min(rows.size(), LyricSprites.MAX_ROWS);
			owner.adlibWrap[k] = wrap;
			owner.adlibScale[k] = scale;
			planWidths[k] = LyricSprites.boundsWidth(widest) * scale;
			planHeights[k] = (LyricSprites.boundsBottom(adlibRows) - LyricSprites.boundsTop(adlibRows)) * scale;
			owner.adlibWidth[k] = planWidths[k];
			owner.adlibHeight[k] = planHeights[k];
		}
		if (!own) {
			// on their own: a column around the spot
			float y = 0f;
			for (int k = 0; k < count; k++) {
				owner.adlibX[k] = 0f;
				owner.adlibY[k] = y + planHeights[k] / 2f;
				y += planHeights[k] + AdlibLayout.between(planHeights[k]);
			}
			return;
		}
		float halfWidth = LyricSprites.boundsWidth(lineWidth) / 2f;
		float top = LyricSprites.boundsTop(lineRows);
		float bottom = LyricSprites.boundsBottom(lineRows);
		float lowest = bottom + adlibRoom(owner.index) * ROW;
		float rowHeight = LyricSprites.boundsBottom(1) - LyricSprites.boundsTop(1);
		AdlibLayout.place(-halfWidth, top, halfWidth, bottom, rowHeight, AdlibLayout.WORLD_GAP_SHARE, planWidths, planHeights, count, lowest, planSpots);
		for (int k = 0; k < count; k++) {
			owner.adlibX[k] = planSpots[2 * k] + planWidths[k] / 2f;
			owner.adlibY[k] = planSpots[2 * k + 1] + planHeights[k] / 2f;
		}
	}

	/** @return a line's widest row in text units, measured before its sprite is ready */
	private static float lineWidth(Minecraft client, Shown shown) {
		float widest = 0f;
		for (String row : rowsFor(client, shown.text, shown.wrap)) {
			widest = Math.max(widest, client.font.width(Ui.lyricSequence(row)));
		}
		return widest * shown.textScale;
	}

	/**
	 * @return where an adlib sung in a gap stands: the line visible when it began, or a spot of its own near where the next line will appear,
	 *         placed once; null while it has no open spot
	 */
	private Shown home(Minecraft client, int slot, LyricMotion.Adlib adlib, float size, float viewYaw, float viewPitch) {
		Shown home = adlibHome[slot];
		boolean kept = adlibHomeFor[slot] == adlib.part && home != null
			&& (home == alone ? alone.appear > 0f : home.on() && home.index == adlibHomeIndex[slot] && home.startMs == adlibHomeStart[slot]);
		if (kept) {
			return home;
		}
		if (adlibHomeFor[slot] == adlib.part && home == alone) {
			return null; // no open spot was found for it: left out until it ends
		}
		home = newestVisible();
		if (home == null) {
			if (alone.text == null) {
				alone.text = "";
				place(client, alone, adlib.part.text(), AdlibLayout.SCALE, viewYaw, viewPitch, size);
				// placed once; without an open spot the adlibs are left out until they end
				alone.appear = alone.text != null ? 1f : 0f;
				alone.text = "";
			}
			home = alone;
		}
		adlibHome[slot] = home;
		adlibHomeFor[slot] = adlib.part;
		adlibHomeIndex[slot] = home.index;
		adlibHomeStart[slot] = home.startMs;
		return home == alone && alone.appear <= 0f ? null : home;
	}

	/**
	 * Submits a line or an adlib in the chosen effect, back to front.
	 *
	 * @param sungChars sung glyphs of the line, or -1 for line by line
	 */
	private void drawLine(SubmitNodeCollector collector, PoseStack pose, Entry entry, float[] rowsSung, float x, float y, float scale, float alpha, float sungChars,
						  float lit, float glow, Look look, boolean adlib) {
		LyricSprites.Sprite sprite = entry.sprite;
		pose.pushPose();
		pose.translate(x, y, 0f);
		pose.scale(scale, scale, 1f);
		if (adlib) {
			pose.last().pose().mul(SLANT);
		}
		// the sung glyphs of each row, from the line's: rows are separated by a space in the line
		float left = sungChars < 0f ? Float.MAX_VALUE : sungChars;
		var rows = sprite.rows();
		for (int k = 0; k < rows.size(); k++) {
			int length = rows.get(k).text().length();
			rowsSung[k] = Math.max(0f, Math.min(length, left));
			left -= length + 1;
		}
		// a soft dark shadow slightly below the text, so it reads against a bright sky: always for handwriting, by day for the clean style
		float shade = entry.hand ? HAND_SHADOW : DAY_SHADOW * day;
		if (shade > 0.01f) {
			pose.pushPose();
			pose.translate(1.4f, 2.2f, -0.01f);
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.GLOW, Ui.withOpacity(SHADOW, shade * alpha));
			pose.popPose();
		}
		switch (music.worldEffect.get()) {
			case GLOW -> drawSoft(collector, pose, entry, rowsSung, alpha, sungChars, lit, glow, look);
			case CLASSIC -> drawClassic(collector, pose, entry, rowsSung, alpha, sungChars, lit, glow, look);
		}
		pose.popPose();
	}

	/** The soft glow: word by word, the sung part lights up with the glow while the rest waits dimmer; line by line the whole line lights at once. */
	private static void drawSoft(SubmitNodeCollector collector, PoseStack pose, Entry entry, float[] rowsSung, float alpha, float sungChars, float lit, float glow, Look look) {
		LyricSprites.Sprite sprite = entry.sprite;
		if (sungChars < 0f) {
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.GLOW, Ui.withOpacity(look.color, GLOW_ALPHA * alpha * glow * lit));
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.TEXT, Ui.withOpacity(look.text, alpha * Mth.lerp(lit, REST_ALPHA, 1f)));
			return;
		}
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.SUNG_GLOW, Ui.withOpacity(look.color, GLOW_ALPHA * alpha * glow));
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.TEXT, Ui.withOpacity(look.text, alpha * REST_ALPHA));
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.SUNG, Ui.withOpacity(look.text, alpha));
	}

	/**
	 * The classic glow, as the lyrics looked before: a glow in the glow color around the whole line that breathes gently, a light in a lighter tint
	 * following the sung edge, the line tinted towards the glow color, and the sung part in the text color.
	 */
	private void drawClassic(SubmitNodeCollector collector, PoseStack pose, Entry entry, float[] rowsSung, float alpha, float sungChars, float lit, float glow, Look look) {
		LyricSprites.Sprite sprite = entry.sprite;
		float breath = animated ? 1f - BREATH + BREATH * (float) Math.sin(time * BREATH_SPEED) : 1f;
		int glowColor = entry.hand ? Ui.mix(look.color, 0xFFFFFFFF, CLASSIC_HAND_WHITE) : look.color;
		float glowStrength = (entry.hand ? CLASSIC_HAND_GLOW : CLASSIC_GLOW) * alpha * glow * breath;
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.GLOW, Ui.withOpacity(glowColor, glowStrength));
		if (sungChars >= 0f) {
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.HOT,
				Ui.withOpacity(Ui.mix(look.color, 0xFFFFFFFF, CLASSIC_HOT_WHITE), CLASSIC_HOT * alpha * glow));
		}
		int dim = Ui.mix(look.color, look.text, entry.hand ? CLASSIC_HAND_DIM : CLASSIC_DIM);
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.TEXT, Ui.withOpacity(dim, alpha));
		// line by line the whole line is in the text color once it is lit
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.SUNG, Ui.withOpacity(look.text, alpha * (sungChars < 0f ? lit : 1f)));
	}

	private static void submit(SubmitNodeCollector collector, PoseStack pose, LyricSprites.Sprite sprite, float[] sung, LyricSprites.Sprite.Layer layer,
							   int color) {
		if ((color >>> 24) == 0 || !OutlineRenderTypes.available()) {
			return;
		}
		pose.pushPose();
		pose.translate(0, 0, layer.depth);
		collector.submitCustomGeometry(pose, sprite.renderType(layer), (p, consumer) -> sprite.emit(p, consumer, sung, layer, color));
		pose.popPose();
	}

	// ------------------------------------------------------------------------------------------------------- sprites

	private Entry find(String text, boolean hand, int wrapWidth, float textScale) {
		for (Entry entry : entries) {
			if (entry.text != null && entry.hand == hand && entry.classic == classicFace && entry.wrap == wrapWidth && entry.textScale == textScale
				&& entry.text.equals(text)) {
				return entry;
			}
		}
		return null;
	}

	/** @return the sprite of a text, made when it is first needed and kept while it is drawn; its sprite is null while none could be made */
	private Entry sprite(String text, boolean hand, int wrapWidth, float textScale) {
		Entry found = find(text, hand, wrapWidth, textScale);
		if (found == null) {
			found = free();
			found.text = text;
			found.hand = hand;
			found.wrap = wrapWidth;
			found.textScale = textScale;
			found.classic = classicFace;
			found.sprite = null;
			found.triedAt = Long.MIN_VALUE / 2;
		}
		found.frame = frame;
		if (found.sprite == null && frame - found.triedAt >= RETRY_FRAMES) {
			found.triedAt = frame;
			// the classic face is lower case, as the lyrics were before; only when that keeps every letter in its place, for the sung part
			String shown = classicFace && text.toLowerCase(Locale.ROOT).length() == text.length() ? text.toLowerCase(Locale.ROOT) : text;
			List<String> rows = rowsFor(Minecraft.getInstance(), shown, wrapWidth);
			found.sprite = LyricSprites.begin(rows.subList(0, Math.min(rows.size(), LyricSprites.MAX_ROWS)), hand, classicFace, textScale);
		}
		if (found.sprite != null && found.sprite.state() == LyricSprites.State.FAILED) {
			found.sprite.release();
			found.sprite = null;
		}
		return found;
	}

	/** @return an entry for a new text: an empty one, or the one drawn longest ago, whose sprite is given back */
	private Entry free() {
		Entry oldest = entries[0];
		for (Entry entry : entries) {
			if (entry.text == null) {
				return entry;
			}
			if (entry.frame < oldest.frame) {
				oldest = entry;
			}
		}
		release(oldest);
		return oldest;
	}

	/** Gives back the sprites of the lines not drawn this frame, so their textures are free for the next lines and the preview. */
	private void releaseUnused() {
		for (Entry entry : entries) {
			if (entry.text != null && entry.frame < frame) {
				release(entry);
			}
		}
	}

	private void releaseAll() {
		for (Entry entry : entries) {
			release(entry);
		}
	}

	private static void release(Entry entry) {
		if (entry.sprite != null) {
			entry.sprite.release();
			entry.sprite = null;
		}
		entry.text = null;
	}

	/** @return the text broken into balanced rows of at most {@code maxWidth} text units, measured with the game's font as the sprites are */
	static List<String> rowsFor(Minecraft client, String text, int maxWidth) {
		return LyricWrap.balanced(text, maxWidth, LyricSprites.MAX_ROWS, row -> client.font.width(Ui.lyricSequence(row)));
	}
}
