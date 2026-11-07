package dev.tim1337.lumio.api.experimental.draw;

import dev.tim1337.lumio.api.draw.Canvas;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The shared panel code. The SkyBlock HUD panels are one family drawn from it (see the second half): the same glass, one header (a small colored
 * icon tile, the title and one quiet value on the right), one hero each, and the same rings, bars, chips, stat tiles, simple rows and number
 * formats. The item search, the item detail and the storage overlay use its first half: a search field with its focus ring, result text and clear
 * button, a segmented control, filter chips, item tiles with their rarity glow and line, and the inner cards. Every part follows the HUD theme
 * (glass, or solid dark and light panels). Boxes are {x, y, width, height} in GUI pixels.
 */
public final class PanelKit {
	public static final int FIELD_HEIGHT = 22;
	public static final int SEGMENT_HEIGHT = 16;
	public static final int CHIP_HEIGHT = 14;
	private static final int CHIP_PAD = 8;
	private static final int SEGMENT_PAD = 8;
	private static final int CLEAR_SIZE = 12;
	/** How much a hovered tile rises, and how far its rarity glow reaches, as shares of the tile. */
	private static final float LIFT = 1.5f;
	private static final float GLOW = 0.42f;
	private static final float RARITY_LINE = 0.42f;

	private PanelKit() {
	}

	public static boolean inside(int[] box, double x, double y) {
		return box[2] > 0 && x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
	}

	/** @return the box of a search field's clear button */
	public static int[] clearBox(int[] field) {
		return new int[]{field[0] + field[2] - CLEAR_SIZE - 5, field[1] + (field[3] - CLEAR_SIZE) / 2, CLEAR_SIZE, CLEAR_SIZE};
	}

