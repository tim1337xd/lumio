package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.Mth;

/**
 * A ring segment as a single shader quad: smooth at any size, with round caps, from one angle to another (in turns, 0 at the top, clockwise).
 * Chart rings used to be built from a hundred small dots, which looked beaded and did not close cleanly at their start.
 * <p>
 * Both angles are packed into one value for the shader (the line width slot, a full float): each in steps of 1/2048 of a turn.
 */
public final class Arcs {
	private static final VertexFormat FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("UV1", GpuFormat.RG16_SINT)
		.addAttribute("LineWidth", GpuFormat.R32_FLOAT)
		.build();

	private static final RenderPipeline PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Lumio.id("pipeline/gui_arc"))
			.withVertexShader(Lumio.id("core/gui_arc"))
			.withFragmentShader(Lumio.id("core/gui_arc"))
			.withVertexBinding(0, FORMAT)
			.build()
	);

	private static int state;

	private Arcs() {
	}

	/** @return whether arcs are rendered by the shader; if the GPU rejects it, charts are drawn from dots as before */
	public static boolean available() {
		if (state == 0 && ModPipelines.shadersLoaded()) {
			try {
				state = ModPipelines.compiles(PIPELINE) ? 1 : -1;
			} catch (RuntimeException | Error e) {
				state = -1;
			}
		}
		return state > 0;
	}

	/**
	 * Adds a ring segment in the units of the current pose.
	 *
	 * @param radius    outer radius
	 * @param thickness ring thickness
	 * @param from      start, in turns from the top, clockwise
	 * @param to        end; one full turn or more draws the whole ring
	 */
	public static void add(GuiGraphicsExtractor graphics, float centreX, float centreY, float radius, float thickness, float from, float to, int color) {
		add(graphics, centreX, centreY, radius, thickness, from, to, color, false);
	}

	/**
	 * A chart segment: flat ends, with a gap to the next segment that has the same width at the inner and outer edge of the ring, left out by the
	 * shader. Segments are passed touching, from where one ends to where the next begins.
	 */
	public static void addSegment(GuiGraphicsExtractor graphics, float centreX, float centreY, float radius, float thickness, float from, float to, int color) {
		add(graphics, centreX, centreY, radius, thickness, from, to, color, true);
	}

	private static void add(GuiGraphicsExtractor graphics, float centreX, float centreY, float radius, float thickness, float from, float to, int color, boolean segment) {
		if ((color >>> 24) == 0 || to - from <= 0.0005f) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		float x0 = centreX - radius - 1;
		float y0 = centreY - radius - 1;
		float x1 = centreX + radius + 1;
		float y1 = centreY + radius + 1;
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle own = new ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1 - x0), Mth.ceil(y1 - y0)).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(own) : own;
		if (bounds == null) {
			return;
		}
		boolean whole = to - from >= 0.9995f;
		int a = whole ? 0 : Math.floorMod(Math.round(from * 2048f), 2048);
		int span = whole ? 2048 : Mth.clamp(Math.round((to - from) * 2048f), 1, 2047);
		float packed = a * 4096f + span;
		// radius and thickness in hundredths, as integers
		int r = Math.round(radius * 100f);
		int t = Math.round(thickness * 100f) * (segment ? -1 : 1);
		graphics.guiRenderState.addGuiElement(new Arc(x0, y0, x1, y1, centreX, centreY, r, t, packed, color, pose, scissor, bounds));
	}

	private record Arc(float x0, float y0, float x1, float y1, float centreX, float centreY, int radius, int thickness, float angles, int color,
					   Matrix3x2f pose, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			corner(consumer, x0, y0);
			corner(consumer, x0, y1);
			corner(consumer, x1, y1);
			corner(consumer, x1, y0);
		}

		private void corner(VertexConsumer consumer, float x, float y) {
			consumer.addVertexWith2DPose(pose, x, y).setColor(color).setUv(x - centreX, y - centreY).setUv1(radius, thickness).setLineWidth(angles);
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
