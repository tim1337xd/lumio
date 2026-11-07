package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.config.FeatureManager;

import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.render.ScreenFade;
import dev.tim1337.lumio.ui.Glass;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTitleScreen;
import dev.tim1337.lumio.ui.MenuBackground;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.ScreenFit;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.UiSounds;

import dev.tim1337.lumio.ui.title.StartupTransition;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * The setup of the first start, over the main menu's world, blurred and darkened: a welcome card, then one window in the menu's look with the
 * steps Main menu, Look, How you play, Music and Ready. The look and the music apply while they are picked (the window itself changes with
 * them); the main menu and the features picked on "How you play" are applied when the setup ends. It is shown once, after the way into the main menu, and can run again
 * from the menu settings, starting from the current settings. An add-on with a part in the setup that is installed after the setup was finished shows
 * only that part, once, in the same window ({@link #addonPart}).
 */
public class SetupScreen extends Screen {
	/** The window, 960 by 660 in the mockups, in menu pixels. */
	private static final int WINDOW_WIDTH = 614;
	private static final int WINDOW_HEIGHT = 422;
	/** Below this width the window has no room for the right column; the content takes all of it. */
	private static final int WIDE_WIDTH = 560;
	private static final int MARGIN = 12;
	/** The smallest screen the window fits on unscaled; smaller ones scale everything down. */
	private static final int FIT_WIDTH = 440;
	private static final int HEADER = 40;
	private static final int FOOTER = 46;
	private static final int PAD_X = 19;
	private static final int PAD_Y = 16;
	/** How far the content slides when the step changes. */
	private static final float SLIDE = 48f;
	private static final int TILE = 17;
	private static final int DOT = 12;
	/** The dot in the current step's ring, even so it sits in its middle. */
	private static final float CURRENT_DOT = 6f;
	/** The step indicator: a connector's length and the gap on its sides, short enough for the names of all six steps. */
	private static final int CONNECTOR = 10;
	private static final int STEP_GAP = 4;
	private static final int BUTTON_HEIGHT = 26;
	private static final int BACK_HEIGHT = 24;
	private static final int POPOVER_WIDTH = 214;
	private static final int POPOVER_HEIGHT = 128;

	private static final String MARKER = "setup_done";

	static final int WELCOME = 0;
	static final int MENU = 1;
	static final int LOOK = 2;
	static final int PLAY = 3;
	static final int MUSIC = 4;
	static final int READY = 5;
	private static final int STEPS = 6;
	private static final String[] NAMES = {"Welcome", "Main menu", "Look", "How you play", "Music", "Ready"};
	/** How long the way out of the setup fades into the main menu when a different one was picked, in seconds. */
	private static final float MENU_CHANGE_FADE_SECONDS = 0.4f;

	private static boolean opened;
	/** Whether the setup was finished before, read once from its marker file; null until checked. */
	private static Boolean setupDone;
	/** Whether the add-on's part is new to the player, read once off the game's thread; null until asked. */
	private static CompletableFuture<Boolean> addonPartNew;

	private final Screen parent;
	/** Whether only the part of an add-on installed later shows: the "How you play" step alone, without the steps around it. */
	private final boolean addonOnly;
	final SetupChoices choices;
	private final ScreenFit fit = new ScreenFit();
	private final SetupKit kit = new SetupKit();
	private final WelcomeCard welcome = new WelcomeCard(this);
	private final SetupStep[] steps;
	private int step;
	private final Spring stepSpring = Spring.smooth(Spring.DEFAULT);
	private final Spring welcomeSpring = Spring.smooth(Spring.DEFAULT);
	private final Spring openSpring = Spring.bouncy(Spring.GENTLE);
	private final Spring closeSpring = Spring.smooth(Spring.DEFAULT);
	private final Spring skipSpring = Spring.smooth(Spring.SNAPPY);
	private final Spring listSpring = Spring.smooth(Spring.DEFAULT);
	private boolean closing;
	/** What happens once the window is gone: null to go back to the screen it was opened over. */
	private Runnable afterClose;
	private boolean skipOpen;
	private boolean listOpen;
	/** Whether the setup ended on another main menu than the one it was opened over, which it then fades into more softly. */
	private boolean menuChanged;

	private long last = System.nanoTime();

	private SetupScreen(Screen parent, boolean again) {
		this(parent, again, false);
	}

	private SetupScreen(Screen parent, boolean again, boolean addonOnly) {
		super(Component.literal("Lumio"));
		this.parent = parent;
		this.addonOnly = addonOnly;
		this.choices = addonOnly ? SetupChoices.addonPart() : again ? SetupChoices.current() : SetupChoices.fresh();
		this.steps = new SetupStep[]{null, new MenuStep(this), new LookStep(this), new PlayStep(this), new MusicStep(this), new ReadyStep(this)};
		if (addonOnly) {
			step = PLAY;
			stepSpring.snap(PLAY);
			welcomeSpring.snap(0f);
		} else if (again) {
			// the player knows the mod: the setup starts at its first real step
			step = MENU;
			stepSpring.snap(MENU);
			welcomeSpring.snap(0f);
		} else {
			welcomeSpring.snap(1f);
		}
	}

	/** @return the setup running again, from the current settings, over a screen it goes back to */
	public static SetupScreen runAgain(Screen parent) {
		return new SetupScreen(parent, true);
	}

	/** @return only the part of an add-on installed after the setup was finished, over a screen it goes back to */
	public static SetupScreen addonPart(Screen parent) {
		return new SetupScreen(parent, false, true);
	}

	// ------------------------------------------------------------------------------------------------------ when to show

	/** Called every tick: on the first start the setup opens over the main menu once the game loaded and the way into the menu is over. */
	public static void tick(Minecraft client) {
		if (opened || client.level != null || client.gui.overlay() != null || StartupTransition.running()) {
			return;
		}
		// the file is checked once instead of every tick in the menus
		if (setupDone == null) {
			setupDone = Files.exists(ModFiles.file(MARKER));
		}
		if (setupDone) {
			checkAddonPart(client);
			return;
		}
		Screen screen = client.gui.screen();
		if (screen instanceof TitleScreen || screen instanceof MacTitleScreen) {
			opened = true;
			client.setScreenAndShow(new SetupScreen(screen, false));
		}
	}

	/** With the setup finished before: an add-on the player has not seen in it shows its part once, as soon as the main menu is there. */
	private static void checkAddonPart(Minecraft client) {
		String addon = Addons.setupAddon();
		if (addon == null) {
			opened = true;
			return;
		}
		if (addonPartNew == null) {
			addonPartNew = SetupAddons.isNew(addon);
		}
		if (!addonPartNew.isDone()) {
			return;
		}
		Screen screen = client.gui.screen();
		if (!addonPartNew.join()) {
			opened = true;
		} else if (screen instanceof TitleScreen || screen instanceof MacTitleScreen) {
			opened = true;
			client.setScreenAndShow(addonPart(screen));
		}
	}

	/** Remembers that the setup is done, in a file written next to the settings, through a temporary file and off the game's thread. */
	private static void markDone() {
		setupDone = true;
		Path file = ModFiles.file(MARKER);
		CompletableFuture.runAsync(() -> {
			try {
				ModFiles.writeAtomically(file, "The setup of Lumio was done. Delete this file to see it again at the next start.");
			} catch (IOException error) {
				Logs.warn("Could not write the file of the setup", error);
			}
		}, Util.ioPool());
		SetupAddons.remember(Addons.setupAddon());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------------------------------------------- navigation

	int step() {
		return step;
	}

	/** Shows a step: the content slides to it, forward to the left and back to the right. */
	void go(int target) {
		int clamped = Mth.clamp(target, WELCOME, READY);
		if (clamped == step) {
			return;
		}
		if (step == WELCOME || clamped == WELCOME) {
			// between the welcome card and the window there is no slide: the content starts where it is
			stepSpring.snap(Math.max(MENU, clamped));
		}
		step = clamped;
		kit.resetFocus();
		listOpen = false;
		UiSounds.play(UiSounds.Cue.TICK);
		if (steps[step] != null) {
			steps[step].shown();
		}
	}

	private void forward() {
		if (addonOnly) {
			choices.applyAddonPart();
			leaveAddonPart();
			UiSounds.play(UiSounds.Cue.DONE);
		} else if (step == READY) {
			finish();
		} else {
			go(step + 1);
		}
	}

	/** The add-on's part is over: done or skipped, it is not asked again. */
	private void leaveAddonPart() {
		SetupAddons.remember(Addons.setupAddon());
		FeatureManager.saveSoon();
		close(null);
	}

	private void back() {
		if (addonOnly) {
			leaveAddonPart();
		} else if (step > WELCOME) {
			go(step - 1);
		}
	}

	void openSkip() {
		skipOpen = true;
		UiSounds.play(UiSounds.Cue.OPEN);
	}

	private void closeSkip() {
		skipOpen = false;
	}

	void openList() {
		listOpen = true;
		UiSounds.play(UiSounds.Cue.OPEN);
	}

	void closeList() {
		listOpen = false;
	}

	/** Opens a screen over the setup, such as the Spotify sign-in, which comes back to it. */
	void open(Screen screen) {
		minecraft.setScreenAndShow(screen);
	}

	// ---------------------------------------------------------------------------------------------------------- ending

	/** Finishes with what was picked and goes back to the main menu. */
	void finish() {
		boolean before = ModFeatures.MENUS.titleActive();
		choices.apply(false);
		done(before);
		close(null);
	}

	/** Finishes with what was picked so far and the recommendations for the rest. */
	void skipRecommended() {
		boolean before = ModFeatures.MENUS.titleActive();
		choices.recommendMenu();
		choices.apply(true);
		done(before);
		Toasts.info("Recommended setup applied", "Change anything later in the settings.");
		close(null);
	}

	/** Finishes with every feature off and the game's own main menu, so the game looks and plays as without the mod; the look of the mod's menus stays. */
	void skipEverythingOff() {
		boolean before = ModFeatures.MENUS.titleActive();
		SetupChoices.turnEverythingOff();
		done(before);
		Toasts.info("Everything turned off", "Turn features on any time in the settings.");
		close(null);
	}

	/** @param menuBefore whether the mod's main menu was on before the setup applied its choices */
	private void done(boolean menuBefore) {
		menuChanged = menuBefore != ModFeatures.MENUS.titleActive();
		markDone();
		FeatureManager.saveSoon();
		UiSounds.play(UiSounds.Cue.DONE);
	}

	private void close(Runnable after) {
		closing = true;
		afterClose = after;
	}

	/** @return the screen the setup ends on: the one it was opened over, or the main menu that was picked */
	private Screen returnScreen() {
		return parent instanceof TitleScreen || parent instanceof MacTitleScreen || parent == null ? new TitleScreen() : parent;
	}

	// ----------------------------------------------------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// the main menu's world, blurred and darkened, as the setup sits over it
		var world = MenuWorld.snapshot();
		if (world != null) {
			graphics.blit(world, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), 0, 0, width, height, 0f, 1f, 1f, 0f);
		} else {
			MenuBackground.draw(graphics, width, height);
		}
		graphics.fill(0, 0, width, height, 0x9E0A0818);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		// small screens get the whole setup scaled down; on medium ones only the right column goes
		fit.begin(this, FIT_WIDTH, WINDOW_HEIGHT + 2 * MARGIN);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(fit.fit, fit.fit);
		try {
			draw(graphics, Math.round(mouseX / fit.fit), Math.round(mouseY / fit.fit));
		} finally {
			pose.popMatrix();
			Ui.setZoom(1f);
			Ui.setAlpha(1f);
			fit.end(this);
		}
	}

	private void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		Ui.setZoom(fit.fit);
		Ui ui = new Ui(graphics, font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), dt, ModFeatures.MENU.animations());
		MenuWorld.prepareThumbnails();
		float grown = openSpring.follow(ui, 1f);
		float gone = closeSpring.follow(ui, closing ? 1f : 0f);
		if (closing && gone > 0.97f) {
			Ui.setAlpha(1f);
			if (afterClose != null) {
				afterClose.run();
			} else {
				minecraft.setScreenAndShow(returnScreen());
				if (menuChanged && ModFeatures.MENU.animations()) {
					// the main menu changes under the setup: a longer fade than between two menus, so the new one emerges calmly
					ScreenFade.fadeFromNow(MENU_CHANGE_FADE_SECONDS);
				}
			}
			return;
		}
		float fade = Mth.clamp(Math.min(grown, 1f - gone), 0f, 1f);
		kit.begin(ui, mouseX, mouseY);
		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = 0.95f + 0.05f * Math.min(grown, 1.03f) - 0.03f * gone;
		pose.translate(width / 2f, height / 2f);
		pose.scale(scale, scale);
		pose.translate(-width / 2f, -height / 2f);

		float onWelcome = Mth.clamp(welcomeSpring.follow(ui, step == WELCOME ? 1f : 0f), 0f, 1f);
		if (onWelcome < 0.99f) {
			kit.live = step != WELCOME && !skipOpen && !listOpen && !closing;
			drawWindow(ui, fade * (1f - onWelcome));
		}
		if (onWelcome > 0.01f) {
			kit.live = step == WELCOME && !skipOpen && !closing;
			Ui.setAlpha(fade * onWelcome);
			welcome.draw(kit, width, height);
		}
		float list = Mth.clamp(listSpring.follow(ui, listOpen ? 1f : 0f), 0f, 1f);
		if (list > 0.01f && step == PLAY) {
			kit.live = listOpen && !skipOpen && !closing;
			Ui.setAlpha(fade * list);
			drawList(ui);
		}
		float skip = Mth.clamp(skipSpring.follow(ui, skipOpen ? 1f : 0f), 0f, 1f);
		if (skip > 0.01f) {
			kit.live = skipOpen && !closing;
			Ui.setAlpha(fade * skip);
			drawSkip(ui, skip);
		}
		Ui.setAlpha(1f);
		pose.popMatrix();
	}

	private int windowWidth() {
		return Math.min(WINDOW_WIDTH, width - 2 * MARGIN);
	}

	private int windowX() {
		return (width - windowWidth()) / 2;
	}

	private int windowY() {
		return (height - WINDOW_HEIGHT) / 2;
	}

	private void drawWindow(Ui ui, float alpha) {
		int x = windowX();
		int y = windowY();
		int w = windowWidth();
		int h = WINDOW_HEIGHT;
		Palette palette = ui.palette;
		if (palette.glass()) {
			Glass.body(ui, x, y, w, h, Radius.XXL, alpha, true, UiGlow.Target.MENU);
			Glass.edges(ui, x, y, w, h, Radius.XXL, alpha, true, UiGlow.Target.MENU);
		} else {
			HudStyle.panel(ui, x, y, w, h, Radius.XXL, HudStyle.opacity() * alpha, UiGlow.Target.MENU, alpha);
		}
		Ui.setAlpha(alpha);
		int line = Ui.withOpacity(palette.text(), 0.09f);
		ui.fill(x + 1, y + HEADER, w - 2, 1, line);
		ui.fill(x + 1, y + h - FOOTER, w - 2, 1, line);
		drawHeader(ui, x, y, w);
		drawFooter(ui, x, y + h - FOOTER, w);

		// the steps slide past each other in the content area, and only the one shown takes clicks
		int contentX = x + PAD_X;
		int contentY = y + HEADER + PAD_Y;
		int contentWidth = w - 2 * PAD_X;
		int contentHeight = h - HEADER - FOOTER - 2 * PAD_Y;
		boolean wide = w >= WIDE_WIDTH;
		float shown = stepSpring.follow(ui, Math.max(MENU, step));
		// under the full list of "Show all" the step fades out, so the list is the only thing there
		float under = 1f - Mth.clamp(listSpring.value(), 0f, 1f);
		boolean live = kit.live;
		ui.graphics.enableScissor(x + 1, y + HEADER + 1, x + w - 1, y + h - FOOTER);
		var pose = ui.graphics.pose();
		for (int i = MENU; i <= READY; i++) {
			float offset = i - shown;
			if (Math.abs(offset) >= 1f) {
				continue;
			}
			kit.live = live && i == Math.max(MENU, step);
			pose.pushMatrix();
			pose.translate(offset * SLIDE, 0f);
			Ui.setAlpha(alpha * (1f - Math.abs(offset)) * under);
			steps[i].draw(kit, contentX, contentY, contentWidth, contentHeight, wide);
			pose.popMatrix();
		}
		ui.graphics.disableScissor();
		kit.live = live;
		Ui.setAlpha(alpha);
	}

	/** The mod's icon and name, the step indicator (finished steps lead back to themselves), and "Skip setup". */
	private void drawHeader(Ui ui, int x, int y, int w) {
		Palette palette = ui.palette;
		int middle = y + HEADER / 2;
		kit.tile("sparkle", x + 15, middle - TILE / 2, TILE, Ui.mix(ui.accent, 0xFFFFFFFF, 0.15f) & 0xFFFFFF,
			Ui.mix(ui.accent, 0xFF000000, 0.3f) & 0xFFFFFF);
		int nameX = x + 15 + TILE + 7;
		ui.textAtScaledBold("Lumio", nameX, middle - 4f, SetupKit.BODY, palette.text());
		if (addonOnly) {
			kit.centered("New add-on", x + w / 2f, middle, SetupKit.SMALL, true, palette.textSecondary());
			return;
		}
		String skip = "Skip setup";
		int skipWidth = Math.round(ui.widthScaled(skip, SetupKit.SMALL));
		kit.link("skip", skip, x + w - 15 - skipWidth, middle - 3.5f, SetupKit.SMALL, this::openSkip);

		// the step indicator, centered; on a narrow window only the current step keeps its name
		int left = nameX + Math.round(ui.boldWidthScaled("Lumio", SetupKit.BODY)) + 12;
		int right = x + w - 15 - skipWidth - 12;
		int full = indicatorWidth(ui, true);
		boolean names = full <= right - left;
		int total = names ? full : indicatorWidth(ui, false);
		int at = Math.max(left, x + (w - total) / 2);
		for (int i = 0; i < STEPS; i++) {
			if (i > 0) {
				at += STEP_GAP;
				ui.rounded(at, middle - 1, CONNECTOR, 2, Radius.XXS, i <= step ? ui.accent : Ui.withOpacity(palette.text(), 0.16f));
				at += CONNECTOR + STEP_GAP;
			}
			boolean finished = i < step;
			boolean current = i == step;
			String name = stepName(i);
			int labelWidth = names || current ? Math.round(ui.widthScaled(name, SetupKit.SMALL)) : 0;
			int itemWidth = DOT + (labelWidth > 0 ? 5 + labelWidth : 0);
			if (finished) {
				int target = i;
				float hover = kit.area("step" + i, at - 2, middle - 8, itemWidth + 4, 16, Radius.SM, () -> go(target));
				kit.radio(at, middle - DOT / 2, DOT, 1f);
				if (labelWidth > 0) {
					ui.textAtScaled(name, at + DOT + 5, middle - 3.5f, SetupKit.SMALL, Ui.mix(palette.textSecondary(), palette.text(), hover));
				}
			} else if (current) {
				ui.ringAt(at + DOT / 2f, middle, DOT, 2f, ui.accent);
				ui.discAt(at + DOT / 2f, middle, CURRENT_DOT, ui.accent);
				ui.textAtScaledBold(name, at + DOT + 5, middle - 3.5f, SetupKit.SMALL, palette.text());
			} else {
				ui.ring(at, middle - DOT / 2, DOT, DOT, DOT / 2f, 1, Ui.withOpacity(palette.text(), 0.28f));
				if (labelWidth > 0) {
					ui.textAtScaled(name, at + DOT + 5, middle - 3.5f, SetupKit.SMALL, Ui.withOpacity(palette.textSecondary(), 0.7f));
				}
			}
			at += itemWidth;
		}
	}

	/** @return a step's name; without an add-on that asks how the player plays, its step only lists the features */
	private static String stepName(int index) {
		return index == PLAY && SetupGroup.world() == null ? "Features" : NAMES[index];
	}

	private int indicatorWidth(Ui ui, boolean names) {
		int total = (STEPS - 1) * (CONNECTOR + 2 * STEP_GAP) + STEPS * DOT;
		for (int i = 0; i < STEPS; i++) {
			if (names || i == step) {
				total += 5 + Math.round(ui.widthScaled(stepName(i), SetupKit.SMALL));
			}
		}
		return total;
	}

	/** Back, where the setup is, and the button that goes on. */
	private void drawFooter(Ui ui, int x, int y, int w) {
		Palette palette = ui.palette;
		int middle = y + FOOTER / 2;
		String backText = addonOnly ? "Skip" : "Back";
		int backWidth = 10 + 3 + Math.round(ui.widthScaled(backText, SetupKit.BODY)) + 14;
		float back = kit.area("back", x + 10, middle - BACK_HEIGHT / 2, backWidth, BACK_HEIGHT, Radius.MD, this::back);
		if (back > 0.01f) {
			ui.rounded(x + 10, middle - BACK_HEIGHT / 2, backWidth, BACK_HEIGHT, Radius.MD, Ui.withOpacity(palette.text(), 0.07f * back));
		}
		if (!addonOnly) {
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(x + 10 + 9f, middle);
			pose.rotate((float) (Math.PI / 2.0));
			ui.icon("chevron", -4, -4, 8, palette.text());
			pose.popMatrix();
		}
		ui.textAtScaled(backText, x + 10 + (addonOnly ? 10 : 16), middle - 4f, SetupKit.BODY, palette.text());

		String primary = addonOnly ? "Done" : step == READY ? "Start playing" : "Continue";
		int primaryWidth = Math.round(ui.boldWidthScaled(primary, SetupKit.BODY)) + 28;
		kit.primaryButton("continue", primary, x + w - 15 - primaryWidth, middle - BUTTON_HEIGHT / 2, primaryWidth, BUTTON_HEIGHT, this::forward);
		if (!addonOnly) {
			kit.centered("Step " + (step + 1) + " of " + STEPS, x + w / 2f, middle, SetupKit.SMALL, false,
				Ui.withOpacity(palette.textSecondary(), 0.7f));
		}
	}

	/** The full list of "Turned on for you", over the content of the window. */
	private void drawList(Ui ui) {
		int x = windowX();
		int y = windowY() + HEADER + 1;
		int w = windowWidth();
		int h = WINDOW_HEIGHT - HEADER - FOOTER - 1;
		((PlayStep) steps[PLAY]).drawFullList(kit, x + PAD_X, y + PAD_Y, w - 2 * PAD_X, h - 2 * PAD_Y);
	}

	/** The small window that asks how to skip: with the recommendations, with everything off, or not at all. */
	private void drawSkip(Ui ui, float shown) {
		Palette palette = ui.palette;
		int x;
		int y;
		if (step == WELCOME) {
			x = (width - POPOVER_WIDTH) / 2;
			y = (height - POPOVER_HEIGHT) / 2;
		} else {
			x = windowX() + windowWidth() - 15 - POPOVER_WIDTH;
			y = windowY() + HEADER - 6;
		}
		y += Math.round((1f - shown) * -6f);
		ui.shadow(x, y, POPOVER_WIDTH, POPOVER_HEIGHT, Radius.XL, 14, 0.5f);
		HudStyle.panel(ui, x, y, POPOVER_WIDTH, POPOVER_HEIGHT, Radius.XL, 0.97f);
		int inner = POPOVER_WIDTH - 28;
		ui.textAtScaledBold("Skip the setup?", x + 14, y + 13, SetupKit.BODY, palette.text());
		ui.textAtScaled(kit.fit("You can run it again in Menu settings.", inner, SetupKit.SMALL), x + 14, y + 27, SetupKit.SMALL, palette.textSecondary());
		kit.primaryButton("skip_recommended", "Use recommended", x + 14, y + 45, inner, 22, this::skipRecommended);
		kit.secondaryButton("skip_off", "Turn everything off", x + 14, y + 73, inner, 22, this::skipEverythingOff);
		String keep = "Keep going";
		float keepWidth = ui.widthScaled(keep, SetupKit.SMALL);
		kit.link("skip_keep", keep, x + (POPOVER_WIDTH - keepWidth) / 2f, y + 105, SetupKit.SMALL, this::closeSkip);
	}

	// ------------------------------------------------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MouseButtonEvent fitted = fit.event(event);
		if (ScreenInput.button(event) != 0 || closing) {
			return true;
		}
		if (step >= MENU && !skipOpen && !listOpen && steps[step].mouseClicked(fitted.x(), fitted.y())) {
			return true;
		} else if (kit.click(fitted.x(), fitted.y())) {
			UiSounds.click();
		} else if (skipOpen) {
			closeSkip();
		} else if (listOpen) {
			closeList();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		MouseButtonEvent fitted = fit.event(event);
		if (step >= MENU) {
			steps[step].mouseDragged(fitted.x(), fitted.y());
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		MouseButtonEvent fitted = fit.event(event);
		if (step >= MENU) {
			steps[step].mouseReleased(fitted.x(), fitted.y());
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (listOpen && step == PLAY) {
			((PlayStep) steps[PLAY]).scrollList(scrollY);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean shift = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.SHIFT) != 0;
		if (closing) {
			return true;
		}
		if (step >= MENU && !skipOpen && !listOpen && steps[step].keyPressed(event)) {
			return true;
		}
		switch (key) {
			case InputConstants.KEY_ESCAPE -> {
				if (skipOpen) {
					closeSkip();
				} else if (listOpen) {
					closeList();
				} else if (step == WELCOME) {
					openSkip();
				} else {
					back();
				}
			}
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (!skipOpen && !listOpen) {
					if (step == WELCOME) {
						go(MENU);
					} else {
						forward();
					}
				}
			}
			case InputConstants.KEY_SPACE -> {
				if (kit.activateFocus()) {
					UiSounds.click();
				}
			}
			case InputConstants.KEY_TAB -> kit.moveFocus(shift ? -1 : 1);
			case InputConstants.KEY_RIGHT, InputConstants.KEY_DOWN -> kit.moveFocus(1);
			case InputConstants.KEY_LEFT, InputConstants.KEY_UP -> kit.moveFocus(-1);
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!closing && step >= MENU && !skipOpen && !listOpen && steps[step].charTyped(event.codepoint())) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void removed() {
		Ui.setAlpha(1f);
	}
}
