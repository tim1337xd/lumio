package dev.tim1337.lumio.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Glows of the mod's own in the world that must lie over see-through blocks behind them: collected with the rest of the world, they are submitted
 * again after the translucent terrain (glass, slime, ice, water). A glow writes no depth, so the translucent terrain drawn after it would cover it
 * wherever such blocks stand behind; drawn after it, the glow is depth-tested against the scene, so blocks in front still hide it.
 */
public final class LateSubmits {
	/** One glow kept for later: where it was, how it is drawn and what draws it. */
	private static final class Late {
		final PoseStack.Pose pose = new PoseStack.Pose();
		RenderType type;
		SubmitNodeCollector.CustomGeometryRenderer renderer;
		int order;
	}

	private static final List<Late> POOL = new ArrayList<>();
	private static int count;
	private static final int MOST = 1024;
	private static final PoseStack STACK = new PoseStack();

	private LateSubmits() {
	}

	/**
	 * @return whether glows are kept for after the translucent terrain: not with 26.3's improved transparency, which orders see-through things itself
	 *         (and draws them before that point)
	 */
	public static boolean wanted() {
		//#if 26.3
		//$ return !net.minecraft.client.Minecraft.getInstance().options.improvedTransparency().get();
		//#else
		return true;
		//#endif
	}

	/** Keeps a glow for after the translucent terrain, at the pose it would have been drawn with now. */
	public static void add(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
		add(pose, type, renderer, 0);
	}

	/**
	 * Same, in a draw order of its own: what has a higher order is drawn after what has a lower one, whatever their types (the letters on a
	 * tile after the tile). Within one order the game batches by type and sorts by distance, so the order is the only sure way.
	 */
	public static void add(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer, int order) {
		if (count >= MOST) {
			// a frame that never reached the translucent terrain: what it kept is dropped
			count = 0;
		}
		if (count == POOL.size()) {
			POOL.add(new Late());
		}
		Late late = POOL.get(count++);
		late.pose.set(pose.last());
		late.type = type;
		late.order = order;
		late.renderer = renderer;
	}

	/** Submits what was kept, after the translucent terrain; called once a frame. */
	public static void submit(LevelRenderContext context) {
		SubmitNodeCollector collector = context.submitNodeCollector();
		for (int i = 0; i < count; i++) {
			Late late = POOL.get(i);
			STACK.pushPose();
			STACK.last().set(late.pose);
			collector.order(late.order).submitCustomGeometry(STACK, late.type, late.renderer);
			STACK.popPose();
			// the renderer is not kept past its frame
			late.renderer = null;
		}
		count = 0;
	}
}
