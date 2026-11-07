package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.function.Supplier;

/**
 * A dashed card with a link icon, a note in which the other page's name is bold, and a link on the right that opens that page, as at the end
 * of the Mining page in the mockups.
 */
public class PageLinkControl extends Control {
	private static final int HEIGHT = 34;
	private static final int PAD = 12;
	private static final int ICON = 12;
	private static final int ICON_GAP = 8;
	/** How far the link's accent color goes towards the text color, so it reads on glass in both themes, and further on hover. */
	private static final float LINK_TEXT = 0.3f;
	private static final float LINK_HOVER = 0.3f;

	private final String before;
	private final String bold;
	private final String after;
	private final String link;
	private final Supplier<Feature> target;
	/** Where the link starts, from the last frame, for the clicks. */
	private float linkX = Float.MAX_VALUE;

	public PageLinkControl(String text, String bold, String link, Supplier<Feature> target) {
		int at = text.indexOf(bold);
		this.before = at < 0 ? text : text.substring(0, at);
		this.bold = at < 0 ? "" : bold;
		this.after = at < 0 ? "" : text.substring(at + bold.length());
		this.link = link;
		this.target = target;
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
		return HEIGHT;
	}

	private boolean overLink(double mouseX, double mouseY) {
		return mouseX >= linkX - 4 && mouseX < x + width - PAD + 4 && mouseY >= y && mouseY < y + height;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		ui.rounded(x, y, width, height, Radius.LG, Ui.withOpacity(palette.text(), 0.03f));
		ui.dashedRounded(x, y, width, height, Radius.LG, Ui.withOpacity(palette.text(), 0.12f));
		int middle = y + height / 2;
		ui.icon("link", x + PAD, middle - ICON / 2, ICON, palette.textSecondary());
		float textY = middle - 4f;
		float textX = x + PAD + ICON + ICON_GAP;
		linkX = x + width - PAD - ui.widthScaled(link, TextScale.SMALL);
		float room = linkX - 10 - textX;
		// the note in three parts so the page's name is bold; on a narrow page the note is cut instead of running under the link
		float beforeWidth = ui.widthScaled(before, TextScale.SMALL);
		float boldWidth = ui.boldWidthScaled(bold, TextScale.SMALL);
		if (beforeWidth + boldWidth + ui.widthScaled(after, TextScale.SMALL) <= room) {
			ui.textAtScaled(before, textX, textY, TextScale.SMALL, palette.textSecondary());
			ui.textAtScaledBold(bold, textX + beforeWidth, textY, TextScale.SMALL, palette.text());
			ui.textAtScaled(after, textX + beforeWidth + boldWidth, textY, TextScale.SMALL, palette.textSecondary());
		} else {
			ui.textAtScaled(ui.fitScaled(before + bold + after, room, TextScale.SMALL), textX, textY, TextScale.SMALL, palette.textSecondary());
		}
		float hover = hover(ui, 0, overLink(mouseX, mouseY));
		ui.textAtScaled(link, linkX, textY, TextScale.SMALL, Ui.mix(ui.accent, palette.text(), LINK_TEXT + LINK_HOVER * hover));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Feature page = target.get();
		if (button != 0 || page == null || !overLink(mouseX, mouseY)) {
			return false;
		}
		UiSounds.click();
		MacScreen.showPage(page);
		return true;
	}
}
