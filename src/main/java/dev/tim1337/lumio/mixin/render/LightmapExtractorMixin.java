package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

/**
 * Full Bright of the Camera module: once the game has filled in the light map's state, its ambient color is lifted towards white, and the sky's and
 * the blocks' own light and the boss bar's darkening of the world fade out with it, so the light is the same everywhere: in a dark room, a lit one
 * and outside, with no change when going in or out. No night vision (no icon, no particles, no tint); the Darkness and Blindness effects still
 * apply, in the light map's shader and the fog.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {
	@Unique
	private static final Vector3f LIFTED = new Vector3f();
	@Unique
	private static final Vector3f MOLTEN_LIGHT = new Vector3f();

	@Shadow
	private boolean needsUpdate;

	/** While Full Bright fades in or out the light map is made every frame; otherwise only when the game wants it. */
	@Inject(method = "extract", at = @At("HEAD"))
	private void lumio$fadeEveryFrame(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (ModFeatures.CAMERA != null && ModFeatures.CAMERA.brightChanging() || ModFeatures.SKY != null && ModFeatures.SKY.moltenChanging()) {
			needsUpdate = true;
		}
	}

	/** Over the Molten sky with "Tint the world", the sky's light on the world turns towards the sky's color, as bright as it was. */
	@Inject(method = "extract", at = @At("TAIL"))
	private void lumio$moltenLight(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (ModFeatures.SKY == null || !state.needsUpdate || ModFeatures.SKY.moltenWorldTint() <= 0f) {
			return;
		}
		Vector3fc sky = state.skyLightColor;
		float[] tinted = ModFeatures.SKY.moltenLight(sky.x(), sky.y(), sky.z());
		// a reused vector, as the lifted color: the state takes a new one from the game at its next update
		state.skyLightColor = MOLTEN_LIGHT.set(tinted[0], tinted[1], tinted[2]);
	}

	/** The state is complete here (the early returns leave it as it was): the ambient color is lifted. */
	@Inject(method = "extract", at = @At("TAIL"))
	private void lumio$fullBright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (ModFeatures.CAMERA == null || !state.needsUpdate) {
			return;
		}
		float lift = ModFeatures.CAMERA.brightLift();
		if (lift <= 0f) {
			return;
		}
		Vector3fc ambient = state.ambientColor;
		// a reused vector: the state takes a new color from the game at its next update, before this runs again
		state.ambientColor = LIFTED.set(ambient).lerp(LightmapRenderStateExtractor.WHITE, lift);
		// the light of the sky and of the blocks would lift lit places above the dark ones, and a boss bar may darken the whole world: both go
		float keep = 1f - ModFeatures.CAMERA.brightShare();
		state.skyFactor *= keep;
		state.blockFactor *= keep;
		state.bossOverlayWorldDarkening *= keep;
	}
}
