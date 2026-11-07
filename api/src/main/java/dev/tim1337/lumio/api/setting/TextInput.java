package dev.tim1337.lumio.api.setting;

import java.util.function.UnaryOperator;

/** A line of text. A secret one is masked, never shared and never logged. */
public interface TextInput extends AddonSetting<String> {
	/** Cleans what is typed before it is kept, such as trimming it. @return this */
	TextInput cleaned(UnaryOperator<String> cleaner);
}
