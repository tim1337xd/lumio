package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.features.visual.world.cinematic.CinematicKeys;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * What the camera shows, only on this client: Full Bright lifts the dark parts of the world through the light map (no night vision, no effect
 * icon), and the perspective key can skip the view from the front. The screen overlays it had (fire, portal, underwater) are in the Visual
 * Filter now ({@link VisualFilterFeature}); what a saved config had of them goes there.
 */
public class CameraFeature extends Feature {
	private static final String BRIGHT = "Full Bright";
	private static final String VIEW = "Perspective";
	/** How long Full Bright takes to fade in or out. */
	private static final float FADE_SECONDS = 0.3f;
	private static final float NANOS_PER_SECOND = 1_000_000_000f;
	/** The longest step of the fade between two light map updates, about two frames at 30 fps. */
	private static final float LONGEST_STEP_SECONDS = 0.07f;

	private final BooleanSetting fullBright = add(new BooleanSetting("full_bright", Component.literal("Full Bright"), false), BRIGHT);
	private final FloatSetting brightness = add(new FloatSetting("brightness", Component.literal("Brightness"), 0.9f, 0.2f, 1f, 2).percent(), BRIGHT);
	private final BooleanSetting skipFrontView = add(new BooleanSetting("skip_front_view", Component.literal("Skip front view"), false), VIEW,
		null);

	private KeyMapping toggleKey;
	/** How far Full Bright is faded in, 0 to 1, and when that was last moved. */
	private float bright;
	private long brightAt;

	public CameraFeature() {
		super("camera", "Camera", "Full Bright, front view and screen overlays.", false);
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("sun");
		summary("Full Bright, no front view, lower fire");
		brightness.under(fullBright);
	}

	/** Registers the Full Bright key, unbound at first, and shows it in the Keys group. Called during mod initialization. */
	public void registerKeys() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + Lumio.MOD_ID + ".full_bright", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CinematicKeys.CATEGORY));
		addKey(toggleKey, KeySetting.Where.WORLD);
	}

	/** Called every tick: the Full Bright key switches it, and the module with it when it is off. */
	public void tick(Minecraft client) {
		boolean pressed = false;
		while (toggleKey != null && toggleKey.consumeClick()) {
			pressed = true;
		}
		if (!pressed) {
			return;
		}
		boolean on = !(isEnabled() && fullBright.get());
		fullBright.set(on);
		if (on && !isEnabled()) {
			setEnabled(true);
		}
	}

	// ------------------------------------------------------------------------------------------------------ Full Bright

	/** How far Full Bright is faded in, eased, as the light map last used it. */
	private float eased;

	private boolean brightWanted() {
		return isEnabled() && fullBright.get();
	}

	/** @return whether Full Bright is still fading in or out, so the light map is made again every frame until it is done */
	public boolean brightChanging() {
		return brightWanted() ? bright < 1f : bright > 0f;
	}

	/**
	 * @return how far the light map's ambient color is lifted towards white now, 0 for the game's own light; moves Full Bright's fade on, once per
	 *         light map update
	 */
	/** @return how far Full Bright is faded in now, eased, 0 to 1, as {@link #brightLift} last moved it */
	public float brightShare() {
		return eased;
	}

	public float brightLift() {
		long now = System.nanoTime();
		// the light map is made only now and then while nothing changes: a fade that begins after such a pause starts from its first step
		float seconds = brightAt == 0L ? 0f : Math.min(LONGEST_STEP_SECONDS, (now - brightAt) / NANOS_PER_SECOND);
		brightAt = now;
		float step = seconds / FADE_SECONDS;
		bright = brightWanted() ? Math.min(1f, bright + step) : Math.max(0f, bright - step);
		// eased, so it starts and settles softly
		eased = bright * bright * (3f - 2f * bright);
		return eased * brightness.get();
	}

	// ------------------------------------------------------------------------------------------------------ perspective and overlays

	/** @return the perspective the key switches to, without the view from the front while that is skipped */
	public CameraType nextPerspective(CameraType next) {
		return isEnabled() && skipFrontView.get() && next == CameraType.THIRD_PERSON_FRONT ? CameraType.FIRST_PERSON : next;
	}

	/** The screen overlays moved to the Visual Filter: what this module had of them goes there, once. */
	@Override
	public void load(com.google.gson.JsonObject json) {
		super.load(json);
		if (json.has("fire") || json.has("portal") || json.has("underwater")) {
			dev.tim1337.lumio.ModFeatures.VISUAL_FILTER.takeOverFromCamera(json);
		}
	}
}
