package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.experimental.util.ModFiles;

import java.nio.file.Path;

/** Where the add-ons' own settings are kept: config/lumio/addons/&lt;add-on id&gt;.json. */
public final class AddonConfigs {
	/** The folder of the add-ons' settings files and of {@link AddonSwitches}, in the mod's config folder. */
	public static final String FOLDER = "addons";
	private static final String EXTENSION = ".json";

	private AddonConfigs() {
	}

	/** @return the settings file of an add-on; nothing is made on disk here, the writer makes the folder when it writes the file */
	public static Path fileOf(String addonId) {
		return ModFiles.file(FOLDER).resolve(AddonSwitches.clean(addonId) + EXTENSION);
	}
}
