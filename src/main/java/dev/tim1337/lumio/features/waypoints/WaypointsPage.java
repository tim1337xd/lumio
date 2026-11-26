package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointColors;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;
import dev.tim1337.lumio.ui.controls.KeyControl;
import dev.tim1337.lumio.ui.controls.SwitchControl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Waypoints page, as one control that fills the page: the groups on the left (each with its count and switch, "New group", and the quick add
 * with its key at the bottom), and the shown group on the right: its name with where it is from, its switch, "···" and "+ Add point", and two
 * tabs: Points (a search, Restart at 1, the numbered points and the route as a map) and Settings (Where, Route and Look, {@link GroupSettingsCard}).
 */
final class WaypointsPage extends Control {
	private static final int GROUP_HEIGHT = 30;
	private static final int GROUP_GAP = 5;
	private static final int NEW_GROUP_HEIGHT = 22;
	private static final int COLUMN_GAP = 10;
	private static final int MIN_LEFT = 128;
	private static final int MAX_LEFT = 175;
	private static final float LEFT_SHARE = 0.3f;
	private static final int LABEL_ROOM = 14;
	private static final int TITLE_ROOM = 30;
	/** The tabs under the title, and the room between them and the tab's content. */
	private static final int TAB_HEIGHT = 18;
	private static final int TAB_GAP = 8;
	private static final int TAB_PAD = 8;
	private static final String POINTS_TAB = "Points";
	private static final String SETTINGS_TAB = "Settings";
	/** The search and Restart at 1 above the points. */
	private static final int LIST_HEAD = 26;
	private static final String FIND = "Find a point";
	private static final String RESTART = "Restart at 1";
	private static final String IMPORTED_FROM = "imported from ";
	/** The label sizes the group menu offers, from the least to the most a group takes. */
	private static final int SECTION_GAP = 8;
	private static final int MAP_MIN_HEIGHT = 130;
	private static final int CAPTION = 12;
	/** Below this width of the right column, the map goes under the points instead of beside them. */
	private static final int SIDE_BY_SIDE = 330;
	private static final float LIST_SHARE = 0.58f;
	private static final int TILE = 18;
	private static final int ZOOM_BUTTON = 11;
	private static final int BUTTON_HEIGHT = 18;
	private static final int QUICK_ROW = 14;
	private static final int KEY_CHIP = 30;
	/** The groups' switches are drawn smaller than the menu's, as in a list. */
	private static final float SWITCH_SCALE = 0.7f;
	private static final int SWITCH_WIDTH = Math.round(SwitchControl.TRACK_WIDTH * SWITCH_SCALE);
	private static final int SWITCH_HEIGHT = Math.round(SwitchControl.TRACK_HEIGHT * SWITCH_SCALE);
	private static final String[][] QUICK_ADD = {
		{"", "Key: place a point where you look"},
		{"", "Route"},
		{"Click", "In chat"},
		{"Ctrl V", "Paste"}};
	private static final String ADD_POINT = "+ Add point";
	private static final String NO_WAYPOINTS = "No waypoints yet";
	private static final String SAVE = "Save";
	private static final int TEMP_ROW = 12;
	/** The place key's row in the quick add list: its key field, and under it the small line of a conflict or of what the waiting field does. */
	private static final int KEY_ROW = 24;
	private static final int KEY_NOTE = 10;
	private static final int KEY_LABEL = 11;
	private static final String HOLD = "Hold ";
	private static final String HOLD_UNSET = "Hold it";

	private final WaypointsFeature feature;
	private final GroupSettingsCard card;
	/** The tabs' places of the last layout; which tab is shown is the feature's ({@link WaypointsFeature#settingsTab}). */
	private int tabsY;
	private int pointsTabWidth;
	private int settingsTabWidth;
	private int contentY;
	/** The search over the points, and whether it takes the keys. */
	private String search = "";
	private boolean searching;
	private int searchWidth;
	private int restartX;
	private int restartWidth;
	/** The group's switch in the header. */
	private int headerSwitchX;
	private float headerSwitch;
	private final PointListView list = new PointListView();
	private final WaypointMapView map = new WaypointMapView();
	private final WaypointMenu menu = new WaypointMenu();
	private final InlineEdit edit = new InlineEdit();
	private final ImportView importView;
	private final float[] switchPositions = new float[64];
	private final float[] groupHovers = new float[64];
	// the layout of the last measure
	private int leftWidth;
	private int rightX;
	private int rightWidth;
	private int cardY;
	private int listX;
	private int listY;
	private int listWidth;
	private int listHeight;
	private int mapX;
	private int mapY;
	private int mapWidth;
	private int mapHeight;
	private int newGroupY;
	/** Where the groups start: under the temporary waypoints, while there are any. */
	private int groupsY;
	private int addPointX;
	private int groupMenuX;
	/** The place key as a key field in the quick add list: the same key as in the feature's settings and in the list of all keys. */
	private KeyControl placeField;
	private KeySetting placeSetting;
	private WaypointGroup laidOut;
	private int screenWidth;
	private int screenHeight;

