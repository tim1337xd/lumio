package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

/** Draws the chat alone, for the cinematic camera, which hides the rest of the HUD but shows the chat while it is open. */
@Mixin(Hud.class)
public interface HudAccessor {
	@Invoker("extractChat")
	void lumio$extractChat(GuiGraphicsExtractor graphics, DeltaTracker tracker);
}
