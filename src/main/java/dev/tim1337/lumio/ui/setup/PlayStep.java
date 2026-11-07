package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Step three: with an add-on that offers a world, where the player plays (that world or anywhere else) and what they do there, and on the right
 * "Turned on for you" lists the features this gives, by group, each with a switch to change it; "Show all" opens the whole list over the window.
 * Without such an add-on there is nothing to ask, and the list takes the whole step. Nothing is applied before the setup ends. For an add-on installed
 * later, this step alone shows its part (see {@link SetupScreen#addonPart}).
 */
final class PlayStep implements SetupStep {
	private static final int GAP = 16;
	/** The left column's share of the width, as in the mockup (1.4 to 1). */
	private static final float LEFT_SHARE = 1.4f / 2.4f;
	private static final int CARD_GAP = 8;
	private static final int WHERE_HEIGHT = 42;
	private static final int WHERE_TILE = 26;
	private static final int CELL_GAP = 6;
	private static final int CELL_HEIGHT = 56;
	private static final int CELL_TILE = 23;
	private static final int COLUMNS = 3;
	private static final int LABEL_GAP = 12;
	private static final int SECTION_GAP = 16;
	private static final int GROUP_ROW = 15;
	private static final int FEATURE_ROW = 28;
	private static final int FEATURE_TILE = 17;
	private static final int SHOW_ALL_ROW = 24;
	/** How many features of a group the column shows before "Show all". */
	private static final int FIRST_ROWS = 2;
	private static final int SCROLL_STEP = 24;

	private final SetupScreen screen;
	private float listScroll;
	private float listScrollTarget;
	private final Spring scrollSpring = Spring.smooth(Spring.SNAPPY);

	PlayStep(SetupScreen screen) {
		this.screen = screen;
	}

	@Override
	public String name() {
		return "How you play";
	}

	@Override
	public void draw(SetupKit kit, int x, int y, int width, int height, boolean wide) {
		SetupChoices choices = screen.choices;
		if (SetupGroup.world() == null) {
			// nothing to choose from: no column of choices, the features take the whole step
			int below = kit.heading("What is turned on", "The features we turn on for you. You can change everything later.", x, y, width) + SECTION_GAP;
			if (wide) {
				drawColumn(kit, x, below, width, y + height - below);
			} else {
				drawCount(kit, choices, x, below);
			}
			return;
		}
		int left = wide ? Math.round((width - GAP) * LEFT_SHARE) : width;
		int top = choices.addonOnly()
			? kit.heading(SetupGroup.provider().worldTitle(), SetupGroup.provider().welcomeLine() + " Pick what you do, or skip it for now.", x, y, left) + SECTION_GAP
			: kit.heading("How do you play?", "We turn on what fits you. You can change everything later.", x, y, left) + SECTION_GAP;
		top = drawWhere(kit, x, top, left) + SECTION_GAP;
		if (choices.inWorld()) {
			top = drawActivities(kit, x, top, left) + SECTION_GAP;
		}
		if (wide) {
			drawColumn(kit, x + left + GAP, y, width - left - GAP, height);
		} else {
			drawCount(kit, choices, x, top);
		}
	}

	/** Without the column: the count, and the whole list behind "Show all". */
	private void drawCount(SetupKit kit, SetupChoices choices, int x, int top) {
		Ui ui = kit.ui;
		ui.textAtScaledBold(choices.countOn() + " features turned on", x, top, SetupKit.BODY, ui.palette.text());
		int countWidth = Math.round(ui.boldWidthScaled(choices.countOn() + " features turned on", SetupKit.BODY));
		kit.link("show_all", "Show all", x + countWidth + 10, top, SetupKit.BODY, screen::openList);
	}

	private int drawWhere(SetupKit kit, int x, int y, int width) {
		SetupChoices choices = screen.choices;
		kit.label("WHERE", x, y);
		int top = y + LABEL_GAP;
		int cardWidth = (width - CARD_GAP) / 2;
		SetupGroup world = SetupGroup.world();
		drawWhereCard(kit, "where_world", x, top, cardWidth, world.icon(), world.light(), world.dark(), SetupGroup.provider().worldTitle(), world.note(),
			choices.inWorld(), () -> choices.setInWorld(true));
		drawWhereCard(kit, "where_other", x + cardWidth + CARD_GAP, top, cardWidth, "cube", 0x64D2FF, 0x5E5CE6, "Anywhere else",
			"Other servers or solo", !choices.inWorld(), () -> choices.setInWorld(false));
		return top + WHERE_HEIGHT;
	}

	private static void drawWhereCard(SetupKit kit, String key, int x, int y, int width, String icon, int light, int dark, String name, String text,
									  boolean on, Runnable action) {
		Ui ui = kit.ui;
		float hover = kit.area(key, x, y, width, WHERE_HEIGHT, SetupKit.CARD_RADIUS, action);
		float chosen = kit.follow(key + "_on", on ? 1f : 0f);
		kit.card(x, y, width, WHERE_HEIGHT, chosen, hover);
		kit.tile(icon, x + 9, y + (WHERE_HEIGHT - WHERE_TILE) / 2, WHERE_TILE, light, dark);
		int textX = x + 9 + WHERE_TILE + 8;
		int room = width - (textX - x) - 9 - 13 - 6;
		ui.textAtScaledBold(kit.fit(name, room, SetupKit.BODY), textX, y + 11, SetupKit.BODY, ui.palette.text());
		ui.textAtScaled(kit.fit(text, room, SetupKit.SMALL), textX, y + 23, SetupKit.SMALL, ui.palette.textSecondary());
		kit.radio(x + width - 9 - 13, y + (WHERE_HEIGHT - 13) / 2, 13, chosen);
	}

	/** What the player does in the world: a grid of activities, any number of them picked. */
	private int drawActivities(SetupKit kit, int x, int y, int width) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		SetupChoices choices = screen.choices;
		kit.label("WHAT YOU DO ON " + SetupGroup.world().shortName().toUpperCase(Locale.ROOT), x, y);
		String any = "Pick any";
		ui.textAtScaled(any, x + width - ui.widthScaled(any, SetupKit.SMALL), y - 0.5f, SetupKit.SMALL, Ui.withOpacity(palette.textSecondary(), 0.7f));
		int top = y + LABEL_GAP;
		int cellWidth = (width - (COLUMNS - 1) * CELL_GAP) / COLUMNS;
		for (int i = 0; i < SetupGroup.activities().size(); i++) {
			SetupGroup group = SetupGroup.activities().get(i);
			int cellX = x + (i % COLUMNS) * (cellWidth + CELL_GAP);
			int cellY = top + (i / COLUMNS) * (CELL_HEIGHT + CELL_GAP);
			float hover = kit.area("activity_" + group.id(), cellX, cellY, cellWidth, CELL_HEIGHT, SetupKit.CARD_RADIUS, () -> choices.togglePick(group));
			float picked = kit.follow("activity_on_" + group.id(), choices.picked(group) ? 1f : 0f);
			// a picked activity is tinted with the accent, with a thin accent ring and a check
			ui.rounded(cellX, cellY, cellWidth, CELL_HEIGHT, SetupKit.CARD_RADIUS,
				Ui.mix(Ui.withOpacity(palette.text(), 0.05f + 0.03f * hover), Ui.withOpacity(ui.accent, 0.2f), picked));
			ui.ring(cellX, cellY, cellWidth, CELL_HEIGHT, SetupKit.CARD_RADIUS, 1,
				Ui.mix(Ui.withOpacity(palette.text(), 0.09f + 0.06f * hover), ui.accent, picked));
			if (picked > 0.01f) {
				kit.radio(cellX + cellWidth - 5 - 10, cellY + 5, 10, picked);
			}
			kit.tile(group.icon(), cellX + (cellWidth - CELL_TILE) / 2, cellY + 9, CELL_TILE, group.light(), group.dark());
			String name = kit.fit(group.displayName(), cellWidth - 8, SetupKit.BODY);
			if (picked > 0.5f) {
				kit.centered(name, cellX + cellWidth / 2f, cellY + 43, SetupKit.BODY, true, palette.text());
			} else {
				kit.centered(name, cellX + cellWidth / 2f, cellY + 43, SetupKit.BODY, false, palette.text());
			}
		}
		int rows = (SetupGroup.activities().size() + COLUMNS - 1) / COLUMNS;
		return top + rows * CELL_HEIGHT + (rows - 1) * CELL_GAP;
	}

	/** "Turned on for you": the first features of every group, with "Show all" for the rest. */
	private void drawColumn(SetupKit kit, int x, int y, int width, int height) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		SetupChoices choices = screen.choices;
		kit.label("TURNED ON FOR YOU", x, y);
		String count = choices.countOn() + " features";
		ui.textAtScaledBold(count, x + width - ui.boldWidthScaled(count, SetupKit.SMALL), y - 0.5f, SetupKit.SMALL, palette.text());
		int boxY = y + LABEL_GAP;
		int boxHeight = height - LABEL_GAP;
		kit.box(x, boxY, width, boxHeight, SetupKit.CARD_RADIUS + 2);
		int bottom = boxY + boxHeight - SHOW_ALL_ROW;
		int rowY = boxY + 4;
		if (choices.shownGroups().isEmpty()) {
			kit.centered("Nothing is turned on.", x + width / 2f, boxY + (bottom - boxY) / 2f, SetupKit.BODY, false, palette.textSecondary());
		}
		// a single group has the box for itself, without a title and as far as its rows fit
		boolean alone = SetupGroup.world() == null;
		for (SetupGroup group : choices.shownGroups()) {
			if (rowY + GROUP_ROW + FEATURE_ROW > bottom) {
				break;
			}
			if (!alone) {
				rowY = drawGroupRow(kit, group, x, rowY, width);
			}
			int shown = 0;
			for (Feature feature : group.features()) {
				if (!alone && shown == FIRST_ROWS || rowY + FEATURE_ROW > bottom) {
					break;
				}
				drawFeatureRow(kit, group, feature, x, rowY, width);
				rowY += FEATURE_ROW;
				shown++;
			}
		}
		ui.fill(x + 1, bottom, width - 2, 1, Ui.withOpacity(palette.text(), 0.09f));
		String all = "Show all " + choices.countListed() + " features";
		float hover = kit.area("show_all", x + 1, bottom + 1, width - 2, SHOW_ALL_ROW - 1, Radius.SM, screen::openList);
		kit.centered(all, x + width / 2f, bottom + SHOW_ALL_ROW / 2f, SetupKit.BODY * 0.95f, true, Ui.mix(palette.text(), ui.accent, hover));
	}

	private int drawGroupRow(SetupKit kit, SetupGroup group, int x, int y, int width) {
		Ui ui = kit.ui;
		int quiet = Ui.withOpacity(ui.palette.textSecondary(), 0.75f);
		ui.sectionLabel(group.displayName().toUpperCase(java.util.Locale.ROOT), x + 8, y + 5, quiet);
		String size = String.valueOf(group.features().size());
		ui.textAtScaledBold(size, x + width - 8 - ui.boldWidthScaled(size, SetupKit.TINY), y + 5, SetupKit.TINY, quiet);
		return y + GROUP_ROW;
	}

	/** A feature with its icon, name, short description and the switch that changes it. */
	private void drawFeatureRow(SetupKit kit, SetupGroup group, Feature feature, int x, int y, int width) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		SetupChoices choices = screen.choices;
		boolean on = choices.wanted(feature, group);
		float hover = kit.area("feature_" + feature.id(), x + 2, y, width - 4, FEATURE_ROW, Radius.MD, () -> choices.toggle(feature, group));
		if (hover > 0.01f) {
			ui.rounded(x + 2, y, width - 4, FEATURE_ROW, Radius.MD, Ui.withOpacity(palette.text(), 0.05f * hover));
		}
		int rgb = 0xFF000000 | feature.iconRgb();
		kit.tile(feature.iconName(), x + 8, y + (FEATURE_ROW - FEATURE_TILE) / 2, FEATURE_TILE, Ui.mix(rgb, 0xFFFFFFFF, 0.15f) & 0xFFFFFF,
			Ui.mix(rgb, 0xFF000000, 0.2f) & 0xFFFFFF);
		int textX = x + 8 + FEATURE_TILE + 7;
		int room = width - (textX - x) - 8 - 20 - 6;
		ui.textAtScaled(kit.fit(feature.nameText(), room, SetupKit.BODY * 0.95f), textX, y + 6, SetupKit.BODY * 0.95f, palette.text());
		ui.textAtScaled(kit.fit(feature.summary(), room, SetupKit.TINY), textX, y + 17, SetupKit.TINY, palette.textSecondary());
		kit.smallSwitch(x + width - 8 - 20, y + (FEATURE_ROW - 12) / 2, 20, 12, kit.follow("feature_on_" + feature.id(), on ? 1f : 0f));
	}

	/** The whole list of "Show all", over the window's content: every group with all its features, scrolled with the wheel. */
	void drawFullList(SetupKit kit, int x, int y, int width, int height) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		SetupChoices choices = screen.choices;
		kit.label("TURNED ON FOR YOU", x, y);
		String count = choices.countOn() + " of " + choices.countListed() + " features";
		String done = "Done";
		float doneWidth = ui.widthScaled(done, SetupKit.BODY);
		float countWidth = ui.boldWidthScaled(count, SetupKit.SMALL);
		ui.textAtScaledBold(count, x + width - doneWidth - 14 - countWidth, y - 0.5f, SetupKit.SMALL, palette.text());
		kit.link("list_done", done, x + width - doneWidth, y - 1, SetupKit.BODY, screen::closeList);
		int boxY = y + LABEL_GAP + 2;
		int boxHeight = height - LABEL_GAP - 2;
		kit.box(x, boxY, width, boxHeight, SetupKit.CARD_RADIUS + 2);
		int content = 4;
		for (SetupGroup group : choices.shownGroups()) {
			content += GROUP_ROW + group.features().size() * FEATURE_ROW;
		}
		float max = Math.max(0, content + 4 - boxHeight);
		listScrollTarget = Mth.clamp(listScrollTarget, 0f, max);
		listScroll = scrollSpring.follow(ui, listScrollTarget);
		ui.graphics.enableScissor(x + 1, boxY + 1, x + width - 1, boxY + boxHeight - 1);
		int rowY = boxY + 4 - Math.round(listScroll);
		for (SetupGroup group : choices.shownGroups()) {
			if (rowY + GROUP_ROW > boxY && rowY < boxY + boxHeight) {
				drawGroupRow(kit, group, x, rowY, width);
			}
			rowY += GROUP_ROW;
			for (Feature feature : group.features()) {
				// rows out of view are neither drawn nor clickable
				if (rowY + FEATURE_ROW > boxY && rowY < boxY + boxHeight) {
					drawFeatureRow(kit, group, feature, x, rowY, width);
				}
				rowY += FEATURE_ROW;
			}
		}
		ui.graphics.disableScissor();
		if (max > 0) {
			int view = boxHeight - 8;
			int bar = Math.max(16, Math.round(view * view / (float) content));
			int barY = boxY + 4 + Math.round((view - bar) * (listScroll / max));
			ui.rounded(x + width - 6, barY, 3, bar, Radius.XXS, Ui.withOpacity(palette.textSecondary(), 0.6f));
		}
	}

	void scrollList(double amount) {
		listScrollTarget -= (float) amount * SCROLL_STEP;
	}

	@Override
	public void shown() {
		listScrollTarget = 0;
	}
}
