package dev.tim1337.lumio.render;

import dev.tim1337.lumio.features.visual.world.BlockOverlayLook;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Draws the box of the block overlay: its edges in layers of light, its faces and how they fill while the block breaks. The numbers of what moves are in {@link BlockOverlayLook}. */
public final class BlockOverlayDraw {
	/** What the box is to look like. */
	public record Look(BlockOverlayLook.Style style, BlockOverlayLook.Colors colors, int rgb, float width, float fill, float speed, float seconds, float alpha) {
		int colorAt(float place) {
			return BlockOverlayLook.color(colors, rgb, place, seconds, speed);
		}
	}

	private static final float EDGE_STEP = 0.125f;
	private static final float GROW = 0.002f;
	/** The layers of light around an edge: how much wider and how bright. */
	private static final float[][] GLOW_LAYERS = {{6f, 0.04f}, {4f, 0.07f}, {2.4f, 0.14f}, {1.5f, 0.28f}};
	/**
	 * The Shader style's outline: one crisp line this wide on a 1080 line screen, as wide on every edge, and how opaque it is (more than the fill).
	 * Wide faint lines along the edges are left out: lines write depth, and a wide one cut a strip out of the fill along every edge.
	 */
	private static final float SHADER_LINE_PIXELS = 1.5f;
	private static final float SHADER_LINE_ALPHA = 0.9f;
	private static final float REFERENCE_LINES = 1080f;
	/** The one layer of that line, its width set each frame (the screen's size and the width setting can change). */
	private static final float[][] SHADER_LINE = {{1f, SHADER_LINE_ALPHA}};
	private static final float SHADER_FILL_LIGHT = 1.6f;
	private static final float BREAKING_ALPHA = 0.38f;
	private static final float PULSE_FILL = 0.3f;

	private BlockOverlayDraw() {
	}

	private static int argb(int rgb, float alpha) {
		return Math.round(Math.clamp(alpha, 0f, 1f) * 255f) << 24 | rgb & 0xFFFFFF;
	}

	/** Moves to a block and draws what is given there. */
	private static void at(LevelRenderContext context, BlockPos pos, Runnable draw) {
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		try {
			draw.run();
		} finally {
			pose.popPose();
		}
	}

	/** Draws the box of a block. */
	public static void box(LevelRenderContext context, BlockPos pos, VoxelShape shape, Look look) {
		if (shape == null || shape.isEmpty() || look.alpha() <= 0.004f) {
			return;
		}
		at(context, pos, () -> {
			switch (look.style()) {
				case OUTLINE -> {
					if (look.fill() > 0f) {
						faces(context, shape, argb(look.colorAt(0f), look.fill() * look.alpha()));
					}
					context.submitNodeCollector().submitShapeOutline(context.poseStack(), shape, RenderTypes.lines(), argb(look.colorAt(0f), look.alpha()), look.width(), false);
				}
				case GLOW -> {
					if (look.fill() > 0f) {
						faces(context, shape, argb(look.colorAt(0f), look.fill() * look.alpha()));
					}
					edges(context, shape, look, GLOW_LAYERS);
					edges(context, shape, look, new float[][]{{1f, 1f}});
				}
				case SHADER -> {
					// colors flow over the block with a slight breath, and a crisp line in the same colors runs along every edge
					float breath = BlockOverlayLook.breath(look.seconds(), look.speed());
					flowingFaces(context, shape, look, GROW, look.fill() * SHADER_FILL_LIGHT * breath * look.alpha());
					float line = SHADER_LINE_PIXELS * Minecraft.getInstance().getWindow().getHeight() / REFERENCE_LINES / Math.max(0.01f, look.width());
					SHADER_LINE[0][0] = line;
					edges(context, shape, look, SHADER_LINE);
				}
			}
		});
	}

