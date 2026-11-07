package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.RecentChanges;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The card on the home page with the settings changed last ({@link RecentChanges}): per change the feature's icon, its name and page, the
 * setting with its old value crossed out and its new one, and when it changed. Hovering a change offers to undo it; a click elsewhere on it
 * jumps to the setting. The newest few are shown, the rest after "Show all". The times are built once per second, not every frame.
 */
final class RecentChangesCard {
	private static final int HEAD = 28;
	private static final int ROW = 32;
	private static final int MORE_ROW = 20;
	private static final int QUIET_ROW = 24;
	private static final int BOTTOM = 4;
	private static final int PAD = 10;
	/** The rows sit this far inside the card, so their hover fill keeps to its rounded shape. */
	private static final int INSET = 4;
	private static final int TILE = 20;
	private static final int TILE_ICON = 13;
	private static final int TEXT_GAP = 8;
	/** How many changes show before "Show all". */
	private static final int FIRST = 5;
	private static final int SWATCH = 7;
	private static final int SWATCH_GAP = 4;
	private static final int VALUE_GAP = 4;
	/** The arrow between the old and the new value, with its room on both sides. */
	private static final int ARROW = 13;
	private static final int CHEVRON = 8;
	private static final int UNDO_HEIGHT = 14;
	private static final int UNDO_ICON = 8;
	private static final int PILL_HEIGHT = 11;
	private static final int PILL_PAD = 6;
	/** Where the line through an old value is, below the top of its text. */
	private static final int STRIKE_Y = 4;
	private static final long REFRESH_MILLIS = 1000;
	private static final long MINUTE_MILLIS = 60_000;
	private static final long MINUTES_PER_HOUR = 60;
	private static final String TITLE = "Changed recently";
	private static final String HINT = "Click a change to jump to it";
	private static final String CLEAR = "Clear";
	private static final String UNDO = "Undo";
	private static final String EMPTY = "Nothing changed yet. Your setting changes show up here.";
	private static final int EMPTY_LINE = 10;
	private static final int EMPTY_LINES = 3;
	/** The empty hint's lines for the width they were wrapped to. */
	private List<String> emptyLines = List.of(EMPTY);
	private int emptyWidth = -1;
	private static final String TOGGLE_LABEL = "Feature";

	private final Runnable onChanged;
	private boolean expanded;
	private int shownVersion = -1;
	private long agesAt;
	private final String[] ages = new String[RecentChanges.KEPT];
	private String showAll = "";
	private final float[] rowHover = new float[RecentChanges.KEPT];
	private float undoHover;
	private float clearHover;
	private float moreHover;
	// where the card and its buttons were drawn in the last frame, for clicks
	private int cardX;
	private int cardY;
	private int cardWidth;
	private int undoRow = -1;
	private int undoX;
	private int undoY;
	private int undoWidth;
	private int clearX;
	private int clearWidth;

	RecentChangesCard(Runnable onChanged) {
		this.onChanged = onChanged;
	}

	/** Builds the times and the "Show all" text again: once per second, or at once when the list changed. */
	void refresh(boolean now) {
		long time = System.currentTimeMillis();
		if (!now && time - agesAt < REFRESH_MILLIS && shownVersion == RecentChanges.version()) {
			return;
		}
		agesAt = time;
		shownVersion = RecentChanges.version();
		List<RecentChanges.Change> changes = RecentChanges.list();
		for (int i = 0; i < changes.size(); i++) {
			ages[i] = age(changes.get(i).millis(), time);
		}
		showAll = "Show all (" + changes.size() + ")";
		if (changes.size() <= FIRST) {
			expanded = false;
		}
	}

	private int shownCount() {
		int size = RecentChanges.list().size();
		return expanded ? size : Math.min(FIRST, size);
	}

	private boolean hasMore() {
		return shownCount() < RecentChanges.list().size();
	}

