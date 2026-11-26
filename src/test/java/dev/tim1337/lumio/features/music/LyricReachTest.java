package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The lyrics' words keep their size on the screen at any distance and field of view, by the size chosen; small words keep their share of it; the
 * block keeps to its share of the screen; and the distance goes by where the player is.
 */
class LyricReachTest {
	private static final double[] DISTANCES = {8, 32, 64, 112};
	/** Half of the cinematic camera's narrowest and widest shot, and of the game's usual and widest field of view. */
	private static final double[] HALF_FOVS = {23, 34, 35, 55};
	private static final int[] HEIGHTS = {720, 1080, 1440, 2160};
	private static final float EM_UNITS = 13.5f;

	@AfterEach
	void normalAgain() {
		LyricReach.use(LyricReach.Size.NORMAL);
	}

	private static double shown(float perBlock, double distance, double halfFov) {
		double em = EM_UNITS * perBlock * distance;
		return LyricReach.capShare(em * LyricReach.grow(em, distance, halfFov), distance, halfFov);
	}

	/** Normal: capitals 3.5 % of the screen's height; Small about 15 % smaller; Large the 4.5 % of before. A key word at most 5 % at Normal. */
	@Test
	void theSizesAreTheOnesChosen() {
		assertEquals(0.035f, LyricReach.targetCapShare(), 1e-6f);
		assertEquals(0.05f, LyricReach.mostCapShare() * LyricReach.KEY_OF_MAIN, 1e-6f, "a key word at the most size");
		assertTrue(LyricReach.targetCapShare() * LyricReach.KEY_OF_MAIN <= 0.05f, "a key word at the placed size");
		LyricReach.use(LyricReach.Size.SMALL);
		assertEquals(0.035f * 0.85f, LyricReach.targetCapShare(), 1e-6f);
		LyricReach.use(LyricReach.Size.LARGE);
		assertEquals(0.045f, LyricReach.targetCapShare(), 1e-6f);
	}

	@Test
	void linesArePlacedAtTheTargetSize() {
		for (LyricReach.Size size : LyricReach.Size.values()) {
			LyricReach.use(size);
			for (double halfFov : HALF_FOVS) {
				float perBlock = LyricReach.placed(EM_UNITS, halfFov);
				for (double distance : DISTANCES) {
					assertEquals(LyricReach.targetCapShare(), shown(perBlock, distance, halfFov), 1e-4,
						size + " at " + distance + " blocks, half field of view " + halfFov);
				}
			}
		}
		// placed so, a line needs no growing where it stands
		float fine = LyricReach.placed(EM_UNITS, 35);
		assertEquals(1f, LyricReach.grow(EM_UNITS * fine * 30, 30, 35));
	}

	@Test
	void aStandingLineKeepsBetweenTheLeastAndTheMostAsTheFieldOfViewChanges() {
		for (double distance : DISTANCES) {
			for (double placedFov : HALF_FOVS) {
				float placed = LyricReach.placed(EM_UNITS, placedFov);
				for (double halfFov : HALF_FOVS) {
					double shown = shown(placed, distance, halfFov);
					assertTrue(shown >= LyricReach.leastCapShare() - 1e-4, "at " + distance + " blocks, half field of view " + halfFov);
					assertTrue(shown <= LyricReach.mostCapShare() + 1e-4, "at " + distance + " blocks, half field of view " + halfFov);
				}
			}
		}
	}

	@Test
	void smallWordsKeepTheirShareAndTheirLeastPixels() {
		float main = CutoutWords.REGULAR;
		assertTrue(CutoutWords.SMALL >= LyricReach.SMALL_LEAST_SHARE * main, "small beside " + main);
		assertTrue(CutoutWords.ADLIB >= LyricReach.SMALL_LEAST_SHARE * main, "ad-lib beside " + main);
		assertTrue(CutoutWords.LEAST >= LyricReach.SMALL_LEAST_SHARE * main, "the least size beside " + main);
		for (LyricReach.Size size : LyricReach.Size.values()) {
			LyricReach.use(size);
			for (int height : HEIGHTS) {
				// the smallest words at the least size the main words are drawn at: about 16 px at 1080p, never under
				double small = LyricReach.leastCapShare() * height * CutoutWords.SMALL / main;
				assertTrue(small >= LyricReach.smallLeastPixels(height), size + " at a height of " + height + ": " + small + " px");
			}
		}
	}

	@Test
	void theGrowthHasALimit() {
		assertEquals(LyricReach.MOST_GROWTH, LyricReach.grow(0.001, 500, 35));
	}

	/** In open sky far away, whatever the render distance; before a far surface a gap nearer; indoors and in caves near; never nearer than 8 blocks. */
	/** The block is at most 40 % of the screen's height and 55 % of its width, at every size; the words never over their most. */
	@Test
	void theBlockKeepsToItsShareOfTheScreen() {
		float em = 40f;
		float column = CutoutLyrics.COLUMN;
		double aspect = 16.0 / 9.0;
		for (LyricReach.Size size : LyricReach.Size.values()) {
			LyricReach.use(size);
			for (double halfFovY : HALF_FOVS) {
				double tan = Math.tan(Math.toRadians(halfFovY));
				for (float perBlock = 0.0002f; perBlock < 0.02f; perBlock *= 1.3f) {
					float limited = LyricReach.limited(perBlock, em, column, halfFovY, aspect);
					assertTrue(limited <= perBlock, "only ever smaller");
					assertTrue(LyricReach.capShare(em * limited, 1.0, halfFovY) <= LyricReach.mostCapShare() + 1e-6, "capitals at most the most size");
					assertTrue(limited * column / (2.0 * tan * aspect) <= LyricReach.MOST_BLOCK_WIDTH + 1e-6, "words at most 55 % of the width");
				}
			}
		}
		assertEquals(0.4f, LyricReach.MOST_BLOCK_HEIGHT);
		assertEquals(0.55f, LyricReach.MOST_BLOCK_WIDTH);
	}
}
