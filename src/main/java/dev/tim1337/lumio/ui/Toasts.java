package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Small notifications at the top right of the screen in the panel style, mostly for errors such as a music player that does not respond. They
 * slide in from the side, stay a few seconds and slide out, and are drawn over the game and every screen. An identical notification is suppressed
 * for a while, since an unreachable player would otherwise fill the screen with the same message every second.
 */
public final class Toasts {
	private static final class Toast {
		final String title;
		final String text;
		final long created = System.nanoTime();
		final boolean error;
		/** A button on the notice, such as "Undo", and what it does; null for none. */
		final String action;
		final Runnable onAction;
		/** How long it stays in seconds, or 0 for as long as set; brief notices stay shorter and make no sound. */
		float stay;
		String icon;

		Toast(String title, String text, boolean error, String action, Runnable onAction) {
			this.title = title;
			this.text = text;
			this.error = error;
			this.action = action;
			this.onAction = onAction;
			this.icon = error ? "close" : "info";
		}
	}

	private static final float STAY_SECONDS = 6.5f;
	private static final float SLIDE_SECONDS = 0.4f;
	private static final float LEAVE_SECONDS = 0.4f;
	private static final long SAME_NOTICE_MS = 45_000L;
	private static final int WIDTH = 270;
	private static final int PAD = 10;
	private static final int MAX_SHOWN = 3;

	/** Created on any thread (e.g. a background player request), consumed by the render thread. */
	private static final ConcurrentLinkedQueue<Toast> INCOMING = new ConcurrentLinkedQueue<>();
	private static final Map<String, Long> LAST = new HashMap<>();
	/** The informational notices of the last few seconds, by title and text. */
	private static final Map<String, Long> RECENT = new HashMap<>();
	private static final long REPEAT_MILLIS = 4000L;
	private static final List<Toast> SHOWN = new ArrayList<>();
	private static long lastDraw;
	/** Notifications with a button, and where the button was drawn last in GUI units: x, y, width and height. */
	private static final Map<Toast, float[]> ACTIONS = new HashMap<>();
	private static final int ACTION_HEIGHT = 16;
	private static final int ACTION_PAD = 8;

	private Toasts() {
	}

	/** @return whether the same notice was shown a moment ago: two sources of one event show it once */
	private static boolean repeated(String title, String text) {
		String key = title + '\n' + text;
		long now = System.currentTimeMillis();
		synchronized (RECENT) {
			RECENT.values().removeIf(at -> now - at >= REPEAT_MILLIS);
			Long before = RECENT.get(key);
			if (before != null) {
				return true;
			}
			RECENT.put(key, now);
		}
		return false;
	}

	/** Reports an error. Identical notifications are rate-limited. */
	public static void error(String key, String title, String text) {
		long now = System.currentTimeMillis();
		synchronized (LAST) {
			Long before = LAST.get(key);
			// an unreachable player is not news every minute: player-related notices repeat after 15 minutes, others after 45 seconds
			long wait = key.startsWith("cider") || key.startsWith("control") || key.startsWith("spotify") ? 15 * 60_000L : SAME_NOTICE_MS;
			if (before != null && now - before < wait) {
				return;
			}
			LAST.put(key, now);
		}
		INCOMING.add(new Toast(title, text, true, null, null));
	}

	/** Shows an informational notification; the same one again within a few seconds is left out. */
	public static void info(String title, String text) {
		if (repeated(title, text)) {
			return;
		}
		INCOMING.add(new Toast(title, text, false, null, null));
	}

	/** Shows an informational notification with a button, such as "Undo"; a click on it runs the action once and closes the notification. */
	public static void info(String title, String text, String action, Runnable onAction) {
		INCOMING.add(new Toast(title, text, false, action, onAction));
	}

	/** Shows a short notice without a sound, such as a command a keybind sent: it stays only for the given seconds. */
	public static void brief(String icon, String title, String text, float seconds) {
		if (repeated(title, text)) {
			return;
		}
		Toast toast = new Toast(title, text, false, null, null);
		toast.icon = icon;
		toast.stay = seconds;
		INCOMING.add(toast);
	}

