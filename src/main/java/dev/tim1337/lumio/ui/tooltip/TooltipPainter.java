package dev.tim1337.lumio.ui.tooltip;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.TooltipLine;
import dev.tim1337.lumio.api.experimental.util.HudCover;
import dev.tim1337.lumio.api.TooltipLineProvider;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.features.visual.screen.ItemTooltipsFeature;
import dev.tim1337.lumio.mixin.accessor.AbstractContainerScreenAccessor;

import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the tooltips of items. What the game sends goes through untouched: the game's own components, drawn by the game's own methods, in their order. Lumio only
 * chooses the frame and the size around them, scrolls what does not fit, and draws its one block of added lines before or after them, never between them.
 * Other tooltips, of buttons and the like, stay the game's.
 */
public final class TooltipPainter {

	private static final String SCROLL_HINT = "Scroll for more";
	/** The game leaves this much room under the first line of a tooltip. */
	private static final int FIRST_LINE_GAP = 2;
	/** A tooltip stays this far from the edges of the screen. */
	private static final int MARGIN = 6;
	private static final int FRAME_PAD_X = 6;
	private static final int FRAME_PAD_Y = 5;
	private static final int FADE = 18;
	private static final int BAR_WIDTH = 2;
	private static final int BAR_GAP = 3;
	private static final int MIN_BAR = 10;
	private static final int HINT_FROM_BOTTOM = 9;
	/** How long a hovered item's lines are kept before they are asked for again: its values change in the background, not per frame. */
	private static final long LINES_MILLIS = 250L;
	/** How long after a frame's tooltip the wheel still belongs to it. */
	private static final long WHEEL_MILLIS = 200L;
	/**
	 * The frame is dense, so that the game's colors read over any world. It is painted and not a pane of real glass, which would take away the blur the game puts behind an open screen
	 * while a tooltip is shown.
	 */
	private static final int FRAME = 0xE80E1018;
	/** Under the game's own frame over a full overlay: its color, opaque, where the frame is only nearly so. */
	private static final int VANILLA_UNDER = 0xFF100010;
	private static final int VANILLA_PAD = 3;
	private static final int FRAME_EDGE = 0x47FFFFFF;
	private static final int FRAME_SHADOW = 9;
	private static final float FRAME_SHADOW_OPACITY = 0.34f;
	private static final int FADE_COLOR = 0xF00D0F18;
	private static final int TEXT = 0xFFF2F2F7;
	private static final int SECONDARY = 0xFFA8A8B0;
	private static final int LINE = 0x38FFFFFF;

	private static final TooltipScroll SCROLL = new TooltipScroll();
	private static final List<TooltipLine> NO_LINES = List.of();

	private static boolean failureLogged;
	private static ItemStack armed;
	private static int armedLines;

	private static ItemStack linesFor;
	/** The slot the lines were made for: equal items in two slots (the face-down cards of a game) may get other lines. */
	private static Slot linesSlot;
	private static ItemStack scrollItem;
	private static long linesAt;
	private static List<TooltipLine> lines = NO_LINES;
	private static long drawnAt;
	private static long lastFrame = System.nanoTime();

	private TooltipPainter() {
	}

	/** Called with what the game made as the tooltip of an item: the next tooltip of this frame that fits it is that item's. */
	public static void arm(ItemStack stack, int lineCount) {
		if (ModFeatures.ITEM_TOOLTIPS != null && ModFeatures.ITEM_TOOLTIPS.isEnabled() && !stack.isEmpty()) {
			armed = stack;
			armedLines = lineCount;
		}
	}

	/** Called when a frame's tooltip has had its turn: an item that asked for its lines for another reason is not the next frame's tooltip. */
	public static void endFrame() {
		armed = null;
	}

	/** @return whether the wheel moved a long tooltip that is shown now; the screen behind it does not scroll then */
	public static boolean wheel(double notches) {
		if (System.currentTimeMillis() - drawnAt > WHEEL_MILLIS || !SCROLL.canScroll() || !ModFeatures.ITEM_TOOLTIPS.scrolls()) {
			return false;
		}
		SCROLL.wheel(notches, Minecraft.getInstance().hasShiftDown());
		return true;
	}

	/** @return the item a tooltip of these components is for, or null for one that is not an item's: a list of lines, and the item's image if it has one */
	private static ItemStack itemOf(int components) {
		ItemStack stack = armed;
		armed = null;
		if (stack == null) {
			return null;
		}
		int extra = components - armedLines;
		return extra == 0 || extra == 1 ? stack : null;
	}

