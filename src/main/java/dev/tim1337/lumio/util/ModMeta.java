package dev.tim1337.lumio.util;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

/** Reads the custom values of a mod's fabric.mod.json; the only place that checks their type. */
public final class ModMeta {
	private ModMeta() {
	}

	/** @return a custom value that is text, or the fallback when it is missing or of another type */
	public static String string(ModMetadata meta, String key, String fallback) {
		CustomValue value = meta.getCustomValue(key);
		return value != null && value.getType() == CustomValue.CvType.STRING ? value.getAsString() : fallback;
	}

	/** @return whether a custom value is the boolean true; false when it is missing or of another type */
	public static boolean flag(ModMetadata meta, String key) {
		CustomValue value = meta.getCustomValue(key);
		return value != null && value.getType() == CustomValue.CvType.BOOLEAN && value.getAsBoolean();
	}
}
