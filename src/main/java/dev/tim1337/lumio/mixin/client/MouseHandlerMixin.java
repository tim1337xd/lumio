package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.api.experimental.util.CameraTurn;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.hud.InfoBarFeature;
import dev.tim1337.lumio.util.InputTrace;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

/** Keeps the cursor position when one menu follows another (see {@link dev.tim1337.lumio.features.misc.CursorFeature}), and counts clicks for the info bar. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	@Shadow
	private boolean mouseGrabbed;

	/** What the mouse's movement does to the camera can be scaled ({@link CameraTurn}); the cursor and everything else stay the game's. Called when the mouse moves in game. */
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double lumio$turnAcross(double across) {
		return across * CameraTurn.scale();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double lumio$turnUp(double up) {
		return up * CameraTurn.scale();
	}

	/** While the tab list is open and has more player rows than it shows, the wheel moves them, not the hotbar. It only looks: nothing is sent, and the hotbar stays where it was. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void lumio$scrollTabList(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (Minecraft.getInstance().gui.screen() == null && ModFeatures.TAB_LIST != null && ModFeatures.TAB_LIST.scrollPlayers(vertical)) {
			ci.cancel();
		}
	}

	@Inject(method = "onButton", at = @At("HEAD"))
	private void lumio$countClick(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
		if (action == InputConstants.PRESS && button.button() == InputConstants.MOUSE_BUTTON_LEFT && minecraft.gui.screen() == null) {
			InfoBarFeature.click();
		}
	}

	@Inject(method = "grabMouse", at = @At("HEAD"))
	private void lumio$traceGrab(CallbackInfo ci) {
		if (!mouseGrabbed) {
			InputTrace.note("mouse grabbed");
		}
	}

	@Inject(method = "releaseMouse", at = @At("HEAD"))
	private void lumio$traceRelease(CallbackInfo ci) {
		if (mouseGrabbed) {
			InputTrace.note("mouse released");
		}
	}

	/** Debug log: a button let go in game, as the window system reports it, which tells a real release from one the game made up. */
	@Inject(method = "onButton", at = @At("HEAD"))
	private void lumio$traceButtonUp(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
		if (action == InputConstants.RELEASE && minecraft.gui.screen() == null && InputTrace.on() && (minecraft.options.keyAttack.isDown() || minecraft.options.keyUse.isDown())) {
			InputTrace.note("mouse button " + button.button() + " up");
		}
	}

	/** A menu was closed: the game is about to grab the mouse, and the cursor is still at its menu position. */
	@Inject(method = "grabMouse", at = @At("HEAD"))
	private void lumio$keepPlace(CallbackInfo ci) {
		if (!mouseGrabbed && ModFeatures.NO_CURSOR_RESET != null) {
			ModFeatures.NO_CURSOR_RESET.grabbing(xpos, ypos);
		}
	}

	/** A menu opened and the game centered the cursor: if one was closed just before, the cursor is moved back to where it was. */
	@Inject(method = "releaseMouse", at = @At("RETURN"))
	private void lumio$putBack(CallbackInfo ci) {
		if (ModFeatures.NO_CURSOR_RESET == null) {
			return;
		}
		double[] place = ModFeatures.NO_CURSOR_RESET.releasing();
		if (place != null) {
			xpos = place[0];
			ypos = place[1];
			//#if 26.3
			//$ InputConstants.releaseMouse(minecraft.getWindow(), xpos, ypos);
			//#else
			InputConstants.grabOrReleaseMouse(minecraft.getWindow(), InputConstants.CURSOR_NORMAL, xpos, ypos);
			//#endif
		}
	}
}
