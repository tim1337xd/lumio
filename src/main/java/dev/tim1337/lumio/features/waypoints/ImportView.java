package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;
import dev.tim1337.lumio.ui.controls.SwitchControl;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Import and export on the Waypoints page. Import: a list pasted from the clipboard (or read from a file in the config folder) is recognized on its
 * own, previewed, and goes into a new or an existing group, with its area guessed from the points and a route shown as one. Export: groups as
 * Lumio's, Skyblocker's or SkyHanni's text, to the clipboard or a file in the config folder. Nothing is sent anywhere.
 */
final class ImportView {
	static final String EXPORTS = "exports";
	private static final int MAX_FILES = 20;
	private static final Pattern NOT_IN_FILE_NAME = Pattern.compile("[^a-z0-9_-]+");
	private static final int COLUMN_GAP = 14;
	private static final float LEFT_SHARE = 0.62f;
	/** Narrower than this, export goes under import instead of beside it. */
	private static final int SIDE_BY_SIDE = 520;
	private static final int TITLE_ROOM = 22;
	private static final int BOX_HEIGHT = 64;
	private static final int ROW = 26;
	private static final int ROW_WITH_HINT = 30;
	private static final int PAD = 9;
	private static final int SEGMENT_HEIGHT = 18;
	private static final int BUTTON_HEIGHT = 22;
	private static final int PREVIEW_ROWS = 4;
	private static final int BOX_LINES = 3;
	/** The most groups offered in Into; those that do not fit are left out. */
	private static final int MOST_TARGETS = 6;
	private static final int MOST_LINE_LETTERS = 200;
	private static final int PREVIEW_ROW = 18;
	private static final int CHIP_HEIGHT = 14;
	private static final String[] READS = {"Lumio", "Skyblocker", "SkyHanni", "ColeWeight", "Skytils"};
	private static final String NOTHING_SENT = "Nothing is sent anywhere: import and export only use your clipboard or a file.";
	private static final String EXPORT_NOTE = "Lumio keeps names, colors, order, areas and the Show mode. Skyblocker and SkyHanni keep what their own "
		+ "format can hold.";

	private final WaypointsFeature feature;
	private final WaypointMenu menu;
	private final InlineEdit edit;
	private String text = "";
	private boolean fromFile;
	private WaypointImport.Result result;
	/** Into: -1 for a new group, else a group's index among those points can be added to. */
	private int into = -1;
	/** How many groups there was room for in Into. */
	private int shownTargets;
	private String groupName = "";
	private String area = "";
	private WaypointGroup.Show show = WaypointGroup.Show.ALL;
	private String exportGroup = "";
	private WaypointExport.Format exportFormat = WaypointExport.Format.LUMIO;
	private boolean exportAreas = true;
	private float areasSwitch = 1f;
	// the layout of the last frame, for clicks
	private int x;
	private int y;
	private int width;
	private int leftWidth;
	private int rightX;
	private int rightWidth;
	private int boxY;
	private int previewY;
	private int previewHeight;
	private int intoY;
	private int footerY;
	private int exportY;
	private int exportTitleY;
	private final Segments sourceSegments = new Segments();
	private final Segments intoSegments = new Segments();
	private final Segments showSegments = new Segments();
	private final Segments formatSegments = new Segments();
	private int nameX;
	private int nameWidth;
	private int areaX;
	private int areaWidth;
	private int importX;
	private int importWidth;
	private int cancelX;
	private int cancelWidth;
	private int groupsX;
	private int copyY;
	private int saveY;
	private int switchX;

	ImportView(WaypointsFeature feature, WaypointMenu menu, InlineEdit edit) {
		this.feature = feature;
		this.menu = menu;
		this.edit = edit;
	}

	/** Takes a text to import, as pasted or read from a file, and works out what it is. */
	void take(String text, boolean fromFile) {
		this.text = text == null ? "" : text;
		this.fromFile = fromFile;
		result = WaypointImport.read(this.text);
		if (result == null) {
			return;
		}
		WaypointImport.Read first = result.groups().getFirst();
		into = -1;
		groupName = uniqueName(first.group().name());
		show = first.group().show();
		area = guessArea(first);
	}

