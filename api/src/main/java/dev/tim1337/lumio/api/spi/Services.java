package dev.tim1337.lumio.api.spi;

import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.api.setting.SettingFactory;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** What the running Lumio provides to the API classes. Implemented by Lumio only; it grows with the API. */
public interface Services {
	/** @return whether the Debug log switch is on */
	boolean debugOn();

	/** @return the factory of settings, which makes the main mod's own setting types */
	SettingFactory settings();

	/** @return the maker of the add-ons' features, panels and pages */
	FeatureBackend features();

	/** @return the look of the HUD panels */
	HudTheme.Provider hud();

	/** @return drawing in the world */
	WorldDraw.Provider world();

	/** @return a canvas in the HUD panels' look */
	Canvas hudCanvas(GuiGraphicsExtractor graphics, int accent, float deltaSeconds, boolean animated);

	/** @return the width of text at a scale, as a canvas draws it */
	float measureScaled(String text, float scale);

	boolean animations();

	float bounce();

	int accentRgb();

	boolean inventoryStyled();

	int[] infoBarBounds();

	void toast(String title, String text);

	void briefToast(String icon, String title, String text, float seconds);

	/** Opens the menu directly on a feature's settings, over the screen now open. */
	void openSettings(dev.tim1337.lumio.api.feature.AddonFeature feature);

	void click();

	boolean hudEditorOpen();

	java.util.List<int[]> hudBoxes();

	void saveSoon();

	void simulate(HudSimulator simulator, String simulationId, int option);
}
