package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.Lumio;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Media control glyphs (previous, play, pause, next) rendered as small white textures with soft rounded corners and anti-aliased edges at screen
 * resolution, so they are as sharp as the text at every menu scale.
 */
public final class Glyphs {
	public static final int PREVIOUS = 0;
	public static final int PLAY = 1;
	public static final int PAUSE = 2;
	public static final int NEXT = 3;
	/** The shapes between play and pause, in this many steps, each a texture of its own; their kinds follow the four glyphs. */
	private static final int MORPH_STEPS = 12;
	private static final int MORPH_BASE = 16;

	private static final Map<Long, Identifier> TEXTURES = new HashMap<>();

	private Glyphs() {
	}

	/** Draws the glyph centered at a position, sized to fit a box of the given size in menu pixels. */
	public static void draw(Ui ui, int kind, float centreX, float centreY, float size, int color) {
		int scale = Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * Ui.zoom()));
		int pixels = Math.max(8, Math.round(size * scale));
		Identifier id = texture(kind, pixels);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / scale, 1f / scale);
		int x = Math.round(centreX * scale - pixels / 2f);
		int y = Math.round(centreY * scale - pixels / 2f);
		ui.graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, pixels, pixels, pixels, pixels, pixels, pixels, Ui.faded(color));
		pose.popMatrix();
	}

	/**
	 * Draws the play/pause glyph part way between the two shapes: at 0 the play triangle, at 1 the two pause bars, in between a blend of both
	 * outlines, so the icon morphs instead of switching.
	 */
	public static void drawMorph(Ui ui, float towardsPause, float centreX, float centreY, float size, int color) {
		int step = Math.round(Mth.clamp(towardsPause, 0f, 1f) * MORPH_STEPS);
		draw(ui, step == 0 ? PLAY : step == MORPH_STEPS ? PAUSE : MORPH_BASE + step, centreX, centreY, size, color);
	}

	private static Identifier texture(int kind, int size) {
		long key = (long) kind << 32 | size;
		Identifier id = TEXTURES.get(key);
		if (id != null) {
			return id;
		}
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		float s = size;
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				float x = (px + 0.5f) / s;
				float y = (py + 0.5f) / s;
				float d = distance(kind, x, y) * s;
				float coverage = Mth.clamp(0.5f - d, 0f, 1f);
				image.setPixelABGR(px, py, (Math.round(coverage * 255f) << 24) | 0xFFFFFF);
			}
		}
		id = Lumio.id("glyph_" + kind + "_" + size);
		Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture("Lumio glyph", image));
		TEXTURES.put(key, id);
		return id;
	}

	/** @return the signed distance from a point in the unit square to the glyph edge, negative inside */
	private static float distance(int kind, float x, float y) {
		if (kind > MORPH_BASE) {
			// blending the two outlines' distances gives a shape that flows from one into the other
			float t = (kind - MORPH_BASE) / (float) MORPH_STEPS;
			t = t * t * (3f - 2f * t);
			return Mth.lerp(t, distance(PLAY, x, y), distance(PAUSE, x, y));
		}
		return switch (kind) {
			case PLAY -> triangle(x, y, 0.30f, 0.24f, 0.30f, 0.76f, 0.74f, 0.50f, 0.07f);
			case PAUSE -> Math.min(box(x, y, 0.30f, 0.22f, 0.44f, 0.78f, 0.06f), box(x, y, 0.56f, 0.22f, 0.70f, 0.78f, 0.06f));
			case NEXT -> Math.min(triangle(x, y, 0.20f, 0.26f, 0.20f, 0.74f, 0.62f, 0.50f, 0.06f), box(x, y, 0.70f, 0.22f, 0.82f, 0.78f, 0.05f));
			default -> Math.min(triangle(x, y, 0.80f, 0.26f, 0.80f, 0.74f, 0.38f, 0.50f, 0.06f), box(x, y, 0.18f, 0.22f, 0.30f, 0.78f, 0.05f));
		};
	}

	/** @return the signed distance to a rounded box between two corners with the given corner radius */
	private static float box(float x, float y, float left, float top, float right, float bottom, float radius) {
		float hx = (right - left) / 2f - radius;
		float hy = (bottom - top) / 2f - radius;
		float dx = Math.abs(x - (left + right) / 2f) - hx;
		float dy = Math.abs(y - (top + bottom) / 2f) - hy;
		float outside = (float) Math.hypot(Math.max(dx, 0f), Math.max(dy, 0f));
		return outside + Math.min(Math.max(dx, dy), 0f) - radius;
	}

	/** @return the signed distance to a triangle with rounded corners: the given triangle grown by the radius on every side */
	private static float triangle(float x, float y, float ax, float ay, float bx, float by, float cx, float cy, float radius) {
		float[] px = {ax, bx, cx};
		float[] py = {ay, by, cy};
		float best = Float.MAX_VALUE;
		boolean outside = false;
		float orientation = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
		for (int i = 0; i < 3; i++) {
			int j = (i + 1) % 3;
			float ex = px[j] - px[i];
			float ey = py[j] - py[i];
			float wx = x - px[i];
			float wy = y - py[i];
			float t = Mth.clamp((wx * ex + wy * ey) / (ex * ex + ey * ey), 0f, 1f);
			float dx = wx - ex * t;
			float dy = wy - ey * t;
			best = Math.min(best, dx * dx + dy * dy);
			// which side of the edge the point lies on: inside, all three agree
			float side = ex * wy - ey * wx;
			if (side * orientation < 0f) {
				outside = true;
			}
		}
		float distance = (float) Math.sqrt(best);
		return (outside ? distance : -distance) - radius;
	}
}
