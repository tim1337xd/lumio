package dev.tim1337.lumio.features.misc;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;

import net.minecraft.network.chat.Component;

/** A simple switch: when enabled, the mod writes extra diagnostic information to the game log, to help find out why something does not work. */
public class DebugFeature extends Feature {
	private final BooleanSetting profile = add(new BooleanSetting("record_profile", Component.literal("Record a performance profile"), false), "Debug",
		"Saved to .minecraft/lumio/profiles when turned off");

	private final BooleanSetting coloredLog = add(new BooleanSetting("log_colored", Component.literal("Log the Colored colors"), false), null,
		"Once per second: how far they moved, and what moved them");

	private final BooleanSetting swingTrace = add(new BooleanSetting("swing_trace", Component.literal("Show the swing values"), false), null,
		"Every frame at the top left, and in the log while you attack");

	public DebugFeature() {
		super("debug_log", "Debug log", "Writes extra information to the log of the game: what the server sends above the hotbar, the lyrics that were found, and where the window was put.", false);
		iconRgb(SystemColors.GRAY_RGB);
		iconName("terminal");
		summary("Writes extra details to the game log");
	}

	/** @return whether the Colored theme's field writes what moves it to the log */
	public boolean logsColored() {
		return isEnabled() && coloredLog.get();
	}

	/** @return whether the first person swing's values are shown on the screen and written to the log, frame by frame */
	public boolean tracesSwing() {
		return isEnabled() && swingTrace.get();
	}

	/** @return whether a JFR profile should be recorded right now: the debug log and the profile switch are both on */
	public boolean recordsProfile() {
		return isEnabled() && profile.get();
	}

	@Override
	public Feature homePage() {
		return ModFeatures.MENU;
	}
}
