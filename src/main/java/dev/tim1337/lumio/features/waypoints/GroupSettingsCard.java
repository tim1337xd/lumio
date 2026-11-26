package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointColors;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorDotsSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings tab of the group shown on the Waypoints page, in three groups: Where (its areas), Route (which points show and how many, when
 * the next one comes, the progress with Restart at 1, the restart in a new lobby, the map) and Look (the marker, the label and its
 * background and size, the line to the next point, through walls, the color). A row that does not apply now (how many ahead without Next, a
 * label's background without a label) is left out. Every row has its control at the right; its name is never squeezed. The rows use the menu's
 * own controls on settings of their own, which take the group's values when another group is shown and give them back when they change.
 */
final class GroupSettingsCard {
	private static final int ROW = 24;
	private static final int ROW_WITH_HINT = 30;
	private static final int PAD = 10;
	private static final int INDENT = 14;
	private static final int SECTION_LABEL = 16;
	private static final int SECTION_GAP = 8;
	private static final int CHIP_HEIGHT = 15;
	private static final int CHIP_PAD = 6;
	private static final int CHIP_GAP = 4;
	private static final int BUTTON_HEIGHT = 16;
	private static final String ADD_AREA = "+ Add";
	private static final String RESTART = "Restart at 1";
	private static final float[] LABEL_SIZES = {WaypointGroup.LEAST_LABEL_SIZE, 0.8f, WaypointGroup.DEFAULT_LABEL_SIZE, 1.3f, WaypointGroup.MOST_LABEL_SIZE};
	private static final String[] LABEL_SIZE_NAMES = {"Smallest", "Small", "Normal", "Large", "Largest"};

	/** The colors of the dots, in the order of {@link WaypointColors}. */
	enum Dot {
		BLUE, PURPLE, GREEN, YELLOW, ORANGE, RED;

		int rgb() {
			return WaypointColors.get(ordinal());
		}
	}

	/** How many points Next and Nearest show. */
	enum Count {
		ONE, TWO, THREE;

		String label() {
			return String.valueOf(ordinal() + 1);
		}
	}

	/** The marker on a point: its block outlined, a beam of light, or both. */
	enum Marker {
		BLOCK("Block"), BEAM("Beam"), BOTH("Both");

		private final String label;

		Marker(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}
	}

	/** What a row holds besides an ordinary control. */
	private enum Kind {
		CONTROL, AREAS, PROGRESS, REACH, LABEL_SIZE
	}

	/** A row as laid out: its name, its hint under the name or null, its control, how far it is indented, and its place. */
	private static final class Row {
		final String name;
		final String hint;
		final Control control;
		final Kind kind;
		final int indent;
		int top;
		int height;

		Row(String name, String hint, Control control, Kind kind, int indent) {
			this.name = name;
			this.hint = hint;
			this.control = control;
			this.kind = kind;
			this.indent = indent;
		}
	}

	/** A group of rows under its label, drawn as one card. */
	private record Section(String title, List<Row> rows, int[] box) {
	}

	private final WaypointsFeature feature;
	private final WaypointMenu menu;
	private final EnumSetting<WaypointGroup.Show> show = new EnumSetting<>("waypoint_show", Component.literal("Show"), WaypointGroup.Show.NEXT,
		value -> Component.literal(value.label()), EnumSetting.Style.SEGMENTED);
	private final EnumSetting<Count> count = new EnumSetting<>("waypoint_count", Component.literal("How many ahead"), Count.TWO,
		value -> Component.literal(value.label()), EnumSetting.Style.SEGMENTED);
	private final EnumSetting<WaypointGroup.Advance> advance = new EnumSetting<>("waypoint_advance", Component.literal("Go to next when"),
		WaypointGroup.Advance.REACHED, value -> Component.literal(value.label()), EnumSetting.Style.SEGMENTED);
	private final BooleanSetting lobby = new BooleanSetting("waypoint_restart_lobby", Component.literal("Restart in a new lobby"), false);
	private final EnumSetting<WaypointGroup.MapMode> map = new EnumSetting<>("waypoint_map", Component.literal("Map of the route"), WaypointGroup.MapMode.OFF,
		value -> Component.literal(value.label()), EnumSetting.Style.SEGMENTED);
	private final EnumSetting<Marker> marker = new EnumSetting<>("waypoint_marker", Component.literal("Marker"), Marker.BLOCK,
		value -> Component.literal(value.label()), EnumSetting.Style.SEGMENTED);
	private final EnumSetting<WaypointGroup.Label> label = new EnumSetting<>("waypoint_label", Component.literal("Label"), WaypointGroup.Label.NAME,
		value -> Component.literal(value.label()), EnumSetting.Style.DROPDOWN);
	private final BooleanSetting labelBackground = new BooleanSetting("waypoint_label_background", Component.literal("Label background"), false);
	private final BooleanSetting line = new BooleanSetting("waypoint_line", Component.literal("Line to next point"), false);
	private final BooleanSetting throughWalls = new BooleanSetting("waypoint_through_walls", Component.literal("Through walls"), false);
	private final ColorDotsSetting<Dot> color = new ColorDotsSetting<>("waypoint_color", Component.literal("Color"), Dot.BLUE,
		value -> Component.literal(value.name()), value -> 0xFF000000 | value.rgb(), null);
	private final Control showControl;
	private final Control countControl;
	private final Control advanceControl;
	private final Control lobbyControl;
	private final Control mapControl;
	private final Control markerControl;
	private final Control labelControl;
	private final Control backgroundControl;
	private final Control lineControl;
	private final Control wallsControl;
	private final Control colorControl;
	private final Control[] controls;
	private final List<Section> sections = new ArrayList<>();
	private WaypointGroup shown;
	private int shownChanges = -1;
	private int x;
	private int y;
	private int width;
	private int height;
	/** The area chips of the last layout, for clicks: left edges and widths, the last one is "+ Add"; and their row's top. */
	private int[] chipX = new int[0];
	private int[] chipWidth = new int[0];
	private int chipCount;
	private int chipsY;
	/** The buttons of the last layout: Restart at 1, the reach and the label size, each left, top and width. */
	private final int[] restart = new int[3];
	private final int[] reach = new int[3];
	private final int[] labelSize = new int[3];

	GroupSettingsCard(WaypointsFeature feature, WaypointMenu menu) {
		this.feature = feature;
		this.menu = menu;
		Runnable changed = this::giveBack;
		showControl = show.createControl(changed);
		countControl = count.createControl(changed);
		advanceControl = advance.createControl(changed);
		lobbyControl = lobby.createControl(changed);
		mapControl = map.createControl(changed);
		markerControl = marker.createControl(changed);
		labelControl = label.createControl(changed);
		backgroundControl = labelBackground.createControl(changed);
		lineControl = line.createControl(changed);
		wallsControl = throughWalls.createControl(changed);
		colorControl = color.createControl(changed);
		controls = new Control[] {showControl, countControl, advanceControl, lobbyControl, mapControl, markerControl, labelControl, backgroundControl, lineControl,
			wallsControl, colorControl};
	}

	/** Takes the group's values into the controls, when another group is shown or the group changed elsewhere. */
	private void take(WaypointGroup group) {
		show.set(group.show());
		count.set(Count.values()[group.nextCount() - 1]);
		advance.set(group.advance());
		lobby.set(group.restartInNewLobby());
		map.set(group.map());
		boolean block = group.has(WaypointGroup.Look.BLOCK);
		boolean beam = group.has(WaypointGroup.Look.BEAM);
		marker.set(block && beam ? Marker.BOTH : beam ? Marker.BEAM : Marker.BLOCK);
		label.set(group.label());
		labelBackground.set(group.labelBackground());
		line.set(group.has(WaypointGroup.Look.LINE));
		throughWalls.set(group.has(WaypointGroup.Look.THROUGH_WALLS));
		color.set(Dot.values()[WaypointColors.nearest(group.rgb())]);
	}

	/** Gives a change made with a control back to the group. */
	private void giveBack() {
		WaypointGroup group = shown;
		if (group == null) {
			return;
		}
		group.show(show.get());
		group.nextCount(count.get().ordinal() + 1);
		group.advance(advance.get());
		group.restartInNewLobby(lobby.get());
		group.map(map.get());
		boolean hadMarker = group.has(WaypointGroup.Look.BLOCK) || group.has(WaypointGroup.Look.BEAM);
		Marker chosen = marker.get();
		if (hadMarker || chosen != Marker.BLOCK) {
			group.set(WaypointGroup.Look.BLOCK, chosen != Marker.BEAM);
			group.set(WaypointGroup.Look.BEAM, chosen != Marker.BLOCK);
		}
		group.label(label.get());
		group.labelBackground(labelBackground.get());
		group.set(WaypointGroup.Look.LINE, line.get());
		group.set(WaypointGroup.Look.THROUGH_WALLS, throughWalls.get());
		if (WaypointColors.nearest(group.rgb()) != color.get().ordinal()) {
			group.rgb(color.get().rgb());
		}
		feature.store().changed(group);
		shownChanges = feature.store().changes();
	}

	/** Builds the rows of the three groups for what applies now. */
	private void build(WaypointGroup group) {
		sections.clear();
		boolean route = group.show() == WaypointGroup.Show.NEXT;
		boolean labelled = group.label() != WaypointGroup.Label.OFF;
		List<Row> where = new ArrayList<>();
		where.add(new Row("Areas", "Only active in these places", null, Kind.AREAS, 0));
		sections.add(new Section("WHERE", where, new int[4]));
		List<Row> routeRows = new ArrayList<>();
		routeRows.add(new Row("Show", null, showControl, Kind.CONTROL, 0));
		if (route || group.show() == WaypointGroup.Show.NEAREST) {
			routeRows.add(new Row(route ? "How many ahead" : "How many", null, countControl, Kind.CONTROL, INDENT));
		}
		if (route) {
			routeRows.add(new Row("Go to next when", null, advanceControl, Kind.CONTROL, 0));
			if (group.advance() == WaypointGroup.Advance.REACHED) {
				routeRows.add(new Row("Near means within", null, null, Kind.REACH, INDENT));
			}
			int points = group.points().size();
			routeRows.add(new Row("Progress", points == 0 ? "No points yet" : "Point " + (group.next() + 1) + " of " + points, null, Kind.PROGRESS, 0));
			routeRows.add(new Row("Restart in a new lobby", null, lobbyControl, Kind.CONTROL, 0));
		}
		routeRows.add(new Row("Map of the route", null, mapControl, Kind.CONTROL, 0));
		sections.add(new Section("ROUTE", routeRows, new int[4]));
		List<Row> look = new ArrayList<>();
		look.add(new Row("Marker", null, markerControl, Kind.CONTROL, 0));
		look.add(new Row("Label", null, labelControl, Kind.CONTROL, 0));
		if (labelled) {
			look.add(new Row("Label background", null, backgroundControl, Kind.CONTROL, INDENT));
			look.add(new Row("Label size", null, null, Kind.LABEL_SIZE, INDENT));
		}
		look.add(new Row("Line to next point", null, lineControl, Kind.CONTROL, 0));
		look.add(new Row("Through walls", null, wallsControl, Kind.CONTROL, 0));
		look.add(new Row("Color", null, colorControl, Kind.CONTROL, 0));
		sections.add(new Section("LOOK", look, new int[4]));
	}

	/** @return the tab's height for a width */
	int measure(Ui ui, WaypointGroup group, int x, int y, int width) {
		this.x = x;
		this.y = y;
		this.width = width;
		if (group != shown || feature.store().changes() != shownChanges) {
			shown = group;
			take(group);
			shownChanges = feature.store().changes();
		}
		build(group);
		int top = y;
		for (Section section : sections) {
			top += SECTION_LABEL;
			int cardTop = top;
			for (Row row : section.rows()) {
				row.top = top;
				row.height = row.hint != null ? ROW_WITH_HINT : ROW;
				place(ui, row);
				top += row.height;
			}
			section.box()[0] = x;
			section.box()[1] = cardTop;
			section.box()[2] = width;
			section.box()[3] = top - cardTop;
			top += SECTION_GAP;
		}
		height = top - y;
		return height;
	}

	/** Places a row's control at its right end, centered in the row; what is left of it is the name's, which it never squeezes. */
	private void place(Ui ui, Row row) {
		int right = x + width - PAD;
		int nameWidth = ui.width(row.name) + PAD + row.indent + 12;
		int room = Math.max(40, width - nameWidth - PAD);
		switch (row.kind) {
			case CONTROL -> {
				int wanted = Math.min(room, row.control.inlineWidth(ui, room));
				row.control.place(ui, right - wanted, row.top, wanted);
				row.control.place(ui, right - wanted, row.top + (row.height - row.control.height()) / 2, wanted);
			}
			case PROGRESS -> button(ui, restart, RESTART, right, row);
			case REACH -> button(ui, reach, shown.reach() + " blocks", right, row);
			case LABEL_SIZE -> button(ui, labelSize, labelSizeName(shown), right, row);
			case AREAS -> {
				chipsY = row.top + (row.height - CHIP_HEIGHT) / 2;
			}
			default -> {
			}
		}
	}

	private static void button(Ui ui, int[] box, String text, int right, Row row) {
		int w = Math.round(ui.widthScaled(text, TextScale.SMALL)) + 2 * CHIP_PAD + 4;
		box[0] = right - w;
		box[1] = row.top + (row.height - BUTTON_HEIGHT) / 2;
		box[2] = w;
	}

	private static String labelSizeName(WaypointGroup group) {
		int best = 0;
		for (int i = 1; i < LABEL_SIZES.length; i++) {
			if (Math.abs(group.labelSize() - LABEL_SIZES[i]) < Math.abs(group.labelSize() - LABEL_SIZES[best])) {
				best = i;
			}
		}
		return LABEL_SIZE_NAMES[best];
	}

	void render(Ui ui, int mouseX, int mouseY) {
		for (Section section : sections) {
			int[] box = section.box();
			ui.sectionLabel(section.title(), x + 4, box[1] - SECTION_LABEL + 4, ui.palette.textSecondary());
			ui.rounded(box[0], box[1], box[2], box[3], Radius.LG, ui.palette.card());
			ui.ring(box[0], box[1], box[2], box[3], Radius.LG, 1, ui.palette.cardBorder());
			for (int i = 0; i < section.rows().size(); i++) {
				Row row = section.rows().get(i);
				if (i > 0) {
					ui.fill(x + PAD, row.top, width - 2 * PAD, 1, ui.palette.cardBorder());
				}
				int nameX = x + PAD + row.indent;
				int middle = row.top + row.height / 2;
				int nameColor = row.indent > 0 ? Ui.mix(ui.palette.text(), ui.palette.textSecondary(), 0.25f) : ui.palette.text();
				if (row.hint == null) {
					ui.textMiddle(row.name, nameX, middle, nameColor);
				} else {
					ui.textAtScaled(row.name, nameX, middle - 9, TextScale.BODY, nameColor);
					ui.textAtScaled(row.hint, nameX, middle + 2, TextScale.SMALL, ui.palette.textSecondary());
				}
				switch (row.kind) {
					case CONTROL -> row.control.renderSafely(ui, mouseX, mouseY);
					case AREAS -> drawAreas(ui, nameX + Math.max(ui.width(row.name), Math.round(ui.widthScaled(row.hint, TextScale.SMALL))) + 12, mouseX, mouseY);
					case PROGRESS -> drawButton(ui, restart, RESTART, mouseX, mouseY);
					case REACH -> drawButton(ui, reach, shown.reach() + " blocks", mouseX, mouseY);
					case LABEL_SIZE -> drawButton(ui, labelSize, labelSizeName(shown), mouseX, mouseY);
					default -> {
					}
				}
			}
		}
	}

	private static void drawButton(Ui ui, int[] box, String text, int mouseX, int mouseY) {
		boolean over = mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + BUTTON_HEIGHT;
		ui.rounded(box[0], box[1], box[2], BUTTON_HEIGHT, Radius.SM, Ui.mix(ui.palette.control(), 0xFFFFFFFF, over ? 0.1f : 0.04f));
		ui.ring(box[0], box[1], box[2], BUTTON_HEIGHT, Radius.SM, 1, ui.palette.controlBorder());
		ui.textAtScaled(text, box[0] + CHIP_PAD + 2, box[1] + BUTTON_HEIGHT / 2f - 3.5f, TextScale.SMALL, ui.palette.text());
	}

	/**
	 * The group's areas as chips, ending at the right, with "+ Add" last; a chip's "×" on hover takes the area away. An area's name is one the
	 * player chose, so it is the only text here that may be cut short, with the whole name on hover.
	 */
	private void drawAreas(Ui ui, int leftLimit, int mouseX, int mouseY) {
		List<String> areas = shown.areas();
		int count = areas.size() + 1;
		if (chipX.length < count) {
			chipX = new int[count];
			chipWidth = new int[count];
		}
		chipCount = count;
		int right = x + width - PAD;
		for (int i = count - 1; i >= 0; i--) {
			boolean add = i == areas.size();
			String full = add ? ADD_AREA : WaypointAreas.nameOf(areas.get(i));
			int room = Math.max(30, right - leftLimit - (add ? 0 : 8) - 2 * CHIP_PAD);
			String text = add ? full : ui.fitScaled(full, room, TextScale.SMALL);
			int w = Math.round(ui.widthScaled(text, TextScale.SMALL)) + 2 * CHIP_PAD + (add ? 0 : 10);
			int left = right - w;
			chipX[i] = left;
			chipWidth[i] = w;
			boolean over = mouseX >= left && mouseX < right && mouseY >= chipsY && mouseY < chipsY + CHIP_HEIGHT;
			if (add) {
				ui.dashedRounded(left, chipsY, w, CHIP_HEIGHT, Radius.XS, Ui.withOpacity(ui.palette.textSecondary(), over ? 0.9f : 0.5f));
				ui.textAtScaled(text, left + CHIP_PAD, chipsY + CHIP_HEIGHT / 2f - 3.5f, TextScale.SMALL, over ? ui.palette.text() : ui.palette.textSecondary());
			} else {
				ui.rounded(left, chipsY, w, CHIP_HEIGHT, Radius.XS, ui.palette.control());
				ui.ring(left, chipsY, w, CHIP_HEIGHT, Radius.XS, 1, ui.palette.controlBorder());
				ui.textAtScaled(text, left + CHIP_PAD, chipsY + CHIP_HEIGHT / 2f - 3.5f, TextScale.SMALL, ui.palette.text());
				ui.textAtScaled("×", right - CHIP_PAD - 4, chipsY + CHIP_HEIGHT / 2f - 3.5f, TextScale.SMALL,
					over ? ui.palette.text() : Ui.withOpacity(ui.palette.textSecondary(), 0.6f));
				if (over && !text.equals(full)) {
					Control.showFullText(full);
				}
			}
			right = left - CHIP_GAP;
		}
	}

	/** @return whether the click was in the tab */
	boolean click(double mouseX, double mouseY, int button, java.util.function.BiConsumer<Integer, Integer> addArea) {
		if (shown == null || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
			return false;
		}
		if (mouseY >= chipsY && mouseY < chipsY + CHIP_HEIGHT) {
			for (int i = 0; i < chipCount; i++) {
				if (mouseX >= chipX[i] && mouseX < chipX[i] + chipWidth[i]) {
					if (i == chipCount - 1) {
						addArea.accept(chipX[i] + chipWidth[i], chipsY + CHIP_HEIGHT + 2);
					} else if (!shown.readOnly()) {
						shown.areas().remove(i);
						feature.areasChanged(shown);
					}
					return true;
				}
			}
		}
		if (button == ScreenInput.LEFT && hit(restart, mouseX, mouseY) && shown.show() == WaypointGroup.Show.NEXT) {
			restart(shown);
			return true;
		}
		if (button == ScreenInput.LEFT && hit(reach, mouseX, mouseY) && shown.show() == WaypointGroup.Show.NEXT && shown.advance() == WaypointGroup.Advance.REACHED) {
			menu.open(reach[0] + reach[2], reach[1] + BUTTON_HEIGHT + 2, reachItems(shown));
			return true;
		}
		if (button == ScreenInput.LEFT && hit(labelSize, mouseX, mouseY) && shown.label() != WaypointGroup.Label.OFF) {
			menu.open(labelSize[0] + labelSize[2], labelSize[1] + BUTTON_HEIGHT + 2, labelSizeItems(shown));
			return true;
		}
		for (Control control : controls) {
			if (inRows(control) && control.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
		}
		return true;
	}

	/** @return whether a control has a row now: one left out (how many ahead without Next) takes no clicks */
	private boolean inRows(Control control) {
		for (Section section : sections) {
			for (Row row : section.rows()) {
				if (row.control == control) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hit(int[] box, double mouseX, double mouseY) {
		return box[2] > 0 && mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + BUTTON_HEIGHT;
	}

	/** Starts the route at its first point again, now. */
	void restart(WaypointGroup group) {
		group.restart();
		UiSounds.click();
		feature.store().changed(group);
		shownChanges = feature.store().changes();
	}

	/** @return the reaches a point counts as reached in, a mark on the group's */
	private List<WaypointMenu.Item> reachItems(WaypointGroup group) {
		List<WaypointMenu.Item> items = new ArrayList<>();
		for (int blocks = RouteProgress.MIN_REACH; blocks <= RouteProgress.MAX_REACH; blocks += blocks < 6 ? 1 : 2) {
			int chosen = blocks;
			items.add(WaypointMenu.Item.of((blocks == group.reach() ? "✓ " : "    ") + blocks + " blocks", () -> {
				group.reach(chosen);
				feature.store().changed(group);
			}));
		}
		return items;
	}

	/** @return the sizes of the labels, a mark on the group's */
	private List<WaypointMenu.Item> labelSizeItems(WaypointGroup group) {
		List<WaypointMenu.Item> items = new ArrayList<>();
		for (int i = 0; i < LABEL_SIZES.length; i++) {
			float size = LABEL_SIZES[i];
			boolean current = Math.abs(group.labelSize() - size) < 0.01f;
			items.add(WaypointMenu.Item.of((current ? "✓ " : "    ") + LABEL_SIZE_NAMES[i], () -> {
				group.labelSize(size);
				feature.store().changed(group);
			}));
		}
		return items;
	}

	void renderOverlay(Ui ui, int mouseX, int mouseY) {
		for (Control control : controls) {
			control.renderOverlay(ui, mouseX, mouseY);
		}
	}

	/** @return the control with an open popup (a dropdown), or null */
	Control openOverlay() {
		for (Control control : controls) {
			if (control.isOverlayOpen()) {
				return control;
			}
		}
		return null;
	}
}
