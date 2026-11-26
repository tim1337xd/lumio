package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.debug.DebugPass;

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
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Lumio's own outline of the highlighted entities, in place of the game's glow in a frame where an add-on asks for it
 * ({@code EntityHighlighter.outlineWidth}). The game still draws the entities' silhouettes in their colors into its outline target; instead of its blur,
 * the distance of every pixel to the nearest silhouette is found exactly, across and then down (two passes, as wide as the line and the glow), and a
 * line of the asked width with a soft glow beyond it is laid over the screen. It is tested against the depth of the world twice, where the silhouette
 * is and where the line is, so nothing of it shows through a wall. A coarse picture of where silhouettes are, one texel per 8 by 8 pixels, grown by
 * the reach, lets the two passes stop at once everywhere else, so the cost follows the highlighted entities, not the screen.
 */
public final class HighlightOutlines {
	/** The widest line and glow, in screen pixels: the reach of the passes. */
	private static final float MOST_WIDTH = 8f;
	private static final float MOST_GLOW = 24f;
	/** Pixels per texel of the coarse picture. */
	private static final int TILE = 8;
	/** The line width and glow go to the shaders in tenths and fifths of a pixel, in a quad's red and green. */
	private static final float WIDTH_STEPS = 10f;
	private static final float GLOW_STEPS = 5f;
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final String[] SAMPLER_NAMES = {"Sampler0", "Sampler1", "Sampler2", "Sampler3", "Sampler4"};
	/** How many samplers a pass binds: the snippet's Sampler0 and Sampler1, and the composite's three more. */
	private static final int PASS_SAMPLERS = 2;
	private static final int COMPOSITE_SAMPLERS = 5;
	/**
	 * An entity with Lumio's outline is drawn into the outline target in a code color, not its own: red its slot (from 1), green and blue a mark. The shader
	 * finds the slot at the nearest silhouette pixel and reads the entity's color and distance from the table, which the depth test needs (the game draws
	 * the silhouettes without a depth of their own).
	 */
	private static final int MOST_SLOTS = 255;
	private static final int MARK = 0x137B;
	/** The entity's distance goes to the table in 32nds of a block, 16 bits. */
	private static final float DISTANCE_STEPS = 32f;
	private static final Supplier<String> NAME = () -> "Lumio highlight outlines";

	private static final RenderPipeline COVER = pipeline("highlight_cover", "COVER", false);
	private static final RenderPipeline GROW = pipeline("highlight_grow", "GROW", false);
	private static final RenderPipeline ACROSS = pipeline("highlight_across", "ACROSS", false);
	private static final RenderPipeline COMPOSITE = pipeline("highlight_composite", "COMPOSITE", true);

	private static final TextureTarget[] targets = new TextureTarget[3];
	private static final int COVER_TARGET = 0;
	private static final int GROWN_TARGET = 1;
	private static final int ACROSS_TARGET = 2;
	private static final Int2ObjectOpenHashMap<GpuBuffer> quads = new Int2ObjectOpenHashMap<>();

	/** The widest line and glow asked in this frame; a line of 0 means the game's own outline. */
	private static float width;
	private static float glow;
	/** The entities with Lumio's outline in this frame, by slot: their id, their color and how far their nearest point is along the view. */
	private static final int[] slotIds = new int[MOST_SLOTS];
	private static final int[] slotColors = new int[MOST_SLOTS];
	private static final float[] slotNear = new float[MOST_SLOTS];
	private static int slots;
	private static ParameterTexture table;

	private HighlightOutlines() {
	}

	private static RenderPipeline pipeline(String name, String define, boolean composite) {
		BindGroupLayout.Builder samplers = BindGroupLayout.builder().withSampler("Sampler1");
		if (composite) {
			samplers.withSampler("Sampler2").withSampler("Sampler3").withSampler("Sampler4");
		}
		return ModPipelines.register(
			RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
				.withLocation(Lumio.id("pipeline/" + name))
				.withVertexShader(Lumio.id("core/glass_blit"))
				.withFragmentShader(Lumio.id("core/highlight_outline"))
				.withShaderDefine(define)
				.withBindGroupLayout(samplers.build())
				.withCull(false)
				.withColorTargetState(composite ? new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
					: new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
				.build());
	}

	/** A new frame: nothing asked yet. */
	public static void newFrame() {
		width = 0f;
		glow = 0f;
		slots = 0;
	}

	/**
	 * @return the color to draw an entity with Lumio's outline into the outline target: the code of its slot in this frame's table, which keeps its own
	 *         color and its distance; its own color when the table is full
	 */
	public static int code(Entity entity, int color) {
		int id = entity.getId();
		for (int i = 0; i < slots; i++) {
			if (slotIds[i] == id) {
				slotColors[i] = color;
				return (i + 1) << 16 | MARK;
			}
		}
		if (slots == MOST_SLOTS) {
			return color;
		}
		slotIds[slots] = id;
		slotColors[slots] = color;
		slotNear[slots] = nearAlongView(entity.getBoundingBox());
		return ++slots << 16 | MARK;
	}

	/** @return how far the nearest corner of the box is along the camera's view, 0 when the camera is in it or past it */
	private static float nearAlongView(AABB box) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Vector3fc forward = camera.forwardVector();
		double near = Double.MAX_VALUE;
		for (int corner = 0; corner < 8; corner++) {
			double x = ((corner & 1) == 0 ? box.minX : box.maxX) - eye.x;
			double y = ((corner & 2) == 0 ? box.minY : box.maxY) - eye.y;
			double z = ((corner & 4) == 0 ? box.minZ : box.maxZ) - eye.z;
			near = Math.min(near, x * forward.x() + y * forward.y() + z * forward.z());
		}
		return (float) Math.max(0.0, near);
	}

