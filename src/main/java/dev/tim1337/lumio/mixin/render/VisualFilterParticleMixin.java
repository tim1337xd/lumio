package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

/** The Visual Filter: the puffs and blasts of explosions are not made, when that is on. */
@Mixin(ParticleEngine.class)
public abstract class VisualFilterParticleMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void lumio$skipExplosions(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
		CallbackInfoReturnable<Particle> cir) {
		if (ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hidesParticle(options)) {
			cir.setReturnValue(null);
		}
	}
}
