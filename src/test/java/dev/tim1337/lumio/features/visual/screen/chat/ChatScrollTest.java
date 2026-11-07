package dev.tim1337.lumio.features.visual.screen.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatScrollTest {
	private static final float MOST = 100f;
	private static final float CLOSE = 0.0001f;
	private static final long START = 1_000_000_000L;

	@Test
	void oneStepIsThreeLines() {
		assertEquals(3f, ChatScroll.lines(1.0, false, 20), CLOSE);
		assertEquals(-3f, ChatScroll.lines(-1.0, false, 20), CLOSE);
	}

	@Test
	void aPartOfAStepGoesAsFarAsItsPart() {
		assertEquals(0.3f, ChatScroll.lines(0.1, false, 20), CLOSE);
	}

	@Test
	void shiftGoesAPage() {
		assertEquals(19f, ChatScroll.lines(1.0, true, 20), CLOSE);
		assertEquals(1, ChatScroll.pageLines(1));
	}

	@Test
	void oneStepEasesThereInAbout120Milliseconds() {
		ChatScroll scroll = new ChatScroll();
		scroll.add(3f, MOST, START);
		assertEquals(0f, scroll.step(START, MOST), CLOSE);
		float early = scroll.step(START + ChatScroll.EASE_NANOS / 4, MOST);
		float late = scroll.step(START + ChatScroll.EASE_NANOS * 3 / 4, MOST);
		assertTrue(early > 0f && early < late && late < 3f, early + " then " + late);
		assertTrue(early > 3f / 4f, "starts fast and slows down: " + early);
		assertEquals(3f, scroll.step(START + ChatScroll.EASE_NANOS, MOST), CLOSE);
		assertEquals(3f, scroll.step(START + 2 * ChatScroll.EASE_NANOS, MOST), CLOSE);
	}

	@Test
	void fastStepsAddUpWithoutAJump() {
		ChatScroll scroll = new ChatScroll();
		long now = START;
		float last = 0f;
		for (int i = 0; i < 5; i++) {
			scroll.add(3f, MOST, now);
			float at = scroll.position();
			assertTrue(at >= last, "never goes back: " + last + " then " + at);
			last = at;
			now += ChatScroll.EASE_NANOS / 3;
			at = scroll.step(now, MOST);
			assertTrue(at >= last, "never goes back: " + last + " then " + at);
			last = at;
		}
		assertTrue(last < 15f, "still on its way: " + last);
		assertEquals(15f, scroll.step(now + ChatScroll.EASE_NANOS, MOST), CLOSE);
	}

	@Test
	void stopsAtTheTopWithoutOvershoot() {
		ChatScroll scroll = new ChatScroll();
		scroll.add(9f, 10f, START);
		for (int i = 0; i < 4; i++) {
			scroll.add(3f, 10f, START + i * 1_000_000L);
		}
		for (long t = START; t < START + 2 * ChatScroll.EASE_NANOS; t += 5_000_000L) {
			assertTrue(scroll.step(t, 10f) <= 10f);
		}
		assertEquals(10f, scroll.position(), CLOSE);
		long later = START + 3 * ChatScroll.EASE_NANOS;
		scroll.add(-3f, 10f, later);
		assertEquals(7f, scroll.step(later + ChatScroll.EASE_NANOS, 10f), CLOSE);
	}

	@Test
	void stopsAtTheBottomWithoutOvershoot() {
		ChatScroll scroll = new ChatScroll();
		scroll.add(3f, MOST, START);
		scroll.step(START + ChatScroll.EASE_NANOS, MOST);
		long later = START + 2 * ChatScroll.EASE_NANOS;
		scroll.add(-30f, MOST, later);
		for (long t = later; t < later + 2 * ChatScroll.EASE_NANOS; t += 5_000_000L) {
			assertTrue(scroll.step(t, MOST) >= 0f);
		}
		assertEquals(0f, scroll.position(), CLOSE);
		assertEquals(0, scroll.line());
	}

	@Test
	void newLinesKeepTheView() {
		ChatScroll scroll = new ChatScroll();
		scroll.set(10f);
		scroll.linesAdded(2f);
		assertEquals(12f, scroll.position(), CLOSE);
		assertEquals(12f, scroll.step(START, MOST), CLOSE);
	}

	@Test
	void newLinesDuringAnEaseKeepItsDistance() {
		ChatScroll scroll = new ChatScroll();
		scroll.set(10f);
		scroll.add(3f, MOST, START);
		float before = scroll.step(START + ChatScroll.EASE_NANOS / 2, MOST);
		scroll.linesAdded(1f);
		assertEquals(before + 1f, scroll.position(), CLOSE);
		assertEquals(14f, scroll.step(START + ChatScroll.EASE_NANOS, MOST), CLOSE);
	}

	@Test
	void onTheWayToTheNewestItStillGoesThere() {
		ChatScroll scroll = new ChatScroll();
		scroll.set(2f);
		scroll.add(-3f, MOST, START);
		scroll.linesAdded(1f);
		assertEquals(0f, scroll.step(START + ChatScroll.EASE_NANOS, MOST), CLOSE);
	}

	@Test
	void fewerLinesPullItBack() {
		ChatScroll scroll = new ChatScroll();
		scroll.set(50f);
		assertEquals(20f, scroll.step(START, 20f), CLOSE);
	}

	@Test
	void wholeAndPartOfALine() {
		ChatScroll scroll = new ChatScroll();
		scroll.set(4.25f);
		assertEquals(4, scroll.line());
		assertEquals(0.25f, scroll.part(), 0.002f);
		scroll.set(4f);
		assertEquals(4, scroll.line());
		assertEquals(0f, scroll.part(), CLOSE);
		scroll.set(3.9995f);
		assertEquals(4, scroll.line());
		assertEquals(0f, scroll.part(), CLOSE);
	}

	@Test
	void theScrollBarGoesDirectly() {
		ChatScroll scroll = new ChatScroll();
		scroll.add(3f, MOST, START);
		scroll.set(ChatScroll.placeAt(90f, 0f, MOST, 180, 40));
		assertEquals(64.2857f, scroll.position(), 0.001f);
		assertEquals(64.2857f, scroll.step(START + 1, MOST), 0.001f);
	}

	@Test
	void theThumbStaysUnderTheMouse() {
		float place = ChatScroll.placeAt(100f, 10f, MOST, 180, 40);
		assertEquals(90f, ChatScroll.thumbFromBottom(place, MOST, 180, 40), 0.001f);
		assertEquals(0f, ChatScroll.placeAt(-20f, 10f, MOST, 180, 40), CLOSE);
		assertEquals(MOST, ChatScroll.placeAt(400f, 10f, MOST, 180, 40), CLOSE);
		assertEquals(0f, ChatScroll.placeAt(50f, 10f, MOST, 40, 40), CLOSE);
	}
}