	int height() {
		int size = RecentChanges.list().size();
		return HEAD + (size == 0 ? QUIET_ROW + (emptyLines.size() - 1) * EMPTY_LINE : shownCount() * ROW + (hasMore() ? MORE_ROW : 0)) + BOTTOM;
	}

	void draw(Ui ui, int mouseX, int mouseY, int x, int y, int width) {
		refresh(false);
		Palette palette = ui.palette;
		cardX = x;
		cardY = y;
		cardWidth = width;
		undoRow = -1;
		List<RecentChanges.Change> changes = RecentChanges.list();
		int height = height();
		ui.rounded(x, y, width, height, Radius.LG, palette.card());
		ui.ring(x, y, width, height, Radius.LG, 1, palette.cardBorder());
		drawHead(ui, mouseX, mouseY, changes.isEmpty());
		if (changes.isEmpty()) {
			// the hint wraps instead of being cut; the card is taller for its second line from the next frame on
			if (width != emptyWidth) {
				emptyWidth = width;
				emptyLines = ui.wrapScaled(EMPTY, width - 2 * PAD, TextScale.SMALL, EMPTY_LINES);
			}
			for (int k = 0; k < emptyLines.size(); k++) {
				ui.textAtScaled(emptyLines.get(k), x + PAD, y + HEAD + QUIET_ROW / 2f - 4f + k * EMPTY_LINE, TextScale.SMALL, palette.textSecondary());
			}
			return;
		}
		int shown = shownCount();
		for (int i = 0; i < shown; i++) {
			drawRow(ui, mouseX, mouseY, changes.get(i), i);
		}
		if (hasMore()) {
			int moreY = y + HEAD + shown * ROW;
			int moreWidth = ui.width(showAll);
			boolean over = inside(mouseX, mouseY, x + PAD, moreY, moreWidth, MORE_ROW);
			moreHover = ui.approach(moreHover, over ? 1f : 0f, Ui.HOVER_RATE);
			int middle = moreY + MORE_ROW / 2;
			ui.textMiddle(showAll, x + PAD, middle, palette.text());
			if (moreHover > 0.01f) {
				ui.fill(x + PAD, middle + 5, moreWidth, 1, Ui.withOpacity(palette.text(), moreHover));
			}
		}
	}

	/** Draws the title, the hint when there is room for it, and the button that empties the list. */
	private void drawHead(Ui ui, int mouseX, int mouseY, boolean empty) {
		Palette palette = ui.palette;
		int titleY = cardY + 9;
		ui.textAtScaledBold(TITLE, cardX + PAD, titleY, 1f, palette.text());
		int right = cardX + cardWidth - PAD;
		clearWidth = empty ? 0 : Math.round(ui.widthScaled(CLEAR, TextScale.SMALL)) + 2 * PILL_PAD;
		clearX = right - clearWidth + PILL_PAD;
		if (!empty) {
			boolean over = inside(mouseX, mouseY, clearX, cardY + 4, clearWidth, HEAD - 6);
			clearHover = ui.approach(clearHover, over ? 1f : 0f, Ui.HOVER_RATE);
			if (clearHover > 0.01f) {
				ui.rounded(clearX, cardY + 6, clearWidth, HEAD - 10, Radius.SM, Ui.withOpacity(palette.hover(), clearHover));
			}
			ui.textAtScaled(CLEAR, clearX + PILL_PAD, titleY + 1, TextScale.SMALL, Ui.mix(palette.textSecondary(), palette.text(), clearHover));
		}
		int hintX = cardX + PAD + Math.round(ui.boldWidthScaled(TITLE, 1f)) + 8;
		int hintRoom = (empty ? right : clearX - 6) - hintX;
		if (!empty && ui.widthScaled(HINT, TextScale.SMALL) <= hintRoom) {
			ui.textAtScaled(HINT, hintX, titleY + 1, TextScale.SMALL, Ui.withOpacity(palette.textSecondary(), 0.8f));
		}
	}

