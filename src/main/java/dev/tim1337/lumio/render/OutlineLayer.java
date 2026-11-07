package dev.tim1337.lumio.render;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.util.function.Consumer;

/** Implemented (via mixin) by the layers of an item render state, which know the item's quads. */
public interface OutlineLayer {
	/** Submits the glow around the layer. */
	void lumio$submitGlow(PoseStack pose, SubmitNodeCollector collector, OutlineStyle style);

	/**
	 * Adds the edge of the layer's pixels, as the pose places it, to the copy of an Echo begun last.
	 *
	 * @return false if the layer is not a flat item, which has no pixels to follow
	 */
	boolean lumio$echoShape(PoseStack pose, EchoShape shape);

	/**
	 * Submits the layer as a flat colored silhouette instead of the regular item.
	 *
	 * @param order the order of the submit, silhouettes with a lower order are drawn first
	 * @param color the ARGB color of the silhouette
	 */
	void lumio$submitSilhouette(PoseStack pose, SubmitNodeCollector collector, int order, int color);

	/**
	 * Passes every corner of the layer's quads, transformed by the pose, to the sink, in pose space. The vector is reused for each point.
	 */
	void lumio$points(PoseStack pose, Consumer<Vector3f> sink);
}
