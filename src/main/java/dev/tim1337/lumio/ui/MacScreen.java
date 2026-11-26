package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.render.WorldBackdrop;
import com.mojang.blaze3d.platform.InputConstants;
import dev.tim1337.lumio.render.ColoredFlow;
import dev.tim1337.lumio.render.RoundedRows;
import dev.tim1337.lumio.render.GlassPanes;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.RecentChanges;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.ColorRowSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.FeatureGroupSetting;
import dev.tim1337.lumio.config.setting.FeatureRowSetting;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.config.setting.SearchHitSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.menu.FeatureListFeature;
import dev.tim1337.lumio.menu.HomeFeature;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.controls.ListDropdownControl;
import dev.tim1337.lumio.ui.controls.SegmentFit;
import dev.tim1337.lumio.ui.controls.ButtonControl;
import dev.tim1337.lumio.ui.controls.ColorSwatchControl;
import dev.tim1337.lumio.ui.controls.FeatureListControl;
import dev.tim1337.lumio.ui.controls.KeyControl;
import dev.tim1337.lumio.ui.controls.SwitchControl;
import dev.tim1337.lumio.util.ModVersion;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The settings menu, styled as a macOS window: a page list on the left and the selected page's settings, in groups, on the right. It is built
 * from the registered features and settings, so new ones need no changes here.
 */
public class MacScreen extends Screen {
	/** Width of the sidebar: 236 of 1380 px in the mockups, at the same proportion of the window. */
	private static final int SIDEBAR = 150;
	/** Width of a settings window opened outside the menu (from the HUD editor). */
	private static final int SETTINGS_WINDOW_WIDTH = 472;
	/** Inset of the sidebar's items and search field from its edges. */
	private static final int SIDE_INSET = 8;
	/** Gap between page groups in the sidebar: 18 px in the mockup, where the rows of a group sit 2 px apart. */
	private static final int GROUP_GAP = 11;
	private static final int PAD = 16;
	private static final int ROW_PAD = 8;
	private static final int CARD_PAD = 16;
	/** Opacity of the dividers between rows, in the text color: about 6 % white on dark windows. */
	private static final float DIVIDER_OPACITY = 0.06f;
	private static final float GLASS_DIVIDER_OPACITY = 0.1f;
	/** Gap between cards, the same everywhere in the menu. */
	private static final int CARD_GAP = 8;
	private static final int TITLE_BAR = 44;
	/** Room reserved for the icon in front of a feature's name in a list. */
	private static final int LABEL_ICON = 22;

	/** Kept across openings, so the menu reopens on the last page. */
	private static int selectedIndex;
	/** Scroll offset per page, restored when the menu reopens. */
	private static final Map<Integer, Double> SCROLLS = new HashMap<>();
	/** The open tab of every page and settings window, by name, so the menu reopens where it was. */
	private static final Map<Integer, String> TABS = new HashMap<>();
	/** Expanded collapsible groups, keyed by page and group name. Collapsed ones only show their switch. */
	private static final Set<String> OPEN_GROUPS = new HashSet<>();
	/** The name of the row a folded group shows. */
	private static final String FOLD_LABEL = "Fine-tune";
	private static final String FOLD_SEPARATOR = " · ";

	/** @return the names of a folded group's settings, for its row: "Edge light · Saturation" */
	private static String foldedNames(List<Entry> entries, String folder) {
		StringBuilder names = new StringBuilder();
		for (Entry each : entries) {
			if (each.collapsible() && each.folder().equals(folder)) {
				if (!names.isEmpty()) {
					names.append(FOLD_SEPARATOR);
				}
				names.append(each.setting().labelText());
			}
		}
		return names.toString();
	}

	private static final float MIN_ZOOM = MenuLayout.MIN_ZOOM;
	/** The room between the end of the sidebar's list and the fixed part under it. */
	private static final int SIDEBAR_BOTTOM_ROOM = 8;
	/**
	 * Smallest scale of the window, in real pixels per menu pixel: about Minecraft's font at GUI scale 1 plus a little, so the menu's text stays
	 * readable; it limits the window size on low GUI scales.
	 */
	private static final float MIN_REAL_SCALE = MenuLayout.MIN_REAL_SCALE;
	/** Step of the window size for Ctrl + wheel, Ctrl + minus/plus and the yellow and green buttons, the same as the slider's. */
	public static final float SIZE_STEP = 0.05f;

	private enum WindowDrag {
		NONE, MOVE
	}

	/** A setting on the page with its control, how deep it is nested under other settings, and how far its hover highlight is faded in. */
	private static final class Row {
		private final Setting<?> setting;
		private final Control control;
		int depth;
		float hover;

		Row(Setting<?> setting, Control control) {
			this.setting = setting;
			this.control = control;
		}

		Setting<?> setting() {
			return setting;
		}

		Control control() {
			return control;
		}
	}

	/** A titled group of rows, drawn as one card. */
	private static final class Group {
		private final String title;
		private final String upperTitle;
		private final List<Row> rows;
		private Boolean bare;

		Group(String title, List<Row> rows) {
			this.title = title;
			this.upperTitle = headingOf(title).toUpperCase(Locale.ROOT);
			this.rows = rows;
		}

		String title() {
			return title;
		}

		List<Row> rows() {
			return rows;
		}

		/** @return whether all rows are bare cards (a page's features), drawn without a card around them; asked once the page is built */
		boolean bare() {
			if (bare == null) {
				boolean all = true;
				for (Row row : rows) {
					all &= row.control().bare();
				}
				bare = all;
			}
			return bare;
		}
	}

	/** Position of a group, computed during layout and used for drawing. Reused from frame to frame, so laying out allocates nothing. */
	private static final class Placed {
		Group group;
		/** Where the group starts, its title or the cards before its card included: the card can start below what is already in view. */
		int top;
		/** How much of their light the title and card, and each row, have while the page's content changes ({@link #keepOutgoing}); 1 otherwise. */
		float cardLight = 1f;
		float[] rowLights = new float[8];
		int cardX;
		int cardY;
		int cardWidth;
		int cardHeight;
		int titleY;
		/** Whether the group's title is drawn above it. */
		boolean titled;
		int rows;
		int[] rowTops = new int[8];
		int[] rowHeights = new int[8];
		int[] rowDescriptionLines = new int[8];
		boolean[] rowDescriptionBelow = new boolean[8];
		/** Rows whose control did not fit beside the name: it is on a line of its own under the name and the description. */
		boolean[] rowControlBelow = new boolean[8];

		void addRow(int top, int height, int descriptionLines, boolean descriptionBelow, boolean controlBelow) {
			if (rows == rowTops.length) {
				rowTops = Arrays.copyOf(rowTops, rows * 2);
				rowHeights = Arrays.copyOf(rowHeights, rows * 2);
				rowDescriptionLines = Arrays.copyOf(rowDescriptionLines, rows * 2);
				rowDescriptionBelow = Arrays.copyOf(rowDescriptionBelow, rows * 2);
				rowControlBelow = Arrays.copyOf(rowControlBelow, rows * 2);
				rowLights = Arrays.copyOf(rowLights, rows * 2);
			}
			rowTops[rows] = top;
			rowHeights[rows] = height;
			rowDescriptionLines[rows] = descriptionLines;
			rowDescriptionBelow[rows] = descriptionBelow;
			rowControlBelow[rows] = controlBelow;
			rows++;
		}

		/** Gives the title, the card and every row the same share of their light. */
		void light(float light) {
			cardLight = light;
			Arrays.fill(rowLights, 0, rows, light);
		}

		/** @return whether the card and the title are where another group's are */
		boolean cardWhere(Placed other) {
			return cardX == other.cardX && cardY == other.cardY && cardWidth == other.cardWidth && cardHeight == other.cardHeight
				&& titled == other.titled && titleY == other.titleY;
		}
	}

	/** Line height of the description under a setting's name. */
	private static final int DESCRIPTION_LINE = 11;
	/** The height of a setting's title line, for a control centered on it. */
	private static final int TITLE_LINE = 12;
	/** Narrowest column for a description beside a control; with less room (a wide text field), it goes under the row at full width. */
	private static final int DESCRIPTION_MIN_ROOM = 150;
	/** The least width a control beside its name is squeezed to, so the name is not cut. */
	private static final int LEAST_INLINE_CONTROL = 100;
	/** Descriptions are drawn slightly smaller than names. */
	private static final float DESCRIPTION_SCALE = TextScale.SMALL;

	/**
	 * A description wrapped for a width, with what it was wrapped from: the text, the room and the text's whole width, which changes with the
	 * font and its size, so a wrap is only made again when one of them changed and not on every frame.
	 */
	private record DescriptionWrap(String text, int room, float width, List<String> lines) {
	}

	/** Wrapped description lines per setting for the available width, cached so layout and drawing agree. */
	private final Map<Setting<?>, DescriptionWrap> descriptionLines = new HashMap<>();

	private int descriptionLines(Ui ui, Row row, int room) {
		String description = row.setting().description();
		if (description == null || room < 60 || searching()) {
			descriptionLines.remove(row.setting());
			return 0;
		}
		float width = ui.widthScaled(description, DESCRIPTION_SCALE);
		DescriptionWrap last = descriptionLines.get(row.setting());
		if (last != null && last.room() == room && last.width() == width && last.text().equals(description)) {
			return last.lines().size();
		}
		// as many lines as needed so nothing is cut off; descriptions are kept short, so it is rarely more than two
		// measured exactly as it is drawn (the size in quarter steps, the game's font in whole pixels), so no line runs under the control
		List<String> lines = ui.wrapScaled(description, room, DESCRIPTION_SCALE, 4);
		descriptionLines.put(row.setting(), new DescriptionWrap(description, room, width, lines));
		return lines.size();
	}

	private final List<Feature> pages = new ArrayList<>();
	private final Map<Setting<?>, Control> controls = new HashMap<>();
	/** The same controls as a list, for the loops of every frame. */
	private final List<Control> controlList = new ArrayList<>();
	private final List<Group> groups = new ArrayList<>();
	private final List<Placed> placed = new ArrayList<>();
	/** Placed groups kept for reuse by the next layout. */
	private final List<Placed> placedPool = new ArrayList<>();
	/** Whether a row of the page has an Edit button, so lone buttons line up with them; known once the page is built. */
	private boolean editColumn;
	/** Title and description in the header, built with the page. */
	private String headerTitle = "";
	private String headerDescription = "";
	private List<Setting<?>> visibleSettings = List.of();
	private long lastShownCheck;

	private int windowX;
	/** The top of the window on screen, the point it is zoomed around; the menu's top bar lies between it and {@link #windowY}, where its body starts. */
	private int frameY;
	private int windowY;
	private int windowWidth;
	private int windowHeight;
	/** The window's height for a long page; a short one takes less, from the top down. */
	private int fullHeight;

	/** The menu's top bar: the Lumio mark, the name and version, the search in the middle and the close button; only the menu has it. */
	private static final int TOP_BAR = 30;

	/** @return how tall the top bar is: the menu's, none for the settings pane or a settings window of its own */
	private int bar() {
		return owner == null && single == null ? TOP_BAR : 0;
	}

	/** @return the whole window's height, the top bar with the body under it */
	private int fullHeightShown() {
		return windowHeight + bar();
	}
	private int contentTop;
	private int contentBottom;
	private int contentLeft;
	private int contentRight;

	private double scroll;
	private int contentHeight;
	private Control dragged;
	private Control focused;
	private boolean rebuildRequested;
	private long lastFrame = System.nanoTime();
	/** Open/close spring: 0 closed, 1 open, overshooting slightly before settling, so the window arrives as an object rather than a fade. */
	private final Spring openSpring = Spring.bouncy(Spring.GENTLE);
	/** Whether the window is closing: it shrinks back to where it came from and then disappears. */
	private boolean closing;
	/** Page transition progress from 0 (just opened) to 1: the page slides and fades in. */
	private float pageAnimation = 1f;
	private final Spring pageSpring = Spring.smooth(Spring.GENTLE);
	private float[] itemHover = new float[0];
	private final MenuFeature menu;
	/** Overall window scale, which resizing changes. */
	private float zoom = 1f;
	/** Target scale (one of the zoom steps) the window animates towards. */
	private float zoomTarget = 1f;
	private double scrollTarget;
	private WindowDrag windowDrag = WindowDrag.NONE;
	private String tooltip;
	/** The search text. While non-empty, the page shows matching settings from all pages. */
	private String query = "";
	private boolean searchFocused;
	// the window is anchored at its center, so it scales around it; where the center should be (on screen, and as a share of the screen,
	// which is what is saved) and where it is shown, both following springs
	private float centerX;
	private float centerY;
	private float positionX = 0.5f;
	private float positionY = 0.5f;
	private float shownCenterX;
	private float shownCenterY;
	private final Spring centerXSpring = Spring.bouncy(Spring.DEFAULT);
	private final Spring centerYSpring = Spring.bouncy(Spring.DEFAULT);
	private final Spring zoomSpring = Spring.smooth(Spring.DEFAULT);
	/**
	 * Scale the text and shapes are made for. While the window scales, it keeps the last settled scale and the content is scaled as one piece;
	 * the new scale is applied once when it settles, so nothing is re-made every frame and the text ends up crisp.
	 */
	private float uiZoom = 1f;
	private boolean geometryKnown;
	private int lastGuiScale;
	// dragging the window: it starts only after the mouse moved a little, so a click on the top bar still works; it follows the mouse, and
	// the snapped center is applied when it is dropped
	private boolean dragPending;
	private double dragStartX;
	private double dragStartY;
	private float grabX;
	private float grabY;
	private float snappedX;
	private float snappedY;
	/** Where the window's center was when it was picked up, for Escape to put it back. */
	private float dragFromX;
	private float dragFromY;
	/** Whether the game had the focus last frame: losing it ends a drag, as the release may never come. */
	private boolean wasActive = true;
	/** How far the mouse must move before a drag starts, in GUI pixels. */
	private static final int DRAG_THRESHOLD = 3;
	/** Where the dragged window (the menu or the floating settings window) snaps to. */
	private final WindowSnap snap = new WindowSnap();

	/** The feature this window is for when it is a single-feature window rather than the menu with its page list; otherwise null. */
	private final Feature single;

	public MacScreen() {
		this(null);
	}

	/** Selection state (Ctrl+A etc.) of the search field. */
	private final TextSelection querySelection = new TextSelection();

