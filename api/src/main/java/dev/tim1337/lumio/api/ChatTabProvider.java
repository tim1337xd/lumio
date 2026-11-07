package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.setting.AddonSetting;

import java.util.List;

/** A tab of the chat that an add-on brings. It only picks which messages it shows; every message stays as the game sends it. */
public interface ChatTabProvider {
	/** @return a short id, unique among the tabs; it is saved with the order of the tabs */
	String tabId();

	/** @return the tab's name */
	String title();

	/** @return whether a message belongs in this tab, from its plain text; asked once per message, on the game's thread, so it must be quick */
	boolean matches(String plainText);

	/** @return whether the messages of this tab show only here and not under All, as the player chose */
	default boolean keptOutOfAll() {
		return false;
	}

	/** @return the settings of this tab, shown in the chat's card with the tabs; they are saved with the add-on's feature */
	default List<AddonSetting<?>> tabSettings() {
		return List.of();
	}
}
