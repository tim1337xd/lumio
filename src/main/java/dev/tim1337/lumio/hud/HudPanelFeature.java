package dev.tim1337.lumio.hud;

import dev.tim1337.lumio.render.WorldBackdrop;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.features.visual.hud.HudStack;
import dev.tim1337.lumio.ui.ScreenTitles;
import dev.tim1337.lumio.ui.Eased;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudParts;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.PanelDepth;
import dev.tim1337.lumio.ui.PanelTheme;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.WidthSnap;


import com.google.gson.JsonObject;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Base class of HUD panels in the style of the info bar: a glass surface with content on it. It knows its position (a distance from one of the
 * screen edges), size and appearance, and can be moved and resized in the HUD editor. The content is drawn by the subclass.
 */
public abstract class HudPanelFeature extends Feature implements EditableHud {
	public enum Anchor {
		LEFT, CENTER, RIGHT
	}

	private final String hudName;
	private final float defaultX;
	private final float defaultY;
	// where the panel is placed from: 0 left or top, 1 center, 2 right or bottom (hidden settings, part of the HUD layout); it starts at the edge
	// the panel was made for, and the HUD editor moves it to the nearest one when the panel is dropped
	private final FloatSetting anchorX;
	private final FloatSetting anchorY;
	private static final int START = 0;
	private static final int MIDDLE = 1;
	private static final int END = 2;

	/** Where the panel's look comes from: the global HUD theme, or its own settings below. */
	private final EnumSetting<PanelLook.Mode> lookMode = add(new EnumSetting<>("look_mode", Component.literal("Look"), PanelLook.Mode.THEME, PanelLook.Mode::displayName,
		EnumSetting.Style.SEGMENTED), "Panel");
	private final PanelLook look = new PanelLook(lookMode);

	protected final ColorSetting accent = add(new ColorSetting("accent", Component.literal("Accent"), SystemColors.BLUE_RGB).menuByDefault(), "Panel");
	protected final FloatSetting scale = add(new FloatSetting("scale", Component.literal("Size"), 1f, 0.3f, 3f, 2).percent(), "Panel");
	/** Shared default width of the SkyBlock cards (profit, commissions, pity, pests, visitors, farming), so they line up when stacked. */
	protected static final int SKYBLOCK_WIDTH = 200;

	/** Whether the panel draws its glass background; when off, only the content is drawn over the game. Only panels that offer it show the switch. */
	protected final BooleanSetting background = add(new BooleanSetting("background", Component.literal("Background"), true), "Panel");
		// below the background switch: the glow disappears with the background, and the background switch stays under the mouse to turn it back on
	protected final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Glow"), false), "Panel");
	/** Whether the glow is the soft one. */
	private final BooleanSetting glowSoft = add(new BooleanSetting("glow_soft", Component.literal("Soft glow"), false), "Panel");
	private final EnumSetting<PanelLook.Glow> glowRow = add(PanelLook.glowRow("glow_row", glow, glowSoft), "Panel");
	/** The panel's theme: the HUD theme, or its own glass, dark or light. Also selectable from the panel's menu in the HUD editor. */
	protected final EnumSetting<PanelTheme> panelTheme = add(new EnumSetting<>("panel_theme",
		Component.literal("Theme"), PanelTheme.HUD, PanelTheme::displayName, EnumSetting.Style.SEGMENTED), "Panel");
	/** How much the game shows through the panel's background, from 0 to 80 %; set in its card in the HUD editor. */
	protected final FloatSetting seeThrough = add(new FloatSetting("see_through", Component.literal("See-through"), 0f, 0f, 0.8f, 2).percent().step(0.05f),
		"Panel");
	/** The panel's depth effect: as set in the menu settings, or on or off for this panel; also chosen in its card in the HUD editor. */
	protected final EnumSetting<PanelDepth> panelDepth = add(new EnumSetting<>("panel_depth",
		Component.literal("Depth effect"), PanelDepth.MENU, PanelDepth::displayName, EnumSetting.Style.SEGMENTED), "Panel");
	/** Whether the panel shows the background switch: all panels do, except elements without a panel of their own. */
	private boolean offersNoBackground = true;

	/** Cached icon stacks for panel headers: creating a new stack every frame was measurable overhead. */
	private static final Map<Item, ItemStack> ICONS = new IdentityHashMap<>();

	/** @return a cached stack of the item, to draw as an icon */
	protected static ItemStack itemIcon(Item item) {
		return ICONS.computeIfAbsent(item, ItemStack::new);
	}

	/** The panel appearance settings (size, background, theme) come after the panel's own settings. */
	@Override
	public Set<String> trailingSections() {
		return Set.of("Panel");
	}

	protected void offerNoBackground() {
		offersNoBackground = true;
	}

	/** Places the panel by default from the middle of the screen's height, as the game places its sidebar scoreboard. */
	protected final void placeCenteredVertically() {
		anchorY.setDefault((float) MIDDLE);
		anchorY.reset();
	}

	/** @return whether the glass background is drawn */
	protected boolean hasBackground() {
		return !offersNoBackground || background.get();
	}

