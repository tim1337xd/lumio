package dev.tim1337.lumio.api.experimental.draw;

import dev.tim1337.lumio.api.spi.Backend;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Drawing in the world in the block overlay's look: block highlights, and the style and colors of the block overlay as the player set it. */
public final class WorldDraw {
	private WorldDraw() {
	}

	/** The color of a highlight at a place along it, from 0 to 1, for a moving gradient. */
	@FunctionalInterface
	public interface Shade {
		int at(float place);
	}

	/**
	 * Draws a block's shape at its position in a style.
	 *
	 * @param rgb         the color of every style but the gradient, which uses the menu's glow colors
	 * @param fillOpacity the opacity of the fill; the glow and the gradient derive their layers from it
	 * @param speed       the speed of the gradient, 1 being the default
	 */
	public static void highlight(LevelRenderContext context, BlockPos pos, VoxelShape shape, BlockStyle style, int rgb, float lineWidth,
		float fillOpacity, float speed) {
		Backend.get().world().highlight(context, pos, shape, style, rgb, lineWidth, fillOpacity, speed);
	}

	/** Adds the faces of a box as quads, in one color or (with a shade) a gradient along the box. */
	public static void quads(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int argb, Shade shade) {
		Backend.get().world().quads(pose, consumer, box, argb, shade);
	}

	/** @return whether the block overlay is on */
	public static boolean blockOverlayOn() {
		return Backend.get().world().blockOverlayOn();
	}

	/** @return the block overlay's style for other highlights to adopt: the one set, or the fill while the overlay is off */
	public static BlockStyle blockOverlayStyle() {
		return Backend.get().world().blockOverlayStyle();
	}

	/** @return the block overlay's line width, as the player set it */
	public static float blockOverlayLineWidth() {
		return Backend.get().world().blockOverlayLineWidth();
	}

	/** @return the block overlay's gradient color at a place along a block, from 0 to 1 */
	public static int blockOverlayShade(float place, float alpha) {
		return Backend.get().world().blockOverlayShade(place, alpha);
	}

	/** What the main mod provides for {@link WorldDraw}. */
	public interface Provider {
		void highlight(LevelRenderContext context, BlockPos pos, VoxelShape shape, BlockStyle style, int rgb, float lineWidth, float fillOpacity,
			float speed);

		void quads(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int argb, Shade shade);

		boolean blockOverlayOn();

		BlockStyle blockOverlayStyle();

		int blockOverlayShade(float place, float alpha);

		float blockOverlayLineWidth();
	}
}
