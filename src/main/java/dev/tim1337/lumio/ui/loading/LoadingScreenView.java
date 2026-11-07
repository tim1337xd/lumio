package dev.tim1337.lumio.ui.loading;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Easing;
import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.render.RoundedImage;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the one loading screen ({@link LoadingSession}): the menu world blurred and darkened behind, the title in the middle, a step line
 * below it with the current step highlighted and the finished ones dimmed, a thin rounded bar in the accent color that fills with the game's
 * progress or shows a soft moving shimmer when there is none, and the server or world quietly at the bottom.
 */
public final class LoadingScreenView {
	private static final int BAR_WIDTH = 220;
	private static final int BAR_HEIGHT = 4;
	/** How dark the scene behind is. */
	private static final float SHADE = 0.42f;
	private static final int FALLBACK_TOP = 0xFF0B0E1C;
	private static final int FALLBACK_BOTTOM = 0xFF181C30;
	/** Width of the shimmer, as a share of the bar, and how long one pass takes, in seconds. */
	private static final float SHIMMER_WIDTH = 0.32f;
	private static final float SHIMMER_SECONDS = 1.6f;
	private static final String SEPARATOR = "  ·  ";

	private LoadingScreenView() {
	}

	/** @return the top of the buttons a loading screen offers (Cancel), below the bar */
	public static int buttonTop(int height) {
		return barY(height) + 26;
	}

	private static int titleY(int height) {
		return Math.round(height * 0.42f);
	}

	private static int barY(int height) {
		return titleY(height) + 46;
	}

	/** Draws the whole view, fully or fading. */
	public static void draw(GuiGraphicsExtractor graphics, float alpha) {
		Minecraft client = Minecraft.getInstance();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, MacTheme.DARK.palette(), ModFeatures.MENU.accentArgb(), 0f, true);
		float before = Ui.alpha();
		Ui.setAlpha(alpha);
		var background = MenuWorld.snapshot();
		if (background != null) {
			// render targets are upside down
			RoundedImage.draw(graphics, background, 0, 0, width, height, 0f, 0f, 1f, 1f, 0f, Ui.withOpacity(0xFFFFFFFF, alpha));
			ui.fill(0, 0, width, height, Ui.withOpacity(0xFF000000, SHADE));
		} else {
			ui.verticalGradient(0, 0, width, height, FALLBACK_TOP, FALLBACK_BOTTOM);
		}
		float barValue = LoadingSession.advanceBar();
		drawTitle(ui, width / 2f, titleY(height));
		drawSteps(ui, width / 2f, titleY(height) + 24);
		drawBar(ui, (width - BAR_WIDTH) / 2, barY(height), barValue);
		String detail = LoadingSession.detail();
		if (!detail.isEmpty()) {
			float scale = TextScale.SMALL;
			ui.textAtScaled(detail, (width - ui.widthScaled(detail, scale)) / 2f, height - 28, scale, 0x99FFFFFF);
		}
		Ui.setAlpha(before);
	}

	private static void drawTitle(Ui ui, float centerX, int y) {
		float change = Easing.smooth(LoadingSession.titleChange());
		float scale = TextScale.TITLE;
		String before = LoadingSession.titleBefore();
		if (change < 1f && !before.isEmpty()) {
			ui.textAtScaledBold(before, centerX - ui.widthScaled(before, scale) / 2f, y - 6f * change, scale, Ui.withOpacity(0xFFFFFFFF, 1f - change));
		}
		String title = LoadingSession.title();
		ui.textAtScaledBold(title, centerX - ui.widthScaled(title, scale) / 2f, y + 6f * (1f - change), scale, Ui.withOpacity(0xFFFFFFFF, change));
	}

	/** The steps on one line: finished ones dimmed, the current one bright, the coming ones in between. */
	private static void drawSteps(Ui ui, float centerX, int y) {
		int count = LoadingSession.stepCount();
		// a single step would only repeat the title
		if (count < 2) {
			return;
		}
		float scale = TextScale.SMALL;
		float separator = ui.widthScaled(SEPARATOR, scale);
		float total = (count - 1) * separator;
		for (int i = 0; i < count; i++) {
			total += ui.widthScaled(LoadingSession.step(i), scale);
		}
		int current = LoadingSession.currentStep();
		float change = Easing.smooth(LoadingSession.stepChange());
		float x = centerX - total / 2f;
		for (int i = 0; i < count; i++) {
			String step = LoadingSession.step(i);
			float bright = i < current ? 0.35f : i == current ? 1f : 0.55f;
			// the step that just became the current one brightens, the one before it dims
			if (i == current) {
				bright = 0.55f + 0.45f * change;
			} else if (i == current - 1) {
				bright = 1f - 0.65f * change;
			}
			ui.textAtScaled(step, x, y, scale, Ui.withOpacity(0xFFFFFFFF, bright));
			x += ui.widthScaled(step, scale);
			if (i < count - 1) {
				ui.textAtScaled(SEPARATOR, x, y, scale, 0x59FFFFFF);
				x += separator;
			}
		}
	}

	private static void drawBar(Ui ui, int x, int y, float value) {
		ui.rounded(x, y, BAR_WIDTH, BAR_HEIGHT, Radius.pill(BAR_HEIGHT), 0x29FFFFFF);
		float filled = BAR_WIDTH * value;
		if (filled > 0.5f) {
			ui.roundedSmooth(x, y, Math.max(BAR_HEIGHT, filled), BAR_HEIGHT, BAR_HEIGHT / 2f, ui.accent);
		}
		if (!LoadingSession.progressKnown()) {
			// no progress from the game: a soft light runs along the rest of the bar, one endless loop that never restarts between steps
			float phase = (LoadingSession.now() % SHIMMER_SECONDS) / SHIMMER_SECONDS;
			float start = filled + (BAR_WIDTH - filled) * (phase * (1f + SHIMMER_WIDTH) - SHIMMER_WIDTH);
			float room = (BAR_WIDTH - filled) * SHIMMER_WIDTH;
			float from = Math.max(filled, start);
			float to = Math.min(BAR_WIDTH, start + room);
			if (to - from > 0.5f) {
				int light = Ui.withOpacity(ui.accent, 0.55f);
				int mid = Math.round(x + (from + to) / 2f);
				ui.horizontalGradient(Math.round(x + from), y, mid - Math.round(x + from), BAR_HEIGHT, light & 0x00FFFFFF, light);
				ui.horizontalGradient(mid, y, Math.round(x + to) - mid, BAR_HEIGHT, light, light & 0x00FFFFFF);
			}
		}
	}
}
