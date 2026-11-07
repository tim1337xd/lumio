package dev.tim1337.lumio.api.feature;

/** A feature as the API shows it to providers: a feature of the main mod or of an add-on. Compare by identity or by id. */
public interface FeatureView {
	/** @return its id, also its section in the settings file */
	String id();

	/** @return its name as plain text */
	String nameText();

	boolean isEnabled();
}
