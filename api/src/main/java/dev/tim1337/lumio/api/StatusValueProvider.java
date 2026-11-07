package dev.tim1337.lumio.api;

import net.minecraft.network.chat.Component;

/**
 * Gives the status bars the values of an add-on's world, read from the action bar the server sends (such as SkyBlock's health, mana, defense,
 * vitality and drill fuel). Without one, the bars show the game's own health, hunger and experience. When several add-ons give values, the one
 * with the highest priority is used. The bars read the values every frame, so they must be kept, not computed when asked.
 */
public interface StatusValueProvider {
	/** @return its priority; the highest is used */
	int priority();

	/** @return whether this action bar text is one of its world, so the bars take their values from it */
	boolean owns(Component actionBar);

	/**
	 * Reads an action bar text as the server sent it, before other mods change it.
	 *
	 * @param wantHealth whether the bars want the world's own health (else the game's)
	 * @param wantMana   whether the bars show mana
	 */
	void observe(Component actionBar, boolean wantHealth, boolean wantMana);

	/**
	 * @param takeOut whether the action bar panel takes parts out at all
	 * @param remove  the parts to take out, as {@link ActionBarParts} bits
	 * @return the action bar text to show, without the parts the bars show
	 */
	Component filter(Component actionBar, boolean wantHealth, boolean wantMana, boolean takeOut, int remove);

	/** @return whether a recent value of the stat is known */
	boolean fresh(StatusStat stat);

	/** @return the stat's current value */
	int value(StatusStat stat);

	/** @return the stat's maximum, or 0 where it has none */
	int max(StatusStat stat);
}
