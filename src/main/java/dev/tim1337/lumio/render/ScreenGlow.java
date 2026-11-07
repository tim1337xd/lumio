package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.features.visual.world.HeldItem;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.List;

/**
 * A glow around an item that looks the same from every side.
 * <p>
 * Every frame the item is projected to the screen and the effect is built around its silhouette: a mask of the item as the camera sees it, the
 * distance from every screen point to it, and from that a glow texture. It is drawn on a camera-facing quad right behind the item, so the
 * item covers what is behind it and the rest forms the halo. The texture is small and filtered, so the glow is smooth at any size.
 * <p>
 * Only regular game textures and shaders are used, so this works with every graphics backend of the game.
 */
public final class ScreenGlow {
	/** Texture size in pixels. */
	private static final int SIZE = 128;
	/** Number of textures, since there are many items per frame: with only a few, an item was drawn with the texture of another that reused its slot in the same frame. */
	private static final int SLOTS = 32;
	private static final float NEAR = 0.05f;
	/** An item is 16 pixels wide regardless of its texture resolution. */
	private static final float ITEM_SIZE = OutlineGeometry.ITEM_SIZE;
	/** Items with larger textures are sampled at this many pixels at most, for speed. */
	private static final int MAX_SPRITE_PIXELS = 32;

	/** A glow reaches this much further than an outline of the same width. */
	private static final float GLOW_REACH = 2.5f;

	private record Slot(DynamicTexture texture, RenderType glow) {
	}

	/** Per-frame work buffers, reused: the arrays are large, and reallocating them every frame would load the garbage collector. */
	private static final boolean[] MASK = new boolean[SIZE * SIZE];
	private static final float[] SQUARED = new float[SIZE * SIZE];
	private static final float[] DISTANCE = new float[SIZE * SIZE];
	private static final float[] OPACITY = new float[SIZE * SIZE];
	private static final float[] BLURRED = new float[SIZE * SIZE];

	private static Slot[] slots;
	private static int nextSlot;

	private ScreenGlow() {
	}

	/**
	 * Submits the glow for an item.
	 *
	 * @param pose  the item's pose; must be the one its quads are drawn with
	 * @param quads the item model
	 * @param flat  the sprite mapping if the item is flat; its transparent pixels are not part of the item
	 */
	public static void submit(PoseStack pose, SubmitNodeCollector collector, List<BakedQuad> quads, FlatItemMapping flat, OutlineStyle style) {
		if (!OutlineRenderTypes.available()) {
			return;
		}
		Matrix4f itemPose = new Matrix4f(pose.last().pose());
		// captured at submit time: the draw callbacks run later, after the player-in-a-screen render has finished
		boolean gui = HeldItem.isGuiEntity();
		Slot slot = slots()[nextSlot];
		nextSlot = (nextSlot + 1) % SLOTS;

		// the geometry is already in the space the poses are in, so it needs no pose of its own
		collector.submitCustomGeometry(new PoseStack(), slot.glow(),
			(identity, consumer) -> draw(slot, consumer, identity, itemPose, quads, flat, style, gui));
	}

