package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.SettingRows;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.config.setting.ButtonsRowSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * A card row with several buttons ({@link ButtonsRowSetting}): the title and a line under it on the left; on the right a small segmented choice and
 * the buttons, the main one in the accent, one that removes something in red. A highlighted row is tinted in the accent. A question asked by one of
 * its buttons opens as an opaque card under it, the page behind it dimmed: the title, what was found on its own lines, and under them a row with
 * the choice, Cancel and the confirming button; Esc or a click beside it cancels.
 */
public final class ButtonsRowControl extends Control {
	private static final int HEIGHT = 40;
	private static final int PAD = 10;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_PAD = 9;
	private static final int BUTTON_GAP = 6;
	private static final int SEGMENT_PAD = 6;
	private static final int SEGMENT_HEIGHT = 16;
	private static final int SUB_LINE = 10;
	private static final int MOST_SUB_LINES = 2;
	private static final int QUESTION_GAP = 6;
	private static final int QUESTION_TITLE = 13;
	private static final int QUESTION_ROW_GAP = 8;
	private static final int MOST_DETAIL_LINES = 3;
	/** The page behind an open question is dimmed, as behind the other confirm dialogs. */
	private static final float DIM = 0.45f;
	/** The dim reaches past the window on every side, whatever the menu's zoom. */
	private static final int DIM_REACH = 8192;
	private static final float HIGHLIGHT_FILL = 0.16f;
	/** How far a highlighted row's help line moves toward the text color. */
	private static final float HIGHLIGHT_SUB = 0.5f;

	/** The question waiting for an answer, and the row it shows under. */
	private static SettingRows.Question question;
	private static ButtonsRowControl asking;
	/** The row whose button runs right now: a question asked meanwhile is its. */
	private static ButtonsRowControl running;

	private final ButtonsRowSetting setting;
	private final Runnable onChanged;
	private final List<int[]> buttonBoxes = new ArrayList<>();
	private final List<SettingRows.Button> shownButtons = new ArrayList<>();
	private final List<int[]> segmentBoxes = new ArrayList<>();
	private int subLines = 1;
	/** Whether the buttons stand on a line of their own under the text: where beside it they would leave the text too little room. */
	private boolean stacked;
	/** The least room for the title and its line beside the buttons. */
	private static final int LEAST_TEXT_ROOM = 110;
	/** The line under the title, wrapped once per text and room; the question's lines once per question. */
	private String wrappedFor;
	private int wrappedRoom;
	private List<String> wrapped = List.of();
	private SettingRows.Question detailFor;
	private int detailRoom;
	private List<String> detailLines = List.of();
	private int option;
	/** The question card's parts, from the last frame. */
	private final int[] card = new int[4];
	private final List<int[]> optionBoxes = new ArrayList<>();
	private final int[] cancelBox = new int[4];
	private final int[] confirmBox = new int[4];

