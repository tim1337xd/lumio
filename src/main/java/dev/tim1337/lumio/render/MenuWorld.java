package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.features.visual.screen.MenusFeature;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The block world behind the main menu, rendered by the {@code menu_world} shader: terraced fields, a path, a ruined house and old field walls,
 * in the season and at the hour of the day. Its textures are the game's own ({@link MenuGameTextures}).
 * <p>
 * It is rendered at reduced size into its own texture, then a second small pass softens what is far (the depth of field), and the result is
 * drawn over the screen with filtering. The land does not move, so a new picture is made at most 30 times per second; in between the last one is
 * drawn again. While something moves (the camera at the start of the game, particles or falling snow) every frame is new. Nothing is rendered
 * while no menu shows it.
 */
public final class MenuWorld {
	/** The world's own values, the particle count, then the particles. */
	private static final int PARTICLE_COUNT_TEXEL = 7;
	private static final int PARTICLE_TEXEL = 8;
	private static final int TEXELS = PARTICLE_TEXEL + MenuParticles.MAX * MenuParticles.TEXELS_EACH;
	/** Duration of the change between seasons, in seconds. */
	private static final float SEASON_SECONDS = 1.1f;
	/** A new picture at most this often, in nanoseconds, while nothing but water and clouds moves. */
	private static final long FRAME_NANOS = 33_000_000L;
	private static final Identifier SNOW = Identifier.withDefaultNamespace("textures/environment/snow.png");

