package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Coordinates in chat lines and pasted text. */
class CoordParserTest {
	private static String coords(CoordParser.Found found) {
		return found.x() + " " + found.y() + " " + found.z();
	}

	@Test
	void labelledCoordsInAPartyMessage() {
		CoordParser.Found found = CoordParser.first("[Party] [MVP+] Kiwi: corpse at x: 87, y: 118, z: -132");
		assertEquals("87 118 -132", coords(found));
		assertEquals("Corpse", found.name());
	}

	@Test
	void plainCoordsWithCommasAndSpaces() {
		assertEquals("12 140 -84", coords(CoordParser.first("12, 140, -84")));
		assertEquals("-5 64 3", coords(CoordParser.first("meet me -5 64 3 pls")));
		assertEquals("10 70 -21", coords(CoordParser.first("x=10.7 y=70 z=-20.2")));
	}

	@Test
	void numbersThatAreNoCoords() {
		assertNull(CoordParser.first("I sold 3 items for 1500000 coins"));
		assertNull(CoordParser.first("1 2000 3"));
		assertNull(CoordParser.first("Version 1.21.4"));
		assertNull(CoordParser.first("12:30:45 something"));
	}

	@Test
	void aListOneAPerLine() {
		var list = CoordParser.all("""
			1 64 2
			not a point
			x: 3 y: 65 z: 4
			5, 66, 6""", 100);
		assertEquals(3, list.size());
		assertEquals("3 65 4", coords(list.get(1)));
	}
}
