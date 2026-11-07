package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.SmoothTexture;
import dev.tim1337.lumio.ui.Swatch;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Choices as a row of colored circles with a ring around the selected one, like the macOS accent colors. A color of the player's own, which
 * opens a color picker, has a rainbow ring around it, so it reads as a picker and not as one more color.
 */
public class SwatchControl<E extends Enum<E> & Swatch> extends Control {
	private static final int RADIUS = 8;
	private static final int STEP = 24;
	private static final int LINE = 24;
	/** The rainbow ring of a color of the player's own: its width, and the gap between it and the color inside. */
	private static final int RING_WIDTH = 2;
	private static final int RING_GAP = 1;
	/** Size of the ring's texture: larger than drawn, so it stays smooth at any GUI scale. */
	private static final int RING_TEXTURE = 64;
	private static final Identifier RING_ID = Lumio.id("swatch_ring");
	private static SmoothTexture ringTexture;

	private final EnumSetting<E> setting;
	private final Runnable onChanged;

	public SwatchControl(EnumSetting<E> setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return SwatchRow.lines(setting.choices().size(), width, STEP) * LINE;
	}

	@Override
	public boolean keepsWidth() {
		return true;
	}

	@Override
	public boolean leftOnOwnLine() {
		return true;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, SwatchRow.fullWidth(setting.choices().size(), STEP));
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		List<E> choices = setting.choices();
		for (int i = 0; i < choices.size(); i++) {
			int centerX = x + STEP / 2 + SwatchRow.cellX(i, width, STEP);
			int centerY = y + LINE / 2 + SwatchRow.line(i, width, STEP) * LINE;
			boolean selected = choices.get(i) == setting.get();
			boolean over = SwatchRow.cellAt(mouseX - x, mouseY - y, choices.size(), width, STEP, LINE) == i;
			float h = hover(ui, i, over);
			ui.circle(centerX, centerY, RADIUS + 1 + Math.round(2 * h), Ui.withOpacity(0xFF000000 | choices.get(i).rgb(), 0.25f * h));
			if (selected) {
				ui.circle(centerX, centerY, RADIUS + 3, Ui.withOpacity(0xFF000000 | choices.get(i).rgb(), 0.35f));
			}
			if (choices.get(i).picker()) {
				ensureRing();
				ui.graphics.blit(RenderPipelines.GUI_TEXTURED, RING_ID, centerX - RADIUS, centerY - RADIUS, 0f, 0f, 2 * RADIUS, 2 * RADIUS, RING_TEXTURE,
					RING_TEXTURE, RING_TEXTURE, RING_TEXTURE, Ui.faded(0xFFFFFFFF));
				ui.circle(centerX, centerY, RADIUS - RING_WIDTH - RING_GAP, 0xFF000000 | choices.get(i).rgb());
			} else {
				ui.circle(centerX, centerY, RADIUS, 0xFF000000 | choices.get(i).rgb());
			}
			if (selected) {
				ui.circle(centerX, centerY, 3, 0xFFFFFFFF);
			}
		}
	}

	/** Builds the rainbow ring once: the hues round the circle from the top, with soft edges. */
	private static void ensureRing() {
		if (ringTexture != null) {
			return;
		}
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, RING_TEXTURE, RING_TEXTURE, false);
		float outer = RING_TEXTURE / 2f;
		float inner = outer * (RADIUS - RING_WIDTH) / RADIUS;
		float edge = RING_TEXTURE / (2f * RADIUS);
		for (int row = 0; row < RING_TEXTURE; row++) {
			for (int column = 0; column < RING_TEXTURE; column++) {
				float dx = column + 0.5f - outer;
				float dy = row + 0.5f - outer;
				float distance = (float) Math.hypot(dx, dy);
				float coverage = Mth.clamp((outer - distance) / edge + 0.5f, 0f, 1f) * Mth.clamp((distance - inner) / edge + 0.5f, 0f, 1f);
				float hue = (float) (Math.atan2(dx, -dy) / (2.0 * Math.PI)) + 1f;
				int rgb = ColorControl.hsvToRgb(hue, 0.85f, 1f);
				image.setPixelABGR(column, row, (Math.round(255 * coverage) << 24) | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
			}
		}
		ringTexture = new SmoothTexture("Lumio swatch ring", image);
		Minecraft.getInstance().getTextureManager().register(RING_ID, ringTexture);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		List<E> choices = setting.choices();
		int index = SwatchRow.cellAt((int) Math.floor(mouseX) - x, (int) Math.floor(mouseY) - y, choices.size(), width, STEP, LINE);
		if (index >= 0 && choices.get(index) != setting.get()) {
			setting.set(choices.get(index));
			UiSounds.tick((float) index / Math.max(1, choices.size() - 1));
			onChanged.run();
		}
		return true;
	}
}
