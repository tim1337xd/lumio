package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

/** Prevents the focus-loss pause menu during cinematic mode, and removes server packs kept from a server when a world of the player's own loads. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** The game opens the pause menu when the window loses focus; in cinematic mode that would end the shot, so it is suppressed there. */
	@Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
	private void lumio$keepFilm(CallbackInfo ci) {
		if (ModFeatures.CINEMATIC != null && ModFeatures.CINEMATIC.active()) {
			ci.cancel();
		}
	}

	/**
	 * Just before the game's first load of resources: the server packs Keep server resource packs saved are given to it, so they are part of that
	 * load. The settings are read by then, as the mod starts first.
	 */
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"))
	private void lumio$restoreServerPacks(CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.restore(self.getDownloadedPackSource(), self.gameDirectory.toPath());
		}
	}

	/** A world of the player's own is loading: server packs that Keep server resource packs kept are removed, as the game would have done on leaving. */
	@Inject(method = "doWorldLoad", at = @At("HEAD"))
	private void lumio$removeKeptPacks(CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.worldLoading();
		}
	}
}
