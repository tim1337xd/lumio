package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.Place;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.CommandBindsSetting;
import dev.tim1337.lumio.features.misc.keybinds.CommandBind;
import dev.tim1337.lumio.features.misc.keybinds.CommandBinds;
import dev.tim1337.lumio.features.misc.keybinds.CommandKeybindsFeature;
import dev.tim1337.lumio.features.misc.keybinds.CustomIslands;
import dev.tim1337.lumio.features.misc.keybinds.KeybindPlaces;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The custom keybinds page: the list of keybinds in the order they were made (right-click for Duplicate and Delete, a switch each) with "New
 * keybind" and a short note on what a press does, and the editor of the selected one beside it, or below it on narrow windows. The editor records
 * the key (Esc cancels, Backspace clears), edits the command, and sets where the keybind works: everywhere, in SkyBlock or on
 * chosen islands, built-in ones and the player's own ("Add island", with the island the player is on a click away); whether only without a
 * screen; and Duplicate and Delete. Conflicts are explained in plain words: another keybind of the player's for the same key in the same place,
 * or a key the game also uses. Deleting a keybind or an island of the player's own can be undone from its notice.
 */
public class CommandBindsControl extends Control {
	private static final int ROW = 28;
	private static final int ROW_GAP = 2;
	private static final int LIST_PAD = 6;
	/** Room before a row's key caps. */
	private static final int CAPS_INSET = 8;
	private static final int CAP_HEIGHT = 16;
	private static final int CAP_PAD = 6;
	private static final int CAP_GAP = 12;
	private static final int MOST_CAPS_WIDTH = 120;
	private static final int TWO_COLUMNS = 560;
	private static final int COLUMN_GAP = 12;
	private static final int PAD = 12;
	private static final int BUTTON_HEIGHT = 22;
	private static final int SMALL_BUTTON_HEIGHT = 22;
	private static final int CAPTION = 14;
	private static final int FIELD_HEIGHT = 22;
	private static final int RECORDER_HEIGHT = 24;
	private static final int CHIP_HEIGHT = 14;
	private static final int CHIP_GAP = 6;
	/** A chip's text inset, the room of its check mark, and the room of the × on an island of the player's own. */
	private static final int CHIP_TEXT = 6;
	private static final int CHIP_CHECK = 10;
	private static final int CHIP_REMOVE = 12;
	private static final int SEGMENT_HEIGHT = 18;
	/** Room around the parts inside the control, and beside a part's text: less of it before the text gets too small. */
	private static final int SEGMENT_INSET = 2;
	private static final int SEGMENT_PADDING = 8;
	private static final int SEGMENT_TIGHT_PADDING = 4;
	private static final float SEGMENT_TIGHT_FROM_SCALE = 0.8f;
	private static final int LINE = 11;
	private static final int NOTE_LINE = 10;
	private static final int MENU_WIDTH = 96;
	private static final int MENU_ROW = 18;
	/** Pulses per second of the key recorder's ring while it listens. */
	private static final double PULSE_RATE = 2.5;
	private static final String[] MENU = {"Duplicate", "Delete"};
	private static final String NOTE = "One press sends one command, as if you typed it. Holding the key doesn't repeat it, and a short cooldown "
		+ "stops double sends.";
	private static final String ADD_ISLAND = "Add island";
	private static final String USE_CURRENT = "Use current: ";
	/** The built-in islands, the player's own, and the "Add island" chip. */
	private static final int MOST_ISLAND_CHIPS = CommandBind.MOST_PLACES + CustomIslands.MOST + 1;

	private final CommandBindsSetting setting;
	private final Runnable onChanged;
	private final TextSelection commandSelection = new TextSelection();
	private final TextSelection islandSelection = new TextSelection();
	private final Map<CommandBind, Spring> knobs = new IdentityHashMap<>();
	private CommandBind selected;
	private boolean recording;
	private boolean commandFocused;
	/** Whether the field for a new island is open, and what is typed in it. */
	private boolean addingIsland;
	private String islandDraft = "";

	/** The layout from the last {@link #measure}, relative to the control's top left. */
	private boolean twoColumns;
	private int listWidth;
	private int listHeight;
	private int buttonY;
	private int buttonWidth;
	private int noteY;
	private List<String> note = List.of();
	private int editorX;
	private int editorY;
	private int editorWidth;
	private int editorHeight;
	private int commandColumn;
	/** Parts of the editor, relative to its top: the key recorder, the conflict lines, the command, the places and the screen switch. */
	private int keyY;
	private int conflictY;
	private List<String> ownConflict = List.of();
	private List<String> gameConflict = List.of();
	private int commandY;
	private int whereY;
	private final int[][] segmentBoxes = new int[CommandBind.Where.values().length][3];
	private float segmentScale = TextScale.BODY;
	/** The island chips: the built-in islands, then the player's own (as {@link #customNames}), then "Add island". */
	private final int[][] islandBoxes = new int[MOST_ISLAND_CHIPS][3];
	private final int[] chipWidths = new int[MOST_ISLAND_CHIPS];
	private final List<String> customNames = new ArrayList<>();
	private int islandChips;
	/** The new island's field and the "Use current" button beside it, relative to the editor; the button's text, or null without one. */
	private int islandFieldY;
	private int islandFieldWidth;
	private String currentLabel;
	private String currentIsland;
	private int screenY;
	private int actionsY;
	private int duplicateWidth;
	private int deleteWidth;
	/** What the layout was made for, and the texts made with it. */
	private int measuredVersion = -1;
	private int measuredWidth = -1;
	private CommandBind measuredSelected;
	private boolean measuredAdding;
	private String measuredCurrent;
	private int measuredIslands = -1;
	private int measuredHeight;
	private final Map<CommandBind, String> whereTexts = new IdentityHashMap<>();

	/** The keybind whose right-click menu is open, and where the menu is, in window pixels. */
	private CommandBind menuFor;
	private int menuX;
	private int menuY;