	/** Corner radius of the glass when it was last drawn, in panel pixels. */
	private float lastRadius = 12f;
	/** Distance from the anchored side edge and from the top or bottom edge, set in the HUD editor. */
	private final FloatSetting x;
	private final FloatSetting y;

	private int[] bounds = {0, 0, 0, 0};
	/** The parts a click in the HUD editor switches, as drawn this frame. */
	private final HudParts parts = new HudParts();
	private final Eased shownScale = new Eased();
	private int lastGuiWidth = 854;
	private int lastGuiHeight = 480;

	protected HudPanelFeature(String id, String name, String description, boolean enabledByDefault, Anchor anchor, boolean fromBottom, float defaultX, float defaultY) {
		super(id, name, description, enabledByDefault);
		this.hudName = name;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
		x = add(new FloatSetting("x", Component.literal("Place across"), defaultX, -4000f, 4000f, 0), "Panel");
		y = add(new FloatSetting("y", Component.literal("Place down"), defaultY, -4000f, 8000f, 0), "Panel");
		anchorX = add(new FloatSetting("anchor_x", Component.literal("Placed from across"), anchor.ordinal(), START, END, 0), "Panel");
		anchorY = add(new FloatSetting("anchor_y", Component.literal("Placed from down"), fromBottom ? END : START, START, END, 0), "Panel");
		look.followAccent(accent, () -> ModFeatures.MENU.hudAccent());
		look.follow(scale, () -> ModFeatures.MENU.hudSize());
		look.follow(background, () -> ModFeatures.MENU.hudBackground());
		look.follow(glow, () -> ModFeatures.MENU.hudGlow());
		look.follow(glowSoft, () -> ModFeatures.MENU.hudGlowSoft(), () -> ModFeatures.MENU.hudGlow());
		look.follow(panelTheme, () -> PanelTheme.HUD);
		look.follow(seeThrough, () -> ModFeatures.MENU.hudSeeThrough());
		look.follow(panelDepth, () -> PanelDepth.MENU);
		glowSoft.visibleWhen(() -> false);
		x.visibleWhen(() -> false);
		y.visibleWhen(() -> false);
		anchorX.visibleWhen(() -> false);
		anchorY.visibleWhen(() -> false);
		seeThrough.visibleWhen(this::hasBackground);
		background.visibleWhen(() -> offersNoBackground);
		// the glow surrounds the panel, so there is none without a background
		glow.visibleWhen(this::hasBackground);
	}

	protected abstract int panelWidth();

	protected abstract int panelHeight();

