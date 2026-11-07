package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Easing;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.ColorDotsSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.InfoSetting;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.config.setting.NavListSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.config.setting.TickTilesSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Eased;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.ui.Glyphs;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.render.RoundedImage;
import dev.tim1337.lumio.ui.Ui;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Shows the currently playing song, in the info bar or in its own window: cover, live lyrics, progress, and bars that dance while it plays.
 * The song data comes from {@link MusicService}; the lyrics floating in the world are handled by {@link WorldLyrics}.
 */
public class MusicFeature extends Feature implements EditableHud {
	private static final String BAR_LYRICS_KEY = "bar_lyrics";
	/** The keys of the world lyrics' placement (Auto or Manual) and its manual place. */
	public static final String WORLD_PLACEMENT_KEY = "world_placement";
	public static final String WORLD_PLACE_KEY = "world_place";

	public enum Display {
		INFO_BAR("In the info bar"),
		MINI("Small player"),
		WINDOW("Window");

		private final Component displayName;

		Display(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The size of the small player or the window in three steps; finer in the More settings. */
	public enum PlayerSize {
		SMALL("Small", 0.85f),
		MEDIUM("Medium", 1f),
		LARGE("Large", 1.25f);

		private final Component displayName;
		private final float scale;

		PlayerSize(String displayName, float scale) {
			this.displayName = Component.literal(displayName);
			this.scale = scale;
		}

		public Component displayName() {
			return displayName;
		}

		/** @return the step closest to a scale */
		static PlayerSize nearest(float scale) {
			PlayerSize best = MEDIUM;
			for (PlayerSize size : values()) {
				if (Math.abs(size.scale - scale) < Math.abs(best.scale - scale)) {
					best = size;
				}
			}
			return best;
		}
	}

	/** How the world lyrics find their spot: a good open one picked for every line, or the place chosen below. */
	public enum WorldPlacement {
		AUTO("Auto"),
		MANUAL("Manual");

		private final Component displayName;

		WorldPlacement(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** How long an older world lyric line stays after the next one started, dimming, before it fades out. */
	public enum WorldLinger {
		SHORT("Short", 0.8f),
		NORMAL("Normal", 1.5f),
		LONG("Long", 2.5f);

		private final Component displayName;
		final float seconds;

		WorldLinger(String displayName, float seconds) {
			this.displayName = Component.literal(displayName);
			this.seconds = seconds;
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** Where the world lyrics stand: around the player near eye height, or large in the sky. */
	public enum WorldPlace {
		NEAR("Around you"),
		SKY("In the sky");

		private final Component displayName;

		WorldPlace(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The look of the world lyrics: the soft glow, the classic glow of before, or a box behind the sung word. */
	public enum WorldEffect {
		GLOW("Soft"),
		CLASSIC("Classic");

		private final Component displayName;

		WorldEffect(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The color of the world lyrics' text. */
	public enum TextColor {
		WHITE("White"),
		ACCENT("Accent"),
		COVER("From the cover"),
		CUSTOM("Custom");

		private final Component displayName;

		TextColor(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The custom color of the lyrics until one is picked. */
	private static final int DEFAULT_LYRICS_CUSTOM = 0x9D00FF;

	public enum LyricColor {
		ACCENT("Accent"),
		COVER("From the cover"),
		WHITE("White"),
		CUSTOM("Custom");

		private final Component displayName;

		LyricColor(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	public enum GlowColor {
		ACCENT("Accent"),
		COVER("From the cover"),
		WHITE("White"),
		CUSTOM("Custom");

		private final Component displayName;

		GlowColor(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** Font of the world lyrics: the clean lyrics font, or handwritten (words with individual size and baseline). */
	public enum WorldFont {
		CLEAN("Clean"),
		HANDWRITTEN("Handwritten");

		private final String title;

		WorldFont(String title) {
			this.title = title;
		}

		public Component displayName() {
			return Component.literal(title);
		}
	}

	public enum Player {
		AUTO("Automatic", MusicService.Source.AUTO),
		CIDER("Cider", MusicService.Source.CIDER),
		WINDOWS("Windows media", MusicService.Source.WINDOWS),
		SPOTIFY("Spotify", MusicService.Source.SPOTIFY);

		private final Component displayName;
		private final MusicService.Source source;

		Player(String displayName, MusicService.Source source) {
			this.displayName = Component.literal(displayName);
			this.source = source;
		}

		public Component displayName() {
			return displayName;
		}
	}

	// ------------------------------------------------------------------------------------------------------ source cards

	private static final int CIDER_RGB = 0xFA2D48;
	private static final int SPOTIFY_RGB = 0x1DB954;
	private static final boolean ON_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	// the states a source card can show: constants, since the menu asks for them every frame
	private static final EnumSetting.OptionState PLAYING = new EnumSetting.OptionState("Playing", SystemColors.GREEN_RGB);
	private static final EnumSetting.OptionState WORKING = new EnumSetting.OptionState("Working", SystemColors.GREEN_RGB);
	private static final EnumSetting.OptionState PICKS = new EnumSetting.OptionState("Picks what plays", -1);
	private static final EnumSetting.OptionState NEEDS_TOKEN = new EnumSetting.OptionState("Needs a token", SystemColors.ORANGE_RGB);
	private static final EnumSetting.OptionState READY = new EnumSetting.OptionState("Ready", -1);
	private static final EnumSetting.OptionState SIGNED_IN = new EnumSetting.OptionState("Signed in", SystemColors.GREEN_RGB);
	private static final EnumSetting.OptionState CIDER_DOWN = new EnumSetting.OptionState("Cider isn't running", SystemColors.ORANGE_RGB);
	private static final EnumSetting.OptionState NOT_SIGNED_IN = new EnumSetting.OptionState("Not signed in", -1);
	private static final EnumSetting.OptionState NO_SETUP = new EnumSetting.OptionState("No setup needed", -1);
	private static final EnumSetting.OptionState WINDOWS_ONLY = new EnumSetting.OptionState("Windows only", -1);

	/** The card of each source, built once on first use, when all settings exist. */
	private Map<Player, EnumSetting.OptionCard> sourceCards;

	/** @return the card of a source in the menu: its icon, what it is, and whether it works */
	private EnumSetting.OptionCard sourceCard(Player choice) {
		if (sourceCards == null) {
			sourceCards = new EnumMap<>(Player.class);
			sourceCards.put(Player.AUTO, new EnumSetting.OptionCard("sparkle", SystemColors.INDIGO_RGB,
				"Cider or Spotify when they answer, otherwise the Windows media controls.", () -> MusicService.track() != null ? PLAYING : PICKS));
			sourceCards.put(Player.CIDER, new EnumSetting.OptionCard("note", CIDER_RGB, "Apple Music through Cider. Needs an API token from Cider.",
				() -> MusicService.playsFrom(MusicService.Source.CIDER) ? WORKING : ciderToken.get().isEmpty() ? NEEDS_TOKEN : READY));
			sourceCards.put(Player.WINDOWS, new EnumSetting.OptionCard("window", SystemColors.BLUE_RGB,
				"Any player in the Windows media controls, such as Apple Music or Spotify. No setup needed.",
				() -> !ON_WINDOWS ? WINDOWS_ONLY : MusicService.playsFrom(MusicService.Source.WINDOWS) ? WORKING : NO_SETUP));
			sourceCards.put(Player.SPOTIFY, new EnumSetting.OptionCard("play", SPOTIFY_RGB, "Spotify Web API. A short guide walks you through the sign-in.",
				() -> MusicService.playsFrom(MusicService.Source.SPOTIFY) ? WORKING : SpotifyClient.connected() ? SIGNED_IN : NOT_SIGNED_IN));
		}
		return sourceCards.get(choice);
	}

	// the picture tiles of the music page: a small scene of the game with the choice in it
	private static final Map<Display, EnumSetting.OptionCard> DISPLAY_CARDS = new EnumMap<>(Map.of(
		Display.INFO_BAR, new EnumSetting.OptionCard("gauge", SystemColors.TEAL_RGB, "Song and lyrics in one place", null, MusicPictures::infoBar),
		Display.MINI, new EnumSetting.OptionCard("note", SystemColors.PINK_RGB, "A card in a corner", null, MusicPictures::smallPlayer),
		Display.WINDOW, new EnumSetting.OptionCard("window", SystemColors.BLUE_RGB, "A bigger player card", null, MusicPictures::window)));

	private static EnumSetting.OptionCard displayCard(Display choice) {
		return DISPLAY_CARDS.get(choice);
	}

	/** A linearly filtered texture, since the cover is drawn much smaller than its resolution. */
	private static final class SmoothTexture extends DynamicTexture {
		SmoothTexture(NativeImage image) {
			super(() -> "Lumio cover", image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	/** Two places for the cover textures, used in turn: the new cover fades in over the old one, which is freed afterwards. */
	private static final Identifier[] COVERS = {Lumio.id("music_cover"), Lumio.id("music_cover_b")};
	private static final Identifier[] COVERS_SMALL = {Lumio.id("music_cover_small"), Lumio.id("music_cover_small_b")};
	/** A song whose cover has not come this long after the song change has none; the old cover then gives way. */
	private static final long COVER_WAIT_NANOS = 3_000_000_000L;
	private static final int SEGMENT_HEIGHT = 16;
	private static final int WINDOW_WIDTH = 220;
	private static final int WINDOW_HEIGHT = 58;

	private final EnumSetting<Player> player = add(new EnumSetting<>("player", Component.literal("Player"), Player.AUTO, Player::displayName, EnumSetting.Style.DROPDOWN)
		.withOptionCards(this::sourceCard), "Source");
	private final StringSetting ciderToken = add(new StringSetting("cider_token", Component.literal("Cider token"), "", true), "Source", "In Cider: Settings, then Connectivity");
	private final StringSetting spotifyId = add(new StringSetting("spotify_client_id", Component.literal("Spotify Client ID"), "", false), "Source");
	private final ButtonSetting spotifyConnect = add(new ButtonSetting("spotify_connect", Component.literal("Spotify account"), Component.literal("Connect"), false, () -> {
		MusicService.setSpotifyClientId(spotifyId.get());
		MusicService.connectSpotify(uri -> Util.getPlatform().openUri(uri));
	}), "Source");
	private final ButtonSetting spotifyOff = add(new ButtonSetting("spotify_disconnect", Component.literal("Spotify account"), Component.literal("Disconnect"), true, MusicService::disconnectSpotify), "Source");
	private final InfoSetting spotifyStatus = add(new InfoSetting("spotify_status", Component.literal("Spotify status"), MusicService::spotifyMessage), "Source");
	private final ButtonSetting spotifyGuide = add(new ButtonSetting("spotify_guide", Component.literal("Connecting Spotify, step by step"), Component.literal("Open guide"), false, () -> {
		var client = Minecraft.getInstance();
		client.setScreenAndShow(SourceSetupScreen.spotify(client.gui.screen()));
	}), "Source");

	/** Whether Spotify has been set up, so its Client ID field and connect button appear on the page. */
	private boolean spotifySetUp;

	/** @return the configured Spotify app Client ID */
	public String spotifyClientId() {
		return spotifyId.get();
	}

	/** Sets the Spotify app Client ID, as the setup guide reads it from the clipboard. */
	public void setSpotifyClientId(String id) {
		spotifyId.set(id == null ? "" : id.strip());
		MusicService.setSpotifyClientId(spotifyId.get());
		FeatureManager.saveSoon();
	}

	/** Switches the source to Spotify. */
	public void useSpotify() {
		player.set(Player.SPOTIFY);
		setEnabled(true);
		FeatureManager.saveSoon();
	}

	/** @return the token of Cider's API, for its setup */
	public String ciderToken() {
		return ciderToken.get();
	}

	/** Uses Cider for the music, with the token its setup tested. */
	public void useCider(String token) {
		ciderToken.set(token);
		player.set(Player.CIDER);
		setEnabled(true);
		FeatureManager.saveSoon();
	}
	private final BooleanSetting onlineCovers = add(new BooleanSetting("online_covers", Component.literal("Online covers"), true), "Source", "When the player gives none");
	private final EnumSetting<Display> display = add(new EnumSetting<>("display", Component.literal("Where the song shows"), Display.INFO_BAR, Display::displayName,
		EnumSetting.Style.SEGMENTED).withOptionCards(MusicFeature::displayCard), "Where the song shows");
	private final BooleanSetting showCover = new BooleanSetting("show_cover", Component.literal("Cover"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting showProgress = new BooleanSetting("show_progress", Component.literal("Progress"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting showBars = new BooleanSetting("show_bars", Component.literal("Dancing bars"), true, BooleanSetting.Style.CHECKBOX);
	private final MultiSelectSetting show = add(new MultiSelectSetting("show", Component.literal("Show"), List.of(showCover, showProgress, showBars)), "Appearance");
	private final ColorSetting accent = add(new ColorSetting("accent", Component.literal("Accent color"), 0xFA586A).followsMenu(), "Appearance");
	private final FloatSetting textWidth = add(new FloatSetting("text_width", Component.literal("Text width"), 150f, 70f, 280f, 0), "Appearance");
	private final FloatSetting windowScale = add(new FloatSetting("scale", Component.literal("Window size"), 1f, 0.3f, 3f, 2), "Appearance");
	private final BooleanSetting panelBackground = add(new BooleanSetting("background", Component.literal("Background"), true), "Appearance");
	private final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Glow"), false), "Appearance");
	/** Window corner radius when it was last drawn, in its own pixels. */
	private float lastRadius = 16f;
	/** How lyric timing is adjusted against the song position. */
	public enum TimingMode {
		MANUAL("Manual"),
		SMART("Smart");

		private final Component displayName;

		TimingMode(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	// lyrics settings span two tabs: content and timing, then the world lyrics appearance
	final BooleanSetting lyrics = add(new BooleanSetting("lyrics", Component.literal("Live lyrics"), true), "Lyrics", "From lrclib.net, or from Apple Music through Cider");
	/** The highlight box behind the sung word: in the info bar and the players. */
	final BooleanSetting highlightBox = add(new BooleanSetting("highlight_box", Component.literal("Highlight box"), true) {
		@Override
		public Control createControl(Runnable onChanged) {
			return new BoxSwitchControl(this, onChanged, MusicFeature.this::lyricsArgb);
		}
	}, "Lyrics", "A colored box moves along behind the sung word");
	/** The color of the lyrics in the info bar and the players. */
	final ColorDotsSetting<LyricColor> lyricsColor = add(new ColorDotsSetting<>("lyrics_color", Component.literal("Color"), LyricColor.ACCENT,
		LyricColor::displayName, this::lyricColorOf, LyricColor.CUSTOM), "Lyrics");
	private final ColorSetting lyricsCustom = add(new ColorSetting("lyrics_color_custom", Component.literal("Custom color"), DEFAULT_LYRICS_CUSTOM), "Lyrics");
	private final BooleanSetting adlibs = add(new BooleanSetting("adlibs", Component.literal("Adlibs"), true), "Lyrics", "Background vocals, smaller below the line");
	private final BooleanSetting bracketAdlibs = add(new BooleanSetting("adlib_brackets", Component.literal("Detect adlibs in brackets"), true), "Lyrics",
		"Such as (oh, yeah), in lyrics that do not mark them");
	private final BooleanSetting safeLyrics = add(new BooleanSetting("safe_lyrics", Component.literal("Censor slurs"), false), "Lyrics", "Shown as ***");
	private final EnumSetting<TimingMode> timingMode = add(new EnumSetting<>("lyric_timing_mode", Component.literal("Timing"), TimingMode.SMART, TimingMode::displayName, EnumSetting.Style.SEGMENTED), "Lyrics",
		"Smart finds the delay itself");
	private final FloatSetting lyricTiming = add(new FloatSetting("lyric_timing", Component.literal("Timing offset"), 0f, -3f, 3f, 1), "Lyrics", "In seconds. Below 0 is earlier");

	// the lyrics in the info bar and the players
	/** The lyrics in the info bar's song segment (the sung line replaces the song there) and in the small player and the window. */
	private final BooleanSetting barLyrics = add(new BooleanSetting(BAR_LYRICS_KEY, Component.literal("Lyrics in the info bar & players"), true), "Lyrics");

	private final BooleanSetting worldLyrics = add(new BooleanSetting("world_lyrics", Component.literal("Lyrics in the world"), false), "Lyrics in the world");
	final EnumSetting<WorldFont> worldFont = add(new EnumSetting<>("world_font", Component.literal("Font"), WorldFont.CLEAN, WorldFont::displayName, EnumSetting.Style.SEGMENTED), "Lyrics in the world");
	final FloatSetting worldSize = add(new FloatSetting("world_size", Component.literal("Size"), 1f, 0.6f, 2f, 2), "Lyrics in the world");
	final EnumSetting<WorldPlacement> worldPlacement = add(new EnumSetting<>(WORLD_PLACEMENT_KEY, Component.literal("Placement"), WorldPlacement.AUTO,
		WorldPlacement::displayName, EnumSetting.Style.SEGMENTED), "Lyrics in the world", "Picks a good open spot for every line, in the sky or around you");
	final EnumSetting<WorldPlace> worldPlace = add(new EnumSetting<>(WORLD_PLACE_KEY, Component.literal("Place"), WorldPlace.NEAR, WorldPlace::displayName,
		EnumSetting.Style.SEGMENTED), "Lyrics in the world");
	final EnumSetting<WorldEffect> worldEffect = add(new EnumSetting<>("world_effect", Component.literal("Glow"), WorldEffect.GLOW, WorldEffect::displayName,
		EnumSetting.Style.SEGMENTED), "Lyrics in the world");
	final EnumSetting<TextColor> worldTextColor = add(new EnumSetting<>("world_text_color", Component.literal("Text color"), TextColor.WHITE,
		TextColor::displayName, EnumSetting.Style.DROPDOWN), "Lyrics in the world");
	private final ColorSetting worldTextCustom = add(new ColorSetting("world_text_custom", Component.literal("Custom text color"), 0xFFF4E6), "Lyrics in the world");
	final EnumSetting<GlowColor> worldGlowColor = add(new ColorDotsSetting<>("world_glow_color", Component.literal("Color"), GlowColor.ACCENT,
		GlowColor::displayName, this::glowColorOf, GlowColor.CUSTOM), "Lyrics in the world");
	private final ColorSetting worldGlowCustom = add(new ColorSetting("world_glow_custom", Component.literal("Custom glow color"), 0xFFB347), "Lyrics in the world");
	final FloatSetting worldReach = add(new FloatSetting("world_reach", Component.literal("Spread"), 0.45f, 0f, 1f, 2), "Lyrics in the world",
		"How far from the crosshair the lines stand");
	final FloatSetting worldDistance = add(new FloatSetting("world_distance", Component.literal("Distance"), 1f, 0.6f, 1.6f, 2), "Lyrics in the world");
	final EnumSetting<WorldLinger> worldLinger = add(new EnumSetting<>("world_linger", Component.literal("Older lines stay"), WorldLinger.NORMAL,
		WorldLinger::displayName, EnumSetting.Style.SEGMENTED), "Lyrics in the world");

	// ------------------------------------------------------------------------------------------------- the music page
	// The page is a short overview: the song that plays and one row per part, each opening a sub-page in the same window (Where the song shows,
	// Lyrics, Source, Keys, More). The settings shown there are views of the settings above, saved there (see pageSettings).

	/** Groups of a sub-page without a title of their own: their titles are spaces, one more each, so they stay apart. */
	private static final String UNTITLED = " ";
	private static final String UNTITLED_2 = "  ";
	private static final String GROUP_SCREEN = "Info bar and players";
	private static final String GROUP_WORLD = "In the world";
	private final DerivedEnumSetting<PlayerSize> playerSize = add(new DerivedEnumSetting<>("player_size", Component.literal("Size"), PlayerSize.MEDIUM,
		PlayerSize::displayName, EnumSetting.Style.SEGMENTED, () -> PlayerSize.nearest(windowScale.get()), size -> windowScale.set(size.scale)), UNTITLED);
	private final TickTilesSetting lyricPlaces = add(new TickTilesSetting("lyric_places", Component.literal("Where the lyrics show"), null, List.of(
		new TickTilesSetting.Tile(barLyrics, "In the info bar & players", "", MusicPictures::barLyrics, null, null),
		new TickTilesSetting.Tile(worldLyrics, "In the world", "", MusicPictures::inWorld, null, null)))
		.compact("Show lyrics in one or more places. Options below only appear for the places you picked."), UNTITLED);
	private final NowPlayingSetting nowPlaying = add(new NowPlayingSetting(this), UNTITLED);
	private final NavListSetting overview = add(new NavListSetting("music_overview", Component.literal("Music"), List.of(
		new NavListSetting.Item("note", SystemColors.PINK_RGB, "Where the song shows", this::displayValue, null, () -> MacScreen.openSubPage(whereView())),
		new NavListSetting.Item("lyrics", SystemColors.PURPLE_RGB, "Lyrics", this::lyricsValue, null, () -> MacScreen.openSubPage(lyricsView())),
		new NavListSetting.Item("link", SystemColors.BLUE_RGB, "Source", this::sourceValue, this::sourceDot, () -> MacScreen.openSubPage(sourceView())),
		new NavListSetting.Item("keyboard", SystemColors.GRAY_RGB, "Keys", this::keysValue, null, () -> MacScreen.openSubPage(keysView())),
		new NavListSetting.Item("sliders", SystemColors.GRAY_RGB, "More", () -> MORE_SUMMARY, null, () -> MacScreen.openSubPage(moreView()))), null), UNTITLED);
	/** While Cider does not answer: says so, with Retry, on the page of the source. */
	private final ActionRowSetting ciderRetry = add(new ActionRowSetting("cider_retry", Component.literal("Cider"), new ActionRowSetting.Look(() -> "note",
		() -> CIDER_RGB, () -> "Cider", null, () -> CIDER_DOWN, null, () -> "Retry"), MusicService::retryCider), UNTITLED);
	private static final String MORE_SUMMARY = "Size, cover lookup, level bars, timing";
	private SettingsView whereView;
	private SettingsView lyricsView;
	private SettingsView sourceView;
	private SettingsView keysView;
	private SettingsView moreView;
	/** Window position, set in the HUD editor. */
	private final FloatSetting windowX = add(new FloatSetting("window_x", Component.literal("Window x"), 12f, -4000f, 8000f, 0), "Appearance");
	private final FloatSetting windowY = add(new FloatSetting("window_y", Component.literal("Window y"), 40f, -4000f, 8000f, 0), "Appearance");
	/** The shared motion of the lyric lines. */
	final LyricMotion motion = new LyricMotion(this);
	private final BarLyrics barView = new BarLyrics(this);
	private final WorldLyrics inWorld = new WorldLyrics(this, motion);

	// animation state
	/** Sides of the two cover textures of the current song, in texture pixels. */
	private final int[] coverSide = new int[2];
	private final int[] smallSide = new int[2];
	private final boolean[] coverFilled = new boolean[2];
	/** The place of the newest cover, the song it belongs to, and when the song last changed. */
	private int coverSlot;
	private int coverTrack = -1;
	private long songChangedAt;
	private float coverAmount;
	private boolean hasCover;
	private int lastTrack = -1;
	private float trackFlash;
	private float shown;
	private float segmentWidth;
	private float danceLevel;
	/** @return how loud the music is now, from 0 to 1, as the indicator shows it; 0 while none plays */
	public float loudness() {
		return isEnabled() && MusicService.isPlaying() ? Math.clamp(danceLevel, 0f, 1f) : 0f;
	}

	/** Dance animation clock and loudness history, so every indicator bar moves with its own phase. */
	private float danceClock;
	String text = "";
	private String previousText = "";
	/** Horizontal scroll offset of the line when last drawn, and of the previous line during a line change. */
	private float drawnShift;
	private float previousShift;
	private float swap = 1f;
	private float progressShown;
	private long lastNanos = System.nanoTime();
	private int[] bounds = {12, 40, WINDOW_WIDTH, WINDOW_HEIGHT};

	public MusicFeature() {
		super("music", "Now playing", "The song that is playing, with the cover and live lyrics, from Apple Music or Cider.", false);
		iconRgb(SystemColors.PINK_RGB);
		iconName("note");
		summary("The song that plays, with its cover and live lyrics");
		ciderToken.under(player, () -> player.get() == Player.AUTO || player.get() == Player.CIDER);
		// Spotify is set up through the guide, which covers every step. Before that the page only offers the guide; once a Client ID exists (from
		// the guide or typed in) it can be edited here, with the connect/disconnect button and the status below it.
		BooleanSupplier spotify = () -> player.get() == Player.AUTO || player.get() == Player.SPOTIFY;
		// stays visible once shown, so the field does not disappear while its text is cleared to type another
		BooleanSupplier setUp = () -> {
			if (!spotifyId.get().isBlank() || MusicService.spotifyConnected()) {
				spotifySetUp = true;
			}
			return spotifySetUp;
		};
		spotifyId.visibleWhen(() -> spotify.getAsBoolean() && setUp.getAsBoolean());
		spotifyStatus.visibleWhen(() -> spotify.getAsBoolean() && setUp.getAsBoolean());
		spotifyGuide.visibleWhen(() -> spotify.getAsBoolean() && !MusicService.spotifyConnected());
		spotifyConnect.visibleWhen(() -> spotify.getAsBoolean() && setUp.getAsBoolean() && !MusicService.spotifyConnected());
		spotifyOff.visibleWhen(() -> spotify.getAsBoolean() && MusicService.spotifyConnected());
		windowX.visibleWhen(() -> false);
		windowY.visibleWhen(() -> false);
		textWidth.under(display, () -> display.get() != Display.WINDOW);
		windowScale.under(display, () -> display.get() != Display.INFO_BAR);
		lyricTiming.visibleWhen(() -> lyrics.get() && timingMode.get() == TimingMode.MANUAL);
		timingMode.under(lyrics);
		worldLyrics.visibleWhen(lyrics::get);
		adlibs.under(lyrics);
		bracketAdlibs.under(adlibs);
		worldSize.visibleWhen(() -> lyrics.get() && worldLyrics.get());
		worldFont.visibleWhen(() -> lyrics.get() && worldLyrics.get());
		// on the lyrics page a group shows for each place that is ticked; the info bar and the players share one
		BooleanSupplier world = () -> lyrics.get() && worldLyrics.get();
		BooleanSupplier screen = () -> lyrics.get() && barLyrics.get();
		for (Setting<?> look : List.of(worldPlacement, worldEffect, worldTextColor, worldGlowColor, worldLinger)) {
			look.visibleWhen(world);
		}
		// the manual place shows below Placement only while it is Manual
		for (Setting<?> manual : List.of(worldPlace, worldReach, worldDistance)) {
			manual.visibleWhen(() -> world.getAsBoolean() && worldPlacement.get() == WorldPlacement.MANUAL);
		}
		for (Setting<?> look : List.of(highlightBox, lyricsColor)) {
			look.visibleWhen(screen);
		}
		lyricsCustom.visibleWhen(() -> screen.getAsBoolean() && lyricsColor.get() == LyricColor.CUSTOM);
		worldTextCustom.visibleWhen(() -> world.getAsBoolean() && worldTextColor.get() == TextColor.CUSTOM);
		worldGlowCustom.visibleWhen(() -> world.getAsBoolean() && worldGlowColor.get() == GlowColor.CUSTOM);
		playerSize.visibleWhen(() -> display.get() != Display.INFO_BAR);
		lyricPlaces.visibleWhen(lyrics::get);
		ciderRetry.visibleWhen(this::ciderDown);
		addResetButton();
	}

	// ------------------------------------------------------------------------------------------------------- state

	/** @return whether lyrics are shown: they are enabled and available for this song */
	public boolean lyricsOn() {
		return lyrics.get() && showingLyric && !pausedLong();
	}

	/** @return the player the music is taken from, for the setup */
	public Player player() {
		return player.get();
	}

	public void setPlayer(Player value) {
		player.set(value);
	}

	/** @return whether a song plays from the player right now */
	public static boolean playsFrom(Player wanted) {
		return MusicService.playsFrom(wanted.source);
	}

	/** @return whether Cider can only be reached with a token from its settings, which is not set yet */
	public boolean ciderNeedsToken() {
		return ciderToken.get().isEmpty();
	}

	/** @return whether the game runs on Windows, where any player is read */
	public static boolean onWindows() {
		return ON_WINDOWS;
	}

	/** The currently sung line and how many of its characters are sung. */
	public record SungLine(String text, float sungChars) {
	}

	/** @return whether world lyrics are shown: when enabled, and always in cinematic mode, which shows them in its shots */
	boolean worldLyricsShown() {
		// while the cinematic camera runs, its own setting decides: the lyrics stand in its shots
		return ModFeatures.CINEMATIC.active() ? ModFeatures.CINEMATIC.showsWorldLyrics() : worldLyrics.get();
	}

	public int accentColor() {
		return accentArgb();
	}

	/** @return the song position used for lyrics, in milliseconds: the playback position adjusted by the timing setting */
	public long lyricClockMs() {
		return timedPosition();
	}

	/**
	 * Song position used for lyrics. In manual mode it is the playback position plus the configured offset. In smart mode lines appear slightly
	 * before they are sung, compensating for the player's position reporting and rendering delay, and long lines appear earlier than short ones
	 * since they take longer to read.
	 */
	private long timedPosition() {
		long position = controls.scrubbing() ? controls.scrubMs() : MusicService.position();
		if (timingMode.get() == TimingMode.MANUAL) {
			return position + Math.round(lyricTiming.get() * 1000f);
		}
		MusicService.LyricLine next = MusicService.lyricLineAt(position + 300L);
		float characters = next == null ? 20f : next.text().length();
		long lead = 260L + Math.round(Mth.clamp(characters / 60f, 0.1f, 1f) * 380f);
		return position + lead;
	}

	private float tickClock() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		return dt;
	}

	/** Starts song detection as soon as the game runs, so the song is known by the first world frame. */
	public void warmUp() {
		if (isEnabled()) {
			MusicService.setSafe(safeLyrics.get());
			MusicService.setBracketAdlibs(bracketAdlibs.get());
			MusicService.setSpotifyClientId(spotifyId.get());
			MusicService.run(player.get().source, lyrics.get(), onlineCovers.get(), ciderToken.get());
		} else if (MusicService.active()) {
			// Disabled: stop all background work, no player polling and no Windows media reader. This used to be stopped only by the renderer,
			// and with the feature off nothing renders it, so it kept running.
			MusicService.stop();
		}
	}

	/** Frame timestamp of the last {@link #update}, so it runs only once per frame. */
	private long lastUpdateStamp;

	/** When the song was paused, or 0 while playing. */
	private long pausedSince;

	/** @return whether the song has been paused for over a minute, in which case the HUD shows the song instead of the lyric line */
	private boolean pausedLong() {
		return pausedSince != 0L && System.nanoTime() - pausedSince > 60_000_000_000L;
	}

	/** Keeps the music service running according to the settings and advances the animations; called by every view that draws the song. @return the frame delta in seconds, 0 for repeated calls in the same frame */
	float update(Minecraft client) {
		// the info bar, the window and the world all call this every frame; only the first call per frame does the work
		long stamp = System.nanoTime();
		if (stamp - lastUpdateStamp < 1_500_000L) {
			return 0f;
		}
		lastUpdateStamp = stamp;
		float dt = tickClock();
		if (isEnabled()) {
			MusicService.setSpotifyClientId(spotifyId.get());
			MusicService.setSafe(safeLyrics.get());
			MusicService.setBracketAdlibs(bracketAdlibs.get());
			MusicService.run(player.get().source, lyrics.get(), onlineCovers.get(), ciderToken.get());
		} else {
			MusicService.stop();
		}
		MusicService.Track track = MusicService.track();
		float target = track != null && isEnabled() ? 1f : 0f;
		shown += (target - shown) * (1f - (float) Math.exp(-dt * 8f));
		if (Math.abs(target - shown) < 0.004f) {
			shown = target;
		}

		if (track != null && MusicService.trackNumber() != lastTrack) {
			lastTrack = MusicService.trackNumber();
			// new song: the old title and text roll up and out while the new ones come in from below, and the pill animates to its new width,
			// like the player crossfading between songs
			previousTitle = lastTitle;
			previousArtist = lastArtist;
			songRoll = 0f;
			trackFlash = 1f;
			// the old cover stays until the new one is there, then they cross-fade
			songChangedAt = System.nanoTime();
		}
		if (track != null) {
			lastTitle = track.title();
			lastArtist = track.artist().isEmpty() ? track.album() : track.artist();
		}
		songRoll = Math.min(1f, songRoll + dt * 3.2f);
		// take the cover only after the song change was detected, otherwise a cover arriving with it would be discarded
		CoverImages.Prepared cover = MusicService.takeCover();
		if (cover != null) {
			uploadCover(cover);
		}
		trackFlash = Math.max(0f, trackFlash - dt * 1.6f);
		coverAmount = Math.min(1f, coverAmount + dt * 3f);
		if (coverAmount >= 1f && coverFilled[1 - coverSlot]) {
			// the cross-fade is over: the old cover is freed
			var textures = Minecraft.getInstance().getTextureManager();
			textures.release(COVERS[1 - coverSlot]);
			textures.release(COVERS_SMALL[1 - coverSlot]);
			coverFilled[1 - coverSlot] = false;
		}
		if (hasCover && coverTrack != MusicService.trackNumber() && System.nanoTime() - songChangedAt > COVER_WAIT_NANOS && !MusicService.hasPendingOrCover()) {
			hasCover = false;
		}

		// the bars dance while a song plays and shrink to dots when paused; they reflect playback state, not the audio
		boolean playing = MusicService.isPlaying();
		if (playing || track == null) {
			pausedSince = 0L;
		} else if (pausedSince == 0L) {
			pausedSince = System.nanoTime();
		}
		danceLevel += ((playing ? 1f : 0f) - danceLevel) * (1f - (float) Math.exp(-dt * 6f));
		danceClock += dt * (playing ? 1f : 0.2f);
		moveBars(dt);

		String wanted = wantedText(track);
		// the players show the song instead while the lyrics are not ticked for them
		playerLyric = showingLyric && barLyrics.get();
		if (showingLyric && !playerLyric) {
			wanted = songText(track);
		}
		// A line can be sung twice in a row ("oh-oh" twice): same text, but still a new line that animates in as one, with the box on its first
		// word. So lines are identified by instance, not only by text.
		MusicService.LyricLine line = playerLyric ? lyricLine : null;
		boolean sameTextAgain = line != null && shownLine != null && line != shownLine && wanted.equals(text);
		shownLine = line;
		if (!wanted.equals(text) || sameTextAgain) {
			// on a song change the old text does not slide away; the new one animates in alone, so two texts never overlap
			boolean newSong = track != null && MusicService.trackNumber() != textTrack;
			previousText = text;
			// the old line leaves from its current scroll offset, not from its start
			previousShift = drawnShift;
			drawnShift = 0f;
			text = wanted;
			swap = 0f;
		}
		textTrack = MusicService.trackNumber();
		frameDt = dt;
		// a new lyric line takes about a third of a second to come in: faster looked like a jump, and the line is already being read while it
		// arrives; the non-lyric text changes a bit slower
		swap = Math.min(1f, swap + dt * (playerLyric ? 3.2f : 2.3f) * (songRoll < 1f ? 2.4f : 1f));
		// ad-libs appear and disappear quickly, and the pill grows down just as fast
		backgroundShown += ((playerLyric && background != null ? 1f : 0f) - backgroundShown) * (1f - (float) Math.exp(-dt * 20f));
		if (backgroundShown < 0.01f && background == null) {
			backgroundShown = 0f;
			shownBackground = null;
		}

		float progress = track != null && track.durationMs() > 0 ? Mth.clamp((float) MusicService.position() / track.durationMs(), 0f, 1f) : 0f;
		progressShown = Math.abs(progress - progressShown) > 0.2f ? progress : progressShown + (progress - progressShown) * (1f - (float) Math.exp(-dt * 10f));
		return dt;
	}

	private int textTrack = -1;
	private String lastTitle = "";
	private String lastArtist = "";
	private String previousTitle = "";
	private String previousArtist = "";
	/** Song change progress, from 0 (new song just started) to 1 (title and artist fully rolled over). */
	private float songRoll = 1f;
	boolean showingLyric;
	/** Whether the small player and the window show the line: the lyrics are ticked for the info bar and the players. */
	private boolean playerLyric;
	MusicService.LyricLine lyricLine;
	long lyricAt;
	float lyricProgress;

	/** @return how many characters of the line are sung at the given time, using word timings when available */
	static float sungChars(MusicService.LyricLine line, long at) {
		if (LyricBreaks.is(line)) {
			// the dots of a break light up one after another over it, the last a moment before the next line
			long next = LyricBreaks.nextStart(MusicService.lyrics(), line);
			return LyricBreaks.progress(line.timeMs(), next, at) * line.text().length();
		}
		if (!line.words().isEmpty()) {
			float chars = 0f;
			for (MusicService.Word word : line.words()) {
				if (at >= word.endMs()) {
					chars = word.to();
				} else if (at >= word.startMs()) {
					// clamp to the word: a held word whose end is not after its start would otherwise run ahead and jump back
						float part = Mth.clamp((at - word.startMs()) / (float) Math.max(1L, word.endMs() - word.startMs()), 0f, 1f);
						chars = Math.max(chars, word.from() + (word.to() - word.from()) * part);
					break;
				} else {
					break;
				}
			}
			return chars;
		}
		return MusicService.lyricProgress(at) * line.text().length();
	}

	/** The current background vocal (ad-lib), or null; the last one stays while it fades out. */
	MusicService.Background background;
	private MusicService.Background shownBackground;
	/** When the background vocal shown first showed, by the lyrics' clock. */
	private long backgroundSince = AdlibTimes.NOT_SHOWN;
	private float backgroundShown;
	/** Displayed sung progress of the background vocal and which one it belongs to; only increases until another one starts. */
	private MusicService.Background sungBackground;
	private float backgroundSung;

	/** @return how many characters of the background vocal are sung at the given time */
	static float backgroundSungChars(MusicService.Background back, long at) {
		if (!back.words().isEmpty()) {
			// background vocal word timings can overlap and are not always ordered: the progress is the furthest any word has reached, not the
			// position of the first word that has not started
			float chars = 0f;
			for (MusicService.Word word : back.words()) {
				if (at >= word.endMs()) {
					chars = Math.max(chars, word.to());
				} else if (at >= word.startMs()) {
					float part = Mth.clamp((at - word.startMs()) / (float) Math.max(1L, word.endMs() - word.startMs()), 0f, 1f);
					chars = Math.max(chars, word.from() + (word.to() - word.from()) * part);
				}
			}
			return chars;
		}
		long end = AdlibTimes.ownEnd(back.startMs(), back.endMs(), AdlibTimes.NO_NEXT, back.text().length());
		float fraction = Mth.clamp((at - back.startMs()) / (float) Math.max(1L, end - back.startMs()), 0f, 1f);
		return fraction * back.text().length();
	}

	/**
	 * Draws the background vocal next to the line, dimmer than it: the sung part is brighter than the rest, but neither reaches the line's
	 * brightness, and there is no word box.
	 */
	private void drawBackground(Ui ui, float x, float y, float alpha, int color, float scale) {
		MusicService.Background back = shownBackground;
		if (back == null || backgroundShown < 0.02f) {
			return;
		}
		// the sung part only moves forward and trails slightly, like the line: the player's position reporting is a little uneven and the part
		// jittered back and forth with it
		if (sungBackground == null || back.startMs() != sungBackground.startMs() || !back.text().equals(sungBackground.text())) {
			sungBackground = back;
			backgroundSung = 0f;
		}
		float target = backgroundSungChars(back, lyricAt);
		backgroundSung += (Math.max(backgroundSung, target) - backgroundSung) * (1f - (float) Math.exp(-Math.min(frameDt, 0.05f) * 18f));
		int split = Mth.clamp(Math.round(backgroundSung), 0, back.text().length());
		float visible = alpha * backgroundShown;
		// Drawn in the menu font rather than the heavy slanted lyrics font, which blurred into blobs when small and faint. Inter semibold stays
		// clear at small sizes and is thick enough to read while staying lighter than the line, as a background voice should; the sung part is
		// nearly as bright as the line. It is vertically centered on where the lyrics font would sit, since it is slightly shorter.
		ui.textSegments(x, y + 0.6f * scale, scale * ADLIB_TEXT, new String[]{back.text().substring(0, split), back.text().substring(split)},
			new int[]{Ui.withOpacity(color, visible * AdlibLayout.ALPHA), Ui.withOpacity(color, visible * AdlibLayout.ALPHA * ADLIB_REST)}, true);
	}

	/** The part of a background vocal not sung yet, as a share of its sung part's brightness. */
	private static final float ADLIB_REST = 0.5f;

	/** Scale of the menu font for the background vocal relative to the given scale; Inter is wider than the lyrics font. */
	private static final float ADLIB_TEXT = 0.94f;

	/** @return the background vocal's width in the font it is drawn with */
	private static float adlibWidth(Ui ui, String text, float scale) {
		return ui.boldWidthScaled(text, scale * ADLIB_TEXT);
	}

	/** @return the room the background vocal takes next to the line, including the gap before it; 0 while there is none */
	private float backgroundRoom(Ui ui, float scale) {
		MusicService.Background back = shownBackground;
		if (back == null || backgroundShown < 0.02f) {
			return 0f;
		}
		return (adlibWidth(ui, back.text(), scale) + ADLIB_BESIDE + 1f) * backgroundShown;
	}

	private String wantedText(MusicService.Track track) {
		showingLyric = false;
		background = null;
		if (track == null) {
			return "";
		}
		// paused for a minute: show the song name again instead of the line it stopped at
		if (lyrics.get() && !pausedLong()) {
			long at = timedPosition();
			MusicService.LyricLine found = MusicService.lyricLineAt(at);
			if (found != null && !lineOver(found, at)) {
				showingLyric = true;
				lyricLine = found;
				lyricAt = at;
				background = adlibs.get() ? MusicService.backgroundAt(at) : null;
				if (background != null && background != shownBackground) {
					backgroundSince = at;
				}
				// a short adlib (a quick "hey") stays a moment after its own time, so it is seen at all
				if (background == null && adlibs.get() && shownBackground != null
					&& AdlibTimes.shown(shownBackground.startMs(), shownBackground.endMs(), backgroundSince, at)) {
					background = shownBackground;
				}
				if (background != null) {
					shownBackground = background;
				}
				lyricProgress = Mth.clamp(sungChars(found, at) / Math.max(1f, found.text().length()), 0f, 1f);
				return found.text();
			}
		}
		return songText(track);
	}

	/** @return the song as one text: the title with the artist, or the album without one */
	private static String songText(MusicService.Track track) {
		return !track.artist().isEmpty() ? track.title() + "  -  " + track.artist() : !track.album().isEmpty() ? track.title() + "  -  " + track.album() : track.title();
	}

	/** How long the HUD keeps the last line after it was sung; after that it shows the song again. */
	private static final long GAP_AFTER_LINE_MS = 3500L;

	/**
	 * @return whether the last line of the lyrics ended a while ago: the HUD shows the song again to the end. A break between lines is a line of
	 *         its own, shown as dots (see {@link LyricBreaks}). The line end comes from its word timings, otherwise it is estimated from its length.
	 */
	private boolean lineOver(MusicService.LyricLine line, long at) {
		List<MusicService.LyricLine> all = MusicService.lyrics();
		int index = all.indexOf(line);
		long next = index >= 0 && index + 1 < all.size() ? all.get(index + 1).timeMs() : Long.MAX_VALUE;
		return next == Long.MAX_VALUE && !LyricBreaks.is(line) && at > LyricBreaks.end(line) + GAP_AFTER_LINE_MS;
	}

	/** The current cover's most vivid color, or 0 without one; picked once per cover. */
	private int coverColor;
	/** The lightest a colored lyric text gets made at least, as a luminance from 0 to 1, and the least brightness of a glow from the cover. */
	private static final float READABLE_TEXT = 0.72f;
	private static final float BRIGHT_GLOW = 0.85f;

	/** Up to three colors of the current cover, for the Colored theme; picked once per cover. */
	private int[] coverPalette;

	/** @return the colors of the cover while its song plays, or null: without a cover, or with the music stopped */
	public int[] coverPalette() {
		return isEnabled() && MusicService.isPlaying() && hasCover && coverTrack == MusicService.trackNumber() ? coverPalette : null;
	}

	/** @return the cover's color while the cover belongs to the song playing, otherwise the given color */
	private int coverOr(int fallback) {
		return coverColor != 0 && hasCover && coverTrack == MusicService.trackNumber() ? coverColor : fallback;
	}

	/** @return the color of the world lyrics' text: white, the accent or the cover's (made light enough to read), or the custom one */
	int worldTextArgb() {
		return switch (worldTextColor.get()) {
			case WHITE -> 0xFFFFFFFF;
			case ACCENT -> readable(accentArgb());
			case COVER -> readable(coverOr(accentArgb()));
			case CUSTOM -> 0xFF000000 | worldTextCustom.get();
		};
	}

	/** @return the color of the world lyrics' glow: the accent, the cover's (bright enough to glow), white, or the custom one */
	int worldGlowArgb() {
		return glowColorOf(worldGlowColor.get());
	}

	private int glowColorOf(GlowColor choice) {
		return switch (choice) {
			case ACCENT -> accentArgb();
			case COVER -> bright(coverOr(accentArgb()));
			case WHITE -> 0xFFFFFFFF;
			case CUSTOM -> 0xFF000000 | worldGlowCustom.get();
		};
	}

	/** @return the color of the lyrics on screen (their highlight box, or the sung words without it): the accent, the cover's, white or the custom one */
	int lyricsArgb() {
		return lyricColorOf(lyricsColor.get());
	}

	private int lyricColorOf(LyricColor choice) {
		return switch (choice) {
			case ACCENT -> accentArgb();
			case COVER -> coverOr(accentArgb());
			case WHITE -> 0xFFFFFFFF;
			case CUSTOM -> 0xFF000000 | lyricsCustom.get();
		};
	}

	/** @return the cover's color while it belongs to the song playing, otherwise the accent: the tint of the now playing card */
	int coverTint() {
		return coverOr(accentArgb());
	}

	/** @return the color mixed with white as far as needed to be light enough for text on the sky */
	private static int readable(int argb) {
		float luminance = Ui.luminance(argb);
		return luminance >= READABLE_TEXT ? argb : Ui.mix(argb, 0xFFFFFFFF, (READABLE_TEXT - luminance) / (1f - luminance));
	}

	/** @return the color with its brightness raised to at least that of a glow, keeping its hue and saturation */
	private static int bright(int argb) {
		float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
		return hsb[2] >= BRIGHT_GLOW ? argb : 0xFF000000 | Color.HSBtoRGB(hsb[0], hsb[1], BRIGHT_GLOW);
	}

	/**
	 * Turns a prepared cover into the two cover textures in the free place, so the cover shown so far stays until the new one has faded in over
	 * it. The images were scaled off the render thread; here they are only handed to the GPU.
	 */
	private void uploadCover(CoverImages.Prepared cover) {
		coverColor = cover.color();
		coverPalette = cover.palette();
		var textures = Minecraft.getInstance().getTextureManager();
		int next = hasCover && coverFilled[coverSlot] ? 1 - coverSlot : coverSlot;
		textures.release(COVERS[next]);
		textures.release(COVERS_SMALL[next]);
		coverSide[next] = cover.large().getWidth();
		smallSide[next] = cover.small().getWidth();
		textures.register(COVERS[next], new SmoothTexture(cover.large()));
		textures.register(COVERS_SMALL[next], new SmoothTexture(cover.small()));
		if (!hasCover && coverFilled[1 - next]) {
			// no cover was shown: nothing to fade from
			textures.release(COVERS[1 - next]);
			textures.release(COVERS_SMALL[1 - next]);
			coverFilled[1 - next] = false;
		}
		coverFilled[next] = true;
		coverSlot = next;
		coverTrack = MusicService.trackNumber();
		hasCover = true;
		coverAmount = 0f;
	}

	/** Sets a demo song, so the appearance can be previewed without a music player. */
	public static void demo() {
		MusicService.demo(new MusicService.Track("Midnight City", "M83", "Hurry Up, We're Dreaming", 243000, "", ""),
			List.of(new MusicService.LyricLine(0, ""),
				new MusicService.LyricLine(1000, "Waiting in a car", List.of(),
					new MusicService.Background("(ooh, ooh)", List.of(), 1400, 90000)),
				new MusicService.LyricLine(60000, "Waiting for a ride in the dark")), 1500, true);
	}

	// --------------------------------------------------------------------------------------------------- info bar segment

	int accentArgb() {
		return 0xFF000000 | accent.get();
	}

	/** @return the current width of the info bar segment, animating as text and song change; 0 if there is none */
	public int inlineWidth(Ui ui) {
		float dt = update(Minecraft.getInstance());
		if (shown <= 0f) {
			return 0;
		}
		float full = PILL_LEFT + (showCover.get() ? SEGMENT_HEIGHT + 6 : 0f) + (playerLyric ? textWidth.get() : Math.max(90f, Math.min(textWidth.get(), ui.width(text))))
			+ (showBars.get() ? PILL_GAP + BARS_WIDTH + PILL_RIGHT : PILL_LEFT);
		// the width follows the text: quickly at first, then slowly
		segmentWidth += (full - segmentWidth) * (1f - (float) Math.exp(-dt * 13f));
		if (Math.abs(full - segmentWidth) < 0.3f) {
			segmentWidth = full;
		}
		float revealTarget = segmentWidth >= full * 0.96f ? 1f : 0f;
		textReveal += (revealTarget - textReveal) * (1f - (float) Math.exp(-dt * 14f));
		if (Math.abs(revealTarget - textReveal) < 0.01f) {
			textReveal = revealTarget;
		}
		return Math.round(segmentWidth * shown);
	}

	/** Draws the info bar segment into the space the bar assigned. */
	public void renderInline(Ui ui, int x, int centerY, int width, boolean framed) {
		int baseHeight = framed ? 24 : SEGMENT_HEIGHT + 2;
		int y = centerY - baseHeight / 2;
		// the pill grows downwards while a background vocal is shown below the line; the line stays in place
		int height = baseHeight + inlineExtraHeight();
		int accentColor = accentArgb();
		MusicService.Track track = MusicService.track();

		graphicsScissor(ui, x + 1, y, width - 2, height, () -> {
			int cursor = x + PILL_LEFT;
			if (showCover.get()) {
				drawCover(ui, cursor, centerY - SEGMENT_HEIGHT / 2 + 1, SEGMENT_HEIGHT - 2, accentColor);
				cursor += SEGMENT_HEIGHT + 6;
			}
			// the text is clipped well before the bars; a cut right next to them looks like a bug
			int barsWidth = showBars.get() ? PILL_GAP + BARS_WIDTH + PILL_RIGHT : PILL_LEFT;
			int room = Math.max(10, width - (cursor - x) - barsWidth);
			// clip slightly left of the text so the box on the first word keeps its corners
			ui.graphics.enableScissor(cursor - 4, y, cursor + room, y + height);
			if (textReveal > 0.01f) {
				drawText(ui, cursor, centerY, room, textReveal, HudStyle.text());
			}
			ui.graphics.disableScissor();
			if (showBars.get()) {
				drawBars(ui, x + width - PILL_RIGHT - BARS_WIDTH, centerY, accentColor, BAR_MAX_PILL);
			}
		});
		if (showProgress.get() && track != null && track.durationMs() > 0) {
			// inset from the pill's rounded ends, which curve in at the bottom, so the line does not run into the corners
				int trackWidth = Math.max(0, width - 2 * PROGRESS_INSET);
			ui.rounded(x + PROGRESS_INSET, y + height - 3, trackWidth, 1, Radius.pill(1), Ui.withOpacity(0xFFFFFFFF, 0.14f));
			ui.rounded(x + PROGRESS_INSET, y + height - 3, Math.round(trackWidth * progressShown), 1, Radius.pill(1), accentColor);
		}
	}

	private void graphicsScissor(Ui ui, int x, int y, int w, int h, Runnable draw) {
		ui.graphics.enableScissor(x, y, x + w, y + h);
		draw.run();
		ui.graphics.disableScissor();
	}

	private void drawCover(Ui ui, int x, int y, int size, int accentColor) {
		drawCover(ui, x, y, size, accentColor, 1f);
	}

	void drawCover(Ui ui, int x, int y, int size, int accentColor, float alpha) {
		if (hasCover) {
			float in = Mth.clamp(coverAmount, 0f, 1f);
			boolean fromOld = coverFilled[1 - coverSlot];
			if (fromOld && in < 1f) {
				drawCoverSlot(ui, 1 - coverSlot, x, y, size, alpha);
			}
			// a cross-fade from the old cover; without one the cover pops in slightly
			float pop = fromOld ? 1f : 0.8f + 0.2f * in;
			float drawn = size * pop;
			float offset = (size - drawn) / 2f;
			drawCoverSlot(ui, coverSlot, x + offset, y + offset, drawn, alpha * (fromOld ? in * in * (3f - 2f * in) : Mth.clamp(in * 1.5f, 0f, 1f)));
		} else {
			ui.rounded(x, y, size, size, size * 0.24f, Ui.withOpacity(accentColor, 0.9f * alpha));
			ui.icon("note", x + size / 5, y + size / 5, size - 2 * (size / 5), Ui.withOpacity(0xFFFFFFFF, alpha));
		}
	}

	/** Draws the cover with corners of its own radius, where it sits inside a rounded shape; as {@link #drawCover} otherwise. */
	void drawCoverRounded(Ui ui, int x, int y, int size, float radius, int accentColor, float alpha) {
		if (hasCover) {
			float in = Mth.clamp(coverAmount, 0f, 1f);
			boolean fromOld = coverFilled[1 - coverSlot];
			if (fromOld && in < 1f) {
				drawRoundedSlot(ui, 1 - coverSlot, x, y, size, radius, alpha);
			}
			float pop = fromOld ? 1f : 0.8f + 0.2f * in;
			int drawn = Math.round(size * pop);
			int offset = (size - drawn) / 2;
			drawRoundedSlot(ui, coverSlot, x + offset, y + offset, drawn, radius * pop, alpha * (fromOld ? in * in * (3f - 2f * in) : Mth.clamp(in * 1.5f, 0f, 1f)));
		} else {
			ui.rounded(x, y, size, size, radius, Ui.withOpacity(accentColor, 0.9f * alpha));
			int inset = Math.round(size * COVER_NOTE_INSET);
			ui.icon("note", x + inset, y + inset, size - 2 * inset, Ui.withOpacity(0xFFFFFFFF, alpha));
		}
	}

	private void drawRoundedSlot(Ui ui, int slot, int x, int y, int drawn, float radius, float alpha) {
		if (alpha <= 0.004f) {
			return;
		}
		Identifier id = ui.realSize(drawn) <= smallSide[slot] ? COVERS_SMALL[slot] : COVERS[slot];
		RoundedImage.draw(ui.graphics, Minecraft.getInstance().getTextureManager().getTexture(id).getTextureView(), x, y, drawn, drawn, radius, Ui.faded(Ui.withOpacity(0xFFFFFFFF, alpha)));
	}

	/** Draws one of the two covers; the small texture where the cover is small on screen, so the GPU never has to shrink the big one far. */
	private void drawCoverSlot(Ui ui, int slot, float x, float y, float drawn, float alpha) {
		if (alpha <= 0.004f) {
			return;
		}
		boolean small = ui.realSize(drawn) <= smallSide[slot];
		ui.imageCrisp(small ? COVERS_SMALL[slot] : COVERS[slot], x, y, drawn, small ? smallSide[slot] : coverSide[slot], Ui.withOpacity(0xFFFFFFFF, alpha));
	}

	/** Keeps the song up to date when no view that shows it did so lately, for what only needs its colors and loudness: a view that draws the song keeps the frame's time for itself. */
	public void updateWithoutView() {
		if (System.nanoTime() - lastUpdateStamp > NO_VIEW_NANOS) {
			update(Minecraft.getInstance());
		}
	}

	private static final long NO_VIEW_NANOS = 100_000_000L;

	/** Keeps the song up to date and advances its animations; called every frame by views of other features that show it (the info bar). */
	public void tickHud() {
		update(Minecraft.getInstance());
	}

	/** Draws the cover without a shadow, for small places such as the info bar's song segment. */
	public void drawCoverArt(Ui ui, int x, int y, int size, float alpha) {
		drawCover(ui, x, y, size, accentArgb(), alpha);
	}

	/** @return whether the info bar shows the lyrics in its song segment: lyrics on, the song shown in the info bar, and its lyrics tile on */
	public boolean barLyricsShown() {
		return isEnabled() && barLyricsChosen();
	}

	/** @return whether the settings put the lyrics in the info bar, whether or not the feature is on (for the page's preview) */
	boolean barLyricsChosen() {
		return lyrics.get() && barLyrics.get() && display.get() == Display.INFO_BAR;
	}

	/** @return the song and lyrics of the info bar's song segment */
	public BarLyrics barLyrics() {
		return barView;
	}

	/** @return whether a lyric line is shown right now in the world, where the info bar's song gives way to it */
	public boolean lyricLineShown() {
		return lyricsPlaying() && worldLyricsShown();
	}

	/** The cinematic camera cut to a new shot: the next line starts there afresh, without the old one moving up out of it. */
	public void freshLyrics() {
		motion.freshStart();
		inWorld.cut();
	}

	/** @return whether a lyric line is being sung and shown: lyrics on, a line now, and the song playing */
	boolean lyricsPlaying() {
		return isEnabled() && lyrics.get() && showingLyric && !pausedLong() && MusicService.isPlaying();
	}

	/** @return whether adlibs are shown */
	boolean adlibsShown() {
		return isEnabled() && lyrics.get() && adlibs.get();
	}

	/** Draws the cover for other features (the cinematic card); the cover texture is kept up to date here. */
	public void drawArtwork(Ui ui, int x, int y, int size, float alpha) {
		update(Minecraft.getInstance());
		ui.shadow(x, y, size, size, size * 0.24f, 6, 0.4f * alpha);
		drawCover(ui, x, y, size, accentArgb(), alpha);
	}

	/** @return where the song shows */
	Display display() {
		return display.get();
	}

	/** @return whether there is a song to show on the main menu */
	public boolean hasSong() {
		return isEnabled() && MusicService.track() != null;
	}

	/** How far the note of a cover without a picture stands from its edges, as a share of its size. */
	private static final float COVER_NOTE_INSET = 0.2f;

	/** The song the card showed last, kept while the card animates out. */
	private MusicService.Track cardTrack;

	// ---------------------------------------------------------------------------------------------- main menu

	/** @return the song for the main menu, kept for a moment after it ended so the island can animate out; null if there never was one */
	MusicService.Track menuTrack() {
		update(Minecraft.getInstance());
		MusicService.Track track = MusicService.track();
		if (track != null) {
			cardTrack = track;
		}
		return cardTrack;
	}

	/** @return the song's progress as shown, from 0 to 1, moving smoothly */
	float shownProgress() {
		return seeking ? seekFraction : progressShown;
	}

	/** @return whether the song bar is being dragged */
	boolean seekingNow() {
		return seeking;
	}

	/** Starts dragging a song bar at a point; {@link #menuCardDrag} and {@link #menuCardRelease} continue it. */
	void beginSeek(int barX, int barWidth, double mouseX) {
		seeking = true;
		menuBarX = barX;
		menuBarWidth = barWidth;
		seekFraction = Mth.clamp((float) ((mouseX - barX) / Math.max(1, barWidth)), 0f, 1f);
	}

	/** The line the main menu's next line was looked up for, and that next line. */
	private MusicService.LyricLine menuLineFor;
	private String menuNext = "";

	/** @return the line sung now, for the main menu, or null when there is none (no lyrics, a break, or lyrics are off) */
	public String menuLyricNow() {
		update(Minecraft.getInstance());
		return lyrics.get() && showingLyric && lyricLine != null && !pausedLong() && !lyricLine.text().isBlank() ? lyricLine.text() : null;
	}

	/** @return the line after the one sung now, for the main menu, or an empty text; looked up once per line */
	public String menuLyricNext() {
		if (lyricLine != menuLineFor) {
			menuLineFor = lyricLine;
			menuNext = "";
			List<MusicService.LyricLine> all = MusicService.lyrics();
			int at = lyricLine == null ? -1 : all.indexOf(lyricLine);
			for (int i = at + 1; at >= 0 && i < all.size(); i++) {
				if (!all.get(i).text().isBlank()) {
					menuNext = all.get(i).text();
					break;
				}
			}
		}
		return menuNext;
	}


	/** Draws a media button glyph: 0 previous, 1 play, 2 pause, 3 next; smooth shapes with rounded corners. */
	private static void glyph(Ui ui, int kind, int cx, int cy, int color) {
		Glyphs.draw(ui, kind, cx, cy, kind == 1 || kind == 2 ? 24f : 20f, color);
	}

	private boolean seeking;
	private float seekFraction;
	private int menuBarX;
	private int menuBarWidth;

	/** Drags the song bar: it follows the mouse, and the song seeks there on release. */
	public boolean menuCardDrag(double mouseX) {
		if (!seeking) {
			return false;
		}
		seekFraction = Mth.clamp((float) ((mouseX - menuBarX) / Math.max(1, menuBarWidth)), 0f, 1f);
		return true;
	}

	public boolean menuCardRelease() {
		if (!seeking) {
			return false;
		}
		seeking = false;
		MusicService.Track track = MusicService.track();
		if (track != null && track.durationMs() > 0) {
			MusicService.seek(Math.round(seekFraction * track.durationMs()));
			progressShown = seekFraction;
		}
		return true;
	}

	/**
	 * Draws title and artist the way every song display does: on a song change the old ones fade out upwards while the new ones come in from below.
	 * The title is drawn at its position and the artist at the given one.
	 */
	public void drawSongText(Ui ui, MusicService.Track track, int x, int titleY, int artistY, int room, float titleScale, int titleColor, int artistColor, float alpha) {
		String artist = track.artist().isEmpty() ? track.album() : track.artist();
		float e = ease(songRoll);
		float lift = 7f;
		if (songRoll < 1f) {
			if (!previousTitle.isEmpty()) {
				ui.textAtScaled(ui.fit(previousTitle, Math.round(room / titleScale)), x, titleY - e * lift, titleScale, Ui.withOpacity(titleColor, alpha * (1f - e) * (1f - e)));
			}
			if (!previousArtist.isEmpty()) {
				ui.text(ui.fit(previousArtist, room), x, Math.round(artistY - e * lift), Ui.withOpacity(artistColor, alpha * (1f - e) * (1f - e)));
			}
		}
		ui.textAtScaled(ui.fit(track.title(), Math.round(room / titleScale)), x, titleY + (1f - e) * lift, titleScale, Ui.withOpacity(titleColor, alpha * e));
		if (!artist.isEmpty()) {
			ui.text(ui.fit(artist, room), x, Math.round(artistY + (1f - e) * lift), Ui.withOpacity(artistColor, alpha * e));
		}
	}

	private static float ease(float t) {
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
	}

	/** The loose box for lyrics with only line timings: the line it belongs to and its edges, which only move forward. */
	private float looseKey = Float.NaN;
	private int looseStart;
	private int looseEnd;

	/** The line the pill shows, to distinguish a repeated line from the same line continuing. */
	private MusicService.LyricLine shownLine;

	/**
	 * @return the identity of a line for the word box: the sung line by its start time, so the same words sung twice are two lines; other lines
	 *         (the main menu card draws neighbors) by their text
	 */
	private float lineKey(String text) {
		if (lyricLine != null && lyricLine.text().equals(text)) {
			// in tenths of a second, keeping the value small enough to be exact as a float, with the track number in front
			return (MusicService.trackNumber() % 1000) * 10_000f + (lyricLine.timeMs() / 100L) % 10_000L;
		}
		// negative, so it never collides with a time-based key
		return -1f - ((MusicService.trackNumber() % 1000) * 1000f + Math.floorMod(text.hashCode(), 1000));
	}

	float frameDt;
	/** How much of the pill text is revealed: when a song starts, the pill first grows to its size, then the text appears. */
	private float textReveal = 1f;

	/**
	 * The box edges' springs are critically damped (2): the box glides to the next word and arrives without overshoot. Both edges move alike, so
	 * the box keeps its shape while it moves; with line timings only it is softer, so it flows evenly along the line.
	 */
	private static final float BOX_DAMPING = 2f;
	private static final float WORD_STIFFNESS = 700f;
	private static final float GLIDE_STIFFNESS = 110f;
	/** The box's height, its top above the text and its corners, in the line's units. */
	private static final float BOX_HEIGHT = 13f;
	private static final float BOX_ABOVE = 2f;
	private static final float BOX_CORNER = 4.5f;
	/** How opaque the box behind the sung word is, so the word on it reads. */
	private static final float BOX_OPACITY = 0.85f;
	private float boxLine = -1;
	private float boxLeft;
	private float boxRight;
	private float boxVelocityLeft;
	private float boxVelocityRight;
	/** Smoothed fill of the boxed word, from 0 to 1, so long held words fill slowly instead of just lighting up. */
	private float wordFill;
	private int fillStart = -1;
	private float lyricShift;
	/** The word the box was on, and when, for its line: so the box never moves back to the previous word. */
	private float holdKey = -1f;
	private int holdStart;
	private int holdEnd;
	private long holdTime;

	/** Draws the pill text: the old text fades out upwards while the new one fades in from below; long text scrolls so all of it can be read. */
	private void drawText(Ui ui, int x, int centerY, int room, float alpha, int color) {
		float lineTop = centerY - 4.5f;
		if (playerLyric && lyricLine != null && drawStrip(ui, x, lineTop, room, alpha, color)) {
			return;
		}
		stripLast = null;
		float e = ease(swap);
		float lift = 6f;
		// New lyric line: the old one drifts up slightly, fades and blurs during the first part of the change, while the new one rises into place
		// from slightly below and decelerates as it arrives. Previously the lines raced past each other sideways across most of the pill, which looked busy.
		float leave = Easing.easeOut(Mth.clamp(swap / 0.55f, 0f, 1f));
		float arrive = Easing.easeOut(Mth.clamp((swap - 0.18f) / 0.82f, 0f, 1f));
		if (swap < 1f && !previousText.isEmpty()) {
			if (playerLyric) {
				int fading = Ui.withOpacity(color, alpha * (1f - leave));
				float blur = leave * 1.3f;
				if (blur > 0.12f) {
					ui.blurredLyricAtScaled(previousText, x + previousShift, lineTop - leave * 5f, 1f, fading, blur);
				} else {
					ui.lyricAt(previousText, x + previousShift, lineTop - leave * 5f, fading);
				}
			} else {
				ui.textAt(previousText, x, lineTop - e * lift, Ui.withOpacity(color, alpha * (1f - e) * (1f - e)));
			}
		}
		float rise = playerLyric ? (1f - arrive) * 6f : (1f - e) * lift;
		float enter = 0f;
		if (playerLyric) {
			e = arrive;
		}
		int textW = playerLyric ? ui.lyricTextWidth(text) : ui.width(text);
		float shift = 0f;
		if (playerLyric && textW + BOX_MARGIN > room) {
			// the line scrolls with the sung word, so the boxed word is always visible, including the last one
			float over = textW + BOX_MARGIN - room;
			float wanted = -Mth.clamp((boxLeft + boxRight) / 2f - room * 0.5f, 0f, over);
			lyricShift += (wanted - lyricShift) * (1f - (float) Math.exp(-frameDt * 7f));
			// never let the line hide the box: whatever the smoothing state, the box is fully visible at the first and last word
			shift = Math.max(Math.min(lyricShift, room - 1f - boxRight), -boxLeft);
		} else if (textW > room) {
			lyricShift = 0f;
			float over = textW - room;
			float period = over / 24f + 5f;
			float phase = (System.nanoTime() / 1_000_000_000f % period) / period;
			// pause at the start, scroll, pause at the end, scroll back, all eased
			float along = phase < 0.15f ? 0f : phase < 0.55f ? ease((phase - 0.15f) / 0.4f) : phase < 0.7f ? 1f : phase < 0.95f ? 1f - ease((phase - 0.7f) / 0.25f) : 0f;
			shift = -over * along;
		}
		drawnShift = shift;
		float textX = x + shift + enter;
		float textY = lineTop + rise;
		if (playerLyric) {
			drawLyric(ui, textX, textY, alpha * e, color);
			// the background vocal sits below the line, in the room the pill opens for it; it stays in place while the line scrolls or the next line
			// comes in, so it is not faded out and in with every line
			drawBackground(ui, adlibX(ui, x, room), lineTop + ADLIB_DROP, alpha, color, BACKGROUND_SCALE);
		} else {
			ui.textAt(text, textX, textY, Ui.withOpacity(color, alpha * e));
		}
	}

	private MusicService.LyricLine stripLast;
	private int stripIndex;
	private float stripShift;
	private static final float STRIP_GAP = 16f;
	/** The background vocal is drawn smaller than the line (see {@link AdlibLayout}), allowing for the menu font it is drawn in. */
	private static final float BACKGROUND_SCALE = AdlibLayout.SCALE / ADLIB_TEXT;

	/**
	 * For short lines there is room to show the previous and next line beside them, as one strip that slides along as the song progresses.
	 *
	 * @return false if the line is too long for that, in which case the regular rendering is used
	 */
	private boolean drawStrip(Ui ui, int x, float lineTop, int room, float alpha, int color) {
		int textW = ui.lyricTextWidth(text);
		if (textW + STRIP_GAP + 12 > room) {
			return false;
		}
		List<MusicService.LyricLine> all = MusicService.lyrics();
		// the lyrics may have been replaced (another song, or the same song found again) while the sung line stayed the same: find the index again,
		// and draw nothing from a list that no longer contains it
		if (stripIndex < 0 || stripIndex >= all.size() || all.get(stripIndex) != stripLast) {
			stripLast = null;
		}
		if (all.isEmpty()) {
			return false;
		}
		if (lyricLine != stripLast) {
			int index = all.indexOf(lyricLine);
			if (index < 0) {
				return false;
			}
			// the strip slides by the width of the line just sung, so the old line leaves to the left and the next one takes its place
			stripShift = stripLast != null && index == stripIndex + 1 && stripIndex >= 0 && stripIndex < all.size() ? ui.lyricTextWidth(all.get(stripIndex).text()) + STRIP_GAP : 0f;
			stripLast = lyricLine;
			stripIndex = index;
		}
		drawnShift = 0f;
		stripShift *= (float) Math.exp(-frameDt * 14f);
		if (stripShift < 0.3f) {
			stripShift = 0f;
		}
		String before = stripIndex > 0 ? all.get(stripIndex - 1).text() : "";
		String after = stripIndex + 1 < all.size() ? all.get(stripIndex + 1).text() : "";
		float beforeW = before.isEmpty() ? 0f : ui.lyricTextWidth(before) + STRIP_GAP;
		float travel = Math.max(1f, stripShift > 0f ? stripShift : 1f);
		float moved = stripShift > 0f ? Mth.clamp(stripShift / (beforeW > 0 ? beforeW : travel), 0f, 1f) : 0f;
		float faint = 0.38f;
		if (!before.isEmpty()) {
			ui.lyricAt(before, x - beforeW + stripShift, lineTop, Ui.withOpacity(color, alpha * (faint + (1f - faint) * moved)));
		}
		if (!after.isEmpty()) {
			ui.lyricAt(after, x + textW + STRIP_GAP + stripShift, lineTop, Ui.withOpacity(color, alpha * faint));
		}
		drawLyric(ui, x + stripShift, lineTop, alpha * (faint + (1f - faint) * (1f - moved)), color);
		// the background vocal sits below the line, in the room the pill opens for it; it does not slide with the strip
		drawBackground(ui, adlibX(ui, x, room), lineTop + ADLIB_DROP, alpha, color, BACKGROUND_SCALE);
		return true;
	}

	/** @return the x where the ad-lib starts: centered in the pill's text area, or at its start when wider */
	private float adlibX(Ui ui, float x, int room) {
		MusicService.Background back = shownBackground;
		if (back == null) {
			return x;
		}
		float w = adlibWidth(ui, back.text(), BACKGROUND_SCALE);
		return x + Math.max(0f, (room - w) / 2f);
	}

	/**
	 * The background vocal below the line: the line's text height and how far its highlight box reaches below it, then the gap kept from the line
	 * (see {@link AdlibLayout}); how far below the line's top the vocal sits, how much the pill grows for it, and the gap beside a line.
	 */
	private static final float LINE_TEXT_HEIGHT = 9f;
	/** Where the middle of a line's small letters is, below its top, where the dots of a break stand. */
	private static final float LINE_DOTS_MIDDLE = 4f;
	private static final float BOX_BELOW_TEXT = 3f;
	private static final float ADLIB_DROP = LINE_TEXT_HEIGHT + BOX_BELOW_TEXT + LINE_TEXT_HEIGHT * AdlibLayout.GAP_SHARE;
	private static final float ADLIB_EXTRA = ADLIB_DROP + LINE_TEXT_HEIGHT * AdlibLayout.SCALE - LINE_TEXT_HEIGHT;
	private static final float ADLIB_BESIDE = BOX_BELOW_TEXT + LINE_TEXT_HEIGHT * AdlibLayout.GAP_SHARE;

	/** @return the extra height of the info bar segment right now, for the background vocal below the line */
	public int inlineExtraHeight() {
		return Math.round(ADLIB_EXTRA * backgroundShown);
	}

	/** Draws a lyric line with a colored box behind the sung word that slides from word to word. */
	private void drawLyric(Ui ui, float x, float y, float alpha, int color) {
		drawLyric(ui, x, y, alpha, color, 1f);
	}

	private void drawLyric(Ui ui, float x, float y, float alpha, int color, float scale) {
		drawLyric(ui, x, y, alpha, color, scale, text);
	}

	/** Same for a given line: the main menu card draws the line in its slot, which is not always the pill's current one yet. */
	private void drawLyric(Ui ui, float x, float y, float alpha, int color, float scale, String text) {
		if (LyricBreaks.MARK.equals(text) && lyricLine != null && LyricBreaks.is(lyricLine)) {
			// a break: its dots in place of the text, in the lyrics' color
			LyricDots.draw(ui, lyricLine, lyricAt, x, y + LINE_DOTS_MIDDLE * scale, LINE_TEXT_HEIGHT * scale, Ui.withOpacity(lyricsArgb(), alpha));
			return;
		}
		int total = text.length();
		float position = lyricProgress * total;
		// with line timings only: how far the box extends around the estimated position; -1 with word timings
		float looseSlack = -1f;
		int start = 0;
		int end = text.length();
		// duration of the sung word in milliseconds (-1 if unknown), and its start and end when words have timings
		long wordMs = -1;
		long wordFromMs = -1;
		long wordToMs = -1;
		if (lyricLine != null && !lyricLine.words().isEmpty() && lyricLine.text().equals(text)) {
			// word timings known: the box covers the sung word, or all words sung simultaneously
			MusicService.Word held = lyricLine.words().get(0);
			int first = -1;
			int last = -1;
			for (MusicService.Word word : lyricLine.words()) {
				if (word.startMs() <= lyricAt) {
					held = word;
					if (lyricAt < word.endMs()) {
						first = first < 0 ? word.from() : Math.min(first, word.from());
						last = Math.max(last, word.to());
					}
				}
			}
			// between two words the box stays on the last sung one
			start = first >= 0 ? first : held.from();
			end = Math.min(total, first >= 0 ? last : held.to());
			// timings can be per syllable: the box covers the whole word, not a part of it, so it does not stop mid-word
			start = Math.max(0, Math.min(start, total));
			while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
				start--;
			}
			while (end < total && !Character.isWhitespace(text.charAt(end))) {
				end++;
			}
			long fromMs = Long.MAX_VALUE;
			long toMs = 0;
			for (MusicService.Word timed : lyricLine.words()) {
				if (timed.from() < end && timed.to() > start) {
					fromMs = Math.min(fromMs, timed.startMs());
					toMs = Math.max(toMs, timed.endMs());
				}
			}
			wordMs = toMs > fromMs ? toMs - fromMs : held.endMs() - held.startMs();
			if (toMs > fromMs) {
				wordFromMs = fromMs;
				wordToMs = toMs;
			}
		} else {
			// Only the line timing is known, so the position within it is estimated from the characters: a box on a single word was a word early or
			// late about half the time. The box is looser here, covering about three quarters of a word on each side of the estimate, so it includes
			// the sung word plus part of the next and is right far more often.
			String[] words = text.split(" ");
			float slack = Math.max(2f, total / (float) Math.max(1, words.length) * 0.75f);
			looseSlack = slack;
			int searchFrom = 0;
			int first = -1;
			int last = -1;
			for (String word : words) {
				int wordStart = text.indexOf(word, searchFrom);
				int wordEnd = wordStart + word.length();
				searchFrom = wordEnd;
				if (word.isEmpty()) {
					continue;
				}
				// every word the window around the estimate touches
				if (wordEnd > position - slack && wordStart < position + slack) {
					if (first < 0) {
						first = wordStart;
					}
					last = wordEnd;
				}
			}
			if (first < 0) {
				first = 0;
				last = Math.min(total, words.length > 0 ? words[0].length() : total);
			}
			// Within a line both edges only move forward: the player position is slightly uneven, the estimate occasionally moved back with it, and a
			// word dropped out of the box or came back, which looked like the box restarting.
			float key = lineKey(text);
			if (key != looseKey) {
				looseKey = key;
				looseStart = first;
				looseEnd = last;
			} else {
				looseStart = Math.max(looseStart, first);
				looseEnd = Math.max(looseEnd, last);
			}
			start = Math.min(looseStart, looseEnd);
			end = looseEnd;
		}
		if (wordMs < 0 && lyricLine != null && lyricLine.text().equals(text)) {
			// line timings only: each word gets its share of the line duration, proportional to its character count
			List<MusicService.LyricLine> all = MusicService.lyrics();
			int index = all.indexOf(lyricLine);
			long lineMs = index >= 0 && index + 1 < all.size() ? Math.min(6500L, all.get(index + 1).timeMs() - lyricLine.timeMs()) : 4000L;
			wordMs = lineMs * Math.max(1, end - start) / Math.max(1, total);
		}
		// the fill animation is only for long held words (a quick word just lights up and is gone; a fill would only flicker), and only with word
		// timings: with line timings the hold duration is guessed from the characters, and the fill went through the word at a pace unrelated to
		// the song
		boolean wordSynced = lyricLine != null && !lyricLine.words().isEmpty() && lyricLine.text().equals(text);
		float longWord = wordSynced ? HighlightBox.longWord(wordMs) : 0f;
		// word timings may include an adjacent space, which would place the box over the end of the previous word
		end = Math.max(0, Math.min(end, total));
		start = Math.max(0, Math.min(start, end));
		while (start < end && Character.isWhitespace(text.charAt(start))) {
			start++;
		}
		while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		// The reported position is slightly uneven and can briefly fall before the start of the highlighted word, which would move the box back to
		// the previous word: within a line it only moves forward, except after a large backwards seek
		float holdLineKey = lineKey(text);
		if (holdLineKey != holdKey) {
			holdKey = holdLineKey;
			holdStart = start;
			holdEnd = end;
			holdTime = lyricAt;
		} else if (start < holdStart && lyricAt > holdTime - 1500L) {
			start = holdStart;
			end = holdEnd;
		} else {
			holdStart = start;
			holdEnd = end;
			holdTime = lyricAt;
		}
		String before = text.substring(0, start);
		String word = text.substring(start, end);
		String after = text.substring(end);
		// The parts are drawn consecutively in font units at one position, so a part starts at the width of what precedes it, measured in the font
		// used at this size: a line's width at default size, scaled up, differs from its width at the larger size.
		float targetWidth = ui.lyricWidthScaled(word, scale);
		float targetX = ui.lyricWidthScaled(before, scale);
		// The box starts slightly before the word but within the space, so it does not cover the end of the previous word. Its position is stored in
		// line units at default size and only scaled when drawn: the sung line in the window grows as it enters, and a box stored in pixels lagged
		// behind its glyphs while they grew; also the pill and the window, at other sizes, took over a box sized for the other.
		float targetLeft = targetX / scale - 2f;
		float targetRight = (targetX + targetWidth) / scale + 3f;
		float lineId = lineKey(text);
		boolean loose = looseSlack > 0f;
		if (loose) {
			// With line timings only, the box does not hop between words: it glides with the estimated position between two points in the text
			// (fractional characters included), and only forward within the line.
			float from = Mth.clamp(position - looseSlack, 0f, total);
			float to = Mth.clamp(position + looseSlack, 0f, total);
			float left = widthAt(ui, text, from, scale) / scale - 2f;
			float right = widthAt(ui, text, to, scale) / scale + 3f;
			if (looseLine != lineId) {
				looseLine = lineId;
				looseLeft = left;
				looseRight = right;
			}
			looseLeft = Math.max(looseLeft, left);
			looseRight = Math.max(looseRight, right);
			targetLeft = looseLeft;
			targetRight = looseRight;
		}
		if (boxLine != lineId) {
			// new line: the box jumps to its first word and moves on from there
			boxLine = lineId;
			boxLeft = targetLeft;
			boxRight = targetRight;
			boxVelocityLeft = 0f;
			boxVelocityRight = 0f;
			lyricShift = 0f;
		}
		// The box edges are critically damped springs, both alike, integrated in small sub-steps for frame-rate independence; when a word is skipped the
		// box does not lag, since the springs only pull towards the target. The loose line box uses softer springs so it flows instead of ticking along.
		float step = Math.min(frameDt, 0.05f) / 6f;
		float stiff = loose ? GLIDE_STIFFNESS : WORD_STIFFNESS;
		float damping = BOX_DAMPING * (float) Math.sqrt(stiff);
		for (int i = 0; i < 6; i++) {
			boxVelocityRight += ((targetRight - boxRight) * stiff - boxVelocityRight * damping) * step;
			boxRight += boxVelocityRight * step;
			boxVelocityLeft += ((targetLeft - boxLeft) * stiff - boxVelocityLeft * damping) * step;
			boxLeft += boxVelocityLeft * step;
		}
		// sung progress of the word: from word timings when known, otherwise from the line progress
		float sungNow = lyricLine != null && lyricLine.text().equals(text) ? sungChars(lyricLine, lyricAt) : position;
		// with word timings the fill sweeps through the word evenly from its start to its end, reaching the end
		float fillTarget = wordToMs > wordFromMs && wordFromMs >= 0
			? Mth.clamp((lyricAt - wordFromMs) / (float) (wordToMs - wordFromMs), 0f, 1f)
			: Mth.clamp((sungNow - start) / Math.max(1f, end - start), 0f, 1f);
		if (start != fillStart) {
			fillStart = start;
			wordFill = 0f;
		}
		wordFill += (fillTarget - wordFill) * (1f - (float) Math.exp(-Math.min(frameDt, 0.05f) * 26f));
		// When the whole line is sung (its last word ended, or with line timings only, its time did) and it still stays (nothing follows, end of the
		// song), the box does not stay half on the last word: it fades out and the last word takes the color of the rest of the sung line, so the
		// line rests as one.
		boolean lineDone = false;
		if (lyricLine != null && lyricLine.text().equals(text)) {
			if (lyricLine.words().isEmpty()) {
				lineDone = lyricProgress >= 0.999f;
			} else {
				long lastEnd = 0;
				for (MusicService.Word timed : lyricLine.words()) {
					lastEnd = Math.max(lastEnd, timed.endMs());
				}
				lineDone = lyricAt >= lastEnd;
			}
		}
		if (lineDoneKey != lineKey(text)) {
			lineDoneKey = lineKey(text);
			lineDoneFade = 0f;
		}
		lineDoneFade += ((lineDone ? 1f : 0f) - lineDoneFade) * (1f - (float) Math.exp(-Math.min(frameDt, 0.05f) * 7f));
		float boxShown = 1f - lineDoneFade;

		boolean boxed = highlightBox.get();
		int accentColor = lyricsArgb();
		float boxHeight = BOX_HEIGHT * scale;
		float left = x + boxLeft * scale;
		float width = Math.max(4f * scale, (boxRight - boxLeft) * scale);
		// positioned in screen pixels rather than menu pixels, so it moves in fine steps
		if (boxed && boxShown > 0.01f) {
			ui.roundedSmooth(left, y - BOX_ABOVE * scale, width, boxHeight, BOX_CORNER * scale, Ui.withOpacity(accentColor, BOX_OPACITY * alpha * boxShown));
		}
		// A lighter part of the box grows through the word as it is sung, so long held words still show movement. Its edge is a position within the
		// word itself, not a fraction of the box: the box still stretches and shrinks as it arrives, and a fraction of it went forward, back and
		// forward again. It starts where the word starts, so the part of the box still traveling from the previous word is not filled.
		float fillFrom = Math.max(boxLeft, targetLeft) * scale;
		float fillEdge = Math.min(boxLeft * scale + width, (targetLeft + (targetRight - targetLeft) * wordFill) * scale);
		float fillWidth = Math.max(0f, fillEdge - fillFrom);
		if (boxed && fillWidth >= 2f && longWord > 0f) {
			ui.roundedSmooth(x + fillFrom, y - BOX_ABOVE * scale, fillWidth, boxHeight, Math.min(BOX_CORNER * scale, fillWidth / 2f), Ui.withOpacity(0xFFFFFFFF, 0.22f * alpha * longWord * boxShown));
		}
		// Highlight exactly the glyphs the box currently covers: highlighted from the box target, they lit up before the box arrived, and the box slid
		// in under glyphs that were already white.
		float[] middles = letterMiddles(ui, text, scale);
		int litStart = total;
		int litEnd = 0;
		for (int i = 0; i < total; i++) {
			if (middles[i] >= boxLeft * scale && middles[i] <= boxRight * scale) {
				litStart = Math.min(litStart, i);
				litEnd = i + 1;
			}
		}
		if (litEnd > litStart) {
			// never split a surrogate pair
			if (litStart > 0 && Character.isLowSurrogate(text.charAt(litStart))) {
				litStart--;
			}
			if (litEnd < total && Character.isLowSurrogate(text.charAt(litEnd))) {
				litEnd++;
			}
			before = text.substring(0, litStart);
			word = text.substring(litStart, litEnd);
			after = text.substring(litEnd);
		} else {
			before = text;
			word = "";
			after = "";
		}
		// the highlighted glyphs blend into the sung color as the box fades at the end of the line
		int sungColor = Ui.withOpacity(color, alpha * 0.8f);
		// on the box the word is white or dark, whichever reads on its color; without the box it is in the lyrics color
		int wordColor = Ui.withOpacity(boxed ? HighlightBox.textOn(accentColor) : accentColor, alpha);
		ui.lyricSegments(x, y, scale, new String[]{before, word, after},
			new int[]{sungColor, Ui.mix(wordColor, sungColor, lineDoneFade), Ui.mix(Ui.withOpacity(color, alpha * 0.4f), sungColor, lineDoneFade)});
	}

	/** Fade-out of the box of a fully sung line, from 0 to 1, and which line it applies to. */
	private float lineDoneFade;
	private float lineDoneKey = Float.NaN;
	/** Glyph center positions per line and size, measured once; the pill and the card each keep their own entries. */
	private final Map<String, float[]> middlesCache = new HashMap<>();

	/** @return the center x of every glyph from the text start at the given scale, measured once and cached */
	private float[] letterMiddles(Ui ui, String text, float scale) {
		String key = scale + "|" + text;
		float[] middles = middlesCache.get(key);
		if (middles == null) {
			if (middlesCache.size() > 8) {
				// only keep entries for lines currently drawn: the pill's, the card's, and those animating out
				middlesCache.clear();
			}
			middles = new float[text.length()];
			float upTo = 0f;
			for (int i = 0; i < text.length(); i++) {
				float next = ui.lyricWidthScaled(text.substring(0, i + 1), scale);
				middles[i] = (upTo + next) / 2f;
				upTo = next;
			}
			middlesCache.put(key, middles);
		}
		return middles;
	}

	/** The gliding box for lines with line timings only: its line and edges, which only move forward. */
	private float looseLine = Float.NaN;
	private float looseLeft;
	private float looseRight;

	/** @return the text width up to a (possibly fractional) character position */
	private static float widthAt(Ui ui, String text, float at, float scale) {
		int whole = Mth.clamp((int) Math.floor(at), 0, text.length());
		float upTo = ui.lyricWidthScaled(text.substring(0, whole), scale);
		if (whole >= text.length()) {
			return upTo;
		}
		float next = ui.lyricWidthScaled(text.substring(0, whole + 1), scale);
		return upTo + (next - upTo) * (at - whole);
	}

	/** Dancing bars: capsules centered on the line, shrinking to dots when nothing plays. */
	private static final int BAR_COUNT = 4;
	private static final int BAR_WIDTH = 3;
	private static final int BAR_GAP = 2;
	private static final int BAR_MIN = 3;
	private static final int BAR_MAX = 15;
	/** Maximum bar height in the 24 px high pill, well below its height. */
	private static final int BAR_MAX_PILL = 11;
	/** Pill padding: left of the cover, between text and bars, and right of the bars. */
	private static final int PILL_LEFT = 9;
	/** Inset of the progress line from the pill ends. */
	private static final int PROGRESS_INSET = 14;
	private static final int PILL_GAP = 12;
	private static final int PILL_RIGHT = 10;
	private static final int BARS_WIDTH = BAR_COUNT * BAR_WIDTH + (BAR_COUNT - 1) * BAR_GAP;

	/** @param x left edge of the bars, which are {@link #BARS_WIDTH} wide */
	void drawBars(Ui ui, int x, int centerY, int color, int maxHeight) {
		for (int i = 0; i < BAR_COUNT; i++) {
			// With nothing playing they shrink to dots of the same shape, so there is no change of element. Heights are not rounded to pixels and
			// the shape is rasterized at screen resolution, so bars grow and shrink without steps.
			float height = BAR_MIN + (maxHeight - BAR_MIN) * barLevel[i] * danceLevel;
			float alpha = 0.5f + 0.5f * danceLevel;
			ui.roundedSmooth(x + i * (BAR_WIDTH + BAR_GAP), centerY - height / 2f, BAR_WIDTH, height, BAR_WIDTH / 2f, Ui.withOpacity(color, alpha));
		}
	}

	/** Draws the dancing bars at any size, as the info bar's song segment and the lyrics show them. */
	public void drawLevelBars(Ui ui, float x, float centerY, float barWidth, float gap, float maxHeight, int color, float alpha) {
		float least = Math.min(BAR_MIN, maxHeight * 0.3f);
		for (int i = 0; i < BAR_COUNT; i++) {
			float height = least + (maxHeight - least) * barLevel[i] * danceLevel;
			ui.roundedSmooth(x + i * (barWidth + gap), centerY - height / 2f, barWidth, height, barWidth / 2f,
				Ui.withOpacity(color, alpha * (0.5f + 0.5f * danceLevel)));
		}
	}

	/** @return the width of {@link #drawLevelBars} with these sizes */
	public static float levelBarsWidth(float barWidth, float gap) {
		return BAR_COUNT * barWidth + (BAR_COUNT - 1) * gap;
	}

	/** Height of each bar from 0 to 1, following its target with a fast attack and slower release, like an audio level meter. */
	private final float[] barLevel = new float[BAR_COUNT];

	private void moveBars(float dt) {
		for (int i = 0; i < BAR_COUNT; i++) {
			// Every bar has its own rhythm from three combined oscillations of different speeds, so they are never all up or all down at once and
			// the pattern does not visibly repeat.
			double t = danceClock;
			float a = (float) Math.sin(t * (3.1 + i * 1.3) + i * 2.1);
			float b = (float) Math.sin(t * (5.7 + i * 1.9) + i * 0.9);
			float c = (float) Math.sin(t * (9.3 + i * 2.7) + i * 1.7);
			float raw = Mth.clamp(0.5f + 0.5f * (0.5f * a + 0.3f * b + 0.2f * c), 0f, 1f);
			float target = 0.08f + 0.92f * (float) Math.pow(raw, 1.35);
			float rate = target > barLevel[i] ? 15f : 7f;
			barLevel[i] += (target - barLevel[i]) * (1f - (float) Math.exp(-dt * rate));
		}
	}

	// ---------------------------------------------------------------------------------------------------- window

	/** The small player: its pill's height and least width, and the row of controls that slides out below it while the mouse is free. */
	private static final int MINI_HEIGHT = 24;
	private static final int MINI_LEAST_WIDTH = 120;
	private static final float MINI_ROW = 22f;
	private static final float MINI_ROW_LIFT = 3f;
	private static final int MINI_CONTROLS_WIDTH = 170;
	private static final float MINI_PAD = 10f;
	private static final float MINI_BUTTON_GAP = 18f;
	private static final float MINI_BUTTON_SIZE = 11f;
	/** The window's controls, top right: their size, the gap between them, and the room the song text gives up for them. */
	private static final float WINDOW_BUTTON_SIZE = 13f;
	private static final float WINDOW_BUTTON_GAP = 20f;
	private static final int WINDOW_CONTROLS_ROOM = 40;
	private static final int COMPACT_HEIGHT = 92;
	private static final int FULL_HEIGHT = 158;
	/** Line spacing of the lyrics in the window. */
	private static final float LYRIC_LINE_HEIGHT = 19f;
	/** How far the word box extends beyond the line on either side, including its spring overshoot. */
	private static final float BOX_MARGIN = 9f;
	private static final int COMPACT_WIDTH = 264;
	private static final int FULL_WIDTH = 324;

	private final Eased shownScale = new Eased();
	/** The controls of the small player and the window, usable while the mouse is free. */
	final PlayerControls controls = new PlayerControls();
	private final Eased lyricView = new Eased();
	/** Index of the lyric line centered in the view, animating between lines so the lyrics scroll. */
	private float scrollLine = -1f;
	private List<MusicService.LyricLine> scrollLyrics;
	private float viewShift;

	/**
	 * A click on a screen: the small player's or the window's controls take it when it hits one of them, while the mouse is free and the screen
	 * has nothing of its own there (the screen always wins).
	 *
	 * @return whether the click was taken, and the screen does not get it
	 */
	public boolean playerClick(Screen screen, double x, double y, int button) {
		if (button != 0 || !isEnabled() || display.get() == Display.INFO_BAR || PlayerControls.screenHas(screen, x, y)) {
			return false;
		}
		return controls.click(x, y);
	}

	/** @return whether a mouse release ended a drag of the player's song bar, which then seeks */
	public boolean playerRelease(int button) {
		return button == 0 && controls.release();
	}

	/** Draws the standalone window, if that is the selected display. */
	public void renderWindow(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || display.get() == Display.INFO_BAR || client.level == null) {
			return;
		}
		graphics.nextStratum();
		drawHud(graphics, client, 1f, false);
	}

	/**
	 * Draws the player at its place and size on screen. The preview in the menu draws it inside its own scaled pose, fully shown and at its
	 * set size (no sliding in or easing), and leaves the bounds the HUD editor uses alone.
	 */
	private void drawHud(GuiGraphicsExtractor graphics, Minecraft client, float baseZoom, boolean preview) {
		if (display.get() == Display.MINI) {
			renderMini(graphics, client, baseZoom, preview);
			return;
		}
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
		float s = preview ? windowScale.get() : shownScale.follow(windowScale.get());
		Ui.setZoom(baseZoom * s);
		update(client);
		boolean editing = HudEditScreen.isOpen() || preview;
		MusicService.Track track = MusicService.track();
		boolean hasLyrics = lyrics.get() && barLyrics.get() && track != null && !MusicService.lyrics().isEmpty() && !pausedLong();
		// the window grows taller when lyrics are available, animated
		float view = lyricView.follow(hasLyrics ? 1f : 0f);
		int width = Math.round(Mth.lerp(view, COMPACT_WIDTH, FULL_WIDTH));
		int height = Math.round(Mth.lerp(view, COMPACT_HEIGHT, FULL_HEIGHT));
		// while edited it is shown fully and in place instead of sliding in, so the editor frame fits it
		float visible = editing ? 1f : shown;
		if (visible > 0.01f) {
			var pose = graphics.pose();
			pose.pushMatrix();
			// slides in from the nearer screen side
			float slide = (1f - visible) * 28f * (windowX.get() > graphics.guiWidth() / 2f ? 1f : -1f);
			if (!preview) {
				controls.begin(windowX.get() + slide, windowY.get(), s);
				controls.reveal(true);
			}
			pose.translate(windowX.get() + slide, windowY.get());
			pose.scale(s, s);
			try {
				drawWindowContents(ui, track, visible, width, height, view, !preview);
			} finally {
				pose.popMatrix();
			}
		}
		Ui.setZoom(baseZoom);
		if (!preview) {
			bounds = new int[]{Math.round(windowX.get()), Math.round(windowY.get()), Math.round(width * s), Math.round(height * s)};
		}
	}

	/**
	 * Draws the mini player: the info bar segment in its own freely placeable pill. While the mouse is free and over it, a row with the controls
	 * and the seek bar slides out below the song.
	 */
	private void renderMini(GuiGraphicsExtractor graphics, Minecraft client, float baseZoom, boolean preview) {
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
		float s = preview ? windowScale.get() : shownScale.follow(windowScale.get());
		Ui.setZoom(baseZoom * s);
		int height = MINI_HEIGHT;
		int width = inlineWidth(ui);
		boolean editing = HudEditScreen.isOpen() || preview;
		if (editing && width < MINI_LEAST_WIDTH) {
			width = MINI_LEAST_WIDTH;
		}
		if (width > 0) {
			float slide = (1f - (editing ? 1f : shown)) * 20f * (windowX.get() > graphics.guiWidth() / 2f ? 1f : -1f);
			float open = 0f;
			if (!preview) {
				controls.begin(windowX.get() + slide, windowY.get(), s);
				// the row stays while the mouse moves down into it
				controls.reveal(controls.over(0f, 0f, width, height + MINI_ROW * controls.reveal()));
				open = controls.reveal();
			}
			int shownWidth = Math.round(Mth.lerp(open, width, Math.max(width, MINI_CONTROLS_WIDTH)));
			float shownHeight = height + MINI_ROW * open;
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(windowX.get() + slide, windowY.get());
			pose.scale(s, s);
			try {
				lastRadius = height / 2f;
				if (panelBackground.get()) {
					HudStyle.panel(ui, 0, 0, shownWidth, Math.round(shownHeight), height / 2f, HudStyle.opacity() * (editing ? 1f : shown), glow.get());
				}
				renderInline(ui, 0, height / 2, width, true);
				if (open > 0.004f) {
					float rowY = height + MINI_ROW / 2f - MINI_ROW_LIFT;
					float buttonsX = MINI_PAD + MINI_BUTTON_GAP;
					controls.drawButtons(ui, buttonsX, rowY, MINI_BUTTON_GAP, MINI_BUTTON_SIZE, open);
					float barLeft = buttonsX + MINI_BUTTON_GAP + MINI_BUTTON_SIZE;
					MusicService.Track track = MusicService.track();
					if (track != null && track.durationMs() > 0) {
						controls.drawBar(ui, barLeft, rowY, shownWidth - MINI_PAD - barLeft, progressShown, accentArgb(), open);
					}
					controls.drawTip(ui, 1f);
				}
			} finally {
				pose.popMatrix();
			}
		}
		Ui.setZoom(baseZoom);
		if (!preview) {
			bounds = new int[]{Math.round(windowX.get()), Math.round(windowY.get()), Math.round(width * s), Math.round(height * s)};
		}
	}

	/** @param live whether it is the player on screen, which has the controls, rather than a preview of it */
	private void drawWindowContents(Ui ui, MusicService.Track track, float visible, int width, int height, float view, boolean live) {
		int accentColor = accentArgb();
		lastRadius = 16f;
		if (panelBackground.get()) {
			HudStyle.panel(ui, 0, 0, width, height, Radius.XXL, HudStyle.opacity() * visible, glow.get());
		}

		int pad = 18;
		int coverSize = 44;
		int left = pad;
		if (showCover.get()) {
			drawCover(ui, pad, pad, coverSize, accentColor);
			left += coverSize + 12;
		}
		// while the mouse is free the controls take the place of the dancing bars, top right
		float controlsShown = live ? controls.reveal() : 0f;
		int room = width - left - pad - 30 - Math.round(WINDOW_CONTROLS_ROOM * controlsShown);
		if (track == null) {
			ui.text("Nothing is playing", left, pad + 10, Ui.withOpacity(HudStyle.text(), visible));
			ui.text(ui.fit(MusicService.status().isEmpty() ? "Start Apple Music or Cider" : MusicService.status(), room), left, pad + 24, Ui.withOpacity(HudStyle.secondary(), visible));
			return;
		}
		ui.graphics.enableScissor(left - 1, pad - 2, left + room + 1, pad + coverSize + 2);
		drawSongText(ui, track, left, pad + 8, pad + 25, room, 1.1f, HudStyle.text(), HudStyle.secondary(), visible);
		ui.graphics.disableScissor();
		if (showBars.get() && controlsShown < 0.99f) {
			drawBars(ui, width - pad - BARS_WIDTH, pad + coverSize / 2, Ui.withOpacity(accentColor, 1f - controlsShown), BAR_MAX);
		}
		if (live) {
			controls.drawButtons(ui, width - pad - WINDOW_BUTTON_GAP - WINDOW_BUTTON_SIZE / 2f, pad + coverSize / 2f, WINDOW_BUTTON_GAP, WINDOW_BUTTON_SIZE, visible);
		}
		// elapsed time, progress bar, remaining time
		int progressY = pad + coverSize + 12;
		if (showProgress.get()) {
			String gone = clock(live && controls.scrubbing() ? controls.scrubMs() : MusicService.position());
			String left2 = track.durationMs() > 0 ? "-" + clock(track.durationMs() - MusicService.position()) : "";
			int barLeft = pad + ui.width("0:00") + 8;
			int barRight = width - pad - ui.width("-0:00") - 8;
			ui.text(gone, pad, progressY - 4, Ui.withOpacity(HudStyle.secondary(), visible));
			if (!left2.isEmpty()) {
				ui.textRight(left2, width - pad, progressY + 0, Ui.withOpacity(HudStyle.secondary(), visible));
			}
			if (track.durationMs() > 0) {
				int trackWidth = barRight - barLeft;
				if (live) {
					controls.drawBar(ui, barLeft, progressY + 0.5f, trackWidth, progressShown, accentColor, visible);
				} else {
					ui.rounded(barLeft, progressY - 1, trackWidth, 3, Radius.pill(3), Ui.withOpacity(HudStyle.text(), 0.16f * visible));
					ui.rounded(barLeft, progressY - 1, Math.max(3, Math.round(trackWidth * progressShown)), 3, Radius.pill(3), Ui.withOpacity(accentColor, visible));
					ui.circle(barLeft + Math.round(trackWidth * progressShown), progressY, 3, Ui.withOpacity(0xFFFFFFFF, visible));
				}
			}
		}
		if (view > 0.02f) {
			// lines are laid out for the full view size and revealed through the visible part, so they are not squeezed while it opens
			drawLyricsView(ui, pad, progressY + 14, FULL_WIDTH - 2 * pad, FULL_HEIGHT - progressY - 14 - 10,
				Math.min(FULL_WIDTH - 2 * pad, width - 2 * pad), Math.max(0, height - progressY - 14 - 10), visible * view);
		}
		if (live) {
			controls.drawTip(ui, visible);
		}
	}

	/** Draws a few lyric lines that scroll as the song progresses: the current line large with the word box, the others fainter. */
	private void drawLyricsView(Ui ui, int x, int top, int room, int height, int shownRoom, int shownHeight, float visible) {
		List<MusicService.LyricLine> lines = MusicService.lyrics();
		if (lines.isEmpty()) {
			return;
		}
		if (lines != scrollLyrics) {
			// different lyrics: start at their current line instead of scrolling through all lines in between
			scrollLyrics = lines;
			scrollLine = -1f;
		}
		long at = lyricAt;
		int current = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).timeMs() <= at) {
				current = i;
			} else {
				break;
			}
		}
		if (scrollLine < 0f) {
			scrollLine = current;
		}
		scrollLine += (current - scrollLine) * (1f - (float) Math.exp(-frameDt * 9f));
		if (Math.abs(current - scrollLine) < 0.003f) {
			scrollLine = current;
		}
		float lineHeight = LYRIC_LINE_HEIGHT;
		float middle = top + height / 2f;
		// slightly wider than the lines so the box on the first and last word is not clipped
		ui.graphics.enableScissor(x - 10, top, x + shownRoom + 10, top + shownHeight);
		// only the sung line plus one before and one after, as a small preview
		int first = Math.max(0, (int) Math.floor(scrollLine) - 1);
		int last = Math.min(lines.size() - 1, (int) Math.ceil(scrollLine) + 1);
		for (int k = first; k <= last; k++) {
			String lineText = lines.get(k).text();
			if (lineText.isBlank()) {
				continue;
			}
			float d = k - scrollLine;
			float near = Math.max(0f, 1f - Math.abs(d));
			float alpha = (0.40f + 0.60f * near) *Mth.clamp(1.6f - Math.abs(d) * 0.6f, 0f, 1f) * visible;
			// the sung line is largest, its neighbors smaller; a line that is too long scrolls instead of shrinking
			float scale = 0.76f + 0.58f * near;
			float width = ui.lyricWidthScaled(lineText, scale);
			float y = middle + d * lineHeight - 5f * scale;
			if (k != current || !playerLyric) {
				// lines near the edge fade into the surface instead of being cut off by the view edge
				float lineMiddle = y + 4.5f * scale;
				alpha *= Mth.clamp(Math.min(lineMiddle - top, top + shownHeight - lineMiddle) / 8f, 0f, 1f);
			}
			if (k == current && playerLyric) {
				float shift = 0f;
				// the box on the last word extends past the text, so a line that barely fits has to scroll too
				if (width + BOX_MARGIN > room) {
					// the box is stored at the line's default size and scaled to the line here
					float boxL = boxLeft * scale;
					float boxR = boxRight * scale;
					float wanted = -Mth.clamp((boxL + boxR) / 2f - room * 0.5f, 0f, width + BOX_MARGIN - room);
					viewShift += (wanted - viewShift) * (1f - (float) Math.exp(-frameDt * 7f));
					// never let the line hide the box, so it is fully visible at the first and last word, corners included
					shift = Math.max(Math.min(viewShift, room - 1f - boxR), -boxL);
				} else {
					viewShift = 0f;
				}
				// the sung line gets extra room for its box, which extends past the text, and the box corners, so nothing is clipped
				ui.graphics.disableScissor();
				int margin = Math.round(10f * Mth.clamp(shownHeight / (float) Math.max(1, height), 0f, 1f));
				ui.graphics.enableScissor(x - margin, top - margin, x + shownRoom + margin, top + shownHeight + margin);
				drawLyric(ui, x + shift, y, alpha, HudStyle.text(), scale);
				// the background vocal goes on the same row if it fits
				float backingScale = scale * BACKGROUND_SCALE;
				float backingRoom = backgroundShown > 0.01f ? backgroundRoom(ui, backingScale) / backgroundShown : 0f;
				if (backingRoom > 0f && width + backingRoom <= room) {
					drawBackground(ui, x + shift + width + ADLIB_BESIDE * scale, y + 0.5f * scale, alpha, HudStyle.text(), backingScale);
				}
				ui.graphics.disableScissor();
				ui.graphics.enableScissor(x - 10, top, x + shownRoom + 10, top + shownHeight);
			} else {
				if (width > room) {
					// a neighbor line too long for the view is made smaller to fit, never cut
					float fitted = scale * room / width;
					y += 5f * (scale - fitted);
					scale = fitted;
				}
					int color = near > 0.5f ? HudStyle.text() : HudStyle.secondary();
					// neighbor lines are smaller and fainter, drawn once so the glyphs stay sharp
					if (near < 0.5f) {
						// neighbor lines are blurred, more the further they are from the sung line
						ui.blurredLyricAtScaled(lineText, x, y, scale, Ui.withOpacity(color, alpha * 1.8f), 0.7f + 1.1f * (1f - near));
					} else {
						ui.lyricAtScaled(lineText, x, y, scale, Ui.withOpacity(color, alpha));
					}
			}
		}
		ui.graphics.disableScissor();
		// lines fade out at the top and bottom of the view into the surface
		int fade = 18;
		int surface = Ui.withOpacity(0xFF0B0C12, 0.0f);
		if (surface == 0) {
			return;
		}
	}

	static String clock(long millis) {
		long seconds = Math.max(0, millis / 1000);
		return seconds / 60 + (seconds % 60 < 10 ? ":0" : ":") + seconds % 60;
	}
	// ------------------------------------------------------------------------------------------------ HUD editor

	@Override
	public String hudName() {
		return "Now playing";
	}

	@Override
	public boolean hudShown() {
		return isEnabled() && display.get() != Display.INFO_BAR;
	}

	@Override
	public int[] hudBounds() {
		return bounds;
	}

	@Override
	public float hudRadius() {
		return lastRadius * shownScale.value();
	}

	@Override
	public void hudMove(int dx, int dy) {
		windowX.set(Mth.clamp(windowX.get() + dx, windowX.min(), windowX.max()));
		windowY.set(Mth.clamp(windowY.get() + dy, windowY.min(), windowY.max()));
	}

	@Override
	public float hudScale() {
		return windowScale.get();
	}

	@Override
	public void hudSetScale(float value) {
		windowScale.set(Mth.clamp(value, windowScale.min(), windowScale.max()));
	}

	@Override
	public void hudReset() {
		windowX.reset();
		windowY.reset();
		windowScale.reset();
	}

	/** Draws the world lyrics; called after the level is rendered. */
	public void collectWorld(LevelRenderContext context) {
		inWorld.collectWorld(context);
	}

	/** @return the source the page shows: with the automatic choice, the one that plays right now, if any */
	private Player shownSource() {
		Player chosen = player.get();
		if (chosen == Player.AUTO) {
			for (Player source : Player.values()) {
				if (source != Player.AUTO && MusicService.playsFrom(source.source)) {
					return source;
				}
			}
		}
		return chosen;
	}

	/** @return where the song shows, for its row on the music page: with the player's size for the small player and the window */
	private String displayValue() {
		String where = display.get().displayName().getString();
		return display.get() == Display.INFO_BAR ? where : where + " \u00B7 " + playerSize.get().displayName().getString();
	}

	/** @return where the lyrics show, for their row on the music page */
	private String lyricsValue() {
		if (!lyrics.get()) {
			return "Off";
		}
		StringBuilder places = new StringBuilder();
		if (barLyrics.get()) {
			places.append("Info bar");
		}
		if (worldLyrics.get()) {
			places.append(places.isEmpty() ? "" : ", ").append("In the world");
		}
		return places.isEmpty() ? "Nowhere" : places.toString();
	}

	/** @return the source and its state, for its row on the music page */
	private String sourceValue() {
		return sourceName() + " \u00B7 " + sourceState().text();
	}

	/** @return the color of the source's state, for the dot before it, or -1 for none */
	private int sourceDot() {
		return sourceState().rgb();
	}

	private EnumSetting.OptionState sourceState() {
		return ciderDown() ? CIDER_DOWN : sourceCard(shownSource()).state().get();
	}

	/** @return the name of the source the page shows */
	String sourceName() {
		return shownSource().displayName().getString();
	}

	/** @return the first music key that is set, with what it does, for the row of the keys; "None set" while no key is */
	private String keysValue() {
		for (Setting<?> setting : keys()) {
			if (setting instanceof KeySetting key && !key.mapping().isUnbound()) {
				return key.labelText() + " " + key.mapping().getTranslatedKeyMessage().getString();
			}
		}
		return "None set";
	}

	/** @return the music keys, registered during initialization */
	private List<Setting<?>> keys() {
		List<Setting<?>> keys = new ArrayList<>();
		for (Setting<?> setting : settings()) {
			if (setting instanceof KeySetting) {
				keys.add(setting);
			}
		}
		return keys;
	}

	/** @return the sub-page of where the song shows: the three places as pictures, and the player's size below for the two players */
	private SettingsView whereView() {
		if (whereView == null) {
			// the info bar's song width shows here too while the song is in the info bar
			EnumSetting<?> songWidth = ModFeatures.INFO_BAR.songSizeSetting();
			songWidth.visibleWhen(() -> display.get() == Display.INFO_BAR);
			whereView = new SettingsView("music_where", "Where the song shows", "", "note", SystemColors.PINK_RGB,
				List.of(new SettingsView.Group(UNTITLED, List.of(display, songWidth, playerSize))));
		}
		return whereView;
	}

	/**
	 * @return the sub-page of the lyrics: where they show, and a group for each place that is ticked (the info bar and the players
	 *         share one), then the adlibs and the main menu
	 */
	private SettingsView lyricsView() {
		if (lyricsView == null) {
			lyricsView = new SettingsView("music_lyrics", "Lyrics", "", "lyrics", SystemColors.PURPLE_RGB, List.of(
				new SettingsView.Group(UNTITLED, List.of(lyricPlaces)),
				new SettingsView.Group(GROUP_SCREEN, List.of(highlightBox, lyricsColor, lyricsCustom)),
				new SettingsView.Group(GROUP_WORLD, List.of(worldPlacement, worldPlace, worldReach, worldDistance, worldEffect, worldGlowColor, worldGlowCustom)),
				new SettingsView.Group(UNTITLED_2, List.of(adlibs))));
		}
		return lyricsView;
	}

	/** @return the sub-page of the keys */
	private SettingsView keysView() {
		if (keysView == null) {
			keysView = new SettingsView("music_keys", "Keys", "", "keyboard", SystemColors.GRAY_RGB, List.of(new SettingsView.Group(UNTITLED, keys())));
		}
		return keysView;
	}

	/** @return the sub-page with the settings the music page keeps out of sight; made when first needed */
	private SettingsView moreView() {
		if (moreView == null) {
			moreView = new SettingsView("music_more", "More", "", "sliders", SystemColors.GRAY_RGB, List.of(
				new SettingsView.Group("Player", List.of(windowScale, textWidth, show, accent, panelBackground, glow)),
				new SettingsView.Group("Lyrics", List.of(lyrics, bracketAdlibs, safeLyrics, timingMode, lyricTiming)),
				new SettingsView.Group("Lyrics in the world", List.of(worldTextColor, worldTextCustom, worldFont, worldSize, worldLinger)),
				new SettingsView.Group("Cover", List.of(onlineCovers))));
		}
		return moreView;
	}

	/** @return whether Cider is the chosen source and did not answer the last time */
	private boolean ciderDown() {
		return player.get() == Player.CIDER && !MusicService.ciderRunning();
	}

	/** @return a row that opens the guided setup of a source, with its state */
	private ActionRowSetting setupRow(Player source) {
		EnumSetting.OptionCard card = sourceCard(source);
		return new ActionRowSetting("setup_" + source.name().toLowerCase(Locale.ROOT), source.displayName(), new ActionRowSetting.Look(card::icon,
			card::rgb, () -> source.displayName().getString(), null, () -> card.state().get(), null, () -> "Set up"), () -> {
			var client = Minecraft.getInstance();
			Screen from = client.gui.screen();
			client.setScreenAndShow(source == Player.SPOTIFY ? SourceSetupScreen.spotify(from) : SourceSetupScreen.cider(from));
		});
	}

	/** @return the sub-page of the source of the music: the players, their setup, and Retry while Cider does not answer */
	private SettingsView sourceView() {
		if (sourceView == null) {
			sourceView = new SettingsView("music_source", "Source", "", "link", SystemColors.BLUE_RGB,
				List.of(new SettingsView.Group(UNTITLED, List.of(player, ciderRetry)),
					new SettingsView.Group("Setup", List.of(setupRow(Player.SPOTIFY), setupRow(Player.CIDER), spotifyOff))));
		}
		return sourceView;
	}

	/** The music page: the song that plays and the rows that open its sub-pages. */
	@Override
	public List<Setting<?>> pageSettings() {
		return List.of(nowPlaying, overview);
	}

	/** Registers the music keys and shows them in the Keys group of the settings. Called during mod initialization. */
	public void registerKeys() {
		for (KeyMapping key : MusicKeys.register()) {
			addKey(key, KeySetting.Where.WORLD);
		}
	}
}
