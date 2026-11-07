package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.Setting;


/** A HUD element that the player can move and resize in the HUD editor. */
public interface EditableHud {
	String hudName();

	/** @return whether the element is currently on screen, which is when it can be edited */
	boolean hudShown();

	/** @return the element's bounds as last drawn: x, y, width and height in GUI pixels */
	int[] hudBounds();

	void hudMove(int dx, int dy);

	/** Shortens a move to what the element can do, for elements moved together, which keep their places to each other. */
	default void hudLimitMove(int[] move) {
	}

	/** @return the scale factor the element is drawn at */
	float hudScale();

	/** @return the smallest scale the editor allows */
	default float hudMinScale() {
		return 0.3f;
	}

	/** @return the largest scale the editor allows */
	default float hudMaxScale() {
		return 3f;
	}

	/** Sets the scale, keeping the element's center in place. */
	void hudSetScale(float scale);

	/** Sets the scale from the mouse wheel or the keys: the element grows and shrinks around the place it is anchored at, so it does not jump. */
	default void hudSetScaleAnchored(float scale) {
		hudSetScale(scale);
	}

	/** @return what the editor's chip says instead of the size, for an element whose size is not its own; null for its size */
	default String hudSizeNote() {
		return null;
	}

	/** @return the scale that puts the element's corner at the mouse,
 for corner-dragging in the HUD editor */
	default float hudScaleAt(int mouseX, int mouseY) {
		int[] box = hudBounds();
		float base = box[2] / Math.max(0.01f, hudScale());
		return (mouseX - (box[0] + box[2] / 2f)) * 2f / base;
	}

	/** @return the x that stays fixed while resizing, from the bounds at resize start: the left edge, unless the element grows from its center */
	default float hudAnchorX(int[] box) {
		return box[0];
	}

	/** @return the feature holding the element's settings, to open them from the editor (its page and its group on that page) */
	default Feature hudFeature() {
		return this instanceof Feature feature ? feature : null;
	}

	/** @return the icon in the editor card: the feature's, unless the element is a part with its own icon (one of the status bars) */
	default String hudIconName() {
		Feature feature = hudFeature();
		return feature == null ? "sliders" : feature.iconName();
	}

	/** @return the icon color in the editor card, as RGB */
	default int hudIconRgb() {
		Feature feature = hudFeature();
		return feature == null ? SystemColors.GRAY_RGB : feature.iconRgb();
	}

	/** @return whether the element can be moved; false for elements the game positions itself, which can only be resized */
	default boolean hudMovable() {
		return true;
	}

	/** @return the setting key this element uses for a generic key (such as bar_width): elements with their own setting return that one */
	default String hudSettingKey(String key) {
		return key;
	}

	/** @return a setting of the element edited from its card in the editor with its own button (the info bar's segments), or null */
	default Setting<?> hudEditSetting() {
		return null;
	}

	/** @return the text of the button that opens {@link #hudEditSetting} */
	default String hudEditLabel() {
		return "Edit";
	}

	/** @return the x, in GUI pixels, of an edge inside the element that sets the width of one of its parts (the info bar's song segment), or NaN */
	default float hudEdgeX() {
		return Float.NaN;
	}

	/**
	 * Drags that edge to an x in GUI pixels, snapping to the part's preset widths unless free.
	 *
	 * @return a short label of the width now, shown while dragging
	 */
	default String hudDragEdge(float x, boolean free) {
		return "";
	}

	/** @return the parts of the element that a click in the HUD editor switches on and off, as drawn this frame, or null for none */
	default HudParts hudParts() {
		return null;
	}

	/** @return whether the element's width can be dragged at its left and right edge in the HUD editor */
	default boolean hudResizableWidth() {
		return false;
	}

	/** Called when a drag of the left or right edge starts; the element remembers what stays in place. */
	default void hudWidthStart(boolean right) {
	}

