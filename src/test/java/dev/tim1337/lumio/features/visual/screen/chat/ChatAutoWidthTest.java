package dev.tim1337.lumio.features.visual.screen.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Auto width: grows at once, shrinks slowly after a wait, never under the tabs or over the Max width, and old configs stay fixed. */
class ChatAutoWidthTest {
	private static final long FRAME_MILLIS = 16L;

	private static long run(ChatAutoWidth auto, int wanted, long from, long millis) {
		for (long now = from; now < from + millis; now += FRAME_MILLIS) {
			auto.update(wanted, now, true);
		}
		return from + millis;
	}

	@Test
	void growsAtOnce() {
		ChatAutoWidth auto = new ChatAutoWidth();
		auto.update(200, 0L, true);
		auto.update(320, FRAME_MILLIS, true);
		assertEquals(320, auto.width());
	}

	@Test
	void staysAfterAWiderLineLeftThenShrinksSlowly() {
		ChatAutoWidth auto = new ChatAutoWidth();
		auto.update(320, 0L, true);
		long now = run(auto, 200, FRAME_MILLIS, ChatAutoWidth.HOLD_MILLIS - 2 * FRAME_MILLIS);
		assertEquals(320, auto.width(), "still waiting");
		now = run(auto, 200, now, 200L);
		assertTrue(auto.width() > 280 && auto.width() < 320, "moves, but no jump: " + auto.width());
		run(auto, 200, now, 4_000L);
		assertEquals(200, auto.width(), "comes to its end");
	}

	@Test
	void neverCutsALineWhileShrinking() {
		ChatAutoWidth auto = new ChatAutoWidth();
		auto.update(320, 0L, true);
		long now = run(auto, 200, FRAME_MILLIS, ChatAutoWidth.HOLD_MILLIS + 300L);
		auto.update(260, now, true);
		assertTrue(auto.width() >= 260);
		auto.update(315, now + FRAME_MILLIS, true);
		assertEquals(315, auto.width(), "a wider line comes in again: at once");
	}

	@Test
	void aWiderLineStartsTheWaitAgain() {
		ChatAutoWidth auto = new ChatAutoWidth();
		auto.update(320, 0L, true);
		long now = run(auto, 200, FRAME_MILLIS, ChatAutoWidth.HOLD_MILLIS / 2);
		auto.update(320, now, true);
		run(auto, 200, now + FRAME_MILLIS, ChatAutoWidth.HOLD_MILLIS - 2 * FRAME_MILLIS);
		assertEquals(320, auto.width());
	}

	@Test
	void withoutAnimationsItShrinksInOneStep() {
		ChatAutoWidth auto = new ChatAutoWidth();
		auto.update(320, 0L, false);
		auto.update(200, ChatAutoWidth.HOLD_MILLIS, false);
		assertEquals(200, auto.width());
	}

	@Test
	void neverNarrowerThanTheTabs() {
		assertEquals(180, ChatAutoWidth.wanted(90, 180, 140, 500));
	}

	@Test
	void neverNarrowerThanTheInputNeedsWithoutTabs() {
		assertEquals(140, ChatAutoWidth.wanted(90, 0, 140, 500));
	}

	@Test
	void asWideAsTheLongestLine() {
		assertEquals(300, ChatAutoWidth.wanted(300, 180, 140, 500));
	}

	@Test
	void theMaxWidthGoesBeforeAll() {
		assertEquals(250, ChatAutoWidth.wanted(300, 180, 140, 250));
		assertEquals(120, ChatAutoWidth.wanted(90, 180, 140, 120));
	}

	@Test
	void aLineWrappedForTheMaxWidthFitsAtIt() {
		// what the Max width's wrapping leaves for a line, with the columns and padding, is the Max width again
		int most = 340;
		int time = 17;
		int longest = ChatLayout.wrapWidth(most, time, true);
		assertEquals(most, ChatLayout.panelFor(longest, time, true));
	}

	@Test
	void anOldConfigKeepsItsFixedWidth() {
		JsonObject json = JsonParser.parseString("{\"width\":280.0}").getAsJsonObject();
		ChatAutoWidth.keepOldWidth(json);
		assertEquals("FIXED", json.get("width_mode").getAsString());
		assertEquals(280f, json.get("width").getAsFloat());
	}

	@Test
	void aNewConfigAndOneWithAModeStayAsTheyAre() {
		JsonObject fresh = new JsonObject();
		ChatAutoWidth.keepOldWidth(fresh);
		assertFalse(fresh.has("width_mode"));
		JsonObject chosen = JsonParser.parseString("{\"width\":280.0,\"width_mode\":\"AUTO\"}").getAsJsonObject();
		ChatAutoWidth.keepOldWidth(chosen);
		assertEquals("AUTO", chosen.get("width_mode").getAsString());
	}
}
