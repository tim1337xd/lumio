package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.hud.HudSimulation;

import com.mojang.blaze3d.platform.InputConstants;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * The HUD layout editor, over the game: every HUD element can be selected, dragged (with snapping to the screen, the other elements and a
 * grid), resized by its corner or the mouse wheel, nudged with the arrow keys, and styled in a card next to it. A floating toolbar holds undo
 * and redo, the snap and grid switches, the activity simulation and the list of all elements. Everything uses the menu's look.
 * <p>
 * The parts live in their own classes: {@link HudEditToolbar}, {@link HudEditCard}, {@link HudEditList}, {@link HudEditSimulate},
 * {@link HudEditSnap} and {@link HudEditHistory}.
 */
public class HudEditScreen extends Screen {
	/** How dark the game is behind the editor. */
	private static final int DIM = 0x66000000;
	/** Size of the resize handle at an element's corner, and the reach for grabbing it. */
	private static final int HANDLE = 5;
	private static final int HANDLE_REACH = 8;
	/** An edge inside an element that sets a part's width (the info bar's song segment): how near the mouse must be, its grip, and its label. */
	private static final float EDGE_REACH = 4f;
	private static final float EDGE_GRIP = 3f;
	private static final int EDGE_INSET = 4;
	private static final int EDGE_LABEL_HEIGHT = 12;
	private static final int EDGE_LABEL_PAD = 4;
	/** How much of the accent color the box being dragged has. */
	private static final float BOX_FILL = 0.14f;
	/** The glow around the selected element: how many rings, and how strong the innermost is. */
	private static final int GLOW_RINGS = 3;
	private static final float GLOW_STRENGTH = 0.3f;
	/** The color of the grid dots, and the size of the tile they are drawn from, in GUI pixels (a multiple of the grid). */
	private static final int GRID_DOT = 0x38FFFFFF;
	private static final int GRID_TILE = 128;
	/** How far the mouse must move with the button held before a drag starts, so a click still only selects. */
	private static final int DRAG_THRESHOLD = 3;
	/** The size range of elements in the editor, and the step sizes move in (scrolling, dragging the corner, the keys). */
	static final float MIN_SIZE = 0.3f;
	static final float MAX_SIZE = 3f;
	static final float SIZE_STEP = 0.05f;
	/** One notch of the wheel with Shift held. */
	static final float FINE_SIZE_STEP = 0.01f;
	/** How long the size stays in view after the wheel moved it, for an element that is not selected. */
	private static final long SIZE_SHOWN_NANOS = 1_200_000_000L;
	private static final int CHIP_HEIGHT = 14;
	private static final int BAR_HEIGHT = 20;
	/** How long an element picked in the list pulses on screen. */
	private static final long PULSE_NANOS = 1_200_000_000L;

	// the switches of the toolbar, kept while the game runs
	static boolean snapOn = true;
	static boolean gridOn;

	private static boolean open;
	/** The selected element, kept across openings of the menu from the card ("All settings"), so it is still selected when the editor comes back. */
	private static EditableHud selectedNow;

	final List<EditableHud> elements = ModFeatures.editableHuds();
	final HudEditHistory history = new HudEditHistory();
	private final HudEditSnap snap = new HudEditSnap();
	final HudEditToolbar toolbar = new HudEditToolbar(this);
	final HudEditCard card = new HudEditCard(this);
	final HudEditList list = new HudEditList(this);
	final HudEditSimulate simulate = new HudEditSimulate(this);
	final HudEditLook look = new HudEditLook(this);

	EditableHud selected;
	/** The elements selected together, the selected one among them, from two on. */
	final java.util.LinkedHashSet<EditableHud> group = new java.util.LinkedHashSet<>();
	/** A box being dragged on empty space, which selects what it touches. */
	private boolean boxing;
	private double boxFromX;
	private double boxFromY;
	private double boxToX;
	private double boxToY;
	private EditableHud hovered;
	// a press on an element: it becomes a drag (or a resize from the corner) once the mouse moved far enough
	private EditableHud pressed;
	/** The element a click went to, and whether it was selected before: a click on a part of the selected element switches that part. */
	private EditableHud partTarget;
	private boolean pressedSelected;
	/** The element whose inner edge is dragged, and the width it shows while dragged. */
	private EditableHud edgeDragged;
	private String edgeLabel = "";
	private boolean pressedCorner;
	private double pressX;
	private double pressY;
	private EditableHud dragged;
	private boolean resizing;
	private float resizeStartScale;
	private float resizeAnchorX;
	private float resizeGrabX;
	/** The unsnapped position of the dragged element, so it can be pulled off a line again, and where it was last moved to. */
	private float freeX;
	private float freeY;
	private int movedX;
	private int movedY;
	private double lastMouseX;
	private double lastMouseY;
	private boolean snapped;
	private long lastSnapSound;

	private EditableHud pulsed;
	private long pulseUntil;
	private long lastFrame = System.nanoTime();
	private final Spring selectLight = Spring.smooth(Spring.SNAPPY);
	private final Spring hoverLight = Spring.smooth(Spring.SNAPPY);

	// the chip above the selected element, built only when its text changes
	private String chipText = "";
	private EditableHud chipElement;
	private float chipScale = -1f;
	private String chipSnap = "";
	private String note = "";

	public HudEditScreen() {
		super(Component.literal("Edit HUD"));
	}

	/** @return whether the editor is open; the HUD elements then show with sample content where they have none */
	public static boolean isOpen() {
		return open;
	}

	/** @return whether the element is selected or dragged in the editor; elements may draw larger while selected */
	public static boolean isSelected(EditableHud hud) {
		return open && hud != null && selectedNow == hud;
	}

	@Override
	protected void init() {
		Ui.clearCaches();
		open = true;
		if (selected == null && selectedNow != null) {
			selected = selectedNow;
		}
	}

