package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.menu.FeatureListFeature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.Arrays;
import java.util.List;

/**
 * The pages of a page's groups as tiles in a grid: the group's icon on a tile in its color, its name, how many of its features are on, a dot
 * where the player is, and a chevron. A click opens the page.
 */
public final class PageTilesControl extends Control {
	private static final int TILE_HEIGHT = 44;
	private static final int GAP = 8;
	/** Tiles narrower than this would cut their text: the grid uses fewer columns then. */
	private static final int MIN_TILE_WIDTH = 170;
	private static final int MAX_COLUMNS = 2;
	private static final int ICON_TILE = 26;
	private static final int ICON = 16;
	private static final int PAD = 10;

	private final List<FeatureListFeature> pages;
	/** The text under each name, built again only when the number of features that are on changes. */
	private final String[] counts;
	private final int[] countedOn;

	public PageTilesControl(List<FeatureListFeature> pages) {
		this.pages = pages;
		counts = new String[pages.size()];
		countedOn = new int[pages.size()];
		Arrays.fill(countedOn, -1);
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

	/** The width a tile needs for its name and line without cutting them, from the last measure. */
	private int neededWidth = MIN_TILE_WIDTH;

	private int columns() {
		return Math.max(1, Math.min(MAX_COLUMNS, (width + GAP) / (Math.max(MIN_TILE_WIDTH, neededWidth) + GAP)));
	}

	private int tileWidth() {
		return (width - GAP * (columns() - 1)) / columns();
	}

	@Override
	protected int measure(Ui ui, int width) {
		this.width = width;
		// fewer columns where a tile's name or line would not fit whole
		int text = 0;
		for (int i = 0; i < pages.size(); i++) {
			text = Math.max(text, Math.max(ui.width(pages.get(i).nameText()), Math.round(ui.widthScaled(count(i), TextScale.SMALL))));
		}
		neededWidth = PAD + ICON_TILE + 9 + text + 12 + PAD;
		int rows = (pages.size() + columns() - 1) / columns();
		return rows * TILE_HEIGHT + Math.max(0, rows - 1) * GAP;
	}

	private int tileX(int i) {
		return x + (i % columns()) * (tileWidth() + GAP);
	}

	private int tileY(int i) {
		return y + (i / columns()) * (TILE_HEIGHT + GAP);
	}

	/** @return the text under a page's name: how many of its features there are and how many are on */
	private String count(int i) {
		if (!pages.get(i).isGroupPage()) {
			// a whole page listed under this one (Chat under Extras) is no group of features: its description says what it is
			if (counts[i] == null) {
				counts[i] = pages.get(i).description().getString();
			}
			return counts[i];
		}
		List<Feature> features = pages.get(i).firstGroup();
		int on = 0;
		for (Feature feature : features) {
			if (feature.isAnyOn()) {
				on++;
			}
		}
		if (on != countedOn[i]) {
			countedOn[i] = on;
			counts[i] = (features.size() == 1 ? "1 feature" : features.size() + " features") + "  ·  " + on + " on";
		}
		return counts[i];
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int tileWidth = tileWidth();
		for (int i = 0; i < pages.size(); i++) {
			FeatureListFeature page = pages.get(i);
			int tx = tileX(i);
			int ty = tileY(i);
			boolean over = mouseX >= tx && mouseX < tx + tileWidth && mouseY >= ty && mouseY < ty + TILE_HEIGHT;
			float hover = hover(ui, i, over);
			ui.rounded(tx, ty, tileWidth, TILE_HEIGHT, Radius.LG, palette.card());
			if (hover > 0f) {
				ui.rounded(tx, ty, tileWidth, TILE_HEIGHT, Radius.LG, Ui.withOpacity(palette.hover(), hover));
			}
			ui.ring(tx, ty, tileWidth, TILE_HEIGHT, Radius.LG, 1, Ui.mix(palette.cardBorder(), 0xFF000000 | page.iconRgb(), 0.5f * hover));
			int middle = ty + TILE_HEIGHT / 2;
			int iconX = tx + PAD;
			ui.iconTile(page.iconName(), iconX, middle - ICON_TILE / 2, ICON_TILE, ICON, Radius.SM, 0xFF000000 | page.iconRgb(), 0xFFFFFFFF);
			int textX = iconX + ICON_TILE + 9;
			int room = tx + tileWidth - PAD - 12 - textX;
			String name = ui.fit(page.nameText(), room);
			ui.textAtScaledBold(name, textX, ty + 9, 1f, palette.text());
			ui.textAtScaled(ui.fitScaled(count(i), room, TextScale.SMALL), textX, ty + 25, TextScale.SMALL, palette.textSecondary());
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(tx + tileWidth - PAD - 4f, middle);
			pose.rotate((float) (-Math.PI / 2.0));
			ui.icon("chevron", -4, -4, 8, Ui.withOpacity(palette.textSecondary(), 0.8f));
			pose.popMatrix();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		int tileWidth = tileWidth();
		for (int i = 0; i < pages.size(); i++) {
			int tx = tileX(i);
			int ty = tileY(i);
			if (mouseX >= tx && mouseX < tx + tileWidth && mouseY >= ty && mouseY < ty + TILE_HEIGHT) {
				UiSounds.click();
				MacScreen.showPage(pages.get(i));
				return true;
			}
		}
		return false;
	}
}
