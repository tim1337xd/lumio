package dev.tim1337.lumio.features.visual.hud;

import java.util.Arrays;

/**
 * How the tab list is laid out, from its content and the room on the screen: the players in columns of equal length, about twelve rows each, the info cards beside them in columns of about the same length, or below them if the
 * screen is too narrow, and no taller than the room: past that the player rows scroll. The window is as big as this and no bigger. Plain numbers, so it is unit tested.
 */
public final class TabLayout {
	/** The sizes that go in, in panel pixels. */
	public record Sizes(int pad, int cellWidth, int cellHeight, int cellGap, int section, int areaGap, int cardWidth, int cardGap, int headerAndFooter) {
	}

	/**
	 * The layout.
	 * @param columns       player columns
	 * @param rows          player rows per column
	 * @param visibleRows   those shown at a time; fewer than rows, and they scroll
	 * @param cardColumns   info card columns, 0 without cards
	 * @param cardsBelow    whether the cards lie below the players, not beside them
	 * @param cardsHeight   the height of the cards with their heading
	 * @param contentWidth  the width of players and cards
	 * @param bodyHeight    the height of players and cards
	 */
	public record Plan(int columns, int rows, int visibleRows, int cardColumns, boolean cardsBelow, int cardsHeight, int contentWidth, int bodyHeight) {
	}

	/** Where the cards go, each to the column that is shortest so far: the column and the top of each, and the height of the tallest column. */
	public record Masonry(int[] column, int[] top, int height) {
	}

	public static final int MOST_ROWS = 12;
	private static final int LEAST_VISIBLE_ROWS = 3;
	private static final int MOST_CARD_COLUMNS = 4;
	/** The sizes the list may be drawn at, from its own. */
	private static final float[] SIZES = {1f, 0.85f, 0.7f, 0.6f, 0.5f};
	/** Cards a little longer than the players are fine, in fifths of their length: one column less keeps the window smaller. */
	private static final int CARDS_MAY_BE_LONGER_FIFTHS = 6;

	private TabLayout() {
	}

	/** @return where cards of these heights go in a number of columns */
	public static Masonry masonry(int[] heights, int columns, int gap) {
		int[] bottoms = new int[columns];
		int[] column = new int[heights.length];
		int[] top = new int[heights.length];
		for (int i = 0; i < heights.length; i++) {
			int shortest = 0;
			for (int c = 1; c < columns; c++) {
				if (bottoms[c] < bottoms[shortest]) {
					shortest = c;
				}
			}
			column[i] = shortest;
			top[i] = bottoms[shortest];
			bottoms[shortest] += heights[i] + gap;
		}
		int tallest = Arrays.stream(bottoms).max().orElse(0);
		return new Masonry(column, top, Math.max(0, tallest - gap));
	}

	/**
	 * @param players     how many players there are
	 * @param cards       the heights of the info cards
	 * @param roomWidth   the most the window may be wide
	 * @param roomHeight  the most the window may be high
	 * @param keepColumns while the list is open: the columns it has, so it does not jump, or 0 to choose
	 * @param keepRows    while the list is open: the rows it has
	 */
	public static Plan plan(int players, int[] cards, Sizes sizes, int roomWidth, int roomHeight, int keepColumns, int keepRows) {
		int count = Math.max(1, players);
		int inner = roomWidth - 2 * sizes.pad();
		int fit = Math.max(1, (inner + sizes.cellGap()) / (sizes.cellWidth() + sizes.cellGap()));
		int wanted = Math.max(1, (count + MOST_ROWS - 1) / MOST_ROWS);
		int from = keepColumns > 0 ? keepColumns : 1;
		int to = keepColumns > 0 ? keepColumns : Math.min(fit, wanted);
		Plan best = null;
		long bestCost = Long.MAX_VALUE;
		for (int columns = from; columns <= to; columns++) {
			int rows = Math.max(keepRows, (count + columns - 1) / columns);
			for (Plan plan : placements(columns, rows, cards, sizes, inner, roomHeight)) {
				long cost = cost(plan, sizes, roomHeight);
				if (cost < bestCost) {
					bestCost = cost;
					best = plan;
				}
			}
		}
		return best;
	}

