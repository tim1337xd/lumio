package dev.tim1337.lumio.api.setting;

import java.util.List;

/** Several switches drawn as one setting: a list of checkboxes, or chips. */
public interface MultiToggle extends AddonSetting<Void> {
	/** @return its switches */
	List<? extends Toggle> options();

	/** Draws the switches as chips in a row. @return this */
	MultiToggle asChips();
}
