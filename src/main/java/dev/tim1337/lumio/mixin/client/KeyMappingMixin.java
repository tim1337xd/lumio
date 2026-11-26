package dev.tim1337.lumio.mixin.client;

import java.util.function.Consumer;

import java.util.List;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.input.MouseButtonEvent;

import net.minecraft.client.input.KeyEvent;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.mojang.blaze3d.platform.InputConstants;

import dev.tim1337.lumio.mixin.accessor.KeyMappingAccessor;

import dev.tim1337.lumio.features.misc.keybinds.KeyCombos;

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

/**
 * Keys on mouse buttons stay held when the game reads its keys again ({@link HeldButtons}), the Debug log notes what lets go of attack and use
 * ({@link InputTrace}), and key combinations choose which mappings act on a press ({@link KeyCombos}).
 */
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

	/** Whether the game is pressing keys now (a click, or a key going down), where key combinations choose which mappings act. */
	@Unique
	private static boolean lumio$pressing;
	@Unique
	private static final List<KeyMapping> LUMIO$WINNERS = new ArrayList<>();

	@Inject(method = "click", at = @At("HEAD"))
	private static void lumio$clickBegins(InputConstants.Key key, CallbackInfo ci) {
		lumio$pressing = true;
	}

	@Inject(method = "click", at = @At("RETURN"))
	private static void lumio$clickEnds(InputConstants.Key key, CallbackInfo ci) {
		lumio$pressing = false;
	}

	/** A key going down is pressing too; going up it lets go of every mapping on it, combination or not, so nothing stays held. */
	@Inject(method = "set", at = @At("HEAD"))
	private static void lumio$setBegins(InputConstants.Key key, boolean down, CallbackInfo ci) {
		lumio$pressing = down;
	}

	@Inject(method = "set", at = @At("RETURN"))
	private static void lumio$setEnds(InputConstants.Key key, boolean down, CallbackInfo ci) {
		lumio$pressing = false;
	}

	/**
	 * Every press of a key reaches its mappings here. With key combinations set, only the most specific mappings on the key act (with "Tab + R"
	 * matching, the plain R does not). Without any combination this returns at once and the game goes on as always.
	 */
	@Inject(method = "forAllKeyMappings", at = @At("HEAD"), cancellable = true)
	private static void lumio$mostSpecific(InputConstants.Key key, Consumer<KeyMapping> action, CallbackInfo ci) {
		if (!lumio$pressing || !KeyCombos.any()) {
			return;
		}
		List<KeyMapping> onKey = KeyMappingAccessor.lumio$map().get(key);
		if (onKey == null) {
			return;
		}
		KeyCombos.winners(key, onKey, LUMIO$WINNERS);
		for (int i = 0; i < LUMIO$WINNERS.size(); i++) {
			action.accept(LUMIO$WINNERS.get(i));
		}
		ci.cancel();
	}

	/** A screen asks whether a key event is this mapping's: with combinations set, only if its holders are held and it is the most specific. */
	@Inject(method = "matches(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("RETURN"), cancellable = true)
	private void lumio$matchesCombo(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && KeyCombos.any()) {
			cir.setReturnValue(KeyCombos.actsInScreen((KeyMapping) (Object) this, KeyMappingHelper.getBoundKeyOf((KeyMapping) (Object) this)));
		}
	}

	@Inject(method = "matchesMouse", at = @At("RETURN"), cancellable = true)
	private void lumio$matchesMouseCombo(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && KeyCombos.any()) {
			cir.setReturnValue(KeyCombos.actsInScreen((KeyMapping) (Object) this, KeyMappingHelper.getBoundKeyOf((KeyMapping) (Object) this)));
		}
	}
}
