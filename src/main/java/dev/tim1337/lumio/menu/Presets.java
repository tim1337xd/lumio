package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.config.ConfigPart;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.ui.MacTheme;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

/**
 * The presets that ship with Lumio: a whole setup to start from, the look, the HUD and which features are on. Both share the main mod's part;
 * SkyBlock adds each installed add-on's own part (a file the add-on ships, {@code assets/<add-on id>/presets/skyblock.json}), and Vanilla turns
 * every feature of an add-on off. Nothing private is in them: what is never shared is never imported either (see {@link FeatureManager}).
 */
public final class Presets {
	/**
	 * A preset: its name and line, and the colors of its picture on the configs page (the menu theme, the accent and second color, the top and
	 * bottom of its sky).
	 */
	public record Preset(String id, String name, String note, boolean withAddons, MacTheme theme, int primary, int secondary, int skyTop, int skyBottom) {
	}

	/** The main mod's part of every preset. */
	private static final String MAIN_PART = "/assets/lumio/presets/lumio.json";

	public static final List<Preset> ALL = List.of(
		new Preset("skyblock", "SkyBlock", "With all SkyBlock helpers", true, MacTheme.COLORED, 0x0A84FF, 0x5B00FF, 0x0B0640, 0x3A1A9E),
		new Preset("vanilla", "Vanilla", "Same look, no SkyBlock", false, MacTheme.COLORED, 0x0A84FF, 0x5B00FF, 0x0B1A30, 0x1E5A9E));

	private Presets() {
	}

	/** Sets the whole setup of a preset: every part of the main mod's settings, then the add-ons' part or their features off. */
	public static void apply(Preset preset) {
		String main = read(MAIN_PART);
		if (main != null) {
			FeatureManager.importText(main, EnumSet.allOf(ConfigPart.class));
		}
		for (AddonInfo addon : Addons.loaded()) {
			String part = preset.withAddons() ? read("/assets/" + addon.id() + "/presets/" + preset.id() + ".json") : null;
			if (part != null) {
				FeatureManager.importText(part, EnumSet.allOf(ConfigPart.class));
			}
		}
		if (!preset.withAddons()) {
			// every one, also a family that reads as on while one of its members is: they all end up off
			for (Feature feature : FeatureManager.features()) {
				if (FeatureManager.addonOf(feature) != null) {
					feature.setEnabled(false);
				}
			}
		}
		FeatureManager.saveSoon();
	}

	private static String read(String path) {
		try (InputStream in = Presets.class.getResourceAsStream(path)) {
			return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Logs.warn("Could not read the preset {}", path, e);
			return null;
		}
	}
}
