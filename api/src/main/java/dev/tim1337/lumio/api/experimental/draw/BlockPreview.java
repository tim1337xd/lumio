package dev.tim1337.lumio.api.experimental.draw;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * A block with a highlight in one of the {@link BlockStyle} styles, drawn as a picture for the preview beside a settings page: what a block overlay does in the world, as far as a picture can say it. Three faces of the game's own stone, each drawn
 * with the pose leaned over.
 */
public final class BlockPreview {
	private static final Identifier STONE = Identifier.withDefaultNamespace("textures/block/stone.png");
	private static final int TEXTURE = 16;
	/** The block's half width as a share of the smaller side of the preview. */
	private static final float SIZE_SHARE = 0.3f;
	private static final float MOST_SIZE = 110f;
	private static final float HEIGHT_FACTOR = 1.12f;
	private static final int LEFT_SHADE = 0xFFD0D0D0;
	private static final int RIGHT_SHADE = 0xFFA4A4A4;
	/** The glow: a wide and a narrower faint line around the sharp one, and how strong the block is tinted. */
	private static final float GLOW_WIDE = 4.5f;
	private static final float GLOW_NARROW = 2.5f;
	private static final float GLOW_WIDE_OPACITY = 0.14f;
	private static final float GLOW_NARROW_OPACITY = 0.3f;
	private static final float GLOW_LIGHT_OPACITY = 0.35f;
	private static final float SHADER_SECONDS = 2.5f;
	private static final double NANOS = 1e9;
	/** One unit of the width settings of the styles, in menu pixels: lines in the world are thin next to a block this large. */
	private static final float LINE_SCALE = 0.6f;

	private static final Matrix3x2f FACE = new Matrix3x2f();

	private BlockPreview() {
	}

	/**
	 * Draws the block in the middle of a preview.
	 *
	 * @param style     the style
	 * @param rgb       its color as 0xRRGGBB
	 * @param lineWidth the width of its lines, as in the style's setting
	 * @param fill      how strongly its faces are tinted, from 0 to 1
	 * @param speed     how fast the moving light goes, 1 being the usual
	 */
	public static void draw(Canvas ui, int x, int y, int width, int height, BlockStyle style, int rgb, float lineWidth, float fill, float speed) {
		float half = Math.min(MOST_SIZE, Math.min(width, height) * SIZE_SHARE);
		float centerX = x + width / 2f;
		float down = half * HEIGHT_FACTOR;
		float slope = half / 2f;
		float top = y + (height - 2f * slope - down) / 2f;

		GuiGraphicsExtractor graphics = ui.graphics();
		face(ui, graphics, centerX, top, half, slope, -half, slope, 0xFFFFFFFF, 0);
		face(ui, graphics, centerX - half, top + slope, half, slope, 0f, down, LEFT_SHADE, 0);
		face(ui, graphics, centerX, top + 2f * slope, half, -slope, 0f, down, RIGHT_SHADE, 0);

		int color = 0xFF000000 | rgb;
		switch (style) {
			case OUTLINE -> edges(ui, centerX, top, half, slope, down, lineWidth * LINE_SCALE, color);
			case FILL -> tint(ui, graphics, centerX, top, half, slope, down, opacity(color, fill));
			case GLOW -> {
				tint(ui, graphics, centerX, top, half, slope, down, opacity(color, fill * GLOW_LIGHT_OPACITY));
				float line = lineWidth * LINE_SCALE;
				edges(ui, centerX, top, half, slope, down, line + GLOW_WIDE, opacity(color, GLOW_WIDE_OPACITY));
				edges(ui, centerX, top, half, slope, down, line + GLOW_NARROW, opacity(color, GLOW_NARROW_OPACITY));
				edges(ui, centerX, top, half, slope, down, line, color);
			}
			case SHADER -> {
				// the moving light of the world's shader, between the color and the menu's accent
				double seconds = System.nanoTime() / NANOS * speed;
				float wave = (float) (0.5 + 0.5 * Math.sin(seconds * 2.0 * Math.PI / SHADER_SECONDS));
				int moving = mix(color, 0xFF000000 | MainSettings.accentRgb(), wave);
				tint(ui, graphics, centerX, top, half, slope, down, opacity(moving, fill));
				edges(ui, centerX, top, half, slope, down, lineWidth * LINE_SCALE, moving);
			}
		}
	}

	/** One face, from its corner along two sides; with a color it is tinted, otherwise it shows the stone. */
	private static void face(Canvas ui, GuiGraphicsExtractor graphics, float cornerX, float cornerY, float acrossX, float acrossY, float downX, float downY, int shade, int tint) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		FACE.set(acrossX / TEXTURE, acrossY / TEXTURE, downX / TEXTURE, downY / TEXTURE, cornerX, cornerY);
		pose.mul(FACE);
		if (tint == 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, STONE, 0, 0, 0f, 0f, TEXTURE, TEXTURE, TEXTURE, TEXTURE, TEXTURE, TEXTURE, shade);
		} else {
			ui.fill(0, 0, TEXTURE, TEXTURE, tint);
		}
		pose.popMatrix();
	}

	private static void tint(Canvas ui, GuiGraphicsExtractor graphics, float centerX, float top, float half, float slope, float down, int color) {
		face(ui, graphics, centerX, top, half, slope, -half, slope, 0, color);
		face(ui, graphics, centerX - half, top + slope, half, slope, 0f, down, 0, color);
		face(ui, graphics, centerX, top + 2f * slope, half, -slope, 0f, down, 0, color);
	}

	/** The nine edges one sees. */
	private static void edges(Canvas ui, float centerX, float top, float half, float slope, float down, float thickness, int color) {
		float left = centerX - half;
		float right = centerX + half;
		float middle = top + slope;
		float front = top + 2f * slope;
		line(ui, centerX, top, right, middle, thickness, color);
		line(ui, right, middle, centerX, front, thickness, color);
		line(ui, centerX, front, left, middle, thickness, color);
		line(ui, left, middle, centerX, top, thickness, color);
		line(ui, left, middle, left, middle + down, thickness, color);
		line(ui, right, middle, right, middle + down, thickness, color);
		line(ui, centerX, front, centerX, front + down, thickness, color);
		line(ui, left, middle + down, centerX, front + down, thickness, color);
		line(ui, centerX, front + down, right, middle + down, thickness, color);
	}

	private static void line(Canvas ui, float fromX, float fromY, float toX, float toY, float thickness, int color) {
		float length = (float) Math.hypot(toX - fromX, toY - fromY);
		Matrix3x2fStack pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate(fromX, fromY);
		pose.rotate((float) Math.atan2(toY - fromY, toX - fromX));
		ui.roundedSmooth(-thickness / 2f, -thickness / 2f, length + thickness, thickness, thickness / 2f, color);
		pose.popMatrix();
	}

	private static int opacity(int color, float share) {
		return Math.round(Math.max(0f, Math.min(1f, share)) * 255f) << 24 | color & 0xFFFFFF;
	}

	private static int mix(int from, int to, float share) {
		int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * share);
		int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * share);
		int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * share);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
