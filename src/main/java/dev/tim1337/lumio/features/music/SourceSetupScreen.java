package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Glass;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.UiSounds;
import dev.tim1337.lumio.ui.controls.ButtonControl;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The guided setup of a music source, one window per source: a header with the source, a list of the steps on the left (done ones with a green
 * check, the current one in the accent color), one task per step on the right, and a footer with the step, Back and the main button.
 * <ul>
 * <li>Spotify: create an app (with the redirect address to copy), paste its Client ID (checked while typing), sign in in the browser (waiting
 * calmly, with a way to open it again), done.</li>
 * <li>Cider: open Cider (found automatically), allow access (create a token in Cider), paste the token and test it (showing the song that plays as
 * proof), done.</li>
 * </ul>
 * The finish closes on its own after three seconds. Problems are said in plain words with one button that fixes them. Tokens are masked, with an
 * eye to show them, stay on this computer and are sent only to the source they belong to; a lock line says so. Steps slide in and checks pop with
 * the menu's springs, at once with animations off.
 */
public final class SourceSetupScreen extends Screen {
	/** Which source is set up. */
	public enum Kind {
		SPOTIFY, CIDER
	}

	private static final int WIDTH = 600;
	private static final int HEIGHT = 340;
	private static final int PAD = 18;
	private static final int HEADER = 58;
	private static final int FOOTER = 50;
	private static final int STEPS_WIDTH = 190;
	private static final int TILE = 26;
	private static final int STEP_ROW = 42;
	private static final int STEP_CIRCLE = 16;
	private static final int BUTTON_HEIGHT = 26;
	private static final int FIELD_HEIGHT = 22;
	private static final int CLOSE = 16;
	/** The steps before the finish; the finish is the last step and is not counted in "Step x of y". */
	private static final int TASKS = 3;
	private static final int DONE = 3;
	/** How long the finish shows before the window closes and how long "Copied" shows, in seconds. */
	private static final float CLOSE_AFTER = 3f;
	private static final float COPIED_SECONDS = 1.5f;
	private static final int MAX_FIELD = 128;
	private static final int SPOTIFY_RGB = 0xFF1DB954;
	private static final int CIDER_RGB = 0xFFFA2D48;
	private static final String DASHBOARD = "https://developer.spotify.com/dashboard";
	private static final String BULLETS = "•".repeat(MAX_FIELD);
	/** On the dark glass a problem gets a darker base and lighter text, and a link a lighter accent, so they stay readable over a bright sky. */
	private static final float PROBLEM_BASE = 0.3f;
	private static final float PROBLEM_TEXT_LIGHTEN = 0.35f;
	private static final float LINK_LIGHTEN = 0.45f;
	private static final float GOOD_TEXT_LIGHTEN = 0.4f;

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Screen parent;
	private final Kind kind;
	private final Spring open = Spring.bouncy(Spring.GENTLE);
	private final Spring close = Spring.smooth(Spring.DEFAULT);
	/** The content slides in from the side it comes from: 1 or -1 when a step changed, moving to 0. */
	private final Spring slide = Spring.smooth(Spring.SNAPPY);
	/** How far each step's check has popped in, from 0 to 1. */
	private final float[] checks = new float[TASKS + 1];
	private final List<Hit> hits = new ArrayList<>();
	/** The buttons, kept per label and kind; the ones drawn this frame; what each runs now; and the one pressed. */
	private final Map<String, ButtonControl> buttons = new HashMap<>();
	private final List<ButtonControl> shownButtons = new ArrayList<>();
	private final Map<String, Runnable> buttonActions = new HashMap<>();
	private ButtonControl pressedButton;
	private static final String PRIMARY_KEY = "primary:";
	private boolean closing;
	private int step;
	private long last = System.nanoTime();
	private float time;
	/** The text field of the step and whether its text shows. */
	private String field = "";
	private boolean reveal;
	private boolean fieldFocused;
	private boolean selectAll;
	private float copiedFor;
	private float doneFor;
	// Spotify: whether the browser was sent to sign in
	private boolean signingIn;
	// Cider: whether it runs and the last token test
	private final CiderConnect connect = new CiderConnect();

	private SourceSetupScreen(Screen parent, Kind kind) {
		super(Component.literal(kind == Kind.SPOTIFY ? "Connect Spotify" : "Connect Cider"));
		this.parent = parent;
		this.kind = kind;
		field = kind == Kind.SPOTIFY ? ModFeatures.MUSIC.spotifyClientId() : ModFeatures.MUSIC.ciderToken();
	}

	public static SourceSetupScreen spotify(Screen parent) {
		return new SourceSetupScreen(parent, Kind.SPOTIFY);
	}

	public static SourceSetupScreen cider(Screen parent) {
		return new SourceSetupScreen(parent, Kind.CIDER);
	}

