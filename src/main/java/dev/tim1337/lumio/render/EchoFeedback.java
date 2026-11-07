package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Logs;

import org.joml.Matrix4f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.level.Level;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Radiance's Echo as an after-glow of the first person item and hands, made on the screen after the hand pass. The hand pass leaves depth only where
 * they are (the game clears it before), which is their silhouette. A picture as large as the screen keeps the after-glow, in two that take
 * turns: each frame the lines fade, the sheet fades and the silhouette is stamped in faintly, and what was there is smeared along the item's
 * motion; where the item moved since the frame before, by a swing, a turn, a step or its sway, each point of its edge draws a streak from there to
 * here (see RadianceEcho). Then it is laid over the screen: a smooth glow in the color around the item and hands (from a distance field, see
 * EchoGlow: a grown band of them, waved and blurred twice, as the reference makes it), added
 * over the screen, and added on top a bright core, the crisp rim of now and the lines, soft and bright in a wide light over the faint sheet;
 * nothing where the item and hands are. It is drawn under any screen, as the world is. The fading
 * follows the time, not the frames. Nothing of it is made while the look is off.
 */
public final class EchoFeedback {
	/** One texel per value block: decays; flags, core width and strength; the color; the core's color; the motion; the shimmer's time and amount; the lean; the width, whether the item moves and whether a trail is left; the item's anchor on the screen; the patches' time and threshold. */
	private static final int PARAMETER_TEXELS = 10;
	/** The glow leans against the recent motion: as far as the item moves in this time, at most this many pixels of a 1080 line screen, following it over this time. */
	private static final float LEAN_SHARE_SECONDS = 0.06f;
	private static final float LEAN_MOST_PIXELS = 6f;
	private static final double LEAN_SECONDS = 0.15;
	/** How long the lines and the sheet take to fade (to a third of their light) at the Echo length factor 1 (the setting at 50 %). */
	private static final float LINE_FADE_SECONDS = 0.04f;
	private static final float SHEET_FADE_SECONDS = 0.04f;
	/**
	 * Every new copy of the outline dims the trail before it by this much, so the oldest drop out first and about ten copies are seen at most
	 * (the tenth before the newest keeps about a tenth of its light).
	 */
	static final float COPY_FADE = 0.8f;
	/** The width setting is passed in hundredths. */
	private static final float WIDTH_STEPS = 100f;
	/** The glow's lobes loop over this many seconds (ten pi): their speeds are whole turns of it, so the loop has no seam. */
	private static final double SHIMMER_PERIOD_SECONDS = 10.0 * Math.PI;
	/** The sheet's blur in the shader is set for a frame of this length; a shorter frame blurs less, so it blurs as much at every frame rate. */
	private static final double BLUR_FRAME_SECONDS = 0.05;
	/** The motion is kept in quarter pixels around the middle of 16 bits. */
	private static final float MOTION_STEPS = 4f;
	private static final int MOTION_ZERO = 32768;
	/** At most this many lines between two frames: three texels each, after a header. */
	public static final int MOST_LINES = 2048;
	/** How wide the lines of the trail are, in pixels of a 1080 line screen. */
	private static final float CORE_PIXELS = 1.75f;
	private static final float REFERENCE_LINES = 1080f;
	/** How much white is in the core of the lines, against the color. */
	private static final float CORE_WHITE = 0.65f;
	/** After a pause this long (a menu, a frame that took long) the after-glow starts anew. */
	private static final double GAP_SECONDS = 0.25;
	private static final double LONGEST_FRAME_SECONDS = 0.1;
	/** The trail is seen as gone this many fade times after the last line or sheet went in: its light is then below a thousandth. */
	private static final float TRAIL_GONE_FADES = 8f;
	/** After the last ask the after-glow fades on for this many times the time a line takes to fade. */
	private static final float FADE_OUT_LINES = 5f;
	private static final Matrix4f NO_TRANSFORM = new Matrix4f();
	private static final Supplier<String> NAME = () -> "Lumio echo";

