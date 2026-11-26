package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Glyphs;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.util.Mth;

/**
 * The music card of the main menu: under Options and Quit in the left column, as wide as the menu, so everything is in one column and the right
 * side stays free for the scene. The cover, the title and artist, and previous, pause and next, always visible; the thin line at its bottom edge
 * is the song's progress. While nothing plays the card is not there and the column ends at Quit: it fades out two seconds after the music
 * stopped and fades in with it. A new song's title and artist cross-fade with the old ones; while paused the card is dimmer and shows play.
 * Sizes are in the main menu's layout units, the
 * pixels of its mockup.
 */
public final class MenuMusicCard {
	public static final float HEIGHT = 72f;
	private static final float PAD = 12f;
	private static final float COVER = 48f;
	private static final float COVER_RADIUS = 10f;
	private static final float TEXT_GAP = 12f;
	/** The buttons, from the right edge: the middle of the last one, the step between them, and the box of the middle one. */
	private static final float BUTTON_RIGHT = 30f;
	private static final float BUTTON_STEP = 36f;
	private static final float BUTTON = 32f;
	private static final float BUTTON_RADIUS = 9f;
	private static final float GLYPH = 14f;
	private static final float BUTTONS_ROOM = BUTTON_RIGHT + 2 * BUTTON_STEP + BUTTON / 2f + 8f;
	/** The progress line at the bottom edge, kept clear of the card's round corners. */
	private static final float LINE = 3f;
	private static final float LINE_INSET = 12f;
	/** Text scale per font pixel of the mockup. */
	private static final float TEXT = 1f / 9.6f;
	private static final float TITLE_PX = 15f;
	private static final float ARTIST_PX = 12.5f;
	private static final float SEEN = 0.05f;
	private static final MusicService.Action[] ACTIONS = {MusicService.Action.PREVIOUS, MusicService.Action.PLAY_PAUSE, MusicService.Action.NEXT};

