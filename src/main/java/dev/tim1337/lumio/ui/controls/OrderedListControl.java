package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.OrderedListSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor for an {@link OrderedListSetting}: every part as a row with a grip to drag it, its name, and a switch that shows or hides it. A dragged
 * row follows the mouse, slightly lifted, and the other rows slide aside as it passes them. A part with its own settings has an arrow; a click on
 * its row expands it and shows those settings below its name. Repeatable parts (a spacer) are added from pills above the list and have a bin
 * instead of a switch.
 */
public class OrderedListControl extends Control {
	private static final int ROW = 28;
	private static final int GAP = 4;
	private static final int CHIP_H = 22;
	private static final int CHIP_GAP = 6;
	/** Gap between the pills and the list. */
	private static final int SPLIT = 14;
	private static final int BIN = 22;
	/** Height of a setting row in an expanded part, and the margin above and below those rows. */
	private static final int OPTION_ROW = 26;
	private static final int OPTION_PAD = 4;
	/** Indent of the settings of an expanded part, aligned with the part's name. */
	private static final int OPTION_INDENT = 44;
	private static final int OPTION_RIGHT = 10;
	private static final int CHEVRON = 10;
	/** Distance the mouse has to move with a row held before it counts as a drag instead of a click. */
	private static final int DRAG_START = 3;
	private static final float SLIDE_RATE = Ui.MOVE_RATE;

	private final OrderedListSetting setting;
	private final Runnable onChanged;
	/** Animated y position of every row relative to the list top, by id. */
	private final Map<String, Float> shownY = new HashMap<>();
	/** Animated switch knob of every row, by id. */
	private final Map<String, Spring> knobs = new HashMap<>();
	/** How far every row is expanded, from 0 to 1, by id. */
	private final Map<String, Spring> opened = new HashMap<>();
	/** The control of every setting of a part, made when its row is first expanded. */
	private final Map<Setting<?>, Control> optionControls = new IdentityHashMap<>();
	/** Pill rectangles from the last layout (x, y, width), indexed like the repeatable parts. */
	private final List<int[]> chips = new ArrayList<>();
	private List<OrderedListSetting.Option> chipOptions = List.of();
	private int listTop;
	/** The expanded row, or null. */
	private String expanded;

	/** The held row: its id, where it was grabbed, and whether it moved far enough to count as a drag instead of a click. */
	private String held;
	private double pressY;
	private double grabOffset;
	private boolean dragging;
	private double mouseYNow;