	public CommandBindsControl(CommandBindsSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
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

	private List<CommandBind> binds() {
		return setting.binds();
	}

	private static CustomIslands customIslands() {
		return ModFeatures.COMMAND_KEYBINDS.customIslands();
	}

	/** Called before a keybind changes, so the change is the setting's. */
	private void changing() {
		setting.changing();
	}

	private void changed() {
		onChanged.run();
	}

	// ---- layout ----

	@Override
	protected int measure(Ui ui, int width) {
		if (selected != null && !binds().contains(selected)) {
			select(null);
		}
		String current = addingIsland ? CommandKeybindsFeature.currentIsland() : null;
		int islands = customIslands().names().size();
		// the page lays out every frame: the layout and its texts are made again only after a change
		if (setting.version() == measuredVersion && width == measuredWidth && selected == measuredSelected && addingIsland == measuredAdding
			&& islands == measuredIslands && (current == null ? measuredCurrent == null : current.equals(measuredCurrent))) {
			return measuredHeight;
		}
		measuredVersion = setting.version();
		measuredWidth = width;
		measuredSelected = selected;
		measuredAdding = addingIsland;
		measuredCurrent = current;
		measuredIslands = islands;
		whereTexts.clear();
		for (CommandBind bind : binds()) {
			whereTexts.put(bind, whereText(bind));
		}
		twoColumns = width >= TWO_COLUMNS;
		listWidth = twoColumns ? Math.round(width * 0.6f) : width;
		int rows = Math.max(1, binds().size());
		listHeight = LIST_PAD * 2 + rows * (ROW + ROW_GAP) - ROW_GAP;
		int widest = 0;
		for (CommandBind bind : binds()) {
			widest = Math.max(widest, capsWidth(ui, bind));
		}
		commandColumn = LIST_PAD + CAPS_INSET + Math.min(MOST_CAPS_WIDTH, Math.max(28, widest)) + CAP_GAP;
		buttonY = listHeight + 8;
		buttonWidth = Math.round(ui.boldWidthScaled("New keybind", TextScale.BODY)) + 30;
		noteY = buttonY + BUTTON_HEIGHT + 8;
		note = ui.wrapScaled(NOTE, listWidth, TextScale.SMALL, 3);
		int leftHeight = noteY + note.size() * NOTE_LINE;

		editorWidth = twoColumns ? width - listWidth - COLUMN_GAP : width;
		editorHeight = layoutEditor(ui, editorWidth, current);
		editorX = twoColumns ? listWidth + COLUMN_GAP : 0;
		editorY = twoColumns ? 0 : leftHeight + 14;
		measuredHeight = twoColumns ? Math.max(leftHeight, editorHeight) : editorY + editorHeight;
		return measuredHeight;
	}

	/** Lays out the parts of "where it works": measured bold, as the chosen one is drawn, and smaller only as far as the control has to fit the editor. */
	private void layoutPlaces(Ui ui, int inner) {
		CommandBind.Where[] places = wheres();
		float text = 0f;
		for (CommandBind.Where where : places) {
			text += ui.boldWidthScaled(label(where), TextScale.BODY);
		}
		int room = inner - 2 * SEGMENT_INSET;
		int padding = SEGMENT_PADDING;
		segmentScale = SegmentFit.scale(room - places.length * 2 * padding, text);
		if (segmentScale < SEGMENT_TIGHT_FROM_SCALE) {
			padding = SEGMENT_TIGHT_PADDING;
			segmentScale = SegmentFit.scale(room - places.length * 2 * padding, text);
		}
		int cursor = 0;
		for (CommandBind.Where where : places) {
			int part = (int) (ui.boldWidthScaled(label(where), TextScale.BODY) * segmentScale) + 2 * padding;
			segmentBoxes[where.ordinal()] = new int[]{cursor, whereY, part};
			cursor += part;
		}
	}

	/** Lays out the editor of the selected keybind. @return its height */
	private int layoutEditor(Ui ui, int width, String current) {
		if (selected == null) {
			return 2 * PAD + 2 * LINE + 4;
		}
		int inner = width - 2 * PAD;
		// Duplicate and Delete have their row right below the title, always in view however long the rest gets
		actionsY = PAD + 18 + 2;
		int y = actionsY + SMALL_BUTTON_HEIGHT + 6;
		keyY = y + CAPTION;
		y = keyY + RECORDER_HEIGHT + 6;
		conflictY = y;
		CommandBind other = CommandBinds.conflictOf(binds(), selected);
		ownConflict = other == null ? List.of()
			: ui.wrap("Your keybind for " + other.command + " uses this key in the same place. Pick another key or other islands.", inner - 14, 3);
		String use = selected.enabled ? CommandKeybindsFeature.otherUseOf(selected.key) : null;
		gameConflict = use == null ? List.of()
			: ui.wrap(String.join(" + ", CommandKeybindsFeature.keyParts(selected.key, selected.modifiers)) + " is also \"" + use
			+ "\" in the game. While this keybind works, it takes the key.", inner - 14, 3);
		y += (ownConflict.size() + gameConflict.size()) * LINE + (ownConflict.isEmpty() && gameConflict.isEmpty() ? 0 : 6);
		commandY = y + CAPTION;
		y = commandY + FIELD_HEIGHT + 10;
		whereY = y + CAPTION;
		layoutPlaces(ui, inner);
		y = whereY + SEGMENT_HEIGHT + 6;
		if (selected.where == CommandBind.Where.ISLANDS) {
			y = layoutIslands(ui, inner, y, current) + 6;
		}
		screenY = y + 6;
		duplicateWidth = ui.width("Duplicate") + 2 * CHIP_TEXT + 6;
		deleteWidth = ui.width("Delete") + 2 * CHIP_TEXT + 6;
		return screenY + 26 + PAD;
	}

	/** Lays out the island chips, and the field for a new island below them when it is open. @return the bottom */
	private int layoutIslands(Ui ui, int inner, int top, String current) {
		List<Place> builtIn = KeybindPlaces.places();
		customNames.clear();
		customNames.addAll(customIslands().names());
		int count = 0;
		// a chosen island has a check before its name; one not chosen has none, and even padding on both sides
		for (Place island : builtIn) {
			chipWidths[count++] = ui.width(island.name()) + 2 * CHIP_TEXT + (selected.islands.contains(island.key()) ? CHIP_CHECK : 0);
		}
		for (String name : customNames) {
			chipWidths[count++] = ui.width(name) + 2 * CHIP_TEXT + (selected.customIslands.contains(name) ? CHIP_CHECK : 0) + CHIP_REMOVE;
		}
		chipWidths[count++] = ui.width(ADD_ISLAND) + 2 * CHIP_TEXT + CHIP_CHECK;
		islandChips = count;
		int y = wrapChips(chipWidths, count, islandBoxes, inner, top);
		if (!addingIsland) {
			return y;
		}
		islandFieldY = y + 6;
		currentIsland = current;
		if (current != null) {
			Place known = CustomIslands.builtIn(current);
			currentLabel = USE_CURRENT + (known != null ? known.name() : current);
			int most = inner / 2;
			currentLabel = ui.fit(currentLabel, most - 2 * CHIP_TEXT);
			islandFieldWidth = inner - (ui.width(currentLabel) + 2 * CHIP_TEXT) - CHIP_GAP;
		} else {
			currentLabel = null;
			islandFieldWidth = inner;
		}
		return islandFieldY + FIELD_HEIGHT;
	}

	/** Places chips of the given widths in rows of a width. @return the bottom of the last row */
	private static int wrapChips(int[] widths, int count, int[][] boxes, int width, int top) {
		int x = 0;
		int y = top;
		for (int i = 0; i < count; i++) {
			int w = widths[i];
			if (x > 0 && x + w > width) {
				x = 0;
				y += CHIP_HEIGHT + CHIP_GAP;
			}
			boxes[i][0] = x;
			boxes[i][1] = y;
			boxes[i][2] = w;
			x += w + CHIP_GAP;
		}
		return y + CHIP_HEIGHT;
	}

	private int capsWidth(Ui ui, CommandBind bind) {
		int width = 0;
		for (String part : CommandKeybindsFeature.keyParts(bind.key, bind.modifiers)) {
			width += capWidth(ui, part) + (width > 0 ? 10 : 0);
		}
		return width;
	}

	private static int capWidth(Ui ui, String part) {
		return Math.max(CAP_HEIGHT + 6, Math.round(ui.boldWidthScaled(part, TextScale.BODY)) + 2 * CAP_PAD);
	}

	private int rowTop(int index) {
		return y + LIST_PAD + index * (ROW + ROW_GAP);
	}

	/** @return the index of the row at a height on the page, clamped to the list */
	private int indexAt(double pageY) {
		int index = (int) Math.floor((pageY - y - LIST_PAD) / (ROW + ROW_GAP));
		return Math.max(0, Math.min(binds().size() - 1, index));
	}

	private int switchX() {
		return x + listWidth - LIST_PAD - 6 - SwitchControl.TRACK_WIDTH;
	}

	// ---- drawing ----

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		boolean menuOpen = menuFor != null;
		int mx = menuOpen ? Integer.MIN_VALUE : mouseX;
		int my = menuOpen ? Integer.MIN_VALUE : mouseY;
		card(ui, x, y, listWidth, listHeight);
		List<CommandBind> binds = binds();
		if (binds.isEmpty()) {
			ui.textMiddle("No keybinds yet. Add one below.", x + LIST_PAD + 10, y + listHeight / 2, palette.textSecondary());
		}
		for (int i = 0; i < binds.size(); i++) {
			drawRow(ui, binds.get(i), rowTop(i), mx, my);
		}

		// New keybind, and what a press does right below it
		int by = y + buttonY;
		boolean overButton = mx >= x && mx < x + buttonWidth && my >= by && my < by + BUTTON_HEIGHT;
		float hover = hover(ui, 0, overButton);
		ui.rounded(x, by, buttonWidth, BUTTON_HEIGHT, Radius.MD, Ui.withOpacity(palette.text(), 0.05f + 0.06f * hover));
		ui.ring(x, by, buttonWidth, BUTTON_HEIGHT, Radius.MD, 1, Ui.mix(palette.controlBorder(), ui.accent, 0.3f + 0.5f * hover));
		ui.icon("plus", x + 9, by + (BUTTON_HEIGHT - 10) / 2, 10, palette.text());
		ui.textAtScaledBold("New keybind", x + 23, by + BUTTON_HEIGHT / 2f - 4f, TextScale.BODY, palette.text());
		for (int i = 0; i < note.size(); i++) {
			ui.textAtScaled(note.get(i), x, y + noteY + i * NOTE_LINE, TextScale.SMALL, palette.textSecondary());
		}

		drawEditor(ui, mx, my);
	}

