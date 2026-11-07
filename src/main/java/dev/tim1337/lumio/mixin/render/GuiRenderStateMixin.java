package dev.tim1337.lumio.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * The game can only blur once per frame. The mod's glass requests that blur first, and a vanilla screen requesting it again afterwards (the pause
 * menu, over the mod's panels) would make the game throw; the second request is ignored and the blur stays where it is.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
	@Shadow
	private int firstStratumAfterBlur;

	@Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), cancellable = true)
	private void lumio$onlyOnce(CallbackInfo ci) {
		if (firstStratumAfterBlur != Integer.MAX_VALUE) {
			ci.cancel();
		}
	}
}
