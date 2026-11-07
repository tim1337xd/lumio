package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.features.music.MenuIsland;
import dev.tim1337.lumio.render.MenuWarmup;
import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.ui.title.MenuLyrics;
import dev.tim1337.lumio.ui.title.SeasonPopover;
import dev.tim1337.lumio.ui.title.ServerListView;
import dev.tim1337.lumio.ui.title.StartupTransition;
import dev.tim1337.lumio.ui.title.TitleListView;
import dev.tim1337.lumio.ui.title.WorldListView;
import dev.tim1337.lumio.util.ModVersion;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The mod's main menu over the block world: the account at the top left, the music island in the middle, the clock, the season button and the
 * settings button at the top right, a greeting with the play card and Options and Quit in the left column, and the lyrics at the bottom right.
 * Everything is laid out in the units of its mockup (1440 by 880 or more) and scaled to the screen, so it looks the same at any GUI scale. It
 * performs the same actions as the vanilla main menu buttons.
 */
public class MacTitleScreen extends Screen {
	// the layout, in the mockup's pixels
	private static final float DESIGN_WIDTH = 1440f;
	private static final float DESIGN_HEIGHT = 880f;
	private static final float SIDE = 96f;
	private static final float TOP = 52f;
	private static final float RIGHT = 56f;
	private static final float COLUMN_TOP = 226f;
	private static final float COLUMN_WIDTH = 400f;
	private static final float COLUMN_GAP = 28f;
	private static final float GREETING_HEIGHT = 81f;
	private static final float CARD_PAD = 6f;
	private static final float ROW = 68f;
	private static final float ROW_TILE = 40f;
	private static final float SMALL_BUTTON = 52f;
	private static final float TOP_BUTTON = 40f;
	private static final float TOP_GAP = 10f;
	private static final float LYRICS_LEFT = 560f;
	private static final float LYRICS_RIGHT = 80f;
	private static final float LYRICS_BOTTOM = 76f;
	/** Text scale per font pixel of the mockup. */
	private static final float TEXT = 1f / 9.6f;
	/** The middle of the digits below the top of their line, in font units: lower than the middle of the line, which has room below them. */
	private static final float DIGIT_MIDDLE = 4f;
	private static final int QUIT_COLOR = 0xFFFFB3AB;
	/** The darkening over the scene: its color by day and by night. */
	private static final int SHADE_DAY = 0x0E0A22;
	private static final int SHADE_NIGHT = 0x040614;
	// how the parts come in: one after another, top to bottom, each fading and sliding in
	private static final float ENTRANCE_SECONDS = 0.4f;
	private static final float STAGGER_SECONDS = 0.04f;
	private static final float ENTRANCE_SLIDE = 14f;
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	/** Row indices of the play card, and of the buttons below it. */
	private static final int SINGLEPLAYER = 0;
	private static final int MULTIPLAYER = 1;
	private static final int REJOIN = 2;
	private static final int OPTIONS = 3;
	private static final int QUIT = 4;
	// the buttons at the top right
	private static final int SEASON = 0;
	private static final int SETTINGS = 1;

