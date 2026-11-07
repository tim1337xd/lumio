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

import com.mojang.blaze3d.platform.cursor.CursorTypes;


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
	/** Gap between page groups in the sidebar. */
	private static final int GROUP_GAP = 6;
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
		/** How far the group is shown while it appears or disappears with a change of the page, from 0 to 1, and whether it is on its way out. */
		float shown = 1f;
		boolean leaving;

		Group(String title, List<Row> rows) {
			this.title = title;
			this.upperTitle = title.toUpperCase(Locale.ROOT);
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
		/** How much of the group shows while it grows in or shrinks away, from its top, or -1 for all of it. */
		int clipHeight = -1;
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
			}
			rowTops[rows] = top;
			rowHeights[rows] = height;
			rowDescriptionLines[rows] = descriptionLines;
			rowDescriptionBelow[rows] = descriptionBelow;
			rowControlBelow[rows] = controlBelow;
			rows++;
		}
	}

	/** Line height of the description under a setting's name. */
	private static final int DESCRIPTION_LINE = 11;
	/** Narrowest column for a description beside a control; with less room (a wide text field), it goes under the row at full width. */
	private static final int DESCRIPTION_MIN_ROOM = 150;
	/** Descriptions are drawn slightly smaller than names. */
	private static final float DESCRIPTION_SCALE = TextScale.SMALL;

	/** Wrapped description lines per setting for the available width, cached so layout and drawing agree. */
	private final Map<Setting<?>, List<String>> descriptionLines = new HashMap<>();

	private int descriptionLines(Ui ui, Row row, int room) {
		String description = row.setting().description();
		if (description == null || room < 60 || searching()) {
			descriptionLines.remove(row.setting());
			return 0;
		}
		// as many lines as needed so nothing is cut off; descriptions are kept short, so it is rarely more than two
		List<String> lines = ui.wrap(description, Math.round(room / DESCRIPTION_SCALE), 4);
		descriptionLines.put(row.setting(), lines);
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
	private int windowY;
	private int windowWidth;
	private int windowHeight;
	/** The window's height for a long page; a short one takes less, from the top down. */
	private int fullHeight;
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
	/** The sidebar highlight's position, which springs to the selected page. */
	private final Spring highlightSpring = Spring.bouncy(Spring.DEFAULT);
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
		} else if (found != null) {
			pages.add(found);
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
		// switching from one feature to another slides the new settings in like a page
		editor.restartPage();
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
		return paneDetached() ? Math.round(windowY + (paneShownY - windowY) / zoom - windowHeight / 2f) : windowY;
	}

	/** Gives the pane its place in the window, in the menu's window coordinates; the pane itself is not zoomed, the menu's zoom applies. */
	private void placePane() {
		editor.windowX = paneX();
		editor.windowY = paneY();
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

	private boolean overPaneClose(double mouseX, double mouseY) {
		return owner != null && !showsSheet() && mouseX >= paneCloseX() && mouseX < paneCloseX() + PANE_BUTTON && mouseY >= paneButtonY()
			&& mouseY < paneButtonY() + PANE_BUTTON;
	}

	/** @return whether the back arrow shows: when the pane covers the page */
	private boolean paneBack() {
		return owner != null && owner.paneCovers();
	}

	private boolean overPaneBack(double mouseX, double mouseY) {
		return paneBack() && !showsSheet() && mouseX >= windowX + PAD && mouseX < windowX + PAD + PANE_BUTTON && mouseY >= paneButtonY()
			&& mouseY < paneButtonY() + PANE_BUTTON;
	}

	private float paneCloseHover;
	private float paneBackHover;

	/** Draws the pane's close button and, when it covers the page, its back arrow, in place of the window buttons. */
	private void drawPaneButtons(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int y = paneButtonY();
		paneCloseHover = ui.approach(paneCloseHover, overPaneClose(mouseX, mouseY) ? 1f : 0f, Ui.HOVER_RATE);
		ui.rounded(paneCloseX(), y, PANE_BUTTON, PANE_BUTTON, Radius.pill(PANE_BUTTON), Ui.withOpacity(palette.hover(), paneCloseHover));
		ui.icon("close", paneCloseX() + 4, y + 4, PANE_BUTTON - 8, Ui.mix(palette.textSecondary(), palette.text(), paneCloseHover));
		if (paneBack()) {
			paneBackHover = ui.approach(paneBackHover, overPaneBack(mouseX, mouseY) ? 1f : 0f, Ui.HOVER_RATE);
			int x = windowX + PAD;
			ui.rounded(x, y, PANE_BUTTON, PANE_BUTTON, Radius.pill(PANE_BUTTON), Ui.withOpacity(palette.hover(), paneBackHover));
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(x + PANE_BUTTON / 2f, y + PANE_BUTTON / 2f);
			pose.rotate((float) Math.PI);
			ui.icon("chevron", -4, -4, 8, Ui.mix(palette.textSecondary(), palette.text(), paneBackHover));
			pose.popMatrix();
		}
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
		return windowY + windowHeight * zoom / 2f;
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
		double screenY = windowY + (y - windowY) * zoom;
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
			snap.find(paneTargetX - w / 2f, paneTargetY - h / 2f, w, h, width, height, true, windowX, windowY, windowWidth * zoom, windowHeight * zoom);
		}
	}

	/**
	 * @return whether the dragged pane docks when dropped where it is, on screen: the mouse or the pane's middle over its docked place (the right
	 *         part of the menu window), or the pane's side near the docked place or the window's side and level with the window for at least
	 *         half its height
	 */
	private boolean wouldDock(double mouseX, double mouseY, float left, float top, float w, float h) {
		float right = windowX + windowWidth * zoom;
		float bottom = windowY + windowHeight * zoom;
		float dockLeft = right - paneWidth() * zoom;
		if (overDockPlace(mouseX, mouseY, dockLeft, right, bottom) || overDockPlace(left + w / 2f, top + h / 2f, dockLeft, right, bottom)) {
			return true;
		}
		float level = Math.min(top + h, bottom) - Math.max(top, windowY);
		boolean nearRight = left >= dockLeft - DOCK_EDGE && left <= right + DOCK_EDGE;
		boolean nearLeft = Math.abs(left + w - windowX) <= DOCK_EDGE;
		return level >= h / 2f && (nearRight || nearLeft);
	}

	private boolean overDockPlace(double x, double y, float dockLeft, float right, float bottom) {
		return x >= dockLeft && x < right && y >= windowY && y < bottom;
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
		ui.rounded(labelX, labelY, labelW, DOCK_LABEL_HEIGHT, Radius.pill(DOCK_LABEL_HEIGHT), Ui.withOpacity(DOCK_LABEL_BACK, dockHint * fade));
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
	private static final int BACK_CHEVRON = 9;
	private int backRight = -1;
	/** The chevron between the page's name and the sub-page's title, and the gaps around it. */
	private static final String CRUMB = "›";
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
		underSheet = sub.isSheet() ? ownFeature() : null;
		subPage = sub;
		enterPage();
	}

	/** Goes back from a sub-page to its page, scrolled where it was. @return whether a sub-page was shown */
	private boolean closeSubPage() {
		if (subPage == null) {
			return false;
		}
		SCROLLS.put(key(), scrollTarget);
		subPage = underSheet;
		underSheet = null;
		enterPage();
		return true;
	}

	/** Shows the page or sub-page now chosen: at its scroll position, sliding in with a tick. */
	private void enterPage() {
		scroll = SCROLLS.getOrDefault(key(), 0.0);
		scrollTarget = scroll;
		restartPage();
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
		backRight = -1;
		ui.iconTile(own.iconName(), left, windowY + HEADER_TILE_TOP, HEADER_TILE, HEADER_ICON, Radius.MD, 0xFF000000 | own.iconRgb(), 0xFFFFFFFF);
		int textX = left + HEADER_TILE + 9;
		int textEnd = own.isSwitchable() ? drawPageSwitch(ui, own, right, windowY + HEADER_TILE_TOP + (HEADER_TILE - PAGE_SWITCH_HEIGHT) / 2, "On", mouseX, mouseY) - CRUMB_GAP : right;
		if (!own.isSwitchable()) {
			pageSwitchX = -1;
		}
		ui.textAtScaledTitle(ui.fit(own.nameText(), Math.max(20, Math.round((textEnd - textX) / (TextScale.TITLE * BOLD_WIDER)))), textX, top + BACK_BUTTON / 2 - TextScale.TITLE * TITLE_HALF, TextScale.TITLE, palette.text());
		ui.textAtScaled(ui.fitScaled(own.descriptionText(), textEnd - textX, TextScale.SMALL), textX, top + BACK_BUTTON + 2, TextScale.SMALL, palette.textSecondary());
	}

	/** Draws a sub-page's header: the back button, the page's name and the sub-page's title. */
	private void drawSubPageHeader(Ui ui, int left, int right, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int top = windowY + HEADER_TILE_TOP + (HEADER_TILE - BACK_BUTTON) / 2;
		String parent = listedPage().nameText();
		int parentX = left + BACK_BUTTON + 10;
		backRight = parentX + ui.width(parent);
		boolean over = mouseY >= top && mouseY < top + BACK_BUTTON && mouseX >= left && mouseX < backRight;
		float hover = ui.approach(backHover, over ? 1f : 0f, Ui.HOVER_RATE);
		backHover = hover;
		ui.rounded(left, top, BACK_BUTTON, BACK_BUTTON, Radius.SM, Ui.mix(palette.control(), palette.hover(), hover));
		ui.ring(left, top, BACK_BUTTON, BACK_BUTTON, Radius.SM, 1, palette.cardBorder());
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(left + BACK_BUTTON / 2f, top + BACK_BUTTON / 2f);
		pose.rotate((float) (Math.PI / 2.0));
		ui.icon("chevron", -BACK_CHEVRON / 2, -BACK_CHEVRON / 2, BACK_CHEVRON, palette.text());
		pose.popMatrix();
		int middle = top + BACK_BUTTON / 2;
		ui.textMiddle(parent, parentX, middle, Ui.mix(palette.textSecondary(), palette.text(), hover * 0.5f));
		int crumbX = backRight + CRUMB_GAP;
		ui.textMiddle(CRUMB, crumbX, middle, palette.textSecondary());
		int titleX = crumbX + ui.width(CRUMB) + CRUMB_GAP;
		String title = ui.fit(headerTitle, Math.max(20, Math.round((right - titleX) / (TextScale.TITLE * BOLD_WIDER))));
		ui.textAtScaledTitle(title, titleX, middle - TextScale.TITLE * TITLE_HALF, TextScale.TITLE, palette.text());
	}

	// a sheet lies over the page's column, with its title and Done at the top; the sidebar and the page's preview stay beside it
	private static final int SHEET_MARGIN = 8;
	private static final int SHEET_PAD = 10;
	/** Room right of the rows, for the scroll bar. */
	private static final int SHEET_RIGHT = 16;
	private static final int SHEET_TITLE_Y = 20;
	private static final int SHEET_LINE_Y = 42;
	private static final int SHEET_DONE_HEIGHT = 18;
	private static final String SHEET_DONE = "Done";
	private int sheetDoneX = -1;
	private int sheetDoneWidth;
	private float sheetDoneHover;

	private boolean showsSheet() {
		// in the pane or a settings window any sub-page is a sheet over the feature's settings: Done takes it back
		return subPage != null && (subPage.isSheet() || single != null) && !searching();
	}

	/** Draws the surface of a sheet, behind its title and rows. */
	private void drawSheetSurface(Ui ui) {
		Palette palette = ui.palette;
		int left = contentLeft - SHEET_PAD;
		int top = windowY + SHEET_MARGIN + sheetTop();
		int width = contentRight - previewRoom() + SHEET_RIGHT - left;
		int height = windowHeight - 2 * SHEET_MARGIN - sheetTop();
		ui.shadow(left, top, width, height, Radius.XL, SHADOW_SPREAD_SHEET, SHADOW_OPACITY_SHEET);
		ui.rounded(left, top, width, height, Radius.XL, palette.popup());
		ui.ring(left, top, width, height, Radius.XL, 1, palette.popupBorder());
	}

	private static final int SHADOW_SPREAD_SHEET = 14;
	private static final float SHADOW_OPACITY_SHEET = 0.45f;

	/** Draws a sheet's title, the line under it, and Done at the right. */
	private void drawSheetHeader(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int right = contentRight - previewRoom();
		sheetDoneWidth = ui.width(SHEET_DONE);
		sheetDoneX = right - sheetDoneWidth;
		sheetDoneHover = ui.approach(sheetDoneHover, overSheetDone(mouseX, mouseY) ? 1f : 0f, Ui.HOVER_RATE);
		int titleY = windowY + SHEET_TITLE_Y + sheetTop();
		ui.textAtScaledTitle(ui.fit(subPage.nameText(), sheetDoneX - contentLeft - PAD), contentLeft, titleY, TextScale.TITLE, palette.text());
		ui.textMiddle(SHEET_DONE, sheetDoneX, titleY + SHEET_DONE_HEIGHT / 2, Ui.mix(palette.textSecondary(), palette.text(), sheetDoneHover));
		ui.textAtScaled(ui.fitScaled(subPage.descriptionText(), right - contentLeft, TextScale.SMALL), contentLeft, windowY + SHEET_LINE_Y + sheetTop(), TextScale.SMALL, palette.textSecondary());
	}

	private boolean overSheetDone(double mouseX, double mouseY) {
		int top = windowY + SHEET_TITLE_Y + sheetTop();
		return showsSheet() && sheetDoneX >= 0 && mouseX >= sheetDoneX - 4 && mouseX < sheetDoneX + sheetDoneWidth + 4 && mouseY >= top && mouseY < top + SHEET_DONE_HEIGHT;
	}

	private float backHover;
	/** Half the height of a title's letters at scale 1, to center it on the back button. */
	private static final float TITLE_HALF = 4.5f;

	/** @return whether the point is on a sub-page's back button or the page's name after it */
	private boolean overBack(double mouseX, double mouseY) {
		int top = windowY + HEADER_TILE_TOP + (HEADER_TILE - BACK_BUTTON) / 2;
		return subPage != null && !searching() && backRight > 0 && mouseX >= pageLeft() && mouseX < backRight && mouseY >= top && mouseY < top + BACK_BUTTON;
	}

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
		windowHeight = fullHeight;
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
		windowY = Math.round(shownCenterY - fullHeight * zoom / 2f);
	}

	/**
	 * Moves the window's scale and center toward their targets on springs (instant with animations off), following the mouse directly while it
	 * is dragged. The scale for text and shapes changes only once the window settled.
	 */
	private void animateGeometry(Ui ui) {
		if (owner == null && single == null) {
			windowWidth = wantedWidth();
			fullHeight = wantedHeight();
			windowHeight = fullHeight;
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
		if (owner != null || mouseX < windowX || mouseX >= windowX + windowWidth || mouseY < windowY) {
			return false;
		}
		return mouseY < windowY + TITLE_BAR || single == null && mouseX >= windowX + side() && mouseX < listRight() && mouseY < contentTop - 2;
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

	private double inWindowY(double screenY) {
		return windowY + (screenY - windowY) / zoom;
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
		}
		dragged = null;
		Feature page = page();
		List<Entry> entries = pageEntries();
		visibleSettings = entries.stream().map(Entry::setting).toList();

		Map<String, Group> byTitle = new LinkedHashMap<>();
		// a folder is a feature (or page group) that expands and collapses: its first setting is always shown, the rest when expanded
		Map<String, Group> folderFirst = new HashMap<>();
		Map<String, Integer> folderCount = new HashMap<>();
		for (Entry entry : entries) {
			Setting<?> setting = entry.setting();
			Group group = byTitle.computeIfAbsent(entry.title(), t -> new Group(t, new ArrayList<>()));
			if (entry.collapsible()) {
				String key = key() + ":" + entry.folder();
				boolean open = OPEN_GROUPS.contains(key);
				int count = folderCount.merge(entry.folder(), 1, Integer::sum);
				if (count == 1) {
					folderFirst.put(entry.folder(), group);
				} else if (count == 2) {
					var toggle = new ButtonSetting("options_" + entry.folder(), Component.literal("Options"), Component.literal(open ? "Hide" : "Show"), false, () -> {
						if (!OPEN_GROUPS.remove(key)) {
							OPEN_GROUPS.add(key);
						}
						rebuildRequested = true;
					});
					Control toggleControl = toggle.createControl(this::onSettingChanged);
					controls.put(toggle, toggleControl);
					folderFirst.get(entry.folder()).rows().add(new Row(toggle, toggleControl));
				}
				if (count >= 2 && !open) {
					if (group.rows().isEmpty()) {
						byTitle.remove(entry.title());
					}
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
		animateGroupChanges(before);
		if (focused != null && !controls.containsValue(focused)) {
			focused = null;
		}
		pageBuilt();
	}

	/** How long a group takes to grow in or shrink away when a change of the page shows or hides it. */
	private static final float GROUP_CHANGE_SECONDS = 0.25f;

	/**
	 * After the same page was built again: a group that is new grows in, and a group that is gone stays where it was while it shrinks away, so the
	 * groups below move smoothly. A group of the page that stays keeps how far it is shown.
	 */
	private void animateGroupChanges(List<Group> before) {
		if (before.isEmpty() || ModFeatures.MENU == null || !ModFeatures.MENU.animations()) {
			return;
		}
		Map<String, Group> kept = new HashMap<>();
		for (Group group : groups) {
			kept.put(group.title(), group);
		}
		for (Group group : groups) {
			Group old = findGroup(before, group.title());
			group.shown = old == null ? 0f : old.shown;
		}
		for (int i = 0; i < before.size(); i++) {
			Group old = before.get(i);
			if (!kept.containsKey(old.title()) && old.shown > 0f) {
				old.leaving = true;
				groups.add(Math.min(i, groups.size()), old);
			}
		}
	}

	private static Group findGroup(List<Group> groups, String title) {
		for (Group group : groups) {
			if (group.title().equals(title)) {
				return group;
			}
		}
		return null;
	}

	/** Moves the groups that appear or disappear on; a group that is gone is dropped once it shrank away. */
	private void stepGroupChanges(Ui ui) {
		for (int i = groups.size() - 1; i >= 0; i--) {
			Group group = groups.get(i);
			group.shown = ui.approach(group.shown, group.leaving ? 0f : 1f, 1f / GROUP_CHANGE_SECONDS);
			if (group.leaving && group.shown <= 0f) {
				groups.remove(i);
			}
		}
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

	// the traffic lights: small dots as in macOS (12 px with 8 px between them in the mockup), 18 px from the window's top left there
	private static final int TRAFFIC_SIZE = 8;
	private static final int TRAFFIC_STEP = TRAFFIC_SIZE + 5;
	private static final int TRAFFIC_LEFT = 11;
	private static final int TRAFFIC_TOP = 11;

	private static final int SEARCH_WIDTH = SIDEBAR - 2 * SIDE_INSET;
	/** As tall as a sidebar row, as in the mockup (34 px there). */
	private static final int SEARCH_HEIGHT = 22;
	/** Top of the search field, 16 px (mockup) below the traffic lights. */
	private static final int SEARCH_TOP = TRAFFIC_TOP + TRAFFIC_SIZE + 10;
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
			if (!(feature instanceof MenuFeature) && matchesWords(words, featureName)) {
				final Feature opened = feature;
				var hit = new SearchHitSetting("hit_" + number, Component.literal(featureName), "Feature  ·  " + pathOf(page), () -> jumpTo(opened, null),
					opened.isSwitchable() ? opened::isEnabled : null, () -> {
						opened.toggle();
						FeatureManager.saveSoon();
					});
				number = addHit(featureHits, shown, hit.withIcon(page.iconName(), page.iconRgb()), number);
			}
			for (Setting<?> setting : feature.settings()) {
				if (!setting.isVisible() || setting instanceof FeatureRowSetting || setting instanceof FeatureGroupSetting || setting.key().equals("enabled")) {
					continue;
				}
				String section = feature.sectionOf(setting) == null ? "" : feature.sectionOf(setting);
				String explanation = setting.description() == null ? "" : setting.description();
				if (!matchesWords(words, setting.labelText() + " " + section + " " + explanation)) {
					continue;
				}
				final Feature owner = feature;
				final Setting<?> target = setting;
				String path = "Setting  ·  " + featureName + (section.isEmpty() ? "" : "  ›  " + section);
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
			|| setting != null && page != null && page.settings().contains(setting);
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
			int tab = subTitles.indexOf(found.title());
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

	// the search field sits in the sidebar, below the traffic lights
	private int searchX() {
		return windowX + SIDE_INSET;
	}

	private int searchY() {
		return windowY + SEARCH_TOP;
	}

	private boolean overSearch(double mouseX, double mouseY) {
		return mouseX >= searchX() && mouseX < searchX() + SEARCH_WIDTH && mouseY >= searchY() && mouseY < searchY() + SEARCH_HEIGHT;
	}

	private static final String SEARCH_HINT = "Search settings";
	private static final String SEARCH_HINT_SHORT = "Search";
	private static final String SEARCH_KEY = "Ctrl F";

	private void drawSearch(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int x = searchX();
		int y = searchY();
		int middle = y + SEARCH_HEIGHT / 2;
		boolean hover = overSearch(mouseX, mouseY);
		// a slightly lighter fill without a border, as in the mockup; focused, a thin accent ring
		ui.rounded(x, y, SEARCH_WIDTH, SEARCH_HEIGHT, Radius.SM, Ui.withOpacity(palette.hover(), hover || searchFocused ? 1f : 0.7f));
		if (searchFocused) {
			ui.ring(x, y, SEARCH_WIDTH, SEARCH_HEIGHT, Radius.SM, 1, ui.accent);
		}
		int glass = palette.textSecondary();
		ui.icon("search", x + SEARCH_PAD, middle - SEARCH_ICON / 2, SEARCH_ICON, searchFocused ? Ui.mix(glass, ui.accent, 0.75f) : glass);
		int textX = x + SEARCH_PAD + SEARCH_ICON + 5;
		if (query.isEmpty() && !searchFocused) {
			// the shortcut as a small key at the end, and the hint, at the size of the descriptions, shortened to what fits before it
			float keyWidth = ui.widthScaled(SEARCH_KEY, TextScale.SMALL) + 7f;
			int keyX = Math.round(x + SEARCH_WIDTH - SEARCH_PAD + 2 - keyWidth);
			ui.ring(keyX, middle - 6, Math.round(keyWidth), 12, Radius.XS, 1, Ui.withOpacity(palette.textSecondary(), 0.35f));
			ui.textAtScaled(SEARCH_KEY, keyX + 3.5f, middle - 3.5f, TextScale.SMALL, glass);
			int room = keyX - 4 - textX;
			String hint = ui.widthScaled(SEARCH_HINT, TextScale.BODY) <= room ? SEARCH_HINT : ui.fitScaled(SEARCH_HINT_SHORT, room, TextScale.BODY);
			ui.textAtScaled(hint, textX, middle - 4f, TextScale.BODY, glass);
			return;
		}
		int right = x + SEARCH_WIDTH - (query.isEmpty() ? SEARCH_PAD : 20);
		String shown = ui.fit(query, right - textX);
		if (searchFocused) {
			querySelection.draw(ui, textX, middle, ui.width(shown));
		}
		ui.textMiddle(shown, textX, middle, palette.text());
		if (searchFocused && !querySelection.all() && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(textX + ui.width(shown) + 1, y + 5, 1, SEARCH_HEIGHT - 10, palette.text());
		}
		if (!query.isEmpty()) {
			ui.icon("close", x + SEARCH_WIDTH - 15, middle - 5, 10, glass);
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
			if (!entry.title().isEmpty() && !entry.title().equals("Reset") && !titles.contains(entry.title())) {
				titles.add(entry.title());
			}
		}
		// Whether to use tabs is decided by all settings of the page, including hidden ones: a setting that hides others (a style with fewer
		// options) must not make the tabs disappear and the whole page change under the mouse.
		int size = list ? all.size() : settingCount(page());
		if (!tabbed || searching() || (size < (list ? 8 : 16) && !page().alwaysTabbed()) || titles.size() < 2) {
			subTitles = List.of();
			return all;
		}
		// keep the open tab, by name, when tabs before it appear or disappear
		if (subTitle != null && titles.contains(subTitle)) {
			subIndex = titles.indexOf(subTitle);
		}
		subTitles = titles;
		subIndex = Mth.clamp(subIndex, 0, titles.size() - 1);
		String chosen = titles.get(subIndex);
		subTitle = chosen;
		return all.stream().filter(entry -> entry.title().isEmpty() || entry.title().equals("Reset") || entry.title().equals(chosen)).toList();
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
	private final Spring tabSpring = Spring.bouncy(Spring.DEFAULT);
	private boolean tabSpringSet;

	/** @return top of the tab bar: below the header, or below the title bar in a settings window */
	private int tabsY() {
		return single != null ? windowY + TITLE_BAR + 8 : windowY + 62;
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
			texts[i] = (int) Math.ceil(ui.boldWidthScaled(subTitles.get(i), TextScale.BODY));
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
		if (!tabSpringSet) {
			tabSpring.snap(selected);
			tabSpringSet = true;
		}
		float at = ui.animated ? Mth.clamp(tabSpring.follow(ui, selected), 0f, tabsShown - 1) : selected;
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
			if (i == selected) {
				// the selected tab in the semibold face, as in the mockups
				String label = subTitles.get(i);
				ui.textAtScaledBold(label, center - ui.boldWidthScaled(label, 1f) / 2f, y + TAB_HEIGHT / 2f - TEXT_HALF_HEIGHT, 1f, color);
			} else {
				ui.textCentered(subTitles.get(i), center, y + TAB_HEIGHT / 2, color);
			}
		}
	}

	private void openTab(int tab) {
		UiSounds.play(UiSounds.Cue.TICK);
		subIndex = tab;
		subTitle = null;
		scroll = 0;
		scrollTarget = 0;
		restartPage();
		buildPage();
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
	private int introY;
	/** The master switch in a settings window's title bar. */
	private final Spring headerSwitch = Spring.bouncy(Spring.DEFAULT);
	private boolean headerSwitchSet;
	private final ButtonControl footerReset = new ButtonControl(Component.literal("Reset"), false, this::resetPage).confirm();
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
		feature.resetSettings();
		UiSounds.play(UiSounds.Cue.OFF);
		onSettingChanged();
		rebuildRequested = true;
	}

	private void countChanged() {
		Feature feature = footerFeature();
		changedCount = feature != null ? feature.changedSettings() : 0;
		footerText = changedCount == 0 ? "All at their defaults" : changedCount == 1 ? "1 changed from default" : changedCount + " changed from default";
	}

	private int headerSwitchX() {
		// in the pane, the switch sits left of the close button
		return windowX + windowWidth - PAD - SwitchControl.TRACK_WIDTH - (owner != null ? PANE_BUTTON + 8 : 0);
	}

	private int headerSwitchY() {
		return windowY + TITLE_BAR / 2 - SwitchControl.TRACK_HEIGHT / 2;
	}

	private boolean overHeaderSwitch(double mouseX, double mouseY) {
		return featureWindow() && !showsSheet() && page().isSwitchable() && mouseX >= headerSwitchX() - 4 && mouseX < headerSwitchX() + SwitchControl.TRACK_WIDTH + 4
			&& mouseY >= headerSwitchY() - 4 && mouseY < headerSwitchY() + SwitchControl.TRACK_HEIGHT + 4;
	}

	/** Draws a settings window's title bar after the traffic lights: the feature's icon on its tile, its name and its master switch. */
	private void drawTitleBar(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		Feature page = page();
		int middle = windowY + TITLE_BAR / 2;
		// after the window buttons; in the pane, at its edge, or after the back arrow when it covers the page
		int tileX = owner == null ? trafficX(2) + TRAFFIC_SIZE / 2 + 14 : windowX + PAD + (paneBack() ? PANE_BUTTON + 6 : 0);
		ui.iconTile(page.iconName(), tileX, middle - TITLE_TILE / 2, TITLE_TILE, TITLE_ICON, Radius.SM, 0xFF000000 | page.iconRgb(), 0xFFFFFFFF);
		int nameX = tileX + TITLE_TILE + 8;
		boolean switchable = featureWindow() && page.isSwitchable();
		int nameRight = switchable ? headerSwitchX() - 10 : owner != null ? paneCloseX() - 8 : windowX + windowWidth - PAD;
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
	}

	/** Draws the introduction above the settings; it scrolls with them. */
	private void drawIntro(Ui ui) {
		int color = Ui.mix(ui.palette.text(), ui.palette.textSecondary(), 0.45f);
		for (int i = 0; i < introLines.size(); i++) {
			ui.text(introLines.get(i), contentLeft + 2, introY + i * INTRO_LINE, color);
		}
	}

	/** Draws the footer: how many settings differ from their defaults, and the button that resets them. */
	private void drawFooter(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int top = windowY + windowHeight - FOOTER_HEIGHT;
		ui.fill(windowX + side() + 1, top, listRight() - windowX - side() - 2, 1, palette.cardBorder());
		int middle = top + FOOTER_HEIGHT / 2;
		ui.textMiddle(footerText, contentLeft + 2, middle, palette.textSecondary());
		int buttonWidth = footerReset.inlineWidth(ui, 120);
		footerReset.place(ui, contentRight - buttonWidth, middle - 10, buttonWidth);
		footerReset.render(ui, mouseX, mouseY);
	}


	// ----------------------------------------------------------------------------------------------------- layout

	/** @return whether a group's title is drawn: not when it is empty, and not when it only repeats the name of the open tab */
	private boolean showsTitle(Group group) {
		// a title of spaces keeps a group apart from the others without a title over it
		return !group.title().isBlank() && !(!subTitles.isEmpty() && group.title().equals(subTitle));
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
		placed.clear();
		applyGeometry();
		// a settings window starts below its title bar, a page below its header; both below the tabs if there are any
		int top = single != null && !showsSheet() ? windowY + TITLE_BAR + 10 : windowY + 66 + sheetTop() - (compactPage() ? COMPACT_HEADER : 0);
		contentTop = subTitles.isEmpty() ? top : tabsY() + TAB_HEIGHT + 10;
		contentBottom = windowY + windowHeight - 8 - (hasFooter() ? FOOTER_HEIGHT : 0);
		// a page with a live preview leaves its right side to it
		int contentWidth = contentRight - contentLeft - previewRoom();

		int slide = Math.round((1f - Mth.clamp(pageAnimation, 0f, 1f)) * 16f);
		int y = contentTop - (int) scroll + slide;
		// a settings window opens with what the feature does, scrolling with the settings
		introLines = hasIntro() ? ui.wrap(page().descriptionText(), contentWidth - 4, INTRO_MAX_LINES) : List.of();
		introY = y;
		if (!introLines.isEmpty()) {
			y += introLines.size() * INTRO_LINE + 10;
		}
		boolean compact = compactPage();
		int rowPad = compact ? COMPACT_ROW_PAD : ROW_PAD;
		// switches sit at the right edge of every row; on pages where rows have a switch plus an Edit button, buttons without a switch align with
		// the Edit buttons so they form one column (editColumn)
		for (int g = 0; g < groups.size(); g++) {
			Group group = groups.get(g);
			if (g == placedPool.size()) {
				placedPool.add(new Placed());
			}
			Placed p = placedPool.get(g);
			p.group = group;
			p.rows = 0;
			p.titleY = 0;
			p.top = y;
			p.titled = showsTitle(group);
			if (p.titled) {
				p.titleY = y + (compact ? COMPACT_TITLE_TOP : 4);
				y += compact ? COMPACT_TITLE : 22;
			}
			p.cardX = contentLeft;
			p.cardY = y;
			p.cardWidth = contentWidth;

			int rowY = y;
			// bare cards before the first setting (the theme cards) sit in their own row above the group's card, not inside it
			boolean leading = !group.bare();
			for (Row row : group.rows()) {
				Control control = row.control();
				int innerX = contentLeft + CARD_PAD;
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
					control.place(ui, contentLeft, rowY, contentWidth);
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
					int natural = control.inlineWidth(ui, innerWidth - indent);
					descriptionLines = descriptionLines(ui, row, innerWidth - indent);
					int textHeight = 12 + descriptionLines * DESCRIPTION_LINE;
					int controlX = control.leftOnOwnLine() ? innerX + indent : innerX + innerWidth - natural;
					control.place(ui, controlX, rowY + rowPad + textHeight + CONTROL_BELOW_GAP, natural);
					rowHeight = rowPad * 2 + textHeight + CONTROL_BELOW_GAP + control.height();
				} else {
					int labelWidth = ui.width(row.setting().labelText()) + iconRoom(row.setting()) + indent;
					// the control gets the width its content needs, up to half the row, and the label is truncated with an ellipsis if it does not fit
					// the rest: a long label must not leave a button too narrow for its text
					int roomLeft = Math.max(60, innerWidth - labelWidth - 16);
					int natural = control.inlineWidth(ui, innerWidth);
					int inline = natural <= roomLeft ? natural : Math.min(natural, Math.max(roomLeft, innerWidth / 2));
					int right = innerX + innerWidth - (editColumn && control.isLoneButton() ? Control.SWITCH_COLUMN : 0);
					// an inline control's height does not depend on its position, so place it at the top first
					control.place(ui, right - inline, rowY, inline);
					int beside = right - innerX - inline - iconRoom(row.setting()) - indent - 12;
					// a description squeezed into a narrow column beside a wide control (a text field) goes under the row at its full width instead
					below = row.setting().description() != null && beside < DESCRIPTION_MIN_ROOM;
					descriptionLines = descriptionLines(ui, row, below ? innerWidth - indent : beside);
					int lineHeight = Math.max(control.height(), 20);
					if (below) {
						rowHeight = lineHeight + rowPad * 2 + descriptionLines * DESCRIPTION_LINE + 2;
						control.place(ui, right - inline, rowY + rowPad + (lineHeight - control.height()) / 2, inline);
					} else {
						int textHeight = 12 + descriptionLines * DESCRIPTION_LINE;
						rowHeight = Math.max(lineHeight, textHeight) + rowPad * 2;
						control.place(ui, right - inline, rowY + (rowHeight - control.height()) / 2, inline);
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
			int full = rowY + (compact ? COMPACT_GROUP_GAP : 14) - p.top;
			if (group.shown < 1f) {
				// appearing or disappearing: the space it takes grows or shrinks smoothly, and it shows only within it
				float eased = group.shown * group.shown * (3f - 2f * group.shown);
				p.clipHeight = Math.round(full * eased);
			} else {
				p.clipHeight = -1;
			}
			y = p.top + (p.clipHeight < 0 ? full : p.clipHeight);
		}
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
		float middleY = windowY + windowHeight * zoom / 2f;
		pose.translate(middleX, middleY);
		pose.scale(grow, grow);
		pose.translate(-middleX, -middleY);
		pose.translate(windowX, windowY);
		pose.scale(zoom, zoom);
		pose.translate(-windowX, -windowY);

		// lifted while dragged: a stronger shadow under the window
		if (windowLift > 0.01f) {
			ui.shadow(windowX, windowY, windowWidth, windowHeight, Radius.XXL, LIFT_SHADOW, LIFT_OPACITY * windowLift * fade);
		}
		boolean solidPane = frameBody(ui, windowX, windowY, windowWidth, windowHeight, fade);
		if (single == null) {
			ui.roundedLeft(windowX, windowY, SIDEBAR, windowHeight, Radius.XXL, Ui.withOpacity(palette.sidebar(), fade));
			ui.fill(windowX + SIDEBAR, windowY + 1, 1, windowHeight - 2, palette.cardBorder());
		}
		drawColoredLight(ui, fade);
		// the edge light goes over the sidebar, which would otherwise cover it on the left
		frameEdges(ui, windowX, windowY, windowWidth, windowHeight, fade, solidPane);

		// from here on everything fades with the window: text, controls and icons, not only the glass
		Ui.setAlpha(fade);
		drawTrafficLights(ui, mouseX, mouseY);
		if (single == null) {
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
		stepGroupChanges(ui);
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
		if (!paneBack() && !owner.paneDetached()) {
			ui.fill(windowX, windowY + 1, 1, windowHeight - 2, ui.palette.cardBorder());
		}
		drawPage(ui, graphics, mouseX, mouseY);
		if (!showsSheet()) {
			drawPaneButtons(ui, mouseX, mouseY);
		}
		drawOverlays(ui, mouseX, mouseY);
	}

	/** Draws the page: its header or title bar, tabs, settings, preview and footer, but not the popups on top. */
	private void drawPage(Ui ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		if (showsSheet()) {
			drawSheetSurface(ui);
		}
		if (single != null && showsSheet()) {
			drawSheetHeader(ui, mouseX, mouseY);
		} else if (single != null) {
			drawTitleBar(ui, mouseX, mouseY);
		} else {
			drawHeader(ui, mouseX, mouseY);
		}
		drawSubTabs(ui, mouseX, mouseY);

		// the scrollable page, clipped at its edges
		graphics.enableScissor(windowX + side() + 1, contentTop - 2, listRight() - 1, contentBottom);
		boolean overlayOpen = openOverlay() != null;
		int pointerX = overlayOpen ? -1 : mouseX;
		int pointerY = overlayOpen ? -1 : mouseY;
		if (!introLines.isEmpty()) {
			drawIntro(ui);
		}
		for (Placed p : placed) {
			drawGroup(ui, p, pointerX, pointerY);
		}
		for (int c = 0; c < controlList.size(); c++) {
			Control control = controlList.get(c);
			control.renderSticky(ui, pointerX, pointerY);
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

	private float lightsHover;

	// the symbols on the traffic lights, shown on hover
	private static final int TRAFFIC_GLYPH = 6;
	private static final int[] TRAFFIC_COLORS = {0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840};
	private static final int[] TRAFFIC_GLYPH_COLORS = {0xFF4D0000, 0xFF5A3A00, 0xFF0A4A12};
	private static final String[] TRAFFIC_GLYPHS = {"close", "minus", "plus"};

	/** @return the center of a traffic light */
	private int trafficX(int index) {
		return windowX + TRAFFIC_LEFT + TRAFFIC_SIZE / 2 + index * TRAFFIC_STEP;
	}

	/** @return the middle of the traffic lights: on their own row in the sidebar, or centered in a settings window's title bar */
	private int trafficY() {
		return single != null ? windowY + TITLE_BAR / 2 : windowY + TRAFFIC_TOP + TRAFFIC_SIZE / 2;
	}

	/** @return the traffic light under the mouse, or -1 */
	private int trafficLightAt(double mouseX, double mouseY) {
		for (int light = 0; light < 3; light++) {
			if (Math.hypot(mouseX - trafficX(light), mouseY - trafficY()) <= TRAFFIC_SIZE / 2f + 2f) {
				return light;
			}
		}
		return -1;
	}

	private void drawTrafficLights(Ui ui, int mouseX, int mouseY) {
		int cy = trafficY();
		int half = TRAFFIC_SIZE / 2;
		boolean hovering = mouseX >= trafficX(0) - half - 3 && mouseX < trafficX(2) + half + 3 && Math.abs(mouseY - cy) < half + 3;
		lightsHover = ui.approach(lightsHover, hovering ? 1f : 0f, Ui.HOVER_RATE);
		for (int i = 0; i < 3; i++) {
			int cx = trafficX(i);
			ui.circle(cx, cy, half, TRAFFIC_COLORS[i]);
			ui.ring(cx - half, cy - half, TRAFFIC_SIZE, TRAFFIC_SIZE, Radius.pill(TRAFFIC_SIZE), 1, 0x26000000);
			if (lightsHover > 0f) {
				// glyphs appear on hover, as in macOS, centered in the dot
				ui.icon(TRAFFIC_GLYPHS[i], cx - TRAFFIC_GLYPH / 2, cy - TRAFFIC_GLYPH / 2, TRAFFIC_GLYPH, Ui.withOpacity(TRAFFIC_GLYPH_COLORS[i], lightsHover));
			}
		}
	}

	/**
	 * Traffic light buttons: red closes the menu, yellow makes the window a step smaller and green a step larger, as the window size in the
	 * menu settings does, around the window's middle.
	 */
	private void trafficLight(int index) {
		if (index == 0) {
			onClose();
			return;
		}
		UiSounds.play(UiSounds.Cue.CLICK);
		resizeBy(index == 1 ? -1 : 1, shownCenterX, shownCenterY);
	}
	// ------------------------------------------------------------------------------------------------------ sidebar

	/** Top of the first page item, below the search field. */
	private static final int ITEMS_TOP = SEARCH_TOP + SEARCH_HEIGHT + 8;
	// rows as in the mockup: 36 px tall, 3 px apart, 24 px icon tiles
	private static final int ITEM_HEIGHT = 23;
	private static final int ITEM_GAP = 2;
	/** Size of the colored tile behind a page icon, and its corner radius. */
	private static final int TILE = 16;
	private static final int TILE_ICON = 9;
	private static final float TILE_RADIUS = 4f;
	private static final int TILE_LEFT = 6;
	/** Space below the last item of the bottom section (Edit HUD layout, Menu settings). */
	private static final int BOTTOM_PAD = 8;

	/** Height of a category item, which sits indented under its page (30 px in the mockup). */
	private static final int SUB_ITEM_HEIGHT = 19;
	private static final int SUB_ITEM_GAP = 1;
	/** Indent of the category items, and where the guide line in front of them runs. */
	private static final int SUB_ITEM_INDENT = 20;
	private static final int SUB_GUIDE_X = 13;
	/** Space below the last category item, before the next page. */
	private static final int SUB_ITEMS_AFTER = 3;
	private static final int HERE_DOT = 3;

	/** What a page item shows on its right: the number of features, or null; built once, the pages do not change while the menu is open. */
	private String[] sidebarCounts;
	private float hudItemHover;
	/** Tops of the page items relative to the window, laid out every frame since the categories fold in and out. */
	private int[] sidebarTops = new int[0];
	/** How far the categories under the SkyBlock page are folded out, from 0 to 1. */
	private float sidebarExpand = -1f;
	private final Spring expandSpring = Spring.smooth(Spring.DEFAULT);
	private boolean highlightSnapped;
	private final Spring highlightHeight = Spring.bouncy(Spring.DEFAULT);
	private final Spring highlightIndent = Spring.bouncy(Spring.DEFAULT);

	/** @return whether the page is a category of another page, listed indented under it */
	private boolean isSubPage(int index) {
		return pages.get(index) instanceof FeatureListFeature list && list.parentPage() != null;
	}

	private int itemHeightOf(int index) {
		return isSubPage(index) ? SUB_ITEM_HEIGHT : ITEM_HEIGHT;
	}

	/** @return whether the categories are folded out: while their page or one of them is shown */
	private boolean categoriesOpen() {
		Feature page = pages.get(Mth.clamp(selectedIndex, 0, pages.size() - 1));
		return page instanceof FeatureListFeature list && (list.hasSubPages() || list.parentPage() != null);
	}

	/** Lays out the page items: the regular pages from the top with gaps between their groups, the menu settings at the bottom. */
	private void layoutSidebar(Ui ui) {
		if (sidebarTops.length != pages.size()) {
			sidebarTops = new int[pages.size()];
		}
		float target = categoriesOpen() ? 1f : 0f;
		// the categories fold on the same spring as the highlight moving between items, so both arrive together
		sidebarExpand = sidebarExpand < 0f ? expandSpring.snap(target) : Mth.clamp(expandSpring.follow(ui, target), 0f, 1f);
		float y = ITEMS_TOP;
		int regular = regularCount();
		for (int i = 0; i < regular; i++) {
			if (gapBefore(i)) {
				y += GROUP_GAP;
			}
			sidebarTops[i] = Math.round(y);
			if (isSubPage(i)) {
				y += (SUB_ITEM_HEIGHT + SUB_ITEM_GAP + (i + 1 < regular && !isSubPage(i + 1) ? SUB_ITEMS_AFTER : 0)) * sidebarExpand;
			} else {
				y += ITEM_HEIGHT + ITEM_GAP;
			}
		}
		// what does not fit scrolls, above the fixed part at the bottom and never over it
		sidebarListEnd = hudItemY() - windowY - SIDEBAR_BOTTOM_ROOM;
		sidebarMostScroll = Math.max(0f, y - sidebarListEnd);
		keepSelectedInSidebar(regular);
		sidebarScroll = Mth.clamp(sidebarScroll, 0f, sidebarMostScroll);
		int moved = Math.round(sidebarScroll);
		for (int i = 0; i < regular; i++) {
			sidebarTops[i] -= moved;
		}
		if (menu != null) {
			sidebarTops[pages.size() - 1] = windowHeight - BOTTOM_PAD - ITEM_HEIGHT;
		}
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
			if (isSubPage(i) && sidebarExpand < 0.9f) {
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
				if (pages.get(i) instanceof FeatureListFeature list && !list.hasSubPages() && (list.featureCount() > 1 || list.parentPage() != null)) {
					sidebarCounts[i] = String.valueOf(list.featureCount());
				}
			}
		}
		if (itemHover.length != pages.size()) {
			itemHover = new float[pages.size()];
		}
		int selected = Mth.clamp(selectedIndex, 0, pages.size() - 1);
		float targetY = sidebarTops[selected];
		float targetIndent = isSubPage(selected) ? SUB_ITEM_INDENT : 0f;
		if (!highlightSnapped) {
			highlightSpring.snap(targetY);
			highlightHeight.snap(itemHeightOf(selected));
			highlightIndent.snap(targetIndent);
			highlightSnapped = true;
		}
		// the highlight springs to the selected item, in position, height and indent, and keeps its velocity when another one is clicked mid-flight
		int highlightY = windowY + Math.round(ui.animated ? highlightSpring.follow(ui, targetY) : targetY);
		int highlightH = Math.round(ui.animated ? highlightHeight.follow(ui, itemHeightOf(selected)) : itemHeightOf(selected));
		int indent = Math.round(ui.animated ? highlightIndent.follow(ui, targetIndent) : targetIndent);

		boolean bottomSelected = menu != null && selected == pages.size() - 1;
		ui.graphics.enableScissor(windowX, windowY + ITEMS_TOP - 1, windowX + SIDEBAR, windowY + sidebarListEnd);
		// the page whose category is shown keeps a light highlight of its own
		if (isSubPage(selected)) {
			int parent = pages.indexOf(((FeatureListFeature) pages.get(selected)).parentPage());
			if (parent >= 0) {
				ui.rounded(itemX, windowY + sidebarTops[parent], itemWidth, ITEM_HEIGHT, Radius.SM, palette.hover());
			}
		}
		int hovered = sidebarItemAt(mouseX, mouseY);
		boolean subsShown = sidebarExpand > 0.02f;
		int subsTop = -1;
		int subsBottom = -1;
		for (int i = 0; i < pages.size(); i++) {
			if (isSubPage(i)) {
				if (subsShown) {
					subsTop = subsTop < 0 ? windowY + sidebarTops[i] : subsTop;
					subsBottom = windowY + sidebarTops[i] + Math.round(SUB_ITEM_HEIGHT * sidebarExpand);
				}
				continue;
			}
			itemHover[i] = ui.approach(itemHover[i], hovered == i && i != selected ? 1f : 0f, Ui.HOVER_RATE);
		}
		// the highlight is drawn before the categories are clipped: moving to or from a category while they fold, it must not be cut off by
		// their shrinking area (it vanished and popped into place when leaving a SkyBlock category)
		if (!bottomSelected && (!isSubPage(selected) || subsShown)) {
			ui.rounded(itemX + indent, highlightY, itemWidth - indent, highlightH, Radius.SM, ui.accent);
		}
		if (subsShown && subsTop >= 0) {
			// the categories fold out of their page: clipped to the space they have taken so far
			ui.graphics.enableScissor(itemX, subsTop, itemX + itemWidth, subsBottom);
			ui.fill(itemX + SUB_GUIDE_X, subsTop + 2, 1, subsBottom - subsTop - 4, palette.cardBorder());
		}
		for (int i = 0; i < pages.size(); i++) {
			if (isSubPage(i)) {
				if (subsShown) {
					itemHover[i] = ui.approach(itemHover[i], hovered == i && i != selected ? 1f : 0f, Ui.HOVER_RATE);
					drawSubItem(ui, i, i == selected);
				}
			}
		}
		if (subsShown && subsTop >= 0) {
			ui.graphics.disableScissor();
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

		// the bottom section: the HUD editor, which is an action rather than a page, above the menu settings, under a separator
		int hudY = hudItemY();
		boolean overHud = overHudItem(mouseX, mouseY);
		hudItemHover = ui.approach(hudItemHover, overHud ? 1f : 0f, Ui.HOVER_RATE);
		ui.fill(itemX + 4, hudY - 5, itemWidth - 8, 1, palette.cardBorder());
		// a neutral tile in the theme's control colors, so it reads in the light theme too
		drawSidebarItem(ui, hudY, hudItemHover, false, palette.trackOff(), "layout", palette.text(), "Edit HUD layout", null, false);
	}

	private void drawPageItem(Ui ui, int index, int selected) {
		Feature feature = pages.get(index);
		drawSidebarItem(ui, windowY + sidebarTops[index], itemHover[index], index == selected, 0xFF000000 | feature.iconRgb(), feature.iconName(), 0xFFFFFFFF,
			feature.nameText(), sidebarCounts[index], feature instanceof FeatureListFeature list && list.hasSubPages());
	}

	/** Draws a category item: its name indented under its page, a dot where the player is, and the number of its features. */
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
		String count = sidebarCounts[index];
		if (count != null) {
			ui.textRight(count, right, middle, selected ? 0xCCFFFFFF : Ui.withOpacity(palette.textSecondary(), 0.85f));
		}
		int textX = itemX + 7;
		int room = right - (count != null ? ui.width(count) + 6 : 0) - textX - 2 * HERE_DOT - 6;
		String name = ui.fit(page.nameText(), room);
		ui.textMiddle(name, textX, middle, selected ? 0xFFFFFFFF : Ui.mix(palette.text(), palette.textSecondary(), 0.25f));
		if (page.isHere()) {
			ui.circle(textX + ui.width(name) + 6 + HERE_DOT, middle, HERE_DOT, SystemColors.GREEN);
		}
	}

	/** Draws one sidebar item: the icon on its colored tile, the name, and on the right a count or a chevron. */
	private void drawSidebarItem(Ui ui, int itemY, float hover, boolean selected, int tile, String icon, int iconColor, String name, String count,
		boolean chevron) {
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
			pose.rotate((float) (-Math.PI / 2.0 * (1f - Mth.clamp(sidebarExpand, 0f, 1f))));
			ui.icon("chevron", -4, -4, 8, selected ? 0xCCFFFFFF : Ui.withOpacity(palette.textSecondary(), 0.8f));
			pose.popMatrix();
			trailing = 12;
		} else if (count != null) {
			ui.textRight(count, right, middle, selected ? 0xCCFFFFFF : Ui.withOpacity(palette.textSecondary(), 0.85f));
			trailing = ui.width(count) + 6;
		}
		int textX = tileX + TILE + 6;
		ui.textMiddle(ui.fit(name, right - trailing - textX), textX, middle, selected ? 0xFFFFFFFF : palette.text());
	}

	/** @return top of the HUD editor item, just above the menu settings at the bottom */
	private int hudItemY() {
		return windowY + windowHeight - BOTTOM_PAD - 2 * ITEM_HEIGHT - ITEM_GAP;
	}

	private boolean overHudItem(double mouseX, double mouseY) {
		int itemX = windowX + SIDE_INSET;
		return single == null && mouseX >= itemX && mouseX < itemX + SIDEBAR - 2 * SIDE_INSET && mouseY >= hudItemY() && mouseY < hudItemY() + ITEM_HEIGHT;
	}

	/** @return the sidebar group of a page; groups are separated by a gap */
	private int groupOf(int index) {
		if (isSubPage(index)) {
			return 1; // the categories belong to the group of their page
		}
		return switch (pages.get(index).id()) {
			// the feature pages form one group, the configs another
			case "world", "interface", "skyblock", "music_page", "misc" -> 1;
			case "configs" -> 2;
			default -> index == 0 ? 0 : 3;
		};
	}

	private int regularCount() {
		return pages.size() - (menu != null ? 1 : 0);
	}

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

	/** Checks once where the player is, for the dots in the sidebar and the chip in the header of a category. */
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
			backRight = -1;
			pageSwitchX = -1;
			drawSheetHeader(ui, mouseX, mouseY);
			return;
		}
		if (subPage != null && !searching()) {
			drawSubPageHeader(ui, left, right, mouseX, mouseY);
			pageSwitchX = -1;
			return;
		}
		backRight = -1;
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
		String title = ui.fit(headerTitle, Math.round((right - left) / (TextScale.TITLE * BOLD_WIDER)));
		ui.textAtScaledTitle(title, left, windowY + 18, TextScale.TITLE, ui.palette.text());
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
		int top = single != null ? windowY + TITLE_BAR : windowY + 1;
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
		float[] rows = RoundedRows.edges(left, top, right, bottom, windowX, windowY, right, bottom, Radius.XXL);
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
		return single == null && editor == null && !searching() ? listedPage().preview() : null;
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

	/** Draws the chip saying where the player is, ending at the right edge; @return its left edge */
	private int drawHereChip(Ui ui, int right) {
		int height = 16;
		int width = ui.width(hereText) + 2 * HERE_DOT + 16;
		int x = right - width;
		int y = windowY + 20;
		ui.rounded(x, y, width, height, Radius.pill(height), Ui.withOpacity(SystemColors.GREEN, 0.16f));
		ui.ring(x, y, width, height, Radius.pill(height), 1, Ui.withOpacity(SystemColors.GREEN, 0.35f));
		ui.circle(x + 6 + HERE_DOT, y + height / 2, HERE_DOT, SystemColors.GREEN);
		ui.textMiddle(hereText, x + 10 + 2 * HERE_DOT, y + height / 2, Ui.mix(ui.palette.text(), SystemColors.GREEN, 0.35f));
		return x - 8;
	}

	/** The gap between a row's name (with its description) and its control on the line under it. */
	private static final int CONTROL_BELOW_GAP = 6;

	/** @return whether a row's control keeps its width but does not fit beside the row's name, so it goes on a line of its own under it */
	private boolean controlBelow(Ui ui, Row row, int innerWidth, int indent) {
		if (!row.control().keepsWidth()) {
			return false;
		}
		int labelWidth = ui.width(row.setting().labelText()) + iconRoom(row.setting()) + indent;
		int natural = row.control().inlineWidth(ui, innerWidth - indent);
		return natural > Math.max(60, innerWidth - labelWidth - 16) && natural <= innerWidth - indent;
	}

	private void drawGroup(Ui ui, Placed p, int mouseX, int mouseY) {
		if (p.cardY + p.cardHeight < contentTop - 30 || p.top > contentBottom + 30 || p.clipHeight == 0) {
			// off screen, from its title or leading cards down; the controls still keep their position for keyboard navigation
			return;
		}
		if (p.clipHeight < 0) {
			drawGroupContent(ui, p, mouseX, mouseY);
			return;
		}
		// growing in or shrinking away: only the part within the space it takes now
		ui.graphics.enableScissor(p.cardX - GROUP_CLIP_MARGIN, p.top, p.cardX + p.cardWidth + GROUP_CLIP_MARGIN, p.top + p.clipHeight);
		drawGroupContent(ui, p, mouseX, mouseY);
		ui.graphics.disableScissor();
	}

	/** Room beside a clipped group for its rings and shadows. */
	private static final int GROUP_CLIP_MARGIN = 4;

	private void drawGroupContent(Ui ui, Placed p, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int rowPad = compactPage() ? COMPACT_ROW_PAD : ROW_PAD;
		if (p.titled) {
			ui.sectionLabel(p.group.upperTitle, p.cardX + 4, p.titleY + 1, palette.textSecondary());
		}
		// a group of bare cards (a page's features) gets no card of its own
		if (!p.group.bare()) {
			ui.rounded(p.cardX, p.cardY, p.cardWidth, p.cardHeight, Radius.LG, palette.card());
			ui.ring(p.cardX, p.cardY, p.cardWidth, p.cardHeight, Radius.LG, 1, palette.cardBorder());
		}

		for (int i = 0; i < p.group.rows().size(); i++) {
			Row row = p.group.rows().get(i);
			int top = p.rowTops[i];
			int rowHeight = p.rowHeights[i];
			if (row.control().bare()) {
				row.control().renderSafely(ui, mouseX, mouseY);
				continue;
			}
			// the name starts after the icon; a sub-option has no icon of its own and is indented under the setting it belongs to
			int textX = p.cardX + CARD_PAD + iconRoom(row.setting()) + row.depth * SUB_INDENT;
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
			String shownLabel = row.control().isBlock() && !row.control().hasLabel() ? label : ui.fit(label, Math.max(40, labelRoom));
			if (!row.control().isBlock() || row.control().hasLabel()) {
				ui.textMiddle(shownLabel, textX, labelMiddle, labelColor);
			}
			// a name too long for its room is shown in full on hover
			if (shownLabel.length() != label.length() && mouseX >= textX && mouseX < textX + Math.max(40, labelRoom) && Math.abs(mouseY - labelMiddle) <= 6) {
				tooltip = label;
			}
			if (lines > 0) {
				List<String> explanation = descriptionLines.get(row.setting());
				if (explanation != null) {
					for (int k = 0; k < explanation.size(); k++) {
						ui.textAtScaled(explanation.get(k), textX, descriptionTop + 7 + k * DESCRIPTION_LINE, DESCRIPTION_SCALE,
							Ui.withOpacity(palette.textSecondary(), 0.9f));
					}
				}
			}
			row.control().renderSafely(ui, mouseX, mouseY);
			if (row.setting().isDisabled()) {
				drawDisabled(ui, p, row, mouseX, mouseY);
			}
		}
	}

	/** What a smaller note needs beside its text: the lock and a margin. */
	private static final int DISABLED_NOTE_ROOM = 28;

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
		// a note of its own stands over the control, so that has to stay behind it
		ui.rounded(left, top, right - left, height, Radius.MD, light ? (own != null ? 0xF2F4F4F6 : 0xB8F4F4F6) : own != null ? 0xE6101014 : 0x70101014);
		ui.ring(left, top, right - left, height, Radius.MD, 1, Ui.withOpacity(palette.textSecondary(), 0.18f));
		int middle = top + height / 2;
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
				if (!query.isEmpty() && mouseX >= searchX() + SEARCH_WIDTH - 26) {
					clearQuery();
				}
				return true;
			}
			searchFocused = false;
			if (overPaneClose(mouseX, mouseY) || overPaneBack(mouseX, mouseY)) {
				owner.closeEditor();
				UiSounds.play(UiSounds.Cue.CLOSE);
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
			if (overBack(mouseX, mouseY) || overSheetDone(mouseX, mouseY)) {
				closeSubPage();
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
			if (hasFooter() && footerReset.contains(mouseX, mouseY) && footerReset.mouseClicked(mouseX, mouseY, button)) {
				dragged = footerReset;
				return true;
			}
			int light = owner == null ? trafficLightAt(mouseX, mouseY) : -1;
			if (light >= 0) {
				trafficLight(light);
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
		// Ctrl + wheel over the window changes its size around the mouse
		if (owner == null && single == null && minecraft.hasControlDown() && mouseX >= windowX && mouseX < windowX + windowWidth * zoom && mouseY >= windowY
			&& mouseY < windowY + windowHeight * zoom) {
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
		// Escape on a sub-page goes back to its page; with the settings pane open it closes the menu as before
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
				// Escape closes the whole menu, also with the pane open; the pane comes back with the menu, where it was
				onClose();
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
		if (single == null && control && ScreenInput.letter(event.key(), 'F') && focused == null) {
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
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
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
