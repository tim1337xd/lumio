package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The last step: a check mark, what was picked as six cards that lead back to their step (three across, two on a narrow window), and two tips
 * for later.
 */
final class ReadyStep implements SetupStep {
	private static final int CHECK = 41;
	private static final float TITLE = 2.1f;
	private static final int CARD_HEIGHT = 33;
	private static final int CARD_GAP = 6;
	/** Summary cards in a row: three, or two where the window has no room for the right column. */
	private static final int COLUMNS = 3;
	private static final int NARROW_COLUMNS = 2;
	private static final int CARD_TILE = 19;
	private static final int TIP_HEIGHT = 64;
	private static final int TIP_GAP = 6;
	private static final int SECTION_GAP = 16;

	/** A summary card: its icon and colors, its label, what was picked, and the step it leads back to. */
	private record Summary(String key, String icon, int light, int dark, String label, String value, int step) {
	}

	/** A tip: its icon, a key to show next to it (or null), its title and text. */
	private record Tip(String icon, String key, String title, String text) {
	}

	/** The first tip, about the settings: where they are depends on the main menu picked. */
	private static final Tip SETTINGS_LIVING = new Tip("gear", null, "Settings any time", "The gear in the main menu, or /lumio in the game.");
	private static final Tip SETTINGS_CLASSIC = new Tip("sparkle", null, "Settings any time",
		"The small button in the main menu, or /lumio in the game.");
	private static final Tip SEARCH = new Tip("search", "Ctrl F", "Find any setting", "Search for a feature, a setting or a page like \"lyrics\".");

	private final SetupScreen screen;

	ReadyStep(SetupScreen screen) {
		this.screen = screen;
	}

	@Override
	public String name() {
		return "Ready";
	}

	@Override
	public void draw(SetupKit kit, int x, int y, int width, int height, boolean wide) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		int column = width;
		int left = x;
		float middle = x + width / 2f;

		// the check mark in the accent, with a soft ring of light
		int top = y + 2;
		int checkX = Math.round(middle - CHECK / 2f);
		ui.roundedSmooth(checkX - 6, top - 6, CHECK + 12, CHECK + 12, (CHECK + 12) / 2f, Ui.withOpacity(ui.accent, 0.18f));
		ui.roundedSmooth(checkX, top, CHECK, CHECK, CHECK / 2f, ui.accent);
		int checkIcon = 22;
		ui.icon("check", checkX + (CHECK - checkIcon) / 2, top + (CHECK - checkIcon) / 2, checkIcon, 0xFFFFFFFF);
		top += CHECK + SECTION_GAP;
		float titleWidth = ui.titleWidthScaled("You are all set", TITLE);
		ui.textAtScaledTitle("You are all set", middle - titleWidth / 2f, top, TITLE, palette.text());
		top += 23;
		kit.centered("Here is what you picked. Click any of it to change it.", middle, top + 4, SetupKit.BODY, false, palette.textSecondary());
		top += 10 + SECTION_GAP;

		// what was picked; each card goes back to its step
		List<Summary> summaries = summaries(screen.choices);
		int columns = wide ? COLUMNS : NARROW_COLUMNS;
		int rowsBefore = (summaries.size() - 1) / columns;
		for (int i = 0; i < summaries.size(); i++) {
			// the last row's cards share its whole width, so fewer cards leave no gap
			int row = i / columns;
			int inRow = row == rowsBefore ? summaries.size() - rowsBefore * columns : columns;
			int cardWidth = (column - (inRow - 1) * CARD_GAP) / inRow;
			int cardX = left + (i % columns) * (cardWidth + CARD_GAP);
			int cardY = top + row * (CARD_HEIGHT + CARD_GAP);
			drawSummary(kit, cardX, cardY, cardWidth, summaries.get(i));
		}
		int rows = (summaries.size() + columns - 1) / columns;
		top += rows * CARD_HEIGHT + (rows - 1) * CARD_GAP + SECTION_GAP;

