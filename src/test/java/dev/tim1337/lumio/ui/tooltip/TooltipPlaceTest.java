package dev.tim1337.lumio.ui.tooltip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** A tooltip moves up from a search bar under it, and gets shorter (it scrolls) where there is no room above. */
class TooltipPlaceTest {
	private static final int[] BAR = {100, 300, 300, 330};

	private static int[] fit(int x, int y, int height) {
		int[] out = new int[2];
		TooltipPlace.fit(x, y, 120, height, 10, BAR, 1, out);
		return out;
	}

	@Test
	void clearOfTheBarItStaysWhereItIs() {
		assertArrayEquals(new int[] {50, 100}, fit(150, 50, 100));
		assertArrayEquals(new int[] {240, 100}, fit(400, 240, 100), "beside the bar");
	}

	@Test
	void onTheBarItMovesUp() {
		assertArrayEquals(new int[] {160, 140}, fit(150, 220, 140));
	}

	@Test
	void tooTallForTheRoomAboveItGetsShorter() {
		assertArrayEquals(new int[] {10, 290}, fit(150, 10, 400));
	}
}
