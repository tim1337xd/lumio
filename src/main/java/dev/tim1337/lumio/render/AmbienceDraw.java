package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.features.visual.world.AmbienceField;
import dev.tim1337.lumio.features.visual.world.AmbienceParticles;
import dev.tim1337.lumio.features.visual.world.AmbiencePool;

import org.joml.Vector3fc;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the ambience's particles from their arrays: two batches a frame, one blended normally and one as light. They are tested against the world's depth, so walls hide them, and the pictures are
 * made here in one sheet, so no game texture is used. Nothing is allocated per frame or per particle.
 */
public final class AmbienceDraw {
	private static final int CELL = 64;
	private static final int CELLS = 4;
	private static final int SIZE = CELL * CELLS;
	private static final int DOT = 0;
	private static final int FLAKE = 1;
	private static final int LEAF = 2;
	private static final int RING = 3;
	private static final int LINE = 4;
	private static final int PUFF = 5;
	private static final int BEAM = 6;
	private static final int FIREFLY = 7;
	private static final int THREAD = 8;
	private static final float SMALLEST_ANGLE = 0.0022f;
	private static final float REACH = 64f;
	private static final float SHAFT_LENGTH = 26f;
	private static final float RAIN_LENGTH = 1.5f;
	private static final float RAIN_ANGLE = 0.0017f;
	private static final float RAIN_LEAST_LIGHT = 0.28f;
	private static final float EDGE_FADE = 0.3f;
	private static final float MIST_NEAR = 6f;
	private static final float MIST_LEAST_LIGHT = 0.3f;
	private static final float MIST_HEIGHT = 0.2f;

	private static final Identifier NORMAL_SHEET = Lumio.id("ambience_sheet");
	private static final Identifier LIGHT_SHEET = Lumio.id("ambience_light_sheet");
	private static RenderType normalType;
	private static RenderType lightType;
	private static boolean leftOutLogged;

	private static AmbiencePool pool;
	private static float partial;
	private static float time;
	private static float reach;
	private static float blink;
	private static float sunX;
	private static float sunY;
	private static double camX;
	private static double camY;
	private static double camZ;
	private static float leftX;
	private static float leftY;
	private static float leftZ;
	private static float upX;
	private static float upY;
	private static float upZ;

	private static final SubmitNodeCollector.CustomGeometryRenderer DRAW_NORMAL = (last, consumer) -> draw(last, consumer, false);
	private static final SubmitNodeCollector.CustomGeometryRenderer DRAW_LIGHT = (last, consumer) -> draw(last, consumer, true);

	private AmbienceDraw() {
	}

