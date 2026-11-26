package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Back and forward through the menu as a browser has them. */
class MenuHistoryTest {
	@Test
	void backAndForwardMoveThroughThePlacesVisited() {
		MenuHistory<String> history = new MenuHistory<>();
		history.visit("World");
		history.visit("World › Sky");
		history.visit("World › Sky › Clouds");
		assertEquals("World › Sky", history.back());
		assertEquals("World", history.back());
		assertEquals("World › Sky", history.forward());
		assertEquals("World › Sky › Clouds", history.forward());
		assertNull(history.forward(), "nothing ahead");
		assertEquals("World › Sky › Clouds", history.current());
	}

	@Test
	void backAtTheStartDoesNothing() {
		MenuHistory<String> history = new MenuHistory<>();
		assertNull(history.back());
		history.visit("Home");
		assertNull(history.back());
		assertEquals("Home", history.current());
		assertFalse(history.canGoBack());
	}

	@Test
	void aNewPlaceAfterGoingBackDropsTheWayForward() {
		MenuHistory<String> history = new MenuHistory<>();
		history.visit("Home");
		history.visit("World");
		history.visit("Music");
		history.back();
		history.back();
		assertTrue(history.canGoForward());
		history.visit("Interface");
		assertFalse(history.canGoForward(), "the steps ahead are gone");
		assertNull(history.forward());
		assertEquals("Home", history.back());
	}

	@Test
	void thePlaceShownTwiceIsOneStep() {
		MenuHistory<String> history = new MenuHistory<>();
		history.visit("Home");
		history.visit("Home");
		history.visit("World");
		history.visit("World");
		assertEquals("Home", history.back());
		assertNull(history.back());
	}

	@Test
	void whatGoingBackSettledOnReplacesTheStepWithoutDroppingTheWayForward() {
		MenuHistory<String> history = new MenuHistory<>();
		history.visit("World");
		history.visit("World › Sky");
		history.back();
		history.replace("World (scrolled)");
		assertEquals("World › Sky", history.forward());
	}

	@Test
	void onlyTheLastStepsAreKept() {
		MenuHistory<Integer> history = new MenuHistory<>();
		for (int i = 0; i <= MenuHistory.MOST_STEPS + 10; i++) {
			history.visit(i);
		}
		int steps = 0;
		while (history.back() != null) {
			steps++;
		}
		assertEquals(MenuHistory.MOST_STEPS, steps);
	}
}
