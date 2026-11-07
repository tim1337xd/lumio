package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.PanelLook;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.api.setting.SettingFactory;
import dev.tim1337.lumio.api.spi.FeatureBackend;
import dev.tim1337.lumio.api.spi.Services;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.hud.HudSimulation;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.config.setting.PreviewSetting;
import dev.tim1337.lumio.ui.PanelTheme;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** What the main mod provides to the add-on API (see {@link Services}); found by the API through META-INF/services. */
public final class MainServices implements Services {
	/** The bounce before the menu settings exist, the same as their default. */
	private static final float DEFAULT_BOUNCE = 0.5f;

	/** Made by {@link java.util.ServiceLoader}. */
	public MainServices() {
	}

	@Override
	public boolean debugOn() {
		return ModFeatures.DEBUG != null && ModFeatures.DEBUG.isEnabled();
	}

	@Override
	public SettingFactory settings() {
		return MainSettingFactory.INSTANCE;
	}

	@Override
	public FeatureBackend features() {
		return Adapters.INSTANCE;
	}

	@Override
	public WorldDraw.Provider world() {
		return MainWorldDraw.INSTANCE;
	}

	@Override
	public HudTheme.Provider hud() {
		return HUD_THEME;
	}

	@Override
	public Canvas hudCanvas(GuiGraphicsExtractor graphics, int accent, float deltaSeconds, boolean animated) {
		return new Ui(graphics, Minecraft.getInstance().font, HudStyle.palette(), accent, deltaSeconds, animated);
	}

	@Override
	public float measureScaled(String text, float scale) {
		return Ui.measureScaled(Minecraft.getInstance().font, text, scale);
	}

	@Override
	public boolean animations() {
		return ModFeatures.MENU == null || ModFeatures.MENU.animations();
	}

	@Override
	public float bounce() {
		return ModFeatures.MENU == null ? DEFAULT_BOUNCE : ModFeatures.MENU.bounce();
	}

	@Override
	public int accentRgb() {
		return MenuFeature.accentRgb();
	}

	@Override
	public boolean inventoryStyled() {
		return ModFeatures.INVENTORY_SCREEN != null && ModFeatures.INVENTORY_SCREEN.isEnabled();
	}

	@Override
	public int[] infoBarBounds() {
		var bar = ModFeatures.INFO_BAR;
		return bar != null && bar.isEnabled() && bar.hudShown() ? bar.hudBounds() : null;
	}

	@Override
	public void toast(String title, String text) {
		Toasts.info(title, text);
	}

	@Override
	public void briefToast(String icon, String title, String text, float seconds) {
		Toasts.brief(icon, title, text, seconds);
	}

	@Override
	public void openSettings(dev.tim1337.lumio.api.feature.AddonFeature feature) {
		dev.tim1337.lumio.config.Feature own = Adapters.featureOf(feature);
		if (own != null) {
			dev.tim1337.lumio.ui.MacScreen.openFrom(own);
		}
	}

	@Override
	public void click() {
		UiSounds.click();
	}

	@Override
	public void simulate(HudSimulator simulator, String simulationId, int option) {
		for (HudSimulation.Entry entry : HudSimulation.entries()) {
			if (entry.simulator() == simulator && entry.simulation().id().equals(simulationId)) {
				HudSimulation.start(entry, option);
				return;
			}
		}
	}

	@Override
	public void saveSoon() {
		FeatureManager.saveSoon();
	}

	@Override
	public java.util.List<int[]> hudBoxes() {
		java.util.List<int[]> boxes = new java.util.ArrayList<>();
		for (dev.tim1337.lumio.ui.EditableHud element : dev.tim1337.lumio.ModFeatures.editableHuds()) {
			if (element.hudShown()) {
				boxes.add(element.hudBounds());
			}
		}
		return boxes;
	}

	@Override
	public boolean hudEditorOpen() {
		return HudEditScreen.isOpen();
	}

	/** The HUD panels' look, from the main mod's panel style. */
	private static final HudTheme.Provider HUD_THEME = new HudTheme.Provider() {
		@Override
		public int line(float opacity) {
			return HudStyle.line(opacity);
		}

		@Override
		public int text() {
			return HudStyle.text();
		}

		@Override
		public int secondary() {
			return HudStyle.secondary();
		}

		@Override
		public int slot() {
			return HudStyle.slot();
		}

		@Override
		public float opacity() {
			return HudStyle.opacity();
		}

		@Override
		public int readable(int rgb) {
			return HudStyle.readable(rgb);
		}

		@Override
		public boolean blurBehind(Canvas canvas, int width, int height, float blur, float fade) {
			return dev.tim1337.lumio.ui.Glass.blurBehind((dev.tim1337.lumio.ui.Ui) canvas, width, height, blur, fade);
		}

		@Override
		public void panel(Canvas canvas, int x, int y, int width, int height, float radius, float opacity) {
			HudStyle.panel((Ui) canvas, x, y, width, height, radius, opacity);
		}

		@Override
		public void panel(Canvas canvas, int x, int y, int width, int height, float radius, float opacity, boolean glow) {
			HudStyle.panel((Ui) canvas, x, y, width, height, radius, opacity, glow);
		}

		@Override
		public void withLook(PanelLook look, Runnable draw) {
			HudStyle.setPanelTheme(PanelTheme.valueOf(look.name()).themeOrHud());
			try {
				draw.run();
			} finally {
				HudStyle.setPanelTheme(null);
			}
		}

		@Override
		public AddonSetting<Void> preview(String key, Component label, int height, HudTheme.PreviewDrawer drawer) {
			return new PreviewSetting(key, label, height, (ui, x, y, width, previewHeight) -> drawer.draw(ui.graphics, ui, x, y, width, previewHeight));
		}
	};
}
