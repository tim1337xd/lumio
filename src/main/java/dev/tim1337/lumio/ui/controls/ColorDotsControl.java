package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.config.setting.ColorDotsSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/**
 * Color dots in a row, one per option in its color right now; the chosen one sits in a ring. The custom option is a "+" in a ring until it is
 * chosen, then a dot in the custom color. Hovering a dot grows it a little.
 */
public class ColorDotsControl<E extends Enum<E>> extends Control {
	private static final int DOT = 14;
	private static final int GAP = 8;
	/** The ring around the chosen dot: its distance from the dot and its width. */
	private static final int RING_GAP = 2;
	private static final int RING = 1;
	private static final float HOVER_GROW = 1.5f;
	private static final int PLUS = 6;
	/** Each dot's cell, which is what can be clicked; and the room the ring needs around the row. */
	private static final int CELL = DOT + GAP;
	private static final int LINE = DOT + 2 * (RING_GAP + RING);
	private static final int ROW_PAD = RING_GAP + RING - GAP / 2;

	private final ColorDotsSetting<E> setting;
	private final Runnable onChanged;
	private final List<E> choices;

	public ColorDotsControl(ColorDotsSetting<E> setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
		this.choices = setting.choices();
	}

	@Override
	protected int measure(Ui ui, int width) {
		return SwatchRow.lines(choices.size(), width - 2 * ROW_PAD, CELL) * LINE;
	}

	@Override
	public boolean keepsWidth() {
		return true;
	}

	@Override
	public boolean leftOnOwnLine() {
		return true;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, SwatchRow.fullWidth(choices.size(), CELL) + 2 * ROW_PAD);
	}

	private int dotX(int index) {
		return x + ROW_PAD + GAP / 2 + SwatchRow.cellX(index, width - 2 * ROW_PAD, CELL);
	}

	private int dotMiddle(int index) {
		return y + LINE / 2 + SwatchRow.line(index, width - 2 * ROW_PAD, CELL) * LINE;
	}

	/** @return the dot under the mouse, or -1 */
	private int dotAt(double mouseX, double mouseY) {
		return SwatchRow.cellAt((int) Math.floor(mouseX) - x - ROW_PAD, (int) Math.floor(mouseY) - y, choices.size(), width - 2 * ROW_PAD, CELL, LINE);
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		E chosen = setting.get();
		int under = dotAt(mouseX, mouseY);
		for (int i = 0; i < choices.size(); i++) {
			E option = choices.get(i);
			int dx = dotX(i);
			int cx = dx + DOT / 2;
			int middle = dotMiddle(i);
			float hover = hover(ui, i, under == i);
			float radius = DOT / 2f + HOVER_GROW * hover;
			boolean isChosen = option == chosen;
			if (isChosen) {
				ui.ring(Math.round(cx - radius - RING_GAP - RING), Math.round(middle - radius - RING_GAP - RING), Math.round(2 * (radius + RING_GAP + RING)),
					Math.round(2 * (radius + RING_GAP + RING)), radius + RING_GAP + RING, RING, palette.text());
			}
			int size = Math.round(2 * radius);
			int left = cx - size / 2;
			int top = middle - size / 2;
			if (option == setting.custom() && !isChosen) {
				ui.ring(left, top, size, size, size / 2f, RING, Ui.withOpacity(palette.text(), 0.5f));
				ui.fill(cx - PLUS / 2, middle, PLUS, 1, palette.textSecondary());
				ui.fill(cx, middle - PLUS / 2, 1, PLUS, palette.textSecondary());
				continue;
			}
			ui.rounded(left, top, size, size, size / 2f, 0xFF000000 | setting.colorOf(option));
			// a dark dot keeps an edge on a dark card
			ui.ring(left, top, size, size, size / 2f, RING, Ui.withOpacity(palette.text(), 0.18f));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		int dot = dotAt(mouseX, mouseY);
		if (dot < 0) {
			return false;
		}
		if (setting.get() != choices.get(dot)) {
			setting.set(choices.get(dot));
			UiSounds.click();
			onChanged.run();
		}
		return true;
	}
}
