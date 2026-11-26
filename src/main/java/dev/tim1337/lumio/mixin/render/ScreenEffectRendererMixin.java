package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.VisualFilterFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.ScreenEffectRenderer;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * The Camera module's screen overlays: the burning overlay lower and lighter, or none, and no water texture over the view. Only these two: the
 * overlays the game uses to hide what is behind them (inside a block) are untouched, and underwater fog and view distance stay the game's.
 * The methods are named only, without their arguments, as those differ between the Minecraft versions.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	/** How far the burning overlay moves down, in the units of its quad (1 is its height), so its top sits at about a fifth of the view at the usual field of view; the game's own already covers about the bottom third. */
	@Unique
	private static final float FIRE_LOWER = 0.2f;
	/** The burning overlay's color, white at the game's 90 % opacity, and the lighter one when it is lowered. */
	@Unique
	private static final int FIRE_COLOR = 0xE5FFFFFF;
	@Unique
	private static final int FIRE_COLOR_LOWER = 0x9EFFFFFF;
	/** Whether the pose was moved for this overlay, so it is put back exactly then. */
	@Unique
	private static boolean lumio$fireLowered;

	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void lumio$fire(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
		VisualFilterFeature.Fire fire = ModFeatures.VISUAL_FILTER == null ? VisualFilterFeature.Fire.NORMAL : ModFeatures.VISUAL_FILTER.fire();
		if (fire == VisualFilterFeature.Fire.HIDE) {
			ci.cancel();
			return;
		}
		lumio$fireLowered = fire == VisualFilterFeature.Fire.LOWER;
		if (lumio$fireLowered) {
			pose.pushPose();
			pose.translate(0f, -FIRE_LOWER, 0f);
		}
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void lumio$fireDone(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
		if (lumio$fireLowered) {
			lumio$fireLowered = false;
			pose.popPose();
		}
	}

	/** The quad's color is read when the overlay is drawn, after it was submitted; so the setting decides, not the flag above. */
	@ModifyConstant(method = "buildFireQuad", constant = @Constant(intValue = FIRE_COLOR))
	private static int lumio$fireColor(int color) {
		return ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.fire() == VisualFilterFeature.Fire.LOWER ? FIRE_COLOR_LOWER : color;
	}

	@Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
	private static void lumio$water(CallbackInfo ci) {
		if (ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hideUnderwater()) {
			ci.cancel();
		}
	}
}
