package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ui.Ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pre-rendered sprites for the lyric lines floating in the world.
 * <p>
 * The game renders text by emitting a quad per glyph every frame, which is slow: about 7 microseconds per glyph, and a lyric line was drawn about
 * six times (four glow copies, the dim line and the sung part). Instead every line is rendered once, off the render thread, with the same font
 * into a texture: the glyphs, and below them the glow. Drawing a line then costs three quads per row, regardless of its content.
 * <p>
 * The texture holds the text rows at the top, each in a cell with margin for the glow, and their glow below. Both are alpha-only (white); the
 * vertex color tints them.
 */
public final class LyricSprites {
	/**
	 * Texture pixels per text unit (one font pixel as the game draws it). Higher is sharper and larger: a line on a 4K screen covers about five
	 * screen pixels per unit, so the glyphs are never stretched there; smaller on screen, the mipmaps keep them smooth.
	 */
	public static final float SCALE = 5f;
	/** Maximum rows of a lyric line; longer lines fall back to regular text rendering. */
	public static final int MAX_ROWS = 3;
	/** Maximum row width in text units, at which lines are wrapped. */
	public static final float MAX_WIDTH = 300f;
	/** Row spacing in text units. */
	public static final float LINE_HEIGHT = 38f;

	/** Height of a row's cell in units. The text is centered in it, with margin above and below for the glow. */
	private static final float CELL = 52f;
	/** Horizontal margin of a row in units, for the glow. */
	private static final float PAD = 12f;
	/** The room around a row's text the glow reaches into, in text units: placement keeps it free of blocks too. */
	public static final float GLOW_ROOM = PAD;

	/** @return the top of a line's cells (its glow included), from the line's center, in text units (down is positive) */
	public static float boundsTop(int rows) {
		return -rows / 2f * LINE_HEIGHT - TEXT_TOP;
	}

	/** @return the bottom of a line's cells (its glow included), from the line's center, in text units */
	public static float boundsBottom(int rows) {
		return boundsTop(rows) + (rows - 1) * LINE_HEIGHT + CELL;
	}

	/** @return the width of a line (its widest row) with the glow on both sides, in text units */
	public static float boundsWidth(float width) {
		return width + 2 * PAD;
	}
	/** Top of the text within its cell. */
	private static final float TEXT_TOP = 7f;
	/** Baseline offset from the top of the line in units, determined by overlaying the sprite on the game's text. */
	private static final float BASELINE = 27f;
	/** The game starts its glyphs slightly further right than the sprite does, as measured by overlaying them. */
	private static final float SHIFT_X = 4.5f;
	/** The glow is the text blurred twice: a tight light hugging the glyphs and a wide soft one around it. Blur radii in units. */
	private static final float GLOW_TIGHT = 1.3f;
	private static final float GLOW_WIDE = 3.3f;
	/** How much the glyphs are thinned, in units: the width of the stroke removed from their edges. */
	private static final float THIN = 0.7f;

	private static final int CELL_PX = Math.round(CELL * SCALE);
	private static final int TEX_W = Math.round((MAX_WIDTH + 2 * PAD) * SCALE);
	private static final int TEX_H = MAX_ROWS * CELL_PX;
	/** The glow is soft, so it is kept at half the size in a texture of its own; it covers the same area as the glyphs. */
	private static final int GLOW_W = TEX_W / 2;
	private static final int GLOW_H = TEX_H / 2;
	private static final int MOST_LEVELS = 5;
	private static final int SLOT_COUNT = 8;
	/** Reference text used to calibrate the font size against the game's rendering. */
	private static final String REFERENCE = "the quick brown fox jumps over the lazy dog while waiting for a ride in the dark";

