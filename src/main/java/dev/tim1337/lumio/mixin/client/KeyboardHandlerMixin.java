package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ScreenHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Routes typed characters to the storage overlay search while it has focus (the chest screen has no text field of its own), and key presses to
 * the custom keybinds before the game or a screen sees them, so a keybind that works takes its key.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void lumio$commandKeybinds(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (ModFeatures.COMMAND_KEYBINDS != null && window == Minecraft.getInstance().getWindow().handle() && ModFeatures.COMMAND_KEYBINDS.onKey(action, event)) {
			ci.cancel();
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void lumio$addonTyping(long window, CharacterEvent event, CallbackInfo ci) {
		var screen = Minecraft.getInstance().gui.screen();
		if (screen == null) {
			return;
		}
		for (ScreenHooks hooks : Addons.screenHooks()) {
			if (hooks.charTyped(screen, event.codepoint())) {
				ci.cancel();
				return;
			}
		}
	}
}
