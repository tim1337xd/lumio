package dev.tim1337.lumio.render;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * GUI render state of one glass pane: a quad covering the pane plus its shadow and glow. The pane shader ({@code gui_glass}) computes everything
 * else from the pane index, looking up its parameters in the {@link GlassPanes} parameter texture. The quad only carries its position, the pane
 * index (in its texture coordinate) and the fade (in its vertex color alpha).
 */
public record GlassPaneState(float x0, float y0, float x1, float y1, int index, float part, float fade, Matrix3x2f pose, ScreenRectangle scissorArea,
							 ScreenRectangle bounds, TextureSetup textureSetup) implements GuiElementRenderState {
	/** Which part of a pane a quad draws, in its second texture coordinate: all of it, what lies outside of its edge, or the pane itself. */
	static final float WHOLE = 0f;
	static final float OUTSIDE = 1f;
	static final float INSIDE = 2f;

	@Override
	public void buildVertices(VertexConsumer consumer) {
		int alpha = Math.round(Math.clamp(fade, 0f, 1f) * 255f);
		int color = (alpha << 24) | 0xFFFFFF;
		consumer.addVertexWith2DPose(pose, x0, y0).setUv(index, part).setColor(color);
		consumer.addVertexWith2DPose(pose, x0, y1).setUv(index, part).setColor(color);
		consumer.addVertexWith2DPose(pose, x1, y1).setUv(index, part).setColor(color);
		consumer.addVertexWith2DPose(pose, x1, y0).setUv(index, part).setColor(color);
	}

	@Override
	public RenderPipeline pipeline() {
		return GlassPanes.pipeline();
	}
}
