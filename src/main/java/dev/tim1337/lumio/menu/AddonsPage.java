package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.util.ModVersion;
import dev.tim1337.lumio.addon.AddonSwitches;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.InfoSetting;
import dev.tim1337.lumio.config.setting.NoteSetting;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Add-ons page of the menu settings: every add-on that was found, with its version, the API versions it works with, what it adds and a switch,
 * and the API version of the main mod. An add-on is loaded or left out while the game starts, so a switch takes effect after a restart.
 */
final class AddonsPage {
	private static final String TITLE = "Add-ons";
	private static final String GROUP_API = " ";
	// untitled groups: a title of one or two spaces, as on the Music page
	private static final String GROUP_NOTE = "  ";
	private static final String RUNNING = "Running";
	private static final String RESTART_NOTE = "Switching an add-on on or off takes effect after a restart.";
	private static final String NONE_NOTE = "No add-ons are installed.";

	private static SettingsView view;

	private AddonsPage() {
	}

	/** @return the page, made the first time it is opened: the API version, then a group for every add-on that was found */
	static SettingsView view() {
		if (view == null) {
			List<SettingsView.Group> groups = new ArrayList<>();
			groups.add(new SettingsView.Group(GROUP_API, List.of(new InfoSetting("addons_api", Component.literal("Add-on API"), () -> LumioApi.VERSION))));
			List<AddonInfo> found = new ArrayList<>(Addons.loaded());
			found.addAll(Addons.disabled());
			for (AddonInfo addon : found) {
				groups.add(new SettingsView.Group(addon.name(), rows(addon)));
			}
			groups.add(new SettingsView.Group(GROUP_NOTE, List.of(new NoteSetting("addons_note", found.isEmpty() ? NONE_NOTE : RESTART_NOTE))));
			view = new SettingsView("addons", TITLE, "", "cube", SystemColors.GRAY_RGB, groups);
		}
		return view;
	}

	/** @return an add-on's rows: what it adds, its switch, its version, the API versions it needs, and whether it runs */
	private static List<Setting<?>> rows(AddonInfo addon) {
		String reason = Addons.whyLeftOut(addon.id());
		String key = "addon_" + addon.id();
		return List.of(
			new NoteSetting(key + "_about", addon.description()),
			new AddonSwitch(addon),
			new InfoSetting(key + "_version", Component.literal("Version"), () -> ModVersion.shownAs(addon.version())),
			new InfoSetting(key + "_api", Component.literal("Needs API"), addon::apiRange),
			new InfoSetting(key + "_status", Component.literal("Status"), () -> reason == null ? RUNNING : reason));
	}

	/** The switch of one add-on: saved in the add-ons' own state file rather than in the settings, read once at the start. */
	private static final class AddonSwitch extends BooleanSetting {
		private final String id;

		AddonSwitch(AddonInfo addon) {
			super("addon_" + addon.id(), Component.literal("Turned on"), AddonSwitches.enabled(addon.id()));
			this.id = addon.id();
			hideFromRecent();
		}

		@Override
		public void set(Boolean value) {
			super.set(value);
			if (id != null) {
				AddonSwitches.setEnabled(id, value);
			}
		}

		@Override
		public JsonElement save() {
			return null;
		}

		@Override
		public void load(JsonElement json) {
		}
	}
}
