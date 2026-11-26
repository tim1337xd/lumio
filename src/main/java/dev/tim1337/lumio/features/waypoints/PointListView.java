package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/**
 * The numbered points of a group on the Waypoints page: a handle to drag a point to another place in the order, its number in its color, its name
 * (with "next" on the next point of a route), its coordinates and "⋯" for its actions (hide, rename, move, delete). Points a route has passed and
 * hidden points are dimmed. Long lists show their first points and "N more"; a search shows only the points it finds, which then cannot be dragged.
 */
final class PointListView {
	static final int ROW = 21;
	private static final int PAD = 6;
	private static final int FOLDED_ROWS = 6;
	private static final int NUMBER = 12;
	/** How far the mouse moves before a press on a row becomes a drag. */
	private static final int DRAG_START = 3;
	private static final String MORE = " more";

	/** What a click on the list hit. */
	enum Part {
		NONE, ROW, MENU, MORE
	}

	private int x;
	private int y;
	private int width;
	private boolean unfolded;
	private int shownRows;
	private int moreRow = -1;
	/** The row pressed, and while it is dragged, where it would go and the mouse's offset in its row. */
	private int pressed = -1;
	private double pressY;
	private boolean dragging;
	private int dropAt = -1;
	private double mouseY;
	private int highlighted = -1;
	/** The search, and the points it finds, in their order; all of them without a search. */
	private String filter = "";
	private int[] found = new int[0];
	private int foundCount;
	private final float[] hovers = new float[FOLDED_ROWS * 4];

	void unfold(boolean unfolded) {
		this.unfolded = unfolded;
	}

	void toggleUnfolded() {
		unfolded = !unfolded;
	}

	void highlight(int index) {
		highlighted = index;
		if (index >= FOLDED_ROWS) {
			unfolded = true;
		}
	}

	int highlighted() {
		return highlighted;
	}

	/** Shows only the points whose name or number holds the text; all of them for an empty one. */
	void filter(String text) {
		filter = text.strip().toLowerCase(java.util.Locale.ROOT);
	}

	/** @return the list's height for a group: its rows, and the "more" row while folded */
	int measure(WaypointGroup group, int x, int y, int width) {
		this.x = x;
		this.y = y;
		this.width = width;
		List<Waypoint> all = group.points();
		if (found.length < all.size()) {
			found = new int[all.size()];
		}
		foundCount = 0;
		for (int i = 0; i < all.size(); i++) {
			String name = all.get(i).name().isEmpty() ? "point " + (i + 1) : all.get(i).name().toLowerCase(java.util.Locale.ROOT);
			if (filter.isEmpty() || name.contains(filter) || String.valueOf(i + 1).equals(filter)) {
				found[foundCount++] = i;
			}
		}
		int count = foundCount;
		shownRows = unfolded ? count : Math.min(count, FOLDED_ROWS);
		moreRow = count > FOLDED_ROWS ? shownRows : -1;
		int rows = Math.max(1, shownRows + (moreRow >= 0 ? 1 : 0));
		return rows * ROW + 2 * PAD;
	}