	private static void card(Ui ui, int x, int y, int width, int height) {
		ui.rounded(x, y, width, height, Radius.LG, ui.palette.card());
		ui.ring(x, y, width, height, Radius.LG, 1, ui.palette.cardBorder());
	}

	/** Draws a section caption, written in capitals. */
	private static void caption(Ui ui, String text, int x, int y) {
		ui.textAtScaledBold(text, x, y, TextScale.SMALL, ui.palette.textSecondary());
	}

	private void drawRow(Ui ui, CommandBind bind, int rowY, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int rowX = x + LIST_PAD;
		int rowWidth = listWidth - 2 * LIST_PAD;
		boolean over = mouseX >= rowX && mouseX < rowX + rowWidth && mouseY >= rowY && mouseY < rowY + ROW;
		boolean isSelected = bind == selected;
		if (isSelected || over) {
			int fill = isSelected ? Ui.mix(palette.control(), ui.accent, 0.08f) : palette.control();
			ui.rounded(rowX, rowY, rowWidth, ROW, Radius.MD, fill);
		}
		if (isSelected) {
			ui.ring(rowX, rowY, rowWidth, ROW, Radius.MD, 1, Ui.withOpacity(ui.accent, 0.85f));
		}
		float dim = bind.enabled ? 1f : 0.5f;
		// the key as key caps, "Ctrl" + "1"
		int capX = rowX + CAPS_INSET;
		int capY = rowY + (ROW - CAP_HEIGHT) / 2;
		List<String> parts = CommandKeybindsFeature.keyParts(bind.key, bind.modifiers);
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				ui.textMiddle("+", capX + 2, rowY + ROW / 2, palette.textSecondary());
				capX += 10;
			}
			capX += keyCap(ui, parts.get(i), capX, capY, bind.hasKey(), dim);
		}
		// the command, then where it works and the switch at the right
		int sx = switchX();
		String chip = whereTexts.getOrDefault(bind, "");
		int chipWidth = Math.min(ui.width(chip) + 22, Math.max(60, (rowWidth - commandColumn) / 2));
		int chipX = sx - 10 - chipWidth;
		int commandX = x + commandColumn;
		int commandColor = bind.validCommand() ? Ui.withOpacity(palette.text(), dim) : SystemColors.RED_TEXT;
		ui.textMiddle(ui.fit(bind.command, chipX - 8 - commandX), commandX, rowY + ROW / 2, commandColor);
		whereChip(ui, bind, chip, chipX, rowY + (ROW - CHIP_HEIGHT) / 2, chipWidth, dim);
		int sy = rowY + (ROW - SwitchControl.TRACK_HEIGHT) / 2;
		boolean overSwitch = mouseX >= sx && mouseX < sx + SwitchControl.TRACK_WIDTH && mouseY >= sy && mouseY < sy + SwitchControl.TRACK_HEIGHT;
		Spring knob = knobs.computeIfAbsent(bind, b -> Spring.smooth(Spring.DEFAULT));
		float position = ui.animated ? knob.step(bind.enabled ? 1f : 0f, ui.deltaSeconds) : knob.snap(bind.enabled ? 1f : 0f);
		SwitchControl.draw(ui, sx, sy, position, overSwitch ? 1f : 0f, 0f);
	}

	/** Draws a key cap. @return its width */
	private static int keyCap(Ui ui, String text, int capX, int capY, boolean set, float dim) {
		Palette palette = ui.palette;
		int width = capWidth(ui, text);
		ui.rounded(capX, capY, width, CAP_HEIGHT, Radius.XS, Ui.withOpacity(palette.text(), 0.08f * dim));
		ui.ring(capX, capY, width, CAP_HEIGHT, Radius.XS, 1, Ui.withOpacity(palette.text(), 0.18f * dim));
		int color = set ? Ui.withOpacity(palette.text(), dim) : palette.textSecondary();
		ui.textAtScaledBold(text, capX + (width - Math.round(ui.boldWidthScaled(text, TextScale.BODY))) / 2f, capY + CAP_HEIGHT / 2f - 4f, TextScale.BODY, color);
		return width;
	}

	/** The choices of where a keybind works: all three with an add-on that knows the world, otherwise only everywhere. */
	private static final CommandBind.Where[] EVERYWHERE_ONLY = {CommandBind.Where.EVERYWHERE};

	private static CommandBind.Where[] wheres() {
		return KeybindPlaces.available() ? CommandBind.Where.values() : EVERYWHERE_ONLY;
	}

	/** @return the name of a choice; the add-on's world goes by its own name ("SkyBlock") */
	private static String label(CommandBind.Where where) {
		String world = KeybindPlaces.worldName();
		return where == CommandBind.Where.SKYBLOCK && !world.isEmpty() ? world : where.label;
	}

	/** @return where a keybind works, for its chip: "Everywhere", "SkyBlock" or its islands */
	private static String whereText(CommandBind bind) {
		return switch (bind.where) {
			case EVERYWHERE -> "Everywhere";
			case SKYBLOCK -> label(CommandBind.Where.SKYBLOCK);
			case ISLANDS -> bind.noIslands() ? "No island picked" : islandNames(bind);
		};
	}

	private static String islandNames(CommandBind bind) {
		StringBuilder names = new StringBuilder();
		// the known places in their order, then keys of places no add-on knows now
		for (Place place : KeybindPlaces.places()) {
			if (bind.islands.contains(place.key())) {
				if (!names.isEmpty()) {
					names.append(", ");
				}
				names.append(place.name());
			}
		}
		for (String island : bind.islands) {
			if (CustomIslands.builtIn(KeybindPlaces.nameOf(island)) == null) {
				if (!names.isEmpty()) {
					names.append(", ");
				}
				names.append(KeybindPlaces.nameOf(island));
			}
		}
		for (String island : bind.customIslands) {
			if (!names.isEmpty()) {
				names.append(", ");
			}
			names.append(island);
		}
		return names.toString();
	}

	private static void whereChip(Ui ui, CommandBind bind, String text, int chipX, int chipY, int width, float dim) {
		int tint = switch (bind.where) {
			case EVERYWHERE -> ui.palette.textSecondary();
			case SKYBLOCK -> SystemColors.YELLOW;
			case ISLANDS -> bind.noIslands() ? SystemColors.RED_TEXT : SystemColors.TEAL;
		};
		String icon = switch (bind.where) {
			case EVERYWHERE -> "map";
			case SKYBLOCK -> "star";
			case ISLANDS -> "pin";
		};
		ui.rounded(chipX, chipY, width, CHIP_HEIGHT, Radius.SM, Ui.withOpacity(tint, 0.14f * dim));
		ui.icon(icon, chipX + 6, chipY + (CHIP_HEIGHT - 8) / 2, 8, Ui.withOpacity(tint, dim));
		ui.textMiddle(ui.fit(text, width - 22), chipX + 17, chipY + CHIP_HEIGHT / 2, Ui.withOpacity(tint, dim));
	}

	private void drawEditor(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int ex = x + editorX;
		int ey = y + editorY;
		card(ui, ex, ey, editorWidth, editorHeight);
		int left = ex + PAD;
		int inner = editorWidth - 2 * PAD;
		if (selected == null) {
			ui.textAtScaledBold("Edit keybind", left, ey + PAD, TextScale.BODY, palette.text());
			ui.text(ui.fit("Select a keybind to edit it, or add a new one.", inner), left, ey + PAD + LINE + 4, palette.textSecondary());
			return;
		}
		CommandBind bind = selected;
		ui.textAtScaledBold("Edit keybind", left, ey + PAD + 2f, TextScale.BODY, palette.text());
		int sx = ex + editorWidth - PAD - SwitchControl.TRACK_WIDTH;
		int sy = ey + PAD - 2;
		Spring knob = knobs.computeIfAbsent(bind, b -> Spring.smooth(Spring.DEFAULT));
		boolean overSwitch = mouseX >= sx && mouseX < sx + SwitchControl.TRACK_WIDTH && mouseY >= sy && mouseY < sy + SwitchControl.TRACK_HEIGHT;
		SwitchControl.draw(ui, sx, sy, knob.value(), overSwitch ? 1f : 0f, 0f);

		drawRecorder(ui, bind, left, ey + keyY, inner, mouseX, mouseY);
		// conflicts, in plain words
		int cy = ey + conflictY;
		for (int i = 0; i < ownConflict.size(); i++) {
			if (i == 0) {
				ui.icon("info", left, cy + 1, 8, SystemColors.RED_TEXT);
			}
			ui.text(ownConflict.get(i), left + 14, cy, SystemColors.RED_TEXT);
			cy += LINE;
		}
		for (int i = 0; i < gameConflict.size(); i++) {
			if (i == 0) {
				ui.icon("info", left, cy + 1, 8, SystemColors.ORANGE);
			}
			ui.text(gameConflict.get(i), left + 14, cy, SystemColors.ORANGE);
			cy += LINE;
		}

		// the command
		caption(ui, "COMMAND", left, ey + commandY - CAPTION);
		int fy = ey + commandY;
		ui.rounded(left, fy, inner, FIELD_HEIGHT, Radius.MD, palette.control());
		ui.ring(left, fy, inner, FIELD_HEIGHT, Radius.MD, 1, commandFocused ? ui.accent : palette.controlBorder());
		boolean valid = bind.validCommand();
		String state = valid ? "Command" : "Starts with /";
		int stateColor = valid ? SystemColors.GREEN : SystemColors.RED_TEXT;
		int stateWidth = ui.width(state) + (valid ? 12 : 0);
		int stateX = left + inner - 8 - stateWidth;
		if (valid) {
			ui.icon("check", stateX, fy + (FIELD_HEIGHT - 8) / 2, 8, stateColor);
		}
		ui.textMiddle(state, stateX + (valid ? 12 : 0), fy + FIELD_HEIGHT / 2, stateColor);
		drawFieldText(ui, bind.command, left + 8, fy, stateX - 8 - (left + 8), commandFocused, commandSelection);

		// where it works
		caption(ui, "WHERE IT WORKS", left, ey + whereY - CAPTION);
		int segmentY = ey + whereY;
		CommandBind.Where[] places = wheres();
		int[] lastPlace = segmentBoxes[places[places.length - 1].ordinal()];
		ui.rounded(left, segmentY, lastPlace[0] + lastPlace[2] + 2 * SEGMENT_INSET, SEGMENT_HEIGHT, Radius.MD, Ui.withOpacity(palette.text(), 0.07f));
		float placeTextY = segmentY + SEGMENT_HEIGHT / 2f - 4f * segmentScale;
		for (CommandBind.Where where : places) {
			int[] box = segmentBoxes[where.ordinal()];
			int bx = left + SEGMENT_INSET + box[0];
			boolean on = where == bind.where;
			boolean over = mouseX >= bx && mouseX < bx + box[2] && mouseY >= segmentY && mouseY < segmentY + SEGMENT_HEIGHT;
			if (on || over) {
				ui.rounded(bx, segmentY + 2, box[2], SEGMENT_HEIGHT - 4, Radius.SM, on ? palette.pill() : Ui.withOpacity(palette.text(), 0.06f));
			}
			// in the middle of its part, which is measured for the bold text
			if (on) {
				ui.textAtScaledBold(label(where), bx + (box[2] - ui.boldWidthScaled(label(where), segmentScale)) / 2f, placeTextY, segmentScale, palette.onPill());
			} else {
				ui.textAtScaled(label(where), bx + (box[2] - ui.widthScaled(label(where), segmentScale)) / 2f, placeTextY, segmentScale, palette.textSecondary());
			}
		}
		if (bind.where == CommandBind.Where.ISLANDS) {
			drawIslands(ui, bind, left, ey, mouseX, mouseY);
		}

		// only while no screen is open
		int screenTop = ey + screenY;
		ui.fill(left, screenTop - 6, inner, 1, Ui.withOpacity(palette.text(), 0.1f));
		ui.text("Only while no screen is open", left, screenTop + 2, palette.text());
		String screenNote = bind.onlyWithoutScreen ? "Not in chat, inventories or menus" : "Also in inventories and containers, never while typing";
		ui.textAtScaled(ui.fitScaled(screenNote, inner - SwitchControl.TRACK_WIDTH - 8, TextScale.SMALL), left, screenTop + 14f, TextScale.SMALL,
			palette.textSecondary());
		int screenSwitchX = left + inner - SwitchControl.TRACK_WIDTH;
		int screenSwitchY = screenTop + 2;
		boolean overScreen = mouseX >= screenSwitchX && mouseX < screenSwitchX + SwitchControl.TRACK_WIDTH && mouseY >= screenSwitchY
			&& mouseY < screenSwitchY + SwitchControl.TRACK_HEIGHT;
		SwitchControl.draw(ui, screenSwitchX, screenSwitchY, bind.onlyWithoutScreen ? 1f : 0f, overScreen ? 1f : 0f, 0f);

		// Duplicate and Delete, below the title at the right
		int actionTop = ey + actionsY;
		int deleteX = left + inner - deleteWidth;
		int duplicateX = deleteX - CHIP_GAP - duplicateWidth;
		button(ui, "Duplicate", duplicateX, actionTop, duplicateWidth, palette.text(), Ui.withOpacity(palette.text(), 0.07f), mouseX, mouseY);
		button(ui, "Delete", deleteX, actionTop, deleteWidth, SystemColors.RED_TEXT, Ui.withOpacity(SystemColors.RED, 0.12f), mouseX, mouseY);
	}

	/** Draws the key recorder: the key as key caps, or "Press a key…" with a pulsing ring while it listens. */
	private void drawRecorder(Ui ui, CommandBind bind, int left, int ry, int inner, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		caption(ui, "KEY", left, ry - CAPTION);
		boolean overRecorder = mouseX >= left && mouseX < left + inner && mouseY >= ry && mouseY < ry + RECORDER_HEIGHT;
		ui.rounded(left, ry, inner, RECORDER_HEIGHT, Radius.MD, palette.control());
		if (recording) {
			float pulse = 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1_000_000_000.0 * Math.PI * 2.0 * PULSE_RATE);
			ui.rounded(left, ry, inner, RECORDER_HEIGHT, Radius.MD, Ui.withOpacity(ui.accent, 0.15f));
			ui.ring(left, ry, inner, RECORDER_HEIGHT, Radius.MD, 1, Ui.withOpacity(ui.accent, pulse));
			ui.textAtScaledBold("Press a key…", left + 10, ry + RECORDER_HEIGHT / 2f - 4f, TextScale.BODY, palette.text());
			String hint = "Esc cancels · Backspace clears";
			ui.textAtScaled(hint, left + inner - 10 - ui.widthScaled(hint, TextScale.SMALL), ry + RECORDER_HEIGHT / 2f - 3.5f, TextScale.SMALL,
				palette.textSecondary());
			return;
		}
		ui.ring(left, ry, inner, RECORDER_HEIGHT, Radius.MD, 1, Ui.mix(palette.controlBorder(), ui.accent, overRecorder ? 0.6f : 0f));
		int capX = left + 6;
		int capY = ry + (RECORDER_HEIGHT - CAP_HEIGHT) / 2;
		if (bind.hasKey()) {
			List<String> parts = CommandKeybindsFeature.keyParts(bind.key, bind.modifiers);
			for (int i = 0; i < parts.size(); i++) {
				if (i > 0) {
					ui.textMiddle("+", capX + 2, ry + RECORDER_HEIGHT / 2, palette.textSecondary());
					capX += 10;
				}
				capX += keyCap(ui, parts.get(i), capX, capY, true, 1f);
			}
		} else {
			ui.textMiddle("No key yet", capX + 4, ry + RECORDER_HEIGHT / 2, palette.textSecondary());
		}
		String hint = bind.hasKey() ? "Click to change" : "Click to set";
		ui.textAtScaled(hint, left + inner - 10 - ui.widthScaled(hint, TextScale.SMALL), ry + RECORDER_HEIGHT / 2f - 3.5f, TextScale.SMALL,
			palette.textSecondary());
	}

	/** Draws the island chips (built-in, the player's own with their ×, and "Add island") and the field for a new island when it is open. */
	private void drawIslands(Ui ui, CommandBind bind, int left, int ey, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		List<Place> builtIn = KeybindPlaces.places();
		for (int i = 0; i < islandChips; i++) {
			int[] box = islandBoxes[i];
			int cx = left + box[0];
			int cy = ey + box[1];
			if (i < builtIn.size()) {
				chip(ui, builtIn.get(i).name(), cx, cy, box[2], bind.islands.contains(builtIn.get(i).key()), mouseX, mouseY);
			} else if (i - builtIn.size() < customNames.size()) {
				String name = customNames.get(i - builtIn.size());
				chip(ui, name, cx, cy, box[2], bind.customIslands.contains(name), mouseX, mouseY);
				int removeX = cx + box[2] - CHIP_REMOVE;
				boolean overRemove = mouseX >= removeX && mouseX < cx + box[2] && mouseY >= cy && mouseY < cy + CHIP_HEIGHT;
				ui.icon("close", removeX + 1, cy + (CHIP_HEIGHT - 7) / 2, 7, Ui.withOpacity(palette.text(), overRemove ? 0.95f : 0.5f));
			} else {
				boolean over = mouseX >= cx && mouseX < cx + box[2] && mouseY >= cy && mouseY < cy + CHIP_HEIGHT;
				ui.rounded(cx, cy, box[2], CHIP_HEIGHT, Radius.SM, Ui.withOpacity(palette.text(), addingIsland || over ? 0.12f : 0.07f));
				ui.icon("plus", cx + 5, cy + (CHIP_HEIGHT - 8) / 2, 8, palette.text());
				ui.textAtScaledBold(ADD_ISLAND, cx + CHIP_TEXT + CHIP_CHECK, cy + CHIP_HEIGHT / 2f - 4f, TextScale.BODY, palette.text());
			}
		}
		if (!addingIsland) {
			return;
		}
		int fy = ey + islandFieldY;
		ui.rounded(left, fy, islandFieldWidth, FIELD_HEIGHT, Radius.MD, palette.control());
		ui.ring(left, fy, islandFieldWidth, FIELD_HEIGHT, Radius.MD, 1, ui.accent);
		if (islandDraft.isEmpty()) {
			ui.textMiddle(ui.fit("Island name", islandFieldWidth - 16), left + 8, fy + FIELD_HEIGHT / 2, palette.textSecondary());
		}
		drawFieldText(ui, islandDraft, left + 8, fy, islandFieldWidth - 16, true, islandSelection);
		if (currentLabel != null) {
			int bx = left + islandFieldWidth + CHIP_GAP;
			int width = editorWidth - 2 * PAD - islandFieldWidth - CHIP_GAP;
			button(ui, currentLabel, bx, fy + (FIELD_HEIGHT - SMALL_BUTTON_HEIGHT) / 2, width, palette.text(), Ui.withOpacity(palette.text(), 0.07f),
				mouseX, mouseY);
		}
	}

	/** Draws the text of a field, its end kept in view while typing, with the selection and a blinking caret when it has the keyboard. */
	private static void drawFieldText(Ui ui, String text, int textX, int fieldY, int room, boolean focused, TextSelection selection) {
		String shown = tail(ui, text, room);
		if (focused) {
			selection.draw(ui, textX, fieldY + FIELD_HEIGHT / 2, ui.width(shown));
		}
		ui.textMiddle(shown, textX, fieldY + FIELD_HEIGHT / 2, ui.palette.text());
		if (focused && !selection.all() && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(textX + ui.width(shown) + 1, fieldY + 5, 1, FIELD_HEIGHT - 10, ui.palette.text());
		}
	}

	/** Draws a small button with a centered label. */
	private static void button(Ui ui, String label, int bx, int by, int width, int textColor, int fill, int mouseX, int mouseY) {
		boolean over = mouseX >= bx && mouseX < bx + width && mouseY >= by && mouseY < by + SMALL_BUTTON_HEIGHT;
		ui.rounded(bx, by, width, SMALL_BUTTON_HEIGHT, Radius.MD, over ? Ui.mix(fill, textColor, 0.08f) : fill);
		float textWidth = ui.boldWidthScaled(label, TextScale.BODY);
		ui.textAtScaledBold(label, bx + (width - textWidth) / 2f, by + SMALL_BUTTON_HEIGHT / 2f - 4f, TextScale.BODY, textColor);
	}

	/** Draws an island's chip: a chosen one in the accent with a check before its name, one not chosen plain, with even padding. */
	private static void chip(Ui ui, String label, int cx, int cy, int width, boolean on, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		boolean over = mouseX >= cx && mouseX < cx + width && mouseY >= cy && mouseY < cy + CHIP_HEIGHT;
		ui.rounded(cx, cy, width, CHIP_HEIGHT, Radius.SM, on ? Ui.withOpacity(ui.accent, 0.22f) : Ui.withOpacity(palette.text(), over ? 0.1f : 0.05f));
		ui.ring(cx, cy, width, CHIP_HEIGHT, Radius.SM, 1, on ? Ui.withOpacity(ui.accent, 0.8f) : Ui.withOpacity(palette.text(), 0.12f));
		int textX = cx + CHIP_TEXT;
		if (on) {
			ui.icon("check", cx + 5, cy + (CHIP_HEIGHT - 8) / 2, 8, palette.text());
			textX += CHIP_CHECK;
		}
		ui.textMiddle(label, textX, cy + CHIP_HEIGHT / 2, on ? palette.text() : palette.textSecondary());
	}

	/** @return the end of a text that fits, so the caret's side stays visible while typing a long command */
	private static String tail(Ui ui, String text, int room) {
		if (ui.width(text) <= room) {
			return text;
		}
		int start = 0;
		while (start < text.length() && ui.width("…" + text.substring(start)) > room) {
			start++;
		}
		return "…" + text.substring(start);
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (menuFor == null) {
			return;
		}
		Palette palette = ui.palette;
		int height = MENU.length * MENU_ROW + 6;
		ui.shadow(menuX, menuY + 2, MENU_WIDTH, height, Radius.MD, 8, 0.35f);
		ui.rounded(menuX, menuY, MENU_WIDTH, height, Radius.MD, palette.popup());
		ui.ring(menuX, menuY, MENU_WIDTH, height, Radius.MD, 1, palette.popupBorder());
		for (int i = 0; i < MENU.length; i++) {
			int rowY = menuY + 3 + i * MENU_ROW;
			boolean over = mouseX >= menuX && mouseX < menuX + MENU_WIDTH && mouseY >= rowY && mouseY < rowY + MENU_ROW;
			if (over) {
				ui.rounded(menuX + 3, rowY, MENU_WIDTH - 6, MENU_ROW, Radius.SM, i == 1 ? Ui.withOpacity(SystemColors.RED, 0.2f) : palette.hover());
			}
			ui.icon(i == 0 ? "plus" : "trash", menuX + 9, rowY + (MENU_ROW - 10) / 2, 10, i == 1 ? SystemColors.RED_TEXT : palette.textSecondary());
			ui.textMiddle(MENU[i], menuX + 25, rowY + MENU_ROW / 2, i == 1 ? SystemColors.RED_TEXT : palette.text());
		}
	}

	@Override
	public boolean isOverlayOpen() {
		return menuFor != null;
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (menuFor != null) {
			return clickMenu(mouseX, mouseY);
		}
		if (recording) {
			// any click ends the recording without a change
			recording = false;
			if (inRecorder(mouseX, mouseY)) {
				return true;
			}
		}
		boolean inField = selected != null && inBox(mouseX, mouseY, x + editorX + PAD, y + editorY + commandY, editorWidth - 2 * PAD, FIELD_HEIGHT);
		if (commandFocused && !inField) {
			commandFocused = false;
		}
		if (!contains(mouseX, mouseY)) {
			addingIsland = false;
			return false;
		}
		List<CommandBind> binds = binds();
		// the list
		if (mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + listHeight && !binds.isEmpty()) {
			int index = indexAt(mouseY);
			int top = rowTop(index);
			if (mouseY < top || mouseY >= top + ROW) {
				return true;
			}
			CommandBind bind = binds.get(index);
			if (button == ScreenInput.RIGHT) {
				menuFor = bind;
				menuX = (int) mouseX;
				menuY = (int) mouseY;
				UiSounds.click();
				return true;
			}
			if (button != ScreenInput.LEFT) {
				return true;
			}
			if (mouseX >= switchX() - 4) {
				toggle(bind);
			} else if (bind != selected) {
				select(bind);
				UiSounds.click();
			}
			return true;
		}
		if (button != ScreenInput.LEFT) {
			return true;
		}
		// New keybind: added, selected and listening for its key
		if (inBox(mouseX, mouseY, x, y + buttonY, buttonWidth, BUTTON_HEIGHT)) {
			CommandBind bind = new CommandBind();
			changing();
			if (setting.add(bind, null)) {
				select(bind);
				recording = true;
				UiSounds.click();
				changed();
			}
			return true;
		}
		if (selected != null) {
			clickEditor(mouseX, mouseY, inField);
		}
		return true;
	}

	private static boolean inBox(double mouseX, double mouseY, int bx, int by, int width, int height) {
		return mouseX >= bx && mouseX < bx + width && mouseY >= by && mouseY < by + height;
	}

	private boolean inRecorder(double mouseX, double mouseY) {
		return selected != null && inBox(mouseX, mouseY, x + editorX + PAD, y + editorY + keyY, editorWidth - 2 * PAD, RECORDER_HEIGHT);
	}

	private void select(CommandBind bind) {
		if (selected != bind) {
			selected = bind;
			recording = false;
			commandFocused = false;
			commandSelection.clear();
			closeIslandField();
		}
	}

	private void toggle(CommandBind bind) {
		changing();
		bind.enabled = !bind.enabled;
		UiSounds.toggle(bind.enabled);
		changed();
	}

	private void clickEditor(double mouseX, double mouseY, boolean inField) {
		CommandBind bind = selected;
		int ex = x + editorX;
		int ey = y + editorY;
		int left = ex + PAD;
		int inner = editorWidth - 2 * PAD;
		if (addingIsland && clickIslandField(bind, mouseX, mouseY, left, ey)) {
			return;
		}
		if (inBox(mouseX, mouseY, ex + editorWidth - PAD - SwitchControl.TRACK_WIDTH, ey + PAD - 2, SwitchControl.TRACK_WIDTH, SwitchControl.TRACK_HEIGHT)) {
			toggle(bind);
			return;
		}
		if (inRecorder(mouseX, mouseY)) {
			recording = true;
			commandFocused = false;
			closeIslandField();
			UiSounds.click();
			return;
		}
		if (inField) {
			commandFocused = true;
			commandSelection.clear();
			closeIslandField();
			return;
		}
		for (CommandBind.Where where : wheres()) {
			int[] box = segmentBoxes[where.ordinal()];
			if (inBox(mouseX, mouseY, left + SEGMENT_INSET + box[0], ey + whereY, box[2], SEGMENT_HEIGHT)) {
				if (bind.where != where) {
					changing();
					bind.where = where;
					closeIslandField();
					UiSounds.click();
					changed();
				}
				return;
			}
		}
		if (bind.where == CommandBind.Where.ISLANDS && clickIslandChip(bind, mouseX, mouseY, left, ey)) {
			return;
		}
		if (inBox(mouseX, mouseY, left + inner - SwitchControl.TRACK_WIDTH, ey + screenY + 2, SwitchControl.TRACK_WIDTH, SwitchControl.TRACK_HEIGHT)) {
			changing();
			bind.onlyWithoutScreen = !bind.onlyWithoutScreen;
			UiSounds.toggle(bind.onlyWithoutScreen);
			changed();
			return;
		}
		int actionTop = ey + actionsY;
		int deleteX = left + inner - deleteWidth;
		if (inBox(mouseX, mouseY, deleteX - CHIP_GAP - duplicateWidth, actionTop, duplicateWidth, SMALL_BUTTON_HEIGHT)) {
			duplicate(bind);
		} else if (inBox(mouseX, mouseY, deleteX, actionTop, deleteWidth, SMALL_BUTTON_HEIGHT)) {
			delete(bind);
		}
	}

	/** A click on an island chip: picks or drops an island, removes one of the player's own (its ×), or opens the field for a new one. */
	private boolean clickIslandChip(CommandBind bind, double mouseX, double mouseY, int left, int ey) {
		List<Place> builtIn = KeybindPlaces.places();
		for (int i = 0; i < islandChips; i++) {
			int[] box = islandBoxes[i];
			int cx = left + box[0];
			int cy = ey + box[1];
			if (!inBox(mouseX, mouseY, cx, cy, box[2], CHIP_HEIGHT)) {
				continue;
			}
			if (i < builtIn.size()) {
				changing();
				boolean on = !bind.islands.remove(builtIn.get(i).key());
				if (on) {
					bind.islands.add(builtIn.get(i).key());
				}
				UiSounds.toggle(on);
				changed();
			} else if (i - builtIn.size() < customNames.size()) {
				String name = customNames.get(i - builtIn.size());
				if (mouseX >= cx + box[2] - CHIP_REMOVE) {
					removeCustomIsland(name);
				} else {
					changing();
					boolean on = !bind.customIslands.remove(name);
					if (on) {
						bind.customIslands.add(name);
					}
					UiSounds.toggle(on);
					changed();
				}
			} else if (addingIsland) {
				closeIslandField();
			} else {
				addingIsland = true;
				islandDraft = "";
				islandSelection.clear();
				commandFocused = false;
				UiSounds.click();
			}
			return true;
		}
		return false;
	}

	/** A click while the field for a new island is open: in the field it stays, on "Use current" that island is added. */
	private boolean clickIslandField(CommandBind bind, double mouseX, double mouseY, int left, int ey) {
		int fy = ey + islandFieldY;
		if (inBox(mouseX, mouseY, left, fy, islandFieldWidth, FIELD_HEIGHT)) {
			islandSelection.clear();
			return true;
		}
		if (currentLabel != null && inBox(mouseX, mouseY, left + islandFieldWidth + CHIP_GAP, fy, editorWidth - 2 * PAD - islandFieldWidth - CHIP_GAP,
			FIELD_HEIGHT)) {
			addIsland(bind, currentIsland);
			return true;
		}
		return false;
	}

	/** Picks an island by name for a keybind: a built-in one when the name is one, else the player's own, added to the list if new. */
	private void addIsland(CommandBind bind, String name) {
		Place known = CustomIslands.builtIn(name);
		String custom = known == null ? customIslands().add(name) : null;
		if (known == null && custom == null) {
			// empty, or the list is full
			UiSounds.play(UiSounds.Cue.OFF);
			return;
		}
		changing();
		if (known != null) {
			bind.islands.add(known.key());
		} else {
			bind.customIslands.add(custom);
			ModFeatures.COMMAND_KEYBINDS.saveCustomIslands();
		}
		closeIslandField();
		UiSounds.toggle(true);
		changed();
	}

	/** Removes one of the player's own islands from the list and from every keybind that had it; its notice can undo it. */
	private void removeCustomIsland(String name) {
		CustomIslands islands = customIslands();
		int index = islands.remove(name);
		if (index < 0) {
			return;
		}
		List<CommandBind> had = new ArrayList<>();
		changing();
		for (CommandBind bind : binds()) {
			if (bind.customIslands.remove(name)) {
				had.add(bind);
			}
		}
		ModFeatures.COMMAND_KEYBINDS.saveCustomIslands();
		UiSounds.toggle(false);
		changed();
		Toasts.info("Island removed", name, "Undo", () -> {
			customIslands().insert(index, name);
			ModFeatures.COMMAND_KEYBINDS.saveCustomIslands();
			setting.changing();
			for (CommandBind bind : had) {
				if (binds().contains(bind)) {
					bind.customIslands.add(name);
				}
			}
			changed();
		});
	}

	private void closeIslandField() {
		addingIsland = false;
		islandDraft = "";
		islandSelection.clear();
	}

	private void duplicate(CommandBind bind) {
		changing();
		CommandBind copy = bind.copy();
		if (setting.add(copy, bind)) {
			select(copy);
		}
		UiSounds.click();
		changed();
	}

	/** Deletes a keybind at once; its notice can bring it back to its place. */
	private void delete(CommandBind bind) {
		int index = binds().indexOf(bind);
		changing();
		setting.remove(bind);
		knobs.remove(bind);
		if (selected == bind) {
			select(null);
		}
		UiSounds.toggle(false);
		changed();
		String title = bind.validCommand() ? bind.command : "Keybind";
		Toasts.info("Keybind deleted", title, "Undo", () -> {
			if (!binds().contains(bind) && setting.insert(index, bind)) {
				select(bind);
				changed();
			}
		});
	}

	private boolean clickMenu(double mouseX, double mouseY) {
		CommandBind bind = menuFor;
		menuFor = null;
		for (int i = 0; i < MENU.length; i++) {
			int rowY = menuY + 3 + i * MENU_ROW;
			if (inBox(mouseX, mouseY, menuX, rowY, MENU_WIDTH, MENU_ROW)) {
				if (i == 0) {
					duplicate(bind);
				} else {
					delete(bind);
				}
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean wantsKeyboard() {
		return recording || commandFocused || addingIsland;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (recording && selected != null) {
			return record(key, modifiers);
		}
		if (addingIsland && selected != null) {
			return islandKey(key, modifiers);
		}
		if (!commandFocused || selected == null) {
			return false;
		}
		CommandBind bind = selected;
		if (commandSelection.key(key, modifiers, () -> bind.command, value -> {
			changing();
			bind.command = value;
			changed();
		}, CommandBind.MOST_COMMAND)) {
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE && !bind.command.isEmpty()) {
			changing();
			bind.command = bind.command.substring(0, bind.command.length() - 1);
			changed();
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_TAB) {
			commandFocused = false;
			return true;
		}
		return true;
	}

	/** A key in the field for a new island: Enter adds it, Esc closes the field. */
	private boolean islandKey(int key, int modifiers) {
		if (key == InputConstants.KEY_ESCAPE) {
			closeIslandField();
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			addIsland(selected, islandDraft);
			return true;
		}
		if (islandSelection.key(key, modifiers, () -> islandDraft, value -> islandDraft = value, CustomIslands.LONGEST_NAME)) {
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE && !islandDraft.isEmpty()) {
			islandDraft = islandDraft.substring(0, islandDraft.length() - 1);
		}
		return true;
	}

	/** A key while the recorder listens: Esc cancels, Backspace clears, a modifier alone waits for the key it modifies. */
	private boolean record(int key, int modifiers) {
		CommandBind bind = selected;
		if (key == InputConstants.KEY_ESCAPE) {
			recording = false;
			return true;
		}
		if (isModifier(key)) {
			return true;
		}
		changing();
		if (key == InputConstants.KEY_BACKSPACE) {
			bind.key = CommandBind.NO_KEY;
			bind.modifiers = 0;
			UiSounds.play(UiSounds.Cue.OFF);
		} else if (CommandBind.validKey(key)) {
			bind.key = key;
			bind.modifiers = modifiers & CommandBind.MODIFIERS;
			UiSounds.play(UiSounds.Cue.ON);
		}
		recording = false;
		changed();
		return true;
	}

	private static boolean isModifier(int key) {
		return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_LCONTROL
			|| key == InputConstants.KEY_RCONTROL || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
			|| key == InputConstants.KEY_LSUPER || key == InputConstants.KEY_RSUPER;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (selected == null || codepoint < ' ') {
			return recording || commandFocused || addingIsland;
		}
		String letters = new String(Character.toChars(codepoint));
		if (addingIsland) {
			String next = islandSelection.typed(islandDraft, letters);
			if (next.length() <= CustomIslands.LONGEST_NAME) {
				islandDraft = next;
			}
			return true;
		}
		if (!commandFocused) {
			return recording;
		}
		CommandBind bind = selected;
		String next = commandSelection.typed(bind.command, letters);
		if (next.length() <= CommandBind.MOST_COMMAND) {
			changing();
			bind.command = next;
			changed();
		}
		return true;
	}

	@Override
	public void loseFocus() {
		recording = false;
		commandFocused = false;
		closeIslandField();
		menuFor = null;
	}
}
