package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.render.WorldBackdrop;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.Ui;

/** A live preview spanning the page's width, on the preview backdrop the page previews use, clipped to its rounded panel. */
public class PreviewControl extends Control {
	private final PagePreview preview;
	private final int previewHeight;

	public PreviewControl(PagePreview preview, int previewHeight) {
		this.preview = preview;
		this.previewHeight = previewHeight;
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
	protected int measure(Ui ui, int width) {
		return previewHeight;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		WorldBackdrop.draw(ui, x, y, width, height, Radius.LG);
		ui.graphics.enableScissor(x, y, x + width, y + height);
		preview.draw(ui, x, y, width, height);
		ui.graphics.disableScissor();
		ui.ring(x, y, width, height, Radius.LG, 1, ui.palette.cardBorder());
	}
}
