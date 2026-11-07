package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.render.CustomSky;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;

/** Draws the custom sky and replaces the vanilla sky color. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	/** The box around the camera that the game draws the End sky on. */
	@Shadow
	@Final
	private GpuBuffer endSkyBuffer;

	@Shadow
	@Final
	private RenderTarget renderTarget;

	/**
	 * A world of the Nether's kind has no sky of the game. Over one of its islands, with nothing above (the Crimson Isle), the game is told it has the
	 * overworld's, so it draws the sky pass and the mod's sky in it; under the Nether's roof it stays as it is.
	 */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;skybox()Lnet/minecraft/world/level/dimension/DimensionType$Skybox;"))
	private DimensionType.Skybox lumio$openNetherSky(DimensionType type, Operation<DimensionType.Skybox> original) {
		DimensionType.Skybox skybox = original.call(type);
		return skybox == DimensionType.Skybox.NONE && ModFeatures.SKY.opensNetherSky() ? DimensionType.Skybox.OVERWORLD : skybox;
	}

	/** Replaces the vanilla sky color with the theme's, so the background is not daytime blue. */
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void lumio$colorSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		ModFeatures.SKY.apply(level, state);
		// the sky's color as shown, for what reflects it (the wet ground)
		//#if 26.3
		//$ dev.tim1337.lumio.render.FogState.captureSky(0xFF000000 | Math.round(state.skyColor.x() * 255f) << 16 | Math.round(state.skyColor.y() * 255f) << 8 | Math.round(state.skyColor.z() * 255f));
		//#else
		dev.tim1337.lumio.render.FogState.captureSky(state.skyColor);
		//#endif
	}

	/** Draws the sky before the sun, moon and stars (HEAD of renderSunMoonAndStars), so they stay on top of it. */
	//#if 26.3
	//$ /** The game draws the whole sky in one pass: what the mod's sky draws into other textures must be done before it opens. */
	//$ @Inject(method = "render", at = @At("HEAD"))
	//$ private void lumio$prepareSky(com.mojang.renderpearl.api.buffers.GpuBufferSlice fog, net.minecraft.client.renderer.state.level.SkyRenderState state, CallbackInfo ci) {
	//$ 	if (state.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD) {
	//$ 		CustomSky.prepare(endSkyBuffer, renderTarget, state.starAngle, state.rainBrightness, state.starBrightness);
	//$ 	}
	//$ }
	//$
	//$ @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
	//$ private void lumio$drawSky(com.mojang.renderpearl.api.commands.RenderPass pass, PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
	//$ 	float rainBrightness, float starBrightness, CallbackInfo ci) {
	//$ 	CustomSky.drawPrepared(pass);
	//$ }
	//#else
	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
	private void lumio$drawSky(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
										 float rainBrightness, float starBrightness, CallbackInfo ci) {
		CustomSky.draw(endSkyBuffer, renderTarget, starAngle, rainBrightness, starBrightness);
	}
	//#endif

	/** Only what the game draws itself: the switches of the sky page. */
	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void lumio$hideSun(CallbackInfo ci) {
		if (ModFeatures.SKY.hidesSun()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
	private void lumio$hideMoon(CallbackInfo ci) {
		if (ModFeatures.SKY.hidesMoon()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
	private void lumio$hideStars(CallbackInfo ci) {
		if (ModFeatures.SKY.hidesVanillaStars()) {
			ci.cancel();
		}
	}

	/** The sun, the moon and the game's stars fade while a jump of the time of day is hidden, so they are never seen to sweep to their new places. */
	@ModifyVariable(method = "renderSun", at = @At("HEAD"), argsOnly = true)
	private float lumio$fadeSun(float alpha) {
		return alpha * ModFeatures.SKY.shownAfterJump();
	}

	@ModifyVariable(method = "renderMoon", at = @At("HEAD"), argsOnly = true)
	private float lumio$fadeMoon(float alpha) {
		return alpha * ModFeatures.SKY.shownAfterJump();
	}

	@ModifyVariable(method = "renderStars", at = @At("HEAD"), argsOnly = true)
	private float lumio$fadeStars(float brightness) {
		return brightness * ModFeatures.SKY.shownAfterJump();
	}
}
