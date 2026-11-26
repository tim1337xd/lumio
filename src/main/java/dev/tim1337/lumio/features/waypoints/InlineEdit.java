package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.ui.Ui;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;

/** A name edited where it is shown (a group's title, a point's row): Enter keeps it, Escape or a click elsewhere goes back. */
final class InlineEdit {
	private static final int CARET_BLINK_MILLIS = 530;
	private final TextSelection selection = new TextSelection();
	private String text = "";
	private int max;
	private Consumer<String> done;
	/** What is being edited, so the view knows where to draw the field: a group's id, or a point's index. */
	private Object target;

	boolean active() {
		return done != null;
	}

	boolean editing(Object what) {
		return done != null && target != null && target.equals(what);
	}

	void start(Object what, String current, int max, Consumer<String> done) {
		this.target = what;
		this.text = current;
		this.max = max;
		this.done = done;
		selection.selectAll();
	}

	void cancel() {
		done = null;
		target = null;
		selection.clear();
	}

	private void commit() {
		Consumer<String> keep = done;
		String kept = text;
		cancel();
		if (keep != null && !kept.isBlank()) {
			keep.accept(kept);
		}
	}

	/** Draws the field over the name's place, with the text, its selection and a blinking caret. */
	void draw(Ui ui, int x, int y, int width, int height) {
		ui.rounded(x - 3, y, width + 6, height, Radius.XS, ui.palette.control());
		ui.ring(x - 3, y, width + 6, height, Radius.XS, 1, ui.accent);
		String shown = ui.fit(text, width - 4);
		int middle = y + height / 2;
		selection.draw(ui, x, middle, ui.width(shown));
		ui.textMiddle(shown, x, middle, ui.palette.text());
		if (!selection.all() && System.currentTimeMillis() / CARET_BLINK_MILLIS % 2 == 0) {
			ui.fill(x + ui.width(shown) + 1, middle - 5, 1, 10, ui.palette.text());
		}
	}

	boolean keyPressed(int key, int modifiers) {
		if (!active()) {
			return false;
		}
		if (selection.key(key, modifiers, () -> text, value -> text = value, max)) {
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE && !text.isEmpty()) {
			text = text.substring(0, text.length() - 1);
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			commit();
		} else if (key == InputConstants.KEY_ESCAPE) {
			cancel();
		}
		return true;
	}

	boolean charTyped(int codepoint) {
		if (!active() || codepoint < 32) {
			return false;
		}
		String next = selection.typed(text, new String(Character.toChars(codepoint)));
		if (next.length() <= max) {
			text = next;
		}
		return true;
	}
}
