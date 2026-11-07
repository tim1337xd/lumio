package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.SkyFeature;
import dev.tim1337.lumio.features.visual.world.SkyLook;
import dev.tim1337.lumio.features.visual.world.SkyMeteors;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws the shooting stars as streaks in the world: tested against its depth, so a far one goes down behind the hills. Nothing is allocated per frame. */
public final class SkyMeteorDraw {
	private static final int SIZE = 64;
	private static final Identifier SHEET = Lumio.id("sky_meteor");
	private static final float TAIL_SECONDS = 0.55f;
	private static final float HEAD_GLOW = 3.6f;
	private static final float WHITE_IN_OWN = 0.25f;
	private static final int[] NATURAL = {0xFFFFFF, 0xCFE0FF, 0xFFE7C4, 0xE6D8FF};
	private static RenderType type;
	private static SkyMeteors shown;
	private static float strength;
	private static float tail;
	private static int own;
	private static boolean natural;
	private static final SubmitNodeCollector.CustomGeometryRenderer DRAW = SkyMeteorDraw::draw;

	private SkyMeteorDraw() {
	}

	/** Submits the frame's streaks; nothing while none flies. */
	public static void collect(LevelRenderContext context) {
		SkyFeature sky = ModFeatures.SKY;
		SkyMeteors meteors = sky.meteors();
		if (meteors.count() == 0 || !OutlineRenderTypes.lightAvailable()) {
			return;
		}
		if (type == null) {
			// two pictures side by side: the streak, bright towards its head, and the soft glow of the head
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE * 2, SIZE, false);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					float u = (x + 0.5f) / SIZE * 2f - 1f;
					float v = (y + 0.5f) / SIZE * 2f - 1f;
					float across = Math.abs(u);
					float along = (y + 0.5f) / SIZE;
					int streak = Math.round((1f - across) * (1f - across) * (float) Math.pow(along, 1.2f) * 255f);
					int glow = Math.round(Mth.clamp((float) Math.exp(-(u * u + v * v) * 9f) * (1f - Mth.sqrt(u * u + v * v)), 0f, 1f) * 255f);
					image.setPixelABGR(x, y, 0xFF000000 | streak << 16 | streak << 8 | streak);
					image.setPixelABGR(x + SIZE, y, 0xFF000000 | glow << 16 | glow << 8 | glow);
				}
			}
			DynamicTexture texture = new DynamicTexture(() -> "Lumio meteor", image);
			texture.upload();
			Minecraft.getInstance().getTextureManager().register(SHEET, texture);
			type = OutlineRenderTypes.light(SHEET);
		}
		shown = meteors;
		strength = sky.meteorStrength();
		tail = TAIL_SECONDS * (0.3f + 1.7f * sky.starTrail());
		natural = sky.starColors() == SkyLook.Colors.NATURAL;
		own = sky.starRgb();
		PoseStack poses = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poses, type, DRAW);
	}

	private static void draw(PoseStack.Pose last, VertexConsumer consumer) {
		SkyMeteors m = shown;
		var camera = Minecraft.getInstance().gameRenderer.mainCamera();
		float leftX = camera.leftVector().x();
		float leftY = camera.leftVector().y();
		float leftZ = camera.leftVector().z();
		float upX = camera.upVector().x();
		float upY = camera.upVector().y();
		float upZ = camera.upVector().z();
		for (int i = 0; i < m.count(); i++) {
			float alpha = m.alpha(i) * strength;
			if (alpha <= 0.004f) {
				continue;
			}
			int rgb = natural ? NATURAL[(int) (m.hue[i] * NATURAL.length) % NATURAL.length] : mixWhite(own, WHITE_IN_OWN);
			int head = lightColor(rgb, alpha);
			float back = Math.min(m.age[i], tail * m.trail[i]) * m.speed[i];
			float hx = m.x[i];
			float hy = m.y[i];
			float hz = m.z[i];
			float tx = hx - m.dx[i] * back;
			float ty = hy - m.dy[i] * back;
			float tz = hz - m.dz[i] * back;
			// the side is across the streak and the view
			float sideX = m.dy[i] * hz - m.dz[i] * hy;
			float sideY = m.dz[i] * hx - m.dx[i] * hz;
			float sideZ = m.dx[i] * hy - m.dy[i] * hx;
			float side = Mth.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
			if (side < 0.001f) {
				continue;
			}
			float half = m.size[i] / side;
			sideX *= half;
			sideY *= half;
			sideZ *= half;
			consumer.addVertex(last, tx + sideX, ty + sideY, tz + sideZ).setUv(0f, 0f).setColor(head);
			consumer.addVertex(last, tx - sideX, ty - sideY, tz - sideZ).setUv(0.5f, 0f).setColor(head);
			consumer.addVertex(last, hx - sideX, hy - sideY, hz - sideZ).setUv(0.5f, 1f).setColor(head);
			consumer.addVertex(last, hx + sideX, hy + sideY, hz + sideZ).setUv(0f, 1f).setColor(head);
			// the glow of the head, facing the camera
			float glow = m.size[i] * HEAD_GLOW;
			consumer.addVertex(last, hx + leftX * glow + upX * glow, hy + leftY * glow + upY * glow, hz + leftZ * glow + upZ * glow).setUv(0.5f, 0f).setColor(head);
			consumer.addVertex(last, hx - leftX * glow + upX * glow, hy - leftY * glow + upY * glow, hz - leftZ * glow + upZ * glow).setUv(1f, 0f).setColor(head);
			consumer.addVertex(last, hx - leftX * glow - upX * glow, hy - leftY * glow - upY * glow, hz - leftZ * glow - upZ * glow).setUv(1f, 1f).setColor(head);
			consumer.addVertex(last, hx + leftX * glow - upX * glow, hy + leftY * glow - upY * glow, hz + leftZ * glow - upZ * glow).setUv(0.5f, 1f).setColor(head);
		}
	}

	private static int mixWhite(int rgb, float share) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return Math.round(r + (255 - r) * share) << 16 | Math.round(g + (255 - g) * share) << 8 | Math.round(b + (255 - b) * share);
	}

	/** Light adds up, ignoring alpha: how much of it shows is in the color. */
	private static int lightColor(int rgb, float alpha) {
		float share = Math.min(1f, alpha);
		return 0xFF000000 | Math.round((rgb >> 16 & 0xFF) * share) << 16 | Math.round((rgb >> 8 & 0xFF) * share) << 8 | Math.round((rgb & 0xFF) * share);
	}
}
