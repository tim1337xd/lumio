package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.SkyFeature;

import org.joml.Matrix4f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Renders the sky of {@link SkyFeature}.
 * <p>
 * The game draws the End sky as a box around the camera. That box is drawn here too, with a shader that computes the sky in every direction. It
 * is drawn in place of the vanilla stars, after the sky color, so the sun and moon stay on top, and it fades in and out with the night.
 * <p>
 * It uses the same calls the game uses for its sky, so it works with every graphics backend.
 */
public final class CustomSky {
	/** The End sky box has 6 faces of 2 triangles each. */
	private static final int INDICES = 36;
	private static final int TEXELS = 48;
	/** Places in the world, each a whole texel of its block (see {@link SkyCoordinates}), and the two texels of their fractions. */
	private static final int PLACE_X_TEXEL = 39;
	private static final int PLACE_Z_TEXEL = 40;
	private static final int EYE_X_TEXEL = 41;
	private static final int EYE_Z_TEXEL = 42;
	private static final int WIND_X_TEXEL = 43;
	private static final int WIND_Z_TEXEL = 44;
	private static final int PLACE_FRACTIONS_TEXEL = 45;
	private static final int WIND_FRACTIONS_TEXEL = 46;
	private static final float BLOCKS_PER_CHUNK = 16f;
	/** Animation loop length at default speed. */
	private static final float CYCLE_SECONDS = 900f;
	private static final float TAU = (float) (Math.PI * 2.0);

	private static final RenderPipeline PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	/**
	 * The smooth sky light (nebulae, aurora) is rendered at this fraction of the screen size in a separate pass and upsampled with filtering by the
	 * main sky pass. It is soft, so it looks the same at a quarter of the pixel cost. Stars are sharp and rendered at full size. The shader uses
	 * this value as well.
	 */
	private static final int LOWRES_DIVISOR = 2;
	/** Themes from this index on (the storm) have no light layer and render in a single pass. */
	private static final int FIRST_THEME_WITHOUT_LIGHT = 4;

