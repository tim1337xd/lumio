package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.hud.HudSimulation;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The element list of the HUD editor, a panel on the right: a search field, how many elements are shown and hidden, and every element
 * grouped (Bars, Panels, SkyBlock, Music) with its color, its name and an eye to show or hide it. Clicking a row selects the element and lets
 * it pulse on screen. While an activity is simulated, its elements are marked and the list can show only them. Whether the panel is open
 * is kept while the game runs.
 */
final class HudEditList {
	private static final int WIDTH = 200;
	/** A row of what is switched on but not going on here. */
	private static final String NOT_ACTIVE = "not active here";
	private static final float NOT_ACTIVE_OPACITY = 0.7f;
	private int countNotActive = -1;
	private static final int PAD = 10;
	private static final int ROW = 20;
	private static final int HEADER = 18;
	private static final int FIELD = 22;
	private static final int TOP_GAP = 8;
	private static final int BOTTOM_ROOM = 40;
	private static final int MAX_SEARCH = 30;
	private static final Set<Feature> BARS = Set.of(ModFeatures.HOTBAR, ModFeatures.STATUS, ModFeatures.ACTION_BAR, ModFeatures.INFO_BAR);

	// clickable parts
	private static final int CLOSE = 1;
	private static final int SEARCH = 2;
	private static final int ONLY_ACTIVITY = 3;
	private static final int RESET_ALL = 4;
	private static final String RESET_LABEL = "Reset all";
	private static final int ROW_ID = 100;
	private static final int EYE_ID = 1000;

	private static boolean open;
	private static boolean onlyActivity;

	private final HudEditScreen screen;
	private final HudEditHits hits = new HudEditHits();
	private final Spring shown = Spring.smooth(Spring.SNAPPY);
	/** The elements in their groups' order, with their group and their name in lower case for the search; built once. */
	private final List<EditableHud> rows = new ArrayList<>();
	private final List<String> rowGroups = new ArrayList<>();
	private final List<String> lowerNames = new ArrayList<>();
	private float[] rowHover = new float[0];
	private String search = "";
	private boolean searchFocused;
	private double scroll;
	private int contentHeight;
	private int x;
	private int y;
	private int height;
	private String countText = "";
	private int countShown = -1;
	private int countHidden = -1;

	HudEditList(HudEditScreen screen) {
		this.screen = screen;
	}

	private void build() {
		if (!rows.isEmpty()) {
			return;
		}
		for (String group : groups()) {
			for (EditableHud element : screen.elements) {
				if (groupOf(element).equals(group)) {
					rows.add(element);
					rowGroups.add(group);
					lowerNames.add(element.hudName().toLowerCase(Locale.ROOT));
				}
			}
		}
		rowHover = new float[rows.size()];
	}

	/** @return the groups of the list in order: the bars, the panels, the groups of the add-ons (such as SkyBlock), and the music */
	private static List<String> groups() {
		List<String> groups = new ArrayList<>(List.of("Bars", "Panels"));
		for (String group : Addons.huds().values()) {
			if (!groups.contains(group)) {
				groups.add(group);
			}
		}
		groups.add("Music");
		return groups;
	}

	private static String groupOf(EditableHud element) {
		Feature feature = element.hudFeature();
		if (feature == ModFeatures.MUSIC) {
			return "Music";
		}
		if (BARS.contains(feature)) {
			return "Bars";
		}
		String addonGroup = Addons.huds().get(element);
		return addonGroup != null ? addonGroup : "Panels";
	}

	boolean isOpen() {
		return open;
	}

	void toggle() {
		open = !open;
		searchFocused = false;
		UiSounds.play(open ? UiSounds.Cue.OPEN : UiSounds.Cue.CLOSE);
	}

	void close() {
		open = false;
		searchFocused = false;
	}

	/** @return the left edge of the panel, or the screen's right edge while it is closed; the card keeps away from it */
	int left() {
		return open ? x : screen.width;
	}

	boolean contains(double mouseX, double mouseY) {
		return open && mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
	}

	private boolean matches(int row) {
		if (!search.isEmpty() && !lowerNames.get(row).contains(search.toLowerCase(Locale.ROOT))) {
			return false;
		}
		return !onlyActivity || !HudSimulation.active() || HudSimulation.simulates(rows.get(row).hudFeature());
	}

