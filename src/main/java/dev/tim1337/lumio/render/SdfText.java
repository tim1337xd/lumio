package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Text and shapes of the lyrics in the world drawn from distances instead of pictures: letters from a signed distance field atlas of the lyrics'
 * own font (Inter Bold, bundled), made once on first use on a thread of its own, and tiles and strokes as rounded rectangles whose edge
 * the shader works out per pixel. Both stay smooth at any size, distance and angle. Until the atlas is ready, or where its shaders did not
 * compile, the lyrics are drawn as before.
 */
public final class SdfText {
	/** The atlas: glyphs of this many pixels per em, distances over this many pixels, in a square of this many pixels. */
	public static final int EM = 48;
	public static final int RANGE = 6;
	public static final int SIZE = 1024;
	/** How far past a shape its quads reach, as a share of its height and at least this many units: room for the anti-aliased edge. */
	private static final float SHAPE_MARGIN = 0.12f;
	private static final float LEAST_MARGIN = 0.8f;
	private static final Identifier ATLAS = Lumio.id("lyric_sdf_atlas");
	private static final Identifier ICONS = Lumio.id("cutout_sdf_icons");
	private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
	private static final int MOST_LAYOUTS = 3000;
	private static final Map<String, float[]> LAYOUTS = new ConcurrentHashMap<>();
	private static volatile SdfAtlas atlas;
	private static volatile boolean failed;
	private static boolean started;
	private static boolean uploaded;
	private static Font font;
	private static RenderType text;
	private static RenderType textFlat;
	private static RenderType glow;
	private static RenderType shape;
	private static RenderType icons;
	private static RenderType iconsFlat;

	private SdfText() {
	}

	/** @return whether text can be drawn from distances now; the first call starts making the atlas */
	public static boolean ready() {
		if (failed || !OutlineRenderTypes.sdfAvailable()) {
			return false;
		}
		if (!started) {
			started = true;
			Thread maker = new Thread(SdfText::make, "Lumio lyric letters");
			maker.setDaemon(true);
			maker.setPriority(Thread.MIN_PRIORITY);
			maker.start();
		}
		SdfAtlas made = atlas;
		if (made == null) {
			return false;
		}
		if (!uploaded) {
			uploaded = true;
			upload(made);
		}
		return text != null;
	}

	/** Makes the atlas off the render thread. */
	private static void make() {
		try (InputStream in = SdfText.class.getResourceAsStream("/assets/lumio/font/inter_bold.ttf")) {
			if (in == null) {
				failed = true;
				return;
			}
			Font face = Font.createFont(Font.TRUETYPE_FONT, in);
			// laid out like the ink the tiles are measured by (CutoutInk), so the letters stay inside their tile
			font = face.deriveFont(1000f);
			atlas = SdfAtlas.build(face, SdfAtlas.LATIN, EM, RANGE, SIZE);
		} catch (Exception | Error e) {
			failed = true;
			Lumio.LOGGER.warn("Lumio could not make the letters of the lyrics from distances; they are drawn as before", e);
		}
	}

