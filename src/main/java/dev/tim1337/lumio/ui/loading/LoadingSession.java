package dev.tim1337.lumio.ui.loading;

import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.ui.motion.LoadingTimeline;
import dev.tim1337.lumio.ui.motion.ProgressSmoother;
import dev.tim1337.lumio.util.FrameTimes;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * One continuous loading screen across all of the game's loading steps: connecting, encrypting, logging in, joining, loading terrain, saving,
 * creating or opening a world, reloading resources. The game's own screens keep doing their work; they report what they show here (their
 * translated texts and real progress), and the one view draws it, so the background, the title and the bar never restart between steps. Only
 * the title and the step line change, with a short cross-fade, and the bar carries on.
 * <p>
 * When the world is ready, the view stays over the game until the bar has reached its end (and at least half a second has passed), then fades
 * out; this never holds back the game. Everything runs on one clock of real time.
 */
public final class LoadingSession {
	/** What is being loaded, which decides the steps shown. */
	public enum Flow {
		SERVER, WORLD, RELOAD
	}

	/** The steps of joining a server, by the game's own texts. */
	private static final String[] SERVER_KEYS = {"connect.connecting", "connect.encrypting", "connect.authorizing", "connect.joining",
		"multiplayer.downloadingTerrain"};
	private static final int MAX_STEPS = 8;
	/** How long the title and step line take to change, in seconds. */
	static final float CHANGE_SECONDS = 0.25f;

	private static final AnimClock CLOCK = new AnimClock();
	private static final ProgressSmoother BAR = new ProgressSmoother();
	private static final FrameTimes FRAMES = new FrameTimes("the loading screen");
	private static boolean active;
	private static boolean ending;
	private static Flow flow = Flow.WORLD;
	private static float shownAt;
	private static float readyAt;
	private static float fullAt = -1f;
	private static float fadeAt = -1f;
	private static boolean progressKnown;
	// the title, the one before it while it changes, and when it changed
	private static String title = "";
	private static String titleBefore = "";
	private static float titleAt = -1f;
	private static Component titleSource;
	// the steps: their texts, the current one, and the step line's change
	private static final String[] STEPS = new String[MAX_STEPS];
	private static final String[] STEP_KEYS = new String[MAX_STEPS];
	private static int stepCount;
	private static int current = -1;
	private static float stepAt = -1f;
	private static Component stepSource;
	/** The quiet line at the bottom: the server or world. */
	private static String detail = "";
	private static String pendingDetail = "";
	private static String pendingTitle = "";
	/** The clock's step in this frame, which moves the bar once. */
	private static float frameStep;
	/** A frame of the view was drawn in this frame of the game. */
	private static boolean drawnThisFrame;

	private LoadingSession() {
	}

	/** The server about to be joined, from the game's connect call: its name and address for the title and the bottom line. */
	public static void joining(String name, String address) {
		String shown = address == null ? "" : address;
		pendingTitle = "Joining " + (name == null || name.isBlank() ? shown : name);
		pendingDetail = name == null || name.isBlank() || name.equals(address) ? shown : name + "  ·  " + shown;
	}

	/**
	 * Reports what a loading screen shows in this frame. Texts are compared by identity, so they are turned into strings only when they change.
	 *
	 * @param screenTitle the screen's title, or null to keep the session's
	 * @param step the step the game names, or null
	 * @param progress the game's progress from 0 to 1, or a negative value when it reports none
	 */
	public static void report(Flow kind, Component screenTitle, Component step, float progress) {
		frameStep = CLOCK.tick();
		float now = CLOCK.seconds();
		// joining a server or reloading while another kind of load runs is a new load; terrain after joining is the same one
		if (!active || ending || (kind != flow && kind != Flow.WORLD)) {
			begin(kind, now);
		}
		drawnThisFrame = true;
		FRAMES.frame();
		if (screenTitle != null && screenTitle != titleSource && flow != Flow.SERVER) {
			titleSource = screenTitle;
			setTitle(clean(screenTitle.getString()), now);
		}
		if (step != null && step != stepSource) {
			stepSource = step;
			stepReached(step, now);
		}
		if (detail.isEmpty()) {
			var server = Minecraft.getInstance().getSingleplayerServer();
			if (server != null) {
				detail = server.getWorldData().getLevelName();
			}
		}
		progressKnown = progress >= 0f;
		if (progressKnown) {
			BAR.report(progress);
		}
	}

	private static void begin(Flow kind, float now) {
		// a load right after the last one ended (a dimension change after joining) carries on the same screen; a different kind of load starts afresh
		boolean resumed = active && ending && (kind == flow || kind == Flow.WORLD);
		active = true;
		ending = false;
		fadeAt = -1f;
		if (resumed) {
			// a new step right after the last one ended (a dimension change after joining): the same screen carries on
			return;
		}
		flow = kind;
		shownAt = now;
		fullAt = -1f;
		BAR.reset();
		stepCount = 0;
		current = -1;
		stepAt = -1f;
		titleSource = null;
		stepSource = null;
		title = "";
		titleBefore = "";
		titleAt = -1f;
		detail = "";
		if (kind == Flow.SERVER) {
			for (String key : SERVER_KEYS) {
				STEP_KEYS[stepCount] = key;
				STEPS[stepCount++] = clean(Component.translatable(key).getString());
			}
			// the server of the last connect call: it stays until the next one, as the game can show another screen for a frame in between
			title = pendingTitle.isEmpty() ? STEPS[0] : pendingTitle;
			detail = pendingDetail;
		} else if (kind == Flow.RELOAD) {
			title = "Reloading resources";
		}
	}

