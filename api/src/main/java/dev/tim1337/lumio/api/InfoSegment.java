package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.setting.AddonSetting;

import java.util.List;

/**
 * A kind of segment an add-on adds to the info bar, listed in the bar's segment list like its own ones. The bar draws it as its icon and a text,
 * which the add-on's {@link InfoValueProvider} fills.
 *
 * @param id       a unique id, saved in the player's segment list
 * @param title    its name in the segment list
 * @param label    its short name in the stacked style, left of the value
 * @param icon     one of the mod's icons by name
 * @param iconColor the color of its icon as 0xAARRGGBB, made readable on the bar; 0 for the usual faint white
 * @param digits   whether its text is a number whose digits roll when it changes (a purse), rather than words
 * @param widest   the widest its text is drawn, in GUI pixels ({@link #TEXT_WIDTH} for most); longer texts are shortened
 * @param after    the id of the segment it is listed after (a main one such as "clock" or "coords", or another of the add-on's)
 * @param settings its own options, shown when the player clicks it in the list; may be empty
 */
public record InfoSegment(String id, String title, String label, String icon, int iconColor, boolean digits, float widest, String after,
	List<? extends AddonSetting<?>> settings) {
	/** The width most text segments are drawn at, like the bar's Area segment. */
	public static final float TEXT_WIDTH = 110f;

	public InfoSegment {
		settings = List.copyOf(settings);
	}
}