	/** An entity that glows in this frame asks for Lumio's outline; the widest line and glow asked are kept. */
	public static void ask(float lineWidth, float glowWidth) {
		if (lineWidth > 0f) {
			width = Math.max(width, Math.min(lineWidth, MOST_WIDTH));
			glow = Math.max(glow, Mth.clamp(glowWidth, 0f, MOST_GLOW));
		}
	}

	/** @return whether this frame's outlines are Lumio's: asked for, and all of its pipelines usable */
	public static boolean active() {
		return width > 0f && ModPipelines.usable(COVER) && ModPipelines.usable(GROW) && ModPipelines.usable(ACROSS) && ModPipelines.usable(COMPOSITE);
	}

	/** Lays the outlines of the silhouettes in the game's outline target over the screen. */
	public static void draw(RenderTarget outline, RenderTarget main) {
		DebugHooks.passBegin(DebugPass.HIGHLIGHTS);
		try {
			drawPasses(outline, main);
		} finally {
			DebugHooks.passEnd(DebugPass.HIGHLIGHTS);
		}
	}

	private static void drawPasses(RenderTarget outline, RenderTarget main) {
		ensureTargets(main);
		int reach = (int) Math.ceil(width + glow) + 1;
		int growTiles = (reach + TILE - 1) / TILE;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(IDENTITY);
		GpuBuffer quad = quad(Math.round(width * WIDTH_STEPS), Math.round(glow * GLOW_STEPS), reach, growTiles);
		writeTable();
		GpuTextureView silhouettes = outline.getColorTextureView();
		GpuTextureView grown = targets[GROWN_TARGET].getColorTextureView();
		// where silhouettes are, coarse; that grown by the reach; the nearest silhouette across; then down, and the line and glow laid over the screen
		pass(encoder, targets[COVER_TARGET].getColorTextureView(), COVER, quad, transform, indices, PASS_SAMPLERS, silhouettes);
		pass(encoder, grown, GROW, quad, transform, indices, PASS_SAMPLERS, targets[COVER_TARGET].getColorTextureView());
		pass(encoder, targets[ACROSS_TARGET].getColorTextureView(), ACROSS, quad, transform, indices, PASS_SAMPLERS, silhouettes, grown);
		pass(encoder, main.getColorTextureView(), COMPOSITE, quad, transform, indices, COMPOSITE_SAMPLERS, targets[ACROSS_TARGET].getColorTextureView(), silhouettes,
			table.view(), main.getDepthTextureView(), grown);
	}

	/** One pass over the whole target; the textures are bound as Sampler0 and on, the last one again for any sampler the pipeline has beyond them. */
	private static void pass(CommandEncoder encoder, GpuTextureView into, RenderPipeline pipeline, GpuBuffer quad, GpuBufferSlice transform,
		RenderSystem.AutoStorageIndexBuffer indices, int samplers, GpuTextureView... textures) {
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (RenderPass pass = encoder.createRenderPass(NAME, into, Optional.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			for (int i = 0; i < samplers; i++) {
				pass.bindTexture(SAMPLER_NAMES[i], textures[Math.min(i, textures.length - 1)], nearest);
			}
			pass.setVertexBuffer(0, quad.slice());
			pass.setIndexBuffer(indices.getBuffer(6), indices.type());
			pass.drawIndexed(6, 1, 0, 0, 0);
		}
	}

	/** Each slot's color in one texel and its distance in the next. */
	private static void writeTable() {
		if (table == null) {
			table = new ParameterTexture("highlight_table", 2 * MOST_SLOTS);
		}
		for (int i = 0; i < slots; i++) {
			int color = slotColors[i];
			int near = Math.min(0xFFFF, Math.round(slotNear[i] * DISTANCE_STEPS));
			table.set(2 * i, color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, 255);
			table.set(2 * i + 1, near & 0xFF, near >> 8, 0, 255);
		}
		table.upload();
	}

	private static void ensureTargets(RenderTarget main) {
		int coarseWidth = (main.width + TILE - 1) / TILE;
		int coarseHeight = (main.height + TILE - 1) / TILE;
		if (targets[ACROSS_TARGET] != null && targets[ACROSS_TARGET].width == main.width && targets[ACROSS_TARGET].height == main.height) {
			return;
		}
		for (TextureTarget target : targets) {
			if (target != null) {
				target.destroyBuffers();
			}
		}
		targets[COVER_TARGET] = new TextureTarget("Lumio highlight cover", coarseWidth, coarseHeight, false, GpuFormat.RGBA16_FLOAT);
		targets[GROWN_TARGET] = new TextureTarget("Lumio highlight grown", coarseWidth, coarseHeight, false, GpuFormat.RGBA16_FLOAT);
		targets[ACROSS_TARGET] = new TextureTarget("Lumio highlight across", main.width, main.height, false, GpuFormat.RGBA16_FLOAT);
	}

	/** @return a quad over the whole target with the line width, the glow, the reach and the grown tiles in its color, as the shader takes them */
	private static GpuBuffer quad(int red, int green, int blue, int alpha) {
		int key = (red & 0xFF) << 24 | (green & 0xFF) << 16 | (blue & 0xFF) << 8 | alpha & 0xFF;
		// looked up without a lambda or a boxed key: this runs for every pass, every frame
		GpuBuffer quad = quads.get(key);
		if (quad == null) {
			quad = FullQuads.create("Lumio highlight quad", (alpha & 0xFF) << 24 | (blue & 0xFF) << 16 | (green & 0xFF) << 8 | red & 0xFF);
			quads.put(key, quad);
		}
		return quad;
	}
}
