package dev.tim1337.lumio.render;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import java.util.List;

/**
 * The glows and shadows of the panes of a frame, in one element that is added before the first of them: everything drawn after it lies over it, so a glow can never cover another
 * element. It is filled while the frame is put together, and its vertices are only asked for when that is done.
 */
public record GlowPassState(List<Quad> quads, ScreenRectangle bounds, TextureSetup textureSetup) implements GuiElementRenderState {
	/** The outside of one pane: as far as its shadow and glow reach. */
	public record Quad(float x0, float y0, float x1, float y1, int index, float fade, Matrix3x2f pose) {
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		for (Quad quad : quads) {
			int color = Math.round(Math.clamp(quad.fade, 0f, 1f) * 255f) << 24 | 0xFFFFFF;
			consumer.addVertexWith2DPose(quad.pose, quad.x0, quad.y0).setUv(quad.index, GlassPaneState.OUTSIDE).setColor(color);
			consumer.addVertexWith2DPose(quad.pose, quad.x0, quad.y1).setUv(quad.index, GlassPaneState.OUTSIDE).setColor(color);
			consumer.addVertexWith2DPose(quad.pose, quad.x1, quad.y1).setUv(quad.index, GlassPaneState.OUTSIDE).setColor(color);
			consumer.addVertexWith2DPose(quad.pose, quad.x1, quad.y0).setUv(quad.index, GlassPaneState.OUTSIDE).setColor(color);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return GlassPanes.pipeline();
	}

	@Override
	public ScreenRectangle scissorArea() {
		return null;
	}
}