	/** Opens a step directly, for the test that screenshots every step. */
	public SourceSetupScreen showStep(int index) {
		step = Mth.clamp(index, 0, DONE);
		for (int i = 0; i <= step; i++) {
			checks[i] = 1f;
		}
		return this;
	}

	// ------------------------------------------------------------------------------------------------------- steps

	private String[] stepTitles() {
		return kind == Kind.SPOTIFY ? new String[] {"Create an app", "Client ID", "Sign in", "Done"}
			: new String[] {"Open Cider", "Allow access", "Paste the token", "Done"};
	}

	private String[] stepSubtitles() {
		return kind == Kind.SPOTIFY ? new String[] {"in the developer dashboard", "paste it here", "in your browser", ""}
			: new String[] {"found automatically", "create a token in Cider", "and test it", ""};
	}

	/** @return whether the current step's task is done, so the main button goes on */
	private boolean stepDone() {
		return switch (kind) {
			case SPOTIFY -> switch (step) {
				case 1 -> validClientId(field);
				case 2 -> MusicService.spotifyConnected();
				default -> true;
			};
			case CIDER -> switch (step) {
				case 0 -> connect.found();
				case 2 -> connect.works();
				default -> true;
			};
		};
	}

	private static boolean validClientId(String id) {
		return id.strip().matches("[0-9a-fA-F]{32}");
	}

	/** Goes to a step, sliding in from the side it lies on, and starts what the step does by itself. */
	private void go(int to) {
		int from = step;
		step = Mth.clamp(to, 0, DONE);
		if (step == from) {
			return;
		}
		if (ModFeatures.MENU.animations()) {
			slide.snap(step > from ? 1f : -1f);
		}
		if (step > from) {
			checks[from] = ModFeatures.MENU.animations() ? 0.01f : 1f;
		}
		if (step == DONE) {
			checks[DONE] = ModFeatures.MENU.animations() ? 0.01f : 1f;
		}
		fieldFocused = kind == Kind.SPOTIFY ? step == 1 : step == 2;
		selectAll = false;
		if (kind == Kind.SPOTIFY && step == 2) {
			signIn();
		}
		if (step == DONE) {
			finish();
		}
	}

	/** The main button of a step: on to the next, after saving what was entered. */
	private void next() {
		if (!stepDone()) {
			return;
		}
		if (kind == Kind.SPOTIFY && step == 1) {
			ModFeatures.MUSIC.setSpotifyClientId(field.strip());
		}
		UiSounds.play(step == DONE - 1 ? UiSounds.Cue.DONE : UiSounds.Cue.TICK);
		go(step + 1);
	}

	/** Sends the browser to Spotify's sign-in, with the Client ID set up before. */
	private void signIn() {
		signingIn = true;
		MusicService.setSpotifyClientId(ModFeatures.MUSIC.spotifyClientId());
		MusicService.connectSpotify(uri -> Util.getPlatform().openUri(uri));
	}

	/** The source is used for the music from now on; the window closes on its own a little later. */
	private void finish() {
		doneFor = 0f;
		if (kind == Kind.SPOTIFY) {
			ModFeatures.MUSIC.useSpotify();
		} else {
			ModFeatures.MUSIC.useCider(field.strip());
		}
	}

	/** Tests the token: Cider is asked once, off the render thread; the answer shows when it comes. */
	private void test() {
		connect.test(field);
	}

	/** Takes the text from the clipboard into the field. */
	private void paste() {
		String text = minecraft.keyboardHandler.getClipboard();
		field = text == null ? "" : text.strip();
		if (field.length() > MAX_FIELD) {
			field = field.substring(0, MAX_FIELD);
		}
		fieldFocused = true;
		if (kind == Kind.CIDER) {
			test();
		}
	}

	// ------------------------------------------------------------------------------------------------------- frame

	private int boxWidth() {
		return Math.min(WIDTH, width - 24);
	}

