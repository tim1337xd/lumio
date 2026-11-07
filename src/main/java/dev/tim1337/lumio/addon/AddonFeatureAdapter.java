package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.render.WorldBackdrop;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.spi.FeatureHandle;
import dev.tim1337.lumio.api.experimental.draw.PanelLook;
import dev.tim1337.lumio.api.experimental.hud.StyledFeature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.PanelTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonObject;

import net.minecraft.client.KeyMapping;

import java.util.function.Consumer;

/**
 * An add-on's feature as the main mod keeps it: a {@link Feature} with the add-on's settings, which hands its hooks (saving, loading, how its
 * page opens) to the add-on's {@link AddonFeature}.
 */
final class AddonFeatureAdapter extends Feature {
	private final AddonFeature addon;
	final FeatureHandle handle = new Handle();

	AddonFeatureAdapter(AddonFeature addon, String id, String name, String description, boolean enabledByDefault, boolean hasSwitch) {
		super(id, name, description, enabledByDefault, hasSwitch);
		this.addon = addon;
	}

	AddonFeature addon() {
		return addon;
	}

	/** The add-on's preview, on the same background as the others. */
	private final PagePreview addonPreview = new PagePreview() {
		@Override
		public void draw(Ui ui, int x, int y, int width, int height) {
			WorldBackdrop.draw(ui, x, y, width, height, Radius.LG);
			addon.drawPreview(ui, x, y, width, height);
		}

		@Override
		public boolean tall() {
			return true;
		}

		@Override
		public String note() {
			return addon.previewNote();
		}
	};

	@Override
	public PagePreview preview() {
		return outline() != null && addon.hasPreview() ? addonPreview : null;
	}

	@Override
	public boolean copiesStyle() {
		return addon instanceof StyledFeature;
	}

	@Override
	public void copyStyleToOthers() {
		if (!(addon instanceof StyledFeature styled)) {
			return;
		}
		PanelTheme theme = PanelTheme.valueOf(styled.look().name());
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof HudPanelFeature panel) {
				panel.takeTheme(theme);
			} else if (feature != this) {
				feature.takePanelTheme(theme);
			}
		}
		styled.styleCopied();
	}

	@Override
	public void takePanelTheme(PanelTheme theme) {
		if (addon instanceof StyledFeature styled) {
			styled.takeLook(PanelLook.valueOf(theme.name()));
		}
	}

	@Override
	public JsonObject save() {
		return addon.save();
	}

	@Override
	public void load(JsonObject json) {
		addon.load(json);
	}

	@Override
	public boolean ownWindow() {
		return addon.ownWindow();
	}

	@Override
	public boolean alwaysTabbed() {
		return addon.alwaysTabbed();
	}

	/** Adds an add-on's setting, with its section and description when given. */
	static void addTo(AddonSetting<?> setting, String section, String description, Consumer<Setting<?>> add) {
		Setting<?> own = (Setting<?>) setting;
		if (section != null) {
			own.setSection(section);
		}
		if (description != null) {
			own.setDescription(description);
		}
		add.accept(own);
	}

	/** @return the main mod's place of a key */
	static KeySetting.Where whereOf(KeyPlace place) {
		return place == KeyPlace.SCREENS ? KeySetting.Where.SCREENS : KeySetting.Where.WORLD;
	}

	/** The add-on's side calls the feature through this. */
	private final class Handle implements FeatureHandle {
		@Override
		public boolean isEnabled() {
			return AddonFeatureAdapter.this.isEnabled();
		}

		@Override
		public void setEnabled(boolean on) {
			AddonFeatureAdapter.this.setEnabled(on);
		}

		@Override
		public void add(AddonSetting<?> setting, String section, String description) {
			addTo(setting, section, description, AddonFeatureAdapter.this::add);
		}

		@Override
		public void iconRgb(int rgb) {
			AddonFeatureAdapter.this.iconRgb(rgb);
		}

		@Override
		public void iconName(String name) {
			AddonFeatureAdapter.this.iconName(name);
		}

		@Override
		public void addChild(AddonFeature child) {
			AddonFeatureAdapter.this.addChild(Adapters.featureOf(child));
		}

		@Override
		public void addKey(KeyMapping mapping, KeyPlace place) {
			AddonFeatureAdapter.this.addKey(mapping, whereOf(place));
		}

		@Override
		public void summary(String text) {
			AddonFeatureAdapter.this.summary(text);
		}

		@Override
		public void addResetButton() {
			AddonFeatureAdapter.this.addResetButton();
		}

		@Override
		public JsonObject saveSettings() {
			return AddonFeatureAdapter.super.save();
		}

		@Override
		public void loadSettings(JsonObject json) {
			AddonFeatureAdapter.super.load(json);
		}
	}
}
