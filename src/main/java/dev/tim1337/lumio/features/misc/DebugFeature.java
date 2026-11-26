package dev.tim1337.lumio.features.misc;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;

/**
 * The "Debug log" switch in About: while it is on, the probes of Lumio and its add-ons write their lines to {@code logs/lumio-debug.log}
 * ({@link dev.tim1337.lumio.util.DebugLogFile}), so a player can send it with a bug report. It does nothing else; the tools for testing and
 * measuring are in the private debug add-on.
 */
public class DebugFeature extends Feature {
	public DebugFeature() {
		super("debug_log", "Debug log", "Extra details for bug reports.", false);
		iconRgb(SystemColors.GRAY_RGB);
		iconName("terminal");
		summary("Extra details for bug reports");
	}

	@Override
	public Feature homePage() {
		return ModFeatures.MENU;
	}
}
