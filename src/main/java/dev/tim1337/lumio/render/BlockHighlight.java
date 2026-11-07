package dev.tim1337.lumio.render;

import dev.tim1337.lumio.api.experimental.draw.BlockStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Draws a block in one of the block overlay styles: an outline, a fill, a soft glow, or an animated gradient in the menu glow colors. Shared by
 * the block overlay, the etherwarp target and the mining helpers, so a highlighted block looks the same everywhere.
 */
public final class BlockHighlight {
	/** Color cycles per second of the animated gradient at speed 1. */
	private static final double GRADIENT_CYCLES_PER_SECOND = 0.35;
	/** The direction out of each face of a box, in the order its faces are drawn: bottom, top, north, east, south, west. */
	private static final int[][] FACE_NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
	/** How far out of a face another box is looked for. */
	private static final double TOUCH = 1.0e-4;

	private BlockHighlight() {
	}

	/** Color at a position along the block, from 0 at its lowest corner to 1 at its highest. */
	@FunctionalInterface
	public interface Shade {
		int at(float place);
	}

	/**
	 * Draws a block's shape at its position.
	 *
	 * @param rgb         the color of every style but the gradient, which uses the menu glow colors (or shades of this color without them)
	 * @param fillOpacity opacity of the fill; the glow and the gradient derive their layers from it
	 * @param speed       speed of the gradient animation, 1 being the default
	 */
	public static void draw(LevelRenderContext context, BlockPos pos, VoxelShape shape, BlockStyle mode, int rgb, float lineWidth, float fillOpacity,
		float speed) {
		if (shape == null || shape.isEmpty()) {
			return;
		}
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
		try {
			switch (mode) {
				case OUTLINE -> outline(context, pose, shape, argb(rgb, 0.95f), lineWidth);
				case FILL -> fill(context, pose, shape, 0.002, argb(rgb, fillOpacity), null);
				case GLOW -> {
					// a soft light around the block in growing, fading layers, plus a bright edge
					fill(context, pose, shape, 0.002, argb(rgb, fillOpacity * 0.7f), null);
					fill(context, pose, shape, 0.02, argb(rgb, fillOpacity * 0.32f), null);
					fill(context, pose, shape, 0.045, argb(rgb, fillOpacity * 0.16f), null);
					fill(context, pose, shape, 0.075, argb(rgb, fillOpacity * 0.07f), null);
					outline(context, pose, shape, Ui.mix(argb(rgb, 1f), 0xFFFFFFFF, 0.35f), lineWidth);
				}
				case SHADER -> {
					// colors flow over the block, with a slight pulse
					float breath = 0.75f + 0.25f * (float) Math.sin(phase(speed) * Math.PI * 2.0 * 1.3);
					fill(context, pose, shape, 0.002, 0, place -> gradient(place, fillOpacity * breath, rgb, speed));
					outline(context, pose, shape, gradient(0.5f, 0.95f, rgb, speed), lineWidth);
				}
			}
		} finally {
			pose.popPose();
		}
	}

	/**
	 * @return the animated gradient color at a position along the block, from 0 to 1: the menu glow colors (or the given color with a lighter and a
	 *         deeper shade), flowing along the block and through each other
	 */
	public static int gradient(float place, float alpha, int rgb, float speed) {
		int[] table = UiGlow.table();
		float at = place * 0.6f + phase(speed);
		int shown;
		if (table != null) {
			int i = Math.floorMod((int) Math.floor(at * 64f), 64);
			shown = table[i] & 0xFFFFFF;
		} else {
			float wave = 0.5f + 0.5f * (float) Math.sin(at * Math.PI * 2.0);
			shown = Ui.mix(0xFF000000 | rgb, 0xFFFFFFFF, 0.45f * wave) & 0xFFFFFF;
		}
		return argb(shown, alpha);
	}

	/** @return the animated gradient's time, in color cycles */
	private static float phase(float speed) {
		return (float) (System.nanoTime() / 1_000_000_000.0 * GRADIENT_CYCLES_PER_SECOND * speed % 1000.0);
	}

