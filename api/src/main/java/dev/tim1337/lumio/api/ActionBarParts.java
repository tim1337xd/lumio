package dev.tim1337.lumio.api;

/** The parts of an action bar text the action bar panel can hide, as bits for {@link StatusValueProvider#filter}. */
public final class ActionBarParts {
	public static final int HEALTH = 1;
	public static final int MANA = 2;
	public static final int DEFENSE = 4;
	public static final int OVERFLOW = 8;
	/** Anything else the status bars do not show. */
	public static final int OTHER = 16;
	public static final int VITALITY = 32;
	public static final int LOCATION = 64;
	public static final int FUEL = 128;
	/** What is hidden when nothing else is chosen: what the bars show anyway. */
	public static final int USUAL = HEALTH | MANA | DEFENSE | OVERFLOW;

	private ActionBarParts() {
	}
}
