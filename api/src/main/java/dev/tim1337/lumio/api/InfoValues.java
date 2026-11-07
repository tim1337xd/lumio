package dev.tim1337.lumio.api;

/** Where an {@link InfoValueProvider} puts the values of its info bar segments. */
public interface InfoValues {
	/** Shows a segment with this text; an unchanged text costs nothing. */
	void set(String segment, String text);

	/** Hides a segment until it has a value again. */
	void none(String segment);
}
