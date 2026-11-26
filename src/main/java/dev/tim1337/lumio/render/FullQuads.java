package dev.tim1337.lumio.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builds the quad over a whole target that the full-screen passes draw: position, texture coordinate and color per vertex. The passes hand
 * values to their shader in the color, so each keeps (and never closes) the quads it made.
 */
final class FullQuads {
	/** The corners in clip space, counterclockwise from the bottom left. */
	private static final float[][] CORNERS = {{-1f, -1f}, {1f, -1f}, {1f, 1f}, {-1f, 1f}};
	/** Three floats of position, two of texture coordinate and four bytes of color. */
	private static final int VERTEX_BYTES = 24;

	private FullQuads() {
	}

	/** @return a new quad over the whole target with one color (alpha, blue, green, red from the highest byte) in every vertex */
	static GpuBuffer create(String label, int color) {
		ByteBuffer data = ByteBuffer.allocateDirect(CORNERS.length * VERTEX_BYTES).order(ByteOrder.nativeOrder());
		for (float[] corner : CORNERS) {
			data.putFloat(corner[0]).putFloat(corner[1]).putFloat(0f);
			data.putFloat(corner[0] * 0.5f + 0.5f).putFloat(corner[1] * 0.5f + 0.5f);
			data.putInt(color);
		}
		data.flip();
		return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, data);
	}
}
