package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;

/**
 * The Visual Filter's fog switches. They hook the fog of the open air alone, after the game set it up: the fogs of water, lava, powder snow,
 * Blindness and Darkness are other classes and never come by here.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void lumio$clearFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker tracker, CallbackInfo ci) {
		ModFeatures.VISUAL_FILTER.clearFog(fog, camera, renderDistance, tracker.getGameTimeDeltaPartialTick(false));
	}
}
