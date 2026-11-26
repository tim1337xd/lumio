package dev.tim1337.lumio.ui;

/**
 * The colors of a theme, as ARGB.
 *
 * @param window        window background
 * @param windowBorder  window outline
 * @param sidebar       page list on the left
 * @param card          settings groups
 * @param cardBorder    outline of the groups and separators between their rows
 * @param text          primary text
 * @param textSecondary secondary text
 * @param trackOff      track of a switch that is off, and of a slider
 * @param knob          knob of a switch and of a slider
 * @param onKnob        text on a knob, such as the selected choice on a segmented control's pill
 * @param control       buttons, fields and segmented control tracks
 * @param controlBorder outline of those controls
 * @param hover         hover overlay
 * @param hudText       primary text of HUD panels; it sits over the world and is slightly brighter or darker than the menu's
 * @param hudSecondary  secondary text of HUD panels
 * @param slot          faint fill for elements on a HUD panel, such as slots
 * @param glass         whether surfaces are translucent over a blurred world
 */
public record Palette(int window, int windowBorder, int sidebar, int card, int cardBorder, int text, int textSecondary,
					  int trackOff, int knob, int onKnob, int control, int controlBorder, int hover,
					  int hudText, int hudSecondary, int slot, boolean glass) {
	/** @return whether the window is light, so surfaces on it darken rather than lighten */
	public boolean light() {
		return (window >> 16 & 0xFF) + (window >> 8 & 0xFF) + (window & 0xFF) > 3 * 128;
	}

	/** @return the fill of the selected choice of a segmented control: a white knob on light windows, a light overlay on dark ones and glass */
	public int pill() {
		return glass ? 0x33FFFFFF : light() ? knob : 0x29FFFFFF;
	}

	/**
	 * @return the background of popups (dropdown lists, the color picker, tooltips, the HUD editor's bars): solid in the dark and light themes, a
	 *         little lighter than the window in dark; nearly opaque glass
	 */
	public int popup() {
		return glass ? 0xF01C1E2A : light() ? 0xFFFFFFFF : 0xFF000000 | ((window & 0xFFFFFF) + 0x0A0A0A);
	}

	/** @return the outline of popups */
	public int popupBorder() {
		return glass ? windowBorder : cardBorder;
	}

	/** How far a colored text (red of a danger button, orange of a key in use elsewhere, a chip's own color) moves toward the text color on light windows. */
	private static final float LIGHT_COLORED_TEXT = 0.45f;

	/**
	 * @return a bright system color as text: as it is on dark windows; darker on light ones, where red, orange or yellow on near white would read
	 *         at less than 3:1; on glass the theme's own text color, since over a bright day behind the glass only white text keeps 3:1 (there the
	 *         color shows in what goes with the text: a button's border, a chip's square, a tag's fill)
	 */
	public int coloredText(int argb) {
		if (glass) {
			return text;
		}
		if (!light()) {
			return argb >>> 24 == 0 ? 0xFF000000 | argb : argb;
		}
		// a color given without its alpha (0xRRGGBB) is opaque text
		int a = argb >>> 24 == 0 ? 0xFF : argb >>> 24;
		float t = LIGHT_COLORED_TEXT;
		int r = Math.round((argb >> 16 & 0xFF) * (1 - t) + (text >> 16 & 0xFF) * t);
		int g = Math.round((argb >> 8 & 0xFF) * (1 - t) + (text >> 8 & 0xFF) * t);
		int b = Math.round((argb & 0xFF) * (1 - t) + (text & 0xFF) * t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** @return the text color on that fill */
	public int onPill() {
		return light() ? onKnob : text;
	}
}
