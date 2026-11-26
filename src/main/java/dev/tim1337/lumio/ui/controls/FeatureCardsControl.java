package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Features as cards in a grid, as the Dungeons page shows them: each card with the feature's name, its one line, its switch, and the one or two
 * settings it chose for its card right there; a click on the card's head opens all its settings in the pane.
 */
public final class FeatureCardsControl extends Control {
	private static final int GAP = 10;
	private static final int PAD = 10;
	private static final int HEAD = 36;
	private static final int ROW = 28;
	private static final int MIN_CARD_WIDTH = 200;
	private static final int MOST_COLUMNS = 3;
	/** The least room a setting's name keeps in its row, and the gap between it and its control. */
	private static final int MIN_LABEL = 50;
	private static final int LABEL_GAP = 8;
	/** The line over the cards that names them all, and the room it takes. */
	private static final int CAPTION = 16;

	private final List<Feature> features;
	private final Runnable onChanged;
	/** Each card's controls for its settings, made once. */
	private final List<List<Control>> controls = new ArrayList<>();
	private final Spring[] switches;
	private int columns = 1;
	private int cardWidth;
	private int[] cardTops = new int[0];
	private int[] cardHeights = new int[0];
	private final String caption;

	public FeatureCardsControl(List<Feature> features, Runnable onChanged) {
		this.features = features;
		this.onChanged = onChanged;
		switches = new Spring[features.size()];
		caption = String.join(" · ", features.stream().map(Feature::nameText).toList());
		for (int i = 0; i < features.size(); i++) {
			List<Control> own = new ArrayList<>();
			for (Setting<?> setting : features.get(i).cardSettings()) {
				own.add(setting.createControl(onChanged));
			}
			controls.add(own);
			switches[i] = Spring.bouncy(Spring.DEFAULT);
			switches[i].snap(features.get(i).isAnyOn() ? 1f : 0f);
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
		columns = Math.clamp((width + GAP) / (MIN_CARD_WIDTH + GAP), 1, MOST_COLUMNS);
		cardWidth = (width - (columns - 1) * GAP) / columns;
		int rowsOfCards = (features.size() + columns - 1) / columns;
		cardTops = new int[features.size()];
		cardHeights = new int[features.size()];
		int top = CAPTION;
		for (int row = 0; row < rowsOfCards; row++) {
			int tallest = 0;
			for (int column = 0; column < columns; column++) {
				int i = row * columns + column;
				if (i < features.size()) {
					tallest = Math.max(tallest, HEAD + visibleSettings(i) * ROW + 4);
				}
			}
			for (int column = 0; column < columns; column++) {
				int i = row * columns + column;
				if (i < features.size()) {
					cardTops[i] = top;
					cardHeights[i] = tallest;
				}
			}
			top += tallest + GAP;
		}
		return Math.max(0, top - GAP);
	}

	private int visibleSettings(int card) {
		int count = 0;
		for (Setting<?> setting : features.get(card).cardSettings()) {
			if (setting.isVisible()) {
				count++;
			}
		}
		return count;
	}

	private int cardX(int i) {
		return x + i % columns * (cardWidth + GAP);
	}

	private int switchX(int i) {
		return cardX(i) + cardWidth - PAD - SwitchControl.TRACK_WIDTH;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		ui.textAtScaled(ui.fitScaled(caption, width, TextScale.SMALL), x + 2, y + 2, TextScale.SMALL, ui.palette.textSecondary());
		for (int i = 0; i < features.size() && i < cardTops.length; i++) {
			drawCard(ui, i, mouseX, mouseY);
		}
	}

	private void drawCard(Ui ui, int i, int mouseX, int mouseY) {
		Feature feature = features.get(i);
		int left = cardX(i);
		int top = y + cardTops[i];
		int height = cardHeights[i];
		boolean overHead = mouseX >= left && mouseX < left + cardWidth && mouseY >= top && mouseY < top + HEAD;
		ui.rounded(left, top, cardWidth, height, Radius.LG, overHead ? Ui.mix(ui.palette.card(), 0xFFFFFFFF, 0.04f) : ui.palette.card());
		ui.ring(left, top, cardWidth, height, Radius.LG, 1, ui.palette.cardBorder());
		int textRoom = cardWidth - 2 * PAD - SwitchControl.TRACK_WIDTH - 8;
		ui.textAtScaledBold(ui.fit(feature.nameText(), textRoom), left + PAD, top + 8, 1f, ui.palette.text());
		ui.textAtScaled(ui.fitScaled(feature.summary(), textRoom, TextScale.SMALL), left + PAD, top + 20, TextScale.SMALL, ui.palette.textSecondary());
		if (feature.isSwitchable()) {
			float position = switches[i].follow(ui, feature.isAnyOn() ? 1f : 0f);
			SwitchControl.draw(ui, switchX(i), top + (HEAD - SwitchControl.TRACK_HEIGHT) / 2, position, 0f, 0f);
		}
		int rowY = top + HEAD;
		List<Setting<?>> settings = feature.cardSettings();
		List<Control> own = controls.get(i);
		for (int k = 0; k < settings.size(); k++) {
			Setting<?> setting = settings.get(k);
			if (!setting.isVisible()) {
				continue;
			}
			ui.fill(left + PAD, rowY, cardWidth - 2 * PAD, 1, ui.palette.cardBorder());
			Control control = own.get(k);
			// the control takes what it needs, up to most of the row; the label has the rest
			int available = cardWidth - 2 * PAD - MIN_LABEL;
			int wanted = Math.min(available, control.inlineWidth(ui, available));
			int labelRoom = cardWidth - 2 * PAD - wanted - LABEL_GAP;
			control.place(ui, left + cardWidth - PAD - wanted, rowY, wanted);
			control.place(ui, left + cardWidth - PAD - wanted, rowY + (ROW - control.height()) / 2, wanted);
			ui.textMiddle(ui.fit(setting.label().getString(), labelRoom), left + PAD, rowY + ROW / 2, ui.palette.text());
			control.renderSafely(ui, mouseX, mouseY);
			rowY += ROW;
		}
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		for (List<Control> own : controls) {
			for (Control control : own) {
				control.renderOverlay(ui, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean isOverlayOpen() {
		for (List<Control> own : controls) {
			for (Control control : own) {
				if (control.isOverlayOpen()) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		for (List<Control> own : controls) {
			for (Control control : own) {
				if (control.mouseDragged(mouseX, mouseY)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int i = 0; i < features.size() && i < cardTops.length; i++) {
			List<Setting<?>> settings = features.get(i).cardSettings();
			List<Control> own = controls.get(i);
			for (int k = 0; k < own.size(); k++) {
				if (settings.get(k).isVisible() && own.get(k).mouseClicked(mouseX, mouseY, button)) {
					return true;
				}
			}
		}
		if (button != 0) {
			return false;
		}
		for (int i = 0; i < features.size() && i < cardTops.length; i++) {
			int left = cardX(i);
			int top = y + cardTops[i];
			if (mouseX < left || mouseX >= left + cardWidth || mouseY < top || mouseY >= top + HEAD) {
				continue;
			}
			Feature feature = features.get(i);
			if (feature.isSwitchable() && mouseX >= switchX(i) - 4) {
				UiSounds.toggle(feature.toggle());
				onChanged.run();
			} else {
				UiSounds.click();
				MacScreen.openEdit(feature);
			}
			return true;
		}
		return false;
	}
}
