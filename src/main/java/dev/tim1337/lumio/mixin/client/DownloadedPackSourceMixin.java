package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;

import java.net.URL;
import java.util.UUID;

/**
 * Keep server resource packs: leaves out the game's reload of all resources when the server packs would not change (see
 * {@code KeepServerPacksFeature}). The packets of the game are not touched: its pack manager answers the server as always.
 */
@Mixin(DownloadedPackSource.class)
public abstract class DownloadedPackSourceMixin {
	@Shadow
	private PackReloadConfig.Callbacks pendingReload;

	/**
	 * Every change of the server packs ends here, before the game reloads its resources. When the reload is left out, the game's own success
	 * callback runs on the next tick, as after a reload: it marks the packs as loaded and gives the server the game's usual answer.
	 */
	@Inject(method = "startReload", at = @At("HEAD"), cancellable = true)
	private void lumio$skipUnchangedReload(PackReloadConfig.Callbacks callbacks, CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null && ModFeatures.KEEP_SERVER_PACKS.skipReload(callbacks.packsToLoad(), pendingReload != null)) {
			ci.cancel();
			Minecraft.getInstance().schedule(callbacks::onSuccess);
		}
	}

	/** The player left the server; the game removes its packs next, which is left out while the feature keeps them. */
	@Inject(method = "cleanupAfterDisconnect", at = @At("HEAD"))
	private void lumio$leaving(CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.leaving();
		}
	}

	/** With the debug log on: what the server sends (the pack's id, hash and address), before the game handles it as always. */
	@Inject(method = "pushPack", at = @At("HEAD"))
	private void lumio$serverSent(UUID id, URL url, String hash, CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.serverSent(id, url, hash);
		}
	}

	/** With the debug log on: the server removed one of its packs. */
	@Inject(method = "popPack", at = @At("HEAD"))
	private void lumio$serverRemoved(UUID id, CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.serverRemoved(id);
		}
	}

	/** With the debug log on: the server removed all of its packs. */
	@Inject(method = "popAll", at = @At("HEAD"))
	private void lumio$serverRemovedAll(CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.serverRemoved(null);
		}
	}

	/** A load of server packs failed: the game recovers with fewer packs, so what is loaded is no longer known. */
	@Inject(method = {"onRecovery", "onRecoveryFailure"}, at = @At("HEAD"))
	private void lumio$loadFailed(CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.loadFailed();
		}
	}
}