	void draw(Ui ui, int mouseX, int mouseY) {
		hits.clear();
		build();
		float amount = Mth.clamp(shown.follow(ui, open ? 1f : 0f), 0f, 1f);
		if (amount <= 0.01f) {
			return;
		}
		x = screen.width - WIDTH - 8 + Math.round((1f - amount) * 24f);
		y = screen.toolbar.bottom() + TOP_GAP;
		height = Math.max(80, screen.height - y - BOTTOM_ROOM);
		Ui.setAlpha(amount);
		Palette palette = ui.palette;
		HudEditScreen.surface(ui, x, y, WIDTH, height, Radius.LG, 1f);
		int left = x + PAD;
		int right = x + WIDTH - PAD;
		int top = y + PAD;
		ui.textAtScaledBold("Elements", left, top + 2, 1f, palette.text());
		ui.icon("close", right - 9, top + 1, 9, palette.textSecondary());
		hits.add(CLOSE, right - 12, top - 2, 14, 14);
		// resetting the whole layout lives here, next to the list of everything it resets
		int resetWidth = ui.width(RESET_LABEL);
		int resetX = right - 18 - resetWidth;
		boolean overReset = mouseX >= resetX - 3 && mouseX < resetX + resetWidth + 3 && mouseY >= top - 2 && mouseY < top + 12;
		ui.text(RESET_LABEL, resetX, top + 1, overReset ? palette.text() : palette.textSecondary());
		hits.add(RESET_ALL, resetX - 3, top - 2, resetWidth + 6, 14);
		top += 16;

		// the search field
		ui.rounded(left, top, right - left, FIELD, Radius.SM, Ui.withOpacity(palette.hover(), searchFocused ? 1f : 0.7f));
		if (searchFocused) {
			ui.ring(left, top, right - left, FIELD, Radius.SM, 1, ui.accent);
		}
		ui.icon("search", left + 6, top + FIELD / 2 - 4, 9, palette.textSecondary());
		String shownText = search.isEmpty() && !searchFocused ? "Find an element" : search;
		ui.textMiddle(ui.fit(shownText, right - left - 26), left + 20, top + FIELD / 2, search.isEmpty() ? palette.textSecondary() : palette.text());
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(left + 20 + ui.width(search) + 1, top + 5, 1, FIELD - 10, palette.text());
		}
		hits.add(SEARCH, left, top, right - left, FIELD);
		top += FIELD + 6;

		// how many are shown and hidden
		int shownCount = 0;
		int hiddenCount = 0;
		for (EditableHud element : rows) {
			shownCount += element.hudShown() ? 1 : 0;
			hiddenCount += element.hudEnabled() ? 0 : 1;
		}
		int notActive = rows.size() - shownCount - hiddenCount;
		if (shownCount != countShown || hiddenCount != countHidden || notActive != countNotActive) {
			countShown = shownCount;
			countHidden = hiddenCount;
			countNotActive = notActive;
			countText = shownCount + " shown  ·  " + notActive + " not active  ·  " + hiddenCount + " hidden";
		}
		ui.textAtScaled(countText, left, top, TextScale.SMALL, palette.textSecondary());
		top += 14;
		if (HudSimulation.active()) {
			// the simulated activity's elements only
			ui.textMiddle("Only this activity", left, top + 6, palette.text());
			ui.rounded(right - 22, top + 1, 22, 11, Radius.pill(11), onlyActivity ? ui.accent : palette.trackOff());
			ui.circle(onlyActivity ? right - 6 : right - 17, top + 6, 4, palette.knob());
			hits.add(ONLY_ACTIVITY, left, top - 2, right - left, 16);
			top += 18;
		}

