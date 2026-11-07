package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;

import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector2f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.tim1337.lumio.ui.HudStyle;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Shader-rendered glass for the menu and the HUD panels: every pane is one quad, and a shader computes it entirely from an image of what is behind
 * it. Like real glass, the background shows through blurred, refracted at the edges with slight chromatic dispersion, with a specular highlight
 * on the edge where light falls, a shadow below and the glow around it.
 * <p>
 * How it works: panes are collected while the screen is extracted. The first pane that needs a backdrop asks the game to split there: the game
 * then renders everything up to that point and calls its blur ({@code processBlurEffect}). Instead of the vanilla blur, the parts of the screen
 * under glass are copied and blurred at reduced size into textures. The panes are then drawn as ordinary GUI elements, in order and in their
 * strata, sampling those textures. Whatever is drawn between the split and a pane (content of earlier panels) is not in the backdrop, so it simply
 * lies under the pane: a window over the panels covers them, as it should.
 * <p>
 * The blur is a dual Kawase blur: a few passes down to ever smaller pictures and as many back up, which blurs as wide as a large Gaussian with
 * few samples. Only the regions under glass panes are copied and blurred (their bounds with a margin for the blur, merged into a few
 * rectangles), so a few small HUD panels cost a small part of the screen. How small and how many passes is the glass quality; Auto chooses it
 * from the GPU time of the backdrop, measured with timestamps a few frames late so nothing ever waits for the GPU.
 * <p>
 * The tint adapts to what is behind a pane: a little denser over a bright backdrop, lighter over a dark one, so text keeps its contrast. The
 * brightness under every pane is read back from the smallest picture of the blur every few frames, without ever waiting for the GPU (the copy
 * reports when it is done), and smoothed per pane over half a second. Where the GPU cannot report it, the shader averages a few samples spread
 * over the pane instead.
 * <p>
 * Pane parameters (position, color, blur and so on) live in a texture, because the shader only gets one value per pane from its quad: the pane
 * index. The glow colors along the edge are stored in the first texels of the same texture.
 */
public final class GlassPanes {
	/** Texels per pane in the parameter texture. */
	private static final int TEXELS_PER_PANE = 13;
	/** First the glow's colors along an edge, one per table entry, then how bright the Colored theme's glow is there, then what holds for the whole frame, then the panes. */
	private static final int LUT_SIZE = UiGlow.TABLE_SIZE;
	private static final int FRAME_TEXEL = 2 * LUT_SIZE;
	private static final int FIRST_PANE_TEXEL = FRAME_TEXEL + 1;
	private static final int MAX_PANES = 120;
	private static final int TEXTURE_TEXELS = FIRST_PANE_TEXEL + MAX_PANES * TEXELS_PER_PANE;

	// pane flags, stored in the parameter texture
	/** The pane is opaque (dark and light theme); no backdrop is sampled. */
	public static final int FLAG_OPAQUE = 1;
	/** A large pane (the menu): deeper edge light and inner glow. */
	public static final int FLAG_WINDOW = 2;
	/** Only the edge line is drawn, over the pane's content, not the pane itself. */
	public static final int FLAG_RIM_ONLY = 4;
	/** Part of the glow also extends inside the edge, as on a window. */
	public static final int FLAG_INNER_GLOW = 8;
	/** Light theme: lighter shadow and a dark edge line. */
	public static final int FLAG_LIGHT = 16;
	/** No depth effect: no gloss or light inside the edge, and an even edge line; the shadow and bending are left out by the style. */
	public static final int FLAG_FLAT = 32;
	/** The Colored theme: the colors of {@link ColoredFlow} at this place of the screen fill the pane and light its edge and glow. */
	public static final int FLAG_COLORED = 64;
	/** The tint gets as dense as readable text needs where the world behind is bright. */
	public static final int FLAG_READABLE = 128;
	/** Not for the shader: the pane stands on its own, so its glow and shadow go in the frame's pass behind all elements. */
	public static final int FLAG_GLOW_BEHIND = 256;

	/**
	 * A quality of the glass: the size the blur starts at (a divisor of the screen, doubled on large screens), how many passes it goes down and
	 * up, and whether the edge bends the picture and splits its colors.
	 */
	public enum Level {
		LOW(4, 2, false, false),
		MEDIUM(2, 2, true, false),
		HIGH(2, 3, true, true);

		final int divisor;
		final int passes;
		final boolean refraction;
		final boolean fringes;

		Level(int divisor, int passes, boolean refraction, boolean fringes) {
			this.divisor = divisor;
			this.passes = passes;
			this.refraction = refraction;
			this.fringes = fringes;
		}
	}