	/** @return whether it drew the tooltip; if not, the game draws it itself */
	public static boolean draw(GuiGraphicsExtractor graphics, Font font, List<ClientTooltipComponent> game, int mouseX, int mouseY, ClientTooltipPositioner positioner, Identifier style) {
		ItemTooltipsFeature feature = ModFeatures.ITEM_TOOLTIPS;
		if (feature == null || !feature.isEnabled() || game.isEmpty()) {
			return false;
		}
		ItemStack stack = itemOf(game.size());
		if (stack == null) {
			return false;
		}
		List<TooltipLine> added = addedLines(feature, stack);
		var menu = ModFeatures.MENU;
		long now = System.nanoTime();
		float delta = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		// the game's colors are made for a dark background, whatever the theme of the menu is
		Ui ui = new Ui(graphics, font, MacTheme.DARK.palette(), menu.accentArgb(), delta, menu.animations());
		float size = feature.size();
		int screenWidth = Math.round(graphics.guiWidth() / size);
		int screenHeight = Math.round(graphics.guiHeight() / size);

		int gameWidth = 0;
		int gameHeight = game.size() == 1 ? -FIRST_LINE_GAP : 0;
		for (int i = 0; i < game.size(); i++) {
			ClientTooltipComponent component = game.get(i);
			gameWidth = Math.max(gameWidth, component.getWidth(font));
			gameHeight += component.getHeight(font) + (i == 0 ? FIRST_LINE_GAP : 0);
		}

		AddedBlock.Measure measure = (text, small) -> small ? Math.round(ui.widthScaled(text, TextScale.SMALL)) : ui.width(text);
		int blockHeight = added.isEmpty() ? 0 : AddedBlock.height(added);
		int width = added.isEmpty() ? gameWidth : Math.max(gameWidth, AddedBlock.width(added, measure));
		int contentHeight = gameHeight + blockHeight;

		boolean scrolls = feature.scrolls();
		int room = screenHeight - 2 * MARGIN - 2 * FRAME_PAD_Y;
		int visible = scrolls ? Math.min(contentHeight, Math.max(room, 1)) : contentHeight;
		// the game makes a new stack now and then for the same item: only another item starts at the top
		if (!sameItem(stack)) {
			SCROLL.clear();
			scrollItem = stack;
		}
		SCROLL.show(scrollItem, contentHeight, visible);

		Vector2ic place = positioner.positionTooltip(screenWidth, screenHeight, Math.round(mouseX / size), Math.round(mouseY / size), width, visible);
		int x = Mth.clamp(place.x(), MARGIN + FRAME_PAD_X, Math.max(MARGIN + FRAME_PAD_X, screenWidth - width - MARGIN - FRAME_PAD_X));
		int y = Mth.clamp(place.y(), MARGIN + FRAME_PAD_Y, Math.max(MARGIN + FRAME_PAD_Y, screenHeight - visible - MARGIN - FRAME_PAD_Y));

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(size, size);
		drawFrame(ui, graphics, feature.frame(), x, y, width, visible, style);
		boolean clipped = visible < contentHeight;
		if (clipped) {
			graphics.enableScissor(x - 1, y - 1, x + width + 1, y + visible + 1);
		}
		int top = y - SCROLL.offset();
		boolean first = feature.place() == ItemTooltipsFeature.Place.TOP;
		if (!added.isEmpty() && first) {
			drawBlock(ui, added, x, top, width, measure, true);
			top += blockHeight;
		}
		drawGame(graphics, font, game, x, top, width, gameHeight);
		top += gameHeight;
		if (!added.isEmpty() && !first) {
			drawBlock(ui, added, x, top, width, measure, false);
		}
		if (clipped) {
			graphics.disableScissor();
			drawScrollSigns(ui, x, y, width, visible, contentHeight);
		}
		pose.popMatrix();
		drawnAt = System.currentTimeMillis();
		return true;
	}

	private static boolean sameItem(ItemStack stack) {
		return scrollItem != null && ItemStack.isSameItemSameComponents(scrollItem, stack);
	}

	private static List<TooltipLine> addedLines(ItemTooltipsFeature feature, ItemStack stack) {
		boolean wanted = switch (feature.show()) {
			case ALWAYS -> true;
			case SHIFT -> Minecraft.getInstance().hasShiftDown();
			case NEVER -> false;
		};
		TooltipLineProvider[] providers = Addons.tooltipLines();
		if (!wanted || providers.length == 0) {
			return NO_LINES;
		}
		long now = System.currentTimeMillis();
		Slot slot = Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreenAccessor container ? container.lumio$hoveredSlot() : null;
		if (now - linesAt < LINES_MILLIS && linesFor != null && slot == linesSlot && ItemStack.isSameItemSameComponents(linesFor, stack)) {
			return lines;
		}
		linesAt = now;
		linesFor = stack;
		linesSlot = slot;
		List<TooltipLine> fresh = new ArrayList<>();
		for (TooltipLineProvider provider : providers) {
			try {
				provider.addLines(stack, fresh);
			} catch (RuntimeException e) {
				// an add-on's data is never worth a broken tooltip; said once, as it would come a few times per second
				fresh.clear();
				if (!failureLogged) {
					failureLogged = true;
					Lumio.LOGGER.warn("Lines for an item's tooltip could not be made", e);
				}
			}
		}
		lines = fresh;
		return lines;
	}

