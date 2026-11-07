package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.setting.AddonSetting;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Picks and orders the scoreboard's lines in an add-on's world (SkyBlock's custom scoreboard), and adds its settings to the scoreboard page.
 * Asked every time the scoreboard changes, not every frame.
 */
public interface ScoreboardProvider {
	/** @return whether it picks the lines now */
	boolean active();

	/** @return the scoreboard's title to show, or null for the server's */
	Component title();

	/** @return the lines to show for the server's sidebar lines (top to bottom); an empty line is a gap */
	List<Component> lines(List<Component> sidebar);

	/** @return the heading of its settings on the scoreboard page ("Lines on SkyBlock") */
	String settingsHeading();

	/** @return its settings on the scoreboard page */
	List<? extends AddonSetting<?>> settings();
}