	private static Slot[] slots() {
		if (slots == null) {
			slots = new Slot[SLOTS];
			for (int i = 0; i < SLOTS; i++) {
				Identifier id = Lumio.id("screen_glow/" + i);
				DynamicTexture texture = new DynamicTexture(() -> "Lumio " + id, new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true));
				Minecraft.getInstance().getTextureManager().register(id, texture);
				slots[i] = new Slot(texture, OutlineRenderTypes.glow(id));
			}
		}
		return slots;
	}

	private static void draw(Slot slot, VertexConsumer consumer, PoseStack.Pose identity, Matrix4f pose,
							 List<BakedQuad> quads, FlatItemMapping flat, OutlineStyle style, boolean gui) {
		Vector3f right;
		Vector3f up;
		Vector3f forward;
		if (gui) {
			// Rendering into part of a screen (the player in the inventory) uses an orthographic projection looking along -z into an image that is
			// flipped when shown: up on screen is down in it. There is no perspective shrinking with distance.
			right = new Vector3f(1f, 0f, 0f);
			up = new Vector3f(0f, -1f, 0f);
			forward = new Vector3f(0f, 0f, -1f);
		} else {
			Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
			// poses are world-oriented relative to the camera, so these are the screen directions
			right = new Vector3f(camera.leftVector()).negate();
			up = new Vector3f(camera.upVector());
			forward = new Vector3f(camera.forwardVector());
		}

		Shapes shapes = new Shapes(right, up, forward, gui);
		if (flat != null) {
			shapes.addFlatItem(pose, flat);
		} else {
			shapes.addModel(pose, quads);
		}
		if (shapes.count == 0) {
			return;
		}

		// on-screen size of one item pixel, as an angle, so the distance does not matter
		float blockLength = pose.transformDirection(1f, 0f, 0f, new Vector3f()).length();
		float pixelSize = gui ? blockLength / ITEM_SIZE : blockLength / ITEM_SIZE / (shapes.depthSum / shapes.depthCount);
		float reach = style.width() * GLOW_REACH;
		float margin = reach * pixelSize;

		// the texture is a square around the item with margin for the glow
		float cell = Math.max(shapes.maxX - shapes.minX, shapes.maxY - shapes.minY) + 2f * margin;
		cell /= SIZE - 4;
		float left = (shapes.minX + shapes.maxX) / 2f - cell * SIZE / 2f;
		float top = (shapes.minY + shapes.maxY) / 2f + cell * SIZE / 2f;

		float[] squaredDistance = OutlineGeometry.squaredDistanceTransform(shapes.mask(left, top, cell), SIZE, SIZE, SQUARED);
		float cellInPixels = cell / pixelSize;
		float[] distance = DISTANCE;
		for (int i = 0; i < distance.length; i++) {
			// distances are measured between cell centers, so the edge of a cell next to the item is half a cell away
			distance[i] = squaredDistance[i] == 0f ? 0f : Math.max(0f, (float) Math.sqrt(squaredDistance[i]) - 0.5f) * cellInPixels;
		}

		NativeImage image = slot.texture().getPixels();
		if (style.kind() == OutlineKind.NEON) {
			drawRimLight(image, distance, reach, style.strength(), style.rgb());
		} else {
			drawGlow(image, distance, reach, style.strength(), style.glowSmoothness(), cellInPixels);
		}
		slot.texture().upload();

		// A camera-facing quad behind the whole item. Along any line of sight the visible glow then matches what it was computed for.
		float depth = shapes.depthMax + blockLength / ITEM_SIZE * 0.5f;
		float inner = 2f / SIZE;
		// the rim light bakes its colors into the texture (the line is lighter than the surrounding light); the others are tinted
		int tint = Math.round(style.opacity() * 255f) << 24 | (style.kind() == OutlineKind.NEON ? 0xFFFFFF : style.rgb());
		corner(consumer, identity, right, up, forward, left, top, cell, inner, inner, depth, tint, gui);
		corner(consumer, identity, right, up, forward, left, top, cell, 1f - inner, inner, depth, tint, gui);
		corner(consumer, identity, right, up, forward, left, top, cell, 1f - inner, 1f - inner, depth, tint, gui);
		corner(consumer, identity, right, up, forward, left, top, cell, inner, 1f - inner, depth, tint, gui);
	}

	private static void corner(VertexConsumer consumer, PoseStack.Pose pose, Vector3f right, Vector3f up, Vector3f forward,
							   float left, float top, float cell, float u, float v, float depth, int color, boolean orthographic) {
		float x = left + u * SIZE * cell;
		float y = top - v * SIZE * cell;
		// in perspective sizes are angles, so positions scale with distance; in orthographic mode they are lengths and only depth is a distance
		float spread = orthographic ? 1f : depth;
		consumer.addVertex(pose,
				right.x * x * spread + up.x * y * spread + forward.x * depth,
				right.y * x * spread + up.y * y * spread + forward.y * depth,
				right.z * x * spread + up.z * y * spread + forward.z * depth)
			.setUv(u, v)
			.setColor(color);
	}

	/**
	 * Rim light: a line a fraction of an item pixel wide at the edge, nearly white in the color, surrounded by a quickly fading light in the
	 * color. The color is baked into the texture, so it is redrawn whenever color or strength change (it animates slightly).
	 */
	private static void drawRimLight(NativeImage image, float[] distance, float reach, float strength, int rgb) {
		float lineWidth = 0.22f;
		int red = rgb >> 16 & 0xFF;
		int green = rgb >> 8 & 0xFF;
		int blue = rgb & 0xFF;
		int lineRed = Math.round(red + (255 - red) * 0.65f);
		int lineGreen = Math.round(green + (255 - green) * 0.65f);
		int lineBlue = Math.round(blue + (255 - blue) * 0.65f);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				float d = distance[y * SIZE + x];
				float t = d / reach;
				float halo = t >= 1f ? 0f : strength * 0.75f * (1f - t) * (1f - t) * (1f - t);
				float line = d >= lineWidth * 2f ? 0f : 1f - Mth.clamp((d - lineWidth) / lineWidth, 0f, 1f);
				float alpha = Math.min(1f, Math.max(halo, line * Math.min(1f, 0.55f + strength)));
				// on the line use its lighter color, blending into the color of the surrounding light
				float mix = Mth.clamp(line / Math.max(0.001f, alpha), 0f, 1f);
				int r = Math.round(Mth.lerp(mix, red, lineRed));
				int gr = Math.round(Mth.lerp(mix, green, lineGreen));
				int b = Math.round(Mth.lerp(mix, blue, lineBlue));
				image.setPixelABGR(x, y, (Math.round(alpha * 255f) << 24) | (b << 16) | (gr << 8) | r);
			}
		}
	}

	private static void drawGlow(NativeImage image, float[] distance, float reach, float strength, float smoothness, float cellInPixels) {
		float[] opacity = OPACITY;
		for (int i = 0; i < opacity.length; i++) {
			float t = distance[i] / reach;
			opacity[i] = t >= 1f ? 0f : strength * (1f - t) * (1f - t);
		}
		// a smooth glow is blurred so it follows the item shape less closely: corners and dents are rounded off
		int radius = Math.min(14, Math.round(smoothness * reach / cellInPixels * 0.7f));
		if (radius > 0) {
			float[] scratch = BLURRED;
			for (int pass = 0; pass < 2; pass++) {
				blur(opacity, scratch, radius, true);
				blur(scratch, opacity, radius, false);
			}
			// the blur pulls light away from the item edge; compensate for it
			float gain = 1f + smoothness * 0.9f;
			for (int i = 0; i < opacity.length; i++) {
				opacity[i] = Math.min(1f, opacity[i] * gain);
			}
		}
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				// the color is not baked into the texture so it can change without redrawing; see the vertex colors
				image.setPixelABGR(x, y, (Math.round(opacity[y * SIZE + x] * 255f) << 24) | 0xFFFFFF);
			}
		}
	}

	private static void blur(float[] from, float[] to, int radius, boolean horizontal) {
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				float sum = 0f;
				for (int d = -radius; d <= radius; d++) {
					int sx = horizontal ? Mth.clamp(x + d, 0, SIZE - 1) : x;
					int sy = horizontal ? y : Mth.clamp(y + d, 0, SIZE - 1);
					sum += from[sy * SIZE + sx];
				}
				to[y * SIZE + x] = sum / (radius * 2 + 1);
			}
		}
	}
	/**
	 * The item's silhouette as seen by the camera: polygons in screen space, as angles relative to the line of sight.
	 */
	private static final class Shapes {
		private final Vector3f right;
		private final Vector3f up;
		private final Vector3f forward;
		private float[] polygons = new float[8 * 64];
		int count;
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float depthMax = Float.NEGATIVE_INFINITY;
		float depthSum;
		int depthCount;

		private final boolean orthographic;

		Shapes(Vector3f right, Vector3f up, Vector3f forward, boolean orthographic) {
			this.right = right;
			this.up = up;
			this.forward = forward;
			this.orthographic = orthographic;
		}

		/** Adds a flat item as a slab: every opaque sprite pixel is a small box, seen from some side. */
		void addFlatItem(Matrix4f pose, FlatItemMapping flat) {
			SpriteContents sprite = flat.sprite().contents();
			int width = sprite.width();
			int height = sprite.height();
			int stride = Math.max(1, (int) Math.ceil(Math.max(width, height) / (double) MAX_SPRITE_PIXELS));

			Vector3f origin = pose.transformPosition(flat.origin(), new Vector3f());
			Vector3f uAxis = pose.transformDirection(flat.uAxis(), new Vector3f());
			Vector3f vAxis = pose.transformDirection(flat.vAxis(), new Vector3f());
			Vector3f zAxis = pose.transformDirection(0f, 0f, flat.halfThickness(), new Vector3f());

			float[][] corners = new float[8][3];
			for (int by = 0; by < height; by += stride) {
				for (int bx = 0; bx < width; bx += stride) {
					if (!isOpaque(sprite, bx, by, stride)) {
						continue;
					}
					float u0 = (float) bx / width;
					float u1 = (float) Math.min(bx + stride, width) / width;
					float v0 = (float) by / height;
					float v1 = (float) Math.min(by + stride, height) / height;
					// corners 0-3 are the front face, 4-7 the back face, in the same order
					for (int i = 0; i < 8; i++) {
						float u = (i % 4 == 0 || i % 4 == 3) ? u0 : u1;
						float v = (i % 4 < 2) ? v0 : v1;
						view(corners[i], origin, uAxis, vAxis, zAxis, u, v, i < 4 ? 1f : -1f);
					}
					quad(corners, 0, 1, 2, 3);
					quad(corners, 4, 5, 6, 7);
					quad(corners, 0, 1, 5, 4);
					quad(corners, 1, 2, 6, 5);
					quad(corners, 2, 3, 7, 6);
					quad(corners, 3, 0, 4, 7);
				}
			}
		}

		private static boolean isOpaque(SpriteContents sprite, int x, int y, int stride) {
			for (int dy = 0; dy < stride && y + dy < sprite.height(); dy++) {
				for (int dx = 0; dx < stride && x + dx < sprite.width(); dx++) {
					if (!sprite.isTransparent(0, x + dx, y + dy)) {
						return true;
					}
				}
			}
			return false;
		}

		/** Writes the position in camera space into out: right, up, and distance. */
		private void view(float[] out, Vector3f origin, Vector3f uAxis, Vector3f vAxis, Vector3f zAxis, float u, float v, float z) {
			float px = origin.x + u * uAxis.x + v * vAxis.x + z * zAxis.x;
			float py = origin.y + u * uAxis.y + v * vAxis.y + z * zAxis.y;
			float pz = origin.z + u * uAxis.z + v * vAxis.z + z * zAxis.z;
			out[0] = px * right.x + py * right.y + pz * right.z;
			out[1] = px * up.x + py * up.y + pz * up.z;
			out[2] = px * forward.x + py * forward.y + pz * forward.z;
		}

		/** Adds any other model by its faces, which are assumed opaque. */
		void addModel(Matrix4f pose, List<BakedQuad> quads) {
			Vector3f point = new Vector3f();
			float[][] corners = new float[4][3];
			for (BakedQuad quad : quads) {
				for (int i = 0; i < 4; i++) {
					pose.transformPosition(quad.position(i), point);
					corners[i][0] = point.dot(right);
					corners[i][1] = point.dot(up);
					corners[i][2] = point.dot(forward);
				}
				quad(corners, 0, 1, 2, 3);
			}
		}

		private void quad(float[][] corners, int a, int b, int c, int d) {
			int[] indices = {a, b, c, d};
			for (int i = 0; i < 4; i++) {
				if (!orthographic && corners[indices[i]][2] < NEAR) {
					return; // extends behind the camera
				}
			}
			if ((count + 1) * 8 > polygons.length) {
				polygons = Arrays.copyOf(polygons, polygons.length * 2);
			}
			for (int i = 0; i < 4; i++) {
				float[] corner = corners[indices[i]];
				float x = orthographic ? corner[0] : corner[0] / corner[2];
				float y = orthographic ? corner[1] : corner[1] / corner[2];
				polygons[count * 8 + i * 2] = x;
				polygons[count * 8 + i * 2 + 1] = y;
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				depthMax = Math.max(depthMax, corner[2]);
				depthSum += corner[2];
				depthCount++;
			}
			count++;
		}

		/** @return for every texture cell, whether the item covers it */
		boolean[] mask(float left, float top, float cell) {
			boolean[] mask = MASK;
			Arrays.fill(mask, false);
			float[] polygon = new float[8];
			for (int p = 0; p < count; p++) {
				for (int i = 0; i < 4; i++) {
					polygon[i * 2] = (polygons[p * 8 + i * 2] - left) / cell;
					polygon[i * 2 + 1] = (top - polygons[p * 8 + i * 2 + 1]) / cell;
				}
				fill(mask, polygon);
			}
			return mask;
		}

		/** Marks the cells whose centers lie inside the convex quadrilateral. */
		private static void fill(boolean[] mask, float[] polygon) {
			float minY = Math.min(Math.min(polygon[1], polygon[3]), Math.min(polygon[5], polygon[7]));
			float maxY = Math.max(Math.max(polygon[1], polygon[3]), Math.max(polygon[5], polygon[7]));
			int firstRow = Math.max(0, (int) Math.ceil(minY - 0.5f));
			int lastRow = Math.min(SIZE - 1, (int) Math.floor(maxY - 0.5f));
			for (int y = firstRow; y <= lastRow; y++) {
				float centerY = y + 0.5f;
				float minX = Float.POSITIVE_INFINITY;
				float maxX = Float.NEGATIVE_INFINITY;
				for (int edge = 0; edge < 4; edge++) {
					int a = edge * 2;
					int b = (edge + 1) % 4 * 2;
					float ya = polygon[a + 1];
					float yb = polygon[b + 1];
					if ((ya <= centerY && yb > centerY) || (yb <= centerY && ya > centerY)) {
						float x = polygon[a] + (centerY - ya) / (yb - ya) * (polygon[b] - polygon[a]);
						minX = Math.min(minX, x);
						maxX = Math.max(maxX, x);
					}
				}
				int firstColumn = Math.max(0, (int) Math.ceil(minX - 0.5f));
				int lastColumn = Math.min(SIZE - 1, (int) Math.floor(maxX - 0.5f));
				for (int x = firstColumn; x <= lastColumn; x++) {
					mask[y * SIZE + x] = true;
				}
			}
		}
	}
}
