package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Render pipelines and render types for the held item outline and glow, the world lyrics, and additive lights. */
public final class OutlineRenderTypes {
	/**
	 * Vanilla's outline pipeline (flat vertex color, shaped by the alpha of the texture) with two differences:
	 * it is translucent, and it does its own depth test. Vanilla's outline render types draw to the entity glow
	 * target instead, which is not composited for the first person hand.
	 * <p>
	 * Being translucent, silhouettes are drawn in a later phase than the (solid) item, no matter their submit order.
	 * They are pushed away from the camera by the feature, so they fail the depth test wherever the item is, and
	 * only the parts sticking out around it stay visible. Depth is written with a strict test, so overlapping
	 * silhouettes of the same depth are only blended once.
	 * Minecraft uses a reversed depth buffer (closer is greater, see {@link DepthStencilState#DEFAULT}).
	 */
	private static final RenderPipeline SILHOUETTE_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/silhouette"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/silhouette"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, true))
			.build()
	);

	private static int checkedFor = -1;
	private static boolean usable;

	/**
	 * For the glow: a textured translucent quad whose filtered texture provides color and opacity. Like the other outlines it sits behind
	 * the item, so it writes no depth either.
	 */
	private static final RenderPipeline GLOW_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/glow"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/glow"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build()
	);

	/**
	 * World lyric glyphs as a sprite: translucent like the glow, but writing depth where they cover, like vanilla text. Water and other
	 * translucent geometry behind the glyphs is drawn after them and would otherwise be drawn over them, making the glyphs look like they are
	 * behind it.
	 */
	private static final RenderPipeline LYRIC_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/lyric"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/lyric"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
			.build()
	);

	/** Additive lights, like fireflies: the texture is a soft glow, occluded only by what is in front of it. */
	private static final RenderPipeline LIGHT_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/light"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/glow"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build()
	);

	private static final Map<Identifier, RenderType> SILHOUETTES = new HashMap<>();

	private OutlineRenderTypes() {
	}

	/** @return whether the additive light of the fireflies can be drawn: on its own, so another effect's shader that failed does not take them away */
	public static boolean lightAvailable() {
		return ModPipelines.usable(LIGHT_PIPELINE);
	}

	/**
	 * @return whether the world's effects can be drawn: the game binds their pipelines itself, later and out of the mod's reach, so what would draw with a
	 *         pipeline that did not compile must not be submitted at all. Kept until the shaders reload, as this is asked for every effect.
	 */
	public static boolean available() {
		if (checkedFor != ModPipelines.generation()) {
			checkedFor = ModPipelines.generation();
			usable = ModPipelines.usable(SILHOUETTE_PIPELINE) & ModPipelines.usable(GLOW_PIPELINE) & ModPipelines.usable(LIGHT_PIPELINE)
				& ModPipelines.usable(LYRIC_PIPELINE);
		}
		return usable;
	}

	/** @param texture the filtered glow texture */
	public static RenderType glow(Identifier texture) {
		return textured(Lumio.MOD_ID + "_glow", GLOW_PIPELINE, texture);
	}

	/** @param texture a filtered soft light, blended additively */
	public static RenderType light(Identifier texture) {
		return textured(Lumio.MOD_ID + "_light", LIGHT_PIPELINE, texture);
	}

	/** @param texture the filtered glyph sprite; covered pixels occlude what is behind */
	public static RenderType lyric(Identifier texture) {
		return textured(Lumio.MOD_ID + "_lyric", LYRIC_PIPELINE, texture);
	}

	/** @param texture a soft light with mipmaps, such as the glow of a lyric line, so it stays smooth far away */
	public static RenderType glowMipmapped(Identifier texture) {
		return textured(Lumio.MOD_ID + "_glow_mipmapped", GLOW_PIPELINE, texture, true);
	}

	/** @param texture the glyph sprite with mipmaps; covered pixels occlude what is behind */
	public static RenderType lyricMipmapped(Identifier texture) {
		return textured(Lumio.MOD_ID + "_lyric_mipmapped", LYRIC_PIPELINE, texture, true);
	}

	private static RenderType textured(String name, RenderPipeline pipeline, Identifier texture) {
		return textured(name, pipeline, texture, false);
	}

	private static RenderType textured(String name, RenderPipeline pipeline, Identifier texture, boolean mipmaps) {
		return RenderType.create(
			name,
			RenderSetup.builder(pipeline)
				.withTexture("Sampler0", texture, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, mipmaps))
				.setOutputTarget(OutputTarget.MAIN_TARGET)
				.createRenderSetup()
		);
	}

	/**
	 * The texture has to be the atlas the sprite of the quad lives on, as the uvs of a quad are only valid there.
	 * Items and blocks are on different atlases.
	 */
	public static RenderType silhouette(Identifier atlas) {
		return SILHOUETTES.computeIfAbsent(atlas, id -> RenderType.create(
			Lumio.MOD_ID + "_silhouette",
			RenderSetup.builder(SILHOUETTE_PIPELINE)
				.withTexture("Sampler0", id)
				.setOutputTarget(OutputTarget.MAIN_TARGET)
				.createRenderSetup()
		));
	}
}
