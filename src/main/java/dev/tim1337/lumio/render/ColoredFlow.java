package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.ColoredMood;
import dev.tim1337.lumio.ui.ColoredPalette;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTheme;

import org.joml.Matrix4f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Arrays;
import java.util.Optional;

/**
 * The drifting colors of the Colored theme: thirteen soft fields over a dark base, drawn by one shader into one small texture of the whole screen, about once a
 * frame. Everything that takes the colors reads that texture at its own place on the screen, so the menu and the HUD are one surface, and fill, edge and glow
 * always match. A change of the colors (the picker, a new song) blends over two seconds as the eye sees it (OKLab), each field to the new color nearest to its own; the drift goes on through it. Nothing here runs while the theme is off.
 */
public final class ColoredFlow {
	/** The texture is this many times smaller than the screen; the colors are soft, and lose nothing by it. */
	private static final int DIVISOR = 8;
	private static final int LEAST_SIDE = 16;
	private static final long BLEND_NANOS = 2_000_000_000L;
	private static final double NANOS = 1e9;
	/** Three texels per field (its color, its place, and its size in 16 bits: in 8 bits a slowly growing field steps, which shows on a panel as a small jump of its colors), then the base and the shape of the screen. */
	private static final int TEXELS_PER_FIELD = 3;
	private static final int BASE_TEXEL = ColoredPalette.FIELDS * TEXELS_PER_FIELD;
	/** Then what the mood makes of the fields: how fast they fall off, how long they are, and how clear their edges. */
	private static final int MOOD_TEXEL = BASE_TEXEL + 1;
	private static final int TEXELS = MOOD_TEXEL + 1;
	private static final float MOST_FALL = 12f;
	private static final float MOST_STRETCH = 4f;
	/** Waves: fields as long bands, how long they get, and how far they roll, as shares of the screen. */
	private static final float WAVE_STRETCH = 2.6f;
	private static final float WAVE_ROLL_SECONDS = 70f;
	private static final float WAVE_ROLL_FROM = -0.25f;
	private static final float WAVE_ROLL_RANGE = 1.5f;
	private static final float WAVE_HEIGHT = 0.05f;
	/** Swirl: how far a field circles, as a share of the screen's height. */
	private static final float SWIRL_REACH = 0.1f;
	/** How long changing from one movement to another takes, and how slowly the brightness follows the music, in seconds. */
	private static final float MOVEMENT_BLEND_SECONDS = 2f;
	private static final float BREATH_SECONDS = 1.6f;
	/** Places are kept in 16 bits, from a little outside of the screen on one side to as far on the other. */
	private static final float PLACE_FROM = -1f;
	private static final float PLACE_RANGE = 3f;
	/** Sizes go up to this, as a share of the screen's height. */
	private static final float MOST_RADIUS = 2f;

	// Where the fields are at rest (a share of the screen, top left is 0), how large (a share of its height), how far they drift, how much they grow, and how
	// long a round takes in seconds: in the places of ColoredPalette.fields. All rounds take 23 to 41 seconds and none fits into another, so it never looks the same. The fields reach the edges of
	// the screen too: a panel there has colors like any other, where the dark base alone used to show.
	private static final float[] AT_X = {0.12f, 0.38f, 0.62f, 0.88f, 0.02f, 0.27f, 0.50f, 0.73f, 0.98f, 0.12f, 0.38f, 0.62f, 0.88f};
	private static final float[] AT_Y = {0.08f, 0.04f, 0.10f, 0.05f, 0.50f, 0.48f, 0.54f, 0.46f, 0.50f, 0.92f, 0.96f, 0.90f, 0.95f};
	private static final float[] RADIUS = {0.46f, 0.42f, 0.48f, 0.44f, 0.40f, 0.46f, 0.46f, 0.48f, 0.40f, 0.44f, 0.48f, 0.42f, 0.46f};
	private static final float[] DRIFT_X = {0.05f, -0.06f, 0.04f, -0.05f, 0.03f, 0.07f, -0.05f, -0.07f, -0.03f, 0.06f, -0.04f, 0.05f, -0.06f};
	private static final float[] DRIFT_Y = {0.05f, 0.07f, 0.04f, 0.06f, 0.06f, -0.08f, 0.07f, 0.08f, -0.06f, -0.06f, -0.05f, -0.07f, -0.04f};
	private static final float[] GROW = {0.12f, -0.10f, 0.14f, -0.12f, -0.12f, 0.10f, -0.14f, 0.12f, 0.12f, -0.10f, 0.14f, -0.12f, 0.10f};
	private static final float[] ROUND_SECONDS = {26f, 31f, 23f, 29f, 39f, 37f, 28f, 33f, 41f, 24f, 35f, 27f, 30f};
	private static final float TAU = (float) (Math.PI * 2.0);
	private static final float FROZEN_SECONDS = 6f;
	private static final int LOGGED_FIELDS = 4;
	private static final ColoredFlowLog LOG = new ColoredFlowLog();
	/** How much film grain lies over the colors, as a share of the screen's range. */
	private static final float GRAIN = 0.022f;

