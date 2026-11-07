package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.util.HeldButtons;
import dev.tim1337.lumio.util.InputTrace;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;

/** Redirects the fullscreen toggle to borderless fullscreen when enabled, and keeps held mouse buttons held when the window is focused again. */
@Mixin(Window.class)
public abstract class WindowMixin {
	/** The fullscreen key toggles borderless fullscreen when that is enabled, instead of the game's exclusive fullscreen with its video mode. */
	@Inject(method = "toggleFullScreen", at = @At("HEAD"), cancellable = true)
	private void lumio$borderless(CallbackInfo ci) {
		if (ModFeatures.BORDERLESS != null && ModFeatures.BORDERLESS.takesFullscreenKey()) {
			ModFeatures.BORDERLESS.switchBorderless();
			ci.cancel();
		}
	}

	/**
	 * The window was focused or lost its focus. Losing it can let go of a held mouse button without a new press when it comes back: the keys on
	 * mouse buttons are read again then, so a button still held stays held. The Debug log notes both.
	 */
	@Inject(method = "onFocus", at = @At("RETURN"))
	private void lumio$focus(CallbackInfo ci) {
		boolean focused = ((Window) (Object) this).isFocused();
		InputTrace.note(focused ? "window focused" : "window lost its focus");
		if (focused) {
			HeldButtons.resync();
		}
	}
}