	/**
	 * Draws a search field: its own backing, a ring in the accent while focused, the search icon, the text with a blinking caret, the result text
	 * at the right and the clear button while there is text.
	 *
	 * @param placeholder shown while empty and not focused
	 * @param right       the result text ("24 results"), or a key hint while empty, or null
	 */
	public static void searchField(Canvas ui, int[] box, String text, String placeholder, String right, boolean focused, int mouseX, int mouseY) {
		boolean over = inside(box, mouseX, mouseY);
		// a glass pill, ringed in the accent with a soft glow while it has focus
		float radius = box[3] / 2f;
		if (focused) {
			ui.softLight(box[0] + box[2] / 2f, box[1] + box[3] / 2f, box[2] / 2f + 6f, box[3], Draw.withOpacity(ui.accent(), 0.25f));
		}
		HudTheme.panel(ui, box[0], box[1], box[2], box[3], radius, HudTheme.opacity());
		if (focused) {
			ui.ring(box[0], box[1], box[2], box[3], radius, 1, ui.accent());
		} else if (over) {
			ui.ring(box[0], box[1], box[2], box[3], radius, 1, Draw.withOpacity(HudTheme.text(), 0.25f));
		}
		int middle = box[1] + box[3] / 2;
		ui.icon("search", box[0] + 9, middle - 5, 10, HudTheme.secondary());
		boolean empty = text.isEmpty();
		int rightRoom = 0;
		if (!empty) {
			int[] clear = clearBox(box);
			boolean onClear = inside(clear, mouseX, mouseY);
			if (onClear) {
				ui.discAt(clear[0] + clear[2] / 2f, clear[1] + clear[3] / 2f, clear[2], Draw.withOpacity(HudTheme.text(), 0.15f));
			}
			ui.icon("close", clear[0] + 3, clear[1] + 3, clear[2] - 6, onClear ? HudTheme.text() : HudTheme.secondary());
			rightRoom = clear[2] + 8;
		}
		if (right != null && !right.isEmpty()) {
			float width = ui.widthScaled(right, TextScale.SMALL);
			float x = box[0] + box[2] - 8 - rightRoom - width;
			if (empty) {
				// a key hint: in a small key cap
				ui.roundedSmooth(x - 4, middle - 6f, width + 8, 12f, Radius.XS, Draw.withOpacity(HudTheme.text(), 0.1f));
			}
			ui.textAtScaled(right, x, middle - 3.5f, TextScale.SMALL, HudTheme.secondary());
			rightRoom += Math.round(width) + 10;
		}
		int textX = box[0] + 23;
		int room = box[2] - 23 - 8 - rightRoom;
		String shown = empty && !focused ? placeholder : text;
		String fitted = ui.fit(shown, room);
		ui.textMiddle(fitted, textX, middle, empty ? HudTheme.secondary() : HudTheme.text());
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(textX + (empty ? 0 : ui.width(fitted)) + 1, box[1] + 5, 1, box[3] - 10, HudTheme.text());
		}
	}

	/** @return the width of a segmented control with these labels */
	public static int segmentedWidth(Canvas ui, String[] labels) {
		int width = 4;
		for (String label : labels) {
			width += Math.round(ui.widthScaled(label, TextScale.SMALL)) + 2 * SEGMENT_PAD;
		}
		return width;
	}

	/**
	 * Draws a segmented control: a track with the selected part on a raised pill, as the menu's.
	 *
	 * @param boxes filled with each part's box, for clicks
	 */
	public static void segmented(Canvas ui, int x, int y, String[] labels, int selected, int mouseX, int mouseY, int[][] boxes) {
		int width = segmentedWidth(ui, labels);
		ui.rounded(x, y, width, SEGMENT_HEIGHT, Radius.MD, Draw.withOpacity(HudTheme.text(), 0.07f));
		int cursor = x + 2;
		for (int i = 0; i < labels.length; i++) {
			int part = Math.round(ui.widthScaled(labels[i], TextScale.SMALL)) + 2 * SEGMENT_PAD;
			boxes[i] = new int[]{cursor, y + 2, part, SEGMENT_HEIGHT - 4};
			boolean on = i == selected;
			if (on) {
				ui.rounded(cursor, y + 2, part, SEGMENT_HEIGHT - 4, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.16f));
			} else if (inside(boxes[i], mouseX, mouseY)) {
				ui.rounded(cursor, y + 2, part, SEGMENT_HEIGHT - 4, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.07f));
			}
			if (on) {
				ui.textAtScaledBold(labels[i], cursor + SEGMENT_PAD, y + SEGMENT_HEIGHT / 2f - 3.5f, TextScale.SMALL, HudTheme.text());
			} else {
				ui.textAtScaled(labels[i], cursor + SEGMENT_PAD, y + SEGMENT_HEIGHT / 2f - 3.5f, TextScale.SMALL, HudTheme.secondary());
			}
			cursor += part;
		}
	}

	public static int chipWidth(Canvas ui, String label) {
		return Math.round(ui.widthScaled(label, TextScale.SMALL)) + 2 * CHIP_PAD;
	}

	/** Draws a filter chip: brighter and bold when selected, lighter when hovered. */
	public static void chip(Canvas ui, int[] box, String label, boolean selected, int mouseX, int mouseY) {
		boolean over = inside(box, mouseX, mouseY);
		float fill = selected ? 0.2f : over ? 0.12f : 0.07f;
		ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, Draw.withOpacity(HudTheme.text(), fill));
		float y = box[1] + box[3] / 2f - 3.5f;
		if (selected) {
			ui.textAtScaledBold(label, box[0] + CHIP_PAD, y, TextScale.SMALL, HudTheme.text());
		} else {
			ui.textAtScaled(label, box[0] + CHIP_PAD, y, TextScale.SMALL, over ? HudTheme.text() : HudTheme.secondary());
		}
	}

	/** Draws an inner card: a slightly lighter area with a fine edge; an accent edge for the one in use. */
	public static void card(Canvas ui, int x, int y, int width, int height, float accent) {
		ui.rounded(x, y, width, height, Radius.LG, Draw.withOpacity(HudTheme.text(), 0.045f));
		ui.ring(x, y, width, height, Radius.LG, 1, Draw.withOpacity(HudTheme.text(), 0.08f));
		if (accent > 0.01f) {
			ui.ring(x, y, width, height, Radius.LG, 1, Draw.withOpacity(ui.accent(), accent));
		}
	}

	/**
	 * Draws an item tile: its slot, a soft glow and a short line in the item's rarity color, the item as the game draws it, and its count. A
	 * hovered tile rises a little.
	 *
	 * @param rarity the rarity color, or 0 for none
	 * @param lift   how far it is hovered, from 0 to 1
	 * @param ring   the accent ring, from 0 to 1 (a search match, a pinned item)
	 * @param count  the count drawn at the bottom right, or null
	 */
	public static void tile(Canvas ui, GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int size, int rarity, float lift, float ring, String count) {
		float rise = LIFT * lift;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0f, -rise);
		ui.rounded(x, y, size, size, Radius.SM, Draw.mix(HudTheme.slot(), HudTheme.text(), 0.03f + 0.05f * lift));
		ui.ring(x, y, size, size, Radius.SM, 1, Draw.withOpacity(HudTheme.text(), 0.08f + 0.18f * lift));
		if (rarity != 0) {
			ui.softLight(x + size / 2f, y + size / 2f, size * GLOW, size * GLOW, Draw.withOpacity(rarity, 0.16f + 0.08f * lift));
			int line = Math.max(4, Math.round(size * RARITY_LINE));
			ui.roundedSmooth(x + (size - line) / 2f, y + size - 3.5f, line, 1.2f, 0.6f, Draw.withOpacity(rarity, 0.9f));
		}
		if (ring > 0.01f) {
			ui.ring(x, y, size, size, Radius.SM, 1, Draw.withOpacity(ui.accent(), ring));
		}
		if (!stack.isEmpty()) {
			float scale = Math.min(1.25f, (size - 4) / 16f);
			pose.pushMatrix();
			pose.translate(x + size / 2f - 8f * scale, y + size / 2f - 8f * scale - 1f);
			pose.scale(scale, scale);
			graphics.item(stack, 0, 0);
			pose.popMatrix();
		}
		if (count != null) {
			graphics.nextStratum();
			float countScale = 0.62f;
			float width = ui.boldWidthScaled(count, countScale);
			ui.textAtScaledBold(count, x + size - 2f - width + 0.5f, y + size - 7.5f + 0.5f, countScale, 0xC0000000);
			ui.textAtScaledBold(count, x + size - 2f - width, y + size - 7.5f, countScale, 0xFFFFFFFF);
		}
		pose.popMatrix();
	}

	/** @return a compact count: 64, 1.2k, 3m */
	public static String count(long value) {
		if (value >= 1_000_000) {
			return value / 1_000_000 + "m";
		}
		if (value >= 1_000) {
			return value >= 10_000 ? value / 1_000 + "k" : String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0);
		}
		return String.valueOf(value);
	}

	// ======================================================================================================== the panel family

	/** The family's glass corner radius, its padding at the sides, top and bottom, and the gap between a panel's sections. */
	public static final float RADIUS = 10f;
	public static final int PAD = 8;
	public static final int TOP = 8;
	public static final int BOTTOM = 9;
	public static final int GAP = 8;
	/** The header's icon tile; the content starts below the header and a gap. */
	public static final int TILE = 14;
	public static final int CONTENT_TOP = TOP + TILE + GAP;
	/** A simple row: its height, the gap between stacked rows, its corner radius, icon tile and the width of its name. */
	public static final int SIMPLE_ROW = 17;
	public static final int SIMPLE_GAP = 4;
	public static final float SIMPLE_RADIUS = Radius.SM;
	private static final int SIMPLE_TILE = 11;
	public static final int SIMPLE_NAME = 50;
	private static final int SIMPLE_VALUE = 26;
	/** A stat tile's height and a tag's height. */
	public static final int STAT = 22;
	public static final int TAG = 10;
	private static final int CHIP_SIDE = 4;
	/** A bar's height, and how strong its track is. */
	public static final float BAR = 2.5f;
	private static final float TRACK = 0.1f;
	/** The type: the title, a value on the right of the header, captions and small labels, and the hero numbers (large and medium). */
	public static final float TITLE = TextScale.BODY;
	public static final float META = TextScale.SMALL;
	public static final float SMALL = TextScale.SMALL;
	public static final float HERO = 2f;
	public static final float HERO_MEDIUM = 1.5f;

	/** The family's colors: ready and done, alerts and bosses, coins, and the cooldown blue. */
	public static final int GREEN = SystemColors.GREEN;
	public static final int GREEN_TEXT = 0xFF4BE07A;
	public static final int RED = SystemColors.RED;
	public static final int RED_TEXT = 0xFFFF6257;
	public static final int GOLD = 0xFFFFC93C;
	public static final int ORANGE = SystemColors.ORANGE;
	public static final int ORANGE_TEXT = 0xFFFFC56B;
	public static final int BLUE = 0xFF0A84FF;
	public static final int CYAN = 0xFF64D2FF;

	/** The header tile's colors, top and bottom, per panel. */
	public static final int[] TILE_PET = {0xFFFFC83D, 0xFFFF8A00};
	public static final int[] TILE_ABILITY = {SystemColors.GREEN, 0xFF0E8F3A};
	public static final int[] TILE_DEPLOYABLE = {0xFFFF9F0A, 0xFFC46A00};
	public static final int[] TILE_COMMISSIONS = {0xFF64D2FF, 0xFF0A84FF};
	public static final int[] TILE_PROFIT = {0xFFFFD34D, 0xFFE89A00};
	public static final int[] TILE_SEA = {0xFF40C8E0, 0xFF0A6CFF};
	public static final int[] TILE_SLAYER = {0xFFFF5A4E, 0xFFB3121F};
	public static final int[] TILE_GARDEN = {0xFF7EE06A, 0xFF2E9E3A};
	public static final int[] TILE_PURPLE = {0xFFBF5AF2, 0xFF7D2AE8};
	public static final int[] TILE_GRAY = {0xFF8E8E93, 0xFF48484A};

	/** @return a color readable on the panel's theme */
	public static int readable(int argb) {
		return HudTheme.readable(argb);
	}

	/**
	 * Draws a panel's header: the colored icon tile, the title and the quiet value on the right (with a green dot before it when live).
	 *
	 * @param meta      the value on the right, or null
	 * @param metaColor its color, or 0 for the quiet secondary color
	 * @param metaRoom  room kept free at the right for something drawn there instead (the mode switch in the editor), or 0
	 */
	public static void header(Canvas ui, int width, String icon, int[] tile, String title, String meta, int metaColor, boolean dot, float metaRoom) {
		float right = width - PAD - metaRoom;
		if (meta != null && !meta.isEmpty() && metaRoom <= 0f) {
			right = headerStatus(ui, right, meta, metaColor, dot);
		}
		headerTitle(ui, icon, tile, title, right);
	}

	/**
	 * Draws the header's quiet value on the right (the status), right-aligned at a place.
	 *
	 * @return where it starts on the left, its dot included
	 */
	public static float headerStatus(Canvas ui, float right, String meta, int metaColor, boolean dot) {
		float middle = TOP + TILE / 2f;
		float metaWidth = ui.widthScaled(meta, META);
		int color = metaColor == 0 ? HudTheme.secondary() : readable(metaColor);
		ui.textAtScaled(meta, right - metaWidth, middle - 3.4f, META, color);
		right -= metaWidth;
		if (dot) {
			ui.softLight(right - 5f, middle, 4f, Draw.withOpacity(GREEN, 0.5f));
			ui.discAt(right - 5f, middle, 3.5f, GREEN);
			right -= 9f;
		}
		return right;
	}

	/**
	 * Draws the header's icon tile and title, the title cut to end before a place.
	 *
	 * @return where the title ends on the right
	 */
	public static float headerTitle(Canvas ui, String icon, int[] tile, String title, float right) {
		ui.roundedGradient(PAD, TOP, TILE, TILE, Radius.XS, tile[0], tile[1]);
		ui.icon(icon, PAD + 3, TOP + 3, TILE - 6, 0xFFFFFFFF);
		float middle = TOP + TILE / 2f;
		int titleRoom = Math.round(right - (PAD + TILE + 5) - 4);
		String shown = ui.fit(title, titleRoom);
		ui.textAtScaledBold(shown, PAD + TILE + 5, middle - 3.6f, TITLE, HudTheme.text());
		return PAD + TILE + 5 + ui.boldWidthScaled(shown, TITLE);
	}

	/** A text cut to a width between words, remembered with the width it was cut for, as rows ask for it every frame. */
	private record WordFit(float room, float scale, String text) {
	}

	private static final Map<String, WordFit> WORD_FITS = new HashMap<>();
	private static final int MOST_WORD_FITS = 500;

	/**
	 * @return a name in bold cut to a width between words with an ellipsis, so no word is cut in the middle; only a first word longer than the
	 *         whole width is shortened
	 */
	public static String fitWords(Canvas ui, String text, float room, float scale) {
		WordFit known = WORD_FITS.get(text);
		if (known != null && known.room() == room && known.scale() == scale) {
			return known.text();
		}
		String fitted = text;
		if (ui.boldWidthScaled(text, scale) > room) {
			String[] words = text.split(" ");
			fitted = "";
			for (int count = words.length - 1; count >= 1; count--) {
				String candidate = String.join(" ", Arrays.copyOfRange(words, 0, count)) + "…";
				if (ui.boldWidthScaled(candidate, scale) <= room) {
					fitted = candidate;
					break;
				}
			}
			if (fitted.isEmpty()) {
				fitted = ui.fit(words[0], Math.round(room / scale));
			}
		}
		if (WORD_FITS.size() > MOST_WORD_FITS) {
			WORD_FITS.clear();
		}
		WORD_FITS.put(text, new WordFit(room, scale, fitted));
		return fitted;
	}

	/** @return the width of the mode switch shown in a panel's header in the HUD editor */
	public static float modeSwitchWidth(Canvas ui, String[] labels) {
		float width = 2f;
		for (String label : labels) {
			width += ui.widthScaled(label, SMALL) + 8f;
		}
		return width;
	}

	/**
	 * Draws the mode switch in a panel's header, right-aligned (only in the HUD editor, on the selected panel).
	 *
	 * @param boxes filled with each part's box in panel pixels: x, y, width, height
	 */
	public static void modeSwitch(Canvas ui, float right, String[] labels, int selected, float[][] boxes) {
		float width = modeSwitchWidth(ui, labels);
		float height = 11f;
		float x = right - width;
		float y = TOP + (TILE - height) / 2f;
		ui.roundedSmooth(x, y, width, height, 4f, Draw.withOpacity(HudTheme.text(), 0.08f));
		float cursor = x + 1f;
		for (int i = 0; i < labels.length; i++) {
			float part = ui.widthScaled(labels[i], SMALL) + 8f;
			if (i == selected) {
				ui.roundedSmooth(cursor, y + 1f, part, height - 2f, 3f, Draw.withOpacity(HudTheme.text(), 0.18f));
				ui.textAtScaledBold(labels[i], cursor + 4f, y + height / 2f - 2.7f, SMALL, HudTheme.text());
			} else {
				ui.textAtScaled(labels[i], cursor + 4f, y + height / 2f - 2.7f, SMALL, HudTheme.secondary());
			}
			boxes[i][0] = cursor;
			boxes[i][1] = y;
			boxes[i][2] = part;
			boxes[i][3] = height;
			cursor += part;
		}
	}

	/**
	 * Draws a progress ring: its faint track and the part done, with round caps, and a soft glow in its color when asked.
	 *
	 * @param fraction the part done, from 0 to 1
	 * @param glow     how strong the glow around it is, from 0 to 1
	 */
	public static void ring(GuiGraphicsExtractor graphics, Canvas ui, float centerX, float centerY, float diameter, float thickness, float fraction,
							int color, float glow) {
		float radius = diameter / 2f;
		if (glow > 0.01f) {
			ui.softLight(centerX, centerY, radius + 6f, Draw.withOpacity(color, 0.3f * glow));
		}
		ui.arc(centerX, centerY, radius, thickness, 0f, 1f, Draw.faded(Draw.withOpacity(HudTheme.text(), TRACK)));
		float done = Mth.clamp(fraction, 0f, 1f);
		if (done >= 0.999f) {
			ui.arc(centerX, centerY, radius, thickness, 0f, 1f, Draw.faded(color));
		} else if (done > 0.002f) {
			// the round caps reach past the ends: start and end inside them, so the arc covers exactly its share
			float caps = thickness / 2f / (float) (Math.PI * diameter);
			ui.arc(centerX, centerY, radius, thickness, caps, Math.max(caps + 0.001f, done - caps), Draw.faded(color));
		}
	}

	/** Draws a bar: its faint track and the part done in a color. */
	public static void bar(Canvas ui, float x, float y, float width, float height, float fraction, int color) {
		ui.roundedSmooth(x, y, width, height, height / 2f, Draw.withOpacity(HudTheme.text(), TRACK));
		float done = Mth.clamp(fraction, 0f, 1f);
		if (done > 0.001f) {
			ui.roundedSmooth(x, y, Math.max(height, width * done), height, height / 2f, color);
		}
	}

	private static final int TAG_BACKING = 0x80000000;

	/** @return the width of a tag (a small chip) with this text */
	public static float tagWidth(Canvas ui, String text) {
		return ui.boldWidthScaled(text, SMALL) + 2 * CHIP_SIDE;
	}

	/** Draws a tag: a small pill with a dark backing, tinted, with its text in the color, such as a rarity, a buff or a timer. @return its width */
	public static float tag(Canvas ui, String text, float x, float y, int color) {
		float width = tagWidth(ui, text);
		// the backing keeps a dark red or gold readable over any panel and any world
		ui.roundedSmooth(x, y, width, TAG, 3f, TAG_BACKING);
		ui.roundedSmooth(x, y, width, TAG, 3f, Draw.withOpacity(color, 0.16f));
		ui.textAtScaledBold(text, x + CHIP_SIDE, y + TAG / 2f - 2.7f, SMALL, readable(color));
		return width;
	}

	private static final float STAT_LABEL_SIDES = 8f;

	/** Draws a stat tile: a soft box with a small label over a bold value. */
	public static void stat(Canvas ui, float x, float y, float width, String label, String value) {
		ui.roundedSmooth(x, y, width, STAT, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.05f));
		// a label is short and whole at every width: a narrow tile sets it a little smaller rather than cutting it
		float labelRoom = width - STAT_LABEL_SIDES;
		float labelSize = Math.min(SMALL, labelRoom / Math.max(1f, ui.widthScaled(label, 1f)));
		ui.textAtScaled(label, x + 5f, y + 3.5f, labelSize, HudTheme.secondary());
		ui.textAtScaledBold(ui.fit(value, Math.round(width - 8f)), x + 5f, y + 12f, TextScale.BODY, HudTheme.text());
	}

	/** Draws a thin line across a panel, between its sections. */
	public static void divider(Canvas ui, int width, float y) {
		ui.roundedSmooth(PAD, y, width - 2 * PAD, 1f, 0.5f, HudTheme.line(0.08f));
	}

	/** Draws a small spaced label in capitals over a section ("TOP DROPS"). */
	public static void sectionLabel(Canvas ui, String text, float x, float y) {
		ui.sectionLabel(text, x, y, HudTheme.secondary());
	}

	/** Draws a hero number in a color, with a soft glow of that color behind it when asked. */
	public static void hero(Canvas ui, String text, float x, float y, float scale, int color, boolean glow) {
		if (glow) {
			float width = ui.boldWidthScaled(text, scale);
			ui.softLight(x + width / 2f, y + 4f * scale, width / 2f + 8f, 6f * scale, Draw.withOpacity(color, 0.22f));
		}
		ui.textAtScaledBold(text, x, y, scale, readable(color));
	}

	/**
	 * Draws a simple row, the same in every panel so rows stack cleanly: its own glass, an icon tile, the name, a bar and the value at the right.
	 *
	 * @param glass    whether the row draws its glass (off when the panel has no background)
	 * @param fraction the bar's part done, or a negative number for no bar
	 */
	public static void simpleRow(Canvas ui, int width, float y, boolean glass, String icon, int[] tile, String name, float fraction, int barColor,
								 String value, int valueColor) {
		if (glass) {
			HudTheme.panel(ui, 0, Math.round(y), width, SIMPLE_ROW, SIMPLE_RADIUS, HudTheme.opacity());
		}
		int tileY = Math.round(y + (SIMPLE_ROW - SIMPLE_TILE) / 2f);
		ui.roundedGradient(3, tileY, SIMPLE_TILE, SIMPLE_TILE, 3f, tile[0], tile[1]);
		ui.icon(icon, 5, tileY + 2, SIMPLE_TILE - 4, 0xFFFFFFFF);
		float nameX = 3 + SIMPLE_TILE + 5;
		float valueWidth = Math.max(SIMPLE_VALUE, ui.boldWidthScaled(value, META));
		boolean withBar = fraction >= 0f;
		float nameRoom = withBar ? SIMPLE_NAME : width - nameX - valueWidth - 10f;
		ui.textAtScaledBold(ui.fit(name, Math.round(nameRoom / META)), nameX, y + SIMPLE_ROW / 2f - 3.2f, META, HudTheme.text());
		float valueX = width - 5f - ui.boldWidthScaled(value, META);
		ui.textAtScaledBold(value, valueX, y + SIMPLE_ROW / 2f - 3.2f, META, readable(valueColor));
		if (withBar) {
			float barX = nameX + SIMPLE_NAME + 4f;
			float barWidth = width - 5f - valueWidth - 5f - barX;
			if (barWidth > 6f) {
				bar(ui, barX, y + SIMPLE_ROW / 2f - BAR / 2f, barWidth, BAR, fraction, barColor);
			}
		}
	}

	/** @return the height of a stack of simple rows */
	public static int simpleHeight(int rows) {
		return rows <= 0 ? 0 : rows * SIMPLE_ROW + (rows - 1) * SIMPLE_GAP;
	}

	// ------------------------------------------------------------------------------------------------------------ numbers

	/** @return a whole number with thousands separators: 4,821,330 */
	public static String grouped(long value) {
		String digits = Long.toString(Math.abs(value));
		StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3 + 1);
		if (value < 0) {
			out.append('-');
		}
		int first = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
		out.append(digits, 0, first);
		for (int i = first; i < digits.length(); i += 3) {
			out.append(',').append(digits, i, i + 3);
		}
		return out.toString();
	}

	/** @return a short number: 214, 1.8k, 34.2k, 860k, 5.33M, 1.20B */
	public static String compact(double value) {
		double size = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (size < 1_000) {
			return sign + Math.round(size);
		}
		if (size < 99_950) {
			return sign + decimals(size / 1_000, 1) + "k";
		}
		if (size < 999_500) {
			return sign + Math.round(size / 1_000) + "k";
		}
		if (size < 999_995_000) {
			return sign + decimals(size / 1_000_000, 2) + "M";
		}
		return sign + decimals(size / 1_000_000_000, 2) + "B";
	}

	/** @return a positive number with a fixed count of decimals (1 or 2), without String.format */
	public static String decimals(double value, int places) {
		long factor = places == 1 ? 10 : 100;
		long whole = Math.round(Math.abs(value) * factor);
		long rest = whole % factor;
		String fraction = places == 2 && rest < 10 ? "0" + rest : Long.toString(rest);
		return (value < 0 ? "-" : "") + whole / factor + "." + fraction;
	}

	/** @return a time as minutes and seconds, or hours, minutes and seconds: 2:41, 1:02:03 */
	public static String clock(long seconds) {
		long total = Math.max(0, seconds);
		long hours = total / 3600;
		long minutes = total / 60 % 60;
		long rest = total % 60;
		String tail = (rest < 10 ? ":0" : ":") + rest;
		if (hours > 0) {
			return hours + (minutes < 10 ? ":0" : ":") + minutes + tail;
		}
		return minutes + tail;
	}
}
