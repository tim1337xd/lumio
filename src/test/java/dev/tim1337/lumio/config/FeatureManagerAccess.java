package dev.tim1337.lumio.config;

import java.util.Set;
import java.util.function.Predicate;

/** Lets tests of other packages apply a config as an import does, without the game's settings file being written. */
public final class FeatureManagerAccess {
	private FeatureManagerAccess() {
	}

	public static boolean apply(String text, Set<ConfigPart> parts, Predicate<Feature> which) {
		return FeatureManager.apply(text, parts, which, false);
	}
}
