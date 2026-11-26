package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The waypoint menu is as wide as its widest item in every state, so the text asking for a second click never runs over its box. */
class WaypointMenuTest {
	/** Six pixels a character, as a stand-in for the font. */
	private static int measure(String text) {
		return text.length() * 6;
	}

	@Test
	void theConfirmTextIsShort() {
		assertEquals("Click again to delete", WaypointMenu.confirmText("Delete group"));
		assertEquals("Click again to delete", WaypointMenu.confirmText("Delete"));
		assertEquals("Click again to clear", WaypointMenu.confirmText(" Clear all points "));
	}

	@Test
	void theMenuFitsEveryState() {
		List<WaypointMenu.Item> items = List.of(WaypointMenu.Item.of("Rename", () -> { }), WaypointMenu.Item.dot("Color", 0xFF9F0A, () -> { }),
			WaypointMenu.Item.danger("Delete group", () -> { }));
		int width = WaypointMenu.width(items, WaypointMenuTest::measure);
		int plain = WaypointMenu.width(List.of(WaypointMenu.Item.of("Rename", () -> { }), WaypointMenu.Item.of("Delete group", () -> { })), WaypointMenuTest::measure);
		assertTrue(width > plain, "sized for the text of the second click, not only for the item's own");
		// every text the menu can show fits inside it, with the room beside it
		for (WaypointMenu.Item item : items) {
			assertTrue(measure(item.text()) < width);
			if (item.danger()) {
				assertTrue(measure(WaypointMenu.confirmText(item.text())) + 12 <= width, "the confirming text fits");
			}
		}
		// an item that asks for nothing adds nothing
		assertEquals(plain, WaypointMenu.width(List.of(WaypointMenu.Item.of("Rename", () -> { }), WaypointMenu.Item.of("Delete group", () -> { })),
			WaypointMenuTest::measure));
	}
}