	public OrderedListControl(OrderedListSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	private static int chipWidth(Ui ui, OrderedListSetting.Option option) {
		return 8 + 10 + 4 + (option.icon() != null ? 14 : 0) + ui.width(option.title()) + 10;
	}

	/** Lays the pills of the repeatable parts out in rows of the control's width. @return their total height */
	private int placeChips(Ui ui, int width) {
		chips.clear();
		chipOptions = setting.repeatables();
		int cx = 0;
		int cy = 0;
		for (OrderedListSetting.Option option : chipOptions) {
			int w = Math.min(width, chipWidth(ui, option));
			if (cx > 0 && cx + w > width) {
				cx = 0;
				cy += CHIP_H + CHIP_GAP;
			}
			chips.add(new int[]{cx, cy, w});
			cx += w + CHIP_GAP;
		}
		return chipOptions.isEmpty() ? 0 : cy + CHIP_H;
	}

	/** @return the full height of a part's settings when its row is expanded, 0 for parts without settings */
	private static int optionsHeight(OrderedListSetting.Option option) {
		return option == null || option.settings().isEmpty() ? 0 : OPTION_PAD * 2 + option.settings().size() * OPTION_ROW;
	}

	private Spring spring(Map<String, Spring> springs, String id, float response) {
		Spring found = springs.get(id);
		if (found == null) {
			found = Spring.smooth(response);
			springs.put(id, found);
		}
		return found;
	}

	/** @return the height a row takes now, with its settings as far as it is expanded */
	private float rowHeight(String id) {
		Spring open = opened.get(id);
		float amount = open == null ? 0f : open.value();
		return ROW + optionsHeight(setting.option(id)) * amount;
	}

	@Override
	protected int measure(Ui ui, int width) {
		int top = placeChips(ui, width);
		listTop = top == 0 ? 0 : top + SPLIT;
		float rows = 0f;
		for (String id : setting.rows()) {
			// advanced here, once per frame, so the page grows with the expanding row
			OrderedListSetting.Option option = setting.option(id);
			if (option != null && !option.settings().isEmpty()) {
				Spring open = spring(opened, id, Spring.DEFAULT);
				if (ui.animated) {
					open.step(id.equals(expanded) ? 1f : 0f, ui.deltaSeconds);
				} else {
					open.snap(id.equals(expanded) ? 1f : 0f);
				}
			}
			rows += rowHeight(id) + GAP;
		}
		return listTop + Math.max(0, Math.round(rows) - GAP);
	}

	private int switchX() {
		return x + width - 8 - SwitchControl.TRACK_WIDTH;
	}

	private int binX() {
		return x + width - 6 - BIN;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		// pills that add a repeatable part
		for (int i = 0; i < chips.size() && i < chipOptions.size(); i++) {
			int[] chip = chips.get(i);
			OrderedListSetting.Option option = chipOptions.get(i);
			int cx = x + chip[0];
			int cy = y + chip[1];
			boolean over = mouseX >= cx && mouseX < cx + chip[2] && mouseY >= cy && mouseY < cy + CHIP_H;
			float h = hover(ui, 1 + i, over);
			ui.rounded(cx, cy, chip[2], CHIP_H, CHIP_H / 2f, Ui.mix(ui.palette.control(), ui.accent, 0.12f + 0.2f * h));
			ui.ring(cx, cy, chip[2], CHIP_H, CHIP_H / 2f, 1, Ui.withOpacity(ui.accent, 0.35f + 0.4f * h));
			int ix = cx + 8;
			ui.icon("plus", ix, cy + (CHIP_H - 10) / 2, 10, ui.accent);
			ix += 14;
			if (option.icon() != null) {
				ui.icon(option.icon(), ix, cy + (CHIP_H - 10) / 2, 10, Ui.withOpacity(ui.palette.text(), 0.75f));
				ix += 14;
			}
			ui.textMiddle(option.title(), ix, cy + CHIP_H / 2, ui.palette.text());
		}

		List<String> ids = setting.rows();
		int top = y + listTop;
		float target = 0f;
		for (String id : ids) {
			float rowSize = rowHeight(id);
			if (id.equals(held) && dragging) {
				target += rowSize + GAP;
				continue; // drawn last, above the others
			}
			// relative to the list top, so rows move with the page when it scrolls, and only animate when the order changes
			float offset = shownY.getOrDefault(id, target);
			offset = ui.animated ? offset + (target - offset) * (1f - (float) Math.exp(-ui.deltaSeconds * SLIDE_RATE)) : target;
			if (Math.abs(target - offset) < 0.3f) {
				offset = target;
			}
			shownY.put(id, offset);
			drawRow(ui, id, Math.round(top + offset), mouseX, mouseY, false);
			target += rowSize + GAP;
		}
		if (dragging && held != null && ids.contains(held)) {
			int listHeight = Math.max(ROW, Math.round(target) - GAP);
			int rowY = (int) Math.round(Math.max(top, Math.min(top + listHeight - ROW, mouseYNow - grabOffset)));
			shownY.put(held, (float) (rowY - top));
			drawRow(ui, held, rowY, mouseX, mouseY, true);
		}
	}

	private void drawRow(Ui ui, String id, int rowY, int mouseX, int mouseY, boolean lifted) {
		OrderedListSetting.Option option = setting.option(id);
		if (option == null) {
			return;
		}
		boolean on = setting.isOn(id);
		boolean space = option.repeatable();
		int extra = Math.round(rowHeight(id)) - ROW;
		int full = ROW + extra;
		boolean over = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW;
		if (lifted) {
			// dragged: shadow below and accent outline, like a card lifted off the list
			ui.shadow(x, rowY + 2, width, full, Radius.MD, 8, 0.35f);
		}
		int background = lifted ? Ui.mix(ui.palette.control(), ui.accent, 0.18f) : over ? Ui.mix(ui.palette.control(), 0xFFFFFFFF, 0.04f) : ui.palette.control();
		// a spacer is an empty line: drawn as a fainter row
		ui.rounded(x, rowY, width, full, Radius.MD, space && !lifted ? Ui.withOpacity(background, 0.55f) : background);
		if (lifted) {
			ui.ring(x, rowY, width, full, Radius.MD, 1, Ui.withOpacity(ui.accent, 0.8f));
		}
		// grip: three short lines signalling the row can be dragged
		int grip = Ui.withOpacity(ui.palette.textSecondary(), over || lifted ? 0.9f : 0.5f);
		for (int k = 0; k < 3; k++) {
			ui.rounded(x + 9, rowY + 9 + k * 3, 10, 1, Radius.pill(1), grip);
		}
		// a part that is switched off is listed dimmer
		float shownAmount = on || space ? 1f : 0.5f;
		int iconX = x + 26;
		if (option.icon() != null) {
			int iconColor = space ? Ui.withOpacity(ui.palette.textSecondary(), 0.7f) : Ui.withOpacity(ui.accent, shownAmount);
			ui.icon(option.icon(), iconX, rowY + (ROW - 12) / 2, 12, iconColor);
			iconX += 18;
		}
		int textColor = space ? ui.palette.textSecondary() : Ui.withOpacity(ui.palette.text(), 0.4f + 0.6f * shownAmount);
		ui.textMiddle(option.title(), iconX, rowY + ROW / 2, textColor);
		if (space) {
			// bin that removes the spacer, red when hovered
			int bx = binX();
			int by = rowY + (ROW - BIN) / 2;
			boolean overBin = !lifted && mouseX >= bx && mouseX < bx + BIN && mouseY >= by && mouseY < by + BIN;
			if (overBin) {
				ui.rounded(bx, by, BIN, BIN, BIN / 2f, Ui.withOpacity(SystemColors.RED, 0.18f));
			}
			ui.icon("trash", bx + (BIN - 12) / 2, by + (BIN - 12) / 2, 12, overBin ? SystemColors.RED : Ui.withOpacity(ui.palette.textSecondary(), over ? 0.9f : 0.55f));
		} else {
			int sx = switchX();
			int sy = rowY + (ROW - SwitchControl.TRACK_HEIGHT) / 2;
			boolean overSwitch = !lifted && mouseX >= sx && mouseX < sx + SwitchControl.TRACK_WIDTH && mouseY >= sy && mouseY < sy + SwitchControl.TRACK_HEIGHT;
			Spring knob = spring(knobs, id, Spring.DEFAULT);
			float position = ui.animated ? knob.step(on ? 1f : 0f, ui.deltaSeconds) : knob.snap(on ? 1f : 0f);
			SwitchControl.draw(ui, sx, sy, position, overSwitch ? 1f : 0f, 0f);
		}
		if (!option.settings().isEmpty()) {
			// arrow: the icon points down; turned to point right while closed
			Spring open = opened.get(id);
			float amount = open == null ? 0f : open.value();
			int cx = (space ? binX() : switchX()) - 10 - CHEVRON / 2;
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(cx, rowY + ROW / 2f);
			pose.rotate((float) (-Math.PI / 2.0 * (1f - amount)));
			ui.icon("chevron", -CHEVRON / 2, -CHEVRON / 2, CHEVRON, Ui.withOpacity(ui.palette.textSecondary(), over ? 0.95f : 0.6f));
			pose.popMatrix();
			if (extra > 0) {
				drawOptions(ui, option, rowY + ROW, extra, mouseX, mouseY, lifted);
			}
		}
	}

	/** Draws the settings of an expanded part below its name, revealed as far as the row is open. */
	private void drawOptions(Ui ui, OrderedListSetting.Option option, int top, int shown, int mouseX, int mouseY, boolean lifted) {
		ui.fill(x + OPTION_INDENT, top, width - OPTION_INDENT - OPTION_RIGHT, 1, Ui.withOpacity(ui.palette.textSecondary(), 0.18f));
		ui.graphics.enableScissor(x, top, x + width, top + shown);
		int rowY = top + OPTION_PAD;
		int right = x + width - OPTION_RIGHT;
		for (Setting<?> own : option.settings()) {
			Control control = controlOf(own);
			int inline = control.inlineWidth(ui, Math.max(40, right - x - OPTION_INDENT - 90));
			control.place(ui, right - inline, rowY + (OPTION_ROW - control.height()) / 2, inline);
			ui.textMiddle(own.labelText(), x + OPTION_INDENT, rowY + OPTION_ROW / 2, ui.palette.textSecondary());
			control.render(ui, lifted ? Integer.MIN_VALUE : mouseX, lifted ? Integer.MIN_VALUE : mouseY);
			rowY += OPTION_ROW;
		}
		ui.graphics.disableScissor();
	}

	private Control controlOf(Setting<?> own) {
		Control control = optionControls.get(own);
		if (control == null) {
			control = own.createControl(onChanged);
			optionControls.put(own, control);
		}
		return control;
	}

	/** The controls of the expanded part and the part they belong to, kept until another part is expanded: the menu asks every frame. */
	private List<Control> openList = List.of();
	private String openListFor;

	/** @return the controls of the expanded part, which receive input while it is open */
	private List<Control> openControls() {
		if (expanded == null) {
			return List.of();
		}
		if (!expanded.equals(openListFor)) {
			OrderedListSetting.Option option = setting.option(expanded);
			List<Control> out = new ArrayList<>();
			if (option != null) {
				for (Setting<?> own : option.settings()) {
					out.add(controlOf(own));
				}
			}
			openList = out;
			openListFor = expanded;
		}
		return openList;
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		for (Control control : openControls()) {
			if (control.isOverlayOpen()) {
				control.renderOverlay(ui, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean isOverlayOpen() {
		for (Control control : openControls()) {
			if (control.isOverlayOpen()) {
				return true;
			}
		}
		return false;
	}

	/** @return the index of the row at a y relative to the list top, by the rows' current heights */
	private int indexAt(double inList) {
		List<String> ids = setting.rows();
		float top = 0f;
		for (int i = 0; i < ids.size(); i++) {
			float bottom = top + rowHeight(ids.get(i)) + GAP;
			if (inList < bottom) {
				return i;
			}
			top = bottom;
		}
		return Math.max(0, ids.size() - 1);
	}

	/** @return the top of a row relative to the list top, by the rows' current heights */
	private float topOf(int index) {
		List<String> ids = setting.rows();
		float top = 0f;
		for (int i = 0; i < index && i < ids.size(); i++) {
			top += rowHeight(ids.get(i)) + GAP;
		}
		return top;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Control control : openControls()) {
			if (control.isOverlayOpen() && control.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
		}
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		// pill: appends a repeatable part
		for (int i = 0; i < chips.size() && i < chipOptions.size(); i++) {
			int[] chip = chips.get(i);
			if (mouseX >= x + chip[0] && mouseX < x + chip[0] + chip[2] && mouseY >= y + chip[1] && mouseY < y + chip[1] + CHIP_H) {
				setting.add(chipOptions.get(i));
				UiSounds.toggle(true);
				onChanged.run();
				return true;
			}
		}
		double inList = mouseY - (y + listTop);
		if (inList < 0) {
			return true;
		}
		List<String> ids = setting.rows();
		int index = indexAt(inList);
		if (index < 0 || index >= ids.size()) {
			return true;
		}
		String id = ids.get(index);
		double inRow = inList - topOf(index);
		if (inRow >= ROW) {
			// in the settings of the expanded part
			for (Control control : openControls()) {
				if (control.contains(mouseX, mouseY)) {
					control.mouseClicked(mouseX, mouseY, button);
					return true;
				}
			}
			return true;
		}
		OrderedListSetting.Option option = setting.option(id);
		if (option != null && option.repeatable() && mouseX >= binX() - 2) {
			// bin: remove the spacer; it can be added again from the pills
			setting.remove(id);
			shownY.remove(id);
			UiSounds.toggle(false);
			onChanged.run();
			return true;
		}
		if (option != null && !option.repeatable() && mouseX >= switchX() - 4) {
			boolean now = !setting.isOn(id);
			setting.setOn(id, now);
			UiSounds.toggle(now);
			onChanged.run();
			return true;
		}
		// anywhere else on the row grabs it: it is dragged once the mouse moves, otherwise the click expands it
		held = id;
		pressY = mouseY;
		mouseYNow = mouseY;
		grabOffset = mouseY - (y + listTop + topOf(index));
		dragging = false;
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		for (Control control : openControls()) {
			if (control.mouseDragged(mouseX, mouseY)) {
				return true;
			}
		}
		if (held == null) {
			return false;
		}
		mouseYNow = mouseY;
		if (!dragging && Math.abs(mouseY - pressY) > DRAG_START) {
			dragging = true;
			// an expanded row closes while it is dragged, so it moves as one line
			expanded = null;
			UiSounds.click();
		}
		if (dragging) {
			// swap places once the dragged row passes the middle of another
			List<String> ids = setting.rows();
			int from = ids.indexOf(held);
			int to = indexAt(mouseY - grabOffset - (y + listTop) + ROW / 2.0);
			if (from >= 0 && to != from) {
				setting.move(from, to);
				UiSounds.tick(to / (float) Math.max(1, ids.size() - 1));
				onChanged.run();
			}
		}
		return true;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		for (Control control : openControls()) {
			control.mouseReleased(mouseX, mouseY);
		}
		if (held != null && !dragging) {
			OrderedListSetting.Option option = setting.option(held);
			if (option != null && !option.settings().isEmpty()) {
				boolean open = !held.equals(expanded);
				expanded = open ? held : null;
				UiSounds.click();
			}
		}
		held = null;
		dragging = false;
	}

	/** A field in an expanded row (a slider's value being typed) has the keyboard: the page sends the keys here, which pass them on. */
	@Override
	public boolean wantsKeyboard() {
		for (Control control : openControls()) {
			if (control.wantsKeyboard()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		for (Control control : openControls()) {
			if (control.wantsKeyboard() && control.keyPressed(key, modifiers)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean charTyped(int codepoint) {
		for (Control control : openControls()) {
			if (control.wantsKeyboard() && control.charTyped(codepoint)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void loseFocus() {
		for (Control control : openControls()) {
			control.loseFocus();
		}
	}
}
