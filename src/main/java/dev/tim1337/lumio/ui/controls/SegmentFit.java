package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.TextScale;

/** How the parts of a segmented control share its width: each as wide as its text plus padding, and never narrower than that. */
public final class SegmentFit {
	private SegmentFit() {
	}

	/**
	 * @param room the width left for the text, without the room around it
	 * @param text the width of all the text at its usual size
	 * @return the text's scale: its usual size when it fits, smaller only as far as needed
	 */
	static float scale(float room, float text) {
		if (text <= room) {
			return TextScale.BODY;
		}
		return text <= 0f ? TextScale.BODY : Math.max(0f, room) / text * TextScale.BODY;
	}

	/**
	 * @param texts   the width of each part's text, measured as it is drawn at its widest (bold)
	 * @param inset   the room between the control's edge and its first and last part
	 * @param padding the room on each side of a text
	 * @return the width the control needs with this padding
	 */
	public static int width(int[] texts, int inset, int padding) {
		int total = 2 * inset;
		for (int text : texts) {
			total += text + 2 * padding;
		}
		return total;
	}

	/** @return whether all the text fits a control of this width with at least the given padding; if not, the control must not show its parts side by side */
	public static boolean fits(int width, int[] texts, int inset, int leastPadding) {
		return width(texts, inset, leastPadding) <= width;
	}

	/**
	 * The width a control asks of its row: its usual one when there is room, all the room when it fits only with less padding, and otherwise what
	 * its dropdown needs.
	 *
	 * @param available the width of the row
	 * @param usual     the width with the usual padding
	 * @param least     the width with the least padding
	 * @param dropdown  the width its dropdown would like
	 */
	static int wanted(int available, int usual, int least, int dropdown) {
		if (usual <= available) {
			return usual;
		}
		return least <= available ? available : Math.min(available, dropdown);
	}

	/**
	 * Lays out the parts: each gets its text and an equal share of the room left, so a label is centered on its part and never reaches the next.
	 *
	 * @param left  the control's left edge
	 * @param width the control's width
	 * @param texts the width of each part's text
	 * @param inset the room between the control's edge and its first and last part
	 * @param edges receives the edges of the parts, one more than there are parts
	 */
	public static void edges(int left, int width, int[] texts, int inset, int[] edges) {
		int count = texts.length;
		int text = 0;
		for (int part : texts) {
			text += part;
		}
		int extra = Math.max(0, width - 2 * inset - text);
		int cursor = left + inset;
		edges[0] = cursor;
		for (int i = 0; i < count; i++) {
			cursor += texts[i];
			// shared out by position, so rounding adds up to exactly the room there is
			edges[i + 1] = cursor + extra * (i + 1) / Math.max(1, count);
		}
	}
}
