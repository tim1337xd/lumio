package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.EntityHighlighter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * The game's glowing outline for the entities the add-ons outline ({@link EntityHighlighter}). Asked for every entity in every frame: without
 * add-ons the loop over an empty array is all it costs.
 */
@Mixin(Minecraft.class)
public abstract class EntityGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void lumio$addonGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		for (EntityHighlighter highlighter : Addons.highlighters()) {
			if (highlighter.glows(entity)) {
				cir.setReturnValue(true);
				return;
			}
		}
	}
}