	/** Submits this frame's batches; nothing when no particle lives. */
	public static void collect(LevelRenderContext context) {
		AmbienceField field = ModFeatures.AMBIENCE.field();
		AmbiencePool particles = field.pool();
		Minecraft client = Minecraft.getInstance();
		if (particles.size() == 0 || client.level == null) {
			return;
		}
		if (!OutlineRenderTypes.available() || !OutlineRenderTypes.lightAvailable()) {
			if (!leftOutLogged) {
				leftOutLogged = true;
				Logs.warn("Ambience: its particles are left out, because their shaders could not be built on this system");
			}
			return;
		}
		ensureSheets();
		Camera camera = client.gameRenderer.mainCamera();
		Vec3 position = camera.position();
		pool = particles;
		partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		time = (client.level.getGameTime() % 24000L) + partial;
		reach = REACH;
		blink = ModFeatures.AMBIENCE.fireflyBlink();
		sunX = field.sunX();
		sunY = field.sunY();
		camX = position.x;
		camY = position.y;
		camZ = position.z;
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		leftX = left.x();
		leftY = left.y();
		leftZ = left.z();
		upX = up.x();
		upY = up.y();
		upZ = up.z();
		PoseStack poses = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poses, normalType, DRAW_NORMAL);
		context.submitNodeCollector().submitCustomGeometry(poses, lightType, DRAW_LIGHT);
	}

	private static void draw(PoseStack.Pose last, VertexConsumer consumer, boolean additive) {
		AmbiencePool p = pool;
		float forwardX = upY * leftZ - upZ * leftY;
		float forwardY = upZ * leftX - upX * leftZ;
		float forwardZ = upX * leftY - upY * leftX;
		for (int i = 0, size = p.size(); i < size; i++) {
			AmbiencePool.Kind kind = p.kindOf(i);
			if (kind.additive() != additive) {
				continue;
			}
			float alpha = p.alpha(i);
			if (alpha <= 0.004f) {
				continue;
			}
			float x = (float) (p.x(i) + p.vx(i) * partial - camX);
			float y = (float) (p.y(i) + p.vy(i) * partial - camY);
			float z = (float) (p.z(i) + p.vz(i) * partial - camZ);
			float distance = Mth.sqrt(x * x + y * y + z * z);
			float sideways = Mth.sqrt(x * x + z * z);
			// what nears the edge of the radius fades out there, before it is taken away
			alpha *= Mth.clamp((reach - sideways) / (reach * EDGE_FADE), 0f, 1f);
			if (alpha <= 0.004f) {
				continue;
			}
			if (kind == AmbiencePool.Kind.SHAFT) {
				shaft(p, i, last, consumer, x, y, z, alpha);
				continue;
			}
			// behind the camera: a big one can still reach into view
			if (x * forwardX + y * forwardY + z * forwardZ > p.size(i) * 2f + 1f) {
				continue;
			}
			draw(p, i, kind, last, consumer, x, y, z, distance, alpha);
		}
	}

	private static void draw(AmbiencePool p, int i, AmbiencePool.Kind kind, PoseStack.Pose last, VertexConsumer consumer, float x, float y, float z, float distance, float alpha) {
		float half = Math.max(p.size(i), distance * SMALLEST_ANGLE);
		float own = p.phase(i);
		int rgb = p.rgb(i);
		switch (kind) {
			case FIREFLY -> {
				float swell = 0.5f + 0.5f * Mth.sin((time + own * 10f) * 0.03f + own * 3f);
				float steady = 1f - blink * (1f - (0.35f + 0.65f * swell));
				billboard(last, consumer, x, y, z, Math.max(half, distance * 0.0075f), 0f, FIREFLY, lightColor(rgb, alpha * steady));
			}
			case RAIN -> line(last, consumer, x, y, z, (float) AmbienceParticles.windX() * 0.25f, p.vy(i), (float) AmbienceParticles.windZ() * 0.25f, RAIN_LENGTH, Math.max(p.size(i), distance * RAIN_ANGLE), LINE, shaded(rgb, alpha, Math.max(p.light(i), RAIN_LEAST_LIGHT)));
			case RIPPLE -> {
				float grown = 1f - p.lifeLeft(i);
				flat(last, consumer, x, y, z, p.size(i) * (0.25f + 0.75f * grown), RING, shaded(rgb, alpha, p.light(i)));
			}
			case GLINT -> billboard(last, consumer, x, y, z, half * (0.6f + 0.4f * Mth.sin(time * 0.6f + own)), 0.8f, FLAKE, lightColor(rgb, alpha));
			case DUST, EMBER -> {
				float flicker = kind == AmbiencePool.Kind.EMBER ? 0.7f + 0.3f * Mth.sin(time * 0.5f + own * 7f) : 0.7f + 0.3f * Mth.sin(time * 0.05f + own);
				billboard(last, consumer, x + Mth.sin(time * 0.02f + own) * 0.2f, y, z + Mth.cos(time * 0.017f + own) * 0.2f, half, 0f, DOT, lightColor(rgb, alpha * flicker * (kind == AmbiencePool.Kind.DUST ? p.light(i) : 1f)));
			}
			case LEAF -> billboard(last, consumer, x + Mth.sin(time * 0.05f + own) * 0.35f, y, z + Mth.cos(time * 0.041f + own) * 0.35f, half, time * 0.04f + own, LEAF, shaded(rgb, alpha, p.light(i)));
			case ASH -> billboard(last, consumer, x + Mth.sin(time * 0.03f + own) * 0.2f, y, z, half, time * 0.03f + own, DOT, shaded(rgb, alpha, p.light(i)));
			case MIST -> {
				// up close it would lie over the whole picture
				float near = Mth.clamp((distance - p.size(i) * 0.6f) / MIST_NEAR, 0f, 1f);
				band(last, consumer, x, y, z, p.size(i), PUFF, shaded(rgb, alpha * near, Math.max(p.light(i), MIST_LEAST_LIGHT)));
			}
			case WEB -> line(last, consumer, x, y, z, Mth.sin(own), 0.25f, Mth.cos(own), p.size(i), 0.012f, THREAD, shaded(rgb, alpha, p.light(i)));
			default -> { }
		}
	}

	private static void shaft(AmbiencePool p, int i, PoseStack.Pose last, VertexConsumer consumer, float x, float y, float z, float alpha) {
		// from the ground towards the sun, turned to face the camera around its own length
		float dirX = sunX;
		float dirY = Math.max(sunY, 0.15f);
		float length = Mth.sqrt(dirX * dirX + dirY * dirY);
		dirX /= length;
		dirY /= length;
		float endX = x + dirX * SHAFT_LENGTH;
		float endY = y + dirY * SHAFT_LENGTH;
		float middleX = (x + endX) / 2f;
		float middleY = (y + endY) / 2f;
		// the side is across the beam and the view
		float sideX = -z * dirY;
		float sideY = z * dirX;
		float sideZ = middleX * dirY - middleY * dirX;
		float side = Mth.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
		if (side < 0.001f) {
			return;
		}
		float half = p.size(i) / side;
		sideX *= half;
		sideY *= half;
		sideZ *= half;
		int color = lightColor(p.rgb(i), alpha);
		float u0 = u(BEAM);
		float v0 = v(BEAM);
		float span = 1f / CELLS;
		consumer.addVertex(last, x + sideX, y + sideY, z + sideZ).setUv(u0, v0 + span).setColor(color);
		consumer.addVertex(last, x - sideX, y - sideY, z - sideZ).setUv(u0 + span, v0 + span).setColor(color);
		consumer.addVertex(last, endX - sideX, endY - sideY, z - sideZ).setUv(u0 + span, v0).setColor(color);
		consumer.addVertex(last, endX + sideX, endY + sideY, z + sideZ).setUv(u0, v0).setColor(color);
	}

	private static int shaded(int rgb, float alpha, float light) {
		int r = Math.round((rgb >> 16 & 0xFF) * light);
		int g = Math.round((rgb >> 8 & 0xFF) * light);
		int b = Math.round((rgb & 0xFF) * light);
		return Math.round(Math.min(1f, alpha) * 255f) << 24 | r << 16 | g << 8 | b;
	}

	/** Light adds up, ignoring alpha: how much of it shows is in the color. */
	private static int lightColor(int rgb, float alpha) {
		float share = Math.min(1f, alpha);
		return 0xFF000000 | Math.round((rgb >> 16 & 0xFF) * share) << 16 | Math.round((rgb >> 8 & 0xFF) * share) << 8 | Math.round((rgb & 0xFF) * share);
	}

	private static float u(int cell) {
		return (cell % CELLS) / (float) CELLS;
	}

	private static float v(int cell) {
		return (cell / CELLS) / (float) CELLS;
	}

	/** A square facing the camera, turned by an angle. */
	private static void billboard(PoseStack.Pose last, VertexConsumer consumer, float x, float y, float z, float half, float angle, int cell, int color) {
		float cos = Mth.cos(angle) * half;
		float sin = Mth.sin(angle) * half;
		float ax = leftX * cos + upX * sin;
		float ay = leftY * cos + upY * sin;
		float az = leftZ * cos + upZ * sin;
		float bx = upX * cos - leftX * sin;
		float by = upY * cos - leftY * sin;
		float bz = upZ * cos - leftZ * sin;
		float u0 = u(cell);
		float v0 = v(cell);
		float span = 1f / CELLS;
		consumer.addVertex(last, x + ax + bx, y + ay + by, z + az + bz).setUv(u0, v0).setColor(color);
		consumer.addVertex(last, x - ax + bx, y - ay + by, z - az + bz).setUv(u0 + span, v0).setColor(color);
		consumer.addVertex(last, x - ax - bx, y - ay - by, z - az - bz).setUv(u0 + span, v0 + span).setColor(color);
		consumer.addVertex(last, x + ax - bx, y + ay - by, z + az - bz).setUv(u0, v0 + span).setColor(color);
	}

	/** A wide, low band facing the camera sideways, upright: mist lying over the ground, which has to stay clear of it to show no straight edge. */
	private static void band(PoseStack.Pose last, VertexConsumer consumer, float x, float y, float z, float half, int cell, int color) {
		float ax = leftX * half;
		float ay = leftY * half;
		float az = leftZ * half;
		float height = half * MIST_HEIGHT;
		float u0 = u(cell);
		float v0 = v(cell);
		float span = 1f / CELLS;
		consumer.addVertex(last, x + ax, y + height, z + az).setUv(u0, v0).setColor(color);
		consumer.addVertex(last, x - ax, y + height, z - az).setUv(u0 + span, v0).setColor(color);
		consumer.addVertex(last, x - ax, y - height, z - az).setUv(u0 + span, v0 + span).setColor(color);
		consumer.addVertex(last, x + ax, y - height, z + az).setUv(u0, v0 + span).setColor(color);
	}

	/** A square lying flat, as a ring on water. */
	private static void flat(PoseStack.Pose last, VertexConsumer consumer, float x, float y, float z, float half, int cell, int color) {
		float u0 = u(cell);
		float v0 = v(cell);
		float span = 1f / CELLS;
		consumer.addVertex(last, x - half, y, z - half).setUv(u0, v0).setColor(color);
		consumer.addVertex(last, x + half, y, z - half).setUv(u0 + span, v0).setColor(color);
		consumer.addVertex(last, x + half, y, z + half).setUv(u0 + span, v0 + span).setColor(color);
		consumer.addVertex(last, x - half, y, z + half).setUv(u0, v0 + span).setColor(color);
	}

	/** A thin strip along a direction, turned to the camera around its length. */
	private static void line(PoseStack.Pose last, VertexConsumer consumer, float x, float y, float z, float dx, float dy, float dz, float length, float half, int cell, int color) {
		float size = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (size < 0.0001f) {
			return;
		}
		float ax = dx / size * length;
		float ay = dy / size * length;
		float az = dz / size * length;
		float sideX = ay * z - az * y;
		float sideY = az * x - ax * z;
		float sideZ = ax * y - ay * x;
		float side = Mth.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
		if (side < 0.0001f) {
			return;
		}
		sideX *= half / side;
		sideY *= half / side;
		sideZ *= half / side;
		float u0 = u(cell);
		float v0 = v(cell);
		float span = 1f / CELLS;
		consumer.addVertex(last, x + sideX, y + sideY, z + sideZ).setUv(u0, v0 + span).setColor(color);
		consumer.addVertex(last, x - sideX, y - sideY, z - sideZ).setUv(u0 + span, v0 + span).setColor(color);
		consumer.addVertex(last, x + ax - sideX, y + ay - sideY, z + az - sideZ).setUv(u0 + span, v0).setColor(color);
		consumer.addVertex(last, x + ax + sideX, y + ay + sideY, z + az + sideZ).setUv(u0, v0).setColor(color);
	}

	private static void ensureSheets() {
		if (normalType != null) {
			return;
		}
		NativeImage normal = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
		NativeImage light = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int cell = y / CELL * CELLS + x / CELL;
				// from -1 to 1 across its cell
				float u = ((x % CELL) + 0.5f) / CELL * 2f - 1f;
				float v = ((y % CELL) + 0.5f) / CELL * 2f - 1f;
				float value = Mth.clamp(picture(cell, u, v), 0f, 1f);
				int level = Math.round(value * 255f);
				normal.setPixelABGR(x, y, level << 24 | 0xFFFFFF);
				// additive drawing ignores alpha: the light is in the color channels
				light.setPixelABGR(x, y, 0xFF000000 | level << 16 | level << 8 | level);
			}
		}
		register(NORMAL_SHEET, normal);
		register(LIGHT_SHEET, light);
		normalType = OutlineRenderTypes.glow(NORMAL_SHEET);
		lightType = OutlineRenderTypes.light(LIGHT_SHEET);
	}

	private static void register(Identifier id, NativeImage image) {
		DynamicTexture texture = new DynamicTexture(() -> "Lumio " + id.getPath(), image);
		texture.upload();
		Minecraft.getInstance().getTextureManager().register(id, texture);
	}

	/** @return how much of a picture covers a point of its cell, which runs from -1 to 1 each way; every picture is gone before the cell's edge */
	private static float picture(int cell, float u, float v) {
		float d = Mth.sqrt(u * u + v * v);
		float edge = smooth((1f - d) / 0.35f);
		return switch (cell) {
			case DOT -> smooth((0.95f - d) / 0.7f);
			case FLAKE -> {
				float angle = (float) Math.atan2(v, u);
				float arms = (float) Math.pow(Math.abs(Math.cos(angle * 3f)), 12f);
				yield Math.max(smooth((0.3f - d) / 0.3f), arms * smooth((0.95f - d) / 0.6f));
			}
			case LEAF -> {
				float across = Math.abs(u) / Math.max(0.02f, 0.52f * (1f - v * v));
				yield smooth((1f - across) / 0.25f) * smooth((0.96f - Math.abs(v)) / 0.08f);
			}
			case RING -> smooth(1f - Math.abs(d - 0.76f) / 0.16f);
			case LINE -> smooth((1f - Math.abs(u) * 1.3f) / 0.8f) * smooth((1f - Math.abs(v) * 1.08f) / 0.5f);
			case PUFF -> {
				float cloud = 0.75f + 0.25f * (float) Math.sin(u * 5.1f + 1.3f) * (float) Math.cos(v * 4.3f + 0.4f);
				yield smooth((1f - d) / 0.95f) * cloud;
			}
			case BEAM -> smooth((1f - Math.abs(u) * 1.05f) / 0.9f) * smooth((v + 0.98f) / 0.5f) * smooth((0.98f - v) / 0.5f);
			case FIREFLY -> (float) (Math.exp(-d * d * 150f) + 0.30f * Math.exp(-d * d * 13f)) * edge;
			case THREAD -> {
				float wave = u - 0.25f * (float) Math.sin(v * 3.1f);
				yield smooth((1f - Math.abs(wave) * 4f) / 0.8f) * smooth((1f - Math.abs(v) * 1.05f) / 0.5f);
			}
			default -> 0f;
		};
	}

	private static float smooth(float value) {
		float t = Mth.clamp(value, 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
