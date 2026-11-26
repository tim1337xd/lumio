package dev.tim1337.lumio.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.hud.HudInMenus.Look;
import dev.tim1337.lumio.hud.HudInMenus.Mode;
import dev.tim1337.lumio.hud.HudInMenus.Own;

import org.junit.jupiter.api.Test;

/** HUD elements while a menu is open: which show, how, and which take clicks. */
class HudInMenusTest {
	@Test
	void withoutAMenuEverythingIsAsAlways() {
		for (Mode mode : Mode.values()) {
			for (Own own : Own.values()) {
				assertEquals(Look.NORMAL, HudInMenus.look(mode, own, false, false), mode + " " + own);
			}
		}
	}

	@Test
	void theSettingDecidesForElementsThatFollowIt() {
		assertEquals(Look.DIMMED, HudInMenus.look(Mode.DIMMED, Own.FOLLOW, true, false));
		assertEquals(Look.NORMAL, HudInMenus.look(Mode.NORMAL, Own.FOLLOW, true, false));
		assertEquals(Look.HIDDEN, HudInMenus.look(Mode.HIDDEN, Own.FOLLOW, true, false));
		assertEquals(Mode.DIMMED, Mode.values()[0], "Dimmed is the first choice");
	}

	@Test
	void anElementsOwnChoiceWins() {
		for (Mode mode : Mode.values()) {
			assertEquals(Look.NORMAL, HudInMenus.look(mode, Own.SHOW, true, false), mode + ": always show");
			assertEquals(Look.HIDDEN, HudInMenus.look(mode, Own.HIDE, true, false), mode + ": always hide");
			assertEquals(Look.HIDDEN, HudInMenus.look(mode, Own.HIDE, true, true), mode + ": always hide, also one used in menus");
		}
	}

	@Test
	void elementsUsedInAnInventoryStayWhole() {
		for (Mode mode : Mode.values()) {
			assertEquals(Look.NORMAL, HudInMenus.look(mode, Own.FOLLOW, true, true), mode.toString());
		}
	}

	@Test
	void dimmedIsFainterAndDarkerByTheStrength() {
		assertEquals(1f, HudInMenus.opacity(Look.DIMMED, 0f, 0f));
		assertTrue(HudInMenus.opacity(Look.DIMMED, 0.5f, 0f) < 1f);
		assertTrue(HudInMenus.opacity(Look.DIMMED, 1f, 0f) < HudInMenus.opacity(Look.DIMMED, 0.5f, 0f));
		assertTrue(HudInMenus.opacity(Look.DIMMED, 1f, 0f) > 0f, "never gone by dimming alone");
		assertEquals(0f, HudInMenus.shade(Look.DIMMED, 0f));
		assertTrue(HudInMenus.shade(Look.DIMMED, 1f) > HudInMenus.shade(Look.DIMMED, 0.5f));
		assertEquals(0f, HudInMenus.shade(Look.NORMAL, 1f));
		assertEquals(0f, HudInMenus.opacity(Look.HIDDEN, 0f, 0f));
	}

	@Test
	void underTheMenusWindowAnElementFadesMoreOrIsGone() {
		int[] window = {100, 50, 200, 150};
		assertEquals(0f, HudInMenus.covered(new int[] {0, 0, 50, 20}, window));
		assertEquals(1f, HudInMenus.covered(new int[] {120, 60, 50, 20}, window));
		assertEquals(0.5f, HudInMenus.covered(new int[] {75, 60, 50, 20}, window), 1e-6f);
		assertEquals(0f, HudInMenus.covered(new int[] {75, 60, 50, 20}, null));
		// a quarter under the window: a quarter fainter; half or more: not drawn
		float free = HudInMenus.opacity(Look.DIMMED, 0.5f, 0f);
		assertEquals(free * 0.75f, HudInMenus.opacity(Look.DIMMED, 0.5f, 0.25f), 1e-6f);
		assertEquals(0f, HudInMenus.opacity(Look.DIMMED, 0.5f, 0.5f));
		assertEquals(0f, HudInMenus.opacity(Look.DIMMED, 0.5f, 0.8f));
		// the overlays of add-ons count like the window: the most covered share decides
		java.util.List<int[]> areas = java.util.List.of(new int[] {0, 0, 25, 20}, new int[] {300, 300, 10, 10});
		assertEquals(0.5f, HudInMenus.covered(new int[] {0, 0, 50, 20}, window, areas), 1e-6f);
		assertEquals(0f, HudInMenus.covered(new int[] {0, 200, 50, 20}, window, areas));
		assertEquals(3, Mode.values().length, "Dimmed, Normal, Hidden: no choice that looks like another");
		// one meant for the inventory stays whole even there
		assertEquals(1f, HudInMenus.opacity(Look.NORMAL, 0.5f, 0.8f));
	}
}
