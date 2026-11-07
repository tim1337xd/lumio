package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.feature.PanelPicture;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.api.spi.PanelHandle;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.Ui;

import com.google.gson.JsonObject;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An add-on's HUD panel as the main mod keeps it: a {@link HudPanelFeature}, placed, sized and styled like the main mod's panels, which hands
 * its measuring and drawing to the add-on's {@link AddonPanel}.
 */
final class AddonPanelAdapter extends HudPanelFeature {
	private final AddonPanel addon;
	final PanelHandle handle = new Handle();

	AddonPanelAdapter(AddonPanel addon, String id, String name, String description, boolean enabledByDefault, AddonPanel.Anchor anchor,
		boolean fromBottom, float defaultX, float defaultY) {
		super(id, name, description, enabledByDefault, switch (anchor) {
			case LEFT -> Anchor.LEFT;
			case CENTER -> Anchor.CENTER;
			case RIGHT -> Anchor.RIGHT;
		}, fromBottom, defaultX, defaultY);
		this.addon = addon;
	}

	AddonPanel addon() {
		return addon;
	}

	@Override
	protected int panelWidth() {
		return addon.panelWidth();
	}

	@Override
	protected int panelHeight() {
		return addon.panelHeight();
	}

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		addon.drawPanel(ui, graphics, client);
	}

	@Override
	protected void prepare(Minecraft client) {
		addon.prepare(client);
	}

	@Override
	protected boolean shown(Minecraft client) {
		return addon.shown(client);
	}

	@Override
	protected boolean inEditorWhenHidden() {
		return addon.inEditorWhenHidden();
	}

	@Override
	public float hudRadius() {
		return addon.hudRadius();
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

	/** @return the groups of a panel's "More" page as the menu keeps them */
	private static List<SettingsView.Group> groups(List<SettingGroup> groups) {
		List<SettingsView.Group> own = new ArrayList<>(groups.size());
		for (SettingGroup group : groups) {
			own.add(SettingsView.group(group.title(), group.settings()));
		}
		return own;
	}

	/** The add-on's side calls the panel through this. */
	private final class Handle implements PanelHandle {
		@Override
		public boolean isEnabled() {
			return AddonPanelAdapter.this.isEnabled();
		}

		@Override
		public void setEnabled(boolean on) {
			AddonPanelAdapter.this.setEnabled(on);
		}

		@Override
		public void add(AddonSetting<?> setting, String section, String description) {
			AddonFeatureAdapter.addTo(setting, section, description, AddonPanelAdapter.this::add);
		}

		@Override
		public void iconRgb(int rgb) {
			AddonPanelAdapter.this.iconRgb(rgb);
		}

		@Override
		public void iconName(String name) {
			AddonPanelAdapter.this.iconName(name);
		}

		@Override
		public void addChild(AddonFeature child) {
			AddonPanelAdapter.this.addChild(Adapters.featureOf(child));
		}

		@Override
		public void addKey(KeyMapping mapping, KeyPlace place) {
			AddonPanelAdapter.this.addKey(mapping, AddonFeatureAdapter.whereOf(place));
		}

		@Override
		public void summary(String text) {
			AddonPanelAdapter.this.summary(text);
		}

		@Override
		public void addResetButton() {
			AddonPanelAdapter.this.addResetButton();
		}

		@Override
		public JsonObject saveSettings() {
			return AddonPanelAdapter.super.save();
		}

		@Override
		public void loadSettings(JsonObject json) {
			AddonPanelAdapter.super.load(json);
		}

		@Override
		public void offerNoBackground() {
			AddonPanelAdapter.this.offerNoBackground();
		}

		@Override
		public boolean hasBackground() {
			return AddonPanelAdapter.this.hasBackground();
		}

		@Override
		public void offerCardSize() {
			AddonPanelAdapter.this.offerCardSize();
		}

		@Override
		public int cardWidth() {
			return AddonPanelAdapter.this.cardWidth();
		}

		@Override
		public int line(int base) {
			return AddonPanelAdapter.this.line(base);
		}

		@Override
		public int accentArgb() {
			return AddonPanelAdapter.this.accentArgb();
		}

		@Override
		public void part(String name, Toggle setting, float x, float y, float width, float height) {
			AddonPanelAdapter.this.part(name, setting, x, y, width, height);
		}

		@Override
		public void drawGlass(Canvas canvas, int width, int height, float radius) {
			AddonPanelAdapter.this.drawGlass(canvas, width, height, radius);
		}

		@Override
		public boolean simulated() {
			return AddonPanelAdapter.this.simulated();
		}

		@Override
		public void drawStack(GuiGraphicsExtractor graphics, Minecraft client, ItemStack stack, int slotX, int slotY, boolean decorations, int boxX,
			int boxY, int boxWidth, int boxHeight) {
			AddonPanelAdapter.this.drawStack(graphics, client, stack, slotX, slotY, decorations, boxX, boxY, boxWidth, boxHeight);
		}

		@Override
		public <E extends Enum<E>> void offerModes(Choice<E> setting) {
			AddonPanelAdapter.this.offerModes(setting);
		}

		@Override
		public void withoutHeader() {
			AddonPanelAdapter.this.withoutHeader();
		}

		@Override
		public int contentTop() {
			return AddonPanelAdapter.this.contentTop();
		}

		@Override
		public void offerHeader() {
			AddonPanelAdapter.this.offerHeader();
		}

		@Override
		public void familyHeader(Canvas canvas, int width, String icon, int[] tile, String title, String meta, int metaColor, boolean dot) {
			AddonPanelAdapter.this.familyHeader(canvas, width, icon, tile, title, meta, metaColor, dot);
		}

		@Override
		public float frameSeconds() {
			return AddonPanelAdapter.this.frameSeconds();
		}

		@Override
		public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
			AddonPanelAdapter.this.render(graphics, deltaTracker);
		}

		@Override
		public int[] hudBounds() {
			return AddonPanelAdapter.this.hudBounds();
		}

		@Override
		public float hudScale() {
			return AddonPanelAdapter.this.hudScale();
		}

		@Override
		public boolean hudShown() {
			return AddonPanelAdapter.this.hudShown();
		}

		@Override
		public float baseHudRadius() {
			return AddonPanelAdapter.super.hudRadius();
		}

		@Override
		public boolean selected() {
			return HudEditScreen.isSelected(AddonPanelAdapter.this);
		}
	}
}
