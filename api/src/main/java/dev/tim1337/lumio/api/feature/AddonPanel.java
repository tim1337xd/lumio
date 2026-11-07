package dev.tim1337.lumio.api.feature;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.api.spi.Backend;
import dev.tim1337.lumio.api.spi.PanelHandle;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The base class of an add-on's HUD panels: a feature drawn over the game in the panels' glass look, which the player moves, sizes and styles in
 * the HUD editor like the main mod's panels. Draw it in {@link #drawPanel}; register it with
 * {@link dev.tim1337.lumio.api.AddonContext#registerHud} and draw it from a HUD layer with {@link #render}.
 */
public abstract class AddonPanel extends AddonFeature {
	/** Which side of the screen the panel keeps its place relative to. */
	public enum Anchor {
		LEFT, CENTER, RIGHT
	}

	/** Item stacks for drawing an item as an icon, made once per item. */
	private static final Map<Item, ItemStack> ICONS = new HashMap<>();

	private final PanelHandle panel;

	/**
	 * @param fromBottom whether the default place is measured from the bottom of the screen
	 * @param defaultX   the default place across, in GUI pixels from the anchor
	 * @param defaultY   the default place down (or up from the bottom), in GUI pixels
	 */
	protected AddonPanel(String id, String name, String description, boolean enabledByDefault, Anchor anchor, boolean fromBottom, float defaultX,
		float defaultY) {
		super(id, name, owner -> Backend.get().features().panel((AddonPanel) owner, id, name, description, enabledByDefault, anchor, fromBottom,
			defaultX, defaultY));
		this.panel = (PanelHandle) handle;
	}

	// ----------------------------------------------------------------------------------------------- what the panel does (called by the HUD)

	/** @return the panel's width in GUI pixels, before its scale */
	public abstract int panelWidth();

	/** @return the panel's height in GUI pixels, before its scale */
	public abstract int panelHeight();

	/** Draws the panel at 0, 0, at its size; the canvas is scaled and placed already. */
	public abstract void drawPanel(Canvas canvas, GuiGraphicsExtractor graphics, Minecraft client);

	/** Updates what the panel shows, once per frame before it is measured and drawn. */
	public void prepare(Minecraft client) {
	}

	/** @return whether the panel is shown now (outside the HUD editor) */
	public boolean shown(Minecraft client) {
		return true;
	}

	/** @return whether the panel has a sample to show in the HUD editor while {@link #shown} is false: the editor shows it when the player asks for it in its list; otherwise the player finds it under Simulate */
	public boolean inEditorWhenHidden() {
		return true;
	}

	/** @return the corner radius of the panel as drawn, for the editor's selection outline */
	public float hudRadius() {
		return panel.baseHudRadius();
	}

	/** Draws the panel, from a HUD layer the add-on registers. */
	public final void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		panel.render(graphics, deltaTracker);
	}

	/** @return where the panel was last drawn: x, y, width and height in GUI pixels */
	public final int[] hudBounds() {
		return panel.hudBounds();
	}

	/** @return the panel's scale, as the player set it */
	public final float hudScale() {
		return panel.hudScale();
	}

	/** @return whether the panel was drawn in the last frame */
	public final boolean hudShown() {
		return panel.hudShown();
	}

	/** @return whether the HUD editor has this panel selected */
	public final boolean selectedInEditor() {
		return panel.selected();
	}

	// ----------------------------------------------------------------------------------------------- helpers for drawing and its settings

	/** @return an item as a stack for drawing it as an icon; made once per item */
	protected static ItemStack itemIcon(Item item) {
		return ICONS.computeIfAbsent(item, ItemStack::new);
	}

	/** Lets the player switch the glass background off (content only). */
	protected final void offerNoBackground() {
		panel.offerNoBackground();
	}

	/** @return whether the glass background is drawn */
	protected final boolean hasBackground() {
		return panel.hasBackground();
	}

	/** Lets the player change the panel's width and line spacing, read with {@link #cardWidth} and {@link #line}. */
	protected final void offerCardSize() {
		panel.offerCardSize();
	}

	/** @return the panel's width as the player set it, in GUI pixels */
	protected final int cardWidth() {
		return panel.cardWidth();
	}

	/** @return a line height with the player's line spacing */
	protected final int line(int base) {
		return panel.line(base);
	}

	/** @return the panel's accent color, as 0xAARRGGBB */
	protected final int accentArgb() {
		return panel.accentArgb();
	}

	/** Marks a part of the panel the HUD editor can switch on and off with a click, at its place in the panel. */
	protected final void part(String name, Toggle setting, float x, float y, float width, float height) {
		panel.part(name, setting, x, y, width, height);
	}

	/** Draws the panel's glass background at its size. */
	protected final void drawGlass(Canvas canvas, int width, int height, float radius) {
		panel.drawGlass(canvas, width, height, radius);
	}

	/** @return whether the HUD editor simulates this panel's activity: then draw the sample content */
	protected final boolean simulated() {
		return panel.simulated();
	}

	/** Draws an item in a slot of the panel, with its count and bar when decorations is true. */
	protected final void drawStack(GuiGraphicsExtractor graphics, Minecraft client, ItemStack stack, int slotX, int slotY, boolean decorations) {
		panel.drawStack(graphics, client, stack, slotX, slotY, decorations, 0, 0, 0, 0);
	}

	/** Draws an item in a slot of the panel; the box is where its tooltip shows when hovered in a screen. */
	protected final void drawStack(GuiGraphicsExtractor graphics, Minecraft client, ItemStack stack, int slotX, int slotY, boolean decorations,
		int boxX, int boxY, int boxWidth, int boxHeight) {
		panel.drawStack(graphics, client, stack, slotX, slotY, decorations, boxX, boxY, boxWidth, boxHeight);
	}

	/** Lets the player pick the panel's mode in its header in the HUD editor. */
	protected final <E extends Enum<E>> void offerModes(Choice<E> setting) {
		panel.offerModes(setting);
	}

	/** Leaves the family header out (the panel draws its own top). */
	protected final void withoutHeader() {
		panel.withoutHeader();
	}

	/** @return where the content starts, under the header when it is shown */
	protected final int contentTop() {
		return panel.contentTop();
	}

	/**
	 * Gives the panel the switches of the header {@link #familyHeader} draws: its title and its status, which follow the HUD theme.
	 */
	protected final void offerHeader() {
		panel.offerHeader();
	}

	/**
	 * Settings pages are laid out by their outlines now, and no card is drawn any more: only the header's switches remain of this.
	 *
	 * @deprecated use {@link #offerHeader()}
	 */
	@Deprecated
	protected final <E extends Enum<E>> void familyCard(String context, Choice<E> look, PanelPicture<E> picture, List<? extends Toggle> shows, String moreSummary, Supplier<List<SettingGroup>> more) {
		panel.offerHeader();
	}

	/** Draws the header all panels share: an icon tile, the title and a status on the right. */
	protected final void familyHeader(Canvas canvas, int width, String icon, int[] tile, String title, String meta, int metaColor, boolean dot) {
		panel.familyHeader(canvas, width, icon, tile, title, meta, metaColor, dot);
	}

	/** @return the seconds since this panel's last frame, for its animations (such as a spring's step) */
	protected final float frameSeconds() {
		return panel.frameSeconds();
	}
}
