package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The sidebar highlight moves with its row while the list scrolls, and glides only when the selection changes. */
class SelectionGlideTest {
	private static final float FRAME = 1f / 60f;
	private static final int ROW = 28;

	/** Where a row is drawn: its content top minus the scroll, as the sidebar draws it. */
	private static int screenTop(float contentTop, int scroll) {
		return Math.round(contentTop) - scroll;
	}

	@Test
	void atAnyScrollTheBoxIsTheSelectedRow() {
		SelectionGlide glide = new SelectionGlide();
		int rowTop = 5 * ROW;
		for (int scroll = 0; scroll <= 400; scroll += 7) {
			glide.follow(5, rowTop, ROW, 0f, FRAME, true);
			assertFalse(glide.gliding());
			assertEquals(screenTop(rowTop, scroll), screenTop(glide.y(), scroll), "scrolled by " + scroll);
			assertEquals(ROW, Math.round(glide.height()));
		}
	}

	@Test
	void aChangeGlidesAndEndsOnTheNewRow() {
		SelectionGlide glide = new SelectionGlide();
		glide.follow(1, ROW, ROW, 0f, FRAME, true);
		glide.follow(6, 6 * ROW, ROW, 0f, FRAME, true);
		assertTrue(glide.gliding());
		assertEquals(ROW, glide.y(), 0.01f, "the first frame starts where the box was");
		glide.follow(6, 6 * ROW, ROW, 0f, SelectionGlide.SECONDS / 2f, true);
		assertTrue(glide.y() > ROW + (5 * ROW) / 2f, "eases out: past the middle at half time");
		glide.follow(6, 6 * ROW, ROW, 0f, SelectionGlide.SECONDS, true);
		assertFalse(glide.gliding());
		assertEquals(6 * ROW, glide.y());
	}

	@Test
	void theSameTimeAtAnyFrameRate() {
		SelectionGlide slow = new SelectionGlide();
		SelectionGlide fast = new SelectionGlide();
		slow.follow(0, 0, ROW, 0f, FRAME, true);
		fast.follow(0, 0, ROW, 0f, FRAME, true);
		slow.follow(4, 4 * ROW, ROW, 0f, 0f, true);
		fast.follow(4, 4 * ROW, ROW, 0f, 0f, true);
		for (int i = 0; i < 3; i++) {
			slow.follow(4, 4 * ROW, ROW, 0f, 0.025f, true);
		}
		for (int i = 0; i < 9; i++) {
			fast.follow(4, 4 * ROW, ROW, 0f, 0.025f / 3f, true);
		}
		assertEquals(slow.y(), fast.y(), 0.01f);
	}

	@Test
	void aRowThatMovesDuringTheGlideIsWhereItEnds() {
		SelectionGlide glide = new SelectionGlide();
		glide.follow(0, 0, ROW, 0f, FRAME, true);
		glide.follow(3, 3 * ROW, ROW, 12f, 0f, true);
		float top = 3 * ROW;
		for (float t = 0; t < SelectionGlide.SECONDS + FRAME; t += FRAME) {
			top += 2f; // a group above it opens
			glide.follow(3, top, ROW, 12f, FRAME, true);
		}
		assertEquals(top, glide.y());
		assertEquals(12f, glide.indent());
	}

	@Test
	void withoutAnimationsTheBoxJumps() {
		SelectionGlide glide = new SelectionGlide();
		glide.follow(0, 0, ROW, 0f, FRAME, false);
		glide.follow(5, 5 * ROW, ROW, 0f, FRAME, false);
		assertFalse(glide.gliding());
		assertEquals(5 * ROW, glide.y());
	}
}
