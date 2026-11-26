package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.ui.Ui;

import java.util.List;

/**
 * A top-down map of a group's points: only the points, the path between them and the player, no terrain. North is up on the page; the HUD map
 * turns with the player instead. The places of the drawn points are kept, so a click can find the point under the mouse.
 */
final class WaypointMapView {
	/** Room around the points, in map pixels. */
	private static final int PAD = 14;
	/** The least span shown, in blocks, so one point or two close ones are not blown up. */
	private static final float LEAST_SPAN_BLOCKS = 24f;
	private static final int GRID_STEP = 16;
	private static final int DOT = 11;
	private static final int NEXT_DOT = 14;
	private static final float PATH_DOT = 1.6f;
	private static final float PATH_STEP = 4.5f;
	private static final int MOST_PATH_DOTS = 200;
	/** Each bar of the player's chevron is turned this far from straight, in radians. */
	private static final float CHEVRON_ANGLE = 0.6f;
	private static final float MIN_ZOOM = 0.25f;
	private static final float MAX_ZOOM = 8f;
	private static final float ZOOM_STEP = 1.5f;
	/** The scale bar's wanted length in map pixels, and the round lengths it may show, in blocks. */
	private static final int SCALE_BAR = 26;
	private static final int[] ROUND_METERS = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000};

	private float zoom = 1f;
	private float[] placedX = new float[0];
	private float[] placedY = new float[0];
	private int placedCount;
	/** The route's line on the page: discs this large, this far apart. */
	private static final float LINE_DOT = 1.4f;
	private static final float LINE_STEP = 0.9f;

	void zoomIn() {
		zoom = Math.min(MAX_ZOOM, zoom * ZOOM_STEP);
	}

	void zoomOut() {
		zoom = Math.max(MIN_ZOOM, zoom / ZOOM_STEP);
	}

	/** Shows the whole group again, after another group was chosen. */
	void resetZoom() {
		zoom = 1f;
	}

	/**
	 * Draws the map of a group, north up, fitted to its points.
	 *
	 * @param player the player's x and z, or null when the player is not where the group is
	 * @param next   the point drawn as the next one, or -1
	 */
	/**
	 * Draws the route on the page: its points as dots on a line in their order, the next one larger and white; no numbers on the map, the
	 * number of the dot under the mouse beside it.
	 */
	void draw(Ui ui, int x, int y, int width, int height, WaypointGroup group, double[] player, float playerYaw, int next, int highlighted, int mouseX,
			  int mouseY) {
		draw(ui, x, y, width, height, group, player, playerYaw, next, highlighted, mouseX, mouseY, true);
	}

	/** Draws the big map in game: numbered dots on a dotted path, the next one larger with a light around it. */
	void draw(Ui ui, int x, int y, int width, int height, WaypointGroup group, double[] player, float playerYaw, int next, int highlighted) {
		draw(ui, x, y, width, height, group, player, playerYaw, next, highlighted, -1, -1, false);
	}

	private void draw(Ui ui, int x, int y, int width, int height, WaypointGroup group, double[] player, float playerYaw, int next, int highlighted, int mouseX,
					  int mouseY, boolean page) {
		List<Waypoint> points = group.points();
		ensure(points.size());
		int lineColor = Ui.withOpacity(ui.palette.cardBorder(), 0.55f);
		for (int gx = x + GRID_STEP; gx < x + width; gx += GRID_STEP) {
			ui.fill(gx, y + 1, 1, height - 2, lineColor);
		}
		for (int gy = y + GRID_STEP; gy < y + height; gy += GRID_STEP) {
			ui.fill(x + 1, gy, width - 2, 1, lineColor);
		}
		// the bounds of the points (and the player, when there), around which the map is centered
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (Waypoint point : points) {
			minX = Math.min(minX, point.x() + 0.5);
			maxX = Math.max(maxX, point.x() + 0.5);
			minZ = Math.min(minZ, point.z() + 0.5);
			maxZ = Math.max(maxZ, point.z() + 0.5);
		}
		if (player != null) {
			minX = Math.min(minX, player[0]);
			maxX = Math.max(maxX, player[0]);
			minZ = Math.min(minZ, player[1]);
			maxZ = Math.max(maxZ, player[1]);
		}
		placedCount = 0;
		if (minX > maxX) {
			return;
		}
		double centerX = (minX + maxX) / 2;
		double centerZ = (minZ + maxZ) / 2;
		double span = Math.max(LEAST_SPAN_BLOCKS, Math.max(maxX - minX, maxZ - minZ));
		float scale = (float) (Math.min(width, height) - 2 * PAD) / (float) span * zoom;
		float middleX = x + width / 2f;
		float middleY = y + height / 2f;
		ui.graphics.enableScissor(x, y, x + width, y + height);
		for (int i = 0; i < points.size(); i++) {
			placedX[i] = middleX + (float) (points.get(i).x() + 0.5 - centerX) * scale;
			placedY[i] = middleY + (float) (points.get(i).z() + 0.5 - centerZ) * scale;
		}
		placedCount = points.size();
		boolean path = group.has(WaypointGroup.Look.LINE) || group.show() == WaypointGroup.Show.NEXT;
		for (int i = 0; path && i + 1 < points.size(); i++) {
			if (page) {
				line(ui, placedX[i], placedY[i], placedX[i + 1], placedY[i + 1], Ui.withOpacity(0xFF000000 | group.rgbOf(points.get(i + 1)), 0.6f));
			} else {
				dotted(ui, placedX[i], placedY[i], placedX[i + 1], placedY[i + 1], Ui.withOpacity(0xFF000000 | group.rgbOf(points.get(i + 1)), 0.7f));
			}
		}
		if (player != null && next >= 0 && next < points.size()) {
			float playerX = middleX + (float) (player[0] - centerX) * scale;
			float playerY = middleY + (float) (player[1] - centerZ) * scale;
			dotted(ui, playerX, playerY, placedX[next], placedY[next], 0xCCFFFFFF);
		}
		int hovered = -1;
		for (int i = 0; i < points.size() && !page; i++) {
			Waypoint point = points.get(i);
			int color = 0xFF000000 | group.rgbOf(point);
			boolean big = i == next || i == highlighted;
			float size = big ? NEXT_DOT : DOT;
			if (!group.drawn(i)) {
				color = Ui.withOpacity(color, 0.35f);
			}
			if (big) {
				ui.softLight(placedX[i], placedY[i], size * 1.4f, Ui.withOpacity(color, 0.6f));
				ui.ringAt(placedX[i], placedY[i], size + 3, 1.2f, Ui.withOpacity(0xFFFFFFFF, 0.8f));
			}
			ui.discAt(placedX[i], placedY[i], size, color);
			String number = String.valueOf(i + 1);
			float textWidth = ui.boldWidthScaled(number, TextScale.SMALL * 0.85f);
			ui.textAtScaledBold(number, placedX[i] - textWidth / 2f, placedY[i] - 3f, TextScale.SMALL * 0.85f, Ui.luminance(color) > 0.6f ? 0xFF1C1C1E : 0xFFFFFFFF);
		}
		for (int i = 0; i < points.size() && page; i++) {
			Waypoint point = points.get(i);
			int color = 0xFF000000 | group.rgbOf(point);
			boolean big = i == next;
			float size = big ? NEXT_DOT * 0.75f : i == highlighted ? DOT * 0.75f + 2f : DOT * 0.55f;
			if (!group.drawn(i) || next >= 0 && i < next) {
				color = Ui.withOpacity(color, 0.4f);
			}
			ui.discAt(placedX[i], placedY[i], size, big ? 0xFFFFFFFF : color);
			double dx = mouseX - placedX[i];
			double dy = mouseY - placedY[i];
			if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height && dx * dx + dy * dy <= NEXT_DOT * NEXT_DOT / 4.0) {
				hovered = i;
			}
		}
		if (hovered >= 0) {
			// the number of the dot under the mouse, beside it
			String number = String.valueOf(hovered + 1);
			float w = ui.boldWidthScaled(number, TextScale.SMALL) + 6;
			float lx = Math.min(placedX[hovered] + 5, x + width - w - 2);
			float ly = placedY[hovered] - 13;
			ui.rounded(Math.round(lx), Math.round(ly), Math.round(w), 10, 3, ui.palette.popup());
			ui.textAtScaledBold(number, lx + 3, ly + 1.5f, TextScale.SMALL, ui.palette.text());
		}
		if (player != null) {
			drawPlayer(ui, middleX + (float) (player[0] - centerX) * scale, middleY + (float) (player[1] - centerZ) * scale, playerYaw);
		}
		ui.graphics.disableScissor();
		drawScale(ui, x + 7, y + height - 9, scale);
	}

	/** @return the point under the mouse, by the places of the last frame, or -1 */
	int pointAt(double mouseX, double mouseY) {
		for (int i = placedCount - 1; i >= 0; i--) {
			double dx = mouseX - placedX[i];
			double dy = mouseY - placedY[i];
			if (dx * dx + dy * dy <= NEXT_DOT * NEXT_DOT / 4.0) {
				return i;
			}
		}
		return -1;
	}

	private void ensure(int count) {
		if (placedX.length < count) {
			placedX = new float[count];
			placedY = new float[count];
		}
	}

	/** A thin solid line between two places, as small discs close together. */
	private static void line(Ui ui, float fromX, float fromY, float toX, float toY, int color) {
		float dx = toX - fromX;
		float dy = toY - fromY;
		int steps = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy) / LINE_STEP));
		for (int s = 0; s <= steps; s++) {
			float t = s / (float) steps;
			ui.discAt(fromX + dx * t, fromY + dy * t, LINE_DOT, color);
		}
	}

	static void dotted(Ui ui, float fromX, float fromY, float toX, float toY, int color) {
		float dx = toX - fromX;
		float dy = toY - fromY;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		int steps = (int) (length / PATH_STEP);
		for (int s = 1; s < steps; s++) {
			float t = s / (float) steps;
			ui.discAt(fromX + dx * t, fromY + dy * t, PATH_DOT, color);
		}
	}

	/** The player as a chevron in the direction they look: yaw 0 looks south, which is down on a map with north up. */
	private static void drawPlayer(Ui ui, float x, float y, float yaw) {
		drawChevron(ui, x, y, (float) Math.toRadians(yaw + 180f));
	}

	/** A chevron pointing up, turned by an angle in radians, on a dark disc. */
	static void drawChevron(Ui ui, float x, float y, float angle) {
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		ui.discAt(0, 0, 12, 0x66000000);
		pose.rotate(angle);
		for (int side = -1; side <= 1; side += 2) {
			pose.pushMatrix();
			pose.translate(0f, -3.5f);
			pose.rotate(side * CHEVRON_ANGLE);
			ui.roundedSmooth(-1.1f, 0f, 2.2f, 7f, 1.1f, 0xFFFFFFFF);
			pose.popMatrix();
		}
		pose.popMatrix();
	}

	/**
	 * Draws the map around the player, as the HUD shows it: the player in the middle pointing up, the map turned with them, a fixed span of blocks
	 * across. Points outside are left out, but the next one of a route stays at the edge in its direction. Drawn without a clip, so it can be
	 * scaled with the panel.
	 */
	void drawAround(Ui ui, int x, int y, int width, int height, WaypointGroup group, double playerX, double playerZ, float yaw, float blocksAcross,
		int next) {
		List<Waypoint> points = group.points();
		ensure(points.size());
		int lineColor = 0x14FFFFFF;
		for (int gx = GRID_STEP; gx < width; gx += GRID_STEP) {
			ui.fill(x + gx, y + 1, 1, height - 2, lineColor);
		}
		for (int gy = GRID_STEP; gy < height; gy += GRID_STEP) {
			ui.fill(x + 1, y + gy, width - 2, 1, lineColor);
		}
		float scale = width / blocksAcross;
		float middleX = x + width / 2f;
		float middleY = y + height / 2f;
		double turn = Math.toRadians(yaw);
		float cos = (float) Math.cos(turn);
		float sin = (float) Math.sin(turn);
		for (int i = 0; i < points.size(); i++) {
			float dx = (float) (points.get(i).x() + 0.5 - playerX);
			float dz = (float) (points.get(i).z() + 0.5 - playerZ);
			// ahead of the player is up: right is (-cos, -sin), ahead is (-sin, cos)
			placedX[i] = middleX + (-dx * cos - dz * sin) * scale;
			placedY[i] = middleY + (dx * sin - dz * cos) * scale;
		}
		placedCount = points.size();
		float left = x + DOT / 2f;
		float right = x + width - DOT / 2f;
		float top = y + DOT / 2f;
		float bottom = y + height - DOT / 2f;
		boolean path = group.has(WaypointGroup.Look.LINE) || group.show() == WaypointGroup.Show.NEXT;
		for (int i = 0; path && i + 1 < points.size(); i++) {
			dottedInside(ui, placedX[i], placedY[i], placedX[i + 1], placedY[i + 1], left, top, right, bottom,
				Ui.withOpacity(0xFF000000 | group.rgbOf(points.get(i + 1)), 0.7f));
		}
		if (next >= 0 && next < points.size()) {
			dottedInside(ui, middleX, middleY, placedX[next], placedY[next], left, top, right, bottom, 0xCCFFFFFF);
		}
		for (int i = 0; i < points.size(); i++) {
			Waypoint point = points.get(i);
			if (!group.drawn(i)) {
				continue;
			}
			float px = placedX[i];
			float py = placedY[i];
			boolean inside = px >= left && px <= right && py >= top && py <= bottom;
			int color = 0xFF000000 | group.rgbOf(point);
			if (!inside) {
				if (i == next) {
					// the next point stays at the edge, in its direction
					float share = edgeShare(px - middleX, py - middleY, right - middleX, bottom - middleY);
					ui.discAt(middleX + (px - middleX) * share, middleY + (py - middleY) * share, DOT - 3, color);
				}
				continue;
			}
			boolean big = i == next;
			float size = big ? NEXT_DOT : DOT;
			if (big) {
				ui.ringAt(px, py, size + 3, 1.2f, 0xCCFFFFFF);
			}
			ui.discAt(px, py, size, color);
			String number = String.valueOf(i + 1);
			float textWidth = ui.boldWidthScaled(number, TextScale.SMALL * 0.85f);
			ui.textAtScaledBold(number, px - textWidth / 2f, py - 3f, TextScale.SMALL * 0.85f, Ui.luminance(color) > 0.6f ? 0xFF1C1C1E : 0xFFFFFFFF);
		}
		drawChevron(ui, middleX, middleY, 0f);
	}

	/** @return how far along a way from the middle the edge of a box around it is, from 0 to 1 */
	private static float edgeShare(float dx, float dy, float halfWidth, float halfHeight) {
		float share = 1f;
		if (Math.abs(dx) > halfWidth) {
			share = Math.min(share, halfWidth / Math.abs(dx));
		}
		if (Math.abs(dy) > halfHeight) {
			share = Math.min(share, halfHeight / Math.abs(dy));
		}
		return share;
	}

	/** A dotted line whose dots outside a box are left out. */
	private static void dottedInside(Ui ui, float fromX, float fromY, float toX, float toY, float left, float top, float right, float bottom, int color) {
		float dx = toX - fromX;
		float dy = toY - fromY;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		int steps = Math.min(MOST_PATH_DOTS, (int) (length / PATH_STEP));
		for (int s = 1; s < steps; s++) {
			float t = s / (float) steps;
			float px = fromX + dx * t;
			float py = fromY + dy * t;
			if (px >= left && px <= right && py >= top && py <= bottom) {
				ui.discAt(px, py, PATH_DOT, color);
			}
		}
	}

	/** A bar of a round number of blocks, about {@link #SCALE_BAR} pixels long. */
	private static void drawScale(Ui ui, int x, int y, float pixelsPerBlock) {
		if (pixelsPerBlock <= 0f) {
			return;
		}
		int meters = ROUND_METERS[ROUND_METERS.length - 1];
		for (int round : ROUND_METERS) {
			if (round * pixelsPerBlock >= SCALE_BAR * 0.6f) {
				meters = round;
				break;
			}
		}
		int length = Math.round(meters * pixelsPerBlock);
		int color = Ui.withOpacity(ui.palette.textSecondary(), 0.9f);
		ui.fill(x, y, length, 1, color);
		ui.fill(x, y - 2, 1, 3, color);
		ui.fill(x + length - 1, y - 2, 1, 3, color);
		ui.textAtScaled(meters + " m", x + length + 4, y - 3.5f, TextScale.SMALL, color);
	}

	/** The compass mark in a corner: an "N" in a small dark circle. */
	static void drawNorth(Ui ui, int centerX, int centerY) {
		ui.discAt(centerX, centerY, 12, 0x99000000);
		ui.textCenteredBold("N", centerX, centerY, 0xFFFFFFFF);
	}

	/** A round zoom button with a plus or a minus. */
	static void drawZoomButton(Ui ui, int x, int y, int size, boolean plus, float hover) {
		ui.rounded(x, y, size, size, Radius.XS, Ui.mix(0xCC1C1C1E, 0xCC3A3A3C, hover));
		ui.icon(plus ? "plus" : "minus", x + 2, y + 2, size - 4, 0xFFFFFFFF);
	}
}