	private static final ExecutorService BAKER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Lumio lyrics");
		thread.setDaemon(true);
		return thread;
	});

	private static final Slot[] SLOTS = new Slot[SLOT_COUNT];
	private static Font font;
	private static boolean fontFailed;

	private LyricSprites() {
	}

	/** The textures of one line at a time: the glyphs, and their glow at half the size. */
	private static final class Slot {
		final Identifier id;
		final Identifier glowId;
		final MipTexture texture;
		final MipTexture glow;
		/** For the glow, which is soft and does not occlude. */
		final RenderType glowType;
		/** For the glyphs, which occlude what is behind them. */
		final RenderType letterType;
		boolean used;

		Slot(int index) {
			id = Lumio.id("lyric_sprite/" + index);
			glowId = Lumio.id("lyric_glow/" + index);
			texture = new MipTexture("Lumio lyric " + index, TEX_W, TEX_H, MipTexture.levelsFor(TEX_W, TEX_H, MOST_LEVELS));
			glow = new MipTexture("Lumio lyric glow " + index, GLOW_W, GLOW_H, MipTexture.levelsFor(GLOW_W, GLOW_H, MOST_LEVELS));
			Minecraft.getInstance().getTextureManager().register(id, texture);
			Minecraft.getInstance().getTextureManager().register(glowId, glow);
			glowType = OutlineRenderTypes.glowMipmapped(glowId);
			letterType = OutlineRenderTypes.lyricMipmapped(id);
		}
	}

	/** One row of a line: its width and the x position of every glyph, in text units. */
	public record Row(String text, float width, float[] letterX) {
	}

	/** A baked line: its rows, and the mip levels of the glyphs and of the glow. */
	private record Baked(List<Row> rows, int[][] letters, int[][] glow) {
	}

	public enum State {
		BAKING, READY, FAILED
	}

	/** A lyric line as a sprite. Only used and drawn on the render thread. */
	public static final class Sprite {
		private final Slot slot;
		private final CompletableFuture<Baked> baked;
		private final boolean hand;
		private final boolean classic;
		private State state = State.BAKING;
		private List<Row> rows = List.of();

		private Sprite(Slot slot, CompletableFuture<Baked> baked, boolean hand, boolean classic) {
			this.slot = slot;
			this.baked = baked;
			this.hand = hand;
			this.classic = classic;
		}

		/** @return whether the line is handwritten, which gets a soft shadow */
		public boolean handwritten() {
			return hand;
		}

		/** @return a row's horizontal offset from the center: handwritten rows alternate left and right, clean ones are centered */
		private float shift(int row) {
			return hand ? (row % 2 == 1 ? 46f : -16f) : classic ? (row % 2 == 1 ? CLASSIC_SHIFT : -CLASSIC_SHIFT_BACK) : 0f;
		}

		/** @return whether the sprite can be drawn; a finished sprite is uploaded to its texture here, on the render thread */
		public State state() {
			if (state == State.BAKING && baked.isDone()) {
				try {
					Baked result = baked.get();
					slot.texture.upload(result.letters());
					slot.glow.upload(result.glow());
					rows = result.rows();
					state = State.READY;
				} catch (Exception | Error e) {
					state = State.FAILED;
				}
			}
			return state;
		}

		public List<Row> rows() {
			return rows;
		}

		public RenderType renderType(Layer layer) {
			// The glow layers are soft and must not occlude: drawn with the glyph type, the sung part's glow hid everything around it (glass blocks
			// disappeared behind the sung word).
			return layer.glow ? slot.glowType : slot.letterType;
		}

		/** Frees the texture for the next line. */
		public void release() {
			baked.cancel(false);
			slot.used = false;
		}

		/**
		 * Emits one layer of the line's quads around its center, in text units. The sung part and its glow end at the sung edge, which is not a
		 * hard cut: glyphs blend in over a stretch, so the sung part fills a word from left to right and a half sung glyph is half lit.
		 *
		 * @param sung how many glyphs of each row are sung; fractional values put the edge of the sung part inside a glyph
		 */
		public void emit(PoseStack.Pose pose, VertexConsumer consumer, float[] sung, Layer layer, int color) {
			int count = rows.size();
			for (int k = 0; k < count; k++) {
				Row row = rows.get(k);
				float x = rowLeft(k);
				float top = (k - count / 2f) * LINE_HEIGHT - TEXT_TOP;
				float left = x - PAD;
				float full = row.width() + 2 * PAD;
				float u1 = full * SCALE / TEX_W;
				// the glow texture covers the same area as the glyphs' at half the size, so both take the same coordinates
				float v0 = k * CELL_PX;
				float v1 = v0 + CELL_PX;
				if (!layer.sungOnly) {
					quad(consumer, pose, left, top, left + full, top + CELL, 0f, 0f, v0 / TEX_H, u1, v1 / TEX_H, color);
					continue;
				}
				int length = row.text().length();
				float here = Math.max(0f, Math.min(sung[k], length));
				if (layer == Layer.HOT) {
					// the light following the sung edge, fading in at the row's start and out towards its end
					float strength = Math.min(1f, here / 2f) * Math.min(1f, (length - here) / 3f);
					if (strength > 0.01f) {
						float edge = PAD + edgeX(row, here);
						float from = Math.max(0f, edge - SOFT_EDGE * HOT_BEHIND);
						float to = Math.min(full, edge + SOFT_EDGE * HOT_AHEAD);
						int none = color & 0xFFFFFF;
						int lit = fade(color, strength);
						quadGradient(consumer, pose, left + from, top, left + edge, top + CELL, 0f, from * SCALE / TEX_W, v0 / TEX_H, edge * SCALE / TEX_W,
							v1 / TEX_H, none, lit);
						quadGradient(consumer, pose, left + edge, top, left + to, top + CELL, 0f, edge * SCALE / TEX_W, v0 / TEX_H, to * SCALE / TEX_W,
							v1 / TEX_H, lit, none);
					}
					continue;
				}
				if (here <= 0f) {
					continue;
				}
				// the whole row plus the margin to its right once it is fully sung
				float edge = here >= length ? full : PAD + edgeX(row, here);
				if (here >= length) {
					quad(consumer, pose, left, top, left + edge, top + CELL, 0f, 0f, v0 / TEX_H, edge * SCALE / TEX_W, v1 / TEX_H, color);
					continue;
				}
				float from = edge - SOFT_EDGE;
				float to = Math.min(full, edge + SOFT_EDGE * 0.4f);
				if (from > 0f) {
					quad(consumer, pose, left, top, left + from, top + CELL, 0f, 0f, v0 / TEX_H, from * SCALE / TEX_W, v1 / TEX_H, color);
				}
				float start = Math.max(0f, from);
				quadGradient(consumer, pose, left + start, top, left + to, top + CELL, 0f, start * SCALE / TEX_W, v0 / TEX_H, to * SCALE / TEX_W, v1 / TEX_H,
					fade(color, (edge + SOFT_EDGE * 0.4f - start) / (SOFT_EDGE * 1.4f)), fade(color, (edge + SOFT_EDGE * 0.4f - to) / (SOFT_EDGE * 1.4f)));
			}
		}

		/**
		 * The layers of a line, back to front: the glow of the whole line, the glow of the sung part, the line, and the sung part. They are
		 * emitted separately rather than together: translucent quads of one geometry are sorted by their centers, not by emission order, so the glow
		 * would end up over the glyphs. The depth is the layer's offset towards the viewer, in text units.
		 */
		public enum Layer {
			GLOW(-0.04f, true, false),
			/** The classic glow's light that follows the edge of the sung part. */
			HOT(-0.035f, true, true),
			SUNG_GLOW(-0.03f, true, true),
			TEXT(0.01f, false, false),
			SUNG(0.02f, false, true);

			public final float depth;
			/** Whether the layer is the glow below the glyphs, and whether it ends at the sung edge. */
			final boolean glow;
			final boolean sungOnly;

			Layer(float depth, boolean glow, boolean sungOnly) {
				this.depth = depth;
				this.glow = glow;
				this.sungOnly = sungOnly;
			}
		}

		/**
		 * Draws the line flat in the menu, for the preview of the world lyrics: the glow of the sung part, the line, and the sung part on top, as in
		 * the world, centered on a point.
		 *
		 * @param unit menu pixels per text unit
		 * @param sung how many glyphs of each row are sung
		 */
		public void drawFlat(GuiGraphicsExtractor graphics, float centerX, float centerY, float unit, float[] sung, int glowColor, int textColor, int sungColor) {
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(centerX, centerY);
			pose.scale(unit / SCALE, unit / SCALE);
			int count = rows.size();
			for (int k = 0; k < count; k++) {
				Row row = rows.get(k);
				pose.pushMatrix();
				pose.translate((rowLeft(k) - PAD) * SCALE, ((k - count / 2f) * LINE_HEIGHT - TEXT_TOP) * SCALE);
				int full = Math.round((row.width() + 2 * PAD) * SCALE);
				int length = row.text().length();
				float here = Math.max(0f, Math.min(sung[k], length));
				int sungWidth = here >= length ? full : Math.round((PAD + edgeX(row, here)) * SCALE);
				if (here > 0f) {
					flatGlow(graphics, k * CELL_PX, sungWidth, glowColor);
				}
				flat(graphics, k * CELL_PX, full, textColor);
				if (here > 0f) {
					flat(graphics, k * CELL_PX, sungWidth, sungColor);
				}
				pose.popMatrix();
			}
			pose.popMatrix();
		}

		/** Draws a part of a row's cell, from its left edge, at the origin. */
		private void flat(GuiGraphicsExtractor graphics, int v, int width, int color) {
			if ((color >>> 24) == 0 || width <= 0) {
				return;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, slot.id, 0, 0, 0f, v, width, CELL_PX, width, CELL_PX, TEX_W, TEX_H, color);
		}

		/** Draws a part of a row's glow, from its left edge, at the origin: from the glow texture at half the size, drawn at full size. */
		private void flatGlow(GuiGraphicsExtractor graphics, int v, int width, int color) {
			if ((color >>> 24) == 0 || width <= 0) {
				return;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, slot.glowId, 0, 0, 0f, v / 2f, width, CELL_PX, width / 2, CELL_PX / 2, GLOW_W, GLOW_H, color);
		}

		/**
		 * Emits a part of a row's glyphs, from one glyph position to another (fractional positions cut inside a glyph): for parts of a line drawn
		 * in their own color, such as the word on the highlight box.
		 */
		public void emitRange(PoseStack.Pose pose, VertexConsumer consumer, int k, float from, float to, Layer layer, int color) {
			if (k < 0 || k >= rows.size() || to <= from) {
				return;
			}
			Row row = rows.get(k);
			int length = row.text().length();
			float full = row.width() + 2 * PAD;
			// the first and the last glyph keep the margin beside them, so nothing of their edges is cut off
			float a = from <= 0f ? 0f : PAD + edgeX(row, Math.min(from, length));
			float b = to >= length ? full : PAD + edgeX(row, to);
			float left = rowLeft(k) - PAD;
			float top = (k - rows.size() / 2f) * LINE_HEIGHT - TEXT_TOP;
			float v0 = k * CELL_PX;
			float v1 = v0 + CELL_PX;
			quad(consumer, pose, left + a, top, left + b, top + CELL, 0f, a * SCALE / TEX_W, v0 / TEX_H, b * SCALE / TEX_W, v1 / TEX_H, color);
		}

		/** @return where a row's glyphs start, from the line's center, in text units */
		public float rowLeft(int k) {
			return -rows.get(k).width() / 2f + shift(k) + SHIFT_X;
		}

		/** @return the baseline of a row, from the line's center, in text units (down is positive) */
		public float rowBaseline(int k) {
			return (k - rows.size() / 2f) * LINE_HEIGHT + BASELINE;
		}

		/** @return the x of a (possibly fractional) glyph position of a row, from the line's center, in text units */
		public float charX(int k, float chars) {
			return rowLeft(k) + edgeX(rows.get(k), chars);
		}

		/** @return the widest row's width in text units, for placing things beside the line */
		public float width() {
			float widest = 0f;
			for (Row row : rows) {
				widest = Math.max(widest, row.width());
			}
			return widest;
		}
	}

	/** Width of the blend window over which glyphs turn from the color to white at the sung edge. */
	private static final float SOFT_EDGE = 11f;
	/** How far the classic glow's light reaches behind and ahead of the sung edge, in blend windows. */
	private static final float HOT_BEHIND = 2.6f;
	private static final float HOT_AHEAD = 1.8f;

	/** @return the x of a (possibly fractional) glyph position within the row, in text units */
	private static float edgeX(Row row, float chars) {
		int length = row.text().length();
		if (chars >= length) {
			return row.width();
		}
		int i = (int) chars;
		float a = row.letterX()[i];
		float b = row.letterX()[i + 1];
		return a + (b - a) * (chars - i);
	}

	/** @return the color with its alpha multiplied by the amount, clamped to 0..1 */
	private static int fade(int color, float amount) {
		float clamped = Math.max(0f, Math.min(1f, amount));
		return Math.round(((color >>> 24) & 0xFF) * clamped) << 24 | (color & 0xFFFFFF);
	}

	/** Emits a quad with one color on its left side and another on its right, interpolated across. */
	private static void quadGradient(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
									 float u0, float v0, float u1, float v1, int left, int right) {
		consumer.addVertex(pose, x0, y0, z).setUv(u0, v0).setColor(left);
		consumer.addVertex(pose, x1, y0, z).setUv(u1, v0).setColor(right);
		consumer.addVertex(pose, x1, y1, z).setUv(u1, v1).setColor(right);
		consumer.addVertex(pose, x0, y1, z).setUv(u0, v1).setColor(left);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
							 float u0, float v0, float u1, float v1, int color) {
		consumer.addVertex(pose, x0, y0, z).setUv(u0, v0).setColor(color);
		consumer.addVertex(pose, x1, y0, z).setUv(u1, v0).setColor(color);
		consumer.addVertex(pose, x1, y1, z).setUv(u1, v1).setColor(color);
		consumer.addVertex(pose, x0, y1, z).setUv(u0, v1).setColor(color);
	}

	/** Starts rendering the sprite for the rows of a line, in the clean or the handwritten style; see {@link #begin(List, boolean, boolean)}. */
	public static Sprite begin(List<String> rows, boolean handwritten) {
		return begin(rows, handwritten, false);
	}

	/**
	 * Starts rendering the sprite for the rows of a line.
	 *
	 * @param classic in the face of the lyrics before their redesign (the rounded, slanted lyrics font with rows set off to the sides), for the
	 *                classic glow; ignored for handwriting
	 *
	 * @return the sprite, or null if none can be made (line too long, no free texture, or the font failed to load), in which case the line is
	 *         drawn as regular text
	 */
	public static Sprite begin(List<String> rows, boolean handwritten, boolean classic) {
		return begin(rows, handwritten, classic, 1f);
	}

	/**
	 * Same, with the text set smaller inside the sprite: a long line keeps all of its words in the rows the sprite has room for, at this share of
	 * the normal size, instead of losing what does not fit.
	 */
	public static Sprite begin(List<String> rows, boolean handwritten, boolean classic, float textScale) {
		if (rows.isEmpty() || rows.size() > MAX_ROWS || fontFailed) {
			return null;
		}
		Font hand = handwritten ? handFont() : null;
		float em = emUnits();
		if (em <= 0f) {
			return null;
		}
		Slot slot = null;
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (SLOTS[i] == null) {
				SLOTS[i] = new Slot(i);
			}
			if (!SLOTS[i].used) {
				slot = SLOTS[i];
				break;
			}
		}
		if (slot == null) {
			return null;
		}
		slot.used = true;
		Font sized = font.deriveFont(em * SCALE * textScale);
		List<String> texts = List.copyOf(rows);
		// the game's exact glyph advances: glyphs are placed there so the line is exactly as wide as the game would render it
		var splitter = Minecraft.getInstance().font.getSplitter();
		float[][] positions = new float[texts.size()][];
		for (int k = 0; k < positions.length; k++) {
			String text = texts.get(k);
			positions[k] = new float[text.length() + 1];
			for (int i = 1; i <= text.length(); i++) {
				positions[k][i] = splitter.stringWidth(Ui.lyricSequence(text.substring(0, i))) * textScale;
			}
		}
		Font handSized = hand == null ? null : hand.deriveFont(em * SCALE * HAND_SIZE * textScale);
		Font clean = handwritten || classic ? null : cleanFont();
		Font cleanSized = clean == null ? null : clean.deriveFont(em * SCALE * CLEAN_SIZE * textScale);
		return new Sprite(slot, CompletableFuture.supplyAsync(() -> bake(sized, handSized, cleanSized, texts, positions), BAKER), hand != null, classic && hand == null);
	}

	/** Size of the handwritten font relative to the lyrics font, whose glyphs are smaller for the same size. */
	private static final float HAND_SIZE = 1.0f;
	/** The classic face sets its second row this far to the right and the first this far to the left, in text units, as before. */
	private static final float CLASSIC_SHIFT = 24f;
	private static final float CLASSIC_SHIFT_BACK = 8f;
	/** Scale of the emphasized word of a line, and how much smaller the rows after the first are. */
	private static final float HAND_BIG = 1.25f;
	private static final float HAND_LATER_ROWS = 0.86f;
	/** How much a handwritten row rises from start to end, in degrees, like handwriting that drifts upwards. */
	private static final float HAND_RISE = 3f;

	private static Font hand;
	private static boolean handTried;

	/** @return the handwriting font (Kalam, SIL Open Font License), or null if it could not be loaded */
	private static Font handFont() {
		if (!handTried) {
			handTried = true;
			try (InputStream in = LyricSprites.class.getResourceAsStream("/assets/" + Lumio.MOD_ID + "/font/hand.ttf")) {
				hand = in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
			} catch (Exception | Error e) {
				hand = null;
			}
		}
		return hand;
	}

	/**
	 * Renders a row in handwriting style: every word gets its own size and baseline and is rotated slightly, the row rises as it goes, and the
	 * longest word of the first row is emphasized, larger and slightly lower, like hand-lettered lyrics. Everything is scaled down if it would not
	 * fit the cell or the width.
	 *
	 * @return the x of every glyph in text units from the row start, with the row end last; null if the font lacks a glyph of the row, which is
	 *         then rendered normally
	 */
	private static float[] handRow(Graphics2D g, Font sized, String text, int index, float baseline, FontRenderContext context) {
		if (text.isBlank() || sized.canDisplayUpTo(text) != -1) {
			return null;
		}
		String[] words = text.split(" ", -1);
		// the emphasized word: the longest of the first row, if it has a few words
		int big = -1;
		if (index == 0 && words.length >= 2) {
			int longest = 3;
			for (int j = 0; j < words.length; j++) {
				if (words[j].length() >= longest) {
					longest = words[j].length();
					big = j;
				}
			}
		}
		float rowSize = index == 0 ? 1f : HAND_LATER_ROWS;
		float space = (float) sized.deriveFont(sized.getSize2D() * rowSize).createGlyphVector(context, " ").getLogicalBounds().getWidth() * 1.15f;
		Random random = new Random(text.hashCode() * 31L + index);
		float[] letterX = new float[text.length() + 1];
		List<Shape> shapes = new ArrayList<>();
		float x = 0f;
		int at = 0;
		float rise = (float) Math.tan(Math.toRadians(HAND_RISE));
		for (int j = 0; j < words.length; j++) {
			String word = words[j];
			float factor = rowSize * (j == big ? HAND_BIG : 0.94f + 0.1f * random.nextFloat());
			Font font = sized.deriveFont(sized.getSize2D() * factor);
			GlyphVector vector = font.createGlyphVector(context, word);
			if (vector.getNumGlyphs() != word.length()) {
				return null; // characters that do not map to one glyph each: render normally
			}
			float advance = (float) vector.getLogicalBounds().getWidth();
			for (int i = 0; i < word.length(); i++) {
				letterX[at + i] = (x + (float) vector.getGlyphPosition(i).getX()) / SCALE;
			}
			// rise along the row, emphasized word slightly lower, every word jittered a little
			float lift = -(x + advance / 2f) * rise + (j == big ? 2.5f * SCALE : 0f) + (random.nextFloat() - 0.5f) * 2.4f * SCALE;
			double angle = Math.toRadians(-HAND_RISE * 0.5f + (random.nextFloat() - 0.5f) * 3.5f + (j == big ? -1.5f : 0f));
			AffineTransform place = new AffineTransform();
			place.translate(x + advance / 2f, lift);
			place.rotate(angle);
			place.translate(-advance / 2f, 0);
			shapes.add(place.createTransformedShape(vector.getOutline()));
			x += advance;
			at += word.length();
			if (j < words.length - 1) {
				letterX[at] = x / SCALE;
				x += space;
				at++;
			}
		}
		letterX[text.length()] = x / SCALE;
		// center the rise on the row's middle instead of only rising from its start
		float middleLift = x / 2f * rise;
		Rectangle2D bounds = null;
		for (Shape shape : shapes) {
			bounds = bounds == null ? shape.getBounds2D() : bounds.createUnion(shape.getBounds2D());
		}
		// fit everything into the cell, with margin for the glow, and no wider than the maximum row width
		float roomUp = (TEXT_TOP + BASELINE - 5f) * SCALE;
		float roomDown = (CELL - TEXT_TOP - BASELINE - 5f) * SCALE;
		float top = (float) -(bounds.getMinY() + middleLift);
		float bottom = (float) (bounds.getMaxY() + middleLift);
		float scale = Math.min(1f, Math.min((MAX_WIDTH - 2f) * SCALE / Math.max(1f, x), Math.min(roomUp / Math.max(1f, top), roomDown / Math.max(1f, bottom))));
		AffineTransform toCell = AffineTransform.getTranslateInstance(PAD * SCALE, baseline);
		toCell.scale(scale, scale);
		toCell.translate(0, middleLift);
		g.setColor(Color.WHITE);
		// a thin stroke around the glyphs makes them slightly heavier, like a pen pressed down
		g.setStroke(new BasicStroke(0.45f * SCALE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (Shape shape : shapes) {
			Shape placed = toCell.createTransformedShape(shape);
			g.fill(placed);
			g.draw(placed);
		}
		for (int i = 0; i < letterX.length; i++) {
			letterX[i] *= scale;
		}
		return letterX;
	}

	/**
	 * Font size in text units such that a line is as wide as the game renders it. Calibrated once from the game's width of a long reference text.
	 */
	private static float emUnits;

	private static float emUnits() {
		if (emUnits > 0f || fontFailed) {
			return emUnits;
		}
		try (InputStream in = LyricSprites.class.getResourceAsStream("/assets/" + Lumio.MOD_ID + "/font/lyric.ttf")) {
			if (in == null) {
				fontFailed = true;
				return 0f;
			}
			font = Font.createFont(Font.TRUETYPE_FONT, in);
			float gameWidth = Minecraft.getInstance().font.getSplitter().stringWidth(Ui.lyricSequence(REFERENCE));
			float awtWidth = (float) font.deriveFont(100f).createGlyphVector(new FontRenderContext(null, true, true), REFERENCE).getLogicalBounds().getWidth();
			emUnits = awtWidth > 0f ? gameWidth / awtWidth * 100f : 0f;
			fontFailed = emUnits <= 0f;
		} catch (Exception | Error e) {
			fontFailed = true;
		}
		return emUnits;
	}

	/** Size of Inter Bold, the clean style's font, relative to the lyrics font, whose glyphs are smaller for the same size. */
	private static final float CLEAN_SIZE = 0.9f;
	private static Font clean;
	private static boolean cleanTried;

	/** @return Inter Bold for the clean style, or null if it could not be loaded, in which case the lyrics font is used */
	private static synchronized Font cleanFont() {
		if (!cleanTried) {
			cleanTried = true;
			try (InputStream in = LyricSprites.class.getResourceAsStream("/assets/" + Lumio.MOD_ID + "/font/inter_bold.ttf")) {
				clean = in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
			} catch (Exception | Error e) {
				clean = null;
			}
		}
		return clean;
	}

	private static Font inter;
	private static boolean interTried;

	/** @return a font with the glyph: the lyrics font, Inter (Cyrillic, Greek, yen sign), or the system font, which covers most of the rest */
	private static Font withGlyph(Font sized, char letter) {
		if (sized.canDisplay(letter)) {
			return sized;
		}
		if (!interTried) {
			interTried = true;
			try (InputStream in = LyricSprites.class.getResourceAsStream("/assets/" + Lumio.MOD_ID + "/font/inter.ttf")) {
				inter = in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
			} catch (Exception | Error e) {
				inter = null;
			}
		}
		if (inter != null) {
			Font own = inter.deriveFont(sized.getSize2D());
			if (own.canDisplay(letter)) {
				return own;
			}
		}
		return new Font(Font.DIALOG, Font.PLAIN, 1).deriveFont(sized.getSize2D());
	}

	/**
	 * @return the font to lay out a whole row in, or null when the lyrics font has all its glyphs (each glyph is then placed where the game puts
	 *         it): Inter for Cyrillic and Greek, which matches the lyrics style, otherwise a system font covering nearly every script
	 */
	private static Font rowFont(Font sized, String text) {
		if (sized.canDisplayUpTo(text) == -1) {
			return null;
		}
		withGlyph(sized, ' '); // loads Inter
		if (inter != null) {
			Font own = inter.deriveFont(sized.getSize2D());
			if (own.canDisplayUpTo(text) == -1) {
				return own;
			}
		}
		// a clean modern system font matching the lyrics style; Java's default font renders Chinese in an old serif style
		for (String name : SYSTEM_FONTS) {
			Font system = SYSTEM_FONT_CACHE.computeIfAbsent(name, key -> new Font(key, Font.PLAIN, 1));
			if (system.getFamily().equalsIgnoreCase(name) && system.canDisplayUpTo(text) == -1) {
				return system.deriveFont(sized.getSize2D());
			}
		}
		return new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(sized.getSize2D());
	}

	/** System fonts tried in order: Chinese, Japanese, Korean, then Arabic, Hebrew and more, Indic scripts, Thai. */
	private static final String[] SYSTEM_FONTS = {"Microsoft YaHei UI", "Microsoft YaHei", "Yu Gothic UI", "Malgun Gothic", "Segoe UI", "Nirmala UI",
		"Leelawadee UI", "Noto Sans", "Arial Unicode MS"};
	private static final Map<String, Font> SYSTEM_FONT_CACHE = new ConcurrentHashMap<>();

	/**
	 * Renders a row as a single text run: shaped (Arabic letters joined), in reading order (right to left where applicable), with system font
	 * fallbacks for missing glyphs.
	 *
	 * @return the x of every glyph in text units from the row start, with the row end last; for right-to-left rows the glyphs are spread evenly,
	 *         since the sung part sweeps across from one side
	 */
	private static float[] layRow(Graphics2D g, Font font, String text, FontRenderContext context, float baseline) {
		float[] letterX = new float[text.length() + 1];
		if (text.isBlank()) {
			return letterX;
		}
		TextLayout layout = new TextLayout(text, font, context);
		Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(PAD * SCALE, baseline));
		g.setColor(Color.WHITE);
		g.fill(outline);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(THIN * SCALE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(outline);
		float width = layout.getAdvance() / SCALE;
		if (!layout.isLeftToRight()) {
			for (int i = 0; i <= text.length(); i++) {
				letterX[i] = width * i / text.length();
			}
			return letterX;
		}
		for (int i = 0; i < text.length(); i++) {
			float[] caret = layout.getCaretInfo(TextHitInfo.leading(i));
			letterX[i] = Math.max(i > 0 ? letterX[i - 1] : 0f, caret[0] / SCALE);
		}
		letterX[text.length()] = Math.max(width, letterX[text.length() - 1]);
		return letterX;
	}

	private static Baked bake(Font sized, Font hand, Font clean, List<String> texts, float[][] positions) {
		FontRenderContext context = new FontRenderContext(null, true, true);
		int textHeight = MAX_ROWS * CELL_PX;
		BufferedImage image = new BufferedImage(TEX_W, textHeight, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setColor(Color.WHITE);
		List<Row> rows = new ArrayList<>();
		for (int k = 0; k < texts.size(); k++) {
			String text = texts.get(k);
			float[] letterX = positions[k];
			float baseline = k * CELL_PX + (TEXT_TOP + BASELINE) * SCALE;
			float[] handX = hand == null ? null : handRow(g, hand, text, k, baseline, context);
			if (handX != null) {
				rows.add(new Row(text, handX[text.length()], handX));
				continue;
			}
			if (clean != null && clean.canDisplayUpTo(text) == -1) {
				// the clean style is upright Inter Bold; a row it makes wider than the sprite is set a little smaller
				float advance = new TextLayout(text.isBlank() ? " " : text, clean, context).getAdvance();
				Font fitted = advance > (MAX_WIDTH - 1f) * SCALE ? clean.deriveFont(clean.getSize2D() * (MAX_WIDTH - 1f) * SCALE / advance) : clean;
				letterX = layRow(g, fitted, text, context, baseline);
				rows.add(new Row(text, letterX[text.length()], letterX));
				continue;
			}
			// Rows with characters the lyrics font lacks (Russian, Arabic, Chinese, ...) are laid out as a whole in a font that has them: Arabic
			// letters join and run right to left, and CJK glyphs keep their real width instead of the game's glyph width. Glyph positions then come
			// from that layout.
			Font rowFont = rowFont(sized, text);
			if (rowFont != null) {
				letterX = layRow(g, rowFont, text, context, baseline);
				if (letterX[text.length()] > MAX_WIDTH) {
					throw new IllegalStateException("a row that is too wide");
				}
				rows.add(new Row(text, letterX[text.length()], letterX));
				continue;
			}
			if (letterX[text.length()] > MAX_WIDTH) {
				throw new IllegalStateException("a row that is too wide");
			}
			for (int i = 0; i < text.length(); i++) {
				char letter = text.charAt(i);
				if (Character.isSurrogate(letter)) {
					throw new IllegalStateException("letters that are not one glyph each");
				}
				if (letter != ' ') {
					// fill the outline instead of drawing text: gives every pixel its exact coverage, where text rendering is slightly bolder
					Shape outline = withGlyph(sized, letter).createGlyphVector(context, String.valueOf(letter)).getOutline((PAD + letterX[i]) * SCALE, baseline);
					g.setColor(Color.WHITE);
					g.fill(outline);
					// The game's glyphs are slightly lighter than their outlines; a black stroke along the edge trims a little off every stroke.
					g.setColor(Color.BLACK);
					g.setStroke(new BasicStroke(THIN * SCALE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(outline);
				}
			}
			rows.add(new Row(text, letterX[text.length()], letterX));
		}
		g.dispose();

		byte[] mask = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		int[] letters = new int[TEX_W * textHeight];
		for (int index = 0; index < letters.length; index++) {
			letters[index] = mask[index] & 0xFF;
		}
		// the glow is soft, so it is made at half the size: each of its pixels averages four of the glyphs'
		float[] tight = new float[GLOW_W * GLOW_H];
		for (int y = 0; y < GLOW_H; y++) {
			for (int x = 0; x < GLOW_W; x++) {
				int at = y * 2 * TEX_W + x * 2;
				tight[y * GLOW_W + x] = (letters[at] + letters[at + 1] + letters[at + TEX_W] + letters[at + TEX_W + 1]) / (4f * 255f);
			}
		}
		float[] wide = tight.clone();
		// the glow is baked once here and drawn as part of the sprite, so its quality costs nothing at draw time
		blur(tight, GLOW_W, GLOW_H, Math.round(GLOW_TIGHT * SCALE / 2f));
		blur(wide, GLOW_W, GLOW_H, Math.round(GLOW_WIDE * SCALE / 2f));
		int[] glow = new int[GLOW_W * GLOW_H];
		for (int index = 0; index < glow.length; index++) {
			// combine both lights, boost them slightly and soft-clip the top so it does not saturate into a flat plateau
			float light = tight[index] * 1.7f + wide[index] * 2.6f;
			glow[index] = Math.min(255, Math.round((1f - (float) Math.exp(-light)) * 255f));
		}
		return new Baked(rows, MipTexture.levels(letters, TEX_W, textHeight, MipTexture.levelsFor(TEX_W, TEX_H, MOST_LEVELS)),
			MipTexture.levels(glow, GLOW_W, GLOW_H, MipTexture.levelsFor(GLOW_W, GLOW_H, MOST_LEVELS)));
	}

	/** Box-blurs the values twice in each direction, which approximates a gaussian blur. */
	private static void blur(float[] values, int width, int height, int radius) {
		if (radius < 1) {
			return;
		}
		float[] line = new float[Math.max(width, height)];
		float[] out = new float[line.length];
		for (int pass = 0; pass < 2; pass++) {
			for (int y = 0; y < height; y++) {
				System.arraycopy(values, y * width, line, 0, width);
				boxLine(line, out, width, radius);
				System.arraycopy(out, 0, values, y * width, width);
			}
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					line[y] = values[y * width + x];
				}
				boxLine(line, out, height, radius);
				for (int y = 0; y < height; y++) {
					values[y * width + x] = out[y];
				}
			}
		}
	}

	private static void boxLine(float[] in, float[] out, int length, int radius) {
		float sum = 0f;
		for (int i = 0; i < Math.min(length, radius); i++) {
			sum += in[i];
		}
		float scale = 1f / (2 * radius + 1);
		for (int i = 0; i < length; i++) {
			if (i + radius < length) {
				sum += in[i + radius];
			}
			if (i - radius - 1 >= 0) {
				sum -= in[i - radius - 1];
			}
			out[i] = sum * scale;
		}
	}
}