	/** @return the area a list is for: the island it names, a place its points are at, the place the player is at, or this server or world */
	private static String guessArea(WaypointImport.Read read) {
		if (!read.island().isEmpty()) {
			String place = WaypointAreas.placeOfIsland(read.island());
			if (place != null) {
				return place;
			}
		}
		if (!read.group().points().isEmpty()) {
			Waypoint point = read.group().points().getFirst();
			String place = WaypointAreas.placeAt(point.x(), point.y(), point.z());
			if (!place.isEmpty()) {
				return place;
			}
		}
		return WaypointAreas.forNewGroup().getFirst();
	}

	private String uniqueName(String name) {
		String candidate = name;
		for (int n = 2; nameTaken(candidate) && n < 100; n++) {
			candidate = name + " (" + n + ")";
		}
		return candidate;
	}

	private boolean nameTaken(String name) {
		for (WaypointGroup group : feature.store().groups()) {
			if (group.name().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	private List<WaypointGroup> targets() {
		List<WaypointGroup> targets = new ArrayList<>();
		for (WaypointGroup group : feature.store().groups()) {
			if (!group.readOnly() && targets.size() < MOST_TARGETS) {
				targets.add(group);
			}
		}
		return targets;
	}

	// ------------------------------------------------------------------------------------------------------ layout and drawing

	int measure(Ui ui, int x, int y, int width) {
		this.x = x;
		this.y = y;
		this.width = width;
		boolean beside = width >= SIDE_BY_SIDE;
		leftWidth = beside ? Math.round((width - COLUMN_GAP) * LEFT_SHARE) : width;
		rightX = beside ? x + leftWidth + COLUMN_GAP : x;
		rightWidth = beside ? width - leftWidth - COLUMN_GAP : width;
		boxY = y + 22;
		int readsY = boxY + BOX_HEIGHT + 6;
		previewY = readsY + 20;
		int count = result == null ? 0 : result.points();
		previewHeight = result == null ? 0 : Math.min(PREVIEW_ROWS, count) * PREVIEW_ROW + (count > PREVIEW_ROWS ? PREVIEW_ROW : 0) + 2 * 6;
		intoY = previewY + (previewHeight > 0 ? previewHeight + 8 : 0);
		int intoHeight = result == null ? 0 : ROW + ROW + ROW_WITH_HINT + ROW_WITH_HINT;
		footerY = intoY + (intoHeight > 0 ? intoHeight + 10 : 0);
		int importBottom = result == null ? previewY : footerY + BUTTON_HEIGHT;
		exportTitleY = beside ? y : importBottom + 18;
		exportY = exportTitleY + TITLE_ROOM;
		int exportBottom = exportY + 3 * ROW + 8 + 2 * (BUTTON_HEIGHT + 6) + 40;
		return Math.max(importBottom + 4, exportBottom) - y;
	}

	void render(Ui ui, int mouseX, int mouseY) {
		renderImport(ui, mouseX, mouseY);
		renderExport(ui, mouseX, mouseY);
	}

	private void renderImport(Ui ui, int mouseX, int mouseY) {
		ui.textAtScaledBold("Import", x, y + 2, TextScale.HEADING, ui.palette.text());
		sourceSegments.draw(ui, x + leftWidth, y + 1, new String[] {"Paste", "File"}, fromFile ? 1 : 0, mouseX, mouseY, true);
		// the box with the text, and what it was read as
		boolean overBox = over(mouseX, mouseY, x, boxY, leftWidth, BOX_HEIGHT);
		ui.rounded(x, boxY, leftWidth, BOX_HEIGHT, Radius.LG, 0x40000000);
		ui.ring(x, boxY, leftWidth, BOX_HEIGHT, Radius.LG, 1, result != null ? ui.accent : Ui.mix(ui.palette.cardBorder(), ui.accent, overBox ? 0.6f : 0f));
		if (text.isEmpty()) {
			ui.textMiddle("Click or press Ctrl+V to paste a list", x + 10, boxY + BOX_HEIGHT / 2, ui.palette.textSecondary());
		} else {
			// wrapped by letters: a pasted list has no spaces to wrap at
			int from = 0;
			for (int line = 0; line < BOX_LINES && from < text.length(); line++) {
				int to = fitting(ui, text, from, leftWidth - 20);
				String shown = text.substring(from, to);
				if (line == BOX_LINES - 1 && to < text.length()) {
					shown = ui.fitScaled(shown + "…", leftWidth - 20, TextScale.SMALL);
				}
				ui.textAtScaled(shown.replace('\n', ' '), x + 10, boxY + 8 + line * 10, TextScale.SMALL, Ui.withOpacity(ui.palette.text(), 0.8f));
				from = to;
			}
			String status = result == null ? "Not a list Lumio can read" : "Detected: " + result.format().label();
			int statusWidth = Math.round(ui.boldWidthScaled(status, TextScale.SMALL)) + 22;
			int statusX = x + leftWidth - statusWidth - 8;
			int statusY = boxY + BOX_HEIGHT - CHIP_HEIGHT - 8;
			int color = result == null ? SystemColors.ORANGE : SystemColors.GREEN;
			ui.rounded(statusX, statusY, statusWidth, CHIP_HEIGHT, Radius.XS, Ui.withOpacity(color, 0.16f));
			ui.ring(statusX, statusY, statusWidth, CHIP_HEIGHT, Radius.XS, 1, Ui.withOpacity(color, 0.4f));
			ui.icon(result == null ? "info" : "check", statusX + 6, statusY + 3, 8, color);
			ui.textAtScaledBold(status, statusX + 17, statusY + CHIP_HEIGHT / 2f - 3.5f, TextScale.SMALL, color);
		}
		// the formats it reads, the found one marked
		int readsY = boxY + BOX_HEIGHT + 6;
		int chipX = x;
		ui.textAtScaled("Reads", chipX, readsY + 3.5f, TextScale.SMALL, ui.palette.textSecondary());
		chipX += Math.round(ui.widthScaled("Reads", TextScale.SMALL)) + 6;
		for (String format : READS) {
			int w = Math.round(ui.widthScaled(format, TextScale.SMALL)) + 12;
			boolean found = result != null && result.format().label().startsWith(format);
			ui.rounded(chipX, readsY, w, CHIP_HEIGHT, Radius.XS, found ? Ui.mix(ui.palette.control(), 0xFFFFFFFF, 0.12f) : 0);
			ui.ring(chipX, readsY, w, CHIP_HEIGHT, Radius.XS, 1, found ? Ui.withOpacity(ui.palette.text(), 0.35f) : Ui.withOpacity(ui.palette.textSecondary(), 0.2f));
			ui.textAtScaled(format, chipX + 6, readsY + 3.5f, TextScale.SMALL, found ? ui.palette.text() : ui.palette.textSecondary());
			chipX += w + 4;
		}
		ui.textAtScaled("· detected on its own", chipX + 2, readsY + 3.5f, TextScale.SMALL, ui.palette.textSecondary());
		if (result == null) {
			return;
		}
		renderPreview(ui);
		renderInto(ui, mouseX, mouseY);
		renderFooter(ui, mouseX, mouseY);
	}

	private void renderPreview(Ui ui) {
		ui.rounded(x, previewY, leftWidth, previewHeight, Radius.LG, ui.palette.card());
		ui.ring(x, previewY, leftWidth, previewHeight, Radius.LG, 1, ui.palette.cardBorder());
		int shown = 0;
		int total = result.points();
		outer:
		for (WaypointImport.Read read : result.groups()) {
			WaypointGroup group = read.group();
			for (int i = 0; i < group.points().size(); i++) {
				if (shown >= PREVIEW_ROWS) {
					break outer;
				}
				Waypoint point = group.points().get(i);
				int middle = previewY + 6 + shown * PREVIEW_ROW + PREVIEW_ROW / 2;
				int color = 0xFF000000 | group.rgbOf(point);
				ui.discAt(x + 16, middle, 11, color);
				String number = String.valueOf(i + 1);
				ui.textAtScaledBold(number, x + 16 - ui.boldWidthScaled(number, TextScale.SMALL) / 2f, middle - 3.5f, TextScale.SMALL,
					Ui.luminance(color) > 0.6f ? 0xFF1C1C1E : 0xFFFFFFFF);
				String coords = point.coords();
				float coordsWidth = ui.widthScaled(coords, TextScale.SMALL);
				ui.textAtScaled(coords, x + leftWidth - 10 - coordsWidth, middle - 3.5f, TextScale.SMALL, ui.palette.textSecondary());
				String name = point.name().isEmpty() ? "Point " + (i + 1) : point.name();
				ui.textMiddle(ui.fit(name, Math.round(leftWidth - 50 - coordsWidth)), x + 28, middle, point.hidden() ? ui.palette.textSecondary()
					: ui.palette.text());
				shown++;
			}
		}
		if (total > PREVIEW_ROWS) {
			ui.textAtScaled("… and " + (total - PREVIEW_ROWS) + " more", x + 10, previewY + 6 + PREVIEW_ROWS * PREVIEW_ROW + 5, TextScale.SMALL,
				ui.palette.textSecondary());
		}
	}

	private void renderInto(Ui ui, int mouseX, int mouseY) {
		int height = ROW + ROW + ROW_WITH_HINT + ROW_WITH_HINT;
		ui.rounded(x, intoY, leftWidth, height, Radius.LG, ui.palette.card());
		ui.ring(x, intoY, leftWidth, height, Radius.LG, 1, ui.palette.cardBorder());
		int right = x + leftWidth - PAD;
		int rowY = intoY;
		// into: a new group or one there is
		ui.textMiddle("Into", x + PAD, rowY + ROW / 2, ui.palette.text());
		List<WaypointGroup> targets = targets();
		// as many groups as fit beside the name
		int room = leftWidth - 2 * PAD - ui.width("Into") - 12;
		int used = Math.round(ui.boldWidthScaled("New group", TextScale.SMALL)) + 18;
		int fit = 0;
		while (fit < targets.size() && used + Math.round(ui.boldWidthScaled(targets.get(fit).name(), TextScale.SMALL)) + 14 <= room) {
			used += Math.round(ui.boldWidthScaled(targets.get(fit).name(), TextScale.SMALL)) + 14;
			fit++;
		}
		shownTargets = fit;
		if (into >= fit) {
			into = -1;
		}
		String[] labels = new String[fit + 1];
		labels[0] = "New group";
		for (int i = 0; i < fit; i++) {
			labels[i + 1] = targets.get(i).name();
		}
		intoSegments.draw(ui, right, rowY + (ROW - SEGMENT_HEIGHT) / 2, labels, into + 1, mouseX, mouseY, true);
		rowY += ROW;
		line(ui, rowY);
		// the new group's name, or the group points go into
		ui.textMiddle(into < 0 ? "Group name" : "Adds to", x + PAD, rowY + ROW / 2, ui.palette.text());
		String name = into < 0 ? groupName : targets.get(Math.min(into, targets.size() - 1)).name();
		nameWidth = Math.min(leftWidth / 2, ui.width(name) + 20);
		nameX = right - nameWidth;
		if (edit.editing("import-name")) {
			edit.draw(ui, nameX + 6, rowY + 5, nameWidth - 6, ROW - 10);
		} else {
			ui.rounded(nameX, rowY + 5, nameWidth, ROW - 10, Radius.SM, ui.palette.control());
			ui.textMiddle(ui.fit(name, nameWidth - 12), nameX + 10, rowY + ROW / 2, ui.palette.text());
		}
		rowY += ROW;
		line(ui, rowY);
		// where it shows
		hinted(ui, rowY, "Shows on", "Guessed from the points; change any time");
		String areaName = WaypointAreas.nameOf(area);
		areaWidth = Math.round(ui.widthScaled(areaName, TextScale.SMALL)) + 14;
		areaX = right - areaWidth;
		boolean overArea = over(mouseX, mouseY, areaX, rowY + 8, areaWidth, CHIP_HEIGHT);
		ui.rounded(areaX, rowY + 8, areaWidth, CHIP_HEIGHT, Radius.XS, Ui.mix(ui.palette.control(), 0xFFFFFFFF, overArea ? 0.08f : 0f));
		ui.ring(areaX, rowY + 8, areaWidth, CHIP_HEIGHT, Radius.XS, 1, ui.palette.controlBorder());
		ui.textAtScaled(areaName, areaX + 7, rowY + 8 + CHIP_HEIGHT / 2f - 3.5f, TextScale.SMALL, ui.palette.text());
		rowY += ROW_WITH_HINT;
		line(ui, rowY);
		// how it shows
		boolean numbered = result.groups().getFirst().group().show() == WaypointGroup.Show.NEXT;
		hinted(ui, rowY, "Show", numbered ? "Numbered points look like a route" : "Every point at once");
		String[] shows = new String[WaypointGroup.Show.values().length];
		for (WaypointGroup.Show each : WaypointGroup.Show.values()) {
			shows[each.ordinal()] = each.label();
		}
		showSegments.draw(ui, right, rowY + (ROW_WITH_HINT - SEGMENT_HEIGHT) / 2, shows, show.ordinal(), mouseX, mouseY, true);
	}

	private void renderFooter(Ui ui, int mouseX, int mouseY) {
		String label = "Import " + result.points() + (result.points() == 1 ? " point" : " points");
		importWidth = Math.round(ui.boldWidthScaled(label, TextScale.BODY)) + 28;
		importX = x + leftWidth - importWidth;
		cancelWidth = ui.width("Cancel") + 20;
		cancelX = importX - 6 - cancelWidth;
		ui.textAtScaled(ui.fitScaled(NOTHING_SENT, cancelX - x - 8, TextScale.SMALL), x, footerY + BUTTON_HEIGHT / 2f - 3.5f, TextScale.SMALL,
			ui.palette.textSecondary());
		button(ui, cancelX, footerY, cancelWidth, "Cancel", false, mouseX, mouseY);
		button(ui, importX, footerY, importWidth, label, true, mouseX, mouseY);
	}

	private void renderExport(Ui ui, int mouseX, int mouseY) {
		ui.textAtScaledBold("Export", rightX, exportTitleY + 2, TextScale.HEADING, ui.palette.text());
		int cardHeight = 3 * ROW;
		ui.rounded(rightX, exportY, rightWidth, cardHeight, Radius.LG, ui.palette.card());
		ui.ring(rightX, exportY, rightWidth, cardHeight, Radius.LG, 1, ui.palette.cardBorder());
		int right = rightX + rightWidth - PAD;
		int rowY = exportY;
		ui.textMiddle("Groups", rightX + PAD, rowY + ROW / 2, ui.palette.text());
		String groups = exportLabel();
		int groupsWidth = Math.min(rightWidth / 2, ui.width(groups) + 6);
		groupsX = right - groupsWidth;
		ui.textMiddle(ui.fit(groups, groupsWidth), groupsX, rowY + ROW / 2, over(mouseX, mouseY, groupsX, rowY, groupsWidth, ROW) ? ui.palette.text()
			: ui.palette.textSecondary());
		rowY += ROW;
		line(ui, rowY, rightX, rightWidth);
		ui.textMiddle("Format", rightX + PAD, rowY + ROW / 2, ui.palette.text());
		String[] formats = new String[WaypointExport.Format.values().length];
		for (WaypointExport.Format format : WaypointExport.Format.values()) {
			formats[format.ordinal()] = format.label();
		}
		formatSegments.draw(ui, right, rowY + (ROW - SEGMENT_HEIGHT) / 2, formats, exportFormat.ordinal(), mouseX, mouseY, true);
		rowY += ROW;
		line(ui, rowY, rightX, rightWidth);
		ui.textMiddle("Include areas", rightX + PAD, rowY + ROW / 2, ui.palette.text());
		switchX = right - SwitchControl.TRACK_WIDTH;
		areasSwitch = ui.approach(areasSwitch, exportAreas ? 1f : 0f, Ui.MOVE_RATE);
		SwitchControl.draw(ui, switchX, rowY + (ROW - SwitchControl.TRACK_HEIGHT) / 2, areasSwitch, 0f, 0f);
		copyY = exportY + cardHeight + 8;
		saveY = copyY + BUTTON_HEIGHT + 6;
		button(ui, rightX, copyY, rightWidth, "Copy to clipboard", true, mouseX, mouseY);
		button(ui, rightX, saveY, rightWidth, "Save as file…", false, mouseX, mouseY);
		List<String> note = ui.wrap(EXPORT_NOTE, rightWidth, 4);
		for (int i = 0; i < note.size(); i++) {
			ui.textAtScaled(note.get(i), rightX, saveY + BUTTON_HEIGHT + 10 + i * 10, TextScale.SMALL, ui.palette.textSecondary());
		}
	}

	/** @return where a line of small text that starts at a letter ends, so it fits a width */
	private static int fitting(Ui ui, String text, int from, int width) {
		int to = Math.min(text.length(), from + MOST_LINE_LETTERS);
		while (to > from + 1 && ui.widthScaled(text.substring(from, to), TextScale.SMALL) > width) {
			to = from + (to - from) * 9 / 10;
		}
		while (to < text.length() && to < from + MOST_LINE_LETTERS && ui.widthScaled(text.substring(from, to + 1), TextScale.SMALL) <= width) {
			to++;
		}
		return Math.max(from + 1, to);
	}

	private String exportLabel() {
		if (exportGroup.isEmpty()) {
			return "All groups";
		}
		WaypointGroup group = feature.store().find(exportGroup);
		return group == null ? "All groups" : group.name();
	}

	private void line(Ui ui, int rowY) {
		line(ui, rowY, x, leftWidth);
	}

	private static void line(Ui ui, int rowY, int left, int width) {
		ui.fill(left + PAD, rowY, width - 2 * PAD, 1, ui.palette.cardBorder());
	}

	private void hinted(Ui ui, int rowY, String name, String hint) {
		ui.textAtScaled(name, x + PAD, rowY + 6, TextScale.BODY, ui.palette.text());
		ui.textAtScaled(hint, x + PAD, rowY + 17, TextScale.SMALL, ui.palette.textSecondary());
	}

	private static void button(Ui ui, int left, int top, int width, String text, boolean primary, int mouseX, int mouseY) {
		boolean over = over(mouseX, mouseY, left, top, width, BUTTON_HEIGHT);
		if (primary) {
			ui.rounded(left, top, width, BUTTON_HEIGHT, Radius.SM, Ui.mix(ui.accent, 0xFFFFFFFF, over ? 0.14f : 0f));
			ui.textCenteredBold(text, left + width / 2f, top + BUTTON_HEIGHT / 2, 0xFFFFFFFF);
		} else {
			ui.rounded(left, top, width, BUTTON_HEIGHT, Radius.SM, Ui.withOpacity(ui.palette.hover(), over ? 1f : 0.5f));
			ui.ring(left, top, width, BUTTON_HEIGHT, Radius.SM, 1, Ui.withOpacity(ui.palette.text(), 0.16f));
			ui.textCentered(text, left + width / 2, top + BUTTON_HEIGHT / 2, ui.palette.text());
		}
	}

	// ------------------------------------------------------------------------------------------------------ input

	/** @return whether the click was the view's; closes it after an import or Cancel */
	boolean click(double mouseX, double mouseY) {
		int source = sourceSegments.at(mouseX, mouseY);
		if (source == 0 || over(mouseX, mouseY, x, boxY, leftWidth, BOX_HEIGHT)) {
			paste();
			return true;
		}
		if (source == 1) {
			openFiles();
			return true;
		}
		if (result != null && clickImport(mouseX, mouseY)) {
			return true;
		}
		return clickExport(mouseX, mouseY);
	}

	/** Takes the clipboard. */
	void paste() {
		take(Minecraft.getInstance().keyboardHandler.getClipboard(), false);
		UiSounds.click();
	}

	private boolean clickImport(double mouseX, double mouseY) {
		int target = intoSegments.at(mouseX, mouseY);
		if (target >= 0) {
			into = target - 1;
			UiSounds.tick(0.5f);
			return true;
		}
		int chosenShow = showSegments.at(mouseX, mouseY);
		if (chosenShow >= 0) {
			show = WaypointGroup.Show.values()[chosenShow];
			UiSounds.tick(0.5f);
			return true;
		}
		if (into < 0 && over(mouseX, mouseY, nameX, intoY + ROW + 5, nameWidth, ROW - 10)) {
			edit.start("import-name", groupName, WaypointGroup.MAX_NAME, name -> groupName = name);
			return true;
		}
		if (over(mouseX, mouseY, areaX, intoY + 2 * ROW + 8, areaWidth, CHIP_HEIGHT)) {
			List<WaypointMenu.Item> items = new ArrayList<>();
			for (WaypointAreas.Area choice : WaypointAreas.choices()) {
				items.add(WaypointMenu.Item.of(choice.name(), () -> area = choice.key()));
			}
			menu.open(areaX + areaWidth, intoY + 2 * ROW + 8 + CHIP_HEIGHT + 2, items);
			return true;
		}
		if (over(mouseX, mouseY, cancelX, footerY, cancelWidth, BUTTON_HEIGHT)) {
			result = null;
			text = "";
			feature.closeImport();
			UiSounds.click();
			return true;
		}
		if (over(mouseX, mouseY, importX, footerY, importWidth, BUTTON_HEIGHT)) {
			importNow();
			return true;
		}
		return false;
	}

	private void importNow() {
		List<WaypointGroup> targets = targets();
		WaypointGroup existing = into >= 0 && into < targets.size() ? targets.get(into) : null;
		int added = 0;
		WaypointGroup shown = null;
		for (int i = 0; i < result.groups().size(); i++) {
			WaypointImport.Read read = result.groups().get(i);
			WaypointGroup group = existing;
			if (group == null) {
				group = feature.importGroup(i == 0 ? groupName : uniqueName(read.group().name()), read.group().rgb(), i == 0 ? area : guessArea(read),
					show, "imported from " + result.format().label().replace(" (ColeWeight list)", ""));
			}
			for (Waypoint point : read.group().points()) {
				Waypoint copy = point.copy();
				// added to a group there is, a point keeps the color it had in its list
				if (copy.rgb() == 0 && group == existing && read.group().rgb() != group.rgb()) {
					copy.rgb(read.group().rgb());
				}
				if (group.add(copy)) {
					added++;
				}
			}
			feature.store().changed(group);
			shown = shown == null ? group : shown;
		}
		feature.select(shown);
		Toasts.info("Waypoints imported", added + (added == 1 ? " point" : " points") + " from " + result.format().label() + ".");
		result = null;
		text = "";
		feature.closeImport();
		UiSounds.click();
	}

	private boolean clickExport(double mouseX, double mouseY) {
		int format = formatSegments.at(mouseX, mouseY);
		if (format >= 0) {
			exportFormat = WaypointExport.Format.values()[format];
			UiSounds.tick(0.5f);
			return true;
		}
		if (over(mouseX, mouseY, groupsX, exportY, rightX + rightWidth - groupsX, ROW)) {
			List<WaypointMenu.Item> items = new ArrayList<>();
			items.add(WaypointMenu.Item.of("All groups", () -> exportGroup = ""));
			for (WaypointGroup group : feature.store().groups()) {
				items.add(WaypointMenu.Item.dot(group.name(), group.rgb(), () -> exportGroup = group.id()));
			}
			menu.open(rightX + rightWidth - PAD, exportY + ROW, items);
			return true;
		}
		if (over(mouseX, mouseY, switchX - 4, exportY + 2 * ROW, SwitchControl.TRACK_WIDTH + 8, ROW)) {
			exportAreas = !exportAreas;
			UiSounds.toggle(exportAreas);
			return true;
		}
		if (over(mouseX, mouseY, rightX, copyY, rightWidth, BUTTON_HEIGHT)) {
			String exported = exportText();
			if (exported != null) {
				Minecraft.getInstance().keyboardHandler.setClipboard(exported);
				Toasts.info("Waypoints copied", exportFormat.label() + " text of " + exportLabel() + ", ready to paste.");
				UiSounds.click();
			}
			return true;
		}
		if (over(mouseX, mouseY, rightX, saveY, rightWidth, BUTTON_HEIGHT)) {
			saveFile();
			return true;
		}
		return false;
	}

	private List<WaypointGroup> exportGroups() {
		WaypointGroup one = exportGroup.isEmpty() ? null : feature.store().find(exportGroup);
		return one != null ? List.of(one) : List.copyOf(feature.store().groups());
	}

	private String exportText() {
		List<WaypointGroup> groups = exportGroups();
		if (groups.isEmpty()) {
			Toasts.info("Nothing to export", "There are no waypoints yet.");
			return null;
		}
		return WaypointExport.write(groups, exportFormat, exportAreas, WaypointAreas::islandOf);
	}

	/** Writes the export to a file in the config folder, in the background, and says where. */
	private void saveFile() {
		String exported = exportText();
		if (exported == null) {
			return;
		}
		String name = NOT_IN_FILE_NAME.matcher(exportLabel().toLowerCase(Locale.ROOT)).replaceAll("-") + "-" + exportFormat.name().toLowerCase(Locale.ROOT)
			+ ".txt";
		String fileName = name.startsWith("-") ? "waypoints" + name : name;
		CompletableFuture.runAsync(() -> {
			try {
				ModFiles.writeAtomically(ModFiles.file(WaypointStore.FOLDER).resolve(EXPORTS).resolve(fileName), exported);
				Minecraft.getInstance().execute(() -> Toasts.info("Waypoints saved", "config/lumio/waypoints/exports/" + fileName));
			} catch (IOException e) {
				Logs.warn("Could not save the waypoints to {}", fileName, e);
				Minecraft.getInstance().execute(() -> Toasts.info("Could not save", "The file could not be written."));
			}
		});
		UiSounds.click();
	}

	/** Lists the text files in the exports folder, in the background, as a menu to read one from. */
	private void openFiles() {
		CompletableFuture.supplyAsync(() -> {
			Path folder = ModFiles.file(WaypointStore.FOLDER).resolve(EXPORTS);
			try {
				Files.createDirectories(folder);
				try (Stream<Path> files = Files.list(folder)) {
					return files.filter(file -> Files.isRegularFile(file) && (file.toString().endsWith(".txt") || file.toString().endsWith(".json")))
						.limit(MAX_FILES).toList();
				}
			} catch (IOException e) {
				return List.<Path>of();
			}
		}).thenAccept(files -> Minecraft.getInstance().execute(() -> {
			List<WaypointMenu.Item> items = new ArrayList<>();
			for (Path file : files) {
				items.add(WaypointMenu.Item.of(file.getFileName().toString(), () -> readFile(file)));
			}
			if (items.isEmpty()) {
				Toasts.info("No files yet", "Put a list into config/lumio/waypoints/exports to read it here.");
				return;
			}
			menu.open(x + leftWidth, y + 20, items);
		}));
	}

	private void readFile(Path file) {
		CompletableFuture.supplyAsync(() -> {
			try {
				if (Files.size(file) > WaypointImport.MAX_TEXT) {
					return null;
				}
				return Files.readString(file);
			} catch (IOException e) {
				return null;
			}
		}).thenAccept(read -> Minecraft.getInstance().execute(() -> {
			if (read == null) {
				Toasts.info("Could not read the file", file.getFileName().toString());
			} else {
				take(read, true);
			}
		}));
	}

	private static boolean over(double mouseX, double mouseY, int left, int top, int width, int height) {
		return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
	}

	/** Choices side by side in a dark bar, the chosen one on a lighter pill; drawn ending at a right edge (or from a left one). */
	static final class Segments {
		private int[] lefts = new int[0];
		private int[] widths = new int[0];
		private int top;
		private int count;

		void draw(Ui ui, int edge, int top, String[] labels, int selected, int mouseX, int mouseY, boolean fromRight) {
			if (lefts.length < labels.length) {
				lefts = new int[labels.length];
				widths = new int[labels.length];
			}
			count = labels.length;
			this.top = top;
			int total = 4;
			for (int i = 0; i < labels.length; i++) {
				widths[i] = Math.round(ui.boldWidthScaled(labels[i], TextScale.SMALL)) + 14;
				total += widths[i];
			}
			int left = fromRight ? edge - total : edge;
			ui.rounded(left, top, total, SEGMENT_HEIGHT, Radius.SM, ui.palette.control());
			ui.ring(left, top, total, SEGMENT_HEIGHT, Radius.SM, 1, ui.palette.controlBorder());
			int at = left + 2;
			for (int i = 0; i < labels.length; i++) {
				lefts[i] = at;
				boolean over = over(mouseX, mouseY, at, top, widths[i], SEGMENT_HEIGHT);
				if (i == selected) {
					ui.rounded(at, top + 2, widths[i], SEGMENT_HEIGHT - 4, Radius.XS, ui.palette.pill());
					ui.textAtScaledBold(labels[i], at + 7, top + SEGMENT_HEIGHT / 2f - 3.5f, TextScale.SMALL, ui.palette.onPill());
				} else {
					ui.textAtScaled(labels[i], at + 7, top + SEGMENT_HEIGHT / 2f - 3.5f, TextScale.SMALL, over ? ui.palette.text()
						: ui.palette.textSecondary());
				}
				at += widths[i];
			}
		}

		/** @return the choice under the mouse in the last frame, or -1 */
		int at(double mouseX, double mouseY) {
			for (int i = 0; i < count; i++) {
				if (over(mouseX, mouseY, lefts[i], top, widths[i], SEGMENT_HEIGHT)) {
					return i;
				}
			}
			return -1;
		}
	}
}
