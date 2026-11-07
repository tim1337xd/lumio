package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.features.music.MusicService;
import dev.tim1337.lumio.menu.ConfigsFeature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;

/**
 * The home page: four status tiles (the changelog and HUD editor buttons are in the page's header), the features for where the player is (the island's activities, or
 * the world features as quick switches elsewhere), the song that plays, and the settings changed last, which lead to where they are. The
 * texts that change are built once per second, not every frame.
 */
public class HomeControl extends Control {
	private static final int TILE_HEIGHT = 40;
	private static final int TILES = 4;
	private static final int GAP = 8;
	private static final int NOW_HEIGHT = 84;
	private static final int COVER = 58;
	private static final int PAD = 10;
	private static final long REFRESH_MILLIS = 1000;
	/** Most of the source line the lyrics chip may take with the lyrics' origin in it. */
	private static final float CHIP_SHARE = 0.6f;

	private final Runnable onChanged;
	private long refreshedAt;
	// the status tiles
	private int featuresOn = -1;
	private int featuresAll = -1;
	private String featuresOnText = "";
	private String featuresAllText = "";
	private String configName = "";
	private int configPrimary = SystemColors.GRAY_RGB;
	private int configSecondary = SystemColors.GRAY_RGB;
	private String themeName = "";
	private int fps = -1;
	private String fpsText = "";
	// the features for where the player is
	private final IslandCard island;
	private boolean islandKnown;
	// the settings changed last
	private final RecentChangesCard changes;
	// the song
	private String songSource = "";
	/** How the lyrics are synced, with and without where they come from, for the chip on the song card. */
	private String lyricsChip = "";
	private String lyricsChipShort = "";
	private long shownSecond = -1;
	private String elapsed = "";
	private String total = "";