	void render(Ui ui, WaypointGroup group, InlineEdit edit, int mouseX, int mouseY) {
		List<Waypoint> points = group.points();
		if (points.isEmpty() || foundCount == 0) {
			ui.textMiddle(points.isEmpty() ? group.readOnly() ? "No points." : "No points yet" : "No point found", x + PAD + 4, y + PAD + ROW / 2,
				ui.palette.textSecondary());
			return;
		}
		boolean route = group.show() == WaypointGroup.Show.NEXT;
		int next = group.next();
		for (int r = 0; r < shownRows; r++) {
			int i = found[r];
			if (dragging && i == pressed) {
				continue;
			}
			int slot = slotOf(r);
			drawRow(ui, group, points.get(i), i, y + PAD + slot * ROW, route && i == next, route && i < next, edit, mouseX, mouseY);
		}
		if (dragging && pressed >= 0 && pressed < points.size()) {
			int top = (int) Math.round(Math.max(y + PAD, Math.min(y + PAD + (shownRows - 1) * ROW, mouseY - pressY)));
			ui.shadow(x + 3, top, width - 6, ROW, Radius.SM, 4, 0.3f);
			ui.rounded(x + 3, top, width - 6, ROW, Radius.SM, ui.palette.popup());
			drawRow(ui, group, points.get(pressed), pressed, top, route && pressed == next, route && pressed < next, edit, -1, -1);
		}
		if (moreRow >= 0) {
			int top = y + PAD + moreRow * ROW;
			String text = unfolded ? "Show fewer" : (foundCount - FOLDED_ROWS) + MORE + (filter.isEmpty() && !group.readOnly() ? "  ·  drag to reorder" : "");
			ui.icon("chevron", x + PAD + 6, top + ROW / 2 - 3, 7, ui.palette.textSecondary());
			ui.textMiddle(text, x + PAD + 17, top + ROW / 2, ui.palette.textSecondary());
		}
	}

	/** @return the row a point is drawn in while another one is dragged: the rows between make room for the drop */
	private int slotOf(int index) {
		if (!dragging || dropAt < 0) {
			return index;
		}
		if (index > pressed && index <= dropAt) {
			return index - 1;
		}
		if (index < pressed && index >= dropAt) {
			return index + 1;
		}
		return index;
	}

	private void drawRow(Ui ui, WaypointGroup group, Waypoint point, int index, int top, boolean next, boolean done, InlineEdit edit, int mouseX,
						 int mouseY) {
		int middle = top + ROW / 2;
		boolean over = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ROW;
		int slot = Math.min(index, hovers.length - 1);
		hovers[slot] = ui.approach(hovers[slot], over || index == highlighted ? 1f : 0f, Ui.HOVER_RATE);
		if (hovers[slot] > 0f || next) {
			ui.rounded(x + 3, top, width - 6, ROW, Radius.SM, Ui.withOpacity(ui.palette.hover(), Math.max(hovers[slot], next ? 0.7f : 0f)));
		}
		if (next) {
			// the next point of the route: marked at its left edge
			ui.rounded(x + 3, top + 3, 2, ROW - 6, Radius.XS, ui.accent);
		}
		// a point the route has passed, or one hidden, is dimmed
		float dim = point.hidden() || !group.drawn(index) ? 0.4f : done ? 0.55f : 1f;
		int handleX = x + PAD + 4;
		if (!group.readOnly()) {
			for (int r = 0; r < 3; r++) {
				for (int c = 0; c < 2; c++) {
					ui.fill(handleX + c * 3, middle - 4 + r * 3, 1, 1, Ui.withOpacity(ui.palette.textSecondary(), 0.7f));
				}
			}
		}
		int numberX = handleX + 10;
		int color = 0xFF000000 | group.rgbOf(point);
		ui.rounded(numberX, middle - NUMBER / 2, NUMBER, NUMBER, Radius.XS, Ui.withOpacity(color, dim));
		String number = String.valueOf(index + 1);
		float numberWidth = ui.boldWidthScaled(number, TextScale.SMALL);
		ui.textAtScaledBold(number, numberX + NUMBER / 2f - numberWidth / 2f, middle - 3.5f, TextScale.SMALL,
			Ui.luminance(color) > 0.6f ? 0xFF1C1C1E : 0xFFFFFFFF);
		int menuX = x + width - PAD - 10;
		String coords = point.coords();
		int coordsRight = menuX - 8;
		int coordsWidth = Math.round(ui.widthScaled(coords, TextScale.SMALL));
		ui.textAtScaled(coords, coordsRight - coordsWidth, middle - 3.5f, TextScale.SMALL, Ui.withOpacity(ui.palette.textSecondary(), dim));
		int nameX = numberX + NUMBER + 7;
		int nameRoom = coordsRight - coordsWidth - 8 - nameX;
		if (edit.editing(index)) {
			edit.draw(ui, nameX, middle - 7, nameRoom, 14);
		} else {
			String name = point.name().isEmpty() ? "Point " + (index + 1) : point.name();
			String mark = next ? "  ·  next" : "";
			int markWidth = next ? Math.round(ui.widthScaled(mark, TextScale.SMALL)) : 0;
			String fitted = ui.fit(name, nameRoom - markWidth);
			ui.textMiddle(fitted, nameX, middle, Ui.withOpacity(ui.palette.text(), dim));
			if (!fitted.equals(name) && over) {
				// a name the player chose may be cut short; the whole of it on hover
				dev.tim1337.lumio.ui.Control.showFullText(name);
			}
			if (next) {
				ui.textAtScaled(mark, nameX + ui.width(fitted), middle - 3.5f, TextScale.SMALL, ui.palette.textSecondary());
			}
		}
		WaypointMenu.drawDots(ui, menuX + 4, middle, ui.palette.textSecondary());
	}

