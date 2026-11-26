package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** What lies behind a line is taken at its start, followed while it stands and blended in softly; small changes leave the tones as they are. */
class LyricBackdropTest {
	private static final int N = CutoutWords.Tone.values().length;
	private static final int GOLD = 0xE8B040;

	@Test
	void theFirstBackgroundIsThereAtOnce() {
		LyricBackdrop backdrop = new LyricBackdrop();
		backdrop.take(0.7f, 0.7f, 0f, true);
		assertEquals(0.7f, backdrop.darkest(), 1e-6f);
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		int[] expected = new int[N];
		CutoutWords.tones(GOLD, GOLD, 0.7f, 0f, expected, new int[N]);
		assertArrayEquals(expected, tones);
	}

	@Test
	void aNewBackgroundIsBlendedInAndThenStays() {
		LyricBackdrop backdrop = new LyricBackdrop();
		backdrop.take(0.02f, 0.02f, 0f, true);
		int[] night = new int[N];
		int[] day = new int[N];
		CutoutWords.tones(GOLD, GOLD, 0.02f, 0f, night, new int[N]);
		CutoutWords.tones(GOLD, GOLD, 0.7f, 0f, day, new int[N]);
		backdrop.take(0.7f, 0.7f, 0f, true);
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		assertArrayEquals(night, tones, "at the new line's start the tones are still the old ones");
		backdrop.step(LyricBackdrop.BLEND_SECONDS / 2f);
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		assertNotEquals(night[CutoutWords.Tone.WORD.ordinal()], tones[CutoutWords.Tone.WORD.ordinal()], "halfway");
		assertNotEquals(day[CutoutWords.Tone.WORD.ordinal()], tones[CutoutWords.Tone.WORD.ordinal()]);
		backdrop.step(LyricBackdrop.BLEND_SECONDS);
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		assertArrayEquals(day, tones, "then the new ones, which stay");
		backdrop.step(10f);
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		assertArrayEquals(day, tones);
	}

	@Test
	void withoutAnimationsTheNewOnesAreThereAtOnce() {
		LyricBackdrop backdrop = new LyricBackdrop();
		backdrop.take(0.02f, 0.02f, 0f, false);
		backdrop.take(0.7f, 0.7f, 0f, false);
		assertEquals(0.7f, backdrop.darkest(), 1e-6f);
	}

	private static final float LARGE = LyricTones.LARGE_CONTRAST;

	@Test
	void aSmallChangeIsNotFollowedAndCannotFlipTheTones() {
		LyricBackdrop backdrop = new LyricBackdrop();
		backdrop.take(0.25f, 0.25f, 0f, true);
		// wavering around a value, as a sky near a border between two tones does
		for (int i = 0; i < 20; i++) {
			float wave = i % 2 == 0 ? 0.27f : 0.23f;
			assertEquals(false, backdrop.follow(wave, wave, 0f, LARGE, LARGE, true), "wave " + i);
		}
		assertEquals(0.25f, backdrop.darkest(), 1e-6f);
	}

	@Test
	void aRealChangeIsFollowedSoftly() {
		LyricBackdrop backdrop = new LyricBackdrop();
		backdrop.take(0.02f, 0.02f, 0f, true);
		assertEquals(true, backdrop.follow(0.7f, 0.7f, 0f, LARGE, LARGE, true));
		assertEquals(0.02f, backdrop.darkest(), 1e-6f);
		backdrop.step(LyricBackdrop.BLEND_SECONDS);
		assertEquals(0.7f, backdrop.darkest(), 1e-6f);
	}

	@Test
	void aChangeDuringABlendGoesOnFromWhatIsShown() {
		LyricBackdrop backdrop = new LyricBackdrop();
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		int[] after = new int[N];
		backdrop.take(0.02f, 0.02f, 0f, true);
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		backdrop.take(0.7f, 0.7f, 0f, true);
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		backdrop.step(LyricBackdrop.BLEND_SECONDS / 2f);
		backdrop.cutout(GOLD, GOLD, tones, dimmed);
		backdrop.take(0.3f, 0.3f, 0f, true);
		backdrop.cutout(GOLD, GOLD, after, dimmed);
		assertArrayEquals(tones, after, "no jump");
	}

	@Test
	void aLineOfTheColumnTakesTheToneBlendAsItIs() {
		LyricBackdrop first = new LyricBackdrop();
		LyricBackdrop second = new LyricBackdrop();
		int[] a = new int[N];
		int[] b = new int[N];
		int[] dimmed = new int[N];
		first.take(0.02f, 0.02f, 0f, true);
		first.cutout(GOLD, GOLD, a, dimmed);
		first.take(0.7f, 0.7f, 0f, true);
		first.step(LyricBackdrop.BLEND_SECONDS / 3f);
		first.cutout(GOLD, GOLD, a, dimmed);
		second.copy(first);
		second.cutout(GOLD, GOLD, b, dimmed);
		assertArrayEquals(a, b);
	}
}
