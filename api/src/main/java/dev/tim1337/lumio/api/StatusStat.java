package dev.tim1337.lumio.api;

/** The values a {@link StatusValueProvider} can give the status bars, beyond the game's own health, hunger and experience. */
public enum StatusStat {
	/** Health, its maximum; more than the maximum is shown as absorption. */
	HEALTH,
	/** Mana and its maximum. */
	MANA,
	/** Mana beyond the maximum (only the value). */
	OVERFLOW_MANA,
	/** Defense (only the value), shown on the health bar. */
	DEFENSE,
	/** Vitality and its maximum. */
	VITALITY,
	/** Drill fuel and its maximum. */
	FUEL
}
