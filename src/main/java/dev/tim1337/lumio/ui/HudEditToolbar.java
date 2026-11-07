package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.hud.HudSimulation;

import net.minecraft.util.Mth;

/**
 * The floating toolbar of the HUD editor, at the top center: a grip to move it, undo and redo, the grid and snap switches, the
 * activity simulation, the element list (an icon) and done, and a button that collapses it into a small pill. It never gets in the way: it fades
 * out while an element is dragged and moves aside when the selected element is under it. Its place and whether it is collapsed are kept
 * while the game runs.
 */
final class HudEditToolbar {
	private static final int HEIGHT = 28;
	private static final int BUTTON_HEIGHT = 22;
	private static final int PAD = 4;
	private static final int BUTTON_PAD = 8;
	private static final int ICON = 10;
	private static final int ICON_BUTTON = 22;
	private static final int TOP = 8;
	/** How near its default place (centered at the top) the toolbar has to be dragged to settle there, in GUI pixels. */
	private static final int HOME_SNAP = 14;
	private static final int CHIP_GAP = 8;

	// the buttons, in their order
	private static final int GRIP = 0;
	private static final int UNDO = 1;
	private static final int REDO = 2;
	private static final int GRID = 3;
	private static final int SNAP = 4;
	/** The theme and depth of the whole HUD, in a small menu of its own. */
	private static final int LOOK = 5;
	private static final int SIMULATE = 6;
	/** The list of elements, as an icon: it is where hidden elements come back and the whole layout is reset. */
	private static final int ELEMENTS = 7;
	private static final int DONE = 8;
	private static final int COLLAPSE = 9;
	private static final int EXPAND = 10;
	private static final int STOP_SIMULATION = 11;
	private static final String[] LABELS = {"", "Undo", "Redo", "Grid", "Snap", "Look", "Simulate", "", "Done", "", "Edit HUD", ""};
	/** Separators come before these buttons, grouping them: Undo, Redo | Grid, Snap | Look, Simulate, Done. */
	private static final boolean[] GROUP_START = {false, true, false, true, false, true, false, false, false, true, false, false};

	// where the toolbar is (its center across, relative to the screen center, and its top) and whether it is collapsed, kept while the game runs
	private static int offsetX;
	private static int top = TOP;
	private static boolean collapsed;

	private final HudEditScreen screen;
	private final HudEditHits hits = new HudEditHits();
	private final Spring fade = Spring.smooth(Spring.SNAPPY);
	private final Spring aside = Spring.smooth(Spring.DEFAULT);
	private final float[] hover = new float[LABELS.length];
	private boolean moving;
	// where the toolbar was grabbed: the mouse position relative to its offset and top, so it stays under the mouse also after an edge
	private double grabX;
	private double grabY;
	/** Whether the toolbar is held in its default place while dragged. */
	private boolean atHome;
	// The drawn place: it follows the mouse exactly while dragged, and glides when it settles into or leaves its default place.
	private final Spring placeX = Spring.smooth(Spring.SNAPPY);
	private final Spring placeY = Spring.smooth(Spring.SNAPPY);
	private boolean gliding;
	private boolean placed;
	// the toolbar's rectangle from the last frame
	private int x;
	private int y;
	private int width;
	private int height = HEIGHT;
	private float alpha = 1f;
	/** Where the Simulate button is, so its menu opens under it. */
	int simulateX;
	int simulateBottom;
	/** Where the Look button is, so its menu opens under it. */
	int lookX;
	int lookBottom;

	HudEditToolbar(HudEditScreen screen) {
		this.screen = screen;
	}

	boolean contains(double mouseX, double mouseY) {
		return alpha > 0.5f && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height || hits.at(mouseX, mouseY) == STOP_SIMULATION;
	}

	/** @return the bottom of the toolbar, where the element list starts */
	int bottom() {
		return y + height;
	}

	/** @return whether a button is shown: Simulate only while an add-on has activities to simulate */
	private static boolean shown(int button) {
		return button != SIMULATE || Addons.simulators().length > 0;
	}

	/** @return whether a separator comes before the button */
	private static boolean groupStart(int button) {
		return GROUP_START[button];
	}

	private int labelWidth(Ui ui, int button) {
		return switch (button) {
			case GRIP, COLLAPSE, ELEMENTS -> ICON_BUTTON;
			default -> ui.width(LABELS[button]) + 2 * BUTTON_PAD;
		};
	}

