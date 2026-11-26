package dev.tim1337.lumio.features.misc.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import dev.tim1337.lumio.config.setting.KeySetting;

import java.util.List;
import java.util.Set;

/** Key combinations: recording, which bind fires (the most specific wins), conflicts, the search and the config. */
class KeyComboTest {
	private static final String TAB = "key.keyboard.tab";
	private static final String R = "key.keyboard.r";
	private static final String G = "key.keyboard.g";
	private static final String H = "key.keyboard.h";
	private static final String F3 = "key.keyboard.f3";
	private static final String ONE = "key.keyboard.1";
	private static final String MOUSE_4 = "key.mouse.4";
	private static final String E = "key.keyboard.e";
	private static final String LEFT_CONTROL = "key.keyboard.left.control";
	private static final String RIGHT_CONTROL = "key.keyboard.right.control";

	@Test
	void recordingHoldsTheKeysAndEndsOnTheFirstRelease() {
		KeyCapture capture = new KeyCapture();
		assertEquals(KeyCapture.Result.PENDING, capture.down(TAB));
		assertEquals(KeyCapture.Result.PENDING, capture.down(R));
		assertEquals(List.of(TAB), capture.soFar().holders());
		assertEquals(KeyCapture.Result.DONE, capture.up(R));
		assertEquals(new KeyCombo(List.of(TAB), R), capture.combo());
		// a single key
		KeyCapture single = new KeyCapture();
		single.down(G);
		assertEquals(KeyCapture.Result.DONE, single.up(G));
		assertEquals(KeyCombo.of(G), single.combo());
		// a mouse button holds too
		KeyCapture mouse = new KeyCapture();
		mouse.down(MOUSE_4);
		mouse.down(E);
		mouse.up(MOUSE_4);
		assertEquals(new KeyCombo(List.of(MOUSE_4), E), mouse.combo());
	}

	@Test
	void recordingStopsAtThreeKeysAndKnowsEscAndBackspace() {
		KeyCapture capture = new KeyCapture();
		capture.down(TAB);
		capture.down(G);
		capture.down(ONE);
		capture.down(R);
		capture.up(R);
		assertEquals(new KeyCombo(List.of(TAB, G), ONE), capture.combo());
		assertEquals(KeyCapture.Result.CLEARED, new KeyCapture().down(KeyCapture.ESCAPE), "Esc first removes the key");
		assertEquals(KeyCapture.Result.CLEARED, new KeyCapture().down(KeyCapture.BACKSPACE));
		assertEquals(KeyCapture.Result.CLEARED, new KeyCapture().down(KeyCapture.DELETE));
		// Esc as a later key is a key of the combination
		KeyCapture later = new KeyCapture();
		later.down(G);
		later.down(KeyCapture.ESCAPE);
		later.up(G);
		assertEquals(new KeyCombo(List.of(G), KeyCapture.ESCAPE), later.combo());
		// the wheel ends it at once, as the trigger
		KeyCapture wheel = new KeyCapture();
		wheel.down(TAB);
		assertEquals(KeyCapture.Result.DONE, wheel.down(KeyCombo.WHEEL_UP));
		assertEquals(new KeyCombo(List.of(TAB), KeyCombo.WHEEL_UP), wheel.combo());
	}

	@Test
	void theMostSpecificBindWins() {
		KeyCombo plain = KeyCombo.of(R);
		KeyCombo combo = new KeyCombo(List.of(TAB), R);
		List<KeyCombo> binds = List.of(plain, combo);
		// Tab held: only Tab + R fires
		int size = KeyCombo.winningSize(binds, R, Set.of(TAB));
		assertEquals(2, size);
		assertTrue(combo.wins(R, Set.of(TAB), size));
		assertFalse(plain.wins(R, Set.of(TAB), size));
		// nothing held: the plain R
		int alone = KeyCombo.winningSize(binds, R, Set.of());
		assertTrue(plain.wins(R, Set.of(), alone));
		assertFalse(combo.wins(R, Set.of(), alone));
	}

