package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.screen.MenusFeature;
import dev.tim1337.lumio.render.MenuWorld;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Animated background of the out-of-world screens, replacing the panorama: a dark night with soft lights, small stars and falling stars with
 * tails. They swerve around the mouse, the whole sky shifts slightly with the mouse position (parallax), and stars near it light up.
 * <p>
 * Star positions are computed from time, so no per-star state is kept, and each star is drawn from two textures: a light and a tail.
 */
public final class MenuBackground {
	private static final int TEXTURE = 128;
	private static final Identifier BLOB = Lumio.id("menu_blob");
	private static final Identifier TRAIL = Lumio.id("menu_trail");
	private static final int TRAIL_W = 128;
	private static final int TRAIL_H = 8;
	/** Distance in menu pixels within which a star reacts to the mouse. */
	private static final float NOTICE = 320f;

	private static SmoothTexture blobTexture;
	private static SmoothTexture trailTexture;
	private static long startNanos;
	private static long lastNanos;
	private static float seconds;

	/** Smoothed mouse position and velocity, which the stars react to. */
	private static float mouseX;
	private static float mouseY;
	private static float windX;
	private static float windY;
	private static boolean mouseKnown;

	private MenuBackground() {
	}

	private static void ensureTextures() {
		if (blobTexture != null) {
			return;
		}
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEXTURE, TEXTURE, false);
		for (int y = 0; y < TEXTURE; y++) {
			for (int x = 0; x < TEXTURE; x++) {
				float dx = (x + 0.5f) / TEXTURE * 2f - 1f;
				float dy = (y + 0.5f) / TEXTURE * 2f - 1f;
				float distance = Mth.sqrt(dx * dx + dy * dy);
				// smooth radial falloff, softer than a disc, so it reads as light rather than a shape
				float fall = Mth.clamp(1f - distance, 0f, 1f);
				float alpha = fall * fall * (3f - 2f * fall);
				image.setPixelABGR(x, y, (Math.round(alpha * 255f) << 24) | 0xFFFFFF);
			}
		}
		blobTexture = new SmoothTexture("Lumio menu light", image);
		Minecraft.getInstance().getTextureManager().register(BLOB, blobTexture);

