package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.FeatureSwitchSetting;
import dev.tim1337.lumio.ui.PagePictures;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.ui.ScreenTitles;
import dev.tim1337.lumio.util.SeasonDates;
import dev.tim1337.lumio.util.TimeOfDay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Out-of-world screens in the mod's menu style: the startup animation, the main menu, and the server and world lists. Each part can be disabled
 * individually, and the whole feature is off until enabled.
 */
public class MenusFeature extends Feature {
	/** Which main menu the game opens with: the mod's, over the living world, or the game's own with a small settings button. */
	public enum MainMenu {
		LIVING_WORLD("Living world"),
		CLASSIC("Classic");

		private final Component displayName;

		MainMenu(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private static final String MAIN_MENU_KEY = "main_menu";
	/** The key of the main menu lyrics. */
	public static final String MENU_LYRICS_KEY = "menu_lyrics";

	/** The animated menu background. */
	public enum BackgroundStyle {
		STARS("Falling stars: trails that follow the mouse"),
		MINECRAFT("Minecraft: a blocky world, made by shaders of the mod");

		private final Component displayName;

		BackgroundStyle(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The mod's own view, or the game's. */
	public enum Style {
		ENHANCED("Enhanced"),
		VANILLA("Vanilla");

		private final Component displayName;

		Style(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** Where the playing song is shown on the main menu. */
	public enum SongPlace {
		BOTTOM_RIGHT("Bottom right"),
		BOTTOM_CENTER("Bottom, in the middle"),
		TOP_CENTER("Top, in the middle");

		private final Component displayName;

		SongPlace(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The block world's season: grass and leaf colors, snow, and plants. */
	public enum Season {
		WINTER("Winter"),
		SPRING("Spring"),
		SUMMER("Summer"),
		AUTUMN("Autumn");

		private final Component displayName;

		Season(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The season the main menu's world shows: the one of the computer's date, or one picked in the main menu, which stays. */
	public enum SeasonChoice {
		DATE("By date"),
		WINTER("Winter"),
		SPRING("Spring"),
		SUMMER("Summer"),
		AUTUMN("Autumn");

		private final Component displayName;

		SeasonChoice(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** Block world rendering cost: the shader's render resolution, ray distance, and whether there are shadows. */
	public enum WorldQuality {
		LOW("Low: a third of the size, no shadows", 3, 150, false),
		BALANCED("Balanced: half the size", 2, 190, true),
		HIGH("High: the full size", 1, 240, true);

		private final Component displayName;
		private final int divisor;
		private final int steps;
		private final boolean shadows;

		WorldQuality(String displayName, int divisor, int steps, boolean shadows) {
			this.displayName = Component.literal(displayName);
			this.divisor = divisor;
			this.steps = steps;
			this.shadows = shadows;
		}

		public Component displayName() {
			return displayName;
		}

		public int divisor() {
			return divisor;
		}

		public int steps() {
			return steps;
		}

		public boolean shadows() {
			return shadows;
		}
	}

	private final BooleanSetting startup = add(new BooleanSetting("startup_animation", Component.literal("Startup animation"), true), "Startup",
		"From the Mojang screen into the main menu");
	/** New keys: the old "loading_screens" switch was about a plain ring view, and these choose between the mod's views and the game's. */
	private final EnumSetting<Style> loadingStyle = add(new EnumSetting<>("loading_style", Component.literal("Loading screens"), Style.ENHANCED,
		Style::displayName, EnumSetting.Style.SEGMENTED), "Startup");
	private final EnumSetting<Style> listStyle = add(new EnumSetting<>("list_style", Component.literal("World and server lists"), Style.ENHANCED,
		Style::displayName, EnumSetting.Style.SEGMENTED), "Main menu");

	private final BooleanSetting shaderBackground = add(new BooleanSetting("shader_background", Component.literal("Animated background"), true), "Background", "Replaces the panorama");
	private final EnumSetting<BackgroundStyle> backgroundStyle = add(new EnumSetting<>("background_style", Component.literal("Style"), BackgroundStyle.MINECRAFT, BackgroundStyle::displayName, EnumSetting.Style.DROPDOWN), "Background");
	private final ColorSetting starsColor = add(new ColorSetting("stars_color", Component.literal("Star color"), SystemColors.PURPLE_RGB).followsMenu(),
		"Background");
	private final BooleanSetting starsPulse = add(new BooleanSetting("stars_pulse", Component.literal("Pulsing lights"), true), "Background");
	private final FloatSetting starsSize = add(new FloatSetting("stars_size", Component.literal("Star size"), 1f, 0.4f, 2.5f, 1), "Background");
	private final FloatSetting starsCount = add(new FloatSetting("stars_count", Component.literal("Star count"), 90f, 5f, 220f, 0), "Background");
	private final BooleanSetting worldLights = add(new BooleanSetting("world_lights", Component.literal("Particles"), true), "Menu world");
	private final BooleanSetting worldStars = add(new BooleanSetting("world_stars", Component.literal("Night stars"), true), "Menu world");
	private final EnumSetting<SeasonChoice> worldSeason = add(new EnumSetting<>("world_season", Component.literal("Season"), SeasonChoice.DATE, SeasonChoice::displayName, EnumSetting.Style.DROPDOWN), "Menu world");
	/** The hour the world shows while it does not follow the clock, set with the main menu's time slider; three decimals hold a minute. */
	private final FloatSetting worldHour = add(new FloatSetting("world_hour", Component.literal("Time of day"), 16f, 0f, 24f, 3), "Menu world");
	/** Whether the time of day follows the computer's clock; moving the main menu's time slider turns it off. Only about the time, never the season. */
	private final BooleanSetting followTime = add(new BooleanSetting("follow_time", Component.literal("Follow the time"), true), "Menu world");
	private final EnumSetting<WorldQuality> worldQuality = add(new EnumSetting<>("world_quality", Component.literal("Quality"), WorldQuality.BALANCED, WorldQuality::displayName, EnumSetting.Style.DROPDOWN), "Menu world");
	private final FloatSetting backgroundSpeed = add(new FloatSetting("background_speed", Component.literal("Animation speed"), 1f, 0.2f, 3f, 1), "Background");
	private final EnumSetting<MainMenu> title = add(new EnumSetting<>(MAIN_MENU_KEY, Component.literal("Main menu"), MainMenu.LIVING_WORLD, MainMenu::displayName,
		EnumSetting.Style.SEGMENTED), "Main menu");

	private final BooleanSetting songCard = add(new BooleanSetting("song_card", Component.literal("Song card"), true), "Main menu");
	private final BooleanSetting songCover = add(new BooleanSetting("song_cover", Component.literal("Song cover"), true), "Main menu");
	/** The song's lyrics in the main menu's song card. */
	private final BooleanSetting menuLyrics = add(new BooleanSetting(MENU_LYRICS_KEY, Component.literal("Lyrics in the main menu"), false),
		"Main menu");
	private final EnumSetting<SongPlace> songPlace = add(new EnumSetting<>("song_place", Component.literal("Song card position"), SongPlace.BOTTOM_RIGHT, SongPlace::displayName, EnumSetting.Style.DROPDOWN), "Main menu");
	private final FloatSetting songWidth = add(new FloatSetting("song_width", Component.literal("Song card width"), 260f, 180f, 420f, 0), "Main menu");

	private final BooleanSetting lists = add(new BooleanSetting("cards", Component.literal("Server and world cards"), true), "Buttons and lists");
	private final BooleanSetting buttons = add(new BooleanSetting("buttons", Component.literal("Styled buttons"), true), "Buttons and lists");

	/** The last joined server, kept so the main menu can rejoin it. Not shown in the settings. */
	private final StringSetting lastServerName = add(new StringSetting("last_server_name", Component.literal("Last server"), "", false), "Main menu");
	private final StringSetting lastServerIp = add(new StringSetting("last_server_ip", Component.literal("Last server address"), "", false), "Main menu");

	/** Remembers a joined server. */
	public void rememberServer(String name, String ip) {
		if (ip == null || ip.isBlank() || ip.equals(lastServerIp.get())) {
			return;
		}
		lastServerName.set(name == null || name.isBlank() ? ip : name);
		lastServerIp.set(ip);
		FeatureManager.saveSoon();
	}

	/** @return name and address of the last joined server, or null if there is none */
	public String[] lastServer() {
		return lastServerIp.get().isBlank() ? null : new String[]{lastServerName.get(), lastServerIp.get()};
	}

	/** How long the sky takes to glide to the real time when "Follow the time" is turned back on. */
	private static final float HOUR_GLIDE_SECONDS = 0.6f;
	private float glideFrom;
	private long glideStart;
	private static final long SEASON_CHECK_NANOS = 60_000_000_000L;
	private Season realSeason;
	private long realSeasonAt;

	/** The main menu, following the page's settings, in the preview beside them. */
	@Override
	public PagePreview preview() {
		return outline() == null ? null : menuPreview;
	}

	private final PagePreview menuPreview = new PagePreview() {
		@Override
		public void draw(Ui ui, int x, int y, int width, int height) {
			PagePictures.mainMenu(ui, new PagePictures.MenuLook(songCard.get(), menuLyrics.get(), buttons.get(), shaderBackground.get()), x, y, width, height);
		}

		@Override
		public boolean tall() {
			return true;
		}

		@Override
		public String note() {
			return followTime.get() ? "Follows the time" : "A sample of the main menu";
		}
	};

	public MenusFeature() {
		super("menus", "Main menu", "The title screen, loading screens and your server packs.", false);
		add(new FeatureSwitchSetting("keep_server_packs_row", Component.literal("Keep server packs"), () -> ModFeatures.KEEP_SERVER_PACKS), "Servers", "Loads them again on the next start");
		iconRgb(SystemColors.PINK_RGB);
		iconName("window");
		summary("The main menu, server and world lists in the mod's style");
		starsColor.under(backgroundStyle, () -> backgroundStyle.get() == BackgroundStyle.STARS);
		for (var starSetting : new Setting<?>[]{starsPulse, starsSize, starsCount}) {
			starSetting.visibleWhen(() -> backgroundStyle.get() == BackgroundStyle.STARS);
		}
		worldSeason.visibleWhen(() -> backgroundStyle.get() == BackgroundStyle.MINECRAFT);
		worldLights.visibleWhen(() -> backgroundStyle.get() == BackgroundStyle.MINECRAFT);
		worldStars.visibleWhen(() -> backgroundStyle.get() == BackgroundStyle.MINECRAFT);
		// the time of day is set in the main menu's season and time menu; the old panel of time controls is gone
		worldHour.visibleWhen(() -> false);
		followTime.visibleWhen(() -> backgroundStyle.get() == BackgroundStyle.MINECRAFT);
		worldQuality.visibleWhen(() -> backgroundStyle.get() == BackgroundStyle.MINECRAFT);
		songCover.under(songCard);
		songPlace.visibleWhen(() -> false);
		songWidth.visibleWhen(() -> false);
		lastServerName.visibleWhen(() -> false);
		lastServerIp.visibleWhen(() -> false);
		addResetButton();
	}

	/** @return whether the pulsing soft lights behind the falling stars are shown */
	public boolean starsPulse() {
		return starsPulse.get();
	}

	/** @return the falling star size factor; 1 is the default */
	public float starsSize() {
		return starsSize.get();
	}

	/** @return the number of falling stars */
	public int starsCount() {
		return Math.round(starsCount.get());
	}

	/**
	 * @return the colors of the falling stars and the lights behind them: the glow colors while the color follows the menu, otherwise the chosen color
	 *         plus two shades of it, slightly warmer and cooler, so the night is not one flat color
	 */
	public int[] starsPalette() {
		if (starsColor.usesMenu()) {
			return ModFeatures.MENU.glowPalette();
		}
		int rgb = starsColor.argb() & 0xFFFFFF;
		float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
		int warmer = Color.HSBtoRGB(hsb[0] + 0.06f, hsb[1], hsb[2]) & 0xFFFFFF;
		int cooler = Color.HSBtoRGB(hsb[0] - 0.06f, hsb[1] * 0.85f, Math.min(1f, hsb[2] * 1.1f)) & 0xFFFFFF;
		return new int[]{rgb, warmer, cooler};
	}

	public boolean songCover() {
		return songCover.get();
	}

	/** @return whether the main menu shows the song's lyrics */
	public boolean menuLyrics() {
		return menuLyrics.get();
	}

	public SongPlace songPlace() {
		return songPlace.get();
	}

	public int songWidth() {
		return Math.round(songWidth.get());
	}

	/** @return whether the game goes from the Mojang screen into the main menu with the startup transition, not the game's quick fade */
	public boolean startupActive() {
		return isEnabled() && startup.get() && titleActive() && ModFeatures.MENU.animations();
	}

	/** @return whether the game's loading screens are drawn as the mod's one loading screen */
	public boolean loadingEnhanced() {
		return isEnabled() && loadingStyle.get() == Style.ENHANCED;
	}

	/** @return whether Singleplayer and Multiplayer open the main menu's own lists instead of the game's screens */
	public boolean listsEnhanced() {
		return isEnabled() && listStyle.get() == Style.ENHANCED && titleActive();
	}

	/** @return whether the mod's main menu replaces the vanilla one */
	public boolean titleActive() {
		return isEnabled() && title.get() == MainMenu.LIVING_WORLD && !Minecraft.getInstance().isDemo();
	}

	/** @return the main menu that is set, whether or not the feature is on */
	public MainMenu mainMenu() {
		return title.get();
	}

	/** @return whether the startup animation is set, whether or not it can play */
	public boolean startupOn() {
		return startup.get();
	}

	/** @return whether the mod's loading screen is set */
	public boolean loadingOn() {
		return loadingStyle.get() == Style.ENHANCED;
	}

	/** Sets the main menu, the startup animation and the loading screens together, as the setup picks them. */
	public void setMenus(MainMenu mainMenu, boolean startupAnimation, boolean smoothLoading) {
		title.set(mainMenu);
		startup.set(startupAnimation);
		loadingStyle.set(smoothLoading ? Style.ENHANCED : Style.VANILLA);
		FeatureManager.saveSoon();
	}

	/** @return whether the main menu and the world and server screens get the animated background instead of the panorama */
	public boolean backgroundActive() {
		return isEnabled() && shaderBackground.get() && Minecraft.getInstance().level == null;
	}


	public BackgroundStyle backgroundStyle() {
		return backgroundStyle.get();
	}

	public boolean worldLights() {
		return worldLights.get();
	}

	public boolean worldStars() {
		return worldStars.get();
	}

	/** @return the season the world shows: the real one while following the date, else the one picked */
	public Season worldSeason() {
		SeasonChoice choice = worldSeason.get();
		return choice == SeasonChoice.DATE ? realSeason() : Season.values()[choice.ordinal() - 1];
	}

	/** @return the season of the computer's date, looked up again at most once a minute */
	public Season realSeason() {
		long now = System.nanoTime();
		if (realSeason == null || now - realSeasonAt > SEASON_CHECK_NANOS) {
			realSeason = Season.values()[SeasonDates.seasonOf(LocalDate.now())];
			realSeasonAt = now;
		}
		return realSeason;
	}

	/** Shows a season and keeps it; the real season goes back to following the date. The world changes with the usual cross-fade. */
	public void setWorldSeason(Season value) {
		worldSeason.set(value == realSeason() ? SeasonChoice.DATE : SeasonChoice.values()[value.ordinal() + 1]);
		FeatureManager.saveSoon();
	}

	public boolean followsTime() {
		return followTime.get();
	}

	/** Turns following the clock on or off; turned on, the sky and light glide from the hour shown to the real one. */
	public void setFollowsTime(boolean value) {
		if (value && !followTime.get()) {
			glideFrom = worldHour.get();
			glideStart = System.nanoTime();
		}
		followTime.set(value);
		FeatureManager.saveSoon();
	}

	/** Shows an hour of the day from 0 to 24, which stops following the clock. */
	public void setWorldHour(float hour) {
		worldHour.set(hour);
		followTime.set(false);
		glideStart = 0L;
		FeatureManager.saveSoon();
	}

	/** @return the hour of the day the world shows, from 0 to 24: the clock's while following it, else the one set in the main menu */
	public float worldHour() {
		if (!followTime.get()) {
			return worldHour.get();
		}
		LocalTime now = LocalTime.now();
		float real = now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f;
		if (glideStart != 0L) {
			float progress = (System.nanoTime() - glideStart) / 1_000_000_000f / HOUR_GLIDE_SECONDS;
			if (progress < 1f) {
				return TimeOfDay.glide(glideFrom, real, progress);
			}
			glideStart = 0L;
		}
		return real;
	}

	/** @return whether the hour shown is gliding to the clock's, so the world draws every frame meanwhile */
	public boolean hourGliding() {
		return glideStart != 0L;
	}

	public WorldQuality worldQuality() {
		return worldQuality.get();
	}

	public float backgroundSpeed() {
		return backgroundSpeed.get();
	}

	/** @return whether the server and world lists are drawn as cards */
	public boolean listsActive() {
		return isEnabled() && lists.get();
	}

	/** @return whether buttons on out-of-world screens use the menu style */
	public boolean buttonsActive() {
		if (!isEnabled() || !buttons.get()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		// every menu of the game takes the mod's buttons, in a world too; what is part of playing keeps its own look
		Screen screen = client.gui.screen();
		return client.level == null || screen != null && !ScreenTitles.partOfPlaying(screen);
	}
}
