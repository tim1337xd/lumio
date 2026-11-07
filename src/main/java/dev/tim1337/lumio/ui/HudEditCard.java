package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.features.visual.hud.BarFont;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.controls.SegmentFit;
import dev.tim1337.lumio.ui.controls.SwitchControl;

import net.minecraft.util.Mth;

/**
 * The settings card of the selected HUD element, next to it with a small arrow pointing at it: its switch, size, see-through, color,
 * background, glow, theme and depth effect, and links to reset it or open all its settings. Only the rows that apply to the element are shown.
 * The card flips to the other side or moves to stay on screen, follows the element when it is moved, and keeps away from the element list.
 * Every change is one undo step and shows live on the element.
 */
final class HudEditCard {
	private static final int WIDTH = 236;
	private static final int PAD = 12;
	private static final int ROW = 28;
	private static final int HEADER = 34;
	private static final int CONTROL = 20;
	private static final int ARROW = 6;
	private static final int GAP = 12;
	private static final int SWATCH = 13;
	private static final float[] SIZES = {0.9f, 1f, 1.1f, 1.2f};
	private static final String[] SIZE_LABELS = {"0.9", "1.0", "1.1", "1.2"};
	private static final int[] SWATCHES = {0x9A4DFF, 0x4D8DFF, 0x00E5FF, 0x4DFFA0, SystemColors.YELLOW_RGB, 0xFF6A00, SystemColors.PINK_RGB, 0xFFFFFF};
	private static final String[] DEPTH_LABELS = {"Menu", "On", "Off"};
	private static final String[] THEME_LABELS = {"Menu", "Glass", "Dark", "Light", "Colored"};
	private static final int THEME_ITEM = 18;
	private static final int THEME_SWATCH = 10;
	private static final int THEME_POPUP_PAD = 4;
	private static final String MIXED = "Mixed";
	private static final String[] FONT_LABELS = {"Menu", "Inter", "Minecraft"};

	// the parts of the card that react to clicks
	private static final int SHOWN = 1;
	private static final int SIZE = 10;
	private static final int SEE_THROUGH = 20;
	private static final int SWATCH_ID = 30;
	private static final int FOLLOW = 40;
	private static final int BACKGROUND = 41;
	private static final int GLOW = 42;
	private static final int THEME_BUTTON = 49;
	private static final int FONT = 65;
	private static final int DEPTH = 60;
	private static final int RESET = 70;
	private static final int ALL_SETTINGS = 71;
	private static final int EDIT = 72;

	private final HudEditScreen screen;
	private final HudEditHits hits = new HudEditHits();
	private final Spring shown = Spring.smooth(Spring.SNAPPY);
	private final float[] hover = new float[80];
	private EditableHud target;
	/** Whether the card is the shared one of the elements selected together. */
	private boolean sharedCard;
	/** Whether the theme's list is open, and where it is, in the card's own coordinates. */
	private boolean themeOpen;
	private int themeX;
	private int themeY;
	private int themeWidth;
	private boolean closing;
	private boolean seeThroughDrag;
	private int x;
	private int y;
	private int height;
	private int trackX;
	private int trackWidth;
	/** Whether the card is to the right of the element (its arrow on the left), and where the arrow points. */
	private boolean right = true;
	private int arrowY;
	/** How much the card is scaled down to fit on a small screen (a small window or a large GUI scale), and its size as drawn. */
	private float fit = 1f;
	private int drawnWidth = WIDTH;
	private int drawnHeight;

	HudEditCard(HudEditScreen screen) {
		this.screen = screen;
	}

	boolean isOpen() {
		return target != null && !closing;
	}

	/** Opens the shared card of the elements selected together. */
	void openGroup(EditableHud element) {
		open(element);
		sharedCard = true;
	}

	void open(EditableHud element) {
		if (target != element || closing) {
			shown.snap(0f);
		}
		target = element;
		sharedCard = false;
		themeOpen = false;
		closing = false;
		UiSounds.play(UiSounds.Cue.OPEN);
	}

	void close() {
		if (target != null && !closing) {
			closing = true;
			screen.history.finish();
		}
	}

	/** Called when the element starts or stops moving: the card follows it by itself, so nothing is needed but finishing a slider drag. */
	void follow() {
		seeThroughDrag = false;
	}