	private static void setTitle(String text, float now) {
		if (text.isEmpty() || text.equals(title)) {
			return;
		}
		titleBefore = title;
		title = text;
		titleAt = now;
	}

	/** Moves the step line to a step: a known one of the flow, or a new one shown after the current one. */
	private static void stepReached(Component step, float now) {
		String key = step.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
		int found = -1;
		for (int i = 0; i < stepCount; i++) {
			if (key != null && key.equals(STEP_KEYS[i])) {
				found = i;
			}
		}
		String text = clean(step.getString());
		if (found < 0) {
			for (int i = 0; i < stepCount; i++) {
				if (text.equals(STEPS[i])) {
					found = i;
				}
			}
		}
		if (found < 0 && !text.isEmpty()) {
			// a step the flow does not list: it comes after the current one
			int at = Math.min(Math.max(current + 1, 0), MAX_STEPS - 1);
			if (stepCount >= MAX_STEPS) {
				System.arraycopy(STEPS, 1, STEPS, 0, MAX_STEPS - 1);
				System.arraycopy(STEP_KEYS, 1, STEP_KEYS, 0, MAX_STEPS - 1);
				stepCount--;
				at = Math.max(0, at - 1);
			}
			System.arraycopy(STEPS, at, STEPS, at + 1, stepCount - at);
			System.arraycopy(STEP_KEYS, at, STEP_KEYS, at + 1, stepCount - at);
			STEPS[at] = text;
			STEP_KEYS[at] = key;
			stepCount++;
			found = at;
		}
		if (found >= 0 && found != current) {
			current = Math.max(current, found);
			stepAt = now;
		}
	}

	/** @return a game text without the dots at its end: "Loading terrain..." becomes "Loading terrain" */
	static String clean(String text) {
		String trimmed = text.strip();
		while (trimmed.endsWith(".") || trimmed.endsWith("…")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	/**
	 * Called at the end of every frame of the game: if no loading screen drew this frame, the session ends. Into the game it fades out on
	 * its own time; anywhere else (an error, the menu) it hands over at once.
	 */
	public static void endOfFrame(boolean inGame) {
		if (!active) {
			return;
		}
		if (drawnThisFrame) {
			drawnThisFrame = false;
			return;
		}
		if (!ending) {
			// into an error or the menu it hands over at once; a reload fades out over whatever is under it
			if (!inGame && flow != Flow.RELOAD) {
				stop();
				return;
			}
			ending = true;
			readyAt = CLOCK.seconds();
			BAR.complete();
		}
	}

	private static void stop() {
		active = false;
		ending = false;
		FRAMES.finish();
	}

	/** @return whether the view is drawn over the game while it fades out */
	public static boolean ending() {
		return active && ending;
	}

	/** Advances the bar and the fade over the game, once per frame while ending. @return how visible the view still is, 0 when it is gone */
	public static float endingAlpha() {
		frameStep = CLOCK.tick();
		FRAMES.frame();
		float now = CLOCK.seconds();
		advanceBar();
		if (fadeAt < 0f && LoadingTimeline.mayFade(shownAt, readyAt, fullAt, now)) {
			fadeAt = now;
		}
		float alpha = fadeAt < 0f ? 1f : LoadingTimeline.fadeAlpha(fadeAt, now);
		if (alpha <= 0f) {
			stop();
		}
		return alpha;
	}

	/** Moves the bar by this frame's step; called once per drawn frame. */
	static float advanceBar() {
		float value = BAR.update(frameStep);
		frameStep = 0f;
		if (BAR.full() && fullAt < 0f) {
			fullAt = CLOCK.seconds();
		}
		return value;
	}

	// ----------------------------------------------------------------------------------------------- what the view reads

	static float now() {
		return CLOCK.seconds();
	}

	static String title() {
		return title;
	}

	static String titleBefore() {
		return titleBefore;
	}

	static float titleChange() {
		return titleAt < 0f ? 1f : Math.min(1f, (CLOCK.seconds() - titleAt) / CHANGE_SECONDS);
	}

	static int stepCount() {
		return stepCount;
	}

	static String step(int index) {
		return STEPS[index];
	}

	static int currentStep() {
		return current;
	}

	static float stepChange() {
		return stepAt < 0f ? 1f : Math.min(1f, (CLOCK.seconds() - stepAt) / CHANGE_SECONDS);
	}

	static String detail() {
		return detail;
	}

	static float barValue() {
		return BAR.value();
	}

	/** @return whether the game reports progress for the current step: otherwise the bar shows a shimmer */
	static boolean progressKnown() {
		return progressKnown || ending;
	}

	/** @return whether a session is running */
	public static boolean active() {
		return active;
	}
}
