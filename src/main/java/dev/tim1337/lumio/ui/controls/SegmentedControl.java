package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/**
 * Choices side by side in one bar, with a pill behind the selected one that slides when the selection changes. Each choice is as wide as its text
 * in bold, as the selected one is drawn, so nothing moves when the selection changes. Choices that do not fit turn into a dropdown instead of
 * being squeezed.
 */
public class SegmentedControl<E extends Enum<E>> extends Control {
	private static final int HEIGHT = 22;
	private static final int PADDING = 8;
	private static final int LEAST_PADDING = 4;
	private static final int INSET = 2;

	private final EnumSetting<E> setting;
	private final Runnable onChanged;
	private final List<E> choices;
	private final int[] textWidths;
	private final int[] edges;
	private final DropdownControl<E> dropdown;
	/** Whether the choices did not fit, so the dropdown stands in. */
	private boolean collapsed;
	/** Pill position as a choice index; fractional while it moves. */
	private float pill;
	private final Spring pillSpring = Spring.bouncy(Spring.DEFAULT);
	private boolean pillKnown;
	private boolean edgesKnown;

	public SegmentedControl(EnumSetting<E> setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
		this.choices = setting.choices();
		this.textWidths = new int[choices.size()];
		this.edges = new int[choices.size() + 1];
		this.dropdown = new DropdownControl<>(setting, onChanged);
	}

	private void measureTexts(Ui ui) {
		for (int i = 0; i < textWidths.length; i++) {
			textWidths[i] = (int) Math.ceil(ui.boldWidthScaled(setting.nameOf(choices.get(i)).getString(), TextScale.BODY));
		}
	}

	/** @return whether the choices did not fit, so a dropdown stands in */
	public boolean collapsed() {
		return collapsed;
	}

	@Override
	protected int measure(Ui ui, int width) {
		measureTexts(ui);
		boolean wasCollapsed = collapsed;
		collapsed = !SegmentFit.fits(width, textWidths, INSET, LEAST_PADDING);
		if (collapsed) {
			dropdown.place(ui, x, y, width);
		} else if (wasCollapsed) {
			dropdown.loseFocus();
		}
		return HEIGHT;
	}

	@Override
	public boolean keepsWidth() {
		return true;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		measureTexts(ui);
		return SegmentFit.wanted(available, SegmentFit.width(textWidths, INSET, PADDING), SegmentFit.width(textWidths, INSET, LEAST_PADDING),
			dropdown.inlineWidth(ui, available));
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		if (collapsed) {
			dropdown.render(ui, mouseX, mouseY);
			return;
		}
		measureTexts(ui);
		SegmentFit.edges(x, width, textWidths, INSET, edges);
		edgesKnown = true;
		int selected = choices.indexOf(setting.get());

		ui.rounded(x, y, width, height, Radius.SM, ui.palette.control());
		ui.ring(x, y, width, height, Radius.SM, 1, ui.palette.controlBorder());

		if (!pillKnown) {
			pillSpring.snap(selected);
			pillKnown = true;
		}
		// the pill springs to the selected choice; a new selection mid-flight continues from its current position and velocity
		pill = pillSpring.follow(ui, selected);
		int from = Math.round(edgeAt(edges, pill, false));
		int to = Math.round(edgeAt(edges, pill, true));
		ui.shadow(from, y + 2, to - from, height - 4, Radius.SM, 2, 0.14f);
		ui.rounded(from, y + 2, to - from, height - 4, Radius.SM, ui.palette.pill());

		int middleY = y + height / 2;
		for (int i = 0; i < choices.size(); i++) {
			String name = setting.nameOf(choices.get(i)).getString();
			float h = hover(ui, i, mouseX >= edges[i] && mouseX < edges[i + 1] && mouseY >= y && mouseY < y + height);
			if (i == selected) {
				ui.textCenteredBold(name, (edges[i] + edges[i + 1]) / 2f, middleY, ui.palette.onPill());
			} else {
				ui.textCentered(name, (edges[i] + edges[i + 1]) / 2, middleY, Ui.mix(ui.palette.textSecondary(), ui.palette.text(), h));
			}
		}
	}

	/** @return the left or right pill edge at a position between choices */
	private static float edgeAt(int[] edges, float position, boolean right) {
		// the spring overshoots the first and last choice, so the position can be outside of them
		int last = Math.max(0, edges.length - 2);
		int lower = Math.max(0, Math.min((int) Math.floor(position), last));
		int upper = Math.min(lower + 1, last);
		float fraction = Math.max(-1f, Math.min(position - lower, 1f));
		int offset = right ? 1 : 0;
		return edges[lower + offset] + (edges[upper + offset] - edges[lower + offset]) * fraction;
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (collapsed) {
			dropdown.renderOverlay(ui, mouseX, mouseY);
		}
	}

	@Override
	public boolean isOverlayOpen() {
		return collapsed && dropdown.isOverlayOpen();
	}

	@Override
	public void overlayScrolled(double amount) {
		if (collapsed) {
			dropdown.overlayScrolled(amount);
		}
	}

	@Override
	public void loseFocus() {
		dropdown.loseFocus();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (collapsed) {
			return dropdown.mouseClicked(mouseX, mouseY, button);
		}
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		// use the edges of the last frame, the only time they were measured with the font
		for (int i = 0; edgesKnown && i < choices.size(); i++) {
			if (mouseX < edges[i + 1]) {
				if (choices.get(i) != setting.get()) {
					setting.set(choices.get(i));
					UiSounds.tick((float) i / Math.max(1, choices.size() - 1));
					onChanged.run();
				}
				break;
			}
		}
		return true;
	}
}