		// the rows, in their groups, scrolled
		int viewTop = top;
		int viewBottom = y + height - PAD;
		ui.graphics.enableScissor(x, viewTop, x + WIDTH, viewBottom);
		int rowY = viewTop - (int) scroll;
		String group = "";
		for (int i = 0; i < rows.size(); i++) {
			if (!matches(i)) {
				continue;
			}
			if (!rowGroups.get(i).equals(group)) {
				group = rowGroups.get(i);
				ui.sectionLabel(group.toUpperCase(Locale.ROOT), left, rowY + 5, palette.textSecondary());
				rowY += HEADER;
			}
			drawRow(ui, i, left, right, rowY, viewTop, viewBottom, mouseX, mouseY);
			rowY += ROW;
		}
		ui.graphics.disableScissor();
		contentHeight = rowY + (int) scroll - viewTop;
		scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (viewBottom - viewTop)));
		Ui.setAlpha(1f);
	}

	private void drawRow(Ui ui, int i, int left, int right, int rowY, int viewTop, int viewBottom, int mouseX, int mouseY) {
		EditableHud element = rows.get(i);
		Palette palette = ui.palette;
		boolean visible = element.hudShown();
		boolean switchedOn = element.hudEnabled();
		boolean notActive = switchedOn && !visible;
		boolean inView = mouseY >= viewTop && mouseY < viewBottom;
		boolean over = inView && mouseX >= left - 4 && mouseX < right + 4 && mouseY >= rowY && mouseY < rowY + ROW;
		rowHover[i] = ui.approach(rowHover[i], over ? 1f : 0f, Ui.HOVER_RATE);
		if (screen.isPicked(element)) {
			ui.rounded(left - 4, rowY, right - left + 8, ROW, Radius.SM, Ui.withOpacity(ui.accent, 0.25f));
		} else if (rowHover[i] > 0f) {
			ui.rounded(left - 4, rowY, right - left + 8, ROW, Radius.SM, Ui.withOpacity(palette.hover(), rowHover[i]));
		}
		float dim = visible ? 1f : 0.45f;
		ui.circle(left + 3, rowY + ROW / 2, 3, Ui.withOpacity(0xFF000000 | element.hudIconRgb(), dim));
		boolean simulated = HudSimulation.active() && HudSimulation.simulates(element.hudFeature());
		int nameRight = right - 18 - (simulated ? 10 : 0);
		int noteWidth = notActive ? Math.round(ui.widthScaled(NOT_ACTIVE, TextScale.SMALL)) + 6 : 0;
		ui.textMiddle(ui.fit(element.hudName(), nameRight - left - 12 - noteWidth), left + 12, rowY + ROW / 2, Ui.withOpacity(palette.text(), dim));
		if (notActive) {
			ui.textAtScaled(NOT_ACTIVE, nameRight - noteWidth + 6, rowY + ROW / 2f - 3.5f, TextScale.SMALL, Ui.withOpacity(palette.textSecondary(), NOT_ACTIVE_OPACITY));
		}
		if (simulated) {
			// marks the elements of the simulated activity
			ui.circle(right - 22, rowY + ROW / 2, 2, ui.accent);
		}
		ui.icon("eye", right - 12, rowY + ROW / 2 - 6, 12, switchedOn ? palette.textSecondary() : Ui.withOpacity(palette.textSecondary(), 0.35f));
		if (!switchedOn) {
			ui.fill(right - 13, rowY + ROW / 2, 14, 1, palette.textSecondary());
		}
		if (rowY + ROW > viewTop && rowY < viewBottom) {
			hits.add(ROW_ID + i, left - 4, Math.max(rowY, viewTop), right - left - 12, Math.min(rowY + ROW, viewBottom) - Math.max(rowY, viewTop));
			hits.add(EYE_ID + i, right - 14, Math.max(rowY, viewTop), 18, Math.min(rowY + ROW, viewBottom) - Math.max(rowY, viewTop));
		}
	}

	// ------------------------------------------------------------------------------------------------------------ input

	/** @return whether the click was on the panel */
	boolean click(double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			searchFocused = false;
			return false;
		}
		int id = hits.at(mouseX, mouseY);
		searchFocused = id == SEARCH;
		if (button != 0) {
			return true;
		}
		if (id == CLOSE) {
			close();
			UiSounds.play(UiSounds.Cue.CLOSE);
		} else if (id == RESET_ALL) {
			screen.resetAll();
		} else if (id == ONLY_ACTIVITY) {
			onlyActivity = !onlyActivity;
			UiSounds.toggle(onlyActivity);
		} else if (id >= EYE_ID) {
			screen.toggleShown(rows.get(id - EYE_ID));
		} else if (id >= ROW_ID) {
			EditableHud element = rows.get(id - ROW_ID);
			if (element.hudShown()) {
				if (net.minecraft.client.Minecraft.getInstance().hasControlDown() || net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
					screen.toggleInGroup(element);
				} else {
					screen.pick(element);
				}
				UiSounds.click();
			} else if (element.hudEnabled()) {
				screen.askFor(element);
				UiSounds.click();
			}
		}
		return true;
	}

	boolean drag(double mouseX, double mouseY) {
		return false;
	}

	void release() {
	}

	/** @return whether the wheel scrolled the list */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		scroll = Math.max(0, scroll - amount * ROW * 2);
		return true;
	}

	/** @return whether a typed character went into the search field */
	boolean charTyped(int codepoint) {
		if (!searchFocused || codepoint < 32 || search.length() >= MAX_SEARCH) {
			return searchFocused;
		}
		search += Character.toString(codepoint);
		scroll = 0;
		return true;
	}

	/** @return whether a key went to the search field: backspace, and Escape, which clears it first and then leaves it */
	boolean keyPressed(int key, int modifiers) {
		if (!searchFocused) {
			return false;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			search = search.isEmpty() ? search : search.substring(0, search.length() - 1);
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (!search.isEmpty()) {
				search = "";
			} else {
				searchFocused = false;
			}
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			searchFocused = false;
			return true;
		}
		// other keys (arrows, undo) are not for the field
		return (modifiers & ScreenInput.CONTROL) == 0 && key != InputConstants.KEY_LEFT && key != InputConstants.KEY_RIGHT && key != InputConstants.KEY_UP
			&& key != InputConstants.KEY_DOWN;
	}
}
