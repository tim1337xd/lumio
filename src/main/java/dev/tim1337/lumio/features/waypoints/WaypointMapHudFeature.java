package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointColors;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The corner map of the waypoints: a group whose Map is Corner, around the player and turning with them, with "GROUP · next/total" on top. Only
 * the points, their path and the player; no terrain. It is a HUD panel, so it can be moved and sized in the HUD editor.
 */
public class WaypointMapHudFeature extends HudPanelFeature {
	private static final int WIDTH = 104;
	private static final int TITLE = 16;
	private static final int MAP = 92;
	private static final int PAD = 6;
	private static final float RADIUS = 12f;

	private final WaypointsFeature waypoints;
	/** The route the editor shows while no group has the corner map: a few points around the player. */
	private static final WaypointGroup SAMPLE = sample();
	private static final String SAMPLE_TITLE = "SAMPLE ROUTE · 2/" + SAMPLE.points().size();
	private final WaypointMapView view = new WaypointMapView();
	private final FloatSetting range = add(new FloatSetting("range", Component.literal("Blocks across"), 40f, 16f, 256f, 0).step(8f), "General");
	/** The group shown and its title, worked out once a tick. */
	private WaypointGroup group;
	private String title = "";
	private int titleNext = -1;
	private int titleTotal = -1;
	private String titleName = "";

	public WaypointMapHudFeature(WaypointsFeature waypoints) {
		super("waypoint_map", "Waypoint map", "Your waypoints from above, turning with you.", false, Anchor.RIGHT, true, 12f, 60f);
		this.waypoints = waypoints;
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("map");
		summary("Your waypoints from above");
	}

	/** Chooses the group shown: the first that is on, here and set to the corner map. Called every tick. */
	public void tick() {
		group = waypoints.groupWithMap(WaypointGroup.MapMode.CORNER);
	}

	private static WaypointGroup sample() {
		WaypointGroup group = new WaypointGroup("sample", "Sample route", WaypointColors.GREEN);
		int[][] places = {{-9, 8}, {-3, -6}, {7, -10}, {12, 4}, {4, 13}};
		for (int[] place : places) {
			group.add(new Waypoint(place[0], 64, place[1], ""));
		}
		group.show(WaypointGroup.Show.NEXT);
		return group;
	}

	@Override
	protected int panelWidth() {
		return WIDTH;
	}

	@Override
	protected int panelHeight() {
		return TITLE + MAP + PAD;
	}

	@Override
	protected boolean shown(Minecraft client) {
		return group != null && client.player != null;
	}

	@Override
	protected boolean inEditorWhenHidden() {
		return true;
	}

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, panelWidth(), panelHeight(), RADIUS);
		WaypointGroup shown = group;
		LocalPlayer player = client.player;
		if (shown == null || player == null) {
			// in the editor without a group: a sample route around the player, so the panel can be placed and sized as it will look
			ui.sectionLabel(SAMPLE_TITLE, PAD + 2, 5, ui.palette.textSecondary());
			view.drawAround(ui, PAD, TITLE, WIDTH - 2 * PAD, MAP - PAD, SAMPLE, 0.5, 0.5, 180f, range.get(), 1);
			return;
		}
		int next = shown.show() == WaypointGroup.Show.NEXT ? shown.next() : -1;
		ui.sectionLabel(title(shown, next), PAD + 2, 5, ui.palette.textSecondary());
		view.drawAround(ui, PAD, TITLE, WIDTH - 2 * PAD, MAP - PAD, shown, player.getX(), player.getZ(), player.getYRot(), range.get(), next);
	}

	/** @return "GLACITE ROUTE · 2/24", made again only when it changes */
	private String title(WaypointGroup shown, int next) {
		int total = shown.points().size();
		if (next != titleNext || total != titleTotal || !titleName.equals(shown.name())) {
			titleName = shown.name();
			titleNext = next;
			titleTotal = total;
			String name = shown.name().toUpperCase(Locale.ROOT);
			title = next >= 0 ? name + "  ·  " + (next + 1) + "/" + total : name + "  ·  " + total;
		}
		return title;
	}
}
