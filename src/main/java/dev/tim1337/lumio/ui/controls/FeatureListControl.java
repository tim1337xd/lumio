package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * The features of one page group as rows on one card: the feature icon on a tile in its color (faded while the feature is off), the name, a
 * summary of what it does (wrapped onto a second line where the row is narrow, never cut), the switch, and a chevron when it has settings. Clicking a row opens its settings next to the menu, or
 * toggles it if it has none; the row of the open feature is marked.
 */
public class FeatureListControl extends Control {
	private static final int ROW_HEIGHT = 32;
	/** A summary's second line, and the most lines it takes. */
	private static final int SUMMARY_LINE = 10;
	private static final int SUMMARY_LINES = 2;
	private static final int PAD = 8;
	private static final int TILE = 20;
	private static final int TILE_ICON = 13;
	private static final int TEXT_GAP = 8;
	/** Room for the chevron at the end of a row. */
	private static final int CHEVRON_ROOM = 14;
	/** Width of the accent bar at the left edge of the row whose settings are open. */
	private static final int SELECTED_BAR = 2;
	/** Opacity of the tile of a feature that is off. */
	private static final float OFF_TILE = 0.55f;
	/** Extra room around a switch that still counts as clicking it. */
	private static final int SWITCH_SLOP = 4;

	private final List<Feature> features;
	private final Runnable onChanged;
	/** Whether each feature has settings to open; settings do not appear or disappear while the menu is open. */
	private final boolean[] editable;
	private final Spring[] switches;
	/** How far each row shows its feature as on, following the switch so the tile fades rather than jumps. */
	private final float[] lit;
	/** Whether the list sits inside another card and draws no card of its own. */
	private boolean embedded;
	/** Every row's summary lines, its top below the list's top and its height, for the width they were made for. */
	private final List<List<String>> summaries = new ArrayList<>();
	private int[] rowTops = new int[0];
	private int[] rowHeights = new int[0];
	private int laidOutFor = -1;

	public FeatureListControl(List<Feature> features, Runnable onChanged) {
		this.features = features;
		this.onChanged = onChanged;
		editable = new boolean[features.size()];
		switches = new Spring[features.size()];
		lit = new float[features.size()];
		for (int i = 0; i < features.size(); i++) {
			Feature feature = features.get(i);
			editable[i] = editable(feature);
			switches[i] = Spring.bouncy(Spring.DEFAULT);
			switches[i].snap(feature.isAnyOn() ? 1f : 0f);
			lit[i] = feature.isAnyOn() ? 1f : 0f;
		}
	}

	/** Leaves out the card around the rows, for a list inside another card, under its header row. */
	public FeatureListControl embedded() {
		embedded = true;
		return this;
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
		if (width != laidOutFor || rowTops.length != features.size()) {
			laidOutFor = width;
			summaries.clear();
			rowTops = new int[features.size()];
			rowHeights = new int[features.size()];
			int top = 0;
			for (int i = 0; i < features.size(); i++) {
				Feature feature = features.get(i);
				int textRight = (feature.isSwitchable() ? width - PAD - CHEVRON_ROOM - SwitchControl.TRACK_WIDTH : width - PAD - CHEVRON_ROOM) - TEXT_GAP;
				int room = Math.max(30, textRight - (PAD + TILE + TEXT_GAP));
				List<String> lines = ui.wrapScaled(feature.summary(), room, TextScale.SMALL, SUMMARY_LINES);
				summaries.add(lines);
				rowTops[i] = top;
				rowHeights[i] = ROW_HEIGHT + Math.max(0, lines.size() - 1) * SUMMARY_LINE;
				top += rowHeights[i];
			}
		}
		return rowTops.length == 0 ? 0 : rowTops[rowTops.length - 1] + rowHeights[rowHeights.length - 1];
	}

	/** @return whether the feature has settings to open in the settings pane: anything besides its switch, or child features */
	public static boolean editable(Feature feature) {
		if (!feature.children().isEmpty() || feature.ownWindow()) {
			return true;
		}
		for (Setting<?> setting : feature.settings()) {
			if (!setting.key().equals("enabled") && setting.isVisible() && !setting.isEditorOnly()) {
				return true;
			}
		}
		return false;
	}

	private int switchX() {
		return x + width - PAD - CHEVRON_ROOM - SwitchControl.TRACK_WIDTH;
	}

	private boolean overSwitch(int row, double mouseX, double mouseY) {
		int top = y + rowTops[row] + (rowHeights[row] - SwitchControl.TRACK_HEIGHT) / 2;
		return features.get(row).isSwitchable() && mouseX >= switchX() - SWITCH_SLOP && mouseX < switchX() + SwitchControl.TRACK_WIDTH + SWITCH_SLOP
			&& mouseY >= top - SWITCH_SLOP && mouseY < top + SwitchControl.TRACK_HEIGHT + SWITCH_SLOP;
	}

