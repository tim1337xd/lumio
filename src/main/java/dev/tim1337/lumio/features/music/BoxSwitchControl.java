package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.controls.SwitchControl;

import java.util.function.IntSupplier;

/**
 * The switch of the highlight box with a tiny sample before it: "sung now next", the middle word on the box in the lyrics color while it is on,
 * the word before lit and the one after dim.
 */
final class BoxSwitchControl extends Control {
	private static final String SUNG = "sung";
	private static final String NOW = "now";
	private static final String NEXT = "next";
	private static final int WORD_GAP = 4;
	private static final int SAMPLE_GAP = 14;
	private static final float BOX_PAD = 3f;
	private static final float BOX_HEIGHT = 11f;
	private static final float DIM = 0.45f;

	private final BooleanSetting setting;
	private final SwitchControl toggle;
	private final IntSupplier color;
	private int sampleWidth = -1;

	BoxSwitchControl(BooleanSetting setting, Runnable onChanged, IntSupplier color) {
		this.setting = setting;
		this.toggle = new SwitchControl(setting, onChanged);
		this.color = color;
	}

	@Override
	protected int measure(Ui ui, int width) {
		toggle.place(ui, x + width - SwitchControl.TRACK_WIDTH, y, SwitchControl.TRACK_WIDTH);
		return toggle.height();
	}

	@Override
	public boolean keepsWidth() {
		return true;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		if (sampleWidth < 0) {
			sampleWidth = Math.round(ui.boldWidthScaled(SUNG + NOW + NEXT, TextScale.SMALL)) + 2 * WORD_GAP + 2 * Math.round(BOX_PAD);
		}
		return sampleWidth + SAMPLE_GAP + SwitchControl.TRACK_WIDTH;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		float middle = y + height / 2f;
		float textY = middle - 3f;
		float at = x;
		int text = ui.palette.text();
		ui.textAtScaledBold(SUNG, at, textY, TextScale.SMALL, text);
		at += ui.boldWidthScaled(SUNG, TextScale.SMALL) + WORD_GAP + BOX_PAD;
		float nowWidth = ui.boldWidthScaled(NOW, TextScale.SMALL);
		int nowColor = text;
		if (setting.get()) {
			int box = color.getAsInt();
			ui.roundedSmooth(at - BOX_PAD, middle - BOX_HEIGHT / 2f, nowWidth + 2 * BOX_PAD, BOX_HEIGHT, 3f, box);
			nowColor = HighlightBox.textOn(box);
		}
		ui.textAtScaledBold(NOW, at, textY, TextScale.SMALL, nowColor);
		at += nowWidth + BOX_PAD + WORD_GAP;
		ui.textAtScaledBold(NEXT, at, textY, TextScale.SMALL, Ui.withOpacity(text, DIM));
		toggle.render(ui, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return toggle.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		return toggle.mouseDragged(mouseX, mouseY);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		toggle.mouseReleased(mouseX, mouseY);
	}
}
