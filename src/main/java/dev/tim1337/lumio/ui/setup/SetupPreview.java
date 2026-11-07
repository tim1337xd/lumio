package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.features.music.MusicService;
import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.render.RoundedImage;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

/**
 * The previews on the right of the steps Look and Music (the main menu step has its own, see {@link MenuPictures}): the main menu's world as a picture, and over it a small settings window and a hotbar
 * in the chosen look, or the music island with the song that plays.
 */
final class SetupPreview {
	/** The picture of the world is 320 by 200. */
	private static final float PICTURE_ASPECT = 1.6f;
	private static final float RADIUS = Radius.XL;
	private static final int SLOT = 14;
	private static final int SLOTS = 7;
	private static final int BARS = 4;
	/** The island: its room from the picture's edges, the width of all but the title and the artist, and the artist's share of a tight room. */
	private static final int ISLAND_MARGIN = 10;
	private static final int TITLE_GAP = 10;
	/** The cover in the pictured pill: the same room on its top, bottom and left, as in the main menu's. */
	private static final int ISLAND_HEIGHT = 24;
	private static final int ISLAND_COVER_GAP = 6;
	private static final int ISLAND_COVER = ISLAND_HEIGHT - 2 * ISLAND_COVER_GAP;
	private static final float ISLAND_FIXED = ISLAND_COVER_GAP + ISLAND_COVER + 7 + TITLE_GAP + 8 + BARS * 3.5f + 9;
	private static final float ARTIST_SHARE = 0.35f;
	/** A title too long for the island scrolls at this speed, in menu pixels a second, with a gap between its ends and a rest at its start. */
	private static final float MARQUEE_SPEED = 18f;
	private static final float MARQUEE_GAP = 32f;
	private static final double MARQUEE_PAUSE = 2.0;
	/** The sidebar of the pictured settings: with the add-on's world after Interface when there is one. */
	private static String[] pages() {
		SetupGroup world = SetupGroup.world();
		return world == null ? new String[]{"Home", "World", "Interface", "Music"} : new String[]{"Home", "World", "Interface", world.shortName(), "Music"};
	}

	private SetupPreview() {
	}

	/** Draws the main menu's world, cut to the box around the part a little right of the middle, where the house is. */
	static void world(SetupKit kit, int x, int y, int width, int height) {
		world(kit, x, y, width, height, RADIUS);
	}

