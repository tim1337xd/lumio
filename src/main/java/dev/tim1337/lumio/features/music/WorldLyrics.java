package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.debug.DebugCategory;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.features.music.MusicFeature.WorldFont;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.visual.world.cinematic.CinematicCameraFeature;
import dev.tim1337.lumio.render.LateSubmits;
import dev.tim1337.lumio.render.LyricSprites;
import dev.tim1337.lumio.render.LyricOverlay;
import dev.tim1337.lumio.render.OutlineRenderTypes;
import dev.tim1337.lumio.ui.Ui;

import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The lyrics in the world (the "World lyrics" settings of {@link MusicFeature}): the line being sung, standing at a fixed spot in the world,
 * far in the sky or around the player. A block gets its spot once, at a line's start, from the view of that moment ({@link LyricSpots}): open sky,
 * else an open spot near the player, else before the blocks in front and off to the side; never in the quiet zone around the crosshair. It is fixed
 * in the world from then on and never rescaled or moved; only one fully out of view for more than two seconds ends early, and the next line is placed
 * anew. When the next line starts, a Classic line stays a moment, slowly dimming, then fades out; at most two older lines stand at once, and the
 * last line before a long instrumental part stays a few seconds after its last word.
 * <p>
 * Sung words light up with a glow in the glow color. Adlibs float smaller next to their line with the same effect, a bit softer. The lyrics fade
 * with the fog;
 * their tones are chosen by what lies behind them ({@link LyricTones}), light and glowing before the dark, deep before the bright. In the cinematic camera every shot has its own spot for the line; the look is the same.
 */
final class WorldLyrics {
	/** The widest a row of a line gets, in text units. */
	static final int WRAP_WIDTH = 300;
	/** A line fades in and out over this time. */
	private static final float FADE_SECONDS = 0.25f;
	/**
	 * In the cinematic camera lines never move, they only fade: the one leaving goes quickly, and the new one starts a moment later, so the two are
	 * never both clearly there at one spot. A line coming while one still fades leaves that one going on from where it is.
	 */
	private static final float FILM_OUT_SECONDS = 0.18f;
	private static final float FILM_IN_DELAY_SECONDS = 0.1f;
	private static final float FILM_IN_SECONDS = 0.22f;
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
	/** A break's dot against the advance of its letter, for how high it hops. */
	private static final float DOT_OF_ADVANCE = 0.6f;
	private static final float ROW = 38f;
	/** The soft dark shadow below handwriting, always there. */
	private static final int SHADOW = 0xFF000014;
	private static final float HAND_SHADOW = 0.42f;
	/**
	 * The tight shadow where a line needs one to read ({@link LyricTones#shadow}): this far off in text units, and this far behind the letters as
	 * the layers of a line count it.
	 */
	private static final float SHADOW_X = 0.6f;
	private static final float SHADOW_Y = 1.1f;
	private static final float SHADOW_DEPTH = -0.02f;
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
	/** How long a Cut-out line takes to dim once the next line starts, in seconds. */
	private static final float PREVIOUS_SECONDS = 0.35f;
	/** How long the earlier copy of a line sung again right away takes to go as the new one comes, in milliseconds. */
	private static final long REPLACE_MS = 260L;
	/** How many rows after a row its place can be taken: the rows of the line right after it. */
	private static final int TAKEN_REACH = 16;
	/** Below this nothing is seen. */
	private static final float VISIBLE_ALPHA = 0.004f;
	/** The places an adlib tries: the one planned, two further below, and one on each side. */
	private static final int ADLIB_SPOTS = 5;
	private static final int SPOT_RIGHT = 3;
	private static final int SPOT_LEFT = 4;
	/** In the cinematic camera: how long the line before takes to fade once the next begins, and how far ahead a new line must stay clear of it,
	 * along the camera's path, with a gap of half a line's height. */
	private static final float FILM_CLEAR_SECONDS = 1.5f;
	private static final float FILM_GAP_ROWS = 0.5f;
	/** A line waiting for the one in its place comes in once that one is this dim. */
	private static final float MADE_WAY = 0.08f;
	/** The room a line's glow and shadow reach around it, in degrees: a new line this near one that is going waits for it. */
	private static final float HANDOVER_ROOM_DEGREES = 2.5f;
	/** From this share of its light on a line is clearly seen. */
	private static final float SEEN_ALPHA = 0.1f;
	/**
	 * At most this many older lines stand beside the sung one (as many as "Lines at once" allows, less the sung one); in the cinematic camera one,
	 * so never more than two lines show.
	 */
	private static final int MOST_OLDER = 3;
	private static final int MOST_OLDER_FILMED = 1;
	/** Room for the sung line and the older ones, or a Cut-out column's lines, and those giving up their places while they fade. */
	private static final int SLOTS = Math.max(MOST_OLDER + 3, CutoutLyrics.MOST_LINES + 2);
	/** A line waits for its adlibs' place to clear only this long, then it comes in anyway: no line is held back until it is over. */
	private static final float MOST_WAIT_SECONDS = 0.15f;
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
	/**
	 * The rows a line prefers at most, the steps it is made smaller in, and the smallest a long line gets in all its rows. A line too long even
	 * for that, at the width the view really allows, is split in time before it comes here ({@link LyricSplit}); a word wider than a row is broken inside
	 * ({@link LyricWrap}). Only what can be neither (a block of fast lines) gets as small as it must, so it is shown whole all the same.
	 */
	private static final int PREFERRED_ROWS = 2;
	private static final float SHRINK_STEP = 0.05f;
	static final float LEAST_TEXT_SCALE = 0.6f;
	/** The steps the width a line may take is counted in, so a little more or less of it never splits the lines anew. */
	private static final int WHOLE_WIDTH_STEP = 20;
	/** Below this a text is hardly read: the smallest of all, for what cannot be broken or split. */
	private static final float LAST_TEXT_SCALE = 0.1f;

	/** Draws the lines of the Cut-out preset. */
	private final CutoutLyrics cutout;

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
		/** In a Cut-out column: how far it has become a line already sung (dimmed where it stands). */
		float previous;
		/** What lies behind it, which its tones are chosen by: taken when it starts, followed while it stands. */
		final LyricBackdrop backdrop = new LyricBackdrop();
		/**
		 * The share of its distance it is drawn at (see {@link WorldLyricDepth}), and whether something stands so far before it that it is covered;
		 * both found when it starts.
		 */
		float nearer = 1f;
		/** The share of its width its words take; ad-libs beside them use what is left and are set smaller where they find less. */
		float wordsShare = 1f;
		boolean covered;
		long endMs;
		String text;
		double x;
		double y;
		double z;
		float facing;
		/** How far it leans back toward the eye, in radians: by the height it stands at, at most 20 degrees either way ({@link LyricUpright}). */
		float tilt;
		float unit;
		/** Half the width and height it covers as seen from where it was placed, in degrees, for the next lines to keep clear of it. */
		float halfWidth;
		float halfHeight;
		/**
		 * Where it was placed by the view ({@link LyricSpots}): the kind of spot (none for a line of the cinematic camera, which has its own
		 * placing), how far away it stood then, and how near the camera it is drawn at most
		 * ({@link #cover}: before a wall nearer than the usual, so it may lie over that wall). Fixed when it is placed; it is never rescaled.
		 */
		LyricSpots.Kind kind = LyricSpots.Kind.NONE;
		double placedDistance;
		double cover = WorldLyricDepth.COVER_BLOCKS;
		/** Whether it can not be read now, and for how long without a break, in milliseconds ({@link LyricReadable}); and whether it was replaced for it. */
		boolean unreadableNow;
		/** Whether it has faced the player once: from then on it turns after the player slowly. */
		boolean faced;
		long unreadableMs;
		boolean replaced;
		/** Whether it stands in the sky, far away (the next lines placed by it keep to the sky too). */
		boolean sky;
		/** A Cut-out column of the film: how much its words were grown when its room was planned, the most they may grow, so it never outgrows its box. */
		float placedGrow = Float.NaN;
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
		/** How long it fades in and, as an older line, out: shorter for a short line, so it is seen in its own time. Its own length, in milliseconds. */
		float fadeIn = FADE_SECONDS;
		long lengthMs = Long.MAX_VALUE;
		/** A line that takes the place of one still fading there waits for it to dim before it comes in, so the two never lie over each other. */
		Shown waitFor;
		/** For the debug log: that it stood over another line was noted. */
		boolean overNoted;
		/** For the debug log: it was drawn in the frame before. */
		boolean wasDrawn;
		/** In the cinematic camera a line leaves with an ease-out over {@link #FILM_OUT_SECONDS}: its light falls quickly at first, then softly. */
		boolean easeOut;
		/** In the cinematic camera: how long a new line still waits before it fades in, while the one before goes. */
		float delay;
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
		/** How far its adlibs may reach from the line's middle to either side, in text units: the line's block, never wider; no limit on their own. */
		float adlibReach;
		/** The film's shot it was placed in, which it never outlives. */
		int shot;
		/**
		 * A line of the Cut-out preset: the text that stands for the room its column takes where the line is placed (its own text says nothing
		 * about that), and how its own text is broken and sized in its sprite.
		 */
		String boxText;
		/** The line it shows, kept from its start: the lines of the song can be put together anew while it still stands. */
		LyricLine cutLine;
		/** Whether it is a Cut-out column that was placed (and stood upright, see {@link #standColumn}); false for every other line. */
		boolean placedColumn;
		/** The preset it started in, which it keeps for its life. */
		WorldLyricPreset preset = WorldLyricPreset.CLASSIC;
		/**
		 * A Cut-out line in a column of several lines: how the column goes on (pages or a scrolling stack), the column's room (its height in text
		 * units), the stack a scrolling column moves by, the first row of the line the column shows, and where its rows stand and when they come and
		 * go, by the plan of that version.
		 */
		CutoutFlow.Mode column;
		float room;
		CutoutScroll stack;
		int fromRow;
		CutoutLyrics.Part part;
		/** How long it has been coming in or standing, in seconds. */
		float shownFor;
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
			fadeIn = FADE_SECONDS;
			lengthMs = Long.MAX_VALUE;
			easeOut = false;
			delay = 0f;
			waitFor = null;
			tilt = 0f;
			previous = 0f;
			backdrop.reset();
			nearer = 1f;
			covered = false;
			overNoted = false;
			wasDrawn = false;
			sung = 0f;
			lit = 0f;
			age = 0f;
			rise = 0f;
			sky = false;
			placedGrow = Float.NaN;
			kind = LyricSpots.Kind.NONE;
			placedDistance = 0.0;
			cover = WorldLyricDepth.COVER_BLOCKS;
			unreadableNow = false;
			faced = false;
			unreadableMs = 0L;
			replaced = false;
			wrap = WRAP_WIDTH;
			textScale = 1f;
			preset = WorldLyricPreset.CLASSIC;
			column = null;
			room = 0f;
			stack = null;
			fromRow = 0;
			part = null;
			boxText = null;
			wordsShare = 1f;
			cutLine = null;
			placedColumn = false;
			shownFor = 0f;
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

