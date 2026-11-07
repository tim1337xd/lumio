package dev.tim1337.lumio.api.setting;

/** An on/off setting. */
public interface Toggle extends AddonSetting<Boolean> {
	/** How the setting is drawn. */
	enum Style {
		/** A switch to the right of the label. */
		SWITCH,
		/** A checkbox, as in lists of things to show. */
		CHECKBOX
	}
}