	/**
	 * Drags the left or right edge to an x in GUI pixels, snapping softly to useful widths unless free.
	 *
	 * @return a short label of the width now, shown while dragging
	 */
	default String hudDragWidth(boolean right, float mouseX, boolean free) {
		return "";
	}

	/** @return whether the element is switched on; it may still not show now, because what it is about is not going on here */
	default boolean hudEnabled() {
		return hudShown();
	}

	/** @return whether the element shows its sample when it is asked for in the editor's list while it is not active here; otherwise the Simulate menu has it */
	default boolean hudPreviewable() {
		return false;
	}

	/** Shows the element's sample in the editor while it is not active here, or stops that. */
	default void hudPreview(boolean on) {
	}

	/** @return what the editor's chip says while the element is dragged,
 instead of its size; empty for its size */
	default String hudDragStatus() {
		return "";
	}

	/** @return whether the left or right edge of a resizable element has a handle; an element lined up with something at one end has none there */
	default boolean hudWidthEdge(boolean right) {
		return true;
	}

	/** Puts the width that the left or right edge sets back to its default (a double-click on the edge). */
	default void hudResetWidth(boolean right) {
	}

	/** @return whether the element snaps to the screen and the other elements while dragged; bars landing in their stack do not */
	default boolean hudSnaps() {
		return true;
	}

	/** Called when a drag of the element in the HUD editor starts. */
	default void hudDragStart() {
	}

	/** Called when a drag of the element in the HUD editor ends, before it is anchored to the nearest screen edge. */
	default void hudDragEnd() {
	}

	/** Resets the element to its default position and size. */
	void hudReset();

	/** Resets the element's look (background, glow, theme, depth effect, see-through, color) as its card in the editor shows it. */
	default void hudResetStyle() {
	}

	/** Places the element from the screen anchor nearest to it, without moving it, so it keeps its place when the screen size changes. */
	default void hudAnchorNearest() {
	}

	/** @return the element's on-screen corner radius in GUI pixels, so the editor frame follows its shape */
	default float hudRadius() {
		return 8f;
	}

	/** @return the element's own glow switch, shown in the editor card, or null */
	default BooleanSetting hudGlowSetting() {
		Feature feature = hudFeature();
		if (feature == null) {
			return null;
		}
		for (Setting<?> setting : feature.settings()) {
			if (setting.key().equals("glow") && setting instanceof BooleanSetting flag) {
				return flag;
			}
		}
		return null;
	}

	/** @return the element's own theme setting (glass, dark, light, or the HUD theme), selectable in the editor card, or null */
	@SuppressWarnings("unchecked")
	default EnumSetting<PanelTheme> hudThemeSetting() {
		Feature feature = hudFeature();
		if (feature == null) {
			return null;
		}
		for (Setting<?> setting : feature.settings()) {
			if (setting.key().equals("panel_theme") && setting instanceof EnumSetting<?> choice) {
				return (EnumSetting<PanelTheme>) choice;
			}
		}
		return null;
	}

	/** @return the element's own depth effect setting (like the menu, on or off), selectable in the editor card, or null */
	@SuppressWarnings("unchecked")
	default EnumSetting<PanelDepth> hudDepthSetting() {
		Feature feature = hudFeature();
		if (feature == null) {
			return null;
		}
		for (Setting<?> setting : feature.settings()) {
			if (setting.key().equals("panel_depth") && setting instanceof EnumSetting<?> choice) {
				return (EnumSetting<PanelDepth>) choice;
			}
		}
		return null;
	}

	/** @return the element's background (glass) switch, if its background can be turned off, shown in the editor card; otherwise null */
	default BooleanSetting hudBackgroundSetting() {
		Feature feature = hudFeature();
		if (feature == null) {
			return null;
		}
		for (Setting<?> setting : feature.settings()) {
			if (setting.key().equals("background") && setting.isVisible() && setting instanceof BooleanSetting flag) {
				return flag;
			}
		}
		return null;
	}
}