	/** Draws the main menu's world with corners of the given radius. */
	static void world(SetupKit kit, int x, int y, int width, int height, float radius) {
		Ui ui = kit.ui;
		var picture = MenuWorld.thumbnail(ModFeatures.MENUS.worldSeason().ordinal());
		if (picture == null) {
			ui.roundedGradient(x, y, width, height, radius, 0xFF2A3560, 0xFF1A1830);
		} else {
			float share = Math.min(1f, width / (float) height / PICTURE_ASPECT);
			float left = Math.min(1f - share, Math.max(0f, 0.7f - share / 2f));
			RoundedImage.draw(ui.graphics, picture, x, y, width, height, radius, left, 1f, left + share, 0f, Ui.faded(0xFFFFFFFF));
		}
		ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(0xFFFFFFFF, 0.12f));
	}

	/** The look's preview: a small settings window with switches and a slider, and a hotbar, in the theme, accent and glow chosen. */
	static void look(SetupKit kit, int x, int y, int width, int height) {
		world(kit, x, y, width, height);
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		int windowX = x + 14;
		int windowY = y + 26;
		int windowWidth = width - 28;
		int windowHeight = Math.min(112, height - 70);
		if (ModFeatures.MENU.glowEnabled()) {
			ui.shadow(windowX, windowY, windowWidth, windowHeight, Radius.LG, 10, 0f);
			ui.halo(windowX, windowY, windowWidth, windowHeight, Radius.LG, 10, place -> Ui.withOpacity(ui.accent, 0.35f));
		}
		ui.rounded(windowX, windowY, windowWidth, windowHeight, Radius.LG, Ui.withOpacity(palette.window(), palette.glass() ? 0.62f : 0.96f));
		ui.ring(windowX, windowY, windowWidth, windowHeight, Radius.LG, 1, Ui.withOpacity(palette.text(), 0.2f));
		// the sidebar: the window's buttons and the pages, Home chosen
		int side = Math.min(59, windowWidth / 3);
		ui.rounded(windowX, windowY, side, windowHeight, Radius.LG, Ui.withOpacity(palette.text(), 0.06f));
		int[] lights = {0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840};
		for (int i = 0; i < lights.length; i++) {
			ui.roundedSmooth(windowX + 6 + i * 7, windowY + 8, 4.5f, 4.5f, 2.25f, lights[i]);
		}
		int itemY = windowY + 18;
		String[] pages = pages();
		for (int i = 0; i < pages.length; i++) {
			if (i == 0) {
				ui.rounded(windowX + 5, itemY, side - 10, 13, Radius.XS, ui.accent);
			}
			ui.textAtScaled(pages[i], windowX + 9, itemY + 3.5f, SetupKit.TINY * 0.85f, i == 0 ? 0xFFFFFFFF : Ui.withOpacity(palette.text(), 0.75f));
			itemY += 15;
		}
		// the page: two switches and a slider in a card
		int pageX = windowX + side + 8;
		int pageWidth = windowWidth - side - 16;
		ui.textAtScaledBold("Interface", pageX, windowY + 9, SetupKit.SMALL, palette.text());
		int cardY = windowY + 24;
		int rowHeight = 18;
		ui.rounded(pageX, cardY, pageWidth, 3 * rowHeight + 6, Radius.SM, Ui.withOpacity(palette.text(), 0.08f));
		String[] rows = {"Status bars", "Info bar"};
		for (int i = 0; i < rows.length; i++) {
			int rowY = cardY + i * rowHeight;
			ui.textAtScaled(rows[i], pageX + 6, rowY + 6, SetupKit.TINY * 0.85f, palette.text());
			kit.smallSwitch(pageX + pageWidth - 23, rowY + 5, 17, 10, 1f);
			ui.fill(pageX + 6, rowY + rowHeight, pageWidth - 12, 1, Ui.withOpacity(palette.text(), 0.08f));
		}
		int sliderY = cardY + 2 * rowHeight;
		ui.textAtScaled("Size", pageX + 6, sliderY + 5, SetupKit.TINY * 0.85f, palette.text());
		int trackWidth = pageWidth - 12;
		ui.rounded(pageX + 6, sliderY + 15, trackWidth, 3, Radius.pill(3), Ui.withOpacity(palette.text(), 0.2f));
		int filled = Math.round(trackWidth * 0.6f);
		ui.rounded(pageX + 6, sliderY + 15, filled, 3, Radius.pill(3), ui.accent);
		ui.roundedSmooth(pageX + 6 + filled - 4, sliderY + 12.5f, 8, 8, 4, 0xFFFFFFFF);

		// a hotbar at the bottom, the third slot chosen
		int barWidth = SLOTS * SLOT + (SLOTS - 1) * 4 + 8;
		int barX = x + (width - barWidth) / 2;
		int barY = y + height - 14 - SLOT - 8;
		ui.rounded(barX, barY, barWidth, SLOT + 8, Radius.SM, Ui.withOpacity(palette.window(), palette.glass() ? 0.55f : 0.9f));
		ui.ring(barX, barY, barWidth, SLOT + 8, Radius.SM, 1, Ui.withOpacity(0xFFFFFFFF, 0.2f));
		for (int i = 0; i < SLOTS; i++) {
			int slotX = barX + 4 + i * (SLOT + 4);
			ui.rounded(slotX, barY + 4, SLOT, SLOT, Radius.XS, Ui.withOpacity(0xFFFFFFFF, i == 2 ? 0.3f : 0.1f));
			if (i == 2) {
				ui.ring(slotX, barY + 4, SLOT, SLOT, Radius.XS, 1, ui.accent);
			}
		}
	}

	/** The music's preview: the island with the song at the top, with the song that plays or placeholders. */
	static void music(SetupKit kit, int x, int y, int width, int height, boolean on) {
		world(kit, x, y, width, height);
		Ui ui = kit.ui;
		MusicService.Track track = on ? MusicService.track() : null;
		String title = track == null ? "Song title" : track.title();
		String artist = track == null ? "Artist" : track.artist();
		float placeholder = track == null ? 0.55f : 1f;
		int accent = 0xFF000000 | ModFeatures.MUSIC.accentColor();

		// the island: the cover, the title, the artist and moving bars; as wide as the song needs, at most the picture's width, and a title
		// that does not fit then scrolls
		float titleFull = ui.boldWidthScaled(title, SetupKit.SMALL);
		float artistFull = ui.widthScaled(artist, SetupKit.SMALL);
		float room = width - 2 * ISLAND_MARGIN - ISLAND_FIXED;
		float artistWidth = titleFull + artistFull <= room ? artistFull : Math.min(artistFull, room * ARTIST_SHARE);
		float titleWidth = Math.min(titleFull, room - artistWidth);
		int islandWidth = Math.round(ISLAND_FIXED + titleWidth + artistWidth);
		int islandX = x + (width - islandWidth) / 2;
		int islandY = y + 17;
		ui.rounded(islandX, islandY, islandWidth, 24, Radius.pill(24), Ui.withOpacity(0xFF0E0A1E, 0.75f));
		ui.ring(islandX, islandY, islandWidth, 24, Radius.pill(24), 1, Ui.withOpacity(0xFFFFFFFF, 0.14f));
		if (track != null) {
			ModFeatures.MUSIC.drawArtwork(ui, islandX + ISLAND_COVER_GAP, islandY + ISLAND_COVER_GAP, ISLAND_COVER, 1f);
		} else {
			ui.roundedGradient(islandX + ISLAND_COVER_GAP, islandY + ISLAND_COVER_GAP, ISLAND_COVER, ISLAND_COVER, ISLAND_COVER / 2f, 0xFFFF375F, 0xFF5E5CE6);
		}
		float textX = islandX + ISLAND_COVER_GAP + ISLAND_COVER + 7;
		marquee(ui, title, textX, islandY + 8.5f, titleWidth, titleFull, Ui.withOpacity(0xFFFFFFFF, placeholder));
		textX += titleWidth + TITLE_GAP;
		ui.textAtScaled(kit.fit(artist, artistWidth, SetupKit.SMALL), textX, islandY + 8.5f, SetupKit.SMALL, Ui.withOpacity(0xFFFFFFFF, 0.6f * placeholder));
		float barX = textX + artistWidth + 8;
		double time = System.nanoTime() / 1_000_000_000.0;
		for (int i = 0; i < BARS; i++) {
			float level = track != null && MusicService.isPlaying() ? 0.35f + 0.65f * (float) Math.abs(Math.sin(time * (2.1 + i * 0.7) + i)) : 0.5f;
			int barHeight = Math.max(2, Math.round(8 * level));
			ui.rounded(Math.round(barX + i * 3.5f), islandY + 16 - barHeight, 2, barHeight, Radius.XXS, accent);
		}
	}

	/** Draws the title in its room, scrolling it slowly round when it is longer: it rests at its start a moment, then moves on. */
	private static void marquee(Ui ui, String text, float x, float y, float room, float full, int color) {
		if (full <= room) {
			ui.textAtScaledBold(text, x, y, SetupKit.SMALL, color);
			return;
		}
		float loop = full + MARQUEE_GAP;
		double cycle = loop / MARQUEE_SPEED + MARQUEE_PAUSE;
		double time = (System.nanoTime() / 1_000_000_000.0) % cycle;
		float offset = (float) Math.max(0.0, (time - MARQUEE_PAUSE) * MARQUEE_SPEED);
		ui.graphics.enableScissor(Math.round(x), Math.round(y) - 3, Math.round(x + room), Math.round(y) + 12);
		ui.textAtScaledBold(text, x - offset, y, SetupKit.SMALL, color);
		ui.textAtScaledBold(text, x - offset + loop, y, SetupKit.SMALL, color);
		ui.graphics.disableScissor();
	}

}