	/** Draws the panel content with its top-left corner at 0, 0, in panel pixels. */
	protected abstract void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client);

	/** Called every frame before the size is queried, so panels with changing content can gather it once. */
	protected void prepare(Minecraft client) {
	}

	/**
	 * @return a factor applied to the scale, so panels with large content still fit on the screen: a panel taller or wider than the screen (a
	 *         tall card at a large GUI scale in a small window) shrinks until it fits, never cut off
	 */
	protected float fit(int width, int height, int guiWidth, int guiHeight, float scale) {
		float room = Math.min((guiWidth - 2f * SCREEN_MARGIN) / Math.max(1f, width * scale), (guiHeight - 2f * SCREEN_MARGIN) / Math.max(1f, height * scale));
		return Math.min(1f, Math.max(MIN_FIT, room));
	}

	/** The room a panel keeps from the screen's edges when it has to shrink, and the least it shrinks to. */
	private static final float SCREEN_MARGIN = 4f;
	private static final float MIN_FIT = 0.3f;

	/**
	 * @return 0, or for a panel of variable width the width it is anchored for: on the right half of the screen the panel then keeps its right edge
	 *         in place and grows to the left, so it stays attached to the nearer edge
	 */
	protected float edgeWidth() {
		return 0f;
	}

	/** Width setting of SkyBlock cards that offer one; null for other panels. */
	private FloatSetting cardWidthSetting;
	private FloatSetting cardHeightSetting;

	/** Adds width and line spacing settings to a SkyBlock card; all cards start at the same width so they line up. */
	protected final void offerCardSize() {
		// a factor of the shared card width (1 = 200), like the height, so it reads 1, 0.8 or 1.2 instead of a pixel count such as 204
		cardWidthSetting = add(new FloatSetting("card_width_factor", Component.literal("Width"), 1f, 0.75f, 1.8f, 2).percent(), "Panel");
		cardHeightSetting = add(new FloatSetting("card_height", Component.literal("Line spacing"), 1f, 0.8f, 1.8f, 2), "Panel");
		spacingRow = add(PanelLook.spacingRow("card_height_row", cardHeightSetting, 0.8f, 1.8f), "Panel");
		look.follow(cardWidthSetting, () -> ModFeatures.MENU.hudWidthMode() == WidthMode.AUTO ? 1f : ModFeatures.MENU.hudWidth());
		look.follow(cardHeightSetting, () -> ModFeatures.MENU.hudLineSpacing());
	}

	private EnumSetting<PanelLook.Spacing> spacingRow;

	/** A panel's width: fitted to its content, or as wide as the player set it. */
	public enum WidthMode {
		AUTO("Auto"),
		CUSTOM("Custom");

		private final Component displayName;

		WidthMode(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private EnumSetting<WidthMode> widthMode;
	private FloatSetting customWidth;
	/** The narrowest the panel can be with its content still fitting, and its width with Auto, both in panel pixels, as last laid out. */
	private int minimumWidth = 60;
	private int autoWidth = 120;

	/** Gives the panel a width setting (Auto or Custom with a slider), also set by dragging its edges in the HUD editor. */
	protected final void offerWidth(int defaultWidth) {
		widthMode = add(new EnumSetting<>("width_mode", Component.literal("Width"), WidthMode.AUTO, WidthMode::displayName,
			EnumSetting.Style.SEGMENTED), "Panel");
		customWidth = add(new FloatSetting("width", Component.literal("Custom width"), defaultWidth, 60f, 1000f, 0), "Panel");
		customWidth.under(widthMode, () -> widthMode.get() == WidthMode.CUSTOM);
		autoWidth = defaultWidth;
		look.follow(widthMode, () -> ModFeatures.MENU.hudWidthMode());
		look.follow(customWidth, () -> Mth.clamp((float) Math.round(defaultWidth * ModFeatures.MENU.hudWidth()), customWidth.min(), customWidth.max()),
			() -> ModFeatures.MENU.hudWidthMode() == WidthMode.CUSTOM);
	}

	/** @return the width settings, for the More page of the settings card; empty for panels without them */
	protected List<Setting<?>> widthSettings() {
		if (cardWidthSetting != null) {
			return List.of(cardWidthSetting, cardHeightSetting);
		}
		return widthMode == null ? List.of() : List.of(widthMode, customWidth);
	}

	/** Tells the panel its narrowest width with the content still fitting, and its width with Auto, in panel pixels. */
	protected final void widthLimits(int minimum, int auto) {
		minimumWidth = Math.max(1, minimum);
		autoWidth = Math.max(minimumWidth, auto);
	}

	/** @return whether the player set a width of their own */
	protected final boolean customWidth() {
		return widthMode != null && widthMode.get() == WidthMode.CUSTOM;
	}

	/** @return the width the panel is drawn at: the player's, but never narrower than the content allows, or the given automatic width */
	protected final int widthOr(int auto) {
		return customWidth() ? Math.max(minimumWidth, Math.round(customWidth.get())) : auto;
	}

	@Override
	public boolean hudResizableWidth() {
		return widthMode != null || cardWidthSetting != null;
	}

	/** The edge that stays in place while the other one is dragged, in GUI pixels. */
	private float fixedEdge;

	@Override
	public void hudWidthStart(boolean right) {
		fixedEdge = right ? bounds[0] : bounds[0] + bounds[2];
	}

	@Override
	public String hudDragWidth(boolean right, float mouseX, boolean free) {
		float s = Math.max(0.01f, drawnScale);
		float wanted = Math.abs(mouseX - fixedEdge) / s;
		// the hotbar's width, and half of it, are useful widths to line up with
		float[] bar = new float[4];
		HudStack.hotbar(lastGuiWidth, lastGuiHeight, bar);
		float hotbar = bar[2] / s;
		if (cardWidthSetting != null) {
			if (!free) {
				wanted = WidthSnap.snap(wanted, WidthSnap.REACH / s, SKYBLOCK_WIDTH, hotbar);
			}
			float factor = Mth.clamp(wanted / SKYBLOCK_WIDTH, cardWidthSetting.min(), cardWidthSetting.max());
			cardWidthSetting.set(Math.round(factor * 100f) / 100f);
			keepEdge(right, cardWidth() * s);
			return WidthSnap.label(cardWidth());
		}
		if (!free) {
			wanted = WidthSnap.snap(wanted, WidthSnap.REACH / s, autoWidth, hotbar, hotbar / 2f);
		}
		wanted = Math.max(minimumWidth, wanted);
		if (!free && Math.abs(wanted - autoWidth) < 0.5f) {
			// snapped onto the automatic width: Auto again
			widthMode.set(WidthMode.AUTO);
			keepEdge(right, autoWidth * s);
			return "Auto";
		}
		widthMode.set(WidthMode.CUSTOM);
		customWidth.set(Mth.clamp((float) Math.round(wanted), customWidth.min(), customWidth.max()));
		keepEdge(right, widthOr(autoWidth) * s);
		return WidthSnap.label(widthOr(autoWidth));
	}

	/** Places the panel so the edge that is not dragged stays where it was, whichever screen edge the panel is placed from. */
	private void keepEdge(boolean right, float drawnWidth) {
		float left = right ? fixedEdge : fixedEdge - drawnWidth;
		float value = switch (across()) {
			case START -> left;
			case END -> lastGuiWidth - left - drawnWidth;
			default -> left + drawnWidth / 2f - lastGuiWidth / 2f;
		};
		x.set(Mth.clamp(value, x.min(), x.max()));
	}

	@Override
	public void hudResetWidth(boolean right) {
		if (cardWidthSetting != null) {
			cardWidthSetting.reset();
		}
		if (widthMode != null) {
			widthMode.reset();
			customWidth.reset();
		}
	}

	/** @return the card width in pixels */
	protected int cardWidth() {
		return cardWidthSetting == null ? SKYBLOCK_WIDTH : Math.round(SKYBLOCK_WIDTH * cardWidthSetting.get());
	}

	/** @return the height of a card line: the base height adjusted by the line spacing setting */
	protected int line(int base) {
		return cardHeightSetting == null ? base : Math.round(base * cardHeightSetting.get());
	}

	/** @return whether the panel should be shown right now, when not being edited */
	protected boolean shown(Minecraft client) {
		return true;
	}

	/** The panel whose sample the editor's list asked for, while it is not active here. */
	private static HudPanelFeature previewed;

	/**
	 * @return whether the panel has a sample to show when the editor's list asks for it while it is hidden. Panels that only appear during an activity return
	 *         false: they show in the editor while that activity is going on, and not otherwise.
	 */
	protected boolean inEditorWhenHidden() {
		return true;
	}

	protected int accentArgb() {
		return 0xFF000000 | accent.get();
	}

	/** The size the panel was last drawn at, including its fit to the screen. */
	private float drawnScale = 1f;

	/** @return the size the panel was last drawn at */
	public float drawnScale() {
		return drawnScale;
	}

	/**
	 * @return for a panel docked to the stack above the hotbar, the bottom edge it sits on in GUI pixels; NaN for a panel placed by its own
	 *         position (all others)
	 */
	protected float dockedBottom(int guiWidth, int guiHeight) {
		return Float.NaN;
	}

	/** @return for a docked panel, the center it is placed on across */
	protected float dockedCenterX(int guiWidth, int guiHeight) {
		return guiWidth / 2f;
	}

	@Override
	public HudParts hudParts() {
		return parts;
	}

	/** Records a part that a click in the HUD editor switches, at a box in panel pixels. */
	protected void part(String name, Toggle setting, float x, float y, float width, float height) {
		if (HudEditScreen.isOpen()) {
			parts.add(name, (BooleanSetting) setting, bounds[0] + x * drawnScale, bounds[1] + y * drawnScale, width * drawnScale, height * drawnScale);
		}
	}

	/** Draws the glass background, like the info bar's. */
	protected void drawGlass(Canvas ui, int width, int height, float radius) {
		lastRadius = radius;
		if (!hasBackground()) {
			return; // no background: content only
		}
		HudStyle.panel((Ui) ui, 0, 0, width, height, radius, HudStyle.opacity() * (1f - seeThrough.get()), glow.get());
	}

	/**
	 * @return whether the HUD editor simulates this panel's activity: the panel then shows with its sample content, whatever the game is doing
	 *         (see {@link HudSimulation})
	 */
	protected boolean simulated() {
		return HudSimulation.simulates(this);
	}

	/** @return whether the menu glow is drawn around this panel */
	public boolean glowOn() {
		return glow.get();
	}

	@Override
	public float hudRadius() {
		return lastRadius * shownScale.value();
	}

	/** Draws a stack at a slot: the item, its count and its durability bar. */
	protected void drawStack(GuiGraphicsExtractor graphics, Minecraft client, ItemStack stack, int slotX, int slotY, boolean decorations) {
		drawStack(graphics, client, stack, slotX, slotY, decorations, 0, 0, 0, 0);
	}

	/** Same as above, clipped to the given box. */
	protected void drawStack(GuiGraphicsExtractor graphics, Minecraft client, ItemStack stack, int slotX, int slotY, boolean decorations, int boxX, int boxY, int boxWidth, int boxHeight) {
		if (stack.isEmpty()) {
			return;
		}
		boolean clipped = boxWidth > 0;
		if (clipped) {
			graphics.enableScissor(boxX, boxY, boxX + boxWidth, boxY + boxHeight);
		}
		graphics.item(stack, slotX, slotY);
		if (decorations) {
			graphics.itemDecorations(client.font, stack, slotX, slotY);
		}
		if (clipped) {
			graphics.disableScissor();
		}
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || client.level == null || client.player == null) {
			return;
		}
		long now = System.nanoTime();
		// shown in the preview beside its page: moving on here too would pull its animations two ways in a frame, and the window covers this place anyway
		if (now - previewDrawnAt < PREVIEW_HOLD_NANOS) {
			return;
		}
		frameSeconds = lastFrameNanos == 0L ? 0f : Math.min(MOST_STEP_SECONDS, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
		prepare(client);
		boolean editing = HudEditScreen.isOpen();
		// the editor shows what would show now; while an activity is simulated there, the panels of the activity show and the others are hidden
		boolean asked = editing && previewed == this;
		boolean hidden = editing && HudSimulation.active() ? !HudSimulation.shows(this) || !simulated() && !asked && !shown(client)
			: !asked && !shown(client);
		if (hidden || !editing && ScreenTitles.titleShown() && ScreenTitles.over(bounds[0], bounds[1], bounds[2], bounds[3], graphics.guiWidth())) {
			hiddenNow = true;
			return;
		}
		hiddenNow = false;
		// Give every panel its own stratum. The game places each new GUI element above the highest element it overlaps, which it finds by scanning
		// every element of the stratum: with the whole HUD in one stratum that was a third of the HUD's cost. Panels do not overlap each other, so
		// separate strata change nothing visible.
		graphics.nextStratum();
		int w = panelWidth();
		int h = panelHeight();
		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		float eased = shownScale.follow(scale.get());
		float s = eased * fit(w, h, guiWidth, guiHeight, eased);
		lastGuiWidth = guiWidth;
		lastGuiHeight = guiHeight;
		float left = switch (across()) {
			case START -> edgeWidth() > 0f && x.get() + edgeWidth() * s / 2f > guiWidth / 2f ? x.get() + (edgeWidth() - w) * s : x.get();
			case END -> guiWidth - x.get() - w * s;
			default -> guiWidth / 2f + x.get() - w * s / 2f;
		};
		float top = switch (down()) {
			case START -> y.get();
			case END -> guiHeight - y.get() - h * s;
			default -> guiHeight / 2f + y.get() - h * s / 2f;
		};
		float dockBottom = dockedBottom(guiWidth, guiHeight);
		if (!Float.isNaN(dockBottom)) {
			// docked to the stack above the hotbar: centered on it, offset across by the place setting
			left = dockedCenterX(guiWidth, guiHeight) + x.get() - w * s / 2f;
			top = dockBottom - h * s;
		}
		bounds = new int[]{Math.round(left), Math.round(top), Math.round(w * s), Math.round(h * s)};
		drawnScale = s;
		parts.clear();

		Ui.setZoom(s);
		// the panel's own look while it is drawn (colors are taken from it for the whole panel)
		enterLook();
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(left, top);
			pose.scale(s, s);
			drawPanel(ui, graphics, client);
		} finally {
			// always restore the render state, so an exception in here cannot break the rest of the screen
			pose.popMatrix();
			Ui.setZoom(1f);
			leaveLook();
		}
	}

	/** Everything drawn from here on has this panel's own look: its theme, depth, glow and transparency, and never the menu's, also outside of the HUD pass. */
	public final void enterLook() {
		HudStyle.setPanelTheme(panelTheme.get().themeOrHud());
		HudStyle.setPanelDepth(panelDepth.get().depth());
		HudStyle.setPanelGlow(glowSoft.get() ? PanelLook.SOFT_GLOW : 1f);
		HudStyle.setPanelSurface(glow.get(), seeThrough.get());
	}

	public static void leaveLook() {
		HudStyle.setPanelTheme(null);
		HudStyle.setPanelDepth(null);
		HudStyle.setPanelGlow(1f);
		HudStyle.setPanelSurface(null, 0f);
	}

	// ------------------------------------------------------------------------------------------------ the panel's look

	private ActionRowSetting lookRow;
	private SettingsView lookView;
	/** The settings the Look sheet holds, which leave the panel's page. */
	private List<Setting<?>> lookSettings;

	/** @return the one row a panel's page has for its look */
	private ActionRowSetting lookRow() {
		if (lookRow == null) {
			lookRow = look.row(() -> MacScreen.openSubPage(lookView()));
		}
		return lookRow;
	}

	private List<Setting<?>> lookSettings() {
		if (lookSettings == null) {
			List<Setting<?>> list = new ArrayList<>(List.of(lookMode, panelTheme, accent, background, seeThrough, glowRow, glow, glowSoft, panelDepth, scale));
			if (cardWidthSetting != null) {
				list.addAll(List.of(cardWidthSetting, cardHeightSetting, spacingRow));
			}
			if (widthMode != null) {
				list.addAll(List.of(widthMode, customWidth));
			}
			if (showTitle != null) {
				list.addAll(List.of(showTitle, showStatus));
			}
			lookSettings = list;
		}
		return lookSettings;
	}

	/** The panel's look in one sheet. */
	private SettingsView lookView() {
		if (lookView == null) {
			List<SettingsView.Group> groups = new ArrayList<>();
			groups.add(new SettingsView.Group("Theme", List.of(panelTheme, accent, background, seeThrough)));
			groups.add(new SettingsView.Group("Glow and depth", List.of(glowRow, panelDepth)));
			List<Setting<?>> size = new ArrayList<>(List.of(scale));
			if (cardWidthSetting != null) {
				size.addAll(List.of(cardWidthSetting, spacingRow));
			}
			if (widthMode != null) {
				size.addAll(List.of(widthMode, customWidth));
			}
			groups.add(new SettingsView.Group("Size and text", size));
			if (showTitle != null) {
				groups.add(new SettingsView.Group("Header", List.of(showTitle, showStatus)));
			}
			ButtonSetting copy = new ButtonSetting("copy_look", Component.literal("Copy this look to the other panels"), Component.literal("Copy"), false, () -> {
				copyStyleToOthers();
				FeatureManager.saveSoon();
			});
			groups.add(new SettingsView.Group("Other panels", List.of(copy)));
			lookView = look.sheet(id(), groups);
			copy.disabledWhen(() -> false);
		}
		return lookView;
	}

	/** The Look row is made for the page, and goes by a key in its outline like the settings. */
	@Override
	public Setting<?> settingOf(String key) {
		return key.equals("look_row") ? lookRow() : super.settingOf(key);
	}

	/** The panel itself, live, in the middle of the preview beside its page. */
	@Override
	public PagePreview preview() {
		return outline() == null ? null : panelPreview;
	}

	private final PagePreview panelPreview = new PagePreview() {
		@Override
		public void draw(Ui ui, int x, int y, int width, int height) {
			WorldBackdrop.draw(ui, x, y, width, height, Radius.LG);
			drawFitted(ui, x, y, width, height);
		}

		@Override
		public boolean tall() {
			return true;
		}

		@Override
		public String note() {
			return look.themed() ? "Panel follows the HUD theme" : "Custom look on this panel only";
		}
	};

	/** The room a panel keeps from the edges of its preview. */
	private static final int PREVIEW_MARGIN = 14;

	private static final long PREVIEW_HOLD_NANOS = 200_000_000L;
	private long previewDrawnAt = -PREVIEW_HOLD_NANOS;

	private void drawFitted(Ui menu, int x, int y, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		long now = System.nanoTime();
		frameSeconds = lastFrameNanos == 0L ? 0f : Math.min(MOST_STEP_SECONDS, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
		previewDrawnAt = now;
		GuiGraphicsExtractor graphics = menu.graphics;
		float zoom = Ui.zoom();
		enterLook();
		HudSimulation.previewing(this);
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			prepare(client);
			int w = panelWidth();
			int h = panelHeight();
			if (w <= 0 || h <= 0) {
				return;
			}
			float s = Math.min(scale.get(), Math.min((width - 2f * PREVIEW_MARGIN) / w, (height - 2f * PREVIEW_MARGIN) / h));
			Ui.setZoom(zoom * s);
			Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
			pose.translate(x + (width - w * s) / 2f, y + (height - h * s) / 2f);
			pose.scale(s, s);
			drawPanel(ui, graphics, client);
		} finally {
			pose.popMatrix();
			Ui.setZoom(zoom);
			HudSimulation.previewing(null);
			leaveLook();
		}
	}

	/** Search leads to the sheet for a value of the look. */
	@Override
	public Feature viewOf(Setting<?> setting) {
		return lookSettings().contains(setting) ? lookView() : super.viewOf(setting);
	}

	@Override
	public void load(JsonObject json) {
		PanelLook.loading(() -> super.load(json));
		if (!json.has(lookMode.key())) {
			look.markUndecided();
			if (json.has(glow.key()) && !json.has(glowSoft.key())) {
				// from before the soft glow: it had the strong one
				glowSoft.setQuietly(false);
			}
		}
	}

	@Override
	protected void afterReset() {
		PanelLook.settleAll();
	}

	@Override
	public int changedSettings() {
		return super.changedSettings() - look.changedByTheme();
	}

	// ------------------------------------------------------------------------------------------------ the panel family

	/** The panel's modes (a rich and a simple look), chosen with picture tiles in its settings card and, in the HUD editor, in its header. */
	private EnumSetting<?> modeSetting;
	private String[] modeLabels = new String[0];
	private float[][] modeBoxes = new float[0][];
	/** Gives the panel modes; their names come from the setting's choices. */
	protected final <E extends Enum<E>> void offerModes(Choice<E> setting) {
		modeSetting = (EnumSetting<E>) setting;
		List<E> choices = setting.choices();
		modeLabels = new String[choices.size()];
		modeBoxes = new float[choices.size()][4];
		for (int i = 0; i < choices.size(); i++) {
			modeLabels[i] = setting.nameOf(choices.get(i)).getString();
		}
	}

	/**
	 * The family header's two parts, which can each be hidden: the icon tile with the panel's name (Title) and the quiet value on the right
	 * (Status). Null for a panel without the header.
	 */
	private BooleanSetting showTitle;
	private BooleanSetting showStatus;
	private boolean withoutHeader;

	/** Marks a panel that draws no family header, so it has no Title and Status switches; call before {@link #offerHeader}. */
	protected final void withoutHeader() {
		withoutHeader = true;
	}

	/** @return whether the family header row is there: with any of its parts shown, and always in the HUD editor (hidden parts faded there) */
	protected final boolean headerShown() {
		return showTitle == null || showTitle.get() || showStatus.get() || HudEditScreen.isOpen();
	}

	/** @return where a family panel's content starts: under the header, or at the panel's top padding with the header hidden */
	protected final int contentTop() {
		return headerShown() ? PanelKit.CONTENT_TOP : PanelKit.TOP;
	}

	/** Gives the panel the switches of the family header's two parts, which follow the HUD theme. */
	protected final void offerHeader() {
		if (withoutHeader || showTitle != null) {
			return;
		}
		showTitle = add(new BooleanSetting("show_header_title", Component.literal("Title"), true), "Header");
		showStatus = add(new BooleanSetting("show_header_status", Component.literal("Status"), true), "Header");
		look.follow(showTitle, () -> ModFeatures.MENU.hudTitle());
		look.follow(showStatus, () -> ModFeatures.MENU.hudStatus());
	}

	@Override
	public List<Setting<?>> shownSettings() {
		if (outline() != null) {
			return super.shownSettings();
		}
		// a panel without an outline: its own rows, and one row for its look
		List<Setting<?>> rows = new ArrayList<>(super.shownSettings());
		rows.removeAll(lookSettings());
		rows.add(lookRow());
		return rows;
	}

	@Override
	public String sectionOf(Setting<?> setting) {
		return setting == lookRow ? PanelLook.ROW_GROUP : super.sectionOf(setting);
	}

	/**
	 * Draws the family header (see {@link PanelKit#header}); on the panel selected in the HUD editor the mode switch takes the place of the value
	 * on the right, and a click on a mode picks it. Never during normal play.
	 */
	protected final void familyHeader(Canvas ui, int width, String icon, int[] tile, String title, String meta, int metaColor, boolean dot) {
		boolean modes = modeSetting != null && HudEditScreen.isSelected(this);
		float room = modes ? PanelKit.modeSwitchWidth(ui, modeLabels) + 4f : 0f;
		if (showTitle == null) {
			PanelKit.header(ui, width, icon, tile, title, meta, metaColor, dot, room);
		} else {
			boolean editing = HudEditScreen.isOpen();
			float before = Ui.alpha();
			float right = width - PanelKit.PAD - room;
			// each part keeps its place when the other is hidden; the title takes the whole row without the status
			if (!modes && meta != null && !meta.isEmpty() && (showStatus.get() || editing)) {
				Ui.setAlpha(before * HudParts.alpha(showStatus.get()));
				float left = PanelKit.headerStatus(ui, right, meta, metaColor, dot);
				Ui.setAlpha(before);
				part("Status", showStatus, left, PanelKit.TOP, right - left, PanelKit.TILE);
				if (showStatus.get()) {
					right = left;
				}
			}
			if (showTitle.get() || editing) {
				Ui.setAlpha(before * HudParts.alpha(showTitle.get()));
				float end = PanelKit.headerTitle(ui, icon, tile, title, right);
				Ui.setAlpha(before);
				part("Title", showTitle, PanelKit.PAD, PanelKit.TOP, end - PanelKit.PAD, PanelKit.TILE);
			}
		}
		if (modes) {
			PanelKit.modeSwitch(ui, width - PanelKit.PAD, modeLabels, modeSetting.get().ordinal(), modeBoxes);
			for (int i = 0; i < modeLabels.length; i++) {
				float[] box = modeBoxes[i];
				parts.addChoice(modeLabels[i], modeSetting, i, bounds[0] + box[0] * drawnScale, bounds[1] + box[1] * drawnScale, box[2] * drawnScale,
					box[3] * drawnScale);
			}
		}
	}

	/** Seconds since the panel was last drawn, at most a tenth of a second, so a slow frame cannot make anything jump. */
	private float frameSeconds;
	private long lastFrameNanos;
	private static final float MOST_STEP_SECONDS = 0.1f;

	/** @return the time step of this frame for the panel's motion, in seconds; 0 with the animations off */
	protected final float frameSeconds() {
		return animated() ? frameSeconds : 0f;
	}

	/** @return whether the panel animates: with "Animations" off in the menu settings everything is instant */
	protected static boolean animated() {
		return ModFeatures.MENU.animations();
	}

	/** Moves a spring toward its target with the panel's time step, or straight there with the animations off. */
	protected final float follow(Spring spring, float target) {
		return animated() ? spring.step(target, frameSeconds) : spring.snap(target);
	}

	// ------------------------------------------------------------------------------------------------ HUD editor

	@Override
	public String hudName() {
		return hudName;
	}

	/** Whether the panel was hidden the last time it should have been drawn; such panels are hidden in the editor too if they only show while active. */
	private boolean hiddenNow;

	@Override
	public boolean hudShown() {
		return isEnabled() && !hiddenNow;
	}

	@Override
	public boolean hudEnabled() {
		return isEnabled();
	}

	@Override
	public boolean hudPreviewable() {
		return inEditorWhenHidden();
	}

	@Override
	public void hudPreview(boolean on) {
		if (on) {
			previewed = this;
		} else if (previewed == this) {
			previewed = null;
		}
	}

	@Override
	public int[] hudBounds() {
		return bounds;
	}

	private int across() {
		return Math.round(anchorX.get());
	}

	private int down() {
		return Math.round(anchorY.get());
	}

	@Override
	public void hudLimitMove(int[] move) {
		int acrossSign = across() == END ? -1 : 1;
		int downSign = down() == END ? -1 : 1;
		move[0] = Math.round((Mth.clamp(x.get() + acrossSign * move[0], x.min(), x.max()) - x.get()) * acrossSign);
		move[1] = Math.round((Mth.clamp(y.get() + downSign * move[1], y.min(), y.max()) - y.get()) * downSign);
	}

	@Override
	public void hudMove(int dx, int dy) {
		x.set(Mth.clamp(x.get() + (across() == END ? -dx : dx), x.min(), x.max()));
		y.set(Mth.clamp(y.get() + (down() == END ? -dy : dy), y.min(), y.max()));
	}

	/**
	 * Places the panel from the screen anchor nearest to it (a corner, the middle of an edge or the center), without moving it, so it keeps its
	 * place relative to that anchor when the game window or the GUI scale changes.
	 */
	@Override
	public void hudAnchorNearest() {
		int[] box = bounds;
		float width = lastGuiWidth;
		float height = lastGuiHeight;
		int nearX = third(box[0] + box[2] / 2f, width);
		int nearY = third(box[1] + box[3] / 2f, height);
		anchorX.set((float) nearX);
		anchorY.set((float) nearY);
		x.set(Mth.clamp(switch (nearX) {
			case START -> (float) box[0];
			case END -> width - box[0] - box[2];
			default -> box[0] + box[2] / 2f - width / 2f;
		}, x.min(), x.max()));
		y.set(Mth.clamp(switch (nearY) {
			case START -> (float) box[1];
			case END -> height - box[1] - box[3];
			default -> box[1] + box[3] / 2f - height / 2f;
		}, y.min(), y.max()));
	}

	/** @return which third of the screen a position falls in: start, middle or end */
	private static int third(float at, float size) {
		return at < size / 3f ? START : at > size * 2f / 3f ? END : MIDDLE;
	}

	@Override
	public float hudScale() {
		return scale.get();
	}

	@Override
	public void hudSetScale(float value) {
		float s = Mth.clamp(value, scale.min(), scale.max());
		// keep the top-left corner in place, so only the dragged corner moves
		scale.set(s);
		// keep the top-left corner in place, also when the panel is placed from the right, the bottom or the center
		if (across() == END) {
			x.set(Mth.clamp(lastGuiWidth - bounds[0] - panelWidth() * s, x.min(), x.max()));
		} else if (across() == MIDDLE) {
			x.set(Mth.clamp(bounds[0] + panelWidth() * s / 2f - lastGuiWidth / 2f, x.min(), x.max()));
		}
		if (down() == END) {
			y.set(Mth.clamp(lastGuiHeight - bounds[1] - panelHeight() * s, y.min(), y.max()));
		} else if (down() == MIDDLE) {
			y.set(Mth.clamp(bounds[1] + panelHeight() * s / 2f - lastGuiHeight / 2f, y.min(), y.max()));
		}
	}

	/** The place is kept from the edge or the middle the panel is anchored at, so only the size changes: never rounded through the last drawn box. */
	@Override
	public void hudSetScaleAnchored(float value) {
		scale.set(Mth.clamp(value, scale.min(), scale.max()));
	}

	@Override
	public float hudAnchorX(int[] box) {
		return across() == MIDDLE ? box[0] + box[2] / 2f : box[0];
	}

	@Override
	public float hudScaleAt(int mouseX, int mouseY) {
		float baseWidth = panelWidth();
		return across() == MIDDLE ? (mouseX - (bounds[0] + bounds[2] / 2f)) * 2f / baseWidth : (mouseX - bounds[0]) / baseWidth;
	}

	@Override
	public void hudReset() {
		x.reset();
		y.reset();
		anchorX.reset();
		anchorY.reset();
		scale.reset();
		PanelLook.settleAll();
	}

	/** @return the panel's appearance settings, for the More page of its settings card */
	protected List<Setting<?>> panelSettings() {
		return List.of(lookRow());
	}

	@Override
	public boolean copiesStyle() {
		return true;
	}

	/** Gives every other panel this one's background, glow, theme, depth effect and see-through. */
	@Override
	public void copyStyleToOthers() {
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof HudPanelFeature other && other != this) {
				other.background.set(background.get());
				other.glow.set(glow.get());
				other.panelTheme.set(panelTheme.get());
				other.panelDepth.set(panelDepth.get());
				other.seeThrough.set(seeThrough.get());
				other.lookMode.set(lookMode.get());
			} else if (feature != this) {
				feature.takePanelTheme(panelTheme.get());
			}
		}
	}

	/** Takes a theme from what has one but is not a panel. */
	public void takeTheme(PanelTheme theme) {
		panelTheme.set(theme);
	}

	/** Resets the panel's look as the editor card shows it: background, glow, theme, depth effect, see-through and color. */
	@Override
	public void hudResetStyle() {
		background.reset();
		glow.reset();
		panelTheme.reset();
		panelDepth.reset();
		seeThrough.reset();
		accent.reset();
		lookMode.set(PanelLook.Mode.THEME);
	}
}