	@Test
	void orderAndRelease() {
		KeyCombo combo = new KeyCombo(List.of(TAB), R);
		// the holder first, then the trigger
		assertTrue(combo.firesWith(R, Set.of(TAB)));
		// the holder pressed last is not the trigger
		assertFalse(combo.firesWith(TAB, Set.of(R)));
		// the holder let go before the trigger: no combination
		assertFalse(combo.firesWith(R, Set.of()));
		// left or right Ctrl are the same holder
		KeyCombo control = KeyCombo.pressed(List.of(LEFT_CONTROL, R));
		assertTrue(control.firesWith(R, Set.of(KeyCombo.normal(RIGHT_CONTROL))));
	}

	@Test
	void conflictsAreTheSameKeysOnly() {
		KeyCombo first = new KeyCombo(List.of(TAB, G), R);
		assertTrue(first.sameAs(new KeyCombo(List.of(G, TAB), R)));
		assertFalse(KeyCombo.of(R).sameAs(new KeyCombo(List.of(TAB), R)));
		assertFalse(new KeyCombo(List.of(TAB), R).sameAs(KeyCombo.of(R)));
		assertFalse(KeyCombo.NONE.sameAs(KeyCombo.NONE));
	}

	@Test
	void shownAndFound() {
		KeyCombo combo = new KeyCombo(List.of(TAB), R);
		String shown = combo.shown(key -> key.equals(TAB) ? "Tab" : "R");
		assertEquals("Tab + R", shown);
		assertTrue(KeyCombo.found(shown, "tab r"));
		assertTrue(KeyCombo.found(shown, "Tab + R"));
		assertFalse(KeyCombo.found(shown, "ctrl r"));
	}

	@Test
	void configReadsOldAndNew() {
		KeyCombo combo = new KeyCombo(List.of(TAB), R);
		assertEquals(combo, KeyCombo.fromJson(combo.toJson()));
		// nothing saved (an old single-key bind): no combination, the game's own key stays
		assertEquals(KeyCombo.NONE, KeyCombo.fromJson(null));
		assertEquals(KeyCombo.NONE, KeyCombo.fromJson(new com.google.gson.JsonObject()));
		// a broken list: what can be read
		com.google.gson.JsonArray broken = new com.google.gson.JsonArray();
		broken.add("x".repeat(100));
		broken.add(R);
		assertEquals(KeyCombo.of(R), KeyCombo.fromJson(broken));
	}

	@Test
	void aClearedKeyStaysClearedAfterARestart() {
		String name = "key.lumio.test_clear";
		KeyCombo combo = new KeyCombo(List.of(TAB), R);
		KeyCombos.remember(name, combo);
		assertEquals(combo, KeyCombos.kept(name));
		JsonObject before = KeyCombos.write();
		KeyCombos.read(before);
		assertEquals(combo, KeyCombos.kept(name), "a combination comes back after a restart");

		// Esc in a waiting key field: the key is removed
		assertEquals(KeyCapture.Result.CLEARED, new KeyCapture().down(KeyCapture.ESCAPE));
		KeyCombos.remember(name, KeyCombo.NONE);
		assertEquals("key.keyboard.unknown", KeyCombos.ownKey(KeyCombo.NONE), "the game keeps none, not the default key");
		JsonObject saved = KeyCombos.write();
		assertFalse(saved.has(name));
		KeyCombos.read(saved);
		assertNull(KeyCombos.kept(name), "nothing of the combination comes back after a restart");

		// a plain key: the game keeps it, Lumio's file keeps nothing
		KeyCombos.remember(name, KeyCombo.of(R));
		assertEquals(R, KeyCombos.ownKey(KeyCombo.of(R)));
		assertNull(KeyCombos.kept(name));
		KeyCombos.read(new JsonObject());
	}

	@Test
	void debugKeysActOnlyWithTheirModifier() {
		KeyCombo f3H = KeySetting.debugCombo(F3, H);
		assertFalse(KeyCombo.of(H).sameAs(f3H), "a plain H is no conflict with F3 + H");
		assertTrue(new KeyCombo(List.of(F3), H).sameAs(f3H), "F3 + H is");
	}
}
