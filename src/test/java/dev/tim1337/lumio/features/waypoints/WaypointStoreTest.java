package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Groups are kept per scope and come back as they were; broken files and values are taken in as far as they can be. */
class WaypointStoreTest {
	@TempDir
	Path folder;

	private WaypointStore open(String scope) {
		WaypointStore store = new WaypointStore(() -> folder);
		store.loadGlobal(Runnable::run);
		store.flush();
		store.enter(scope, Runnable::run);
		store.flush();
		return store;
	}

	@Test
	void aGroupComesBackAfterARestart() {
		WaypointStore store = open("server-mc.example.net");
		WaypointGroup group = new WaypointGroup(store.newId(), "Glacite route", 0x5AC8FA);
		group.areas().add("glacite_mineshafts");
		group.show(WaypointGroup.Show.NEXT);
		group.set(WaypointGroup.Look.THROUGH_WALLS, true);
		group.add(new Waypoint(12, 140, -84, "Entrance ladder"));
		Waypoint vein = new Waypoint(18, 131, -97, "Tungsten vein");
		vein.rgb(0xFF9F0A);
		group.add(vein);
		group.next(1);
		store.add(group, false);
		store.saveNow();
		store.flush();

		WaypointStore again = open("server-mc.example.net");
		assertEquals(1, again.groups().size());
		WaypointGroup read = again.groups().getFirst();
		assertEquals("Glacite route", read.name());
		assertEquals(List.of("glacite_mineshafts"), read.areas());
		assertEquals(WaypointGroup.Show.NEXT, read.show());
		assertTrue(read.has(WaypointGroup.Look.THROUGH_WALLS));
		assertEquals(1, read.next());
		assertEquals("18, 131, -97", read.points().get(1).coords());
		assertEquals(0xFF9F0A, read.points().get(1).rgb());
	}

	@Test
	void scopesKeepTheirOwnGroups() {
		WaypointStore store = open("skyblock-one");
		store.add(new WaypointGroup(store.newId(), "One", 0), false);
		store.enter("skyblock-two", Runnable::run);
		store.flush();
		assertTrue(store.groups().isEmpty());
		store.add(new WaypointGroup(store.newId(), "Everywhere", 0), true);
		store.saveNow();
		store.flush();
		store.enter("skyblock-one", Runnable::run);
		store.flush();
		assertEquals(List.of("Everywhere", "One"), store.groups().stream().map(WaypointGroup::name).toList());
	}

	@Test
	void brokenValuesAreLeftOut() {
		JsonObject root = JsonParser.parseString("""
			{"groups": [
			  {"id": "../evil", "name": "Ok", "show": "nonsense", "points": [
			    {"x": 1, "y": 2, "z": 3},
			    {"x": 1e300, "y": 2, "z": 3},
			    {"x": "a", "y": 2, "z": 3},
			    {"x": 4, "y": 99999, "z": 3}
			  ]},
			  "not a group"
			]}""").getAsJsonObject();
		List<WaypointGroup> groups = WaypointJson.read(root, saved -> saved == null ? "new" : saved);
		assertEquals(1, groups.size());
		assertEquals("new", groups.getFirst().id());
		assertEquals(WaypointGroup.Show.ALL, groups.getFirst().show());
		assertEquals(1, groups.getFirst().points().size());
	}

	@Test
	void scopeNamesAreSafeFileNames() {
		assertEquals("server-mc.hypixel.net_25565", WaypointStore.scopeName("server-mc.hypixel.net:25565"));
		assertEquals("_global", WaypointStore.scopeName("global"));
		assertFalse(WaypointStore.scopeName("../../x").contains("/"));
		assertTrue(WaypointStore.scopeName("..").startsWith("_"));
	}

	@Test
	void movingKeepsTheNextPoint() {
		WaypointGroup group = new WaypointGroup("g", "Route", 0);
		for (int i = 0; i < 4; i++) {
			group.add(new Waypoint(i, 64, 0, "P" + i));
		}
		group.next(2);
		group.move(2, 0);
		assertEquals("P2", group.points().get(group.next()).name());
		group.remove(0);
		assertEquals("P0", group.points().get(group.next()).name());
	}

	@Test
	void noFileMeansNoGroups() throws Exception {
		WaypointStore store = open("nothing-here");
		assertTrue(store.groups().isEmpty());
		assertFalse(Files.exists(folder.resolve("nothing-here.json")));
	}

	@Test
	void anOldFairySoulGroupIsLeftOutAndTheOthersLoad() {
		JsonObject root = JsonParser.parseString("{\"groups\":[{\"id\":\"builtin-fairy_souls\",\"name\":\"Fairy souls\",\"built_in\":\"fairy_souls\",\"points\":[]},"
			+ "{\"id\":\"g1\",\"name\":\"Route\",\"points\":[{\"x\":1,\"y\":2,\"z\":3}]}]}").getAsJsonObject();
		List<WaypointGroup> groups = WaypointJson.read(root, id -> id == null ? "new" : id);
		assertEquals(1, groups.size());
		assertEquals("Route", groups.getFirst().name());
		assertEquals(1, groups.getFirst().points().size());
	}

	@Test
	void anOldGroupTakesItsLabelFromItsLookAndGetsTheBlock() {
		JsonObject root = JsonParser.parseString("{\"groups\":[{\"id\":\"g1\",\"name\":\"Old\",\"show\":\"nearest\",\"look\":[\"beam\",\"name\",\"distance\"],"
			+ "\"points\":[]},{\"id\":\"g2\",\"name\":\"Quiet\",\"show\":\"next\",\"look\":[\"line\"],\"points\":[]}]}").getAsJsonObject();
		List<WaypointGroup> groups = WaypointJson.read(root, id -> id == null ? "new" : id);
		WaypointGroup old = groups.getFirst();
		assertEquals(WaypointGroup.Label.NAME_DISTANCE, old.label());
		assertTrue(old.has(WaypointGroup.Look.BLOCK));
		assertTrue(old.has(WaypointGroup.Look.BEAM));
		assertEquals(3, old.nextCount());
		WaypointGroup quiet = groups.get(1);
		assertEquals(WaypointGroup.Label.OFF, quiet.label());
		assertEquals(WaypointGroup.DEFAULT_SHOWN, quiet.nextCount());
	}

	@Test
	void aNewGroupShowsTheNextTwoWithItsBlockAndNoBeam() {
		WaypointGroup group = new WaypointGroup("g", "New", 0x5AC8FA);
		assertEquals(WaypointGroup.Show.NEXT, group.show());
		assertEquals(2, group.nextCount());
		assertEquals(WaypointGroup.Label.NAME, group.label());
		assertFalse(group.labelBackground());
		assertTrue(group.has(WaypointGroup.Look.BLOCK));
		assertFalse(group.has(WaypointGroup.Look.BEAM));
		group.labelSize(1.3f);
		group.labelBackground(true);
		WaypointGroup again = WaypointJson.read(WaypointJson.write(List.of(group)), id -> id).getFirst();
		assertEquals(1.3f, again.labelSize(), 1e-4);
		assertTrue(again.labelBackground());
		assertEquals(2, again.nextCount());
	}
}