	WaypointsPage(WaypointsFeature feature) {
		this.feature = feature;
		this.card = new GroupSettingsCard(feature, menu);
		this.importView = new ImportView(feature, menu, edit);
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
	public boolean bare() {
		return true;
	}

	/** The page takes Ctrl+V for the import once it was clicked; it types only while a name is edited. */
	@Override
	public boolean wantsKeyboard() {
		return true;
	}

	@Override
	public boolean takesText() {
		return edit.active() || searching;
	}

	private List<WaypointGroup> groups() {
		return feature.store().groups();
	}

	@Override
	protected int measure(Ui ui, int width) {
		if (feature.importOpen()) {
			String pending = feature.takeImportText();
			if (pending != null) {
				importView.take(pending, false);
			}
			return importView.measure(ui, x, y, width);
		}
		leftWidth = Math.clamp(Math.round(width * LEFT_SHARE), MIN_LEFT, MAX_LEFT);
		rightX = x + leftWidth + COLUMN_GAP;
		rightWidth = width - leftWidth - COLUMN_GAP;
		int count = Math.min(groups().size(), switchPositions.length);
		groupsY = y + temporaryHeight();
		newGroupY = groupsY + LABEL_ROOM + count * (GROUP_HEIGHT + GROUP_GAP);
		int leftHeight = newGroupY - y + NEW_GROUP_HEIGHT + SECTION_GAP * 2 + quickAddHeight();
		WaypointGroup group = feature.selectedGroup();
		laidOut = group;
		int rightHeight = TITLE_ROOM;
		if (group != null) {
			tabsY = y + TITLE_ROOM;
			contentY = tabsY + TAB_HEIGHT + TAB_GAP;
			if (feature.settingsTab()) {
				cardY = contentY;
				rightHeight = card.measure(ui, group, rightX, contentY, rightWidth) + contentY - y;
			} else {
				list.filter(search);
				if (rightWidth >= SIDE_BY_SIDE) {
					listX = rightX;
					listWidth = Math.round(rightWidth * LIST_SHARE);
					listY = contentY;
					listHeight = Math.max(LIST_HEAD + list.measure(group, listX, listY + LIST_HEAD, listWidth), MAP_MIN_HEIGHT);
					mapX = listX + listWidth + SECTION_GAP;
					mapY = listY;
					mapWidth = rightWidth - listWidth - SECTION_GAP;
					mapHeight = Math.max(MAP_MIN_HEIGHT, Math.min(listHeight, mapWidth + 30));
				} else {
					listX = rightX;
					listWidth = rightWidth;
					listY = contentY;
					listHeight = LIST_HEAD + list.measure(group, listX, listY + LIST_HEAD, listWidth);
					mapX = rightX;
					mapY = listY + listHeight + SECTION_GAP;
					mapWidth = rightWidth;
					mapHeight = MAP_MIN_HEIGHT;
				}
				rightHeight = Math.max(listY + listHeight, mapY + mapHeight) + 4 - y;
			}
		}
		return Math.max(leftHeight, rightHeight);
	}

	private static int quickAddHeight() {
		return LABEL_ROOM + 2 * KEY_LABEL + KEY_ROW + KEY_NOTE + (QUICK_ADD.length - 1) * QUICK_ROW + 12;
	}

	/** @return the place key's field, made once: the key's setting is the one the feature's Keys group shows */
	private KeyControl placeField() {
		if (placeField == null) {
			for (KeySetting key : KeySetting.all()) {
				if (key.mapping() == feature.placing().placeKey()) {
					placeSetting = key;
					placeField = new KeyControl(key, () -> { });
					break;
				}
			}
		}
		return placeField;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		screenWidth = ui.graphics.guiWidth();
		screenHeight = ui.graphics.guiHeight();
		if (!feature.importOpen() && feature.selectedGroup() != laidOut) {
			// a group was added or taken away since the layout: the next frame lays it out again
			return;
		}
		boolean menuOpen = menu.open() || card.openOverlay() != null;
		int hoverX = menuOpen ? -1 : mouseX;
		int hoverY = menuOpen ? -1 : mouseY;
		if (feature.importOpen()) {
			importView.render(ui, hoverX, hoverY);
			return;
		}
		renderGroups(ui, hoverX, hoverY);
		renderRight(ui, hoverX, hoverY);
	}

	// ------------------------------------------------------------------------------------------------------ the groups

	private void renderGroups(Ui ui, int mouseX, int mouseY) {
		int points = 0;
		for (WaypointGroup group : groups()) {
			points += group.points().size();
		}
		renderTemporary(ui, mouseX, mouseY);
		ui.sectionLabel("GROUPS", x + 2, groupsY + 1, ui.palette.textSecondary());
		String total = String.valueOf(groups().size());
		float totalWidth = ui.widthScaled(total, TextScale.SMALL);
		ui.textAtScaled(total, x + leftWidth - totalWidth - 2, groupsY + 1, TextScale.SMALL, ui.palette.textSecondary());
		WaypointGroup selected = feature.selectedGroup();
		int count = Math.min(groups().size(), switchPositions.length);
		for (int i = 0; i < count; i++) {
			WaypointGroup group = groups().get(i);
			int top = groupsY + LABEL_ROOM + i * (GROUP_HEIGHT + GROUP_GAP);
			boolean over = mouseX >= x && mouseX < x + leftWidth && mouseY >= top && mouseY < top + GROUP_HEIGHT;
			groupHovers[i] = ui.approach(groupHovers[i], over ? 1f : 0f, Ui.HOVER_RATE);
			boolean chosen = group == selected;
			ui.rounded(x, top, leftWidth, GROUP_HEIGHT, Radius.MD, Ui.over(Ui.withOpacity(ui.palette.hover(), groupHovers[i] * 0.6f), ui.palette.card()));
			ui.ring(x, top, leftWidth, GROUP_HEIGHT, Radius.MD, 1, chosen ? ui.accent : ui.palette.cardBorder());
				int rgb = 0xFF000000 | group.rgb();
			// the group's color as a bar at its left
			ui.rounded(x + 6, top + 6, 3, GROUP_HEIGHT - 12, Radius.XS, rgb);
			int textX = x + 6 + 3 + 8;
			int switchX = x + leftWidth - 7 - SWITCH_WIDTH;
			int room = switchX - 6 - textX;
			if (edit.editing(group.id())) {
				edit.draw(ui, textX, top + 3, room, 13);
			} else {
				String name = ui.fit(group.name(), room);
				ui.textAtScaledBold(name, textX, top + 6, TextScale.BODY, ui.palette.text());
				if (!name.equals(group.name()) && over && mouseX < switchX) {
					// a name the player chose may be cut short; the whole of it on hover
					Control.showFullText(group.name());
				}
			}
			String sub = group.points().size() + (group.points().size() == 1 ? " point" : " points");
			ui.textAtScaled(sub, textX, top + 18, TextScale.SMALL, ui.palette.textSecondary());
			switchPositions[i] = ui.approach(switchPositions[i], group.enabled() ? 1f : 0f, Ui.MOVE_RATE);
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(switchX, top + (GROUP_HEIGHT - SWITCH_HEIGHT) / 2f);
			pose.scale(SWITCH_SCALE, SWITCH_SCALE);
			SwitchControl.draw(ui, 0, 0, switchPositions[i], over ? 0.5f : 0f, 0f);
			pose.popMatrix();
		}
		boolean overNew = mouseX >= x && mouseX < x + leftWidth && mouseY >= newGroupY && mouseY < newGroupY + NEW_GROUP_HEIGHT;
		ui.dashedRounded(x, newGroupY, leftWidth, NEW_GROUP_HEIGHT, Radius.MD, Ui.withOpacity(ui.palette.textSecondary(), overNew ? 0.85f : 0.45f));
		ui.textCentered("+  New group", x + leftWidth / 2, newGroupY + NEW_GROUP_HEIGHT / 2, overNew ? ui.palette.text() : ui.palette.textSecondary());
		renderQuickAdd(ui, mouseX, mouseY);
	}

	// ------------------------------------------------------------------------------------------------------ temporary waypoints

	/** @return the room the temporary waypoints take at the top of the left column: none while there are none */
	private int temporaryHeight() {
		int count = feature.temporary().size();
		return count == 0 ? 0 : LABEL_ROOM + count * TEMP_ROW + SECTION_GAP;
	}

	/** The temporary waypoints from chat, each with Save, which moves it into a group. */
	private void renderTemporary(Ui ui, int mouseX, int mouseY) {
		List<TemporaryWaypoints.Temp> temps = feature.temporary();
		if (temps.isEmpty()) {
			return;
		}
		ui.sectionLabel("TEMPORARY", x + 2, y + 1, ui.palette.textSecondary());
		float saveWidth = ui.boldWidthScaled(SAVE, TextScale.SMALL);
		for (int i = 0; i < temps.size(); i++) {
			int top = y + LABEL_ROOM + i * TEMP_ROW;
			TemporaryWaypoints.Temp temp = temps.get(i);
			ui.rounded(x + 2, top + 3, 6, 6, Radius.XS, 0xFF000000 | TemporaryWaypoints.RGB);
			ui.textAtScaled(ui.fitScaled(temp.point().name(), leftWidth - saveWidth - 24, TextScale.SMALL), x + 12, top + 2, TextScale.SMALL,
				ui.palette.text());
			boolean over = mouseX >= x + leftWidth - saveWidth - 6 && mouseX < x + leftWidth && mouseY >= top && mouseY < top + TEMP_ROW;
			ui.textAtScaledBold(SAVE, x + leftWidth - saveWidth - 2, top + 2, TextScale.SMALL, over ? ui.palette.text() : ui.accent);
		}
	}

	/** Save on a temporary waypoint: a menu of the groups to move it into. @return whether the click was on one */
	private boolean clickTemporary(double mouseX, double mouseY) {
		List<TemporaryWaypoints.Temp> temps = feature.temporary();
		for (int i = 0; i < temps.size(); i++) {
			int top = y + LABEL_ROOM + i * TEMP_ROW;
			if (mouseY >= top && mouseY < top + TEMP_ROW) {
				TemporaryWaypoints.Temp temp = temps.get(i);
				List<WaypointMenu.Item> items = new ArrayList<>();
				for (WaypointGroup group : groups()) {
					if (!group.readOnly()) {
						items.add(WaypointMenu.Item.of("Save to " + group.name(), () -> feature.saveTemporary(temp, group)));
					}
				}
				items.add(WaypointMenu.Item.of("Save to a new group", () -> feature.saveTemporary(temp, feature.newGroup())));
				menu.open((int) mouseX + 1, (int) mouseY + 4, items);
				return true;
			}
		}
		return false;
	}

	/** The keys of the quick add in game, in a card at the bottom of the left column; the place key is set right here. */
	private void renderQuickAdd(Ui ui, int mouseX, int mouseY) {
		String placeKey = feature.placing().placeKeyName();
		int height = quickAddHeight();
		int top = Math.max(newGroupY + NEW_GROUP_HEIGHT + 2 * SECTION_GAP, y + this.height - height);
		ui.rounded(x, top, leftWidth, height, Radius.MD, ui.palette.card());
		ui.ring(x, top, leftWidth, height, Radius.MD, 1, ui.palette.cardBorder());
		ui.sectionLabel("QUICK ADD", x + 8, top + 7, ui.palette.textSecondary());
		int rowTop = top + LABEL_ROOM + 8;
		KeyControl field = placeField();
		if (field != null) {
			// what the key does on lines of its own, wrapped: beside the field it would be cut in the narrow column
			List<String> lines = ui.wrapScaled(QUICK_ADD[0][1], leftWidth - 16, TextScale.SMALL, 2);
			for (String each : lines) {
				ui.textAtScaled(each, x + 8, rowTop, TextScale.SMALL, ui.palette.textSecondary());
				rowTop += KEY_LABEL - 1;
			}
			rowTop += 1;
			field.place(ui, x + 8, rowTop, leftWidth - 16);
			field.render(ui, mouseX, mouseY);
			String note = placeSetting.description();
			if (note != null) {
				int color = placeSetting.quietNote() ? ui.palette.textSecondary()
					: placeSetting.conflict() == KeySetting.Conflict.OWN ? 0xFF000000 | SystemColors.RED_TEXT_RGB : SystemColors.ORANGE;
				ui.textAtScaled(ui.fitScaled(note, leftWidth - 16, TextScale.SMALL), x + 8, rowTop + KEY_ROW, TextScale.SMALL, color);
			}
		}
		rowTop += KEY_ROW + KEY_NOTE;
		for (int i = 1; i < QUICK_ADD.length; i++) {
			int middle = rowTop + (i - 1) * QUICK_ROW + QUICK_ROW / 2;
			// holding the place key records; while no key is set the chip says so without naming one
			boolean unset = i == 1 && placeKey.isEmpty();
			String key = unset ? HOLD_UNSET : i == 1 ? HOLD + placeKey : QUICK_ADD[i][0];
			float keyWidth = ui.boldWidthScaled(key, TextScale.SMALL);
			int chip = Math.max(KEY_CHIP, Math.round(keyWidth) + 8);
			ui.rounded(x + 8, middle - 5, chip, 10, Radius.XS, ui.palette.control());
			ui.textAtScaledBold(key, x + 8 + (chip - keyWidth) / 2f, middle - 3.5f, TextScale.SMALL, unset ? ui.palette.textSecondary() : ui.palette.text());
			ui.textAtScaled(ui.fitScaled(QUICK_ADD[i][1], leftWidth - chip - 24, TextScale.SMALL), x + 8 + chip + 7, middle - 3.5f,
				TextScale.SMALL, ui.palette.textSecondary());
		}
	}

	// ------------------------------------------------------------------------------------------------------ the shown group

	private void renderRight(Ui ui, int mouseX, int mouseY) {
		WaypointGroup group = laidOut;
		if (group == null) {
			// a heading wider than the column (the Minecraft font in a narrow window) is set at the body size
			float titleScale = ui.boldWidthScaled(NO_WAYPOINTS, TextScale.HEADING) <= rightWidth ? TextScale.HEADING : TextScale.BODY;
			ui.textAtScaledBold(NO_WAYPOINTS, rightX, y + 2, titleScale, ui.palette.text());
			List<String> lines = ui.wrapScaled("Make a group with + New group, or press your place key in game to mark the block you look at.", rightWidth,
				TextScale.SMALL, 6);
			for (int i = 0; i < lines.size(); i++) {
				ui.textAtScaled(lines.get(i), rightX, y + 18 + i * 10, TextScale.SMALL, ui.palette.textSecondary());
			}
			return;
		}
		// the title, with where the group is from, its switch, "···" and "+ Add point"
		int addWidth = Math.round(ui.boldWidthScaled(ADD_POINT, TextScale.BODY)) + 16;
		addPointX = rightX + rightWidth - addWidth;
		groupMenuX = addPointX - BUTTON_HEIGHT - 5;
		headerSwitchX = groupMenuX - 6 - SWITCH_WIDTH;
		if (!group.readOnly()) {
			boolean overAdd = over(mouseX, mouseY, addPointX, y + 1, addWidth, BUTTON_HEIGHT);
			ui.rounded(addPointX, y + 1, addWidth, BUTTON_HEIGHT, Radius.SM, Ui.mix(ui.accent, 0xFFFFFFFF, overAdd ? 0.12f : 0f));
			ui.textAtScaledBold(ADD_POINT, addPointX + 8, y + 1 + BUTTON_HEIGHT / 2f - 4f, TextScale.BODY, 0xFFFFFFFF);
		}
		boolean overMenu = over(mouseX, mouseY, groupMenuX, y + 1, BUTTON_HEIGHT, BUTTON_HEIGHT);
		ui.rounded(groupMenuX, y + 1, BUTTON_HEIGHT, BUTTON_HEIGHT, Radius.SM, Ui.withOpacity(ui.palette.hover(), overMenu ? 1f : 0.5f));
		WaypointMenu.drawDots(ui, groupMenuX + BUTTON_HEIGHT / 2f, y + 1 + BUTTON_HEIGHT / 2f, ui.palette.text());
		headerSwitch = ui.approach(headerSwitch, group.enabled() ? 1f : 0f, Ui.MOVE_RATE);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(headerSwitchX, y + 1 + (BUTTON_HEIGHT - SWITCH_HEIGHT) / 2f);
		pose.scale(SWITCH_SCALE, SWITCH_SCALE);
		SwitchControl.draw(ui, 0, 0, headerSwitch, over(mouseX, mouseY, headerSwitchX, y + 1, SWITCH_WIDTH, BUTTON_HEIGHT) ? 0.5f : 0f, 0f);
		pose.popMatrix();
		int titleRoom = headerSwitchX - 8 - rightX;
		if (edit.editing(group.id() + "#title")) {
			edit.draw(ui, rightX, y, titleRoom, 16);
		} else {
			String title = ui.fitScaled(group.name(), titleRoom, TextScale.HEADING);
			ui.textAtScaledBold(title, rightX, y + 1, TextScale.HEADING, ui.palette.text());
			if (!title.equals(group.name()) && over(mouseX, mouseY, rightX, y, titleRoom, 14)) {
				Control.showFullText(group.name());
			}
		}
		String from = subtitle(group);
		ui.textAtScaled(ui.fitScaled(from, titleRoom, TextScale.SMALL), rightX, y + 16, TextScale.SMALL, ui.palette.textSecondary());
		renderTabs(ui, group, mouseX, mouseY);
		if (feature.settingsTab()) {
			card.render(ui, mouseX, mouseY);
			return;
		}
		ui.rounded(listX, listY, listWidth, listHeight, Radius.LG, ui.palette.card());
		ui.ring(listX, listY, listWidth, listHeight, Radius.LG, 1, ui.palette.cardBorder());
		renderListHead(ui, group, mouseX, mouseY);
		list.render(ui, group, edit, mouseX, mouseY);
		renderMap(ui, group, mouseX, mouseY);
	}

	/** The tabs Points (with the count) and Settings, with a line under the chosen one. */
	private void renderTabs(Ui ui, WaypointGroup group, int mouseX, int mouseY) {
		String points = POINTS_TAB + "  " + group.points().size();
		pointsTabWidth = ui.width(points) + 2 * TAB_PAD;
		settingsTabWidth = ui.width(SETTINGS_TAB) + 2 * TAB_PAD;
		ui.fill(rightX, tabsY + TAB_HEIGHT - 1, rightWidth, 1, ui.palette.cardBorder());
		int left = rightX;
		for (int tab = 0; tab < 2; tab++) {
			boolean chosen = (tab == 1) == feature.settingsTab();
			int w = tab == 0 ? pointsTabWidth : settingsTabWidth;
			boolean over = over(mouseX, mouseY, left, tabsY, w, TAB_HEIGHT);
			int color = chosen ? ui.palette.text() : over ? Ui.mix(ui.palette.textSecondary(), ui.palette.text(), 0.5f) : ui.palette.textSecondary();
			ui.textMiddle(tab == 0 ? points : SETTINGS_TAB, left + TAB_PAD, tabsY + TAB_HEIGHT / 2 - 1, color);
			if (chosen) {
				ui.fill(left + 2, tabsY + TAB_HEIGHT - 2, w - 4, 2, ui.accent);
			}
			left += w + 4;
		}
	}

	/** The search over the points and Restart at 1, at the top of the list. */
	private void renderListHead(Ui ui, WaypointGroup group, int mouseX, int mouseY) {
		int top = listY + 6;
		boolean route = group.show() == WaypointGroup.Show.NEXT;
		restartWidth = route ? Math.round(ui.widthScaled(RESTART, TextScale.SMALL)) + 16 : 0;
		restartX = listX + listWidth - 6 - restartWidth;
		searchWidth = (route ? restartX - 6 : listX + listWidth - 6) - (listX + 6);
		ui.rounded(listX + 6, top, searchWidth, LIST_HEAD - 10, Radius.SM, Ui.withOpacity(ui.palette.hover(), searching ? 1f : 0.7f));
		if (searching) {
			ui.ring(listX + 6, top, searchWidth, LIST_HEAD - 10, Radius.SM, 1, ui.accent);
		}
		ui.icon("search", listX + 12, top + (LIST_HEAD - 10) / 2 - 4, 8, ui.palette.textSecondary());
		String shown = search.isEmpty() && !searching ? FIND : ui.fitScaled(search, searchWidth - 26, TextScale.SMALL);
		ui.textAtScaled(shown, listX + 24, top + (LIST_HEAD - 10) / 2f - 3.5f, TextScale.SMALL, search.isEmpty() ? ui.palette.textSecondary() : ui.palette.text());
		if (searching && System.currentTimeMillis() / 530 % 2 == 0) {
			ui.fill(listX + 24 + Math.round(ui.widthScaled(shown, TextScale.SMALL)) + 1, top + 4, 1, LIST_HEAD - 18, ui.palette.text());
		}
		if (route) {
			boolean over = over(mouseX, mouseY, restartX, top, restartWidth, LIST_HEAD - 10);
			ui.rounded(restartX, top, restartWidth, LIST_HEAD - 10, Radius.SM, Ui.mix(ui.palette.control(), 0xFFFFFFFF, over ? 0.1f : 0.04f));
			ui.ring(restartX, top, restartWidth, LIST_HEAD - 10, Radius.SM, 1, ui.palette.controlBorder());
			ui.textAtScaled(RESTART, restartX + 8, top + (LIST_HEAD - 10) / 2f - 3.5f, TextScale.SMALL, ui.palette.text());
		}
	}

	/** @return the one line under the title: where the group is from and where it shows ("From Skyblocker · Dwarven Mines") */
	private String subtitle(WaypointGroup group) {
		String areas = WaypointAreas.summary(group);
		String source = group.source();
		// an older import wrote its source as a phrase ("imported from SkyHanni")
		source = source.regionMatches(true, 0, IMPORTED_FROM, 0, IMPORTED_FROM.length()) ? source.substring(IMPORTED_FROM.length()) : source;
		return source.isEmpty() ? areas : "From " + source + "  ·  " + areas;
	}

	private void renderMap(Ui ui, WaypointGroup group, int mouseX, int mouseY) {
		ui.rounded(mapX, mapY, mapWidth, mapHeight, Radius.LG, ui.palette.card());
		ui.ring(mapX, mapY, mapWidth, mapHeight, Radius.LG, 1, ui.palette.cardBorder());
		int innerX = mapX + 6;
		int innerY = mapY + 18;
		int innerWidth = mapWidth - 12;
		int innerHeight = mapHeight - 24;
		ui.rounded(innerX, innerY, innerWidth, innerHeight, Radius.MD, Ui.mix(ui.palette.card(), 0xFF0A0F1A, 0.6f));
		LocalPlayer player = Minecraft.getInstance().player;
		double[] at = player != null && WaypointAreas.here(group) ? new double[] {player.getX(), player.getZ()} : null;
		int next = group.show() == WaypointGroup.Show.NEXT && !group.points().isEmpty() ? group.next() : -1;
		map.draw(ui, innerX + 1, innerY + 1, innerWidth - 2, innerHeight - 2, group, at, player == null ? 0f : player.getYRot(), next, list.highlighted(), mouseX, mouseY);
		ui.sectionLabel("ROUTE", mapX + 8, mapY + 6, ui.palette.textSecondary());
		WaypointMapView.drawNorth(ui, mapX + mapWidth - 12, mapY + 9);
		int zoomX = zoomX();
		WaypointMapView.drawZoomButton(ui, zoomX, zoomInY(), ZOOM_BUTTON, true, over(mouseX, mouseY, zoomX, zoomInY(), ZOOM_BUTTON, ZOOM_BUTTON) ? 1f : 0f);
		WaypointMapView.drawZoomButton(ui, zoomX, zoomOutY(), ZOOM_BUTTON, false, over(mouseX, mouseY, zoomX, zoomOutY(), ZOOM_BUTTON, ZOOM_BUTTON) ? 1f : 0f);
	}

	private int zoomInY() {
		return mapY + mapHeight - 2 * ZOOM_BUTTON - 13;
	}

	private int zoomOutY() {
		return mapY + mapHeight - ZOOM_BUTTON - 10;
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (laidOut != null) {
			card.renderOverlay(ui, mouseX, mouseY);
		}
		menu.render(ui, mouseX, mouseY, screenWidth, screenHeight);
	}

	@Override
	public boolean isOverlayOpen() {
		return menu.open() || card.openOverlay() != null;
	}

	// ------------------------------------------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (menu.open()) {
			return menu.click(mouseX, mouseY);
		}
		if (placeField != null && placeField.listening()) {
			// while the place key's field waits, a mouse button is its key; a left click ends the waiting
			placeField.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		Control overlay = card.openOverlay();
		if (overlay != null) {
			overlay.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		if (edit.active()) {
			// a click elsewhere keeps the name typed so far
			edit.keyPressed(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0);
		}
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		if (feature.importOpen()) {
			importView.click(mouseX, mouseY);
			return true;
		}
		if (mouseX < x + leftWidth) {
			return clickGroups(mouseX, mouseY, button);
		}
		WaypointGroup group = laidOut;
		if (group == null) {
			return true;
		}
		if (button != ScreenInput.LEFT) {
			return true;
		}
		searching = false;
		if (over(mouseX, mouseY, addPointX, y + 1, rightX + rightWidth - addPointX, BUTTON_HEIGHT) && !group.readOnly()) {
			addPointHere(group);
			return true;
		}
		if (over(mouseX, mouseY, groupMenuX, y + 1, BUTTON_HEIGHT, BUTTON_HEIGHT)) {
			openGroupMenu(group, groupMenuX + BUTTON_HEIGHT, y + 1 + BUTTON_HEIGHT + 2);
			return true;
		}
		if (over(mouseX, mouseY, headerSwitchX - 2, y, SWITCH_WIDTH + 4, BUTTON_HEIGHT + 2)) {
			group.enabled(!group.enabled());
			UiSounds.toggle(group.enabled());
			feature.store().changed(group);
			return true;
		}
		if (mouseY < tabsY && mouseX < headerSwitchX && !group.readOnly()) {
			edit.start(group.id() + "#title", group.name(), WaypointGroup.MAX_NAME, name -> rename(group, name));
			return true;
		}
		if (mouseY >= tabsY && mouseY < tabsY + TAB_HEIGHT) {
			boolean settings = mouseX >= rightX + pointsTabWidth + 4 && mouseX < rightX + pointsTabWidth + 4 + settingsTabWidth;
			boolean points = mouseX >= rightX && mouseX < rightX + pointsTabWidth;
			if ((settings || points) && settings != feature.settingsTab()) {
				feature.settingsTab(settings);
				UiSounds.click();
			}
			return true;
		}
		if (feature.settingsTab()) {
			return card.click(mouseX, mouseY, button, (ax, ay) -> openAreaMenu(group, ax, ay));
		}
		int headTop = listY + 6;
		if (over(mouseX, mouseY, listX + 6, headTop, searchWidth, LIST_HEAD - 10)) {
			searching = true;
			return true;
		}
		if (restartWidth > 0 && over(mouseX, mouseY, restartX, headTop, restartWidth, LIST_HEAD - 10)) {
			card.restart(group);
			return true;
		}
		if (clickMap(group, mouseX, mouseY)) {
			return true;
		}
		return clickList(group, mouseX, mouseY);
	}

	private boolean clickGroups(double mouseX, double mouseY, int button) {
		if (clickTemporary(mouseX, mouseY)) {
			return true;
		}
		int count = Math.min(groups().size(), switchPositions.length);
		for (int i = 0; i < count; i++) {
			int top = groupsY + LABEL_ROOM + i * (GROUP_HEIGHT + GROUP_GAP);
			if (mouseY >= top && mouseY < top + GROUP_HEIGHT) {
				WaypointGroup group = groups().get(i);
				if (button == ScreenInput.RIGHT) {
					openGroupMenu(group, (int) mouseX + 1, (int) mouseY);
					return true;
				}
				int switchX = x + leftWidth - 7 - SWITCH_WIDTH;
				if (mouseX >= switchX - 4) {
					group.enabled(!group.enabled());
					UiSounds.toggle(group.enabled());
					feature.store().changed(group);
				} else if (group != feature.selectedGroup()) {
					feature.select(group);
					map.resetZoom();
					list.unfold(false);
					list.highlight(-1);
					UiSounds.click();
				}
				return true;
			}
		}
		// the place key's field in the quick add list
		if (placeField != null && placeField.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (mouseY >= newGroupY && mouseY < newGroupY + NEW_GROUP_HEIGHT) {
			WaypointGroup group = feature.newGroup();
			map.resetZoom();
			list.highlight(-1);
			UiSounds.click();
			edit.start(group.id(), group.name(), WaypointGroup.MAX_NAME, name -> rename(group, name));
		}
		return true;
	}

	/** @return the left edge of the zoom buttons, the same where they are drawn and where they are clicked: 4 inside the map's inner box */
	private int zoomX() {
		return mapX + mapWidth - ZOOM_BUTTON - 10;
	}

	private boolean clickMap(WaypointGroup group, double mouseX, double mouseY) {
		if (!over(mouseX, mouseY, mapX, mapY, mapWidth, mapHeight)) {
			return false;
		}
		int zoomX = zoomX();
		if (over(mouseX, mouseY, zoomX, zoomInY(), ZOOM_BUTTON, ZOOM_BUTTON)) {
			map.zoomIn();
		} else if (over(mouseX, mouseY, zoomX, zoomOutY(), ZOOM_BUTTON, ZOOM_BUTTON)) {
			map.zoomOut();
		} else {
			int point = map.pointAt(mouseX, mouseY);
			if (point >= 0) {
				list.highlight(point);
				openPointMenu(group, point, (int) mouseX + 1, (int) mouseY + 4);
			}
		}
		return true;
	}

	private boolean clickList(WaypointGroup group, double mouseX, double mouseY) {
		switch (list.partAt(mouseX, mouseY)) {
			case MORE -> {
				list.toggleUnfolded();
				UiSounds.click();
			}
			case MENU -> {
				int index = list.rowAt(mouseX, mouseY);
				int[] anchor = list.menuAnchor(index);
				list.highlight(index);
				openPointMenu(group, index, anchor[0], anchor[1]);
			}
			case ROW -> {
				int index = list.rowAt(mouseX, mouseY);
				list.highlight(index);
				list.press(index, mouseY);
			}
			case NONE -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		WaypointGroup group = laidOut;
		return group != null && list.drag(group, mouseY);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		WaypointGroup group = laidOut;
		if (group != null && list.release(group)) {
			list.highlight(-1);
			feature.store().changed(group);
		}
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (placeField != null && placeField.listening()) {
			return placeField.keyPressed(key, modifiers);
		}
		if (edit.keyPressed(key, modifiers)) {
			return true;
		}
		if (searching) {
			if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
			} else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
				search = "";
				searching = false;
			} else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
				searching = false;
			}
			return true;
		}
		// Ctrl+V on the page: a list or coordinates go to the import
		if ((modifiers & ScreenInput.CONTROL) != 0 && ScreenInput.letter(key, 'V')) {
			if (feature.importOpen()) {
				importView.paste();
			} else {
				feature.openImport(Minecraft.getInstance().keyboardHandler.getClipboard());
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!edit.active() && searching) {
			if (codepoint >= 32 && search.length() < WaypointGroup.MAX_NAME) {
				search += new String(Character.toChars(codepoint));
			}
			return true;
		}
		return edit.charTyped(codepoint);
	}

	@Override
	public void loseFocus() {
		edit.cancel();
		searching = false;
		menu.close();
		if (placeField != null) {
			placeField.loseFocus();
		}
	}

	// ------------------------------------------------------------------------------------------------------ actions

	private void rename(WaypointGroup group, String name) {
		group.rename(name);
		feature.store().changed(group);
	}

	/** Adds a point where the player stands, named "Waypoint N". */
	private void addPointHere(WaypointGroup group) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Waypoint point = new Waypoint(player.getBlockX(), player.getBlockY(), player.getBlockZ(), "Waypoint " + (group.points().size() + 1));
		if (group.add(point)) {
			feature.store().changed(group);
			UiSounds.click();
			int index = group.points().size() - 1;
			list.highlight(index);
			edit.start(index, point.name(), Waypoint.MAX_NAME, name -> {
				point.rename(name);
				feature.store().changed(group);
			});
		}
	}