	/** @return the row under the mouse, or -1 */
	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
			return -1;
		}
		for (int i = rowTops.length - 1; i >= 0; i--) {
			if (mouseY >= y + rowTops[i]) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int cardHeight = height;
		if (!embedded) {
			ui.rounded(x, y, width, cardHeight, Radius.LG, palette.card());
			ui.ring(x, y, width, cardHeight, Radius.LG, 1, palette.cardBorder());
		}
		Feature open = MacScreen.openFeature();
		int hovered = rowAt(mouseX, mouseY);
		for (int i = 0; i < features.size(); i++) {
			drawRow(ui, i, open, hovered == i, mouseX, mouseY);
		}
	}

	private void drawRow(Ui ui, int i, Feature open, boolean over, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		Feature feature = features.get(i);
		int top = y + rowTops[i];
		int rowHeight = rowHeights[i];
		int middle = top + rowHeight / 2;
		boolean selected = feature == open;
		boolean onSwitch = over && overSwitch(i, mouseX, mouseY);
		float hover = hover(ui, i, over && !onSwitch);
		if (selected || hover > 0f) {
			rowBackground(ui, i, selected ? palette.hover() : Ui.withOpacity(palette.hover(), hover * 0.7f));
		}
		if (selected) {
			ui.rounded(x + 1, top + 4, SELECTED_BAR, rowHeight - 8, Radius.XXS, ui.accent);
		}
		if (i > 0) {
			// separator starting under the name, like phone lists
			int lineX = x + PAD + TILE + TEXT_GAP;
			ui.fill(lineX, top, x + width - lineX, 1, Ui.withOpacity(palette.cardBorder(), 0.8f));
		}

		boolean on = feature.isAnyOn();
		lit[i] = ui.approach(lit[i], on ? 1f : 0f, 10f);
		int tileX = x + PAD;
		ui.iconTile(feature.iconName(), tileX, middle - TILE / 2, TILE, TILE_ICON, Radius.SM, Ui.withOpacity(0xFF000000 | feature.iconRgb(), OFF_TILE + (1f - OFF_TILE) * lit[i]),
			Ui.withOpacity(0xFFFFFFFF, 0.75f + 0.25f * lit[i]));

		int textX = tileX + TILE + TEXT_GAP;
		int textRight = (feature.isSwitchable() ? switchX() : x + width - PAD - CHEVRON_ROOM) - TEXT_GAP;
		int room = Math.max(30, textRight - textX);
		ui.textAtScaledBold(ui.fit(feature.nameText(), room - 4), textX, top + 6, 1f, palette.text());
		List<String> lines = summaries.get(i);
		for (int k = 0; k < lines.size(); k++) {
			ui.textAtScaled(lines.get(k), textX, top + 18 + k * SUMMARY_LINE, TextScale.SMALL, Ui.withOpacity(palette.textSecondary(), 0.95f));
		}

		if (feature.isSwitchable()) {
			float position = switches[i].follow(ui, on ? 1f : 0f);
			SwitchControl.draw(ui, switchX(), middle - SwitchControl.TRACK_HEIGHT / 2, position, onSwitch ? 1f : 0f, 0f);
		}
		if (editable[i]) {
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(x + width - PAD - 4f, middle);
			pose.rotate((float) (-Math.PI / 2.0));
			ui.icon("chevron", -4, -4, 8, Ui.withOpacity(palette.textSecondary(), 0.8f));
			pose.popMatrix();
		}
	}

	/** Fills a row edge to edge, following the card's rounded corners on the first and last row. */
	private void rowBackground(Ui ui, int i, int color) {
		int top = y + rowTops[i];
		int rowHeight = rowHeights[i];
		// in a card under its header row the first row has no corner of the card to follow, and the row reaches the card's edge
		boolean first = i == 0 && !embedded;
		boolean last = i == features.size() - 1;
		int rowWidth = embedded ? width - 1 : width - 2;
		if (first && last) {
			ui.rounded(x + 1, top + 1, rowWidth, rowHeight - 2, Radius.LG - 1, color);
		} else if (first || last) {
			ui.graphics.enableScissor(x + 1, top, x + 1 + rowWidth, top + rowHeight);
			ui.rounded(x + 1, first ? top + 1 : top - rowHeight, rowWidth, rowHeight * 2 - 1, Radius.LG - 1, color);
			ui.graphics.disableScissor();
		} else {
			ui.fill(x + 1, top, rowWidth, rowHeight, color);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int row = rowAt(mouseX, mouseY);
		if (button != 0 || row < 0) {
			return false;
		}
		Feature feature = features.get(row);
		if (overSwitch(row, mouseX, mouseY) || !editable[row] && feature.isSwitchable()) {
			boolean now = feature.toggle();
			UiSounds.toggle(now);
			onChanged.run();
		} else if (editable[row]) {
			UiSounds.click();
			MacScreen.openEdit(feature);
		}
		return true;
	}
}
