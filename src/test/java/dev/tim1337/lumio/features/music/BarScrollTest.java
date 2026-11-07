package dev.tim1337.lumio.features.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The info bar's lyric scroll keeps the sung word around the middle: not at the start of a line, not past the end of a long one, never for a line
 * that fits; it does not overshoot, moves evenly through fast words, and is the same at any frame rate.
 */
class BarScrollTest {
	private static final float ROOM = 100f;
	private static final float TOTAL = 300f;
	private static final float WORD = 10f;

	@Test
	void aLineThatFitsDoesNotScroll() {
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		for (int i = 0; i < 120; i++) {
			assertEquals(0f, scroll.update(70f, 90f, 65f, 75f, 90f, ROOM, 1f / 60f, true));
		}
	}

	@Test
	void theStartOfALineStaysAtItsBeginning() {
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		assertEquals(0f, settle(scroll, 20f, 20f), 0.01f);
		assertEquals(0f, settle(scroll, 50f, 50f), 0.01f);
	}

	@Test
	void theBoxIsKeptInTheMiddle() {
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		assertEquals(150f - ROOM * BarScroll.CENTER, settle(scroll, 150f, 150f), 0.05f);
	}

	@Test
	void theEndOfALongLineDoesNotScrollFurther() {
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		assertEquals(TOTAL - ROOM, settle(scroll, 290f, 290f), 0.01f);
	}

	@Test
	void noOvershoot() {
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		settle(scroll, 60f, 60f);
		float wanted = 160f - ROOM * BarScroll.CENTER;
		float last = scroll.scroll();
		for (int i = 0; i < 600; i++) {
			float now = scroll.update(160f, 160f, 160f - WORD, 160f + WORD, TOTAL, ROOM, 1f / 144f, true);
			assertTrue(now <= wanted + 0.001f, "overshoot: " + now);
			assertTrue(now >= last - 0.001f, "moved back: " + now);
			last = now;
		}
	}

	@Test
	void fastWordsScrollEvenly() {
		// a rap-like part: a new word every 90 ms, 14 pixels apart; the scroll never jumps between two frames
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		float step = 1f / 60f;
		float last = 0f;
		float biggest = 0f;
		for (int frame = 0; frame < 180; frame++) {
			float time = frame * step;
			float word = 5f + 14f * (int) (time / 0.09f);
			float ahead = 5f + 14f * (int) ((time + 0.45f) / 0.09f);
			float now = scroll.update(word, ahead, word - WORD, word + WORD, 2000f, ROOM, step, true);
			biggest = Math.max(biggest, now - last);
			last = now;
		}
		assertTrue(biggest < 4f, "a jump of " + biggest + " pixels");
	}

	@Test
	void fastWordsKeepTheBoxNearTheMiddle() {
		// the rap-like part of the game test: 150 pixels a second in words of about 30 pixels; the box does not drift to the right edge
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		float step = 1f / 60f;
		float sum = 0f;
		int counted = 0;
		for (int frame = 0; frame < 240; frame++) {
			float time = frame * step;
			float word = 5f + 30f * (int) (time / 0.2f) + 15f;
			float ahead = 5f + 150f * (time + 0.45f);
			float now = scroll.update(word, Math.max(word, ahead), word - 15f, word + 15f, 4000f, ROOM, step, true);
			if (frame >= 60) {
				sum += (word - now) / ROOM;
				counted++;
			}
		}
		float average = sum / counted;
		assertTrue(average > 0.4f && average < 0.6f, "the box stands at " + average + " of the room on average");
	}

	@Test
	void theSameAtAnyFrameRate() {
		BarScroll slow = new BarScroll();
		BarScroll fast = new BarScroll();
		slow.reset(5f);
		fast.reset(5f);
		for (int i = 0; i < 30; i++) {
			slow.update(180f, 200f, 170f, 190f, TOTAL, ROOM, 1f / 30f, true);
		}
		for (int i = 0; i < 240; i++) {
			fast.update(180f, 200f, 170f, 190f, TOTAL, ROOM, 1f / 240f, true);
		}
		assertEquals(fast.scroll(), slow.scroll(), 0.5f);
	}

	@Test
	void aStallDoesNotThrowItOff() {
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		float now = scroll.update(200f, 220f, 190f, 210f, TOTAL, ROOM, 2f, true);
		assertTrue(now >= 0f && now <= TOTAL - ROOM, "out of the line: " + now);
		assertTrue(Float.isFinite(now));
	}

	@Test
	void theBoxNeverLeavesTheRoom() {
		// a seek within the line: the box is far right at once, the scroll follows at least as far as it takes to show the box
		BarScroll scroll = new BarScroll();
		scroll.reset(5f);
		float now = scroll.update(260f, 260f, 250f, 270f, TOTAL, ROOM, 1f / 60f, true);
		assertTrue(270f - now <= ROOM, "the box is cut off at " + now);
	}

	private static float settle(BarScroll scroll, float center, float ahead) {
		float now = 0f;
		for (int i = 0; i < 600; i++) {
			now = scroll.update(center, ahead, center - WORD, center + WORD, TOTAL, ROOM, 1f / 60f, true);
		}
		return now;
	}
}
