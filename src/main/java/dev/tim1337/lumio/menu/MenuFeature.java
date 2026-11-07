package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.hud.PanelLook;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.FeatureSwitchSetting;
import dev.tim1337.lumio.config.PageOutline;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.InfoSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.features.misc.BorderlessFeature;
import dev.tim1337.lumio.ui.ChangelogScreen;
import dev.tim1337.lumio.ui.CreditsScreen;
import dev.tim1337.lumio.ui.Glass;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.config.setting.ColoredColorsSetting;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.DerivedFloatSetting;
import dev.tim1337.lumio.config.setting.GlowColorsSetting;
import dev.tim1337.lumio.config.setting.LinksRowSetting;
import dev.tim1337.lumio.config.setting.PairSetting;
import dev.tim1337.lumio.config.setting.SmallSwitchSetting;
import dev.tim1337.lumio.render.ColoredFlow;
import dev.tim1337.lumio.render.GlassPanes;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.ui.FeaturePreview;
import dev.tim1337.lumio.ui.PanelTheme;
import dev.tim1337.lumio.ui.ColoredCustomizeScreen;
import dev.tim1337.lumio.ui.ColoredMood;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Swatch;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.controls.SwitchControl;
import dev.tim1337.lumio.ui.setup.SetupScreen;
import dev.tim1337.lumio.util.ModVersion;


import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settings of the menu itself (its appearance). Not a visual feature of the game, but persisted like the others and shown on a page like them.
 */
public class MenuFeature extends Feature {
	/** Accent colors for selected and enabled elements, modeled on the macOS accent colors. */
	public enum Accent implements Swatch {
		BLUE("Blue", SystemColors.BLUE_RGB),
		PURPLE("Purple", SystemColors.PURPLE_RGB),
		PINK("Pink", SystemColors.PINK_RGB),
		RED("Red", SystemColors.RED_RGB),
		ORANGE("Orange", SystemColors.ORANGE_RGB),
		YELLOW("Yellow", SystemColors.YELLOW_RGB),
		GREEN("Green", SystemColors.GREEN_RGB),
		GRAPHITE("Graphite", SystemColors.GRAY_RGB),
		/** The color picked in the settings. */
		CUSTOM("Custom", 0xFFFFFF);

		private final Component displayName;
		private final int rgb;

		Accent(String displayName, int rgb) {
			this.displayName = Component.literal(displayName);
			this.rgb = rgb;
		}

		public Component displayName() {
			return displayName;
		}

		@Override
		public int rgb() {
			return this == CUSTOM ? ModFeatures.MENU.customAccent.get() & 0xFFFFFF : rgb;
		}

		@Override
		public boolean picker() {
			return this == CUSTOM;
		}
	}

	private static final int GLOW_COLORS = 3;
	private static final int[] GLOW_DEFAULTS = {SystemColors.BLUE_RGB, SystemColors.PURPLE_RGB, SystemColors.PINK_RGB, SystemColors.TEAL_RGB};
	// the groups of the page; the last has no title and only keeps the links at the bottom apart
	private static final String APPEARANCE = "Appearance";
	private static final String MATERIAL = "Material";
	private static final String TEXT_AND_SIZE = "Text and size";
	private static final String MOTION_AND_SOUND = "Motion and sound";
	private static final String LINKS = " ";
	// the sheet and the page opened from those links
	private static final String ABOUT = "About Lumio";
	private static final String GLASS = "Glass";
	private static final String GLOW = "Glow";
	private static final String SOUND = "Sound";
	private static final String CUSTOMIZE = "Customize";
	/** What a row says that has no effect because nothing is made of glass now. */
	private static final String ONLY_FOR_GLASS = "Only for glass";
	private static final String GLOW_OFF = "Glow is off";
	private static final String NO_EFFECT_HERE = "No effect here";
	/** How long the answer to whether anything is made of glass is kept: finding it out goes through every HUD element. */
	private static final long GLASS_CHECK_MILLIS = 1000L;
	/** The density a config's "Reduce transparency" stands for, which the slider now sets: nearly solid. */
	private static final float REDUCED_TRANSPARENCY = 0.9f;
	/** The range of the window size; the screen can narrow it further. */
	private static final float MIN_WINDOW_SIZE = 0.7f;
	private static final float MAX_WINDOW_SIZE = 1.3f;

	private final ColorSetting glassTint = add(new ColorSetting("glass_tint", Component.literal("Tint"), 0x0B0E1A).withOpacity(0.48f), GLASS);
	private final FloatSetting glassBlur = add(new FloatSetting("glass_blur", Component.literal("Blur"), 0.55f, 0f, 1f, 2).percent(), GLASS);

	private final EnumSetting<MacTheme> theme = add(new EnumSetting<>("theme", Component.literal("Theme"), MacTheme.DARK, MacTheme::displayName, EnumSetting.Style.RADIO)
		.withOptionCards(MenuFeature::themeCard).optionCardsInGroup(), APPEARANCE);
	private final EnumSetting<Accent> accent = add(new EnumSetting<>("accent", Component.literal("Accent color"), Accent.BLUE, Accent::displayName, EnumSetting.Style.SWATCHES), APPEARANCE);
	private final ColorSetting customAccent = add(new ColorSetting("custom_accent", Component.literal("Custom color"), SystemColors.BLUE_RGB), APPEARANCE);
	private final ColoredColorsSetting coloredColors = add(new ColoredColorsSetting("colored_colors", Component.literal("Colors")), APPEARANCE);
	private final BooleanSetting coloredFollowSong = add(new BooleanSetting("colored_follow_song", Component.literal("Follow the song"), false), APPEARANCE);
	/** The colors and, small beside them, whether they follow the song: one row, so that the page stays in view. */
	private final PairSetting coloredRow = add(new PairSetting("colored_row", Component.literal("Colors"), new SmallSwitchSetting(coloredFollowSong), coloredColors) {
		@Override
		public String description() {
			return theme.get() == MacTheme.COLORED ? "Accent follows color 1" : null;
		}
	}, APPEARANCE);
	/** The row that sums up the Customize sheet and opens it. */
	private final ButtonSetting coloredCustomize = add(new ButtonSetting("colored_customize", Component.literal("Look and motion"), Component.literal("Customize…"), false, () -> {
		var client = Minecraft.getInstance();
		client.setScreenAndShow(new ColoredCustomizeScreen(client.gui.screen()));
	}) {
		@Override
		public String description() {
			return coloredSummary();
		}
	}, APPEARANCE);
	// the Customize sheet: saved as settings and shared with configs like the others, shown only there
	private final BooleanSetting coloredInGame = add(new BooleanSetting("colored_in_game", Component.literal("Also in the game"), true), CUSTOMIZE);
	private final BooleanSetting coloredWholeScreen = add(new BooleanSetting("colored_whole_screen", Component.literal("Color the whole screen"), false), CUSTOMIZE);
	private final FloatSetting coloredSpeed = add(new FloatSetting("colored_speed", Component.literal("Speed"), ColoredMood.Preset.FLOW.speed(), 0f, 1f, 2).percent(), CUSTOMIZE);
	private final FloatSetting coloredEdges = add(new FloatSetting("colored_edges", Component.literal("Edges"), ColoredMood.Preset.FLOW.edges(), 0f, 1f, 2).percent(), CUSTOMIZE);
	private final EnumSetting<ColoredMood.Movement> coloredMovement = add(new EnumSetting<>("colored_movement", Component.literal("Movement"), ColoredMood.Preset.FLOW.movement(),
		ColoredMood.Movement::displayName, EnumSetting.Style.SEGMENTED), CUSTOMIZE);
	private final FloatSetting coloredFieldSize = add(new FloatSetting("colored_field_size", Component.literal("Field size"), ColoredMood.Preset.FLOW.fieldSize(), 0f, 1f, 2).percent(), CUSTOMIZE);
	private final FloatSetting coloredIntensity = add(new FloatSetting("colored_intensity", Component.literal("Intensity"), ColoredMood.Preset.FLOW.intensity(), 0f, 1f, 2).percent(), CUSTOMIZE);
	private final BooleanSetting coloredGrain = add(new BooleanSetting("colored_grain", Component.literal("Fine grain"), true), CUSTOMIZE);
	private final BooleanSetting coloredBreathe = add(new BooleanSetting("colored_breathe", Component.literal("Breathe with the music"), false), CUSTOMIZE);
	/** How much the glass may cost: Auto picks a level from what the glass costs here; lower levels blur at a smaller size and leave out the bent and colored edge. */
	public enum GlassQuality {
		AUTO("Auto"),
		LOW("Low"),
		MEDIUM("Medium"),
		HIGH("High");