		// two tips for later
		List<Tip> tips = List.of(screen.choices.livingWorld() ? SETTINGS_LIVING : SETTINGS_CLASSIC, SEARCH);
		int tipWidth = (column - (tips.size() - 1) * TIP_GAP) / tips.size();
		for (int i = 0; i < tips.size(); i++) {
			Tip tip = tips.get(i);
			int tipX = left + i * (tipWidth + TIP_GAP);
			kit.box(tipX, top, tipWidth, TIP_HEIGHT, SetupKit.CARD_RADIUS + 2);
			ui.icon(tip.icon(), tipX + 10, top + 10, 13, palette.text());
			if (tip.key() != null) {
				int keyWidth = Math.round(ui.boldWidthScaled(tip.key(), SetupKit.TINY)) + 9;
				int keyX = tipX + tipWidth - 10 - keyWidth;
				ui.rounded(keyX, top + 9, keyWidth, 14, Radius.XS, Ui.withOpacity(palette.text(), 0.10f));
				ui.ring(keyX, top + 9, keyWidth, 14, Radius.XS, 1, Ui.withOpacity(palette.text(), 0.16f));
				kit.centered(tip.key(), keyX + keyWidth / 2f, top + 16, SetupKit.TINY, true, palette.text());
			}
			ui.textAtScaledBold(kit.fit(tip.title(), tipWidth - 20, SetupKit.BODY), tipX + 10, top + 30, SetupKit.BODY, palette.text());
			int lineY = top + 43;
			for (String line : kit.wrap(tip.text(), tipWidth - 20, SetupKit.SMALL)) {
				if (lineY > top + TIP_HEIGHT - 10) {
					break;
				}
				ui.textAtScaled(line, tipX + 10, lineY, SetupKit.SMALL, palette.textSecondary());
				lineY += 10;
			}
		}
	}

	/** @return the summary cards, in the order of the mockup: the main menu, the look and how you play, then features, music and startup */
	private static List<Summary> summaries(SetupChoices choices) {
		MenuFeature menu = ModFeatures.MENU;
		String look = menu.theme().displayName().getString() + " · "
			+ (menu.accent() == MenuFeature.Accent.CUSTOM ? "Custom color" : menu.accent().displayName().getString());
		List<Summary> summaries = new ArrayList<>();
		summaries.add(new Summary("summary_menu", "window", 0x30D158, 0x64D2FF, "Main menu", choices.livingWorld() ? "Living world" : "Classic", SetupScreen.MENU));
		summaries.add(new Summary("summary_look", "palette", 0xBF5AF2, 0x5E5CE6, "Look", look, SetupScreen.LOOK));
		// where and what the player plays only with an add-on that asks it
		if (SetupGroup.world() != null) {
			summaries.add(new Summary("summary_play", "star", 0x30D158, 0x0A84FF, "How you play", play(choices), SetupScreen.PLAY));
		}
		summaries.add(new Summary("summary_features", "list", 0x64D2FF, 0x0A84FF, "Features", choices.countOn() + " turned on", SetupScreen.PLAY));
		summaries.add(new Summary("summary_music", "note", 0xFF375F, 0xBF5AF2, "Music", MusicStep.summary(), SetupScreen.MUSIC));
		summaries.add(new Summary("summary_startup", "sparkle", 0xFF9F0A, 0xFF375F, "Startup", choices.startupAnimation() ? "Animation on" : "Off", SetupScreen.MENU));
		return summaries;
	}

	/** @return where and what the player plays, short: the world and its activity, or how many activities */
	private static String play(SetupChoices choices) {
		if (!choices.inWorld()) {
			return "Anywhere else";
		}
		List<SetupGroup> picks = choices.picks();
		String world = SetupGroup.world() == null ? "" : SetupGroup.world().shortName();
		return switch (picks.size()) {
			case 0 -> world;
			case 1 -> world + " · " + picks.getFirst().shortName();
			default -> world + " · " + picks.size() + " activities";
		};
	}

	private void drawSummary(SetupKit kit, int x, int y, int width, Summary summary) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		float hover = kit.area(summary.key(), x, y, width, CARD_HEIGHT, SetupKit.CARD_RADIUS, () -> screen.go(summary.step()));
		kit.card(x, y, width, CARD_HEIGHT, 0f, hover);
		kit.tile(summary.icon(), x + 9, y + (CARD_HEIGHT - CARD_TILE) / 2, CARD_TILE, summary.light(), summary.dark());
		String change = "Change";
		float changeWidth = ui.widthScaled(change, SetupKit.SMALL);
		ui.textAtScaled(change, x + width - 9 - changeWidth, y + CARD_HEIGHT / 2f - 3.5f, SetupKit.SMALL,
			Ui.mix(palette.textSecondary(), ui.accent, hover));
		int textX = x + 9 + CARD_TILE + 8;
		float room = x + width - 9 - changeWidth - 8 - textX;
		ui.textAtScaled(summary.label(), textX, y + 7, SetupKit.TINY, Ui.withOpacity(palette.textSecondary(), 0.75f));
		ui.textAtScaledBold(kit.fit(summary.value(), room, SetupKit.BODY), textX, y + 17, SetupKit.BODY, palette.text());
	}
}
