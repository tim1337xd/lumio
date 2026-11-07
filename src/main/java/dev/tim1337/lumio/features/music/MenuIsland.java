package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Glyphs;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.util.Mth;

/**
 * The music island at the top of the main menu: a small pill with the cover, title, artist and level bars, which expands while hovered into a
 * card with the song's progress and previous, pause and next. Sizes are in the main menu's layout units, the pixels of its mockup.
 */
public final class MenuIsland {
	// the pill
	private static final float PILL_TOP = 50f;
	private static final float PILL_HEIGHT = 44f;
	/** The same room around the cover on its top, bottom and left, so it sits inside the pill. */
	private static final float PILL_COVER_GAP = 10f;
	private static final float PILL_PAD_LEFT = PILL_COVER_GAP;
	private static final float PILL_PAD_RIGHT = 16f;
	private static final float PILL_COVER = PILL_HEIGHT - 2 * PILL_COVER_GAP;
	private static final float GAP = 12f;
	private static final float TITLE_MAX = 220f;
	private static final float ARTIST_MAX = 150f;
	// the card
	private static final float CARD_TOP = 40f;
	private static final float CARD_WIDTH = 400f;
	private static final float CARD_HEIGHT = 172f;
	private static final float CARD_PAD = 16f;
	private static final float CARD_PAD_TOP = 14f;
	private static final float CARD_COVER = 56f;
	private static final float ROW_GAP = 14f;
	private static final float BUTTON = 36f;
	private static final float PLAY_BUTTON = 46f;
	private static final float BUTTON_GAP = 28f;
	private static final float GLYPH = 18f;
	/** Text scale per font pixel of the mockup. */
	private static final float TEXT = 1f / 9.6f;
	private static final int PLAY_GLYPH_COLOR = 0xFF0C1238;
	private static final MusicService.Action[] ACTIONS = {MusicService.Action.PREVIOUS, MusicService.Action.PLAY_PAUSE, MusicService.Action.NEXT};

