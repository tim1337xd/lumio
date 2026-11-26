package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Look group of the Waypoints settings: one look for all waypoints, the player's own and those of add-ons (such as dungeon waypoints). Its
 * values at their defaults are the look waypoints always had ({@link WaypointStyle#DEFAULT}). The style is made anew once a tick, so drawing
 * reads one finished value.
 */
public final class WaypointLookSettings {
	/** The group's title on the page. */
	static final String GROUP = "Look";
	private static final float PERCENT = 100f;

	private final WaypointLookPreviewSetting preview = new WaypointLookPreviewSetting("look_preview", this);
	private final FloatSetting textSize = new FloatSetting("look_text_size", Component.literal("Text size"), PERCENT, 50f, 200f, 0).unit("%");
	private final EnumSetting<WaypointStyle.TextScaling> textScaling = new EnumSetting<>("look_text_distance", Component.literal("Scale with distance"),
		WaypointStyle.TextScaling.SMALLER_FAR, value -> Component.literal(value.label()), EnumSetting.Style.DROPDOWN);
	private final BooleanSetting showName = new BooleanSetting("look_show_name", Component.literal("Name"), true);
	private final BooleanSetting showDistance = new BooleanSetting("look_show_distance", Component.literal("Distance"), true);
	private final BooleanSetting textShadow = new BooleanSetting("look_text_shadow", Component.literal("Text shadow"), true);
	private final BooleanSetting textBackground = new BooleanSetting("look_text_background", Component.literal("Text background"), false);
	private final EnumSetting<WaypointStyle.Marker> marker = new EnumSetting<>("look_marker", Component.literal("Marker style"), WaypointStyle.Marker.BOX,
		value -> Component.literal(value.label()), EnumSetting.Style.SEGMENTED);
	private final FloatSetting lineWidth = new FloatSetting("look_line_width", Component.literal("Line width"), WaypointStyle.DEFAULT_LINE_WIDTH, 1f, 6f, 1).unit(" px");
	private final FloatSetting fill = new FloatSetting("look_fill", Component.literal("Fill strength"), PERCENT, 0f, 200f, 0).unit("%");
	private final BooleanSetting beam = new BooleanSetting("look_beam", Component.literal("Beam"), true);
	private final FloatSetting beamHeight = new FloatSetting("look_beam_height", Component.literal("Beam height"), WaypointLook.BEAM_HEIGHT, 8f, 128f, 0).unit(" blocks");
	private final FloatSetting beamStrength = new FloatSetting("look_beam_strength", Component.literal("Beam strength"), PERCENT, 20f, 200f, 0).unit("%");
	private final BooleanSetting fadeNear = new BooleanSetting("look_fade_near", Component.literal("Fade when close"), true);
	private final FloatSetting maxDistance = new FloatSetting("look_max_distance", Component.literal("Max distance"), 0f, 0f, 512f, 0).unit(" blocks").offAtZero("Any");
	private final FloatSetting iconSize = new FloatSetting("look_icon_size", Component.literal("Icon size"), PERCENT, 50f, 200f, 0).unit("%");

	private WaypointStyle style = WaypointStyle.DEFAULT;

	WaypointLookSettings() {
		lineWidth.visibleWhen(() -> marker.get() == WaypointStyle.Marker.BOX || marker.get() == WaypointStyle.Marker.OUTLINE);
		fill.visibleWhen(() -> marker.get() == WaypointStyle.Marker.BOX);
		beam.visibleWhen(() -> marker.get() != WaypointStyle.Marker.ICON);
		beamHeight.visibleWhen(() -> beam.get() && marker.get() != WaypointStyle.Marker.ICON);
		beamStrength.visibleWhen(() -> beam.get() && marker.get() != WaypointStyle.Marker.ICON);
		iconSize.visibleWhen(() -> marker.get() == WaypointStyle.Marker.ICON);
		textShadow.visibleWhen(() -> !textBackground.get());
		showDistance.setDescription("Where a group shows the distance");
		textBackground.setDescription("A dark box behind labels");
		beam.setDescription("Off: no waypoint has a beam");
	}

	/** @return the group's settings, in the order they show */
	List<Setting<?>> settings() {
		return List.of(preview, textSize, textScaling, showName, showDistance, textShadow, textBackground, marker, lineWidth, fill, beam, beamHeight,
			beamStrength, fadeNear, maxDistance, iconSize);
	}

	/** Makes the style anew from the settings; called once a tick and while the preview shows. */
	void refresh() {
		style = new WaypointStyle(textSize.get() / PERCENT, textScaling.get(), showName.get(), showDistance.get(), textShadow.get(), textBackground.get(),
			marker.get(), lineWidth.get(), fill.get() / PERCENT, beam.get(), beamHeight.get(), beamStrength.get() / PERCENT, fadeNear.get(),
			Math.round(maxDistance.get()), iconSize.get() / PERCENT);
	}

	/** @return the look all waypoints draw by, as of the last tick */
	public WaypointStyle style() {
		return style;
	}
}
