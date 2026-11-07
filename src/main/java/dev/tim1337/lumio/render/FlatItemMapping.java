package dev.tim1337.lumio.render;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Location of a flat item's sprite (a generated item model: the sprite extruded by one pixel) in model space. Converts sprite-space positions
 * into model positions on the item's middle plane.
 *
 * @param halfThickness half the distance between the item's two large faces
 */
public record FlatItemMapping(TextureAtlasSprite sprite, Vector3f origin, Vector3f uAxis, Vector3f vAxis, float halfThickness) {
	/** Maximum thickness of a flat item, in blocks. A generated model is 1/16 thick. */
	private static final float MAX_THICKNESS = 0.1f;

	/** @return the mapping, or null if the quads are not a flat item */
	public static FlatItemMapping of(List<BakedQuad> quads) {
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		BakedQuad front = null;
		float frontArea = 0f;

		for (BakedQuad quad : quads) {
			for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
				float z = quad.position(i).z();
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
			}
			if (quad.direction().getAxis() == Direction.Axis.Z) {
				float area = new Vector3f(quad.position(1)).sub(quad.position(0))
					.cross(new Vector3f(quad.position(3)).sub(quad.position(0))).length();
				if (area > frontArea) {
					front = quad;
					frontArea = area;
				}
			}
		}
		if (front == null || maxZ - minZ > MAX_THICKNESS) {
			return null;
		}

		TextureAtlasSprite sprite = front.materialInfo().sprite();
		float[] u = new float[BakedQuad.VERTEX_COUNT];
		float[] v = new float[BakedQuad.VERTEX_COUNT];
		for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
			// as fractions of the sprite rather than atlas coordinates
			u[i] = (UVPair.unpackU(front.packedUV(i)) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
			v[i] = (UVPair.unpackV(front.packedUV(i)) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
		}

		// The quad maps sprite space to model space linearly: position = p0 + s * (p1 - p0) + t * (p3 - p0), where (s, t) solves the uv equations
		// of vertices 1 and 3 relative to vertex 0.
		float a = u[1] - u[0];
		float b = u[3] - u[0];
		float c = v[1] - v[0];
		float d = v[3] - v[0];
		float determinant = a * d - b * c;
		if (Math.abs(determinant) < 1e-6f) {
			return null;
		}

		Vector3fc p0 = front.position(0);
		Vector3f edge1 = new Vector3f(front.position(1)).sub(p0);
		Vector3f edge3 = new Vector3f(front.position(3)).sub(p0);

		Vector3f origin = positionAt(0f, 0f, u[0], v[0], a, b, c, d, determinant, p0, edge1, edge3);
		Vector3f uAxis = positionAt(1f, 0f, u[0], v[0], a, b, c, d, determinant, p0, edge1, edge3).sub(origin);
		Vector3f vAxis = positionAt(0f, 1f, u[0], v[0], a, b, c, d, determinant, p0, edge1, edge3).sub(origin);

		// the item's middle plane
		origin.z = (minZ + maxZ) / 2f;
		uAxis.z = 0f;
		vAxis.z = 0f;
		return new FlatItemMapping(sprite, origin, uAxis, vAxis, (maxZ - minZ) / 2f);
	}

	private static Vector3f positionAt(float spriteU, float spriteV, float u0, float v0, float a, float b, float c, float d,
									   float determinant, Vector3fc p0, Vector3f edge1, Vector3f edge3) {
		float s = ((spriteU - u0) * d - b * (spriteV - v0)) / determinant;
		float t = (a * (spriteV - v0) - c * (spriteU - u0)) / determinant;
		return new Vector3f(p0).fma(s, edge1).fma(t, edge3);
	}
}