	/** Light pass without blending: writes the light as is, into an HDR texture. */
	private static final RenderPipeline LIGHT_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_light"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky"))
			.withShaderDefine("LIGHT_PASS")
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	/** Sky pass that composites the light from the previous pass. */
	private static final RenderPipeline SPLIT_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_split"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky"))
			.withShaderDefine("SPLIT")
			.withShaderDefine("LOWRES_DIVISOR", LOWRES_DIVISOR)
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	private static TextureTarget lightTarget;

	/** What {@link #prepare} left for the last draw of the frame; no pipeline when there is nothing to draw. */
	private static RenderPipeline preparedPipeline;
	private static GpuTextureView preparedLayer;
	private static GpuBuffer preparedBox;
	private static GpuBuffer preparedIndexBuffer;
	private static RenderSystem.AutoStorageIndexBuffer preparedIndices;
	private static GpuBufferSlice preparedTransform;

	/**
	 * The storm's volumetric clouds are ray-marched at this fraction of the screen size in a separate pass: a ray per pixel marches through the
	 * clouds in many steps, too expensive for every pixel of a large screen, and soft clouds look the same upsampled. The shader uses this value as
	 * well.
	 */
	private static final int VOLUME_DIVISOR = 3;
	/** On the lowest graphics the picture of the clouds is smaller still; never a flat one. */
	private static final int VOLUME_DIVISOR_FAST = 4;

	/** Volumetric cloud pass: cloud radiance and the remaining transmittance of the sky behind, into an HDR texture. */
	private static final RenderPipeline VOLUME_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_volume"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky"))
			.withShaderDefine("VOLUME_PASS")
			// the previous frame's clouds, which the new ones are blended into
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	/**
	 * The clouds over the finished world: their light is added and what is behind them dimmed, where the world is farther than where the ray enters their layer, and the depth of the world tells that. A
	 * pass without a depth target, as it samples that depth.
	 */
	private static final RenderPipeline CLOUD_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_clouds"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky"))
			.withShaderDefine("CLOUD_COMPOSITE")
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.build()
	);

	/**
	 * The clouds over the sky, laid on right after it, before the world: everything of the world drawn later (the land, and what glows or is see-through:
	 * fireflies, lyrics, trails, particles, the weather) lies over them where it is nearer, with the depth of the scene, and nothing of it is cut by the
	 * clouds. Where land stands behind the clouds, {@link #CLOUD_PIPELINE} lays them over it at the end.
	 */
	private static final RenderPipeline CLOUD_SKY_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_clouds_over_sky"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky"))
			.withShaderDefine("CLOUD_COMPOSITE")
			.withShaderDefine("CLOUD_OVER_SKY")
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.build()
	);

	private static boolean cloudsPending;
	private static GpuTextureView cloudsView;

	/**
	 * Temporal accumulation for the clouds: every frame jitters the ray-march start and blends into the previous result, reprojected to where it was
	 * on screen then (the view has rotated since). Each pixel thus accumulates the samples of many frames, removing the noise and banding of distant
	 * steps for the cost of one frame. Two textures ping-pong: one is rendered, the other is the history.
	 */
	private static int checkedFor = -1;
	private static boolean usable;
	private static final TextureTarget[] volumeTargets = new TextureTarget[2];
	private static int volumeCurrent;
	private static int volumeFrame;
	/** View rotation of the previous frame (column-major 3x3 of the model-view matrix), or null when there is no usable history. */
	private static float[] lastRotation;
	private static long lastVolumeNanos;
	private static Vec3 lastVolumeEye = Vec3.ZERO;
	/** The history is discarded after this long without clouds (another sky, a menu), or after a move this far in blocks. */
	private static final long HISTORY_GAP_NANOS = 250_000_000L;
	private static final double HISTORY_JUMP_BLOCKS = 32.0;
	/** The most the camera's move between two frames is stored as; the history is not used past {@link #HISTORY_JUMP_BLOCKS} anyway. The shader uses it as well. */
	private static final double MOVE_RANGE_BLOCKS = 32.0;

	/**
	 * Time constant in seconds for the sky to follow the player: it covers about two thirds of the distance in this time, independent of frame rate.
	 * Position follows fastest, then height and climate, which would flicker if they changed with every step.
	 */
	private static final float FOLLOW_PLACE_SECONDS = 2.5f;
	private static final float FOLLOW_CLIMATE_SECONDS = 5f;
	/** Moves further than this in blocks are teleports or dimension changes; the sky snaps instead of traveling the distance. */
	private static final double TELEPORT_BLOCKS = 256.0;
	/**
	 * The lights are anchored in the world, but walking under them would sweep their fine rays past very quickly. They move by this fraction of the
	 * player's movement (parallax), so the sky still changes perspective as the player travels, just more calmly.
	 */
	private static final double PARALLAX = 1.0;

	private static float coldNow = 0.3f;
	private static float clearNow = 0.4f;
	private static float latitudeNow = 0.5f;
	private static double followX;
	private static double followY = 64;
	private static double followZ;
	private static boolean following;
	private static long followNanos;
	private static final AnimationClock CLOCK = new AnimationClock();
	private static ParameterTexture parameters;
	/** Elapsed sky time in seconds, scaled by the sky speed, for animations measured in seconds. */
	private static double fastSeconds;
	private static long fastNanos;
	private static double starSeconds;
	/** The aurora's clock: seconds at its speed, so its curtains drift on steadily, however the speed is set. */
	private static double auroraSeconds;

	private CustomSky() {
	}

	/**
	 * Draws the sky if it is enabled and dark enough.
	 *
	 * @param box            the box around the camera that the game draws the End sky on
	 * @param starAngle      rotation of the vanilla stars
	 * @param rainBrightness 1 in clear weather
	 * @param starBrightness brightness of the vanilla stars
	 */
	public static void draw(GpuBuffer box, RenderTarget target, float starAngle, float rainBrightness, float starBrightness) {
		prepare(box, target, starAngle, rainBrightness, starBrightness);
		if (preparedPipeline != null) {
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lumio sky", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
				drawPrepared(pass);
			}
		}
	}

	/** Draws what {@link #prepare} made ready into a pass on the screen's target; nothing when it made nothing ready. */
	public static void drawPrepared(RenderPass pass) {
		if (preparedPipeline == null) {
			return;
		}
		if (preparedLayer != null) {
			pass.bindTexture("Sampler1", preparedLayer, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		}
		drawBox(pass, preparedPipeline, preparedBox, preparedIndexBuffer, preparedIndices, preparedTransform);
		preparedPipeline = null;
		preparedLayer = null;
	}

	/**
	 * Everything of the sky of a frame but the last draw: its parameters, and the passes into textures of its own, which cannot run while a pass on the
	 * screen's target is open.
	 */
	public static void prepare(GpuBuffer box, RenderTarget target, float starAngle, float rainBrightness, float starBrightness) {
		preparedPipeline = null;
		preparedLayer = null;
		cloudsPending = false;
		SkyFeature sky = ModFeatures.SKY;
		if (!pipelinesUsable()) {
			return;
		}
		float opacity = sky.opacity(starBrightness, rainBrightness);
		boolean volumetric = sky.cloudLevel() > 0.004f;
		if (opacity < 0.004f && !volumetric) {
			return;
		}

		if (parameters == null) {
			parameters = new ParameterTexture("sky_parameters", TEXELS);
		}
		float phase = CLOCK.advance(1f, CYCLE_SECONDS);
		float turns = (starAngle / TAU) % 1f;
		int rgb = sky.rgb();
		parameters.setPacked16(0, phase, sky.themeIndex(), Math.round(opacity * 255f));
		parameters.setPacked16(1, turns < 0f ? turns + 1f : turns, Math.round(sky.brightness() * sky.shownAfterJump() * 64f), Math.round(sky.starDensity() * 255f));
		parameters.set(2, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Math.round(sky.netherMood() * 255f));
		// background: the color at the horizon and at the zenith
		float[] horizon = sky.horizonNow();
		float[] zenith = sky.zenithNow();
		parameters.set(3, Math.round(horizon[0] * 255f), Math.round(horizon[1] * 255f), Math.round(horizon[2] * 255f), 0);
		parameters.set(4, Math.round(zenith[0] * 255f), Math.round(zenith[1] * 255f), Math.round(zenith[2] * 255f), 0);
		writeLocation(sky);
		long clockNow = System.nanoTime();
		if (fastNanos == 0L) {
			fastNanos = clockNow;
		} else if (clockNow - fastNanos >= 4_000_000L) {
			double step = Math.min(0.25, (clockNow - fastNanos) / 1e9);
			fastSeconds += step;
			starSeconds += step * sky.starSpeed();
			auroraSeconds += step * sky.auroraSpeed();
			fastNanos = clockNow;
		}
		double kept = fastSeconds % 65536.0;
		int whole = (int) kept;
		parameters.set(11, whole & 0xFF, whole >> 8 & 0xFF, Math.min(255, (int) ((kept - whole) * 255.0)), 255);
		double starKept = starSeconds % 65536.0;
		int starWhole = (int) starKept;
		parameters.set(12, starWhole & 0xFF, starWhole >> 8 & 0xFF, Math.min(255, (int) ((starKept - starWhole) * 255.0)), 255);
		// the aurora's own clock, at its speed: it drifts on by it, so a change of the speed never makes it jump
		double auroraKept = auroraSeconds % 65536.0;
		int auroraWhole = (int) auroraKept;
		parameters.set(47, auroraWhole & 0xFF, auroraWhole >> 8 & 0xFF, Math.min(255, (int) ((auroraKept - auroraWhole) * 255.0)), 255);
		parameters.set(9, 0, 0, 0, volumetric ? 2 : 0);
		for (int i = 0; i < 3; i++) {
			int aurora = sky.auroraRgb(i);
			parameters.set(29 + i, aurora >> 16 & 0xFF, aurora >> 8 & 0xFF, aurora & 0xFF, 255);
		}
		writeClouds(sky);
		boolean history = false;
		Matrix4f view = RenderSystem.getModelViewMatrixCopy();
		float[] rotation = {view.m00(), view.m01(), view.m02(), view.m10(), view.m11(), view.m12(), view.m20(), view.m21(), view.m22()};
		if (volumetric) {
			Vec3 before = lastVolumeEye;
			history = prepareVolume(target);
			writeMove(history ? lastVolumeEye.subtract(before) : Vec3.ZERO);
			writeRotation(history ? lastRotation : rotation);
			parameters.set(21, volumeFrame & 0xFF, history ? 255 : 0, 0, 255);
		}
		parameters.upload();

		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(INDICES);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());

		if (volumetric) {
			// the clouds are ray-marched at reduced size and the sky is rendered over them at full size
			TextureTarget volume = volumeTargets[volumeCurrent];
			TextureTarget before = volumeTargets[1 - volumeCurrent];
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lumio sky volume", volume.getColorTextureView(), Optional.empty(), volume.getDepthTextureView(), OptionalDouble.empty())) {
				pass.bindTexture("Sampler1", before.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				drawBox(pass, VOLUME_PIPELINE, box, indexBuffer, indices, transform);
			}
		// this frame becomes the history of the next; the textures ping-pong
			lastRotation = rotation;
			volumeCurrent = 1 - volumeCurrent;
			volumeFrame++;
			cloudsView = volume.getColorTextureView();
			cloudsPending = Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.NONE;
			preparedBox = box;
			preparedIndexBuffer = indexBuffer;
			preparedIndices = indices;
			preparedTransform = transform;
		}
		if (opacity < 0.004f) {
			return;
		}

		// "detailed" quality renders everything at full size, as does the storm, which has no light layer
		boolean split = sky.quality() < 2 && sky.themeIndex() < FIRST_THEME_WITHOUT_LIGHT;
		if (split) {
			TextureTarget light = lightTarget(target);
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lumio sky light", light.getColorTextureView(), Optional.empty(), light.getDepthTextureView(), OptionalDouble.empty())) {
				drawBox(pass, LIGHT_PIPELINE, box, indexBuffer, indices, transform);
			}
		}

		if (split) {
			preparedLayer = lightTarget.getColorTextureView();
		}
		prepared(split ? SPLIT_PIPELINE : PIPELINE, box, indexBuffer, indices, transform);
	}

	/** Lays the clouds over the sky, right after the sky pass and before the world, when {@link #prepare} marched them this frame. */
	public static void drawCloudsOverSky(RenderTarget target) {
		if (cloudsPending) {
			composite(target, CLOUD_SKY_PIPELINE, "Lumio clouds over the sky");
		}
	}

	/** Lays the clouds over the land behind them, and their shadow on it, at the end of the world. */
	public static void drawClouds(RenderTarget target) {
		if (!cloudsPending) {
			return;
		}
		cloudsPending = false;
		composite(target, CLOUD_PIPELINE, "Lumio clouds");
	}

	private static void composite(RenderTarget target, RenderPipeline pipeline, String name) {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> name, target.getColorTextureView(), Optional.empty())) {
			pass.bindTexture("Sampler1", cloudsView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.bindTexture("Sampler2", target.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			drawBox(pass, pipeline, preparedBox, preparedIndexBuffer, preparedIndices, preparedTransform);
		}
	}

	/** Stores the look of the clouds in texels 22 to 28, and how far the fog reaches. */
	private static void writeClouds(SkyFeature sky) {
		int sunX = signed16(sky.sunX());
		int sunY = signed16(sky.sunY());
		parameters.set(22, sunX & 0xFF, sunX >> 8, sunY & 0xFF, sunY >> 8);
		parameters.set(23, Math.round(sky.rainNow() * 255f), Math.round(sky.rainDarkness() * 255f), Math.round(sky.shadowStrength() * 255f), (sky.lightAndShadows() ? 1 : 0) | (sky.sunsetGlow() ? 2 : 0));
		parameters.set(24, Math.round(sky.cover() * 255f), sky.gaps().ordinal() * 127, Math.round(sky.gapSize() * 255f), Math.round(sky.cloudSize() * 255f));
		int bottom = Mth.clamp(Math.round((sky.cloudBottom() + 512f) * 16f), 0, 65535);
		parameters.set(25, Math.round(sky.puffiness() * 255f), Math.round(sky.thickness() * 255f), bottom & 0xFF, bottom >> 8);
		parameters.set(26, 0, 0, 0, Math.round(sky.cloudLevel() * 255f));
		setPlace(WIND_X_TEXEL, sky.windX());
		setPlace(WIND_Z_TEXEL, sky.windZ());
		parameters.set(WIND_FRACTIONS_TEXEL, SkyCoordinates.fraction(sky.windX()), SkyCoordinates.fraction(sky.windZ()), 0, 255);
		// the land fades out by the fog or at the end of the render distance, whichever is nearer
		int fogEnd = Mth.clamp(Math.round(Math.min(FogState.end(), Minecraft.getInstance().options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK)), 0, 65535);
		parameters.set(28, fogEnd & 0xFF, fogEnd >> 8, sky.auroraColorCount(), 255);
		// the light the clouds are lit by is the sky as shown, and the game's color of the low sun
		float[] ambient = sky.ambient();
		parameters.set(35, Math.round(ambient[0] * 255f), Math.round(ambient[1] * 255f), Math.round(ambient[2] * 255f), 255);
		float[] sunset = sky.sunsetRgb();
		parameters.set(36, Math.round(sunset[0] * 255f), Math.round(sunset[1] * 255f), Math.round(sunset[2] * 255f), 255);
		// the strike of lightning now: where round the player and how far, how bright, and whether a bolt goes down
		var strike = sky.strikes();
		int around = Math.round(strike.azimuth() / (float) (Math.PI * 2.0) * 65535f) & 0xFFFF;
		parameters.set(37, around & 0xFF, around >> 8, Mth.clamp(Math.round(strike.distance() / 4f), 0, 255), Math.round(Math.min(1f, strike.flash() * sky.cloudLevel()) * 255f));
		parameters.set(38, strike.bolt() ? 255 : 0, Math.round(strike.seed()) & 0xFF, 0, 255);
		int tint = sky.cloudTintRgb();
		parameters.set(32, tint >> 16 & 0xFF, tint >> 8 & 0xFF, tint & 0xFF, Math.round(sky.cloudTintShare() * 255f));
	}

	/** @return whether all of the sky's pipelines can be bound; without them the game's own sky shows. Kept until the shaders reload, as this is asked every frame. */
	private static boolean pipelinesUsable() {
		if (checkedFor != ModPipelines.generation()) {
			checkedFor = ModPipelines.generation();
			usable = ModPipelines.usable(PIPELINE) & ModPipelines.usable(LIGHT_PIPELINE) & ModPipelines.usable(SPLIT_PIPELINE) & ModPipelines.usable(VOLUME_PIPELINE)
				& ModPipelines.usable(CLOUD_PIPELINE) & ModPipelines.usable(CLOUD_SKY_PIPELINE);
		}
		return usable;
	}

	private static void prepared(RenderPipeline pipeline, GpuBuffer box, GpuBuffer indexBuffer, RenderSystem.AutoStorageIndexBuffer indices, GpuBufferSlice transform) {
		preparedPipeline = pipeline;
		preparedBox = box;
		preparedIndexBuffer = indexBuffer;
		preparedIndices = indices;
		preparedTransform = transform;
	}

	private static void drawBox(RenderPass pass, RenderPipeline pipeline, GpuBuffer box, GpuBuffer indexBuffer,
								RenderSystem.AutoStorageIndexBuffer indices, GpuBufferSlice transform) {
		pass.setPipeline(pipeline);
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", transform);
		pass.bindTexture("Sampler0", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
		pass.setVertexBuffer(0, box.slice());
		pass.setIndexBuffer(indexBuffer, indices.type());
		pass.drawIndexed(INDICES, 1, 0, 0, 0);
	}

	/** @return the light texture, sized to the screen divided by {@link #LOWRES_DIVISOR}, rounded up, as the shader expects */
	private static TextureTarget lightTarget(RenderTarget screen) {
		int width = Math.max(1, (screen.width + LOWRES_DIVISOR - 1) / LOWRES_DIVISOR);
		int height = Math.max(1, (screen.height + LOWRES_DIVISOR - 1) / LOWRES_DIVISOR);
		if (lightTarget == null) {
			lightTarget = new TextureTarget("Lumio sky light", width, height, true, GpuFormat.RGBA16_FLOAT);
		} else if (lightTarget.width != width || lightTarget.height != height) {
			lightTarget.resize(width, height);
		}
		return lightTarget;
	}

	/**
	 * Sizes both volumetric cloud textures to the screen divided by {@link #VOLUME_DIVISOR}, rounded up, as the shader expects.
	 *
	 * @return whether the history is usable: present, of this size, recent, and from roughly the same position
	 */
	private static boolean prepareVolume(RenderTarget screen) {
		int divisor = GraphicsQuality.isFast() ? VOLUME_DIVISOR_FAST : VOLUME_DIVISOR;
		int width = Math.max(1, (screen.width + divisor - 1) / divisor);
		int height = Math.max(1, (screen.height + divisor - 1) / divisor);
		boolean usable = lastRotation != null;
		for (int i = 0; i < 2; i++) {
			if (volumeTargets[i] == null) {
				volumeTargets[i] = new TextureTarget("Lumio sky volume " + i, width, height, true, GpuFormat.RGBA16_FLOAT);
				usable = false;
			} else if (volumeTargets[i].width != width || volumeTargets[i].height != height) {
				volumeTargets[i].resize(width, height);
				usable = false;
			}
		}
		long now = System.nanoTime();
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		if (now - lastVolumeNanos > HISTORY_GAP_NANOS || eye.distanceToSqr(lastVolumeEye) > HISTORY_JUMP_BLOCKS * HISTORY_JUMP_BLOCKS) {
			usable = false;
		}
		lastVolumeNanos = now;
		lastVolumeEye = eye;
		return usable;
	}

	/** Stores how far the camera moved since the frame of the history in texels 33 and 34, 16 bits per axis, up to {@link #MOVE_RANGE_BLOCKS} each way. */
	private static void writeMove(Vec3 move) {
		int x = signed16((float) (move.x / MOVE_RANGE_BLOCKS));
		int y = signed16((float) (move.y / MOVE_RANGE_BLOCKS));
		int z = signed16((float) (move.z / MOVE_RANGE_BLOCKS));
		parameters.set(33, x & 0xFF, x >> 8, y & 0xFF, y >> 8);
		parameters.set(34, z & 0xFF, z >> 8, 0, 255);
	}

	/** Stores the view rotation in texels 16 to 20: two values from -1 to 1 per texel, 16 bits each. */
	private static void writeRotation(float[] rotation) {
		for (int i = 0; i < 5; i++) {
			int a = signed16(rotation[i * 2]);
			int b = i * 2 + 1 < rotation.length ? signed16(rotation[i * 2 + 1]) : 0;
			parameters.set(16 + i, a & 0xFF, a >> 8, b & 0xFF, b >> 8);
		}
	}

	private static int signed16(float value) {
		return Math.round((Math.clamp(value, -1f, 1f) * 0.5f + 0.5f) * 65535f);
	}

	/**
	 * Stores the player context for the sky: world position, biome temperature, height and how far north. The positions are passed whole, see {@link SkyCoordinates}.
	 */
	private static void writeLocation(SkyFeature sky) {
		Minecraft client = Minecraft.getInstance();
		Vec3 position = client.gameRenderer.mainCamera().position();
		long now = System.nanoTime();
		float seconds = following ? Math.min((now - followNanos) / 1e9f, 0.25f) : 0f;
		followNanos = now;
		float place = 1f - (float) Math.exp(-seconds / FOLLOW_PLACE_SECONDS);
		float climate = 1f - (float) Math.exp(-seconds / FOLLOW_CLIMATE_SECONDS);

		float targetCold = 0.3f;
		double targetX = 0;
		double targetZ = 0;
		double targetY = 64;
		if (client.level != null) {
			targetX = sky.skyEye().x();
			targetZ = sky.skyEye().z();
			targetY = sky.skyEye().y();
		}
		if (!following || Math.abs(targetX - followX) > TELEPORT_BLOCKS || Math.abs(targetZ - followZ) > TELEPORT_BLOCKS) {
			followX = targetX;
			followZ = targetZ;
			followY = targetY;
			following = true;
		}
		followX += (targetX - followX) * place;
		followZ += (targetZ - followZ) * place;
		followY += (targetY - followY) * climate;

		// the sky composition follows the smoothed position, not the camera's current one
		float targetClear = Mth.clamp((float) (followY - 40.0) / 220f, 0f, 1f);
		float targetLatitude = Mth.clamp((float) (0.5 - followZ / 12000.0), 0f, 1f);
		coldNow += (targetCold - coldNow) * climate;
		clearNow += (targetClear - clearNow) * climate;
		latitudeNow += (targetLatitude - latitudeNow) * climate;

		double x = followX * PARALLAX;
		double z = followZ * PARALLAX;
		parameters.set(5, 0, 0, 0, Math.round(coldNow * 255f));
		parameters.set(6, 0, 0, 0, Math.round(clearNow * 255f));
		setPlace(PLACE_X_TEXEL, x);
		setPlace(PLACE_Z_TEXEL, z);
		int height = Mth.clamp((int) Math.round((followY + 512.0) * 32.0), 0, 65535);
		parameters.set(7, height & 0xFF, height >> 8, sky.quality(), Math.round(latitudeNow * 255f));
		// The exact current camera position for the volumetric clouds, which are anchored in the world: the player passes them while walking or
		// flying and can fly into them. Neither smoothed nor scaled by parallax like the sky lights.
		// The sky's own eye takes the jumps of warps out, which suits the lights; the clouds stand in the world and take nothing out: they are where they
		// are, so coming back to a place shows the same ones, and a warp or a new height never moves them.
		double eyeX = position.x;
		double eyeZ = position.z;
		setPlace(EYE_X_TEXEL, eyeX);
		setPlace(EYE_Z_TEXEL, eyeZ);
		parameters.set(PLACE_FRACTIONS_TEXEL, SkyCoordinates.fraction(x), SkyCoordinates.fraction(z), SkyCoordinates.fraction(eyeX), SkyCoordinates.fraction(eyeZ));
		int exactHeight = Mth.clamp((int) Math.round((position.y + 512.0) * 32.0), 0, 65535);
		parameters.set(15, exactHeight & 0xFF, exactHeight >> 8, 0, 255);
	}

	/** Stores the block of a place in the world in one texel; its fraction goes into a fraction texel. */
	private static void setPlace(int texel, double coordinate) {
		long whole = SkyCoordinates.whole(coordinate);
		parameters.set(texel, SkyCoordinates.wholeByte(whole, 0), SkyCoordinates.wholeByte(whole, 1), SkyCoordinates.wholeByte(whole, 2), SkyCoordinates.wholeByte(whole, 3));
	}
}