	void draw(Ui ui, int mouseX, int mouseY) {
		hits.clear();
		alpha = Mth.clamp(fade.follow(ui, screen.dragging() ? 0f : 1f), 0f, 1f);
		if (collapsed) {
			drawCollapsed(ui, mouseX, mouseY);
			return;
		}
		layout(ui);
		int home = place(ui, Mth.clamp(screen.width / 2 + offsetX - width / 2, 4, Math.max(4, screen.width - width - 4)),
			Mth.clamp(top, 4, Math.max(4, screen.height - height - 4)));
		x = home + Math.round(aside.follow(ui, asideShift(home)));
		if (alpha <= 0.01f) {
			return;
		}
		HudEditScreen.surface(ui, x, y, width, height, Radius.LG, alpha);
		for (int button = GRIP; button <= COLLAPSE; button++) {
			if (!shown(button)) {
				continue;
			}
			int bx = x + buttonX[button];
			int by = y + PAD + buttonRow[button] * ROW_STEP;
			if (groupStart(button) && buttonX[button] > PAD + 5) {
				ui.fill(bx - 4, by + 3, 1, BUTTON_HEIGHT - 6, Ui.withOpacity(ui.palette.cardBorder(), alpha));
			}
			int buttonWidth = labelWidth(ui, button);
			drawButton(ui, button, bx, by, buttonWidth, mouseX, mouseY);
			if (button == SIMULATE) {
				simulateX = bx;
				simulateBottom = y + height;
			}
			if (button == LOOK) {
				lookX = bx;
				lookBottom = y + height;
			}
		}
		drawSimulationChip(ui, mouseX, mouseY);
	}

	// where every button is in the toolbar: its row and its left edge; a toolbar wider than the screen (a small window or a large GUI
	// scale) wraps onto more rows
	private final int[] buttonX = new int[LABELS.length];
	private final int[] buttonRow = new int[LABELS.length];
	private static final int ROW_STEP = BUTTON_HEIGHT + 2;

	/** Lays the buttons out in rows no wider than the screen, and sets the toolbar's width and height from them. */
	private void layout(Ui ui) {
		int most = screen.width - 8 - 2 * PAD;
		int at = 0;
		int row = 0;
		int widest = 0;
		for (int button = GRIP; button <= COLLAPSE; button++) {
			if (!shown(button)) {
				continue;
			}
			int buttonWidth = labelWidth(ui, button);
			int gap = button == GRIP ? 0 : groupStart(button) ? 7 : 2;
			if (at > 0 && at + gap + buttonWidth > most) {
				row++;
				at = 0;
				gap = 0;
			}
			at += gap;
			buttonX[button] = PAD + at;
			buttonRow[button] = row;
			at += buttonWidth;
			widest = Math.max(widest, at);
		}
		width = widest + 2 * PAD;
		height = Math.max(HEIGHT, 2 * PAD + (row + 1) * ROW_STEP - 2);
	}

	/** Sets {@link #y} to the drawn top. @return the drawn left edge: the wanted one, or on its way there while gliding */
	private int place(Ui ui, int wantedX, int wantedY) {
		if (!gliding || !placed) {
			placed = true;
			placeX.snap(wantedX);
			placeY.snap(wantedY);
			y = wantedY;
			return wantedX;
		}
		float drawnX = placeX.follow(ui, wantedX);
		float drawnY = placeY.follow(ui, wantedY);
		if (Math.abs(drawnX - wantedX) < 0.5f && Math.abs(drawnY - wantedY) < 0.5f) {
			gliding = false;
		}
		y = Math.round(drawnY);
		return Math.round(drawnX);
	}

	/** @return how far the toolbar moves aside so it does not cover the selected element, 0 when it is not in the way */
	private float asideShift(int home) {
		EditableHud selected = screen.selected;
		if (selected == null || screen.dragging()) {
			return 0f;
		}
		int[] box = selected.hudBounds();
		int toolbarTop = Mth.clamp(top, 4, Math.max(4, screen.height - height - 4));
		boolean covers = box[0] < home + width && box[0] + box[2] > home && box[1] < toolbarTop + height && box[1] + box[3] > toolbarTop;
		if (!covers) {
			return 0f;
		}
		// to the side with more room
		int left = box[0] - 8 - width - home;
		int right = box[0] + box[2] + 8 - home;
		boolean leftFits = home + left >= 4;
		boolean rightFits = home + right + width <= screen.width - 4;
		return rightFits && (!leftFits || -left > right) ? right : leftFits ? left : 0f;
	}

