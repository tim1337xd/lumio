package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.SkyPreviewControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** The Sky page's live preview at its top; it has no value and is not saved. */
final class SkyPreviewSetting extends Setting<Void> {
	private final SkyFeature sky;

	SkyPreviewSetting(SkyFeature sky) {
		super("sky_preview", Component.literal("Preview"), null);
		this.sky = sky;
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
		return new SkyPreviewControl(sky);
	}
}