	/** Creates a settings window for a single feature, without the page list, or the menu with null. */
	public MacScreen(Feature single) {
		super(Component.literal(single == null ? "Lumio" : single.name().getString()));
		this.single = single;
		MenuFeature found = null;
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof MenuFeature menuFeature) {
				found = menuFeature;
			}
			if (single == null && feature.isListed() && !(feature instanceof MenuFeature)) {
				pages.add(feature);
			}
		}
		this.menu = found;
		if (single != null) {
			pages.add(single);
		} else {
			SidebarOrder.sort(pages);
			if (found != null) {
				pages.add(found);
			}
		}
	}

	/** Opens a feature's settings: in the pane inside the menu if the menu is open, otherwise in a window; closing it returns to the previous screen. */
	public static void openEdit(Feature feature) {
		Minecraft client = Minecraft.getInstance();
		Feature target = feature.isChild() ? feature.parent() : feature;
		if (client.gui.screen() instanceof MacScreen menuScreen && menuScreen.single == null && menuScreen.owner == null) {
			menuScreen.openEditor(target); // the menu stays as it is, with the settings in a pane on the right of its page
			return;
		}
		MacScreen screen = new MacScreen(target);
		screen.back = client.gui.screen();
		client.setScreenAndShow(screen);
	}

	/**
	 * Opens the menu directly on a feature's settings, in the pane beside the page that lists it, from another screen, which stays as it is: Esc leaves the menu for it, as it does anywhere in the menu. The game's own menu behind it is neither closed nor told
	 * anything.
	 */
	public static void openFrom(Feature feature) {
		Minecraft client = Minecraft.getInstance();
		Feature target = feature.isChild() ? feature.parent() : feature;
		MacScreen menu = createFrom(client.gui.screen());
		client.setScreenAndShow(menu);
		for (int i = 0; i < menu.pages.size(); i++) {
			if (lists(menu.pages.get(i), target)) {
				menu.selectPage(i);
				menu.openEditor(target);
				menu.openedOn = target;
				return;
			}
		}
		// not on any list: its settings alone, in a window over the same screen
		MacScreen window = new MacScreen(target);
		window.back = menu.back;
		client.setScreenAndShow(window);
	}

	// the settings pane inside the menu window, and, in the pane, the menu it belongs to; the pane is a MacScreen of its own that is drawn and
	// given input by the menu, in the menu's window coordinates
	private MacScreen editor;
	private MacScreen owner;
	/** The feature whose settings the menu was opened on from another screen (see {@link #openFrom(Feature)}): Esc from them closes the menu. */
	private Feature openedOn;
	/** Share of the page area the pane takes, as in the mockup, and its narrowest width. */
	private static final float PANE_SHARE = 0.45f;
	private static final int PANE_MIN_WIDTH = 300;
	/** Narrowest the page beside the pane may get (about 280 px at mockup scale); below it, the pane covers the page instead. */
	private static final int LIST_MIN_WIDTH = 190;
	/** How far the pane slides in from the right while it fades in. */
	private static final int PANE_SLIDE = 24;
	/** The close button at the top right of the pane, and the back arrow when the pane covers the page. */
	private static final int PANE_BUTTON = 16;
	/** How far the pane is shown, from 0 (closed) to 1; the page's width follows the same spring, quickly, so the rows do not crawl aside. */
	private final Spring paneSpring = Spring.smooth(Spring.SNAPPY);
	private float paneShown;
	/** Whether the pane is animating out; it is removed when it is gone. */
	private boolean paneClosing;

	/** A feature whose tab the list shows at its next build, or null. */
	private Feature tabFor;

	private void openEditor(Feature feature) {
		if (editor != null) {
			if (!paneClosing && editor.single == feature) {
				// Edit again for the feature whose pane is open closes it, like clicking a toggle button a second time
				closeEditor();
				UiSounds.play(UiSounds.Cue.CLOSE);
				return;
			}
			editor.removed();
		}
		editor = new MacScreen(feature);
		editor.owner = this;
		paneClosing = false;
		editor.init(width, height);
		showTabOf(feature);
		// switching from one feature to another slides the new settings in like a page
		editor.restartPage();
	}

	/** On a page of features in tabs, shows the tab whose list has the feature, so its row is seen beside its sheet (opened from a search or another screen): on the next build of the tabs. */
	private void showTabOf(Feature feature) {
		tabFor = feature;
		rebuildRequested = true;
	}

	/** @return the feature whose settings are open in the pane, or null; the feature lists mark its row */
	public static Feature openFeature() {
		return Minecraft.getInstance().gui.screen() instanceof MacScreen screen && screen.editor != null && !screen.paneClosing
			? screen.editor.single : null;
	}

	/** The settings pane that was open when the menu was closed, with its tab and scroll position: it is reopened with the menu. */
	private record Remembered(Feature feature, int tab) {
	}

	private static Remembered remembered;

	private void rememberEditor() {
		remembered = editor != null && !paneClosing && editor.single != null ? new Remembered(editor.single, editor.subIndex) : null;
		if (remembered != null) {
			SCROLLS.put(editor.key(), editor.scrollTarget);
			editor.rememberTab();
		}
	}

	private void rememberTab() {
		if (subTitle != null) {
			TABS.put(key(), subTitle);
		}
	}

	/** Closes the pane: it slides out and is removed once it is gone, at once with animations off. */
	private void closeEditor() {
		remembered = null; // an explicitly closed pane is not reopened with the menu
		if (editor != null && !paneClosing) {
			paneClosing = true;
			if (menu == null || !menu.animations()) {
				finishPaneClose();
			}
		}
	}

	private void finishPaneClose() {
		if (editor != null) {
			editor.removed();
			editor = null;
		}
		paneClosing = false;
		paneSpring.snap(0f);
		paneShown = 0f;
	}

	/** @return the width of the pane: its share of the page area, or all of it when it covers the page */
	private int paneWidth() {
		int area = windowWidth - side();
		int share = Math.max(PANE_MIN_WIDTH, Math.round(area * PANE_SHARE));
		return area - share < LIST_MIN_WIDTH ? area : share;
	}

	/** @return whether the pane covers the page instead of sitting beside it, because the page would be too narrow */
	private boolean paneCovers() {
		return paneWidth() == windowWidth - side();
	}

	/** @return the right edge of the page: the window's, or the pane's left edge as far as the pane is shown and docked */
	private int listRight() {
		if (editor == null || paneCovers()) {
			return windowX + windowWidth;
		}
		return Math.round(windowX + windowWidth - paneWidth() * paneShown * paneDocked);
	}

	/** @return the pane's left edge: docked, sliding in from the right as it is shown; floating, where it floats */
	private int paneX() {
		if (paneDetached()) {
			return Math.round(windowX + (paneShownX - windowX) / zoom - paneWidth() / 2f);
		}
		return Math.round(windowX + windowWidth - paneWidth() + (1f - paneShown) * PANE_SLIDE);
	}

	/** @return the pane's top edge: the window's while docked, or where it floats */
	private int paneY() {
		return paneDetached() ? Math.round(frameY + (paneShownY - frameY) / zoom - windowHeight / 2f) : windowY;
	}

	/** Gives the pane its place in the window, in the menu's window coordinates; the pane itself is not zoomed, the menu's zoom applies. */
	private void placePane() {
		editor.windowX = paneX();
		editor.windowY = paneY();
		editor.frameY = editor.windowY;
		editor.windowWidth = paneWidth();
		editor.windowHeight = windowHeight;
		editor.zoom = 1f;
		editor.zoomTarget = 1f;
		editor.applyGeometry();
	}

	/** @return whether a point (in the menu's window coordinates) lies on the pane */
	private boolean paneHits(double x, double y) {
		return x >= windowX && x < windowX + windowWidth && y >= windowY && y < windowY + windowHeight;
	}

	/** @return the window's right edge, in GUI pixels */
	public int windowRight() {
		return windowX + windowWidth;
	}

	/** @return whether a point on the screen lies on the menu's window or its pane, where a click belongs to the menu */
	public boolean covers(double screenX, double screenY) {
		double x = inWindowX(screenX);
		double y = inWindowY(screenY);
		return paneHits(x, y) || editor != null && editor.paneHits(x, y);
	}

	/** @return the event moved into the menu's window coordinates, for the pane */
	private MouseButtonEvent inWindow(MouseButtonEvent event) {
		return new MouseButtonEvent(inWindowX(event.x()), inWindowY(event.y()), event.buttonInfo());
	}

	private int paneCloseX() {
		return windowX + windowWidth - PAD - PANE_BUTTON;
	}

	private int paneButtonY() {
		return windowY + TITLE_BAR / 2 - PANE_BUTTON / 2;
	}

	/** @return whether the mouse is over the close button in the title bar of the pane or of a settings window of its own */
	private boolean overPaneClose(double mouseX, double mouseY) {
		return owner == null && single != null && !showsSheet() && mouseX >= paneCloseX() && mouseX < paneCloseX() + PANE_BUTTON && mouseY >= paneButtonY()
			&& mouseY < paneButtonY() + PANE_BUTTON;
	}

	/** @return whether this is the pane and covers the page, where the page is too narrow beside it */
	private boolean paneCoversPage() {
		return owner != null && owner.paneCovers();
	}

	private float paneCloseHover;

	/** Draws the close button of a settings window of its own. */
	private void drawPaneButtons(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int y = paneButtonY();
		paneCloseHover = ui.approach(paneCloseHover, overPaneClose(mouseX, mouseY) ? 1f : 0f, Ui.HOVER_RATE);
		ui.rounded(paneCloseX(), y, PANE_BUTTON, PANE_BUTTON, Radius.pill(PANE_BUTTON), Ui.withOpacity(palette.hover(), paneCloseHover));
		ui.icon("close", paneCloseX() + 4, y + 4, PANE_BUTTON - 8, Ui.mix(palette.textSecondary(), palette.text(), paneCloseHover));
	}

	// ------------------------------------------------------------------------------------------- the settings window off the menu window
	// The pane can be pulled off by its title bar and float as a window of its own; dropped on its docked place (or double-clicked) it goes back.
	// Where it floats is kept as the screen position of its center, like the menu window's, and moves on springs.

	/** How close the dragged pane must come to its docked place or to the menu window's side to dock when dropped, in GUI pixels. */
	private static final int DOCK_EDGE = 40;
	/** How close a docking pane must be to its place to count as arrived, in GUI pixels. */
	private static final float ARRIVED = 0.5f;
	/** The stronger shadow under a window while it is dragged: how far it reaches and how dark it gets. */
	private static final int LIFT_SHADOW = 30;
	private static final float LIFT_OPACITY = 0.35f;
	/** How fast the lift and the hint at the docked place fade in and out, per second. */
	private static final float LIFT_RATE = Ui.HOVER_RATE;
	/** The quiet hint in the middle of the docked place while a dragged pane would dock there: its text, height, side padding and background. */
	private static final String DOCK_LABEL = "Release to dock";
	private static final int DOCK_LABEL_HEIGHT = 20;
	private static final int DOCK_LABEL_PAD = 10;
	private static final int DOCK_LABEL_BACK = 0xB0101014;

	/** Whether the pane floats (as chosen; it stays in the window while it covers the page), and whether it is on its way back to its place. */
	private boolean paneFloating;
	private boolean paneSettling;
	/** Where the floating pane's center should be and where it is shown, on screen. */
	private float paneTargetX;
	private float paneTargetY;
	private float paneShownX;
	private float paneShownY;
	private final Spring paneXSpring = Spring.bouncy(Spring.DEFAULT);
	private final Spring paneYSpring = Spring.bouncy(Spring.DEFAULT);
	/** How far the pane is in its docked place, from 0 (floating) to 1: the page's width and the floating frame follow it. */
	private final Spring paneDockSpring = Spring.smooth(Spring.SNAPPY);
	private float paneDocked = 1f;
	// dragging the pane by its title bar: like the window, it starts only after the mouse moved a little
	private boolean paneDragPending;
	private boolean paneDragging;
	private double paneDragStartX;
	private double paneDragStartY;
	private float paneGrabX;
	private float paneGrabY;
	/** Whether the pane floated, and where, when it was picked up, for Escape to put it back. */
	private boolean paneFromFloating;
	private float paneFromX;
	private float paneFromY;
	/** Whether the dragged pane would dock where it is, and how far the hint at its docked place is shown. */
	private boolean dockNear;
	private float dockHint;
	/** How far the window and the pane are lifted (their stronger shadow while dragged). */
	private float windowLift;
	private float paneLift;

	/** @return whether the pane floats now: as chosen, unless it covers the page, which leaves no room for it beside the window */
	private boolean paneFloats() {
		return paneFloating && !paneCovers();
	}

	/** @return whether the pane is drawn at its own place rather than in the window: floating, or on its way back */
	private boolean paneDetached() {
		return paneFloats() || paneSettling;
	}

	/** @return the center of the pane's docked place on screen */
	private float dockCenterX() {
		return windowX + (windowWidth - paneWidth() / 2f) * zoom;
	}

	private float dockCenterY() {
		return screenY(windowY) + windowHeight * zoom / 2f;
	}

	/** Keeps the floating pane's title bar on the screen, as the window's; a pane wider than the screen is centered. */
	private void clampPane() {
		float w = paneWidth() * zoomTarget;
		float h = windowHeight * zoomTarget;
		paneTargetX = w >= width ? width / 2f : Mth.clamp(paneTargetX, w / 2f, width - w / 2f);
		paneTargetY = Mth.clamp(paneTargetY, h / 2f, Math.max(h / 2f, height - TITLE_BAR * zoomTarget + h / 2f));
	}

	/** Puts the pane where it was saved, at once: floating at its place or docked; when the menu opens and when the screen's size changes. */
	private void restorePane() {
		paneFloating = menu != null && menu.paneFloating();
		paneSettling = false;
		if (paneFloating) {
			paneTargetX = menu.panePositionX() * width;
			paneTargetY = menu.panePositionY() * height;
			clampPane();
			paneShownX = paneXSpring.snap(paneTargetX);
			paneShownY = paneYSpring.snap(paneTargetY);
		}
		paneDocked = paneDockSpring.snap(paneFloats() ? 0f : 1f);
	}

	/** Remembers whether the pane floats and where, as shares of the screen; once, when a drag ends, never per frame. */
	private void savePane() {
		if (menu != null) {
			menu.setPane(paneFloating, paneTargetX / Math.max(1, width), paneTargetY / Math.max(1, height));
			FeatureManager.saveSoon();
		}
	}

	/** The pane comes off the window: from now on it floats, starting where it is. */
	private void undock() {
		float fromX = paneSettling ? paneShownX : dockCenterX();
		float fromY = paneSettling ? paneShownY : dockCenterY();
		paneFloating = true;
		paneSettling = false;
		paneTargetX = fromX;
		paneTargetY = fromY;
		paneShownX = paneXSpring.snap(fromX);
		paneShownY = paneYSpring.snap(fromY);
		UiSounds.dock(false);
	}

	/** The pane goes back into the window: it moves to its docked place, and the page makes room for it. */
	private void dock() {
		paneFloating = false;
		paneSettling = true;
		dockNear = false;
		UiSounds.dock(true);
		forgetPanePlace();
	}

	/** Forgets where the pane floated: docked, it comes off from its docked place the next time. */
	private void forgetPanePlace() {
		if (menu != null) {
			menu.resetPane();
			FeatureManager.saveSoon();
		}
	}

	/**
	 * A press on the pane's title bar where no control took it, in the menu's window coordinates: a drag may start, and a double click docks a
	 * floating pane. @return whether the press was taken; a pane that covers the page cannot come off
	 */
	private boolean pressPaneTitle(double x, double y, boolean doubleClick) {
		if (paneCovers()) {
			return false;
		}
		if (doubleClick && paneFloating) {
			dock();
			return true;
		}
		double screenX = windowX + (x - windowX) * zoom;
		double screenY = screenY(y);
		paneDragPending = true;
		paneDragStartX = screenX;
		paneDragStartY = screenY;
		paneFromFloating = paneFloating;
		paneFromX = paneTargetX;
		paneFromY = paneTargetY;
		paneGrabX = (float) screenX - (paneDetached() ? paneShownX : dockCenterX());
		paneGrabY = (float) screenY - (paneDetached() ? paneShownY : dockCenterY());
		return true;
	}

	/**
	 * Moves the dragged pane with the mouse, pulling it off the window once the mouse moved far enough; finds where it snaps (the screen's edges
	 * and middle and the window's edges, Shift drags freely), or whether it would dock.
	 */
	private void dragPane(MouseButtonEvent event) {
		if (paneDragPending && Math.hypot(event.x() - paneDragStartX, event.y() - paneDragStartY) > DRAG_THRESHOLD) {
			paneDragPending = false;
			paneDragging = true;
			if (!paneFloating) {
				undock();
			}
		}
		if (!paneDragging) {
			return;
		}
		paneTargetX = (float) event.x() - paneGrabX;
		paneTargetY = (float) event.y() - paneGrabY;
		clampPane();
		float w = paneWidth() * zoom;
		float h = windowHeight * zoom;
		dockNear = wouldDock(event.x(), event.y(), paneTargetX - w / 2f, paneTargetY - h / 2f, w, h);
		if (dockNear || (ScreenInput.modifiers(event.modifiers()) & ScreenInput.SHIFT) != 0) {
			snap.free(paneTargetX - w / 2f, paneTargetY - h / 2f);
		} else {
			snap.find(paneTargetX - w / 2f, paneTargetY - h / 2f, w, h, width, height, true, windowX, frameY, windowWidth * zoom, fullHeight * zoom);
		}
	}

	/**
	 * @return whether the dragged pane docks when dropped where it is, on screen: the mouse or the pane's middle over its docked place (the right
	 *         part of the menu window), or the pane's side near the docked place or the window's side and level with the window for at least
	 *         half its height
	 */
	private boolean wouldDock(double mouseX, double mouseY, float left, float top, float w, float h) {
		float right = windowX + windowWidth * zoom;
		float bodyTop = screenY(windowY);
		float bottom = bodyTop + windowHeight * zoom;
		float dockLeft = right - paneWidth() * zoom;
		if (overDockPlace(mouseX, mouseY, dockLeft, right, bottom) || overDockPlace(left + w / 2f, top + h / 2f, dockLeft, right, bottom)) {
			return true;
		}
		float level = Math.min(top + h, bottom) - Math.max(top, bodyTop);
		boolean nearRight = left >= dockLeft - DOCK_EDGE && left <= right + DOCK_EDGE;
		boolean nearLeft = Math.abs(left + w - windowX) <= DOCK_EDGE;
		return level >= h / 2f && (nearRight || nearLeft);
	}

	private boolean overDockPlace(double x, double y, float dockLeft, float right, float bottom) {
		return x >= dockLeft && x < right && y >= screenY(windowY) && y < bottom;
	}

	/** Drops the dragged pane: it docks if it is near its place, otherwise it springs to where it snaps and stays there. */
	private void dropPane() {
		boolean moved = paneDragging;
		paneDragPending = false;
		paneDragging = false;
		if (!moved) {
			return;
		}
		if (dockNear) {
			dock();
			return;
		}
		paneTargetX = snap.left + paneWidth() * zoom / 2f;
		paneTargetY = snap.top + windowHeight * zoom / 2f;
		clampPane();
		savePane();
	}

	/**
	 * Moves the floating pane on its springs (directly with the mouse while it is dragged) and lets a docking pane arrive; fades the lift and the
	 * hint at the docked place. Every frame, after the window moved.
	 */
	private void animatePane(Ui ui) {
		paneDocked = paneDockSpring.follow(ui, paneFloats() ? 0f : 1f);
		if (paneSettling) {
			paneTargetX = dockCenterX();
			paneTargetY = dockCenterY();
		}
		if (paneDragging || windowDrag == WindowDrag.MOVE) {
			paneShownX = paneXSpring.snap(paneTargetX);
			paneShownY = paneYSpring.snap(paneTargetY);
		} else if (paneDetached()) {
			paneShownX = paneXSpring.follow(ui, paneTargetX);
			paneShownY = paneYSpring.follow(ui, paneTargetY);
		}
		if (paneSettling && Math.abs(paneShownX - paneTargetX) < ARRIVED && Math.abs(paneShownY - paneTargetY) < ARRIVED && !paneXSpring.moving()
			&& !paneYSpring.moving()) {
			paneSettling = false;
		}
		dockHint = ui.approach(dockHint, paneDragging && dockNear ? 1f : 0f, LIFT_RATE);
		windowLift = ui.approach(windowLift, windowDrag == WindowDrag.MOVE ? 1f : 0f, LIFT_RATE);
		paneLift = ui.approach(paneLift, paneDragging ? 1f : 0f, LIFT_RATE);
	}

	/** @return whether a window is pressed to be dragged or is being dragged */
	private boolean dragActive() {
		return dragPending || windowDrag == WindowDrag.MOVE || paneDragPending || paneDragging;
	}

	/**
	 * Ends a drag whose release never came, as if it had been dropped: the button is up (released while the screen did not get it) or the game
	 * lost the focus. Every frame, but it asks for the button only while a drag runs.
	 */
	private void endLostDrag() {
		boolean active = minecraft.isWindowActive();
		boolean focusLost = wasActive && !active;
		wasActive = active;
		if (dragActive() && (focusLost || !leftButtonDown(minecraft))) {
			if (paneDragPending || paneDragging) {
				dropPane();
			}
			dragPending = false;
			if (windowDrag == WindowDrag.MOVE) {
				dropWindow();
			}
		}
	}

	/**
	 * @return whether the left button is down now, as the window system reports it: the game's own flag covers only the game without a screen,
	 *         and the screen's events miss a release that went elsewhere
	 */
	private static boolean leftButtonDown(Minecraft client) {
		//#if 26.3
		//$ return (org.lwjgl.sdl.SDLMouse.SDL_GetGlobalMouseState((java.nio.FloatBuffer) null, null) & org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LMASK) != 0;
		//#else
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().handle(), InputConstants.MOUSE_BUTTON_LEFT) == InputConstants.PRESS;
		//#endif
	}

	/** Drops the dragged window: it springs to where it snaps and keeps that place. */
	private void dropWindow() {
		windowDrag = WindowDrag.NONE;
		float fromX = centerX;
		float fromY = centerY;
		centerX = snappedX;
		centerY = snappedY;
		clampCenter();
		if (editor != null && paneFloats()) {
			// the floating pane stays with the window, and keeps the place it has now
			paneTargetX += centerX - fromX;
			paneTargetY += centerY - fromY;
			clampPane();
			menu.setPane(paneFloating, paneTargetX / Math.max(1, width), paneTargetY / Math.max(1, height));
		}
		savePosition();
	}

	/** Escape during a drag: the dragged window goes back to where it was picked up, a pane pulled off goes back into the window, nothing is saved. */
	private void cancelDrags() {
		if (paneDragging) {
			if (paneFromFloating) {
				paneTargetX = paneFromX;
				paneTargetY = paneFromY;
			} else {
				paneFloating = false;
				paneSettling = true;
			}
		}
		if (windowDrag == WindowDrag.MOVE) {
			centerX = dragFromX;
			centerY = dragFromY;
			clampCenter();
		}
		clearDrags();
		// the lift eases back as after a drop; the hint of a dock that will not happen goes at once
		dockHint = 0f;
	}

	/** Forgets any drag at once: when the menu opens, comes back or closes, and when the screen's size changes. */
	private void clearDrags() {
		dragPending = false;
		windowDrag = WindowDrag.NONE;
		paneDragPending = false;
		paneDragging = false;
		dockNear = false;
		snap.free(0f, 0f);
	}

	/**
	 * Draws the hint in the middle of the pane's docked place while a dragged pane would dock there, in the menu's window coordinates: only a quiet
	 * label, without a line or a colored area, so a drop is not a surprise.
	 */
	private void drawDockHint(Ui ui, float fade) {
		if (dockHint <= 0.01f) {
			return;
		}
		int labelW = Math.round(ui.boldWidthScaled(DOCK_LABEL, TextScale.BODY)) + 2 * DOCK_LABEL_PAD;
		int labelX = windowX + windowWidth - paneWidth() + (paneWidth() - labelW) / 2;
		int labelY = windowY + (windowHeight - DOCK_LABEL_HEIGHT) / 2;
		ui.rounded(labelX, labelY, labelW, DOCK_LABEL_HEIGHT, Radius.box(DOCK_LABEL_HEIGHT), Ui.withOpacity(DOCK_LABEL_BACK, dockHint * fade));
		ui.textAtScaledBold(DOCK_LABEL, labelX + DOCK_LABEL_PAD, labelY + DOCK_LABEL_HEIGHT / 2f - 4f, TextScale.BODY, Ui.withOpacity(0xFFFFFFFF, dockHint * fade));
	}

	/**
	 * Draws the floating pane's own window behind its content: its shadow (stronger while dragged) and its glass or solid body; the edge goes
	 * over the content. @return whether the solid body was drawn in one pass
	 */
	private boolean drawPaneBody(Ui ui, float fade) {
		int x = paneX();
		int y = paneY();
		int w = paneWidth();
		if (paneLift > 0.01f) {
			ui.shadow(x, y, w, windowHeight, Radius.XXL, LIFT_SHADOW, LIFT_OPACITY * paneLift * fade);
		}
		return frameBody(ui, x, y, w, windowHeight, fade);
	}

	private int side() {
		return single != null ? 0 : SIDEBAR;
	}

	/** @return the key under which this page's scroll position and expanded groups are stored */
	private int key() {
		if (subPage != null) {
			return SUB_PAGE_KEYS + (subPage.id().hashCode() & (SUB_PAGE_KEYS - 1));
		}
		return single != null ? 1000 + FeatureManager.features().indexOf(single) : selectedIndex;
	}

	/** The screen closing the menu returns to (e.g. the server list), or null to return to the game. */
	private Screen back;

	/** Opens the menu from a screen; closing it returns to that screen. */
	public static void openFrom(Screen from) {
		if (from instanceof MacTitleScreen title) {
			// let the main menu animate out first
			title.leaveTo(() -> createFrom(from));
			return;
		}
		Minecraft.getInstance().gui.setScreen(createFrom(from));
	}

	private static MacScreen createFrom(Screen from) {
		MacScreen screen = new MacScreen();
		screen.back = from;
		return screen;
	}

	@Override
	public void onClose() {
		if (owner != null) {
			owner.closeEditor();
			return;
		}
		if (closing) {
			return;
		}
		rememberEditor();
		if (menu != null && menu.animations()) {
			// animate back out the way it came, then close
			closing = true;
			UiSounds.play(UiSounds.Cue.CLOSE);
			return;
		}
		minecraft.gui.setScreen(back);
	}

	/** Group the page should scroll to when shown: the name of a child feature on the page. */
	private static String pendingSection;

	/** Makes the menu open on the feature's page next time. */
	public static void select(Feature feature) {
		int index = 0;
		for (Feature candidate : FeatureManager.features()) {
			if (!candidate.isListed() || candidate instanceof MenuFeature) {
				continue;
			}
			if (candidate == feature) {
				selectedIndex = index;
				return;
			}
			index++;
		}
		selectedIndex = feature instanceof MenuFeature ? index : 0;
	}

	private Feature page() {
		if (subPage != null) {
			return subPage;
		}
		if (single != null) {
			return single;
		}
		Feature listed = listedPage();
		return listed instanceof FeatureListFeature list && list.direct() != null ? list.direct() : listed;
	}

	/** @return the page chosen in the sidebar, also while one of its sub-pages is shown */
	private Feature listedPage() {
		selectedIndex = Mth.clamp(selectedIndex, 0, Math.max(0, pages.size() - 1));
		return pages.get(selectedIndex);
	}

	// ------------------------------------------------------------------------------------------------------- history
	// Back and forward through what the menu showed, as in a browser (mouse buttons 4 and 5, Alt + Left and Right): the page chosen, its
	// sub-page or sheet, the feature whose settings are in the pane and the pane's own sheet. Kept by the menu while it is open.

	/** A place in the menu: the page, its sub-page or sheet, the feature in the pane and the pane's sheet (null where there is none). */
	private record Place(int page, Feature sub, Feature pane, Feature paneSub) {
	}

	private final MenuHistory<Place> history = new MenuHistory<>();
	/** Whether the place shown was reached by going back or forward: it settles as that step instead of becoming a new one. */
	private boolean travelled;

	/** @return the place shown now, or null while a search or an animation of the pane leaves it open */
	private Place placeNow() {
		if (searching() || paneClosing && editor != null) {
			return null;
		}
		boolean pane = editor != null;
		return new Place(selectedIndex, subPage, pane ? editor.single : null, pane ? editor.subPage : null);
	}

	/** Notes where the menu is, once per frame: a new step when it moved on, the step itself when going back or forward led here. */
	private void trackHistory() {
		Place now = placeNow();
		if (now == null) {
			return;
		}
		if (travelled) {
			travelled = false;
			history.replace(now);
		} else {
			history.visit(now);
		}
	}

	/** Goes one step back or forward in the history, if there is one. @return whether it moved */
	private boolean travel(boolean forward) {
		trackHistory();
		Place place = forward ? history.forward() : history.back();
		if (place == null) {
			return false;
		}
		show(place);
		travelled = true;
		return true;
	}

	/** Shows a place of the history: its page and sub-page, and the pane with its sheet, or no pane. */
	private void show(Place place) {
		if (searching()) {
			query = "";
			queryChanged();
		}
		if (place.page() != selectedIndex && place.page() < pages.size()) {
			selectPage(place.page());
		}
		if (place.sub() != subPage) {
			if (place.sub() == null) {
				closeSubPage();
			} else {
				showSubPage(place.sub());
			}
		}
		if (place.pane() == null) {
			if (editor != null && !paneClosing) {
				closeEditor();
				UiSounds.play(UiSounds.Cue.CLOSE);
			}
			return;
		}
		if (editor == null || paneClosing || editor.single != place.pane()) {
			openEditor(place.pane());
		}
		if (editor != null && place.paneSub() != editor.subPage) {
			if (place.paneSub() == null) {
				editor.closeSubPage();
			} else {
				editor.showSubPage(place.paneSub());
			}
		}
	}

	/** @return whether a key press is Alt + Left or Alt + Right, the keyboard's back and forward */
	private static boolean historyKey(KeyEvent event, boolean forward) {
		return (ScreenInput.modifiers(event.modifiers()) & ScreenInput.ALT) != 0 && event.key() == (forward ? InputConstants.KEY_RIGHT : InputConstants.KEY_LEFT);
	}

	// ------------------------------------------------------------------------------------------------------- sub-pages
	// A long page is a short overview whose rows open sub-pages in the same window: a sub-page takes the place of the page's settings, with a
	// back button and the way back in its header ("Music › Lyrics"), and the page's preview stays beside it. Esc or the back button returns.

	/** The sub-page shown in place of the chosen page's settings, or null for the page itself. */
	private Feature subPage;
	/** The feature's own page a sheet lies over: the sheet goes back there, and the page's header and preview stay beside it. */
	private Feature underSheet;
	/** Scroll keys of sub-pages start here, apart from the pages' and the settings windows'. */
	private static final int SUB_PAGE_KEYS = 1 << 20;
	/** The back button of a sub-page's header: its size, and where it was drawn for clicks (with the page's name after it). */
	private static final int BACK_BUTTON = 22;
	/** The chevron between the page's name and the sub-page's title, and the gaps around it. */
	private static final int CRUMB_GAP = 8;

	/**
	 * Opens a sub-page of what is shown: in the settings pane while there is one, otherwise in the page or the settings window; without the menu it opens as a settings window.
	 */
	public static void openSubPage(Feature sub) {
		Minecraft client = Minecraft.getInstance();
		if (!(client.gui.screen() instanceof MacScreen menu)) {
			openEdit(sub);
		} else if (menu.editor != null && !menu.paneClosing) {
			// asked for from the pane: it opens there, over the feature's settings
			menu.editor.showSubPage(sub);
		} else {
			menu.showSubPage(sub);
		}
	}

	private void showSubPage(Feature sub) {
		if (sub == subPage) {
			return;
		}
		SCROLLS.put(key(), scrollTarget);
		clearQuery();
		// a sheet opens over the page as one cross-fade; a sub-page of its own is entered like a page
		boolean sheet = sub.isSheet() || single != null;
		if (sheet) {
			tabDirection = 0;
			keepOutgoing(groups, false);
		}
		underSheet = sub.isSheet() ? ownFeature() : null;
		subPage = sub;
		enterPage(!sheet);
	}

	/** Goes back from a sub-page to its page, scrolled where it was. @return whether a sub-page was shown */
	private boolean closeSubPage() {
		if (subPage == null) {
			return false;
		}
		SCROLLS.put(key(), scrollTarget);
		boolean sheet = showsSheet();
		if (sheet) {
			tabDirection = 0;
			keepOutgoing(groups, false);
		}
		subPage = underSheet;
		underSheet = null;
		enterPage(!sheet);
		return true;
	}

	/** Shows the page or sub-page now chosen: at its scroll position, with a tick; sliding in, unless a sheet opens or closes over the page. */
	private void enterPage(boolean replay) {
		scroll = SCROLLS.getOrDefault(key(), 0.0);
		scrollTarget = scroll;
		if (replay) {
			restartPage();
		}
		UiSounds.play(UiSounds.Cue.TICK);
		buildPage();
	}

	/** @return the feature a sidebar page is, also under a sheet; null for another page */
	private Feature ownFeature() {
		Feature own = underSheet;
		if (own == null && single == null && listedPage() instanceof FeatureListFeature list) {
			own = list.direct();
		}
		return own != null && own.ownPage() && !searching() ? own : null;
	}

	/** @return where a page starts */
	private int pageLeft() {
		return windowX + side() + PAD;
	}

	/** Where a sheet over a sidebar page that is one feature starts, under the page's header. */
	private int sheetTop() {
		return underSheet != null ? OWN_SHEET_TOP : 0;
	}

	/** Draws the header of a sidebar page that is one feature: its icon, its title and its description under them, and its switch at the top right. */
	private void drawOwnHeader(Ui ui, Feature own, int right, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int left = pageLeft();
		int top = windowY + HEADER_TILE_TOP + (HEADER_TILE - BACK_BUTTON) / 2;
		ui.iconTile(own.iconName(), left, windowY + HEADER_TILE_TOP, HEADER_TILE, HEADER_ICON, Radius.MD, 0xFF000000 | own.iconRgb(), 0xFFFFFFFF);
		int textX = left + HEADER_TILE + 9;
		int textEnd = own.isSwitchable() ? drawPageSwitch(ui, own, right, windowY + HEADER_TILE_TOP + (HEADER_TILE - PAGE_SWITCH_HEIGHT) / 2, "On", mouseX, mouseY) - CRUMB_GAP : right;
		if (!own.isSwitchable()) {
			pageSwitchX = -1;
		}
		float ownScale = titleScale(ui, own.nameText(), textEnd - textX);
		ui.textAtScaledTitle(ui.fit(own.nameText(), Math.max(20, Math.round((textEnd - textX) / (ownScale * BOLD_WIDER)))), textX, top + BACK_BUTTON / 2 - ownScale * TITLE_HALF, ownScale,
			palette.text());
		ui.textAtScaled(ui.fitScaled(own.descriptionText(), textEnd - textX, TextScale.SMALL), textX, top + BACK_BUTTON + 2, TextScale.SMALL, palette.textSecondary());
	}

	/** Draws a sub-page's header: the back button with the page's name, and the sub-page's title after it. */
	private void drawSubPageHeader(Ui ui, int left, int right, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int middle = windowY + HEADER_TILE_TOP + HEADER_TILE / 2;
		int chip = drawBackChip(ui, listedPage().nameText(), left, middle, (right - left) / 2, true, mouseX, mouseY);
		int titleX = left + chip + BACK_CHIP_TITLE_GAP;
		String title = ui.fit(headerTitle, Math.max(20, Math.round((right - titleX) / (TextScale.TITLE * BOLD_WIDER))));
		ui.textAtScaledTitle(title, titleX, middle - TextScale.TITLE * TITLE_HALF, TextScale.TITLE, palette.text());
	}

	// a sheet lies over the page's column, with its back button and title at the top; the sidebar and the page's preview stay beside it
	private static final int SHEET_MARGIN = 8;
	private static final int SHEET_PAD = 10;
	/** Room right of the rows, for the scroll bar. */
	private static final int SHEET_RIGHT = 16;
	private static final int SHEET_TITLE_Y = 20;
	private static final int SHEET_LINE_Y = 42;
	private static final int SHEET_DONE_HEIGHT = 18;

	private boolean showsSheet() {
		// in the pane or a settings window any sub-page is a sheet over the feature's settings: Done takes it back
		return subPage != null && (subPage.isSheet() || single != null) && !searching();
	}

	/** @return how much of its light the sheet shown has: it fades in over the page it opens on */
	private float sheetLight() {
		return outgoing != null && outgoing.sheet == null && tabSwitch < 1f ? eased(tabSwitch) : 1f;
	}

	/** Draws the surface of the sheet shown, behind its title and rows. */
	private void drawSheetSurface(Ui ui) {
		float before = Ui.alpha();
		Ui.setAlpha(before * sheetLight());
		drawSheetSurface(ui, sheetTop());
		Ui.setAlpha(before);
	}

	/** Draws the sheet shown before, with its title, while it fades out over the page it closed on. */
	private void drawOutgoingSheet(Ui ui) {
		float before = Ui.alpha();
		Ui.setAlpha(before * (1f - eased(tabSwitch)));
		drawSheetSurface(ui, outgoing.sheetTop);
		drawSheetHeader(ui, outgoing.sheet, outgoing.sheetTop, -1, -1);
		Ui.setAlpha(before);
	}

	private void drawSheetSurface(Ui ui, int sheetTop) {
		Palette palette = ui.palette;
		int left = sheetLeft();
		int top = sheetTopY(sheetTop);
		int width = sheetWidth();
		int height = sheetHeight(sheetTop);
		ui.shadow(left, top, width, height, Radius.XL, SHADOW_SPREAD_SHEET, SHADOW_OPACITY_SHEET);
		ui.rounded(left, top, width, height, Radius.XL, palette.popup());
		ui.ring(left, top, width, height, Radius.XL, 1, palette.popupBorder());
	}

	private int sheetLeft() {
		return contentLeft - SHEET_PAD;
	}

	private int sheetTopY(int sheetTop) {
		return windowY + SHEET_MARGIN + sheetTop;
	}

	private int sheetWidth() {
		return contentRight - previewRoom() + SHEET_RIGHT - sheetLeft();
	}

	private int sheetHeight(int sheetTop) {
		return windowHeight - 2 * SHEET_MARGIN - sheetTop;
	}

	/** Goes back one step for a click beside the sheet shown, or on the page beside the pane. @return whether it went back */
	private boolean clickBesideGoesBack(double x, double y) {
		boolean inPage = x >= windowX + side() && x < windowX + windowWidth && y >= windowY && y < windowY + windowHeight;
		if (!inPage) {
			return false;
		}
		if (showsSheet() && sheetLight() >= 1f && !overSheet(x, y)) {
			return closeSubPage();
		}
		if (owner == null && editor != null && !paneClosing && x < listRight()) {
			return editor.stepBack();
		}
		return false;
	}

	/** @return whether a point lies on the sheet shown */
	private boolean overSheet(double x, double y) {
		int top = sheetTopY(sheetTop());
		return x >= sheetLeft() && x < sheetLeft() + sheetWidth() && y >= top && y < top + sheetHeight(sheetTop());
	}

	private static final int SHADOW_SPREAD_SHEET = 14;
	private static final float SHADOW_OPACITY_SHEET = 0.45f;

	/** Draws the back button and title of the sheet shown, and the line under them. */
	private void drawSheetHeader(Ui ui, int mouseX, int mouseY) {
		float before = Ui.alpha();
		Ui.setAlpha(before * sheetLight());
		drawSheetHeader(ui, subPage, sheetTop(), mouseX, mouseY);
		Ui.setAlpha(before);
	}

	private void drawSheetHeader(Ui ui, Feature sheet, int sheetTop, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int right = contentRight - previewRoom();
		int titleY = windowY + SHEET_TITLE_Y + sheetTop;
		int middle = titleY + SHEET_DONE_HEIGHT / 2;
		// the way back first, with where it goes, then the sheet's title, a step smaller where it would not fit
		int chip = drawBackChip(ui, sheetParent(), contentLeft, middle, (right - contentLeft) / 2, sheet == subPage, mouseX, mouseY);
		int titleX = contentLeft + chip + BACK_CHIP_TITLE_GAP;
		int sheetRoom = right - titleX;
		float sheetScale = titleScale(ui, sheet.nameText(), sheetRoom);
		ui.textAtScaledTitle(ui.fit(sheet.nameText(), Math.round(sheetRoom / (sheetScale * BOLD_WIDER))), titleX, middle - sheetScale * TITLE_HALF, sheetScale, palette.text());
		ui.textAtScaled(ui.fitScaled(sheet.descriptionText(), right - contentLeft, TextScale.SMALL), contentLeft, windowY + SHEET_LINE_Y + sheetTop, TextScale.SMALL, palette.textSecondary());
	}

	// the back button of a sheet, a sub-page and the settings pane: a small box with a chevron and the name of where it goes back to ("‹ Music");
	// it is their only way out besides Esc and a click beside them, so they have no close button or Done
	private static final int BACK_CHIP_HEIGHT = 16;
	private static final int BACK_CHIP_PAD = 6;
	private static final int BACK_CHIP_CHEVRON = 7;
	private static final int BACK_CHIP_GAP = 4;
	/** The gap between the back button and the title after it. */
	private static final int BACK_CHIP_TITLE_GAP = 8;
	/** Where the back button shown was drawn, for clicks; -1 while none is shown. */
	private int backChipX = -1;
	private int backChipY;
	private int backChipWidth;
	private float backChipHover;

	/**
	 * Draws a back button, its name shortened to the room it has.
	 *
	 * @param live whether it is the button shown, which takes clicks, rather than one fading out with its sheet
	 * @return its width
	 */
	private int drawBackChip(Ui ui, String label, int x, int middle, int room, boolean live, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int inside = 2 * BACK_CHIP_PAD + BACK_CHIP_CHEVRON + BACK_CHIP_GAP;
		String shown = ui.fitScaled(label, Math.max(12, room - inside), TextScale.SMALL);
		int width = Math.round(ui.widthScaled(shown, TextScale.SMALL)) + inside;
		int y = middle - BACK_CHIP_HEIGHT / 2;
		float hover = 0f;
		if (live) {
			backChipX = x;
			backChipY = y;
			backChipWidth = width;
			backChipHover = ui.approach(backChipHover, overBackChip(mouseX, mouseY) ? 1f : 0f, Ui.HOVER_RATE);
			hover = backChipHover;
		}
		ui.rounded(x, y, width, BACK_CHIP_HEIGHT, Radius.SM, Ui.mix(palette.control(), palette.hover(), hover));
		ui.ring(x, y, width, BACK_CHIP_HEIGHT, Radius.SM, 1, palette.cardBorder());
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x + BACK_CHIP_PAD + BACK_CHIP_CHEVRON / 2f, middle);
		// the chevron points down in its picture: a quarter turn makes it point back
		pose.rotate((float) (Math.PI / 2.0));
		ui.icon("chevron", -BACK_CHIP_CHEVRON / 2, -BACK_CHIP_CHEVRON / 2, BACK_CHIP_CHEVRON, Ui.mix(palette.textSecondary(), palette.text(), hover));
		pose.popMatrix();
		ui.textAtScaled(shown, x + BACK_CHIP_PAD + BACK_CHIP_CHEVRON + BACK_CHIP_GAP, middle - TextScale.SMALL * 4f, TextScale.SMALL,
			Ui.mix(palette.textSecondary(), palette.text(), 0.4f + 0.6f * hover));
		return width;
	}

	/** @return whether the point is on the back button shown */
	private boolean overBackChip(double mouseX, double mouseY) {
		return backChipX >= 0 && mouseX >= backChipX && mouseX < backChipX + backChipWidth && mouseY >= backChipY && mouseY < backChipY + BACK_CHIP_HEIGHT;
	}

	/** @return the name of where a sheet shown goes back to: the feature it lies over, or the page */
	private String sheetParent() {
		if (underSheet != null) {
			return underSheet.nameText();
		}
		return single != null ? single.nameText() : listedPage().nameText();
	}

	/**
	 * Goes back one step from what is shown: from a sub-page or a sheet to its page, from the settings pane to the page beside it.
	 *
	 * @return whether there was a step to go back
	 */
	private boolean stepBack() {
		if (closeSubPage()) {
			return true;
		}
		if (owner != null) {
			owner.closeEditor();
			UiSounds.play(UiSounds.Cue.CLOSE);
			return true;
		}
		return false;
	}

	/** Half the height of a title's letters at scale 1, to center it on the back button. */
	private static final float TITLE_HALF = 4.5f;

	private boolean opened;

	@Override
	protected void init() {
		Ui.clearCaches();
		clearDrags();
		if (!opened) {
			opened = true;
			subTitle = TABS.get(key());
			KeySetting.refreshConflicts();
			openSpring.snap(0f);
			if (owner == null) {
				UiSounds.play(UiSounds.Cue.OPEN);
			}
		}
		// fixed logical window size: on small screens (large GUI scale) the whole window is scaled down instead of squeezed
		windowWidth = wantedWidth();
		fullHeight = wantedHeight();
		windowHeight = fullHeight - bar();
		int guiScale = minecraft.getWindow().getGuiScale();
		if (owner != null) {
			owner.placePane();
		} else {
			zoomTarget = targetZoom();
			if (single == null && menu != null && !geometryKnown) {
				loadPosition();
			}
			centerX = positionX * width;
			centerY = positionY * height;
			clampCenter();
			if (!geometryKnown) {
				zoom = zoomSpring.snap(zoomTarget);
				shownCenterX = centerXSpring.snap(centerX);
				shownCenterY = centerYSpring.snap(centerY);
				uiZoom = zoomTarget;
				geometryKnown = true;
			} else {
				// the game window or the GUI scale changed: the window starts where and as large as it was on screen and animates to its new fit
				if (lastGuiScale != guiScale) {
					zoom = zoomSpring.snap(zoom * lastGuiScale / guiScale);
					uiZoom = zoom;
				}
				shownCenterX = centerXSpring.snap(Mth.clamp(shownCenterX, 0f, width));
				shownCenterY = centerYSpring.snap(Mth.clamp(shownCenterY, 0f, height));
			}
			placeWindow();
			if (single == null) {
				restorePane();
			}
		}
		lastGuiScale = guiScale;
		applyGeometry();
		scrollTarget = SCROLLS.getOrDefault(key(), 0.0);
		scroll = scrollTarget;
		buildPage();
		if (editor != null) {
			editor.init(width, height);
		} else if (owner == null && single == null && remembered != null && !closing && hostsPane(page())) {
			// reopen the pane that was open when the menu was closed, on its tab, already open
			Remembered was = remembered;
			remembered = null;
			openEditor(was.feature());
			editor.subIndex = was.tab();
			editor.subTitle = null;
			editor.buildPage();
			paneSpring.snap(1f);
			paneShown = 1f;
		}
	}

	/**
	 * @return the width the window wants: the pane's; a settings window's; or the menu's, the same on every page, so that nothing but the player's own drag or the Window size setting ever moves or resizes it
	 */
	private int wantedWidth() {
		if (owner != null) {
			return owner.paneWidth();
		}
		if (single != null) {
			return SETTINGS_WINDOW_WIDTH;
		}
		return MenuLayout.width(width);
	}

	/** @return the height the window wants */
	private int wantedHeight() {
		return MenuLayout.height(height);
	}

	private void loadPosition() {
		positionX = menu.windowPositionX();
		positionY = menu.windowPositionY();
	}

	/** Updates the values that depend on the window's position. */
	private void applyGeometry() {
		contentRight = listRight() - PAD - 8;
		contentLeft = pageLeft();
	}

	/** @return the largest scale at which the window fits on the screen */
	private float fitZoom() {
		return MenuLayout.fit(width, height, wantedWidth(), wantedHeight());
	}

	/** Keeps the window's top bar on screen at its target size: the window may hang off the bottom, never off the sides or the top. */
	private void clampCenter() {
		float w = wantedWidth() * zoomTarget;
		float h = wantedHeight() * zoomTarget;
		centerX = w >= width ? width / 2f : Mth.clamp(centerX, w / 2f, width - w / 2f);
		centerY = Mth.clamp(centerY, h / 2f, Math.max(h / 2f, height - TITLE_BAR * zoomTarget + h / 2f));
	}

	/** Places the window around its shown center at its shown scale. */
	private void placeWindow() {
		windowX = Math.round(shownCenterX - windowWidth * zoom / 2f);
		frameY = Math.round(shownCenterY - fullHeight * zoom / 2f);
		windowY = frameY + bar();
	}

	/**
	 * Moves the window's scale and center toward their targets on springs (instant with animations off), following the mouse directly while it
	 * is dragged. The scale for text and shapes changes only once the window settled.
	 */
	private void animateGeometry(Ui ui) {
		if (owner == null && single == null) {
			windowWidth = wantedWidth();
			fullHeight = wantedHeight();
			windowHeight = fullHeight - bar();
		}
		// the size can change from the slider in the menu settings, while the window is open
		float wanted = targetZoom();
		if (wanted != zoomTarget) {
			zoomTarget = wanted;
			clampCenter();
		}
		zoom = zoomSpring.follow(ui, zoomTarget);
		if (windowDrag == WindowDrag.MOVE) {
			shownCenterX = centerXSpring.snap(centerX);
			shownCenterY = centerYSpring.snap(centerY);
		} else {
			shownCenterX = centerXSpring.follow(ui, centerX);
			shownCenterY = centerYSpring.follow(ui, centerY);
		}
		if (zoom == zoomTarget) {
			uiZoom = zoomTarget;
		}
		placeWindow();
	}

	/** Moves the window back to the middle of the screen, animated, and remembers that. */
	private void centerOnScreen() {
		centerX = width / 2f;
		centerY = height / 2f;
		clampCenter();
		savePosition();
	}

	/** Remembers where the window is as a share of the screen; the menu's own position is saved in its settings, once, not per frame. */
	private void savePosition() {
		positionX = centerX / Math.max(1, width);
		positionY = centerY / Math.max(1, height);
		if (single == null && owner == null && menu != null) {
			menu.setWindowPosition(positionX, positionY);
			FeatureManager.saveSoon();
		}
	}

	/** @return whether a point lies where the window can be dragged: its top bar or the empty part of the page header */
	private boolean inDragZone(double mouseX, double mouseY) {
		if (owner != null || mouseX < windowX || mouseX >= windowX + windowWidth || mouseY < frameY) {
			return false;
		}
		if (bar() > 0 && mouseY < windowY) {
			return !overSearch(mouseX, mouseY) && !overMenuClose(mouseX, mouseY);
		}
		return mouseY < windowY + TITLE_BAR && bar() == 0 || single == null && mouseX >= windowX + side() && mouseX < listRight() && mouseY < contentTop - 2;
	}

	/**
	 * Moves the dragged window with the mouse and finds where it snaps when dropped: the screen's edges and middle, and the floating settings
	 * window's edges; Shift drags freely.
	 */
	private void dragTo(double rawX, double rawY, boolean free) {
		float fromX = centerX;
		float fromY = centerY;
		centerX = (float) rawX - grabX;
		centerY = (float) rawY - grabY;
		clampCenter();
		// a floating pane belongs to the window: it moves with it, in the same frame, as one
		if (editor != null && paneFloats()) {
			paneTargetX += centerX - fromX;
			paneTargetY += centerY - fromY;
		}
		float halfW = windowWidth * zoomTarget / 2f;
		float halfH = windowHeight * zoomTarget / 2f;
		if (free) {
			snap.free(centerX - halfW, centerY - halfH);
		} else {
			boolean other = editor != null && !paneClosing && paneFloating;
			float paneW = paneWidth() * zoom;
			float paneH = windowHeight * zoom;
			snap.find(centerX - halfW, centerY - halfH, 2f * halfW, 2f * halfH, width, height, other, paneTargetX - paneW / 2f, paneTargetY - paneH / 2f,
				paneW, paneH);
		}
		snappedX = snap.left + halfW;
		snappedY = snap.top + halfH;
	}

	/** @return the scale of the default size: the mockup size where it fits, otherwise as large as fits */
	private float baseZoom() {
		if (owner != null || single != null) {
			return Math.min(1f, fitZoom());
		}
		return Math.min(fitZoom(), MenuLayout.autoZoom(width, wantedWidth()));
	}

	/** @return the chosen size of the window */
	private float shownSize() {
		if (menu == null) {
			return 1f;
		}
		return menu.windowSize();
	}

	/** @return the scale the window should have: the chosen window size of the default size, as far as the screen fits it */
	private float targetZoom() {
		return Math.max(MIN_ZOOM, Math.min(fitZoom(), baseZoom() * shownSize()));
	}

	/**
	 * @return the smallest window size the screen allows, as a share of the default size, on the slider's steps: the menu's text must not get
	 *         smaller than {@link #MIN_REAL_SCALE} real pixels per menu pixel
	 */
	public static double smallestWindowSize() {
		var window = Minecraft.getInstance().getWindow();
		int guiScale = Math.max(1, window.getGuiScale());
		float base = MenuLayout.defaultZoom(window.getGuiScaledWidth(), window.getGuiScaledHeight());
		double needed = MIN_REAL_SCALE / (guiScale * base);
		return Math.ceil(needed / SIZE_STEP - 1e-4) * SIZE_STEP;
	}

	/** @return the largest window size that still fits the screen, as a share of the default size, on the slider's steps: above it the window would not grow */
	public static double largestWindowSize() {
		var window = Minecraft.getInstance().getWindow();
		int screenWidth = window.getGuiScaledWidth();
		int screenHeight = window.getGuiScaledHeight();
		float base = MenuLayout.defaultZoom(screenWidth, screenHeight);
		float fit = MenuLayout.fit(screenWidth, screenHeight, MenuLayout.width(screenWidth), MenuLayout.height(screenHeight));
		return Math.max(1.0, Math.floor(fit / base / SIZE_STEP + 1e-4) * SIZE_STEP);
	}

	/**
	 * Changes the chosen window size by steps, from Ctrl + wheel or Ctrl + minus/plus, scaling around a point on screen (the mouse, or the
	 * window's center) so that point stays where it is.
	 */
	private void resizeBy(int steps, double aroundX, double aroundY) {
		if (menu == null || owner != null) {
			return;
		}
		setWindowSize(shownSize() + steps * SIZE_STEP, aroundX, aroundY);
	}

	private void setWindowSize(float size, double aroundX, double aroundY) {
		float before = zoomTarget;
		menu.setWindowSize(size);
		zoomTarget = targetZoom();
		if (zoomTarget == before) {
			return;
		}
		float factor = zoomTarget / before;
		centerX = (float) (aroundX + (centerX - aroundX) * factor);
		centerY = (float) (aroundY + (centerY - aroundY) * factor);
		clampCenter();
		savePosition();
	}

	private double inWindowX(double screenX) {
		return windowX + (screenX - windowX) / zoom;
	}

	/** @return where a point of the window (in its own coordinates) is on screen, vertically */
	private float screenY(double y) {
		return (float) (frameY + (y - frameY) * zoom);
	}

	private double inWindowY(double screenY) {
		return frameY + (screenY - frameY) / zoom;
	}

	private void buildPage() {
		if (searching()) {
			buildSearch();
			return;
		}
		// Rebuilding the same page because a setting changed which others are visible: every setting keeps its control, so an open dropdown
		// stays open, a field keeps its text and nothing moves unnecessarily. Another page gets fresh controls.
		boolean samePage = key() == builtKey;
		builtKey = key();
		Map<Setting<?>, Control> previous = samePage ? new HashMap<>(controls) : new HashMap<>();
		List<Group> before = samePage ? new ArrayList<>(groups) : List.of();
		controls.clear();
		groups.clear();
		if (!samePage) {
			focused = null;
			noteLights.clear();
		}
		dragged = null;
		Feature page = page();
		List<Entry> entries = pageEntries();
		visibleSettings = entries.stream().map(Entry::setting).toList();

		Map<String, Group> byTitle = new LinkedHashMap<>();
		// a folder is a feature (or page group) that expands and collapses: its first setting is always shown, the rest when expanded
		Map<String, Integer> folderCount = new HashMap<>();
		for (Entry entry : entries) {
			Setting<?> setting = entry.setting();
			Group group = byTitle.computeIfAbsent(entry.title(), t -> new Group(t, new ArrayList<>()));
			if (entry.collapsible()) {
				String key = key() + ":" + entry.folder();
				boolean open = OPEN_GROUPS.contains(key);
				int count = folderCount.merge(entry.folder(), 1, Integer::sum);
				if (count == 1) {
					// one row stands for the folded group: "Fine-tune", with the names of what it holds, and Show or Hide
					var toggle = new ButtonSetting("fold_" + entry.folder(), Component.literal(FOLD_LABEL), Component.literal(open ? "Hide" : "Show"), false, () -> {
						if (!OPEN_GROUPS.remove(key)) {
							OPEN_GROUPS.add(key);
						}
						rebuildRequested = true;
					});
					toggle.setDescription(foldedNames(entries, entry.folder()));
					Control toggleControl = toggle.createControl(this::onSettingChanged);
					controls.put(toggle, toggleControl);
					group.rows().add(new Row(toggle, toggleControl));
				}
				if (!open) {
					continue;
				}
			}
			if (setting instanceof ColorSetting color && color.rowLabel() != null) {
				// colors sharing a row are collected into it; its control is made once all of them are known
				Row last = group.rows().isEmpty() ? null : group.rows().getLast();
				if (last != null && last.setting() instanceof ColorRowSetting row && row.labelText().equals(color.rowLabel())) {
					row.colors().add(color);
				} else {
					group.rows().add(new Row(new ColorRowSetting(color.rowLabel(), color), null));
				}
				continue;
			}
			Control control = previous.get(setting);
			if (control == null) {
				control = RowGuard.control(setting, this::onSettingChanged);
				if (control == null) {
					continue;
				}
			}
			controls.put(setting, control);
			group.rows().add(new Row(setting, control));
		}
		groups.addAll(byTitle.values());
		buildColorRows(samePage);
		nestSubOptions();
		// the same page with other rows (a choice that shows other options): the old rows fade out while the new ones fade in. Another tab
		// or a sheet kept what was shown before it was built
		if (samePage && !tabSwitching && !before.isEmpty() && !sameRows(before, groups)) {
			tabDirection = 0;
			keepOutgoing(before, true);
		}
		if (focused != null && !controls.containsValue(focused)) {
			focused = null;
		}
		pageBuilt();
	}

	/** @return whether two builds of a page show the same settings in the same groups, in the same order */
	private static boolean sameRows(List<Group> before, List<Group> now) {
		if (before.size() != now.size()) {
			return false;
		}
		for (int g = 0; g < now.size(); g++) {
			List<Row> old = before.get(g).rows();
			List<Row> rows = now.get(g).rows();
			if (!before.get(g).title().equals(now.get(g).title()) || old.size() != rows.size()) {
				return false;
			}
			for (int i = 0; i < rows.size(); i++) {
				if (old.get(i).setting() != rows.get(i).setting() || old.get(i).depth != rows.get(i).depth) {
					return false;
				}
			}
		}
		return true;
	}

	/** The chip of each color in a row of colors, kept while the page is rebuilt so an open color picker stays open. */
	private final Map<ColorSetting, ColorSwatchControl> colorChips = new HashMap<>();

	/** Gives the rows of colors their controls, from the chips of the colors. */
	private void buildColorRows(boolean samePage) {
		Map<ColorSetting, ColorSwatchControl> kept = samePage ? new HashMap<>(colorChips) : Map.of();
		colorChips.clear();
		for (Group group : groups) {
			List<Row> rows = group.rows();
			for (int i = 0; i < rows.size(); i++) {
				if (!(rows.get(i).setting() instanceof ColorRowSetting row)) {
					continue;
				}
				List<ColorSwatchControl> chips = new ArrayList<>();
				for (ColorSetting color : row.colors()) {
					ColorSwatchControl chip = kept.get(color);
					if (chip == null) {
						chip = new ColorSwatchControl(color, color.labelText(), this::onSettingChanged);
					}
					colorChips.put(color, chip);
					chips.add(chip);
				}
				Control control = row.createControl(chips);
				controls.put(row, control);
				rows.set(i, new Row(row, control));
			}
		}
	}

	/** Deepest nesting of sub-options; deeper ones are drawn at this depth. */
	private static final int MAX_DEPTH = 4;

	/**
	 * Moves every sub-option ({@link Setting#under}) right after its parent, behind the parent's earlier sub-options, also from another group,
	 * and records how deep it is nested. Sub-options whose parent is not on the page stay where they are.
	 */
	private void nestSubOptions() {
		Set<Setting<?>> onPage = new HashSet<>();
		for (Group group : groups) {
			for (Row row : group.rows()) {
				onPage.add(row.setting());
			}
		}
		Map<Setting<?>, List<Row>> children = new HashMap<>();
		for (Group group : groups) {
			for (Row row : group.rows()) {
				Setting<?> parent = row.setting().parent();
				if (parent != null && parent != row.setting() && onPage.contains(parent)) {
					children.computeIfAbsent(parent, key -> new ArrayList<>()).add(row);
				}
			}
		}
		if (children.isEmpty()) {
			return;
		}
		for (Group group : groups) {
			List<Row> ordered = new ArrayList<>();
			for (Row row : group.rows()) {
				Setting<?> parent = row.setting().parent();
				if (parent == null || parent == row.setting() || !onPage.contains(parent)) {
					addNested(ordered, row, children, 0);
				}
			}
			group.rows().clear();
			group.rows().addAll(ordered);
		}
		groups.removeIf(group -> group.rows().isEmpty());
	}

	private static void addNested(List<Row> into, Row row, Map<Setting<?>, List<Row>> children, int depth) {
		row.depth = Math.min(depth, MAX_DEPTH);
		into.add(row);
		List<Row> nested = children.get(row.setting());
		if (nested != null && depth < MAX_DEPTH) {
			for (Row child : nested) {
				addNested(into, child, children, depth + 1);
			}
		}
	}

	/** Derives what drawing needs from the built page once, instead of every frame. */
	private void pageBuilt() {
		controlList.clear();
		controlList.addAll(controls.values());
		editColumn = false;
		for (Group group : groups) {
			for (Row row : group.rows()) {
				editColumn |= row.control().hasEditButton();
			}
		}
		Feature page = page();
		if (searching()) {
			headerTitle = "Search";
			headerDescription = resultCount() + (resultCount() == 1 ? " setting" : " settings") + " found";
		} else {
			headerTitle = single != null ? page.nameText() : page.headerTitle();
			headerDescription = page.descriptionText();
		}
		headerParent = !searching() && page instanceof FeatureListFeature list && list.parentPage() != null ? list.parentPage().nameText() : null;
		countChanged();
	}

	/** The page the controls were last built for. */
	private int builtKey = Integer.MIN_VALUE;

	// ------------------------------------------------------------------------------------------------------- search

	// the menu's top bar: the Lumio mark, the name, the version on a small chip, the search in the middle and the round close button at the right
	private static final int MARK = 14;
	private static final int CLOSE_BUTTON = 16;
	/** The search field: as wide as it wants in the middle of the bar (about 280 of 1380 px in the mockup), and the narrowest it gets. */
	private static final int SEARCH_WANTED = 190;
	private static final int SEARCH_LEAST = 70;
	private static final int SEARCH_HEIGHT = 20;
	/** Where the name and version on the left of the bar end, as last drawn; the search keeps clear of it. */
	private int barLeftEnd = Integer.MIN_VALUE;
	private static final int SEARCH_PAD = 8;
	private static final int SEARCH_ICON = 9;

	private boolean searching() {
		return !query.isBlank();
	}

	private int resultCount() {
		int count = 0;
		for (Group group : groups) {
			count += group.rows().size();
		}
		return count;
	}

	/** Builds the search results: settings of every page whose name, group or page matches the query, grouped by page. */
	private void buildSearch() {
		controls.clear();
		groups.clear();
		focused = null;
		dragged = null;
		String[] words = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
		List<Setting<?>> shown = new ArrayList<>();
		// three kinds of results, in this order: pages and categories, features, settings; each leads to where it lives
		Group pageHits = new Group("Pages", new ArrayList<>());
		Group featureHits = new Group("Features", new ArrayList<>());
		Group settingHits = new Group("Settings", new ArrayList<>());
		int number = 0;
		for (int i = 0; i < pages.size(); i++) {
			Feature page = pages.get(i);
			FeatureListFeature parent = page instanceof FeatureListFeature list ? list.parentPage() : null;
			String name = page.nameText();
			if (!matchesWords(words, parent == null ? name : parent.nameText() + " " + name)) {
				continue;
			}
			final int index = i;
			String kind = parent == null ? "Page" : "Category  ·  " + parent.nameText();
			number = addHit(pageHits, shown, new SearchHitSetting("hit_" + number, Component.literal(name), kind, () -> showFromSearch(index))
				.withIcon(page.iconName(), page.iconRgb()), number);
		}
		for (Feature feature : FeatureManager.features()) {
			if (feature.isChild() || feature instanceof HomeFeature || feature instanceof FeatureListFeature) {
				continue;
			}
			String featureName = feature.nameText();
			int pageIndex = pageOf(feature);
			Feature page = pageIndex >= 0 ? pages.get(pageIndex) : feature;
			if (!(feature instanceof MenuFeature) && matchesWords(words, featureName + " " + feature.searchWordsText())) {
				final Feature opened = feature;
				var hit = new SearchHitSetting("hit_" + number, Component.literal(featureName), "Feature  ·  " + pathOf(page), () -> jumpTo(opened, null),
					opened.isSwitchable() ? opened::isEnabled : null, () -> {
						opened.toggle();
						FeatureManager.saveSoon();
					});
				number = addHit(featureHits, shown, hit.withIcon(page.iconName(), page.iconRgb()).workInProgress(opened.isWorkInProgress()), number);
			}
			for (Setting<?> setting : feature.settings()) {
				if (!setting.isVisible() || !setting.searchable() || setting instanceof FeatureRowSetting || setting instanceof FeatureGroupSetting || setting instanceof dev.tim1337.lumio.config.setting.FeatureCardsSetting || setting.key().equals("enabled")) {
					continue;
				}
				String section = feature.sectionOf(setting) == null ? "" : feature.sectionOf(setting);
				String explanation = setting.description() == null ? "" : setting.description();
				if (!matchesWords(words, setting.labelText() + " " + section + " " + explanation + " " + setting.searchText())) {
					continue;
				}
				final Feature owner = feature;
				final Setting<?> target = setting;
				String where = pathOf(section);
				String path = "Setting  ·  " + featureName + (where.isEmpty() ? "" : "  ›  " + where);
				var hit = setting instanceof BooleanSetting flag
					? new SearchHitSetting("hit_" + number, setting.label(), path, () -> jumpTo(owner, target), flag::get, () -> {
						flag.set(!flag.get());
						FeatureManager.saveSoon();
					})
					: new SearchHitSetting("hit_" + number, setting.label(), path, () -> jumpTo(owner, target));
				number = addHit(settingHits, shown, hit.withIcon(page.iconName(), page.iconRgb()), number);
			}
		}
		for (Group group : List.of(pageHits, featureHits, settingHits)) {
			if (!group.rows().isEmpty()) {
				groups.add(group);
			}
		}
		visibleSettings = shown;
		pageBuilt();
	}

	/** Adds a result to its group; @return the next result number */
	private int addHit(Group group, List<Setting<?>> shown, SearchHitSetting hit, int number) {
		Control control = hit.createControl(this::onSettingChanged);
		controls.put(hit, control);
		group.rows().add(new Row(hit, control));
		shown.add(hit);
		return number + 1;
	}

	/** @return whether every word of the query starts a word of the text, ignoring case: "min", "mining" and "skyblock mining" find "SkyBlock Mining" */
	private static boolean matchesWords(String[] words, String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String word : words) {
			if (!word.isEmpty() && !startsWord(lower, word)) {
				return false;
			}
		}
		return true;
	}

	private static boolean startsWord(String text, String word) {
		for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1)) {
			if (at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1))) {
				return true;
			}
		}
		return false;
	}

	/** @return the sidebar page a feature lives on: the page that is the feature, or the page listing it, a category before its parent; or -1 */
	private int pageOf(Feature feature) {
		int found = -1;
		for (int i = 0; i < pages.size(); i++) {
			Feature page = pages.get(i);
			if (page == feature) {
				return i;
			}
			if (page instanceof FeatureListFeature list && (list.holds(feature) || list.holdsAll(feature)) && (found < 0 || list.parentPage() != null)) {
				found = i;
			}
		}
		return found;
	}

	/** @return where a page is, for a search result: "SkyBlock › Mining" for a category, otherwise its name */
	private static String pathOf(Feature page) {
		return page instanceof FeatureListFeature list ? list.path() : page.nameText();
	}

	// ------------------------------------------------------------------------------------------ navigating to a search result

	/** Setting to reveal on the next layout: scrolled into view and briefly highlighted. */
	private Setting<?> pendingSetting;
	private Setting<?> highlightSetting;
	private long highlightUntil;
	/** How long a revealed setting pulses before it has faded out. */
	private static final long HIGHLIGHT_NANOS = 1_200_000_000L;

	/** Ends the search and shows a page as a click in the sidebar does, at its top and without the pane. */
	private void showFromSearch(int index) {
		closeEditor();
		goToPage(index);
	}

	/** Ends the search and shows a page at its top, with the page animation; the pane stays as it is. */
	private void goToPage(int index) {
		query = "";
		searchFocused = false;
		SCROLLS.put(index, 0.0);
		if (index == selectedIndex) {
			scroll = 0;
			scrollTarget = 0;
			restartPage();
			buildPage();
		} else {
			selectPage(index);
		}
	}

	/**
	 * A feature or setting result was clicked: ends the search, shows the page the feature lives on and, for a feature with settings of its own,
	 * opens its pane; a setting is then revealed on its tab, scrolled to and briefly highlighted, on the page or in the pane.
	 */
	private void jumpTo(Feature feature, Setting<?> setting) {
		Feature home = feature.homePage();
		if (home != null && (setting == null || feature.viewOf(setting) == null)) {
			jumpTo(home, null);
			return;
		}
		Feature shown = home != null ? home : feature;
		int index = pageOf(shown);
		if (index >= 0) {
			goToPage(index);
		} else {
			query = "";
			searchFocused = false;
			buildPage();
		}
		Feature view = index >= 0 && setting != null ? feature.viewOf(setting) : null;
		if (view != null) {
			Feature listing = index >= 0 ? pages.get(index) : null;
			if (listing instanceof FeatureListFeature list && list.direct() != shown && lists(listing, shown)) {
				// on a sheet of the feature's settings: in the pane
				if (editor == null || paneClosing || editor.single != shown) {
					openEditor(shown);
				}
				editor.showSubPage(view);
				editor.pendingSetting = setting;
				return;
			}
			// on a sheet or a sub-page of the page: it opens there
			closeEditor();
			showSubPage(view);
			pendingSetting = setting;
			return;
		}
		Feature page = index >= 0 ? pages.get(index) : null;
		boolean onPage = page == feature || page instanceof FeatureListFeature list && list.holdsAll(feature)
			|| setting != null && page != null && (page.settings().contains(setting) || page.shownSettings().contains(setting));
		if (onPage || !FeatureListControl.editable(feature)) {
			closeEditor();
			pendingSetting = setting != null ? setting : rowOf(page, feature);
			return;
		}
		// the feature's row is shown in the list, and its settings in the pane
		pendingSetting = rowOf(page, feature);
		if (editor == null || paneClosing || editor.single != feature) {
			openEditor(feature);
		}
		editor.pendingSetting = setting;
	}

	/** @return the row of a feature in a page's list, or null */
	private static Setting<?> rowOf(Feature page, Feature feature) {
		if (page == null) {
			return null;
		}
		for (Setting<?> setting : page.settings()) {
			if (setting instanceof FeatureRowSetting row && row.feature() == feature) {
				return setting;
			}
		}
		return null;
	}

	/** @return whether the page's content is still changing over to other rows */
	private boolean contentSwitching() {
		return tabSwitch < 1f;
	}

	/** Reveals the pending target: opens its tab group and folder, scrolls the page to it and highlights it. */
	private void resolvePending() {
		Setting<?> wanted = pendingSetting;
		Entry found = null;
		for (Entry entry : entriesOf(page())) {
			if (entry.setting() == wanted) {
				found = entry;
				break;
			}
		}
		if (found == null) {
			pendingSetting = null;
			return;
		}
		if (!subTitles.isEmpty() && !found.title().isEmpty()) {
			// by the tab part of the group's title: "Glass and glow · " is on the tab Glass and glow
			int tab = subTitles.indexOf(tabOf(found.title()));
			if (tab >= 0 && tab != subIndex) {
				subIndex = tab;
				subTitle = null;
				rebuildRequested = true;
				return;
			}
		}
		if (found.collapsible() && OPEN_GROUPS.add(key() + ":" + found.folder())) {
			rebuildRequested = true;
			return;
		}
		// the highlight waits until the page has settled: no rows still fading out under the others
		if (contentSwitching()) {
			return;
		}
		for (Placed p : placed) {
			for (int i = 0; i < p.group.rows().size(); i++) {
				if (p.group.rows().get(i).setting() == wanted) {
					scrollTarget = Mth.clamp(p.rowTops[i] - contentTop + scroll - 60, 0, maxScroll());
					highlightSetting = wanted;
					highlightUntil = System.nanoTime() + HIGHLIGHT_NANOS;
					pendingSetting = null;
					return;
				}
			}
		}
		pendingSetting = null;
	}
	private void queryChanged() {
		scroll = 0;
		scrollTarget = 0;
		buildPage();
	}

	/** A sidebar page was clicked: ends any search and shows the page. */
	private void selectFromSidebar(int index) {
		if (!searching()) {
			selectPage(index);
			return;
		}
		leavePage(index);
		query = "";
		searchFocused = false;
		SCROLLS.put(selectedIndex, 0.0);
		selectedIndex = index;
		subPage = null;
		underSheet = null;
		closePaneOffList();
		scroll = SCROLLS.getOrDefault(index, 0.0);
		scrollTarget = scroll;
		restartPage();
		buildPage();
	}

	private void clearQuery() {
		if (!query.isEmpty()) {
			query = "";
			queryChanged();
		}
	}

	// the search field sits in the middle of the top bar
	private MenuBar.Search searchPlace() {
		int leftEnd = barLeftEnd == Integer.MIN_VALUE ? windowX + SIDEBAR : barLeftEnd;
		return MenuBar.search(leftEnd, menuCloseX(), windowX + windowWidth / 2, SEARCH_WANTED, SEARCH_LEAST);
	}

	private int searchX() {
		return searchPlace().x();
	}

	private int searchWidth() {
		return searchPlace().width();
	}

	private int searchY() {
		return frameY + (TOP_BAR - SEARCH_HEIGHT) / 2;
	}

	private boolean overSearch(double mouseX, double mouseY) {
		return single == null && owner == null && searchWidth() > 0 && mouseX >= searchX() && mouseX < searchX() + searchWidth() && mouseY >= searchY() && mouseY < searchY() + SEARCH_HEIGHT;
	}

	private static final String SEARCH_HINT = "Search settings";
	private static final String SEARCH_HINT_SHORT = "Search";
	private static final String SEARCH_KEY = "Ctrl F";

	private void drawSearch(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		if (searchWidth() == 0) {
			return;
		}
		int x = searchX();
		int y = searchY();
		int middle = y + SEARCH_HEIGHT / 2;
		boolean hover = overSearch(mouseX, mouseY);
		// a slightly lighter fill without a border, as in the mockup; focused, a thin accent ring
		ui.rounded(x, y, searchWidth(), SEARCH_HEIGHT, Radius.SM, Ui.withOpacity(palette.hover(), hover || searchFocused ? 1f : 0.7f));
		if (searchFocused) {
			ui.ring(x, y, searchWidth(), SEARCH_HEIGHT, Radius.SM, 1, ui.accent);
		}
		int glass = palette.textSecondary();
		ui.icon("search", x + SEARCH_PAD, middle - SEARCH_ICON / 2, SEARCH_ICON, searchFocused ? Ui.mix(glass, ui.accent, 0.75f) : glass);
		int textX = x + SEARCH_PAD + SEARCH_ICON + 5;
		if (query.isEmpty() && !searchFocused) {
			// the shortcut as a small key at the end, and the hint, at the size of the descriptions, shortened to what fits before it
			float keyWidth = ui.widthScaled(SEARCH_KEY, TextScale.SMALL) + 7f;
			int keyX = Math.round(x + searchWidth() - SEARCH_PAD + 2 - keyWidth);
			ui.ring(keyX, middle - 6, Math.round(keyWidth), 12, Radius.XS, 1, Ui.withOpacity(palette.textSecondary(), 0.35f));
			ui.textAtScaled(SEARCH_KEY, keyX + 3.5f, middle - 3.5f, TextScale.SMALL, glass);
			int room = keyX - 4 - textX;
			String hint = ui.widthScaled(SEARCH_HINT, TextScale.BODY) <= room ? SEARCH_HINT : ui.fitScaled(SEARCH_HINT_SHORT, room, TextScale.BODY);
			ui.textAtScaled(hint, textX, middle - 4f, TextScale.BODY, glass);
			return;
		}
		int right = x + searchWidth() - (query.isEmpty() ? SEARCH_PAD : 20);
		String shown = ui.fit(query, right - textX);
		if (searchFocused) {
			querySelection.draw(ui, textX, middle, ui.width(shown));
		}
		ui.textMiddle(shown, textX, middle, palette.text());
		if (searchFocused && !querySelection.all() && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(textX + ui.width(shown) + 1, y + 5, 1, SEARCH_HEIGHT - 10, palette.text());
		}
		if (!query.isEmpty()) {
			ui.icon("close", x + searchWidth() - 15, middle - 5, 10, glass);
		}
	}

	/** A setting on a page, with the title of its group. */
	private record Entry(Setting<?> setting, String title, boolean collapsible, String folder) {
	}

	// subcategory tabs of a feature window: the page groups, one at a time, selected at the top
	private int subIndex;
	private List<String> subTitles = List.of();

	/** Name of the open tab, kept when the set of tabs changes. */
	private String subTitle;

	/** @return the number of settings of a feature and its children in this window, visible or not */
	private int settingCount(Feature feature) {
		int count = 0;
		for (Setting<?> setting : feature.settings()) {
			if (!setting.key().equals("enabled")) {
				count++;
			}
		}
		for (Feature part : feature.children()) {
			count += part.settings().size();
		}
		return count;
	}

	private List<Entry> pageEntries() {
		List<Entry> all = entriesOf(page());
		// A feature made of others (corpses: highlight and profit) gets one tab per part with all its groups, and its switch on top. One tab per
		// group per part ("Corpse highlight / Sharing") made a long tab row, including tabs containing only a switch.
		if (single != null && !page().children().isEmpty() && !searching()) {
			List<String> parts = new ArrayList<>();
			for (Entry entry : all) {
				if (!entry.folder().isEmpty() && !parts.contains(entry.folder())) {
					parts.add(entry.folder());
				}
			}
			if (parts.size() >= 2) {
				if (subTitle != null && parts.contains(subTitle)) {
					subIndex = parts.indexOf(subTitle);
				}
				subTitles = parts;
				subIndex = Mth.clamp(subIndex, 0, parts.size() - 1);
				String chosen = parts.get(subIndex);
				subTitle = chosen;
				List<Entry> shown = new ArrayList<>();
				for (Entry entry : all) {
					if (entry.folder().isEmpty()) {
						shown.add(entry); // the feature's own settings
					} else if (entry.folder().equals(chosen)) {
						String section = entry.setting().section();
						shown.add(new Entry(entry.setting(), section == null ? "" : section, false, entry.folder()));
					}
				}
				return shown;
			}
		}
		// groups become tabs only with very many settings (a feature window), or on pages that request it
		boolean list = page() instanceof FeatureListFeature;
		boolean tabbed = list ? ((FeatureListFeature) page()).isTabbed() : single != null || page().alwaysTabbed();
		List<String> titles = new ArrayList<>();
		for (Entry entry : all) {
			// the untitled group (holding the feature switch) is always shown and gets no tab; neither does Reset, which is an action rather than a
			// settings group: it appears at the end of every tab
			if (!entry.title().isEmpty() && !entry.title().equals("Reset") && !titles.contains(tabOf(entry.title()))) {
				titles.add(tabOf(entry.title()));
			}
		}
		// Whether to use tabs is decided by all settings of the page, including hidden ones: a setting that hides others (a style with fewer
		// options) must not make the tabs disappear and the whole page change under the mouse.
		int size = list ? all.size() : settingCount(page());
		if (!tabbed || page().neverTabbed() || searching() || (size < (list ? 8 : 16) && !page().alwaysTabbed()) || titles.size() < 2) {
			subTitles = List.of();
			return all;
		}
		if (tabFor != null) {
			for (Entry entry : all) {
				boolean listed = entry.setting() instanceof FeatureRowSetting row && row.feature() == tabFor
					|| entry.setting() instanceof FeatureGroupSetting group && group.features().contains(tabFor);
				if (listed && titles.contains(tabOf(entry.title()))) {
					subTitle = tabOf(entry.title());
					break;
				}
			}
			tabFor = null;
		}
		// keep the open tab, by name, when tabs before it appear or disappear
		if (subTitle != null && titles.contains(subTitle)) {
			subIndex = titles.indexOf(subTitle);
		}
		subTitles = titles;
		subIndex = Mth.clamp(subIndex, 0, titles.size() - 1);
		String chosen = titles.get(subIndex);
		subTitle = chosen;
		return all.stream().filter(entry -> entry.title().isEmpty() || entry.title().equals("Reset") || tabOf(entry.title()).equals(chosen)).toList();
	}

	/** Between a group's tab and its heading: "Look · Theme" is the group Theme on the tab Look. */
	private static final String TAB_SEPARATOR = " · ";

	/** @return the tab a group belongs to: the part of its title before " · ", or the whole title */
	static String tabOf(String title) {
		int at = title.indexOf(TAB_SEPARATOR);
		return at < 0 ? title : title.substring(0, at);
	}

	/** @return where a group is, for a search result: "Glass and glow  ›  Glass", or only the tab for a group without a heading */
	static String pathOf(String title) {
		String tab = tabOf(title);
		String heading = headingOf(title);
		return tab.equals(title) ? title : heading.isBlank() ? tab : tab + "  ›  " + heading;
	}

	/** @return the heading a group shows: the part of its title after " · ", or the whole title */
	static String headingOf(String title) {
		int at = title.indexOf(TAB_SEPARATOR);
		return at < 0 ? title : title.substring(at + TAB_SEPARATOR.length());
	}
	private static final int TAB_HEIGHT = 20;
	/** Padding on each side of a tab's name; it shrinks down to the minimum when the tabs do not fit otherwise. */
	private static final int TAB_PAD = 10;
	private static final int TAB_PAD_MIN = 4;
	/** Half the height of a line of regular text, to center text drawn from its top. */
	private static final float TEXT_HALF_HEIGHT = 4f;
	/** How much wider the bold face of page titles runs than the regular one, for fitting a title measured in the regular face. */
	private static final float BOLD_WIDER = 1.09f;
	/** Edges of the tabs from the last frame, the left edge of each and the right edge of the last shown one, for hit testing. */
	private int[] tabEdges = new int[0];
	private int tabsShown;
	private static final int TAB_INSET = 2;
	/** Whether the tabs did not fit, so the dropdown stands in. */
	private boolean tabsCollapsed;
	private final ListDropdownControl tabDropdown = new ListDropdownControl(TAB_HEIGHT) {
		@Override
		protected int count() {
			return subTitles.size();
		}

		@Override
		protected String name(int index) {
			return subTitles.get(index);
		}

		@Override
		protected int selectedIndex() {
			return Mth.clamp(subIndex, 0, subTitles.size() - 1);
		}

		@Override
		protected void pick(int index) {
			openTab(index);
		}
	};

	/** @return top of the tab bar: below the header, or below the title bar in a settings window */
	// a settings window with tabs has what the feature does above them, once, not in every tab
	private int tabsY() {
		return single != null ? windowY + TITLE_BAR + 8 + introAbove : windowY + 62;
	}

	/** Draws the tabs as a segmented control: a track with a pill under the selected tab, which slides when another one is picked. */
	private void drawSubTabs(Ui ui, int mouseX, int mouseY) {
		tabsShown = 0;
		if (subTitles.isEmpty() || searching()) {
			return;
		}
		Palette palette = ui.palette;
		int count = subTitles.size();
		if (tabEdges.length != count + 1) {
			tabEdges = new int[count + 1];
		}
		int x = contentLeft;
		int y = tabsY();
		int right = contentRight;
		int[] texts = new int[count];
		int text = 0;
		for (int i = 0; i < count; i++) {
			texts[i] = (int) Math.ceil(ui.boldWidthScaled(subTitles.get(i), TextScale.BODY)) + workTagRoom(ui, subTitles.get(i));
			text += texts[i];
		}
		// tabs that do not fit even at the smallest padding become a dropdown: none may be left out, or its settings could not be reached
		tabsCollapsed = !SegmentFit.fits(right - x, texts, TAB_INSET, TAB_PAD_MIN);
		if (tabsCollapsed) {
			tabDropdown.place(ui, x, y, tabDropdown.inlineWidth(ui, right - x));
			tabDropdown.render(ui, mouseX, mouseY);
			return;
		}
		tabDropdown.loseFocus();
		int pad = Mth.clamp((right - x - 2 * TAB_INSET - text) / (2 * count), TAB_PAD_MIN, TAB_PAD);
		tabEdges[0] = x + TAB_INSET;
		for (int i = 0; i < count; i++) {
			tabEdges[i + 1] = tabEdges[i] + texts[i] + 2 * pad;
			tabsShown++;
		}
		int trackWidth = tabEdges[tabsShown] + 2 - x;
		ui.rounded(x, y, trackWidth, TAB_HEIGHT, Radius.SM, palette.control());
		ui.ring(x, y, trackWidth, TAB_HEIGHT, Radius.SM, 1, palette.controlBorder());
		int selected = Math.min(subIndex, tabsShown - 1);
		// the marker moves with the content: in the time of the tab switch, from where it was to the new tab
		float marker = tabSwitch * tabSwitch * (3f - 2f * tabSwitch);
		float at = ui.animated && tabSwitch < 1f ? Mth.clamp(Mth.lerp(marker, tabMarkerFrom, selected), 0f, tabsShown - 1) : selected;
		tabMarkerAt = at;
		int low = (int) Math.floor(at);
		int high = Math.min(low + 1, tabsShown - 1);
		float t = at - low;
		int from = Math.round(Mth.lerp(t, tabEdges[low], tabEdges[high]));
		int to = Math.round(Mth.lerp(t, tabEdges[low + 1], tabEdges[high + 1]));
		ui.shadow(from, y + 2, to - from, TAB_HEIGHT - 4, Radius.SM, 2, 0.14f);
		ui.rounded(from, y + 2, to - from, TAB_HEIGHT - 4, Radius.SM, palette.pill());
		for (int i = 0; i < tabsShown; i++) {
			boolean over = mouseX >= tabEdges[i] && mouseX < tabEdges[i + 1] && mouseY >= y && mouseY < y + TAB_HEIGHT;
			int color = i == selected ? palette.onPill() : over ? palette.text() : palette.textSecondary();
			int center = (tabEdges[i] + tabEdges[i + 1]) / 2;
			String label = subTitles.get(i);
			int tagRoom = workTagRoom(ui, label);
			float nameWidth = i == selected ? ui.boldWidthScaled(label, 1f) : ui.width(label);
			float nameX = center - (nameWidth + tagRoom) / 2f;
			if (i == selected) {
				// the selected tab in the semibold face, as in the mockups
				ui.textAtScaledBold(label, nameX, y + TAB_HEIGHT / 2f - TEXT_HALF_HEIGHT, 1f, color);
			} else {
				ui.textAtScaled(label, nameX, y + TAB_HEIGHT / 2f - TEXT_HALF_HEIGHT, 1f, color);
			}
			if (tagRoom > 0) {
				// a tab still being tested: a small tag after its name
				int tagX = Math.round(nameX + nameWidth) + WORK_TAG_GAP;
				int tagWidth = tagRoom - WORK_TAG_GAP;
				ui.rounded(tagX, y + (TAB_HEIGHT - WORK_TAG_HEIGHT) / 2, tagWidth, WORK_TAG_HEIGHT, Radius.box(WORK_TAG_HEIGHT),
					Ui.withOpacity(FeatureListControl.WORK_COLOR, 0.2f));
				ui.textAtScaled(Feature.WORK_IN_PROGRESS_SHORT, tagX + WORK_TAG_PAD, y + TAB_HEIGHT / 2f - TEXT_HALF_HEIGHT * TextScale.SMALL, TextScale.SMALL,
					Ui.mix(palette.text(), palette.coloredText(FeatureListControl.WORK_COLOR), 0.55f));
			}
		}
	}

	/** The tag after the name of a tab still being tested: the gap before it, its padding and its height. */
	private static final int WORK_TAG_GAP = 5;
	private static final int WORK_TAG_PAD = 4;
	private static final int WORK_TAG_HEIGHT = 11;

	/** @return the room the "work in progress" tag takes after a tab's name, with its gap; 0 for a tab without it */
	private int workTagRoom(Ui ui, String tab) {
		if (!(page() instanceof FeatureListFeature list) || !list.isWorkInProgress(tab)) {
			return 0;
		}
		return WORK_TAG_GAP + 2 * WORK_TAG_PAD + (int) Math.ceil(ui.widthScaled(Feature.WORK_IN_PROGRESS_SHORT, TextScale.SMALL));
	}

	/**
	 * A change of the page's content (another tab, a sheet opening or closing, a choice that shows other rows) is one cross-fade of this
	 * length: what was shown fades out while the new content fades in. Tabs also move a few pixels, towards and from the side the new tab lies on.
	 */
	private static final float TAB_SWITCH_SECONDS = 0.18f;
	private static final float TAB_SLIDE = 6f;
	/** How far the switch is, from 0 to 1, which way the new tab lies (-1 left, 1 right, 0 no tab), and whether the page is being built for a switch of tabs. */
	private float tabSwitch = 1f;
	private int tabDirection;
	private boolean tabSwitching;
	/** Where the tab bar's marker was drawn last, and where it started from at the last switch, in tabs. */
	private float tabMarkerAt;
	private float tabMarkerFrom;

	/**
	 * Shows another tab of the page as one short transition: the page is not replayed from the top (no slide up, no cover), the old tab's
	 * content fades out while the new tab's fades in. With animations off it switches at once; with reduced animations it only fades.
	 */
	private void openTab(int tab) {
		UiSounds.play(UiSounds.Cue.TICK);
		tabDirection = Integer.signum(tab - subIndex);
		tabMarkerFrom = tabMarkerAt;
		keepOutgoing(groups, false);
		subIndex = tab;
		subTitle = null;
		scroll = 0;
		scrollTarget = 0;
		tabSwitch = ModFeatures.MENU != null && ModFeatures.MENU.animations() ? 0f : 1f;
		tabSwitching = true;
		try {
			buildPage();
		} finally {
			tabSwitching = false;
		}
	}

	/** What the page showed before its content changed, kept while it fades out under the new content. */
	private static final class Outgoing {
		final List<Group> groups;
		final List<Placed> placed = new ArrayList<>();
		final List<Placed> pool = new ArrayList<>();
		final List<Control> controls;
		final List<String> intro;
		final int introY;
		/**
		 * Whether its rows have the controls of the new content (the same page with other rows). A control has one place, so such content is
		 * laid out again in every frame of the switch, and what stands where it stood is drawn once, at its full light.
		 */
		final boolean shared;
		/** The sheet it was, with where it started, or null. */
		final Feature sheet;
		final int sheetTop;

		Outgoing(List<Group> groups, List<Control> controls, List<String> intro, int introY, boolean shared, Feature sheet, int sheetTop) {
			this.groups = groups;
			this.controls = controls;
			this.intro = intro;
			this.introY = introY;
			this.shared = shared;
			this.sheet = sheet;
			this.sheetTop = sheetTop;
		}
	}

	private Outgoing outgoing;

	/**
	 * Keeps what the page shows now, to fade out under the content built next, and starts the switch. Called before the page changes. With
	 * animations off nothing is kept and the content changes at once.
	 */
	private void keepOutgoing(List<Group> shown, boolean shared) {
		outgoing = null;
		if (ModFeatures.MENU == null || !ModFeatures.MENU.animations() || searching()) {
			tabSwitch = 1f;
			return;
		}
		Outgoing kept = new Outgoing(new ArrayList<>(shown), shared ? List.of() : new ArrayList<>(controlList), introFixed ? List.of() : introLines, introY, shared,
			showsSheet() ? subPage : null, sheetTop());
		// its places leave the pool: the next layout must not write over them
		kept.placed.addAll(placed);
		kept.pool.addAll(placed);
		placedPool.removeAll(placed);
		placed.clear();
		outgoing = kept;
		tabSwitch = 0f;
		tabMarkerFrom = tabMarkerAt;
	}

	private static float eased(float progress) {
		return progress * progress * (3f - 2f * progress);
	}

	/**
	 * Draws the content shown before the change, fading out, and gives the new content its light. Content of the same page is laid out
	 * again first, with the controls it shares; the page's own layout follows, so every control ends the frame at its place.
	 */
	private void drawOutgoing(Ui ui, float eased, float slide) {
		Outgoing old = outgoing;
		float before = Ui.alpha();
		if (old.shared) {
			double scrollNow = scroll;
			double targetNow = scrollTarget;
			layout(ui, old.groups, old.pool, old.placed);
			scroll = scrollNow;
			scrollTarget = targetNow;
			lightInPlace(old.placed, eased);
		} else {
			for (Placed p : old.placed) {
				p.light(1f - eased);
			}
		}
		if (slide != 0f) {
			ui.graphics.pose().pushMatrix();
			ui.graphics.pose().translate(-slide, 0f);
		}
		if (!old.shared && !old.intro.isEmpty()) {
			Ui.setAlpha(before * (1f - eased));
			drawIntro(ui, old.intro, old.introY);
			Ui.setAlpha(before);
		}
		for (Placed p : old.placed) {
			drawGroup(ui, p, -1, -1);
		}
		Ui.setAlpha(before * (1f - eased));
		for (int c = 0; c < old.controls.size(); c++) {
			old.controls.get(c).renderSticky(ui, -1, -1);
		}
		Ui.setAlpha(before);
		if (slide != 0f) {
			ui.graphics.pose().popMatrix();
		}
		if (old.shared) {
			layout(ui);
		}
	}

	/**
	 * The lights of a page whose rows changed: a card or a row that stands where it stood is left out of the old content and keeps its full
	 * light in the new, so nothing that stays flickers; everything else cross-fades.
	 */
	private void lightInPlace(List<Placed> old, float eased) {
		for (Placed p : old) {
			p.light(1f - eased);
			Placed now = placedOf(p.group.title());
			if (now == null) {
				continue;
			}
			if (p.cardWhere(now)) {
				p.cardLight = 0f;
				now.cardLight = 1f;
			}
			if (p.cardX != now.cardX || p.cardWidth != now.cardWidth) {
				continue;
			}
			for (int i = 0; i < p.rows; i++) {
				Setting<?> setting = p.group.rows().get(i).setting();
				for (int j = 0; j < now.rows; j++) {
					if (now.group.rows().get(j).setting() == setting && now.rowTops[j] == p.rowTops[i] && now.rowHeights[j] == p.rowHeights[i]) {
						p.rowLights[i] = 0f;
						now.rowLights[j] = 1f;
						break;
					}
				}
			}
		}
	}

	/** @return where the group with a title was laid out now, or null */
	private Placed placedOf(String title) {
		for (Placed p : placed) {
			if (p.group.title().equals(title)) {
				return p;
			}
		}
		return null;
	}


	/** @return the tab under the mouse, or -1 */
	private int tabAt(double mouseX, double mouseY) {
		int y = tabsY();
		if (mouseY < y || mouseY >= y + TAB_HEIGHT) {
			return -1;
		}
		for (int i = 0; i < tabsShown; i++) {
			if (mouseX >= tabEdges[i] && mouseX < tabEdges[i + 1]) {
				return i;
			}
		}
		return -1;
	}

	/** Group (and tab) of the style settings edited in the HUD editor, such as the colors of each status bar. */
	private static final String STYLE_SECTION = "Style";

	/** @return the settings shown on a feature's page: its own and those of its child features */
	private List<Entry> entriesOf(Feature feature) {
		List<Entry> own = new ArrayList<>();
		for (Setting<?> setting : feature.shownSettings()) {
			// the feature switch lives in the feature list and the window's title bar, not in its settings; a settings window has its reset in the
			// footer instead of a row
			if (RowGuard.visible(setting) && !setting.key().equals("enabled")
				&& !(setting.key().equals(RESET_KEY) && footerFeature() != null && footerFeature().settings().contains(setting))) {
				// the style settings that used to be in a window of their own, edited from the HUD editor, have a Style tab
				String section = setting.isEditorOnly() ? STYLE_SECTION : feature.sectionOf(setting) == null ? "" : feature.sectionOf(setting);
				own.add(new Entry(setting, section, !setting.isEditorOnly() && feature.isCollapsible(feature.sectionOf(setting)), section));
			}
		}
		// groups the feature wants last (panel appearance, shared by every panel) follow its own groups, in order
		Set<String> trailing = feature.trailingSections();
		if (!trailing.isEmpty()) {
			List<Entry> later = new ArrayList<>();
			own.removeIf(entry -> trailing.contains(entry.title()) && later.add(entry));
			own.addAll(later);
		}
		List<Entry> parts = new ArrayList<>();
		for (Feature part : feature.children()) {
			for (Setting<?> setting : part.settings()) {
				if (setting.isVisible()) {
					// a child feature is a folder; its own groups are named after it
					String name = part.name().getString();
					String section = setting.section();
					parts.add(new Entry(setting, section == null || section.isEmpty() || section.equals("Reset") ? name : name + "  /  " + section, true, name));
				}
			}
		}
		// child settings first, then the page's own entries such as a reset-all
		List<Entry> entries = new ArrayList<>(parts);
		entries.addAll(own);
		return entries;
	}
	private void onSettingChanged() {
		FeatureManager.saveSoon();
		countChanged();
		// a setting in the window next to the menu can change what the menu shows (the theme decides whether glass settings exist): refresh the menu too
		if (owner != null) {
			owner.onSettingChanged();
		}
		if (searching()) {
			return; // search result controls stay as they are while one of them is in use
		}
		// A setting can change which others are visible. Controls cannot be replaced while one of them is handling the change, so the rebuild
		// happens on the next frame.
		if (!pageEntries().stream().map(Entry::setting).toList().equals(visibleSettings)) {
			rebuildRequested = true;
		}
	}

	private void selectPage(int index) {
		if (index == selectedIndex) {
			// the page's own item in the sidebar leads back from a sub-page
			closeSubPage();
			return;
		}
		leavePage(index);
		SCROLLS.put(key(), scrollTarget);
		subPage = null;
		underSheet = null;
		selectedIndex = index;
		closePaneOffList();
		scroll = SCROLLS.getOrDefault(index, 0.0);
		scrollTarget = scroll;
		restartPage();
		UiSounds.play(UiSounds.Cue.TICK);
		buildPage();
	}

	/** Tells the page shown that the menu leaves it for another one. */
	private void leavePage(int next) {
		if (next != selectedIndex && selectedIndex >= 0 && selectedIndex < pages.size()) {
			pages.get(selectedIndex).pageLeft();
		}
	}

	/**
	 * Closes the settings pane when the page now shown has no features to open in it (such as Music, which is its own settings), or does not
	 * list the feature the pane shows: its settings would stay beside a page they do not belong to (such as Home after a feature of World).
	 */
	private void closePaneOffList() {
		Feature page = pages.get(selectedIndex);
		if (editor != null && !paneClosing && (!hostsPane(page) || editor.single != null && !lists(page, editor.single))) {
			closeEditor();
		}
	}

	private static boolean hostsPane(Feature page) {
		return page instanceof FeatureListFeature list && list.opensFeatures() || page instanceof HomeFeature;
	}

	/** @return whether a page lists a feature in its own list, so the feature's settings in the pane belong next to it */
	private static boolean lists(Feature page, Feature feature) {
		return page instanceof FeatureListFeature list && (list.holds(feature) || list.holdsAll(feature));
	}

	private Ui createUi(GuiGraphicsExtractor graphics) {
		long now = System.nanoTime();
		float delta = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		boolean animated = menu == null || menu.animations();
		MacTheme theme = menu == null ? MacTheme.DARK : menu.theme();
		int accent = menu == null ? SystemColors.BLUE : menu.accentArgb();
		return new Ui(graphics, font, theme.palette(), accent, delta, animated);
	}

	// ---------------------------------------------------------------------------------------------- settings window

	/** How far a sub-option's name is indented per level under the setting it belongs to. */
	private static final int SUB_INDENT = 10;
	/** Two columns of groups: the room between them, and how wide each must at least be, in menu pixels. */
	private static final int COLUMN_GAP = 12;
	private static final int COLUMN_MIN_WIDTH = 200;
	/** The line beside a sub-option: its width, how far it keeps from the row's edges, and how strong its accent is. */
	private static final int SUB_LINE_WIDTH = 2;
	private static final int SUB_LINE_INSET = 3;
	private static final float SUB_LINE_OPACITY = 0.85f;
	private static final int FOOTER_HEIGHT = 30;
	private static final int INTRO_LINE = 11;
	private static final int INTRO_MAX_LINES = 4;
	/** Key of the reset row of a feature, which a settings window shows in its footer instead. */
	private static final String RESET_KEY = "reset_page";
	private static final int TITLE_TILE = 20;
	private static final int TITLE_ICON = 12;
	private static final float TITLE_SCALE = 1.15f;

	/** The introduction of a settings window: the feature's description, wrapped, and where it was laid out. */
	private List<String> introLines = List.of();
	/** Whether the introduction stands above the tabs, fixed, and how tall it is there; 0 when it scrolls with the settings. */
	private boolean introFixed;
	private int introAbove;
	private int introY;
	/** The master switch in a settings window's title bar. */
	private final Spring headerSwitch = Spring.bouncy(Spring.DEFAULT);
	private boolean headerSwitchSet;
	private final ButtonControl pageReset = new ButtonControl(Component.literal("Reset"), false, this::resetPage).confirm();
	private final ButtonControl tabReset = new ButtonControl(Component.literal("Reset this tab"), false, this::resetPage).confirm();

	/** @return the footer's reset: of the open tab on a page that resets by tab, else of the whole page */
	private ButtonControl footerReset() {
		Feature feature = footerFeature();
		return feature != null && feature.resetsByTab() && !subTitles.isEmpty() ? tabReset : pageReset;
	}
	/** Settings of the window's feature that differ from their defaults, counted when the page is built and when a setting changes. */
	private int changedCount;
	private String footerText = "";

	/** @return whether this is a feature's settings window (not a HUD style window, not the menu settings), which has an intro and a footer */
	private boolean featureWindow() {
		return single != null && !(single instanceof MenuFeature);
	}

	private boolean hasIntro() {
		return featureWindow() && !searching() && !showsSheet();
	}

	private boolean hasFooter() {
		return footerFeature() != null && !(single != null && showsSheet());
	}

	/**
	 * @return the feature the footer counts and resets: the feature of a settings window, or the feature a page is all about (its switch is in
	 * the page's header, as on the Music page) if it has a reset row; null for no footer
	 */
	private Feature footerFeature() {
		if (featureWindow()) {
			return single;
		}
		// a page in tabs that resets one tab at a time (Menu settings); not over search results, which are no tab
		if (single == null && !pages.isEmpty() && page().resetsByTab() && !searching()) {
			return page();
		}
		if (single == null && !pages.isEmpty() && page() instanceof FeatureListFeature list && list.headerSwitch() != null) {
			Feature feature = list.headerSwitch();
			for (Setting<?> setting : feature.settings()) {
				if (setting.key().equals(RESET_KEY)) {
					return feature;
				}
			}
		}
		return null;
	}

	/** Resets the feature's settings from the footer; the feature stays on or off. */
	private void resetPage() {
		Feature feature = footerFeature();
		if (feature == null) {
			return;
		}
		if (resetsTab(feature)) {
			feature.resetSettings(this::inOpenTab);
		} else {
			feature.resetSettings();
		}
		UiSounds.play(UiSounds.Cue.OFF);
		onSettingChanged();
		rebuildRequested = true;
	}

	/** @return whether the footer counts and resets only the open tab of this page */
	private boolean resetsTab(Feature feature) {
		return feature.resetsByTab() && !subTitles.isEmpty() && subTitle != null;
	}

	/** @return whether a setting of the page is on the open tab */
	private boolean inOpenTab(Setting<?> setting) {
		String section = page().sectionOf(setting);
		return subTitle != null && section != null && subTitle.equals(tabOf(section));
	}

	private void countChanged() {
		Feature feature = footerFeature();
		changedCount = feature == null ? 0 : resetsTab(feature) ? feature.changedSettings(this::inOpenTab) : feature.changedSettings();
		footerText = changedCount == 0 ? "All at their defaults" : changedCount == 1 ? "1 changed from default" : changedCount + " changed from default";
	}

	private int headerSwitchX() {
			// the switch sits left of the close button of a window of its own; the pane has none, it goes back with its back button
		return windowX + windowWidth - PAD - SwitchControl.TRACK_WIDTH - (owner == null ? PANE_BUTTON + 8 : 0);
	}

	private int headerSwitchY() {
		return windowY + TITLE_BAR / 2 - SwitchControl.TRACK_HEIGHT / 2;
	}

	private boolean overHeaderSwitch(double mouseX, double mouseY) {
		return featureWindow() && !showsSheet() && page().isSwitchable() && mouseX >= headerSwitchX() - 4 && mouseX < headerSwitchX() + SwitchControl.TRACK_WIDTH + 4
			&& mouseY >= headerSwitchY() - 4 && mouseY < headerSwitchY() + SwitchControl.TRACK_HEIGHT + 4;
	}

	/**
	 * Draws a settings window's title bar: in the pane the back button with the page beside it, in a window of its own the feature's icon on its
	 * tile; then its name, its master switch and, in a window of its own, the close button.
	 */
	private void drawTitleBar(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		Feature page = page();
		int middle = windowY + TITLE_BAR / 2;
		int nameX;
		if (owner != null) {
			int chip = drawBackChip(ui, owner.page().nameText(), windowX + PAD, middle, (windowWidth - 2 * PAD) / 3, !showsSheet(), mouseX, mouseY);
			nameX = windowX + PAD + chip + BACK_CHIP_TITLE_GAP;
		} else {
			int tileX = windowX + PAD;
			ui.iconTile(page.iconName(), tileX, middle - TITLE_TILE / 2, TITLE_TILE, TITLE_ICON, Radius.SM, 0xFF000000 | page.iconRgb(), 0xFFFFFFFF);
			nameX = tileX + TITLE_TILE + 8;
		}
		boolean switchable = featureWindow() && page.isSwitchable();
		int nameRight = switchable ? headerSwitchX() - 10 : paneCloseX() - 8;
		int room = Math.round((nameRight - nameX) / TITLE_SCALE);
		ui.textAtScaledBold(ui.fit(headerTitle, room), nameX, middle - 5.5f, TITLE_SCALE, palette.text());
		if (switchable) {
			boolean on = page.isAnyOn();
			if (!headerSwitchSet) {
				headerSwitch.snap(on ? 1f : 0f);
				headerSwitchSet = true;
			}
			SwitchControl.draw(ui, headerSwitchX(), headerSwitchY(), headerSwitch.follow(ui, on ? 1f : 0f), overHeaderSwitch(mouseX, mouseY) ? 1f : 0f, 0f);
		}
		ui.fill(windowX + 1, windowY + TITLE_BAR, windowWidth - 2, 1, palette.cardBorder());
		if (owner == null && !showsSheet()) {
			drawPaneButtons(ui, mouseX, mouseY);
		}
	}

	/** Draws a settings window's own title bar while a sheet fades in over it: until the sheet covers it, it is still seen under the sheet. */
	private void drawTitleBarUnderSheet(Ui ui) {
		Feature sheet = subPage;
		String title = headerTitle;
		subPage = null;
		headerTitle = page().nameText();
		drawTitleBar(ui, -1, -1);
		subPage = sheet;
		headerTitle = title;
	}

	/** Draws the introduction above the settings; it scrolls with them. */
	private void drawIntro(Ui ui, List<String> lines, int y) {
		int color = Ui.mix(ui.palette.text(), ui.palette.textSecondary(), 0.45f);
		for (int i = 0; i < lines.size(); i++) {
			ui.text(lines.get(i), contentLeft + 2, y + i * INTRO_LINE, color);
		}
	}

	/** Draws the footer: how many settings differ from their defaults, and the button that resets them. */
	private void drawFooter(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int top = windowY + windowHeight - FOOTER_HEIGHT;
		ui.fill(windowX + side() + 1, top, listRight() - windowX - side() - 2, 1, palette.cardBorder());
		int middle = top + FOOTER_HEIGHT / 2;
		ui.textMiddle(footerText, contentLeft + 2, middle, palette.textSecondary());
		ButtonControl reset = footerReset();
		int buttonWidth = reset.inlineWidth(ui, 120);
		reset.place(ui, contentRight - buttonWidth, middle - 10, buttonWidth);
		reset.render(ui, mouseX, mouseY);
	}


	// ----------------------------------------------------------------------------------------------------- layout

	/** A named group of a page's feature list: the space after it (with the heading's own 4 px about 22 before the next name), and the thin line right of its name (its gap, its height below the name's top, its strength). */
	private static final int PAGE_GROUP_GAP = 18;
	private static final int PAGE_GROUP_LINE_GAP = 10;
	private static final int PAGE_GROUP_LINE_DROP = 6;
	private static final float PAGE_GROUP_LINE_OPACITY = 0.1f;

	/** @return whether a group is a named group of a page's features (their rows in one box), not a group of settings */
	private boolean pageGroup(Group group) {
		return single == null && !searching() && page() instanceof FeatureListFeature && group.bare() && showsTitle(group) && !group.rows().isEmpty()
			&& group.rows().get(0).setting() instanceof FeatureGroupSetting;
	}

	/** @return whether a group's title is drawn: not when it is empty, and not when it only repeats the name of the open tab */
	private boolean showsTitle(Group group) {
		// a title of spaces keeps a group apart from the others without a title over it
		return !headingOf(group.title()).isBlank() && !(!subTitles.isEmpty() && group.title().equals(subTitle));
	}

	// a page that keeps everything in view as far as it can
	private static final int COMPACT_ROW_PAD = 3;
	private static final int COMPACT_TITLE_TOP = 1;
	private static final int COMPACT_TITLE = 14;
	private static final int COMPACT_GROUP_GAP = 5;
	/** How much higher the rows start: its header has one short line under the title. */
	private static final int COMPACT_HEADER = 8;

	private boolean compactPage() {
		return !searching() && page().compactRows();
	}

	/** Lays out every row of the page. Controls keep the position they are given for drawing and hit testing. */
	private void layout(Ui ui) {
		layout(ui, groups, placedPool, placed);
	}

	/** Lays out groups of rows as the page's content: usually the page's own; during a change of its content also the rows shown before. */
	private void layout(Ui ui, List<Group> groups, List<Placed> placedPool, List<Placed> placed) {
		placed.clear();
		applyGeometry();
		// a settings window starts below its title bar, a page below its header; both below the tabs if there are any
		int top = single != null && !showsSheet() ? windowY + TITLE_BAR + 10 : windowY + 66 + sheetTop() - (compactPage() ? COMPACT_HEADER : 0);
		// with tabs, a settings window's introduction stands above them, once; without, it scrolls with the settings
		introFixed = hasIntro() && !subTitles.isEmpty() && single != null;
		int introWidth = contentRight - contentLeft - previewRoom() - 4;
		List<String> fixedIntro = introFixed ? ui.wrap(page().descriptionText(), introWidth, INTRO_MAX_LINES) : List.of();
		introAbove = fixedIntro.isEmpty() ? 0 : fixedIntro.size() * INTRO_LINE + 6;
		contentTop = subTitles.isEmpty() ? top : tabsY() + TAB_HEIGHT + 10;
		contentBottom = windowY + windowHeight - 8 - (hasFooter() ? FOOTER_HEIGHT : 0);
		// a page with a live preview leaves its right side to it
		int contentWidth = contentRight - contentLeft - previewRoom();

		int slide = Math.round((1f - Mth.clamp(pageAnimation, 0f, 1f)) * 16f);
		int y = contentTop - (int) scroll + slide;
		// a settings window opens with what the feature does, scrolling with the settings
		introLines = introFixed ? fixedIntro : hasIntro() ? ui.wrap(page().descriptionText(), contentWidth - 4, INTRO_MAX_LINES) : List.of();
		introY = introFixed ? top - 2 : y;
		if (!introLines.isEmpty() && !introFixed) {
			y += introLines.size() * INTRO_LINE + 10;
		}
		boolean compact = compactPage();
		int rowPad = compact ? COMPACT_ROW_PAD : ROW_PAD;
		// a tab that asks for it gets two columns where both are wide enough: the first half of its groups on the left, the rest on the right
		int columnWidth = (contentWidth - COLUMN_GAP) / 2;
		boolean twoColumns = !searching() && !subTitles.isEmpty() && subTitle != null && page().twoColumns(subTitle) && columnWidth >= COLUMN_MIN_WIDTH;
		int split = (groups.size() + 1) / 2;
		int[] columnY = {y, y};
		int pageWidth = contentWidth;
		int pageLeft = contentLeft;
		// one width for the page's segmented choices, dropdowns, key fields and sliders: the standard one, or the wider one where texts need it
		int widestNeed = 0;
		for (Group group : groups) {
			for (Row row : group.rows()) {
				widestNeed = Math.max(widestNeed, row.control().standardNeed(ui));
			}
		}
		pageControlWidth = ControlWidths.forPage(widestNeed);
		// switches sit at the right edge of every row; on pages where rows have a switch plus an Edit button, buttons without a switch align with
		// the Edit buttons so they form one column (editColumn)
		for (int g = 0; g < groups.size(); g++) {
			Group group = groups.get(g);
			if (g == placedPool.size()) {
				placedPool.add(new Placed());
			}
			Placed p = placedPool.get(g);
			int column = twoColumns && g >= split ? 1 : 0;
			int groupLeft = pageLeft;
			if (twoColumns) {
				y = columnY[column];
				contentWidth = columnWidth;
				groupLeft = pageLeft + column * (columnWidth + COLUMN_GAP);
			}
			p.group = group;
			p.rows = 0;
			p.titleY = 0;
			p.top = y;
			p.titled = showsTitle(group);
			if (p.titled) {
				p.titleY = y + (compact ? COMPACT_TITLE_TOP : 4);
				y += compact ? COMPACT_TITLE : 22;
			}
			p.cardX = groupLeft;
			p.cardY = y;
			p.cardWidth = contentWidth;

			int rowY = y;
			// bare cards before the first setting (the theme cards) sit in their own row above the group's card, not inside it
			boolean leading = !group.bare();
			for (Row row : group.rows()) {
				Control control = row.control();
				int innerX = groupLeft + CARD_PAD;
				int innerWidth = contentWidth - 2 * CARD_PAD;
				// sub-options are indented under the setting they belong to
				int indent = row.depth * SUB_INDENT;
				int rowHeight;
				// description under the name, one or two lines
				int descriptionLines = 0;
				boolean below = false;
				// a control too wide to sit beside its name (a narrow window) gets a line of its own under the name, at the right
				boolean controlBelow = !control.bare() && !control.isBlock() && controlBelow(ui, row, innerWidth, indent);
				if (control.bare()) {
					// bare cards (a page's features): full page width, without a label above or a card around them
					control.place(ui, groupLeft, rowY, contentWidth);
					rowHeight = control.height() + (row == group.rows().get(group.rows().size() - 1) ? 0 : leading ? CARD_GAP : 10);
					if (leading) {
						p.cardY = rowY + rowHeight;
					}
				} else if (control.isBlock() && !control.hasLabel()) {
					// a block that is its own content (a note) has no name above it
					control.place(ui, innerX + indent, rowY + rowPad, innerWidth - indent);
					rowHeight = rowPad * 2 + control.height();
				} else if (control.isBlock()) {
					descriptionLines = descriptionLines(ui, row, innerWidth - indent);
					control.place(ui, innerX + indent, rowY + rowPad + 16 + descriptionLines * DESCRIPTION_LINE, innerWidth - indent);
					rowHeight = rowPad * 2 + 16 + descriptionLines * DESCRIPTION_LINE + control.height();
				} else if (controlBelow) {
					int natural = naturalWidth(ui, control, innerWidth - indent);
					descriptionLines = descriptionLines(ui, row, innerWidth - indent);
					int textHeight = 12 + descriptionLines * DESCRIPTION_LINE;
					int controlX = control.leftOnOwnLine() ? innerX + indent : innerX + innerWidth - natural;
					control.place(ui, controlX, rowY + rowPad + textHeight + CONTROL_BELOW_GAP, natural);
					rowHeight = rowPad * 2 + textHeight + CONTROL_BELOW_GAP + control.height();
				} else {
					int labelWidth = ui.width(row.setting().labelText()) + iconRoom(row.setting()) + tipRoom(row.setting()) + indent;
					// the control gets the width its content needs, up to half the row, and the label is truncated with an ellipsis if it does not fit
					// the rest: a long label must not leave a button too narrow for its text
					int roomLeft = Math.max(60, innerWidth - labelWidth - 16);
					int natural = naturalWidth(ui, control, innerWidth);
					// the name stays whole: the control gives up room down to the least a control needs, not only to half the row
					int inline = natural <= roomLeft ? natural : Math.min(natural, Math.max(roomLeft, Math.min(innerWidth / 2, LEAST_INLINE_CONTROL)));
					int right = innerX + innerWidth - (editColumn && control.isLoneButton() ? Control.SWITCH_COLUMN : 0);
					// an inline control's height does not depend on its position, so place it at the top first
					control.place(ui, right - inline, rowY, inline);
					int beside = right - innerX - inline - iconRoom(row.setting()) - indent - 12;
					// a description squeezed into a narrow column beside a wide control (a text field) goes under the row at its full width instead
					// and so does one that would wrap beside the control but is one line at the full width
					String help = row.setting().description();
					below = help != null && (beside < DESCRIPTION_MIN_ROOM
						|| ui.widthScaled(help, DESCRIPTION_SCALE) > beside && ui.widthScaled(help, DESCRIPTION_SCALE) <= innerWidth - indent);
					descriptionLines = descriptionLines(ui, row, below ? innerWidth - indent : beside);
					int lineHeight = Math.max(control.height(), 20);
					if (below) {
						rowHeight = lineHeight + rowPad * 2 + descriptionLines * DESCRIPTION_LINE + 2;
						control.place(ui, right - inline, rowY + rowPad + (lineHeight - control.height()) / 2, inline);
					} else {
						int textHeight = 12 + descriptionLines * DESCRIPTION_LINE;
						rowHeight = Math.max(lineHeight, textHeight) + rowPad * 2;
						// on the row's middle; with a description of two lines or more, on the title's line, the same in both fonts
						int controlY = descriptionLines >= 2 ? rowY + rowPad + (TITLE_LINE - control.height()) / 2 : rowY + (rowHeight - control.height()) / 2;
						control.place(ui, right - inline, Math.max(rowY + 2, controlY), inline);
					}
				}
				if (!control.bare()) {
					leading = false;
				}
				p.addRow(rowY, rowHeight, descriptionLines, below, controlBelow);
				rowY += rowHeight;
			}
			p.cardHeight = rowY - p.cardY;
			placed.add(p);
			y = rowY + (compact ? COMPACT_GROUP_GAP : pageGroup(group) ? PAGE_GROUP_GAP : 14);
			if (twoColumns) {
				columnY[column] = y;
			}
		}
		if (twoColumns) {
			y = Math.max(columnY[0], columnY[1]);
		}
		contentWidth = pageWidth;
		contentHeight = y + (int) scroll - contentTop;
		scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
		scroll = Mth.clamp(scroll, 0, maxScroll());
	}

	/** Replays the page entrance animation: it slides up and fades in. */
	private void restartPage() {
		pageAnimation = 0f;
		pageSpring.snap(0f);
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - (contentBottom - contentTop) + 10);
	}

	// ------------------------------------------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// a page chosen by a control during a click (a category tile) is shown now, outside of that click
		if (pendingPage != null && single == null && owner == null) {
			int index = pages.indexOf(pendingPage);
			pendingPage = null;
			if (index >= 0) {
				selectFromSidebar(index);
			}
		}
		if (pendingRevealFeature != null && single == null && owner == null) {
			Feature feature = pendingRevealFeature;
			Setting<?> setting = pendingRevealSetting;
			pendingRevealFeature = null;
			pendingRevealSetting = null;
			jumpTo(feature, setting);
		}
		refreshContent();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (owner == null && single == null) {
			trackHistory();
		}
		Ui ui = createUi(graphics);
		endLostDrag();
		// the window's scale and position animate to their targets instead of jumping
		animateGeometry(ui);
		animatePane(ui);
		Ui.setZoom(uiZoom);
		mouseX = (int) inWindowX(mouseX);
		mouseY = (int) inWindowY(mouseY);
		// the pane opens and closes on a spring, and the page's width follows it
		paneShown = Mth.clamp(paneSpring.follow(ui, editor != null && !paneClosing ? 1f : 0f), 0f, 1f);
		if (paneClosing && paneShown <= 0.01f) {
			finishPaneClose();
		}
		applyGeometry();
		boolean overPane = editor != null && mouseX >= paneX() && mouseX < paneX() + paneWidth() && mouseY >= paneY() && mouseY < paneY() + windowHeight;
		// the page underneath the pane gets no hover
		int pageMouseX = overPane ? -10000 : mouseX;
		int pageMouseY = overPane ? -10000 : mouseY;
		layoutFrame(ui);
		Palette palette = ui.palette;

		float open = openSpring.follow(ui, closing ? 0f : 1f);
		if (closing && open <= 0.02f) {
			Ui.setZoom(1f);
			minecraft.gui.setScreen(back);
			return;
		}
		float fade = Mth.clamp(open, 0f, 1f);

		// the window grows from the screen center when opening, overshoots slightly and settles, and reverses when closing
		var pose = graphics.pose();
		pose.pushMatrix();
		float grow = 0.9f + 0.1f * open;
		// grow from the window's own center rather than the screen's, so it does not drift
		float middleX = windowX + windowWidth * zoom / 2f;
		float middleY = frameY + fullHeight * zoom / 2f;
		pose.translate(middleX, middleY);
		pose.scale(grow, grow);
		pose.translate(-middleX, -middleY);
		pose.translate(windowX, frameY);
		pose.scale(zoom, zoom);
		pose.translate(-windowX, -frameY);

		// lifted while dragged: a stronger shadow under the window
		if (windowLift > 0.01f) {
			ui.shadow(windowX, frameY, windowWidth, fullHeightShown(), Radius.XXL, LIFT_SHADOW, LIFT_OPACITY * windowLift * fade);
		}
		boolean solidPane = frameBody(ui, windowX, frameY, windowWidth, fullHeightShown(), fade);
		if (single == null) {
			// the sidebar starts under the top bar: its own corner at the bottom only, the window's at the top belong to the bar
			ui.graphics.enableScissor(windowX, windowY, windowX + SIDEBAR, windowY + windowHeight);
			ui.roundedLeft(windowX, frameY, SIDEBAR, fullHeightShown(), Radius.XXL, Ui.withOpacity(palette.sidebar(), fade));
			ui.graphics.disableScissor();
			ui.fill(windowX + SIDEBAR, windowY, 1, windowHeight - 1, palette.cardBorder());
			ui.fill(windowX + 1, windowY - 1, windowWidth - 2, 1, palette.cardBorder());
		}
		drawColoredLight(ui, fade);
		// the edge light goes over the sidebar, which would otherwise cover it on the left
		frameEdges(ui, windowX, frameY, windowWidth, fullHeightShown(), fade, solidPane);

		// from here on everything fades with the window: text, controls and icons, not only the glass
		Ui.setAlpha(fade);
		if (single == null) {
			drawMenuHeader(ui, mouseX, mouseY);
			drawSidebar(ui, mouseX, mouseY);
			drawSearch(ui, mouseX, mouseY);
		}
		// a pane that covers the page fades the page out as it comes in
		float pageAlpha = editor != null && paneCovers() ? 1f - paneShown : 1f;
		if (pageAlpha > 0.01f) {
			Ui.setAlpha(fade * pageAlpha);
			drawPage(ui, graphics, pageMouseX, pageMouseY);
		}
		Ui.setAlpha(1f);
		if (editor != null) {
			placePane();
			// a floating pane is a window of its own; its frame fades as it docks, where the menu's window holds it
			float frame = paneDetached() ? fade * paneShown * (1f - paneDocked) : 0f;
			boolean solidFrame = frame > 0.01f && drawPaneBody(ui, frame);
			Ui.setAlpha(fade * paneShown);
			Glass.setInsideWindow(true);
			editor.drawAsPane(graphics, mouseX, mouseY);
			Glass.setInsideWindow(false);
			if (frame > 0.01f) {
				Ui.setAlpha(1f);
				frameEdges(ui, paneX(), paneY(), paneWidth(), windowHeight, frame, solidFrame);
			}
			drawDockHint(ui, fade);
		}
		if (pageAlpha > 0.01f) {
			Ui.setAlpha(fade * pageAlpha);
			drawOverlays(ui, pageMouseX, pageMouseY);
		}
		Ui.setAlpha(1f);
		pose.popMatrix();
		Ui.setZoom(1f);
		// the move cursor while a window is dragged
		if (windowDrag == WindowDrag.MOVE || paneDragging) {
			graphics.requestCursor(CursorTypes.RESIZE_ALL);
		}
	}

	/**
	 * Draws a window's body: glass, or the solid theme's pane with its shadow and glow, in one pass where possible. Content drawn afterwards is
	 * in front of it. @return whether the solid pane was drawn in one pass (its edge line included)
	 */
	private boolean frameBody(Ui ui, int x, int y, int w, int h, float fade) {
		Palette palette = ui.palette;
		if (palette.glass()) {
			Glass.body(ui, x, y, w, h, Radius.XXL, fade, true, UiGlow.Target.MENU);
			return false;
		}
		boolean light = menu != null && menu.theme() == MacTheme.LIGHT;
		boolean solid = Glass.solid(ui, x, y, w, h, Radius.XXL, fade * 0.94f, UiGlow.Target.MENU, fade, light, palette.window(), palette.windowBorder());
		if (!solid) {
			ui.shadow(x, y, w, h, Radius.XXL, 22, 0.55f * fade);
			UiGlow.halo(ui, x, y, w, h, Radius.XXL, UiGlow.Target.MENU, fade);
			ui.rounded(x, y, w, h, Radius.XXL, Ui.withOpacity(palette.window(), fade));
		}
		return solid;
	}

	/** Draws a window's edge: the glass's edge light, or the solid theme's line and glow, over its content. */
	private void frameEdges(Ui ui, int x, int y, int w, int h, float fade, boolean solid) {
		Palette palette = ui.palette;
		if (palette.glass()) {
			Glass.edges(ui, x, y, w, h, Radius.XXL, fade, true, UiGlow.Target.MENU);
			return;
		}
		ui.ring(x, y, w, h, Radius.XXL, 1, Ui.withOpacity(palette.windowBorder(), fade));
		if (!solid) {
			UiGlow.edge(ui, x, y, w, h, Radius.XXL, UiGlow.Target.MENU, fade);
		}
	}

	/** Once per second, checks whether the page's content changed on its own; rebuilds the page when asked to. */
	private void refreshContent() {
		// the content can change on its own without a setting change (a song starts and the home page shows it): check once per second
		long now = System.currentTimeMillis();
		if (now - lastShownCheck > 1000L) {
			lastShownCheck = now;
			if (single == null) {
				refreshHere();
			}
			if (!searching() && !pageEntries().stream().map(Entry::setting).toList().equals(visibleSettings)) {
				rebuildRequested = true;
			}
		}
		if (rebuildRequested) {
			rebuildRequested = false;
			buildPage();
		}
	}

	/** Scrolls toward the scroll target, lays out the page and resolves what it should scroll to. */
	private void layoutFrame(Ui ui) {
		if (ui.animated) {
			float follow = 1f - (float) Math.exp(-ui.deltaSeconds * 14f);
			scroll = Math.abs(scrollTarget - scroll) < 0.4 ? scrollTarget : scroll + (scrollTarget - scroll) * follow;
		} else {
			scroll = scrollTarget;
		}
		tooltip = null;
		layout(ui);
		if (pendingSetting != null && !rebuildRequested) {
			resolvePending();
		}
		Control.setView(contentTop, contentBottom);
		if (pendingSection != null) {
			for (Placed p : placed) {
				if (p.group.title().startsWith(pendingSection)) {
					scrollTarget = Mth.clamp(p.cardY - contentTop + scroll - 30, 0, maxScroll());
					break;
				}
			}
			pendingSection = null;
		}
	}

	/**
	 * Draws the settings pane inside the menu's window: the menu has set its place and the pose, and gives the mouse in its window coordinates.
	 * It has the parts of a settings window without the window: a divider on its left, its title bar with a close button, and the page.
	 */
	private void drawAsPane(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		refreshContent();
		Ui ui = createUi(graphics);
		layoutFrame(ui);
		// the divider to the page, while docked
		if (!paneCoversPage() && !owner.paneDetached()) {
			ui.fill(windowX, windowY + 1, 1, windowHeight - 2, ui.palette.cardBorder());
		}
		drawPage(ui, graphics, mouseX, mouseY);
		drawOverlays(ui, mouseX, mouseY);
	}

	/** Draws the page: its header or title bar, tabs, settings, preview and footer, but not the popups on top. */
	private void drawPage(Ui ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		// the back button shown is found anew each frame, by whatever header draws it
		backChipX = -1;
		boolean sheet = showsSheet();
		if (single != null && !sheet) {
			drawTitleBar(ui, mouseX, mouseY);
		} else if (single != null && sheetLight() < 1f) {
			drawTitleBarUnderSheet(ui);
		}
		if (sheet) {
			drawSheetSurface(ui);
		}
		if (single == null) {
			drawHeader(ui, mouseX, mouseY);
		} else if (sheet) {
			drawSheetHeader(ui, mouseX, mouseY);
		}
		if (outgoing != null && outgoing.sheet != null && !sheet) {
			drawOutgoingSheet(ui);
		}
		if (introFixed && !introLines.isEmpty()) {
			drawIntro(ui, introLines, introY);
		}
		drawSubTabs(ui, mouseX, mouseY);

		// the scrollable page, clipped at its edges
		graphics.enableScissor(windowX + side() + 1, contentTop - 2, listRight() - 1, contentBottom);
		boolean overlayOpen = openOverlay() != null;
		int pointerX = overlayOpen ? -1 : mouseX;
		int pointerY = overlayOpen ? -1 : mouseY;
		// a change of the content: what was shown fades out while the new content fades in; tabs also move a few pixels across
		if (tabSwitch < 1f) {
			tabSwitch = ui.approach(tabSwitch, 1f, 1f / TAB_SWITCH_SECONDS);
		}
		boolean switching = tabSwitch < 1f;
		if (!switching) {
			outgoing = null;
		}
		float light = switching ? eased(tabSwitch) : 1f;
		float slide = switching && !ModFeatures.MENU.reducedAnimations() ? tabDirection * TAB_SLIDE : 0f;
		for (Placed p : placed) {
			p.light(light);
		}
		// the introduction of the same page stays as it is
		float introLight = outgoing != null && outgoing.shared ? 1f : light;
		if (outgoing != null) {
			drawOutgoing(ui, light, slide * light);
		}
		float alphaBefore = Ui.alpha();
		if (slide != 0f) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(slide * (1f - light), 0f);
		}
		if (!introLines.isEmpty() && !introFixed) {
			Ui.setAlpha(alphaBefore * introLight);
			drawIntro(ui, introLines, introY);
			Ui.setAlpha(alphaBefore);
		}
		for (Placed p : placed) {
			drawGroup(ui, p, pointerX, pointerY);
		}
		Ui.setAlpha(alphaBefore * (outgoing != null && outgoing.shared ? 1f : light));
		for (int c = 0; c < controlList.size(); c++) {
			Control control = controlList.get(c);
			control.renderSticky(ui, pointerX, pointerY);
		}
		Ui.setAlpha(alphaBefore);
		if (slide != 0f) {
			graphics.pose().popMatrix();
		}
		if (searching() && placed.isEmpty()) {
			ui.textCentered("Nothing found for \"" + ui.fit(query.strip(), 200) + "\"", (contentLeft + contentRight) / 2, contentTop + 48, palette.textSecondary());
		}
		// the page fades in over the window
		pageAnimation = pageSpring.follow(ui, 1f);
		if (pageAnimation < 1f && !palette.glass() && !(ModFeatures.MENU != null && ModFeatures.MENU.backgroundGlow())) {
			// glass has nothing to cover with, and a cover in the window color would hide the colored light: the slide-in alone is enough
			ui.fill(windowX + side() + 1, contentTop - 2, listRight() - windowX - side() - 2, contentBottom - contentTop + 2, Ui.withOpacity(palette.window() | 0xFF000000, 1f - Mth.clamp(pageAnimation, 0f, 1f)));
		}
		graphics.disableScissor();
		drawScrollBar(ui);
		PagePreview preview = pagePreview();
		if (preview != null) {
			drawPagePreview(ui, preview);
		}
		if (hasFooter()) {
			drawFooter(ui, pointerX, pointerY);
		}
	}

	/** Draws open dropdowns and popups on top of everything, so the page does not clip them, and the tooltip. */
	private void drawOverlays(Ui ui, int mouseX, int mouseY) {
		if (tabDropdown.isOverlayOpen()) {
			tabDropdown.renderOverlay(ui, mouseX, mouseY);
		}
		for (int c = 0; c < controlList.size(); c++) {
			Control control = controlList.get(c);
			if (control.isOverlayOpen()) {
				control.renderOverlay(ui, mouseX, mouseY);
			}
		}
		// a control's value too long for its room shows in full on hover
		String full = Control.takeFullText();
		if (tooltip == null) {
			tooltip = full;
		}
		drawTooltip(ui, mouseX, mouseY);
	}

	private float closeHover;

	private int menuCloseX() {
		return windowX + windowWidth - SIDE_INSET - 2 - CLOSE_BUTTON;
	}

	private int menuCloseY() {
		return frameY + (TOP_BAR - CLOSE_BUTTON) / 2;
	}

	/** @return whether the mouse is over the close button of the menu's header row */
	private boolean overMenuClose(double mouseX, double mouseY) {
		return single == null && mouseX >= menuCloseX() - 2 && mouseX < menuCloseX() + CLOSE_BUTTON + 2 && mouseY >= menuCloseY() - 2
			&& mouseY < menuCloseY() + CLOSE_BUTTON + 2;
	}

	/** Draws the menu's top bar: the Lumio mark, "Lumio" and the version on a chip at the left, and the round close button at the right; the search is drawn on its own. */
	private void drawMenuHeader(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int x = windowX + SIDE_INSET + 4;
		int middle = frameY + TOP_BAR / 2;
		ui.rounded(x, middle - MARK / 2, MARK, MARK, TILE_RADIUS, ui.accent);
		ui.textCenteredBold(MARK_LETTER, x + MARK / 2f, middle, 0xFFFFFFFF);
		int nameX = x + MARK + 6;
		ui.textAtScaledBold(MENU_NAME, nameX, middle - 4.5f, 1f, palette.text());
		int versionX = Math.round(nameX + ui.boldWidthScaled(MENU_NAME, 1f) + 6f);
		// the version whole, or its number alone where that does not fit beside the narrowest search, or nothing: never cut
		int room = menuCloseX() - 2 * MenuBar.GAP - SEARCH_LEAST - versionX;
		String version = ModVersion.shortDisplay();
		if (ui.chipWidth(version) > room) {
			String number = version.split(" ")[0];
			version = ui.chipWidth(number) <= room ? number : "";
		}
		barLeftEnd = version.isEmpty() ? versionX - 6 : versionX + ui.chip(version, versionX, middle);
		closeHover = ui.approach(closeHover, overMenuClose(mouseX, mouseY) ? 1f : 0f, Ui.HOVER_RATE);
		ui.rounded(menuCloseX(), menuCloseY(), CLOSE_BUTTON, CLOSE_BUTTON, Radius.pill(CLOSE_BUTTON), Ui.withOpacity(palette.hover(), 0.7f + 0.3f * closeHover));
		ui.icon("close", menuCloseX() + 4, menuCloseY() + 4, CLOSE_BUTTON - 8, Ui.mix(palette.textSecondary(), palette.text(), closeHover));
	}

	private static final String MENU_NAME = "Lumio";
	private static final String MARK_LETTER = "L";

	// ------------------------------------------------------------------------------------------------------ sidebar

	/** Top of the first page item, under the top bar. */
	private static final int ITEMS_TOP = 8;
	// one spacing rule, as in the mockup: every row 38 px tall there with 24 px icon tiles, rows of a group 2 px apart, groups 18 px
	private static final int ITEM_HEIGHT = 24;
	private static final int ITEM_GAP = 1;
	/** The small uppercase label over a group, and the room it takes with the space under it. */
	private static final float GROUP_LABEL_SCALE = TextScale.SMALL;
	private static final int GROUP_LABEL_ROW = 12;
	private static final int GROUP_LABEL_LEFT = 6;
	/** Size of the colored tile behind a page icon, and its corner radius. */
	private static final int TILE = 16;
	private static final int TILE_ICON = 9;
	private static final float TILE_RADIUS = 4f;
	private static final int TILE_LEFT = 6;
	/** Space below the last item of the bottom section (Edit HUD layout, Menu settings). */
	private static final int BOTTOM_PAD = 8;

	/** A category item sits indented under its page, as tall and as far apart as every row. */
	private static final int SUB_ITEM_HEIGHT = ITEM_HEIGHT;
	/** Indent of the category items, and where the guide line in front of them runs. */
	private static final int SUB_ITEM_INDENT = 20;
	private static final int SUB_GUIDE_X = 13;
	/** The counts on the right of the rows: small, in the secondary color; a detail and no notification. */
	private static final float COUNT_SCALE = TextScale.SMALL;
	/** The tag a developer's page shows instead of a count. */
	private static final String DEV_TAG = "DEV";
	private static final float DEV_TAG_SCALE = 0.62f;
	/** Folded out further than this, categories are drawn. */
	private static final float SUBS_SHOWN = 0.02f;

	/** What a page item shows on its right: the number of features, or null; built once, the pages do not change while the menu is open. */
	private String[] sidebarCounts;
	private float hudItemHover;
	/** Tops of the page items relative to the window, laid out every frame since the categories fold in and out. */
	private int[] sidebarTops = new int[0];
	/** Per page item: how far the categories under that page are folded out, from 0 to 1 (SkyBlock, Extras); 0 for the others. */
	private float[] sidebarExpand = new float[0];
	private Spring[] expandSprings = new Spring[0];
	/** Per page item: the index of the page it is a category of, or -1. */
	private int[] sidebarParents = new int[0];
	/** The sidebar highlight: in the list's content coordinates, gliding only when the selection changes. */
	private final SelectionGlide highlight = new SelectionGlide();

	/** @return whether the page is a category of another page, listed indented under it */
	private boolean isSubPage(int index) {
		return pages.get(index) instanceof FeatureListFeature list && list.parentPage() != null;
	}

	private int itemHeightOf(int index) {
		return isSubPage(index) ? SUB_ITEM_HEIGHT : ITEM_HEIGHT;
	}

	/** @return the page item whose categories are folded out: the shown page when it has categories, the page of the shown category, or -1 */
	private int openParent() {
		int selected = Mth.clamp(selectedIndex, 0, pages.size() - 1);
		if (pages.get(selected) instanceof FeatureListFeature list && list.hasSubPages()) {
			return selected;
		}
		return sidebarParents.length == pages.size() ? sidebarParents[selected] : -1;
	}

	/** @return how far the category item is folded out, from 0 to 1; 1 for a page item */
	private float expandOfSub(int index) {
		int parent = sidebarParents[index];
		return parent < 0 ? 1f : sidebarExpand[parent];
	}

	/**
	 * Lays out the page items: Home, then the groups under their labels with a gap before each, and fixed at the bottom the tools (Configs, the
	 * HUD editor, the menu settings) under theirs. The part in between scrolls when the window is too small for it.
	 */
	private void layoutSidebar(Ui ui) {
		boolean first = sidebarTops.length != pages.size();
		if (first) {
			sidebarTops = new int[pages.size()];
			sidebarExpand = new float[pages.size()];
			expandSprings = new Spring[pages.size()];
			sidebarParents = new int[pages.size()];
			for (int i = 0; i < pages.size(); i++) {
				sidebarParents[i] = isSubPage(i) ? pages.indexOf(((FeatureListFeature) pages.get(i)).parentPage()) : -1;
				if (pages.get(i) instanceof FeatureListFeature list && list.hasSubPages()) {
					expandSprings[i] = Spring.smooth(Spring.DEFAULT);
				}
			}
		}
		int open = openParent();
		for (int i = 0; i < pages.size(); i++) {
			if (expandSprings[i] != null) {
				float target = i == open ? 1f : 0f;
				// the categories fold on the same spring as the highlight moving between items, so both arrive together; each page's on its own,
				// so one folds in while the other folds out
				sidebarExpand[i] = first ? expandSprings[i].snap(target) : Mth.clamp(expandSprings[i].follow(ui, target), 0f, 1f);
			}
		}
		float y = ITEMS_TOP;
		int regular = regularCount();
		java.util.Arrays.fill(groupLabelTops, NO_LABEL);
		for (int i = 0; i < regular; i++) {
			if (gapBefore(i)) {
				y += GROUP_GAP;
				groupLabelTops[groupOf(i).ordinal()] = Math.round(y);
				y += GROUP_LABEL_ROW;
			}
			sidebarTops[i] = Math.round(y);
			y += (ITEM_HEIGHT + ITEM_GAP) * expandOfSub(i);
		}
		// what does not fit scrolls, above the fixed part at the bottom and never over it
		int toolsTop = toolsTop();
		sidebarListEnd = toolsTop - GROUP_LABEL_ROW - SIDEBAR_BOTTOM_ROOM;
		sidebarMostScroll = Math.max(0f, y - sidebarListEnd);
		keepSelectedInSidebar(regular);
		sidebarScroll = Mth.clamp(sidebarScroll, 0f, sidebarMostScroll);
		int moved = Math.round(sidebarScroll);
		for (int i = 0; i < regular; i++) {
			sidebarTops[i] -= moved;
		}
		for (int group = 0; group < groupLabelTops.length; group++) {
			groupLabelTops[group] -= groupLabelTops[group] == NO_LABEL ? 0 : moved;
		}
		// the tools: the HUD editor first, then the pages of that group (Configs), the menu settings last
		groupLabelTops[SidebarOrder.Group.TOOLS.ordinal()] = toolsTop - GROUP_LABEL_ROW;
		int row = 1;
		for (int i = regular; i < pages.size(); i++) {
			sidebarTops[i] = toolsTop + row * (ITEM_HEIGHT + ITEM_GAP);
			row++;
		}
	}

	/** Where the label over each group is, from the window's top, or {@link #NO_LABEL}; laid out with the items. */
	private final int[] groupLabelTops = new int[SidebarOrder.Group.values().length];
	private static final int NO_LABEL = Integer.MIN_VALUE;

	/** @return how many rows the fixed part at the bottom has: its pages and the HUD editor */
	private int toolRows() {
		return pages.size() - regularCount() + 1;
	}

	/** @return the top of the first row of the tools at the bottom, from the window's top */
	private int toolsTop() {
		return windowHeight - BOTTOM_PAD - toolRows() * ITEM_HEIGHT - (toolRows() - 1) * ITEM_GAP;
	}

	/** Where the sidebar's list ends, from the window's top, how far it can scroll, and how far it has. */
	private int sidebarListEnd;
	private float sidebarMostScroll;
	private float sidebarScroll;
	private int sidebarShownSelected = -1;

	/** When another page is selected, the list scrolls so that its item is in view. */
	private void keepSelectedInSidebar(int regular) {
		if (selectedIndex == sidebarShownSelected || selectedIndex < 0 || selectedIndex >= regular) {
			sidebarShownSelected = selectedIndex;
			return;
		}
		sidebarShownSelected = selectedIndex;
		int top = sidebarTops[selectedIndex];
		if (top < ITEMS_TOP + sidebarScroll) {
			sidebarScroll = top - ITEMS_TOP;
		} else if (top + ITEM_HEIGHT > sidebarListEnd + sidebarScroll) {
			sidebarScroll = top + ITEM_HEIGHT - sidebarListEnd;
		}
	}

	/** @return whether a point is in the part of the sidebar where its list shows */
	private boolean inSidebarList(double mouseY) {
		return mouseY >= windowY + ITEMS_TOP && mouseY < windowY + sidebarListEnd;
	}

	/** @return the page item under the mouse, or -1; folded categories cannot be hit */
	private int sidebarItemAt(double mouseX, double mouseY) {
		int itemX = windowX + SIDE_INSET;
		if (single != null || mouseX < itemX || mouseX >= itemX + SIDEBAR - 2 * SIDE_INSET || sidebarTops.length != pages.size()) {
			return -1;
		}
		for (int i = 0; i < pages.size(); i++) {
			if (isSubPage(i) && expandOfSub(i) < 0.9f) {
				continue;
			}
			if (i < regularCount() && !inSidebarList(mouseY)) {
				continue;
			}
			int top = windowY + sidebarTops[i];
			if (mouseY >= top && mouseY < top + itemHeightOf(i)) {
				return i;
			}
		}
		return -1;
	}

	private void drawSidebar(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int itemX = windowX + SIDE_INSET;
		int itemWidth = SIDEBAR - 2 * SIDE_INSET;
		layoutSidebar(ui);
		if (sidebarCounts == null) {
			sidebarCounts = new String[pages.size()];
			for (int i = 0; i < pages.size(); i++) {
				// a page with categories has a chevron instead, and a page with a single feature (music) needs no number; categories always show it
				if (SidebarOrder.devPage(pages.get(i).id())) {
					sidebarCounts[i] = DEV_TAG;
				} else if (pages.get(i) instanceof FeatureListFeature list && !list.hasSubPages() && (list.featureCount() > 1 || list.isGroupPage())) {
					sidebarCounts[i] = String.valueOf(list.featureCount());
				}
			}
		}
		if (itemHover.length != pages.size()) {
			itemHover = new float[pages.size()];
		}
		int selected = Mth.clamp(selectedIndex, 0, pages.size() - 1);
		boolean bottomSelected = selected >= regularCount();
		// the highlight lives in the list's content coordinates: the scroll is added back here and taken off again when drawn, so it moves with
		// its row exactly while the list scrolls; only a change of the selection glides (the fixed rows at the bottom do not scroll)
		int scrolled = bottomSelected ? 0 : Math.round(sidebarScroll);
		highlight.follow(selected, sidebarTops[selected] + scrolled, itemHeightOf(selected), isSubPage(selected) ? SUB_ITEM_INDENT : 0f,
				ui.deltaSeconds(), ui.animated);
		int highlightY = windowY + Math.round(highlight.y()) - scrolled;
		int highlightH = Math.round(highlight.height());
		int indent = Math.round(highlight.indent());

		ui.graphics.enableScissor(windowX, windowY + ITEMS_TOP - 1, windowX + SIDEBAR, windowY + sidebarListEnd);
		for (SidebarOrder.Group group : SidebarOrder.Group.values()) {
			if (group != SidebarOrder.Group.TOOLS) {
				drawGroupLabel(ui, group);
			}
		}
		int hovered = sidebarItemAt(mouseX, mouseY);
		for (int i = 0; i < pages.size(); i++) {
			if (!isSubPage(i)) {
				itemHover[i] = ui.approach(itemHover[i], hovered == i && i != selected ? 1f : 0f, Ui.HOVER_RATE);
			}
		}
		// the highlight is drawn before the categories are clipped: moving to or from a category while they fold, it must not be cut off by
		// their shrinking area (it vanished and popped into place when leaving a SkyBlock category)
		if (!bottomSelected && (!isSubPage(selected) || expandOfSub(selected) > SUBS_SHOWN)) {
			ui.rounded(itemX + indent, highlightY, itemWidth - indent, highlightH, Radius.SM, ui.accent);
		}
		for (int parent = 0; parent < pages.size(); parent++) {
			if (sidebarExpand[parent] > SUBS_SHOWN) {
				drawSubItems(ui, parent, hovered, selected);
			}
		}
		int regular = regularCount();
		for (int i = 0; i < regular; i++) {
			if (!isSubPage(i)) {
				drawPageItem(ui, i, selected);
			}
		}
		ui.graphics.disableScissor();
		if (bottomSelected) {
			ui.rounded(itemX + indent, highlightY, itemWidth - indent, highlightH, Radius.SM, ui.accent);
		}
		for (int i = regular; i < pages.size(); i++) {
			if (!isSubPage(i)) {
				drawPageItem(ui, i, selected);
			}
		}

		// the tools at the bottom, under their label: the HUD editor, which is an action rather than a page, is the first of them
		drawGroupLabel(ui, SidebarOrder.Group.TOOLS);
		int hudY = hudItemY();
		boolean overHud = overHudItem(mouseX, mouseY);
		hudItemHover = ui.approach(hudItemHover, overHud ? 1f : 0f, Ui.HOVER_RATE);
		// a neutral tile in the theme's control colors, so it reads in the light theme too
		drawSidebarItem(ui, hudY, hudItemHover, false, palette.trackOff(), "layout", palette.text(), "Edit HUD layout", null, -1f);
	}

	/** Draws the small uppercase label over a group, where the group has one and is shown. */
	private void drawGroupLabel(Ui ui, SidebarOrder.Group group) {
		int top = groupLabelTops[group.ordinal()];
		if (group.label() == null || top == NO_LABEL) {
			return;
		}
		ui.textAtScaledBold(group.label(), windowX + SIDE_INSET + GROUP_LABEL_LEFT, windowY + top + 1.5f, GROUP_LABEL_SCALE,
			ui.palette.textSecondary());
	}

	/** Draws the categories of one page, folding out of it: clipped to the space they have taken so far, along a guide line. */
	private void drawSubItems(Ui ui, int parent, int hovered, int selected) {
		int itemX = windowX + SIDE_INSET;
		int itemWidth = SIDEBAR - 2 * SIDE_INSET;
		// whether there are categories at all is its own flag: scrolled above the screen's top their place is negative, and taking a negative
		// place for "none" (the mistake that blanked the Keybinds page) would skip the first of them or all
		boolean any = false;
		int top = 0;
		int bottom = 0;
		for (int i = 0; i < pages.size(); i++) {
			if (sidebarParents[i] == parent) {
				top = any ? top : windowY + sidebarTops[i];
				any = true;
				bottom = windowY + sidebarTops[i] + Math.round(SUB_ITEM_HEIGHT * sidebarExpand[parent]);
			}
		}
		if (!any) {
			return;
		}
		ui.graphics.enableScissor(itemX, top, itemX + itemWidth, bottom);
		ui.fill(itemX + SUB_GUIDE_X, top + 2, 1, bottom - top - 4, ui.palette.cardBorder());
		for (int i = 0; i < pages.size(); i++) {
			if (sidebarParents[i] == parent) {
				itemHover[i] = ui.approach(itemHover[i], hovered == i && i != selected ? 1f : 0f, Ui.HOVER_RATE);
				drawSubItem(ui, i, i == selected);
			}
		}
		ui.graphics.disableScissor();
	}

	private void drawPageItem(Ui ui, int index, int selected) {
		Feature feature = pages.get(index);
		drawSidebarItem(ui, windowY + sidebarTops[index], itemHover[index], index == selected, 0xFF000000 | feature.iconRgb(), feature.iconName(), 0xFFFFFFFF,
			feature.nameText(), sidebarCounts[index], feature instanceof FeatureListFeature list && list.hasSubPages() ? sidebarExpand[index] : -1f);
		if (feature == ModFeatures.HOME && WhatsNewScreen.unseen()) {
			// something new waits in What is new: a quiet dot at the end of Home's row, never a window of its own
			ui.circle(windowX + SIDEBAR - SIDE_INSET - 10, windowY + sidebarTops[index] + ITEM_HEIGHT / 2, NEW_DOT, SystemColors.GREEN);
		}
	}

	/** Draws a category item: its name indented under its page, and the number of its features. */
	private void drawSubItem(Ui ui, int index, boolean selected) {
		Palette palette = ui.palette;
		FeatureListFeature page = (FeatureListFeature) pages.get(index);
		int itemX = windowX + SIDE_INSET + SUB_ITEM_INDENT;
		int itemWidth = SIDEBAR - 2 * SIDE_INSET - SUB_ITEM_INDENT;
		int top = windowY + sidebarTops[index];
		int middle = top + SUB_ITEM_HEIGHT / 2;
		if (itemHover[index] > 0f) {
			ui.rounded(itemX, top, itemWidth, SUB_ITEM_HEIGHT, Radius.SM, Ui.withOpacity(palette.hover(), itemHover[index]));
		}
		int right = itemX + itemWidth - 6;
		int trailing = drawCount(ui, sidebarCounts[index], right, middle, selected);
		int textX = itemX + 7;
		String name = ui.fit(page.nameText(), right - trailing - textX);
		ui.textMiddle(name, textX, middle, selected ? 0xFFFFFFFF : Ui.mix(palette.text(), palette.textSecondary(), 0.25f));
	}

	/**
	 * Draws what a row shows at its right end: the number of its features, small and dim, or the "DEV" tag of a developer's page.
	 *
	 * @return the room it takes, with the gap before it; 0 without one
	 */
	private int drawCount(Ui ui, String count, int right, int middle, boolean selected) {
		if (count == null) {
			return 0;
		}
		Palette palette = ui.palette;
		if (count.equals(DEV_TAG)) {
			int height = 9;
			int tagColor = selected ? 0xFFFFFFFF : palette.textSecondary();
			int width = Math.round(ui.boldDrawnWidthScaled(DEV_TAG, DEV_TAG_SCALE, Ui.withOpacity(tagColor, 0.8f))) + 7;
			ui.rounded(right - width, middle - height / 2, width, height, Radius.box(height), Ui.withOpacity(tagColor, 0.14f));
			ui.textAtScaledBold(DEV_TAG, right - width + 3.5f, middle - 2.5f, DEV_TAG_SCALE, Ui.withOpacity(tagColor, 0.8f));
			return width + 6;
		}
		float width = ui.widthScaled(count, COUNT_SCALE);
		ui.textAtScaled(count, right - width, middle - 3f, COUNT_SCALE, selected ? 0xF2FFFFFF : palette.textSecondary());
		return Math.round(width) + 6;
	}

	/** Draws one sidebar item: the icon on its colored tile, the name, and on the right a count or a chevron. */
	private void drawSidebarItem(Ui ui, int itemY, float hover, boolean selected, int tile, String icon, int iconColor, String name, String count,
		float chevronOpen) {
		boolean chevron = chevronOpen >= 0f;
		Palette palette = ui.palette;
		int itemX = windowX + SIDE_INSET;
		int itemWidth = SIDEBAR - 2 * SIDE_INSET;
		int middle = itemY + ITEM_HEIGHT / 2;
		if (hover > 0f) {
			ui.rounded(itemX, itemY, itemWidth, ITEM_HEIGHT, Radius.SM, Ui.withOpacity(palette.hover(), hover));
		}
		int tileX = itemX + TILE_LEFT;
		ui.iconTile(icon, tileX, middle - TILE / 2, TILE, TILE_ICON, TILE_RADIUS, tile, iconColor);
		int right = itemX + itemWidth - 6;
		int trailing = 0;
		if (chevron) {
			// pointing right while the categories are folded in, down while they are out
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(right - 4f, middle);
			pose.rotate((float) (-Math.PI / 2.0 * (1f - Mth.clamp(chevronOpen, 0f, 1f))));
			ui.icon("chevron", -4, -4, 8, selected ? 0xCCFFFFFF : Ui.withOpacity(palette.textSecondary(), 0.8f));
			pose.popMatrix();
			trailing = 12;
		} else {
			trailing = drawCount(ui, count, right, middle, selected);
		}
		int textX = tileX + TILE + 6;
		ui.textMiddle(ui.fit(name, right - trailing - textX), textX, middle, selected ? 0xFFFFFFFF : palette.text());
	}

	/** @return top of the HUD editor item, the first of the tools at the bottom */
	private int hudItemY() {
		return windowY + toolsTop();
	}

	private boolean overHudItem(double mouseX, double mouseY) {
		int itemX = windowX + SIDE_INSET;
		return single == null && mouseX >= itemX && mouseX < itemX + SIDEBAR - 2 * SIDE_INSET && mouseY >= hudItemY() && mouseY < hudItemY() + ITEM_HEIGHT;
	}

	/** @return the sidebar group of a page; a category belongs to the group of its page */
	private SidebarOrder.Group groupOf(int index) {
		if (isSubPage(index)) {
			return groupOf(pages.indexOf(((FeatureListFeature) pages.get(index)).parentPage()));
		}
		return pages.get(index) == menu ? SidebarOrder.Group.TOOLS : SidebarOrder.group(pages.get(index).id());
	}

	/** @return how many pages are in the part that scrolls: all but the tools, which are the last ones and fixed at the bottom */
	private int regularCount() {
		int regular = pages.size();
		while (regular > 0 && groupOf(regular - 1) == SidebarOrder.Group.TOOLS) {
			regular--;
		}
		return regular;
	}

	/** @return whether a group starts at a page: its label goes over it, after the gap between groups */
	private boolean gapBefore(int index) {
		return index > 0 && index < regularCount() && groupOf(index) != groupOf(index - 1);
	}

	/** A page to show on the next frame, chosen by a control while it handles a click; the page is not rebuilt under the control. */
	private static Feature pendingPage;
	/** A setting to reveal on the next frame, chosen the same way: from the changes listed on the home page. */
	private static Feature pendingRevealFeature;
	private static Setting<?> pendingRevealSetting;

	/** Shows a setting where it is, as a click on a search result does: on its page or in its feature's window, briefly highlighted. */
	public static void reveal(Feature feature, Setting<?> setting) {
		pendingRevealFeature = feature;
		pendingRevealSetting = setting;
	}

	/** Shows a page of the menu, as a click on it in the sidebar does: used by the category tiles. */
	public static void showPage(Feature page) {
		pendingPage = page;
	}

	/** Breadcrumb and location chip of the shown page, built when the page is built and when the location is checked. */
	private String headerParent;
	private String hereText = "";

	/** Checks once where the player is, for the chip in the header of a category. */
	private void refreshHere() {
		for (Feature page : pages) {
			if (page instanceof FeatureListFeature list) {
				list.refreshHere();
			}
		}
		String island = Addons.location() == null ? "" : Addons.location().placeName();
		hereText = island == null || island.isBlank() ? "You are here" : "You are in " + island;
	}

	/** Draws a page's title and description above it; settings windows have a title bar instead ({@link #drawTitleBar}). */
	private void drawHeader(Ui ui, int mouseX, int mouseY) {
		int left = contentLeft;
		int right = listRight() - PAD;
		Feature own = ownFeature();
		if (own != null) {
			drawOwnHeader(ui, own, right, mouseX, mouseY);
			if (showsSheet()) {
				drawSheetHeader(ui, mouseX, mouseY);
			}
			return;
		}
		if (showsSheet()) {
				pageSwitchX = -1;
			drawSheetHeader(ui, mouseX, mouseY);
			return;
		}
		if (subPage != null && !searching()) {
			drawSubPageHeader(ui, left, right, mouseX, mouseY);
			pageSwitchX = -1;
			return;
		}
		if (headerParent != null) {
			// a category names its page above its title
			ui.textAtScaled(headerParent, left, windowY + 8, TextScale.SMALL, ui.palette.textSecondary());
		}
		Feature page = page();
		if (!searching() && !(page instanceof HomeFeature) && headerParent == null) {
			ui.iconTile(page.iconName(), left, windowY + HEADER_TILE_TOP, HEADER_TILE, HEADER_ICON, Radius.MD, 0xFF000000 | page.iconRgb(), 0xFFFFFFFF);
			left += HEADER_TILE + 9;
		}
		pageSwitchX = -1;
		if (!searching() && page instanceof FeatureListFeature list && list.headerSwitch() != null) {
			right = drawPageSwitch(ui, list.headerSwitch(), right, mouseX, mouseY);
		} else if (!searching() && page instanceof FeatureListFeature list && list.isHere()) {
			right = drawHereChip(ui, right);
		}
		right = drawHeaderButtons(ui, page, right, mouseX, mouseY);
		// page titles in the bold face, as the largest step of the menu's text sizes
		// a title that does not fit beside the header's buttons is set a step smaller before it is cut
		float titleScale = titleScale(ui, headerTitle, right - left);
		String title = ui.fit(headerTitle, Math.round((right - left) / (titleScale * BOLD_WIDER)));
		ui.textAtScaledTitle(title, left, windowY + 18 + (TextScale.TITLE - titleScale) * TITLE_HALF, titleScale, ui.palette.text());
		String chip = searching() ? null : page.headerChip();
		if (chip != null) {
			// a chip after the title, such as the version on the home page, centered on the title's letters
			int chipX = left + Math.round(ui.titleWidthScaled(title, TextScale.TITLE)) + TITLE_CHIP_GAP;
			int chipWidth = ui.chip(chip, chipX, windowY + TITLE_MIDDLE);
			String chipTip = page.headerChipTooltip();
			if (chipTip != null && mouseX >= chipX && mouseX < chipX + chipWidth && Math.abs(mouseY - (windowY + TITLE_MIDDLE)) <= CHIP_HOVER_HALF) {
				tooltip = chipTip;
			}
		}
		// the title may extend to the window edge; a still too long description is shrunk slightly before truncation
		int room = Math.max(40, listRight() - PAD - left);
		String full = headerDescription;
		float scale = Math.max(0.82f, Math.min(1f, room / (float) Math.max(1, ui.width(full))));
		if (scale >= 1f) {
			ui.text(full, left, windowY + 42, ui.palette.textSecondary());
		} else if (ui.widthScaled(full, scale) <= room) {
			ui.textAtScaled(full, left, windowY + 42 + (1f - scale) * 3f, scale, ui.palette.textSecondary());
		} else {
			// still too long: two lines at the smaller size, which still end above the page
			List<String> lines = ui.wrapScaled(full, room, scale, HEADER_DESCRIPTION_LINES);
			for (int i = 0; i < lines.size(); i++) {
				ui.textAtScaled(lines.get(i), left, windowY + HEADER_DESCRIPTION_TOP + i * HEADER_DESCRIPTION_LINE, scale, ui.palette.textSecondary());
			}
		}
	}

	private static final int HEADER_TILE = 26;
	/** A header description of two lines: where it starts, its line height and its lines. */
	private static final int HEADER_DESCRIPTION_TOP = 38;
	private static final int HEADER_DESCRIPTION_LINE = 9;
	private static final int HEADER_DESCRIPTION_LINES = 2;
	private static final int HEADER_TILE_TOP = 16;
	private static final int HEADER_ICON = 17;
	private static final int PAGE_SWITCH_HEIGHT = 24;
	/** A sheet over a feature's own page starts this far below the window's top, under the page's header. */
	private static final int OWN_SHEET_TOP = 52;
	/** The switch in a page's header, from the last frame, for hit testing; -1 while there is none. */
	private int pageSwitchX = -1;
	private int pageSwitchY;
	/** The feature the header switch was last drawn for: another one starts at its own position instead of springing there. */
	private Feature pageSwitchShownFor;

	/** Draws a feature's switch with its name in the page header, ending at the right edge; @return its left edge */
	private int drawPageSwitch(Ui ui, Feature feature, int right, int mouseX, int mouseY) {
		return drawPageSwitch(ui, feature, right, windowY + 17, feature.nameText(), mouseX, mouseY);
	}

	/** Same, at a height and with a label of its own. */
	private int drawPageSwitch(Ui ui, Feature feature, int right, int y, String label, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int width = ui.width(label) + 18 + SwitchControl.TRACK_WIDTH;
		int x = right - width;
		ui.rounded(x, y, width, PAGE_SWITCH_HEIGHT, Radius.MD, palette.control());
		ui.ring(x, y, width, PAGE_SWITCH_HEIGHT, Radius.MD, 1, palette.controlBorder());
		ui.textMiddle(label, x + 8, y + PAGE_SWITCH_HEIGHT / 2, palette.text());
		pageSwitchX = x + width - 3 - SwitchControl.TRACK_WIDTH;
		pageSwitchY = y + (PAGE_SWITCH_HEIGHT - SwitchControl.TRACK_HEIGHT) / 2;
		boolean on = feature.isAnyOn();
		if (pageSwitchShownFor != feature) {
			headerSwitch.snap(on ? 1f : 0f);
			pageSwitchShownFor = feature;
		}
		SwitchControl.draw(ui, pageSwitchX, pageSwitchY, headerSwitch.follow(ui, on ? 1f : 0f), overPageSwitch(mouseX, mouseY) ? 1f : 0f, 0f);
		return x - 8;
	}

	private boolean overPageSwitch(double mouseX, double mouseY) {
		return pageSwitchX >= 0 && mouseX >= pageSwitchX - 4 && mouseX < pageSwitchX + SwitchControl.TRACK_WIDTH + 4 && mouseY >= pageSwitchY - 4
			&& mouseY < pageSwitchY + SwitchControl.TRACK_HEIGHT + 4;
	}

	// ---------------------------------------------------------------------------------------------------- colored light

	// peak opacity of the colored light per theme: stronger behind glass, subtle on dark, faint on light; only the soft outer part shows
	private static final float LIGHT_GLASS = 0.24f;
	private static final float LIGHT_DARK = 0.15f;
	private static final float LIGHT_LIGHT = 0.07f;
	/** The second light is a little weaker than the first. */
	private static final float SECOND_LIGHT = 0.85f;
	// the lights are wide ellipses, larger than the content area (as shares of its width and height), centered beyond its top-left and
	// bottom-right corners so only their soft outer part is visible and the two overlap gently
	private static final float LIGHT_RADIUS_X = 0.7f;
	private static final float LIGHT_RADIUS_Y = 0.6f;
	private static final float LIGHT_OUTSIDE_X = 0.12f;
	private static final float LIGHT_OUTSIDE_Y = 0.18f;
	/** How far the lights drift, as a share of the content width, and how long one slow swing takes. */
	private static final float LIGHT_DRIFT = 0.03f;
	private static final double LIGHT_DRIFT_SECONDS = 24.0;

	/**
	 * Draws soft colored light in the menu's colors over the background of the content area, under the cards and rows, as in the mockups: the
	 * accent from the top left and the second glow color from the bottom right, as a very soft wash with no visible edge. The sidebar, the search
	 * field and the window buttons keep their neutral background. Two scaled textures per frame, plus a small shape in the rounded corner.
	 */
	private void drawColoredLight(Ui ui, float fade) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || !menu.backgroundGlow()) {
			return;
		}
		int left = windowX + side() + 1;
		int top = single != null ? windowY + TITLE_BAR : windowY + (bar() > 0 ? 0 : 1);
		// up to the window's edge, which is drawn over it: the same box and radius as the window, so their corners are the same curve
		int right = windowX + windowWidth;
		int bottom = windowY + windowHeight;
		float width = right - left;
		float height = bottom - top;
		float strength = fade * (ui.palette.glass() ? LIGHT_GLASS : menu.theme() == MacTheme.LIGHT ? LIGHT_LIGHT : LIGHT_DARK);
		// a very slow drift, which stops with animations off
		float drift = ui.animated ? (float) Math.sin(System.nanoTime() / 1e9 * 2.0 * Math.PI / LIGHT_DRIFT_SECONDS) * LIGHT_DRIFT * width : 0f;
		float radiusX = width * LIGHT_RADIUS_X;
		float radiusY = height * LIGHT_RADIUS_Y;
		float firstX = left - width * LIGHT_OUTSIDE_X + drift;
		float firstY = top - height * LIGHT_OUTSIDE_Y;
		float secondX = right + width * LIGHT_OUTSIDE_X - drift;
		float secondY = bottom + height * LIGHT_OUTSIDE_Y;
		int first = Ui.withOpacity(ui.accent | 0xFF000000, strength);
		int second = Ui.withOpacity(0xFF000000 | menu.secondGlowRgb(), strength * SECOND_LIGHT);
		// cut to the window's own curve, in one piece: a scissor is square, and one per row of the corners rounded apart in a window of any other size, each seam a thin line
		float[] rows = RoundedRows.edges(left, top, right, bottom, windowX, frameY, right, bottom, Radius.XXL);
		ui.washInRows(firstX, firstY, radiusX, radiusY, first, rows);
		ui.washInRows(secondX, secondY, radiusX, radiusY, second, rows);
	}

	// ---------------------------------------------------------------------------------------------------- page preview

	private static final int PREVIEW_WIDTH = 240;
	/** Between the page and the preview: the scroll bar of the page sits in its middle, with the same room on both sides. */
	private static final int PREVIEW_GAP = 24;
	private static final int PREVIEW_LABEL = 14;
	private static final String PREVIEW_TITLE = "PREVIEW";

	/** @return the live preview of the page chosen in the sidebar, also on its sub-pages, or null; the settings of a feature have none */
	private PagePreview pagePreview() {
		PagePreview preview = single == null && editor == null && !searching() ? listedPage().preview() : null;
		return preview != null && (subTitles.isEmpty() || subTitle == null || preview.shownOn(subTitle)) ? preview : null;
	}

	/** @return the width the preview takes from the page */
	private int previewRoom() {
		return pagePreview() != null ? PREVIEW_WIDTH + PREVIEW_GAP : 0;
	}

	/**
	 * Draws the preview panel at the right of the page, below the top of the settings, with its title above and a note right below it. The panel
	 * has the radius and outline of the cards; the preview keeps its content away from the rounded corners.
	 */
	private void drawPagePreview(Ui ui, PagePreview preview) {
		Palette palette = ui.palette;
		int x = contentRight - PREVIEW_WIDTH;
		int top = contentTop + PREVIEW_LABEL;
		int room = contentBottom - PREVIEW_LABEL - top;
		// a miniature of the game screen, its aspect ratio as wide as the column; or as tall as the column allows, with its note in two lines
		int height = preview.tall() ? Math.min(room - PREVIEW_TALL_NOTE, PREVIEW_TALL_HEIGHT) : Math.min(HudPreview.heightFor(PREVIEW_WIDTH), room);
		ui.sectionLabel(PREVIEW_TITLE, x + 4, contentTop + 1, palette.textSecondary());
		if (!preview.tall()) {
			WorldBackdrop.draw(ui, x, top, PREVIEW_WIDTH, height, Radius.LG);
		}
		preview.draw(ui, x, top, PREVIEW_WIDTH, height);
		ui.ring(x, top, PREVIEW_WIDTH, height, Radius.LG, 1, palette.cardBorder());
		// a sub-page has its own note, by its name
		String note = preview.note(subPage != null ? subPage.nameText() : subTitle);
		if (preview.tall()) {
			if (!note.equals(previewNote)) {
				previewNote = note;
				previewNoteLines = ui.wrapScaled(note, PREVIEW_WIDTH - 8, TextScale.SMALL, 2);
			}
			for (int line = 0; line < previewNoteLines.size(); line++) {
				ui.textAtScaled(previewNoteLines.get(line), x + 4, top + height + 5 + line * PREVIEW_NOTE_LINE, TextScale.SMALL, palette.textSecondary());
			}
			return;
		}
		ui.textAtScaled(ui.fitScaled(note, PREVIEW_WIDTH - 8, TextScale.SMALL), x + 4, top + height + 5, TextScale.SMALL,
			palette.textSecondary());
	}

	private static final int PREVIEW_TALL_HEIGHT = 300;
	private static final int PREVIEW_TALL_NOTE = 26;
	private static final int PREVIEW_NOTE_LINE = 10;
	/** The note of a tall preview in its lines, wrapped again only when it changes. */
	private String previewNote;
	private List<String> previewNoteLines = List.of();

	// buttons in a page's header (Feature#headerButtons), drawn right to left from the right edge
	private static final int HEADER_BUTTON_TOP = 17;
	private static final int HEADER_BUTTON_HEIGHT = 22;
	private static final int HEADER_BUTTON_PAD = 8;
	/** Middle of the page title's letters, which header buttons and chips are centered on, and the gap between the title and its chip. */
	private static final int TITLE_MIDDLE = 27;
	private static final int TITLE_CHIP_GAP = 6;
	/** Half the height of the chip after the title, for its tooltip. */
	private static final int CHIP_HOVER_HALF = 7;
	private static final int HEADER_BUTTON_GAP = 6;
	private static final int MAX_HEADER_BUTTONS = 4;
	/** Where the header buttons were drawn, for clicks: their left edges and widths, and how many there are. */
	private final int[] headerButtonX = new int[MAX_HEADER_BUTTONS];
	private final int[] headerButtonWidth = new int[MAX_HEADER_BUTTONS];
	private final float[] headerButtonHover = new float[MAX_HEADER_BUTTONS];
	private int headerButtonCount;

	/** Draws the page's header buttons, ending at the right edge; @return the room left for the title */
	private int drawHeaderButtons(Ui ui, Feature page, int right, int mouseX, int mouseY) {
		headerButtonCount = 0;
		List<Feature.HeaderButton> buttons = searching() ? List.of() : page.headerButtons();
		Palette palette = ui.palette;
		int y = windowY + HEADER_BUTTON_TOP;
		for (int i = Math.min(buttons.size(), MAX_HEADER_BUTTONS) - 1; i >= 0; i--) {
			Feature.HeaderButton button = buttons.get(i);
			float textWidth = ui.widthScaled(button.text(), TextScale.BODY);
			int width = Math.round(textWidth) + 2 * HEADER_BUTTON_PAD;
			int x = right - width;
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEADER_BUTTON_HEIGHT;
			float hover = headerButtonHover[i] = ui.approach(headerButtonHover[i], over ? 1f : 0f, Ui.HOVER_RATE);
			if (button.primary()) {
				ui.rounded(x, y, width, HEADER_BUTTON_HEIGHT, Radius.SM, Ui.mix(ui.accent, 0xFFFFFFFF, 0.14f * hover));
				ui.textCentered(button.text(), x + width / 2, y + HEADER_BUTTON_HEIGHT / 2, 0xFFFFFFFF);
			} else {
				// as in the mockup: transparent with a thin outline, a light fill on hover
				ui.rounded(x, y, width, HEADER_BUTTON_HEIGHT, Radius.SM, Ui.withOpacity(palette.hover(), hover));
				ui.ring(x, y, width, HEADER_BUTTON_HEIGHT, Radius.SM, 1, Ui.mix(Ui.withOpacity(palette.text(), 0.16f), ui.accent, 0.6f * hover));
				ui.textAtScaled(button.text(), x + (width - textWidth) / 2f, y + HEADER_BUTTON_HEIGHT / 2f - 4f, TextScale.BODY, palette.text());
			}
			if (button.dot().getAsBoolean()) {
				// a quiet mark that something waits behind the button (what is new after an update): a small dot at its top right corner
				ui.circle(x + width - 3, y + 3, NEW_DOT, SystemColors.GREEN);
			}
			headerButtonX[i] = x;
			headerButtonWidth[i] = width;
			headerButtonCount = Math.max(headerButtonCount, i + 1);
			right = x - HEADER_BUTTON_GAP;
		}
		return right;
	}

	/** @return the header button under the mouse, or -1 */
	private int headerButtonAt(double mouseX, double mouseY) {
		int y = windowY + HEADER_BUTTON_TOP;
		if (single != null || mouseY < y || mouseY >= y + HEADER_BUTTON_HEIGHT) {
			return -1;
		}
		for (int i = 0; i < headerButtonCount; i++) {
			if (mouseX >= headerButtonX[i] && mouseX < headerButtonX[i] + headerButtonWidth[i]) {
				return i;
			}
		}
		return -1;
	}

	private static final int HERE_DOT = 3;
	/** The dot on Home and its What is new button while something new waits there. */
	private static final int NEW_DOT = 3;

	/** Draws the chip saying where the player is, ending at the right edge; @return its left edge */
	private int drawHereChip(Ui ui, int right) {
		int height = 16;
		int width = ui.width(hereText) + 2 * HERE_DOT + 16;
		int x = right - width;
		int y = windowY + 20;
		ui.rounded(x, y, width, height, Radius.box(height), Ui.withOpacity(SystemColors.GREEN, 0.16f));
		ui.ring(x, y, width, height, Radius.box(height), 1, Ui.withOpacity(SystemColors.GREEN, 0.35f));
		ui.circle(x + 6 + HERE_DOT, y + height / 2, HERE_DOT, SystemColors.GREEN);
		ui.textMiddle(hereText, x + 10 + 2 * HERE_DOT, y + height / 2, Ui.mix(ui.palette.text(), SystemColors.GREEN, 0.35f));
		return x - 8;
	}

	/** The one width of the page's choices, dropdowns, key fields and sliders this layout (see {@link ControlWidths}). */
	private int pageControlWidth = ControlWidths.STANDARD;

	/** @return the width a control wants beside its name: the page's one width for the controls that share it, else its own */
	private int naturalWidth(Ui ui, Control control, int available) {
		return control.standardNeed(ui) >= 0 ? Math.min(available, pageControlWidth) : control.inlineWidth(ui, available);
	}

	/** The gap between a row's name (with its description) and its control on the line under it. */
	private static final int CONTROL_BELOW_GAP = 6;

	/** @return whether a row's control keeps its width but does not fit beside the row's name, so it goes on a line of its own under it */
	private boolean controlBelow(Ui ui, Row row, int innerWidth, int indent) {
		if (!row.control().keepsWidth()) {
			return false;
		}
		int labelWidth = ui.width(row.setting().labelText()) + iconRoom(row.setting()) + indent;
		int natural = naturalWidth(ui, row.control(), innerWidth - indent);
		return natural > Math.max(60, innerWidth - labelWidth - 16) && natural <= innerWidth - indent;
	}

	private void drawGroup(Ui ui, Placed p, int mouseX, int mouseY) {
		if (p.cardY + p.cardHeight < contentTop - 30 || p.top > contentBottom + 30) {
			// off screen, from its title or leading cards down; the controls still keep their position for keyboard navigation
			return;
		}
		// each part with its own share of light: 1, except while the page's content changes
		float alphaBefore = Ui.alpha();
		if (p.cardLight > 0f) {
			Ui.setAlpha(alphaBefore * p.cardLight);
			drawGroupCard(ui, p);
		}
		drawGroupRows(ui, p, mouseX, mouseY, alphaBefore);
		Ui.setAlpha(alphaBefore);
	}

	private void drawGroupCard(Ui ui, Placed p) {
		Palette palette = ui.palette;
		if (p.titled) {
			ui.sectionLabel(p.group.upperTitle, p.cardX + 4, p.titleY + 1, palette.textSecondary());
			if (pageGroup(p.group)) {
				// a named group of a page's features: a thin line from its name to the page's edge
				float lineX = p.cardX + 4 + ui.sectionLabelWidth(p.group.upperTitle) + PAGE_GROUP_LINE_GAP;
				float lineEnd = p.cardX + p.cardWidth - 2;
				if (lineEnd > lineX) {
					ui.fill(Math.round(lineX), p.titleY + PAGE_GROUP_LINE_DROP, Math.round(lineEnd - lineX), 1, Ui.withOpacity(palette.text(), PAGE_GROUP_LINE_OPACITY));
				}
			}
		}
		// a group of bare cards (a page's features) gets no card of its own
		if (!p.group.bare()) {
			ui.rounded(p.cardX, p.cardY, p.cardWidth, p.cardHeight, Radius.LG, palette.card());
			ui.ring(p.cardX, p.cardY, p.cardWidth, p.cardHeight, Radius.LG, 1, palette.cardBorder());
		}
	}

	private void drawGroupRows(Ui ui, Placed p, int mouseX, int mouseY, float alpha) {
		Palette palette = ui.palette;
		int rowPad = compactPage() ? COMPACT_ROW_PAD : ROW_PAD;
		for (int i = 0; i < p.group.rows().size(); i++) {
			Row row = p.group.rows().get(i);
			float share = i < p.rows ? p.rowLights[i] : 1f;
			if (share <= 0f) {
				continue;
			}
			Ui.setAlpha(alpha * share);
			int top = p.rowTops[i];
			int rowHeight = p.rowHeights[i];
			if (row.control().bare()) {
				row.control().renderSafely(ui, mouseX, mouseY);
				continue;
			}
			// the name starts after the icon; a sub-option has no icon of its own and is indented under the setting it belongs to
			int textX = p.cardX + CARD_PAD + iconRoom(row.setting()) + row.depth * SUB_INDENT;
			if (row.depth > 0) {
				// rows that belong to the setting above them carry a line in the accent at their left
				ui.fill(textX - SUB_INDENT + SUB_LINE_INSET, top + SUB_LINE_INSET, SUB_LINE_WIDTH, rowHeight - 2 * SUB_LINE_INSET, Ui.withOpacity(ui.accent(), SUB_LINE_OPACITY));
			}
			if (top > p.cardY) {
				// subtle separator between settings, starting under the name rather than the icon, like phone lists
				ui.fill(textX - 2, top, p.cardX + p.cardWidth - CARD_PAD - textX + 2, 1, Ui.withOpacity(palette.text(), palette == MacTheme.COLORED.palette() ? MacTheme.COLORED.lineOpacity(GLASS_DIVIDER_OPACITY) : palette.glass() ? GLASS_DIVIDER_OPACITY : DIVIDER_OPACITY));
			}
			// hover highlight on the row; large rows (lists, color picker, preview) skip it since their parts highlight themselves and a full-box
			// highlight underneath only got in the way
			boolean large = row.control().isBlock() && rowHeight > 60;
			boolean overRow = !large && mouseX >= p.cardX && mouseX < p.cardX + p.cardWidth && mouseY >= top && mouseY < top + rowHeight && !row.setting().isDisabled();
			float rowLight = ui.approach(row.hover, overRow ? 1f : 0f, Ui.HOVER_RATE);
			row.hover = rowLight;
			if (rowLight > 0f) {
				// spans the card width and follows the card's rounded corners on the first and last row
				int light = Ui.withOpacity(palette.hover(), rowLight * 0.7f);
				int rowX = p.cardX + 1;
				int rowW = p.cardWidth - 2;
				boolean first = top == p.cardY;
				boolean last = i == p.group.rows().size() - 1;
				if (first && last) {
					ui.rounded(rowX, top + 1, rowW, rowHeight - 2, Radius.LG - 1, light);
				} else if (first || last) {
					ui.graphics.enableScissor(rowX, top, rowX + rowW, top + rowHeight);
					ui.rounded(rowX, first ? top + 1 : top - 24, rowW, rowHeight + 23, Radius.LG - 1, light);
					ui.graphics.disableScissor();
				} else {
					ui.fill(rowX, top, rowW, rowHeight, light);
				}
			}
			if (row.setting() == highlightSetting) {
				// the setting navigated to glows briefly, pulsing while it does
				float remaining = (highlightUntil - System.nanoTime()) / 1_000_000_000f;
				if (remaining <= 0f) {
					highlightSetting = null;
				} else {
					float fade = Mth.clamp(remaining / 0.6f, 0f, 1f) * (0.65f + 0.35f * (float) Math.sin(System.nanoTime() / 1.2e8));
					ui.rounded(p.cardX + 2, top + 1, p.cardWidth - 4, rowHeight - 2, Radius.SM, Ui.withOpacity(ui.accent, 0.20f * fade));
					ui.ring(p.cardX + 2, top + 1, p.cardWidth - 4, rowHeight - 2, Radius.SM, 1, Ui.withOpacity(ui.accent, 0.95f * fade));
				}
			}
			String label = row.setting().labelText();
			int lines = i < p.rows ? p.rowDescriptionLines[i] : 0;
			boolean below = i < p.rows && p.rowDescriptionBelow[i];
			boolean ownLine = i < p.rows && p.rowControlBelow[i];
			// with a description, the name sits at the top of the row and the description below; without, it is vertically centered; a
			// description under the whole row leaves the name centered on the control
			int textHeight = 12 + lines * DESCRIPTION_LINE;
			int labelMiddle = row.control().isBlock() || ownLine ? top + rowPad + 6
				: below ? row.control().top() + row.control().height() / 2
				: lines > 0 ? top + (rowHeight - textHeight) / 2 + 6 : top + rowHeight / 2;
			int descriptionTop = below ? top + rowPad + Math.max(row.control().height(), 20) + 2 - 7 : labelMiddle;
			int labelRoom = row.control().isBlock() || ownLine ? p.cardX + p.cardWidth - CARD_PAD - textX : row.control().left() - textX - 12;
			int labelColor = row.depth == 0 ? palette.text() : Ui.mix(palette.text(), palette.textSecondary(), 0.35f);
			if (row.setting() instanceof FeatureRowSetting featureRow) {
				ui.icon(featureRow.feature().iconName(), p.cardX + CARD_PAD, labelMiddle - 7, 14, 0xFF000000 | featureRow.feature().iconRgb());
			} else if (row.setting() instanceof SearchHitSetting hit && hit.icon() != null) {
				ui.iconTile(hit.icon(), p.cardX + CARD_PAD - 1, labelMiddle - 8, 16, 11, Radius.XS, 0xFF000000 | hit.iconRgb(), 0xFFFFFFFF);
			}
			// a row with a longer explanation has a small mark after its name; the explanation shows while the pointer rests on either
			String tip = row.setting().tooltip();
			int tipRoom = tip != null ? TIP_MARK + TIP_GAP : 0;
			String shownLabel = row.control().isBlock() && !row.control().hasLabel() ? label : ui.fit(label, Math.max(40, labelRoom - tipRoom));
			if (!row.control().isBlock() || row.control().hasLabel()) {
				ui.textMiddle(shownLabel, textX, labelMiddle, labelColor);
				if (tip != null) {
					int markX = textX + ui.width(shownLabel) + TIP_GAP;
					ui.inlineIcon("info", markX, labelMiddle, Ui.TEXT_HEIGHT, Ui.withOpacity(palette.textSecondary(), 0.8f));
					if (mouseX >= textX && mouseX < markX + TIP_MARK && Math.abs(mouseY - labelMiddle) <= 6) {
						tooltip = tip;
					}
				}
			}
			// a name too long for its room is shown in full on hover
			if (shownLabel.length() != label.length() && mouseX >= textX && mouseX < textX + Math.max(40, labelRoom) && Math.abs(mouseY - labelMiddle) <= 6) {
				tooltip = label;
			}
			if (lines > 0) {
				DescriptionWrap wrapped = descriptionLines.get(row.setting());
				List<String> explanation = wrapped == null ? null : wrapped.lines();
				if (explanation != null) {
					for (int k = 0; k < explanation.size(); k++) {
						ui.textAtScaled(explanation.get(k), textX, descriptionTop + 7 + k * DESCRIPTION_LINE, DESCRIPTION_SCALE,
							Ui.withOpacity(palette.textSecondary(), 0.9f));
					}
				}
			}
			boolean disabled = row.setting().isDisabled();
			// a row that can do nothing here and says why shows only that: its control steps aside for the note, the two in one cross-fade
			float note = row.setting().disabledNote() == null ? 0f : noteLight(ui, row.setting(), disabled);
			if (note < 1f) {
				Ui.setAlpha(alpha * share * (1f - note));
				row.control().renderSafely(ui, note > 0f ? -1 : mouseX, note > 0f ? -1 : mouseY);
			}
			if (disabled || note > 0f) {
				Ui.setAlpha(alpha * share * (note > 0f ? note : 1f));
				drawDisabled(ui, p, row, disabled ? mouseX : -1, disabled ? mouseY : -1);
			}
		}
	}

	/** How far each setting with a note of its own shows the note in place of its control, from 0 to 1; of the page shown. */
	private final Reference2FloatOpenHashMap<Setting<?>> noteLights = new Reference2FloatOpenHashMap<>();

	/** @return how much of its light a row's note has, the rest being its control's: it changes over in the time of a change of the content */
	private float noteLight(Ui ui, Setting<?> setting, boolean disabled) {
		float target = disabled ? 1f : 0f;
		float light = ui.animated && noteLights.containsKey(setting) ? ui.approach(noteLights.getFloat(setting), target, 1f / TAB_SWITCH_SECONDS) : target;
		noteLights.put(setting, light);
		return eased(light);
	}

	/** What a smaller note needs beside its text: the lock and a margin. */
	private static final int DISABLED_NOTE_ROOM = 28;
	/** The lock before a disabled row's own note, and how strong the note is. */
	private static final int DISABLED_LOCK = 10;
	private static final float DISABLED_NOTE_OPACITY = 0.85f;

	private void drawDisabled(Ui ui, Placed p, Row row, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		Control control = row.control();
		// cover the control and a small margin with the shade of the surface underneath
		int left = control.left() - 5;
		// it stays clear of a description under the row
		int spread = row.setting().description() != null ? 1 : 4;
		int top = control.top() - spread;
		int right = control.left() + (control.isBlock() ? p.cardWidth - 2 * CARD_PAD : control.width()) + 5;
		int height = control.height() + 2 * spread;
		boolean light = palette.light();
		String own = row.setting().disabledNote();
		int middle = top + height / 2;
		if (own != null) {
			// the control is not drawn: the lock and the note stand quietly at its right end, where its value was
			int quiet = Ui.withOpacity(palette.textSecondary(), DISABLED_NOTE_OPACITY);
			int noteRight = right - 5;
			int noteWidth = Math.round(ui.widthScaled(own, TextScale.SMALL));
			int noteX = Math.max(left + 5 + DISABLED_LOCK + 6, noteRight - noteWidth);
			String fitted = ui.fitScaled(own, noteRight - noteX, TextScale.SMALL);
			ui.icon("lock", noteX - DISABLED_LOCK - 6, middle - DISABLED_LOCK / 2, DISABLED_LOCK, quiet);
			ui.textAtScaled(fitted, noteX, middle - TextScale.SMALL * 4f, TextScale.SMALL, quiet);
			if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + height) {
				tooltip = own;
			}
			return;
		}
		// the menu's color: the control stays visible under a light veil
		ui.rounded(left, top, right - left, height, Radius.MD, light ? 0xB8F4F4F6 : 0x70101014);
		ui.ring(left, top, right - left, height, Radius.MD, 1, Ui.withOpacity(palette.textSecondary(), 0.18f));
		String note = own != null ? own : "Menu color";
		int noteWidth = ui.width(note);
		int smallWidth = Math.round(ui.widthScaled(note, TextScale.SMALL));
		boolean room = right - left >= noteWidth + 44;
		boolean smallRoom = !room && right - left >= smallWidth + DISABLED_NOTE_ROOM;
		int shown = room ? noteWidth + 20 : smallRoom ? smallWidth + 20 : 14;
		int startX = (left + right) / 2 - shown / 2;
		int iconColor = Ui.withOpacity(palette.textSecondary(), 0.95f);
		ui.icon("lock", startX, middle - 7, 14, iconColor);
		if (room) {
			ui.textMiddle(note, startX + 20, middle, iconColor);
		} else if (smallRoom) {
			ui.textAtScaled(note, startX + 20, middle - TextScale.SMALL * 4f, TextScale.SMALL, iconColor);
		}
		if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + height) {
			tooltip = own != null ? own : "Disabled because this feature uses the color of the menu.";
		}
	}
	/** The mark after a name that has a longer explanation on hover: its size and the room before it. */
	private static final int TIP_MARK = 9;
	private static final int TIP_GAP = 4;
	/** The widest a tooltip gets before its text wraps, and its line height. */
	private static final int TOOLTIP_WIDTH = 300;
	private static final int TOOLTIP_LINE = 11;
	private static final int TOOLTIP_LINES = 6;

	private void drawTooltip(Ui ui, int mouseX, int mouseY) {
		if (tooltip == null) {
			return;
		}
		// a long text wraps instead of running out of the window
		List<String> lines = ui.wrap(tooltip, TOOLTIP_WIDTH - 20, TOOLTIP_LINES);
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, ui.width(line));
		}
		int boxWidth = textWidth + 20;
		int boxHeight = 13 + lines.size() * TOOLTIP_LINE;
		int x = Mth.clamp(mouseX + 14, 4, (int) inWindowX(width) - boxWidth - 4);
		int y = Mth.clamp(mouseY + 16, 4, (int) inWindowY(height) - boxHeight - 6);
		ui.shadow(x, y, boxWidth, boxHeight, Radius.SM, 8, 0.4f);
		ui.rounded(x, y, boxWidth, boxHeight, Radius.SM, 0xF2161618);
		ui.ring(x, y, boxWidth, boxHeight, Radius.SM, 1, 0x40FFFFFF);
		// the tooltip is dark in every theme, so it uses the dark theme's text color
		for (int i = 0; i < lines.size(); i++) {
			ui.textMiddle(lines.get(i), x + 10, y + 12 + i * TOOLTIP_LINE, MacTheme.DARK.palette().text());
		}
	}

	/** Room in front of a row's name: a feature in a list and a search result show an icon there; settings have none. */
	/** @return the room the mark of a longer explanation takes after a name, or 0 */
	/** @return the size of a page title in a room: the title size, or a step smaller where it would not fit whole */
	private static float titleScale(Ui ui, String title, int room) {
		return ui.width(title) * TextScale.TITLE * BOLD_WIDER <= room ? TextScale.TITLE : TextScale.HEADING;
	}

	private static int tipRoom(Setting<?> setting) {
		return setting.tooltip() != null ? TIP_MARK + TIP_GAP : 0;
	}

	private static int iconRoom(Setting<?> setting) {
		return setting instanceof FeatureRowSetting || setting instanceof SearchHitSetting hit && hit.icon() != null ? LABEL_ICON : 0;
	}

	/** The page's scroll bar and its room on each side, which centers it between the page and the preview. */
	private static final int SCROLL_BAR_WIDTH = 4;
	private static final int SCROLL_BAR_GAP = 10;

	private void drawScrollBar(Ui ui) {
		int view = contentBottom - contentTop;
		int max = maxScroll();
		if (max <= 0) {
			return;
		}
		int barHeight = Math.max(24, (view - 12) * view / (contentHeight + 10));
		int barY = contentTop + 6 + (int) ((view - 12 - barHeight) * (scroll / max));
		int x = contentRight - previewRoom() + SCROLL_BAR_GAP;
		ui.rounded(x, barY, SCROLL_BAR_WIDTH, barHeight, Radius.XXS, Ui.withOpacity(ui.palette.textSecondary(), 0.7f));
	}

	// ------------------------------------------------------------------------------------------------------- input

	private Control openOverlay() {
		if (tabDropdown.isOverlayOpen()) {
			return tabDropdown;
		}
		for (int c = 0; c < controlList.size(); c++) {
			Control control = controlList.get(c);
			if (control.isOverlayOpen()) {
				return control;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		RecentChanges.begin();
		try {
			return handleMouseClicked(event, doubleClick);
		} finally {
			RecentChanges.end();
		}
	}

	private boolean handleMouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) {
			return true;
		}
		// the mouse's back and forward buttons go through the menu's history, as in a browser
		int pressed = ScreenInput.button(event);
		if (owner == null && single == null && (pressed == ScreenInput.BACK || pressed == ScreenInput.FORWARD)) {
			travel(pressed == ScreenInput.FORWARD);
			return true;
		}
		// a popup of the page gets the click first, also where it lies over the pane: otherwise its lower part could not be clicked
		if (editor != null && !paneClosing && openOverlay() == null) {
			MouseButtonEvent inside = inWindow(event);
			if (editor.paneHits(inside.x(), inside.y()) || editor.openOverlay() != null) {
				if (focused != null) {
					focused.loseFocus();
					focused = null;
				}
				searchFocused = false;
				return editor.mouseClicked(inside, doubleClick);
			}
		}
		if (editor != null && editor.focused != null) {
			// a click outside the settings window ends what is being edited there, such as a key waiting for its new key
			editor.focused.loseFocus();
			editor.focused = null;
		}
		double rawX = event.x();
		double rawY = event.y();
		double mouseX = inWindowX(rawX);
		double mouseY = inWindowY(rawY);
		int button = ScreenInput.button(event);
		// a key waiting for its new key takes any mouse button but the left one, wherever the mouse is
		if (focused instanceof KeyControl key && key.listening() && button != 0) {
			key.mouseClicked(mouseX, mouseY, button);
			focused = null;
			return true;
		}

		Control overlay = openOverlay();
		if (overlay != null) {
			overlay.mouseClicked(mouseX, mouseY, button);
			// a popup that stays open (a color picker) is dragged in and typed into like the page's controls
			if (overlay.isOverlayOpen()) {
				dragged = overlay;
				focused = overlay.wantsKeyboard() ? overlay : null;
			}
			return true;
		}
		List<Control> usable = new ArrayList<>();
		controls.forEach((setting, control) -> {
			if (!setting.isDisabled()) {
				usable.add(control);
			}
		});

		if (button == 0) {
			if (overHudItem(mouseX, mouseY)) {
				UiSounds.click();
				minecraft.setScreenAndShow(new HudEditScreen());
				return true;
			}
			if (single == null && overSearch(mouseX, mouseY)) {
				if (focused != null) {
					focused.loseFocus();
					focused = null;
				}
				searchFocused = true;
				// the clear button at the end of the field empties it
				if (!query.isEmpty() && mouseX >= searchX() + searchWidth() - 26) {
					clearQuery();
				}
				return true;
			}
			searchFocused = false;
			if (owner == null && overPaneClose(mouseX, mouseY)) {
				onClose();
				return true;
			}
			if (overBackChip(mouseX, mouseY)) {
				stepBack();
				return true;
			}
			int tab = tabAt(mouseX, mouseY);
			if (tab >= 0) {
				if (tab != subIndex) {
					openTab(tab);
				}
				return true;
			}
			if (tabsCollapsed && !searching() && !subTitles.isEmpty() && tabDropdown.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
			if (overHeaderSwitch(mouseX, mouseY)) {
				boolean now = page().toggle();
				UiSounds.toggle(now);
				onSettingChanged();
				return true;
			}
			int headerButton = headerButtonAt(mouseX, mouseY);
			if (headerButton >= 0 && headerButton < page().headerButtons().size()) {
				UiSounds.click();
				page().headerButtons().get(headerButton).action().run();
				return true;
			}
			if (overPageSwitch(mouseX, mouseY) && ownFeature() != null) {
				boolean now = ownFeature().toggle();
				UiSounds.toggle(now);
				onSettingChanged();
				return true;
			}
			if (overPageSwitch(mouseX, mouseY) && page() instanceof FeatureListFeature list && list.headerSwitch() != null) {
				boolean now = list.headerSwitch().toggle();
				UiSounds.toggle(now);
				onSettingChanged();
				return true;
			}
			if (hasFooter() && footerReset().contains(mouseX, mouseY) && footerReset().mouseClicked(mouseX, mouseY, button)) {
				dragged = footerReset();
				return true;
			}
			if (owner == null && overMenuClose(mouseX, mouseY)) {
				onClose();
				return true;
			}
			int item = sidebarItemAt(mouseX, mouseY);
			if (item >= 0) {
				selectFromSidebar(item);
				return true;
			}
			// in the pane, its title bar where no control took the click pulls it off the window or moves it; a double click docks it again
			if (owner != null && mouseY >= windowY && mouseY < windowY + TITLE_BAR && owner.pressPaneTitle(mouseX, mouseY, doubleClick)) {
				return true;
			}
			// where no control took the click, the top bar and the empty header move the window; a double click centers it
			if (inDragZone(mouseX, mouseY)) {
				if (doubleClick) {
					centerOnScreen();
					return true;
				}
				dragPending = true;
				dragStartX = rawX;
				dragStartY = rawY;
				dragFromX = centerX;
				dragFromY = centerY;
				grabX = (float) rawX - shownCenterX;
				grabY = (float) rawY - shownCenterY;
				return true;
			}
		}

		if (mouseX >= windowX + side() && mouseY >= contentTop - 2 && mouseY < contentBottom) {
			Control hit = null;
			for (Control control : usable) {
				if (control.contains(mouseX, mouseY) && control.mouseClicked(mouseX, mouseY, button)) {
					hit = control;
					break;
				}
			}
			if (focused != null && focused != hit) {
				focused.loseFocus();
				focused = null;
			}
			if (hit != null) {
				dragged = hit;
				// a control that stopped wanting the keyboard with this click (a key that was waiting) gives the focus up
				focused = hit.wantsKeyboard() ? hit : null;
				return true;
			}
			// controls larger than their reactive area (such as an open list) still receive the click
			for (Control control : usable) {
				if (control.mouseClicked(mouseX, mouseY, button)) {
					dragged = control;
					return true;
				}
			}
		}
		// a click beside a sheet or the pane, where nothing took it, goes back one step
		if (button == ScreenInput.LEFT) {
			clickBesideGoesBack(mouseX, mouseY);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		RecentChanges.begin();
		try {
			return handleMouseDragged(event, dragX, dragY);
		} finally {
			RecentChanges.end();
		}
	}

	private boolean handleMouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (paneDragPending || paneDragging) {
			dragPane(event);
			return true;
		}
		if (editor != null && !paneClosing && editor.mouseDragged(inWindow(event), dragX / zoom, dragY / zoom)) {
			return true;
		}
		if (dragPending && Math.hypot(event.x() - dragStartX, event.y() - dragStartY) > DRAG_THRESHOLD) {
			dragPending = false;
			windowDrag = WindowDrag.MOVE;
		}
		if (windowDrag == WindowDrag.MOVE) {
			dragTo(event.x(), event.y(), (ScreenInput.modifiers(event.modifiers()) & ScreenInput.SHIFT) != 0);
			return true;
		}
		if (dragPending) {
			return true;
		}
		if (dragged != null) {
			dragged.mouseDragged(inWindowX(event.x()), inWindowY(event.y()));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		RecentChanges.begin();
		try {
			return handleMouseReleased(event);
		} finally {
			RecentChanges.end();
		}
	}

	private boolean handleMouseReleased(MouseButtonEvent event) {
		if (editor != null) {
			editor.mouseReleased(inWindow(event));
		}
		if (paneDragPending || paneDragging) {
			dropPane();
			return true;
		}
		dragPending = false;
		if (windowDrag != WindowDrag.NONE) {
			dropWindow();
			return true;
		}
		if (dragged != null) {
			dragged.mouseReleased(inWindowX(event.x()), inWindowY(event.y()));
			dragged = null;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// a key combination being recorded takes the wheel as its trigger
		if (scrollY != 0 && dev.tim1337.lumio.ui.controls.ComboRecorder.turnWheel(scrollY > 0)) {
			return true;
		}
		// Ctrl + wheel over the window changes its size around the mouse
		if (owner == null && single == null && minecraft.hasControlDown() && mouseX >= windowX && mouseX < windowX + windowWidth * zoom && mouseY >= frameY
			&& mouseY < frameY + fullHeightShown() * zoom) {
			resizeBy(scrollY > 0 ? 1 : -1, mouseX, mouseY);
			return true;
		}
		if (single == null && owner == null && sidebarMostScroll > 0f && inWindowX(mouseX) >= windowX && inWindowX(mouseX) < windowX + SIDEBAR && inSidebarList(inWindowY(mouseY))) {
			sidebarScroll = Mth.clamp(sidebarScroll - (float) scrollY * ITEM_HEIGHT, 0f, sidebarMostScroll);
			return true;
		}
		if (editor != null && !paneClosing && editor.paneHits(inWindowX(mouseX), inWindowY(mouseY))) {
			return editor.mouseScrolled(inWindowX(mouseX), inWindowY(mouseY), scrollX, scrollY);
		}
		Control overlay = openOverlay();
		if (overlay != null) {
			// an open list taller than the screen scrolls; the page underneath stays put
			overlay.overlayScrolled(scrollY);
			return true;
		}
		scrollTarget = Mth.clamp(scrollTarget - scrollY * 48, 0, maxScroll());
		return true;
	}

	/** @return whether a text field has keyboard focus */
	private boolean typing() {
		return focused != null || searchFocused;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		RecentChanges.begin();
		try {
			return handleKeyPressed(event);
		} finally {
			RecentChanges.end();
		}
	}

	private boolean handleKeyPressed(KeyEvent event) {
		if (closing) {
			return true;
		}
		// Escape during a drag ends the drag only: the window goes back, and the menu stays open
		if (event.key() == InputConstants.KEY_ESCAPE && dragActive()) {
			cancelDrags();
			return true;
		}
		if (owner == null && single == null && !typing() && (historyKey(event, false) || historyKey(event, true))) {
			travel(historyKey(event, true));
			return true;
		}
		// Escape goes back one step: the pane's sheet, the pane, the page's sub-page or sheet, then it closes the menu
		if (event.key() == InputConstants.KEY_ESCAPE && subPage != null && editor == null && !typing() && openOverlay() == null && !searching()) {
			closeSubPage();
			return true;
		}
		if (editor != null && !paneClosing && !typing() && openOverlay() == null) {
			if (event.key() == InputConstants.KEY_ESCAPE && !editor.typing() && editor.openOverlay() == null && editor.subPage != null) {
				editor.closeSubPage();
				return true;
			}
			if (event.key() == InputConstants.KEY_ESCAPE && !editor.typing() && editor.openOverlay() == null) {
				// the pane closes; opened right on it from another screen, the menu closes back to that screen, as it came
				if (openedOn != null && editor.single == openedOn) {
					onClose();
				} else {
					closeEditor();
					UiSounds.play(UiSounds.Cue.CLOSE);
				}
				return true;
			}
			return editor.keyPressed(event);
		}
		Control overlay = openOverlay();
		if (overlay != null && event.key() == InputConstants.KEY_ESCAPE) {
			// Escape closes an open popup first, then the window
			overlay.loseFocus();
			if (focused == overlay) {
				focused = null;
			}
			return true;
		}
		// asked as Ctrl + wheel asks it, so that the keys and the wheel always agree
		boolean control = minecraft.hasControlDown();
		// Ctrl + minus / plus / 0 change the window size around its center, as in other programs
		if (single == null && control && !typing()) {
			int key = event.key();
			if (key == InputConstants.KEY_MINUS || key == ScreenInput.numpadMinus()) {
				resizeBy(-1, shownCenterX, shownCenterY);
				return true;
			}
			if (key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD) {
				resizeBy(1, shownCenterX, shownCenterY);
				return true;
			}
			if ((key == InputConstants.KEY_0 || key == InputConstants.KEY_NUMPAD0) && menu != null) {
				setWindowSize(1f, shownCenterX, shownCenterY);
				return true;
			}
		}
		if (single == null && control && ScreenInput.letter(event.key(), 'F') && (focused == null || !focused.takesText())) {
			// Ctrl+F focuses the search field, as in other programs
			if (focused != null) {
				focused.loseFocus();
				focused = null;
			}
			searchFocused = true;
			return true;
		}
		if (searchFocused) {
			int key = event.key();
			// Ctrl+A plus copy, cut and paste on the selection
			if (querySelection.key(key, ScreenInput.modifiers(event.modifiers()), () -> query, value -> {
				query = value;
				queryChanged();
			}, 40)) {
				return true;
			}
			if (key == InputConstants.KEY_BACKSPACE) {
				if (!query.isEmpty()) {
					query = query.substring(0, query.length() - 1);
					queryChanged();
				}
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				// the first Escape clears the field, the next one leaves it
				if (!query.isEmpty()) {
					clearQuery();
				} else {
					searchFocused = false;
				}
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				searchFocused = false;
				return true;
			}
		}
		if (focused != null && focused.keyPressed(event.key(), ScreenInput.modifiers(event.modifiers()))) {
			// a key that got its new key is done; Escape afterwards closes the window again
			if (!focused.wantsKeyboard()) {
				focused = null;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		RecentChanges.begin();
		try {
			return handleCharTyped(event);
		} finally {
			RecentChanges.end();
		}
	}

	private boolean handleCharTyped(CharacterEvent event) {
		if (editor != null && !paneClosing && !typing()) {
			return editor.charTyped(event);
		}
		if (searchFocused && event.codepoint() >= 32) {
			String next = querySelection.typed(query, new String(Character.toChars(event.codepoint())));
			if (next.length() <= 40) {
				query = next;
				queryChanged();
			}
			return true;
		}
		if (focused != null && focused.charTyped(event.codepoint())) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// opened from the main menu or a list outside a world: the menu background is drawn behind it, as behind those screens
		if (ModFeatures.MENUS.backgroundActive()) {
			MenuBackground.draw(graphics, width, height);
			coloredBackdrop(graphics);
			return;
		}
		if (coloredBackdrop(graphics)) {
			return;
		}
		boolean clear = menu != null && menu.theme().onGlass() && !menu.frostedGlass();
		if (clear) {
			// no blur: the world stays sharp behind the glass
			graphics.fill(0, 0, width, height, 0x28000000);
			return;
		}
		if (glassBlurredBackground(graphics)) {
			return;
		}
		// the game's own blur: Auto times it as it times the glass, so a card that struggles with it gets the cheaper blur of a lower level
		GlassPanes.timeGameBlur();
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
	}

	/** The game's blur radius at which the glass blurs all it can; a smaller radius blurs less, in step. At the game's default of 5 the two look alike. */
	private static final float GAME_BLUR_AT_FULL = 7f;

	/**
	 * Below the High glass quality, in a world, the blur behind the menu is the glass's blur, at a small part of the cost of the game's own (on an
	 * integrated card about 0.5 ms instead of 6); the game's darkening lies over it as before. High keeps the game's own blur.
	 *
	 * @return whether it was drawn so
	 */
	private boolean glassBlurredBackground(GuiGraphicsExtractor graphics) {
		int radius = minecraft.options.getMenuBackgroundBlurriness();
		if (isInGameUi() || minecraft.level == null || radius < 1 || !GlassPanes.active() || GlassPanes.level() == GlassPanes.Level.HIGH) {
			return false;
		}
		GlassPanes.backdrop(new Ui(graphics, font, MacTheme.DARK.palette(), 0, 0f, false), width, height, Math.min(1f, radius / GAME_BLUR_AT_FULL));
		extractMenuBackground(graphics);
		minecraft.gui.hud.extractDeferredSubtitles();
		return true;
	}

	/** Reaches a little past the screen, so the backdrop's edge line is out of sight. */
	private static final int BACKDROP_MARGIN = 2;

	/** The Colored theme, when asked for: its colors fill the screen behind the window, over a little of what is behind them. @return whether they did */
	private boolean coloredBackdrop(GuiGraphicsExtractor graphics) {
		if (menu == null || menu.theme() != MacTheme.COLORED || !menu.coloredWholeScreen() || !GlassPanes.active()) {
			return false;
		}
		GlassPanes.pane(new Ui(graphics, font, MacTheme.COLORED.palette(), menu.accentArgb(), 0f, false), -BACKDROP_MARGIN, -BACKDROP_MARGIN, width + 2 * BACKDROP_MARGIN, height + 2 * BACKDROP_MARGIN, 0f, 1f,
			new GlassPanes.Style(ColoredPalette.LAYER_RGB, ColoredFlow.backdropLayer(), menu.glassBlur(), 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0xFFFFFF, 2f, 0, 0f,
				GlassPanes.FLAG_COLORED | GlassPanes.FLAG_FLAT, ColoredFlow.backdropShare()));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		Ui.setAlpha(1f);
		clearDrags();
		if (owner == null && single == null && selectedIndex >= 0 && selectedIndex < pages.size()) {
			pages.get(selectedIndex).pageLeft();
		}
		SCROLLS.put(key(), scrollTarget);
		rememberTab();
		Ui.setZoom(1f);
		FeatureManager.save();
		// keys live in the game's options, saved once when the menu closes, as the game's controls screen does
		KeySetting.saveIfChanged();
	}
}
