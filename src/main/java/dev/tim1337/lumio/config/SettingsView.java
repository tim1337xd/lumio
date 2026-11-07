package dev.tim1337.lumio.config;

import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A sub-page over a part of a feature's settings, in groups of its own, such as the music settings the music page keeps out of sight or a HUD
 * element's More. It is not a feature of its own: the settings belong to their feature and are saved there, so this saves nothing.
 */
public final class SettingsView extends Feature {
	/** A group of the window: its title and its settings, in order. */
	public record Group(String title, List<Setting<?>> settings) {
	}

	/** @return a group of settings, also of settings an add-on made through the API */
	public static Group group(String title, List<? extends AddonSetting<?>> settings) {
		List<Setting<?>> own = new ArrayList<>(settings.size());
		for (AddonSetting<?> setting : settings) {
			own.add((Setting<?>) setting);
		}
		return new Group(title, own);
	}

	private final List<Setting<?>> shown = new ArrayList<>();
	private final Map<Setting<?>, String> groups = new IdentityHashMap<>();

	public SettingsView(String id, String name, String description, String icon, int rgb, List<Group> content) {
		super(id, name, description, true, false);
		iconName(icon);
		iconRgb(rgb);
		for (Group group : content) {
			for (Setting<?> setting : group.settings()) {
				shown.add(setting);
				groups.put(setting, group.title());
			}
		}
	}

	private boolean sheet;

	/** Makes it lie over its page as a sheet. */
	public SettingsView asSheet() {
		sheet = true;
		return this;
	}

	@Override
	public boolean isSheet() {
		return sheet;
	}

	@Override
	public boolean compactRows() {
		return sheet;
	}

	@Override
	public List<Setting<?>> settings() {
		return shown;
	}

	@Override
	public String sectionOf(Setting<?> setting) {
		return groups.get(setting);
	}

	@Override
	public JsonObject save() {
		return new JsonObject();
	}

	@Override
	public void load(JsonObject json) {
	}
}
