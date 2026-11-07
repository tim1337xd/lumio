package dev.tim1337.lumio.render;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
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
 * A textured quad with its own color at every corner, interpolated across it. The panel glow is built from such pieces: with one color per
 * piece, the colors changed in steps from piece to piece, which showed as seams.
 */
public final class GradientBlit {
	private GradientBlit() {
	}

	/**
	 * @param topLeft color at the top-left corner; the other corners follow clockwise
	 */
	public static void add(GuiGraphicsExtractor graphics, Identifier texture, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
						   int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (x1 <= x0 || y1 <= y0 || ((topLeft | topRight | bottomRight | bottomLeft) >>> 24) == 0) {
			return;
		}
		AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle own = new ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1 - x0), Mth.ceil(y1 - y0)).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(own) : own;
		if (bounds == null) {
			return;
		}
		graphics.guiRenderState.addGuiElement(new Piece(TextureSetup.singleTexture(loaded.getTextureView(), loaded.getSampler()), pose, x0, y0, x1, y1, u0, v0, u1, v1,
			topLeft, topRight, bottomRight, bottomLeft, scissor, bounds));
	}

	/** An untextured rectangle with its own color at every corner, interpolated across it; panel edge lines are built from these. */
	public static void fill(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (x1 <= x0 || y1 <= y0 || ((topLeft | topRight | bottomRight | bottomLeft) >>> 24) == 0) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle own = new ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Math.max(1, Mth.ceil(x1 - x0)), Math.max(1, Mth.ceil(y1 - y0))).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(own) : own;
		if (bounds == null) {
			return;
		}
		graphics.guiRenderState.addGuiElement(new Plain(pose, x0, y0, x1, y1, topLeft, topRight, bottomRight, bottomLeft, scissor, bounds));
	}

	private record Plain(Matrix3x2f pose, float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomRight, int bottomLeft,
						 ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			consumer.addVertexWith2DPose(pose, x0, y0).setColor(topLeft);
			consumer.addVertexWith2DPose(pose, x0, y1).setColor(bottomLeft);
			consumer.addVertexWith2DPose(pose, x1, y1).setColor(bottomRight);
			consumer.addVertexWith2DPose(pose, x1, y0).setColor(topRight);
		}

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}

	private record Piece(TextureSetup textureSetup, Matrix3x2f pose, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
						 int topLeft, int topRight, int bottomRight, int bottomLeft, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(topLeft);
			consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(bottomLeft);
			consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(bottomRight);
			consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(topRight);
		}

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI_TEXTURED;
		}
	}
}
