package dev.tim1337.lumio.api.feature;

import dev.tim1337.lumio.api.setting.AddonSetting;

import java.util.List;

/** A titled group of settings on a page, such as a panel's "More" page. */
public record SettingGroup(String title, List<? extends AddonSetting<?>> settings) {
	public SettingGroup {
		settings = List.copyOf(settings);
	}
}