	private static void drawFrame(Ui ui, GuiGraphicsExtractor graphics, ItemTooltipsFeature.Frame frame, int x, int y, int width, int height, Identifier style) {
		// over a screen that covers the whole game (an add-on's overlay) what lies behind is that screen's own text: the tooltip hides it completely
		boolean covering = Minecraft.getInstance().gui.screen() != null && HudCover.covers(Minecraft.getInstance().gui.screen());
		if (frame == ItemTooltipsFeature.Frame.VANILLA) {
			if (covering) {
				ui.fill(x - VANILLA_PAD, y - VANILLA_PAD, width + 2 * VANILLA_PAD, height + 2 * VANILLA_PAD, VANILLA_UNDER);
			}
			TooltipRenderUtil.extractTooltipBackground(graphics, x, y, width, height, style);
			return;
		}
		int left = x - FRAME_PAD_X;
		int top = y - FRAME_PAD_Y;
		int frameWidth = width + 2 * FRAME_PAD_X;
		int frameHeight = height + 2 * FRAME_PAD_Y;
		ui.shadow(left, top, frameWidth, frameHeight, Radius.LG, FRAME_SHADOW, FRAME_SHADOW_OPACITY);
		ui.rounded(left, top, frameWidth, frameHeight, Radius.LG, covering ? FRAME | 0xFF000000 : FRAME);
		ui.ring(left, top, frameWidth, frameHeight, Radius.LG, 1, FRAME_EDGE);
	}

	/** The game's lines, in its order, by its own methods. */
	private static void drawGame(GuiGraphicsExtractor graphics, Font font, List<ClientTooltipComponent> game, int x, int y, int width, int height) {
		int at = y;
		for (int i = 0; i < game.size(); i++) {
			ClientTooltipComponent component = game.get(i);
			component.extractText(graphics, font, x, at);
			at += component.getHeight(font) + (i == 0 ? FIRST_LINE_GAP : 0);
		}
		at = y;
		for (int i = 0; i < game.size(); i++) {
			ClientTooltipComponent component = game.get(i);
			component.extractImage(font, x, at, width, height, graphics);
			at += component.getHeight(font) + (i == 0 ? FIRST_LINE_GAP : 0);
		}
	}

	/** The block: a thin line on the side of the game's lines, and the lines. */
	private static void drawBlock(Ui ui, List<TooltipLine> added, int x, int y, int width, AddedBlock.Measure measure, boolean above) {
		int at = y;
		if (!above) {
			at += AddedBlock.LINE_GAP;
			ui.fill(x, at, width, 1, LINE);
			at += 1 + AddedBlock.LINE_GAP;
		}

		int[] columns = AddedBlock.columns(added, measure);
		int valueRight = x + columns[0] + AddedBlock.NAME_GAP + columns[1];
		for (TooltipLine line : added) {
			switch (line.kind()) {
				case HEADING -> {
					ui.textAtScaled(line.text(), x, at + AddedBlock.HEADING_TOP, TextScale.SMALL, SECONDARY);
					if (!line.value().isEmpty()) {
						int aside = measure.width(line.value(), true);
						ui.textAtScaled(line.value(), x + width - aside, at + AddedBlock.HEADING_TOP, TextScale.SMALL, colorOf(line));
					}
				}
				case ROW -> {
					int middle = at + AddedBlock.ROW_HEIGHT / 2;
					ui.textMiddle(line.text(), x, middle, SECONDARY);
					ui.textRight(line.value(), valueRight, middle, colorOf(line));
					if (!line.note().isEmpty()) {
						ui.textAtScaled(line.note(), valueRight + AddedBlock.NOTE_GAP, middle - TextScale.SMALL * 4f, TextScale.SMALL, SECONDARY);
					}
				}
				case FOOTNOTE -> ui.textAtScaled(line.text(), x, at + 1f, TextScale.SMALL, SECONDARY);
			}
			at += AddedBlock.lineHeight(line);
		}
		if (above) {
			at += AddedBlock.LINE_GAP;
			ui.fill(x, at, width, 1, LINE);
		}
	}

	private static int colorOf(TooltipLine line) {
		return line.valueRgb() == TooltipLine.PLAIN ? TEXT : 0xFF000000 | line.valueRgb();
	}

	/** The soft fade at the bottom while more lies below, the thin scroll bar and the hint. */
	private static void drawScrollSigns(Ui ui, int x, int y, int width, int visible, int contentHeight) {
		if (SCROLL.moreBelow()) {
			int fadeTop = y + visible - FADE;
			ui.verticalGradient(x - 1, fadeTop, width + 2, FADE, FADE_COLOR & 0xFFFFFF, FADE_COLOR);
			int hint = Math.round(ui.widthScaled(SCROLL_HINT, TextScale.SMALL));
			ui.textAtScaled(SCROLL_HINT, x + (width - hint) / 2f, y + visible - HINT_FROM_BOTTOM, TextScale.SMALL, SECONDARY);
		}
		int barHeight = Math.max(MIN_BAR, visible * visible / Math.max(1, contentHeight));
		int barY = y + Math.round((visible - barHeight) * SCROLL.share());
		ui.rounded(x + width + BAR_GAP, barY, BAR_WIDTH, barHeight, Radius.XXS, 0x80FFFFFF);
	}

}