	/** How dark the layer between the colors and the content is, in the menu and in the game, when the colors allow it. */
	private static final float MENU_LAYER = 0.24f;
	private static final float GAME_LAYER = 0.24f;
	/** How much of the world shows under the colors in the game; in the menu window, nothing. */
	public static final float GAME_SHARE = 0.92f;
	/** The fields on the left of the screen, where the chat is, and how much of the dark layer lies over them in its background. */
	private static final int FIELD_TOP_LEFT = 0;
	private static final int FIELD_BOTTOM_LEFT = 9;
	private static final float CHAT_LAYER = 0.72f;
	public static final float MENU_SHARE = 1f;
	private static final float BACKDROP_SHARE = 0.92f;
	private static final float BACKDROP_LAYER = 0.18f;

	private static final RenderPipeline PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/colored_flow"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/colored_flow"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.build()
	);
	private static final Matrix4f NO_TRANSFORM = new Matrix4f();

	private static float flowSeconds = FROZEN_SECONDS;
	private static final float[] HUD_FIELD = new float[4];
	private static long lastWrite;
	private static ColoredMood.Movement movementNow = ColoredMood.Movement.DRIFT;
	private static ColoredMood.Movement movementBefore = ColoredMood.Movement.DRIFT;
	private static float movementBlend = 1f;
	private static float breath;
	private static float layerIntensity = -1f;
	private static boolean layerBreathes;
	private static final int[] LAYER_FIELDS = new int[ColoredPalette.FIELDS];
	private static TextureTarget target;
	private static ParameterTexture parameters;
	private static boolean wanted;
	/** A pane asked for the colors in the last frame, also while the menu and the HUD have another theme: one element can have this one of its own. */
	private static boolean askedLastFrame;
	private static boolean failed;

	/** The colors now in use, and what the blend goes from and to, each as the fields and the base. */
	private static int[] colors;
	/** Whether the blend is over: nothing changes until new colors come. */
	private static boolean blended;
	private static final int[] FROM = new int[ColoredPalette.FIELDS + 1];
	private static final int[] TO = new int[ColoredPalette.FIELDS + 1];
	private static final int[] SHOWN = new int[ColoredPalette.FIELDS + 1];
	private static long blendStart;
	private static int accentFrom;
	private static int accentTo;
	private static int accent = ColoredPalette.DEFAULT_COLORS[0];
	private static float menuLayerFrom;
	private static float menuLayerTo;
	private static float gameLayerFrom;
	private static float gameLayerTo;
	private static float menuLayer = MENU_LAYER;
	private static float gameLayer = GAME_LAYER;

	private ColoredFlow() {
	}

	/** @return whether the theme is on, in the menu or also in the game */
	public static boolean on() {
		MenuFeature menu = ModFeatures.MENU;
		return menu != null && menu.coloredUsed();
	}

	/** @return the texture that has the colors, or null without one */
	public static GpuTextureView view() {
		return failed || target == null ? null : target.getColorTextureView();
	}

	/** Called at the start of every frame: moves the blend on, and sizes the texture. It does nothing while the theme is off. */
	public static void newFrame() {
		boolean asked = askedLastFrame;
		askedLastFrame = false;
		wanted = false;
		if (!on() && !asked || failed) {
			return;
		}
		// the song's colors and loudness come from the music, also while nothing on the screen shows the song
		MenuFeature colors = ModFeatures.MENU;
		if ((colors.coloredFollowsSong() || colors.coloredBreathe()) && ModFeatures.MUSIC != null) {
			ModFeatures.MUSIC.updateWithoutView();
		}
		follow();
		followLayer(ModFeatures.MENU);
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null) {
			return;
		}
		RenderTarget main = client.gameRenderer.mainRenderTarget();
		int width = Math.max(LEAST_SIDE, (main.width + DIVISOR - 1) / DIVISOR);
		int height = Math.max(LEAST_SIDE, (main.height + DIVISOR - 1) / DIVISOR);
		try {
			if (target == null) {
				target = new TextureTarget("Lumio colored flow", width, height, false, GpuFormat.RGBA8_UNORM);
			} else if (target.width != width || target.height != height) {
				target.resize(width, height);
			}
		} catch (RuntimeException | Error error) {
			failed = true;
			dev.tim1337.lumio.api.experimental.util.Logs.warn("The colors of the Colored theme could not be made: they are left out", error);
		}
	}

	/** An element in this frame has the colors as its theme: they are there from the next frame on, whatever the menu and the HUD have. */
	public static void ask() {
		askedLastFrame = true;
	}

	/** A pane in this frame reads the colors: they are drawn before it. */
	public static void wanted() {
		wanted = true;
		LOG.pane(HudStyle.inHud());
	}

	/** @return what the colors are now: the song's, while it plays and that is asked for, otherwise the player's */
	private static int[] wantedColors(MenuFeature menu) {
		if (menu.coloredFollowsSong() && ModFeatures.MUSIC != null) {
			int[] song = ModFeatures.MUSIC.coverPalette();
			if (song != null && song.length > 0) {
				return song;
			}
		}
		return menu.coloredColors();
	}

	private static void follow() {
		MenuFeature menu = ModFeatures.MENU;
		int[] wantedNow = wantedColors(menu);
		long now = System.nanoTime();
		if (colors == null || !Arrays.equals(colors, wantedNow)) {
			boolean first = colors == null;
			colors = wantedNow.clone();
			System.arraycopy(SHOWN, 0, FROM, 0, SHOWN.length);
			layerIntensity = -1f;
			// each field to the new color nearest to it, so they do not swap places across the screen
			System.arraycopy(first ? ColoredPalette.fields(colors) : ColoredPalette.matched(SHOWN, ColoredPalette.fields(colors)), 0, TO, 0, ColoredPalette.FIELDS);
			TO[ColoredPalette.FIELDS] = ColoredPalette.base(colors);
			accentFrom = accent;
			accentTo = ColoredPalette.accent(colors[0]);
			menuLayerFrom = menuLayer;
			menuLayerTo = ColoredPalette.layer(colors, MENU_LAYER, 1f - MENU_SHARE);
			gameLayerFrom = gameLayer;
			gameLayerTo = ColoredPalette.layer(colors, GAME_LAYER, 1f - GAME_SHARE);
			blendStart = first || !menu.animations() ? now - BLEND_NANOS : now;
			blended = false;
		}
		float blend = Math.min(1f, (float) ((now - blendStart) / (double) BLEND_NANOS));
		float eased = blend * blend * (3f - 2f * blend);
		if (blended && blend >= 1f) {
			return;
		}
		blended = blend >= 1f;
		for (int i = 0; i < SHOWN.length; i++) {
			SHOWN[i] = ColoredPalette.mixPerceptual(FROM[i], TO[i], eased);
		}
		accent = ColoredPalette.mixPerceptual(accentFrom, accentTo, eased);
		// the layer moves as the colors do: at once to the darker one would show as a jump the moment the colors change
		menuLayer = menuLayerFrom + (menuLayerTo - menuLayerFrom) * eased;
		gameLayer = gameLayerFrom + (gameLayerTo - gameLayerFrom) * eased;
	}

	/** @return the accent color as 0xRRGGBB: the first color */
	public static int accent() {
		if (colors == null && on()) {
			follow();
		}
		return accent;
	}

	/**
	 * @param share where in the chat, from its bottom (0) to its top (1)
	 * @return a chat line's background as 0xRRGGBB: the colors of the left side of the screen, very quiet, so long text stays as readable as on black
	 */
	public static int chatBackground(float share) {
		int color = ColoredPalette.mix(SHOWN[FIELD_BOTTOM_LEFT], SHOWN[FIELD_TOP_LEFT], share);
		return ColoredPalette.mix(color, ColoredPalette.LAYER_RGB, CHAT_LAYER);
	}

	/** @return how dark the layer between the colors and what is on the menu window is */
	public static float menuLayer() {
		return menuLayer;
	}

	/** @return how dark the layer between the colors and what is on a panel in the game is */
	public static float gameLayer() {
		return gameLayer;
	}

	/** @return how much film grain lies over the colors */
	public static float grain() {
		return ModFeatures.MENU != null && ModFeatures.MENU.coloredGrain() ? GRAIN : 0f;
	}

	/** @return how much of the world is seen behind the whole screen's colors while a menu is open */
	public static float backdropShare() {
		return BACKDROP_SHARE;
	}

	/** @return how dark the whole screen's colors are while a menu is open */
	public static float backdropLayer() {
		return BACKDROP_LAYER;
	}

	/** @return where the field of a HUD panel lies now, in pixels of the screen: x, y, width, height. The same array each time. */
	public static float[] hudField(float x, float y, float width, float height, float screenWidth, float screenHeight) {
		ColoredFieldSpace.ofPanel(HUD_FIELD, x, y, width, height, screenWidth, screenHeight, flowSeconds);
		return HUD_FIELD;
	}

	/** @return a plain color for when the shader of the panes is not there: what the colors are like, dark enough for white text */
	public static int plainColor() {
		return ColoredPalette.mix(SHOWN[ColoredPalette.FIELDS], ColoredPalette.LAYER_RGB, BACKDROP_LAYER);
	}

	/** Draws the colors into their texture, in every frame where a pane reads them, in the game and on any screen. Called while no pass of the game is open, before the panes that read them are drawn. */
	public static void render() {
		MenuFeature menu = ModFeatures.MENU;
		if (ModFeatures.DEBUG != null && ModFeatures.DEBUG.logsColored() && menu != null) {
			Screen screen = Minecraft.getInstance().gui.screen();
			LOG.frame(wanted, System.nanoTime(), flowSeconds, ColoredMood.rate(menu.coloredSpeed()), screen == null ? "none" : screen.getClass().getSimpleName(), menu.coloredFollowsSong(),
				ModFeatures.MUSIC == null ? null : ModFeatures.MUSIC.coverPalette(), Arrays.copyOf(SHOWN, LOGGED_FIELDS));
		}
		if (!wanted || failed || target == null || !ModPipelines.usable(PIPELINE)) {
			return;
		}
		try {
			if (parameters == null) {
				parameters = new ParameterTexture("colored_flow_parameters", TEXELS);
			}
			write();
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			GpuBuffer quad = GlassPanes.fullQuad();
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indexBuffer = indices.getBuffer(6);
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(NO_TRANSFORM);
			try (RenderPass pass = encoder.createRenderPass(() -> "Lumio colored flow", target.getColorTextureView(), Optional.empty())) {
				pass.setPipeline(PIPELINE);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.bindTexture("Sampler0", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.setVertexBuffer(0, quad.slice());
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(6, 1, 0, 0, 0);
			}
		} catch (RuntimeException | Error error) {
			failed = true;
			dev.tim1337.lumio.api.experimental.util.Logs.warn("The colors of the Colored theme could not be made: they are left out", error);
		}
	}

	/** Puts the places, sizes and colors of this moment into the shader's parameters. */
	private static void write() {
		MenuFeature menu = ModFeatures.MENU;
		long now = System.nanoTime();
		float dt = lastWrite == 0L ? 0f : Math.min(0.25f, (float) ((now - lastWrite) / NANOS));
		lastWrite = now;
		ColoredMood.Movement movement = menu.coloredMovement();
		if (movement != movementNow) {
			movementBefore = movementNow;
			movementNow = movement;
			movementBlend = 0f;
		}
		movementBlend = menu.animations() ? Math.min(1f, movementBlend + dt / MOVEMENT_BLEND_SECONDS) : 1f;
		float blend = movementBlend * movementBlend * (3f - 2f * movementBlend);
		// the time runs at the mood's speed, and adds up: a change of speed does not make the fields jump
		if (menu.animations() && movement != ColoredMood.Movement.STILL) {
			flowSeconds += dt * ColoredMood.rate(menu.coloredSpeed());
		}
		float brighter = 0f;
		if (menu.coloredBreathe() && ModFeatures.MUSIC != null) {
			breath += (ModFeatures.MUSIC.loudness() - breath) * (1f - (float) Math.exp(-dt / BREATH_SECONDS));
			brighter = breath * ColoredMood.MOST_BREATH;
		} else {
			breath = 0f;
		}
		float intensity = menu.coloredIntensity();
		float sizeFactor = ColoredMood.radiusFactor(menu.coloredFieldSize());
		float aspect = target.width / (float) target.height;
		for (int i = 0; i < ColoredPalette.FIELDS; i++) {
			float x = placeX(movementBefore, i, aspect) + (placeX(movementNow, i, aspect) - placeX(movementBefore, i, aspect)) * blend;
			float y = placeY(movementBefore, i) + (placeY(movementNow, i) - placeY(movementBefore, i)) * blend;
			float radius = RADIUS[i] * sizeFactor * (1f + GROW[i] * swing(i));
			int color = ColoredMood.intense(SHOWN[i], intensity);
			if (brighter > 0f) {
				color = ColoredMood.brighter(color, brighter);
			}
			parameters.set(i * TEXELS_PER_FIELD, color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, 0);
			int px = place(x);
			int py = place(y);
			parameters.set(i * TEXELS_PER_FIELD + 1, px & 0xFF, px >> 8, py & 0xFF, py >> 8);
			int size = Math.round(Math.clamp(radius / MOST_RADIUS, 0f, 1f) * 65535f);
			parameters.set(i * TEXELS_PER_FIELD + 2, size & 0xFF, size >> 8, 0, 0);
		}
		int base = SHOWN[ColoredPalette.FIELDS];
		parameters.set(BASE_TEXEL, base >> 16 & 0xFF, base >> 8 & 0xFF, base & 0xFF, Math.round(Math.clamp(aspect / 4f, 0f, 1f) * 255f));
		float stretch = stretchOf(movementBefore) + (stretchOf(movementNow) - stretchOf(movementBefore)) * blend;
		parameters.set(MOOD_TEXEL, unit(ColoredMood.fall(menu.coloredEdges()) / MOST_FALL), unit((stretch - 1f) / (MOST_STRETCH - 1f)), unit(ColoredMood.sharp(menu.coloredEdges())), 0);
		parameters.upload();
	}

	private static int unit(float value) {
		return Math.round(Math.clamp(value, 0f, 1f) * 255f);
	}

	private static float stretchOf(ColoredMood.Movement movement) {
		return movement == ColoredMood.Movement.WAVES ? WAVE_STRETCH : 1f;
	}

	/** @return how far a field is on its way out and back, slow at both ends */
	private static float swing(int field) {
		return 0.5f - 0.5f * (float) Math.cos(TAU * (flowSeconds % ROUND_SECONDS[field]) / ROUND_SECONDS[field]);
	}

	private static float placeX(ColoredMood.Movement movement, int field, float aspect) {
		return switch (movement) {
			case DRIFT -> AT_X[field] + DRIFT_X[field] * swing(field);
			case SWIRL -> AT_X[field] + SWIRL_REACH / aspect * (float) Math.cos(swirlAngle(field));
			case WAVES -> {
				float rolled = AT_X[field] + flowSeconds / WAVE_ROLL_SECONDS;
				yield WAVE_ROLL_FROM + (rolled - (float) Math.floor(rolled)) * WAVE_ROLL_RANGE;
			}
			case STILL -> AT_X[field];
		};
	}

	private static float placeY(ColoredMood.Movement movement, int field) {
		return switch (movement) {
			case DRIFT -> AT_Y[field] + DRIFT_Y[field] * swing(field);
			case SWIRL -> AT_Y[field] + SWIRL_REACH * (float) Math.sin(swirlAngle(field));
			case WAVES -> AT_Y[field] + WAVE_HEIGHT * (float) Math.sin(TAU * (flowSeconds / ROUND_SECONDS[field] + AT_X[field]));
			case STILL -> AT_Y[field];
		};
	}

	/** @return where a field is on its circle; the circles run both ways by turns, so the fields pass each other */
	private static float swirlAngle(int field) {
		float round = TAU * (flowSeconds % ROUND_SECONDS[field]) / ROUND_SECONDS[field];
		return field % 2 == 0 ? round : -round;
	}

	/** Keeps the dark layer as thick as what is shown now needs, for text's contrast: with the player's Intensity, and the music's brightness at its most. */
	private static void followLayer(MenuFeature menu) {
		float intensity = menu.coloredIntensity();
		boolean breathes = menu.coloredBreathe();
		if (Math.abs(intensity - layerIntensity) < 0.001f && breathes == layerBreathes || colors == null) {
			return;
		}
		layerIntensity = intensity;
		layerBreathes = breathes;
		for (int i = 0; i < LAYER_FIELDS.length; i++) {
			int color = ColoredMood.intense(TO[i], intensity);
			LAYER_FIELDS[i] = breathes ? ColoredMood.brighter(color, ColoredMood.MOST_BREATH) : color;
		}
		menuLayer = ColoredPalette.layerOver(LAYER_FIELDS, MENU_LAYER, 1f - MENU_SHARE);
		gameLayer = ColoredPalette.layerOver(LAYER_FIELDS, GAME_LAYER, 1f - GAME_SHARE);
		menuLayerFrom = menuLayer;
		menuLayerTo = menuLayer;
		gameLayerFrom = gameLayer;
		gameLayerTo = gameLayer;
	}

	private static int place(float value) {
		return Math.round(Math.clamp((value - PLACE_FROM) / PLACE_RANGE, 0f, 1f) * 65535f);
	}
}
