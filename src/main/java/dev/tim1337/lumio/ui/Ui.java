package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.render.Arcs;
import dev.tim1337.lumio.render.GradientBlit;
import dev.tim1337.lumio.render.LightWash;
import dev.tim1337.lumio.render.RoundedShapes;

import org.joml.Vector2f;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drawing context for controls: the graphics, the current theme, and helpers for the shapes the theme is built from.
 * <p>
 * The game can only fill rectangles, so rounded corners are built from rows. The edge pixel of every row is partially transparent according to
 * how much of it the circle covers, which anti-aliases the corners.
 */
public final class Ui implements Canvas {
	/** Text caches per font size (33 quarter steps) and font (mod or game), see {@link #slot}. */
	private static final int TEXT_CACHES = 33 * 2;
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final Map<String, Component>[] TEXTS = new Map[TEXT_CACHES];
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final Map<String, Integer>[] WIDTHS = new Map[TEXT_CACHES];
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final Map<String, Component>[] LYRIC_TEXTS = new Map[TEXT_CACHES];
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final Map<String, Integer>[] LYRIC_WIDTHS = new Map[TEXT_CACHES];
	/** Widths of texts in the font of a size, in its glyph units, for scaled text. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final Map<String, Integer>[] GLYPH_WIDTHS = new Map[TEXT_CACHES];
	/** Section labels split into letters with the offset of each, for letter spacing; keyed by text, per size and font. */
	private static final Map<String, Letters> LETTERS = new HashMap<>();
	private static final int MAX_LETTER_LABELS = 256;
	private static final Map<String, Identifier> ICONS = new HashMap<>();
	private static final Map<Long, Identifier> CORNERS = capped(160);

	/** Whether the mod's text uses the game font instead of the mod font; set in the menu settings. */
	private static boolean gameFont;

	/** Applies the font setting; texts are re-laid out in the other font when it changes. */
	private static void syncFont() {
		boolean wanted = fontOverride != null ? fontOverride : ModFeatures.MENU != null && ModFeatures.MENU.gameFont();
		// there are caches for both fonts (see slot), so switching the font for part of the screen is free
		gameFont = wanted;
	}

	/** Font override for part of the screen (the status bars): true for the game font, false for the mod font, null to follow the menu setting. */
	private static Boolean fontOverride;

	public static void setFontOverride(Boolean game) {
		fontOverride = game;
		syncFont();
	}

	/**
	 * @return the index of the text cache for a size in the current font: the same text in the other font is in another cache, so a lookup needs
	 *         no key built per call (this runs for every text drawn, every frame)
	 */
	private static int slot(int quarters) {
		return quarters * 2 + (gameFont ? 1 : 0);
	}

	/** @return the glyph scale for text at the given scale: the mod fonts exist per size, the game font has a single size */
	private static float fontUnit(float scale, int quarters) {
		return gameFont ? gameFontUnit(scale) : scale * 4f / quarters;
	}

	/** @return the game font's scale nearest to a size: it is a bitmap, and only a whole number of screen pixels per font pixel is sharp */
	public static float gameFontUnit(float scale) {
		float pixels = effectiveScale();
		return Math.max(1, Math.round(scale * pixels)) / pixels;
	}

	/**
	 * The scale menu text is drawn at: the font built for the nearest size, one font pixel per screen pixel. Stretching it by the few percent
	 * between the requested size and the font size made the unsmoothed glyphs lose or double a pixel column here and there ("dces" for "does").
	 * Lyrics keep the exact size because they grow and shrink smoothly.
	 */
	private static float uiUnit(float scale, int quarters) {
		if (gameFont) {
			return gameFontUnit(scale);
		}
		float wanted = effectiveScale() * scale * 4f;
		// sizes below the smallest or above the largest font are drawn scaled, since no font exists for them
		return Math.abs(wanted - quarters) <= 0.5f ? 1f / effectiveScale() : scale * 4f / quarters;
	}

	public final GuiGraphicsExtractor graphics;
	public final Font font;
	public final Palette palette;
	/** Color of enabled or selected elements, as ARGB. */
	public final int accent;
	/** Duration of the last frame in seconds, for animations. */
	public final float deltaSeconds;
	/** Whether animations are enabled; otherwise values jump to their targets. */
	public final boolean animated;

	public Ui(GuiGraphicsExtractor graphics, Font font, Palette palette, int accent, float deltaSeconds, boolean animated) {
		syncFont();
		this.graphics = graphics;
		this.font = font;
		this.palette = palette;
		this.accent = accent;
		this.deltaSeconds = deltaSeconds;
		this.animated = animated;
	}

	@Override
	public GuiGraphicsExtractor graphics() {
		return graphics;
	}

	@Override
	public int accent() {
		return accent;
	}

	@Override
	public boolean animated() {
		return animated;
	}

	@Override
	public float deltaSeconds() {
		return deltaSeconds;
	}

	@Override
	public void arc(float centerX, float centerY, float radius, float thickness, float from, float to, int color) {
		Arcs.add(graphics, centerX, centerY, radius, thickness, from, to, color);
	}

	@Override
	public void arcSegment(float centerX, float centerY, float radius, float thickness, float from, float to, int color) {
		Arcs.addSegment(graphics, centerX, centerY, radius, thickness, from, to, color);
	}

	@Override
	public boolean arcsAvailable() {
		return Arcs.available();
	}

	// ----------------------------------------------------------------------------------------------------- colors

	/** Shadow under a knob (switch, slider), drawn one pixel below the knob. */
	public static final int KNOB_SHADOW = 0x26000000;
	/** Light outline around round elements on glass (switch track, color ring), keeping their edge visible over a bright world. */
	public static final int GLASS_RIM = 0x59FFFFFF;

	/** @return the color with its alpha multiplied by the factor */
	public static int withOpacity(int argb, float factor) {
		return Draw.withOpacity(argb, factor);
	}

	/** @return the result of alpha-compositing {@code top} over {@code under} */
	public static int over(int top, int under) {
		return Draw.over(top, under);
	}

	/**
	 * @return the number without trailing fractional zeros, and without the decimal point if nothing is left ("1.50" -> "1.5", "2.00" -> "2");
	 *         numbers without a point are returned unchanged. Implemented as a loop because it runs every frame and a regex was recompiled each time.
	 */
	public static String trimZeros(String number) {
		return Draw.trimZeros(number);
	}

	public static int mix(int from, int to, float amount) {
		return Draw.mix(from, to, amount);
	}

	/** @return how light a color looks, from 0 (black) to 1 (white), weighting its channels as the eye does */
	public static float luminance(int argb) {
		return Draw.luminance(argb);
	}

	/** How fast hover and highlight fades follow the mouse, per second: every hover in the menu fades in about a ninth of a second. */
	public static final float HOVER_RATE = 9f;
	/** How fast something follows a place it moves to. */
	public static final float MOVE_RATE = 14f;

	public float approach(float value, float target, float speed) {
		if (!animated) {
			return target;
		}
		float step = deltaSeconds * speed;
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	// ----------------------------------------------------------------------------------------------------- shapes

	public void fill(int x, int y, int width, int height, int color) {
		if ((width == 1 || height == 1) && width > 0 && height > 0) {
			hairline(x, y, width, height, color);
			return;
		}
		graphics.fill(x, y, x + width, y + height, a(color));
	}

	/** The ends of a hairline in screen coordinates, reused: lines are only drawn on the render thread. */
	private static final Vector2f HAIRLINE_A = new Vector2f();
	private static final Vector2f HAIRLINE_B = new Vector2f();

	/**
	 * Draws a one-unit line snapped to screen pixels. In a scaled panel a unit is a fractional number of pixels, so such lines came out one pixel
	 * thick in some places and two in others; here every line is a whole number of pixels thick.
	 */
	private void hairline(int x, int y, int width, int height, int color) {
		float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
		var pose = graphics.pose();
		Vector2f a = pose.transformPosition(x, y, HAIRLINE_A);
		Vector2f b = pose.transformPosition(x + width, y + height, HAIRLINE_B);
		// in screen pixels
		float x0 = Math.min(a.x, b.x) * gui;
		float y0 = Math.min(a.y, b.y) * gui;
		float x1 = Math.max(a.x, b.x) * gui;
		float y1 = Math.max(a.y, b.y) * gui;
		int thick = Math.max(1, Math.round(effectiveScale()));
		int px0 = Math.round(x0);
		int py0 = Math.round(y0);
		int px1 = width == 1 ? px0 + thick : Math.round(x1);
		int py1 = height == 1 ? py0 + thick : Math.round(y1);
		pose.pushMatrix();
		pose.identity();
		pose.scale(1f / gui, 1f / gui);
		graphics.fill(px0, py0, px1, py1, a(color));
		pose.popMatrix();
	}

	public void verticalGradient(int x, int y, int width, int height, int top, int bottom) {
		graphics.fillGradient(x, y, x + width, y + height, a(top), a(bottom));
	}

	/**
	 * Fills the top band of a rounded container (a header row) with a horizontal gradient, edge to edge: one shape with the container's own bounds,
	 * radius and pixel snapping, so its top corners are the container's very shape and nothing shows between it and the border; its bottom
	 * corners are square. Without a scissor, which inside the menu window sat a few pixels off and left a strip at the left. Draw the border after
	 * it.
	 */
	public void roundedTopBand(int x, int y, int width, int height, float radius, int leftColor, int rightColor) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		int r = Math.min(Math.round(radius * scale), Math.min(w, h) / 2);
		int px = x * scale;
		int py = y * scale;
		int left = a(leftColor);
		int right = a(rightColor);
		if (RoundedShapes.available()) {
			inScreenPixels(() -> RoundedShapes.addGradient(graphics, px, py, w, h, r, 0, left, right, RoundedShapes.SQUARE_BOTTOM));
			return;
		}
		// without the shape shader: square corners under the border, the gradient across
		horizontalGradient(x, y, width, height, leftColor, rightColor);
	}

