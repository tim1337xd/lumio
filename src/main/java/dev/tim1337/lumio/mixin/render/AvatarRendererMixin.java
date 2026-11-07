package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.render.EntityRadiance;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;

/** Radiance around the first person hand, which the game renders as the arm of the player model. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "renderHand", at = @At("TAIL"))
	private void lumio$handRadiance(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
		// the arm is in the hand pass, and so in the Echo's glow: its bounds are not known, so the glow is made over the whole screen
		if (ModFeatures.HELD_ITEM_OUTLINE != null && ModFeatures.HELD_ITEM_OUTLINE.shownStyle() == dev.tim1337.lumio.features.visual.world.RadianceLook.Style.ECHO) {
			dev.tim1337.lumio.render.EchoFeedback.armDrawn();
		}
		if (ModFeatures.HELD_ITEM_OUTLINE != null && ModFeatures.HELD_ITEM_OUTLINE.glowsOnArm()) {
			EntityRadiance.submitPart(arm, skin, pose, collector);
		}
	}
}
