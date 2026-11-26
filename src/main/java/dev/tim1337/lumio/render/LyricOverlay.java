package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.features.music.LyricVignette;

import org.joml.Matrix4f;
import org.joml.Vector4f;

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
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Draws the lyrics in the world last, over the finished world: after the terrain, the see-through blocks, the sky (Molten, aurora, stars) and the clouds,
 * in the order they were submitted, tested only against the depth the world left. They stand so near in that depth that just what is right before the
 * camera covers them ({@code WorldLyricDepth}); the hand is drawn after them.
 * <p>
 * It also keeps them out of the game's vignette, which multiplies the finished picture. While lyrics stand in the world, the game's own draw of it is
 * held back ({@link #takeOver}) and made here, right before the HUD, with the same texture, color and blending, except where lyrics show: they go into a
 * mask as well, and the vignette leaves that out, but takes the hand back in. In a frame without lyrics the game draws its vignette itself.
 */
public final class LyricOverlay {
	private static final int MOST_PIECES = 4096;
	private static final int QUAD_VERTICES = 4;
	private static final int QUAD_INDICES = 6;
	private static final int FIRST_BUFFER_BYTES = 1 << 16;
	private static final int ALPHA_SHIFT = 24;
	private static final int RED_SHIFT = 16;
	private static final int GREEN_SHIFT = 8;
	private static final Supplier<String> NAME = () -> "Lumio lyrics";
	private static final Vector4f NOTHING = new Vector4f(0f, 0f, 0f, 0f);

	private static final RenderPipeline VIGNETTE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/lyric_vignette"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/lyric_vignette"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").withSampler("Sampler3").build())
			.withCull(false)
			// the blending of the game's vignette
			.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_COLOR, BlendFactor.ZERO, BlendFactor.ONE)))
			.build()
	);

	/** A piece of the lyrics kept for the end of the world. */
	private static final class Piece {
		final PoseStack.Pose pose = new PoseStack.Pose();
		OutlineRenderTypes.Overlay overlay;
		SubmitNodeCollector.CustomGeometryRenderer renderer;
	}

	/** Pieces next to each other in the order of drawing that are drawn alike. */
	private record Run(OutlineRenderTypes.Overlay overlay, int firstByte, int bytes, int vertices) {
	}

	private static final List<Piece> PIECES = new ArrayList<>();
	private static final List<Run> RUNS = new ArrayList<>();
	private static int count;
	private static Matrix4f worldView = new Matrix4f();
	private static TextureTarget maskTarget;
	private static ParameterTexture parameters;
	private static ByteBufferBuilder bytes;
	private static ByteBuffer vertexData;
	private static GpuBuffer vertexBuffer;
	private static boolean failed;
	private static int checkedFor = -1;
	private static boolean usable;
	/** This frame's vignette is made here: what the game would have drawn, and whether the mask holds this frame's lyrics. */
	private static boolean pending;
	private static Identifier texture;
	private static int color;
	private static boolean maskDrawn;

	private LyricOverlay() {
	}

	/**
	 * The game is about to draw its vignette.
	 *
	 * @return whether it is made here instead, because lyrics stand in the world
	 */
	public static boolean takeOver(Identifier vignette, int argb) {
		if (!LyricVignette.lyricsShown() || !ready()) {
			return false;
		}
		pending = true;
		texture = vignette;
		color = argb;
		return true;
	}

	private static boolean ready() {
		if (checkedFor != ModPipelines.generation()) {
			checkedFor = ModPipelines.generation();
			usable = ModPipelines.usable(VIGNETTE) && OutlineRenderTypes.overlaysUsable();
		}
		return usable && !failed;
	}

	/**
	 * Keeps a piece of the lyrics for the end of the world, at the pose it is drawn with now.
	 *
	 * @return whether it is kept; if not, it goes the way of everything else in the world
	 */
	public static boolean add(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
		OutlineRenderTypes.Overlay overlay = OutlineRenderTypes.overlayOf(type);
		if (overlay == null || count >= MOST_PIECES || !ready()) {
			return false;
		}
		if (count == 0) {
			// the world's view holds only while the world is rendered, and is gone by the time the lyrics are drawn
			worldView = RenderSystem.getModelViewMatrixCopy();
		}
		if (count == PIECES.size()) {
			PIECES.add(new Piece());
		}
		Piece piece = PIECES.get(count++);
		piece.pose.set(pose.last());
		piece.overlay = overlay;
		piece.renderer = renderer;
		return true;
	}

	/** The world is finished, clouds and all, the hand not yet drawn: the lyrics go over it, and into the vignette's mask when one is needed. */
	public static void draw(RenderTarget main) {
		if (count == 0) {
			return;
		}
		try {
			int total = buildVertices();
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			if (pending) {
				ensureTarget(main);
				encoder.clearColorTexture(maskTarget.getColorTexture(), NOTHING);
				maskDrawn = true;
			}
			if (total == 0) {
				return;
			}
			upload(encoder, total);
			int mostIndices = 0;
			var textures = Minecraft.getInstance().getTextureManager();
			for (Run run : RUNS) {
				mostIndices = Math.max(mostIndices, run.vertices() / QUAD_VERTICES * QUAD_INDICES);
				// taken before a pass opens: a texture used for the first time is written to the graphics card, which cannot happen during a pass
				textures.getTexture(run.overlay().texture()).getTextureView();
			}
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indexBuffer = indices.getBuffer(mostIndices);
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(worldView);
			drawRuns(encoder, main, main.getColorTextureView(), indices, indexBuffer, transform);
			if (pending) {
				drawRuns(encoder, main, maskTarget.getColorTextureView(), indices, indexBuffer, transform);
			}
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private static void drawRuns(CommandEncoder encoder, RenderTarget main, com.mojang.blaze3d.textures.GpuTextureView into, RenderSystem.AutoStorageIndexBuffer indices,
								 GpuBuffer indexBuffer, GpuBufferSlice transform) {
		var textures = Minecraft.getInstance().getTextureManager();
		try (RenderPass pass = encoder.createRenderPass(NAME, into, Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
			for (Run run : RUNS) {
				OutlineRenderTypes.Overlay overlay = run.overlay();
				pass.setPipeline(overlay.pipeline());
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.bindTexture("Sampler0", textures.getTexture(overlay.texture()).getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, overlay.mipmaps()));
				pass.setVertexBuffer(0, vertexBuffer.slice(run.firstByte(), run.bytes()));
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(run.vertices() / QUAD_VERTICES * QUAD_INDICES, 1, 0, 0, 0);
			}
		}
	}

	/** @return how many bytes of vertices the kept pieces came to, in {@link #RUNS}: in the order they were submitted, which is the order they lie over each other */
	private static int buildVertices() {
		RUNS.clear();
		if (bytes == null) {
			bytes = new ByteBufferBuilder(FIRST_BUFFER_BYTES);
		}
		int total = 0;
		int from = 0;
		while (from < count) {
			OutlineRenderTypes.Overlay kind = PIECES.get(from).overlay;
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			int to = from;
			while (to < count && PIECES.get(to).overlay == kind) {
				Piece piece = PIECES.get(to++);
				piece.renderer.render(piece.pose, builder);
			}
			from = to;
			try (MeshData mesh = builder.build()) {
				if (mesh != null) {
					ByteBuffer data = mesh.vertexBuffer();
					int size = data.remaining();
					ensureData(total, total + size);
					vertexData.position(total);
					vertexData.put(data);
					RUNS.add(new Run(kind, total, size, mesh.drawState().vertexCount()));
					total += size;
				}
			}
		}
		return total;
	}

	private static void ensureData(int used, int size) {
		if (vertexData != null && vertexData.capacity() >= size) {
			return;
		}
		ByteBuffer grown = ByteBuffer.allocateDirect(Math.max(size, vertexData == null ? FIRST_BUFFER_BYTES : vertexData.capacity() * 2)).order(ByteOrder.nativeOrder());
		if (vertexData != null) {
			vertexData.position(0).limit(used);
			grown.put(vertexData);
		}
		vertexData = grown;
	}

	private static void upload(CommandEncoder encoder, int total) {
		if (vertexBuffer == null || vertexBuffer.size() < total) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}
			vertexBuffer = RenderSystem.getDevice().createBuffer(NAME, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) vertexData.capacity());
		}
		vertexData.position(0).limit(total);
		encoder.writeToBuffer(vertexBuffer.slice(0, total), vertexData);
		vertexData.clear();
	}

	/** Right before the HUD: the hand is drawn, so its depth is what the main target holds now. Lays the vignette over the picture, when it is made here. */
	public static void finish(RenderTarget main) {
		for (int i = 0; i < count; i++) {
			PIECES.get(i).renderer = null;
		}
		count = 0;
		if (!pending) {
			return;
		}
		pending = false;
		boolean drawn = maskDrawn;
		maskDrawn = false;
		try {
			ensureTarget(main);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			if (!drawn) {
				encoder.clearColorTexture(maskTarget.getColorTexture(), NOTHING);
			}
			if (parameters == null) {
				parameters = new ParameterTexture("lyric_vignette_parameters", 1);
			}
			parameters.set(0, color >> RED_SHIFT & 0xFF, color >> GREEN_SHIFT & 0xFF, color & 0xFF, color >>> ALPHA_SHIFT);
			parameters.upload();
			AbstractTexture vignette = Minecraft.getInstance().getTextureManager().getTexture(texture);
			var vignetteView = vignette.getTextureView();
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
			GpuBuffer quad = GlassPanes.fullQuad();
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indexBuffer = indices.getBuffer(QUAD_INDICES);
			try (RenderPass pass = encoder.createRenderPass(NAME, main.getColorTextureView(), Optional.empty())) {
				pass.setPipeline(VIGNETTE);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.bindTexture("Sampler0", vignetteView, vignette.getSampler());
				pass.bindTexture("Sampler1", maskTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.bindTexture("Sampler2", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.bindTexture("Sampler3", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.setVertexBuffer(0, quad.slice());
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(QUAD_INDICES, 1, 0, 0, 0);
			}
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private static void ensureTarget(RenderTarget main) {
		if (maskTarget == null) {
			maskTarget = new TextureTarget("Lumio lyric mask", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
		} else if (maskTarget.width != main.width || maskTarget.height != main.height) {
			maskTarget.resize(main.width, main.height);
		}
	}

	/** From now on the lyrics go with the rest of the world again, and the game draws its vignette itself. */
	private static void fail(RuntimeException e) {
		failed = true;
		Logs.warn("The lyrics could not be drawn over the world, they are drawn with it", e);
	}
}