	boolean contains(double mouseX, double mouseY) {
		return isOpen() && mouseX >= x && mouseX < x + drawnWidth && mouseY >= y && mouseY < y + drawnHeight;
	}

	/** @return a screen position across, in the card's own (unscaled) coordinates */
	private double localX(double screenX) {
		return x + (screenX - x) / fit;
	}

	private double localY(double screenY) {
		return y + (screenY - y) / fit;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Setting<?>> T settingOf(EditableHud element, String key, Class<T> type) {
		Feature feature = element.hudFeature();
		if (feature == null) {
			return null;
		}
		String wanted = element.hudSettingKey(key);
		for (Setting<?> setting : feature.settings()) {
			if (setting.key().equals(wanted) && type.isInstance(setting)) {
				return (T) setting;
			}
		}
		return null;
	}

	// the settings of the element, looked up once per frame
	private FloatSetting seeThrough;
	private ColorSetting color;
	private BooleanSetting background;
	private BooleanSetting glow;
	private EnumSetting<PanelTheme> theme;
	private EnumSetting<PanelDepth> depth;

	private void lookUp(EditableHud element) {
		seeThrough = settingOf(element, "see_through", FloatSetting.class);
		color = settingOf(element, "accent", ColorSetting.class);
		background = element.hudBackgroundSetting();
		boolean panel = background == null || background.get();
		glow = panel ? element.hudGlowSetting() : null;
		theme = panel ? element.hudThemeSetting() : null;
		depth = panel ? element.hudDepthSetting() : null;
		if (!panel) {
			seeThrough = null;
		}
	}

	/** @return the card's height for the rows the element has */
	private int measure() {
		int rows = HEADER + ROW + 2 * PAD;
		if (seeThrough != null) {
			rows += ROW;
		}
		if (color != null) {
			rows += ROW + (color.canUseMenu() ? ROW : 0);
		}
		if (background != null) {
			rows += ROW;
		}
		if (glow != null) {
			rows += ROW;
		}
		if (theme != null) {
			rows += ROW;
		}
		if (depth != null) {
			rows += ROW;
		}
		if (target.hudEditSetting() != null) {
			rows += CONTROL + 6;
		}
		return rows + CONTROL + 8;
	}

	/** Places the card beside the element: right of it, or left when there is no room or the element list is there; within the screen. */
	private void place(int[] box) {
		fit = Math.min(1f, Math.min((screen.height - 8f) / height, (screen.width - 8f) / WIDTH));
		drawnWidth = Math.round(WIDTH * fit);
		drawnHeight = Math.round(height * fit);
		int roomRight = screen.width - (box[0] + box[2] + GAP + ARROW) - 4;
		int listLeft = screen.list.left();
		boolean rightFits = roomRight >= drawnWidth && box[0] + box[2] + GAP + ARROW + drawnWidth <= listLeft - 4;
		boolean leftFits = box[0] - GAP - ARROW - drawnWidth >= 4;
		right = rightFits || !leftFits;
		x = right ? box[0] + box[2] + GAP + ARROW : box[0] - GAP - ARROW - drawnWidth;
		x = Mth.clamp(x, 4, Math.max(4, screen.width - drawnWidth - 4));
		int middle = box[1] + box[3] / 2;
		y = Mth.clamp(middle - drawnHeight / 2, 4, Math.max(4, screen.height - drawnHeight - 4));
		arrowY = Mth.clamp(middle, y + 12, y + drawnHeight - 12);
	}

	void draw(Ui ui, int mouseX, int mouseY) {
		hits.clear();
		if (target == null) {
			return;
		}
		float amount = Mth.clamp(shown.follow(ui, closing ? 0f : 1f), 0f, 1f);
		if (closing && amount <= 0.01f) {
			target = null;
			closing = false;
			return;
		}
		if (sharedCard && !screen.hasGroup()) {
			sharedCard = false;
		}
		lookUp(target);
		height = sharedCard ? measureGroup() : measure();
		place(sharedCard ? screen.groupBounds() : target.hudBounds());
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		// it scales in from the element it belongs to
		float fromX = right ? x : x + drawnWidth;
		float grow = 0.92f + 0.08f * amount;
		pose.translate(fromX, arrowY);
		pose.scale(grow, grow);
		pose.translate(-fromX, -arrowY);
		Ui.setAlpha(amount);
		drawArrow(ui);
		// on a small screen the whole card is scaled down around its top-left corner
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(fit, fit);
		pose.translate(-x, -y);
		// text and shapes are made for the smaller size, so they stay sharp
		float zoom = Ui.zoom();
		Ui.setZoom(zoom * fit);
		HudEditScreen.surface(ui, x, y, WIDTH, height, Radius.LG, 1f);
		boolean live = !closing;
		int mx = live ? (int) localX(mouseX) : -1000;
		int my = live ? (int) localY(mouseY) : -1000;
		if (sharedCard) {
			drawGroupRows(ui, mx, my);
		} else {
			drawRows(ui, mx, my);
		}
		drawThemeList(ui, mx, my);
		Ui.setZoom(zoom);
		pose.popMatrix();
		Ui.setAlpha(1f);
		pose.popMatrix();
	}

	/** Draws the small arrow on the card's edge, pointing at the element. */
	private void drawArrow(Ui ui) {
		int color = ui.palette.popup();
		for (int i = 0; i < ARROW; i++) {
			int length = 2 * (ARROW - i) - 1;
			int column = right ? x - ARROW + i : x + drawnWidth + ARROW - 1 - i;
			ui.fill(column, arrowY - length / 2, 1, length, color);
		}
	}

	private void drawRows(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		EditableHud element = target;
		Feature feature = element.hudFeature();
		int left = x + PAD;
		int right = x + WIDTH - PAD;
		int rowY = y + PAD;

		// head: the element's icon, its name and its switch
		ui.iconTile(element.hudIconName(), left, rowY + 4, 20, 13, Radius.SM, 0xFF000000 | element.hudIconRgb(), 0xFFFFFFFF);
		int switchX = right - SwitchControl.TRACK_WIDTH;
		ui.textAtScaledBold(ui.fit(element.hudName(), switchX - left - 34), left + 28, rowY + 9, 1f, palette.text());
		if (feature != null) {
			boolean on = feature.isEnabled();
			hoverFor(ui, SHOWN, mouseX, mouseY, switchX, rowY + 4, SwitchControl.TRACK_WIDTH, SwitchControl.TRACK_HEIGHT);
			SwitchControl.draw(ui, switchX, rowY + 4, on ? 1f : 0f, hover[SHOWN], 0f);
			hits.add(SHOWN, switchX, rowY + 4, SwitchControl.TRACK_WIDTH, SwitchControl.TRACK_HEIGHT);
		}
		rowY += HEADER;
		ui.fill(x + 1, rowY - 6, WIDTH - 2, 1, palette.cardBorder());

		// size: four presets and the exact value
		float scale = element.hudScale();
		String value = HudEditScreen.sizeText(scale);
		int valueWidth = ui.width(value);
		label(ui, "Size", left, rowY);
		int segmentRight = right - valueWidth - 8;
		int chosen = -1;
		for (int i = 0; i < SIZES.length; i++) {
			if (Math.abs(SIZES[i] - scale) < 0.001f) {
				chosen = i;
			}
		}
		segmented(ui, SIZE, SIZE_LABELS, chosen, left + 52, rowY, segmentRight - left - 52, mouseX, mouseY);
		ui.textRight(value, right, rowY + CONTROL / 2, palette.text());
		rowY += ROW;

		if (seeThrough != null) {
			label(ui, "See-through", left, rowY);
			String percent = Math.round(seeThrough.get() * 100f) + " %";
			int percentWidth = ui.width("80 %");
			trackX = left + 82;
			trackWidth = right - percentWidth - 8 - trackX;
			int middle = rowY + CONTROL / 2;
			float fraction = seeThrough.get() / seeThrough.max();
			ui.rounded(trackX, middle - 2, trackWidth, 4, Radius.pill(4), palette.trackOff());
			ui.rounded(trackX, middle - 2, Math.round(trackWidth * fraction), 4, Radius.pill(4), ui.accent);
			ui.circle(trackX + Math.round(trackWidth * fraction), middle, 6, palette.knob());
			ui.textRight(percent, right, middle, palette.text());
			hits.add(SEE_THROUGH, trackX - 6, rowY, trackWidth + 12, CONTROL);
			rowY += ROW;
		}

		if (color != null) {
			label(ui, "Color", left, rowY);
			int swatchLeft = left + 52;
			int step = (right - swatchLeft - SWATCH) / (SWATCHES.length - 1);
			for (int i = 0; i < SWATCHES.length; i++) {
				int sx = swatchLeft + i * step;
				int sy = rowY + (CONTROL - SWATCH) / 2;
				boolean picked = !color.usesMenu() && (color.get() & 0xFFFFFF) == SWATCHES[i];
				ui.circle(sx + SWATCH / 2, sy + SWATCH / 2, SWATCH / 2, 0xFF000000 | SWATCHES[i]);
				if (picked) {
					ui.ring(sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, Radius.pill(SWATCH + 4), 2, 0xFFFFFFFF);
				}
				hits.add(SWATCH_ID + i, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4);
			}
			rowY += ROW;
			if (color.canUseMenu()) {
				rowY = switchRow(ui, FOLLOW, "Menu color", color.usesMenu(), left, right, rowY, mouseX, mouseY);
			}
		}
		if (background != null) {
			rowY = switchRow(ui, BACKGROUND, "Background", background.get(), left, right, rowY, mouseX, mouseY);
		}
		if (glow != null) {
			rowY = switchRow(ui, GLOW, "Glow around it", glow.get(), left, right, rowY, mouseX, mouseY);
		}
		if (theme != null) {
			label(ui, "Theme", left, rowY);
			themeButton(ui, theme.get(), left + 64, rowY, right - left - 64, mouseX, mouseY);
			rowY += ROW;
		}
		if (depth != null) {
			label(ui, "Depth", left, rowY);
			segmented(ui, DEPTH, DEPTH_LABELS, depth.get().ordinal(), left + 64, rowY, right - left - 64, mouseX, mouseY);
			rowY += ROW;
		}

		// footer: reset this element, or open all its settings in the menu
		rowY += 4;
		ui.fill(x + 1, rowY - 4, WIDTH - 2, 1, palette.cardBorder());
		// the element's own editor first, such as the info bar's segments, above reset and all settings
		if (target.hudEditSetting() != null) {
			button(ui, EDIT, target.hudEditLabel(), left, rowY + 4, right - left, true, mouseX, mouseY);
			rowY += CONTROL + 6;
		}
		int half = (right - left - 6) / 2;
		button(ui, RESET, "Reset", left, rowY + 4, half, false, mouseX, mouseY);
		button(ui, ALL_SETTINGS, "All settings", left + half + 6, rowY + 4, right - left - half - 6, target.hudEditSetting() == null, mouseX, mouseY);
	}

	private static void label(Ui ui, String text, int x, int rowY) {
		ui.textMiddle(text, x, rowY + CONTROL / 2, ui.palette.textSecondary());
	}

	private void hoverFor(Ui ui, int id, int mouseX, int mouseY, int hx, int hy, int hw, int hh) {
		boolean over = mouseX >= hx && mouseX < hx + hw && mouseY >= hy && mouseY < hy + hh;
		hover[id] = ui.approach(hover[id], over ? 1f : 0f, Ui.HOVER_RATE);
	}

	/** Draws a row with a name and a switch; @return the top of the next row */
	private int switchRow(Ui ui, int id, String text, boolean on, int left, int right, int rowY, int mouseX, int mouseY) {
		label(ui, text, left, rowY);
		int switchX = right - SwitchControl.TRACK_WIDTH;
		hoverFor(ui, id, mouseX, mouseY, switchX, rowY, SwitchControl.TRACK_WIDTH, SwitchControl.TRACK_HEIGHT);
		SwitchControl.draw(ui, switchX, rowY, on ? 1f : 0f, hover[id], 0f);
		hits.add(id, switchX, rowY, SwitchControl.TRACK_WIDTH, SwitchControl.TRACK_HEIGHT);
		return rowY + ROW;
	}

	/** Draws the theme's button: its swatch and name, or Mixed, and the arrow of a list. */
	private void themeButton(Ui ui, PanelTheme chosen, int bx, int by, int width, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		hoverFor(ui, THEME_BUTTON, mouseX, mouseY, bx, by, width, CONTROL);
		ui.rounded(bx, by, width, CONTROL, Radius.SM, Ui.mix(palette.control(), palette.hover(), hover[THEME_BUTTON]));
		ui.ring(bx, by, width, CONTROL, Radius.SM, 1, themeOpen ? ui.accent : palette.controlBorder());
		int textX = bx + 6;
		if (chosen != null) {
			themeSwatch(ui, chosen, textX, by + (CONTROL - THEME_SWATCH) / 2);
			textX += THEME_SWATCH + 6;
		}
		ui.textMiddle(chosen != null ? THEME_LABELS[chosen.ordinal()] : MIXED, textX, by + CONTROL / 2, palette.text());
		ui.icon("chevron", bx + width - 16, by + CONTROL / 2 - 6, 12, palette.textSecondary());
		hits.add(THEME_BUTTON, bx, by, width, CONTROL);
		themeX = bx;
		themeY = by + CONTROL + 2;
		themeWidth = width;
	}

	/** A small picture of a theme: its window's color, the glass of the HUD's look, and the colors of the Colored theme side by side. */
	private static void themeSwatch(Ui ui, PanelTheme theme, int sx, int sy) {
		MacTheme shown = theme.themeOrHud();
		if (shown == MacTheme.COLORED) {
			int[] colors = ModFeatures.MENU.coloredColors();
			int each = THEME_SWATCH / Math.max(1, colors.length);
			ui.rounded(sx, sy, THEME_SWATCH, THEME_SWATCH, Radius.pill(THEME_SWATCH), 0xFF000000 | colors[0]);
			for (int i = 1; i < colors.length; i++) {
				ui.rounded(sx + i * each, sy + 1, THEME_SWATCH - i * each, THEME_SWATCH - 2, Radius.pill(THEME_SWATCH - 2), 0xFF000000 | colors[i]);
			}
		} else {
			ui.rounded(sx, sy, THEME_SWATCH, THEME_SWATCH, Radius.pill(THEME_SWATCH), 0xFF000000 | shown.palette().window());
		}
		ui.ring(sx, sy, THEME_SWATCH, THEME_SWATCH, Radius.pill(THEME_SWATCH), 1, Ui.withOpacity(0xFFFFFFFF, 0.3f));
	}

	/** Draws the open list of themes over the card. */
	private void drawThemeList(Ui ui, int mouseX, int mouseY) {
		if (!themeOpen) {
			return;
		}
		PanelTheme[] themes = PanelTheme.values();
		int listHeight = themes.length * THEME_ITEM + 2 * THEME_POPUP_PAD;
		Palette palette = ui.palette;
		ui.shadow(themeX, themeY, themeWidth, listHeight, Radius.SM, 6, 0.35f);
		ui.rounded(themeX, themeY, themeWidth, listHeight, Radius.SM, palette.popup());
		ui.ring(themeX, themeY, themeWidth, listHeight, Radius.SM, 1, palette.popupBorder());
		for (int i = 0; i < themes.length; i++) {
			int itemY = themeY + THEME_POPUP_PAD + i * THEME_ITEM;
			boolean over = mouseX >= themeX && mouseX < themeX + themeWidth && mouseY >= itemY && mouseY < itemY + THEME_ITEM;
			if (over) {
				ui.rounded(themeX + 2, itemY, themeWidth - 4, THEME_ITEM, Radius.XS, Ui.withOpacity(ui.accent, 0.85f));
			}
			themeSwatch(ui, themes[i], themeX + 6, itemY + (THEME_ITEM - THEME_SWATCH) / 2);
			ui.textMiddle(THEME_LABELS[i], themeX + 12 + THEME_SWATCH, itemY + THEME_ITEM / 2, over ? 0xFFFFFFFF : palette.text());
		}
	}

	/** @return the theme under a place in the card, while its list is open, or -1 */
	private int themeItemAt(double localX, double localY) {
		if (!themeOpen || localX < themeX || localX >= themeX + themeWidth) {
			return -1;
		}
		int item = (int) Math.floor((localY - themeY - THEME_POPUP_PAD) / THEME_ITEM);
		return item >= 0 && item < PanelTheme.values().length ? item : -1;
	}

	/** Sets a theme: on the element, or on all of the selection, as one step of the history. */
	private void chooseTheme(PanelTheme chosen) {
		if (sharedCard) {
			screen.history.begin(screen.groupFeatures());
			for (EditableHud element : screen.group) {
				if (element.hudThemeSetting() != null) {
					element.hudThemeSetting().set(chosen);
				}
			}
		} else {
			if (target.hudFeature() != null) {
				screen.history.begin(target.hudFeature());
			}
			theme.set(chosen);
		}
		screen.history.finish();
		FeatureManager.saveSoon();
		UiSounds.click();
	}

	// ------------------------------------------------------------------------------------------------- the shared card of a selection

	/** @return a setting all the selected elements have, by what it is on each, or null when one of them does not have it */
	private <T> java.util.List<T> common(java.util.function.Function<EditableHud, T> of) {
		java.util.List<T> found = new java.util.ArrayList<>();
		for (EditableHud element : screen.group) {
			T setting = of.apply(element);
			if (setting == null) {
				return null;
			}
			found.add(setting);
		}
		return found;
	}

	/** @return the one value all settings have, or null when they differ */
	private static <V> V same(java.util.List<? extends dev.tim1337.lumio.config.setting.Setting<V>> settings) {
		V first = settings.getFirst().get();
		for (dev.tim1337.lumio.config.setting.Setting<V> setting : settings) {
			if (!java.util.Objects.equals(setting.get(), first)) {
				return null;
			}
		}
		return first;
	}

	@SuppressWarnings("unchecked")
	private static EnumSetting<BarFont> fontOf(EditableHud element) {
		EnumSetting<?> found = settingOf(element, "font", EnumSetting.class);
		return found != null && found.get() instanceof BarFont ? (EnumSetting<BarFont>) found : null;
	}

	private int measureGroup() {
		int rows = HEADER + 2 * PAD + ROW;
		rows += common(EditableHud::hudThemeSetting) != null ? ROW : 0;
		rows += common(EditableHud::hudGlowSetting) != null ? ROW : 0;
		rows += common(EditableHud::hudDepthSetting) != null ? ROW : 0;
		rows += common(EditableHud::hudBackgroundSetting) != null ? ROW : 0;
		rows += common(HudEditCard::fontOf) != null ? ROW : 0;
		return rows;
	}

	private void drawGroupRows(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int left = x + PAD;
		int right = x + WIDTH - PAD;
		int rowY = y + PAD;
		ui.textAtScaledBold(screen.group.size() + " elements", left, rowY + 9, 1f, palette.text());
		rowY += HEADER;

		// the size: a value if all have the same
		float size = target.hudScale();
		boolean sameSize = true;
		for (EditableHud element : screen.group) {
			sameSize &= Math.abs(element.hudScale() - size) < 0.001f;
		}
		label(ui, "Size", left, rowY);
		String value = sameSize ? HudEditScreen.sizeText(size) : MIXED;
		int chosen = -1;
		for (int i = 0; sameSize && i < SIZES.length; i++) {
			if (Math.abs(SIZES[i] - size) < 0.001f) {
				chosen = i;
			}
		}
		int segmentRight = right - ui.width(value) - 8;
		segmented(ui, SIZE, SIZE_LABELS, chosen, left + 52, rowY, segmentRight - left - 52, mouseX, mouseY);
		ui.textRight(value, right, rowY + CONTROL / 2, palette.text());
		rowY += ROW;

		rowY = groupSwitch(ui, common(EditableHud::hudBackgroundSetting), BACKGROUND, "Background", left, right, rowY, mouseX, mouseY);
		rowY = groupSwitch(ui, common(EditableHud::hudGlowSetting), GLOW, "Glow around it", left, right, rowY, mouseX, mouseY);
		java.util.List<EnumSetting<PanelTheme>> themes = common(EditableHud::hudThemeSetting);
		if (themes != null) {
			label(ui, "Theme", left, rowY);
			themeButton(ui, same(themes), left + 64, rowY, right - left - 64, mouseX, mouseY);
			rowY += ROW;
		}
		java.util.List<EnumSetting<PanelDepth>> depths = common(EditableHud::hudDepthSetting);
		if (depths != null) {
			label(ui, "Depth", left, rowY);
			PanelDepth one = same(depths);
			segmented(ui, DEPTH, DEPTH_LABELS, one == null ? -1 : one.ordinal(), left + 64, rowY, right - left - 64, mouseX, mouseY);
			rowY += ROW;
		}
		java.util.List<EnumSetting<BarFont>> fonts = common(HudEditCard::fontOf);
		if (fonts != null) {
			label(ui, "Font", left, rowY);
			BarFont one = same(fonts);
			segmented(ui, FONT, FONT_LABELS, one == null ? -1 : one.ordinal(), left + 64, rowY, right - left - 64, mouseX, mouseY);
		}
	}

	private int groupSwitch(Ui ui, java.util.List<BooleanSetting> settings, int id, String text, int left, int right, int rowY, int mouseX, int mouseY) {
		if (settings == null) {
			return rowY;
		}
		Boolean one = same(settings);
		int next = switchRow(ui, id, text, one != null && one, left, right, rowY, mouseX, mouseY);
		if (one == null) {
			ui.textRight(MIXED, right - SwitchControl.TRACK_WIDTH - 6, rowY + CONTROL / 2, ui.palette.textSecondary());
		}
		return next;
	}

	/** A click on the shared card: each change is one step of the history. */
	private void clickGroup(int id) {
		screen.history.begin(screen.groupFeatures());
		if (id >= SIZE && id < SIZE + SIZES.length) {
			for (EditableHud element : screen.group) {
				element.hudSetScale(Mth.clamp(SIZES[id - SIZE], element.hudMinScale(), element.hudMaxScale()));
			}
		} else if (id == BACKGROUND) {
			toggleAll(common(EditableHud::hudBackgroundSetting));
		} else if (id == GLOW) {
			toggleAll(common(EditableHud::hudGlowSetting));
		} else if (id >= DEPTH && id < DEPTH + PanelDepth.values().length) {
			for (EnumSetting<PanelDepth> setting : common(EditableHud::hudDepthSetting)) {
				setting.set(PanelDepth.values()[id - DEPTH]);
			}
		} else if (id >= FONT && id < FONT + BarFont.values().length) {
			for (EnumSetting<BarFont> setting : common(HudEditCard::fontOf)) {
				setting.set(BarFont.values()[id - FONT]);
			}
		}
		screen.history.finish();
		FeatureManager.saveSoon();
		UiSounds.click();
	}

	/** Turns all on, or all off when all are on already. */
	private static void toggleAll(java.util.List<BooleanSetting> settings) {
		boolean on = !Boolean.TRUE.equals(same(settings));
		for (BooleanSetting setting : settings) {
			setting.set(on);
		}
	}

	/** Draws a segmented control in the menu's style; each choice is the id plus its index */
	private void segmented(Ui ui, int id, String[] labels, int chosen, int sx, int sy, int width, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		ui.rounded(sx, sy, width, CONTROL, Radius.SM, palette.control());
		int[] texts = new int[labels.length];
		int[] edges = new int[labels.length + 1];
		for (int i = 0; i < labels.length; i++) {
			texts[i] = (int) Math.ceil(ui.boldWidthScaled(labels[i], TextScale.BODY));
		}
		SegmentFit.edges(sx, width, texts, 0, edges);
		for (int i = 0; i < labels.length; i++) {
			int cx = edges[i];
			int cw = edges[i + 1] - edges[i];
			hoverFor(ui, id + i, mouseX, mouseY, cx, sy, cw, CONTROL);
			if (i == chosen) {
				ui.rounded(cx + 2, sy + 2, cw - 4, CONTROL - 4, Radius.SM, palette.pill());
			} else if (hover[id + i] > 0f) {
				ui.rounded(cx + 2, sy + 2, cw - 4, CONTROL - 4, Radius.SM, Ui.withOpacity(palette.hover(), hover[id + i]));
			}
			int color = i == chosen ? palette.onPill() : Ui.mix(palette.textSecondary(), palette.text(), hover[id + i]);
			if (i == chosen) {
				ui.textCenteredBold(labels[i], cx + cw / 2f, sy + CONTROL / 2, color);
			} else {
				ui.textCentered(labels[i], cx + cw / 2, sy + CONTROL / 2, color);
			}
			hits.add(id + i, cx, sy, cw, CONTROL);
		}
	}

	private void button(Ui ui, int id, String text, int bx, int by, int width, boolean primary, int mouseX, int mouseY) {
		hoverFor(ui, id, mouseX, mouseY, bx, by, width, CONTROL);
		Palette palette = ui.palette;
		if (primary) {
			ui.rounded(bx, by, width, CONTROL, Radius.SM, Ui.mix(ui.accent, 0xFFFFFFFF, 0.14f * hover[id]));
		} else {
			ui.rounded(bx, by, width, CONTROL, Radius.SM, Ui.withOpacity(palette.hover(), 0.6f + 0.4f * hover[id]));
		}
		ui.textCentered(text, bx + width / 2, by + CONTROL / 2, primary ? 0xFFFFFFFF : palette.text());
		hits.add(id, bx, by, width, CONTROL);
	}

	// ------------------------------------------------------------------------------------------------------------ input

	/** @return whether the click was on the card */
	boolean click(double mouseX, double mouseY, int button) {
		if (themeOpen) {
			int item = themeItemAt(localX(mouseX), localY(mouseY));
			themeOpen = false;
			if (item >= 0) {
				chooseTheme(PanelTheme.values()[item]);
				return true;
			}
			if (contains(mouseX, mouseY)) {
				return true;
			}
		}
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		int id = hits.at(localX(mouseX), localY(mouseY));
		if (button != 0 || id < 0) {
			return true;
		}
		if (id == THEME_BUTTON) {
			themeOpen = true;
			UiSounds.click();
			return true;
		}
		if (sharedCard) {
			clickGroup(id);
			return true;
		}
		EditableHud element = target;
		Feature feature = element.hudFeature();
		if (id >= SIZE && id < SIZE + SIZES.length) {
			screen.setSize(element, SIZES[id - SIZE]);
			UiSounds.click();
			return true;
		}
		if (id == SHOWN) {
			screen.toggleShown(element);
			return true;
		}
		if (id == ALL_SETTINGS) {
			UiSounds.click();
			screen.openAllSettings(element);
			return true;
		}
		if (id == EDIT) {
			UiSounds.click();
			screen.openSetting(element, element.hudEditSetting());
			return true;
		}
		if (feature == null) {
			return true;
		}
		if (id == SEE_THROUGH) {
			screen.history.begin(feature);
			seeThroughDrag = true;
			setSeeThrough(localX(mouseX));
			return true;
		}
		screen.history.begin(feature);
		if (id >= SWATCH_ID && id < SWATCH_ID + SWATCHES.length) {
			color.setUsesMenu(false);
			color.set(SWATCHES[id - SWATCH_ID]);
			UiSounds.click();
		} else if (id == FOLLOW) {
			color.setUsesMenu(!color.usesMenu());
			UiSounds.toggle(color.usesMenu());
		} else if (id == BACKGROUND) {
			background.set(!background.get());
			UiSounds.toggle(background.get());
		} else if (id == GLOW) {
			glow.set(!glow.get());
			UiSounds.toggle(glow.get());
		} else if (id >= DEPTH && id < DEPTH + PanelDepth.values().length) {
			depth.set(PanelDepth.values()[id - DEPTH]);
			UiSounds.click();
		} else if (id == RESET) {
			element.hudReset();
			element.hudResetStyle();
			UiSounds.click();
		}
		screen.history.finish();
		FeatureManager.saveSoon();
		return true;
	}

	private void setSeeThrough(double mouseX) {
		if (seeThrough == null || trackWidth <= 0) {
			return;
		}
		float fraction = Mth.clamp((float) ((mouseX - trackX) / trackWidth), 0f, 1f);
		seeThrough.set(seeThrough.rounded(fraction * seeThrough.max()));
	}

	/** @return whether the see-through slider is being dragged */
	boolean drag(double mouseX, double mouseY) {
		if (!seeThroughDrag) {
			return false;
		}
		setSeeThrough(localX(mouseX));
		return true;
	}

	void release() {
		if (seeThroughDrag) {
			seeThroughDrag = false;
			screen.history.finish();
			FeatureManager.saveSoon();
		}
	}
}
