package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.render.FlatItemMapping;
import dev.tim1337.lumio.render.EchoShape;
import dev.tim1337.lumio.render.OutlineGeometry;
import dev.tim1337.lumio.render.OutlineLayer;
import dev.tim1337.lumio.render.OutlineRenderTypes;
import dev.tim1337.lumio.render.OutlineStyle;
import dev.tim1337.lumio.render.ScreenGlow;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Gives item layers access to their quads, to build outlines, silhouettes and glow from them. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class LayerRenderStateMixin implements OutlineLayer {
	//#if 26.3
	//$ @Shadow
	//$ private net.minecraft.client.resources.model.geometry.ItemQuads quads;
	//$
	//$ @org.spongepowered.asm.mixin.Unique
	//$ private List<BakedQuad> prepareQuadList() {
	//$ 	return quads.all();
	//$ }
	//#else
	@Shadow
	public abstract List<BakedQuad> prepareQuadList();
	//#endif

	@Shadow
	private java.util.function.Supplier<org.joml.Vector3fc[]> extents;

	@Shadow
	protected abstract void applyTransform(PoseStack.Pose pose);

	@Override
	public void lumio$submitGlow(PoseStack pose, SubmitNodeCollector collector, OutlineStyle style) {
		List<BakedQuad> quads = prepareQuadList();
		if (quads.isEmpty() || !OutlineRenderTypes.available()) {
			return;
		}
		// the glow is built around the item's on-screen silhouette, whatever its shape
		pose.pushPose();
		applyTransform(pose.last());
		ScreenGlow.submit(pose, collector, quads, FlatItemMapping.of(quads), style);
		pose.popPose();
	}

	@Override
	public boolean lumio$echoShape(PoseStack pose, EchoShape shape) {
		List<BakedQuad> quads = prepareQuadList();
		FlatItemMapping mapping = quads.isEmpty() ? null : FlatItemMapping.of(quads);
		if (mapping == null) {
			return false;
		}
		pose.pushPose();
		applyTransform(pose.last());
		shape.addItem(pose.last().pose(), mapping, OutlineGeometry.get(mapping.sprite().contents(), 0f));
		pose.popPose();
		return true;
	}

	@Override
	public void lumio$points(PoseStack pose, Consumer<Vector3f> sink) {
		List<BakedQuad> quads = prepareQuadList();
		pose.pushPose();
		applyTransform(pose.last());
		Vector3f out = new Vector3f();
		if (quads.isEmpty()) {
			// a shield or a trident draws itself and has no quads: the game knows how far it reaches
			for (org.joml.Vector3fc point : extents.get()) {
				pose.last().pose().transformPosition(point, out);
				sink.accept(out);
			}
		}
		for (BakedQuad quad : quads) {
			for (int i = 0; i < 4; i++) {
				pose.last().pose().transformPosition(quad.position(i), out);
				sink.accept(out);
			}
		}
		pose.popPose();
	}

	@Override
	public void lumio$submitSilhouette(PoseStack pose, SubmitNodeCollector collector, int order, int color) {
		List<BakedQuad> quads = prepareQuadList();
		if (quads.isEmpty() || !OutlineRenderTypes.available()) {
			return;
		}

		// a quad's uvs are only valid on its sprite's atlas, and a render type binds a single texture
		Map<Identifier, List<BakedQuad>> quadsByAtlas = new HashMap<>(2);
		for (BakedQuad quad : quads) {
			quadsByAtlas.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), atlas -> new ArrayList<>()).add(quad);
		}

		pose.pushPose();
		applyTransform(pose.last());
		quadsByAtlas.forEach((atlas, atlasQuads) ->
			collector.order(order).submitCustomGeometry(pose, OutlineRenderTypes.silhouette(atlas), (quadPose, consumer) -> {
				QuadInstance instance = new QuadInstance();
				instance.setColor(color);
				for (BakedQuad quad : atlasQuads) {
					consumer.putBakedQuad(quadPose, quad, instance);
				}
			}));
		pose.popPose();
	}
}