	public ButtonsRowControl(ButtonsRowSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	/** Asks a question under the row whose button is running now (or the last one that asked). */
	public static void ask(SettingRows.Question asked) {
		question = asked;
		if (running != null) {
			asking = running;
		}
		if (asking != null) {
			asking.option = Math.max(0, asked.option());
		}
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
		return HEIGHT + (subLines - 1) * SUB_LINE + (stacked ? BUTTON_HEIGHT + BUTTON_GAP : 0);
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		boolean highlighted = setting.highlighted();
		ui.rounded(x, y, width, height, Radius.LG, highlighted ? Ui.mix(palette.card(), ui.accent, HIGHLIGHT_FILL) : palette.card());
		ui.ring(x, y, width, height, Radius.LG, 1, highlighted ? ui.accent : palette.cardBorder());
		// the buttons beside the text, or on their own line under it where they would leave it too little room
		int controls = 0;
		for (SettingRows.Button button : setting.buttons()) {
			controls += Math.round(ui.widthScaled(button.label(), TextScale.BODY)) + 2 * BUTTON_PAD + BUTTON_GAP;
		}
		boolean stack = width - 2 * PAD - controls - 6 < LEAST_TEXT_ROOM && setting.inline() == null;
		if (stack != stacked) {
			stacked = stack;
			onChanged.run();
		}
		int textHeight = stacked ? height - BUTTON_HEIGHT - BUTTON_GAP : height;
		int middle = y + textHeight / 2;
		int buttonsMiddle = stacked ? y + height - PAD - BUTTON_HEIGHT / 2 : middle;
		int right = x + width - PAD;
		buttonBoxes.clear();
		shownButtons.clear();
		List<SettingRows.Button> buttons = setting.buttons();
		for (int i = buttons.size() - 1; i >= 0; i--) {
			SettingRows.Button button = buttons.get(i);
			int buttonWidth = Math.round(ui.widthScaled(button.label(), TextScale.BODY)) + 2 * BUTTON_PAD;
			int[] box = {right - buttonWidth, buttonsMiddle - BUTTON_HEIGHT / 2, buttonWidth, BUTTON_HEIGHT};
			drawButton(ui, box, button.label(), button.kind(), mouseX, mouseY);
			buttonBoxes.addFirst(box);
			shownButtons.addFirst(button);
			right = box[0] - BUTTON_GAP;
		}
		segmentBoxes.clear();
		EnumSetting<?> inline = setting.inline();
		if (inline != null) {
			right = drawSegments(ui, inline, right, middle, mouseX, mouseY) - BUTTON_GAP;
		}
		int textX = x + PAD;
		int room = stacked ? width - 2 * PAD : Math.max(40, right - textX - 6);
		String title = ui.fit(setting.labelText(), room);
		String sub = setting.subtext();
		if (!sub.equals(wrappedFor) || room != wrappedRoom) {
			wrappedFor = sub;
			wrappedRoom = room;
			wrapped = sub.isEmpty() ? List.of() : ui.wrapScaled(sub, room, TextScale.SMALL, MOST_SUB_LINES);
		}
		List<String> lines = wrapped;
		int wanted = Math.max(1, Math.min(MOST_SUB_LINES, lines.size()));
		if (wanted != subLines) {
			subLines = wanted;
			onChanged.run();
		}
		int textTop = lines.isEmpty() ? middle - 4 : middle - 4 - (Math.min(MOST_SUB_LINES, lines.size()) * SUB_LINE) / 2;
		ui.textAtScaledBold(title, textX, textTop, TextScale.BODY, palette.text());
		// on the tinted row of a highlighted one, the help line moves toward the text color, so it keeps its contrast there
		int subColor = setting.highlighted() ? Ui.mix(palette.textSecondary(), palette.text(), HIGHLIGHT_SUB) : palette.textSecondary();
		for (int i = 0; i < Math.min(MOST_SUB_LINES, lines.size()); i++) {
			ui.textAtScaled(lines.get(i), textX, textTop + 11 + i * SUB_LINE, TextScale.SMALL, subColor);
		}
	}

	private int drawSegments(Ui ui, EnumSetting<?> inline, int right, int middle, int mouseX, int mouseY) {
		List<?> choices = inline.choices();
		int total = 4;
		List<String> names = new ArrayList<>(choices.size());
		for (Object choice : choices) {
			String name = nameOf(inline, choice);
			names.add(name);
			total += Math.round(ui.widthScaled(name, TextScale.SMALL)) + 2 * SEGMENT_PAD;
		}
		int left = right - total;
		int top = middle - SEGMENT_HEIGHT / 2;
		ui.rounded(left, top, total, SEGMENT_HEIGHT, Radius.MD, Ui.withOpacity(ui.palette.text(), 0.07f));
		int cursor = left + 2;
		for (int i = 0; i < choices.size(); i++) {
			int part = Math.round(ui.widthScaled(names.get(i), TextScale.SMALL)) + 2 * SEGMENT_PAD;
			int[] box = {cursor, top + 2, part, SEGMENT_HEIGHT - 4};
			segmentBoxes.add(box);
			boolean on = choices.get(i) == inline.get();
			if (on) {
				ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, Ui.withOpacity(ui.palette.text(), 0.16f));
				ui.textAtScaledBold(names.get(i), cursor + SEGMENT_PAD, middle - 3.5f, TextScale.SMALL, ui.palette.text());
			} else {
				ui.textAtScaled(names.get(i), cursor + SEGMENT_PAD, middle - 3.5f, TextScale.SMALL, inside(box, mouseX, mouseY) ? ui.palette.text()
					: ui.palette.textSecondary());
			}
			cursor += part;
		}
		return left;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String nameOf(EnumSetting inline, Object choice) {
		return inline.nameOf((Enum) choice).getString();
	}

	private static void drawButton(Ui ui, int[] box, String label, SettingRows.Kind kind, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		boolean over = inside(box, mouseX, mouseY);
		int fill;
		int ring;
		int text;
		switch (kind) {
			case PRIMARY -> {
				fill = over ? Ui.mix(ui.accent, 0xFFFFFFFF, 0.1f) : ui.accent;
				ring = fill;
				text = 0xFFFFFFFF;
			}
			case DANGER -> {
				fill = Ui.withOpacity(SystemColors.RED, over ? 0.2f : 0.1f);
				ring = Ui.withOpacity(SystemColors.RED, 0.6f);
				text = palette.coloredText(SystemColors.RED_TEXT);
			}
			default -> {
				fill = over ? Ui.mix(palette.control(), palette.hover(), 1f) : palette.control();
				ring = Ui.withOpacity(palette.text(), 0.14f);
				text = palette.text();
			}
		}
		ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, fill);
		ui.ring(box[0], box[1], box[2], box[3], Radius.SM, 1, ring);
		ui.textAtScaled(label, box[0] + BUTTON_PAD, box[1] + box[3] / 2f - 4f, TextScale.BODY, text);
	}

