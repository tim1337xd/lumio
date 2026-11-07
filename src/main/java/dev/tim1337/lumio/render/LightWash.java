package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Logs;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A large, very soft wash of light (the colored light of the menu) as one quad whose falloff a shader computes for every screen pixel, with a
 * faint dither against the steps of an 8-bit screen. A stretched texture tinted down to a few percent left only a few alpha steps across the
 * window, which showed as bands, and its pixels were blown up into blocks.
 */
public final class LightWash {
	private static final RenderPipeline PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Lumio.id("pipeline/gui_wash"))
			.withVertexShader(Lumio.id("core/gui_wash"))
			.withFragmentShader(Lumio.id("core/gui_wash"))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.build()
	);

	/** 0 unknown, 1 the shader compiled, -1 it did not and the wash is a texture as before. */
	private static int state;

	private LightWash() {
	}

	/** @return whether the wash is rendered by the shader; it is compiled on first call, and never retried if that fails */
	public static boolean available() {
		if (state == 0 && ModPipelines.shadersLoaded()) {
			try {
				state = ModPipelines.compiles(PIPELINE) ? 1 : -1;
			} catch (RuntimeException | Error e) {
				state = -1;
			}
			if (state < 0) {
				Logs.warn("The light wash could not be made by the graphics card, it is drawn from a texture");
			}
		}
		return state > 0;
	}

	/** Adds an ellipse of light around a point that fades to nothing at its radii, in the units of the current pose. */
	public static void add(GuiGraphicsExtractor graphics, float centerX, float centerY, float radiusX, float radiusY, int color) {
		if (radiusX <= 0.5f || radiusY <= 0.5f || (color >>> 24) == 0) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		float x0 = centerX - radiusX;
		float y0 = centerY - radiusY;
		float x1 = centerX + radiusX;
		float y1 = centerY + radiusY;
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle own = new ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1) - Mth.floor(x0), Mth.ceil(y1) - Mth.floor(y0)).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(own) : own;
		if (bounds == null) {
			return;
		}
		graphics.guiRenderState.addGuiElement(new Wash(x0, y0, x1, y1, color, pose, scissor, bounds));
	}

	/**
	 * Adds the same light, cut to rows from {@link RoundedRows#edges}, in one draw and with no scissor.
	 *
	 * @param texture the wash's texture where the shader could not be made, or null to use the shader
	 */
	public static void addRows(GuiGraphicsExtractor graphics, float centerX, float centerY, float radiusX, float radiusY, int color, float[] edges, Identifier texture) {
		if (radiusX <= 0.5f || radiusY <= 0.5f || (color >>> 24) == 0 || edges.length < 2 * RoundedRows.STRIDE) {
			return;
		}
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		for (int i = 0; i < edges.length; i += RoundedRows.STRIDE) {
			minX = Math.min(minX, edges[i + 1]);
			maxX = Math.max(maxX, edges[i + 2]);
		}
		float minY = edges[0];
		float maxY = edges[edges.length - RoundedRows.STRIDE];
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle own = new ScreenRectangle(Mth.floor(minX), Mth.floor(minY), Mth.ceil(maxX) - Mth.floor(minX), Mth.ceil(maxY) - Mth.floor(minY)).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(own) : own;
		if (bounds == null) {
			return;
		}
		TextureSetup setup = TextureSetup.noTexture();
		if (texture != null) {
			AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
			setup = TextureSetup.singleTexture(loaded.getTextureView(), loaded.getSampler());
		}
		graphics.guiRenderState.addGuiElement(new Rows(centerX, centerY, radiusX, radiusY, edges, color, texture != null, setup, pose, scissor, bounds));
	}

	private record Rows(float centerX, float centerY, float radiusX, float radiusY, float[] edges, int color, boolean textured, TextureSetup textureSetup, Matrix3x2f pose,
						ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			for (int i = 0; i + RoundedRows.STRIDE < edges.length; i += RoundedRows.STRIDE) {
				float top = edges[i];
				float bottom = edges[i + RoundedRows.STRIDE];
				vertex(consumer, edges[i + 1], top);
				vertex(consumer, edges[i + RoundedRows.STRIDE + 1], bottom);
				vertex(consumer, edges[i + RoundedRows.STRIDE + 2], bottom);
				vertex(consumer, edges[i + 2], top);
			}
		}

		private void vertex(VertexConsumer consumer, float x, float y) {
			float u = (x - centerX) / radiusX;
			float v = (y - centerY) / radiusY;
			// the shader takes the place in the ellipse from -1 to 1, the texture holds that ellipse from 0 to 1
			consumer.addVertexWith2DPose(pose, x, y).setUv(textured ? (u + 1f) / 2f : u, textured ? (v + 1f) / 2f : v).setColor(color);
		}

		@Override
		public RenderPipeline pipeline() {
			return textured ? RenderPipelines.GUI_TEXTURED : PIPELINE;
		}
	}

	private record Wash(float x0, float y0, float x1, float y1, int color, Matrix3x2f pose, ScreenRectangle scissorArea, ScreenRectangle bounds)
		implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			consumer.addVertexWith2DPose(pose, x0, y0).setUv(-1f, -1f).setColor(color);
			consumer.addVertexWith2DPose(pose, x0, y1).setUv(-1f, 1f).setColor(color);
			consumer.addVertexWith2DPose(pose, x1, y1).setUv(1f, 1f).setColor(color);
			consumer.addVertexWith2DPose(pose, x1, y0).setUv(1f, -1f).setColor(color);
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
