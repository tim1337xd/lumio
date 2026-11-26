package dev.tim1337.lumio.api.experimental.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets an add-on take part in Lumio's update check: it names its mod id, its GitHub repository and the start of its jars' names
 * (lumio-skyblock for lumio-skyblock-1.0.0-beta.2-26.3.jar). Lumio only takes repositories it knows; it checks only when the player turned the check
 * on or asked for it, and downloads only when the player presses Update.
 */
public final class AddonUpdates {
	/** An add-on's releases on GitHub. */
	public record Source(String modId, String owner, String repository, String jarName) {
	}

	private static final List<Source> SOURCES = new ArrayList<>();

	private AddonUpdates() {
	}

	/** Offers the add-on's releases to the update check; once, while the add-on starts. */
	public static void offer(String modId, String owner, String repository, String jarName) {
		SOURCES.add(new Source(modId, owner, repository, jarName));
	}

	/** @return the add-ons that offered their releases */
	public static List<Source> sources() {
		return List.copyOf(SOURCES);
	}
}
