package dev.tim1337.lumio.ui;

import net.minecraft.network.chat.Component;

/**
 * Menu themes: the macOS light and dark themes, plus a glass theme with translucent surfaces and lit edges. The glass appearance (tint, clarity,
 * edge light strength, and whether the world behind it is blurred) is configured in the menu settings.
 * <p>
 * Surfaces on a window (cards, controls, hover) are translucent overlays, as in the mockups, so the window, the colored light and, behind glass,
 * the game show through them.
 */
public enum MacTheme {
	GLASS("Glass", new Palette(
		0x4A1C1E2A, 0x80FFFFFF, 0x12FFFFFF, 0x12FFFFFF, 0x1FFFFFFF,
		0xFFFFFFFF, 0xD8FFFFFF, 0x44FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x14FFFFFF, 0x29FFFFFF, 0x1AFFFFFF,
		0xFFF2F2F7, 0xFFCCCCD4, 0x14FFFFFF, true)),
	DARK("Dark", new Palette(
		0xFF1C1C1E, 0xFF3A3A3D, 0xFF151517, 0x0DFFFFFF, 0x14FFFFFF,
		0xFFF5F5F7, 0xFF98989D, 0xFF48484B, 0xFFFFFFFF, 0xFF1D1D1F, 0x0FFFFFFF, 0x1FFFFFFF, 0x17FFFFFF,
		0xFFF2F2F7, 0xFFA8A8B0, 0x14FFFFFF, false)),
	LIGHT("Light", new Palette(
		0xFFF5F5F7, 0xFFD2D2D7, 0x99FFFFFF, 0x0A000000, 0x17000000,
		0xFF1D1D1F, 0xFF636368, 0xFFD9D9DE, 0xFFFFFFFF, 0xFF1D1D1F, 0x0D000000, 0x1F000000, 0x12000000,
		0xFF1D1D1F, 0xFF636368, 0x14000000, false)),
	COLORED("Colored", new Palette(
		0x4A1C1E2A, 0x80FFFFFF, 0x12FFFFFF, 0x14FFFFFF, 0x24FFFFFF,
		0xFFFFFFFF, 0xD8FFFFFF, 0x44FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x16FFFFFF, 0x2EFFFFFF, 0x1CFFFFFF,
		0xFFFFFFFF, 0xFFDEDEE6, 0x14FFFFFF, true));

	/** A faint light line is lost on the Colored theme's bright, saturated field, where it lifts what is already bright: there it has this many times the opacity, and stands out as on Dark. */
	private static final float COLORED_LINE = 2.2f;

	private final Component displayName;
	private final Palette palette;

	/** @return how opaque a thin light line of an opacity is on this theme */
	public float lineOpacity(float opacity) {
		return this == COLORED ? opacity * COLORED_LINE : opacity;
	}

	MacTheme(String displayName, Palette palette) {
		this.displayName = Component.literal(displayName);
		this.palette = palette;
	}

	/** @return whether it is drawn on the glass panes, with the world or the colors behind them */
	public boolean onGlass() {
		return this == GLASS || this == COLORED;
	}

	public Component displayName() {
		return displayName;
	}

	public Palette palette() {
		return palette;
	}
}
