package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.Mth;

/**
 * An image with rounded corners in the menus, such as the season pictures and the world and server icons: one textured quad whose corners a
 * shader cuts by the distance to the edge, smooth at any size. The game can only draw square images.
 * <p>
 * Size and radius travel with every vertex in the slots of the entity vertex format, and the corner a vertex belongs to in its normal.
 */
public final class RoundedImage {
	/** Radius precision: it is sent as a whole number of sixteenths. */
	private static final float RADIUS_STEPS = 16f;

	private static final RenderPipeline PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Lumio.id("pipeline/gui_rounded_image"))
			.withVertexShader(Lumio.id("core/gui_rounded_image"))
			.withFragmentShader(Lumio.id("core/gui_rounded_image"))
			.withVertexBinding(0, DefaultVertexFormat.ENTITY)
			.build()
	);

	private RoundedImage() {
	}

	/** Draws a whole texture with rounded corners, filtered, tinted with a color, in the units of the current pose. */
	public static void draw(GuiGraphicsExtractor graphics, GpuTextureView texture, int x, int y, int width, int height, float radius, int color) {
		draw(graphics, texture, x, y, width, height, radius, 0f, 0f, 1f, 1f, color);
	}

	/** Draws a part of a texture, from u0, v0 to u1, v1, with rounded corners. */
	public static void draw(GuiGraphicsExtractor graphics, GpuTextureView texture, int x, int y, int width, int height, float radius,
							float u0, float v0, float u1, float v1, int color) {
		if (texture == null || width <= 0 || height <= 0 || (color >>> 24) == 0) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle own = new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(own) : own;
		if (bounds == null) {
			return;
		}
		int r = Math.round(Mth.clamp(radius, 0f, Math.min(width, height) / 2f) * RADIUS_STEPS);
		if (!ModPipelines.usable(PIPELINE)) {
			// the picture without its rounded corners
			graphics.blit(texture, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + width, y + height, u0, u1, v0, v1);
			return;
		}
		TextureSetup setup = TextureSetup.singleTexture(texture, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		graphics.guiRenderState.addGuiElement(new Image(setup, pose, x, y, width, height, r, u0, v0, u1, v1, color, scissor, bounds));
	}

	private record Image(TextureSetup textureSetup, Matrix3x2f pose, int x, int y, int width, int height, int radius, float u0, float v0, float u1,
						 float v1, int color, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			corner(consumer, x, y, u0, v0, -1f, -1f);
			corner(consumer, x, y + height, u0, v1, -1f, 1f);
			corner(consumer, x + width, y + height, u1, v1, 1f, 1f);
			corner(consumer, x + width, y, u1, v0, 1f, -1f);
		}

		private void corner(VertexConsumer consumer, float px, float py, float u, float v, float sideX, float sideY) {
			consumer.addVertexWith2DPose(pose, px, py).setColor(color).setUv(u, v).setUv1(width, height).setUv2(radius, 0).setNormal(sideX, sideY, 0f);
		}

		@Override
		public RenderPipeline pipeline() {
			return PIPELINE;
		}
	}
}
