package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** The Visual Filter: pure clutter nothing reads (experience orbs, falling blocks) is not ticked by the client while it is not drawn. */
@Mixin(ClientLevel.class)
public abstract class VisualFilterTickMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void lumio$skipFiltered(Entity entity, CallbackInfo ci) {
		if (ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.skipsTick(entity)) {
			ci.cancel();
		}
	}
}
