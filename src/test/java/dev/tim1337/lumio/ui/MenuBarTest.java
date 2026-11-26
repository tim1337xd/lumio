package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The search in the top bar: centered where it fits, narrower where it does not, never over the name or the close button. */
class MenuBarTest {
	@Test
	void aWideWindowCentersTheSearchAtItsWidth() {
		MenuBar.Search search = MenuBar.search(120, 860, 500, 190, 70);
		assertEquals(190, search.width());
		assertEquals(500 - 95, search.x());
	}

	@Test
	void aNarrowWindowShrinksItAndKeepsItClear() {
		for (int rightEnd = 200; rightEnd <= 900; rightEnd += 7) {
			int leftEnd = 150;
			int center = (leftEnd + rightEnd) / 2 + 40;
			MenuBar.Search search = MenuBar.search(leftEnd, rightEnd, center, 190, 70);
			if (search.width() == 0) {
				assertTrue(rightEnd - leftEnd - 2 * MenuBar.GAP < 70, "left out only without room for the narrowest: " + rightEnd);
				continue;
			}
			assertTrue(search.width() >= 70 && search.width() <= 190);
			assertTrue(search.x() >= leftEnd + MenuBar.GAP, "clear of the name at " + rightEnd);
			assertTrue(search.x() + search.width() <= rightEnd - MenuBar.GAP, "clear of the close button at " + rightEnd);
		}
	}
}