	private void drawRow(Ui ui, int mouseX, int mouseY, RecentChanges.Change change, int i) {
		Palette palette = ui.palette;
		int rowX = cardX + INSET;
		int rowWidth = cardWidth - 2 * INSET;
		int top = cardY + HEAD + i * ROW;
		int middle = top + ROW / 2;
		boolean over = inside(mouseX, mouseY, rowX, top, rowWidth, ROW);
		float hover = rowHover[i] = ui.approach(rowHover[i], over ? 1f : 0f, Ui.HOVER_RATE);
		int tileX = rowX + PAD - INSET;
		int textX = tileX + TILE + TEXT_GAP;
		if (i > 0) {
			// a separator starting at the text, as in the feature lists
			ui.fill(textX, top, rowX + rowWidth - (PAD - INSET) - textX, 1, Ui.withOpacity(palette.cardBorder(), 0.8f));
		}
		if (hover > 0.01f) {
			ui.rounded(rowX, top, rowWidth, ROW, Radius.MD, Ui.withOpacity(palette.hover(), hover));
		}
		ui.iconTile(change.feature().iconName(), tileX, middle - TILE / 2, TILE, TILE_ICON, Radius.SM, 0xFF000000 | change.feature().iconRgb(), 0xFFFFFFFF);

		// on the right: a chevron, and when it changed, or on hover the button that undoes it
		int chevronX = rowX + rowWidth - (PAD - INSET) - CHEVRON;
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(chevronX + CHEVRON / 2f, middle);
		pose.rotate((float) (-Math.PI / 2.0));
		ui.icon("chevron", -CHEVRON / 2, -CHEVRON / 2, CHEVRON, Ui.withOpacity(palette.textSecondary(), 0.8f));
		pose.popMatrix();
		int timeRight = chevronX - 6;
		String age = ages[i] == null ? "" : ages[i];
		float ageWidth = ui.widthScaled(age, TextScale.SMALL);
		int undoW = UNDO_ICON + 3 + Math.round(ui.widthScaled(UNDO, TextScale.SMALL)) + 2 * PILL_PAD;
		boolean undoable = change.undoable();
		if (hover < 0.99f || !undoable) {
			float fade = undoable ? 1f - hover : 1f;
			ui.textAtScaled(age, timeRight - ageWidth, middle - 3.5f, TextScale.SMALL, Ui.withOpacity(palette.textSecondary(), fade));
		}
		if (undoable && hover > 0.01f) {
			int ux = timeRight - undoW;
			int uy = middle - UNDO_HEIGHT / 2;
			boolean onUndo = over && inside(mouseX, mouseY, ux, uy, undoW, UNDO_HEIGHT);
			undoHover = over ? ui.approach(undoHover, onUndo ? 1f : 0f, Ui.HOVER_RATE) : undoHover;
			float lit = over ? undoHover : 0f;
			ui.rounded(ux, uy, undoW, UNDO_HEIGHT, Radius.XS, Ui.withOpacity(palette.text(), (0.08f + 0.08f * lit) * hover));
			ui.ring(ux, uy, undoW, UNDO_HEIGHT, Radius.XS, 1, Ui.withOpacity(palette.text(), 0.16f * hover));
			ui.icon("reset", ux + PILL_PAD, middle - UNDO_ICON / 2, UNDO_ICON, Ui.withOpacity(palette.text(), hover));
			ui.textAtScaled(UNDO, ux + PILL_PAD + UNDO_ICON + 3, middle - 3.5f, TextScale.SMALL, Ui.withOpacity(palette.text(), hover));
			if (over) {
				undoRow = i;
				undoX = ux;
				undoY = uy;
				undoWidth = undoW;
			}
		}
		int reserved = Math.max(Math.round(ageWidth), undoable ? undoW : 0);
		int room = Math.max(30, timeRight - reserved - 8 - textX);

		// the first line: the feature, and its page in a quieter color
		String name = ui.fit(change.feature().nameText(), room);
		ui.textAtScaledBold(name, textX, top + 6, 1f, palette.text());
		int placeX = textX + Math.round(ui.boldWidthScaled(name, 1f)) + 6;
		int placeRoom = textX + room - placeX;
		if (!change.place().isEmpty() && !change.place().equals(change.feature().nameText()) && placeRoom > 24) {
			ui.textAtScaled(ui.fitScaled(change.place(), placeRoom, TextScale.SMALL), placeX, top + 7, TextScale.SMALL,
				Ui.withOpacity(palette.textSecondary(), 0.8f));
		}
		drawValues(ui, change, textX, top + 18, room);
	}

