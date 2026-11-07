package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.HeldItem;
import dev.tim1337.lumio.render.OutlineItem;
import dev.tim1337.lumio.render.OutlineLayer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;

/** Submits the held item outline together with the item, after the item transform is applied. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements OutlineItem {
	@Shadow
	ItemDisplayContext displayContext;

	@Shadow
	private int activeLayerCount;

	@Shadow
	private ItemStackRenderState.LayerRenderState[] layers;

	/**
	 * The transform has to be applied first, so that the outline follows the transformed item.
	 */
	@WrapMethod(method = "submit")
	private void lumio$submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outlineColor, Operation<Void> original) {
		boolean transformed = ModFeatures.HELD_ITEM_TRANSFORM.apply(pose, displayContext, this);
		if (HeldItem.isFirstPerson(displayContext)) {
			ModFeatures.HELD_ITEM_TRANSFORM.recordBounds(pose, displayContext, this);
			ModFeatures.HELD_ITEM_TRANSFORM.submitArm(pose, collector, light, displayContext);
		}
		// a non-zero outline color is vanilla's own glowing effect, which is left alone
		if (outlineColor == 0) {
			ModFeatures.HELD_ITEM_OUTLINE.submitOutline(this, displayContext, pose, collector);
		}
		original.call(pose, collector, light, overlay, outlineColor);
		if (transformed) {
			pose.popPose();
		}
	}

	@Override
	public int lumio$layerCount() {
		return activeLayerCount;
	}

	@Override
	public OutlineLayer lumio$layer(int index) {
		return (OutlineLayer) (Object) layers[index];
	}
}
