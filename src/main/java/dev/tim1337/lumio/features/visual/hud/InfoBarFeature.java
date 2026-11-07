package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.InfoSegment;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.hud.PanelLook;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.OrderedListSetting.Option;
import dev.tim1337.lumio.config.setting.OrderedListSetting;
import dev.tim1337.lumio.config.setting.PreviewSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.ScreenTitles;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.PanelDepth;
import dev.tim1337.lumio.ui.PanelTheme;
import dev.tim1337.lumio.ui.Ui;


import com.google.gson.JsonObject;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A bar at the top of the screen with the information players look for: the time, frame rate, connection, where they are and the song that
 * plays. Its segments are picked and ordered like the scoreboard lines, each with its own small options, and it comes in a few styles. Values
 * stay white while all is fine and take a color only when something is off.
 */
public class InfoBarFeature extends Feature implements EditableHud {
	public enum Style {
		BOX("Box"),
		PILL("Pill"),
		STACKED("Stacked"),
		CHIPS("Chips"),
		MINIMAL("Minimal");

		private final Component displayName;

		Style(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	public enum Clock {
		REAL("Real time"),
		GAME("Game time");

		private final Component displayName;

		Clock(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/**
	 * The width of the song segment while it shows the lyrics, as three presets of the exact width; in pixels of the mockups at the default size,
	 * where 230 is the width it had before.
	 */
	public enum SongSize {
		SMALL("Small", 230f),
		MEDIUM("Medium", 340f),
		LARGE("Large", 460f);

		private final Component displayName;
		final float width;

		SongSize(String displayName, float width) {
			this.displayName = Component.literal(displayName);
			this.width = width;
		}

		public Component displayName() {
			return displayName;
		}

		/** @return the preset nearest to a width */
		static SongSize nearest(float width) {
			SongSize best = MEDIUM;
			for (SongSize size : values()) {
				if (Math.abs(size.width - width) < Math.abs(best.width - width)) {
					best = size;
				}
			}
			return best;
		}
	}

	/** When the song segment shows the song. */
	public enum SongWhen {
		ALWAYS("Always"),
		NO_LYRICS("When no lyrics are shown");

		private final Component displayName;

		SongWhen(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The key of the segment list. */
	public static final String SEGMENTS_KEY = "segments";
	private static final int PREVIEW_HEIGHT = 96;
	/** The song width setting: its key, its range in pixels of the mockups, and how many GUI pixels one of them is (230 of them were 145). */
	private static final String SONG_WIDTH_KEY = "song_width";
	private static final float SONG_WIDTH_LEAST = 200f;
	private static final float SONG_WIDTH_MOST = 600f;
	private static final float MOCKUP_TO_GUI = 145f / 230f;

	// the segments' own options, edited in their rows of the segment list
	private final EnumSetting<Clock> clock = add(new EnumSetting<>("clock", Component.literal("Time"), Clock.REAL, Clock::displayName, EnumSetting.Style.SEGMENTED));
	private final BooleanSetting hours24 = add(new BooleanSetting("hours_24", Component.literal("24 hour clock"), true));
	private final BooleanSetting fpsLine = add(new BooleanSetting("fps_line", Component.literal("Line of the last 10 seconds"), true));
	private final FloatSetting fpsYellow = add(new FloatSetting("fps_yellow", Component.literal("Yellow below"), 60f, 5f, 360f, 0)).atLeast(this::fpsRedValue);
	private final FloatSetting fpsRed = add(new FloatSetting("fps_red", Component.literal("Red below"), 30f, 5f, 360f, 0)).atMost(this::fpsYellowValue);
	private final BooleanSetting pingBars = add(new BooleanSetting("ping_bars", Component.literal("Signal bars"), true));
	private final BooleanSetting pingNumber = add(new BooleanSetting("ping_ms", Component.literal("Milliseconds"), true));
	private final FloatSetting pingYellow = add(new FloatSetting("ping_yellow", Component.literal("Yellow from"), 80f, 10f, 1000f, 0)).atMost(this::pingRedValue);
	private final FloatSetting pingRed = add(new FloatSetting("ping_red", Component.literal("Red from"), 150f, 10f, 1000f, 0)).atLeast(this::pingYellowValue);
	private final FloatSetting tpsYellow = add(new FloatSetting("tps_yellow", Component.literal("Yellow below"), 19.5f, 1f, 20f, 1)).atLeast(this::tpsOrangeValue);
	private final FloatSetting tpsOrange = add(new FloatSetting("tps_orange", Component.literal("Orange below"), 18f, 1f, 20f, 1)).atMost(this::tpsYellowValue);
	private final EnumSetting<SongWhen> songWhen = add(new EnumSetting<>("song_when", Component.literal("Show the song"), SongWhen.ALWAYS, SongWhen::displayName,
		EnumSetting.Style.DROPDOWN));
	/** The song segment's width with the lyrics in it: the exact width, and its presets; scaled with the bar. */
	private final FloatSetting songWidth = add(new FloatSetting(SONG_WIDTH_KEY, Component.literal("Exact song width"), SongSize.MEDIUM.width, SONG_WIDTH_LEAST,
		SONG_WIDTH_MOST, 0));
	private final DerivedEnumSetting<SongSize> songSize = add(new DerivedEnumSetting<>("song_size", Component.literal("Song width"), SongSize.MEDIUM,
		SongSize::displayName, EnumSetting.Style.SEGMENTED, () -> SongSize.nearest(songWidth.get()), size -> songWidth.set(size.width)));
	private final BooleanSetting coordsY = add(new BooleanSetting("coords_y", Component.literal("Height (Y)"), true));
	private final BooleanSetting facing = add(new BooleanSetting("facing", Component.literal("Facing direction"), true));

	private final EnumSetting<Style> style = add(new EnumSetting<>("style", Component.literal("Style"), Style.BOX, Style::displayName, EnumSetting.Style.SEGMENTED), "Segments");
	{
		// the bar as it looks with these settings, above its segment list
		add(new PreviewSetting("preview", Component.literal("Preview"), PREVIEW_HEIGHT, this::drawSettingsPreview), "Segments");
	}
	private final OrderedListSetting segments = add(new OrderedListSetting(SEGMENTS_KEY, Component.literal("Segments and their order"), List.of(
		segment(InfoBarSegment.CLOCK, clock, hours24),
		segment(InfoBarSegment.FPS, fpsLine, fpsYellow, fpsRed),
		segment(InfoBarSegment.PING, pingBars, pingNumber, pingYellow, pingRed),
		segment(InfoBarSegment.TPS, tpsYellow, tpsOrange),
		segment(InfoBarSegment.AREA),
		segment(InfoBarSegment.SONG, songWhen, songSize, songWidth),
		segment(InfoBarSegment.COORDS, coordsY, facing),
		segment(InfoBarSegment.MEMORY),
		segment(InfoBarSegment.CPS))).defaultLayout(List.of("clock", "fps", "ping", "tps", "area", "song")), "Segments",
		"Switch them on or off, drag to order; click one for its options");

	private final Spring awayFromTitle = Spring.smooth(Spring.DEFAULT);
	private static final float AWAY_MARGIN = 2f;
	/** Where the bar's look comes from: the global HUD theme, or its own settings below. */
	private final EnumSetting<PanelLook.Mode> lookMode = add(new EnumSetting<>("look_mode", Component.literal("Look"), PanelLook.Mode.THEME, PanelLook.Mode::displayName,
		EnumSetting.Style.SEGMENTED), "Panel");
	private final PanelLook look = new PanelLook(lookMode);
	private final FloatSetting scale = add(new FloatSetting("scale", Component.literal("Size"), 1f, 0.3f, 3f, 2).percent(), "Panel");
	private final BooleanSetting background = add(new BooleanSetting("background", Component.literal("Background"), true), "Panel");
	private final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Glow"), true), "Panel");
	private final BooleanSetting glowSoft = add(new BooleanSetting("glow_soft", Component.literal("Soft glow"), false), "Panel");
	private final EnumSetting<PanelLook.Glow> glowRow = add(PanelLook.glowRow("glow_row", glow, glowSoft), "Panel");
	/** Position set in the HUD editor: offset from the screen center and from the top. */
	private final FloatSetting xOffset = add(new FloatSetting("x_offset", Component.literal("Horizontal place"), 0f, -4000f, 4000f, 0), "Panel");
	private final FloatSetting top = add(new FloatSetting("top", Component.literal("Distance from top"), 6f, 0f, 4000f, 0), "Panel");
	/** The bar's theme: the HUD theme, or its own glass, dark or light. */
	private final EnumSetting<PanelTheme> panelTheme = add(new EnumSetting<>("panel_theme",
		Component.literal("Theme"), PanelTheme.HUD, PanelTheme::displayName, EnumSetting.Style.SEGMENTED), "Panel");
	/** The bar's depth effect: as set in the menu settings, or on or off for this bar; also chosen in its card in the HUD editor. */
	private final EnumSetting<PanelDepth> panelDepth = add(new EnumSetting<>("panel_depth",
		Component.literal("Depth effect"), PanelDepth.MENU, PanelDepth::displayName, EnumSetting.Style.SEGMENTED), "Panel");

	private final InfoBarValues values = new InfoBarValues(this);
	private final InfoBarRenderer renderer = new InfoBarRenderer(this, values);
	/** Whether the bar is drawn for a preview, which shows it also outside a world (in the main menu). */
	private boolean previewing;

	public InfoBarFeature() {
		super("info_bar", "Info bar", "The time, frame rate, connection, where you are and the song, at the top of the screen.", true);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("gauge");
		summary("Time, frame rate, ping and more at the top of the screen");
		xOffset.visibleWhen(() -> false);
		top.visibleWhen(() -> false);
		for (Setting<?> own : List.of(clock, hours24, fpsLine, fpsYellow, fpsRed, pingBars, pingNumber, pingYellow, pingRed, tpsYellow, tpsOrange, songWhen,
			coordsY, facing)) {
			// shown in the rows of the segment list, not as rows of the page
			own.visibleWhen(() -> false);
		}
		background.visibleWhen(() -> style.get() != Style.MINIMAL);
		glowRow.visibleWhen(() -> style.get() != Style.MINIMAL && background.get());
		glow.visibleWhen(() -> false);
		glowSoft.visibleWhen(() -> false);
		look.follow(scale, () -> ModFeatures.MENU.hudSize());
		look.follow(background, () -> ModFeatures.MENU.hudBackground());
		look.follow(glow, () -> ModFeatures.MENU.hudGlow());
		look.follow(glowSoft, () -> ModFeatures.MENU.hudGlowSoft(), () -> ModFeatures.MENU.hudGlow());
		look.follow(panelTheme, () -> PanelTheme.HUD);
		look.follow(panelDepth, () -> PanelDepth.MENU);
		addResetButton();
	}

	private ActionRowSetting lookRow;
	private SettingsView lookView;

	/** The bar's look in one sheet, as on the panels. */
	private SettingsView lookView() {
		if (lookView == null) {
			lookView = look.sheet(id(), List.of(new SettingsView.Group("Theme", List.of(panelTheme, background)),
				new SettingsView.Group("Glow and depth", List.of(glowRow, panelDepth)),
				new SettingsView.Group("Size", List.of(scale))));
		}
		return lookView;
	}

	/** The bar's own rows, and one row for its look. */
	@Override
	public List<Setting<?>> shownSettings() {
		if (outline() != null) {
			return super.shownSettings();
		}
		List<Setting<?>> rows = new ArrayList<>(super.shownSettings());
		rows.removeAll(List.of(lookMode, panelTheme, background, glowRow, glow, glowSoft, panelDepth, scale));
		rows.add(settingOf("look_row"));
		return rows;
	}

	@Override
	public Setting<?> settingOf(String key) {
		if (key.equals("look_row")) {
			if (lookRow == null) {
				lookRow = look.row(() -> MacScreen.openSubPage(lookView()));
			}
			return lookRow;
		}
		return super.settingOf(key);
	}

	@Override
	public String sectionOf(Setting<?> setting) {
		return setting == lookRow ? PanelLook.ROW_GROUP : super.sectionOf(setting);
	}

	@Override
	public Feature viewOf(Setting<?> setting) {
		return List.of(lookMode, panelTheme, background, glowRow, panelDepth, scale).contains(setting) ? lookView() : super.viewOf(setting);
	}

	@Override
	public void load(JsonObject json) {
		PanelLook.loading(() -> super.load(json));
		if (!json.has(lookMode.key())) {
			look.markUndecided();
			if (json.has(glow.key()) && !json.has(glowSoft.key())) {
				// from before the soft glow: it had the strong one
				glowSoft.setQuietly(false);
			}
		}
	}

	@Override
	protected void afterReset() {
		PanelLook.settleAll();
	}

	@Override
	public int changedSettings() {
		return super.changedSettings() - look.changedByTheme();
	}

	private static Option segment(InfoBarSegment segment, Setting<?>... own) {
		return new Option(segment.id, segment.title, segment.icon, false).with(List.of(own));
	}

	/**
	 * Adds an add-on's segment to the bar and to its segment list, after the one it names; while the game starts, before the settings are
	 * read, so its place and state are loaded like the others'.
	 */
	public void addSegment(InfoSegment segment) {
		InfoBarSegment own = InfoBarSegment.register(segment.id(), segment.title(), segment.label(), segment.icon(), segment.widest(),
			segment.digits(), segment.iconColor());
		if (own == null) {
			Logs.warn("The info bar segment {} is not added: its id is taken, or the bar has no room for more", segment.id());
			return;
		}
		List<Setting<?>> settings = new ArrayList<>();
		for (AddonSetting<?> setting : segment.settings()) {
			settings.add((Setting<?>) setting);
		}
		segments.addOption(new Option(own.id, own.title, own.icon, false).with(settings), segment.after());
	}

	// ------------------------------------------------------------------------------------------------------ settings for the parts

	Style style() {
		return style.get();
	}

	List<String> enabledSegments() {
		return segments.enabledInOrder();
	}

	/** @return all segments in the list's order, on or off */
	List<String> segmentRows() {
		return segments.rows();
	}

	int segmentsVersion() {
		return segments.version();
	}

	boolean showsSegment(InfoBarSegment segment) {
		return isEnabled() && segments.isOn(segment.id);
	}

	boolean hours24() {
		return hours24.get();
	}

	boolean gameClock() {
		return clock.get() == Clock.GAME;
	}

	boolean fpsLine() {
		return fpsLine.get();
	}

	// the thresholds keep their order: a warning color starts before the stronger one (yellow before red, also while typing a value)
	private double fpsRedValue() {
		return fpsRed.get();
	}

	private double fpsYellowValue() {
		return fpsYellow.get();
	}

	private double pingRedValue() {
		return pingRed.get();
	}

	private double pingYellowValue() {
		return pingYellow.get();
	}

	private double tpsOrangeValue() {
		return tpsOrange.get();
	}

	private double tpsYellowValue() {
		return tpsYellow.get();
	}

	float fpsYellow() {
		return fpsYellow.get();
	}

	float fpsRed() {
		return fpsRed.get();
	}

	boolean pingBars() {
		return pingBars.get() || !pingNumber.get();
	}

	boolean pingNumber() {
		return pingNumber.get();
	}

	float pingYellow() {
		return pingYellow.get();
	}

	float pingRed() {
		return pingRed.get();
	}

	float tpsYellow() {
		return tpsYellow.get();
	}

	float tpsOrange() {
		return tpsOrange.get();
	}

	boolean songOnlyWithoutLyrics() {
		return songWhen.get() == SongWhen.NO_LYRICS;
	}

	boolean coordsWithY() {
		return coordsY.get();
	}

	boolean facing() {
		return facing.get();
	}

	boolean background() {
		return background.get();
	}

	boolean glow() {
		return glow.get();
	}

	float scale() {
		return scale.get();
	}

	boolean previewing() {
		return previewing;
	}

	// ------------------------------------------------------------------------------------------------------ drawing

	/** A mouse button was pressed in game: counted for the clicks per second. */
	public static void click() {
		InfoBarValues.click();
	}

	/** Called when the server sends the world time, for the server speed. */
	public static void onServerTime(long gameTime) {
		InfoBarValues.onServerTime(gameTime);
	}

	/** An answer to the bar's ping request arrived, on the network thread. */
	public static void onPong(long timestamp) {
		InfoBarValues.onPong(timestamp);
	}

	/** Draws the bar. Called every frame, above the game and below screens. */
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || client.level == null && !previewing) {
			return;
		}
		// own stratum, like every panel: the game only checks for overlaps within a stratum
		graphics.nextStratum();
		// its own theme while it is drawn
		HudStyle.setPanelTheme(panelTheme.get().theme());
		HudStyle.setPanelSurface(glow.get(), 0f);
		HudStyle.setPanelDepth(panelDepth.get().depth());
		HudStyle.setPanelGlow(glowSoft.get() ? PanelLook.SOFT_GLOW : 1f);
		try {
			values.update(client, previewing && client.level == null, true);
			// over a menu of the game the bar makes way for its title, up off the screen
			float away = awayFromTitle.follow(ScreenTitles.titleShown() ? 1f : 0f);
			float rise = away * (top.get() + hudBounds()[3] + AWAY_MARGIN);
			renderer.render(graphics, graphics.guiWidth() / 2f + xOffset.get(), top.get() - rise, !previewing);
		} finally {
			HudStyle.setPanelTheme(null);
			HudStyle.setPanelSurface(null, 0f);
			HudStyle.setPanelDepth(null);
			HudStyle.setPanelGlow(1f);
		}
	}

	/** Draws the bar centered in the preview above the segment list, at its size in game. */
	private void drawSettingsPreview(Ui ui, int x, int y, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		HudStyle.setPanelTheme(panelTheme.get().theme());
		HudStyle.setPanelSurface(glow.get(), 0f);
		HudStyle.setPanelDepth(panelDepth.get().depth());
		previewing = true;
		try {
			// the preview never measures the ping: it shows the last value the bar in the game measured
			values.update(client, client.level == null, false);
			renderer.renderFitted(ui.graphics, x, y, width, height);
		} finally {
			previewing = false;
			HudStyle.setPanelTheme(null);
			HudStyle.setPanelSurface(null, 0f);
			HudStyle.setPanelDepth(null);
		}
	}

	// ------------------------------------------------------------------------------------------------------ HUD editor

	@Override
	public String hudName() {
		return "Info bar";
	}

	@Override
	public boolean hudShown() {
		return isEnabled() && !segments.enabledInOrder().isEmpty();
	}

	/** @return the song segment's width with the lyrics in it, in GUI pixels at the bar's own size */
	public float songWidth() {
		return songWidth.get() * MOCKUP_TO_GUI;
	}

	/** @return the song width presets, for the music page's Where the song shows */
	public EnumSetting<SongSize> songSizeSetting() {
		return songSize;
	}

	/**
	 * Sets the song width from a width in GUI pixels, dragged in the HUD editor: one of the presets, or free.
	 *
	 * @return the width now, in pixels of the mockups
	 */
	int dragSongWidth(float guiWidth, boolean free) {
		float wanted = Mth.clamp(guiWidth / MOCKUP_TO_GUI, SONG_WIDTH_LEAST, SONG_WIDTH_MOST);
		songWidth.set(free ? (float) Math.round(wanted) : SongSize.nearest(wanted).width);
		return Math.round(songWidth.get());
	}

	@Override
	public float hudEdgeX() {
		return renderer.songEdgeX();
	}

	@Override
	public String hudDragEdge(float x, boolean free) {
		int width = dragSongWidth(renderer.songWidthTo(x), free);
		SongSize preset = SongSize.nearest(width);
		return preset.width == width ? preset.displayName().getString() : String.valueOf(width);
	}

	@Override
	public int[] hudBounds() {
		return renderer.bounds();
	}

	@Override
	public float hudRadius() {
		return renderer.radius();
	}

	@Override
	public Setting<?> hudEditSetting() {
		return segments;
	}

	@Override
	public String hudEditLabel() {
		return "Edit segments";
	}

	@Override
	public void hudMove(int dx, int dy) {
		xOffset.set(Mth.clamp(xOffset.get() + dx, xOffset.min(), xOffset.max()));
		top.set(Mth.clamp(top.get() + dy, top.min(), top.max()));
	}

	@Override
	public float hudScale() {
		return scale.get();
	}

	@Override
	public void hudSetScale(float value) {
		scale.set(Mth.clamp(value, scale.min(), scale.max()));
	}

	@Override
	public void hudReset() {
		xOffset.reset();
		top.reset();
		scale.reset();
	}
}