	/** Draws the second line: the setting, then its old value crossed out, an arrow and the new value; or the feature turned on or off. */
	private void drawValues(Ui ui, RecentChanges.Change change, int lineX, int lineY, int room) {
		Palette palette = ui.palette;
		float scale = TextScale.SMALL;
		String label = change.toggle() ? TOGGLE_LABEL : change.setting().labelText();
		float labelWidth = ui.widthScaled(label, scale);
		if (change.toggle()) {
			String state = change.on() ? "Turned on" : "Turned off";
			int pillWidth = Math.round(ui.boldWidthScaled(state, TextScale.SMALL)) + 2 * PILL_PAD;
			int labelRoom = Math.max(0, room - pillWidth - VALUE_GAP);
			ui.textAtScaled(ui.fitScaled(label, labelRoom, scale), lineX, lineY, scale, palette.textSecondary());
			int pillX = lineX + Math.round(Math.min(labelWidth, labelRoom)) + VALUE_GAP;
			int color = change.on() ? SystemColors.GREEN : 0xFF000000 | SystemColors.GRAY_RGB;
			int text = change.on() ? (palette.light() ? Ui.mix(color, 0xFF000000, 0.35f) : Ui.mix(color, 0xFFFFFFFF, 0.45f)) : palette.textSecondary();
			ui.rounded(pillX, lineY - 2, pillWidth, PILL_HEIGHT, Radius.pill(PILL_HEIGHT), Ui.withOpacity(color, 0.22f));
			ui.textAtScaledBold(state, pillX + PILL_PAD, lineY - 0.5f, TextScale.SMALL, text);
			return;
		}
		RecentChanges.Value before = change.before();
		String old = before == null ? null : before.text();
		String now = change.after() == null ? null : change.after().text();
		// the values keep their room before the setting's name does, and share it equally when even they do not fit
		float oldWidth = old == null ? 0f : ui.widthScaled(old, scale) + swatchRoom(before) + ARROW;
		float newWidth = now == null ? 0f : ui.boldWidthScaled(now, scale) + swatchRoom(change.after());
		float values = oldWidth + newWidth;
		float labelRoom = values == 0f ? room : Math.max(room / 3f, room - values - VALUE_GAP);
		ui.textAtScaled(ui.fitScaled(label, labelRoom, scale), lineX, lineY, scale, palette.textSecondary());
		float valueX = lineX + Math.min(labelWidth, labelRoom) + VALUE_GAP;
		float left = lineX + room - valueX;
		int count = (old == null ? 0 : 1) + (now == null ? 0 : 1);
		float each = count == 0 ? 0f : (left - (old == null ? 0 : ARROW)) / count;
		if (old != null) {
			valueX += swatch(ui, before, valueX, lineY);
			String shown = ui.fitScaled(old, each - swatchRoom(before), scale);
			float width = ui.widthScaled(shown, scale);
			int oldColor = Ui.withOpacity(palette.textSecondary(), 0.75f);
			ui.textAtScaled(shown, valueX, lineY, scale, oldColor);
			ui.fill(Math.round(valueX), lineY + STRIKE_Y, Math.round(width), 1, Ui.withOpacity(palette.textSecondary(), 0.6f));
			valueX += width;
			arrow(ui, Math.round(valueX), lineY + STRIKE_Y, Ui.withOpacity(palette.textSecondary(), 0.75f));
			valueX += ARROW;
		}
		if (now != null) {
			valueX += swatch(ui, change.after(), valueX, lineY);
			ui.textAtScaledBold(ui.fitScaled(now, each - swatchRoom(change.after()), scale), valueX, lineY, scale, palette.text());
		}
	}

