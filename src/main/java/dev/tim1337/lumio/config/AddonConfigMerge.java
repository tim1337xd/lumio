package dev.tim1337.lumio.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** Reads the add-ons' settings, each in its own file (config/lumio/addons/&lt;id&gt;.json), together with the main file. Plain logic on JSON, tested on its own. */
public final class AddonConfigMerge {
	private AddonConfigMerge() {
	}

	/**
	 * @return one object to read every feature from: the main file's sections with the add-ons' sections over them, at the main file's version
	 *         (the add-on files are written at the same time with the same version)
	 */
	public static JsonObject merged(JsonObject main, Map<String, JsonObject> addons) {
		JsonObject merged = main == null ? new JsonObject() : main.deepCopy();
		for (JsonObject addon : addons.values()) {
			for (Map.Entry<String, JsonElement> section : addon.entrySet()) {
				if (section.getKey().equals(ConfigUpgrade.VERSION_KEY)) {
					if (!merged.has(ConfigUpgrade.VERSION_KEY)) {
						merged.add(ConfigUpgrade.VERSION_KEY, section.getValue().deepCopy());
					}
					continue;
				}
				merged.add(section.getKey(), section.getValue().deepCopy());
			}
		}
		return merged;
	}
}
