package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the steps of the setup are drawn with: the shared shapes of the mockups (cards that light up when chosen, radio marks, small switches,
 * icon tiles in two colors, buttons) and the areas that react to the mouse and the keyboard. The areas are collected while a frame is drawn and
 * used for the clicks and key presses until the next one; the one focused with Tab or the arrow keys gets an accent ring.
 */
final class SetupKit {
	/** The text sizes of the mockups, in the menu's text scale (14 px there is the menu's regular text). */
	static final float TITLE = TextScale.TITLE;
	static final float BODY = 1f;
	static final float SMALL = TextScale.SMALL;
	static final float TINY = TextScale.SMALL;
	/** A card's radius, and the radius of the small parts in it. */
	static final float CARD_RADIUS = Radius.LG;
	private static final int FOCUS_SPREAD = 2;
	/** The middle of a line's letters below its top, in text units, for centering text on a point. */
	private static final float TEXT_MIDDLE = 4f;
	/** A switch card: its tile, and how faded it is while it is not available. */
	private static final int SWITCH_TILE = 19;
	private static final float UNAVAILABLE_OPACITY = 0.4f;

	/** An area that reacts to the mouse: where it is, its corner radius for the focus ring, and what a click does. */
	private record Area(int x, int y, int width, int height, float radius, Runnable action) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	Ui ui;
	int mouseX;
	int mouseY;
	/** Whether the areas drawn now take clicks: not while a step slides out, or under the popover. */
	boolean live = true;
	private final List<Area> areas = new ArrayList<>();
	private final Map<String, Spring> springs = new HashMap<>();
	/** The area focused with the keyboard, by its place among this frame's areas, or -1. */
	private int focus = -1;

	/** Starts a frame: the areas of the last one are forgotten. */
	void begin(Ui frameUi, int frameMouseX, int frameMouseY) {
		ui = frameUi;
		mouseX = frameMouseX;
		mouseY = frameMouseY;
		areas.clear();
		live = true;
	}

	Palette palette() {
		return ui.palette;
	}

	/**
	 * Registers an area for this frame and draws the focus ring around it when it has the keyboard's focus.
	 *
	 * @return how far the mouse is over it, from 0 to 1, smoothed
	 */
	float area(String key, int x, int y, int width, int height, float radius, Runnable action) {
		boolean over = false;
		if (live) {
			Area area = new Area(x, y, width, height, radius, action);
			over = area.contains(mouseX, mouseY);
			if (areas.size() == focus) {
				ui.ring(x - FOCUS_SPREAD, y - FOCUS_SPREAD, width + 2 * FOCUS_SPREAD, height + 2 * FOCUS_SPREAD, radius + FOCUS_SPREAD, 1, ui.accent);
			}
			areas.add(area);
		}
		return follow(key, over ? 1f : 0f);
	}

	/** @return a value smoothly following a target, by name, for hovers, selections and switches */
	float follow(String key, float target) {
		return springs.computeIfAbsent(key, name -> Spring.smooth(Spring.SNAPPY)).follow(ui, target);
	}

	/** @return whether a click hit an area; its action ran */
	boolean click(double x, double y) {
		for (int i = areas.size() - 1; i >= 0; i--) {
			if (areas.get(i).contains(x, y)) {
				areas.get(i).action().run();
				return true;
			}
		}
		return false;
	}

	/** Moves the keyboard's focus to the next or the previous area. */
	void moveFocus(int step) {
		if (areas.isEmpty()) {
			return;
		}
		focus = focus < 0 ? (step > 0 ? 0 : areas.size() - 1) : Math.floorMod(focus + step, areas.size());
	}

	/** @return whether a focused area was activated */
	boolean activateFocus() {
		if (focus >= 0 && focus < areas.size()) {
			areas.get(focus).action().run();
			return true;
		}
		return false;
	}

	/** Forgets the focus, when the step changes. */
	void resetFocus() {
		focus = -1;
	}

	// ----------------------------------------------------------------------------------------------------------- shapes

