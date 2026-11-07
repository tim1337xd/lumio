package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.SmoothTexture;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * A color picker, in rows with even gaps: a saturation/brightness square next to a hue bar, picked by clicking or dragging; a hex field with a
 * swatch of the color; the preset colors; the opacity, for colors that have one; and the switch to the menu color. The hue is stored here so it
 * survives gray colors, which have none.
 */
public class ColorControl extends Control {
	private static final int SQUARE_HEIGHT = 74;
	private static final int BAR_WIDTH = 14;
	/** Gap between the square and the hue bar, and between the rows. */
	private static final int GAP = 10;
	private static final int ROW_GAP = 12;
	private static final int FIELD_WIDTH = 120;
	private static final int FIELD_HEIGHT = 22;
	private static final int FIELD_SWATCH = 12;
	private static final int TOGGLE_HEIGHT = 14;
	private static final int OPACITY_HEIGHT = 14;
	/** Preset circles: their size and the smallest gap between them; they wrap to a second row if they do not fit. */
	private static final int PRESET_SIZE = 18;
	private static final int PRESET_MIN_GAP = 4;
	/** The square's knob: a white ring that stays inside the square. */
	private static final int KNOB = 12;
	private static final int[] PRESETS = {0x9A4DFF, 0x4D8DFF, 0x00E5FF, 0x4DFFA0, SystemColors.YELLOW_RGB, 0xFF6A00, SystemColors.PINK_RGB, 0xFFFFFF};

	/** The square is drawn from a texture; drawing it as lines would be a few hundred shapes per frame. */
	private static final int TEXTURE_WIDTH = 512;
	/** The texture has more pixels than the square is drawn with and is filtered smoothly, so the rounded corners are not jagged. */
	private static final int TEXTURE_HEIGHT = SQUARE_HEIGHT * 3;
	/**
	 * Every picker has its own square texture. The game draws a frame's GUI only after all of it was submitted, so with one shared texture every
	 * picker would show the last one that was generated.
	 */
	private static final class Square {
		final Identifier id;
		final SmoothTexture texture;
		float hue = -1f;
		int drawn = -1;

		Square(int number) {
			id = Lumio.id("color_square_" + number);
			texture = new SmoothTexture("Lumio color square " + number, new NativeImage(NativeImage.Format.RGBA, TEXTURE_WIDTH, TEXTURE_HEIGHT, false));
			Minecraft.getInstance().getTextureManager().register(id, texture);
		}
	}

	private static final Map<ColorSetting, Square> SQUARES = new HashMap<>();

	private Square square() {
		return SQUARES.computeIfAbsent(setting, key -> new Square(SQUARES.size()));
	}
	private static final int BAR_SCALE = 4;
	private static final int BAR_TEXTURE_WIDTH = BAR_WIDTH * BAR_SCALE;
	private static final int BAR_TEXTURE_HEIGHT = SQUARE_HEIGHT * BAR_SCALE;
	private static final Identifier BAR_ID = Lumio.id("color_bar");
	private static SmoothTexture barTexture;
	/** Corner radius of the square. */
	private static final float CORNER = 8f;

	private enum Drag {
		NONE, SQUARE, BAR, OPACITY
	}

	private final ColorSetting setting;
	private final Runnable onChanged;
	private float hue;
	private float saturation;
	private float value;
	private Drag dragging = Drag.NONE;
	private boolean fieldFocused;
	private String fieldText;

	private final int[] presets;

	public ColorControl(ColorSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.presets = setting.presets() != null ? setting.presets() : PRESETS;
		this.onChanged = onChanged;
		adopt(setting.get());
	}

	@Override
	protected int measure(Ui ui, int width) {
		this.width = width;
		return toggleY() - y + (setting.canUseMenu() ? TOGGLE_HEIGHT : -ROW_GAP);
	}

	/** @return how many preset circles fit in a row */
	private int presetsPerRow() {
		return Math.max(1, Math.min(presets.length, (width + PRESET_MIN_GAP) / (PRESET_SIZE + PRESET_MIN_GAP)));
	}

	private int presetRows() {
		return (presets.length + presetsPerRow() - 1) / presetsPerRow();
	}

	private int presetsY() {
		return fieldY() + FIELD_HEIGHT + ROW_GAP;
	}

	/** @return the center of a preset circle: evenly spaced over the full width */
	private int presetX(int index) {
		int perRow = presetsPerRow();
		int column = index % perRow;
		float step = perRow == 1 ? 0f : (width - PRESET_SIZE) / (float) (perRow - 1);
		return x + PRESET_SIZE / 2 + Math.round(column * step);
	}

