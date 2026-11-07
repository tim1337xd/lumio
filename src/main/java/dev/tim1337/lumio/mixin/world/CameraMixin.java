package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.cinematic.CinematicCameraFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;

/** Positions the cinematic camera. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	/**
	 * The cinematic camera replaces the game camera for the frame and is marked detached, so the player is rendered as in third person. It is applied
	 * at TAIL of alignWithEntity rather than after the whole camera update: the update computes the culling frustum and projection from the camera
	 * position at that point, so a camera moved afterwards would be rendered with the first person frustum, and the world (and the culling done by
	 * entity/block culling mods) would be clipped in the wrong places.
	 */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void lumio$cinematic(float partial, CallbackInfo ci) {
		CinematicCameraFeature cinematic = ModFeatures.CINEMATIC;
		if (cinematic == null || !cinematic.active()) {
			return;
		}
		if (cinematic.frame(partial)) {
			detached = true;
			setPosition(cinematic.cameraX(), cinematic.cameraY(), cinematic.cameraZ());
			setRotation(cinematic.cameraYaw(), cinematic.cameraPitch());
		}
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void lumio$fov(float partial, CallbackInfoReturnable<Float> cir) {
		CinematicCameraFeature cinematic = ModFeatures.CINEMATIC;
		// also while the film is off: it remembers the game's field of view for the glides out of the eyes and back
		if (cinematic != null) {
			cir.setReturnValue(cinematic.fovOr(cir.getReturnValueF()));
		}
	}
}
