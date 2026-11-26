package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;

/**
 * The pictures of the Cut-out preset of the lyrics in the world besides its letters: the sheet of symbols ({@link CutoutIcons}) and the plain
 * sheet the tiles behind words are cut from. Made once, when first drawn.
 */
public final class CutoutSheets {
	/** A symbol's cell in the sheet, in pixels. */
	private static final int CELL = 96;
	private static final Identifier ICONS = Lumio.id("cutout_icons");
	private static final Identifier PLAIN = Lumio.id("cutout_plain");
	private static RenderType icons;
	private static RenderType tiles;

	private CutoutSheets() {
	}

	/** @return whether the pictures can be drawn */
	public static boolean available() {
		return OutlineRenderTypes.available();
	}

	/** @return the render type of the symbols: like letters, what a symbol covers hides what is behind it */
	public static RenderType icons() {
		make();
		return icons;
	}

	/** @return the render type of the tiles: flat color, behind the letters, hiding nothing */
	public static RenderType tiles() {
		make();
		return tiles;
	}

	private static void make() {
		if (icons != null) {
			return;
		}
		BufferedImage sheet = CutoutIcons.sheet(CELL);
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, sheet.getWidth(), sheet.getHeight(), false);
		for (int y = 0; y < sheet.getHeight(); y++) {
			for (int x = 0; x < sheet.getWidth(); x++) {
				image.setPixelABGR(x, y, sheet.getRGB(x, y) & 0xFF000000 | 0xFFFFFF);
			}
		}
		icons = OutlineRenderTypes.lyricMipmapped(register(ICONS, "Lumio cut-out symbols", image));
		NativeImage plain = new NativeImage(NativeImage.Format.RGBA, 4, 4, false);
		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < 4; x++) {
				plain.setPixelABGR(x, y, 0xFFFFFFFF);
			}
		}
		tiles = OutlineRenderTypes.glow(register(PLAIN, "Lumio cut-out tile", plain));
	}

	private static Identifier register(Identifier id, String name, NativeImage image) {
		DynamicTexture texture = new DynamicTexture(() -> name, image);
		texture.upload();
		Minecraft.getInstance().getTextureManager().register(id, texture);
		return id;
	}

	/** Emits a symbol in a square around a place. */
	public static void icon(VertexConsumer consumer, PoseStack.Pose pose, int icon, float x, float y, float half, int color) {
		int rows = (CutoutIcons.COUNT + CutoutIcons.ACROSS - 1) / CutoutIcons.ACROSS;
		float u0 = icon % CutoutIcons.ACROSS / (float) CutoutIcons.ACROSS;
		float v0 = icon / CutoutIcons.ACROSS / (float) rows;
		float u1 = u0 + 1f / CutoutIcons.ACROSS;
		float v1 = v0 + 1f / rows;
		consumer.addVertex(pose, x - half, y - half, 0f).setUv(u0, v0).setColor(color);
		consumer.addVertex(pose, x + half, y - half, 0f).setUv(u1, v0).setColor(color);
		consumer.addVertex(pose, x + half, y + half, 0f).setUv(u1, v1).setColor(color);
		consumer.addVertex(pose, x - half, y + half, 0f).setUv(u0, v1).setColor(color);
	}

	/**
	 * Emits a tile: a flat rectangle of color with clean edges. Its edges are a thin strip that runs out into nothing, so they are smooth at
	 * every distance and angle without a picture of an edge, whose pixels would show when the tile is large.
	 *
	 * @param feather how wide that strip is, outside the rectangle
	 */
	public static void tile(VertexConsumer consumer, PoseStack.Pose pose, float left, float top, float right, float bottom, float feather, int color) {
		int none = color & 0xFFFFFF;
		float outLeft = left - feather;
		float outTop = top - feather;
		float outRight = right + feather;
		float outBottom = bottom + feather;
		corner(consumer, pose, left, top, color);
		corner(consumer, pose, right, top, color);
		corner(consumer, pose, right, bottom, color);
		corner(consumer, pose, left, bottom, color);
		// the four strips around it, each from the rectangle's edge out to nothing; they meet in the corners
		corner(consumer, pose, outLeft, outTop, none);
		corner(consumer, pose, outRight, outTop, none);
		corner(consumer, pose, right, top, color);
		corner(consumer, pose, left, top, color);

		corner(consumer, pose, right, top, color);
		corner(consumer, pose, outRight, outTop, none);
		corner(consumer, pose, outRight, outBottom, none);
		corner(consumer, pose, right, bottom, color);

		corner(consumer, pose, left, bottom, color);
		corner(consumer, pose, right, bottom, color);
		corner(consumer, pose, outRight, outBottom, none);
		corner(consumer, pose, outLeft, outBottom, none);

		corner(consumer, pose, outLeft, outTop, none);
		corner(consumer, pose, left, top, color);
		corner(consumer, pose, left, bottom, color);
		corner(consumer, pose, outLeft, outBottom, none);
	}

	/** How many steps a rounded corner is drawn in. */
	private static final int CORNER_STEPS = 6;
	private static final float[] COS = new float[CORNER_STEPS + 1];
	private static final float[] SIN = new float[CORNER_STEPS + 1];

	static {
		for (int i = 0; i <= CORNER_STEPS; i++) {
			double angle = Math.PI / 2.0 * i / CORNER_STEPS;
			COS[i] = (float) Math.cos(angle);
			SIN[i] = (float) Math.sin(angle);
		}
	}

	/**
	 * A tile with rounded corners: a filled rectangle whose corners are quarter circles of a radius, with the same soft strip around its
	 * whole outline. A radius of 0 is the plain rectangle of {@link #tile}.
	 *
	 * @param radius the corners' radius, at most half the shorter side
	 */
	public static void roundedTile(VertexConsumer consumer, PoseStack.Pose pose, float left, float top, float right, float bottom, float radius, float feather,
								   int color) {
		float r = Math.min(radius, Math.min(right - left, bottom - top) / 2f);
		if (r <= 0.01f) {
			tile(consumer, pose, left, top, right, bottom, feather, color);
			return;
		}
		int none = color & 0xFFFFFF;
		// the inner rectangles: across the whole width between the corners' rows, and the rows of the corners between them
		quad(consumer, pose, left, top + r, right, bottom - r, color);
		quad(consumer, pose, left + r, top, right - r, top + r, color);
		quad(consumer, pose, left + r, bottom - r, right - r, bottom, color);
		// the four corners, each a fan from its middle, with its soft strip outside
		float[] middlesX = {right - r, left + r, left + r, right - r};
		float[] middlesY = {top + r, top + r, bottom - r, bottom - r};
		float[] signsX = {1f, -1f, -1f, 1f};
		float[] signsY = {-1f, -1f, 1f, 1f};
		for (int c = 0; c < 4; c++) {
			for (int i = 0; i < CORNER_STEPS; i++) {
				float ax = middlesX[c] + signsX[c] * COS[i] * r;
				float ay = middlesY[c] + signsY[c] * SIN[i] * r;
				float bx = middlesX[c] + signsX[c] * COS[i + 1] * r;
				float by = middlesY[c] + signsY[c] * SIN[i + 1] * r;
				corner(consumer, pose, middlesX[c], middlesY[c], color);
				corner(consumer, pose, middlesX[c], middlesY[c], color);
				corner(consumer, pose, ax, ay, color);
				corner(consumer, pose, bx, by, color);
				corner(consumer, pose, ax, ay, color);
				corner(consumer, pose, bx, by, color);
				corner(consumer, pose, middlesX[c] + signsX[c] * COS[i + 1] * (r + feather), middlesY[c] + signsY[c] * SIN[i + 1] * (r + feather), none);
				corner(consumer, pose, middlesX[c] + signsX[c] * COS[i] * (r + feather), middlesY[c] + signsY[c] * SIN[i] * (r + feather), none);
			}
		}
		// the soft strips along the straight edges
		strip(consumer, pose, left + r, top, right - r, top, 0f, -feather, color, none);
		strip(consumer, pose, left + r, bottom, right - r, bottom, 0f, feather, color, none);
		strip(consumer, pose, left, top + r, left, bottom - r, -feather, 0f, color, none);
		strip(consumer, pose, right, top + r, right, bottom - r, feather, 0f, color, none);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float left, float top, float right, float bottom, int color) {
		corner(consumer, pose, left, top, color);
		corner(consumer, pose, right, top, color);
		corner(consumer, pose, right, bottom, color);
		corner(consumer, pose, left, bottom, color);
	}

	/** A soft strip from an edge (from one point to another) out by an offset, fading to nothing. */
	private static void strip(VertexConsumer consumer, PoseStack.Pose pose, float x1, float y1, float x2, float y2, float outX, float outY, int color, int none) {
		corner(consumer, pose, x1, y1, color);
		corner(consumer, pose, x2, y2, color);
		corner(consumer, pose, x2 + outX, y2 + outY, none);
		corner(consumer, pose, x1 + outX, y1 + outY, none);
	}

	private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int color) {
		consumer.addVertex(pose, x, y, 0f).setUv(0.5f, 0.5f).setColor(color);
	}
}
