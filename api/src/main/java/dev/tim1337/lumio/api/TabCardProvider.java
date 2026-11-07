package dev.tim1337.lumio.api;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Reads the info columns of an add-on's world from the tab list (the entries that are not players, such as SkyBlock's "Area", "Skills" and
 * "Stats" sections) and turns them into cards. Without one, the tab list shows the players only.
 */
public interface TabCardProvider {
	/** @return whether the text entries of the tab list are this world's info now, to be read as cards instead of listed as players */
	boolean active();

	/** @return the cards of the info lines, in order; asked only while the tab list is open and its lines changed */
	List<TabCard> cards(List<Component> lines);
}
