package dev.tim1337.lumio.config.setting;

/** A row of one to a few colors that can be added and removed, each with a picker of its own. */
public interface ColorSlots {
	/** @return how many colors there are */
	int count();

	/** @return the setting of a color, for its swatch and its picker */
	ColorSetting slot(int index);

	boolean canAdd();

	boolean canRemove();

	/** Adds a color at the end. */
	void add();

	/** Removes a color; the ones after it move up. The last one stays. */
	void remove(int index);
}
