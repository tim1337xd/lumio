package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.EntityHighlighter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

/** The outline color of the entities the add-ons outline ({@link EntityHighlighter}); the game queries it every frame. */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void lumio$addonColor(CallbackInfoReturnable<Integer> cir) {
		for (EntityHighlighter highlighter : Addons.highlighters()) {
			int color = highlighter.outlineColor((Entity) (Object) this);
			if (color >= 0) {
				cir.setReturnValue(color);
				return;
			}
		}
	}
}