	private void drawButton(Ui ui, int button, int bx, int by, int buttonWidth, int mouseX, int mouseY) {
		boolean enabled = button != UNDO && button != REDO || (button == UNDO ? screen.history.canUndo() : screen.history.canRedo());
		boolean on = button == SNAP && HudEditScreen.snapOn || button == GRID && HudEditScreen.gridOn
			|| button == ELEMENTS && screen.list.isOpen() || button == SIMULATE && HudSimulation.running() != null || button == LOOK && screen.look.isOpen();
		boolean over = enabled && mouseX >= bx && mouseX < bx + buttonWidth && mouseY >= by && mouseY < by + BUTTON_HEIGHT;
		hover[button] = ui.approach(hover[button], over ? 1f : 0f, Ui.HOVER_RATE);
		Palette palette = ui.palette;
		if (button == DONE) {
			ui.rounded(bx, by, buttonWidth, BUTTON_HEIGHT, Radius.SM, Ui.withOpacity(Ui.mix(ui.accent, 0xFFFFFFFF, 0.14f * hover[button]), alpha));
		} else if (on) {
			ui.rounded(bx, by, buttonWidth, BUTTON_HEIGHT, Radius.SM, Ui.withOpacity(ui.accent, (0.22f + 0.1f * hover[button]) * alpha));
		} else if (hover[button] > 0f) {
			ui.rounded(bx, by, buttonWidth, BUTTON_HEIGHT, Radius.SM, Ui.withOpacity(palette.hover(), hover[button] * alpha));
		}
		int color = !enabled ? Ui.withOpacity(palette.textSecondary(), 0.45f * alpha)
			: Ui.withOpacity(button == DONE ? 0xFFFFFFFF : on ? palette.text() : Ui.mix(palette.textSecondary(), palette.text(), hover[button]), alpha);
		int middle = by + BUTTON_HEIGHT / 2;
		switch (button) {
			case GRIP -> ui.icon("move", bx + (buttonWidth - ICON) / 2, middle - ICON / 2, ICON, color);
			case ELEMENTS -> ui.icon("layers", bx + (buttonWidth - ICON) / 2, middle - ICON / 2, ICON, color);
			case COLLAPSE -> {
				// the chevron points up: the toolbar folds into a pill
				var pose = ui.graphics.pose();
				pose.pushMatrix();
				pose.translate(bx + buttonWidth / 2f, middle);
				pose.rotate((float) Math.PI);
				ui.icon("chevron", -4, -4, 8, color);
				pose.popMatrix();
			}
			default -> ui.textCentered(LABELS[button], bx + buttonWidth / 2, middle, color);
		}
		if (enabled) {
			hits.add(button, bx, by, buttonWidth, BUTTON_HEIGHT);
		}
	}

	/** Draws the collapsed toolbar: a small pill that opens it again. */
	private void drawCollapsed(Ui ui, int mouseX, int mouseY) {
		width = labelWidth(ui, EXPAND) + 18;
		height = BUTTON_HEIGHT + 6;
		x = place(ui, Mth.clamp(screen.width / 2 + offsetX - width / 2, 4, Math.max(4, screen.width - width - 4)),
			Mth.clamp(top, 4, Math.max(4, screen.height - height - 4)));
		if (alpha <= 0.01f) {
			return;
		}
		boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		hover[EXPAND] = ui.approach(hover[EXPAND], over ? 1f : 0f, Ui.HOVER_RATE);
		HudEditScreen.surface(ui, x, y, width, height, Radius.pill(height), alpha);
		if (hover[EXPAND] > 0f) {
			ui.rounded(x, y, width, height, Radius.pill(height), Ui.withOpacity(ui.palette.hover(), hover[EXPAND] * alpha));
		}
		ui.textMiddle(LABELS[EXPAND], x + 9, y + height / 2, Ui.withOpacity(ui.palette.text(), alpha));
		ui.icon("chevron", x + width - 14, y + height / 2 - 4, 8, Ui.withOpacity(ui.palette.textSecondary(), alpha));
		hits.add(EXPAND, x, y, width, height);
		drawSimulationChip(ui, mouseX, mouseY);
	}

	private String chipText = "";
	private float stopHover;

