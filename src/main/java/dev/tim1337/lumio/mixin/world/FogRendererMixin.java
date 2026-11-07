package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.render.FogState;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;

/** Fog color and density from the custom sky and the ambience; the result is kept for the world lyrics. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	/** Tints the fog with the mod sky's horizon color; the ambience then thickens it. */
	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private FogData lumio$changeFog(FogData fog, Camera camera, int renderDistance, DeltaTracker tracker, float partialTick, ClientLevel level) {
		ModFeatures.SKY.apply(fog);
		FogState.capture(fog);
		return fog;
	}
}
