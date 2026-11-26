package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.config.setting.KeyClash.Context;
import dev.tim1337.lumio.config.setting.KeyClash.Kind;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The contexts of binds and the rules of a conflict between two binds on the same key. */
class KeyClashTest {
	private static final Context LOADOUTS = Context.menu("Loadouts");
	/** A hotbar key of the game: in the world, and in menus too. */
	private static final List<Context> HOTBAR = List.of(Context.WORLD, Context.ANY_MENU);
	/** Most keys of the game: in the world only. */
	private static final List<Context> GAME = List.of(Context.WORLD);

	@Test
	void twoBindsInTheSameContextConflict() {
		assertEquals(Kind.BOTH, KeyClash.between(Context.WORLD, Context.WORLD));
		assertEquals(Kind.BOTH, KeyClash.between(Context.ANY_MENU, Context.ANY_MENU));
		assertEquals(Kind.BOTH, KeyClash.between(Context.TYPING, Context.TYPING));
		assertEquals(Kind.BOTH, KeyClash.between(LOADOUTS, Context.menu("Loadouts")));
	}

	@Test
	void bindsThatNeverActTogetherDoNotConflict() {
		assertEquals(Kind.NONE, KeyClash.between(Context.WORLD, Context.ANY_MENU));
		assertEquals(Kind.NONE, KeyClash.between(Context.ANY_MENU, Context.WORLD));
		assertEquals(Kind.NONE, KeyClash.between(LOADOUTS, Context.WORLD));
		assertEquals(Kind.NONE, KeyClash.between(LOADOUTS, Context.menu("Wardrobe")));
		assertEquals(Kind.NONE, KeyClash.between(Context.TYPING, Context.WORLD));
		assertEquals(Kind.NONE, KeyClash.between(Context.TYPING, Context.ANY_MENU));
		assertEquals(Kind.NONE, KeyClash.between(Context.WORLD, Context.TYPING));
	}

	@Test
	void aBindOfOneMenuIsUsedInsteadOfABindOfAnyMenu() {
		assertEquals(Kind.OVERRIDES, KeyClash.between(LOADOUTS, Context.ANY_MENU));
		assertEquals(Kind.OVERRIDDEN, KeyClash.between(Context.ANY_MENU, LOADOUTS));
	}

	@Test
	void aLoadoutKeyOnAHotbarKeyIsNoConflict() {
		// the case that showed "Also: Hotbar Slot 1 (Inventory)": the Loadout key takes the key inside the Loadouts menu
		assertEquals(Kind.OVERRIDES, KeyClash.between(LOADOUTS, HOTBAR));
		// a key of the game that does nothing in menus never meets it
		assertEquals(Kind.NONE, KeyClash.between(LOADOUTS, GAME));
	}

	@Test
	void theGamesKeysCountAsInTheWorldUnlessTheyAlsoActInMenus() {
		assertEquals(Kind.BOTH, KeyClash.between(Context.WORLD, GAME));
		assertEquals(Kind.BOTH, KeyClash.between(Context.WORLD, HOTBAR));
		assertEquals(Kind.NONE, KeyClash.between(Context.ANY_MENU, GAME));
		assertEquals(Kind.BOTH, KeyClash.between(Context.ANY_MENU, HOTBAR));
		assertEquals(Kind.NONE, KeyClash.between(Context.TYPING, HOTBAR));
	}

	@Test
	void aRealConflictSaysWhatAlsoTriggers() {
		assertEquals("Also triggers: Drop Item", KeyClash.conflictHint(List.of("Drop Item")));
		assertEquals("Also triggers: Drop Item, Music › Next song", KeyClash.conflictHint(List.of("Drop Item", "Music › Next song")));
		assertNull(KeyClash.conflictHint(List.of()));
	}

	@Test
	void aHintListsTwoNamesThenCountsTheRest() {
		assertEquals("Also triggers: A, B and 1 more", KeyClash.conflictHint(List.of("A", "B", "C")));
		assertEquals("Also triggers: A, B and 3 more", KeyClash.conflictHint(List.of("A", "B", "C", "D", "E")));
	}

	@Test
	void aNameShowsOnceInAHint() {
		// the check gathers the names in a set, in the order found
		Set<String> names = new LinkedHashSet<>(List.of("Hotbar Slot 1", "Hotbar Slot 1", "Drop Item"));
		assertEquals("Also triggers: Hotbar Slot 1, Drop Item", KeyClash.conflictHint(names));
	}

	@Test
	void theQuietNoteNamesTheMenu() {
		assertEquals("Used instead of Hotbar Slot 1 while the Loadouts menu is open", KeyClash.overrideNote(List.of("Hotbar Slot 1"), "Loadouts"));
		assertEquals("Used instead of A, B and 2 more while the Loadouts menu is open", KeyClash.overrideNote(List.of("A", "B", "C", "D"), "Loadouts"));
		assertNull(KeyClash.overrideNote(List.of(), "Loadouts"));
		assertNull(KeyClash.overrideNote(List.of("A"), null));
	}
}
