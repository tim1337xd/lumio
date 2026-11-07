package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.render.GameTextures;

import net.minecraft.util.Mth;

/**
 * Small animated illustrations of the features for the setup screen, drawn with the menu's shapes. They are not the features themselves but
 * simplified depictions; everything in them is animated over time.
 */
public final class FeaturePreview {
	private FeaturePreview() {
	}

	private static void line(Ui ui, float x0, float y0, float x1, float y1, float thickness, int color) {
		float length = (float) Math.hypot(x1 - x0, y1 - y0);
		if (length < 1f) {
			return;
		}
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.rotate((float) Math.atan2(y1 - y0, x1 - x0));
		ui.roundedSmooth(0, -thickness / 2f, length, thickness, thickness / 2f, color);
		pose.popMatrix();
	}

	/** Draws a trail: a line made of a few pieces, brightest at its head. */
	private static void trail(Ui ui, float hx, float hy, float dx, float dy, float length, float thickness, int color) {
		int pieces = 8;
		for (int i = 0; i < pieces; i++) {
			float a = i / (float) pieces;
			float b = (i + 1) / (float) pieces;
			line(ui, hx - dx * length * b, hy - dy * length * b, hx - dx * length * a, hy - dy * length * a, thickness * (1f - 0.6f * b),
				Ui.withOpacity(color, (1f - b) * 0.9f + 0.05f));
		}
		ui.circle(Math.round(hx), Math.round(hy), Math.max(1, Math.round(thickness)), 0xFFFFFFFF);
	}

	private static void glowDot(Ui ui, float cx, float cy, float radius, int color, float amount) {
		for (int i = 0; i < 4; i++) {
			float r = radius * (1f + (3 - i) * 0.7f);
			ui.circle(Math.round(cx), Math.round(cy), Math.max(1, Math.round(r)), Ui.withOpacity(color, amount * (0.07f + 0.08f * i)));
		}
		ui.circle(Math.round(cx), Math.round(cy), Math.max(1, Math.round(radius * 0.5f)), Ui.withOpacity(0xFFFFFFFF, amount));
	}

	private static void pane(Ui ui, int x, int y, int w, int h, float radius) {
		ui.rounded(x, y, w, h, radius, 0xCC12141F);
		ui.ring(x, y, w, h, radius, 1, 0x40FFFFFF);
	}

	private static void bar(Ui ui, int x, int y, int w, int h, float fraction, int color) {
		ui.rounded(x, y, w, h, h / 2f, 0x66000000);
		ui.rounded(x, y, Math.max(h, Math.round(w * fraction)), h, h / 2f, color);
	}

	private static void textBar(Ui ui, int x, int y, int w, int alpha) {
		ui.rounded(x, y, w, 4, 2f, Ui.withOpacity(0xFFFFFFFF, alpha / 255f));
	}

	/**
	 * Draws the backdrop of a page's live preview with rounded corners, as in the mockups: an evening sky over dark ground, in flat gradients so
	 * the corners stay round without clipping.
	 */
	public static void backdrop(Ui ui, int x, int y, int w, int h, float radius) {
		int r = Math.min(Math.round(radius), h / 4);
		int horizon = y + Math.round(h * HORIZON);
		ui.rounded(x, y, w, 2 * r, r, SKY_TOP);
		int blend = HORIZON_BLEND / 2;
		ui.verticalGradient(x, y + r, w, horizon - blend - y - r, SKY_TOP, SKY_BOTTOM);
		// a short blend instead of a hard line between sky and ground
		ui.verticalGradient(x, horizon - blend, w, HORIZON_BLEND, SKY_BOTTOM, GROUND_TOP);
		ui.rounded(x, y + h - 2 * r, w, 2 * r, r, GROUND_BOTTOM);
		ui.verticalGradient(x, horizon + blend, w, y + h - r - horizon - blend, GROUND_TOP, GROUND_BOTTOM);
	}

	// the colors of the mockups' preview backdrop over a dark window, made opaque
	private static final int SKY_TOP = 0xFF2B3C6B;
	private static final int SKY_BOTTOM = 0xFF1A1F33;
	private static final int GROUND_TOP = 0xFF1A3023;
	private static final int GROUND_BOTTOM = 0xFF14271C;
	/** Where the sky ends, as a share of the height. */
	private static final float HORIZON = 0.55f;
	/** Height of the blend between sky and ground, in menu pixels. */
	private static final int HORIZON_BLEND = 10;

