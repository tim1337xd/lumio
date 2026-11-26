package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.render.HighlightOutlines;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Lumio's outline of highlighted entities ({@link HighlightOutlines}) in place of the game's, in a frame where an add-on asks for it: the game still draws
 * the silhouettes into its outline target, but its blur of them is left out and its blend of them onto the screen is Lumio's passes instead. In every
 * other frame nothing changes.
 */
@Mixin(LevelRenderer.class)
public abstract class EntityOutlineMixin {
	@Shadow
	@Final
	private RenderTarget entityOutlineTarget;

	@Shadow
	@Final
	private static Identifier ENTITY_OUTLINE_POST_CHAIN_ID;

	/** The outline chain is looked up once per frame, after the frame's entities were extracted (which asked for Lumio's outline or not). */
	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"))
	private PostChain lumio$ownOutline(ShaderManager shaders, Identifier id, Set<Identifier> targets) {
		// the same call looks up the chain of Fabulous graphics, which stays
		return id.equals(ENTITY_OUTLINE_POST_CHAIN_ID) && HighlightOutlines.active() ? null : shaders.getPostChain(id, targets);
	}

	/** At the end of the world's render, while its projection is still set: the outline pass needs it to tell the world's distance from its depth. */
	@Inject(method = "render", at = @At("TAIL"))
	private void lumio$drawOwnOutline(CallbackInfo ci) {
		if (HighlightOutlines.active()) {
			HighlightOutlines.draw(entityOutlineTarget, Minecraft.getInstance().gameRenderer.mainRenderTarget());
		}
	}

	/** The game's blend of its outline target onto the screen is left out while the outline is Lumio's. */
	//#if 26.3
	//$ @Inject(method = "blitEntityOutline", at = @At("HEAD"), cancellable = true)
	//#else
	@Inject(method = "doEntityOutline", at = @At("HEAD"), cancellable = true)
	//#endif
	private void lumio$skipGameOutline(CallbackInfo ci) {
		if (HighlightOutlines.active()) {
			ci.cancel();
		}
	}
}
