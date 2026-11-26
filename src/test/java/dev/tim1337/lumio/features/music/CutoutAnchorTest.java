package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A song's lyric blocks keep to one side of the view, and a new song starts without one. */
class CutoutAnchorTest {
	@Test
	void theFirstBlockSetsTheSideAndAMoveToTheOtherSideIsKept() {
		CutoutAnchor anchor = new CutoutAnchor();
		anchor.song(1);
		assertEquals(0, anchor.side(), "none before the first block");
		anchor.placed(0.4);
		assertEquals(1, anchor.side());
		anchor.song(1);
		assertEquals(1, anchor.side(), "the same song keeps it");
		anchor.placed(-0.3);
		assertEquals(-1, anchor.side(), "moved when it had to: that side is the song's now");
	}

	@Test
	void aNewSongStartsWithoutASide() {
		CutoutAnchor anchor = new CutoutAnchor();
		anchor.song(1);
		anchor.placed(-0.5);
		anchor.song(2);
		assertEquals(0, anchor.side());
	}
}
