package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.controls.PreviewControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/**
 * A live preview inside a page, above the settings it shows, such as the info bar above its segment list. It has no value and is not
 * persisted; the preview draws whatever the settings are right now.
 */
public class PreviewSetting extends Setting<Void> {
	private final PagePreview preview;
	private final int height;

	public PreviewSetting(String key, Component label, int height, PagePreview preview) {
		super(key, label, null);
		this.preview = preview;
		this.height = height;
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
		return new PreviewControl(preview, height);
	}
}
