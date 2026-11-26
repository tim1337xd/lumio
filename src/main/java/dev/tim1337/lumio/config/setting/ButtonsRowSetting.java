package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.experimental.ui.SettingRows;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ButtonsRowControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * A row of a settings sheet with several buttons: its title and a line under it, an optional choice as a small segmented control, and the buttons
 * (read when drawn, so they may change). Highlighted, it stands out in the accent. A button's action may ask a question
 * ({@link SettingRows#ask}), which the row then shows under itself until it is answered. It has no value.
 */
public class ButtonsRowSetting extends Setting<Void> {
	/** The add-on API's rows, made here. */
	public static final SettingRows.Provider PROVIDER = new SettingRows.Provider() {
		@Override
		public AddonSetting<Void> buttons(String key, Component label, Supplier<String> subtext, boolean highlighted, Choice<?> inline,
			Supplier<List<SettingRows.Button>> buttons) {
			return new ButtonsRowSetting(key, label, subtext, highlighted, inline instanceof EnumSetting<?> choice ? choice : null, buttons);
		}

		@Override
		public void colors(MultiToggle chips, int[] rgb) {
			if (chips instanceof MultiSelectSetting multi) {
				multi.colors(rgb);
			}
		}

		@Override
		public void ask(SettingRows.Question question) {
			ButtonsRowControl.ask(question);
		}
	};

	private final Supplier<String> subtext;
	private final boolean highlighted;
	private final EnumSetting<?> inline;
	private final Supplier<List<SettingRows.Button>> buttons;

	public ButtonsRowSetting(String key, Component label, Supplier<String> subtext, boolean highlighted, EnumSetting<?> inline,
		Supplier<List<SettingRows.Button>> buttons) {
		super(key, label, null);
		this.subtext = subtext;
		this.highlighted = highlighted;
		this.inline = inline;
		this.buttons = buttons;
	}

	public String subtext() {
		String text = subtext == null ? null : subtext.get();
		return text == null ? "" : text;
	}

	public boolean highlighted() {
		return highlighted;
	}

	/** @return the choice shown in the row, or null */
	public EnumSetting<?> inline() {
		return inline;
	}

	public List<SettingRows.Button> buttons() {
		List<SettingRows.Button> list = buttons == null ? null : buttons.get();
		return list == null ? List.of() : list;
	}

	@Override
	public void reset() {
	}

	@Override
	public boolean isDefault() {
		return true;
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
		return new ButtonsRowControl(this, onChanged);
	}
}
