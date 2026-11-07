package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.features.music.MusicService;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * What the film draws over the picture: the letterbox bars that slide in and out with it, the fade in from black at its start, a light vignette,
 * the song card with the artwork when a song starts ("NOW PLAYING", the title and the artist, lower left; with the song info always on it stays
 * as a calm smaller card), the fade out as the song ends, and the film's keys for a few seconds after the start.
 */
final class CineOverlay {
	/** The song card fades in at this many seconds after the song start and out (or shrinks) at the second, each fade this long. */
	private static final float CARD_IN = 1f;
	private static final float CARD_OUT = 6f;
	private static final float CARD_FADE = 0.5f;
	private static final float SHRINK_RATE = 4f;
	/** The keys are shown for this long after the start, then fade. */
	private static final float HINT_SECONDS = 3f;
	private static final float HINT_FADE = 0.5f;
	/** Sizes as shares of the screen, from the mockups (1440 by 900). */
	private static final float CARD_LEFT = 120f / 1440f;
	private static final float CARD_ABOVE_BARS = 44f / 900f;
	private static final float TITLE_SCALE = 2.3f;
	private static final float ARTIST_SCALE = 1.05f;
	/** The artwork: its side, the gap to the text, its corners as a share of its side, and its shadow's spread. */
	private static final float ART_SIZE = 46f;
	private static final float ART_GAP = 10f;
	private static final float ART_RADIUS = 0.12f;
	private static final int ART_SHADOW = 6;
	/** The calm card that stays with the song info always on: smaller, a bit lower, with room for its progress line. */
	private static final float ART_SIZE_SMALL = 30f;
	private static final float TITLE_SCALE_SMALL = 1.3f;
	private static final float ARTIST_SCALE_SMALL = 0.9f;
	private static final float SMALL_ABOVE_BARS = 0.6f;
	private static final float PROGRESS_ROOM = 4f;
	private static final float PROGRESS_LEAST = 60f;
	private static final float VIGNETTE_ALPHA = 0.42f;
	private static final float VIGNETTE_HEIGHT = 0.24f;
	private static final float VIGNETTE_WIDTH = 0.16f;
	private static final int VIGNETTE = 0xFF080412;
	/** As the song ends the picture fades out this far on its shot. */
	private static final float END_FADE = 0.85f;
	private static final float END_RATE = 3f;
	/** How quickly the bars slide in and out, per second. */
	private static final float BARS_RATE = 6f;
	private static final int HINT_GAP = 14;
	private static final int KEY_PAD = 4;
	private static final int KEY_HEIGHT = 12;

	private final CinematicCameraFeature feature;
	private final CineCamera camera;
	private final AnimClock clock = new AnimClock();
	private boolean wasRunning;
	private int cardTrack = -1;
	private float cardAge = Float.MAX_VALUE;
	private float hintAge = Float.MAX_VALUE;
	private float endShade;
	private float bars;
	/** How far the song card has shrunk into the calm card, from 0 to 1. */
	private float cardSmall;
	/** The keys' names, read once per film. */
	private final String[] hintKeys = new String[4];
	private static final String[] HINT_WORDS = {"stop", "next shot", "hold", "lyrics"};

	CineOverlay(CinematicCameraFeature feature, CineCamera camera) {
		this.feature = feature;
		this.camera = camera;
	}