	/** Called from the screen render hook, and by the HUD while no screen is open. */
	public static void render(GuiGraphicsExtractor graphics) {
		if (ModFeatures.CINEMATIC != null && ModFeatures.CINEMATIC.hidesHud()) {
			return; // not over the cinematic camera's film, also not over the chat open on it; they show when it ends
		}
		var settings = ModFeatures.NOTIFICATIONS;
		Toast incoming;
		while ((incoming = INCOMING.poll()) != null) {
			SHOWN.add(incoming);
			if (incoming.stay > 0f) {
				continue;
			}
			if (settings != null) {
				settings.playSound(incoming.error);
			} else {
				UiSounds.play(incoming.error ? UiSounds.Cue.FAIL : UiSounds.Cue.DONE);
			}
		}
		long now = System.nanoTime();
		SHOWN.removeIf(toast -> (now - toast.created) / 1_000_000_000f > stayOf(toast) + LEAVE_SECONDS);
		ACTIONS.keySet().retainAll(SHOWN);
		// the position editor draws its own samples; real notifications are not drawn over it
		if (SHOWN.isEmpty() || now == lastDraw || Minecraft.getInstance().gui.screen() instanceof NotificationEditScreen) {
			return;
		}
		lastDraw = now;
		Minecraft client = Minecraft.getInstance();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), ModFeatures.MENU == null ? SystemColors.BLUE : ModFeatures.MENU.accentArgb(), 0f, false);
		float scale = settings == null ? 1f : settings.scale();
		float right = settings == null ? 10f : settings.fromRight();
		float top = settings == null ? 10f : settings.fromTop();
		// at the default top-right position, on screens that have the clock and gear there, move below them
		if ((settings == null || settings.atDefaultPlace()) && MenuGear.wanted(client.gui.screen())) {
			top = 42;
		}
		// in the lower half of the screen notifications stack upwards so they stay on screen
		boolean upward = top > graphics.guiHeight() / 2f;
		int first = Math.max(0, SHOWN.size() - MAX_SHOWN);
		float y = 0;
		for (int i = first; i < SHOWN.size(); i++) {
			Toast toast = SHOWN.get(i);
			var pose = graphics.pose();
			pose.pushMatrix();
			float x = graphics.guiWidth() - right - WIDTH * scale;
			pose.translate(x, top + (upward ? -y : y));
			pose.scale(scale, scale);
			int height = heightOf(ui, toast);
			if (upward) {
				pose.translate(0, -height);
			}
			draw(ui, graphics, toast, 0, now, x + WIDTH * scale / 2f > graphics.guiWidth() / 2f);
			if (toast.action != null) {
				float[] area = ACTIONS.computeIfAbsent(toast, key -> new float[4]);
				int actionWidth = ui.width(toast.action) + 2 * ACTION_PAD;
				float top0 = top + (upward ? -y - height * scale : y);
				area[0] = x + (WIDTH - PAD - actionWidth) * scale;
				area[1] = top0 + PAD * scale;
				area[2] = actionWidth * scale;
				area[3] = ACTION_HEIGHT * scale;
			}
			pose.popMatrix();
			y += (height + 8) * scale;
		}
	}

	/** A click on a notification's button runs its action and lets the notification go. @return whether a button was hit */
	public static boolean click(double mouseX, double mouseY) {
		for (var entry : ACTIONS.entrySet()) {
			float[] area = entry.getValue();
			if (mouseX >= area[0] && mouseX < area[0] + area[2] && mouseY >= area[1] && mouseY < area[1] + area[3]) {
				Toast toast = entry.getKey();
				ACTIONS.remove(toast);
				SHOWN.remove(toast);
				UiSounds.click();
				toast.onAction.run();
				return true;
			}
		}
		return false;
	}

	/** Lets every screen pass clicks on a notification's button to it first. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ScreenMouseEvents.allowMouseClick(screen).register(
			(target, event) -> ScreenInput.button(event) != 0 || !click(event.x(), event.y())));
	}

	/** @return how long a notification stays: its own time, or the configured one */
	private static float stayOf(Toast toast) {
		return toast.stay > 0f ? toast.stay : ModFeatures.NOTIFICATIONS == null ? STAY_SECONDS : ModFeatures.NOTIFICATIONS.seconds();
	}

	public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		// over a screen, the screen render hook draws them; drawing here too would draw them twice
		if (Minecraft.getInstance().gui.screen() == null) {
			render(graphics);
		}
	}

	private static int heightOf(Ui ui, Toast toast) {
		return PAD * 2 + 12 + wrap(ui, toast.text, WIDTH - PAD * 2 - 26, 3).size() * 11 + 2;
	}

	/** Unscaled notification size for the position editor: its width, and the height of one with a single text line. */
	public static int width() {
		return WIDTH;
	}

	/** Draws a static notification at 0, 0 of the pose, for the position editor. @return its height */
	public static int drawSample(Ui ui, GuiGraphicsExtractor graphics, String title, String text) {
		Toast sample = new Toast(title, text, false, null, null);
		draw(ui, graphics, sample, 0, sample.created + (long) (SLIDE_SECONDS * 1_000_000_000L), true);
		return heightOf(ui, sample);
	}

	/**
	 * @param fromRight whether it slides in from the right edge rather than the left, whichever is nearer
	 * @return the notification height without its current animation offset, so the ones below do not jump
	 */
	private static int draw(Ui ui, GuiGraphicsExtractor graphics, Toast toast, int y, long now, boolean fromRight) {
		float age = (now - toast.created) / 1_000_000_000f;
		float stay = stayOf(toast);
		float in = Mth.clamp(age / SLIDE_SECONDS, 0f, 1f);
		float out = Mth.clamp((stay + LEAVE_SECONDS - age) / LEAVE_SECONDS, 0f, 1f);
		float slide = (1f - (1f - in) * (1f - in) * (1f - in)) * out;
		int textWidth = WIDTH - PAD * 2 - 26;
		List<String> lines = wrap(ui, toast.text, textWidth, 3);
		int height = PAD * 2 + 12 + lines.size() * 11 + 2;
		// a short slide combined with a fade: sliding in over its full width was a lot of motion at the screen edge
		int x = Math.round((1f - slide) * 16f * (fromRight ? 1f : -1f));
		HudStyle.panel(ui, x, y, WIDTH, height, Radius.XL, HudStyle.opacity() * slide, ModFeatures.NOTIFICATIONS == null || ModFeatures.NOTIFICATIONS.glow());
		int accent = toast.error ? SystemColors.RED : ui.accent;
		// round badge with the icon of the notification kind
		ui.circle(x + PAD + 9, y + PAD + 9, 9, Ui.withOpacity(accent, 0.25f * slide));
		ui.ring(x + PAD, y + PAD, 18, 18, Radius.pill(18), 1, Ui.withOpacity(accent, 0.9f * slide));
		ui.icon(toast.icon, x + PAD + 5, y + PAD + 5, 8, Ui.withOpacity(accent, slide));
		int left = x + PAD + 26;
		int titleWidth = textWidth;
		if (toast.action != null) {
			int actionWidth = ui.width(toast.action) + 2 * ACTION_PAD;
			int actionX = x + WIDTH - PAD - actionWidth;
			ui.rounded(actionX, y + PAD, actionWidth, ACTION_HEIGHT, ACTION_HEIGHT / 2f, Ui.withOpacity(ui.accent, slide));
			ui.textCentered(toast.action, actionX + actionWidth / 2, y + PAD + ACTION_HEIGHT / 2, Ui.withOpacity(0xFFFFFFFF, slide));
			titleWidth = actionX - 6 - left;
		}
		ui.text(ui.fit(toast.title, titleWidth), left, y + PAD, Ui.withOpacity(HudStyle.text(), slide));
		for (int i = 0; i < lines.size(); i++) {
			ui.text(lines.get(i), left, y + PAD + 14 + i * 11, Ui.withOpacity(HudStyle.secondary(), slide));
		}
		return height;
	}

	private static List<String> wrap(Ui ui, String text, int width, int most) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && ui.width(candidate) > width) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		if (lines.size() > most) {
			List<String> cut = new ArrayList<>(lines.subList(0, most));
			cut.set(most - 1, ui.fit(cut.get(most - 1) + "...", width));
			return cut;
		}
		return lines;
	}
}
