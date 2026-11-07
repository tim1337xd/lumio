package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.menu.FeatureListFeature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.PageTilesControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Pages of a page's groups, shown as tiles on it that lead to them. It has no value. */
public class PageTilesSetting extends Setting<Void> {
	private final List<FeatureListFeature> pages;

	public PageTilesSetting(String key, Component label, List<FeatureListFeature> pages) {
		super(key, label, null);
		this.pages = pages;
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
		return new PageTilesControl(pages);
	}
}
