package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.BarAdlibs.Mode;
import dev.tim1337.lumio.features.music.BarAdlibs.Shows;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Ad-libs in the info bar's one line: the three choices, an ad-lib that overlaps the main line in time, and when a line counts as sung. */
class BarAdlibsTest {
	/** A line of two words, sung from 1000 to 3000 ms. */
	private static final List<MusicService.Word> WORDS = List.of(new MusicService.Word(0, 5, 1000, 1800), new MusicService.Word(6, 11, 2000, 3000));

	@Test
	void hideShowsOnlyTheMainLine() {
		assertEquals(Shows.LINE, BarAdlibs.shows(Mode.HIDE, true, true, true));
		assertEquals(Shows.LINE, BarAdlibs.shows(Mode.HIDE, true, true, false));
		assertFalse(BarAdlibs.keepsRoom(Mode.HIDE, true), "no room is kept for what never shows");
	}

	@Test
	void afterTheLineKeepsTheLineAndAddsTheAdlib() {
		assertEquals(Shows.LINE_AND_ADLIB, BarAdlibs.shows(Mode.AFTER, true, true, true));
		assertEquals(Shows.LINE_AND_ADLIB, BarAdlibs.shows(Mode.AFTER, true, true, false));
		assertEquals(Shows.LINE, BarAdlibs.shows(Mode.AFTER, true, false, true));
		assertTrue(BarAdlibs.keepsRoom(Mode.AFTER, true), "the room after the line is kept from the start, so the ad-lib never moves it");
	}

	@Test
	void insteadShowsTheAdlibAloneOnlyWhileNoMainWordIsSung() {
		assertEquals(Shows.ADLIB_ALONE, BarAdlibs.shows(Mode.INSTEAD, true, true, false));
		assertEquals(Shows.LINE, BarAdlibs.shows(Mode.INSTEAD, true, false, false), "back to the main line when the ad-lib is over");
		assertFalse(BarAdlibs.keepsRoom(Mode.INSTEAD, true));
	}

	@Test
	void anAdlibOverTheMainLinesWordsNeverTakesItsPlace() {
		// the ad-lib has its own timing and overlaps the line: the line keeps its place and its highlight
		assertEquals(Shows.LINE, BarAdlibs.shows(Mode.INSTEAD, true, true, true));
		// and in no mode is the result ever "two texts in one place": the line and the ad-lib together only as line plus what stands after it
		for (Mode mode : Mode.values()) {
			for (boolean adlibNow : new boolean[] {false, true}) {
				for (boolean mainActive : new boolean[] {false, true}) {
					Shows shows = BarAdlibs.shows(mode, true, adlibNow, mainActive);
					assertTrue(shows != Shows.ADLIB_ALONE || !mainActive, mode + " replaced a line that was being sung");
					assertTrue(shows != Shows.LINE_AND_ADLIB || mode == Mode.AFTER);
				}
			}
		}
	}

	@Test
	void withTheLyricsOwnSwitchOffNothingShows() {
		for (Mode mode : Mode.values()) {
			assertEquals(Shows.LINE, BarAdlibs.shows(mode, false, true, false));
			assertFalse(BarAdlibs.keepsRoom(mode, false));
		}
	}

	@Test
	void whenALineCountsAsSung() {
		assertFalse(BarAdlibs.mainActive(999, WORDS), "before its first word");
		assertTrue(BarAdlibs.mainActive(1000, WORDS));
		assertTrue(BarAdlibs.mainActive(1900, WORDS), "between two of its words");
		assertTrue(BarAdlibs.mainActive(2999, WORDS));
		assertFalse(BarAdlibs.mainActive(3000, WORDS), "after its last word");
		// a line without word timings is never replaced
		assertTrue(BarAdlibs.mainActive(5000, List.of()));
		assertTrue(BarAdlibs.mainActive(5000, null));
	}
}