	public HomeControl(Runnable onChanged) {
		this.onChanged = onChanged;
		this.island = new IslandCard(onChanged);
		this.changes = new RecentChangesCard(onChanged);
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

	private int columnWidth() {
		return (width - GAP) / 2;
	}

	private int columnsTop() {
		return y + TILE_HEIGHT + 12;
	}

	@Override
	protected int measure(Ui ui, int width) {
		this.width = width;
		refresh(false);
		int left = island.height(ui, columnWidth());
		int right = NOW_HEIGHT + GAP + changes.height();
		return TILE_HEIGHT + 12 + Math.max(left, right);
	}

	/** Reads what the page shows: once per second, or at once when the page opens. */
	private void refresh(boolean now) {
		long time = System.currentTimeMillis();
		if (!now && time - refreshedAt < REFRESH_MILLIS && islandKnown) {
			return;
		}
		refreshedAt = time;
		int all = 0;
		int on = 0;
		for (Feature feature : FeatureManager.features()) {
			if (feature.children().isEmpty() && feature.isSwitchable()) {
				all++;
				if (feature.isEnabled()) {
					on++;
				}
			}
		}
		if (on != featuresOn || all != featuresAll) {
			featuresOn = on;
			featuresAll = all;
			featuresOnText = String.valueOf(on);
			featuresAllText = "of " + all;
		}
		ConfigsFeature.Entry config = ModFeatures.CONFIGS.activeEntry();
		configName = config == null ? "None" : config.name();
		configPrimary = config == null ? SystemColors.GRAY_RGB : config.primary();
		configSecondary = config == null ? SystemColors.GRAY_RGB : config.secondary();
		themeName = ModFeatures.MENU.theme().displayName().getString();
		int nowFps = Minecraft.getInstance().getFps();
		if (nowFps != fps) {
			fps = nowFps;
			fpsText = String.valueOf(nowFps);
		}
		songSource = "Now playing  ·  " + MusicService.sourceDescription();
		lyricsChip = MusicService.lyricsChip(true);
		lyricsChipShort = MusicService.lyricsChip(false);
		changes.refresh(true);
		pickArea();
	}

	/** Chooses the features to show: those of the island the player is on, or the world features as quick switches. */
	private void pickArea() {
		island.refresh();
		islandKnown = true;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		refresh(false);
		drawTiles(ui);
		int top = columnsTop();
		drawArea(ui, mouseX, mouseY, x, top, columnWidth());
		int rightX = x + columnWidth() + GAP;
		drawSong(ui, rightX, top, width - columnWidth() - GAP);
		changes.draw(ui, mouseX, mouseY, rightX, top + NOW_HEIGHT + GAP, width - columnWidth() - GAP);
	}

	/** Draws the four status tiles: features on, the config in use, the theme and the frame rate. */
	private void drawTiles(Ui ui) {
		int top = y;
		int tileWidth = (width - (TILES - 1) * 8) / TILES;
		tile(ui, x, top, tileWidth, "Features on", featuresOnText, featuresAllText, -1);
		tile(ui, x + (tileWidth + 8), top, tileWidth, "Config in use", configName, null, configPrimary);
		tile(ui, x + 2 * (tileWidth + 8), top, tileWidth, "Theme", themeName, null, -1);
		tile(ui, x + 3 * (tileWidth + 8), top, tileWidth, "Frame rate", fpsText, "fps", -1);
	}

	/** Draws a tile: a label, a value in bold, an optional smaller unit after it, and an optional swatch before the value. */
	private void tile(Ui ui, int tx, int ty, int tileWidth, String label, String value, String unit, int swatch) {
		Palette palette = ui.palette;
		ui.rounded(tx, ty, tileWidth, TILE_HEIGHT, Radius.LG, palette.card());
		ui.ring(tx, ty, tileWidth, TILE_HEIGHT, Radius.LG, 1, palette.cardBorder());
		ui.textAtScaled(label, tx + PAD, ty + 7, TextScale.SMALL, palette.textSecondary());
		int valueX = tx + PAD;
		if (swatch >= 0) {
			ui.circle(valueX + 9, ty + 26, 5, 0xFF000000 | configSecondary);
			ui.circle(valueX + 5, ty + 26, 5, 0xFF000000 | swatch);
			valueX += 18;
		}
		int room = tx + tileWidth - PAD - valueX;
		String shown = ui.fit(value, room - (unit != null ? ui.width(unit) + 4 : 0));
		ui.textAtScaledBold(shown, valueX, ty + 21, 1f, palette.text());
		if (unit != null) {
			ui.text(unit, valueX + ui.width(shown) + 4, ty + 21, palette.textSecondary());
		}
	}

	/** Draws the features for where the player is (see {@link IslandCard}). */
	private void drawArea(Ui ui, int mouseX, int mouseY, int ax, int ay, int areaWidth) {
		island.draw(ui, mouseX, mouseY, ax, ay, areaWidth);
	}

	/** Draws the song that plays: cover, source, title, artist, the sung line and the progress, or that nothing plays. */
	private void drawSong(Ui ui, int sx, int sy, int songWidth) {
		Palette palette = ui.palette;
		int accent = 0xFF000000 | ModFeatures.MUSIC.accentColor();
		ui.rounded(sx, sy, songWidth, NOW_HEIGHT, Radius.LG, palette.card());
		ui.rounded(sx, sy, songWidth, NOW_HEIGHT, Radius.LG, Ui.withOpacity(accent, 0.14f));
		ui.ring(sx, sy, songWidth, NOW_HEIGHT, Radius.LG, 1, palette.cardBorder());
		MusicService.Track track = MusicService.track();
		if (track == null || !ModFeatures.MUSIC.isEnabled()) {
			ui.textCentered("Nothing is playing", sx + songWidth / 2, sy + NOW_HEIGHT / 2 - 5, palette.textSecondary());
			ui.textAtScaled("Start a song in your player", sx + songWidth / 2f - ui.widthScaled("Start a song in your player", TextScale.SMALL) / 2f,
				sy + NOW_HEIGHT / 2f + 4, TextScale.SMALL, palette.textSecondary());
			return;
		}
		ModFeatures.MUSIC.drawArtwork(ui, sx + PAD, sy + (NOW_HEIGHT - COVER) / 2, COVER, 1f);
		int textX = sx + PAD + COVER + 10;
		int room = sx + songWidth - PAD - textX;
		// how the lyrics are synced, as a chip at the end of the source line; without the lyrics' origin if it would crowd the line
		String chip = ui.chipWidth(lyricsChip) <= room * CHIP_SHARE ? lyricsChip : lyricsChipShort;
		int chipWidth = chip.isEmpty() ? 0 : ui.chipWidth(chip);
		if (chipWidth > 0) {
			ui.chip(chip, sx + songWidth - PAD - chipWidth, sy + 13);
		}
		int sourceRoom = room - (chipWidth > 0 ? chipWidth + 6 : 0);
		ui.textAtScaled(ui.fitScaled(songSource, sourceRoom, TextScale.SMALL), textX, sy + 10, TextScale.SMALL, Ui.mix(palette.textSecondary(), accent, 0.4f));
		ui.textAtScaledBold(ui.fit(track.title(), room), textX, sy + 21, 1f, palette.text());
		ui.text(ui.fit(track.artist(), room), textX, sy + 33, palette.textSecondary());
		String line = MusicService.lyricAt(MusicService.position());
		if (line != null && !line.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(line, room, TextScale.SMALL), textX, sy + 47, TextScale.SMALL, palette.text());
		}
		long position = MusicService.position();
		long length = Math.max(1, track.durationMs());
		long second = position / 1000;
		if (second != shownSecond) {
			shownSecond = second;
			elapsed = time(position);
			total = time(track.durationMs());
		}
		int barY = sy + NOW_HEIGHT - 14;
		int timeWidth = ui.width(total);
		ui.textAtScaled(elapsed, textX, barY - 3, TextScale.SMALL, palette.textSecondary());
		int barX = textX + timeWidth + 6;
		int barWidth = Math.max(10, room - 2 * timeWidth - 12);
		ui.rounded(barX, barY, barWidth, 3, Radius.pill(3), palette.trackOff());
		ui.rounded(barX, barY, Math.round(barWidth * Math.min(1f, position / (float) length)), 3, Radius.pill(3), palette.text());
		ui.textAtScaled(total, barX + barWidth + 6, barY - 3, TextScale.SMALL, palette.textSecondary());
	}

	private static String time(long millis) {
		long seconds = Math.max(0, millis / 1000);
		long rest = seconds % 60;
		return seconds / 60 + (rest < 10 ? ":0" : ":") + rest;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		return island.click(mouseX, mouseY, button) || changes.click(mouseX, mouseY);
	}
}