	private static final RenderPipeline PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Lumio.id("pipeline/gui_glass"))
			.withVertexShader(Lumio.id("core/silhouette"))
			.withFragmentShader(Lumio.id("core/gui_glass"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").build())
			.build()
	);

	/** One pass of the blur: one full-target quad with the direction (down or up) and the sample distance encoded in its vertex color. */
	private static final RenderPipeline BLUR_PIPELINE = ModPipelines.registerForStartup(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/glass_blur"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/glass_blur"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.build()
	);

	/** Screens taller than this blur from a picture half as large again: the blur looks the same, at a quarter of the work. */
	private static final int LARGE_SCREEN_HEIGHT = 1500;
	/** At most this many rectangles are blurred; more are merged into the ones that grow least. */
	private static final int MAX_REGIONS = 4;
	/** Regions closer than this (in screen pixels) are merged, as blurring the gap costs less than another round of draws. */
	private static final int MERGE_GAP = 24;
	/** The sample distance of the blur at no blur and at full blur, in half texels. */
	private static final float LEAST_SPREAD = 0.5f;
	private static final float MOST_SPREAD = 2.0f;
	private static final int BLUR_DOWN = 0;
	private static final int BLUR_UP = 1;
	private static final Supplier<String> BLUR_NAME = () -> "Lumio glass blur";
	/** The blit shader places its quad itself; the transform it is given is never used, so it is the same every time. */
	private static final Matrix4f NO_TRANSFORM = new Matrix4f();

	// Auto quality: the backdrop's GPU time is averaged over a number of frames; above the budget the quality steps down, first while it is chosen
	// and later only when it stays far above, never up again, so it never goes back and forth
	private static final int AUTO_SAMPLES = 120;
	private static final double BUDGET_MILLIS = 0.8;
	private static final double LATER_FACTOR = 2.0;

	private static ParameterTexture parameters;
	private static GpuTexture sharp;
	private static GpuTextureView sharpView;
	// the textures replaced in an earlier frame, kept until nothing draws with them any more
	private static GpuTextureView retiredView;
	private static GpuTexture retiredSharp;
	private static TextureTarget[] retiredLevels = new TextureTarget[0];
	private static GpuFormat sharpFormat;
	/** The blur's pictures: the first is the result the panes sample, each next one half as large; the smallest is never written by a pass up. */
	private static TextureTarget[] levels = new TextureTarget[0];
	private static Level builtLevel;
	private static int divisor;
	/**
	 * The quads of the blur passes, one per direction and sample distance (two directions of 256 distances): all of them fit, so none is ever
	 * thrown away and made again. A map of twelve was thrown away and rebuilt every frame once more than twelve were in use, and its boxed keys
	 * allocated on every look-up.
	 */
	private static final GpuBuffer[] QUADS = new GpuBuffer[512];

	private static int count;
	private static boolean wantsBackdrop;
	private static boolean stopRequested;
	private static boolean failed;
	private static boolean dirty;
	private static boolean captured;
	/** Framebuffer size of the current frame; panes write their texels as they are added, so each pane has a fixed slot in the texture. */
	private static int lastWidth;
	private static int lastHeight;

	// the regions under glass, in framebuffer pixels from the bottom left: left, bottom, right and top; one more slot for the merge
	private static final int[] REGION_X0 = new int[MAX_REGIONS + 1];
	private static final int[] REGION_Y0 = new int[MAX_REGIONS + 1];
	private static final int[] REGION_X1 = new int[MAX_REGIONS + 1];
	private static final int[] REGION_Y1 = new int[MAX_REGIONS + 1];
	private static int regions;
	/** The most blur a pane of this frame asks for. */
	private static float paneBlur;
	// reused for placing a pane, so adding one creates nothing but its render state
	private static final Vector2f TOP_LEFT = new Vector2f();
	private static final Vector2f BOTTOM_RIGHT = new Vector2f();

	// GPU time of the backdrop (copy and blur), measured with timestamps a few frames late so nothing ever waits for the GPU
	private static final int TIMING_FRAMES = 4;
	private static GpuQueryPool timestamps;
	private static boolean timingFailed;
	private static int timingSlot;
	private static final boolean[] TIMING_WRITTEN = new boolean[TIMING_FRAMES];
	private static double backdropTotalMillis;
	private static long backdropSamples;
	private static Level autoLevel = Level.HIGH;
	private static boolean autoSettled;
	private static double autoSum;
	private static int autoCount;

	// the adaptive tint: a readback of the smallest blur picture every few frames, the panes' places in it at that time, and the brightness per
	// pane slot, smoothed over time; a pane that changed its size starts over from what is measured for it
	private static final int READBACK_FRAMES = 10;
	/** How many frames a readback may take before the GPU is taken to be unable to report it, and the shader averages instead. */
	private static final int READBACK_PATIENCE = 120;
	private static final float BRIGHTNESS_SECONDS = 0.5f;
	/** The brightness under a pane that is taken while nothing is measured yet: the tint as it is set. */
	private static final float NEUTRAL_BRIGHTNESS = 0.45f;
	/** At most this many samples per side of a pane are averaged from the readback. */
	private static final int READ_SAMPLES = 8;
	private static GpuBuffer readback;
	private static int readbackWidth;
	private static int readbackHeight;
	private static int readbackShrink;
	private static boolean readbackPending;
	private static boolean readbackFailed;
	private static boolean readbackLogged;
	private static int readbackAge;
	private static int framesSinceReadback;
	private static int readCount;
	private static final int[] READ_RECT = new int[MAX_PANES * 4];
	private static final int[] PANE_RECT = new int[MAX_PANES * 4];
	private static final float[] TARGET = new float[MAX_PANES];
	private static final boolean[] MEASURED = new boolean[MAX_PANES];
	private static final float[] BRIGHTNESS = new float[MAX_PANES];
	private static final float[] SEEN_WIDTH = new float[MAX_PANES];
	private static final float[] SEEN_HEIGHT = new float[MAX_PANES];
	private static long lastFrame;
	private static float frameSeconds;
	/** The Colored field of this frame's window, from that window's pane on; null for the whole screen, as the HUD before it has it. */
	private static float[] windowField;

	private GlassPanes() {
	}

	public static RenderPipeline pipeline() {
		return PIPELINE;
	}

	/** @return whether shader glass is active: the setting is on, the shaders compiled, and something is being drawn */
	public static boolean active() {
		MenuFeature menu = ModFeatures.MENU;
		return !failed && menu != null && menu.shaderGlass() && Minecraft.getInstance().gameRenderer != null;
	}

	/** @return the quality the glass is drawn at: the one set, or what Auto chose */
	public static Level level() {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null) {
			return Level.MEDIUM;
		}
		return switch (menu.glassQuality()) {
			case LOW -> Level.LOW;
			case MEDIUM -> Level.MEDIUM;
			case HIGH -> Level.HIGH;
			case AUTO -> timingFailed ? Level.MEDIUM : autoLevel;
		};
	}

	/** The outsides of the frame's panes, those of the HUD and of the screen over it apart, and what each one's glow reaches for finding where they meet. */
	private static final java.util.List<GlowPassState.Quad> HUD_OUTSIDE = new java.util.ArrayList<>();
	private static final java.util.List<GlowPassState.Quad> SCREEN_OUTSIDE = new java.util.ArrayList<>();
	private static final float[] GLOW_RECTS = new float[MAX_PANES * 4];
	private static final int[] NEIGHBORS = new int[GlowNeighbors.MOST];

	/** Puts a pane's outside in the frame's pass, which is added before the first one, so that all that follows lies over it. */
	private static void addOutside(GuiGraphicsExtractor graphics, Minecraft client, float x0, float y0, float x1, float y1, int index, float fade, Matrix3x2f pose,
		GpuSampler linear, GpuSampler nearest) {
		java.util.List<GlowPassState.Quad> pass = HudStyle.inHud() ? HUD_OUTSIDE : SCREEN_OUTSIDE;
		if (pass.isEmpty()) {
			// the Colored theme's colors if they are there: the other panes do not read them
			GpuTextureView first = ColoredFlow.view() != null ? ColoredFlow.view() : sharpView;
			ScreenRectangle screen = new ScreenRectangle(0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
			graphics.guiRenderState.addGuiElement(new GlowPassState(pass, screen, new TextureSetup(first, levels[0].getColorTextureView(), parameters.view(), linear, linear, nearest)));
		}
		pass.add(new GlowPassState.Quad(x0, y0, x1, y1, index, fade, pose));
	}

	/** Called at the start of every frame, before extraction: drops the panes of the previous frame. */
	public static void newFrame() {
		long now = System.nanoTime();
		frameSeconds = lastFrame == 0 ? 0f : Math.min(0.25f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		count = 0;
		regions = 0;
		paneBlur = 0f;
		HUD_OUTSIDE.clear();
		SCREEN_OUTSIDE.clear();
		java.util.Arrays.fill(GLOW_RECTS, 0f);
		wantsBackdrop = false;
		stopRequested = false;
		windowField = null;
		ColoredFlow.newFrame();
	}

	/** @return whether a pane in this frame needs a backdrop, so the vanilla blur is replaced by the glass blur */
	public static boolean wantsBackdrop() {
		return wantsBackdrop && !failed;
	}

	/**
	 * Appearance of a pane. All sizes are in menu pixels, at the scale of the pose the pane is drawn with.
	 *
	 * @param tint          glass color as 0xRRGGBB, with its amount in {@code tintAlpha} (1 is opaque)
	 * @param blur          background blur, from 0 to 1
	 * @param refraction    edge refraction strength, from 0 to 1
	 * @param dispersion    chromatic dispersion at the edge, from 0 to 1
	 * @param edgeLight     specular edge highlight strength, from 0 to 1
	 * @param shadowSpread  shadow reach in menu pixels, 0 for none
	 * @param shadow        shadow darkness, from 0 to 1
	 * @param glowSpread    glow reach in menu pixels, 0 for none
	 * @param glow          glow strength, from 0 to 1 (colors come from the glow settings)
	 * @param lineRgb       edge line color of opaque panes (dark and light theme)
	 * @param thickness     depth of the edge refraction and edge light, in menu pixels
	 * @param accentRgb     highlight color of a hovered button, with {@code lit} as its amount
	 * @param flowShare     how much of the colors of the Colored theme lie over the world, from 0 to 1, with {@link #FLAG_COLORED}
	 */
	public record Style(int tint, float tintAlpha, float blur, float refraction, float dispersion, float edgeLight, float shadowSpread, float shadow,
						float glowSpread, float glow, int lineRgb, float thickness, int accentRgb, float lit, int flags, float flowShare) {
	}

	/** The saturation of what is seen through glass by default, as a setting from 0 to 1; the shader has the factor it stands for. */
	public static final float DEFAULT_SATURATION = 0.6f;
	/** The setting at which the shader leaves the colors as they are: its factor is 1 there. */
	private static final float NEUTRAL_SATURATION = DEFAULT_SATURATION / 1.18f;
	private static boolean neutralSaturation;

	private static float paneSaturation() {
		if (neutralSaturation) {
			return NEUTRAL_SATURATION;
		}
		MenuFeature menu = ModFeatures.MENU;
		return menu == null ? DEFAULT_SATURATION : menu.glassSaturation();
	}

	/** Adds a pane that shows what is behind it without the look of glass. */
	public static void clearPane(Ui ui, int x, int y, int width, int height, float radius, Style style) {
		clearPane(ui, x, y, width, height, radius, 1f, style);
	}

	/** @param fade pane opacity, from 0 to 1 */
	public static void clearPane(Ui ui, int x, int y, int width, int height, float radius, float fade, Style style) {
		neutralSaturation = true;
		pane(ui, x, y, width, height, radius, fade, style);
		neutralSaturation = false;
	}

	/**
	 * Adds a pane. It is positioned by the current graphics pose, like everything else drawn with it.
	 *
	 * @param fade pane opacity, from 0 to 1
	 */
	public static void pane(Ui ui, int x, int y, int width, int height, float radius, float fade, Style style) {
		if (!active() || width <= 0 || height <= 0 || count >= MAX_PANES) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.gameRenderer.mainRenderTarget();
		try {
			ensureResources(main);
		} catch (RuntimeException | Error error) {
			failed = true;
			Logs.warn("The glass could not be made by the graphics card, the plain glass is used", error);
			return;
		}
		GuiGraphicsExtractor graphics = ui.graphics;
		fade = Mth.clamp(fade * Ui.alpha(), 0f, 1f);
		boolean opaque = (style.flags() & FLAG_OPAQUE) != 0;
		boolean rimOnly = (style.flags() & FLAG_RIM_ONLY) != 0;
		boolean sees = !opaque && !rimOnly;
		if (sees && !stopRequested) {
			// the first pane needing a backdrop: split the stratum here; everything after it is drawn over the backdrop captured at this point
			graphics.nextStratum();
			graphics.blurBeforeThisStratum();
			stopRequested = true;
			wantsBackdrop = true;
		}

		// on-screen position in framebuffer pixels from the pose, which only translates and scales, never rotates
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		float scale = (float) client.getWindow().getGuiScale();
		float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
		pose.transformPosition(x, y, TOP_LEFT);
		pose.transformPosition(x + width, y + height, BOTTOM_RIGHT);
		float px = TOP_LEFT.x * scale;
		float py = TOP_LEFT.y * scale;
		float pw = (BOTTOM_RIGHT.x - TOP_LEFT.x) * scale;
		float ph = (BOTTOM_RIGHT.y - TOP_LEFT.y) * scale;
		float pixels = poseScale * scale;
		int fbHeight = main.height;
		// the shader's y axis starts at the bottom of the screen
		float bottom = fbHeight - (py + ph);

		// the quad extends as far as the shadow and glow reach, unless they are drawn in the pass behind all elements; what lies in a clip stays with its pane
		float outsideReach = rimOnly ? 0f : Math.max(style.shadowSpread(), style.glowSpread());
		boolean behind = (style.flags() & FLAG_GLOW_BEHIND) != 0 && !rimOnly && outsideReach > 0f && graphics.scissorStack.peek() == null;
		float reach = behind ? 1f : outsideReach + 1f;
		float x0 = x - reach;
		float y0 = y - reach;
		float x1 = x + width + reach;
		float y1 = y + height + reach;
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle own = new ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1 - x0), Mth.ceil(y1 - y0)).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(own) : own;
		if (bounds == null) {
			return; // fully clipped
		}

		Level level = level();
		int index = count++;
		int base = FIRST_PANE_TEXEL + index * TEXELS_PER_PANE;
		ParameterTexture p = parameters;
		set16(p, base, px + 32768f, bottom + 32768f);
		set16(p, base + 1, pw, ph);
		float r = Math.min(radius * pixels, Math.min(pw, ph) / 2f);
		setPacked(p, base + 2, r * 8f, Mth.clamp(style.blur(), 0f, 1f), fade);
		p.set(base + 3, style.tint() >> 16 & 0xFF, style.tint() >> 8 & 0xFF, style.tint() & 0xFF, unit(style.tintAlpha()));
		p.set(base + 4, unit(style.edgeLight()), unit(level.refraction ? style.refraction() : 0f), unit(level.fringes ? style.dispersion() : 0f),
			unit(style.shadow()));
		p.set(base + 5, Math.min(255, Math.round(style.shadowSpread() * pixels)), Math.min(255, Math.round(style.glowSpread() * pixels)), unit(style.glow()), style.flags() & 0xFF & 0xFF);
		p.set(base + 6, style.lineRgb() >> 16 & 0xFF, style.lineRgb() >> 8 & 0xFF, style.lineRgb() & 0xFF, Math.min(255, Math.round(style.thickness() * pixels)));
		p.set(base + 7, style.accentRgb() >> 16 & 0xFF, style.accentRgb() >> 8 & 0xFF, style.accentRgb() & 0xFF, unit(style.lit()));
		p.set(base + 8, unit(sees ? brightness(index, pw, ph, px, bottom) : NEUTRAL_BRIGHTNESS), readbackFailed ? 255 : 0, unit(style.flowShare()), (style.flags() & FLAG_COLORED) != 0 ? unit(ColoredFlow.grain()) : unit(paneSaturation()));
		set16(p, base + 9, main.width, main.height);
		// where the Colored field lies for this pane: a HUD panel's own, or from a menu's window on, in the window, or else on the screen
		if ((style.flags() & FLAG_COLORED) != 0 && (style.flags() & FLAG_WINDOW) != 0 && !rimOnly && !HudStyle.inHud() && windowField == null) {
			windowField = ColoredFieldSpace.ofWindow(px, bottom, pw, ph, main.width, main.height);
		}
		float[] field = (style.flags() & FLAG_COLORED) != 0 && HudStyle.inHud() ? ColoredFlow.hudField(px, bottom, pw, ph, main.width, main.height) : windowField;
		set16(p, base + 11, (field == null ? 0f : field[0]) + 32768f, (field == null ? 0f : field[1]) + 32768f);
		set16(p, base + 12, field == null ? main.width : field[2], field == null ? main.height : field[3]);
		dirty = true;
		if (behind) {
			float outside = (outsideReach + 1f) * pixels;
			GLOW_RECTS[index * 4] = px - outside;
			GLOW_RECTS[index * 4 + 1] = bottom - outside;
			GLOW_RECTS[index * 4 + 2] = px + pw + outside;
			GLOW_RECTS[index * 4 + 3] = bottom + ph + outside;
		}
		if (sees) {
			paneBlur = Math.max(paneBlur, style.blur());
		}
		if (sees) {
			// what the blur has to cover: the pane, and as far around it as the blur reaches into it
			int margin = divisor << (level.passes + 1);
			addRegion(Mth.floor(px) - margin, Mth.floor(bottom) - margin, Mth.ceil(px + pw) + margin, Mth.ceil(bottom + ph) + margin, main.width,
				main.height);
		}

		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		// with the colors, the first texture is theirs: nothing of a colored pane needs the sharp picture
		GpuTextureView first = sharpView;
		if ((style.flags() & FLAG_COLORED) != 0 && ColoredFlow.view() != null) {
			first = ColoredFlow.view();
			ColoredFlow.wanted();
		}
		TextureSetup textures = new TextureSetup(first, levels[0].getColorTextureView(), parameters.view(), linear, linear, nearest);
		if (behind) {
			addOutside(graphics, client, x - outsideReach - 1f, y - outsideReach - 1f, x + width + outsideReach + 1f, y + height + outsideReach + 1f, index, fade, pose, linear, nearest);
		}
		graphics.guiRenderState.addGuiElement(new GlassPaneState(x0, y0, x1, y1, index, behind ? GlassPaneState.INSIDE : GlassPaneState.WHOLE, fade, pose, scissor, bounds, textures));
	}

	/**
	 * Adds a rectangle to the regions to blur: merged with every region it overlaps or nearly touches, and when there are too many, the two that
	 * grow least together are merged.
	 */
	private static void addRegion(int x0, int y0, int x1, int y1, int screenWidth, int screenHeight) {
		x0 = Math.max(0, x0);
		y0 = Math.max(0, y0);
		x1 = Math.min(screenWidth, x1);
		y1 = Math.min(screenHeight, y1);
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		boolean merged = true;
		while (merged) {
			merged = false;
			for (int i = 0; i < regions; i++) {
				if (x0 - MERGE_GAP <= REGION_X1[i] && REGION_X0[i] <= x1 + MERGE_GAP && y0 - MERGE_GAP <= REGION_Y1[i] && REGION_Y0[i] <= y1 + MERGE_GAP) {
					x0 = Math.min(x0, REGION_X0[i]);
					y0 = Math.min(y0, REGION_Y0[i]);
					x1 = Math.max(x1, REGION_X1[i]);
					y1 = Math.max(y1, REGION_Y1[i]);
					removeRegion(i);
					merged = true;
					break;
				}
			}
		}
		REGION_X0[regions] = x0;
		REGION_Y0[regions] = y0;
		REGION_X1[regions] = x1;
		REGION_Y1[regions] = y1;
		regions++;
		if (regions > MAX_REGIONS) {
			mergeClosest();
		}
	}

	private static void removeRegion(int i) {
		regions--;
		REGION_X0[i] = REGION_X0[regions];
		REGION_Y0[i] = REGION_Y0[regions];
		REGION_X1[i] = REGION_X1[regions];
		REGION_Y1[i] = REGION_Y1[regions];
	}

	/** Merges the two regions whose union adds the least area. */
	private static void mergeClosest() {
		int bestA = 0;
		int bestB = 1;
		long bestGrowth = Long.MAX_VALUE;
		for (int a = 0; a < regions; a++) {
			for (int b = a + 1; b < regions; b++) {
				long union = (long) (Math.max(REGION_X1[a], REGION_X1[b]) - Math.min(REGION_X0[a], REGION_X0[b]))
					* (Math.max(REGION_Y1[a], REGION_Y1[b]) - Math.min(REGION_Y0[a], REGION_Y0[b]));
				long growth = union - area(a) - area(b);
				if (growth < bestGrowth) {
					bestGrowth = growth;
					bestA = a;
					bestB = b;
				}
			}
		}
		REGION_X0[bestA] = Math.min(REGION_X0[bestA], REGION_X0[bestB]);
		REGION_Y0[bestA] = Math.min(REGION_Y0[bestA], REGION_Y0[bestB]);
		REGION_X1[bestA] = Math.max(REGION_X1[bestA], REGION_X1[bestB]);
		REGION_Y1[bestA] = Math.max(REGION_Y1[bestA], REGION_Y1[bestB]);
		removeRegion(bestB);
	}

	private static long area(int i) {
		return (long) (REGION_X1[i] - REGION_X0[i]) * (REGION_Y1[i] - REGION_Y0[i]);
	}

	private static int unit(float value) {
		return Math.round(Mth.clamp(value, 0f, 1f) * 255f);
	}

	/** Packs two 16-bit values into one texel. */
	private static void set16(ParameterTexture p, int texel, float a, float b) {
		int ia = Mth.clamp(Math.round(a), 0, 65535);
		int ib = Mth.clamp(Math.round(b), 0, 65535);
		p.set(texel, ia & 0xFF, ia >> 8, ib & 0xFF, ib >> 8);
	}

	private static void setPacked(ParameterTexture p, int texel, float a, float blue, float alpha) {
		int ia = Mth.clamp(Math.round(a), 0, 65535);
		p.set(texel, ia & 0xFF, ia >> 8, unit(blue), unit(alpha));
	}

	/** Before rendering the frame: uploads the glow colors and pane parameters to the GPU. */
	public static void beforeRender() {
		// the colors every frame something reads them: not only when the glass's picture is made, whatever else is on the screen
		ColoredFlow.render();
		if (parameters == null || count == 0) {
			return;
		}
		int[] table = UiGlow.table();
		int[] brightness = UiGlow.coloredBrightness();
		for (int i = 0; i < LUT_SIZE; i++) {
			int color = table == null ? 0 : table[i];
			parameters.set(i, color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24);
			parameters.set(LUT_SIZE + i, 0, 0, 0, brightness == null ? 0 : brightness[i]);
		}
		for (int i = 0; i < count; i++) {
			GlowNeighbors.find(GLOW_RECTS, count, i, NEIGHBORS);
			parameters.set(FIRST_PANE_TEXEL + i * TEXELS_PER_PANE + 10, NEIGHBORS[0], NEIGHBORS[1], NEIGHBORS[2], NEIGHBORS[3]);
		}
		if (dirty) {
			parameters.upload();
			dirty = false;
		}
	}

	/** Creates the backdrop textures for the screen size and quality, and the parameter texture once; rebuilt only when either changes. */
	private static void ensureResources(RenderTarget main) {
		if (parameters == null) {
			parameters = new ParameterTexture("glass_parameters", TEXTURE_TEXELS);
			// precompile now so a shader the driver rejects is detected immediately rather than mid-frame
			if (!ModPipelines.compiles(PIPELINE) || !ModPipelines.compiles(BLUR_PIPELINE)) {
				throw new IllegalStateException("the shaders of the glass were not compiled");
			}
			Logs.info("The glass is made by the graphics card");
		}
		int width = main.width;
		int height = main.height;
		GpuFormat format = main.getColorTexture().getFormat();
		Level level = level();
		if (sharp == null || width != lastWidth || height != lastHeight || format != sharpFormat || level != builtLevel) {
			closeTargets();
			lastWidth = width;
			lastHeight = height;
			sharpFormat = format;
			builtLevel = level;
			sharp = RenderSystem.getDevice().createTexture("Lumio glass backdrop", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, format, width, height, 1, 1);
			sharpView = RenderSystem.getDevice().createTextureView(sharp);
			divisor = level.divisor * (height > LARGE_SCREEN_HEIGHT ? 2 : 1);
			levels = new TextureTarget[level.passes + 1];
			for (int i = 0; i < levels.length; i++) {
				int shrink = divisor << i;
				levels[i] = new TextureTarget("Lumio glass blur " + i, Math.max(1, (width + shrink - 1) / shrink),
					Math.max(1, (height + shrink - 1) / shrink), false, GpuFormat.RGBA8_UNORM);
			}
		}
	}

	/**
	 * Puts the textures aside when they are replaced. The panes of this frame were collected before, and still sample them when they are drawn after the
	 * capture: closed now, they would be used after they were freed, which OpenGL lets pass but on Vulkan loses the graphics device. So they go at the next capture.
	 */
	private static void closeTargets() {
		closeRetired();
		retiredView = sharpView;
		retiredSharp = sharp;
		retiredLevels = levels;
		sharpView = null;
		sharp = null;
		levels = new TextureTarget[0];
	}

	/** Closes the textures of an earlier frame, which nothing uses any more. */
	private static void closeRetired() {
		if (retiredView != null) {
			retiredView.close();
			retiredView = null;
		}
		if (retiredSharp != null) {
			retiredSharp.close();
			retiredSharp = null;
		}
		for (TextureTarget target : retiredLevels) {
			target.destroyBuffers();
		}
		retiredLevels = new TextureTarget[0];
	}

	/**
	 * Replaces the vanilla blur: copies the parts of the screen under glass, as rendered up to the first pane, and blurs them into the textures the
	 * panes sample. Called while no render pass of the game is open.
	 */
	public static void capture() {
		// a pipeline that did not compile must never be bound: the driver crashes on the empty one the game binds instead
		if (failed || sharp == null || regions == 0 || !ModPipelines.usable(BLUR_PIPELINE)) {
			return;
		}
		try {
			Minecraft client = Minecraft.getInstance();
			RenderTarget main = client.gameRenderer.mainRenderTarget();
			closeRetired();
			ensureResources(main);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			int slot = beginTiming(encoder);
			// one copy of the whole screen: a copy is cheap next to the blur, which is what only runs where glass is
			encoder.copyTextureToTexture(main.getColorTexture(), sharp, 0, 0, 0, 0, 0, main.width, main.height);
			if (!captured) {
				captured = true;
				Logs.info("The glass has the picture of the screen behind it ({}x{}, blurred from {}x{} in {} passes)", main.width, main.height,
					levels[0].width, levels[0].height, levels.length - 1);
			}
			MenuFeature menu = ModFeatures.MENU;
			// a pane may want more blur than the menu's glass (an overlay's background), and with none set it would find no blurred picture
			float blur = Math.max(menu == null ? 0.55f : menu.glassBlur(), paneBlur);
			if (blur > 0.001f) {
				GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(NO_TRANSFORM);
				float spread = Mth.lerp(Mth.clamp(blur, 0f, 1f), LEAST_SPREAD, MOST_SPREAD);
				// the first pass goes from the screen copy straight to the second size: every pass has a fixed cost on the CPU, more than its work
				// on the GPU for these small pictures, so one fewer counts
				pass(encoder, transform, levels[1], sharpView, BLUR_DOWN, spread * 2f, divisor << 1);
				for (int i = 2; i < levels.length; i++) {
					pass(encoder, transform, levels[i], levels[i - 1].getColorTextureView(), BLUR_DOWN, spread, divisor << i);
				}
				for (int i = levels.length - 1; i > 0; i--) {
					pass(encoder, transform, levels[i - 1], levels[i].getColorTextureView(), BLUR_UP, spread, divisor << (i - 1));
				}
				requestReadback(encoder);
			}
			endTiming(encoder, slot);
		} catch (RuntimeException | Error error) {
			failed = true;
			Logs.warn("The glass could not be made by the graphics card, the plain glass is used", error);
		}
	}

	/**
	 * @return the brightness under a pane slot this frame, eased towards what was last measured for it; a slot that now holds a pane of another
	 *         size starts from the measurement instead, so a new pane never fades in from the old one's value
	 */
	private static float brightness(int index, float width, float height, float x, float bottom) {
		int at = index * 4;
		PANE_RECT[at] = Mth.floor(x);
		PANE_RECT[at + 1] = Mth.floor(bottom);
		PANE_RECT[at + 2] = Mth.ceil(x + width);
		PANE_RECT[at + 3] = Mth.ceil(bottom + height);
		boolean sameSize = Math.abs(width - SEEN_WIDTH[index]) <= SEEN_WIDTH[index] * 0.25f && Math.abs(height - SEEN_HEIGHT[index]) <= SEEN_HEIGHT[index] * 0.25f;
		SEEN_WIDTH[index] = width;
		SEEN_HEIGHT[index] = height;
		if (!MEASURED[index]) {
			BRIGHTNESS[index] = NEUTRAL_BRIGHTNESS;
		} else if (!sameSize) {
			BRIGHTNESS[index] = TARGET[index];
		} else {
			BRIGHTNESS[index] += (TARGET[index] - BRIGHTNESS[index]) * (1f - (float) Math.exp(-frameSeconds / BRIGHTNESS_SECONDS));
		}
		return BRIGHTNESS[index];
	}

	/**
	 * Every few frames with glass: copies the smallest blur picture to a buffer the CPU can read, noting where every pane was in it. The copy
	 * reports when it is done; until then nothing waits, and the panes keep the brightness they had.
	 */
	private static void requestReadback(CommandEncoder encoder) {
		if (readbackFailed) {
			return;
		}
		if (readbackPending) {
			if (++readbackAge > READBACK_PATIENCE) {
				readbackFailed = true;
				Logs.info("The adaptive tint of the glass gets no brightness back from the graphics card; the shader averages it instead");
			}
			return;
		}
		if (++framesSinceReadback < READBACK_FRAMES) {
			return;
		}
		framesSinceReadback = 0;
		TextureTarget tiny = levels[levels.length - 1];
		try {
			if (readback == null || readbackWidth != tiny.width || readbackHeight != tiny.height) {
				if (readback != null) {
					readback.close();
				}
				readbackWidth = tiny.width;
				readbackHeight = tiny.height;
				readback = RenderSystem.getDevice().createBuffer(() -> "Lumio glass brightness", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
					(long) tiny.width * tiny.height * 4);
			}
			readbackShrink = divisor << (levels.length - 1);
			readCount = count;
			System.arraycopy(PANE_RECT, 0, READ_RECT, 0, count * 4);
			readbackPending = true;
			readbackAge = 0;
			encoder.copyTextureToBuffer(tiny.getColorTexture(), readback, 0, GlassPanes::readbackDone, 0);
			if (!readbackLogged) {
				readbackLogged = true;
				Logs.info("The adaptive tint of the glass reads the brightness back from the graphics card ({}x{} every {} frames)", tiny.width,
					tiny.height, READBACK_FRAMES);
			}
		} catch (RuntimeException | Error error) {
			readbackFailed = true;
			readbackPending = false;
			Logs.info("The adaptive tint of the glass cannot read back from the graphics card; the shader averages it instead", error);
		}
	}

	/** The readback is done: the brightness under every pane noted with it, averaged from a few samples over its place. */
	private static void readbackDone() {
		readbackPending = false;
		if (readback == null || readback.isClosed()) {
			return;
		}
		try (GpuBufferSlice.MappedView view = readback.map(true, false)) {
			ByteBuffer data = view.data();
			for (int i = 0; i < readCount; i++) {
				int x0 = Mth.clamp(READ_RECT[i * 4] / readbackShrink, 0, readbackWidth - 1);
				int y0 = Mth.clamp(READ_RECT[i * 4 + 1] / readbackShrink, 0, readbackHeight - 1);
				int x1 = Mth.clamp(READ_RECT[i * 4 + 2] / readbackShrink, x0 + 1, readbackWidth);
				int y1 = Mth.clamp(READ_RECT[i * 4 + 3] / readbackShrink, y0 + 1, readbackHeight);
				int stepX = Math.max(1, (x1 - x0) / READ_SAMPLES);
				int stepY = Math.max(1, (y1 - y0) / READ_SAMPLES);
				float sum = 0f;
				int samples = 0;
				for (int y = y0; y < y1; y += stepY) {
					for (int x = x0; x < x1; x += stepX) {
						int at = (y * readbackWidth + x) * 4;
						sum += (0.2126f * (data.get(at) & 0xFF) + 0.7152f * (data.get(at + 1) & 0xFF) + 0.0722f * (data.get(at + 2) & 0xFF)) / 255f;
						samples++;
					}
				}
				if (samples > 0) {
					TARGET[i] = sum / samples;
					MEASURED[i] = true;
				}
			}
		} catch (RuntimeException | Error error) {
			readbackFailed = true;
			Logs.info("The adaptive tint of the glass cannot read back from the graphics card; the shader averages it instead", error);
		}
	}

	/**
	 * One blur pass into a target, only in the regions under glass: they are scaled down to the target, with a texel more on every side so the
	 * filter at their edge reads what this pass wrote.
	 *
	 * @param shrink how many screen pixels one texel of the target covers
	 */
	private static void pass(CommandEncoder encoder, GpuBufferSlice transform, TextureTarget target, GpuTextureView source, int direction, float spread, int shrink) {
		GpuBuffer quad = quad(direction, spread);
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(6);
		try (RenderPass pass = encoder.createRenderPass(BLUR_NAME, target.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(BLUR_PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setVertexBuffer(0, quad.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			for (int i = 0; i < regions; i++) {
				int x0 = Math.max(0, REGION_X0[i] / shrink - 1);
				int y0 = Math.max(0, REGION_Y0[i] / shrink - 1);
				int x1 = Math.min(target.width, (REGION_X1[i] + shrink - 1) / shrink + 1);
				int y1 = Math.min(target.height, (REGION_Y1[i] + shrink - 1) / shrink + 1);
				if (x1 > x0 && y1 > y0) {
					pass.enableScissor(x0, y0, x1 - x0, y1 - y0);
					pass.drawIndexed(6, 1, 0, 0, 0);
				}
			}
			pass.disableScissor();
		}
	}

	/** @return a quad over the whole target */
	static GpuBuffer fullQuad() {
		return quad(0, 0f);
	}

	private static GpuBuffer quad(int direction, float spread) {
		int blue = Mth.clamp(Math.round(spread / 4f * 255f), 0, 255);
		int key = direction << 8 | blue;
		GpuBuffer found = QUADS[key];
		if (found != null) {
			return found;
		}
		int color = 0xFF000000 | (direction == BLUR_UP ? 0xFF : 0) | (blue << 16);
		float[][] corners = {{-1f, -1f}, {1f, -1f}, {1f, 1f}, {-1f, 1f}};
		ByteBuffer data = ByteBuffer.allocateDirect(4 * 24).order(ByteOrder.nativeOrder());
		for (float[] corner : corners) {
			data.putFloat(corner[0]).putFloat(corner[1]).putFloat(0f);
			data.putFloat(corner[0] * 0.5f + 0.5f).putFloat(corner[1] * 0.5f + 0.5f);
			data.putInt(color);
		}
		data.flip();
		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Lumio glass quad", GpuBuffer.USAGE_VERTEX, data);
		QUADS[key] = buffer;
		return buffer;
	}

	// ----------------------------------------------------------------------------------------------- timing and quality

	/**
	 * Starts timing the backdrop of this frame, after reading the result of the frame that used the same slot a few frames ago, if the GPU has
	 * it by now. @return the slot, or -1 when the GPU cannot time
	 */
	private static int beginTiming(CommandEncoder encoder) {
		if (timingFailed) {
			return -1;
		}
		try {
			if (timestamps == null) {
				timestamps = RenderSystem.getDevice().createTimestampQueryPool(2 * TIMING_FRAMES);
			}
			int slot = timingSlot;
			timingSlot = (timingSlot + 1) % TIMING_FRAMES;
			if (TIMING_WRITTEN[slot]) {
				OptionalLong start = timestamps.getValue(2 * slot);
				OptionalLong end = timestamps.getValue(2 * slot + 1);
				if (start.isPresent() && end.isPresent() && end.getAsLong() >= start.getAsLong()) {
					measured((end.getAsLong() - start.getAsLong()) / 1_000_000.0);
				}
			}
			encoder.writeTimestamp(timestamps, 2 * slot);
			TIMING_WRITTEN[slot] = false;
			return slot;
		} catch (RuntimeException | Error error) {
			timingFailed = true;
			Logs.info("The graphics card cannot time the glass, so Auto uses the medium quality", error);
			return -1;
		}
	}

	private static void endTiming(CommandEncoder encoder, int slot) {
		if (slot >= 0 && !timingFailed) {
			encoder.writeTimestamp(timestamps, 2 * slot + 1);
			TIMING_WRITTEN[slot] = true;
		}
	}

	/** One measured backdrop: counted, and for Auto averaged until a decision. */
	private static void measured(double millis) {
		backdropTotalMillis += millis;
		backdropSamples++;
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || menu.glassQuality() != MenuFeature.GlassQuality.AUTO) {
			return;
		}
		autoSum += millis;
		if (++autoCount < AUTO_SAMPLES) {
			return;
		}
		double average = autoSum / autoCount;
		autoSum = 0;
		autoCount = 0;
		double limit = autoSettled ? BUDGET_MILLIS * LATER_FACTOR : BUDGET_MILLIS;
		if (average > limit && autoLevel != Level.LOW) {
			Level lower = Level.values()[autoLevel.ordinal() - 1];
			Logs.info("Glass quality Auto: {} cost {} ms per frame on the GPU, stepping down to {}", autoLevel,
				String.format(java.util.Locale.ROOT, "%.2f", average), lower);
			autoLevel = lower;
		} else if (!autoSettled) {
			autoSettled = true;
			Logs.info("Glass quality Auto chose {} ({} ms per frame on the GPU)", autoLevel, String.format(java.util.Locale.ROOT, "%.2f", average));
		}
	}

	/** @return the GPU time of all backdrops measured so far, in milliseconds, and how many there were; for comparing two moments */
	public static double backdropTotalMillis() {
		return backdropTotalMillis;
	}

	public static long backdropSamples() {
		return backdropSamples;
	}
}