	private final SeasonPopover seasons = new SeasonPopover();
	// the world and server lists in the left column: the one open, the one drawn (also while it goes away), and how far it is shown
	private final ServerListView serverList = new ServerListView(this);
	private final WorldListView worldList = new WorldListView(this);
	private TitleListView list;
	private TitleListView shownList;
	private final Spring listSpring = Spring.smooth(Spring.DEFAULT);
	private float listShown;
	private final MenuIsland island = new MenuIsland(ModFeatures.MUSIC);
	private final Spring[] rowLight = {Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY),
		Spring.smooth(Spring.SNAPPY)};
	private final Spring[] topLight = {Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY)};
	/** One highlight for the play card's rows: it slides between rows, and fades in where it first appears and out when nothing is hovered. */
	private final Spring highlightFade = Spring.smooth(Spring.SNAPPY);
	private final Spring highlightY = Spring.smooth(Spring.SNAPPY);
	private float highlightAmount;
	private boolean highlightPlaced;
	/** When the parts started coming in. */
	private long openedAt;
	private long lastFrame = System.nanoTime();
	private ResolvableProfile profile;
	/** Name and address of the last joined server, or null. */
	private String[] last;
	/** Whether the server list opens with the screen, as after leaving a server. */
	private boolean openServers;
	/** The name of the world played last, once the worlds are known. */
	private static volatile String lastWorld;
	private static boolean worldsAsked;
	/** The screen to switch to once the parts have animated out; null while staying. */
	private Supplier<Screen> leaveTo;
	private final Spring leaveSpring = Spring.smooth(Spring.DEFAULT);
	// the lyrics: the line shown, the one before it while it leaves, and how far the change is
	private final MenuLyrics lyrics = new MenuLyrics();

	// the layout scale and the size of the screen in layout units, for the frame being drawn
	private float scale = 1f;
	private float layoutWidth = DESIGN_WIDTH;
	private float layoutHeight = DESIGN_HEIGHT;

	public MacTitleScreen() {
		super(Component.literal("Minecraft"));
	}

	/** @return the main menu with its server list open: where the game would show its own server list, such as after leaving a server */
	public static MacTitleScreen withServerList() {
		MacTitleScreen screen = new MacTitleScreen();
		screen.openServers = true;
		return screen;
	}

	/** Switches to another screen with an exit animation: the parts slide away and fade, then the next screen opens. */
	public void leaveTo(Supplier<Screen> next) {
		if (leaveTo == null) {
			leaveTo = next;
		}
	}

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = System.nanoTime();
		}
		askLastWorld();
		if (openServers) {
			openServers = false;
			showList(serverList);
		} else if (list != null) {
			// back from a world, a server or one of the game's screens: the open list reads its worlds or pings its servers again
			list.open();
		}
	}

	@Override
	public void removed() {
		if (list != null) {
			list.close();
		}
	}

	@Override
	public void tick() {
		if (list != null) {
			list.tick();
		}
	}

	/** Shows a list in the left column: the buttons slide out to the left and the list comes in. */
	private void showList(TitleListView shown) {
		list = shown;
		shownList = shown;
		shown.open();
	}

	/** Goes back from a list to the buttons, the same way. */
	private void hideList() {
		if (list != null) {
			list.close();
			list = null;
			UiSounds.play(UiSounds.Cue.CLOSE);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Looks up the world played last in the background, once per game, for the Singleplayer row. */
	private void askLastWorld() {
		if (worldsAsked) {
			return;
		}
		worldsAsked = true;
		var source = minecraft.getLevelSource();
		CompletableFuture.supplyAsync(() -> {
			try {
				return source.findLevelCandidates();
			} catch (Exception error) {
				return null;
			}
		}).thenCompose(candidates -> candidates == null ? CompletableFuture.completedFuture(List.<LevelSummary>of()) : source.loadLevelSummaries(candidates))
			.thenAccept(summaries -> {
				LevelSummary latest = null;
				for (LevelSummary summary : summaries) {
					if (latest == null || summary.getLastPlayed() > latest.getLastPlayed()) {
						latest = summary;
					}
				}
				lastWorld = latest == null ? null : latest.getLevelName();
			}).exceptionally(error -> null);
	}

	/** Forgets the world played last, so it is looked up again: after playing, a different world may be the last one. */
	public static void forgetLastWorld() {
		worldsAsked = false;
	}

	// ---------------------------------------------------------------------------------------------------------------- layout

	private void layout() {
		var window = minecraft.getWindow();
		float realWidth = window.getGuiScaledWidth();
		float realHeight = window.getGuiScaledHeight();
		scale = Math.min(realWidth / DESIGN_WIDTH, realHeight / DESIGN_HEIGHT);
		layoutWidth = realWidth / scale;
		layoutHeight = realHeight / scale;
	}

	private int rows() {
		return last == null ? 2 : 3;
	}

	private float cardTop() {
		return COLUMN_TOP + GREETING_HEIGHT + COLUMN_GAP;
	}

	private float cardHeight() {
		return 2 * CARD_PAD + rows() * ROW;
	}

	private float smallButtonsTop() {
		return cardTop() + cardHeight() + COLUMN_GAP;
	}

	/** @return x, y, width and height of a row of the play card or of Options and Quit */
	private float[] rect(int index) {
		if (index <= REJOIN) {
			return new float[]{SIDE + CARD_PAD, cardTop() + CARD_PAD + index * ROW, COLUMN_WIDTH - 2 * CARD_PAD, ROW};
		}
		float half = (COLUMN_WIDTH - TOP_GAP) / 2f;
		return new float[]{SIDE + (index == QUIT ? half + TOP_GAP : 0f), smallButtonsTop(), half, SMALL_BUTTON};
	}

	private boolean shown(int index) {
		return index != REJOIN || last != null;
	}

	private boolean enabled(int index) {
		return (index != MULTIPLAYER && index != REJOIN) || minecraft.allowsMultiplayer();
	}

	/** @return x of a button at the top right; the clock pill is left of them */
	private float topButtonX(int index) {
		return layoutWidth - RIGHT - TOP_BUTTON - (SETTINGS - index) * (TOP_BUTTON + TOP_GAP);
	}

	private static boolean inside(float[] r, double mouseX, double mouseY) {
		return mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
	}

	/** @return how far a part has come in, from 0 to 1, by its place in the order */
	private float entrance(int order) {
		float t = (System.nanoTime() - openedAt) / 1_000_000_000f - order * STAGGER_SECONDS;
		float p = Mth.clamp(t / ENTRANCE_SECONDS, 0f, 1f);
		return 1f - (1f - p) * (1f - p) * (1f - p);
	}

	// ----------------------------------------------------------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// the main menu always shows the block world; only if the graphics card cannot draw it, the other background is used. While the Mojang
		// screen still covers all of it at game start, it is not drawn at all
		if (StartupTransition.coversMenu()) {
			return;
		}
		if (!MenuWorld.draw(graphics, mouseX / Math.max(1f, width), mouseY / Math.max(1f, height))) {
			MenuBackground.draw(graphics, width, height);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		if (!StartupTransition.running()) {
			// the loading background and the season pictures, a slice per frame once the way into the menu is over (it does them itself)
			MenuWarmup.step();
		}
		float dt = Math.min(1f / 30f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		// at game start the transition moves the camera and decides when the parts come in
		long uiStart = StartupTransition.beforeMenu();
		if (uiStart != 0L) {
			openedAt = uiStart;
		}
		if (StartupTransition.coversMenu()) {
			// the Mojang screen still covers all of the menu: nothing of it is drawn, so no shader of it is used before it is compiled
			return;
		}
		float leave = leaveSpring.step(leaveTo != null ? 1f : 0f, dt);
		if (leaveTo != null && leave > 0.93f) {
			Screen next = leaveTo.get();
			leaveTo = null;
			leaveSpring.snap(0f);
			minecraft.gui.setScreen(next);
			return;
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		layout();
		last = ModFeatures.MENUS.lastServer();
		Ui ui = new Ui(graphics, font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), dt, true);
		// in screen pixels: drawn scaled, the gradient pieces left a seam between them
		drawShade(ui);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(scale, scale);
		Ui.setZoom(scale);
		int mx = Math.round(mouseX / scale);
		int my = Math.round(mouseY / scale);
		// when leaving, the parts move left, shrink slightly and fade
		pose.pushMatrix();
		pose.translate(-leave * 90f, 0f);
		pose.translate(layoutWidth / 2f, layoutHeight / 2f);
		pose.scale(1f - 0.06f * leave, 1f - 0.06f * leave);
		pose.translate(-layoutWidth / 2f, -layoutHeight / 2f);
		Ui.setAlpha(Mth.clamp(1f - leave * 1.15f, 0f, 1f));
		boolean popover = seasons.isOpen();
		int pointerX = popover || leaveTo != null ? -10000 : mx;
		int pointerY = popover || leaveTo != null ? -10000 : my;

		drawAccount(ui, entrance(0));
		island.draw(ui, layoutWidth / 2f, pointerX, pointerY, entrance(1));
		drawTopRight(ui, pointerX, pointerY);
		// the buttons slide out to the left while a list is shown, and the list comes in where they were
		listShown = Mth.clamp(listSpring.follow(ui, list != null ? 1f : 0f), 0f, 1f);
		float columnOut = listShown;
		int columnX = list != null ? -10000 : pointerX;
		pose.pushMatrix();
		pose.translate(-60f * columnOut, 0f);
		float columnAlpha = Ui.alpha();
		Ui.setAlpha(columnAlpha * (1f - columnOut));
		if (columnOut < 0.99f) {
			drawGreeting(ui, entrance(5));
			drawCard(ui, columnX, pointerY, entrance(6));
			drawSmallButtons(ui, columnX, pointerY, entrance(7));
		}
		Ui.setAlpha(columnAlpha);
		pose.popMatrix();
		if (shownList != null) {
			if (list == null && listShown < 0.01f) {
				shownList.dispose();
				shownList = null;
			} else {
				serverList.scale = scale;
				shownList.draw(ui, layoutHeight, list != null ? pointerX : -10000, pointerY, list != null ? listShown : listShown * listShown);
			}
		}
		drawLyrics(ui, entrance(8));
		drawFooter(ui, entrance(9));
		if (list != null) {
			list.drawMenu(ui, mx, my);
		}
		seasons.draw(ui, layoutWidth - RIGHT, TOP + TOP_BUTTON + 10f, mx, my);
		Ui.setAlpha(1f);
		pose.popMatrix();
		pose.popMatrix();
		Ui.setZoom(1f);
	}

	/** Darkens the scene on the left, where the column is, and slightly at the top and bottom, so the text reads on any season and hour. */
	private void drawShade(Ui ui) {
		float hour = ModFeatures.MENUS.worldHour();
		boolean night = hour < 6f || hour >= 20f;
		int shade = night ? SHADE_NIGHT : SHADE_DAY;
		int width = this.width;
		int height = this.height;
		int third = Math.round(width * 0.3f);
		ui.horizontalGradient(0, 0, third, height, Ui.withOpacity(0xFF000000 | shade, night ? 0.62f : 0.66f), Ui.withOpacity(0xFF000000 | shade, night ? 0.30f : 0.34f));
		ui.horizontalGradient(third, 0, Math.round(width * 0.5f) - third, height, Ui.withOpacity(0xFF000000 | shade, night ? 0.30f : 0.34f), shade);
		int bottom = Math.round(height * 0.24f);
		ui.verticalGradient(0, height - bottom, width, bottom, shade, Ui.withOpacity(0xFF000000 | shade, 0.40f));
		int top = Math.round(height * 0.16f);
		ui.verticalGradient(0, 0, width, top, Ui.withOpacity(0xFF000000 | shade, 0.30f), shade);
	}

	private void drawAccount(Ui ui, float in) {
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(0f, (1f - in) * -ENTRANCE_SLIDE);
		String name = minecraft.getUser() != null ? minecraft.getUser().getName() : "player";
		if (profile == null && minecraft.getUser() != null) {
			profile = ResolvableProfile.createUnresolved(minecraft.getUser().getProfileId());
		}
		int face = Math.round(ROW_TILE);
		if (profile != null && in > 0.3f) {
			PlayerFaceExtractor.extractRenderState(ui.graphics, profile, Math.round(SIDE), Math.round(TOP), face);
		}
		ui.ring(Math.round(SIDE) - 1, Math.round(TOP) - 1, face + 2, face + 2, 4f, 1, Ui.withOpacity(0xFFFFFFFF, 0.28f * in));
		ui.textAtScaledBold(name, SIDE + face + 12f, TOP + 4f, 15f * TEXT, Ui.withOpacity(0xFFFFFFFF, in));
		ui.textAtScaled("Minecraft account", SIDE + face + 12f, TOP + 23f, 12.5f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.66f * in));
		pose.popMatrix();
	}

	private void drawTopRight(Ui ui, int mouseX, int mouseY) {
		var pose = ui.graphics.pose();
		// the clock, in a pill left of the buttons
		float clockIn = entrance(2);
		String time = LocalTime.now().format(CLOCK);
		float clockScale = 15f * TEXT;
		float pillWidth = ui.boldWidthScaled(time, clockScale) + 32f;
		float pillX = topButtonX(SEASON) - TOP_GAP - pillWidth;
		pose.pushMatrix();
		pose.translate(0f, (1f - clockIn) * -ENTRANCE_SLIDE);
		HudStyle.panel(ui, Math.round(pillX), Math.round(TOP), Math.round(pillWidth), Math.round(TOP_BUTTON), TOP_BUTTON / 2f, HudStyle.opacity() * clockIn,
			UiGlow.Target.TITLE, clockIn);
		ui.textAtScaledBold(time, pillX + 16f, TOP + TOP_BUTTON / 2f - DIGIT_MIDDLE * clockScale, clockScale, Ui.withOpacity(0xFFFFFFFF, clockIn));
		pose.popMatrix();
		for (int i = SEASON; i <= SETTINGS; i++) {
			float in = entrance(3 + i);
			float bx = topButtonX(i);
			boolean over = mouseX >= bx && mouseX < bx + TOP_BUTTON && mouseY >= TOP && mouseY < TOP + TOP_BUTTON;
			float light = Mth.clamp(topLight[i].follow(ui, over ? 1f : 0f), 0f, 1f);
			pose.pushMatrix();
			pose.translate(0f, (1f - in) * -ENTRANCE_SLIDE);
			HudStyle.panel(ui, Math.round(bx), Math.round(TOP), Math.round(TOP_BUTTON), Math.round(TOP_BUTTON), TOP_BUTTON / 2f, HudStyle.opacity() * in,
				UiGlow.Target.TITLE, in);
			// the season button is filled with the accent while its menu is open
			float filled = i == SEASON ? seasons.amount() : 0f;
			if (filled > 0.01f) {
				ui.roundedSmooth(bx, TOP, TOP_BUTTON, TOP_BUTTON, TOP_BUTTON / 2f, Ui.withOpacity(ui.accent, filled * in));
			}
			if (light > 0.01f) {
				ui.roundedSmooth(bx, TOP, TOP_BUTTON, TOP_BUTTON, TOP_BUTTON / 2f, Ui.withOpacity(0xFFFFFFFF, 0.12f * light * in));
			}
			String icon = i == SETTINGS ? "gear" : seasonIcon();
			ui.icon(icon, Math.round(bx + 11f), Math.round(TOP + 11f), 18, Ui.withOpacity(0xFFFFFFFF, in));
			pose.popMatrix();
		}
	}

	/** @return the icon of the season on screen: blossom, sun, leaf or snowflake */
	private static String seasonIcon() {
		return switch (ModFeatures.MENUS.worldSeason()) {
			case SPRING -> "blossom";
			case SUMMER -> "sun";
			case AUTUMN -> "leaf";
			case WINTER -> "snow";
		};
	}

	private void drawGreeting(Ui ui, float in) {
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate((1f - in) * -ENTRANCE_SLIDE, 0f);
		String name = minecraft.getUser() != null ? minecraft.getUser().getName() : "player";
		String greeting = greeting() + ", " + name;
		ui.textAtScaledBold(greeting.toUpperCase(java.util.Locale.ROOT), SIDE, COLUMN_TOP, 13f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.78f * in));
		float titleScale = 54f * TEXT;
		MenuLyrics.softShadow(ui, "Ready to play?", SIDE, COLUMN_TOP + 26f, titleScale, Ui.withOpacity(0xFF0A061E, 0.9f * in), 3f, MenuLyrics.TITLE_FONT);
		ui.textAtScaledTitle("Ready to play?", SIDE, COLUMN_TOP + 26f, titleScale, Ui.withOpacity(0xFFFFFFFF, in));
		pose.popMatrix();
	}

	/** @return the greeting for the hour of the day */
	private static String greeting() {
		int hour = LocalTime.now().getHour();
		if (hour < 5 || hour >= 22) {
			return "Good night";
		}
		return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
	}

	private void drawCard(Ui ui, int mouseX, int mouseY, float in) {
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate((1f - in) * -ENTRANCE_SLIDE, 0f);
		float top = cardTop();
		ui.shadow(Math.round(SIDE), Math.round(top), Math.round(COLUMN_WIDTH), Math.round(cardHeight()), 22f, 18, 0.35f * in);
		HudStyle.panel(ui, Math.round(SIDE), Math.round(top), Math.round(COLUMN_WIDTH), Math.round(cardHeight()), 22f, HudStyle.opacity() * in,
			UiGlow.Target.TITLE, in);
		// the highlight slides to the hovered row
		int pointed = -1;
		for (int i = SINGLEPLAYER; i <= REJOIN; i++) {
			if (shown(i) && enabled(i) && inside(rect(i), mouseX, mouseY)) {
				pointed = i;
			}
		}
		highlightAmount = Mth.clamp(highlightFade.follow(ui, pointed >= 0 ? 1f : 0f), 0f, 1f);
		if (pointed >= 0) {
			if (!highlightPlaced || highlightAmount < 0.04f) {
				highlightY.snap(rect(pointed)[1]);
				highlightPlaced = true;
			}
		}
		float lightY = highlightY.follow(ui, pointed >= 0 ? rect(pointed)[1] : highlightY.value());
		if (highlightAmount > 0.01f) {
			ui.roundedSmooth(SIDE + CARD_PAD, lightY, COLUMN_WIDTH - 2 * CARD_PAD, ROW, Radius.XXL, Ui.withOpacity(0xFFFFFFFF, 0.12f * highlightAmount * in));
			ui.roundedSmooth(SIDE + CARD_PAD + 12f, lightY, COLUMN_WIDTH - 2 * CARD_PAD - 24f, 1f, 0.5f, Ui.withOpacity(0xFFFFFFFF, 0.10f * highlightAmount * in));
		}
		for (int i = SINGLEPLAYER; i <= REJOIN; i++) {
			if (!shown(i)) {
				continue;
			}
			float[] r = rect(i);
			float light = Mth.clamp(rowLight[i].follow(ui, i == pointed ? 1f : 0f), 0f, 1f);
			int tile = enabled(i) ? 0xFF000000 | tileColor(i) : 0xFF48484A;
			int tileX = Math.round(r[0] + 12f);
			int tileY = Math.round(r[1] + (ROW - ROW_TILE) / 2f);
			ui.iconTile(rowIcon(i), tileX, tileY, Math.round(ROW_TILE), 20, Radius.LG, Ui.withOpacity(tile, in), Ui.withOpacity(0xFFFFFFFF, in));
			float textX = r[0] + 12f + ROW_TILE + 14f;
			int nameColor = enabled(i) ? 0xFFFFFFFF : 0xFF8E8E93;
			ui.textAtScaledBold(rowName(i), textX, r[1] + 17f, 16.5f * TEXT, Ui.withOpacity(nameColor, in));
			ui.textAtScaled(ui.fitScaled(rowNote(i), r[2] - (textX - r[0]) - 40f, 13f * TEXT), textX, r[1] + 40f, 13f * TEXT,
				Ui.withOpacity(0xFFFFFFFF, 0.64f * in));
			// the chevron, which moves a little to the right on hover
			pose.pushMatrix();
			pose.translate(r[0] + r[2] - 16f - 8f + light * 2f, r[1] + ROW / 2f);
			pose.rotate((float) (-Math.PI / 2.0));
			ui.icon("chevron", -8, -8, 16, Ui.withOpacity(0xFFFFFFFF, 0.55f * in));
			pose.popMatrix();
		}
		pose.popMatrix();
	}

	private void drawSmallButtons(Ui ui, int mouseX, int mouseY, float in) {
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate((1f - in) * -ENTRANCE_SLIDE, 0f);
		for (int i = OPTIONS; i <= QUIT; i++) {
			float[] r = rect(i);
			boolean over = inside(r, mouseX, mouseY);
			float light = Mth.clamp(rowLight[i].follow(ui, over ? 1f : 0f), 0f, 1f);
			HudStyle.panel(ui, Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3]), Radius.XXL, HudStyle.opacity() * in, UiGlow.Target.TITLE, in);
			if (light > 0.01f) {
				ui.roundedSmooth(r[0], r[1], r[2], r[3], Radius.XXL, Ui.withOpacity(0xFFFFFFFF, 0.10f * light * in));
			}
			int color = i == QUIT ? QUIT_COLOR : 0xFFFFFFFF;
			String label = i == QUIT ? "Quit" : "Options";
			float labelScale = 15f * TEXT;
			float content = 17f + 10f + ui.widthScaled(label, labelScale);
			float startX = r[0] + (r[2] - content) / 2f;
			ui.icon(i == QUIT ? "power" : "sliders", Math.round(startX), Math.round(r[1] + (r[3] - 17f) / 2f), 17, Ui.withOpacity(color, in));
			ui.textAtScaled(label, startX + 27f, r[1] + r[3] / 2f - 4.5f * labelScale, labelScale, Ui.withOpacity(color, in));
		}
		pose.popMatrix();
	}

	private int tileColor(int index) {
		return switch (index) {
			case SINGLEPLAYER -> SystemColors.GREEN_RGB;
			case MULTIPLAYER -> SystemColors.BLUE_RGB;
			default -> SystemColors.ORANGE_RGB;
		};
	}

	private static String rowIcon(int index) {
		return switch (index) {
			case SINGLEPLAYER -> "person";
			case MULTIPLAYER -> "people";
			default -> "reset";
		};
	}

	private static String rowName(int index) {
		return switch (index) {
			case SINGLEPLAYER -> "Singleplayer";
			case MULTIPLAYER -> "Multiplayer";
			default -> "Rejoin";
		};
	}

	private String rowNote(int index) {
		return switch (index) {
			case SINGLEPLAYER -> lastWorld == null ? "Your worlds" : "Last played: " + lastWorld;
			case MULTIPLAYER -> "Your servers";
			default -> last == null ? "" : last[1];
		};
	}

	/** The sung line and the next one, without a box, with a soft shadow, centered in the lower right part of the screen. */
	private void drawLyrics(Ui ui, float in) {
		var music = ModFeatures.MUSIC;
		boolean on = ModFeatures.MENUS.menuLyrics() && music.isEnabled() && music.hasSong();
		float centerX = (LYRICS_LEFT + layoutWidth - LYRICS_RIGHT) / 2f;
		float room = layoutWidth - LYRICS_RIGHT - LYRICS_LEFT;
		lyrics.draw(ui, on ? music.menuLyricNow() : null, on ? music.menuLyricNext() : "", centerX, layoutHeight - LYRICS_BOTTOM, room, 32f * TEXT,
			19f * TEXT, in);
	}

	/** The soft shadow of the lines at the bottom. */
	private static final int FOOTER_SHADOW = 0xFF0A061E;
	private static final float FOOTER_SHADOW_RADIUS = 2f;

	private void drawFooter(Ui ui, float in) {
		float textScale = 12f * TEXT;
		float bottom = layoutHeight - 40f;
		String line = "Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric · Lumio " + ModVersion.display();
		// soft shadows, as under the title: the fields behind them can be bright
		String copyright = "Copyright Mojang AB. Do not distribute!";
		int shadow = Ui.withOpacity(FOOTER_SHADOW, 0.9f * in);
		MenuLyrics.softShadow(ui, line, SIDE, bottom - 32f, textScale, shadow, FOOTER_SHADOW_RADIUS, MenuLyrics.REGULAR_FONT);
		MenuLyrics.softShadow(ui, copyright, SIDE, bottom - 14f, textScale, shadow, FOOTER_SHADOW_RADIUS, MenuLyrics.REGULAR_FONT);
		ui.textAtScaled(line, SIDE, bottom - 32f, textScale, Ui.withOpacity(0xFFFFFFFF, 0.92f * in));
		ui.textAtScaled(copyright, SIDE, bottom - 14f, textScale, Ui.withOpacity(0xFFFFFFFF, 0.78f * in));
	}

	// ------------------------------------------------------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		layout();
		if (StartupTransition.running()) {
			skipTransition();
			return true;
		}
		double mx = event.x() / scale;
		double my = event.y() / scale;
		if (ScreenInput.button(event) != 0 || leaveTo != null) {
			return super.mouseClicked(event, doubleClick);
		}
		if (seasons.click(mx, my, event.hasShiftDown())) {
			return true;
		}
		if (island.click(mx, my)) {
			return true;
		}
		for (int i = SEASON; i <= SETTINGS; i++) {
			float bx = topButtonX(i);
			if (mx >= bx && mx < bx + TOP_BUTTON && my >= TOP && my < TOP + TOP_BUTTON) {
				if (i == SEASON) {
					seasons.toggle();
				} else {
					UiSounds.click();
					MacScreen.openFrom(this);
				}
				return true;
			}
		}
		if (list != null) {
			return list.click(mx, my, ScreenInput.button(event), this::hideList) || super.mouseClicked(event, doubleClick);
		}
		for (int i = SINGLEPLAYER; i <= QUIT; i++) {
			if (shown(i) && enabled(i) && inside(rect(i), mx, my)) {
				press(i);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return list != null ? list.scroll(vertical) : super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return list != null && list.charTyped(event.codepoint()) || super.charTyped(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return seasons.drag(event.x() / scale, event.hasShiftDown()) || ModFeatures.MUSIC.menuCardDrag(event.x() / scale) || list != null && list.drag(event.y() / scale) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (seasons.release()) {
			return true;
		}
		if (list != null) {
			list.release();
		}
		return ModFeatures.MUSIC.menuCardRelease() || super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (StartupTransition.running()) {
			skipTransition();
			return true;
		}
		if (event.key() == InputConstants.KEY_ESCAPE && seasons.isOpen()) {
			seasons.close();
			UiSounds.play(UiSounds.Cue.CLOSE);
			return true;
		}
		if (seasons.key(event.key(), event.hasShiftDown())) {
			return true;
		}
		if (list != null && list.key(event.key(), ScreenInput.modifiers(event.modifiers()), this::hideList)) {
			return true;
		}
		return super.keyPressed(event);
	}

	/** Skips the startup transition: the camera rests and the parts are shown at once, under a quick fade from the frame on screen. */
	private void skipTransition() {
		StartupTransition.skip();
		openedAt = System.nanoTime() - (long) ((ENTRANCE_SECONDS + 10 * STAGGER_SECONDS) * 1_000_000_000L);
	}

	private void press(int index) {
		UiSounds.click();
		switch (index) {
			case MULTIPLAYER -> {
				// the game's warning about online play comes first until it was confirmed once; then the menu's own list, unless set to the game's
				if (ModFeatures.MENUS.listsEnhanced() && minecraft.options.skipMultiplayerWarning) {
					showList(serverList);
				} else {
					leaveTo(() -> minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
				}
			}
			case SINGLEPLAYER -> {
				if (ModFeatures.MENUS.listsEnhanced()) {
					showList(worldList);
				} else {
					leaveTo(() -> new SelectWorldScreen(this));
				}
			}
			case OPTIONS -> leaveTo(() -> new MacOptionsScreen(this, minecraft.options));
			case QUIT -> minecraft.stop();
			default -> rejoin();
		}
	}

	private void rejoin() {
		String[] server = last;
		if (server == null) {
			return;
		}
		// Use the server's entry from the server list if present: it carries the resource pack choice (so the game does not ask again, and mods
		// that cache the pack find it), and choices made now are saved to the list. A fresh entry each time knew none of that.
		ServerData data = null;
		try {
			var list = new ServerList(minecraft);
			list.load();
			data = list.get(server[1]);
			if (data == null) {
				// a hidden entry, as the game's Direct Connect makes one: choices made while joining are kept in it
				data = new ServerData(server[0], server[1], ServerData.Type.OTHER);
				list.add(data, true);
				list.save();
			}
		} catch (RuntimeException ignored) {
			// the server list could not be read: join with a fresh entry
		}
		if (data == null) {
			data = new ServerData(server[0], server[1], ServerData.Type.OTHER);
		}
		ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
	}
}