	private static int swatchRoom(RecentChanges.Value value) {
		return value.swatch() < 0 ? 0 : SWATCH + SWATCH_GAP;
	}

	/** Draws the small color before a value, if it has one. @return the room it takes */
	private static int swatch(Ui ui, RecentChanges.Value value, float x, int lineY) {
		if (value.swatch() < 0) {
			return 0;
		}
		int sx = Math.round(x);
		ui.rounded(sx, lineY, SWATCH, SWATCH, Radius.XXS, 0xFF000000 | value.swatch());
		ui.ring(sx, lineY, SWATCH, SWATCH, Radius.XXS, 1, Ui.withOpacity(0xFFFFFFFF, 0.25f));
		return SWATCH + SWATCH_GAP;
	}

	/** Draws an arrow pointing right, centered in its room: a short line with a chevron at its end. */
	private static void arrow(Ui ui, int x, int middle, int color) {
		ui.fill(x + 3, middle, 6, 1, color);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x + 8f, middle + 0.5f);
		pose.rotate((float) (-Math.PI / 2.0));
		ui.icon("chevron", -3, -3, 6, color);
		pose.popMatrix();
	}

	/** @return whether the click was on the card */
	boolean click(double mouseX, double mouseY) {
		List<RecentChanges.Change> changes = RecentChanges.list();
		if (!inside(mouseX, mouseY, cardX, cardY, cardWidth, height())) {
			return false;
		}
		if (!changes.isEmpty() && inside(mouseX, mouseY, clearX, cardY + 4, clearWidth, HEAD - 6)) {
			UiSounds.click();
			RecentChanges.clear();
			expanded = false;
			return true;
		}
		int shown = shownCount();
		if (undoRow >= 0 && undoRow < shown && inside(mouseX, mouseY, undoX, undoY, undoWidth, UNDO_HEIGHT)) {
			undo(changes.get(undoRow));
			return true;
		}
		for (int i = 0; i < shown; i++) {
			if (inside(mouseX, mouseY, cardX + INSET, cardY + HEAD + i * ROW, cardWidth - 2 * INSET, ROW)) {
				UiSounds.click();
				MacScreen.reveal(changes.get(i).feature(), changes.get(i).setting());
				return true;
			}
		}
		if (hasMore() && inside(mouseX, mouseY, cardX + PAD, cardY + HEAD + shown * ROW, cardWidth - 2 * PAD, MORE_ROW)) {
			UiSounds.click();
			expanded = true;
			return true;
		}
		return false;
	}

	private void undo(RecentChanges.Change change) {
		if (!RecentChanges.undo(change)) {
			return;
		}
		UiSounds.click();
		String name = change.feature().nameText();
		String old = change.before() == null ? null : change.before().text();
		String text = change.toggle() ? name + (change.on() ? " is off again." : " is on again.")
			: change.setting().labelText() + (old == null ? " is back as it was." : " is back to " + old + ".");
		Toasts.info("Change undone", text);
		onChanged.run();
	}

	/** @return when a change was made, as the list says it: "Just now", "2 min ago", "3 h ago", "Yesterday", "3 days ago" */
	private static String age(long millis, long now) {
		long minutes = Math.max(0L, now - millis) / MINUTE_MILLIS;
		if (minutes < 1) {
			return "Just now";
		}
		if (minutes < MINUTES_PER_HOUR) {
			return minutes + " min ago";
		}
		ZoneId zone = ZoneId.systemDefault();
		long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), LocalDate.ofInstant(Instant.ofEpochMilli(now), zone));
		if (days < 1) {
			return minutes / MINUTES_PER_HOUR + " h ago";
		}
		return days == 1 ? "Yesterday" : days + " days ago";
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