	/** Draws the chip saying which activity is simulated, next to the toolbar, with an × that stops it. */
	private void drawSimulationChip(Ui ui, int mouseX, int mouseY) {
		String text = HudEditScreen.simulationText();
		if (text.isEmpty() || alpha <= 0.01f) {
			return;
		}
		if (!text.equals(chipText)) {
			chipText = text;
		}
		int chipHeight = BUTTON_HEIGHT;
		int chipWidth = ui.width(chipText) + 30;
		int chipX = x + width + CHIP_GAP;
		int chipY = y + (height - chipHeight) / 2;
		if (chipX + chipWidth > screen.width - 4) {
			// no room beside the toolbar: under it
			chipX = Mth.clamp(x + width / 2 - chipWidth / 2, 4, screen.width - chipWidth - 4);
			chipY = y + height + 6;
		}
		boolean over = mouseX >= chipX + chipWidth - 20 && mouseX < chipX + chipWidth && mouseY >= chipY && mouseY < chipY + chipHeight;
		stopHover = ui.approach(stopHover, over ? 1f : 0f, Ui.HOVER_RATE);
		ui.rounded(chipX, chipY, chipWidth, chipHeight, Radius.pill(chipHeight), Ui.withOpacity(ui.accent, alpha));
		ui.textMiddle(chipText, chipX + 10, chipY + chipHeight / 2, Ui.withOpacity(0xFFFFFFFF, alpha));
		ui.icon("close", chipX + chipWidth - 15, chipY + chipHeight / 2 - 4, 8, Ui.withOpacity(0xFFFFFFFF, (0.7f + 0.3f * stopHover) * alpha));
		hits.add(STOP_SIMULATION, chipX + chipWidth - 20, chipY, 20, chipHeight);
	}

	/** @return whether the click was on the toolbar */
	boolean click(double mouseX, double mouseY, int button) {
		if (alpha < 0.5f) {
			return false;
		}
		int hit = hits.at(mouseX, mouseY);
		if (hit < 0) {
			boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
			// the toolbar can be grabbed anywhere between its buttons, not only at the grip
			if (inside && button == 0 && !collapsed) {
				grab(mouseX, mouseY);
			}
			return inside;
		}
		if (button != 0) {
			return true;
		}
		switch (hit) {
			case GRIP -> grab(mouseX, mouseY);
			case UNDO -> screen.undo();
			case REDO -> screen.redo();
			case SNAP -> {
				HudEditScreen.snapOn = !HudEditScreen.snapOn;
				UiSounds.toggle(HudEditScreen.snapOn);
			}
			case GRID -> {
				HudEditScreen.gridOn = !HudEditScreen.gridOn;
				UiSounds.toggle(HudEditScreen.gridOn);
			}
			case LOOK -> screen.look.toggle(lookX, lookBottom);
			case SIMULATE -> screen.simulate.toggle(simulateX, simulateBottom);
			case ELEMENTS -> screen.list.toggle();
			case DONE -> {
				UiSounds.click();
				screen.onClose();
			}
			case COLLAPSE, EXPAND -> {
				collapsed = !collapsed;
				UiSounds.click();
			}
			case STOP_SIMULATION -> {
				HudSimulation.stop();
				UiSounds.play(UiSounds.Cue.CLOSE);
			}
			default -> {
			}
		}
		return true;
	}

	private void grab(double mouseX, double mouseY) {
		moving = true;
		grabX = mouseX - offsetX;
		grabY = mouseY - top;
		atHome = offsetX == 0 && top == TOP;
	}

	/**
	 * Moves the toolbar while it is held, keeping it on screen. Near its default place it settles there, with a soft tick, and leaves it again
	 * when dragged further. @return whether it is being moved
	 */
	boolean drag(double mouseX, double mouseY) {
		if (!moving) {
			return false;
		}
		int wantedX = (int) Math.round(mouseX - grabX);
		int wantedTop = (int) Math.round(mouseY - grabY);
		boolean home = Math.abs(wantedX) <= HOME_SNAP && Math.abs(wantedTop - TOP) <= HOME_SNAP;
		if (home != atHome) {
			atHome = home;
			gliding = true;
			if (home) {
				UiSounds.play(UiSounds.Cue.TICK);
			}
		}
		int room = Math.max(0, (screen.width - width) / 2 - 4);
		offsetX = home ? 0 : Mth.clamp(wantedX, -room, room);
		top = home ? TOP : Mth.clamp(wantedTop, 4, Math.max(4, screen.height - height - 4));
		return true;
	}

	void release() {
		moving = false;
	}

	/** Opens the Simulate menu under its button. */
	void openSimulate() {
		if (!screen.simulate.isOpen()) {
			screen.simulate.toggle(simulateX, simulateBottom);
		}
	}
}
