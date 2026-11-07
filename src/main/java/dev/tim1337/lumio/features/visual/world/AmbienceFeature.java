package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;

import net.minecraft.network.chat.Component;

/** Fireflies or rain around the camera, and a wet ground that reflects in the rain. What is wanted when is in {@link AmbienceLook}, and {@link AmbienceField} keeps the air filled. */
public class AmbienceFeature extends Feature {
	private static final String LOOK = "Look";
	private static final String RAIN = "Rain";
	private static final String FIREFLIES = "Fireflies";

	private final EnumSetting<AmbienceLook.Style> style = add(new EnumSetting<>("mode", Component.literal("Style"), AmbienceLook.Style.FIREFLIES, AmbienceLook.Style::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final FloatSetting intensity = add(new FloatSetting("intensity", Component.literal("Amount"), 1f, 0f, 2f, 2).percent(), LOOK);
	private final FloatSetting particleSize = add(new FloatSetting("particle_size", Component.literal("Size"), 1f, 0.3f, 3f, 2).percent(), LOOK);
	private final EnumSetting<AmbienceLook.WetGround> wetGround = add(new EnumSetting<>("wet_ground", Component.literal("Wet ground"), AmbienceLook.WetGround.PUDDLES, AmbienceLook.WetGround::displayName, EnumSetting.Style.SEGMENTED), RAIN);
	private final FloatSetting wetStrength = add(new FloatSetting("wet_strength", Component.literal("Strength"), 0.7f, 0f, 1f, 2).percent(), RAIN);
	private final FloatSetting reflectionDistance = add(new FloatSetting("reflection_distance", Component.literal("Reflection distance"), 64f, 16f, 128f, 0), RAIN);
	private final ColorSetting fireflyColor = add(new ColorSetting("firefly_color", Component.literal("Firefly color"), 0xC8FF5A).followsMenu(), FIREFLIES);
	/** Firefly blinking: 0 is a steady glow, 1 alternates between lit and dark. */
	private final FloatSetting fireflyBlink = add(new FloatSetting("firefly_blink", Component.literal("Firefly blinking"), 0.2f, 0f, 1f, 2), FIREFLIES,
		"0 glows steadily, 1 blinks on and off");
	/** Firefly brightness, i.e. how much of their light is visible. */
	private final FloatSetting fireflyBrightness = add(new FloatSetting("firefly_brightness", Component.literal("Firefly brightness"), 0.8f, 0.1f, 1f, 2), FIREFLIES);
	/** Firefly count relative to the default. */
	private final FloatSetting fireflyAmount = add(new FloatSetting("firefly_amount", Component.literal("Firefly amount"), 1f, 0.1f, 6f, 1), FIREFLIES);
	private final BooleanSetting firefliesAnywhere = add(new BooleanSetting("fireflies_anywhere", Component.literal("Fireflies at any time and place"), false), FIREFLIES);

	private final AmbienceField field = new AmbienceField();

	public AmbienceFeature() {
		super("ambience", "Ambience", "Fireflies or rain around you, and a wet ground that reflects.", false);
		iconRgb(0x5AC8FA);
		iconName("cloud_snow");
		summary("Fireflies or rain around you, and a wet ground that reflects");
		wetGround.visibleWhen(() -> style.get() == AmbienceLook.Style.RAIN);
		wetStrength.visibleWhen(() -> style.get() == AmbienceLook.Style.RAIN && wetGround.get() != AmbienceLook.WetGround.OFF);
		reflectionDistance.visibleWhen(() -> style.get() == AmbienceLook.Style.RAIN && wetGround.get() != AmbienceLook.WetGround.OFF);
		addResetButton();
	}

	public AmbienceField field() {
		return field;
	}

	/** Copies the page's choices for {@link AmbienceLook}. */
	void fill(AmbienceLook.Choice choice) {
		choice.style = style.get();
		choice.firefliesAnywhere = firefliesAnywhere.get();
	}

	/** @return the particle size factor; 1 is the default size */
	public float particleSize() {
		return particleSize.get();
	}

	public float intensity() {
		return intensity.get();
	}

	public AmbienceLook.WetGround wetGround() {
		return wetGround.get();
	}

	/** @return how far the wet ground reflects, in blocks; it fades out over the last quarter */
	public float reflectionDistance() {
		return reflectionDistance.get();
	}

	/** @return how strongly the ground should reflect now */
	public float wetWanted() {
		return isEnabled() ? AmbienceLook.wetWanted(style.get(), wetGround.get(), wetStrength.get()) : 0f;
	}

	public float fireflyAmount() {
		return fireflyAmount.get();
	}

	public float fireflyBrightness() {
		return fireflyBrightness.get();
	}

	public float fireflyBlink() {
		return fireflyBlink.get();
	}

	public int fireflyRgb() {
		return fireflyColor.get();
	}
}
