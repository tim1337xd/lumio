package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.setting.SettingFactory;
import dev.tim1337.lumio.api.spi.Backend;

/** The add-on API of Lumio: its version, so an add-on can use newer parts only when they are there. */
public final class LumioApi {
	/** The version of this API; see API.md for the rules of how it changes. */
	public static final String VERSION = "1.8.0";
	/** The Fabric entrypoint add-ons are registered under, in their fabric.mod.json. */
	public static final String ENTRYPOINT = "lumio";
	/** The mod id of Lumio: the namespace of its textures and the name of its config folder. */
	public static final String MOD_ID = "lumio";

	private static final ApiVersion PARSED = ApiVersion.parse(VERSION);

	private LumioApi() {
	}

	/** @return the factory of settings, for an add-on's features (the same as {@link AddonContext#settings()}) */
	public static SettingFactory settings() {
		return Backend.get().settings();
	}

	/** Saves the settings a moment from now, once for many changes in a row (such as a tracker's totals); written off the game's thread. */
	public static void saveSoon() {
		Backend.get().saveSoon();
	}

	/** @return the version of the API the running Lumio provides */
	public static ApiVersion version() {
		return PARSED;
	}
}
