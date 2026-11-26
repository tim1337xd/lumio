package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/**
 * The live preview of the waypoint look: a point close by and one far away, drawn with the same parts the world uses (the labels) and flat
 * stand-ins for the box and the beam, so every setting of the group shows at once. It has no value of its own.
 */
final class WaypointLookPreviewSetting extends Setting<Void> {
	private final WaypointLookSettings look;

	WaypointLookPreviewSetting(String key, WaypointLookSettings look) {
		super(key, Component.literal("Preview"), null);
		this.look = look;
	}

	@Override
	public void reset() {
	}

	@Override
	public boolean isDefault() {
		return true;
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new Preview(look);
	}

	/** The preview's panel. */
	private static final class Preview extends Control {
		private static final int HEIGHT = 112;
		private static final int GROUND = 22;
		private static final int NEAR_BLOCK = 30;
		private static final double NEAR_BLOCKS = 6;
		private static final double FAR_BLOCKS = 96;
		private static final int FAR_BLOCK = 12;
		private static final float USUAL_FILL = 0.28f;
		private static final float OUTLINE_OPACITY = 0.9f;
		/** A beam of the usual height fills this share of the room above its block. */
		private static final float BEAM_ROOM = 0.9f;
		private static final float BEAM_OPACITY = 0.5f;
		private static final int LABEL_GAP = 4;
		private static final int SKY_TOP = 0xFF1B2333;
		private static final int SKY_BOTTOM = 0xFF2C3A52;
		private static final int GROUND_COLOR = 0xFF26301F;
		private static final int NEAR_RGB = SystemColors.ORANGE_RGB;
		private static final int FAR_RGB = SystemColors.BLUE_RGB;

		private final WaypointLookSettings look;

		Preview(WaypointLookSettings look) {
			this.look = look;
		}

		@Override
		public boolean isBlock() {
			return true;
		}

		@Override
		public boolean hasLabel() {
			return false;
		}

		@Override
		protected int measure(Ui ui, int width) {
			return HEIGHT;
		}

		@Override
		public void render(Ui ui, int mouseX, int mouseY) {
			look.refresh();
			WaypointStyle style = look.style();
			ui.roundedGradient(x, y, width, height, Radius.MD, SKY_TOP, SKY_BOTTOM);
			ui.rounded(x, y + height - GROUND, width, GROUND, Radius.MD, GROUND_COLOR);
			ui.fill(x, y + height - GROUND, width, GROUND / 2, GROUND_COLOR);
			int floor = y + height - GROUND / 2;
			point(ui, style, x + width / 3, floor, NEAR_BLOCK, NEAR_BLOCKS, "Chest", "chest", NEAR_RGB, 1);
			if (style.inReach(FAR_BLOCKS)) {
				point(ui, style, x + width * 3 / 4, floor - GROUND / 4, FAR_BLOCK, FAR_BLOCKS, "Mine", "pickaxe", FAR_RGB, 2);
			} else {
				ui.textAtScaled("Beyond the maximum distance", x + width * 3 / 4f - ui.widthScaled("Beyond the maximum distance", TextScale.SMALL) / 2f,
					floor - GROUND, TextScale.SMALL, Draw.withOpacity(0xFFFFFFFF, 0.45f));
			}
		}

		/** One point: its block standing on the floor, its beam, and its label above the block. */
		private void point(Ui ui, WaypointStyle style, int centerX, int floor, int block, double distance, String name, String icon, int rgb, int number) {
			int color = 0xFF000000 | rgb;
			int left = centerX - block / 2;
			int top = floor - block;
			if (style.beamOn(true)) {
				int room = top - y;
				int beamHeight = Math.round(Math.min(room, room * BEAM_ROOM * style.beamHeight() / WaypointLook.BEAM_HEIGHT));
				int half = Math.max(1, block / 6);
				ui.roundedGradient(centerX - half, top - beamHeight, 2 * half, beamHeight + block / 2, 0f, Draw.withOpacity(color, 0f),
					Draw.withOpacity(color, Math.clamp(BEAM_OPACITY * style.beamStrength(), 0f, 1f)));
			}
			if (style.boxed()) {
				float fill = style.fillOpacity(USUAL_FILL);
				if (fill > 0f) {
					ui.fill(left, top, block, block, Draw.withOpacity(color, fill));
				}
				int thickness = Math.max(1, Math.round(style.lineWidth() / 2f));
				ui.ring(left, top, block, block, 0f, thickness, Draw.withOpacity(Draw.mix(color, 0xFFFFFFFF, 0.35f), OUTLINE_OPACITY));
			}
			String shownName = style.showName() ? name : "";
			String shownDistance = style.showDistance() ? WaypointLook.distance(distance) : "";
			if (shownName.isEmpty() && shownDistance.isEmpty() && !style.iconLabel()) {
				return;
			}
			float scale = style.labelScale(distance);
			float labelWidth;
			int labelHeight;
			if (style.iconLabel()) {
				labelWidth = WaypointLook.iconLabelWidth(ui, shownName, shownDistance, style.iconSize());
				labelHeight = WaypointLook.iconLabelHeight(style.iconSize());
			} else if (style.textBackground()) {
				labelWidth = WaypointLook.labelWidth(ui, shownName, shownDistance);
				labelHeight = WaypointLook.labelHeight();
			} else {
				labelWidth = WaypointLook.plainLabelWidth(ui, shownName, shownDistance);
				labelHeight = WaypointLook.plainLabelHeight();
			}
			// an icon label marks the point by itself, so it stands where the block would
			float labelBottom = style.boxed() ? top - LABEL_GAP : floor - block / 2f;
			var pose = ui.graphics().pose();
			pose.pushMatrix();
			pose.translate(centerX - labelWidth * scale / 2f, Math.max(y + 2, labelBottom - labelHeight * scale));
			pose.scale(scale, scale);
			if (style.iconLabel()) {
				WaypointLook.iconLabel(ui, icon, shownName, shownDistance, rgb, WaypointLook.State.SHOWN, style.textShadow(), style.iconSize());
			} else if (style.textBackground()) {
				WaypointLook.label(ui, number, shownName, shownDistance, rgb, WaypointLook.State.SHOWN);
			} else {
				WaypointLook.plainLabel(ui, shownName, shownDistance, WaypointLook.State.SHOWN, style.textShadow());
			}
			pose.popMatrix();
		}
	}
}
