package dev.tim1337.lumio.api.setting;

/** A row with a button that does something when clicked. */
public interface Action extends AddonSetting<Void> {
	/** Asks before the action runs. @return this */
	Action confirm();
}