	private void openGroupMenu(WaypointGroup group, int anchorX, int anchorY) {
		List<WaypointMenu.Item> items = new ArrayList<>();
		if (!group.readOnly()) {
			items.add(WaypointMenu.Item.of("Rename", () -> {
				feature.select(group);
				edit.start(group.id() + "#title", group.name(), WaypointGroup.MAX_NAME, name -> rename(group, name));
			}));
		}
		items.add(WaypointMenu.Item.of("Duplicate", () -> feature.select(feature.duplicate(group))));
		if (!group.readOnly()) {
			items.add(WaypointMenu.Item.danger("Delete group", () -> {
				feature.store().remove(group);
				feature.select(null);
			}));
		}
		menu.open(anchorX, anchorY, items);
	}

	private void openPointMenu(WaypointGroup group, int index, int anchorX, int anchorY) {
		Waypoint point = group.points().get(index);
		List<WaypointMenu.Item> items = new ArrayList<>();
		if (!group.readOnly()) {
			items.add(WaypointMenu.Item.of("Rename", () -> edit.start(index, point.name(), Waypoint.MAX_NAME, name -> {
				point.rename(name);
				feature.store().changed(group);
			})));
			items.add(WaypointMenu.Item.of("Color  ›", () -> menu.replace(colorItems(group, point))));
			List<WaypointMenu.Item> targets = moveTargets(group, point);
			if (!targets.isEmpty()) {
				items.add(WaypointMenu.Item.of("Move to  ›", () -> menu.replace(targets)));
			}
		}
		if (group.show() == WaypointGroup.Show.NEXT && index != group.next()) {
			items.add(WaypointMenu.Item.of("Make it the next", () -> {
				group.next(index);
				group.progress().hold();
				feature.store().changed(group);
			}));
		}
		items.add(WaypointMenu.Item.of(point.hidden() ? "Show" : "Hide", () -> {
			point.hidden(!point.hidden());
			feature.store().changed(group);
		}));
		items.add(WaypointMenu.Item.of("Copy coords", () -> Minecraft.getInstance().keyboardHandler.setClipboard(point.x() + " " + point.y() + " " + point.z())));
		if (!group.readOnly()) {
			items.add(WaypointMenu.Item.danger("Delete", () -> {
				group.remove(index);
				list.highlight(-1);
				feature.store().changed(group);
			}));
		}
		menu.open(anchorX, anchorY, items);
	}