	private final MusicFeature music;
	private final Spring shown = Spring.smooth(Spring.DEFAULT);
	private final Spring expand = Spring.smooth(Spring.DEFAULT);
	private final Spring[] buttonLight = {Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY)};
	// the island as last drawn, and how far it is expanded
	private float x;
	private float y;
	private float width;
	private float height;
	private float expanded;
	private float visible;
	// the song bar and the buttons' centers as last drawn
	private float barX;
	private float barY;
	private float barWidth;
	private float buttonsY;
	private float centerX;
	private String positionText = "";
	private String durationText = "";
	private long shownSecond = -1;

	public MenuIsland(MusicFeature music) {
		this.music = music;
	}

	/** @return whether a point is on the island as last drawn */
	public boolean contains(double mouseX, double mouseY) {
		return visible > 0.05f && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Draws the island centered on a point across the top, fading with the menu's entrance. */
	public void draw(Ui ui, float center, int mouseX, int mouseY, float fade) {
		MusicService.Track track = music.isEnabled() ? music.menuTrack() : null;
		visible = Mth.clamp(shown.follow(ui, track != null && music.hasSong() ? 1f : 0f), 0f, 1f) * fade;
		if (track == null || visible <= 0.01f) {
			return;
		}
		centerX = center;
		boolean hovered = contains(mouseX, mouseY) || music.seekingNow();
		expanded = Mth.clamp(expand.follow(ui, hovered ? 1f : 0f), 0f, 1f);
		float e = expanded;
		String artist = track.artist().isEmpty() ? track.album() : track.artist();
		float titleScale = 14f * TEXT;
		float titleWidth = Math.min(TITLE_MAX, ui.widthScaled(track.title(), titleScale));
		float artistWidth = artist.isEmpty() ? 0f : Math.min(ARTIST_MAX, ui.widthScaled(artist, titleScale));
		float barsWidth = 18f;
		float pillWidth = PILL_PAD_LEFT + PILL_COVER + GAP + titleWidth + (artist.isEmpty() ? 0f : GAP + artistWidth) + GAP + barsWidth + PILL_PAD_RIGHT;
		width = Mth.lerp(e, pillWidth, CARD_WIDTH);
		height = Mth.lerp(e, PILL_HEIGHT, CARD_HEIGHT);
		x = center - width / 2f;
		y = Mth.lerp(e, PILL_TOP, CARD_TOP);
		float radius = Mth.lerp(e, PILL_HEIGHT / 2f, 28f);
		HudStyle.panel(ui, Math.round(x), Math.round(y), Math.round(width), Math.round(height), radius, HudStyle.opacity() * visible, UiGlow.Target.TITLE, visible);

		int accent = music.accentColor();
		// the cover grows from the pill's round one into the card's square one
		float cover = Mth.lerp(e, PILL_COVER, CARD_COVER);
		float coverX = x + Mth.lerp(e, PILL_PAD_LEFT, CARD_PAD);
		float coverY = y + Mth.lerp(e, (PILL_HEIGHT - PILL_COVER) / 2f, CARD_PAD_TOP);
		// the cover's corners run parallel to the window's: its radius less the room around the cover
		float coverRadius = radius - Mth.lerp(e, PILL_COVER_GAP, CARD_PAD);
		music.drawCoverRounded(ui, Math.round(coverX), Math.round(coverY), Math.round(cover), coverRadius, accent, visible);

		// the pill's row fades out in the first half of the expansion, the card's content in in the second
		float pillText = visible * Mth.clamp(1f - e * 2f, 0f, 1f);
		float cardText = visible * Mth.clamp(e * 2f - 1f, 0f, 1f);
		float middle = y + PILL_HEIGHT / 2f;
		if (pillText > 0.01f) {
			float textX = x + PILL_PAD_LEFT + PILL_COVER + GAP;
			ui.textAtScaledBold(ui.fitScaled(track.title(), TITLE_MAX, titleScale), textX, middle - 4.5f * titleScale, titleScale, Ui.withOpacity(HudStyle.text(), pillText));
			if (!artist.isEmpty()) {
				ui.textAtScaled(ui.fitScaled(artist, ARTIST_MAX, titleScale), textX + titleWidth + GAP, middle - 4.5f * titleScale, titleScale,
					Ui.withOpacity(HudStyle.secondary(), pillText));
			}
		}
		// the level bars keep their place at the right end
		var pose = ui.graphics.pose();
		float barsX = x + width - Mth.lerp(e, PILL_PAD_RIGHT, CARD_PAD) - barsWidth;
		float barsY = Mth.lerp(e, middle, y + CARD_PAD_TOP + CARD_COVER / 2f);
		pose.pushMatrix();
		pose.translate(barsX, barsY);
		music.drawBars(ui, 0, 0, Ui.withOpacity(accent, visible), Math.round(Mth.lerp(e, 16f, 20f)));
		pose.popMatrix();
		if (cardText > 0.01f) {
			drawCard(ui, track, artist, cardText, mouseX, mouseY);
		}
	}

	private void drawCard(Ui ui, MusicService.Track track, String artist, float alpha, int mouseX, int mouseY) {
		float left = x + CARD_PAD + CARD_COVER + 14f;
		float room = width - (left - x) - CARD_PAD - 18f - 10f;
		float titleScale = 15f * TEXT;
		float artistScale = 13f * TEXT;
		ui.textAtScaledBold(ui.fitScaled(track.title(), room, titleScale), left, y + CARD_PAD_TOP + 11f, titleScale, Ui.withOpacity(HudStyle.text(), alpha));
		String source = MusicService.sourceDescription();
		int bracket = source.indexOf(" (");
		String second = artist + (bracket > 0 ? " · " + source.substring(0, bracket) : "");
		ui.textAtScaled(ui.fitScaled(second, room, artistScale), left, y + CARD_PAD_TOP + 31f, artistScale, Ui.withOpacity(HudStyle.secondary(), alpha));

		// the song's progress: times on both sides of a thin bar
		float rowY = y + CARD_PAD_TOP + CARD_COVER + ROW_GAP + 6f;
		updateTimes(track);
		float timeScale = 11.5f * TEXT;
		float timeWidth = 30f;
		ui.textAtScaled(positionText, x + CARD_PAD, rowY - 4.5f * timeScale, timeScale, Ui.withOpacity(HudStyle.secondary(), alpha));
		float durationWidth = ui.widthScaled(durationText, timeScale);
		ui.textAtScaled(durationText, x + width - CARD_PAD - durationWidth, rowY - 4.5f * timeScale, timeScale, Ui.withOpacity(HudStyle.secondary(), alpha));
		barX = x + CARD_PAD + timeWidth + 10f;
		barWidth = width - 2 * CARD_PAD - 2 * (timeWidth + 10f);
		barY = rowY - 2f;
		ui.roundedSmooth(barX, barY, barWidth, 4f, 2f, Ui.withOpacity(0xFFFFFFFF, 0.18f * alpha));
		if (track.durationMs() > 0) {
			ui.roundedSmooth(barX, barY, Math.max(4f, barWidth * music.shownProgress()), 4f, 2f, Ui.withOpacity(0xFFFFFFFF, alpha));
		}

		// previous, pause or play, next
		buttonsY = rowY + 6f + ROW_GAP + PLAY_BUTTON / 2f;
		boolean playing = MusicService.isPlaying();
		for (int i = 0; i < 3; i++) {
			float cx = buttonX(i);
			float size = i == 1 ? PLAY_BUTTON : BUTTON;
			boolean over = Math.abs(mouseX - cx) <= size / 2f && Math.abs(mouseY - buttonsY) <= size / 2f;
			float light = Mth.clamp(buttonLight[i].follow(ui, over ? 1f : 0f), 0f, 1f);
			if (i == 1) {
				float grow = 1f + 0.06f * light;
				ui.roundedSmooth(cx - size * grow / 2f, buttonsY - size * grow / 2f, size * grow, size * grow, size * grow / 2f, Ui.withOpacity(0xFFFFFFFF, alpha));
				Glyphs.draw(ui, playing ? 2 : 1, cx, buttonsY, GLYPH, Ui.withOpacity(PLAY_GLYPH_COLOR, alpha));
			} else {
				if (light > 0.01f) {
					ui.roundedSmooth(cx - size / 2f, buttonsY - size / 2f, size, size, size / 2f, Ui.withOpacity(0xFFFFFFFF, 0.12f * light * alpha));
				}
				Glyphs.draw(ui, i == 0 ? 0 : 3, cx, buttonsY, GLYPH, Ui.withOpacity(0xFFFFFFFF, alpha));
			}
		}
	}

	private float buttonX(int index) {
		float step = PLAY_BUTTON / 2f + BUTTON_GAP + BUTTON / 2f;
		return centerX + (index - 1) * step;
	}

	/** Formats the position and length once a second, not every frame. */
	private void updateTimes(MusicService.Track track) {
		long position = MusicService.position() / 1000L;
		if (position == shownSecond && !durationText.isEmpty()) {
			return;
		}
		shownSecond = position;
		positionText = MusicFeature.clock(position * 1000L);
		durationText = track.durationMs() > 0 ? MusicFeature.clock(track.durationMs()) : "–";
	}

	/** @return whether a click hit the island's buttons or song bar, which then did their part */
	public boolean click(double mouseX, double mouseY) {
		if (expanded < 0.6f || !contains(mouseX, mouseY)) {
			return contains(mouseX, mouseY);
		}
		MusicService.Track track = MusicService.track();
		if (track != null && track.durationMs() > 0 && mouseX >= barX - 4 && mouseX < barX + barWidth + 4 && Math.abs(mouseY - (barY + 2f)) <= 7) {
			music.beginSeek(Math.round(barX), Math.round(barWidth), mouseX);
			return true;
		}
		for (int i = 0; i < 3; i++) {
			float size = i == 1 ? PLAY_BUTTON : BUTTON;
			if (Math.abs(mouseX - buttonX(i)) <= size / 2f && Math.abs(mouseY - buttonsY) <= size / 2f) {
				UiSounds.click();
				MusicService.control(ACTIONS[i]);
				return true;
			}
		}
		return true;
	}
}