	private int presetY(int index) {
		return presetsY() + (index / presetsPerRow()) * (PRESET_SIZE + PRESET_MIN_GAP) + PRESET_SIZE / 2;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	private int squareWidth() {
		return width - BAR_WIDTH - GAP;
	}

	private int fieldY() {
		return y + SQUARE_HEIGHT + ROW_GAP;
	}

	/** Adopts a color, keeping the current hue if the color is a gray (which has no hue). */
	private void adopt(int rgb) {
		float r = (rgb >> 16 & 0xFF) / 255f;
		float g = (rgb >> 8 & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float delta = max - Math.min(r, Math.min(g, b));
		value = max;
		saturation = max == 0f ? 0f : delta / max;
		if (delta != 0f) {
			float h;
			if (max == r) {
				h = ((g - b) / delta) % 6f;
			} else if (max == g) {
				h = (b - r) / delta + 2f;
			} else {
				h = (r - g) / delta + 4f;
			}
			hue = (h / 6f + 1f) % 1f;
		}
		fieldText = ColorSetting.toHex(rgb);
	}

	private int rgb() {
		return hsvToRgb(hue, saturation, value);
	}

	private void picked() {
		setting.set(rgb());
		fieldText = ColorSetting.toHex(rgb());
		onChanged.run();
	}

	/** @return whether the mouse is over the checkbox in the name row that switches to the menu color */
	private boolean overMenuToggle(double mouseX, double mouseY) {
		return setting.canUseMenu() && mouseX >= x + width - 160 && mouseX < x + width && mouseY >= toggleY() - 5 && mouseY < toggleY() + 19;
	}

	private int toggleY() {
		return opacityY() + (setting.hasOpacity() ? OPACITY_HEIGHT + ROW_GAP : 0);
	}

	private int opacityY() {
		int rows = presetRows();
		return presetsY() + rows * PRESET_SIZE + (rows - 1) * PRESET_MIN_GAP + ROW_GAP;
	}

	/** Draws the opacity slider: a track filled with the color, a knob, and the value on the right. */
	private void drawOpacity(Ui ui) {
		if (!setting.hasOpacity()) {
			return;
		}
		int y0 = opacityY();
		ui.textMiddle("Opacity", x, y0 + 7, ui.palette.textSecondary());
		int tx = x + 62;
		int tw = width - 62 - 44;
		ui.rounded(tx, y0 + 4, tw, 6, Radius.pill(6), ui.palette.trackOff());
		// the track fades from transparent to the full color, previewing every opacity along it
		int cap = 3;
		for (int px = tx + cap; px < tx + tw - cap; px += 2) {
			float at = (px + 1 - tx) / (float) tw;
			ui.fill(px, y0 + 4, 2, 6, Ui.withOpacity(0xFF000000 | rgb(), at));
		}
		ui.circle(tx + tw - cap, y0 + 7, 3, 0xFF000000 | rgb());
		int knob = tx + Math.round((tw - 1) * setting.opacity());
		ui.circle(knob, y0 + 7, 7, 0xFFFFFFFF);
		ui.circle(knob, y0 + 7, 4, Ui.withOpacity(0xFF000000 | rgb(), setting.opacity()));
		ui.textRight(Math.round(setting.opacity() * 100f) + "%", x + width, y0 + 7, ui.palette.text());
	}

	private boolean overOpacity(double mouseX, double mouseY) {
		return setting.hasOpacity() && mouseY >= opacityY() - 3 && mouseY < opacityY() + 17 && mouseX >= x + 56 && mouseX < x + width - 40;
	}

	private void pickOpacity(double mouseX) {
		int tx = x + 62;
		int tw = width - 62 - 44;
		setting.setOpacity((float) ((mouseX - tx) / Math.max(1, tw - 1)));
		onChanged.run();
	}

	private void drawMenuToggle(Ui ui, int mouseX, int mouseY) {
		if (!setting.canUseMenu()) {
			return;
		}
		float h = hover(ui, 1, overMenuToggle(mouseX, mouseY));
		int box = 14;
		int bx = x + width - box - 6;
		int by = toggleY();
		if (setting.usesMenu()) {
			ui.rounded(bx, by, box, box, Radius.XS, ui.accent);
			ui.icon("tick", bx + 2, by + 2, box - 4, 0xFFFFFFFF);
		} else {
			ui.rounded(bx, by, box, box, Radius.XS, ui.palette.control());
			ui.ring(bx, by, box, box, Radius.XS, 1, Ui.mix(ui.palette.controlBorder(), ui.accent, 0.7f * h));
		}
		ui.textRight("Menu color", bx - 12, by + box / 2, ui.palette.textSecondary());
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		// the color may have been changed elsewhere, e.g. by a reset button
		if (setting.get() != rgb() && dragging == Drag.NONE && !fieldFocused) {
			adopt(setting.get());
		}

		int squareWidth = squareWidth();
		// brighter towards the top, more saturated towards the right
		updateSquare(squareWidth);
		// the square and bar textures fade with the window like everything else; without a tint they stayed opaque while it closed
		int tint = Ui.faded(0xFFFFFFFF);
		ui.graphics.blit(RenderPipelines.GUI_TEXTURED, square().id, x, y, 0f, 0f, squareWidth, SQUARE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, tint);
		int barX = x + squareWidth + GAP;
		ensureBar();
		ui.graphics.blit(RenderPipelines.GUI_TEXTURED, BAR_ID, barX, y, 0f, 0f, BAR_WIDTH, SQUARE_HEIGHT, BAR_TEXTURE_WIDTH, BAR_TEXTURE_HEIGHT, BAR_TEXTURE_WIDTH, BAR_TEXTURE_HEIGHT, tint);
		ui.ring(x, y, squareWidth, SQUARE_HEIGHT, CORNER, 1, ui.palette.controlBorder());
		ui.ring(barX, y, BAR_WIDTH, SQUARE_HEIGHT, BAR_WIDTH / 2f, 1, ui.palette.controlBorder());

		// the square's knob is a white ring kept inside the square, so it is never cut off or drawn over the hue bar
		int half = KNOB / 2;
		int markerX = Mth.clamp(x + Math.round(saturation * (squareWidth - 1)), x + half, x + squareWidth - half);
		int markerY = Mth.clamp(y + Math.round((1f - value) * (SQUARE_HEIGHT - 1)), y + half, y + SQUARE_HEIGHT - half);
		ui.ring(markerX - half - 1, markerY - half - 1, KNOB + 2, KNOB + 2, Radius.pill(KNOB + 2), 1, 0x59000000);
		ui.ring(markerX - half, markerY - half, KNOB, KNOB, Radius.pill(KNOB), 2, 0xFFFFFFFF);
		// the hue knob: a ring exactly as wide as the bar
		int hueY = Mth.clamp(y + Math.round(hue * (SQUARE_HEIGHT - 1)), y + 3, y + SQUARE_HEIGHT - 3);
		ui.ring(barX, hueY - 3, BAR_WIDTH, 6, Radius.pill(6), 2, 0xFFFFFFFF);

		// hex field, with a swatch of the color at its left
		int fieldY = fieldY();
		int fieldMiddle = fieldY + FIELD_HEIGHT / 2;
		int fieldWidth = Math.min(FIELD_WIDTH, width);
		ui.rounded(x, fieldY, fieldWidth, FIELD_HEIGHT, Radius.SM, ui.palette.control());
		ui.ring(x, fieldY, fieldWidth, FIELD_HEIGHT, Radius.SM, 1, fieldFocused ? ui.accent : ui.palette.controlBorder());
		int swatchX = x + (FIELD_HEIGHT - FIELD_SWATCH) / 2;
		ui.rounded(swatchX, fieldMiddle - FIELD_SWATCH / 2, FIELD_SWATCH, FIELD_SWATCH, Radius.XS, 0xFF000000 | rgb());
		ui.ring(swatchX, fieldMiddle - FIELD_SWATCH / 2, FIELD_SWATCH, FIELD_SWATCH, Radius.XS, 1, Ui.withOpacity(0xFF000000, 0.25f));
		int textX = swatchX + FIELD_SWATCH + 6;
		ui.textMiddle(fieldText, textX, fieldMiddle, ui.palette.text());
		if (fieldFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(textX + ui.width(fieldText), fieldY + 5, 1, FIELD_HEIGHT - 10, ui.palette.text());
		}

		if (setting.usesMenu()) {
			// the picker is inactive while the menu color is used: it stays visible, dimmed
			ui.rounded(x - 3, y - 3, width + 6, opacityY() - ROW_GAP - y + 6, CORNER + 3, Ui.withOpacity(0xFF000000, 0.46f));
			// a short notice on top explains why it is locked
			String notice = "Locked: using the menu color";
			int noticeWidth = ui.width(notice) + 40;
			int noticeX = x + (width - noticeWidth) / 2;
			int noticeY = y + SQUARE_HEIGHT / 2 - 11;
			ui.rounded(noticeX, noticeY, noticeWidth, 22, Radius.pill(22), Ui.withOpacity(0xFF000000, 0.72f));
			ui.ring(noticeX, noticeY, noticeWidth, 22, Radius.pill(22), 1, Ui.withOpacity(0xFFFFFFFF, 0.18f));
			ui.icon("lock", noticeX + 8, noticeY + 5, 12, 0xFFFFFFFF);
			ui.text(notice, noticeX + 28, noticeY + 11 - 4, 0xFFFFFFFF);
		}

		// preset colors in their own row, evenly spaced; the chosen one has a white ring
		int presetRadius = PRESET_SIZE / 2;
		for (int i = 0; i < presets.length; i++) {
			int centerX = presetX(i);
			int centerY = presetY(i);
			ui.circle(centerX, centerY, presetRadius, 0xFF000000 | presets[i]);
			ui.ring(centerX - presetRadius, centerY - presetRadius, PRESET_SIZE, PRESET_SIZE, presetRadius, 1, Ui.withOpacity(0xFF000000, 0.25f));
			if (presets[i] == setting.get()) {
				ui.ring(centerX - presetRadius + 2, centerY - presetRadius + 2, PRESET_SIZE - 4, PRESET_SIZE - 4, presetRadius - 2, 2, 0xFFFFFFFF);
			}
		}
		drawOpacity(ui);
		drawMenuToggle(ui, mouseX, mouseY);
	}

	/** Builds the hue bar texture once, with smooth rounded ends; built from pixel rows it did not fill the corners the outline goes around. */
	private static void ensureBar() {
		if (barTexture != null) {
			return;
		}
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, BAR_TEXTURE_WIDTH, BAR_TEXTURE_HEIGHT, false);
		float radius = BAR_WIDTH / 2f;
		for (int row = 0; row < BAR_TEXTURE_HEIGHT; row++) {
			float drawnY = (row + 0.5f) / BAR_SCALE;
			int rgb = hsvToRgb(Mth.clamp(drawnY / SQUARE_HEIGHT, 0f, 1f), 1f, 1f);
			int r = rgb >> 16 & 0xFF;
			int g = rgb >> 8 & 0xFF;
			int b = rgb & 0xFF;
			// the bar is a capsule: a pixel is inside when its distance to the segment between the end centers is below the radius
			float along = Mth.clamp(drawnY, radius, SQUARE_HEIGHT - radius);
			for (int column = 0; column < BAR_TEXTURE_WIDTH; column++) {
				float drawnX = (column + 0.5f) / BAR_SCALE;
				float distance = (float) Math.hypot(drawnX - radius, drawnY - along);
				float coverage = Mth.clamp((radius - distance) * BAR_SCALE + 0.5f, 0f, 1f);
				image.setPixelABGR(column, row, (Math.round(255 * coverage) << 24) | (b << 16) | (g << 8) | r);
			}
		}
		barTexture = new SmoothTexture("Lumio color bar", image);
		Minecraft.getInstance().getTextureManager().register(BAR_ID, barTexture);
	}

	private void updateSquare(int drawnWidth) {
		Square square = square();
		if (square.hue == hue && square.drawn == drawnWidth) {
			return;
		}
		NativeImage image = square.texture.getPixels();
		for (int column = 0; column < TEXTURE_WIDTH; column++) {
			int top = hsvToRgb(hue, column / (float) (TEXTURE_WIDTH - 1), 1f);
			int r = top >> 16 & 0xFF;
			int g = top >> 8 & 0xFF;
			int b = top & 0xFF;
			// corners are cut out in texture space; the rounding is computed for the size the texture is drawn at
			float drawnX = (column + 0.5f) * drawnWidth / TEXTURE_WIDTH;
			float toSide = Math.min(drawnX, drawnWidth - drawnX);
			for (int row = 0; row < TEXTURE_HEIGHT; row++) {
				float brightness = 1f - row / (float) (TEXTURE_HEIGHT - 1);
				float drawnY = (row + 0.5f) * SQUARE_HEIGHT / TEXTURE_HEIGHT;
				float toEdge = Math.min(drawnY, SQUARE_HEIGHT - drawnY);
				float coverage = 1f;
				if (toSide < CORNER && toEdge < CORNER) {
					float dx = CORNER - toSide;
					float dy = CORNER - toEdge;
					coverage = Math.max(0f, Math.min(1f, CORNER - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f));
				}
				image.setPixelABGR(column, row, (Math.round(255 * coverage) << 24) | (Math.round(b * brightness) << 16) | (Math.round(g * brightness) << 8) | Math.round(r * brightness));
			}
		}
		square.texture.upload();
		square.hue = hue;
		square.drawn = drawnWidth;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		if (overMenuToggle(mouseX, mouseY)) {
			setting.setUsesMenu(!setting.usesMenu());
				UiSounds.toggle(setting.usesMenu());
			fieldFocused = false;
			picked();
			return true;
		}
		if (setting.usesMenu()) {
			return false; // the picker is inactive
		}
		fieldFocused = false;
		if (overOpacity(mouseX, mouseY)) {
			dragging = Drag.OPACITY;
			pickOpacity(mouseX);
			return true;
		}
		int squareWidth = squareWidth();
		int barX = x + squareWidth + GAP;
		if (mouseY >= y && mouseY < y + SQUARE_HEIGHT) {
			if (mouseX >= x && mouseX < x + squareWidth + GAP / 2) {
				dragging = Drag.SQUARE;
				pick(mouseX, mouseY);
				return true;
			}
			if (mouseX >= barX - 3 && mouseX < barX + BAR_WIDTH + 3) {
				dragging = Drag.BAR;
				pick(mouseX, mouseY);
				return true;
			}
		}
		int fieldY = fieldY();
		if (mouseY >= fieldY && mouseY < fieldY + FIELD_HEIGHT) {
			if (mouseX >= x && mouseX < x + Math.min(FIELD_WIDTH, width)) {
				fieldFocused = true;
				return true;
			}
		}
		for (int i = 0; i < presets.length; i++) {
			if (Math.hypot(mouseX - presetX(i), mouseY - presetY(i)) <= PRESET_SIZE / 2f + 1f) {
				adopt(presets[i]);
				picked();
				return true;
			}
		}
		return false;
	}

	private void pick(double mouseX, double mouseY) {
		float fy = Mth.clamp((float) ((mouseY - y) / (SQUARE_HEIGHT - 1)), 0f, 1f);
		if (dragging == Drag.SQUARE) {
			saturation = Mth.clamp((float) ((mouseX - x) / (squareWidth() - 1)), 0f, 1f);
			value = 1f - fy;
		} else if (dragging == Drag.BAR) {
			hue = fy;
		} else {
			return;
		}
		picked();
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		if (dragging == Drag.OPACITY) {
			pickOpacity(mouseX);
			return true;
		}
		if (dragging != Drag.NONE) {
			pick(mouseX, mouseY);
			return true;
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		dragging = Drag.NONE;
	}

	@Override
	public boolean wantsKeyboard() {
		return fieldFocused;
	}

	@Override
	public void loseFocus() {
		fieldFocused = false;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!fieldFocused || fieldText.length() >= 7) {
			return false;
		}
		char c = (char) codepoint;
		if (Character.digit(c, 16) >= 0 || (c == '#' && fieldText.isEmpty())) {
			fieldText += Character.toUpperCase(c);
			commitField();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!fieldFocused) {
			return false;
		}
		if (key == InputConstants.KEY_BACKSPACE && !fieldText.isEmpty()) {
			fieldText = fieldText.substring(0, fieldText.length() - 1);
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE) {
			fieldFocused = false;
			fieldText = ColorSetting.toHex(setting.get());
			return true;
		}
		return false;
	}

	/** Applies the field's text as the color as soon as it is a complete hex color. */
	private void commitField() {
		int rgb = ColorSetting.parseHex(fieldText);
		if (rgb >= 0) {
			String typed = fieldText;
			adopt(rgb);
			fieldText = typed;
			setting.set(rgb);
			onChanged.run();
		}
	}

	static int hsvToRgb(float hue, float saturation, float value) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		int sector = (int) h;
		float f = h - sector;
		float p = value * (1f - saturation);
		float q = value * (1f - saturation * f);
		float t = value * (1f - saturation * (1f - f));

		float r, g, b;
		switch (sector % 6) {
			case 0 -> { r = value; g = t; b = p; }
			case 1 -> { r = q; g = value; b = p; }
			case 2 -> { r = p; g = value; b = t; }
			case 3 -> { r = p; g = q; b = value; }
			case 4 -> { r = t; g = p; b = value; }
			default -> { r = value; g = p; b = q; }
		}
		return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}
}
