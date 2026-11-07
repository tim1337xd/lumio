package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.render.CustomSky;
import dev.tim1337.lumio.render.WetGround;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;

/** What needs the finished world: the reflections of the wet ground, and the clouds, which cover the sun and pass in front of hills that reach into them. The hand is drawn after this, over them. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererCloudsMixin {
	/** Right after the sky, before the world: the clouds over the sky, which all of the world then lies over (see {@link CustomSky#drawCloudsOverSky}). */
	@Inject(method = "lambda$addSkyPass$0", at = @At("TAIL"))
	private void lumio$cloudsOverSky(CallbackInfo ci) {
		CustomSky.drawCloudsOverSky(Minecraft.getInstance().gameRenderer.mainRenderTarget());
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void lumio$clouds(CallbackInfo ci) {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		WetGround.draw(main);
		CustomSky.drawClouds(main);
	}
}