	/** Draws the backdrop of a preview: a simplified in-game view with sky, blocky hills and a ground of game textures. */
	private static void scene(Ui ui, int x, int y, int w, int h, boolean night) {
		int tile = 10;
		int groundTop = y + h - tile * 3;
		int horizon = groundTop;
		ui.verticalGradient(x, y, w, horizon - y, night ? 0xFF080D2A : 0xFF4F8FE0, night ? 0xFF2B3C78 : 0xFFBBD9F4);
		// hills as block columns of varying height
		int hill = night ? 0xFF0F2030 : 0xFF6C9E62;
		int farHill = night ? 0xFF132438 : 0xFF86B07C;
		for (int column = 0; column * 8 < w + 8; column++) {
			int far = 12 + (int) (7f * Math.sin(column * 0.55f + 1f) + 4f * Math.sin(column * 1.3f));
			int near = 4 + (int) (5f * Math.sin(column * 0.8f + 4f) + 2f * Math.sin(column * 2.1f));
			ui.fill(x + column * 8, horizon - far, 8, far, farHill);
			ui.fill(x + column * 8, horizon - near, 8, near, hill);
		}
		if (!night) {
			// a couple of clouds
			ui.rounded(x + w / 5, y + 8, 38, 9, 4f, 0xCCFFFFFF);
			ui.rounded(x + w / 5 + 8, y + 3, 24, 9, 4f, 0xCCFFFFFF);
			ui.rounded(x + w * 3 / 4, y + 16, 30, 8, 4f, 0xAAFFFFFF);
		}
		var textures = GameTextures.get();
		int shade = night ? 0xFF8A94B8 : 0xFFFFFFFF;
		int grass = night ? 0xFF4E6A48 : 0xFF8DBB55;
		for (int column = 0; column * tile < w; column++) {
			int tx = x + column * tile;
			textures.blit(ui.graphics, 1, tx, groundTop, tile, shade);
			textures.blit(ui.graphics, 15, tx, groundTop, tile, grass);
			textures.blit(ui.graphics, 2, tx, groundTop + tile, tile, shade);
			textures.blit(ui.graphics, 2, tx, groundTop + tile * 2, tile, Ui.mix(shade, 0xFF000000, 0.12f));
		}
	}