	private final MusicFeature music;
	private final Spring shown = Spring.smooth(Spring.DEFAULT);
	private final Spring[] buttonLight = {Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY)};
	// the card as last drawn
	private float x;
	private float y;
	private float width;
	private float visible;

	public MenuMusicCard(MusicFeature music) {
		this.music = music;
	}

	/** @return whether a point is on the card as last drawn */
	public boolean contains(double mouseX, double mouseY) {
		return visible > SEEN && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT;
	}

	/** The card stays this long after the music stopped before it fades out, so a gap between two songs does not make it blink. */
	static final float HIDE_DELAY_SECONDS = 2f;
	/** A new song's title and artist fade in over this time while the old ones fade out. */
	private static final float TRACK_FADE_SECONDS = 0.25f;
	/** While paused the cover, the texts and the progress line keep this much of their light. */
	private static final float PAUSED_LIGHT = 0.55f;

	/** The song shown, the one before it while it fades out, and how far the change is. */
	private MusicService.Track showing;
	private String titleBefore = "";
	private String artistBefore = "";
	private float changed = 1f;
	private float sinceMusic;
	private long lastFrame;
	private final Spring pausedLight = Spring.smooth(Spring.DEFAULT);

	/**
	 * @param hasMusic     whether a song is there now
	 * @param sinceMusic   how long none has been there, in seconds
	 * @return whether the card is wanted: with a song, and for a moment after the last one
	 */
	static boolean wanted(boolean hasMusic, float sinceMusic) {
		return hasMusic || sinceMusic < HIDE_DELAY_SECONDS;
	}

	/** Draws the card with its top left corner at a point, fading with the menu's entrance. */
	public void draw(Ui ui, float left, float top, float cardWidth, int mouseX, int mouseY, float fade) {
		long now = System.nanoTime();
		float seconds = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		MusicService.Track track = music.isEnabled() && music.hasSong() ? music.menuTrack() : null;
		// a gap between songs: the last one stays on the card until the delay is over
		sinceMusic = track != null ? 0f : showing == null ? HIDE_DELAY_SECONDS : sinceMusic + seconds;
		if (track != null && (showing == null || !track.title().equals(showing.title()) || !track.artist().equals(showing.artist()))) {
			titleBefore = showing == null ? "" : showing.title();
			artistBefore = showing == null ? "" : artistOf(showing);
			changed = showing == null ? 1f : 0f;
			showing = track;
		}
		visible = Mth.clamp(shown.follow(ui, showing != null && wanted(track != null, sinceMusic) ? 1f : 0f), 0f, 1f) * fade;
		if (showing == null || visible <= 0.01f) {
			if (track == null && visible <= 0.01f) {
				showing = null;
			}
			return;
		}
		changed = Math.min(1f, changed + seconds / TRACK_FADE_SECONDS);
		x = left;
		y = top;
		width = cardWidth;
		boolean playing = MusicService.isPlaying() && track != null;
		float light = Mth.lerp(Mth.clamp(pausedLight.follow(ui, playing ? 0f : 1f), 0f, 1f), 1f, PAUSED_LIGHT);
		HudStyle.panel(ui, Math.round(x), Math.round(y), Math.round(width), Math.round(HEIGHT), Radius.XXL, HudStyle.opacity() * visible, UiGlow.Target.TITLE, visible);
		int accent = music.accentColor();
		music.drawCoverRounded(ui, Math.round(x + PAD), Math.round(y + (HEIGHT - COVER) / 2f), Math.round(COVER), COVER_RADIUS, accent, visible * light * changed);

		float textX = x + PAD + COVER + TEXT_GAP;
		float room = width - (textX - x) - BUTTONS_ROOM;
		float middle = y + HEIGHT / 2f;
		// the song before fades out where it stood while the new one fades in
		if (changed < 1f) {
			texts(ui, titleBefore, artistBefore, textX, middle, room, visible * light * (1f - changed));
		}
		texts(ui, showing.title(), artistOf(showing), textX, middle, room, visible * light * changed);

		// previous, pause or play, next: always there, the middle one in a box
		for (int i = 0; i < ACTIONS.length; i++) {
			float cx = buttonX(i);
			boolean over = overButton(i, mouseX, mouseY);
			float hover = Mth.clamp(buttonLight[i].follow(ui, over ? 1f : 0f), 0f, 1f);
			float box = (i == 1 ? 0.14f : 0f) + 0.12f * hover;
			if (box > 0.01f) {
				ui.roundedSmooth(cx - BUTTON / 2f, middle - BUTTON / 2f, BUTTON, BUTTON, BUTTON_RADIUS, Ui.withOpacity(0xFFFFFFFF, box * visible));
			}
			Glyphs.draw(ui, i == 0 ? 0 : i == 2 ? 3 : playing ? 2 : 1, cx, middle, GLYPH, Ui.withOpacity(0xFFFFFFFF, visible));
		}

		// the song's progress as a thin line along the bottom edge
		if (showing.durationMs() > 0 && track != null) {
			float lineWidth = (width - 2 * LINE_INSET) * Mth.clamp(music.shownProgress(), 0f, 1f);
			if (lineWidth >= LINE) {
				ui.roundedSmooth(x + LINE_INSET, y + HEIGHT - LINE, lineWidth, LINE, LINE / 2f, Ui.withOpacity(accent, visible * light));
			}
		}
	}

	private static String artistOf(MusicService.Track track) {
		return track.artist().isEmpty() ? track.album() : track.artist();
	}

	/** The title and, under it, the artist; the title alone in the middle without one. */
	private static void texts(Ui ui, String title, String artist, float textX, float middle, float room, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		float titleScale = TITLE_PX * TEXT;
		float artistScale = ARTIST_PX * TEXT;
		if (artist.isEmpty()) {
			ui.textAtScaledBold(ui.fitScaled(title, room, titleScale), textX, middle - 4.5f * titleScale, titleScale, Ui.withOpacity(HudStyle.text(), alpha));
		} else {
			ui.textAtScaledBold(ui.fitScaled(title, room, titleScale), textX, middle - 10f - 4.5f * titleScale, titleScale, Ui.withOpacity(HudStyle.text(), alpha));
			ui.textAtScaled(ui.fitScaled(artist, room, artistScale), textX, middle + 10f - 4.5f * artistScale, artistScale, Ui.withOpacity(HudStyle.secondary(), alpha));
		}
	}

	private float buttonX(int index) {
		return x + width - BUTTON_RIGHT - (ACTIONS.length - 1 - index) * BUTTON_STEP;
	}

	private boolean overButton(int index, double mouseX, double mouseY) {
		return visible > SEEN && Math.abs(mouseX - buttonX(index)) <= BUTTON / 2f && Math.abs(mouseY - (y + HEIGHT / 2f)) <= BUTTON / 2f;
	}

	/** @return whether a click hit the card: on a button it did that button's part, one click for one command of the player */
	public boolean click(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		for (int i = 0; i < ACTIONS.length; i++) {
			if (overButton(i, mouseX, mouseY)) {
				UiSounds.click();
				MusicService.control(ACTIONS[i]);
				return true;
			}
		}
		return true;
	}
}
