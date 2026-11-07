package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.render.MenuWarmup;
import dev.tim1337.lumio.ui.title.StartupTransition;
import dev.tim1337.lumio.util.FrameTimes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.ShaderManager;

/**
 * The game's shaders, loaded in every resource load. Once they are applied (still during the loading screen) the mod's shaders can be compiled,
 * so the main menu's are prepared then, a slice per frame, instead of when loading has finished and the way into the menu runs. Nothing about
 * loading changes.
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
	@Unique
	private long lumio$applyStart;

	@Inject(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
		at = @At("HEAD"))
	private void lumio$applyStart(CallbackInfo ci) {
		lumio$applyStart = System.nanoTime();
	}

	@Inject(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
		at = @At("TAIL"))
	private void lumio$shadersApplied(CallbackInfo ci) {
		if (FrameTimes.enabled()) {
			// for the frame time log of the way into the main menu: how long the game itself took to load its shaders in this frame
			StartupTransition.noteWork("the game's shaders " + (System.nanoTime() - lumio$applyStart) / 1_000_000L + " ms");
		}
		MenuWarmup.shadersReady();
	}
}
