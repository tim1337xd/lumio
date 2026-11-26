package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.EntityHighlighter;
import dev.tim1337.lumio.render.HighlightOutlines;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

/** The outline color of the entities the add-ons outline ({@link EntityHighlighter}); the game queries it every frame. With Lumio's outline
 * ({@link HighlightOutlines}) it is the code of the entity's slot. */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void lumio$addonColor(CallbackInfoReturnable<Integer> cir) {
		Entity entity = (Entity) (Object) this;
		for (EntityHighlighter highlighter : Addons.highlighters()) {
			int color = highlighter.outlineColor(entity);
			if (color >= 0) {
				// with Lumio's outline the silhouette carries the code of the entity's slot, whose color and distance the outline pass reads
				cir.setReturnValue(highlighter.outlineWidth(entity) > 0f ? HighlightOutlines.code(entity, color) : color);
				return;
			}
		}
	}
}
