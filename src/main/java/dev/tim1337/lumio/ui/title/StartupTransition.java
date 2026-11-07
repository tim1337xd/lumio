package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.api.experimental.motion.Easing;
import dev.tim1337.lumio.render.MenuWarmup;
import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.render.ScreenFade;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.motion.CameraPath;
import dev.tim1337.lumio.ui.motion.ProgressSmoother;
import dev.tim1337.lumio.util.FrameTimes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The way from the game's Mojang screen into the main menu, once per game start, as one continuous motion. While the game loads, the Mojang
 * screen stays the game's own, only its bar moves smoothly: it follows the real progress, never jumps and never goes back. When loading is done
 * the mod draws the same screen over the main menu, the bar glides on to its end, and as it gets there the logo and bar fade and the red
 * background contracts upwards into a large soft glow, like a setting sun, through which the menu world's sky shows, and melts into it.
 * Meanwhile the camera comes down from high up, looking at the sky, to its resting place, and in its last half second the menu's parts come in.
 * The phases overlap on one clock of real time, so something always moves. What it needs is prepared while the Mojang screen still shows
 * ({@link MenuWarmup}); only the menu world's shader, which the game lets the mod compile at the very end of loading only, is compiled once the
 * bar is full, on the still screen. A click or a key skips to the end with a quick fade.
 */
public final class StartupTransition {
	// the steps, in seconds since the collapse began
	private static final float LOGO_FADE = 0.25f;
	private static final float COLLAPSE_START = 0.12f;
	private static final float COLLAPSE_END = 0.8f;
	private static final float MELT_START = 0.55f;
	private static final float MELT_END = 1.15f;
	/** When the menu's parts start coming in: half a second before the camera rests. */
	public static final float UI_START = CameraPath.SECONDS - 0.5f;
	/** The collapse starts while the bar glides through its last part, so the two are one motion, once the menu is prepared. */
	private static final float BAR_HANDOVER = 0.97f;
	private static final float SKIP_FADE = 0.25f;
	private static final int MOJANG_RED = 0xFFEF323D;
	private static final int MOJANG_DARK = 0xFF0E0E0E;

	private enum Phase {
		LOADING, GLIDE, RUNNING, DONE
	}

	private static final AnimClock CLOCK = new AnimClock();
	private static final ProgressSmoother BAR = new ProgressSmoother();
	private static final FrameTimes FRAMES = new FrameTimes("the way from the Mojang screen into the menu");
	private static Phase phase = Phase.LOADING;
	private static float runStart;

	private StartupTransition() {
	}

	/** @return whether the Mojang screen's bar is the mod's smooth one: at game start, while the mod's menus are on */
	public static boolean drawsBar() {
		return phase == Phase.LOADING && ModFeatures.MENUS.isEnabled();
	}

	/**
	 * Called every frame of the Mojang screen at game start: the bar follows the loading's real progress, and a slice of the menu's
	 * preparation is done.
	 */
	public static void loadingFrame(float progress) {
		if (phase != Phase.LOADING) {
			return;
		}
		float step = CLOCK.tick();
		MenuWarmup.loadClasses();
		BAR.report(progress);
		BAR.update(step);
		note(MenuWarmup.step());
		FRAMES.frame("Mojang screen");
	}

	/**
	 * The game's startup screen finished loading. @return whether the transition takes over from it, which happens once per game start, when it
	 * is on; then the game's own fade is left out.
	 */
	public static boolean takeOver() {
		if (phase != Phase.LOADING) {
			return false;
		}
		if (!ModFeatures.MENUS.startupActive()) {
			phase = Phase.DONE;
			FRAMES.finish();
			return false;
		}
		phase = Phase.GLIDE;
		CLOCK.tick();
		BAR.complete();
		FRAMES.markZero();
		MenuWorld.setCamera(1f, 1f);
		return true;
	}

	/**
	 * @return whether the Mojang screen's bar has come to rest at its end (or the transition does not run at all), so a long piece of work no
	 *         longer stops a moving bar
	 */
	public static boolean barResting() {
		return phase == Phase.DONE || phase == Phase.RUNNING || phase == Phase.GLIDE && BAR.full();
	}

	/** @return whether the Mojang screen still covers the whole main menu, so the menu need not draw its world under it */
	public static boolean coversMenu() {
		return phase == Phase.GLIDE;
	}

	/** Notes work done by the game or the mod in the current frame of the way into the menu, for its frame time log. */
	public static void noteWork(String work) {
		note(work);
	}

	/** @return whether the transition is showing */
	public static boolean running() {
		return phase == Phase.GLIDE || phase == Phase.RUNNING;
	}

	/** @return seconds since the collapse began, or a negative value while the bar still glides */
	private static float elapsed() {
		return phase == Phase.RUNNING ? CLOCK.seconds() - runStart : -1f;
	}