	/** @return the point under the mouse, or -1 */
	int rowAt(double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + width || mouseY < y + PAD) {
			return -1;
		}
		int row = (int) ((mouseY - y - PAD) / ROW);
		return row >= 0 && row < shownRows ? found[row] : -1;
	}

	/** @return the row a point is shown in now, or -1 */
	private int rowOf(int index) {
		for (int r = 0; r < shownRows; r++) {
			if (found[r] == index) {
				return r;
			}
		}
		return -1;
	}

	/** @return the part of the list under the mouse */
	Part partAt(double mouseX, double mouseY) {
		if (moreRow >= 0 && mouseX >= x && mouseX < x + width && mouseY >= y + PAD + moreRow * ROW && mouseY < y + PAD + (moreRow + 1) * ROW) {
			return Part.MORE;
		}
		if (rowAt(mouseX, mouseY) < 0) {
			return Part.NONE;
		}
		int menuX = x + width - PAD - 10;
		return mouseX >= menuX - 3 ? Part.MENU : Part.ROW;
	}

	/** @return the menu button's place of a row, where its menu opens: x and y */
	int[] menuAnchor(int index) {
		return new int[] {x + width - PAD, y + PAD + Math.max(0, rowOf(index)) * ROW + ROW};
	}

	/** @return the name's place of a row, for the inline rename */
	int rowTop(int index) {
		return y + PAD + Math.max(0, rowOf(index)) * ROW;
	}

	/** A press on a row: a drag may begin from it. */
	void press(int index, double mouseY) {
		pressed = index;
		pressY = mouseY - rowTop(index);
		this.mouseY = mouseY;
		dragging = false;
		dropAt = -1;
	}

	/** @return whether the press became a drag */
	boolean drag(WaypointGroup group, double mouseY) {
		if (pressed < 0 || group.readOnly() || !filter.isEmpty()) {
			// a found point is not dragged: the rows between it and its place are not shown
			return false;
		}
		this.mouseY = mouseY;
		if (!dragging && Math.abs(mouseY - (rowTop(pressed) + pressY)) >= DRAG_START) {
			dragging = true;
			unfolded = true;
		}
		if (dragging) {
			int row = (int) Math.floor((mouseY - pressY + ROW / 2.0 - y - PAD) / ROW);
			dropAt = Math.max(0, Math.min(group.points().size() - 1, row));
		}
		return dragging;
	}

	/** Ends a press: a drag moves the point to where it was dropped. @return whether a point moved */
	boolean release(WaypointGroup group) {
		boolean moved = false;
		if (dragging && pressed >= 0 && dropAt >= 0 && dropAt != pressed) {
			group.move(pressed, dropAt);
			UiSounds.tick(0.6f);
			moved = true;
		}
		pressed = -1;
		dragging = false;
		dropAt = -1;
		return moved;
	}

	boolean dragging() {
		return dragging;
	}
}