	/**
	 * Draws a dashed outline that follows a rounded rectangle, as thick as {@link #ring}, with soft edges like the other rounded shapes: a whole
	 * number of dashes per side, one centered on the middle of every side, so all sides look alike. For empty states and places to add
	 * something. Without the shader it is a plain outline.
	 */
	public void dashedRounded(int x, int y, int width, int height, float radius, int rawColor) {
		if (!RoundedShapes.available()) {
			ring(x, y, width, height, radius, 1, rawColor);
			return;
		}
		int color = a(rawColor);
		if (width <= 2 || height <= 2 || (color >>> 24) == 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		int r = Math.min(Math.round(radius * scale), Math.min(w, h) / 2);
		int t = Math.max(1, Math.round(scale * 0.75f));
		int px = x * scale;
		int py = y * scale;
		inScreenPixels(() -> RoundedShapes.add(graphics, px, py, w, h, r, t, color, RoundedShapes.DASHED));
	}

	/** Corners of a picture on screen, reused for every crisp picture, so drawing one allocates nothing. */
	private static final Vector2f PICTURE_START = new Vector2f();
	private static final Vector2f PICTURE_END = new Vector2f();

	/** @return how many real screen pixels a length in the current drawing coordinates covers, with every scale applied */
	public float realSize(float length) {
		var pose = graphics.pose();
		pose.transformPosition(0f, 0f, PICTURE_START);
		pose.transformPosition(length, 0f, PICTURE_END);
		return (PICTURE_END.x - PICTURE_START.x) * (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	/**
	 * Draws a square texture on whole real screen pixels: its position and size are snapped to the screen's pixel grid after every scale, so
	 * a sharp picture is not blurred by falling between pixels. For pictures such as album covers. It fades with the window, like everything else.
	 */
	public void imageCrisp(Identifier id, float x, float y, float size, int textureSide, int color) {
		var pose = graphics.pose();
		pose.transformPosition(x, y, PICTURE_START);
		pose.transformPosition(x + size, y + size, PICTURE_END);
		float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
		int left = Math.round(PICTURE_START.x * gui);
		int top = Math.round(PICTURE_START.y * gui);
		int side = Math.max(1, Math.round((PICTURE_END.x - PICTURE_START.x) * gui));
		pose.pushMatrix();
		pose.identity();
		pose.scale(1f / gui, 1f / gui);
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, left, top, 0f, 0f, side, side, textureSide, textureSide, textureSide, textureSide, a(color));
		pose.popMatrix();
	}

	/** Width of the strips a horizontal gradient is drawn in; the game only draws vertical gradients. */
	private static final int GRADIENT_STRIP = 3;

	/** A horizontal gradient, drawn as narrow strips of blended color. */
	public void horizontalGradient(int x, int y, int width, int height, int left, int right) {
		for (int at = 0; at < width; at += GRADIENT_STRIP) {
			int strip = Math.min(GRADIENT_STRIP, width - at);
			fill(x + at, y, strip, height, mix(left, right, (at + strip / 2f) / width));
		}
	}

	/** A vertical gradient with rounded corners: rounded caps in the end colors and the gradient between them. For opaque colors only. */
	public void roundedGradient(int x, int y, int width, int height, float radius, int top, int bottom) {
		int r = Math.min(Math.round(radius), height / 2);
		if (r <= 0) {
			verticalGradient(x, y, width, height, top, bottom);
			return;
		}
		rounded(x, y, width, 2 * r, r, top);
		rounded(x, y + height - 2 * r, width, 2 * r, r, bottom);
		verticalGradient(x, y + r, width, height - 2 * r, top, bottom);
	}

	// Shapes are built from a few fills plus corners cut from a small disc texture. The texture is generated for screen pixels, not menu pixels,
	// so edges stay sharp at every menu scale.

	public static void setAlpha(float value) {
		Draw.setAlpha(value);
	}

	public static float alpha() {
		return Draw.alpha();
	}

	private static int a(int color) {
		return Draw.faded(color);
	}

	public static void setZoom(float zoom) {
		Draw.setZoom(zoom);
	}

	public static float zoom() {
		return Draw.zoom();
	}

	/** @return the color with the current window opacity applied */
	public static int faded(int color) {
		return a(color);
	}

	/** An outline's thickness in screen pixels per GUI pixel of it, per screen pixel of GUI scale: a bit finer than a full GUI pixel. */
	private static final float RING_WEIGHT = 0.75f;

	private static int scale() {
		return Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * Draw.zoom()));
	}