	private List<WaypointMenu.Item> colorItems(WaypointGroup group, Waypoint point) {
		List<WaypointMenu.Item> items = new ArrayList<>();
		items.add(WaypointMenu.Item.dot("Group color", group.rgb(), () -> {
			point.rgb(0);
			feature.store().changed(group);
		}));
		String[] names = {"Blue", "Purple", "Green", "Yellow", "Orange", "Red"};
		for (int i = 0; i < WaypointColors.count(); i++) {
			int rgb = WaypointColors.get(i);
			items.add(WaypointMenu.Item.dot(names[i], rgb, () -> {
				point.rgb(rgb == group.rgb() ? 0 : rgb);
				feature.store().changed(group);
			}));
		}
		return items;
	}

	private List<WaypointMenu.Item> moveTargets(WaypointGroup from, Waypoint point) {
		List<WaypointMenu.Item> items = new ArrayList<>();
		for (WaypointGroup target : groups()) {
			if (target != from && !target.readOnly()) {
				items.add(WaypointMenu.Item.dot(target.name(), target.rgb(), () -> {
					int index = from.points().indexOf(point);
					if (index >= 0 && target.add(point)) {
						from.remove(index);
						list.highlight(-1);
						feature.store().changed(from);
						feature.store().changed(target);
					}
				}));
			}
		}
		return items;
	}

	private void openAreaMenu(WaypointGroup group, int anchorX, int anchorY) {
		if (group.readOnly()) {
			return;
		}
		List<WaypointMenu.Item> items = new ArrayList<>();
		for (WaypointAreas.Area area : WaypointAreas.choices()) {
			if (!group.areas().contains(area.key())) {
				items.add(WaypointMenu.Item.of(area.name(), () -> {
					group.areas().add(area.key());
					feature.areasChanged(group);
				}));
			}
		}
		if (!items.isEmpty()) {
			menu.open(anchorX, anchorY, items);
		}
	}

	private static boolean over(double mouseX, double mouseY, int left, int top, int width, int height) {
		return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
	}
}