	/** A card: quiet, lighter on hover, and with an accent ring and a soft accent light when chosen. */
	void card(int x, int y, int width, int height, float chosen, float hover) {
		Palette palette = ui.palette;
		if (chosen > 0.01f) {
			ui.shadow(x, y, width, height, CARD_RADIUS, 8, 0.35f * chosen);
		}
		ui.rounded(x, y, width, height, CARD_RADIUS, Ui.withOpacity(palette.text(), 0.05f + 0.03f * chosen + 0.03f * hover));
		ui.ring(x, y, width, height, CARD_RADIUS, 1, Ui.withOpacity(palette.text(), 0.09f + 0.06f * hover));
		if (chosen > 0.01f) {
			ui.ring(x - 1, y - 1, width + 2, height + 2, CARD_RADIUS + 1, 2, Ui.withOpacity(ui.accent, chosen));
		}
	}

	/** A quiet box, such as the one around a list or a hint. */
	void box(int x, int y, int width, int height, float radius) {
		ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.palette.text(), 0.05f));
		ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(ui.palette.text(), 0.09f));
	}

	/** A round mark of a choice: an accent dot with a check when chosen, an empty ring when not. */
	void radio(int x, int y, int size, float chosen) {
		// ring, fill and check from the same center, so they stay concentric at every GUI scale
		float centerX = x + size / 2f;
		float centerY = y + size / 2f;
		ui.ringAt(centerX, centerY, size, 1f, Ui.withOpacity(ui.palette.text(), 0.3f * (1f - chosen)));
		if (chosen > 0.01f) {
			ui.discAt(centerX, centerY, size, Ui.withOpacity(ui.accent, chosen));
			int check = 2 * Math.round(size * 0.35f);
			ui.icon("check", Math.round(centerX - check / 2f), Math.round(centerY - check / 2f), check, Ui.withOpacity(0xFFFFFFFF, chosen));
		}
	}

	/** A small switch, as in the mockups: a pill track in the accent when on and a white knob. */
	void smallSwitch(int x, int y, int width, int height, float on) {
		ui.rounded(x, y, width, height, Radius.pill(height), Ui.mix(ui.palette.trackOff(), ui.accent, on));
		int knob = height - 4;
		float left = x + 2 + on * (width - 4 - knob);
		ui.roundedSmooth(left, y + 3, knob, knob, knob / 2f, Ui.KNOB_SHADOW);
		ui.roundedSmooth(left, y + 2, knob, knob, knob / 2f, 0xFFFFFFFF);
	}

	/** An icon on a tile in two colors, lighter at the top, as the mockups draw the icons of features and choices. */
	void tile(String icon, int x, int y, int size, int light, int dark) {
		float radius = size * 0.27f;
		ui.shadow(x, y + 2, size, size, radius, 4, 0.22f);
		ui.roundedGradient(x, y, size, size, radius, 0xFF000000 | light, 0xFF000000 | dark);
		int iconSize = Math.round(size * 0.55f);
		ui.icon(icon, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, 0xFFFFFFFF);
	}

	/**
	 * A compact card with an icon tile, a name, a line of text and a small switch at the right; the whole card toggles it. A card that is not
	 * available is faded, shows its switch off and takes no clicks.
	 */
	void switchCard(String key, int x, int y, int width, int height, String icon, int light, int dark, String name, String text, boolean on,
					boolean available, Runnable toggle) {
		float hover = available ? area(key, x, y, width, height, CARD_RADIUS, toggle) : follow(key, 0f);
		float before = Ui.alpha();
		if (!available) {
			Ui.setAlpha(before * UNAVAILABLE_OPACITY);
		}
		card(x, y, width, height, 0f, hover);
		tile(icon, x + 9, y + (height - SWITCH_TILE) / 2, SWITCH_TILE, light, dark);
		int textX = x + 9 + SWITCH_TILE + 8;
		int room = width - (textX - x) - 9 - 24 - 6;
		ui.textAtScaled(fit(name, room, BODY), textX, y + height / 2f - 9.5f, BODY, ui.palette.text());
		ui.textAtScaled(fit(text, room, SMALL), textX, y + height / 2f + 2.5f, SMALL, ui.palette.textSecondary());
		smallSwitch(x + width - 9 - 24, y + (height - 14) / 2, 24, 14, follow(key + "_on", on && available ? 1f : 0f));
		Ui.setAlpha(before);
	}

	/** The primary button, filled with the accent. */
	void primaryButton(String key, String label, int x, int y, int width, int height, Runnable action) {
		float hover = area(key, x, y, width, height, Radius.MD, action);
		ui.shadow(x, y + 2, width, height, Radius.MD, 6, 0.25f);
		ui.rounded(x, y, width, height, Radius.MD, Ui.mix(ui.accent, 0xFFFFFFFF, 0.12f * hover));
		centered(label, x + width / 2f, y + height / 2f, BODY, true, 0xFFFFFFFF);
	}

	/** A button with an outline and a light fill, for a second action. */
	void secondaryButton(String key, String label, int x, int y, int width, int height, Runnable action) {
		float hover = area(key, x, y, width, height, Radius.MD, action);
		Palette palette = ui.palette;
		ui.rounded(x, y, width, height, Radius.MD, Ui.withOpacity(palette.text(), 0.06f + 0.06f * hover));
		ui.ring(x, y, width, height, Radius.MD, 1, Ui.withOpacity(palette.text(), 0.16f));
		centered(label, x + width / 2f, y + height / 2f, BODY, false, palette.text());
	}

	/** A small chip button inside a card, such as "Connect". */
	void chipButton(String key, String label, int x, int y, int width, int height, Runnable action) {
		float hover = area(key, x, y, width, height, Radius.SM, action);
		Palette palette = ui.palette;
		ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(palette.text(), 0.10f + 0.06f * hover));
		ui.ring(x, y, width, height, Radius.SM, 1, Ui.withOpacity(palette.text(), 0.14f));
		centered(label, x + width / 2f, y + height / 2f, SMALL, false, palette.text());
	}

	/** @return the width of a chip button with the label */
	int chipWidth(String label) {
		return Math.round(ui.widthScaled(label, SMALL)) + 16;
	}

	/** A quiet text link; @return its width */
	int link(String key, String label, float x, float y, float scale, Runnable action) {
		int width = Math.round(ui.widthScaled(label, scale));
		float hover = area(key, Math.round(x) - 2, Math.round(y) - 3, width + 4, Math.round(9 * scale) + 6, Radius.XS, action);
		ui.textAtScaled(label, x, y, scale, Ui.mix(ui.palette.textSecondary(), ui.palette.text(), hover));
		return width;
	}

	/** The title and the text under it (two lines at most) at the top of a step. @return the y below them */
	int heading(String title, String text, int x, int y, int width) {
		ui.textAtScaledTitle(ui.fitScaled(title, width, TITLE), x, y, TITLE, ui.palette.text());
		int lineY = y + 25;
		List<String> lines = wrap(text, width, BODY);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			ui.textAtScaled(i == 1 && lines.size() > 2 ? ui.fitScaled(lines.get(1) + " …", width, BODY) : lines.get(i), x, lineY, BODY, ui.palette.textSecondary());
			lineY += 12;
		}
		return lineY - 2;
	}

	/** A small label in capitals above a group, in the quietest text color. */
	void label(String text, int x, int y) {
		ui.sectionLabel(text, x, y, Ui.withOpacity(ui.palette.textSecondary(), 0.75f));
	}

	/** Draws text centered on a point, in the regular or the semibold face. */
	void centered(String text, float centerX, float middleY, float scale, boolean bold, int color) {
		float width = bold ? ui.boldWidthScaled(text, scale) : ui.widthScaled(text, scale);
		float top = middleY - TEXT_MIDDLE * scale;
		if (bold) {
			ui.textAtScaledBold(text, centerX - width / 2f, top, scale, color);
		} else {
			ui.textAtScaled(text, centerX - width / 2f, top, scale, color);
		}
	}

	/** @return the text broken into lines no wider than the width at the given scale */
	List<String> wrap(String text, float width, float scale) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && ui.widthScaled(candidate, scale) > width) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** @return the text cut to the width with "…" at the given scale */
	String fit(String text, float width, float scale) {
		return ui.fitScaled(text, width, scale);
	}
}