		private final Component displayName;

		GlassQuality(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The theme of the HUD: the menu's theme, or one of its own. */
	public enum HudTheme {
		MENU("Like the menu", null),
		GLASS("Glass", MacTheme.GLASS),
		DARK("Dark", MacTheme.DARK),
		LIGHT("Light", MacTheme.LIGHT),
		COLORED("Colored", MacTheme.COLORED);

		private final Component displayName;
		private final MacTheme theme;

		HudTheme(String displayName, MacTheme theme) {
			this.displayName = Component.literal(displayName);
			this.theme = theme;
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** How far animations go: Reduced is the same movement without any overshoot. */
	public enum Motion {
		FULL("Full"),
		REDUCED("Reduced"),
		OFF("Off");

		private final Component displayName;

		Motion(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** One slider for how dense the glass is and how blurred what is behind it; each is saved as it was, and the blur can still be set by itself. */
	private final FloatSetting transparency = add(new DerivedFloatSetting("transparency", Component.literal("Transparency"), 0f, 1f, 2,
		() -> 1f - glassTint.opacity(), this::setTransparency) {
		@Override
		public String disabledNote() {
			return theme.get() == MacTheme.COLORED ? "In Colored: Customize › Intensity" : ONLY_FOR_GLASS;
		}
	}.percent(), MATERIAL, "How much of the game shows through");

	private final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Glow"), true), GLOW);
	private final EnumSetting<UiGlow.Mode> glowMode = add(new EnumSetting<>("glow_mode", Component.literal("Style"), UiGlow.Mode.AURORA, UiGlow.Mode::displayName, EnumSetting.Style.DROPDOWN), GLOW);
	private final FloatSetting glowStrength = add(new FloatSetting("glow_strength", Component.literal("Strength"), 0.65f, 0.05f, 1f, 2).percent(), GLOW);
	/** The glow's strength with its switch: at 0 it is off, and the strength it had is kept for when it comes back. */
	private final FloatSetting glowAmount = add(new DerivedFloatSetting("glow_amount", Component.literal("Glow strength"), 0f, 1f, 2,
		() -> glow.get() ? glowStrength.get() : 0f, this::setGlowAmount).percent());
	private final PairSetting glowRow = add(new PairSetting("glow_row", Component.literal("Glow"), glowMode, glowAmount), MATERIAL);
	private final FloatSetting glowSize = add(new FloatSetting("glow_size", Component.literal("Size"), 12f, 3f, 32f, 0), GLOW);
	private final FloatSetting glowSpeed = add(new FloatSetting("glow_speed", Component.literal("Speed"), 1f, 0.1f, 3f, 1), GLOW);
	private final FloatSetting glowBlend = add(new FloatSetting("glow_blend", Component.literal("Blend"), 0.5f, 0f, 1f, 2), GLOW);
	private final FloatSetting glowCount = add(new FloatSetting("glow_count", Component.literal("Colors"), 3f, 1f, GLOW_COLORS, 0), GLOW);
	private final ColorSetting[] glowColors = addGlowColors();
	private final GlowColorsSetting glowColorsRow = add(new GlowColorsSetting("glow_colors", Component.literal("Glow colors"), glowCount, glowColors), GLOW,
		"They also color the falling stars and the block overlay");
	/** Saved under its old key, so that configs keep it. */
	private final BooleanSetting backgroundGlow = add(new BooleanSetting("colored_background", Component.literal("Background glow"), true), GLOW,
		"Soft glows behind the settings, in the accent and glow color 2");

	private final FloatSetting glassEffect = add(new FloatSetting("glass_effect", Component.literal("Edge light"), 0.8f, 0f, 1f, 2).percent(), GLASS);
	private final FloatSetting glassSaturation = add(new FloatSetting("glass_saturation", Component.literal("Saturation"), GlassPanes.DEFAULT_SATURATION, 0f, 1f, 2).percent(), GLASS);
	private final BooleanSetting shaderGlass = add(new BooleanSetting("shader_glass", Component.literal("Real glass"), true), GLASS, "Off: a cheaper tinted pane");
	private final EnumSetting<GlassQuality> glassQuality = add(new EnumSetting<>("glass_quality", Component.literal("Glass quality"), GlassQuality.AUTO,
		GlassQuality::displayName, EnumSetting.Style.SEGMENTED), GLASS);
	private final BooleanSetting frostedGlass = add(new BooleanSetting("frosted_glass", Component.literal("Blur background"), false), GLASS, "Blurs the game behind the menu");
	// kept in configs and still in effect, without a row until the glass is reworked
	private final FloatSetting glassRefraction = add(new FloatSetting("glass_refraction", Component.literal("Refraction"), 0.6f, 0f, 1f, 2), GLASS);
	private final FloatSetting glassDispersion = add(new FloatSetting("glass_dispersion", Component.literal("Dispersion"), 0.35f, 0f, 1f, 2), GLASS);

	private final EnumSetting<HudTheme> hudTheme = add(new EnumSetting<>("hud_theme", Component.literal("Theme"), HudTheme.MENU, HudTheme::displayName, EnumSetting.Style.SEGMENTED));
	/** The depth effect of the HUD panels (shadow, lit edge, gloss); each element can also have it on or off by itself. */
	private final BooleanSetting hudDepth = add(new BooleanSetting("hud_depth", Component.literal("Depth effect"), true));

	// the global HUD theme: what a HUD panel looks like while it is set to the theme (see PanelLook); the theme and the depth effect are the two above
	private static final String HUD_THEME = "HUD theme";
	private final ColorSetting hudAccent = add(new ColorSetting("hud_accent", Component.literal("Accent"), SystemColors.BLUE_RGB).menuByDefault(), HUD_THEME);
	private final BooleanSetting hudBackground = add(new BooleanSetting("hud_background", Component.literal("Background"), true), HUD_THEME);
	private final FloatSetting hudSeeThrough = add(new FloatSetting("hud_see_through", Component.literal("See-through"), 0f, 0f, 0.8f, 2).percent().step(0.05f), HUD_THEME);
	private final BooleanSetting hudGlowOn = add(new BooleanSetting("hud_glow", Component.literal("Glow"), true), HUD_THEME);
	private final BooleanSetting hudGlowSoft = add(new BooleanSetting("hud_glow_soft", Component.literal("Soft glow"), true), HUD_THEME);
	private final EnumSetting<PanelLook.Glow> hudGlowRow = add(PanelLook.glowRow("hud_glow_row", hudGlowOn, hudGlowSoft), HUD_THEME);
	private final FloatSetting hudSize = add(new FloatSetting("hud_size", Component.literal("Size"), 1f, 0.3f, 3f, 2).percent(), HUD_THEME);
	private final EnumSetting<HudPanelFeature.WidthMode> hudWidthMode = add(new EnumSetting<>("hud_width_mode", Component.literal("Width"), HudPanelFeature.WidthMode.AUTO,
		HudPanelFeature.WidthMode::displayName, EnumSetting.Style.SEGMENTED), HUD_THEME);
	private final FloatSetting hudWidth = add(new FloatSetting("hud_width", Component.literal("Custom width"), 1f, 0.75f, 1.8f, 2).percent(), HUD_THEME);
	private final FloatSetting hudLineSpacing = add(new FloatSetting("hud_line_spacing", Component.literal("Line spacing"), 1f, 0.8f, 1.8f, 2), HUD_THEME);
	private final EnumSetting<PanelLook.Spacing> hudSpacingRow = add(PanelLook.spacingRow("hud_line_spacing_row", hudLineSpacing, 0.8f, 1.8f), HUD_THEME);
	private final BooleanSetting hudTitle = add(new BooleanSetting("hud_title", Component.literal("Title"), true), HUD_THEME);
	private final BooleanSetting hudStatus = add(new BooleanSetting("hud_status", Component.literal("Status"), true), HUD_THEME);

	private final EnumSetting<UiFont> uiFont = add(new EnumSetting<>("ui_font", Component.literal("Font"), UiFont.MOD, UiFont::displayName, EnumSetting.Style.SEGMENTED), TEXT_AND_SIZE);
	// the size of the menu window, as a share of its default size; also changed with Ctrl + wheel, Ctrl + minus, plus and 0, and the window buttons
	private final FloatSetting windowSize = add(new FloatSetting("menu_window_size", Component.literal("Window size"), 1f, MIN_WINDOW_SIZE, MAX_WINDOW_SIZE, 2).percent().step(MacScreen.SIZE_STEP)
		.lowerLimit(MacScreen::smallestWindowSize).upperLimit(MacScreen::largestWindowSize).doubleClickResets(), TEXT_AND_SIZE, "Ctrl + mouse wheel works too · double-click resets");
	// where the menu window is, as a share of the screen (its center); moved by dragging it, not shown on any page, and not shared with configs
	private final FloatSetting windowX = add(new FloatSetting("menu_window_x", Component.literal("Window across"), 0.5f, 0f, 1f, 3));
	private final FloatSetting windowY = add(new FloatSetting("menu_window_y", Component.literal("Window down"), 0.5f, 0f, 1f, 3));
	// whether the settings window was pulled off the menu window, and where it floats then (its center, as a share of the screen); like the
	// window's place, not shown on any page and not shared
	private final BooleanSetting paneFloating = add(new BooleanSetting("menu_pane_floating", Component.literal("Settings window floats"), false));
	private final FloatSetting paneX = add(new FloatSetting("menu_pane_x", Component.literal("Settings window across"), 0.75f, 0f, 1f, 3));
	private final FloatSetting paneY = add(new FloatSetting("menu_pane_y", Component.literal("Settings window down"), 0.5f, 0f, 1f, 3));

	private final BooleanSetting animations = add(new BooleanSetting("animations", Component.literal("Animations"), true));
	private final BooleanSetting animationsReduced = add(new BooleanSetting("animations_reduced", Component.literal("Reduced animations"), false));
	// kept in configs: it is what Full overshoots by
	private final FloatSetting bounce = add(new FloatSetting("bounce", Component.literal("Bounce"), 0.5f, 0f, 1f, 2));
	private final EnumSetting<Motion> motion = add(new DerivedEnumSetting<>("motion", Component.literal("Animations"), Motion.FULL, Motion::displayName, EnumSetting.Style.SEGMENTED,
		this::motionNow, this::setMotion), MOTION_AND_SOUND);
	private final BooleanSetting sounds = add(new BooleanSetting("ui_sounds", Component.literal("Menu sounds"), true), MOTION_AND_SOUND);
	private final FloatSetting soundVolume = add(new FloatSetting("ui_sound_volume", Component.literal("Menu sound volume"), 0.5f, 0f, 1f, 2).percent(), SOUND);

	private final LinksRowSetting pageLinks;

	public MenuFeature() {
		super("menu", "Menu settings", "How Lumio looks.", true, false);
		theme.warnOn(value -> value == MacTheme.LIGHT, LIGHT_TITLE, LIGHT_TEXT);
		// the rows of the Colored theme also while only the HUD has it: its colors have no other place
		for (Setting<?> coloredSetting : new Setting<?>[]{coloredRow, coloredCustomize}) {
			coloredSetting.visibleWhen(this::coloredUsed);
		}
		accent.visibleWhen(() -> theme.get() != MacTheme.COLORED);
		customAccent.under(accent, () -> theme.get() != MacTheme.COLORED && accent.get() == Accent.CUSTOM);
		iconRgb(SystemColors.GRAY_RGB);
		iconName("gear");
		// what has no row on any page: kept in configs, not offered by the search either
		for (Setting<?> hidden : new Setting<?>[]{coloredColors, coloredFollowSong, glow, glowStrength, glowAmount, glowCount, glassTint, glassRefraction, glassDispersion, coloredInGame, coloredWholeScreen,
			windowX, windowY, paneFloating, paneX, paneY, animations, animationsReduced, bounce}) {
			hidden.visibleWhen(() -> false);
		}
		for (ColorSetting color : glowColors) {
			color.visibleWhen(() -> false);
		}
		// the quality and the blur are those of the real glass; the plain glass has no blur to make cheaper, and asks the game to blur instead
		glassQuality.visibleWhen(shaderGlass::get);
		glassBlur.visibleWhen(shaderGlass::get);
		glassSaturation.visibleWhen(shaderGlass::get);
		frostedGlass.visibleWhen(() -> !shaderGlass.get());
		// a row that can do nothing right now says so, and stays where it is
		transparency.disabledWhen(this::nothingInGlass, ONLY_FOR_GLASS);
		glassBlur.disabledWhen(this::nothingInGlass, ONLY_FOR_GLASS);
		glassSaturation.disabledWhen(this::nothingInGlass, ONLY_FOR_GLASS);
		glassEffect.disabledWhen(() -> nothingInGlass() && !coloredUsed(), ONLY_FOR_GLASS);
		glowSize.disabledWhen(() -> !glow.get(), GLOW_OFF);
		glowBlend.disabledWhen(() -> !glow.get(), GLOW_OFF);
		// the glow's speed is its own only where it moves by itself: on the moving colors it moves with them, unless it pulses
		glowSpeed.disabledWhen(() -> !glow.get() || onlyColored() && glowMode.get() != UiGlow.Mode.PULSE, NO_EFFECT_HERE);
		backgroundGlow.disabledWhen(() -> theme.get() == MacTheme.COLORED, "Colored uses its own colors");
		soundVolume.disabledWhen(() -> !sounds.get(), "Menu sounds are off");
		coloredInGame.disabledWhen(() -> hudTheme.get() != HudTheme.MENU, "The HUD theme decides this");
		// the window's place and size belong to this screen: not listed as recent changes
		windowX.hideFromRecent();
		windowY.hideFromRecent();
		windowSize.hideFromRecent();
		paneFloating.hideFromRecent();
		paneX.hideFromRecent();
		paneY.hideFromRecent();
		add(new InfoSetting("about_version", Component.literal("Version"), ModVersion::display), ABOUT);
		add(new ButtonSetting("changelog", Component.literal("What changed in every version"), Component.literal("Changelog"), false, () -> {
			var client = Minecraft.getInstance();
			client.setScreenAndShow(new ChangelogScreen(client.gui.screen()));
		}), ABOUT);
		add(new ButtonSetting("credits", Component.literal("Who made it, and what it uses"), Component.literal("Credits"), false, () -> {
			var client = Minecraft.getInstance();
			client.setScreenAndShow(new CreditsScreen(client.gui.screen()));
		}), ABOUT);
		add(new ButtonSetting("addons", Component.literal("What add-ons are installed, and the add-on API"), Component.literal("Add-ons"), false,
			() -> MacScreen.openSubPage(AddonsPage.view())), ABOUT);
		add(new ButtonSetting("run_setup", Component.literal("The setup: main menu, look, how you play and music"), Component.literal("Run setup again"), false, () -> {
			var client = Minecraft.getInstance();
			client.setScreenAndShow(SetupScreen.runAgain(client.gui.screen()));
		}), ABOUT);
		// the last row of About: rare and risky, out of the way
		add(new ButtonSetting("reset_all", Component.literal("All settings"), Component.literal("Reset all"), true, () -> {
			for (Feature feature : FeatureManager.features()) {
				if (feature instanceof BorderlessFeature) {
					continue; // window mode is not part of a look and stays unchanged
				}
				feature.settings().forEach(Setting::reset);
			}
			FeatureManager.save();
		}).confirm(), ABOUT, "Reset every setting of the mod. Window mode stays as it is.");
		// at the very end of About: the debug switch, and with it the Developer page
		add(new FeatureSwitchSetting("debug_switch", Component.literal("Debug"), () -> ModFeatures.DEBUG), ABOUT, "Writes extra information to the log of the game");
		ButtonSetting developer = add(new ButtonSetting("developer_page", Component.literal("Developer"), Component.literal("Open"), false, () -> MacScreen.openSubPage(developerView())), ABOUT);
		developer.visibleWhen(() -> ModFeatures.DEBUG.isEnabled());
		pageLinks = add(new LinksRowSetting("page_links", "sliders", "Advanced…", () -> MacScreen.openSubPage(advancedView()),
			"About Lumio · Reset", () -> MacScreen.openSubPage(aboutView())), LINKS);
	}

	/** A config from before the slider that says "Reduce transparency" still gets its nearly solid glass, once; the switch is not saved any more. */
	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.get("reduce_transparency") instanceof JsonPrimitive reduced && reduced.isBoolean() && reduced.getAsBoolean()) {
			glassTint.setOpacity(Math.max(glassTint.opacity(), REDUCED_TRANSPARENCY));
		}
	}

	/** @return whether the menu and the HUD are both in the Colored theme, so that the glow has no colors of its own anywhere */
	private boolean onlyColored() {
		return theme.get() == MacTheme.COLORED && hudTheme() == MacTheme.COLORED;
	}

	/** @return whether the theme of the menu or of the HUD is Colored, so its colors matter */
	public boolean coloredUsed() {
		return theme.get() == MacTheme.COLORED || hudTheme.get() == HudTheme.COLORED;
	}

	private long glassCheckedAt;
	private boolean nothingInGlass;

	/** @return whether neither the menu nor an element of the HUD is made of glass now, so what sets the glass does nothing; worked out once in a while */
	private boolean nothingInGlass() {
		long now = System.currentTimeMillis();
		if (glassCheckedAt == 0L || now - glassCheckedAt >= GLASS_CHECK_MILLIS) {
			glassCheckedAt = now;
			nothingInGlass = !anythingInGlass();
		}
		return nothingInGlass;
	}

	private boolean anythingInGlass() {
		if (theme.get() == MacTheme.GLASS) {
			return true;
		}
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof EditableHud element && feature.isEnabled()) {
				EnumSetting<PanelTheme> own = element.hudThemeSetting();
				MacTheme shown = own == null || own.get().theme() == null ? hudTheme() : own.get().theme();
				if (shown == MacTheme.GLASS) {
					return true;
				}
			}
		}
		return false;
	}

	private void setTransparency(float amount) {
		float clear = Mth.clamp(amount, 0f, 1f);
		glassBlur.set(GlassMix.blurAfter(glassBlur.get(), 1f - glassTint.opacity(), clear));
		glassTint.setOpacity(1f - clear);
	}

	private void setGlowAmount(float amount) {
		if (amount <= 0f) {
			glow.set(false);
			return;
		}
		glow.set(true);
		glowStrength.set(Mth.clamp(amount, glowStrength.min(), glowStrength.max()));
	}

	private Motion motionNow() {
		return !animations.get() ? Motion.OFF : animationsReduced.get() ? Motion.REDUCED : Motion.FULL;
	}

	private void setMotion(Motion value) {
		animations.set(value != Motion.OFF);
		animationsReduced.set(value == Motion.REDUCED);
	}

	/** @return the short line about the Customize sheet: the preset, the movement and the mood */
	private String coloredSummary() {
		return ColoredMood.summary(coloredPreset(), coloredMovement.get(), coloredSpeed.get());
	}

	private SettingsView advancedView;
	private SettingsView aboutView;
	private List<Setting<?>> shown;

	/** The fine-tuning, the same in every theme, as a sheet over the page: the preview beside it stays live. */
	private SettingsView advancedView() {
		if (advancedView == null) {
			advancedView = new SettingsView("menu_advanced", "Advanced", "For fine-tuning. Most people never need this.", "sliders", SystemColors.GRAY_RGB, List.of(
				new SettingsView.Group(HUD_THEME, List.of(hudTheme, hudAccent, hudBackground, hudSeeThrough, hudGlowRow, hudDepth, hudSize, hudWidthMode, hudWidth, hudSpacingRow, hudTitle, hudStatus)),
				new SettingsView.Group("Window", windowRows()),
				new SettingsView.Group(GLASS, List.of(glassBlur, glassEffect, glassSaturation, shaderGlass, glassQuality, frostedGlass)),
				new SettingsView.Group(GLOW, List.of(glowSize, glowSpeed, glowBlend, glowColorsRow, backgroundGlow)),
				new SettingsView.Group(SOUND, List.of(soundVolume)))).asSheet();
		}
		return advancedView;
	}

	private final FeatureSwitchSetting keepCursor = add(new FeatureSwitchSetting("keep_cursor", Component.literal("Keep cursor in place"), () -> ModFeatures.NO_CURSOR_RESET), "Window",
		"When one menu follows another");

	private List<Setting<?>> windowRows() {
		return List.of(keepCursor, ModFeatures.BORDERLESS.settingOf("start_borderless"), ModFeatures.BORDERLESS.settingOf("borderless_now"));
	}

	private SettingsView developerView;

	/** The Developer page: what writes extra to the log, from every feature and add-on, reached from About while the debug switch at its end is on. */
	public SettingsView developerView() {
		if (developerView == null) {
			List<Setting<?>> rows = new ArrayList<>();
			for (Feature feature : FeatureManager.features()) {
				if (feature.outline() != null) {
					for (PageOutline.Row row : feature.outline().developer()) {
						Setting<?> setting = feature.settingOf(row.key());
						if (setting != null) {
							rows.add(setting);
						}
					}
				}
			}
			developerView = new SettingsView("menu_developer", "Developer", "What writes extra to the log", "terminal", SystemColors.GRAY_RGB, List.of(new SettingsView.Group("Developer", rows)));
		}
		return developerView;
	}

	/** The version and what belongs to the mod as a whole, with the reset of all settings as its last row. */
	private SettingsView aboutView() {
		if (aboutView == null) {
			List<Setting<?>> rows = new ArrayList<>();
			for (Setting<?> setting : settings()) {
				if (ABOUT.equals(setting.section())) {
					rows.add(setting);
				}
			}
			aboutView = new SettingsView("menu_about", ABOUT, "", "info", SystemColors.GRAY_RGB, List.of(new SettingsView.Group(LINKS, rows)));
		}
		return aboutView;
	}

	/** @return the sheet or sub-page a setting of this page's feature is on, for the search; null for one on the page itself */
	@Override
	public Feature viewOf(Setting<?> setting) {
		if (advancedView().settings().contains(setting)) {
			return advancedView();
		}
		if (developerView().settings().contains(setting)) {
			return developerView();
		}
		return aboutView().settings().contains(setting) ? aboutView() : null;
	}

	@Override
	public boolean compactRows() {
		return true;
	}

	/** Four groups of two to four rows, and the links at the bottom; the rest is on the sheet and the page they open. */
	@Override
	public List<Setting<?>> shownSettings() {
		if (shown == null) {
			shown = List.of(theme, accent, customAccent, coloredRow, coloredCustomize, transparency, glowRow, uiFont, windowSize, motion, sounds, pageLinks);
		}
		return shown;
	}

	/** The glow colors in their slots; the row of swatches shows as many as the glow's color count. */
	private ColorSetting[] addGlowColors() {
		ColorSetting[] colors = new ColorSetting[GLOW_COLORS];
		for (int i = 0; i < GLOW_COLORS; i++) {
			ColorSetting color = new ColorSetting("glow_color_" + (i + 1), Component.literal("Color " + (i + 1)), GLOW_DEFAULTS[i]);
			if (i == 0) {
				color.followsMenu();
			}
			colors[i] = add(color, GLOW);
		}
		return colors;
	}

	/** The HUD follows a color picked in the setup: every element that can use the menu color by default does so again. */
	public static void hudFollowsMenu() {
		for (Feature feature : FeatureManager.features()) {
			for (var setting : feature.settings()) {
				if (setting instanceof ColorSetting color && color.isMenuByDefault()) {
					color.setUsesMenu(true);
				}
			}
		}
	}

	/** @return the menu's accent color as 0xRRGGBB */
	public static int accentRgb() {
		return ModFeatures.MENU.accentRgbNow();
	}

	/** @return the menu's accent color as chosen, whatever the theme does with it */
	public static int chosenAccentRgb() {
		return ModFeatures.MENU.accent.get().rgb();
	}

	private int accentRgbNow() {
		return theme.get() == MacTheme.COLORED ? ColoredFlow.accent() : accent.get().rgb();
	}

	/** @return the colors of the Colored theme, as 0xRRGGBB; the array is reused, for reading only */
	public int[] coloredColors() {
		return coloredColors.colors();
	}

	/** @return the preset the Colored theme's values are, or null for values of the player's own */
	public ColoredMood.Preset coloredPreset() {
		return ColoredMood.presetOf(coloredSpeed.get(), coloredEdges.get(), coloredMovement.get(), coloredFieldSize.get(), coloredIntensity.get());
	}

	public void applyColoredPreset(ColoredMood.Preset preset) {
		coloredSpeed.set(preset.speed());
		coloredEdges.set(preset.edges());
		coloredMovement.set(preset.movement());
		coloredFieldSize.set(preset.fieldSize());
		coloredIntensity.set(preset.intensity());
	}

	/** Sets what the Mood pad sets. */
	public void setColoredMood(float speed, float edges) {
		coloredSpeed.set(speed);
		coloredEdges.set(edges);
	}

	public float coloredSpeed() {
		return coloredSpeed.get();
	}

	public float coloredEdges() {
		return coloredEdges.get();
	}

	public ColoredMood.Movement coloredMovement() {
		return coloredMovement.get();
	}

	public float coloredFieldSize() {
		return coloredFieldSize.get();
	}

	public float coloredIntensity() {
		return coloredIntensity.get();
	}

	public boolean coloredGrain() {
		return coloredGrain.get();
	}

	public boolean coloredBreathe() {
		return coloredBreathe.get();
	}

	public BooleanSetting coloredFollowSongSetting() {
		return coloredFollowSong;
	}

	public BooleanSetting coloredInGameSetting() {
		return coloredInGame;
	}

	public BooleanSetting coloredWholeScreenSetting() {
		return coloredWholeScreen;
	}

	public EnumSetting<HudTheme> hudThemeSetting() {
		return hudTheme;
	}

	public BooleanSetting hudDepthSetting() {
		return hudDepth;
	}

	/** @return the rows the setup's look step shows too: the theme, the accent and its own color, the colors of the Colored theme, the glow and the sounds */
	public List<Setting<?>> setupRows() {
		return List.of(theme, accent, customAccent, coloredRow, glowRow, sounds);
	}

	public EnumSetting<ColoredMood.Movement> coloredMovementSetting() {
		return coloredMovement;
	}

	public FloatSetting coloredFieldSizeSetting() {
		return coloredFieldSize;
	}

	public FloatSetting coloredIntensitySetting() {
		return coloredIntensity;
	}

	public BooleanSetting coloredGrainSetting() {
		return coloredGrain;
	}

	public BooleanSetting coloredBreatheSetting() {
		return coloredBreathe;
	}

	/** @return the setting of the colors of the Colored theme */
	public ColoredColorsSetting coloredColorsSetting() {
		return coloredColors;
	}

	/** @return whether the colors of the Colored theme fill the whole screen behind the menu, not only the menu */
	public boolean coloredWholeScreen() {
		return coloredWholeScreen.get();
	}

	/** @return whether the colors of the Colored theme come from the cover while music plays */
	public boolean coloredFollowsSong() {
		return coloredFollowSong.get();
	}

	/** @return how colorful what is behind the glass looks, from 0 (gray) to 1 */
	public float glassSaturation() {
		return glassSaturation.get();
	}

	/** @return strength of the light on the glass edge, from 0 (flat) to 1 */
	public float glassEffect() {
		return glassEffect.get();
	}

	/** @return glass density, from 0 (clear) to 1 (solid): the opacity of its color */
	public float glassOpacity() {
		return glassTint.opacity();
	}

	/** @return the glass tint as 0xRRGGBB */
	public int glassTint() {
		return glassTint.get() & 0xFFFFFF;
	}

	/** @return the glass quality that is set; Auto is resolved by the glass itself */
	public GlassQuality glassQuality() {
		return glassQuality.get();
	}

	/** @return whether the glass is rendered by the shader, with blur and refraction */
	public boolean shaderGlass() {
		// the Colored theme is made by the shader of the panes; that switch belongs to the glass theme
		return shaderGlass.get() || theme.get() == MacTheme.COLORED;
	}

	/** @return how strongly the background behind the glass is blurred, from 0 to 1 */
	public float glassBlur() {
		return glassBlur.get();
	}

	/** @return how strongly the glass edge refracts the background, from 0 to 1 */
	public float glassRefraction() {
		return glassRefraction.get();
	}

	/** @return how strongly the colors split up at the glass edge (chromatic dispersion), from 0 to 1 */
	public float glassDispersion() {
		return glassDispersion.get();
	}

	/** @return whether the world behind the plain (non-shader) glass is blurred by the game, as behind vanilla menus */
	public boolean frostedGlass() {
		return frostedGlass.get() && !shaderGlass.get();
	}

	/** The font of the menu and panels: the mod's font or the game's. */
	public enum UiFont {
		MOD("Inter"),
		GAME("Minecraft");

		private final Component displayName;

		UiFont(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** @return whether the mod's text uses the game font instead of the mod font */
	public boolean gameFont() {
		return uiFont.get() == UiFont.GAME;
	}

	public void setTheme(MacTheme value) {
		theme.set(value);
	}

	private static final String LIGHT_TITLE = "Turn on light mode?";
	private static final String LIGHT_TEXT = "Light mode is very bright, in the menu and in the panels over the game, and can be hard on the eyes in the dark. You can switch back to another design in the settings at any time.";

	public Accent accent() {
		return accent.get();
	}

	/** @return the color picked for the custom accent, which the setup edits with the menu's color picker */
	public ColorSetting customAccent() {
		return customAccent;
	}

	public void setAccent(Accent value) {
		accent.set(value);
	}

	public ColorSetting hudAccent() {
		return hudAccent;
	}

	public boolean hudBackground() {
		return hudBackground.get();
	}

	public float hudSeeThrough() {
		return hudSeeThrough.get();
	}

	public boolean hudGlow() {
		return hudGlowOn.get();
	}

	public boolean hudGlowSoft() {
		return hudGlowSoft.get();
	}

	public float hudSize() {
		return hudSize.get();
	}

	public HudPanelFeature.WidthMode hudWidthMode() {
		return hudWidthMode.get();
	}

	public float hudWidth() {
		return hudWidth.get();
	}

	public float hudLineSpacing() {
		return hudLineSpacing.get();
	}

	public boolean hudTitle() {
		return hudTitle.get();
	}

	public boolean hudStatus() {
		return hudStatus.get();
	}

	public boolean glowEnabled() {
		return glow.get();
	}

	/** @return whether HUD panels have the depth effect, unless an element chose for itself */
	public boolean hudDepth() {
		return hudDepth.get();
	}

	public MacTheme hudTheme() {
		MacTheme own = hudTheme.get().theme;
		if (own != null) {
			return own;
		}
		// the Colored theme stays in the menu when it is not wanted in the game: the HUD is glass then
		return theme.get() == MacTheme.COLORED && !coloredInGame.get() ? MacTheme.GLASS : theme.get();
	}

	public MacTheme theme() {
		return theme.get();
	}

	/** @return the accent color as ARGB */
	public int accentArgb() {
		return 0xFF000000 | accentRgbNow();
	}

	public boolean animations() {
		return animations.get();
	}

	/** @return how far animations may overshoot their target, from 0 (none) to 1 */
	public float bounce() {
		return animations.get() && !animationsReduced.get() ? bounce.get() : 0f;
	}

	// ------------------------------------------------------------------------------------------------------------------ glow

	/** @return whether the glow is on; a HUD panel also has a switch of its own */
	public boolean glowOn() {
		return glow.get();
	}

	/** @return where the menu window's center is, as a share of the screen width */
	public float windowPositionX() {
		return windowX.get();
	}

	/** @return the same as a share of the screen height */
	public float windowPositionY() {
		return windowY.get();
	}

	/** Remembers where the menu window is; saved by the caller. */
	public void setWindowPosition(float x, float y) {
		windowX.set(Mth.clamp(x, 0f, 1f));
		windowY.set(Mth.clamp(y, 0f, 1f));
	}

	/** @return whether the settings window floats on its own instead of sitting in the menu window */
	public boolean paneFloating() {
		return paneFloating.get();
	}

	/** @return where the floating settings window's center is, as a share of the screen width */
	public float panePositionX() {
		return paneX.get();
	}

	/** @return the same as a share of the screen height */
	public float panePositionY() {
		return paneY.get();
	}

	/** Puts the settings window back into the menu window and forgets where it floated; saved by the caller. */
	public void resetPane() {
		paneFloating.reset();
		paneX.reset();
		paneY.reset();
	}

	/** Remembers whether the settings window floats and where; saved by the caller. */
	public void setPane(boolean floating, float x, float y) {
		paneFloating.set(floating);
		paneX.set(Mth.clamp(x, 0f, 1f));
		paneY.set(Mth.clamp(y, 0f, 1f));
	}

	/** @return the size of the menu window as a share of its default size, within the limit the screen allows */
	public float windowSize() {
		return Mth.clamp(windowSize.get(), windowSize.min(), windowSize.max());
	}

	/** Sets the size of the menu window, on its 5 % steps and within its limits; saved by the caller. */
	public void setWindowSize(float size) {
		windowSize.set(windowSize.rounded(size));
	}

	/** @return whether the menu has soft glows behind its settings area, in the accent and the second glow color; in the Colored theme its colors take that place */
	public boolean backgroundGlow() {
		return backgroundGlow.get() && theme.get() != MacTheme.COLORED;
	}

	/** @return the second glow color as 0xRRGGBB; with one color, that one */
	public int secondGlowRgb() {
		return glowColors[colorCount() > 1 ? 1 : 0].get() & 0xFFFFFF;
	}

	public int colorCount() {
		return Math.max(1, Math.min(GLOW_COLORS, Math.round(glowCount.get())));
	}

	/** @return the glow colors as 0xRRGGBB, as many as configured */
	public int[] glowPalette() {
		int[] colors = new int[colorCount()];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = glowColors[i].get() & 0xFFFFFF;
		}
		return colors;
	}

	public UiGlow.Mode glowMode() {
		return glowMode.get();
	}

	public float glowStrength() {
		return glowStrength.get();
	}

	/** @return how far the glow extends from the edge, in menu pixels */
	public int glowSize() {
		return Math.round(glowSize.get());
	}

	/** @return how far each glow color blends into the next, as a factor: 0.25 is a short blend, 1 the default, 1.75 a long one */
	public float glowBlend() {
		return 0.25f + 1.5f * glowBlend.get();
	}

	public float glowSpeed() {
		return glowSpeed.get();
	}

	// ----------------------------------------------------------------------------------------------------------------- sounds

	public boolean soundsOn() {
		return sounds.get();
	}

	public float soundVolume() {
		return soundVolume.get();
	}

	// ----------------------------------------------------------------------------------------------------------- page

	// the small window on a theme card: an accent bar and two lines of text
	private static final int THEME_PICTURE_INSET = 8;
	private static final int THEME_BAR_HEIGHT = 5;
	private static final int THEME_LINE_HEIGHT = 4;
	private static final int THEME_LINE_GAP = 4;

	/** The card of each theme: a small window in its look, and nothing to report as a state. */
	private static final Map<MacTheme, EnumSetting.OptionCard> THEME_CARDS = Map.of(
		MacTheme.GLASS, new EnumSetting.OptionCard("drop", SystemColors.TEAL_RGB, "Translucent glass over the game, with blur and shine.", null,
			themePicture(0xFF5A64A8, 0xFF2B3058, 0x40FFFFFF, 0x59FFFFFF)),
		MacTheme.DARK, new EnumSetting.OptionCard("moon", 0x636366, "Solid dark windows and panels.", null,
			themePicture(0xFF242428, 0xFF1C1C1F, 0x1FFFFFFF, 0x38FFFFFF)),
		MacTheme.LIGHT, new EnumSetting.OptionCard("sun", SystemColors.ORANGE_RGB, "Solid light windows and panels.", null,
			themePicture(0xFFF7F7FA, 0xFFEDEDF2, 0x24000000, 0x2E000000)),
		MacTheme.COLORED, new EnumSetting.OptionCard("drop", SystemColors.PINK_RGB, "Your colors flow slowly behind the glass.", null,
			coloredPicture()));

	/** @return a picture of a theme: a small window in its colors with an accent bar and two lines of text, as in the mockups */
	private static EnumSetting.OptionPicture themePicture(int top, int bottom, int border, int lines) {
		return (ui, x, y, width, height) -> {
			ui.roundedGradient(x, y, width, height, Radius.SM, top, bottom);
			ui.ring(x, y, width, height, Radius.SM, 1, border);
			int left = x + THEME_PICTURE_INSET;
			int inner = width - 2 * THEME_PICTURE_INSET;
			int barY = y + THEME_PICTURE_INSET;
			int lineY = barY + THEME_BAR_HEIGHT + THEME_LINE_GAP;
			ui.rounded(left, barY, Math.round(inner * 0.4f), THEME_BAR_HEIGHT, Radius.pill(THEME_BAR_HEIGHT), ui.accent);
			ui.rounded(left, lineY, Math.round(inner * 0.8f), THEME_LINE_HEIGHT, Radius.pill(THEME_LINE_HEIGHT), lines);
			// a short picture, as in one row of four, has room for one line
			int secondY = lineY + THEME_LINE_HEIGHT + THEME_LINE_GAP;
			if (secondY + THEME_LINE_HEIGHT + THEME_PICTURE_INSET <= y + height) {
				ui.rounded(left, secondY, Math.round(inner * 0.6f), THEME_LINE_HEIGHT, Radius.pill(THEME_LINE_HEIGHT), lines);
			}
		};
	}

	/** @return the picture of the Colored theme: the small window over the colors there are now */
	private static EnumSetting.OptionPicture coloredPicture() {
		EnumSetting.OptionPicture window = themePicture(0, 0, 0x40FFFFFF, 0x59FFFFFF);
		return (ui, x, y, width, height) -> {
			int[] colors = ModFeatures.MENU.coloredColors();
			ui.roundedGradient(x, y, width, height, Radius.SM, 0xFF000000 | colors[0], 0xFF000000 | colors[colors.length - 1]);
			window.draw(ui, x, y, width, height);
		};
	}

	private static EnumSetting.OptionCard themeCard(MacTheme theme) {
		return THEME_CARDS.get(theme);
	}

	private final PagePreview preview = new MenuPreview();

	@Override
	public PagePreview preview() {
		return preview;
	}

	/** A card of the menu and the HUD over the world that is behind the window now, drawn as the theme, the accent, the glass and the glow currently make them look. */
	private static final class MenuPreview implements PagePreview {
		private static final String NOTE = "The menu and the HUD over your world, live while you change things.";
		/** Room beside and above the card and the HUD, in menu pixels. */
		private static final int SIDE = 12;
		private static final int PAD = 10;
		private static final int TILE = 16;
		/** The three rows of the card (a setting, a slider, a button) are this far apart, middle to middle, with the padding above and below. */
		private static final int ROW = 24;
		private static final int BUTTON_HEIGHT = 22;
		private static final int CARD_HEIGHT = 2 * PAD + 3 * ROW;
		private static final int BAR_HEIGHT = 20;
		private static final int HOTBAR_HEIGHT = 26;
		private static final int HOTBAR_SLOTS = 9;
		private static final int HOTBAR_INSET = 3;
		private static final int HOTBAR_GAP = 2;
		private static final int CHOSEN_SLOT = 2;
		private static final int HUD_GAP = 6;
		private static final int BAR_TILE = 10;
		private static final int BAR_PAD = 8;
		private static final String BAR_TEXT = "Your song · 14:32";
		/** How far the sample slider is filled. */
		private static final float SLIDER = 0.55f;

		@Override
		public String note() {
			return NOTE;
		}

		@Override
		public boolean tall() {
			return true;
		}

		@Override
		public void draw(Ui ui, int x, int y, int width, int height) {
			if (!Glass.world(ui, x, y, width, height, Radius.LG)) {
				// without the shader nothing can look through the window: a painted scene stands in for the world
				FeaturePreview.backdrop(ui, x, y, width, height, Radius.LG);
			}
			int inner = width - 2 * SIDE;
			drawCard(ui, x + SIDE, y + SIDE, inner);
			drawHud(ui, x + SIDE, y + height - SIDE - HOTBAR_HEIGHT - HUD_GAP - BAR_HEIGHT, inner);
		}

		/** The HUD as the game draws it, in its own theme: an info bar and a hotbar. */
		private static void drawHud(Ui ui, int left, int top, int width) {
			HudStyle.setPanelTheme(ModFeatures.MENU.hudTheme());
			try {
				HudStyle.panel(ui, left, top, width, BAR_HEIGHT, Radius.pill(BAR_HEIGHT), HudStyle.opacity(), UiGlow.Target.HUD, 1f);
				int middle = top + BAR_HEIGHT / 2;
				ui.rounded(left + BAR_PAD, middle - BAR_TILE / 2, BAR_TILE, BAR_TILE, Radius.XS, ui.accent);
				ui.textMiddle(BAR_TEXT, left + BAR_PAD + BAR_TILE + BAR_PAD / 2, middle, HudStyle.text());
				int barTop = top + BAR_HEIGHT + HUD_GAP;
				HudStyle.panel(ui, left, barTop, width, HOTBAR_HEIGHT, Radius.MD, HudStyle.opacity(), UiGlow.Target.HUD, 1f);
				int slotWidth = (width - 2 * HOTBAR_INSET - (HOTBAR_SLOTS - 1) * HOTBAR_GAP) / HOTBAR_SLOTS;
				int slotHeight = HOTBAR_HEIGHT - 2 * HOTBAR_INSET;
				for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
					int slotX = left + HOTBAR_INSET + slot * (slotWidth + HOTBAR_GAP);
					ui.rounded(slotX, barTop + HOTBAR_INSET, slotWidth, slotHeight, Radius.XS, HudStyle.slot());
					if (slot == CHOSEN_SLOT) {
						ui.ring(slotX, barTop + HOTBAR_INSET, slotWidth, slotHeight, Radius.XS, 1, ui.accent);
					}
				}
			} finally {
				HudStyle.setPanelTheme(null);
			}
		}

		/** Draws a card of the menu, large across the preview: a setting with its switch, a slider and a button. */
		private static void drawCard(Ui ui, int panelX, int panelY, int panelWidth) {
			HudStyle.panel(ui, panelX, panelY, panelWidth, CARD_HEIGHT, Radius.XL, HudStyle.opacity(), UiGlow.Target.MENU, 1f);
			Palette palette = HudStyle.palette();
			int left = panelX + PAD;
			int right = panelX + panelWidth - PAD;
			// a setting with its switch
			int settingY = panelY + PAD + ROW / 2;
			ui.rounded(left, settingY - TILE / 2, TILE, TILE, Radius.XS, ui.accent);
			ui.textMiddle("A setting", left + TILE + 7, settingY, palette.text());
			SwitchControl.draw(ui, right - SwitchControl.TRACK_WIDTH, settingY - SwitchControl.TRACK_HEIGHT / 2, 1f, 0f, 0f);
			// a slider
			int sliderY = settingY + ROW;
			ui.textMiddle("Size", left, sliderY, palette.textSecondary());
			int trackX = left + 34;
			int trackWidth = right - trackX;
			ui.rounded(trackX, sliderY - 2, trackWidth, 4, Radius.pill(4), palette.trackOff());
			int filled = Math.round(trackWidth * SLIDER);
			ui.rounded(trackX, sliderY - 2, filled, 4, Radius.pill(4), ui.accent);
			ui.roundedSmooth(trackX + filled - 6f, sliderY - 6f, 12f, 12f, 6f, palette.knob());
			// a button in the accent color
			int buttonY = sliderY + ROW - BUTTON_HEIGHT / 2;
			ui.rounded(left, buttonY, right - left, BUTTON_HEIGHT, Radius.SM, ui.accent);
			ui.textCentered("Button", (left + right) / 2, buttonY + BUTTON_HEIGHT / 2, 0xFFFFFFFF);
		}
	}
}