	/**
	 * Called at the start of the main menu's frame: advances the transition and puts the camera where it is on its way down.
	 *
	 * @return when the menu's parts start coming in, as {@link System#nanoTime()}, or 0 when the transition does not decide it
	 */
	public static long beforeMenu() {
		if (!running()) {
			return 0L;
		}
		long now = System.nanoTime();
		float step = CLOCK.tick(now);
		BAR.update(step);
		// only what the way needs; the loading background and the season pictures follow once it is over
		if (!MenuWarmup.ready()) {
			note(MenuWarmup.step());
		}
		if (phase == Phase.GLIDE && BAR.value() >= BAR_HANDOVER && MenuWarmup.ready()) {
			phase = Phase.RUNNING;
			runStart = CLOCK.seconds();
		}
		if (phase == Phase.GLIDE) {
			FRAMES.frame(BAR.full() ? "full bar, preparing" : "bar gliding to its end");
			MenuWorld.setCamera(1f, 1f);
			return now + (long) (UI_START * 1_000_000_000L) + 1_000_000_000L;
		}
		float t = elapsed();
		FRAMES.frame(t < COLLAPSE_END ? "collapse" : t < MELT_END ? "glow melting into the sky" : t < UI_START ? "camera" : "menu coming in");
		if (t >= CameraPath.SECONDS) {
			finish();
			return 0L;
		}
		MenuWorld.setCamera(CameraPath.descent(t), CameraPath.tilt(t));
		return now + (long) ((UI_START - t) * 1_000_000_000L);
	}

	private static void note(String work) {
		if (work != null) {
			FRAMES.note(work);
		}
	}

	private static void finish() {
		phase = Phase.DONE;
		MenuWorld.setCamera(0f, 0f);
		FRAMES.finish();
	}

	/** Skips to the end: the camera at rest and the menu shown, with a quick fade from the frame on screen. */
	public static void skip() {
		if (!running()) {
			return;
		}
		ScreenFade.fadeFromNow(SKIP_FADE);
		finish();
		MenuWarmup.finishNeeded();
	}

	/** Draws the transition over everything: the Mojang screen while the bar glides, then the logo fading and the red contracting into a glow. */
	public static void draw(GuiGraphicsExtractor graphics) {
		if (!running()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean dark = client.options.darkMojangStudiosBackground().get();
		int brand = dark ? MOJANG_DARK : MOJANG_RED;
		float t = elapsed();
		graphics.nextStratum();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, MacTheme.DARK.palette(), brand, 0f, true);
		if (t < 0f) {
			graphics.fill(0, 0, width, height, brand);
			drawMojang(graphics, width, height, 1f);
			return;
		}
		// the red contracts upwards: the full screen fades while a large soft glow of it rises to the top center and shrinks
		float collapse = Easing.smooth((t - COLLAPSE_START) / (COLLAPSE_END - COLLAPSE_START));
		float melt = Easing.smooth((t - MELT_START) / (MELT_END - MELT_START));
		float reach = Math.max(width, height);
		float glowX = width / 2f;
		float glowY = Mth.lerp(collapse, height * 0.5f, height * 0.04f);
		float radiusX = Mth.lerp(collapse, reach * 1.25f, width * 0.36f) * (1f - 0.45f * melt);
		float radiusY = Mth.lerp(collapse, reach * 1.25f, height * 0.42f) * (1f - 0.45f * melt);
		float glowAlpha = Mth.lerp(collapse, 1f, 0.85f) * (1f - melt);
		if (glowAlpha > 0.003f) {
			ui.wash(glowX, glowY, radiusX, radiusY, Ui.withOpacity(brand, glowAlpha));
		}
		float cover = 1f - collapse;
		if (cover > 0.003f) {
			graphics.fill(0, 0, width, height, Ui.withOpacity(brand, cover));
		}
		float logo = 1f - Easing.smooth(t / LOGO_FADE);
		if (logo > 0.003f) {
			drawMojang(graphics, width, height, logo);
		}
	}

	/** The Mojang logo and the loading bar, laid out exactly as the game draws them, so the hand-over shows no change. */
	private static void drawMojang(GuiGraphicsExtractor graphics, int width, int height, float alpha) {
		int middleX = (int) (width * 0.5);
		int middleY = (int) (height * 0.5);
		double logoHeight = Math.min(width * 0.75, height) * 0.25;
		int halfHeight = (int) (logoHeight * 0.5);
		int halfWidth = (int) (logoHeight * 4.0 * 0.5);
		int white = ARGB.white(alpha);
		graphics.blit(RenderPipelines.MOJANG_LOGO, LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION, middleX - halfWidth, middleY - halfHeight, -0.0625f, 0f,
			halfWidth, (int) logoHeight, 120, 60, 120, 120, white);
		graphics.blit(RenderPipelines.MOJANG_LOGO, LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION, middleX, middleY - halfHeight, 0.0625f, 60f,
			halfWidth, (int) logoHeight, 120, 60, 120, 120, white);
		int barY = (int) (height * 0.8325);
		drawBar(graphics, width / 2 - halfWidth, barY - 5, width / 2 + halfWidth, barY + 5, alpha);
	}

	/** The Mojang screen's bar as the game draws it, filled as far as the smooth bar is. */
	public static void drawBar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float alpha) {
		int filled = Mth.ceil((x1 - x0 - 2) * BAR.value());
		int color = ARGB.color(Math.round(alpha * 255f), 255, 255, 255);
		graphics.fill(x0 + 2, y0 + 2, x0 + filled, y1 - 2, color);
		graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
		graphics.fill(x0 + 1, y1, x1 - 1, y1 - 1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1, y0, x1 - 1, y1, color);
	}
}
