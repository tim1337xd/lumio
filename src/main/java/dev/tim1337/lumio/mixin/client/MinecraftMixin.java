package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * Prevents the focus-loss pause menu during cinematic mode, removes server packs kept from a server when a world of the player's own loads, and lets
 * the perspective key skip the view from the front (Camera).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** The game opens the pause menu when the window loses focus; in cinematic mode that would end the shot, so it is suppressed there. */
	/** The perspective key: the Camera module can skip the view from the front, so the key goes from first person to behind and back. Only the view. */
	@ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;cycle()Lnet/minecraft/client/CameraType;"))
	private CameraType lumio$skipFrontView(CameraType next) {
		return ModFeatures.CAMERA == null ? next : ModFeatures.CAMERA.nextPerspective(next);
	}

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
