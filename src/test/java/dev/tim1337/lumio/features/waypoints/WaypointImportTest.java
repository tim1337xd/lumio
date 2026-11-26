package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lists other mods share, read as they are. The texts in src/test/resources/waypoints are our own lists, made up for these tests in each mod's
 * format (Skyblocker, its older ordered lists, Skytils plain and gzipped, ColeWeight); SkyHanni's list is written as its source writes it.
 */
class WaypointImportTest {
	private static String resource(String name) throws IOException {
		try (InputStream in = WaypointImportTest.class.getResourceAsStream("/waypoints/" + name + ".txt")) {
			assertNotNull(in, name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String at(Waypoint point) {
		return point.x() + " " + point.y() + " " + point.z();
	}

	@Test
	void skyblockerV1() throws IOException {
		WaypointImport.Result result = WaypointImport.read(resource("made_up_skyblocker_v1_route"));
		assertEquals(WaypointImport.Format.SKYBLOCKER, result.format());
		WaypointImport.Read read = result.groups().getFirst();
		assertEquals("Gem route", read.group().name());
		assertEquals("crystal_hollows", read.island());
		assertEquals(WaypointGroup.Show.NEXT, read.group().show());
		assertEquals("512 70 498", at(read.group().points().getFirst()));
		assertEquals("1", read.group().points().getFirst().name());
		// all green: the group takes the color
		assertEquals(0x00FF00, read.group().rgb());
	}

	@Test
	void skyblockerOlderOrderedLists() throws IOException {
		WaypointImport.Result rift = WaypointImport.read(resource("made_up_skyblocker_ordered_path"));
		assertEquals("path", rift.groups().getFirst().group().name());
		assertEquals("12 80 -34", at(rift.groups().getFirst().group().points().getFirst()));
		WaypointImport.Result stones = WaypointImport.read(resource("made_up_skyblocker_ordered_stones"));
		assertEquals("Stones", stones.groups().getFirst().group().name());
		assertEquals("-20 120 40", at(stones.groups().getFirst().group().points().getFirst()));
		assertEquals(WaypointGroup.Show.NEXT, stones.groups().getFirst().group().show());
	}

	@Test
	void skytilsGzippedWithTwoGroups() throws IOException {
		WaypointImport.Result result = WaypointImport.read(resource("made_up_skytils_v1_gzip"));
		assertEquals(WaypointImport.Format.SKYTILS, result.format());
		assertEquals(List.of("Safe Spots A", "Safe Spots B"), result.groups().stream().map(read -> read.group().name()).toList());
		assertEquals(3, result.groups().get(0).group().points().size());
		assertEquals(5, result.groups().get(1).group().points().size());
		assertEquals("-100 70 -80", at(result.groups().get(0).group().points().getFirst()));
		assertEquals("Corner", result.groups().get(0).group().points().getFirst().name());
	}

	@Test
	void skytilsPlainBase64() throws IOException {
		WaypointImport.Result hub = WaypointImport.read(resource("made_up_skytils_base64_hub"));
		assertEquals("group", hub.groups().getFirst().group().name());
		assertEquals("hub", hub.groups().getFirst().island());
		// "enabled": false hides the point
		assertTrue(hub.groups().getFirst().group().points().getFirst().hidden());
		WaypointImport.Result hollows = WaypointImport.read(resource("made_up_skytils_base64_crystal_hollows"));
		assertEquals(6, hollows.points());
		assertEquals("Start here", hollows.groups().getFirst().group().points().getFirst().name());
		assertEquals("crystal_hollows", hollows.groups().getFirst().island());
	}

	@Test
	void coleweightWithNumbersIsASkyHanniRoute() throws IOException {
		WaypointImport.Result result = WaypointImport.read(resource("made_up_coleweight_route"));
		assertEquals(WaypointImport.Format.SKYHANNI, result.format());
		WaypointGroup group = result.groups().getFirst().group();
		assertEquals("30 75 20", at(group.points().getFirst()));
		assertEquals("1", group.points().getFirst().name());
		assertEquals(WaypointGroup.Show.NEXT, group.show());
	}

	@Test
	void coleweightWithWordsStaysColeweight() {
		WaypointImport.Result result = WaypointImport.read("[{\"x\":1,\"y\":64,\"z\":2,\"r\":1,\"g\":0,\"b\":0,\"options\":{\"name\":\"Vein\"}}]");
		assertEquals(WaypointImport.Format.COLEWEIGHT, result.format());
		assertEquals(0xFF0000, result.groups().getFirst().group().rgb());
	}

	@Test
	void ownExportsComeBack() {
		WaypointGroup route = new WaypointGroup("g", "Glacite route", 0x5AC8FA);
		route.show(WaypointGroup.Show.NEXT);
		route.areas().add("mineshaft");
		route.add(new Waypoint(12, 140, -84, "Entrance ladder"));
		Waypoint vein = new Waypoint(18, 131, -97, "Tungsten vein");
		vein.rgb(0xFF9F0A);
		route.add(vein);
		for (WaypointExport.Format format : WaypointExport.Format.values()) {
			String text = WaypointExport.write(List.of(route), format, true, area -> area.equals("mineshaft") ? "mineshaft" : null);
			WaypointImport.Result back = WaypointImport.read(text);
			assertNotNull(back, format.name());
			WaypointGroup read = back.groups().getFirst().group();
			assertEquals(2, read.points().size(), format.name());
			assertEquals("18 131 -97", at(read.points().get(1)), format.name());
			assertEquals(WaypointGroup.Show.NEXT, read.show(), format.name());
		}
		WaypointImport.Result lumio = WaypointImport.read(WaypointExport.write(List.of(route), WaypointExport.Format.LUMIO, true, area -> null));
		assertEquals(WaypointImport.Format.LUMIO, lumio.format());
		assertEquals(List.of("mineshaft"), lumio.groups().getFirst().group().areas());
		assertEquals("Entrance ladder", lumio.groups().getFirst().group().points().getFirst().name());
		WaypointImport.Result skyblocker = WaypointImport.read(WaypointExport.write(List.of(route), WaypointExport.Format.SKYBLOCKER, true,
			area -> "mineshaft"));
		assertEquals("mineshaft", skyblocker.groups().getFirst().island());
		assertEquals("Tungsten vein", skyblocker.groups().getFirst().group().points().get(1).name());
		WaypointImport.Result withoutAreas = WaypointImport.read(WaypointExport.write(List.of(route), WaypointExport.Format.LUMIO, false, area -> null));
		assertTrue(withoutAreas.groups().getFirst().group().areas().isEmpty());
	}

	@Test
	void plainCoordinateLines() {
		WaypointImport.Result result = WaypointImport.read("start 1 64 2\nvein at x: 3 y: 65 z: 4\n");
		assertEquals(WaypointImport.Format.COORDINATES, result.format());
		assertEquals(2, result.points());
		assertEquals("Vein", result.groups().getFirst().group().points().get(1).name());
	}

	@Test
	void nothingInBrokenOrHugeTexts() {
		assertNull(WaypointImport.read("hello there"));
		assertNull(WaypointImport.read("[Skyblocker-Waypoint-Data-V1]not base64 at all!"));
		assertNull(WaypointImport.read("{\"something\": 1}"));
		assertNull(WaypointImport.read("x".repeat(WaypointImport.MAX_TEXT + 1)));
		// a gzip bomb is stopped at the limit
		String bomb = WaypointImport.SKYBLOCKER + WaypointExport.pack("[" + " ".repeat(WaypointImport.MAX_UNPACKED + 10) + "]");
		assertNull(WaypointImport.read(bomb));
		assertFalse(WaypointImport.read("1 64 2") == null);
	}
}
