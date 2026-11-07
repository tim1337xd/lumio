package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Logs;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.Mth;

/**
 * Rounded menu shapes as a single quad: a filled box or its outline, with rounded corners on both sides or one, computed by a shader from the
 * distance to the edge. Previously they took up to eight pieces (fills plus a texture per corner), and the game places every GUI element by
 * comparing it with all previous elements of its stratum: eight times fewer pieces is many times less work, and that was the largest part of the
 * frame time with the menu open.
 * <p>
 * Size, radius, outline thickness and the rounded-corner flags travel with every vertex of the quad, in the slots of the entity vertex format
 * (the two integer pairs and the normal), since the shader sees one quad at a time.
 */
public final class RoundedShapes {
	/** The left corners are square. */
	public static final int SQUARE_LEFT = 1;
	/** The right corners are square. */
	public static final int SQUARE_RIGHT = 2;
	/** The outline is dashed: a whole number of dashes per side, one centered on the middle of every side. */
	public static final int DASHED = 4;
	/** The bottom corners are square, as the header band at the top of a card. */
	public static final int SQUARE_BOTTOM = 8;

	private static final RenderPipeline PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Lumio.id("pipeline/gui_rounded"))
			.withVertexShader(Lumio.id("core/gui_rounded"))
			.withFragmentShader(Lumio.id("core/gui_rounded"))
			.withVertexBinding(0, DefaultVertexFormat.ENTITY)
			.build()
	);

	/** 0 unknown, 1 the shader compiled, -1 it did not and shapes are built from pieces as before. */
	private static int state;

	private RoundedShapes() {
	}

	/** @return whether shapes are rendered by the shader; it is compiled on first call, and never retried if that fails */
	public static boolean available() {
		if (state == 0 && ModPipelines.shadersLoaded()) {
			try {
				state = ModPipelines.compiles(PIPELINE) ? 1 : -1;
			} catch (RuntimeException | Error e) {
				state = -1;
			}
			if (state < 0) {
				Logs.warn("The rounded shapes could not be made by the graphics card, they are made of pieces");
			}
		}
		return state > 0;
	}

	/**
	 * Adds a shape in the units of the current pose (screen pixels, as the menu draws its shapes).
	 *
	 * @param thickness 0 for a filled box, otherwise the outline thickness
	 */
	public static void add(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int thickness, int color, int flags) {
		addGradient(graphics, x, y, width, height, radius, thickness, color, color, flags);
	}

	/**
	 * Adds a shape whose color runs from its left edge to its right one (the GPU blends the two across the quad), in the units of the current
	 * pose.
	 */
	public static void addGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int thickness, int leftColor,
								   int rightColor, int flags) {
		int color = leftColor;
		if (width <= 0 || height <= 0 || (color >>> 24) == 0 && (rightColor >>> 24) == 0) {
			return;
		}
		// one extra pixel on every side for the anti-aliased edge
		float x0 = x - 1;
		float y0 = y - 1;
		float x1 = x + width + 1;
		float y1 = y + height + 1;
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle bounds = bounds(graphics.pose(), Mth.floor(x0), Mth.floor(y0), Mth.floor(x0) + Mth.ceil(x1 - x0), Mth.floor(y0) + Mth.ceil(y1 - y0),
			scissor);
		if (bounds == null) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		graphics.guiRenderState.addGuiElement(new Shape(x0, y0, x1, y1, x + width / 2f, y + height / 2f, width, height, r, Math.max(0, thickness), flags, color, rightColor, pose, scissor,
			bounds));
	}

	/** A corner of a shape in screen coordinates, reused: shapes are only added on the render thread. */
	private static final Vector2f CORNER = new Vector2f();

	/**
	 * @return the screen bounds of a box in the pose, cut to the scissor, as one rectangle; null when nothing of it is left. The same as the game's
	 *         transformMaxBounds and intersection, without the rectangles they make on the way.
	 */
	private static ScreenRectangle bounds(Matrix3x2fc pose, int left, int top, int right, int bottom, ScreenRectangle scissor) {
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int corner = 0; corner < 4; corner++) {
			pose.transformPosition((corner & 1) == 0 ? left : right, (corner & 2) == 0 ? top : bottom, CORNER);
			minX = Math.min(minX, CORNER.x);
			minY = Math.min(minY, CORNER.y);
			maxX = Math.max(maxX, CORNER.x);
			maxY = Math.max(maxY, CORNER.y);
		}
		// rounded exactly as the game does: bounds a pixel larger overlap their neighbors, which splits the GUI into more draw passes
		int x0 = Mth.floor(minX);
		int y0 = Mth.floor(minY);
		int x1 = x0 + Mth.ceil(maxX - minX);
		int y1 = y0 + Mth.ceil(maxY - minY);
		if (scissor != null) {
			x0 = Math.max(x0, scissor.left());
			y0 = Math.max(y0, scissor.top());
			x1 = Math.min(x1, scissor.right());
			y1 = Math.min(y1, scissor.bottom());
		}
		return x1 > x0 && y1 > y0 ? new ScreenRectangle(x0, y0, x1 - x0, y1 - y0) : null;
	}

	private record Shape(float x0, float y0, float x1, float y1, float centreX, float centreY, int width, int height, int radius, int thickness, int flags,
						 int color, int rightColor, Matrix3x2f pose, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			corner(consumer, x0, y0);
			corner(consumer, x0, y1);
			corner(consumer, x1, y1);
			corner(consumer, x1, y0);
		}

		private void corner(VertexConsumer consumer, float x, float y) {
			// the left corners take the left color, the right ones the right color
			consumer.addVertexWith2DPose(pose, x, y).setColor(x == x0 ? color : rightColor).setUv(x - centreX, y - centreY)
				.setUv1(width, height).setUv2(radius, thickness | flags << 10).setNormal(0f, 0f, 1f);
		}

		@Override
		public RenderPipeline pipeline() {
			return PIPELINE;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}
}
