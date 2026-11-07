package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.UiSounds;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;

/** Glass-styled widgets play the menu click sound instead of the vanilla one while menu sounds are enabled. */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
	@Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
	private void lumio$sound(SoundManager soundManager, CallbackInfo ci) {
		if (ModFeatures.MENUS.buttonsActive() && ModFeatures.MENU.soundsOn()) {
			UiSounds.click();
			ci.cancel();
		}
	}
}