		// star tail: transparent at the far end, brightening towards the star, thin at the edges
		NativeImage tail = new NativeImage(NativeImage.Format.RGBA, TRAIL_W, TRAIL_H, false);
		for (int y = 0; y < TRAIL_H; y++) {
			float across = (y + 0.5f) / TRAIL_H * 2f - 1f;
			float profile = (float) Math.exp(-across * across * 3.2f);
			for (int x = 0; x < TRAIL_W; x++) {
				float along = (x + 0.5f) / TRAIL_W;
				float alpha = (float) Math.pow(along, 1.7) * profile;
				tail.setPixelABGR(x, y, (Math.round(Mth.clamp(alpha, 0f, 1f) * 255f) << 24) | 0xFFFFFF);
			}
		}
		trailTexture = new SmoothTexture("Lumio menu trail", tail);
		Minecraft.getInstance().getTextureManager().register(TRAIL, trailTexture);
	}

	/** Draws a soft light of any width and height centered at a point. */
	private static void light(GuiGraphicsExtractor graphics, float cx, float cy, float halfWidth, float halfHeight, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, BLOB, Math.round(cx - halfWidth), Math.round(cy - halfHeight), 0f, 0f, Math.round(halfWidth * 2f), Math.round(halfHeight * 2f),
			TEXTURE, TEXTURE, TEXTURE, TEXTURE, color);
	}

	private static float hash(int i, int salt) {
		int hash = i * 73856093 ^ salt * 19349663 ^ 0x5bd1e995;
		hash = (hash ^ (hash >>> 15)) * 0x2c1b3c6d;
		hash ^= hash >>> 12;
		return (hash & 0xFFFF) / 65535f;
	}

	/** Advances the background clock and tracks the mouse. */
	private static void step(int width, int height) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		seconds += dt * ModFeatures.MENUS.backgroundSpeed();
		Minecraft client = Minecraft.getInstance();
		double scale = client.getWindow().getGuiScale();
		float x = (float) (client.mouseHandler.xpos() / scale);
		float y = (float) (client.mouseHandler.ypos() / scale);
		if (!mouseKnown) {
			mouseKnown = true;
			mouseX = x;
			mouseY = y;
		}
		if (dt > 0f) {
			float follow = 1f - (float) Math.exp(-dt * 3.2f);
			float vx = (x - mouseX) / dt;
			float vy = (y - mouseY) / dt;
			mouseX += (x - mouseX) * follow;
			mouseY += (y - mouseY) * follow;
			// mouse "wind" is its smoothed velocity, decaying while the mouse rests
			float wind = 1f - (float) Math.exp(-dt * 2.2f);
			windX += (Mth.clamp(vx, -1400f, 1400f) - windX) * wind;
			windY += (Mth.clamp(vy, -1400f, 1400f) - windY) * wind;
		}
	}

	/** Draws the night sky and the stars. */
	public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		draw(graphics, width, height, 1f);
	}

	/**
	 * @param fade opacity from 0 to 1: fades in and out with the hosting screen, such as the startup animation
	 */
	public static void draw(GuiGraphicsExtractor graphics, int width, int height, float fade) {
		draw(graphics, width, height, fade, false);
	}

	private static void draw(GuiGraphicsExtractor graphics, int width, int height, float fade, boolean light) {
		if (startNanos == 0) {
			startNanos = System.nanoTime();
		}
		ensureTextures();
		step(width, height);
		if (!light && ModFeatures.MENUS.backgroundStyle() == MenusFeature.BackgroundStyle.MINECRAFT
			&& MenuWorld.draw(graphics, mouseX / Math.max(1f, width), mouseY / Math.max(1f, height))) {
			// the world fills the screen and looks the same at startup and in the menu, so the startup fade does not apply to it; edges are darkened like the stars background
			Ui.setZoom(1f);
			Ui edge = new Ui(graphics, Minecraft.getInstance().font, MacTheme.DARK.palette(), 0xFF000000, 0f, false);
			edge.verticalGradient(0, 0, width, height / 5, 0x50000000, 0x00000000);
			edge.verticalGradient(0, height - height / 3, width, height / 3, 0x00000000, 0x80000000);
			return;
		}
		float t = seconds;
		Ui.setZoom(1f);
		Minecraft client = Minecraft.getInstance();
		int accent = ModFeatures.MENU.accentArgb();
		Ui ui = new Ui(graphics, client.font, MacTheme.DARK.palette(), accent, 0f, false);
		int[] palette = ModFeatures.MENUS.starsPalette();
		float f = Mth.clamp(fade, 0f, 1f);
		float shiftX = (mouseX - width / 2f) * -0.025f;
		float shiftY = (mouseY - height / 2f) * -0.025f;
		float reach = Math.max(width, height);

		// the night: dark, with a hint of the first glow color towards the bottom
		int low = Ui.mix(0xFF0A0710, 0xFF000000 | palette[0], 0.16f);
		ui.verticalGradient(0, 0, width, height, Ui.withOpacity(0xFF040308, f), Ui.withOpacity(low, f));
		// large soft drifting lights in the glow colors, shifting slightly against the mouse
		for (int i = 0; i < 4; i++) {
			float fx = 0.5f + 0.40f * (float) Math.sin(t * (0.021f + 0.007f * i) * Mth.TWO_PI + i * 1.9f);
			float fy = 0.5f + 0.36f * (float) Math.sin(t * (0.017f + 0.009f * (i % 3)) * Mth.TWO_PI + i * 2.7f);
			float size = reach * (0.34f + 0.05f * (i % 3)) * (1f + 0.07f * (float) Math.sin(t * 0.3f + i));
			light(graphics, width * fx + shiftX * (0.6f + 0.2f * i), height * fy + shiftY * (0.6f + 0.2f * i), size, size * 0.8f,
				Ui.withOpacity(0xFF000000 | palette[i % palette.length], 0.17f * f));
		}

		// large soft glowing dots floating slowly; those near the mouse light up
		for (int i = 0; i < (light || !ModFeatures.MENUS.starsPulse() ? 0 : 9); i++) {
			float depth = 0.25f + 0.75f * hash(i, 11);
			float x = hash(i, 12) * width + 18f * (float) Math.sin(t * (0.09f + 0.06f * hash(i, 13)) + i);
			float y = hash(i, 14) * height + 12f * (float) Math.cos(t * (0.08f + 0.06f * hash(i, 15)) + i * 2f);
			x = Mth.positiveModulo(x, width + 120f) - 60f;
			y = Mth.positiveModulo(y, height + 120f) - 60f;
			float near = Mth.clamp(1f - (float) Math.hypot(x - mouseX, y - mouseY) / (NOTICE * 1.4f), 0f, 1f);
			float pulse = 0.55f + 0.45f * (float) Math.sin(t * (0.5f + hash(i, 16)) + i);
			int color = 0xFF000000 | palette[i % palette.length];
			float size = 14f + 30f * depth;
			light(graphics, x, y, size * (1f + 0.5f * near), size * (1f + 0.5f * near), Ui.withOpacity(color, (0.20f + 0.35f * near) * pulse * f));
			light(graphics, x, y, size * 0.22f, size * 0.22f, Ui.withOpacity(Ui.mix(color, 0xFFFFFFFF, 0.6f), (0.5f + 0.4f * near) * pulse * f));
		}

		streaks(graphics, ui, width, height, t, palette, f, light ? 34 : ModFeatures.MENUS.starsCount());

		// vignette: darker edges keep the eye in the center and the text there readable
		ui.fill(0, 0, width, height, Ui.withOpacity(0x30000000, f));
		ui.verticalGradient(0, 0, width, height / 4, Ui.withOpacity(0x70000000, f), 0x00000000);
		ui.verticalGradient(0, height - height / 3, width, height / 3, 0x00000000, Ui.withOpacity(0x98000000, f));
	}

	/**
	 * @return the position of a falling star at a time: on its straight path, pushed sideways near the mouse so it passes around it while keeping its
	 *         heading. It keeps to the side it is already on, deflecting smoothly as the mouse approaches and returning after it passes.
	 */
	private static float[] starAt(float time, float baseAngle, float speed, float x0, float y0, float marginX, float marginY, float length, float depth, float[] out) {
		float bx = (float) Mth.positiveModulo(x0 + Math.cos(baseAngle) * speed * time, marginX) - length - 40f;
		float by = (float) Mth.positiveModulo(y0 + Math.sin(baseAngle) * speed * time, marginY) - length - 40f;
		float dx = bx - mouseX;
		float dy = by - mouseY;
		float distance = (float) Math.sqrt(dx * dx + dy * dy);
		float reach = Mth.clamp(1f - distance / NOTICE, 0f, 1f);
		float near = reach * reach * reach * (reach * (reach * 6f - 15f) + 10f);
		float perpX = (float) -Math.sin(baseAngle);
		float perpY = (float) Math.cos(baseAngle);
		float lateral = dx * perpX + dy * perpY;
		float side = Mth.clamp(lateral / 16f, -1f, 1f);
		if (Math.abs(lateral) < 0.5f) {
			side = 1f;
		}
		float push = 55f * near * (0.45f + 0.55f * depth);
		// stars are also carried slightly with the moving mouse, gently, as its wind decays slowly
		float carry = 0.02f * near * (0.4f + 0.6f * depth);
		out[0] = bx + perpX * side * push + windX * carry;
		out[1] = by + perpY * side * push + windY * carry;
		out[2] = near;
		return out;
	}

	/**
	 * Draws the falling stars: a head with a tail crossing from top right to bottom left, faster for closer stars. Only stars near the mouse react
	 * to it: they pass around it and light up. They never change direction, they only shift sideways off their path.
	 */
	private static void streaks(GuiGraphicsExtractor graphics, Ui ui, int width, int height, float t, int[] palette, float fade, int count) {
		var pose = graphics.pose();
		float[] now = new float[3];
		float[] later = new float[3];
		float scale = ModFeatures.MENUS.starsSize();
		for (int i = 0; i < count; i++) {
			float depth = 0.3f + 0.7f * hash(i, 21);
			depth *= depth;
			depth = 0.2f + 0.8f * depth;
			float speed = 16f + 150f * depth;
			float baseAngle = 2.62f + 0.35f * (hash(i, 22) - 0.5f);
			float length = (40f + 210f * depth) * (0.7f + 0.6f * hash(i, 23)) * scale;
			float marginX = width + 2f * length + 80f;
			float marginY = height + 2f * length + 80f;
			float x0 = hash(i, 24) * marginX;
			float y0 = hash(i, 25) * marginY;
			starAt(t, baseAngle, speed, x0, y0, marginX, marginY, length, depth, now);
			starAt(t + 0.06f, baseAngle, speed, x0, y0, marginX, marginY, length, depth, later);
			float x = now[0];
			float y = now[1];
			float near = now[2];
		// the tail points back along the actual path, which curves slightly where the star passes the mouse
			float angle = (float) Math.atan2(later[1] - y, later[0] - x);
			float size = length * (1f + 0.9f * near);
			float thickness = (2.2f + 4.5f * depth) * scale;
			float bright = (0.35f + 0.65f * depth) * (0.6f + 0.4f * near) * fade;
			int color = Ui.mix(0xFF000000 | palette[i % palette.length], 0xFFFFFFFF, 0.25f + 0.35f * near);
			pose.pushMatrix();
			pose.translate(x, y);
			pose.rotate(angle);
			graphics.blit(RenderPipelines.GUI_TEXTURED, TRAIL, Math.round(-size), -Math.round(thickness / 2f), 0f, 0f, Math.round(size), Math.round(thickness), TRAIL_W, TRAIL_H, TRAIL_W, TRAIL_H,
				Ui.withOpacity(color, bright));
			pose.popMatrix();
			// the head: a small bright light with a wider glow that strengthens near the mouse
			float head = (3f + 5f * depth) * scale;
			light(graphics, x, y, head * (1.6f + 1.8f * near), head * (1.6f + 1.8f * near), Ui.withOpacity(color, (0.30f + 0.4f * near) * bright));
			light(graphics, x, y, head * 0.7f, head * 0.7f, Ui.withOpacity(0xFFFFFFFF, Math.min(1f, bright * 1.3f)));
		}
	}
}
