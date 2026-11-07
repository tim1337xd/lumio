package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.mojang.blaze3d.platform.FramerateLimitTracker;

/**
 * Outside a world (main menu, server and world lists) the game caps itself at 60 FPS. The mod's menus are animated (menu world, lyrics) and look
 * less smooth at 60, so they run at a fixed 120 instead, whatever the limit in the options. The lower limits while the window is minimized or the
 * player is away stay as they are, but while the cinematic camera films: it is meant to be watched without touching anything.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	private static final int MENU_LIMIT = 120;

	@Shadow
	public abstract FramerateLimitTracker.FramerateThrottleReason getThrottleReason();

	/** While the cinematic camera films (also while it records or plays a shot) nobody touches anything: the limit for a player away does not apply then. */
	@ModifyReturnValue(method = "getThrottleReason", at = @At("RETURN"))
	private FramerateLimitTracker.FramerateThrottleReason lumio$filming(FramerateLimitTracker.FramerateThrottleReason reason) {
		boolean away = reason == FramerateLimitTracker.FramerateThrottleReason.SHORT_AFK || reason == FramerateLimitTracker.FramerateThrottleReason.LONG_AFK;
		return away && ModFeatures.CINEMATIC != null && ModFeatures.CINEMATIC.active() ? FramerateLimitTracker.FramerateThrottleReason.NONE : reason;
	}

	@ModifyReturnValue(method = "getFramerateLimit", at = @At("RETURN"))
	private int lumio$menuLimit(int limit) {
		if (getThrottleReason() == FramerateLimitTracker.FramerateThrottleReason.OUT_OF_LEVEL_MENU) {
			return MENU_LIMIT;
		}
		return limit;
	}
}
