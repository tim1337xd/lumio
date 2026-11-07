package dev.tim1337.lumio.features.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The info bar's next line preview and the highlight box's motion. */
class BarMotionTest {
	private static final float ROOM = 200f;
	private static final float FRAME = 1f / 60f;

	/** @return where each character of a text starts, at a fixed width per character, with its end last */
	private static float[] glyphs(String text) {
		float[] x = new float[text.length() + 1];
		for (int i = 0; i <= text.length(); i++) {
			x[i] = i * 5f;
		}
		return x;
	}

	private static int fit(String text, float room) {
		return NextLinePreview.fit(text, glyphs(text), text.length(), room);
	}

	@Test
	void aLineAheadIsWholeOrInWholeWords() {
		String line = "bring the money";
		assertEquals(line.length(), fit(line, 75f), "it fits exactly");
		assertEquals(line.length(), fit(line, 500f));
		assertEquals("bring the".length(), fit(line, 74f), "no word is cut off");
		assertEquals("bring the".length(), fit(line, 45f));
		assertEquals("bring".length(), fit(line, 44f));
		assertEquals("bring".length(), fit(line, 25f));
	}

	@Test
	void aLineWhoseFirstWordDoesNotFitIsLeftOut() {
		assertEquals(0, fit("bring the money", 24f));
		assertEquals(0, fit("bring", 24f));
		assertEquals(0, fit("bring", 0f));
	}

	@Test
	void theFartherAheadTheDimmer() {
		for (int place = 1; place < NextLinePreview.MOST_LINES; place++) {
			assertTrue(NextLinePreview.alpha(place) < NextLinePreview.alpha(place - 1));
		}
		assertEquals(NextLinePreview.ALPHA, NextLinePreview.alpha(0));
	}

	@Test
	void onlyWordsHeldLongFillUp() {
		assertEquals(0f, HighlightBox.longWord(200L));
		assertEquals(0f, HighlightBox.longWord(500L));
		assertEquals(1f, HighlightBox.longWord(900L));
		assertEquals(1f, HighlightBox.longWord(4000L));
		assertEquals(0.5f, HighlightBox.longWord(700L), 0.001f);
	}

	/** Moves a box across words of a line at 60 frames per second and checks every frame. */
	private static void glidesCleanly(boolean glide, float[] lefts, float[] rights, int framesPerWord) {
		LyricBox box = new LyricBox();
		box.follow(0, lefts[0], rights[0], FRAME, true, glide);
		float lastLeft = box.left();
		float lastRight = box.right();
		float most = 0f;
		for (int word = 0; word < lefts.length; word++) {
			for (int frame = 0; frame < framesPerWord; frame++) {
				box.follow(0, lefts[word], rights[word], FRAME, true, glide);
				// only forward, never past the word it moves to, and no jump
				assertTrue(box.left() >= lastLeft - 1e-4f, "the left edge went back");
				assertTrue(box.right() >= lastRight - 1e-4f, "the right edge went back");
				assertTrue(box.left() <= lefts[word] + 1e-4f, "the left edge overshot");
				assertTrue(box.right() <= rights[word] + 1e-4f, "the right edge overshot");
				most = Math.max(most, Math.max(box.left() - lastLeft, box.right() - lastRight));
				lastLeft = box.left();
				lastRight = box.right();
			}
		}
		// continuous: no frame moves further than a part of the longest step between words
		assertTrue(most < 12f, "a jump of " + most);
	}

	@Test
	void theBoxGlidesFromWordToWordWithoutOvershoot() {
		// words of different widths, a short and fast one among them
		float[] lefts = {0f, 22f, 30f, 70f, 76f, 120f};
		float[] rights = {20f, 28f, 66f, 74f, 116f, 140f};
		glidesCleanly(false, lefts, rights, 12);
		glidesCleanly(false, lefts, rights, 3);
	}

	@Test
	void withoutWordTimingItGlidesEvenly() {
		float[] lefts = new float[40];
		float[] rights = new float[40];
		for (int i = 0; i < lefts.length; i++) {
			lefts[i] = i * 2.5f;
			rights[i] = i * 2.5f + 18f;
		}
		glidesCleanly(true, lefts, rights, 4);
	}
}