	private static boolean inside(int[] box, double mouseX, double mouseY) {
		return Ui.over(mouseX, mouseY, box[0], box[1], box[2], box[3]);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isOverlayOpen()) {
			return clickQuestion(mouseX, mouseY);
		}
		if (button != ScreenInput.LEFT || !contains(mouseX, mouseY)) {
			return false;
		}
		for (int i = 0; i < buttonBoxes.size(); i++) {
			if (inside(buttonBoxes.get(i), mouseX, mouseY)) {
				UiSounds.click();
				running = this;
				try {
					shownButtons.get(i).action().run();
				} finally {
					running = null;
				}
				onChanged.run();
				return true;
			}
		}
		EnumSetting<?> inline = setting.inline();
		for (int i = 0; inline != null && i < segmentBoxes.size(); i++) {
			if (inside(segmentBoxes.get(i), mouseX, mouseY)) {
				choose(inline, inline.choices().get(i));
				UiSounds.click();
				onChanged.run();
				return true;
			}
		}
		return true;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void choose(EnumSetting inline, Object choice) {
		inline.set((Enum) choice);
	}

	// ------------------------------------------------------------------------------------------------ the question

	@Override
	public boolean isOverlayOpen() {
		return question != null && asking == this;
	}

	@Override
	public boolean wantsKeyboard() {
		return isOverlayOpen();
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		SettingRows.Question asked = question;
		if (asked == null) {
			return;
		}
		Palette palette = ui.palette;
		int inner = width - 2 * (PAD + 2);
		if (asked != detailFor || inner != detailRoom) {
			detailFor = asked;
			detailRoom = inner;
			detailLines = asked.detail().isEmpty() ? List.of() : ui.wrapScaled(asked.detail(), inner, TextScale.SMALL, MOST_DETAIL_LINES);
		}
		List<String> detail = detailLines;
		int lines = Math.min(MOST_DETAIL_LINES, detail.size());
		int confirmWidth = Math.round(ui.widthScaled(asked.confirm(), TextScale.BODY)) + 2 * BUTTON_PAD;
		int cancelWidth = Math.round(ui.widthScaled("Cancel", TextScale.BODY)) + 2 * BUTTON_PAD;
		int optionsWidth = 0;
		for (String name : asked.options()) {
			optionsWidth += Math.round(ui.widthScaled(name, TextScale.SMALL)) + 2 * SEGMENT_PAD;
		}
		optionsWidth += asked.options().isEmpty() ? 0 : 4;
		// the choice shares the buttons' row while it fits beside them; in a narrow sheet or with a wide font it gets a row of its own
		boolean ownRow = optionsWidth > 0 && optionsWidth + 2 * BUTTON_GAP + cancelWidth + BUTTON_GAP + confirmWidth > inner;
		card[0] = x;
		card[1] = y + height + QUESTION_GAP;
		card[2] = width;
		card[3] = PAD + QUESTION_TITLE + lines * SUB_LINE + QUESTION_ROW_GAP + (ownRow ? SEGMENT_HEIGHT + QUESTION_ROW_GAP : 0) + BUTTON_HEIGHT + PAD;
		ui.fill(-DIM_REACH, -DIM_REACH, 2 * DIM_REACH, 2 * DIM_REACH, Ui.withOpacity(0xFF000000, DIM));
		ui.shadow(card[0], card[1], card[2], card[3], Radius.LG, 10, 0.4f);
		ui.rounded(card[0], card[1], card[2], card[3], Radius.LG, palette.popup() | 0xFF000000);
		ui.ring(card[0], card[1], card[2], card[3], Radius.LG, 1, palette.popupBorder());
		int left = card[0] + PAD + 2;
		int top = card[1] + PAD;
		ui.textAtScaledBold(ui.fit(asked.title(), inner), left, top, TextScale.BODY, palette.text());
		for (int i = 0; i < lines; i++) {
			ui.textAtScaled(detail.get(i), left, top + QUESTION_TITLE + i * SUB_LINE, TextScale.SMALL, palette.textSecondary());
		}
		int rowTop = top + QUESTION_TITLE + lines * SUB_LINE + QUESTION_ROW_GAP;
		int buttonsTop = ownRow ? rowTop + SEGMENT_HEIGHT + QUESTION_ROW_GAP : rowTop;
		int right = card[0] + card[2] - PAD - 2;
		set(confirmBox, right - confirmWidth, buttonsTop, confirmWidth, BUTTON_HEIGHT);
		drawButton(ui, confirmBox, asked.confirm(), SettingRows.Kind.PRIMARY, mouseX, mouseY);
		set(cancelBox, confirmBox[0] - BUTTON_GAP - cancelWidth, buttonsTop, cancelWidth, BUTTON_HEIGHT);
		drawButton(ui, cancelBox, "Cancel", SettingRows.Kind.NORMAL, mouseX, mouseY);
		optionBoxes.clear();
		if (optionsWidth > 0) {
			int middle = ownRow ? rowTop + SEGMENT_HEIGHT / 2 : buttonsTop + BUTTON_HEIGHT / 2;
			int cursor = left + 2;
			ui.rounded(left, middle - SEGMENT_HEIGHT / 2, optionsWidth, SEGMENT_HEIGHT, Radius.MD, Ui.withOpacity(palette.text(), 0.07f));
			for (int i = 0; i < asked.options().size(); i++) {
				String name = asked.options().get(i);
				int part = Math.round(ui.widthScaled(name, TextScale.SMALL)) + 2 * SEGMENT_PAD;
				int[] box = {cursor, middle - SEGMENT_HEIGHT / 2 + 2, part, SEGMENT_HEIGHT - 4};
				optionBoxes.add(box);
				if (i == option) {
					ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, Ui.withOpacity(palette.text(), 0.16f));
					ui.textAtScaledBold(name, cursor + SEGMENT_PAD, middle - 3.5f, TextScale.SMALL, palette.text());
				} else {
					ui.textAtScaled(name, cursor + SEGMENT_PAD, middle - 3.5f, TextScale.SMALL, palette.textSecondary());
				}
				cursor += part;
			}
		}
	}

	private static void set(int[] box, int x, int y, int width, int height) {
		box[0] = x;
		box[1] = y;
		box[2] = width;
		box[3] = height;
	}

	private boolean clickQuestion(double mouseX, double mouseY) {
		SettingRows.Question asked = question;
		if (inside(confirmBox, mouseX, mouseY)) {
			close();
			UiSounds.click();
			asked.chosen().accept(option);
			onChanged.run();
			return true;
		}
		for (int i = 0; i < optionBoxes.size(); i++) {
			if (inside(optionBoxes.get(i), mouseX, mouseY)) {
				option = i;
				UiSounds.click();
				return true;
			}
		}
		if (inside(cancelBox, mouseX, mouseY) || !inside(card, mouseX, mouseY)) {
			close();
			UiSounds.click();
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!isOverlayOpen()) {
			return false;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			SettingRows.Question asked = question;
			close();
			asked.chosen().accept(option);
			onChanged.run();
		}
		return true;
	}

	/** Esc or leaving the page: the question goes unanswered, and nothing changes. */
	@Override
	public void loseFocus() {
		if (isOverlayOpen()) {
			close();
		}
	}

	private static void close() {
		question = null;
		asking = null;
	}
}
