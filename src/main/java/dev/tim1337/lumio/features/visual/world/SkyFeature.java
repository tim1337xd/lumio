package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.DerivedBooleanSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.DerivedFloatSetting;
import dev.tim1337.lumio.config.setting.GlowColorsSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.menu.MenuFeature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Set;

/**
 * Clouds, aurora and shooting stars in the world, in 3D, seen from a place that never jumps ({@link SkyLook}). The clouds are marched in a pass of their own and
 * go over the finished world, the night skies are drawn in place of the game's stars, see {@link dev.tim1337.lumio.render.CustomSky}, and the shooting stars are streaks in the world, see
 * {@link dev.tim1337.lumio.render.SkyMeteorDraw}.
 */
public class SkyFeature extends Feature {
	/** The night skies, with the index the shader uses for each. */
	public enum Theme {
		NORTHERN_LIGHTS(2), FALLING_STARS(5);

		private final int shaderIndex;

		Theme(int shaderIndex) {
			this.shaderIndex = shaderIndex;
		}

		/** @return the horizon color, also used for the fog, as RGB from 0 to 1 */
		public float[] horizon(int rgb) {
			float r = (rgb >> 16 & 0xFF) / 255f;
			float g = (rgb >> 8 & 0xFF) / 255f;
			float b = (rgb & 0xFF) / 255f;
			return this == NORTHERN_LIGHTS ? new float[]{0.02f + r * 0.03f, 0.07f + g * 0.04f, 0.11f + b * 0.03f} : new float[]{r * 0.07f + 0.025f, g * 0.02f + 0.006f, b * 0.06f + 0.03f};
		}

		/** @return the zenith color */
		public float[] zenith(int rgb) {
			float r = (rgb >> 16 & 0xFF) / 255f;
			float g = (rgb >> 8 & 0xFF) / 255f;
			float b = (rgb & 0xFF) / 255f;
			return this == NORTHERN_LIGHTS ? new float[]{0.006f + r * 0.01f, 0.02f + g * 0.015f, 0.04f + b * 0.015f} : new float[]{r * 0.02f + 0.006f, g * 0.006f + 0.002f, b * 0.02f + 0.01f};
		}
	}

	/** Detail level of the sky lights, i.e. the GPU cost. */
	public enum Quality {
		FAST("Fast"),
		BALANCED("Balanced"),
		DETAILED("Detailed");

		private final Component displayName;