	private static int argb(int rgb, float alpha) {
		return (Math.round(Mth.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
	}

	private static void outline(LevelRenderContext context, PoseStack pose, VoxelShape shape, int argb, float lineWidth) {
		context.submitNodeCollector().submitShapeOutline(pose, shape, RenderTypes.lines(), argb, lineWidth, false);
	}

	/** Fills the boxes of a shape: their outer faces, slightly enlarged so they are not hidden inside the block; faces where two boxes meet are left out. */
	private static void fill(LevelRenderContext context, PoseStack pose, VoxelShape shape, double grow, int argb, Shade shade) {
		List<AABB> boxes = shape.toAabbs();
		RenderType type = RenderTypes.debugQuads();
		context.submitNodeCollector().submitCustomGeometry(pose, type, (last, consumer) -> {
			for (AABB box : boxes) {
				quadsOf(last, consumer, box, grow, boxes, argb, shade);
			}
		});
	}

	/**
	 * Draws the faces of one box of a shape that are on the outside: a face against another box of the shape is left out, so the fill of stairs or a
	 * fence has no seams inside.
	 *
	 * @param grow  how far the faces lie out of the box, so that they are not hidden inside the block
	 * @param boxes all boxes of the shape, the box among them
	 */
	public static void quadsOf(PoseStack.Pose last, VertexConsumer consumer, AABB box, double grow, List<AABB> boxes, int argb, Shade shade) {
		int hidden = 0;
		for (int face = 0; face < FACE_NORMALS.length; face++) {
			if (againstAnother(box, face, boxes)) {
				hidden |= 1 << face;
			}
		}
		quadsOf(last, consumer, box.inflate(grow), argb, shade, hidden);
	}

	/** @return whether the middle of a face of a box touches another box of the shape, just outside the face */
	static boolean againstAnother(AABB box, int face, List<AABB> boxes) {
		int[] normal = FACE_NORMALS[face];
		double x = normal[0] == 0 ? (box.minX + box.maxX) / 2 : normal[0] < 0 ? box.minX - TOUCH : box.maxX + TOUCH;
		double y = normal[1] == 0 ? (box.minY + box.maxY) / 2 : normal[1] < 0 ? box.minY - TOUCH : box.maxY + TOUCH;
		double z = normal[2] == 0 ? (box.minZ + box.maxZ) / 2 : normal[2] < 0 ? box.minZ - TOUCH : box.maxZ + TOUCH;
		for (AABB other : boxes) {
			if (other != box && x >= other.minX && x <= other.maxX && y >= other.minY && y <= other.maxY && z >= other.minZ && z <= other.maxZ) {
				return true;
			}
		}
		return false;
	}

	public static void quadsOf(PoseStack.Pose last, VertexConsumer consumer, AABB box, int argb, Shade shade) {
		quadsOf(last, consumer, box, argb, shade, 0);
	}

	/** Draws the faces of a box, but those whose bit (in the order of {@link #FACE_NORMALS}) is set in {@code hidden}. */
	private static void quadsOf(PoseStack.Pose last, VertexConsumer consumer, AABB box, int argb, Shade shade, int hidden) {
		float x0 = (float) box.minX;
		float y0 = (float) box.minY;
		float z0 = (float) box.minZ;
		float x1 = (float) box.maxX;
		float y1 = (float) box.maxY;
		float z1 = (float) box.maxZ;
		// corner colors: along the block diagonal for the gradient, otherwise uniform
		float[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
		int[] colors = new int[8];
		for (int i = 0; i < 8; i++) {
			colors[i] = shade == null ? argb : shade.at((corners[i][0] + corners[i][1] + corners[i][2]) / 3f);
		}
		int[][] faces = {{0, 1, 2, 3}, {7, 6, 5, 4}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};
		for (int index = 0; index < faces.length; index++) {
			if ((hidden & 1 << index) != 0) {
				continue;
			}
			int[] face = faces[index];
			for (int corner : face) {
				consumer.addVertex(last, corners[corner][0], corners[corner][1], corners[corner][2]).setColor(colors[corner]);
			}
		}
	}
}
