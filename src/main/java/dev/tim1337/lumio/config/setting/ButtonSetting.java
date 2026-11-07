package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ButtonControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** A button that runs an action when pressed. It has no value and is not persisted. */
public class ButtonSetting extends Setting<Void> implements Action {
	private final Component buttonText;
	private final Runnable action;
	private final boolean destructive;

	private boolean confirm;

	/** Makes the button ask for confirmation ("Sure?") first, for actions that cannot be undone. */
	public ButtonSetting confirm() {
		confirm = true;
		return this;
	}

	/**
	 * @param label       row label, shown on the left
	 * @param buttonText  text on the button
	 * @param destructive whether the action deletes something; the button is colored accordingly
	 */
	public ButtonSetting(String key, Component label, Component buttonText, boolean destructive, Runnable action) {
		super(key, label, null);
		this.buttonText = buttonText;
		this.destructive = destructive;
		this.action = action;
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	@Override
	public Control createControl(Runnable onChanged) {
		ButtonControl control = new ButtonControl(buttonText, destructive, () -> {
			action.run();
			onChanged.run();
		});
		return confirm ? control.confirm() : control;
	}
}