		/** @return how visible it is by its way in and out alone: an older line dims by its tones ({@link #dim}), not by this */
		float fade() {
			return appear * (easeOut ? out * out : out);
		}
	}

	private final MusicFeature music;
	private final LyricMotion motion;
	private final AnimClock clock = new AnimClock();
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
	private final boolean[] planLong = new boolean[MOST_PLANNED];
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
	/** Which of the places an adlib may take each tried box stands for. */
	private final int[] spotOrder = new int[ADLIB_SPOTS];
	private final float[] oneBox = new float[AdlibBoxes.VALUES];
	private final float[] adlibFilmBox = new float[4];
	/** How visible everything is around a cut of the film, this frame. */
	private float filmFade = 1f;
	private boolean filmedNow;
	private final MusicService.Background[] adlibHomeFor = new MusicService.Background[LyricMotion.MAX_ADLIBS];
	private final int[] adlibHomeIndex = new int[LyricMotion.MAX_ADLIBS];
	private final long[] adlibHomeStart = new long[LyricMotion.MAX_ADLIBS];
	private float time;
	private long frame;
	private float dt;
	private boolean animated;
	private Vec3 eye = Vec3.ZERO;
	/** Whether the lines are in the classic glow's own face this frame: lower case in the old lyrics font, as before. */
	private boolean classicFace;
	/** Whether the lines are in the clean style this frame: rows are measured as their sprites will be, which depends on the font. */
	private static boolean cleanStyle = true;

	WorldLyrics(MusicFeature music, LyricMotion motion) {
		this.music = music;
		this.cutout = new CutoutLyrics(music);
		this.motion = motion;
		motion.fitsWhole = this::fitsWhole;
		motion.fitWidth = () -> wholeWidth(Minecraft.getInstance());
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
			if (client.level != null && client.player != null && (songBreak.running() || anyOn())) {
				// switched off: what is shown goes together and quickly, as at a song change, then everything is cleared
				songBreak.start();
				dt = clock.tick();
				if (!songBreak.step(dt)) {
					drawBreak(context);
					return;
				}
			}
			LyricVignette.off();
			releaseAll();
			cutout.clear();
			clearLines();
			alone.clear();
			openSky.reset();
			songClock.reset();
			stackNow = null;
			return;
		}
		motion.update();
		frame++;
		dt = clock.tick();
		double nowSeconds = System.nanoTime() / 1e9;
		boolean scrubbing = music.scrubbing();
		double playerMs = scrubbing ? music.scrubMs() : MusicService.positionExact();
		double reportSeconds = scrubbing ? nowSeconds : MusicService.positionStamp() / 1e9;
		if (songClock.update(playerMs, reportSeconds, music.lyricLeadMs((long) playerMs), MusicService.isPlaying() && !scrubbing, nowSeconds)) {
			// a seek (or the first frame): the scrolling stack ends, the next line starts a column of its own
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.MUSIC, "World lyrics: the song clock was cut (a seek) at {} ms", Math.round(playerMs));
			}
			clockCut = true;
			stackNow = null;
			// what stands of the old place in the song goes together; the new place starts clean
			if (anyOn()) {
				songBreak.start();
			}
		}
		// a long pause is a break too; the lyrics come back clean, placed anew, when the song plays again
		pausedFor = MusicService.isPlaying() || scrubbing ? 0f : pausedFor + dt;
		boolean heldOff = pausedFor >= SongBreak.LONG_PAUSE_SECONDS;
		// another song (or other lyrics): the old one's lyrics go together before the first line of the new one
		if (heldOff && anyOn() || spawnedIn != null && motion.lines != spawnedIn && anyOn()) {
			songBreak.start();
		}
		if (!songBreak.running()) {
			// held during a break: nothing scrolls or moves on
			scrollAt = songClock.ms();
		}
		time += dt;
		animated = ModFeatures.MENU.animations();
		var camera = client.gameRenderer.mainCamera();
		eye = camera.position();
		viewAngles(client);
		// the Cut-out preset has its own lettering: the heaviest of the clean style
		boolean cutouts = music.worldPreset.get() == WorldLyricPreset.CUTOUT;
		boolean hand = !cutouts && music.worldFont.get() == WorldFont.HANDWRITTEN;

		cleanStyle = !hand;
		LyricReach.use(music.worldSize.get());
		float size = music.worldSize.get().factor;
		CinematicCameraFeature film = ModFeatures.CINEMATIC;
		boolean filmed = film.active();
		filmFade = filmed ? film.lyricFade() * filmShownNow(film) : 1f;
		filmedNow = filmed;

		watchMoving(client);
		// the grid of rays across the view: where a block may stand, and how bright what lies behind it is, for its tones
		openSky.step(client, eye, camera.yRot(), camera.xRot(), halfFovY, aspect, dt);
		if (songBreak.running()) {
			if (!songBreak.step(dt)) {
				drawBreak(context);
				return;
			}
			endBreak();
		}
		LyricLine line = motion.visible > 0.001f && !heldOff ? motion.line(motion.current) : null;
		// right after a cut only the line sung by the clock starts in the new shot, not one the motion has yet to move on from
		if (filmed && !placeAfterCut && line != null && LyricCutFade.inCutFade(film.sinceCutMs()) && LyricTimes.mainIndexAt(motion.lines, music.lyricClockMs()) != motion.current) {
			line = null;
		}
		// lyrics switched on in the middle of a song: the line waits a fraction of a second for the first look at the open sky, rather than stand near
		boolean awaitsLook = !filmed && openSky.firstLookPending();
		// a line is new by its index and start, so the same text sung again is a line of its own
		if (line != null && !awaitsLook && !line.text().isBlank() && (motion.lines != spawnedIn || motion.current != spawnedIndex || line.timeMs() != spawnedStart)) {
			startMissed(client, camera.yRot(), camera.xRot(), size, filmed);
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
		// a line that was too young to give up its place when the next started goes once it was readable
		pushOut(mostOlder(filmed));
		long clockMs = music.lyricClockMs();
		float linger = music.worldLinger.get().seconds;
		float lingerFade = music.worldLinger.get().fadeSeconds;
		for (Shown shown : lines) {
			advance(shown, shown == current && line != null && motion.current == shown.index, clockMs, linger, lingerFade, filmed);
		}
		if (filmed) {
			replaceLostFilmCutout(client, film, size);
		} else {
			watchStanding(camera.yRot(), camera.xRot());
		}
		keepColumn();
		if (current != null && !current.on()) {
			current = null;
		}
		followBackdrops();
		LyricVignette.update(anyVisible(null), filmed);

		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		// the older lines first, the sung one last
		for (Shown shown : lines) {
			if (shown != current) {
				drawShown(collector, pose, shown, hand);
			}
		}
		if (current != null && drawShown(collector, pose, current, hand) && filmed && DebugHooks.ACTIVE && film.shotNumber() != loggedShot) {
			loggedShot = film.shotNumber();
			DebugHooks.log(DebugCategory.MUSIC, "Cinematic shot {}: line drawn", loggedShot);
		}
		if (DebugHooks.ACTIVE) {
			noteOverlaps();
		}
		drawAdlibs(client, collector, pose, hand, size, camera.yRot(), camera.xRot());
		releaseUnused();
	}

	// ------------------------------------------------------------------------------------------------ kept in view

	private final double[] screenPlace = new double[2];
	private final double[] wantedDirection = new double[3];
	/** Half the vertical field of view of this frame and the window's shape. */
	private double halfFovY;
	private double aspect;

	/** Half the vertical field of view of this frame and the window's shape, for where things lie on the screen. */
	private void viewAngles(Minecraft client) {
		float fov = client.gameRenderer.mainCamera().getFov();
		halfFovY = (fov > 1f ? fov : client.options.fov().get()) / 2.0;
		aspect = client.getWindow().getWidth() / (double) Math.max(1, client.getWindow().getHeight());
	}

	/** A line stands at least this far away at a render distance of {@link #LEAST_AT_CHUNKS} chunks, more or less with the render distance. */
	private static final double LEAST_BLOCKS = 8.0;
	private static final double LEAST_AT_CHUNKS = 12.0;
	private static final double LEAST_MOST_BLOCKS = 16.0;
	/** Where a line goes that would stand too close: eye level, then a little higher, in the same heading. */
	private static final double[] HIGHER_DEGREES = {0.0, 8.0, 16.0, 24.0};

	/** @return how far away a line stands at least, in blocks: {@link #LEAST_BLOCKS}, more with a larger render distance */
	private static double leastDistance() {
		int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
		return Math.clamp(LEAST_BLOCKS * chunks / LEAST_AT_CHUNKS, LEAST_BLOCKS * 0.75, LEAST_MOST_BLOCKS);
	}

	/** @return how far the view reaches in a direction (unit length) up to a distance, before a block, from the eye */
	private double reach(double dx, double dy, double dz, double distance) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return distance;
		}
		Vec3 end = eye.add(dx * distance, dy * distance, dz * distance);
		var hit = client.level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.VISUAL,
			net.minecraft.world.level.ClipContext.Fluid.NONE, client.player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? distance : hit.getLocation().distanceTo(eye);
	}

	/** For the debug log: two lines that stand over each other while both are clearly seen, noted once for the newer one. */
	private void noteOverlaps() {
		if (!DebugHooks.ACTIVE) {
			return;
		}
		for (Shown upper : lines) {
			if (!upper.on() || upper.overNoted || upper.alpha() <= SEEN_ALPHA) {
				continue;
			}
			directionOf(upper, direction);
			float yaw = direction[0];
			float elevation = direction[1];
			for (Shown lower : lines) {
				if (lower == upper || !lower.on() || lower.startMs > upper.startMs || lower.alpha() <= SEEN_ALPHA) {
					continue;
				}
				directionOf(lower, direction);
				if (Math.abs(Mth.wrapDegrees(direction[0] - yaw)) < lower.halfWidth + upper.halfWidth
					&& Math.abs(direction[1] - elevation) < lower.halfHeight + upper.halfHeight) {
					upper.overNoted = true;
					DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: drawn over line {} while both are seen ({} and {} of their light)", upper.index, lower.index,
						Math.round(upper.alpha() * 100f) / 100f, Math.round(lower.alpha() * 100f) / 100f);
					break;
				}
			}
		}
	}

	/** @return how many older lines may stand beside the sung one */
	private int mostOlder(boolean filmed) {
		return filmed ? MOST_OLDER_FILMED : Math.clamp(Math.round(music.worldLinesAtOnce.get()) - 1, 0, MOST_OLDER);
	}

	/**
	 * The lines the clock passed since the last one started (a fast song, a late report of the player's position, a slow frame) start now, in
	 * their order, before the sung one: late, but never left out. Not after a seek, and not in the cinematic camera, where a shot has one line.
	 */
	private void startMissed(Minecraft client, float viewYaw, float viewPitch, float size, boolean filmed) {
		if (filmed || motion.lines != spawnedIn || motion.current <= spawnedIndex + 1) {
			return;
		}
		long clockMs = music.lyricClockMs();
		int first = WorldLyricPace.firstToStart(motion.lines, spawnedIndex, motion.current, clockMs);
		for (int i = first; i < motion.current; i++) {
			LyricLine missed = motion.lines.get(i);
			if (!LyricTimes.main(missed) || LyricBreaks.is(missed)) {
				continue;
			}
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: delayed by {} ms, the clock passed its start (update too coarse or a clock step)", i,
					clockMs - missed.timeMs());
			}
			spawn(client, missed, i, viewYaw, viewPitch, size, false);
			lateLines++;
		}
	}

	/** Lines started late because the clock had passed them, for the test log. */
	int lateLines;


	/** Lines started in the world since the lyrics came, by their index: for the tests, which check that none is left out. */
	final java.util.BitSet started = new java.util.BitSet();
	/** Lines really drawn (their sprite was ready and lit), by their index: a line started but never drawn was left out. */
	final java.util.BitSet drawn = new java.util.BitSet();
	/** The smallest size a line of these lyrics was set in, for the tests: below {@link #LEAST_TEXT_SCALE} it is hardly read. */
	float smallestScale = 1f;

	/**
	 * The same line sung again right after it (a chorus line several times in a row): the line on show stays where it is, as the line sung now, and only
	 * pulses once and starts its fill again. A cut of the film has cleared it, so there it starts at the new place.
	 *
	 * @return whether the line on show is the one sung again
	 */
	private boolean repeatsCurrent(LyricLine line, int index, boolean filmed) {
		// a Cut-out column does not keep the line: the line sung again comes in its place, its words with the singing (see rowRepeat)
		if (current == null || !current.on() || current.leaving || current.column != null || filmed && current.shot != ModFeatures.CINEMATIC.shotNumber()
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

	/** A cut happened and the line being sung is still to be placed in the new shot (at once, even during the cut's fade); while it is placed, set. */
	private boolean placeAfterCut;
	private boolean placingAfterCut;

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
		// a cut leaves the lyrics unreadable at once: they are placed again in the new shot right away, for the new camera
		placeAfterCut = true;
	}

	/** The clean break at a song change, a seek, a long pause or the lyrics switched off (see {@link SongBreak}). */
	private final SongBreak songBreak = new SongBreak();
	/** How long the player has been paused, in seconds. */
	private float pausedFor;

	/** @return whether anything of the lyrics stands in the world: a line, a column or an ad-lib */
	private boolean anyOn() {
		for (Shown shown : lines) {
			if (shown.on()) {
				return true;
			}
		}
		for (int i = 0; i < adlibPlace.length; i++) {
			if (placedAdlibVisible(i)) {
				return true;
			}
		}
		return false;
	}

	/** Draws what stands, held where it is, as it goes in the break: nothing new comes and nothing moves on. */
	private void drawBreak(LevelRenderContext context) {
		boolean hand = music.worldPreset.get() != WorldLyricPreset.CUTOUT && music.worldFont.get() == WorldFont.HANDWRITTEN;
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		for (Shown shown : lines) {
			if (shown != current) {
				drawShown(collector, pose, shown, hand);
			}
		}
		if (current != null) {
			drawShown(collector, pose, current, hand);
		}
		Minecraft client = Minecraft.getInstance();
		var camera = client.gameRenderer.mainCamera();
		drawAdlibs(client, collector, pose, hand, music.worldSize.get().factor, camera.yRot(), camera.xRot());
		releaseUnused();
	}

	/** The break is over: nothing of the old lyrics is left, and the next line is placed fresh and comes in when it is sung. */
	private void endBreak() {
		cutout.clear();
		clearLines();
		alone.clear();
		for (int i = 0; i < adlibPlaceFor.length; i++) {
			adlibPlaceFor[i] = null;
			adlibHomeFor[i] = null;
			adlibHome[i] = null;
		}
		stackNow = null;
		clockCut = true;
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "World lyrics: a clean break, nothing of the old lyrics is left");
		}
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
			started.clear();
			drawn.clear();
			smallestScale = 1f;
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
		// a block that could not be read for over a second goes now, at a line start, and this line is placed fresh
		if (!filmed) {
			replaceUnreadable();
		}
		spawnedIn = all;
		spawnedIndex = index;
		spawnedStart = line.timeMs();
		if (current != null) {
			current.older = true;
			current.olderFor = 0f;
			current.sung = Float.MAX_VALUE;
			current.lit = 1f;
			if (filmed && current.column == null) {
				// in the cinematic camera at most two lines show: the new one and the one before, which fades out where it stands as the new one
				// begins; anything older goes at once
				for (Shown shown : lines) {
					if (shown.on() && shown != current) {
						shown.leaving = true;
						shown.pushed = true;
						shown.easeOut = true;
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
		if (cutoutChosen() && LyricBreaks.is(line)) {
			// an instrumental break shows nothing: what stands goes, and the next line comes in as it starts
			for (Shown shown : lines) {
				if (shown.on()) {
					shown.leaving = true;
				}
			}
			current = null;
			return;
		}
		pushOut(mostOlder(filmed));
		Shown fresh = freeSlot();
		fresh.clear();
		fresh.index = index;
		fresh.startMs = line.timeMs();
		fresh.endMs = index < all.size() ? LyricTimes.lineEnd(all, index) : line.timeMs();
		fresh.beforeGap = beforeLongGap(all, index, fresh.endMs);
		fresh.text = line.text();
		fresh.lengthMs = fresh.endMs == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, fresh.endMs - fresh.startMs);
		fresh.fadeIn = WorldLyricPace.fadeSeconds(music.worldFade.get(), fresh.lengthMs, FADE_SECONDS);
		started.set(index);
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: starts at {} ms, {} ms long, fade in {} s{}", index, fresh.startMs,
				fresh.lengthMs == Long.MAX_VALUE ? -1 : fresh.lengthMs, fresh.fadeIn, line.text().indexOf(LyricGroups.BREAK) >= 0 ? ", a block of fast lines" : "");
		}
		// right after a cut the cut's own fade brings it in, with nothing on top of it
		fresh.appear = !animated || filmed && LyricCutFade.inCutFade(ModFeatures.CINEMATIC.sinceCutMs()) ? 1f : 0f;
		fresh.shot = filmed ? ModFeatures.CINEMATIC.shotNumber() : 0;
		if (filmed && fresh.appear < 1f && anyVisible(fresh)) {
			fresh.delay = FILM_IN_DELAY_SECONDS;
		}
		current = fresh;
		if (filmed) {
			placingAfterCut = placeAfterCut;
			if (music.worldPreset.get() == WorldLyricPreset.CUTOUT) {
				// a Cut-out column in the film: it goes on in the shot's spot while the shot lasts, or is placed in the shot as a box of its size
				fresh.boxText = cutoutBox(BOX_GROUPS);
				fresh.cutLine = motion.line(fresh.index);
				fresh.wrap = WRAP_WIDTH;
				fresh.textScale = 1f;
				if (!joinColumn(client, fresh, 0f, 0f, true)) {
					placeInShot(client, ModFeatures.CINEMATIC, fresh, size, music.lyricClockMs());
					// words grown to their size on the screen in this shot's view: fewer rows on a page, the page as tall as planned
					startColumn(fresh, CutoutLyrics.height(BOX_GROUPS) / drawnGrow(fresh), placingAfterCut ? rowAt(fresh.index, music.lyricAt) : firstRow(fresh.index));
				}
			} else {
				fitRows(client, fresh.text, WRAP_WIDTH);
				fresh.wrap = fitWrap;
				fresh.textScale = fitScale;
				placeInShot(client, ModFeatures.CINEMATIC, fresh, size, music.lyricClockMs());
			}
			makeAdlibsWay(fresh);
			placeAfterCut = false;
			placingAfterCut = false;
		} else {
			if (music.worldPreset.get() == WorldLyricPreset.CUTOUT) {
				placeCutout(client, fresh, viewYaw, viewPitch);
			} else {
				place(client, fresh, line.text(), 1, viewYaw, viewPitch);
			}
			if (fresh.on()) {
				makeWay(fresh);
			}
		}
		// the dots of a break have their own way: no word stack there
		fresh.preset = fresh.boxText == null && music.worldPreset.get() == WorldLyricPreset.CUTOUT ? WorldLyricPreset.CLASSIC : music.worldPreset.get();
		if (fresh.on()) {
			// what lies behind it, once, as it starts: a line keeps its tones while it is sung
			measureBackdrop(fresh);
		}
		smallestScale = Math.min(smallestScale, fresh.textScale);
	}

	/** The text standing for the room a Cut-out column takes, made for the number of words kept it was last asked for. */
	private String cutoutBox;
	private int cutoutBoxKept;

	/** @return a text of as many rows as the column of the Cut-out preset is tall, each about as wide as the column */
	private String cutoutBox(int kept) {
		if (cutoutBox == null || cutoutBoxKept != kept) {
			cutoutBoxKept = kept;
			StringBuilder row = new StringBuilder();
			while (LyricSprites.rowWidth(row.toString(), true) < CutoutLyrics.COLUMN) {
				row.append('o');
			}
			int rows = Math.clamp(Math.round(CutoutLyrics.height(kept) / ROW), 1, LyricSprites.MAX_ROWS);
			StringBuilder box = new StringBuilder();
			for (int i = 0; i < rows; i++) {
				box.append(i > 0 ? String.valueOf(LyricGroups.BREAK) : "").append(row);
			}
			cutoutBox = box.toString();
		}
		return cutoutBox;
	}

	/** The groups of usual size a column is placed with where no open area gives it more room. */
	private static final int BOX_GROUPS = 5;
	/** A scrolling column's words fade out over this band below the top of its room, in text units. */
	private static final float SCROLL_FADE = 36f;
	/** An older line of a column dims to this, over this time. */
	private static final float COLUMN_OLDER = 0.62f;
	private static final float COLUMN_DIM_SECONDS = 0.3f;
	/** The player moves when the eye goes this far between two frames, in blocks. */
	private static final double MOVED_BLOCKS = 0.03;
	/** The room under the last row of a sung line, in text units: the lines of a page stand a little apart. */
	private static final float LINE_SPACE = 12f;

	private final CutoutFlow flow = new CutoutFlow();
	private final CutoutScroll[] stacks = {new CutoutScroll(), new CutoutScroll()};
	private final WorldLyricOpenSky openSky = new WorldLyricOpenSky();
	private double lastX = Double.NaN;
	private double lastY;
	private double lastZ;
	/**
	 * The rows of the song's Cut-out column (each a big word with the small words sung before it), measured ahead for these lyrics and this look:
	 * per line its first row, per row its height (with the room under a line's last row), its measured height, when it starts and its line;
	 * then its top in the column's stack, which pages and scrolling both move.
	 */
	private List<LyricLine> planOf;
	private int[] firstRow = new int[0];
	private int rows;
	private float[] rowHeight = new float[0];
	private float[] rowFit = new float[0];
	private long[] rowStart = new long[0];
	private int[] rowLine = new int[0];
	/** Per row: the row of the line before it that it takes the place of, where its line is that line sung again right after (the same words,
	 * the same rows), else -1. Such a row stands where the earlier one stood and takes no room of its own; the earlier one goes as it comes. */
	private int[] rowRepeat = new int[0];
	private float[] stackTop = new float[0];
	/** The room of the column last started, for a stack whose lines have all gone. */
	private float plannedRoom = -1f;
	/** The scrolling stack being built: the last line it has its rows for, whether it ends there (a long pause), and where its next row goes. */
	private CutoutScroll stackNow;
	private int stackedTo = -1;
	private boolean stackClosed;
	private float stackBottom;
	private int stackFrom;
	/** Whether the stack moves once per sung line, as far as it needs (the Pages flow), or row by row (Scroll). */
	private boolean stackByLine;
	/** The smooth clock a column moves and its words come by (see {@link SongClock}), and whether it was cut (a seek) since the last line placed. */
	private final SongClock songClock = new SongClock();
	private double scrollAt;
	private boolean clockCut;

	/**
	 * Places a line of the Cut-out preset. It joins the column standing while that is still in view: its rows go where the planned pages or the
	 * scrolling stack have them. A change of the flow starts a column anew at the same spot; only when the view has left the column (or there is
	 * none) is the spot fitted to the view anew, between lines.
	 */
	private void placeCutout(Minecraft client, Shown fresh, float viewYaw, float viewPitch) {
		fresh.boxText = cutoutBox(BOX_GROUPS);
		fresh.cutLine = motion.line(fresh.index);
		if (joinColumn(client, fresh, viewYaw, viewPitch, false)) {
			return;
		}
		startColumn(fresh, placeCutoutAnew(client, fresh, viewYaw, viewPitch), firstRow(fresh.index));
	}

	/**
	 * A new Cut-out line goes on in the column standing, or a new column starts at its spot.
	 *
	 * @return whether the line has its place; otherwise the column before is going and the line needs a spot of its own
	 */
	private boolean joinColumn(Minecraft client, Shown fresh, float viewYaw, float viewPitch, boolean filmed) {
		CutoutFlow.Mode mode = flow.atLineStart(music.cutoutFlow.get(), System.currentTimeMillis(), filmed);
		measureRows();
		Shown before = null;
		for (Shown shown : lines) {
			if (shown != fresh && shown.on() && !shown.leaving && shown.column != null && (before == null || shown.startMs > before.startMs)) {
				before = shown;
			}
		}
		boolean seen = before != null && columnSeen(before, viewYaw, viewPitch, filmed);
		// a column in view is never placed anew because the view changed: only when the Placement chosen no longer takes its kind of spot
		boolean covered = false;
		if (seen && !filmed && !LyricSpots.allowed(mode(), before.kind)) {
			covered = true;
			seen = false;
		}
		int first = firstRow(fresh.index);
		// after a seek the column never goes on: the new place in the song comes in by a cross-fade, never by a fast scroll
		boolean cut = clockCut;
		clockCut = false;
		if (seen && before.column == mode && first >= 0) {
			if (!cut && before.stack == stackNow && !closedBefore(fresh.index)) {
				copyPlace(before, fresh);
				fresh.fromRow = first;
				stackAhead(fresh.index);
				fresh.part = part(fresh);
				return true;
			}
		}
		// the column before ends here: a change of the flow is a cross-fade, a new stack after the last one ended comes in where it stood
		for (Shown shown : lines) {
			if (shown != fresh && shown.on() && (shown.column != null || shown.boxText != null)) {
				shown.leaving = true;
				shown.pushed = true;
				if (filmed || covered || seen && before.column == mode) {
					fresh.waitFor = fresh.waitFor == null || shown.alpha() > fresh.waitFor.alpha() ? shown : fresh.waitFor;
				}
			}
		}
		if (!seen) {
			return false;
		}
		copyPlace(before, fresh);
		startColumn(fresh, before.room, first);
		return true;
	}

	/** @return whether the scrolling stack ended before this line (at a long pause) */
	private boolean closedBefore(int index) {
		return stackClosed && index > stackedTo;
	}

	/** @return whether a column is still in view, at least a part of it (or, in the film, in the same shot and well inside the picture) */
	private boolean columnSeen(Shown column, float viewYaw, float viewPitch, boolean filmed) {
		if (!filmed) {
			return column.placedColumn && !column.leaving && CutoutPlace.joins(whereInView(column, viewYaw, viewPitch));
		}
		CinematicCameraFeature film = ModFeatures.CINEMATIC;
		if (column.shot != film.shotNumber() || !film.lyricPlaced()) {
			return false;
		}
		boolean inFront = film.lyricBoxNow(column.x, column.boxY, column.z, column.boxRightX, column.boxRightZ, column.boxHalfWidth, column.boxHalfHeight,
			filmColumnBox);
		return CutoutPlace.keepsShot(CutoutPlace.where(inFront, (filmColumnBox[0] + filmColumnBox[2]) / 2.0, (filmColumnBox[1] + filmColumnBox[3]) / 2.0,
			(filmColumnBox[2] - filmColumnBox[0]) / 2.0, (filmColumnBox[3] - filmColumnBox[1]) / 2.0));
	}

	/**
	 * The first line of a column: its flow and room, and its rows from the one given on (the rows before never show) as the first of a new page
	 * or at the start of a new scrolling stack.
	 */
	private void startColumn(Shown fresh, float room, int fromRow) {
		measureRows();
		CutoutFlow.Mode mode = flow.shown();
		fresh.column = mode;
		fresh.room = room;
		fresh.placedGrow = fresh.kind == LyricSpots.Kind.NONE ? drawnGrow(fresh) : Float.NaN;
		fresh.fromRow = fromRow;
		fresh.stack = null;
		if (fromRow < 0) {
			// a line with no row of its own: it stands alone
			fresh.part = null;
			return;
		}
		// a stack of its own, in both flows (pages move it once per line, as far as a line needs); one still fading out keeps its own motion
		CutoutScroll free = stacks[0];
		for (Shown shown : lines) {
			if (shown.on() && shown.stack == free) {
				free = stacks[1];
			}
		}
		// both stacks still moving lines (a seek, a flow switch): the older column goes, rather than its lines following the new stack
		for (Shown shown : lines) {
			if (shown != fresh && shown.on() && shown.stack == free) {
				shown.clear();
			}
		}
		free.clear();
		stackNow = free;
		kept.clear();
		stackByLine = mode == CutoutFlow.Mode.PAGES;
		plannedRoom = room;
		fresh.stack = free;
		stackedTo = fresh.index - 1;
		stackClosed = false;
		stackBottom = 0f;
		stackFrom = fromRow;
		stackAhead(fresh.index);
		fresh.part = part(fresh);
	}

	/** The tops of the latest lines of the scrolling stack, which bound how many sung lines it keeps. */
	private final CutoutScroll.Kept kept = new CutoutScroll.Kept();

	/** A stack knows the rows of this many lines ahead, so its motion never changes once it is under way. */
	private static final int STACK_AHEAD = 3;

	/** Gives the rows of the lines after this one their places in the scrolling stack, up to a long pause or the end of the song. */
	private void stackAhead(int index) {
		if (stackNow == null || planOf == null) {
			return;
		}
		float room = roomOf(stackNow);
		int ends = Math.min(firstRow.length - 1, index + STACK_AHEAD);
		while (!stackClosed && stackedTo < ends) {
			int line = ++stackedTo;
			int first = firstRow[line];
			if (first < 0) {
				continue;
			}
			int from = Math.max(first, stackFrom);
			float lineTop = stackBottom;
			int end = from;
			while (end + 1 < rows && rowLine[end + 1] == line) {
				end++;
			}
			// a line sung again right away stands where it stood only while that is still in the room (CutoutRowStack)
			stackBottom = CutoutRowStack.placeLine(stackTop, rowHeight, rowFit, rowRepeat, from, end, stackBottom, room);
			int last = -1;
			for (int r = from; r <= end; r++) {
				last = rowRepeat[r] >= 0 ? last : r;
			}
			if (last >= 0) {
				// scrolling keeps a few sung lines above the one being sung, however tall its room: the older ones leave over the top
				float least = stackByLine ? Float.NEGATIVE_INFINITY : kept.add(stackTop[from]);
				boolean byRow = !stackByLine || stackTop[last] + rowFit[last] - lineTop > room;
				// pages move once per line, as far as it needs; scrolling, and a line taller than the room, row by row
				for (int r = from; byRow && r <= last; r++) {
					if (rowRepeat[r] >= 0) {
						continue;
					}
					if (stackByLine) {
						stackNow.addStep(rowStart[r], CutoutScroll.pagePlace(stackTop[r] + rowFit[r], room));
					} else {
						stackNow.addPlace(rowStart[r], Math.max(stackTop[r] + rowFit[r] - room, least));
					}
				}
				if (!byRow) {
					stackNow.addStep(rowStart[from], CutoutScroll.pagePlace(stackTop[last] + rowFit[last], room));
				}
			}
			if (beforeLongGap(planOf, line, LyricTimes.lineEnd(planOf, line))) {
				// the column ends at the pause: no line after it is in this stack
				stackClosed = true;
			}
		}
	}

	/** @return the room of the column that moves by a stack */
	private float roomOf(CutoutScroll stack) {
		for (Shown shown : lines) {
			if (shown.on() && shown.stack == stack) {
				return shown.room;
			}
		}
		return plannedRoom;
	}

	/** @return the first row of a line, or -1 */
	private int firstRow(int index) {
		measureRows();
		return index >= 0 && index < firstRow.length ? firstRow[index] : -1;
	}

	/** @return the row of a line being sung at a time: the last that has started, or its first */
	private int rowAt(int index, long at) {
		int row = firstRow(index);
		if (row < 0) {
			return -1;
		}
		while (row + 1 < rows && rowLine[row + 1] == index && rowStart[row + 1] <= at) {
			row++;
		}
		return row;
	}

	/** Measures the rows of every Cut-out line of the song, once per lyrics and look; a column then starts its stack anew. */
	private void measureRows() {
		List<LyricLine> all = motion.lines;

		if (all == planOf) {
			return;
		}
		planOf = all;
		CutoutLyrics.Measure[] measures = new CutoutLyrics.Measure[all.size()];
		int count = 0;
		for (int i = 0; i < all.size(); i++) {
			LyricLine line = all.get(i);
			if (LyricTimes.main(line) && !LyricBreaks.is(line) && !line.adlibOnly()) {
				measures[i] = cutout.measure(line, LyricTimes.lineEnd(all, i));
				count += measures[i].heights().length;
			}
		}
		firstRow = new int[all.size()];
		java.util.Arrays.fill(firstRow, -1);
		rows = count;
		rowHeight = new float[count];
		rowFit = new float[count];
		rowStart = new long[count];
		rowLine = new int[count];
		rowRepeat = new int[count];
		java.util.Arrays.fill(rowRepeat, -1);
		stackTop = new float[count];
		int r = 0;
		int before = -1;
		for (int i = 0; i < all.size(); i++) {
			if (measures[i] == null || measures[i].heights().length == 0) {
				continue;
			}
			firstRow[i] = r;
			float[] heights = measures[i].heights();
			// the line before sung again right away (case, marks and ad-libs aside): it takes that line's place instead of standing under it
			boolean again = before >= 0 && LyricChorus.sameLine(all.get(i).text(), all.get(before).text())
				&& measures[before].heights().length == heights.length;
			for (int g = 0; g < heights.length; g++) {
				rowFit[r] = Math.max(1f, heights[g]);
				rowHeight[r] = rowFit[r] + (g == heights.length - 1 ? LINE_SPACE : 0f);
				long start = measures[i].starts()[g];
				rowStart[r] = start == Long.MAX_VALUE ? all.get(i).timeMs() : start;
				rowLine[r] = i;
				rowRepeat[r] = again ? firstRow[before] + g : -1;
				r++;
			}
			before = i;
		}
		plannedRoom = -1f;
		stackNow = null;
	}

	/** @return where a line's rows stand and when they come and go, by its column's plan; rows before the column's first never show */
	private CutoutLyrics.Part part(Shown shown) {
		int first = firstRow(shown.index);
		if (first < 0 || shown.column == null) {
			return null;
		}
		int count = 0;
		while (first + count < rows && rowLine[first + count] == shown.index) {
			count++;
		}
		float[] tops = new float[count];
		float[] fit = new float[count];
		long[] appear = new long[count];
		long[] goneFrom = new long[count];
		long[] goneFor = new long[count];
		for (int g = 0; g < count; g++) {
			int r = first + g;
			fit[g] = rowFit[r];
			goneFor[g] = CutoutStack.OUT_MS;
			goneFrom[g] = Long.MAX_VALUE;
			if (r < shown.fromRow) {
				appear[g] = Long.MAX_VALUE;
			} else {
				tops[g] = stackTop[r];
				// scrolling, a row is there as it reaches the room; on a page, a line comes in whole as it starts, under the lines before
				appear[g] = shown.column == CutoutFlow.Mode.SCROLL ? Long.MIN_VALUE : rowStart[first] - CutoutStack.IN_MS;
			}
			// the line sung again right away takes this row's place: this one goes as the new one comes
			int again = takenBy(r);
			if (again >= 0 && appear[g] != Long.MAX_VALUE) {
				long from = rowStart[firstRow[rowLine[again]]] - REPLACE_MS / 2;
				if (from < goneFrom[g]) {
					goneFrom[g] = from;
					goneFor[g] = REPLACE_MS;
				}
			}
		}
		return new CutoutLyrics.Part(tops, fit, appear, goneFrom, goneFor);
	}

	/** Gives a line the place of the column it joins: its spot, size and turn, and the column's flow, room and stack. */
	private static void copyPlace(Shown from, Shown to) {
		to.x = from.x;
		to.y = from.y;
		to.z = from.z;
		to.facing = from.facing;
		to.tilt = from.tilt;
		to.faced = from.faced;
		to.unit = from.unit;
		to.halfWidth = from.halfWidth;
		to.halfHeight = from.halfHeight;
		to.sky = from.sky;
		to.placedGrow = from.placedGrow;
		to.kind = from.kind;
		to.placedDistance = from.placedDistance;
		to.cover = from.cover;
		to.wrap = from.wrap;
		to.textScale = from.textScale;
		to.placedColumn = from.placedColumn;
		to.rise = from.rise;
		to.shot = from.shot;
		to.boxY = from.boxY;
		to.boxShift = from.boxShift;
		to.boxHalfWidth = from.boxHalfWidth;
		to.boxHalfHeight = from.boxHalfHeight;
		to.boxRightX = from.boxRightX;
		to.boxRightZ = from.boxRightZ;
		to.column = from.column;
		to.room = from.room;
		to.stack = from.stack;
	}

	/** Notes when the player moves, for the Auto flow: still for a while, the column scrolls; moving, it turns pages. */
	private void watchMoving(Minecraft client) {
		var player = client.player;
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		if (Double.isNaN(lastX) || Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ) > MOVED_BLOCKS) {
			flow.moved(System.currentTimeMillis());
		}
		// the speed and the way the player goes, measured over a fifth of a second: for a block placed ahead while moving fast
		sinceSpeed += dt;
		if (Double.isNaN(speedX)) {
			speedX = x;
			speedY = y;
			speedZ = z;
			sinceSpeed = 0f;
		} else if (sinceSpeed >= SPEED_SECONDS) {
			double way = Math.sqrt((x - speedX) * (x - speedX) + (y - speedY) * (y - speedY) + (z - speedZ) * (z - speedZ));
			speed = way / sinceSpeed;
			if (way > 1e-3) {
				goX = (x - speedX) / way;
				goY = (y - speedY) / way;
				goZ = (z - speedZ) / way;
			}
			speedX = x;
			speedY = y;
			speedZ = z;
			sinceSpeed = 0f;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
	}

	/**
	 * The column's upkeep each frame: a line whose rows the stack has carried out of the top of its room is gone; a column whose last line is
	 * followed by a long pause goes as a whole; and the stack learns the places of the rows coming.
	 */
	private void keepColumn() {
		boolean ends = false;
		for (Shown shown : lines) {
			ends |= shown.on() && shown.column != null && shown.leaving && !shown.pushed && shown.beforeGap;
		}
		for (Shown shown : lines) {
			if (!shown.on() || shown.column == null) {
				continue;
			}
			if (ends && !shown.leaving) {
				shown.leaving = true;
			}
			if (shown != current && shown.part != null && gone(shown)) {
				shown.clear();
			}
		}
		if (current != null && current.column != null && current.stack == stackNow) {
			stackAhead(current.index);
		}
	}

	/** @return whether all of a column line's rows have gone out of the top of the column's room */
	private boolean gone(Shown shown) {
		CutoutLyrics.Part part = shown.part;
		int last = part.tops().length - 1;
		if (last < 0) {
			return false;
		}
		if (shown.stack != null) {
			return part.tops()[last] + part.fit()[last] - shown.stack.at(scrollAt) < 0f;
		}
		long from = part.goneFrom()[last];
		return from != Long.MAX_VALUE && scrollAt >= from + part.goneFor()[last];
	}

	// ------------------------------------------------------------------------------------------------ placed by the view

	/** The room kept around every HUD panel, in GUI pixels. */
	private static final int HUD_MARGIN_PIXELS = 10;
	/**
	 * Where the hand and the item it holds lie in first person: from this far across to the screen's side, and up to this height; in third person
	 * the player's own model, around the middle up to its height.
	 */
	private static final double HAND_FROM = 0.3;
	private static final double HAND_TOP = -0.2;
	private static final double MODEL_HALF_WIDTH = 0.2;
	private static final double MODEL_TOP = 0.3;
	/**
	 * Moving faster than this (walking on, sprinting, riding, flying), a block around the player is placed ahead, in blocks per second; it then
	 * stands this many times the way made while its line is sung away (so that way changes its size on the screen by a fifth at most, under the
	 * drift that would fade it out); a line counts for this long at least and at most.
	 */
	private static final double MOVING_BLOCKS_PER_SECOND = 2.0;
	private static final double AHEAD_WAYS = 6.0;
	private static final long AHEAD_LEAST_MS = 2000L;
	private static final long AHEAD_MOST_MS = 8000L;
	/** The speed is measured over this long, in seconds. */
	private static final float SPEED_SECONDS = 0.2f;
	/** A Cut-out block wants this many rows. */
	private static final int WANTED_ROWS = 6;
	/**
	 * Before a wall a block is drawn at this share of the way to the wall, at least this near: it lies over that wall and over what grows or hangs before
	 * it (crops, grass, a torch), which the rays of the grid pass; the hand still lies over it.
	 */
	private static final double WALL_COVER_SHARE = 0.4;
	private static final double LEAST_COVER_BLOCKS = 0.3;

	/** A block's size and place are fitted to what was chosen this many times as it is stood, its size by no more than this either way each time. */
	private static final int FIT_PASSES = 3;
	private static final double LEAST_FIT = 0.5;
	private static final double MOST_FIT = 1.25;
	/** A view whose direction has less than this of its length across the ground looks straight up or down: a block then turns by the view's yaw. */
	private static final double STRAIGHT_UP_OR_DOWN = 0.05;

	private final LyricSpots.Ask ask = new LyricSpots.Ask();
	/** The one size tried for a size the player chose. */
	private static final float[] ONE_SCALE = {1f};
	private final LyricSpots.Spot chosen = new LyricSpots.Spot();
	/** The side a song's blocks keep to, where they can. */
	private final CutoutAnchor anchor = new CutoutAnchor();
	private final double[] blockPlace = new double[2];
	private final double[] blockRect = new double[4];
	private final double[] gridPlace = new double[2];
	/** The player's speed in blocks per second and the way they go, and where they were when it was last measured. */
	private double speed;
	private double goX;
	private double goY;
	private double goZ;
	private double speedX = Double.NaN;
	private double speedY;
	private double speedZ;
	private float sinceSpeed;

	private LyricSpots.Mode mode() {
		return switch (music.worldPlacement.get()) {
			case AUTO -> LyricSpots.Mode.AUTO;
			case AROUND -> LyricSpots.Mode.AROUND;
			case SKY -> LyricSpots.Mode.SKY;
		};
	}

	/** Writes where the crosshair of the view now lies in the picture of the grid's last look into {@link #gridPlace}. @return whether it lies in front there */
	private boolean crosshairInGrid(float viewYaw, float viewPitch) {
		WorldLyricView.direction(0.0, 0.0, viewYaw, viewPitch, halfFovY, aspect, wantedDirection);
		return WorldLyricView.project(wantedDirection[0], wantedDirection[1], wantedDirection[2], openSky.yaw(), openSky.pitch(), openSky.halfFovY(), openSky.aspect(), gridPlace);
	}

	/**
	 * Chooses a block's spot from the view ({@link LyricSpots}), into {@link #chosen}: by the grid's last look, with the crosshair and the horizon
	 * where they lie in that look's picture now, clear of every HUD panel shown, of the hand and the held item, and of the lines still standing.
	 *
	 * @param halfWidth    half the block's width at its size on the screen, as a share of the screen's half width
	 * @param rowHeight    the height of one of its rows, as a share of the screen's half height
	 * @param clearOfLines whether it keeps clear of the lines still standing
	 * @return whether there is a spot; without one nothing is drawn in the world
	 */
	private boolean chooseSpot(Minecraft client, Shown placing, double halfWidth, double rowHeight, int wantedRows, int leastRows, boolean clearOfLines, float viewYaw,
							   float viewPitch) {
		chosen.kind = LyricSpots.Kind.NONE;
		if (!openSky.fresh() || !crosshairInGrid(viewYaw, viewPitch)) {
			return false;
		}
		float gridYaw = openSky.yaw();
		float gridPitch = openSky.pitch();
		double fov = openSky.halfFovY();
		double shape = openSky.aspect();
		ask.mode = mode();
		ask.depth = openSky.distance();
		ask.sky = openSky.open();
		ask.light = openSky.light();
		ask.columns = WorldLyricOpenSky.COLUMNS;
		ask.rows = WorldLyricOpenSky.ROWS;
		ask.aspect = shape;
		ask.crossX = gridPlace[0];
		ask.crossY = gridPlace[1];
		double yaw = Math.toRadians(viewYaw);
		ask.horizonY = WorldLyricView.project(-Math.sin(yaw), 0.0, Math.cos(yaw), gridYaw, gridPitch, fov, shape, gridPlace) ? gridPlace[1] : viewPitch > 0f ? 2.0 : -2.0;
		ask.halfWidth = halfWidth;
		ask.rowHeight = rowHeight;
		ask.wantedRows = wantedRows;
		ask.leastRows = leastRows;
		// Auto: the size from the room at this placement, a step smaller for a long line or a block of lines, and all of it in its free area
		boolean auto = music.worldSize.get() == LyricReach.Size.AUTO;
		LyricLine line = motion.line(placing.index);
		String text = line == null ? "" : line.text();
		ask.scales = auto ? LyricReach.autoScales(LyricTimes.words(text), text.indexOf(LyricGroups.BREAK) >= 0) : ONE_SCALE;
		ask.wholeInSky = auto;
		ask.takenCount = 0;
		takeHud(client);
		takeHand(client);
		if (clearOfLines) {
			for (Shown shown : lines) {
				if (shown != placing && shown.on() && !shown.leaving && shown.alpha() > VISIBLE_ALPHA && blockOnScreen(shown, gridYaw, gridPitch, fov, shape, blockRect)) {
					ask.take(blockRect[0], blockRect[1], blockRect[2], blockRect[3]);
				}
			}
		}
		anchor.song(MusicService.trackNumber());
		ask.lastSide = anchor.side();
		ask.moving = speed > MOVING_BLOCKS_PER_SECOND;
		if (ask.moving) {
			// the way ahead where it lies in the picture; moving backwards or sideways out of it, the crosshair stands for it
			boolean ahead = WorldLyricView.project(goX, goY, goZ, gridYaw, gridPitch, fov, shape, gridPlace);
			ask.aheadX = ahead ? Math.clamp(gridPlace[0], -1.0, 1.0) : ask.crossX;
			ask.aheadY = ahead ? Math.clamp(gridPlace[1], -1.0, 1.0) : ask.crossY;
			ask.aheadBlocks = speed * Math.clamp(placing.lengthMs, AHEAD_LEAST_MS, AHEAD_MOST_MS) / 1000.0 * AHEAD_WAYS;
		}
		return LyricSpots.choose(ask, chosen);
	}

	/** Takes every element of the HUD shown now (the chat, the scoreboard, the info bar, the panels) with a margin; they lie on the screen, so they move with the crosshair in the grid's picture. */
	private void takeHud(Minecraft client) {
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		if (width <= 0 || height <= 0) {
			return;
		}
		for (dev.tim1337.lumio.ui.EditableHud hud : ModFeatures.editableHuds()) {
			int[] box = hud.hudShown() ? hud.hudBounds() : null;
			if (box != null && box[2] > 0 && box[3] > 0) {
				ask.take(ask.crossX + (box[0] - HUD_MARGIN_PIXELS) * 2.0 / width - 1.0, ask.crossX + (box[0] + box[2] + HUD_MARGIN_PIXELS) * 2.0 / width - 1.0,
					ask.crossY + 1.0 - (box[1] + box[3] + HUD_MARGIN_PIXELS) * 2.0 / height, ask.crossY + 1.0 - (box[1] - HUD_MARGIN_PIXELS) * 2.0 / height);
			}
		}
	}

	/** Takes where the hand and the item it holds lie in first person (both lower corners with an item in the other hand), or the player's model in third person. */
	private void takeHand(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (!client.options.getCameraType().isFirstPerson()) {
			ask.take(ask.crossX - MODEL_HALF_WIDTH, ask.crossX + MODEL_HALF_WIDTH, ask.crossY - 1.0, ask.crossY + MODEL_TOP);
			return;
		}
		boolean right = client.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		boolean both = !client.player.getOffhandItem().isEmpty();
		if (right || both) {
			ask.take(ask.crossX + HAND_FROM, ask.crossX + 1.0, ask.crossY - 1.0, ask.crossY + HAND_TOP);
		}
		if (!right || both) {
			ask.take(ask.crossX - 1.0, ask.crossX - HAND_FROM, ask.crossY - 1.0, ask.crossY + HAND_TOP);
		}
	}

	/**
	 * Stands a block at the spot chosen: fixed in the world from now on, at the distance of its kind of spot, as large in the world as gives it its
	 * size on the screen at that distance and this field of view. It is never rescaled afterwards.
	 *
	 * @param perBlock    its size, in blocks per text unit and block of distance
	 * @param widthUnits  its width and ({@code heightUnits}) height, in text units
	 */
	private void standAt(Shown shown, float perBlock, float widthUnits, float heightUnits) {
		double distance = chosen.distance;
		float size = perBlock * chosen.scale;
		double aimX = chosen.x;
		double aimY = chosen.y;
		// it stands square to the view of this moment, not to the line from the eye: upright, turned the way the player looks and leaned as the view
		// is, so it is seen level on the screen and not as if rolled, also off to the side and looking steeply down or up
		WorldLyricView.direction(ask.crossX, ask.crossY, openSky.yaw(), openSky.pitch(), openSky.halfFovY(), openSky.aspect(), wantedDirection);
		double yaw = Math.toRadians(openSky.yaw());
		boolean steep = Math.hypot(wantedDirection[0], wantedDirection[2]) < STRAIGHT_UP_OR_DOWN;
		float facing = LyricUpright.facing(steep ? -Math.sin(yaw) : wantedDirection[0], steep ? Math.cos(yaw) : wantedDirection[2]);
		float tilt = LyricUpright.leanToward(Math.asin(Math.clamp(wantedDirection[1], -1.0, 1.0)));
		// off the middle of the view a block is seen a little larger than its angles say, and a little off its middle; so its size and its
		// place are fitted, until what is drawn lies where it was chosen and is as large as it was chosen
		for (int pass = 0; pass <= FIT_PASSES; pass++) {
			WorldLyricView.direction(aimX, aimY, openSky.yaw(), openSky.pitch(), openSky.halfFovY(), openSky.aspect(), wantedDirection);
			shown.x = eye.x + wantedDirection[0] * distance;
			shown.y = eye.y + wantedDirection[1] * distance;
			shown.z = eye.z + wantedDirection[2] * distance;
			shown.unit = size * (float) distance;
			shown.halfWidth = (float) Math.toDegrees(Math.atan(widthUnits / 2f * size));
			shown.halfHeight = (float) Math.toDegrees(Math.atan(heightUnits / 2f * size));
			shown.facing = facing;
			shown.tilt = tilt;
			if (pass == FIT_PASSES || !blockOnScreen(shown, openSky.yaw(), openSky.pitch(), openSky.halfFovY(), openSky.aspect(), blockRect)) {
				break;
			}
			double seenHalfWidth = (blockRect[1] - blockRect[0]) / 2.0;
			double seenHalfHeight = (blockRect[3] - blockRect[2]) / 2.0;
			if (seenHalfWidth > 1e-6 && seenHalfHeight > 1e-6) {
				size *= (float) Math.clamp(Math.min(ask.halfWidth * chosen.scale / seenHalfWidth, chosen.rows * ask.rowHeight * chosen.scale / 2.0 / seenHalfHeight), LEAST_FIT, MOST_FIT);
			}
			aimX += chosen.x - (blockRect[0] + blockRect[1]) / 2.0;
			aimY += chosen.y - (blockRect[2] + blockRect[3]) / 2.0;
		}
		shown.sky = chosen.kind == LyricSpots.Kind.SKY;
		shown.kind = chosen.kind;
		shown.placedDistance = distance;
		shown.cover = chosen.kind == LyricSpots.Kind.WALL ? Math.clamp(chosen.surface * WALL_COVER_SHARE, LEAST_COVER_BLOCKS, WorldLyricDepth.COVER_BLOCKS)
			: WorldLyricDepth.COVER_BLOCKS;
		anchor.placed(chosen.x - ask.crossX);
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: placed {} at {} blocks, {} rows, size {}, at {} {} of the view, quiet zone {} %, {} x {} degrees", shown.index,
				chosen.kind, Math.round(distance * 10.0) / 10.0, chosen.rows, chosen.scale, Math.round((chosen.x - ask.crossX) * 100.0) / 100.0,
				Math.round((chosen.y - ask.crossY) * 100.0) / 100.0, Math.round(chosen.quiet * 100f), Math.round(shown.halfWidth * 2f), Math.round(shown.halfHeight * 2f));
		}
	}

	/** @return the size a Cut-out block is placed at, in blocks per text unit and block of distance: by the Size setting alone */
	private float cutoutPerBlock() {
		return LyricReach.limited(LyricReach.placed(cutoutEm(), halfFovY), cutoutEm(), CutoutLyrics.COLUMN, halfFovY, aspect);
	}

	/** @return the width of a Cut-out block, in text units: its words, and beside them room for the ad-libs where those show */
	private float cutoutWidth() {
		return music.adlibsShown() ? CutoutLyrics.COLUMN * CutoutLyrics.ADLIB_ROOM : CutoutLyrics.COLUMN;
	}

	/** Chooses the spot of a Cut-out block for the view now, into {@link #chosen}; its rows as many as fit, down to one. */
	private boolean chooseCutout(Minecraft client, Shown placing, float viewYaw, float viewPitch) {
		float perBlock = cutoutPerBlock();
		double tan = Math.tan(Math.toRadians(halfFovY));
		double rowHeight = CutoutLyrics.height(1) * perBlock / tan;
		int wanted = Math.clamp((int) (LyricReach.MOST_BLOCK_HEIGHT * 2.0 / rowHeight), 1, WANTED_ROWS);
		return chooseSpot(client, placing, cutoutWidth() / 2.0 * perBlock / (tan * aspect), rowHeight, wanted, 1, false, viewYaw, viewPitch);
	}

	/**
	 * Gives a Cut-out column its spot in the view of this moment. Without one (Sky with no sky in view, or no room for a single row) the line is
	 * not shown in the world.
	 *
	 * @return the column's room: its height in text units
	 */
	private float placeCutoutAnew(Minecraft client, Shown fresh, float viewYaw, float viewPitch) {
		if (!chooseCutout(client, fresh, viewYaw, viewPitch)) {
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: no spot in the view, not shown in the world", fresh.index);
			}
			fresh.text = null;
			return CutoutLyrics.height(1);
		}
		float room = CutoutLyrics.height(chosen.rows);
		float width = cutoutWidth();
		standAt(fresh, cutoutPerBlock(), width, room);
		fresh.wrap = WRAP_WIDTH;
		fresh.textScale = 1f;
		fresh.wordsShare = CutoutLyrics.COLUMN / width;
		fresh.placedColumn = true;
		return room;
	}

	/** How often a block's readability is measured (a few rays each), in seconds. */
	private static final float READ_CHECK_SECONDS = 0.2f;
	/** The points over a block that the rays go to: this many across and up. */
	private static final int READ_SAMPLES = 3;
	/** A ray that ends this much before a point did not reach it. */
	private static final double READ_MARGIN_BLOCKS = 0.3;
	private float readCheckIn;

	/**
	 * Looks after the blocks standing, every frame ({@link LyricReadable}). A placed block keeps its place in the world whatever the view does,
	 * and turns on the spot to face the player, slowly, upright and never rolled. Its readability is measured a few times a second; one that is
	 * unreadable for longer than a second is replaced at the next line start ({@link #replaceUnreadable}), never in the middle of a line.
	 */
	private void watchStanding(float viewYaw, float viewPitch) {
		readCheckIn -= dt;
		boolean measure = readCheckIn <= 0f;
		if (measure) {
			readCheckIn = READ_CHECK_SECONDS;
		}
		long frameMs = Math.round(dt * 1000f);
		for (Shown shown : lines) {
			if (!shown.on() || shown.leaving || shown.kind == LyricSpots.Kind.NONE) {
				continue;
			}
			float[] facingEye = LyricReadable.facingEye(eye.x - shown.x, eye.y - shown.y, eye.z - shown.z);
			// a new block starts facing the player (it never swings in as it appears); later it follows slowly
			boolean follow = animated && shown.faced;
			shown.facing = follow ? LyricReadable.follow(shown.facing, facingEye[0], dt) : facingEye[0];
			shown.tilt = follow ? LyricReadable.follow(shown.tilt, facingEye[1], dt) : facingEye[1];
			shown.faced = true;
			if (measure) {
				double size = shown.placedDistance / Math.max(0.1, distance(shown));
				shown.unreadableNow = LyricReadable.unreadable(hiddenShare(shown), outsideShare(shown, viewYaw, viewPitch), size);
			}
			shown.unreadableMs = LyricReadable.unreadableFor(shown.unreadableMs, shown.unreadableNow, frameMs);
		}
	}

	/**
	 * At a line start: a block unreadable for longer than a second fades out over {@link LyricReadable#REPLACED_SECONDS}, and the line is placed
	 * fresh by the usual rules.
	 */
	private void replaceUnreadable() {
		for (Shown shown : lines) {
			if (shown.on() && !shown.leaving && shown.kind != LyricSpots.Kind.NONE && LyricReadable.replaceAtLineStart(shown.unreadableMs)) {
				shown.leaving = true;
				shown.replaced = true;
				endedOutOfView++;
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: unreadable for {} s, it fades out; the next line is placed anew", shown.index,
						Math.round(shown.unreadableMs / 100f) / 10f);
				}
			}
		}
	}

	/** @return the share of a block's area hidden by something nearer than it, and nearer than {@link LyricReadable#NEAR_COVER_BLOCKS} */
	private double hiddenShare(Shown shown) {
		double distance = distance(shown);
		double across = Math.tan(Math.toRadians(shown.halfWidth)) * distance * shown.wordsShare;
		double up = Math.tan(Math.toRadians(shown.halfHeight)) * distance;
		double[] axes = LyricUpright.axes(shown.facing, shown.tilt);
		int hidden = 0;
		for (int i = 0; i < READ_SAMPLES; i++) {
			for (int k = 0; k < READ_SAMPLES; k++) {
				double side = (i - (READ_SAMPLES - 1) / 2.0) / ((READ_SAMPLES - 1) / 2.0) * 0.8;
				double rise = (k - (READ_SAMPLES - 1) / 2.0) / ((READ_SAMPLES - 1) / 2.0) * 0.8;
				double dx = shown.x - eye.x + side * across * axes[3] + rise * up * axes[0];
				double dy = shown.y - eye.y + side * across * axes[4] + rise * up * axes[1];
				double dz = shown.z - eye.z + side * across * axes[5] + rise * up * axes[2];
				double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
				// a far block is drawn over far terrain on purpose: only something near hides it
				double limit = Math.min(length * shown.nearer, LyricReadable.NEAR_COVER_BLOCKS);
				if (length > 1e-6 && reach(dx / length, dy / length, dz / length, limit) < limit - READ_MARGIN_BLOCKS) {
					hidden++;
				}
			}
		}
		return hidden / (double) (READ_SAMPLES * READ_SAMPLES);
	}

	/** @return the share of a block's area off the screen: all of it when a corner lies behind the eye */
	private double outsideShare(Shown shown, float viewYaw, float viewPitch) {
		if (!blockOnScreen(shown, viewYaw, viewPitch, halfFovY, aspect, readRect)) {
			return 1.0;
		}
		double area = (readRect[1] - readRect[0]) * (readRect[3] - readRect[2]);
		if (area <= 1e-9) {
			return 1.0;
		}
		double seenAcross = Math.max(0.0, Math.min(1.0, readRect[1]) - Math.max(-1.0, readRect[0]));
		double seenUp = Math.max(0.0, Math.min(1.0, readRect[3]) - Math.max(-1.0, readRect[2]));
		return 1.0 - seenAcross * seenUp / area;
	}

	private final double[] readRect = new double[4];

	/** Blocks ended because they were out of view too long, for the test log. */
	int endedOutOfView;

	/** @return an em of a Cut-out column's smallest words, in text units: what the least size on the screen goes by, so no word is under it */
	private static float cutoutEm() {
		return LyricSprites.cleanSize() * CutoutLyrics.BIG * CutoutWords.SMALL;
	}

	/** @return an em of the main words of a line being placed, at full size, in text units */
	private static float placedEm(Shown shown) {
		return shown.boxText != null ? cutoutEm() : LyricSprites.cleanSize();
	}

	/** @return how much a line grows as it is drawn, to keep its words' size on the screen */
	private float drawnGrow(Shown shown) {
		return LyricReach.grow(mainEm(shown) * shown.unit, distance(shown), halfFovY);
	}

	/**
	 * Where a block lies on the screen, from its four corners as it stands in the world.
	 *
	 * @param out receives left, right, bottom and top, from -1 to 1
	 * @return whether all of it lies in front of the eye
	 */
	private boolean blockOnScreen(Shown shown, float viewYaw, float viewPitch, double fov, double shape, double[] out) {
		double distance = distance(shown);
		double across = Math.tan(Math.toRadians(shown.halfWidth)) * distance * shown.wordsShare;
		double up = Math.tan(Math.toRadians(shown.halfHeight)) * distance;
		double[] axes = LyricUpright.axes(shown.facing, shown.tilt);
		out[0] = Double.MAX_VALUE;
		out[1] = -Double.MAX_VALUE;
		out[2] = Double.MAX_VALUE;
		out[3] = -Double.MAX_VALUE;
		for (int side = -1; side <= 1; side += 2) {
			for (int rise = -1; rise <= 1; rise += 2) {
				double dx = shown.x - eye.x + side * across * axes[3] + rise * up * axes[0];
				double dy = shown.y - eye.y + side * across * axes[4] + rise * up * axes[1];
				double dz = shown.z - eye.z + side * across * axes[5] + rise * up * axes[2];
				if (!WorldLyricView.project(dx, dy, dz, viewYaw, viewPitch, fov, shape, blockPlace)) {
					return false;
				}
				out[0] = Math.min(out[0], blockPlace[0]);
				out[1] = Math.max(out[1], blockPlace[0]);
				out[2] = Math.min(out[2], blockPlace[1]);
				out[3] = Math.max(out[3], blockPlace[1]);
			}
		}
		return true;
	}

	/** @return where a block lies in the view: all of it inside, at an edge, or nothing of it on the screen */
	private CutoutPlace.Where whereInView(Shown shown, float viewYaw, float viewPitch) {
		boolean inFront = WorldLyricView.project(shown.x - eye.x, shown.y - eye.y, shown.z - eye.z, viewYaw, viewPitch, halfFovY, aspect, screenPlace);
		return CutoutPlace.where(inFront, screenPlace[0], screenPlace[1], WorldLyricView.share(shown.halfWidth, halfFovY) / aspect,
			WorldLyricView.share(shown.halfHeight, halfFovY));
	}

	/**
	 * A column the view has left moves as a whole to the spot the sung line was just given (nothing of it is seen meanwhile), and comes in there
	 * anew. Its stack keeps its lines at the bottom of the new room.
	 */
	private void moveColumn(Shown lead, float room) {
		float before = lead.room;
		if (lead.stack != null) {
			lead.stack.shift(before - room);
		}
		lead.room = room;
		for (Shown shown : lines) {
			if (shown != lead && shown.on() && shown.column != null && !shown.leaving) {
				copyPlace(lead, shown);
				shown.appear = 0f;
			}
		}
		lead.appear = 0f;
		lead.part = part(lead);
	}

	/**
	 * In the cinematic camera a Cut-out column is placed anew at most once per shot ({@link #replacedInShot}). A shot that has no room for it (no
	 * spot keeps it fully in the picture, or it is lost a second time) shows no lyrics in the world at all ({@link #hiddenShot}): they fade out
	 * over {@link #FILM_HIDE_SECONDS} and come back with the next shot. The info bar goes on showing the line.
	 */
	private int replacedInShot = -1;
	private int hiddenShot = -1;
	private float filmShown = 1f;
	private static final float FILM_HIDE_SECONDS = 0.4f;

	/** The shot has no room for the lyrics: they fade out until the next shot. */
	private void hideForShot(CinematicCameraFeature film, int index, String why) {
		if (hiddenShot == film.shotNumber()) {
			return;
		}
		hiddenShot = film.shotNumber();
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "Cinematic lyric {}: {} shot has no room for the column ({}), the lyrics in the world fade until the next shot", index,
				film.shotName(), why);
		}
	}

	/** @return how much the lyrics in the world show in this shot of the film: fading out in a shot without room, back in at the next */
	private float filmShownNow(CinematicCameraFeature film) {
		float target = hiddenShot == film.shotNumber() ? 0f : 1f;
		float step = animated ? dt / FILM_HIDE_SECONDS : 1f;
		filmShown = target < filmShown ? Math.max(target, filmShown - step) : Math.min(target, filmShown + step);
		return filmShown;
	}

	/** The lines of a column the cinematic camera has left are gone at once (nothing of them is seen); the sung one starts the column anew. */
	private void leaveColumn(Shown keep) {
		for (Shown shown : lines) {
			if (shown != keep && shown.on() && shown.column != null) {
				shown.clear();
			}
		}
	}
	private final float[] filmColumnBox = new float[4];
	/** How long the film's column has been out of the picture, in seconds. */
	private float cutoutOutsideSeconds;

	/**
	 * The same rule in the cinematic camera, measured against the picture between the letterbox bars: a column the shot has left for a moment
	 * is cleared and placed anew in the shot with the next word. The camera's slow drift never moves one that is still seen.
	 */
	private void replaceLostFilmCutout(Minecraft client, CinematicCameraFeature film, float size) {
		Shown column = current;
		if (column == null || !column.on() || column.boxText == null || column.leaving || !film.lyricPlaced() || hiddenShot == film.shotNumber()) {
			cutoutOutsideSeconds = 0f;
			return;
		}
		boolean inFront = film.lyricBoxNow(column.x, column.boxY, column.z, column.boxRightX, column.boxRightZ, column.boxHalfWidth, column.boxHalfHeight,
			filmColumnBox);
		CutoutPlace.Where where = CutoutPlace.where(inFront, (filmColumnBox[0] + filmColumnBox[2]) / 2.0, (filmColumnBox[1] + filmColumnBox[3]) / 2.0,
			(filmColumnBox[2] - filmColumnBox[0]) / 2.0, (filmColumnBox[3] - filmColumnBox[1]) / 2.0);
		cutoutOutsideSeconds = where == CutoutPlace.Where.OUTSIDE ? cutoutOutsideSeconds + dt : 0f;
		if (CutoutPlace.clearShot(where, (long) (cutoutOutsideSeconds * 1000f))) {
			cutoutOutsideSeconds = 0f;
			if (replacedInShot == film.shotNumber()) {
				// placed anew once in this shot already and out of the picture again: the shot has no room for it, so the lyrics fade until the next shot
				hideForShot(film, column.index, "lost again after it was placed anew");
				return;
			}
			replacedInShot = film.shotNumber();
			leaveColumn(column);
			placeInShot(client, film, column, size, music.lyricClockMs());
			startColumn(column, CutoutLyrics.height(BOX_GROUPS) / drawnGrow(column), rowAt(column.index, music.lyricAt));
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.MUSIC, "Cinematic lyric {}: its column was out of the picture, placed anew", column.index);
			}
		}
	}

	/**
	 * In the cinematic camera a new line gets a spot of its own, fixed for its whole life: first the shot's open area (see
	 * {@link CinematicCameraFeature#lyricOpen}), far away and sized to it, then the shot's own spot as before; at either the spot itself when it is
	 * free, else below or above the line still fading out. A spot counts only when the line's whole box (its rows, adlibs and glow) stays inside the
	 * safe part of the picture along the shot's planned camera path for all of its time, clear of the song card and the character and not behind
	 * blocks (see {@link CinematicCameraFeature#lyricMargin}). Without such a spot it stands beside the player the camera looks at (see
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
		int rows = rowsOf(client, boxText(fresh), fresh.wrap);
		float top = LyricSprites.boundsTop(rows);
		float bottom = LyricSprites.boundsBottom(rows) + adlibRoom(fresh.index) * ROW;
		float total = (bottom - top) / ROW;
		// the box's middle below the line's, in text units: the adlibs' room is below it
		float shift = (top + bottom) / 2f;
		double halfWidthUnits = LyricSprites.boundsWidth(lineWidth(client, fresh)) / 2.0;
		if (fresh.boxText != null) {
			// a Cut-out column: as tall as its page, centered on its spot, and as wide as its ad-libs beside the words reach
			total = CutoutLyrics.height(BOX_GROUPS) / ROW;
			shift = 0f;
			halfWidthUnits = music.adlibsShown() ? CutoutLyrics.ADLIB_REACH : CutoutLyrics.COLUMN / 2f;
		}
		double halfHeightUnits = total * ROW / 2.0;
		float seconds = Math.max(0f, (fresh.endMs - clockMs) / 1000f) + FILM_HOLD_MS / 1000f + OUT_SECONDS;
		// the line before may stand elsewhere than the shot's spot (beside the player, in a push-in): its box in the picture is what must stay clear
		boolean olderVisible = older != null && film.lyricBoxNow(older.x, older.boxY, older.z, older.boxRightX, older.boxRightZ, older.boxHalfWidth,
			older.boxHalfHeight, olderBox);
		shotMisses = "";
		if (film.lyricOpen()) {
			float openUnit = openUnit(film, unit, halfWidthUnits, halfHeightUnits);
			if (openUnit > 0f && placeNear(client, film, fresh, older, olderVisible, film.openX(), film.openY(), film.openZ(), openUnit, total, shift,
				halfWidthUnits, seconds, "open sky")) {
				return;
			}
		}
		if (placeNear(client, film, fresh, older, olderVisible, film.lyricX(), film.lyricY(), film.lyricZ(), unit, total, shift, halfWidthUnits, seconds,
			"the shot's spot")) {
			return;
		}
		// no spot by the shot's lyric place stays in the picture: one beside the player the camera looks at, which stays in the frame, made a
		// little smaller when it spawns if that is what it takes (never afterwards)
		besideFocus(film, fresh, older, unit, shift, total, halfWidthUnits, seconds, shotMisses);
	}

	/** Why the spots of the shot tried last failed, for the debug log. */
	private String shotMisses = "";
	/** In the shot's open area a line fills at most this share of it, and is at least and at most this share of its usual size as seen. */
	private static final float FILM_OPEN_FILL = 0.85f;
	private static final float FILM_OPEN_LEAST = 0.7f;
	private static final float FILM_OPEN_MOST = 1.3f;

	/**
	 * @return the size of a line in the shot's open area, in blocks per text unit: as large as fits the area, about its usual size as seen from the
	 *         camera; 0 when it would have to be much smaller than usual there
	 */
	private float openUnit(CinematicCameraFeature film, float unit, double halfWidthUnits, double halfHeightUnits) {
		double fits = Math.min(film.openHalfWidth() / halfWidthUnits, film.openHalfHeight() / halfHeightUnits) * FILM_OPEN_FILL;
		// its usual size as seen: the shot's spot's size, at the open area's distance
		double usual = unit * distanceTo(film.openX(), film.openY(), film.openZ()) / Math.max(1.0, distanceTo(film.lyricX(), film.lyricY(), film.lyricZ()));
		double open = Math.min(fits, usual * FILM_OPEN_MOST);
		return open >= usual * FILM_OPEN_LEAST ? (float) open : 0f;
	}

	private double distanceTo(double x, double y, double z) {
		return Math.sqrt((x - eye.x) * (x - eye.x) + (y - eye.y) * (y - eye.y) + (z - eye.z) * (z - eye.z));
	}

	/**
	 * Tries the spots by one place of the shot: the place itself when the line before leaves it free, else below that line, else above it, and a
	 * little above the place, clear of the ground or a block right below it.
	 *
	 * @return whether the line was placed
	 */
	private boolean placeNear(Minecraft client, CinematicCameraFeature film, Shown fresh, Shown older, boolean olderVisible, double anchorX, double anchorY,
							  double anchorZ, float unit, float total, float shift, double halfWidthUnits, float seconds, String where) {
		double halfWidth = halfWidthUnits * unit;
		double halfHeight = total * ROW / 2.0 * unit;
		double dx = anchorX - eye.x;
		double dz = anchorZ - eye.z;
		double flat = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
		double rightX = -dz / flat;
		double rightZ = dx / flat;
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
		for (int k = 0; k < count; k++) {
			double centerY = anchorY - (rises[k] + shift) * unit;
			float margin = film.lyricMargin(anchorX, centerY, anchorZ, rightX, rightZ, halfWidth, halfHeight, seconds);
			if (margin >= 0f && onVisibleAdlib(film, anchorX, centerY, anchorZ, rightX, rightZ, halfWidth, halfHeight)) {
				if (DebugHooks.ACTIVE) {
					shotMisses += (shotMisses.isEmpty() ? "" : "; ") + "on an adlib";
				}
				continue;
			}
			if (margin >= 0f && olderVisible && !clearOf(film, older, anchorX, centerY, anchorZ, rightX, rightZ, halfWidth, halfHeight, total)) {
				if (DebugHooks.ACTIVE) {
					shotMisses += (shotMisses.isEmpty() ? "" : "; ") + "on the line before";
				}
				continue;
			}
			if (margin >= 0f) {
				standAt(fresh, anchorX, anchorY, anchorZ, unit, shift, rightX, rightZ, halfWidth, halfHeight);
				fresh.rise = rises[k];
				fresh.boxY = centerY;
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(DebugCategory.MUSIC, "Cinematic line {}: {} shot, in the world {} rows {} {}, least margin to the frame edge {}", fresh.index,
						film.shotName(), Math.round(Math.abs(rises[k]) / ROW * 10f) / 10f, rises[k] >= 0f ? "below" : "above", where,
						Math.round(margin * 100f) / 100f);
				}
				return true;
			}
			if (DebugHooks.ACTIVE) {
				shotMisses += (shotMisses.isEmpty() ? "" : "; ") + where + ": " + film.lyricMiss();
			}
		}
		return false;
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
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "Cinematic line {}: {} shot, in the world beside the player with the most room ({}, {}), no spot stays fully inside: {}",
				fresh.index, film.shotName(), Math.round(best * 100f) / 100f, bestMiss, misses);
		}
		if (fresh.boxText != null && !placingAfterCut) {
			// a Cut-out column the shot has no room for: no lyrics in the world in this shot, rather than one cut off or moved again and again; right after a
			// cut it stands at the spot with the most room instead, as the new shot must show the line being sung
			hideForShot(film, fresh.index, "no spot stays fully inside");
		}
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
					if (DebugHooks.ACTIVE) {
						DebugHooks.log(DebugCategory.MUSIC, "Cinematic line {}: {} shot, in front of the camera at size {}, the only way to keep it whole", fresh.index, film.shotName(), size);
					}
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
								if (DebugHooks.ACTIVE) {
									DebugHooks.log(DebugCategory.MUSIC, "Cinematic line {}: {} shot, in the world beside the player at {} blocks, size {}, least margin to the frame edge "
										+ "{} (the shot's spot did not stay inside: {})", fresh.index, film.shotName(), Math.round(side * 10) / 10.0, size,
										Math.round(best * 100f) / 100f, misses);
								}
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
						if (DebugHooks.ACTIVE) {
							DebugHooks.log(DebugCategory.MUSIC, "Cinematic line {}: {} shot, in the world in front of the camera at {} {} of the picture, size {}, least margin to the "
								+ "frame edge {} (the shot's spot did not stay inside: {})", fresh.index, film.shotName(), PICTURE_SPOTS[i], PICTURE_SPOTS[i + 1],
								size, Math.round(best * 100f) / 100f, misses);
						}
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
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "Cinematic line {}: push-in, in the world by the player, size {}, push-in kept {} (the shot's spot did not stay inside: {})",
				fresh.index, Math.round(pushSpot[5] * 100.0) / 100.0, Math.round(pushSpot[6] * 100.0) / 100.0, misses);
		}
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
		older.easeOut = true;
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
		upright(shown);
		shown.sky = false;
	}

	/** @return whether two boxes in picture units (left, bottom, right, top) come closer than a gap */
	private static boolean closer(float[] a, float[] b, float gap) {
		return a[0] < b[2] + gap && a[2] > b[0] - gap && a[1] < b[3] + gap && a[3] > b[1] - gap;
	}

	/** @return the height of a line with the room for its adlibs, in rows */
	private float boxRows(Minecraft client, Shown shown) {
		int rows = rowsOf(client, boxText(shown), shown.wrap);
		return (LyricSprites.boundsBottom(rows) - LyricSprites.boundsTop(rows)) / ROW + adlibRoom(shown.index);
	}

	/**
	 * Stands a line upright once it is placed ({@link LyricUpright}): no more than 45 degrees above or below the horizon (moved there at the same
	 * distance if it was placed higher or lower), facing the eye across the ground, leaning back toward it by up to 20 degrees, never rolled.
	 * Turned once; afterwards it stays as it is, whatever the view does.
	 */
	private void upright(Shown shown) {
		double[] direction = {shown.x - eye.x, shown.y - eye.y, shown.z - eye.z};
		double elevation = LyricUpright.keepNearHorizon(direction, Minecraft.getInstance().gameRenderer.mainCamera().yRot());
		// never right in front of the face: closer than the least distance (the ground or a wall below or ahead), it goes to eye level or a little
		// above in the same heading, wherever there is room that far
		double distance = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
		double least = leastDistance();
		if (distance < least && distance > 1e-6) {
			double across = Math.hypot(direction[0], direction[2]);
			double headX = across > 1e-6 ? direction[0] / across : 0.0;
			double headZ = across > 1e-6 ? direction[2] / across : 1.0;
			for (double up : HIGHER_DEGREES) {
				double rad = Math.toRadians(up);
				double dx = headX * Math.cos(rad);
				double dy = Math.sin(rad);
				double dz = headZ * Math.cos(rad);
				if (reach(dx, dy, dz, least) >= least) {
					direction[0] = dx * least;
					direction[1] = dy * least;
					direction[2] = dz * least;
					elevation = rad;
					// as large on the screen as it was meant to be
					shown.unit *= (float) (least / distance);
					break;
				}
			}
		}
		shown.x = eye.x + direction[0];
		shown.y = eye.y + direction[1];
		shown.z = eye.z + direction[2];
		shown.facing = LyricUpright.facing(direction[0], direction[2]);
		shown.tilt = LyricUpright.lean(elevation);
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
			for (int i = 0; i < lines.length; i++) {
				Shown shown = lines[i];
				evictStaying[i] = shown.on() && shown.older && !shown.pushed && shown.column == null;
				evictStarts[i] = shown.startMs;
				evictAges[i] = shown.age;
			}
			int oldest = WorldLyricPace.toEvict(evictStarts, evictAges, evictStaying, most);
			if (oldest < 0) {
				return;
			}
			lines[oldest].pushed = true;
			lines[oldest].leaving = true;
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: gives up its place after {} s, more lines than may stand at once", lines[oldest].index,
					Math.round(lines[oldest].age * 10f) / 10f);
			}
		}
	}

	private final long[] evictStarts = new long[SLOTS];
	private final float[] evictAges = new float[SLOTS];
	private final boolean[] evictStaying = new boolean[SLOTS];

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

	/** @return the letters of each row, for the debug log, which never holds the lyrics themselves */
	private static String rowLengths(List<String> rows) {
		StringBuilder out = new StringBuilder("(");
		for (String row : rows) {
			out.append(out.length() > 1 ? ", " : "").append(row.length());
		}
		return out.append(')').toString();
	}

	/** @return the text a line's room in the world is worked out from: its own, or for a Cut-out line the one standing for its column */
	private static String boxText(Shown shown) {
		return shown.boxText != null ? shown.boxText : shown.text;
	}

	/** @return how many rows a line is broken into at a width */
	private static int rowsOf(Minecraft client, String text, int wrapWidth) {
		return Math.max(1, Math.min(LyricSprites.MAX_ROWS, rowsFor(client, text, wrapWidth).size()));
	}

	/**
	 * Finds the spot of a line (or of an ad-lib sung alone) from its size: its rows and the angles it covers follow from its width at the size
	 * set, and the view gives it its spot ({@link #chooseSpot}), clear of the lines still standing; only without such a spot in the place of one of
	 * them, which then goes (see {@link #makeWay}). Without any spot it is not shown in the world.
	 */
	private void place(Minecraft client, Shown shown, String text, float scale, float viewYaw, float viewPitch) {
		float perBlock = fittedSize(client, shown, text, LyricReach.placed(placedEm(shown), halfFovY) * scale, shown == alone ? 0f : adlibRoom(shown.index));
		double tan = Math.tan(Math.toRadians(halfFovY));
		double across = Math.tan(Math.toRadians(halfAngles[0]));
		double up = Math.tan(Math.toRadians(halfAngles[1]));
		if (!chooseSpot(client, shown, across / (tan * aspect), 2.0 * up / tan, 1, 1, true, viewYaw, viewPitch)
			&& !chooseSpot(client, shown, across / (tan * aspect), 2.0 * up / tan, 1, 1, false, viewYaw, viewPitch)) {
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: no spot in the view, not shown in the world", shown.index);
			}
			shown.text = null;
			return;
		}
		standAt(shown, perBlock, (float) (2.0 * across / perBlock), (float) (2.0 * up / perBlock));
	}

	/**
	 * @return the size of a line in blocks per text unit; its rows and text size are chosen so it is no wider than a line may be (see
	 *         {@link #fitRows}), and go to the line, its angles into {@link #halfAngles}
	 * @param shown     the line, which keeps the width its rows break at, or null when only measured
	 * @param adlibRows the room kept below the line for its adlibs, in rows
	 */
	private float fittedSize(Minecraft client, Shown shown, String text, float perBlock, float adlibRows) {
		float most = LyricReach.mostLineHalfWidth(halfFovY, aspect);
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
	 * at full size; else two rows a little smaller, down to {@link #LEAST_SHRINK}; else three rows the same way; else all the sprite's rows,
	 * smaller down to {@link #LEAST_TEXT_SCALE}. A line is never cut and never left out: what still does not fit (a word wider than a row, which
	 * cannot be broken) gets exactly as small as it must. The choice goes into {@link #fitWrap} and {@link #fitScale}.
	 */
	private void fitRows(Minecraft client, String text, float allowed) {
		float limit = Math.min(WRAP_WIDTH, allowed);
		// a block of fast lines has a row for each of its lines: fewer rows would run its lines together
		int blockLines = 1;
		for (int i = 0; i < text.length(); i++) {
			blockLines += text.charAt(i) == LyricGroups.BREAK ? 1 : 0;
		}
		for (int most = Math.clamp(blockLines, PREFERRED_ROWS, LyricSprites.MAX_ROWS); most <= LyricSprites.MAX_ROWS; most++) {
			float least = most == LyricSprites.MAX_ROWS ? LEAST_TEXT_SCALE : LEAST_SHRINK;
			for (float scale = 1f; scale >= least - 1e-4f; scale -= SHRINK_STEP) {
				int wrapWidth = (int) Math.floor(limit / scale);
				List<String> rows = rowsFor(client, text, wrapWidth);
				// a block fits when each of its lines has its own row; rows that run its lines together are made smaller first
				boolean ownRows = blockLines == 1 || rows.size() == blockLines && String.join(String.valueOf(LyricGroups.BREAK), rows).equals(text);
				if (rows.size() <= most && ownRows && widest(client, rows) <= wrapWidth) {
					fitWrap = wrapWidth;
					fitScale = scale;
					return;
				}
			}
		}
		// nothing fits at a size that reads well: all the rows, as small as the widest of them needs
		for (float scale = LEAST_TEXT_SCALE - SHRINK_STEP; scale >= LAST_TEXT_SCALE - 1e-4f; scale -= SHRINK_STEP) {
			int wrapWidth = (int) Math.floor(limit / scale);
			List<String> rows = rowsFor(client, text, wrapWidth);
			if (rows.size() <= LyricSprites.MAX_ROWS && widest(client, rows) <= wrapWidth) {
				fitWrap = wrapWidth;
				fitScale = scale;
				noteTight(text, "smaller than the smallest size, it cannot be broken or split further");
				return;
			}
		}
		fitWrap = (int) Math.floor(limit / LAST_TEXT_SCALE);
		fitScale = LAST_TEXT_SCALE;
		noteTight(text, "at the very smallest size; the sprite fits what is wider into its rows");
	}

	/** @return the width of the widest row, measured with the game's font as the sprites are */
	private static float widest(Minecraft client, List<String> rows) {
		float width = 0f;
		for (String row : rows) {
			width = Math.max(width, LyricSprites.rowWidth(row, cleanStyle));
		}
		return width;
	}

	/**
	 * @return whether a text is shown whole in the world: in the sprite's rows at the smallest size a long line gets, at the width a line may
	 *         really take (see {@link #wholeWidth}). What does not is split in time, so it is read instead of set very small.
	 */
	private boolean fitsWhole(String text) {
		Minecraft client = Minecraft.getInstance();
		if (cutoutChosen()) {
			// the words come one by one: however long the line is, it is shown whole
			return true;
		}
		int wrapWidth = (int) Math.floor(wholeWidth(client) / LEAST_TEXT_SCALE);
		List<String> rows = rowsFor(client, text, wrapWidth);
		return rows.size() <= LyricSprites.MAX_ROWS && widest(client, rows) <= wrapWidth;
	}

	/**
	 * @return the width a line may take, in text units at full size, in steps of {@link #WHOLE_WIDTH_STEP}: what fits on one side of the view
	 *         in the sky, where a text unit is largest (around the player only where the player chose that place), at the size set. From the
	 *         field of view set in the options, not the one of this frame, so sprinting or a bow never splits the lines anew.
	 */
	private int wholeWidth(Minecraft client) {
		if (cutoutChosen()) {
			// a width no other preset has, so the lines are put together anew when the preset changes
			return CUTOUT_WIDTH;
		}
		float perBlock = LyricReach.placed(LyricSprites.cleanSize(), client.options.fov().get() / 2.0);
		float aspect = client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getHeight());
		float most = LyricReach.mostLineHalfWidth(client.options.fov().get() / 2.0, aspect);
		float allowed = (float) (2.0 * Math.tan(Math.toRadians(most)) / perBlock);
		int width = (int) Math.clamp(allowed, LEAST_WRAP, WRAP_WIDTH);
		return Math.max(LEAST_WRAP, width - width % WHOLE_WIDTH_STEP);
	}

	private static final int CUTOUT_WIDTH = Integer.MAX_VALUE;

	/** @return whether lines are shown word by word in the Cut-out preset, also in the cinematic camera */
	private boolean cutoutChosen() {
		return music.worldPreset.get() == WorldLyricPreset.CUTOUT;
	}

	private void noteTight(String text, String why) {
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "World lyric of {} characters: {} (size {}, rows of {})", text.length(), why, fitScale, fitWrap);
		}
	}

	/** Writes half the width and height a text covers, in degrees, at a size in blocks per text unit, into {@link #halfAngles}. */
	private void angularSize(Minecraft client, String text, float perBlock, int wrapWidth, float textScale, float adlibRows) {
		// the rows the line will have, and the widest of them, measured with the game's font, as the sprite places its glyphs
		List<String> wrapped = rowsFor(client, text, wrapWidth);
		float rows = Math.max(1, Math.min(LyricSprites.MAX_ROWS, wrapped.size())) + adlibRows;
		float width = widest(client, wrapped);
		halfAngles[0] = (float) Math.toDegrees(Math.atan(width * textScale / 2f * perBlock));
		halfAngles[1] = (float) Math.toDegrees(Math.atan(rows * ROW / 2f * perBlock));
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
		int rows = rowsOf(client, boxText(shown), shown.wrap);
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

	/** @return whether a line stands in the Cut-out column the new one joins: its rows take their own places there, it stays */
	/** @return the row that takes a row's place (its line sung again right after), or -1 */
	private int takenBy(int row) {
		for (int q = row + 1; q < rows && q <= row + TAKEN_REACH; q++) {
			if (rowRepeat[q] == row) {
				return q;
			}
		}
		return -1;
	}

	/** @return whether a line of the column is sung again right after it, in its place: it then goes, rather than standing above as the line before */
	private boolean takenOver(Shown shown) {
		int first = firstRow(shown.index);
		return first >= 0 && takenBy(first) >= 0;
	}

	private final float[] backdrop = new float[4];

	/**
	 * Takes what lies behind a line as it starts, for its tones: how light the darkest and the brightest of the open-sky grid's cells its block
	 * covers are (world and sky), and how busy the world's surfaces among them are. Where the grid has no recent look of that place, the sky's
	 * brightness in its direction. The other lines of its column stand before the same and take it too.
	 */
	private void measureBackdrop(Shown shown) {
		double distance = Math.max(1e-3, distance(shown));
		float darkest = WorldLyricOpenSky.skyLight(dev.tim1337.lumio.render.FogState.skyColor(), dev.tim1337.lumio.render.FogState.color(),
			(shown.y - eye.y) / distance);
		float brightest = darkest;
		float busy = 0f;
		if (lookBehind(shown)) {
			busy = backdrop[1];
			darkest = backdrop[2];
			brightest = backdrop[3];
		}
		float em = emPixels(shown);
		shown.backdrop.take(darkest, brightest, busy, wordContrast(shown, em), smallContrast(shown, em), animated);
		shareBackdrop(shown);
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: behind it a luminance from {} to {}, busy {}, its big words {} px", shown.index, Math.round(darkest * 1000f) / 1000f,
				Math.round(brightest * 1000f) / 1000f, Math.round(busy * 1000f) / 1000f, Math.round(em));
		}
	}

	/** @return whether the grid's last look shows what lies behind the line; {@link #backdrop} then holds it */
	private boolean lookBehind(Shown shown) {
		double fov = openSky.halfFovY();
		double shape = openSky.aspect();
		if (!openSky.fresh() || !WorldLyricView.project(shown.x - eye.x, shown.y - eye.y, shown.z - eye.z, openSky.yaw(), openSky.pitch(), fov, shape, screenPlace)) {
			return false;
		}
		double halfX = WorldLyricView.share(shown.halfWidth, fov) / shape;
		double halfY = WorldLyricView.share(shown.halfHeight, fov);
		return openSky.backdrop(screenPlace[0] - halfX, screenPlace[0] + halfX, screenPlace[1] + halfY, screenPlace[1] - halfY, backdrop);
	}

	private void shareBackdrop(Shown shown) {
		for (Shown other : lines) {
			if (other != shown && other.on() && sameColumn(other, shown)) {
				other.backdrop.copy(shown.backdrop);
			}
		}
	}

	/** The look of the grid the standing lines last followed. */
	private int followedLook = -1;

	/**
	 * Keeps the tones of the standing lines right as the view and the world behind them change: once per look of the grid (about four a second), never
	 * in the frames between. A line out of the view keeps what it has; the lines of a column go by its first.
	 */
	private void followBackdrops() {
		int look = openSky.looks();
		if (look == followedLook) {
			return;
		}
		followedLook = look;
		for (int i = 0; i < lines.length; i++) {
			Shown shown = lines[i];
			if (!shown.on() || shown.leaving || !shown.backdrop.known() || followedInColumn(i) || !lookBehind(shown)) {
				continue;
			}
			float em = emPixels(shown);
			if (shown.backdrop.follow(backdrop[2], backdrop[3], backdrop[1], wordContrast(shown, em), smallContrast(shown, em), animated)) {
				shareBackdrop(shown);
			}
		}
	}

	/** @return whether a line before this one stands in the same column */
	private boolean followedInColumn(int index) {
		for (int i = 0; i < index; i++) {
			if (lines[i].on() && sameColumn(lines[i], lines[index])) {
				return true;
			}
		}
		return false;
	}

	/** @return how tall an em of the line's main words is on the screen, in pixels, as it is drawn */
	private float emPixels(Shown shown) {
		double distance = Math.max(1e-3, distance(shown));
		int height = Minecraft.getInstance().getWindow().getHeight();
		double em = mainEm(shown) * shown.unit;
		return (float) (em * (shown.kind == LyricSpots.Kind.NONE ? LyricReach.grow(em, distance, halfFovY) : 1f) / distance * height / (2.0 * Math.tan(Math.toRadians(halfFovY))));
	}

	/** @return the contrast the line's words need: for Cut-out by its smallest usual word, on a tile or free */
	private static float wordContrast(Shown shown, float em) {
		return LyricTones.contrastFor(em);
	}

	/** @return the contrast the line's small words need */
	private static float smallContrast(Shown shown, float em) {
		return LyricTones.contrastFor(em);
	}

	/** @return how many lines of a Cut-out column began after this one: 0 for the line being sung, 1 for the line before it */
	private int ageInColumn(Shown shown, long clockMs) {
		if (shown.column == null) {
			return 0;
		}
		int age = 0;
		for (Shown other : lines) {
			if (other != shown && other.on() && other.column == shown.column && other.x == shown.x && other.y == shown.y && other.z == shown.z
				&& other.startMs > shown.startMs && other.startMs <= clockMs) {
				age++;
			}
		}
		return age;
	}

	private static boolean sameColumn(Shown shown, Shown fresh) {
		return fresh.column != null && shown.column == fresh.column && !shown.leaving && shown.x == fresh.x && shown.y == fresh.y && shown.z == fresh.z;
	}

	/**
	 * A line the new one would cover, with its glow and shadow, goes at once, and the new one comes in only as that one has gone dim, so two lines
	 * never lie over each other. That is a line already leaving whose place the new one takes (the placer keeps clear only of the lines that
	 * stay, so a place is used again as its line goes), or, when no free spot was found at all (boxed in), an older line still staying.
	 */
	private void makeWay(Shown fresh) {
		directionOf(fresh, direction);
		float freshYaw = direction[0];
		float freshElevation = direction[1];
		for (Shown shown : lines) {
			if (shown == fresh || !shown.on() || shown.alpha() <= VISIBLE_ALPHA || sameColumn(shown, fresh)) {
				continue;
			}
			directionOf(shown, direction);
			if (Math.abs(Mth.wrapDegrees(direction[0] - freshYaw)) < shown.halfWidth + fresh.halfWidth + HANDOVER_ROOM_DEGREES
				&& Math.abs(direction[1] - freshElevation) < shown.halfHeight + fresh.halfHeight + HANDOVER_ROOM_DEGREES) {
				shown.pushed = true;
				shown.leaving = true;
				// the new line comes in as the one in its way has gone dim (a tenth of a second or so): the two never lie over each other
				if (fresh.waitFor == null || shown.alpha() > fresh.waitFor.alpha()) {
					fresh.waitFor = shown;
				}
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
	private void advance(Shown shown, boolean sung, long clockMs, float linger, float lingerFade, boolean filmed) {
		if (!shown.on()) {
			return;
		}
		shown.age += dt;
		// a leaving line only fades: it never grows brighter again, even when it had not fully come in yet
		if (!shown.leaving && !(shown == current && shown.appear <= 0f && shown.age < MOST_WAIT_SECONDS && adlibMakingWay())) {
			if (shown.waitFor != null && (!shown.waitFor.on() || shown.waitFor.alpha() < MADE_WAY)) {
				shown.waitFor = null;
			}
			if (shown.waitFor == null) {
				if (shown.delay > 0f && animated) {
					shown.delay -= dt;
				} else {
					shown.appear = animated ? Math.min(1f, shown.appear + dt / (filmedNow ? FILM_IN_SECONDS : shown.fadeIn)) : 1f;
				}
			}
		}
		if (shown.appear > 0f) {
			shown.shownFor += dt;
		}
		shown.repeatPulse = Math.max(0f, shown.repeatPulse - dt / REPEAT_PULSE_SECONDS);
		if (sung) {
			shown.sung = motion.sung;
			shown.lit = motion.lit;
		}
		if (shown.older && shown.column != null) {
			// in a column an older line stays until its page or the stack takes it away, a little dimmer
			shown.olderFor += dt;
			float dimming = animated ? Math.min(1f, shown.olderFor / COLUMN_DIM_SECONDS) : 1f;
			shown.dim = Mth.lerp(dimming * dimming * (3f - 2f * dimming), 1f, COLUMN_OLDER);
		} else if (shown.older && !filmed) {
			shown.olderFor += dt;
			float dimming = Math.min(1f, shown.olderFor / linger);
			float eased = animated ? dimming * dimming * (3f - 2f * dimming) : 1f;
			shown.dim = Mth.lerp(eased, 1f, OLDER_DIM);
		}
		if (!shown.leaving) {
			if (shown.column != null) {
				// a column goes as a whole before a long pause (see keepColumn)
				shown.leaving = !shown.older && shown.beforeGap && clockMs >= shown.endMs + HOLD_BEFORE_GAP_MS;
			} else if (filmed) {
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
			shown.out = animated ? Math.max(0f, shown.out - dt / (shown.easeOut ? FILM_OUT_SECONDS : shown.replaced ? LyricReadable.REPLACED_SECONDS : shown.pushed ? WorldLyricPace.EVICTED_SECONDS : shown.older ? WorldLyricPace.fadeSeconds(music.worldFade.get(), shown.lengthMs, lingerFade) : OUT_SECONDS)) : 0f;
		}
		if (shown.out <= 0f) {
			shown.clear();
		}
	}

	/** Draws a line of the Cut-out preset: its words, cut from its sprite. @return whether it was drawn this frame */
	private boolean drawCutout(SubmitNodeCollector collector, PoseStack pose, Shown shown, float alpha) {
		LyricLine line = shown.cutLine;
		if (line == null) {
			return false;
		}
		float top;
		float fadeTop = 0f;
		float fadeZone = 0f;
		if (shown.part == null) {
			top = -CutoutLyrics.height(BOX_GROUPS) / 2f;
		} else {
			float roomTop = -shown.room / 2f;
			top = roomTop;
			if (shown.stack != null) {
				// the whole stack moves as one (once per line on a page); a word fades as it reaches the top of the room
				top = roomTop - shown.stack.at(scrollAt);
				fadeTop = roomTop;
				fadeZone = SCROLL_FADE;
			}
		}
		// a line already sung stays where it stands and dims, softly as the next one starts (a line sung again in its place goes instead)
		int age = ageInColumn(shown, (long) Math.floor(scrollAt));
		float step = animated ? dt / PREVIOUS_SECONDS : 1f;
		shown.previous = Mth.approach(shown.previous, age >= 1 && !takenOver(shown) ? 1f : 0f, step);
		pose.pushPose();
		near(pose, shown);
		at(pose, shown);
		boolean drawn = cutout.draw(collector, pose, line, shown.endMs, (long) Math.floor(scrollAt), alpha, animated, frame, shown.part, top, fadeTop,
			fadeZone, shown.previous, shown.backdrop);
		pose.popPose();
		if (drawn && alpha > 0.004f) {
			if (shown.index >= 0) {
				this.drawn.set(shown.index);
			}
			shown.wasDrawn = true;
		}
		return drawn;
	}

	/** @return whether the line was drawn this frame */
	private boolean drawShown(SubmitNodeCollector collector, PoseStack pose, Shown shown, boolean hand) {
		if (!shown.on()) {
			return false;
		}
		// an older line dims by its tones, toward what lies behind it, never by being see-through
		float alpha = shown.fade() * (shown.older || shown.leaving ? 1f : motion.visible) * filmFade * songBreak.light();
		shown.backdrop.step(animated ? dt : LyricBackdrop.BLEND_SECONDS);
		if (shown.preset == WorldLyricPreset.CUTOUT) {
			return drawCutout(collector, pose, shown, alpha);
		}
		Entry entry = sprite(shown.text, hand, shown.wrap, shown.textScale);
		if (alpha <= 0.004f || entry.sprite == null || entry.sprite.state() != LyricSprites.State.READY) {
			if (DebugHooks.ACTIVE && shown.wasDrawn && !shown.leaving) {
				shown.wasDrawn = false;
				DebugHooks.log(DebugCategory.MUSIC, "World lyric {}: was drawn and is not now, though it stays (its light {}, the lyrics' {}, appear {}, dim {}, out {}, sprite {})", shown.index,
					shown.alpha(), motion.visible, shown.appear, shown.dim, shown.out, entry.sprite == null ? "none" : entry.sprite.state());
			}
			return false;
		}
		if (shown.index >= 0) {
			drawn.set(shown.index);
		}
		shown.wasDrawn = true;
		Look look = look(shown, hand, olderShare(shown));
		pose.pushPose();
		near(pose, shown);
		at(pose, shown);
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
		float lineAlpha = Math.min(1f, alpha * look.light * (1f + REPEAT_LIGHT * pulse));
		if (LyricBreaks.is(line) && isDotMark(entry.sprite)) {
			// each dot as a piece of its own, hopping in a wave from left to right
			LyricSprites.Sprite sprite = entry.sprite;
			long now = music.lyricClockMs();
			float dot = (sprite.charX(0, 1f) - sprite.charX(0, 0f)) * DOT_OF_ADVANCE;
			float[] cuts = {Float.NEGATIVE_INFINITY, sprite.charX(0, 1.5f), sprite.charX(0, 3.5f), Float.POSITIVE_INFINITY};
			for (int i = 0; i < cuts.length - 1; i++) {
				sliceFrom = cuts[i];
				sliceTo = cuts[i + 1];
				float lift = LyricDots.hop(i, line, now) * LyricDots.HOP_SHARE * dot;
				drawLine(collector, pose, entry, shown.rowsSung, x, -lift, 1f, lineAlpha, shown.sung, shown.lit, glow, look, false);
			}
			sliceFrom = Float.NEGATIVE_INFINITY;
			sliceTo = Float.POSITIVE_INFINITY;
		} else {
			drawLine(collector, pose, entry, shown.rowsSung, x, 0f, 1f, lineAlpha, shown.sung, shown.lit, glow, look, false);
		}
		pose.popPose();
		return true;
	}

	/** @return whether a sprite is the "• • •" of a break, in one row, so its dots can be drawn one by one */
	private static boolean isDotMark(LyricSprites.Sprite sprite) {
		return sprite.rows().size() == 1 && sprite.rows().getFirst().text().equals(LyricBreaks.MARK);
	}

	/**
	 * The brightness, glow and colors of a line, chosen by what lies behind it ({@link LyricTones#classic}): the sung part, the part still
	 * waiting, the classic glow's tinted line, the glow (how much it shows in its alpha: none on a bright background) and the tight shadow
	 * where the line needs one (its strength in its alpha). One, reused for every line.
	 */
	private static final class Look {
		float light;
		float glow;
		int sung;
		int rest;
		int tinted;
		int glowTone;
		int shadow;
		/** How much of the full glow the words not being sung have: none with the Glow "Sung word" (see {@link LyricGlow}). */
		float restGlow;
		/** Whether there is a glow at all (Glow Off: none, the letters crisp). */
		boolean glowOn = true;
	}

	private final Look shared = new Look();
	private final int[] classicTones = new int[LyricTones.Classic.values().length];
	private final int[] classicDimmed = new int[LyricTones.Classic.values().length];

	/** @param older how far the line is dimmed as an older line, from 0 to 1 */
	private Look look(Shown shown, boolean hand, float older) {
		shared.light = 1f;
		shared.glow = 1f;
		shown.backdrop.classic(music.worldTextArgb(), music.worldGlowArgb(), hand ? CLASSIC_HAND_DIM : CLASSIC_DIM, classicTones, classicDimmed);
		shared.sung = classicTone(LyricTones.Classic.SUNG, older);
		shared.rest = classicTone(LyricTones.Classic.REST, older);
		shared.tinted = classicTone(LyricTones.Classic.TINTED, older);
		shared.glowTone = classicTone(LyricTones.Classic.GLOW, older);
		shared.shadow = classicTone(LyricTones.Classic.SHADOW, older);
		shared.glowOn = LyricGlow.drawn(music.worldGlow.get());
		shared.restGlow = LyricGlow.of(music.worldGlow.get(), 0f, older);
		return shared;
	}

	private int classicTone(LyricTones.Classic tone, float older) {
		int full = classicTones[tone.ordinal()];
		return older <= 0f ? full : Ui.mix(full, classicDimmed[tone.ordinal()], older);
	}

	/** @return how far an older line is dimmed, from 0 to 1: its tones move that far toward their dimmed ones */
	private static float olderShare(Shown shown) {
		return Math.clamp((1f - shown.dim) / (1f - OLDER_DIM), 0f, 1f);
	}

	/** @return a tone made whiter by this much, its alpha kept: a light glow, never more of it than the tone has */
	private static int whiter(int tone, float amount) {
		return tone & 0xFF000000 | Ui.mix(tone | 0xFF000000, 0xFFFFFFFF, amount) & 0xFFFFFF;
	}

	/**
	 * Moves what is drawn of a line toward the camera, to where only what stands right before the camera can cover it ({@link WorldLyricDepth}): the same
	 * place and size on the screen, followed as the camera moves.
	 */
	private void near(PoseStack pose, Shown shown) {
		shown.nearer = WorldLyricDepth.share(distance(shown), shown.cover);
		if (shown.nearer < 1f) {
			pose.last().pose().scaleLocal(shown.nearer);
		}
	}

	/**
	 * Moves the pose to a line's spot, turned sideways the way it faces, in text units: upright, leaned back toward the eye by its lean
	 * ({@link LyricUpright}), never rolled.
	 */
	private void at(PoseStack pose, Shown shown) {
		pose.translate(shown.x - eye.x, shown.y - eye.y, shown.z - eye.z);
		// upright: turned about the up axis to face where the eye was, then leaned back about its own level axis; never rolled
		pose.mulPose(Axis.YP.rotation(shown.facing));
		if (shown.tilt != 0f) {
			pose.mulPose(Axis.XP.rotation(shown.tilt));
		}
		// in a clean break everything shrinks a little about its own spot as it goes
		float breakSize = songBreak.size();
		pose.scale(shown.unit * breakSize, -shown.unit * breakSize, shown.unit);
		pose.translate(0f, shown.rise, 0f);
		// a line of the cinematic camera, placed for a shot, is kept from becoming too small to read as the shot moves: it grows around its middle. A block
		// placed by the view is never rescaled.
		float grow = shown.kind == LyricSpots.Kind.NONE ? LyricReach.grow(mainEm(shown) * shown.unit, distance(shown), halfFovY) : 1f;
		if (!Float.isNaN(shown.placedGrow)) {
			// a column's room was planned for its words grown so far: they grow no further, so it stays inside its box when the camera pulls back
			grow = Math.min(grow, shown.placedGrow);
		}
		if (grow != 1f) {
			pose.scale(grow, grow, 1f);
		}
	}

	/** @return how large an em of a line's words is, in text units, for its size on the screen: a Cut-out column's smallest words, or a Classic line's letters */
	private static float mainEm(Shown shown) {
		return shown.boxText != null || shown.preset == WorldLyricPreset.CUTOUT ? cutoutEm() : LyricSprites.cleanSize() * shown.textScale;
	}

	private double distance(Shown shown) {
		double dx = shown.x - eye.x;
		double dy = shown.y - eye.y;
		double dz = shown.z - eye.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** @return how far a line of the film is moved off its spot as it comes in or leaves, in text units (down is positive) */
	/** @return whether a line other than this one can still be seen */
	private boolean anyVisible(Shown except) {
		for (Shown shown : lines) {
			if (shown.on() && shown != except && shown.alpha() > VISIBLE_ALPHA) {
				return true;
			}
		}
		return false;
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
		if (cutoutChosen()) {
			// the column of words has no lines to stand ad-libs beside: those in brackets are small words of the column
			return;
		}
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
			if (adlibPlaceFor[i] != adlib.part && (songBreak.running() || !placeAdlib(client, i, adlib, hand, size, viewYaw, viewPitch))) {
				// it comes in when it has its place
				adlib.alpha = 0f;
				continue;
			}
			Entry entry = sprite(adlib.part.text(), hand, adlibPlaceWrap[i], 1f);
			if (entry.sprite == null || entry.sprite.state() != LyricSprites.State.READY) {
				continue;
			}
			Shown place = adlibPlace[i];
			Look look = look(place, hand, 0f);
			pose.pushPose();
			near(pose, place);
			at(pose, place);
			drawLine(collector, pose, entry, adlibSung[i], adlibPlaceX[i], adlibPlaceY[i], adlibPlaceScale[i], adlib.alpha * AdlibLayout.ALPHA * filmFade * songBreak.light()
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
			if (!owner.backdrop.known()) {
				// a spot of its own (an ad-lib sung in a gap): its tones by what lies behind it, as a line's
				measureBackdrop(owner);
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
		// as in Cut-out: a long adlib never stands beside, and none reaches out of its line's block
		boolean longOne = CutoutAdlibs.words(adlib.part.text()) > CutoutAdlibs.MOST_WORDS_BESIDE;
		int spots = 0;
		for (int spot = 0; spot < ADLIB_SPOTS; spot++) {
			float spotX = spotX(spot, x, stepAside);
			if (spot >= SPOT_RIGHT && (longOne || Math.abs(spotX) + width / 2f > owner.adlibReach)) {
				continue;
			}
			spotOrder[spots] = spot;
			textBox(owner, spotX, spotY(spot, y, stepDown), width, height, spotBoxes, spots++ * AdlibBoxes.VALUES);
		}
		int standingCount = 0;
		for (int other = 0; other < adlibPlace.length; other++) {
			if (other != slot && placedAdlibVisible(other)) {
				adlibBox(other, standingBoxes, standingCount++);
			}
		}
		// its own line, and every other line that shows or is placed and still to come in: an adlib never lies over a word, also not one of a
		// line that only appears after it
		for (Shown shown : lines) {
			if (shown != alone && shown.on() && (shown.alpha() > VISIBLE_ALPHA || !shown.leaving)) {
				lineBox(shown, standingBoxes, standingCount++ * AdlibBoxes.VALUES);
			}
		}
		int found = AdlibBoxes.firstFree(spotBoxes, spots, standingBoxes, standingCount, 0f);
		int free = found < 0 ? -1 : spotOrder[found];
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
		place.tilt = owner.tilt;
		place.unit = owner.unit;
		place.rise = owner.rise;
		place.sky = owner.sky;
		place.nearer = owner.nearer;
		place.backdrop.copy(owner.backdrop);
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
			planLong[k] = CutoutAdlibs.words(text) > CutoutAdlibs.MOST_WORDS_BESIDE;
		}
		owner.adlibReach = own ? room / 2f : Float.MAX_VALUE;
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
		// as in Cut-out: a long adlib gets a row of its own below the line, and none reaches out of the line's block
		AdlibLayout.place(-halfWidth, top, halfWidth, bottom, rowHeight, AdlibLayout.WORLD_GAP_SHARE, planWidths, planHeights, planLong, count, lowest, planSpots);
		for (int k = 0; k < count; k++) {
			owner.adlibX[k] = planSpots[2 * k] + planWidths[k] / 2f;
			owner.adlibY[k] = planSpots[2 * k + 1] + planHeights[k] / 2f;
			if (owner.adlibY[k] > bottom) {
				owner.adlibX[k] = AdlibLayout.inside(owner.adlibX[k], planWidths[k] / 2f, owner.adlibReach);
			}
		}
	}

	/** @return a line's widest row in text units, measured before its sprite is ready */
	private static float lineWidth(Minecraft client, Shown shown) {
		return widest(client, rowsFor(client, boxText(shown), shown.wrap)) * shown.textScale;
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
				place(client, alone, adlib.part.text(), AdlibLayout.SCALE, viewYaw, viewPitch);
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
		// the sung glyphs of each row, from the line's: rows are separated by a space in the line (or the mark between a block's lines), and
		// by nothing where a long word was broken inside
		float left = sungChars < 0f ? Float.MAX_VALUE : sungChars;
		var rows = sprite.rows();
		String whole = entry.text;
		int at = 0;
		for (int k = 0; k < rows.size(); k++) {
			int length = rows.get(k).text().length();
			rowsSung[k] = Math.max(0f, Math.min(length, left));
			at += length;
			boolean between = whole != null && at < whole.length() && (whole.charAt(at) == ' ' || whole.charAt(at) == LyricGroups.BREAK);
			at += between ? 1 : 0;
			left -= length + (between ? 1 : 0);
		}
		// a soft dark shadow slightly below handwriting, whose thin strokes need it on any background
		if (entry.hand) {
			pose.pushPose();
			pose.translate(1.4f, 2.2f, -0.01f);
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.GLOW, Ui.withOpacity(SHADOW, HAND_SHADOW * alpha));
			pose.popPose();
		}
		// a tight shadow, the letters' own shape, only where the line needs it to read against what lies behind it
		if ((look.shadow >>> 24) != 0) {
			pose.pushPose();
			pose.translate(SHADOW_X, SHADOW_Y, 0f);
			pose.last().pose().scaleLocal(1f - SHADOW_DEPTH * LAYER_NEARER);
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.TEXT, Ui.withOpacity(look.shadow, alpha));
			pose.popPose();
		}
		drawSoft(collector, pose, entry, rowsSung, alpha, sungChars, lit, glow, look);
		pose.popPose();
	}

	/** The soft glow: word by word, the sung part lights up with the glow while the rest waits dimmer; line by line the whole line lights at once. */
	private static void drawSoft(SubmitNodeCollector collector, PoseStack pose, Entry entry, float[] rowsSung, float alpha, float sungChars, float lit, float glow, Look look) {
		LyricSprites.Sprite sprite = entry.sprite;
		if (sungChars < 0f) {
			if (look.glowOn) {
				submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.GLOW, Ui.withOpacity(look.glowTone, GLOW_ALPHA * alpha * glow * Math.max(lit, look.restGlow)));
			}
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.TEXT, Ui.withOpacity(Ui.mix(look.rest, look.sung, lit), alpha));
			return;
		}
		// with the Glow "Whole text" the whole line glows softly under the sung part's own glow
		if (look.restGlow > 0f) {
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.GLOW, Ui.withOpacity(look.glowTone, GLOW_ALPHA * alpha * glow * look.restGlow));
		}
		if (look.glowOn) {
			submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.SUNG_GLOW, Ui.withOpacity(look.glowTone, GLOW_ALPHA * alpha * glow));
		}
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.TEXT, Ui.withOpacity(look.rest, alpha));
		submit(collector, pose, sprite, rowsSung, LyricSprites.Sprite.Layer.SUNG, Ui.withOpacity(look.sung, alpha));
	}

	/** The part across the line that is drawn now (see {@link LyricSprites.Sprite#emitSlice}), for the dots of a break that hop one by one. */
	private static float sliceFrom = Float.NEGATIVE_INFINITY;
	private static float sliceTo = Float.POSITIVE_INFINITY;

	/**
	 * How much nearer to the camera a layer of a line is drawn than the line's own place, as a share of its distance for each unit of the layer's
	 * depth: the fill 0.3 % nearer, the glow 0.6 % farther. A share of the distance, as the depth buffer tells places apart by about that much at
	 * any distance; a fixed gap (a centimeter, as before) was too small for it far away.
	 */
	private static final float LAYER_NEARER = 0.15f;

	private static void submit(SubmitNodeCollector collector, PoseStack pose, LyricSprites.Sprite sprite, float[] sung, LyricSprites.Sprite.Layer layer,
							   int color) {
		if ((color >>> 24) == 0 || !OutlineRenderTypes.available()) {
			return;
		}
		pose.pushPose();
		// The layers of a line are told apart by depth alone: the glow behind, the line, the sung fill in front. Whatever order they are drawn in
		// (the glow is drawn late, after the see-through blocks; the game sorts see-through things itself with improved transparency), the fill
		// must win against its own glow and line. They stood a fixed centimeter apart along the line's facing: too little for the depth buffer
		// once the line was far away or seen from the side, so the glow or the dim line came out over the fill now and then while the camera
		// moved. Now each layer is moved along the line of sight, toward the camera (the origin here) by a share of its distance: the same place
		// on the screen, and apart by more than depth can confuse at any distance and angle.
		pose.last().pose().scaleLocal(1f - layer.depth * LAYER_NEARER);
		// the rows sung are read when the geometry is made, later in this frame; the line keeps its array until the next frame
		float[] rows = sung;
		float from = sliceFrom;
		float to = sliceTo;
		SubmitNodeCollector.CustomGeometryRenderer renderer = from == Float.NEGATIVE_INFINITY && to == Float.POSITIVE_INFINITY ? (p, consumer) -> sprite.emit(p, consumer, rows, layer, color)
			: (p, consumer) -> sprite.emitSlice(p, consumer, rows, layer, color, from, to);
		add(collector, pose, sprite, layer, renderer);
		pose.popPose();
	}

	private static void add(SubmitNodeCollector collector, PoseStack pose, LyricSprites.Sprite sprite, LyricSprites.Sprite.Layer layer,
							SubmitNodeCollector.CustomGeometryRenderer renderer) {
		if (DebugHooks.ACTIVE) {
			SubmitNodeCollector.CustomGeometryRenderer timed = renderer;
			renderer = (p, consumer) -> {
				long start = System.nanoTime();
				timed.render(p, consumer);
				LyricSprites.emitNanos += System.nanoTime() - start;
			};
		}
		if (LyricOverlay.add(pose, sprite.renderType(layer), renderer)) {
			return;
		}
		if (layer.isGlow() && LateSubmits.wanted()) {
			// a glow writes no depth: drawn after the see-through blocks, so glass, slime or water behind it does not cover it
			LateSubmits.add(pose, sprite.renderType(layer), renderer);
		} else {
			collector.submitCustomGeometry(pose, sprite.renderType(layer), renderer);
		}
	}

	// ------------------------------------------------------------------------------------------------------- sprites

	private Entry find(String text, boolean hand, int wrapWidth, float textScale) {
		for (Entry entry : entries) {
			if (entry.text != null && entry.hand == hand && entry.wrap == wrapWidth && entry.textScale == textScale
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
			found.sprite = null;
			found.triedAt = Long.MIN_VALUE / 2;
		}
		found.frame = frame;
		if (found.sprite == null && frame - found.triedAt >= RETRY_FRAMES) {
			found.triedAt = frame;
			List<String> rows = rowsFor(Minecraft.getInstance(), text, wrapWidth);
			found.sprite = LyricSprites.begin(rows.subList(0, Math.min(rows.size(), LyricSprites.MAX_ROWS)), hand, false, textScale);
		}
		if (found.sprite != null && found.sprite.state() == LyricSprites.State.FAILED) {
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.MUSIC, "World lyric of {} characters at size {}: its sprite could not be made ({}), tried again", text.length(), textScale,
					found.sprite.failure());
			}
			found.sprite.release();
			found.sprite = null;
		}
		if (found.sprite == null) {
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
		cutout.endFrame(frame);
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

	/** @return the text broken into balanced rows of at most {@code maxWidth} text units, measured as the sprites will be */
	static List<String> rowsFor(Minecraft client, String text, int maxWidth) {
		return LyricWrap.balanced(text, maxWidth, LyricSprites.MAX_ROWS, row -> LyricSprites.rowWidth(row, cleanStyle));
	}
}