	private int boxHeight() {
		return Math.min(HEIGHT, height - 24);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		hits.clear();
		shownButtons.clear();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		time += dt;
		advance(dt);
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), dt, true);
		float grown = open.follow(ui, 1f);
		float gone = close.follow(ui, closing ? 1f : 0f);
		if (closing && gone > 0.97f) {
			minecraft.gui.setScreen(parent);
			return;
		}
		float fade = Mth.clamp(Math.min(grown, 1f - gone), 0f, 1f);
		int w = boxWidth();
		int h = boxHeight();
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = 0.92f + 0.08f * Math.min(grown, 1.04f) - 0.06f * gone;
		pose.translate(x + w / 2f, y + h / 2f);
		pose.scale(scale, scale);
		pose.translate(-(x + w / 2f), -(y + h / 2f));
		if (HudStyle.palette().glass()) {
			Glass.body(ui, x, y, w, h, Radius.XXL, fade, true, UiGlow.Target.MENU);
		} else {
			HudStyle.panel(ui, x, y, w, h, Radius.XXL, HudStyle.opacity() * fade, UiGlow.Target.MENU, fade);
		}
		Ui.setAlpha(fade);
		drawHeader(ui, x, y, w, mouseX, mouseY);
		ui.fill(x + 1, y + HEADER, w - 2, 1, Ui.withOpacity(HudStyle.text(), 0.08f));
		drawSteps(ui, x, y + HEADER, h - HEADER - FOOTER);
		ui.fill(x + STEPS_WIDTH, y + HEADER + 1, 1, h - HEADER - FOOTER - 1, Ui.withOpacity(HudStyle.text(), 0.08f));
		float s = slide.follow(ui, 0f);
		int cx = x + STEPS_WIDTH + PAD + Math.round(s * 24f);
		int cw = w - STEPS_WIDTH - 2 * PAD;
		Ui.setAlpha(fade * (1f - Math.abs(s)));
		drawTask(ui, cx, y + HEADER + 16, cw, y + h - FOOTER - 10, mouseX, mouseY);
		Ui.setAlpha(fade);
		ui.fill(x + 1, y + h - FOOTER, w - 2, 1, Ui.withOpacity(HudStyle.text(), 0.08f));
		drawFooter(ui, x, y + h - FOOTER, w, mouseX, mouseY);
		if (HudStyle.palette().glass()) {
			Ui.setAlpha(1f);
			Glass.edges(ui, x, y, w, h, Radius.XXL, fade, true, UiGlow.Target.MENU);
		}
		Ui.setAlpha(1f);
		pose.popMatrix();
	}

	/** Moves the checks, the finish's countdown and the look for Cider; takes the answers of Cider that came. */
	private void advance(float dt) {
		boolean animated = ModFeatures.MENU.animations();
		for (int i = 0; i < checks.length; i++) {
			if (checks[i] > 0f) {
				checks[i] = animated ? Math.min(1f, checks[i] + dt / 0.3f) : 1f;
			}
		}
		copiedFor = Math.max(0f, copiedFor - dt);
		if (step == DONE) {
			doneFor += dt;
			if (doneFor >= CLOSE_AFTER) {
				onClose();
			}
		}
		if (kind == Kind.CIDER && step < DONE) {
			if (connect.advance(dt) != null) {
				UiSounds.play(connect.works() ? UiSounds.Cue.DONE : UiSounds.Cue.FAIL);
			}
		}
		if (kind == Kind.SPOTIFY && step == 2 && MusicService.spotifyConnected()) {
			// signed in: on to the finish by itself
			UiSounds.play(UiSounds.Cue.DONE);
			go(DONE);
		}
	}

	private void drawHeader(Ui ui, int x, int y, int w, int mouseX, int mouseY) {
		int middle = y + HEADER / 2;
		int tileX = x + PAD;
		ui.rounded(tileX, middle - TILE / 2, TILE, TILE, Radius.MD, kind == Kind.SPOTIFY ? SPOTIFY_RGB : CIDER_RGB);
		String letter = kind == Kind.SPOTIFY ? "S" : "C";
		ui.textAtScaledBold(letter, tileX + (TILE - ui.boldWidthScaled(letter, TextScale.HEADING)) / 2f, middle - 6.5f, TextScale.HEADING, 0xFFFFFFFF);
		int textX = tileX + TILE + 11;
		ui.textAtScaledBold(kind == Kind.SPOTIFY ? "Connect Spotify" : "Connect Cider", textX, middle - 11f, TextScale.BODY, HudStyle.text());
		ui.textAtScaled(kind == Kind.SPOTIFY ? "Uses the Spotify Web API · about 2 minutes" : "Apple Music through Cider · about 1 minute", textX,
			middle + 3f, TextScale.SMALL, HudStyle.secondary());
		int closeX = x + w - PAD - CLOSE;
		int closeY = middle - CLOSE / 2;
		boolean over = mouseX >= closeX && mouseX < closeX + CLOSE && mouseY >= closeY && mouseY < closeY + CLOSE;
		if (over) {
			ui.rounded(closeX, closeY, CLOSE, CLOSE, CLOSE / 2f, Ui.withOpacity(HudStyle.text(), 0.1f));
		}
		ui.icon("close", closeX + 4, closeY + 4, CLOSE - 8, HudStyle.secondary());
		hits.add(new Hit(closeX, closeY, CLOSE, CLOSE, this::onClose));
	}

	/** The steps on the left: a check for the done ones, the number in the accent color for the current one, a ring for the rest. */
	private void drawSteps(Ui ui, int x, int top, int height) {
		String[] titles = stepTitles();
		String[] subtitles = stepSubtitles();
		int rowY = top + 14;
		for (int i = 0; i < titles.length; i++) {
			boolean current = i == step;
			boolean done = i < step;
			if (current && i < DONE) {
				ui.rounded(x + 10, rowY - 6, STEPS_WIDTH - 20, STEP_ROW - 4, Radius.MD, Ui.withOpacity(HudStyle.text(), 0.07f));
			}
			int cx = x + 22 + STEP_CIRCLE / 2;
			int cy = rowY + STEP_CIRCLE / 2;
			if (done || i == DONE && current) {
				// the check pops in with a slight overshoot
				float t = checks[i] <= 0f && i < step ? 1f : checks[i];
				float pop = t >= 1f ? 1f : 1f + 2.7f * (t - 1f) * (t - 1f) * (t - 1f) + 1.7f * (t - 1f) * (t - 1f);
				int size = Math.max(2, Math.round(STEP_CIRCLE * pop));
				ui.rounded(cx - size / 2, cy - size / 2, size, size, size / 2f, SystemColors.GREEN);
				if (pop > 0.6f) {
					ui.icon("tick", cx - 5, cy - 5, 10, 0xFFFFFFFF);
				}
			} else {
				String number = Integer.toString(i + 1);
				if (current) {
					ui.rounded(cx - STEP_CIRCLE / 2, cy - STEP_CIRCLE / 2, STEP_CIRCLE, STEP_CIRCLE, STEP_CIRCLE / 2f, ui.accent);
				} else {
					ui.ring(cx - STEP_CIRCLE / 2, cy - STEP_CIRCLE / 2, STEP_CIRCLE, STEP_CIRCLE, STEP_CIRCLE / 2f, 1, Ui.withOpacity(HudStyle.text(), 0.4f));
				}
				ui.textAtScaledBold(number, cx - ui.boldWidthScaled(number, TextScale.SMALL) / 2f, cy - 3.5f, TextScale.SMALL,
					current ? 0xFFFFFFFF : HudStyle.secondary());
			}
			int textX = x + 22 + STEP_CIRCLE + 9;
			int room = STEPS_WIDTH - (textX - x) - 12;
			ui.textAtScaledBold(ui.fit(titles[i], room), textX, rowY + (subtitles[i].isEmpty() ? 4 : 0), 1f, current || done ? HudStyle.text() : HudStyle.secondary());
			if (!subtitles[i].isEmpty()) {
				ui.textAtScaled(ui.fitScaled(subtitles[i], room, TextScale.SMALL), textX, rowY + 12, TextScale.SMALL, HudStyle.secondary());
			}
			rowY += STEP_ROW;
		}
	}

	/** The task of the current step, on the right. */
	private void drawTask(Ui ui, int x, int y, int w, int bottom, int mouseX, int mouseY) {
		if (step == DONE) {
			drawDone(ui, x, y, w);
			return;
		}
		if (kind == Kind.SPOTIFY) {
			drawSpotify(ui, x, y, w, bottom, mouseX, mouseY);
		} else {
			drawCider(ui, x, y, w, bottom, mouseX, mouseY);
		}
	}

	private void drawSpotify(Ui ui, int x, int y, int w, int bottom, int mouseX, int mouseY) {
		switch (step) {
			case 0 -> {
				int at = title(ui, "Create an app", x, y);
				at = paragraph(ui, "Open the Spotify developer dashboard and log in; the first time, accept the terms. Click Create app: any name and "
					+ "description work. Add the redirect address below as the Redirect URI, tick Web API, then Save.", x, at, w);
				at = redirect(ui, x, at + 8, w, mouseX, mouseY);
				button(ui, mouseX, mouseY, x, at + 10, "Open dashboard", false, true, () -> Util.getPlatform().openUri(URI.create(DASHBOARD)));
			}
			case 1 -> {
				int at = title(ui, "Paste your Client ID", x, y);
				at = paragraph(ui, "Open your app in the Spotify developer dashboard and copy the Client ID from its settings page.", x, at, w);
				at = redirect(ui, x, at + 6, w, mouseX, mouseY);
				ui.textAtScaledBold("Client ID", x, at + 8, TextScale.SMALL, HudStyle.text());
				int fieldY = at + 20;
				String text = field.strip();
				boolean valid = validClientId(text);
				boolean wrong = !text.isEmpty() && !valid;
				field(ui, x, fieldY, w, mouseX, mouseY, valid ? SystemColors.GREEN : wrong ? SystemColors.RED : 0);
				if (valid) {
					ui.textAtScaled("Looks right: 32 characters", x, fieldY + FIELD_HEIGHT + 5, TextScale.SMALL, goodText());
				} else if (wrong) {
					ui.textAtScaled("That doesn't look like a Client ID (it has 32 letters and numbers)", x, fieldY + FIELD_HEIGHT + 5, TextScale.SMALL,
						HudStyle.readable(SystemColors.RED_TEXT));
				}
				lock(ui, "The sign-in stays on this computer. Lumio never sees your password.", x, bottom, w);
			}
			default -> {
				int at = title(ui, "Sign in to Spotify", x, y);
				at = paragraph(ui, "Your browser opens Spotify. Click Agree, then come back here; the page in the browser can be closed.", x, at, w);
				String problem = spotifyProblem();
				if (problem != null) {
					at = problem(ui, problem, x, at + 8, w);
					button(ui, mouseX, mouseY, x, at + 8, "Try again", true, true, this::signIn);
				} else {
					spinner(ui, x + 8, at + 20);
					ui.textAtScaledBold("Waiting for your browser…", x + 22, at + 16, 1f, HudStyle.text());
					ui.textAtScaled("Didn't open?", x + 22, at + 30, TextScale.SMALL, HudStyle.secondary());
					float linkX = x + 22 + ui.widthScaled("Didn't open? ", TextScale.SMALL);
					link(ui, "Open it again", linkX, at + 30, mouseX, mouseY, this::signIn);
				}
				lock(ui, "The sign-in stays on this computer. Lumio never sees your password.", x, bottom, w);
			}
		}
	}

	/** @return what went wrong with the sign-in, in plain words, or null while it goes on */
	private String spotifyProblem() {
		if (!signingIn) {
			return null;
		}
		String message = MusicService.spotifyMessage();
		if (message == null) {
			return null;
		}
		String lower = message.toLowerCase(Locale.ROOT);
		if (lower.startsWith("spotify said no")) {
			return "Spotify didn't allow the sign-in. Try again and click Agree.";
		}
		if (lower.startsWith("spotify did not accept")) {
			return "Spotify didn't accept the sign-in. Check that the redirect address of your app is the one above.";
		}
		if (lower.startsWith("spotify could not be reached")) {
			return "Spotify couldn't be reached. Check your connection and try again.";
		}
		if (lower.startsWith("spotify sign-in could not start")) {
			return "The sign-in couldn't start. Try again.";
		}
		return null;
	}

	private void drawCider(Ui ui, int x, int y, int w, int bottom, int mouseX, int mouseY) {
		switch (step) {
			case 0 -> {
				int at = title(ui, "Open Cider", x, y);
				at = paragraph(ui, "Start Cider on this computer and play something. The game finds it by itself.", x, at, w);
				ciderStatus(ui, x, at + 10);
			}
			case 1 -> {
				int at = title(ui, "Allow access", x, y);
				at = paragraph(ui, "In Cider, open Settings, then Connectivity. Turn on the API and create a token, then copy it.", x, at, w);
				ciderStatus(ui, x, at + 10);
			}
			default -> {
				int at = title(ui, "Paste the token from Cider", x, y);
				ciderStatus(ui, x, at);
				ui.textAtScaledBold("Cider token", x, at + 18, TextScale.SMALL, HudStyle.text());
				int fieldY = at + 30;
				MusicService.CiderCheck tested = connect.tested();
				boolean works = connect.works();
				boolean wrong = tested != null && !works;
				field(ui, x, fieldY, w, mouseX, mouseY, works ? SystemColors.GREEN : wrong ? SystemColors.RED : 0);
				int below = fieldY + FIELD_HEIGHT + 6;
				if (connect.testing()) {
					spinner(ui, x + 6, below + 4);
					ui.textAtScaled("Testing…", x + 16, below, TextScale.SMALL, HudStyle.secondary());
				} else if (works) {
					ui.textAtScaled("Connection works", x, below, TextScale.SMALL, goodText());
					playing(ui, x, below + 14, w, tested.title(), tested.artist());
				} else if (tested != null && tested.state() == MusicService.CiderState.NEEDS_TOKEN) {
					int at2 = problem(ui, "Cider didn't accept this token. Copy it again in Cider, under Settings, Connectivity.", x, below, w);
					button(ui, mouseX, mouseY, x, at2 + 6, "Paste from clipboard", true, true, this::paste);
				} else if (tested != null) {
					int at2 = problem(ui, "Cider isn't running. Start it, then test again.", x, below, w);
					button(ui, mouseX, mouseY, x, at2 + 6, "Retry", true, true, this::test);
				}
				lock(ui, "The token is stored only on this computer and never shared in configs.", x, bottom, w);
			}
		}
	}

	/** Shows whether Cider was found: a green dot and that it runs, or a calm spinner while it is looked for. */
	private void ciderStatus(Ui ui, int x, int y) {
		if (connect.found()) {
			ui.circle(x + 3, y + 4, 3, SystemColors.GREEN);
			ui.textAtScaled("Cider is running on this computer", x + 11, y, TextScale.SMALL, goodText());
		} else {
			spinner(ui, x + 4, y + 4);
			ui.textAtScaled("Looking for Cider…", x + 13, y, TextScale.SMALL, HudStyle.secondary());
		}
	}

	private void drawDone(Ui ui, int x, int y, int w) {
		int middle = y + 40;
		ui.rounded(x, middle - 16, 32, 32, 16f, SystemColors.GREEN);
		ui.icon("tick", x + 8, middle - 8, 16, 0xFFFFFFFF);
		ui.textAtScaledBold(kind == Kind.SPOTIFY ? "Connected to Spotify" : "Connected to Cider", x + 44, middle - 11, TextScale.BODY, HudStyle.text());
		ui.textAtScaled("You can close this window", x + 44, middle + 4, TextScale.SMALL, HudStyle.secondary());
		MusicService.Track track = MusicService.track();
		boolean fromSource = track != null && MusicService.playsFrom(kind == Kind.SPOTIFY ? MusicService.Source.SPOTIFY : MusicService.Source.CIDER);
		if (fromSource) {
			playing(ui, x, middle + 32, w, track.title(), track.artist());
		} else if (kind == Kind.CIDER && connect.tested() != null && !connect.tested().title().isEmpty()) {
			playing(ui, x, middle + 32, w, connect.tested().title(), connect.tested().artist());
		} else {
			ui.textAtScaled(kind == Kind.SPOTIFY ? "Play a song in Spotify and it shows in the game." : "Play a song in Cider and it shows in the game.", x,
				middle + 36, TextScale.SMALL, HudStyle.secondary());
		}
	}

	/** The song that plays, as proof that the source works. */
	private void playing(Ui ui, int x, int y, int w, String title, String artist) {
		int h = 40;
		int color = kind == Kind.SPOTIFY ? SPOTIFY_RGB : CIDER_RGB;
		// green like the check: a red frame for Cider would read as an error
		ui.rounded(x, y, w, h, Radius.LG, Ui.withOpacity(SystemColors.GREEN, 0.1f));
		ui.ring(x, y, w, h, Radius.LG, 1, Ui.withOpacity(SystemColors.GREEN, 0.35f));
		ui.roundedGradient(x + 7, y + 7, h - 14, h - 14, Radius.SM, color, Ui.mix(color, 0xFF7C4DFF, 0.6f));
		int tx = x + h + 2;
		String label = title.isEmpty() ? "CONNECTED" : "CONNECTED · PLAYING NOW";
		ui.textAtScaledBold(label, tx, y + 6, TextScale.SMALL, goodText());
		ui.textAtScaledBold(ui.fit(title.isEmpty() ? "Nothing plays right now" : title, w - h - 10), tx, y + 16, 1f, HudStyle.text());
		if (!artist.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(artist, w - h - 10, TextScale.SMALL), tx, y + 28, TextScale.SMALL, HudStyle.secondary());
		}
	}

	private void drawFooter(Ui ui, int x, int y, int w, int mouseX, int mouseY) {
		int middle = y + FOOTER / 2;
		String where = step == DONE ? "Done" : "Step " + (step + 1) + " of " + TASKS;
		ui.textAtScaled(where, x + PAD, middle - 4f, TextScale.SMALL, HudStyle.secondary());
		if (step == DONE) {
			button(ui, mouseX, mouseY, x + w - PAD - 80, middle - BUTTON_HEIGHT / 2, 80, "Close", true, true, this::onClose);
			return;
		}
		String main = step == DONE - 1 && kind == Kind.CIDER ? "Finish" : "Continue";
		boolean done = stepDone();
		int mainWidth = buttonWidth(ui, main);
		int mainX = x + w - PAD - mainWidth;
		button(ui, mouseX, mouseY, mainX, middle - BUTTON_HEIGHT / 2, mainWidth, main, true, done, this::next);
		int left = mainX - 8;
		if (kind == Kind.CIDER && step == 2) {
			String again = connect.tested() == null ? "Test" : "Test again";
			int width = buttonWidth(ui, again);
			button(ui, mouseX, mouseY, left - width, middle - BUTTON_HEIGHT / 2, width, again, false, !connect.testing() && !field.isBlank(), this::test);
			left -= width + 8;
		}
		if (step > 0 && !(kind == Kind.SPOTIFY && step == 2 && MusicService.spotifyConnected())) {
			int width = buttonWidth(ui, "Back");
			button(ui, mouseX, mouseY, left - width, middle - BUTTON_HEIGHT / 2, width, "Back", false, true, () -> go(step - 1));
		}
	}

	// ------------------------------------------------------------------------------------------------------- parts

	/** @return the y below a task's title */
	private static int title(Ui ui, String text, int x, int y) {
		ui.textAtScaledBold(text, x, y, TextScale.HEADING, HudStyle.text());
		return y + 22;
	}

	/** @return the y below a paragraph, wrapped to the width */
	private static int paragraph(Ui ui, String text, int x, int y, int w) {
		List<String> lines = ui.wrap(text, Math.round(w / TextScale.SMALL), 5);
		for (String line : lines) {
			ui.textAtScaled(line, x, y, TextScale.SMALL, Ui.withOpacity(HudStyle.text(), 0.82f));
			y += 12;
		}
		return y;
	}

	/** @return the green of good news, readable on the glass */
	private static int goodText() {
		return HudStyle.light() ? HudStyle.readable(SystemColors.GREEN) : Ui.mix(SystemColors.GREEN, 0xFFFFFFFF, GOOD_TEXT_LIGHTEN);
	}

	/** A problem in plain words, in red. @return the y below it */
	private static int problem(Ui ui, String text, int x, int y, int w) {
		List<String> lines = ui.wrap(text, Math.round((w - 16) / TextScale.SMALL), 3);
		int h = lines.size() * 12 + 10;
		boolean light = HudStyle.light();
		ui.rounded(x, y, w, h, Radius.MD, Ui.withOpacity(light ? 0xFFFFFFFF : 0xFF000000, PROBLEM_BASE));
		ui.rounded(x, y, w, h, Radius.MD, Ui.withOpacity(SystemColors.RED, 0.12f));
		ui.ring(x, y, w, h, Radius.MD, 1, Ui.withOpacity(SystemColors.RED, 0.4f));
		int color = light ? HudStyle.readable(SystemColors.RED_TEXT) : Ui.mix(SystemColors.RED_TEXT, 0xFFFFFFFF, PROBLEM_TEXT_LIGHTEN);
		for (int i = 0; i < lines.size(); i++) {
			ui.textAtScaled(lines.get(i), x + 8, y + 6 + i * 12, TextScale.SMALL, color);
		}
		return y + h;
	}

	/** The redirect address of the Spotify app with its Copy button. @return the y below it */
	private int redirect(Ui ui, int x, int y, int w, int mouseX, int mouseY) {
		int h = 36;
		ui.rounded(x, y, w, h, Radius.MD, Ui.withOpacity(HudStyle.text(), 0.05f));
		ui.ring(x, y, w, h, Radius.MD, 1, Ui.withOpacity(HudStyle.text(), 0.1f));
		ui.textAtScaled("Redirect address for your app (step 1)", x + 9, y + 6, TextScale.SMALL, HudStyle.secondary());
		String copy = copiedFor > 0f ? "Copied" : "Copy";
		int bw = buttonWidth(ui, copy);
		ui.textAtScaledBold(ui.fit(MusicService.spotifyRedirect(), w - bw - 26), x + 9, y + 19, TextScale.SMALL, HudStyle.text());
		button(ui, mouseX, mouseY, x + w - bw - 7, y + (h - BUTTON_HEIGHT) / 2, bw, copy, false, true, () -> {
			minecraft.keyboardHandler.setClipboard(MusicService.spotifyRedirect());
			copiedFor = COPIED_SECONDS;
		});
		return y + h;
	}

	/**
	 * The text field of the step: masked unless shown with the eye, with a colored frame for a checked value (0 for none). A click focuses it;
	 * Ctrl+V pastes.
	 */
	private void field(Ui ui, int x, int y, int w, int mouseX, int mouseY, int frame) {
		ui.rounded(x, y, w, FIELD_HEIGHT, Radius.MD, Ui.withOpacity(0xFF000000, 0.25f));
		ui.ring(x, y, w, FIELD_HEIGHT, Radius.MD, frame != 0 ? 2 : 1, frame != 0 ? frame : fieldFocused ? ui.accent : Ui.withOpacity(HudStyle.text(), 0.16f));
		int eyeX = x + w - 22;
		String shown = reveal ? field : masked(field);
		String fitted = ui.fit(shown, w - 40);
		if (selectAll && !field.isEmpty()) {
			ui.rounded(x + 8, y + 6, ui.width(fitted), FIELD_HEIGHT - 12, 2f, Ui.withOpacity(ui.accent, 0.4f));
		}
		ui.text(fitted, x + 9, y + FIELD_HEIGHT / 2 - 4, HudStyle.text());
		if (fieldFocused && (time % 1f) < 0.55f) {
			ui.fill(x + 10 + ui.width(fitted), y + 7, 1, FIELD_HEIGHT - 14, HudStyle.text());
		}
		if (field.isEmpty() && !fieldFocused) {
			ui.text("Paste it here (Ctrl+V)", x + 9, y + FIELD_HEIGHT / 2 - 4, HudStyle.secondary());
		}
		ui.icon("eye", eyeX, y + FIELD_HEIGHT / 2 - 6, 12, reveal ? ui.accent : HudStyle.secondary());
		hits.add(new Hit(eyeX - 3, y, 18, FIELD_HEIGHT, () -> reveal = !reveal));
		hits.add(new Hit(x, y, w - 24, FIELD_HEIGHT, () -> fieldFocused = true));
	}

	/** @return the field's text masked: a Client ID keeps its start and end, so it can be recognized; a token is all bullets */
	private String masked(String text) {
		int length = Math.min(text.length(), MAX_FIELD);
		if (kind == Kind.SPOTIFY && length > 12) {
			return text.substring(0, 8) + BULLETS.substring(0, length - 12) + text.substring(length - 4, length);
		}
		return BULLETS.substring(0, length);
	}

	/** The lock line at the bottom of a task: what happens with what is entered. */
	private static void lock(Ui ui, String text, int x, int bottom, int w) {
		List<String> lines = ui.wrap(text, Math.round((w - 16) / TextScale.SMALL), 2);
		int y = bottom - lines.size() * 10;
		ui.icon("lock", x, y, 9, HudStyle.secondary());
		for (int i = 0; i < lines.size(); i++) {
			ui.textAtScaled(lines.get(i), x + 14, y + i * 10, TextScale.SMALL, HudStyle.secondary());
		}
	}

	/** A calm spinner: eight dots going round, the leading one brightest. */
	private void spinner(Ui ui, int x, int y) {
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4.0;
			float lead = ((time * 1.2f - i / 8f) % 1f + 1f) % 1f;
			int dx = Math.round(x + (float) Math.cos(angle) * 5f);
			int dy = Math.round(y + (float) Math.sin(angle) * 5f);
			ui.circle(dx, dy, 1, Ui.withOpacity(HudStyle.text(), 0.2f + 0.8f * (1f - lead)));
		}
	}

	private void link(Ui ui, String text, float x, int y, int mouseX, int mouseY, Runnable action) {
		int w = Math.round(ui.widthScaled(text, TextScale.SMALL));
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + 10;
		int color = HudStyle.light() ? ui.accent : Ui.mix(ui.accent, 0xFFFFFFFF, LINK_LIGHTEN);
		if (over) {
			color = Ui.mix(color, HudStyle.text(), 0.3f);
		}
		ui.textAtScaled(text, x, y, TextScale.SMALL, color);
		ui.fill(Math.round(x), y + 8, w, 1, Ui.withOpacity(color, 0.6f));
		hits.add(new Hit(Math.round(x), y - 2, w, 12, () -> {
			UiSounds.click();
			action.run();
		}));
	}

	private static int buttonWidth(Ui ui, String label) {
		return Math.round(ui.widthScaled(label, TextScale.BODY)) + 24;
	}

	private void button(Ui ui, int mouseX, int mouseY, int bx, int by, String label, boolean primary, boolean enabled, Runnable action) {
		button(ui, mouseX, mouseY, bx, by, buttonWidth(ui, label), label, primary, enabled, action);
	}

	/**
	 * Draws a button with the menu's shared button control, so it follows the theme like every other button: solid on the dark and light
	 * themes, neutral glass on the glass themes, the main button in the accent. The control is kept per label and kind, and runs the action
	 * given this frame.
	 */
	private void button(Ui ui, int mouseX, int mouseY, int bx, int by, int bw, String label, boolean primary, boolean enabled, Runnable action) {
		String key = (primary ? PRIMARY_KEY : "") + label;
		ButtonControl control = buttons.computeIfAbsent(key, made -> {
			ButtonControl created = new ButtonControl(Component.literal(label), false, () -> {
				Runnable now = buttonActions.get(made);
				if (now != null) {
					now.run();
				}
			}).height(BUTTON_HEIGHT);
			return primary ? created.primary() : created;
		});
		buttonActions.put(key, action);
		control.place(ui, bx, by, bw);
		control.setEnabled(enabled);
		control.render(ui, mouseX, mouseY);
		shownButtons.add(control);
	}

	// ------------------------------------------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (ScreenInput.button(event) != 0 || closing) {
			return true;
		}
		fieldFocused = false;
		selectAll = false;
		for (ButtonControl control : List.copyOf(shownButtons)) {
			if (control.mouseClicked(event.x(), event.y(), ScreenInput.button(event))) {
				pressedButton = control;
				return true;
			}
		}
		for (Hit hit : List.copyOf(hits)) {
			if (hit.contains(event.x(), event.y())) {
				hit.action().run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		// a button runs its action on release over it, as everywhere in the menu
		if (pressedButton != null) {
			ButtonControl released = pressedButton;
			pressedButton = null;
			released.mouseReleased(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		boolean control = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.CONTROL) != 0;
		if (fieldFocused) {
			if (control && ScreenInput.letter(key, 'V')) {
				paste();
				return true;
			}
			if (control && ScreenInput.letter(key, 'A')) {
				selectAll = true;
				return true;
			}
			if (key == InputConstants.KEY_BACKSPACE && !field.isEmpty()) {
				field = selectAll ? "" : field.substring(0, field.length() - 1);
				selectAll = false;
				connect.forgetTest();
				return true;
			}
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (kind == Kind.CIDER && step == 2 && connect.tested() == null && !field.isBlank()) {
				test();
			} else {
				next();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!fieldFocused || event.codepoint() < 32 || Character.isWhitespace(event.codepoint())) {
			return true;
		}
		if (selectAll) {
			field = "";
			selectAll = false;
		}
		if (field.length() < MAX_FIELD) {
			field += new String(Character.toChars(event.codepoint()));
			connect.forgetTest();
		}
		return true;
	}

	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			UiSounds.play(UiSounds.Cue.CLOSE);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		Ui.setAlpha(1f);
	}
}
