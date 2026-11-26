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
import java.util.IdentityHashMap;
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

	/** Letters and symbols from a signed distance field ({@link SdfText}): writing depth where they cover, like the lyric sprites. */
	private static final RenderPipeline SDF_TEXT_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sdf_text"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/sdf_text"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
			.build()
	);

	/** The same, writing no depth: letters on a tile, drawn after it. */
	private static final RenderPipeline SDF_TEXT_FLAT_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sdf_text_flat"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/sdf_text"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build()
	);

	/** The soft glow of letters, from the field's true distance; writes no depth. */
	private static final RenderPipeline SDF_GLOW_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sdf_glow"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/sdf_glow"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build()
	);

	/** Tiles and strokes with rounded corners, drawn as a distance; write no depth. */
	private static final RenderPipeline SDF_SHAPE_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sdf_shape"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/sdf_shape"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build()
	);

	/** The same glyphs writing no depth: for letters on a tile that is drawn late, which they must not cut out. */
	private static final RenderPipeline LYRIC_FLAT_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/lyric_flat"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/lyric"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
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

	/** How a piece of the lyrics is drawn over the finished world ({@link LyricOverlay}): the same shader and texture, tested against the world, writing no depth. */
	public record Overlay(RenderPipeline pipeline, Identifier texture, boolean mipmaps) {
	}

	private static final RenderPipeline GLOW_OVERLAY = overlayPipeline("glow", "core/glow");
	private static final RenderPipeline LYRIC_OVERLAY = overlayPipeline("lyric", "core/lyric");
	private static final RenderPipeline SDF_TEXT_OVERLAY = overlayPipeline("sdf_text", "core/sdf_text");
	private static final RenderPipeline SDF_GLOW_OVERLAY = overlayPipeline("sdf_glow", "core/sdf_glow");
	private static final RenderPipeline SDF_SHAPE_OVERLAY = overlayPipeline("sdf_shape", "core/sdf_shape");
	private static final Map<RenderType, Overlay> OVERLAYS = new IdentityHashMap<>();

	private static RenderPipeline overlayPipeline(String name, String fragment) {
		return ModPipelines.register(
			RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
				.withLocation(Lumio.id("pipeline/lyric_overlay_" + name))
				.withVertexShader(Lumio.id("core/silhouette"))
				.withFragmentShader(Lumio.id(fragment))
				.withCull(false)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
				.build()
		);
	}

	/** @return the overlay pipeline for a pipeline of the lyrics, or null for any other */
	private static RenderPipeline overlayPipelineOf(RenderPipeline pipeline) {
		if (pipeline == GLOW_PIPELINE) {
			return GLOW_OVERLAY;
		}
		if (pipeline == LYRIC_PIPELINE || pipeline == LYRIC_FLAT_PIPELINE) {
			return LYRIC_OVERLAY;
		}
		if (pipeline == SDF_TEXT_PIPELINE || pipeline == SDF_TEXT_FLAT_PIPELINE) {
			return SDF_TEXT_OVERLAY;
		}
		if (pipeline == SDF_GLOW_PIPELINE) {
			return SDF_GLOW_OVERLAY;
		}
		return pipeline == SDF_SHAPE_PIPELINE ? SDF_SHAPE_OVERLAY : null;
	}

	/** @return how a piece of this type is drawn over the world, or null when it is no piece of the lyrics */
	static Overlay overlayOf(RenderType type) {
		return OVERLAYS.get(type);
	}

	/** @return whether every pipeline of the overlay compiled */
	static boolean overlaysUsable() {
		return ModPipelines.usable(GLOW_OVERLAY) && ModPipelines.usable(LYRIC_OVERLAY) && ModPipelines.usable(SDF_TEXT_OVERLAY) && ModPipelines.usable(SDF_GLOW_OVERLAY)
			&& ModPipelines.usable(SDF_SHAPE_OVERLAY);
	}

	// With improved transparency, 26.3 draws see-through things order-independently: each render type of ours needs those variants of its pipeline, or
	// the game stops with "does not have OIT pipelines set up". The shaders have the lines for them (see "//26.3" in them).
	//#if 26.3
	//$ private static final net.minecraft.client.renderer.oit.OitPipelineSet SILHOUETTE_OIT = oit("lumio_silhouette", "core/silhouette", false);
	//$ private static final net.minecraft.client.renderer.oit.OitPipelineSet GLOW_OIT = oit("lumio_glow", "core/glow", false);
	//$ private static final net.minecraft.client.renderer.oit.OitPipelineSet LIGHT_OIT = oit("lumio_light", "core/glow", true);
	//$ private static final net.minecraft.client.renderer.oit.OitPipelineSet LYRIC_OIT = oit("lumio_lyric", "core/lyric", false);
	//$ private static final net.minecraft.client.renderer.oit.OitPipelineSet SDF_TEXT_OIT = oit("lumio_sdf_text", "core/sdf_text", false);
	//$ private static final net.minecraft.client.renderer.oit.OitPipelineSet SDF_GLOW_OIT = oit("lumio_sdf_glow", "core/sdf_glow", false);
	//$ private static final net.minecraft.client.renderer.oit.OitPipelineSet SDF_SHAPE_OIT = oit("lumio_sdf_shape", "core/sdf_shape", false);
	//$
	//$ private static net.minecraft.client.renderer.oit.OitPipelineSet oit(String name, String fragment, boolean additive) {
	//$ 	// the outline's bindings and vertex format, without its color target: the OIT stages bring their own
	//$ 	RenderPipeline.Builder base = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
	//$ 		.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.PROJECTION)
	//$ 		.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.DYNAMIC_TRANSFORMS)
	//$ 		.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
	//$ 		.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR)
	//$ 		.withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS)
	//$ 		.withVertexShader(Lumio.id("core/silhouette"))
	//$ 		.withFragmentShader(Lumio.id(fragment))
	//$ 		.withCull(false);
	//$ 	if (additive) {
	//$ 		base = base.withShaderDefine("OIT_ADDITIVE");
	//$ 	}
	//$ 	net.minecraft.client.renderer.oit.OitPipelineSet set = net.minecraft.client.renderer.oit.OitPipelineSet.builder(name, base).build();
	//$ 	ModPipelines.register(set.depthBoundsPipeline());
	//$ 	ModPipelines.register(set.transmittancePipeline());
	//$ 	ModPipelines.register(set.accumulatePipeline());
	//$ 	return set;
	//$ }
	//$
	//$ private static net.minecraft.client.renderer.oit.OitPipelineSet oitOf(RenderPipeline pipeline) {
	//$ 	return pipeline == LIGHT_PIPELINE ? LIGHT_OIT : pipeline == LYRIC_PIPELINE ? LYRIC_OIT
	//$ 		: pipeline == SDF_TEXT_PIPELINE || pipeline == SDF_TEXT_FLAT_PIPELINE ? SDF_TEXT_OIT : pipeline == SDF_GLOW_PIPELINE ? SDF_GLOW_OIT
	//$ 		: pipeline == SDF_SHAPE_PIPELINE ? SDF_SHAPE_OIT : GLOW_OIT;
	//$ }
	//$
	//$ private static boolean usable(net.minecraft.client.renderer.oit.OitPipelineSet set) {
	//$ 	return ModPipelines.usable(set.depthBoundsPipeline()) & ModPipelines.usable(set.transmittancePipeline()) & ModPipelines.usable(set.accumulatePipeline());
	//$ }
	//#endif

	private OutlineRenderTypes() {
	}

	/** @return whether the additive light of the fireflies can be drawn: on its own, so another effect's shader that failed does not take them away */
	public static boolean lightAvailable() {
		//#if 26.3
		//$ return ModPipelines.usable(LIGHT_PIPELINE) && usable(LIGHT_OIT);
		//#else
		return ModPipelines.usable(LIGHT_PIPELINE);
		//#endif
	}

	/**
	 * @return whether the world's effects can be drawn: the game binds their pipelines itself, later and out of the mod's reach, so what would draw with a
	 *         pipeline that did not compile must not be submitted at all. Kept until the shaders reload, as this is asked for every effect.
	 */
	public static boolean available() {
		if (checkedFor != ModPipelines.generation()) {
			checkedFor = ModPipelines.generation();
			usable = ModPipelines.usable(SILHOUETTE_PIPELINE) & ModPipelines.usable(GLOW_PIPELINE) & ModPipelines.usable(LIGHT_PIPELINE)
				& ModPipelines.usable(LYRIC_PIPELINE)
				//#if 26.3
				//$ & usable(SILHOUETTE_OIT) & usable(GLOW_OIT) & usable(LIGHT_OIT) & usable(LYRIC_OIT)
				//#endif
				;
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

	/** @param texture the glyph sprite with mipmaps, drawn flat: it hides nothing behind it */
	public static RenderType lyricFlatMipmapped(Identifier texture) {
		return textured(Lumio.MOD_ID + "_lyric_flat_mipmapped", LYRIC_FLAT_PIPELINE, texture, true);
	}

	/** @return whether the distance field pipelines compiled, so the letters and tiles can be drawn from distances */
	public static boolean sdfAvailable() {
		boolean pipelines = available() && ModPipelines.usable(SDF_TEXT_PIPELINE) && ModPipelines.usable(SDF_TEXT_FLAT_PIPELINE) && ModPipelines.usable(SDF_GLOW_PIPELINE)
			&& ModPipelines.usable(SDF_SHAPE_PIPELINE);
		//#if 26.3
		//$ pipelines &= usable(SDF_TEXT_OIT) && usable(SDF_GLOW_OIT) && usable(SDF_SHAPE_OIT);
		//#endif
		return pipelines;
	}

	/** @param texture a distance field atlas: letters that write depth where they cover */
	public static RenderType sdfText(Identifier texture) {
		return textured(Lumio.MOD_ID + "_sdf_text", SDF_TEXT_PIPELINE, texture, false);
	}

	/** @param texture a distance field atlas: letters that write no depth (on a tile) */
	public static RenderType sdfTextFlat(Identifier texture) {
		return textured(Lumio.MOD_ID + "_sdf_text_flat", SDF_TEXT_FLAT_PIPELINE, texture, false);
	}

	/** @param texture a distance field atlas: the glow around its letters */
	public static RenderType sdfGlow(Identifier texture) {
		return textured(Lumio.MOD_ID + "_sdf_glow", SDF_GLOW_PIPELINE, texture, false);
	}

	/** @param texture any texture (it is not read): shapes with rounded corners */
	public static RenderType sdfShape(Identifier texture) {
		return textured(Lumio.MOD_ID + "_sdf_shape", SDF_SHAPE_PIPELINE, texture, false);
	}

	private static RenderType textured(String name, RenderPipeline pipeline, Identifier texture) {
		return textured(name, pipeline, texture, false);
	}

	private static RenderType textured(String name, RenderPipeline pipeline, Identifier texture, boolean mipmaps) {
		RenderType type = RenderType.create(
			name,
			RenderSetup.builder(pipeline)
				.withTexture("Sampler0", texture, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, mipmaps))
				.setOutputTarget(OutputTarget.MAIN_TARGET)
				//#if 26.3
				//$ .setOitPipelines(oitOf(pipeline))
				//#endif
				.createRenderSetup()
		);
		RenderPipeline overlay = overlayPipelineOf(pipeline);
		if (overlay != null) {
			OVERLAYS.put(type, new Overlay(overlay, texture, mipmaps));
		}
		return type;
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
				//#if 26.3
				//$ .setOitPipelines(SILHOUETTE_OIT)
				//#endif
				.createRenderSetup()
		));
	}
}