	/** Draws the preview of a feature into the box. */
	public static void draw(Ui ui, String id, int x, int y, int w, int h, float t, int accent) {
		var graphics = ui.graphics;
		graphics.enableScissor(x, y, x + w, y + h);
		int cx = x + w / 2;
		int cy = y + h / 2;
		switch (id) {
			case "sky" -> {
				scene(ui, x, y, w, h, true);
				for (int i = 0; i < 22; i++) {
					float tw = 0.5f + 0.5f * (float) Math.sin(t * (1f + i % 5 * 0.4f) + i * 1.7f);
					ui.fill(x + (i * 53) % w, y + (i * 29) % (h - 6), 1 + i % 2, 1 + i % 2, Ui.withOpacity(0xFFFFFFFF, 0.25f + 0.6f * tw));
				}
				for (int k = 0; k < 3; k++) {
					float p = ((t * 0.35f + k * 0.37f) % 1f);
					trail(ui, x + w * (1.1f - 1.3f * p), y + h * (0.05f + 0.35f * k + 0.5f * p * 0.5f), -0.85f, -0.5f, 34f + 10f * k, 1.4f, 0xFFB57BFF);
				}
			}
			case "ambience" -> {
				scene(ui, x, y, w, h, true);
				for (int i = 0; i < 3; i++) {
					float drift = (float) Math.sin(t * 0.3f + i * 2f) * 14f;
					ui.rounded(Math.round(x + w * (0.1f + 0.3f * i) + drift), y + h - 24 - i * 3, 70, 16, 8f, Ui.withOpacity(0xFFB8E0C8, 0.10f));
				}
				for (int i = 0; i < 9; i++) {
					float fx = x + w * ((i * 0.37f + 0.1f) % 1f) + (float) Math.sin(t * 0.7f + i) * 8f;
					float fy = y + h * (0.2f + (i * 0.23f) % 0.6f) + (float) Math.cos(t * 0.5f + i * 1.3f) * 6f;
					glowDot(ui, fx, fy, 1.5f, 0xFFC8FF5A, 0.5f + 0.5f * (float) Math.sin(t + i));
				}
			}
			case "info_bar" -> {
				scene(ui, x, y, w, h, false);
				pane(ui, cx - 62, y + 18, 124, 22, 11f);
				ui.icon("clock", cx - 54, y + 23, 12, accent);
				ui.text("18:42", cx - 38, y + 25, 0xFFF2F2F7);
				ui.icon("gauge", cx + 4, y + 23, 12, accent);
				ui.text("120", cx + 20, y + 25, 0xFFF2F2F7);
			}
			case "hotbar" -> {
				scene(ui, x, y, w, h, false);
				pane(ui, cx - 80, y + h - 40, 160, 24, 12f);
				float sel = (float) ((Math.sin(t * 1.4f) * 0.5 + 0.5) * 8);
				ui.rounded(Math.round(cx - 78 + sel * 17), y + h - 38, 17, 20, 9f, Ui.withOpacity(accent, 0.7f));
				for (int i = 0; i < 9; i++) {
					ui.rounded(cx - 72 + i * 17, y + h - 33, 6, 6, 2f, Ui.withOpacity(0xFFFFFFFF, 0.5f));
				}
			}
			case "status_bars" -> {
				scene(ui, x, y, w, h, false);
				bar(ui, cx - 70, cy - 18, 140, 8, 0.8f + 0.1f * (float) Math.sin(t), SystemColors.RED);
				bar(ui, cx - 70, cy - 2, 140, 8, 0.6f, SystemColors.ORANGE);
				bar(ui, cx - 70, cy + 14, 140, 5, 0.35f + 0.3f * (0.5f + 0.5f * (float) Math.sin(t * 0.8f)), SystemColors.GREEN);
			}
			case "armor_hud", "inventory_hud" -> {
				scene(ui, x, y, w, h, false);
				boolean armor = id.equals("armor_hud");
				int cols = armor ? 4 : 3;
				int rows = armor ? 1 : 3;
				int pw = cols * 22 + 12;
				int ph = rows * 22 + 12;
				pane(ui, cx - pw / 2, cy - ph / 2, pw, ph, 8f);
				for (int r = 0; r < rows; r++) {
					for (int c = 0; c < cols; c++) {
						int color = (r + c + (armor ? 0 : 1)) % 3 == 0 ? SystemColors.TEAL : (r + c) % 3 == 1 ? 0xFFFFC53A : SystemColors.GRAY;
						ui.rounded(cx - pw / 2 + 8 + c * 22, cy - ph / 2 + 8 + r * 22, 14, 14, 3f, Ui.withOpacity(color, 0.85f));
					}
				}
			}
			case "scoreboard_hud", "tab_list_hud" -> {
				scene(ui, x, y, w, h, false);
				boolean tab = id.equals("tab_list_hud");
				int pw = tab ? 150 : 100;
				pane(ui, cx - pw / 2, y + 10, pw, h - 20, 8f);
				textBar(ui, cx - pw / 2 + 10, y + 18, pw / 2, 230);
				for (int i = 0; i < 4; i++) {
					if (tab) {
						ui.rounded(cx - pw / 2 + 10, y + 30 + i * 11, 8, 8, 2f, Ui.withOpacity(accent, 0.8f));
					}
					textBar(ui, cx - pw / 2 + (tab ? 24 : 10), y + 32 + i * 11, 30 + (i * 23) % 40, 140);
				}
			}
			case "music" -> {
				scene(ui, x, y, w, h, false);
				pane(ui, cx - 85, cy - 22, 170, 44, 10f);
				ui.rounded(cx - 79, cy - 16, 32, 32, 6f, Ui.withOpacity(accent, 0.9f));
				ui.icon("note", cx - 70, cy - 7, 14, 0xFFFFFFFF);
				String line = "Sing along with me";
				int tx = cx - 40;
				ui.text(line, tx, cy - 8, Ui.withOpacity(accent, 0.7f));
				// the highlight sweeps over the letters from left to right
				float sung = (t * 0.45f) % 1.3f;
				int cut = Math.round(Mth.clamp(sung, 0f, 1f) * ui.width(line));
				ui.graphics.enableScissor(tx, cy - 12, tx + cut, cy + 6);
				ui.text(line, tx, cy - 8, 0xFFFFFFFF);
				ui.graphics.disableScissor();
				bar(ui, cx - 40, cy + 8, 110, 3, 0.3f + 0.4f * (0.5f + 0.5f * (float) Math.sin(t * 0.5f)), 0xFFFFFFFF);
			}
			case "held_item_outline" -> {
				scene(ui, x, y, w, h, false);
				float pulse = 0.6f + 0.4f * (float) Math.sin(t * 2f);
				glowDot(ui, cx, cy, 16f, SystemColors.TEAL, pulse);
				ui.rounded(cx - 14, cy - 14, 28, 28, 5f, 0xFF3A8DFF);
				ui.ring(cx - 15, cy - 15, 30, 30, 6f, 2, Ui.withOpacity(0xFF9FE8FF, pulse));
			}
			case "held_item_transform" -> {
				scene(ui, x, y, w, h, false);
				var pose = ui.graphics.pose();
				pose.pushMatrix();
				pose.translate(cx, cy);
				float swing = (float) Math.sin(t * 2.2f) * 0.6f;
				pose.rotate(swing - 0.6f);
				float scale = 1f + 0.12f * (float) Math.sin(t * 1.1f);
				pose.scale(scale, scale);
				ui.rounded(-3, -26, 6, 40, 3f, 0xFFB8C4D8);
				ui.rounded(-10, 12, 20, 5, 2f, 0xFF8A5A2A);
				ui.rounded(-3, 16, 6, 12, 2f, 0xFF6B4423);
				pose.popMatrix();
			}
			case "walk_effects" -> {
				scene(ui, x, y, w, h, true);
				for (int i = 0; i < 4; i++) {
					float p = (t * 0.6f + i * 0.25f) % 1f;
					int radius = Math.round(4 + 22 * p);
					ui.ring(cx - 20 + i * 14 - radius, y + h - 22 - radius / 3, radius * 2, Math.max(2, radius * 2 / 3), Math.max(1f, radius / 3f), 1, Ui.withOpacity(0xFF7FFFD4, 1f - p));
					glowDot(ui, cx - 20 + i * 14, y + h - 22 - p * 24, 1.5f, 0xFF7FFFD4, 1f - p);
				}
			}
			case "arrow_trails", "swing_trail" -> {
				scene(ui, x, y, w, h, true);
				float p = (t * 0.5f) % 1f;
				if (id.equals("arrow_trails")) {
					trail(ui, x + w * (0.15f + 0.8f * p), y + h * (0.75f - 0.5f * p), 0.9f, -0.5f, 70f, 2.4f, 0xFFFF8A2B);
				} else {
					for (int i = 0; i < 12; i++) {
						float a = -1.1f + (p * 2.2f) - i * 0.1f;
						int px = Math.round(cx + (float) Math.sin(a) * 40f);
						int py = Math.round(y + h - 12 - (float) Math.cos(a) * 36f);
						ui.circle(px, py, Math.max(1, 5 - i / 3), Ui.withOpacity(accent, 1f - i / 12f));
					}
				}
			}
			case "chat" -> {
				scene(ui, x, y, w, h, false);
				pane(ui, cx - 70, y + 12, 140, 20, 10f);
				ui.rounded(cx - 68, y + 14, 30, 16, 8f, Ui.withOpacity(accent, 0.9f));
				ui.text("All", cx - 60, y + 19, 0xFFFFFFFF);
				ui.text("Party", cx - 30, y + 19, 0xFFA8A8B0);
				ui.text("Guild", cx + 12, y + 19, 0xFFA8A8B0);
				for (int i = 0; i < 3; i++) {
					textBar(ui, cx - 70, y + 44 + i * 12, 60 + (i * 31) % 70, 170);
				}
			}
			case "menus" -> {
				scene(ui, x, y, w, h, false);
				for (int i = 0; i < 3; i++) {
					float p = Mth.clamp(((t * 0.8f) % 3f) - i * 0.25f, 0f, 1f);
					ui.rounded(cx - 50, y + 14 + i * 24, 100, 18, 7f, Ui.withOpacity(i == 0 ? accent : 0xFF2A2C3A, 0.35f + 0.65f * p));
				}
			}
			case "boss_bars" -> {
				scene(ui, x, y, w, h, false);
				ui.text("Ender Dragon", cx - 28, cy - 16, 0xFFF2F2F7);
				bar(ui, cx - 80, cy - 2, 160, 7, 0.55f + 0.1f * (float) Math.sin(t), SystemColors.PURPLE);
			}
			case "action_bar_hud" -> {
				scene(ui, x, y, w, h, false);
				pane(ui, cx - 60, cy - 10, 120, 20, 10f);
				ui.text("+12 Mining Fortune", cx - 50, cy - 4, SystemColors.YELLOW);
			}
			case "cinematic_camera" -> {
				scene(ui, x, y, w, h, false);
				float shift = (float) Math.sin(t * 0.5f) * 10f;
				ui.circle(Math.round(cx + shift), cy, 14, 0xFFFFD08A);
				ui.fill(x, y, w, 14, 0xFF000000);
				ui.fill(x, y + h - 14, w, 14, 0xFF000000);
			}
			default -> {
				scene(ui, x, y, w, h, false);
				ui.icon("sparkle", cx - 12, cy - 12, 24, accent);
			}
		}
		graphics.disableScissor();
	}
}