	/**
	 * @return a white alpha-only texture of a disc (or a ring of the given thickness) whose quarters are corners of the given extent (in screen
	 *         pixels) and continuity step, see {@link CornerShape}
	 */
	private static Identifier corner(int radius, int step, int thickness) {
		long key = (long) step << 40 | (long) radius << 16 | thickness;
		Identifier id = CORNERS.get(key);
		if (id != null) {
			return id;
		}
		int size = radius * 2;
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				float distance = CornerShape.box(px + 0.5f - radius, py + 0.5f - radius, radius, radius, radius, step);
				float coverage = Mth.clamp(0.5f - distance, 0f, 1f);
				if (thickness > 0) {
					coverage *= 1f - Mth.clamp(-distance - thickness + 0.5f, 0f, 1f);
				}
				image.setPixelABGR(px, py, (Math.round(coverage * 255f) << 24) | 0xFFFFFF);
			}
		}
		id = Lumio.id("corner_" + radius + "_" + step + "_" + thickness);
		Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture("Lumio corner", image));
		CORNERS.put(key, id);
		return id;
	}

	/**
	 * @return how far a corner of radius r reaches in a w by h shape (see {@link CornerShape}) and its continuity step, packed as extent << 8 |
	 *         step, so it costs no object
	 */
	private static int cornerOf(int r, int w, int h) {
		int half = Math.min(w, h) / 2;
		int step = CornerShape.step(r, half);
		return Math.min(CornerShape.extent(r, step), half) << 8 | step;
	}

	private void corners(Identifier id, int radius, int x, int y, int width, int height, int color, boolean left, boolean right) {
		int size = radius * 2;
		if (left) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, radius, radius, radius, radius, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y + height - radius, 0f, radius, radius, radius, radius, radius, size, size, color);
		}
		if (right) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, id, x + width - radius, y, radius, 0f, radius, radius, radius, radius, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, id, x + width - radius, y + height - radius, radius, radius, radius, radius, radius, radius, size, size, color);
		}
	}

	/** Switches the pose to screen pixels, like {@link #inScreenPixels}, without a lambda. @return whether it was switched, for {@link #leaveScreenPixels} */
	private boolean enterScreenPixels() {
		int scale = scale();
		if (scale == 1) {
			return false;
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / scale, 1f / scale);
		return true;
	}

	private void leaveScreenPixels(boolean scaled) {
		if (scaled) {
			graphics.pose().popMatrix();
		}
	}

	private void inScreenPixels(Runnable draw) {
		int scale = scale();
		if (scale == 1) {
			draw.run();
			return;
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / scale, 1f / scale);
		draw.run();
		pose.popMatrix();
	}

	public void rounded(int x, int y, int width, int height, float radius, int color) {
		shape(x, y, width, height, radius, color, true, true);
	}

	/** Like {@link #rounded}, but only the left corners are rounded, for shapes attached to a window edge. */
	public void roundedLeft(int x, int y, int width, int height, float radius, int color) {
		shape(x, y, width, height, radius, color, true, false);
	}

	/** Like {@link #rounded}, but at fractional positions and sizes, resolved to screen pixels, so it can animate smoothly. */
	public void roundedSmooth(float x, float y, float width, float height, float radius, int color) {
		if (width <= 0f || height <= 0f || (color >>> 24) == 0) {
			return;
		}
		int scale = scale();
		int px = Math.round(x * scale);
		int py = Math.round(y * scale);
		int w = Math.max(1, Math.round((x + width) * scale) - px);
		int h = Math.max(1, Math.round((y + height) * scale) - py);
		shapePixels(px, py, w, h, Math.min(Math.round(radius * scale), Math.min(w, h) / 2), color, true, true);
	}

	private void shape(int x, int y, int width, int height, float radius, int color, boolean left, boolean right) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		shapePixels(x * scale, y * scale, w, h, Math.min(Math.round(radius * scale), Math.min(w, h) / 2), color, left, right);
	}

	private void shapePixels(int px, int py, int w, int h, int r, int rawColor, boolean left, boolean right) {
		int color = a(rawColor);
		// the hottest drawing of the menus and panels: without a lambda, which would be made anew for every shape
		boolean scaled = enterScreenPixels();
		try {
			shapeInPixels(px, py, w, h, r, color, left, right);
		} finally {
			leaveScreenPixels(scaled);
		}
	}

	private void shapeInPixels(int px, int py, int w, int h, int r, int color, boolean left, boolean right) {
		if (r <= 0) {
			graphics.fill(px, py, px + w, py + h, color);
			return;
		}
		if (RoundedShapes.available()) {
			// a single shader quad instead of seven pieces
			RoundedShapes.add(graphics, px, py, w, h, r, 0, color,
				(left ? 0 : RoundedShapes.SQUARE_LEFT) | (right ? 0 : RoundedShapes.SQUARE_RIGHT));
			return;
		}
		int shape = cornerOf(r, w, h);
		int e = shape >> 8;
		graphics.fill(px + (left ? e : 0), py, px + w - (right ? e : 0), py + h, color);
		if (left) {
			graphics.fill(px, py + e, px + e, py + h - e, color);
		}
		if (right) {
			graphics.fill(px + w - e, py + e, px + w, py + h - e, color);
		}
		corners(corner(e, shape & 0xFF, 0), e, px, py, w, h, color, left, right);
	}

	/** Outline of a rounded shape, leaving its inside uncovered. */
	public void ring(int x, int y, int width, int height, float radius, int thickness, int rawColor) {
		final int color = a(rawColor);
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		int r = Math.min(Math.round(radius * scale), Math.min(w, h) / 2);
		int t = Math.max(1, Math.round(thickness * scale * RING_WEIGHT));
		ringPixels(x * scale, y * scale, w, h, r, t, color);
	}

	/** An outline in screen pixels, with the color already faded. */
	private void ringPixels(int px, int py, int w, int h, int r, int t, int color) {
		boolean scaled = enterScreenPixels();
		try {
			ringInPixels(px, py, w, h, r, t, color);
		} finally {
			leaveScreenPixels(scaled);
		}
	}

	private void ringInPixels(int px, int py, int w, int h, int r, int t, int color) {
		if (r <= 0) {
			graphics.fill(px, py, px + w, py + t, color);
			graphics.fill(px, py + h - t, px + w, py + h, color);
			graphics.fill(px, py + t, px + t, py + h - t, color);
			graphics.fill(px + w - t, py + t, px + w, py + h - t, color);
			return;
		}
		if (RoundedShapes.available()) {
			// a single shader quad instead of eight pieces
			RoundedShapes.add(graphics, px, py, w, h, r, t, color, 0);
			return;
		}
		int shape = cornerOf(r, w, h);
		int e = shape >> 8;
		corners(corner(e, shape & 0xFF, Math.min(t, e)), e, px, py, w, h, color, true, true);
		graphics.fill(px + e, py, px + w - e, py + t, color);
		graphics.fill(px + e, py + h - t, px + w - e, py + h, color);
		graphics.fill(px, py + e, px + t, py + h - e, color);
		graphics.fill(px + w - t, py + e, px + w, py + h - e, color);
	}
	public void circle(int centerX, int centerY, int radius, int color) {
		rounded(centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color);
	}

	/**
	 * A filled circle around a center given in floats, smooth at its edge. Its center is snapped to a screen pixel corner the same way as
	 * {@link #ringAt}'s, and its size in screen pixels is even, so a dot and a ring drawn around the same point are exactly concentric at every GUI
	 * scale (a radio mark's dot in its ring).
	 */
	public void discAt(float centerX, float centerY, float diameter, int color) {
		if ((color >>> 24) == 0 || diameter <= 0f) {
			return;
		}
		int scale = scale();
		int d = evenPixels(diameter * scale);
		int cx = Math.round(centerX * scale);
		int cy = Math.round(centerY * scale);
		shapePixels(cx - d / 2, cy - d / 2, d, d, d / 2, color, true, true);
	}

	/** A circle's outline around a center given in floats, equally thick all around, concentric with a {@link #discAt} at the same point. */
	public void ringAt(float centerX, float centerY, float diameter, float thickness, int color) {
		if ((color >>> 24) == 0 || diameter <= 0f) {
			return;
		}
		int scale = scale();
		int d = evenPixels(diameter * scale);
		int cx = Math.round(centerX * scale);
		int cy = Math.round(centerY * scale);
		int t = Math.max(1, Math.round(thickness * scale * RING_WEIGHT));
		ringPixels(cx - d / 2, cy - d / 2, d, d, d / 2, t, a(color));
	}

	/** @return a length in screen pixels rounded to an even number, at least 2, so a shape centered on a pixel corner stays symmetric */
	private static int evenPixels(float pixels) {
		return Math.max(2, 2 * Math.round(pixels / 2f));
	}

	/** White soft radial light texture, generated once and scaled to every size, so growing or pulsing lights create no new textures. */
	private static Identifier softLight;
	private static final int SOFT_LIGHT_SIZE = 128;

	private static Identifier softLightTexture() {
		if (softLight == null) {
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, SOFT_LIGHT_SIZE, SOFT_LIGHT_SIZE, false);
			float half = SOFT_LIGHT_SIZE / 2f;
			for (int py = 0; py < SOFT_LIGHT_SIZE; py++) {
				for (int px = 0; px < SOFT_LIGHT_SIZE; px++) {
					float dx = (px + 0.5f - half) / half;
					float dy = (py + 0.5f - half) / half;
					float d = Math.min(1f, Mth.sqrt(dx * dx + dy * dy));
					// smooth falloff to zero at the edge, without rings or steps
					float fall = 1f - d;
					float value = fall * fall * (3f - 2f * fall);
					image.setPixelABGR(px, py, (Math.round(value * value * 255f) << 24) | 0xFFFFFF);
				}
			}
			softLight = Lumio.id("soft_light");
			Minecraft.getInstance().getTextureManager().register(softLight, new SmoothTexture("Lumio soft light", image));
		}
		return softLight;
	}

	/** Same as below, stretched to an ellipse, e.g. a light behind a word. */
	public void softLight(float centerX, float centerY, float radiusX, float radiusY, int rawColor) {
		if (radiusX <= 0.5f || radiusY <= 0.5f) {
			return;
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.scale(1f, radiusY / radiusX);
		softLight(0f, 0f, radiusX, rawColor);
		pose.popMatrix();
	}

	/**
	 * A soft radial light around a point that fades out at its radius. It is a single scaled texture, so size and position can be fractional and a
	 * pulsing or moving light stays smooth without generating anything new.
	 */
	public void softLight(float centerX, float centerY, float radius, int rawColor) {
		int color = a(rawColor);
		if (radius <= 0.5f || (color >>> 24) == 0 || HudStyle.surfaceGlowNow() <= 0f) {
			// a panel with its glow off has none anywhere: not around a ring in its Ready state either
			return;
		}
		Identifier id = softLightTexture();
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.scale(radius / (SOFT_LIGHT_SIZE / 2f), radius / (SOFT_LIGHT_SIZE / 2f));
		int half = SOFT_LIGHT_SIZE / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, -half, -half, 0f, 0f, SOFT_LIGHT_SIZE, SOFT_LIGHT_SIZE, SOFT_LIGHT_SIZE, SOFT_LIGHT_SIZE,
			SOFT_LIGHT_SIZE, SOFT_LIGHT_SIZE, color);
		pose.popMatrix();
	}

	/** A large, very soft wash of light, for the colored background of the menu; generated once. */
	private static Identifier wash;
	private static final int WASH_SIZE = 256;
	/** Amplitude of the noise baked into the wash, in alpha levels: once the wash is drawn faintly, it dithers away the steps of an 8-bit screen. */
	private static final int WASH_DITHER = 3;

	/** @return how strong the wash is at a distance from its center, as a share of its radius: smooth, and exactly zero at the edge */
	public static float washFalloff(float distance) {
		if (distance >= 1f) {
			return 0f;
		}
		float rest = 1f - distance * distance;
		return rest * rest * rest;
	}

	private static Identifier washTexture() {
		if (wash == null) {
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, WASH_SIZE, WASH_SIZE, false);
			float half = WASH_SIZE / 2f;
			for (int py = 0; py < WASH_SIZE; py++) {
				for (int px = 0; px < WASH_SIZE; px++) {
					float dx = (px + 0.5f - half) / half;
					float dy = (py + 0.5f - half) / half;
					float value = washFalloff(Mth.sqrt(dx * dx + dy * dy));
					int alpha = 0;
					if (value > 0f) {
						// a fixed per-pixel noise from a hash of the position, so the texture is the same every time
						int hash = (px * 73856093) ^ (py * 19349663);
						hash ^= hash >>> 13;
						hash *= 0x5bd1e995;
						int noise = ((hash >>> 8) & 0xFF) * (2 * WASH_DITHER + 1) / 256 - WASH_DITHER;
						alpha = Mth.clamp(Math.round(value * 255f) + noise, 1, 255);
					}
					image.setPixelABGR(px, py, (alpha << 24) | 0xFFFFFF);
				}
			}
			wash = Lumio.id("wash");
			Minecraft.getInstance().getTextureManager().register(wash, new SmoothTexture("Lumio wash", image));
		}
		return wash;
	}

	/** Draws a large soft wash of light, an ellipse around a point that fades to nothing at its radii, with {@link #washFalloff}. */
	public void wash(float centerX, float centerY, float radiusX, float radiusY, int rawColor) {
		int color = a(rawColor);
		if (radiusX <= 0.5f || radiusY <= 0.5f || (color >>> 24) == 0) {
			return;
		}
		if (LightWash.available()) {
			LightWash.add(graphics, centerX, centerY, radiusX, radiusY, color);
			return;
		}
		Identifier id = washTexture();
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.scale(radiusX / (WASH_SIZE / 2f), radiusY / (WASH_SIZE / 2f));
		int half = WASH_SIZE / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, -half, -half, 0f, 0f, WASH_SIZE, WASH_SIZE, WASH_SIZE, WASH_SIZE, WASH_SIZE, WASH_SIZE, color);
		pose.popMatrix();
	}

	/** Draws the same wash cut to rows from {@link dev.tim1337.lumio.render.RoundedRows#edges}. */
	public void washInRows(float centerX, float centerY, float radiusX, float radiusY, int rawColor, float[] edges) {
		LightWash.addRows(graphics, centerX, centerY, radiusX, radiusY, a(rawColor), edges, LightWash.available() ? null : washTexture());
	}

	private static final Map<Long, Identifier> SHADOWS = capped(60);

	/** @return an LRU texture cache that evicts the least recently used texture and releases it to the game when full */
	private static <K> Map<K, Identifier> capped(int limit) {
		return new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, Identifier> eldest) {
				if (size() > limit) {
					Minecraft.getInstance().getTextureManager().release(eldest.getValue());
					return true;
				}
				return false;
			}
		};
	}

	/** Clears all text measurement caches, for when fonts may have changed. */
	public static void clearCaches() {
		for (int i = 0; i < TEXTS.length; i++) {
			TEXTS[i] = null;
			WIDTHS[i] = null;
			LYRIC_TEXTS[i] = null;
			LYRIC_WIDTHS[i] = null;
			GLYPH_WIDTHS[i] = null;
		}
		LETTERS.clear();
		SEMIBOLD.clear();
		BOLD.clear();
		LYRICS.clear();
		Arrays.fill(LYRIC_FLOAT_WIDTHS, null);
		LYRIC_SEQUENCES.clear();
		FITTED.clear();
		WRAPPED.clear();
	}

	/**
	 * Shadow texture of a rounded shape: a blur of the shape with the shape itself cut out, so it only surrounds it, evenly on all sides. Generated
	 * once per size from a corner texture and drawn in pieces: the corners, plus stretched edges.
	 */
	private static Identifier shadowTexture(int corner, int spread, int radius, int step) {
		long key = ((long) corner << 40) | ((long) spread << 24) | ((long) radius << 4) | step;
		Identifier id = SHADOWS.get(key);
		if (id != null) {
			return id;
		}
		int size = corner * 2;
		float[] core = new float[size * size];
		float half = size / 2f;
		float inner = half - spread; // half width of the shape casting the shadow
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				core[y * size + x] = Mth.clamp(0.5f - CornerShape.box(x + 0.5f - half, y + 0.5f - half, inner, inner, radius, step), 0f, 1f);
				if (Math.abs(x + 0.5f - half) > inner || Math.abs(y + 0.5f - half) > inner) {
					core[y * size + x] = 0f;
				}
			}
		}
		// gaussian blur approximated by repeated box blurs
		float[] blurred = core.clone();
		float[] scratch = new float[core.length];
		int boxRadius = Math.max(1, Math.round(spread / 3.2f));
		for (int pass = 0; pass < 3; pass++) {
			for (int horizontal = 0; horizontal < 2; horizontal++) {
				float[] from = horizontal == 0 ? blurred : scratch;
				float[] to = horizontal == 0 ? scratch : blurred;
				for (int line = 0; line < size; line++) {
					for (int i = 0; i < size; i++) {
						float sum = 0f;
						for (int d = -boxRadius; d <= boxRadius; d++) {
							int k = Mth.clamp(i + d, 0, size - 1);
							sum += horizontal == 0 ? from[line * size + k] : from[k * size + line];
						}
						if (horizontal == 0) {
							to[line * size + i] = sum / (boxRadius * 2 + 1);
						} else {
							to[i * size + line] = sum / (boxRadius * 2 + 1);
						}
					}
				}
			}
		}
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		for (int i = 0; i < blurred.length; i++) {
			// nothing under the shape itself, so glass on top of it is not darkened
			float alpha = blurred[i] * (1f - core[i]);
			image.setPixelABGR(i % size, i / size, (dithered(alpha, i % size, i / size) << 24));
		}
		id = Lumio.id("shadow_" + Long.toHexString(key));
		Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture("Lumio shadow", image));
		SHADOWS.put(key, id);
		return id;
	}

	/** A soft shadow around a rounded shape, even on all sides. */
	public void shadow(int x, int y, int width, int height, float radius, int spread, float opacity) {
		if (opacity <= 0f || width <= 0 || height <= 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		int shape = cornerOf(Math.min(Math.round(radius * scale), Math.min(w, h) / 2), w, h);
		int r = shape >> 8;
		int sp = Math.max(2, spread * scale);
		int corner = sp + r;
		Identifier id = shadowTexture(corner, sp, r, shape & 0xFF);
		int px = x * scale;
		int py = y * scale;
		int tint = Math.round(Mth.clamp(opacity * 1.5f * Draw.alpha(), 0f, 1f) * 255f) << 24;
		int size = corner * 2;
		inScreenPixels(() -> {
			var g = graphics;
			g.blit(RenderPipelines.GUI_TEXTURED, id, px - sp, py - sp, 0f, 0f, corner, corner, corner, corner, size, size, tint);
			g.blit(RenderPipelines.GUI_TEXTURED, id, px + w - r, py - sp, corner, 0f, corner, corner, corner, corner, size, size, tint);
			g.blit(RenderPipelines.GUI_TEXTURED, id, px - sp, py + h - r, 0f, corner, corner, corner, corner, corner, size, size, tint);
			g.blit(RenderPipelines.GUI_TEXTURED, id, px + w - r, py + h - r, corner, corner, corner, corner, corner, corner, size, size, tint);
			int across = w - 2 * r;
			int down = h - 2 * r;
			if (across > 0) {
				g.blit(RenderPipelines.GUI_TEXTURED, id, px + r, py - sp, corner - 1, 0f, across, corner, 1, corner, size, size, tint);
				g.blit(RenderPipelines.GUI_TEXTURED, id, px + r, py + h - r, corner - 1, corner, across, corner, 1, corner, size, size, tint);
			}
			if (down > 0) {
				g.blit(RenderPipelines.GUI_TEXTURED, id, px - sp, py + r, 0f, corner - 1, corner, down, corner, 1, size, size, tint);
				g.blit(RenderPipelines.GUI_TEXTURED, id, px + w - r, py + r, corner, corner - 1, corner, down, corner, 1, size, size, tint);
			}
		});
	}
	// ------------------------------------------------------------------------------------------------------ icons

	/** How much lighter the top and darker the bottom of an icon tile is than its color. */
	private static final float TILE_LIGHT = 0.2f;
	private static final float TILE_SHADE = 0.1f;

	// a small chip: semibold caption text on a subtle fill, as the version next to the home page's title
	private static final int CHIP_HEIGHT = 14;
	private static final int CHIP_PAD = 6;

	/** Draws a small chip centered on a line, from its left edge; @return its width */
	public int chip(String text, int x, int middleY) {
		float textWidth = boldWidthScaled(text, TextScale.SMALL);
		int width = Math.round(textWidth) + 2 * CHIP_PAD;
		rounded(x, middleY - CHIP_HEIGHT / 2, width, CHIP_HEIGHT, Radius.XS, palette.hover());
		textAtScaledBold(text, x + CHIP_PAD, middleY - 3.5f, TextScale.SMALL, mix(palette.textSecondary(), palette.text(), 0.35f));
		return width;
	}

	/** @return the width {@link #chip} draws */
	public int chipWidth(String text) {
		return Math.round(boldWidthScaled(text, TextScale.SMALL)) + 2 * CHIP_PAD;
	}

	/**
	 * An icon centered on a rounded tile in its color, lit slightly from the top. A translucent tile (fading in or out) is drawn flat, since the
	 * gradient's pieces overlap.
	 */
	public void iconTile(String name, int x, int y, int size, int iconSize, float radius, int color, int iconColor) {
		if ((color >>> 24) == 0xFF) {
			roundedGradient(x, y, size, size, radius, mix(color, 0xFFFFFFFF, TILE_LIGHT), mix(color, 0xFF000000, TILE_SHADE));
		} else {
			rounded(x, y, size, size, radius, color);
		}
		icon(name, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, iconColor);
	}

	/** Draws one of the mod's icons (white shapes, tinted with the color) at the given size in menu pixels. */
	public void icon(String name, int x, int y, int size, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS.computeIfAbsent(name, Ui::iconTexture), x, y, 0f, 0f, size, size, 128, 128, 128, 128, a(color));
	}

	/**
	 * Looks up an icon texture once. Icons without an image fall back to the sliders icon; otherwise the game would retry creating a texture for
	 * the missing file every frame.
	 */
	private static Identifier iconTexture(String name) {
		Identifier id = Lumio.id("textures/gui/icon_" + name + ".png");
		if (Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) {
			Logs.warn("There is no icon called {}", name);
			return Lumio.id("textures/gui/icon_sliders.png");
		}
		return id;
	}

	// ------------------------------------------------------------------------------------------------------ edge lighting
	// Glass and glow consist of light whose color or strength varies along the edge of a shape. Edge positions are given as one number from 0 to
	// 1, starting at the beginning of the top edge and running clockwise. One piece of light is drawn per small edge segment, each in the
	// color wanted there.

	/** Light color (ARGB) at a position along an edge, from 0 to 1. */
	@FunctionalInterface
	public interface EdgeColor {
		int at(float place);
	}

	private static final Map<Long, Identifier> HALOS = capped(48);
	private static final Map<Long, Identifier> GRIDS = capped(24);

	/**
	 * Draws a grid of rounded slots as a single texture generated once per size. Menus with many slots (the storage) drew every slot as its own
	 * shape, and the game compared each one against all others every frame. The texture is generated at screen resolution, so it stays sharp.
	 *
	 * @param step distance from one slot to the next, in menu pixels
	 * @param cell slot size
	 */
	public void slotGrid(int x, int y, int columns, int rows, int step, int cell, float radius, int color) {
		if (columns <= 0 || rows <= 0 || (color >>> 24) == 0) {
			return;
		}
		int s = scale();
		int w = (columns - 1) * step + cell;
		int h = (rows - 1) * step + cell;
		long key = ((long) columns << 48) | ((long) rows << 40) | ((long) step << 32) | ((long) cell << 24) | ((long) Math.round(radius * 4) << 12) | s;
		Identifier id = GRIDS.get(key);
		if (id == null) {
			int tw = w * s;
			int th = h * s;
			float r = radius * s;
			float c = cell * s;
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, tw, th, true);
			for (int py = 0; py < th; py++) {
				float cy = (py + 0.5f) % (step * s);
				for (int px = 0; px < tw; px++) {
					float cx = (px + 0.5f) % (step * s);
					float alpha = 0f;
					if (cx < c && cy < c) {
						// distance to the edge of the rounded slot, for a one-pixel soft edge
						float qx = Math.abs(cx - c / 2f) - (c / 2f - r);
						float qy = Math.abs(cy - c / 2f) - (c / 2f - r);
						float outside = Mth.sqrt(Math.max(qx, 0f) * Math.max(qx, 0f) + Math.max(qy, 0f) * Math.max(qy, 0f)) + Math.min(Math.max(qx, qy), 0f) - r;
						alpha = Mth.clamp(0.5f - outside, 0f, 1f);
					}
					image.setPixelABGR(px, py, (Math.round(alpha * 255f) << 24) | 0xFFFFFF);
				}
			}
			id = Lumio.id("slot_grid_" + Long.toHexString(key));
			Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture("Lumio slots", image));
			GRIDS.put(key, id);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w * s, h * s, w * s, h * s, a(color));
	}
	private static final Map<Long, Identifier> INNER_LIGHTS = capped(48);

	/**
	 * @return the position (0 to 1) along the edge of a rounded shape of the edge point nearest to a point. The edge starts at the left end of the
	 *         straight top edge and runs clockwise; everything is in screen pixels.
	 */
	public static float edgePlace(float qx, float qy, float left, float top, float width, float height, float radius) {
		float x0 = left + radius;
		float x1 = left + width - radius;
		float y0 = top + radius;
		float y1 = top + height - radius;
		float cx = Mth.clamp(qx, x0, x1);
		float cy = Mth.clamp(qy, y0, y1);
		float dx = qx - cx;
		float dy = qy - cy;
		float across = Math.max(0f, x1 - x0);
		float down = Math.max(0f, y1 - y0);
		float arc = (float) (Math.PI * radius / 2.0);
		float total = 2f * (across + down) + 4f * arc;
		if (total <= 0f) {
			return 0f;
		}
		float s;
		if (dx == 0f || dy == 0f) {
			if (Math.abs(dy) >= Math.abs(dx)) {
				s = dy < 0f ? cx - x0 : across + arc + down + arc + (x1 - cx);
			} else {
				s = dx > 0f ? across + arc + (cy - y0) : across + arc + down + arc + across + arc + (y1 - cy);
			}
		} else {
			float angle = (float) Math.atan2(dy, dx);
			if (dx > 0f && dy < 0f) {
				s = across + (angle + (float) Math.PI / 2f) * radius;
			} else if (dx > 0f) {
				s = across + arc + down + angle * radius;
			} else if (dy > 0f) {
				s = across + arc + down + arc + across + (angle - (float) Math.PI / 2f) * radius;
			} else {
				s = across + arc + down + arc + across + arc + down + (angle + (float) Math.PI) * radius;
			}
		}
		return Mth.clamp(s / total, 0f, 0.99999f);
	}

	/** @return the edge positions of the midpoints of the four rounded corners: top left, top right, bottom right, bottom left; in the unit of the sizes */
	public static float[] cornerPlaces(float width, float height, float radius) {
		float across = Math.max(0f, width - 2f * radius);
		float down = Math.max(0f, height - 2f * radius);
		float arc = (float) (Math.PI * radius / 2.0);
		float total = 2f * (across + down) + 4f * arc;
		if (total <= 0f) {
			return new float[]{0f, 0.25f, 0.5f, 0.75f};
		}
		return new float[]{
			1f - arc / 2f / total,
			(across + arc / 2f) / total,
			(across + arc + down + arc / 2f) / total,
			(across + arc + down + arc + across + arc / 2f) / total
		};
	}

	/** @return the distance between two edge positions, from 0 to 0.5, treating the edge as a loop */
	public static float edgeDistance(float a, float b) {
		float d = Math.abs(a - b);
		return d > 0.5f ? 1f - d : d;
	}

	/**
	 * @return an alpha from 0 to 1 as a byte, with a faint fixed noise of about one step, so a soft light or shadow drawn from it shows no
	 *         bands; zero stays zero, so nothing appears where there is nothing
	 */
	private static int dithered(float alpha, int x, int y) {
		if (alpha <= 0f) {
			return 0;
		}
		int hash = (x * 73856093) ^ (y * 19349663);
		float noise = ((hash >>> 8) & 1023) / 1023f - 0.5f;
		return Mth.clamp(Math.round(Mth.clamp(alpha, 0f, 1f) * 255f + noise), 0, 255);
	}

	private static Identifier lightTexture(Map<Long, Identifier> cache, boolean outside, int corner, int spread, int radius, int step) {
		long key = ((long) corner << 40) | ((long) spread << 24) | ((long) radius << 4) | step;
		Identifier id = cache.get(key);
		if (id != null) {
			return id;
		}
		int size = corner * 2;
		float half = size / 2f;
		// half width of the shape the light surrounds; inner light is generated for a corner that spans the whole texture
		float inner = outside ? half - spread : half;
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				// signed distance from the shape edge, positive outside
				float distance = CornerShape.box(px + 0.5f - half, py + 0.5f - half, inner, inner, radius, step);
				float alpha;
				if (outside) {
					float t = Mth.clamp(distance / spread, 0f, 1f);
					float falloff = 1f - t * t * (3f - 2f * t);
					alpha = Mth.clamp(distance + 0.5f, 0f, 1f) * (float) Math.pow(falloff, 1.6);
				} else {
					// inside: strongest at the edge, gone at the spread depth
					float t = Mth.clamp(-distance / spread, 0f, 1f);
					float falloff = 1f - t * t * (3f - 2f * t);
					alpha = Mth.clamp(0.5f - distance, 0f, 1f) * falloff * falloff;
				}
				image.setPixelABGR(px, py, (dithered(alpha, px, py) << 24) | 0xFFFFFF);
			}
		}
		id = Lumio.id((outside ? "halo_" : "inner_") + Long.toHexString(key));
		Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture("Lumio light", image));
		cache.put(key, id);
		return id;
	}

	private void lightBlit(Identifier id, int x, int y, float u, float v, int width, int height, int uWidth, int vHeight, int size, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, width, height, uWidth, vHeight, size, size, a(color));
	}

	/** Corner of a ring: only the cells the ring passes through are drawn, so cells can be small and the color varies smoothly along the arc. */
	private void ringCorner(Identifier id, int destX, int destY, int u, int v, int pieceWidth, int pieceHeight, int size, int cells,
							int left, int top, int width, int height, int radius, EdgeColor color, int thickness) {
		float centreX = u == 0 ? destX + pieceWidth : destX;
		float centreY = v == 0 ? destY + pieceHeight : destY;
		for (int i = 0; i < cells; i++) {
			int x0 = pieceWidth * i / cells;
			int x1 = pieceWidth * (i + 1) / cells;
			for (int j = 0; j < cells; j++) {
				int y0 = pieceHeight * j / cells;
				int y1 = pieceHeight * (j + 1) / cells;
				float nearX = Math.max(0f, Math.max(destX + x0 - centreX, centreX - (destX + x1)));
				float nearY = Math.max(0f, Math.max(destY + y0 - centreY, centreY - (destY + y1)));
				float farX = Math.max(Math.abs(destX + x0 - centreX), Math.abs(destX + x1 - centreX));
				float farY = Math.max(Math.abs(destY + y0 - centreY), Math.abs(destY + y1 - centreY));
				if (Math.hypot(nearX, nearY) > radius * DIAGONAL_REACH + 1 || Math.hypot(farX, farY) < radius - thickness - 1) {
					continue;
				}
				float place = edgePlace(destX + (x0 + x1) / 2f, destY + (y0 + y1) / 2f, left, top, width, height, radius);
				lightBlit(id, destX + x0, destY + y0, u + x0, v + y0, x1 - x0, y1 - y0, x1 - x0, y1 - y0, size, color.at(place));
			}
		}
	}

	/**
	 * A glow around the outside of a rounded shape that fades away from it, colored per edge position by the given function. It does not cover the
	 * shape itself. Pieces along straight edges are stretched from a single texture column.
	 */
	public void halo(int x, int y, int width, int height, float radius, int spread, EdgeColor color) {
		if (width <= 0 || height <= 0 || spread <= 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		int shape = cornerOf(Math.min(Math.round(radius * scale), Math.min(w, h) / 2), w, h);
		int r = shape >> 8;
		int sp = Math.max(2, spread * scale);
		int corner = sp + r;
		Identifier id = lightTexture(HALOS, true, corner, sp, r, shape & 0xFF);
		int px = x * scale;
		int py = y * scale;
		float size = corner * 2;
		// every piece gets the edge colors at its corners, blended across it: no color steps between pieces and no seams
		int cells = Mth.clamp(corner / 16, 2, 4);
		float mid = (corner - 0.5f) / size;
		float midHigh = (corner + 0.5f) / size;
		inScreenPixels(() -> {
			haloCorner(id, px - sp, py - sp, 0f, 0f, corner, size, cells, px, py, w, h, r, color);
			haloCorner(id, px + w - r, py - sp, 0.5f, 0f, corner, size, cells, px, py, w, h, r, color);
			haloCorner(id, px - sp, py + h - r, 0f, 0.5f, corner, size, cells, px, py, w, h, r, color);
			haloCorner(id, px + w - r, py + h - r, 0.5f, 0.5f, corner, size, cells, px, py, w, h, r, color);
			int across = w - 2 * r;
			int down = h - 2 * r;
			int acrossPieces = Mth.clamp(across / 40, 1, 16);
			for (int i = 0; i < acrossPieces && across > 0; i++) {
				int from = px + r + across * i / acrossPieces;
				int to = px + r + across * (i + 1) / acrossPieces;
				int topFrom = a(color.at(edgePlace(from, py, px, py, w, h, r)));
				int topTo = a(color.at(edgePlace(to, py, px, py, w, h, r)));
				int bottomFrom = a(color.at(edgePlace(from, py + h, px, py, w, h, r)));
				int bottomTo = a(color.at(edgePlace(to, py + h, px, py, w, h, r)));
				GradientBlit.add(graphics, id, from, py - sp, to, py - sp + corner, mid, 0f, mid, 0.5f, topFrom, topTo, topTo, topFrom);
				GradientBlit.add(graphics, id, from, py + h - r, to, py + h - r + corner, mid, 0.5f, mid, 1f, bottomFrom, bottomTo, bottomTo, bottomFrom);
			}
			int downPieces = Mth.clamp(down / 40, 1, 8);
			for (int i = 0; i < downPieces && down > 0; i++) {
				int from = py + r + down * i / downPieces;
				int to = py + r + down * (i + 1) / downPieces;
				int leftFrom = a(color.at(edgePlace(px, from, px, py, w, h, r)));
				int leftTo = a(color.at(edgePlace(px, to, px, py, w, h, r)));
				int rightFrom = a(color.at(edgePlace(px + w, from, px, py, w, h, r)));
				int rightTo = a(color.at(edgePlace(px + w, to, px, py, w, h, r)));
				GradientBlit.add(graphics, id, px - sp, from, px - sp + corner, to, 0f, mid, 0.5f, midHigh, leftFrom, leftFrom, leftTo, leftTo);
				GradientBlit.add(graphics, id, px + w - r, from, px + w - r + corner, to, 0.5f, mid, 1f, midHigh, rightFrom, rightFrom, rightTo, rightTo);
			}
		});
	}

	/** How far out a continuous corner reaches on its diagonal, at most, as a share of its extent (the superellipse of exponent 4 does: 2 to the 1/4). */
	private static final float DIAGONAL_REACH = 1.19f;
	private static int[] cornerColors = new int[25];

	/** One quarter of the halo, in a few cells, with the edge color at every cell corner blended across each cell. */
	private void haloCorner(Identifier id, int destX, int destY, float u, float v, int corner, float size, int cells,
							int left, int top, int width, int height, int radius, EdgeColor color) {
		// cell corner colors in one reused array (the GUI is drawn on a single thread): this runs for every corner of every glowing panel,
		// every frame
		int[] colors = cornerColors.length >= (cells + 1) * (cells + 1) ? cornerColors : (cornerColors = new int[(cells + 1) * (cells + 1)]);
		for (int j = 0; j <= cells; j++) {
			for (int i = 0; i <= cells; i++) {
				float vx = destX + corner * (float) i / cells;
				float vy = destY + corner * (float) j / cells;
				colors[j * (cells + 1) + i] = a(color.at(edgePlace(vx, vy, left, top, width, height, radius)));
			}
		}
		float uv = corner / size;
		for (int j = 0; j < cells; j++) {
			for (int i = 0; i < cells; i++) {
				float x0 = destX + corner * (float) i / cells;
				float x1 = destX + corner * (float) (i + 1) / cells;
				float y0 = destY + corner * (float) j / cells;
				float y1 = destY + corner * (float) (j + 1) / cells;
				GradientBlit.add(graphics, id, x0, y0, x1, y1,
					u + uv * i / cells, v + uv * j / cells, u + uv * (i + 1) / cells, v + uv * (j + 1) / cells,
					colors[j * (cells + 1) + i], colors[j * (cells + 1) + i + 1], colors[(j + 1) * (cells + 1) + i + 1], colors[(j + 1) * (cells + 1) + i]);
			}
		}
	}

	/** Light just inside the edge of a rounded shape, fading towards the center, like the edge light of thick glass. */
	public void innerLight(int x, int y, int width, int height, float radius, int depth, EdgeColor color) {
		if (width <= 0 || height <= 0 || depth <= 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		int shape = cornerOf(Math.min(Math.round(radius * scale), Math.min(w, h) / 2), w, h);
		int r = shape >> 8;
		if (r < 2) {
			return;
		}
		int d = Math.min(depth * scale, r);
		Identifier id = lightTexture(INNER_LIGHTS, false, r, d, r, shape & 0xFF);
		int px = x * scale;
		int py = y * scale;
		float size = r * 2;
		int cells = Mth.clamp(r / 12, 2, 4);
		float mid = (r - 0.5f) / size;
		float midHigh = (r + 0.5f) / size;
		// like the halo: every piece gets the edge colors at its corners blended across it, so there are no color steps
		inScreenPixels(() -> {
			haloCorner(id, px, py, 0f, 0f, r, size, cells, px, py, w, h, r, color);
			haloCorner(id, px + w - r, py, 0.5f, 0f, r, size, cells, px, py, w, h, r, color);
			haloCorner(id, px, py + h - r, 0f, 0.5f, r, size, cells, px, py, w, h, r, color);
			haloCorner(id, px + w - r, py + h - r, 0.5f, 0.5f, r, size, cells, px, py, w, h, r, color);
			int across = w - 2 * r;
			int down = h - 2 * r;
			int acrossPieces = Mth.clamp(across / 40, 1, 16);
			for (int i = 0; i < acrossPieces && across > 0; i++) {
				int from = px + r + across * i / acrossPieces;
				int to = px + r + across * (i + 1) / acrossPieces;
				int topFrom = a(color.at(edgePlace(from, py, px, py, w, h, r)));
				int topTo = a(color.at(edgePlace(to, py, px, py, w, h, r)));
				int bottomFrom = a(color.at(edgePlace(from, py + h, px, py, w, h, r)));
				int bottomTo = a(color.at(edgePlace(to, py + h, px, py, w, h, r)));
				GradientBlit.add(graphics, id, from, py, to, py + r, mid, 0f, mid, 0.5f, topFrom, topTo, topTo, topFrom);
				GradientBlit.add(graphics, id, from, py + h - r, to, py + h, mid, 0.5f, mid, 1f, bottomFrom, bottomTo, bottomTo, bottomFrom);
			}
			int downPieces = Mth.clamp(down / 40, 1, 8);
			for (int i = 0; i < downPieces && down > 0; i++) {
				int from = py + r + down * i / downPieces;
				int to = py + r + down * (i + 1) / downPieces;
				int leftFrom = a(color.at(edgePlace(px, from, px, py, w, h, r)));
				int leftTo = a(color.at(edgePlace(px, to, px, py, w, h, r)));
				int rightFrom = a(color.at(edgePlace(px + w, from, px, py, w, h, r)));
				int rightTo = a(color.at(edgePlace(px + w, to, px, py, w, h, r)));
				GradientBlit.add(graphics, id, px, from, px + r, to, 0f, mid, 0.5f, midHigh, leftFrom, leftFrom, leftTo, leftTo);
				GradientBlit.add(graphics, id, px + w - r, from, px + w, to, 0.5f, mid, 1f, midHigh, rightFrom, rightFrom, rightTo, rightTo);
			}
		});
	}

	/** A thin line along the edge of a rounded shape, like {@link #ring}, with a color that varies along it. */
	public void rim(int x, int y, int width, int height, float radius, int thickness, EdgeColor color) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int scale = scale();
		int w = width * scale;
		int h = height * scale;
		int shape = cornerOf(Math.min(Math.round(radius * scale), Math.min(w, h) / 2), w, h);
		int r = shape >> 8;
		int t = Math.max(1, Math.round(thickness * scale * 0.75f));
		int px = x * scale;
		int py = y * scale;
		inScreenPixels(() -> {
			int across = w - 2 * r;
			int down = h - 2 * r;
			// straight parts blend between colors along their length, so long edges have no steps
			int acrossPieces = Mth.clamp(across / 40, 1, 16);
			for (int i = 0; i < acrossPieces && across > 0; i++) {
				int from = px + r + across * i / acrossPieces;
				int to = px + r + across * (i + 1) / acrossPieces;
				int topFrom = a(color.at(edgePlace(from, py, px, py, w, h, r)));
				int topTo = a(color.at(edgePlace(to, py, px, py, w, h, r)));
				int bottomFrom = a(color.at(edgePlace(from, py + h, px, py, w, h, r)));
				int bottomTo = a(color.at(edgePlace(to, py + h, px, py, w, h, r)));
				GradientBlit.fill(graphics, from, py, to, py + t, topFrom, topTo, topTo, topFrom);
				GradientBlit.fill(graphics, from, py + h - t, to, py + h, bottomFrom, bottomTo, bottomTo, bottomFrom);
			}
			int downPieces = Mth.clamp(down / 40, 1, 8);
			for (int i = 0; i < downPieces && down > 0; i++) {
				int from = py + r + down * i / downPieces;
				int to = py + r + down * (i + 1) / downPieces;
				int leftFrom = a(color.at(edgePlace(px, from, px, py, w, h, r)));
				int leftTo = a(color.at(edgePlace(px, to, px, py, w, h, r)));
				int rightFrom = a(color.at(edgePlace(px + w, from, px, py, w, h, r)));
				int rightTo = a(color.at(edgePlace(px + w, to, px, py, w, h, r)));
				GradientBlit.fill(graphics, px, from, px + t, to, leftFrom, leftFrom, leftTo, leftTo);
				GradientBlit.fill(graphics, px + w - t, from, px + w, to, rightFrom, rightFrom, rightTo, rightTo);
			}
			if (r <= 0) {
				return;
			}
			Identifier id = corner(r, shape & 0xFF, Math.min(t, r));
			int size = r * 2;
			int cells = Mth.clamp(r / 12, 1, 3);
			ringCorner(id, px, py, 0, 0, r, r, size, cells, px, py, w, h, r, color, Math.min(t, r));
			ringCorner(id, px + w - r, py, r, 0, r, r, size, cells, px, py, w, h, r, color, Math.min(t, r));
			ringCorner(id, px, py + h - r, 0, r, r, r, size, cells, px, py, w, h, r, color, Math.min(t, r));
			ringCorner(id, px + w - r, py + h - r, r, r, r, r, size, cells, px, py, w, h, r, color, Math.min(t, r));
		});
	}
	// ------------------------------------------------------------------------------------------------------ text

	// Text is drawn at screen resolution: there is a font per menu size whose glyphs are rasterized at their on-screen size and drawn in screen
	// pixels. That keeps text sharp, including at 4K.

	private static final int MAX_FONT = 8;
	/** Fonts exist for every quarter size step, so text of any size uses glyphs rasterized for it instead of stretched ones. */
	private static final int MAX_QUARTERS = MAX_FONT * 4;
	private static final FontDescription[] FONTS = new FontDescription[MAX_QUARTERS + 1];

	private static FontDescription fontFor(int quarters) {
		int k = Mth.clamp(quarters, 4, MAX_QUARTERS);
		if (gameFont) {
			return FontDescription.DEFAULT;
		}
		if (FONTS[k] == null) {
			FONTS[k] = new FontDescription.Resource(Lumio.id("uq" + k));
		}
		return FONTS[k];
	}

	/**
	 * A heavier face of the menu font (Inter SemiBold or Bold): its fonts per size, like {@link #fontFor} for the regular face, and the texts and
	 * widths made with them, per size and font.
	 */
	private static final class Face {
		private final String prefix;
		/** The largest size it has a font for, in quarter steps. */
		private final int maxQuarters;
		private final FontDescription[] fonts;
		private final Map<String, Component>[] texts;
		private final Map<String, Integer>[] widths;

		@SuppressWarnings({"unchecked", "rawtypes"})
		Face(String prefix, int maxQuarters) {
			this.prefix = prefix;
			this.maxQuarters = maxQuarters;
			fonts = new FontDescription[maxQuarters + 1];
			// one cache per size and font, see slot
			texts = new Map[(maxQuarters + 1) * 2];
			widths = new Map[(maxQuarters + 1) * 2];
		}

		FontDescription font(int quarters) {
			if (fonts[quarters] == null) {
				fonts[quarters] = new FontDescription.Resource(Lumio.id(prefix + quarters));
			}
			return fonts[quarters];
		}

		void clear() {
			Arrays.fill(texts, null);
			Arrays.fill(widths, null);
		}
	}

	/** Feature names, card titles, tab labels and section labels. */
	private static final Face SEMIBOLD = new Face("ub", MAX_QUARTERS);
	/**
	 * The largest size of the bold face, in quarter steps: larger than the other faces, as it draws the large headings (the main menu's "Ready to
	 * play?"), which drawn stretched from a smaller font had jagged edges.
	 */
	private static final int MAX_TITLE_QUARTERS = 96;
	private static final Face BOLD = new Face("uh", MAX_TITLE_QUARTERS);

	private static Component styled(String text, int quarters) {
		int k = Mth.clamp(quarters, 4, MAX_QUARTERS);
		Map<String, Component> cache = TEXTS[slot(k)];
		if (cache == null) {
			cache = TEXTS[slot(k)] = new HashMap<>();
		}
		Component found = cache.get(text);
		if (found == null) {
			if (cache.size() > 2000) {
				cache.clear();
			}
			found = Component.literal(text).withStyle(style -> style.withFont(fontFor(k)));
			cache.put(text, found);
		}
		return found;
	}

	/** @return the text width in menu pixels; cached per text because measuring is comparatively slow */
	public int width(String text) {
		int quarters = baseQuarters();
		Map<String, Integer> cache = WIDTHS[slot(quarters)];
		if (cache == null) {
			cache = WIDTHS[slot(quarters)] = new HashMap<>();
		}
		Integer found = cache.get(text);
		if (found == null) {
			if (cache.size() > 3000) {
				cache.clear();
			}
			found = (int) Math.ceil(units(font, null, text, quarters, quarters / 4f) * (gameFont ? gameFontUnit(1f) : 4.0 / quarters));
			cache.put(text, found);
		}
		return found;
	}
	/** Bound of the remembered fits and wraps; menus show far fewer different texts at once. */
	private static final int LAYOUT_CACHE_LIMIT = 2000;

	/**
	 * @return the text wrapped at spaces into lines no wider than the room when drawn at the scale, at most {@code most} (the last one cut with an
	 *         ellipsis); not remembered, so callers keep the result while the room stays the same
	 */
	public List<String> wrapScaled(String text, float room, float scale, int most) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && widthScaled(candidate, scale) > room) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		if (lines.size() > most) {
			List<String> cut = new ArrayList<>(lines.subList(0, most));
			cut.set(most - 1, fitScaled(cut.get(most - 1) + " …", room, scale));
			return cut;
		}
		return lines;
	}

	/** The last wrap of a text: menus wrap the same text to the same width every frame. */
	private record Wrapped(int maxWidth, int maxLines, int slot, List<String> lines) {
	}

	private static final Map<String, Wrapped> WRAPPED = new HashMap<>();

	/**
	 * @return the text wrapped at spaces into lines no wider than the width, at most {@code maxLines} (the last one truncated with an ellipsis); the
	 *         list must not be changed, it is remembered for the next call with the same text and width
	 */
	public List<String> wrap(String text, int maxWidth, int maxLines) {
		if (text == null || text.isEmpty()) {
			return List.of();
		}
		int slot = slot(baseQuarters());
		Wrapped last = WRAPPED.get(text);
		if (last != null && last.maxWidth() == maxWidth && last.maxLines() == maxLines && last.slot() == slot) {
			return last.lines();
		}
		List<String> lines = List.copyOf(wrapNow(text, maxWidth, maxLines));
		if (WRAPPED.size() > LAYOUT_CACHE_LIMIT) {
			WRAPPED.clear();
		}
		WRAPPED.put(text, new Wrapped(maxWidth, maxLines, slot, lines));
		return lines;
	}

	private List<String> wrapNow(String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		for (String word : words) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (width(candidate) <= maxWidth || line.isEmpty()) {
				line.setLength(0);
				line.append(candidate);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
				if (lines.size() == maxLines - 1) {
					break;
				}
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		if (lines.size() == maxLines) {
			// the overflow goes onto the last line, truncated with an ellipsis
			int taken = 0;
			for (int i = 0; i < lines.size() - 1; i++) {
				taken += lines.get(i).length() + 1;
			}
			String rest = taken < text.length() ? text.substring(taken) : lines.get(lines.size() - 1);
			lines.set(lines.size() - 1, fit(rest, maxWidth));
		}
		return lines;
	}

	/** The last truncation of a text: shortening it measures it once per removed character, too slow to repeat every frame. */
	private record Fitted(float maxWidth, float scale, int slot, String result) {
	}

	private static final Map<String, Fitted> FITTED = new HashMap<>();

	/** @return the remembered truncation of the text for this width and scale, or null */
	private static String fitted(String text, float maxWidth, float scale, int slot) {
		Fitted last = FITTED.get(text);
		return last != null && last.maxWidth() == maxWidth && last.scale() == scale && last.slot() == slot ? last.result() : null;
	}

	private static String rememberFit(String text, float maxWidth, float scale, int slot, String result) {
		if (FITTED.size() > LAYOUT_CACHE_LIMIT) {
			FITTED.clear();
		}
		FITTED.put(text, new Fitted(maxWidth, scale, slot, result));
		return result;
	}

	/** @return the text, truncated with an ellipsis if it is wider than the width */
	public String fit(String text, int maxWidth) {
		if (width(text) <= maxWidth) {
			return text;
		}
		int slot = slot(baseQuarters());
		String known = fitted(text, maxWidth, 1f, slot);
		if (known != null) {
			return known;
		}
		String cut = text;
		while (!cut.isEmpty() && width(cut + "...") > maxWidth) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return rememberFit(text, maxWidth, 1f, slot, cut.stripTrailing() + "...");
	}

	/** @return the text, truncated with an ellipsis if it is wider than the width when drawn at the scale by {@link #textAtScaled} */
	public String fitScaled(String text, float maxWidth, float scale) {
		// scaled widths are not cached, so a text that fits is remembered as well
		int slot = slot(Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, MAX_QUARTERS));
		String known = fitted(text, maxWidth, scale, slot);
		if (known != null) {
			return known;
		}
		if (widthScaled(text, scale) <= maxWidth) {
			return rememberFit(text, maxWidth, scale, slot, text);
		}
		String cut = text;
		while (!cut.isEmpty() && widthScaled(cut + "...", scale) > maxWidth) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return rememberFit(text, maxWidth, scale, slot, cut.stripTrailing() + "...");
	}

	// ------------------------------------------------------------------------------------------------------ symbols the menu font lacks

	/** @return whether the text has a symbol the menu font has no glyph for (the server's ✯ ✦ ❈), which the game's font draws instead */
	private static boolean hasMissing(String text) {
		if (gameFont) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			if (MissingGlyphs.missing(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	/** Texts split into runs, the menu font's first and then by turns the missing symbols' (a run may be empty); remembered, as they are drawn every frame. */
	private static final Map<String, String[]> RUNS = new HashMap<>();
	/** The symbol runs as text in the game's font. */
	private static final Map<String, Component> PLAIN_RUNS = new HashMap<>();
	private static final int RUNS_LIMIT = 500;

	private static String[] runs(String text) {
		String[] found = RUNS.get(text);
		if (found == null) {
			if (RUNS.size() > RUNS_LIMIT) {
				RUNS.clear();
				PLAIN_RUNS.clear();
			}
			List<String> parts = new ArrayList<>();
			boolean symbols = false;
			int start = 0;
			for (int i = 0; i <= text.length(); i++) {
				if (i == text.length() || MissingGlyphs.missing(text.charAt(i)) != symbols) {
					parts.add(text.substring(start, i));
					start = i;
					symbols = !symbols;
				}
			}
			found = parts.toArray(new String[0]);
			RUNS.put(text, found);
		}
		return found;
	}

	private static Component plainRun(String run) {
		return PLAIN_RUNS.computeIfAbsent(run, Component::literal);
	}

	/**
	 * @param face        the heavier face the text is in, or null for the regular one
	 * @param symbolScale how much larger the game's font is drawn than its own size, in the menu font's units, to match the text's size
	 * @return the width of the text in the menu font's units, its symbols in the game's font
	 */
	private static float units(Font font, Face face, String text, int quarters, float symbolScale) {
		if (!hasMissing(text)) {
			return font.width(face == null ? styled(text, quarters) : styledIn(face, text, quarters));
		}
		String[] runs = runs(text);
		float total = 0f;
		for (int i = 0; i < runs.length; i++) {
			if (runs[i].isEmpty()) {
				continue;
			}
			total += i % 2 == 0 ? font.width(face == null ? styled(runs[i], quarters) : styledIn(face, runs[i], quarters))
				: font.width(plainRun(runs[i])) * symbolScale;
		}
		return total;
	}

	/**
	 * Draws text whose symbols the menu font lacks run by run, in the pose of the menu font (its units): the symbols in the game's font, scaled
	 * to the text's size and on its baseline.
	 */
	private void drawRuns(Face face, String text, int quarters, float symbolScale, int color) {
		var pose = graphics.pose();
		String[] runs = runs(text);
		float cursor = 0f;
		for (int i = 0; i < runs.length; i++) {
			if (runs[i].isEmpty()) {
				continue;
			}
			pose.pushMatrix();
			pose.translate(cursor, 0f);
			if (i % 2 == 0) {
				Component run = face == null ? styled(runs[i], quarters) : styledIn(face, runs[i], quarters);
				graphics.text(font, run, 0, 0, a(color), false);
				cursor += font.width(run);
			} else {
				Component run = plainRun(runs[i]);
				pose.scale(symbolScale, symbolScale);
				graphics.text(font, run, 0, 0, a(color), false);
				cursor += font.width(run) * symbolScale;
			}
			pose.popMatrix();
		}
	}

	/**
	 * Draws text with its top-left corner at a position in screen pixels, using the glyphs of the font for the size.
	 *
	 * @param factor how much the font is scaled, the same for both fonts: the callers know the game font's single size ({@link #fontUnit})
	 */
	private void drawText(String text, float x, float y, float factor, float menuScale, int size, int color) {
		var pose = graphics.pose();
		pose.pushMatrix();
		// snap glyphs to screen pixels; between two pixels they would be blurred
		float pixels = effectiveScale();
		pose.translate(Math.round(x * pixels) / pixels, Math.round(y * pixels) / pixels);
		pose.scale(factor, factor);
		if (hasMissing(text)) {
			drawRuns(null, text, size, menuScale / factor, color);
			pose.popMatrix();
			return;
		}
		Component styledText = styled(text, size);
		graphics.text(font, styledText, 0, 0, a(color), false);
		if (!gameFont && size <= SMALL_TEXT) {
			// Small text (a scaled-down panel at a small GUI scale) has strokes thinner than a pixel, which render as an unreadable faint gray: draw
			// it again one screen pixel to the side, which thickens the strokes by a pixel.
			// the mod fonts are drawn in screen pixels, so one unit here is one screen pixel
			graphics.text(font, styledText, 1, 0, a(Ui.withOpacity(color, 0.3f)), false);
		}
		pose.popMatrix();
	}

	/** Text of this size or smaller (in quarter menu pixels, like the fonts) is drawn slightly bolder. */
	private static final int SMALL_TEXT = 5;

	/** @return the font size for regular text, matching its on-screen size, also in scaled panels */
	private static int baseQuarters() {
		return Mth.clamp(Math.round(effectiveScale() * 4f), 4, MAX_QUARTERS);
	}

	public void text(String text, int x, int y, int color) {
		int quarters = baseQuarters();
		drawText(text, x, y, fontUnit(1f, quarters), 1f, quarters, color);
	}

	/** Draws text at a fractional position so it can move smoothly. */
	public void textAt(String text, float x, float y, int color) {
		int quarters = baseQuarters();
		drawText(text, x, y, fontUnit(1f, quarters), 1f, quarters, color);
	}

	/** Draws scaled text at a fractional position, using the font built for that size. */
	public void textAtScaled(String text, float x, float y, float scale, int color) {
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, MAX_QUARTERS);
		drawText(text, x, y, uiUnit(scale, quarters), scale, quarters, color);
	}

	/** Same for a lyric line in the lyrics font. */
	public void blurredLyricAtScaled(String text, float x, float y, float scale, int color, float radius) {
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI / 4.0 * i;
			lyricAtScaled(text, x + (float) Math.cos(angle) * radius, y + (float) Math.sin(angle) * radius, scale, withOpacity(color, 0.11f));
		}
		lyricAtScaled(text, x, y, scale, withOpacity(color, 0.24f));
	}

	/** Measures scaled text without a Ui instance, for layout done before drawing. */
	public static float measureScaled(Font font, String text, float scale) {
		syncFont();
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, MAX_QUARTERS);
		float unit = uiUnit(scale, quarters);
		return units(font, null, text, quarters, scale / unit) * unit;
	}

	/** @return the width of text drawn by {@link #textAtScaled}, in menu pixels */
	public float widthScaled(String text, float scale) {
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, MAX_QUARTERS);
		Map<String, Integer> cache = GLYPH_WIDTHS[slot(quarters)];
		if (cache == null) {
			cache = GLYPH_WIDTHS[slot(quarters)] = new HashMap<>();
		}
		Integer found = cache.get(text);
		if (found == null) {
			if (cache.size() > 3000) {
				cache.clear();
			}
			found = Math.round(units(font, null, text, quarters, scale / uiUnit(scale, quarters)));
			cache.put(text, found);
		}
		return found * uiUnit(scale, quarters);
	}

	private static Component boldStyled(String text, int quarters) {
		return styledIn(SEMIBOLD, text, quarters);
	}

	private static Component styledIn(Face face, String text, int quarters) {
		int k = Mth.clamp(quarters, 4, face.maxQuarters);
		Map<String, Component> cache = face.texts[slot(k)];
		if (cache == null) {
			cache = face.texts[slot(k)] = new HashMap<>();
		}
		Component found = cache.get(text);
		if (found == null) {
			if (cache.size() > 2000) {
				cache.clear();
			}
			// Use a real bold face built for the size: the game's synthetic bold draws every glyph twice side by side, which smeared the round
			// strokes of the mod fonts (the bottom of a 6 or an 8). The game's pixel font still only has the synthetic bold.
			found = gameFont ? Component.literal(text).withStyle(style -> style.withFont(fontFor(k)).withBold(true))
				: Component.literal(text).withStyle(style -> style.withFont(face.font(k)));
			cache.put(text, found);
		}
		return found;
	}

	/** Draws scaled text in the semibold face, which makes small text heavier. */
	public void textAtScaledBold(String text, float x, float y, float scale, int color) {
		textAtScaledIn(SEMIBOLD, text, x, y, scale, color);
	}

	/** Draws scaled text in the bold face, for page titles. */
	public void textAtScaledTitle(String text, float x, float y, float scale, int color) {
		textAtScaledIn(BOLD, text, x, y, scale, color);
	}

	private void textAtScaledIn(Face face, String text, float x, float y, float scale, int color) {
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, face.maxQuarters);
		float factor = uiUnit(scale, quarters);
		var pose = graphics.pose();
		pose.pushMatrix();
		// snap to screen pixels like other text; between two pixels the glyphs blur
		float pixels = effectiveScale();
		pose.translate(Math.round(x * pixels) / pixels, Math.round(y * pixels) / pixels);
		pose.scale(factor, factor);
		if (hasMissing(text)) {
			drawRuns(face, text, quarters, scale / factor, color);
		} else {
			graphics.text(font, styledIn(face, text, quarters), 0, 0, a(color), false);
		}
		pose.popMatrix();
	}

	/**
	 * @return the top for small text placed next to large text on the same baseline ("821.9k coins", "845 / 2,000"); positioned by eye, it sat too
	 *         low, because the small text was moved down by more than the large text is taller
	 */
	public static float besideTop(float largeTop, float largeScale, float smallScale) {
		return Draw.besideTop(largeTop, largeScale, smallScale);
	}

	/** @return the width of text drawn by {@link #textAtScaledBold}, in menu pixels; remembered, so it can be asked every frame */
	public float boldWidthScaled(String text, float scale) {
		return widthScaledIn(SEMIBOLD, text, scale);
	}

	/** @return the width of text drawn by {@link #textAtScaledTitle}, in menu pixels */
	public float titleWidthScaled(String text, float scale) {
		return widthScaledIn(BOLD, text, scale);
	}

	private float widthScaledIn(Face face, String text, float scale) {
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, face.maxQuarters);
		Map<String, Integer> cache = face.widths[slot(quarters)];
		if (cache == null) {
			cache = face.widths[slot(quarters)] = new HashMap<>();
		}
		Integer found = cache.get(text);
		if (found == null) {
			if (cache.size() > 2000) {
				cache.clear();
			}
			found = Math.round(units(font, face, text, quarters, scale / uiUnit(scale, quarters)));
			cache.put(text, found);
		}
		return found * uiUnit(scale, quarters);
	}

	/**
	 * @return the width of the glyphs themselves of text drawn by {@link #textAtScaledBold}: without the advance after the last glyph, which a
	 *         font's width includes and which makes text centered by it sit a pixel to the left
	 */
	public float boldInkWidthScaled(String text, float scale) {
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, SEMIBOLD.maxQuarters);
		return Math.max(0f, boldWidthScaled(text, scale) - uiUnit(scale, quarters));
	}

	/** @return the size of one screen pixel in the menu pixels drawn now (at the current zoom) */
	public static float screenPixel() {
		return 1f / Math.max(0.01f, effectiveScale());
	}

	/** Extra space between the letters of a section label, as a share of the font size, as in the mockups (0.06em). */
	private static final float LABEL_TRACKING = 0.06f;
	/** Font size of regular menu text in menu pixels, the "em" the tracking is measured in. */
	private static final float EM = 9f;

	/** A section label split into letters, with where each starts, for one size and font. */
	private record Letters(String[] letters, float[] offsets, int quarters, boolean gameFont) {
	}

	/**
	 * Draws a section label: small semibold capitals, set slightly apart. Fonts have no letter spacing, so the letters are drawn one by one at
	 * offsets measured once per label and size.
	 */
	public void sectionLabel(String text, float x, float y, int color) {
		float scale = TextScale.SMALL;
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, MAX_QUARTERS);
		Letters letters = LETTERS.get(text);
		if (letters == null || letters.quarters != quarters || letters.gameFont != gameFont) {
			if (LETTERS.size() >= MAX_LETTER_LABELS) {
				LETTERS.clear();
			}
			int[] codePoints = text.codePoints().toArray();
			String[] parts = new String[codePoints.length];
			float[] offsets = new float[codePoints.length];
			float tracking = LABEL_TRACKING * EM * scale;
			float at = 0f;
			for (int i = 0; i < codePoints.length; i++) {
				parts[i] = Character.toString(codePoints[i]);
				offsets[i] = at;
				at += boldWidthScaled(parts[i], scale) + tracking;
			}
			letters = new Letters(parts, offsets, quarters, gameFont);
			LETTERS.put(text, letters);
		}
		for (int i = 0; i < letters.letters.length; i++) {
			textAtScaledBold(letters.letters[i], x + letters.offsets[i], y, scale, color);
		}
	}

	/** Draws text vertically centered on the given y. */
	public void textMiddle(String text, int x, int middleY, int color) {
		text(text, x, middleY - font.lineHeight / 2, color);
	}

	public void textCentered(String text, int centerX, int middleY, int color) {
		textMiddle(text, centerX - width(text) / 2, middleY, color);
	}

	/** Draws text in the semibold face, centered on the given x by its glyphs and vertically on the given y. */
	public void textCenteredBold(String text, float centerX, int middleY, int color) {
		textAtScaledBold(text, centerX - boldInkWidthScaled(text, TextScale.BODY) / 2f, middleY - font.lineHeight / 2, TextScale.BODY, color);
	}

	public void textRight(String text, int rightX, int middleY, int color) {
		textMiddle(text, rightX - width(text), middleY, color);
	}

	// ---------------------------------------------------------------------------------------------- lyrics in the menu and the HUD
	// Lyric lines in the HUD players use the world lyrics font, built per screen size like the regular text font (lq4 to lq32), so they are just
	// as sharp.

	private static final FontDescription[] LYRIC_HUD_FONTS = new FontDescription[MAX_QUARTERS + 1];

	private static Component lyricStyled(String text, int quarters) {
		int k = Mth.clamp(quarters, 4, MAX_QUARTERS);
		Map<String, Component> cache = LYRIC_TEXTS[slot(k)];
		if (cache == null) {
			cache = LYRIC_TEXTS[slot(k)] = new HashMap<>();
		}
		Component found = cache.get(text);
		if (found == null) {
			if (cache.size() > 2000) {
				cache.clear();
			}
			if (LYRIC_HUD_FONTS[k] == null) {
				LYRIC_HUD_FONTS[k] = new FontDescription.Resource(Lumio.id("lq" + k));
			}
			FontDescription description = gameFont ? FontDescription.DEFAULT : LYRIC_HUD_FONTS[k];
			found = Component.literal(text).withStyle(style -> style.withFont(description));
			cache.put(text, found);
		}
		return found;
	}

	/**
	 * @return the wanted size, raised to the minimum that still renders cleanly: below seven quarter menu pixels at the current screen scale the
	 *         font cannot draw its glyphs well, so labels of scaled-down elements stay at that size
	 */
	public static float readable(float wanted) {
		float screen = effectiveScale();
		return screen <= 0f ? wanted : Math.max(wanted, 7f / (4f * screen));
	}

	/** @return screen pixels per menu pixel, unrounded; fonts are built for this so text is as sharp as possible */
	private static float effectiveScale() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale() * Draw.zoom();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final Map<String, Float>[] LYRIC_FLOAT_WIDTHS = new Map[TEXT_CACHES];

	/**
	 * @return the width of a lyric line drawn by {@link #lyricAtScaled} at the scale, in menu pixels. It is measured in the font it is drawn with:
	 *         glyph widths of other sizes differ due to rounding, and a line composed of parts (such as the one with a highlighted word) would get gaps
	 *         and overlaps where the parts meet.
	 */
	public float lyricWidthScaled(String text, float scale) {
		int quarters = Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, MAX_QUARTERS);
		Map<String, Float> cache = LYRIC_FLOAT_WIDTHS[slot(quarters)];
		if (cache == null) {
			cache = LYRIC_FLOAT_WIDTHS[slot(quarters)] = new HashMap<>();
		}
		Float found = cache.get(text);
		if (found == null) {
			if (cache.size() > 3000) {
				cache.clear();
			}
			found = font.width(lyricStyled(text, quarters)) * fontUnit(scale, quarters);
			cache.put(text, found);
		}
		return found;
	}

	/** @return the width of a lyric line in the lyrics font, in menu pixels */
	public int lyricTextWidth(String text) {
		return (int) Math.ceil(lyricWidthScaled(text, 1f));
	}

	/** @return the lyric line, truncated with an ellipsis if it is wider than the width */
	public String lyricFit(String text, int maxWidth) {
		if (lyricTextWidth(text) <= maxWidth) {
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && lyricTextWidth(cut + "...") > maxWidth) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.stripTrailing() + "...";
	}

	/** Draws a lyric line at a fractional position so it can move smoothly. */
	public void lyricAt(String text, float x, float y, int color) {
		lyricAtScaled(text, x, y, 1f, color);
	}

	/**
	 * Draws a lyric line made of differently colored parts, such as one with a highlighted word. The parts are laid out one after another in font
	 * units at a single screen position, so there are no gaps or overlaps where they meet at any size; parts snapped to pixels individually were
	 * occasionally off by a pixel.
	 */
	public void lyricSegments(float x, float y, float scale, String[] parts, int[] colors) {
		float effective = effectiveScale();
		int quarters = Mth.clamp(Math.round(effective * scale * 4f), 4, MAX_QUARTERS);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(x * effective) / effective, Math.round(y * effective) / effective);
		float factor = fontUnit(scale, quarters);
		pose.scale(factor, factor);
		int offset = 0;
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].isEmpty()) {
				continue;
			}
			Component styled = lyricStyled(parts[i], quarters);
			if ((colors[i] >>> 24) != 0) {
				graphics.text(font, styled, offset, 0, colors[i], false);
			}
			offset += font.width(styled);
		}
		pose.popMatrix();
	}

	/** Same as {@link #lyricSegments} in the menu font (Inter), which is clearer at small sizes; optionally in its semibold face. */
	public void textSegments(float x, float y, float scale, String[] parts, int[] colors, boolean bold) {
		float effective = effectiveScale();
		int quarters = Mth.clamp(Math.round(effective * scale * 4f), 4, MAX_QUARTERS);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(x * effective) / effective, Math.round(y * effective) / effective);
		float factor = uiUnit(scale, quarters);
		pose.scale(factor, factor);
		int offset = 0;
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].isEmpty()) {
				continue;
			}
			Component styled = bold ? boldStyled(parts[i], quarters) : styled(parts[i], quarters);
			if ((colors[i] >>> 24) != 0) {
				graphics.text(font, styled, offset, 0, colors[i], false);
			}
			offset += font.width(styled);
		}
		pose.popMatrix();
	}

	/** Draws a scaled lyric line using the font built for that size. */
	public void lyricAtScaled(String text, float x, float y, float scale, int color) {
		float effective = effectiveScale();
		int quarters = Mth.clamp(Math.round(effective * scale * 4f), 4, MAX_QUARTERS);
		var pose = graphics.pose();
		pose.pushMatrix();
		// snap to screen pixels (glyphs between pixels blur); this moves the text by at most one pixel
		pose.translate(Math.round(x * effective) / effective, Math.round(y * effective) / effective);
		float factor = fontUnit(scale, quarters);
		pose.scale(factor, factor);
		graphics.text(font, lyricStyled(text, quarters), 0, 0, color, false);
		pose.popMatrix();
	}

	private static final FontDescription LYRIC_FONT = new FontDescription.Resource(Lumio.id("lyric"));
	private static final Map<String, Component> LYRICS = new HashMap<>();

	private static Component lyricComponent(String text) {
		if (LYRICS.size() > 500) {
			LYRICS.clear();
		}
		return LYRICS.computeIfAbsent(text, s -> Component.literal(s).withStyle(style -> style.withFont(LYRIC_FONT)));
	}

	/**
	 * @return server-formatted text with its colors in the menu font at the default size, built for the current scale; null while the menu uses
	 *         the game font, which such text already has
	 */
	public static FormattedCharSequence inMenuFont(FormattedText text) {
		syncFont();
		if (gameFont) {
			return null;
		}
		FontDescription menuFont = fontFor(baseQuarters());
		MutableComponent made = Component.empty();
		text.visit((style, part) -> {
			made.append(Component.literal(part).setStyle(style.withFont(menuFont)));
			return Optional.empty();
		}, Style.EMPTY);
		return made.getVisualOrderText();
	}

	/**
	 * @return server-formatted text with its colors in the menu font built for a size (regular or semibold), so it is drawn crisp, one font pixel
	 *         per screen pixel; null while the menu uses the game font
	 */
	public static FormattedCharSequence inMenuFont(FormattedText text, float scale, boolean semibold) {
		syncFont();
		if (gameFont) {
			return null;
		}
		int quarters = menuQuarters(scale, semibold);
		FontDescription menuFont = semibold ? SEMIBOLD.font(quarters) : fontFor(quarters);
		MutableComponent made = Component.empty();
		text.visit((style, part) -> {
			made.append(Component.literal(part).setStyle(style.withFont(menuFont)));
			return Optional.empty();
		}, Style.EMPTY);
		return made.getVisualOrderText();
	}

	/** The game's font with Hypixel's symbols drawn at the capitals' height (font/symbols.json, tools/FontSymbols.java); the rest is the game's. */
	private static final FontDescription SYMBOLS_FONT = new FontDescription.Resource(Lumio.id("symbols"));

	/**
	 * @return server text in the game's font with the symbols the game draws small within their box (location pin, gemstone, star) at the
	 *         capitals' height; only the font of parts in the game's font changes, never the text or its colors
	 */
	public static FormattedText withSymbols(FormattedText text) {
		MutableComponent made = Component.empty();
		text.visit((style, part) -> {
			made.append(Component.literal(part).setStyle(withSymbolsFont(style)));
			return Optional.empty();
		}, Style.EMPTY);
		return made;
	}

	private static Style withSymbolsFont(Style style) {
		return FontDescription.DEFAULT.equals(style.getFont()) ? style.withFont(SYMBOLS_FONT) : style;
	}

	/** A piece of server text: a run in the menu font, or a run of symbols the menu font lacks, in the game's font. */
	public record Piece(FormattedCharSequence text, boolean symbols) {
	}

	/**
	 * @return server text in the menu font built for a size, in pieces: its runs in the menu font, and between them the symbols the menu font has no
	 *         glyph for (the server's ✯ ✦ ❈ ✎), in the game's font, to be drawn at the text's size ({@link #drawPieces}); a single piece when it has
	 *         none; null while the menu uses the game font
	 */
	public static List<Piece> inMenuFontPieces(FormattedText text, float scale, boolean semibold) {
		syncFont();
		if (gameFont) {
			return null;
		}
		if (!hasMissing(text.getString())) {
			return List.of(new Piece(inMenuFont(text, scale, semibold), false));
		}
		int quarters = menuQuarters(scale, semibold);
		FontDescription menuFont = semibold ? SEMIBOLD.font(quarters) : fontFor(quarters);
		List<Piece> pieces = new ArrayList<>();
		MutableComponent[] menuRun = {Component.empty()};
		text.visit((style, part) -> {
			String[] runs = runs(part);
			for (int i = 0; i < runs.length; i++) {
				if (runs[i].isEmpty()) {
					continue;
				}
				if (i % 2 == 0) {
					menuRun[0].append(Component.literal(runs[i]).setStyle(style.withFont(menuFont)));
				} else {
					pieces.add(new Piece(menuRun[0].getVisualOrderText(), false));
					menuRun[0] = Component.empty();
					pieces.add(new Piece(Component.literal(runs[i]).setStyle(withSymbolsFont(style)).getVisualOrderText(), true));
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		pieces.add(new Piece(menuRun[0].getVisualOrderText(), false));
		return pieces;
	}

	/** @return how much larger the game's font is drawn than its own size in {@link #inMenuFontPieces} text, so its symbols match the text */
	public static float symbolScale(float scale, boolean semibold) {
		return scale / menuFontUnit(scale, semibold);
	}

	/** @return the width of pieces in the menu font's units */
	public static float piecesWidth(Font font, List<Piece> pieces, float symbolScale) {
		float total = 0f;
		for (Piece piece : pieces) {
			total += font.width(piece.text()) * (piece.symbols() ? symbolScale : 1f);
		}
		return total;
	}

	/** Draws pieces one after the other, in the pose of the menu font (its units), the symbols scaled to the text's size. */
	public static void drawPieces(GuiGraphicsExtractor graphics, Font font, List<Piece> pieces, float symbolScale, int color, boolean shadow) {
		var pose = graphics.pose();
		float cursor = 0f;
		for (Piece piece : pieces) {
			pose.pushMatrix();
			pose.translate(cursor, 0f);
			float pieceScale = piece.symbols() ? symbolScale : 1f;
			pose.scale(pieceScale, pieceScale);
			graphics.text(font, HudStyle.readable(piece.text()), 0, 0, color, shadow);
			pose.popMatrix();
			cursor += font.width(piece.text()) * pieceScale;
		}
	}

	/** @return the glyph scale of {@link #inMenuFont(FormattedText, float, boolean)} text in menu pixels */
	public static float menuFontUnit(float scale, boolean semibold) {
		return uiUnit(scale, menuQuarters(scale, semibold));
	}

	private static int menuQuarters(float scale, boolean semibold) {
		return Mth.clamp(Math.round(effectiveScale() * scale * 4f), 4, semibold ? SEMIBOLD.maxQuarters : MAX_QUARTERS);
	}

	/** @return the glyph scale of {@link #inMenuFont} text in menu pixels; its fonts are built in screen pixels */
	public static float menuFontUnit() {
		return 4f / baseQuarters();
	}

	/** Text in the lyrics font, cached, as the game draws text in the world. */
	private static final Map<String, FormattedCharSequence> LYRIC_SEQUENCES = new HashMap<>();

	public static FormattedCharSequence lyricSequence(String text) {
		if (LYRIC_SEQUENCES.size() > 4000) {
			LYRIC_SEQUENCES.clear();
		}
		return LYRIC_SEQUENCES.computeIfAbsent(text, s -> lyricComponent(s).getVisualOrderText());
	}

}
