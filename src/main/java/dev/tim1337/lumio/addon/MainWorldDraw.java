package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.BlockStyle;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.render.BlockHighlight;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The add-on API's world drawing, by the main mod's block highlights and block overlay. */
final class MainWorldDraw implements WorldDraw.Provider {
	static final MainWorldDraw INSTANCE = new MainWorldDraw();

	private MainWorldDraw() {
	}

	@Override
	public void highlight(LevelRenderContext context, BlockPos pos, VoxelShape shape, BlockStyle style, int rgb, float lineWidth, float fillOpacity,
		float speed) {
		BlockHighlight.draw(context, pos, shape, style, rgb, lineWidth, fillOpacity, speed);
	}

	@Override
	public void quads(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int argb, WorldDraw.Shade shade) {
		BlockHighlight.quadsOf(pose, consumer, box, argb, shade == null ? null : shade::at);
	}

	@Override
	public boolean blockOverlayOn() {
		return ModFeatures.BLOCK_OVERLAY != null && ModFeatures.BLOCK_OVERLAY.isEnabled();
	}

	@Override
	public BlockStyle blockOverlayStyle() {
		return ModFeatures.BLOCK_OVERLAY == null ? BlockStyle.FILL : ModFeatures.BLOCK_OVERLAY.lookForOthers();
	}

	/** The line width before the main mod's features exist. */
	private static final float DEFAULT_LINE_WIDTH = 2f;

	@Override
	public float blockOverlayLineWidth() {
		return ModFeatures.BLOCK_OVERLAY == null ? DEFAULT_LINE_WIDTH : ModFeatures.BLOCK_OVERLAY.lineWidth();
	}

	@Override
	public int blockOverlayShade(float place, float alpha) {
		return ModFeatures.BLOCK_OVERLAY.shaderColor(place, alpha);
	}
}
