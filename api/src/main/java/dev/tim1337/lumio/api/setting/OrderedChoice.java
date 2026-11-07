package dev.tim1337.lumio.api.setting;

import java.util.List;

/** A list of entries the player switches on or off and puts in order by dragging, such as the lines of a scoreboard. */
public interface OrderedChoice extends AddonSetting<Void> {
	/**
	 * One entry of the list.
	 *
	 * @param repeatable whether it can be added more than once (a space, a separator line)
	 */
	record Entry(String id, String title, String icon, boolean onByDefault, boolean repeatable) {
		public Entry(String id, String title, String icon, boolean onByDefault) {
			this(id, title, icon, onByDefault, false);
		}
	}

	/** Sets the order a reset returns to. @return this */
	OrderedChoice defaultLayout(List<String> layout);

	/** @return the ids of the entries that are on, in their order. The list is shared and must not be changed */
	List<String> enabledInOrder();

	/** @return the ids of all shown entries, on or off, in their order. The list is shared and must not be changed */
	List<String> rows();

	boolean isOn(String id);

	/** Adds a repeatable entry (a space, a line) before an entry, as the default layout of a group. */
	void insertBefore(String repeatableId, String beforeId);
}
