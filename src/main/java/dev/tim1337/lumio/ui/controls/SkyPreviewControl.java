package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.features.visual.world.SkyFeature;
import dev.tim1337.lumio.render.WorldBackdrop;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

/**
 * The Sky page's live preview: the world behind the menu as it is drawn right now, with a "LIVE" mark, a line on the chosen style, and chips that show
 * the sky by day, by night or in the rain while the page is open (Auto is the game's own time and weather).
 */
public final class SkyPreviewControl extends Control {
	private static final float ASPECT = 0.5f;
	private static final int MIN_HEIGHT = 110;
	private static final int MAX_HEIGHT = 190;
	private static final int PAD = 8;
	private static final int PILL_HEIGHT = 16;
	private static final int PILL_PAD = 7;
	private static final int DOT = 3;
	private static final int CHIP_GAP = 2;
	private static final int BACKING = 0xB0101014;
	private static final int TEXT = 0xFFF2F2F7;
	private static final int TEXT_SOFT = 0xB3F2F2F7;
	private static final String LIVE = "LIVE";

	private final SkyFeature sky;
	private final SkyFeature.PreviewLook[] looks = SkyFeature.PreviewLook.values();
	/** The chips' left edges and the right edge of the last, from the last frame, for clicks. */
	private final int[] chipEdges = new int[looks.length + 1];
	private int chipTop;
	private boolean chipsKnown;

	public SkyPreviewControl(SkyFeature sky) {
		this.sky = sky;
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
		return Math.clamp(Math.round(width * ASPECT), MIN_HEIGHT, MAX_HEIGHT);
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		WorldBackdrop.draw(ui, x, y, width, height, Radius.LG);
		// the LIVE mark
		int liveWidth = Math.round(ui.boldWidthScaled(LIVE, TextScale.SMALL)) + 2 * PILL_PAD + DOT * 2 + 4;
		ui.rounded(x + PAD, y + PAD, liveWidth, PILL_HEIGHT, Radius.box(PILL_HEIGHT), BACKING);
		ui.circle(x + PAD + PILL_PAD + DOT, y + PAD + PILL_HEIGHT / 2, DOT, SystemColors.GREEN);
		ui.textAtScaledBold(LIVE, x + PAD + PILL_PAD + DOT * 2 + 4, y + PAD + PILL_HEIGHT / 2f - 3.5f, TextScale.SMALL, TEXT);
		// the chips, at the bottom right
		int chipsWidth = 0;
		for (SkyFeature.PreviewLook look : looks) {
			chipsWidth += Math.round(ui.boldWidthScaled(look.label(), TextScale.SMALL)) + 2 * PILL_PAD + CHIP_GAP;
		}
		chipsWidth -= CHIP_GAP;
		chipTop = y + height - PAD - PILL_HEIGHT;
		int chipX = x + width - PAD - chipsWidth;
		ui.rounded(chipX - 2, chipTop - 2, chipsWidth + 4, PILL_HEIGHT + 4, (PILL_HEIGHT + 4) / 2f, BACKING);
		for (int i = 0; i < looks.length; i++) {
			SkyFeature.PreviewLook look = looks[i];
			int chipWidth = Math.round(ui.boldWidthScaled(look.label(), TextScale.SMALL)) + 2 * PILL_PAD;
			chipEdges[i] = chipX;
			boolean chosen = sky.previewLook() == look;
			boolean hovered = mouseX >= chipX && mouseX < chipX + chipWidth && mouseY >= chipTop && mouseY < chipTop + PILL_HEIGHT;
			if (chosen || hovered) {
				ui.rounded(chipX, chipTop, chipWidth, PILL_HEIGHT, Radius.box(PILL_HEIGHT), chosen ? 0x40FFFFFF : 0x1AFFFFFF);
			}
			ui.textAtScaledBold(look.label(), chipX + PILL_PAD, chipTop + PILL_HEIGHT / 2f - 3.5f, TextScale.SMALL, chosen ? TEXT : TEXT_SOFT);
			chipX += chipWidth + CHIP_GAP;
		}
		chipEdges[looks.length] = chipX - CHIP_GAP;
		chipsKnown = true;
		// the line on the style, at the bottom left beside the chips; where they leave it too little room, at the top beside the LIVE mark, or under
		// it: never cut, never over another text
		String caption = sky.styleCaption();
		int captionWidth = Math.round(ui.widthScaled(caption, TextScale.SMALL)) + 2 * PILL_PAD;
		int captionX = x + PAD;
		int captionTop;
		if (captionWidth <= chipEdges[0] - x - 3 * PAD) {
			captionTop = chipTop;
		} else if (captionWidth <= width - liveWidth - 3 * PAD) {
			captionX = x + PAD + liveWidth + PAD;
			captionTop = y + PAD;
		} else {
			captionTop = y + PAD + PILL_HEIGHT + PAD;
		}
		ui.rounded(captionX, captionTop - 2, captionWidth, PILL_HEIGHT + 4, Radius.SM, BACKING);
		ui.textAtScaled(caption, captionX + PILL_PAD, captionTop + PILL_HEIGHT / 2f - 3.5f, TextScale.SMALL, TEXT);
		ui.ring(x, y, width, height, Radius.LG, 1, ui.palette.cardBorder());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !chipsKnown || mouseY < chipTop || mouseY >= chipTop + PILL_HEIGHT) {
			return false;
		}
		for (int i = 0; i < looks.length; i++) {
			if (mouseX >= chipEdges[i] && mouseX < (i + 1 < looks.length ? chipEdges[i + 1] : chipEdges[looks.length])) {
				if (sky.previewLook() != looks[i]) {
					sky.previewLook(looks[i]);
					UiSounds.tick((float) i / Math.max(1, looks.length - 1));
				}
				return true;
			}
		}
		return false;
	}
}