	private static void upload(SdfAtlas made) {
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, made.width, made.height, false);
		for (int y = 0; y < made.height; y++) {
			for (int x = 0; x < made.width; x++) {
				int at = (y * made.width + x) * 4;
				int r = made.rgba[at] & 0xFF;
				int g = made.rgba[at + 1] & 0xFF;
				int b = made.rgba[at + 2] & 0xFF;
				int a = made.rgba[at + 3] & 0xFF;
				image.setPixelABGR(x, y, a << 24 | b << 16 | g << 8 | r);
			}
		}
		register(ATLAS, "Lumio lyric letters", image);
		text = OutlineRenderTypes.sdfText(ATLAS);
		textFlat = OutlineRenderTypes.sdfTextFlat(ATLAS);
		glow = OutlineRenderTypes.sdfGlow(ATLAS);
		shape = OutlineRenderTypes.sdfShape(ATLAS);
		byte[] field = SdfIcons.field(CutoutIcons.sheet(SdfIcons.CELL), SdfIcons.RANGE);
		int across = CutoutIcons.ACROSS * SdfIcons.CELL;
		int rows = (CutoutIcons.COUNT + CutoutIcons.ACROSS - 1) / CutoutIcons.ACROSS * SdfIcons.CELL;
		NativeImage iconImage = new NativeImage(NativeImage.Format.RGBA, across, rows, false);
		for (int y = 0; y < rows; y++) {
			for (int x = 0; x < across; x++) {
				int value = field[y * across + x] & 0xFF;
				iconImage.setPixelABGR(x, y, value << 24 | value << 16 | value << 8 | value);
			}
		}
		register(ICONS, "Lumio cut-out symbols", iconImage);
		icons = OutlineRenderTypes.sdfText(ICONS);
		iconsFlat = OutlineRenderTypes.sdfTextFlat(ICONS);
	}

	private static void register(Identifier id, String name, NativeImage image) {
		DynamicTexture texture = new DynamicTexture(() -> name, image);
		texture.upload();
		Minecraft.getInstance().getTextureManager().register(id, texture);
	}

	/** @return letters that hide what is behind them where they cover */
	public static RenderType text() {
		return text;
	}

	/** @return letters drawn over something else (a tile), hiding nothing */
	public static RenderType textFlat() {
		return textFlat;
	}

	public static RenderType glow() {
		return glow;
	}

	public static RenderType shapes() {
		return shape;
	}

	public static RenderType icons() {
		return icons;
	}

	public static RenderType iconsFlat() {
		return iconsFlat;
	}

	/** @return the atlas of the letters, or null while it is being made */
	public static SdfAtlas atlas() {
		return atlas;
	}

	/** @return whether every letter of a text is in the atlas */
	public static boolean covers(String text) {
		SdfAtlas made = atlas;
		return made != null && made.covers(text);
	}

	/** @return where each character's glyph starts from the text's start, in em, as the font lays the text out */
	private static float[] layout(String text, SdfAtlas made) {
		float[] known = LAYOUTS.get(text);
		if (known != null) {
			return known;
		}
		float[] xs = new float[text.length()];
		boolean done = false;
		Font face = font;
		if (face != null) {
			try {
				char[] chars = text.toCharArray();
				GlyphVector vector = face.layoutGlyphVector(CONTEXT, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
				if (vector.getNumGlyphs() == chars.length) {
					for (int i = 0; i < chars.length; i++) {
						xs[vector.getGlyphCharIndex(i)] = (float) vector.getGlyphPosition(i).getX() / 1000f;
					}
					done = true;
				}
			} catch (Exception | Error e) {
				done = false;
			}
		}
		if (!done) {
			float x = 0f;
			for (int i = 0; i < text.length(); i++) {
				xs[i] = x;
				SdfAtlas.Glyph glyph = made.glyph(text.charAt(i));
				x += glyph == null ? 0.25f : glyph.advance();
			}
		}
		if (LAYOUTS.size() > MOST_LAYOUTS) {
			LAYOUTS.clear();
		}
		LAYOUTS.put(text, xs);
		return xs;
	}

	/**
	 * Emits a text's letters.
	 *
	 * @param penX     where the text starts, on its baseline at {@code baseline}
	 * @param em       how large an em is, in the pose's units
	 */
	public static void emit(VertexConsumer consumer, PoseStack.Pose pose, String text, float penX, float baseline, float em, int color) {
		SdfAtlas made = atlas;
		if (made == null) {
			return;
		}
		float[] xs = layout(text, made);
		float texel = 1f / made.width;
		for (int i = 0; i < text.length(); i++) {
			SdfAtlas.Glyph glyph = made.glyph(text.charAt(i));
			if (glyph == null || glyph.width() == 0) {
				continue;
			}
			float x = penX + xs[i] * em;
			float left = x + glyph.left() * em;
			float right = x + glyph.right() * em;
			float top = baseline + glyph.top() * em;
			float bottom = baseline + glyph.bottom() * em;
			float u0 = glyph.atlasX() * texel;
			float v0 = glyph.atlasY() * texel;
			float u1 = (glyph.atlasX() + glyph.width()) * texel;
			float v1 = (glyph.atlasY() + glyph.height()) * texel;
			consumer.addVertex(pose, left, top, 0f).setUv(u0, v0).setColor(color);
			consumer.addVertex(pose, left, bottom, 0f).setUv(u0, v1).setColor(color);
			consumer.addVertex(pose, right, bottom, 0f).setUv(u1, v1).setColor(color);
			consumer.addVertex(pose, right, top, 0f).setUv(u1, v0).setColor(color);
		}
	}

	/** Emits a symbol of the Cut-out preset in a square around a place, from its distance field. */
	public static void icon(VertexConsumer consumer, PoseStack.Pose pose, int icon, float x, float y, float half, int color) {
		CutoutSheets.icon(consumer, pose, icon, x, y, half, color);
	}

	/**
	 * Emits a rectangle with rounded corners as nine quads, whose coordinates tell the shader how far past the inner rectangle each point is,
	 * in units of the radius: its edge and corners are worked out per pixel.
	 */
	public static void roundedRect(VertexConsumer consumer, PoseStack.Pose pose, float left, float top, float right, float bottom, float radius, int color) {
		float r = Math.max(0.05f, Math.min(radius, Math.min(right - left, bottom - top) / 2f));
		float margin = Math.max(LEAST_MARGIN, (bottom - top) * SHAPE_MARGIN);
		float[] xs = {left - margin, left + r, right - r, right + margin};
		float[] ys = {top - margin, top + r, bottom - r, bottom + margin};
		float outer = (r + margin) / r;
		float[] us = {outer, 0f, 0f, outer};
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				corner(consumer, pose, xs[column], ys[row], us[column], us[row], color);
				corner(consumer, pose, xs[column], ys[row + 1], us[column], us[row + 1], color);
				corner(consumer, pose, xs[column + 1], ys[row + 1], us[column + 1], us[row + 1], color);
				corner(consumer, pose, xs[column + 1], ys[row], us[column + 1], us[row], color);
			}
		}
	}

	private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int color) {
		consumer.addVertex(pose, x, y, 0f).setUv(u, v).setColor(color);
	}
}
