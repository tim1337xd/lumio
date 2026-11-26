package dev.tim1337.lumio.util;

import java.util.stream.Collectors;

import dev.tim1337.lumio.api.experimental.debug.DebugCategory;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.debug.DebugLevel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * A probe for the debug tools: what lets go of the attack and use keys while playing (a screen opened or closed, the keys released or read again, the mouse
 * released or grabbed, the window's focus, the button itself let go), each with a short trace of who asked. Written as it happens, so it stands right
 * before the line of whatever stopped because of it, such as a block that stops breaking.
 */
public final class InputTrace {
	private static final int TRACE_FRAMES = 5;
	private static final int OWN_FRAMES = 2;

	private InputTrace() {
	}

	/** @return whether events are written: something listens to the probes and a world is shown */
	public static boolean on() {
		return DebugHooks.ACTIVE && Minecraft.getInstance().level != null;
	}

	/** @return whether the mapping is the attack or the use key, and is held now */
	public static boolean heldAttackOrUse(KeyMapping mapping) {
		var options = Minecraft.getInstance().options;
		return (mapping == options.keyAttack || mapping == options.keyUse) && mapping.isDown();
	}

	/** Writes an event with the attack and use keys as they are now, and who asked for it. */
	public static void note(String what) {
		if (!on()) {
			return;
		}
		var client = Minecraft.getInstance();
		String caller = StackWalker.getInstance().walk(frames -> frames.skip(OWN_FRAMES).limit(TRACE_FRAMES)
			.map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName())
			.collect(Collectors.joining(" < ")));
		DebugHooks.log(DebugCategory.INPUT, DebugLevel.TRACE, "Input: {} (attack down {}, left button really down {}, use down {}, screen {}, mouse grabbed {}) from {}", what, client.options.keyAttack.isDown(),
			HeldButtons.down(InputConstants.MOUSE_BUTTON_LEFT), client.options.keyUse.isDown(),
			client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName(), client.mouseHandler.isMouseGrabbed(), caller);
	}
}