	@Override
	public void removed() {
		if (asked != null) {
			asked.hudPreview(false);
			asked = null;
		}
		history.finish();
		open = false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// nothing here: this would lie over the elements; see dimWorld
	}

	/** Called before the HUD is drawn: the world stays visible, dimmed under the elements, so they stand out and read as they do in the game. */
	public static void dimWorld(GuiGraphicsExtractor graphics) {
		if (open) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), DIM);
		}
	}

	private Ui createUi(GuiGraphicsExtractor graphics) {
		long now = System.nanoTime();
		float delta = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		var menu = ModFeatures.MENU;
		return new Ui(graphics, font, menu.theme().palette(), menu.accentArgb(), delta, menu.animations());
	}

	/** Draws a surface of the editor's own UI (toolbar, card, list, menus) in the menu's look: nearly opaque, with the menu's border. */
	static void surface(Ui ui, int x, int y, int width, int height, float radius, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		ui.shadow(x, y, width, height, radius, 10, 0.4f * alpha);
		ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.palette.popup(), alpha));
		ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(ui.palette.popupBorder(), alpha));
	}

	/** @return whether an element is being dragged or resized; the toolbar fades out meanwhile */
	boolean dragging() {
		return dragged != null;
	}

	// ---------------------------------------------------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (selected != null && !selected.hudShown()) {
			// hidden by the simulation or from the list
			card.close();
			selected = null;
		}
		if (asked != null && selected != asked && asked != pendingAsk) {
			asked.hudPreview(false);
			asked = null;
		}
		if (pendingAsk != null && pendingAsk.hudShown()) {
			pick(pendingAsk);
			pendingAsk = null;
		}
		selectedNow = dragged != null ? dragged : selected;
		Ui ui = createUi(graphics);
		if (gridOn) {
			drawGrid(ui);
		}
		boolean overUi = toolbar.contains(mouseX, mouseY) || card.contains(mouseX, mouseY) || list.contains(mouseX, mouseY)
			|| simulate.contains(mouseX, mouseY) || look.contains(mouseX, mouseY);
		hovered = dragged == null && !overUi ? elementAt(mouseX, mouseY) : null;
		float hover = hoverLight.follow(ui, hovered != null && hovered != selected ? 1f : 0f);
		if (hovered != null && hovered != selected && hover > 0.01f) {
			int[] box = hovered.hudBounds();
			// a thin solid outline that follows the element's shape, fainter than the selection's
			ui.ring(box[0] - 2, box[1] - 2, box[2] + 4, box[3] + 4, hovered.hudRadius() + 2f, 1, Ui.withOpacity(ui.accent, 0.6f * hover));
		}
		drawPulse(ui);
		float lit = selectLight.follow(ui, selected != null ? 1f : 0f);
		if (hasGroup()) {
			drawGroup(ui, lit);
		} else if (selected != null) {
			drawSelection(ui, selected, lit);
		}
		if (boxing) {
			int boxX = (int) Math.min(boxFromX, boxToX);
			int boxY = (int) Math.min(boxFromY, boxToY);
			int boxW = (int) Math.abs(boxToX - boxFromX);
			int boxH = (int) Math.abs(boxToY - boxFromY);
			ui.rounded(boxX, boxY, boxW, boxH, Radius.XS, Ui.withOpacity(ui.accent, BOX_FILL));
			ui.ring(boxX, boxY, boxW, boxH, Radius.XS, 1, ui.accent);
		}
		drawShownSize(ui);
		EditableHud edged = edgeDragged != null ? edgeDragged : selected != null ? selected : hovered;
		if (edged != null && !hasGroup()) {
			drawEdge(ui, edged, mouseX, mouseY);
		}
		EditableHud widened = widthDragged != null ? widthDragged : selected;
		if (widened != null && !hasGroup() && dragged == null && widened.hudShown() && widened.hudResizableWidth()) {
			drawWidthHandles(ui, widened, mouseX, mouseY);
		}
		if (dragged != null && !resizing && snapped) {
			drawSnapNote(ui);
		}
		if (selected != null && !hasGroup() && dragged == null && !overUi) {
			drawPartChip(ui, selected, mouseX, mouseY);
		}
		list.draw(ui, mouseX, mouseY);
		card.draw(ui, mouseX, mouseY);
		toolbar.draw(ui, mouseX, mouseY);
		simulate.draw(ui, mouseX, mouseY);
		look.draw(ui, mouseX, mouseY);
	}

	/**
	 * Draws the grid positions snap to when the grid is on: a faint dot at every position. The dots come from a small tile with many of them,
	 * drawn a few dozen times across the screen: a dot per position every frame (thousands) made the editor stutter.
	 */
	private void drawGrid(Ui ui) {
		int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
		Identifier tile = gridTile(guiScale);
		int side = GRID_TILE * guiScale;
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		// in screen pixels, so every dot is exactly one GUI pixel on the pixel grid
		pose.scale(1f / guiScale, 1f / guiScale);
		int across = width * guiScale;
		int down = height * guiScale;
		for (int x = 0; x < across; x += side) {
			for (int y = 0; y < down; y += side) {
				ui.graphics.blit(RenderPipelines.GUI_TEXTURED, tile, x, y, 0f, 0f, side, side, side, side, side, side, GRID_DOT);
			}
		}
		pose.popMatrix();
	}

	/** The tile of grid dots, made for one GUI scale and made again when it changes. */
	private static Identifier gridTile;
	private static int gridTileScale;

	private static Identifier gridTile(int guiScale) {
		if (gridTile == null || gridTileScale != guiScale) {
			var textures = Minecraft.getInstance().getTextureManager();
			Identifier id = Lumio.id("hud_edit_grid");
			if (gridTile != null) {
				textures.release(id);
			}
			int side = GRID_TILE * guiScale;
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, side, side, false);
			// a white dot, one GUI pixel large, at every grid position inside the tile; the color is given when it is drawn
			for (int x = 0; x < side; x += HudEditSnap.GRID * guiScale) {
				for (int y = 0; y < side; y += HudEditSnap.GRID * guiScale) {
					for (int dx = 0; dx < guiScale; dx++) {
						for (int dy = 0; dy < guiScale; dy++) {
							image.setPixelABGR(x + dx, y + dy, 0xFFFFFFFF);
						}
					}
				}
			}
			textures.register(id, new DynamicTexture(() -> "Lumio HUD editor grid", image));
			gridTile = id;
			gridTileScale = guiScale;
		}
		return gridTile;
	}

	/** Draws the selected element's outline with a soft glow, its chip and its resize handle. */
	private void drawSelection(Ui ui, EditableHud element, float lit) {
		int[] box = element.hudBounds();
		int x = box[0] - 2;
		int y = box[1] - 2;
		int w = box[2] + 4;
		int h = box[3] + 4;
		float radius = element.hudRadius() + 2f;
		int accent = ui.accent;
		// a soft glow outside the outline: a few rings, fainter further out
		for (int i = GLOW_RINGS; i >= 1; i--) {
			int out = 2 * i;
			ui.ring(x - out, y - out, w + 2 * out, h + 2 * out, radius + out, 2, Ui.withOpacity(accent, GLOW_STRENGTH * lit / i));
		}
		ui.ring(x, y, w, h, radius, 2, Ui.withOpacity(accent, lit));
		// the handle at the bottom-right corner
		int handleX = x + w - 1;
		int handleY = y + h - 1;
		ui.circle(handleX, handleY, HANDLE + 1, Ui.withOpacity(0xFFFFFFFF, lit));
		ui.circle(handleX, handleY, HANDLE - 1, Ui.withOpacity(accent, lit));
		drawChip(ui, element, x, y, w, h, lit);
	}

	/** Shows the size of an element the wheel just changed, when it is not the selected one, which has its chip anyway. */
	private void drawShownSize(Ui ui) {
		if (sizeShown == null || sizeShown == selected || !sizeShown.hudShown()) {
			return;
		}
		if (System.nanoTime() > sizeShownUntil) {
			sizeShown = null;
			return;
		}
		int[] box = sizeShown.hudBounds();
		drawChip(ui, sizeShown, box[0], box[1], box[2], box[3], 1f);
	}

	private EditableHud sizeShown;
	private long sizeShownUntil;

	/** Draws the chip above the element: its name and size, or where it is snapped while dragged. */
	private void drawChip(Ui ui, EditableHud element, int x, int y, int w, int h, float lit) {
		String status = dragged == element ? element.hudDragStatus() : "";
		String snapText = !status.isEmpty() ? status : dragged == element && !resizing && snapped ? chipSnap : "";
		if (element != chipElement || element.hudScale() != chipScale || !snapText.equals(chipShown)) {
			chipElement = element;
			chipScale = element.hudScale();
			chipShown = snapText;
			String note = element.hudSizeNote();
			chipText = element.hudName() + "  ·  " + (!snapText.isEmpty() ? snapText : note != null ? note : sizeText(chipScale));
		}
		int chipWidth = ui.width(chipText) + 16;
		int chipX = Mth.clamp(x + w / 2 - chipWidth / 2, 4, width - chipWidth - 4);
		boolean above = y >= CHIP_HEIGHT + 10;
		int chipY = above ? y - CHIP_HEIGHT - 6 : y + h + 6;
		ui.rounded(chipX, chipY, chipWidth, CHIP_HEIGHT, Radius.pill(CHIP_HEIGHT), Ui.withOpacity(ui.accent, lit));
		ui.textMiddle(chipText, chipX + 8, chipY + CHIP_HEIGHT / 2, Ui.withOpacity(0xFFFFFFFF, lit));
	}

	private String chipShown = "";

	/** @return a size as the editor shows it: "1.10×" */
	static String sizeText(float scale) {
		return String.format(Locale.ROOT, "%.2f×", scale);
	}

	/** Draws a soft pulse around the element picked in the list, so it is found on screen. */
	private void drawPulse(Ui ui) {
		if (pulsed == null) {
			return;
		}
		long left = pulseUntil - System.nanoTime();
		if (left <= 0L || !pulsed.hudShown()) {
			pulsed = null;
			return;
		}
		float fade = left / (float) PULSE_NANOS;
		float wave = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1.2e8);
		int[] box = pulsed.hudBounds();
		ui.rounded(box[0] - 4, box[1] - 4, box[2] + 8, box[3] + 8, pulsed.hudRadius() + 4f, Ui.withOpacity(ui.accent, 0.25f * fade * wave));
	}

	/** The width handles at the selected element's left and right edge: their size, how near the mouse must be, and their label. */
	private static final float WIDTH_HANDLE = 3f;
	private static final float WIDTH_HANDLE_HEIGHT = 10f;
	private static final float WIDTH_REACH = 5f;
	/** The element whose width is dragged, at which edge, and the width it shows meanwhile. */
	private EditableHud widthDragged;
	private boolean widthRight;
	private String widthLabel = "";

	/** @return 1 for the selected element's right width handle under the mouse, -1 for its left one, 0 for none */
	private int widthHandleAt(double mouseX, double mouseY) {
		if (selected == null || !selected.hudShown() || !selected.hudResizableWidth()) {
			return 0;
		}
		int[] box = selected.hudBounds();
		float middle = box[1] + box[3] / 2f;
		if (Math.abs(mouseY - middle) > Math.max(WIDTH_HANDLE_HEIGHT, box[3]) / 2f + 2f) {
			return 0;
		}
		if (selected.hudWidthEdge(true) && Math.abs(mouseX - (box[0] + box[2])) <= WIDTH_REACH) {
			return 1;
		}
		return selected.hudWidthEdge(false) && Math.abs(mouseX - box[0]) <= WIDTH_REACH ? -1 : 0;
	}

	/** Draws the small handles at the left and right edge of the element, brighter under the mouse, and the width while one is dragged. */
	private void drawWidthHandles(Ui ui, EditableHud element, int mouseX, int mouseY) {
		int[] box = element.hudBounds();
		float middle = box[1] + box[3] / 2f;
		int hot = widthDragged == element ? (widthRight ? 1 : -1) : selected == element ? widthHandleAt(mouseX, mouseY) : 0;
		for (int side = -1; side <= 1; side += 2) {
			if (!element.hudWidthEdge(side > 0)) {
				continue;
			}
			float edge = side > 0 ? box[0] + box[2] + 2f : box[0] - 2f;
			boolean lit = hot == side;
			ui.roundedSmooth(edge - WIDTH_HANDLE / 2f - 0.5f, middle - WIDTH_HANDLE_HEIGHT / 2f - 0.5f, WIDTH_HANDLE + 1f, WIDTH_HANDLE_HEIGHT + 1f,
				(WIDTH_HANDLE + 1f) / 2f, ui.accent);
			ui.roundedSmooth(edge - WIDTH_HANDLE / 2f, middle - WIDTH_HANDLE_HEIGHT / 2f, WIDTH_HANDLE, WIDTH_HANDLE_HEIGHT, WIDTH_HANDLE / 2f,
				lit ? 0xFFFFFFFF : Ui.mix(ui.accent, 0xFFFFFFFF, 0.55f));
		}
		if (widthDragged == element && !widthLabel.isEmpty()) {
			float edge = widthRight ? box[0] + box[2] : box[0];
			int labelWidth = ui.width(widthLabel) + 2 * EDGE_LABEL_PAD;
			int left = Mth.clamp(Math.round(edge - labelWidth / 2f), 2, width - labelWidth - 2);
			int top = box[1] + box[3] + EDGE_INSET + 2;
			ui.rounded(left, top, labelWidth, EDGE_LABEL_HEIGHT, EDGE_LABEL_HEIGHT / 2f, Ui.withOpacity(ui.accent, 0.95f));
			ui.textCentered(widthLabel, left + labelWidth / 2, top + EDGE_LABEL_HEIGHT / 2, 0xFFFFFFFF);
		}
	}

	/** The chip over a hidden part of the selected element under the mouse: its height and the gap above the part. */
	private static final int PART_CHIP_HEIGHT = 14;
	private static final int PART_CHIP_GAP = 4;
	private static final String PART_CHIP = "Click to show";

	/** Draws a small "Click to show" chip above a hidden part of the selected element while the mouse is on it. No outline: the part is faded. */
	private void drawPartChip(Ui ui, EditableHud element, int mouseX, int mouseY) {
		HudParts parts = element.hudParts();
		if (parts == null) {
			return;
		}
		int index = parts.at(mouseX, mouseY);
		if (index < 0 || parts.setting(index) == null || parts.setting(index).get()) {
			return;
		}
		float[] box = parts.box(index);
		int chipWidth = ui.width(PART_CHIP) + 12;
		int chipX = Mth.clamp(Math.round(box[0] + box[2] / 2f - chipWidth / 2f), 4, width - chipWidth - 4);
		int chipY = Math.round(box[1]) - PART_CHIP_HEIGHT - PART_CHIP_GAP;
		if (chipY < 4) {
			chipY = Math.round(box[1] + box[3]) + PART_CHIP_GAP;
		}
		ui.rounded(chipX, chipY, chipWidth, PART_CHIP_HEIGHT, Radius.pill(PART_CHIP_HEIGHT), ui.accent);
		ui.textCentered(PART_CHIP, chipX + chipWidth / 2, chipY + PART_CHIP_HEIGHT / 2, 0xFFFFFFFF);
	}

	/** Switches the part of an element under the mouse, as one undo step. */
	private void togglePart(EditableHud element, double mouseX, double mouseY) {
		HudParts parts = element.hudParts();
		if (parts == null) {
			return;
		}
		int index = parts.at(mouseX, mouseY);
		if (index < 0) {
			return;
		}
		Feature feature = featureOf(element);
		if (feature != null) {
			history.begin(feature);
		}
		var setting = parts.setting(index);
		if (setting == null) {
			parts.pick(index);
			UiSounds.click();
		} else {
			setting.set(!setting.get());
			UiSounds.toggle(setting.get());
		}
		history.finish();
		FeatureManager.saveSoon();
	}

	/** Draws the note about what the dragged element is snapped to, at the bottom of the screen. The editor draws no lines. */
	private void drawSnapNote(Ui ui) {
		if (!note.isEmpty()) {
			int noteWidth = Math.min(ui.width(note) + 20, width - 16);
			int noteX = (width - noteWidth) / 2;
			int noteY = height - BAR_HEIGHT - 8;
			surface(ui, noteX, noteY, noteWidth, BAR_HEIGHT, Radius.pill(BAR_HEIGHT), 1f);
			ui.textMiddle(ui.fit(note, noteWidth - 20), noteX + 10, noteY + BAR_HEIGHT / 2, ui.palette.text());
		}
	}

	// -------------------------------------------------------------------------------------------------------- elements

	/** @return the shown element under the mouse, the smallest one where several overlap, or null */
	EditableHud elementAt(double mouseX, double mouseY) {
		EditableHud found = null;
		long smallest = Long.MAX_VALUE;
		for (int i = 0; i < elements.size(); i++) {
			EditableHud element = elements.get(i);
			if (!element.hudShown()) {
				continue;
			}
			int[] box = element.hudBounds();
			if (mouseX >= box[0] - 2 && mouseX < box[0] + box[2] + 2 && mouseY >= box[1] - 2 && mouseY < box[1] + box[3] + 2) {
				long area = (long) box[2] * box[3];
				if (area < smallest) {
					smallest = area;
					found = element;
				}
			}
		}
		return found;
	}

	/** @return the element whose inner edge is under the mouse (the selected one first), or null */
	private EditableHud edgeAt(double mouseX, double mouseY) {
		EditableHud[] candidates = {selected, elementAt(mouseX, mouseY)};
		for (EditableHud element : candidates) {
			if (element == null || !element.hudShown()) {
				continue;
			}
			float edge = element.hudEdgeX();
			int[] box = element.hudBounds();
			if (!Float.isNaN(edge) && Math.abs(mouseX - edge) <= EDGE_REACH && mouseY >= box[1] && mouseY < box[1] + box[3]) {
				return element;
			}
		}
		return null;
	}

	/** Draws the grip of an element's inner edge, brighter when the mouse is on it, with the width above it while it is dragged. */
	private void drawEdge(Ui ui, EditableHud element, int mouseX, int mouseY) {
		float edge = element.hudEdgeX();
		if (Float.isNaN(edge)) {
			return;
		}
		int[] box = element.hudBounds();
		boolean hot = edgeDragged == element || Math.abs(mouseX - edge) <= EDGE_REACH && mouseY >= box[1] && mouseY < box[1] + box[3];
		ui.roundedSmooth(edge - EDGE_GRIP / 2f, box[1] + EDGE_INSET, EDGE_GRIP, Math.max(EDGE_GRIP, box[3] - 2 * EDGE_INSET), EDGE_GRIP / 2f,
			Ui.withOpacity(hot ? 0xFFFFFFFF : ui.accent, hot ? 1f : 0.6f));
		if (edgeDragged == element && !edgeLabel.isEmpty()) {
			int width = ui.width(edgeLabel) + 2 * EDGE_LABEL_PAD;
			int left = Math.round(edge - width / 2f);
			int top = box[1] + box[3] + EDGE_INSET;
			ui.rounded(left, top, width, EDGE_LABEL_HEIGHT, EDGE_LABEL_HEIGHT / 2f, Ui.withOpacity(ui.accent, 0.95f));
			ui.textCentered(edgeLabel, Math.round(edge), top + EDGE_LABEL_HEIGHT / 2, 0xFFFFFFFF);
		}
	}

	/** @return whether the mouse is on the selected element's resize handle */
	private boolean onHandle(double mouseX, double mouseY) {
		if (selected == null) {
			return false;
		}
		int[] box = selected.hudBounds();
		return Math.hypot(mouseX - (box[0] + box[2] + 1), mouseY - (box[1] + box[3] + 1)) <= HANDLE_REACH;
	}

	/** @return the feature of an element, for undo, or null */
	static Feature featureOf(EditableHud element) {
		return element == null ? null : element.hudFeature();
	}

	/** @return whether several elements are selected together */
	boolean hasGroup() {
		return group.size() > 1;
	}

	/** @return whether an element is selected, alone or together with others */
	boolean isPicked(EditableHud element) {
		return element == selected || group.contains(element);
	}

	/** Adds an element to the selection or takes it out. */
	void toggleInGroup(EditableHud element) {
		if (group.isEmpty() && selected != null) {
			group.add(selected);
		}
		if (!group.remove(element)) {
			group.add(element);
			selected = element;
		} else if (selected == element) {
			selected = group.isEmpty() ? null : group.getLast();
		}
		if (group.size() == 1) {
			selected = group.getFirst();
			group.clear();
		}
		card.close();
		UiSounds.click();
	}

	/** @return the features of the selected elements, as one step of the history */
	Feature[] groupFeatures() {
		java.util.Set<Feature> features = new java.util.LinkedHashSet<>();
		for (EditableHud element : group) {
			if (featureOf(element) != null) {
				features.add(featureOf(element));
			}
		}
		return features.toArray(new Feature[0]);
	}

	/** @return the box around all the selected elements */
	int[] groupBounds() {
		int left = Integer.MAX_VALUE;
		int top = Integer.MAX_VALUE;
		int right = Integer.MIN_VALUE;
		int bottom = Integer.MIN_VALUE;
		for (EditableHud element : group) {
			int[] box = element.hudBounds();
			left = Math.min(left, box[0]);
			top = Math.min(top, box[1]);
			right = Math.max(right, box[0] + box[2]);
			bottom = Math.max(bottom, box[1] + box[3]);
		}
		return new int[]{left, top, right - left, bottom - top};
	}

	/** Selects what the box touches, as well as what was selected with the key held. */
	private void finishBox() {
		boxing = false;
		int left = (int) Math.min(boxFromX, boxToX);
		int right = (int) Math.max(boxFromX, boxToX);
		int top = (int) Math.min(boxFromY, boxToY);
		int bottom = (int) Math.max(boxFromY, boxToY);
		if (right - left < DRAG_THRESHOLD && bottom - top < DRAG_THRESHOLD) {
			return;
		}
		for (EditableHud element : elements) {
			if (!element.hudShown() || !element.hudMovable()) {
				continue;
			}
			int[] box = element.hudBounds();
			if (box[0] < right && box[0] + box[2] > left && box[1] < bottom && box[1] + box[3] > top) {
				group.add(element);
				selected = element;
			}
		}
		if (group.size() == 1) {
			selected = group.getFirst();
			group.clear();
		}
	}

	/** The outline of each selected element, and a chip with how many they are. */
	private void drawGroup(Ui ui, float lit) {
		for (EditableHud element : group) {
			if (element.hudShown()) {
				int[] box = element.hudBounds();
				ui.ring(box[0] - 2, box[1] - 2, box[2] + 4, box[3] + 4, element.hudRadius() + 2f, 2, Ui.withOpacity(ui.accent, lit));
			}
		}
		int[] all = groupBounds();
		ui.ring(all[0] - 6, all[1] - 6, all[2] + 12, all[3] + 12, 8f, 1, Ui.withOpacity(ui.accent, 0.5f * lit));
		String text = group.size() + " elements";
		int chipWidth = ui.width(text) + 12;
		int chipX = Mth.clamp(all[0] + (all[2] - chipWidth) / 2, 2, Math.max(2, width - chipWidth - 2));
		int chipY = all[1] - 22 < 2 ? all[1] + all[3] + 8 : all[1] - 22;
		ui.rounded(chipX, chipY, chipWidth, 14, Radius.pill(14), Ui.withOpacity(ui.accent, lit));
		ui.textMiddle(text, chipX + 6, chipY + 7, 0xFFFFFFFF);
	}

	/** Selects an element and closes the card of another. */
	void select(EditableHud element) {
		if (selected != element) {
			card.close();
		}
		selected = element;
		if (element == null || !group.contains(element)) {
			group.clear();
		}
	}

	/** The element that shows its sample because the list asked for it while it is not active here, until something else is selected. */
	private EditableHud asked;
	private EditableHud pendingAsk;

	/** Asks for an element that is not active here: its sample shows for placing it, or, when only an activity has one, the Simulate menu opens. */
	void askFor(EditableHud element) {
		if (!element.hudPreviewable()) {
			toolbar.openSimulate();
			return;
		}
		if (asked != null) {
			asked.hudPreview(false);
		}
		asked = element;
		pendingAsk = element;
		element.hudPreview(true);
	}

	void pick(EditableHud element) {
		select(element);
		pulsed = element;
		pulseUntil = System.nanoTime() + PULSE_NANOS;
	}

	/** @return the size an element may have: within its own limits and the editor's range */
	static float clampSize(EditableHud element, float size) {
		return Mth.clamp(size, Math.max(MIN_SIZE, element.hudMinScale()), Math.min(MAX_SIZE, element.hudMaxScale()));
	}

	/** Sets an element's size as one undo step. */
	void setSize(EditableHud element, float size) {
		Feature feature = featureOf(element);
		if (feature != null) {
			history.begin(feature);
		}
		element.hudSetScale(clampSize(element, size));
		history.finish();
		FeatureManager.saveSoon();
	}

	/** Resizes by a wheel notch or a key, around the element's anchor: one size step, a small one with Shift held; the new size shows for a moment. */
	private void resizeBy(EditableHud element, int direction) {
		float step = minecraft.hasShiftDown() ? FINE_SIZE_STEP : SIZE_STEP;
		float next = clampSize(element, Math.round(element.hudScale() / step + direction) * step);
		Feature feature = featureOf(element);
		if (feature != null) {
			history.begin(feature);
		}
		element.hudSetScaleAnchored(next);
		history.finish();
		FeatureManager.saveSoon();
		sizeShown = element;
		sizeShownUntil = System.nanoTime() + SIZE_SHOWN_NANOS;
	}

	/** Puts every element back to its place, size and look, as one undo step; asked first. */
	void resetAll() {
		Confirm.ask("Reset the HUD layout?", "Every element goes back to its place, size and look.", "Reset all", "Cancel", () -> {
			Feature[] features = elements.stream().map(EditableHud::hudFeature).filter(java.util.Objects::nonNull).distinct().toArray(Feature[]::new);
			history.begin(features);
			for (EditableHud element : elements) {
				element.hudReset();
				element.hudResetStyle();
			}
			history.finish();
			FeatureManager.saveSoon();
		});
	}

	/** Opens the menu at an element's feature with its settings pane; closing the menu comes back here with the element still selected. */
	void openAllSettings(EditableHud element) {
		Feature feature = featureOf(element);
		if (feature == null) {
			return;
		}
		selectedNow = element;
		FeatureManager.save();
		MacScreen.openFrom(this);
		MacScreen.reveal(feature.isChild() ? feature.parent() : feature, null);
	}

	/** Opens the settings of an element in the menu scrolled to one setting, such as the info bar's segment list. */
	void openSetting(EditableHud element, Setting<?> setting) {
		Feature feature = featureOf(element);
		if (feature == null) {
			return;
		}
		selectedNow = element;
		FeatureManager.save();
		MacScreen.openFrom(this);
		MacScreen.reveal(feature.isChild() ? feature.parent() : feature, setting);
	}

	/** Shows or hides an element (its feature), as one undo step. */
	void toggleShown(EditableHud element) {
		Feature feature = featureOf(element);
		if (feature == null) {
			return;
		}
		history.begin(feature);
		boolean now = feature.toggle();
		history.finish();
		UiSounds.toggle(now);
		FeatureManager.saveSoon();
	}

	void undo() {
		history.undo();
		UiSounds.click();
	}

	void redo() {
		history.redo();
		UiSounds.click();
	}

	// ------------------------------------------------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = ScreenInput.button(event);
		// the open menus and panels come first, top to bottom
		if (look.isOpen()) {
			if (look.click(mouseX, mouseY, button)) {
				return true;
			}
			look.close();
		}
		if (simulate.isOpen()) {
			if (simulate.click(mouseX, mouseY)) {
				return true;
			}
			simulate.close();
		}
		if (toolbar.click(mouseX, mouseY, button)) {
			return true;
		}
		if (card.click(mouseX, mouseY, button)) {
			return true;
		}
		if (list.click(mouseX, mouseY, button)) {
			return true;
		}
		boolean adding = button == 0 && ((ScreenInput.modifiers(event.modifiers()) & (ScreenInput.SHIFT | ScreenInput.CONTROL)) != 0 || minecraft.hasControlDown() || minecraft.hasShiftDown());
		if (adding) {
			EditableHud clicked = elementAt(mouseX, mouseY);
			if (clicked != null) {
				toggleInGroup(clicked);
				return true;
			}
			boxing = true;
			boxFromX = boxToX = mouseX;
			boxFromY = boxToY = mouseY;
			if (group.isEmpty() && selected != null) {
				group.add(selected);
			}
			return true;
		}
		int handle = button == 0 && !hasGroup() ? widthHandleAt(mouseX, mouseY) : 0;
		if (handle != 0) {
			Feature feature = featureOf(selected);
			if (feature != null) {
				history.begin(feature);
			}
			if (doubleClick) {
				// a double-click on an edge puts the width back to its default
				selected.hudResetWidth(handle > 0);
				history.finish();
				FeatureManager.saveSoon();
				UiSounds.click();
				return true;
			}
			widthDragged = selected;
			widthRight = handle > 0;
			widthLabel = "";
			selected.hudWidthStart(widthRight);
			return true;
		}
		EditableHud edged = button == 0 && !hasGroup() ? edgeAt(mouseX, mouseY) : null;
		if (edged != null) {
			// the edge of a part sets its width (snapping to its presets, free with Shift)
			select(edged);
			edgeDragged = edged;
			edgeLabel = "";
			Feature feature = featureOf(edged);
			if (feature != null) {
				history.begin(feature);
			}
			return true;
		}
		EditableHud element = button == 0 && !hasGroup() && onHandle(mouseX, mouseY) ? selected : elementAt(mouseX, mouseY);
		if (button == 1 || button == 0 && doubleClick) {
			// right-click or double-click opens the element's card, or the shared one of a selection it is in
			if (element != null && group.contains(element) && hasGroup()) {
				card.openGroup(element);
			} else if (element != null) {
				select(element);
				card.open(element);
			} else {
				card.close();
			}
			return true;
		}
		if (button != 0) {
			return true;
		}
		if (element == null) {
			// empty space closes the card and lets go of the selection; a drag from there selects what its box touches
			card.close();
			select(null);
			boxing = true;
			boxFromX = boxToX = mouseX;
			boxFromY = boxToY = mouseY;
			return true;
		}
		pressedSelected = selected == element && !hasGroup();
		partTarget = hasGroup() ? null : element;
		select(element);
		pressed = element.hudMovable() || onHandle(mouseX, mouseY) ? element : null;
		pressedCorner = !hasGroup() && onHandle(mouseX, mouseY);
		pressX = mouseX;
		pressY = mouseY;
		return true;
	}

	/** Starts dragging or resizing the pressed element, once the mouse moved far enough. */
	private void startDrag(double mouseX, double mouseY) {
		dragged = pressed;
		resizing = pressedCorner;
		if (hasGroup()) {
			startGroupDrag();
			return;
		}
		Feature feature = featureOf(dragged);
		if (feature != null) {
			history.begin(feature);
		}
		int[] box = dragged.hudBounds();
		if (resizing) {
			resizeStartScale = dragged.hudScale();
			resizeAnchorX = dragged.hudAnchorX(box);
			resizeGrabX = (float) pressX;
		} else {
			dragged.hudDragStart();
			freeX = box[0];
			freeY = box[1];
			movedX = box[0];
			movedY = box[1];
			snap.start(elements, dragged, width, height);
		}
		lastMouseX = pressX;
		lastMouseY = pressY;
		snapped = false;
		card.follow();
	}

	/** Moves the selected elements together, only as far as each of them can go, so they keep their places to each other. @return how far they moved */
	private int[] moveGroup(int dx, int dy) {
		int[] move = {dx, dy};
		for (EditableHud element : group) {
			element.hudLimitMove(move);
		}
		for (EditableHud element : group) {
			element.hudMove(move[0], move[1]);
		}
		return move;
	}

	private int[] groupDragBox;

	/** Starts moving the selected elements together, as one step of the history; they snap by their outer edges. */
	private void startGroupDrag() {
		history.begin(groupFeatures());
		for (EditableHud element : group) {
			element.hudDragStart();
		}
		int[] all = groupBounds();
		groupDragBox = all;
		freeX = all[0];
		freeY = all[1];
		movedX = all[0];
		movedY = all[1];
		snap.start(elements, group, width, height);
		lastMouseX = pressX;
		lastMouseY = pressY;
		snapped = false;
		card.follow();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (look.drag(mouseX, mouseY) || toolbar.drag(mouseX, mouseY) || card.drag(mouseX, mouseY) || list.drag(mouseX, mouseY)) {
			return true;
		}
		if (widthDragged != null) {
			widthLabel = widthDragged.hudDragWidth(widthRight, (float) mouseX, (ScreenInput.modifiers(event.modifiers()) & ScreenInput.SHIFT) != 0);
			return true;
		}
		if (edgeDragged != null) {
			edgeLabel = edgeDragged.hudDragEdge((float) mouseX, (ScreenInput.modifiers(event.modifiers()) & ScreenInput.SHIFT) != 0);
			return true;
		}
		if (boxing) {
			boxToX = mouseX;
			boxToY = mouseY;
			return true;
		}
		if (dragged == null && pressed != null && Math.hypot(mouseX - pressX, mouseY - pressY) > DRAG_THRESHOLD) {
			startDrag(mouseX, mouseY);
		}
		if (dragged == null) {
			return true;
		}
		if (resizing) {
			// the size follows the mouse's distance from the fixed edge, relative to that distance when the corner was grabbed
			float start = Math.max(24f, Math.abs(resizeGrabX - resizeAnchorX));
			float now = Math.max(1f, (float) mouseX - resizeAnchorX);
			float wanted = resizeStartScale * now / start;
			wanted = Math.round(wanted / SIZE_STEP) * SIZE_STEP;
			wanted = clampSize(dragged, wanted);
			if (wanted != dragged.hudScale()) {
				dragged.hudSetScale(wanted);
			}
			return true;
		}
		freeX += (float) (mouseX - lastMouseX);
		freeY += (float) (mouseY - lastMouseY);
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		// the group keeps its size from the start of the drag: an element held at the edge of the screen must not make it look smaller, so that the others would go on
		int[] box = hasGroup() ? groupDragBox : dragged.hudBounds();
		boolean free = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.SHIFT) != 0 || !snapOn || !dragged.hudSnaps();
		boolean nowSnapped = snap.snap(Math.round(freeX), Math.round(freeY), box[2], box[3], free, gridOn && !free, width, height);
		if (nowSnapped && (snap.held(0) != heldBefore[0] || snap.held(1) != heldBefore[1])) {
			snapFeedback();
			describeSnap();
		}
		heldBefore[0] = snap.held(0);
		heldBefore[1] = snap.held(1);
		snapped = nowSnapped;
		int x = snap.snapped[0];
		int y = snap.snapped[1];
		if (hasGroup()) {
			int[] moved = moveGroup(x - movedX, y - movedY);
			movedX += moved[0];
			movedY += moved[1];
			return true;
		}
		dragged.hudMove(x - movedX, y - movedY);
		movedX = x;
		movedY = y;
		return true;
	}

	private final int[] heldBefore = {-1, -1};

	/** Builds the chip and the note for the current snap, once per change: "centered", "Snapped to the screen center and the top edge". */
	private void describeSnap() {
		String across = snap.describe(0);
		String down = snap.describe(1);
		chipSnap = snap.centered(0) && snap.centered(1) ? "centered" : snap.centered(0) ? "centered across" : snap.centered(1) ? "centered down"
			: across != null ? "at " + across : down != null ? "at " + down : "";
		String both = across != null && down != null ? (across.equals(down) ? across : across + " and " + down) : across != null ? across : down;
		note = both == null ? "" : "Snapped to " + both + "  ·  Shift without snapping";
	}

	/** Plays a soft tick when the element snaps to a line, at most a few times per second while sliding along many. */
	private void snapFeedback() {
		long now = System.currentTimeMillis();
		if (now - lastSnapSound > 90) {
			lastSnapSound = now;
			UiSounds.play(UiSounds.Cue.TICK);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		look.release(event.x(), event.y());
		toolbar.release();
		card.release();
		list.release();
		if (widthDragged != null) {
			history.finish();
			FeatureManager.saveSoon();
			widthDragged = null;
		}
		if (edgeDragged != null) {
			history.finish();
			FeatureManager.saveSoon();
			edgeDragged = null;
		}
		if (dragged == null && partTarget != null && pressedSelected && ScreenInput.button(event) == 0) {
			togglePart(partTarget, event.x(), event.y());
		}
		partTarget = null;
		if (boxing) {
			finishBox();
		}
		if (dragged != null) {
			if (hasGroup()) {
				for (EditableHud element : group) {
					element.hudDragEnd();
					element.hudAnchorNearest();
				}
			} else if (!resizing) {
				dragged.hudDragEnd();
				// placed from the nearest screen anchor, so it stays right when the screen size changes
				dragged.hudAnchorNearest();
			}
			history.finish();
			FeatureManager.saveSoon();
			dragged = null;
			snapped = false;
			card.follow();
		}
		pressed = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0) {
			return false;
		}
		if (list.scroll(mouseX, mouseY, scrollY) || simulate.contains(mouseX, mouseY) || look.contains(mouseX, mouseY) || card.contains(mouseX, mouseY)) {
			return true;
		}
		EditableHud target = dragged != null ? dragged : elementAt(mouseX, mouseY);
		if (target != null) {
			resizeBy(target, scrollY > 0.0 ? 1 : -1);
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (list.charTyped(event.codepoint())) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean control = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.CONTROL) != 0 || minecraft.hasControlDown();
		boolean shift = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.SHIFT) != 0;
		if (list.keyPressed(key, ScreenInput.modifiers(event.modifiers()))) {
			return true;
		}
		if (control && (ScreenInput.letter(key, 'Y') || ScreenInput.letter(key, 'Z') && shift)) {
			redo();
			return true;
		}
		if (control && ScreenInput.letter(key, 'Z')) {
			undo();
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			// the open card, menu or panel first; with nothing open, the editor closes like Done
			if (look.isOpen()) {
				look.close();
			} else if (simulate.isOpen()) {
				simulate.close();
			} else if (card.isOpen()) {
				card.close();
			} else if (hasGroup()) {
				group.clear();
			} else if (list.isOpen()) {
				list.close();
			} else {
				onClose();
			}
			return true;
		}
		if (hasGroup()) {
			int step = shift ? 10 : 1;
			int dx = key == InputConstants.KEY_LEFT ? -step : key == InputConstants.KEY_RIGHT ? step : 0;
			int dy = key == InputConstants.KEY_UP ? -step : key == InputConstants.KEY_DOWN ? step : 0;
			if (dx != 0 || dy != 0) {
				int[] all = groupBounds();
				dx = Mth.clamp(all[0] + dx, 0, Math.max(0, width - all[2])) - all[0];
				dy = Mth.clamp(all[1] + dy, 0, Math.max(0, height - all[3])) - all[1];
				history.begin(groupFeatures());
				moveGroup(dx, dy);
				history.finish();
				FeatureManager.saveSoon();
				return true;
			}
		} else if (selected != null && selected.hudShown() && selected.hudMovable()) {
			int step = shift ? 10 : 1;
			int dx = key == InputConstants.KEY_LEFT ? -step : key == InputConstants.KEY_RIGHT ? step : 0;
			int dy = key == InputConstants.KEY_UP ? -step : key == InputConstants.KEY_DOWN ? step : 0;
			if (dx != 0 || dy != 0) {
				int[] box = selected.hudBounds();
				// elements stay fully on screen
				dx = Mth.clamp(box[0] + dx, 0, Math.max(0, width - box[2])) - box[0];
				dy = Mth.clamp(box[1] + dy, 0, Math.max(0, height - box[3])) - box[1];
				Feature feature = featureOf(selected);
				if (feature != null) {
					history.begin(feature);
				}
				selected.hudMove(dx, dy);
				history.finish();
				FeatureManager.saveSoon();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	/** Done: saves and closes, back to the menu; the simulation ends with the editor. */
	@Override
	public void onClose() {
		history.finish();
		FeatureManager.save();
		selectedNow = null;
		Minecraft.getInstance().setScreenAndShow(new MacScreen());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** @return the simulated activity's chip text, or empty when nothing is simulated */
	static String simulationText() {
		return HudSimulation.running() == null ? "" : "Simulating: " + HudSimulation.describe();
	}
}