	private static final RenderPipeline ACCUMULATE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/echo_accumulate"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/echo_accumulate"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
			.build()
	);

	private static final RenderPipeline LINES = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/echo_segment"))
			.withVertexShader(Lumio.id("core/echo_segment"))
			.withFragmentShader(Lumio.id("core/echo_segment"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
			.build()
	);

	private static final RenderPipeline COMPOSITE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/echo_composite"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/echo_composite"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").withSampler("Sampler3").withSampler("Sampler4").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.build()
	);

	private static final TextureTarget[] targets = new TextureTarget[2];
	private static int current;
	private static ParameterTexture parameters;
	private static ParameterTexture lineData;
	private static GpuBuffer lineQuads;
	private static int lineCount;
	private static long lastNanos;
	/** Until when the trail may still hold light; after it, its picture holds nothing that could be seen, and it is neither faded nor read. */
	private static long trailUntil;
	private static Level lastLevel;
	private static boolean failed;

	/** The first person hand's field of view, from the hand pass, in degrees. */
	private static float handFov = 70f;
	/** When Radiance last asked for the after-glow; for a short while after, it goes on fading without anything new. */
	private static long lastAsked;

	/** What the after-glow is to look like this frame, set by Radiance before the hand pass ends. */
	private static boolean wanted;
	private static boolean stamp;
	private static int rgb;
	private static float strength;
	private static float width;
	private static float lineSeconds;
	private static float sheetSeconds;
	private static float shimmer;
	private static boolean moving;
	/** How many copies of the outline this frame left: each dims the trail before it. */
	private static int copies;
	/** How far the item moved on the screen since the frame before, in pixels of the screen; the trail is smeared along it. */
	private static float motionX;
	private static float motionY;
	/** Where the waves lean, in pixels of the screen: against the recent motion, followed smoothly. */
	private static float leanX;
	private static float leanY;
	/** Where the item is on the screen, from -1 to 1: the glow's patches move with it (see EchoPatches). */
	private static float anchorX;
	private static float anchorY;
	/** The screen bounds of the items drawn this frame (from -1 to 1), and whether the arm is in the hand pass: then the bounds are not known. */
	private static float boundsLeft = Float.MAX_VALUE;
	private static float boundsBottom = Float.MAX_VALUE;
	private static float boundsRight = -Float.MAX_VALUE;
	private static float boundsTop = -Float.MAX_VALUE;
	private static boolean armDrawn;
	/** How far the glow reaches out of the item at most, in pixels of a 1080 line screen at the width 1: its band (20), the waves (16), the shift (9),
	 * the wide blur (2.5 sigmas of 18) and the rim, with room to spare. */
	private static final float GLOW_REACH_PIXELS = 110f;
	/** The glow region of this frame in screen pixels from the bottom left (x, y, width, height), reused. */
	private static final int[] REGION = new int[4];

	private EchoFeedback() {
	}

	/**
	 * Asks for the after-glow this frame.
	 *
	 * @param stampNow   whether the item and hands of now go into it (else what is there only fades)
	 * @param color      its color
	 * @param light      how bright, from 0 to 1
	 * @param glowWidth  the width setting: how far the glow reaches, 1 for the usual
	 * @param lengthFactor how many times longer than at the factor 1 the trail lasts, from the Echo length setting
	 * @param shimmerAmount how much the slow shimmer moves the light, from 0 (none) to 1
	 */
	public static void want(boolean stampNow, int color, float light, float glowWidth, float lengthFactor, float shimmerAmount) {
		lastAsked = System.nanoTime();
		wanted = true;
		stamp = stampNow;
		rgb = color & 0xFFFFFF;
		strength = light;
		width = glowWidth;
		lineSeconds = LINE_FADE_SECONDS * lengthFactor;
		sheetSeconds = SHEET_FADE_SECONDS * lengthFactor;
		shimmer = shimmerAmount;
	}

	/** Whether an item moved on the screen this frame: only then the sheet between the lines is laid. Either hand moving is enough. */
	public static void moving(boolean now) {
		moving |= now;
	}

	/** This frame left so many copies of the outline: the trail before them dims for each (see {@link #COPY_FADE}). */
	public static void copies(int count) {
		copies += count;
	}

	/** The item moved from one point of the screen to another (as {@link #onScreen} gives them) since the frame before: the after-glow is smeared along it. */
	public static void motionBetween(float[] from, float[] to) {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		motion((to[0] - from[0]) * 0.5f * main.width, (to[1] - from[1]) * 0.5f * main.height);
	}

	/** The item is at this point of the screen (from -1 to 1), as {@link #onScreen} gives it: the glow's patches are anchored to it. */
	public static void anchor(float x, float y) {
		anchorX = x;
		anchorY = y;
	}

	/** @return a point of the screen (from -1 to 1) relative to the anchor, in pixels of a 1080 line screen, into {@code out} at {@code at} */
	public static void relativeToAnchor(float x, float y, float[] out, int at) {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		float perPixel = REFERENCE_LINES / Math.max(1, main.height);
		out[at] = (x - anchorX) * 0.5f * main.width * perPixel;
		out[at + 1] = (y - anchorY) * 0.5f * main.height * perPixel;
	}

	/** An item drawn this frame lies within these screen bounds (from -1 to 1): the glow is only made around them. */
	public static void itemBounds(float left, float bottom, float right, float top) {
		boundsLeft = Math.min(boundsLeft, left);
		boundsBottom = Math.min(boundsBottom, bottom);
		boundsRight = Math.max(boundsRight, right);
		boundsTop = Math.max(boundsTop, top);
	}

	/** The first person arm is drawn this frame: it is in the hand pass, and its bounds are not known, so the glow is made over the whole screen. */
	public static void armDrawn() {
		armDrawn = true;
	}

	/**
	 * @return the region the glow can reach this frame, in {@link #REGION}, or null for the whole screen: while a trail is left (it can lie anywhere),
	 *         with the arm in the hand pass, or without bounds. The bounds are used up.
	 */
	private static int[] glowRegion(RenderTarget main, boolean trail) {
		boolean known = !trail && !armDrawn && boundsLeft <= boundsRight && boundsBottom <= boundsTop;
		float reach = GLOW_REACH_PIXELS * Math.max(1f, width) * main.height / REFERENCE_LINES;
		int x0 = Math.max(0, (int) Math.floor((boundsLeft + 1f) * 0.5f * main.width - reach));
		int y0 = Math.max(0, (int) Math.floor((boundsBottom + 1f) * 0.5f * main.height - reach));
		int x1 = Math.min(main.width, (int) Math.ceil((boundsRight + 1f) * 0.5f * main.width + reach));
		int y1 = Math.min(main.height, (int) Math.ceil((boundsTop + 1f) * 0.5f * main.height + reach));
		boundsLeft = Float.MAX_VALUE;
		boundsBottom = Float.MAX_VALUE;
		boundsRight = -Float.MAX_VALUE;
		boundsTop = -Float.MAX_VALUE;
		armDrawn = false;
		if (!known || x1 <= x0 || y1 <= y0) {
			return null;
		}
		REGION[0] = x0;
		REGION[1] = y0;
		REGION[2] = x1 - x0;
		REGION[3] = y1 - y0;
		return REGION;
	}

	/** The item moved this far on the screen since the frame before, in pixels: the after-glow is smeared along it. */
	public static void motion(float dxPixels, float dyPixels) {
		motionX = dxPixels;
		motionY = dyPixels;
	}

	/** The hand pass begins with this field of view, in degrees. */
	public static void handFov(float degrees) {
		handFov = degrees;
	}

	/**
	 * Puts a point of the hand pass on the screen, from -1 to 1, as the game draws it there.
	 *
	 * @param modelView the hand pass's model view matrix at the time the point was placed
	 * @return false if it is behind the camera
	 */
	public static boolean onScreen(Matrix4f modelView, float x, float y, float z, float[] out) {
		float vx = modelView.m00() * x + modelView.m10() * y + modelView.m20() * z + modelView.m30();
		float vy = modelView.m01() * x + modelView.m11() * y + modelView.m21() * z + modelView.m31();
		float vz = modelView.m02() * x + modelView.m12() * y + modelView.m22() * z + modelView.m32();
		if (vz > -1.0e-3f) {
			return false;
		}
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		float focal = (float) (1.0 / Math.tan(Math.toRadians(handFov) / 2.0));
		float aspect = main.width / (float) Math.max(1, main.height);
		out[0] = focal / aspect * vx / -vz;
		out[1] = focal * vy / -vz;
		return true;
	}

	/** @return how far apart two points on the screen (from -1 to 1) are, in pixels of a 1080 line screen */
	public static float pixelsBetween(float[] a, float[] b) {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		float dx = (a[0] - b[0]) * 0.5f * main.width;
		float dy = (a[1] - b[1]) * 0.5f * main.height;
		return (float) Math.sqrt(dx * dx + dy * dy) * REFERENCE_LINES / Math.max(1, main.height);
	}

	/**
	 * Adds a line between two frames, its ends on the screen from -1 to 1.
	 *
	 * @param light how much of its light is left, from 0 to 1
	 */
	public static void addLine(float x0, float y0, float x1, float y1, float light) {
		if (lineCount >= MOST_LINES || lineData == null) {
			return;
		}
		int texel = 1 + lineCount * 3;
		putPoint(texel, x0, y0);
		putPoint(texel + 1, x1, y1);
		lineData.set(texel + 2, Math.round(Math.clamp(light, 0f, 1f) * 255f), Math.round(Math.clamp(coreHalfPixels(), 0f, 15f) * 16f), 0, 255);
		lineCount++;
	}

	private static void putPoint(int texel, float x, float y) {
		int px = Math.round(Math.clamp((x + 2f) / 4f, 0f, 1f) * 65535f);
		int py = Math.round(Math.clamp((y + 2f) / 4f, 0f, 1f) * 65535f);
		lineData.set(texel, px & 0xFF, px >> 8, py & 0xFF, py >> 8);
	}

	/** @return half the core's width in pixels of the after-glow's picture, which is as large as the screen */
	private static float coreHalfPixels() {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		return CORE_PIXELS * main.height / REFERENCE_LINES * 0.5f;
	}

	/** Called at the end of the hand pass: makes the after-glow of this frame and lays it over the screen, if Radiance asked for it. */
	public static void render() {
		boolean asked = wanted;
		wanted = false;
		int lines = lineCount;
		lineCount = 0;
		Minecraft client = Minecraft.getInstance();
		// asked for no more: what is there fades out for a short while, nothing new comes in, so it never stops at once
		boolean fading = !asked && lastNanos != 0L && System.nanoTime() - lastAsked < FADE_OUT_LINES * lineSeconds * 1e9;
		if (fading) {
			stamp = false;
		}
		if (!asked && !fading || failed || client.gui.hud.isHidden() || client.level == null) {
			// nothing of it now: it starts anew next time
			lastNanos = 0L;
			return;
		}
		if (!ModPipelines.usable(ACCUMULATE) || !ModPipelines.usable(LINES) || !ModPipelines.usable(COMPOSITE) || !EchoGlow.usable()) {
			return;
		}
		try {
			RenderTarget main = client.gameRenderer.mainRenderTarget();
			boolean fresh = ensureResources(main) || client.level != lastLevel;
			lastLevel = client.level;
			long now = System.nanoTime();
			double seconds = lastNanos == 0L ? 0.0 : (now - lastNanos) / 1e9;
			fresh |= lastNanos == 0L || seconds > GAP_SECONDS;
			lastNanos = now;
			seconds = Math.min(seconds, LONGEST_FRAME_SECONDS);
			if (lines > 0 || moving && stamp) {
				trailUntil = now + (long) (TRAIL_GONE_FADES * Math.max(lineSeconds, sheetSeconds) * 1e9);
			}
			boolean trail = !fresh && now < trailUntil;
			int[] region = glowRegion(main, trail);
			writeParameters(seconds, fresh, trail, main);

			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(NO_TRANSFORM);
			GpuBuffer quad = GlassPanes.fullQuad();
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			TextureTarget before = targets[current];
			TextureTarget into = before;
			// a trail that has faded out is left as it is; a fresh start still clears it once
			if (trail || fresh) {
				into = targets[1 - current];
				current = 1 - current;
				try (RenderPass pass = encoder.createRenderPass(NAME, into.getColorTextureView(), Optional.empty())) {
					pass.setPipeline(ACCUMULATE);
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("DynamicTransforms", transform);
					pass.bindTexture("Sampler0", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
					pass.bindTexture("Sampler1", before.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
					pass.bindTexture("Sampler2", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
					pass.setVertexBuffer(0, quad.slice());
					pass.setIndexBuffer(indices.getBuffer(6), indices.type());
					pass.drawIndexed(6, 1, 0, 0, 0);
				}
			}
			if (lines > 0 && !fresh) {
				lineData.set(0, into.width & 0xFF, into.width >> 8, into.height & 0xFF, into.height >> 8);
				lineData.upload();
				try (RenderPass pass = encoder.createRenderPass(NAME, into.getColorTextureView(), Optional.empty())) {
					pass.setPipeline(LINES);
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("DynamicTransforms", transform);
					pass.bindTexture("Sampler0", lineData.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
					pass.setVertexBuffer(0, lineQuads.slice());
					pass.setIndexBuffer(indices.getBuffer(6 * lines), indices.type());
					pass.drawIndexed(6 * lines, 1, 0, 0, 0);
				}
			}
			if (stamp) {
				// the glow around the item and hands of now: a band of them, waved and blurred twice
				EchoGlow.build(encoder, main, transform, parameters.view(), width, region);
			}
			try (RenderPass pass = encoder.createRenderPass(NAME, main.getColorTextureView(), Optional.empty())) {
				pass.setPipeline(COMPOSITE);
				if (region != null) {
					// nothing of the glow or the rim lies outside it, and no trail is left
					pass.enableScissor(region[0], region[1], region[2], region[3]);
				}
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.bindTexture("Sampler0", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.bindTexture("Sampler1", into.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.bindTexture("Sampler2", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				// bilinear, so the glow made from it has no steps of the half sized picture
				pass.bindTexture("Sampler3", EchoGlow.narrow(main), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.bindTexture("Sampler4", EchoGlow.wide(main), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.setVertexBuffer(0, quad.slice());
				pass.setIndexBuffer(indices.getBuffer(6), indices.type());
				pass.drawIndexed(6, 1, 0, 0, 0);
			}
		} catch (RuntimeException e) {
			failed = true;
			Logs.warn("Radiance's Echo could not be drawn, and is left out", e);
		}
	}

	private static void writeParameters(double seconds, boolean fresh, boolean trail, RenderTarget main) {
		float decayLines = (float) Math.exp(-seconds / Math.max(0.01f, lineSeconds));
		float decaySheet = (float) Math.exp(-seconds / Math.max(0.01f, sheetSeconds));
		float byCopies = (float) Math.pow(COPY_FADE, copies);
		copies = 0;
		decayLines *= byCopies;
		decaySheet *= byCopies;
		int lines = Math.round(decayLines * 65535f);
		int sheet = Math.round(decaySheet * 65535f);
		parameters.set(0, lines & 0xFF, lines >> 8, sheet & 0xFF, sheet >> 8);
		float core = CORE_PIXELS * main.height / REFERENCE_LINES;
		parameters.set(1, stamp ? 255 : 0, fresh ? 255 : 0, Math.round(Math.clamp(core, 0f, 15.9f) * 16f), Math.round(Math.clamp(strength, 0f, 1f) * 255f));
		parameters.set(2, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255);
		parameters.set(3, whiter(rgb >> 16 & 0xFF), whiter(rgb >> 8 & 0xFF), whiter(rgb & 0xFF), 255);
		float most = LEAN_MOST_PIXELS * main.height / REFERENCE_LINES;
		float follow = (float) (1.0 - Math.exp(-seconds / LEAN_SECONDS));
		// against the speed, not the motion of a frame, so it leans as far at every frame rate
		float perSecond = seconds > 0.0 ? (float) (LEAN_SHARE_SECONDS / seconds) : 0f;
		leanX += (Math.clamp(-motionX * perSecond, -most, most) - leanX) * follow;
		leanY += (Math.clamp(-motionY * perSecond, -most, most) - leanY) * follow;
		int lx = Math.clamp(Math.round(leanX * MOTION_STEPS) + MOTION_ZERO, 0, 65535);
		int ly = Math.clamp(Math.round(leanY * MOTION_STEPS) + MOTION_ZERO, 0, 65535);
		parameters.set(6, lx & 0xFF, lx >> 8, ly & 0xFF, ly >> 8);
		int mx = Math.clamp(Math.round(motionX * MOTION_STEPS) + MOTION_ZERO, 0, 65535);
		int my = Math.clamp(Math.round(motionY * MOTION_STEPS) + MOTION_ZERO, 0, 65535);
		parameters.set(4, mx & 0xFF, mx >> 8, my & 0xFF, my >> 8);
		parameters.set(7, Math.round(Math.clamp(width * WIDTH_STEPS, 0f, 255f)), moving && stamp ? 255 : 0, trail ? 255 : 0, 255);
		moving = false;
		motionX = 0f;
		motionY = 0f;
		int phase = (int) Math.round((System.nanoTime() / 1e9 % SHIMMER_PERIOD_SECONDS) / SHIMMER_PERIOD_SECONDS * 65535.0);
		parameters.set(5, phase & 0xFF, phase >> 8, Math.round(Math.clamp(shimmer, 0f, 1f) * 255f), (int) Math.round(Math.clamp(seconds / BLUR_FRAME_SECONDS, 0.0, 1.0) * 255.0));
		// the patches: where the item is, in quarter pixels of a 1080 line screen, and the noise's time, with the threshold kept so about a third glows
		float perPixel = REFERENCE_LINES / Math.max(1, main.height);
		int ax = Math.clamp(Math.round((anchorX + 1f) * 0.5f * main.width * perPixel * MOTION_STEPS) + MOTION_ZERO, 0, 65535);
		int ay = Math.clamp(Math.round((anchorY + 1f) * 0.5f * main.height * perPixel * MOTION_STEPS) + MOTION_ZERO, 0, 65535);
		parameters.set(8, ax & 0xFF, ax >> 8, ay & 0xFF, ay >> 8);
		double patchPhase = System.nanoTime() / 1e9 % EchoPatches.PERIOD_SECONDS / EchoPatches.PERIOD_SECONDS;
		float leanMost = LEAN_MOST_PIXELS * main.height / REFERENCE_LINES;
		EchoPatches.follow(seconds, patchPhase, leanX / leanMost, leanY / leanMost, width);
		int patchTime = (int) Math.round(patchPhase * 65535.0);
		parameters.set(9, patchTime & 0xFF, patchTime >> 8, Math.round(Math.clamp(EchoPatches.shift() + 0.5f, 0f, 1f) * 255f), 255);
		parameters.upload();
	}

	private static int whiter(int channel) {
		return Math.round(channel + (255 - channel) * CORE_WHITE);
	}

	/** @return whether the pictures were made anew (the first time, or for another size of the screen): the after-glow starts empty then */
	private static boolean ensureResources(RenderTarget main) {
		if (parameters == null) {
			parameters = new ParameterTexture("echo_parameters", PARAMETER_TEXELS);
			lineData = new ParameterTexture("echo_lines", 1 + MOST_LINES * 3);
			lineQuads = lineQuads();
		}
		int width = main.width;
		int height = main.height;
		if (targets[0] != null && targets[0].width == width && targets[0].height == height) {
			return false;
		}
		for (int i = 0; i < targets.length; i++) {
			if (targets[i] != null) {
				targets[i].destroyBuffers();
			}
			targets[i] = new TextureTarget("Lumio echo " + i, width, height, false, GpuFormat.RGBA16_FLOAT);
		}
		return true;
	}

	/** @return one quad per line: each corner says which corner it is and which line, the shader finds the rest in the line texture */
	private static GpuBuffer lineQuads() {
		ByteBuffer data = ByteBuffer.allocateDirect(MOST_LINES * 4 * 24).order(ByteOrder.nativeOrder());
		for (int line = 0; line < MOST_LINES; line++) {
			for (int corner = 0; corner < 4; corner++) {
				data.putFloat(corner).putFloat(line).putFloat(0f);
				data.putFloat(0f).putFloat(0f);
				data.putInt(0);
			}
		}
		data.flip();
		return RenderSystem.getDevice().createBuffer(() -> "Lumio echo lines", GpuBuffer.USAGE_VERTEX, data);
	}
}
