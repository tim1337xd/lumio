package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/** The Visual Filter: an entity it leaves out is not drawn; it stays in the world. Asked per entity per frame: a cheap check first. */
@Mixin(EntityRenderDispatcher.class)
public abstract class VisualFilterRenderMixin {
	//#if 26.3
	//$ @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	//$ private <E extends Entity> void lumio$filtered(E entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
	//#else
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void lumio$filtered(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
	//#endif
		// an add-on's entities not drawn now (a short list, a cheap lookup), then the Visual Filter's rules
		if (dev.tim1337.lumio.api.experimental.filter.HiddenEntities.hidden(entity.getId())
			|| ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hides(entity)) {
			cir.setReturnValue(false);
		}
	}
}