	private static final RenderPipeline PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/menu_world"))
			.withVertexShader(Lumio.id("core/menu_world"))
			.withFragmentShader(Lumio.id("core/menu_world"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").withSampler("Sampler3").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	private static final RenderPipeline FOCUS_PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/menu_world_dof"))
			.withVertexShader(Lumio.id("core/menu_world"))
			.withFragmentShader(Lumio.id("core/menu_world_dof"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	private static TextureTarget target;
	private static TextureTarget focused;
	private static GpuBuffer quad;
	private static ParameterTexture parameters;
	private static boolean failed;
	private static long lastNanos;
	private static long lastRender;
	private static float seconds;
	private static int fromSeason = -1;
	private static int toSeason = -1;
	/** When the change to the season shown started, in nanoseconds, or 0 when no change runs. */
	private static long seasonStart;
	/** Smoothed camera target following the mouse: it moves slowly so it does not step. */
	private static float smoothAcross = 0.5f;
	private static float smoothDown = 0.5f;
	private static boolean mouseKnown;
	// the camera coming down at the start of the game, from 1 (high up, looking up) to 0 (resting), as set and as last rendered
	private static float descent;
	private static float tilt;
	private static float renderedDescent;
	private static float renderedTilt;

	private MenuWorld() {
	}

	/**
	 * Sets where the camera is on its way down at the start of the game: how high it still is and how far it still looks up, both from 1 to 0.
	 * At 0 and 0 it rests at its place.
	 */
	public static void setCamera(float height, float lookUp) {
		descent = Mth.clamp(height, 0f, 1f);
		tilt = Mth.clamp(lookUp, 0f, 1f);
	}

	/** @return whether the world was drawn; if not (the shader was rejected) the caller draws something else, and it is not retried */
	public static boolean draw(GuiGraphicsExtractor graphics, float mouseAcross, float mouseDown) {
		if (failed || !texturesReady()) {
			return false;
		}
		try {
			render(graphics, mouseAcross, mouseDown);
			return true;
		} catch (Throwable error) {
			failed = true;
			Logs.warn("The world of the menus could not be drawn, the stars are used", error);
			return false;
		}
	}

	/** @return whether the game's textures are read: from the first resource load on, before any menu shows */
	private static boolean texturesReady() {
		return MenuGameTextures.view() != null && MenuGameTextures.blocksView() != null && ModPipelines.usable(PIPELINE) && ModPipelines.usable(FOCUS_PIPELINE);
	}

	/** Makes everything the world needs (targets at their size, textures, buffers) without drawing, so its first frame costs no more than the others. */
	public static void prepare() {
		if (failed) {
			return;
		}
		try {
			int divisor = ModFeatures.MENUS.worldQuality().divisor();
			var window = Minecraft.getInstance().getWindow();
			ensureResources(Math.max(2, (window.getWidth() + divisor - 1) / divisor), Math.max(2, (window.getHeight() + divisor - 1) / divisor));
		} catch (Throwable error) {
			failed = true;
			Logs.warn("The world of the menus could not be prepared", error);
		}
	}

	private static void ensureResources(int width, int height) {
		if (target == null) {
			target = new TextureTarget("Lumio menu world", width, height, true, GpuFormat.RGBA8_UNORM);
			focused = new TextureTarget("Lumio menu world in focus", width, height, true, GpuFormat.RGBA8_UNORM);
		} else if (target.width != width || target.height != height) {
			target.resize(width, height);
			focused.resize(width, height);
		}
		if (parameters == null) {
			parameters = new ParameterTexture("menu_world_parameters", TEXELS);
		}
		if (quad == null) {
			quad = makeQuad();
		}
	}

	private static void render(GuiGraphicsExtractor graphics, float mouseAcross, float mouseDown) {
		MenusFeature menus = ModFeatures.MENUS;
		Minecraft client = Minecraft.getInstance();
		long now = System.nanoTime();
		MenuParticles.update(menus.worldSeason().ordinal(), menus.worldHour(), menus.worldLights());
		// while the camera moves, the season changes, or particles or snow fall, every frame is a new picture: a picture kept for a second frame
		// made them look like 30 frames per second, and uneven on screens faster than 60 Hz
		boolean snowing = menus.worldSeason() == MenusFeature.Season.WINTER && menus.worldLights();
		boolean moving = Math.abs(descent - renderedDescent) > 1e-5f || Math.abs(tilt - renderedTilt) > 1e-5f || seasonStart != 0L
			|| toSeason >= 0 && menus.worldSeason().ordinal() != toSeason || menus.hourGliding()
			|| MenuParticles.active() || snowing;
		boolean fresh = target != null && lastRender != 0 && now - lastRender < FRAME_NANOS && !moving;
		float dt = lastNanos == 0 ? 0f : Math.min(1f / 30f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		seconds += dt * menus.backgroundSpeed();
		if (!mouseKnown) {
			smoothAcross = mouseAcross;
			smoothDown = mouseDown;
			mouseKnown = true;
		} else {
			float follow = 1f - (float) Math.exp(-dt * 2.2f);
			smoothAcross += (mouseAcross - smoothAcross) * follow;
			smoothDown += (mouseDown - smoothDown) * follow;
		}
		if (fresh) {
			// the land is still, and water and clouds need no more than 30 pictures per second: the last one is drawn again
			blit(graphics);
			return;
		}
		lastRender = now;
		renderedDescent = descent;
		renderedTilt = tilt;
		MenusFeature.WorldQuality quality = menus.worldQuality();
		int divisor = quality.divisor();
		int width = Math.max(2, (client.getWindow().getWidth() + divisor - 1) / divisor);
		int height = Math.max(2, (client.getWindow().getHeight() + divisor - 1) / divisor);
		ensureResources(width, height);

		// the season, and during a change the one before and how far the change is
		int wantedSeason = menus.worldSeason().ordinal();
		if (toSeason < 0) {
			fromSeason = toSeason = wantedSeason;
		} else if (wantedSeason != toSeason) {
			fromSeason = toSeason;
			toSeason = wantedSeason;
			seasonStart = now;
		}
		// on real time, so the change takes as long at any frame rate
		float blend = seasonStart == 0L ? 1f : Math.min(1f, (now - seasonStart) / 1_000_000_000f / SEASON_SECONDS);
		if (blend >= 1f) {
			seasonStart = 0L;
		}
		int first = blend >= 1f ? toSeason : fromSeason;
		float amount = blend >= 1f ? 0f : blend;
		writeParameters(first, toSeason, amount, menus.worldHour(), smoothAcross, smoothDown, width, height, quality, false, true);
		renderPass(target);
		focusPass(target, focused);
		drawnAt = System.nanoTime();
		blit(graphics);
	}

	/** @return the menu world as it is on the screen (upside down, as render targets are); where the menus do not draw it now, its still picture; null if it cannot be drawn at all */
	public static GpuTextureView currentView() {
		return focused != null && System.nanoTime() - drawnAt < FRESH_NANOS ? focused.getColorTextureView() : snapshot();
	}

	private static final long FRESH_NANOS = 500_000_000L;
	private static long drawnAt = Long.MIN_VALUE / 2;

	private static void blit(GuiGraphicsExtractor graphics) {
		graphics.blit(focused.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
			0, 0, graphics.guiWidth(), graphics.guiHeight(), 0f, 1f, 1f, 0f);
	}

	// ------------------------------------------------------------------------------------------------------ season pictures

	/** Size of a season's picture, in pixels, and how far the clock may move on before the pictures are made again, in hours. */
	private static final int THUMB_WIDTH = 320;
	private static final int THUMB_HEIGHT = 200;
	private static final float THUMB_HOURS = 0.5f;
	private static final TextureTarget[] THUMBS = new TextureTarget[4];
	private static final float[] THUMB_HOUR = {-1f, -1f, -1f, -1f};

	/** @return the picture of a season (upside down, as render targets are), or null if it has not been made yet */
	public static GpuTextureView thumbnail(int season) {
		return THUMBS[season] == null || THUMB_HOUR[season] < 0f ? null : THUMBS[season].getColorTextureView();
	}

	/**
	 * Makes the next missing or outdated season picture, one per call, so opening the season menu does not cost a frame four renders. Only
	 * called while the pictures are shown.
	 */
	public static void prepareThumbnails() {
		if (failed || target == null || !texturesReady()) {
			return;
		}
		float hour = ModFeatures.MENUS.worldHour();
		for (int season = 0; season < THUMBS.length; season++) {
			float gap = Math.abs(((hour - THUMB_HOUR[season] + 36f) % 24f) - 12f);
			if (THUMBS[season] != null && THUMB_HOUR[season] >= 0f && gap < THUMB_HOURS) {
				continue;
			}
			try {
				if (THUMBS[season] == null) {
					THUMBS[season] = new TextureTarget("Lumio season " + season, THUMB_WIDTH, THUMB_HEIGHT, true, GpuFormat.RGBA8_UNORM);
				}
				writeParameters(season, season, 0f, hour, 0.5f, 0.5f, THUMB_WIDTH, THUMB_HEIGHT, MenusFeature.WorldQuality.BALANCED, true, false);
				renderPass(THUMBS[season]);
				THUMB_HOUR[season] = hour;
			} catch (RuntimeException error) {
				Logs.warn("A season picture could not be made", error);
				THUMB_HOUR[season] = hour;
			}
			return;
		}
	}

	// ------------------------------------------------------------------------------------------------------ loading background

	/** Size of the loading screens' background picture, in pixels: small, as it is blurred anyway. */
	private static final int SNAPSHOT_WIDTH = 384;
	private static final int SNAPSHOT_HEIGHT = 216;
	private static TextureTarget snapshot;
	private static TextureTarget snapshotSpare;
	private static int snapshotSeason = -1;
	private static float snapshotHour = -1f;

	/**
	 * @return the background of the loading screens: the scene blurred, in the season and at the hour, upside down as render targets are. It is
	 *         made once and reused until the season changes or the clock moved on by half an hour, so loading is not slowed down by the shader.
	 *         Null if the scene cannot be drawn.
	 */
	public static GpuTextureView snapshot() {
		if (failed || !texturesReady()) {
			return null;
		}
		try {
			int season = ModFeatures.MENUS.worldSeason().ordinal();
			float hour = ModFeatures.MENUS.worldHour();
			float gap = Math.abs(((hour - snapshotHour + 36f) % 24f) - 12f);
			if (snapshot == null || season != snapshotSeason || gap > THUMB_HOURS) {
				ensureResources(target == null ? 2 : target.width, target == null ? 2 : target.height);
				if (snapshot == null) {
					snapshot = new TextureTarget("Lumio loading background", SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT, true, GpuFormat.RGBA8_UNORM);
					snapshotSpare = new TextureTarget("Lumio loading background blur", SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT, true, GpuFormat.RGBA8_UNORM);
				}
				// sharp: every pixel comes out fully out of focus, so the focus pass blurs all of it; twice for a softer picture
				writeParameters(season, season, 0f, hour, 0.5f, 0.5f, SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT, MenusFeature.WorldQuality.BALANCED, true, false);
				renderPass(snapshot);
				focusPass(snapshot, snapshotSpare);
				focusPass(snapshotSpare, snapshot);
				snapshotSeason = season;
				snapshotHour = hour;
			}
			return snapshot.getColorTextureView();
		} catch (RuntimeException error) {
			Logs.warn("The loading background could not be made", error);
			failed = true;
			return null;
		}
	}

	// ------------------------------------------------------------------------------------------------------------ rendering

	private static void writeParameters(int first, int second, float amount, float hour, float across, float down, int width, int height,
										MenusFeature.WorldQuality quality, boolean sharp, boolean withParticles) {
		MenusFeature menus = ModFeatures.MENUS;
		double kept = seconds % 65536.0;
		int whole = (int) kept;
		parameters.set(0, whole & 0xFF, whole >> 8 & 0xFF, Math.min(255, (int) ((kept - whole) * 255.0)), first);
		int acrossPacked = Math.round(Mth.clamp(across, 0f, 1f) * 65535f);
		int downPacked = Math.round(Mth.clamp(down, 0f, 1f) * 65535f);
		parameters.set(1, acrossPacked & 0xFF, acrossPacked >> 8, second, Math.round(amount * 255f));
		parameters.set(2, downPacked & 0xFF, downPacked >> 8, quality.steps(), quality.shadows() ? 255 : 0);
		parameters.set(3, width & 0xFF, width >> 8, height & 0xFF, height >> 8);
		int hourPacked = Math.round(hour / 24f * 65535f);
		parameters.set(4, hourPacked & 0xFF, hourPacked >> 8, menus.worldLights() ? 255 : 0, menus.worldStars() ? 255 : 0);
		// the camera's way down at the start, in 16 bits each, so its move has no steps
		int down16 = Math.round(sharp ? 0f : descent * 65535f);
		int tilt16 = Math.round(sharp ? 0f : tilt * 65535f);
		parameters.set(5, down16 & 0xFF, down16 >> 8, tilt16 & 0xFF, tilt16 >> 8);
		// whether it is a still picture (the season tiles and snapshots), which comes out all sharp
		parameters.set(6, 0, 0, 0, sharp ? 255 : 0);
		// the particles fly in the menu's world only, not in the season pictures or the loading background
		int particles = withParticles ? MenuParticles.write(parameters, PARTICLE_TEXEL) : 0;
		parameters.set(PARTICLE_COUNT_TEXEL, particles, 0, 0, 0);
		parameters.upload();
	}

	private static void renderPass(TextureTarget into) {
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(6);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
		// taken before the pass opens: the first time, these are written to the graphics card, which cannot happen during a pass
		var blocks = MenuGameTextures.blocksView();
		var snow = Minecraft.getInstance().getTextureManager().getTexture(SNOW).getTextureView();
		var gameTextures = MenuGameTextures.view();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "Lumio menu world", into.getColorTextureView(), Optional.empty(), into.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.bindTexture("Sampler1", blocks, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			// the game's own snow texture (from the resource packs in use), repeated as the game's weather does
			pass.bindTexture("Sampler2", snow,
				RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
			pass.bindTexture("Sampler3", gameTextures, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setVertexBuffer(0, quad.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(6, 1, 0, 0, 0);
		}
	}

	/** Softens what is far: the scene's alpha says how far out of focus every pixel is. */
	private static void focusPass(TextureTarget from, TextureTarget into) {
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(6);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "Lumio menu world focus", into.getColorTextureView(), Optional.empty(), into.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(FOCUS_PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", from.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setVertexBuffer(0, quad.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(6, 1, 0, 0, 0);
		}
	}

	/** @return a full-image quad in the pipeline's vertex format: position, texture coordinate and color */
	private static GpuBuffer makeQuad() {
		float[][] corners = {{-1f, -1f}, {1f, -1f}, {1f, 1f}, {-1f, 1f}};
		ByteBuffer data = ByteBuffer.allocateDirect(4 * 24).order(ByteOrder.nativeOrder());
		for (float[] corner : corners) {
			data.putFloat(corner[0]).putFloat(corner[1]).putFloat(0f);
			data.putFloat(corner[0] * 0.5f + 0.5f).putFloat(corner[1] * 0.5f + 0.5f);
			data.putInt(0xFFFFFFFF);
		}
		data.flip();
		return RenderSystem.getDevice().createBuffer(() -> "Lumio menu world quad", GpuBuffer.USAGE_VERTEX, data);
	}
}