	void render(GuiGraphicsExtractor graphics) {
		float dt = clock.tick();
		boolean running = camera.running();
		// the bars slide in when the film starts and out after it stopped, as before; the rest is only there while it runs
		bars += ((running ? 1f : 0f) - bars) * (1f - (float) Math.exp(-dt * BARS_RATE));
		if (!running && bars < 0.004f) {
			bars = 0f;
			wasRunning = false;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float ratio = feature.letterboxRatio();
		int bar = ratio <= 0f ? 0 : Math.round(Math.max(0f, (height - width / ratio) / 2f) * bars);
		if (!running) {
			wasRunning = false;
			drawBars(graphics, width, height, bar);
			return;
		}
		if (!wasRunning) {
			// a film starts: the card of the song that plays, and the keys
			cardAge = 0f;
			hintAge = 0f;
			cardTrack = MusicService.trackNumber();
			cardSmall = 0f;
			CinematicKeys.names(hintKeys);
		}
		wasRunning = true;
		if (MusicService.trackNumber() != cardTrack) {
			cardTrack = MusicService.trackNumber();
			cardAge = 0f;
		}
		cardAge += dt;
		hintAge += dt;
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), ModFeatures.MUSIC.accentColor(), 0f, false);
		if (feature.vignette()) {
			int edge = Ui.withOpacity(VIGNETTE, VIGNETTE_ALPHA);
			int band = Math.round(height * VIGNETTE_HEIGHT);
			int side = Math.round(width * VIGNETTE_WIDTH);
			ui.verticalGradient(0, 0, width, band, edge, 0);
			ui.verticalGradient(0, height - band, width, band, 0, edge);
			ui.horizontalGradient(0, 0, side, height, edge, 0);
			ui.horizontalGradient(width - side, 0, side, height, 0, edge);
		}
		// the song's last seconds fade out on the shot, before the next song
		float endTarget = camera.director.songEnding ? END_FADE * camera.director.endProgress : 0f;
		endShade += (endTarget - endShade) * Math.min(1f, dt * END_RATE);
		if (endShade > 0.004f) {
			graphics.fill(0, 0, width, height, Ui.withOpacity(0xFF000000, endShade));
		}
		// the film fades in from black at its start
		float black = camera.blackness();
		if (black > 0.004f) {
			graphics.fill(0, 0, width, height, Ui.withOpacity(0xFF000000, black));
		}
		drawBars(graphics, width, height, bar);
		if (feature.titleCard()) {
			drawCard(ui, width, height, bar, dt);
		}
		drawHint(ui, width, height, bar, 1f - black);
	}

	private static void drawBars(GuiGraphicsExtractor graphics, int width, int height, int bar) {
		if (bar > 0) {
			graphics.fill(0, 0, width, bar, 0xFF000000);
			graphics.fill(0, height - bar, width, height, 0xFF000000);
		}
	}

	/**
	 * Draws the song card in the lower left, as the mockup shows it: the artwork with rounded corners and a soft shadow (cross-fading when the song
	 * changes) beside "NOW PLAYING", the title and the artist. At the song start it fades in and after a few seconds out again; with the song
	 * info always on, it then settles into a calm, smaller card with a thin progress line instead.
	 */
	private void drawCard(Ui ui, int width, int height, int bar, float dt) {
		MusicService.Track track = MusicService.track();
		if (track == null) {
			return;
		}
		boolean always = feature.songInfoAlways();
		float in = Mth.clamp((cardAge - CARD_IN) / CARD_FADE, 0f, 1f);
		float alpha;
		if (always) {
			// after the start the card shrinks into the calm card and stays
			cardSmall += ((cardAge >= CARD_OUT ? 1f : 0f) - cardSmall) * (1f - (float) Math.exp(-dt * SHRINK_RATE));
			alpha = in;
		} else {
			cardSmall = 0f;
			alpha = in * Mth.clamp((CARD_OUT + CARD_FADE - cardAge) / CARD_FADE, 0f, 1f);
		}
		alpha *= feature.lyricFade();
		if (alpha <= 0.004f) {
			return;
		}
		float k = cardSmall * cardSmall * (3f - 2f * cardSmall);
		float art = Mth.lerp(k, ART_SIZE, ART_SIZE_SMALL);
		float titleScale = Mth.lerp(k, TITLE_SCALE, TITLE_SCALE_SMALL);
		float artistScale = Mth.lerp(k, ARTIST_SCALE, ARTIST_SCALE_SMALL);
		float labelAlpha = 1f - k;
		float left = width * CARD_LEFT;
		float bottom = height - bar - height * CARD_ABOVE_BARS * Mth.lerp(k, 1f, SMALL_ABOVE_BARS);
		String artist = track.artist().isEmpty() ? track.album() : track.artist();
		float artTop = bottom - art;
		float textLeft = left + art + ART_GAP;
		float artistY = bottom - 9f * artistScale - Mth.lerp(k, 0f, PROGRESS_ROOM);
		float titleY = artistY - 5f - 9f * titleScale;
		float labelY = titleY - 13f;
		// a soft dark wash behind the card, so it reads over a bright scene
		ui.wash(left + 80f, bottom - art / 2f, 150f, art * 0.95f, Ui.withOpacity(0xFF0A0414, 0.35f * alpha));
		int artLeft = Math.round(left);
		int artY = Math.round(artTop);
		int artSide = Math.round(art);
		ui.shadow(artLeft, artY, artSide, artSide, artSide * ART_RADIUS, ART_SHADOW, 0.5f * alpha);
		ModFeatures.MUSIC.drawCoverArt(ui, artLeft, artY, artSide, alpha);
		if (labelAlpha > 0.01f) {
			ui.sectionLabel("NOW PLAYING", textLeft, labelY, Ui.withOpacity(0xFFFFFFFF, 0.72f * alpha * labelAlpha));
		}
		String title = ui.fitScaled(track.title(), width * 0.45f, titleScale);
		ui.textAtScaled(title, textLeft, titleY, titleScale, Ui.withOpacity(0xFFFFFFFF, alpha));
		if (!artist.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(artist, width * 0.38f, artistScale), textLeft, artistY, artistScale, Ui.withOpacity(0xFFFFFFFF, 0.78f * alpha));
		}
		if (k > 0.01f && track.durationMs() > 0) {
			// the calm card's thin progress line under the text
			float lineWidth = Math.max(PROGRESS_LEAST, ui.widthScaled(title, titleScale));
			float done = Mth.clamp(MusicService.position() / (float) track.durationMs(), 0f, 1f);
			float lineY = bottom - 1f;
			ui.fill(Math.round(textLeft), Math.round(lineY), Math.round(lineWidth), 1, Ui.withOpacity(0xFFFFFFFF, 0.22f * alpha * k));
			ui.fill(Math.round(textLeft), Math.round(lineY), Math.round(lineWidth * done), 1, Ui.withOpacity(0xFFFFFFFF, 0.8f * alpha * k));
		}
	}

	/** Draws the film's keys in the bottom bar (or at the bottom without bars) for a few seconds after the start. */
	private void drawHint(Ui ui, int width, int height, int bar, float presence) {
		float alpha = Mth.clamp((HINT_SECONDS + HINT_FADE - hintAge) / HINT_FADE, 0f, 1f) * presence;
		if (alpha <= 0.004f) {
			return;
		}
		int middle = bar >= KEY_HEIGHT + 4 ? height - bar / 2 : height - KEY_HEIGHT;
		int x = Math.round(width - width * CARD_LEFT);
		// from the right: the last key first
		for (int i = hintKeys.length - 1; i >= 0; i--) {
			String key = hintKeys[i];
			if (key == null || key.isEmpty()) {
				continue;
			}
			int wordWidth = ui.width(HINT_WORDS[i]);
			x -= wordWidth;
			ui.textMiddle(HINT_WORDS[i], x, middle, Ui.withOpacity(0xFFFFFFFF, 0.55f * alpha));
			int keyWidth = Math.max(KEY_HEIGHT, ui.width(key) + 2 * KEY_PAD);
			x -= keyWidth + 5;
			ui.rounded(x, middle - KEY_HEIGHT / 2, keyWidth, KEY_HEIGHT, 3f, Ui.withOpacity(0xFFFFFFFF, 0.14f * alpha));
			ui.textCentered(key, x + keyWidth / 2, middle, Ui.withOpacity(0xFFFFFFFF, 0.8f * alpha));
			x -= HINT_GAP;
		}
	}
}
