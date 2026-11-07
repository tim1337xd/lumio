package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

/**
 * The song that plays now, at the top of the music page: the cover, the title and the artist, the progress with both times, and a chip with the
 * source it comes from (green while it plays). Without a song it says so. The times are written only when the second changes.
 */
final class NowPlayingControl extends Control {
	private static final int HEIGHT = 58;
	private static final int PAD = 9;
	private static final int COVER = HEIGHT - 2 * PAD;
	private static final int TEXT_GAP = 10;
	private static final int BAR = 3;
	private static final int TIME_GAP = 6;
	private static final int CHIP_HEIGHT = 14;
	private static final int CHIP_PAD = 7;
	private static final int DOT = 2;
	/** How strongly the cover's color tints the card. */
	private static final float TINT = 0.16f;
	private static final String NOTHING = "Nothing plays right now";
	private static final String NOTHING_HINT = "Play a song in your player and it shows here";

	private final MusicFeature music;
	private long shownSecond = -1L;
	private long shownLength = -1L;
	private String elapsed = "";
	private String length = "";

	NowPlayingControl(MusicFeature music) {
		this.music = music;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	public boolean bare() {
		return true;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return HEIGHT;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		MusicService.Track track = MusicService.track();
		ui.rounded(x, y, width, height, Radius.LG, palette.card());
		ui.rounded(x, y, width, height, Radius.LG, Ui.withOpacity(music.coverTint(), TINT));
		ui.ring(x, y, width, height, Radius.LG, 1, palette.cardBorder());
		music.drawArtwork(ui, x + PAD, y + PAD, COVER, 1f);
		int textX = x + PAD + COVER + TEXT_GAP;
		// the source chip at the right, centered on the title and the artist
		String source = music.sourceName();
		boolean playing = track != null && MusicService.isPlaying();
		int chipWidth = Math.round(ui.widthScaled(source, TextScale.SMALL)) + 2 * CHIP_PAD + (playing ? 2 * DOT + 4 : 0);
		int chipX = x + width - PAD - chipWidth;
		int chipY = y + PAD + 4;
		int green = SystemColors.GREEN;
		ui.rounded(chipX, chipY, chipWidth, CHIP_HEIGHT, Radius.pill(CHIP_HEIGHT), Ui.withOpacity(playing ? green : palette.text(), 0.14f));
		int chipTextX = chipX + CHIP_PAD;
		if (playing) {
			ui.circle(chipTextX + DOT, chipY + CHIP_HEIGHT / 2, DOT, green);
			chipTextX += 2 * DOT + 4;
		}
		ui.textAtScaled(source, chipTextX, chipY + CHIP_HEIGHT / 2f - 3.5f, TextScale.SMALL, playing ? Ui.mix(palette.text(), green, 0.6f) : palette.textSecondary());
		int room = chipX - 8 - textX;
		if (track == null) {
			ui.textAtScaledBold(ui.fit(NOTHING, room), textX, y + PAD + 6, 1f, palette.text());
			ui.textAtScaled(ui.fitScaled(NOTHING_HINT, room, TextScale.SMALL), textX, y + PAD + 20, TextScale.SMALL, palette.textSecondary());
			return;
		}
		ui.textAtScaledBold(ui.fit(track.title(), room), textX, y + PAD + 1, 1f, palette.text());
		ui.textAtScaled(ui.fitScaled(track.artist(), room, TextScale.SMALL), textX, y + PAD + 14, TextScale.SMALL, palette.textSecondary());
		long position = Math.max(0L, MusicService.position());
		long duration = Math.max(0L, track.durationMs());
		times(position, duration);
		int barY = y + height - PAD - 4;
		float elapsedWidth = ui.widthScaled(elapsed, TextScale.SMALL);
		float lengthWidth = ui.widthScaled(length, TextScale.SMALL);
		int barX = Math.round(textX + elapsedWidth + TIME_GAP);
		int barRight = Math.round(x + width - PAD - lengthWidth - TIME_GAP);
		ui.textAtScaled(elapsed, textX, barY - 2.5f, TextScale.SMALL, palette.textSecondary());
		ui.textAtScaled(length, barRight + TIME_GAP, barY - 2.5f, TextScale.SMALL, palette.textSecondary());
		int barWidth = Math.max(0, barRight - barX);
		ui.rounded(barX, barY, barWidth, BAR, Radius.pill(BAR), Ui.withOpacity(palette.text(), 0.16f));
		float done = duration > 0L ? Math.min(1f, position / (float) duration) : 0f;
		ui.rounded(barX, barY, Math.round(barWidth * done), BAR, Radius.pill(BAR), palette.text());
	}

	/** Writes the elapsed time and the length when either second changed. */
	private void times(long position, long duration) {
		long second = position / 1000L;
		long lengthSecond = duration / 1000L;
		if (second != shownSecond) {
			shownSecond = second;
			elapsed = MusicFeature.clock(position);
		}
		if (lengthSecond != shownLength) {
			shownLength = lengthSecond;
			length = MusicFeature.clock(duration);
		}
	}
}