	/**
	 * @return the layout at the largest size that needs no scrolling: on a small screen, such as a large GUI scale, the list is laid out for more room and drawn smaller; only if it does not fit even at the smallest size do its rows scroll
	 */
	public static Plan planSmallest(int players, int[] cards, Sizes sizes, int roomWidth, int roomHeight) {
		Plan plan = null;
		for (float size : SIZES) {
			plan = plan(players, cards, sizes, Math.round(roomWidth / size), Math.round(roomHeight / size), 0, 0);
			if (plan.visibleRows() == plan.rows() && plan.bodyHeight() + sizes.headerAndFooter() + 2 * sizes.pad() <= roomHeight / size) {
				return plan;
			}
		}
		return plan;
	}

	/** @return how bad a layout is, the worst first: taller than the room, rows that scroll, longer columns than wanted, cards below, and then its size */
	private static long cost(Plan plan, Sizes sizes, int roomHeight) {
		long tooTall = Math.max(0, plan.bodyHeight() + sizes.headerAndFooter() + 2 * sizes.pad() - roomHeight);
		long scrolled = plan.rows() - plan.visibleRows();
		long longer = Math.max(0, plan.rows() - MOST_ROWS);
		return tooTall * 1_000_000_000L + scrolled * 10_000_000L + longer * 100_000L + (plan.cardsBelow() ? 50_000L : 0L) + plan.bodyHeight() * 10L + plan.contentWidth() / 10;
	}

	private static Plan[] placements(int columns, int rows, int[] cards, Sizes s, int inner, int roomHeight) {
		int playersWidth = columns * s.cellWidth() + (columns - 1) * s.cellGap();
		if (cards.length == 0) {
			return new Plan[]{plan(columns, rows, 0, false, 0, playersWidth, s, roomHeight)};
		}
		int playersHeight = s.section() + rows * (s.cellHeight() + s.cellGap()) - s.cellGap();
		// beside: the fewest columns that are about as long as the players; if none, the lowest
		int beside = 0;
		int lowest = Integer.MAX_VALUE;
		for (int k = 1; k <= Math.min(MOST_CARD_COLUMNS, cards.length); k++) {
			if (playersWidth + s.areaGap() + cardsWidth(k, s) > inner) {
				break;
			}
			int height = s.section() + masonry(cards, k, s.cardGap()).height();
			if (height < lowest) {
				lowest = height;
				beside = k;
			}
			if (height <= playersHeight * CARDS_MAY_BE_LONGER_FIFTHS / 5) {
				beside = k;
				break;
			}
		}
		// below: as many as fit, at least as wide as the players where they can be
		int below = Math.max(1, Math.min(Math.min(MOST_CARD_COLUMNS, cards.length), (inner + s.cardGap()) / (s.cardWidth() + s.cardGap())));
		int belowHeight = s.section() + masonry(cards, below, s.cardGap()).height();
		Plan under = plan(columns, rows, below, true, belowHeight, Math.max(playersWidth, cardsWidth(below, s)), s, roomHeight);
		if (beside == 0) {
			return new Plan[]{under};
		}
		int besideHeight = s.section() + masonry(cards, beside, s.cardGap()).height();
		return new Plan[]{plan(columns, rows, beside, false, besideHeight, playersWidth + s.areaGap() + cardsWidth(beside, s), s, roomHeight), under};
	}

	private static int cardsWidth(int columns, Sizes s) {
		return columns * s.cardWidth() + (columns - 1) * s.cardGap();
	}

	private static Plan plan(int columns, int rows, int cardColumns, boolean below, int cardsHeight, int contentWidth, Sizes s, int roomHeight) {
		int room = roomHeight - s.headerAndFooter() - 2 * s.pad() - s.section();
		if (below) {
			room -= s.areaGap() + cardsHeight;
		}
		int fits = Math.max(LEAST_VISIBLE_ROWS, (room + s.cellGap()) / (s.cellHeight() + s.cellGap()));
		int visible = Math.min(rows, fits);
		int playersHeight = s.section() + visible * (s.cellHeight() + s.cellGap()) - s.cellGap();
		int body = below ? playersHeight + s.areaGap() + cardsHeight : Math.max(playersHeight, cardsHeight);
		return new Plan(columns, rows, visible, cardColumns, below, cardsHeight, contentWidth, body);
	}
}
