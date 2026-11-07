package dev.tim1337.lumio.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.HeldItem;
import dev.tim1337.lumio.features.visual.world.HeldItemOutlineFeature;
import dev.tim1337.lumio.features.visual.world.RadianceLook;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

import java.util.Arrays;

/**
 * Radiance of living entities: a glow or outline around a player, a mob, or the player's first person hand, in the item outline color, plus a
 * light inside the body itself.
 * <p>
 * The outline works like the one for non-flat items: the model is drawn again as a flat colored silhouette, offset sideways in a ring around
 * the real one and pushed away from the camera, so only what sticks out around the body is visible. A glow uses several rings, further out and
 * fainter. The inner light is the model drawn once more over itself, translucent and fullbright, in the color.
 */
public final class EntityRadiance {
	private static final int DIRECTIONS = 12;
	/** Size of one model pixel in blocks; the outline width is configured in pixels. */
	private static final float RADIUS_PER_PIXEL = 0.028f;
	private static final float BASE_DEPTH_BIAS = 0.004f;
	private static final float DEPTH_BIAS_PER_RADIUS = 14f;
	private static final float MIN_DISTANCE = 0.1f;
	private static final Vector3f OFFSET = new Vector3f();
	private static final Vector3f POSITION = new Vector3f();
	private static final Matrix4f INVERSE = new Matrix4f();

	private EntityRadiance() {
	}

	/** Submits the glow around a single model part: the player's first person arm. */
	public static void submitPart(ModelPart part, Identifier texture, PoseStack pose, SubmitNodeCollector collector) {
		if (!OutlineRenderTypes.available()) {
			return;
		}
		HeldItemOutlineFeature feature = ModFeatures.HELD_ITEM_OUTLINE;
		RadianceLook.Style shown = feature.shownStyle();
		// Echo is an after-glow of what the hand pass draws: the hand is in it by being drawn, and gets no rings
		if (shown == RadianceLook.Style.ECHO) {
			feature.echoHand();
			return;
		}
		OutlineStyle style = feature.handStyle();
		RenderType silhouette = OutlineRenderTypes.silhouette(texture);
		// the hand is rendered in view space, not world space: screen-sideways is x and y there
		rings(style, 0f, true, pose, (ringPose, color) -> collector.submitModelPart(part, ringPose, silhouette, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color, null));
	}

	/** Draws one silhouette copy at a pose, in a color. */
	private interface Copy {
		void draw(PoseStack pose, int color);
	}

	private static int argb(int rgb, float alpha) {
		return (Math.round(Math.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
	}

	/**
	 * Submits the rings of copies: one tight ring for an outline; for a glow several, further out and fainter, together forming a soft light. Every
	 * copy is offset sideways in the screen plane and pushed away from the camera so the entity itself hides it.
	 *
	 * @param smoothness how round and soft a glow is: more rings, further out and fainter, so it follows the edges less closely
	 * @param viewSpace  whether the pose is in view space (the first person hand) rather than world space
	 */
	private static void rings(OutlineStyle style, float smoothness, boolean viewSpace, PoseStack pose, Copy copy) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		float strength = style.strength();
		float base = Math.max(0.3f, style.width()) * RADIUS_PER_PIXEL;
		float[] scales;
		float[] alphas;
		int[] colors;
		switch (style.kind()) {
			case NEON -> {
				// rim light: a thin, nearly white line right at the edge, surrounded by a quickly fading soft light in the color
				scales = new float[]{0.45f, 1.2f, 2.1f};
				alphas = new float[]{Math.min(1f, 0.9f + 0.1f * strength), 0.5f * strength, 0.18f * strength};
				colors = new int[]{lighter(lighter(style.rgb())), style.rgb(), style.rgb()};
			}
			default -> {
				// glow: rings further out and fainter; smoother glows reach further with more, softer rings, so they look round
				float s = Math.clamp(smoothness, 0f, 1f);
				scales = s > 0.05f ? new float[]{0.7f, 1.5f + 0.4f * s, 2.5f + 1.2f * s, 3.6f + 2f * s} : new float[]{0.7f, 1.5f, 2.5f};
				alphas = s > 0.05f
					? new float[]{Math.min(1f, (0.95f - 0.35f * s) * strength + 0.2f), (0.45f - 0.08f * s) * strength, (0.2f + 0.04f * s) * strength, 0.12f * s * strength}
					: new float[]{Math.min(1f, 0.95f * strength + 0.2f), 0.45f * strength, 0.2f * strength};
				colors = new int[scales.length];
				Arrays.fill(colors, style.rgb());
			}
		}
		int directions = style.kind() == OutlineKind.GLOW && smoothness > 0.5f ? 16 : DIRECTIONS;
		float distance = Math.max(pose.last().pose().getTranslation(POSITION).length(), MIN_DISTANCE);
		for (int ring = 0; ring < scales.length; ring++) {
			float radius = base * scales[ring];
			int color = argb(colors[ring], alphas[ring]);
			if ((color >>> 24) == 0) {
				continue;
			}
			float depth = BASE_DEPTH_BIAS + radius * DEPTH_BIAS_PER_RADIUS;
			float scale = 1f + depth / distance;
			for (int i = 0; i < directions; i++) {
				double angle = Math.PI * 2.0 * i / directions + (ring % 2 == 1 ? Math.PI / directions : 0.0);
				float right = (float) Math.cos(angle) * radius;
				float up = (float) Math.sin(angle) * radius;
				if (viewSpace) {
					// in view space x points right and y up on the screen
					OFFSET.set(right, up, 0f);
				} else {
					// poses are world-oriented with the camera rotation applied separately, so the camera vectors give the screen-sideways directions
					OFFSET.set(camera.leftVector()).mul(-right).fma(up, camera.upVector());
				}
				pose.last().pose().invert(INVERSE).transformDirection(OFFSET);
				pose.pushPose();
				pose.translate(OFFSET.x, OFFSET.y, OFFSET.z);
				// scaling about the camera moves every point along its view ray: further away, at the same screen position
				pose.last().pose().scaleLocal(scale, scale, scale);
				copy.draw(pose, color);
				pose.popPose();
			}
		}
	}

	private static int lighter(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return (Math.round(r + (255 - r) * 0.45f) << 16) | (Math.round(g + (255 - g) * 0.45f) << 8) | Math.round(b + (255 - b) * 0.45f);
	}
}
