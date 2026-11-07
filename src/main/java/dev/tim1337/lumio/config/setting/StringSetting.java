package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.TextFieldControl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;

import java.util.function.UnaryOperator;

/** A short text such as a token, typed into a field. The value is hidden while the field is not being edited. */
public class StringSetting extends Setting<String> implements TextInput {
	private final boolean secret;
	/** Cleans what is typed or loaded, such as a player name to the characters a name can have. */
	private UnaryOperator<String> cleaner = UnaryOperator.identity();

	public StringSetting(String key, Component label, String defaultValue, boolean secret) {
		super(key, label, defaultValue);
		this.secret = secret;
	}

	/** Cleans every value set from now on, typed or loaded. @return this setting */
	public StringSetting cleaned(UnaryOperator<String> cleaner) {
		this.cleaner = cleaner;
		return this;
	}

	@Override
	public void set(String value) {
		super.set(cleaner.apply(value));
	}

	public boolean secret() {
		return secret;
	}

	// a secret (a token) is never shown
	@Override
	public String valueText() {
		return secret || get().isEmpty() ? null : get();
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(get());
	}

	@Override
	public void load(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new TextFieldControl(this, onChanged);
	}
}
