package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.features.music.MusicService;
import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.render.RoundedImage;
import dev.tim1337.lumio.ui.Glyphs;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

/**
 * The previews on the right of the steps Look and Music (the main menu step has its own, see {@link MenuPictures}): the main menu's world as a picture, and over it a small settings window and a hotbar
 * in the chosen look, or the main menu's column with its music card and the song that plays.
 */
final class SetupPreview {
	/** The picture of the world is 320 by 200. */
	private static final float PICTURE_ASPECT = 1.6f;
	private static final float RADIUS = Radius.XL;
	private static final int SLOT = 14;
	private static final int SLOTS = 7;
	/** The pictured column of the main menu: its room from the picture's edges, its width, and the heights of its parts. */
	private static final int CARD_MARGIN = 10;
	private static final float CARD_SHARE = 0.48f;
	private static final int CARD_MOST_WIDTH = 150;
	private static final int CARD_GAP = 4;
	private static final int PLAY_CARD = 30;
	private static final int BUTTON_ROW = 12;
	/** The pictured music card: its height, the cover and the room around it, and its three buttons. */
	private static final int CARD_HEIGHT = 26;
	private static final int CARD_PAD = 5;
	private static final int CARD_COVER = 16;
	private static final float CARD_GLYPH = 6f;
	private static final float CARD_GLYPH_GAP = 5f;
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
		if (ModFeatures.MENU.glowOn()) {
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

	/** The music's preview: the card in the main menu's left column, under its buttons, with the song that plays or placeholders. */
	static void music(SetupKit kit, int x, int y, int width, int height, boolean on) {
		world(kit, x, y, width, height);
		Ui ui = kit.ui;
		MusicService.Track track = on ? MusicService.track() : null;
		String title = track == null ? "Song title" : track.title();
		String artist = track == null ? "Artist" : track.artist();
		float placeholder = track == null ? 0.55f : 1f;
		int accent = 0xFF000000 | ModFeatures.MUSIC.accentColor();

		// the column as the main menu has it, small: the play card, Options and Quit as plain boxes, and the music card under them
		int column = Math.min(CARD_MOST_WIDTH, Math.round(width * CARD_SHARE));
		int left = x + CARD_MARGIN;
		int cardY = y + height - CARD_MARGIN - CARD_HEIGHT;
		int buttonsY = cardY - CARD_GAP - BUTTON_ROW;
		int playY = buttonsY - CARD_GAP - PLAY_CARD;
		int box = Ui.withOpacity(0xFF0E0A1E, 0.75f);
		int edge = Ui.withOpacity(0xFFFFFFFF, 0.14f);
		ui.rounded(left, playY, column, PLAY_CARD, Radius.SM, Ui.withOpacity(0xFF0E0A1E, 0.5f));
		int half = (column - CARD_GAP) / 2;
		ui.rounded(left, buttonsY, half, BUTTON_ROW, Radius.SM, Ui.withOpacity(0xFF0E0A1E, 0.5f));
		ui.rounded(left + half + CARD_GAP, buttonsY, column - half - CARD_GAP, BUTTON_ROW, Radius.SM, Ui.withOpacity(0xFF0E0A1E, 0.5f));
		ui.rounded(left, cardY, column, CARD_HEIGHT, Radius.SM, box);
		ui.ring(left, cardY, column, CARD_HEIGHT, Radius.SM, 1, edge);
		int coverY = cardY + (CARD_HEIGHT - CARD_COVER) / 2;
		if (track != null) {
			ModFeatures.MUSIC.drawArtwork(ui, left + CARD_PAD, coverY, CARD_COVER, 1f);
		} else {
			ui.roundedGradient(left + CARD_PAD, coverY, CARD_COVER, CARD_COVER, Radius.XS, 0xFFFF375F, 0xFF5E5CE6);
		}
		// previous, pause or play, next at the right, always there
		boolean playing = track != null && MusicService.isPlaying();
		float middle = cardY + CARD_HEIGHT / 2f;
		float buttonX = left + column - CARD_PAD - CARD_GLYPH / 2f;
		for (int i = 2; i >= 0; i--) {
			Glyphs.draw(ui, i == 0 ? 0 : i == 2 ? 3 : playing ? 2 : 1, buttonX, middle, CARD_GLYPH, Ui.withOpacity(0xFFFFFFFF, placeholder));
			buttonX -= CARD_GLYPH + CARD_GLYPH_GAP;
		}
		float textX = left + CARD_PAD + CARD_COVER + 5;
		float room = buttonX + CARD_GLYPH / 2f - textX;
		ui.textAtScaledBold(kit.fit(title, room, SetupKit.SMALL), textX, middle - 8f, SetupKit.SMALL, Ui.withOpacity(0xFFFFFFFF, placeholder));
		ui.textAtScaled(kit.fit(artist, room, SetupKit.SMALL), textX, middle + 1f, SetupKit.SMALL, Ui.withOpacity(0xFFFFFFFF, 0.6f * placeholder));
		// the thin line of the song's progress along the card's bottom edge
		float progress = track == null ? 0.3f : Math.clamp(ModFeatures.MUSIC.cardProgress(), 0f, 1f);
		ui.rounded(left + 3, cardY + CARD_HEIGHT - 2, Math.max(2, Math.round((column - 6) * progress)), 2, Radius.XXS, accent);
	}
}