	private static void faces(LevelRenderContext context, VoxelShape shape, int argb) {
		List<AABB> boxes = shape.toAabbs();
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (last, consumer) -> {
			for (AABB box : boxes) {
				BlockHighlight.quadsOf(last, consumer, box, GROW, boxes, argb, null);
			}
		});
	}

	/** The edges in short pieces, each in the color of its place, so that colors flow along them and around the corners. */
	private static void edges(LevelRenderContext context, VoxelShape shape, Look look, float[][] layers) {
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (last, consumer) -> {
			for (float[] layer : layers) {
				shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> edge(last, consumer, (float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1, look, layer[0] * look.width(), layer[1]));
			}
		});
	}

	private static void edge(PoseStack.Pose last, VertexConsumer consumer, float x0, float y0, float z0, float x1, float y1, float z1, Look look, float width, float strength) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dz = z1 - z0;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length <= 0f) {
			return;
		}
		int pieces = Math.max(1, Math.round(length / EDGE_STEP));
		float nx = dx / length;
		float ny = dy / length;
		float nz = dz / length;
		for (int i = 0; i < pieces; i++) {
			edgeEnd(last, consumer, x0 + dx * i / pieces, y0 + dy * i / pieces, z0 + dz * i / pieces, nx, ny, nz, look, width, strength);
			edgeEnd(last, consumer, x0 + dx * (i + 1) / pieces, y0 + dy * (i + 1) / pieces, z0 + dz * (i + 1) / pieces, nx, ny, nz, look, width, strength);
		}
	}

	private static void edgeEnd(PoseStack.Pose last, VertexConsumer consumer, float x, float y, float z, float nx, float ny, float nz, Look look, float width, float strength) {
		float place = BlockOverlayLook.place(x, y, z);
		int rgb = look.style() == BlockOverlayLook.Style.SHADER ? BlockOverlayLook.shaderColor(look.colors(), look.rgb(), place, look.seconds(), look.speed()) : look.colorAt(place);
		consumer.addVertex(last, x, y, z).setColor(argb(rgb, strength * look.alpha())).setNormal(last, nx, ny, nz).setLineWidth(width);
	}

	/** The faces in the colors of the Shader style, which run along the block from corner to corner. */
	private static void flowingFaces(LevelRenderContext context, VoxelShape shape, Look look, double grow, float alpha) {
		List<AABB> boxes = shape.toAabbs();
		BlockHighlight.Shade shade = place -> argb(BlockOverlayLook.shaderColor(look.colors(), look.rgb(), place, look.seconds(), look.speed()), alpha);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (last, consumer) -> {
			for (AABB box : boxes) {
				BlockHighlight.quadsOf(last, consumer, box, grow, boxes, 0, shade);
			}
		});
	}

	/** The faces filling from the bottom up as the block breaks. */
	public static void breaking(LevelRenderContext context, BlockPos pos, VoxelShape shape, int rgb, float progress, float alpha) {
		if (shape == null || shape.isEmpty() || progress <= 0f || alpha <= 0.004f) {
			return;
		}
		AABB bounds = shape.bounds();
		double top = bounds.minY + (bounds.maxY - bounds.minY) * progress;
		int argb = argb(rgb, BREAKING_ALPHA * alpha);
		at(context, pos, () -> context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (last, consumer) -> {
			for (AABB box : shape.toAabbs()) {
				if (box.minY < top) {
					BlockHighlight.quadsOf(last, consumer, new AABB(box.minX, box.minY, box.minZ, box.maxX, Math.min(box.maxY, top), box.maxZ).inflate(GROW * 2f), argb, null);
				}
			}
		}));
	}

	/** The pulse of a block that has just broken: its box grows and fades. */
	public static void pulse(LevelRenderContext context, BlockPos pos, VoxelShape shape, int rgb, float width, long ageMillis) {
		float alpha = BlockOverlayLook.pulseAlpha(ageMillis);
		if (shape == null || shape.isEmpty() || alpha <= 0.004f) {
			return;
		}
		double grow = BlockOverlayLook.pulseGrow(ageMillis);
		VoxelShape bigger = net.minecraft.world.phys.shapes.Shapes.create(shape.bounds().inflate(grow));
		at(context, pos, () -> {
			faces(context, bigger, argb(rgb, PULSE_FILL * alpha));
			context.submitNodeCollector().submitShapeOutline(context.poseStack(), bigger, RenderTypes.lines(), argb(rgb, alpha), width, false);
		});
	}
}
