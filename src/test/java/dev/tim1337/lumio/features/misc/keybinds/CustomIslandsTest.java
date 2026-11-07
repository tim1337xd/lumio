package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.api.Place;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The player's own islands for keybinds: loose name matching, no repeats of built-in islands, saving, removing and Undo. */
class CustomIslandsTest {
	/** Places as an add-on knows them: a name, and the names the server shows for each. */
	private static final Place CRIMSON = new Place("crimson_isle", "Crimson Isle", List.of("crimson isle"));
	private static final Place DUNGEONS = new Place("dungeons", "Dungeons", List.of("catacombs", "the catacombs"));
	private static final Place SPIDERS_DEN = new Place("spiders_den", "Spider's Den", List.of("spider's den"));
	private static final Place GARDEN = new Place("garden", "The Garden", List.of("garden", "the garden"));
	private static final Place HUB = new Place("hub", "Hub", List.of("hub"));
	private static final List<Place> PLACES = List.of(HUB, CRIMSON, DUNGEONS, SPIDERS_DEN, GARDEN);

	@Test
	void namesMatchLoosely() {
		assertTrue(CustomIslands.same("Mythic Reef", "mythic reef"));
		assertTrue(CustomIslands.same("Mythic Reef", "  Mythic   Reef "));
		assertTrue(CustomIslands.same("Mythic Reef", "§bMythic §lReef"));
		assertTrue(CustomIslands.same("Mythic Reef", "Mythic Reef ⏣"));
		assertTrue(CustomIslands.same("Spider's Den", "spiders den"));
		assertFalse(CustomIslands.same("Mythic Reef", "Mythic Reefs"));
		assertFalse(CustomIslands.same("", ""));
		assertEquals("Mythic Reef", CustomIslands.clean("§b  Mythic \t Reef "));
	}

	@Test
	void builtInIslandsAreFoundByLabelAndArea() {
		assertEquals(CRIMSON, CustomIslands.builtIn("§cCrimson  Isle", PLACES));
		assertEquals(DUNGEONS, CustomIslands.builtIn("The Catacombs", PLACES));
		assertEquals(SPIDERS_DEN, CustomIslands.builtIn("spiders den", PLACES));
		assertEquals(GARDEN, CustomIslands.builtIn("Garden", PLACES));
		assertNull(CustomIslands.builtIn("Mythic Reef", PLACES));
	}

	@Test
	void addingKeepsOneOfEachAndNoBuiltIn() {
		CustomIslands islands = new CustomIslands(() -> PLACES);
		assertEquals("Mythic Reef", islands.add(" Mythic  Reef"));
		assertEquals("Mythic Reef", islands.add("mythic reef"));
		assertNull(islands.add("Crimson Isle"));
		assertNull(islands.add("  "));
		assertNull(islands.add("§a"));
		assertEquals(List.of("Mythic Reef"), islands.names());
		for (int i = 0; i < CustomIslands.MOST * 2; i++) {
			islands.add("Island " + i);
		}
		assertEquals(CustomIslands.MOST, islands.names().size());
		assertTrue(CustomIslands.clean("x".repeat(100)).length() <= CustomIslands.LONGEST_NAME);
	}

	@Test
	void savingAndLoadingKeepTheList() {
		CustomIslands islands = new CustomIslands(() -> PLACES);
		islands.add("Mythic Reef");
		islands.add("Frost Lake");
		CustomIslands loaded = new CustomIslands(() -> PLACES);
		loaded.load(islands.save());
		assertEquals(List.of("Mythic Reef", "Frost Lake"), loaded.names());
		// an edited config: built-in islands, repeats and empty lines are left out
		loaded.load("Hub\nFrost Lake\nfrost  lake\n\n§cMythic Reef");
		assertEquals(List.of("Frost Lake", "Mythic Reef"), loaded.names());
		loaded.load(null);
		assertTrue(loaded.names().isEmpty());
	}

	@Test
	void removingAndUndoPutTheIslandBack() {
		CustomIslands islands = new CustomIslands(() -> PLACES);
		islands.add("Mythic Reef");
		islands.add("Frost Lake");
		int index = islands.remove("mythic reef");
		assertEquals(0, index);
		assertEquals(List.of("Frost Lake"), islands.names());
		islands.insert(index, "Mythic Reef");
		assertEquals(List.of("Mythic Reef", "Frost Lake"), islands.names());
		assertEquals(-1, islands.remove("Nowhere"));
	}

	@Test
	void keybindsWorkOnTheirOwnIslandsOnly() {
		CommandBind bind = new CommandBind();
		bind.command = "/warp reef";
		bind.where = CommandBind.Where.ISLANDS;
		bind.customIslands.add("Mythic Reef");
		CommandBinds.Spot reef = new CommandBinds.Spot(true, null, CustomIslands.key("§bMythic Reef"), false);
		CommandBinds.Spot hub = new CommandBinds.Spot(true, "hub", CustomIslands.key("Hub"), false);
		CommandBinds.Spot outside = new CommandBinds.Spot(false, null, CustomIslands.key("Mythic Reef"), false);
		assertTrue(CommandBinds.applies(bind, reef));
		assertFalse(CommandBinds.applies(bind, hub));
		assertFalse(CommandBinds.applies(bind, outside));
		assertFalse(bind.noIslands());

		// two keybinds on the same key overlap on the same island of the player's own
		CommandBind other = bind.copy();
		other.customIslands.clear();
		other.customIslands.add("mythic reef");
		bind.key = 'G';
		other.key = 'G';
		assertTrue(CommandBinds.overlap(bind, other));
		other.customIslands.clear();
		other.customIslands.add("Frost Lake");
		assertFalse(CommandBinds.overlap(bind, other));
	}

	@Test
	void keybindsSaveTheirOwnIslands() {
		CommandBind bind = new CommandBind();
		bind.where = CommandBind.Where.ISLANDS;
		bind.islands.add("hub");
		bind.customIslands.add("Mythic Reef");
		CommandBind loaded = CommandBind.load(bind.save());
		assertNotNull(loaded);
		assertEquals(bind.islands, loaded.islands);
		assertEquals(bind.customIslands, loaded.customIslands);
		// a config without the player's own islands still loads
		var json = bind.save();
		json.remove("custom_islands");
		assertTrue(CommandBind.load(json).customIslands.isEmpty());
	}
}