		Quality(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private static final String LOOK = "Look";
	private static final String WHEN = "When";
	private static final String SHOW = "Show";
	private static final String EXTRAS = "Extras";
	private static final String SHAPE = "Clouds: amount and shape";
	private static final String LAYER = "Clouds: layer and motion";
	private static final String LIGHT = "Clouds: light";
	private static final String AURORA = "Aurora";
	private static final String STARS = "Stars";
	private static final int NATURAL_TINT_DEFAULT = 0xFFD0B0;
	private static final int NATURAL_AURORA_RGB = 0x3DFF9A;
	private static final int NATURAL_STAR_RGB = 0x9A4DFF;
	private static final int[] AURORA_COLORS = {NATURAL_AURORA_RGB, 0x6FB4FF, 0xFF6FB5};
	private static final float CLOUD_TINT = 0.65f;
	/** An island of the Nether under the open sky (the Crimson Isle): the natural colors of its mood, warm light from below and dark ash. */
	private static final int NETHER_STAR_RGB = 0xFF7A3A;
	private static final int NETHER_AURORA_RGB = 0xFF5A1E;
	private static final int NETHER_ASH_RGB = 0x3A2C28;
	private static final float[] NETHER_HORIZON = {0.26f, 0.085f, 0.045f};
	private static final float[] NETHER_ZENITH = {0.05f, 0.025f, 0.03f};
	/** How far from the camera the cover over the Nether is looked at, besides straight above it. */
	private static final int NETHER_AROUND_BLOCKS = 12;
	/** How much a flash of lightning brightens the sky and the fog at most. */
	private static final float FLASH_ON_SKY = 0.22f;
	private static final float STYLE_FADE_SECONDS = 3f;
	private static final float CLOUD_FADE_SECONDS = 6f;
	/** How much a strike in clear weather darkens the clouds, how long it stays, and how quickly it comes and goes. */
	private static final float GLOOM = 0.35f;
	private static final float GLOOM_SECONDS = 4f;
	private static final float GLOOM_FADE_SECONDS = 2f;
	/** How fast the clouds go when the camera goes under cover, and come back when it comes out. */
	private static final float OPEN_SKY_FADE_SECONDS = 1.5f;
	private static final float COVER_CHECK_SECONDS = 0.25f;
	/** The Weather look: how long it fades, how much Cloudy veils the sun and the moon and dims the stars, and how much rain it is on the clouds and on the sky. */
	private static final float WEATHER_LOOK_SECONDS = 2f;
	private static final float CLOUDY_VEIL = 0.4f;
	private static final float CLOUDY_STAR_DIM = 0.6f;
	private static final float CLOUDY_RAIN = 0.3f;
	private static final float CLOUDY_SKY_RAIN = 0.45f;
	private static final float RAIN_FADE_SECONDS = 8f;
	private static final float WIND_BLOCKS_PER_SECOND = 9f;
	private static final float FOLLOW_COLOR_SECONDS = 1.2f;

	private final EnumSetting<SkyLook.Style> style = add(new EnumSetting<>("style", Component.literal("Style"), SkyLook.Style.AUTO, SkyLook.Style::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final FloatSetting cloudCover = add(new FloatSetting("cloud_cover", Component.literal("Cloud cover"), 0.55f, 0f, 1f, 2).percent(), LOOK);
	private final EnumSetting<SkyLook.WeatherLook> weather = add(new EnumSetting<>("weather_look", Component.literal("Weather look"), SkyLook.WeatherLook.CLEAR, SkyLook.WeatherLook::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final EnumSetting<SkyLook.CloudHeight> cloudHeight = add(new EnumSetting<>("cloud_height", Component.literal("Cloud height"), SkyLook.CloudHeight.GAME, SkyLook.CloudHeight::displayName, EnumSetting.Style.SEGMENTED), LOOK,
		"Low clouds drift through hills");
	private final BooleanSetting cloudLight = add(new BooleanSetting("cloud_light", Component.literal("Light and shadows"), true), LOOK, "Sun and moon light them");
	private final BooleanSetting weatherFollows = add(new BooleanSetting("weather_follows", Component.literal("Weather follows the game"), true), WHEN, "Rain turns clouds into a storm");
	private final EnumSetting<SkyLightning.When> lightning = add(new EnumSetting<>("lightning_when", Component.literal("Lightning"), SkyLightning.When.WITH_RAIN, SkyLightning.When::displayName, EnumSetting.Style.SEGMENTED), WHEN,
		"Flashes in the clouds");
	private final EnumSetting<SkyLightning.Often> lightningOften = add(new EnumSetting<>("lightning_often", Component.literal("Lightning"), SkyLightning.Often.NORMAL, SkyLightning.Often::displayName, EnumSetting.Style.SEGMENTED), LIGHT);
	private final BooleanSetting flyBys = add(new BooleanSetting("fly_bys", Component.literal("Close fly-bys"), true), EXTRAS, "Now and then one passes right over you");
	private final EnumSetting<SkyLook.Colors> cloudColors = colorsRow("cloud_colors", "Cloud colors", LOOK);
	private final ColorSetting cloudColor = hiddenColor("cloud_color", NATURAL_TINT_DEFAULT);
	private final GlowColorsSetting cloudColorRow = add(new GlowColorsSetting("cloud_color_row", Component.literal("Clouds"), new FloatSetting("one", Component.empty(), 1f, 1f, 1f, 0), new ColorSetting[]{cloudColor}), LOOK);
	private final EnumSetting<SkyLook.Colors> auroraColors = colorsRow("aurora_colors", "Aurora colors", LOOK);
	private final FloatSetting auroraCount = add(new FloatSetting("aurora_color_count", Component.literal("Colors"), 1f, 1f, AURORA_COLORS.length, 0), LOOK);
	private final ColorSetting[] auroraSlots = {hiddenColor("aurora_color_1", AURORA_COLORS[0]), hiddenColor("aurora_color_2", AURORA_COLORS[1]), hiddenColor("aurora_color_3", AURORA_COLORS[2])};
	private final GlowColorsSetting auroraColorRow = add(new GlowColorsSetting("aurora_color_row", Component.literal("Aurora"), auroraCount, auroraSlots), LOOK);
	private final EnumSetting<SkyLook.Colors> starColors = colorsRow("star_colors", "Star colors", LOOK);
	private final ColorSetting starColor = hiddenColor("star_color", NATURAL_STAR_RGB);
	private final GlowColorsSetting starColorRow = add(new GlowColorsSetting("star_color_row", Component.literal("Stars"), new FloatSetting("one", Component.empty(), 1f, 1f, 1f, 0), new ColorSetting[]{starColor}), LOOK);
	private final FloatSetting starTrail = add(new FloatSetting("star_trail", Component.literal("Trail length"), 0.6f, 0f, 1f, 2).percent(), LOOK);
	private final DerivedFloatSetting starAmountAgain = add(new DerivedFloatSetting("star_amount_again", Component.literal("Amount"), 0f, 1f, 2, () -> this.starDensity.get(), value -> this.starDensity.set(value)), STARS);
	private final DerivedFloatSetting starSpeedAgain = add(new DerivedFloatSetting("star_speed_again", Component.literal("Speed"), 0.2f, 5f, 2, () -> this.starSpeed.get(), value -> this.starSpeed.set(value)), STARS);
	private final DerivedFloatSetting starTrailAgain = add(new DerivedFloatSetting("star_trail_again", Component.literal("Trail length"), 0f, 1f, 2, () -> starTrail.get(), value -> starTrail.set(value)), STARS);
	private final DerivedBooleanSetting flyBysAgain = add(new DerivedBooleanSetting("fly_bys_again", Component.literal("Close fly-bys"), flyBys), STARS);
	private final DerivedFloatSetting coverAgain = add(new DerivedFloatSetting("cover_again", Component.literal("Cover"), 0f, 1f, 2, () -> cloudCover.get(), value -> cloudCover.set(value)), SHAPE);
	private final EnumSetting<SkyLook.Gaps> gaps = add(new EnumSetting<>("gaps", Component.literal("Gaps"), SkyLook.Gaps.FEW, SkyLook.Gaps::displayName, EnumSetting.Style.SEGMENTED), SHAPE);
	private final FloatSetting gapSize = add(new FloatSetting("gap_size", Component.literal("Gap size"), 0.4f, 0f, 1f, 2).percent(), SHAPE);
	private final FloatSetting cloudSize = add(new FloatSetting("cloud_size", Component.literal("Cloud size"), 0.42f, 0f, 1f, 2).percent(), SHAPE);
	private final FloatSetting puffiness = add(new FloatSetting("puffiness", Component.literal("Puffiness"), 0.7f, 0f, 1f, 2).percent(), SHAPE);
	private final FloatSetting heightOffset = add(new FloatSetting("height_offset", Component.literal("Height"), 0f, -80f, 120f, 0), LAYER);
	private final FloatSetting thickness = add(new FloatSetting("thickness", Component.literal("Thickness"), 0.45f, 0f, 1f, 2).percent(), LAYER);
	private final FloatSetting cloudSpeed = add(new FloatSetting("cloud_speed", Component.literal("Speed"), 0.3f, 0f, 1f, 2).percent(), LAYER);
	private final EnumSetting<SkyLook.Wind> windFrom = add(new EnumSetting<>("wind", Component.literal("Wind"), SkyLook.Wind.GAME, SkyLook.Wind::displayName, EnumSetting.Style.SEGMENTED), LAYER);
	private final FloatSetting shadowStrength = add(new FloatSetting("shadow_strength", Component.literal("Shadow strength"), 0.5f, 0f, 1f, 2).percent(), LIGHT);
	private final BooleanSetting sunsetGlow = add(new BooleanSetting("sunset_glow", Component.literal("Sunset glow"), true), LIGHT);
	private final FloatSetting rainDarkness = add(new FloatSetting("rain_darkness", Component.literal("Rain darkness"), 0.7f, 0f, 1f, 2).percent(), LIGHT);

	private final SkyLook.AutoPick autoPick = new SkyLook.AutoPick();
	private final SkyLook.Eye eye = new SkyLook.Eye();
	private final SkyLook.SkyClock clock = new SkyLook.SkyClock();
	private final SkyLook.Fade<Theme> shownTheme = new SkyLook.Fade<>(STYLE_FADE_SECONDS);
	private final SkyLook.Slew cloudLevel = new SkyLook.Slew(CLOUD_FADE_SECONDS);
	private final SkyLook.Slew gloom = new SkyLook.Slew(GLOOM_FADE_SECONDS);
	/** The Weather look shown, faded in and out: Cloudy and Storm. */
	private final SkyLook.Slew cloudyLook = new SkyLook.Slew(WEATHER_LOOK_SECONDS);
	private final SkyLook.Slew stormLook = new SkyLook.Slew(WEATHER_LOOK_SECONDS);
	/** 1 where the camera is under the open sky, 0 under cover (a cave, the mines): the clouds and their weather on the sky and the fog go with it. */
	private final SkyLook.Slew openSky = new SkyLook.Slew(OPEN_SKY_FADE_SECONDS);
	private boolean underCover;
	/** In a world of the Nether's kind, which has no sky of the game: 1 under the open sky, 0 under its roof. Only then is the mod's sky shown there. */
	private final SkyLook.Slew netherSky = new SkyLook.Slew(OPEN_SKY_FADE_SECONDS);
	private boolean netherKind;
	private boolean netherOpen;
	private float coverCheckIn;
	private final SkyLook.Slew rainNow = new SkyLook.Slew(RAIN_FADE_SECONDS);
	private final SkyLook.Slew stormNow = new SkyLook.Slew(RAIN_FADE_SECONDS);
	/** The sky as shown this frame, and the color of the low sun: the clouds take their light from them, so the two always fit. */
	private final float[] ambient = {0.5f, 0.6f, 0.8f};
	private final float[] sunsetRgb = {1f, 0.52f, 0.26f};
	private final float[] look = new float[3];
	private final SkyMeteors meteors = new SkyMeteors();
	private final SkyLook.Slew glowShare = new SkyLook.Slew(STYLE_FADE_SECONDS);
	private final SkyLook.Slew starLevel = new SkyLook.Slew(STYLE_FADE_SECONDS);
	private long lastFrame;
	private float sunX;
	private float sunY = 1f;
	private double windX;
	private double windZ;
	private float shownSkyRed = -1f;
	private float shownSkyGreen;
	private float shownSkyBlue;
	private float shownFogRed = -1f;
	private float shownFogGreen;
	private float shownFogBlue;



	private final FloatSetting starDensity = add(new FloatSetting("star_density", Component.literal("Amount"), 0.6f, 0f, 1f, 2).percent(), LOOK);
	private final FloatSetting starSpeed = add(new FloatSetting("star_speed", Component.literal("Speed"), 1f, 0.2f, 5f, 2), LOOK);
	/** How fast the aurora's curtains drift and wave, against their usual pace. */
	private final FloatSetting auroraSpeed = add(new FloatSetting("aurora_speed", Component.literal("Speed"), 1f, 0.25f, 3f, 2), LOOK);


	private final BooleanSetting showSun = add(new BooleanSetting("show_sun", Component.literal("Sun"), true), SHOW);
	private final BooleanSetting showMoon = add(new BooleanSetting("show_moon", Component.literal("Moon"), true), SHOW);
	private final BooleanSetting showStars = add(new BooleanSetting("show_stars", Component.literal("Stars"), true), SHOW, "The game's own stars");
	private final EnumSetting<SkyLook.When> when = add(new EnumSetting<>("when", Component.literal("When"), SkyLook.When.NIGHT, SkyLook.When::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final EnumSetting<Quality> quality = add(new EnumSetting<>("quality", Component.literal("Quality"), Quality.BALANCED, Quality::displayName, EnumSetting.Style.SEGMENTED), LOOK,
		"Higher costs more performance");
	private final FloatSetting brightness = add(new FloatSetting("brightness", Component.literal("Brightness"), 1f, 0.3f, 2f, 2), LOOK);

	// the rows of the styles' main pages again in Advanced, where Auto has them all
	private final DerivedEnumSetting<SkyLook.CloudHeight> heightAgain = add(new DerivedEnumSetting<>("height_again", Component.literal("Cloud height"), SkyLook.CloudHeight.GAME, SkyLook.CloudHeight::displayName, EnumSetting.Style.SEGMENTED, cloudHeight::get, cloudHeight::set), SHAPE);
	private final DerivedBooleanSetting lightAgain = add(new DerivedBooleanSetting("light_again", Component.literal("Light and shadows"), cloudLight), LIGHT);
	private final DerivedBooleanSetting weatherAgain = add(new DerivedBooleanSetting("weather_again", Component.literal("Weather follows the game"), weatherFollows), LIGHT);
	private final DerivedFloatSetting auroraBrightness = add(new DerivedFloatSetting("aurora_brightness_again", Component.literal("Brightness"), 0.3f, 2f, 2, brightness::get, brightness::set), AURORA);
	private final DerivedFloatSetting starBrightness = add(new DerivedFloatSetting("star_brightness_again", Component.literal("Brightness"), 0.3f, 2f, 2, brightness::get, brightness::set), STARS);

	private final DerivedEnumSetting<SkyLightning.When> lightningAgain = add(new DerivedEnumSetting<>("lightning_again", Component.literal("Lightning"), SkyLightning.When.WITH_RAIN, SkyLightning.When::displayName,
		EnumSetting.Style.SEGMENTED, lightning::get, lightning::set), LIGHT);
	private final SkyLightning strikes = new SkyLightning();

	/** The sky opacity as last computed. */
	private float lastOpacity;

	public SkyFeature() {
		super("sky", "Sky", "Clouds, aurora and stars in the world, in 3D.", false);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("moon");
		summary("Clouds, aurora and stars in the world, in 3D");

		for (Setting<?> row : List.of(cloudCover, weather, cloudHeight, cloudLight, weatherFollows, lightning)) {
			row.visibleWhen(() -> style.get() == SkyLook.Style.CLOUDS);
		}
		for (Setting<?> row : List.of(starDensity, starSpeed, starTrail, flyBys)) {
			row.visibleWhen(() -> style.get() == SkyLook.Style.STARS);
		}
		cloudColors.visibleWhen(this::cloudsChoosable);
		cloudColorRow.visibleWhen(() -> cloudsChoosable() && cloudColors.get() == SkyLook.Colors.CUSTOM);
		auroraColors.visibleWhen(this::auroraChoosable);
		auroraColorRow.visibleWhen(() -> auroraChoosable() && auroraColors.get() == SkyLook.Colors.CUSTOM);
		starColors.visibleWhen(this::starsChoosable);
		starColorRow.visibleWhen(() -> starsChoosable() && starColors.get() == SkyLook.Colors.CUSTOM);
		auroraCount.visibleWhen(() -> false);
		when.visibleWhen(() -> style.get() != SkyLook.Style.CLOUDS);
		brightness.visibleWhen(() -> style.get() == SkyLook.Style.AURORA);
		auroraSpeed.visibleWhen(() -> style.get() == SkyLook.Style.AURORA);
		// Advanced: the sections of the style in use; with Auto one for each
		for (Setting<?> row : List.of(gaps, gapSize, cloudSize, puffiness, heightOffset, thickness, cloudSpeed, windFrom, shadowStrength, sunsetGlow, rainDarkness)) {
			row.visibleWhen(this::cloudsChoosable);
		}
		gapSize.visibleWhen(() -> cloudsChoosable() && gaps.get() != SkyLook.Gaps.NONE);
		for (Setting<?> row : List.of(coverAgain, heightAgain, lightAgain, weatherAgain, lightningAgain, auroraBrightness, starAmountAgain, starSpeedAgain, starTrailAgain, flyBysAgain)) {
			row.visibleWhen(() -> style.get() == SkyLook.Style.AUTO);
		}
		starBrightness.visibleWhen(this::starsChoosable);
		addResetButton();
	}

	/**
	 * @param starBrightness the vanilla star brightness, i.e. how dark the night is
	 * @param rainBrightness how clear the weather is, 1 without rain
	 * @return the sky opacity, from 0 to 1
	 */
	public float opacity(float starBrightness, float rainBrightness) {
		if (shownTheme.shown() == null) {
			lastOpacity = 0f;
			return 0f;
		}
		// the game reports 0.5 at the darkest and less during dusk and dawn, so the sky fades in and out with them
		// the Nether has no time of day: over its islands the sky is always the night's, and under its roof there is none
		float opacity = (when.get() == SkyLook.When.ALWAYS || netherKind ? 1f : Mth.clamp(starBrightness * 2f, 0f, 1f)) * rainBrightness;
		opacity *= shownTheme.level();
		if (netherKind) {
			opacity *= netherSky.value();
		}
		lastOpacity = opacity;
		return opacity;
	}

	/** @return the current horizon color: the night's or the day's, or a blend */
	public float[] horizonNow() {
		return netherLook(theme().horizon(rgb()), NETHER_HORIZON);
	}

	public float[] zenithNow() {
		return netherLook(theme().zenith(rgb()), NETHER_ZENITH);
	}

	/** @return the color, turned towards the Nether's own over one of its islands, with natural colors */
	private float[] netherLook(float[] color, float[] nether) {
		if (!netherKind || !naturalColors()) {
			return color;
		}
		float share = netherSky.value();
		return new float[]{Mth.lerp(share, color[0], nether[0]), Mth.lerp(share, color[1], nether[1]), Mth.lerp(share, color[2], nether[2])};
	}

	private boolean naturalColors() {
		return (theme() == Theme.NORTHERN_LIGHTS ? auroraColors.get() : starColors.get()) == SkyLook.Colors.NATURAL;
	}

	/** @return how much of the mood of an island of the Nether the sky has: 1 over one, 0 anywhere else */
	public float netherMood() {
		return netherKind ? netherSky.value() : 0f;
	}

	/** @return whether the game is to draw a sky in this world of the Nether's kind, which it otherwise never does: the camera is under the open sky */
	public boolean opensNetherSky() {
		return isEnabled() && netherKind && netherSky.value() > 0f;
	}

	public int themeIndex() {
		return theme().shaderIndex;
	}

	public Theme theme() {
		return shownTheme.shown() == null ? Theme.FALLING_STARS : shownTheme.shown();
	}

	/** @return the sky's base color as 0xRRGGBB */
	public int rgb() {
		if (theme() == Theme.NORTHERN_LIGHTS) {
			return auroraColors.get() == SkyLook.Colors.NATURAL ? (netherKind ? NETHER_AURORA_RGB : NATURAL_AURORA_RGB) : auroraRgb(0);
		}
		return starColors.get() == SkyLook.Colors.NATURAL ? (netherKind ? NETHER_STAR_RGB : NATURAL_STAR_RGB) : starRgb();
	}

	/** @return the quality as the shader expects it: 0, 1 or 2 */
	public int quality() {
		return dev.tim1337.lumio.render.GraphicsQuality.isFast() ? 0 : quality.get().ordinal();
	}

	public float brightness() {
		return brightness.get();
	}

	/** @return how much of the sun, the moon and the lights of the sky is shown: less only while a jump of the time of day is hidden */
	public float shownAfterJump() {
		return clock.shown();
	}

	public float starDensity() {
		return starDensity.get();
	}

	public float starSpeed() {
		return starSpeed.get();
	}

	/** @return how fast the aurora drifts and waves, 1 for its usual pace */
	public float auroraSpeed() {
		return auroraSpeed.get();
	}

	public boolean hidesVanillaStars() {
		return isEnabled() && (lastOpacity > 0f || !showStars.get());
	}

	/** @return whether the game's sun is left out */
	public boolean hidesSun() {
		// the Nether has no time of day, so no sun or moon over its islands
		return isEnabled() && (!showSun.get() || netherKind);
	}

	/** @return whether the game's moon is left out */
	public boolean hidesMoon() {
		return isEnabled() && (!showMoon.get() || netherKind);
	}

	/** What a place must not change under the mod's sky: its colors, and how far off it and the clouds fade into the fog. */
	private static final Set<EnvironmentAttribute<?>> SAME_EVERYWHERE = Set.of(EnvironmentAttributes.SKY_COLOR, EnvironmentAttributes.FOG_COLOR,
		EnvironmentAttributes.WATER_FOG_COLOR, EnvironmentAttributes.CLOUD_COLOR, EnvironmentAttributes.SUNRISE_SUNSET_COLOR, EnvironmentAttributes.STAR_BRIGHTNESS,
		EnvironmentAttributes.SKY_FOG_END_DISTANCE, EnvironmentAttributes.CLOUD_FOG_END_DISTANCE);

	/**
	 * The colors the biomes change: the sky, the fog and the water fog. Under the mod's sky they come from the dimension alone (its base color with the
	 * time of day applied), so the sky looks the same in every biome. Weather, the sun and moon, being underwater or in lava and the blindness
	 * and darkness effects still change the picture as before.
	 *
	 * @param system the attribute system asked; only the shown world's counts
	 * @return whether the biome is left out of the attribute's value: the sky is on, the attribute is one of these colors, and the shown world
	 * has the overworld's sky (the Nether and the End keep their own)
	 */
	public boolean replacesBiomeColor(EnvironmentAttributeSystem system, EnvironmentAttribute<?> attribute) {
		if (!isEnabled() || !SAME_EVERYWHERE.contains(attribute)) {
			return false;
		}
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.environmentAttributes() == system && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
	}

	/** @return whether vanilla clouds are hidden: they would be drawn on top of the sky, which does not fit a nebula */
	public boolean hidesClouds() {
		return isEnabled();
	}

	/** @return what the page wants of the night skies: a theme, or null for none */
	private Theme wantedTheme(float rain) {
		if (!isEnabled()) {
			return null;
		}
		return switch (style.get()) {
			case CLOUDS -> null;
			case AURORA -> Theme.NORTHERN_LIGHTS;
			case STARS -> Theme.FALLING_STARS;
			case AUTO -> switch (autoPick.update(sunY, rain)) {
				case CLOUDS -> null;
				case AURORA -> Theme.NORTHERN_LIGHTS;
				case STARS -> Theme.FALLING_STARS;
			};
		};
	}

	private boolean wantsClouds(float rain) {
		if (!isEnabled()) {
			return false;
		}
		return switch (style.get()) {
			case CLOUDS -> true;
			case AURORA, STARS -> false;
			case AUTO -> autoPick.update(sunY, rain) == SkyLook.Pick.CLOUDS;
		};
	}

	private EnumSetting<SkyLook.Colors> colorsRow(String key, String label, String group) {
		return add(new EnumSetting<>(key, Component.literal(label), SkyLook.Colors.NATURAL, SkyLook.Colors::displayName, EnumSetting.Style.SEGMENTED), group);
	}

	/** A color that shows as a swatch of a row of its own, so it has no row. */
	private ColorSetting hiddenColor(String key, int rgb) {
		ColorSetting setting = add(new ColorSetting(key, Component.literal("Color"), rgb), LOOK);
		setting.visibleWhen(() -> false);
		return setting;
	}

	private boolean auroraChoosable() {
		return (style.get() == SkyLook.Style.AURORA || style.get() == SkyLook.Style.AUTO);
	}

	private boolean starsChoosable() {
		return (style.get() == SkyLook.Style.STARS || style.get() == SkyLook.Style.AUTO);
	}

	/** @return how many colors the aurora blends, 0 for its natural ones */
	public int auroraColorCount() {
		return switch (auroraColors.get()) {
			// over an island of the Nether the natural aurora is one warm color
			case NATURAL -> netherKind ? 1 : 0;
			case ACCENT -> 1;
			case CUSTOM -> Math.clamp(Math.round(auroraCount.get()), 1, auroraSlots.length);
		};
	}

	/** @return one of the aurora's colors, as 0xRRGGBB */
	public int auroraRgb(int index) {
		return switch (auroraColors.get()) {
			case NATURAL -> NETHER_AURORA_RGB;
			case ACCENT -> MenuFeature.accentRgb();
			case CUSTOM -> auroraSlots[index].get() & 0xFFFFFF;
		};
	}

	/** @return the clouds' tint as 0xRRGGBB, whose strength is {@link #cloudTintShare} */
	public int cloudTintRgb() {
		return switch (cloudColors.get()) {
			case NATURAL -> NETHER_ASH_RGB;
			case ACCENT -> MenuFeature.accentRgb();
			case CUSTOM -> cloudColor.get() & 0xFFFFFF;
		};
	}

	/** @return how much the clouds take of the tint: nothing with their natural colors, lit only by sun, sunset and moon */
	public float cloudTintShare() {
		// natural clouds take no tint, but over an island of the Nether they are dark with ash
		return cloudColors.get() == SkyLook.Colors.NATURAL ? netherMood() * CLOUD_TINT : CLOUD_TINT;
	}

	/** @return the color of the stars and their trails as 0xRRGGBB, without their natural ones */
	public int starRgb() {
		return starColors.get() == SkyLook.Colors.ACCENT ? MenuFeature.accentRgb() : starColor.get() & 0xFFFFFF;
	}

	private boolean cloudsChoosable() {
		return (style.get() == SkyLook.Style.CLOUDS || style.get() == SkyLook.Style.AUTO);
	}

	/** Moves all that blends on by a frame, from what the game says of the moment. */
	private void updateFrame(ClientLevel level, SkyRenderState state) {
		long now = System.nanoTime();
		float seconds = lastFrame == 0L ? 0f : Math.min(0.25f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		boolean nether = level.dimensionType().skybox() == DimensionType.Skybox.NONE;
		if (nether != netherKind) {
			// into a world of the Nether's kind or out of it: looked at again at once, and the sky starts from nothing there
			netherKind = nether;
			netherOpen = false;
			netherSky.update(0f, OPEN_SKY_FADE_SECONDS);
			coverCheckIn = 0f;
		}
		var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double eyeX = eye.x();
		double eyeY = eye.y();
		double eyeZ = eye.z();
		eye.update(camera.x, camera.y, camera.z, seconds);
		meteors.shift((float) (eye.x() - eyeX), (float) (eye.y() - eyeY), (float) (eye.z() - eyeZ));
		clock.update(state.sunAngle, state.moonAngle, state.starAngle, System.identityHashCode(level), seconds);
		float sun = clock.sun();
		state.sunAngle = sun;
		state.moonAngle = clock.moon();
		state.starAngle = clock.stars();
		sunX = -Mth.sin(sun);
		sunY = Mth.cos(sun);
		// the game's fan of light at sunrise and sunset stands where the sun is: while a jump of time is hidden it has to wait, and it never comes at once
		//#if 26.3
		//$ float fanWanted = state.sunriseAndSunsetColor.w();
		//#else
		float fanWanted = (state.sunriseAndSunsetColor >>> 24) / 255f;
		//#endif
		glowShare.update(clock.settled() ? fanWanted : 0f, seconds);
		state.starBrightness = starLevel.update(state.starBrightness, seconds);
		float rain = weatherFollows.get() ? 1f - state.rainBrightness : 0f;
		rainNow.update(rain, seconds);
		stormNow.update(weatherFollows.get() ? level.getThunderLevel(1f) : 0f, seconds);
		boolean cloudSky = style.get() == SkyLook.Style.CLOUDS;
		cloudyLook.update(cloudSky && weather.get() == SkyLook.WeatherLook.CLOUDY ? 1f : 0f, seconds);
		stormLook.update(cloudSky && weather.get() == SkyLook.WeatherLook.STORM ? 1f : 0f, seconds);
		// the Weather look hides the sun, the moon and the stars as the game's rain does: softened behind thin clouds, gone in a storm
		float veil = shownClouds() * Math.max(cloudyLook.value() * CLOUDY_VEIL, stormLook.value());
		state.rainBrightness = Math.min(state.rainBrightness, 1f - veil);
		state.starBrightness *= 1f - Math.max(veil, cloudyLook.value() * shownClouds() * CLOUDY_STAR_DIM);
		// a Storm look counts as rain for the lightning
		float storm = Math.max(Math.max(rainNow.value(), stormNow.value()), stormLook.value());
		strikes.update(seconds, shownClouds() * SkyLightning.stormFor(lightning.get(), storm), isEnabled() && lightning.get() != SkyLightning.When.OFF, lightningOften.get());
		// a strike in clear weather leaves the clouds a little darker and stormier for some seconds
		gloom.update(lightning.get() == SkyLightning.When.ALWAYS && strikes.struckWithin(GLOOM_SECONDS) ? GLOOM : 0f, seconds);
		shownTheme.update(wantedTheme(rainNow.value()), seconds);
		cloudLevel.update(wantsClouds(rainNow.value()) ? 1f : 0f, seconds);
		coverCheckIn -= seconds;
		if (coverCheckIn <= 0f) {
			coverCheckIn = COVER_CHECK_SECONDS;
			checkCover(level, camera);
		}
		openSky.update(underCover ? 0f : 1f, seconds);
		netherSky.update(netherKind && netherOpen ? 1f : 0f, seconds);
		float drift = WIND_BLOCKS_PER_SECOND * cloudSpeed.get() * (1f + 2f * rainNow.value()) * seconds;
		windX += windFrom.get() == SkyLook.Wind.GAME ? drift : drift * 0.8f;
		windZ += windFrom.get() == SkyLook.Wind.GAME ? 0f : drift * 0.6f;
		boolean flying = shownTheme.shown() == Theme.FALLING_STARS && lastOpacity > 0.05f;
		meteors.update(seconds, starDensity.get(), starSpeed.get(), flying, flyBys.get());
	}

	public SkyMeteors meteors() {
		return meteors;
	}

	public SkyLook.Eye skyEye() {
		return eye;
	}

	/** @return how strongly the shooting stars show now */
	public float meteorStrength() {
		return shownTheme.shown() == Theme.FALLING_STARS ? lastOpacity * brightness.get() : 0f;
	}

	public float starTrail() {
		return starTrail.get();
	}

	public SkyLook.Colors starColors() {
		return starColors.get();
	}

	/** @return how much the clouds show: their own fade, and none under cover */
	public float cloudLevel() {
		return shownClouds();
	}

	private float shownClouds() {
		return cloudLevel.value() * openSky.value();
	}

	/**
	 * Looks at the cover over the camera, from the sky light where it is and how high the land above it reaches; only where the world has a sky.
	 * Asked a few times a second.
	 */
	private void checkCover(ClientLevel level, net.minecraft.world.phys.Vec3 camera) {
		if (netherKind) {
			// the roof of the Nether is in the height map; over an island there is nothing above: straight up, or in most places around
			BlockPos at = BlockPos.containing(camera);
			int openAround = 0;
			for (int side = 0; side < 4; side++) {
				int x = at.getX() + (side == 0 ? NETHER_AROUND_BLOCKS : side == 1 ? -NETHER_AROUND_BLOCKS : 0);
				int z = at.getZ() + (side == 2 ? NETHER_AROUND_BLOCKS : side == 3 ? -NETHER_AROUND_BLOCKS : 0);
				openAround += level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) <= at.getY() ? 1 : 0;
			}
			netherOpen = SkyLook.openOverNether(level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) - at.getY(), openAround);
			underCover = false;
			return;
		}
		if (!level.dimensionType().hasSkyLight()) {
			underCover = false;
			return;
		}
		BlockPos at = BlockPos.containing(camera);
		int above = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) - at.getY();
		underCover = SkyLook.underCover(level.getBrightness(LightLayer.SKY, at), above);
	}

	public float sunX() {
		return sunX;
	}

	public float sunY() {
		return sunY;
	}

	public float rainNow() {
		return Math.max(Math.max(rainNow.value(), gloom.value()), Math.max(cloudyLook.value() * CLOUDY_RAIN, stormLook.value()));
	}

	public double windX() {
		return windX;
	}

	public double windZ() {
		return windZ;
	}

	/** @return how much of the sky the clouds cover: the setting, raised by the Weather look */
	public float cover() {
		return SkyLook.weatherCover(cloudCover.get(), cloudyLook.value(), stormLook.value());
	}

	public SkyLook.Gaps gaps() {
		return gaps.get();
	}

	public float gapSize() {
		return gapSize.get();
	}

	public float cloudSize() {
		return cloudSize.get();
	}

	public float puffiness() {
		return puffiness.get();
	}

	/** @return the height of the base of the clouds, in blocks of the world */
	public float cloudBottom() {
		return cloudHeight.get().blocks() + heightOffset.get();
	}

	public float thickness() {
		return thickness.get();
	}

	public boolean lightAndShadows() {
		return cloudLight.get();
	}

	public float shadowStrength() {
		return cloudLight.get() ? shadowStrength.get() : 0f;
	}

	public boolean sunsetGlow() {
		return sunsetGlow.get();
	}

	public float rainDarkness() {
		return rainDarkness.get();
	}

	/** Gives the vanilla sky the theme color, so no daytime blue or night black remains, and takes the jumps out of what the game shows: a warp to a place with another time of day or other weather blends. */
	public void apply(ClientLevel level, SkyRenderState state) {
		if (!isEnabled() && cloudLevel.value() <= 0f && shownTheme.shown() == null) {
			lastFrame = 0L;
			clock.forget();
			return;
		}
		if (!isEnabled()) {
			updateFrame(level, state);
			return;
		}
		// the game darkens the lower half of the sky while the camera is below the horizon's height: walking down a hill flipped it on and off
		state.shouldRenderDarkDisc = false;
		updateFrame(level, state);
		float opacity = opacity(state.starBrightness, state.rainBrightness);
		steadySkyColor(state);
		if (opacity <= 0f) {
			return;
		}

		float[] horizon = horizonNow();
		float[] zenith = zenithNow();
		// the vanilla sky is a disc in the horizon color fading into the color above
		//#if 26.3
		//$ float[] themed = average(horizon, zenith);
		//$ state.skyColor = shownSky.set(Mth.lerp(opacity, state.skyColor.x(), themed[0]), Mth.lerp(opacity, state.skyColor.y(), themed[1]), Mth.lerp(opacity, state.skyColor.z(), themed[2]));
		//$ // the orange sunrise/sunset band does not belong in the theme
		//$ org.joml.Vector4fc glow = state.sunriseAndSunsetColor;
		//$ state.sunriseAndSunsetColor = shownGlow.set(glow.x(), glow.y(), glow.z(), glow.w() * (1f - opacity));
		//#else
		state.skyColor = mix(state.skyColor, average(horizon, zenith), opacity);
		// the orange sunrise/sunset band does not belong in the theme
		int glowAlpha = Math.round((state.sunriseAndSunsetColor >>> 24) * (1f - opacity));
		state.sunriseAndSunsetColor = (glowAlpha << 24) | (state.sunriseAndSunsetColor & 0xFFFFFF);
		//#endif
	}

	/** The game's sky color follows the time and weather of the place: a warp to another changes it at once, and here it blends. */
	private void steadySkyColor(SkyRenderState state) {
		//#if 26.3
		//$ float red = state.skyColor.x();
		//$ float green = state.skyColor.y();
		//$ float blue = state.skyColor.z();
		//#else
		float red = (state.skyColor >> 16 & 0xFF) / 255f;
		float green = (state.skyColor >> 8 & 0xFF) / 255f;
		float blue = (state.skyColor & 0xFF) / 255f;
		//#endif
		if (netherKind && naturalColors()) {
			// over an island of the Nether the sky the game would show is its warm one, which the clouds also take their light from
			float share = netherSky.value();
			red = Mth.lerp(share, red, (NETHER_HORIZON[0] * 2f + NETHER_ZENITH[0]) / 3f);
			green = Mth.lerp(share, green, (NETHER_HORIZON[1] * 2f + NETHER_ZENITH[1]) / 3f);
			blue = Mth.lerp(share, blue, (NETHER_HORIZON[2] * 2f + NETHER_ZENITH[2]) / 3f);
		}
		float share = shownSkyRed < 0f ? 1f : 1f - (float) Math.exp(-frameSeconds() / FOLLOW_COLOR_SECONDS);
		shownSkyRed = shownSkyRed < 0f ? red : shownSkyRed + (red - shownSkyRed) * share;
		shownSkyGreen += (green - shownSkyGreen) * share;
		shownSkyBlue += (blue - shownSkyBlue) * share;
		look[0] = shownSkyRed;
		look[1] = shownSkyGreen;
		look[2] = shownSkyBlue;
		weatherLook(look);
		System.arraycopy(look, 0, ambient, 0, 3);
		//#if 26.3
		//$ org.joml.Vector4fc low = state.sunriseAndSunsetColor;
		//$ if (low.w() > 0.01f) {
		//$ 	sunsetRgb[0] = low.x();
		//$ 	sunsetRgb[1] = low.y();
		//$ 	sunsetRgb[2] = low.z();
		//$ }
		//#else
		if ((state.sunriseAndSunsetColor >>> 24) > 2) {
			sunsetRgb[0] = (state.sunriseAndSunsetColor >> 16 & 0xFF) / 255f;
			sunsetRgb[1] = (state.sunriseAndSunsetColor >> 8 & 0xFF) / 255f;
			sunsetRgb[2] = (state.sunriseAndSunsetColor & 0xFF) / 255f;
		}
		//#endif
		//#if 26.3
		//$ state.skyColor = shownSky.set(look[0], look[1], look[2]);
		//$ org.joml.Vector4fc fan = state.sunriseAndSunsetColor;
		//$ state.sunriseAndSunsetColor = shownGlow.set(fan.x(), fan.y(), fan.z(), glowShare.value());
		//#else
		state.skyColor = 0xFF000000 | Math.round(look[0] * 255f) << 16 | Math.round(look[1] * 255f) << 8 | Math.round(look[2] * 255f);
		state.sunriseAndSunsetColor = Math.round(glowShare.value() * 255f) << 24 | state.sunriseAndSunsetColor & 0xFFFFFF;
		//#endif
	}

	/** The clouds' weather on a color of the sky or the fog, as much as the clouds are shown. */
	private void weatherLook(float[] rgb) {
		float clouds = shownClouds();
		if (clouds <= 0f) {
			return;
		}
		float overcast = clouds * Mth.clamp((cover() - 0.35f) / 0.65f, 0f, 1f);
		float rain = Math.max(rainNow.value(), Math.max(cloudyLook.value() * CLOUDY_SKY_RAIN, stormLook.value()));
		float storm = Math.max(stormNow.value(), stormLook.value());
		SkyLook.weatherSky(rgb, overcast, rain * clouds, storm * clouds, cloudTintRgb(), cloudTintShare() > 0f ? clouds : 0f);
		float flash = strikes.flash() * clouds * FLASH_ON_SKY;
		if (flash > 0f) {
			rgb[0] = Math.min(1f, rgb[0] + flash * 0.82f);
			rgb[1] = Math.min(1f, rgb[1] + flash * 0.86f);
			rgb[2] = Math.min(1f, rgb[2] + flash);
		}
	}

	/** @return the lightning of the storm, for the clouds and the bolt */
	public SkyLightning strikes() {
		return strikes;
	}

	/** @return the sky as shown this frame, from 0 to 1 per channel, for the clouds' light */
	public float[] ambient() {
		return ambient;
	}

	/** @return the color of the low sun, the game's own at sunrise and sunset */
	public float[] sunsetRgb() {
		return sunsetRgb;
	}

	private float frameSeconds() {
		return 1f / Math.max(10, Minecraft.getInstance().getFps());
	}

	/** Tints the fog with the horizon color, so the world fades into the sky, and lets a warp's change of fog color blend. */
	public void apply(FogData fog) {
		if (!isEnabled()) {
			shownFogRed = -1f;
			return;
		}
		// over an island of the Nether its fog goes into the sky's horizon in every style, so there is no seam between them
		float toSky = Math.max(lastOpacity, netherMood());
		if (toSky > 0f) {
			float[] horizon = horizonNow();
			fog.color.set(
				Mth.lerp(toSky, fog.color.x, horizon[0]),
				Mth.lerp(toSky, fog.color.y, horizon[1]),
				Mth.lerp(toSky, fog.color.z, horizon[2]),
				fog.color.w);
		}
		// the fog and the horizon are the sky's: the same weather over them, so there is no seam between sky, fog and clouds
		look[0] = fog.color.x;
		look[1] = fog.color.y;
		look[2] = fog.color.z;
		weatherLook(look);
		fog.color.set(look[0], look[1], look[2], fog.color.w);
		float share = shownFogRed < 0f ? 1f : 1f - (float) Math.exp(-frameSeconds() / FOLLOW_COLOR_SECONDS);
		shownFogRed = shownFogRed < 0f ? fog.color.x : shownFogRed + (fog.color.x - shownFogRed) * share;
		shownFogGreen += (fog.color.y - shownFogGreen) * share;
		shownFogBlue += (fog.color.z - shownFogBlue) * share;
		fog.color.set(shownFogRed, shownFogGreen, shownFogBlue, fog.color.w);
	}

	private static float[] average(float[] a, float[] b) {
		return new float[]{(a[0] * 2f + b[0]) / 3f, (a[1] * 2f + b[1]) / 3f, (a[2] * 2f + b[2]) / 3f};
	}

	//#if 26.3
	//$ private static final org.joml.Vector4fc NO_GLOW = new org.joml.Vector4f(0f);
	//$ /** The colors handed to the game's sky every frame; kept, so no new ones are made. */
	//$ private final org.joml.Vector3f shownSky = new org.joml.Vector3f();
	//$ private final org.joml.Vector4f shownGlow = new org.joml.Vector4f();
	//#endif

	/** @return a game ARGB color blended towards an RGB color */
	private static int mix(int argb, float[] rgb, float amount) {
		int red = Math.round(Mth.lerp(amount, argb >> 16 & 0xFF, rgb[0] * 255f));
		int green = Math.round(Mth.lerp(amount, argb >> 8 & 0xFF, rgb[1] * 255f));
		int blue = Math.round(Mth.lerp(amount, argb & 0xFF, rgb[2] * 255f));
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}
}
