package dev.tim1337.lumio.api.spi;

import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.api.setting.SettingFactory;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** What the running Lumio provides to the API classes. Implemented by Lumio only; it grows with the API. */
public interface Services {
	/** @return the factory of settings, which makes the main mod's own setting types */
	SettingFactory settings();

	/** @return the maker of the add-ons' features, panels and pages */
	FeatureBackend features();

	/** @return the look of the HUD panels */
	HudTheme.Provider hud();

	/** @return the settings rows of {@link dev.tim1337.lumio.api.experimental.ui.SettingRows} */
	dev.tim1337.lumio.api.experimental.ui.SettingRows.Provider rows();

	/** @return drawing in the world */
	WorldDraw.Provider world();

	/** @return a canvas in the HUD panels' look */
	Canvas hudCanvas(GuiGraphicsExtractor graphics, int accent, float deltaSeconds, boolean animated);

	/** @return the width of text at a scale, as a canvas draws it */
	float measureScaled(String text, float scale);

	boolean animations();

	float bounce();

	int accentRgb();

	/** @return the two colors the name in a "[Lumio]" chat line runs between, readable on the dark chat */
	default int[] chatNameColors() {
		int accent = accentRgb();
		return new int[] {accent, accent};
	}

	/** Adds a rule of an add-on to the Visual Filter ({@link dev.tim1337.lumio.api.experimental.filter.VisualFilters}); nothing without it. */
	default void visualFilter(String key, String label, String description, String section, boolean on,
		dev.tim1337.lumio.api.experimental.filter.VisualFilters.Rule rule) {
	}

	boolean inventoryStyled();

	int[] infoBarBounds();

	void toast(String title, String text);

	void briefToast(String icon, String title, String text, float seconds);

	/** Opens the menu directly on a feature's settings, over the screen now open. */
	void openSettings(dev.tim1337.lumio.api.feature.AddonFeature feature);

	/** Opens the menu on its Keybinds page, where a key without a default is set. */
	default void openKeybinds() {
	}

	void click();

	boolean hudEditorOpen();

	/** Draws something over the HUD editor's own marks this frame (see {@link dev.tim1337.lumio.api.experimental.hud.HudEditor#drawOnTop}). */
	void hudDrawOnTop(java.util.function.Consumer<dev.tim1337.lumio.api.draw.Canvas> drawing);

	java.util.List<int[]> hudBoxes();

	/** @return a snapper with the HUD editor's grid and logic */
	dev.tim1337.lumio.api.experimental.hud.GridSnap gridSnap();

	/** Draws the HUD editor's grid over a screen, as while an element is dragged there. */
	void drawHudGrid(dev.tim1337.lumio.api.draw.Canvas canvas, int screenWidth, int screenHeight);

	/** @return the look set for all waypoints, as it is now */
	default dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle waypointStyle() {
		return dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle.DEFAULT;
	}

	/** @return the waypoints' place key, or null before it is registered */
	default net.minecraft.client.KeyMapping waypointPlaceKey() {
		return null;
	}

	/** @return a key mapping's keys as shown, a combination such as "Tab + R"; empty while unbound */
	default String keyText(net.minecraft.client.KeyMapping mapping) {
		return mapping.isUnbound() ? "" : mapping.getTranslatedKeyMessage().getString();
	}

	void saveSoon();

	void simulate(HudSimulator simulator, String simulationId, int option);
}
