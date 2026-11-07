package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.util.HeldButtons;
import dev.tim1337.lumio.util.InputTrace;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Keys on mouse buttons stay held when the game reads its keys again ({@link HeldButtons}), and the Debug log notes what lets go of attack and use ({@link InputTrace}). */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
	/** The game reads every key from the keyboard here, after it grabbed the mouse: a key on a mouse button reads that button instead. Called only then. */
	@WrapOperation(method = "setAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;setDown(Z)V"))
	private static void lumio$readMouseButtons(KeyMapping mapping, boolean down, Operation<Void> original) {
		Boolean button = HeldButtons.mouseKeyDown(mapping);
		original.call(mapping, button != null ? button : down);
	}

	@Inject(method = "setAll", at = @At("HEAD"))
	private static void lumio$traceReadAgain(CallbackInfo ci) {
		InputTrace.note("keys read again");
	}

	@Inject(method = "releaseAll", at = @At("HEAD"))
	private static void lumio$traceReleaseAll(CallbackInfo ci) {
		InputTrace.note("all keys let go");
	}

	@Inject(method = "resetMapping", at = @At("HEAD"))
	private static void lumio$traceResetMapping(CallbackInfo ci) {
		InputTrace.note("keys bound again");
	}

	/** Called on every press and release of a key: only a held attack or use key being let go is looked at further. */
	@Inject(method = "setDown", at = @At("HEAD"))
	private void lumio$traceLetGo(boolean down, CallbackInfo ci) {
		KeyMapping self = (KeyMapping) (Object) this;
		if (!down && InputTrace.heldAttackOrUse(self)) {
			InputTrace.note((self == Minecraft.getInstance().options.keyAttack ? "attack" : "use") + " key let go");
		}
	}
}
